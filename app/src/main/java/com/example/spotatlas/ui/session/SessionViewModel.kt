package com.example.spotatlas.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotatlas.domain.ParkingException
import com.example.spotatlas.domain.ParkingRepository
import com.example.spotatlas.domain.ParkingSession
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The running stay with a live cost, or the last finished one once it has been stopped. */
data class SessionUiState(
    val session: ParkingSession?,
    val elapsed: Duration,
    val remaining: Duration?,
    val cost: Double,
    val isStopping: Boolean,
    val errorMessage: String?,
) {
    val isActive: Boolean get() = session?.isActive == true
}

class SessionViewModel(
    private val repository: ParkingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(val isStopping: Boolean = false, val errorMessage: String? = null)

    /** Drives the cost and countdown readouts. */
    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<SessionUiState> = combine(
        repository.activeSession,
        repository.sessionHistory,
        ticker,
        transient,
    ) { active, history, now, current ->
        val session = active ?: history.firstOrNull()
        session.toUiState(now, current)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null.toUiState(clock.instant(), TransientState()),
    )

    fun consumeErrorMessage() = transient.update { it.copy(errorMessage = null) }

    fun stopParking() {
        if (transient.value.isStopping) return
        transient.update { it.copy(isStopping = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.endSession(null)
                transient.update { it.copy(isStopping = false) }
            } catch (failure: ParkingException) {
                transient.update { it.copy(isStopping = false, errorMessage = failure.message) }
            }
        }
    }

    private fun ParkingSession?.toUiState(now: Instant, current: TransientState) = SessionUiState(
        session = this,
        elapsed = this?.let { Duration.between(it.startedAt, it.endedAt ?: now) } ?: Duration.ZERO,
        remaining = this?.expiresAt?.takeIf { endedAt == null }?.let { Duration.between(now, it) },
        cost = this?.costAt(now) ?: 0.0,
        isStopping = current.isStopping,
        errorMessage = current.errorMessage,
    )

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
