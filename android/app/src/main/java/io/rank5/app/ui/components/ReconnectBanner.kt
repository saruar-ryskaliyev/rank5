package io.rank5.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.rank5.app.ui.theme.Motion
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

/**
 * Persistent strip shown while the socket is re-establishing. The snackbar
 * already announces the drop; this stays until the room state flows again so
 * players know why buttons are not responding.
 */
@Composable
fun ReconnectBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = Motion.itemEnter(),
        exit = Motion.itemExit(),
        label = "reconnect-banner",
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Sizes.ctaSpinner),
                    strokeWidth = Sizes.ctaSpinnerStroke,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text("Reconnecting…", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hang tight — your spot in the room is saved.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
