package game

import (
	"errors"
	"testing"
	"time"
)

func TestValidateMusicSettings(t *testing.T) {
	track, scope, err := ValidateMusicSettings(MusicTrackEasyGlow, "")
	if err != nil || track != MusicTrackEasyGlow || scope != MusicScopeLobbyAndGame {
		t.Fatalf("expected valid track with default scope, got track=%q scope=%q err=%v", track, scope, err)
	}
	if _, _, err := ValidateMusicSettings("unknown", MusicScopeLobby); err == nil {
		t.Fatal("expected unknown track to fail validation")
	}
	if _, _, err := ValidateMusicSettings("", "everywhere"); err == nil {
		t.Fatal("expected unknown scope to fail validation")
	}
}

func TestDisplacementPerfect(t *testing.T) {
	a := []string{"A", "B", "C", "D", "E"}
	if d := Displacement(a, a); d != 0 {
		t.Fatalf("got %d want 0", d)
	}
	if s := ScorePrediction(a, a); s != 2000 {
		t.Fatalf("got %d want 2000", s)
	}
}

func TestDisplacementReverse(t *testing.T) {
	a := []string{"A", "B", "C", "D", "E"}
	p := []string{"E", "D", "C", "B", "A"}
	d := Displacement(a, p)
	if d != 12 {
		t.Fatalf("got %d want 12", d)
	}
	// Official formula: 2000 - 50*12 = 1400
	if s := ScoreFromDisplacement(d); s != 1400 {
		t.Fatalf("got %d want 1400", s)
	}
}

func TestOfficialExampleDisplacement4Scores1800(t *testing.T) {
	// From rank5.io/howTo: prediction off by total displacement 4 → 1800 pts.
	actual := []string{"fail", "kindness", "friends", "grew", "music"}
	predicted := []string{"friends", "fail", "kindness", "grew", "music"}
	// friends: 2→0 (2), fail: 0→1 (1), kindness: 1→2 (1) = 4
	d := Displacement(actual, predicted)
	if d != 4 {
		t.Fatalf("displacement got %d want 4", d)
	}
	if s := ScorePrediction(actual, predicted); s != 1800 {
		t.Fatalf("score got %d want 1800", s)
	}
}

func TestDisplacementPartial(t *testing.T) {
	a := []string{"A", "B", "C", "D", "E"}
	p := []string{"A", "C", "B", "D", "E"} // swap B and C → displacement 2
	if d := Displacement(a, p); d != 2 {
		t.Fatalf("got %d want 2", d)
	}
	if s := ScorePrediction(a, p); s != 1900 {
		t.Fatalf("got %d want 1900", s)
	}
}

func TestSimultaneousSubmitFlow(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Alice", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Bob", Connected: true},
	}
	qs := []Question{{
		ID: "q1", Prompt: "Fav food?",
		Options: []string{"Pizza", "Sushi", "Tacos", "Salad", "Mochi"},
	}}
	if err := s.StartGame("p1", ModeCoop, []string{"food"}, 1, qs, nil); err != nil {
		t.Fatal(err)
	}
	if s.Phase != PhaseRoundSubmit {
		t.Fatalf("phase %s", s.Phase)
	}
	ranking := []string{"Pizza", "Sushi", "Tacos", "Salad", "Mochi"}
	// Predictor can submit before subject.
	if err := s.SubmitEntry("p2", ranking); err != nil {
		t.Fatal(err)
	}
	if s.Phase != PhaseRoundSubmit {
		t.Fatalf("should still be submit, got %s", s.Phase)
	}
	if err := s.SubmitEntry("p1", ranking); err != nil {
		t.Fatal(err)
	}
	if s.Phase != PhaseRoundReveal {
		t.Fatalf("phase %s", s.Phase)
	}
	if s.CurrentRound.Scores["p2"] != 2000 {
		t.Fatalf("score %d", s.CurrentRound.Scores["p2"])
	}
	if s.TeamScore != 2000 {
		t.Fatalf("team %d", s.TeamScore)
	}
}

func TestForceSubmitDeadline(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Alice", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Bob", Connected: true},
	}
	qs := []Question{{
		ID: "q1", Prompt: "Q?",
		Options: []string{"A", "B", "C", "D", "E"},
	}}
	_ = s.StartGame("p1", ModeCoop, []string{"test"}, 1, qs, nil)
	_ = s.SubmitEntry("p1", []string{"A", "B", "C", "D", "E"})
	if err := s.ForceSubmitDeadline(); err != nil {
		t.Fatal(err)
	}
	if s.Phase != PhaseRoundReveal {
		t.Fatalf("phase %s", s.Phase)
	}
	if !s.CurrentRound.Submitted["p2"] {
		t.Fatal("p2 should be auto-submitted")
	}
	// Default options match subject ranking → perfect score
	if s.CurrentRound.Scores["p2"] != 2000 {
		t.Fatalf("score %d", s.CurrentRound.Scores["p2"])
	}
}

func TestReadyGating(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Alice", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Bob", Connected: true},
	}
	qs := []Question{
		{ID: "q1", Prompt: "Q1?", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "q2", Prompt: "Q2?", Options: []string{"A", "B", "C", "D", "E"}},
	}
	_ = s.StartGame("p1", ModeCoop, []string{"test"}, 2, qs, nil)
	opts := []string{"A", "B", "C", "D", "E"}
	_ = s.SubmitEntry("p1", opts)
	_ = s.SubmitEntry("p2", opts)
	if s.Phase != PhaseRoundReveal {
		t.Fatalf("phase %s", s.Phase)
	}
	if err := s.MarkReady("p1"); err != nil {
		t.Fatal(err)
	}
	if s.Phase != PhaseRoundReveal {
		t.Fatal("should wait for p2")
	}
	if err := s.MarkReady("p2"); err != nil {
		t.Fatal(err)
	}
	if s.Phase != PhaseRoundSubmit {
		t.Fatalf("expected next round submit, got %s", s.Phase)
	}
	if s.RoundIndex != 1 {
		t.Fatalf("round %d", s.RoundIndex)
	}
}

func TestVersusScoring(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Alice", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Bob", Connected: true},
		{ID: "p3", Nickname: "Carol", Connected: true},
	}
	qs := []Question{{
		ID: "q1", Prompt: "Q?",
		Options: []string{"A", "B", "C", "D", "E"},
	}}
	if err := s.StartGame("p1", ModeVersus, []string{"test"}, 1, qs, nil); err != nil {
		t.Fatal(err)
	}
	_ = s.SubmitEntry("p1", []string{"A", "B", "C", "D", "E"})
	_ = s.SubmitEntry("p2", []string{"A", "B", "C", "D", "E"})
	_ = s.SubmitEntry("p3", []string{"E", "D", "C", "B", "A"})
	if s.Phase != PhaseRoundReveal {
		t.Fatalf("phase %s", s.Phase)
	}
	if s.PlayerByID("p2").Score != 2000 {
		t.Fatalf("p2 score %d", s.PlayerByID("p2").Score)
	}
	if s.PlayerByID("p3").Score != 1400 {
		t.Fatalf("p3 score %d want 1400", s.PlayerByID("p3").Score)
	}
}

func TestSubjectSkipReplacesQuestionAndClearsPartialSubmissions(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "subject", IsHost: true, Connected: true},
		{ID: "predictor", Connected: true},
	}
	questions := []Question{
		{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "q2", Options: []string{"F", "G", "H", "I", "J"}},
		{ID: "q3", Options: []string{"K", "L", "M", "N", "O"}},
		{ID: "q4", Options: []string{"P", "Q", "R", "S", "T"}},
	}
	if err := s.StartGame("subject", ModeCoop, []string{"test"}, 2, questions, nil); err != nil {
		t.Fatal(err)
	}
	oldRound := s.CurrentRound
	oldDeadline := oldRound.Deadline
	if err := s.SubmitEntryForQuestion("predictor", oldRound.Index, oldRound.Question.ID, oldRound.Question.Options); err != nil {
		t.Fatal(err)
	}
	if err := s.SkipQuestion("subject", oldRound.Index, oldRound.Question.ID); err != nil {
		t.Fatal(err)
	}

	got := s.CurrentRound
	if got.Question.ID != "q2" || got.Index != oldRound.Index || got.SubjectID != oldRound.SubjectID {
		t.Fatalf("unexpected replacement round: %+v", got)
	}
	if !got.Deadline.Equal(oldDeadline) {
		t.Fatalf("deadline changed from %s to %s", oldDeadline, got.Deadline)
	}
	if len(got.Submitted) != 0 || len(got.Predictions) != 0 || len(got.SubjectRanking) != 0 {
		t.Fatalf("old answers survived skip: %+v", got)
	}
	if s.SkipsRemaining["subject"] != DefaultSkipsPerPlayer-1 ||
		s.SkipsRemaining["predictor"] != DefaultSkipsPerPlayer {
		t.Fatalf("skip allowances = %+v", s.SkipsRemaining)
	}
	if s.Phase != PhaseRoundSubmit || !s.CanSkipQuestion("subject") || s.CanSkipQuestion("predictor") {
		t.Fatalf("unexpected post-skip eligibility: phase=%s subject=%v predictor=%v", s.Phase, s.CanSkipQuestion("subject"), s.CanSkipQuestion("predictor"))
	}
}

func TestSkipAuthorizationAndStaleCommands(t *testing.T) {
	newGame := func() *State {
		s := NewState("ABCD")
		s.Players = []*Player{
			{ID: "subject", IsHost: true, Connected: true},
			{ID: "predictor", Connected: true},
		}
		questions := []Question{
			{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}},
			{ID: "q2", Options: []string{"F", "G", "H", "I", "J"}},
		}
		if err := s.StartGame("subject", ModeCoop, []string{"test"}, 1, questions, nil); err != nil {
			t.Fatal(err)
		}
		return s
	}

	t.Run("non-subject", func(t *testing.T) {
		s := newGame()
		r := s.CurrentRound
		if err := s.SkipQuestion("predictor", r.Index, r.Question.ID); !errors.Is(err, ErrOnlySubjectSkip) {
			t.Fatalf("error = %v", err)
		}
	})
	t.Run("stale question", func(t *testing.T) {
		s := newGame()
		if err := s.SkipQuestion("subject", 0, "old"); !errors.Is(err, ErrStaleQuestion) {
			t.Fatalf("error = %v", err)
		}
		if s.SkipsRemaining["subject"] != DefaultSkipsPerPlayer {
			t.Fatal("stale command spent a skip")
		}
	})
	t.Run("subject already submitted", func(t *testing.T) {
		s := newGame()
		r := s.CurrentRound
		if err := s.SubmitEntry("subject", r.Question.Options); err != nil {
			t.Fatal(err)
		}
		if err := s.SkipQuestion("subject", r.Index, r.Question.ID); !errors.Is(err, ErrAlreadySubmitted) {
			t.Fatalf("error = %v", err)
		}
	})
	t.Run("no remaining allowance", func(t *testing.T) {
		s := newGame()
		r := s.CurrentRound
		s.SkipsRemaining["subject"] = 0
		if err := s.SkipQuestion("subject", r.Index, r.Question.ID); !errors.Is(err, ErrNoSkipsRemaining) {
			t.Fatalf("error = %v", err)
		}
	})
}

func TestSkipDefersQuestionAndPreservesEnoughQuestionsToFinish(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "subject", IsHost: true, Connected: true},
		{ID: "predictor", Connected: true},
	}
	questions := []Question{
		{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "q2", Options: []string{"F", "G", "H", "I", "J"}},
	}
	if err := s.StartGame("subject", ModeCoop, []string{"test"}, 2, questions, nil); err != nil {
		t.Fatal(err)
	}
	r := s.CurrentRound
	if !s.CanSkipQuestion("subject") {
		t.Fatal("another unplayed question should be available as a replacement")
	}
	if err := s.SkipQuestion("subject", r.Index, r.Question.ID); err != nil {
		t.Fatal(err)
	}
	if s.CurrentRound.Question.ID != "q2" {
		t.Fatalf("replacement = %q, want q2", s.CurrentRound.Question.ID)
	}
	if err := s.ForceSubmitDeadline(); err != nil {
		t.Fatal(err)
	}
	s.CurrentRound.Deadline = time.Now().Add(-time.Second)
	if err := s.ForceNextRound(); err != nil {
		t.Fatal(err)
	}
	if s.CurrentRound.Question.ID != "q1" {
		t.Fatalf("deferred question = %q, want q1", s.CurrentRound.Question.ID)
	}
}

func TestSkipRemainsAvailableForNextSubjectWithFiveOfSixRounds(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "a", IsHost: true, Connected: true},
		{ID: "b", Connected: true},
	}
	questions := []Question{
		{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "q2", Options: []string{"F", "G", "H", "I", "J"}},
		{ID: "q3", Options: []string{"K", "L", "M", "N", "O"}},
		{ID: "q4", Options: []string{"P", "Q", "R", "S", "T"}},
		{ID: "q5", Options: []string{"U", "V", "W", "X", "Y"}},
		{ID: "q6", Options: []string{"a", "b", "c", "d", "e"}},
	}
	if err := s.StartGame("a", ModeCoop, []string{"test"}, 5, questions, nil); err != nil {
		t.Fatal(err)
	}
	first := s.CurrentRound
	if err := s.SkipQuestion("a", first.Index, first.Question.ID); err != nil {
		t.Fatal(err)
	}
	if !s.CanSkipQuestion("a") {
		t.Fatal("skip button should remain available after one replacement")
	}
	if err := s.ForceSubmitDeadline(); err != nil {
		t.Fatal(err)
	}
	if err := s.ForceNextRound(); err != nil {
		t.Fatal(err)
	}
	if s.CurrentRound.SubjectID != "b" || !s.CanSkipQuestion("b") {
		t.Fatalf("next subject eligibility: subject=%q canSkip=%v", s.CurrentRound.SubjectID, s.CanSkipQuestion("b"))
	}
	if s.SkipsRemaining["a"] != DefaultSkipsPerPlayer-1 || s.SkipsRemaining["b"] != DefaultSkipsPerPlayer {
		t.Fatalf("personal allowances = %+v", s.SkipsRemaining)
	}
}

func TestSkipDoesNotRepeatAQuestionWithinRound(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "subject", IsHost: true, Connected: true},
		{ID: "predictor", Connected: true},
	}
	questions := []Question{
		{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "q2", Options: []string{"F", "G", "H", "I", "J"}},
		{ID: "q3", Options: []string{"K", "L", "M", "N", "O"}},
	}
	if err := s.StartGame("subject", ModeCoop, []string{"test"}, 3, questions, nil); err != nil {
		t.Fatal(err)
	}
	seen := map[string]bool{s.CurrentRound.Question.ID: true}
	for i := 0; i < len(questions)-1; i++ {
		r := s.CurrentRound
		if err := s.SkipQuestion("subject", r.Index, r.Question.ID); err != nil {
			t.Fatal(err)
		}
		if seen[s.CurrentRound.Question.ID] {
			t.Fatalf("question repeated within round: %q", s.CurrentRound.Question.ID)
		}
		seen[s.CurrentRound.Question.ID] = true
	}
	if s.CanSkipQuestion("subject") {
		t.Fatal("skip should stop after every distinct question was shown in the round")
	}
}

func TestSkipRejectsDelayedRankingAndRematchResetsAllowance(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "subject", IsHost: true, Connected: true},
		{ID: "predictor", Connected: true},
	}
	questions := []Question{
		{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}},
		{ID: "q2", Options: []string{"A", "B", "C", "D", "E"}},
	}
	if err := s.StartGame("subject", ModeCoop, []string{"test"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	old := *s.CurrentRound
	if err := s.SkipQuestion("subject", old.Index, old.Question.ID); err != nil {
		t.Fatal(err)
	}
	if err := s.SubmitEntryForQuestion("predictor", old.Index, old.Question.ID, old.Question.Options); !errors.Is(err, ErrStaleQuestion) {
		t.Fatalf("delayed ranking error = %v", err)
	}
	if err := s.ForceSubmitDeadline(); err != nil {
		t.Fatal(err)
	}
	s.CurrentRound.Deadline = time.Now().Add(-time.Second)
	if err := s.ForceNextRound(); err != nil {
		t.Fatal(err)
	}
	if err := s.StartGame("subject", ModeCoop, []string{"test"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	if s.SkipsRemaining["subject"] != DefaultSkipsPerPlayer {
		t.Fatalf("rematch skips = %d", s.SkipsRemaining["subject"])
	}
}

func TestTwoPlayerDisconnectPausesAndReconnectRestoresDeadline(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "host", IsHost: true, Connected: true},
		{ID: "guest", Connected: true},
	}
	questions := []Question{{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}}}
	if err := s.StartGame("host", ModeCoop, []string{"test"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	s.CurrentRound.Deadline = now.Add(30 * time.Second)
	s.PlayerByID("host").Connected = false
	if !s.PauseForReconnect(now, ReconnectGrace) {
		t.Fatal("expected game to pause")
	}
	if !s.Paused || !s.CurrentRound.Deadline.IsZero() {
		t.Fatalf("pause state=%v deadline=%s", s.Paused, s.CurrentRound.Deadline)
	}
	if err := s.SubmitEntry("guest", questions[0].Options); !errors.Is(err, ErrGamePaused) {
		t.Fatalf("solo submission error = %v", err)
	}

	resumeAt := now.Add(10 * time.Second)
	s.PlayerByID("host").Connected = true
	if !s.ResumeFromPause(resumeAt) {
		t.Fatal("expected game to resume")
	}
	if s.Paused || !s.CurrentRound.Deadline.Equal(resumeAt.Add(30*time.Second)) {
		t.Fatalf("resume state=%v deadline=%s", s.Paused, s.CurrentRound.Deadline)
	}
}

func TestExplicitHostLeaveAbortsTwoPlayerGameWithoutGameOver(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "host", IsHost: true, Connected: true},
		{ID: "guest", Connected: true},
	}
	questions := []Question{{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}}}
	if err := s.StartGame("host", ModeCoop, []string{"test"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	if !s.RemovePlayer("host", time.Now()) {
		t.Fatal("host was not removed")
	}
	if s.Phase != PhaseLobby || s.AbortReason != "not_enough_players" || s.CurrentRound != nil {
		t.Fatalf("unexpected abort state phase=%s reason=%q round=%+v", s.Phase, s.AbortReason, s.CurrentRound)
	}
	if len(s.FinishedRounds) != 0 || len(s.Players) != 1 || !s.Players[0].IsHost {
		t.Fatalf("unexpected remaining state players=%+v finished=%+v", s.Players, s.FinishedRounds)
	}
}

func TestExplicitHostLeaveMigratesHostWhenQuorumRemains(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "host", IsHost: true, Connected: true},
		{ID: "guest1", Connected: true},
		{ID: "guest2", Connected: true},
	}
	questions := []Question{{ID: "q1", Options: []string{"A", "B", "C", "D", "E"}}}
	if err := s.StartGame("host", ModeCoop, []string{"test"}, 1, questions, nil); err != nil {
		t.Fatal(err)
	}
	if !s.RemovePlayer("host", time.Now()) {
		t.Fatal("host was not removed")
	}
	if s.Phase != PhaseRoundSubmit || s.ConnectedCount() != 2 {
		t.Fatalf("game did not continue: phase=%s connected=%d", s.Phase, s.ConnectedCount())
	}
	if host := s.Host(); host == nil || host.ID != "guest1" {
		t.Fatalf("new host = %+v", host)
	}
	if s.CurrentRound.SubjectID == "host" || len(s.CurrentRound.Submitted) != 0 {
		t.Fatalf("departed subject survived: %+v", s.CurrentRound)
	}
}
