package io.rank5.app.deck

import io.rank5.app.BuildConfig
import io.rank5.app.auth.AuthRepository
import io.rank5.app.auth.AuthState

class DeckRepository(
    private val auth: AuthRepository,
    private val api: DeckApi = DeckApi(BuildConfig.SERVER_BASE_URL),
) {
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
        api.save(token, id)
    }

    suspend fun unsave(id: String) {
        val token = currentToken() ?: error("Sign in to manage saved decks")
        api.unsave(token, id)
    }

    suspend fun searchCommunity(q: String, offset: Int = 0): List<DeckSummary> =
        api.searchCommunity(q, limit = 20, offset = offset)

    suspend fun get(id: String): Deck {
        return api.get(id, currentToken())
    }

    suspend fun create(title: String, emoji: String, questions: List<DeckQuestion>): Deck {
        val token = currentToken() ?: error("Sign in to create decks")
        return api.create(token, DeckWriteRequest(title, emoji, questions))
    }

    suspend fun generate(topic: String, questionCount: Int): DeckGenerationResponse {
        val token = currentToken() ?: error("Sign in to generate decks")
        return api.generate(token, DeckGenerationRequest(topic.trim(), questionCount))
    }

    suspend fun update(id: String, title: String, emoji: String, questions: List<DeckQuestion>): Deck {
        val token = currentToken() ?: error("Sign in to edit decks")
        return api.update(token, id, DeckWriteRequest(title, emoji, questions))
    }

    suspend fun delete(id: String) {
        val token = currentToken() ?: error("Sign in to delete decks")
        api.delete(token, id)
    }

    suspend fun publish(id: String): Deck {
        val token = currentToken() ?: error("Sign in to publish decks")
        return api.publish(token, id)
    }

    suspend fun unpublish(id: String): Deck {
        val token = currentToken() ?: error("Sign in to unpublish decks")
        return api.unpublish(token, id)
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
