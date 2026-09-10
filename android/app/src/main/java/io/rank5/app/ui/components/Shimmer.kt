package io.rank5.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import io.rank5.app.ui.theme.Motion

private const val SHIMMER_MS = 1_200

/**
 * Sweeping highlight for loading placeholders. Falls back to a flat
 * surfaceVariant fill when the user has animations disabled.
 */
fun Modifier.shimmer(shape: Shape): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    if (Motion.reducedMotion()) {
        return@composed clip(shape).background(base)
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    val width = size.width.toFloat()
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(width * progress, 0f),
        end = Offset(width * (progress + 1f), size.height.toFloat()),
    )
    this
        .onSizeChanged { size = it }
        .clip(shape)
        .background(brush)
}

/** Full-width placeholder row of the given [height]. */
@Composable
fun ShimmerBlock(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .shimmer(MaterialTheme.shapes.medium),
    )
}
