package io.rank5.app.ui.components

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import io.rank5.app.ui.theme.LocalRank5Extras
import io.rank5.app.ui.theme.Sizes
import kotlin.random.Random

private const val DURATION_MS = 1_200
private const val PARTICLE_COUNT = 40

private class Particle(
    val color: Color,
    val startX: Float,
    val vx: Float,
    val vy: Float,
    val sizeFactor: Float,
    val spin: Float,
)

private fun buildParticles(random: Random, palette: List<Color>): List<Particle> {
    return List(PARTICLE_COUNT) {
        Particle(
            color = palette[random.nextInt(palette.size)],
            startX = 0.5f + (random.nextFloat() - 0.5f) * 0.2f,
            vx = (random.nextFloat() - 0.5f) * 1.2f,
            vy = -(0.3f + random.nextFloat() * 0.8f),
            sizeFactor = 0.7f + random.nextFloat() * 0.8f,
            spin = (random.nextFloat() - 0.5f) * 720f,
        )
    }
}

/** One-shot celebratory particle burst; draws nothing once finished. */
@Composable
fun ConfettiBurst(play: Boolean, modifier: Modifier = Modifier) {
    if (!play || !ValueAnimator.areAnimatorsEnabled()) return

    val extras = LocalRank5Extras.current
    val particles = remember {
        buildParticles(Random(System.nanoTime()), extras.avatarPalette + extras.highlight)
    }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (progress < 1f) {
            withFrameNanos { now ->
                progress = ((now - startNanos) / (DURATION_MS * 1_000_000f)).coerceAtMost(1f)
            }
        }
    }

    if (progress >= 1f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress * (DURATION_MS / 1000f)
        val alpha = (1f - ((progress - 0.7f) / 0.3f)).coerceIn(0f, 1f)
        val base = Sizes.confetti.toPx()
        particles.forEach { p ->
            val x = (p.startX + p.vx * t) * size.width
            val y = (0.2f + p.vy * t + 0.55f * t * t) * size.height
            val side = base * p.sizeFactor
            rotate(degrees = p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - side / 2f, y - side / 2f),
                    size = Size(side, side * 0.6f),
                    alpha = alpha,
                )
            }
        }
    }
}
