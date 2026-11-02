package com.killteamtimer.ui.cover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.killteamtimer.cover.CoverSave
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.killteamtimer.cover.CoverResult
import com.killteamtimer.cover.CoverSetup
import com.killteamtimer.cover.PieceRole
import com.killteamtimer.cover.Seek
import com.killteamtimer.cover.TargetOrder
import com.killteamtimer.cover.TerrainPiece
import com.killteamtimer.cover.TerrainType
import com.killteamtimer.cover.Tone
import com.killteamtimer.cover.evaluate
import com.killteamtimer.ui.components.Segmented
import com.killteamtimer.ui.components.Stepper
import kotlin.math.abs
import kotlin.math.roundToInt

private val TargetColor = Color(0xFFE53935)
private val ShooterColor = Color(0xFF42A5F5)
private val LightColor = Color(0xFF9CCC65)
private val HeavyColor = Color(0xFFB0BEC5)
private val CoverColor = Color(0xFFFFB300)
private val ObscureColor = Color(0xFFBA68C8)

/**
 * Target at the top, shooter at the bottom, terrain as lines across the gap. Drag a line to move it;
 * everything below the diagram updates live.
 */
@Composable
fun CoverCheckScreen(setup: CoverSetup, onChange: (CoverSetup) -> Unit, onBack: () -> Unit) {
    val result = evaluate(setup)
    var selectedId by remember { mutableStateOf(setup.pieces.lastOrNull()?.id) }
    val selected = setup.pieces.firstOrNull { it.id == selectedId }

    fun update(p: TerrainPiece) = onChange(setup.copy(pieces = setup.pieces.map { if (it.id == p.id) p else it }))
    fun add(type: TerrainType) {
        val p = TerrainPiece((setup.pieces.maxOfOrNull { it.id } ?: 0) + 1, type, snap(setup.distance / 2))
        onChange(setup.copy(pieces = setup.pieces + p))
        selectedId = p.id
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Cover check", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onChange(CoverSetup()); selectedId = null }) { Text("Reset") }
        }
        // Stays on screen while scrolling down to the settings.
        Verdict(result)
    }
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Diagram(setup, result, selectedId, onSelect = { selectedId = it }, onMove = { update(it) })

        Results(result)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { add(TerrainType.LIGHT) }, Modifier.weight(1f)) { Text("+ Light terrain") }
            FilledTonalButton(onClick = { add(TerrainType.HEAVY) }, Modifier.weight(1f)) { Text("+ Heavy terrain") }
        }
        if (selected != null) PieceEditor(
            piece = selected,
            distance = setup.distance,
            onChange = { update(it) },
            onDelete = { onChange(setup.copy(pieces = setup.pieces - selected)); selectedId = null },
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Distance between them: ${inches(setup.distance)}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = setup.distance,
                    onValueChange = { d ->
                        val dist = (d * 2).roundToInt() / 2f
                        onChange(setup.copy(distance = dist, pieces = setup.pieces.map { it.copy(fromTarget = it.fromTarget.coerceAtMost(dist)) }))
                    },
                    valueRange = 1f..24f,
                )
                Text("Target", style = MaterialTheme.typography.titleSmall)
                Segmented(TargetOrder.entries.map { it to it.label }, setup.order, { onChange(setup.copy(order = it)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible to the shooter", Modifier.weight(1f))
                    Switch(setup.visible, { onChange(setup.copy(visible = it)) })
                }
                Stepper(
                    "Target height", height(setup.targetHeight),
                    onMinus = { onChange(setup.copy(targetHeight = (setup.targetHeight - 1).coerceAtLeast(0f))) },
                    onPlus = { onChange(setup.copy(targetHeight = setup.targetHeight + 1)) },
                )
                Stepper(
                    "Shooter height", height(setup.shooterHeight),
                    onMinus = { onChange(setup.copy(shooterHeight = (setup.shooterHeight - 1).coerceAtLeast(0f))) },
                    onPlus = { onChange(setup.copy(shooterHeight = setup.shooterHeight + 1)) },
                )
                Text("Weapon: Seek", style = MaterialTheme.typography.titleSmall)
                Segmented(Seek.entries.map { it to it.short }, setup.seek, { onChange(setup.copy(seek = it)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saturate", Modifier.weight(1f))
                    Switch(setup.saturate, { onChange(setup.copy(saturate = it)) })
                }
                Text(
                    "Height above 0 means on Vantage terrain. Vantage edges near the target are Light terrain, so add a Light line within 1\" for them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
    }
}

/** One-line answer: can I shoot it, and what modifies the dice. */
@Composable
private fun Verdict(result: CoverResult) {
    val parts = buildList {
        add((if (result.valid) "Valid target" else "Not a valid target") to (if (result.valid) ShooterColor else TargetColor))
        if (result.valid) {
            when (result.coverSave) {
                CoverSave.NORMAL -> add("Cover save" to TargetColor)
                CoverSave.IMPROVED -> add("Improved cover save" to TargetColor)
                CoverSave.SATURATED -> add("No cover save" to ShooterColor)
                CoverSave.NONE -> Unit
            }
            if (result.obscured) add("Obscured" to TargetColor)
            if (result.accurate > 0) add("Accurate ${result.accurate}" to ShooterColor)
        }
    }
    Text(
        buildAnnotatedString {
            parts.forEachIndexed { i, (text, color) ->
                if (i > 0) append("  ·  ")
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text) }
            }
        },
        Modifier.padding(start = 12.dp, bottom = 4.dp),
        fontSize = 16.sp,
    )
}

@Composable
private fun Diagram(
    setup: CoverSetup,
    result: CoverResult,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onMove: (TerrainPiece) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(setup)
    val currentSelected by rememberUpdatedState(selectedId)
    // The gesture handlers live across recompositions, so they must call the latest callbacks.
    val move by rememberUpdatedState(onMove)
    val select by rememberUpdatedState(onSelect)
    val labelColor = MaterialTheme.colorScheme.onSurface

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF15181C))) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .pointerInput(Unit) {
                    // Tapping overlapping pieces cycles through them.
                    detectTapGestures { pos ->
                        val h = size.height.toFloat()
                        val others = current.copy(pieces = current.pieces.filter { it.id != currentSelected })
                        select(nearest(others, pos.y, h) ?: nearest(current, pos.y, h))
                    }
                }
                .pointerInput(Unit) {
                    var dragging: Int? = null
                    detectDragGestures(
                        onDragStart = { pos ->
                            dragging = nearest(current, pos.y, size.height.toFloat(), currentSelected)
                            select(dragging)
                        },
                        onDragEnd = { dragging = null },
                    ) { change, _ ->
                        val piece = current.pieces.firstOrNull { it.id == dragging } ?: return@detectDragGestures
                        change.consume()
                        val geo = Geometry(size.height.toFloat(), current.distance)
                        move(piece.copy(fromTarget = snap(geo.inchesAt(change.position.y)).coerceIn(0f, current.distance)))
                    }
                },
        ) {
            val geo = Geometry(size.height, setup.distance)
            val cx = size.width / 2
            val lineHalf = size.width * 0.32f

            // 1" zones: cover around the target, and where Heavy terrain stops obscuring.
            zone(geo.y(0f), geo.y(1f), CoverColor.copy(alpha = 0.12f))
            zone(geo.y(setup.distance - 1f), geo.y(setup.distance), Color.White.copy(alpha = 0.06f))
            label(measurer, "cover 1\"", Offset(8.dp.toPx(), geo.y(1f) - 16.dp.toPx()), CoverColor.copy(alpha = 0.8f), 11)
            label(measurer, "1\"", Offset(8.dp.toPx(), geo.y(setup.distance - 1f) + 2.dp.toPx()), labelColor.copy(alpha = 0.5f), 11)

            drawLine(
                Color.White.copy(alpha = 0.5f), Offset(cx, geo.y(0f)), Offset(cx, geo.y(setup.distance)), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )

            setup.pieces.forEach { p ->
                val y = geo.y(p.fromTarget)
                val role = result.roles[p.id] ?: PieceRole.NONE
                val base = if (p.type == TerrainType.LIGHT) LightColor else HeavyColor
                if (p.id == selectedId) {
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - lineHalf - 6, y), Offset(cx + lineHalf + 6, y), 22.dp.toPx(), StrokeCap.Round)
                }
                drawLine(
                    if (role == PieceRole.NONE) base.copy(alpha = 0.55f) else base,
                    Offset(cx - lineHalf, y), Offset(cx + lineHalf, y),
                    (if (p.type == TerrainType.HEAVY) 10 else 5).dp.toPx(), StrokeCap.Round,
                    pathEffect = if (p.type == TerrainType.LIGHT) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 5.dp.toPx())) else null,
                )
                val tag = when (role) {
                    PieceRole.COVER -> "cover" to CoverColor
                    PieceRole.OBSCURES -> "obscures" to ObscureColor
                    PieceRole.NONE -> "" to labelColor
                }
                label(measurer, inches(p.fromTarget), Offset(cx + lineHalf + 10.dp.toPx(), y - 18.dp.toPx()), labelColor, 12)
                if (tag.first.isNotEmpty()) {
                    label(measurer, tag.first, Offset(cx + lineHalf + 10.dp.toPx(), y), tag.second, 12, bold = true)
                }
                if (p.connectedToVantage) {
                    label(measurer, "vantage", Offset(cx - lineHalf - 56.dp.toPx(), y - 8.dp.toPx()), labelColor.copy(alpha = 0.6f), 11)
                }
            }

            operative(measurer, Offset(cx, geo.y(0f)), TargetColor, "TARGET · ${setup.order.label}${up(setup.targetHeight)}", above = true)
            operative(measurer, Offset(cx, geo.y(setup.distance)), ShooterColor, "SHOOTER${up(setup.shooterHeight)}", above = false)
        }
    }
}

/** Maps inches from the target to y pixels, leaving room above and below for the operatives. */
private class Geometry(height: Float, val distance: Float) {
    private val top = height * 0.13f
    private val span = height * 0.74f
    fun y(inches: Float) = top + inches / distance * span
    fun inchesAt(y: Float) = (y - top) / span * distance
}

/** The piece under [y]; the selected one wins when pieces overlap, e.g. one just added on top of another. */
private fun nearest(setup: CoverSetup, y: Float, height: Float, selected: Int? = null): Int? {
    val geo = Geometry(height, setup.distance)
    val near = setup.pieces.filter { abs(geo.y(it.fromTarget) - y) < height * 0.07f }
    return (near.firstOrNull { it.id == selected } ?: near.minByOrNull { abs(geo.y(it.fromTarget) - y) })?.id
}

private fun DrawScope.zone(from: Float, to: Float, color: Color) =
    drawRect(color, Offset(0f, from), Size(size.width, (to - from).coerceAtLeast(0f)))

private fun DrawScope.operative(m: TextMeasurer, at: Offset, color: Color, text: String, above: Boolean) {
    drawCircle(color, 16.dp.toPx(), at)
    drawCircle(Color.White, 16.dp.toPx(), at, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    val layout = m.measure(text, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color))
    val y = if (above) at.y - 20.dp.toPx() - layout.size.height else at.y + 20.dp.toPx()
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2, y))
}

private fun DrawScope.label(m: TextMeasurer, text: String, at: Offset, color: Color, sizeSp: Int, bold: Boolean = false) =
    drawText(m, text, at, TextStyle(fontSize = sizeSp.sp, color = color, fontWeight = if (bold) FontWeight.Bold else null))

@Composable
private fun Results(result: CoverResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result.findings.forEach { f ->
                val color = when (f.tone) {
                    Tone.GOOD_FOR_SHOOTER -> ShooterColor
                    Tone.GOOD_FOR_TARGET -> TargetColor
                    Tone.INFO -> MaterialTheme.colorScheme.secondary
                }
                Column {
                    Text(f.title, color = color, fontWeight = FontWeight.Bold)
                    Text(f.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PieceEditor(piece: TerrainPiece, distance: Float, onChange: (TerrainPiece) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selected terrain", style = MaterialTheme.typography.titleSmall)
            Segmented(TerrainType.entries.map { it to it.label }, piece.type, { onChange(piece.copy(type = it)) })
            Stepper(
                "From target", inches(piece.fromTarget),
                onMinus = { onChange(piece.copy(fromTarget = (piece.fromTarget - 0.25f).coerceAtLeast(0f))) },
                onPlus = { onChange(piece.copy(fromTarget = (piece.fromTarget + 0.25f).coerceAtMost(distance))) },
            )
            if (piece.type == TerrainType.HEAVY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(piece.connectedToVantage, { onChange(piece.copy(connectedToVantage = it)) })
                    Text("Part of the Vantage terrain either operative is on", style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick = onDelete, Modifier.fillMaxWidth()) { Text("Remove") }
        }
    }
}

private fun snap(inches: Float) = (inches * 4).roundToInt() / 4f

private fun inches(v: Float): String {
    val whole = v.toInt()
    val frac = when (((v - whole) * 4).roundToInt()) { 1 -> "¼"; 2 -> "½"; 3 -> "¾"; 4 -> return "${whole + 1}\""; else -> "" }
    return if (whole == 0 && frac.isNotEmpty()) "$frac\"" else "$whole$frac\""
}

private fun height(v: Float) = if (v <= 0f) "Floor" else inches(v)

private fun up(v: Float) = if (v > 0f) " · ${inches(v)} up" else ""
