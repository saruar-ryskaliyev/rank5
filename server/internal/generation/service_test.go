package generation

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/saruar/rank5/server/internal/llm"
)

type fakeClient struct {
	responses []llm.CompletionResponse
	errs      []error
	requests  []llm.CompletionRequest
	calls     int
}

func (f *fakeClient) Complete(_ context.Context, request llm.CompletionRequest) (llm.CompletionResponse, error) {
	index := f.calls
	f.calls++
	f.requests = append(f.requests, request)
	if index < len(f.errs) && f.errs[index] != nil {
		return llm.CompletionResponse{}, f.errs[index]
	}
	return f.responses[index], nil
}

func (f *fakeClient) Info() llm.ProviderInfo {
	return llm.ProviderInfo{Driver: "fake", Model: "test-model"}
}

const validDeck = `{
  "title":"Weekend Adventures",
  "emoji":"🏕️",
  "questions":[
    {"id":"wrong","prompt":"Ideal Saturday activity?","options":["Hiking","Camping","Beach","Museum","Road trip"]},
    {"id":"wrong","prompt":"Best travel snack?","options":["Fruit","Chocolate","Sandwich","Trail mix","Jerky"]},
    {"id":"wrong","prompt":"Perfect place to relax?","options":["Lake","Forest","Mountain","Cafe","Garden"]}
  ]
}`

func TestGenerateNormalizesAndReturnsValidDraft(t *testing.T) {
	client := &fakeClient{responses: []llm.CompletionResponse{{
		Content: validDeck,
		Usage:   llm.Usage{InputTokens: 10, OutputTokens: 20, ProviderUnits: 1.5},
	}}}
	service := NewService(client, 1500)

	result, err := service.Generate(context.Background(), Request{Topic: "Weekend adventures", QuestionCount: 3})
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if result.Draft.Questions[0].ID != "q1" || result.Draft.Questions[2].ID != "q3" {
		t.Fatalf("question ids were not normalized: %+v", result.Draft.Questions)
	}
	if result.Attempts != 1 || result.Usage.OutputTokens != 20 {
		t.Fatalf("unexpected metadata: %+v", result)
	}
	providerRequest := client.requests[0]
	if providerRequest.Temperature != 0.2 || len(providerRequest.JSONSchema) == 0 {
		t.Fatalf("generation should use low-temperature structured output: %+v", providerRequest)
	}
}

func TestGenerateReportsTruncationSeparately(t *testing.T) {
	client := &fakeClient{responses: []llm.CompletionResponse{
		{FinishReason: "length", Usage: llm.Usage{OutputTokens: 1500}},
		{FinishReason: "length", Usage: llm.Usage{OutputTokens: 1500}},
	}}
	service := NewService(client, 1500)

	result, err := service.Generate(context.Background(), Request{Topic: "Weekend adventures", QuestionCount: 3})
	if !errors.Is(err, ErrTruncatedOutput) || !errors.Is(err, ErrInvalidOutput) {
		t.Fatalf("expected truncated invalid output, got %v", err)
	}
	if result.Attempts != 2 || result.Usage.OutputTokens != 3000 {
		t.Fatalf("unexpected truncation metadata: %+v", result)
	}
}

func TestDeckJSONSchemaUsesCloudflareSupportedConstraints(t *testing.T) {
	schema := string(deckJSONSchema(6))
	if strings.Contains(schema, `"uniqueItems"`) {
		t.Fatal("Cloudflare GPT-OSS grammar does not implement uniqueItems")
	}
	if !strings.Contains(schema, `"minItems":6`) || !strings.Contains(schema, `"maxItems":6`) {
		t.Fatalf("schema does not enforce the requested question count: %s", schema)
	}
}

func TestGenerateRetriesInvalidOutputOnce(t *testing.T) {
	client := &fakeClient{responses: []llm.CompletionResponse{
		{Content: `{"title":"Bad","emoji":"🃏","questions":{}}`, Usage: llm.Usage{OutputTokens: 5}},
		{Content: validDeck, Usage: llm.Usage{OutputTokens: 20}},
	}}
	service := NewService(client, 1500)

	result, err := service.Generate(context.Background(), Request{Topic: "Weekend adventures", QuestionCount: 3})
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if result.Attempts != 2 || result.Usage.OutputTokens != 25 || client.calls != 2 {
		t.Fatalf("retry or usage aggregation failed: result=%+v calls=%d", result, client.calls)
	}
}

func TestGenerateRetriesEmptySuccessfulProviderResponse(t *testing.T) {
	client := &fakeClient{responses: []llm.CompletionResponse{
		{Content: ""},
		{Content: validDeck},
	}}
	service := NewService(client, 1500)

	result, err := service.Generate(context.Background(), Request{Topic: "Weekend adventures", QuestionCount: 3})
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if result.Attempts != 2 {
		t.Fatalf("expected empty response retry, got %d attempts", result.Attempts)
	}
}

func TestGenerateRejectsDuplicateOptionsAfterRetry(t *testing.T) {
	duplicate := `{"title":"Food","emoji":"🍕","questions":[` +
		`{"id":"q1","prompt":"Pick one","options":["Pizza","Pizza","Tacos","Pasta","Soup"]},` +
		`{"id":"q2","prompt":"Pick two","options":["A","B","C","D","E"]},` +
		`{"id":"q3","prompt":"Pick three","options":["F","G","H","I","J"]}]}`
	client := &fakeClient{responses: []llm.CompletionResponse{{Content: duplicate}, {Content: duplicate}}}
	service := NewService(client, 1500)

	_, err := service.Generate(context.Background(), Request{Topic: "Food favorites", QuestionCount: 3})
	if !errors.Is(err, ErrInvalidOutput) {
		t.Fatalf("expected ErrInvalidOutput, got %v", err)
	}
	if client.calls != 2 {
		t.Fatalf("expected exactly two attempts, got %d", client.calls)
	}
}

func TestGenerateValidatesRequestBeforeCallingProvider(t *testing.T) {
	client := &fakeClient{}
	service := NewService(client, 1500)

	_, err := service.Generate(context.Background(), Request{Topic: "x", QuestionCount: 3})
	if !errors.Is(err, ErrInvalidRequest) {
		t.Fatalf("expected ErrInvalidRequest, got %v", err)
	}
	if client.calls != 0 {
		t.Fatalf("provider should not be called for invalid input")
	}
}

func TestGenerateReturnsProviderErrorWithoutSemanticRetry(t *testing.T) {
	want := errors.New("provider unavailable")
	client := &fakeClient{errs: []error{want}}
	service := NewService(client, 1500)

	_, err := service.Generate(context.Background(), Request{Topic: "Weekend", QuestionCount: 3})
	if !errors.Is(err, want) {
		t.Fatalf("expected provider error, got %v", err)
	}
	if client.calls != 1 {
		t.Fatalf("transport errors must not be retried here")
	}
}

func TestParseRejectsTrailingJSON(t *testing.T) {
	_, err := parseAndValidate(validDeck+` {}`, 3)
	if err == nil {
		t.Fatal("expected trailing JSON to be rejected")
	}
}
