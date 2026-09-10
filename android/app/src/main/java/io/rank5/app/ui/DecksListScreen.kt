package io.rank5.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import io.rank5.app.deck.DeckSummary
import io.rank5.app.deck.DecksUiState
import io.rank5.app.deck.unifiedDeckLibrary
import io.rank5.app.deck.toSummary
import io.rank5.app.ui.components.DeckIconTile
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.ShimmerBlock
import io.rank5.app.ui.theme.Motion
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

enum class DeckFilter { All, AvailableOffline, Mine, Saved, Community }

private enum class ShelfState { SavedLoading, SavedError, Loading, LoadError, Empty, Shelf }

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
    // Tab state now survives switches, so only fetch when the shelf is empty;
    // explicit refreshes (auth changes, retry) still go through onRefresh.
    val shelfEmpty = state.mine.isEmpty() && state.community.isEmpty() && state.saved.isEmpty()
    LaunchedEffect(Unit) { if (shelfEmpty) onRefresh() }

    val query = state.communityQuery.trim()
    val mine = state.mine.filter { query.isEmpty() || it.title.contains(query, true) }
    val ownedIds = state.mine.mapTo(mutableSetOf()) { it.id }
    val downloadedSummaries = state.downloadedDecks.map { it.toSummary() }
    val downloadedIds = state.downloadedDecks.mapTo(hashSetOf()) { it.id }
    val all = unifiedDeckLibrary(mine, downloadedSummaries + state.saved + state.community)
        .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
    val visible = when (filter) {
        DeckFilter.All -> all
        DeckFilter.AvailableOffline -> all.filter { it.id in downloadedIds }
        DeckFilter.Mine -> mine
        DeckFilter.Saved -> state.saved.filter { query.isEmpty() || it.title.contains(query, true) }
        DeckFilter.Community -> all.filterNot { it.id in ownedIds }
    }
    val remotePagination = filter == DeckFilter.All || filter == DeckFilter.Community

    GameScaffold(
        snackbarHostState = snackbarHostState,
        maxContentWidth = io.rank5.app.ui.theme.Sizes.wideContentMax,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Decks", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, null, Modifier.size(Sizes.metadataIcon))
                Spacer(Modifier.width(Spacing.xs))
                Text("Create")
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Good conversations start with a great topic.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        OutlinedTextField(
            value = state.communityQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text("Search decks") },
            trailingIcon = if (state.communityQuery.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } }
            } else null,
            shape = MaterialTheme.shapes.large,
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
                    DeckFilter.AvailableOffline -> "Available offline"
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
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.heightIn(min = Sizes.touchTarget),
                    enabled = item != DeckFilter.Mine || signedIn,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        if (state.loadError != null && all.isNotEmpty() && !state.loading) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Showing decks on this phone", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        val shelfState = when {
            filter == DeckFilter.Saved && state.savedLoading -> ShelfState.SavedLoading
            filter == DeckFilter.Saved && state.savedError != null -> ShelfState.SavedError
            state.loading && all.isEmpty() -> ShelfState.Loading
            state.loadError != null && all.isEmpty() -> ShelfState.LoadError
            visible.isEmpty() && !state.communityLoading -> ShelfState.Empty
            else -> ShelfState.Shelf
        }
        // Loading -> list / error / empty crossfade instead of snapping.
        AnimatedContent(
            targetState = shelfState,
            modifier = Modifier.weight(1f),
            transitionSpec = { Motion.crossfadeEnter() togetherWith Motion.crossfadeExit() },
            label = "deck-shelf",
        ) { shelf ->
        when (shelf) {
            ShelfState.SavedLoading -> DeckSkeletons(Modifier.fillMaxSize())
            ShelfState.SavedError -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Saved decks didn’t load", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        state.savedError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRefreshSaved) { Text("Try again") }
                }
            }
            ShelfState.Loading -> DeckSkeletons(Modifier.fillMaxSize())
            ShelfState.LoadError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The deck shelf didn’t load", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(state.loadError.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRefresh) { Text("Try again") }
                }
            }
            ShelfState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(Sizes.iconTile),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Spacing.md))
                    Text(if (query.isEmpty()) "No decks here yet" else "No match for “$query”",
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (filter == DeckFilter.Mine) "Create your first deck to see it here."
                        else if (filter == DeckFilter.Saved) "Bookmark an official or public deck to find it here."
                        else if (filter == DeckFilter.AvailableOffline) "Download a deck to use it without internet."
                        else "Try a shorter search or another filter.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ShelfState.Shelf -> BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= Sizes.responsiveBreakpoint) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        DeckLibraryList(
                            visible, ownedIds, downloadedIds,
                            state.communityLoading && remotePagination,
                            state.communityHasMore && remotePagination,
                            onOpenDeck, onLoadMore, Modifier.weight(1f),
                        )
                        Surface(
                            modifier = Modifier.width(Sizes.sidebar),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(Modifier.padding(Spacing.md)) {
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
                        visible, ownedIds, downloadedIds,
                        state.communityLoading && remotePagination,
                        state.communityHasMore && remotePagination,
                        onOpenDeck, onLoadMore, Modifier.fillMaxSize(),
                    )
                }
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
    downloadedIds: Set<String>,
    loading: Boolean,
    hasMore: Boolean,
    onOpen: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(decks, key = { it.id }) { deck ->
            DeckRow(
                deck, deck.id in ownedIds, deck.id in downloadedIds, onOpen,
                Modifier.animateItem(placementSpec = Motion.placementSpring()),
            )
        }
        if (loading) item("loading") {
            Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (hasMore) item("more") {
            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Load more decks") }
        }
        item("bottom-space") { Spacer(Modifier.height(Spacing.sm)) }
    }
}

@Composable
private fun DeckRow(
    deck: DeckSummary,
    owned: Boolean,
    downloaded: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onOpen(deck.id) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            DeckIconTile(deck.emoji, deck.title)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(deck.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                val source = when {
                    deck.isBuiltin -> "Official"
                    owned && deck.visibility == "public" -> "By you · published"
                    owned -> "By you · private"
                    else -> "By ${deck.creatorName}"
                }
                Text("${deck.questionCount} questions · $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (downloaded) {
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DownloadDone, null, Modifier.size(Sizes.metadataIcon),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Available offline", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeckSkeletons(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(5) { ShimmerBlock(height = Sizes.listRow) }
    }
}
