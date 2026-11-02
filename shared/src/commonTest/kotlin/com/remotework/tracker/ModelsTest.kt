package com.remotework.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelsTest {

    @Test
    fun monthNames_numbersAndNamesRoundTrip() {
        for (n in 1..12) {
            assertEquals(n, MonthNames.getMonthNumber(MonthNames.getMonthName(n)))
        }
        assertEquals("Januar", MonthNames.getMonthName(1))
        assertEquals("Dezember", MonthNames.getMonthName(12))
    }

    @Test
    fun monthNames_outOfRangeAndUnknownFallBackToJanuary() {
        assertEquals("Januar", MonthNames.getMonthName(0))
        assertEquals("Januar", MonthNames.getMonthName(13))
        assertEquals(1, MonthNames.getMonthNumber("January"))
    }

    @Test
    fun holidays_2025BavariaIncludesMovableFeasts() {
        for (d in listOf("2025-04-18", "2025-04-21", "2025-05-29", "2025-06-09", "2025-06-19")) {
            assertTrue(BavarianHolidays.isHoliday(d), d)
        }
    }

    @Test
    fun holidays_2026BavariaIncludesMovableFeasts() {
        for (d in listOf("2026-04-03", "2026-04-06", "2026-05-14", "2026-05-25", "2026-06-04")) {
            assertTrue(BavarianHolidays.isHoliday(d), d)
        }
    }

    @Test
    fun holidays_bavariaSpecificDaysArePresent() {
        assertTrue(BavarianHolidays.isHoliday("2025-01-06"))
        assertTrue(BavarianHolidays.isHoliday("2025-08-15"))
        assertTrue(BavarianHolidays.isHoliday("2025-11-01"))
        assertTrue(BavarianHolidays.isHoliday("2026-01-06"))
    }

    @Test
    fun holidays_2027BavariaIncludesMovableFeasts() {
        // Easter Sunday 2027-03-28
        for (d in listOf("2027-03-26", "2027-03-29", "2027-05-06", "2027-05-17", "2027-05-27")) {
            assertTrue(BavarianHolidays.isHoliday(d), d)
        }
    }

    @Test
    fun holidays_2028BavariaIncludesMovableFeasts() {
        // Easter Sunday 2028-04-16
        for (d in listOf("2028-04-14", "2028-04-17", "2028-05-25", "2028-06-05", "2028-06-15")) {
            assertTrue(BavarianHolidays.isHoliday(d), d)
        }
    }

    @Test
    fun holidays_2029BavariaIncludesMovableFeasts() {
        // Easter Sunday 2029-04-01
        for (d in listOf("2029-03-30", "2029-04-02", "2029-05-10", "2029-05-21", "2029-05-31")) {
            assertTrue(BavarianHolidays.isHoliday(d), d)
        }
    }

    @Test
    fun holidays_fixedDatesExistInEveryCoveredYear() {
        for (y in 2025..2029) {
            for (md in listOf("01-01", "01-06", "05-01", "08-15", "10-03", "11-01", "12-25", "12-26")) {
                assertTrue(BavarianHolidays.isHoliday("$y-$md"), "$y-$md")
            }
        }
    }

    @Test
    fun holidays_eachCoveredYearHasThirteenHolidaysInItsOwnYear() {
        val tables = mapOf(
            2025 to BavarianHolidays.holidays2025, 2026 to BavarianHolidays.holidays2026,
            2027 to BavarianHolidays.holidays2027, 2028 to BavarianHolidays.holidays2028,
            2029 to BavarianHolidays.holidays2029
        )
        for ((year, days) in tables) {
            assertEquals(13, days.size, "count $year")
            assertTrue(days.all { it.startsWith("$year-") }, "all dates belong to $year")
        }
    }

    @Test
    fun holidays_easterRelatedDaysAreOnTheExpectedWeekdays() {
        // Good Friday and Easter Monday are Fri/Mon, Ascension Thu, Whit Monday Mon, Corpus Christi Thu
        val expected = mapOf(
            "2027-03-26" to kotlinx.datetime.DayOfWeek.FRIDAY, "2027-03-29" to kotlinx.datetime.DayOfWeek.MONDAY,
            "2027-05-06" to kotlinx.datetime.DayOfWeek.THURSDAY, "2027-05-17" to kotlinx.datetime.DayOfWeek.MONDAY,
            "2027-05-27" to kotlinx.datetime.DayOfWeek.THURSDAY,
            "2028-04-14" to kotlinx.datetime.DayOfWeek.FRIDAY, "2028-04-17" to kotlinx.datetime.DayOfWeek.MONDAY,
            "2028-05-25" to kotlinx.datetime.DayOfWeek.THURSDAY, "2028-06-05" to kotlinx.datetime.DayOfWeek.MONDAY,
            "2028-06-15" to kotlinx.datetime.DayOfWeek.THURSDAY,
            "2029-03-30" to kotlinx.datetime.DayOfWeek.FRIDAY, "2029-04-02" to kotlinx.datetime.DayOfWeek.MONDAY,
            "2029-05-10" to kotlinx.datetime.DayOfWeek.THURSDAY, "2029-05-21" to kotlinx.datetime.DayOfWeek.MONDAY,
            "2029-05-31" to kotlinx.datetime.DayOfWeek.THURSDAY
        )
        for ((date, dow) in expected) {
            assertEquals(dow, kotlinx.datetime.LocalDate.parse(date).dayOfWeek, date)
        }
    }

    @Test
    fun holidays_ordinaryDaysAreNotHolidays() {
        assertFalse(BavarianHolidays.isHoliday("2025-10-06"))
        assertFalse(BavarianHolidays.isHoliday("2027-03-28")) // Easter Sunday is not in the list
        assertFalse(BavarianHolidays.isHoliday("not a date"))
    }

    // Documents a known limitation: only 2025-2029 are in the table.
    @Test
    fun holidays_yearsOutsideTheTableHaveNoHolidays() {
        assertFalse(BavarianHolidays.isHoliday("2031-01-01"))
        assertFalse(BavarianHolidays.isHoliday("2024-12-25"))
    }

    @Test
    fun operationResult_successDependsOnState() {
        assertFalse(OperationResult(error = "x").isSuccess)
        val state = TrackerState(
            aktuellerMonat = "Januar", aktuellesJahr = 2026,
            currentMonthData = MonthData("Januar", 2026, 40.0, 0.0, 0.0, 0.0, 0.0),
            currentQuartal = "Q1/2026"
        )
        assertTrue(OperationResult(state = state).isSuccess)
    }
}
