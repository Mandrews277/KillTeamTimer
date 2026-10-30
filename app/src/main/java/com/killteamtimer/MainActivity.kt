package com.killteamtimer

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import com.killteamtimer.net.LobbyPlayer
import com.killteamtimer.ui.components.ColorDot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.killteamtimer.ui.home.HomeScreen
import com.killteamtimer.ui.lobby.HostLobbyScreen
import com.killteamtimer.ui.lobby.JoinLobbyScreen
import com.killteamtimer.ui.lobby.LobbyEntryScreen
import com.killteamtimer.ui.local.LocalGameScreen
import com.killteamtimer.ui.remote.RemoteGameScreen
import com.killteamtimer.ui.setup.LocalSetupScreen
import com.killteamtimer.ui.theme.KillTeamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            KillTeamTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(viewModel())
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    when (vm.screen) {
        Screen.HOME -> HomeScreen(
            onSinglePhone = { vm.goTo(Screen.LOCAL_SETUP) },
            onMultiPhone = { vm.goTo(Screen.LOBBY_ENTRY) },
        )

        Screen.LOCAL_SETUP -> {
            BackHandler { vm.goTo(Screen.HOME) }
            LocalSetupScreen(
                names = vm.localNames,
                onNamesChange = { vm.localNames = it },
                config = vm.localConfig,
                onConfigChange = { vm.localConfig = it },
                onStart = vm::startLocalGame,
            )
        }

        Screen.LOCAL_GAME -> vm.local?.let {
            LocalGameScreen(it, onRematch = vm::startLocalGame, onExit = vm::leave)
        }

        Screen.LOBBY_ENTRY -> {
            BackHandler { vm.goTo(Screen.HOME) }
            LobbyEntryScreen(
                name = vm.playerName,
                onNameChange = { vm.playerName = it },
                linkType = vm.linkType,
                onLinkTypeChange = { vm.linkType = it },
                onHost = vm::hostGame,
                onJoin = vm::joinGame,
                onTest = vm::hostTestGame,
            )
        }

        Screen.HOST_LOBBY -> vm.host?.let { host ->
            BackHandler(onBack = vm::leave)
            HostLobbyScreen(
                host = host,
                linkType = vm.linkType,
                config = vm.hostConfig,
                onConfigChange = { vm.hostConfig = it },
                onStart = vm::startHostedGame,
                onCancel = vm::leave,
                onAddTestPlayer = if (vm.testMode) vm::addTestPlayer else null,
            )
        }

        Screen.JOIN_LOBBY -> vm.client?.let { client ->
            BackHandler(onBack = vm::leave)
            JoinLobbyScreen(client, vm.linkType, onGameStarted = vm::enterRemoteGame, onCancel = vm::leave)
        }

        Screen.REMOTE_GAME -> RemoteGame(vm)
    }
}

@Composable
private fun RemoteGame(vm: AppViewModel) {
    val host = vm.host
    val client = vm.client
    if (host != null) {
        val lobby by host.lobby.collectAsState()
        val testClient = vm.testClients.firstOrNull { it.myPlayerId.value == vm.viewingAs }
        val header: (@Composable () -> Unit)? = if (vm.testMode) {
            { TestPlayerSwitcher(lobby, vm.viewingAs, onSelect = { vm.viewingAs = it }) }
        } else {
            null
        }
        // In test mode, act as a simulated player through its own client session.
        val myId = if (testClient != null) vm.viewingAs else host.myPlayerId
        key(myId) {
            RemoteGameScreen(
                controller = testClient ?: host,
                myId = myId,
                isHost = testClient == null,
                lobby = lobby,
                status = null,
                commandPoints = vm.commandPoints(myId),
                onCommandPointsChange = { vm.setCommandPoints(myId, it) },
                onExit = vm::leave,
                teamId = vm.team(myId),
                onTeamChange = { vm.setTeam(myId, it) },
                header = header,
            )
        }
    } else if (client != null) {
        val lobby by client.lobby.collectAsState()
        val status by client.status.collectAsState()
        val myId by client.myPlayerId.collectAsState()
        val id = myId ?: return
        RemoteGameScreen(
            controller = client,
            myId = id,
            isHost = false,
            lobby = lobby,
            status = status,
            commandPoints = vm.commandPoints(id),
            onCommandPointsChange = { vm.setCommandPoints(id, it) },
            onExit = vm::leave,
            teamId = vm.team(id),
            onTeamChange = { vm.setTeam(id, it) },
        )
    }
}

/** Test mode only: pick which player this phone is acting as. */
@Composable
private fun TestPlayerSwitcher(players: List<LobbyPlayer>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("TEST MODE · acting as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            players.forEach { p ->
                FilterChip(
                    selected = p.id == selected,
                    onClick = { onSelect(p.id) },
                    label = { Text(p.name) },
                    leadingIcon = { ColorDot(p.colorIndex) },
                )
            }
        }
    }
}
