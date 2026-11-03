package com.remotework.tracker

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edge cases and seeded property tests for TrackerCalculations (fictional values only). */
class RobustCalculationsTest {

    private val tol = 1e-9

    private fun hhmm(minutes: Int) =
        (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')

    // --- time helpers -------------------------------------------------------------------------

    @Test
    fun midnightAndZeroLengthSpans() {
        val full = TrackerCalculations.calculateNetHours("00:00", "00:00", 0.0)
        assertEquals(0.0, full.grossHours, tol)            // 00:00-00:00 is a zero-length span in the calculator
        val wrap = TrackerCalculations.calculateNetHours("23:00", "01:00", 0.0)
        assertEquals(2.0, wrap.grossHours, tol)
        val toMidnight = TrackerCalculations.calculateNetHours("16:00", "00:00", 30.0)
        assertEquals(8.0, toMidnight.grossHours, tol)
        assertEquals(7.5, toMidnight.netHours, tol)
    }

    @Test
    fun invalidTimesNeverThrow() {
        for (t in listOf("", ":", "abc", "24:00", "99:99", "-1:30", "8:5", "08:60", "08:00:00", " 08:00", "08:00 ", "٠٨:00", "08.00")) {
            TrackerCalculations.timeToMinutes(t)
            TrackerCalculations.calculateNetHours(t, "10:00", 0.0)
            TrackerCalculations.calculateNetHours("10:00", t, 0.0)
            assertFalse(TrackerCalculations.isValidTime(t), "'$t' must be invalid")
        }
    }

    @Test
    fun isValidTimeMatchesExactlyTheClockRange() {
        for (h in 0..24) for (m in listOf(0, 1, 59, 60)) {
            val s = h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
            assertEquals(h < 24 && m < 60, TrackerCalculations.isValidTime(s), s)
        }
    }

    @Test
    fun isValidTimeRejectsTrailingNewline() {
        // Kotlin matches() is anchored to the whole input, so a trailing newline must not slip through.
        assertFalse(TrackerCalculations.isValidTime("08:00\n"))
        assertFalse(TrackerCalculations.isValidTime("\n08:00"))
    }

    @Test
    fun hugeAndOddPauseValuesDoNotThrow() {
        for (p in listOf(0.0, -1.0, 1e300, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            TrackerCalculations.calculateNetHours("08:00", "16:00", p)
        }
    }

    // --- calendar -------------------------------------------------------------------------------

    @Test
    fun invalidDatesAreRejectedNotThrown() {
        for (d in listOf("", "2025-13-01", "2025-00-10", "2025-02-30", "2025-04-31", "2025-6-1", "01.10.2025", "20251001", "2025-10-01T00:00", "abc", "2025-10-01 ", "\u0000")) {
            assertFalse(TrackerCalculations.isValidWorkingDay(d).isValid, "'$d'")
        }
    }

    @Test
    fun yearsOutsideHolidayTableHaveNoHolidays() {
        assertFalse(BavarianHolidays.isHoliday("2024-12-25"))
        assertFalse(BavarianHolidays.isHoliday("2031-01-01"))
        // Soll for a year without table: 25 Dec 2031 (Thursday) counts as working day
        val dec2031 = TrackerCalculations.calculateSollStundenMonth(2031, 12, 8.0)
        assertTrue(dec2031 > 0.0)
    }

    @Test
    fun sollHoursNeverThrowForWideYearRange() {
        for (year in listOf(1, 1900, 1999, 2000, 2024, 2025, 2030, 2031, 2100, 9999)) {
            for (month in 1..12) {
                val soll = TrackerCalculations.calculateSollStundenMonth(year, month, 8.0)
                assertTrue(soll in 0.0..(31 * 8.0), "$year-$month: $soll")
            }
        }
    }

    @Test
    fun sollHoursScaleLinearlyWithDailyHours() {
        for (month in 1..12) {
            val a = TrackerCalculations.calculateSollStundenMonth(2026, month, 8.0)
            val b = TrackerCalculations.calculateSollStundenMonth(2026, month, 4.0)
            assertEquals(a / 2, b, tol)
        }
    }

    @Test
    fun specialDaysInDecember() {
        assertEquals(0.0, TrackerCalculations.specialDayFactor(12, 24), tol)
        assertEquals(0.5, TrackerCalculations.specialDayFactor(12, 31), tol)
        assertEquals(1.0, TrackerCalculations.specialDayFactor(11, 24), tol)
        assertEquals(1.0, TrackerCalculations.specialDayFactor(1, 31), tol)
        assertEquals(1.0, TrackerCalculations.specialDayFactor(12, 23), tol)
        assertEquals(1.0, TrackerCalculations.specialDayFactor(12, 25), tol)
        assertEquals(1.0, TrackerCalculations.specialDayFactor(12, 30), tol)
    }

    @Test
    fun specialDaysReduceDecemberOnlyWhenOnWeekdays() {
        // 2025: 24 Dec is Wed, 31 Dec is Wed -> 22 weekdays - 2 holidays(25,26) = 20 ... minus 1 minus 0.5
        // Reference: count weekdays minus holidays by hand with factors.
        for (year in 2025..2030) {
            var expected = 0.0
            for (day in 1..31) {
                val d = kotlinx.datetime.LocalDate(year, 12, day)
                val dow = d.dayOfWeek
                if (dow == kotlinx.datetime.DayOfWeek.SATURDAY || dow == kotlinx.datetime.DayOfWeek.SUNDAY) continue
                if (BavarianHolidays.isHoliday(d.toString())) continue
                expected += 8.0 * when (day) { 24 -> 0.0; 31 -> 0.5; else -> 1.0 }
            }
            assertEquals(expected, TrackerCalculations.calculateSollStundenMonth(year, 12, 8.0), tol, "Dec $year")
        }
    }

    @Test
    fun leapYearFebruary() {
        // Feb 2028 (leap): 29 days, starts Tuesday -> 21 weekdays, no holidays
        assertEquals(21 * 8.0, TrackerCalculations.calculateSollStundenMonth(2028, 2, 8.0), tol)
        // Feb 2027: 28 days, starts Monday -> 20 weekdays
        assertEquals(20 * 8.0, TrackerCalculations.calculateSollStundenMonth(2027, 2, 8.0), tol)
    }

    @Test
    fun holidayAndWeekendEntriesAreRefused() {
        assertFalse(TrackerCalculations.isValidWorkingDay("2025-12-25").isValid)
        assertFalse(TrackerCalculations.isValidWorkingDay("2030-12-26").isValid)
        assertFalse(TrackerCalculations.isValidWorkingDay("2025-10-04").isValid)  // Saturday
        assertFalse(TrackerCalculations.isValidWorkingDay("2025-10-05").isValid)  // Sunday
        assertTrue(TrackerCalculations.isValidWorkingDay("2025-12-24").isValid)
        assertTrue(TrackerCalculations.isValidWorkingDay("2025-12-31").isValid)
    }

    @Test
    fun quartalForAllMonthsAndOddNames() {
        val expected = listOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4)
        MonthNames.german.forEachIndexed { i, name ->
            assertEquals("Q${expected[i]}/2026", TrackerCalculations.getQuartalForMonth(name, 2026))
        }
        assertEquals("Q1/2026", TrackerCalculations.getQuartalForMonth("Unsinn", 2026))
        assertEquals("Q1/2026", TrackerCalculations.getQuartalForMonth("", 2026))
        assertEquals("Q1/-5", TrackerCalculations.getQuartalForMonth("Januar", -5))
        assertEquals("Januar", MonthNames.getMonthName(0))
        assertEquals("Januar", MonthNames.getMonthName(13))
        assertEquals("Januar", MonthNames.getMonthName(-3))
    }

    // --- overlap --------------------------------------------------------------------------------

    private fun entry(id: String, s: String, e: String) =
        WorkEntry(id, "2025-10-01", s, e, 0.0, 0.0, 0.0)

    @Test
    fun overlapSymmetryAndAdjacency() {
        val a = entry("a", "08:00", "12:00")
        assertNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "12:00", "16:00", listOf(a)))
        assertNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "04:00", "08:00", listOf(a)))
        assertNotNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "11:59", "13:00", listOf(a)))
        assertNotNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "07:00", "08:01", listOf(a)))
        assertNotNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "09:00", "10:00", listOf(a)))
        assertNotNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "06:00", "18:00", listOf(a)))
        assertNotNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "08:00", "12:00", listOf(a)))
    }

    @Test
    fun overlapWithOwnIdIsIgnoredAndMidnightSpanWorks() {
        val a = entry("a", "22:00", "02:00")
        assertNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "22:00", "02:00", listOf(a), excludeId = "a"))
        assertNotNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "23:00", "23:30", listOf(a)))
        assertNull(TrackerCalculations.checkTimeOverlap("2025-10-01", "10:00", "12:00", listOf(a)))
    }

    @Test
    fun overlapIsSymmetricForRandomIntervalsOnSameDay() {
        val rnd = Random(2024)
        repeat(2000) {
            val s1 = rnd.nextInt(0, 1439); val e1 = rnd.nextInt(s1 + 1, 1440)
            val s2 = rnd.nextInt(0, 1439); val e2 = rnd.nextInt(s2 + 1, 1440)
            val one = entry("1", hhmm(s1), hhmm(e1))
            val two = entry("2", hhmm(s2), hhmm(e2))
            val ab = TrackerCalculations.checkTimeOverlap("d", hhmm(s2), hhmm(e2), listOf(one)) != null
            val ba = TrackerCalculations.checkTimeOverlap("d", hhmm(s1), hhmm(e1), listOf(two)) != null
            val truth = s1 < e2 && s2 < e1
            assertEquals(truth, ab, "$s1-$e1 vs $s2-$e2")
            assertEquals(truth, ba, "$s2-$e2 vs $s1-$e1")
        }
    }

    // --- statement break: property tests --------------------------------------------------------

    @Test
    fun statementBreakProperties() {
        var prev = 0
        for (work in 0..1500) {
            val b = TrackerCalculations.statementBreakMinutes(work)
            assertTrue(b >= 0, "negative at $work")
            assertTrue(b <= work, "break $b > work $work")
            assertTrue(work - b >= minOf(work, 360), "net below 6 h at $work")
            assertTrue(b <= 45, "more than 45 at $work")
            assertEquals(b, TrackerCalculations.statementBreakMinutes(work), "deterministic")
            // net working time is monotonic in booked time
            val net = work - b
            assertTrue(net >= prev, "net time drops at $work")
            prev = net
        }
    }

    @Test
    fun statementBreakGapsOnlyReduceAndNeverGoNegative() {
        val rnd = Random(31337)
        repeat(3000) {
            val work = rnd.nextInt(0, 1500)
            val gap = rnd.nextInt(-300, 900)
            val withGap = TrackerCalculations.statementBreakMinutes(work, gap)
            val noGap = TrackerCalculations.statementBreakMinutes(work, 0)
            assertTrue(withGap in 0..noGap, "work=$work gap=$gap -> $withGap (no gap $noGap)")
        }
        // extreme arguments
        TrackerCalculations.statementBreakMinutes(Int.MAX_VALUE, Int.MAX_VALUE)
        TrackerCalculations.statementBreakMinutes(Int.MIN_VALUE, Int.MIN_VALUE)
        assertTrue(TrackerCalculations.statementBreakMinutes(Int.MAX_VALUE, Int.MIN_VALUE) >= 0)
    }

    private fun randomSessions(rnd: Random): List<Pair<String, String>> {
        val n = rnd.nextInt(1, 7)
        return List(n) {
            val s = rnd.nextInt(0, 1440)
            val len = rnd.nextInt(0, 700)
            hhmm(s) to hhmm((s + len) % 1440)
        }
    }

    @Test
    fun assignStatementBreaksProperties() {
        val rnd = Random(20251003)
        repeat(3000) {
            val sessions = randomSessions(rnd)
            val breaks = TrackerCalculations.assignStatementBreaks(sessions)
            assertEquals(sessions.size, breaks.size)
            assertTrue(breaks.all { it >= 0.0 && it.isFinite() }, "$sessions -> $breaks")
            assertEquals(breaks, TrackerCalculations.assignStatementBreaks(sessions), "deterministic")
            // total deduction never exceeds the total booked time
            val work = sessions.sumOf { (s, e) ->
                val a = TrackerCalculations.timeToMinutes(s)
                val b = TrackerCalculations.timeToMinutes(e).let { x -> if (x < a) x + 1440 else x }
                b - a
            }
            assertTrue(breaks.sum() <= work + tol, "sum ${breaks.sum()} > work $work for $sessions")
            assertTrue(breaks.sum() <= 45.0 + tol)
        }
    }

    @Test
    fun assignStatementBreaksIgnoresOrderOfSessionsForTheTotal() {
        val rnd = Random(77)
        repeat(1000) {
            val sessions = randomSessions(rnd)
            val a = TrackerCalculations.assignStatementBreaks(sessions).sum()
            val b = TrackerCalculations.assignStatementBreaks(sessions.shuffled(rnd)).sum()
            assertEquals(a, b, tol, "$sessions")
        }
    }

    @Test
    fun assignStatementBreaksEmptyAndDegenerate() {
        assertEquals(emptyList(), TrackerCalculations.assignStatementBreaks(emptyList()))
        assertEquals(listOf(0.0), TrackerCalculations.assignStatementBreaks(listOf("00:00" to "00:00")))
        assertEquals(listOf(0.0, 0.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "08:00", "08:00" to "08:00")))
        assertEquals(listOf(0.0), TrackerCalculations.assignStatementBreaks(listOf("xx" to "yy")))
    }

    /**
     * Each imported session must be able to carry the break assigned to it: the import computes
     * net = session length - pause and refuses negative values. Many short back-to-back bookings
     * of one day (no gaps) must therefore not put the whole day's break onto a too short session.
     */
    @Test
    fun breakAssignedToASessionNeverExceedsThatSession() {
        // 16 adjacent bookings of 25 minutes = 400 min booked, 30 min break required
        val sessions = List(16) { hhmm(8 * 60 + it * 25) to hhmm(8 * 60 + (it + 1) * 25) }
        val breaks = TrackerCalculations.assignStatementBreaks(sessions)
        assertEquals(30.0, breaks.sum(), tol)
        sessions.forEachIndexed { i, (s, e) ->
            val len = TrackerCalculations.timeToMinutes(e) - TrackerCalculations.timeToMinutes(s)
            assertTrue(breaks[i] <= len, "session $i: break ${breaks[i]} > length $len")
        }
    }
}
