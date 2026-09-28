package com.example.spotatlas.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotatlas.domain.ParkingKind
import com.example.spotatlas.domain.ParkingSession
import com.example.spotatlas.domain.ParkingSpot
import com.example.spotatlas.ui.formatDistance
import com.example.spotatlas.ui.formatPricePerHour
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

private const val CITY_ZOOM = 12f
private const val SPOT_ZOOM = 15f

/**
 * Map of one city's parking, with a card carousel along the bottom.
 *
 * This is the surface a deep link from a Gemini answer lands on when the user only wants to look
 * around; [onStartParking] takes them to the confirmation screen.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onStartParking: (spotId: String) -> Unit,
    onOpenActiveSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.selectedCity.center.toLatLng(), CITY_ZOOM)
    }
    val carouselState = rememberLazyListState()

    // Follow the selection: a tapped marker, a tapped card, or a spot named by a deep link.
    LaunchedEffect(state.selectedCity.id, state.selectedSpotId) {
        val spot = state.selectedSpot
        val target = spot?.location?.toLatLng() ?: state.selectedCity.center.toLatLng()
        val zoom = if (spot != null) SPOT_ZOOM else CITY_ZOOM
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, zoom))

        val index = state.spots.indexOfFirst { it.id == state.selectedSpotId }
        if (index >= 0) carouselState.animateScrollToItem(index)
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            contentPadding = PaddingValues(bottom = 220.dp, top = 96.dp),
        ) {
            state.spots.forEach { spot ->
                Marker(
                    state = rememberUpdatedMarkerState(spot.location.toLatLng()),
                    title = spot.name,
                    snippet = spot.availabilityLabel + " · " + formatPricePerHour(spot.pricePerHour, spot.currency),
                    icon = BitmapDescriptorFactory.defaultMarker(spot.markerHue()),
                    onClick = {
                        viewModel.selectSpot(spot.id)
                        false // keep the default info window
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CitySelector(
                cities = state.cities,
                selectedCityId = state.selectedCity.id,
                onCitySelected = viewModel::selectCity,
            )
            state.activeSession?.let { ActiveSessionBanner(session = it, onClick = onOpenActiveSession) }
        }

        LazyRow(
            state = carouselState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.spots, key = { _, spot -> spot.id }) { _, spot ->
                SpotCard(
                    spot = spot,
                    distanceMeters = spot.location.distanceMetersTo(state.selectedCity.center).toInt(),
                    isSelected = spot.id == state.selectedSpotId,
                    onSelect = { viewModel.selectSpot(spot.id) },
                    onStartParking = { onStartParking(spot.id) },
                )
            }
        }
    }
}

@Composable
private fun CitySelector(
    cities: List<com.example.spotatlas.domain.City>,
    selectedCityId: String,
    onCitySelected: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cities.size, key = { cities[it].id }) { index ->
            val city = cities[index]
            FilterChip(
                selected = city.id == selectedCityId,
                onClick = { onCitySelected(city.id) },
                label = { Text(city.displayName) },
            )
        }
    }
}

@Composable
private fun ActiveSessionBanner(session: ParkingSession, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Parked at ${session.spotName}", fontWeight = FontWeight.SemiBold)
            Text(
                text = "${session.vehiclePlate} · tap to view or stop",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SpotCard(
    spot: ParkingSpot,
    distanceMeters: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onStartParking: () -> Unit,
) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.width(280.dp),
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(spot.address, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(8.dp)
                        .width(8.dp)
                        .background(spot.availabilityColor(), shape = MaterialTheme.shapes.small),
                )
                Text(spot.availabilityLabel, style = MaterialTheme.typography.bodyMedium)
                Text("·", style = MaterialTheme.typography.bodyMedium)
                Text(formatPricePerHour(spot.pricePerHour, spot.currency), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "${spot.kind.label()} · ${formatDistance(distanceMeters)} from centre · ${spot.openingHours}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onStartParking,
                enabled = !spot.isFull,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (spot.isFull) "Full" else "Park here")
            }
        }
    }
}

private val ParkingSpot.availabilityLabel: String
    get() = if (isFull) "Full" else "$availableSpaces of $totalSpaces free"

private fun ParkingSpot.availabilityColor(): Color = when {
    isFull -> Color(0xFFC62828)
    availableSpaces < 10 -> Color(0xFFEF6C00)
    else -> Color(0xFF2E7D32)
}

private fun ParkingSpot.markerHue(): Float = when {
    isFull -> BitmapDescriptorFactory.HUE_RED
    availableSpaces < 10 -> BitmapDescriptorFactory.HUE_ORANGE
    else -> BitmapDescriptorFactory.HUE_GREEN
}

private fun ParkingKind.label(): String = when (this) {
    ParkingKind.STREET -> "Street"
    ParkingKind.LOT -> "Car park"
    ParkingKind.GARAGE -> "Garage"
    ParkingKind.PARK_AND_RIDE -> "Park & ride"
}

private fun com.example.spotatlas.domain.GeoPoint.toLatLng() = LatLng(latitude, longitude)
