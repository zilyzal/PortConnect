package com.example.portconnect

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ground = Color(0xFF070B1A)
internal val Panel = Color(0xFF141C3A)
internal val Line = Color(0xFF26315A)
internal val Ink = Color(0xFFEEF3FF)
internal val Dim = Color(0xBDE8EEFF)
internal val Accent = Color(0xFF5B86FF)
internal val Bad = Color(0xFFFF8A8A)

/** Tile colours, handed out to services in order. */
internal val TileColors = listOf(
    Color(0xFF7FA6FF),
    Color(0xFFB69CFF),
    Color(0xFF55D6C2),
    Color(0xFFFFC65C),
    Color(0xFFFF8BA7),
    Color(0xFF8BD17C),
    Color(0xFFFF9F6B),
    Color(0xFF6CCBFF),
)

@Composable
fun PortConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Ground,
            surface = Panel,
            onBackground = Ink,
            onSurface = Ink,
        ),
        content = content
    )
}
