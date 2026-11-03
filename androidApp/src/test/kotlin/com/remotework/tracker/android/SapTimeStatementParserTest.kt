package com.remotework.tracker.android

import com.remotework.tracker.ParsedWorkEntry
import com.remotework.tracker.SapTimeStatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the text parsing of SAP time statements. The fixtures follow the line layout of the
 * real statements (day line, optional continuation lines) but contain no personal data.
 */
class SapTimeStatementParserTest {

    private val germanHeader = """
        Zeitnachweis
        vom 01.10.2025 bis 28.10.2025
        Monat: Oktober - 2025
        Einzelergebnisse pro Tag
    """.trimIndent()

    private val englishHeader = """
        Time Statement
        from 01.04.2026 to 27.04.2026
        Month: April - 2026
    """.trimIndent()

    private fun parse(header: String, vararg lines: String) =
        SapTimeStatementParser.parse(header + "\n" + lines.joinToString("\n"))

    // --- basics -----------------------------------------------------------------------------

    @Test
    fun `german statement takes month and year from the header`() {
        val e = parse(
            germanHeader,
            "01 MI    Zeitbuchungen : Remote Work     08:15    17:30            8,00           8,00"
        )
        assertEquals(1, e.size)
        assertEquals("2025-10-01", e[0].datum)
        assertEquals("08:15", e[0].startTime)
        assertEquals("17:30", e[0].endTime)
    }

    @Test
    fun `english statement is parsed with english weekdays and keywords`() {
        val e = parse(
            englishHeader,
            "01 WE    Time Postings : Remote Work       08:00   17:45             8,00",
            "07 TU    Time Postings : At work           08:00   17:45             8,00"
        )
        assertEquals(2, e.size)
        assertEquals("2026-04-01", e[0].datum)
        assertTrue(e[0].isRemote)
        assertEquals("2026-04-07", e[1].datum)
        assertFalse(e[1].isRemote)
    }

    @Test
    fun `holidays and weekly sums without times produce no entries`() {
        val e = parse(
            germanHeader,
            "03 FR    Tag der Deutschen Einheit",
            "         arbeitsfrei",
            "Wochensumme : 01.10.2025 - 05.10.2025                                            8,00        8,00"
        )
        assertTrue(e.isEmpty())
    }

    @Test
    fun `text without any header date gives no entries`() {
        assertTrue(SapTimeStatementParser.parse("irgendein Text ohne Datum").isEmpty())
    }

    // --- header detection -----------------------------------------------------------------------

    @Test
    fun personnelNumberIsNotMistakenForADate() {
        // 00001502 used to match "any 8 digits" and invent month 15 of year 0000
        val e = SapTimeStatementParser.parse(
            "Personalnummer 00001502\n01 MI    Zeitbuchungen : Remote Work     08:15    17:30            8,00"
        )
        assertTrue(e.isEmpty())
    }

    @Test
    fun plausibleDateInTheTextIsUsedWhenThereIsNoDateRange() {
        val e = SapTimeStatementParser.parse(
            "Zeitnachweis_20251001_20251031\n01 MI    Zeitbuchungen : Remote Work     08:15    17:30            8,00"
        )
        assertEquals(1, e.size)
        assertEquals("2025-10-01", e[0].datum)
    }

    // --- bug 1: flextime compensation is not worked time ---------------------------------------

    @Test
    fun `gleitzeitausgleich line is skipped but the booking after it is kept`() {
        val e = parse(
            germanHeader,
            "15 MI    Gleitzeitausgleich              08:00    12:15            8,00           8,00",
            "         Zeitbuchungen : Remote Work     12:00    17:45            8,00"
        )
        assertEquals(1, e.size)
        assertEquals("2025-10-15", e[0].datum)
        assertEquals("12:00", e[0].startTime)
        assertEquals("17:45", e[0].endTime)
        assertTrue(e[0].isRemote)
    }

    @Test
    fun `english flextime line is skipped`() {
        val e = parse(
            englishHeader,
            "15 WE    Flextime compensation           08:00   12:15             8,00",
            "         Time Postings : At work         12:30   17:00             8,00"
        )
        assertEquals(1, e.size)
        assertEquals("12:30", e[0].startTime)
    }

    // --- bug 2: every booking line decides its own remote flag ---------------------------------

    @Test
    fun `second booking marked anwesend is not remote although the day line is remote`() {
        val e = parse(
            germanHeader,
            "02 DO    Zeitbuchungen : Remote Work     08:45    11:45            8,00           8,00",
            "         Zeitbuchungen : anwesend        12:30    18:00            8,00"
        )
        assertEquals(2, e.size)
        assertTrue(e[0].isRemote)
        assertFalse(e[1].isRemote)
    }

    @Test
    fun `second booking marked remote after an anwesend day line is remote`() {
        val e = parse(
            germanHeader,
            "17 FR    Zeitbuchungen : anwesend        09:00    14:30            8,00           8,00",
            "         Zeitbuchungen : Remote Work     15:30    17:30            8,00"
        )
        assertEquals(2, e.size)
        assertFalse(e[0].isRemote)
        assertTrue(e[1].isRemote)
    }

    @Test
    fun `english second booking at work after remote is not remote`() {
        val e = parse(
            englishHeader,
            "23 TH    Time Postings : Remote Work       07:45   10:00             8,00",
            "         Time Postings : At work           10:45   18:00             8,00"
        )
        assertEquals(2, e.size)
        assertTrue(e[0].isRemote)
        assertFalse(e[1].isRemote)
    }

    @Test
    fun `continuation line without keyword inherits the remote flag of the day line`() {
        val e = parse(
            germanHeader,
            "20 MO    Zeitbuchungen : Remote Work     08:30    12:00            8,00",
            "         Zeitbuchungen :                 12:30    15:30            8,00"
        )
        assertEquals(2, e.size)
        assertTrue(e[0].isRemote)
        assertTrue(e[1].isRemote)
    }

    @Test
    fun `remote flag does not leak into the next day`() {
        val e = parse(
            germanHeader,
            "14 DI    Zeitbuchungen : Remote Work     08:00    17:30            8,00",
            "15 MI    Zeitbuchungen : anwesend        08:00    16:00            8,00"
        )
        assertEquals(2, e.size)
        assertTrue(e[0].isRemote)
        assertFalse(e[1].isRemote)
    }

    // --- absence lines seen in the 2026 statements ----------------------------------------------

    @Test
    fun leaveLinesWithTimesAreNotWork() {
        val e = parse(
            englishHeader.replace("04.2026", "08.2026").replace("April", "August"),
            "07 FR    Time Postings : Remote Work       08:00   17:30             8,00         8,00",
            "10 MO    Leave                             08:00   16:30             8,00         8,00",
            "                                                                     8,00",
            "11 TU    Leave                             08:00   16:30             8,00         8,00",
            "15 SA    Leave                             00:00   00:00             8,00         8,00",
            "         off",
            "16 SU    Leave                             00:00   00:00             8,00         8,00",
            "19 WE    Flexitime Compensation            08:00   16:30             8,00         8,00",
            "24 MO    Time Postings : Remote Work       08:15   17:30             8,00         8,00"
        )
        assertEquals(listOf("2026-08-07", "2026-08-24"), e.map { it.datum })
    }

    @Test
    fun germanUrlaubAndGleitzeitLinesAreNotWork() {
        val e = parse(
            germanHeader,
            "06 MO    Urlaub                          08:00    16:30            8,00           8,00",
            "07 DI    Gleitzeitausgleich              08:00    16:30            8,00           8,00",
            "08 MI    Zeitbuchungen : Remote Work     08:00    16:30            8,00           8,00"
        )
        assertEquals(listOf("2025-10-08"), e.map { it.datum })
    }

    @Test
    fun secondBookingAfterAPageBreakStillBelongsToThatDay() {
        val e = parse(
            englishHeader,
            "27 MO    Time Postings : Remote Work       08:00   11:00             8,00         8,00         8,00",
            "                                                                                  01.11.2026 Page 2/4",
            "",
            "Day                                 from     to         Hrs          Plnd      Skel.     Flex       OT       DWS",
            "                                                                               Time",
            "",
            "          Time Postings : At work   12:00    17:45            8,00",
            "28 TU     Time Postings : At work   07:45    17:30            8,00      8,00      8,00      8,00         8,00 ZA01"
        )
        assertEquals(listOf("2026-04-27", "2026-04-27", "2026-04-28"), e.map { it.datum })
        assertEquals(listOf(true, false, false), e.map { it.isRemote })
        assertEquals("12:00", e[1].startTime)
    }

    @Test
    fun summaryAndOverviewLinesAfterTheLastDayAreIgnored() {
        val e = parse(
            englishHeader,
            "30 TH    Time Postings : At work           08:30   15:45             8,00         8,00         8,00",
            "Monthly Overview for Key Date 30.04.2026",
            "Flextime Balance Total                            8,00 Overtime Basic Pay/Time Off                             8,00",
            "Weekly Total :   20.04.2026 - 26.04.2026                                         8,00       8,00            8,00"
        )
        assertEquals(1, e.size)
    }

    // --- whole month -------------------------------------------------------------------------

    @Test
    fun `october layout yields 21 entries with the expected remote ones`() {
        val e = parse(
            germanHeader,
            "01 MI    Zeitbuchungen : Remote Work     08:15    17:30            8,00           8,00",
            "02 DO    Zeitbuchungen : Remote Work     08:45    11:45            8,00           8,00",
            "         Zeitbuchungen : anwesend        12:30    18:00            8,00",
            "03 FR    Tag der Deutschen Einheit",
            "06 MO    Zeitbuchungen : anwesend        08:45    17:15            8,00           8,00",
            "07 DI    Zeitbuchungen : Remote Work     08:15    16:30            8,00           8,00",
            "08 MI    Zeitbuchungen : Remote Work     08:30    16:15            8,00           8,00",
            "09 DO    Zeitbuchungen : anwesend        08:30    17:45            8,00           8,00",
            "10 FR    Zeitbuchungen : Remote Work     09:00    15:00            8,00           8,00",
            "13 MO    Zeitbuchungen : anwesend        08:30    18:00            8,00           8,00",
            "14 DI    Zeitbuchungen : Remote Work     08:00    17:30            8,00           8,00",
            "15 MI    Gleitzeitausgleich              08:00    12:15            8,00           8,00",
            "         Zeitbuchungen : Remote Work     12:00    17:45            8,00",
            "16 DO    Zeitbuchungen : anwesend        08:45    17:45            8,00           8,00",
            "17 FR    Zeitbuchungen : Remote Work     09:00    14:30            8,00           8,00",
            "         Zeitbuchungen : anwesend        15:30    17:30            8,00",
            "20 MO    Zeitbuchungen : Remote Work     08:30    15:30            8,00           8,00",
            "21 DI    Zeitbuchungen : anwesend        08:30    16:45            8,00           8,00",
            "22 MI    Zeitbuchungen : Remote Work     08:15    16:30            8,00           8,00",
            "23 DO    Zeitbuchungen : anwesend        08:30    17:00            8,00           8,00",
            "24 FR    Zeitbuchungen : Remote Work     08:30    15:45            8,00           8,00",
            "27 MO    Zeitbuchungen : Remote Work     08:15    16:00            8,00           8,00",
            "28 DI    Zeitbuchungen : anwesend        08:30    16:30            8,00           8,00"
        )
        assertEquals(21, e.size)
        assertEquals(
            listOf("01", "02", "07", "08", "10", "14", "15", "17", "20", "22", "24", "27"),
            // 02.10. and 17.10. each have one remote booking
            e.filter { it.isRemote }.map { it.datum.takeLast(2) }
        )
        assertEquals(12, e.count { it.isRemote })
        assertEquals(9, e.count { !it.isRemote })
    }

    // --- breaks: the app must show the statement's "Istz" (booked time minus the legal break) -----

    private fun net(e: ParsedWorkEntry) =
        com.remotework.tracker.TrackerCalculations.calculateNetHours(e.startTime, e.endTime, e.pauseMinutes).netHours

    @Test
    fun `single booking gets the legal break`() {
        val e = parse(germanHeader, "01 MI    Zeitbuchungen : Remote Work     08:00    17:00            9,00           8,50")
        assertEquals(30.0, e[0].pauseMinutes, 1e-9)
        assertEquals(8.5, net(e[0]), 1e-9)
    }

    @Test
    fun `booking above nine and a half hours never drops below nine hours`() {
        val e = parse(germanHeader, "02 DO    Zeitbuchungen : Remote Work     07:50    17:25            9,58           9,00")
        assertEquals(9.0, net(e[0]), 1e-9)
    }

    @Test
    fun `short booking has no break`() {
        val e = parse(germanHeader, "03 FR    Zeitbuchungen : Remote Work     08:00    13:30            5,50           5,50")
        assertEquals(0.0, e[0].pauseMinutes, 1e-9)
    }

    @Test
    fun `split day with a long gap keeps both parts without a deduction`() {
        val e = parse(
            germanHeader,
            "06 MO    Zeitbuchungen : Remote Work     08:00    11:00            3,00           8,00",
            "         Zeitbuchungen : anwesend        12:00    17:00            5,00"
        )
        assertEquals(2, e.size)
        assertEquals(0.0, e[0].pauseMinutes, 1e-9)
        assertEquals(0.0, e[1].pauseMinutes, 1e-9)
        assertEquals(8.0, e.sumOf { net(it) }, 1e-9)
    }

    @Test
    fun `split day with a short gap deducts the missing rest from the longest part`() {
        val e = parse(
            germanHeader,
            "07 DI    Zeitbuchungen : Remote Work     08:00    11:00            3,00           8,00",
            "         Zeitbuchungen : anwesend        11:10    17:00            5,83"
        )
        assertEquals(0.0, e[0].pauseMinutes, 1e-9)
        assertEquals(20.0, e[1].pauseMinutes, 1e-9)
    }

    @Test
    fun `flextime compensation is neither a booking nor does it change the break`() {
        val e = parse(
            germanHeader,
            "08 MI    Gleitzeitausgleich              08:00    12:00            4,00           8,00",
            "         Zeitbuchungen : Remote Work     12:00    17:00            5,00"
        )
        assertEquals(1, e.size)
        assertEquals(0.0, e[0].pauseMinutes, 1e-9)
        assertEquals(5.0, net(e[0]), 1e-9)
    }
}
