package hub

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"sync"
	"time"
)

const codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1

// RoomHandle is what the hub stores; the ws package owns the live Room.
type RoomHandle struct {
	Code       string
	CreatedAt  time.Time
	LastActive time.Time
	Runner     RoomRunner
}

// RoomRunner is implemented by the live room actor.
type RoomRunner interface {
	Code() string
	Stop()
	Touch()
	IdleSince() time.Time
}

// Hub tracks active rooms by code.
type Hub struct {
	mu    sync.RWMutex
	rooms map[string]*RoomHandle
}

func New() *Hub {
	return &Hub{rooms: make(map[string]*RoomHandle)}
}

func (h *Hub) Register(r RoomRunner) {
	h.mu.Lock()
	defer h.mu.Unlock()
	now := time.Now()
	h.rooms[r.Code()] = &RoomHandle{
		Code:       r.Code(),
		CreatedAt:  now,
		LastActive: now,
		Runner:     r,
	}
}

func (h *Hub) Get(code string) (RoomRunner, bool) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	rh, ok := h.rooms[code]
	if !ok {
		return nil, false
	}
	return rh.Runner, true
}

func (h *Hub) Remove(code string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if rh, ok := h.rooms[code]; ok {
		rh.Runner.Stop()
		delete(h.rooms, code)
	}
}

// CreateCode generates a unique 4-character room code.
func (h *Hub) CreateCode() (string, error) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for i := 0; i < 50; i++ {
		code, err := randomCode(4)
		if err != nil {
			return "", err
		}
		if _, exists := h.rooms[code]; !exists {
			return code, nil
		}
	}
	return "", fmt.Errorf("could not allocate room code")
}

func randomCode(n int) (string, error) {
	out := make([]byte, n)
	max := big.NewInt(int64(len(codeAlphabet)))
	for i := 0; i < n; i++ {
		v, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", err
		}
		out[i] = codeAlphabet[v.Int64()]
	}
	return string(out), nil
}

// ReapIdle removes rooms idle longer than maxIdle.
func (h *Hub) ReapIdle(maxIdle time.Duration) {
	h.mu.Lock()
	defer h.mu.Unlock()
	now := time.Now()
	for code, rh := range h.rooms {
		if now.Sub(rh.Runner.IdleSince()) > maxIdle {
			rh.Runner.Stop()
			delete(h.rooms, code)
		}
	}
}

// StartReaper periodically cleans idle rooms.
func (h *Hub) StartReaper(interval, maxIdle time.Duration) {
	go func() {
		t := time.NewTicker(interval)
		defer t.Stop()
		for range t.C {
			h.ReapIdle(maxIdle)
		}
	}()
}
