package com.example.spotatlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.spotatlas.deeplink.SpotAtlasDestination
import com.example.spotatlas.di.appContainer
import com.example.spotatlas.ui.map.MapScreen
import com.example.spotatlas.ui.map.MapViewModel
import com.example.spotatlas.ui.navigation.MapRoute
import com.example.spotatlas.ui.navigation.SessionRoute
import com.example.spotatlas.ui.navigation.StartParkingRoute
import com.example.spotatlas.ui.session.SessionScreen
import com.example.spotatlas.ui.session.SessionViewModel
import com.example.spotatlas.ui.session.StartParkingScreen
import com.example.spotatlas.ui.session.StartParkingViewModel

/**
 * Navigation host.
 *
 * [pendingDeepLink] is the destination the activity resolved from an incoming `spotatlas://` intent.
 * Handling it here rather than in the activity keeps the back stack the single place that decides
 * what is on screen, whether the user came from the launcher or from a Gemini answer.
 */
@Composable
fun SpotAtlasApp(
    pendingDeepLink: SpotAtlasDestination?,
    onDeepLinkHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val backStack = rememberNavBackStack(MapRoute())

    LaunchedEffect(pendingDeepLink) {
        val destination = pendingDeepLink ?: return@LaunchedEffect
        backStack.apply {
            clear()
            when (destination) {
                is SpotAtlasDestination.CityOnMap -> add(MapRoute(cityId = destination.cityId))

                is SpotAtlasDestination.SpotOnMap -> add(MapRoute(spotId = destination.spotId))

                // Land on the confirmation form, but keep the map underneath so Back behaves.
                is SpotAtlasDestination.StartParking -> {
                    add(MapRoute(spotId = destination.spotId))
                    add(
                        StartParkingRoute(
                            spotId = destination.spotId,
                            vehiclePlate = destination.vehiclePlate,
                            durationMinutes = destination.durationMinutes,
                        ),
                    )
                }

                is SpotAtlasDestination.ActiveSession -> {
                    add(MapRoute())
                    add(SessionRoute)
                }
            }
        }
        onDeepLinkHandled()
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<MapRoute> { route ->
                MapScreen(
                    // Keyed on the route so a deep link into another city gets a fresh selection.
                    viewModel = viewModel(key = "map:${route.cityId}:${route.spotId}") {
                        MapViewModel(
                            repository = container.parkingRepository,
                            initialCityId = route.cityId,
                            initialSpotId = route.spotId,
                        )
                    },
                    onStartParking = { spotId -> backStack.add(StartParkingRoute(spotId = spotId)) },
                    onOpenActiveSession = { backStack.add(SessionRoute) },
                )
            }

            entry<StartParkingRoute> { route ->
                StartParkingScreen(
                    viewModel = viewModel(key = "start:${route.spotId}") {
                        StartParkingViewModel(
                            repository = container.parkingRepository,
                            spotId = route.spotId,
                            prefillVehiclePlate = route.vehiclePlate,
                            prefillDurationMinutes = route.durationMinutes,
                        )
                    },
                    onBack = { backStack.removeLastOrNull() },
                    onStarted = {
                        backStack.removeLastOrNull()
                        backStack.add(SessionRoute)
                    },
                    onOpenActiveSession = {
                        backStack.removeLastOrNull()
                        backStack.add(SessionRoute)
                    },
                )
            }

            entry<SessionRoute> {
                SessionScreen(
                    viewModel = viewModel(key = "session") {
                        SessionViewModel(
                            repository = container.parkingRepository,
                            clock = container.clock,
                        )
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
