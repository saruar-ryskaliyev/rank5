package decks

import (
	"strings"
	"testing"

	"github.com/saruar/rank5/server/internal/game"
)

// Built-in decks ship in the APK as well as the database, so a malformed one
// breaks both clients. Hold them to the same rules as user-created decks.
func TestBuiltinDecksAreValid(t *testing.T) {
	all, err := AllFull()
	if err != nil {
		t.Fatal(err)
	}
	if len(all) == 0 {
		t.Fatal("no built-in decks were embedded")
	}
	for _, d := range all {
		if err := ValidateInput(d.Name, d.Emoji, d.Questions); err != nil {
			t.Errorf("deck %q: %v", d.ID, err)
		}
	}
}

func TestBuiltinPlayerQuestionsCarryNoOptions(t *testing.T) {
	all, err := AllFull()
	if err != nil {
		t.Fatal(err)
	}
	playerQuestions := 0
	for _, d := range all {
		for _, q := range d.Questions {
			if q.Kind != game.QuestionKindPlayers {
				continue
			}
			playerQuestions++
			if len(q.Options) != 0 {
				t.Errorf("deck %q question %q authors options for a player question", d.ID, q.ID)
			}
		}
	}
	if playerQuestions == 0 {
		t.Fatal("expected at least one built-in players-kind question")
	}
}

// A prompt that mentions the subject must use the placeholder, otherwise the
// personalization silently does nothing.
func TestBuiltinSubjectPlaceholdersRender(t *testing.T) {
	all, err := AllFull()
	if err != nil {
		t.Fatal(err)
	}
	personalized := 0
	for _, d := range all {
		for _, q := range d.Questions {
			if !strings.Contains(q.Prompt, game.SubjectPlaceholder) {
				continue
			}
			personalized++
			if q.Kind == game.QuestionKindPlayers {
				t.Errorf("deck %q question %q ranks players and does not need a name", d.ID, q.ID)
			}
			rendered := strings.ReplaceAll(q.Prompt, game.SubjectPlaceholder, "Maya")
			if strings.Contains(rendered, "{") || strings.Contains(rendered, "}") {
				t.Errorf("deck %q question %q has an unrendered placeholder: %q", d.ID, q.ID, rendered)
			}
		}
	}
	if personalized == 0 {
		t.Fatal("expected at least one built-in personalized question")
	}
}
