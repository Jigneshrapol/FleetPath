package com.fleetpath.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.fleetpath.app.data.PointEntity
import com.fleetpath.app.data.TripEntity
import com.fleetpath.app.data.durationMs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Exporter {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val local = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun file(context: Context, name: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    private fun uri(context: Context, f: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun gpx(context: Context, trip: TripEntity, points: List<PointEntity>): Uri {
        val f = file(context, "fleetpath_trip_${trip.id}.gpx")
        f.writeText(buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<gpx version="1.1" creator="FleetPath" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
            append("<trk><name>").append(esc("${trip.startAddress ?: "Start"} to ${trip.endAddress ?: "End"}")).append("</name><trkseg>\n")
            for (p in points) {
                append(String.format(Locale.US, "<trkpt lat=\"%.6f\" lon=\"%.6f\"><time>%s</time><speed>%.2f</speed></trkpt>\n",
                    p.lat, p.lon, iso.format(Date(p.time)), p.speedKmh / 3.6f))
            }
            append("</trkseg></trk></gpx>\n")
        })
        return uri(context, f)
    }

    fun tripsCsv(context: Context, trips: List<TripEntity>): Uri {
        val f = file(context, "fleetpath_trips.csv")
        f.writeText(buildString {
            append("id,start,end,from,to,distance_km,duration_min,avg_kmh,max_kmh,idle_min,overspeed,harsh_braking,score,auto\n")
            for (t in trips) {
                val row = listOf(
                    t.id.toString(),
                    local.format(Date(t.startTime)),
                    t.endTime?.let { local.format(Date(it)) } ?: "",
                    "\"${(t.startAddress ?: "").replace("\"", "'")}\"",
                    "\"${(t.endAddress ?: "").replace("\"", "'")}\"",
                    String.format(Locale.US, "%.2f", t.distanceM / 1000),
                    (t.durationMs() / 60_000).toString(),
                    String.format(Locale.US, "%.1f", t.avgSpeedKmh),
                    String.format(Locale.US, "%.1f", t.maxSpeedKmh),
                    (t.idleMs / 60_000).toString(),
                    t.overspeedCount.toString(),
                    t.harshBrakeCount.toString(),
                    t.score.toString(),
                    t.autoDetected.toString(),
                )
                append(row.joinToString(",")).append('\n')
            }
        })
        return uri(context, f)
    }

    fun share(context: Context, uri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title))
    }

    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title))
    }
}
