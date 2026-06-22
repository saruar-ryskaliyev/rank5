package store

import (
	"bytes"
	"encoding/json"
	"testing"
)

func TestAggregateEmptyStatsMarshalsCoopBestsAsArray(t *testing.T) {
	stats := aggregateUserStats(nil)
	raw, err := json.Marshal(stats)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(raw, []byte(`"bestByRounds":[]`)) {
		t.Fatalf("empty bestByRounds must be an array, got %s", raw)
	}
}

func TestAggregateUserStatsIncludesLegacyCoreStats(t *testing.T) {
	games := []statsGame{
		{
			Mode:        "versus",
			TotalRounds: 3,
			Rounds:      json.RawMessage(`[{"index":0,"teamScore":1800,"scores":{"old-player":1800}}]`),
			SelfScore:   5400,
			Players: []statsPlayer{
				{Nickname: "Me", Score: 5400, IsSelf: true},
				{Nickname: "Friend", Score: 5200},
			},
		},
		{
			Mode:        "coop",
			TotalRounds: 3,
			Rounds: json.RawMessage(`[
				{"index":0,"teamScore":1800,"scores":{"old-player":1800}},
				{"index":1,"teamScore":1900,"scores":{"old-player":1900}},
				{"index":2,"teamScore":2000,"scores":{"old-player":2000}}
			]`),
			Players: []statsPlayer{{Nickname: "Me", IsSelf: true}, {Nickname: "Friend"}},
		},
	}

	got := aggregateUserStats(games)
	if got.GamesPlayed != 2 {
		t.Fatalf("gamesPlayed = %d, want 2", got.GamesPlayed)
	}
	if got.Versus.Played != 1 || got.Versus.Wins != 1 || got.Versus.WinRatePct != 100 {
		t.Fatalf("unexpected versus stats: %+v", got.Versus)
	}
	if got.Coop.Played != 1 || len(got.Coop.BestByRounds) != 1 {
		t.Fatalf("unexpected coop stats: %+v", got.Coop)
	}
	best := got.Coop.BestByRounds[0]
	if best.Rounds != 3 || best.Score != 5700 || best.MaxScore != 6000 {
		t.Fatalf("unexpected coop best: %+v", best)
	}
	if got.Guessing != nil {
		t.Fatalf("legacy result must not invent detailed guessing stats: %+v", got.Guessing)
	}
	if got.Superlatives.DetailedGamesTracked != 0 {
		t.Fatalf("tracked detailed games = %d, want 0", got.Superlatives.DetailedGamesTracked)
	}
}

func TestAggregateUserStatsDetailedMetricsAndTieSemantics(t *testing.T) {
	games := []statsGame{
		{
			Mode:         "coop",
			TotalRounds:  2,
			SelfPlayerID: "p1",
			Rounds: json.RawMessage(`[
				{"index":0,"subjectId":"p1","teamScore":1800,"scores":{"p2":1800}},
				{"index":1,"subjectId":"p2","teamScore":1900,"scores":{"p1":1900}}
			]`),
			Players: []statsPlayer{
				{PlayerID: "p1", Nickname: "Me", IsSelf: true},
				{PlayerID: "p2", Nickname: "Friend"},
			},
		},
		{
			Mode:         "versus",
			TotalRounds:  1,
			SelfPlayerID: "p1",
			SelfScore:    1900,
			Rounds:       json.RawMessage(`[{"index":0,"subjectId":"p2","teamScore":1900,"scores":{"p1":1900}}]`),
			Players: []statsPlayer{
				{PlayerID: "p1", Nickname: "Me", Score: 1900, IsSelf: true},
				{PlayerID: "p2", Nickname: "Friend", Score: 1900},
			},
		},
	}

	got := aggregateUserStats(games)
	if got.Versus.Wins != 0 || got.Versus.TiesForFirst != 1 || got.Versus.WinRatePct != 0 {
		t.Fatalf("unexpected tie handling: %+v", got.Versus)
	}
	if got.Guessing == nil {
		t.Fatal("expected guessing stats")
	}
	if got.Guessing.PredictionsTracked != 2 || got.Guessing.AccuracyPct != 83 {
		t.Fatalf("unexpected guessing stats: %+v", got.Guessing)
	}
	if got.Superlatives.BestGuesser != 1 {
		t.Fatalf("best guesser = %d, want 1", got.Superlatives.BestGuesser)
	}
	if got.Superlatives.MostPredictable != 0 {
		t.Fatalf("most predictable = %d, want 0", got.Superlatives.MostPredictable)
	}
	if got.Superlatives.DetailedGamesTracked != 2 {
		t.Fatalf("tracked games = %d, want 2", got.Superlatives.DetailedGamesTracked)
	}
}

func TestHashClaimTokenIsDeterministicAndDoesNotExposeToken(t *testing.T) {
	const token = "guest-reconnect-secret"
	first := HashClaimToken(token)
	second := HashClaimToken(token)
	if first == "" || first != second {
		t.Fatalf("hashes differ or are empty: %q %q", first, second)
	}
	if first == token {
		t.Fatal("claim token was stored in plaintext")
	}
	if first == HashClaimToken("different-token") {
		t.Fatal("different claim tokens produced the same hash")
	}
}
