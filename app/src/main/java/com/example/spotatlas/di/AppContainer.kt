package com.example.spotatlas.di

import android.content.Context
import com.example.spotatlas.SpotAtlasApplication
import com.example.spotatlas.data.InMemoryParkingRepository
import com.example.spotatlas.domain.ParkingRepository
import java.time.Clock

/**
 * Manual dependency container.
 *
 * The demo has a single repository shared between the UI and the AppFunctions service, so a
 * hand-rolled container is enough and keeps the build free of a second annotation processor next to
 * the AppFunctions KSP compiler. Swapping in Hilt later means replacing this file with a `@Module`
 * and annotating the service with `@AndroidEntryPoint`; nothing else changes.
 */
interface AppContainer {
    val clock: Clock
    val parkingRepository: ParkingRepository
}

class DefaultAppContainer(override val clock: Clock = Clock.systemDefaultZone()) : AppContainer {
    override val parkingRepository: ParkingRepository by lazy { InMemoryParkingRepository(clock) }
}

/** Reaches the container from anywhere holding a [Context], including services. */
val Context.appContainer: AppContainer
    get() = (applicationContext as SpotAtlasApplication).container
