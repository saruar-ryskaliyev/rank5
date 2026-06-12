package io.rank5.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.rank5.app.ui.theme.LocalRank5Extras
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

@Composable
fun SecondaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = Sizes.ctaHeight),
        colors = ButtonDefaults.outlinedButtonColors(
            // Accent-as-text variant: AA-safe on background in both themes.
            contentColor = LocalRank5Extras.current.accentText,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.ctaSpinner),
                color = LocalContentColor.current,
                strokeWidth = Sizes.ctaSpinnerStroke,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
