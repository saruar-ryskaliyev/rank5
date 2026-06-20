package ws

import (
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/saruar/rank5/server/internal/auth"
	"github.com/saruar/rank5/server/internal/decks"
	"github.com/saruar/rank5/server/internal/game"
	"github.com/saruar/rank5/server/internal/store"
)

type deckWriteRequest struct {
	Title     string          `json:"title"`
	Emoji     string          `json:"emoji"`
	Questions []game.Question `json:"questions"`
}

type deckPayload struct {
	ID          string          `json:"id"`
	Title       string          `json:"title"`
	Emoji       string          `json:"emoji"`
	Questions   []game.Question `json:"questions"`
	IsBuiltin   bool            `json:"isBuiltin"`
	OwnerID     *string         `json:"ownerId,omitempty"`
	Visibility  string          `json:"visibility"`
	OwnerName   string          `json:"ownerName,omitempty"`
	PublishedAt *string         `json:"publishedAt,omitempty"`
}

type deckSummaryPayload struct {
	ID            string  `json:"id"`
	Title         string  `json:"title"`
	Emoji         string  `json:"emoji"`
	IsBuiltin     bool    `json:"isBuiltin"`
	OwnerID       *string `json:"ownerId,omitempty"`
	QuestionCount int     `json:"questionCount"`
	Visibility    string  `json:"visibility,omitempty"`
	OwnerName     string  `json:"ownerName,omitempty"`
	PublishedAt   *string `json:"publishedAt,omitempty"`
}

func formatTimePtr(t *time.Time) *string {
	if t == nil {
		return nil
	}
	s := t.UTC().Format(time.RFC3339)
	return &s
}

func deckToPayload(d *store.Deck) deckPayload {
	vis := d.Visibility
	ownerName := d.OwnerName
	if d.IsBuiltin {
		vis = store.VisibilityPublic
		if ownerName == "" {
			ownerName = "Rank5"
		}
	}
	if vis == "" {
		vis = store.VisibilityPrivate
	}
	p := deckPayload{
		ID:          d.ID,
		Title:       d.Title,
		Emoji:       d.Emoji,
		Questions:   d.Questions,
		IsBuiltin:   d.IsBuiltin,
		Visibility:  vis,
		OwnerName:   ownerName,
		PublishedAt: formatTimePtr(d.PublishedAt),
	}
	if d.OwnerID != nil {
		s := d.OwnerID.String()
		p.OwnerID = &s
	}
	return p
}

func summaryToPayload(d store.DeckSummary) deckSummaryPayload {
	vis := d.Visibility
	ownerName := d.OwnerName
	if d.IsBuiltin {
		vis = store.VisibilityPublic
		if ownerName == "" {
			ownerName = "Rank5"
		}
	}
	if vis == "" {
		vis = store.VisibilityPrivate
	}
	p := deckSummaryPayload{
		ID:            d.ID,
		Title:         d.Title,
		Emoji:         d.Emoji,
		IsBuiltin:     d.IsBuiltin,
		QuestionCount: d.QuestionCount,
		Visibility:    vis,
		OwnerName:     ownerName,
		PublishedAt:   formatTimePtr(d.PublishedAt),
	}
	if d.OwnerID != nil {
		s := d.OwnerID.String()
		p.OwnerID = &s
	}
	return p
}

func (s *Server) handleDecks(w http.ResponseWriter, r *http.Request) {
	if s.Store != nil && s.Store.Enabled() {
		list, err := s.Store.ListBuiltinDecks(r.Context())
		if err != nil {
			http.Error(w, `{"error":"could not list decks"}`, http.StatusInternalServerError)
			return
		}
		out := make([]deckSummaryPayload, 0, len(list))
		for _, d := range list {
			out = append(out, summaryToPayload(d))
		}
		writeJSON(w, http.StatusOK, out)
		return
	}
	infos, err := listDecks()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	out := make([]deckSummaryPayload, 0, len(infos))
	for _, d := range infos {
		out = append(out, deckSummaryPayload{
			ID: d.ID, Title: d.Name, Emoji: d.Emoji, IsBuiltin: true,
			QuestionCount: d.QuestionCount,
		})
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) handleGetDeck(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, `{"error":"id required"}`, http.StatusBadRequest)
		return
	}

	viewerID := optionalUserID(r, s.Auth)

	if s.Store != nil && s.Store.Enabled() {
		d, err := s.Store.GetDeck(r.Context(), id)
		if err != nil {
			if errors.Is(err, store.ErrNotFound) {
				http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
				return
			}
			http.Error(w, `{"error":"could not load deck"}`, http.StatusInternalServerError)
			return
		}
		if !canViewDeck(d, viewerID) {
			http.Error(w, `{"error":"forbidden"}`, http.StatusForbidden)
			return
		}
		writeJSON(w, http.StatusOK, deckToPayload(d))
		return
	}

	d, err := decks.Get(id)
	if err != nil {
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		return
	}
	writeJSON(w, http.StatusOK, deckPayload{
		ID: d.ID, Title: d.Name, Emoji: d.Emoji, Questions: d.Questions, IsBuiltin: true,
		Visibility: store.VisibilityPrivate,
	})
}

func (s *Server) handleCommunityDecks(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	q := r.URL.Query().Get("q")
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	offset, _ := strconv.Atoi(r.URL.Query().Get("offset"))
	list, err := s.Store.SearchPublicDecks(r.Context(), q, limit, offset)
	if err != nil {
		log.Printf("community search: %v", err)
		http.Error(w, `{"error":"could not search decks"}`, http.StatusInternalServerError)
		return
	}
	out := make([]deckSummaryPayload, 0, len(list))
	for _, d := range list {
		out = append(out, summaryToPayload(d))
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) handleMyDecks(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	uid := auth.UserIDFromContext(r.Context())
	list, err := s.Store.ListDecksByOwner(r.Context(), uid)
	if err != nil {
		http.Error(w, `{"error":"could not list decks"}`, http.StatusInternalServerError)
		return
	}
	out := make([]deckSummaryPayload, 0, len(list))
	for _, d := range list {
		out = append(out, summaryToPayload(d))
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) handleSavedDecks(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	list, err := s.Store.ListSavedDecks(r.Context(), auth.UserIDFromContext(r.Context()))
	if err != nil {
		log.Printf("list saved decks: %v", err)
		http.Error(w, `{"error":"could not load saved decks"}`, http.StatusInternalServerError)
		return
	}
	out := make([]deckSummaryPayload, 0, len(list))
	for _, d := range list {
		out = append(out, summaryToPayload(d))
	}
	writeJSON(w, http.StatusOK, out)
}

func (s *Server) handleSaveDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := strings.TrimSpace(r.PathValue("deckId"))
	if id == "" {
		http.Error(w, `{"error":"deck id required"}`, http.StatusBadRequest)
		return
	}
	err := s.Store.SaveDeckBookmark(r.Context(), auth.UserIDFromContext(r.Context()), id)
	if errors.Is(err, store.ErrForbidden) {
		http.Error(w, `{"error":"this deck cannot be saved"}`, http.StatusForbidden)
		return
	}
	if err != nil {
		log.Printf("save deck bookmark: %v", err)
		http.Error(w, `{"error":"could not save deck"}`, http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleUnsaveDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := strings.TrimSpace(r.PathValue("deckId"))
	if id == "" {
		http.Error(w, `{"error":"deck id required"}`, http.StatusBadRequest)
		return
	}
	if err := s.Store.UnsaveDeckBookmark(r.Context(), auth.UserIDFromContext(r.Context()), id); err != nil {
		log.Printf("unsave deck bookmark: %v", err)
		http.Error(w, `{"error":"could not remove saved deck"}`, http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleCreateDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	req, ok := decodeDeckWrite(w, r)
	if !ok {
		return
	}
	title, emoji, qs := decks.NormalizeInput(req.Title, req.Emoji, req.Questions)
	if err := decks.ValidateInput(title, emoji, qs); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	uid := auth.UserIDFromContext(r.Context())
	d, err := s.Store.CreateDeck(r.Context(), uid, title, emoji, qs)
	if err != nil {
		log.Printf("create deck: %v", err)
		http.Error(w, `{"error":"could not create deck"}`, http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusCreated, deckToPayload(d))
}

func (s *Server) handleUpdateDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, `{"error":"id required"}`, http.StatusBadRequest)
		return
	}
	req, ok := decodeDeckWrite(w, r)
	if !ok {
		return
	}
	title, emoji, qs := decks.NormalizeInput(req.Title, req.Emoji, req.Questions)
	if err := decks.ValidateInput(title, emoji, qs); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	uid := auth.UserIDFromContext(r.Context())
	d, err := s.Store.UpdateDeck(r.Context(), uid, id, title, emoji, qs)
	if err != nil {
		writeDeckMutateError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, deckToPayload(d))
}

func (s *Server) handleDeleteDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, `{"error":"id required"}`, http.StatusBadRequest)
		return
	}
	uid := auth.UserIDFromContext(r.Context())
	if err := s.Store.DeleteDeck(r.Context(), uid, id); err != nil {
		writeDeckMutateError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handlePublishDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, `{"error":"id required"}`, http.StatusBadRequest)
		return
	}
	uid := auth.UserIDFromContext(r.Context())
	existing, err := s.Store.GetDeck(r.Context(), id)
	if err != nil {
		writeDeckMutateError(w, err)
		return
	}
	if existing.IsBuiltin || existing.OwnerID == nil || *existing.OwnerID != uid {
		http.Error(w, `{"error":"forbidden"}`, http.StatusForbidden)
		return
	}
	if err := decks.ValidateInput(existing.Title, existing.Emoji, existing.Questions); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	d, err := s.Store.PublishDeck(r.Context(), uid, id)
	if err != nil {
		writeDeckMutateError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, deckToPayload(d))
}

func (s *Server) handleUnpublishDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, `{"error":"id required"}`, http.StatusBadRequest)
		return
	}
	uid := auth.UserIDFromContext(r.Context())
	d, err := s.Store.UnpublishDeck(r.Context(), uid, id)
	if err != nil {
		writeDeckMutateError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, deckToPayload(d))
}

type reportRequest struct {
	Reason string `json:"reason"`
}

func (s *Server) handleReportDeck(w http.ResponseWriter, r *http.Request) {
	if !s.storeReady(w) {
		return
	}
	id := r.PathValue("id")
	if id == "" {
		http.Error(w, `{"error":"id required"}`, http.StatusBadRequest)
		return
	}
	var req reportRequest
	if err := json.NewDecoder(io.LimitReader(r.Body, 1<<16)).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return
	}
	reason := strings.TrimSpace(req.Reason)
	if reason == "" {
		http.Error(w, `{"error":"reason required"}`, http.StatusBadRequest)
		return
	}
	if len(reason) > 500 {
		reason = reason[:500]
	}
	uid := auth.UserIDFromContext(r.Context())
	rep, err := s.Store.CreateReport(r.Context(), uid, id, reason)
	if err != nil {
		switch {
		case errors.Is(err, store.ErrNotFound):
			http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		case errors.Is(err, store.ErrConflict):
			http.Error(w, `{"error":"already reported"}`, http.StatusConflict)
		case errors.Is(err, store.ErrForbidden):
			http.Error(w, `{"error":"forbidden"}`, http.StatusForbidden)
		default:
			log.Printf("create report: %v", err)
			http.Error(w, `{"error":"could not create report"}`, http.StatusInternalServerError)
		}
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{
		"id":        rep.ID.String(),
		"deckId":    rep.DeckID,
		"reason":    rep.Reason,
		"status":    rep.Status,
		"createdAt": rep.CreatedAt.UTC().Format(time.RFC3339),
	})
}

type reportPayload struct {
	ID           string `json:"id"`
	DeckID       string `json:"deckId"`
	ReporterID   string `json:"reporterId"`
	Reason       string `json:"reason"`
	Status       string `json:"status"`
	CreatedAt    string `json:"createdAt"`
	DeckTitle    string `json:"deckTitle"`
	DeckEmoji    string `json:"deckEmoji"`
	ReporterName string `json:"reporterName"`
}

func (s *Server) handleListReports(w http.ResponseWriter, r *http.Request) {
	list, err := s.Store.ListOpenReports(r.Context())
	if err != nil {
		log.Printf("list reports: %v", err)
		http.Error(w, `{"error":"could not list reports"}`, http.StatusInternalServerError)
		return
	}
	out := make([]reportPayload, 0, len(list))
	for _, rep := range list {
		out = append(out, reportPayload{
			ID:           rep.ID.String(),
			DeckID:       rep.DeckID,
			ReporterID:   rep.ReporterID.String(),
			Reason:       rep.Reason,
			Status:       rep.Status,
			CreatedAt:    rep.CreatedAt.UTC().Format(time.RFC3339),
			DeckTitle:    rep.DeckTitle,
			DeckEmoji:    rep.DeckEmoji,
			ReporterName: rep.ReporterName,
		})
	}
	writeJSON(w, http.StatusOK, out)
}

type resolveReportRequest struct {
	Action string `json:"action"`
}

func (s *Server) handleResolveReport(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	reportID, err := uuid.Parse(idStr)
	if err != nil {
		http.Error(w, `{"error":"invalid id"}`, http.StatusBadRequest)
		return
	}
	var req resolveReportRequest
	if err := json.NewDecoder(io.LimitReader(r.Body, 1<<16)).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return
	}
	action := strings.TrimSpace(req.Action)
	if action != "dismiss" && action != "remove" {
		http.Error(w, `{"error":"action must be dismiss or remove"}`, http.StatusBadRequest)
		return
	}
	if err := s.Store.ResolveReport(r.Context(), reportID, action); err != nil {
		switch {
		case errors.Is(err, store.ErrNotFound):
			http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
		case errors.Is(err, store.ErrConflict):
			http.Error(w, `{"error":"already resolved"}`, http.StatusConflict)
		default:
			log.Printf("resolve report: %v", err)
			http.Error(w, `{"error":"could not resolve report"}`, http.StatusInternalServerError)
		}
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func decodeDeckWrite(w http.ResponseWriter, r *http.Request) (deckWriteRequest, bool) {
	var req deckWriteRequest
	if err := json.NewDecoder(io.LimitReader(r.Body, 1<<20)).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return req, false
	}
	return req, true
}

func writeDeckMutateError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, store.ErrNotFound):
		http.Error(w, `{"error":"not found"}`, http.StatusNotFound)
	case errors.Is(err, store.ErrForbidden):
		http.Error(w, `{"error":"forbidden"}`, http.StatusForbidden)
	default:
		log.Printf("deck mutate: %v", err)
		http.Error(w, `{"error":"could not update deck"}`, http.StatusInternalServerError)
	}
}

func canViewDeck(d *store.Deck, viewerID uuid.UUID) bool {
	if d.IsBuiltin {
		return true
	}
	if d.Visibility == store.VisibilityPublic {
		return true
	}
	if d.OwnerID == nil {
		return false
	}
	return viewerID != uuid.Nil && *d.OwnerID == viewerID
}

func optionalUserID(r *http.Request, authSvc *auth.Service) uuid.UUID {
	if authSvc == nil {
		return uuid.Nil
	}
	raw := r.Header.Get("Authorization")
	if !strings.HasPrefix(raw, "Bearer ") {
		return uuid.Nil
	}
	id, err := authSvc.ParseJWT(strings.TrimPrefix(raw, "Bearer "))
	if err != nil {
		return uuid.Nil
	}
	return id
}
