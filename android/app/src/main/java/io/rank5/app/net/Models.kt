package io.rank5.app.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Envelope(
    val type: String,
    val payload: JsonElement? = null,
)

@Serializable
data class CreateRoomResponse(val code: String)

@Serializable
data class JoinRoomPayload(
    val nickname: String,
    val authToken: String? = null,
)

@Serializable
data class ReconnectPayload(
    val playerId: String,
    val reconnectToken: String,
)

@Serializable
data class AttachAccountPayload(val authToken: String)

@Serializable
data class StartGamePayload(
    val mode: String,
    val deckIds: List<String>,
    val deckId: String = deckIds.firstOrNull().orEmpty(),
    val rounds: Int,
)

typealias UpdateGameSettingsPayload = StartGamePayload

@Serializable
data class RankingPayload(
    val roundIndex: Int,
    val questionId: String,
    val ranking: List<String>,
)

@Serializable
data class SkipQuestionPayload(
    val roundIndex: Int,
    val questionId: String,
)

@Serializable
data class ErrorPayload(val message: String)

@Serializable
data class WelcomePayload(
    val playerId: String,
    val reconnectToken: String,
    val isHost: Boolean,
)

@Serializable
data class DeckInfo(
    val id: String,
    val name: String,
    val emoji: String = "🃏",
    val questionCount: Int = 0,
)

@Serializable
data class Question(
    val id: String,
    val deckId: String = "",
    val prompt: String,
    val options: List<String>,
)

@Serializable
data class PlayerView(
    val id: String,
    val nickname: String,
    val isHost: Boolean,
    val connected: Boolean,
    val score: Int,
)

@Serializable
data class RoundView(
    val index: Int,
    val subjectId: String,
    val question: Question,
    val submitted: Map<String, Boolean> = emptyMap(),
    val ready: Map<String, Boolean> = emptyMap(),
    val deadlineMs: Long? = null,
    val myRanking: List<String>? = null,
    val subjectRanking: List<String>? = null,
    val predictions: Map<String, List<String>>? = null,
    val scores: Map<String, Int>? = null,
    val teamScore: Int = 0,
    val skipsRemaining: Int = 0,
    val canSkip: Boolean = false,
)

@Serializable
data class RoomStateView(
    val code: String,
    val phase: String,
    val mode: String = "coop",
    val deckId: String = "",
    val deckIds: List<String> = emptyList(),
    val selectedDecks: List<DeckInfo> = emptyList(),
    val totalRounds: Int = 5,
    val players: List<PlayerView> = emptyList(),
    val currentRound: RoundView? = null,
    val teamScore: Int = 0,
    val decks: List<DeckInfo> = emptyList(),
    val youAre: String = "",
    val paused: Boolean = false,
    val pauseReason: String? = null,
    val reconnectByMs: Long? = null,
    val abortReason: String? = null,
)

fun RoomStateView.canonicalDeckIds(): List<String> =
    deckIds.ifEmpty { deckId.takeIf(String::isNotBlank)?.let(::listOf).orEmpty() }.distinct()

fun RoomStateView.canonicalSelectedDecks(): List<DeckInfo> {
    val ids = canonicalDeckIds()
    if (ids.isEmpty()) return emptyList()
    val metadata = (selectedDecks + decks).associateBy { it.id }
    return ids.map { id -> metadata[id] ?: DeckInfo(id = id, name = "Selected deck") }
}

object MsgType {
    const val JOIN_ROOM = "join_room"
    const val RECONNECT = "reconnect"
    const val ATTACH_ACCOUNT = "attach_account"
    const val START_GAME = "start_game"
    const val UPDATE_GAME_SETTINGS = "update_game_settings"
    const val SUBMIT_RANKING = "submit_ranking"
    const val SKIP_QUESTION = "skip_question"
    const val READY = "ready"
    const val NEXT_ROUND = "next_round"
    const val LEAVE_ROOM = "leave_room"
    const val ROOM_STATE = "room_state"
    const val ERROR = "error"
    const val WELCOME = "welcome"
}

object Phase {
    const val LOBBY = "LOBBY"
    const val ROUND_SUBMIT = "ROUND_SUBMIT"
    const val ROUND_REVEAL = "ROUND_REVEAL"
    const val GAME_OVER = "GAME_OVER"
}
