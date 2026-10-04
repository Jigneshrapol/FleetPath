package com.fleetpath.app.ui.components

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fleetpath.app.util.LatLon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

data class MapLine(val points: List<LatLon>, val color: Color, val widthDp: Float = 5f, val dashed: Boolean = false)
data class MapMarker(val position: LatLon, @DrawableRes val icon: Int, val rotation: Float = 0f, val anchorBottom: Boolean = false)
data class MapCircle(val center: LatLon, val radiusM: Double, val color: Color)

/**
 * OpenStreetMap view (no API key needed).
 * - [center] + [follow]: keep the camera on a moving point.
 * - [fitTo]: zoom to show these points (route or trip), re-run when [fitKey] changes.
 * - [recenterKey]: bump to animate back to [center].
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    lines: List<MapLine> = emptyList(),
    markers: List<MapMarker> = emptyList(),
    circles: List<MapCircle> = emptyList(),
    center: LatLon? = null,
    follow: Boolean = false,
    zoom: Double = 16.0,
    fitTo: List<LatLon>? = null,
    fitKey: Any? = null,
    recenterKey: Int = 0,
    style: String = "standard",
    onUserGesture: () -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(20.5937, 78.9629)) // India until the first fix arrives
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(style) {
        mapView.setTileSource(if (style == "topo") TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
    }

    val hasCentered = remember { booleanArrayOf(false) }
    LaunchedEffect(center?.lat, center?.lon, follow) {
        if (center == null || fitTo != null) return@LaunchedEffect
        val p = GeoPoint(center.lat, center.lon)
        if (!hasCentered[0]) {
            hasCentered[0] = true
            mapView.controller.setZoom(zoom)
            mapView.controller.setCenter(p)
        } else if (follow) {
            mapView.controller.animateTo(p)
        }
    }
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0 && center != null) {
            mapView.controller.animateTo(GeoPoint(center.lat, center.lon), zoom, 600L)
        }
    }
    LaunchedEffect(fitKey) {
        val pts = fitTo
        if (!pts.isNullOrEmpty()) fit(mapView, pts)
    }

    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Move) onUserGesture()
                }
            }
        }
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
    }

    SideEffect { applyOverlays(context, mapView, lines, markers, circles) }
}

private fun fit(map: MapView, points: List<LatLon>) {
    val doFit = {
        if (points.size == 1) {
            map.controller.setZoom(16.0)
            map.controller.setCenter(GeoPoint(points[0].lat, points[0].lon))
        } else {
            val box = BoundingBox.fromGeoPointsSafe(points.map { GeoPoint(it.lat, it.lon) })
            map.zoomToBoundingBox(box.increaseByScale(1.25f), false, 80)
        }
    }
    if (map.width > 0 && map.height > 0) doFit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> doFit() }
}

private fun applyOverlays(
    context: Context,
    map: MapView,
    lines: List<MapLine>,
    markers: List<MapMarker>,
    circles: List<MapCircle>,
) {
    val density = context.resources.displayMetrics.density
    map.overlays.clear()
    circles.forEach { c ->
        val poly = Polygon(map)
        poly.points = Polygon.pointsAsCircle(GeoPoint(c.center.lat, c.center.lon), c.radiusM)
        poly.fillPaint.color = c.color.copy(alpha = 0.08f).toArgb()
        poly.outlinePaint.color = c.color.copy(alpha = 0.6f).toArgb()
        poly.outlinePaint.strokeWidth = 1.5f * density
        poly.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        poly.setOnClickListener { _, _, _ -> false }
        map.overlays.add(poly)
    }
    lines.filter { it.points.size >= 2 }.forEach { l ->
        // White casing under the coloured line, like the designs.
        if (!l.dashed) {
            val casing = Polyline(map)
            casing.setPoints(l.points.map { GeoPoint(it.lat, it.lon) })
            casing.outlinePaint.color = android.graphics.Color.WHITE
            casing.outlinePaint.strokeWidth = (l.widthDp + 4f) * density
            casing.outlinePaint.strokeCap = Paint.Cap.ROUND
            casing.outlinePaint.strokeJoin = Paint.Join.ROUND
            casing.setOnClickListener { _, _, _ -> false }
            map.overlays.add(casing)
        }
        val pl = Polyline(map)
        pl.setPoints(l.points.map { GeoPoint(it.lat, it.lon) })
        pl.outlinePaint.color = l.color.toArgb()
        pl.outlinePaint.strokeWidth = l.widthDp * density
        pl.outlinePaint.strokeCap = Paint.Cap.ROUND
        pl.outlinePaint.strokeJoin = Paint.Join.ROUND
        if (l.dashed) pl.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(4f * density, 8f * density), 0f)
        pl.setOnClickListener { _, _, _ -> false }
        map.overlays.add(pl)
    }
    markers.forEach { m ->
        val mk = Marker(map)
        mk.position = GeoPoint(m.position.lat, m.position.lon)
        mk.icon = ContextCompat.getDrawable(context, m.icon)
        mk.setAnchor(Marker.ANCHOR_CENTER, if (m.anchorBottom) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
        mk.isFlat = true
        mk.rotation = -m.rotation
        mk.setOnMarkerClickListener { _, _ -> true }
        map.overlays.add(mk)
    }
    map.invalidate()
}
