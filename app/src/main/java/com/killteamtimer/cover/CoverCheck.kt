package com.killteamtimer.cover

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Cover, obscured and valid-target rules (Kill Team 2024 core rules plus the October '26 errata)
 * for one shooter and one target with terrain between them, reduced to distances along the line.
 * Distances and heights are in inches.
 */

enum class TerrainType(val label: String) { LIGHT("Light"), HEAVY("Heavy") }

enum class TargetOrder(val label: String) { ENGAGE("Engage"), CONCEAL("Conceal") }

enum class Seek(val short: String) { NONE("None"), LIGHT("Light"), ALL("All") }

data class TerrainPiece(
    val id: Int,
    val type: TerrainType,
    /** Distance from the target, along the line to the shooter. */
    val fromTarget: Float,
    /** Part of the same terrain feature as Vantage terrain either operative is on: never obscures. */
    val connectedToVantage: Boolean = false,
)

data class CoverSetup(
    val distance: Float = 6f,
    val order: TargetOrder = TargetOrder.ENGAGE,
    val visible: Boolean = true,
    /** Height above the killzone floor; above 0 means on Vantage terrain. */
    val shooterHeight: Float = 0f,
    val targetHeight: Float = 0f,
    val seek: Seek = Seek.NONE,
    val saturate: Boolean = false,
    val pieces: List<TerrainPiece> = emptyList(),
    /** The shooter's Move stat, for charge reach. */
    val move: Float = 6f,
    /** Display only: how far down the board the target has been dragged. */
    val targetAt: Float = 0f,
) {
    fun distanceFromShooter(p: TerrainPiece) = distance - p.fromTarget
}

enum class PieceRole { COVER, OBSCURES, NONE }

enum class Tone { GOOD_FOR_SHOOTER, GOOD_FOR_TARGET, INFO }

data class Finding(val tone: Tone, val title: String, val detail: String)

enum class CoverSave { NONE, NORMAL, IMPROVED, SATURATED }

data class CoverResult(
    /** Within each other's control range: Fight, not Shoot. */
    val melee: Boolean,
    /** Not in melee, but a Charge (Move + 2") can end within 1". */
    val canCharge: Boolean,
    val valid: Boolean,
    val coverSave: CoverSave,
    val obscured: Boolean,
    val accurate: Int,
    val roles: Map<Int, PieceRole>,
    val findings: List<Finding>,
)

/** Within 1" of an operative = in its control range (assuming it can see the terrain). */
private const val CONTROL_RANGE = 1f
private const val TOO_CLOSE = 2f

private const val CHARGE_BONUS = 2f

fun evaluate(s: CoverSetup): CoverResult {
    val findings = mutableListOf<Finding>()
    val tooClose = s.distance <= TOO_CLOSE
    val drop = s.shooterHeight - s.targetHeight

    // Control range is measured in three dimensions, so a height difference can keep them apart.
    if (s.visible && hypot(s.distance, drop) <= CONTROL_RANGE) {
        findings += Finding(Tone.GOOD_FOR_SHOOTER, "Can Fight", "The enemy is within control range (visible and within 1\").")
        findings += Finding(Tone.GOOD_FOR_TARGET, "Can't Shoot", "An operative can't Shoot while within control range of an enemy operative.")
        findings += Finding(
            Tone.INFO, "Other actions",
            "Can't Charge, Reposition or Dash while within control range of an enemy. It can Fall Back (2AP) but can't end that move within control range.",
        )
        return CoverResult(melee = true, canCharge = false, valid = false, CoverSave.NONE, obscured = false, accurate = 0,
            roles = s.pieces.associate { it.id to PieceRole.NONE }, findings = findings)
    }
    val shooterOnVantage = s.shooterHeight > 0f

    val cover = if (tooClose) emptyList() else s.pieces.filter { it.fromTarget <= CONTROL_RANGE }
    val heavyNearOperative = s.pieces.filter {
        it.type == TerrainType.HEAVY && (it.fromTarget <= CONTROL_RANGE || s.distanceFromShooter(it) <= CONTROL_RANGE)
    }
    val obscuring = s.pieces.filter {
        it.type == TerrainType.HEAVY && !it.connectedToVantage && it !in heavyNearOperative
    }

    // Selecting a valid target: some rules stop Light (or all) terrain counting as cover.
    val vantageIgnoresLight = shooterOnVantage && drop >= 2f && s.order == TargetOrder.CONCEAL
    val coverForSelection = cover.filter {
        when {
            s.seek == Seek.ALL -> false
            s.seek == Seek.LIGHT || vantageIgnoresLight -> it.type == TerrainType.HEAVY
            else -> true
        }
    }
    val valid = s.visible && (s.order == TargetOrder.ENGAGE || coverForSelection.isEmpty())

    if (!s.visible) {
        findings += Finding(Tone.GOOD_FOR_TARGET, "Not a valid target", "The shooter can't see the target.")
    } else if (!valid) {
        val kind = coverForSelection.joinToString(" and ") { it.type.label }.ifEmpty { "terrain" }
        findings += Finding(Tone.GOOD_FOR_TARGET, "Not a valid target", "Conceal order and in cover from $kind terrain within 1\".")
    } else {
        val why = when {
            s.order == TargetOrder.ENGAGE -> "Engage order and visible."
            cover.isEmpty() -> "Conceal order but not in cover."
            s.seek == Seek.ALL -> "Conceal order, but Seek: it can't use terrain for cover when selecting a target."
            s.seek == Seek.LIGHT -> "Conceal order, but Seek Light: it can't use Light terrain for cover when selecting a target."
            else -> "Conceal order, but the shooter is on Vantage at least 2\" higher, so it can't use Light terrain for cover."
        }
        findings += Finding(Tone.GOOD_FOR_SHOOTER, "Valid target", why)
    }

    if (tooClose) {
        findings += Finding(Tone.GOOD_FOR_SHOOTER, "Too close for cover", "Within 2\" of the shooter, the target can't be in cover.")
    }

    val inCover = cover.isNotEmpty()
    val save = when {
        !inCover -> CoverSave.NONE
        s.saturate -> CoverSave.SATURATED
        vantageIgnoresLight && valid && s.seek == Seek.NONE -> CoverSave.IMPROVED
        else -> CoverSave.NORMAL
    }
    if (valid) when (save) {
        CoverSave.NORMAL -> findings += Finding(
            Tone.GOOD_FOR_TARGET, "Cover save",
            "In cover: the defender retains one normal success without rolling it." +
                if (vantageIgnoresLight) " (Seek lets the attacker deny the improved save from Vantage.)" else "",
        )
        CoverSave.IMPROVED -> findings += Finding(
            Tone.GOOD_FOR_TARGET, "Improved cover save",
            "Vantage: the defender can retain the cover save as a critical success, or retain one additional cover save.",
        )
        CoverSave.SATURATED -> findings += Finding(
            Tone.GOOD_FOR_SHOOTER, "No cover save", "Saturate: the defender can't retain cover saves even though it's in cover.",
        )
        CoverSave.NONE -> Unit
    }

    val obscured = obscuring.isNotEmpty()
    if (valid && obscured) {
        findings += Finding(
            Tone.GOOD_FOR_TARGET, "Obscured",
            "Intervening Heavy terrain: the attacker discards one success, and all their critical successes count as normal successes.",
        )
    }
    if (valid && heavyNearOperative.any { !it.connectedToVantage } && !obscured) {
        findings += Finding(Tone.INFO, "Not obscured", "Heavy terrain within 1\" of either operative doesn't obscure.")
    }
    if (valid && s.pieces.any { it.type == TerrainType.HEAVY && it.connectedToVantage } && !obscured) {
        findings += Finding(Tone.INFO, "Not obscured", "Heavy terrain connected to the Vantage terrain an operative is on doesn't obscure.")
    }

    val accurate = when {
        !valid || !shooterOnVantage || s.order != TargetOrder.ENGAGE -> 0
        drop >= 4f -> 2
        drop >= 2f -> 1
        else -> 0
    }
    if (accurate > 0) {
        findings += Finding(
            Tone.GOOD_FOR_SHOOTER, "Accurate $accurate",
            "Shooting from Vantage at an Engage target at least ${if (accurate == 2) 4 else 2}\" lower: retain up to $accurate attack dice as normal successes without rolling.",
        )
    }

    if (valid && save == CoverSave.NONE && !obscured && accurate == 0 && !tooClose) {
        findings += Finding(Tone.INFO, "Clear shot", "No cover, not obscured.")
    }
    if (valid && cover.isNotEmpty() && obscuring.isNotEmpty() && cover.any { it.type == TerrainType.HEAVY }) {
        findings += Finding(
            Tone.INFO, "Same terrain feature?",
            "If the cover and the obscuring terrain are the same feature, the defender picks one: cover or obscured.",
        )
    }

    // A Charge moves Move + 2" and must finish within 1"; on the flat it needs to cover distance - 1".
    val chargeNeeded = (s.distance - CONTROL_RANGE).coerceAtLeast(0f)
    val canCharge = chargeNeeded <= s.move + CHARGE_BONUS
    val climb = if (drop != 0f) " Climbing ${inches(abs(drop))} counts too." else ""
    findings += if (canCharge) {
        Finding(
            Tone.GOOD_FOR_SHOOTER, "Can Charge",
            "Needs ${inches(chargeNeeded)} of its ${inches(s.move + CHARGE_BONUS)} charge move (Move + 2\") to end within 1\", if nothing is in the way.$climb Needs an Engage order.",
        )
    } else {
        Finding(Tone.INFO, "Out of charge range", "Needs ${inches(chargeNeeded)} to end within 1\", but a Charge only moves ${inches(s.move + CHARGE_BONUS)} (Move + 2\").")
    }

    val roles = s.pieces.associate { p ->
        p.id to when (p) {
            in cover -> PieceRole.COVER
            in obscuring -> PieceRole.OBSCURES
            else -> PieceRole.NONE
        }
    }
    return CoverResult(melee = false, canCharge, valid, save, obscured, accurate, roles, findings)
}

/** 2.25 -> 2¼", the way distances are read off a tape measure. */
fun inches(v: Float): String {
    val whole = v.toInt()
    val frac = when (((v - whole) * 4).roundToInt()) { 1 -> "¼"; 2 -> "½"; 3 -> "¾"; 4 -> return "${whole + 1}\""; else -> "" }
    return if (whole == 0 && frac.isNotEmpty()) "$frac\"" else "$whole$frac\""
}

