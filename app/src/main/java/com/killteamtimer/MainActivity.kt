package com.killteamtimer

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
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
        RemoteGameScreen(
            controller = host,
            myId = host.myPlayerId,
            isHost = true,
            lobby = lobby,
            status = null,
            commandPoints = vm.commandPoints,
            onCommandPointsChange = { vm.commandPoints = it },
            onExit = vm::leave,
        )
    } else if (client != null) {
        val lobby by client.lobby.collectAsState()
        val status by client.status.collectAsState()
        val myId by client.myPlayerId.collectAsState()
        RemoteGameScreen(
            controller = client,
            myId = myId ?: return,
            isHost = false,
            lobby = lobby,
            status = status,
            commandPoints = vm.commandPoints,
            onCommandPointsChange = { vm.commandPoints = it },
            onExit = vm::leave,
        )
    }
}
