package io.rank5.app.deck

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class BundledDeck(
    val id: String,
    val name: String,
    val emoji: String = "🃏",
    val questions: List<DeckQuestion>,
)

/** Persistent device-side snapshots for the unified deck library. */
class DownloadedDeckStore(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val directory = File(appContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val snapshots = DeckSnapshotFiles(directory, json)
    private val builtIns: List<Deck> = loadBuiltIns()
    private val builtInIds = builtIns.mapTo(hashSetOf()) { it.id }
    private val _decks = MutableStateFlow(loadAll())

    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    @Synchronized
    fun put(deck: Deck) {
        if (deck.isBuiltin || deck.id in builtInIds) return
        snapshots.put(deck)
        refresh()
    }

    @Synchronized
    fun remove(id: String): Boolean {
        if (id in builtInIds) return false
        val removed = snapshots.remove(id)
        refresh()
        return removed
    }

    fun get(id: String): Deck? = decks.value.firstOrNull { it.id == id }

    @Synchronized
    private fun refresh() {
        _decks.value = loadAll()
    }

    private fun loadAll(): List<Deck> {
        val downloaded = snapshots.load()
        return (builtIns + downloaded).distinctBy { it.id }
    }

    private fun loadBuiltIns(): List<Deck> = BUILT_IN_FILES.mapNotNull { file ->
        runCatching {
            val wire = appContext.assets.open(file).bufferedReader().use { reader ->
                json.decodeFromString<BundledDeck>(reader.readText())
            }
            Deck(
                id = wire.id,
                title = wire.name,
                emoji = wire.emoji,
                questions = wire.questions,
                isBuiltin = true,
                visibility = "public",
                ownerName = "Rank5",
            )
        }.getOrNull()
    }

    private companion object {
        const val DIRECTORY_NAME = "downloaded-decks"
        val BUILT_IN_FILES = listOf(
            "food.json",
            "movies.json",
            "personality.json",
            "would_you_rather.json",
        )
    }
}

/** File persistence kept Android-free so corruption and restart behavior are unit-testable. */
internal class DeckSnapshotFiles(
    private val directory: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    init {
        directory.mkdirs()
    }

    fun put(deck: Deck) {
        val target = fileFor(deck.id)
        val temporary = File(directory, ".${target.name}.tmp")
        temporary.writeText(json.encodeToString(deck))
        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    }

    fun remove(id: String): Boolean = fileFor(id).delete()

    fun load(): List<Deck> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == "json" }
        .mapNotNull { file ->
            runCatching { json.decodeFromString<Deck>(file.readText()) }.getOrNull()
        }

    private fun fileFor(id: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }
}
