package com.killteamtimer.cover

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
) {
    fun distanceFromShooter(p: TerrainPiece) = distance - p.fromTarget
}

enum class PieceRole { COVER, OBSCURES, NONE }

enum class Tone { GOOD_FOR_SHOOTER, GOOD_FOR_TARGET, INFO }

data class Finding(val tone: Tone, val title: String, val detail: String)

enum class CoverSave { NONE, NORMAL, IMPROVED, SATURATED }

data class CoverResult(
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

fun evaluate(s: CoverSetup): CoverResult {
    val findings = mutableListOf<Finding>()
    val tooClose = s.distance <= TOO_CLOSE
    val drop = s.shooterHeight - s.targetHeight
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

    val roles = s.pieces.associate { p ->
        p.id to when (p) {
            in cover -> PieceRole.COVER
            in obscuring -> PieceRole.OBSCURES
            else -> PieceRole.NONE
        }
    }
    return CoverResult(valid, save, obscured, accurate, roles, findings)
}
