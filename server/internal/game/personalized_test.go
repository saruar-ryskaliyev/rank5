package game

import (
	"errors"
	"sort"
	"testing"
	"time"
)

func threePlayerState() *State {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Maya", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Noah", Connected: true},
		{ID: "p3", Nickname: "Iris", Connected: true},
	}
	return s
}

func optionsQuestion(id string) Question {
	return Question{ID: id, Prompt: "Best breakfast?", Options: []string{"a", "b", "c", "d", "e"}}
}

func playersQuestion(id string) Question {
	return Question{ID: id, Kind: QuestionKindPlayers, Prompt: "Who is most likely to oversleep?"}
}

func TestRenderReplacesSubjectPlaceholderEachRound(t *testing.T) {
	s := threePlayerState()
	questions := []Question{
		{ID: "q1", Prompt: "What would {subject} grab first?", Options: []string{"a", "b", "c", "d", "e"}},
		{ID: "q2", Prompt: "What would {subject} grab first?", Options: []string{"a", "b", "c", "d", "e"}},
	}
	if err := s.StartGame("p1", ModeCoop, []string{"food"}, 2, questions, nil); err != nil {
		t.Fatal(err)
	}

	first := s.CurrentRound
	if first.Question.Prompt != "What would Maya grab first?" {
		t.Fatalf("round 1 prompt = %q", first.Question.Prompt)
	}
	if first.Template.Prompt != "What would {subject} grab first?" {
		t.Fatalf("template was personalized: %q", first.Template.Prompt)
	}

	playRoundThroughReveal(t, s)
	for _, id := range []string{"p1", "p2", "p3"} {
		_ = s.MarkReady(id)
	}

	if s.CurrentRound.SubjectID != "p2" {
		t.Fatalf("round 2 subject = %q, want p2", s.CurrentRound.SubjectID)
	}
	if s.CurrentRound.Question.Prompt != "What would Noah grab first?" {
		t.Fatalf("round 2 prompt = %q", s.CurrentRound.Question.Prompt)
	}
}

func TestPlayersQuestionUsesRoomPlayersAsOptions(t *testing.T) {
	s := threePlayerState()
	if err := s.StartGame("p1", ModeCoop, []string{"most_likely"}, 1, []Question{playersQuestion("q1")}, nil); err != nil {
		t.Fatal(err)
	}
	got := append([]string(nil), s.CurrentRound.Question.Options...)
	sort.Strings(got)
	want := []string{"Iris", "Maya", "Noah"}
	if len(got) != len(want) {
		t.Fatalf("options = %v, want the three players", got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("options = %v, want %v", got, want)
		}
	}
	// The rendered options must be rankable by the normal submit path.
	if err := s.SubmitEntry("p1", s.CurrentRound.Question.Options); err != nil {
		t.Fatalf("subject could not rank the players: %v", err)
	}
}

func TestPlayersQuestionOptionsAreDistinctWhenNicknamesCollide(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Sam", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Sam", Connected: true},
		{ID: "p3", Nickname: "Sam", Connected: true},
	}
	if err := s.StartGame("p1", ModeCoop, []string{"most_likely"}, 1, []Question{playersQuestion("q1")}, nil); err != nil {
		t.Fatal(err)
	}
	options := s.CurrentRound.Question.Options
	seen := map[string]bool{}
	for _, o := range options {
		if seen[o] {
			t.Fatalf("duplicate option %q in %v", o, options)
		}
		seen[o] = true
	}
	if len(options) != 3 {
		t.Fatalf("options = %v, want 3", options)
	}
}

func TestPlayersQuestionsDroppedBelowThreePlayers(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Maya", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Noah", Connected: true},
	}
	questions := []Question{playersQuestion("q1"), optionsQuestion("q2")}
	if err := s.StartGame("p1", ModeCoop, []string{"mix"}, 2, questions, nil); err != nil {
		t.Fatal(err)
	}
	if s.TotalRounds != 1 {
		t.Fatalf("totalRounds = %d, want 1 after dropping the players question", s.TotalRounds)
	}
	if s.CurrentRound.Question.ID != "q2" {
		t.Fatalf("played question %q, want the options question q2", s.CurrentRound.Question.ID)
	}
	for _, q := range s.Questions {
		if q.Kind == QuestionKindPlayers {
			t.Fatal("a players question survived a two-player start")
		}
	}
}

func TestStartGameRejectsPlayersOnlyDeckBelowThreePlayers(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "p1", Nickname: "Maya", IsHost: true, Connected: true},
		{ID: "p2", Nickname: "Noah", Connected: true},
	}
	err := s.StartGame("p1", ModeCoop, []string{"most_likely"}, 3,
		[]Question{playersQuestion("q1"), playersQuestion("q2"), playersQuestion("q3")}, nil)
	if !errors.Is(err, ErrNeedMorePlayers) {
		t.Fatalf("err = %v, want ErrNeedMorePlayers", err)
	}
	if s.Phase != PhaseLobby {
		t.Fatalf("phase = %s, want the room to stay in the lobby", s.Phase)
	}
	if len(s.SubjectOrder) != 0 || s.CurrentRound != nil {
		t.Fatal("a rejected start must not leave round state behind")
	}
}

func TestSkipRendersReplacementAndDefersTemplate(t *testing.T) {
	s := threePlayerState()
	questions := []Question{
		{ID: "q1", Prompt: "{subject} ranks breakfast", Options: []string{"a", "b", "c", "d", "e"}},
		{ID: "q2", Prompt: "{subject} ranks dinner", Options: []string{"a", "b", "c", "d", "e"}},
	}
	if err := s.StartGame("p1", ModeCoop, []string{"food"}, 2, questions, nil); err != nil {
		t.Fatal(err)
	}
	round := s.CurrentRound
	if err := s.SkipQuestion("p1", round.Index, round.Question.ID); err != nil {
		t.Fatal(err)
	}
	if s.CurrentRound.Question.Prompt != "Maya ranks dinner" {
		t.Fatalf("replacement prompt = %q", s.CurrentRound.Question.Prompt)
	}
	// The deferred question returns as an unrendered template so the next
	// subject sees their own name.
	deferred := s.Questions[len(s.Questions)-1]
	if deferred.ID != "q1" || deferred.Prompt != "{subject} ranks breakfast" {
		t.Fatalf("deferred question = %+v, want the q1 template", deferred)
	}
}

func TestSubjectDepartureRerendersPlayersQuestion(t *testing.T) {
	s := threePlayerState()
	s.Players = append(s.Players, &Player{ID: "p4", Nickname: "Theo", Connected: true})
	if err := s.StartGame("p1", ModeCoop, []string{"most_likely"}, 2,
		[]Question{playersQuestion("q1"), playersQuestion("q2")}, nil); err != nil {
		t.Fatal(err)
	}
	if s.CurrentRound.SubjectID != "p1" {
		t.Fatalf("subject = %q, want p1", s.CurrentRound.SubjectID)
	}

	if !s.RemovePlayer("p1", time.Now()) {
		t.Fatal("expected the subject to be removed")
	}
	round := s.CurrentRound
	if round.SubjectID == "p1" {
		t.Fatal("subject was not reassigned")
	}
	for _, o := range round.Question.Options {
		if o == "Maya" {
			t.Fatalf("options still contain the departed player: %v", round.Question.Options)
		}
	}
	if len(round.Question.Options) != 3 {
		t.Fatalf("options = %v, want the three remaining players", round.Question.Options)
	}
}

func TestPlayersQuestionFallsBackWhenGroupShrinks(t *testing.T) {
	s := threePlayerState()
	// Round 1 is an options question; the reserve holds a players question
	// which stops being playable once the room drops to two people.
	questions := []Question{optionsQuestion("q1"), playersQuestion("q2"), optionsQuestion("q3")}
	if err := s.StartGame("p1", ModeCoop, []string{"mix"}, 3, questions, nil); err != nil {
		t.Fatal(err)
	}
	playRoundThroughReveal(t, s)
	if !s.RemovePlayer("p3", time.Now()) {
		t.Fatal("expected p3 to be removed")
	}
	for _, id := range []string{"p1", "p2"} {
		_ = s.MarkReady(id)
	}
	if s.Phase != PhaseRoundSubmit {
		t.Fatalf("phase = %s, want a second round", s.Phase)
	}
	if s.CurrentRound.Question.Kind == QuestionKindPlayers {
		t.Fatal("a players question was played with only two players left")
	}
}

func playRoundThroughReveal(t *testing.T, s *State) {
	t.Helper()
	options := s.CurrentRound.Question.Options
	for _, p := range s.Players {
		if !p.Connected || s.CurrentRound.Submitted[p.ID] {
			continue
		}
		if err := s.SubmitEntry(p.ID, options); err != nil {
			t.Fatalf("submit for %s: %v", p.ID, err)
		}
	}
	if s.Phase != PhaseRoundReveal {
		t.Fatalf("phase = %s, want reveal", s.Phase)
	}
}
