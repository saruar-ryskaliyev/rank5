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
	ModeCoop Mode = "coop"
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

// Question kinds. An empty kind is the original five-authored-options shape.
const (
	QuestionKindOptions = ""
	QuestionKindPlayers = "players"
)

const (
	// MinPlayersForPlayerQuestions keeps a players-kind ranking meaningful:
	// with two people the honest order carries almost no information.
	MinPlayersForPlayerQuestions = 3
	// SubjectPlaceholder is replaced with the round subject's nickname when the
	// round begins, so one template can be personalized for every player.
	SubjectPlaceholder = "{subject}"
)

// Question is one prompt with its rankable options. Options-kind questions
// carry exactly five authored options; players-kind questions author none and
// receive the room's players as options when the round is rendered.
type Question struct {
	ID      string   `json:"id"`
	DeckID  string   `json:"deckId,omitempty"`
	Kind    string   `json:"kind,omitempty"`
	Prompt  string   `json:"prompt"`
	Options []string `json:"options"`
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
	Index     int      `json:"index"`
	SubjectID string   `json:"subjectId"`
	Question  Question `json:"question"`
	// Template is the deck question before personalization. Rendering replaces
	// the subject placeholder and can fill in player options, so a skipped
	// question is deferred as its template rather than one subject's copy.
	Template       Question            `json:"-"`
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
	// PlayerQuestionCount is how many questions rank the players themselves.
	// Lobbies use it to warn that those questions need at least 3 players.
	PlayerQuestionCount int `json:"playerQuestionCount,omitempty"`
}

// CountPlayerQuestions returns how many questions rank the room's players.
func CountPlayerQuestions(questions []Question) int {
	n := 0
	for _, q := range questions {
		if q.Kind == QuestionKindPlayers {
			n++
		}
	}
	return n
}
