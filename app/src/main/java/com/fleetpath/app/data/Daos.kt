package com.fleetpath.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY id")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: Long): VehicleEntity?

    @Insert
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)
}

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE endTime IS NOT NULL AND startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE endTime IS NOT NULL AND startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    suspend fun between(from: Long, to: Long): List<TripEntity>

    @Query("SELECT * FROM trips WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun allCompleted(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE endTime IS NULL")
    suspend fun openTrips(): List<TripEntity>

    @Query("DELETE FROM trips WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}

@Dao
interface PointDao {
    @Insert
    suspend fun insert(point: PointEntity)

    @Query("SELECT * FROM points WHERE tripId = :tripId ORDER BY time")
    suspend fun forTrip(tripId: Long): List<PointEntity>

    @Query("DELETE FROM points WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: Long)

    @Query("DELETE FROM points WHERE tripId NOT IN (SELECT id FROM trips)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM points")
    suspend fun deleteAll()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY time DESC LIMIT 300")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllRead()

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}

@Dao
interface GeofenceDao {
    @Query("SELECT * FROM geofences ORDER BY name")
    fun observeAll(): Flow<List<GeofenceEntity>>

    @Insert
    suspend fun insert(fence: GeofenceEntity): Long

    @Delete
    suspend fun delete(fence: GeofenceEntity)
}
