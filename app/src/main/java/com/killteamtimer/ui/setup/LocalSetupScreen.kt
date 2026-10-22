package com.killteamtimer.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.killteamtimer.engine.GameConfig
import com.killteamtimer.ui.components.ColorDot
import com.killteamtimer.ui.components.GameOptionsForm
import com.killteamtimer.ui.components.Segmented

@Composable
fun LocalSetupScreen(
    names: List<String>,
    onNamesChange: (List<String>) -> Unit,
    config: GameConfig,
    onConfigChange: (GameConfig) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("One-phone game", style = MaterialTheme.typography.headlineSmall)
        Text("Players", style = MaterialTheme.typography.titleMedium)
        Segmented(
            options = listOf(2 to "2", 3 to "3", 4 to "4"),
            selected = names.size,
            onSelect = { count ->
                onNamesChange(List(count) { i -> names.getOrNull(i) ?: "Player ${i + 1}" })
            },
        )
        names.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(i, size = 20)
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { v -> onNamesChange(names.toMutableList().also { it[i] = v.take(20) }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GameOptionsForm(config, names.size, onConfigChange)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart, Modifier.fillMaxWidth().height(56.dp)) { Text("Start") }
    }
}
