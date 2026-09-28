package com.example.spotatlas.domain

/**
 * Failures the parking domain can produce.
 *
 * Kept as a sealed hierarchy so callers translate them exhaustively: the UI turns them into
 * snackbars, the AppFunctions layer into `AppFunctionException` subclasses an agent understands.
 */
sealed class ParkingException(message: String) : Exception(message) {

    class UnknownCity(val query: String) : ParkingException("No parking coverage for \"$query\"")

    class SpotNotFound(val spotId: String) : ParkingException("No parking spot with id \"$spotId\"")

    class SpotFull(val spotId: String) : ParkingException("Parking spot \"$spotId\" has no free spaces")

    class SessionAlreadyActive(val sessionId: String) :
        ParkingException("A parking session ($sessionId) is already running; stop it first")

    class NoActiveSession : ParkingException("No parking session is currently running")

    class SessionNotFound(val sessionId: String) : ParkingException("No parking session with id \"$sessionId\"")

    class InvalidVehiclePlate(val plate: String) : ParkingException("\"$plate\" is not a usable licence plate")
}
