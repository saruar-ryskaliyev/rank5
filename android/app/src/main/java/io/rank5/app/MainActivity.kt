package io.rank5.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import io.rank5.app.auth.AuthRepository
import io.rank5.app.auth.AuthState
import io.rank5.app.audio.Rank5SoundPlayer
import io.rank5.app.deck.DeckRepository
import io.rank5.app.deck.DecksViewModel
import io.rank5.app.game.GameViewModel
import io.rank5.app.stats.StatsRepository
import io.rank5.app.stats.StatsViewModel
import io.rank5.app.ui.Rank5App
import io.rank5.app.ui.theme.Rank5Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val authRepo by lazy { AuthRepository(applicationContext) }
    private val deckRepo by lazy { DeckRepository(authRepo) }
    private val statsRepo by lazy { StatsRepository(authRepo) }
    private val soundPlayer by lazy { Rank5SoundPlayer(applicationContext) }

    private val gameVm: GameViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T = GameViewModel(authRepo, deckRepo, extras.createSavedStateHandle()) as T
        }
    }

    private val authVm: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AuthViewModel(authRepo) as T
        }
    }

    private val decksVm: DecksViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras,
            ): T = DecksViewModel(deckRepo, extras.createSavedStateHandle()) as T
        }
    }

    private val statsVm: StatsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StatsViewModel(statsRepo, authRepo) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Rank5Theme {
                Rank5App(
                    gameVm = gameVm,
                    authVm = authVm,
                    decksVm = decksVm,
                    statsVm = statsVm,
                    soundPlayer = soundPlayer,
                )
            }
        }
    }

    override fun onStop() {
        soundPlayer.onBackground()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        soundPlayer.onForeground()
    }

    override fun onDestroy() {
        soundPlayer.release()
        super.onDestroy()
    }
}

class AuthViewModel(
    private val auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<AuthState> = auth.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    init {
        viewModelScope.launch { auth.restore() }
    }

    fun statusShown() {
        _status.value = null
    }

    fun signInGoogle(activity: ComponentActivity) {
        viewModelScope.launch {
            _busy.value = true
            try {
                auth.signInWithGoogle(activity)
            } catch (e: Exception) {
                _status.value = "Couldn’t sign in with Google. Try again."
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _status.value = "Signed out"
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _busy.value = true
            try {
                auth.deleteAccount()
                _status.value = "Account deleted"
            } catch (e: Exception) {
                _status.value = "Couldn’t delete the account. Try again."
            } finally {
                _busy.value = false
            }
        }
    }
}
