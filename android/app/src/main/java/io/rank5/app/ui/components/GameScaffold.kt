package io.rank5.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

/**
 * The single screen frame: gradient background, insets, edge padding, snackbar
 * host, and an optional footer pinned above the nav bar.
 */
@Composable
fun GameScaffold(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    maxContentWidth: androidx.compose.ui.unit.Dp = Sizes.contentMax,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.safeDrawingPadding(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).safeDrawingPadding().imePadding(),
        ) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
           Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = maxContentWidth)
                .padding(horizontal = Spacing.screen),
           ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Spacing.lg)
                    .then(
                        if (scrollable) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    ),
                content = content,
            )
            if (footer != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg, bottom = Spacing.lg),
                ) {
                    footer()
                }
            }
           }
          }
        }
    }
}
