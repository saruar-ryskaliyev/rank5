package io.rank5.app.ui

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.rank5.app.deck.DeckSummary
import io.rank5.app.deck.DecksUiState
import io.rank5.app.game.UiState
import io.rank5.app.net.DeckInfo
import io.rank5.app.net.PlayerView
import io.rank5.app.net.RoomStateView
import io.rank5.app.ui.theme.Rank5Theme

@Preview(name = "Home · light", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "Home · dark", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreview() {
    Rank5Theme {
        HomeScreen(
            state = UiState(nickname = "Maya"),
            snackbarHostState = remember { SnackbarHostState() },
            onNickname = {}, onJoinCode = {}, onCreate = {}, onJoin = {},
        )
    }
}

@Preview(name = "Decks · populated", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DecksPopulatedPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(
                mine = listOf(DeckSummary("mine", "Our hot takes", "🔥", ownerId = "me", questionCount = 8)),
                community = listOf(
                    DeckSummary("food", "Food fight", "🍕", isBuiltin = true, questionCount = 12),
                    DeckSummary("travel", "Dream trips", "✈️", ownerName = "Nora", questionCount = 10, visibility = "public"),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = true,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onCreate = {},
            onRefreshSaved = {},
            onDismissLoginPrompt = {}, onGoSignIn = {},
        )
    }
}

@Preview(name = "Decks · loading", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DecksLoadingPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(loading = true),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = false,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onCreate = {},
            onRefreshSaved = {},
            onDismissLoginPrompt = {}, onGoSignIn = {},
        )
    }
}

@Preview(name = "Decks · error", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DecksErrorPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(loadError = "Couldn’t load decks. Check your connection and try again."),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = false,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onCreate = {},
            onRefreshSaved = {},
            onDismissLoginPrompt = {}, onGoSignIn = {},
        )
    }
}

@Preview(name = "Saved · empty", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SavedEmptyPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(savedLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = true,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onRefreshSaved = {},
            onCreate = {}, onDismissLoginPrompt = {}, onGoSignIn = {},
            initialFilter = DeckFilter.Saved,
        )
    }
}

@Preview(name = "Saved · loading", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SavedLoadingPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(savedLoading = true),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = true,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onRefreshSaved = {},
            onCreate = {}, onDismissLoginPrompt = {}, onGoSignIn = {},
            initialFilter = DeckFilter.Saved,
        )
    }
}

@Preview(name = "Saved · error", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SavedErrorPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(savedError = "Couldn’t load saved decks."),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = true,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onRefreshSaved = {},
            onCreate = {}, onDismissLoginPrompt = {}, onGoSignIn = {},
            initialFilter = DeckFilter.Saved,
        )
    }
}

@Preview(name = "Saved · populated", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SavedPopulatedPreview() {
    Rank5Theme {
        DecksListScreen(
            state = DecksUiState(
                saved = listOf(DeckSummary("food", "Food fight", "🍕", isBuiltin = true, questionCount = 12)),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            signedIn = true,
            onRefresh = {}, onOpenDeck = {}, onQuery = {}, onLoadMore = {}, onRefreshSaved = {},
            onCreate = {}, onDismissLoginPrompt = {}, onGoSignIn = {},
            initialFilter = DeckFilter.Saved,
        )
    }
}

@Preview(name = "Lobby · deck mix", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LobbyDeckMixPreview() {
    val selected = listOf(
        DeckInfo("food", "Food fight", "🍕", 12),
        DeckInfo("movies", "Movie night", "🎬", 10),
    )
    Rank5Theme {
        LobbyScreen(
            state = UiState(
                isHost = true,
                selectedDecks = selected,
                room = RoomStateView(
                    code = "R5MX",
                    phase = "LOBBY",
                    deckId = "food",
                    deckIds = selected.map { it.id },
                    selectedDecks = selected,
                    decks = selected,
                    players = listOf(PlayerView("host", "Maya", true, true, 0)),
                    youAre = "host",
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSelectDeck = {}, onRemoveDeck = {}, onSelectMode = {}, onSelectRounds = {},
            onLoadDeckLibrary = {}, onCommunityQuery = {}, onPickCommunityDeck = {},
            onStart = {}, onLeave = {},
        )
    }
}

@Preview(name = "Lobby · single deck", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LobbySingleDeckPreview() {
    val deck = DeckInfo("food", "Food fight", "🍕", 12)
    Rank5Theme {
        LobbyScreen(
            state = UiState(
                isHost = true,
                selectedDecks = listOf(deck),
                room = RoomStateView(
                    code = "R5MX", phase = "LOBBY", deckId = deck.id,
                    deckIds = listOf(deck.id), selectedDecks = listOf(deck), decks = listOf(deck),
                    players = listOf(PlayerView("host", "Maya", true, true, 0)), youAre = "host",
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSelectDeck = {}, onRemoveDeck = {}, onSelectMode = {}, onSelectRounds = {},
            onLoadDeckLibrary = {}, onCommunityQuery = {}, onPickCommunityDeck = {},
            onStart = {}, onLeave = {},
        )
    }
}
