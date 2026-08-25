package ws

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	mathrand "math/rand"
	"sync"
	"time"

	"github.com/coder/websocket"
	"github.com/google/uuid"
	"github.com/saruar/rank5/server/internal/auth"
	"github.com/saruar/rank5/server/internal/decks"
	"github.com/saruar/rank5/server/internal/game"
	"github.com/saruar/rank5/server/internal/store"
)

type connSlot struct {
	conn     *websocket.Conn
	playerID string
	send     chan []byte
	cancel   context.CancelFunc
}

// Room is a single room actor.
type Room struct {
	code       string
	state      *game.State
	events     chan roomEvent
	done       chan struct{}
	conns      map[string]*connSlot
	muIdle     sync.Mutex
	lastAct    time.Time
	stopOnce   sync.Once
	timer      *time.Timer
	pauseTimer *time.Timer
	auth       *auth.Service
	store      *store.Store
	savedGame  bool // one write per finished game (rematch resets via StartGame path)
	prevPhase  game.Phase
	// activeDecks caches only the selected metadata. Guests never receive the
	// host's complete private library.
	activeDecks []game.DeckInfo
}

type roomEvent struct {
	kind     string
	playerID string
	conn     *websocket.Conn
	slot     *connSlot
	payload  json.RawMessage
	ctx      context.Context
}

func NewRoom(code string, authSvc *auth.Service, st *store.Store) *Room {
	r := &Room{
		code:      code,
		state:     game.NewState(code),
		events:    make(chan roomEvent, 64),
		done:      make(chan struct{}),
		conns:     make(map[string]*connSlot),
		lastAct:   time.Now(),
		auth:      authSvc,
		store:     st,
		prevPhase: game.PhaseLobby,
	}
	go r.loop()
	return r
}

func (r *Room) Code() string { return r.code }

func (r *Room) Touch() {
	r.muIdle.Lock()
	r.lastAct = time.Now()
	r.muIdle.Unlock()
}

func (r *Room) IdleSince() time.Time {
	r.muIdle.Lock()
	defer r.muIdle.Unlock()
	return r.lastAct
}

func (r *Room) Stop() {
	r.stopOnce.Do(func() {
		r.clearTimer()
		r.clearPauseTimer()
		close(r.done)
	})
}

func (r *Room) Enqueue(ev roomEvent) {
	select {
	case r.events <- ev:
	case <-r.done:
	}
}

func (r *Room) AttachConn(ctx context.Context, conn *websocket.Conn) {
	r.Enqueue(roomEvent{kind: "attach", conn: conn, ctx: ctx})
}

func (r *Room) clearTimer() {
	if r.timer != nil {
		r.timer.Stop()
		r.timer = nil
	}
}

func (r *Room) clearPauseTimer() {
	if r.pauseTimer != nil {
		r.pauseTimer.Stop()
		r.pauseTimer = nil
	}
}

func (r *Room) schedulePauseDeadline() {
	r.clearPauseTimer()
	if !r.state.Paused || r.state.ReconnectBy.IsZero() {
		return
	}
	delay := time.Until(r.state.ReconnectBy)
	if delay < 0 {
		delay = 0
	}
	r.pauseTimer = time.AfterFunc(delay, func() {
		r.Enqueue(roomEvent{kind: "pause_timeout"})
	})
}

func (r *Room) scheduleDeadline() {
	r.clearTimer()
	if r.state.Paused {
		return
	}
	round := r.state.CurrentRound
	if round == nil || round.Deadline.IsZero() {
		return
	}
	phase := r.state.Phase
	if phase != game.PhaseRoundSubmit && phase != game.PhaseRoundReveal {
		return
	}
	delay := time.Until(round.Deadline) + game.DeadlineGrace
	if delay < 0 {
		delay = 0
	}
	r.timer = time.AfterFunc(delay, func() {
		r.Enqueue(roomEvent{kind: "timeout"})
	})
}

func (r *Room) loop() {
	for {
		select {
		case <-r.done:
			r.clearTimer()
			r.clearPauseTimer()
			for _, c := range r.conns {
				c.cancel()
			}
			return
		case ev := <-r.events:
			r.Touch()
			r.handle(ev)
		}
	}
}

func (r *Room) handle(ev roomEvent) {
	switch ev.kind {
	case "attach":
		r.handleAttach(ev)
	case "message":
		r.handleMessage(ev)
		r.afterStateChange()
	case "disconnect":
		r.handleDisconnect(ev.playerID, ev.slot)
	case "timeout":
		r.handleTimeout()
		r.afterStateChange()
	case "pause_timeout":
		r.handlePauseTimeout()
		r.afterStateChange()
	}
}

// afterStateChange detects the edge into GAME_OVER and persists once.
func (r *Room) afterStateChange() {
	phase := r.state.Phase
	if phase == game.PhaseGameOver && r.prevPhase != game.PhaseGameOver && !r.savedGame {
		r.persistResults()
		r.savedGame = true
	}
	if phase != game.PhaseGameOver {
		// Rematch / new game: allow another write when we next hit GAME_OVER.
		if phase == game.PhaseRoundSubmit || phase == game.PhaseLobby {
			r.savedGame = false
		}
	}
	r.prevPhase = phase
}

func (r *Room) persistResults() {
	if r.store == nil || !r.store.Enabled() {
		return
	}
	roundsJSON, err := json.Marshal(r.state.FinishedRounds)
	if err != nil {
		log.Printf("results marshal: %v", err)
		return
	}
	players := make([]store.ResultPlayer, 0, len(r.state.Players))
	for _, p := range r.state.Players {
		rp := store.ResultPlayer{
			PlayerID:       p.ID,
			Nickname:       p.Nickname,
			Score:          p.Score,
			ClaimTokenHash: store.HashClaimToken(p.ReconnectToken),
		}
		if p.UserID != "" {
			if id, err := uuid.Parse(p.UserID); err == nil {
				rp.UserID = &id
			}
		}
		players = append(players, rp)
	}
	result := store.GameResult{
		RoomCode:    r.state.Code,
		Mode:        string(r.state.Mode),
		DeckID:      r.state.DeckID,
		DeckIDs:     append([]string(nil), r.state.DeckIDs...),
		TotalRounds: r.state.TotalRounds,
		Rounds:      roundsJSON,
		Players:     players,
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := r.store.SaveGameResult(ctx, result); err != nil {
			log.Printf("save game result: %v", err)
		}
	}()
}

func (r *Room) handleTimeout() {
	if r.state.Paused {
		return
	}
	switch r.state.Phase {
	case game.PhaseRoundSubmit:
		if err := r.state.ForceSubmitDeadline(); err != nil {
			return
		}
		r.scheduleDeadline()
		r.broadcastState()
	case game.PhaseRoundReveal:
		if err := r.state.ForceNextRound(); err != nil {
			return
		}
		r.scheduleDeadline()
		r.broadcastState()
	}
}

func (r *Room) handlePauseTimeout() {
	if !r.state.Paused {
		return
	}
	if r.state.ConnectedCount() >= 2 {
		r.state.ResumeFromPause(time.Now())
		r.clearPauseTimer()
		r.scheduleDeadline()
		r.broadcastState()
		return
	}
	r.state.AbortToLobby("not_enough_players")
	r.state.ElectConnectedHost()
	r.clearTimer()
	r.clearPauseTimer()
	r.broadcastState()
}

func (r *Room) handleAttach(ev roomEvent) {
	tempID := "pending-" + randomID(4)
	ctx, cancel := context.WithCancel(ev.ctx)
	slot := &connSlot{
		conn:     ev.conn,
		playerID: tempID,
		send:     make(chan []byte, 16),
		cancel:   cancel,
	}
	r.conns[tempID] = slot
	go r.writePump(slot)
	go r.readPump(ctx, slot)
}

func (r *Room) readPump(ctx context.Context, slot *connSlot) {
	defer func() {
		r.Enqueue(roomEvent{kind: "disconnect", playerID: slot.playerID, slot: slot})
	}()
	for {
		_, data, err := slot.conn.Read(ctx)
		if err != nil {
			return
		}
		var env Envelope
		if err := json.Unmarshal(data, &env); err != nil {
			r.sendError(slot, "invalid message")
			continue
		}
		r.Enqueue(roomEvent{
			kind:     "message",
			playerID: slot.playerID,
			payload:  data,
		})
		_ = env
	}
}

func (r *Room) writePump(slot *connSlot) {
	for {
		select {
		case <-r.done:
			return
		case msg, ok := <-slot.send:
			if !ok {
				return
			}
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			err := slot.conn.Write(ctx, websocket.MessageText, msg)
			cancel()
			if err != nil {
				return
			}
		}
	}
}

func (r *Room) handleMessage(ev roomEvent) {
	var env Envelope
	if err := json.Unmarshal(ev.payload, &env); err != nil {
		return
	}
	slot := r.conns[ev.playerID]
	if slot == nil {
		return
	}

	switch env.Type {
	case TypeJoinRoom:
		p, err := DecodePayload[JoinRoomPayload](env)
		if err != nil || p.Nickname == "" {
			r.sendError(slot, "nickname required")
			return
		}
		r.doJoin(slot, p.Nickname, p.AuthToken)
	case TypeReconnect:
		p, err := DecodePayload[ReconnectPayload](env)
		if err != nil {
			r.sendError(slot, "invalid reconnect")
			return
		}
		r.doReconnect(slot, p.PlayerID, p.ReconnectToken)
	case TypeAttachAccount:
		if !r.requireIdentified(slot) {
			return
		}
		p, err := DecodePayload[AttachAccountPayload](env)
		if err != nil || p.AuthToken == "" {
			r.sendError(slot, "invalid account token")
			return
		}
		r.doAttachAccount(slot, p.AuthToken)
	case TypeLeaveRoom:
		if !r.requireIdentified(slot) {
			return
		}
		r.doLeave(slot)
	case TypeStartGame:
		if !r.requireIdentified(slot) {
			return
		}
		p, err := DecodePayload[StartGamePayload](env)
		if err != nil {
			r.sendError(slot, "invalid start_game")
			return
		}
		deckIDs := p.CanonicalDeckIDs()
		resolved, infos, err := r.resolveDecks(deckIDs, slot.playerID)
		if err != nil {
			r.sendError(slot, err.Error())
			return
		}
		qs, err := game.ScheduleQuestionPool(resolved, mathrand.New(mathrand.NewSource(time.Now().UnixNano())))
		if err != nil {
			r.sendError(slot, err.Error())
			return
		}
		if err := r.state.StartGame(slot.playerID, game.Mode(p.Mode), deckIDs, p.Rounds, qs, infos); err != nil {
			r.sendError(slot, err.Error())
			return
		}
		r.activeDecks = append([]game.DeckInfo(nil), infos...)
		r.savedGame = false
		r.scheduleDeadline()
		r.broadcastState()
	case TypeUpdateSettings:
		if !r.requireIdentified(slot) {
			return
		}
		p, err := DecodePayload[UpdateGameSettingsPayload](env)
		if err != nil {
			r.sendError(slot, "invalid game settings")
			return
		}
		deckIDs := p.CanonicalDeckIDs()
		_, infos, err := r.resolveDecks(deckIDs, slot.playerID)
		if err != nil {
			r.sendError(slot, err.Error())
			return
		}
		effectiveRounds := p.Rounds
		available := 0
		for _, info := range infos {
			available += info.QuestionCount
		}
		if available > 0 && effectiveRounds > available {
			effectiveRounds = available
		}
		if err := r.state.UpdateSettings(slot.playerID, game.Mode(p.Mode), deckIDs, effectiveRounds, infos); err != nil {
			r.sendError(slot, err.Error())
			return
		}
		r.activeDecks = append([]game.DeckInfo(nil), infos...)
		r.broadcastState()
	case TypeSubmitRanking, TypeSubmitPrediction:
		if !r.requireIdentified(slot) {
			return
		}
		p, err := DecodePayload[RankingPayload](env)
		if err != nil {
			r.sendError(slot, "invalid ranking")
			return
		}
		if err := r.state.SubmitEntryForQuestion(slot.playerID, p.RoundIndex, p.QuestionID, p.Ranking); err != nil {
			r.sendError(slot, err.Error())
			return
		}
		r.scheduleDeadline()
		r.broadcastState()
	case TypeSkipQuestion:
		if !r.requireIdentified(slot) {
			return
		}
		p, err := DecodePayload[SkipQuestionPayload](env)
		if err != nil {
			r.sendError(slot, "invalid skip_question")
			return
		}
		if err := r.state.SkipQuestion(slot.playerID, p.RoundIndex, p.QuestionID); err != nil {
			r.sendError(slot, err.Error())
			return
		}
		// A skip preserves the exact round deadline, so the existing timer stays valid.
		r.broadcastState()
	case TypeReady:
		if !r.requireIdentified(slot) {
			return
		}
		if err := r.state.MarkReady(slot.playerID); err != nil {
			r.sendError(slot, err.Error())
			return
		}
		r.scheduleDeadline()
		r.broadcastState()
	case TypeNextRound:
		if !r.requireIdentified(slot) {
			return
		}
		if err := r.state.NextRound(slot.playerID); err != nil {
			r.sendError(slot, err.Error())
			return
		}
		r.scheduleDeadline()
		r.broadcastState()
	default:
		r.sendError(slot, "unknown message type")
	}
}

func (r *Room) doAttachAccount(slot *connSlot, token string) {
	if r.auth == nil {
		r.sendError(slot, "auth unavailable")
		return
	}
	userID, err := r.auth.ParseJWT(token)
	if err != nil {
		r.sendError(slot, "invalid account token")
		return
	}
	player := r.state.PlayerByID(slot.playerID)
	if player == nil {
		r.sendError(slot, "player not found")
		return
	}
	if player.UserID != "" && player.UserID != userID.String() {
		r.sendError(slot, "player already belongs to another account")
		return
	}
	player.UserID = userID.String()
}

func (r *Room) requireIdentified(slot *connSlot) bool {
	if len(slot.playerID) >= 8 && slot.playerID[:8] == "pending-" {
		r.sendError(slot, "join the room first")
		return false
	}
	return true
}

func (r *Room) doJoin(slot *connSlot, nickname, authToken string) {
	if r.state.Phase != game.PhaseLobby {
		r.sendError(slot, "game already started")
		return
	}
	if len(nickname) > 24 {
		nickname = nickname[:24]
	}
	userID := ""
	if authToken != "" && r.auth != nil {
		if id, err := r.auth.ParseJWT(authToken); err == nil {
			userID = id.String()
		}
		// Invalid token → anonymous; never an error.
	}
	id := randomID(8)
	token := randomID(16)
	isHost := r.state.Host() == nil || !r.state.Host().Connected
	if isHost {
		for _, existing := range r.state.Players {
			existing.IsHost = false
		}
	}
	p := &game.Player{
		ID: id, Nickname: nickname, IsHost: isHost,
		Connected: true, ReconnectToken: token, UserID: userID,
	}
	r.state.Players = append(r.state.Players, p)

	delete(r.conns, slot.playerID)
	slot.playerID = id
	r.conns[id] = slot

	welcome, _ := Encode(TypeWelcome, WelcomePayload{
		PlayerID: id, ReconnectToken: token, IsHost: isHost,
	})
	r.sendRaw(slot, welcome)
	r.broadcastState()
}

func (r *Room) doReconnect(slot *connSlot, playerID, token string) {
	p := r.state.PlayerByID(playerID)
	if p == nil || p.ReconnectToken != token {
		r.sendError(slot, "invalid reconnect token")
		return
	}
	if old, ok := r.conns[playerID]; ok && old != slot {
		old.cancel()
		delete(r.conns, playerID)
	}
	delete(r.conns, slot.playerID)
	slot.playerID = playerID
	r.conns[playerID] = slot
	p.Connected = true

	welcome, _ := Encode(TypeWelcome, WelcomePayload{
		PlayerID: playerID, ReconnectToken: token, IsHost: p.IsHost,
	})
	r.sendRaw(slot, welcome)
	if r.state.ResumeFromPause(time.Now()) {
		r.clearPauseTimer()
		r.scheduleDeadline()
	}
	r.broadcastState()
}

func (r *Room) doLeave(slot *connSlot) {
	playerID := slot.playerID
	delete(r.conns, playerID)
	if !r.state.RemovePlayer(playerID, time.Now()) {
		return
	}
	// Removing the slot before cancellation makes the read-pump's deferred
	// disconnect event a harmless no-op rather than a second state transition.
	slot.cancel()
	if r.state.IsActiveGame() {
		r.clearPauseTimer()
		r.scheduleDeadline()
	} else {
		r.clearTimer()
		r.clearPauseTimer()
	}
	r.broadcastState()
}

func (r *Room) handleDisconnect(playerID string, disconnected *connSlot) {
	slot, ok := r.conns[playerID]
	if !ok || (disconnected != nil && slot != disconnected) {
		return
	}
	delete(r.conns, playerID)
	slot.cancel()
	if len(playerID) >= 8 && playerID[:8] == "pending-" {
		return
	}
	if p := r.state.PlayerByID(playerID); p != nil {
		p.Connected = false
		if r.state.IsActiveGame() {
			if r.state.PauseForReconnect(time.Now(), game.ReconnectGrace) {
				r.clearTimer()
				r.schedulePauseDeadline()
			} else if r.state.ConnectedCount() >= 2 && p.IsHost {
				r.state.ElectConnectedHost()
			}
		} else if p.IsHost {
			r.state.ElectConnectedHost()
		}
		r.broadcastState()
	}
}

func (r *Room) broadcastState() {
	r.ensureSelectedMetadata()
	for pid, slot := range r.conns {
		if len(pid) >= 8 && pid[:8] == "pending-" {
			continue
		}
		view := snapshotFor(r.state, pid, r.lobbyDecks(pid))
		msg, err := Encode(TypeRoomState, view)
		if err != nil {
			log.Printf("encode state: %v", err)
			continue
		}
		r.sendRaw(slot, msg)
	}
}

func (r *Room) resolveDecks(deckIDs []string, hostPlayerID string) ([]game.Deck, []game.DeckInfo, error) {
	if len(deckIDs) < 1 || len(deckIDs) > 5 {
		return nil, nil, game.ErrInvalidDecks
	}
	seen := make(map[string]struct{}, len(deckIDs))
	resolved := make([]game.Deck, 0, len(deckIDs))
	infos := make([]game.DeckInfo, 0, len(deckIDs))
	host := r.state.PlayerByID(hostPlayerID)
	for _, deckID := range deckIDs {
		if deckID == "" {
			return nil, nil, game.ErrInvalidDecks
		}
		if _, exists := seen[deckID]; exists {
			return nil, nil, game.ErrInvalidDecks
		}
		seen[deckID] = struct{}{}
		if r.store != nil && r.store.Enabled() {
			d, err := r.store.GetDeck(context.Background(), deckID)
			if err == nil {
				if !canHostUseDeck(d, host) {
					return nil, nil, fmt.Errorf("deck %q is no longer available", deckID)
				}
				if len(d.Questions) == 0 {
					return nil, nil, fmt.Errorf("deck %q has no questions", d.Title)
				}
				resolved = append(resolved, game.Deck{ID: d.ID, Name: d.Title, Emoji: d.Emoji, Questions: d.Questions})
				infos = append(infos, game.DeckInfo{ID: d.ID, Name: d.Title, Emoji: d.Emoji, QuestionCount: len(d.Questions)})
				continue
			}
			if !errors.Is(err, store.ErrNotFound) {
				return nil, nil, fmt.Errorf("could not load selected decks")
			}
			return nil, nil, fmt.Errorf("deck %q is no longer available", deckID)
		}
		d, err := decks.Get(deckID)
		if err != nil {
			return nil, nil, fmt.Errorf("deck %q is no longer available", deckID)
		}
		resolved = append(resolved, *d)
		infos = append(infos, game.DeckInfo{ID: d.ID, Name: d.Name, Emoji: d.Emoji, QuestionCount: len(d.Questions)})
	}
	return resolved, infos, nil
}

func canHostUseDeck(deck *store.Deck, host *game.Player) bool {
	if deck == nil || deck.Visibility == store.VisibilityRemoved {
		return false
	}
	if deck.IsBuiltin || deck.Visibility == store.VisibilityPublic {
		return true
	}
	return deck.Visibility == store.VisibilityPrivate && host != nil && deck.OwnerID != nil &&
		host.UserID != "" && host.UserID == deck.OwnerID.String()
}

func (r *Room) ensureSelectedMetadata() {
	if len(r.state.SelectedDecks) > 0 || len(r.state.DeckIDs) == 0 {
		return
	}
	host := r.state.Host()
	hostID := ""
	if host != nil {
		hostID = host.ID
	}
	_, infos, err := r.resolveDecks(r.state.DeckIDs, hostID)
	if err == nil {
		r.state.SelectedDecks = infos
		r.activeDecks = append([]game.DeckInfo(nil), infos...)
	}
}

func (r *Room) lobbyDecks(viewerID string) []game.DeckInfo {
	viewer := r.state.PlayerByID(viewerID)
	if viewer == nil || !viewer.IsHost {
		return append([]game.DeckInfo(nil), r.state.SelectedDecks...)
	}
	if r.store != nil && r.store.Enabled() {
		builtins, err := r.store.ListBuiltinDecks(context.Background())
		if err != nil {
			log.Printf("list builtin decks: %v", err)
		} else {
			out := make([]game.DeckInfo, 0, len(builtins)+4)
			seen := map[string]bool{}
			for _, d := range builtins {
				out = append(out, d.ToDeckInfo())
				seen[d.ID] = true
			}
			for _, p := range r.state.Players {
				if !p.IsHost || p.UserID == "" {
					continue
				}
				uid, err := uuid.Parse(p.UserID)
				if err != nil {
					break
				}
				mine, err := r.store.ListDecksByOwner(context.Background(), uid)
				if err != nil {
					log.Printf("list host decks: %v", err)
					break
				}
				for _, d := range mine {
					if d.Visibility == store.VisibilityRemoved {
						continue // not offered in lobby picker; owner can still start via known id
					}
					out = append(out, d.ToDeckInfo())
					seen[d.ID] = true
				}
				break
			}
			for _, active := range r.activeDecks {
				if !seen[active.ID] {
					out = append(out, active)
					seen[active.ID] = true
				}
			}
			if len(out) > 0 {
				return out
			}
		}
	}
	infos, err := decks.All()
	if err != nil {
		return nil
	}
	return infos
}

func (r *Room) sendError(slot *connSlot, message string) {
	msg, _ := Encode(TypeError, ErrorPayload{Message: message})
	r.sendRaw(slot, msg)
}

func (r *Room) sendRaw(slot *connSlot, msg []byte) {
	select {
	case slot.send <- msg:
	default:
		log.Printf("send buffer full for %s", slot.playerID)
	}
}

func randomID(nBytes int) string {
	b := make([]byte, nBytes)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
