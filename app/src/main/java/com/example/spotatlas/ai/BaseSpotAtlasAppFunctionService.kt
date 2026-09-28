package com.example.spotatlas.ai

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionElementAlreadyExistsException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionLimitExceededException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.example.spotatlas.ai.model.EndParkingParams
import com.example.spotatlas.ai.model.FindParkingParams
import com.example.spotatlas.ai.model.FindParkingResult
import com.example.spotatlas.ai.model.ParkingSessionSummary
import com.example.spotatlas.ai.model.StartParkingParams
import com.example.spotatlas.deeplink.SpotAtlasDeepLink
import com.example.spotatlas.di.appContainer
import com.example.spotatlas.domain.ParkingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SpotAtlas's tools for the on-device intelligence system.
 *
 * KSP reads the annotations below and generates the concrete `SpotAtlasAppFunctionService` declared in
 * the manifest, plus `assets/spotatlas_app_function_service.xml` describing the schema. The KDoc on each
 * function is what the agent reads when it decides whether and how to call it, so it is written for a
 * language model rather than for a developer.
 *
 * Every function delegates to the same [com.example.spotatlas.domain.ParkingRepository] the UI uses, so
 * a stay started by Gemini shows up on the map immediately.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "SpotAtlasAppFunctionService",
    appFunctionXmlFileName = "spotatlas_app_function_service",
)
abstract class BaseSpotAtlasAppFunctionService : AppFunctionService() {

    private val repository by lazy { appContainer.parkingRepository }
    private val clock by lazy { appContainer.clock }

    /**
     * Search for parking spots in a city, nearest first.
     *
     * Returns the number of free spaces, the hourly price and, for each spot, a link that opens
     * SpotAtlas on a pre-filled confirmation screen.
     *
     * Required workflow: call this before "startParkingSession" to obtain a valid spotId.
     *
     * @param params City to search, an optional point to sort results around, and result limits.
     * @return The resolved city and its nearest matching spots.
     * @throws AppFunctionInvalidArgumentException If the city is blank. Ask the user which city they
     *   mean before retrying.
     * @throws AppFunctionElementNotFoundException If SpotAtlas has no coverage there. The message lists
     *   the covered cities; offer those to the user instead of retrying.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun findParkingSpots(params: FindParkingParams): FindParkingResult =
        withContext(Dispatchers.IO) {
            if (params.city.isBlank()) {
                throw AppFunctionInvalidArgumentException("A city name is required to search for parking")
            }

            val city = repository.findCity(params.city) ?: throw AppFunctionElementNotFoundException(
                "SpotAtlas has no parking coverage for \"${params.city}\". Covered cities: " +
                    repository.cities.joinToString { it.displayName },
            )

            val origin = params.near?.toGeoPoint() ?: city.center
            val defaultPlate = repository.knownVehiclePlates.firstOrNull()

            FindParkingResult(
                city = city.displayName,
                country = city.country,
                spots = repository
                    .searchSpots(
                        cityId = city.id,
                        near = origin,
                        onlyAvailable = params.onlyAvailable ?: true,
                        maxResults = (params.maxResults ?: DEFAULT_SPOTS_PER_RESPONSE)
                            .coerceIn(1, MAX_SPOTS_PER_RESPONSE),
                    )
                    .map { it.toSummary(origin = origin, vehiclePlate = defaultPlate) },
                openMapLink = SpotAtlasDeepLink.city(city.id),
            )
        }

    /**
     * Start a paid parking stay and begin billing the driver.
     *
     * Required workflow: call "findParkingSpots" first to obtain a valid spotId, then confirm the
     * spot, the vehicle and the duration with the user. This function spends the user's money, so it
     * runs only after the user has agreed to the specific spot and price.
     *
     * Only one stay runs at a time. Starting a stay takes a space away from the spot, which the app's
     * map reflects straight away.
     *
     * @param params Spot to park at, licence plate, and prepaid minutes.
     * @return The started stay, including a link that opens it in SpotAtlas.
     * @throws AppFunctionElementNotFoundException If the spotId is unknown. Run "findParkingSpots"
     *   again rather than guessing another id.
     * @throws AppFunctionElementAlreadyExistsException If a stay is already running. Offer to stop it
     *   with "endParkingSession" first; the message carries the running session id.
     * @throws AppFunctionLimitExceededException If the spot filled up in the meantime. Suggest the
     *   next nearest spot from the previous "findParkingSpots" result.
     * @throws AppFunctionInvalidArgumentException If the licence plate is unusable. Ask the user for
     *   the plate.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun startParkingSession(params: StartParkingParams): ParkingSessionSummary =
        withContext(Dispatchers.IO) {
            val plate = params.vehiclePlate?.takeIf { it.isNotBlank() }
                ?: repository.knownVehiclePlates.firstOrNull()
                ?: throw AppFunctionInvalidArgumentException(
                    "No saved vehicle; ask the user for the licence plate of the car",
                )

            try {
                repository
                    .startSession(
                        spotId = params.spotId,
                        vehiclePlate = plate,
                        durationMinutes = params.durationMinutes?.takeIf { it > 0 },
                    )
                    .toSummary(clock.instant())
            } catch (failure: ParkingException) {
                throw failure.toAppFunctionException()
            }
        }

    /**
     * Stop a running parking stay and report the final cost.
     *
     * @param params Identifier of the stay to stop, or null to stop whichever stay is running.
     * @return The stopped stay with its final cost.
     * @throws AppFunctionElementNotFoundException If nothing is running, or the id does not match the
     *   running stay. Tell the user they are not currently parked.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun endParkingSession(params: EndParkingParams): ParkingSessionSummary =
        withContext(Dispatchers.IO) {
            try {
                repository
                    .endSession(params.sessionId?.takeIf { it.isNotBlank() })
                    .toSummary(clock.instant())
            } catch (failure: ParkingException) {
                throw failure.toAppFunctionException()
            }
        }

    private companion object {
        const val DEFAULT_SPOTS_PER_RESPONSE = 5

        /** Agents pay per token for every field, so a search never returns more than this. */
        const val MAX_SPOTS_PER_RESPONSE = 10
    }
}

/** Maps domain failures onto the exception types the intelligence system understands. */
private fun ParkingException.toAppFunctionException(): Exception = when (this) {
    is ParkingException.SpotNotFound,
    is ParkingException.SessionNotFound,
    is ParkingException.NoActiveSession,
    is ParkingException.UnknownCity -> AppFunctionElementNotFoundException(message.orEmpty())

    is ParkingException.SessionAlreadyActive -> AppFunctionElementAlreadyExistsException(message.orEmpty())

    is ParkingException.SpotFull -> AppFunctionLimitExceededException(message.orEmpty())

    is ParkingException.InvalidVehiclePlate -> AppFunctionInvalidArgumentException(message.orEmpty())
}
