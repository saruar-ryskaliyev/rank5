package game_test

import (
	"testing"

	"github.com/saruar/rank5/server/internal/game"
)

func TestFinishedRoundsAccumulatedOncePerGame(t *testing.T) {
	s := game.NewState("ABCD")
	s.Players = []*game.Player{
		{ID: "h", Nickname: "Host", IsHost: true, Connected: true},
		{ID: "g", Nickname: "Guest", Connected: true},
	}
	qs := []game.Question{
		{ID: "q1", Prompt: "Q1", Options: []string{"a", "b", "c", "d", "e"}},
		{ID: "q2", Prompt: "Q2", Options: []string{"a", "b", "c", "d", "e"}},
	}
	if err := s.StartGame("h", game.ModeVersus, []string{"food"}, 2, qs, nil); err != nil {
		t.Fatal(err)
	}
	playRound := func() {
		_ = s.SubmitEntry("h", qs[0].Options) // subject depends on order; submit both rankings of current options
		opts := s.CurrentRound.Question.Options
		_ = s.SubmitEntry("h", opts)
		_ = s.SubmitEntry("g", opts)
		if s.Phase != game.PhaseRoundReveal {
			// one of the submits may have been duplicate if host was subject twice — force
			if s.Phase == game.PhaseRoundSubmit {
				_ = s.ForceSubmitDeadline()
			}
		}
		if s.Phase != game.PhaseRoundReveal {
			t.Fatalf("want reveal got %s", s.Phase)
		}
		_ = s.MarkReady("h")
		_ = s.MarkReady("g")
	}
	playRound()
	if s.Phase == game.PhaseGameOver {
		if len(s.FinishedRounds) != 2 && len(s.FinishedRounds) != 1 {
			// after first round advance may still be in submit for round 2
		}
	}
	if s.Phase == game.PhaseRoundSubmit {
		playRound()
	}
	if s.Phase != game.PhaseGameOver {
		t.Fatalf("want game over got %s finished=%d", s.Phase, len(s.FinishedRounds))
	}
	if len(s.FinishedRounds) != 2 {
		t.Fatalf("want 2 finished rounds got %d", len(s.FinishedRounds))
	}
	if got := s.FinishedRounds[0].SubjectID; got != "h" {
		t.Fatalf("first round subject = %q, want h", got)
	}
	if got := s.FinishedRounds[1].SubjectID; got != "g" {
		t.Fatalf("second round subject = %q, want g", got)
	}

	// Rematch clears history.
	if err := s.StartGame("h", game.ModeVersus, []string{"food"}, 2, qs, nil); err != nil {
		t.Fatal(err)
	}
	if len(s.FinishedRounds) != 0 {
		t.Fatalf("rematch should clear finished rounds, got %d", len(s.FinishedRounds))
	}
}
