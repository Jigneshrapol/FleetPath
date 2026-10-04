package com.fleetpath.app.analysis

import com.fleetpath.app.data.TripEntity
import com.fleetpath.app.util.LatLon
import com.fleetpath.app.util.addDays
import com.fleetpath.app.util.distanceM
import com.fleetpath.app.util.fmtDate
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.hourOf
import com.fleetpath.app.util.startOfDay
import java.util.Calendar
import java.util.Locale

data class VisitedPlace(val name: String, val lat: Double, val lon: Double, val visits: Int, val avgStayMs: Long?)

/** Groups trip end points within 300 m of each other into places, most visited first. */
fun frequentPlaces(trips: List<TripEntity>, limit: Int = 4): List<VisitedPlace> {
    class Cluster(var lat: Double, var lon: Double) {
        var count = 0
        val names = mutableMapOf<String, Int>()
        val stays = mutableListOf<Long>()
    }
    val sorted = trips.filter { it.endLat != null && it.endLon != null && it.endTime != null }.sortedBy { it.startTime }
    val clusters = mutableListOf<Cluster>()
    sorted.forEachIndexed { i, t ->
        val lat = t.endLat!!
        val lon = t.endLon!!
        val c = clusters.firstOrNull { distanceM(it.lat, it.lon, lat, lon) < 300 } ?: Cluster(lat, lon).also { clusters += it }
        c.count++
        c.lat += (lat - c.lat) / c.count
        c.lon += (lon - c.lon) / c.count
        t.endAddress?.let { c.names[it] = (c.names[it] ?: 0) + 1 }
        val next = sorted.getOrNull(i + 1)
        if (next?.startLat != null && next.startLon != null) {
            val stay = next.startTime - t.endTime!!
            if (stay in 0..(24 * 3_600_000L) && distanceM(next.startLat, next.startLon, lat, lon) < 300) c.stays += stay
        }
    }
    return clusters.sortedByDescending { it.count }.take(limit).map { c ->
        val name = c.names.maxByOrNull { it.value }?.key ?: String.format(Locale.US, "%.4f, %.4f", c.lat, c.lon)
        VisitedPlace(name, c.lat, c.lon, c.count, if (c.stays.isEmpty()) null else c.stays.average().toLong())
    }
}

data class Tip(val title: String, val detail: String)

/** Uses past trips that ended near [dest] to suggest when to leave. */
fun departureTip(trips: List<TripEntity>, dest: LatLon): Tip? {
    val matches = trips.filter {
        it.endLat != null && it.endLon != null && it.endTime != null &&
            distanceM(it.endLat, it.endLon, dest.lat, dest.lon) < 1000
    }
    if (matches.isEmpty()) return null
    val durations = matches.map { it.endTime!! - it.startTime }
    val avgDur = durations.average().toLong()
    val commonHour = matches.groupingBy { hourOf(it.startTime) }.eachCount().maxByOrNull { it.value }?.key ?: 9
    if (matches.size >= 3) {
        val speedByHour = matches.groupBy { hourOf(it.startTime) }.mapValues { (_, l) ->
            l.sumOf { it.distanceM } / l.sumOf { (it.endTime!! - it.startTime).coerceAtLeast(1L) }.toDouble()
        }
        val overall = matches.sumOf { it.distanceM } / durations.sum().coerceAtLeast(1L).toDouble()
        val best = speedByHour.entries.maxByOrNull { it.value }
        if (best != null && speedByHour.size >= 2 && best.value > 0) {
            val saving = (avgDur * (1 - overall / best.value)).toLong()
            if (saving >= 3 * 60_000L) {
                return Tip(
                    String.format(Locale.US, "Leave around %02d:00 to save about %s", best.key, fmtDuration(saving)),
                    "Across your ${matches.size} past trips here, the ones starting around that hour were the quickest.",
                )
            }
        }
    }
    return Tip(
        "Your trips here usually take ${fmtDuration(avgDur)}",
        String.format(Locale.US, "Based on %d past trip%s. You most often leave around %02d:00.", matches.size, if (matches.size == 1) "" else "s", commonHour),
    )
}

enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class Bar(val label: String, val km: Double, val highlight: Boolean)

data class PeriodRange(val from: Long, val to: Long, val prevFrom: Long)

fun periodRange(period: Period, now: Long = System.currentTimeMillis()): PeriodRange {
    val end = addDays(startOfDay(now), 1)
    return when (period) {
        Period.WEEK -> PeriodRange(addDays(end, -7), end, addDays(end, -14))
        Period.MONTH -> PeriodRange(addDays(end, -30), end, addDays(end, -60))
        Period.YEAR -> {
            val c = Calendar.getInstance()
            c.timeInMillis = startOfDay(now)
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, -11)
            val from = c.timeInMillis
            c.add(Calendar.MONTH, -12)
            PeriodRange(from, end, c.timeInMillis)
        }
    }
}

fun bars(period: Period, range: PeriodRange, trips: List<TripEntity>): List<Bar> {
    return when (period) {
        Period.WEEK, Period.MONTH -> {
            val days = if (period == Period.WEEK) 7 else 30
            (0 until days).map { i ->
                val dayStart = addDays(range.from, i)
                val dayEnd = addDays(dayStart, 1)
                val km = trips.filter { it.startTime in dayStart until dayEnd }.sumOf { it.distanceM } / 1000
                val label = when {
                    period == Period.WEEK -> fmtDate(dayStart, "EEE")
                    i % 7 == 0 || i == days - 1 -> fmtDate(dayStart, "d MMM")
                    else -> ""
                }
                Bar(label, km, i == days - 1)
            }
        }
        Period.YEAR -> {
            val c = Calendar.getInstance()
            c.timeInMillis = range.from
            (0 until 12).map { i ->
                val mStart = c.timeInMillis
                c.add(Calendar.MONTH, 1)
                val mEnd = c.timeInMillis
                val km = trips.filter { it.startTime in mStart until mEnd }.sumOf { it.distanceM } / 1000
                Bar(fmtDate(mStart, "MMM").take(1), km, i == 11)
            }
        }
    }
}

/** Distance-weighted average of trip scores. */
fun weightedScore(trips: List<TripEntity>): Int? {
    val total = trips.sumOf { it.distanceM }
    if (trips.isEmpty() || total <= 0) return null
    return (trips.sumOf { it.score * it.distanceM } / total).toInt()
}
