CREATE TABLE IF NOT EXISTS decks (
    id         TEXT PRIMARY KEY,
    owner_id   UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    title      TEXT NOT NULL,
    emoji      TEXT NOT NULL DEFAULT '🃏',
    questions  JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_builtin BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_decks_owner
    ON decks(owner_id)
    WHERE owner_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_decks_builtin
    ON decks(id)
    WHERE is_builtin = true;
