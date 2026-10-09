package com.watchthetime.domain.rules

import kotlinx.serialization.Serializable

@Serializable
enum class ClockMode(val label: String, val detail: String) {
    STOPPING(
        "Stopping time",
        "The clock pauses when you register a score, foul, timeout, free throws or a substitution. Tap the clock to resume.",
    ),
    RUNNING(
        "Running time",
        "The clock keeps running through scores and fouls. It stops when you tap it, on a timeout or at the end of the period.",
    ),
}

/** What the user registered; used to decide whether the clock pauses on its own. */
enum class StopTrigger { SCORE, FOUL, TIMEOUT, FREE_THROWS, SUBSTITUTION }

/**
 * How the game clock reacts to registered events. Chosen before the game, stored with it
 * ([com.watchthetime.domain.event.GameCreated]) and changeable mid-game through a logged
 * [com.watchthetime.domain.event.ClockPolicyChanged] event.
 *
 * The default is RUNNING with no late stop: that is how games created before clock modes
 * existed behaved, so replaying them gives the same result.
 */
@Serializable
data class ClockPolicy(
    val mode: ClockMode = ClockMode.RUNNING,
    /** STOPPING only: scores pause the clock only inside the preset's late-game window. */
    val scoresStopOnlyLate: Boolean = false,
    /** RUNNING only: inside the preset's late-game window, behave like stopping time. */
    val runningStopsLate: Boolean = false,
) {
    /**
     * Whether registering [trigger] pauses a running clock. The clock never restarts on its
     * own in any mode: only the user (tap on the clock) starts it.
     */
    fun stops(trigger: StopTrigger, rules: Rules, period: Int, clockMs: Long): Boolean {
        val late = rules.inLateWindow(period, clockMs)
        return when (mode) {
            ClockMode.STOPPING -> if (trigger == StopTrigger.SCORE && scoresStopOnlyLate) late else true
            ClockMode.RUNNING -> trigger == StopTrigger.TIMEOUT || (runningStopsLate && late)
        }
    }

    val summary: String
        get() = when (mode) {
            ClockMode.STOPPING -> if (scoresStopOnlyLate) "${mode.label} · scores stop late only" else mode.label
            ClockMode.RUNNING -> if (runningStopsLate) "${mode.label} · stops late in game" else mode.label
        }

    companion object {
        /** Default for newly created games (official-style). */
        val NEW_GAME = ClockPolicy(mode = ClockMode.STOPPING)
    }
}
