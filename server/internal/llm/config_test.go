package llm

import "testing"

func TestLoadConfigUsesProviderNeutralVariables(t *testing.T) {
	t.Setenv("LLM_ENABLED", "true")
	t.Setenv("LLM_DRIVER", DriverOpenAICompatible)
	t.Setenv("LLM_BASE_URL", "https://example.test/v1")
	t.Setenv("LLM_API_KEY", "secret")
	t.Setenv("LLM_MODEL", "model")

	config := LoadConfig()
	if !config.Enabled || config.Driver != DriverOpenAICompatible || config.Model != "model" {
		t.Fatalf("unexpected config: %+v", config)
	}
}

func TestLoadConfigAutoDetectsExistingCloudflareVariables(t *testing.T) {
	t.Setenv("LLM_ENABLED", "")
	t.Setenv("LLM_DRIVER", "")
	t.Setenv("LLM_API_KEY", "")
	t.Setenv("LLM_MODEL", "")
	t.Setenv("CLOUDFLARE_ACCOUNT_ID", "account")
	t.Setenv("CLOUDFLARE_AI_TOKEN", "secret")
	t.Setenv("CLOUDFLARE_AI_MODEL", "")

	config := LoadConfig()
	if !config.Enabled || config.Driver != DriverCloudflareRun || config.Model != "@cf/openai/gpt-oss-120b" {
		t.Fatalf("unexpected Cloudflare fallback: %+v", config)
	}
	if config.GlobalDailyProviderUnitLimit != 8000 {
		t.Fatalf("Cloudflare provider-unit limit = %v, want 8000", config.GlobalDailyProviderUnitLimit)
	}
	if config.MaxOutputTokens != 3000 || config.Pricing.InputUSDPerMillion != 0.35 || config.Pricing.OutputUSDPerMillion != 0.75 {
		t.Fatalf("unexpected GPT-OSS defaults: %+v", config)
	}
}
