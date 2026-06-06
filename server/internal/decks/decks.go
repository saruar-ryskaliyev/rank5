package decks

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"sync"

	"github.com/saruar/rank5/server/internal/game"
)

//go:embed data/*.json
var files embed.FS

var (
	once    sync.Once
	cache   map[string]*game.Deck
	list    []game.DeckInfo
	loadErr error
)

func load() {
	cache = make(map[string]*game.Deck)
	entries, err := files.ReadDir("data")
	if err != nil {
		loadErr = err
		return
	}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		b, err := files.ReadFile("data/" + e.Name())
		if err != nil {
			loadErr = err
			return
		}
		var d game.Deck
		if err := json.Unmarshal(b, &d); err != nil {
			loadErr = fmt.Errorf("%s: %w", e.Name(), err)
			return
		}
		if d.Emoji == "" {
			d.Emoji = "🃏"
		}
		cache[d.ID] = &d
		list = append(list, game.DeckInfo{
			ID: d.ID, Name: d.Name, Emoji: d.Emoji, QuestionCount: len(d.Questions),
		})
	}
}

// All returns lightweight deck listings.
func All() ([]game.DeckInfo, error) {
	once.Do(load)
	return list, loadErr
}

// Get returns a deck by ID.
func Get(id string) (*game.Deck, error) {
	once.Do(load)
	if loadErr != nil {
		return nil, loadErr
	}
	d, ok := cache[id]
	if !ok {
		return nil, fmt.Errorf("deck not found: %s", id)
	}
	return d, nil
}

// AllFull returns every embedded deck with questions.
func AllFull() ([]*game.Deck, error) {
	once.Do(load)
	if loadErr != nil {
		return nil, loadErr
	}
	out := make([]*game.Deck, 0, len(cache))
	for _, d := range cache {
		out = append(out, d)
	}
	return out, nil
}

// QuestionsForGame returns up to n questions from the deck (in order).
func QuestionsForGame(deckID string, n int) ([]game.Question, error) {
	d, err := Get(deckID)
	if err != nil {
		return nil, err
	}
	if n > len(d.Questions) {
		n = len(d.Questions)
	}
	out := make([]game.Question, n)
	copy(out, d.Questions[:n])
	return out, nil
}

// BuiltinSeeder upserts built-in decks into persistent storage.
type BuiltinSeeder interface {
	UpsertBuiltinDeck(ctx context.Context, id, title, emoji string, questions []byte) error
}

// SeedInto writes embedded decks into the store as built-ins.
func SeedInto(ctx context.Context, s BuiltinSeeder) error {
	full, err := AllFull()
	if err != nil {
		return err
	}
	for _, d := range full {
		qs, err := json.Marshal(d.Questions)
		if err != nil {
			return fmt.Errorf("marshal %s: %w", d.ID, err)
		}
		emoji := d.Emoji
		if emoji == "" {
			emoji = "🃏"
		}
		if err := s.UpsertBuiltinDeck(ctx, d.ID, d.Name, emoji, qs); err != nil {
			return fmt.Errorf("seed %s: %w", d.ID, err)
		}
	}
	return nil
}
