package game

import (
	"fmt"
	"math/rand"
	"strings"
	"time"
)

// ScheduleQuestions creates a balanced, without-replacement round schedule.
// Deck order is stable while questions within each deck are shuffled.
func ScheduleQuestions(selected []Deck, rounds int, rng *rand.Rand) ([]Question, error) {
	if rounds < 1 || rounds > 20 {
		return nil, ErrInvalidRounds
	}
	pool, err := ScheduleQuestionPool(selected, rng)
	if err != nil {
		return nil, err
	}
	if rounds > len(pool) {
		rounds = len(pool)
	}
	return append([]Question(nil), pool[:rounds]...), nil
}

// ScheduleQuestionPool creates a balanced, shuffled pool containing every
// question in the selected decks once. Completed rounds consume questions;
// skipped questions are deferred by the engine so they remain usable later.
func ScheduleQuestionPool(selected []Deck, rng *rand.Rand) ([]Question, error) {
	if len(selected) < 1 || len(selected) > 5 {
		return nil, ErrInvalidDecks
	}
	if rng == nil {
		rng = rand.New(rand.NewSource(time.Now().UnixNano()))
	}
	seenDecks := make(map[string]struct{}, len(selected))
	queues := make([][]Question, len(selected))
	total := 0
	for i, deck := range selected {
		if strings.TrimSpace(deck.ID) == "" {
			return nil, ErrInvalidDecks
		}
		if _, exists := seenDecks[deck.ID]; exists {
			return nil, ErrInvalidDecks
		}
		seenDecks[deck.ID] = struct{}{}
		queues[i] = append([]Question(nil), deck.Questions...)
		rng.Shuffle(len(queues[i]), func(a, b int) { queues[i][a], queues[i][b] = queues[i][b], queues[i][a] })
		total += len(queues[i])
	}
	if total == 0 {
		return nil, ErrNoQuestions
	}

	positions := make([]int, len(selected))
	out := make([]Question, 0, total)
	for len(out) < total {
		progressed := false
		for i, deck := range selected {
			if positions[i] >= len(queues[i]) {
				continue
			}
			q := queues[i][positions[i]]
			positions[i]++
			q.DeckID = deck.ID
			q.ID = fmt.Sprintf("%s:%s", deck.ID, q.ID)
			out = append(out, q)
			progressed = true
		}
		if !progressed {
			break
		}
	}
	return out, nil
}
