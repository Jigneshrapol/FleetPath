package com.fleetpath.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.R
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.tracking.LocationService
import com.fleetpath.app.ui.components.EmptyState
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.MapLine
import com.fleetpath.app.ui.components.MapMarker
import com.fleetpath.app.ui.components.OsmMap
import com.fleetpath.app.ui.components.PrimaryButton
import com.fleetpath.app.ui.components.ScreenHeader
import com.fleetpath.app.ui.components.SecondaryButton
import com.fleetpath.app.ui.components.StatTile
import com.fleetpath.app.ui.openNavigation
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.distanceM
import com.fleetpath.app.util.fmtClock
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.fmtKm
import com.fleetpath.app.util.fmtKmNumber
import com.fleetpath.app.util.fmtTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ActiveTripScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trip by app.tracking.trip.collectAsStateWithLifecycle()
    val fix by app.tracking.live.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var saving by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val t = trip
    if (t == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Trip", onBack = onBack)
            if (saving) {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = FP.Blue)
                    Text("Saving trip…", color = FP.Muted)
                }
            } else {
                EmptyState(Icons.Outlined.Directions, "No trip in progress", "Start a trip from the Live tab, or turn on auto-detect in Settings to record trips automatically.")
            }
        }
        return
    }

    val current = fix
    val avgKmh = if (t.movingMs > 0) t.distanceM / 1000.0 / (t.movingMs / 3_600_000.0) else 0.0

    Box(Modifier.fillMaxSize()) {
        val lines = buildList {
            t.planned?.let { add(MapLine(it, FP.Subtle, widthDp = 5f, dashed = true)) }
            add(MapLine(t.path, FP.Blue))
        }
        val markers = buildList {
            t.destination?.let { add(MapMarker(it, R.drawable.ic_marker_destination, anchorBottom = true)) }
            t.path.firstOrNull()?.let { add(MapMarker(it, R.drawable.ic_marker_start)) }
            current?.let { add(MapMarker(it.latLon, R.drawable.ic_marker_vehicle, it.bearing)) }
        }
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            lines = lines,
            markers = markers,
            center = current?.latLon,
            follow = follow,
            style = settings.mapStyle,
            onUserGesture = { follow = false },
        )

        // Header card with timer and live speed
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)
                .shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(FP.Orange))
                    Text("RECORDING TRIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = FP.OrangeText)
                }
                Text(fmtTimer(now - t.startTime), fontFamily = Mono, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                Text(
                    "Started ${fmtClock(t.startTime)} · ${if (t.auto) "auto-detected" else "manual"}",
                    fontSize = 13.sp, color = FP.Muted,
                )
            }
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(FP.Ink).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${current?.speedKmh?.roundToInt() ?: 0}", fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("km/h", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dest = t.destination
            if (dest != null || t.movingMs > 2 * 3_600_000L) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (dest != null) {
                        val remaining = current?.let { distanceM(it.latLon, dest) }
                        IconTile(Icons.Outlined.Navigation, FP.BlueSoft, FP.Blue)
                        Column(Modifier.weight(1f)) {
                            Text("DESTINATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = FP.Muted)
                            Text(t.destinationName ?: "Destination", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (remaining != null) Text("${fmtKm(remaining)} away in a straight line", fontSize = 13.sp, color = FP.Muted)
                        }
                        SecondaryButton("Navigate", onClick = { openNavigation(context, dest) })
                    } else {
                        IconTile(Icons.Outlined.LocalCafe, FP.AmberSoft, FP.Amber)
                        Column(Modifier.weight(1f)) {
                            Text("TIME FOR A BREAK", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = FP.Muted)
                            Text("You've been driving for ${fmtDuration(t.movingMs)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                            Text("A 15-minute stop every two hours helps you stay alert.", fontSize = 13.sp, color = FP.Muted)
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color.White)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Distance", fmtKmNumber(t.distanceM), Modifier.weight(1f), unit = "km")
                    StatTile("Average speed", "${avgKmh.roundToInt()}", Modifier.weight(1f), unit = "km/h")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Max speed", "${t.maxKmh.roundToInt()}", Modifier.weight(1f), unit = "km/h")
                    StatTile("Idle time", "${t.idleMs / 60_000}", Modifier.weight(1f), unit = "min")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Map", onClick = onBack, icon = Icons.Outlined.Map, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        if (saving) "Saving…" else "Stop & save trip",
                        icon = Icons.Filled.Stop,
                        container = FP.Ink,
                        enabled = !saving,
                        modifier = Modifier.weight(1.6f),
                        onClick = {
                            saving = true
                            scope.launch {
                                val id = app.tracking.stopTrip()
                                if (!settings.autoDetect) LocationService.stop(context)
                                if (id != null) onSaved(id) else onBack()
                            }
                        },
                    )
                }
            }
        }
    }
}
