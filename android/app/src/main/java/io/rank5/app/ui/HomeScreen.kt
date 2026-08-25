package io.rank5.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.rank5.app.R
import io.rank5.app.game.BusyAction
import io.rank5.app.game.UiState
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

private const val MAX_CODE_LENGTH = 6
private const val MIN_CODE_LENGTH = 4

private enum class OnlineChoice { Create, Join }

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
    val creating = state.busyAction == BusyAction.Create
    val joining = state.busyAction == BusyAction.Join
    val nameValid = state.nickname.trim().isNotEmpty()
    val codeValid = state.joinCode.length in MIN_CODE_LENGTH..MAX_CODE_LENGTH
    val focus = LocalFocusManager.current
    var onlineChoice by rememberSaveable { mutableStateOf<OnlineChoice?>(null) }

    GameScaffold(snackbarHostState = snackbarHostState, scrollable = true) {
        BrandHeader()

        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.choose_game_mode),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.choose_game_mode_help),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.lg))
        SectionHeading(
            title = stringResource(R.string.play_offline),
            subtitle = stringResource(R.string.play_offline_help),
        )
        Spacer(Modifier.height(Spacing.sm))
        PlayModeCard(
            title = stringResource(R.string.pass_and_play),
            subtitle = stringResource(R.string.pass_and_play_help),
            icon = Icons.Rounded.WifiOff,
            selected = false,
            emphasized = true,
            onClick = onPassAndPlay,
        )

        Spacer(Modifier.height(Spacing.lg))
        SectionHeading(
            title = stringResource(R.string.play_online),
            subtitle = stringResource(R.string.play_online_help),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PlayModeCard(
                title = stringResource(R.string.create_room),
                subtitle = stringResource(R.string.create_room_help),
                icon = Icons.Rounded.AddCircleOutline,
                selected = onlineChoice == OnlineChoice.Create,
                onClick = { onlineChoice = OnlineChoice.Create },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PlayModeCard(
                title = stringResource(R.string.join_room),
                subtitle = stringResource(R.string.join_room_help),
                icon = Icons.AutoMirrored.Rounded.Login,
                selected = onlineChoice == OnlineChoice.Join,
                onClick = { onlineChoice = OnlineChoice.Join },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        AnimatedVisibility(visible = onlineChoice != null) {
            Column {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    if (onlineChoice == OnlineChoice.Create) {
                        stringResource(R.string.create_room_setup)
                    } else {
                        stringResource(R.string.join_room_setup)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = { onNickname(it.take(28)) },
                    label = { Text(stringResource(R.string.your_name)) },
                    isError = !nameValid && state.nickname.isNotEmpty(),
                    supportingText = {
                        if (!nameValid && state.nickname.isNotEmpty()) {
                            Text(stringResource(R.string.name_validation))
                        }
                    },
                    singleLine = true,
                    enabled = state.busyAction == null,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (onlineChoice == OnlineChoice.Join) ImeAction.Next else ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onlineChoice == OnlineChoice.Join) {
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = state.joinCode,
                        onValueChange = { value ->
                            onJoinCode(value.filter(Char::isLetterOrDigit).take(MAX_CODE_LENGTH))
                        },
                        label = { Text(stringResource(R.string.room_code)) },
                        placeholder = { Text(stringResource(R.string.room_code_placeholder)) },
                        supportingText = {
                            Text(
                                if (state.joinCode.isEmpty() || codeValid) {
                                    stringResource(R.string.room_code_help)
                                } else {
                                    stringResource(R.string.room_code_incomplete)
                                },
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
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(Spacing.md))
                PrimaryCta(
                    text = when (onlineChoice) {
                        OnlineChoice.Create -> if (creating) {
                            stringResource(R.string.creating_room)
                        } else {
                            stringResource(R.string.create_game)
                        }
                        OnlineChoice.Join -> if (joining) {
                            stringResource(R.string.joining_room)
                        } else {
                            stringResource(R.string.join_game)
                        }
                        null -> ""
                    },
                    onClick = if (onlineChoice == OnlineChoice.Create) onCreate else onJoin,
                    enabled = nameValid &&
                        (onlineChoice == OnlineChoice.Create || codeValid) &&
                        state.busyAction == null,
                    loading = creating || joining,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.rank5_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(Sizes.avatarLarge),
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
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val selectedOrEmphasized = selected || emphasized
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selectedOrEmphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) Sizes.ctaSpinnerStroke else Sizes.hairline,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selectedOrEmphasized) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(Sizes.actionIcon),
            )
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selectedOrEmphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedOrEmphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (emphasized) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
