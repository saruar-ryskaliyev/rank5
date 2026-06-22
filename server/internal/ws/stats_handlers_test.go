package ws

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	"github.com/saruar/rank5/server/internal/auth"
	"github.com/saruar/rank5/server/internal/game"
)

func TestStatsRoutesRequireAuthentication(t *testing.T) {
	server := &Server{Auth: auth.NewService(auth.Config{
		JWTSecret: []byte("test-secret-key-32bytes-long!!!!"),
	})}
	handler := server.Routes()

	for _, test := range []struct {
		method string
		path   string
	}{
		{method: http.MethodGet, path: "/me/stats"},
		{method: http.MethodPost, path: "/me/results/claim"},
		{method: http.MethodGet, path: "/me/saved-decks"},
		{method: http.MethodPut, path: "/me/saved-decks/food"},
		{method: http.MethodDelete, path: "/me/saved-decks/food"},
	} {
		t.Run(test.method+" "+test.path, func(t *testing.T) {
			req := httptest.NewRequest(test.method, test.path, nil)
			response := httptest.NewRecorder()
			handler.ServeHTTP(response, req)
			if response.Code != http.StatusUnauthorized {
				t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
			}
		})
	}
}

func TestAttachAccountUpdatesAnonymousLivePlayer(t *testing.T) {
	service := auth.NewService(auth.Config{
		JWTSecret: []byte("test-secret-key-32bytes-long!!!!"),
	})
	userID := uuid.New()
	token, err := service.MintJWT(userID)
	if err != nil {
		t.Fatal(err)
	}
	room := &Room{auth: service, state: game.NewState("ABCD")}
	room.state.Players = []*game.Player{{ID: "player-1", Nickname: "Guest", Connected: true}}
	slot := &connSlot{playerID: "player-1", send: make(chan []byte, 1)}

	room.doAttachAccount(slot, token)

	if got := room.state.Players[0].UserID; got != userID.String() {
		t.Fatalf("user id = %q, want %q", got, userID)
	}
}
