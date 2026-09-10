package io.rank5.app.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Shared motion vocabulary. Every screen transition, list placement and reveal
 * draws from these tokens so the whole product moves with one feel. Keep
 * transitions short and reserve them for state/layout changes.
 */
object Motion {
    // Durations (ms).
    const val fastMillis = 150
    const val standardMillis = 250
    const val enterMs = 220
    const val exitMs = 160
    const val staggerMs = 90
    /** Beat between rows on the Reveal screen; slower than a list stagger for drama. */
    const val revealStepMs = 340
    const val countUpMs = 800

    // Drag-to-reorder physics.
    const val liftedScale = 1.025f
    const val standardDamping = Spring.DampingRatioNoBouncy
    const val momentumDamping = 0.8f
    const val standardStiffness = Spring.StiffnessMedium
    const val quickStiffness = Spring.StiffnessHigh

    /** Fraction of the container a screen travels during a slide. */
    private const val SCREEN_SLIDE_DIVISOR = 14
    private const val TAB_SLIDE_DIVISOR = 6

    // Retargetable spring keeps velocity when reactive state changes mid-flight,
    // following the I/O "reentrant, continuous, smooth" guidance.
    val layout: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = standardDamping,
        stiffness = standardStiffness,
    )

    /** Spring used for LazyList `animateItem` placement across the app. */
    fun placementSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = momentumDamping,
        stiffness = standardStiffness,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    private fun screenSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = standardDamping,
        stiffness = standardStiffness,
    )

    /** Forward step inside the live game (Lobby -> Submit -> Reveal -> Results). */
    fun screenEnter(): EnterTransition =
        slideInVertically(screenSpring()) { it / SCREEN_SLIDE_DIVISOR } + fadeIn(tween(enterMs))

    fun screenExit(): ExitTransition =
        slideOutVertically(screenSpring()) { -it / SCREEN_SLIDE_DIVISOR } + fadeOut(tween(exitMs))

    /**
     * Horizontal slide for sibling destinations (bottom tabs, Decks stack).
     * [forward] = moving right/deeper; the exit mirrors the direction.
     */
    fun tabEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(screenSpring()) { full ->
            if (forward) full / TAB_SLIDE_DIVISOR else -full / TAB_SLIDE_DIVISOR
        } + fadeIn(tween(enterMs))

    fun tabExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(screenSpring()) { full ->
            if (forward) -full / TAB_SLIDE_DIVISOR else full / TAB_SLIDE_DIVISOR
        } + fadeOut(tween(exitMs))

    /** Row/card entrance for staggered lists; [delayMs] offsets each row. */
    fun itemEnter(delayMs: Int = 0): EnterTransition =
        fadeIn(tween(enterMs, delayMillis = delayMs)) +
            expandVertically(tween(enterMs, delayMillis = delayMs), expandFrom = Alignment.Top) +
            scaleIn(tween(enterMs, delayMillis = delayMs), initialScale = 0.96f)

    fun itemExit(): ExitTransition = fadeOut(tween(exitMs)) + shrinkVertically(tween(exitMs))

    /** Pop-in for content inside a fixed-size slot (reveal cells, badges). */
    fun popIn(bouncy: Boolean = false, delayMs: Int = 0): EnterTransition =
        fadeIn(tween(enterMs, delayMillis = delayMs)) + scaleIn(
            animationSpec = if (delayMs == 0) {
                spring(
                    dampingRatio = if (bouncy) Spring.DampingRatioMediumBouncy else standardDamping,
                    stiffness = standardStiffness,
                )
            } else {
                tween(enterMs, delayMillis = delayMs)
            },
            initialScale = if (bouncy) 0.6f else 0.85f,
        )

    /** Plain crossfade for in-place content swaps (labels, status banners). */
    fun crossfadeEnter(): EnterTransition = fadeIn(tween(enterMs))
    fun crossfadeExit(): ExitTransition = fadeOut(tween(exitMs))

    /**
     * True when the user disabled animations (accessibility "Remove animations"
     * or a 0x developer animator scale). Callers should reveal content instantly.
     */
    fun reducedMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()
}

/** Compose equivalent of animateLayoutChanges for state-driven containers. */
fun Modifier.animateLayoutChanges(): Modifier = animateContentSize(animationSpec = Motion.layout)
