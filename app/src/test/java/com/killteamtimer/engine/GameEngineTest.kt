package com.killteamtimer.engine

import com.killteamtimer.engine.GameAction.End
import com.killteamtimer.engine.GameAction.PassTo
import com.killteamtimer.engine.GameAction.Start
import com.killteamtimer.engine.GameAction.VotePause
import com.killteamtimer.engine.GameAction.VoteResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GameEngineTest {

    private val players = listOf(Player(0, "A", 0), Player(1, "B", 1), Player(2, "C", 2))

    private fun game(config: GameConfig = GameConfig()) = GameEngine.newGame(players, config)

    private fun GameState.apply(action: GameAction, now: Long) = GameEngine.reduce(this, action, now)

    private fun GameState.elapsed(id: Int, now: Long) = player(id)!!.elapsedAt(now)

    @Test
    fun startRunsOnlyStartersClock() {
        val s = game().apply(Start(1), 1_000)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(1, s.activePlayerId)
        assertEquals(500, s.elapsed(1, 1_500))
        assertEquals(0, s.elapsed(0, 1_500))
    }

    @Test
    fun passMovesTimeBetweenPlayers() {
        val s = game().apply(Start(0), 0)
            .apply(PassTo(0, 1), 1_000)
            .apply(PassTo(1, 0), 1_300)
        assertEquals(0, s.activePlayerId)
        assertEquals(1_700, s.elapsed(0, 2_000))
        assertEquals(300, s.elapsed(1, 2_000))
    }

    @Test
    fun onlyActivePlayerCanPass() {
        val s = game().apply(Start(0), 0)
        assertSame(s, s.apply(PassTo(1, 2), 100))
        assertSame(s, s.apply(PassTo(0, 0), 100))
        assertSame(s, s.apply(PassTo(0, 9), 100))
    }

    @Test
    fun cannotPassBeforeStartOrWhilePaused() {
        val fresh = game()
        assertSame(fresh, fresh.apply(PassTo(0, 1), 0))
        val paused = game(GameConfig(votesToPause = 1)).apply(Start(0), 0).apply(VotePause(2), 100)
        assertEquals(Phase.PAUSED, paused.phase)
        assertSame(paused, paused.apply(PassTo(0, 1), 200))
    }

    @Test
    fun pauseNeedsConfiguredVotes() {
        var s = game(GameConfig(votesToPause = 2)).apply(Start(0), 0)
        s = s.apply(VotePause(1), 1_000)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(setOf(1), s.livePauseVotes(1_000))
        s = s.apply(VotePause(2), 2_000)
        assertEquals(Phase.PAUSED, s.phase)
        assertEquals(2_000, s.elapsed(0, 10_000))
        assertEquals(emptyMap<Int, Long>(), s.pauseVotes)
    }

    @Test
    fun votingTwiceWithdrawsVote() {
        val s = game(GameConfig(votesToPause = 2)).apply(Start(0), 0)
            .apply(VotePause(1), 100)
            .apply(VotePause(1), 200)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(emptySet<Int>(), s.livePauseVotes(200))
    }

    @Test
    fun votesExpire() {
        val s = game(GameConfig(votesToPause = 2, voteExpiryMs = 30_000)).apply(Start(0), 0)
            .apply(VotePause(1), 0)
        assertEquals(emptySet<Int>(), s.livePauseVotes(30_000))
        val after = s.apply(VotePause(2), 31_000)
        assertEquals(Phase.RUNNING, after.phase)
        assertEquals(setOf(2), after.livePauseVotes(31_000))
    }

    @Test
    fun votesNeededClampedToPlayerCount() {
        val s = game(GameConfig(votesToPause = 10)).apply(Start(0), 0)
            .apply(VotePause(0), 1).apply(VotePause(1), 2).apply(VotePause(2), 3)
        assertEquals(Phase.PAUSED, s.phase)
    }

    @Test
    fun anyPlayerResumeRule() {
        val s = game(GameConfig(votesToPause = 1)).apply(Start(0), 0)
            .apply(VotePause(1), 1_000)
            .apply(VoteResume(2), 5_000)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(0, s.activePlayerId)
        assertEquals(2_000, s.elapsed(0, 6_000))
    }

    @Test
    fun voteResumeRule() {
        var s = game(GameConfig(votesToPause = 2, resumeRule = ResumeRule.VOTES)).apply(Start(0), 0)
            .apply(VotePause(1), 0).apply(VotePause(2), 0)
        s = s.apply(VoteResume(1), 1_000)
        assertEquals(Phase.PAUSED, s.phase)
        s = s.apply(VoteResume(0), 1_000)
        assertEquals(Phase.RUNNING, s.phase)
    }

    @Test
    fun countDownGoesNegative() {
        val s = game(GameConfig(mode = TimerMode.CountDown(60_000))).apply(Start(0), 0)
        assertEquals(50_000, s.player(0)!!.displayMs(10_000, s.config.mode))
        assertEquals(-5_000, s.player(0)!!.displayMs(65_000, s.config.mode))
    }

    @Test
    fun endFreezesClocks() {
        val s = game().apply(Start(0), 0).apply(End(2), 4_000)
        assertEquals(Phase.ENDED, s.phase)
        assertEquals(4_000, s.elapsed(0, 99_000))
        assertNull(s.player(0)!!.activeSinceMs)
    }

    @Test
    fun unknownPlayerIgnored() {
        val s = game()
        assertSame(s, s.apply(Start(42), 0))
    }
}
