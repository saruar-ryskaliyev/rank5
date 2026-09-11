package io.rank5.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.rank5.app.BuildConfig
import io.rank5.app.net.ServerEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.authDataStore by preferencesDataStore(name = "rank5_auth")

class AuthRepository(
    private val context: Context,
    private val api: AuthApi = AuthApi(ServerEndpoints.baseUrl(BuildConfig.DEBUG)),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenKey = stringPreferencesKey("jwt")
    private val userKey = stringPreferencesKey("user_json")

    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun restore() {
        val prefs = context.authDataStore.data.first()
        val token = prefs[tokenKey] ?: return
        val userJson = prefs[userKey] ?: return
        try {
            val user = json.decodeFromString<AuthUser>(userJson)
            // Refresh profile; drop session if token is invalid.
            val fresh = runCatching { api.me(token) }.getOrNull()
            if (fresh != null) {
                persist(token, fresh)
                _state.value = AuthState.SignedIn(token, fresh)
            } else {
                // Keep cached identity offline; server will reject joins with bad token as anonymous.
                _state.value = AuthState.SignedIn(token, user)
            }
        } catch (_: Exception) {
            clear()
        }
    }

    suspend fun signInWithGoogle(activityContext: Context) {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            throw IllegalStateException("GOOGLE_WEB_CLIENT_ID is not configured")
        }
        // Explicit button → Sign in with Google dialog (can add an account if none exist).
        // GetGoogleIdOption bottomsheet only lists accounts already on the device.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(clientId)
                    .setNonce(UUID.randomUUID().toString())
                    .build(),
            )
            .build()
        val cm = CredentialManager.create(activityContext)
        val result = try {
            cm.getCredential(activityContext, request)
        } catch (e: GetCredentialException) {
            throw IllegalStateException(e.message ?: "Google sign-in failed", e)
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Unexpected credential type")
        }
        val googleId = GoogleIdTokenCredential.createFrom(credential.data)
        val resp = api.signInWithGoogle(googleId.idToken)
        persist(resp.token, resp.user)
        _state.value = AuthState.SignedIn(resp.token, resp.user)
    }

    suspend fun signOut() {
        clear()
    }

    suspend fun deleteAccount() {
        val current = _state.value as? AuthState.SignedIn ?: return
        api.deleteMe(current.token)
        clear()
    }

    fun currentToken(): String? =
        (_state.value as? AuthState.SignedIn)?.token

    private suspend fun persist(token: String, user: AuthUser) {
        context.authDataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[userKey] = json.encodeToString(user)
        }
    }

    private suspend fun clear() {
        context.authDataStore.edit { it.clear() }
        _state.value = AuthState.SignedOut
    }
}
