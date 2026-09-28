package com.example.spotatlas.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotatlas.domain.City
import com.example.spotatlas.domain.ParkingRepository
import com.example.spotatlas.domain.ParkingSession
import com.example.spotatlas.domain.ParkingSpot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the map screen draws. */
data class MapUiState(
    val cities: List<City>,
    val selectedCity: City,
    val spots: List<ParkingSpot>,
    val selectedSpotId: String?,
    val activeSession: ParkingSession?,
) {
    val selectedSpot: ParkingSpot? get() = spots.firstOrNull { it.id == selectedSpotId }
}

class MapViewModel(
    private val repository: ParkingRepository,
    initialCityId: String? = null,
    initialSpotId: String? = null,
) : ViewModel() {

    private val selectedCityId = MutableStateFlow(
        initialCityId?.takeIf { id -> repository.cities.any { it.id == id } }
            ?: initialSpotId?.let { repository.spotById(it)?.cityId }
            ?: repository.cities.first().id,
    )
    private val selectedSpotId = MutableStateFlow(initialSpotId)

    val uiState: StateFlow<MapUiState> = combine(
        repository.spots,
        repository.activeSession,
        selectedCityId,
        selectedSpotId,
    ) { spots, session, cityId, spotId ->
        MapUiState(
            cities = repository.cities,
            selectedCity = repository.cityById(cityId) ?: repository.cities.first(),
            spots = spots.filter { it.cityId == cityId },
            selectedSpotId = spotId,
            activeSession = session,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MapUiState(
            cities = repository.cities,
            selectedCity = repository.cityById(selectedCityId.value) ?: repository.cities.first(),
            spots = repository.spotsInCityOrEmpty(selectedCityId.value),
            selectedSpotId = selectedSpotId.value,
            activeSession = null,
        ),
    )

    fun selectCity(cityId: String) {
        if (selectedCityId.value == cityId) return
        selectedCityId.value = cityId
        selectedSpotId.value = null
    }

    fun selectSpot(spotId: String?) {
        selectedSpotId.value = spotId
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun ParkingRepository.spotsInCityOrEmpty(cityId: String): List<ParkingSpot> =
    searchSpots(cityId = cityId, onlyAvailable = false, maxResults = Int.MAX_VALUE)
