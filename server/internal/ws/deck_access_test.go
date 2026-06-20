package ws

import (
	"testing"

	"github.com/google/uuid"
	"github.com/saruar/rank5/server/internal/game"
	"github.com/saruar/rank5/server/internal/store"
)

func TestHostDeckAccessCombinations(t *testing.T) {
	owner := uuid.New()
	host := &game.Player{UserID: owner.String()}
	tests := []struct {
		name string
		deck *store.Deck
		want bool
	}{
		{"official", &store.Deck{IsBuiltin: true, Visibility: store.VisibilityPublic}, true},
		{"public community", &store.Deck{Visibility: store.VisibilityPublic}, true},
		{"owned private", &store.Deck{OwnerID: &owner, Visibility: store.VisibilityPrivate}, true},
		{"unpublished by another owner", &store.Deck{OwnerID: ptrUUID(uuid.New()), Visibility: store.VisibilityPrivate}, false},
		{"removed", &store.Deck{OwnerID: &owner, Visibility: store.VisibilityRemoved}, false},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := canHostUseDeck(tc.deck, host); got != tc.want {
				t.Fatalf("access = %v, want %v", got, tc.want)
			}
		})
	}
}

func ptrUUID(id uuid.UUID) *uuid.UUID { return &id }
