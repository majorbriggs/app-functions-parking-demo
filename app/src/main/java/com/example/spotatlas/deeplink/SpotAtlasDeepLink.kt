package com.example.spotatlas.deeplink

import android.net.Uri
import androidx.core.net.toUri

/**
 * The URLs that open SpotAtlas from outside the app.
 *
 * AppFunction results hand these to the on-device agent, so a Gemini answer can end with a link that
 * drops the driver straight onto the "start parking" screen instead of the launcher.
 *
 * Two equivalent forms are accepted:
 *  - `spotatlas://park/waw-zlote-tarasy?plate=WZ%201234A&duration=90` — custom scheme, always resolves
 *    to this app.
 *  - `https://spotatlas.example.com/park/waw-zlote-tarasy?...` — Android App Link form, which needs a
 *    verified `assetlinks.json` on the host before it opens without a chooser.
 */
object SpotAtlasDeepLink {

    const val SCHEME = "spotatlas"
    const val WEB_HOST = "spotatlas.example.com"

    private const val PATH_CITY = "city"
    private const val PATH_SPOT = "spot"
    private const val PATH_PARK = "park"
    private const val PATH_SESSION = "session"
    private const val QUERY_PLATE = "plate"
    private const val QUERY_DURATION = "duration"

    /** Opens the map centred on [cityId]. */
    fun city(cityId: String): Uri = base(PATH_CITY).appendPath(cityId).build()

    /** Opens the map centred on [spotId]. */
    fun spot(spotId: String): Uri = base(PATH_SPOT).appendPath(spotId).build()

    /**
     * Opens the confirmation screen for starting a stay at [spotId], pre-filling the form.
     *
     * The app always asks the driver to confirm: an agent link never starts a paid session on its own.
     */
    fun startParking(spotId: String, vehiclePlate: String? = null, durationMinutes: Int? = null): Uri =
        base(PATH_PARK)
            .appendPath(spotId)
            .apply {
                vehiclePlate?.let { appendQueryParameter(QUERY_PLATE, it) }
                durationMinutes?.let { appendQueryParameter(QUERY_DURATION, it.toString()) }
            }
            .build()

    /** Opens the running session screen. */
    fun session(sessionId: String): Uri = base(PATH_SESSION).appendPath(sessionId).build()

    /** Maps an incoming intent [uri] to a destination, or null when it is not one of ours. */
    fun resolve(uri: Uri?): SpotAtlasDestination? {
        if (uri == null) return null
        val isOurs = uri.scheme == SCHEME || (uri.scheme in setOf("http", "https") && uri.host == WEB_HOST)
        if (!isOurs) return null

        // spotatlas://park/<id> puts "park" in the authority, https://host/park/<id> puts it in the path.
        val segments = buildList {
            if (uri.scheme == SCHEME) uri.authority?.let(::add)
            addAll(uri.pathSegments)
        }.filter { it.isNotBlank() }

        val action = segments.firstOrNull() ?: return null
        val id = segments.getOrNull(1)

        return when (action) {
            PATH_CITY -> id?.let(SpotAtlasDestination::CityOnMap)
            PATH_SPOT -> id?.let(SpotAtlasDestination::SpotOnMap)
            PATH_PARK -> id?.let {
                SpotAtlasDestination.StartParking(
                    spotId = it,
                    vehiclePlate = uri.getQueryParameter(QUERY_PLATE)?.takeIf(String::isNotBlank),
                    durationMinutes = uri.getQueryParameter(QUERY_DURATION)?.toIntOrNull(),
                )
            }
            PATH_SESSION -> SpotAtlasDestination.ActiveSession(id)
            else -> null
        }
    }

    private fun base(action: String): Uri.Builder =
        "$SCHEME://$action".toUri().buildUpon()
}

/** Where a resolved deep link should land the driver. */
sealed interface SpotAtlasDestination {

    /** Show the map centred on a city. */
    data class CityOnMap(val cityId: String) : SpotAtlasDestination

    /** Show the map with one spot highlighted. */
    data class SpotOnMap(val spotId: String) : SpotAtlasDestination

    /** Show the pre-filled confirmation screen for a new stay. */
    data class StartParking(
        val spotId: String,
        val vehiclePlate: String?,
        val durationMinutes: Int?,
    ) : SpotAtlasDestination

    /** Show the running stay; [sessionId] is informational because only one stay runs at a time. */
    data class ActiveSession(val sessionId: String?) : SpotAtlasDestination
}
