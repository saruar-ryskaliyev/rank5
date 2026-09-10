package io.rank5.app.offline

import io.rank5.app.deck.Deck
import io.rank5.app.deck.DeckQuestion
import io.rank5.app.deck.QuestionKindPlayers
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

    private val playersDeck = Deck(
        id = "most-likely",
        title = "Most likely to",
        questions = (1..6).map { index ->
            DeckQuestion(
                id = "ml$index",
                prompt = "Who is most likely to do thing $index?",
                options = emptyList(),
                kind = QuestionKindPlayers,
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

    @Test
    fun `score formula spans the same range for a three-player ranking`() {
        val actual = listOf("Maya", "Noah", "Iris")
        assertEquals(2_000, OfflineGameEngine.scorePrediction(actual, actual))
        assertEquals(1_400, OfflineGameEngine.scorePrediction(actual, actual.reversed()))
    }

    @Test
    fun `subject placeholder is replaced with each round's spotlight player`() {
        val personal = deck.copy(
            id = "personal",
            questions = deck.questions.map { it.copy(prompt = "What would {subject} grab first?") },
        )
        val engine = OfflineGameEngine(Random(11))
        val state = engine.start(listOf("Maya", "Noah"), listOf(personal), setOf(personal.id), 2)

        assertEquals("What would Maya grab first?", state.schedule[0].prompt)
        assertEquals("What would Noah grab first?", state.schedule[1].prompt)
    }

    @Test
    fun `player questions rank the group`() {
        val engine = OfflineGameEngine(Random(12))
        var state = engine.start(
            listOf("Maya", "Noah", "Iris"),
            listOf(playersDeck),
            setOf(playersDeck.id),
            3,
        )

        val question = requireNotNull(state.currentQuestion)
        assertTrue(question.usesPlayersAsOptions)
        assertEquals(listOf("Iris", "Maya", "Noah"), question.options.sorted())

        // The rendered options are rankable and score through the normal path.
        state = engine.revealToActor(state)
        state = engine.reorder(state, question.options)
        state = engine.lockIn(state)
        assertEquals(OfflineScreen.Handoff, state.screen)
    }

    @Test
    fun `player questions are dropped below three players`() {
        val mixed = Deck(
            id = "mixed",
            title = "Mixed",
            questions = listOf(
                DeckQuestion(id = "p1", prompt = "Who is most likely to oversleep?", options = emptyList(), kind = QuestionKindPlayers),
                DeckQuestion(id = "o1", prompt = "Best breakfast?", options = listOf("A", "B", "C", "D", "E")),
            ),
        )
        val state = OfflineGameEngine(Random(13))
            .start(listOf("Maya", "Noah"), listOf(mixed), setOf(mixed.id), 2)

        assertEquals(1, state.schedule.size)
        assertEquals("o1", state.schedule.single().id)
    }

    @Test
    fun `a player-only deck cannot start with two players`() {
        val engine = OfflineGameEngine(Random(14))
        val error = runCatching {
            engine.start(listOf("Maya", "Noah"), listOf(playersDeck), setOf(playersDeck.id), 3)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error?.message.orEmpty(),
            error?.message.orEmpty().contains("at least 3"),
        )
    }

    @Test
    fun `setup warns about player questions only for small groups`() {
        val base = OfflineGameState(decks = listOf(playersDeck), selectedDeckIds = setOf(playersDeck.id))

        val twoPlayers = base.copy(playerNames = listOf("Maya", "Noah"))
        assertTrue(offlinePlayerQuestionWarning(twoPlayers).orEmpty().contains("at least 3"))
        assertEquals(0, twoPlayers.availableQuestionCount)

        val threePlayers = base.copy(playerNames = listOf("Maya", "Noah", "Iris"))
        assertNull(offlinePlayerQuestionWarning(threePlayers))
        assertEquals(playersDeck.questions.size, threePlayers.availableQuestionCount)
    }

    @Test
    fun `duplicate names still produce distinct ranking options`() {
        val players = listOf(
            OfflinePlayer("local-0", "Sam"),
            OfflinePlayer("local-1", "Sam"),
            OfflinePlayer("local-2", "Sam"),
        )
        val names = OfflineGameEngine.distinctPlayerNames(players)
        assertEquals(3, names.distinct().size)
    }
}
