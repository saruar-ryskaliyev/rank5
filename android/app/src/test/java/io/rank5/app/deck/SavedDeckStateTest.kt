package io.rank5.app.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedDeckStateTest {
    @Test fun `failed save clears inline progress and keeps original saved list`() {
        val original = DeckSummary("food", "Food")
        val started = savedOperationStarted(DecksUiState(saved = listOf(original)), "movies")
        assertTrue("movies" in started.savedOperations)

        val failed = savedOperationFailed(started, "movies", wasSaved = false)

        assertFalse("movies" in failed.savedOperations)
        assertEquals(listOf(original), failed.saved)
        assertEquals("Couldn’t save this deck. Try again.", failed.statusMessage)
    }

    @Test fun `successful unsave replaces authoritative list`() {
        val food = DeckSummary("food", "Food")
        val movies = DeckSummary("movies", "Movies")
        val started = savedOperationStarted(DecksUiState(saved = listOf(food, movies)), "movies")

        val done = savedOperationSucceeded(started, "movies", listOf(food), wasSaved = true)

        assertEquals(listOf("food"), done.saved.map { it.id })
        assertTrue(done.savedOperations.isEmpty())
    }
}
