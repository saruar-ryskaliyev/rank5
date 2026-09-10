package io.rank5.app.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.rank5.app.game.UiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MinDeckQuestions = 3
const val MaxDeckQuestions = 12

data class DecksUiState(
    val route: DecksRoute = DecksRoute.List,
    val mine: List<DeckSummary> = emptyList(),
    val saved: List<DeckSummary> = emptyList(),
    val community: List<DeckSummary> = emptyList(),
    val communityQuery: String = "",
    val communityLoading: Boolean = false,
    val savedLoading: Boolean = false,
    val savedError: String? = null,
    val savedOperations: Set<String> = emptySet(),
    val communityHasMore: Boolean = false,
    val downloadedDecks: List<Deck> = emptyList(),
    val downloadOperations: Set<String> = emptySet(),
    val detail: Deck? = null,
    val editorTitle: String = "",
    val editorEmoji: String = "🎯",
    val editorQuestions: List<DeckQuestion> = listOf(blankQuestion(1)),
    val editingId: String? = null,
    val editorGenerated: Boolean = false,
    val generationTopic: String = "",
    val generationQuestionCount: Int = 6,
    val generating: Boolean = false,
    val generationError: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val showLoginPrompt: Boolean = false,
    val loginPromptForSave: Boolean = false,
    val statusMessage: UiStatus? = null,
    val loadError: String? = null,
    val editorDirty: Boolean = false,
)

fun savedOperationStarted(state: DecksUiState, deckId: String): DecksUiState =
    state.copy(savedOperations = state.savedOperations + deckId)

fun savedOperationSucceeded(
    state: DecksUiState,
    deckId: String,
    saved: List<DeckSummary>,
    wasSaved: Boolean,
): DecksUiState = state.copy(
    saved = saved,
    savedOperations = state.savedOperations - deckId,
    statusMessage = UiStatus.info(if (wasSaved) "Removed from Saved" else "Saved to your library"),
)

fun savedOperationFailed(state: DecksUiState, deckId: String, wasSaved: Boolean): DecksUiState =
    state.copy(
        savedOperations = state.savedOperations - deckId,
        statusMessage = UiStatus.error(
            if (wasSaved) {
                "Couldn’t remove this saved deck. Try again."
            } else {
                "Couldn’t save this deck. Try again."
            },
        ),
    )

private fun blankQuestion(index: Int): DeckQuestion =
    DeckQuestion(
        id = "q$index",
        prompt = "",
        options = List(5) { "" },
    )

class DecksViewModel(
    private val repo: DeckRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(restoreEditorState())
    val state: StateFlow<DecksUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            repo.downloadedDecks.collect { downloaded ->
                _state.update { it.copy(downloadedDecks = downloaded) }
            }
        }
    }

    private fun restoreEditorState(): DecksUiState {
        if (savedStateHandle.get<Boolean>("editor_active") != true) return DecksUiState()
        val raw = savedStateHandle.get<ArrayList<String>>("editor_questions").orEmpty()
        val questions = raw.mapNotNull { runCatching { json.decodeFromString<DeckQuestion>(it) }.getOrNull() }
        val editingId = savedStateHandle.get<String>("editor_id")?.takeUnless { it == "__new__" }
        return DecksUiState(
            route = DecksRoute.Editor(editingId),
            editingId = editingId,
            editorTitle = savedStateHandle.get<String>("editor_title") ?: "",
            editorEmoji = savedStateHandle.get<String>("editor_emoji") ?: "🎯",
            editorQuestions = questions.ifEmpty { listOf(blankQuestion(1)) },
            editorDirty = savedStateHandle.get<Boolean>("editor_dirty") ?: false,
            editorGenerated = savedStateHandle.get<Boolean>("editor_generated") ?: false,
        )
    }

    private fun persistEditor() {
        val s = _state.value
        val route = s.route as? DecksRoute.Editor ?: return
        savedStateHandle["editor_active"] = true
        savedStateHandle["editor_id"] = route.deckId ?: "__new__"
        savedStateHandle["editor_title"] = s.editorTitle
        savedStateHandle["editor_emoji"] = s.editorEmoji
        savedStateHandle["editor_dirty"] = s.editorDirty
        savedStateHandle["editor_generated"] = s.editorGenerated
        savedStateHandle["editor_questions"] = ArrayList(s.editorQuestions.map { json.encodeToString(it) })
    }

    private fun clearPersistedEditor() {
        listOf("editor_active", "editor_id", "editor_title", "editor_emoji", "editor_dirty", "editor_generated", "editor_questions")
            .forEach { savedStateHandle.remove<Any>(it) }
    }

    fun statusShown() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                communityLoading = true,
                savedLoading = repo.isSignedIn(),
                loadError = null,
                savedError = null,
            )
            try {
                val mine = if (repo.isSignedIn()) repo.listMine() else emptyList()
                val saved = if (repo.isSignedIn()) {
                    try {
                        repo.listSaved()
                    } catch (_: Exception) {
                        _state.value = _state.value.copy(
                            savedLoading = false,
                            savedError = "Couldn’t load saved decks.",
                        )
                        _state.value.saved
                    }
                } else emptyList()
                val library = repo.searchCommunity(_state.value.communityQuery.trim())
                _state.value = _state.value.copy(
                    mine = mine,
                    saved = saved,
                    community = library,
                    loading = false,
                    communityLoading = false,
                    savedLoading = false,
                    communityHasMore = library.size >= 20,
                    loadError = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    communityLoading = false,
                    savedLoading = false,
                    statusMessage = UiStatus.error("Couldn’t load decks. Check your connection and try again."),
                    loadError = "Couldn’t load decks. Check your connection and try again.",
                )
            }
        }
    }

    fun openList() {
        generationJob?.cancel()
        clearPersistedEditor()
        _state.value = _state.value.copy(
            route = DecksRoute.List,
            detail = null,
            showLoginPrompt = false,
            loginPromptForSave = false,
            editorDirty = false,
            editorGenerated = false,
            generating = false,
            generationError = null,
        )
        persistEditor()
        refresh()
    }

    fun setCommunityQuery(q: String) {
        _state.value = _state.value.copy(communityQuery = q)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            searchCommunity(q, reset = true)
        }
    }

    fun loadMoreCommunity() {
        if (_state.value.communityLoading || !_state.value.communityHasMore) return
        searchCommunity(_state.value.communityQuery, reset = false)
    }

    private fun searchCommunity(q: String, reset: Boolean) {
        viewModelScope.launch {
            val offset = if (reset) 0 else _state.value.community.size
            _state.value = _state.value.copy(communityLoading = true, loadError = null)
            try {
                val page = repo.searchCommunity(q.trim(), offset = offset)
                val merged = if (reset) page else _state.value.community + page
                _state.value = _state.value.copy(
                    community = merged,
                    communityLoading = false,
                    communityHasMore = page.size >= 20,
                    loadError = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    communityLoading = false,
                    statusMessage = UiStatus.error("Couldn’t update the deck library."),
                    loadError = "Couldn’t update the deck library.",
                )
            }
        }
    }

    fun openDetail(deckId: String) {
        clearPersistedEditor()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                route = DecksRoute.Detail(deckId),
            )
            try {
                val deck = repo.get(deckId)
                _state.value = _state.value.copy(detail = deck, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    route = DecksRoute.List,
                    statusMessage = UiStatus.error("Couldn’t open that deck. Try again."),
                )
            }
        }
    }

    fun backFromDetail() {
        openList()
    }

    fun requestCreate() {
        if (!repo.isSignedIn()) {
            _state.value = _state.value.copy(showLoginPrompt = true)
            return
        }
        _state.value = _state.value.copy(
            route = DecksRoute.Generator,
            editingId = null,
            generationTopic = "",
            generationQuestionCount = 6,
            generating = false,
            generationError = null,
            showLoginPrompt = false,
            loginPromptForSave = false,
            editorDirty = false,
            editorGenerated = false,
        )
    }

    fun setGenerationTopic(value: String) {
        if (value.length > 160) return
        _state.value = _state.value.copy(generationTopic = value, generationError = null)
    }

    fun setGenerationQuestionCount(value: Int) {
        if (value !in setOf(3, 6, 9, 12)) return
        _state.value = _state.value.copy(generationQuestionCount = value, generationError = null)
    }

    fun startBlankDeck() {
        generationJob?.cancel()
        _state.value = _state.value.copy(
            route = DecksRoute.Editor(null),
            editingId = null,
            editorTitle = "",
            editorEmoji = "🎯",
            editorQuestions = listOf(blankQuestion(1)),
            editorDirty = false,
            editorGenerated = false,
            generating = false,
            generationError = null,
        )
        persistEditor()
    }

    fun generateDeck() {
        val snapshot = _state.value
        val topic = snapshot.generationTopic.trim()
        if (topic.length < 3) {
            _state.value = snapshot.copy(generationError = "Enter a topic with at least 3 characters")
            return
        }
        if (snapshot.generating) return
        generationJob = viewModelScope.launch {
            _state.value = _state.value.copy(generating = true, generationError = null)
            try {
                val generated = repo.generate(topic, snapshot.generationQuestionCount).draft
                val questions = generated.questions.mapIndexed { index, question ->
                    question.copy(
                        id = "q${index + 1}",
                        options = (question.options + List(5) { "" }).take(5),
                    )
                }
                _state.value = _state.value.copy(
                    route = DecksRoute.Editor(null),
                    editingId = null,
                    editorTitle = generated.title,
                    editorEmoji = generated.emoji.ifBlank { "🃏" },
                    editorQuestions = questions,
                    editorDirty = true,
                    editorGenerated = true,
                    generating = false,
                    generationError = null,
                    statusMessage = UiStatus.info("AI draft ready — review it before saving"),
                )
                persistEditor()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DeckGenerationException) {
                _state.value = _state.value.copy(
                    generating = false,
                    generationError = generationErrorMessage(error.code),
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    generating = false,
                    generationError = generationErrorMessage("generation_failed"),
                )
            } finally {
                generationJob = null
            }
        }
    }

    fun dismissLoginPrompt() {
        _state.value = _state.value.copy(showLoginPrompt = false, loginPromptForSave = false)
    }

    fun refreshSaved() {
        if (!repo.isSignedIn()) {
            _state.value = _state.value.copy(
                showLoginPrompt = true,
                loginPromptForSave = true,
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(savedLoading = true, savedError = null)
            try {
                val saved = repo.listSaved()
                _state.value = _state.value.copy(saved = saved, savedLoading = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    savedLoading = false,
                    savedError = "Couldn’t load saved decks.",
                )
            }
        }
    }

    fun toggleSaved(deckId: String) {
        if (_state.value.mine.any { it.id == deckId }) return
        if (!repo.isSignedIn()) {
            _state.value = _state.value.copy(
                showLoginPrompt = true,
                loginPromptForSave = true,
            )
            return
        }
        if (deckId in _state.value.savedOperations) return
        val wasSaved = _state.value.saved.any { it.id == deckId }
        viewModelScope.launch {
            _state.value = savedOperationStarted(_state.value, deckId)
            try {
                if (wasSaved) repo.unsave(deckId) else repo.save(deckId)
                val refreshed = repo.listSaved()
                _state.value = savedOperationSucceeded(_state.value, deckId, refreshed, wasSaved)
            } catch (_: Exception) {
                _state.value = savedOperationFailed(_state.value, deckId, wasSaved)
            }
        }
    }

    fun toggleDownloaded() {
        val deck = _state.value.detail ?: return
        if (deck.isBuiltin || deck.id in _state.value.downloadOperations) return
        val wasDownloaded = _state.value.downloadedDecks.any { it.id == deck.id }
        viewModelScope.launch {
            _state.update { it.copy(downloadOperations = it.downloadOperations + deck.id) }
            try {
                if (wasDownloaded) {
                    repo.removeDownload(deck.id)
                    _state.update {
                        it.copy(
                            downloadOperations = it.downloadOperations - deck.id,
                            statusMessage = UiStatus.info("Removed from this device"),
                        )
                    }
                } else {
                    val downloaded = repo.download(deck.id)
                    _state.update {
                        it.copy(
                            detail = downloaded,
                            downloadOperations = it.downloadOperations - deck.id,
                            statusMessage = UiStatus.info("Available offline"),
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        downloadOperations = it.downloadOperations - deck.id,
                        statusMessage = UiStatus.error("Couldn’t update offline availability. Check your connection."),
                    )
                }
            }
        }
    }

    fun openEditor(deck: Deck) {
        if (!repo.isSignedIn()) {
            _state.value = _state.value.copy(showLoginPrompt = true)
            return
        }
        val qs = deck.questions.ifEmpty { listOf(blankQuestion(1)) }.mapIndexed { i, q ->
            val opts = (q.options + List(5) { "" }).take(5)
            q.copy(id = q.id.ifBlank { "q${i + 1}" }, options = opts)
        }
        _state.value = _state.value.copy(
            route = DecksRoute.Editor(deck.id),
            editingId = deck.id,
            editorTitle = deck.title,
            editorEmoji = deck.emoji.ifBlank { "🎯" },
            editorQuestions = qs,
            showLoginPrompt = false,
            loginPromptForSave = false,
            editorDirty = false,
            editorGenerated = false,
        )
        persistEditor()
    }

    fun setEditorTitle(v: String) {
        _state.value = _state.value.copy(editorTitle = v, editorDirty = true)
        persistEditor()
    }

    fun setEditorEmoji(v: String) {
        _state.value = _state.value.copy(editorEmoji = v, editorDirty = true)
        persistEditor()
    }

    fun setQuestionPrompt(index: Int, prompt: String) {
        val qs = _state.value.editorQuestions.toMutableList()
        if (index !in qs.indices) return
        qs[index] = qs[index].copy(prompt = prompt)
        _state.value = _state.value.copy(editorQuestions = qs, editorDirty = true)
        persistEditor()
    }

    fun setQuestionOption(qIndex: Int, oIndex: Int, value: String) {
        val qs = _state.value.editorQuestions.toMutableList()
        if (qIndex !in qs.indices) return
        val opts = qs[qIndex].options.toMutableList()
        if (oIndex !in opts.indices) return
        opts[oIndex] = value
        qs[qIndex] = qs[qIndex].copy(options = opts)
        _state.value = _state.value.copy(editorQuestions = qs, editorDirty = true)
        persistEditor()
    }

    fun addQuestion() {
        val qs = _state.value.editorQuestions
        if (qs.size >= MaxDeckQuestions) {
            _state.value = _state.value.copy(
                statusMessage = UiStatus.error("Maximum $MaxDeckQuestions questions per deck"),
            )
            return
        }
        _state.value = _state.value.copy(
            editorQuestions = qs + blankQuestion(qs.size + 1), editorDirty = true,
        )
        persistEditor()
    }

    fun removeQuestion(index: Int) {
        val qs = _state.value.editorQuestions
        if (index !in qs.indices || qs.size <= 1) return
        _state.value = _state.value.copy(
            editorQuestions = qs.filterIndexed { i, _ -> i != index }
                .mapIndexed { i, q -> q.copy(id = "q${i + 1}") },
            editorDirty = true,
        )
        persistEditor()
    }

    fun moveQuestion(from: Int, to: Int) {
        val qs = _state.value.editorQuestions
        if (from !in qs.indices || to !in qs.indices || from == to) return
        val moved = qs.toMutableList().apply { add(to, removeAt(from)) }
            .mapIndexed { i, q -> q.copy(id = "q${i + 1}") }
        _state.value = _state.value.copy(editorQuestions = moved, editorDirty = true)
        persistEditor()
    }

    fun saveEditor() {
        val s = _state.value
        val err = validateEditor(s.editorTitle, s.editorEmoji, s.editorQuestions)
        if (err != null) {
            _state.value = s.copy(statusMessage = UiStatus.error(err))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                val saved = if (s.editingId == null) {
                    repo.create(s.editorTitle.trim(), s.editorEmoji.trim(), normalizedQuestions(s.editorQuestions))
                } else {
                    repo.update(
                        s.editingId,
                        s.editorTitle.trim(),
                        s.editorEmoji.trim(),
                        normalizedQuestions(s.editorQuestions),
                    )
                }
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.info("Deck saved"),
                    editorDirty = false,
                    editorGenerated = false,
                )
                openDetail(saved.id)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.error("Couldn’t save this deck. Your changes are still here."),
                )
            }
        }
    }

    fun deleteCurrent() {
        val id = _state.value.detail?.id ?: _state.value.editingId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                repo.delete(id)
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.info("Deck deleted"),
                )
                openList()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.error("Couldn’t delete this deck. Try again."),
                )
            }
        }
    }

    fun publishCurrent() {
        val id = _state.value.detail?.id ?: return
        if (!repo.isSignedIn()) {
            _state.value = _state.value.copy(showLoginPrompt = true)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                val deck = repo.publish(id)
                _state.value = _state.value.copy(
                    detail = deck,
                    saving = false,
                    statusMessage = UiStatus.info("Published to community"),
                )
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.error("Couldn’t publish this deck. Try again."),
                )
            }
        }
    }

    fun unpublishCurrent() {
        val id = _state.value.detail?.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                val deck = repo.unpublish(id)
                _state.value = _state.value.copy(
                    detail = deck,
                    saving = false,
                    statusMessage = UiStatus.info("Unpublished — private again"),
                )
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.error("Couldn’t make this deck private. Try again."),
                )
            }
        }
    }

    fun reportCurrent(reason: String) {
        val id = _state.value.detail?.id ?: return
        if (!repo.isSignedIn()) {
            _state.value = _state.value.copy(showLoginPrompt = true)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                repo.report(id, reason.trim())
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.info("Report submitted — thanks"),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    statusMessage = UiStatus.error("Couldn’t send your report. Try again."),
                )
            }
        }
    }
}

fun generationErrorMessage(code: String): String = when (code) {
    "generation_limit" -> "You’ve reached today’s deck-generation limit. You can still create a deck manually."
    "generation_incomplete" -> "The AI response was incomplete. Please try again."
    "invalid_generation" -> "The AI draft didn’t match the required format. Try again or start with a blank deck."
    "generation_timeout" -> "Deck generation timed out. Please try again."
    "generation_unavailable", "provider_unavailable" -> "AI generation is temporarily unavailable. Try again shortly."
    else -> "Couldn’t generate a deck. Check your connection and try again."
}

fun validateEditor(title: String, emoji: String, questions: List<DeckQuestion>): String? {
    val t = title.trim()
    if (t.isEmpty() || t.length > 60) return "Title must be 1–60 characters"
    if (emoji.trim().isEmpty()) return "Pick an emoji"
    if (questions.size < MinDeckQuestions) {
        return "Add at least $MinDeckQuestions questions (needed for a 3-round game)"
    }
    if (questions.size > MaxDeckQuestions) {
        return "Maximum $MaxDeckQuestions questions"
    }
    questions.forEachIndexed { i, q ->
        if (q.prompt.trim().isEmpty()) return "Question ${i + 1} needs a prompt"
        if (q.options.size != 5 || q.options.any { it.trim().isEmpty() }) {
            return "Question ${i + 1} needs 5 options"
        }
        if (q.options.map { it.trim().lowercase() }.distinct().size != 5) {
            return "Question ${i + 1} needs 5 different options"
        }
    }
    return null
}

private fun normalizedQuestions(questions: List<DeckQuestion>): List<DeckQuestion> =
    questions.mapIndexed { i, q ->
        q.copy(
            id = q.id.ifBlank { "q${i + 1}" },
            prompt = q.prompt.trim(),
            options = q.options.map { it.trim() },
        )
    }
