package com.fleetpath.app.routing

import com.fleetpath.app.util.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double) {
    val latLon get() = LatLon(lat, lon)
}

data class RouteOption(val distanceM: Double, val durationS: Double, val summary: String, val points: List<LatLon>)

/**
 * Free OpenStreetMap services, no API key needed:
 *  - Nominatim for place search (max 1 request/second)
 *  - OSRM public demo server for driving routes
 * Both are fine for testing; use your own servers or a paid provider for production.
 */
object RoutingClient {
    private const val USER_AGENT = "FleetPath-Android/1.0 (personal testing build)"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Accept", "application/json")
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("Server returned $code")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun search(query: String, near: LatLon?): List<Place> {
        val q = URLEncoder.encode(query, "UTF-8")
        var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=$q"
        if (near != null) {
            url += "&viewbox=${near.lon - 1.5},${near.lat + 1.5},${near.lon + 1.5},${near.lat - 1.5}"
        }
        val arr = JSONArray(get(url))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val parts = o.optString("display_name").split(",").map { it.trim() }
            Place(
                name = parts.take(2).joinToString(", "),
                detail = parts.drop(2).take(3).joinToString(", "),
                lat = o.getString("lat").toDouble(),
                lon = o.getString("lon").toDouble(),
            )
        }
    }

    suspend fun routes(from: LatLon, to: LatLon, avoidTolls: Boolean): List<RouteOption> {
        val base = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.lon},${from.lat};${to.lon},${to.lat}?alternatives=true&overview=full&geometries=geojson"
        val json = JSONObject(get(if (avoidTolls) "$base&exclude=toll" else base))
        if (json.optString("code") != "Ok") throw IOException(json.optString("message", "No route found"))
        val routes = json.getJSONArray("routes")
        return (0 until routes.length()).map { i ->
            val r = routes.getJSONObject(i)
            val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
            val pts = (0 until coords.length()).map { j ->
                val c = coords.getJSONArray(j)
                LatLon(c.getDouble(1), c.getDouble(0))
            }
            val legs = r.getJSONArray("legs")
            val summary = if (legs.length() > 0) legs.getJSONObject(0).optString("summary") else ""
            RouteOption(r.getDouble("distance"), r.getDouble("duration"), summary, pts)
        }
    }
}
