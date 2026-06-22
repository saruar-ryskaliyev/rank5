package io.rank5.app.stats

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class StatsApi(private val baseUrl: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
    }

    suspend fun getStats(token: String): StatsResponse =
        http.get("$baseUrl/me/stats") {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun claim(token: String, claim: GuestResultClaim): ClaimResultResponse =
        http.post("$baseUrl/me/results/claim") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                ClaimResultRequest(
                    roomCode = claim.roomCode,
                    playerId = claim.playerId,
                    reconnectToken = claim.reconnectToken,
                ),
            )
        }.body()

    fun close() = http.close()
}
