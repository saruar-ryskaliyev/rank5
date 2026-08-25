package io.rank5.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

/** The single ranking-row card language shared by Submit and Reveal lists. */
@Composable
fun RankCard(
    rank: Int,
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outline),
        // Paper-on-paper tactility in light; border-only in dark.
        shadowElevation = if (isSystemInDarkTheme()) Sizes.flatElevation else Sizes.hairline,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(rank = rank)
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}
