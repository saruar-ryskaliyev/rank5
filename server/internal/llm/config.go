package llm

import (
	"fmt"
	"math"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	DriverOpenAICompatible = "openai_compatible"
	DriverCloudflareRun    = "cloudflare_run"
)

// Pricing is optional and is used only for normalized usage reporting.
type Pricing struct {
	InputUSDPerMillion  float64
	OutputUSDPerMillion float64
	USDPerThousandUnits float64
}

func (p Pricing) EstimateMicroUSD(usage Usage) int64 {
	if usage.ProviderUnits > 0 && p.USDPerThousandUnits > 0 {
		return int64(math.Round(usage.ProviderUnits * p.USDPerThousandUnits * 1000))
	}
	return int64(math.Round(
		float64(usage.InputTokens)*p.InputUSDPerMillion +
			float64(usage.OutputTokens)*p.OutputUSDPerMillion,
	))
}

// Config selects an adapter at startup. LLM_* variables are provider-neutral;
// existing CLOUDFLARE_* variables remain a convenient local-development fallback.
type Config struct {
	Enabled                      bool
	Driver                       string
	BaseURL                      string
	APIKey                       string
	Model                        string
	CloudflareAccountID          string
	Timeout                      time.Duration
	MaxOutputTokens              int
	UserDailyLimit               int
	GlobalDailyLimit             int
	GlobalDailyProviderUnitLimit float64
	GlobalDailyCostMicroUSDLimit int64
	Pricing                      Pricing
}

func LoadConfig() Config {
	driver := strings.TrimSpace(os.Getenv("LLM_DRIVER"))
	baseURL := strings.TrimSpace(os.Getenv("LLM_BASE_URL"))
	apiKey := strings.TrimSpace(os.Getenv("LLM_API_KEY"))
	model := strings.TrimSpace(os.Getenv("LLM_MODEL"))
	accountID := strings.TrimSpace(os.Getenv("CLOUDFLARE_ACCOUNT_ID"))

	if driver == "" && accountID != "" && os.Getenv("CLOUDFLARE_AI_TOKEN") != "" {
		driver = DriverCloudflareRun
	}
	if apiKey == "" && driver == DriverCloudflareRun {
		apiKey = strings.TrimSpace(os.Getenv("CLOUDFLARE_AI_TOKEN"))
	}
	if model == "" && driver == DriverCloudflareRun {
		model = strings.TrimSpace(os.Getenv("CLOUDFLARE_AI_MODEL"))
	}
	if model == "" && driver == DriverCloudflareRun {
		model = "@cf/openai/gpt-oss-120b"
	}

	configured := driver != "" && apiKey != "" && model != "" &&
		(driver != DriverOpenAICompatible || baseURL != "") &&
		(driver != DriverCloudflareRun || accountID != "")
	enabled := configured
	if raw := strings.TrimSpace(os.Getenv("LLM_ENABLED")); raw != "" {
		enabled = parseBool(raw, false) && configured
	}

	pricing := Pricing{
		InputUSDPerMillion:  envFloat("LLM_INPUT_USD_PER_MILLION", 0),
		OutputUSDPerMillion: envFloat("LLM_OUTPUT_USD_PER_MILLION", 0),
		USDPerThousandUnits: envFloat("LLM_USD_PER_THOUSAND_UNITS", 0),
	}
	if driver == DriverCloudflareRun {
		if pricing.USDPerThousandUnits == 0 {
			pricing.USDPerThousandUnits = 0.011
		}
		switch {
		case strings.Contains(model, "gpt-oss-120b"):
			setDefaultTokenPricing(&pricing, 0.35, 0.75)
		case strings.Contains(model, "kimi-k2.6"):
			setDefaultTokenPricing(&pricing, 0.95, 4.00)
		case strings.Contains(model, "gemma-4-26b-a4b-it"):
			setDefaultTokenPricing(&pricing, 0.10, 0.30)
		}
	}

	providerUnitLimit := envFloat("LLM_GLOBAL_DAILY_PROVIDER_UNIT_LIMIT", 0)
	if providerUnitLimit == 0 && driver == DriverCloudflareRun {
		providerUnitLimit = 8000
	}
	dailyCostUSDLimit := envFloat("LLM_GLOBAL_DAILY_COST_USD_LIMIT", 0)

	return Config{
		Enabled:                      enabled,
		Driver:                       driver,
		BaseURL:                      baseURL,
		APIKey:                       apiKey,
		Model:                        model,
		CloudflareAccountID:          accountID,
		Timeout:                      time.Duration(envInt("LLM_TIMEOUT_SECONDS", 35)) * time.Second,
		MaxOutputTokens:              envInt("LLM_MAX_OUTPUT_TOKENS", 3000),
		UserDailyLimit:               envInt("LLM_USER_DAILY_LIMIT", 5),
		GlobalDailyLimit:             envInt("LLM_GLOBAL_DAILY_LIMIT", 150),
		GlobalDailyProviderUnitLimit: providerUnitLimit,
		GlobalDailyCostMicroUSDLimit: int64(math.Round(dailyCostUSDLimit * 1_000_000)),
		Pricing:                      pricing,
	}
}

func setDefaultTokenPricing(pricing *Pricing, input, output float64) {
	if pricing.InputUSDPerMillion == 0 {
		pricing.InputUSDPerMillion = input
	}
	if pricing.OutputUSDPerMillion == 0 {
		pricing.OutputUSDPerMillion = output
	}
}

func (c Config) Validate() error {
	if !c.Enabled {
		return nil
	}
	if c.APIKey == "" || c.Model == "" {
		return fmt.Errorf("LLM_API_KEY and LLM_MODEL are required")
	}
	switch c.Driver {
	case DriverOpenAICompatible:
		if c.BaseURL == "" {
			return fmt.Errorf("LLM_BASE_URL is required for %s", c.Driver)
		}
	case DriverCloudflareRun:
		if c.CloudflareAccountID == "" {
			return fmt.Errorf("CLOUDFLARE_ACCOUNT_ID is required for %s", c.Driver)
		}
	default:
		return fmt.Errorf("unsupported LLM_DRIVER %q", c.Driver)
	}
	if c.Timeout <= 0 || c.MaxOutputTokens <= 0 {
		return fmt.Errorf("LLM timeout and output-token limit must be positive")
	}
	return nil
}

func parseBool(raw string, fallback bool) bool {
	v, err := strconv.ParseBool(raw)
	if err != nil {
		return fallback
	}
	return v
}

func envInt(key string, fallback int) int {
	v, err := strconv.Atoi(strings.TrimSpace(os.Getenv(key)))
	if err != nil {
		return fallback
	}
	return v
}

func envFloat(key string, fallback float64) float64 {
	v, err := strconv.ParseFloat(strings.TrimSpace(os.Getenv(key)), 64)
	if err != nil {
		return fallback
	}
	return v
}
