@file:OptIn(ExperimentalMaterial3Api::class)

package com.fleetpath.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.R
import com.fleetpath.app.analysis.departureTip
import com.fleetpath.app.analysis.frequentPlaces
import com.fleetpath.app.data.AppSettings
import com.fleetpath.app.data.TripEntity
import com.fleetpath.app.routing.Place
import com.fleetpath.app.routing.RouteOption
import com.fleetpath.app.routing.RoutingClient
import com.fleetpath.app.tracking.LocationService
import com.fleetpath.app.ui.components.ForegroundLocationUpdates
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.MapLine
import com.fleetpath.app.ui.components.MapMarker
import com.fleetpath.app.ui.components.OsmMap
import com.fleetpath.app.ui.components.PillChip
import com.fleetpath.app.ui.components.PrimaryButton
import com.fleetpath.app.ui.components.SecondaryButton
import com.fleetpath.app.ui.components.SectionLabel
import com.fleetpath.app.ui.openNavigation
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.LatLon
import com.fleetpath.app.util.fmtDuration
import com.fleetpath.app.util.fmtKm
import com.fleetpath.app.util.hasLocationPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PlanRouteScreen(onStarted: () -> Unit, onPermissions: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    ForegroundLocationUpdates()

    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val fix by app.tracking.live.collectAsStateWithLifecycle()
    val pending by app.pendingPlanDestination.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<Place?>(null) }
    var origin by remember { mutableStateOf<LatLon?>(null) }
    var avoidTolls by rememberSaveable { mutableStateOf(false) }
    var routes by remember { mutableStateOf<List<RouteOption>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val history by produceState(emptyList<TripEntity>()) { value = app.db.trips().allCompleted() }
    val places = remember(history) { frequentPlaces(history, 4) }
    val tip = remember(history, destination) { destination?.let { departureTip(history, it.latLon) } }

    fun choose(p: Place) {
        destination = p
        query = p.name
        results = emptyList()
        message = null
        origin = app.tracking.live.value?.latLon
        focus.clearFocus()
    }

    LaunchedEffect(pending) {
        pending?.let {
            choose(it)
            app.pendingPlanDestination.value = null
        }
    }
    // If GPS wasn't ready when the destination was picked, use the first fix that arrives.
    LaunchedEffect(destination, fix != null) {
        if (destination != null && origin == null) origin = app.tracking.live.value?.latLon
    }
    LaunchedEffect(origin, destination, avoidTolls) {
        val o = origin
        val d = destination
        if (o == null || d == null) {
            routes = emptyList()
            return@LaunchedEffect
        }
        loading = true
        message = null
        routes = try {
            RoutingClient.routes(o, d.latLon, avoidTolls)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (avoidTolls) {
                try {
                    val r = RoutingClient.routes(o, d.latLon, false)
                    message = "Toll avoidance isn't supported by the routing server, so all routes are shown."
                    r
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    message = "Couldn't load routes. Check your internet connection."
                    emptyList()
                }
            } else {
                message = "Couldn't load routes. Check your internet connection."
                emptyList()
            }
        }
        selected = 0
        loading = false
    }

    fun search() {
        val q = query.trim()
        if (q.length < 3) {
            message = "Type at least 3 letters to search."
            return
        }
        focus.clearFocus()
        searching = true
        message = null
        scope.launch {
            results = try {
                RoutingClient.search(q, app.tracking.live.value?.latLon)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Search failed. Check your internet connection."
                emptyList()
            }
            if (results.isEmpty() && message == null) message = "No places found for \"$q\"."
            searching = false
        }
    }

    val route = routes.getOrNull(selected)
    val dest = destination

    Column(Modifier.fillMaxSize()) {
        // ---------- From / To ----------
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Plan route", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FP.Ink)
            Column(Modifier.fillMaxWidth().border(1.dp, FP.Field, RoundedCornerShape(14.dp))) {
                Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).border(3.dp, FP.Ink, CircleShape))
                    Text("From", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FP.Muted, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        when {
                            !context.hasLocationPermission() -> "Location permission needed"
                            fix == null -> "Waiting for GPS…"
                            else -> "Current location"
                        },
                        fontSize = 15.sp, color = FP.Ink, modifier = Modifier.weight(1f),
                    )
                    if (!context.hasLocationPermission()) {
                        Text("Allow", color = FP.Blue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onPermissions).padding(8.dp))
                    }
                }
                HorizontalDivider(color = FP.Line)
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = FP.Blue, modifier = Modifier.size(18.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Where to? Search a place or address", color = FP.Subtle) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            destination = null
                            results = emptyList()
                            routes = emptyList()
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Clear destination", tint = FP.Muted) }
                    }
                    IconButton(onClick = { search() }) {
                        if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FP.Blue)
                        else Icon(Icons.Outlined.Search, contentDescription = "Search", tint = FP.Ink)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip(if (avoidTolls) "Avoiding tolls" else "Avoid tolls", avoidTolls, { avoidTolls = !avoidTolls })
                places.forEach { p ->
                    PillChip(p.name.substringBefore(","), destination?.let { it.lat == p.lat && it.lon == p.lon } == true, {
                        query = p.name
                        choose(Place(p.name, "Visited ${p.visits} times", p.lat, p.lon))
                    })
                }
            }
        }

        // ---------- Map ----------
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            val lines = buildList {
                routes.forEachIndexed { i, r -> if (i != selected) add(MapLine(r.points, FP.Subtle, widthDp = 4f, dashed = true)) }
                route?.let { add(MapLine(it.points, FP.Blue)) }
            }
            val markers = buildList {
                origin?.let { add(MapMarker(it, R.drawable.ic_marker_start)) }
                dest?.let { add(MapMarker(it.latLon, R.drawable.ic_marker_destination, anchorBottom = true)) }
                if (origin == null) fix?.let { add(MapMarker(it.latLon, R.drawable.ic_marker_vehicle, it.bearing)) }
            }
            val fit = route?.points ?: listOfNotNull(origin, dest?.latLon).takeIf { it.isNotEmpty() }
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lines = lines,
                markers = markers,
                center = fix?.latLon,
                zoom = 13.0,
                fitTo = fit,
                fitKey = Triple(dest?.lat, selected, routes.size),
                style = settings.mapStyle,
            )
            Text(
                "© OpenStreetMap contributors",
                fontSize = 10.sp, color = FP.Muted,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
            )
        }

        // ---------- Results, routes and suggestions ----------
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            message?.let { Text(it, fontSize = 14.sp, color = FP.OrangeText) }

            if (results.isNotEmpty()) {
                SectionLabel("Search results")
                Column(Modifier.fillMaxWidth().border(1.dp, FP.Line, RoundedCornerShape(14.dp))) {
                    results.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = FP.Line)
                        Row(
                            Modifier.fillMaxWidth().clickable { choose(p) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = FP.Muted)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (p.detail.isNotBlank()) Text(p.detail, fontSize = 13.sp, color = FP.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = FP.Blue)
                    Text("Finding routes…", color = FP.Muted)
                }
            }

            val fastest = routes.minOfOrNull { it.durationS } ?: 0.0
            routes.forEachIndexed { i, r ->
                RouteCard(
                    route = r,
                    index = i,
                    selected = i == selected,
                    extraMinutes = ((r.durationS - fastest) / 60).toInt(),
                    onClick = { selected = i },
                )
            }

            if (dest != null || history.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FP.Surface2).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = FP.Ink2, modifier = Modifier.size(16.dp))
                        Text("BASED ON YOUR TRIP HISTORY", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = FP.Ink2)
                    }
                    when {
                        tip != null -> SuggestionRow(Icons.Outlined.Schedule, tip.title, tip.detail)
                        dest != null -> SuggestionRow(Icons.Outlined.Schedule, "No past trips to this place yet", "After a few trips here, FleetPath will suggest the best time to leave.")
                    }
                    if (places.isNotEmpty() && dest == null) {
                        SuggestionRow(Icons.Outlined.Home, "Your frequent places", places.joinToString(" · ") { "${it.name.substringBefore(",")} (${it.visits}×)" })
                    }
                }
            } else {
                SuggestionRow(Icons.Outlined.AutoAwesome, "Smart suggestions", "Search a destination above. As you record trips, FleetPath learns your usual places and the best times to leave.")
            }

            if (dest != null && route != null) {
                PrimaryButton(
                    "Start trip on this route",
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!context.hasLocationPermission()) {
                            onPermissions()
                        } else {
                            app.tracking.startTrip(planned = route.points, destination = dest.latLon, destinationName = dest.name)
                            LocationService.start(context)
                            onStarted()
                        }
                    },
                )
                SecondaryButton(
                    "Turn-by-turn in Google Maps",
                    icon = Icons.Outlined.Navigation,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openNavigation(context, dest.latLon) },
                )
            }
            Text(
                "Routes by OSRM and search by Nominatim, using OpenStreetMap data. Travel times don't include live traffic.",
                fontSize = 11.sp, color = FP.Subtle, lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun RouteCard(route: RouteOption, index: Int, selected: Boolean, extraMinutes: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) FP.Blue else FP.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (route.summary.isNotBlank()) "Via ${route.summary}" else "Route ${index + 1}",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (index == 0) {
                    Text(
                        "FASTEST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FP.Blue,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(FP.BlueSoft).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Box(Modifier.weight(1f))
                Text(fmtDuration((route.durationS * 1000).toLong()), fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
            }
            Text(
                fmtKm(route.distanceM) + if (extraMinutes > 0) " · $extraMinutes min slower than fastest" else "",
                fontSize = 13.sp, color = FP.Muted,
            )
        }
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, Color.White, FP.Ink, 36)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
            Text(detail, fontSize = 13.sp, color = FP.Muted, lineHeight = 18.sp)
        }
    }
}
