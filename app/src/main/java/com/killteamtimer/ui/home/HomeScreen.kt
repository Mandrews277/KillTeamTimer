package com.killteamtimer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onSinglePhone: () -> Unit, onMultiPhone: () -> Unit, onCoverCheck: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("KILL TEAM", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
        Text("TIMER", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onSinglePhone, Modifier.fillMaxWidth().height(64.dp)) {
            Text("One phone (2–4 players)")
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onMultiPhone, Modifier.fillMaxWidth().height(64.dp)) {
            Text("Phone per player")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCoverCheck, Modifier.fillMaxWidth().height(56.dp)) {
            Text("Cover & obscuring check")
        }
    }
}
