package ws

import (
	"encoding/json"
)

// Client → server message types.
const (
	TypeJoinRoom         = "join_room"
	TypeReconnect        = "reconnect"
	TypeStartGame        = "start_game"
	TypeUpdateSettings   = "update_game_settings"
	TypeSubmitRanking    = "submit_ranking"
	TypeSubmitPrediction = "submit_prediction" // alias for submit_ranking
	TypeSkipQuestion     = "skip_question"
	TypeReady            = "ready"
	TypeNextRound        = "next_round"
	TypeLeaveRoom        = "leave_room"
	TypeAttachAccount    = "attach_account"
)

// Server → client message types.
const (
	TypeRoomState = "room_state"
	TypeError     = "error"
	TypeWelcome   = "welcome"
)

// Envelope is the wire format for all messages.
type Envelope struct {
	Type    string          `json:"type"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

type JoinRoomPayload struct {
	Nickname  string `json:"nickname"`
	AuthToken string `json:"authToken,omitempty"` // optional JWT; absent = anonymous
}

type ReconnectPayload struct {
	PlayerID       string `json:"playerId"`
	ReconnectToken string `json:"reconnectToken"`
}

type AttachAccountPayload struct {
	AuthToken string `json:"authToken"`
}

type StartGamePayload struct {
	Mode       string   `json:"mode"`
	DeckIDs    []string `json:"deckIds,omitempty"`
	DeckID     string   `json:"deckId,omitempty"`
	Rounds     int      `json:"rounds"`
	MusicTrack string   `json:"musicTrack,omitempty"`
	MusicScope string   `json:"musicScope,omitempty"`
}

type UpdateGameSettingsPayload = StartGamePayload

func (p StartGamePayload) CanonicalDeckIDs() []string {
	if len(p.DeckIDs) > 0 {
		return append([]string(nil), p.DeckIDs...)
	}
	if p.DeckID != "" {
		return []string{p.DeckID}
	}
	return nil
}

type RankingPayload struct {
	RoundIndex int      `json:"roundIndex"`
	QuestionID string   `json:"questionId"`
	Ranking    []string `json:"ranking"`
}

type SkipQuestionPayload struct {
	RoundIndex int    `json:"roundIndex"`
	QuestionID string `json:"questionId"`
}

type ErrorPayload struct {
	Message string `json:"message"`
}

type WelcomePayload struct {
	PlayerID       string `json:"playerId"`
	ReconnectToken string `json:"reconnectToken"`
	IsHost         bool   `json:"isHost"`
}

func Encode(typ string, payload any) ([]byte, error) {
	var raw json.RawMessage
	if payload != nil {
		b, err := json.Marshal(payload)
		if err != nil {
			return nil, err
		}
		raw = b
	}
	return json.Marshal(Envelope{Type: typ, Payload: raw})
}

func DecodePayload[T any](env Envelope) (T, error) {
	var v T
	if len(env.Payload) == 0 {
		return v, nil
	}
	err := json.Unmarshal(env.Payload, &v)
	return v, err
}
