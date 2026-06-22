# Rank5 Product Spec — Profiles, Stats, LLM Decks

Status: PROPOSAL for review · Author: product discovery session 2026-08-04
Builds on: `plans/2026-08-04-ui-redesign.md` (shipped), community-decks evaluation (chat, 2026-08-04)

## Product thesis

Rank5's moat is the live moment between friends. Everything here strengthens that moment or lets
players keep what it produces. Nothing may add friction to nickname → room code → play.

**North-star principle (lazy registration):** login never gates *playing*; it gates *keeping* —
identity, stats, created decks. Login prompts appear only at moments of earned value, never up front.

## What login unlocks (the Profile value proposition)

1. **Identity** — stable display name + avatar across games; friends recognize you between sessions.
2. **Stats** — the unique asset. Rank5's data is "how well do you know your friends":
   - Games played, versus win rate, co-op team bests
   - Guess quality: average displacement → "guess accuracy" (server already computes displacement)
   - Superlatives: Best Guesser, Most Predictable (fun, shareable)
   - Friend chemistry: per-co-player accuracy ("You know Maya 86%") — only linked when BOTH players
     are logged in; anonymous co-players remain ephemeral nicknames, never linkable
3. **Content** — saved and (later) published decks, including LLM-generated ones.
4. Cross-device continuity.

**Privacy default:** stats are private to the owner. Sharing happens via an explicit end-of-game
result card (image/share-sheet). No public profiles in v1 — "what your friends think of you" data is
personal; opt-in sharing only.

**Conversion moment:** the Results screen, at the emotional peak:
"Save your stats — create a profile." A guest's just-finished game is claimable at signup (session →
account attach). This replaces any create-wall as the primary registration driver.

## LLM-generated decks (milestone 5 — deprioritized)

Shape: **ephemeral room magic**. Host (logged in) types a topic → server generates a 6-question
deck (5 options each, ranking-suitable) → playable immediately → dies with the room.

- Login + per-user quota required (cost + abuse control; also a natural login driver)
- Ephemeral by default = only your room sees it = low moderation bar (server-side safety filter on
  generation output is still required: slurs/PII/self-harm blocklist + refusal categories)
- Saving a generated deck privately for reuse = small follow-on. PUBLISHING to the community
  library is explicitly deferred to milestone 3 (that's where human moderation cost lives)
- Prompt design: template enforces "rankable by personal preference" question shape (matches the
  official scoring model); topic + optional context ("our friend group" free-text stays private)
- Cost control: small/cheap model, per-user daily quota (e.g. 5 decks), topic-level caching

## Milestones (REVISED 2026-08-04 12:20 — manual creation prioritized, LLM deferred)

**M1 — Foundation: Postgres + auth + minimal Profile**
- Backend: Postgres; auth = Google Sign-In on Android exchanging for our own JWTs (self-hosted Go,
  no Firebase dependency; Apple Sign-In when iOS ships); `users` table; `GET /me`, `DELETE /me`
  (Play Store requires account deletion); privacy policy page. WebSocket game loop stays anonymous —
  a logged-in client presents its JWT when joining so results attribute to the account.
- ALSO start writing `game_results` at GAME_OVER now (one table, no UI) so stats history accumulates
  retroactively for M4.
- Client: avatar chip on Home → minimal Profile screen (identity, sign in/out). No bottom nav yet.

**M2 — Manual deck creation (private decks)**
- Backend: deck CRUD (`decks` table, JSONB questions, visibility private/unlisted); room creation
  resolves deck ids from the DB (replaces static-JSON-only loading; built-in decks become DB rows).
- Client: deck editor (title, emoji, 6 questions × 5 options), "My decks" in Profile, own decks
  selectable in the Lobby. Zero moderation burden while decks stay private.

**M3 — Community deck library**
- Backend: publish flow, browse/search (Postgres full-text), reports + moderation tooling.
- Client: 3-tab shell Play / Decks / Profile; bottom nav hidden during live game sessions.
- Cold start solved by M2: early users' private decks become launch content via a publish nudge.

**M4 — Stats surface** (data already collected since M1)
- Backend: `GET /me/stats` aggregation endpoint.
- Client: stats on Profile, "Save your stats" guest prompt on Results, shareable result card.

**M5 — LLM deck generation** (deprioritized; unchanged design, see LLM section)
- Slots into the M2 deck editor as "generate with AI"; quotas + safety filter; ephemeral or private.

## Explicitly not doing

- Async feed / follows / activity notifications (different product; re-evaluate only if community
  decks prove async demand)
- Public profiles / follower counts in v1
- Login walls anywhere in the join-and-play path

## Open questions (flagged, defaults chosen)

- Auth: Google-only at M1 is accepted (email magic link as fallback later if data shows need)
- LLM model/provider + exact quota numbers: decide at M2 implementation
- Whether chemistry stats need both-parties consent UI beyond mutual login: revisit at M1 design
