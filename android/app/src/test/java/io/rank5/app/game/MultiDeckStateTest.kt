package io.rank5.app.game

import io.rank5.app.net.DeckInfo
import io.rank5.app.net.RoomStateView
import io.rank5.app.net.canonicalDeckIds
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiDeckStateTest {
    private fun deck(id: Int, questions: Int = 4) = DeckInfo("d$id", "Deck $id", "🃏", questions)

    @Test fun `selection rejects duplicates and a sixth deck`() {
        var selected = emptyList<DeckInfo>()
        (1..5).forEach { selected = addDeckSelection(selected, deck(it)) }
        selected = addDeckSelection(selected, deck(3))
        selected = addDeckSelection(selected, deck(6))

        assertEquals(listOf("d1", "d2", "d3", "d4", "d5"), selected.map { it.id })
    }

    @Test fun `selection always retains one deck`() {
        val one = listOf(deck(1))
        assertEquals(one, removeDeckSelection(one, "d1"))
        assertEquals(listOf("d1"), removeDeckSelection(one + deck(2), "d2").map { it.id })
    }

    @Test fun `combined count clamps rounds at available choices and twenty`() {
        assertEquals(12, combinedQuestionCount(listOf(deck(1, 5), deck(2, 7))))
        assertEquals(10, clampRounds(20, 12))
        assertEquals(20, clampRounds(20, 50))
    }

    @Test fun `legacy room payload falls back to deckId`() {
        val room = RoomStateView(code = "ABCD", phase = "LOBBY", deckId = "food")
        assertEquals(listOf("food"), room.canonicalDeckIds())
    }

    @Test fun `selected deck metadata survives saved state serialization`() {
        val selected = listOf(deck(1, 7), deck(2, 9))
        val restored = decodeDeckSelection(encodeDeckSelection(selected))

        assertEquals(selected, restored)
    }
}
