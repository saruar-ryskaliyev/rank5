package llm

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

const maxProviderResponseBytes = 2 << 20

type httpClient struct {
	client             *http.Client
	driver             string
	model              string
	url                string
	apiKey             string
	pricing            Pricing
	cloudflareEnvelope bool
}

func NewClient(config Config) (Client, error) {
	if err := config.Validate(); err != nil {
		return nil, err
	}
	if !config.Enabled {
		return nil, nil
	}
	endpoint := ""
	cloudflareEnvelope := false
	switch config.Driver {
	case DriverOpenAICompatible:
		endpoint = strings.TrimRight(config.BaseURL, "/") + "/chat/completions"
	case DriverCloudflareRun:
		accountID := url.PathEscape(config.CloudflareAccountID)
		endpoint = "https://api.cloudflare.com/client/v4/accounts/" + accountID + "/ai/run/" + config.Model
		cloudflareEnvelope = true
	}
	return &httpClient{
		client:             &http.Client{Timeout: config.Timeout},
		driver:             config.Driver,
		model:              config.Model,
		url:                endpoint,
		apiKey:             config.APIKey,
		pricing:            config.Pricing,
		cloudflareEnvelope: cloudflareEnvelope,
	}, nil
}

func (c *httpClient) Info() ProviderInfo {
	return ProviderInfo{Driver: c.driver, Model: c.model}
}

func (c *httpClient) Complete(ctx context.Context, request CompletionRequest) (CompletionResponse, error) {
	payload := newWireRequest(request, c.cloudflareEnvelope, c.model)
	if !c.cloudflareEnvelope {
		payload.Model = c.model
	}
	encoded, err := json.Marshal(payload)
	if err != nil {
		return CompletionResponse{}, fmt.Errorf("encode completion request: %w", err)
	}
	httpRequest, err := http.NewRequestWithContext(ctx, http.MethodPost, c.url, bytes.NewReader(encoded))
	if err != nil {
		return CompletionResponse{}, fmt.Errorf("create completion request: %w", err)
	}
	httpRequest.Header.Set("Authorization", "Bearer "+c.apiKey)
	httpRequest.Header.Set("Content-Type", "application/json")

	response, err := c.client.Do(httpRequest)
	if err != nil {
		return CompletionResponse{}, fmt.Errorf("call LLM provider: %w", err)
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, maxProviderResponseBytes))
	if err != nil {
		return CompletionResponse{}, fmt.Errorf("read LLM response: %w", err)
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return CompletionResponse{}, providerHTTPError(response.StatusCode, body)
	}

	var wire completionWireResponse
	if c.cloudflareEnvelope {
		var envelope struct {
			Success bool                   `json:"success"`
			Result  completionWireResponse `json:"result"`
			Errors  []struct {
				Message string `json:"message"`
			} `json:"errors"`
		}
		if err := json.Unmarshal(body, &envelope); err != nil {
			return CompletionResponse{}, fmt.Errorf("decode Cloudflare response: %w", err)
		}
		if !envelope.Success {
			return CompletionResponse{}, fmt.Errorf("Cloudflare completion failed")
		}
		wire = envelope.Result
	} else if err := json.Unmarshal(body, &wire); err != nil {
		return CompletionResponse{}, fmt.Errorf("decode completion response: %w", err)
	}

	normalized, err := normalizeWireResponse(wire, c.pricing)
	if err != nil {
		return CompletionResponse{}, fmt.Errorf("normalize completion response: %w", err)
	}
	return normalized, nil
}

func providerHTTPError(status int, body []byte) error {
	var payload struct {
		Error struct {
			Message string `json:"message"`
		} `json:"error"`
		Errors []struct {
			Message string `json:"message"`
		} `json:"errors"`
	}
	if json.Unmarshal(body, &payload) == nil {
		message := strings.TrimSpace(payload.Error.Message)
		if message == "" && len(payload.Errors) > 0 {
			message = strings.TrimSpace(payload.Errors[0].Message)
		}
		if len(message) > 500 {
			message = message[:500]
		}
		if message != "" {
			return fmt.Errorf("LLM provider returned HTTP %d: %s", status, message)
		}
	}
	return fmt.Errorf("LLM provider returned HTTP %d", status)
}
