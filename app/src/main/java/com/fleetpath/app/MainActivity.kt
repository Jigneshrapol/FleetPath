package com.fleetpath.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.fleetpath.app.tracking.LocationService
import com.fleetpath.app.tracking.Notifications
import com.fleetpath.app.ui.FleetPathRoot
import com.fleetpath.app.ui.theme.FleetPathTheme
import com.fleetpath.app.util.hasLocationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Trip to open, set when the user taps a trip or alert notification. */
    private val openTrip = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        val app = application as FleetPathApp
        lifecycleScope.launch {
            val s = app.settings.settings.first()
            if (s.signedIn && s.autoDetect && hasLocationPermission() && !app.tracking.serviceRunning.value) {
                LocationService.start(this@MainActivity)
            }
        }

        setContent {
            FleetPathTheme {
                FleetPathRoot(openTrip = openTrip, onTripOpened = { openTrip.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifications.EXTRA_TRIP_ID, -1L) ?: -1L
        if (id > 0) openTrip.value = id
    }
}
