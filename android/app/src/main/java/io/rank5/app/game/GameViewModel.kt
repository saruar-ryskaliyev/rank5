package io.rank5.app.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.rank5.app.BuildConfig
import io.rank5.app.auth.AuthRepository
import io.rank5.app.auth.AuthState
import io.rank5.app.deck.DeckRepository
import io.rank5.app.deck.DeckSummary
import io.rank5.app.deck.Deck
import io.rank5.app.net.ClientEvent
import io.rank5.app.net.DeckInfo
import io.rank5.app.net.GameClient
import io.rank5.app.net.Phase
import io.rank5.app.net.RoomStateView
import io.rank5.app.net.WelcomePayload
import io.rank5.app.net.canonicalSelectedDecks
import io.rank5.app.net.canonicalDeckIds
import io.rank5.app.stats.GuestResultClaim
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MaxSelectedDecks = 5

/** Which button is currently waiting on the server; each renders its own spinner. */
enum class BusyAction { Create, Join, Start }

/** One finished round, accumulated for the Results breakdown. */
data class RoundResult(
    val index: Int,
    val deckId: String = "",
    val teamScore: Int,
    val scores: Map<String, Int>,
)

data class UiState(
    val screen: Screen = Screen.Home,
    val nickname: String = "",
    val joinCode: String = "",
    val isHost: Boolean = false,
    val playerId: String = "",
    val room: RoomStateView? = null,
    val selectedDecks: List<DeckInfo> = listOf(
        DeckInfo("food", "Food Favorites", "🍕", 6),
    ),
    val selectedRounds: Int = 5,
    val communityQuery: String = "",
    val communityResults: List<DeckSummary> = emptyList(),
    val communityLoading: Boolean = false,
    val localRanking: List<String> = emptyList(),
    val statusMessage: String? = null,
    val busyAction: BusyAction? = null,
    val submitting: Boolean = false,
    val skippingQuestion: Boolean = false,
    val reconnecting: Boolean = false,
    val roundHistory: List<RoundResult> = emptyList(),
) {
    val busy: Boolean get() = busyAction != null
}

enum class Screen {
    Home, Lobby, Submit, Reveal, Results
}

fun addDeckSelection(current: List<DeckInfo>, deck: DeckInfo): List<DeckInfo> = when {
    current.any { it.id == deck.id } -> current
    current.size >= MaxSelectedDecks -> current
    else -> current + deck
}

fun removeDeckSelection(current: List<DeckInfo>, deckId: String): List<DeckInfo> =
    if (current.size <= 1) current else current.filterNot { it.id == deckId }

fun combinedQuestionCount(decks: List<DeckInfo>): Int = decks.sumOf { it.questionCount.coerceAtLeast(0) }

fun allowedRoundChoices(questionCount: Int): List<Int> {
    if (questionCount <= 0) return listOf(3, 5, 6)
    return listOf(3, 5, 6, 10, 15, 20).filter { it <= questionCount }
        .ifEmpty { listOf(questionCount.coerceIn(1, 20)) }
}

fun clampRounds(current: Int, questionCount: Int): Int {
    val allowed = allowedRoundChoices(questionCount)
    return if (current in allowed) current else allowed.last()
}

fun isSameRoundQuestionReplacement(previous: RoomStateView?, next: RoomStateView): Boolean {
    val previousRound = previous?.currentRound ?: return false
    val nextRound = next.currentRound ?: return false
    return previous.phase == Phase.ROUND_SUBMIT && next.phase == Phase.ROUND_SUBMIT &&
        previousRound.index == nextRound.index && previousRound.question.id != nextRound.question.id
}

fun canSkipCurrentQuestion(state: UiState): Boolean {
    val room = state.room ?: return false
    val round = room.currentRound ?: return false
    return state.screen == Screen.Submit && room.phase == Phase.ROUND_SUBMIT &&
        room.youAre == round.subjectId && round.submitted[room.youAre] != true &&
        round.canSkip && round.skipsRemaining > 0 && !state.skippingQuestion && !state.submitting
}

fun encodeDeckSelection(decks: List<DeckInfo>, json: Json = Json): ArrayList<String> =
    ArrayList(decks.distinctBy { it.id }.take(MaxSelectedDecks).map { json.encodeToString(it) })

fun decodeDeckSelection(raw: List<String>, json: Json = Json { ignoreUnknownKeys = true }): List<DeckInfo> =
    raw.mapNotNull { runCatching { json.decodeFromString<DeckInfo>(it) }.getOrNull() }
        .distinctBy { it.id }
        .take(MaxSelectedDecks)

class GameViewModel(
    private val auth: AuthRepository,
    private val decks: DeckRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val baseUrl: String = BuildConfig.SERVER_BASE_URL,
) : ViewModel() {
    private val client = GameClient(baseUrl, viewModelScope)

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(restoreSettings())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var communitySearchJob: Job? = null
    private var awaitingInitialSettings = false
    private var pendingSkipQuestionId: String? = null

    private fun restoreSettings(): UiState {
        val restoredDecks = savedStateHandle.get<ArrayList<String>>("selected_decks")
            .orEmpty()
            .let(::decodeDeckSelection)
        return UiState(
            selectedDecks = restoredDecks.ifEmpty { UiState().selectedDecks },
            selectedRounds = savedStateHandle["selected_rounds"] ?: 5,
        )
    }

    private fun persistSettings(state: UiState = _state.value) {
        savedStateHandle["selected_decks"] = encodeDeckSelection(state.selectedDecks, json)
        savedStateHandle["selected_rounds"] = state.selectedRounds
    }

    init {
        viewModelScope.launch {
            // Prefill Home "Your name" from the signed-in profile when the field is empty.
            // Never overwrite a name the player already typed.
            auth.state.collect { authState ->
                val name = (authState as? AuthState.SignedIn)?.user?.displayName?.trim().orEmpty()
                if (name.isEmpty()) return@collect
                _state.update { ui ->
                    if (ui.nickname.isBlank()) ui.copy(nickname = name) else ui
                }
            }
        }
        viewModelScope.launch {
            client.events.collect { event ->
                when (event) {
                    is ClientEvent.Connected -> Unit
                    is ClientEvent.Welcome -> onWelcome(event.payload)
                    is ClientEvent.RoomState -> onRoomState(event.state)
                    is ClientEvent.ServerError -> {
                        pendingSkipQuestionId = null
                        _state.update {
                            it.copy(
                                statusMessage = friendly(event.message),
                                busyAction = null,
                                submitting = false,
                                skippingQuestion = false,
                            )
                        }
                    }
                    is ClientEvent.ConnectionError -> {
                        pendingSkipQuestionId = null
                        _state.update {
                            val liveRoom = it.room != null && it.screen != Screen.Home
                            it.copy(
                                statusMessage = if (liveRoom) {
                                    if (it.reconnecting) it.statusMessage
                                    else "Connection lost — reconnecting…"
                                } else {
                                    friendly(event.message)
                                },
                                busyAction = null,
                                skippingQuestion = false,
                                reconnecting = liveRoom,
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateNickname(v: String) = _state.update { it.copy(nickname = v) }
    fun updateJoinCode(v: String) = _state.update { it.copy(joinCode = v.uppercase()) }
    fun selectDeck(id: String) {
        val st = _state.value
        val info = (st.room?.decks.orEmpty() + st.room?.selectedDecks.orEmpty())
            .firstOrNull { it.id == id } ?: return
        toggleDeck(info)
    }

    fun removeDeck(id: String) {
        updateSelection(removeDeckSelection(_state.value.selectedDecks, id))
    }

    private fun toggleDeck(info: DeckInfo) {
        val current = _state.value.selectedDecks
        val updated = if (current.any { it.id == info.id }) {
            removeDeckSelection(current, info.id)
        } else {
            addDeckSelection(current, info)
        }
        if (updated == current && current.none { it.id == info.id } && current.size >= MaxSelectedDecks) {
            _state.update { it.copy(statusMessage = "Choose up to five decks.") }
            return
        }
        updateSelection(updated)
    }

    private fun updateSelection(decks: List<DeckInfo>) {
        _state.update { state ->
            state.copy(
                selectedDecks = decks,
                selectedRounds = clampRounds(state.selectedRounds, combinedQuestionCount(decks)),
            )
        }
        persistSettings()
        syncSettings()
    }

    /** Selects a deck from the library before a room exists and keeps enough
     * metadata for the Lobby to present community/private decks correctly. */
    fun chooseDeck(deck: Deck) = _state.update { st ->
        val info = DeckInfo(
            id = deck.id,
            name = deck.title,
            emoji = deck.emoji,
            questionCount = deck.questions.size,
        )
        st.copy(
            selectedDecks = listOf(info),
            selectedRounds = clampRounds(st.selectedRounds, deck.questions.size),
        )
    }.also { persistSettings() }
    fun selectRounds(n: Int) {
        _state.update { it.copy(selectedRounds = clampRounds(n, combinedQuestionCount(it.selectedDecks))) }
        persistSettings()
        syncSettings()
    }

    fun loadDeckLibrary() {
        searchCommunityDecks(_state.value.communityQuery)
    }

    fun setCommunityQuery(q: String) {
        _state.update { it.copy(communityQuery = q) }
        communitySearchJob?.cancel()
        communitySearchJob = viewModelScope.launch {
            delay(300)
            searchCommunityDecks(q)
        }
    }

    fun pickCommunityDeck(summary: DeckSummary) {
        val info = DeckInfo(
            id = summary.id,
            name = summary.title,
            emoji = summary.emoji,
            questionCount = summary.questionCount,
        )
        toggleDeck(info)
    }

    private fun searchCommunityDecks(q: String) {
        viewModelScope.launch {
            _state.update { it.copy(communityLoading = true) }
            try {
                val results = decks.searchCommunity(q.trim())
                _state.update {
                    it.copy(communityResults = results, communityLoading = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        communityLoading = false,
                        statusMessage = "Couldn’t update the deck library.",
                    )
                }
            }
        }
    }

    /** One-shot consumption: the router clears the message before showing it. */
    fun statusShown() = _state.update { it.copy(statusMessage = null) }

    fun createAndJoin() {
        val nick = _state.value.nickname.trim()
        if (nick.isEmpty()) {
            _state.update { it.copy(statusMessage = "Enter a nickname") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyAction = BusyAction.Create) }
            try {
                val code = client.createRoom()
                _state.update { it.copy(joinCode = code) }
                client.connect(code, nick, authToken = auth.currentToken())
            } catch (e: Exception) {
                _state.update {
                    it.copy(busyAction = null, statusMessage = friendly(e.message))
                }
            }
        }
    }

    fun joinRoom() {
        val nick = _state.value.nickname.trim()
        val code = _state.value.joinCode.trim()
        if (nick.isEmpty() || code.isEmpty()) {
            _state.update { it.copy(statusMessage = "Nickname and room code required") }
            return
        }
        _state.update { it.copy(busyAction = BusyAction.Join) }
        client.connect(code, nick, authToken = auth.currentToken())
    }

    fun startGame() {
        val s = _state.value
        _state.update { it.copy(busyAction = BusyAction.Start) }
        client.startGame(
            s.selectedDecks.map { it.id },
            s.selectedRounds,
        )
    }

    private fun syncSettings() {
        val state = _state.value
        val phase = state.room?.phase
        if (!state.isHost || (phase != Phase.LOBBY && phase != Phase.GAME_OVER)) return
        client.updateGameSettings(
            state.selectedDecks.map { it.id },
            state.selectedRounds,
        )
    }

    fun currentGuestResultClaim(): GuestResultClaim? {
        val roomCode = client.roomCode ?: return null
        val reconnectToken = client.reconnectToken ?: return null
        val playerId = client.playerId ?: return null
        return GuestResultClaim(roomCode, playerId, reconnectToken)
    }

    fun attachAccount(authToken: String) {
        client.attachAccount(authToken)
    }

    fun setLocalRanking(ranking: List<String>) {
        _state.update { it.copy(localRanking = ranking) }
    }

    fun submitEntry(auto: Boolean = false) {
        val s = _state.value
        val ranking = s.localRanking
        if (ranking.size != 5) return
        if (s.submitting || s.skippingQuestion) return
        val round = s.room?.currentRound ?: return
        val already = round.submitted[s.playerId] == true
        if (already) return
        _state.update {
            it.copy(
                submitting = true,
                statusMessage = if (auto) {
                    "Time’s up — your current order was submitted."
                } else {
                    it.statusMessage
                },
            )
        }
        client.submitRanking(round.index, round.question.id, ranking)
    }

    fun skipQuestion() {
        val s = _state.value
        if (!canSkipCurrentQuestion(s)) return
        val round = s.room?.currentRound ?: return
        pendingSkipQuestionId = round.question.id
        _state.update { it.copy(skippingQuestion = true) }
        client.skipQuestion(round.index, round.question.id)
    }

    fun markReady() {
        val s = _state.value
        if (s.room?.currentRound?.ready?.get(s.playerId) == true) return
        client.markReady()
    }

    fun leaveToHome() {
        pendingSkipQuestionId = null
        client.leaveRoom()
        _state.update {
            UiState(nickname = it.nickname)
        }
    }

    private fun onWelcome(w: WelcomePayload) {
        pendingSkipQuestionId = null
        _state.update {
            it.copy(
                playerId = w.playerId,
                isHost = w.isHost,
                busyAction = null,
                statusMessage = null,
                screen = Screen.Lobby,
                reconnecting = false,
            )
        }
        if (w.isHost) {
            awaitingInitialSettings = true
            val state = _state.value
            client.updateGameSettings(
                state.selectedDecks.map { it.id },
                state.selectedRounds,
            )
        }
    }

    private fun onRoomState(room: RoomStateView) {
        val screen = when (room.phase) {
            Phase.LOBBY -> Screen.Lobby
            Phase.ROUND_SUBMIT -> Screen.Submit
            Phase.ROUND_REVEAL -> Screen.Reveal
            Phase.GAME_OVER -> Screen.Results
            else -> _state.value.screen
        }
        val previousRoom = _state.value.room
        val questionReplaced = isSameRoundQuestionReplacement(previousRoom, room)
        val gameAborted = previousRoom?.phase in setOf(Phase.ROUND_SUBMIT, Phase.ROUND_REVEAL) &&
            room.phase == Phase.LOBBY && room.abortReason == "not_enough_players"
        val options = room.currentRound?.question?.options.orEmpty()
        val existing = _state.value.localRanking
        val alreadySubmitted = room.currentRound?.submitted?.get(room.youAre) == true
        val ranking = when {
            questionReplaced -> options
            room.currentRound?.myRanking != null -> room.currentRound.myRanking
            !alreadySubmitted && existing.isNotEmpty() && existing.toSet() == options.toSet() -> existing
            options.isNotEmpty() -> options
            else -> emptyList()
        }

        val pendingQuestion = pendingSkipQuestionId
        if (pendingQuestion != null &&
            (room.phase != Phase.ROUND_SUBMIT || room.currentRound?.question?.id != pendingQuestion)
        ) {
            pendingSkipQuestionId = null
        }

        _state.update { st ->
            val serverDecks = room.canonicalSelectedDecks()
            val serverMatchesPending = room.totalRounds == st.selectedRounds &&
                room.canonicalDeckIds() == st.selectedDecks.map { it.id }
            val preservePending = st.isHost && awaitingInitialSettings && !serverMatchesPending
            if (serverMatchesPending) awaitingInitialSettings = false
            val effectiveDecks = if (preservePending) st.selectedDecks else serverDecks.ifEmpty { st.selectedDecks }
            val round = room.currentRound
            val history = if (room.phase == Phase.ROUND_REVEAL && round != null) {
                st.roundHistory.filter { it.index < round.index } +
                    RoundResult(round.index, round.question.deckId, round.teamScore, round.scores.orEmpty())
            } else {
                st.roundHistory
            }
            st.copy(
                room = room,
                screen = screen,
                localRanking = ranking,
                selectedDecks = effectiveDecks,
                selectedRounds = clampRounds(
                    if (preservePending) st.selectedRounds else room.totalRounds,
                    combinedQuestionCount(effectiveDecks),
                ),
                isHost = room.players.any { p -> p.id == st.playerId && p.isHost },
                busyAction = null,
                submitting = alreadySubmitted,
                skippingQuestion = pendingSkipQuestionId != null,
                reconnecting = false,
                roundHistory = history,
                statusMessage = if (gameAborted) {
                    "Game ended because fewer than two players remained."
                } else {
                    st.statusMessage
                },
            )
        }
        persistSettings()
    }

    private fun friendly(raw: String?): String {
        val msg = raw.orEmpty()
        return when {
            msg.contains("404") || msg.contains("Expected HTTP 101") ->
                "That room doesn't exist. Check the code and try again."
            msg.contains("invalid reconnect", ignoreCase = true) ->
                "Couldn't rejoin. Join again with the room code."
            msg.contains("need at least 2 players", ignoreCase = true) ->
                "You need at least 2 players to start."
            msg.contains("already submitted", ignoreCase = true) ->
                "You already locked in."
            msg.contains("only the subject", ignoreCase = true) ->
                "Only the player in the spotlight can skip."
            msg.contains("no skips remaining", ignoreCase = true) ->
                "You’re out of skips for this game."
            msg.contains("no replacement question", ignoreCase = true) ->
                "No spare questions are available."
            msg.contains("question is no longer current", ignoreCase = true) ->
                "That question already changed."
            msg.contains("game is paused", ignoreCase = true) ->
                "Waiting for another player to reconnect."
            msg.contains("game already started", ignoreCase = true) ->
                "That game already started without you — ask for a new code."
            msg.contains("no longer available", ignoreCase = true) ->
                "A selected deck is no longer available. Remove it and choose another."
            msg.contains("select between 1 and 5", ignoreCase = true) ->
                "Choose between one and five different decks."
            msg.contains("only the host", ignoreCase = true) ->
                "Only the host can change game settings."
            msg.contains("connect", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("unreachable", ignoreCase = true) ||
                msg.contains("unable to resolve host", ignoreCase = true) ->
                "Can't reach the game server."
            else -> "Something went wrong. Try again."
        }
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
