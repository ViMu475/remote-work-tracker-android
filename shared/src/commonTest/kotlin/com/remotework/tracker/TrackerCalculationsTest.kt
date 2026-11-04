package com.remotework.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackerCalculationsTest {

    private val tol = 1e-9

    // --- timeToMinutes ------------------------------------------------------------------------

    @Test
    fun timeToMinutes_parsesHoursAndMinutes() {
        assertEquals(0, TrackerCalculations.timeToMinutes("00:00"))
        assertEquals(8 * 60 + 30, TrackerCalculations.timeToMinutes("08:30"))
        assertEquals(23 * 60 + 59, TrackerCalculations.timeToMinutes("23:59"))
    }

    @Test
    fun timeToMinutes_invalidInputGivesZero() {
        assertEquals(0, TrackerCalculations.timeToMinutes(""))
        assertEquals(0, TrackerCalculations.timeToMinutes("0830"))
        assertEquals(0, TrackerCalculations.timeToMinutes("ab:cd"))
        assertEquals(0, TrackerCalculations.timeToMinutes("08:30:00"))
    }

    // --- isValidTime ----------------------------------------------------------------------------

    @Test
    fun isValidTime_acceptsOnlyStrictHhMm() {
        for (ok in listOf("00:00", "08:05", "12:30", "23:59")) assertTrue(TrackerCalculations.isValidTime(ok), ok)
        for (bad in listOf("", "8:05", "24:00", "23:60", "99:99", "ab:cd", "08:5", "08:05:00", " 08:05", "0805")) {
            assertFalse(TrackerCalculations.isValidTime(bad), "expected invalid: '$bad'")
        }
    }

    // --- calculateNetHours --------------------------------------------------------------------

    @Test
    fun netHours_subtractsPause() {
        val r = TrackerCalculations.calculateNetHours("08:00", "16:30", 30.0)
        assertEquals(8.5, r.grossHours, tol)
        assertEquals(8.0, r.netHours, tol)
        assertNull(r.pauseWarning)
    }

    @Test
    fun netHours_overnightShiftAddsADay() {
        val r = TrackerCalculations.calculateNetHours("22:00", "02:00", 0.0)
        assertEquals(4.0, r.grossHours, tol)
        assertEquals(4.0, r.netHours, tol)
    }

    @Test
    fun netHours_pauseLongerThanWorkGivesNegativeNet() {
        val r = TrackerCalculations.calculateNetHours("08:00", "08:30", 60.0)
        assertEquals(-0.5, r.netHours, tol)
    }

    @Test
    fun pauseWarning_noMinimumBelowSixHours() {
        assertNull(TrackerCalculations.calculateNetHours("08:00", "13:59", 0.0).pauseWarning)
    }

    @Test
    fun pauseWarning_halfHourRequiredFromSixHours() {
        val warn = TrackerCalculations.calculateNetHours("08:00", "14:00", 0.0).pauseWarning
        assertNotNull(warn)
        assertTrue(warn.startsWith("WARNUNG"))
        assertTrue(warn.contains("0.50h"))
        assertNull(TrackerCalculations.calculateNetHours("08:00", "14:00", 30.0).pauseWarning)
    }

    @Test
    fun pauseWarning_threeQuartersRequiredFromNineHours() {
        val warn = TrackerCalculations.calculateNetHours("08:00", "17:00", 30.0).pauseWarning
        assertNotNull(warn)
        assertTrue(warn.contains("0.75h"))
        assertNull(TrackerCalculations.calculateNetHours("08:00", "17:00", 45.0).pauseWarning)
    }

    // --- isValidWorkingDay --------------------------------------------------------------------

    @Test
    fun workingDay_weekdayIsValid() {
        val r = TrackerCalculations.isValidWorkingDay("2025-10-06") // Monday
        assertTrue(r.isValid)
        assertNull(r.errorMessage)
    }

    @Test
    fun workingDay_holidayIsRejected() {
        val r = TrackerCalculations.isValidWorkingDay("2025-10-03") // Friday, Tag der Deutschen Einheit
        assertFalse(r.isValid)
        assertTrue(r.errorMessage!!.contains("Feiertag"))
    }

    @Test
    fun workingDay_holidayWinsOverWeekend() {
        // 2026-11-01 is a Sunday and Allerheiligen
        val r = TrackerCalculations.isValidWorkingDay("2026-11-01")
        assertFalse(r.isValid)
        assertTrue(r.errorMessage!!.contains("Feiertag"))
    }

    @Test
    fun workingDay_sundayAndSaturdayAreRejectedWithDifferentMessages() {
        val sunday = TrackerCalculations.isValidWorkingDay("2025-10-05")
        val saturday = TrackerCalculations.isValidWorkingDay("2025-10-04")
        assertFalse(sunday.isValid)
        assertTrue(sunday.errorMessage!!.contains("Sonntag"))
        assertFalse(saturday.isValid)
        assertTrue(saturday.errorMessage!!.contains("Samstag"))
    }

    @Test
    fun workingDay_invalidFormatIsRejected() {
        for (bad in listOf("", "06.10.2025", "2025-13-01", "2025-02-30", "heute")) {
            val r = TrackerCalculations.isValidWorkingDay(bad)
            assertFalse(r.isValid, "expected invalid: $bad")
            assertEquals("Ungültiges Datumsformat.", r.errorMessage)
        }
    }

    // --- checkTimeOverlap ---------------------------------------------------------------------

    private fun entry(id: String, start: String, end: String) = WorkEntry(
        id = id, datum = "2025-10-06", startTime = start, endTime = end,
        pauseMinutes = 0.0, remoteStundenHeute = 0.0, totalStundenHeute = 0.0
    )

    private val existing = listOf(entry("a", "09:00", "11:00"))

    private fun overlap(start: String, end: String, exclude: String? = null) =
        TrackerCalculations.checkTimeOverlap("2025-10-06", start, end, existing, exclude)

    @Test
    fun overlap_noEntriesMeansNoOverlap() {
        assertNull(TrackerCalculations.checkTimeOverlap("2025-10-06", "09:00", "10:00", emptyList()))
    }

    @Test
    fun overlap_detectsAllOverlapShapes() {
        assertNotNull(overlap("10:00", "12:00"), "starts inside")
        assertNotNull(overlap("08:00", "10:00"), "ends inside")
        assertNotNull(overlap("08:00", "12:00"), "contains existing")
        assertNotNull(overlap("09:30", "10:30"), "inside existing")
        assertNotNull(overlap("09:00", "11:00"), "identical")
    }

    @Test
    fun overlap_touchingEntriesDoNotOverlap() {
        assertNull(overlap("11:00", "12:00"))
        assertNull(overlap("08:00", "09:00"))
    }

    @Test
    fun overlap_messageNamesTheConflictingEntry() {
        assertTrue(overlap("10:00", "12:00")!!.contains("09:00-11:00"))
    }

    @Test
    fun overlap_excludedEntryIsIgnored() {
        assertNull(overlap("09:30", "10:30", exclude = "a"))
    }

    // --- calculateSollStundenMonth ------------------------------------------------------------

    @Test
    fun soll_novemberTwentySixHas21WeekdaysAndNoWeekdayHoliday() {
        // 1 Nov 2026 is a Sunday (Allerheiligen on a weekend)
        assertEquals(168.0, TrackerCalculations.calculateSollStundenMonth(2026, 11, 8.0), tol)
    }

    @Test
    fun soll_octoberTwentyFiveSubtractsGermanUnityDay() {
        // 23 weekdays minus Fri 3 Oct
        assertEquals(176.0, TrackerCalculations.calculateSollStundenMonth(2025, 10, 8.0), tol)
    }

    @Test
    fun soll_scalesWithHoursPerDay() {
        assertEquals(165.0, TrackerCalculations.calculateSollStundenMonth(2025, 10, 7.5), tol)
    }

    @Test
    fun soll_februaryLeapYearVersusNormalYear() {
        // Feb 2024 (leap) has 21 weekdays, Feb 2025 has 20
        assertEquals(168.0, TrackerCalculations.calculateSollStundenMonth(2024, 2, 8.0), tol)
        assertEquals(160.0, TrackerCalculations.calculateSollStundenMonth(2025, 2, 8.0), tol)
    }

    @Test
    fun soll_decemberSubtractsChristmasDays() {
        // Dec 2025: 23 weekdays minus Thu 25 and Fri 26, minus Wed 24 (off) and half of Wed 31
        assertEquals(156.0, TrackerCalculations.calculateSollStundenMonth(2025, 12, 8.0), tol)
    }

    @Test
    fun soll_2027SubtractsWeekdayHolidays() {
        // Jan: 21 weekdays minus Fri 1 Jan and Wed 6 Jan
        assertEquals(152.0, TrackerCalculations.calculateSollStundenMonth(2027, 1, 8.0), tol)
        // Mar: 23 weekdays minus Good Friday (26th) and Easter Monday (29th)
        assertEquals(168.0, TrackerCalculations.calculateSollStundenMonth(2027, 3, 8.0), tol)
        // May: 21 weekdays minus Ascension (6th), Whit Monday (17th), Corpus Christi (27th); 1 May is a Saturday
        assertEquals(144.0, TrackerCalculations.calculateSollStundenMonth(2027, 5, 8.0), tol)
        // Dec: Christmas days fall on a weekend
        // Fri 24 off, Fri 31 half
        assertEquals(172.0, TrackerCalculations.calculateSollStundenMonth(2027, 12, 8.0), tol)
    }

    @Test
    fun soll_2028SubtractsWeekdayHolidays() {
        // Apr: Good Friday (14th) and Easter Monday (17th)
        assertEquals(144.0, TrackerCalculations.calculateSollStundenMonth(2028, 4, 8.0), tol)
        // Jun: Whit Monday (5th) and Corpus Christi (15th)
        assertEquals(160.0, TrackerCalculations.calculateSollStundenMonth(2028, 6, 8.0), tol)
        // Dec: Mon 25 and Tue 26 Dec
        assertEquals(152.0, TrackerCalculations.calculateSollStundenMonth(2028, 12, 8.0), tol)
    }

    @Test
    fun soll_2029SubtractsWeekdayHolidays() {
        // Jan: 23 weekdays minus Mon 1 Jan (6 Jan is a Saturday)
        assertEquals(176.0, TrackerCalculations.calculateSollStundenMonth(2029, 1, 8.0), tol)
        // Apr: Easter Monday (2nd); Good Friday is on 30 Mar
        assertEquals(160.0, TrackerCalculations.calculateSollStundenMonth(2029, 4, 8.0), tol)
        // May: 1 May, Ascension (10th), Whit Monday (21st), Corpus Christi (31st)
        assertEquals(152.0, TrackerCalculations.calculateSollStundenMonth(2029, 5, 8.0), tol)
        // Dec: Tue 25 and Wed 26 Dec
        // Mon 24 off, Mon 31 half
        assertEquals(140.0, TrackerCalculations.calculateSollStundenMonth(2029, 12, 8.0), tol)
    }

    @Test
    fun workingDay_holidaysInThe2027To2029TablesAreRejected() {
        for (d in listOf("2027-03-29", "2027-05-27", "2028-04-17", "2028-06-15", "2029-03-30", "2029-05-31")) {
            val r = TrackerCalculations.isValidWorkingDay(d)
            assertFalse(r.isValid, d)
            assertTrue(r.errorMessage!!.contains("Feiertag"), d)
        }
    }

    @Test
    fun soll_2030SubtractsWeekdayHolidays() {
        // Jan: Tue 1 Jan (6 Jan is a Sunday)
        assertEquals(176.0, TrackerCalculations.calculateSollStundenMonth(2030, 1, 8.0), tol)
        // Apr: Good Friday (19th) and Easter Monday (22nd)
        assertEquals(160.0, TrackerCalculations.calculateSollStundenMonth(2030, 4, 8.0), tol)
        // May: 1 May and Ascension (30th)
        assertEquals(168.0, TrackerCalculations.calculateSollStundenMonth(2030, 5, 8.0), tol)
        // Jun: Whit Monday (10th) and Corpus Christi (20th)
        assertEquals(144.0, TrackerCalculations.calculateSollStundenMonth(2030, 6, 8.0), tol)
        // Nov: All Saints (Fri 1st)
        assertEquals(160.0, TrackerCalculations.calculateSollStundenMonth(2030, 11, 8.0), tol)
        // Dec: Wed 25 and Thu 26 Dec
        // Tue 24 off, Tue 31 half
        assertEquals(148.0, TrackerCalculations.calculateSollStundenMonth(2030, 12, 8.0), tol)
    }

    @Test
    fun workingDay_holidaysIn2030AreRejected() {
        for (d in listOf("2030-04-19", "2030-04-22", "2030-05-30", "2030-06-10", "2030-06-20", "2030-11-01")) {
            val r = TrackerCalculations.isValidWorkingDay(d)
            assertFalse(r.isValid, d)
            assertTrue(r.errorMessage!!.contains("Feiertag"), d)
        }
    }

    // --- quarter ------------------------------------------------------------------------------

    @Test
    fun quartal_mapsMonthsToQuarters() {
        assertEquals("Q1/2026", TrackerCalculations.getQuartalForMonth("Januar", 2026))
        assertEquals("Q1/2026", TrackerCalculations.getQuartalForMonth("März", 2026))
        assertEquals("Q2/2026", TrackerCalculations.getQuartalForMonth("April", 2026))
        assertEquals("Q3/2026", TrackerCalculations.getQuartalForMonth("September", 2026))
        assertEquals("Q4/2025", TrackerCalculations.getQuartalForMonth("Dezember", 2025))
    }

    @Test
    fun quartal_unknownMonthFallsBackToQ1() {
        assertEquals("Q1/2026", TrackerCalculations.getQuartalForMonth("Smarch", 2026))
    }

    // --- break of an imported statement day (derived from real statements; fictional values here) ----

    @Test
    fun statementBreak_followsTheTimeSystemRule() {
        fun h(h: Int, m: Int = 0) = h * 60 + m
        assertEquals(0, TrackerCalculations.statementBreakMinutes(h(4)))
        assertEquals(0, TrackerCalculations.statementBreakMinutes(h(6)))
        assertEquals(2, TrackerCalculations.statementBreakMinutes(h(6, 2)))     // only the excess above 6 h
        assertEquals(30, TrackerCalculations.statementBreakMinutes(h(6, 30)))
        assertEquals(30, TrackerCalculations.statementBreakMinutes(h(9, 30)))
        assertEquals(40, TrackerCalculations.statementBreakMinutes(h(9, 40)))   // net never below 9 h
        assertEquals(45, TrackerCalculations.statementBreakMinutes(h(10)))
        assertEquals(0, TrackerCalculations.statementBreakMinutes(h(8), gapMinutes = 45))
        assertEquals(20, TrackerCalculations.statementBreakMinutes(h(8), gapMinutes = 10))
    }

    @Test
    fun assignStatementBreaks_perDay() {
        assertEquals(listOf(30.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "17:00")))
        assertEquals(listOf(0.0, 0.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "11:00", "12:00" to "17:00")))
        assertEquals(listOf(0.0, 20.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "11:00", "11:10" to "17:00")))
        assertEquals(listOf(0.0, 0.0), TrackerCalculations.assignStatementBreaks(listOf("12:00" to "17:00", "08:00" to "11:00")))
        assertEquals(listOf(0.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "13:00")))
        assertTrue(TrackerCalculations.assignStatementBreaks(emptyList()).isEmpty())
    }

    @Test
    fun statementBreak_bookingOverSixHoursKeepsAMinimumBreakDespiteAGap() {
        fun h(h: Int, m: Int = 0) = h * 60 + m
        assertEquals(30, TrackerCalculations.statementBreakMinutes(h(9), gapMinutes = 45, longestBlockMinutes = h(7)))
        assertEquals(0, TrackerCalculations.statementBreakMinutes(h(9), gapMinutes = 45, longestBlockMinutes = h(6)))
        assertEquals(30, TrackerCalculations.statementBreakMinutes(h(9, 32), gapMinutes = 45, longestBlockMinutes = h(7, 27)))
        assertEquals(10, TrackerCalculations.statementBreakMinutes(h(6, 10), gapMinutes = 60, longestBlockMinutes = h(6, 5)))
        assertEquals(20, TrackerCalculations.statementBreakMinutes(h(8), gapMinutes = 10, longestBlockMinutes = h(5)))
    }

    @Test
    fun assignStatementBreaks_bookingOverSixHoursLosesThirtyMinutesDespiteTheGap() {
        assertEquals(listOf(0.0, 30.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "10:00", "10:45" to "18:00")))
        assertEquals(listOf(0.0, 0.0), TrackerCalculations.assignStatementBreaks(listOf("08:00" to "10:00", "10:45" to "16:45")))
    }

    // --- Special days 24 Dec (off) and 31 Dec (half day) ---------------------------------------

    @Test
    fun december2025HasTwoHolidaysAnOffDayAndAHalfDay() {
        // 23 weekdays - 25./26.12. (holidays) - 24.12. (off) - 0.5 (31.12.) = 19.5 days
        assertEquals(19.5 * 8.0, TrackerCalculations.calculateSollStundenMonth(2025, 12, 8.0), tol)
    }

    @Test
    fun december2026CountsOnly20AndAHalfDays() {
        // 23 weekdays - 25.12. (holiday) - 24.12. (off) - 0.5 (31.12.)
        assertEquals(20.5 * 8.0, TrackerCalculations.calculateSollStundenMonth(2026, 12, 8.0), tol)
    }

    @Test
    fun specialDaysAreFullDaysInOtherMonths() {
        assertEquals(1.0, TrackerCalculations.specialDayFactor(11, 24), tol)
        assertEquals(1.0, TrackerCalculations.specialDayFactor(1, 31), tol)
        assertEquals(0.0, TrackerCalculations.specialDayFactor(12, 24), tol)
        assertEquals(0.5, TrackerCalculations.specialDayFactor(12, 31), tol)
    }
}
