package com.example.spotatlas.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotatlas.ui.formatMinutes
import com.example.spotatlas.ui.formatMoney
import com.example.spotatlas.ui.formatPricePerHour
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/** Prepaid options offered on the form; null is an open-ended stay. */
private val DURATION_OPTIONS: List<Int?> = listOf(30, 60, 120, 180, null)

/**
 * Confirmation step before any money is spent.
 *
 * Reached either by tapping a spot on the map or by following a `spotatlas://park/...` link from an
 * agent answer, in which case the plate and duration arrive already filled in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartParkingScreen(
    viewModel: StartParkingViewModel,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onOpenActiveSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.startedSessionId) {
        if (state.startedSessionId != null) onStarted()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Start parking") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        val spot = state.spot
        if (spot == null) {
            Text(
                text = "This parking spot is no longer available.",
                modifier = Modifier.padding(innerPadding).padding(16.dp),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(spot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(spot.address, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatPricePerHour(spot.pricePerHour, spot.currency) +
                            " · " + (if (spot.isFull) "Full" else "${spot.availableSpaces} free") +
                            " · " + spot.openingHours,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.blockingSessionSpotName?.let { spotName ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You are already parked at $spotName.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Only one parking session can run at a time.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onOpenActiveSession) { Text("Open running session") }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vehicle", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.vehiclePlate,
                    onValueChange = viewModel::setVehiclePlate,
                    label = { Text("Licence plate") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.knownPlates.forEach { plate ->
                        SuggestionChip(
                            onClick = { viewModel.setVehiclePlate(plate) },
                            label = { Text(plate) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duration", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = state.durationMinutes == minutes,
                            onClick = { viewModel.setDurationMinutes(minutes) },
                            label = { Text(minutes?.let(::formatMinutes) ?: "Open-ended") },
                        )
                    }
                }
                Text(
                    text = state.durationMinutes
                        ?.let { "Estimated cost ${formatMoney(spot.pricePerHour * it / 60.0, spot.currency)}" }
                        ?: "Billed when you stop, at ${formatPricePerHour(spot.pricePerHour, spot.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::startParking,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isStarting) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                }
                Text(if (spot.isFull) "No spaces left" else "Start parking")
            }
        }
    }
}
