package com.example.spotatlas.deeplink

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trips the links SpotAtlas hands to an on-device agent.
 *
 * Runs on a device because [android.net.Uri] is a platform class; a broken link here means a Gemini
 * answer that cannot open the app, which is exactly the failure this demo must not have.
 */
@RunWith(AndroidJUnit4::class)
class SpotAtlasDeepLinkTest {

    @Test
    fun cityLink_roundTrips() {
        val uri = SpotAtlasDeepLink.city("warsaw")

        assertEquals("spotatlas://city/warsaw", uri.toString())
        assertEquals(SpotAtlasDestination.CityOnMap("warsaw"), SpotAtlasDeepLink.resolve(uri))
    }

    @Test
    fun spotLink_roundTrips() {
        val uri = SpotAtlasDeepLink.spot("waw-zlote-tarasy")

        assertEquals("spotatlas://spot/waw-zlote-tarasy", uri.toString())
        assertEquals(SpotAtlasDestination.SpotOnMap("waw-zlote-tarasy"), SpotAtlasDeepLink.resolve(uri))
    }

    @Test
    fun startParkingLink_carriesPlateAndDuration() {
        val uri = SpotAtlasDeepLink.startParking(
            spotId = "waw-zlote-tarasy",
            vehiclePlate = "WZ 1234A",
            durationMinutes = 90,
        )

        assertEquals(
            SpotAtlasDestination.StartParking("waw-zlote-tarasy", "WZ 1234A", 90),
            SpotAtlasDeepLink.resolve(uri),
        )
    }

    @Test
    fun startParkingLink_escapesSpacesInThePlate() {
        val uri = SpotAtlasDeepLink.startParking(spotId = "krk-wawel", vehiclePlate = "KR 99XYZ")

        assertEquals("spotatlas://park/krk-wawel?plate=KR%2099XYZ", uri.toString())
    }

    @Test
    fun startParkingLink_withoutOptionalsResolvesToNulls() {
        val resolved = SpotAtlasDeepLink.resolve(SpotAtlasDeepLink.startParking("ber-tempelhof"))

        assertEquals(SpotAtlasDestination.StartParking("ber-tempelhof", null, null), resolved)
    }

    @Test
    fun sessionLink_roundTrips() {
        val uri = SpotAtlasDeepLink.session("ps_12345678")

        assertEquals(SpotAtlasDestination.ActiveSession("ps_12345678"), SpotAtlasDeepLink.resolve(uri))
    }

    @Test
    fun httpsForm_resolvesToTheSameDestination() {
        val resolved = SpotAtlasDeepLink.resolve(
            "https://spotatlas.example.com/park/lon-soho?plate=WZ%201234A&duration=45".toUri(),
        )

        assertEquals(SpotAtlasDestination.StartParking("lon-soho", "WZ 1234A", 45), resolved)
    }

    @Test
    fun foreignLinks_areIgnored() {
        assertNull(SpotAtlasDeepLink.resolve("https://example.com/park/waw-zlote-tarasy".toUri()))
        assertNull(SpotAtlasDeepLink.resolve("otherapp://park/waw-zlote-tarasy".toUri()))
        assertNull(SpotAtlasDeepLink.resolve(null))
    }

    @Test
    fun unknownActions_andMissingIds_areIgnored() {
        assertNull(SpotAtlasDeepLink.resolve("spotatlas://teleport/waw-zlote-tarasy".toUri()))
        assertNull(SpotAtlasDeepLink.resolve("spotatlas://park".toUri()))
    }

    @Test
    fun malformedDuration_fallsBackToNull() {
        val resolved = SpotAtlasDeepLink.resolve("spotatlas://park/gda-forum?duration=soon".toUri())

        assertEquals(SpotAtlasDestination.StartParking("gda-forum", null, null), resolved)
    }
}
