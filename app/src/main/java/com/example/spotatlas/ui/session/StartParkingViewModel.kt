package com.example.spotatlas.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotatlas.domain.ParkingException
import com.example.spotatlas.domain.ParkingRepository
import com.example.spotatlas.domain.ParkingSpot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the confirmation screen needs, including the values a deep link pre-filled. */
data class StartParkingUiState(
    val spot: ParkingSpot?,
    val vehiclePlate: String,
    val knownPlates: List<String>,
    /** Null means an open-ended stay, billed when it is stopped. */
    val durationMinutes: Int?,
    val isStarting: Boolean,
    val blockingSessionSpotName: String?,
    val errorMessage: String?,
    /** Set once the stay has started, so the screen can navigate on. */
    val startedSessionId: String?,
) {
    val canStart: Boolean
        get() = spot != null &&
            !spot.isFull &&
            vehiclePlate.isNotBlank() &&
            !isStarting &&
            blockingSessionSpotName == null
}

class StartParkingViewModel(
    private val repository: ParkingRepository,
    private val spotId: String,
    prefillVehiclePlate: String? = null,
    prefillDurationMinutes: Int? = null,
) : ViewModel() {

    private data class Form(
        val vehiclePlate: String,
        val durationMinutes: Int?,
        val isStarting: Boolean = false,
        val errorMessage: String? = null,
        val startedSessionId: String? = null,
    )

    private val form = MutableStateFlow(
        Form(
            vehiclePlate = prefillVehiclePlate?.takeIf { it.isNotBlank() }
                ?: repository.knownVehiclePlates.firstOrNull().orEmpty(),
            durationMinutes = prefillDurationMinutes?.takeIf { it > 0 },
        ),
    )

    val uiState: StateFlow<StartParkingUiState> =
        combine(repository.spots, repository.activeSession, form) { spots, active, current ->
            StartParkingUiState(
                spot = spots.firstOrNull { it.id == spotId },
                vehiclePlate = current.vehiclePlate,
                knownPlates = repository.knownVehiclePlates,
                durationMinutes = current.durationMinutes,
                isStarting = current.isStarting,
                blockingSessionSpotName = active?.spotName,
                errorMessage = current.errorMessage,
                startedSessionId = current.startedSessionId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StartParkingUiState(
                spot = repository.spotById(spotId),
                vehiclePlate = form.value.vehiclePlate,
                knownPlates = repository.knownVehiclePlates,
                durationMinutes = form.value.durationMinutes,
                isStarting = false,
                blockingSessionSpotName = null,
                errorMessage = null,
                startedSessionId = null,
            ),
        )

    fun setVehiclePlate(plate: String) = form.update { it.copy(vehiclePlate = plate) }

    fun setDurationMinutes(minutes: Int?) = form.update { it.copy(durationMinutes = minutes) }

    fun consumeErrorMessage() = form.update { it.copy(errorMessage = null) }

    fun startParking() {
        if (form.value.isStarting) return
        form.update { it.copy(isStarting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val session = repository.startSession(
                    spotId = spotId,
                    vehiclePlate = form.value.vehiclePlate,
                    durationMinutes = form.value.durationMinutes,
                )
                form.update { it.copy(isStarting = false, startedSessionId = session.id) }
            } catch (failure: ParkingException) {
                form.update { it.copy(isStarting = false, errorMessage = failure.message) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
