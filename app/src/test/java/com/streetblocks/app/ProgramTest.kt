package com.streetblocks.app

import com.streetblocks.app.data.model.Block
import com.streetblocks.app.data.model.BlockType
import com.streetblocks.app.data.model.DaySlot
import com.streetblocks.app.data.model.EditScope
import com.streetblocks.app.data.model.PlanChange
import com.streetblocks.app.data.model.PlanStatus
import com.streetblocks.app.data.model.PlannedSession
import com.streetblocks.app.data.model.Planning
import com.streetblocks.app.data.model.ProgramLength
import com.streetblocks.app.data.model.ProgramOps
import com.streetblocks.app.data.model.ProgramSpec
import com.streetblocks.app.data.model.SessionContent
import com.streetblocks.app.data.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class ProgramTest {
    private val start = LocalDate.of(2026, 10, 5) // lundi
    private val pull = Block(type = BlockType.PULLUP, exercise = "TRACTION", sets = 5, reps = 5)
    private val dips = Block(type = BlockType.DIPS, exercise = "DIPS", sets = 4, reps = 8)

    /** Lundi / mercredi / vendredi pendant deux mois, une séance différente par jour. */
    private fun twoMonths(): List<PlannedSession> {
        val spec = ProgramSpec(
            "Force", "Force", start, ProgramLength.TWO_MONTHS.endFor(start),
            listOf(DaySlot(MONDAY, SessionSource.Empty, 18 * 60), DaySlot(WEDNESDAY, SessionSource.Empty), DaySlot(FRIDAY, SessionSource.Empty)),
        )
        var id = 1L
        return ProgramOps.generate(1, spec) { slot ->
            when (slot.day) {
                MONDAY -> SessionContent("Tractions", listOf(pull), 10)
                else -> SessionContent("Dips", listOf(dips), null)
            }
        }.map { it.copy(id = id++) }
    }

    private fun List<PlannedSession>.apply(ch: PlanChange): List<PlannedSession> {
        var next = 1000L
        val updated = ch.update.associateBy { it.id }
        return filter { it.id !in ch.delete }.map { updated[it.id] ?: it } + ch.insert.map { it.copy(id = next++) }
    }

    @Test
    fun durations() {
        assertEquals(LocalDate.of(2026, 10, 11), ProgramLength.ONE_WEEK.endFor(start))
        assertEquals(LocalDate.of(2026, 10, 18), ProgramLength.TWO_WEEKS.endFor(start))
        assertEquals(LocalDate.of(2026, 11, 4), ProgramLength.ONE_MONTH.endFor(start))
        assertEquals(LocalDate.of(2026, 12, 4), ProgramLength.TWO_MONTHS.endFor(start))
    }

    @Test
    fun weeklyOrganisationIsRepeatedOverThePeriod() {
        val all = twoMonths()
        // 5 oct → 4 déc : 9 lundis, 9 mercredis, 9 vendredis
        assertEquals(27, all.size)
        assertTrue(all.all { it.date.dayOfWeek in setOf(MONDAY, WEDNESDAY, FRIDAY) })
        assertTrue(all.filter { it.date.dayOfWeek == MONDAY }.all { it.timeMinutes == 18 * 60 && it.name == "Tractions" })
        assertEquals(3, all.map { it.seriesId }.distinct().size)
    }

    @Test
    fun editThisOnlyVsFollowing() {
        val all = twoMonths()
        val mondays = all.filter { it.date.dayOfWeek == MONDAY }
        val third = mondays[2]
        val heavier: (PlannedSession) -> PlannedSession = { p -> p.copy(blocks = p.blocks.map { it.copy(vestKg = 10) }) }

        val only = all.apply(ProgramOps.edit(all, third, EditScope.THIS, heavier))
        assertEquals(1, only.count { p -> p.blocks.any { it.vestKg == 10 } })

        val following = all.apply(ProgramOps.edit(all, third, EditScope.FOLLOWING, heavier))
        val changed = following.filter { p -> p.blocks.any { it.vestKg == 10 } }
        assertEquals(7, changed.size) // 3e lundi + 6 suivants
        assertTrue(changed.all { it.date.dayOfWeek == MONDAY && !it.date.isBefore(third.date) })
    }

    @Test
    fun doneSessionsAreNeverModified() {
        var all = twoMonths()
        val first = all.first { it.date.dayOfWeek == MONDAY }
        val second = all.filter { it.date.dayOfWeek == MONDAY }[1]
        all = all.map { if (it.id == second.id) it.copy(status = PlanStatus.DONE, sessionId = 42) else it }
        val res = all.apply(ProgramOps.edit(all, first, EditScope.FOLLOWING) { it.copy(name = "Nouveau") })
        val done = res.first { it.id == second.id }
        assertEquals("Tractions", done.name)
        assertEquals(PlanStatus.DONE, done.status)
        assertEquals(42L, done.sessionId)
        // Déplacer / supprimer : la séance réalisée reste en place
        val moved = all.apply(ProgramOps.move(all, first, first.date.plusDays(1), EditScope.FOLLOWING))
        assertEquals(second.date, moved.first { it.id == second.id }.date)
        val deleted = all.apply(ProgramOps.delete(all, first, EditScope.FOLLOWING))
        assertTrue(deleted.any { it.id == second.id })
        // Et on ne peut pas la modifier directement
        assertEquals(0, ProgramOps.edit(all, all.first { it.id == second.id }, EditScope.THIS) { it.copy(name = "x") }.count)
    }

    @Test
    fun moveThisAndFollowingShiftsByTheSameDelta() {
        val all = twoMonths()
        val wed = all.filter { it.date.dayOfWeek == WEDNESDAY }[4]
        val res = all.apply(ProgramOps.move(all, wed, wed.date.plusDays(1), EditScope.FOLLOWING))
        val thursdays = res.filter { it.date.dayOfWeek == java.time.DayOfWeek.THURSDAY }
        assertEquals(5, thursdays.size) // 5e à 9e mercredi → jeudi
        assertEquals(4, res.count { it.date.dayOfWeek == WEDNESDAY })
        val single = all.apply(ProgramOps.move(all, wed, wed.date.plusDays(2), EditScope.THIS))
        assertEquals(1, single.count { it.date.dayOfWeek == FRIDAY && it.name == "Dips" && it.seriesId == wed.seriesId })
    }

    @Test
    fun duplicateAndRepeatWeek() {
        val all = twoMonths()
        val week2 = start.plusWeeks(1)
        val dup = ProgramOps.duplicateWeek(all, week2, start.plusWeeks(10))
        assertEquals(3, dup.insert.size)
        assertTrue(dup.insert.all { Planning.weekStart(it.date) == start.plusWeeks(10) })

        // Personnaliser la semaine 1, puis la répéter : les semaines suivantes prennent la même organisation
        val customized = all.map { if (it.date == start) it.copy(name = "Tractions lourdes") else it }
        val repeated = customized.apply(ProgramOps.repeatWeek(customized, start, ProgramLength.TWO_MONTHS.endFor(start)))
        assertEquals(27, repeated.size)
        assertTrue(repeated.filter { it.date.dayOfWeek == MONDAY }.all { it.name == "Tractions lourdes" })
    }

    @Test
    fun duplicateSessionIsIndependent() {
        val all = twoMonths()
        val ch = ProgramOps.duplicateTo(all.first(), start.plusDays(1))
        val copy = ch.insert.single()
        assertEquals(start.plusDays(1), copy.date)
        assertTrue(copy.seriesId != all.first().seriesId)
        assertEquals(PlanStatus.PLANNED, copy.status)
    }

    @Test
    fun progressionOnlyTouchesFollowingOccurrencesOfTheSlot() {
        val all = twoMonths()
        val done = all.first { it.date.dayOfWeek == MONDAY }
        val key = com.streetblocks.app.data.model.BlockText.progressKey(pull)
        val ch = ProgramOps.applyProgression(all, done, key) { it.copy(reps = it.reps + 1) }
        assertEquals(8, ch.update.size)
        assertTrue(ch.update.all { p -> p.blocks.all { it.reps == 6 } && p.date.isAfter(done.date) })
    }

    @Test
    fun statsAndLateSessions() {
        val all = twoMonths().mapIndexed { i, p -> if (i < 3) p.copy(status = PlanStatus.DONE) else p }
        val st = Planning.stats(all, today = start.plusDays(9))
        assertEquals(3, st.done)
        assertEquals(1, st.late) // lundi 12 oct. non fait
        assertEquals(start.plusDays(9), st.next?.date)
    }
}
