package io.rank5.app.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rank5.app.auth.AuthRepository
import io.rank5.app.auth.AuthState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ClaimStatus {
    data object Idle : ClaimStatus
    data object Claiming : ClaimStatus
    data class Saved(val games: Int) : ClaimStatus
    data class Failed(val message: String) : ClaimStatus
}

data class StatsUiState(
    val loading: Boolean = false,
    val stats: StatsResponse? = null,
    val loadError: String? = null,
    val claimStatus: ClaimStatus = ClaimStatus.Idle,
)

class StatsViewModel(
    private val repository: StatsRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var pendingClaim: GuestResultClaim? = null

    init {
        viewModelScope.launch {
            auth.state.collect { authState ->
                when (authState) {
                    AuthState.SignedOut -> {
                        refreshJob?.cancel()
                        _state.value = StatsUiState()
                    }
                    is AuthState.SignedIn -> refresh()
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            try {
                val stats = repository.load()
                _state.update { it.copy(loading = false, stats = stats, loadError = null) }
            } catch (_: Exception) {
                _state.update {
                    it.copy(loading = false, loadError = "Couldn't load stats. Try again.")
                }
            }
        }
    }

    fun claim(claim: GuestResultClaim) {
        if (_state.value.claimStatus is ClaimStatus.Claiming) return
        pendingClaim = claim
        viewModelScope.launch {
            _state.update { it.copy(claimStatus = ClaimStatus.Claiming) }
            try {
                val claimed = repository.claim(claim)
                pendingClaim = null
                _state.update { it.copy(claimStatus = ClaimStatus.Saved(claimed)) }
                refresh()
            } catch (_: Exception) {
                _state.update {
                    it.copy(claimStatus = ClaimStatus.Failed("Couldn't save this game."))
                }
            }
        }
    }

    fun retryClaim() {
        pendingClaim?.let(::claim)
    }

    fun clearClaimStatus() {
        if (_state.value.claimStatus !is ClaimStatus.Claiming) {
            _state.update { it.copy(claimStatus = ClaimStatus.Idle) }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
