# Question Skip — Product Decision and WebSocket Implementation Plan

Date: 2026-08-09

## Outcome

Add a server-authoritative **Skip question** action that mirrors the useful parts of rank5.io:

- Only the player currently in the spotlight (the round subject) can skip.
- A skip replaces the question for the entire room immediately.
- The round index, subject, and countdown stay unchanged.
- Any rankings already submitted for the old question are invalidated and cleared.
- Each player starts a game with 8 personal skips; one player's skips do not reduce another player's allowance.
- A skip is unavailable if the subject has locked in, the allowance is exhausted, or every remaining question has already been shown in the current round.
- A skipped question is deferred into the future-round pool rather than permanently consumed. This prevents one player's skip from exhausting the next subject's replacements in small decks.
- The Go room actor remains the only authority. Android clients request a skip and react to the next personalized `room_state` broadcast.

This is **not** a vote and is **not** host-only. Predictors never receive an enabled skip control.

## What rank5.io does product-wise

Observed with a two-player co-op lobby on [rank5.io](https://rank5.io/) on 2026-08-09:

1. On a question about `CodexGuest`, only `CodexGuest` saw `skip(8)`. The predictor saw no skip action.
2. When `CodexGuest` skipped, both players moved to the same replacement question, the subject stayed `CodexGuest`, the round label stayed the same, and their counter became `skip(7)`.
3. On the next subject turn, `CodexTest` saw `skip(8)`, confirming the allowance is per player rather than shared by the party.
4. The countdown continued instead of restarting after a skip.
5. A predictor was allowed to lock in before the subject. When the subject then skipped, the predictor's `WAITING` state was cleared and the predictor could rank and lock in again for the replacement question.

Product interpretation: the subject is the only person who can judge that a personal question is unsuitable or unanswerable, but the question itself is shared room state. Therefore one authorized action replaces it for everybody.

## Current codebase gap

The current protocol supports `submit_ranking`, `ready`, and `next_round`, but has no skip command. `game.State` pre-schedules exactly `TotalRounds` questions, so it also has no replacement-question reserve. The room actor already serializes all WebSocket commands and broadcasts a personalized `room_state` after mutations; that is the right synchronization boundary to extend.

Relevant files:

- `server/internal/game/types.go`
- `server/internal/game/engine.go`
- `server/internal/game/schedule.go`
- `server/internal/ws/messages.go`
- `server/internal/ws/room.go`
- `server/internal/ws/snapshot.go`
- `android/app/src/main/java/io/rank5/app/net/Models.kt`
- `android/app/src/main/java/io/rank5/app/net/GameClient.kt`
- `android/app/src/main/java/io/rank5/app/game/GameViewModel.kt`
- `android/app/src/main/java/io/rank5/app/ui/SubmitScreen.kt`
- `android/app/src/main/java/io/rank5/app/ui/Rank5App.kt`
- `server/scripts/e2e/main.go`
- `README.md`

## Product contract

### Authorization

The server accepts a skip only when all are true:

- phase is `ROUND_SUBMIT`;
- sender is a connected room player;
- sender is `currentRound.subjectId`;
- sender has not submitted for the current question;
- payload `roundIndex` and `questionId` match the current authoritative round;
- sender has at least one personal skip remaining;
- a different question remains which has not already been shown in the current round.

Host status is irrelevant.

### Successful transition

In one room-actor transaction:

1. Consume exactly one skip from the subject.
2. Pull the next question not yet shown in the current round, and defer the replaced question to the future-round pool.
3. Keep `Round.Index`, `Round.SubjectID`, and `Round.Deadline` unchanged.
4. Replace `Round.Question`.
5. Clear `SubjectRanking`, `Predictions`, `Submitted`, `Ready`, `Scores`, and `TeamScore` for the in-progress round.
6. Broadcast a fresh, personalized `room_state` to every connected player.

Clients must treat that snapshot as authoritative. No client advances locally and no client chooses the replacement question.

### Failure behavior

Reject without mutating state or decrementing the allowance. Send the existing requester-only `error` envelope with a stable message for:

- wrong phase;
- only the subject may skip;
- subject already submitted;
- stale round/question;
- no skips remaining;
- no different question available for the current round.

A duplicate command naturally becomes stale after the first command changes `questionId`, so it cannot spend two skips.

## WebSocket contract

### Client to server

Add:

```json
{
  "type": "skip_question",
  "payload": {
    "roundIndex": 0,
    "questionId": "food:q12"
  }
}
```

Also extend `submit_ranking` so new clients bind a submission to the question they rendered:

```json
{
  "type": "submit_ranking",
  "payload": {
    "roundIndex": 0,
    "questionId": "food:q19",
    "ranking": ["A", "B", "C", "D", "E"]
  }
}
```

The server must validate these identifiers before applying the ranking. This prevents a delayed WebSocket frame for the skipped question from being accepted for its replacement, including the rare case where two questions contain the same option strings.

### Server to client

Keep `room_state` as the only success broadcast. Add per-recipient fields to `RoundView`:

```json
{
  "currentRound": {
    "index": 0,
    "subjectId": "p1",
    "question": { "id": "food:q19" },
    "submitted": {},
    "deadlineMs": 1786230000000,
    "skipsRemaining": 7,
    "canSkip": true
  }
}
```

`skipsRemaining` is the allowance of the snapshot recipient. `canSkip` is calculated by the server and is true only for the unlocked subject when a safe replacement is available. Predictors receive `canSkip: false`.

The observable success signal is: same round index + changed question ID. This works for live clients and reconnects without introducing a second event stream.

## Server implementation

### 1. Build a reusable question pool

- Refactor scheduling so game setup creates a balanced, shuffled pool from all selected decks, not only `TotalRounds` entries.
- Preserve the existing round-robin balance across decks and source-qualified question IDs.
- Keep `TotalRounds` capped by the total pool size.
- Add a cursor/helper that returns the next question not already shown in the current round.
- On skip, append the replaced question to the future-round tail. The pool therefore keeps enough questions to finish without requiring a one-time surplus.
- Track question IDs shown during the current round so repeated skips cannot cycle back to an already rejected question.

### 2. Store personal allowances

- Add `DefaultSkipsPerPlayer = 8`.
- Add server-only `SkipsRemaining map[string]int` to `game.State`.
- Initialize it for every player in `SubjectOrder` on each `StartGame`, including rematches.
- Do not persist skipped questions as finished rounds and do not change scores.

### 3. Add the engine transition

Add an engine method shaped like:

```go
func (s *State) SkipQuestion(playerID string, roundIndex int, questionID string) error
```

Keep validation and mutation in the game package. Define sentinel errors so the WebSocket layer can map them to friendly text. Preserve the existing deadline value rather than calling `beginRound`.

### 4. Wire the room actor

- Add `TypeSkipQuestion = "skip_question"` and `SkipQuestionPayload`.
- Handle it inside `Room.handleMessage`, after `requireIdentified`.
- Call `state.SkipQuestion(...)` only from the actor loop.
- On success, do **not** call `scheduleDeadline`; the existing timer and deadline remain valid. Broadcast state immediately.
- On error, use `sendError` only for the requester.
- Extend ranking handling to validate `roundIndex` and `questionId` before `SubmitEntry`.

Because all room events already pass through one actor channel, concurrent skips/submissions are deterministic. Whichever command is processed first wins; stale identifiers safely reject any command based on the previous question.

### 5. Personalize snapshots

- Add `SkipsRemaining` and `CanSkip` to `ws.RoundView`.
- Calculate them in `snapshotFor` using `viewerID`; never trust the UI to decide authorization.
- Continue sending the same new question and cleared `submitted` map to all players.
- A reconnect receives the current question and the reconnecting player's remaining allowance from the next normal snapshot.

## Android implementation

### Protocol and client

- Add `SkipQuestionPayload`, `MsgType.SKIP_QUESTION`, and `GameClient.skipQuestion(roundIndex, questionId)`.
- Add `roundIndex` and `questionId` to `RankingPayload` and send them from `submitEntry`.
- Add `skipsRemaining` and `canSkip` to `RoundView` with safe defaults.

### ViewModel

- Add `skippingQuestion: Boolean` to `UiState`.
- Add `skipQuestion()` with local guards mirroring the snapshot: submit screen, current user is subject, not submitted, `canSkip`, and not already pending.
- Set pending immediately to prevent double taps; clear it on the next `room_state` or `error`.
- Track the previous `question.id`. When a snapshot has the same round index but a different question ID:
  - rebuild `localRanking` from the new options even if the option set happens to be identical;
  - clear local submitting/locked state from the authoritative empty `submitted` map;
  - optionally show `Question skipped` to predictors.

### Compose UI

- Add an `onSkipQuestion` callback to `SubmitScreen` and wire it from `Rank5App`.
- Render a secondary `Skip (N)` action only when `amSubject && !locked && round.canSkip`.
- Keep `Lock In` as the primary action.
- Disable the skip action and show progress while `skippingQuestion` is true.
- Do not show a skip button to predictors, hosts who are not the subject, or locked subjects.
- Accessibility label: `Skip question, N remaining`.

## Test plan

### Game engine unit tests

- subject skip succeeds and decrements only that subject from 8 to 7;
- non-subject and host-who-is-not-subject are rejected;
- wrong phase, locked subject, zero allowance, stale round/question, and no different current-round question are rejected without mutation;
- skip changes question ID but preserves index, subject, phase, and exact deadline;
- skip clears every prior submission/prediction/ranking/score for the current question;
- multiple subjects have independent allowances;
- replacement questions are never repeated within the same round;
- deferring skipped questions always leaves enough questions to finish `TotalRounds`;
- repeated skips never show the same question twice within one round;
- in a five-round game using a six-question deck, player A skipping does not remove player B's skip on the next turn;
- rematch resets allowances.

### WebSocket/room tests

- two connected clients receive the same replacement question after the subject skips;
- only the subject's snapshot has `canSkip: true`;
- a predictor who submitted before the skip receives `submitted: {}` and can submit again;
- a duplicated old skip frame returns stale-question error and consumes no extra allowance;
- a delayed ranking for the old question is rejected;
- reconnect returns current question and personal remaining count;
- simultaneous skip/submit ordering remains consistent through the actor.

### Android tests

- button visibility follows subject/locked/canSkip state;
- double tap sends one command while pending;
- question-ID change resets local ranking and pending state;
- error restores the button and shows friendly copy;
- predictor locked state reopens after a room snapshot clears submission.

### End-to-end acceptance

With two clients in co-op:

1. Player B is subject; only B sees `Skip (8)`.
2. Player A locks in; B skips.
3. Both clients show the same new question, remain on the same round and subject, and keep the original countdown.
4. A is unlocked and can rank again; B sees `Skip (7)`.
5. When A later becomes subject, A sees `Skip (8)`.
6. Refresh/reconnect either client and verify state and counters remain authoritative.

Run at minimum:

```bash
cd server && go test ./...
cd server && go run ./scripts/e2e/
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Delivery sequence

1. Add failing engine and WebSocket tests for the contract.
2. Refactor scheduling into a reusable full pool while preserving existing tests.
3. Implement state allowances and the atomic skip transition.
4. Add WebSocket payloads, stale-question validation, snapshot fields, and broadcast behavior.
5. Update Android models/client/ViewModel.
6. Add the subject-only Compose control and reset behavior.
7. Extend e2e coverage, update README protocol docs, and run all verification commands.

## Copy-ready build prompt

```text
Implement subject-only question skipping in this Rank5 repository.

Match the verified rank5.io product behavior:
- Only the current round subject can skip; it is not host-only and not a party vote.
- Every player starts each game/rematch with 8 personal skips.
- A successful skip replaces the current question for the whole room, decrements only the subject's allowance, preserves the current round index, subject, ROUND_SUBMIT phase, and exact deadline, and clears every submission/ranking/prediction for the replaced question so already-locked predictors can answer again.
- Do not allow skipping after the subject has submitted, with zero allowance, in another phase, or when every remaining question was already shown in the current round.
- Completed questions must not repeat. A skipped question may be deferred to a future round, but must not cycle back within the round where it was rejected.

Make the Go room actor/server authoritative and synchronize through WebSockets:
1. Add client message `skip_question` with payload `{ roundIndex, questionId }`.
2. Extend `submit_ranking` payload with `{ roundIndex, questionId, ranking }` and reject stale frames. Update every client and e2e sender accordingly.
3. On success, broadcast the existing personalized `room_state` to all connected clients. Add per-recipient `currentRound.skipsRemaining` and `currentRound.canSkip`; only the eligible subject gets `canSkip=true`.
4. Do not add a client-side question picker or optimistic round transition. Disable the skip button while awaiting the authoritative snapshot. Duplicate/stale commands must not spend another skip.
5. Preserve the original deadline/timer on skip; do not reschedule it.

Server changes:
- Refactor `server/internal/game/schedule.go` so StartGame receives a balanced shuffled pool containing all questions from the selected decks, rather than only TotalRounds questions.
- Defer each skipped question to the future-round tail and track IDs already shown in the current round.
- Add server-only personal skip counts and a question-pool cursor to game state.
- Add an atomic `SkipQuestion(playerID, roundIndex, questionID)` engine transition with sentinel errors.
- Wire it in `server/internal/ws/messages.go`, `room.go`, and personalized fields in `snapshot.go`.
- Ensure a replacement is offered only when the remaining pool can still cover all future configured rounds.

Android changes:
- Update `Models.kt`, `GameClient.kt`, `GameViewModel.kt`, `SubmitScreen.kt`, and `Rank5App.kt`.
- Show a secondary `Skip (N)` control only to the unlocked subject when the server says `canSkip`.
- Track question ID in ViewModel state so a same-round question replacement always resets the local ranking, including when two questions happen to have identical option sets.
- If a predictor was already locked, the cleared authoritative `submitted` map must return them to the editable state.
- Add friendly error handling and accessibility text `Skip question, N remaining`.

Tests are required before considering the work complete:
- engine authorization, independent quotas, stale IDs, unchanged deadline/index/subject, clearing partial submissions, same-round exhaustion/no repeats, next-subject availability, and rematch reset;
- two-client WebSocket synchronization, duplicate/delayed frames, reconnect snapshots, and actor ordering;
- Android visibility, pending/double-tap protection, question reset, and error recovery;
- extend `server/scripts/e2e/main.go` to cover a predictor locking before a subject skip.

Update README WebSocket documentation. Run `cd server && go test ./...`, the server e2e flow against a local server, and `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug`. Do not stop at code generation: fix failures and report the exact verification results.
```
