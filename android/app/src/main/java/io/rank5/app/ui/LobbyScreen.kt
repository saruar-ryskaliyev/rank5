package io.rank5.app.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import io.rank5.app.R
import io.rank5.app.deck.DeckSummary
import io.rank5.app.game.BusyAction
import io.rank5.app.game.MaxSelectedDecks
import io.rank5.app.game.UiState
import io.rank5.app.game.allowedRoundChoices
import io.rank5.app.game.combinedQuestionCount
import io.rank5.app.net.DeckInfo
import io.rank5.app.net.canonicalSelectedDecks
import io.rank5.app.ui.components.CenteredLoading
import io.rank5.app.ui.components.DeckIconTile
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.InfoBanner
import io.rank5.app.ui.components.PlayerChip
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.Rank5TopBar
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import io.rank5.app.ui.theme.animateLayoutChanges
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onSelectDeck: (String) -> Unit,
    onRemoveDeck: (String) -> Unit,
    onSelectMode: (String) -> Unit,
    onSelectRounds: (Int) -> Unit,
    onLoadDeckLibrary: () -> Unit,
    onCommunityQuery: (String) -> Unit,
    onPickCommunityDeck: (DeckSummary) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onCodeConfirmed: () -> Unit = {},
) {
    val room = state.room
    if (room == null) {
        GameScaffold(snackbarHostState = snackbarHostState) { CenteredLoading() }
        return
    }
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectedCount = room.players.count { it.connected }
    val canStart = connectedCount >= 2 && state.selectedDecks.isNotEmpty()
    val hostName = room.players.firstOrNull { it.isHost }?.nickname ?: "the host"

    LaunchedEffect(room.code, state.isHost) {
        if (state.isHost) onLoadDeckLibrary()
    }

    val roomDecks = (room.decks + room.selectedDecks + state.selectedDecks).distinctBy { it.id }
    val options = lobbyDeckOptions(roomDecks, state.communityResults)

    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = if (state.isHost) {
            {
                Column(Modifier.fillMaxWidth()) {
                    PrimaryCta(
                        text = "Start game",
                        onClick = onStart,
                        enabled = canStart,
                        loading = state.busyAction == BusyAction.Start,
                    )
                    if (!canStart) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Need at least 2 players — share the code!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else null,
    ) {
        Rank5TopBar(title = "Game lobby", onBack = onLeave)
        Spacer(Modifier.height(Spacing.md))
        RoomCodeCard(
            code = room.code,
            onCopy = {
                clipboard.setText(AnnotatedString(room.code))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCodeConfirmed()
                scope.launch { snackbarHostState.showSnackbar("Room code copied") }
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my Rank5 room: ${room.code}")
                }
                context.startActivity(Intent.createChooser(send, "Share room code"))
                onCodeConfirmed()
            },
        )
        Spacer(Modifier.height(Spacing.md))
        SectionLabel("PLAYERS (${room.players.size})")
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            room.players.forEach { player ->
                PlayerChip(
                    name = player.nickname,
                    colorSeed = player.id,
                    isHost = player.isHost,
                    dimmed = !player.connected,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        if (state.isHost) {
            HostSettings(
                mode = state.selectedMode,
                selected = state.selectedDecks,
                rounds = state.selectedRounds,
                options = options,
                query = state.communityQuery,
                loading = state.communityLoading,
                onSelectMode = onSelectMode,
                onToggleRoomDeck = onSelectDeck,
                onRemoveDeck = onRemoveDeck,
                onSelectRounds = onSelectRounds,
                onQuery = onCommunityQuery,
                onPickCommunityDeck = onPickCommunityDeck,
            )
        } else {
            GuestSettingsSummary(
                mode = room.mode,
                decks = room.canonicalSelectedDecks(),
                rounds = room.totalRounds,
            )
            Spacer(Modifier.height(Spacing.md))
            InfoBanner("Waiting for $hostName to start the game")
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun RoomCodeCard(code: String, onCopy: () -> Unit, onShare: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel("ROOM CODE")
            Spacer(Modifier.height(Spacing.sm))
            Text(
                code,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Invite friends with this code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Copy")
                }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Share")
                }
            }
        }
    }
}

data class LobbyDeckOption(
    val id: String,
    val name: String,
    val emoji: String,
    val questionCount: Int,
    val metadata: String,
    val publicDeck: DeckSummary? = null,
)

fun lobbyDeckOptions(
    roomDecks: List<DeckInfo>,
    publicDecks: List<DeckSummary>,
): List<LobbyDeckOption> {
    val publicById = publicDecks.associateBy { it.id }
    val roomOptions = roomDecks.distinctBy { it.id }.map { deck ->
        val publicDeck = publicById[deck.id]
        LobbyDeckOption(
            id = deck.id,
            name = deck.name,
            emoji = deck.emoji,
            questionCount = deck.questionCount,
            metadata = publicDeck?.libraryMetadata ?: "${deck.questionCount} questions · Your deck",
        )
    }
    val roomIds = roomOptions.mapTo(mutableSetOf()) { it.id }
    return roomOptions + publicDecks.filterNot { it.id in roomIds }.map { deck ->
        LobbyDeckOption(
            id = deck.id,
            name = deck.title,
            emoji = deck.emoji,
            questionCount = deck.questionCount,
            metadata = deck.libraryMetadata,
            publicDeck = deck,
        )
    }
}

@Composable
private fun HostSettings(
    mode: String,
    selected: List<DeckInfo>,
    rounds: Int,
    options: List<LobbyDeckOption>,
    query: String,
    loading: Boolean,
    onSelectMode: (String) -> Unit,
    onToggleRoomDeck: (String) -> Unit,
    onRemoveDeck: (String) -> Unit,
    onSelectRounds: (Int) -> Unit,
    onQuery: (String) -> Unit,
    onPickCommunityDeck: (DeckSummary) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    SectionLabel("MODE")
    Spacer(Modifier.height(Spacing.sm))
    val modes = listOf("coop" to "Team up", "versus" to "Compete")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelectMode(value) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(label) }
        }
    }
    Spacer(Modifier.height(Spacing.md))
    SectionLabel("DECK MIX")
    Spacer(Modifier.height(Spacing.sm))
    SelectedDeckList(selected, onRemoveDeck)
    Spacer(Modifier.height(Spacing.sm))
    OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text(if (selected.size >= MaxSelectedDecks) "Review selected decks" else "Add decks")
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        pluralStringResource(
            R.plurals.deck_mix_availability,
            selected.size,
            combinedQuestionCount(selected),
            selected.size,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(R.string.deck_mix_even_rotation),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.deck_mix_limit),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.md))
    SectionLabel("ROUNDS")
    Spacer(Modifier.height(Spacing.sm))
    val choices = allowedRoundChoices(combinedQuestionCount(selected))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        choices.forEachIndexed { index, count ->
            SegmentedButton(
                selected = rounds == count,
                onClick = { onSelectRounds(count) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
            ) { Text(count.toString()) }
        }
    }
    if (pickerOpen) {
        DeckPickerSheet(
            options = options,
            selectedIds = selected.map { it.id }.toSet(),
            query = query,
            loading = loading,
            onDismiss = { pickerOpen = false },
            onQuery = onQuery,
            onToggle = { option ->
                if (option.publicDeck != null) onPickCommunityDeck(option.publicDeck)
                else onToggleRoomDeck(option.id)
            },
        )
    }
}

@Composable
private fun SelectedDeckList(selected: List<DeckInfo>, onRemove: (String) -> Unit) {
    Column(
        modifier = Modifier.animateLayoutChanges(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        selected.forEach { deck ->
            androidx.compose.runtime.key(deck.id) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckIconTile(deck.emoji, deck.name)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(deck.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${deck.questionCount} questions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onRemove(deck.id) },
                            enabled = selected.size > 1,
                            modifier = Modifier.semantics {
                                contentDescription = if (selected.size > 1) {
                                    "Remove ${deck.name} from deck mix"
                                } else {
                                    "At least one deck is required"
                                }
                            },
                        ) { Icon(Icons.Rounded.Close, contentDescription = null) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DeckPickerSheet(
    options: List<LobbyDeckOption>,
    selectedIds: Set<String>,
    query: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit,
    onToggle: (LobbyDeckOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            Text("Choose decks", style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.deck_mix_picker_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search decks") },
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.sm))
            val filtered = remember(options, query) {
                options.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = Sizes.pickerMaxHeight)) {
                items(filtered, key = { it.id }) { deck ->
                    val selected = deck.id in selectedIds
                    val canAdd = selected || selectedIds.size < MaxSelectedDecks
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selected,
                                enabled = canAdd,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(deck) },
                            )
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null, enabled = canAdd)
                        Spacer(Modifier.width(Spacing.sm))
                        DeckIconTile(deck.emoji, deck.name)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(deck.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                deck.metadata,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
                if (loading) item("loading") {
                    Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filtered.isEmpty()) item("empty") {
                    Text("No decks match your search.", Modifier.padding(Spacing.md))
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun GuestSettingsSummary(mode: String, decks: List<DeckInfo>, rounds: Int) {
    SectionLabel("GAME SETUP")
    Spacer(Modifier.height(Spacing.sm))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                if (mode == "versus") "Compete for the high score" else "Team up for one score",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                guestSetupLabel(decks, rounds),
                style = MaterialTheme.typography.titleMedium,
            )
            if (decks.size > 1) {
                Text(
                    decks.joinToString(" · ") { "${it.emoji} ${it.name}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.deck_mix_even_rotation),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun guestSetupLabel(decks: List<DeckInfo>, rounds: Int): String =
    if (decks.size == 1) "${decks.first().name} · $rounds rounds"
    else "${decks.size}-deck mix · $rounds rounds"
