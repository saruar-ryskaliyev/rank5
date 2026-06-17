package store

import (
	"strings"
	"testing"
)

func TestSavedDeckMigrationHasCascadeForeignKeysAndCompositeKey(t *testing.T) {
	raw, err := migrationsFS.ReadFile("migrations/006_saved_decks.sql")
	if err != nil {
		t.Fatal(err)
	}
	sql := strings.ToLower(string(raw))
	for _, required := range []string{
		"primary key (user_id, deck_id)",
		"references users(id) on delete cascade",
		"references decks(id) on delete cascade",
		"idx_user_saved_decks_deck",
	} {
		if !strings.Contains(sql, required) {
			t.Fatalf("migration missing %q", required)
		}
	}
}

func TestMultiDeckResultMigrationRetainsLegacyAndBackfills(t *testing.T) {
	raw, err := migrationsFS.ReadFile("migrations/007_multideck_results.sql")
	if err != nil {
		t.Fatal(err)
	}
	sql := strings.ToLower(string(raw))
	if !strings.Contains(sql, "add column if not exists deck_ids jsonb") ||
		!strings.Contains(sql, "jsonb_build_array(deck_id)") {
		t.Fatalf("migration does not add/backfill deck_ids: %s", sql)
	}
}

func TestLLMGenerationMigrationStoresUsageWithoutRawContent(t *testing.T) {
	raw, err := migrationsFS.ReadFile("migrations/008_llm_generations.sql")
	if err != nil {
		t.Fatal(err)
	}
	sql := strings.ToLower(string(raw))
	for _, required := range []string{
		"provider_units", "estimated_cost_microusd", "prompt_version", "user_id",
	} {
		if !strings.Contains(sql, required) {
			t.Fatalf("migration missing %q", required)
		}
	}
	for _, forbidden := range []string{"raw_topic", "raw_prompt", "raw_response"} {
		if strings.Contains(sql, forbidden) {
			t.Fatalf("migration must not persist %q", forbidden)
		}
	}
}
