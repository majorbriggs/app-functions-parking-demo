package com.example.spotatlas.domain

/** Where a driver can leave their car. */
data class ParkingSpot(
    val id: String,
    val name: String,
    val cityId: String,
    val address: String,
    val location: GeoPoint,
    val kind: ParkingKind,
    val totalSpaces: Int,
    val availableSpaces: Int,
    val pricePerHour: Double,
    val currency: String,
    val openingHours: String,
    val hasEvCharging: Boolean = false,
    val isAccessible: Boolean = false,
) {
    val isFull: Boolean get() = availableSpaces <= 0
}

enum class ParkingKind {
    /** Marked bays along a public road. */
    STREET,

    /** Open-air surface car park. */
    LOT,

    /** Multi-storey or underground garage. */
    GARAGE,

    /** Park-and-ride next to public transport. */
    PARK_AND_RIDE,
}
