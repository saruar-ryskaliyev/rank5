package io.rank5.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.rank5.app.game.UiState
import io.rank5.app.game.canSkipCurrentQuestion
import io.rank5.app.net.canonicalSelectedDecks
import io.rank5.app.ui.components.CenteredLoading
import io.rank5.app.ui.components.CountdownPill
import io.rank5.app.ui.components.DraggableRankList
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PlayerChip
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.components.SecondaryCta
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

private const val SUBMIT_TOTAL_MS = 60_000L

@Composable
fun SubmitScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onReorder: (List<String>) -> Unit,
    onLockIn: () -> Unit,
    onSkipQuestion: () -> Unit,
    onAutoSubmit: () -> Unit,
    onDragStart: () -> Unit = {},
    onRankCross: () -> Unit = {},
    onCountdown: (Int) -> Unit = {},
    onTimeUp: () -> Unit = {},
) {
    val room = state.room
    val round = room?.currentRound
    if (room == null || round == null) {
        GameScaffold(snackbarHostState = snackbarHostState) { CenteredLoading() }
        return
    }
    val amSubject = room.youAre == round.subjectId
    val subjectName = room.players.find { it.id == round.subjectId }?.nickname ?: "Player"
    val locked = round.submitted[room.youAre] == true
    var hintDismissed by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    GameScaffold(
        snackbarHostState = snackbarHostState,
        footer = if (locked) {
            null
        } else {
            {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (canSkipCurrentQuestion(state) || state.skippingQuestion) {
                        SecondaryCta(
                            text = "Skip (${round.skipsRemaining})",
                            onClick = onSkipQuestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription =
                                        "Skip question, ${round.skipsRemaining} remaining"
                                },
                            enabled = canSkipCurrentQuestion(state),
                            loading = state.skippingQuestion,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    PrimaryCta(
                        text = "Lock In",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLockIn()
                        },
                        loading = state.submitting,
                        enabled = state.localRanking.size == 5 && !state.skippingQuestion,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "No changes after you lock in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("ROUND ${round.index + 1} OF ${room.totalRounds}")
            CountdownPill(
                deadlineMs = round.deadlineMs,
                totalMs = SUBMIT_TOTAL_MS,
                onExpire = onAutoSubmit,
                onFinalSecond = onCountdown,
                onTimeUp = onTimeUp,
            )
        }
        Spacer(Modifier.height(Spacing.lg))

        SourceDeckLabel(
            room.canonicalSelectedDecks().firstOrNull { it.id == round.question.deckId }
                ?: room.canonicalSelectedDecks().singleOrNull(),
        )
        Spacer(Modifier.height(Spacing.sm))

        if (locked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LockedCheckCircle()
                Spacer(Modifier.height(Spacing.sm))
                Text("Locked in!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Spacing.xs))
                val pending = room.players.count {
                    it.connected && round.submitted[it.id] != true
                }
                Text(
                    text = "Waiting for $pending more…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    room.players.filter { it.connected }.forEach { p ->
                        PlayerChip(
                            name = p.nickname,
                            colorSeed = p.id,
                            dimmed = round.submitted[p.id] != true,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            DraggableRankList(
                items = state.localRanking.ifEmpty { round.myRanking.orEmpty() },
                onReorder = {},
                enabled = false,
                modifier = Modifier.weight(1f),
            )
        } else {
            if (amSubject) {
                Text(round.question.prompt, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Your honest order — friends are guessing it. #1 = your top pick.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "How will $subjectName rank these?",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = round.question.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "#1 = their top pick",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "1 = most · 5 = least",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            // One-time affordance hint; collapses forever after the first
            // successful reorder (session-scoped is enough per plan).
            AnimatedVisibility(
                visible = !hintDismissed,
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = "Drag cards to reorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = Spacing.xs,
                            ),
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
            DraggableRankList(
                items = state.localRanking,
                onReorder = {
                    hintDismissed = true
                    onReorder(it)
                },
                enabled = true,
                modifier = Modifier.weight(1f),
                onDragStart = onDragStart,
                onRankCross = onRankCross,
            )
        }
    }
}

/** Filled tertiary circle with a check mark — success state without an icon dependency. */
@Composable
private fun LockedCheckCircle(modifier: Modifier = Modifier) {
    val circle = MaterialTheme.colorScheme.tertiary
    val mark = MaterialTheme.colorScheme.onTertiary
    Canvas(modifier = modifier.size(Sizes.lockCheck)) {
        drawCircle(color = circle)
        val stroke = Sizes.lockCheckStroke.toPx()
        val start = Offset(size.width * 0.30f, size.height * 0.52f)
        val mid = Offset(size.width * 0.44f, size.height * 0.66f)
        val end = Offset(size.width * 0.70f, size.height * 0.38f)
        drawLine(color = mark, start = start, end = mid, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = mark, start = mid, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
