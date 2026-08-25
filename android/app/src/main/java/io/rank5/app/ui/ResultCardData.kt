package io.rank5.app.ui

import io.rank5.app.game.UiState
import io.rank5.app.net.canonicalSelectedDecks

data class ResultCardData(
    val deckName: String,
    val deckEmoji: String,
    val deckNames: List<String>,
    val rounds: Int,
    val teamScore: Int,
    val maxTeamScore: Int,
    val roundScores: List<Int>,
) {
    companion object {
        private const val MAX_SCORE_PER_ROUND = 2000

        fun from(state: UiState): ResultCardData {
            val room = requireNotNull(state.room) { "A finished room is required for a result card" }
            val decks = room.canonicalSelectedDecks()
            val deck = decks.singleOrNull()
            return ResultCardData(
                deckName = deck?.name ?: "${decks.size}-deck mix",
                deckEmoji = deck?.emoji?.ifBlank { "🃏" } ?: "🃏",
                deckNames = decks.map { it.name },
                rounds = room.totalRounds,
                teamScore = room.teamScore,
                maxTeamScore = room.totalRounds * MAX_SCORE_PER_ROUND,
                roundScores = state.roundHistory.sortedBy { it.index }.map { it.teamScore },
            )
        }
    }
}
