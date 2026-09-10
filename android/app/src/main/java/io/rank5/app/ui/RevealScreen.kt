package io.rank5.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.rank5.app.game.UiState
import io.rank5.app.net.PlayerView
import io.rank5.app.net.canonicalSelectedDecks
import io.rank5.app.ui.components.CenteredLoading
import io.rank5.app.ui.components.CountdownPill
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PlayerChip
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.RankCard
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.components.StaggeredAppear
import io.rank5.app.ui.theme.LocalRank5Extras
import io.rank5.app.ui.theme.Motion
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private const val REVEAL_TOTAL_MS = 20_000L
private const val STAGGER_MS = Motion.staggerMs
private const val STAGGER_ENTER_MS = 360
private const val COUNT_UP_MS = Motion.countUpMs

/** Keeps the disabled Ready label from ballooning with many long nicknames. */
private const val MAX_WAITING_NAMES_LENGTH = 24

private fun formatPts(n: Int): String = String.format(Locale.US, "%,d", n)

@Composable
fun RevealScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onReady: () -> Unit,
    onRevealCard: (Int) -> Unit = {},
    onScoreCountUp: () -> Unit = {},
    onScoreOutcome: (Int) -> Unit = {},
) {
    val room = state.room
    val round = room?.currentRound
    if (room == null || round == null) {
        GameScaffold(snackbarHostState = snackbarHostState) { CenteredLoading() }
        return
    }

    fun nameOf(pid: String): String =
        room.players.find { it.id == pid }?.nickname ?: "Player"

    val amSubject = room.youAre == round.subjectId
    val iAmReady = round.ready[room.youAre] == true
    val connected = room.players.filter { it.connected }
    val readyCount = connected.count { round.ready[it.id] == true }
    val haptics = LocalHapticFeedback.current

    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            val waitingNames = connected
                .filter { round.ready[it.id] != true }
                .joinToString(", ") { it.nickname }
            val waitingLabel = if (waitingNames.isEmpty() || waitingNames.length > MAX_WAITING_NAMES_LENGTH) {
                "Waiting for others…"
            } else {
                "Waiting for $waitingNames…"
            }
            PrimaryCta(
                text = if (iAmReady) waitingLabel else "Ready",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReady()
                },
                enabled = !iAmReady,
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    round.question.usesPlayersAsOptions && amSubject -> "Your call"
                    round.question.usesPlayersAsOptions -> "${nameOf(round.subjectId)}'s call"
                    amSubject -> "Your real order"
                    else -> "${nameOf(round.subjectId)}'s real order"
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.md))
            CountdownPill(
                deadlineMs = round.deadlineMs,
                totalMs = REVEAL_TOTAL_MS,
                onExpire = { /* server advances */ },
            )
        }
        Spacer(Modifier.height(Spacing.md))

        SourceDeckLabel(
            room.canonicalSelectedDecks().firstOrNull { it.id == round.question.deckId }
                ?: room.canonicalSelectedDecks().singleOrNull(),
        )
        Spacer(Modifier.height(Spacing.sm))

        val subjectRanking = round.subjectRanking.orEmpty()
        val localScore = round.scores?.get(room.youAre)
        // Saved across rotation so a mid-reveal config change never replays the cues.
        var revealAudioStarted by rememberSaveable(round.index, round.question.id) { mutableStateOf(false) }
        var scoreAudioStarted by rememberSaveable(round.index, round.question.id) { mutableStateOf(false) }
        val reducedMotion = Motion.reducedMotion()
        val expectedOptions = round.question.options.size
        LaunchedEffect(subjectRanking) {
            if (revealAudioStarted || subjectRanking.size != expectedOptions) return@LaunchedEffect
            revealAudioStarted = true
            subjectRanking.indices.forEachIndexed { position, cardIndex ->
                onRevealCard(cardIndex)
                if (!reducedMotion && position < subjectRanking.lastIndex) delay(STAGGER_MS.toLong())
            }
        }
        LaunchedEffect(subjectRanking, localScore) {
            if (scoreAudioStarted || subjectRanking.size != expectedOptions || localScore == null) {
                return@LaunchedEffect
            }
            scoreAudioStarted = true
            if (!reducedMotion) {
                delay((subjectRanking.lastIndex * STAGGER_MS + STAGGER_ENTER_MS).toLong())
            }
            onScoreCountUp()
            if (!reducedMotion) delay(COUNT_UP_MS.toLong())
            onScoreOutcome(localScore)
        }
        StaggeredRankList(items = subjectRanking, roundKey = round.index)

        Spacer(Modifier.height(Spacing.md))
        SectionLabel("THE GUESSES")
        Spacer(Modifier.height(Spacing.sm))

        val predictions = round.predictions.orEmpty()
        if (predictions.isEmpty()) {
            Text(
                text = "Nobody guessed this round",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val sorted = predictions.entries.sortedByDescending { (pid, _) ->
                round.scores?.get(pid) ?: 0
            }
            // Guess cards land one beat apart once the subject's list has finished.
            sorted.forEachIndexed { position, (pid, guess) ->
                key(round.index, pid) {
                    StaggeredAppear(
                        order = subjectRanking.size + position,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GuessCard(
                            predictor = room.players.find { it.id == pid },
                            predictorId = pid,
                            guess = guess,
                            subjectRanking = subjectRanking,
                            points = round.scores?.get(pid) ?: 0,
                            roundKey = round.index,
                            modifier = Modifier.padding(bottom = Spacing.md),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = scoringLegend(expectedOptions),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Team this round: ${formatPts(round.teamScore)} · " +
                "Game total: ${formatPts(room.teamScore)}",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(Spacing.md))
        SectionLabel("READY $readyCount/${connected.size}")
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            connected.forEach { p ->
                PlayerChip(
                    name = p.nickname,
                    colorSeed = p.id,
                    dimmed = round.ready[p.id] != true,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

/** Subject's real order, cards entering one by one (one stagger beat apart). */
@Composable
private fun StaggeredRankList(items: List<String>, roundKey: Int) {
    // Under reduced motion the list is simply there; otherwise it cascades in.
    var entered by remember(roundKey) { mutableStateOf(Motion.reducedMotion()) }
    LaunchedEffect(roundKey) { entered = true }
    items.forEachIndexed { i, label ->
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(STAGGER_ENTER_MS, delayMillis = i * STAGGER_MS)) +
                slideInVertically(tween(STAGGER_ENTER_MS, delayMillis = i * STAGGER_MS)) { it / 3 },
        ) {
            RankCard(
                rank = i + 1,
                label = label,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
}

@Composable
private fun GuessCard(
    predictor: PlayerView?,
    predictorId: String,
    guess: List<String>,
    subjectRanking: List<String>,
    points: Int,
    roundKey: Int,
    modifier: Modifier = Modifier,
) {
    val tier = when {
        points >= 2000 -> "Perfect!"
        points >= 1800 -> "So close!"
        points >= 1400 -> "Not bad"
        else -> "Way off!"
    }
    // Count up from 0 on entry; keyed on the round so a rematch re-animates.
    var target by remember(roundKey) { mutableIntStateOf(0) }
    LaunchedEffect(roundKey, points) { target = points }
    val shownPts by animateIntAsState(
        targetValue = target,
        animationSpec = tween(COUNT_UP_MS),
        label = "scoreCountUp",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerChip(
                    name = predictor?.nickname ?: "Player",
                    colorSeed = predictorId,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${formatPts(shownPts)} of 2,000 points",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalRank5Extras.current.accentText,
                    )
                    Text(
                        text = tier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            guess.forEachIndexed { position, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${position + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    val actual = subjectRanking.indexOf(item)
                    if (actual >= 0) {
                        DeltaChip(delta = abs(position - actual))
                    }
                }
            }
        }
    }
}

/** Display-only delta vs the subject's order — never feeds score math. */
@Composable
private fun DeltaChip(delta: Int, modifier: Modifier = Modifier) {
    val exact = delta == 0
    val color = when {
        exact -> MaterialTheme.colorScheme.onTertiaryContainer
        delta == 1 -> LocalRank5Extras.current.highlightText
        else -> MaterialTheme.colorScheme.error
    }
    // Exact hits get a filled container so they read at a glance down the card.
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (exact) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = when (delta) {
                0 -> "Exact"
                1 -> "1 place off"
                else -> "$delta places off"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
