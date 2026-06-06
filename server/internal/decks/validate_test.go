package decks

import (
	"testing"

	"github.com/saruar/rank5/server/internal/game"
)

func TestValidateInput_OK(t *testing.T) {
	qs := validQuestions(MinQuestions)
	if err := ValidateInput("My Deck", "🎯", qs); err != nil {
		t.Fatalf("unexpected: %v", err)
	}
}

func TestValidateInput_BadTitle(t *testing.T) {
	qs := validQuestions(MinQuestions)
	if err := ValidateInput("", "🎯", qs); err == nil {
		t.Fatal("expected title error")
	}
	long := stringsRepeat("a", MaxTitleLen+1)
	if err := ValidateInput(long, "🎯", qs); err == nil {
		t.Fatal("expected long title error")
	}
}

func TestValidateInput_QuestionCount(t *testing.T) {
	tooFew := validQuestions(MinQuestions - 1)
	if err := ValidateInput("Deck", "🎯", tooFew); err == nil {
		t.Fatal("expected too-few questions error")
	}
	tooMany := validQuestions(MaxQuestions + 1)
	if err := ValidateInput("Deck", "🎯", tooMany); err == nil {
		t.Fatal("expected too-many questions error")
	}
}

func TestValidateInput_OptionCount(t *testing.T) {
	qs := validQuestions(MinQuestions)
	qs[0].Options = []string{"a", "b", "c", "d"}
	if err := ValidateInput("Deck", "🎯", qs); err == nil {
		t.Fatal("expected option count error")
	}
}

func TestNormalizeInput_Defaults(t *testing.T) {
	title, emoji, qs := NormalizeInput("  Hi  ", "", []game.Question{{
		Prompt:  "  P  ",
		Options: []string{" a ", "b", "c", "d", "e"},
	}})
	if title != "Hi" {
		t.Fatalf("title=%q", title)
	}
	if emoji != DefaultEmoji {
		t.Fatalf("emoji=%q", emoji)
	}
	if qs[0].ID != "q1" || qs[0].Prompt != "P" || qs[0].Options[0] != "a" {
		t.Fatalf("normalized=%+v", qs[0])
	}
}

func validQuestions(n int) []game.Question {
	out := make([]game.Question, n)
	for i := range out {
		out[i] = game.Question{
			ID:      "q",
			Prompt:  "Prompt",
			Options: []string{"a", "b", "c", "d", "e"},
		}
	}
	return out
}

func stringsRepeat(s string, n int) string {
	b := make([]byte, 0, n*len(s))
	for i := 0; i < n; i++ {
		b = append(b, s...)
	}
	return string(b)
}
