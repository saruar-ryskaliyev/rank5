package io.rank5.app.game

import io.rank5.app.net.DeckInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQuestionWarningTest {
    private fun deck(
        id: String,
        questions: Int,
        playerQuestions: Int = 0,
    ) = DeckInfo(id, "Deck $id", "🃏", questions, playerQuestions)

    @Test fun `no warning without player questions`() {
        assertNull(playerQuestionWarning(listOf(deck("food", 6)), connectedPlayers = 2))
    }

    @Test fun `no warning once the group is big enough`() {
        val decks = listOf(deck("most_likely", 6, playerQuestions = 6))
        assertNull(playerQuestionWarning(decks, connectedPlayers = 3))
        assertNull(playerQuestionWarning(decks, connectedPlayers = 8))
    }

    @Test fun `mixed selection warns that player questions are skipped`() {
        val warning = playerQuestionWarning(
            listOf(deck("food", 6), deck("most_likely", 4, playerQuestions = 4)),
            connectedPlayers = 2,
        )
        assertNotNull(warning)
        assertTrue(warning!!, warning.contains("skipped"))
    }

    @Test fun `player-only selection warns the game cannot start`() {
        val warning = playerQuestionWarning(
            listOf(deck("most_likely", 6, playerQuestions = 6)),
            connectedPlayers = 2,
        )
        assertNotNull(warning)
        assertTrue(warning!!, warning.contains("at least 3"))
    }
}
