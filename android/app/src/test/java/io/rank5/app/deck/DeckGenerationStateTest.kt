package io.rank5.app.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckGenerationStateTest {
    @Test
    fun `generation errors have actionable messages`() {
        assertTrue(generationErrorMessage("generation_limit").contains("limit"))
        assertTrue(generationErrorMessage("generation_incomplete").contains("incomplete"))
        assertTrue(generationErrorMessage("invalid_generation").contains("required format"))
        assertEquals(
            "Deck generation timed out. Please try again.",
            generationErrorMessage("generation_timeout"),
        )
        assertTrue(generationErrorMessage("provider_unavailable").contains("temporarily unavailable"))
    }

    @Test
    fun `editor rejects duplicate ranking options`() {
        val questions = List(3) { index ->
            DeckQuestion(
                id = "q${index + 1}",
                prompt = "Question ${index + 1}",
                options = listOf("One", "one", "Three", "Four", "Five"),
            )
        }

        assertEquals(
            "Question 1 needs 5 different options",
            validateEditor("Deck", "🃏", questions),
        )
    }

    @Test
    fun `generated response keeps draft separate from saved deck`() {
        val response = DeckGenerationResponse(
            generationId = "generation-1",
            draft = GeneratedDeckDraft(
                title = "Weekend",
                questions = listOf(DeckQuestion(id = "q1", prompt = "Pick", options = listOf("A", "B", "C", "D", "E"))),
            ),
        )

        assertEquals("generation-1", response.generationId)
        assertEquals("Weekend", response.draft.title)
    }
}
