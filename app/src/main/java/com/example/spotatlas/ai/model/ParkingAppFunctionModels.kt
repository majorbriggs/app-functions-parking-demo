package com.example.spotatlas.ai.model

import android.net.Uri
import androidx.appfunctions.AppFunctionSerializable
import java.time.Instant

/*
 * The wire format between SpotAtlas and the on-device agent.
 *
 * These types are deliberately separate from the domain model in `com.example.spotatlas.domain`: the
 * agent-facing contract is documentation for a language model, so field names, units and KDoc are
 * chosen for an LLM reader and must stay stable even if the domain model is refactored.
 *
 * KSP only picks up documentation written inline on each property, so every field carries its own
 * KDoc and none of these classes use class-level @param or @property tags.
 */

/** A geographic point in WGS-84 degrees. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GeoLocation(
    /** Latitude in degrees, between -90 and 90. */
    val latitude: Double,
    /** Longitude in degrees, between -180 and 180. */
    val longitude: Double,
)

/** Criteria for a parking search. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FindParkingParams(
    /**
     * City to search in, for example "Warsaw", "Kraków", "Gdańsk", "Berlin" or "London". Local
     * spellings such as "Warszawa" and accent-free spellings such as "Krakow" are accepted.
     */
    val city: String,
    /** Point to sort results around. When null, results are sorted from the city centre. */
    val near: GeoLocation? = null,
    /** Maximum number of spots to return. Null means 5; values above 10 are capped. */
    val maxResults: Int? = null,
    /** Omits spots with no free spaces. Null means true; set false to include full spots. */
    val onlyAvailable: Boolean? = null,
)

/** A place where a car can be left. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ParkingSpotSummary(
    /** Identifier to pass to startParkingSession. */
    val spotId: String,
    /** Name of the car park, for example "Złote Tarasy Garage". */
    val name: String,
    /** Street address including district. */
    val address: String,
    /** One of STREET, LOT, GARAGE or PARK_AND_RIDE. */
    val kind: String,
    /** Location of the entrance. */
    val location: GeoLocation,
    /** Straight-line distance in metres from the search centre. */
    val distanceMeters: Int,
    /** Free spaces right now. Zero means the car park is full. */
    val availableSpaces: Int,
    /** Total capacity, for context on how busy the car park is. */
    val totalSpaces: Int,
    /** Price for one hour, in the currency given by currencyCode. Zero means free. */
    val pricePerHour: Double,
    /** ISO 4217 currency code, for example "PLN", "EUR" or "GBP". */
    val currencyCode: String,
    /** When the car park is open, for example "24/7" or "Mon–Fri 08:00–20:00". */
    val openingHours: String,
    /** True when electric vehicle charging is available on site. */
    val hasEvCharging: Boolean,
    /** True when the car park has accessible bays. */
    val isAccessible: Boolean,
    /**
     * Link that opens SpotAtlas on a pre-filled confirmation screen for this spot. Offer it to the user
     * when they would rather start the stay themselves than have it started for them.
     */
    val startParkingLink: Uri,
)

/** Result of a parking search. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FindParkingResult(
    /** City the results belong to, spelled as SpotAtlas knows it. */
    val city: String,
    /** Country the city is in. */
    val country: String,
    /** Matching spots, nearest first. Empty when every spot in the city is full. */
    val spots: List<ParkingSpotSummary>,
    /** Link that opens the SpotAtlas map centred on this city. */
    val openMapLink: Uri,
)

/** Details for starting a parking stay. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class StartParkingParams(
    /** Identifier of the spot, taken from a ParkingSpotSummary returned by findParkingSpots. */
    val spotId: String,
    /** Licence plate of the car. Null uses the driver's saved default vehicle. */
    val vehiclePlate: String? = null,
    /** Prepaid minutes. Null starts an open-ended stay that is billed when it is stopped. */
    val durationMinutes: Int? = null,
)

/** Identifies the stay to stop. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class EndParkingParams(
    /** Identifier of the stay to stop. Null stops whichever stay is currently running. */
    val sessionId: String? = null,
)

/** A parking stay, running or finished. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ParkingSessionSummary(
    /** Identifier to pass to endParkingSession. */
    val sessionId: String,
    /** Identifier of the spot the car is parked at. */
    val spotId: String,
    /** Name of the car park the car is parked at. */
    val spotName: String,
    /** Licence plate the stay was started for. */
    val vehiclePlate: String,
    /** When the stay started. */
    val startedAt: Instant,
    /** When prepaid time runs out. Null for an open-ended stay. */
    val expiresAt: Instant?,
    /** When the stay was stopped. Null while it is still running. */
    val endedAt: Instant?,
    /** Amount billed so far, in the currency given by currencyCode. */
    val cost: Double,
    /** ISO 4217 currency code, for example "PLN". */
    val currencyCode: String,
    /** True while the stay is running. */
    val isActive: Boolean,
    /** Link that opens this stay in SpotAtlas. */
    val openInAppLink: Uri,
)
