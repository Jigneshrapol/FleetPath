@file:OptIn(ExperimentalMaterial3Api::class)

package com.fleetpath.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.R
import com.fleetpath.app.analysis.Analyzer
import com.fleetpath.app.analysis.TripAnalysis
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.data.PointEntity
import com.fleetpath.app.data.TripEntity
import com.fleetpath.app.data.VehicleEntity
import com.fleetpath.app.data.endName
import com.fleetpath.app.data.startName
import com.fleetpath.app.export.Exporter
import com.fleetpath.app.routing.Place
import com.fleetpath.app.ui.components.EmptyState
import com.fleetpath.app.ui.components.MapLine
import com.fleetpath.app.ui.components.MapMarker
import com.fleetpath.app.ui.components.OsmMap
import com.fleetpath.app.ui.components.PillChip
import com.fleetpath.app.ui.components.PrimaryButton
import com.fleetpath.app.ui.components.ScreenHeader
import com.fleetpath.app.ui.components.SecondaryButton
import com.fleetpath.app.ui.components.SpeedChart
import com.fleetpath.app.ui.components.SquareIconButton
import com.fleetpath.app.ui.components.StatTile
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.AddressLookup
import com.fleetpath.app.util.LatLon
import com.fleetpath.app.util.fmtClock
import com.fleetpath.app.util.fmtCoord
import com.fleetpath.app.util.fmtDate
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.fmtKmNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TripDetailScreen(tripId: Long, onBack: () -> Unit, onPlanAgain: () -> Unit) {
    val app = rememberApp()
    var trip by remember { mutableStateOf<TripEntity?>(null) }
    var points by remember { mutableStateOf<List<PointEntity>?>(null) }
    LaunchedEffect(tripId) {
        trip = app.db.trips().get(tripId)
        points = app.db.points().forTrip(tripId)
    }
    val t = trip
    val pts = points
    when {
        pts == null -> Column(Modifier.fillMaxSize()) {
            ScreenHeader("Trip", onBack = onBack)
            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FP.Blue) }
        }
        t == null -> Column(Modifier.fillMaxSize()) {
            ScreenHeader("Trip", onBack = onBack)
            EmptyState(Icons.Outlined.SearchOff, "Trip not found", "It may have been deleted or removed by your history settings.")
        }
        else -> TripDetailContent(t, pts, onBack, onPlanAgain)
    }
}

private data class TimelineEvent(val time: Long, val title: String, val detail: String, val color: Color)

@Composable
private fun TripDetailContent(trip: TripEntity, points: List<PointEntity>, onBack: () -> Unit, onPlanAgain: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val limit = settings.speedLimitKmh
    val analysis: TripAnalysis = remember(points, limit) { Analyzer.analyze(points, limit) }
    val vehicle by produceState<VehicleEntity?>(null, trip.vehicleId) {
        value = trip.vehicleId?.let { app.db.vehicles().get(it) }
    }
    val stopNames by produceState(emptyMap<Int, String>(), analysis) {
        value = analysis.stops.mapIndexedNotNull { i, s -> AddressLookup.reverse(context, s.lat, s.lon)?.let { i to it } }.toMap()
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val startT = points.firstOrNull()?.time ?: trip.startTime
    val endT = (points.lastOrNull()?.time ?: trip.endTime ?: trip.startTime).coerceAtLeast(startT + 1)
    var replayTime by remember { mutableLongStateOf(startT) }
    var playing by remember { mutableStateOf(false) }
    var multiplier by remember { mutableIntStateOf(1) }
    LaunchedEffect(playing, multiplier) {
        while (playing) {
            delay(50)
            // 1× replays one minute of driving per second.
            val next = replayTime + 50L * 60L * multiplier
            if (next >= endT) {
                replayTime = endT
                playing = false
            } else {
                replayTime = next
            }
        }
    }
    val replayIdx = remember(replayTime, points) { indexAt(points, replayTime) }
    val replayPoint = points.getOrNull(replayIdx)
    val replayStarted = replayTime > startT

    val path = remember(points) { points.map { LatLon(it.lat, it.lon) } }
    val lines = remember(path, analysis) {
        buildList {
            add(MapLine(path, FP.Blue))
            analysis.overspeeds.forEach { seg ->
                val end = (seg.endIdx + 1).coerceAtMost(path.lastIndex)
                val sub = path.subList(seg.startIdx, (end + 1).coerceAtMost(path.size))
                add(MapLine(sub, FP.Orange))
            }
        }
    }
    val markers = buildList {
        analysis.stops.forEach { add(MapMarker(LatLon(it.lat, it.lon), R.drawable.ic_marker_stop)) }
        path.firstOrNull()?.let { add(MapMarker(it, R.drawable.ic_marker_start)) }
        path.lastOrNull()?.let { add(MapMarker(it, R.drawable.ic_marker_end, anchorBottom = true)) }
        if (replayStarted && replayPoint != null) {
            add(MapMarker(LatLon(replayPoint.lat, replayPoint.lon), R.drawable.ic_marker_vehicle, replayPoint.bearing))
        }
    }

    val events = remember(analysis, stopNames, trip) {
        buildList {
            add(TimelineEvent(trip.startTime, "Trip started", trip.startName(), FP.Ink))
            analysis.stops.forEachIndexed { i, s ->
                add(TimelineEvent(s.startTime, "Stopped ${fmtDuration(s.durationMs)}", stopNames[i] ?: fmtCoord(s.lat, s.lon), FP.Subtle))
            }
            analysis.overspeeds.forEach { o ->
                add(TimelineEvent(o.startTime, "Over-speed · ${o.maxKmh.roundToInt()} km/h", "Limit $limit km/h, for ${fmtDuration(o.durationMs)}", FP.Orange))
            }
            add(TimelineEvent(trip.endTime ?: endT, "Trip ended", trip.endName(), FP.Blue))
        }.sortedBy { it.time }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "${trip.startName()} → ${trip.endName()}",
            subtitle = "${fmtDate(trip.startTime, "EEE d MMM")} · ${fmtClock(trip.startTime)} – ${fmtClock(trip.endTime ?: endT)}",
            onBack = onBack,
        ) {
            SquareIconButton(Icons.Outlined.Delete, "Delete trip", { confirmDelete = true })
        }

        Box(Modifier.fillMaxWidth().height(300.dp)) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lines = lines,
                markers = markers,
                fitTo = path,
                fitKey = trip.id,
                style = settings.mapStyle,
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legend(FP.Blue, "Route")
                Legend(FP.Orange, "Over-speed")
            }
            if (replayStarted && replayPoint != null) {
                Text(
                    "${replayPoint.speedKmh.roundToInt()} km/h · ${fmtClock(replayPoint.time)}",
                    fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(FP.Ink).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                "© OpenStreetMap contributors",
                fontSize = 10.sp, color = FP.Muted,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
            )
        }

        // Replay controls
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = {
                        if (!playing && replayTime >= endT) replayTime = startT
                        playing = !playing
                    },
                    shape = CircleShape,
                    color = FP.Blue,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause replay" else "Play replay",
                            tint = Color.White,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = ((replayTime - startT).toFloat() / (endT - startT)).coerceIn(0f, 1f),
                        onValueChange = {
                            playing = false
                            replayTime = startT + (it * (endT - startT)).toLong()
                        },
                        colors = SliderDefaults.colors(thumbColor = FP.Blue, activeTrackColor = FP.Blue, inactiveTrackColor = FP.Line),
                    )
                    Row {
                        Text(fmtClock(replayTime), fontFamily = Mono, fontSize = 12.sp, color = FP.Muted)
                        Spacer(Modifier.weight(1f))
                        Text(fmtClock(endT), fontFamily = Mono, fontSize = 12.sp, color = FP.Muted)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 4, 8).forEach { m -> PillChip("$m×", multiplier == m, { multiplier = m }) }
                Spacer(Modifier.weight(1f))
                Text("1× = 1 min/s", fontSize = 12.sp, color = FP.Subtle)
            }
        }
        HorizontalDivider(color = FP.Line)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val fuel = vehicle?.kmPerLitre?.takeIf { it > 0 }?.let { trip.distanceM / 1000 / it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Distance", fmtKmNumber(trip.distanceM), Modifier.weight(1f), unit = "km")
                StatTile("Duration", fmtDuration((trip.endTime ?: endT) - trip.startTime).replace(" m", "m").replace(" h", "h"), Modifier.weight(1f))
                StatTile("Avg speed", "${trip.avgSpeedKmh.roundToInt()}", Modifier.weight(1f), unit = "km/h")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Max speed", "${trip.maxSpeedKmh.roundToInt()}", Modifier.weight(1f), unit = "km/h")
                StatTile("Idle", "${trip.idleMs / 60_000}", Modifier.weight(1f), unit = "min")
                if (fuel != null) StatTile("Fuel est.", String.format(Locale.US, "%.1f", fuel), Modifier.weight(1f), unit = "L")
                else StatTile("Score", "${trip.score}", Modifier.weight(1f), unit = "/100")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speed profile", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                val speeds = remember(points) {
                    val step = (points.size / 300).coerceAtLeast(1)
                    points.filterIndexed { i, _ -> i % step == 0 }.map { it.speedKmh }
                }
                SpeedChart(speeds, limit, if (replayStarted) (replayTime - startT).toFloat() / (endT - startT) else null)
                Row {
                    Text(fmtClock(startT), fontFamily = Mono, fontSize = 11.sp, color = FP.Subtle)
                    Spacer(Modifier.weight(1f))
                    Text("Dashed line: $limit km/h limit", fontSize = 11.sp, color = FP.OrangeText)
                    Spacer(Modifier.weight(1f))
                    Text(fmtClock(endT), fontFamily = Mono, fontSize = 11.sp, color = FP.Subtle)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Timeline", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, modifier = Modifier.padding(bottom = 6.dp))
                events.forEachIndexed { i, e ->
                    TimelineRow(e, last = i == events.lastIndex, onClick = {
                        playing = false
                        replayTime = e.time.coerceIn(startT, endT)
                    })
                }
                Text("Tap an event to jump the replay there.", fontSize = 12.sp, color = FP.Subtle)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "Export GPX",
                    icon = Icons.Outlined.FileDownload,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { Exporter.gpx(context, trip, points) }
                            Exporter.share(context, uri, "application/gpx+xml", "Export trip")
                        }
                    },
                )
                PrimaryButton(
                    "Plan again",
                    icon = Icons.Outlined.Directions,
                    container = FP.Ink,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val lat = trip.endLat
                        val lon = trip.endLon
                        if (lat != null && lon != null) {
                            app.pendingPlanDestination.value = Place(trip.endName(), "From your trip on ${fmtDate(trip.startTime, "d MMM")}", lat, lon)
                        }
                        onPlanAgain()
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this trip?") },
            text = { Text("The route and all its GPS points will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.db.points().deleteForTrip(trip.id)
                        app.db.trips().delete(trip.id)
                        onBack()
                    }
                }) { Text("Delete", color = FP.Orange) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun indexAt(points: List<PointEntity>, time: Long): Int {
    if (points.isEmpty()) return -1
    val i = points.binarySearchBy(time) { it.time }
    return if (i >= 0) i else (-i - 2).coerceIn(0, points.lastIndex)
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(14.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, fontSize = 12.sp, color = FP.Ink2)
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent, last: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 3.dp).size(14.dp).clip(CircleShape).background(event.color))
            if (!last) Box(Modifier.width(2.dp).height(44.dp).background(FP.Line))
        }
        Column(Modifier.weight(1f).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
            Text(event.detail, fontSize = 13.sp, color = FP.Muted, lineHeight = 18.sp)
        }
        Text(fmtClock(event.time), fontFamily = Mono, fontSize = 13.sp, color = FP.Muted)
    }
}
