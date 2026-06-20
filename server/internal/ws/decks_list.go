package ws

import (
	"github.com/saruar/rank5/server/internal/decks"
	"github.com/saruar/rank5/server/internal/game"
)

func listDecks() ([]game.DeckInfo, error) {
	return decks.All()
}
