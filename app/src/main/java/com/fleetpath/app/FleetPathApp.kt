package com.fleetpath.app

import android.app.Application
import android.content.Context
import com.fleetpath.app.data.AppDatabase
import com.fleetpath.app.data.SettingsStore
import com.fleetpath.app.routing.Place
import com.fleetpath.app.tracking.Notifications
import com.fleetpath.app.tracking.TrackingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class FleetPathApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var tracking: TrackingManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Destination handed from another screen (e.g. trip detail) to Plan route. */
    val pendingPlanDestination = MutableStateFlow<Place?>(null)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = SettingsStore(this)
        tracking = TrackingManager(this, db, settings)
        Notifications.createChannels(this)

        val osm = Configuration.getInstance()
        osm.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        osm.userAgentValue = packageName
        osm.osmdroidBasePath = File(cacheDir, "osmdroid")
        osm.osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

        appScope.launch {
            tracking.recoverOpenTrips()
            val months = settings.settings.first().historyMonths
            val cutoff = System.currentTimeMillis() - months * 30L * 24 * 3_600_000L
            db.trips().deleteOlderThan(cutoff)
            db.points().deleteOrphans()
        }
    }
}
