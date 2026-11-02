package com.remotework.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Same cases as flask-tracker/tests/test_final_report.py, so every platform rates identically. */
class FinalReportTest {

    private val tol = 1e-9

    private fun month(
        name: String, year: Int, remote: Double, max: Double, soll: Double = 160.0,
        quote: Double = 40.0, quartal: String? = null
    ) = MonthData(name, year, quote, soll, max, remote, soll, 0.0, quartal)

    private fun screenshotHistory(): List<MonthData> {
        val rows = listOf(
            Triple("Juli", 130.25, 110.40), Triple("Mai", 80.43, 86.40), Triple("Juni", 107.63, 100.80),
            Triple("April", 101.62, 96.00), Triple("Oktober", 90.47, 105.60), Triple("September", 109.98, 105.60),
            Triple("August", 94.60, 100.80)
        )
        val q = mapOf("April" to "Q2/2026", "Mai" to "Q2/2026", "Juni" to "Q2/2026", "Juli" to "Q3/2026",
            "August" to "Q3/2026", "September" to "Q3/2026", "Oktober" to "Q4/2026")
        return rows.map { (n, r, mx) -> month(n, 2026, r, mx, soll = mx / 0.6, quote = 60.0, quartal = q[n]) }
    }

    @Test fun overQuota_whenRemoteExceedsLimit() {
        assertTrue(FinalReport.isOverQuota(month("Juli", 2026, 130.25, 110.40)))
    }

    @Test fun overQuota_notWhenBelowOrEqualOrRoundingNoise() {
        assertFalse(FinalReport.isOverQuota(month("Mai", 2026, 80.43, 86.40)))
        assertFalse(FinalReport.isOverQuota(month("Mai", 2026, 86.40, 86.40)))
        assertFalse(FinalReport.isOverQuota(month("Mai", 2026, 86.4000000001, 86.40)))
    }

    @Test fun sortHistory_screenshotOrderBecomesChronological() {
        val names = listOf("Juli", "Mai", "Juni", "April", "Oktober", "September", "August")
        val sorted = FinalReport.sortHistory(names.map { month(it, 2026, 1.0, 1.0) })
        assertEquals(listOf("April", "Mai", "Juni", "Juli", "August", "September", "Oktober"), sorted.map { it.monat })
    }

    @Test fun sortHistory_newestFirstAcrossYears() {
        val hist = listOf(month("Dezember", 2025, 1.0, 1.0), month("Januar", 2026, 1.0, 1.0), month("März", 2025, 1.0, 1.0))
        assertEquals(
            listOf("Januar" to 2026, "Dezember" to 2025, "März" to 2025),
            FinalReport.sortHistory(hist, newestFirst = true).map { it.monat to it.jahr }
        )
    }

    @Test fun report_empty() {
        val r = FinalReport.build(emptyList())
        assertTrue(r.quarters.isEmpty())
        assertEquals(0, r.overall.months)
        assertNull(r.overall.rating)
    }

    @Test fun report_quartersAndMonthsAreOrdered() {
        val r = FinalReport.build(screenshotHistory())
        assertEquals(listOf("Q2/2026", "Q3/2026", "Q4/2026"), r.quarters.map { it.quartal })
        assertEquals(listOf("April", "Mai", "Juni"), r.quarters[0].months.map { it.monat })
    }

    @Test fun report_sumsAndRatings() {
        val (q2, q3, q4) = FinalReport.build(screenshotHistory()).quarters
        assertEquals(101.62 + 80.43 + 107.63, q2.remote, 1e-6)
        assertEquals(96.0 + 86.4 + 100.8, q2.maxRemote, 1e-6)
        assertEquals(FinalReport.Rating.EXCEEDED, q2.rating)
        assertEquals(2, q2.monthsOver)
        assertTrue(q2.complete)
        assertEquals(FinalReport.Rating.EXCEEDED, q3.rating)
        assertEquals(2, q3.monthsOver)
        assertEquals(FinalReport.Rating.GOOD, q4.rating)
        assertFalse(q4.complete)
        assertEquals(1, q4.monthCount)
    }

    @Test fun report_tightBetween90And100PercentGoodAtOrBelow90() {
        val tight = listOf("Januar", "Februar", "März").map { month(it, 2026, 95.0, 100.0, quartal = "Q1/2026") }
        val good = listOf("Januar", "Februar", "März").map { month(it, 2026, 90.0, 100.0, quartal = "Q1/2026") }
        assertEquals(FinalReport.Rating.TIGHT, FinalReport.build(tight).quarters[0].rating)
        assertEquals(FinalReport.Rating.GOOD, FinalReport.build(good).quarters[0].rating)
    }

    @Test fun report_oneMonthOverButQuarterWithinLimit() {
        val hist = listOf(month("Januar", 2026, 120.0, 100.0, quartal = "Q1/2026"),
            month("Februar", 2026, 50.0, 100.0, quartal = "Q1/2026"), month("März", 2026, 50.0, 100.0, quartal = "Q1/2026"))
        val q = FinalReport.build(hist).quarters[0]
        assertEquals(1, q.monthsOver)
        assertEquals(FinalReport.Rating.GOOD, q.rating)
    }

    @Test fun report_quartalLabelDerivedWhenMissing_sameQuarterTwoYearsSorted() {
        assertEquals("Q4/2026", FinalReport.build(listOf(month("November", 2026, 1.0, 10.0))).quarters[0].quartal)
        val r = FinalReport.build(listOf(month("Februar", 2026, 1.0, 10.0), month("Februar", 2025, 1.0, 10.0)))
        assertEquals(listOf("Q1/2025", "Q1/2026"), r.quarters.map { it.quartal })
    }

    @Test fun report_duplicateMonthCountedOnce() {
        val r = FinalReport.build(listOf(month("Mai", 2026, 10.0, 20.0), month("Mai", 2026, 10.0, 20.0)))
        assertEquals(1, r.quarters[0].monthCount)
        assertEquals(10.0, r.quarters[0].remote, tol)
    }

    @Test fun report_overall() {
        val o = FinalReport.build(screenshotHistory()).overall
        assertEquals(7, o.months)
        assertEquals(4, o.monthsOver)
        assertEquals(3, o.quarters)
        assertEquals(2, o.quartersExceeded)
        assertEquals(FinalReport.Rating.EXCEEDED, o.rating)
    }

    @Test fun report_zeroSollDoesNotDivideByZero() {
        val q = FinalReport.build(listOf(month("Mai", 2026, 0.0, 0.0, soll = 0.0))).quarters[0]
        assertEquals(0.0, q.actualQuote, tol)
        assertEquals(FinalReport.Rating.GOOD, q.rating)
    }
}
