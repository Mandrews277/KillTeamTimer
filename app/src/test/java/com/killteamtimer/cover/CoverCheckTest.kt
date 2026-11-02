package com.killteamtimer.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCheckTest {
    private fun light(at: Float, id: Int = 1) = TerrainPiece(id, TerrainType.LIGHT, at)
    private fun heavy(at: Float, id: Int = 2, vantage: Boolean = false) = TerrainPiece(id, TerrainType.HEAVY, at, vantage)

    @Test fun `open ground is a clear shot`() {
        val r = evaluate(CoverSetup(distance = 6f))
        assertTrue(r.valid)
        assertEquals(CoverSave.NONE, r.coverSave)
        assertFalse(r.obscured)
    }

    @Test fun `terrain within 1 inch of target gives cover`() {
        val r = evaluate(CoverSetup(pieces = listOf(light(0.5f))))
        assertTrue(r.valid)
        assertEquals(CoverSave.NORMAL, r.coverSave)
        assertEquals(PieceRole.COVER, r.roles[1])
    }

    @Test fun `terrain further than 1 inch from target gives no cover`() {
        assertEquals(CoverSave.NONE, evaluate(CoverSetup(pieces = listOf(light(1.5f)))).coverSave)
    }

    @Test fun `conceal in cover is not a valid target`() {
        assertFalse(evaluate(CoverSetup(order = TargetOrder.CONCEAL, pieces = listOf(light(1f)))).valid)
    }

    @Test fun `no cover within 2 inches of the shooter`() {
        val r = evaluate(CoverSetup(distance = 2f, order = TargetOrder.CONCEAL, pieces = listOf(light(0.5f))))
        assertTrue(r.valid)
        assertEquals(CoverSave.NONE, r.coverSave)
    }

    @Test fun `heavy terrain in the middle obscures`() {
        val r = evaluate(CoverSetup(distance = 8f, pieces = listOf(heavy(4f))))
        assertTrue(r.obscured)
        assertEquals(PieceRole.OBSCURES, r.roles[2])
    }

    @Test fun `heavy terrain within 1 inch of either operative does not obscure`() {
        assertFalse(evaluate(CoverSetup(distance = 8f, pieces = listOf(heavy(0.8f)))).obscured)
        assertFalse(evaluate(CoverSetup(distance = 8f, pieces = listOf(heavy(7.5f)))).obscured)
    }

    @Test fun `light terrain never obscures`() {
        assertFalse(evaluate(CoverSetup(distance = 8f, pieces = listOf(light(4f)))).obscured)
    }

    @Test fun `heavy connected to vantage does not obscure`() {
        assertFalse(evaluate(CoverSetup(distance = 8f, pieces = listOf(heavy(4f, vantage = true)))).obscured)
    }

    @Test fun `vantage gives accurate against engage targets`() {
        assertEquals(1, evaluate(CoverSetup(shooterHeight = 3f)).accurate)
        assertEquals(2, evaluate(CoverSetup(shooterHeight = 4f)).accurate)
        assertEquals(0, evaluate(CoverSetup(shooterHeight = 1f)).accurate)
        assertEquals(0, evaluate(CoverSetup(shooterHeight = 4f, order = TargetOrder.CONCEAL)).accurate)
    }

    @Test fun `vantage lets conceal targets in light cover be shot with an improved save`() {
        val r = evaluate(CoverSetup(shooterHeight = 3f, order = TargetOrder.CONCEAL, pieces = listOf(light(0.5f))))
        assertTrue(r.valid)
        assertEquals(CoverSave.IMPROVED, r.coverSave)
    }

    @Test fun `vantage does not ignore heavy cover`() {
        assertFalse(evaluate(CoverSetup(shooterHeight = 3f, order = TargetOrder.CONCEAL, pieces = listOf(heavy(0.5f)))).valid)
    }

    @Test fun `seek light makes conceal valid but keeps a normal save`() {
        val r = evaluate(CoverSetup(order = TargetOrder.CONCEAL, seek = Seek.LIGHT, pieces = listOf(light(0.5f))))
        assertTrue(r.valid)
        assertEquals(CoverSave.NORMAL, r.coverSave)
        assertFalse(evaluate(CoverSetup(order = TargetOrder.CONCEAL, seek = Seek.LIGHT, pieces = listOf(heavy(0.5f)))).valid)
        assertTrue(evaluate(CoverSetup(order = TargetOrder.CONCEAL, seek = Seek.ALL, pieces = listOf(heavy(0.5f)))).valid)
    }

    @Test fun `saturate removes the cover save`() {
        assertEquals(CoverSave.SATURATED, evaluate(CoverSetup(saturate = true, pieces = listOf(light(0.5f)))).coverSave)
    }

    @Test fun `within 1 inch is melee, not shooting`() {
        val r = evaluate(CoverSetup(distance = 1f, pieces = listOf(light(0.5f))))
        assertTrue(r.melee)
        assertFalse(r.valid)
        assertFalse(r.canCharge)
    }

    @Test fun `height difference keeps them out of control range`() {
        assertFalse(evaluate(CoverSetup(distance = 0.5f, shooterHeight = 3f)).melee)
    }

    @Test fun `charge reaches move plus 2 plus control range`() {
        assertTrue(evaluate(CoverSetup(distance = 9f, move = 6f)).canCharge)
        assertFalse(evaluate(CoverSetup(distance = 9.25f, move = 6f)).canCharge)
    }

    @Test fun `not visible is never valid`() {
        assertFalse(evaluate(CoverSetup(visible = false)).valid)
    }
}
