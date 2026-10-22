package com.killteamtimer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killteamtimer.engine.GameState
import com.killteamtimer.ui.theme.playerColor

@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun ColorDot(colorIndex: Int, size: Int = 14) {
    Spacer(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(playerColor(colorIndex)),
    )
}

/** "−  value  +" control. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−", fontSize = 20.sp) }
        Text(value, Modifier.width(80.dp), textAlign = TextAlign.Center, fontSize = 18.sp)
        OutlinedButton(onClick = onPlus) { Text("+", fontSize = 20.sp) }
    }
}

/** Row of mutually exclusive option buttons. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val mod = Modifier.weight(1f)
            if (value == selected) {
                Button(onClick = { onSelect(value) }, mod) { Text(label) }
            } else {
                FilledTonalButton(onClick = { onSelect(value) }, mod) { Text(label) }
            }
        }
    }
}

/** Final per-player times, longest first. */
@Composable
fun EndSummary(game: GameState, onDone: () -> Unit, extraAction: (@Composable () -> Unit)? = null) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Game over", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            game.players.sortedByDescending { it.accumulatedMs }.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(p.colorIndex)
                    Spacer(Modifier.width(12.dp))
                    Text(p.name, Modifier.weight(1f), fontSize = 18.sp)
                    Text(formatTime(p.accumulatedMs), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Total ${formatTime(game.players.sumOf { it.accumulatedMs })}",
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(32.dp))
            extraAction?.invoke()
            Button(onClick = onDone, Modifier.fillMaxWidth()) { Text("Back to home") }
        }
    }
}
