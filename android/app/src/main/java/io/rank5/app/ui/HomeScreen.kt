package io.rank5.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.rank5.app.R
import io.rank5.app.game.BusyAction
import io.rank5.app.game.UiState
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.Rank5BrandMark
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing
import java.util.Locale

private const val MAX_CODE_LENGTH = 6
private const val MIN_CODE_LENGTH = 4
private enum class OnlineChoice { Create, Join }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onNickname: (String) -> Unit,
    onJoinCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onPassAndPlay: () -> Unit,
) {
    var onlineChoice by rememberSaveable { mutableStateOf<OnlineChoice?>(null) }
    var showHowToPlay by rememberSaveable { mutableStateOf(false) }
    val inactiveSnackbar = remember { SnackbarHostState() }

    GameScaffold(
        snackbarHostState = if (onlineChoice == null) snackbarHostState else inactiveSnackbar,
        scrollable = true,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Rank5BrandMark()
            Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showHowToPlay = true }) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, stringResource(R.string.how_to_play),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.home_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(Spacing.lg))
        Surface(
            onClick = onPassAndPlay,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(Spacing.lg)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(Sizes.compactIcon))
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(stringResource(R.string.no_wifi_needed),
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pass_and_play), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(R.string.pass_and_play_help), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.play_online_together), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.play_online_help), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OnlineModeCard(
                stringResource(R.string.create_room), stringResource(R.string.create_room_help),
                Icons.Rounded.Add, { onlineChoice = OnlineChoice.Create },
                Modifier.weight(1f).fillMaxHeight(),
            )
            OnlineModeCard(
                stringResource(R.string.join_room), stringResource(R.string.join_room_help),
                Icons.AutoMirrored.Rounded.Login, { onlineChoice = OnlineChoice.Join },
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.home_reassurance), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.lg))
    }

    onlineChoice?.let { choice ->
        ModalBottomSheet(
            onDismissRequest = { onlineChoice = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            OnlineSetup(
                choice, state, snackbarHostState, onNickname, onJoinCode, onCreate, onJoin,
                onClose = { onlineChoice = null },
            )
        }
    }
    if (showHowToPlay) {
        AlertDialog(
            onDismissRequest = { showHowToPlay = false },
            title = { Text(stringResource(R.string.how_to_play)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    HowToStep("1", stringResource(R.string.how_rank), stringResource(R.string.how_rank_help))
                    HowToStep("2", stringResource(R.string.how_guess), stringResource(R.string.how_guess_help))
                    HowToStep("3", stringResource(R.string.how_reveal), stringResource(R.string.how_reveal_help))
                }
            },
            confirmButton = { TextButton(onClick = { showHowToPlay = false }) {
                Text(stringResource(R.string.got_it))
            } },
        )
    }
}

@Composable
private fun OnlineModeCard(
    title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier,
) {
    Surface(
        onClick = onClick, modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Icon(icon, null, Modifier.padding(Spacing.sm), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xs))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OnlineSetup(
    choice: OnlineChoice,
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onNickname: (String) -> Unit,
    onJoinCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onClose: () -> Unit,
) {
    val isJoin = choice == OnlineChoice.Join
    val nameValid = state.nickname.trim().isNotEmpty()
    val codeValid = state.joinCode.length in MIN_CODE_LENGTH..MAX_CODE_LENGTH
    val busy = state.busyAction != null
    val canSubmit = nameValid && (!isJoin || codeValid) && !busy
    val focus = LocalFocusManager.current
    val submit = {
        if (canSubmit) {
            focus.clearFocus()
            if (isJoin) onJoin() else onCreate()
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (isJoin) R.string.join_room_setup else R.string.create_room_setup),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
        }
        Text(stringResource(if (isJoin) R.string.join_room_explanation else R.string.start_room_explanation),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(
            value = state.nickname, onValueChange = { onNickname(it.take(28)) },
            label = { Text(stringResource(R.string.your_name)) },
            placeholder = { Text(stringResource(R.string.name_placeholder)) },
            singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,
                imeAction = if (isJoin) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isJoin) {
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.joinCode,
                onValueChange = { value -> onJoinCode(value.uppercase(Locale.ROOT)
                    .filter { it in 'A'..'Z' || it in '0'..'9' }.take(MAX_CODE_LENGTH)) },
                label = { Text(stringResource(R.string.room_code)) },
                placeholder = { Text(stringResource(R.string.room_code_placeholder)) },
                supportingText = { Text(stringResource(R.string.room_code_help)) },
                singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                textStyle = MaterialTheme.typography.titleLarge,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        SnackbarHost(snackbarHostState)
        PrimaryCta(
            text = stringResource(when {
                state.busyAction == BusyAction.Create -> R.string.creating_room
                state.busyAction == BusyAction.Join -> R.string.joining_room
                isJoin -> R.string.join_game
                else -> R.string.create_game
            }),
            onClick = submit, enabled = canSubmit, loading = busy,
        )
    }
}

@Composable
private fun HowToStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
            Text(number, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xs))
            Text(body, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
