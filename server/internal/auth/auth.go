package auth

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"google.golang.org/api/idtoken"
)

const tokenTTL = 30 * 24 * time.Hour

// Config holds auth settings from the environment.
type Config struct {
	GoogleWebClientID string
	JWTSecret         []byte
	DevFakeAuth       bool
}

// LoadConfig reads auth settings from env.
func LoadConfig() Config {
	secret := os.Getenv("JWT_SECRET")
	if secret == "" {
		secret = "dev-insecure-jwt-secret-change-me"
	}
	return Config{
		GoogleWebClientID: os.Getenv("GOOGLE_WEB_CLIENT_ID"),
		JWTSecret:         []byte(secret),
		DevFakeAuth:       os.Getenv("DEV_FAKE_AUTH") == "1",
	}
}

// Claims is our JWT payload.
type Claims struct {
	jwt.RegisteredClaims
}

// Service verifies Google tokens and mints/validates our JWTs.
type Service struct {
	cfg Config
}

func NewService(cfg Config) *Service {
	return &Service{cfg: cfg}
}

func (s *Service) DevFakeEnabled() bool {
	return s.cfg.DevFakeAuth
}

func (s *Service) GoogleClientID() string {
	return s.cfg.GoogleWebClientID
}

// VerifyGoogleIDToken validates a Google ID token and returns sub + name.
func (s *Service) VerifyGoogleIDToken(ctx context.Context, idToken string) (sub, name string, err error) {
	if s.cfg.GoogleWebClientID == "" {
		return "", "", fmt.Errorf("GOOGLE_WEB_CLIENT_ID not configured")
	}
	payload, err := idtoken.Validate(ctx, idToken, s.cfg.GoogleWebClientID)
	if err != nil {
		return "", "", fmt.Errorf("invalid google token: %w", err)
	}
	sub = payload.Subject
	if n, ok := payload.Claims["name"].(string); ok && n != "" {
		name = n
	} else if e, ok := payload.Claims["email"].(string); ok && e != "" {
		name = e
	} else {
		name = "Player"
	}
	return sub, name, nil
}

// MintJWT issues a 30-day HS256 JWT whose subject is the user id.
func (s *Service) MintJWT(userID uuid.UUID) (string, error) {
	now := time.Now()
	claims := Claims{
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   userID.String(),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(tokenTTL)),
			Issuer:    "rank5",
		},
	}
	t := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return t.SignedString(s.cfg.JWTSecret)
}

// ParseJWT validates our JWT and returns the user id.
func (s *Service) ParseJWT(tokenStr string) (uuid.UUID, error) {
	t, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (any, error) {
		if t.Method != jwt.SigningMethodHS256 {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return s.cfg.JWTSecret, nil
	})
	if err != nil {
		return uuid.Nil, err
	}
	claims, ok := t.Claims.(*Claims)
	if !ok || !t.Valid {
		return uuid.Nil, errors.New("invalid token")
	}
	id, err := uuid.Parse(claims.Subject)
	if err != nil {
		return uuid.Nil, fmt.Errorf("bad subject: %w", err)
	}
	return id, nil
}

type ctxKey int

const userIDKey ctxKey = 1

// Middleware requires a valid Bearer JWT and puts the user id in context.
func (s *Service) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		raw := r.Header.Get("Authorization")
		if !strings.HasPrefix(raw, "Bearer ") {
			http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
			return
		}
		id, err := s.ParseJWT(strings.TrimPrefix(raw, "Bearer "))
		if err != nil {
			http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
			return
		}
		ctx := context.WithValue(r.Context(), userIDKey, id)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// UserIDFromContext returns the authenticated user id, or uuid.Nil.
func UserIDFromContext(ctx context.Context) uuid.UUID {
	id, _ := ctx.Value(userIDKey).(uuid.UUID)
	return id
}
