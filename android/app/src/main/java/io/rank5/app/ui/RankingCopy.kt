package io.rank5.app.ui

import io.rank5.app.offline.OfflineGameEngine

/**
 * Explains the scoring under a reveal. A perfect guess is always worth the
 * same, but the cost of one misplaced position depends on how many options
 * there were, so the exact figure is only shown when it is a whole number.
 */
fun scoringLegend(optionCount: Int): String {
    val maxDisplacement = OfflineGameEngine.maxDisplacementFor(optionCount)
    val perPosition = if (maxDisplacement > 0) {
        OfflineGameEngine.PenaltyRange / maxDisplacement
    } else {
        0
    }
    val exact = maxDisplacement > 0 && perPosition * maxDisplacement == OfflineGameEngine.PenaltyRange
    return if (exact) {
        "Perfect match = 2,000 pts · every spot off costs $perPosition"
    } else {
        "Perfect match = 2,000 pts · the further off, the fewer points"
    }
}

/**
 * The "1 = most" legend under a ranking list. Rankings are no longer always
 * five items: "most likely" questions rank however many players are in the
 * room, so the last position is derived from the option count.
 */
fun rankingLegend(optionCount: Int, rankingPlayers: Boolean): String {
    val last = optionCount.coerceAtLeast(2)
    return if (rankingPlayers) {
        "1 = most likely · $last = least likely"
    } else {
        "1 = most · $last = least"
    }
}
