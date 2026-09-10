package io.rank5.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.rank5.app.ui.theme.Motion

/**
 * Plays the shared item entrance once on first composition, delayed by
 * [order] beats so sibling rows cascade. Content is shown immediately when the
 * user has animations disabled.
 */
@Composable
fun StaggeredAppear(
    order: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(Motion.reducedMotion()).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = Motion.itemEnter(delayMs = order * Motion.staggerMs),
        exit = ExitTransition.None,
        label = "staggered-appear",
    ) {
        content()
    }
}
