@file:OptIn(ExperimentalMaterial3Api::class)

package com.fleetpath.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.data.TripEntity
import com.fleetpath.app.data.durationMs
import com.fleetpath.app.data.endName
import com.fleetpath.app.data.startName
import com.fleetpath.app.export.Exporter
import com.fleetpath.app.ui.components.EmptyState
import com.fleetpath.app.ui.components.PillChip
import com.fleetpath.app.ui.components.ScreenHeader
import com.fleetpath.app.ui.components.SectionLabel
import com.fleetpath.app.ui.components.Segmented
import com.fleetpath.app.ui.components.SquareIconButton
import com.fleetpath.app.ui.components.SummaryStrip
import com.fleetpath.app.ui.components.TripThumbnail
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.addDays
import com.fleetpath.app.util.fmtClock
import com.fleetpath.app.util.fmtDate
import com.fleetpath.app.util.fmtDayHeader
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.fmtKm
import com.fleetpath.app.util.startOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private data class HistoryRange(val from: Long, val to: Long, val label: String)

private fun historyRange(mode: Int, offset: Int): HistoryRange {
    val today = startOfDay(System.currentTimeMillis())
    return when (mode) {
        0 -> {
            val s = addDays(today, offset)
            HistoryRange(s, addDays(s, 1), fmtDate(s, "EEE d MMM yyyy"))
        }
        1 -> {
            val c = Calendar.getInstance()
            c.timeInMillis = today
            val sinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val s = addDays(today, -sinceMonday + offset * 7)
            val e = addDays(s, 7)
            HistoryRange(s, e, "${fmtDate(s, "d MMM")} – ${fmtDate(addDays(e, -1), "d MMM yyyy")}")
        }
        else -> {
            val c = Calendar.getInstance()
            c.timeInMillis = today
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, offset)
            val s = c.timeInMillis
            c.add(Calendar.MONTH, 1)
            HistoryRange(s, c.timeInMillis, fmtDate(s, "MMMM yyyy"))
        }
    }
}

@Composable
fun TripHistoryScreen(onOpenTrip: (Long) -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableIntStateOf(1) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val range = remember(mode, offset) { historyRange(mode, offset) }
    val trips by remember(range) { app.db.trips().observeBetween(range.from, range.to) }.collectAsStateWithLifecycle(emptyList())

    val shown = trips.filter {
        when (filter) {
            1 -> !it.autoDetected
            2 -> it.autoDetected
            3 -> it.overspeedCount > 0 || it.harshBrakeCount > 0
            else -> true
        }
    }
    val grouped = shown.groupBy { startOfDay(it.startTime) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Trips") {
                SquareIconButton(Icons.Outlined.FileDownload, "Export these trips as CSV", onClick = {
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { Exporter.tripsCsv(context, shown) }
                        Exporter.share(context, uri, "text/csv", "Export trips")
                    }
                })
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(listOf("Day", "Week", "Month"), mode, { mode = it; offset = 0 })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset-- }) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous") }
                    Text(range.label, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next") }
                }
                SummaryStrip(
                    listOf(
                        "Trips" to "${shown.size}",
                        "Distance" to fmtKm(shown.sumOf { it.distanceM }),
                        "Drive time" to fmtDuration(shown.sumOf { it.movingMs }),
                    )
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All trips", "Manual", "Auto-detected", "With alerts").forEachIndexed { i, label ->
                        PillChip(label, filter == i, { filter = i })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.History,
                    "No trips here yet",
                    if (trips.isEmpty()) "Trips you record show up here with their route, distance and driving score."
                    else "No trips match this filter.",
                )
            }
        }
        grouped.forEach { (day, list) ->
            item(key = "h$day") {
                SectionLabel(fmtDayHeader(day), Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp))
            }
            items(list, key = { it.id }) { trip ->
                TripCard(trip, onClick = { onOpenTrip(trip.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
fun TripCard(trip: TripEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, FP.Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TripThumbnail(trip.previewPath)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${trip.startName()} → ${trip.endName()}",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${fmtClock(trip.startTime)} – ${trip.endTime?.let { fmtClock(it) } ?: "now"} · ${fmtDuration(trip.durationMs())}",
                    fontSize = 13.sp, color = FP.Muted,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fmtKm(trip.distanceM), fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                    if (trip.overspeedCount > 0) {
                        Badge("${trip.overspeedCount} over-speed", FP.OrangeSoft, FP.OrangeText)
                    } else {
                        Badge("Score ${trip.score}", FP.GreenSoft, FP.Green)
                    }
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = FP.Subtle, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
