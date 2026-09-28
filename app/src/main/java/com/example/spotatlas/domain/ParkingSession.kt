package com.example.spotatlas.domain

import java.time.Duration
import java.time.Instant

/** A paid parking stay, started either from the app UI or by an on-device agent. */
data class ParkingSession(
    val id: String,
    val spotId: String,
    val spotName: String,
    val cityId: String,
    val vehiclePlate: String,
    val startedAt: Instant,
    /** When the prepaid time runs out. Null means open-ended, billed on stop. */
    val expiresAt: Instant?,
    val endedAt: Instant? = null,
    val pricePerHour: Double,
    val currency: String,
) {
    val isActive: Boolean get() = endedAt == null

    /** Billed cost so far, rounded up to whole minutes at the spot's hourly rate. */
    fun costAt(now: Instant): Double {
        val until = endedAt ?: expiresAt?.takeIf { it.isBefore(now) } ?: now
        val minutes = Duration.between(startedAt, until).toMinutes().coerceAtLeast(0)
        return Math.round(pricePerHour * minutes / 60.0 * 100.0) / 100.0
    }
}
