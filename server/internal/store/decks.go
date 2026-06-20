package store

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/saruar/rank5/server/internal/game"
)

// ErrNotFound is returned when a deck does not exist.
var ErrNotFound = errors.New("not found")

// ErrForbidden is returned when the caller does not own the deck.
var ErrForbidden = errors.New("forbidden")

// ErrConflict is returned when a unique constraint is violated (e.g. duplicate report).
var ErrConflict = errors.New("conflict")

// Visibility values for decks.
const (
	VisibilityPrivate = "private"
	VisibilityPublic  = "public"
	VisibilityRemoved = "removed"
)

// Report status values.
const (
	ReportOpen      = "open"
	ReportDismissed = "dismissed"
	ReportActioned  = "actioned"
)

// Deck is a persisted deck row.
type Deck struct {
	ID          string
	OwnerID     *uuid.UUID
	Title       string
	Emoji       string
	Questions   []game.Question
	IsBuiltin   bool
	Visibility  string
	PublishedAt *time.Time
	OwnerName   string
	CreatedAt   time.Time
	UpdatedAt   time.Time
}

// DeckSummary is a lightweight listing row.
type DeckSummary struct {
	ID            string
	Title         string
	Emoji         string
	IsBuiltin     bool
	OwnerID       *uuid.UUID
	QuestionCount int
	Visibility    string
	OwnerName     string
	PublishedAt   *time.Time
}

// DeckReport is a moderation report row.
type DeckReport struct {
	ID           uuid.UUID
	DeckID       string
	ReporterID   uuid.UUID
	Reason       string
	Status       string
	CreatedAt    time.Time
	DeckTitle    string
	DeckEmoji    string
	ReporterName string
}

const deckSelectCols = `id, owner_id, title, emoji, questions, is_builtin, visibility, published_at, coalesce(owner_name, ''), created_at, updated_at`

func scanDeck(row pgx.Row) (*Deck, error) {
	var d Deck
	var qsRaw []byte
	err := row.Scan(
		&d.ID, &d.OwnerID, &d.Title, &d.Emoji, &qsRaw, &d.IsBuiltin,
		&d.Visibility, &d.PublishedAt, &d.OwnerName, &d.CreatedAt, &d.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	if err := json.Unmarshal(qsRaw, &d.Questions); err != nil {
		return nil, err
	}
	if d.Visibility == "" {
		d.Visibility = VisibilityPrivate
	}
	return &d, nil
}

// UpsertBuiltinDeck inserts or refreshes a built-in deck from seed data.
func (s *Store) UpsertBuiltinDeck(ctx context.Context, id, title, emoji string, questions []byte) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	_, err := s.pool.Exec(ctx, `
		INSERT INTO decks
			(id, owner_id, title, emoji, questions, is_builtin, visibility, owner_name, published_at)
		VALUES ($1, NULL, $2, $3, $4::jsonb, true, 'public', 'Rank5', now())
		ON CONFLICT (id) DO UPDATE SET
			title = EXCLUDED.title,
			emoji = EXCLUDED.emoji,
			questions = EXCLUDED.questions,
			is_builtin = true,
			owner_id = NULL,
			visibility = 'public',
			owner_name = 'Rank5',
			published_at = coalesce(decks.published_at, now()),
			updated_at = now()
	`, id, title, emoji, questions)
	return err
}

// CreateDeck inserts a private user deck and returns it.
func (s *Store) CreateDeck(ctx context.Context, ownerID uuid.UUID, title, emoji string, questions []game.Question) (*Deck, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	qs, err := json.Marshal(questions)
	if err != nil {
		return nil, err
	}
	ownerName := ""
	if u, err := s.GetUser(ctx, ownerID); err == nil {
		ownerName = u.DisplayName
	}
	id := uuid.NewString()
	row := s.pool.QueryRow(ctx, `
		INSERT INTO decks (id, owner_id, title, emoji, questions, is_builtin, visibility, owner_name)
		VALUES ($1, $2, $3, $4, $5::jsonb, false, 'private', $6)
		RETURNING `+deckSelectCols+`
	`, id, ownerID, title, emoji, qs, ownerName)
	return scanDeck(row)
}

// UpdateDeck updates a private or public deck owned by ownerID. Visibility is preserved.
func (s *Store) UpdateDeck(ctx context.Context, ownerID uuid.UUID, id, title, emoji string, questions []game.Question) (*Deck, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	existing, err := s.GetDeck(ctx, id)
	if err != nil {
		return nil, err
	}
	if existing.IsBuiltin || existing.OwnerID == nil || *existing.OwnerID != ownerID {
		return nil, ErrForbidden
	}
	qs, err := json.Marshal(questions)
	if err != nil {
		return nil, err
	}
	row := s.pool.QueryRow(ctx, `
		UPDATE decks
		SET title = $1, emoji = $2, questions = $3::jsonb, updated_at = now()
		WHERE id = $4 AND owner_id = $5 AND is_builtin = false
		RETURNING `+deckSelectCols+`
	`, title, emoji, qs, id, ownerID)
	return scanDeck(row)
}

// DeleteDeck removes a private/public deck owned by ownerID.
func (s *Store) DeleteDeck(ctx context.Context, ownerID uuid.UUID, id string) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	tag, err := s.pool.Exec(ctx, `
		DELETE FROM decks
		WHERE id = $1 AND owner_id = $2 AND is_builtin = false
	`, id, ownerID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		existing, gerr := s.GetDeck(ctx, id)
		if gerr != nil {
			return gerr
		}
		if existing.IsBuiltin || existing.OwnerID == nil || *existing.OwnerID != ownerID {
			return ErrForbidden
		}
		return ErrNotFound
	}
	return nil
}

// GetDeck returns a deck by id.
func (s *Store) GetDeck(ctx context.Context, id string) (*Deck, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	row := s.pool.QueryRow(ctx, `
		SELECT `+deckSelectCols+` FROM decks WHERE id = $1
	`, id)
	return scanDeck(row)
}

// PublishDeck makes a user-owned deck public.
func (s *Store) PublishDeck(ctx context.Context, ownerID uuid.UUID, id string) (*Deck, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	existing, err := s.GetDeck(ctx, id)
	if err != nil {
		return nil, err
	}
	if existing.IsBuiltin || existing.OwnerID == nil || *existing.OwnerID != ownerID {
		return nil, ErrForbidden
	}
	if existing.Visibility == VisibilityRemoved {
		return nil, fmt.Errorf("%w: deck was removed by moderation", ErrForbidden)
	}
	ownerName := existing.OwnerName
	if u, err := s.GetUser(ctx, ownerID); err == nil {
		ownerName = u.DisplayName
	}
	row := s.pool.QueryRow(ctx, `
		UPDATE decks
		SET visibility = 'public',
		    published_at = coalesce(published_at, now()),
		    owner_name = $1,
		    updated_at = now()
		WHERE id = $2 AND owner_id = $3 AND is_builtin = false AND visibility <> 'removed'
		RETURNING `+deckSelectCols+`
	`, ownerName, id, ownerID)
	return scanDeck(row)
}

// UnpublishDeck returns a public deck to private.
func (s *Store) UnpublishDeck(ctx context.Context, ownerID uuid.UUID, id string) (*Deck, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	existing, err := s.GetDeck(ctx, id)
	if err != nil {
		return nil, err
	}
	if existing.IsBuiltin || existing.OwnerID == nil || *existing.OwnerID != ownerID {
		return nil, ErrForbidden
	}
	row := s.pool.QueryRow(ctx, `
		UPDATE decks
		SET visibility = 'private', published_at = NULL, updated_at = now()
		WHERE id = $1 AND owner_id = $2 AND is_builtin = false
		RETURNING `+deckSelectCols+`
	`, id, ownerID)
	return scanDeck(row)
}

// SearchPublicDecks browses or full-text searches the unified public library.
// Official Rank5 decks are persisted rows and sort before community decks.
func (s *Store) SearchPublicDecks(ctx context.Context, query string, limit, offset int) ([]DeckSummary, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	if offset < 0 {
		offset = 0
	}
	query = strings.TrimSpace(query)

	var rows pgx.Rows
	var err error
	if query == "" {
		rows, err = s.pool.Query(ctx, `
			SELECT id, title, emoji, is_builtin, owner_id, jsonb_array_length(questions),
			       visibility, coalesce(owner_name, ''), published_at
			FROM decks
			WHERE visibility = 'public'
			ORDER BY is_builtin DESC, published_at DESC NULLS LAST, title
			LIMIT $1 OFFSET $2
		`, limit, offset)
	} else {
		rows, err = s.pool.Query(ctx, `
			SELECT id, title, emoji, is_builtin, owner_id, jsonb_array_length(questions),
			       visibility, coalesce(owner_name, ''), published_at
			FROM decks
			WHERE visibility = 'public'
			  AND search_vector @@ websearch_to_tsquery('english', $1)
			ORDER BY is_builtin DESC,
			         ts_rank(search_vector, websearch_to_tsquery('english', $1)) DESC,
			         published_at DESC NULLS LAST
			LIMIT $2 OFFSET $3
		`, query, limit, offset)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanCommunitySummaries(rows)
}

// ListBuiltinDecks returns built-in deck summaries ordered by title.
func (s *Store) ListBuiltinDecks(ctx context.Context) ([]DeckSummary, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	rows, err := s.pool.Query(ctx, `
		SELECT id, title, emoji, is_builtin, owner_id, jsonb_array_length(questions),
		       visibility, coalesce(owner_name, ''), published_at
		FROM decks WHERE is_builtin = true
		ORDER BY title
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanCommunitySummaries(rows)
}

// ListDecksByOwner returns decks for a user (including removed, so owners can see them).
func (s *Store) ListDecksByOwner(ctx context.Context, ownerID uuid.UUID) ([]DeckSummary, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	rows, err := s.pool.Query(ctx, `
		SELECT id, title, emoji, is_builtin, owner_id, jsonb_array_length(questions),
		       visibility, coalesce(owner_name, ''), published_at
		FROM decks WHERE owner_id = $1 AND is_builtin = false
		ORDER BY updated_at DESC
	`, ownerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanCommunitySummaries(rows)
}

// ListSavedDecks returns only bookmarks that are still publicly accessible.
// Private, unpublished, or moderated decks remain hidden and expose no metadata.
func (s *Store) ListSavedDecks(ctx context.Context, userID uuid.UUID) ([]DeckSummary, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	rows, err := s.pool.Query(ctx, `
		SELECT d.id, d.title, d.emoji, d.is_builtin, d.owner_id,
		       jsonb_array_length(d.questions), d.visibility,
		       coalesce(d.owner_name, ''), d.published_at
		FROM user_saved_decks saved
		JOIN decks d ON d.id = saved.deck_id
		WHERE saved.user_id = $1
		  AND d.visibility = 'public'
		  AND (d.owner_id IS NULL OR d.owner_id <> $1)
		ORDER BY saved.created_at DESC, d.title
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanCommunitySummaries(rows)
}

// SaveDeckBookmark idempotently bookmarks an accessible deck not owned by the user.
func (s *Store) SaveDeckBookmark(ctx context.Context, userID uuid.UUID, deckID string) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	tag, err := s.pool.Exec(ctx, `
		INSERT INTO user_saved_decks (user_id, deck_id)
		SELECT $1, d.id
		FROM decks d
		WHERE d.id = $2
		  AND d.visibility = 'public'
		  AND (d.owner_id IS NULL OR d.owner_id <> $1)
		ON CONFLICT (user_id, deck_id) DO NOTHING
	`, userID, deckID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() > 0 {
		return nil
	}
	var eligible bool
	err = s.pool.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM decks
			WHERE id = $2 AND visibility = 'public'
			  AND (owner_id IS NULL OR owner_id <> $1)
		)
	`, userID, deckID).Scan(&eligible)
	if err != nil {
		return err
	}
	if eligible { // duplicate save
		return nil
	}
	return ErrForbidden
}

// UnsaveDeckBookmark is intentionally idempotent, including after deck deletion.
func (s *Store) UnsaveDeckBookmark(ctx context.Context, userID uuid.UUID, deckID string) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	_, err := s.pool.Exec(ctx, `
		DELETE FROM user_saved_decks WHERE user_id = $1 AND deck_id = $2
	`, userID, deckID)
	return err
}

func scanCommunitySummaries(rows pgx.Rows) ([]DeckSummary, error) {
	out := make([]DeckSummary, 0)
	for rows.Next() {
		var d DeckSummary
		if err := rows.Scan(
			&d.ID, &d.Title, &d.Emoji, &d.IsBuiltin, &d.OwnerID, &d.QuestionCount,
			&d.Visibility, &d.OwnerName, &d.PublishedAt,
		); err != nil {
			return nil, err
		}
		if d.Visibility == "" {
			d.Visibility = VisibilityPrivate
		}
		out = append(out, d)
	}
	return out, rows.Err()
}

// CreateReport files a report against a public deck.
func (s *Store) CreateReport(ctx context.Context, reporterID uuid.UUID, deckID, reason string) (*DeckReport, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	d, err := s.GetDeck(ctx, deckID)
	if err != nil {
		return nil, err
	}
	if d.IsBuiltin || d.Visibility != VisibilityPublic {
		return nil, fmt.Errorf("%w: can only report public decks", ErrForbidden)
	}
	if d.OwnerID != nil && *d.OwnerID == reporterID {
		return nil, fmt.Errorf("%w: cannot report your own deck", ErrForbidden)
	}
	var rep DeckReport
	err = s.pool.QueryRow(ctx, `
		INSERT INTO deck_reports (deck_id, reporter_id, reason)
		VALUES ($1, $2, $3)
		RETURNING id, deck_id, reporter_id, reason, status, created_at
	`, deckID, reporterID, reason).Scan(
		&rep.ID, &rep.DeckID, &rep.ReporterID, &rep.Reason, &rep.Status, &rep.CreatedAt,
	)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrConflict
		}
		return nil, err
	}
	rep.DeckTitle = d.Title
	rep.DeckEmoji = d.Emoji
	return &rep, nil
}

// ListOpenReports returns open moderation reports newest first.
func (s *Store) ListOpenReports(ctx context.Context) ([]DeckReport, error) {
	if !s.Enabled() {
		return nil, fmt.Errorf("store disabled")
	}
	rows, err := s.pool.Query(ctx, `
		SELECT r.id, r.deck_id, r.reporter_id, r.reason, r.status, r.created_at,
		       coalesce(d.title, ''), coalesce(d.emoji, ''), coalesce(u.display_name, '')
		FROM deck_reports r
		LEFT JOIN decks d ON d.id = r.deck_id
		LEFT JOIN users u ON u.id = r.reporter_id
		WHERE r.status = 'open'
		ORDER BY r.created_at DESC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]DeckReport, 0)
	for rows.Next() {
		var r DeckReport
		if err := rows.Scan(
			&r.ID, &r.DeckID, &r.ReporterID, &r.Reason, &r.Status, &r.CreatedAt,
			&r.DeckTitle, &r.DeckEmoji, &r.ReporterName,
		); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

// ResolveReport dismisses a report or removes the deck (soft-remove).
func (s *Store) ResolveReport(ctx context.Context, reportID uuid.UUID, action string) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	var deckID string
	var status string
	err := s.pool.QueryRow(ctx, `
		SELECT deck_id, status FROM deck_reports WHERE id = $1
	`, reportID).Scan(&deckID, &status)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		return err
	}
	if status != ReportOpen {
		return fmt.Errorf("%w: report already resolved", ErrConflict)
	}

	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	newStatus := ReportDismissed
	if action == "remove" {
		newStatus = ReportActioned
		_, err = tx.Exec(ctx, `
			UPDATE decks
			SET visibility = 'removed', published_at = NULL, updated_at = now()
			WHERE id = $1 AND is_builtin = false
		`, deckID)
		if err != nil {
			return err
		}
		// Close sibling open reports on the same deck.
		_, err = tx.Exec(ctx, `
			UPDATE deck_reports SET status = 'actioned'
			WHERE deck_id = $1 AND status = 'open'
		`, deckID)
		if err != nil {
			return err
		}
	} else {
		_, err = tx.Exec(ctx, `
			UPDATE deck_reports SET status = $1 WHERE id = $2
		`, newStatus, reportID)
		if err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

// ToDeckInfo converts a summary to the lobby listing DTO.
func (d DeckSummary) ToDeckInfo() game.DeckInfo {
	return game.DeckInfo{
		ID: d.ID, Name: d.Title, Emoji: d.Emoji, QuestionCount: d.QuestionCount,
	}
}

// QuestionsForGame returns up to n questions from a stored deck.
func (d *Deck) QuestionsForGame(n int) []game.Question {
	if n > len(d.Questions) {
		n = len(d.Questions)
	}
	out := make([]game.Question, n)
	copy(out, d.Questions[:n])
	return out
}

// IsPublic returns true when the deck is in the community library.
func (d *Deck) IsPublic() bool {
	return d.Visibility == VisibilityPublic
}

func isUniqueViolation(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "23505"
}
