-- Official decks are first-class entries in the same public library as
-- community decks. The embedded seed remains the source for their content.
UPDATE decks
SET visibility = 'public',
    owner_name = 'Rank5',
    published_at = coalesce(published_at, created_at),
    updated_at = now()
WHERE is_builtin = true;
