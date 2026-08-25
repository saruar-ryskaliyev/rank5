package io.rank5.app.offline

import io.rank5.app.deck.Deck
import kotlinx.serialization.Serializable

const val MinOfflinePlayers = 2
const val MaxOfflinePlayers = 8

@Serializable
data class OfflineQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val deckId: String = "",
)

data class OfflinePlayer(
    val id: String,
    val name: String,
)

enum class OfflineScreen { Setup, Handoff, Ranking, Reveal, Results }

data class OfflineRoundResult(
    val index: Int,
    val subject: OfflinePlayer,
    val question: OfflineQuestion,
    val subjectRanking: List<String>,
    val predictions: Map<String, List<String>>,
    val scores: Map<String, Int>,
    val teamScore: Int,
)

data class OfflineGameState(
    val active: Boolean = false,
    val screen: OfflineScreen = OfflineScreen.Setup,
    val playerNames: List<String> = listOf("", ""),
    val decks: List<Deck> = emptyList(),
    val selectedDeckIds: Set<String> = setOf("food"),
    val selectedRounds: Int = 5,
    val players: List<OfflinePlayer> = emptyList(),
    val schedule: List<OfflineQuestion> = emptyList(),
    val roundIndex: Int = 0,
    val actorIndex: Int = 0,
    val localRanking: List<String> = emptyList(),
    val subjectRanking: List<String> = emptyList(),
    val predictions: Map<String, List<String>> = emptyMap(),
    val currentResult: OfflineRoundResult? = null,
    val history: List<OfflineRoundResult> = emptyList(),
    val teamScore: Int = 0,
) {
    val currentQuestion: OfflineQuestion? get() = schedule.getOrNull(roundIndex)
    val subject: OfflinePlayer? get() = players.getOrNull(roundIndex % players.size.coerceAtLeast(1))
    val actors: List<OfflinePlayer>
        get() {
            val currentSubject = subject ?: return emptyList()
            return listOf(currentSubject) + players.filterNot { it.id == currentSubject.id }
        }
    val actor: OfflinePlayer? get() = actors.getOrNull(actorIndex)
    val actorIsSubject: Boolean get() = actor?.id != null && actor?.id == subject?.id
    val availableQuestionCount: Int
        get() = decks.filter { it.id in selectedDeckIds }.sumOf { it.questions.size }
}

fun offlinePlayerValidation(names: List<String>): String? {
    val cleaned = names.map(String::trim)
    if (cleaned.size !in MinOfflinePlayers..MaxOfflinePlayers) {
        return "Add between $MinOfflinePlayers and $MaxOfflinePlayers players."
    }
    if (cleaned.any(String::isBlank)) return "Enter a name for every player."
    if (cleaned.distinctBy { it.lowercase() }.size != cleaned.size) {
        return "Player names must be different."
    }
    return null
}

fun offlineAllowedRoundChoices(questionCount: Int): List<Int> =
    listOf(3, 5, 6, 10, 15, 20).filter { it <= questionCount }
        .ifEmpty { listOf(questionCount.coerceAtLeast(1)) }
