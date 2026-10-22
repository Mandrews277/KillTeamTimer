package com.killteamtimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Private command point tracker; never shared with other phones. */
@Composable
fun CpCounter(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Command points", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, Modifier.size(72.dp)) {
                    Text("−", fontSize = 28.sp)
                }
                Text("$value", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { onChange(value + 1) }, Modifier.size(72.dp)) {
                    Text("+", fontSize = 28.sp)
                }
            }
        }
    }
}
