package io.rank5.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rank5.app.deck.DeckSummary
import io.rank5.app.deck.DecksUiState
import io.rank5.app.deck.unifiedDeckLibrary
import io.rank5.app.ui.components.DeckIconTile
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.theme.Spacing

enum class DeckFilter { All, Mine, Saved, Community }

@Composable
fun DecksListScreen(
    state: DecksUiState,
    snackbarHostState: SnackbarHostState,
    signedIn: Boolean,
    onRefresh: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onQuery: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefreshSaved: () -> Unit,
    onCreate: () -> Unit,
    onDismissLoginPrompt: () -> Unit,
    onGoSignIn: () -> Unit,
    initialFilter: DeckFilter = DeckFilter.All,
) {
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    LaunchedEffect(Unit) { onRefresh() }

    val query = state.communityQuery.trim()
    val mine = state.mine.filter { query.isEmpty() || it.title.contains(query, true) }
    val ownedIds = state.mine.mapTo(mutableSetOf()) { it.id }
    val all = unifiedDeckLibrary(mine, state.saved + state.community)
    val visible = when (filter) {
        DeckFilter.All -> all
        DeckFilter.Mine -> mine
        DeckFilter.Saved -> state.saved.filter { query.isEmpty() || it.title.contains(query, true) }
        DeckFilter.Community -> all.filterNot { it.id in ownedIds }
    }

    GameScaffold(
        snackbarHostState = snackbarHostState,
        maxContentWidth = io.rank5.app.ui.theme.Sizes.wideContentMax,
        footer = {
            PrimaryCta("Create a deck", onCreate, enabled = !state.loading)
        },
    ) {
        Text("Decks", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Pick a conversation starter or make one that only your group could invent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(
            value = state.communityQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text("Search decks") },
            singleLine = true,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DeckFilter.entries.forEach { item ->
                val label = when (item) {
                    DeckFilter.All -> "All"
                    DeckFilter.Mine -> "Mine"
                    DeckFilter.Saved -> "Saved"
                    DeckFilter.Community -> "Community"
                }
                FilterChip(
                    selected = filter == item,
                    onClick = {
                        if (item == DeckFilter.Saved && !signedIn) onRefreshSaved()
                        else filter = item
                    },
                    label = { Text(label) },
                    enabled = item != DeckFilter.Mine || signedIn,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        when {
            filter == DeckFilter.Saved && state.savedLoading -> DeckSkeletons(Modifier.weight(1f))
            filter == DeckFilter.Saved && state.savedError != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Saved decks didn’t load", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        state.savedError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRefreshSaved) { Text("Try again") }
                }
            }
            state.loading && all.isEmpty() -> DeckSkeletons(Modifier.weight(1f))
            state.loadError != null && all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The deck shelf didn’t load", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(state.loadError, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRefresh) { Text("Try again") }
                }
            }
            visible.isEmpty() && !state.communityLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (query.isEmpty()) "No decks here yet" else "No match for “$query”",
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (filter == DeckFilter.Mine) "Create your first deck to see it here."
                        else if (filter == DeckFilter.Saved) "Bookmark an official or public deck to find it here."
                        else "Try a shorter search or another filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth >= 840.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                        DeckLibraryList(
                            visible, ownedIds, state.communityLoading, state.communityHasMore,
                            onOpenDeck, onLoadMore, Modifier.weight(1f),
                        )
                        Surface(
                            modifier = Modifier.width(320.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(Modifier.padding(Spacing.xl)) {
                                Text("Build the perfect round", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(Spacing.sm))
                                Text(
                                    "Open a deck to review every question, or create one tailored to your group.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    DeckLibraryList(
                        visible, ownedIds, state.communityLoading, state.communityHasMore,
                        onOpenDeck, onLoadMore, Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (state.showLoginPrompt) AlertDialog(
        onDismissRequest = onDismissLoginPrompt,
        title = { Text(if (state.loginPromptForSave) "Sign in to save decks" else "Sign in to create decks") },
        text = {
            Text(
                if (state.loginPromptForSave) "Sign in to keep saved decks with your profile."
                else "Sign in once so your decks stay private, editable, and available on this profile.",
            )
        },
        confirmButton = { TextButton(onClick = { onDismissLoginPrompt(); onGoSignIn() }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = onDismissLoginPrompt) { Text("Not now") } },
    )
}

@Composable
private fun DeckLibraryList(
    decks: List<DeckSummary>,
    ownedIds: Set<String>,
    loading: Boolean,
    hasMore: Boolean,
    onOpen: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(decks, key = { it.id }) { deck -> DeckRow(deck, deck.id in ownedIds, onOpen) }
        if (loading) item("loading") {
            Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (hasMore) item("more") {
            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more decks") }
        }
        item("bottom-space") { Spacer(Modifier.height(Spacing.sm)) }
    }
}

@Composable
private fun DeckRow(deck: DeckSummary, owned: Boolean, onOpen: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(deck.id) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            DeckIconTile(deck.emoji, deck.title)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(deck.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "${deck.questionCount} questions · ${if (owned) "by You" else "by ${deck.creatorName}"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                when { deck.isBuiltin -> "Official"; owned && deck.visibility == "public" -> "Published";
                    owned -> "Private"; else -> "Public" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DeckSkeletons(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(5) {
            Surface(Modifier.fillMaxWidth().height(72.dp), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant) {}
        }
    }
}
