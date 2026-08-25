package io.rank5.app.offline

import io.rank5.app.deck.Deck
import io.rank5.app.deck.DeckQuestion
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGameEngineTest {
    private val deck = Deck(
        id = "test",
        title = "Test deck",
        questions = (1..6).map { index ->
            DeckQuestion(
                id = "q$index",
                prompt = "Question $index",
                options = listOf("A", "B", "C", "D", "E"),
            )
        },
    )

    @Test
    fun `private handoffs separate every ranking and preserve actor order`() {
        val engine = OfflineGameEngine(Random(4))
        var state = engine.start(listOf("Maya", "Noah", "Iris"), listOf(deck), setOf(deck.id), 3)

        assertEquals(OfflineScreen.Handoff, state.screen)
        assertEquals("Maya", state.subject?.name)
        assertEquals("Maya", state.actor?.name)

        state = engine.revealToActor(state)
        state = engine.reorder(state, listOf("A", "B", "C", "D", "E"))
        state = engine.lockIn(state)
        assertEquals(OfflineScreen.Handoff, state.screen)
        assertEquals("Noah", state.actor?.name)

        state = engine.revealToActor(state)
        state = engine.reorder(state, listOf("A", "B", "C", "D", "E"))
        state = engine.lockIn(state)
        assertEquals("Iris", state.actor?.name)

        state = engine.revealToActor(state)
        state = engine.reorder(state, listOf("E", "D", "C", "B", "A"))
        state = engine.lockIn(state)
        assertEquals(OfflineScreen.Reveal, state.screen)
        assertEquals(2, state.currentResult?.predictions?.size)
        assertEquals(1_700, state.currentResult?.teamScore)
    }

    @Test
    fun `subjects rotate and final reveal advances to results`() {
        val engine = OfflineGameEngine(Random(2))
        var state = engine.start(listOf("Maya", "Noah"), listOf(deck), setOf(deck.id), 3)

        repeat(2) {
            state = engine.revealToActor(state)
            state = engine.lockIn(state)
        }
        assertEquals(OfflineScreen.Reveal, state.screen)
        state = engine.nextRound(state)
        assertEquals("Noah", state.subject?.name)

        repeat(2) {
            state = engine.revealToActor(state)
            state = engine.lockIn(state)
        }
        state = engine.nextRound(state)
        assertEquals("Maya", state.subject?.name)

        repeat(2) {
            state = engine.revealToActor(state)
            state = engine.lockIn(state)
        }
        state = engine.nextRound(state)
        assertEquals(OfflineScreen.Results, state.screen)
        assertEquals(3, state.history.size)
    }

    @Test
    fun `schedule rotates selected decks and never repeats a question`() {
        val second = deck.copy(
            id = "second",
            title = "Second",
            questions = deck.questions.map { it.copy(id = "second-${it.id}") },
        )
        val schedule = OfflineGameEngine(Random(8)).balancedSchedule(listOf(deck, second), 10)

        assertEquals(10, schedule.size)
        assertEquals(10, schedule.map { "${it.deckId}:${it.id}" }.distinct().size)
        assertEquals(setOf("test", "second"), schedule.take(2).map { it.deckId }.toSet())
    }

    @Test
    fun `player validation rejects blanks and duplicate names`() {
        assertEquals("Enter a name for every player.", offlinePlayerValidation(listOf("Maya", "")))
        assertEquals(
            "Player names must be different.",
            offlinePlayerValidation(listOf("Maya", " maya ")),
        )
        assertNull(offlinePlayerValidation(listOf("Maya", "Noah")))
        assertTrue(offlinePlayerValidation(listOf("Only one")) != null)
    }

    @Test
    fun `score formula matches online game`() {
        val actual = listOf("A", "B", "C", "D", "E")
        assertEquals(2_000, OfflineGameEngine.scorePrediction(actual, actual))
        assertEquals(1_400, OfflineGameEngine.scorePrediction(actual, actual.reversed()))
    }
}
