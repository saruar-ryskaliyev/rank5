package io.rank5.app.deck

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class DeckApi(private val baseUrl: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout)
        install(ContentNegotiation) { json(json) }
    }

    suspend fun listBuiltins(): List<DeckSummary> =
        http.get("$baseUrl/decks").body()

    suspend fun listMine(token: String): List<DeckSummary> =
        http.get("$baseUrl/me/decks") {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun listSaved(token: String): List<DeckSummary> =
        http.get("$baseUrl/me/saved-decks") {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun save(token: String, id: String) {
        val response = http.put("$baseUrl/me/saved-decks/$id") {
            header("Authorization", "Bearer $token")
        }
        if (response.status.value !in 200..299) error("Save failed")
    }

    suspend fun unsave(token: String, id: String) {
        val response = http.delete("$baseUrl/me/saved-decks/$id") {
            header("Authorization", "Bearer $token")
        }
        if (response.status.value !in 200..299) error("Unsave failed")
    }

    suspend fun searchCommunity(q: String, limit: Int = 20, offset: Int = 0): List<DeckSummary> =
        http.get("$baseUrl/community/decks") {
            if (q.isNotBlank()) parameter("q", q)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun get(id: String, token: String?): Deck {
        return http.get("$baseUrl/decks/$id") {
            if (!token.isNullOrBlank()) {
                header("Authorization", "Bearer $token")
            }
        }.body()
    }

    suspend fun create(token: String, body: DeckWriteRequest): Deck =
        http.post("$baseUrl/decks") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun generate(token: String, body: DeckGenerationRequest): DeckGenerationResponse {
        val response = try {
            http.post("$baseUrl/me/deck-generations") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
                timeout {
                    connectTimeoutMillis = GENERATION_CONNECT_TIMEOUT_MILLIS
                    socketTimeoutMillis = GENERATION_TIMEOUT_MILLIS
                    requestTimeoutMillis = GENERATION_TIMEOUT_MILLIS
                }
            }
        } catch (error: HttpRequestTimeoutException) {
            throw generationTimeout(error)
        } catch (error: SocketTimeoutException) {
            throw generationTimeout(error)
        } catch (error: java.net.SocketTimeoutException) {
            throw generationTimeout(error)
        }
        if (response.status.value in 200..299) return response.body()
        val apiError = runCatching { response.body<DeckApiError>() }.getOrNull()
        throw DeckGenerationException(
            code = apiError?.code.orEmpty().ifBlank { "generation_failed" },
            message = apiError?.error.orEmpty().ifBlank { "Deck generation failed" },
        )
    }

    private fun generationTimeout(cause: Throwable) = DeckGenerationException(
        code = "generation_timeout",
        message = "Deck generation timed out",
        cause = cause,
    )

    suspend fun update(token: String, id: String, body: DeckWriteRequest): Deck =
        http.put("$baseUrl/decks/$id") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun delete(token: String, id: String) {
        val resp = http.delete("$baseUrl/decks/$id") {
            header("Authorization", "Bearer $token")
        }
        if (resp.status != HttpStatusCode.NoContent && resp.status.value !in 200..299) {
            error("Delete failed: ${resp.status}")
        }
    }

    suspend fun publish(token: String, id: String): Deck =
        http.post("$baseUrl/decks/$id/publish") {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun unpublish(token: String, id: String): Deck =
        http.post("$baseUrl/decks/$id/unpublish") {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun report(token: String, id: String, reason: String) {
        val resp = http.post("$baseUrl/decks/$id/report") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ReportRequest(reason))
        }
        when (resp.status) {
            HttpStatusCode.Created, HttpStatusCode.OK -> Unit
            HttpStatusCode.Conflict -> error("You already reported this deck")
            else -> error("Report failed: ${resp.status}")
        }
    }

    fun close() = http.close()
}

private const val GENERATION_CONNECT_TIMEOUT_MILLIS = 10_000L
private const val GENERATION_TIMEOUT_MILLIS = 60_000L
