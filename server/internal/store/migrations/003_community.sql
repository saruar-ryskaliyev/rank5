-- M3: community library — visibility, FTS, reports, admin flag.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE decks
    ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'private';

ALTER TABLE decks
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

ALTER TABLE decks
    ADD COLUMN IF NOT EXISTS owner_name TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'decks_visibility_check'
    ) THEN
        ALTER TABLE decks
            ADD CONSTRAINT decks_visibility_check
            CHECK (visibility IN ('private', 'public', 'removed'));
    END IF;
END $$;

-- Full-text search over title + questions JSON (prompts + options).
ALTER TABLE decks
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector(
            'english',
            coalesce(title, '') || ' ' || coalesce(questions::text, '') || ' ' || coalesce(owner_name, '')
        )
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_decks_search_vector
    ON decks USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_decks_public_published
    ON decks (published_at DESC)
    WHERE visibility = 'public';

CREATE TABLE IF NOT EXISTS deck_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deck_id     TEXT NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason      TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'open',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (deck_id, reporter_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'deck_reports_status_check'
    ) THEN
        ALTER TABLE deck_reports
            ADD CONSTRAINT deck_reports_status_check
            CHECK (status IN ('open', 'dismissed', 'actioned'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_deck_reports_open
    ON deck_reports (created_at DESC)
    WHERE status = 'open';
