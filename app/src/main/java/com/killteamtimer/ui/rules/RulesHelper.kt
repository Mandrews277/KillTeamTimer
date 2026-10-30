package com.killteamtimer.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.killteamtimer.rules.Kind
import com.killteamtimer.rules.RuleItem
import com.killteamtimer.rules.RulesData

/**
 * Pick your team and what you're doing; lists the names of the rules that could apply.
 * Names only: the rules text itself lives in the official downloads.
 */
@Composable
fun RulesHelper(teamId: String?, onTeamChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data = remember { RulesData.load(context) }
    val team = data.team(teamId)
    var situation by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What can I use?", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                Text(team?.name ?: "Choose your kill team")
            }
            if (team == null) return@Column

            // Turn-structure situations on the left, what an operative is doing on the right.
            val (left, right) = data.tags.keys.partition { it in LEFT_COLUMN }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(left.sortedBy { LEFT_COLUMN.indexOf(it) }, right).forEach { column ->
                    Column(Modifier.weight(1f)) {
                        column.forEach { id ->
                            FilterChip(
                                selected = situation == id,
                                onClick = { situation = if (situation == id) null else id },
                                label = { Text(data.tags.getValue(id)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            val tag = situation ?: return@Column
            val matches = (team.items + data.universal.map { it.copy(kind = Kind.EQUIPMENT) })
                .filter { tag in it.tags }
            if (matches.isEmpty()) {
                Text("Nothing specific for this.", color = MaterialTheme.colorScheme.secondary)
            }
            Kind.entries.forEach { kind ->
                val ofKind = matches.filter { it.kind == kind }
                if (ofKind.isNotEmpty()) RuleSection(kind.label, ofKind)
            }
        }
    }

    if (picking) {
        TeamPicker(data, onPick = { onTeamChange(it); picking = false }, onDismiss = { picking = false })
    }
}

private val LEFT_COLUMN = listOf("strategy", "activate", "incapacitated", "end", "general", "action")

/** Names grouped under their heading: "Boss Nob" then its abilities, or a ploy on its own. */
@Composable
private fun RuleSection(title: String, items: List<RuleItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider()
        Text(
            title.uppercase(),
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        items.groupBy { it.heading }.forEach { (heading, group) ->
            val subs = group.mapNotNull { it.sub }
            Text(heading, fontWeight = FontWeight.Bold)
            subs.forEach { Text("   • $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun TeamPicker(data: RulesData, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your kill team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(data.teams.filter { it.name.contains(query.trim(), ignoreCase = true) }, key = { it.id }) { team ->
                        Text(
                            team.name,
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(team.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
