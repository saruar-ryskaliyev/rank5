package decks

import (
	"fmt"
	"strings"
	"unicode/utf8"

	"github.com/saruar/rank5/server/internal/game"
)

const (
	MinTitleLen  = 1
	MaxTitleLen  = 60
	MinQuestions = 3  // matches smallest Lobby round option
	MaxQuestions = 12 // soft cap; Lobby only offers up to 6 rounds
	RequiredOpts = 5
	MaxPromptLen = 200
	MaxOptionLen = 80
	MaxEmojiLen  = 16
	DefaultEmoji = "🃏"
)

// ValidateInput checks title/emoji/questions for create/update.
func ValidateInput(title, emoji string, questions []game.Question) error {
	title = strings.TrimSpace(title)
	if utf8.RuneCountInString(title) < MinTitleLen || utf8.RuneCountInString(title) > MaxTitleLen {
		return fmt.Errorf("title must be %d–%d characters", MinTitleLen, MaxTitleLen)
	}
	emoji = strings.TrimSpace(emoji)
	if emoji == "" {
		return fmt.Errorf("emoji is required")
	}
	if utf8.RuneCountInString(emoji) > MaxEmojiLen {
		return fmt.Errorf("emoji is too long")
	}
	if len(questions) < MinQuestions || len(questions) > MaxQuestions {
		return fmt.Errorf("deck must have %d–%d questions", MinQuestions, MaxQuestions)
	}
	for i, q := range questions {
		prompt := strings.TrimSpace(q.Prompt)
		if prompt == "" {
			return fmt.Errorf("question %d: prompt is required", i+1)
		}
		if utf8.RuneCountInString(prompt) > MaxPromptLen {
			return fmt.Errorf("question %d: prompt is too long", i+1)
		}
		if len(q.Options) != RequiredOpts {
			return fmt.Errorf("question %d: must have exactly %d options", i+1, RequiredOpts)
		}
		for j, opt := range q.Options {
			o := strings.TrimSpace(opt)
			if o == "" {
				return fmt.Errorf("question %d option %d: required", i+1, j+1)
			}
			if utf8.RuneCountInString(o) > MaxOptionLen {
				return fmt.Errorf("question %d option %d: too long", i+1, j+1)
			}
		}
	}
	return nil
}

// NormalizeInput trims fields and fills default emoji / question ids.
func NormalizeInput(title, emoji string, questions []game.Question) (string, string, []game.Question) {
	title = strings.TrimSpace(title)
	emoji = strings.TrimSpace(emoji)
	if emoji == "" {
		emoji = DefaultEmoji
	}
	out := make([]game.Question, len(questions))
	for i, q := range questions {
		id := strings.TrimSpace(q.ID)
		if id == "" {
			id = fmt.Sprintf("q%d", i+1)
		}
		opts := make([]string, len(q.Options))
		for j, o := range q.Options {
			opts[j] = strings.TrimSpace(o)
		}
		out[i] = game.Question{
			ID:      id,
			Prompt:  strings.TrimSpace(q.Prompt),
			Options: opts,
		}
	}
	return title, emoji, out
}
