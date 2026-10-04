package com.fleetpath.app.ui.components

import android.annotation.SuppressLint
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.util.hasLocationPermission
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink2)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = FP.Subtle) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = FP.Field,
                focusedBorderColor = FP.Blue,
                focusedTextColor = FP.Ink,
                unfocusedTextColor = FP.Ink,
            ),
        )
    }
}

/**
 * Feeds GPS fixes into the tracking engine while this screen is visible and the
 * background service isn't already doing it.
 */
@SuppressLint("MissingPermission")
@Composable
fun ForegroundLocationUpdates() {
    val app = rememberApp()
    val context = LocalContext.current
    val serviceRunning by app.tracking.serviceRunning.collectAsStateWithLifecycle()
    LifecycleResumeEffect(serviceRunning) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { app.tracking.onLocation(it) }
            }
        }
        val active = !serviceRunning && context.hasLocationPermission()
        if (active) {
            try {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && app.tracking.live.value == null) app.tracking.onLocation(loc)
                }
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L).build()
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                // Permission was revoked; the screen shows a prompt instead.
            }
        }
        onPauseOrDispose {
            if (active) client.removeLocationUpdates(callback)
        }
    }
}
