package io.rank5.app.ui.theme

import androidx.compose.ui.unit.dp

/** The only four padding/margin steps allowed in UI code. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}

/** The only two corner radii used by containers and controls. */
object Corners {
    val compact = 12.dp
    val roomy = 20.dp
}

/** Fixed component dimensions, kept here so no dp literals leak into components. */
object Sizes {
    val flatElevation = 0.dp
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
    val brandMark = 64.dp
    val deckHero = 64.dp
    val metadataIcon = 18.dp
    val compactIcon = 32.dp
    val actionIcon = 40.dp
    val listRow = 72.dp
    val pickerMaxHeight = 480.dp
    val responsiveBreakpoint = 840.dp
    val sidebar = 320.dp
    val googleButtonWidth = 180.dp
    val googleButtonHeight = 48.dp
    val googleButtonArtworkHeight = 40.dp
}
