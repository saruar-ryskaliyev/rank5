package ws

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strings"

	"github.com/coder/websocket"
	"github.com/saruar/rank5/server/internal/auth"
	"github.com/saruar/rank5/server/internal/generation"
	"github.com/saruar/rank5/server/internal/hub"
	"github.com/saruar/rank5/server/internal/store"
)

// Server wires HTTP + WebSocket to the hub.
type Server struct {
	Hub              *hub.Hub
	Auth             *auth.Service
	Store            *store.Store
	Generator        generation.Generator
	GenerationLimits GenerationLimits
}

func (s *Server) Routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.handleHealth)
	mux.HandleFunc("POST /rooms", s.handleCreateRoom)
	mux.HandleFunc("GET /decks", s.handleDecks)
	mux.HandleFunc("GET /decks/{id}", s.handleGetDeck)
	mux.HandleFunc("GET /community/decks", s.handleCommunityDecks)
	mux.Handle("GET /me/decks", s.requireAuth(http.HandlerFunc(s.handleMyDecks)))
	mux.Handle("GET /me/saved-decks", s.requireAuth(http.HandlerFunc(s.handleSavedDecks)))
	mux.Handle("PUT /me/saved-decks/{deckId}", s.requireAuth(http.HandlerFunc(s.handleSaveDeck)))
	mux.Handle("DELETE /me/saved-decks/{deckId}", s.requireAuth(http.HandlerFunc(s.handleUnsaveDeck)))
	mux.Handle("POST /decks", s.requireAuth(http.HandlerFunc(s.handleCreateDeck)))
	mux.Handle("POST /me/deck-generations", s.requireAuth(http.HandlerFunc(s.handleGenerateDeck)))
	mux.Handle("PUT /decks/{id}", s.requireAuth(http.HandlerFunc(s.handleUpdateDeck)))
	mux.Handle("DELETE /decks/{id}", s.requireAuth(http.HandlerFunc(s.handleDeleteDeck)))
	mux.Handle("POST /decks/{id}/publish", s.requireAuth(http.HandlerFunc(s.handlePublishDeck)))
	mux.Handle("POST /decks/{id}/unpublish", s.requireAuth(http.HandlerFunc(s.handleUnpublishDeck)))
	mux.Handle("POST /decks/{id}/report", s.requireAuth(http.HandlerFunc(s.handleReportDeck)))
	mux.Handle("GET /admin/reports", s.requireAdmin(http.HandlerFunc(s.handleListReports)))
	mux.Handle("POST /admin/reports/{id}/resolve", s.requireAdmin(http.HandlerFunc(s.handleResolveReport)))
	mux.HandleFunc("GET /ws", s.handleWS)

	mux.HandleFunc("POST /auth/google", s.handleAuthGoogle)
	mux.HandleFunc("POST /auth/dev", s.handleAuthDev)
	mux.Handle("GET /me", s.requireAuth(http.HandlerFunc(s.handleMe)))
	mux.Handle("DELETE /me", s.requireAuth(http.HandlerFunc(s.handleDeleteMe)))
	mux.Handle("GET /me/stats", s.requireAuth(http.HandlerFunc(s.handleMeStats)))
	mux.Handle("POST /me/results/claim", s.requireAuth(http.HandlerFunc(s.handleClaimResults)))

	mux.HandleFunc("GET /privacy", s.handlePrivacy)
	mux.HandleFunc("GET /account-deletion", s.handleAccountDeletion)

	return withCORS(mux)
}

func (s *Server) requireAuth(next http.Handler) http.Handler {
	if s.Auth == nil {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, `{"error":"auth unavailable"}`, http.StatusServiceUnavailable)
		})
	}
	return s.Auth.Middleware(next)
}

func (s *Server) requireAdmin(next http.Handler) http.Handler {
	return s.requireAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !s.storeReady(w) {
			return
		}
		uid := auth.UserIDFromContext(r.Context())
		ok, err := s.Store.IsAdmin(r.Context(), uid)
		if err != nil || !ok {
			http.Error(w, `{"error":"forbidden"}`, http.StatusForbidden)
			return
		}
		next.ServeHTTP(w, r)
	}))
}

func (s *Server) storeReady(w http.ResponseWriter) bool {
	if s.Store == nil || !s.Store.Enabled() {
		http.Error(w, `{"error":"database unavailable"}`, http.StatusServiceUnavailable)
		return false
	}
	return true
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) handleCreateRoom(w http.ResponseWriter, r *http.Request) {
	code, err := s.Hub.CreateCode()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	room := NewRoom(code, s.Auth, s.Store)
	s.Hub.Register(room)
	writeJSON(w, http.StatusCreated, map[string]string{"code": code})
}

func (s *Server) handleWS(w http.ResponseWriter, r *http.Request) {
	code := strings.ToUpper(strings.TrimSpace(r.URL.Query().Get("code")))
	if code == "" {
		http.Error(w, "code required", http.StatusBadRequest)
		return
	}
	runner, ok := s.Hub.Get(code)
	if !ok {
		http.Error(w, "room not found", http.StatusNotFound)
		return
	}
	room, ok := runner.(*Room)
	if !ok {
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true, // Android / local dev
		OriginPatterns:     []string{"*"},
	})
	if err != nil {
		log.Printf("ws accept: %v", err)
		return
	}
	// Do not use r.Context() — it is cancelled when this handler returns.
	room.AttachConn(context.Background(), conn)
}

type googleAuthRequest struct {
	IDToken string `json:"idToken"`
}

type authResponse struct {
	Token string      `json:"token"`
	User  userPayload `json:"user"`
}

type userPayload struct {
	ID          string `json:"id"`
	DisplayName string `json:"displayName"`
	AvatarSeed  string `json:"avatarSeed"`
}

type devAuthRequest struct {
	Name string `json:"name"`
}

func (s *Server) handleAuthGoogle(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) || s.Auth == nil {
		return
	}
	var req googleAuthRequest
	if err := json.NewDecoder(io.LimitReader(r.Body, 1<<20)).Decode(&req); err != nil || req.IDToken == "" {
		http.Error(w, `{"error":"idToken required"}`, http.StatusBadRequest)
		return
	}
	sub, name, err := s.Auth.VerifyGoogleIDToken(r.Context(), req.IDToken)
	if err != nil {
		http.Error(w, `{"error":"invalid google token"}`, http.StatusUnauthorized)
		return
	}
	s.issueForGoogle(w, r, sub, name)
}

func (s *Server) handleAuthDev(w http.ResponseWriter, r *http.Request) {
	if s.Auth == nil || !s.Auth.DevFakeEnabled() {
		http.Error(w, `{"error":"dev auth disabled"}`, http.StatusNotFound)
		return
	}
	if !s.storeReady(w) {
		return
	}
	var req devAuthRequest
	_ = json.NewDecoder(io.LimitReader(r.Body, 1<<16)).Decode(&req)
	name := strings.TrimSpace(req.Name)
	if name == "" {
		name = "Dev Player"
	}
	if len(name) > 24 {
		name = name[:24]
	}
	sub := "dev:" + strings.ToLower(strings.ReplaceAll(name, " ", "_"))
	s.issueForGoogle(w, r, sub, name)
}

func (s *Server) issueForGoogle(w http.ResponseWriter, r *http.Request, googleSub, displayName string) {
	u, err := s.Store.UpsertGoogleUser(r.Context(), googleSub, displayName)
	if err != nil {
		log.Printf("upsert user: %v", err)
		http.Error(w, `{"error":"could not create user"}`, http.StatusInternalServerError)
		return
	}
	tok, err := s.Auth.MintJWT(u.ID)
	if err != nil {
		http.Error(w, `{"error":"could not mint token"}`, http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, authResponse{
		Token: tok,
		User: userPayload{
			ID:          u.ID.String(),
			DisplayName: u.DisplayName,
			AvatarSeed:  u.AvatarSeed,
		},
	})
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := auth.UserIDFromContext(r.Context())
	u, err := s.Store.GetUser(r.Context(), id)
	if err != nil {
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		return
	}
	writeJSON(w, http.StatusOK, userPayload{
		ID:          u.ID.String(),
		DisplayName: u.DisplayName,
		AvatarSeed:  u.AvatarSeed,
	})
}

func (s *Server) handleDeleteMe(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := auth.UserIDFromContext(r.Context())
	if err := s.Store.DeleteUser(r.Context(), id); err != nil {
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleMeStats(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	stats, err := s.Store.GetUserStats(r.Context(), auth.UserIDFromContext(r.Context()))
	if err != nil {
		log.Printf("get user stats: %v", err)
		http.Error(w, `{"error":"could not load stats"}`, http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, stats)
}

type claimResultsRequest struct {
	RoomCode       string `json:"roomCode"`
	PlayerID       string `json:"playerId"`
	ReconnectToken string `json:"reconnectToken"`
}

func (s *Server) handleClaimResults(w http.ResponseWriter, r *http.Request) {
	var request claimResultsRequest
	if err := json.NewDecoder(io.LimitReader(r.Body, 1<<16)).Decode(&request); err != nil {
		http.Error(w, `{"error":"invalid request"}`, http.StatusBadRequest)
		return
	}
	request.RoomCode = strings.ToUpper(strings.TrimSpace(request.RoomCode))
	request.PlayerID = strings.TrimSpace(request.PlayerID)
	request.ReconnectToken = strings.TrimSpace(request.ReconnectToken)
	if request.RoomCode == "" || request.PlayerID == "" || request.ReconnectToken == "" {
		http.Error(w, `{"error":"roomCode, playerId, and reconnectToken are required"}`, http.StatusBadRequest)
		return
	}
	if !s.storeReady(w) {
		return
	}
	claimed, err := s.Store.ClaimGameResults(
		r.Context(), auth.UserIDFromContext(r.Context()),
		request.RoomCode, request.PlayerID, request.ReconnectToken,
	)
	if errors.Is(err, store.ErrClaimNotFound) {
		http.Error(w, `{"error":"claimable result not found"}`, http.StatusNotFound)
		return
	}
	if err != nil {
		log.Printf("claim game result: %v", err)
		http.Error(w, `{"error":"could not save result"}`, http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]int64{"claimedGames": claimed})
}

func (s *Server) handlePrivacy(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = io.WriteString(w, privacyHTML)
}

func (s *Server) handleAccountDeletion(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = io.WriteString(w, accountDeletionHTML)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

const privacyHTML = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Rank5 Privacy Policy</title>
<style>body{font-family:system-ui,sans-serif;max-width:40rem;margin:2rem auto;padding:0 1rem;line-height:1.5;color:#221D15;background:#FAF6EF}
h1{font-size:1.5rem}h2{font-size:1.1rem;margin-top:1.5rem}</style></head>
<body>
<h1>Rank5 Privacy Policy</h1>
<p>Last updated: August 4, 2026</p>
<p>Rank5 is a party game. You can play without an account. If you sign in, we store the information below so we can keep your profile and game history.</p>
<h2>What we collect</h2>
<ul>
<li><strong>Account:</strong> Google account identifier, display name you choose or that Google provides, and an avatar seed.</li>
<li><strong>Gameplay:</strong> Finished game results (room code, mode, deck, scores, nicknames). Anonymous players appear as nicknames only.</li>
<li><strong>Deck library:</strong> Decks you create and public or official decks you bookmark.</li>
<li><strong>AI deck generation:</strong> If you choose to generate a deck, the topic you enter is sent to our configured AI provider. We retain usage metadata, but not the raw topic or generated draft in generation logs.</li>
</ul>
<h2>What we do not collect</h2>
<ul>
<li>We do not sell your data.</li>
<li>We do not use your data for advertising.</li>
<li>Playing without signing in does not create an account.</li>
</ul>
<h2>How we use data</h2>
<p>To authenticate you, show your profile and private stats, attribute game results to your account, and keep decks you create.</p>
<h2>Retention and deletion</h2>
<p>You can delete your account in the Rank5 app (Profile → Delete account). Deletion removes your user record. Past game rows keep nicknames and scores but unlink your account id. See also our <a href="/account-deletion">account deletion</a> page.</p>
<h2>Contact</h2>
<p>Questions about this policy: open an issue on the Rank5 project repository or contact the developer who published the app on Google Play.</p>
</body></html>`

const accountDeletionHTML = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Rank5 Account Deletion</title>
<style>body{font-family:system-ui,sans-serif;max-width:40rem;margin:2rem auto;padding:0 1rem;line-height:1.5;color:#221D15;background:#FAF6EF}
h1{font-size:1.5rem}ol{padding-left:1.25rem}</style></head>
<body>
<h1>Delete your Rank5 account</h1>
<p>You can delete your Rank5 account at any time from inside the app:</p>
<ol>
<li>Open Rank5.</li>
<li>Tap your avatar on the Home screen to open Profile.</li>
<li>Tap <strong>Delete account</strong> and confirm.</li>
</ol>
<p>This permanently deletes your account, your decks, and your saved-deck bookmarks. Game history rows may remain as anonymous nickname/score records with your user id removed.</p>
<p>If you cannot access the app, contact the developer listed on the Google Play store listing and request deletion with the Google account email you used to sign in.</p>
<p><a href="/privacy">Privacy policy</a></p>
</body></html>`
