package com.example.spotatlas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.spotatlas.deeplink.SpotAtlasDeepLink
import com.example.spotatlas.deeplink.SpotAtlasDestination
import com.example.spotatlas.ui.theme.SpotAtlasTheme

/**
 * Single activity.
 *
 * Declared `singleTask` so repeated `spotatlas://` links from an agent conversation reuse this instance
 * and arrive through [onNewIntent] instead of stacking up new copies of the app.
 */
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<SpotAtlasDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingDeepLink = SpotAtlasDeepLink.resolve(intent?.data)

        setContent {
            SpotAtlasTheme {
                SpotAtlasApp(
                    pendingDeepLink = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = SpotAtlasDeepLink.resolve(intent.data)
    }
}
