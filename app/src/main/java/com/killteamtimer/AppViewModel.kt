package com.killteamtimer

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.killteamtimer.engine.Clock
import com.killteamtimer.engine.GameConfig
import com.killteamtimer.engine.GameEngine
import com.killteamtimer.engine.Player
import com.killteamtimer.net.LanTransport
import com.killteamtimer.net.NearbyTransport
import com.killteamtimer.session.ClientSession
import com.killteamtimer.session.HostSession
import com.killteamtimer.session.LocalGameController
import java.util.UUID

enum class Screen { HOME, LOCAL_SETUP, LOCAL_GAME, LOBBY_ENTRY, HOST_LOBBY, JOIN_LOBBY, REMOTE_GAME }

enum class LinkType { NEARBY, WIFI }

const val STARTING_CP = 2

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("ktt", Context.MODE_PRIVATE)
    private val clock = Clock { SystemClock.elapsedRealtime() }

    /** Stable per-install id so the host can give a reconnecting phone its old seat. */
    val deviceId: String = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString("deviceId", it) }
    }

    var screen by mutableStateOf(Screen.HOME)
        private set

    var playerName by mutableStateOf(prefs.getString("name", null) ?: "")

    var linkType by mutableStateOf(LinkType.NEARBY)

    var local by mutableStateOf<LocalGameController?>(null)
        private set
    var host by mutableStateOf<HostSession?>(null)
        private set
    var client by mutableStateOf<ClientSession?>(null)
        private set

    /** This phone's own command points (multi-phone mode only). */
    var commandPoints by mutableIntStateOf(STARTING_CP)

    var localNames by mutableStateOf(listOf("Player 1", "Player 2"))
    var localConfig by mutableStateOf(GameConfig())
    var hostConfig by mutableStateOf(GameConfig())

    fun goTo(target: Screen) {
        screen = target
    }

    fun startLocalGame() {
        val players = localNames.mapIndexed { i, name -> Player(i, name.ifBlank { "Player ${i + 1}" }, i) }
        local = LocalGameController(GameEngine.newGame(players, localConfig), clock)
        screen = Screen.LOCAL_GAME
    }

    fun hostGame() {
        saveName()
        host = HostSession(newTransport(), playerName, clock, viewModelScope)
        screen = Screen.HOST_LOBBY
    }

    fun joinGame() {
        saveName()
        client = ClientSession(newTransport(), playerName, deviceId, clock, viewModelScope)
        screen = Screen.JOIN_LOBBY
    }

    fun startHostedGame() {
        host?.startGame(hostConfig)
        enterRemoteGame()
    }

    fun enterRemoteGame() {
        commandPoints = STARTING_CP
        screen = Screen.REMOTE_GAME
    }

    /** Tears down any session and returns to the home screen. */
    fun leave() {
        closeSessions()
        local = null
        screen = Screen.HOME
    }

    override fun onCleared() = closeSessions()

    private fun closeSessions() {
        host?.close()
        client?.close()
        host = null
        client = null
    }

    private fun saveName() {
        playerName = playerName.trim().ifBlank { "Player" }
        prefs.edit { putString("name", playerName) }
    }

    private fun newTransport() = when (linkType) {
        LinkType.NEARBY -> NearbyTransport(getApplication())
        LinkType.WIFI -> LanTransport(getApplication())
    }
}
