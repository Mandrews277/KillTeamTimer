package com.killteamtimer.ui.cover

import androidx.compose.animation.core.animateFloatAsState
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
import com.killteamtimer.cover.inches
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
 * Target at the top, shooter below, terrain as lines across the gap. Drag the operatives or the lines;
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
        Diagram(setup, result, selectedId, onSelect = { selectedId = it }, onChange = { onChange(it) })

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
                    onValueChange = { d -> onChange(setup.withDistance((d * 2).roundToInt() / 2f)) },
                    valueRange = 0f..24f,
                )
                Stepper(
                    "Shooter's Move", inches(setup.move),
                    onMinus = { onChange(setup.copy(move = (setup.move - 1).coerceAtLeast(1f))) },
                    onPlus = { onChange(setup.copy(move = setup.move + 1)) },
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
        if (result.melee) {
            add("Control range" to TargetColor)
            add("Fight" to ShooterColor)
            add("Can't shoot" to TargetColor)
            return@buildList
        }
        add((if (result.valid) "Valid target" else "Not a valid target") to (if (result.valid) ShooterColor else TargetColor))
        if (result.canCharge) add("Can charge" to ShooterColor)
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
    onChange: (CoverSetup) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    // Inches shown top to bottom. Held while dragging so the board doesn't rescale under the finger,
    // then eases to fit once the finger lifts.
    var heldRange by remember { mutableStateOf<Float?>(null) }
    val range by animateFloatAsState(heldRange ?: fitRange(setup), label = "range")
    val current by rememberUpdatedState(setup)
    val currentRange by rememberUpdatedState(range)
    val currentSelected by rememberUpdatedState(selectedId)
    // The gesture handlers live across recompositions, so they must call the latest callbacks.
    val change by rememberUpdatedState(onChange)
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
                        val geo = Geometry(size.height.toFloat(), currentRange)
                        val others = current.copy(pieces = current.pieces.filter { it.id != currentSelected })
                        select(nearestPiece(others, geo, pos.y) ?: nearestPiece(current, geo, pos.y))
                    }
                }
                .pointerInput(Unit) {
                    var dragging: Int? = null
                    detectDragGestures(
                        onDragStart = { pos ->
                            heldRange = currentRange
                            val geo = Geometry(size.height.toFloat(), currentRange)
                            dragging = grab(current, geo, pos, size.width / 2f, 32.dp.toPx(), currentSelected)
                            if (dragging != TARGET && dragging != SHOOTER) select(dragging)
                        },
                        onDragEnd = { dragging = null; heldRange = null },
                        onDragCancel = { dragging = null; heldRange = null },
                    ) { pointer, _ ->
                        val id = dragging ?: return@detectDragGestures
                        pointer.consume()
                        val s = current
                        val geo = Geometry(size.height.toFloat(), currentRange)
                        val at = snap(geo.inchesAt(pointer.position.y)).coerceIn(0f, currentRange)
                        change(
                            when (id) {
                                SHOOTER -> s.withDistance((at - s.targetAt).coerceAtLeast(0f))
                                // Terrain stays put on the board while the target moves past it.
                                TARGET -> {
                                    val newAt = at.coerceAtMost(s.targetAt + s.distance)
                                    val shift = newAt - s.targetAt
                                    s.copy(
                                        targetAt = newAt,
                                        distance = s.distance - shift,
                                        pieces = s.pieces.map { it.copy(fromTarget = (it.fromTarget - shift).coerceIn(0f, s.distance - shift)) },
                                    )
                                }
                                else -> s.copy(pieces = s.pieces.map {
                                    if (it.id == id) it.copy(fromTarget = (at - s.targetAt).coerceIn(0f, s.distance)) else it
                                })
                            },
                        )
                    }
                },
        ) {
            val geo = Geometry(size.height, range)
            val cx = size.width / 2
            val lineHalf = size.width * 0.32f
            val targetY = geo.y(setup.targetAt)
            val shooterY = geo.y(setup.targetAt + setup.distance)

            if (result.melee) {
                // Base to base: one shared control-range zone.
                zone(targetY - geo.px(1f), shooterY + geo.px(1f), TargetColor.copy(alpha = 0.16f))
                label(measurer, "control range", Offset(8.dp.toPx(), shooterY + geo.px(1f) - 16.dp.toPx()), TargetColor, 11)
            } else {
                // 1" zones: cover around the target, and where Heavy terrain stops obscuring.
                zone(targetY, geo.y(setup.targetAt + 1f), CoverColor.copy(alpha = 0.12f))
                zone(shooterY - geo.px(1f), shooterY, Color.White.copy(alpha = 0.06f))
                label(measurer, "cover 1\"", Offset(8.dp.toPx(), geo.y(setup.targetAt + 1f) - 16.dp.toPx()), CoverColor.copy(alpha = 0.8f), 11)
                label(measurer, "1\"", Offset(8.dp.toPx(), shooterY - geo.px(1f) + 2.dp.toPx()), labelColor.copy(alpha = 0.5f), 11)
            }

            drawLine(
                (if (result.melee) TargetColor else Color.White).copy(alpha = 0.5f), Offset(cx, targetY), Offset(cx, shooterY), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )

            // How far a Charge (Move + 2") gets the shooter towards the target.
            val reachY = shooterY - geo.px(setup.move + 2f)
            if (!result.melee && reachY > 0f) {
                drawLine(ShooterColor, Offset(cx - 14.dp.toPx(), reachY), Offset(cx + 14.dp.toPx(), reachY), 3.dp.toPx(), StrokeCap.Round)
                label(measurer, "charge", Offset(cx - 70.dp.toPx(), reachY - 8.dp.toPx()), ShooterColor, 11)
            }

            setup.pieces.forEach { p ->
                val y = geo.y(setup.targetAt + p.fromTarget)
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

            label(measurer, inches(setup.distance), Offset(cx + 24.dp.toPx(), (targetY + shooterY) / 2 - 8.dp.toPx()), labelColor.copy(alpha = 0.7f), 12)
            operative(measurer, Offset(cx, targetY), TargetColor, "TARGET · ${setup.order.label}${up(setup.targetHeight)}", above = true)
            operative(measurer, Offset(cx, shooterY), ShooterColor, "SHOOTER${up(setup.shooterHeight)}", above = false)
        }
    }
}

private const val TARGET = -1
private const val SHOOTER = -2

/** Inches the board shows: at least 6", and enough to fit both operatives. */
private fun fitRange(s: CoverSetup) = maxOf(6f, s.targetAt + s.distance)

/** Moves the shooter; terrain can't end up behind it. */
private fun CoverSetup.withDistance(d: Float) =
    copy(distance = d, pieces = pieces.map { it.copy(fromTarget = it.fromTarget.coerceAtMost(d)) })

/** Maps inches down the board to y pixels, leaving room above and below for the operatives' labels. */
private class Geometry(val height: Float, val range: Float) {
    private val top = height * 0.13f
    private val span = height * 0.74f
    fun y(inches: Float) = top + inches / range * span
    fun px(inches: Float) = inches / range * span
    fun inchesAt(y: Float) = (y - top) / span * range
}

/** An operative near the centre line wins over terrain; otherwise the piece under [pos]. */
private fun grab(s: CoverSetup, geo: Geometry, pos: Offset, cx: Float, reach: Float, selected: Int?): Int? {
    if (abs(pos.x - cx) < reach * 1.5f) {
        val toTarget = abs(geo.y(s.targetAt) - pos.y)
        val toShooter = abs(geo.y(s.targetAt + s.distance) - pos.y)
        if (minOf(toTarget, toShooter) < reach) return if (toShooter <= toTarget) SHOOTER else TARGET
    }
    return nearestPiece(s, geo, pos.y, selected)
}

/** The piece under [y]; the selected one wins when pieces overlap, e.g. one just added on top of another. */
private fun nearestPiece(s: CoverSetup, geo: Geometry, y: Float, selected: Int? = null): Int? {
    fun gap(p: TerrainPiece) = abs(geo.y(s.targetAt + p.fromTarget) - y)
    val near = s.pieces.filter { gap(it) < geo.height * 0.07f }
    return (near.firstOrNull { it.id == selected } ?: near.minByOrNull(::gap))?.id
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

private fun height(v: Float) = if (v <= 0f) "Floor" else inches(v)

private fun up(v: Float) = if (v > 0f) " · ${inches(v)} up" else ""
