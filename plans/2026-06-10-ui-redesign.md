# Rank5 UI Redesign — Implementation Plan

Source: Dieter Rams audit `DESIGN-IS-2026-08-04/` (verdict REDESIGN, 15/30). Baseline screenshots in
`DESIGN-IS-2026-08-04/screens/`. Evidence anchors (E1.x–E5.x) refer to `01-evidence.md`.

Goal: redesign the presentation layer of the Android app with mobile best practices, keeping the flow
architecture (`Screen` when-router), the WebSocket protocol, and game mechanics intact. No server changes.
Not a git repo — no commits; verify each phase on the two running emulators (emulator-5554 host /
emulator-5556 guest, Go server already on host :8080).

## Scope decisions (conscious trade-offs)

- REVISED (user direction 2026-08-04 01:15): dual theme honoring `isSystemInDarkTheme()`. The signature
  look is a warm "tabletop paper" LIGHT theme (board-game heritage, distinct from the generic
  purple-on-black template); the DARK theme is warm ink, not pure black. NO electric purple anywhere.
- Strings stay in Kotlin (no localization pass now); copy comes verbatim from the Copy Deck below.
- Portrait phone-first; no tablet/landscape work.
- "Play again" becomes an honest instant **Rematch** (protocol has no return-to-lobby message; do NOT
  invent one).

## Design tokens (Phase 1 creates `ui/theme/` — all consumers use these, zero inline hexes/dp in screens)

### Color — "Tabletop" dual scheme (REVISED). Every ratio below computed with the WCAG formula on 2026-08-04; all listed pairs PASS AA.

**LIGHT (signature look — warm paper, ink, persimmon):**

| Role | Hex | Verified pairs |
|---|---|---|
| `background` | `#FAF6EF` | ink text 15.5:1 |
| `surface` (cards) | `#FFFFFF` | ink 16.7:1 |
| `surfaceVariant` | `#F3EDE3` | ink 14.4:1, muted 5.2:1 |
| `onBackground`/`onSurface` | `#221D15` | warm ink |
| `onSurfaceVariant` (the ONE muted) | `#6B6255` | 5.6:1 on background |
| `primary` (CTAs, badges, accents) | `#B23A1D` persimmon | white on it 5.97:1; as TEXT on background 5.5:1 (safe both ways) |
| `secondary` | `#166157` deep teal | white on it 7.3:1; as text 6.8:1 |
| `tertiary` (success) | `#2E6B38` | as text 5.95:1 |
| `error` | `#B3261E` | as text 6.1:1 |
| `outline` | `#D8CFC0` | borders/dividers (non-text) |
| gold fill / gold text | `#F5C544` / `#8A6D0B` | ink on gold fill 10.3:1; gold text 4.6:1 |

**DARK (warm ink, follows system):**

| Role | Hex | Verified pairs |
|---|---|---|
| `background` | `#161310` | warm-white text 15.3:1 |
| `surface` | `#1F1B17` | 14.2:1 |
| `surfaceVariant` | `#2B251E` | 12.6:1, muted 6.7:1 |
| `onBackground`/`onSurface` | `#EFE9DF` | warm white |
| `onSurfaceVariant` | `#B5AB9C` | 8.2:1 |
| `primary` (CTA fill) | `#E8662E` | **onPrimary = `#241004` dark ink 5.5:1 (white FAILS 4.46 — do not use)** |
| primary-as-TEXT | `#FF8A5C` | 8.0:1 bg / 6.5:1 card (timer, links, outlined buttons) |
| `secondary` | `#5CC0AE` teal | 8.5:1 as text; onSecondary `#10201C` |
| `tertiary` (success) | `#7BC47F` | implementer: verify ≥4.5 with the same formula, nudge lightness if short |
| `error` | `#FF7B66` | 7.3:1 |
| `outline` | `#4A4238` | |
| gold | `#E6BE4A` | 10.4:1 as text; ink `#241C04` on gold fill 9.5:1 |

Implementation notes:
- `Rank5Theme` picks scheme via `isSystemInDarkTheme()`. Extras (gold, goldText, accentText, urgent,
  avatarPalette) live in an immutable `Rank5Extras` class provided through a `LocalRank5Extras`
  CompositionLocal, with a light and a dark instance.
- Avatar palette (both themes, white initial on each — implementer verifies each ≥3:1 vs white):
  `[#B23A1D, #166157, #A66A08, #7B5CB8, #2E6B38, #B34A7D, #3E6FA3, #6B6255]` (muted, warm — no neon).
- GameScaffold gradient: light `#FDFBF6 → #F5EFE3`; dark `#1B1713 → #131009` (subtle, ≤4% luminance drift).
- RankCard/cards: container `surface`, 1dp `outline` border, 1–2dp shadow in light (paper-on-paper
  tactility), border-only in dark.
- Status-bar icons: `enableEdgeToEdge()` default auto style already follows system light/dark — matches.
- HARD RULE: no purple accents, no neon, no pure-black backgrounds; accent usage follows 60/30/10
  (neutrals dominate; persimmon reserved for the ONE primary action + key highlights per screen).

### Typography (custom `Typography` in theme; role → token, SAME on every screen — fixes E1.3)

| Role | Token | Spec |
|---|---|---|
| Big score / room code hero | `displayLarge` | 48sp, W800, -0.5sp tracking |
| Screen title | `headlineLarge` | 32sp, W700 |
| Prompt / question | `headlineMedium` | 24sp, W700, lineHeight 30sp |
| Card title / list item | `titleMedium` | 16sp, W600 |
| Body | `bodyMedium` | 14sp, W400, lineHeight 20sp |
| Big body / field text | `bodyLarge` | 16sp |
| Button | `labelLarge` | 15sp, W600 |
| Section label / overline | `labelMedium` | 12sp, W600, +1.2sp tracking, ALL CAPS via copy |
| Caption / hints | `labelSmall` | 11sp, W500 |

### Spacing / shape / motion

- `Spacing` object: 4, 8, 12, 16, 24, 32 dp ONLY (kills 2/10/14 strays, E2.2). Screen edge padding: 24.
- Shapes: small 10dp, medium 16dp (cards), large 24dp; buttons keep M3 pill. Cards get 1dp border
  `Color.White.copy(alpha=0.06f)` for edge definition on dark.
- Motion: screen transition = `AnimatedContent` fade+slide-up 24dp, 300ms. Reorder = `animateItem()`
  spring(MediumBouncy). Reveal stagger = 120ms/item entrance. Score count-up = `animateIntAsState`
  800ms. Confetti one-shot ≤2.5s. Timer pulse only when ≤10s.
- Haptics (`LocalHapticFeedback`): drag start = LongPress; each reorder shift = TextHandleMove;
  lock-in/ready press = LongPress. Use ONLY these two types (verify vs Phase 0 API report).

## Shared components (Phase 1 creates in `ui/components/`; Phases 2–4 may ONLY consume, never fork)

1. `GameScaffold(snackbarHostState: SnackbarHostState, modifier, scrollable: Boolean = false, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`
   — M3 `Scaffold` + gradient background + `safeDrawingPadding` + `imePadding` + horizontal 24dp padding
   + `SnackbarHost` + footer slot pinned above nav bar. Every screen uses it (fixes E1.3 root columns,
   E5.6 IME, E2.5 error surfacing).
2. `PrimaryCta(text, onClick, modifier, enabled = true, loading = false)` — filled pill, `fillMaxWidth().height(56.dp)`,
   loading → 20dp `CircularProgressIndicator` + text stays; disabled keeps 4.5:1 label.
3. `SecondaryCta(text, onClick, modifier, enabled)` — outlined pill, content color `secondary` (#A69BFF, fixes E2.4).
4. `RankBadge(rank: Int, modifier)` — 32dp rounded square, `primary` fill, white W700 number.
5. `RankCard(rank, label, modifier, trailing: (@Composable () -> Unit)? = null)` — THE single ranking-row
   card language (surfaceVariant, 16dp shape, border): used by Submit list, Reveal lists (fixes E1.3 #11).
6. `DraggableRankList(items: List<String>, onReorder: (List<String>) -> Unit, enabled: Boolean)` — rewrite of
   RankingList: IMMEDIATE `detectDragGestures` (no long-press, fixes E5.3), per-row drag handle drawn with
   a tiny `Canvas` (2 rounded lines — drop `material-icons-extended`, E1.4), thresholds from MEASURED row
   heights via `onSizeChanged` (fixes E5.4 single-position bug), `animateItem()` placement animation,
   dragged card: scale 1.03 + shadow + zIndex, haptic ticks, a11y `customActions` "Move up"/"Move down"
   per row + `stateDescription` "Ranked 2 of 5" (fixes E5.2/E5.3).
7. `CountdownPill(deadlineMs: Long?, totalMs: Long, onExpire: () -> Unit)` — compact pill (time text +
   thin linear progress), `secondary` color, switches to `error` + gentle pulse at ≤10s, clamps at 0 with
   "…" instead of frozen "0s" (fixes E2.8), `liveRegion = Polite` announce at 10s and 0.
8. `PlayerChip(name, colorSeed: String, isHost = false, dimmed = false, trailing: (@Composable () -> Unit)? = null)`
   — 32dp avatar circle (initial + palette hue) + name; ONE player representation everywhere (fixes E1.3 #7).
9. `SectionLabel(text)` — labelMedium, onSurfaceVariant, caps.
10. `InfoBanner(text)` — subtle surfaceVariant banner for persistent notes (guest waiting, non-host hints).
11. `ConfettiBurst(play: Boolean, modifier)` — Canvas particle one-shot (~60 particles, palette colors, ≤2.5s).

## ViewModel / plumbing changes (Phase 1)

Verified against current source (GameViewModel.kt 1–184, Models.kt 1–113):

- Replace `busy: Boolean` with `busyAction: BusyAction?` (`enum class BusyAction { Create, Join, Start }`)
  so each button renders its own loading state. Set on createAndJoin/joinRoom/startGame; cleared where
  `busy = false` is cleared today (onWelcome, onRoomState, error events).
- STOP putting progress text in `statusMessage` ("Creating room…" GameViewModel.kt:75, "Joining…" :95,
  "Connected" :50 — the last also overwrites visible errors). Progress = button loading state only.
  `statusMessage` becomes error/notice-only, consumed as one-shot snackbar.
- Snackbar plumbing: single `SnackbarHostState` created in `Rank5App`, passed to every screen's
  `GameScaffold`; `LaunchedEffect(state.statusMessage)` in `Rank5App`: non-null → `vm.statusShown()`
  (clears state) then `snackbarHostState.showSnackbar(msg)`.
- Round history (for Results breakdown): `RoundResult(index: Int, teamScore: Int, scores: Map<String, Int>)`.
  In `onRoomState`, when `room.phase == Phase.ROUND_REVEAL` and `room.currentRound != null` with
  index i: `history = history.filter { it.index < i } + RoundResult(i, round.teamScore, round.scores.orEmpty())`
  — replaces re-broadcasts of the same reveal AND self-resets on rematch (index restarts at 0).
  Add `roundHistory: List<RoundResult>` to UiState.
- Reveal deltas are DISPLAY-ONLY client math: for predictor p, item at position k in
  `round.predictions[p]`, delta = |k − indexOf(item in round.subjectRanking)|. Never recompute totals —
  `round.scores` / `players[].score` from server are authoritative.
- `GameViewModel.friendly(raw: String?): String` maps raw server/exception text → copy-deck messages
  (E3.3). Raw strings NEVER reach `statusMessage` anymore. Map: contains "404"/"Expected HTTP 101" →
  "That room doesn't exist. Check the code and try again."; connect/timeout exceptions → "Can't reach the
  game server."; "invalid reconnect"* → "Couldn't rejoin. Join again with the room code."; "need at least
  2 players" → "You need at least 2 players to start."; "already submitted" → "You already locked in.";
  "game already started" → "That game already started without you — ask for a new code."; else →
  "Something went wrong. Try again."
- `statusMessage` rendered as one-shot snackbar by every screen via `GameScaffold` +
  `LaunchedEffect(statusMessage)` in `Rank5App`; add `vm.statusShown()` to clear (fixes E2.5 invisible errors).
- `GameClient.disconnect()` clears `playerId`/reconnect token so "Join room" never sends a stale
  reconnect (E3.4). Read `net/GameClient.kt:52-53,171-174` first; keep reconnect behavior DURING a session.
- Round history for Results: VM appends `(roundIndex, teamScore or per-player points)` when Reveal phase
  arrives; exposed as `roundHistory: List<RoundResult>` for the Results breakdown.
- `submitEntry(auto: Boolean = false)`: when `auto`, after send set status "Time's up — your current order
  was sent." (E3.4 disclosure).
- `BackHandler` in `Rank5App`: on any screen except Home show `AlertDialog` "Leave the game?" /
  "You'll leave the room for everyone else too." confirm → `leaveToHome()` (currently back exits app, E1.5).
- Router transitions: wrap the `when` in `AnimatedContent(targetState = state.screen)`; null-state guards
  render a centered `CircularProgressIndicator` instead of blank (fixes E1.7).
- `build.gradle.kts`: remove `navigation-compose` (unreferenced) and `material-icons-extended`
  (replaced by Canvas handle) — E1.4.

## Copy deck (verbatim; plain party language — fixes E3.3)

- Home: title "Rank5"; tag "Find out what your friends really think"; field "Your name"; card A title
  "Host a game", caption "Get a room code to share", button "Create room"; card B title "Join a game",
  field "Room code", button "Join". Loading labels: "Creating…" / "Joining…".
- Lobby: SectionLabel "ROOM CODE"; hero code (displayLarge, +4sp tracking); caption "Tap to copy · share
  it with friends"; copy-confirm snackbar "Code copied"; "PLAYERS (n)"; guest banner "Waiting for {host}
  to start the game"; host sections "MODE" (segmented: "Team up" / "Compete"), one-line caption under
  selection: "Everyone's points count together." / "Highest score wins."; "DECK" chips with emoji
  (🍕 Food Favorites, 🎬 Movies & Shows, 🎭 Personality, 🤔 Would You Rather, fallback 🃏); "ROUNDS"
  segmented 3/5/6; Start button "Start game", disabled caption "Need at least 2 players — share the code!";
  guests see the same settings read-only (fixes E3.5).
- Submit: progress "ROUND {i} OF {n}"; subject: prompt + caption "Your honest order — friends are guessing
  it. #1 = your top pick."; predictor: "How will {name} rank these?" + caption "{prompt}" + caption2
  "#1 = their top pick"; direction line above list "1 = most · 5 = least" (labelSmall); first-visit hint
  chip "Drag cards to reorder" (fades permanently after first successful drag); button "Lock In" +
  caption "No changes after you lock in"; locked state: title "Locked in!", caption "Waiting for
  {k} more…" + PlayerChip row (submitted = lit, pending = dimmed).
- Reveal: title "{name}'s real order" (subject sees "Your real order"); predictions section "THE GUESSES";
  each guess card: PlayerChip + count-up "{pts} pts" + tier caption (2000 "Perfect!", ≥1800 "So close!",
  ≥1400 "Not bad", else "Way off!"); per-row delta chips: exact → "✓" (tertiary), off-by-n → "±n"
  (error color if n≥2, gold if 1); formula caption "Perfect match = 2,000 pts · every spot off costs 50";
  coop line "Team this round: {x} · Game total: {y}"; ready section "READY {k}/{n}" + PlayerChip row;
  button "Ready" → disabled "Waiting for {names}…".
- Results: coop — "Game over!", count-up "{pts} pts", caption "Team score · {n} rounds", per-round
  breakdown chips "R1 1,850 · R2 2,000 · R3 2,050"; versus — podium list (gold/silver/bronze avatars,
  winner "👑 {name}"); host button "Rematch" + caption "Same deck & settings · starts right away";
  guest banner "Only {host} can start a rematch"; secondary "Leave room".
- Errors: see `friendly()` map above.

## Per-screen structure (component trees)

- **Home** = GameScaffold(footer = none, content): brand block (title + tag, centered upper third) →
  name field (OutlinedTextField, singleLine) → "Host" card (surfaceVariant, title+caption+PrimaryCta) →
  "Join" card (code field CAPS auto-uppercase + SecondaryCta side-by-side). Create ignores code field —
  now visually separated so it's no longer a decoy (E3.4). Both buttons get loading states (E2.5).
- **Lobby** = GameScaffold(scrollable, footer = host? PrimaryCta+caption : InfoBanner): code hero card
  (tap-to-copy via `LocalClipboardManager` + haptic + snackbar) → players SectionLabel + PlayerChip flow
  (host crown ♛ suffix on chip, offline dimmed) → settings (host: interactive; guest: read-only same
  layout, `alpha 0.75f`, no click) → footer.
- **Submit** = GameScaffold(footer = PrimaryCta("Lock In")+caption / locked: none): header row
  (ROUND i OF n + CountdownPill) → prompt block → direction line → DraggableRankList(weight 1f) →
  hint chip overlay. Locked variant: check circle (tertiary), "Locked in!", waiting PlayerChips,
  static RankCard list (dimmed).
- **Reveal** = GameScaffold(scrollable, footer = PrimaryCta("Ready")): header (title + CountdownPill) →
  subject RankCard list (staggered entrance) → THE GUESSES: per-predictor card (PlayerChip + pts count-up
  + tier + 5 rows with delta chips) sorted by score → formula caption → coop score line → ready section.
- **Results** = GameScaffold(footer = host? Rematch+Leave : Leave): ConfettiBurst overlay → "Game over!" →
  coop: score count-up + breakdown chips; versus: podium list → footer. Non-host banner (E3.5 dead-end fix).

## Phases

### Phase 1 — Foundation (theme, components, plumbing)
Files: `ui/theme/Theme.kt` (+ new `Type.kt`, `Dimens.kt`), all 11 components in `ui/components/`,
`GameViewModel.kt`, `net/GameClient.kt` (playerId clear only), `ui/Rank5App.kt` (AnimatedContent, snackbar
host, BackHandler + dialog), `app/build.gradle.kts` (drop navigation-compose ONLY — icons-extended stays
until Phase 3 because old `RankingList.kt` imports `Icons.Default.DragHandle`).
Mechanical screen conversions included in Phase 1 (content INSIDE stays as-is, restyled later):
- Each screen's root `Column(fillMaxSize().padding(24.dp))` → `GameScaffold(snackbarHostState, …)`
  (Home: centered content; Lobby/Reveal: scrollable = true). Theme's double-Surface + `systemBarsPadding`
  is removed (GameScaffold owns insets); old CountdownBar keeps working until Phase 3/4.
- `?: return` null guards → render `CenteredLoading()` (new tiny component) instead of blank.
- Delete inline statusMessage Texts (`HomeScreen.kt:73-76`, `LobbyScreen.kt:99-102`) — snackbar replaces.
- UiState: add `busyAction: BusyAction?`; keep `val busy: Boolean get() = busyAction != null` as a derived
  property so untouched screens still compile.
Verification: `./gradlew :app:compileDebugKotlin` clean; `installDebug` on both emulators; drive
create→join→start→submit→reveal round on emulators (old content, new theme/scaffold); confirm back-press
shows leave dialog instead of exiting; confirm join→leave→join again works (playerId fix); invalid code
shows FRIENDLY snackbar on Home.
Anti-pattern guards: no API not in `2026-08-04-api-allowlist.md`; no `Color(0x…)` outside theme; no new dp
values outside Spacing; only LongPress/TextHandleMove haptics; `CustomAccessibilityAction` lambdas return
Boolean; MaterialTheme called with named args.

### Phase 2 — Home + Lobby
Files: `ui/HomeScreen.kt`, `ui/LobbyScreen.kt` (+ call-site tweaks in `Rank5App.kt`).
Verification (emulators): create flow with loading state visible; join flow; invalid code "ZZZZ" shows
friendly snackbar (NOT raw HTTP text); code tap copies + snackbar; guest sees read-only settings; Start
disabled caption when 1 player; IME: keyboard doesn't cover fields (imePadding); screenshots →
`DESIGN-IS-2026-08-04/screens/after/` (01-home, 02-lobby-host, 03-lobby-guest, 12-error).
Anti-pattern guards: no raw `e.message` rendering; all text via typography roles; no `.copy(alpha=` text colors.

### Phase 3 — Submit + drag interaction
Files: `ui/SubmitScreen.kt`, `ui/components/DraggableRankList.kt` (delete old `RankingList.kt`).
Verification (emulators): immediate drag (no long-press) moves a card MULTIPLE positions in one gesture
(the old bug allowed only one — E5.4); reorder animates; hint chip disappears after first drag; lock-in
→ locked state with waiting chips; let timer expire on one device → auto-submit + "Time's up" snackbar;
screenshots (04-submit-subject, 05-submit-predictor, 06-after-drag, 07-locked).
Anti-pattern guards: `detectDragGesturesAfterLongPress` must have ZERO matches in the module; no
hardcoded 64.dp threshold — grep proves `onSizeChanged`-derived heights; customActions present.

### Phase 4 — Reveal + Results
Files: `ui/RevealScreen.kt`, `ui/ResultsScreen.kt`.
Verification (emulators): full 3-round co-op game — staggered reveal, delta chips correct vs actual
order, count-up, ready gating, confetti + breakdown on results; then one 3-round VERSUS game — podium
order matches scores, rematch honesty copy, guest banner; rejection path: rematch with 1 player connected
→ friendly snackbar visible ON results (E3.4 silent-failure fix); screenshots (08-reveal, 10-ready,
11-results-coop, 11b-results-versus).
Anti-pattern guards: subject answers still hidden during submit (server snapshot handles it — do not
add client leaks); scores from server only (client never recomputes totals, only displays).

### Phase 5 — Final verification & audit regression
- Greps: 0 × `detectDragGesturesAfterLongPress`; 0 × `Color(0x` outside theme; 0 × `icons.extended`;
  0 × `navigation-compose`; 0 × `?: return` blank guards in screens; 0 × `.copy(alpha = 0.5f|0.6f|0.7f)`
  on text colors; statusMessage rendered via scaffold on ALL screens.
- Contrast: recompute every text pair from final Theme.kt hexes — all ≥4.5:1 (normal) / 3:1 (large).
- States checklist per screen: loading / error / empty / success / focus / disabled — all present.
- Full 2-emulator playthrough (create → join → 3 rounds → results → rematch → leave) with zero raw
  protocol strings on screen; final screenshot set for the summary.
- `./gradlew :app:lint` (or at minimum compileDebugKotlin) passes.

## Cutover criteria (from audit handoff)

Two-emulator live playthrough passes; all contrast pairs AA; every state reachable and rendered; the five
audit moves demonstrably addressed (map each to evidence in the final summary).
