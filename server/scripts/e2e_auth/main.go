// Auth + results attribution E2E.
// Requires a running server with DEV_FAKE_AUTH=1 and DATABASE_URL set.
// Optional: DATABASE_URL for direct DB assertions (defaults to docker-compose local).
package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/coder/websocket"
	"github.com/jackc/pgx/v5/pgxpool"
)

type envelope struct {
	Type    string          `json:"type"`
	Payload json.RawMessage `json:"payload"`
}

type welcome struct {
	PlayerID       string `json:"playerId"`
	ReconnectToken string `json:"reconnectToken"`
	IsHost         bool   `json:"isHost"`
}

type roomState struct {
	Phase        string `json:"phase"`
	TeamScore    int    `json:"teamScore"`
	CurrentRound *struct {
		Index    int `json:"index"`
		Question struct {
			ID      string   `json:"id"`
			Options []string `json:"options"`
		} `json:"question"`
	} `json:"currentRound"`
}

type authResp struct {
	Token string `json:"token"`
	User  struct {
		ID          string `json:"id"`
		DisplayName string `json:"displayName"`
	} `json:"user"`
}

func main() {
	base := envOr("BASE_URL", "http://127.0.0.1:8080")
	dbURL := envOr("DATABASE_URL", "postgres://rank5:rank5@127.0.0.1:5433/rank5?sslmode=disable")

	tok, uid := mustDevAuth(base, "E2ESigned")
	mustMe(base, tok, uid)

	deckID := mustCreateDeck(base, tok)
	mustListMyDecks(base, tok, deckID)
	mustGetDeck(base, tok, deckID)
	mustUpdateDeck(base, tok, deckID)

	code := mustCreate(base)
	host, guest := mustDial(base, code), mustDial(base, code)
	_ = join(host, "E2ESigned", tok)
	_ = join(guest, "E2EAnon", "")
	drain(host)
	drain(guest)

	mustSend(host, "start_game", map[string]any{"mode": "coop", "deckId": deckID, "rounds": 1})
	hs := waitPhase(host, "ROUND_SUBMIT", 5*time.Second)
	_ = waitPhase(guest, "ROUND_SUBMIT", 5*time.Second)
	opts := hs.CurrentRound.Question.Options
	mustSend(guest, "submit_ranking", rankingPayload(hs, opts))
	mustSend(host, "submit_ranking", rankingPayload(hs, opts))
	_ = waitPhase(host, "ROUND_REVEAL", 5*time.Second)
	_ = waitPhase(guest, "ROUND_REVEAL", 5*time.Second)
	mustSend(host, "ready", map[string]any{})
	mustSend(guest, "ready", map[string]any{})
	_ = waitPhase(host, "GAME_OVER", 5*time.Second)
	time.Sleep(800 * time.Millisecond)
	_ = host.Close(websocket.StatusNormalClosure, "")
	_ = guest.Close(websocket.StatusNormalClosure, "")

	// --- M3: publish → search → play as other user → report → moderate ---
	mustPublishDeck(base, tok, deckID)
	tok2, _ := mustDevAuth(base, "E2EReporter")
	mustSearchCommunity(base, "Private Deck Updated", deckID)
	mustSaveDeck(base, tok2, "food")
	mustSaveDeck(base, tok2, "food") // idempotent
	mustSaveDeck(base, tok2, deckID)
	mustListSavedDecks(base, tok2, []string{"food", deckID})
	mustRejectOwnSave(base, tok, deckID)
	mustUnsaveDeck(base, tok2, "food")
	mustUnsaveDeck(base, tok2, "food") // idempotent

	code2 := mustCreate(base)
	host2, guest2 := mustDial(base, code2), mustDial(base, code2)
	_ = join(host2, "E2EReporter", tok2)
	guestWelcome := join(guest2, "E2EGuest2", "")
	drain(host2)
	drain(guest2)
	mustSend(host2, "start_game", map[string]any{"mode": "coop", "deckId": deckID, "rounds": 1})
	hs2 := waitPhase(host2, "ROUND_SUBMIT", 5*time.Second)
	_ = waitPhase(guest2, "ROUND_SUBMIT", 5*time.Second)
	opts2 := hs2.CurrentRound.Question.Options
	mustSend(guest2, "submit_ranking", rankingPayload(hs2, opts2))
	mustSend(host2, "submit_ranking", rankingPayload(hs2, opts2))
	_ = waitPhase(host2, "ROUND_REVEAL", 5*time.Second)
	_ = waitPhase(guest2, "ROUND_REVEAL", 5*time.Second)
	mustSend(host2, "ready", map[string]any{})
	mustSend(guest2, "ready", map[string]any{})
	_ = waitPhase(host2, "GAME_OVER", 5*time.Second)
	time.Sleep(800 * time.Millisecond)
	claimToken, _ := mustDevAuth(base, "E2EClaimedGuest")
	mustRejectClaimResult(base, claimToken, code2, welcome{
		PlayerID:       guestWelcome.PlayerID,
		ReconnectToken: guestWelcome.ReconnectToken + "-wrong",
	})
	mustClaimResult(base, claimToken, code2, guestWelcome)
	mustRejectClaimResult(base, tok2, code2, guestWelcome)
	mustStatsAtLeast(base, claimToken, 1)
	_ = host2.Close(websocket.StatusNormalClosure, "")
	_ = guest2.Close(websocket.StatusNormalClosure, "")

	reportID := mustReportDeck(base, tok2, deckID, "spam test")

	ctx := context.Background()
	pool, err := pgxpool.New(ctx, dbURL)
	must(err)
	defer pool.Close()

	_, err = pool.Exec(ctx, `UPDATE users SET is_admin = true WHERE id = $1`, uid)
	must(err)
	mustResolveReport(base, tok, reportID, "remove")
	mustSearchCommunityMissing(base, deckID)
	mustListSavedDecks(base, tok2, nil) // removed deck metadata is hidden

	mustDeleteDeck(base, tok, deckID)

	rows, err := pool.Query(ctx, `
		SELECT p.nickname, p.user_id::text
		FROM game_results g
		JOIN game_result_players p ON p.game_id = g.id
		WHERE g.room_code = $1
		ORDER BY p.nickname`, code)
	must(err)
	got := map[string]*string{}
	for rows.Next() {
		var nick string
		var uidPtr *string
		must(rows.Scan(&nick, &uidPtr))
		got[nick] = uidPtr
	}
	rows.Close()
	must(rows.Err())

	if u := got["E2ESigned"]; u == nil || *u != uid {
		log.Fatalf("signed player user_id want %s got %v", uid, got["E2ESigned"])
	}
	if u := got["E2EAnon"]; u != nil {
		log.Fatalf("anon player should have NULL user_id, got %v", *u)
	}
	log.Printf("DB attribution ok room=%s", code)

	var builtinCount int
	must(pool.QueryRow(ctx, `SELECT count(*) FROM decks WHERE is_builtin = true`).Scan(&builtinCount))
	if builtinCount < 4 {
		log.Fatalf("expected seeded builtins, got %d", builtinCount)
	}

	req, _ := http.NewRequest(http.MethodDelete, base+"/me", nil)
	req.Header.Set("Authorization", "Bearer "+tok)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		log.Fatalf("DELETE /me want 204 got %d", resp.StatusCode)
	}

	var nullCount int
	must(pool.QueryRow(ctx, `
		SELECT count(*) FROM game_result_players
		WHERE nickname = 'E2ESigned'
		  AND game_id IN (SELECT id FROM game_results WHERE room_code = $1)
		  AND user_id IS NULL`, code).Scan(&nullCount))
	if nullCount != 1 {
		log.Fatalf("expected anonymized E2ESigned row, nullCount=%d", nullCount)
	}
	var userCount int
	must(pool.QueryRow(ctx, `SELECT count(*) FROM users WHERE id = $1`, uid).Scan(&userCount))
	if userCount != 0 {
		log.Fatalf("user row should be gone, count=%d", userCount)
	}

	for _, path := range []string{"/privacy", "/account-deletion"} {
		r, err := http.Get(base + path)
		must(err)
		b, _ := io.ReadAll(r.Body)
		r.Body.Close()
		if r.StatusCode != 200 || !bytes.Contains(b, []byte("Rank5")) {
			log.Fatalf("%s failed status=%d", path, r.StatusCode)
		}
	}

	fmt.Println("E2E AUTH PASS: sign-in, saved decks, stats, deck CRUD/moderation, attribution, deletion, compliance")
}

func mustSaveDeck(base, token, deckID string) {
	req, _ := http.NewRequest(http.MethodPut, base+"/me/saved-decks/"+url.PathEscape(deckID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("PUT saved deck: %s %s", resp.Status, raw)
	}
}

func mustUnsaveDeck(base, token, deckID string) {
	req, _ := http.NewRequest(http.MethodDelete, base+"/me/saved-decks/"+url.PathEscape(deckID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("DELETE saved deck: %s %s", resp.Status, raw)
	}
}

func mustListSavedDecks(base, token string, want []string) {
	req, _ := http.NewRequest(http.MethodGet, base+"/me/saved-decks", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		log.Fatalf("GET saved decks: %s %s", resp.Status, raw)
	}
	var decks []struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &decks))
	got := map[string]bool{}
	for _, deck := range decks {
		got[deck.ID] = true
	}
	for _, id := range want {
		if !got[id] {
			log.Fatalf("saved list missing %q: %s", id, raw)
		}
	}
	if len(want) == 0 && len(decks) != 0 {
		log.Fatalf("saved list should be empty after removal: %s", raw)
	}
}

func mustRejectOwnSave(base, token, deckID string) {
	req, _ := http.NewRequest(http.MethodPut, base+"/me/saved-decks/"+url.PathEscape(deckID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusForbidden {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("own save should be forbidden: %s %s", resp.Status, raw)
	}
}

func mustClaimResult(base, token, roomCode string, guest welcome) {
	body, _ := json.Marshal(map[string]string{
		"roomCode":       roomCode,
		"playerId":       guest.PlayerID,
		"reconnectToken": guest.ReconnectToken,
	})
	req, _ := http.NewRequest(http.MethodPost, base+"/me/results/claim", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		log.Fatalf("POST /me/results/claim: %s %s", resp.Status, raw)
	}
	var out struct {
		ClaimedGames int `json:"claimedGames"`
	}
	must(json.Unmarshal(raw, &out))
	if out.ClaimedGames < 1 {
		log.Fatalf("claim result count = %d, want at least 1", out.ClaimedGames)
	}
}

func mustRejectClaimResult(base, token, roomCode string, guest welcome) {
	body, _ := json.Marshal(map[string]string{
		"roomCode":       roomCode,
		"playerId":       guest.PlayerID,
		"reconnectToken": guest.ReconnectToken,
	})
	req, _ := http.NewRequest(http.MethodPost, base+"/me/results/claim", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("invalid result claim: want 404, got %s %s", resp.Status, raw)
	}
}

func mustStatsAtLeast(base, token string, wantGames int) {
	req, _ := http.NewRequest(http.MethodGet, base+"/me/stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		log.Fatalf("GET /me/stats: %s %s", resp.Status, raw)
	}
	var out struct {
		GamesPlayed int `json:"gamesPlayed"`
		Guessing    *struct {
			PredictionsTracked int `json:"predictionsTracked"`
		} `json:"guessing"`
	}
	must(json.Unmarshal(raw, &out))
	if out.GamesPlayed < wantGames {
		log.Fatalf("stats games = %d, want at least %d", out.GamesPlayed, wantGames)
	}
	if out.Guessing == nil || out.Guessing.PredictionsTracked < 1 {
		log.Fatalf("expected detailed guessing stats, got %s", raw)
	}
}

func mustDevAuth(base, name string) (token, userID string) {
	body, _ := json.Marshal(map[string]string{"name": name})
	resp, err := http.Post(base+"/auth/dev", "application/json", bytes.NewReader(body))
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("auth/dev: %s %s", resp.Status, raw)
	}
	var out authResp
	must(json.Unmarshal(raw, &out))
	return out.Token, out.User.ID
}

func mustMe(base, token, wantID string) {
	req, _ := http.NewRequest(http.MethodGet, base+"/me", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("/me: %s %s", resp.Status, raw)
	}
	var u struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &u))
	if u.ID != wantID {
		log.Fatalf("/me id mismatch")
	}
}

func sampleQuestions() []map[string]any {
	out := make([]map[string]any, 6)
	for i := 0; i < 6; i++ {
		out[i] = map[string]any{
			"id":     fmt.Sprintf("q%d", i+1),
			"prompt": fmt.Sprintf("Question %d?", i+1),
			"options": []string{
				fmt.Sprintf("A%d", i+1),
				fmt.Sprintf("B%d", i+1),
				fmt.Sprintf("C%d", i+1),
				fmt.Sprintf("D%d", i+1),
				fmt.Sprintf("E%d", i+1),
			},
		}
	}
	return out
}

func mustCreateDeck(base, token string) string {
	body, _ := json.Marshal(map[string]any{
		"title":     "E2E Private Deck",
		"emoji":     "🧪",
		"questions": sampleQuestions(),
	})
	req, _ := http.NewRequest(http.MethodPost, base+"/decks", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 201 {
		log.Fatalf("POST /decks: %s %s", resp.Status, raw)
	}
	var out struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &out))
	if out.ID == "" {
		log.Fatal("POST /decks missing id")
	}
	return out.ID
}

func mustListMyDecks(base, token, wantID string) {
	req, _ := http.NewRequest(http.MethodGet, base+"/me/decks", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("GET /me/decks: %s %s", resp.Status, raw)
	}
	var list []struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &list))
	for _, d := range list {
		if d.ID == wantID {
			return
		}
	}
	log.Fatalf("GET /me/decks missing %s", wantID)
}

func mustGetDeck(base, token, id string) {
	req, _ := http.NewRequest(http.MethodGet, base+"/decks/"+id, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("GET /decks/%s: %s %s", id, resp.Status, raw)
	}
}

func mustUpdateDeck(base, token, id string) {
	body, _ := json.Marshal(map[string]any{
		"title":     "E2E Private Deck Updated",
		"emoji":     "🔬",
		"questions": sampleQuestions(),
	})
	req, _ := http.NewRequest(http.MethodPut, base+"/decks/"+id, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("PUT /decks/%s: %s %s", id, resp.Status, raw)
	}
}

func mustDeleteDeck(base, token, id string) {
	req, _ := http.NewRequest(http.MethodDelete, base+"/decks/"+id, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("DELETE /decks/%s: %s %s", id, resp.Status, raw)
	}
}

func mustPublishDeck(base, token, id string) {
	req, _ := http.NewRequest(http.MethodPost, base+"/decks/"+id+"/publish", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("POST /decks/%s/publish: %s %s", id, resp.Status, raw)
	}
	var out struct {
		Visibility string `json:"visibility"`
	}
	must(json.Unmarshal(raw, &out))
	if out.Visibility != "public" {
		log.Fatalf("publish: want visibility=public got %q", out.Visibility)
	}
}

func mustSearchCommunity(base, query, wantID string) {
	resp, err := http.Get(base + "/community/decks?q=" + url.QueryEscape(query))
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("GET /community/decks: %s %s", resp.Status, raw)
	}
	var list []struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &list))
	for _, d := range list {
		if d.ID == wantID {
			return
		}
	}
	log.Fatalf("GET /community/decks?q=%s missing %s (got %d)", query, wantID, len(list))
}

func mustSearchCommunityMissing(base, id string) {
	resp, err := http.Get(base + "/community/decks")
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("GET /community/decks: %s %s", resp.Status, raw)
	}
	var list []struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &list))
	for _, d := range list {
		if d.ID == id {
			log.Fatalf("removed deck %s still in community list", id)
		}
	}
}

func mustReportDeck(base, token, deckID, reason string) string {
	body, _ := json.Marshal(map[string]string{"reason": reason})
	req, _ := http.NewRequest(http.MethodPost, base+"/decks/"+deckID+"/report", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 201 {
		log.Fatalf("POST /decks/%s/report: %s %s", deckID, resp.Status, raw)
	}
	var out struct {
		ID string `json:"id"`
	}
	must(json.Unmarshal(raw, &out))
	if out.ID == "" {
		log.Fatal("report missing id")
	}
	return out.ID
}

func mustResolveReport(base, token, reportID, action string) {
	body, _ := json.Marshal(map[string]string{"action": action})
	req, _ := http.NewRequest(http.MethodPost, base+"/admin/reports/"+reportID+"/resolve", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("resolve report: %s %s", resp.Status, raw)
	}
}

func rankingPayload(state roomState, ranking []string) map[string]any {
	if state.CurrentRound == nil {
		log.Fatal("cannot build ranking payload without a current round")
	}
	return map[string]any{
		"roundIndex": state.CurrentRound.Index,
		"questionId": state.CurrentRound.Question.ID,
		"ranking":    ranking,
	}
}

func envOr(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return strings.TrimRight(v, "/")
	}
	return d
}

func mustCreate(base string) string {
	resp, err := http.Post(base+"/rooms", "application/json", nil)
	must(err)
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 201 {
		log.Fatalf("create: %s %s", resp.Status, body)
	}
	var out struct {
		Code string `json:"code"`
	}
	must(json.Unmarshal(body, &out))
	return out.Code
}

func mustDial(base, code string) *websocket.Conn {
	wsURL := strings.Replace(base, "http", "ws", 1) + "/ws?code=" + code
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	c, _, err := websocket.Dial(ctx, wsURL, nil)
	must(err)
	return c
}

func join(c *websocket.Conn, nick, authToken string) welcome {
	payload := map[string]any{"nickname": nick}
	if authToken != "" {
		payload["authToken"] = authToken
	}
	mustSend(c, "join_room", payload)
	for i := 0; i < 8; i++ {
		env := mustRead(c, 3*time.Second)
		if env.Type == "welcome" {
			var w welcome
			must(json.Unmarshal(env.Payload, &w))
			return w
		}
		if env.Type == "error" {
			log.Fatalf("error: %s", env.Payload)
		}
	}
	log.Fatal("no welcome")
	return welcome{}
}

func drain(c *websocket.Conn) {
	_, _ = readEnv(c, 500*time.Millisecond)
}

func mustSend(c *websocket.Conn, typ string, payload any) {
	b, err := json.Marshal(map[string]any{"type": typ, "payload": payload})
	must(err)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	must(c.Write(ctx, websocket.MessageText, b))
}

func readEnv(c *websocket.Conn, timeout time.Duration) (envelope, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	_, data, err := c.Read(ctx)
	if err != nil {
		return envelope{}, err
	}
	var env envelope
	return env, json.Unmarshal(data, &env)
}

func mustRead(c *websocket.Conn, timeout time.Duration) envelope {
	env, err := readEnv(c, timeout)
	must(err)
	return env
}

func waitPhase(c *websocket.Conn, phase string, timeout time.Duration) roomState {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		env, err := readEnv(c, time.Until(deadline))
		must(err)
		if env.Type == "error" {
			log.Fatalf("error: %s", env.Payload)
		}
		if env.Type != "room_state" {
			continue
		}
		var s roomState
		must(json.Unmarshal(env.Payload, &s))
		if s.Phase == phase {
			return s
		}
	}
	log.Fatalf("timeout waiting for %s", phase)
	return roomState{}
}

func must(err error) {
	if err != nil {
		log.Fatal(err)
	}
}
