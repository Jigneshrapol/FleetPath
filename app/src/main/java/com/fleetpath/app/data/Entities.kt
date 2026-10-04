package com.fleetpath.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plate: String,
    val model: String,
    val driver: String = "",
    val kmPerLitre: Float? = null,
)

@Entity(tableName = "trips", indices = [Index("startTime")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val distanceM: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val movingMs: Long = 0,
    val idleMs: Long = 0,
    val overspeedCount: Int = 0,
    val harshBrakeCount: Int = 0,
    val score: Int = 100,
    val autoDetected: Boolean = false,
    /** Down-sampled "lat,lon;lat,lon" path used for list thumbnails. */
    val previewPath: String? = null,
)

fun TripEntity.durationMs(): Long = (endTime ?: System.currentTimeMillis()) - startTime
fun TripEntity.startName(): String = startAddress ?: "Start"
fun TripEntity.endName(): String = endAddress ?: "End"

@Entity(tableName = "points", indices = [Index("tripId")])
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Float,
    val bearing: Float,
    val accuracy: Float,
)

@Entity(tableName = "alerts", indices = [Index("time")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: String,
    val title: String,
    val message: String,
    val tripId: Long? = null,
    val isRead: Boolean = false,
)

object AlertType {
    const val SPEED = "SPEED"
    const val GEOFENCE = "GEOFENCE"
    const val TRIP = "TRIP"
}

@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Float,
)
