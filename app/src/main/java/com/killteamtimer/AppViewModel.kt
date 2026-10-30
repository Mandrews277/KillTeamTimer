package com.killteamtimer

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.killteamtimer.net.LoopbackNetwork
import com.killteamtimer.net.NearbyTransport
import com.killteamtimer.session.ClientSession
import com.killteamtimer.session.HostEndpoint
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

    /** Command points per player id. Only this phone's own player is shown, except in test mode. */
    private val commandPoints = mutableStateMapOf<Int, Int>()

    fun commandPoints(playerId: Int): Int = commandPoints[playerId] ?: STARTING_CP

    fun setCommandPoints(playerId: Int, value: Int) {
        commandPoints[playerId] = value
    }

    /** Each player's kill team for the rules helper; the last choice is remembered for next time. */
    private val teams = mutableStateMapOf<Int, String>()

    fun team(playerId: Int): String? = teams[playerId] ?: prefs.getString("team", null)

    fun setTeam(playerId: Int, teamId: String) {
        teams[playerId] = teamId
        prefs.edit { putString("team", teamId) }
    }

    /** Test mode: one phone runs the host and simulated players, switching between them. */
    var testMode by mutableStateOf(false)
        private set
    private var testNetwork: LoopbackNetwork? = null
    val testClients = mutableStateListOf<ClientSession>()

    /** Which player this phone is acting as in test mode (0 = host). */
    var viewingAs by mutableIntStateOf(0)

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

    fun hostTestGame() {
        saveName()
        val net = LoopbackNetwork()
        testNetwork = net
        testMode = true
        viewingAs = 0
        host = HostSession(net.host, playerName, clock, viewModelScope)
        screen = Screen.HOST_LOBBY
    }

    fun addTestPlayer() {
        val net = testNetwork ?: return
        val n = testClients.size + 1
        val client = ClientSession(net.client("test-$n"), "Test $n", "test-device-$n", clock, viewModelScope)
        client.join(HostEndpoint(LoopbackNetwork.HOST_ID, playerName))
        testClients += client
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
        commandPoints.clear()
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
        testClients.forEach { it.close() }
        testClients.clear()
        testNetwork = null
        testMode = false
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
