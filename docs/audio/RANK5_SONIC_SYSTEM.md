# Rank5 sonic system

## Audio identity

**Five warm objects, one shared reveal.** Rank5 sounds like rounded cards meeting a tabletop: dry paper movement, muted wood and ceramic taps, warm marimba-like notes, and a small violet glint of glass. The system is tactile first, tonal second, and celebratory only when the social moment earns it.

The palette stays sparse enough for a room where people are speaking. There is no continuous gameplay music, no sound for ordinary navigation, and no repeating waiting loop. Every cue maps to a meaningful state change and is paired with visual, text, or haptic feedback.

### Sonic mood board

| Brand trait | Sonic translation | Avoided edge |
|---|---|---|
| Warm paper / deep ink | Close, dry paper releases; short decay; centered mono image | Hiss, foley realism, dark sub-bass |
| Violet primary | D-minor-pentatonic color: D–F–G–A–D, softened by open fifths | Bright major jingles or generic notification arpeggios |
| Pink celebration | A restrained glass partial appears only at reveals and wins | Casino sparkle, coin showers, confetti noise |
| Rounded cards | Muted wooden/ceramic transients with eased attacks | Clicky plastic, sharp rimshots, hard digital ticks |
| Tactile dragging | Paper lift plus very short positional knocks aligned with haptics | A sound on every pointer update |
| Social play | Moderate pitch register, low density, brief tails, no audio-focus grab | Speech masking, loops, cinematic impacts |

## Five-note Rank5 motif

All interpretations use the pitch contour **D5–F5–G5–A5–D6**: five distinct placements, minor-pentatonic warmth, and an open-octave arrival. Recognition comes from the contour and five-pulse rhythm, so timbre can scale with the emotional size of the moment.

1. **Paper Steps — canonical.** Warm marimba taps with a nearly subliminal card release and a small glass edge. Even 90 ms spacing makes each of the five cards legible. Used by Game Start and as the source for Perfect and Final Results. Asset: `r5_motif_alt_a_paper_steps.ogg`.
2. **Ceramic Arc.** Muted ceramic/wood bodies double the same contour. More tactile and communal, less luminous. Best alternative if device tests find the glass layer too bright. Asset: `r5_motif_alt_b_ceramic_arc.ogg`.
3. **Glass Bloom.** Overlapping glass partials let later notes reveal fragments of earlier ones. More curious and premium, but slightly less speech-transparent. Reserved as an alternate, not the default. Asset: `r5_motif_alt_c_glass_bloom.ogg`.

The shipped `r5_game_start.ogg` is the firmer canonical Paper Steps interpretation. `r5_score_perfect.ogg`, both final-result stings, the reveal family, and the ready transition inherit the same contour or its open-fifth resolution.

## Cue sheet

Priority is a playback/preemption class: **P4 phase-critical**, **P3 outcome/transition**, **P2 social status**, **P1 tactile/utility**. Priority does not imply that sound carries essential information.

| Cue / asset | Trigger | Duration | Emotional purpose | Priority |
|---|---|---:|---|---:|
| Player joins — `r5_player_join_01..03` | A new player is added after the initial lobby snapshot | 180 ms | Warm two-note welcome; makes the room feel inhabited | P2 |
| Player leaves — `r5_player_leave` | A present player leaves or is removed after disconnect timeout | 180 ms | Quiet downward information without alarm | P2 |
| Code copied/shared — `r5_code_copy` | Clipboard or share handoff succeeds | 70 ms | Tiny crisp confirmation, deliberately uncelebratory | P1 |
| Selection tick — `r5_selection_tick_01..03` | Mode, deck, or round choice changes to a valid value | 48 ms | One coherent tactile family for related controls | P1 |
| Game starts — `r5_game_start` | Lobby transitions into the first ranking phase | 650 ms | Establish the five-card identity and forward motion | P4 |
| Drag begins — `r5_drag_lift_01..03` | Long-press/drag threshold is crossed and the card lifts | 110 ms | Physical release from the stack; confirms control | P1 |
| Rank crossing — `r5_rank_cross_01..04` | Dragged card enters a different rank index | 30 ms | Positional confirmation that complements haptics | P1 |
| Lock In — `r5_lock_in_01..02` | Local submission is accepted, not merely tapped | 180 ms | Card-settle plus soft latch; confident finality | P4 |
| Remote lock — `r5_remote_lock_01..02` | Another player changes from editing to locked | 120 ms | Understated social progress | P2 |
| Countdown 3 — `r5_countdown_3` | Exactly 3 seconds remain | 95 ms | Introduce restrained urgency | P3 |
| Countdown 2 — `r5_countdown_2` | Exactly 2 seconds remain | 95 ms | Raise urgency by pitch, not level | P3 |
| Countdown 1 — `r5_countdown_1` | Exactly 1 second remains | 95 ms | Highest, still soft final prompt | P3 |
| Time's up — `r5_time_up` | Authoritative timer reaches zero | 260 ms | Soft, unmistakable closure | P4 |
| Reveal card 1–5 — `r5_reveal_card_01..05` | Each card's entrance animation begins | 160 ms each | Curiosity that rises one card at a time | P3 |
| Reveal sequence reference — `r5_reveal_sequence_reference` | Prototype/QA only, or fallback when card timing cannot emit discrete events | 760 ms | Demonstrates intended five-card synchronization | P3 |
| Score count-up — `r5_score_countup` | Numeric score animation begins | 780 ms | Delicate forward texture with a clean arrival | P3 |
| Way off — `r5_score_way_off` | Final score enters the lowest product-defined band | 260 ms | Rounded, nonjudgmental acknowledgment | P3 |
| Not bad — `r5_score_not_bad` | Final score enters the second band | 320 ms | Partial upward answer | P3 |
| So close — `r5_score_so_close` | Final score enters the third band | 400 ms | Brighter near-resolution | P3 |
| Perfect — `r5_score_perfect` | Exact ranking match | 620 ms | Memorable micro-celebration derived from all five notes | P4 |
| Round ready — `r5_round_ready_01..02` | One remote player becomes ready for the next round | 120 ms | Small affirmative social note | P2 |
| Everyone ready — `r5_everyone_ready` | The final required player becomes ready | 430 ms | Resolve individual readiness into transition | P3 |
| Final results, cooperative — `r5_final_results_coop` | Cooperative session result is shown | 1,520 ms | Five notes converge into a shared open chord | P4 |
| Final results, versus — `r5_final_results_versus` | Competitive winner result is shown | 1,560 ms | Same five-note motif, then a clear high-card crown | P4 |
| Error — `r5_error_01..02` | A user action fails and a visible message explains why | 170 ms | Rounded wooden refusal; clear, never punitive | P3 |
| Connection lost — `r5_connection_lost` | Connection crosses the product's confirmed-offline threshold | 420 ms | Suspended airy uncertainty without an alarm | P3 |
| Reconnected — `r5_reconnect_success` | State is resynchronized and interaction is safe again | 340 ms | Warm open-fifth resolution | P3 |

### Chained-event rules

- **Automatic submission:** play `r5_time_up`, then play one normal local `r5_lock_in` only when the server accepts the automatic ranking. Start the lock cue after the time-up transient, about 180–220 ms after time-up begins. The two sounds communicate separate facts.
- **Reveal:** use the five individual reveal-card files, triggered from actual animation milestones. Never also play the reference sequence. If the animation is interrupted, stop scheduling remaining notes; do not catch up in a burst.
- **Score:** start `r5_score_countup` with the 780 ms number animation. Play exactly one outcome at the landing frame, after the count-up tail. Perfect replaces, rather than layers with, any generic success cue.
- **Everyone ready:** if the last individual ready event and the all-ready transition arrive together, suppress the individual ready cue and play only `r5_everyone_ready`.
- **Reconnect:** play lost once per confirmed outage and success once after authoritative state resync. Do not sonify retry attempts.

## Repetition and variation

Use a shuffle bag, not unconstrained random choice: play every variant once in a randomized order before refilling, and do not repeat the previous file across bag boundaries.

| Family | Variants | Rotation rule |
|---|---:|---|
| Player join | 3 | Shuffle bag; reset when leaving the lobby |
| Selection tick | 3 | Shuffle bag shared by mode, deck, and round controls |
| Drag lift | 3 | Shuffle bag per local player session |
| Rank crossing | 4 | Deterministic `(gestureId + crossingCount) mod 4`; no pitch randomization at runtime |
| Lock In | 2 | Alternate; preserve the first variant for the first round |
| Remote lock | 2 | Alternate globally, not per remote player |
| Round ready | 2 | Alternate globally |
| Error | 2 | Shuffle bag, but never play more often than the error cooldown |

Do not runtime-pitch-shift the assets. Small phone speakers and Ogg transients make on-device pitch variation less consistent than rendered variants.

## Volume hierarchy

Files are category-normalized, mono, and leave at least 4.5 dBFS decoded peak headroom. Apply one user-controlled **Game sounds** bus, then these relative trims:

| Runtime bus | Cue examples | Trim from game-sounds bus |
|---|---|---:|
| Phase-critical | Game start, local lock, time's up, perfect, final result | 0 dB |
| Outcome / transition | Reveal, score movement/outcome, all-ready, error, network | −2 dB |
| Social status | Join, leave, remote lock, individual ready | −4 dB |
| Utility / tactile | Copy, selection, drag lift | −6 dB |
| Positional | Rank crossing | −8 dB |

Recommended default game-sounds bus is **70% amplitude** (approximately −3 dB). Do not add automatic gain compensation for quiet rooms. Final stings are brighter and denser, not substantially louder.

## Concurrency, cooldown, and interruption

- Maximum **3 simultaneous voices**: one P4/P3 tonal voice, one paper/gesture voice, and one social voice.
- Only one P4/P3 transition voice may be active. A new P4 stops P1 and may stop P2; it replaces an obsolete P3 state cue.
- P1 never preempts anything. Drop it if all voices are occupied; never queue micro-cues.
- Selection tick: **70 ms global cooldown**.
- Drag lift: once per gesture, **120 ms cooldown**.
- Rank crossing: **45 ms global cooldown**, only on a changed rank index, never on layout jitter; drop overlaps and cap at **8 plays per rolling second**.
- Clipboard/share: **300 ms cooldown**.
- Join/leave: **180 ms cooldown**. On snapshot hydration, play nothing. If several members arrive at once, play one join cue for the batch.
- Remote lock / ready: **160 ms cooldown**, maximum three social confirmations per second; batch extras visually.
- Error: **500 ms cooldown** per visible error category.
- Connection lost/success: once per outage cycle.
- Final result: once per session result screen; never replay on recomposition, rotation, or process state restore.

## Android playback and accessibility

- Preload the short OGG files into `SoundPool` before the lobby/game phase. Use mono playback and an `AudioAttributes` category appropriate for game sonification; do not request transient audio focus or duck speech/music for these sub-two-second cues.
- Honor the in-app **Game sounds** toggle and level. Provide a separate **Music** toggle/level, default music off; this pack intentionally contains no continuous music. Never route a sound through the alarm channel.
- Respect device silent mode: when the device ringer mode is silent or vibrate, default the Rank5 game-sounds bus to muted for that session unless the player has explicitly chosen to allow game sounds in silent mode.
- All cues duplicate visual state, text, motion, and/or haptics. No lock, timeout, network, score, or error meaning is audio-only.
- If TalkBack touch exploration is active, suppress `selection_tick`, `drag_lift`, and `rank_cross` by default; TalkBack speech and the drag haptic remain authoritative. Keep phase-critical cues at the user's selected level only if they do not overlap a spoken announcement; otherwise defer up to 250 ms or drop them when stale.
- Do not play join/leave or remote-status cues while TalkBack is speaking a newly focused label. Never duck TalkBack.
- Persist preferences locally and expose them from Settings: `Game sounds` on/off plus level, `Music` on/off plus level, and optional `Allow in silent mode` off by default.
- The same assets serve light and dark themes. Theme-specific EQ or alternate files would weaken recognition without adding useful information, so no theme variants are included.

## Asset specification and QA targets

- Format: Ogg Vorbis, mono, 48,000 Hz.
- Location: `android/app/src/main/res/raw/`.
- Naming: lowercase Android resource-safe `r5_<event>[_variant].ogg`.
- Headroom: decoded peaks from −15.0 to −4.5 dBFS; no sample clipping.
- Most micro-interface cues: 48–180 ms. The deliberately extreme positional tick is 30 ms.
- Reveal reference and score movement: 760 ms and 780 ms. Final-result stings: 1.52 s and 1.56 s.
- Total encoded pack: approximately 245 KB. All cues are original procedural synthesis with a fixed seed.
- `asset-manifest.json` records decoded peak/RMS values, duration, byte size, and SHA-256 for every export.

### Device acceptance test

Audition at 20%, 50%, and 80% media volume on at least one small mono phone speaker, one modern stereo phone, and wired/Bluetooth headphones. Pass when rank-crossing remains felt more than heard, dialogue at normal conversational distance stays intelligible, no result cue buzzes the speaker, and the five-note motif remains recognizable at 20%. Re-check Ogg start latency on the minimum supported Android version after `SoundPool` preload.

## Regeneration

From the repository root:

```sh
python3 -m pip install -r tools/audio-requirements.txt
python3 tools/generate_rank5_audio.py
```

The generator is deterministic. Re-running it replaces only the named Rank5 audio exports and refreshes the QA manifest.
