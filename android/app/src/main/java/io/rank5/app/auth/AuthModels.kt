package io.rank5.app.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String,
    val displayName: String,
    val avatarSeed: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: AuthUser,
)

@Serializable
data class GoogleAuthRequest(val idToken: String)

sealed class AuthState {
    data object SignedOut : AuthState()
    data class SignedIn(val token: String, val user: AuthUser) : AuthState()
}
