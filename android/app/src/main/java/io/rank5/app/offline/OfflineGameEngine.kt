package io.rank5.app.offline

import io.rank5.app.deck.Deck
import kotlin.random.Random

class OfflineGameEngine(
    private val random: Random = Random.Default,
) {
    fun start(
        names: List<String>,
        decks: List<Deck>,
        selectedDeckIds: Set<String>,
        rounds: Int,
    ): OfflineGameState {
        offlinePlayerValidation(names)?.let { throw IllegalArgumentException(it) }
        val selected = decks.filter { it.id in selectedDeckIds && it.questions.isNotEmpty() }
        require(selected.isNotEmpty()) { "Choose at least one deck." }
        val schedule = balancedSchedule(selected, rounds)
        require(schedule.isNotEmpty()) { "The selected decks have no questions." }
        val players = names.mapIndexed { index, name ->
            OfflinePlayer(id = "local-$index", name = name.trim())
        }
        return OfflineGameState(
            active = true,
            screen = OfflineScreen.Handoff,
            playerNames = names.map(String::trim),
            decks = decks,
            selectedDeckIds = selected.mapTo(linkedSetOf()) { it.id },
            selectedRounds = schedule.size,
            players = players,
            schedule = schedule,
            localRanking = schedule.first().options.shuffled(random),
        )
    }

    fun revealToActor(state: OfflineGameState): OfflineGameState {
        require(state.screen == OfflineScreen.Handoff)
        return state.copy(screen = OfflineScreen.Ranking)
    }

    fun reorder(state: OfflineGameState, ranking: List<String>): OfflineGameState {
        val options = state.currentQuestion?.options.orEmpty()
        if (state.screen != OfflineScreen.Ranking ||
            ranking.size != options.size || ranking.toSet() != options.toSet()
        ) return state
        return state.copy(localRanking = ranking)
    }

    fun lockIn(state: OfflineGameState): OfflineGameState {
        require(state.screen == OfflineScreen.Ranking)
        val actor = requireNotNull(state.actor)
        val question = requireNotNull(state.currentQuestion)
        require(state.localRanking.size == question.options.size)

        val subjectRanking = if (state.actorIsSubject) state.localRanking else state.subjectRanking
        val predictions = if (state.actorIsSubject) {
            state.predictions
        } else {
            state.predictions + (actor.id to state.localRanking)
        }
        val nextActor = state.actorIndex + 1
        if (nextActor < state.actors.size) {
            return state.copy(
                screen = OfflineScreen.Handoff,
                actorIndex = nextActor,
                localRanking = question.options.shuffled(random),
                subjectRanking = subjectRanking,
                predictions = predictions,
            )
        }

        val scores = predictions.mapValues { (_, prediction) ->
            scorePrediction(subjectRanking, prediction)
        }
        val roundScore = scores.values.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
        val result = OfflineRoundResult(
            index = state.roundIndex,
            subject = requireNotNull(state.subject),
            question = question,
            subjectRanking = subjectRanking,
            predictions = predictions,
            scores = scores,
            teamScore = roundScore,
        )
        return state.copy(
            screen = OfflineScreen.Reveal,
            subjectRanking = subjectRanking,
            predictions = predictions,
            currentResult = result,
            history = state.history + result,
            teamScore = state.teamScore + roundScore,
        )
    }

    fun nextRound(state: OfflineGameState): OfflineGameState {
        require(state.screen == OfflineScreen.Reveal)
        val nextRound = state.roundIndex + 1
        if (nextRound >= state.schedule.size) return state.copy(screen = OfflineScreen.Results)
        return state.copy(
            screen = OfflineScreen.Handoff,
            roundIndex = nextRound,
            actorIndex = 0,
            localRanking = state.schedule[nextRound].options.shuffled(random),
            subjectRanking = emptyList(),
            predictions = emptyMap(),
            currentResult = null,
        )
    }

    fun rematch(state: OfflineGameState): OfflineGameState = start(
        names = state.playerNames,
        decks = state.decks,
        selectedDeckIds = state.selectedDeckIds,
        rounds = state.selectedRounds,
    )

    fun balancedSchedule(decks: List<Deck>, rounds: Int): List<OfflineQuestion> {
        if (rounds <= 0 || decks.isEmpty()) return emptyList()
        val queues = decks.shuffled(random).map { deck ->
            deck.questions.shuffled(random).map { question ->
                OfflineQuestion(
                    id = question.id,
                    prompt = question.prompt,
                    options = question.options,
                    deckId = deck.id,
                )
            }.toMutableList()
        }.toMutableList()
        val result = mutableListOf<OfflineQuestion>()
        var cursor = 0
        while (result.size < rounds && queues.any { it.isNotEmpty() }) {
            val queue = queues[cursor % queues.size]
            if (queue.isNotEmpty()) result += queue.removeAt(0)
            cursor++
        }
        return result
    }

    companion object {
        const val MaxScore = 2_000
        private const val PenaltyPerPosition = 50

        fun scorePrediction(actual: List<String>, predicted: List<String>): Int {
            val positions = actual.withIndex().associate { it.value to it.index }
            val displacement = predicted.withIndex().sumOf { (index, option) ->
                kotlin.math.abs(index - (positions[option] ?: index))
            }
            return (MaxScore - PenaltyPerPosition * displacement).coerceAtLeast(0)
        }
    }
}
