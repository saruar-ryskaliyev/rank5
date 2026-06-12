package io.rank5.app.ui.theme

import androidx.compose.ui.unit.dp

/** The only spacing steps allowed in UI code. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val screen = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Fixed component dimensions, kept here so no dp literals leak into components. */
object Sizes {
    val ctaHeight = 56.dp
    val ctaSpinner = 20.dp
    val ctaSpinnerStroke = 2.dp
    val badge = 32.dp
    val avatar = 32.dp
    val avatarLarge = 48.dp
    val iconTile = 48.dp
    val touchTarget = 48.dp
    val contentMax = 600.dp
    val wideContentMax = 960.dp
    val hairline = 1.dp
    val timerBar = 48.dp
    val confetti = 10.dp
    val dragHandle = 20.dp
    val dragHandleStroke = 2.dp
    val dragElevation = 8.dp
    val lockCheck = 48.dp
    val lockCheckStroke = 3.dp
}
