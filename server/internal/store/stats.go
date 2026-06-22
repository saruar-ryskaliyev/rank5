package store

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"math"
	"sort"

	"github.com/google/uuid"
	"github.com/saruar/rank5/server/internal/game"
)

var ErrClaimNotFound = errors.New("claimable result not found")

type UserStats struct {
	GamesPlayed  int              `json:"gamesPlayed"`
	Versus       VersusStats      `json:"versus"`
	Coop         CoopStats        `json:"coop"`
	Guessing     *GuessingStats   `json:"guessing"`
	Superlatives SuperlativeStats `json:"superlatives"`
}

type VersusStats struct {
	Played       int `json:"played"`
	Wins         int `json:"wins"`
	TiesForFirst int `json:"tiesForFirst"`
	WinRatePct   int `json:"winRatePct"`
}

type CoopStats struct {
	Played       int            `json:"played"`
	BestByRounds []CoopBestStat `json:"bestByRounds"`
}

type CoopBestStat struct {
	Rounds   int `json:"rounds"`
	Score    int `json:"score"`
	MaxScore int `json:"maxScore"`
}

type GuessingStats struct {
	AccuracyPct        int `json:"accuracyPct"`
	PredictionsTracked int `json:"predictionsTracked"`
}

type SuperlativeStats struct {
	BestGuesser          int `json:"bestGuesser"`
	MostPredictable      int `json:"mostPredictable"`
	DetailedGamesTracked int `json:"detailedGamesTracked"`
}

type statsPlayer struct {
	PlayerID string
	Nickname string
	Score    int
	IsSelf   bool
}

type statsGame struct {
	Mode         string
	TotalRounds  int
	Rounds       json.RawMessage
	SelfPlayerID string
	SelfScore    int
	Players      []statsPlayer
}

type storedRound struct {
	Index     int            `json:"index"`
	SubjectID string         `json:"subjectId"`
	TeamScore int            `json:"teamScore"`
	Scores    map[string]int `json:"scores"`
}

func HashClaimToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func (s *Store) GetUserStats(ctx context.Context, userID uuid.UUID) (UserStats, error) {
	if !s.Enabled() {
		return UserStats{}, errors.New("store disabled")
	}
	rows, err := s.pool.Query(ctx, `
		SELECT g.id::text, g.mode, g.total_rounds, g.rounds,
		       self.player_id, self.score,
		       participant.player_id, participant.nickname, participant.score,
		       participant.nickname = self.nickname AS is_self
		FROM game_results g
		JOIN LATERAL (
			SELECT player_id, nickname, score
			FROM game_result_players
			WHERE game_id = g.id AND user_id = $1
			ORDER BY nickname
			LIMIT 1
		) self ON true
		JOIN game_result_players participant ON participant.game_id = g.id
		ORDER BY g.finished_at, g.id, participant.nickname
	`, userID)
	if err != nil {
		return UserStats{}, err
	}
	defer rows.Close()

	byID := make(map[string]*statsGame)
	order := make([]string, 0)
	for rows.Next() {
		var gameID, mode, nickname string
		var totalRounds, selfScore, playerScore int
		var rounds []byte
		var selfPlayerID, playerID *string
		var isSelf bool
		if err := rows.Scan(
			&gameID, &mode, &totalRounds, &rounds,
			&selfPlayerID, &selfScore,
			&playerID, &nickname, &playerScore, &isSelf,
		); err != nil {
			return UserStats{}, err
		}
		entry := byID[gameID]
		if entry == nil {
			entry = &statsGame{
				Mode: mode, TotalRounds: totalRounds,
				Rounds: append([]byte(nil), rounds...), SelfScore: selfScore,
			}
			if selfPlayerID != nil {
				entry.SelfPlayerID = *selfPlayerID
			}
			byID[gameID] = entry
			order = append(order, gameID)
		}
		participant := statsPlayer{Nickname: nickname, Score: playerScore, IsSelf: isSelf}
		if playerID != nil {
			participant.PlayerID = *playerID
		}
		entry.Players = append(entry.Players, participant)
	}
	if err := rows.Err(); err != nil {
		return UserStats{}, err
	}

	games := make([]statsGame, 0, len(order))
	for _, id := range order {
		games = append(games, *byID[id])
	}
	return aggregateUserStats(games), nil
}

func (s *Store) ClaimGameResults(
	ctx context.Context,
	userID uuid.UUID,
	roomCode, playerID, reconnectToken string,
) (int64, error) {
	if !s.Enabled() {
		return 0, errors.New("store disabled")
	}
	if roomCode == "" || playerID == "" || reconnectToken == "" {
		return 0, ErrClaimNotFound
	}
	tag, err := s.pool.Exec(ctx, `
		UPDATE game_result_players participant
		SET user_id = $1
		FROM game_results result
		WHERE participant.game_id = result.id
		  AND result.room_code = $2
		  AND participant.player_id = $3
		  AND participant.claim_token_hash = $4
		  AND (participant.user_id IS NULL OR participant.user_id = $1)
	`, userID, roomCode, playerID, HashClaimToken(reconnectToken))
	if err != nil {
		return 0, err
	}
	if tag.RowsAffected() == 0 {
		return 0, ErrClaimNotFound
	}
	return tag.RowsAffected(), nil
}

func aggregateUserStats(games []statsGame) UserStats {
	stats := UserStats{
		GamesPlayed: len(games),
		Coop: CoopStats{
			BestByRounds: make([]CoopBestStat, 0),
		},
	}
	bestCoop := make(map[int]int)
	totalDisplacement := 0
	predictionsTracked := 0

	for _, result := range games {
		var rounds []storedRound
		_ = json.Unmarshal(result.Rounds, &rounds)

		switch result.Mode {
		case "versus":
			stats.Versus.Played++
			hasHigher := false
			hasEqual := false
			for _, participant := range result.Players {
				if participant.IsSelf {
					continue
				}
				if participant.Score > result.SelfScore {
					hasHigher = true
				}
				if participant.Score == result.SelfScore {
					hasEqual = true
				}
			}
			if !hasHigher && hasEqual {
				stats.Versus.TiesForFirst++
			} else if !hasHigher {
				stats.Versus.Wins++
			}
		case "coop":
			stats.Coop.Played++
			total := 0
			for _, round := range rounds {
				total += round.TeamScore
			}
			if current, ok := bestCoop[result.TotalRounds]; !ok || total > current {
				bestCoop[result.TotalRounds] = total
			}
		}

		if result.SelfPlayerID == "" || !hasDetailedRounds(rounds) {
			continue
		}
		stats.Superlatives.DetailedGamesTracked++

		scoreTotals := make(map[string]int)
		predictionCounts := make(map[string]int)
		incomingTotals := make(map[string]int)
		incomingCounts := make(map[string]int)
		for _, round := range rounds {
			incoming := 0
			incomingCount := 0
			for id, score := range round.Scores {
				scoreTotals[id] += score
				predictionCounts[id]++
				incoming += score
				incomingCount++
				if id == result.SelfPlayerID {
					displacement := (game.MaxScore - score) / game.PenaltyPerPosition
					if displacement < 0 {
						displacement = 0
					}
					if displacement > game.MaxDisplacement {
						displacement = game.MaxDisplacement
					}
					totalDisplacement += displacement
					predictionsTracked++
				}
			}
			if round.SubjectID != "" && incomingCount > 0 {
				incomingTotals[round.SubjectID] += incoming
				incomingCounts[round.SubjectID] += incomingCount
			}
		}

		if predictionCounts[result.SelfPlayerID] > 0 && countPositive(predictionCounts) >= 2 &&
			isUniqueIntLeader(result.SelfPlayerID, scoreTotals) {
			stats.Superlatives.BestGuesser++
		}
		if incomingCounts[result.SelfPlayerID] > 0 && len(incomingCounts) >= 2 &&
			isUniqueAverageLeader(result.SelfPlayerID, incomingTotals, incomingCounts) {
			stats.Superlatives.MostPredictable++
		}
	}

	if stats.Versus.Played > 0 {
		stats.Versus.WinRatePct = int(math.Round(float64(stats.Versus.Wins) * 100 / float64(stats.Versus.Played)))
	}
	for rounds, score := range bestCoop {
		stats.Coop.BestByRounds = append(stats.Coop.BestByRounds, CoopBestStat{
			Rounds: rounds, Score: score, MaxScore: rounds * game.MaxScore,
		})
	}
	sort.Slice(stats.Coop.BestByRounds, func(i, j int) bool {
		return stats.Coop.BestByRounds[i].Rounds < stats.Coop.BestByRounds[j].Rounds
	})
	if predictionsTracked > 0 {
		accuracy := 100 * (1 - float64(totalDisplacement)/float64(predictionsTracked*game.MaxDisplacement))
		stats.Guessing = &GuessingStats{
			AccuracyPct: int(math.Round(accuracy)), PredictionsTracked: predictionsTracked,
		}
	}
	return stats
}

func hasDetailedRounds(rounds []storedRound) bool {
	for _, round := range rounds {
		if round.SubjectID != "" {
			return true
		}
	}
	return false
}

func countPositive(values map[string]int) int {
	count := 0
	for _, value := range values {
		if value > 0 {
			count++
		}
	}
	return count
}

func isUniqueIntLeader(self string, values map[string]int) bool {
	selfValue, ok := values[self]
	if !ok {
		return false
	}
	for id, value := range values {
		if id != self && value >= selfValue {
			return false
		}
	}
	return true
}

func isUniqueAverageLeader(self string, totals, counts map[string]int) bool {
	selfCount := counts[self]
	if selfCount == 0 {
		return false
	}
	selfAverage := float64(totals[self]) / float64(selfCount)
	for id, count := range counts {
		if id == self || count == 0 {
			continue
		}
		if float64(totals[id])/float64(count) >= selfAverage {
			return false
		}
	}
	return true
}
