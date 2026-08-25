package io.rank5.app.ui

import io.rank5.app.game.RoundResult
import io.rank5.app.game.UiState
import io.rank5.app.net.DeckInfo
import io.rank5.app.net.RoomStateView
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultCardDataTest {
    @Test
    fun `maps coop result with deck and round breakdown`() {
        val state = UiState(
            room = room(teamScore = 5700),
            roundHistory = listOf(
                RoundResult(index = 0, teamScore = 1800, scores = emptyMap()),
                RoundResult(index = 1, teamScore = 1900, scores = emptyMap()),
                RoundResult(index = 2, teamScore = 2000, scores = emptyMap()),
            ),
        )

        val card = ResultCardData.from(state)

        assertEquals("Food Fight", card.deckName)
        assertEquals("🍕", card.deckEmoji)
        assertEquals(5700, card.teamScore)
        assertEquals(6000, card.maxTeamScore)
        assertEquals(listOf(1800, 1900, 2000), card.roundScores)
    }

    @Test fun `multi deck result uses mix title and contributing names`() {
        val selected = listOf(
            DeckInfo("food", "Food Fight", "🍕", 6),
            DeckInfo("movies", "Movie Night", "🎬", 6),
        )
        val state = UiState(
            room = room().copy(
                deckIds = selected.map { it.id },
                selectedDecks = selected,
                decks = selected,
            ),
        )

        val card = ResultCardData.from(state)

        assertEquals("2-deck mix", card.deckName)
        assertEquals(listOf("Food Fight", "Movie Night"), card.deckNames)
    }

    private fun room(
        teamScore: Int = 0,
    ) = RoomStateView(
        code = "ABCD",
        phase = "GAME_OVER",
        deckId = "food",
        totalRounds = 3,
        teamScore = teamScore,
        decks = listOf(DeckInfo("food", "Food Fight", "🍕", 6)),
    )
}
