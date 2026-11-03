package com.remotework.tracker

import android.content.Context
import android.content.SharedPreferences
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlinx.datetime.plus
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Robustness of backup restore, month closing and statement import (fictional round values). */
class RobustRepositoryTest {

    private val tol = 1e-9
    private val store = mutableMapOf<String, String>()

    private fun newRepository(): TrackerRepository {
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(any(), any())).thenAnswer {
            synchronized(store) { store[it.arguments[0] as String] = it.arguments[1] as String }
            editor
        }
        `when`(editor.remove(any())).thenAnswer { synchronized(store) { store.remove(it.arguments[0] as String) }; editor }
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString(any(), any())).thenAnswer { synchronized(store) { store[it.arguments[0] as String] } }
        `when`(prefs.edit()).thenReturn(editor)
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(prefs)
        return TrackerRepository(LocalStorage(context))
    }

    private val repo = newRepository()

    private fun state(
        month: String = "Oktober", year: Int = 2025, quote: Double = 40.0,
        history: List<MonthData> = emptyList(), entries: Map<String, List<WorkEntry>> = emptyMap()
    ) = repo.recalculateMonthlyTotals(
        TrackerState(
            aktuellerMonat = month, aktuellesJahr = year,
            currentMonthData = MonthData(month, year, quote, 0.0, 0.0, 0.0, 0.0),
            currentQuartal = TrackerCalculations.getQuartalForMonth(month, year),
            dailyRecords = entries, monatlicheHistorie = history, individuelleQuoteProzent = quote
        )
    )

    private fun hist(month: String, year: Int, remote: Double = 10.0, max: Double = 60.0) =
        MonthData(month, year, 40.0, 150.0, max, remote, 100.0, quartal = null)

    private fun add(s: TrackerState, d: String, a: String, b: String, pause: Double = 30.0, remote: Double? = null) =
        repo.addWorkEntry(s, d, a, b, pause, remote)

    // === (1) restoreState ====================================================================

    private fun backupOf(s: TrackerState = state()) = repo.serializeState(s)

    @Test
    fun restoreRejectsGarbageWithoutThrowing() {
        val inputs = listOf(
            "", " ", "\n", "null", "[]", "{}", "[1,2,3]", "\"text\"", "42", "true", "{", "}", "{\"a\":", "\u0000\u0000",
            "{\"aktuellerMonat\":\"Oktober\"}", "<xml/>", "NaN", "{\"aktuellerMonat\":\"Oktober\",\"aktuellesJahr\":NaN}"
        )
        for (i in inputs) {
            val r = repo.restoreState(i)
            assertFalse(r.isSuccess, "'$i'")
            assertNotNull(r.error)
        }
    }

    @Test
    fun restoreRejectsEveryTruncationOfAValidBackup() {
        val full = backupOf(add(state(), "2025-10-01", "08:00", "16:30").state!!)
        assertTrue(repo.restoreState(full).isSuccess)
        // cutting the JSON anywhere before the closing brace must fail cleanly (or, if it still parses, validate)
        for (cut in 0 until full.trimEnd().length - 1) {
            val r = repo.restoreState(full.substring(0, cut))
            if (r.isSuccess) fail("truncation at $cut was accepted")
        }
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)

    @Test
    fun restoreOfOldBackupWithoutNewFieldsWorks() {
        val old = """{"aktuellerMonat":"Oktober","aktuellesJahr":2025,
          "currentMonthData":{"monat":"Oktober","jahr":2025,"individuelleQuote":40.0,"sollStundenGesamt":176.0,
          "maxRemoteStunden":70.4,"istRemoteStundenLaufend":0.0,"istArbeitszeitLaufend":0.0},
          "currentQuartal":"Q4/2025"}"""
        val r = repo.restoreState(old)
        assertTrue(r.isSuccess, r.error)
        assertNull(r.state!!.gleitzeitSaldo)
        assertEquals("de", r.state!!.language)
        assertEquals(40.0, r.state!!.individuelleQuoteProzent, tol)
        assertEquals(176.0, r.state!!.currentMonthData.sollStundenGesamt, tol)  // recomputed
    }

    @Test
    fun restoreOfOldEntryWithoutSourceAndPauseWarningWorks() {
        val old = """{"aktuellerMonat":"Oktober","aktuellesJahr":2025,
          "currentMonthData":{"monat":"Oktober","jahr":2025,"individuelleQuote":40.0,"sollStundenGesamt":1.0,
          "maxRemoteStunden":1.0,"istRemoteStundenLaufend":0.0,"istArbeitszeitLaufend":0.0},
          "currentQuartal":"Q4/2025",
          "dailyRecords":{"2025-10-01":[{"id":"x","datum":"2025-10-01","startTime":"08:00","endTime":"16:00",
          "pauseMinutes":30.0,"remoteStundenHeute":4.0,"totalStundenHeute":7.5}]}}"""
        val r = repo.restoreState(old)
        assertTrue(r.isSuccess, r.error)
        assertNull(r.state!!.dailyRecords["2025-10-01"]!![0].source)
        assertEquals(4.0, r.state!!.currentMonthData.istRemoteStundenLaufend, tol)
    }

    @Test
    fun restoreIgnoresUnknownFieldsAtEveryLevel() {
        val text = """{"zukunft":{"a":[1,2]},"aktuellerMonat":"Oktober","aktuellesJahr":2025,
          "currentMonthData":{"monat":"Oktober","jahr":2025,"individuelleQuote":40.0,"sollStundenGesamt":1.0,
          "maxRemoteStunden":1.0,"istRemoteStundenLaufend":0.0,"istArbeitszeitLaufend":0.0,"neu":"x"},
          "currentQuartal":"Q4/2025","gleitzeitSaldo":{"total":2.5,"extra":1}}"""
        val r = repo.restoreState(text)
        assertTrue(r.isSuccess, r.error)
        assertEquals(2.5, r.state!!.gleitzeitSaldo!!.total!!, tol)
    }

    @Test
    fun restoreRejectsWrongTypes() {
        val rich = add(state(quote = 50.0, history = listOf(hist("September", 2025))), "2025-10-01", "08:00", "16:30").state!!
        val good = backupOf(rich.copy(darkMode = true))
        val variants = listOf(
            good.replace("\"aktuellesJahr\": 2025", "\"aktuellesJahr\": 2025.5"),
            good.replace("\"aktuellesJahr\": 2025", "\"aktuellesJahr\": null"),
            good.replace("\"aktuellerMonat\": \"Oktober\"", "\"aktuellerMonat\": 10"),
            good.replace("\"dailyRecords\": {", "\"dailyRecords\": [").replaceFirst("}\n    },\n    \"monatlicheHistorie", "]\n    },\n    \"monatlicheHistorie"),
            good.replace("\"monatlicheHistorie\": [", "\"monatlicheHistorie\": {"),
            good.replace("\"darkMode\": true", "\"darkMode\": \"nein\""),
            good.replace("\"individuelleQuoteProzent\": 50.0", "\"individuelleQuoteProzent\": [50.0]"),
            good.replace("\"startTime\": \"08:00\"", "\"startTime\": 800"),
            good.replace("\"pauseMinutes\": 30.0", "\"pauseMinutes\": \"abc\"")
        )
        for ((i, v) in variants.withIndex()) {
            assertFalse(v == good, "variant $i did not change the text")
            assertFalse(repo.restoreState(v).isSuccess, "variant $i accepted")
        }
    }

    @Test
    fun restoreQuotedNumberIsEitherRejectedOrExact() {
        val good = backupOf()
        val r = repo.restoreState(good.replace("\"aktuellesJahr\": 2025", "\"aktuellesJahr\": \"2025\""))
        if (r.isSuccess) assertEquals(2025, r.state!!.aktuellesJahr)
    }

    @Test
    fun restoreRejectsOutOfRangeAndNonFiniteNumbers() {
        val good = backupOf(state(quote = 50.0).copy(sollStundenProTag = 7.5, language = "en"))
        val bad = listOf(
            good.replace("\"individuelleQuoteProzent\": 50.0", "\"individuelleQuoteProzent\": 99.0"),
            good.replace("\"individuelleQuoteProzent\": 50.0", "\"individuelleQuoteProzent\": 1e999"),
            good.replace("\"individuelleQuoteProzent\": 50.0", "\"individuelleQuoteProzent\": -40.0"),
            good.replace("\"sollStundenProTag\": 7.5", "\"sollStundenProTag\": 0.0"),
            good.replace("\"sollStundenProTag\": 7.5", "\"sollStundenProTag\": 1e999"),
            good.replace("\"aktuellesJahr\": 2025", "\"aktuellesJahr\": 1999"),
            good.replace("\"aktuellesJahr\": 2025", "\"aktuellesJahr\": 2101"),
            good.replace("\"aktuellesJahr\": 2025", "\"aktuellesJahr\": 2147483648"),
            good.replace("\"aktuellerMonat\": \"Oktober\"", "\"aktuellerMonat\": \"October\""),
            good.replace("\"language\": \"en\"", "\"language\": \"fr\"")
        )
        for ((i, v) in bad.withIndex()) {
            assertFalse(v == good, "bad variant $i did not change the text")
            assertFalse(repo.restoreState(v).isSuccess, "bad variant $i accepted")
        }
    }

    @Test
    fun restoreRejectsBadEntries() {
        val base = add(state(), "2025-10-01", "08:00", "16:30").state!!
        val good = backupOf(base)
        val bad = listOf(
            good.replace("\"startTime\": \"08:00\"", "\"startTime\": \"25:00\""),
            good.replace("\"endTime\": \"16:30\"", "\"endTime\": \"\""),
            good.replace("\"pauseMinutes\": 30.0", "\"pauseMinutes\": -1.0"),
            good.replace("\"pauseMinutes\": 30.0", "\"pauseMinutes\": 1e999"),
            good.replace("\"datum\": \"2025-10-01\",\n                \"startTime\"", "\"datum\": \"2025-10-02\",\n                \"startTime\""),
            good.replace("\"2025-10-01\": [", "\"2025-10-1\": ["),
            good.replace("\"2025-10-01\": [", "\"2025-02-30\": ["),
            good.replace("\"2025-10-01\": [", "\"1999-10-01\": [")
        )
        for ((i, v) in bad.withIndex()) {
            assertFalse(v == good, "variant $i did not change the text")
            assertFalse(repo.restoreState(v).isSuccess, "bad entry variant $i accepted")
        }
    }

    @Test
    fun restoreRecalculatesInsteadOfTrustingStoredTotals() {
        val s = add(state(), "2025-10-01", "08:00", "16:00", pause = 60.0, remote = 3.0).state!!
        val tampered = backupOf(s)
            .replace("\"istRemoteStundenLaufend\": 3.0", "\"istRemoteStundenLaufend\": 999.0")
            .replace("\"currentQuartal\": \"Q4/2025\"", "\"currentQuartal\": \"Q9/1900\"")
        val r = repo.restoreState(tampered)
        assertTrue(r.isSuccess, r.error)
        assertEquals(3.0, r.state!!.currentMonthData.istRemoteStundenLaufend, tol)
        assertEquals("Q4/2025", r.state!!.currentQuartal)
    }

    @Test
    fun restoreRoundTripIsStable() {
        val s = add(state(history = listOf(hist("September", 2025))), "2025-10-01", "08:00", "16:30").state!!
        val once = repo.restoreState(backupOf(s)).state!!
        val twice = repo.restoreState(backupOf(once)).state!!
        assertEquals(once, twice)
    }

    @Test
    fun restoreNormalizesDuplicateAndUnsortedHistory() {
        val s = state(history = listOf(hist("Dezember", 2025), hist("Januar", 2025), hist("Dezember", 2025, remote = 7.0), hist("März", 2024)))
        val r = repo.restoreState(backupOf(s)).state!!
        assertEquals(listOf(2024 to "März", 2025 to "Januar", 2025 to "Dezember"), r.monatlicheHistorie.map { it.jahr to it.monat })
        assertEquals(7.0, r.monatlicheHistorie.last().istRemoteStundenLaufend, tol)
    }

    @Test
    fun restoreHandlesHugeEntryLists() {
        // 3000 days x 4 entries; must finish and stay consistent
        val records = mutableMapOf<String, List<WorkEntry>>()
        var day = kotlinx.datetime.LocalDate(2025, 1, 1)
        repeat(3000) {
            val d = day.toString()
            records[d] = List(4) { n ->
                WorkEntry("id-$d-$n", d, "${(6 + n * 3).toString().padStart(2, '0')}:00", "${(8 + n * 3).toString().padStart(2, '0')}:00", 0.0, 1.0, 2.0)
            }
            day = day.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
        val big = state(entries = records)
        val json = backupOf(big)
        val t0 = System.nanoTime()
        val r = repo.restoreState(json)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(r.isSuccess, r.error)
        assertTrue(ms < 20_000, "took $ms ms")
        assertEquals(3000, r.state!!.dailyRecords.size)
        assertEquals(31 * 4 * 2.0, r.state!!.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun restoreWithHugeHistoryList() {
        val h = (0 until 5000).map { hist(MonthNames.german[it % 12], 2000 + it % 101) }
        val r = repo.restoreState(backupOf(state(history = h)))
        assertTrue(r.isSuccess, r.error)
        assertTrue(r.state!!.monatlicheHistorie.size <= 12 * 101)
    }

    @Test
    fun restoreWithDeeplyNestedJunkDoesNotCrash() {
        val deep = "[".repeat(5000) + "]".repeat(5000)
        val r = try { repo.restoreState("{\"aktuellerMonat\":\"Oktober\",\"x\":$deep}") } catch (e: StackOverflowError) { fail("stack overflow") }
        assertFalse(r.isSuccess)  // missing required fields
    }

    /** gleitzeitSaldo is not validated on restore: hostile figures must at least not break the state. */
    @Test
    fun restoreWithOddFlexBalanceKeepsStateUsable() {
        val good = backupOf(repo.withFlexBalance(state(), FlexBalance(1.0, 2.0, 3.0, 2025, 10)))
        for (variant in listOf(
            good.replace("\"total\": 3.0", "\"total\": 1e999"),
            good.replace("\"month\": 10", "\"month\": 13"),
            good.replace("\"year\": 2025,\n        \"month\"", "\"year\": -5,\n        \"month\"")
        )) {
            assertFalse(variant == good, "variant did not change the text")
            val r = repo.restoreState(variant)
            // acceptable: rejected, or accepted with cleaned figures; never an infinite value in the state
            if (r.isSuccess) {
                val b = r.state!!.gleitzeitSaldo
                if (b != null) listOf(b.previous, b.current, b.total).forEach { assertTrue(it == null || it.isFinite(), "non-finite flex value kept") }
            }
        }
    }

    @Test
    fun restoreCleansImplausibleFlexBalance() {
        val good = backupOf(repo.withFlexBalance(state(), FlexBalance(1.0, 2.0, 3.0, 2025, 10)))
        val badPeriod = repo.restoreState(good.replace("\"month\": 10", "\"month\": 13")).state!!.gleitzeitSaldo!!
        assertNull(badPeriod.month)
        assertNull(badPeriod.year)
        assertEquals(3.0, badPeriod.total!!, tol)
        val huge = repo.restoreState(good.replace("\"total\": 3.0", "\"total\": 1e30")).state!!.gleitzeitSaldo!!
        assertNull(huge.total)
        assertEquals(2.0, huge.current!!, tol)
        val none = repo.restoreState(
            good.replace("\"total\": 3.0", "\"total\": 1e30").replace("\"current\": 2.0", "\"current\": 1e30").replace("\"previous\": 1.0", "\"previous\": -1e30")
        ).state!!
        assertNull(none.gleitzeitSaldo)
        // a valid balance round-trips unchanged
        assertEquals(FlexBalance(1.0, 2.0, 3.0, 2025, 10), repo.restoreState(good).state!!.gleitzeitSaldo)
    }

    @Test
    fun loadStateFallsBackToDefaultOnCorruptStorage() {
        for (junk in listOf("", "{", "null", "[]", "{\"aktuellerMonat\":1}")) {
            store["tracker_state"] = junk
            val s = repo.loadState()
            assertTrue(s.aktuellerMonat in MonthNames.german)
            assertTrue(s.dailyRecords.isEmpty())
        }
        store.clear()
        assertNotNull(repo.loadState())
    }

    @Test
    fun saveAndLoadRoundTrip() {
        val s = add(state(), "2025-10-01", "08:00", "16:30").state!!
        repo.saveState(s)
        assertEquals(s, repo.loadState())
        repo.clearAll()
        assertTrue(repo.loadState().dailyRecords.isEmpty())
    }

    // === (4) closing months ==================================================================

    @Test
    fun closeMonthWithoutEntriesNeverAddsHistory() {
        val (s, msg) = repo.closeMonth(state())
        assertTrue(s.monatlicheHistorie.isEmpty())
        assertNull(msg)
        assertEquals("November", s.aktuellerMonat)
        assertEquals(2025, s.aktuellesJahr)
    }

    @Test
    fun closeMonthTwiceDoesNotDuplicate() {
        val s0 = add(state(), "2025-10-01", "08:00", "16:30").state!!
        val (s1, _) = repo.closeMonth(s0)
        assertEquals(1, s1.monatlicheHistorie.size)
        // jump back to October and close it again
        val back = repo.switchToMonth(s1, 2025, 10)
        val (s2, _) = repo.closeMonth(back)
        assertEquals(1, s2.monatlicheHistorie.size)
        val (s3, _) = repo.closeSpecificMonth(s2, 2025, 10)
        assertEquals(1, s3.monatlicheHistorie.size)
    }

    @Test
    fun closeDecemberRollsOverIntoNextYear() {
        val s0 = add(state("Dezember", 2025), "2025-12-01", "08:00", "16:30").state!!
        val (s, _) = repo.closeMonth(s0)
        assertEquals("Januar", s.aktuellerMonat)
        assertEquals(2026, s.aktuellesJahr)
        assertEquals("Q1/2026", s.currentQuartal)
        assertEquals("Q4/2025", s.monatlicheHistorie.single().quartal)
        assertEquals(
            TrackerCalculations.calculateSollStundenMonth(2026, 1, 8.0), s.currentMonthData.sollStundenGesamt, tol
        )
    }

    @Test
    fun closeAtYear2100StillWorks() {
        val s0 = add(state("Dezember", 2100), "2100-12-01", "08:00", "16:30").state!!
        val (s, _) = repo.closeMonth(s0)
        assertEquals(2101, s.aktuellesJahr)
    }

    @Test
    fun closingOutOfOrderKeepsHistorySorted() {
        var s = state("Oktober", 2025)
        for ((d, m) in listOf("2025-10-01" to 10, "2025-02-03" to 2, "2024-12-02" to 12, "2025-07-01" to 7)) {
            s = add(s, d, "08:00", "16:30").state!!
        }
        for ((y, m) in listOf(2025 to 7, 2024 to 12, 2025 to 10, 2025 to 2)) s = repo.closeSpecificMonth(s, y, m).first
        assertEquals(
            listOf(2024 to "Dezember", 2025 to "Februar", 2025 to "Juli", 2025 to "Oktober"),
            s.monatlicheHistorie.map { it.jahr to it.monat }
        )
        assertTrue(s.dailyRecords.isEmpty())
    }

    @Test
    fun closeSpecificMonthForOtherMonthsKeepsHistoryAtMostOnce() {
        val s = add(state(), "2025-10-01", "08:00", "16:30").state!!
        for (m in listOf(1, 12, 2, 11)) {
            val (r, _) = repo.closeSpecificMonth(s, 2025, m)
            assertTrue(r.monatlicheHistorie.size <= 1)
        }
    }

    private fun quarterState(remote: Double, max: Double, quote: Double = 40.0): TrackerState {
        val h = listOf(hist("Oktober", 2025, remote, max), hist("November", 2025, remote, max))
        return add(state("Dezember", 2025, quote, h), "2025-12-01", "08:00", "16:30").state!!
    }

    @Test
    fun quarterRuleLowersQuoteWhenOverAndRaisesWhenUnder() {
        // 3 x 100 remote > 3 x 60 max -> -10, floor 40
        val (over, msgOver) = repo.closeMonth(quarterState(100.0, 60.0, quote = 50.0))
        assertEquals(40.0, over.individuelleQuoteProzent, tol)
        assertTrue(msgOver!!.contains("40%"))
        val (floor, _) = repo.closeMonth(quarterState(100.0, 60.0, quote = 40.0))
        assertEquals(40.0, floor.individuelleQuoteProzent, tol)
        val (under, msgUnder) = repo.closeMonth(quarterState(10.0, 60.0, quote = 50.0))
        assertEquals(60.0, under.individuelleQuoteProzent, tol)
        assertNotNull(msgUnder)
        val (cap, _) = repo.closeMonth(quarterState(10.0, 60.0, quote = 60.0))
        assertEquals(60.0, cap.individuelleQuoteProzent, tol)
    }

    @Test
    fun quarterRuleNeedsAllThreeMonthsAndIgnoresOtherYears() {
        val h2 = listOf(hist("November", 2025), hist("Oktober", 2024))
        val s = add(state("Dezember", 2025, 50.0, h2), "2025-12-01", "08:00", "16:30").state!!
        val (r, msg) = repo.closeMonth(s)
        assertNull(msg)
        assertEquals(50.0, r.individuelleQuoteProzent, tol)
    }

    @Test
    fun quarterRuleIsNotRepeatedWhenClosingTheSameQuarterEndAgain() {
        val (once, m1) = repo.closeMonth(quarterState(10.0, 60.0, quote = 40.0))
        assertNotNull(m1)
        assertEquals(50.0, once.individuelleQuoteProzent, tol)
        val back = repo.switchToMonth(once, 2025, 12)
        val (twice, m2) = repo.closeMonth(back)
        assertNull(m2)
        assertEquals(50.0, twice.individuelleQuoteProzent, tol)
    }

    @Test
    fun nonQuarterEndMonthsNeverChangeTheQuote() {
        for (m in listOf("Januar", "Februar", "April", "Mai", "Juli", "August", "Oktober", "November")) {
            val num = MonthNames.getMonthNumber(m)
            val d = "2025-${num.toString().padStart(2, '0')}-"
            val day = (1..28).map { d + it.toString().padStart(2, '0') }.first { TrackerCalculations.isValidWorkingDay(it).isValid }
            val s = add(state(m, 2025, 50.0), day, "08:00", "16:30").state!!
            val (r, msg) = repo.closeMonth(s)
            assertNull(msg, m)
            assertEquals(50.0, r.individuelleQuoteProzent, tol)
        }
    }

    // === (5) importWorkEntry =================================================================

    @Test
    fun importingTheSameStatementRepeatedlyIsIdempotent() {
        var s = state()
        repeat(25) {
            s = repo.importWorkEntry(s, "2025-10-01", "08:00", "12:00", 0.0, null).state!!
            s = repo.importWorkEntry(s, "2025-10-01", "13:00", "17:00", 30.0, 3.0).state!!
        }
        val day = s.dailyRecords["2025-10-01"]!!
        assertEquals(2, day.size)
        assertTrue(day.all { it.source == SOURCE_PDF_IMPORT && it.pauseWarning == null })
        assertEquals(4.0 + 3.5, s.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun importCorrectionReplacesInPlaceAndKeepsOtherEntries() {
        var s = repo.importWorkEntry(state(), "2025-10-01", "08:00", "16:00", 0.0, null).state!!
        s = repo.addWorkEntry(s, "2025-10-01", "17:00", "18:00", 0.0, null).state!!
        val fixed = repo.importWorkEntry(s, "2025-10-01", "08:00", "16:00", 30.0, null)
        assertTrue(fixed.isSuccess, fixed.error)
        val day = fixed.state!!.dailyRecords["2025-10-01"]!!
        assertEquals(2, day.size)
        assertEquals(7.5, day.first { it.startTime == "08:00" }.totalStundenHeute, tol)
    }

    @Test
    fun failedCorrectionLeavesTheOldEntryUntouched() {
        val s = repo.importWorkEntry(state(), "2025-10-01", "08:00", "16:00", 0.0, null).state!!
        for (r in listOf(
            repo.importWorkEntry(s, "2025-10-01", "08:00", "16:00", -5.0, null),
            repo.importWorkEntry(s, "2025-10-01", "08:00", "16:00", Double.NaN, null),
            repo.importWorkEntry(s, "2025-10-01", "08:00", "16:00", 9999.0, null),
            repo.importWorkEntry(s, "2025-10-01", "08:00", "16:00", 0.0, 99.0)
        )) {
            assertFalse(r.isSuccess)
        }
        // a failed import returns no state; the caller's state object stays valid
        assertEquals(1, s.dailyRecords["2025-10-01"]!!.size)
    }

    @Test
    fun overlappingImportsAreRejectedAdjacentOnesAccepted() {
        val s = repo.importWorkEntry(state(), "2025-10-01", "08:00", "12:00", 0.0, null).state!!
        assertFalse(repo.importWorkEntry(s, "2025-10-01", "11:00", "13:00", 0.0, null).isSuccess)
        assertFalse(repo.importWorkEntry(s, "2025-10-01", "07:00", "12:30", 0.0, null).isSuccess)
        assertTrue(repo.importWorkEntry(s, "2025-10-01", "12:00", "13:00", 0.0, null).isSuccess)
        assertTrue(repo.importWorkEntry(s, "2025-10-01", "07:00", "08:00", 0.0, null).isSuccess)
    }

    @Test
    fun importRejectsHolidaysWeekendsAndImpossibleDates() {
        for (d in listOf("2025-10-03", "2025-10-04", "2025-10-05", "2025-04-31", "2025-02-29", "2025-13-01", "garbage", ""))
            assertFalse(repo.importWorkEntry(state(), d, "08:00", "12:00", 0.0, null).isSuccess, d)
    }

    @Test
    fun importRejectsZeroLengthAndInvalidTimes() {
        for ((a, b) in listOf("00:00" to "00:00", "08:00" to "08:00", "24:00" to "08:00", "8:00" to "12:00", "08:00" to "12:60"))
            assertFalse(repo.importWorkEntry(state(), "2025-10-01", a, b, 0.0, null).isSuccess, "$a-$b")
    }

    @Test
    fun midnightCrossingImportIsAcceptedAndCountedOnItsStartDay() {
        val r = repo.importWorkEntry(state(), "2025-10-01", "22:00", "02:00", 0.0, null)
        assertTrue(r.isSuccess, r.error)
        assertEquals(4.0, r.state!!.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun importOfWholeDayWithManyShortBookingsAssignsNoNegativeNet() {
        // Regression: 16 back-to-back 25 minute bookings need a 30 minute break in total.
        val sessions = List(16) { fun h(m: Int) = "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
            h(8 * 60 + it * 25) to h(8 * 60 + (it + 1) * 25) }
        val breaks = TrackerCalculations.assignStatementBreaks(sessions)
        var s = state()
        sessions.forEachIndexed { i, (a, b) ->
            val r = repo.importWorkEntry(s, "2025-10-01", a, b, breaks[i], null)
            assertTrue(r.isSuccess, "session $i: ${r.error}")
            s = r.state!!
        }
        assertEquals((400 - 30) / 60.0, s.currentMonthData.istArbeitszeitLaufend, tol)
    }

    @Test
    fun parallelImportsOnIndependentStatesDoNotInterfere() {
        // The repository is stateless apart from storage; every thread works on its own copy.
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 64).map { n ->
                Callable {
                    var s = state()
                    repeat(5) { s = repo.importWorkEntry(s, "2025-10-01", "08:00", "12:00", 0.0, null).state!! }
                    repo.saveState(s)
                    repo.loadState()
                    s.dailyRecords["2025-10-01"]!!.size to n
                }
            }
            for (f in pool.invokeAll(tasks)) assertEquals(1, f.get().first)
        } finally {
            pool.shutdownNow()
        }
        // storage holds one consistent document after the race
        val loaded = repo.loadState()
        assertEquals(1, loaded.dailyRecords["2025-10-01"]!!.size)
    }

    @Test
    fun randomOperationSequencesKeepTheStateConsistent() {
        val rnd = Random(123)
        var s = state()
        val days = listOf("2025-10-01", "2025-10-02", "2025-10-06", "2025-10-07", "2025-11-03", "2025-12-24", "2025-12-31", "2025-10-03", "2025-02-30")
        fun t() = "${rnd.nextInt(0, 24).toString().padStart(2, '0')}:${listOf("00", "15", "30", "59", "60").random(rnd)}"
        repeat(1500) {
            when (rnd.nextInt(6)) {
                0, 1, 2 -> repo.importWorkEntry(s, days.random(rnd), t(), t(), rnd.nextInt(-10, 90).toDouble(), listOf(null, 0.0, 2.0, 50.0).random(rnd))
                    .state?.let { s = it }
                3 -> repo.addWorkEntry(s, days.random(rnd), t(), t(), 30.0, null).state?.let { s = it }
                4 -> s.dailyRecords.values.flatten().randomOrNull(rnd)?.let { s = repo.deleteEntry(s, it.id) }
                else -> s = repo.closeSpecificMonth(s, 2025, rnd.nextInt(1, 13)).first
            }
            for ((d, es) in s.dailyRecords) {
                assertTrue(es.isNotEmpty(), "empty day list kept for $d")
                for (e in es) {
                    assertEquals(d, e.datum)
                    assertTrue(e.totalStundenHeute >= 0 && e.remoteStundenHeute <= e.totalStundenHeute + 1e-9)
                    assertTrue(TrackerCalculations.isValidTime(e.startTime) && TrackerCalculations.isValidTime(e.endTime))
                }
                val sorted = es.sortedBy { TrackerCalculations.timeToMinutes(it.startTime) }
                for (i in 0 until sorted.size - 1) {
                    val a = sorted[i]; val b = sorted[i + 1]
                    val aEnd = TrackerCalculations.timeToMinutes(a.endTime).let { x -> if (x < TrackerCalculations.timeToMinutes(a.startTime)) x + 1440 else x }
                    assertTrue(aEnd <= TrackerCalculations.timeToMinutes(b.startTime), "overlap on $d: ${a.startTime}-${a.endTime} / ${b.startTime}-${b.endTime}")
                }
            }
            assertEquals(s.monatlicheHistorie.map { it.jahr to it.monat }.distinct().size, s.monatlicheHistorie.size)
        }
        // survives a full backup/restore cycle
        assertTrue(repo.restoreState(repo.serializeState(s)).isSuccess)
    }

    // === (6) withFlexBalance =================================================================

    @Test
    fun flexBalanceWithOddValues() {
        val base = state()
        val inf = Double.POSITIVE_INFINITY
        assertEquals(base, repo.withFlexBalance(base, null))
        assertEquals(base, repo.withFlexBalance(base, FlexBalance()))
        assertEquals(base, repo.withFlexBalance(base, FlexBalance(Double.NaN, inf, -inf)))
        assertEquals(base, repo.withFlexBalance(base, FlexBalance(1e5, -1e5, Double.MAX_VALUE)))
        assertEquals(base, repo.withFlexBalance(base, FlexBalance(10000.0, -10000.0, 1e300)))
        val partial = repo.withFlexBalance(base, FlexBalance(Double.NaN, 3.0, 9999.99, 2025, 10)).gleitzeitSaldo!!
        assertNull(partial.previous)
        assertEquals(3.0, partial.current!!, tol)
        assertEquals(9999.99, partial.total!!, tol)
        // -0.0 and tiny values are fine
        assertEquals(-0.0, repo.withFlexBalance(base, FlexBalance(total = -0.0)).gleitzeitSaldo!!.total!!, tol)
        assertEquals(Double.MIN_VALUE, repo.withFlexBalance(base, FlexBalance(total = Double.MIN_VALUE)).gleitzeitSaldo!!.total!!, 0.0)
    }

    @Test
    fun flexBalanceSurvivesSerialization() {
        val s = repo.withFlexBalance(state(), FlexBalance(1.5, -2.5, -1.0, 2025, 10))
        val r = repo.restoreState(repo.serializeState(s))
        assertTrue(r.isSuccess, r.error)
        assertEquals(s.gleitzeitSaldo, r.state!!.gleitzeitSaldo)
    }

    @Test
    fun flexBalanceKeepsPreviousValueWhenNewOneIsUnusable() {
        val s1 = repo.withFlexBalance(state(), FlexBalance(total = 5.0))
        val s2 = repo.withFlexBalance(s1, FlexBalance(total = Double.NaN))
        assertEquals(s1, s2)
        assertNotNull(s2.gleitzeitSaldo)
    }

    @Test
    fun flexBalanceDoesNotTouchOtherState() {
        val s = add(state(), "2025-10-01", "08:00", "16:30").state!!
        val r = repo.withFlexBalance(s, FlexBalance(total = 1.0))
        assertEquals(s.copy(gleitzeitSaldo = r.gleitzeitSaldo), r)
    }
}
