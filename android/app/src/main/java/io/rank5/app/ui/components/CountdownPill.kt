package io.rank5.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.rank5.app.ui.theme.Motion
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val PULSE_MS = 500

/**
 * Compact countdown pill driven by an absolute server deadline. Turns urgent
 * (error color + gentle pulse) at <=10s, clamps to "…" at zero, and fires
 * [onExpire] exactly once per deadline.
 */
@Composable
fun CountdownPill(
    deadlineMs: Long?,
    totalMs: Long,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    onFinalSecond: (Int) -> Unit = {},
    onTimeUp: () -> Unit = {},
) {
    if (deadlineMs == null || deadlineMs <= 0L) return

    var remainingMs by remember(deadlineMs) {
        mutableLongStateOf((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    val latestOnExpire by rememberUpdatedState(onExpire)
    val latestOnFinalSecond by rememberUpdatedState(onFinalSecond)
    val latestOnTimeUp by rememberUpdatedState(onTimeUp)

    LaunchedEffect(deadlineMs) {
        var fired = false
        var lastFinalSecond: Int? = null
        while (true) {
            val left = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
            remainingMs = left
            val second = ((left + 999) / 1000).toInt()
            if (second in 1..3 && second != lastFinalSecond) {
                lastFinalSecond = second
                latestOnFinalSecond(second)
            }
            if (left <= 0L) {
                if (!fired) {
                    fired = true
                    latestOnTimeUp()
                    latestOnExpire()
                }
                break
            }
            delay(200)
        }
    }

    val seconds = ((remainingMs + 999) / 1000).toInt()
    val urgent = seconds <= 10
    // Per component spec: secondary while calm, error when urgent. (The light
    // accentText is persimmon, visually identical to error — urgency would vanish.)
    val accent by animateColorAsState(
        targetValue = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        animationSpec = tween(Motion.enterMs),
        label = "countdown-accent",
    )
    // Gentle heartbeat on the digits for the final ten seconds; the infinite
    // transition only exists while urgent so calm rounds don't animate at all.
    val pulseScale = if (urgent && !Motion.reducedMotion()) {
        rememberInfiniteTransition(label = "countdown-pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "countdown-pulse-scale",
        ).value
    } else {
        1f
    }
    val progress = if (totalMs > 0) {
        (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val timeText = if (remainingMs <= 0L) {
        "…"
    } else {
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Coarse (10s-bucket) description so the polite live region doesn't
            // announce every tick, but still calls out ~10s and expiry.
            val announced = if (remainingMs <= 0L) {
                "Time's up"
            } else {
                "${((seconds + 9) / 10) * 10} seconds left"
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = announced
                    },
            )
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(Sizes.timerBar),
                color = accent,
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
