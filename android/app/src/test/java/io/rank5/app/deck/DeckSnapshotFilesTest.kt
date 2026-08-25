package io.rank5.app.deck

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckSnapshotFilesTest {
    @Test
    fun `downloaded deck persists across cache instances and can be removed`() {
        val directory = Files.createTempDirectory("rank5-deck-cache").toFile()
        try {
            val deck = sampleDeck("community-1")
            DeckSnapshotFiles(directory).put(deck)

            val restored = DeckSnapshotFiles(directory).load()
            assertEquals(listOf(deck), restored)
            assertTrue(DeckSnapshotFiles(directory).remove(deck.id))
            assertTrue(DeckSnapshotFiles(directory).load().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `corrupt snapshot is ignored without hiding valid downloads`() {
        val directory = Files.createTempDirectory("rank5-deck-cache").toFile()
        try {
            DeckSnapshotFiles(directory).put(sampleDeck("valid"))
            directory.resolve("broken.json").writeText("not json")
            directory.resolve("unfinished.tmp").writeText("temporary")

            val restored = DeckSnapshotFiles(directory).load()
            assertEquals(listOf("valid"), restored.map { it.id })
            assertFalse(restored.any { it.id == "broken" })
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun sampleDeck(id: String) = Deck(
        id = id,
        title = "Downloaded deck",
        emoji = "📥",
        questions = listOf(
            DeckQuestion(
                id = "q1",
                prompt = "Rank these",
                options = listOf("A", "B", "C", "D", "E"),
            ),
        ),
        visibility = "public",
        ownerName = "Creator",
    )
}
