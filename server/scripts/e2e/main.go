package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/coder/websocket"
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
	Phase        string   `json:"phase"`
	Mode         string   `json:"mode"`
	DeckIDs      []string `json:"deckIds"`
	TotalRounds  int      `json:"totalRounds"`
	TeamScore    int      `json:"teamScore"`
	Paused       bool     `json:"paused"`
	AbortReason  string   `json:"abortReason"`
	CurrentRound *struct {
		Index     int    `json:"index"`
		SubjectID string `json:"subjectId"`
		Question  struct {
			ID      string   `json:"id"`
			DeckID  string   `json:"deckId"`
			Kind    string   `json:"kind"`
			Prompt  string   `json:"prompt"`
			Options []string `json:"options"`
		} `json:"question"`
		Scores         map[string]int  `json:"scores"`
		Submitted      map[string]bool `json:"submitted"`
		Ready          map[string]bool `json:"ready"`
		DeadlineMs     int64           `json:"deadlineMs"`
		SkipsRemaining int             `json:"skipsRemaining"`
		CanSkip        bool            `json:"canSkip"`
	} `json:"currentRound"`
	Players []struct {
		ID     string `json:"id"`
		IsHost bool   `json:"isHost"`
		Score  int    `json:"score"`
	} `json:"players"`
}

func main() {
	base := envOr("BASE_URL", "http://127.0.0.1:8080")
	log.Printf("=== COOP ===")
	runCoop(base)
	log.Printf("=== MULTI-DECK ===")
	runMultiDeck(base)
	log.Printf("=== DISCONNECT LIFECYCLE ===")
	runDisconnectLifecycle(base)
	log.Printf("=== PERSONALIZED QUESTIONS ===")
	runPersonalizedQuestions(base)
	fmt.Println("E2E PASS (skip + team scoring + multi-deck + disconnect/reconnect/leave + personalized/most-likely)")
}

// runPersonalizedQuestions covers the two personalization paths: a three
// player room ranking each other, and a two player room that cannot.
func runPersonalizedQuestions(base string) {
	code := mustCreate(base)
	host, second, third := mustDial(base, code), mustDial(base, code), mustDial(base, code)
	hostWelcome := join(host, "Maya")
	secondWelcome := join(second, "Noah")
	thirdWelcome := join(third, "Iris")
	drain(host)
	drain(second)
	drain(third)

	nicknames := map[string]string{
		hostWelcome.PlayerID:   "Maya",
		secondWelcome.PlayerID: "Noah",
		thirdWelcome.PlayerID:  "Iris",
	}
	settings := map[string]any{
		"mode": "coop", "deckIds": []string{"most_likely"}, "rounds": 1,
	}
	mustSend(host, "start_game", settings)
	hs := waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
	gs := waitPhase(second, "ROUND_SUBMIT", 3*time.Second)
	ts := waitPhase(third, "ROUND_SUBMIT", 3*time.Second)

	options := hs.CurrentRound.Question.Options
	if len(options) != 3 {
		log.Fatalf("most-likely options = %v, want the three players", options)
	}
	want := map[string]bool{"Maya": true, "Noah": true, "Iris": true}
	for _, option := range options {
		if !want[option] {
			log.Fatalf("unexpected option %q in %v", option, options)
		}
		delete(want, option)
	}
	// Every client must rank the same set, though order is personalized per room.
	for _, state := range []roomState{gs, ts} {
		if len(state.CurrentRound.Question.Options) != 3 {
			log.Fatalf("client saw %v, want three players", state.CurrentRound.Question.Options)
		}
	}
	log.Printf("most-likely options: %v subject=%s", options, nicknames[hs.CurrentRound.SubjectID])

	mustSend(second, "submit_ranking", rankingPayload(gs, gs.CurrentRound.Question.Options))
	mustSend(third, "submit_ranking", rankingPayload(ts, ts.CurrentRound.Question.Options))
	mustSend(host, "submit_ranking", rankingPayload(hs, options))
	reveal := waitPhase(host, "ROUND_REVEAL", 3*time.Second)
	if reveal.TeamScore <= 0 {
		log.Fatalf("most-likely round scored %d", reveal.TeamScore)
	}
	log.Printf("most-likely reveal teamScore=%d", reveal.TeamScore)
	for _, c := range []*websocket.Conn{host, second, third} {
		_ = c.Close(websocket.StatusNormalClosure, "")
	}

	// A personalized deck names the subject in the prompt itself.
	code = mustCreate(base)
	host, second = mustDial(base, code), mustDial(base, code)
	_ = join(host, "Maya")
	_ = join(second, "Noah")
	drain(host)
	drain(second)
	mustSend(host, "start_game", map[string]any{
		"mode": "coop", "deckIds": []string{"fact_check"}, "rounds": 1,
	})
	personalized := waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
	prompt := personalized.CurrentRound.Question.Prompt
	if strings.Contains(prompt, "{subject}") {
		log.Fatalf("prompt was not personalized: %q", prompt)
	}
	if !strings.Contains(prompt, "Maya") && !strings.Contains(prompt, "Noah") {
		log.Fatalf("prompt does not name the subject: %q", prompt)
	}
	log.Printf("personalized prompt: %q", prompt)

	// The same room cannot play a most-likely deck with only two players.
	mustSend(host, "leave_room", map[string]any{})
	_ = host.Close(websocket.StatusNormalClosure, "")
	_ = second.Close(websocket.StatusNormalClosure, "")

	code = mustCreate(base)
	host, second = mustDial(base, code), mustDial(base, code)
	_ = join(host, "Maya")
	_ = join(second, "Noah")
	drain(host)
	drain(second)
	mustSend(host, "start_game", settings)
	message := waitError(host, 3*time.Second)
	if !strings.Contains(message, "at least 3 players") {
		log.Fatalf("two-player most-likely error = %q", message)
	}
	log.Printf("two-player most-likely rejected: %q", message)
	_ = host.Close(websocket.StatusNormalClosure, "")
	_ = second.Close(websocket.StatusNormalClosure, "")
}

func runDisconnectLifecycle(base string) {
	// A dropped socket pauses a two-player game and reconnect resumes it.
	code := mustCreate(base)
	host, guest := mustDial(base, code), mustDial(base, code)
	hostWelcome := join(host, "ReconnectHost")
	_ = join(guest, "ReconnectGuest")
	drain(host)
	drain(guest)
	mustSend(host, "start_game", map[string]any{"mode": "coop", "deckId": "food", "rounds": 1})
	_ = waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
	_ = waitPhase(guest, "ROUND_SUBMIT", 3*time.Second)
	_ = host.Close(websocket.StatusNormalClosure, "network test")
	paused := waitPaused(guest, true, 3*time.Second)
	if paused.Phase != "ROUND_SUBMIT" {
		log.Fatalf("disconnect changed phase instead of pausing: %s", paused.Phase)
	}

	reconnected := mustDial(base, code)
	mustSend(reconnected, "reconnect", map[string]any{
		"playerId": hostWelcome.PlayerID, "reconnectToken": hostWelcome.ReconnectToken,
	})
	for {
		env := mustRead(reconnected, 3*time.Second)
		if env.Type == "welcome" {
			break
		}
		if env.Type == "error" {
			log.Fatalf("reconnect error: %s", env.Payload)
		}
	}
	_ = waitPaused(reconnected, false, 3*time.Second)
	_ = waitPaused(guest, false, 3*time.Second)
	_ = reconnected.Close(websocket.StatusNormalClosure, "")
	_ = guest.Close(websocket.StatusNormalClosure, "")

	// An explicit host leave immediately aborts when only one player remains.
	code = mustCreate(base)
	host, guest = mustDial(base, code), mustDial(base, code)
	_ = join(host, "LeavingHost")
	guestWelcome := join(guest, "RemainingGuest")
	drain(host)
	drain(guest)
	mustSend(host, "start_game", map[string]any{"mode": "coop", "deckId": "food", "rounds": 1})
	_ = waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
	_ = waitPhase(guest, "ROUND_SUBMIT", 3*time.Second)
	mustSend(host, "leave_room", map[string]any{})
	aborted := waitPhase(guest, "LOBBY", 3*time.Second)
	if aborted.AbortReason != "not_enough_players" || len(aborted.Players) != 1 ||
		aborted.Players[0].ID != guestWelcome.PlayerID || !aborted.Players[0].IsHost {
		log.Fatalf("explicit leave did not abort/migrate correctly: %+v", aborted)
	}
	_ = guest.Close(websocket.StatusNormalClosure, "")
}

func runMultiDeck(base string) {
	code := mustCreate(base)
	host, guest := mustDial(base, code), mustDial(base, code)
	_ = join(host, "MixHost")
	_ = join(guest, "MixGuest")
	drain(host)
	drain(guest)
	settings := map[string]any{
		"mode": "coop", "deckIds": []string{"food", "movies"},
		"deckId": "food", "rounds": 4,
	}
	mustSend(host, "update_game_settings", settings)
	guestSetup := waitSettings(guest, 2, 4, 3*time.Second)
	if guestSetup.DeckIDs[0] != "food" || guestSetup.DeckIDs[1] != "movies" {
		log.Fatalf("guest settings out of order: %v", guestSetup.DeckIDs)
	}
	mustSend(host, "start_game", settings)
	counts := map[string]int{}
	questionIDs := map[string]bool{}
	for round := 0; round < 4; round++ {
		hs := waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
		_ = waitPhase(guest, "ROUND_SUBMIT", 3*time.Second)
		q := hs.CurrentRound.Question
		counts[q.DeckID]++
		if questionIDs[q.ID] {
			log.Fatalf("question repeated in mix: %s", q.ID)
		}
		questionIDs[q.ID] = true
		if !strings.HasPrefix(q.ID, q.DeckID+":") {
			log.Fatalf("question id %q not qualified by %q", q.ID, q.DeckID)
		}
		mustSend(guest, "submit_ranking", rankingPayload(hs, q.Options))
		mustSend(host, "submit_ranking", rankingPayload(hs, q.Options))
		_ = waitPhase(host, "ROUND_REVEAL", 3*time.Second)
		_ = waitPhase(guest, "ROUND_REVEAL", 3*time.Second)
		mustSend(host, "ready", map[string]any{})
		mustSend(guest, "ready", map[string]any{})
	}
	over := waitPhase(host, "GAME_OVER", 3*time.Second)
	_ = waitPhase(guest, "GAME_OVER", 3*time.Second)
	if counts["food"] != 2 || counts["movies"] != 2 {
		log.Fatalf("unbalanced mix: %v", counts)
	}
	if len(over.DeckIDs) != 2 || over.TotalRounds != 4 {
		log.Fatalf("game over lost mix settings: ids=%v rounds=%d", over.DeckIDs, over.TotalRounds)
	}
	// Rematch reuses the exact mix/settings.
	mustSend(host, "start_game", settings)
	rematch := waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
	if len(rematch.DeckIDs) != 2 || rematch.TotalRounds != 4 {
		log.Fatalf("rematch lost settings: ids=%v rounds=%d", rematch.DeckIDs, rematch.TotalRounds)
	}
	_ = host.Close(websocket.StatusNormalClosure, "")
	_ = guest.Close(websocket.StatusNormalClosure, "")
}

func runCoop(base string) {
	code := mustCreate(base)
	host, guest := mustDial(base, code), mustDial(base, code)
	hw := join(host, "Alice")
	gw := join(guest, "Bob")
	drain(host)
	drain(guest)
	mustSend(host, "start_game", map[string]any{"mode": "coop", "deckId": "food", "rounds": 1})
	hs := waitPhase(host, "ROUND_SUBMIT", 3*time.Second)
	gs := waitPhase(guest, "ROUND_SUBMIT", 3*time.Second)
	opts := hs.CurrentRound.Question.Options
	log.Printf("submit options: %v subject=%s", opts, hs.CurrentRound.SubjectID)
	if !hs.CurrentRound.CanSkip || gs.CurrentRound.CanSkip {
		log.Fatalf("skip eligibility host=%v guest=%v", hs.CurrentRound.CanSkip, gs.CurrentRound.CanSkip)
	}
	if hs.CurrentRound.SkipsRemaining != 8 || gs.CurrentRound.SkipsRemaining != 8 {
		log.Fatalf("initial skip counters host=%d guest=%d", hs.CurrentRound.SkipsRemaining, gs.CurrentRound.SkipsRemaining)
	}
	oldQuestionID := hs.CurrentRound.Question.ID
	oldDeadline := hs.CurrentRound.DeadlineMs

	// Predictor locks first; subject skip must clear that submission for everyone.
	mustSend(guest, "submit_ranking", rankingPayload(hs, opts))
	mustSend(host, "skip_question", map[string]any{
		"roundIndex": hs.CurrentRound.Index,
		"questionId": oldQuestionID,
	})
	hs = waitQuestionChange(host, oldQuestionID, 3*time.Second)
	gs = waitQuestionChange(guest, oldQuestionID, 3*time.Second)
	if hs.CurrentRound.Question.ID != gs.CurrentRound.Question.ID {
		log.Fatalf("replacement question mismatch host=%s guest=%s", hs.CurrentRound.Question.ID, gs.CurrentRound.Question.ID)
	}
	if hs.CurrentRound.Index != 0 || hs.CurrentRound.SubjectID != hw.PlayerID || hs.CurrentRound.DeadlineMs != oldDeadline {
		log.Fatalf("skip changed round context: %+v", hs.CurrentRound)
	}
	if len(hs.CurrentRound.Submitted) != 0 || len(gs.CurrentRound.Submitted) != 0 {
		log.Fatalf("skip did not clear submissions host=%v guest=%v", hs.CurrentRound.Submitted, gs.CurrentRound.Submitted)
	}
	if hs.CurrentRound.SkipsRemaining != 7 || gs.CurrentRound.SkipsRemaining != 8 ||
		!hs.CurrentRound.CanSkip || gs.CurrentRound.CanSkip {
		log.Fatalf("post-skip personalization host=(%d,%v) guest=(%d,%v)",
			hs.CurrentRound.SkipsRemaining, hs.CurrentRound.CanSkip,
			gs.CurrentRound.SkipsRemaining, gs.CurrentRound.CanSkip)
	}

	opts = hs.CurrentRound.Question.Options
	mustSend(guest, "submit_ranking", rankingPayload(hs, opts))
	mustSend(host, "submit_ranking", rankingPayload(hs, opts))

	reveal := waitPhase(host, "ROUND_REVEAL", 3*time.Second)
	_ = waitPhase(guest, "ROUND_REVEAL", 3*time.Second)
	log.Printf("reveal teamScore=%d", reveal.TeamScore)

	mustSend(host, "ready", map[string]any{})
	mustSend(guest, "ready", map[string]any{})
	over := waitPhase(host, "GAME_OVER", 3*time.Second)
	log.Printf("GAME_OVER teamScore=%d", over.TeamScore)
	if over.TeamScore != 2000 {
		log.Fatalf("expected team score 2000, got %d", over.TeamScore)
	}
	_ = gw
	log.Printf("coop ok")
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

func join(c *websocket.Conn, nick string) welcome {
	mustSend(c, "join_room", map[string]string{"nickname": nick})
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

// waitError expects the server to refuse a command, and returns its message.
func waitError(c *websocket.Conn, timeout time.Duration) string {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		env, err := readEnv(c, time.Until(deadline))
		must(err)
		if env.Type != "error" {
			continue
		}
		var payload struct {
			Message string `json:"message"`
		}
		must(json.Unmarshal(env.Payload, &payload))
		return payload.Message
	}
	log.Fatal("expected an error response")
	return ""
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

func waitQuestionChange(c *websocket.Conn, oldQuestionID string, timeout time.Duration) roomState {
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
		if s.Phase == "ROUND_SUBMIT" && s.CurrentRound != nil &&
			s.CurrentRound.Question.ID != oldQuestionID {
			return s
		}
	}
	log.Fatal("timeout waiting for replacement question")
	return roomState{}
}

func waitPaused(c *websocket.Conn, paused bool, timeout time.Duration) roomState {
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
		if s.Paused == paused {
			return s
		}
	}
	log.Fatalf("timeout waiting for paused=%v", paused)
	return roomState{}
}

func waitSettings(c *websocket.Conn, deckCount, rounds int, timeout time.Duration) roomState {
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
		if len(s.DeckIDs) == deckCount && s.TotalRounds == rounds {
			return s
		}
	}
	log.Fatal("timeout waiting for synchronized settings")
	return roomState{}
}

func must(err error) {
	if err != nil {
		log.Fatal(err)
	}
}
