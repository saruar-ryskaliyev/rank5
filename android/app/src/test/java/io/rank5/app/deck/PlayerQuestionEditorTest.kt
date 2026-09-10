package io.rank5.app.deck

import io.rank5.app.ui.deckContentsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQuestionEditorTest {
    private fun optionsQuestion(index: Int) = DeckQuestion(
        id = "q$index",
        prompt = "Question $index",
        options = listOf("A", "B", "C", "D", "E"),
    )

    private fun playersQuestion(index: Int) = DeckQuestion(
        id = "p$index",
        prompt = "Who is most likely to do thing $index?",
        options = emptyList(),
        kind = QuestionKindPlayers,
    )

    @Test fun `player questions validate without authored options`() {
        val questions = listOf(playersQuestion(1), playersQuestion(2), playersQuestion(3))
        assertNull(validateEditor("Most likely to", "👀", questions))
    }

    @Test fun `a mixed deck still requires five options on normal questions`() {
        val questions = listOf(
            playersQuestion(1),
            optionsQuestion(2),
            optionsQuestion(3).copy(options = listOf("A", "B", "C", "D", "")),
        )
        val error = validateEditor("Mixed", "🎯", questions)
        assertEquals("Question 3 needs 5 options", error)
    }

    @Test fun `a prompt still cannot be blank on a player question`() {
        val questions = listOf(playersQuestion(1).copy(prompt = "  "), playersQuestion(2), playersQuestion(3))
        assertEquals("Question 1 needs a prompt", validateEditor("Deck", "🎯", questions))
    }

    @Test fun `deck summary explains what a room will be asked`() {
        assertTrue(
            deckContentsSummary(List(3) { playersQuestion(it) }).contains("rank each other"),
        )
        assertTrue(
            deckContentsSummary(List(3) { optionsQuestion(it) }).contains("five options"),
        )
        val mixed = deckContentsSummary(listOf(optionsQuestion(1), playersQuestion(2)))
        assertTrue(mixed, mixed.contains("1 question rank"))
        assertTrue(deckContentsSummary(emptyList()).contains("no questions"))
    }
}
