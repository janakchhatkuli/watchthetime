package com.watchthetime.domain

import com.watchthetime.domain.engine.*
import com.watchthetime.domain.event.*
import com.watchthetime.domain.model.FoulType
import com.watchthetime.domain.model.PlayerInfo
import com.watchthetime.domain.model.TeamSide.AWAY
import com.watchthetime.domain.model.TeamSide.HOME
import com.watchthetime.domain.rules.RulePreset
import com.watchthetime.domain.rules.Rules
import com.watchthetime.domain.stats.BoxScoreBuilder
import com.watchthetime.domain.stats.CsvExport
import com.watchthetime.domain.sync.CommandEnvelope
import com.watchthetime.domain.sync.EventBatch
import com.watchthetime.domain.sync.WireJson
import kotlin.test.*

class EditUndoRecomputeTest {

    @Test
    fun editingAScoreRecomputesTotalsAndBoxScore() {
        val (s, _) = Fixtures.session()
        val id = s.dispatch(Command.AddScore(HOME, 2, "h23")).changed.first { it.payload is Score }.id
        s.dispatch(Command.AddScore(HOME, 3, "h4"))
        assertEquals(5, s.state.team(HOME).score)
        val edited = s.dispatch(Command.EditEvent(id, Score(AWAY, 3, "a11")))
        assertTrue(edited.cues.any { it.type == CueType.EDIT_SAVED })
        assertEquals(3, s.state.team(HOME).score)
        assertEquals(3, s.state.team(AWAY).score)
        assertEquals(0, s.state.player("h23")!!.points)
        assertEquals(3, s.state.player("a11")!!.points)
        assertEquals(2, s.event(id)!!.rev)
        val box = BoxScoreBuilder.build(s.state)
        assertEquals(3, box.away.total)
        assertEquals(3, box.away.byPeriod[0])
        assertEquals(0, box.home.unassignedPoints)
    }

    @Test
    fun assignScorerAfterTheFact() {
        val (s, _) = Fixtures.session()
        val id = s.dispatch(Command.AddScore(AWAY, 2)).changed.first { it.payload is Score }.id
        assertEquals(2, BoxScoreBuilder.build(s.state).away.unassignedPoints)
        s.dispatch(Command.AssignPlayer(id, "a0"))
        assertEquals(2, s.state.player("a0")!!.points)
        assertEquals(0, BoxScoreBuilder.build(s.state).away.unassignedPoints)
    }

    @Test
    fun deletingAFoulReversesFoulOutAndBonus() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.FIBA))
        val ids = (1..5).map { s.dispatch(Command.AddFoul(HOME, "h4")).changed.single().id }
        assertTrue(s.state.player("h4")!!.disqualified(s.state.rules))
        s.dispatch(Command.DeleteEvent(ids[2]))
        assertFalse(s.state.player("h4")!!.disqualified(s.state.rules))
        assertEquals(4, s.state.teamFouls(HOME))
        s.dispatch(Command.RestoreEvent(ids[2]))
        assertTrue(s.state.player("h4")!!.disqualified(s.state.rules))
    }

    @Test
    fun changingFoulTypeOrPeriodRecomputesFlagsAndTeamFouls() {
        val (s, _) = Fixtures.session(Rules.preset(RulePreset.NBA))
        val id = s.dispatch(Command.AddFoul(AWAY, "a0")).changed.single().id
        s.dispatch(Command.NextPeriod)
        assertEquals(0, s.state.teamFouls(AWAY))
        s.dispatch(Command.EditEvent(id, Foul(AWAY, "a0", FoulType.FLAGRANT), period = 2))
        assertEquals(1, s.state.teamFouls(AWAY), "foul moved into Q2")
        assertEquals(listOf(FoulType.FLAGRANT to 1), s.state.player("a0")!!.flagBadges())
    }

    @Test
    fun undoRedoScoreFoulAndClock() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.AddScore(HOME, 3))
        s.dispatch(Command.AddFoul(AWAY, "a11", FoulType.TECHNICAL))
        assertEquals(3, s.state.team(HOME).score)
        assertEquals(CueType.UNDO, s.undo().cues.single().type)
        assertEquals(0, s.state.player("a11")!!.technicals)
        s.undo()
        assertEquals(0, s.state.team(HOME).score)
        assertFalse(s.undo().ok, "nothing left")
        s.redo()
        assertEquals(3, s.state.team(HOME).score)
        s.redo()
        assertEquals(1, s.state.player("a11")!!.technicals)

        // Undo of an accidental clock start puts the clock back where it was.
        s.dispatch(Command.StartClock)
        t.advance(4_000)
        s.dispatch(Command.Undo)
        assertFalse(s.state.clock.running)
        assertEquals(720_000, s.state.clock.at(t.now()))
    }

    @Test
    fun undoOfAnEditRestoresPreviousVersionWithHigherRevision() {
        val (s, _) = Fixtures.session()
        val id = s.dispatch(Command.AddScore(HOME, 2)).changed.first { it.payload is Score }.id
        s.dispatch(Command.EditEvent(id, Score(HOME, 3)))
        assertEquals(3, s.state.team(HOME).score)
        val undone = s.undo().changed.single()
        assertEquals(2, s.state.team(HOME).score)
        assertEquals(3, undone.rev, "undo writes a new revision so other devices pick it up")
        s.redo()
        assertEquals(3, s.state.team(HOME).score)
    }

    @Test
    fun newActionClearsRedo() {
        val (s, _) = Fixtures.session()
        s.dispatch(Command.AddScore(HOME, 1))
        s.undo()
        assertTrue(s.canRedo)
        s.dispatch(Command.AddScore(AWAY, 1))
        assertFalse(s.canRedo)
    }

    @Test
    fun failedCommandsLeaveNoTrace() {
        val (s, _) = Fixtures.session()
        val before = s.allEvents.size
        assertFalse(s.dispatch(Command.StopClock).ok)
        assertFalse(s.dispatch(Command.UpsertPlayer(PlayerInfo("x", HOME, "4", "Dup"))).ok, "#4 taken")
        assertFalse(s.dispatch(Command.UpsertPlayer(PlayerInfo("y", HOME, "123"))).ok)
        assertEquals(before, s.allEvents.size)
        assertFalse(s.canUndo)
    }

    @Test
    fun rosterEditsMidGameKeepHistory() {
        val (s, _) = Fixtures.session()
        s.dispatch(Command.AddFoul(HOME, "h7"))
        s.dispatch(Command.UpsertPlayer(PlayerInfo("h7", HOME, "8", "Bo Renamed")))
        val p = s.state.player("h7")!!
        assertEquals("8", p.info.number)
        assertEquals(1, p.fouls.size, "fouls follow the player, not the number")
        s.dispatch(Command.RemovePlayer("h7"))
        assertTrue(s.state.roster(HOME).none { it.id == "h7" })
        assertEquals(1, BoxScoreBuilder.build(s.state).home.lines.single { it.playerId == "h7" }.personal)
    }

    @Test
    fun replayIsDeterministicAcrossDevices() {
        val (s, t) = Fixtures.session()
        s.dispatch(Command.StartClock); t.advance(12_345)
        s.dispatch(Command.AddScore(HOME, 2, "h4"))
        s.dispatch(Command.AddFoul(AWAY, "a0"))
        s.dispatch(Command.StopClock)
        val json = WireJson.encodeToString(EventBatch.serializer(), EventBatch("g1", s.allEvents))
        val decoded = WireJson.decodeFromString(EventBatch.serializer(), json)
        val phone = GameSession("g1", decoded.events, FakeTime(boot = 99), "phone")
        assertEquals(s.state, phone.state)
    }

    @Test
    fun offlineEditsFromBothDevicesConverge() {
        val (watch, _) = Fixtures.session()
        val id = watch.dispatch(Command.AddScore(HOME, 2)).changed.first { it.payload is Score }.id
        val phone = GameSession("g1", watch.allEvents, FakeTime(), "phone")

        // Disconnected: phone edits the score, watch keeps scoring.
        val phoneEdit = phone.dispatch(Command.EditEvent(id, Score(HOME, 3))).changed
        val watchNew = watch.dispatch(Command.AddScore(AWAY, 1)).changed

        // Reconnect: exchange.
        watch.mergeRemote(phoneEdit)
        phone.mergeRemote(watchNew)
        assertEquals(watch.state.team(HOME).score, phone.state.team(HOME).score)
        assertEquals(3, watch.state.team(HOME).score)
        assertEquals(1, phone.state.team(AWAY).score)
        assertTrue(watch.mergeRemote(phoneEdit).changed.isEmpty(), "idempotent")
    }

    @Test
    fun commandsSurviveTheWire() {
        val cmd = CommandEnvelope("g1", "r1", Command.EditEvent("e9", Foul(HOME, "h4", FoulType.UNSPORTSMANLIKE), period = 2))
        val back = WireJson.decodeFromString(CommandEnvelope.serializer(), WireJson.encodeToString(CommandEnvelope.serializer(), cmd))
        assertEquals(cmd, back)
    }

    @Test
    fun csvExportContainsBoxAndEvents() {
        val (s, _) = Fixtures.session()
        s.dispatch(Command.AddScore(HOME, 3, "h23"))
        s.dispatch(Command.AddFoul(AWAY, "a00", FoulType.TECHNICAL))
        val csv = CsvExport.full(s.allEvents, s.state)
        assertTrue(csv.contains("HOME,23,Cy,3,0,0,1"))
        assertTrue(csv.contains("Technical foul AWAY #00 Eli"))
    }
}
