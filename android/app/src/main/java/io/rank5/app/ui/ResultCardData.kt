package io.rank5.app.ui

import io.rank5.app.game.UiState
import io.rank5.app.net.canonicalSelectedDecks

data class ResultStanding(
    val rank: Int,
    val nickname: String,
    val score: Int,
)

data class ResultCardData(
    val mode: String,
    val deckName: String,
    val deckEmoji: String,
    val deckNames: List<String>,
    val rounds: Int,
    val teamScore: Int,
    val maxTeamScore: Int,
    val roundScores: List<Int>,
    val standings: List<ResultStanding>,
    val morePlayers: Int,
) {
    companion object {
        private const val MAX_SCORE_PER_ROUND = 2000
        private const val MAX_SHARED_STANDINGS = 5

        fun from(state: UiState): ResultCardData {
            val room = requireNotNull(state.room) { "A finished room is required for a result card" }
            val decks = room.canonicalSelectedDecks()
            val deck = decks.singleOrNull()
            val sortedPlayers = room.players.sortedByDescending { it.score }
            return ResultCardData(
                mode = room.mode,
                deckName = deck?.name ?: "${decks.size}-deck mix",
                deckEmoji = deck?.emoji?.ifBlank { "🃏" } ?: "🃏",
                deckNames = decks.map { it.name },
                rounds = room.totalRounds,
                teamScore = room.teamScore,
                maxTeamScore = room.totalRounds * MAX_SCORE_PER_ROUND,
                roundScores = state.roundHistory.sortedBy { it.index }.map { it.teamScore },
                standings = sortedPlayers.take(MAX_SHARED_STANDINGS).mapIndexed { index, player ->
                    ResultStanding(index + 1, player.nickname, player.score)
                },
                morePlayers = (sortedPlayers.size - MAX_SHARED_STANDINGS).coerceAtLeast(0),
            )
        }
    }
}
