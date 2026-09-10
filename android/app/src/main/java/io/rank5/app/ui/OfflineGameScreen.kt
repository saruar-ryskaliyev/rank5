package io.rank5.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import io.rank5.app.offline.MaxOfflinePlayers
import io.rank5.app.offline.MinOfflinePlayers
import io.rank5.app.offline.OfflineGameState
import io.rank5.app.offline.OfflinePlayer
import io.rank5.app.offline.OfflineRoundResult
import io.rank5.app.offline.OfflineScreen
import io.rank5.app.offline.offlineAllowedRoundChoices
import io.rank5.app.offline.offlinePlayerQuestionWarning
import io.rank5.app.offline.offlinePlayerValidation
import io.rank5.app.ui.components.DeckIconTile
import io.rank5.app.ui.components.DraggableRankList
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.InfoBanner
import io.rank5.app.ui.components.PlayerChip
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.Rank5TopBar
import io.rank5.app.ui.components.RankBadge
import io.rank5.app.ui.components.RankCard
import io.rank5.app.ui.components.SecondaryCta
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.theme.LocalRank5Extras
import io.rank5.app.ui.theme.Motion
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import java.util.Locale

@Composable
fun OfflineGameFlow(
    state: OfflineGameState,
    snackbarHostState: SnackbarHostState,
    onPlayerName: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onToggleDeck: (String) -> Unit,
    onBrowseDecks: () -> Unit,
    onSelectRounds: (Int) -> Unit,
    onStart: () -> Unit,
    onRevealToActor: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onLockIn: () -> Unit,
    onNextRound: () -> Unit,
    onRematch: () -> Unit,
    onExit: () -> Unit,
    onDragStart: () -> Unit = {},
    onRankCross: () -> Unit = {},
) {
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            if (initialState == OfflineScreen.Ranking && targetState == OfflineScreen.Handoff) {
                // Remove the private ranking before animating the handoff cover in.
                fadeIn(tween(Motion.fastMillis)) togetherWith fadeOut(tween(0))
            } else {
                (slideInVertically(tween(Motion.standardMillis)) { it / 12 } +
                    fadeIn(tween(Motion.standardMillis))) togetherWith
                    fadeOut(tween(Motion.fastMillis))
            }
        },
        label = "offline game screen",
    ) { screen ->
        when (screen) {
            OfflineScreen.Setup -> OfflineSetupScreen(
                state, snackbarHostState, onPlayerName, onAddPlayer, onRemovePlayer,
                onToggleDeck, onBrowseDecks, onSelectRounds, onStart, onExit,
            )
            OfflineScreen.Handoff -> OfflineHandoffScreen(
                state, snackbarHostState, onRevealToActor, onExit,
            )
            OfflineScreen.Ranking -> OfflineRankingScreen(
                state, snackbarHostState, onReorder, onLockIn, onExit,
                onDragStart, onRankCross,
            )
            OfflineScreen.Reveal -> OfflineRevealScreen(
                state, snackbarHostState, onNextRound, onExit,
            )
            OfflineScreen.Results -> OfflineResultsScreen(
                state, snackbarHostState, onRematch, onExit,
            )
        }
    }
}

@Composable
private fun OfflineSetupScreen(
    state: OfflineGameState,
    snackbarHostState: SnackbarHostState,
    onPlayerName: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onToggleDeck: (String) -> Unit,
    onBrowseDecks: () -> Unit,
    onSelectRounds: (Int) -> Unit,
    onStart: () -> Unit,
    onExit: () -> Unit,
) {
    val validation = offlinePlayerValidation(state.playerNames)
    val playerQuestionWarning = offlinePlayerQuestionWarning(state)
    val hasPlayableQuestions = state.availableQuestionCount > 0
    val canStart = validation == null &&
        state.decks.any { it.id in state.selectedDeckIds } &&
        hasPlayableQuestions
    val focus = LocalFocusManager.current
    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            Text(
                when {
                    canStart -> "${state.playerNames.size} players · ${state.selectedRounds} rounds · all set!"
                    validation != null -> validation
                    !hasPlayableQuestions && playerQuestionWarning != null -> playerQuestionWarning
                    else -> "Choose a deck to get started."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            )
            PrimaryCta(
                text = "Start Pass & Play",
                onClick = { focus.clearFocus(); onStart() },
                enabled = canStart,
            )
        },
    ) {
        Rank5TopBar(title = "Pass & Play", onBack = onExit)
        Spacer(Modifier.height(Spacing.md))
        Text("Bring everyone in.", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "A few names, a favorite topic, and you’re ready.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(Sizes.metadataIcon),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(Spacing.sm))
            Text("Private turns. No internet needed.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Spacing.lg))
        SetupSection("1", "Who’s playing?", "${state.playerNames.size}/$MaxOfflinePlayers")
        Spacer(Modifier.height(Spacing.sm))
        state.playerNames.forEachIndexed { index, name ->
            OutlinedTextField(
                value = name,
                onValueChange = { onPlayerName(index, it) },
                label = { Text("Player ${index + 1} name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                leadingIcon = {
                    Text("${index + 1}", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = if (index == state.playerNames.lastIndex) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                trailingIcon = if (state.playerNames.size > MinOfflinePlayers) {
                    {
                        IconButton(onClick = { onRemovePlayer(index) }) {
                            Icon(Icons.Rounded.PersonRemove, contentDescription = "Remove player ${index + 1}")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            )
        }
        androidx.compose.material3.TextButton(
            onClick = onAddPlayer,
            enabled = state.playerNames.size < MaxOfflinePlayers,
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(Sizes.metadataIcon))
            Spacer(Modifier.size(Spacing.sm))
            Text("Add player")
        }
        if (validation != null && state.playerNames.all(String::isNotBlank)) {
            Text(validation, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Spacing.md))
        SetupSection("2", "How many rounds?")
        Spacer(Modifier.height(Spacing.md))
        val roundChoices = offlineAllowedRoundChoices(state.availableQuestionCount)
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            roundChoices.forEachIndexed { index, count ->
                SegmentedButton(
                    selected = state.selectedRounds == count,
                    onClick = { onSelectRounds(count) },
                    shape = SegmentedButtonDefaults.itemShape(index, roundChoices.size),
                    modifier = Modifier.heightIn(min = Sizes.touchTarget)
                        .semantics { contentDescription = "$count rounds" },
                ) { Text(count.toString()) }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SetupSection("3", "Pick your decks", "${state.selectedDeckIds.size} selected")
        Spacer(Modifier.height(Spacing.sm))
        Text("Mix a few topics to keep everyone guessing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        state.decks.forEach { deck ->
            val selected = deck.id in state.selectedDeckIds
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                border = BorderStroke(Sizes.hairline, if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.toggleable(
                        value = selected, role = Role.Checkbox,
                        onValueChange = { onToggleDeck(deck.id) },
                    ).padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeckIconTile(deck.emoji, deck.title)
                    Spacer(Modifier.size(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(deck.title, style = MaterialTheme.typography.titleMedium)
                        Text("${deck.questions.size} questions", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked = selected, onCheckedChange = null)
                }
            }
        }
        if (playerQuestionWarning != null) {
            InfoBanner(playerQuestionWarning)
            Spacer(Modifier.height(Spacing.sm))
        }
        SecondaryCta(
            text = "Browse more decks",
            onClick = onBrowseDecks,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun SetupSection(number: String, title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(number, Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() })
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OfflineHandoffScreen(
    state: OfflineGameState,
    snackbarHostState: SnackbarHostState,
    onRevealToActor: () -> Unit,
    onExit: () -> Unit,
) {
    val actor = state.actor ?: return
    val subject = state.subject ?: return
    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            PrimaryCta(
                text = "I’m ${actor.name}",
                onClick = onRevealToActor,
                modifier = Modifier.semantics {
                    contentDescription = "I am ${actor.name}. Show my private turn."
                },
            )
        },
    ) {
        Rank5TopBar(
            title = "Round ${state.roundIndex + 1} of ${state.schedule.size}",
            onBack = onExit,
        )
        Spacer(Modifier.height(Spacing.lg))
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
        Icon(
            Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(Spacing.lg).size(Sizes.deckHero),
        )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Pass the phone to\n${actor.name}",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            when {
                state.actorIsSubject && state.currentQuestion?.usesPlayersAsOptions == true ->
                    "${actor.name}, you’ll secretly rank the group."
                state.actorIsSubject ->
                    "${actor.name}, you’ll secretly rank your real order."
                state.currentQuestion?.usesPlayersAsOptions == true ->
                    "${actor.name}, guess how ${subject.name} ranked everyone."
                else ->
                    "${actor.name}, guess how ${subject.name} ranked the five."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Spacing.lg))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null)
                Spacer(Modifier.size(Spacing.md))
                Text(
                    "Everyone else: look away until ${actor.name} locks in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun OfflineRankingScreen(
    state: OfflineGameState,
    snackbarHostState: SnackbarHostState,
    onReorder: (List<String>) -> Unit,
    onLockIn: () -> Unit,
    onExit: () -> Unit,
    onDragStart: () -> Unit,
    onRankCross: () -> Unit,
) {
    val actor = state.actor ?: return
    val subject = state.subject ?: return
    val question = state.currentQuestion ?: return
    GameScaffold(
        snackbarHostState = snackbarHostState,
        footer = {
            PrimaryCta(
                text = "Lock in & hide",
                onClick = onLockIn,
                enabled = state.localRanking.size == question.options.size,
            )
        },
    ) {
        Rank5TopBar(
            title = "Round ${state.roundIndex + 1} of ${state.schedule.size}",
            onBack = onExit,
        )
        LinearProgressIndicator(
            progress = { (state.actorIndex + 1).toFloat() / state.actors.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        )
        PlayerChip(name = actor.name, colorSeed = actor.id)
        Spacer(Modifier.height(Spacing.md))
        val rankingPlayers = question.usesPlayersAsOptions
        Text(
            when {
                state.actorIsSubject && rankingPlayers -> "Your turn to call it"
                state.actorIsSubject -> "Your turn to rank"
                rankingPlayers -> "Predict ${subject.name}’s call"
                else -> "Predict ${subject.name}’s order"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            when {
                state.actorIsSubject && rankingPlayers ->
                    "Most likely at the top. Drag to put everyone in order."
                state.actorIsSubject ->
                    "Favorite at the top. Drag to put the five in order."
                rankingPlayers ->
                    "Drag from who you think ${subject.name} picked first to last."
                else ->
                    "Drag from what you think ${subject.name} ranked first to last."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(question.prompt, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            rankingLegend(question.options.size, rankingPlayers),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        DraggableRankList(
            items = state.localRanking,
            onReorder = onReorder,
            enabled = true,
            modifier = Modifier.weight(1f),
            onDragStart = onDragStart,
            onRankCross = onRankCross,
        )
    }
}

@Composable
private fun OfflineRevealScreen(
    state: OfflineGameState,
    snackbarHostState: SnackbarHostState,
    onNextRound: () -> Unit,
    onExit: () -> Unit,
) {
    val result = state.currentResult ?: return
    val finalRound = state.roundIndex == state.schedule.lastIndex
    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            PrimaryCta(
                text = if (finalRound) "See final result" else "Next round",
                onClick = onNextRound,
            )
        },
    ) {
        Rank5TopBar(
            title = "Round ${state.roundIndex + 1} reveal",
            onBack = onExit,
        )
        Spacer(Modifier.height(Spacing.md))
        Text("${result.subject.name}’s real order", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            result.question.prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        result.subjectRanking.forEachIndexed { index, item ->
            RankCard(index + 1, item, modifier = Modifier.padding(bottom = Spacing.sm))
        }

        Spacer(Modifier.height(Spacing.md))
        SectionLabel("THE GUESSES")
        Spacer(Modifier.height(Spacing.sm))
        result.predictions.entries.sortedByDescending { result.scores[it.key] ?: 0 }.forEach { (id, guess) ->
            OfflineGuessCard(
                player = state.players.firstOrNull { it.id == id },
                guess = guess,
                actual = result.subjectRanking,
                points = result.scores[id] ?: 0,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            scoringLegend(result.subjectRanking.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Text("TEAM SCORE", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${formatOfflinePoints(result.teamScore)} this round",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${formatOfflinePoints(state.teamScore)} total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun OfflineGuessCard(
    player: OfflinePlayer?,
    guess: List<String>,
    actual: List<String>,
    points: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                    name = player?.name ?: "Player",
                    colorSeed = player?.id ?: "player",
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "${formatOfflinePoints(points)} pts",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalRank5Extras.current.accentText,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            guess.forEachIndexed { index, item ->
                val actualIndex = actual.indexOf(item)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(Spacing.sm))
                    Text(item, modifier = Modifier.weight(1f))
                    Text(
                        if (actualIndex == index) "Exact" else "${kotlin.math.abs(actualIndex - index)} off",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (actualIndex == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineResultsScreen(
    state: OfflineGameState,
    snackbarHostState: SnackbarHostState,
    onRematch: () -> Unit,
    onExit: () -> Unit,
) {
    val average = state.history.map { it.teamScore }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            PrimaryCta(text = "Play again", onClick = onRematch)
            Spacer(Modifier.height(Spacing.sm))
            SecondaryCta(text = "Back to home", onClick = onExit)
        },
    ) {
        Rank5TopBar(title = "Pass & Play results")
        Spacer(Modifier.height(Spacing.lg))
        Text("That’s the game", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Your group scored ${formatOfflinePoints(state.teamScore)} points together.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("TEAM TOTAL", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatOfflinePoints(state.teamScore),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${formatOfflinePoints(average)} average per round",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel("GROUP HIGHLIGHTS")
        Spacer(Modifier.height(Spacing.sm))
        val playerAverages = state.players.mapNotNull { player ->
            val scores = state.history.mapNotNull { it.scores[player.id] }
            if (scores.isEmpty()) null else player to scores.average().toInt()
        }.sortedByDescending { it.second }
        playerAverages.forEachIndexed { index, (player, score) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(Spacing.md))
                PlayerChip(player.name, player.id, modifier = Modifier.weight(1f))
                Text("${formatOfflinePoints(score)} avg", style = MaterialTheme.typography.titleMedium)
            }
            if (index < playerAverages.lastIndex) HorizontalDivider()
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel("ROUND BREAKDOWN")
        Spacer(Modifier.height(Spacing.sm))
        state.history.forEach { result ->
            OfflineRoundSummary(
                result = result,
                deckName = state.decks.firstOrNull { it.id == result.question.deckId }?.title,
            )
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun OfflineRoundSummary(result: OfflineRoundResult, deckName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(result.index + 1)
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text("Round ${result.index + 1} · ${result.subject.name}", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(deckName, result.question.prompt).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(formatOfflinePoints(result.teamScore), style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatOfflinePoints(points: Int): String = String.format(Locale.US, "%,d", points)
