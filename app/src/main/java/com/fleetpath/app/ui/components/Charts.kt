package com.fleetpath.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetpath.app.analysis.Bar
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono
import com.fleetpath.app.util.decodePreview
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Small route sketch used on trip cards. */
@Composable
fun TripThumbnail(preview: String?, modifier: Modifier = Modifier) {
    val pts = remember(preview) { decodePreview(preview) }
    Canvas(modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(FP.MapBg)) {
        drawLine(Color.White, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.46f), strokeWidth = 5.dp.toPx())
        drawLine(Color.White, Offset(size.width * 0.34f, 0f), Offset(size.width * 0.46f, size.height), strokeWidth = 4.dp.toPx())
        if (pts.size < 2) return@Canvas
        val pad = 9.dp.toPx()
        val minLat = pts.minOf { it.lat }
        val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }
        val maxLon = pts.maxOf { it.lon }
        val spanLat = max(maxLat - minLat, 1e-6)
        val spanLon = max(maxLon - minLon, 1e-6)
        val scale = min((size.width - 2 * pad) / spanLon, (size.height - 2 * pad) / spanLat)
        val offX = (size.width - spanLon * scale).toFloat() / 2f
        val offY = (size.height - spanLat * scale).toFloat() / 2f
        fun x(lon: Double) = offX + ((lon - minLon) * scale).toFloat()
        fun y(lat: Double) = size.height - offY - ((lat - minLat) * scale).toFloat()
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.lon), y(p.lat)) else path.lineTo(x(p.lon), y(p.lat)) }
        drawPath(path, FP.Blue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val s = pts.first()
        drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x(s.lon), y(s.lat)))
        drawCircle(FP.Ink, radius = 4.dp.toPx(), center = Offset(x(s.lon), y(s.lat)), style = Stroke(2.dp.toPx()))
    }
}

/** Speed over the trip with the speed-limit line; [cursor] is 0..1 for replay position. */
@Composable
fun SpeedChart(speeds: List<Float>, limitKmh: Int, cursor: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (speeds.size < 2) return@Canvas
        val top = max(limitKmh * 1.2f, (speeds.maxOrNull() ?: 0f) * 1.1f)
        val w = size.width
        val h = size.height
        fun x(i: Int) = w * i / (speeds.size - 1)
        fun y(v: Float) = h - (v / top) * h
        val line = Path()
        val fill = Path()
        fill.moveTo(0f, h)
        speeds.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v))
            fill.lineTo(x(i), y(v))
        }
        fill.lineTo(w, h)
        fill.close()
        drawPath(fill, FP.Blue.copy(alpha = 0.12f))
        drawPath(line, FP.Blue, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        val ly = y(limitKmh.toFloat())
        drawLine(FP.Orange, Offset(0f, ly), Offset(w, ly), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        drawLine(FP.Field, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
        if (cursor != null) {
            val cx = w * cursor.coerceIn(0f, 1f)
            drawLine(FP.Ink, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier) {
    val maxKm = max(bars.maxOfOrNull { it.km } ?: 0.0, 1.0)
    val showValues = bars.size <= 12
    Row(
        modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 2.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                if (showValues) Text(if (b.km > 0) b.km.roundToInt().toString() else "", fontFamily = Mono, fontSize = 11.sp, color = FP.Muted, maxLines = 1)
                val frac = (b.km / maxKm).toFloat().coerceIn(0.02f, 1f)
                Box(
                    Modifier.fillMaxWidth().widthIn(max = 30.dp).height((110 * frac).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (b.highlight) FP.Blue else FP.BlueLight)
                )
                Text(b.label, fontSize = 11.sp, maxLines = 1, softWrap = false, color = if (b.highlight) FP.Ink else FP.Muted, fontWeight = if (b.highlight) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val color = when {
        score >= 80 -> FP.GreenDot
        score >= 60 -> FP.OrangeMid
        else -> FP.Orange
    }
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(FP.Line, 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
            drawArc(color, -90f, 360f * score / 100f, false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
            Text("of 100", fontSize = 11.sp, color = FP.Muted)
        }
    }
}

/** Horizontal split bar, e.g. moving / idle / parked. */
@Composable
fun SplitBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(FP.Line)) {
        parts.filter { it.first > 0f }.forEach { (w, c) -> Box(Modifier.weight(w).fillMaxHeight().background(c)) }
    }
}
