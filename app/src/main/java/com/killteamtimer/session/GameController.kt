package com.killteamtimer.session

import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameState
import kotlinx.coroutines.flow.StateFlow

/** What a game screen needs, regardless of whether the game is local or networked. */
interface GameController {
    val game: StateFlow<GameState?>

    /** Current time in the same timebase as the timestamps inside [game]. */
    fun now(): Long

    fun send(action: GameAction)
}
