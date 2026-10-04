package com.fleetpath.app.tracking

import android.content.Context
import android.location.Location
import com.fleetpath.app.analysis.Analyzer
import com.fleetpath.app.data.AlertEntity
import com.fleetpath.app.data.AlertType
import com.fleetpath.app.data.AppDatabase
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.data.GeofenceEntity
import com.fleetpath.app.data.PointEntity
import com.fleetpath.app.data.SettingsStore
import com.fleetpath.app.data.TripEntity
import com.fleetpath.app.util.AddressLookup
import com.fleetpath.app.util.LatLon
import com.fleetpath.app.util.distanceM
import com.fleetpath.app.util.encodePreview
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.fmtKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.roundToInt

data class LiveFix(
    val lat: Double,
    val lon: Double,
    val speedKmh: Float,
    val bearing: Float,
    val accuracy: Float,
    val time: Long,
) {
    val latLon get() = LatLon(lat, lon)
}

data class ActiveTrip(
    val tripId: Long,
    val startTime: Long,
    val auto: Boolean,
    val distanceM: Double = 0.0,
    val maxKmh: Float = 0f,
    val movingMs: Long = 0,
    val idleMs: Long = 0,
    val path: List<LatLon> = emptyList(),
    val planned: List<LatLon>? = null,
    val destination: LatLon? = null,
    val destinationName: String? = null,
)

/**
 * Single source of truth for live position and the trip being recorded.
 * Location fixes arrive from [LocationService] (background) or from the
 * screens while the app is open; every fix goes through [onLocation].
 */
class TrackingManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _live = MutableStateFlow<LiveFix?>(null)
    val live: StateFlow<LiveFix?> = _live.asStateFlow()

    private val _trip = MutableStateFlow<ActiveTrip?>(null)
    val trip: StateFlow<ActiveTrip?> = _trip.asStateFlow()

    val serviceRunning = MutableStateFlow(false)

    @Volatile private var settings = AppSettings()
    @Volatile private var fences: List<GeofenceEntity> = emptyList()

    private var lastRecorded: LiveFix? = null
    private var lastFixTime = 0L
    private var fastCount = 0
    private var stillSince = 0L
    private var overspeedSince = 0L
    private var overspeedAlerted = false
    private val insideFence = mutableMapOf<Long, Boolean>()

    init {
        scope.launch { settingsStore.settings.collect { settings = it } }
        scope.launch { db.geofences().observeAll().collect { fences = it } }
    }

    fun settingsSnapshot(): AppSettings = settings

    fun onLocation(location: Location) {
        scope.launch { mutex.withLock { handle(location) } }
    }

    fun startTrip(planned: List<LatLon>? = null, destination: LatLon? = null, destinationName: String? = null) {
        scope.launch { mutex.withLock { startTripInternal(false, planned, destination, destinationName) } }
    }

    /** Stops and saves the current trip. Returns the saved trip id, or null if it was discarded. */
    suspend fun stopTrip(): Long? = mutex.withLock {
        val t = _trip.value ?: return@withLock null
        _trip.value = null
        finalizeTrip(t.tripId, t.auto)
    }

    /** Closes trips left open if the app was killed while recording. */
    suspend fun recoverOpenTrips() = mutex.withLock {
        val activeId = _trip.value?.tripId
        db.trips().openTrips().filter { it.id != activeId }.forEach { finalizeTrip(it.id, it.autoDetected) }
    }

    // ------------------------------------------------------------------

    private suspend fun handle(loc: Location) {
        val prev = _live.value
        val speed = when {
            loc.hasSpeed() -> loc.speed * 3.6f
            prev != null && loc.time > prev.time -> {
                val d = distanceM(prev.lat, prev.lon, loc.latitude, loc.longitude)
                (d / ((loc.time - prev.time) / 1000.0) * 3.6).toFloat()
            }
            else -> 0f
        }
        val fix = LiveFix(
            lat = loc.latitude,
            lon = loc.longitude,
            speedKmh = speed,
            bearing = if (loc.hasBearing()) loc.bearing else prev?.bearing ?: 0f,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else 0f,
            time = loc.time,
        )
        // Ignore stale or duplicate fixes coming from two listeners.
        if (prev != null && fix.time <= prev.time) return
        _live.value = fix

        val s = settings
        val good = fix.accuracy <= 50f
        if (good) checkGeofences(fix, s)

        if (_trip.value == null && s.autoDetect && serviceRunning.value && good) {
            fastCount = if (fix.speedKmh > 10f) fastCount + 1 else 0
            if (fastCount >= 3) {
                fastCount = 0
                startTripInternal(true, null, null, null)
            }
        }

        val t = _trip.value ?: return
        if (!good) return
        record(t, fix)
        checkOverspeed(fix, s)
        if (t.auto) {
            if (fix.speedKmh < Analyzer.IDLE_KMH) {
                if (stillSince == 0L) stillSince = fix.time
                else if (fix.time - stillSince > AUTO_STOP_MS) {
                    _trip.value = null
                    finalizeTrip(t.tripId, true)
                }
            } else {
                stillSince = 0L
            }
        }
    }

    private suspend fun record(t: ActiveTrip, fix: LiveFix) {
        val lastT = lastFixTime
        lastFixTime = fix.time
        val dt = if (lastT > 0) (fix.time - lastT).coerceIn(0L, 120_000L) else 0L
        val moving = fix.speedKmh >= Analyzer.IDLE_KMH
        var updated = t.copy(
            movingMs = t.movingMs + if (moving) dt else 0L,
            idleMs = t.idleMs + if (moving) 0L else dt,
            maxKmh = max(t.maxKmh, fix.speedKmh),
        )
        val last = lastRecorded
        val d = if (last != null) distanceM(last.lat, last.lon, fix.lat, fix.lon) else 0.0
        val jitter = max(20.0, fix.accuracy.toDouble())
        val store = last == null || (moving && d >= 5) || d >= jitter || fix.time - last.time >= 60_000
        if (store) {
            db.points().insert(
                PointEntity(
                    tripId = t.tripId, time = fix.time, lat = fix.lat, lon = fix.lon,
                    speedKmh = fix.speedKmh, bearing = fix.bearing, accuracy = fix.accuracy,
                )
            )
            if (last != null && (moving || d >= jitter)) updated = updated.copy(distanceM = updated.distanceM + d)
            updated = updated.copy(path = updated.path + fix.latLon)
            if (last == null) {
                db.trips().get(t.tripId)?.let { db.trips().update(it.copy(startLat = fix.lat, startLon = fix.lon)) }
            }
            lastRecorded = fix
        }
        _trip.value = updated
    }

    private suspend fun startTripInternal(auto: Boolean, planned: List<LatLon>?, destination: LatLon?, destinationName: String?) {
        if (_trip.value != null) return
        val now = System.currentTimeMillis()
        val vehicleId = settings.activeVehicleId.takeIf { it > 0 }
        val id = db.trips().insert(TripEntity(vehicleId = vehicleId, startTime = now, autoDetected = auto))
        lastRecorded = null
        lastFixTime = 0L
        stillSince = 0L
        overspeedSince = 0L
        overspeedAlerted = false
        val t = ActiveTrip(id, now, auto, planned = planned, destination = destination, destinationName = destinationName)
        _trip.value = t
        val fix = _live.value
        if (fix != null && now - fix.time < 30_000 && fix.accuracy <= 50f) record(t, fix)
        if (auto) addAlert(AlertType.TRIP, "Trip started automatically", "Recording began when the vehicle started moving.", id, settings.notifications)
    }

    private suspend fun finalizeTrip(tripId: Long, auto: Boolean): Long? {
        val trip = db.trips().get(tripId) ?: return null
        val points = db.points().forTrip(tripId)
        val analysis = Analyzer.analyze(points, settings.speedLimitKmh)
        if (points.size < 2 || analysis.distanceM < 100) {
            db.points().deleteForTrip(tripId)
            db.trips().delete(tripId)
            if (!auto) addAlert(AlertType.TRIP, "Trip not saved", "It was shorter than 100 m.", null, false)
            return null
        }
        val first = points.first()
        val last = points.last()
        val startAddr = AddressLookup.reverse(context, first.lat, first.lon)
        val endAddr = AddressLookup.reverse(context, last.lat, last.lon)
        val saved = trip.copy(
            endTime = last.time,
            startLat = first.lat, startLon = first.lon,
            endLat = last.lat, endLon = last.lon,
            startAddress = startAddr, endAddress = endAddr,
            distanceM = analysis.distanceM,
            maxSpeedKmh = analysis.maxKmh,
            avgSpeedKmh = analysis.avgKmh,
            movingMs = analysis.movingMs,
            idleMs = analysis.idleMs,
            overspeedCount = analysis.overspeeds.size,
            harshBrakeCount = analysis.harshBrakes,
            score = analysis.score,
            previewPath = encodePreview(points.map { LatLon(it.lat, it.lon) }),
        )
        db.trips().update(saved)
        addAlert(
            AlertType.TRIP,
            "Trip saved · ${fmtKm(analysis.distanceM)}",
            "${startAddr ?: "Start"} → ${endAddr ?: "End"} · ${fmtDuration(last.time - trip.startTime)} · score ${analysis.score}",
            tripId,
            settings.notifications,
        )
        return tripId
    }

    private suspend fun checkOverspeed(fix: LiveFix, s: AppSettings) {
        if (fix.speedKmh > s.speedLimitKmh) {
            if (overspeedSince == 0L) overspeedSince = fix.time
            if (!overspeedAlerted && fix.time - overspeedSince >= 10_000) {
                overspeedAlerted = true
                addAlert(
                    AlertType.SPEED,
                    "Over-speed · ${fix.speedKmh.roundToInt()} km/h",
                    "Above your ${s.speedLimitKmh} km/h limit for more than 10 seconds.",
                    _trip.value?.tripId,
                    s.notifications,
                )
            }
        } else if (fix.speedKmh < s.speedLimitKmh - 5) {
            overspeedSince = 0L
            overspeedAlerted = false
        }
    }

    private suspend fun checkGeofences(fix: LiveFix, s: AppSettings) {
        for (f in fences) {
            val inside = distanceM(f.lat, f.lon, fix.lat, fix.lon) <= f.radiusM
            val before = insideFence[f.id]
            insideFence[f.id] = inside
            if (before != null && before != inside) {
                addAlert(
                    AlertType.GEOFENCE,
                    if (inside) "Entered ${f.name}" else "Left ${f.name}",
                    if (inside) "The vehicle arrived inside the ${f.name} zone." else "The vehicle exited the ${f.name} zone.",
                    _trip.value?.tripId,
                    s.notifications,
                )
            }
        }
    }

    private suspend fun addAlert(type: String, title: String, message: String, tripId: Long?, notify: Boolean) {
        val id = db.alerts().insert(AlertEntity(time = System.currentTimeMillis(), type = type, title = title, message = message, tripId = tripId))
        if (notify) Notifications.showAlert(context, id.toInt(), title, message, tripId)
    }

    companion object {
        /** An auto-detected trip ends after the vehicle has been still this long. */
        const val AUTO_STOP_MS = 5 * 60_000L
    }
}
