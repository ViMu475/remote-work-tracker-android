package com.remotework.tracker.android

import com.remotework.tracker.Advice
import com.remotework.tracker.AdviceCode
import com.remotework.tracker.FlexEntry
import com.remotework.tracker.FlexStatus
import com.remotework.tracker.Flextime
import com.remotework.tracker.MonthData
import com.remotework.tracker.MonthRef
import com.remotework.tracker.TrackerState
import com.remotework.tracker.WorkEntry
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The texts of the flextime view: every proposal in both languages, number formats, no unfilled placeholders. */
class FlexTextTest {

    private val today = LocalDate(2026, 11, 6)

    private fun state() = TrackerState(
        aktuellerMonat = "Oktober", aktuellesJahr = 2026, currentMonthData = MonthData("Oktober", 2026, 40.0, 0.0, 0.0, 0.0, 0.0),
        currentQuartal = "Q4/2026"
    )

    private fun series(vararg balances: Double, changes: List<Double>? = null) =
        balances.mapIndexed { i, b -> FlexEntry(2026, i + 1, null, changes?.get(i), b) }

    private fun entry(day: String, start: String, end: String, net: Double) =
        WorkEntry(day, day, start, end, 30.0, 0.0, net)

    private fun overtimeAnalysis() = Flextime.analyzeMonth(
        state().copy(
            dailyRecords = mapOf(
                "2025-11-04" to listOf(entry("2025-11-04", "06:00", "19:00", 11.9)),
                "2025-11-05" to listOf(entry("2025-11-05", "05:30", "15:00", 9.5)),
                "2025-11-06" to listOf(entry("2025-11-06", "11:30", "21:00", 9.0)),
                "2025-11-08" to listOf(entry("2025-11-08", "09:00", "12:00", 3.0))
            )
        ), 2025, 11, today
    )

    /** Scenarios that together produce every proposal the shared module can make. */
    private fun allAdvice(): List<Advice> = listOf(
        Flextime.advice(state(), today, series(62.0, changes = listOf(5.0)), overtimeAnalysis()),
        Flextime.advice(state(), today, series(35.0)),
        Flextime.advice(state(), today, series(13.0, 17.0, 21.0, 25.0, changes = listOf(4.0, 4.0, 4.0, 4.0))),
        Flextime.advice(state(), today, series(-45.0)),
        Flextime.advice(state(), today, series(-15.0)),
        Flextime.advice(state(), today, series(20.0))
    )

    @Test
    fun scenariosCoverEveryProposalCode() {
        val seen = allAdvice().flatMap { it.items }.map { it.code }.toSet()
        assertEquals(AdviceCode.values().toSet(), seen)
    }

    @Test
    fun everyProposalIsFormattedInBothLanguagesWithoutLeftovers() {
        for (advice in allAdvice()) for (item in advice.items) for (lang in listOf("de", "en")) {
            val text = adviceText(item, lang)
            assertTrue("empty text for ${item.code}/$lang", text.isNotBlank())
            assertFalse("unfilled placeholder in ${item.code}/$lang: $text", Regex("""%\d?\$?s|\{\w+\}""").containsMatchIn(text))
        }
    }

    @Test
    fun germanUsesDecimalCommaAndEnglishDecimalPoint() {
        val reduce = Flextime.advice(state(), today, series(45.46)).items.first { it.code == AdviceCode.REDUCE }
        assertTrue(adviceText(reduce, "de").contains("45,46 h"))
        assertTrue(adviceText(reduce, "en").contains("45.46 h"))
    }

    @Test
    fun theDeadlineOfTheShorterDaysUsesTheLocalDateFormat() {
        val item = Flextime.advice(state(), today, series(45.0)).items.first { it.code == AdviceCode.SHORTER_DAYS }
        assertTrue(adviceText(item, "de").contains("30.11.2026"))
        assertTrue(adviceText(item, "en").contains("2026-11-30"))
    }

    @Test
    fun numbersLoseTheirTrailingZeros() {
        assertEquals("40", numText(40.0, "de"))
        assertEquals("0,5", numText(0.5, "de"))
        assertEquals("2.5", numText(2.5, "en"))
        assertEquals("-13,5", numText(-13.5, "de"))
        assertEquals("0", numText(0.0, "en"))
    }

    @Test
    fun hoursCarryASignOnlyWhenPositive() {
        assertEquals("+41,50h", signedHours(41.5, "de"))
        assertEquals("-13.50h", signedHours(-13.5, "en"))
        assertEquals("0,00h", signedHours(0.0, "de"))
        assertEquals("0,00h", signedHours(0.001, "de"))
    }

    @Test
    fun monthNamesFollowTheAppLanguage() {
        assertEquals("Oktober 2026", monthYearText(MonthRef(2026, 10), "de"))
        assertEquals("October 2026", monthYearText(MonthRef(2026, 10), "en"))
        assertEquals("März 2026", monthYearText(MonthRef(2026, 3), "de"))
    }

    @Test
    fun everyStatusHasATextInBothLanguages() {
        for (status in FlexStatus.values()) for (lang in listOf("de", "en")) {
            assertTrue("$status/$lang", flexStatusText(status, lang).isNotBlank())
        }
        assertEquals("", flexStatusText(null, "de"))
    }

    @Test
    fun theStatusTextsNameTheThresholds() {
        assertTrue(flexStatusText(FlexStatus.YELLOW_POS, "de").contains("40"))
        assertTrue(flexStatusText(FlexStatus.RED_POS, "en").contains("60"))
        assertTrue(flexStatusText(FlexStatus.RED_NEG, "de").contains("−60"))
    }
}
