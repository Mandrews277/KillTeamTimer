package com.killteamtimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.killteamtimer.engine.GameConfig
import com.killteamtimer.engine.ResumeRule
import com.killteamtimer.engine.TimerMode

private const val MINUTE = 60_000L

/** Session options shared by single-phone setup and the multi-phone host lobby. */
@Composable
fun GameOptionsForm(config: GameConfig, playerCount: Int, onChange: (GameConfig) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Timer", style = MaterialTheme.typography.titleMedium)
        val countDown = config.mode as? TimerMode.CountDown
        Segmented(
            options = listOf(false to "Count up", true to "Count down"),
            selected = countDown != null,
            onSelect = { down ->
                onChange(config.copy(mode = if (down) TimerMode.CountDown(30 * MINUTE) else TimerMode.CountUp))
            },
        )
        if (countDown != null) {
            val minutes = countDown.limitMs / MINUTE
            Stepper(
                label = "Time per player",
                value = "$minutes min",
                onMinus = { onChange(config.copy(mode = TimerMode.CountDown((minutes - 5).coerceAtLeast(5) * MINUTE))) },
                onPlus = { onChange(config.copy(mode = TimerMode.CountDown((minutes + 5).coerceAtMost(600) * MINUTE))) },
            )
        }

        Text("Pausing", style = MaterialTheme.typography.titleMedium)
        val maxVotes = playerCount.coerceAtLeast(1)
        val votes = config.votesToPause.coerceIn(1, maxVotes)
        Stepper(
            label = "Votes to pause",
            value = "$votes / $maxVotes",
            onMinus = { onChange(config.copy(votesToPause = (votes - 1).coerceAtLeast(1))) },
            onPlus = { onChange(config.copy(votesToPause = (votes + 1).coerceAtMost(maxVotes))) },
        )
        Text("Who can resume", style = MaterialTheme.typography.bodyMedium)
        Segmented(
            options = listOf(ResumeRule.ANY_PLAYER to "Anyone", ResumeRule.VOTES to "Same votes"),
            selected = config.resumeRule,
            onSelect = { onChange(config.copy(resumeRule = it)) },
        )
    }
}
