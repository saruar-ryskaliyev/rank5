package io.rank5.app.stats

import kotlinx.serialization.Serializable

@Serializable
data class StatsResponse(
    val gamesPlayed: Int,
    val versus: VersusStats,
    val coop: CoopStats,
    val guessing: GuessingStats? = null,
    val superlatives: SuperlativeStats,
)

@Serializable
data class VersusStats(
    val played: Int,
    val wins: Int,
    val tiesForFirst: Int,
    val winRatePct: Int,
)

@Serializable
data class CoopStats(
    val played: Int,
    val bestByRounds: List<CoopBestStat> = emptyList(),
)

@Serializable
data class CoopBestStat(
    val rounds: Int,
    val score: Int,
    val maxScore: Int,
)

@Serializable
data class GuessingStats(
    val accuracyPct: Int,
    val predictionsTracked: Int,
)

@Serializable
data class SuperlativeStats(
    val bestGuesser: Int,
    val mostPredictable: Int,
    val detailedGamesTracked: Int,
)

@Serializable
data class ClaimResultRequest(
    val roomCode: String,
    val playerId: String,
    val reconnectToken: String,
)

@Serializable
data class ClaimResultResponse(val claimedGames: Int)

data class GuestResultClaim(
    val roomCode: String,
    val playerId: String,
    val reconnectToken: String,
)
