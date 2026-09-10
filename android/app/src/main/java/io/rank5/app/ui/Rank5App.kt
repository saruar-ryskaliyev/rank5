package io.rank5.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.rank5.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rank5.app.AuthViewModel
import io.rank5.app.audio.Rank5SoundPlayer
import io.rank5.app.auth.AuthState
import io.rank5.app.deck.DecksRoute
import io.rank5.app.deck.DecksViewModel
import io.rank5.app.game.GameViewModel
import io.rank5.app.game.MaxSelectedDecks
import io.rank5.app.game.Screen
import io.rank5.app.game.UiState
import io.rank5.app.offline.OfflineGameViewModel
import io.rank5.app.offline.OfflineScreen
import io.rank5.app.stats.GuestResultClaim
import io.rank5.app.stats.StatsViewModel
import io.rank5.app.ui.components.ReconnectBanner
import io.rank5.app.ui.theme.Motion
import kotlinx.coroutines.launch

private enum class AppTab { Play, Decks, Profile }
private enum class AppDestination { LiveGame, OfflineGame, Play, Decks, Profile }

/** Bottom-tab destinations in left-to-right order; games are not tabs. */
private fun AppDestination.tabIndex(): Int? = when (this) {
    AppDestination.Play -> 0
    AppDestination.Decks -> 1
    AppDestination.Profile -> 2
    AppDestination.LiveGame, AppDestination.OfflineGame -> null
}

private val GuestResultClaimSaver = listSaver<GuestResultClaim?, String>(
    save = { claim -> claim?.let { listOf(it.roomCode, it.playerId, it.reconnectToken) } ?: emptyList() },
    restore = { parts -> if (parts.size == 3) GuestResultClaim(parts[0], parts[1], parts[2]) else null },
)

/** Depth in the Decks stack; deeper destinations slide in from the right. */
private fun DecksRoute.depth(): Int = when (this) {
    DecksRoute.List -> 0
    is DecksRoute.Detail, DecksRoute.Generator -> 1
    is DecksRoute.Editor -> 2
}

@Composable
fun Rank5App(
    gameVm: GameViewModel,
    authVm: AuthViewModel,
    decksVm: DecksViewModel,
    statsVm: StatsViewModel,
    offlineVm: OfflineGameViewModel,
    soundPlayer: Rank5SoundPlayer,
) {
    val state by gameVm.state.collectAsStateWithLifecycle()
    val authState by authVm.state.collectAsStateWithLifecycle()
    val authBusy by authVm.busy.collectAsStateWithLifecycle()
    val authStatus by authVm.status.collectAsStateWithLifecycle()
    val decksState by decksVm.state.collectAsStateWithLifecycle()
    val statsState by statsVm.state.collectAsStateWithLifecycle()
    val offlineState by offlineVm.state.collectAsStateWithLifecycle()
    val audioSettings by soundPlayer.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity

    var tab by rememberSaveable { mutableStateOf(AppTab.Play) }
    var pendingGuestClaim by rememberSaveable(stateSaver = GuestResultClaimSaver) {
        mutableStateOf<GuestResultClaim?>(null)
    }
    var returnToDecksAfterSignIn by rememberSaveable { mutableStateOf(false) }
    var resumeReportAfterSignIn by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val inOnlineGame = state.screen != Screen.Home
    val inLiveGame = inOnlineGame || offlineState.active
    val showBottomBar = !inLiveGame &&
        (decksState.route is DecksRoute.List)
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    LaunchedEffect(state.statusMessage) {
        val status = state.statusMessage ?: return@LaunchedEffect
        if (status.isError) soundPlayer.playError()
        gameVm.statusShown()
        scope.launch { snackbarHostState.showSnackbar(status.text) }
    }
    LaunchedEffect(authStatus) {
        val status = authStatus ?: return@LaunchedEffect
        if (status.isError) soundPlayer.playError()
        authVm.statusShown()
        scope.launch { snackbarHostState.showSnackbar(status.text) }
    }
    LaunchedEffect(decksState.statusMessage) {
        val status = decksState.statusMessage ?: return@LaunchedEffect
        if (status.isError) soundPlayer.playError()
        decksVm.statusShown()
        scope.launch { snackbarHostState.showSnackbar(status.text) }
    }
    LaunchedEffect(authState) {
        if (authState is AuthState.SignedIn && returnToDecksAfterSignIn) {
            returnToDecksAfterSignIn = false
            tab = AppTab.Decks
            decksVm.refresh()
        } else if (tab == AppTab.Decks) {
            decksVm.refresh()
        }
    }
    LaunchedEffect(authState, pendingGuestClaim) {
        val signedIn = authState as? AuthState.SignedIn ?: return@LaunchedEffect
        val claim = pendingGuestClaim ?: return@LaunchedEffect
        gameVm.attachAccount(signedIn.token)
        statsVm.claim(claim)
        pendingGuestClaim = null
    }
    LaunchedEffect(state.screen) {
        if (state.screen != Screen.Results) statsVm.clearClaimStatus()
    }
    SoundEventObserver(state = state, soundPlayer = soundPlayer)

    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var showOfflineLeaveDialog by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = inOnlineGame) { showLeaveDialog = true }
    BackHandler(enabled = offlineState.active) {
        if (offlineState.screen in setOf(OfflineScreen.Setup, OfflineScreen.Results)) {
            offlineVm.close()
        } else {
            showOfflineLeaveDialog = true
        }
    }
    BackHandler(enabled = !inLiveGame && decksState.route !is DecksRoute.List && tab == AppTab.Decks) {
        when (val route = decksState.route) {
            is DecksRoute.Editor -> {
                if (route.deckId != null) decksVm.openDetail(route.deckId)
                else decksVm.openList()
            }
            is DecksRoute.Detail -> decksVm.backFromDetail()
            DecksRoute.Generator -> decksVm.openList()
            DecksRoute.List -> Unit
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave the game?") },
            text = {
                Text("Your spot will be removed. If fewer than two players remain, the game will end.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        gameVm.leaveToHome()
                        tab = AppTab.Play
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) { Text("Stay") }
            },
        )
    }

    if (showOfflineLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineLeaveDialog = false },
            title = { Text("Leave Pass & Play?") },
            text = { Text("This game’s progress is stored only on this phone and will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOfflineLeaveDialog = false
                        offlineVm.close()
                        tab = AppTab.Play
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Leave game") }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineLeaveDialog = false }) { Text("Keep playing") }
            },
        )
    }

    Scaffold(
        // Every destination owns safeDrawing/IME handling through GameScaffold.
        // Applying Scaffold's default safe insets here as well doubled the
        // status-bar/display-cutout space above every screen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = io.rank5.app.ui.theme.Sizes.flatElevation,
                ) {
                    NavigationBarItem(
                        selected = tab == AppTab.Play,
                        onClick = { tab = AppTab.Play },
                        icon = { Icon(Icons.Rounded.SportsEsports, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_play)) },
                        colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.Decks,
                        onClick = {
                            tab = AppTab.Decks
                            decksVm.openList()
                        },
                        icon = { Icon(Icons.Rounded.Style, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_decks)) },
                        colors = navColors,
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.Profile,
                        onClick = { tab = AppTab.Profile },
                        icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_profile)) },
                        colors = navColors,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val destination = when {
                inOnlineGame -> AppDestination.LiveGame
                offlineState.active -> AppDestination.OfflineGame
                tab == AppTab.Play -> AppDestination.Play
                tab == AppTab.Decks -> AppDestination.Decks
                else -> AppDestination.Profile
            }
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val fromTab = initialState.tabIndex()
                    val toTab = targetState.tabIndex()
                    when {
                        // Entering or leaving a game: vertical push, like a modal.
                        fromTab == null || toTab == null ->
                            Motion.screenEnter() togetherWith Motion.screenExit()
                        // Tab to tab: slide in the direction of travel along the bar.
                        else -> {
                            val forward = toTab > fromTab
                            Motion.tabEnter(forward) togetherWith Motion.tabExit(forward)
                        }
                    }
                },
                label = "app destination",
            ) { currentDestination ->
              // Each tab keeps its own scroll/filter state across switches.
              saveableStateHolder.SaveableStateProvider(currentDestination.name) {
              when (currentDestination) {
                AppDestination.LiveGame -> GameFlow(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    gameVm = gameVm,
                    signedIn = authState is AuthState.SignedIn,
                    signInBusy = authBusy,
                    claimStatus = statsState.claimStatus,
                    onSaveStats = {
                        val claim = gameVm.currentGuestResultClaim()
                        if (claim == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Couldn't prepare this game to save.")
                            }
                        } else {
                            pendingGuestClaim = claim
                            authVm.signInGoogle(activity)
                        }
                    },
                    onRetryClaim = statsVm::retryClaim,
                    onShare = {
                        scope.launch {
                            runCatching {
                                ResultShareManager.share(activity, ResultCardData.from(state))
                            }.onFailure {
                                snackbarHostState.showSnackbar("Couldn't share this result.")
                            }
                        }
                    },
                    onRequestLeave = { showLeaveDialog = true },
                    soundPlayer = soundPlayer,
                )
                AppDestination.OfflineGame -> OfflineGameFlow(
                    state = offlineState,
                    snackbarHostState = snackbarHostState,
                    onPlayerName = offlineVm::updatePlayer,
                    onAddPlayer = offlineVm::addPlayer,
                    onRemovePlayer = offlineVm::removePlayer,
                    onToggleDeck = offlineVm::toggleDeck,
                    onBrowseDecks = {
                        offlineVm.close()
                        tab = AppTab.Decks
                        decksVm.openList()
                    },
                    onSelectRounds = offlineVm::selectRounds,
                    onStart = {
                        soundPlayer.playGameStart()
                        offlineVm.start()
                    },
                    onRevealToActor = offlineVm::revealToActor,
                    onReorder = offlineVm::reorder,
                    onLockIn = {
                        soundPlayer.playLockIn()
                        offlineVm.lockIn()
                    },
                    onNextRound = {
                        if (offlineState.roundIndex == offlineState.schedule.lastIndex) {
                            soundPlayer.playFinalResults()
                        } else {
                            soundPlayer.playRoundReady()
                        }
                        offlineVm.nextRound()
                    },
                    onRematch = {
                        soundPlayer.playGameStart()
                        offlineVm.rematch()
                    },
                    onExit = {
                        if (offlineState.screen in setOf(OfflineScreen.Setup, OfflineScreen.Results)) {
                            offlineVm.close()
                            tab = AppTab.Play
                        } else {
                            showOfflineLeaveDialog = true
                        }
                    },
                    onDragStart = soundPlayer::playDragLift,
                    onRankCross = soundPlayer::playRankCross,
                )
                AppDestination.Play -> HomeScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onNickname = gameVm::updateNickname,
                    onJoinCode = gameVm::updateJoinCode,
                    onCreate = gameVm::createAndJoin,
                    onJoin = gameVm::joinRoom,
                    onPassAndPlay = {
                        soundPlayer.playSelectionTick()
                        offlineVm.open()
                    },
                )
                AppDestination.Decks -> DecksTab(
                    decksState = decksState,
                    authState = authState,
                    snackbarHostState = snackbarHostState,
                    decksVm = decksVm,
                    onGoSignIn = {
                        returnToDecksAfterSignIn = true
                        resumeReportAfterSignIn = decksState.route is DecksRoute.Detail
                        tab = AppTab.Profile
                    },
                    onGoSignInForSave = {
                        returnToDecksAfterSignIn = true
                        resumeReportAfterSignIn = false
                        tab = AppTab.Profile
                    },
                    resumeReportAfterSignIn = resumeReportAfterSignIn,
                    onReportResumed = { resumeReportAfterSignIn = false },
                    onUseDeck = { deck ->
                        gameVm.chooseDeck(deck)
                        decksVm.openList()
                        tab = AppTab.Play
                        scope.launch {
                            snackbarHostState.showSnackbar("${deck.title} selected for your next game")
                        }
                    },
                )
                AppDestination.Profile -> ProfileScreen(
                    authState = authState,
                    snackbarHostState = snackbarHostState,
                    busy = authBusy,
                    myDeckCount = decksState.mine.size,
                    statsState = statsState,
                    audioSettings = audioSettings,
                    onSignInGoogle = { authVm.signInGoogle(activity) },
                    onSignOut = authVm::signOut,
                    onDeleteAccount = authVm::deleteAccount,
                    onOpenMyDecks = {
                        tab = AppTab.Decks
                        decksVm.openList()
                    },
                    onRetryStats = statsVm::refresh,
                    onGameSoundsEnabled = soundPlayer::setGameSoundsEnabled,
                    onGameSoundsVolume = soundPlayer::setGameSoundsVolume,
                    onAllowInSilentMode = soundPlayer::setAllowInSilentMode,
                    onPreviewSound = soundPlayer::playGameStart,
                    onAppear = {
                        if (authState is AuthState.SignedIn) {
                            decksVm.refresh()
                            statsVm.refresh()
                        }
                    },
                )
              }
              }
            }
        }
    }
}

@Composable
private fun SoundEventObserver(state: UiState, soundPlayer: Rank5SoundPlayer) {
    var previous by remember { mutableStateOf<UiState?>(null) }

    LaunchedEffect(state.screen, state.room, state.reconnecting) {
        val before = previous
        if (before == null) {
            previous = state
            return@LaunchedEffect
        }

        val oldRoom = before.room
        val room = state.room

        if (before.reconnecting != state.reconnecting) {
            if (state.reconnecting) soundPlayer.playConnectionLost()
            else soundPlayer.playReconnectSuccess()
        }

        if (oldRoom != null && room != null && oldRoom.code == room.code) {
            val oldPlayers = oldRoom.players.associateBy { it.id }
            val players = room.players.associateBy { it.id }
            val joined = players.keys - oldPlayers.keys
            if (joined.isNotEmpty() && state.screen == Screen.Lobby) {
                soundPlayer.playPlayerJoin()
            }
            val disconnected = oldPlayers.values.any { old ->
                old.connected && players[old.id]?.connected != true
            }
            if (disconnected) soundPlayer.playPlayerLeave()

            val reconnected = players.values.any { current ->
                current.connected && oldPlayers[current.id]?.connected == false
            }
            if (reconnected) soundPlayer.playReconnectSuccess()

            val oldRound = oldRoom.currentRound
            val round = room.currentRound
            val sameQuestion = oldRound != null && round != null &&
                oldRound.index == round.index && oldRound.question.id == round.question.id
            if (sameQuestion) {
                val previousRound = requireNotNull(oldRound)
                val currentRound = requireNotNull(round)
                val newlySubmitted = currentRound.submitted.filter { (id, submitted) ->
                    submitted && previousRound.submitted[id] != true
                }.keys
                if (room.youAre in newlySubmitted) {
                    soundPlayer.playLockIn()
                } else if (newlySubmitted.any { it != room.youAre }) {
                    soundPlayer.playRemoteLock()
                }

                val connectedIds = room.players.filter { it.connected }.mapTo(mutableSetOf()) { it.id }
                val everyoneReady = connectedIds.isNotEmpty() &&
                    connectedIds.all { currentRound.ready[it] == true }
                val wasEveryoneReady = connectedIds.isNotEmpty() &&
                    connectedIds.all { previousRound.ready[it] == true }
                val newlyReady = currentRound.ready.filter { (id, ready) ->
                    ready && previousRound.ready[id] != true
                }.keys
                when {
                    everyoneReady && !wasEveryoneReady -> soundPlayer.playEveryoneReady()
                    newlyReady.isNotEmpty() -> soundPlayer.playRoundReady()
                }
            }
        }

        if (state.screen == Screen.Submit &&
            before.screen in setOf(Screen.Lobby, Screen.Results)
        ) {
            soundPlayer.playGameStart()
        }
        if (state.screen == Screen.Results && before.screen != Screen.Results) {
            soundPlayer.playFinalResults()
        }

        previous = state
    }
}

@Composable
private fun GameFlow(
    state: io.rank5.app.game.UiState,
    snackbarHostState: SnackbarHostState,
    gameVm: GameViewModel,
    signedIn: Boolean,
    signInBusy: Boolean,
    claimStatus: io.rank5.app.stats.ClaimStatus,
    onSaveStats: () -> Unit,
    onRetryClaim: () -> Unit,
    onShare: () -> Unit,
    onRequestLeave: () -> Unit,
    soundPlayer: Rank5SoundPlayer,
) {
    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = { Motion.screenEnter() togetherWith Motion.screenExit() },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Home -> Unit
            Screen.Lobby -> LobbyScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onSelectDeck = { id ->
                    val selected = state.selectedDecks
                    if (selected.any { it.id == id } || selected.size < MaxSelectedDecks) {
                        soundPlayer.playSelectionTick()
                    }
                    gameVm.selectDeck(id)
                },
                onRemoveDeck = { id ->
                    if (state.selectedDecks.size > 1) soundPlayer.playSelectionTick()
                    gameVm.removeDeck(id)
                },
                onSelectRounds = { rounds ->
                    if (state.selectedRounds != rounds) soundPlayer.playSelectionTick()
                    gameVm.selectRounds(rounds)
                },
                onLoadDeckLibrary = gameVm::loadDeckLibrary,
                onCommunityQuery = gameVm::setCommunityQuery,
                onPickCommunityDeck = { deck ->
                    val selected = state.selectedDecks
                    if (selected.any { it.id == deck.id } || selected.size < MaxSelectedDecks) {
                        soundPlayer.playSelectionTick()
                    }
                    gameVm.pickCommunityDeck(deck)
                },
                onStart = gameVm::startGame,
                onLeave = onRequestLeave,
                onCodeConfirmed = soundPlayer::playCodeConfirmed,
            )
            Screen.Submit -> SubmitScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onReorder = gameVm::setLocalRanking,
                onLockIn = gameVm::submitEntry,
                onSkipQuestion = gameVm::skipQuestion,
                onAutoSubmit = { gameVm.submitEntry(auto = true) },
                onDragStart = soundPlayer::playDragLift,
                onRankCross = soundPlayer::playRankCross,
                onCountdown = soundPlayer::playCountdown,
                onTimeUp = soundPlayer::playTimeUp,
            )
            Screen.Reveal -> RevealScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onReady = gameVm::markReady,
                onRevealCard = soundPlayer::playRevealCard,
                onScoreCountUp = soundPlayer::playScoreCountUp,
                onScoreOutcome = soundPlayer::playScoreOutcome,
            )
            Screen.Results -> ResultsScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onPlayAgain = gameVm::startGame,
                onLeave = gameVm::leaveToHome,
                signedIn = signedIn,
                signInBusy = signInBusy,
                claimStatus = claimStatus,
                onSaveStats = onSaveStats,
                onRetryClaim = onRetryClaim,
                onShare = onShare,
            )
        }
    }
    ReconnectBanner(
        visible = state.reconnecting,
        modifier = Modifier.align(Alignment.TopCenter),
    )
    }

    val room = state.room
    if (room?.paused == true) {
        val disconnected = room.players.filterNot { it.connected }
            .joinToString { it.nickname }
            .ifBlank { "Another player" }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Game paused") },
            text = {
                Text("$disconnected disconnected. The game will resume automatically if they reconnect; otherwise it will return to the lobby.")
            },
            confirmButton = {
                TextButton(onClick = gameVm::leaveToHome) { Text("Leave game") }
            },
        )
    }
}

@Composable
private fun DecksTab(
    decksState: io.rank5.app.deck.DecksUiState,
    authState: AuthState,
    snackbarHostState: SnackbarHostState,
    decksVm: DecksViewModel,
    onGoSignIn: () -> Unit,
    onGoSignInForSave: () -> Unit,
    resumeReportAfterSignIn: Boolean,
    onReportResumed: () -> Unit,
    onUseDeck: (io.rank5.app.deck.Deck) -> Unit,
) {
    val signedIn = authState is AuthState.SignedIn
    AnimatedContent(
        targetState = decksState.route,
        // Animate only when the destination type changes, not on Detail(a) -> Detail(b).
        contentKey = { it::class },
        transitionSpec = {
            val forward = targetState.depth() > initialState.depth()
            if (targetState.depth() == initialState.depth()) {
                Motion.crossfadeEnter() togetherWith Motion.crossfadeExit()
            } else {
                Motion.tabEnter(forward) togetherWith Motion.tabExit(forward)
            }
        },
        label = "decks-route",
    ) { route ->
    when (route) {
        DecksRoute.List -> DecksListScreen(
            state = decksState,
            snackbarHostState = snackbarHostState,
            signedIn = signedIn,
            onRefresh = decksVm::refresh,
            onOpenDeck = decksVm::openDetail,
            onQuery = decksVm::setCommunityQuery,
            onLoadMore = decksVm::loadMoreCommunity,
            onRefreshSaved = decksVm::refreshSaved,
            onCreate = decksVm::requestCreate,
            onDismissLoginPrompt = decksVm::dismissLoginPrompt,
            onGoSignIn = onGoSignIn,
        )
        is DecksRoute.Detail -> {
            val deck = decksState.detail
            val canEdit = deck != null && !deck.isBuiltin && signedIn &&
                (authState as? AuthState.SignedIn)?.user?.id == deck.ownerId
            DeckDetailScreen(
                deck = deck,
                loading = decksState.loading,
                saving = decksState.saving,
                canEdit = canEdit,
                signedIn = signedIn,
                isSaved = deck?.let { current -> decksState.saved.any { it.id == current.id } } == true,
                savingSaved = deck?.id?.let { it in decksState.savedOperations } == true,
                isDownloaded = deck?.id?.let { id ->
                    decksState.downloadedDecks.any { it.id == id }
                } == true,
                downloading = deck?.id?.let { it in decksState.downloadOperations } == true,
                snackbarHostState = snackbarHostState,
                onBack = decksVm::backFromDetail,
                onUseDeck = onUseDeck,
                onEdit = { deck?.let(decksVm::openEditor) },
                onDelete = decksVm::deleteCurrent,
                onPublish = decksVm::publishCurrent,
                onUnpublish = decksVm::unpublishCurrent,
                onReport = decksVm::reportCurrent,
                onToggleSaved = { deck?.let { decksVm.toggleSaved(it.id) } },
                onToggleDownloaded = decksVm::toggleDownloaded,
                onGoSignIn = onGoSignIn,
                onGoSignInForSave = onGoSignInForSave,
                resumeReportAfterSignIn = resumeReportAfterSignIn,
                onReportResumed = onReportResumed,
            )
        }
        DecksRoute.Generator -> DeckGeneratorScreen(
            topic = decksState.generationTopic,
            questionCount = decksState.generationQuestionCount,
            generating = decksState.generating,
            error = decksState.generationError,
            snackbarHostState = snackbarHostState,
            onTopic = decksVm::setGenerationTopic,
            onQuestionCount = decksVm::setGenerationQuestionCount,
            onGenerate = decksVm::generateDeck,
            onStartBlank = decksVm::startBlankDeck,
            onBack = decksVm::openList,
        )
        is DecksRoute.Editor -> DeckEditorScreen(
            title = decksState.editorTitle,
            emoji = decksState.editorEmoji,
            questions = decksState.editorQuestions,
            saving = decksState.saving,
            isNew = route.deckId == null,
            dirty = decksState.editorDirty,
            generatedDraft = decksState.editorGenerated,
            snackbarHostState = snackbarHostState,
            onTitle = decksVm::setEditorTitle,
            onEmoji = decksVm::setEditorEmoji,
            onPrompt = decksVm::setQuestionPrompt,
            onOption = decksVm::setQuestionOption,
            onAddQuestion = decksVm::addQuestion,
            onRemoveQuestion = decksVm::removeQuestion,
            onMoveQuestion = decksVm::moveQuestion,
            onSave = decksVm::saveEditor,
            onCancel = {
                if (route.deckId != null) decksVm.openDetail(route.deckId)
                else decksVm.openList()
            },
        )
    }
    }
}
