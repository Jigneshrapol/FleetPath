package com.fleetpath.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val userName: String = "",
    val phone: String = "",
    val signedIn: Boolean = false,
    val onboarded: Boolean = false,
    val activeVehicleId: Long = -1L,
    val autoDetect: Boolean = false,
    val intervalSec: Int = 5,
    val batterySaver: Boolean = true,
    val speedLimitKmh: Int = 80,
    val notifications: Boolean = true,
    val historyMonths: Int = 12,
    val mapStyle: String = "standard",
)

class SettingsStore(private val context: Context) {

    private object K {
        val NAME = stringPreferencesKey("name")
        val PHONE = stringPreferencesKey("phone")
        val SIGNED_IN = booleanPreferencesKey("signed_in")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val VEHICLE = longPreferencesKey("active_vehicle")
        val AUTO = booleanPreferencesKey("auto_detect")
        val INTERVAL = intPreferencesKey("interval_sec")
        val SAVER = booleanPreferencesKey("battery_saver")
        val LIMIT = intPreferencesKey("speed_limit")
        val NOTIFY = booleanPreferencesKey("notifications")
        val HISTORY = intPreferencesKey("history_months")
        val MAP = stringPreferencesKey("map_style")
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            userName = p[K.NAME] ?: d.userName,
            phone = p[K.PHONE] ?: d.phone,
            signedIn = p[K.SIGNED_IN] ?: d.signedIn,
            onboarded = p[K.ONBOARDED] ?: d.onboarded,
            activeVehicleId = p[K.VEHICLE] ?: d.activeVehicleId,
            autoDetect = p[K.AUTO] ?: d.autoDetect,
            intervalSec = p[K.INTERVAL] ?: d.intervalSec,
            batterySaver = p[K.SAVER] ?: d.batterySaver,
            speedLimitKmh = p[K.LIMIT] ?: d.speedLimitKmh,
            notifications = p[K.NOTIFY] ?: d.notifications,
            historyMonths = p[K.HISTORY] ?: d.historyMonths,
            mapStyle = p[K.MAP] ?: d.mapStyle,
        )
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(read(p))
            p[K.NAME] = n.userName
            p[K.PHONE] = n.phone
            p[K.SIGNED_IN] = n.signedIn
            p[K.ONBOARDED] = n.onboarded
            p[K.VEHICLE] = n.activeVehicleId
            p[K.AUTO] = n.autoDetect
            p[K.INTERVAL] = n.intervalSec
            p[K.SAVER] = n.batterySaver
            p[K.LIMIT] = n.speedLimitKmh
            p[K.NOTIFY] = n.notifications
            p[K.HISTORY] = n.historyMonths
            p[K.MAP] = n.mapStyle
        }
    }
}
