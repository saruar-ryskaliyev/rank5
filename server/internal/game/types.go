package game

import "time"

// Phase is the room lifecycle stage.
type Phase string

const (
	PhaseLobby       Phase = "LOBBY"
	PhaseRoundSubmit Phase = "ROUND_SUBMIT"
	PhaseRoundReveal Phase = "ROUND_REVEAL"
	PhaseGameOver    Phase = "GAME_OVER"
)

// Mode is the scoring mode for a game.
type Mode string

const (
	ModeCoop   Mode = "coop"
	ModeVersus Mode = "versus"
)

const (
	MusicTrackFiveAlive        = "five_alive"
	MusicTrackDancehallShuffle = "dancehall_shuffle"
	MusicTrackEasyGlow         = "easy_glow"
	MusicTrackT8Bounce         = "t8_bounce"
	MusicScopeLobby            = "lobby"
	MusicScopeLobbyAndGame     = "lobby_and_game"
)

// Phase durations.
const (
	SubmitDuration = 60 * time.Second
	RevealDuration = 20 * time.Second
	DeadlineGrace  = 3 * time.Second
	ReconnectGrace = 45 * time.Second
	// DefaultSkipsPerPlayer is reset for every new game and rematch.
	DefaultSkipsPerPlayer = 8
)

// Player is a participant in a room.
type Player struct {
	ID             string `json:"id"`
	Nickname       string `json:"nickname"`
	IsHost         bool   `json:"isHost"`
	Connected      bool   `json:"connected"`
	Score          int    `json:"score"`
	ReconnectToken string `json:"-"`
	// UserID is set when the player joined with a valid auth JWT. Not sent to clients.
	UserID string `json:"-"`
}

// FinishedRound is a scored round kept for persistence at GAME_OVER.
type FinishedRound struct {
	Index     int            `json:"index"`
	DeckID    string         `json:"deckId,omitempty"`
	SubjectID string         `json:"subjectId"`
	TeamScore int            `json:"teamScore"`
	Scores    map[string]int `json:"scores"` // playerID -> points
}

// Question is one prompt with exactly five options.
type Question struct {
	ID      string   `json:"id"`
	DeckID  string   `json:"deckId,omitempty"`
	Prompt  string   `json:"prompt"`
	Options []string `json:"options"` // length 5
}

// Deck is a named collection of questions.
type Deck struct {
	ID        string     `json:"id"`
	Name      string     `json:"name"`
	Emoji     string     `json:"emoji,omitempty"`
	Questions []Question `json:"questions"`
}

// Round holds per-round data.
type Round struct {
	Index          int                 `json:"index"`
	SubjectID      string              `json:"subjectId"`
	Question       Question            `json:"question"`
	SubjectRanking []string            `json:"-"` // option texts in ranked order (best first)
	Predictions    map[string][]string `json:"-"` // playerID -> ranking
	Submitted      map[string]bool     `json:"submitted"`
	Ready          map[string]bool     `json:"ready"`
	Scores         map[string]int      `json:"scores"`
	TeamScore      int                 `json:"teamScore"`
	Deadline       time.Time           `json:"-"`
}

// State is the authoritative room game state.
type State struct {
	Code           string     `json:"code"`
	Phase          Phase      `json:"phase"`
	Mode           Mode       `json:"mode"`
	DeckID         string     `json:"deckId"`
	DeckIDs        []string   `json:"deckIds"`
	SelectedDecks  []DeckInfo `json:"selectedDecks"`
	TotalRounds    int        `json:"totalRounds"`
	MusicTrack     string     `json:"musicTrack,omitempty"`
	MusicScope     string     `json:"musicScope"`
	Players        []*Player  `json:"players"`
	CurrentRound   *Round     `json:"currentRound,omitempty"`
	TeamScore      int        `json:"teamScore"`
	Questions      []Question `json:"-"`
	QuestionCursor int        `json:"-"`
	// TriedQuestionIDs prevents a skip from cycling back to a question that
	// was already shown during the current round. Skipped questions remain in
	// the pool so they can still be used for a different subject later.
	TriedQuestionIDs map[string]bool `json:"-"`
	SkipsRemaining   map[string]int  `json:"-"`
	SubjectOrder     []string        `json:"-"`
	RoundIndex       int             `json:"-"`
	FinishedRounds   []FinishedRound `json:"-"`
	Paused           bool            `json:"-"`
	PauseReason      string          `json:"-"`
	ReconnectBy      time.Time       `json:"-"`
	PausedRemaining  time.Duration   `json:"-"`
	AbortReason      string          `json:"-"`
}

// DeckInfo is a lightweight deck listing for clients.
type DeckInfo struct {
	ID            string `json:"id"`
	Name          string `json:"name"`
	Emoji         string `json:"emoji,omitempty"`
	QuestionCount int    `json:"questionCount,omitempty"`
}
