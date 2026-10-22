package com.killteamtimer.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TimerMode {
    @Serializable
    @SerialName("up")
    data object CountUp : TimerMode

    @Serializable
    @SerialName("down")
    data class CountDown(val limitMs: Long) : TimerMode
}

@Serializable
enum class ResumeRule { ANY_PLAYER, VOTES }

@Serializable
data class GameConfig(
    val mode: TimerMode = TimerMode.CountUp,
    val votesToPause: Int = 2,
    val resumeRule: ResumeRule = ResumeRule.ANY_PLAYER,
    val voteExpiryMs: Long = 30_000,
)

@Serializable
data class Player(
    val id: Int,
    val name: String,
    val colorIndex: Int,
    val accumulatedMs: Long = 0,
    /** Clock time this player's timer started running, or null when it isn't running. */
    val activeSinceMs: Long? = null,
) {
    fun elapsedAt(now: Long): Long = accumulatedMs + (activeSinceMs?.let { now - it } ?: 0)

    /** Time to display: elapsed for count-up, remaining (possibly negative) for count-down. */
    fun displayMs(now: Long, mode: TimerMode): Long = when (mode) {
        TimerMode.CountUp -> elapsedAt(now)
        is TimerMode.CountDown -> mode.limitMs - elapsedAt(now)
    }
}

@Serializable
enum class Phase { NOT_STARTED, RUNNING, PAUSED, ENDED }

@Serializable
data class GameState(
    val players: List<Player>,
    val config: GameConfig,
    val phase: Phase = Phase.NOT_STARTED,
    val activePlayerId: Int? = null,
    /** playerId -> time the vote was cast. */
    val pauseVotes: Map<Int, Long> = emptyMap(),
    val resumeVotes: Map<Int, Long> = emptyMap(),
) {
    fun player(id: Int): Player? = players.firstOrNull { it.id == id }

    /** Votes needed, never more than the number of players. */
    val votesNeeded: Int get() = config.votesToPause.coerceIn(1, players.size.coerceAtLeast(1))

    fun livePauseVotes(now: Long): Set<Int> = live(pauseVotes, now)
    fun liveResumeVotes(now: Long): Set<Int> = live(resumeVotes, now)

    private fun live(votes: Map<Int, Long>, now: Long): Set<Int> =
        votes.filterValues { now - it < config.voteExpiryMs }.keys
}
