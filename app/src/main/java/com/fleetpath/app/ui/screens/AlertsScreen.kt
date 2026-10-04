package com.fleetpath.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.data.AlertEntity
import com.fleetpath.app.data.AlertType
import com.fleetpath.app.ui.components.EmptyState
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.PillChip
import com.fleetpath.app.ui.components.ScreenHeader
import com.fleetpath.app.ui.components.SectionLabel
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.util.fmtClock
import com.fleetpath.app.util.fmtDayHeader
import com.fleetpath.app.util.startOfDay
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val alerts by remember { app.db.alerts().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val types = listOf(null, AlertType.SPEED, AlertType.GEOFENCE, AlertType.TRIP)
    val labels = listOf("All", "Speed", "Geofence", "Trips")
    val shown = alerts.filter { types[filter] == null || it.type == types[filter] }
    val grouped = shown.groupBy { startOfDay(it.time) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Alerts", onBack = onBack) {
            TextButton(onClick = { scope.launch { app.db.alerts().markAllRead() } }) {
                Text("Mark all read", color = FP.Blue, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val count = alerts.count { types[i] == null || it.type == types[i] }
                PillChip("$label · $count", filter == i, { filter = i })
            }
        }
        if (shown.isEmpty()) {
            EmptyState(Icons.Outlined.NotificationsNone, "No alerts", "Over-speed, geofence and trip alerts will appear here.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                grouped.forEach { (day, list) ->
                    item(key = "h$day") {
                        SectionLabel(fmtDayHeader(day), Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(list, key = { it.id }) { a ->
                        AlertRow(a) {
                            scope.launch { app.db.alerts().markRead(a.id) }
                            a.tripId?.let(onOpenTrip)
                        }
                        HorizontalDivider(color = FP.Line)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertEntity, onClick: () -> Unit) {
    val (icon, colors) = when (alert.type) {
        AlertType.SPEED -> Icons.Outlined.Speed to (FP.OrangeSoft to FP.OrangeText)
        AlertType.GEOFENCE -> Icons.Outlined.TripOrigin to (FP.BlueSoft to FP.Blue)
        else -> Icons.Outlined.Directions to (FP.GreenSoft to FP.Green)
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (alert.isRead) Color.White else Color(0xFFF7F9FE))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, colors.first, colors.second)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(alert.title, fontSize = 15.sp, fontWeight = if (alert.isRead) FontWeight.SemiBold else FontWeight.Bold, color = FP.Ink)
            Text(alert.message, fontSize = 13.sp, color = FP.Muted, lineHeight = 18.sp)
            Text(fmtClock(alert.time), fontSize = 12.sp, color = FP.Subtle)
        }
        if (!alert.isRead) {
            Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(FP.Blue))
        }
    }
}
