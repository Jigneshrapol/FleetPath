package com.fleetpath.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.data.GeofenceEntity
import com.fleetpath.app.data.VehicleEntity
import com.fleetpath.app.export.Exporter
import com.fleetpath.app.tracking.LocationService
import com.fleetpath.app.ui.components.BorderedCard
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.LabeledField
import com.fleetpath.app.ui.components.PillChip
import com.fleetpath.app.ui.components.ScreenHeader
import com.fleetpath.app.ui.components.SectionLabel
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.hasBackgroundLocation
import com.fleetpath.app.util.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onPermissions: () -> Unit, onSignedOut: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val vehicles by remember { app.db.vehicles().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val fences by remember { app.db.geofences().observeAll() }.collectAsStateWithLifecycle(emptyList())

    var addVehicle by remember { mutableStateOf(false) }
    var addFence by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { app.settings.update(transform) }
    }

    fun setAutoDetect(on: Boolean) {
        if (on && !context.hasLocationPermission()) {
            onPermissions()
            return
        }
        scope.launch {
            app.settings.update { it.copy(autoDetect = on) }
            if (on) LocationService.start(context)
            else if (app.tracking.trip.value == null) LocationService.stop(context)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings")
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Profile
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FP.Surface2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val initials = settings.userName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
                Box(Modifier.size(52.dp).clip(CircleShape).background(FP.Ink), contentAlignment = Alignment.Center) {
                    Text(initials.ifEmpty { "?" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text(settings.userName.ifBlank { "Your account" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                    Text(listOf(settings.phone, "Owner").filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = FP.Muted)
                }
            }

            Group("Vehicles") {
                vehicles.forEachIndexed { i, v ->
                    if (i > 0) HorizontalDivider(color = FP.Line)
                    VehicleRow(
                        v,
                        active = v.id == settings.activeVehicleId,
                        onSelect = { update { it.copy(activeVehicleId = v.id) } },
                        onDelete = if (v.id != settings.activeVehicleId) ({ scope.launch { app.db.vehicles().delete(v) } }) else null,
                    )
                }
                if (vehicles.isNotEmpty()) HorizontalDivider(color = FP.Line)
                ActionRow(Icons.Outlined.Add, "Add vehicle", color = FP.Blue) { addVehicle = true }
            }

            Group("Tracking") {
                SwitchRow(
                    Icons.Outlined.Directions, "Auto-detect trips",
                    "Starts recording when you drive faster than 10 km/h and stops after 5 minutes parked.",
                    settings.autoDetect, ::setAutoDetect,
                )
                if (settings.autoDetect && !context.hasBackgroundLocation()) {
                    Text(
                        "Allow location \"all the time\" so trips record with the screen off.",
                        fontSize = 13.sp, color = FP.OrangeText,
                        modifier = Modifier.clickable(onClick = onPermissions).padding(start = 50.dp, end = 14.dp, bottom = 12.dp),
                    )
                }
                HorizontalDivider(color = FP.Line)
                ChoiceRow(Icons.Outlined.Schedule, "Location update interval", listOf(5, 10, 30).map { "$it s" to it }, settings.intervalSec) { v -> update { it.copy(intervalSec = v) } }
                HorizontalDivider(color = FP.Line)
                SwitchRow(Icons.Outlined.BatteryStd, "Battery saver", "Slower updates while no trip is recording.", settings.batterySaver) { on -> update { it.copy(batterySaver = on) } }
                HorizontalDivider(color = FP.Line)
                ChoiceRow(Icons.Outlined.Layers, "Map style", listOf("Standard" to "standard", "Terrain" to "topo"), settings.mapStyle) { v -> update { it.copy(mapStyle = v) } }
            }

            Group("Alerts and zones") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = FP.Ink2)
                    Text("Over-speed limit", fontSize = 15.sp, color = FP.Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = { update { it.copy(speedLimitKmh = (it.speedLimitKmh - 5).coerceAtLeast(20)) } }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Lower limit")
                    }
                    Text("${settings.speedLimitKmh} km/h", fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                    IconButton(onClick = { update { it.copy(speedLimitKmh = (it.speedLimitKmh + 5).coerceAtMost(160)) } }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Raise limit")
                    }
                }
                fences.forEach { f ->
                    HorizontalDivider(color = FP.Line)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.TripOrigin, contentDescription = null, tint = FP.Blue)
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontSize = 15.sp, color = FP.Ink)
                            Text("Geofence · ${f.radiusM.roundToInt()} m radius", fontSize = 13.sp, color = FP.Muted)
                        }
                        IconButton(onClick = { scope.launch { app.db.geofences().delete(f) } }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${f.name}", tint = FP.Muted)
                        }
                    }
                }
                HorizontalDivider(color = FP.Line)
                ActionRow(Icons.Outlined.Add, "Add geofence at current location", color = FP.Blue) { addFence = true }
                HorizontalDivider(color = FP.Line)
                SwitchRow(Icons.Outlined.Notifications, "Push notifications", "Over-speed, geofence and trip alerts.", settings.notifications) { on -> update { it.copy(notifications = on) } }
            }

            Group("Data and privacy") {
                ActionRow(Icons.Outlined.FileDownload, "Export all trips (CSV)") {
                    scope.launch {
                        val trips = app.db.trips().allCompleted()
                        val uri = withContext(Dispatchers.IO) { Exporter.tripsCsv(context, trips) }
                        Exporter.share(context, uri, "text/csv", "Export trips")
                    }
                }
                HorizontalDivider(color = FP.Line)
                ChoiceRow(Icons.Outlined.Lock, "Keep history for", listOf(3, 6, 12, 24).map { "$it mo" to it }, settings.historyMonths) { v -> update { it.copy(historyMonths = v) } }
                HorizontalDivider(color = FP.Line)
                ActionRow(Icons.Outlined.Security, "App permissions", onClick = onPermissions)
                HorizontalDivider(color = FP.Line)
                ActionRow(Icons.Outlined.DeleteOutline, "Delete all trip history", color = FP.Orange) { confirmWipe = true }
                HorizontalDivider(color = FP.Line)
                ActionRow(Icons.AutoMirrored.Outlined.Logout, "Sign out") { confirmSignOut = true }
            }

            Text(
                "FleetPath 1.0.0 · Map data © OpenStreetMap contributors · Routing by OSRM",
                fontSize = 12.sp, color = FP.Subtle,
            )
        }
    }

    if (addVehicle) {
        AddVehicleDialog(
            onDismiss = { addVehicle = false },
            onSave = { v ->
                addVehicle = false
                scope.launch {
                    val id = app.db.vehicles().insert(v)
                    if (vehicles.isEmpty()) app.settings.update { it.copy(activeVehicleId = id) }
                }
            },
        )
    }
    if (addFence) {
        AddFenceDialog(
            hasFix = app.tracking.live.value != null,
            onDismiss = { addFence = false },
            onSave = { name, radius ->
                addFence = false
                val fix = app.tracking.live.value
                if (fix != null) {
                    scope.launch { app.db.geofences().insert(GeofenceEntity(name = name, lat = fix.lat, lon = fix.lon, radiusM = radius)) }
                }
            },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all trip history?") },
            text = { Text("Every recorded trip, route and alert on this phone will be removed. Vehicles and settings stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        app.db.points().deleteAll()
                        app.db.trips().deleteAll()
                        app.db.alerts().deleteAll()
                    }
                }) { Text("Delete", color = FP.Orange) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Tracking will stop. Your trips stay on this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch {
                        app.tracking.stopTrip()
                        LocationService.stop(context)
                        app.settings.update { it.copy(signedIn = false, autoDetect = false) }
                        onSignedOut()
                    }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        BorderedCard { Column { content() } }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = FP.Ink2)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = FP.Ink)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = FP.Muted, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FP.Blue, uncheckedTrackColor = FP.Grey, uncheckedThumbColor = Color.White, uncheckedBorderColor = FP.Grey),
        )
    }
}

@Composable
private fun <T> ChoiceRow(icon: ImageVector, title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = FP.Ink2)
            Text(title, fontSize = 15.sp, color = FP.Ink)
        }
        Row(Modifier.padding(start = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) -> PillChip(label, value == selected, { onSelect(value) }) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, color: Color = FP.Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (color == FP.Ink) FP.Ink2 else color)
        Text(title, fontSize = 15.sp, color = color, fontWeight = if (color == FP.Ink) FontWeight.Normal else FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (color == FP.Ink) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = FP.Subtle)
    }
}

@Composable
private fun VehicleRow(vehicle: VehicleEntity, active: Boolean, onSelect: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(Icons.Outlined.LocalShipping)
        Column(Modifier.weight(1f)) {
            Text(vehicle.plate, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
            Text(
                listOf(vehicle.model, vehicle.driver.takeIf { it.isNotBlank() }?.let { "Driver $it" } ?: "")
                    .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Vehicle" },
                fontSize = 13.sp, color = FP.Muted,
            )
        }
        if (active) {
            Text("Active", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FP.Green, modifier = Modifier.padding(end = 12.dp))
        } else {
            RadioButton(selected = false, onClick = onSelect, colors = RadioButtonDefaults.colors(unselectedColor = FP.Field))
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${vehicle.plate}", tint = FP.Muted) }
            }
        }
    }
}

@Composable
private fun AddVehicleDialog(onDismiss: () -> Unit, onSave: (VehicleEntity) -> Unit) {
    var plate by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("Number plate", plate, { plate = it }, placeholder = "GJ 15 CD 1190", capitalization = KeyboardCapitalization.Characters)
                LabeledField("Make and model", model, { model = it }, placeholder = "e.g. Maruti Eeco", capitalization = KeyboardCapitalization.Words)
                LabeledField("Driver (optional)", driver, { driver = it }, capitalization = KeyboardCapitalization.Words)
                LabeledField("Mileage, km per litre (optional)", mileage, { mileage = it }, placeholder = "e.g. 14", keyboardType = KeyboardType.Decimal)
            }
        },
        confirmButton = {
            TextButton(
                enabled = plate.isNotBlank(),
                onClick = {
                    onSave(
                        VehicleEntity(
                            plate = plate.trim().uppercase(),
                            model = model.trim(),
                            driver = driver.trim(),
                            kmPerLitre = mileage.replace(",", ".").toFloatOrNull(),
                        )
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddFenceDialog(hasFix: Boolean, onDismiss: () -> Unit, onSave: (String, Float) -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(300f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add geofence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasFix) {
                    Text("Waiting for a GPS fix. Open the Live tab for a moment, then try again.", color = FP.OrangeText, fontSize = 14.sp)
                }
                Text("You'll get an alert when the vehicle enters or leaves this zone, centred on your current location.", fontSize = 14.sp, color = FP.Muted)
                LabeledField("Zone name", name, { name = it }, placeholder = "e.g. Home, Depot", capitalization = KeyboardCapitalization.Words)
                Text("Radius: ${radius.roundToInt()} m", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink2)
                Slider(
                    value = radius,
                    onValueChange = { radius = (it / 50f).roundToInt() * 50f },
                    valueRange = 100f..2000f,
                    colors = SliderDefaults.colors(thumbColor = FP.Blue, activeTrackColor = FP.Blue),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = hasFix && name.isNotBlank(), onClick = { onSave(name.trim(), radius) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
