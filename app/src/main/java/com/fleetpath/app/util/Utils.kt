package com.fleetpath.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

fun distanceM(a: LatLon, b: LatLon): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

/** Down-samples a path to at most [maxPoints] points, stored as "lat,lon;lat,lon". */
fun encodePreview(points: List<LatLon>, maxPoints: Int = 40): String {
    if (points.isEmpty()) return ""
    val step = (points.size / maxPoints).coerceAtLeast(1)
    val picked = points.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
    if (picked.last() != points.last()) picked.add(points.last())
    return picked.joinToString(";") { String.format(Locale.US, "%.5f,%.5f", it.lat, it.lon) }
}

fun decodePreview(s: String?): List<LatLon> {
    if (s.isNullOrBlank()) return emptyList()
    return s.split(";").mapNotNull { part ->
        val p = part.split(",")
        val lat = p.getOrNull(0)?.toDoubleOrNull()
        val lon = p.getOrNull(1)?.toDoubleOrNull()
        if (lat != null && lon != null) LatLon(lat, lon) else null
    }
}

// ---------- Formatting ----------

fun fmtKm(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.US, "%.1f km", m / 1000)

fun fmtKmNumber(m: Double): String = String.format(Locale.US, "%.1f", m / 1000)

fun fmtDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "$h h $m m"
        totalMin > 0 -> "$m min"
        else -> "${(ms / 1000).coerceAtLeast(0)} s"
    }
}

fun fmtTimer(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun fmtClock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun fmtDate(ms: Long, pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))

fun fmtCoord(lat: Double, lon: Double): String {
    val ns = if (lat >= 0) "N" else "S"
    val ew = if (lon >= 0) "E" else "W"
    return String.format(Locale.US, "%.5f° %s, %.5f° %s", abs(lat), ns, abs(lon), ew)
}

fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun addDays(ms: Long, days: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

fun hourOf(ms: Long): Int {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    return c.get(Calendar.HOUR_OF_DAY)
}

/** "TODAY · SUN 4 OCT", "YESTERDAY · SAT 3 OCT" or "THU 1 OCT". */
fun fmtDayHeader(ms: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    val day = startOfDay(ms)
    val base = fmtDate(ms, "EEE d MMM").uppercase(Locale.getDefault())
    return when (day) {
        today -> "TODAY · $base"
        addDays(today, -1) -> "YESTERDAY · $base"
        else -> base
    }
}

// ---------- Permissions ----------

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun Context.hasBackgroundLocation(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

// ---------- Reverse geocoding ----------

object AddressLookup {
    suspend fun reverse(context: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
            val a = results?.firstOrNull() ?: return@withContext null
            val parts = listOfNotNull(a.thoroughfare ?: a.featureName, a.subLocality, a.locality)
                .filter { it.isNotBlank() }
                .distinct()
            if (parts.isNotEmpty()) parts.joinToString(", ") else a.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }
}
