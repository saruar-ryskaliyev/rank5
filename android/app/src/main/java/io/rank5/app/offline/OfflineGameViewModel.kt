package io.rank5.app.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rank5.app.deck.DeckRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OfflineGameViewModel(
    private val repository: DeckRepository,
    private val engine: OfflineGameEngine = OfflineGameEngine(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        OfflineGameState(decks = repository.downloadedDecks.value),
    )
    val state: StateFlow<OfflineGameState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.downloadedDecks.collect { decks ->
                _state.update { state ->
                    val availableIds = decks.mapTo(hashSetOf()) { it.id }
                    val retained = state.selectedDeckIds.filterTo(linkedSetOf()) { it in availableIds }
                    val selected = retained.ifEmpty {
                        decks.firstOrNull()?.id?.let(::setOf).orEmpty()
                    }
                    state.copy(decks = decks, selectedDeckIds = selected).withValidRounds()
                }
            }
        }
    }

    fun open() = _state.update { it.copy(active = true, screen = OfflineScreen.Setup) }

    fun close() {
        _state.value = OfflineGameState(decks = _state.value.decks)
    }

    fun updatePlayer(index: Int, name: String) = _state.update { state ->
        if (index !in state.playerNames.indices) state else state.copy(
            playerNames = state.playerNames.toMutableList().also { it[index] = name.take(28) },
        )
    }

    // The player count decides whether player-ranking questions are playable,
    // so both directions re-check the available length.
    fun addPlayer() = _state.update { state ->
        if (state.playerNames.size >= MaxOfflinePlayers) state
        else state.copy(playerNames = state.playerNames + "").withValidRounds()
    }

    fun removePlayer(index: Int) = _state.update { state ->
        if (state.playerNames.size <= MinOfflinePlayers || index !in state.playerNames.indices) state
        else state.copy(
            playerNames = state.playerNames.filterIndexed { i, _ -> i != index },
        ).withValidRounds()
    }

    private fun OfflineGameState.withValidRounds(): OfflineGameState =
        copy(selectedRounds = clampOfflineRounds(selectedRounds, availableQuestionCount))

    fun toggleDeck(id: String) = _state.update { state ->
        val selected = state.selectedDeckIds
        val next = when {
            id in selected && selected.size > 1 -> selected - id
            id !in selected -> selected + id
            else -> selected
        }
        state.copy(selectedDeckIds = next).withValidRounds()
    }

    fun selectRounds(rounds: Int) = _state.update { state ->
        if (rounds in offlineAllowedRoundChoices(state.availableQuestionCount)) {
            state.copy(selectedRounds = rounds)
        } else state
    }

    fun start() {
        val state = _state.value
        _state.value = engine.start(
            names = state.playerNames,
            decks = state.decks,
            selectedDeckIds = state.selectedDeckIds,
            rounds = state.selectedRounds,
        )
    }

    fun revealToActor() = _state.update(engine::revealToActor)
    fun reorder(ranking: List<String>) = _state.update { engine.reorder(it, ranking) }
    fun lockIn() = _state.update(engine::lockIn)
    fun nextRound() = _state.update(engine::nextRound)
    fun rematch() = _state.update(engine::rematch)
}
