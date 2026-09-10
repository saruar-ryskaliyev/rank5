package io.rank5.app.ui

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
