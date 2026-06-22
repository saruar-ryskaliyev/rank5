ALTER TABLE game_results
    ADD COLUMN IF NOT EXISTS deck_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE game_results
SET deck_ids = jsonb_build_array(deck_id)
WHERE deck_ids = '[]'::jsonb AND deck_id <> '';

CREATE INDEX IF NOT EXISTS idx_game_results_deck_ids
    ON game_results USING GIN (deck_ids);
