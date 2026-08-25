package game

import (
	"errors"
	"fmt"
	"time"
)

var (
	ErrNotHost          = errors.New("only the host can do that")
	ErrWrongPhase       = errors.New("action not allowed in this phase")
	ErrAlreadySubmitted = errors.New("already submitted")
	ErrInvalidRanking   = errors.New("ranking must contain each option exactly once")
	ErrNotEnoughPlayers = errors.New("need at least 2 players to start")
	ErrInvalidMode      = errors.New("invalid mode")
	ErrInvalidRounds    = errors.New("rounds must be between 1 and 20")
	ErrNoQuestions      = errors.New("deck has no questions")
	ErrInvalidDecks     = errors.New("select between 1 and 5 unique decks")
	ErrPlayerNotFound   = errors.New("player not found")
	ErrAlreadyReady     = errors.New("already ready")
	ErrOnlySubjectSkip  = errors.New("only the subject can skip")
	ErrNoSkipsRemaining = errors.New("no skips remaining")
	ErrNoReplacement    = errors.New("no replacement question available")
	ErrStaleQuestion    = errors.New("question is no longer current")
	ErrGamePaused       = errors.New("game is paused while a player reconnects")
)

// NewState creates a lobby state for a room.
func NewState(code string) *State {
	return &State{
		Code:        code,
		Phase:       PhaseLobby,
		Mode:        ModeCoop,
		DeckID:      "food",
		DeckIDs:     []string{"food"},
		TotalRounds: 5,
		Players:     []*Player{},
	}
}

// UpdateSettings changes the authoritative lobby/rematch settings without
// exposing deck contents. Access to each deck is resolved by the room first.
func (s *State) UpdateSettings(hostID string, mode Mode, deckIDs []string, totalRounds int, selected []DeckInfo) error {
	if s.Phase != PhaseLobby && s.Phase != PhaseGameOver {
		return ErrWrongPhase
	}
	host := s.Host()
	if host == nil || host.ID != hostID {
		return ErrNotHost
	}
	if mode != ModeCoop && mode != ModeVersus {
		return ErrInvalidMode
	}
	if totalRounds < 1 || totalRounds > 20 {
		return ErrInvalidRounds
	}
	if !validUniqueDeckIDs(deckIDs) {
		return ErrInvalidDecks
	}
	s.Mode = mode
	s.DeckIDs = append([]string(nil), deckIDs...)
	s.DeckID = deckIDs[0]
	s.TotalRounds = totalRounds
	s.SelectedDecks = append([]DeckInfo(nil), selected...)
	return nil
}

func (s *State) Host() *Player {
	for _, p := range s.Players {
		if p.IsHost {
			return p
		}
	}
	return nil
}

func (s *State) PlayerByID(id string) *Player {
	for _, p := range s.Players {
		if p.ID == id {
			return p
		}
	}
	return nil
}

func (s *State) ConnectedCount() int {
	n := 0
	for _, p := range s.Players {
		if p.Connected {
			n++
		}
	}
	return n
}

func (s *State) IsActiveGame() bool {
	return s.Phase == PhaseRoundSubmit || s.Phase == PhaseRoundReveal
}

// ElectConnectedHost retains a connected host when possible and otherwise
// promotes the first connected player in stable room order.
func (s *State) ElectConnectedHost() {
	for _, p := range s.Players {
		if p.IsHost && p.Connected {
			return
		}
	}
	for _, p := range s.Players {
		p.IsHost = false
	}
	for _, p := range s.Players {
		if p.Connected {
			p.IsHost = true
			return
		}
	}
}

// PauseForReconnect freezes an active round when fewer than two players are
// connected. The original round deadline is restored if quorum returns.
func (s *State) PauseForReconnect(now time.Time, grace time.Duration) bool {
	if !s.IsActiveGame() || s.Paused || s.ConnectedCount() >= 2 || s.CurrentRound == nil {
		return false
	}
	remaining := s.CurrentRound.Deadline.Sub(now)
	if remaining < 0 {
		remaining = 0
	}
	s.Paused = true
	s.PauseReason = "player_disconnected"
	s.ReconnectBy = now.Add(grace)
	s.PausedRemaining = remaining
	s.CurrentRound.Deadline = time.Time{}
	return true
}

func (s *State) ResumeFromPause(now time.Time) bool {
	if !s.Paused || s.ConnectedCount() < 2 || s.CurrentRound == nil {
		return false
	}
	s.CurrentRound.Deadline = now.Add(s.PausedRemaining)
	s.Paused = false
	s.PauseReason = ""
	s.ReconnectBy = time.Time{}
	s.PausedRemaining = 0
	return true
}

// AbortToLobby ends an unplayable game without producing a GAME_OVER edge, so
// partial scores are never persisted. Lobby settings remain available.
func (s *State) AbortToLobby(reason string) {
	s.Phase = PhaseLobby
	s.CurrentRound = nil
	s.TeamScore = 0
	s.Questions = nil
	s.QuestionCursor = 0
	s.TriedQuestionIDs = nil
	s.SkipsRemaining = nil
	s.SubjectOrder = nil
	s.RoundIndex = 0
	s.FinishedRounds = nil
	s.Paused = false
	s.PauseReason = ""
	s.ReconnectBy = time.Time{}
	s.PausedRemaining = 0
	s.AbortReason = reason
	for _, p := range s.Players {
		p.Score = 0
	}
}

// StartGame begins a game. questions must already be selected for the run.
func (s *State) StartGame(hostID string, mode Mode, deckIDs []string, totalRounds int, questions []Question, selected []DeckInfo) error {
	if s.Phase != PhaseLobby && s.Phase != PhaseGameOver {
		return ErrWrongPhase
	}
	host := s.Host()
	if host == nil || host.ID != hostID {
		return ErrNotHost
	}
	if s.ConnectedCount() < 2 {
		return ErrNotEnoughPlayers
	}
	if mode != ModeCoop && mode != ModeVersus {
		return ErrInvalidMode
	}
	if totalRounds < 1 || totalRounds > 20 {
		return ErrInvalidRounds
	}
	if len(questions) == 0 {
		return ErrNoQuestions
	}
	if !validUniqueDeckIDs(deckIDs) {
		return ErrInvalidDecks
	}

	for _, p := range s.Players {
		p.Score = 0
	}
	s.TeamScore = 0
	s.Mode = mode
	s.DeckIDs = append([]string(nil), deckIDs...)
	s.DeckID = deckIDs[0]
	s.SelectedDecks = append([]DeckInfo(nil), selected...)
	s.TotalRounds = totalRounds
	if totalRounds > len(questions) {
		s.TotalRounds = len(questions)
	}

	s.Questions = append([]Question(nil), questions...)
	s.QuestionCursor = 0
	s.TriedQuestionIDs = nil

	s.SubjectOrder = nil
	s.SkipsRemaining = make(map[string]int)
	for _, p := range s.Players {
		if p.Connected {
			s.SubjectOrder = append(s.SubjectOrder, p.ID)
			s.SkipsRemaining[p.ID] = DefaultSkipsPerPlayer
		}
	}
	s.RoundIndex = 0
	s.FinishedRounds = nil
	s.Paused = false
	s.PauseReason = ""
	s.ReconnectBy = time.Time{}
	s.PausedRemaining = 0
	s.AbortReason = ""
	return s.beginRound()
}

func (s *State) beginRound() error {
	if s.RoundIndex >= s.TotalRounds {
		s.Phase = PhaseGameOver
		s.CurrentRound = nil
		return nil
	}
	subjectID, ok := s.connectedSubjectForRound()
	if !ok {
		return ErrNotEnoughPlayers
	}
	q, ok := s.takeNextQuestion()
	if !ok {
		return ErrNoQuestions
	}
	s.CurrentRound = &Round{
		Index:       s.RoundIndex,
		SubjectID:   subjectID,
		Question:    q,
		Predictions: make(map[string][]string),
		Submitted:   make(map[string]bool),
		Ready:       make(map[string]bool),
		Scores:      make(map[string]int),
		Deadline:    time.Now().Add(SubmitDuration),
	}
	s.TriedQuestionIDs = map[string]bool{q.ID: true}
	s.Phase = PhaseRoundSubmit
	return nil
}

func (s *State) connectedSubjectForRound() (string, bool) {
	if len(s.SubjectOrder) == 0 {
		return "", false
	}
	for offset := 0; offset < len(s.SubjectOrder); offset++ {
		id := s.SubjectOrder[(s.RoundIndex+offset)%len(s.SubjectOrder)]
		if p := s.PlayerByID(id); p != nil && p.Connected {
			return id, true
		}
	}
	return "", false
}

func (s *State) takeNextQuestion() (Question, bool) {
	if s.QuestionCursor < 0 || s.QuestionCursor >= len(s.Questions) {
		return Question{}, false
	}
	q := s.Questions[s.QuestionCursor]
	s.QuestionCursor++
	return q, true
}

func (s *State) hasSkipReplacement() bool {
	for i := s.QuestionCursor; i < len(s.Questions); i++ {
		if !s.TriedQuestionIDs[s.Questions[i].ID] {
			return true
		}
	}
	return false
}

// takeSkipReplacement selects a question which has not already appeared in
// this round. Questions skipped earlier in the round can be present later in
// the pool because they remain valid for a future subject.
func (s *State) takeSkipReplacement() (Question, bool) {
	for i := s.QuestionCursor; i < len(s.Questions); i++ {
		if s.TriedQuestionIDs[s.Questions[i].ID] {
			continue
		}
		s.Questions[s.QuestionCursor], s.Questions[i] = s.Questions[i], s.Questions[s.QuestionCursor]
		return s.takeNextQuestion()
	}
	return Question{}, false
}

// CanSkipQuestion returns the server-authoritative eligibility used for a
// recipient's room snapshot. SkipQuestion repeats these checks before mutation.
func (s *State) CanSkipQuestion(playerID string) bool {
	if s.Paused || s.Phase != PhaseRoundSubmit || s.CurrentRound == nil {
		return false
	}
	r := s.CurrentRound
	p := s.PlayerByID(playerID)
	return p != nil && p.Connected && playerID == r.SubjectID &&
		!r.Submitted[playerID] && s.SkipsRemaining[playerID] > 0 && s.hasSkipReplacement()
}

// SkipQuestion atomically replaces the in-progress question. The current
// subject, round index, and deadline are preserved, while all answers tied to
// the old question are discarded.
func (s *State) SkipQuestion(playerID string, roundIndex int, questionID string) error {
	if s.Paused {
		return ErrGamePaused
	}
	if s.Phase != PhaseRoundSubmit || s.CurrentRound == nil {
		return ErrWrongPhase
	}
	r := s.CurrentRound
	p := s.PlayerByID(playerID)
	if p == nil || !p.Connected {
		return ErrPlayerNotFound
	}
	if playerID != r.SubjectID {
		return ErrOnlySubjectSkip
	}
	if roundIndex != r.Index || questionID == "" || questionID != r.Question.ID {
		return ErrStaleQuestion
	}
	if r.Submitted[playerID] {
		return ErrAlreadySubmitted
	}
	if s.SkipsRemaining[playerID] <= 0 {
		return ErrNoSkipsRemaining
	}
	if !s.hasSkipReplacement() {
		return ErrNoReplacement
	}

	q, ok := s.takeSkipReplacement()
	if !ok {
		return ErrNoReplacement
	}
	// A skipped question was not played, so return it to the tail for a later
	// round. This keeps the future schedule viable without showing it again to
	// the same subject in the current round.
	s.Questions = append(s.Questions, r.Question)
	s.TriedQuestionIDs[q.ID] = true
	s.SkipsRemaining[playerID]--
	s.CurrentRound = &Round{
		Index:       r.Index,
		SubjectID:   r.SubjectID,
		Question:    q,
		Predictions: make(map[string][]string),
		Submitted:   make(map[string]bool),
		Ready:       make(map[string]bool),
		Scores:      make(map[string]int),
		Deadline:    r.Deadline,
	}
	return nil
}

// SubmitEntry is called by any player during ROUND_SUBMIT.
// The subject stores an honest ranking; everyone else stores a prediction.
func (s *State) SubmitEntry(playerID string, ranking []string) error {
	if s.Paused {
		return ErrGamePaused
	}
	if s.Phase != PhaseRoundSubmit {
		return ErrWrongPhase
	}
	r := s.CurrentRound
	if r == nil {
		return ErrWrongPhase
	}
	p := s.PlayerByID(playerID)
	if p == nil || !p.Connected {
		return ErrPlayerNotFound
	}
	if r.Submitted[playerID] {
		return ErrAlreadySubmitted
	}
	if err := validateRanking(r.Question.Options, ranking); err != nil {
		return err
	}

	if playerID == r.SubjectID {
		r.SubjectRanking = append([]string(nil), ranking...)
	} else {
		r.Predictions[playerID] = append([]string(nil), ranking...)
	}
	r.Submitted[playerID] = true

	if s.allSubmitted() {
		s.scoreRound()
		s.enterReveal()
	}
	return nil
}

// SubmitEntryForQuestion rejects delayed commands that were created for a
// question which has since been skipped or advanced.
func (s *State) SubmitEntryForQuestion(playerID string, roundIndex int, questionID string, ranking []string) error {
	if s.Phase != PhaseRoundSubmit || s.CurrentRound == nil {
		return ErrWrongPhase
	}
	if roundIndex != s.CurrentRound.Index || questionID == "" || questionID != s.CurrentRound.Question.ID {
		return ErrStaleQuestion
	}
	return s.SubmitEntry(playerID, ranking)
}

// SubmitRanking is an alias for SubmitEntry (legacy clients / subject).
func (s *State) SubmitRanking(playerID string, ranking []string) error {
	return s.SubmitEntry(playerID, ranking)
}

// SubmitPrediction is an alias for SubmitEntry (legacy clients / predictors).
func (s *State) SubmitPrediction(playerID string, ranking []string) error {
	return s.SubmitEntry(playerID, ranking)
}

func (s *State) allSubmitted() bool {
	r := s.CurrentRound
	if r == nil {
		return false
	}
	if subject := s.PlayerByID(r.SubjectID); subject == nil ||
		(!subject.Connected && len(r.SubjectRanking) == 0) {
		return false
	}
	for _, p := range s.Players {
		if !p.Connected {
			continue
		}
		if !r.Submitted[p.ID] {
			return false
		}
	}
	return true
}

func (s *State) enterReveal() {
	r := s.CurrentRound
	r.Ready = make(map[string]bool)
	r.Deadline = time.Now().Add(RevealDuration)
	s.Phase = PhaseRoundReveal
}

func (s *State) scoreRound() {
	r := s.CurrentRound
	total := 0
	count := 0
	for playerID, pred := range r.Predictions {
		pts := ScorePrediction(r.SubjectRanking, pred)
		r.Scores[playerID] = pts
		total += pts
		count++
		if s.Mode == ModeVersus {
			if p := s.PlayerByID(playerID); p != nil {
				p.Score += pts
			}
		}
	}
	if count > 0 {
		r.TeamScore = total / count
	}
	if s.Mode == ModeCoop {
		s.TeamScore += r.TeamScore
	}
}

// ForceSubmitDeadline fills missing submissions with the default option order,
// then scores and moves to reveal.
func (s *State) ForceSubmitDeadline() error {
	if s.Paused {
		return ErrGamePaused
	}
	if s.Phase != PhaseRoundSubmit {
		return ErrWrongPhase
	}
	r := s.CurrentRound
	defaults := append([]string(nil), r.Question.Options...)
	for _, p := range s.Players {
		if !p.Connected || r.Submitted[p.ID] {
			continue
		}
		if p.ID == r.SubjectID {
			r.SubjectRanking = append([]string(nil), defaults...)
		} else {
			r.Predictions[p.ID] = append([]string(nil), defaults...)
		}
		r.Submitted[p.ID] = true
	}
	// Ensure subject ranking exists even if subject disconnected mid-round.
	if len(r.SubjectRanking) == 0 {
		r.SubjectRanking = append([]string(nil), defaults...)
	}
	s.scoreRound()
	s.enterReveal()
	return nil
}

// MarkReady marks a player ready on the reveal screen.
func (s *State) MarkReady(playerID string) error {
	if s.Paused {
		return ErrGamePaused
	}
	if s.Phase != PhaseRoundReveal {
		return ErrWrongPhase
	}
	r := s.CurrentRound
	if r == nil {
		return ErrWrongPhase
	}
	p := s.PlayerByID(playerID)
	if p == nil || !p.Connected {
		return ErrPlayerNotFound
	}
	if r.Ready[playerID] {
		return ErrAlreadyReady
	}
	r.Ready[playerID] = true
	if s.allReady() {
		return s.advanceRound()
	}
	return nil
}

func (s *State) allReady() bool {
	r := s.CurrentRound
	for _, p := range s.Players {
		if !p.Connected {
			continue
		}
		if !r.Ready[p.ID] {
			return false
		}
	}
	return true
}

func (s *State) advanceRound() error {
	if r := s.CurrentRound; r != nil {
		scores := make(map[string]int, len(r.Scores))
		for k, v := range r.Scores {
			scores[k] = v
		}
		s.FinishedRounds = append(s.FinishedRounds, FinishedRound{
			Index:     r.Index,
			DeckID:    r.Question.DeckID,
			SubjectID: r.SubjectID,
			TeamScore: r.TeamScore,
			Scores:    scores,
		})
	}
	s.RoundIndex++
	return s.beginRound()
}

func validUniqueDeckIDs(ids []string) bool {
	if len(ids) < 1 || len(ids) > 5 {
		return false
	}
	seen := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		if id == "" {
			return false
		}
		if _, ok := seen[id]; ok {
			return false
		}
		seen[id] = struct{}{}
	}
	return true
}

// NextRound is a host force-advance from reveal (kept for host override).
func (s *State) NextRound(hostID string) error {
	if s.Paused {
		return ErrGamePaused
	}
	if s.Phase != PhaseRoundReveal {
		return ErrWrongPhase
	}
	host := s.Host()
	if host == nil || host.ID != hostID {
		return ErrNotHost
	}
	return s.advanceRound()
}

// ForceNextRound advances from reveal on timer expiry.
func (s *State) ForceNextRound() error {
	if s.Paused {
		return ErrGamePaused
	}
	if s.Phase != PhaseRoundReveal {
		return ErrWrongPhase
	}
	return s.advanceRound()
}

// RemovePlayer permanently removes an explicit leaver. Temporary network
// disconnects must keep the player record so reconnect tokens remain valid.
func (s *State) RemovePlayer(playerID string, now time.Time) bool {
	playerIndex := -1
	for i, p := range s.Players {
		if p.ID == playerID {
			playerIndex = i
			break
		}
	}
	if playerIndex < 0 {
		return false
	}

	s.Players = append(s.Players[:playerIndex], s.Players[playerIndex+1:]...)
	for i, id := range s.SubjectOrder {
		if id == playerID {
			s.SubjectOrder = append(s.SubjectOrder[:i], s.SubjectOrder[i+1:]...)
			break
		}
	}
	delete(s.SkipsRemaining, playerID)

	if s.IsActiveGame() && s.ConnectedCount() < 2 {
		s.AbortToLobby("not_enough_players")
		s.ElectConnectedHost()
		return true
	}

	if s.IsActiveGame() && s.CurrentRound != nil {
		r := s.CurrentRound
		delete(r.Predictions, playerID)
		delete(r.Submitted, playerID)
		delete(r.Ready, playerID)
		delete(r.Scores, playerID)

		if r.SubjectID == playerID && s.Phase == PhaseRoundSubmit {
			if subjectID, ok := s.connectedSubjectForRound(); ok {
				r.SubjectID = subjectID
				s.TriedQuestionIDs = map[string]bool{r.Question.ID: true}
				r.SubjectRanking = nil
				r.Predictions = make(map[string][]string)
				r.Submitted = make(map[string]bool)
				r.Ready = make(map[string]bool)
				r.Scores = make(map[string]int)
				r.TeamScore = 0
				r.Deadline = now.Add(SubmitDuration)
			}
		} else if s.Phase == PhaseRoundSubmit && s.allSubmitted() {
			s.scoreRound()
			s.enterReveal()
		} else if s.Phase == PhaseRoundReveal && s.allReady() {
			_ = s.advanceRound()
		}
	}

	s.ElectConnectedHost()
	return true
}

// ForceReveal is kept for compatibility; prefer ForceSubmitDeadline.
func (s *State) ForceReveal() error {
	return s.ForceSubmitDeadline()
}

func validateRanking(options, ranking []string) error {
	if len(ranking) != len(options) {
		return ErrInvalidRanking
	}
	seen := make(map[string]bool, len(ranking))
	optSet := make(map[string]bool, len(options))
	for _, o := range options {
		optSet[o] = true
	}
	for _, r := range ranking {
		if !optSet[r] || seen[r] {
			return fmt.Errorf("%w: %q", ErrInvalidRanking, r)
		}
		seen[r] = true
	}
	return nil
}
