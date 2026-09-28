package com.example.spotatlas.ai

import com.example.spotatlas.ai.model.GeoLocation
import com.example.spotatlas.ai.model.ParkingSessionSummary
import com.example.spotatlas.ai.model.ParkingSpotSummary
import com.example.spotatlas.deeplink.SpotAtlasDeepLink
import com.example.spotatlas.domain.GeoPoint
import com.example.spotatlas.domain.ParkingSession
import com.example.spotatlas.domain.ParkingSpot
import java.time.Instant
import kotlin.math.roundToInt

/** Translates domain types into the agent-facing contract, attaching the deep links agents hand back to the user. */

internal fun GeoLocation.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

internal fun GeoPoint.toGeoLocation() = GeoLocation(latitude = latitude, longitude = longitude)

internal fun ParkingSpot.toSummary(origin: GeoPoint, vehiclePlate: String?) = ParkingSpotSummary(
    spotId = id,
    name = name,
    address = address,
    kind = kind.name,
    location = location.toGeoLocation(),
    distanceMeters = location.distanceMetersTo(origin).roundToInt(),
    availableSpaces = availableSpaces,
    totalSpaces = totalSpaces,
    pricePerHour = pricePerHour,
    currencyCode = currency,
    openingHours = openingHours,
    hasEvCharging = hasEvCharging,
    isAccessible = isAccessible,
    startParkingLink = SpotAtlasDeepLink.startParking(spotId = id, vehiclePlate = vehiclePlate),
)

internal fun ParkingSession.toSummary(now: Instant) = ParkingSessionSummary(
    sessionId = id,
    spotId = spotId,
    spotName = spotName,
    vehiclePlate = vehiclePlate,
    startedAt = startedAt,
    expiresAt = expiresAt,
    endedAt = endedAt,
    cost = costAt(now),
    currencyCode = currency,
    isActive = isActive,
    openInAppLink = SpotAtlasDeepLink.session(id),
)
