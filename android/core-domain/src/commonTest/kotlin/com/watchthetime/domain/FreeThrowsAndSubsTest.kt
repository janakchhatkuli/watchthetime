package com.watchthetime.domain

import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.engine.EventText
import com.watchthetime.domain.event.ClockStopped
import com.watchthetime.domain.event.FreeThrows
import com.watchthetime.domain.event.Score
import com.watchthetime.domain.event.ShotType
import com.watchthetime.domain.model.TeamSide.AWAY
import com.watchthetime.domain.model.TeamSide.HOME
import com.watchthetime.domain.rules.ClockMode
import com.watchthetime.domain.rules.ClockPolicy
import kotlin.test.*

class FreeThrowsAndSubsTest {

    @Test
    fun freeThrowSetAddsMadePointsToTeamAndPlayer() {
        val (s, _) = Fixtures.session()
        val out = s.dispatch(Command.AddFreeThrows(HOME, "h4", attempts = 3, made = 2))
        assertTrue(out.ok)
        assertEquals(CueType.FREE_THROWS, out.cues.first().type)
        assertEquals(2, s.state.team(HOME).score)
        val p = s.state.player("h4")!!
        assertEquals(2, p.points)
        assertEquals(2, p.ftMade)
        assertEquals(3, p.ftAttempted)
        val e = s.allEvents.last { it.payload is FreeThrows }
        assertEquals("Free throws 2/3 HOME #4 Ava", EventText.describe(e, s.state))
    }

    @Test
    fun freeThrowValidation() {
        val (s, _) = Fixtures.session()
        assertFalse(s.dispatch(Command.AddFreeThrows(HOME, null, 0, 0)).ok)
        assertFalse(s.dispatch(Command.AddFreeThrows(HOME, null, 2, 3)).ok)
        assertFalse(s.dispatch(Command.AddFreeThrows(HOME, "a0", 2, 1)).ok, "shooter must be on that team")
        assertEquals(0, s.state.team(HOME).score)
    }

    @Test
    fun freeThrowEditAndUndoRecompute() {
        val (s, _) = Fixtures.session()
        val ev = s.dispatch(Command.AddFreeThrows(AWAY, "a11", 2, 2)).changed.single { it.payload is FreeThrows }
        s.dispatch(Command.EditEvent(ev.id, FreeThrows(AWAY, "a11", 2, 1)))
        assertEquals(1, s.state.team(AWAY).score)
        assertEquals(1, s.state.player("a11")!!.ftMade)
        s.dispatch(Command.Undo)
        assertEquals(2, s.state.team(AWAY).score)
        s.dispatch(Command.DeleteEvent(ev.id))
        assertEquals(0, s.state.team(AWAY).score)
        assertEquals(0, s.state.player("a11")!!.ftAttempted)
    }

    @Test
    fun scoreShotTypesShowInTheLog() {
        val (s, _) = Fixtures.session()
        s.dispatch(Command.AddScore(HOME, 1, "h7", ShotType.FREE_THROW))
        s.dispatch(Command.AddScore(HOME, 3, null, ShotType.ARC))
        s.dispatch(Command.AddScore(HOME, 2))
        val texts = s.allEvents.filter { it.payload is Score }.map { EventText.describe(it, s.state) }
        assertEquals(listOf("+1 FT HOME #7 Bo", "+3 3PT HOME", "+2 HOME"), texts)
        assertEquals(1, s.state.player("h7")!!.ftMade)
        assertEquals(ShotType.FREE_THROW, Score(HOME, 1).shotOf(), "old 5-on-5 +1 events are free throws")
    }

    @Test
    fun scoreResetsShotClockInEveryFormat() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.StartClock); t.advance(5_000); s.tick()
        assertTrue(s.state.shot.at(t.now()) < 24_000, "shot clock has run down")
        s.dispatch(Command.AddScore(HOME, 2, "h4"))
        assertEquals(24_000, s.state.shot.baseMs, "made basket resets the shot clock")
        assertTrue(s.state.shot.running || !s.state.clock.running, "shot clock is live again (or the game clock stopped)")

        s.dispatch(Command.StartClock); t.advance(3_000); s.tick()
        s.dispatch(Command.AddScore(AWAY, 3, "a11", ShotType.ARC))
        assertEquals(24_000, s.state.shot.baseMs, "3-pointer too")

        // Free-throw set with a make resets; all misses do not.
        s.dispatch(Command.StartClock); t.advance(2_000); s.tick()
        s.dispatch(Command.AddFreeThrows(HOME, "h4", attempts = 2, made = 1))
        assertEquals(24_000, s.state.shot.baseMs, "made free throw resets")
        s.dispatch(Command.StartClock); t.advance(2_000); s.tick()
        val remaining = s.state.shot.at(t.now())
        s.dispatch(Command.AddFreeThrows(AWAY, "a0", attempts = 2, made = 0))
        assertTrue(s.state.shot.at(t.now()) < 24_000, "all-missed free throws leave the shot clock alone")
        assertTrue(s.state.shot.at(t.now()) <= remaining, "no reset on a miss")
        assertEquals(remaining, s.state.shot.at(t.now()), "unchanged after 0/2")
    }

    @Test
    fun substitutionIsLoggedAndValidated() {
        val (s, _) = Fixtures.session()
        assertTrue(s.dispatch(Command.AddSubstitution(HOME, "h4", "h7")).ok)
        assertTrue(s.allEvents.any { EventText.describe(it, s.state) == "Substitution HOME in #4 Ava out #7 Bo" })
        assertFalse(s.dispatch(Command.AddSubstitution(HOME, "a0", null)).ok)
        assertFalse(s.dispatch(Command.AddSubstitution(HOME, "h4", "h4")).ok)
    }

    @Test
    fun stoppingTimePausesOnFreeThrowsAndSubstitution() {
        val (s, t) = Fixtures.session(policy = ClockPolicy(ClockMode.STOPPING))
        s.dispatch(Command.StartClock); t.advance(1_000)
        s.dispatch(Command.AddSubstitution(HOME))
        assertFalse(s.state.clock.running, "substitution pauses")
        s.dispatch(Command.StartClock); t.advance(1_000)
        s.dispatch(Command.AddFreeThrows(AWAY, null, 2, 1))
        assertFalse(s.state.clock.running, "free throws pause")
        assertEquals(2, s.allEvents.count { (it.payload as? ClockStopped)?.auto == true })
    }

    @Test
    fun runningTimeIgnoresFreeThrowsAndSubstitution() {
        val (s, t) = Fixtures.session(policy = ClockPolicy(ClockMode.RUNNING))
        s.dispatch(Command.StartClock); t.advance(1_000)
        s.dispatch(Command.AddSubstitution(HOME))
        s.dispatch(Command.AddFreeThrows(AWAY, null, 2, 2))
        assertTrue(s.state.clock.running)
    }
}
