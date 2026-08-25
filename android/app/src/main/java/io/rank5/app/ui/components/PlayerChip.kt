package io.rank5.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.rank5.app.ui.theme.LocalRank5Extras
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

/** The one player representation: avatar circle (initial + palette hue) + name. */
@Composable
fun PlayerChip(
    name: String,
    colorSeed: String,
    modifier: Modifier = Modifier,
    isHost: Boolean = false,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalRank5Extras.current.avatarPalette
    val hue = palette[((colorSeed.hashCode() % palette.size) + palette.size) % palette.size]

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.avatar)
                .background(color = hue, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.labelLarge,
                // Every palette swatch is verified >=3:1 against white.
                color = Color.White,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(text = name, style = MaterialTheme.typography.titleMedium)
        if (isHost) {
            Spacer(Modifier.width(Spacing.sm))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text("Host", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs))
            }
        }
        if (dimmed) {
            Spacer(Modifier.width(Spacing.sm))
            Text("Offline", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailing()
        }
    }
}
