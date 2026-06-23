package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

var ErrGenerationLimit = errors.New("generation limit reached")

type GenerationUsage struct {
	Status                string
	Attempts              int
	InputTokens           int64
	OutputTokens          int64
	ProviderUnits         float64
	EstimatedCostMicroUSD int64
	Latency               time.Duration
}

// ReserveGeneration atomically applies UTC-day limits and records a pending
// request. The advisory lock keeps the limits correct across server replicas.
func (s *Store) ReserveGeneration(
	ctx context.Context,
	userID uuid.UUID,
	provider, model, promptVersion string,
	userDailyLimit, globalDailyLimit int,
	globalDailyProviderUnitLimit float64,
	globalDailyCostMicroUSDLimit int64,
) (uuid.UUID, error) {
	if !s.Enabled() {
		return uuid.Nil, fmt.Errorf("store disabled")
	}
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return uuid.Nil, err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(hashtext('rank5-llm-generation'))`); err != nil {
		return uuid.Nil, err
	}
	dayStart := time.Now().UTC().Truncate(24 * time.Hour)
	if globalDailyLimit > 0 {
		var count int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM llm_generations WHERE created_at >= $1`, dayStart).Scan(&count); err != nil {
			return uuid.Nil, err
		}
		if count >= globalDailyLimit {
			return uuid.Nil, ErrGenerationLimit
		}
	}
	if globalDailyProviderUnitLimit > 0 {
		var used float64
		if err := tx.QueryRow(ctx, `SELECT coalesce(sum(provider_units), 0) FROM llm_generations WHERE created_at >= $1`, dayStart).Scan(&used); err != nil {
			return uuid.Nil, err
		}
		if used >= globalDailyProviderUnitLimit {
			return uuid.Nil, ErrGenerationLimit
		}
	}
	if globalDailyCostMicroUSDLimit > 0 {
		var used int64
		if err := tx.QueryRow(ctx, `SELECT coalesce(sum(estimated_cost_microusd), 0) FROM llm_generations WHERE created_at >= $1`, dayStart).Scan(&used); err != nil {
			return uuid.Nil, err
		}
		if used >= globalDailyCostMicroUSDLimit {
			return uuid.Nil, ErrGenerationLimit
		}
	}
	if userDailyLimit > 0 {
		var count int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM llm_generations WHERE user_id = $1 AND created_at >= $2`, userID, dayStart).Scan(&count); err != nil {
			return uuid.Nil, err
		}
		if count >= userDailyLimit {
			return uuid.Nil, ErrGenerationLimit
		}
	}

	var id uuid.UUID
	err = tx.QueryRow(ctx, `
		INSERT INTO llm_generations (user_id, provider, model, prompt_version)
		VALUES ($1, $2, $3, $4)
		RETURNING id
	`, userID, provider, model, promptVersion).Scan(&id)
	if err != nil {
		return uuid.Nil, err
	}
	if err := tx.Commit(ctx); err != nil {
		return uuid.Nil, err
	}
	return id, nil
}

func (s *Store) CompleteGeneration(ctx context.Context, id uuid.UUID, usage GenerationUsage) error {
	if !s.Enabled() {
		return fmt.Errorf("store disabled")
	}
	if usage.Status != "success" && usage.Status != "invalid_output" && usage.Status != "provider_error" {
		return fmt.Errorf("invalid generation status %q", usage.Status)
	}
	_, err := s.pool.Exec(ctx, `
		UPDATE llm_generations
		SET status = $1,
		    attempts = $2,
		    input_tokens = $3,
		    output_tokens = $4,
		    provider_units = $5,
		    estimated_cost_microusd = $6,
		    latency_ms = $7,
		    completed_at = now()
		WHERE id = $8
	`, usage.Status, usage.Attempts, usage.InputTokens, usage.OutputTokens,
		usage.ProviderUnits, usage.EstimatedCostMicroUSD, usage.Latency.Milliseconds(), id)
	return err
}
