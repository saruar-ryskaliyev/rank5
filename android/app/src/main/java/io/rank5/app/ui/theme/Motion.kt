package io.rank5.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/** Shared motion timings. Keep transitions short and reserve them for state/layout changes. */
object Motion {
    const val fastMillis = 150
    const val standardMillis = 250

    // Retargetable spring keeps velocity when reactive state changes mid-flight,
    // following the I/O "reentrant, continuous, smooth" guidance.
    val layout: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/** Compose equivalent of animateLayoutChanges for state-driven containers. */
fun Modifier.animateLayoutChanges(): Modifier = animateContentSize(animationSpec = Motion.layout)
