package io.rank5.app.deck

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The APK bundles the server's deck JSON verbatim (see the assets srcDir in
 * build.gradle.kts), so a field the app decodes differently from the server
 * would only surface at runtime. This checks the shared files decode into the
 * shapes Pass & Play expects.
 */
class BundledDeckJsonTest {
    @Serializable
    private data class BundledDeck(
        val id: String,
        val name: String,
        val emoji: String = "🃏",
        val questions: List<DeckQuestion>,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val dataDirectory: File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "server/internal/decks/data") }
        .firstOrNull { it.isDirectory }

    private fun deck(file: String): BundledDeck {
        val directory = requireNotNull(dataDirectory)
        return json.decodeFromString(File(directory, file).readText())
    }

    @Test fun `every bundled file listed by the store decodes`() {
        assumeTrue(dataDirectory != null)
        DownloadedDeckStore.BUILT_IN_FILES.forEach { file ->
            val decoded = deck(file)
            assertTrue(file, decoded.questions.isNotEmpty())
            assertTrue(file, decoded.id.isNotBlank() && decoded.name.isNotBlank())
        }
    }

    @Test fun `player questions decode with no options`() {
        assumeTrue(dataDirectory != null)
        val decoded = deck("most_likely.json")
        decoded.questions.forEach { question ->
            assertTrue(question.id, question.usesPlayersAsOptions)
            // A missing options field would fall back to five blank strings.
            assertEquals(question.id, emptyList<String>(), question.options)
        }
    }

    @Test fun `personalized questions keep the subject placeholder and five options`() {
        assumeTrue(dataDirectory != null)
        val decoded = deck("fact_check.json")
        assertTrue(decoded.questions.any { it.prompt.contains("{subject}") })
        decoded.questions.forEach { question ->
            assertEquals(question.id, QuestionKindOptions, question.kind)
            assertEquals(question.id, 5, question.options.size)
            assertTrue(question.id, question.options.none { it.isBlank() })
        }
    }
}
