package com.fleetpath.app.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fleetpath.app.FleetPathApp
import com.fleetpath.app.util.hasLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps GPS running while a trip records, or while
 * auto-detect is on. Android requires the visible notification for this.
 */
class LocationService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var lastNotify = 0L

    private val app get() = application as FleetPathApp

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { app.tracking.onLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasLocationPermission()) {
            // startForegroundService() must still be answered before stopping.
            try { goForeground() } catch (e: Exception) { }
            shutdown()
            return START_NOT_STICKY
        }
        // Must call startForeground promptly after startForegroundService().
        try {
            goForeground()
        } catch (e: Exception) {
            // Android blocks location services started from the background without "all the time" access.
            shutdown()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_STOP_TRIP -> scope.launch {
                app.tracking.stopTrip()
                if (!app.tracking.settingsSnapshot().autoDetect) shutdown()
            }
        }
        if (!started) {
            started = true
            app.tracking.serviceRunning.value = true
            observe()
        }
        return START_STICKY
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, Notifications.tracking(this, app.tracking.trip.value), type)
    }

    private fun observe() {
        scope.launch {
            combine(app.tracking.trip, app.settings.settings) { trip, s ->
                val idle = trip == null && s.batterySaver
                s.intervalSec * 1000L * (if (idle) 3 else 1)
            }.distinctUntilChanged().collect { interval -> requestUpdates(interval) }
        }
        scope.launch {
            app.tracking.trip.collect { trip ->
                val now = SystemClock.elapsedRealtime()
                if (trip == null || now - lastNotify > 5_000) {
                    lastNotify = now
                    updateNotification(trip)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(trip: ActiveTrip?) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, Notifications.tracking(this, trip))
        } catch (e: SecurityException) {
            // Notifications not allowed; the foreground notification still exists.
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalMs: Long) {
        if (!hasLocationPermission()) {
            shutdown()
            return
        }
        client.removeLocationUpdates(callback)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun shutdown() {
        if (::client.isInitialized) client.removeLocationUpdates(callback)
        app.tracking.serviceRunning.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (::client.isInitialized) client.removeLocationUpdates(callback)
        app.tracking.serviceRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.fleetpath.app.action.STOP"
        const val ACTION_STOP_TRIP = "com.fleetpath.app.action.STOP_TRIP"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
