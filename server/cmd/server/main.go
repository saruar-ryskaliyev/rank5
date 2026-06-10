package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/saruar/rank5/server/internal/auth"
	"github.com/saruar/rank5/server/internal/decks"
	"github.com/saruar/rank5/server/internal/generation"
	"github.com/saruar/rank5/server/internal/hub"
	"github.com/saruar/rank5/server/internal/llm"
	"github.com/saruar/rank5/server/internal/store"
	"github.com/saruar/rank5/server/internal/ws"
)

func main() {
	addr := ":8080"
	if v := os.Getenv("PORT"); v != "" {
		addr = ":" + v
	}

	ctx := context.Background()
	st, err := store.Open(ctx, os.Getenv("DATABASE_URL"))
	if err != nil {
		log.Fatalf("store: %v", err)
	}
	defer st.Close()
	if st != nil && st.Enabled() {
		if err := decks.SeedInto(ctx, st); err != nil {
			log.Fatalf("seed decks: %v", err)
		}
		log.Printf("store: built-in decks seeded")
		if n, err := st.PromoteAdminsByGoogleSubs(ctx, parseAdminSubs(os.Getenv("ADMIN_GOOGLE_SUBS"))); err != nil {
			log.Printf("store: admin bootstrap: %v", err)
		} else if n > 0 {
			log.Printf("store: promoted %d admin(s) from ADMIN_GOOGLE_SUBS", n)
		}
	}

	authCfg := auth.LoadConfig()
	authSvc := auth.NewService(authCfg)
	if authCfg.DevFakeAuth {
		log.Printf("auth: DEV_FAKE_AUTH enabled")
	}
	if authCfg.GoogleWebClientID == "" {
		log.Printf("auth: GOOGLE_WEB_CLIENT_ID unset — Google sign-in will fail until configured")
	}

	h := hub.New()
	h.StartReaper(1*time.Minute, 30*time.Minute)

	llmConfig := llm.LoadConfig()
	var generator generation.Generator
	if llmConfig.Enabled {
		client, err := llm.NewClient(llmConfig)
		if err != nil {
			log.Fatalf("llm: %v", err)
		}
		generator = generation.NewService(client, llmConfig.MaxOutputTokens)
		log.Printf("llm: deck generation enabled provider=%s model=%s", llmConfig.Driver, llmConfig.Model)
	} else {
		log.Printf("llm: deck generation disabled or not configured")
	}

	srv := &ws.Server{
		Hub:       h,
		Auth:      authSvc,
		Store:     st,
		Generator: generator,
		GenerationLimits: ws.GenerationLimits{
			UserDaily:                llmConfig.UserDailyLimit,
			GlobalDaily:              llmConfig.GlobalDailyLimit,
			GlobalDailyProviderUnits: llmConfig.GlobalDailyProviderUnitLimit,
			GlobalDailyCostMicroUSD:  llmConfig.GlobalDailyCostMicroUSDLimit,
		},
	}
	log.Printf("rank5 server listening on %s", addr)
	if err := http.ListenAndServe(addr, srv.Routes()); err != nil {
		log.Fatal(err)
	}
}

func parseAdminSubs(raw string) []string {
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}
