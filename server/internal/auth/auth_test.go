package auth

import (
	"net/http"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

func TestMintAndParseJWT(t *testing.T) {
	svc := NewService(Config{JWTSecret: []byte("test-secret-key-32bytes-long!!!!")})
	id := uuid.New()
	tok, err := svc.MintJWT(id)
	if err != nil {
		t.Fatal(err)
	}
	got, err := svc.ParseJWT(tok)
	if err != nil {
		t.Fatal(err)
	}
	if got != id {
		t.Fatalf("got %s want %s", got, id)
	}
}

func TestParseJWTRejectsBadSecret(t *testing.T) {
	a := NewService(Config{JWTSecret: []byte("secret-a-xxxxxxxxxxxxxxxxxxxxxxx")})
	b := NewService(Config{JWTSecret: []byte("secret-b-xxxxxxxxxxxxxxxxxxxxxxx")})
	id := uuid.New()
	tok, err := a.MintJWT(id)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := b.ParseJWT(tok); err == nil {
		t.Fatal("expected rejection with wrong secret")
	}
}

func TestParseJWTRejectsGarbage(t *testing.T) {
	svc := NewService(Config{JWTSecret: []byte("test-secret-key-32bytes-long!!!!")})
	if _, err := svc.ParseJWT("not.a.jwt"); err == nil {
		t.Fatal("expected error")
	}
}

func TestMiddlewareUnauthorized(t *testing.T) {
	svc := NewService(Config{JWTSecret: []byte("test-secret-key-32bytes-long!!!!")})
	h := svc.Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	req, _ := http.NewRequest(http.MethodGet, "/me", nil)
	rr := &captureWriter{code: 200, header: make(http.Header)}
	h.ServeHTTP(rr, req)
	if rr.code != http.StatusUnauthorized {
		t.Fatalf("status %d", rr.code)
	}
}

func TestMiddlewareAcceptsValid(t *testing.T) {
	svc := NewService(Config{JWTSecret: []byte("test-secret-key-32bytes-long!!!!")})
	id := uuid.New()
	tok, _ := svc.MintJWT(id)
	var got uuid.UUID
	h := svc.Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = UserIDFromContext(r.Context())
		w.WriteHeader(http.StatusOK)
	}))
	req, _ := http.NewRequest(http.MethodGet, "/me", nil)
	req.Header.Set("Authorization", "Bearer "+tok)
	rr := &captureWriter{code: 200, header: make(http.Header)}
	h.ServeHTTP(rr, req)
	if rr.code != http.StatusOK {
		t.Fatalf("status %d", rr.code)
	}
	if got != id {
		t.Fatalf("got %s want %s", got, id)
	}
}

func TestDevFakeFlag(t *testing.T) {
	off := NewService(Config{})
	if off.DevFakeEnabled() {
		t.Fatal("expected disabled")
	}
	on := NewService(Config{DevFakeAuth: true})
	if !on.DevFakeEnabled() {
		t.Fatal("expected enabled")
	}
}

func TestExpiredTokenRejected(t *testing.T) {
	secret := []byte("test-secret-key-32bytes-long!!!!")
	svc := NewService(Config{JWTSecret: secret})
	id := uuid.New()
	now := time.Now().Add(-48 * time.Hour)
	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   id.String(),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(time.Hour)),
			Issuer:    "rank5",
		},
	}
	tok, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(secret)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ParseJWT(tok); err == nil {
		t.Fatal("expected expired rejection")
	}
}

type captureWriter struct {
	code   int
	header http.Header
	body   []byte
}

func (c *captureWriter) Header() http.Header { return c.header }
func (c *captureWriter) Write(b []byte) (int, error) {
	c.body = append(c.body, b...)
	return len(b), nil
}
func (c *captureWriter) WriteHeader(statusCode int) { c.code = statusCode }
