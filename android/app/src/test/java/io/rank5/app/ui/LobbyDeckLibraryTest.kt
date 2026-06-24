package io.rank5.app.ui

import io.rank5.app.deck.DeckSummary
import io.rank5.app.net.DeckInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class LobbyDeckLibraryTest {
    @Test
    fun `lobby combines room and public decks without duplicates`() {
        val roomDecks = listOf(
            DeckInfo("food", "Food Favorites", "🍕", 6),
            DeckInfo("private", "My private deck", "🎯", 3),
        )
        val publicDecks = listOf(
            DeckSummary("food", "Food Favorites", "🍕", isBuiltin = true, questionCount = 6),
            DeckSummary("party", "Party", "🎉", ownerName = "Maya", questionCount = 5),
        )

        val result = lobbyDeckOptions(roomDecks, publicDecks)

        assertEquals(listOf("food", "private", "party"), result.map { it.id })
        assertEquals("6 questions · by Rank5", result[0].metadata)
        assertEquals("3 questions · Your deck", result[1].metadata)
        assertEquals("5 questions · by Maya", result[2].metadata)
    }

    @Test fun `guest summary distinguishes single deck and deck mix`() {
        val decks = listOf(
            DeckInfo("food", "Food", "🍕", 6),
            DeckInfo("movies", "Movies", "🎬", 6),
            DeckInfo("travel", "Travel", "✈️", 6),
        )
        assertEquals("Food · 6 rounds", guestSetupLabel(decks.take(1), 6))
        assertEquals("3-deck mix · 6 rounds", guestSetupLabel(decks, 6))
    }
}
