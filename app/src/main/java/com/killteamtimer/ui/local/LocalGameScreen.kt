package com.killteamtimer.ui.local

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameState
import com.killteamtimer.engine.Phase
import com.killteamtimer.engine.Player
import com.killteamtimer.engine.ResumeRule
import com.killteamtimer.session.GameController
import com.killteamtimer.ui.components.EndSummary
import com.killteamtimer.ui.components.KeepScreenOn
import com.killteamtimer.ui.components.formatTime
import com.killteamtimer.ui.components.rememberNow
import com.killteamtimer.ui.theme.OvertimeRed
import com.killteamtimer.ui.theme.onPlayerColor
import com.killteamtimer.ui.theme.playerColor

/**
 * One phone flat on the table. Each player gets a panel facing their side: panels on the far
 * side are rotated 180 degrees.
 */
@Composable
fun LocalGameScreen(controller: GameController, onRematch: () -> Unit, onExit: () -> Unit) {
    KeepScreenOn()
    val game by controller.game.collectAsState()
    val state = game ?: return
    val now = rememberNow(controller)
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler { menuOpen = true }

    if (state.phase == Phase.ENDED) {
        EndSummary(state, onDone = onExit, extraAction = {
            OutlinedButton(onClick = onRematch, Modifier.fillMaxWidth()) { Text("Rematch") }
        })
        return
    }

    val panel: @Composable (Player, Modifier) -> Unit = { player, modifier ->
        PlayerPanel(player, state, now, controller::send, modifier.padding(3.dp))
    }
    val p = state.players
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            when (p.size) {
                2 -> {
                    panel(p[1], Modifier.weight(1f).fillMaxWidth().rotate(180f))
                    panel(p[0], Modifier.weight(1f).fillMaxWidth())
                }
                3 -> {
                    Row(Modifier.weight(1f).fillMaxWidth().rotate(180f)) {
                        panel(p[2], Modifier.weight(1f).fillMaxHeight())
                        panel(p[1], Modifier.weight(1f).fillMaxHeight())
                    }
                    panel(p[0], Modifier.weight(1f).fillMaxWidth())
                }
                else -> {
                    Row(Modifier.weight(1f).fillMaxWidth().rotate(180f)) {
                        panel(p[3], Modifier.weight(1f).fillMaxHeight())
                        panel(p[2], Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        panel(p[0], Modifier.weight(1f).fillMaxHeight())
                        panel(p[1], Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        SmallFloatingActionButton(onClick = { menuOpen = true }, Modifier.align(Alignment.Center)) {
            Text("☰", fontSize = 18.sp)
        }
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("Game menu") },
            text = { Text("End the game to see everyone's total time.") },
            confirmButton = {
                Button(onClick = {
                    menuOpen = false
                    controller.send(GameAction.End(state.players.first().id))
                }) { Text("End game") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onExit) { Text("Quit") }
                    TextButton(onClick = { menuOpen = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPanel(
    player: Player,
    state: GameState,
    now: Long,
    onAction: (GameAction) -> Unit,
    modifier: Modifier,
) {
    val color = playerColor(player.colorIndex)
    val running = state.phase == Phase.RUNNING && state.activePlayerId == player.id
    val holdsClock = state.activePlayerId == player.id
    val background = if (running) color else color.copy(alpha = 0.15f).compositeOver(Color(0xFF181818))
    val foreground = if (running) onPlayerColor(player.colorIndex) else Color.White
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(if (holdsClock) 4.dp else 1.dp, color, shape),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            val timeSize = minOf(maxWidth.value / 4.5f, maxHeight.value / 3.2f, 96f).sp

            Text(
                player.name,
                Modifier.align(Alignment.TopStart),
                color = foreground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.phase == Phase.RUNNING) {
                PauseVoteButton(state, player.id, now, foreground, onAction, Modifier.align(Alignment.TopEnd))
            }

            val display = player.displayMs(now, state.config.mode)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatTime(display),
                    fontSize = timeSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (display < 0 && !running) OvertimeRed else foreground,
                )
                if (display < 0) {
                    Text("OVERTIME", color = if (running) foreground else OvertimeRed, fontWeight = FontWeight.Bold)
                }
                if (state.phase == Phase.PAUSED) {
                    Text("PAUSED", color = foreground, fontWeight = FontWeight.Bold)
                }
            }

            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                when (state.phase) {
                    Phase.NOT_STARTED -> Button(
                        onClick = { onAction(GameAction.Start(player.id)) },
                        Modifier.align(Alignment.Center),
                        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = onPlayerColor(player.colorIndex)),
                    ) { Text("I go first") }

                    Phase.RUNNING -> if (running) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        ) {
                            state.players.filter { it.id != player.id }.forEach { other ->
                                Button(
                                    onClick = { onAction(GameAction.PassTo(player.id, other.id)) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black.copy(alpha = 0.55f),
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) { Text("→ ${other.name}", maxLines = 1) }
                            }
                        }
                    }

                    Phase.PAUSED -> ResumeButton(state, player.id, now, onAction, Modifier.align(Alignment.Center))

                    Phase.ENDED -> Unit
                }
            }
        }
    }
}

@Composable
private fun PauseVoteButton(
    state: GameState,
    playerId: Int,
    now: Long,
    foreground: Color,
    onAction: (GameAction) -> Unit,
    modifier: Modifier,
) {
    val votes = state.livePauseVotes(now)
    val voted = playerId in votes
    val label = if (votes.isEmpty()) "Pause" else "Pause ${votes.size}/${state.votesNeeded}"
    OutlinedButton(
        onClick = { onAction(GameAction.VotePause(playerId)) },
        modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (voted) foreground.copy(alpha = 0.3f) else Color.Transparent,
            contentColor = foreground,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) { Text(label) }
}

@Composable
private fun ResumeButton(state: GameState, playerId: Int, now: Long, onAction: (GameAction) -> Unit, modifier: Modifier) {
    val label = if (state.config.resumeRule == ResumeRule.ANY_PLAYER) {
        "Resume"
    } else {
        val votes = state.liveResumeVotes(now)
        val mark = if (playerId in votes) "✓ " else ""
        "${mark}Vote resume ${votes.size}/${state.votesNeeded}"
    }
    Button(onClick = { onAction(GameAction.VoteResume(playerId)) }, modifier) { Text(label) }
}
