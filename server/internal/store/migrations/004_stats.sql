ALTER TABLE game_result_players
    ADD COLUMN IF NOT EXISTS player_id TEXT NULL,
    ADD COLUMN IF NOT EXISTS claim_token_hash TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_game_result_players_claim
    ON game_result_players(player_id, claim_token_hash)
    WHERE player_id IS NOT NULL AND claim_token_hash IS NOT NULL;
