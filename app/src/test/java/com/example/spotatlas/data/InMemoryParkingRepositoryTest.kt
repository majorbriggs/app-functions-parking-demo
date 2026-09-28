package com.example.spotatlas.data

import com.example.spotatlas.domain.ParkingException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryParkingRepositoryTest {

    private val clock = MutableClock(Instant.parse("2026-09-16T10:00:00Z"))
    private val repository = InMemoryParkingRepository(clock = clock)

    // ------------------------------------------------------------------ city resolution

    @Test
    fun `findCity matches the canonical display name`() {
        assertEquals("warsaw", repository.findCity("Warsaw")?.id)
    }

    @Test
    fun `findCity ignores case and diacritics`() {
        assertEquals("krakow", repository.findCity("KRAKOW")?.id)
        assertEquals("krakow", repository.findCity("Kraków")?.id)
        assertEquals("gdansk", repository.findCity("gdańsk")?.id)
    }

    @Test
    fun `findCity accepts local and historical aliases`() {
        assertEquals("warsaw", repository.findCity("Warszawa")?.id)
        assertEquals("gdansk", repository.findCity("Danzig")?.id)
        assertEquals("london", repository.findCity("Londyn")?.id)
    }

    @Test
    fun `findCity tolerates a city name embedded in a phrase`() {
        assertEquals("berlin", repository.findCity("somewhere in Berlin city centre")?.id)
    }

    @Test
    fun `findCity returns null outside the covered cities`() {
        assertNull(repository.findCity("Paris"))
        assertNull(repository.findCity("   "))
    }

    // ------------------------------------------------------------------ search

    @Test
    fun `searchSpots orders by distance from the city centre`() {
        val distances = repository
            .searchSpots(cityId = "warsaw", onlyAvailable = false, maxResults = 10)
            .map { it.location.distanceMetersTo(repository.cityById("warsaw")!!.center) }

        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `searchSpots drops full spots by default`() {
        val ids = repository.searchSpots(cityId = "warsaw", maxResults = 10).map { it.id }

        // Hala Koszyki is seeded with zero free spaces.
        assertTrue("waw-hala-koszyki" !in ids)
        assertTrue(ids.isNotEmpty())
    }

    @Test
    fun `searchSpots includes full spots when asked`() {
        val ids = repository.searchSpots(cityId = "warsaw", onlyAvailable = false, maxResults = 10).map { it.id }

        assertTrue("waw-hala-koszyki" in ids)
    }

    @Test
    fun `searchSpots caps the result size`() {
        assertEquals(2, repository.searchSpots(cityId = "warsaw", maxResults = 2).size)
    }

    @Test
    fun `searchSpots returns nothing for an unknown city`() {
        assertEquals(emptyList<String>(), repository.searchSpots(cityId = "atlantis").map { it.id })
    }

    // ------------------------------------------------------------------ sessions

    @Test
    fun `startSession takes a space and becomes the active session`() = runBlocking<Unit> {
        val before = repository.spotById("waw-zlote-tarasy")!!.availableSpaces

        val session = repository.startSession("waw-zlote-tarasy", "WZ 1234A", durationMinutes = 60)

        assertEquals("waw-zlote-tarasy", session.spotId)
        assertEquals("WZ 1234A", session.vehiclePlate)
        assertTrue(session.isActive)
        assertEquals(before - 1, repository.spotById("waw-zlote-tarasy")!!.availableSpaces)
        assertEquals(session.id, repository.activeSession.first()?.id)
    }

    @Test
    fun `startSession normalises the plate`() = runBlocking<Unit> {
        val session = repository.startSession("waw-zlote-tarasy", "  wz 1234a  ", durationMinutes = null)

        assertEquals("WZ 1234A", session.vehiclePlate)
    }

    @Test
    fun `startSession without a duration leaves the stay open ended`() = runBlocking<Unit> {
        val session = repository.startSession("waw-zlote-tarasy", "WZ 1234A", durationMinutes = null)

        assertNull(session.expiresAt)
    }

    @Test
    fun `startSession rejects a second concurrent stay`() = runBlocking<Unit> {
        repository.startSession("waw-zlote-tarasy", "WZ 1234A", durationMinutes = 30)

        assertThrows(ParkingException.SessionAlreadyActive::class.java) {
            runBlocking { repository.startSession("waw-arkadia", "WZ 1234A", durationMinutes = 30) }
        }
    }

    @Test
    fun `startSession rejects a full spot`() = runBlocking<Unit> {
        assertThrows(ParkingException.SpotFull::class.java) {
            runBlocking { repository.startSession("waw-hala-koszyki", "WZ 1234A", durationMinutes = 30) }
        }
    }

    @Test
    fun `startSession rejects an unknown spot`() = runBlocking<Unit> {
        assertThrows(ParkingException.SpotNotFound::class.java) {
            runBlocking { repository.startSession("nope", "WZ 1234A", durationMinutes = 30) }
        }
    }

    @Test
    fun `startSession rejects an unusable plate`() = runBlocking<Unit> {
        assertThrows(ParkingException.InvalidVehiclePlate::class.java) {
            runBlocking { repository.startSession("waw-zlote-tarasy", "X", durationMinutes = 30) }
        }
    }

    @Test
    fun `endSession gives the space back and records history`() = runBlocking<Unit> {
        val before = repository.spotById("waw-zlote-tarasy")!!.availableSpaces
        val started = repository.startSession("waw-zlote-tarasy", "WZ 1234A", durationMinutes = 60)

        clock.advanceBy(Duration.ofMinutes(30))
        val ended = repository.endSession(started.id)

        assertEquals(started.id, ended.id)
        assertNotNull(ended.endedAt)
        assertTrue(!ended.isActive)
        assertEquals(before, repository.spotById("waw-zlote-tarasy")!!.availableSpaces)
        assertNull(repository.activeSession.first())
        assertEquals(listOf(ended.id), repository.sessionHistory.first().map { it.id })
    }

    @Test
    fun `endSession without an id stops whatever is running`() = runBlocking<Unit> {
        val started = repository.startSession("waw-arkadia", "WZ 1234A", durationMinutes = null)

        assertEquals(started.id, repository.endSession(null).id)
    }

    @Test
    fun `endSession fails when nothing is running`() = runBlocking<Unit> {
        assertThrows(ParkingException.NoActiveSession::class.java) {
            runBlocking { repository.endSession(null) }
        }
    }

    @Test
    fun `endSession fails for a stay that is not the running one`() = runBlocking<Unit> {
        repository.startSession("waw-arkadia", "WZ 1234A", durationMinutes = null)

        assertThrows(ParkingException.SessionNotFound::class.java) {
            runBlocking { repository.endSession("ps_other") }
        }
    }

    // ------------------------------------------------------------------ billing

    @Test
    fun `cost is prorated by the minute at the spot rate`() = runBlocking<Unit> {
        // Złote Tarasy is 12.00 PLN per hour.
        val session = repository.startSession("waw-zlote-tarasy", "WZ 1234A", durationMinutes = null)

        clock.advanceBy(Duration.ofMinutes(30))

        assertEquals(6.0, session.costAt(clock.instant()), 0.001)
    }

    @Test
    fun `cost stops accruing once the prepaid time runs out`() = runBlocking<Unit> {
        val session = repository.startSession("waw-zlote-tarasy", "WZ 1234A", durationMinutes = 60)

        clock.advanceBy(Duration.ofHours(3))

        assertEquals(12.0, session.costAt(clock.instant()), 0.001)
    }
}
