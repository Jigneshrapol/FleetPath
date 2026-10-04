@file:OptIn(ExperimentalMaterial3Api::class)

package com.fleetpath.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.R
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.data.VehicleEntity
import com.fleetpath.app.export.Exporter
import com.fleetpath.app.tracking.LocationService
import com.fleetpath.app.ui.components.ForegroundLocationUpdates
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.MapCircle
import com.fleetpath.app.ui.components.MapLine
import com.fleetpath.app.ui.components.MapMarker
import com.fleetpath.app.ui.components.OsmMap
import com.fleetpath.app.ui.components.PrimaryButton
import com.fleetpath.app.ui.components.SecondaryButton
import com.fleetpath.app.ui.components.SquareIconButton
import com.fleetpath.app.ui.components.StatusPill
import com.fleetpath.app.ui.components.SummaryStrip
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.AddressLookup
import com.fleetpath.app.util.LatLon
import com.fleetpath.app.util.addDays
import com.fleetpath.app.util.fmtCoord
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.fmtKm
import com.fleetpath.app.util.hasLocationPermission
import com.fleetpath.app.util.startOfDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LiveScreen(
    onAlerts: () -> Unit,
    onActiveTrip: () -> Unit,
    onPlan: () -> Unit,
    onPermissions: () -> Unit,
) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val fix by app.tracking.live.collectAsStateWithLifecycle()
    val trip by app.tracking.trip.collectAsStateWithLifecycle()
    val serviceRunning by app.tracking.serviceRunning.collectAsStateWithLifecycle()
    val vehicles by remember { app.db.vehicles().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val unread by remember { app.db.alerts().observeUnreadCount() }.collectAsStateWithLifecycle(0)
    val fences by remember { app.db.geofences().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val dayStart = remember { startOfDay(System.currentTimeMillis()) }
    val today by remember(dayStart) { app.db.trips().observeBetween(dayStart, addDays(dayStart, 1)) }
        .collectAsStateWithLifecycle(emptyList())

    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    LifecycleResumeEffect(Unit) {
        hasPermission = context.hasLocationPermission()
        onPauseOrDispose { }
    }
    ForegroundLocationUpdates()

    var follow by remember { mutableStateOf(true) }
    var recenter by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Reverse-geocode roughly every 100 m of movement.
    var address by remember { mutableStateOf<String?>(null) }
    val gridKey = fix?.let { (it.lat * 1000).roundToInt() to (it.lon * 1000).roundToInt() }
    LaunchedEffect(gridKey) {
        val f = fix ?: return@LaunchedEffect
        AddressLookup.reverse(context, f.lat, f.lon)?.let { address = it }
    }

    val vehicle = vehicles.firstOrNull { it.id == settings.activeVehicleId } ?: vehicles.firstOrNull()
    val current = fix
    val activeTrip = trip

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            lines = activeTrip?.path?.let { listOf(MapLine(it, FP.Blue)) } ?: emptyList(),
            markers = current?.let { listOf(MapMarker(it.latLon, R.drawable.ic_marker_vehicle, it.bearing)) } ?: emptyList(),
            circles = fences.map { MapCircle(LatLon(it.lat, it.lon), it.radiusM.toDouble(), FP.Blue) },
            center = current?.latLon,
            follow = follow,
            recenterKey = recenter,
            style = settings.mapStyle,
            onUserGesture = { follow = false },
        )

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VehicleSelector(
                vehicle = vehicle,
                all = vehicles,
                onSelect = { v -> scope.launch { app.settings.update { it.copy(activeVehicleId = v.id) } } },
                modifier = Modifier.weight(1f),
            )
            SquareIconButton(Icons.Outlined.Notifications, "Alerts, $unread unread", onAlerts, badge = unread, elevated = true, modifier = Modifier.padding(top = 4.dp))
        }

        Column(Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareIconButton(
                Icons.Outlined.Layers, "Switch map style",
                onClick = { scope.launch { app.settings.update { it.copy(mapStyle = if (it.mapStyle == "topo") "standard" else "topo") } } },
                elevated = true,
            )
            SquareIconButton(
                Icons.Outlined.MyLocation, "Centre on vehicle",
                onClick = { follow = true; recenter++ },
                tint = FP.Blue, elevated = true,
            )
        }
        Text(
            "© OpenStreetMap contributors",
            fontSize = 10.sp, color = FP.Muted,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 84.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 4.dp, vertical = 1.dp),
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.White)
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FP.Grey).align(Alignment.CenterHorizontally))

            if (!hasPermission) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.LocationOff, FP.OrangeSoft, FP.OrangeText)
                    Column(Modifier.weight(1f)) {
                        Text("Location is off", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                        Text("Allow location to see your vehicle and record trips.", fontSize = 13.sp, color = FP.Muted)
                    }
                }
                PrimaryButton("Allow location", onClick = onPermissions, modifier = Modifier.fillMaxWidth())
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    current == null -> StatusPill("Waiting for GPS", FP.Track, FP.Ink2)
                    current.speedKmh >= 3f -> StatusPill("Moving", FP.GreenSoft, FP.Green, FP.GreenDot)
                    else -> StatusPill("Stopped", FP.Track, FP.Ink2, FP.Subtle)
                }
                if (activeTrip != null) StatusPill("Recording", FP.OrangeSoft, FP.OrangeText, FP.Orange)
                else StatusPill(if (serviceRunning) "Auto-detect on" else "Tracking off", FP.Track, FP.Ink2)
                Spacer(Modifier.weight(1f))
                if (current != null) Text(updatedAgo(now - current.time), fontSize = 12.sp, color = FP.Subtle)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    address ?: if (current == null) "Finding your location…" else "Current location",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text(
                        "Heading ${compass(current.bearing)} · ${fmtCoord(current.lat, current.lon)}",
                        fontSize = 13.sp, color = FP.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(current?.speedKmh?.roundToInt()?.toString() ?: "--", fontFamily = Mono, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                Text(" km/h", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Muted, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp, bottom = 4.dp)) {
                    Text(if (current != null) "GPS ±${current.accuracy.roundToInt()} m" else "GPS searching", fontSize = 12.sp, color = FP.Muted)
                    Text(vehicle?.plate ?: "", fontSize = 12.sp, color = FP.Muted, fontFamily = Mono)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White).border(4.dp, FP.Orange, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${settings.speedLimitKmh}", fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = FP.Ink) }
            }

            val todayKm = today.sumOf { it.distanceM } + (activeTrip?.distanceM ?: 0.0)
            val todayDrive = today.sumOf { it.movingMs } + (activeTrip?.movingMs ?: 0L)
            val todayTrips = today.size + if (activeTrip != null) 1 else 0
            SummaryStrip(listOf("Today" to fmtKm(todayKm), "Drive time" to fmtDuration(todayDrive), "Trips" to "$todayTrips"))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeTrip != null) {
                    PrimaryButton("View trip", onClick = onActiveTrip, modifier = Modifier.weight(1f), icon = Icons.Filled.PlayArrow, container = FP.Orange)
                } else {
                    PrimaryButton(
                        "Start trip",
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            app.tracking.startTrip()
                            LocationService.start(context)
                            onActiveTrip()
                        },
                    )
                }
                SecondaryButton("Plan route", onClick = onPlan, modifier = Modifier.weight(1f), icon = Icons.Outlined.Directions)
                SquareIconButton(
                    Icons.Outlined.Share, "Share live location",
                    onClick = {
                        val f = current ?: return@SquareIconButton
                        val link = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", f.lat, f.lon)
                        Exporter.shareText(context, "Current location of ${vehicle?.plate ?: "my vehicle"}: $link", "Share location")
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun VehicleSelector(
    vehicle: VehicleEntity?,
    all: List<VehicleEntity>,
    onSelect: (VehicleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(Icons.Outlined.LocalShipping, FP.BlueSoft, FP.Blue, 36)
                Column(Modifier.weight(1f)) {
                    Text(vehicle?.plate ?: "No vehicle", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1)
                    val sub = listOfNotNull(
                        vehicle?.model?.takeIf { it.isNotBlank() },
                        vehicle?.driver?.takeIf { it.isNotBlank() }?.let { "Driver $it" },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = FP.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ExpandMore, contentDescription = "Change vehicle", tint = FP.Muted)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.plate} · ${v.model}") },
                    onClick = {
                        open = false
                        onSelect(v)
                    },
                )
            }
        }
    }
}

private fun compass(bearing: Float): String {
    val names = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
    val i = (((bearing % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8
    return names[i]
}

private fun updatedAgo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "Updated $s s ago"
        s < 3600 -> "Updated ${s / 60} min ago"
        else -> "Updated ${s / 3600} h ago"
    }
}
