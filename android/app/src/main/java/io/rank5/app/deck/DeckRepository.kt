package io.rank5.app.deck

import io.rank5.app.BuildConfig
import io.rank5.app.auth.AuthRepository
import io.rank5.app.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class DeckRepository(
    private val auth: AuthRepository,
    private val downloads: DownloadedDeckStore,
    private val api: DeckApi = DeckApi(BuildConfig.SERVER_BASE_URL),
) {
    val downloadedDecks: StateFlow<List<Deck>> = downloads.decks

    suspend fun listBuiltins(): List<DeckSummary> = api.listBuiltins()

    suspend fun listMine(): List<DeckSummary> {
        val token = currentToken() ?: return emptyList()
        return api.listMine(token)
    }

    suspend fun listSaved(): List<DeckSummary> {
        val token = currentToken() ?: return emptyList()
        return api.listSaved(token)
    }

    suspend fun save(id: String) {
        val token = currentToken() ?: error("Sign in to save decks")
        val deck = api.get(id, token)
        api.save(token, id)
        withContext(Dispatchers.IO) { downloads.put(deck) }
    }

    suspend fun unsave(id: String) {
        val token = currentToken() ?: error("Sign in to manage saved decks")
        api.unsave(token, id)
        removeDownload(id)
    }

    suspend fun searchCommunity(q: String, offset: Int = 0): List<DeckSummary> =
        api.searchCommunity(q, limit = 20, offset = offset)

    suspend fun get(id: String): Deck {
        downloads.get(id)?.let { return it }
        return api.get(id, currentToken())
    }

    suspend fun download(id: String): Deck {
        val deck = api.get(id, currentToken())
        withContext(Dispatchers.IO) { downloads.put(deck) }
        return deck
    }

    suspend fun removeDownload(id: String) {
        withContext(Dispatchers.IO) { downloads.remove(id) }
    }

    suspend fun create(title: String, emoji: String, questions: List<DeckQuestion>): Deck {
        val token = currentToken() ?: error("Sign in to create decks")
        val deck = api.create(token, DeckWriteRequest(title, emoji, questions))
        withContext(Dispatchers.IO) { downloads.put(deck) }
        return deck
    }

    suspend fun generate(topic: String, questionCount: Int): DeckGenerationResponse {
        val token = currentToken() ?: error("Sign in to generate decks")
        return api.generate(token, DeckGenerationRequest(topic.trim(), questionCount))
    }

    suspend fun update(id: String, title: String, emoji: String, questions: List<DeckQuestion>): Deck {
        val token = currentToken() ?: error("Sign in to edit decks")
        val deck = api.update(token, id, DeckWriteRequest(title, emoji, questions))
        withContext(Dispatchers.IO) { downloads.put(deck) }
        return deck
    }

    suspend fun delete(id: String) {
        val token = currentToken() ?: error("Sign in to delete decks")
        api.delete(token, id)
        withContext(Dispatchers.IO) { downloads.remove(id) }
    }

    suspend fun publish(id: String): Deck {
        val token = currentToken() ?: error("Sign in to publish decks")
        val deck = api.publish(token, id)
        if (downloads.get(id) != null) withContext(Dispatchers.IO) { downloads.put(deck) }
        return deck
    }

    suspend fun unpublish(id: String): Deck {
        val token = currentToken() ?: error("Sign in to unpublish decks")
        val deck = api.unpublish(token, id)
        if (downloads.get(id) != null) withContext(Dispatchers.IO) { downloads.put(deck) }
        return deck
    }

    suspend fun report(id: String, reason: String) {
        val token = currentToken() ?: error("Sign in to report decks")
        api.report(token, id, reason)
    }

    fun isSignedIn(): Boolean = auth.state.value is AuthState.SignedIn

    private fun currentToken(): String? = when (val s = auth.state.value) {
        is AuthState.SignedIn -> s.token
        AuthState.SignedOut -> null
    }
}
