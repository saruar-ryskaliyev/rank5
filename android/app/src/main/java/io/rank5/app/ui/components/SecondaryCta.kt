package io.rank5.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.rank5.app.ui.theme.Sizes

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
        shape = MaterialTheme.shapes.large,
        modifier = modifier.heightIn(min = Sizes.ctaHeight),
        colors = ButtonDefaults.outlinedButtonColors(
            // Accent-as-text variant: AA-safe on background in both themes.
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        CtaContent(text = text, loading = loading)
    }
}
