package ws

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/saruar/rank5/server/internal/auth"
	"github.com/saruar/rank5/server/internal/generation"
	"github.com/saruar/rank5/server/internal/store"
)

type GenerationLimits struct {
	UserDaily                int
	GlobalDaily              int
	GlobalDailyProviderUnits float64
	GlobalDailyCostMicroUSD  int64
}

type deckGenerationPayload struct {
	GenerationID string           `json:"generationId"`
	Draft        generation.Draft `json:"draft"`
}

func (s *Server) handleGenerateDeck(w http.ResponseWriter, r *http.Request) {
	if s.Generator == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{
			"error": "deck generation is currently unavailable",
			"code":  "generation_unavailable",
		})
		return
	}
	if !s.storeReady(w) {
		return
	}

	var request generation.Request
	decoder := json.NewDecoder(io.LimitReader(r.Body, 16<<10))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&request); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{
			"error": "invalid generation request",
			"code":  "invalid_request",
		})
		return
	}
	if request.QuestionCount == 0 {
		request.QuestionCount = 6
	}
	if err := generation.ValidateRequest(request); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{
			"error": err.Error(),
			"code":  "invalid_request",
		})
		return
	}

	provider := s.Generator.ProviderInfo()
	generationID, err := s.Store.ReserveGeneration(
		r.Context(), auth.UserIDFromContext(r.Context()),
		provider.Driver, provider.Model, generation.PromptVersion,
		s.GenerationLimits.UserDaily, s.GenerationLimits.GlobalDaily,
		s.GenerationLimits.GlobalDailyProviderUnits,
		s.GenerationLimits.GlobalDailyCostMicroUSD,
	)
	if errors.Is(err, store.ErrGenerationLimit) {
		writeJSON(w, http.StatusTooManyRequests, map[string]string{
			"error": "daily deck-generation limit reached",
			"code":  "generation_limit",
		})
		return
	}
	if err != nil {
		log.Printf("reserve deck generation: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{
			"error": "could not start deck generation",
			"code":  "generation_start_failed",
		})
		return
	}

	started := time.Now()
	result, generateErr := s.Generator.Generate(r.Context(), request)
	status := "success"
	if generateErr != nil {
		status = "provider_error"
		if errors.Is(generateErr, generation.ErrInvalidOutput) {
			status = "invalid_output"
		}
	}
	recordContext, cancelRecord := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancelRecord()
	if err := s.Store.CompleteGeneration(recordContext, generationID, store.GenerationUsage{
		Status:                status,
		Attempts:              result.Attempts,
		InputTokens:           result.Usage.InputTokens,
		OutputTokens:          result.Usage.OutputTokens,
		ProviderUnits:         result.Usage.ProviderUnits,
		EstimatedCostMicroUSD: result.Usage.EstimatedCostMicroUSD,
		Latency:               time.Since(started),
	}); err != nil {
		log.Printf("complete deck generation %s: %v", generationID, err)
	}

	if generateErr != nil {
		switch {
		case errors.Is(generateErr, generation.ErrInvalidRequest):
			writeJSON(w, http.StatusBadRequest, map[string]string{
				"error": generateErr.Error(),
				"code":  "invalid_request",
			})
		case errors.Is(generateErr, generation.ErrTruncatedOutput):
			writeJSON(w, http.StatusBadGateway, map[string]string{
				"error": "the AI response was incomplete; please try again",
				"code":  "generation_incomplete",
			})
		case errors.Is(generateErr, generation.ErrInvalidOutput):
			writeJSON(w, http.StatusBadGateway, map[string]string{
				"error": "the generated draft was not valid; please try again",
				"code":  "invalid_generation",
			})
		case errors.Is(generateErr, context.DeadlineExceeded):
			writeJSON(w, http.StatusGatewayTimeout, map[string]string{
				"error": "deck generation timed out; please try again",
				"code":  "generation_timeout",
			})
		default:
			log.Printf("deck generation %s provider=%s model=%s: %v", generationID, provider.Driver, provider.Model, generateErr)
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{
				"error": "the generation service is temporarily unavailable",
				"code":  "provider_unavailable",
			})
		}
		return
	}

	log.Printf(
		"deck generation %s provider=%s model=%s attempts=%d input_tokens=%d output_tokens=%d units=%.2f latency_ms=%d",
		generationID, provider.Driver, provider.Model, result.Attempts,
		result.Usage.InputTokens, result.Usage.OutputTokens, result.Usage.ProviderUnits,
		time.Since(started).Milliseconds(),
	)
	writeJSON(w, http.StatusOK, deckGenerationPayload{
		GenerationID: generationID.String(),
		Draft:        result.Draft,
	})
}
