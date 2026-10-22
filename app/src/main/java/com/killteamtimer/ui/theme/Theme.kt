package com.killteamtimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Seat colours; player colorIndex wraps around this list. */
val PlayerColors = listOf(
    Color(0xFFE53935), // red
    Color(0xFF1E88E5), // blue
    Color(0xFF43A047), // green
    Color(0xFFFDD835), // yellow
    Color(0xFF8E24AA), // purple
    Color(0xFFFB8C00), // orange
    Color(0xFF00ACC1), // teal
    Color(0xFFD81B60), // pink
)

fun playerColor(index: Int): Color = PlayerColors[Math.floorMod(index, PlayerColors.size)]

/** Readable text colour on top of a player colour. */
fun onPlayerColor(index: Int): Color =
    if (Math.floorMod(index, PlayerColors.size) == 3) Color.Black else Color.White

val OvertimeRed = Color(0xFFFF5252)

private val Scheme = darkColorScheme(
    primary = Color(0xFFFF7043),
    onPrimary = Color.Black,
    secondary = Color(0xFFB0BEC5),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF2A2A2A),
)

@Composable
fun KillTeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
