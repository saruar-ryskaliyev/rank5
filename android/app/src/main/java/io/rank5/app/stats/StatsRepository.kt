package io.rank5.app.stats

import io.rank5.app.BuildConfig
import io.rank5.app.auth.AuthRepository
import io.rank5.app.auth.AuthState
import io.rank5.app.net.ServerEndpoints
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay

class StatsRepository(
    private val auth: AuthRepository,
    private val api: StatsApi = StatsApi(ServerEndpoints.baseUrl(BuildConfig.DEBUG)),
) {
    suspend fun load(): StatsResponse = api.getStats(requireToken())

    suspend fun claim(claim: GuestResultClaim): Int {
        val token = requireToken()
        repeat(CLAIM_ATTEMPTS) { attempt ->
            try {
                return api.claim(token, claim).claimedGames
            } catch (error: ClientRequestException) {
                val resultMayStillBePersisting = error.response.status == HttpStatusCode.NotFound
                if (!resultMayStillBePersisting || attempt == CLAIM_ATTEMPTS - 1) throw error
                delay(CLAIM_RETRY_DELAYS_MS[attempt])
            }
        }
        error("Claim attempts exhausted")
    }

    private fun requireToken(): String = when (val state = auth.state.value) {
        is AuthState.SignedIn -> state.token
        AuthState.SignedOut -> error("Sign in to view stats")
    }

    fun close() = api.close()

    private companion object {
        const val CLAIM_ATTEMPTS = 4
        val CLAIM_RETRY_DELAYS_MS = listOf(200L, 400L, 800L)
    }
}
