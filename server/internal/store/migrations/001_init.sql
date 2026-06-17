CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_sub   TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    avatar_seed  TEXT NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS game_results (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code    TEXT NOT NULL,
    mode         TEXT NOT NULL,
    deck_id      TEXT NOT NULL DEFAULT '',
    total_rounds INT NOT NULL,
    rounds       JSONB NOT NULL DEFAULT '[]'::jsonb,
    finished_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS game_result_players (
    game_id  UUID NOT NULL REFERENCES game_results(id) ON DELETE CASCADE,
    user_id  UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    nickname TEXT NOT NULL,
    score    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (game_id, nickname)
);

CREATE INDEX IF NOT EXISTS idx_game_result_players_user
    ON game_result_players(user_id)
    WHERE user_id IS NOT NULL;
