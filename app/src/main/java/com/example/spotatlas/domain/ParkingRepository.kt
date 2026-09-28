package com.example.spotatlas.domain

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for parking data and sessions.
 *
 * Both the Compose UI and the AppFunctions service talk to this interface, so an agent-triggered
 * action and a tap inside the app go through exactly the same code path.
 */
interface ParkingRepository {

    /** Cities SpotAtlas has coverage for. */
    val cities: List<City>

    /** Live view of every spot, including availability that changes as sessions start and stop. */
    val spots: Flow<List<ParkingSpot>>

    /** The session currently running, or null when the driver is not parked. */
    val activeSession: Flow<ParkingSession?>

    /** Sessions that have already been stopped, most recent first. */
    val sessionHistory: Flow<List<ParkingSession>>

    /** Plates the driver has used before; the first is treated as the default vehicle. */
    val knownVehiclePlates: List<String>

    /** Resolves free-form input such as "warszawa" or "Krakow" to a known city, or null. */
    fun findCity(query: String): City?

    fun cityById(cityId: String): City?

    fun spotById(spotId: String): ParkingSpot?

    /**
     * Spots in [cityId] ordered by distance from [near], falling back to the city centre.
     *
     * @param onlyAvailable drops spots with no free spaces.
     * @param maxResults caps the result size; pass a small number for agent responses.
     */
    fun searchSpots(
        cityId: String,
        near: GeoPoint? = null,
        onlyAvailable: Boolean = true,
        maxResults: Int = 5,
    ): List<ParkingSpot>

    /**
     * Starts a stay at [spotId] for [vehiclePlate].
     *
     * @param durationMinutes prepaid minutes, or null for an open-ended stay billed on stop.
     * @throws ParkingException.SessionAlreadyActive when a stay is already running.
     */
    suspend fun startSession(spotId: String, vehiclePlate: String, durationMinutes: Int?): ParkingSession

    /**
     * Stops [sessionId], or the active session when [sessionId] is null.
     *
     * @throws ParkingException.NoActiveSession when nothing is running.
     */
    suspend fun endSession(sessionId: String?): ParkingSession
}
