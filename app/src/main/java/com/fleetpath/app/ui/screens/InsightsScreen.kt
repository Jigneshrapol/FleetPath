package com.fleetpath.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.analysis.Period
import com.fleetpath.app.analysis.bars
import com.fleetpath.app.analysis.frequentPlaces
import com.fleetpath.app.analysis.periodRange
import com.fleetpath.app.analysis.weightedScore
import com.fleetpath.app.ui.components.BarChart
import com.fleetpath.app.ui.components.BorderedCard
import com.fleetpath.app.ui.components.EmptyState
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.ScoreRing
import com.fleetpath.app.ui.components.ScreenHeader
import com.fleetpath.app.ui.components.Segmented
import com.fleetpath.app.ui.components.SplitBar
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.fmtDuration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun InsightsScreen() {
    val app = rememberApp()
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[periodIdx]
    val range = remember(period) { periodRange(period) }
    val all by remember(range) { app.db.trips().observeBetween(range.prevFrom, range.to) }.collectAsStateWithLifecycle(emptyList())
    val vehicles by remember { app.db.vehicles().observeAll() }.collectAsStateWithLifecycle(emptyList())
    var vehicleFilter by rememberSaveable { mutableLongStateOf(-1L) }
    var vehicleMenu by remember { mutableStateOf(false) }

    val filtered = all.filter { vehicleFilter < 0 || it.vehicleId == vehicleFilter }
    val current = filtered.filter { it.startTime >= range.from }
    val previous = filtered.filter { it.startTime < range.from }
    val totalKm = current.sumOf { it.distanceM } / 1000
    val prevKm = previous.sumOf { it.distanceM } / 1000
    val barData = remember(current, period) { bars(period, range, current) }
    val score = weightedScore(current)
    val moving = current.sumOf { it.movingMs }
    val idle = current.sumOf { it.idleMs }
    val parked = (min(System.currentTimeMillis(), range.to) - range.from - moving - idle).coerceAtLeast(0L)
    val places = remember(current) { frequentPlaces(current, 3) }
    val periodName = when (period) {
        Period.WEEK -> "last 7 days"
        Period.MONTH -> "last 30 days"
        Period.YEAR -> "last 12 months"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Insights") {
            Box {
                OutlinedButton(
                    onClick = { vehicleMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, FP.Line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FP.Ink),
                ) {
                    Icon(Icons.Outlined.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        vehicles.firstOrNull { it.id == vehicleFilter }?.plate ?: "All vehicles",
                        modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                    DropdownMenuItem(text = { Text("All vehicles") }, onClick = { vehicleFilter = -1L; vehicleMenu = false })
                    vehicles.forEach { v ->
                        DropdownMenuItem(text = { Text("${v.plate} · ${v.model}") }, onClick = { vehicleFilter = v.id; vehicleMenu = false })
                    }
                }
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmented(Period.entries.map { it.label }, periodIdx, { periodIdx = it })

            if (current.isEmpty()) {
                EmptyState(Icons.Outlined.BarChart, "No trips in the $periodName", "Record a few trips and you'll see distance, driving score and your most visited places here.")
                return@Column
            }

            BorderedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Distance, $periodName", fontSize = 13.sp, color = FP.Muted)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(String.format(Locale.US, "%.0f", totalKm), fontFamily = Mono, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                                Text(" km", fontSize = 15.sp, color = FP.Muted, modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                        val change = if (prevKm > 0.5) ((totalKm - prevKm) / prevKm * 100).roundToInt() else null
                        Text(
                            when {
                                change == null -> "No earlier data"
                                change >= 0 -> "+$change% vs previous"
                                else -> "−${abs(change)}% vs previous"
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink2,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(FP.Track).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    BarChart(barData)
                }
            }

            BorderedCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ScoreRing(score ?: 100)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        val label = when {
                            (score ?: 100) >= 85 -> "Excellent"
                            (score ?: 100) >= 70 -> "Good"
                            (score ?: 100) >= 50 -> "Needs work"
                            else -> "Risky"
                        }
                        Text("Driving score · $label", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                        ScoreRow(Icons.Outlined.Speed, "Over-speeding", "${current.sumOf { it.overspeedCount }}", FP.OrangeText)
                        ScoreRow(Icons.Outlined.Warning, "Harsh braking", "${current.sumOf { it.harshBrakeCount }}")
                        ScoreRow(Icons.Outlined.Schedule, "Idling", fmtDuration(idle))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Where the time went", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                SplitBar(listOf(moving.toFloat() to FP.Blue, idle.toFloat() to FP.OrangeMid, parked.toFloat() to FP.Grey))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(FP.Blue, "Moving ${fmtDuration(moving)}")
                    LegendDot(FP.OrangeMid, "Idle ${fmtDuration(idle)}")
                    LegendDot(FP.Grey, "Parked ${fmtDuration(parked)}")
                }
                Text("${current.size} trips · average ${fmtDuration(current.sumOf { it.movingMs + it.idleMs } / current.size)} each", fontSize = 13.sp, color = FP.Muted)
            }

            if (places.isNotEmpty()) {
                Column {
                    Text("Most visited", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, modifier = Modifier.padding(bottom = 4.dp))
                    places.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = FP.Line)
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(Icons.Outlined.Business, size = 36)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.avgStayMs?.let { "Average stay ${fmtDuration(it)}" } ?: "Trip destination", fontSize = 13.sp, color = FP.Muted)
                            }
                            Text("${p.visits}×", fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(icon: ImageVector, label: String, value: String, valueColor: Color = FP.Ink) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = FP.Muted, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, color = FP.Ink, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, fontSize = 12.sp, color = FP.Ink2)
    }
}
