package io.rank5.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import io.rank5.app.R
import io.rank5.app.game.BusyAction
import io.rank5.app.game.UiState
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.SecondaryCta
import io.rank5.app.ui.theme.Spacing

private const val MAX_CODE_LENGTH = 6
private const val MIN_CODE_LENGTH = 4

@Composable
fun HomeScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onNickname: (String) -> Unit,
    onJoinCode: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val creating = state.busyAction == BusyAction.Create
    val joining = state.busyAction == BusyAction.Join
    val nameValid = state.nickname.trim().isNotEmpty()
    val codeValid = state.joinCode.length in MIN_CODE_LENGTH..MAX_CODE_LENGTH
    val focus = LocalFocusManager.current

    GameScaffold(snackbarHostState = snackbarHostState, scrollable = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.rank5_brand_mark),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.home_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        OutlinedTextField(
            value = state.nickname,
            onValueChange = { onNickname(it.take(28)) },
            label = { Text(stringResource(R.string.your_name)) },
            supportingText = {
                if (!nameValid && state.nickname.isNotEmpty()) Text(stringResource(R.string.name_validation))
            },
            isError = !nameValid && state.nickname.isNotEmpty(),
            singleLine = true,
            enabled = state.busyAction == null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.xl))
        Text(stringResource(R.string.start_room), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.start_room_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryCta(
            text = if (creating) stringResource(R.string.creating_room) else stringResource(R.string.create_game),
            onClick = onCreate,
            enabled = nameValid && state.busyAction == null,
            loading = creating,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                stringResource(R.string.or_join_friends),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f))
        }

        OutlinedTextField(
            value = state.joinCode,
            onValueChange = { value ->
                onJoinCode(value.filter(Char::isLetterOrDigit).take(MAX_CODE_LENGTH))
            },
            label = { Text(stringResource(R.string.room_code)) },
            placeholder = { Text(stringResource(R.string.room_code_placeholder)) },
            supportingText = {
                Text(
                    if (state.joinCode.isEmpty() || codeValid) stringResource(R.string.room_code_help)
                    else stringResource(R.string.room_code_incomplete),
                )
            },
            isError = state.joinCode.isNotEmpty() && !codeValid,
            singleLine = true,
            enabled = state.busyAction == null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = {
                if (nameValid && codeValid) {
                    focus.clearFocus()
                    onJoin()
                }
            }),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        SecondaryCta(
            text = if (joining) stringResource(R.string.joining_room) else stringResource(R.string.join_game),
            onClick = onJoin,
            enabled = nameValid && codeValid && state.busyAction == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}
