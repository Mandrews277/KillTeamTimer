package com.killteamtimer.session

import com.killteamtimer.engine.Clock
import com.killteamtimer.engine.GameAction
import com.killteamtimer.engine.GameEngine
import com.killteamtimer.engine.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class LocalGameController(initial: GameState, private val clock: Clock) : GameController {
    private val _game = MutableStateFlow<GameState?>(initial)
    override val game: StateFlow<GameState?> = _game

    override fun now(): Long = clock.now()

    override fun send(action: GameAction) {
        _game.update { it?.let { s -> GameEngine.reduce(s, action, clock.now()) } }
    }
}
