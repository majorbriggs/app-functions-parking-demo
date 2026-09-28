package com.example.spotatlas

import android.app.Application
import com.example.spotatlas.di.AppContainer
import com.example.spotatlas.di.DefaultAppContainer

class SpotAtlasApplication : Application() {

    /** Shared by the Compose UI and by the AppFunctions service the system binds to. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer()
    }
}
