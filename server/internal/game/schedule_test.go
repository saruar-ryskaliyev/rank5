package game

import (
	"math/rand"
	"strings"
	"testing"
)

func testDeck(id string, count int) Deck {
	questions := make([]Question, count)
	for i := range questions {
		questions[i] = Question{ID: "q" + string(rune('1'+i)), Prompt: id, Options: []string{"a", "b", "c", "d", "e"}}
	}
	return Deck{ID: id, Name: id, Questions: questions}
}

func TestScheduleQuestionsBalancesDecks(t *testing.T) {
	decks := []Deck{testDeck("food", 5), testDeck("movies", 5), testDeck("travel", 5)}
	got, err := ScheduleQuestions(decks, 6, rand.New(rand.NewSource(1)))
	if err != nil {
		t.Fatal(err)
	}
	counts := map[string]int{}
	for _, q := range got {
		counts[q.DeckID]++
	}
	for _, id := range []string{"food", "movies", "travel"} {
		if counts[id] != 2 {
			t.Fatalf("%s count = %d, want 2", id, counts[id])
		}
	}
}

func TestScheduleQuestionsHandlesExhaustedDeckAndNoReplacement(t *testing.T) {
	got, err := ScheduleQuestions(
		[]Deck{testDeck("short", 1), testDeck("long", 6)},
		6,
		rand.New(rand.NewSource(2)),
	)
	if err != nil {
		t.Fatal(err)
	}
	counts := map[string]int{}
	ids := map[string]bool{}
	for _, q := range got {
		counts[q.DeckID]++
		if ids[q.ID] {
			t.Fatalf("duplicate scheduled question %q", q.ID)
		}
		ids[q.ID] = true
	}
	if counts["short"] != 1 || counts["long"] != 5 {
		t.Fatalf("unexpected allocation: %+v", counts)
	}
}

func TestScheduleQuestionsQualifiesCollidingQuestionIDs(t *testing.T) {
	got, err := ScheduleQuestions(
		[]Deck{testDeck("first", 1), testDeck("second", 1)},
		2,
		rand.New(rand.NewSource(3)),
	)
	if err != nil {
		t.Fatal(err)
	}
	if got[0].ID == got[1].ID || !strings.HasPrefix(got[0].ID, got[0].DeckID+":") ||
		!strings.HasPrefix(got[1].ID, got[1].DeckID+":") {
		t.Fatalf("ids were not source-qualified: %q %q", got[0].ID, got[1].ID)
	}
}

func TestScheduleQuestionPoolIncludesBalancedReserveWithoutReplacement(t *testing.T) {
	got, err := ScheduleQuestionPool(
		[]Deck{testDeck("first", 2), testDeck("second", 3)},
		rand.New(rand.NewSource(4)),
	)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 5 {
		t.Fatalf("pool size = %d, want 5", len(got))
	}
	wantDecks := []string{"first", "second", "first", "second", "second"}
	ids := map[string]bool{}
	for i, q := range got {
		if q.DeckID != wantDecks[i] {
			t.Fatalf("question %d deck = %q, want %q", i, q.DeckID, wantDecks[i])
		}
		if ids[q.ID] {
			t.Fatalf("duplicate question %q", q.ID)
		}
		ids[q.ID] = true
	}
}

func TestUpdateSettingsRejectsGuestAndDuplicates(t *testing.T) {
	s := NewState("ABCD")
	s.Players = []*Player{
		{ID: "host", IsHost: true, Connected: true},
		{ID: "guest", Connected: true},
	}
	if err := s.UpdateSettings("guest", ModeCoop, []string{"food"}, 5, nil); err != ErrNotHost {
		t.Fatalf("guest error = %v, want ErrNotHost", err)
	}
	if err := s.UpdateSettings("host", ModeCoop, []string{"food", "food"}, 5, nil); err != ErrInvalidDecks {
		t.Fatalf("duplicate error = %v, want ErrInvalidDecks", err)
	}
}
