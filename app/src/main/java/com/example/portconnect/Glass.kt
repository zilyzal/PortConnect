package com.example.portconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared "glass" pieces. Android can't blur what is behind a view without extra libraries,
// so the glass is a translucent fill, a light sheen and a bright edge over a soft wallpaper.

internal val FieldHeight = 48.dp
internal val PanelShape = RoundedCornerShape(24.dp)

/** A diagonal highlight across the top-left of every glass surface. */
internal val Sheen = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.20f),
    0.42f to Color.Transparent,
)

private val PanelBase = Color.White.copy(alpha = 0.07f)

private val GlassEdge = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.42f),
    0.5f to Color.White.copy(alpha = 0.10f),
    1f to Color.White.copy(alpha = 0.18f),
)

private val ButtonOn = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.26f),
    1f to Color.White.copy(alpha = 0.10f),
)
private val ButtonOff = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.07f),
    1f to Color.White.copy(alpha = 0.07f),
)

private val WallBlue = Color(0xFF3A66FF)
private val WallViolet = Color(0xFF8B5CFF)
private val WallTeal = Color(0xFF12C9AE)
private val WallAmber = Color(0xFFFFAE2E)
private val WallRose = Color(0xFFFF4F86)

/** A frosted panel: faint white fill, sheen and a light edge. */
internal fun Modifier.glassPanel(shape: Shape = PanelShape): Modifier = this
    .clip(shape)
    .background(PanelBase)
    .background(Sheen)
    .border(1.dp, GlassEdge, shape)

/** The colourful backdrop that the glass sits on. It stays put while the list scrolls. */
internal fun Modifier.wallpaper(): Modifier = drawBehind {
    fun blob(color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
        val center = Offset(size.width * x, size.height * y)
        val r = size.width * radius
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                0.45f to color.copy(alpha = alpha * 0.55f),
                1f to Color.Transparent,
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
    }
    blob(WallBlue, 0.11f, 0.20f, 0.62f, 0.85f)
    blob(WallViolet, 0.93f, 0.47f, 0.60f, 0.80f)
    blob(WallTeal, 0.10f, 0.74f, 0.60f, 0.80f)
    blob(WallAmber, 0.90f, 0.94f, 0.55f, 0.70f)
    blob(WallRose, 0.47f, 1.00f, 0.50f, 0.60f)
}

/** A see-through button. It is brighter when it can be tapped and dimmer when it can't. */
@Composable
internal fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(FieldHeight)
            .clip(shape)
            .background(if (enabled) ButtonOn else ButtonOff)
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.40f else 0.12f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Ink else Ink.copy(alpha = 0.5f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
