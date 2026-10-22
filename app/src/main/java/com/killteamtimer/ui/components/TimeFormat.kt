package com.killteamtimer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.killteamtimer.session.GameController
import kotlinx.coroutines.delay
import kotlin.math.abs

/** "M:SS" or "H:MM:SS", with a leading "-" for overtime. */
fun formatTime(ms: Long): String {
    val totalSeconds = abs(ms) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val sign = if (ms < 0 && totalSeconds > 0) "-" else ""
    return if (h > 0) "%s%d:%02d:%02d".format(sign, h, m, s) else "%s%d:%02d".format(sign, m, s)
}

/** The controller's current time, refreshed ten times a second for clock displays. */
@Composable
fun rememberNow(controller: GameController): Long {
    val now by produceState(controller.now(), controller) {
        while (true) {
            value = controller.now()
            delay(100)
        }
    }
    return now
}
