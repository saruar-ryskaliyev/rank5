package io.rank5.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.rank5.app.auth.AuthState
import io.rank5.app.auth.AuthUser
import io.rank5.app.game.UiState
import io.rank5.app.deck.Deck
import io.rank5.app.deck.DeckQuestion
import io.rank5.app.net.DeckInfo
import io.rank5.app.net.PlayerView
import io.rank5.app.net.RoomStateView
import io.rank5.app.ui.theme.Rank5Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Rank5AccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeExplainsRequiredInputAndKeepsCreateDisabled() {
        compose.setContent {
            Rank5Theme {
                HomeScreen(
                    state = UiState(),
                    snackbarHostState = SnackbarHostState(),
                    onNickname = {}, onJoinCode = {}, onCreate = {}, onJoin = {},
                )
            }
        }
        compose.onNodeWithText("Who’s playing?").assertIsDisplayed()
        compose.onNodeWithText("Create game").assertIsNotEnabled()
        compose.onNodeWithText("Four to six letters or numbers").assertIsDisplayed()
    }

    @Test fun savedActionAnnouncesSelectedState() {
        compose.setContent {
            Rank5Theme {
                DeckDetailScreen(
                    deck = Deck(
                        id = "food",
                        title = "Food",
                        questions = listOf(DeckQuestion(prompt = "Pick", options = listOf("1", "2", "3", "4", "5"))),
                        isBuiltin = true,
                        visibility = "public",
                    ),
                    loading = false,
                    saving = false,
                    canEdit = false,
                    signedIn = true,
                    isSaved = true,
                    savingSaved = false,
                    snackbarHostState = SnackbarHostState(),
                    onBack = {}, onUseDeck = {}, onEdit = {}, onDelete = {}, onPublish = {},
                    onUnpublish = {}, onReport = {}, onToggleSaved = {}, onGoSignIn = {},
                )
            }
        }
        compose.onNodeWithText("Saved").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saved"),
        )
    }

    @Test fun deckPickerExposesCheckedSelection() {
        val deck = DeckInfo("food", "Food", "🍕", 6)
        compose.setContent {
            Rank5Theme {
                LobbyScreen(
                    state = UiState(
                        isHost = true,
                        selectedDecks = listOf(deck),
                        room = RoomStateView(
                            code = "ABCD", phase = "LOBBY", deckId = deck.id,
                            deckIds = listOf(deck.id), selectedDecks = listOf(deck), decks = listOf(deck),
                            players = listOf(PlayerView("host", "Host", true, true, 0)), youAre = "host",
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onSelectDeck = {}, onRemoveDeck = {}, onSelectMode = {}, onSelectRounds = {},
                    onLoadDeckLibrary = {}, onCommunityQuery = {}, onPickCommunityDeck = {},
                    onStart = {}, onLeave = {},
                )
            }
        }
        compose.onNodeWithText("Add decks").performClick()
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test fun profileExposesGoogleSignInAsAButton() {
        compose.setContent {
            Rank5Theme {
                ProfileScreen(
                    authState = AuthState.SignedOut,
                    snackbarHostState = SnackbarHostState(),
                    busy = false,
                    onSignInGoogle = {},
                    onSignOut = {},
                    onDeleteAccount = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Sign in with Google")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test fun profilePlacesIdentityAndStatsBeforeSoundSettings() {
        compose.setContent {
            Rank5Theme {
                ProfileScreen(
                    authState = AuthState.SignedIn(
                        token = "token",
                        user = AuthUser("user-1", "Alex", "alex"),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    busy = false,
                    onSignInGoogle = {},
                    onSignOut = {},
                    onDeleteAccount = {},
                )
            }
        }

        val accountTop = compose.onNodeWithText("ACCOUNT").fetchSemanticsNode().boundsInRoot.top
        val statsTop = compose.onNodeWithText("YOUR STATS").fetchSemanticsNode().boundsInRoot.top
        val soundTop = compose.onNodeWithText("SOUND").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Account should appear before stats", accountTop < statsTop)
        assertTrue("Stats should appear before sound settings", statsTop < soundTop)
    }
}
