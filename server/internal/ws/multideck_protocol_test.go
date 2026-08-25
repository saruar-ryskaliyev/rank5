package ws

import (
	"context"
	"encoding/json"
	"reflect"
	"testing"

	"github.com/saruar/rank5/server/internal/game"
)

func TestStartGamePayloadLegacyDeckIDFallback(t *testing.T) {
	var payload StartGamePayload
	if err := json.Unmarshal([]byte(`{"mode":"coop","deckId":"food","rounds":6,"future":true}`), &payload); err != nil {
		t.Fatal(err)
	}
	if got := payload.CanonicalDeckIDs(); !reflect.DeepEqual(got, []string{"food"}) {
		t.Fatalf("canonical ids = %#v", got)
	}
}

func TestReconnectSnapshotPreservesMultiDeckSettings(t *testing.T) {
	state := game.NewState("ABCD")
	state.Mode = game.ModeCoop
	state.DeckID = "food"
	state.DeckIDs = []string{"food", "movies"}
	state.TotalRounds = 6
	state.SelectedDecks = []game.DeckInfo{{ID: "food", Name: "Food"}, {ID: "movies", Name: "Movies"}}
	state.Players = []*game.Player{{
		ID: "guest", Nickname: "Guest", Connected: false, ReconnectToken: "secret",
	}}
	slot := &connSlot{playerID: "pending-new", send: make(chan []byte, 4)}
	room := &Room{
		state: state,
		conns: map[string]*connSlot{"pending-new": slot},
	}

	room.doReconnect(slot, "guest", "secret")

	<-slot.send // welcome
	stateMessage := <-slot.send
	var env Envelope
	if err := json.Unmarshal(stateMessage, &env); err != nil {
		t.Fatal(err)
	}
	var snapshot RoomStateView
	if err := json.Unmarshal(env.Payload, &snapshot); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(snapshot.DeckIDs, []string{"food", "movies"}) || snapshot.TotalRounds != 6 {
		t.Fatalf("reconnect snapshot lost settings: %+v", snapshot)
	}
}

func TestStartGamePayloadPrefersCanonicalDeckIDs(t *testing.T) {
	var payload StartGamePayload
	if err := json.Unmarshal([]byte(`{"mode":"coop","deckIds":["food","movies"],"deckId":"legacy","rounds":6}`), &payload); err != nil {
		t.Fatal(err)
	}
	if got := payload.CanonicalDeckIDs(); !reflect.DeepEqual(got, []string{"food", "movies"}) {
		t.Fatalf("canonical ids = %#v", got)
	}
}

func TestSubjectSkipBroadcastsPersonalizedAuthoritativeState(t *testing.T) {
	state := game.NewState("ABCD")
	state.Players = []*game.Player{
		{ID: "subject", Nickname: "Subject", IsHost: true, Connected: true},
		{ID: "predictor", Nickname: "Predictor", Connected: true},
	}
	questions := []game.Question{
		{ID: "deck:q1", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "deck:q2", Options: []string{"F", "G", "H", "I", "J"}},
		{ID: "deck:q3", Options: []string{"K", "L", "M", "N", "O"}},
	}
	if err := state.StartGame("subject", game.ModeCoop, []string{"deck"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	subjectSlot := &connSlot{playerID: "subject", send: make(chan []byte, 8)}
	predictorSlot := &connSlot{playerID: "predictor", send: make(chan []byte, 8)}
	room := &Room{
		state: state,
		conns: map[string]*connSlot{
			"subject":   subjectSlot,
			"predictor": predictorSlot,
		},
	}

	beforeSubject := snapshotFor(state, "subject", nil)
	beforePredictor := snapshotFor(state, "predictor", nil)
	if !beforeSubject.CurrentRound.CanSkip || beforePredictor.CurrentRound.CanSkip {
		t.Fatalf("eligibility subject=%v predictor=%v", beforeSubject.CurrentRound.CanSkip, beforePredictor.CurrentRound.CanSkip)
	}
	old := state.CurrentRound
	rankingMessage, err := Encode(TypeSubmitRanking, RankingPayload{
		RoundIndex: old.Index,
		QuestionID: old.Question.ID,
		Ranking:    old.Question.Options,
	})
	if err != nil {
		t.Fatal(err)
	}
	room.handleMessage(roomEvent{playerID: "predictor", payload: rankingMessage})
	<-subjectSlot.send
	<-predictorSlot.send

	skipMessage, err := Encode(TypeSkipQuestion, SkipQuestionPayload{
		RoundIndex: old.Index,
		QuestionID: old.Question.ID,
	})
	if err != nil {
		t.Fatal(err)
	}
	room.handleMessage(roomEvent{playerID: "subject", payload: skipMessage})
	subjectState := decodeRoomStateMessage(t, <-subjectSlot.send)
	predictorState := decodeRoomStateMessage(t, <-predictorSlot.send)

	if subjectState.CurrentRound.Question.ID == old.Question.ID ||
		subjectState.CurrentRound.Question.ID != predictorState.CurrentRound.Question.ID {
		t.Fatalf("replacement mismatch: subject=%q predictor=%q", subjectState.CurrentRound.Question.ID, predictorState.CurrentRound.Question.ID)
	}
	if len(subjectState.CurrentRound.Submitted) != 0 || len(predictorState.CurrentRound.Submitted) != 0 {
		t.Fatalf("submissions were not cleared: subject=%v predictor=%v", subjectState.CurrentRound.Submitted, predictorState.CurrentRound.Submitted)
	}
	if subjectState.CurrentRound.SkipsRemaining != game.DefaultSkipsPerPlayer-1 ||
		predictorState.CurrentRound.SkipsRemaining != game.DefaultSkipsPerPlayer {
		t.Fatalf("personal counters subject=%d predictor=%d", subjectState.CurrentRound.SkipsRemaining, predictorState.CurrentRound.SkipsRemaining)
	}
	if !subjectState.CurrentRound.CanSkip || predictorState.CurrentRound.CanSkip {
		t.Fatalf("post-skip eligibility subject=%v predictor=%v", subjectState.CurrentRound.CanSkip, predictorState.CurrentRound.CanSkip)
	}

	// Replaying the old command is stale and cannot consume another skip.
	room.handleMessage(roomEvent{playerID: "subject", payload: skipMessage})
	var errorEnvelope Envelope
	if err := json.Unmarshal(<-subjectSlot.send, &errorEnvelope); err != nil {
		t.Fatal(err)
	}
	if errorEnvelope.Type != TypeError || state.SkipsRemaining["subject"] != game.DefaultSkipsPerPlayer-1 {
		t.Fatalf("duplicate skip result type=%q skips=%d", errorEnvelope.Type, state.SkipsRemaining["subject"])
	}
}

func decodeRoomStateMessage(t *testing.T, message []byte) RoomStateView {
	t.Helper()
	var env Envelope
	if err := json.Unmarshal(message, &env); err != nil {
		t.Fatal(err)
	}
	if env.Type != TypeRoomState {
		t.Fatalf("message type = %q, want %q", env.Type, TypeRoomState)
	}
	var state RoomStateView
	if err := json.Unmarshal(env.Payload, &state); err != nil {
		t.Fatal(err)
	}
	return state
}

func TestTwoPlayerDisconnectPausesAndReconnectResumesRoom(t *testing.T) {
	state := game.NewState("ABCD")
	state.Players = []*game.Player{
		{ID: "host", Nickname: "Host", IsHost: true, Connected: true, ReconnectToken: "host-token"},
		{ID: "guest", Nickname: "Guest", Connected: true, ReconnectToken: "guest-token"},
	}
	questions := []game.Question{{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}}}
	if err := state.StartGame("host", game.ModeCoop, []string{"deck"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	hostSlot := &connSlot{playerID: "host", send: make(chan []byte, 8), cancel: func() {}}
	guestSlot := &connSlot{playerID: "guest", send: make(chan []byte, 8), cancel: func() {}}
	room := &Room{
		state: state,
		conns: map[string]*connSlot{"host": hostSlot, "guest": guestSlot},
	}
	defer room.clearTimer()
	defer room.clearPauseTimer()

	room.handleDisconnect("host", hostSlot)
	paused := decodeRoomStateMessage(t, <-guestSlot.send)
	if !paused.Paused || paused.ReconnectByMs == 0 || state.CurrentRound.Deadline.IsZero() == false {
		t.Fatalf("unexpected pause snapshot/state: %+v deadline=%s", paused, state.CurrentRound.Deadline)
	}

	old := state.CurrentRound
	message, err := Encode(TypeSubmitRanking, RankingPayload{
		RoundIndex: old.Index, QuestionID: old.Question.ID, Ranking: old.Question.Options,
	})
	if err != nil {
		t.Fatal(err)
	}
	room.handleMessage(roomEvent{playerID: "guest", payload: message})
	var errorEnvelope Envelope
	if err := json.Unmarshal(<-guestSlot.send, &errorEnvelope); err != nil {
		t.Fatal(err)
	}
	if errorEnvelope.Type != TypeError {
		t.Fatalf("solo command type = %q", errorEnvelope.Type)
	}

	reconnectSlot := &connSlot{
		playerID: "pending-new", send: make(chan []byte, 8), cancel: func() {},
	}
	room.conns["pending-new"] = reconnectSlot
	room.doReconnect(reconnectSlot, "host", "host-token")
	<-reconnectSlot.send // welcome
	resumedHost := decodeRoomStateMessage(t, <-reconnectSlot.send)
	resumedGuest := decodeRoomStateMessage(t, <-guestSlot.send)
	if resumedHost.Paused || resumedGuest.Paused || state.CurrentRound.Deadline.IsZero() {
		t.Fatalf("room did not resume: host=%+v guest=%+v deadline=%s", resumedHost, resumedGuest, state.CurrentRound.Deadline)
	}
}

func TestExplicitHostLeaveAbortsToLobbyAndMigratesHost(t *testing.T) {
	state := game.NewState("ABCD")
	state.Players = []*game.Player{
		{ID: "host", Nickname: "Host", IsHost: true, Connected: true},
		{ID: "guest", Nickname: "Guest", Connected: true},
	}
	questions := []game.Question{{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}}}
	if err := state.StartGame("host", game.ModeCoop, []string{"deck"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	hostSlot := &connSlot{playerID: "host", send: make(chan []byte, 4), cancel: func() {}}
	guestSlot := &connSlot{playerID: "guest", send: make(chan []byte, 4), cancel: func() {}}
	room := &Room{
		state: state,
		conns: map[string]*connSlot{"host": hostSlot, "guest": guestSlot},
	}
	defer room.clearTimer()
	defer room.clearPauseTimer()

	leave, err := Encode(TypeLeaveRoom, nil)
	if err != nil {
		t.Fatal(err)
	}
	room.handleMessage(roomEvent{playerID: "host", payload: leave, ctx: context.Background()})
	snapshot := decodeRoomStateMessage(t, <-guestSlot.send)
	if snapshot.Phase != game.PhaseLobby || snapshot.AbortReason != "not_enough_players" || len(snapshot.Players) != 1 {
		t.Fatalf("unexpected aborted snapshot: %+v", snapshot)
	}
	if !snapshot.Players[0].IsHost || snapshot.Players[0].ID != "guest" {
		t.Fatalf("host did not migrate: %+v", snapshot.Players)
	}
}

func TestStaleDisconnectCannotRemoveReplacementConnection(t *testing.T) {
	state := game.NewState("ABCD")
	state.Players = []*game.Player{{
		ID: "player", Nickname: "Player", IsHost: true, Connected: true,
	}}
	oldSlot := &connSlot{playerID: "player", send: make(chan []byte, 1), cancel: func() {}}
	newSlot := &connSlot{playerID: "player", send: make(chan []byte, 1), cancel: func() {}}
	room := &Room{
		state: state,
		conns: map[string]*connSlot{"player": newSlot},
	}

	room.handleDisconnect("player", oldSlot)
	if room.conns["player"] != newSlot || !state.Players[0].Connected {
		t.Fatal("stale read-pump disconnect removed the replacement connection")
	}
}
