package io.rank5.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.rank5.app.BuildConfig
import io.rank5.app.R
import io.rank5.app.audio.AudioSettings
import io.rank5.app.auth.AuthState
import io.rank5.app.stats.StatsResponse
import io.rank5.app.stats.StatsUiState
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PlayerChip
import io.rank5.app.ui.components.SecondaryCta
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.theme.Spacing
import io.rank5.app.ui.theme.Sizes
import java.util.Locale

@Composable
fun ProfileScreen(
    authState: AuthState,
    snackbarHostState: SnackbarHostState,
    busy: Boolean,
    myDeckCount: Int = 0,
    statsState: StatsUiState = StatsUiState(),
    audioSettings: AudioSettings = AudioSettings(),
    onSignInGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onOpenMyDecks: () -> Unit = {},
    onRetryStats: () -> Unit = {},
    onGameSoundsEnabled: (Boolean) -> Unit = {},
    onGameSoundsVolume: (Float) -> Unit = {},
    onAllowInSilentMode: (Boolean) -> Unit = {},
    onPreviewSound: () -> Unit = {},
    onAppear: () -> Unit = {},
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val privacyUrl = BuildConfig.SERVER_BASE_URL.trimEnd('/') + "/privacy"

    LaunchedEffect(Unit) { onAppear() }

    GameScaffold(snackbarHostState = snackbarHostState, scrollable = true) {
        Text("Profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Spacing.md))

        SectionLabel("ACCOUNT")
        Spacer(Modifier.height(Spacing.sm))

        when (authState) {
            is AuthState.SignedOut -> {
                Text(
                    "Save your progress",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Sign in to keep your game stats, stable identity, and private decks. Playing without an account still works — login is optional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                GoogleSignInButton(
                    onClick = onSignInGoogle,
                    busy = busy,
                )
            }
            is AuthState.SignedIn -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        PlayerChip(
                            name = authState.user.displayName,
                            colorSeed = authState.user.avatarSeed.ifBlank { authState.user.id },
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text("Your games and decks sync to this profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                StatsSection(state = statsState, onRetry = onRetryStats)
                Spacer(Modifier.height(Spacing.md))
                SectionLabel("MY DECKS")
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (myDeckCount == 0) "No decks yet" else "$myDeckCount deck${if (myDeckCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onOpenMyDecks) {
                    Text("Open Decks")
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        AudioSettingsSection(
            settings = audioSettings,
            onGameSoundsEnabled = onGameSoundsEnabled,
            onGameSoundsVolume = onGameSoundsVolume,
            onAllowInSilentMode = onAllowInSilentMode,
            onPreviewSound = onPreviewSound,
        )
        Spacer(Modifier.height(Spacing.md))

        when (authState) {
            is AuthState.SignedOut -> {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                }) {
                    Text("Privacy policy")
                }
            }
            is AuthState.SignedIn -> {
                SecondaryCta(
                    text = "Sign out",
                    onClick = onSignOut,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.md))
                SectionLabel("DANGER AREA")
                Spacer(Modifier.height(Spacing.sm))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text("Delete account", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Permanently removes your profile and private decks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(
                            onClick = { confirmDelete = true },
                            enabled = !busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Delete account") }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                }) {
                    Text("Privacy policy")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = {
                Text("This permanently deletes your Rank5 account. Past games keep nicknames and scores but unlink your identity.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Unmodified, pre-approved Android asset from Google's identity guidelines.
        // The 48 dp wrapper keeps the visible 180 x 40 dp asset's aspect ratio while
        // providing a full-size accessible touch target.
        Box(
            modifier = Modifier
                .width(Sizes.googleButtonWidth)
                .height(Sizes.googleButtonHeight)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Sign in with Google"
                }
                .clickable(
                    enabled = !busy,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.google_sign_in),
                contentDescription = null,
                modifier = Modifier
                    .width(Sizes.googleButtonWidth)
                    .height(Sizes.googleButtonArtworkHeight),
            )
        }
        if (busy) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Sizes.ctaSpinner),
                    strokeWidth = Sizes.ctaSpinnerStroke,
                )
                Text(
                    "Signing in…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioSettingsSection(
    settings: AudioSettings,
    onGameSoundsEnabled: (Boolean) -> Unit,
    onGameSoundsVolume: (Float) -> Unit,
    onAllowInSilentMode: (Boolean) -> Unit,
    onPreviewSound: () -> Unit,
) {
    SectionLabel("SOUND")
    Spacer(Modifier.height(Spacing.sm))
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            SettingSwitchRow(
                title = "Game sounds",
                supporting = "Meaningful cues for ranking, reveals, and results",
                checked = settings.gameSoundsEnabled,
                onCheckedChange = onGameSoundsEnabled,
            )
            if (settings.gameSoundsEnabled) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Volume ${Math.round(settings.gameSoundsVolume * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.gameSoundsVolume,
                    onValueChange = onGameSoundsVolume,
                    valueRange = 0f..1f,
                    modifier = Modifier.semantics {
                        contentDescription = "Game sounds volume"
                    },
                )
                TextButton(onClick = onPreviewSound) { Text("Preview sound") }
            }
            Spacer(Modifier.height(Spacing.sm))
            SettingSwitchRow(
                title = "Allow in silent mode",
                supporting = "Off by default so Rank5 follows your device",
                checked = settings.allowInSilentMode,
                onCheckedChange = onAllowInSilentMode,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatsSection(state: StatsUiState, onRetry: () -> Unit) {
    SectionLabel("YOUR STATS")
    Spacer(Modifier.height(Spacing.sm))
    when {
        state.loading && state.stats == null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(Sizes.badge))
            }
        }
        state.loadError != null && state.stats == null -> {
            Text(
                state.loadError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        state.stats == null || state.stats.gamesPlayed == 0 -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Play your first game to start building stats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
        else -> StatsContent(stats = state.stats)
    }
}

@Composable
private fun StatsContent(stats: StatsResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatTile(
            value = stats.gamesPlayed.toString(),
            label = "Games played",
            modifier = Modifier.fillMaxWidth(),
        )
    }

    stats.guessing?.let { guessing ->
        Spacer(Modifier.height(Spacing.sm))
        StatTile(
            value = "${guessing.accuracyPct}%",
            label = "Guess accuracy · ${guessing.predictionsTracked} predictions",
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (stats.coop.bestByRounds.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.md))
        Text("Best team scores", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        stats.coop.bestByRounds.forEach { best ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${best.rounds} rounds", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatStatNumber(best.score)} / ${formatStatNumber(best.maxScore)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (stats.superlatives.detailedGamesTracked > 0) {
        Spacer(Modifier.height(Spacing.md))
        Text("Superlatives", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatTile(
                value = "${stats.superlatives.bestGuesser}×",
                label = "Best guesser",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${stats.superlatives.mostPredictable}×",
                label = "Most predictable",
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (stats.guessing == null && stats.gamesPlayed > 0) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Detailed accuracy starts with newly tracked games.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatStatNumber(value: Int): String = String.format(Locale.US, "%,d", value)
