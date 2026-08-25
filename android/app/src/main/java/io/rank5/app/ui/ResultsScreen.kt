package io.rank5.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.rank5.app.game.BusyAction
import io.rank5.app.game.UiState
import io.rank5.app.net.PlayerView
import io.rank5.app.net.canonicalSelectedDecks
import androidx.compose.ui.res.stringResource
import io.rank5.app.R
import io.rank5.app.stats.ClaimStatus
import io.rank5.app.ui.components.CenteredLoading
import io.rank5.app.ui.components.ConfettiBurst
import io.rank5.app.ui.components.DeckIconTile
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.InfoBanner
import io.rank5.app.ui.components.PlayerChip
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.RankBadge
import io.rank5.app.ui.components.SecondaryCta
import io.rank5.app.ui.theme.LocalRank5Extras
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import java.util.Locale

private const val COUNT_UP_MS = 800

private fun formatPts(n: Int): String = String.format(Locale.US, "%,d", n)

@Composable
fun ResultsScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onPlayAgain: () -> Unit,
    onLeave: () -> Unit,
    signedIn: Boolean = true,
    signInBusy: Boolean = false,
    claimStatus: ClaimStatus = ClaimStatus.Idle,
    onSaveStats: () -> Unit = {},
    onRetryClaim: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    val room = state.room
    if (room == null) {
        GameScaffold(snackbarHostState = snackbarHostState) { CenteredLoading() }
        return
    }
    val hostName = room.players.find { it.isHost }?.nickname ?: "the host"

    GameScaffold(
        snackbarHostState = snackbarHostState,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.isHost) {
                    PrimaryCta(
                        text = "Rematch",
                        onClick = onPlayAgain,
                        loading = state.busyAction == BusyAction.Start,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = if (room.canonicalSelectedDecks().size > 1) {
                            stringResource(R.string.same_deck_mix_rematch)
                        } else {
                            "Same deck & settings · starts right away"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    InfoBanner("Only $hostName can start a rematch")
                }
                Spacer(Modifier.height(Spacing.md))
                SecondaryCta(
                    text = "Leave room",
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.md))
                val winner = room.players.maxByOrNull { it.score }
                Text(
                    if (room.mode == "coop") "That’s the game" else "${winner?.nickname ?: "Winner"} takes it",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                if (room.mode == "coop") {
                    CoopResults(state = state)
                } else {
                    VersusPodium(players = room.players)
                }
                if (state.roundHistory.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    DeckMixSummary(room.canonicalSelectedDecks())
                    Spacer(Modifier.height(Spacing.md))
                    RoundBreakdown(state)
                }
                Spacer(Modifier.height(Spacing.md))
                ShareResultCard(
                    data = ResultCardData.from(state),
                    onShare = onShare,
                )
                if (!signedIn) {
                    Spacer(Modifier.height(Spacing.md))
                    SaveStatsPrompt(
                        loading = signInBusy,
                        onSave = onSaveStats,
                    )
                } else {
                    when (claimStatus) {
                        ClaimStatus.Idle -> Unit
                        ClaimStatus.Claiming -> {
                            Spacer(Modifier.height(Spacing.md))
                            InfoBanner("Saving this game to your profile…")
                        }
                        is ClaimStatus.Saved -> {
                            Spacer(Modifier.height(Spacing.md))
                            InfoBanner(
                                if (claimStatus.games == 1) {
                                    "Game saved to your profile"
                                } else {
                                    "${claimStatus.games} games saved to your profile"
                                },
                            )
                        }
                        is ClaimStatus.Failed -> {
                            Spacer(Modifier.height(Spacing.md))
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Text(
                                        claimStatus.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(Modifier.height(Spacing.sm))
                                    SecondaryCta(
                                        text = "Retry saving",
                                        onClick = onRetryClaim,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }
            ConfettiBurst(play = true, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ShareResultCard(data: ResultCardData, onShare: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("SHARE YOUR RESULT", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeckIconTile(data.deckEmoji, data.deckName)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    if (data.mode == "coop") "${data.deckName} · ${formatPts(data.teamScore)} team points"
                    else "${data.deckName} · ${data.standings.firstOrNull()?.nickname ?: "Winner"} takes first",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            SecondaryCta(
                text = "Share result",
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RoundBreakdown(state: UiState) {
    val room = state.room ?: return
    Column(Modifier.fillMaxWidth()) {
        Text("Round breakdown", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.sm))
        state.roundHistory.sortedBy { it.index }.forEach { round ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RankBadge(round.index + 1)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text("Round ${round.index + 1}", style = MaterialTheme.typography.bodyLarge)
                    if (room.canonicalSelectedDecks().size > 1) {
                        val source = room.canonicalSelectedDecks().firstOrNull { it.id == round.deckId }
                        if (source != null) {
                            Text(
                                source.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    if (room.mode == "coop") formatPts(round.teamScore)
                    else formatPts(round.scores.values.maxOrNull() ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun DeckMixSummary(decks: List<io.rank5.app.net.DeckInfo>) {
    if (decks.size <= 1) return
    Column(Modifier.fillMaxWidth()) {
        Text("${decks.size}-deck mix", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            decks.joinToString(" · ") { "${it.emoji} ${it.name}" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveStatsPrompt(loading: Boolean, onSave: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                "Save your stats",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Keep this game and start building your Rank5 history. Playing never requires an account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(Spacing.md))
            PrimaryCta(
                text = if (loading) "Signing in…" else "Save this game",
                onClick = onSave,
                enabled = !loading,
                loading = loading,
            )
        }
    }
}

@Composable
private fun CoopResults(state: UiState) {
    val room = state.room ?: return
    // Count up from 0 on entry.
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(room.teamScore) { target = room.teamScore }
    val shown by animateIntAsState(
        targetValue = target,
        animationSpec = tween(COUNT_UP_MS),
        label = "teamScoreCountUp",
    )

    Text(
        text = "${formatPts(shown)} pts",
        style = MaterialTheme.typography.displayLarge,
        color = LocalRank5Extras.current.accentText,
    )
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = "Team score · ${room.totalRounds} rounds",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.roundHistory.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.roundHistory.sortedBy { it.index }.forEach { r ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = "R${r.index + 1} ${formatPts(r.teamScore)}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(
                            horizontal = Spacing.md,
                            vertical = Spacing.xs,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VersusPodium(players: List<PlayerView>) {
    val extras = LocalRank5Extras.current
    val standings = players.sortedByDescending { it.score }
    standings.forEachIndexed { i, p ->
        val winner = i == 0
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
            shape = MaterialTheme.shapes.medium,
            color = if (winner) extras.highlight else MaterialTheme.colorScheme.surface,
            border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RankBadge(rank = i + 1)
                Spacer(Modifier.width(Spacing.md))
                PlayerChip(
                    name = p.nickname,
                    colorSeed = p.id,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = "${formatPts(p.score)} pts",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (winner) extras.onHighlight else extras.accentText,
                )
            }
        }
    }
}
