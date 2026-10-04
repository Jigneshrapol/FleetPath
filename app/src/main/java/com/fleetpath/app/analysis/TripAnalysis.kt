package com.fleetpath.app.analysis

import com.fleetpath.app.data.PointEntity
import com.fleetpath.app.util.distanceM
import kotlin.math.max
import kotlin.math.roundToInt

data class OverspeedSegment(val startIdx: Int, val endIdx: Int, val maxKmh: Float, val startTime: Long, val durationMs: Long)
data class StopSegment(val startIdx: Int, val endIdx: Int, val startTime: Long, val durationMs: Long, val lat: Double, val lon: Double)

data class TripAnalysis(
    val distanceM: Double,
    val movingMs: Long,
    val idleMs: Long,
    val maxKmh: Float,
    val avgKmh: Float,
    val overspeeds: List<OverspeedSegment>,
    val stops: List<StopSegment>,
    val harshBrakes: Int,
    val score: Int,
)

object Analyzer {
    const val IDLE_KMH = 3f
    private const val MIN_STOP_MS = 3 * 60_000L
    private const val HARSH_DECEL = 3.5f // m/s²
    private const val MAX_GAP_MS = 10 * 60_000L

    fun analyze(points: List<PointEntity>, limitKmh: Int): TripAnalysis {
        if (points.size < 2) {
            return TripAnalysis(0.0, 0, 0, points.firstOrNull()?.speedKmh ?: 0f, 0f, emptyList(), emptyList(), 0, 100)
        }
        var dist = 0.0
        var moving = 0L
        var idle = 0L
        var maxKmh = points[0].speedKmh
        var harsh = 0
        var lastHarshTime = 0L

        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dt = b.time - a.time
            maxKmh = max(maxKmh, b.speedKmh)
            if (dt <= 0 || dt > MAX_GAP_MS) continue
            val d = distanceM(a.lat, a.lon, b.lat, b.lon)
            if (b.speedKmh >= IDLE_KMH || d >= 20) dist += d
            if (b.speedKmh < IDLE_KMH) idle += dt else moving += dt
            if (dt <= 6_000 && a.speedKmh > 20f) {
                val decel = (a.speedKmh - b.speedKmh) / 3.6f / (dt / 1000f)
                if (decel > HARSH_DECEL && b.time - lastHarshTime > 15_000) {
                    harsh++
                    lastHarshTime = b.time
                }
            }
        }

        val overs = mutableListOf<OverspeedSegment>()
        var osStart = -1
        var osMax = 0f
        fun closeOver(endIdx: Int) {
            val dur = points[endIdx].time - points[osStart].time
            if (dur >= 5_000 || endIdx > osStart) {
                overs += OverspeedSegment(osStart, endIdx, osMax, points[osStart].time, dur)
            }
        }
        for (i in points.indices) {
            val p = points[i]
            if (p.speedKmh > limitKmh) {
                if (osStart < 0) { osStart = i; osMax = 0f }
                osMax = max(osMax, p.speedKmh)
            } else if (osStart >= 0) {
                closeOver(i - 1)
                osStart = -1
            }
        }
        if (osStart >= 0) closeOver(points.lastIndex)

        val stops = mutableListOf<StopSegment>()
        var stopStart = -1
        for (i in points.indices) {
            val slow = points[i].speedKmh < IDLE_KMH
            if (slow && stopStart < 0) stopStart = i
            if ((!slow || i == points.lastIndex) && stopStart >= 0) {
                val endIdx = if (slow) i else i - 1
                val dur = points[endIdx].time - points[stopStart].time
                // A stop that runs into the end of the trip is just the trip ending.
                if (dur >= MIN_STOP_MS && stopStart > 0 && endIdx < points.lastIndex) {
                    val p = points[stopStart]
                    stops += StopSegment(stopStart, endIdx, p.time, dur, p.lat, p.lon)
                }
                stopStart = -1
            }
        }

        val avg = if (moving > 0) (dist / 1000.0 / (moving / 3_600_000.0)).toFloat() else 0f
        val idleRatio = if (moving + idle > 0) idle.toDouble() / (moving + idle) else 0.0
        val score = (100 - overs.size * 5 - harsh * 3 - (idleRatio * 20).roundToInt()).coerceIn(0, 100)
        return TripAnalysis(dist, moving, idle, maxKmh, avg, overs, stops, harsh, score)
    }
}
