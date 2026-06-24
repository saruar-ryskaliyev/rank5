package io.rank5.app.game

import io.rank5.app.net.Phase
import io.rank5.app.net.Question
import io.rank5.app.net.RoomStateView
import io.rank5.app.net.RoundView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSkipStateTest {
    private val options = listOf("A", "B", "C", "D", "E")

    @Test
    fun sameRoundQuestionIdChangeResetsRankingEvenWithIdenticalOptions() {
        val previous = room(questionId = "deck:q1", canSkip = true)
        val next = room(questionId = "deck:q2", canSkip = true)

        assertTrue(isSameRoundQuestionReplacement(previous, next))
        assertFalse(isSameRoundQuestionReplacement(previous, next.copy(phase = Phase.ROUND_REVEAL)))
    }

    @Test
    fun skipRequiresUnlockedEligibleSubjectAndNoPendingCommand() {
        val eligible = UiState(
            screen = Screen.Submit,
            playerId = "subject",
            room = room(questionId = "deck:q1", canSkip = true),
        )
        assertTrue(canSkipCurrentQuestion(eligible))
        assertFalse(canSkipCurrentQuestion(eligible.copy(skippingQuestion = true)))
        assertFalse(canSkipCurrentQuestion(eligible.copy(submitting = true)))

        val predictorRoom = eligible.room!!.copy(youAre = "predictor")
        assertFalse(canSkipCurrentQuestion(eligible.copy(room = predictorRoom)))

        val lockedRound = eligible.room.currentRound!!.copy(submitted = mapOf("subject" to true))
        assertFalse(canSkipCurrentQuestion(eligible.copy(room = eligible.room.copy(currentRound = lockedRound))))
    }

    private fun room(questionId: String, canSkip: Boolean) = RoomStateView(
        code = "ABCD",
        phase = Phase.ROUND_SUBMIT,
        youAre = "subject",
        currentRound = RoundView(
            index = 0,
            subjectId = "subject",
            question = Question(questionId, "deck", "Rank these", options),
            skipsRemaining = 8,
            canSkip = canSkip,
        ),
    )
}
