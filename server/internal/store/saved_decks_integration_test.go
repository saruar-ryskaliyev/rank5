package store

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"testing"

	"github.com/google/uuid"
	"github.com/saruar/rank5/server/internal/game"
)

func integrationStore(t *testing.T) *Store {
	t.Helper()
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		t.Skip("DATABASE_URL not set")
	}
	st, err := Open(context.Background(), url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(st.Close)
	return st
}

func testUser(t *testing.T, st *Store, name string) *User {
	t.Helper()
	u, err := st.UpsertGoogleUser(context.Background(), "test:"+name+":"+uuid.NewString(), name)
	if err != nil {
		t.Fatal(err)
	}
	return u
}

func TestSavedDeckLifecycleAndCascadesIntegration(t *testing.T) {
	st := integrationStore(t)
	ctx := context.Background()
	owner := testUser(t, st, "owner")
	saver := testUser(t, st, "saver")
	t.Cleanup(func() {
		_, _ = st.pool.Exec(context.Background(), `DELETE FROM users WHERE id = $1 OR id = $2`, owner.ID, saver.ID)
	})
	questions := []game.Question{{ID: "q1", Prompt: "Pick", Options: []string{"a", "b", "c", "d", "e"}}}
	publicDeck, err := st.CreateDeck(ctx, owner.ID, "Public", "🌍", questions)
	if err != nil {
		t.Fatal(err)
	}
	if _, err = st.PublishDeck(ctx, owner.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	privateDeck, err := st.CreateDeck(ctx, owner.ID, "Private", "🔒", questions)
	if err != nil {
		t.Fatal(err)
	}
	builtinID := "test-builtin-" + uuid.NewString()
	builtinQuestions, _ := json.Marshal(questions)
	if err := st.UpsertBuiltinDeck(ctx, builtinID, "Official", "⭐", builtinQuestions); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _, _ = st.pool.Exec(context.Background(), `DELETE FROM decks WHERE id = $1`, builtinID) })

	if err := st.SaveDeckBookmark(ctx, saver.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.SaveDeckBookmark(ctx, saver.ID, publicDeck.ID); err != nil { // idempotent
		t.Fatal(err)
	}
	if err := st.SaveDeckBookmark(ctx, saver.ID, builtinID); err != nil {
		t.Fatal(err)
	}
	if err := st.SaveDeckBookmark(ctx, saver.ID, privateDeck.ID); !errors.Is(err, ErrForbidden) {
		t.Fatalf("private save error = %v, want forbidden", err)
	}
	if err := st.SaveDeckBookmark(ctx, owner.ID, publicDeck.ID); !errors.Is(err, ErrForbidden) {
		t.Fatalf("own save error = %v, want forbidden", err)
	}
	list, err := st.ListSavedDecks(ctx, saver.ID)
	if err != nil || len(list) != 2 {
		t.Fatalf("saved list = %+v, err=%v", list, err)
	}
	if _, err := st.UnpublishDeck(ctx, owner.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	list, err = st.ListSavedDecks(ctx, saver.ID)
	if err != nil || len(list) != 1 || list[0].ID != builtinID {
		t.Fatalf("unpublished deck leaked through saved list: %+v err=%v", list, err)
	}
	if err := st.UnsaveDeckBookmark(ctx, saver.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.UnsaveDeckBookmark(ctx, saver.ID, publicDeck.ID); err != nil { // idempotent
		t.Fatal(err)
	}
	if err := st.UnsaveDeckBookmark(ctx, saver.ID, builtinID); err != nil {
		t.Fatal(err)
	}

	if _, err := st.PublishDeck(ctx, owner.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.SaveDeckBookmark(ctx, saver.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.DeleteDeck(ctx, owner.ID, publicDeck.ID); err != nil {
		t.Fatal(err)
	}
	var count int
	if err := st.pool.QueryRow(ctx, `SELECT count(*) FROM user_saved_decks WHERE deck_id = $1`, publicDeck.ID).Scan(&count); err != nil || count != 0 {
		t.Fatalf("deck cascade count=%d err=%v", count, err)
	}

	otherOwner := testUser(t, st, "other-owner")
	t.Cleanup(func() { _, _ = st.pool.Exec(context.Background(), `DELETE FROM users WHERE id = $1`, otherOwner.ID) })
	otherDeck, _ := st.CreateDeck(ctx, otherOwner.ID, "Other", "✨", questions)
	_, _ = st.PublishDeck(ctx, otherOwner.ID, otherDeck.ID)
	if err := st.SaveDeckBookmark(ctx, saver.ID, otherDeck.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.DeleteUser(ctx, saver.ID); err != nil {
		t.Fatal(err)
	}
	if err := st.pool.QueryRow(ctx, `SELECT count(*) FROM user_saved_decks WHERE user_id = $1`, saver.ID).Scan(&count); err != nil || count != 0 {
		t.Fatalf("account cascade count=%d err=%v", count, err)
	}
}

func TestMultiDeckGameResultPersistenceIntegration(t *testing.T) {
	st := integrationStore(t)
	ctx := context.Background()
	roomCode := "T" + uuid.NewString()[:7]
	rounds := []byte(`[{"index":0,"deckId":"food","subjectId":"p1","teamScore":1800,"scores":{}}]`)
	if err := st.SaveGameResult(ctx, GameResult{
		RoomCode: roomCode, Mode: "coop", DeckID: "food", DeckIDs: []string{"food", "movies"},
		TotalRounds: 1, Rounds: rounds, Players: []ResultPlayer{{Nickname: "Tester"}},
	}); err != nil {
		t.Fatal(err)
	}
	var legacy string
	var deckIDsRaw, roundsRaw []byte
	if err := st.pool.QueryRow(ctx, `
		SELECT deck_id, deck_ids, rounds FROM game_results WHERE room_code = $1 ORDER BY finished_at DESC LIMIT 1
	`, roomCode).Scan(&legacy, &deckIDsRaw, &roundsRaw); err != nil {
		t.Fatal(err)
	}
	var deckIDs []string
	if err := json.Unmarshal(deckIDsRaw, &deckIDs); err != nil {
		t.Fatal(err)
	}
	if legacy != "food" || len(deckIDs) != 2 || deckIDs[1] != "movies" || !json.Valid(roundsRaw) {
		t.Fatalf("legacy=%q deckIDs=%v rounds=%s", legacy, deckIDs, roundsRaw)
	}
}
