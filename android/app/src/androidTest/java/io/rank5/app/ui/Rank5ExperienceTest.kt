package io.rank5.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.rank5.app.audio.AudioSettings
import io.rank5.app.auth.AuthState
import io.rank5.app.deck.Deck
import io.rank5.app.deck.DeckQuestion
import io.rank5.app.deck.DecksUiState
import io.rank5.app.game.UiState
import io.rank5.app.offline.OfflineGameEngine
import io.rank5.app.offline.OfflineScreen
import io.rank5.app.ui.theme.Rank5Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

class Rank5ExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun roomSheetNormalizesPastedCodeAndOnlySubmitsValidInput() {
        var state by mutableStateOf(UiState())
        var joins = 0
        compose.setContent {
            Rank5Theme {
                HomeScreen(state, SnackbarHostState(),
                    onNickname = { state = state.copy(nickname = it) },
                    onJoinCode = { state = state.copy(joinCode = it) },
                    onCreate = {}, onJoin = { joins++ }, onPassAndPlay = {})
            }
        }
        compose.onNodeWithText("Join room").performClick()
        compose.onNodeWithText("Join game").assertIsNotEnabled()
        compose.onNodeWithText("Your name").performTextInput("Alex")
        compose.onNodeWithText("Room code").performTextInput("ab c-12")
        compose.runOnIdle { assertEquals("ABC12", state.joinCode) }
        compose.onNodeWithText("Join game").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, joins) }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Room code").assertDoesNotExist()
    }

    @Test fun helpExplainsTheGameAndReturnsToModeChoices() {
        compose.setContent {
            Rank5Theme {
                HomeScreen(UiState(), SnackbarHostState(), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("How to play").performClick()
        compose.onNodeWithText("Rank your five").assertIsDisplayed()
        compose.onNodeWithText("Read the room").assertIsDisplayed()
        compose.onNodeWithText("Reveal the surprises").assertIsDisplayed()
        compose.onNodeWithText("Got it, let’s play").performClick()
        compose.onNodeWithText("Pass & Play").assertIsDisplayed()
    }

    @Test fun searchingAlsoFiltersDownloadedDecksAndCanBeCleared() {
        var state by mutableStateOf(DecksUiState(
            communityQuery = "food",
            downloadedDecks = listOf(Deck("food", "Food"), Deck("travel", "Travel")),
        ))
        compose.setContent {
            Rank5Theme {
                DecksListScreen(state, SnackbarHostState(), false,
                    onRefresh = {}, onOpenDeck = {},
                    onQuery = { state = state.copy(communityQuery = it) },
                    onLoadMore = {}, onRefreshSaved = {}, onCreate = {},
                    onDismissLoginPrompt = {}, onGoSignIn = {})
            }
        }
        compose.onNodeWithText("Food").assertIsDisplayed()
        compose.onNodeWithText("Travel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Travel").assertIsDisplayed()
    }

    @Test fun soundSettingCanBeToggledByItsLabel() {
        var settings by mutableStateOf(AudioSettings(gameSoundsEnabled = true))
        compose.setContent {
            Rank5Theme {
                ProfileScreen(AuthState.SignedOut, SnackbarHostState(), false,
                    audioSettings = settings,
                    onSignInGoogle = {}, onSignOut = {}, onDeleteAccount = {},
                    onGameSoundsEnabled = { settings = settings.copy(gameSoundsEnabled = it) })
            }
        }
        compose.onNodeWithText("Game sounds").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(false, settings.gameSoundsEnabled) }
    }

    @Test fun offlineGameHidesEachRankingBeforeTheNextPlayerAndReachesResults() {
        val engine = OfflineGameEngine(Random(7))
        val deck = Deck("food", "Food", questions = (1..3).map { index ->
            DeckQuestion("q$index", "Pick your favorites $index", listOf("Pizza", "Sushi", "Tacos", "Pasta", "Salad"))
        })
        var state by mutableStateOf(engine.start(listOf("Alex", "Sam"), listOf(deck), setOf("food"), 3))
        compose.setContent {
            Rank5Theme {
                OfflineGameFlow(state, SnackbarHostState(),
                    onPlayerName = { _, _ -> }, onAddPlayer = {}, onRemovePlayer = {},
                    onToggleDeck = {}, onBrowseDecks = {}, onSelectRounds = {}, onStart = {},
                    onRevealToActor = { state = engine.revealToActor(state) },
                    onReorder = { state = engine.reorder(state, it) },
                    onLockIn = { state = engine.lockIn(state) },
                    onNextRound = { state = engine.nextRound(state) },
                    onRematch = { state = engine.rematch(state) }, onExit = {})
            }
        }
        repeat(3) { round ->
            repeat(2) {
                compose.waitForIdle()
                compose.onNodeWithText("Pizza").assertDoesNotExist()
                val actor = state.actor!!.name
                compose.onNodeWithContentDescription("I am $actor. Show my private turn.").performClick()
                compose.onNodeWithText("Pizza").assertIsDisplayed()
                compose.onNodeWithText("Lock in & hide").performClick()
            }
            compose.onNodeWithText(if (round == 2) "See final result" else "Next round").performClick()
        }
        compose.onNodeWithText("That’s the game").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(OfflineScreen.Results, state.screen)
            assertEquals(3, state.history.size)
        }
    }
}
