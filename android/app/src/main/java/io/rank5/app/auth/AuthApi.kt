package io.rank5.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AuthApi(private val baseUrl: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun signInWithGoogle(idToken: String): AuthResponse =
        http.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleAuthRequest(idToken))
        }.body()

    suspend fun me(token: String): AuthUser =
        http.get("$baseUrl/me") {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun deleteMe(token: String) {
        http.delete("$baseUrl/me") {
            header("Authorization", "Bearer $token")
        }
    }

    fun close() = http.close()
}
