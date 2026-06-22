CREATE TABLE IF NOT EXISTS user_saved_decks (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    deck_id    TEXT NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, deck_id)
);

CREATE INDEX IF NOT EXISTS idx_user_saved_decks_deck
    ON user_saved_decks(deck_id);

CREATE INDEX IF NOT EXISTS idx_user_saved_decks_created
    ON user_saved_decks(user_id, created_at DESC);
