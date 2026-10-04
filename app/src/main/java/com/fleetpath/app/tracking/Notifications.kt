package com.fleetpath.app.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fleetpath.app.MainActivity
import com.fleetpath.app.R
import com.fleetpath.app.util.fmtClock
import com.fleetpath.app.util.fmtKm
import com.fleetpath.app.util.hasNotificationPermission
import kotlin.math.roundToInt

object Notifications {
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_ALERTS = "alerts"
    const val EXTRA_TRIP_ID = "open_trip_id"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "Live tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while FleetPath records your location"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Vehicle alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Over-speed, geofence and trip alerts"
            }
        )
    }

    private fun openApp(context: Context, tripId: Long?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (tripId != null) intent.putExtra(EXTRA_TRIP_ID, tripId)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun tracking(context: Context, trip: ActiveTrip?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(context, null, 0))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (trip != null) {
            val stop = PendingIntent.getService(
                context, 1,
                Intent(context, LocationService::class.java).setAction(LocationService.ACTION_STOP_TRIP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentTitle("Recording trip · ${fmtKm(trip.distanceM)}")
                .setContentText("Started ${fmtClock(trip.startTime)} · max ${trip.maxKmh.roundToInt()} km/h")
                .addAction(0, "Stop & save", stop)
        } else {
            val off = PendingIntent.getService(
                context, 2,
                Intent(context, LocationService::class.java).setAction(LocationService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentTitle("Watching for trips")
                .setContentText("A trip starts recording automatically when you drive.")
                .addAction(0, "Turn off", off)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun showAlert(context: Context, id: Int, title: String, text: String, tripId: Long?) {
        if (!context.hasNotificationPermission()) return
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, tripId, 1000 + id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1000 + id, n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
