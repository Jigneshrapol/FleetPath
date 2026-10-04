package com.fleetpath.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fleetpath.app.ui.components.IconTile
import com.fleetpath.app.ui.components.PrimaryButton
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.util.hasBackgroundLocation
import com.fleetpath.app.util.hasLocationPermission
import com.fleetpath.app.util.hasNotificationPermission
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val location = remember(refresh) { context.hasLocationPermission() }
    val background = remember(refresh) { context.hasBackgroundLocation() }
    val notifications = remember(refresh) { context.hasNotificationPermission() }
    val battery = remember(refresh) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) openAppSettings(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(FP.MapBg), contentAlignment = Alignment.Center) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(FP.Blue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(FP.Blue.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = FP.Blue, modifier = Modifier.size(28.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Allow tracking access", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FP.Ink)
            Text(
                "FleetPath records trips even with the screen off. Android needs these permissions for that to work.",
                fontSize = 15.sp, lineHeight = 21.sp, color = FP.Muted,
            )
        }
        Column {
            PermissionRow(
                Icons.Outlined.LocationOn, "Precise location", "Show your vehicle's exact position on the map.",
                granted = location, actionLabel = "Allow",
                onAction = {
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
            )
            HorizontalDivider(color = FP.Line)
            PermissionRow(
                Icons.Outlined.Schedule, "Location all the time",
                if (location) "Needed to auto-detect and record trips in the background. Choose \"Allow all the time\"."
                else "Allow precise location first.",
                granted = background, actionLabel = "Allow", enabled = location,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
            )
            HorizontalDivider(color = FP.Line)
            PermissionRow(
                Icons.Outlined.Notifications, "Notifications", "Over-speed, geofence and trip alerts.",
                granted = notifications, actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            HorizontalDivider(color = FP.Line)
            PermissionRow(
                Icons.Outlined.BatteryStd, "Unrestricted battery", "Stops Android from pausing tracking to save power.",
                granted = battery, actionLabel = "Open",
                onAction = { requestBatteryExemption(context) },
            )
        }
        Text(
            "While tracking, a small notification stays visible. You can turn tracking off any time in Settings.",
            fontSize = 12.sp, lineHeight = 18.sp, color = FP.Subtle,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            if (location) "Continue" else "Continue without location",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    app.settings.update { it.copy(onboarded = true) }
                    onContinue()
                }
            },
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = FP.Muted)
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = FP.Green, modifier = Modifier.size(18.dp))
                Text("Allowed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FP.Green)
            }
        } else {
            Button(
                onClick = onAction,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FP.BlueSoft, contentColor = FP.Blue),
                modifier = Modifier.heightIn(min = 44.dp),
            ) { Text(actionLabel, fontWeight = FontWeight.SemiBold) }
        }
    }
}

fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    } catch (e: Exception) {
        // Settings app unavailable.
    }
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        )
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e2: Exception) {
            // Not available on this device.
        }
    }
}
