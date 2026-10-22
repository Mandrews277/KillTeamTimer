package com.killteamtimer.engine

/** Monotonic millisecond clock. Injectable so the engine can be tested without real time. */
fun interface Clock {
    fun now(): Long
}
