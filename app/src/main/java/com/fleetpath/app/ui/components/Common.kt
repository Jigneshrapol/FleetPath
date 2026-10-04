@file:OptIn(ExperimentalMaterial3Api::class)

package com.fleetpath.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleetpath.app.ui.theme.FP
import com.fleetpath.app.ui.theme.Mono

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = if (onBack != null) 18.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                color = FP.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = FP.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    tint: Color = FP.Ink,
    elevated: Boolean = false,
) {
    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = if (elevated) null else BorderStroke(1.dp, FP.Line),
            shadowElevation = if (elevated) 4.dp else 0.dp,
            modifier = Modifier.size(48.dp).semantics { this.contentDescription = contentDescription },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape).background(FP.Orange).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (badge > 9) "9+" else "$badge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) FP.Ink else Color.White,
        border = if (selected) null else BorderStroke(1.dp, FP.Field),
        modifier = modifier.heightIn(min = 36.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) Color.White else FP.Ink2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FP.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) FP.Ink else FP.Muted)
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String = "") {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(FP.Surface2).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = FP.Muted, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", fontSize = 13.sp, color = FP.Muted, modifier = Modifier.padding(bottom = 2.dp), maxLines = 1)
        }
    }
}

/** Three bordered cells in a row, e.g. Trips / Distance / Drive time. */
@Composable
fun SummaryStrip(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().border(1.dp, FP.Line, RoundedCornerShape(14.dp))) {
        items.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.width(1.dp).height(58.dp).background(FP.Line))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, fontSize = 12.sp, color = FP.Muted, maxLines = 1)
                Text(value, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink, maxLines = 1)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = FP.Muted)
}

@Composable
fun StatusPill(text: String, background: Color, foreground: Color, dot: Color? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = FP.Blue,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FP.Field),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = FP.Ink),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconTile(icon: ImageVector, background: Color = FP.Surface2, tint: Color = FP.Ink, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size / 2).dp)) }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, size = 56)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = FP.Ink)
        Text(text, fontSize = 14.sp, color = FP.Muted, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun BorderedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, FP.Line, RoundedCornerShape(16.dp)).background(Color.White)) {
        content()
    }
}
