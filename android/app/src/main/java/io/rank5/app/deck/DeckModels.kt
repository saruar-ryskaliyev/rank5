package io.rank5.app.deck

import kotlinx.serialization.Serializable

@Serializable
data class DeckQuestion(
    val id: String = "",
    val prompt: String = "",
    val options: List<String> = List(5) { "" },
)

@Serializable
data class DeckSummary(
    val id: String,
    val title: String,
    val emoji: String = "🃏",
    val isBuiltin: Boolean = false,
    val ownerId: String? = null,
    val questionCount: Int = 0,
    val visibility: String = "private",
    val ownerName: String = "",
    val publishedAt: String? = null,
) {
    val creatorName: String
        get() = when {
            isBuiltin -> "Rank5"
            ownerName.isNotBlank() -> ownerName
            else -> "Community creator"
        }

    val libraryMetadata: String
        get() = "$questionCount questions · by $creatorName"
}

@Serializable
data class Deck(
    val id: String,
    val title: String,
    val emoji: String = "🃏",
    val questions: List<DeckQuestion> = emptyList(),
    val isBuiltin: Boolean = false,
    val ownerId: String? = null,
    val visibility: String = "private",
    val ownerName: String = "",
    val publishedAt: String? = null,
) {
    val isPublic: Boolean get() = visibility == "public"
    val isRemoved: Boolean get() = visibility == "removed"
}

@Serializable
data class DeckWriteRequest(
    val title: String,
    val emoji: String,
    val questions: List<DeckQuestion>,
)

@Serializable
data class DeckGenerationRequest(
    val topic: String,
    val questionCount: Int = 6,
    val language: String = "en",
)

@Serializable
data class GeneratedDeckDraft(
    val title: String,
    val emoji: String = "🃏",
    val questions: List<DeckQuestion>,
)

@Serializable
data class DeckGenerationResponse(
    val generationId: String,
    val draft: GeneratedDeckDraft,
)

@Serializable
data class DeckApiError(
    val error: String = "",
    val code: String = "",
)

class DeckGenerationException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
data class ReportRequest(
    val reason: String,
)

fun unifiedDeckLibrary(
    ownedDecks: List<DeckSummary>,
    publicDecks: List<DeckSummary>,
): List<DeckSummary> = (ownedDecks + publicDecks).distinctBy { it.id }

sealed class DecksRoute {
    data object List : DecksRoute()
    data class Detail(val deckId: String) : DecksRoute()
    data object Generator : DecksRoute()
    data class Editor(val deckId: String?) : DecksRoute() // null = create
}
