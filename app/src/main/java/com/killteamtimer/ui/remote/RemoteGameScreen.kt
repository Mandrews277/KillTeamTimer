package com.killteamtimer.ui.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameState
import com.killteamtimer.engine.Phase
import com.killteamtimer.engine.ResumeRule
import com.killteamtimer.net.LobbyPlayer
import com.killteamtimer.session.ClientStatus
import com.killteamtimer.session.GameController
import com.killteamtimer.ui.components.ColorDot
import com.killteamtimer.ui.components.CpCounter
import com.killteamtimer.ui.components.EndSummary
import com.killteamtimer.ui.components.KeepScreenOn
import com.killteamtimer.ui.components.formatTime
import com.killteamtimer.ui.components.rememberNow
import com.killteamtimer.ui.theme.OvertimeRed
import com.killteamtimer.ui.theme.onPlayerColor
import com.killteamtimer.ui.theme.playerColor

/**
 * One player's own phone in a multi-phone game.
 * [status] is null on the host, which can't lose its own connection.
 */
@Composable
fun RemoteGameScreen(
    controller: GameController,
    myId: Int,
    isHost: Boolean,
    lobby: List<LobbyPlayer>,
    status: ClientStatus?,
    commandPoints: Int,
    onCommandPointsChange: (Int) -> Unit,
    onExit: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    KeepScreenOn()
    val game by controller.game.collectAsState()
    val state = game ?: return
    val now = rememberNow(controller)
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler { confirmLeave = true }
    VibrateWhenPassedTo(state, myId)

    if (state.phase == Phase.ENDED) {
        EndSummary(state, onDone = onExit)
        return
    }
    val canPass = state.activePlayerId == myId && state.phase == Phase.RUNNING

    val me = state.player(myId) ?: return
    val myTurn = state.activePlayerId == myId
    val running = state.phase == Phase.RUNNING
    val disconnected = lobby.filterNot { it.connected }.map { it.id }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header?.invoke()
        if (status == ClientStatus.HostLost) {
            Text(
                "Lost connection to host. Reconnecting…",
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        // My clock
        val color = playerColor(me.colorIndex)
        val highlight = myTurn && running
        val fg = if (highlight) onPlayerColor(me.colorIndex) else Color.White
        val display = me.displayMs(now, state.config.mode)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (highlight) color else color.copy(alpha = 0.15f).compositeOver(Color(0xFF181818)))
                .border(2.dp, color, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(me.name, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(
                formatTime(display),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (display < 0 && !highlight) OvertimeRed else fg,
            )
            val caption = when {
                state.phase == Phase.PAUSED -> "PAUSED"
                state.phase == Phase.NOT_STARTED -> "Waiting for someone to start"
                display < 0 && myTurn -> "YOUR TURN · OVERTIME"
                display < 0 -> "OVERTIME"
                myTurn -> "YOUR TURN"
                else -> "Waiting on ${state.activePlayerId?.let { state.player(it)?.name } ?: "…"}"
            }
            Text(caption, color = fg, fontWeight = FontWeight.Bold)
        }

        when (state.phase) {
            Phase.NOT_STARTED -> Button(
                onClick = { controller.send(GameAction.Start(myId)) },
                Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Start — I go first") }

            Phase.RUNNING -> {
                val votes = state.livePauseVotes(now)
                val voted = myId in votes
                OutlinedButton(
                    onClick = { controller.send(GameAction.VotePause(myId)) },
                    Modifier.fillMaxWidth(),
                    colors = if (voted) {
                        ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) {
                    Text(
                        (if (voted) "✓ Voted to pause" else "Vote to pause") +
                            " (${votes.size}/${state.votesNeeded})",
                    )
                }
            }

            Phase.PAUSED -> {
                val label = if (state.config.resumeRule == ResumeRule.ANY_PLAYER) {
                    "Resume"
                } else {
                    val votes = state.liveResumeVotes(now)
                    (if (myId in votes) "✓ Voted to resume" else "Vote to resume") +
                        " (${votes.size}/${state.votesNeeded})"
                }
                Button(
                    onClick = { controller.send(GameAction.VoteResume(myId)) },
                    Modifier.fillMaxWidth().height(56.dp),
                ) { Text(label) }
            }

            Phase.ENDED -> Unit
        }

        if (canPass) {
            PassGrid(state) { to -> controller.send(GameAction.PassTo(myId, to)) }
        }

        PlayerList(state, now, myId, disconnected)

        CpCounter(commandPoints, onCommandPointsChange)

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { confirmLeave = true }, Modifier.fillMaxWidth()) {
            Text(if (isHost) "End game / leave" else "Leave game")
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (isHost) "End the game?" else "Leave the game?") },
            text = {
                Text(
                    if (isHost) {
                        "You are the host. Ending shows everyone the final times. Leaving without ending disconnects everyone."
                    } else {
                        "You can rejoin from the lobby as long as the host keeps the game open."
                    },
                )
            },
            confirmButton = {
                if (isHost) {
                    Button(onClick = {
                        confirmLeave = false
                        controller.send(GameAction.End(myId))
                    }) { Text("End game") }
                } else {
                    Button(onClick = onExit) { Text("Leave") }
                }
            },
            dismissButton = {
                Row {
                    if (isHost) TextButton(onClick = onExit) { Text("Quit") }
                    TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun PlayerList(
    state: GameState,
    now: Long,
    myId: Int,
    disconnected: Set<Int>,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.players.forEach { p ->
                val active = p.id == state.activePlayerId
                val display = p.displayMs(now, state.config.mode)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) playerColor(p.colorIndex).copy(alpha = 0.25f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(p.colorIndex)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name + if (p.id == myId) " (you)" else "",
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (p.id in disconnected) {
                            Text("disconnected", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        formatTime(display),
                        fontFamily = FontFamily.Monospace,
                        color = if (display < 0) OvertimeRed else Color.Unspecified,
                    )
                }
            }
        }
    }
}

/** Big pass targets, two per row, each labelled with the player it passes to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassGrid(state: GameState, onPass: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pass the clock to…", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            state.players.filter { it.id != state.activePlayerId }.forEach { p ->
                Button(
                    onClick = { onPass(p.id) },
                    Modifier.weight(1f).height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = playerColor(p.colorIndex),
                        contentColor = onPlayerColor(p.colorIndex),
                    ),
                ) {
                    Text(
                        p.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Buzzes when the clock arrives at this player. */
@Composable
private fun VibrateWhenPassedTo(state: GameState, myId: Int) {
    val context = LocalContext.current
    val mineAndRunning = state.activePlayerId == myId && state.phase == Phase.RUNNING
    LaunchedEffect(mineAndRunning) {
        if (mineAndRunning) vibrate(context)
    }
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
}
