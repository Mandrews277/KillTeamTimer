package com.killteamtimer.ui.lobby

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.killteamtimer.LinkType
import com.killteamtimer.engine.GameConfig
import com.killteamtimer.net.LanTransport
import com.killteamtimer.net.LobbyPlayer
import com.killteamtimer.session.ClientSession
import com.killteamtimer.session.ClientStatus
import com.killteamtimer.session.HostEndpoint
import com.killteamtimer.session.HostSession
import com.killteamtimer.ui.components.ColorDot
import com.killteamtimer.ui.components.GameOptionsForm
import com.killteamtimer.ui.components.Segmented
import kotlinx.coroutines.delay

private fun nearbyPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun LobbyEntryScreen(
    name: String,
    onNameChange: (String) -> Unit,
    linkType: LinkType,
    onLinkTypeChange: (LinkType) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pending?.invoke() else permissionError = true
        pending = null
    }

    fun proceed(action: () -> Unit) {
        permissionError = false
        if (linkType == LinkType.WIFI) return action()
        val missing = nearbyPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            launcher.launch(missing.toTypedArray())
        }
    }

    ScreenColumn {
        Text("Phone per player", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it.take(20)) },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Connect using", style = MaterialTheme.typography.titleMedium)
        Segmented(
            options = listOf(LinkType.NEARBY to "Nearby", LinkType.WIFI to "Same Wi-Fi"),
            selected = linkType,
            onSelect = onLinkTypeChange,
        )
        Text(
            when (linkType) {
                LinkType.NEARBY -> "Uses Bluetooth and Wi-Fi Direct. No network needed. Everyone must pick Nearby."
                LinkType.WIFI -> "Backup option: every phone must be on the same Wi-Fi network."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (permissionError) {
            Text(
                "Nearby needs the Bluetooth / nearby devices permissions. Allow them, or use Same Wi-Fi.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { proceed(onHost) }, Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank()) {
            Text("Host a game")
        }
        FilledTonalButton(onClick = { proceed(onJoin) }, Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank()) {
            Text("Join a game")
        }
    }
}

@Composable
private fun LobbyList(players: List<LobbyPlayer>, myId: Int?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Players (${players.size})", style = MaterialTheme.typography.titleMedium)
            players.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(p.colorIndex)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        p.name + (if (p.id == myId) " (you)" else "") + (if (p.id == 0) " · host" else ""),
                        fontWeight = if (p.id == myId) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (!p.connected) {
                        Text("  disconnected", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun HostLobbyScreen(
    host: HostSession,
    linkType: LinkType,
    config: GameConfig,
    onConfigChange: (GameConfig) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val lobby by host.lobby.collectAsState()
    val error by host.error.collectAsState()
    val address by produceState<String?>(null, host) {
        while (true) {
            value = host.hostAddress
            delay(1_000)
        }
    }
    ScreenColumn {
        Text("Hosting", style = MaterialTheme.typography.headlineSmall)
        Text(
            when (linkType) {
                LinkType.NEARBY -> "Ask everyone to choose Nearby → Join a game."
                LinkType.WIFI -> "Ask everyone to choose Same Wi-Fi → Join a game." +
                    (address?.let { "\nIf they can't see you, they can enter $it" } ?: "")
            },
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LobbyList(lobby, host.myPlayerId)
        GameOptionsForm(config, lobby.size, onConfigChange)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, Modifier.fillMaxWidth().height(56.dp), enabled = lobby.size >= 2) {
            Text(if (lobby.size >= 2) "Start game" else "Waiting for players…")
        }
        OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
fun JoinLobbyScreen(
    client: ClientSession,
    linkType: LinkType,
    onGameStarted: () -> Unit,
    onCancel: () -> Unit,
) {
    val status by client.status.collectAsState()
    val hosts by client.hosts.collectAsState()
    val lobby by client.lobby.collectAsState()
    val myId by client.myPlayerId.collectAsState()
    val game by client.game.collectAsState()

    LaunchedEffect(game != null) {
        if (game != null) onGameStarted()
    }

    ScreenColumn {
        Text("Join a game", style = MaterialTheme.typography.headlineSmall)
        when (val s = status) {
            ClientStatus.Discovering, is ClientStatus.Error -> {
                if (s is ClientStatus.Error) Text(s.message, color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Looking for games…")
                }
                hosts.forEach { h ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { client.join(h) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(h.name, style = MaterialTheme.typography.titleMedium)
                            Text("Tap to join", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (s is ClientStatus.Error) {
                    OutlinedButton(onClick = { client.retryDiscovery() }) { Text("Search again") }
                }
                if (linkType == LinkType.WIFI) ManualJoin(client)
            }
            ClientStatus.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connecting…")
            }
            ClientStatus.Joined -> {
                Text("Waiting for the host to start…")
                LobbyList(lobby, myId)
            }
            ClientStatus.HostLost -> Text("Lost the host. Reconnecting…", color = MaterialTheme.colorScheme.error)
            is ClientStatus.Rejected -> Text(s.reason, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("Leave") }
    }
}

@Composable
private fun ManualJoin(client: ClientSession) {
    var address by remember { mutableStateOf("") }
    Spacer(Modifier.height(8.dp))
    Text("Or enter the host's address", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim() },
            placeholder = { Text("192.168.1.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                val id = if (':' in address) address else "$address:${LanTransport.DEFAULT_PORT}"
                client.join(HostEndpoint(id, address))
            },
            enabled = address.isNotBlank(),
        ) { Text("Join") }
    }
}
