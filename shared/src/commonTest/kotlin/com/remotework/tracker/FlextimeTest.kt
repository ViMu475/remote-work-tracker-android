package com.remotework.tracker

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flextime traffic light, month analysis and proposals. Same cases as flask-tracker
 * (tests/test_flextime.py, tests/test_flex_analysis.py), so every platform gives the same result (fictional data).
 */
class FlextimeTest {

    private val tol = 1e-9
    private val today = LocalDate(2026, 11, 6)     // a Friday: 17 working days are left in November 2026

    private fun state(
        entries: Map<String, List<WorkEntry>> = emptyMap(), series: List<FlexEntry> = emptyList(),
        saldo: FlexBalance? = null, perDay: Double = 8.0
    ) = TrackerState(
        aktuellerMonat = "Oktober", aktuellesJahr = 2026,
        currentMonthData = MonthData("Oktober", 2026, 40.0, 0.0, 0.0, 0.0, 0.0),
        currentQuartal = "Q4/2026", dailyRecords = entries, sollStundenProTag = perDay,
        gleitzeitSaldo = saldo, gleitzeitVerlauf = series
    )

    private fun e(year: Int, month: Int, total: Double? = null, change: Double? = null, prev: Double? = null) =
        FlexEntry(year, month, prev, change, total)

    private fun series(vararg balances: Double, changes: List<Double>? = null): List<FlexEntry> =
        balances.mapIndexed { i, b -> e(2026, i + 1, b, changes?.get(i)) }

    // ---- traffic light ----

    @Test
    fun statusThresholdsOfTheEmployeePortal() {
        val expected = listOf(
            0.0 to FlexStatus.OK, 39.99 to FlexStatus.OK, 40.0 to FlexStatus.OK, 40.01 to FlexStatus.YELLOW_POS,
            59.99 to FlexStatus.YELLOW_POS, 60.0 to FlexStatus.YELLOW_POS, 60.01 to FlexStatus.RED_POS, 250.0 to FlexStatus.RED_POS,
            -40.0 to FlexStatus.OK, -40.01 to FlexStatus.YELLOW_NEG, -60.0 to FlexStatus.YELLOW_NEG, -60.01 to FlexStatus.RED_NEG
        )
        for ((balance, status) in expected) assertEquals(status, Flextime.status(balance), "balance $balance")
    }

    @Test
    fun noNumberNoStatus() {
        assertNull(Flextime.status(null))
        assertNull(Flextime.status(Double.NaN))
    }

    // ---- series ----

    @Test
    fun cleanEntryKeepsValidEntriesAndRoundsNumbers() {
        assertEquals(e(2026, 5, 15.75, 3.25, 12.5), Flextime.cleanEntry(e(2026, 5, 15.75, 3.25, 12.5)))
        assertEquals(1.23, Flextime.cleanEntry(e(2026, 5, 1.23456))!!.gesamt!!, tol)
    }

    @Test
    fun cleanEntryRejectsDamagedEntries() {
        val bad = listOf(
            e(2026, 0, 1.0), e(2026, 13, 1.0), e(1800, 5, 1.0), e(2026, 5), e(2026, 5, Double.NaN),
            e(2026, 5, Double.POSITIVE_INFINITY), e(2026, 5, 1e9)
        )
        for (entry in bad) assertNull(Flextime.cleanEntry(entry), entry.toString())
    }

    @Test
    fun aDamagedNumberIsDroppedButTheEntryStays() {
        assertEquals(e(2026, 5, 2.0), Flextime.cleanEntry(e(2026, 5, 2.0, Double.NaN)))
    }

    @Test
    fun normalizeSortsAndKeepsOneEntryPerMonth() {
        val result = Flextime.normalize(listOf(e(2026, 3, 3.0), e(2025, 12, 1.0), e(2026, 3, 9.0), e(2025, 2, 0.5)))
        assertEquals(listOf(2025 to 2, 2025 to 12, 2026 to 3), result.map { it.jahr to it.monat })
        assertEquals(9.0, result.last().gesamt!!, tol)
    }

    @Test
    fun recordStoresTheThreeNumbersOfAStatement() {
        val result = Flextime.record(state(), FlexBalance(12.5, 3.25, 15.75, 2026, 5))
        assertEquals(listOf(e(2026, 5, 15.75, 3.25, 12.5)), result.gleitzeitVerlauf)
    }

    @Test
    fun recordReplacesTheSameMonthAndStaysSorted() {
        var s = state()
        s = Flextime.record(s, FlexBalance(total = 3.0), 2026, 3)
        s = Flextime.record(s, FlexBalance(total = 1.0), 2025, 12)
        s = Flextime.record(s, FlexBalance(total = 7.0), 2026, 3)
        assertEquals(listOf(2025 to 12, 2026 to 3), s.gleitzeitVerlauf.map { it.jahr to it.monat })
        assertEquals(7.0, s.gleitzeitVerlauf.last().gesamt!!, tol)
    }

    @Test
    fun recordWithoutMonthOrNumbersChangesNothing() {
        val s = state()
        assertEquals(s, Flextime.record(s, null))
        assertEquals(s, Flextime.record(s, FlexBalance(total = 1.0)))
        assertEquals(s, Flextime.record(s, FlexBalance(total = 1.0, year = 2026)))
        assertEquals(s, Flextime.record(s, FlexBalance(year = 2026, month = 5)))
    }

    @Test
    fun olderDataWithOnlyTheLatestBalanceStillShowsAPoint() {
        val s = state(saldo = FlexBalance(1.0, 2.0, 3.0, 2026, 2))
        assertEquals(listOf(e(2026, 2, 3.0, 2.0, 1.0)), Flextime.series(s))
    }

    @Test
    fun theLatestBalanceIsNotAddedTwice() {
        val s = state(saldo = FlexBalance(total = 3.0, year = 2026, month = 2), series = listOf(e(2026, 2, 8.0)))
        assertEquals(listOf(8.0), Flextime.series(s).map { it.gesamt })
    }

    @Test
    fun summaryCountsTheYellowAndRedMonths() {
        val s = Flextime.summarize(series(10.0, 45.0, 55.0, 70.0, -45.0, 5.0))
        assertEquals(3, s.monthsYellow)
        assertEquals(1, s.monthsRed)
        assertEquals(FlexStatus.OK, s.status)
        assertEquals(5.0, s.latest!!, tol)
        assertEquals(-45.0, s.min!!, tol)
        assertEquals(MonthRef(2026, 4), s.maxMonth)
    }

    @Test
    fun summaryOfNothing() {
        val s = Flextime.summarize(emptyList())
        assertNull(s.latest)
        assertNull(s.status)
        assertEquals(0, s.monthsYellow + s.monthsRed)
    }

    // ---- month analysis ----

    private fun entry(day: String, start: String, end: String, net: Double, remote: Double = 0.0) =
        WorkEntry(id = day, datum = day, startTime = start, endTime = end, pauseMinutes = 30.0, remoteStundenHeute = remote, totalStundenHeute = net)

    private fun november() = state(
        entries = mapOf(
            "2025-11-03" to listOf(entry("2025-11-03", "08:00", "16:30", 8.0, 8.0)),
            "2025-11-04" to listOf(entry("2025-11-04", "08:00", "19:00", 10.5)),         // more than 10 hours
            "2025-11-05" to listOf(entry("2025-11-05", "05:30", "14:00", 8.0, 4.0)),     // before 06:00
            "2025-11-06" to listOf(entry("2025-11-06", "12:00", "20:30", 8.0)),          // after 20:00
            "2025-11-08" to listOf(entry("2025-11-08", "09:00", "12:00", 3.0))           // Saturday
        ),
        series = listOf(FlexEntry(2025, 11, 20.0, -122.0, -102.0))
    )

    @Test
    fun totalsOfAFinishedMonth() {
        val a = Flextime.analyzeMonth(november(), 2025, 11, today)
        val t = a.totals
        assertTrue(a.complete)
        assertTrue(a.hasEntries)
        assertEquals(5, t.daysWorked)
        assertEquals(37.5, t.net, tol)
        assertEquals(12.0, t.remote, tol)
        assertEquals(160.0, t.soll, tol)
        assertEquals(-122.5, t.deltaCalc, tol)
        assertEquals(7.5, t.avgNet!!, tol)
        assertEquals("08:30", t.avgStart)
        assertEquals("16:24", t.avgEnd)
        assertEquals(LongestDay("2025-11-04", 10.5), t.longest)
    }

    @Test
    fun signsOfOvertimeAreCounted() {
        val t = Flextime.analyzeMonth(november(), 2025, 11, today).totals
        assertEquals(listOf(1, 1, 1, 1), listOf(t.longDays, t.earlyDays, t.lateDays, t.nonWorkingDays))
    }

    @Test
    fun workingDaysWithoutAnEntryAreListedWithTheirTargetHours() {
        val t = Flextime.analyzeMonth(november(), 2025, 11, today).totals
        assertEquals(16, t.missingDays.size)
        assertEquals(128.0, t.missingHours, tol)
        assertTrue("2025-11-07" in t.missingDays)
        assertFalse("2025-11-03" in t.missingDays)
    }

    @Test
    fun runningBalanceEndsAtNetMinusTarget() {
        val a = Flextime.analyzeMonth(november(), 2025, 11, today)
        assertEquals(a.totals.deltaCalc, a.days.last().balance, tol)
        assertEquals("2025-11-03", a.days.first().datum)
        assertEquals(0.0, a.days.first().balance, tol)
        assertEquals(2.5, a.days[1].balance, tol)                  // 8.0 - 8 + 10.5 - 8
    }

    @Test
    fun rowsCarryTheirFlags() {
        val rows = Flextime.analyzeMonth(november(), 2025, 11, today).days.associateBy { it.datum }
        assertEquals(listOf(DayFlag.LONG_DAY), rows.getValue("2025-11-04").flags)
        assertEquals(listOf(DayFlag.EARLY), rows.getValue("2025-11-05").flags)
        assertEquals(listOf(DayFlag.LATE), rows.getValue("2025-11-06").flags)
        assertEquals(listOf(DayFlag.NON_WORKING_DAY), rows.getValue("2025-11-08").flags)
        assertEquals(listOf(DayFlag.NO_ENTRY), rows.getValue("2025-11-07").flags)
        assertEquals(0.0, rows.getValue("2025-11-08").soll, tol)
        assertEquals(6, rows.getValue("2025-11-08").weekday)       // Saturday, ISO number
    }

    @Test
    fun weeksAddUpToTheMonth() {
        val a = Flextime.analyzeMonth(november(), 2025, 11, today)
        assertEquals(listOf(45, 46, 47, 48), a.weeks.map { it.week })
        assertEquals(a.totals.deltaCalc, a.weeks.sumOf { it.delta }, 0.001)
        assertEquals(37.5, a.weeks[0].net, tol)
        assertEquals(40.0, a.weeks[0].soll, tol)
        assertEquals(5, a.weeks[0].days)
    }

    @Test
    fun isoWeekAtTheTurnOfTheYear() {
        // 2025-12-29 and 2025-12-31 belong to ISO week 1 of 2026
        val s = state(entries = mapOf(
            "2025-12-29" to listOf(entry("2025-12-29", "08:00", "16:30", 8.0)),
            "2025-12-31" to listOf(entry("2025-12-31", "08:00", "12:00", 4.0))
        ))
        val weeks = Flextime.analyzeMonth(s, 2025, 12, today).weeks.filter { it.days > 0 }.map { it.year to it.week }.distinct()
        assertEquals(listOf(2026 to 1), weeks)
    }

    @Test
    fun statementNumbersAndTheDifference() {
        val a = Flextime.analyzeMonth(november(), 2025, 11, today)
        assertEquals(FlexEntry(2025, 11, 20.0, -122.0, -102.0), a.flex)
        assertEquals(FlexStatus.RED_NEG, a.flexStatus)
        assertEquals(0.5, a.difference!!, tol)                      // statement change minus net-minus-target
    }

    @Test
    fun aRunningMonthCountsTheTargetOnlyUntilToday() {
        val s = state(entries = mapOf("2026-03-02" to listOf(entry("2026-03-02", "08:00", "16:30", 8.0))))
        val a = Flextime.analyzeMonth(s, 2026, 3, LocalDate(2026, 3, 15))
        assertFalse(a.complete)
        assertEquals(80.0, a.totals.soll, tol)                      // 10 working days up to the 13th
        assertNull(a.difference)
    }

    @Test
    fun aFutureMonthHasNoTargetAndNoRows() {
        val a = Flextime.analyzeMonth(state(), 2026, 6, LocalDate(2026, 3, 15))
        assertTrue(a.days.isEmpty())
        assertEquals(0.0, a.totals.soll, tol)
        assertFalse(a.hasEntries)
    }

    @Test
    fun aMonthWithoutEntriesListsAllWorkingDaysAsMissing() {
        val a = Flextime.analyzeMonth(state(), 2025, 11, today)
        assertFalse(a.hasEntries)
        assertEquals(20, a.totals.missingDays.size)
        assertNull(a.totals.avgNet)
        assertNull(a.totals.longest)
    }

    @Test
    fun theTargetFollowsTheState() {
        assertEquals(150.0, Flextime.analyzeMonth(state(perDay = 7.5), 2025, 11, today).totals.soll, tol)
    }

    @Test
    fun damagedEntriesDoNotBreakTheAnalysis() {
        val s = state(entries = mapOf(
            "kaputt" to listOf(entry("kaputt", "08:00", "09:00", 1.0)),
            "2025-11-03" to listOf(entry("2025-11-03", "x", "y", Double.NaN, Double.POSITIVE_INFINITY))
        ))
        val a = Flextime.analyzeMonth(s, 2025, 11, today)
        assertEquals(0.0, a.totals.net, tol)
        assertEquals(2025, a.year)
    }

    @Test
    fun christmasEveHasNoTargetNewYearsEveHalf() {
        assertFalse(LocalDate(2026, 12, 24) in Flextime.workdays(LocalDate(2026, 12, 21), LocalDate(2026, 12, 31)))
        assertEquals(4.0, Flextime.targetHours(LocalDate(2026, 12, 31), 8.0), tol)
        assertEquals(listOf(LocalDate(2026, 5, 4), LocalDate(2026, 5, 5)), Flextime.workdays(LocalDate(2026, 5, 1), LocalDate(2026, 5, 5)))   // May Day is a Friday
    }

    // ---- proposals ----

    private fun advice(vararg balances: Double, changes: List<Double>? = null, day: LocalDate = today, analysis: MonthAnalysis? = null, perDay: Double = 8.0) =
        Flextime.advice(state(perDay = perDay), day, series(*balances, changes = changes), analysis)

    private fun Advice.codes() = items.map { it.code }
    private fun Advice.item(code: AdviceCode) = items.first { it.code == code }

    @Test
    fun noDataNoProposals() {
        val a = Flextime.advice(state(), today, emptyList())
        assertNull(a.balance)
        assertTrue(a.items.isEmpty())
    }

    @Test
    fun yellowBalanceGetsAPlanToGetBelowTheBuffer() {
        val a = advice(45.0)
        assertEquals(FlexStatus.YELLOW_POS, a.status)
        assertEquals(listOf(AdviceCode.REDUCE, AdviceCode.FLEX_DAYS, AdviceCode.SHORTER_DAYS), a.codes())
        assertEquals(mapOf("balance" to 45.0, "over" to 5.0, "limit" to 40.0, "target" to 30.0, "need" to 15.0), a.item(AdviceCode.REDUCE).values)
        assertEquals(mapOf("days" to 1.9, "fullDays" to 1.0, "rest" to 7.0, "soll" to 8.0), a.item(AdviceCode.FLEX_DAYS).values)
        assertEquals(mapOf("perDay" to 0.88, "workdays" to 17.0), a.item(AdviceCode.SHORTER_DAYS).values)
        assertEquals("2026-11-30", a.item(AdviceCode.SHORTER_DAYS).until)
        assertTrue(a.items.all { it.priority == Priority.MEDIUM })
    }

    @Test
    fun aHighBalanceIsUrgentAndAsksToTalkToTheManager() {
        val a = advice(55.0)
        assertEquals(listOf(Priority.HIGH, Priority.HIGH, Priority.HIGH), a.items.take(3).map { it.priority })
        assertEquals(Priority.MEDIUM, a.item(AdviceCode.APPROVAL).priority)
    }

    @Test
    fun aboveSixtyHoursTheCutOffIsAnnouncedFirst() {
        val a = advice(62.0)
        assertEquals(AdviceCode.CAP_CUT, a.codes().first())
        assertEquals(2.0, a.item(AdviceCode.CAP_CUT).values.getValue("over"), tol)
        assertEquals(FlexStatus.RED_POS, a.status)
    }

    @Test
    fun closeToTheYellowLimitIsAWarningBeforeItHappens() {
        val a = advice(35.0)
        assertTrue(AdviceCode.NEAR_LIMIT in a.codes())
        assertFalse(AdviceCode.REDUCE in a.codes())
        assertEquals(5.0, a.item(AdviceCode.NEAR_LIMIT).values.getValue("toLimit"), tol)
    }

    @Test
    fun aComfortableBalanceNeedsNothing() {
        assertEquals(listOf(AdviceCode.ON_TRACK), advice(20.0, changes = listOf(0.0)).codes())
    }

    @Test
    fun aGrowingBalanceIsAnnouncedWithTheMonthsLeft() {
        val a = advice(13.0, 17.0, 21.0, 25.0, changes = listOf(4.0, 4.0, 4.0, 4.0))
        assertEquals(listOf(AdviceCode.TREND_UP), a.codes())
        assertEquals(3.8, a.item(AdviceCode.TREND_UP).values.getValue("months"), tol)
    }

    @Test
    fun aDistantLimitIsNotAWarning() {
        assertFalse(AdviceCode.TREND_UP in advice(0.0, 1.0, 2.0, changes = listOf(1.0, 1.0, 1.0)).codes())
    }

    @Test
    fun aBalanceOverTheLimitThatKeepsGrowingSaysSo() {
        val a = advice(40.0, 45.0, changes = listOf(3.0, 5.0))
        assertEquals(4.0, a.item(AdviceCode.TREND_UP_OVER).values.getValue("trend"), tol)
    }

    @Test
    fun stronglyNegativeBalanceAsksToCheckTheBookings() {
        val a = advice(-45.0)
        assertEquals(listOf(AdviceCode.CHECK_MISSING), a.codes())
        assertEquals(Priority.HIGH, a.items.first().priority)
    }

    @Test
    fun aSlightlyNegativeBalanceIsANote() {
        val a = advice(-15.0)
        assertEquals(listOf(AdviceCode.BUILD_UP), a.codes())
        assertEquals(15.0, a.item(AdviceCode.BUILD_UP).values.getValue("need"), tol)
    }

    @Test
    fun thePlanContinuesIntoTheNextMonthWhenFewDaysAreLeft() {
        val item = advice(45.0, day = LocalDate(2026, 11, 27)).item(AdviceCode.SHORTER_DAYS)
        assertEquals("2026-12-31", item.until)
        assertTrue(item.values.getValue("workdays") > 5)
    }

    @Test
    fun theTargetHoursOfTheStateAreUsed() {
        assertEquals(7.5, advice(45.0, perDay = 7.5).item(AdviceCode.FLEX_DAYS).values.getValue("soll"), tol)
    }

    @Test
    fun itemsAreSortedByPriority() {
        val order = advice(55.0, changes = listOf(5.0)).items.map { it.priority.ordinal }
        assertEquals(order.sorted(), order)
    }

    private fun overtimeAnalysis() = Flextime.analyzeMonth(
        state(entries = mapOf(
            "2025-11-04" to listOf(entry("2025-11-04", "06:00", "19:00", 11.9)),
            "2025-11-05" to listOf(entry("2025-11-05", "05:30", "15:00", 9.5)),
            "2025-11-06" to listOf(entry("2025-11-06", "12:00", "20:45", 8.2)),
            "2025-11-08" to listOf(entry("2025-11-08", "09:00", "12:00", 3.0))
        )), 2025, 11, today
    )

    @Test
    fun aMonthWithOvertimeSignsAddsTheCauses() {
        val a = advice(45.0, analysis = overtimeAnalysis())
        for (code in listOf(AdviceCode.LONG_DAYS, AdviceCode.WEEKEND_WORK, AdviceCode.OUTSIDE_WINDOW)) assertTrue(code in a.codes(), code.name)
        assertEquals(2.0, a.item(AdviceCode.OUTSIDE_WINDOW).values.getValue("n"), tol)
    }

    @Test
    fun theCausesAreOnlyListedWhenThereIsOvertime() {
        assertEquals(listOf(AdviceCode.ON_TRACK), advice(5.0, analysis = overtimeAnalysis()).codes())
    }

    @Test
    fun proposalsUseTheLatestMonthWithABalance() {
        val a = advice(70.0, 10.0)
        assertEquals(10.0, a.balance!!, tol)
        assertEquals(MonthRef(2026, 2), a.asOf)
    }

    @Test
    fun entriesWithoutABalanceAreIgnored() {
        val a = Flextime.advice(state(), today, listOf(e(2026, 1, change = 2.0), e(2026, 2, 45.0)))
        assertEquals(45.0, a.balance!!, tol)
    }

    // ---- final report ----

    private fun hist(name: String) = MonthData(name, 2026, 40.0, 160.0, 64.0, 10.0, 160.0, 0.0, "Q1/2026")

    @Test
    fun reportRowsAndQuartersCarryTheirFlextime() {
        val flex = listOf(FlexEntry(2026, 1, null, 2.0, 10.0), FlexEntry(2026, 2, null, -3.5, 6.5))
        val report = FinalReport.build(listOf(hist("Januar"), hist("Februar"), hist("März")), flex)
        val q = report.quarters.single()
        assertEquals(listOf(10.0, 6.5, null), q.months.map { it.flexTotal })
        assertEquals(listOf(2.0, -3.5, null), q.months.map { it.flexChange })
        assertEquals(6.5, q.flexEnd!!, tol)
        assertEquals(-1.5, q.flexChange!!, tol)
        assertEquals(flex, report.flex.series)
        assertEquals(10.0, report.flex.summary.max!!, tol)
    }

    @Test
    fun reportStatusPerMonthAndQuarter() {
        val flex = series(10.0, 45.0, 61.0)
        val q = FinalReport.build(listOf(hist("Januar"), hist("Februar"), hist("März")), flex).quarters.single()
        assertEquals(listOf(FlexStatus.OK, FlexStatus.YELLOW_POS, FlexStatus.RED_POS), q.months.map { it.flexStatus })
        assertEquals(FlexStatus.RED_POS, q.flexStatus)
    }

    @Test
    fun reportWithoutFlextimeHasNoValues() {
        val report = FinalReport.build(listOf(hist("Januar")))
        assertNull(report.quarters.single().flexEnd)
        assertNull(report.quarters.single().months.single().flexTotal)
        assertTrue(report.flex.series.isEmpty())
    }

    @Test
    fun flextimeOfAnotherYearDoesNotLeakIntoTheMonth() {
        val report = FinalReport.build(listOf(hist("Januar")), listOf(FlexEntry(2025, 1, null, 9.0, 99.0)))
        assertNull(report.quarters.single().months.single().flexTotal)
    }

    @Test
    fun theOtherNumbersOfTheReportDoNotChange() {
        val history = listOf(hist("Januar"), hist("Februar"))
        assertEquals(FinalReport.build(history).overall, FinalReport.build(history, series(10.0, 45.0)).overall)
    }
}
