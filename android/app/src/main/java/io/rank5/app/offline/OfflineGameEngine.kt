package io.rank5.app.offline

import io.rank5.app.deck.Deck
import io.rank5.app.deck.SubjectPlaceholder
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
        val players = names.mapIndexed { index, name ->
            OfflinePlayer(id = "local-$index", name = name.trim())
        }
        // Questions that rank the players themselves need a real group; drop
        // them rather than handing this one a two-name list to sort.
        val playersKindAllowed = players.size >= MinPlayersForPlayerQuestions
        val selected = decks
            .filter { it.id in selectedDeckIds }
            .map { deck ->
                if (playersKindAllowed) deck
                else deck.copy(questions = deck.questions.filterNot { it.usesPlayersAsOptions })
            }
            .filter { it.questions.isNotEmpty() }
        require(selected.isNotEmpty()) {
            if (decks.any { it.id in selectedDeckIds }) {
                "These questions rank the players, so you need at least $MinPlayersForPlayerQuestions."
            } else {
                "Choose at least one deck."
            }
        }
        val schedule = balancedSchedule(selected, rounds)
            .mapIndexed { index, question ->
                renderQuestion(question, players[index % players.size], players)
            }
        require(schedule.isNotEmpty()) { "The selected decks have no questions." }
        return OfflineGameState(
            active = true,
            screen = OfflineScreen.Handoff,
            playerNames = names.map(String::trim),
            decks = decks,
            selectedDeckIds = selectedDeckIds.filterTo(linkedSetOf()) { id ->
                decks.any { it.id == id && it.questions.isNotEmpty() }
            },
            selectedRounds = schedule.size,
            players = players,
            schedule = schedule,
            localRanking = schedule.first().options.shuffled(random),
        )
    }

    /**
     * Personalizes a deck template for the player in the spotlight: the
     * placeholder becomes their name, and a players-kind question receives the
     * group as its options. The schedule is fixed up front offline, so the
     * subject of every round is already known here.
     */
    fun renderQuestion(
        question: OfflineQuestion,
        subject: OfflinePlayer,
        players: List<OfflinePlayer>,
    ): OfflineQuestion = question.copy(
        prompt = question.prompt.replace(SubjectPlaceholder, subject.name),
        options = if (question.usesPlayersAsOptions) {
            distinctPlayerNames(players).shuffled(random)
        } else {
            question.options
        },
    )

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
                    kind = question.kind,
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

        /**
         * Points a prediction can lose. Five options cost 50 per displaced
         * position over a maximum displacement of 12; other lengths spread the
         * same range so every question is worth the same and scores stay
         * comparable with the server.
         */
        const val PenaltyRange = 600

        /** Largest possible displacement between two orderings of [n] items. */
        fun maxDisplacementFor(n: Int): Int = if (n < 2) 0 else n * n / 2

        fun scorePrediction(actual: List<String>, predicted: List<String>): Int {
            val positions = actual.withIndex().associate { it.value to it.index }
            val displacement = predicted.withIndex().sumOf { (index, option) ->
                kotlin.math.abs(index - (positions[option] ?: index))
            }
            val maxDisplacement = maxDisplacementFor(actual.size)
            if (maxDisplacement <= 0) return MaxScore
            val penalty = (PenaltyRange * displacement + maxDisplacement / 2) / maxDisplacement
            return (MaxScore - penalty).coerceAtLeast(0)
        }

        /**
         * Rankings are keyed by option text, so two players sharing a name
         * would corrupt scoring. Setup rejects duplicates, but the ranking
         * stays correct even if that ever changes.
         */
        internal fun distinctPlayerNames(players: List<OfflinePlayer>): List<String> {
            val used = mutableSetOf<String>()
            return players.map { player ->
                val base = player.name.trim().ifEmpty { "Player" }
                var name = base
                var suffix = 2
                while (!used.add(name)) {
                    name = "$base ($suffix)"
                    suffix++
                }
                name
            }
        }
    }
}
