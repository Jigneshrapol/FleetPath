package com.fleetpath.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetpath.app.data.VehicleEntity
import com.fleetpath.app.ui.components.LabeledField
import com.fleetpath.app.ui.components.PrimaryButton
import com.fleetpath.app.ui.components.SectionLabel
import com.fleetpath.app.ui.rememberApp
import com.fleetpath.app.ui.theme.FP
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(onDone: () -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var plate by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var driver by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank() && plate.isNotBlank() && !saving

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeaderArt(Modifier.fillMaxWidth().height(190.dp))
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FP.Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Directions, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text("FleetPath", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FP.Ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Set up your account", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FP.Ink)
                Text(
                    "See your vehicle live, record every trip and plan better routes. Everything is stored on this phone.",
                    fontSize = 15.sp, lineHeight = 21.sp, color = FP.Muted,
                )
            }
            LabeledField("Your name", name, { name = it }, placeholder = "e.g. Amit Desai", capitalization = KeyboardCapitalization.Words)
            LabeledField("Mobile number", phone, { phone = it }, placeholder = "+91 98xxx xxxxx", keyboardType = KeyboardType.Phone)
            SectionLabel("Your first vehicle", Modifier.padding(top = 4.dp))
            LabeledField("Number plate", plate, { plate = it }, placeholder = "GJ 15 AB 4821", capitalization = KeyboardCapitalization.Characters)
            LabeledField("Make and model", model, { model = it }, placeholder = "e.g. Tata Ace Gold", capitalization = KeyboardCapitalization.Words)
            LabeledField("Driver name (optional)", driver, { driver = it }, capitalization = KeyboardCapitalization.Words)
            PrimaryButton(
                "Get started",
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true
                    scope.launch {
                        val id = app.db.vehicles().insert(
                            VehicleEntity(plate = plate.trim().uppercase(), model = model.trim(), driver = driver.trim())
                        )
                        app.settings.update {
                            it.copy(userName = name.trim(), phone = phone.trim(), signedIn = true, activeVehicleId = id)
                        }
                        onDone()
                    }
                },
            )
            Text("You can add more vehicles later in Settings.", fontSize = 12.sp, color = FP.Subtle)
        }
    }
}

@Composable
private fun HeaderArt(modifier: Modifier) {
    Canvas(modifier.background(FP.MapBg)) {
        val w = size.width
        val h = size.height
        val water = Path().apply {
            moveTo(0f, h * 0.8f)
            cubicTo(w * 0.2f, h * 0.68f, w * 0.35f, h * 0.92f, w * 0.56f, h * 0.78f)
            cubicTo(w * 0.75f, h * 0.65f, w * 0.9f, h * 0.7f, w, h * 0.7f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(water, Color(0xFFD3E3F0))
        drawRoundRect(Color(0xFFDCEADC), topLeft = Offset(w * 0.67f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.26f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f))
        drawLine(Color(0xFFE3C67A), Offset(0f, h * 0.58f), Offset(w, h * 0.4f), strokeWidth = 16.dp.toPx())
        drawLine(Color(0xFFF5DFA3), Offset(0f, h * 0.58f), Offset(w, h * 0.4f), strokeWidth = 11.dp.toPx())
        drawLine(Color.White, Offset(w * 0.33f, 0f), Offset(w * 0.38f, h), strokeWidth = 7.dp.toPx())
        drawLine(Color.White, Offset(w * 0.73f, 0f), Offset(w * 0.65f, h), strokeWidth = 6.dp.toPx())
        val route = Path().apply {
            moveTo(w * 0.14f, h * 0.72f)
            cubicTo(w * 0.3f, h * 0.6f, w * 0.4f, h * 0.55f, w * 0.5f, h * 0.5f)
            cubicTo(w * 0.6f, h * 0.45f, w * 0.65f, h * 0.35f, w * 0.72f, h * 0.3f)
        }
        drawPath(route, Color.White, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(route, FP.Blue, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 7.dp.toPx(), Offset(w * 0.14f, h * 0.72f))
        drawCircle(FP.Ink, 7.dp.toPx(), Offset(w * 0.14f, h * 0.72f), style = Stroke(3.dp.toPx()))
        drawCircle(FP.Blue, 9.dp.toPx(), Offset(w * 0.72f, h * 0.3f))
        drawCircle(Color.White, 3.5.dp.toPx(), Offset(w * 0.72f, h * 0.3f))
    }
}
