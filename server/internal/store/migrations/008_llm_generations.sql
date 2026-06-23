CREATE TABLE IF NOT EXISTS llm_generations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider                 TEXT NOT NULL,
    model                    TEXT NOT NULL,
    prompt_version           TEXT NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'pending',
    attempts                 INT NOT NULL DEFAULT 0,
    input_tokens             BIGINT NOT NULL DEFAULT 0,
    output_tokens            BIGINT NOT NULL DEFAULT 0,
    provider_units           DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_cost_microusd  BIGINT NOT NULL DEFAULT 0,
    latency_ms               BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'llm_generations_status_check'
    ) THEN
        ALTER TABLE llm_generations
            ADD CONSTRAINT llm_generations_status_check
            CHECK (status IN ('pending', 'success', 'invalid_output', 'provider_error'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_llm_generations_created
    ON llm_generations(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_generations_user_created
    ON llm_generations(user_id, created_at DESC);
