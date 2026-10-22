package com.killteamtimer.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Everything a player can do. [by] is the id of the player performing the action. */
@Serializable
sealed interface GameAction {
    val by: Int

    /** Starts the game with [by]'s clock running. */
    @Serializable
    @SerialName("start")
    data class Start(override val by: Int) : GameAction

    @Serializable
    @SerialName("pass")
    data class PassTo(override val by: Int, val to: Int) : GameAction

    /** Casts a pause vote, or withdraws it if [by] already voted. */
    @Serializable
    @SerialName("votePause")
    data class VotePause(override val by: Int) : GameAction

    /** Resumes (ANY_PLAYER rule) or casts/withdraws a resume vote (VOTES rule). */
    @Serializable
    @SerialName("voteResume")
    data class VoteResume(override val by: Int) : GameAction

    @Serializable
    @SerialName("end")
    data class End(override val by: Int) : GameAction
}

object GameEngine {

    fun newGame(players: List<Player>, config: GameConfig): GameState =
        GameState(players = players, config = config)

    /** Applies [action] at time [now]. Invalid actions return [state] unchanged. */
    fun reduce(state: GameState, action: GameAction, now: Long): GameState {
        if (state.player(action.by) == null) return state
        return when (action) {
            is GameAction.Start -> start(state, action.by, now)
            is GameAction.PassTo -> pass(state, action, now)
            is GameAction.VotePause -> votePause(state, action.by, now)
            is GameAction.VoteResume -> voteResume(state, action.by, now)
            is GameAction.End -> end(state, now)
        }
    }

    private fun start(state: GameState, by: Int, now: Long): GameState {
        if (state.phase != Phase.NOT_STARTED) return state
        return state.copy(
            phase = Phase.RUNNING,
            activePlayerId = by,
            players = state.players.map { if (it.id == by) it.copy(activeSinceMs = now) else it },
        )
    }

    private fun pass(state: GameState, action: GameAction.PassTo, now: Long): GameState {
        if (state.phase != Phase.RUNNING) return state
        if (state.activePlayerId != action.by || action.to == action.by) return state
        if (state.player(action.to) == null) return state
        return state.copy(
            activePlayerId = action.to,
            players = state.players.map {
                when (it.id) {
                    action.by -> it.stopped(now)
                    action.to -> it.copy(activeSinceMs = now)
                    else -> it
                }
            },
        )
    }

    private fun votePause(state: GameState, by: Int, now: Long): GameState {
        if (state.phase != Phase.RUNNING) return state
        val votes = toggleVote(state.pauseVotes, by, now, state.config.voteExpiryMs)
        if (votes.size < state.votesNeeded) return state.copy(pauseVotes = votes)
        return state.copy(
            phase = Phase.PAUSED,
            pauseVotes = emptyMap(),
            resumeVotes = emptyMap(),
            players = state.players.map { it.stopped(now) },
        )
    }

    private fun voteResume(state: GameState, by: Int, now: Long): GameState {
        if (state.phase != Phase.PAUSED) return state
        if (state.config.resumeRule == ResumeRule.VOTES) {
            val votes = toggleVote(state.resumeVotes, by, now, state.config.voteExpiryMs)
            if (votes.size < state.votesNeeded) return state.copy(resumeVotes = votes)
        }
        return state.copy(
            phase = Phase.RUNNING,
            resumeVotes = emptyMap(),
            players = state.players.map {
                if (it.id == state.activePlayerId) it.copy(activeSinceMs = now) else it
            },
        )
    }

    private fun end(state: GameState, now: Long): GameState {
        if (state.phase == Phase.ENDED) return state
        return state.copy(
            phase = Phase.ENDED,
            pauseVotes = emptyMap(),
            resumeVotes = emptyMap(),
            players = state.players.map { it.stopped(now) },
        )
    }

    private fun toggleVote(votes: Map<Int, Long>, by: Int, now: Long, expiryMs: Long): Map<Int, Long> {
        val live = votes.filterValues { now - it < expiryMs }
        return if (by in live) live - by else live + (by to now)
    }

    private fun Player.stopped(now: Long): Player =
        copy(accumulatedMs = elapsedAt(now), activeSinceMs = null)
}
