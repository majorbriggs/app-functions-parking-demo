package com.example.spotatlas.data

import com.example.spotatlas.domain.City
import com.example.spotatlas.domain.GeoPoint
import com.example.spotatlas.domain.ParkingException
import com.example.spotatlas.domain.ParkingRepository
import com.example.spotatlas.domain.ParkingSession
import com.example.spotatlas.domain.ParkingSpot
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-lifetime implementation backed by [ParkingCatalog].
 *
 * Starting a session takes a space from the chosen spot and stopping it gives the space back, so an
 * agent-driven action is visible on the map immediately.
 */
class InMemoryParkingRepository(
    private val clock: Clock = Clock.systemUTC(),
    catalog: List<ParkingSpot> = ParkingCatalog.spots,
    override val cities: List<City> = ParkingCatalog.cities,
    override val knownVehiclePlates: List<String> = ParkingCatalog.vehiclePlates,
) : ParkingRepository {

    private val spotsState = MutableStateFlow(catalog)
    private val activeSessionState = MutableStateFlow<ParkingSession?>(null)
    private val historyState = MutableStateFlow<List<ParkingSession>>(emptyList())

    /** Guards the read-modify-write across spot availability and session state. */
    private val mutex = Mutex()

    override val spots: Flow<List<ParkingSpot>> = spotsState.asStateFlow()
    override val activeSession: Flow<ParkingSession?> = activeSessionState.asStateFlow()
    override val sessionHistory: Flow<List<ParkingSession>> = historyState.asStateFlow()

    override fun findCity(query: String): City? {
        val normalized = query.normalizeForMatching()
        if (normalized.isEmpty()) return null
        return cities.firstOrNull { city ->
            city.id == normalized ||
                city.displayName.normalizeForMatching() == normalized ||
                city.aliases.any { it.normalizeForMatching() == normalized }
        } ?: cities.firstOrNull { city ->
            // Tolerate "parking in Warsaw city centre" style input from an agent.
            normalized.contains(city.displayName.normalizeForMatching()) ||
                city.aliases.any { normalized.contains(it.normalizeForMatching()) }
        }
    }

    override fun cityById(cityId: String): City? = cities.firstOrNull { it.id == cityId }

    override fun spotById(spotId: String): ParkingSpot? = spotsState.value.firstOrNull { it.id == spotId }

    override fun searchSpots(
        cityId: String,
        near: GeoPoint?,
        onlyAvailable: Boolean,
        maxResults: Int,
    ): List<ParkingSpot> {
        val origin = near ?: cityById(cityId)?.center ?: return emptyList()
        return spotsState.value
            .asSequence()
            .filter { it.cityId == cityId }
            .filter { !onlyAvailable || !it.isFull }
            .sortedBy { it.location.distanceMetersTo(origin) }
            .take(maxResults.coerceAtLeast(1))
            .toList()
    }

    override suspend fun startSession(
        spotId: String,
        vehiclePlate: String,
        durationMinutes: Int?,
    ): ParkingSession = mutex.withLock {
        val plate = vehiclePlate.trim().uppercase()
        if (plate.length !in PLATE_LENGTH_RANGE) throw ParkingException.InvalidVehiclePlate(vehiclePlate)

        activeSessionState.value?.let { throw ParkingException.SessionAlreadyActive(it.id) }

        val spot = spotById(spotId) ?: throw ParkingException.SpotNotFound(spotId)
        if (spot.isFull) throw ParkingException.SpotFull(spotId)

        val startedAt: Instant = clock.instant()
        val session = ParkingSession(
            id = "ps_" + UUID.randomUUID().toString().take(8),
            spotId = spot.id,
            spotName = spot.name,
            cityId = spot.cityId,
            vehiclePlate = plate,
            startedAt = startedAt,
            expiresAt = durationMinutes
                ?.takeIf { it > 0 }
                ?.let { startedAt.plusSeconds(it * 60L) },
            pricePerHour = spot.pricePerHour,
            currency = spot.currency,
        )

        spotsState.update { current ->
            current.map { if (it.id == spot.id) it.copy(availableSpaces = it.availableSpaces - 1) else it }
        }
        activeSessionState.value = session
        session
    }

    override suspend fun endSession(sessionId: String?): ParkingSession = mutex.withLock {
        val running = activeSessionState.value ?: throw ParkingException.NoActiveSession()
        if (sessionId != null && sessionId != running.id) throw ParkingException.SessionNotFound(sessionId)

        val closed = running.copy(endedAt = clock.instant())

        spotsState.update { current ->
            current.map {
                if (it.id == closed.spotId) {
                    it.copy(availableSpaces = (it.availableSpaces + 1).coerceAtMost(it.totalSpaces))
                } else {
                    it
                }
            }
        }
        activeSessionState.value = null
        historyState.update { listOf(closed) + it }
        closed
    }

    private companion object {
        val PLATE_LENGTH_RANGE = 2..12
    }
}

private val DIACRITICS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

/** Lowercases and strips diacritics so "Kraków", "krakow" and "KRAKOW" all match. */
private fun String.normalizeForMatching(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
