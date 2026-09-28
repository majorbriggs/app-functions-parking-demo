package com.example.spotatlas.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Map of a city, optionally opened with one spot highlighted. */
@Serializable
data class MapRoute(val cityId: String? = null, val spotId: String? = null) : NavKey

/**
 * Confirmation screen for a new stay.
 *
 * Deep links from an agent answer land here with the form already filled in; the driver still has to
 * press the button, so nothing is ever charged without a tap in the app.
 */
@Serializable
data class StartParkingRoute(
    val spotId: String,
    val vehiclePlate: String? = null,
    val durationMinutes: Int? = null,
) : NavKey

/** The running (or most recently finished) stay. */
@Serializable
data object SessionRoute : NavKey
