package com.fleetpath.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VehicleEntity::class, TripEntity::class, PointEntity::class, AlertEntity::class, GeofenceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicles(): VehicleDao
    abstract fun trips(): TripDao
    abstract fun points(): PointDao
    abstract fun alerts(): AlertDao
    abstract fun geofences(): GeofenceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fleetpath.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
