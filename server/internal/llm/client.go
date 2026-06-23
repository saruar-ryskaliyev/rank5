package llm

import (
	"context"
	"encoding/json"
	"strings"
)

// Message is a provider-neutral chat message.
type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// CompletionRequest describes the small subset of chat-completion features
// needed by Rank5. Provider adapters translate it to their wire format.
type CompletionRequest struct {
	Messages         []Message
	Temperature      float64
	MaxOutputTokens  int
	JSONMode         bool
	JSONSchemaName   string
	JSONSchema       json.RawMessage
	DisableReasoning bool
}

// Usage normalizes metering returned by different providers.
type Usage struct {
	InputTokens           int64
	OutputTokens          int64
	ProviderUnits         float64
	EstimatedCostMicroUSD int64
}

func (u Usage) Add(other Usage) Usage {
	return Usage{
		InputTokens:           u.InputTokens + other.InputTokens,
		OutputTokens:          u.OutputTokens + other.OutputTokens,
		ProviderUnits:         u.ProviderUnits + other.ProviderUnits,
		EstimatedCostMicroUSD: u.EstimatedCostMicroUSD + other.EstimatedCostMicroUSD,
	}
}

// CompletionResponse is the normalized result consumed by domain services.
type CompletionResponse struct {
	Content      string
	FinishReason string
	Usage        Usage
}

// ProviderInfo is safe to log and persist. It must never contain credentials.
type ProviderInfo struct {
	Driver string
	Model  string
}

// Client is implemented by each LLM transport.
type Client interface {
	Complete(context.Context, CompletionRequest) (CompletionResponse, error)
	Info() ProviderInfo
}

type completionWireRequest struct {
	Model               string          `json:"model,omitempty"`
	Messages            []Message       `json:"messages"`
	Temperature         float64         `json:"temperature,omitempty"`
	MaxTokens           int             `json:"max_tokens,omitempty"`
	MaxCompletionTokens int             `json:"max_completion_tokens,omitempty"`
	ReasoningEffort     string          `json:"reasoning_effort,omitempty"`
	ChatTemplateKwargs  map[string]any  `json:"chat_template_kwargs,omitempty"`
	ResponseFormat      *responseFormat `json:"response_format,omitempty"`
}

type responseFormat struct {
	Type       string `json:"type"`
	JSONSchema any    `json:"json_schema,omitempty"`
}

type namedJSONSchema struct {
	Name   string          `json:"name"`
	Strict bool            `json:"strict"`
	Schema json.RawMessage `json:"schema"`
}

type completionWireResponse struct {
	Response json.RawMessage `json:"response"`
	Choices  []struct {
		FinishReason string `json:"finish_reason"`
		Message      struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
	FinishReason string `json:"finish_reason"`
	Usage        struct {
		PromptTokens     int64   `json:"prompt_tokens"`
		CompletionTokens int64   `json:"completion_tokens"`
		Neurons          float64 `json:"neurons"`
	} `json:"usage"`
}

func newWireRequest(request CompletionRequest, cloudflareDirect bool, model string) completionWireRequest {
	wire := completionWireRequest{
		Messages:    request.Messages,
		Temperature: request.Temperature,
	}
	if cloudflareDirect && strings.Contains(model, "gpt-oss") {
		wire.MaxTokens = request.MaxOutputTokens
	} else {
		wire.MaxCompletionTokens = request.MaxOutputTokens
	}
	if request.DisableReasoning {
		switch {
		case cloudflareDirect && strings.Contains(model, "gpt-oss"):
			// Cloudflare's Harmony runtime rejects "none" for GPT-OSS.
			wire.ReasoningEffort = "low"
		case cloudflareDirect && strings.Contains(model, "kimi-k2.6"):
			wire.ChatTemplateKwargs = map[string]any{"thinking": false}
		default:
			wire.ReasoningEffort = "none"
		}
	}
	if len(request.JSONSchema) > 0 {
		wire.ResponseFormat = &responseFormat{Type: "json_schema"}
		if cloudflareDirect {
			wire.ResponseFormat.JSONSchema = request.JSONSchema
		} else {
			name := request.JSONSchemaName
			if name == "" {
				name = "structured_response"
			}
			wire.ResponseFormat.JSONSchema = namedJSONSchema{
				Name: name, Strict: true, Schema: request.JSONSchema,
			}
		}
	} else if request.JSONMode {
		wire.ResponseFormat = &responseFormat{Type: "json_object"}
	}
	return wire
}

func normalizeWireResponse(wire completionWireResponse, pricing Pricing) (CompletionResponse, error) {
	content, err := rawContent(wire.Response)
	if err != nil {
		return CompletionResponse{}, err
	}
	finishReason := wire.FinishReason
	if len(wire.Choices) > 0 {
		if content == "" {
			content = wire.Choices[0].Message.Content
		}
		if finishReason == "" {
			finishReason = wire.Choices[0].FinishReason
		}
	}
	usage := Usage{
		InputTokens:   wire.Usage.PromptTokens,
		OutputTokens:  wire.Usage.CompletionTokens,
		ProviderUnits: wire.Usage.Neurons,
	}
	usage.EstimatedCostMicroUSD = pricing.EstimateMicroUSD(usage)
	return CompletionResponse{Content: content, FinishReason: finishReason, Usage: usage}, nil
}

func rawContent(raw json.RawMessage) (string, error) {
	if len(raw) == 0 || string(raw) == "null" {
		return "", nil
	}
	var text string
	if err := json.Unmarshal(raw, &text); err == nil {
		return text, nil
	}
	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return "", err
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		return "", err
	}
	return string(encoded), nil
}
