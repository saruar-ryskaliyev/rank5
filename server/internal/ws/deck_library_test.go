package ws

import (
	"testing"

	"github.com/saruar/rank5/server/internal/store"
)

func TestBuiltinDeckSummaryIsAttributedToRank5(t *testing.T) {
	payload := summaryToPayload(store.DeckSummary{
		ID: "food", Title: "Food Favorites", IsBuiltin: true,
	})
	if payload.OwnerName != "Rank5" {
		t.Fatalf("ownerName = %q, want Rank5", payload.OwnerName)
	}
	if payload.Visibility != store.VisibilityPublic {
		t.Fatalf("visibility = %q, want public", payload.Visibility)
	}
}

func TestBuiltinDeckDetailIsAttributedToRank5(t *testing.T) {
	payload := deckToPayload(&store.Deck{
		ID: "food", Title: "Food Favorites", IsBuiltin: true,
	})
	if payload.OwnerName != "Rank5" {
		t.Fatalf("ownerName = %q, want Rank5", payload.OwnerName)
	}
	if payload.Visibility != store.VisibilityPublic {
		t.Fatalf("visibility = %q, want public", payload.Visibility)
	}
}
