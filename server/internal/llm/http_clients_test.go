package llm

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestOpenAICompatibleClientNormalizesResponseAndUsage(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/chat/completions" {
			t.Fatalf("path = %q", r.URL.Path)
		}
		if r.Header.Get("Authorization") != "Bearer secret" {
			t.Fatalf("authorization header missing")
		}
		var request map[string]any
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Fatal(err)
		}
		if request["reasoning_effort"] != "none" {
			t.Fatalf("reasoning_effort = %#v", request["reasoning_effort"])
		}
		if request["model"] != "test" {
			t.Fatalf("model = %#v", request["model"])
		}
		writeTestJSON(t, w, map[string]any{
			"choices": []any{map[string]any{
				"finish_reason": "stop",
				"message":       map[string]any{"content": `{"ok":true}`},
			}},
			"usage": map[string]any{"prompt_tokens": 10, "completion_tokens": 20},
		})
	}))
	defer server.Close()

	client, err := NewClient(Config{
		Enabled: true, Driver: DriverOpenAICompatible, BaseURL: server.URL + "/v1",
		APIKey: "secret", Model: "test", Timeout: time.Second, MaxOutputTokens: 100,
		Pricing: Pricing{InputUSDPerMillion: 0.10, OutputUSDPerMillion: 0.30},
	})
	if err != nil {
		t.Fatal(err)
	}
	response, err := client.Complete(context.Background(), CompletionRequest{
		Messages: []Message{{Role: "user", Content: "test"}}, JSONMode: true,
		DisableReasoning: true, MaxOutputTokens: 100,
	})
	if err != nil {
		t.Fatal(err)
	}
	if response.Content != `{"ok":true}` || response.FinishReason != "stop" {
		t.Fatalf("unexpected response: %+v", response)
	}
	if response.Usage.InputTokens != 10 || response.Usage.OutputTokens != 20 || response.Usage.EstimatedCostMicroUSD != 7 {
		t.Fatalf("unexpected usage: %+v", response.Usage)
	}
}

func TestCloudflareEnvelopeSupportsObjectResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		writeTestJSON(t, w, map[string]any{
			"success": true,
			"result": map[string]any{
				"response": map[string]any{"title": "Deck"},
				"usage":    map[string]any{"neurons": 20.0},
			},
		})
	}))
	defer server.Close()

	client := &httpClient{
		client: &http.Client{Timeout: time.Second}, driver: DriverCloudflareRun,
		model: "model", url: server.URL, apiKey: "secret", cloudflareEnvelope: true,
		pricing: Pricing{USDPerThousandUnits: 0.011},
	}
	response, err := client.Complete(context.Background(), CompletionRequest{Messages: []Message{{Role: "user", Content: "x"}}})
	if err != nil {
		t.Fatal(err)
	}
	if response.Content != `{"title":"Deck"}` || response.Usage.EstimatedCostMicroUSD != 220 {
		t.Fatalf("unexpected response: %+v", response)
	}
}

func TestWireRequestTranslatesStructuredOutputPerProvider(t *testing.T) {
	schema := json.RawMessage(`{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"]}`)
	request := CompletionRequest{
		Messages: []Message{{Role: "user", Content: "test"}}, Temperature: 0.2,
		MaxOutputTokens: 3000, JSONSchemaName: "rank5_deck", JSONSchema: schema,
		DisableReasoning: true,
	}

	cloudflare := newWireRequest(request, true, "@cf/openai/gpt-oss-120b")
	if cloudflare.MaxTokens != 3000 || cloudflare.MaxCompletionTokens != 0 || cloudflare.ReasoningEffort != "low" {
		t.Fatalf("unexpected GPT-OSS translation: %+v", cloudflare)
	}
	if cloudflare.ResponseFormat == nil || cloudflare.ResponseFormat.Type != "json_schema" {
		t.Fatalf("Cloudflare schema missing: %+v", cloudflare.ResponseFormat)
	}
	if _, ok := cloudflare.ResponseFormat.JSONSchema.(json.RawMessage); !ok {
		t.Fatalf("Cloudflare must receive the raw schema: %#v", cloudflare.ResponseFormat.JSONSchema)
	}

	compatible := newWireRequest(request, false, "provider-model")
	if compatible.MaxCompletionTokens != 3000 || compatible.MaxTokens != 0 || compatible.ReasoningEffort != "none" {
		t.Fatalf("unexpected OpenAI-compatible translation: %+v", compatible)
	}
	named, ok := compatible.ResponseFormat.JSONSchema.(namedJSONSchema)
	if !ok || named.Name != "rank5_deck" || !named.Strict {
		t.Fatalf("OpenAI-compatible named schema missing: %#v", compatible.ResponseFormat.JSONSchema)
	}

	kimi := newWireRequest(request, true, "@cf/moonshotai/kimi-k2.6")
	if thinking, ok := kimi.ChatTemplateKwargs["thinking"].(bool); !ok || thinking {
		t.Fatalf("Kimi fallback should disable thinking: %+v", kimi.ChatTemplateKwargs)
	}
}

func TestProviderHTTPErrorExtractsBoundedMessage(t *testing.T) {
	err := providerHTTPError(http.StatusBadRequest, []byte(`{"errors":[{"message":"unsupported schema field"}]}`))
	if got := err.Error(); got != "LLM provider returned HTTP 400: unsupported schema field" {
		t.Fatalf("unexpected provider error: %s", got)
	}
}

func writeTestJSON(t *testing.T, w http.ResponseWriter, value any) {
	t.Helper()
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(value); err != nil {
		t.Fatal(err)
	}
}
