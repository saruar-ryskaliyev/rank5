package io.rank5.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.rank5.app.ui.theme.Motion
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.ctaHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            // While loading, keep the active look; when truly disabled, keep an
            // AA-contrast label instead of M3's 38%-alpha default.
            disabledContainerColor = if (loading) scheme.primary else scheme.surfaceVariant,
            disabledContentColor = if (loading) scheme.onPrimary else scheme.onSurfaceVariant,
        ),
    ) {
        CtaContent(text = text, loading = loading)
    }
}

/**
 * Shared button body: the spinner grows in from the label's leading edge and
 * label changes ("Create game" -> "Creating room...") crossfade instead of jumping.
 */
@Composable
internal fun RowScope.CtaContent(text: String, loading: Boolean) {
    AnimatedVisibility(
        visible = loading,
        enter = fadeIn(tween(Motion.enterMs)) + expandHorizontally(tween(Motion.enterMs)),
        exit = fadeOut(tween(Motion.exitMs)) + shrinkHorizontally(tween(Motion.exitMs)),
        label = "cta-spinner",
    ) {
        Row {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.ctaSpinner),
                color = LocalContentColor.current,
                strokeWidth = Sizes.ctaSpinnerStroke,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = { Motion.crossfadeEnter() togetherWith Motion.crossfadeExit() },
        label = "cta-label",
    ) { label ->
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
