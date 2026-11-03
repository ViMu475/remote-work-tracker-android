package com.remotework.tracker

import android.content.Context
import android.content.SharedPreferences
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Repository tests; the Android LocalStorage runs on an in-memory SharedPreferences fake. */
class TrackerRepositoryTest {

    private val tol = 1e-9

    // --- fake storage ---------------------------------------------------------------------------

    private val store = mutableMapOf<String, String>()

    private fun newRepository(): TrackerRepository {
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(any(), any())).thenAnswer {
            store[it.arguments[0] as String] = it.arguments[1] as String
            editor
        }
        `when`(editor.remove(any())).thenAnswer {
            store.remove(it.arguments[0] as String)
            editor
        }
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString(any(), any())).thenAnswer { store[it.arguments[0] as String] }
        `when`(prefs.edit()).thenReturn(editor)
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(prefs)
        return TrackerRepository(LocalStorage(context))
    }

    private val repo = newRepository()

    /** October 2025: 22 working days, 176 h, 40 % -> 70.4 h remote. */
    private fun october2025(
        quote: Double = 40.0,
        history: List<MonthData> = emptyList(),
        entries: Map<String, List<WorkEntry>> = emptyMap()
    ) = repo.recalculateMonthlyTotals(
        TrackerState(
            aktuellerMonat = "Oktober",
            aktuellesJahr = 2025,
            currentMonthData = MonthData("Oktober", 2025, quote, 176.0, 70.4, 0.0, 0.0),
            currentQuartal = "Q4/2025",
            dailyRecords = entries,
            monatlicheHistorie = history,
            individuelleQuoteProzent = quote
        )
    )

    private fun TrackerState.add(
        datum: String, start: String = "08:00", end: String = "16:30",
        pause: Double = 30.0, remote: Double? = null
    ): TrackerState {
        val r = repo.addWorkEntry(this, datum, start, end, pause, remote)
        return assertNotNull(r.state, "add failed: ${r.error}")
    }

    // --- several months: the numbers must follow every entry and every switch ---------------------

    /** Sums of the entries of one month, computed independently of the repository. */
    private fun sums(s: TrackerState, year: Int, month: Int): Pair<Double, Double> {
        val prefix = "%04d-%02d-".format(year, month)
        val all = s.dailyRecords.filterKeys { it.startsWith(prefix) }.values.flatten()
        return all.sumOf { it.remoteStundenHeute } to all.sumOf { it.totalStundenHeute }
    }

    private fun assertShows(s: TrackerState, year: Int, month: Int) {
        val (remote, total) = sums(s, year, month)
        assertEquals(MonthNames.getMonthName(month), s.aktuellerMonat)
        assertEquals(year, s.aktuellesJahr)
        assertEquals(remote, s.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals(total, s.currentMonthData.istArbeitszeitLaufend, tol)
        val soll = TrackerCalculations.calculateSollStundenMonth(year, month, s.sollStundenProTag)
        assertEquals(soll, s.currentMonthData.sollStundenGesamt, tol)
        assertEquals(soll * s.individuelleQuoteProzent / 100.0, s.currentMonthData.maxRemoteStunden, tol)
    }

    @Test
    fun severalMonths_valuesFollowEveryEntryAndEverySwitch() {
        var s = october2025()
        s = s.add("2025-10-06").add("2025-10-07", remote = 4.0)
        assertShows(s, 2025, 10)

        s = repo.switchToMonth(s, 2025, 9)
        assertShows(s, 2025, 9)                       // September is still empty
        s = s.add("2025-09-01").add("2025-09-02").add("2025-09-03", remote = 0.0)
        assertShows(s, 2025, 9)

        s = repo.switchToMonth(s, 2025, 8)
        assertShows(s, 2025, 8)
        s = s.add("2025-08-04")
        assertShows(s, 2025, 8)

        s = repo.switchToMonth(s, 2025, 10)           // back to the first month
        assertShows(s, 2025, 10)
        s = repo.switchToMonth(s, 2025, 9)
        assertShows(s, 2025, 9)
        assertEquals(s, repo.recalculateMonthlyTotals(s))  // recalculating changes nothing
    }

    @Test
    fun importedEntry_hasNoPauseWarning_manualEntryStillHasOne() {
        // A statement lists sessions without breaks (pause 0); a break warning would be wrong for each of them.
        val imported = assertNotNull(repo.importWorkEntry(october2025(), "2025-10-06", "08:00", "16:30", 0.0, null).state)
        val manual = assertNotNull(repo.addWorkEntry(october2025(), "2025-10-06", "08:00", "16:30", 0.0, null).state)
        val i = imported.dailyRecords.getValue("2025-10-06").single()
        val m = manual.dailyRecords.getValue("2025-10-06").single()
        assertEquals(SOURCE_PDF_IMPORT, i.source)
        assertNull(i.pauseWarning)
        assertNull(m.source)
        assertNotNull(m.pauseWarning)
        assertEquals(m.totalStundenHeute, i.totalStundenHeute, tol)       // only the warning differs
    }

    @Test
    fun history_loadMonthsBackwardCloseForwardReviewBackward() {
        // Jan 2025 .. Oct 2026 (22 months): load newest first, close oldest first, review newest first.
        val months = (0 until 22).map { 2025 + it / 12 to it % 12 + 1 }
        var s = october2025()
        val expected = mutableMapOf<Pair<Int, Int>, Pair<Double, Double>>()   // month -> (remote, total)

        for ((y, m) in months.reversed()) {                                   // backward: load
            s = repo.switchToMonth(s, y, m)
            assertShows(s, y, m)
            var added = 0
            for (day in 1..28) {
                if (added == 3) break
                val r = repo.addWorkEntry(s, "%04d-%02d-%02d".format(y, m, day), "08:00", "16:30", 30.0, if (day % 2 == 0) 0.0 else null)
                if (r.state != null) { s = r.state!!; added++ }
            }
            assertEquals(3, added, "$y-$m")
            assertShows(s, y, m)
            expected[y to m] = sums(s, y, m)
            for ((k, v) in expected) {                                        // loading must never change another month
                assertEquals(v, sums(s, k.first, k.second), "month $k changed after loading $y-$m")
            }
        }

        for ((i, ym) in months.withIndex()) {                                 // forward: close month after month
            val (y, m) = ym
            s = repo.switchToMonth(s, y, m)
            assertShows(s, y, m)
            val before = s.monatlicheHistorie.size
            val (closed, _) = repo.closeSpecificMonth(s, y, m)
            s = closed
            assertEquals(before + 1, s.monatlicheHistorie.size, "history after closing $y-$m")
            val snap = s.monatlicheHistorie.single { it.jahr == y && it.monat == MonthNames.getMonthName(m) }
            assertEquals(expected[ym]!!.first, snap.istRemoteStundenLaufend, tol)
            assertEquals(expected[ym]!!.second, snap.istArbeitszeitLaufend, tol)
            assertEquals(0.0 to 0.0, sums(s, y, m))                           // its entries are gone
            for (later in months.drop(i + 1)) {                               // later months untouched
                assertEquals(expected[later], sums(s, later.first, later.second), "month $later after closing $y-$m")
            }
            val (again, _) = repo.closeSpecificMonth(s, y, m)                 // closing twice: no duplicate
            assertEquals(s.monatlicheHistorie, again.monatlicheHistorie)
            assertEquals(
                s.monatlicheHistorie.sortedWith(compareBy({ it.jahr }, { MonthNames.getMonthNumber(it.monat) })),
                s.monatlicheHistorie, "history stays sorted"
            )
        }
        assertEquals(22, s.monatlicheHistorie.size)
        assertEquals(22, s.monatlicheHistorie.map { it.jahr to it.monat }.toSet().size)

        for ((y, m) in months.reversed()) {                                   // backward: review closed months
            s = repo.switchToMonth(s, y, m)
            assertShows(s, y, m)
            assertEquals(expected[y to m]!!.second,
                s.monatlicheHistorie.single { it.jahr == y && it.monat == MonthNames.getMonthName(m) }.istArbeitszeitLaufend, tol)
        }
        s = repo.clearAllHistory(s)
        assertTrue(s.monatlicheHistorie.isEmpty())
    }

    // --- load / save ----------------------------------------------------------------------------

    @Test
    fun loadState_withoutStoredDataGivesDefaults() {
        val s = repo.loadState()
        val today = TrackerCalculations.getCurrentDate()
        assertEquals(MonthNames.getMonthName(today.monthNumber), s.aktuellerMonat)
        assertEquals(today.year, s.aktuellesJahr)
        assertEquals(40.0, s.individuelleQuoteProzent, tol)
        assertEquals(8.0, s.sollStundenProTag, tol)
        assertTrue(s.dailyRecords.isEmpty())
        assertTrue(s.monatlicheHistorie.isEmpty())
    }

    @Test
    fun saveAndLoad_roundTripsTheState() {
        val s = october2025().add("2025-10-06", remote = 4.0)
        repo.saveState(s)
        assertEquals(s, repo.loadState())
    }

    @Test
    fun loadState_corruptDataFallsBackToDefaults() {
        store["tracker_state"] = "{ this is not json"
        val s = repo.loadState()
        assertTrue(s.dailyRecords.isEmpty())
        assertEquals(40.0, s.individuelleQuoteProzent, tol)
    }

    @Test
    fun clearAll_removesStoredState() {
        repo.saveState(october2025().add("2025-10-06"))
        repo.clearAll()
        assertTrue(repo.loadState().dailyRecords.isEmpty())
    }

    // --- addWorkEntry ---------------------------------------------------------------------------

    @Test
    fun add_validEntryUpdatesTotalsAndQuote() {
        val s = october2025().add("2025-10-06")
        val m = s.currentMonthData
        assertEquals(8.0, m.istRemoteStundenLaufend, tol)
        assertEquals(8.0, m.istArbeitszeitLaufend, tol)
        assertEquals(8.0 / 176.0 * 100, m.tatsaechlicheRemoteQuote, 1e-6)
        assertEquals(1, s.dailyRecords["2025-10-06"]!!.size)
    }

    @Test
    fun add_explicitRemoteHoursAreKeptSeparateFromWorkHours() {
        val s = october2025().add("2025-10-06", remote = 4.0)
        assertEquals(4.0, s.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals(8.0, s.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun add_rejectsHolidayWeekendAndBadDate() {
        val base = october2025()
        assertNotNull(repo.addWorkEntry(base, "2025-10-03", "08:00", "16:30", 30.0, null).error)
        assertNotNull(repo.addWorkEntry(base, "2025-10-04", "08:00", "16:30", 30.0, null).error)
        assertNotNull(repo.addWorkEntry(base, "2025-10-05", "08:00", "16:30", 30.0, null).error)
        assertEquals("Ungültiges Datumsformat.", repo.addWorkEntry(base, "kaputt", "08:00", "16:30", 30.0, null).error)
    }

    @Test
    fun add_rejectsOverlapOnTheSameDay() {
        val s = october2025().add("2025-10-06", "08:00", "12:00", 0.0)
        val r = repo.addWorkEntry(s, "2025-10-06", "11:00", "13:00", 0.0, null)
        assertNull(r.state)
        assertTrue(r.error!!.contains("Zeitüberlappung"))
    }

    @Test
    fun add_allowsASecondNonOverlappingEntryOnTheSameDay() {
        val s = october2025()
            .add("2025-10-06", "08:00", "12:00", 0.0)
            .add("2025-10-06", "13:00", "17:00", 0.0)
        assertEquals(2, s.dailyRecords["2025-10-06"]!!.size)
        assertEquals(8.0, s.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun add_rejectsRemoteHoursAboveNet() {
        val r = repo.addWorkEntry(october2025(), "2025-10-06", "08:00", "16:30", 30.0, 9.0)
        assertTrue(r.error!!.startsWith("Remote-Stunden"))
    }

    @Test
    fun add_rejectsPauseLongerThanTheWorkedTime() {
        val r = repo.addWorkEntry(october2025(), "2025-10-06", "08:00", "08:30", 60.0, null)
        assertEquals("Ungültige Zeitangabe!", r.error)
    }

    @Test
    fun add_storesPauseWarningOnTheEntry() {
        val s = october2025().add("2025-10-06", "08:00", "17:00", 30.0)
        assertTrue(s.dailyRecords["2025-10-06"]!![0].pauseWarning!!.startsWith("WARNUNG"))
    }

    @Test
    fun add_entryInAnotherMonthDoesNotChangeTheCurrentMonthTotals() {
        val s = october2025().add("2025-11-03")
        assertEquals(0.0, s.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals(1, s.dailyRecords.size)
    }

    // --- input validation -----------------------------------------------------------------------

    private fun addError(start: String = "08:00", end: String = "16:30", pause: Double = 30.0, remote: Double? = null) =
        repo.addWorkEntry(october2025(), "2025-10-06", start, end, pause, remote).error

    @Test
    fun add_rejectsMalformedTimes() {
        for (bad in listOf("abc", "99:99", "24:00", "8:00", "", "08:60")) {
            assertEquals("Ungültige Zeitangabe!", addError(start = bad), "start '$bad'")
            assertEquals("Ungültige Zeitangabe!", addError(end = bad), "end '$bad'")
        }
    }

    @Test
    fun add_rejectsAnEntryThatStartsAndEndsAtTheSameTime() {
        assertEquals("Ungültige Zeitangabe!", addError(start = "09:00", end = "09:00", pause = 0.0))
    }

    @Test
    fun add_rejectsNonFiniteAndNegativeNumbers() {
        assertEquals("Ungültige Pause!", addError(pause = Double.NaN))
        assertEquals("Ungültige Pause!", addError(pause = Double.POSITIVE_INFINITY))
        assertEquals("Ungültige Pause!", addError(pause = -5.0))
        assertEquals("Ungültige Remote-Stunden!", addError(remote = Double.NaN))
        assertEquals("Ungültige Remote-Stunden!", addError(remote = -1.0))
    }

    @Test
    fun add_withNaNPauseNeverReachesTheStorage() {
        // regression: NaN used to be stored and later crashed saveState (JSON cannot encode NaN)
        val r = repo.addWorkEntry(october2025(), "2025-10-06", "08:00", "16:30", Double.NaN, null)
        assertNull(r.state)
        repo.saveState(october2025()) // still works
    }

    @Test
    fun update_usesTheSameInputValidation() {
        val s = october2025().add("2025-10-06")
        val id = s.dailyRecords["2025-10-06"]!![0].id
        assertEquals("Ungültige Zeitangabe!", repo.updateEntry(s, id, "2025-10-06", "xx:yy", "16:30", 30.0, null).error)
        assertEquals("Ungültige Pause!", repo.updateEntry(s, id, "2025-10-06", "08:00", "16:30", Double.NaN, null).error)
        assertEquals("Ungültige Remote-Stunden!", repo.updateEntry(s, id, "2025-10-06", "08:00", "16:30", 30.0, -2.0).error)
        assertEquals(s, repo.deleteEntry(s, "nope")) // original untouched
    }

    // --- deleteEntry ----------------------------------------------------------------------------

    @Test
    fun delete_removesTheEntryAndTheEmptyDay() {
        val s = october2025().add("2025-10-06")
        val id = s.dailyRecords["2025-10-06"]!![0].id
        val after = repo.deleteEntry(s, id)
        assertFalse("2025-10-06" in after.dailyRecords)
        assertEquals(0.0, after.currentMonthData.istRemoteStundenLaufend, tol)
    }

    @Test
    fun delete_keepsOtherEntriesOfTheSameDay() {
        val s = october2025().add("2025-10-06", "08:00", "12:00", 0.0).add("2025-10-06", "13:00", "17:00", 0.0)
        val first = s.dailyRecords["2025-10-06"]!![0]
        val after = repo.deleteEntry(s, first.id)
        assertEquals(1, after.dailyRecords["2025-10-06"]!!.size)
        assertEquals(4.0, after.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun delete_unknownIdChangesNothing() {
        val s = october2025().add("2025-10-06")
        assertEquals(s, repo.deleteEntry(s, "gibt-es-nicht"))
    }

    // --- updateEntry ----------------------------------------------------------------------------

    @Test
    fun update_changesTimesAndKeepsTheId() {
        val s = october2025().add("2025-10-06")
        val id = s.dailyRecords["2025-10-06"]!![0].id
        val r = repo.updateEntry(s, id, "2025-10-06", "09:00", "15:00", 30.0, null)
        val updated = r.state!!.dailyRecords["2025-10-06"]!!.single()
        assertEquals(id, updated.id)
        assertEquals("09:00", updated.startTime)
        assertEquals(5.5, updated.totalStundenHeute, tol)
        assertEquals(5.5, r.state!!.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun update_canMoveTheEntryToAnotherDay() {
        val s = october2025().add("2025-10-06")
        val id = s.dailyRecords["2025-10-06"]!![0].id
        val moved = repo.updateEntry(s, id, "2025-10-07", "08:00", "16:30", 30.0, null).state!!
        assertFalse("2025-10-06" in moved.dailyRecords)
        assertEquals(id, moved.dailyRecords["2025-10-07"]!!.single().id)
    }

    @Test
    fun update_doesNotCollideWithItself() {
        val s = october2025().add("2025-10-06", "08:00", "12:00", 0.0)
        val id = s.dailyRecords["2025-10-06"]!![0].id
        assertNotNull(repo.updateEntry(s, id, "2025-10-06", "09:00", "11:00", 0.0, null).state)
    }

    @Test
    fun update_rejectsOverlapWithAnotherEntryAndInvalidDate() {
        val s = october2025().add("2025-10-06", "08:00", "12:00", 0.0).add("2025-10-06", "13:00", "17:00", 0.0)
        val second = s.dailyRecords["2025-10-06"]!![1]
        assertNotNull(repo.updateEntry(s, second.id, "2025-10-06", "11:00", "14:00", 0.0, null).error)
        assertNotNull(repo.updateEntry(s, second.id, "2025-10-03", "13:00", "17:00", 0.0, null).error)
    }

    // --- settings -------------------------------------------------------------------------------

    @Test
    fun settings_acceptsTheAllowedRangeAndRecalculates() {
        val r = repo.updateSettings(october2025(), 50.0, 7.5)
        val m = r.state!!.currentMonthData
        assertEquals(165.0, m.sollStundenGesamt, tol)
        assertEquals(82.5, m.maxRemoteStunden, tol)
        assertEquals(50.0, m.individuelleQuote, tol)
    }

    @Test
    fun settings_boundaryValuesAreAccepted() {
        assertNotNull(repo.updateSettings(october2025(), 40.0, 1.0).state)
        assertNotNull(repo.updateSettings(october2025(), 60.0, 10.0).state)
    }

    @Test
    fun settings_nonFiniteValuesAreRejected() {
        assertNotNull(repo.updateSettings(october2025(), Double.NaN, 8.0).error)
        assertNotNull(repo.updateSettings(october2025(), 40.0, Double.NaN).error)
        assertNotNull(repo.updateSettings(october2025(), Double.POSITIVE_INFINITY, 8.0).error)
    }

    @Test
    fun settings_outOfRangeIsRejected() {
        assertNotNull(repo.updateSettings(october2025(), 39.9, 8.0).error)
        assertNotNull(repo.updateSettings(october2025(), 60.1, 8.0).error)
        assertNotNull(repo.updateSettings(october2025(), 40.0, 0.9).error)
        assertNotNull(repo.updateSettings(october2025(), 40.0, 10.1).error)
    }

    // --- month queries --------------------------------------------------------------------------

    @Test
    fun monthsFromEntries_areUniqueAndSortedChronologically() {
        val s = october2025().add("2025-11-03").add("2025-10-06").add("2025-10-07").add("2026-01-05")
        assertEquals(listOf(2025 to 10, 2025 to 11, 2026 to 1), repo.getMonthsFromEntries(s))
    }

    @Test
    fun entriesForMonth_filtersAndSortsByDate() {
        val s = october2025().add("2025-10-14").add("2025-10-06").add("2025-11-03")
        assertEquals(listOf("2025-10-06", "2025-10-14"), repo.getEntriesForMonth(s, 2025, 10).map { it.datum })
    }

    @Test
    fun calculateMonthData_usesTheRequestedMonth() {
        val s = october2025().add("2025-11-03", "08:00", "16:30", 30.0, 4.0)
        val nov = repo.calculateMonthData(s, 2025, 11)
        assertEquals("November", nov.monat)
        assertEquals(4.0, nov.istRemoteStundenLaufend, tol)
        assertEquals(8.0, nov.istArbeitszeitLaufend, tol)
        assertEquals("Q4/2025", nov.quartal)
        assertEquals(160.0, nov.sollStundenGesamt, tol) // 20 weekdays, 1 Nov is a Saturday
    }

    @Test
    fun switchToMonth_changesViewAndQuarter() {
        val s = october2025().add("2025-11-03")
        val nov = repo.switchToMonth(s, 2025, 11)
        assertEquals("November", nov.aktuellerMonat)
        assertEquals(8.0, nov.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals("Q4/2025", nov.currentQuartal)
    }

    // --- close month ----------------------------------------------------------------------------

    @Test
    fun closeSpecificMonth_movesTheMonthToHistoryAndRemovesItsEntries() {
        val s = october2025().add("2025-10-06").add("2025-11-03")
        val (after, msg) = repo.closeSpecificMonth(s, 2025, 10)
        assertNull(msg)
        assertEquals(1, after.monatlicheHistorie.size)
        val h = after.monatlicheHistorie[0]
        assertEquals("Oktober", h.monat)
        assertEquals(8.0, h.istRemoteStundenLaufend, tol)
        assertEquals("Q4/2025", h.quartal)
        assertEquals(listOf(2025 to 11), repo.getMonthsFromEntries(after))
    }

    @Test
    fun closeSpecificMonth_switchesTheViewToTheLatestRemainingMonth() {
        val s = october2025().add("2025-10-06").add("2025-11-03")
        val (after, _) = repo.closeSpecificMonth(s, 2025, 10)
        assertEquals("November", after.aktuellerMonat)
        assertEquals(2025, after.aktuellesJahr)
        assertEquals(8.0, after.currentMonthData.istRemoteStundenLaufend, tol)
    }

    @Test
    fun closeSpecificMonth_secondCloseDoesNotDuplicateHistory() {
        val s = october2025().add("2025-10-06")
        val (once, _) = repo.closeSpecificMonth(s, 2025, 10)
        val again = once.add("2025-10-07")
        val (twice, _) = repo.closeSpecificMonth(again, 2025, 10)
        assertEquals(1, twice.monatlicheHistorie.size)
        assertFalse("2025-10-07" in twice.dailyRecords)
    }

    @Test
    fun closeSpecificMonth_historyIsSortedChronologically() {
        val s = october2025().add("2025-11-03").add("2025-10-06")
        val (a, _) = repo.closeSpecificMonth(s, 2025, 11)
        val (b, _) = repo.closeSpecificMonth(a, 2025, 10)
        assertEquals(listOf("Oktober", "November"), b.monatlicheHistorie.map { it.monat })
    }

    @Test
    fun closeMonth_advancesToTheNextMonth() {
        val (after, msg) = repo.closeMonth(october2025().add("2025-10-06"))
        assertNull(msg)
        assertEquals("November", after.aktuellerMonat)
        assertEquals("Q4/2025", after.currentQuartal)
        assertEquals(1, after.monatlicheHistorie.size)
        assertEquals(0.0, after.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals(160.0, after.currentMonthData.sollStundenGesamt, tol)
    }

    @Test
    fun closeMonth_decemberRollsOverToJanuaryOfTheNextYear() {
        val dec = repo.switchToMonth(october2025(), 2025, 12)
        val (after, _) = repo.closeMonth(dec)
        assertEquals("Januar", after.aktuellerMonat)
        assertEquals(2026, after.aktuellesJahr)
        assertEquals("Q1/2026", after.currentQuartal)
    }

    // --- quarterly quota adjustment -------------------------------------------------------------

    private fun histMonth(name: String, year: Int, remote: Double, max: Double) =
        MonthData(name, year, 40.0, 160.0, max, remote, remote, quartal = null)

    /** March 2026 is the viewed month and has one entry (an empty month is never closed into the history). */
    private fun marchWith(history: List<MonthData>, quote: Double) = repo.switchToMonth(
        october2025(quote = quote, history = history), 2026, 3
    ).add("2026-03-02")

    @Test
    fun quarter_underQuotaRaisesTheQuotaByTenPoints() {
        val s = marchWith(
            listOf(histMonth("Januar", 2026, 10.0, 64.0), histMonth("Februar", 2026, 10.0, 64.0)), 40.0
        )
        val (after, msg) = repo.closeSpecificMonth(s, 2026, 3)
        assertEquals(50.0, after.individuelleQuoteProzent, tol)
        assertEquals("Quartal abgeschlossen. Neue Quote: 50%", msg)
    }

    @Test
    fun quarter_overQuotaLowersTheQuotaByTenPoints() {
        val s = marchWith(
            listOf(histMonth("Januar", 2026, 150.0, 64.0), histMonth("Februar", 2026, 150.0, 64.0)), 50.0
        )
        val (after, msg) = repo.closeSpecificMonth(s, 2026, 3)
        assertEquals(40.0, after.individuelleQuoteProzent, tol)
        assertEquals("Quartal abgeschlossen. Remote über Quote! Neue Quote: 40%", msg)
    }

    @Test
    fun quarter_quotaNeverDropsBelowFortyOrRisesAboveSixty() {
        val low = marchWith(
            listOf(histMonth("Januar", 2026, 150.0, 64.0), histMonth("Februar", 2026, 150.0, 64.0)), 40.0
        )
        val (afterLow, msgLow) = repo.closeSpecificMonth(low, 2026, 3)
        assertEquals(40.0, afterLow.individuelleQuoteProzent, tol)
        assertTrue(msgLow!!.contains("40%"))

        val high = marchWith(
            listOf(histMonth("Januar", 2026, 10.0, 64.0), histMonth("Februar", 2026, 10.0, 64.0)), 60.0
        )
        val (afterHigh, msgHigh) = repo.closeSpecificMonth(high, 2026, 3)
        assertEquals(60.0, afterHigh.individuelleQuoteProzent, tol)
        assertTrue(msgHigh!!.contains("60%"))
    }

    @Test
    fun quarter_adjustmentOnlyAtQuarterEndAndWithThreeMonths() {
        val notEnd = marchWith(emptyList(), 40.0)
        assertNull(repo.closeSpecificMonth(repo.switchToMonth(notEnd, 2026, 2), 2026, 2).second)

        val twoMonthsOnly = marchWith(listOf(histMonth("Februar", 2026, 10.0, 64.0)), 40.0)
        val (after, msg) = repo.closeSpecificMonth(twoMonthsOnly, 2026, 3)
        assertNull(msg)
        assertEquals(40.0, after.individuelleQuoteProzent, tol)
    }

    @Test
    fun quarter_otherYearsAndQuartersAreIgnored() {
        val s = marchWith(
            listOf(
                histMonth("Januar", 2025, 10.0, 64.0), histMonth("Februar", 2025, 10.0, 64.0)
            ), 40.0
        )
        assertNull(repo.closeSpecificMonth(s, 2026, 3).second)
    }

    // --- export / backup / reset ----------------------------------------------------------------

    @Test
    fun csv_hasHeaderAndOneLinePerEntry() {
        val s = october2025().add("2025-10-07").add("2025-10-06", remote = 4.0)
        val lines = repo.generateCsv(s, 2025, 10).trim().lines()
        assertEquals("Datum,Start,Ende,Pause (min),Remote (h),Gesamt (h)", lines[0])
        assertEquals("2025-10-06,08:00,16:30,30.0,4.0,8.0", lines[1])
        assertEquals("2025-10-07,08:00,16:30,30.0,8.0,8.0", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun csv_emptyMonthHasOnlyTheHeader() {
        assertEquals(1, repo.generateCsv(october2025(), 2025, 10).trim().lines().size)
    }

    @Test
    fun backup_serializeThenDeserializeRestoresTheState() {
        val s = october2025().add("2025-10-06", remote = 4.0).copy(language = "en", darkMode = true)
        assertEquals(s, repo.deserializeState(repo.serializeState(s)))
    }

    @Test
    fun backup_invalidJsonGivesNull() {
        assertNull(repo.deserializeState("kein json"))
        assertNull(repo.deserializeState("{}"))
    }

    @Test
    fun backup_unknownKeysAreIgnored() {
        val json = repo.serializeState(october2025()).replaceFirst("{", "{\"neu\": 1,")
        assertNotNull(repo.deserializeState(json))
    }

    // --- restore with validation ----------------------------------------------------------------

    private fun restoreOf(state: TrackerState) = repo.restoreState(repo.serializeState(state))

    @Test
    fun restore_validBackupIsAcceptedAndTotalsAreRecalculated() {
        val s = october2025().add("2025-10-06", remote = 4.0)
        val tampered = s.copy(currentMonthData = s.currentMonthData.copy(istRemoteStundenLaufend = 999.0))
        val r = restoreOf(tampered)
        assertNotNull(r.state, r.error)
        assertEquals(4.0, r.state!!.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals("Q4/2025", r.state!!.currentQuartal)
    }

    @Test
    fun restore_garbageAndEmptyJsonAreRejected() {
        assertNotNull(repo.restoreState("kein json").error)
        assertNotNull(repo.restoreState("{}").error)
        assertNotNull(repo.restoreState("").error)
    }

    @Test
    fun restore_rejectsUnknownMonthAndYearOutOfRange() {
        val s = october2025()
        assertTrue(restoreOf(s.copy(aktuellerMonat = "x")).error!!.startsWith("Ungültiger Monat"))
        assertTrue(restoreOf(s.copy(aktuellesJahr = 1)).error!!.startsWith("Ungültiges Jahr"))
        assertTrue(restoreOf(s.copy(aktuellesJahr = 9999)).error!!.startsWith("Ungültiges Jahr"))
    }

    @Test
    fun restore_rejectsOutOfRangeSettingsAndUnknownLanguage() {
        val s = october2025()
        assertNotNull(restoreOf(s.copy(individuelleQuoteProzent = 5000.0)).error)
        assertNotNull(restoreOf(s.copy(individuelleQuoteProzent = 39.0)).error)
        assertNotNull(restoreOf(s.copy(sollStundenProTag = 0.0)).error)
        assertNotNull(restoreOf(s.copy(sollStundenProTag = -8.0)).error)
        assertNotNull(restoreOf(s.copy(language = "fr")).error)
    }

    @Test
    fun restore_rejectsBrokenEntries() {
        val s = october2025().add("2025-10-06")
        val e = s.dailyRecords["2025-10-06"]!![0]
        fun withEntry(entry: WorkEntry, key: String = "2025-10-06") =
            s.copy(dailyRecords = mapOf(key to listOf(entry)))
        assertTrue(restoreOf(withEntry(e, key = "kein-datum")).error!!.startsWith("Ungültiges Datum"))
        assertTrue(restoreOf(withEntry(e, key = "2025-02-30")).error!!.startsWith("Ungültiges Datum"))
        assertTrue(restoreOf(withEntry(e.copy(datum = "2025-10-07"))).error!!.startsWith("Ungültiger Eintrag"))
        assertTrue(restoreOf(withEntry(e.copy(id = " "))).error!!.startsWith("Ungültiger Eintrag"))
        assertTrue(restoreOf(withEntry(e.copy(startTime = "99:99"))).error!!.startsWith("Ungültige Zeit"))
        assertTrue(restoreOf(withEntry(e.copy(totalStundenHeute = -3.0))).error!!.startsWith("Ungültige Stunden"))
        assertTrue(restoreOf(withEntry(e.copy(pauseMinutes = -1.0))).error!!.startsWith("Ungültige Stunden"))
    }

    @Test
    fun restore_rejectsBrokenHistory() {
        val bad = october2025(history = listOf(MonthData("Nope", 2025, 40.0, 160.0, 64.0, 0.0, 0.0)))
        assertEquals("Ungültiger Historieneintrag", restoreOf(bad).error)
    }

    @Test
    fun restore_roundTripOfAFullStateKeepsEntriesAndHistory() {
        val closed = repo.closeSpecificMonth(october2025().add("2025-10-06").add("2025-11-03"), 2025, 10).first
        val r = restoreOf(closed)
        assertNotNull(r.state, r.error)
        assertEquals(closed.monatlicheHistorie, r.state!!.monatlicheHistorie)
        assertEquals(closed.dailyRecords, r.state!!.dailyRecords)
    }

    @Test
    fun clearAllHistory_resetsEverythingToDefaults() {
        val s = repo.updateSettings(october2025().add("2025-10-06"), 55.0, 7.0).state!!
            .copy(language = "en", darkMode = true)
        val reset = repo.clearAllHistory(s)
        assertTrue(reset.dailyRecords.isEmpty())
        assertTrue(reset.monatlicheHistorie.isEmpty())
        assertEquals(40.0, reset.individuelleQuoteProzent, tol)
        assertEquals(8.0, reset.sollStundenProTag, tol)
        // documents the behaviour shown in the confirmation dialog: language and dark mode are reset too
        assertEquals("de", reset.language)
        assertFalse(reset.darkMode)
        assertNotEquals(s.language, reset.language)
    }

    // --- close month: no empty month, no duplicate, history always sorted -------------------------

    private fun names(s: TrackerState) = s.monatlicheHistorie.map { it.monat to it.jahr }

    private fun snapshot(name: String, year: Int, remote: Double = 10.0) =
        MonthData(name, year, 40.0, 160.0, 64.0, remote, 160.0, quartal = null)

    @Test
    fun closeSpecificMonth_monthWithoutEntriesIsNotAddedToTheHistory() {
        val s = october2025()
        val (after, msg) = repo.closeSpecificMonth(s, 2025, 10)
        assertNull(msg)
        assertTrue(after.monatlicheHistorie.isEmpty())
        assertEquals(s, after)                                   // nothing at all changed
    }

    @Test
    fun closeSpecificMonth_emptyMonthLeavesTheExistingHistoryUntouched() {
        val s = october2025(history = listOf(snapshot("August", 2025), snapshot("September", 2025)))
        val (after, _) = repo.closeSpecificMonth(s, 2025, 10)
        assertEquals(listOf("August" to 2025, "September" to 2025), names(after))
    }

    @Test
    fun closeSpecificMonth_monthWithEntriesIsAdded() {
        val (after, _) = repo.closeSpecificMonth(october2025().add("2025-10-06"), 2025, 10)
        assertEquals(listOf("Oktober" to 2025), names(after))
    }

    @Test
    fun closeSpecificMonth_monthAlreadyInHistoryWithoutEntriesKeepsItsSnapshot() {
        val s = october2025(history = listOf(snapshot("Oktober", 2025, remote = 77.0)))
        val (after, _) = repo.closeSpecificMonth(s, 2025, 10)
        assertEquals(1, after.monatlicheHistorie.size)
        assertEquals(77.0, after.monatlicheHistorie[0].istRemoteStundenLaufend, tol)
    }

    @Test
    fun closeSpecificMonth_monthAlreadyInHistoryWithEntriesIsUpdatedNotDuplicated() {
        val s = october2025(history = listOf(snapshot("Oktober", 2025, remote = 77.0))).add("2025-10-06")
        val (after, _) = repo.closeSpecificMonth(s, 2025, 10)
        assertEquals(1, after.monatlicheHistorie.size)
        assertEquals(8.0, after.monatlicheHistorie[0].istRemoteStundenLaufend, tol)
        assertFalse("2025-10-06" in after.dailyRecords)
    }

    @Test
    fun closeSpecificMonth_sameMonthOfAnotherYearIsADifferentMonth() {
        val s = october2025(history = listOf(snapshot("Oktober", 2024))).add("2025-10-06")
        val (after, _) = repo.closeSpecificMonth(s, 2025, 10)
        assertEquals(listOf("Oktober" to 2024, "Oktober" to 2025), names(after))
    }

    @Test
    fun closeSpecificMonth_monthsClosedInAnyOrderEndUpSorted() {
        var s = october2025()
            .add("2025-07-01").add("2025-05-05").add("2026-01-05").add("2025-12-01").add("2025-06-02")
        for ((y, m) in listOf(2025 to 7, 2025 to 5, 2026 to 1, 2025 to 12, 2025 to 6)) {
            s = repo.closeSpecificMonth(s, y, m).first
        }
        assertEquals(
            listOf("Mai" to 2025, "Juni" to 2025, "Juli" to 2025, "Dezember" to 2025, "Januar" to 2026),
            names(s)
        )
    }

    @Test
    fun closeSpecificMonth_alreadyExistingHistoryIsSortedAfterAClose() {
        val shuffled = listOf(snapshot("Juli", 2025), snapshot("Mai", 2025), snapshot("Juni", 2025))
        val s = october2025(history = shuffled).add("2025-10-06")
        assertEquals(listOf("Mai", "Juni", "Juli", "Oktober"), repo.closeSpecificMonth(s, 2025, 10).first.monatlicheHistorie.map { it.monat })
    }

    @Test
    fun closeMonth_monthWithoutEntriesIsNotAddedButTheMonthAdvances() {
        val (after, _) = repo.closeMonth(october2025())
        assertTrue(after.monatlicheHistorie.isEmpty())
        assertEquals("November", after.aktuellerMonat)
    }

    @Test
    fun closeMonth_neverAddsTheSameMonthTwice() {
        val s = october2025(history = listOf(snapshot("Oktober", 2025, remote = 77.0))).add("2025-10-06")
        val (after, _) = repo.closeMonth(s)
        assertEquals(listOf("Oktober" to 2025), names(after))
    }

    @Test
    fun closeMonth_keepsTheHistorySorted() {
        val s = october2025(history = listOf(snapshot("Dezember", 2025), snapshot("August", 2025))).add("2025-10-06")
        assertEquals(listOf("August", "Oktober", "Dezember"), repo.closeMonth(s).first.monatlicheHistorie.map { it.monat })
    }

    @Test
    fun closeMonth_noQuarterAdjustmentWhenNothingWasAdded() {
        val q = listOf(snapshot("Januar", 2026), snapshot("Februar", 2026))
        val march = repo.switchToMonth(october2025(history = q), 2026, 3)
        val (after, msg) = repo.closeMonth(march)                // March is empty
        assertNull(msg)
        assertEquals(40.0, after.individuelleQuoteProzent, tol)
    }

    @Test
    fun monthHasEntries_onlyTrueForMonthsWithEntries() {
        val s = october2025().add("2025-10-06")
        assertTrue(repo.monthHasEntries(s, 2025, 10))
        assertFalse(repo.monthHasEntries(s, 2025, 11))
        assertFalse(repo.monthHasEntries(s, 2024, 10))
        assertFalse(repo.monthHasEntries(october2025(), 2025, 10))
    }

    @Test
    fun normalizeHistory_sortsAndRemovesDuplicatesLastOneWins() {
        val got = repo.normalizeHistory(
            listOf(snapshot("Mai", 2026, 1.0), snapshot("Januar", 2026), snapshot("Mai", 2026, 2.0), snapshot("Dezember", 2025))
        )
        assertEquals(listOf("Dezember" to 2025, "Januar" to 2026, "Mai" to 2026), got.map { it.monat to it.jahr })
        assertEquals(2.0, got[2].istRemoteStundenLaufend, tol)
        assertTrue(repo.normalizeHistory(emptyList()).isEmpty())
    }

    @Test
    fun restoreState_sortsAShuffledHistoryAndRemovesDuplicates() {
        val shuffled = listOf(snapshot("Juli", 2025), snapshot("Mai", 2025), snapshot("Mai", 2025, 5.0), snapshot("Juni", 2025))
        val restored = assertNotNull(restoreOf(october2025(history = shuffled)).state)
        assertEquals(listOf("Mai" to 2025, "Juni" to 2025, "Juli" to 2025), names(restored))
    }

    // --- statement import: same time as the statement, loading it again corrects in place ----------

    @Test
    fun importWorkEntry_storesTheBreakAndShowsTheStatementTime() {
        val s = assertNotNull(repo.importWorkEntry(october2025(), "2025-10-06", "08:00", "17:00", 30.0, null).state)
        val e = s.dailyRecords.getValue("2025-10-06").single()
        assertEquals(8.5, e.totalStundenHeute, tol)
        assertEquals(8.5, e.remoteStundenHeute, tol)
    }

    @Test
    fun importWorkEntry_sameStatementAgainCorrectsAnOldImportWithoutDuplicate() {
        val old = assertNotNull(repo.importWorkEntry(october2025(), "2025-10-06", "08:00", "17:00", 0.0, null).state)
        assertEquals(9.0, old.dailyRecords.getValue("2025-10-06").single().totalStundenHeute, tol)
        val fixed = assertNotNull(repo.importWorkEntry(old, "2025-10-06", "08:00", "17:00", 30.0, null).state)
        val day = fixed.dailyRecords.getValue("2025-10-06")
        assertEquals(1, day.size)
        assertEquals(8.5, day[0].totalStundenHeute, tol)
        assertEquals(8.5, fixed.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun importWorkEntry_neverOverwritesAManualEntry() {
        val manual = october2025().add("2025-10-06")
        val r = repo.importWorkEntry(manual, "2025-10-06", "08:00", "16:30", 30.0, null)
        assertNull(r.state)
        assertNotNull(r.error)
    }

    // --- Flextime balance -----------------------------------------------------------------------

    @Test
    fun flexBalanceIsStoredInTheState() {
        val s = october2025()
        val out = repo.withFlexBalance(s, FlexBalance(previous = 1.5, current = -2.0, total = -0.5, year = 2025, month = 12))
        assertEquals(-0.5, out.gleitzeitSaldo!!.total!!, 1e-9)
        assertEquals(12, out.gleitzeitSaldo!!.month)
    }

    @Test
    fun missingOrImplausibleBalanceKeepsTheState() {
        val s = october2025()
        assertSame(s, repo.withFlexBalance(s, null))
        assertSame(s, repo.withFlexBalance(s, FlexBalance(current = Double.NaN, total = 99999.0)))
    }

    @Test
    fun oldBackupWithoutBalanceStillLoads() {
        val json = repo.serializeState(october2025()).replace(Regex(""","gleitzeitSaldo":[^,}]*"""), "")
        assertNull(repo.restoreState(json).state!!.gleitzeitSaldo)
    }

    @Test
    fun balanceSurvivesBackupAndRestore() {
        val s = repo.withFlexBalance(october2025(), FlexBalance(total = 7.25))
        assertEquals(7.25, repo.restoreState(repo.serializeState(s)).state!!.gleitzeitSaldo!!.total!!, 1e-9)
    }

    // --- Target hours are recalculated on load -------------------------------------------------

    private fun staleDecember(history: List<MonthData> = emptyList()) = TrackerState(
        aktuellerMonat = "Dezember", aktuellesJahr = 2025,
        currentMonthData = MonthData("Dezember", 2025, 40.0, 168.0, 67.2, 0.0, 0.0),   // saved before the special days
        currentQuartal = "Q4/2025", monatlicheHistorie = history
    )

    @Test
    fun loadRecalculatesTheTargetOfTheCurrentMonth() {
        repo.saveState(staleDecember())
        val loaded = repo.loadState()
        assertEquals(156.0, loaded.currentMonthData.sollStundenGesamt, 1e-9)   // 24 Dec off, 31 Dec half
        assertEquals(156.0 * 0.4, loaded.currentMonthData.maxRemoteStunden, 1e-9)
    }

    @Test
    fun loadKeepsClosedMonthsAsTheyWereSaved() {
        val closed = MonthData("Dezember", 2024, 40.0, 168.0, 67.2, 10.0, 80.0, quartal = "Q4/2024")
        repo.saveState(staleDecember(history = listOf(closed)))
        assertEquals(listOf(closed), repo.loadState().monatlicheHistorie)
    }

    @Test
    fun loadRecalculatesWithTheSavedDailyTargetAndQuota() {
        val s = staleDecember().copy(sollStundenProTag = 6.0, individuelleQuoteProzent = 50.0)
        repo.saveState(s)
        val m = repo.loadState().currentMonthData
        assertEquals(19.5 * 6.0, m.sollStundenGesamt, 1e-9)
        assertEquals(19.5 * 6.0 * 0.5, m.maxRemoteStunden, 1e-9)
        assertEquals(50.0, m.individuelleQuote, 1e-9)
    }

    @Test
    fun loadOfAnUpToDateStateChangesNothing() {
        val s = october2025()
        repo.saveState(s)
        assertEquals(s, repo.loadState())
    }
}
