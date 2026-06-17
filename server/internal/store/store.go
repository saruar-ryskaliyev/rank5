package store

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"log"
	"sort"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

// Store wraps a Postgres pool. A nil Store is a valid no-op for game-only mode.
type Store struct {
	pool *pgxpool.Pool
}

// User is a persisted account.
type User struct {
	ID          uuid.UUID
	GoogleSub   string
	DisplayName string
	AvatarSeed  string
	IsAdmin     bool
	CreatedAt   time.Time
}

// ResultPlayer is one player row written at game end.
type ResultPlayer struct {
	UserID         *uuid.UUID // nil = anonymous
	PlayerID       string
	Nickname       string
	Score          int
	ClaimTokenHash string
}

// GameResult is one finished game.
type GameResult struct {
	RoomCode    string
	Mode        string
	DeckID      string
	DeckIDs     []string
	TotalRounds int
	Rounds      []byte // JSON
	Players     []ResultPlayer
}

// Open connects to DATABASE_URL and runs migrations.
// Returns (nil, nil) when databaseURL is empty — callers treat nil as disabled.
func Open(ctx context.Context, databaseURL string) (*Store, error) {
	if databaseURL == "" {
		log.Printf("store: DATABASE_URL unset — auth/results persistence disabled")
		return nil, nil
	}
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		return nil, fmt.Errorf("store connect: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("store ping: %w", err)
	}
	s := &Store{pool: pool}
	if err := s.migrate(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	log.Printf("store: connected and migrated")
	return s, nil
}

func (s *Store) Close() {
	if s != nil && s.pool != nil {
		s.pool.Close()
	}
}

func (s *Store) Enabled() bool {
	return s != nil && s.pool != nil
}

func (s *Store) migrate(ctx context.Context) error {
	entries, err := migrationsFS.ReadDir("migrations")
	if err != nil {
		return fmt.Errorf("list migrations: %w", err)
	}
	names := make([]string, 0, len(entries))
	for _, e := range entries {
		if e.IsDir() || len(e.Name()) < 5 || e.Name()[len(e.Name())-4:] != ".sql" {
			continue
		}
		names = append(names, e.Name())
	}
	sort.Strings(names)
	for _, name := range names {
		sqlBytes, err := migrationsFS.ReadFile("migrations/" + name)
		if err != nil {
			return fmt.Errorf("read migration %s: %w", name, err)
		}
		if _, err := s.pool.Exec(ctx, string(sqlBytes)); err != nil {
			return fmt.Errorf("run migration %s: %w", name, err)
		}
	}
	return nil
}

// UpsertGoogleUser finds or creates a user by Google subject.
func (s *Store) UpsertGoogleUser(ctx context.Context, googleSub, displayName string) (*User, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	avatarSeed := googleSub
	var u User
	err := s.pool.QueryRow(ctx, `
		INSERT INTO users (google_sub, display_name, avatar_seed)
		VALUES ($1, $2, $3)
		ON CONFLICT (google_sub) DO UPDATE
			SET display_name = EXCLUDED.display_name
		RETURNING id, google_sub, display_name, avatar_seed, coalesce(is_admin, false), created_at
	`, googleSub, displayName, avatarSeed).Scan(
		&u.ID, &u.GoogleSub, &u.DisplayName, &u.AvatarSeed, &u.IsAdmin, &u.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return &u, nil
}

// GetUser returns a user by id.
func (s *Store) GetUser(ctx context.Context, id uuid.UUID) (*User, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	var u User
	err := s.pool.QueryRow(ctx, `
		SELECT id, google_sub, display_name, avatar_seed, coalesce(is_admin, false), created_at
		FROM users WHERE id = $1
	`, id).Scan(&u.ID, &u.GoogleSub, &u.DisplayName, &u.AvatarSeed, &u.IsAdmin, &u.CreatedAt)
	if err != nil {
		return nil, err
	}
	return &u, nil
}

// PromoteAdminsByGoogleSubs sets is_admin=true for the given Google subject IDs.
// Used at boot from ADMIN_GOOGLE_SUBS. Non-matching subs are ignored.
func (s *Store) PromoteAdminsByGoogleSubs(ctx context.Context, googleSubs []string) (int64, error) {
	if !s.Enabled() || len(googleSubs) == 0 {
		return 0, nil
	}
	tag, err := s.pool.Exec(ctx, `
		UPDATE users SET is_admin = true
		WHERE google_sub = ANY($1) AND is_admin = false
	`, googleSubs)
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

// IsAdmin reports whether the user has moderation privileges.
func (s *Store) IsAdmin(ctx context.Context, id uuid.UUID) (bool, error) {
	if !s.Enabled() {
		return false, fmt.Errorf("store disabled")
	}
	var admin bool
	err := s.pool.QueryRow(ctx, `
		SELECT coalesce(is_admin, false) FROM users WHERE id = $1
	`, id).Scan(&admin)
	if err != nil {
		return false, err
	}
	return admin, nil
}

// DeleteUser removes the account. game_result_players.user_id is SET NULL.
func (s *Store) DeleteUser(ctx context.Context, id uuid.UUID) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	tag, err := s.pool.Exec(ctx, `DELETE FROM users WHERE id = $1`, id)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("user not found")
	}
	return nil
}

// SaveGameResult persists one finished game. No-op when store is disabled.
func (s *Store) SaveGameResult(ctx context.Context, r GameResult) error {
	if !s.Enabled() {
		return nil
	}
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	deckIDs := append([]string(nil), r.DeckIDs...)
	if len(deckIDs) == 0 && r.DeckID != "" {
		deckIDs = []string{r.DeckID}
	}

	var gameID uuid.UUID
	err = tx.QueryRow(ctx, `
		INSERT INTO game_results (room_code, mode, deck_id, deck_ids, total_rounds, rounds)
		VALUES ($1, $2, $3, $4::jsonb, $5, $6)
		RETURNING id
	`, r.RoomCode, r.Mode, r.DeckID, mustJSON(deckIDs), r.TotalRounds, r.Rounds).Scan(&gameID)
	if err != nil {
		return err
	}
	for _, p := range r.Players {
		_, err = tx.Exec(ctx, `
			INSERT INTO game_result_players
				(game_id, user_id, player_id, nickname, score, claim_token_hash)
			VALUES ($1, $2, $3, $4, $5, $6)
		`, gameID, p.UserID, nullableString(p.PlayerID), p.Nickname, p.Score, nullableString(p.ClaimTokenHash))
		if err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

func mustJSON(value any) []byte {
	b, err := json.Marshal(value)
	if err != nil {
		return []byte("[]")
	}
	return b
}

func nullableString(value string) any {
	if value == "" {
		return nil
	}
	return value
}
