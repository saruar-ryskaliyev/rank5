package io.rank5.app.deck

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckLibraryPresentationTest {
    @Test
    fun `official decks are attributed to Rank5`() {
        val deck = DeckSummary(
            id = "food",
            title = "Food Favorites",
            isBuiltin = true,
            questionCount = 6,
        )

        assertEquals("Rank5", deck.creatorName)
        assertEquals("6 questions · by Rank5", deck.libraryMetadata)
    }

    @Test
    fun `community decks show their creator`() {
        val deck = DeckSummary(
            id = "party",
            title = "Party",
            ownerName = "Maya",
            questionCount = 8,
            visibility = "public",
        )

        assertEquals("Maya", deck.creatorName)
        assertEquals("8 questions · by Maya", deck.libraryMetadata)
    }

    @Test
    fun `unified library puts owned decks first and removes published duplicates`() {
        val mine = listOf(
            DeckSummary(id = "mine", title = "My deck", ownerName = "Me"),
        )
        val public = listOf(
            DeckSummary(id = "official", title = "Official", isBuiltin = true),
            mine.single().copy(visibility = "public"),
            DeckSummary(id = "community", title = "Community", ownerName = "Maya"),
        )

        val result = unifiedDeckLibrary(mine, public)

        assertEquals(listOf("mine", "official", "community"), result.map { it.id })
    }
}
