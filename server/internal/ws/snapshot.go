package ws

import (
	"github.com/saruar/rank5/server/internal/game"
)

// PlayerView is a client-safe player snapshot.
type PlayerView struct {
	ID        string `json:"id"`
	Nickname  string `json:"nickname"`
	IsHost    bool   `json:"isHost"`
	Connected bool   `json:"connected"`
	Score     int    `json:"score"`
}

// RoundView is a per-recipient round snapshot.
type RoundView struct {
	Index          int                 `json:"index"`
	SubjectID      string              `json:"subjectId"`
	Question       game.Question       `json:"question"`
	Submitted      map[string]bool     `json:"submitted"`
	Ready          map[string]bool     `json:"ready,omitempty"`
	DeadlineMs     int64               `json:"deadlineMs,omitempty"`
	MyRanking      []string            `json:"myRanking,omitempty"`
	SubjectRanking []string            `json:"subjectRanking,omitempty"`
	Predictions    map[string][]string `json:"predictions,omitempty"`
	Scores         map[string]int      `json:"scores,omitempty"`
	TeamScore      int                 `json:"teamScore,omitempty"`
	SkipsRemaining int                 `json:"skipsRemaining"`
	CanSkip        bool                `json:"canSkip"`
}

// RoomStateView is the full snapshot pushed to each client.
type RoomStateView struct {
	Code          string          `json:"code"`
	Phase         game.Phase      `json:"phase"`
	Mode          game.Mode       `json:"mode"`
	DeckID        string          `json:"deckId"`
	DeckIDs       []string        `json:"deckIds"`
	SelectedDecks []game.DeckInfo `json:"selectedDecks"`
	TotalRounds   int             `json:"totalRounds"`
	Players       []PlayerView    `json:"players"`
	CurrentRound  *RoundView      `json:"currentRound,omitempty"`
	TeamScore     int             `json:"teamScore"`
	Decks         []game.DeckInfo `json:"decks,omitempty"`
	YouAre        string          `json:"youAre"`
	Paused        bool            `json:"paused"`
	PauseReason   string          `json:"pauseReason,omitempty"`
	ReconnectByMs int64           `json:"reconnectByMs,omitempty"`
	AbortReason   string          `json:"abortReason,omitempty"`
}

func snapshotFor(s *game.State, viewerID string, available []game.DeckInfo) RoomStateView {
	players := make([]PlayerView, 0, len(s.Players))
	for _, p := range s.Players {
		players = append(players, PlayerView{
			ID: p.ID, Nickname: p.Nickname, IsHost: p.IsHost,
			Connected: p.Connected, Score: p.Score,
		})
	}
	view := RoomStateView{
		Code: s.Code, Phase: s.Phase, Mode: s.Mode, DeckID: s.DeckID,
		DeckIDs:       append([]string(nil), s.DeckIDs...),
		SelectedDecks: append([]game.DeckInfo(nil), s.SelectedDecks...),
		TotalRounds:   s.TotalRounds, Players: players, TeamScore: s.TeamScore,
		YouAre: viewerID, Paused: s.Paused, PauseReason: s.PauseReason,
		AbortReason: s.AbortReason,
	}
	if !s.ReconnectBy.IsZero() {
		view.ReconnectByMs = s.ReconnectBy.UnixMilli()
	}
	if s.Phase == game.PhaseLobby || s.Phase == game.PhaseGameOver {
		view.Decks = available
	}
	if s.CurrentRound != nil {
		r := s.CurrentRound
		rv := &RoundView{
			Index:          r.Index,
			SubjectID:      r.SubjectID,
			Question:       r.Question,
			Submitted:      copyBoolMap(r.Submitted),
			Ready:          copyBoolMap(r.Ready),
			SkipsRemaining: s.SkipsRemaining[viewerID],
			CanSkip:        s.CanSkipQuestion(viewerID),
		}
		if !r.Deadline.IsZero() {
			rv.DeadlineMs = r.Deadline.UnixMilli()
		}
		// Personal ranking only — never leak others during submit.
		if viewerID == r.SubjectID && len(r.SubjectRanking) > 0 {
			rv.MyRanking = append([]string(nil), r.SubjectRanking...)
		}
		if pred, ok := r.Predictions[viewerID]; ok {
			rv.MyRanking = append([]string(nil), pred...)
		}
		if s.Phase == game.PhaseRoundReveal || s.Phase == game.PhaseGameOver {
			rv.SubjectRanking = append([]string(nil), r.SubjectRanking...)
			rv.Predictions = copyStringSliceMap(r.Predictions)
			rv.Scores = copyIntMap(r.Scores)
			rv.TeamScore = r.TeamScore
		}
		view.CurrentRound = rv
	}
	return view
}

func copyBoolMap(m map[string]bool) map[string]bool {
	if m == nil {
		return map[string]bool{}
	}
	out := make(map[string]bool, len(m))
	for k, v := range m {
		out[k] = v
	}
	return out
}

func copyIntMap(m map[string]int) map[string]int {
	out := make(map[string]int, len(m))
	for k, v := range m {
		out[k] = v
	}
	return out
}

func copyStringSliceMap(m map[string][]string) map[string][]string {
	out := make(map[string][]string, len(m))
	for k, v := range m {
		out[k] = append([]string(nil), v...)
	}
	return out
}
