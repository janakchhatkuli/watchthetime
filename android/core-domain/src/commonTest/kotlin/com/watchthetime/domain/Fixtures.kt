package com.watchthetime.domain

import com.watchthetime.domain.engine.GameSession
import com.watchthetime.domain.engine.TimeSource
import com.watchthetime.domain.event.Stamp
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamInfo
import com.watchthetime.domain.model.TeamSide
import com.watchthetime.domain.rules.Rules

/** Controllable clock for deterministic tests. */
class FakeTime(var mono: Long = 1_000_000, var wall: Long = 1_700_000_000_000, var boot: Int = 1) : TimeSource {
    override fun now() = Stamp(mono, wall, boot)
    fun advance(ms: Long) { mono += ms; wall += ms }
    fun reboot(newMono: Long = 5_000) { boot += 1; mono = newMono }
}

object Fixtures {
    val homePlayers = listOf(
        PlayerInfo("h4", TeamSide.HOME, "4", "Ava"),
        PlayerInfo("h7", TeamSide.HOME, "7", "Bo"),
        PlayerInfo("h23", TeamSide.HOME, "23", "Cy"),
    )
    val awayPlayers = listOf(
        PlayerInfo("a0", TeamSide.AWAY, "0", "Dee"),
        PlayerInfo("a00", TeamSide.AWAY, "00", "Eli"),
        PlayerInfo("a11", TeamSide.AWAY, "11", "Fay"),
    )

    fun session(rules: Rules = Rules(), time: FakeTime = FakeTime()): Pair<GameSession, FakeTime> {
        var n = 0
        val s = GameSession("g1", emptyList(), time, "watch", newId = { "e${++n}" })
        s.create(rules, TeamInfo.default(TeamSide.HOME), TeamInfo.default(TeamSide.AWAY), homePlayers + awayPlayers, "Test")
        return s to time
    }
}
