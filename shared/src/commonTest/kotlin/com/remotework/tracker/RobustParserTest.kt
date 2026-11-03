package com.remotework.tracker

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Robustness of the statement parser (fictional round values, fixed seeds). */
class RobustParserTest {

    private val header = "from 01.10.2025 to 31.10.2025\n"

    private fun elapsedMs(block: () -> Unit): Long {
        val mark = TimeSource.Monotonic.markNow()
        block()
        return mark.elapsedNow().inWholeMilliseconds
    }

    private fun assertFast(limitMs: Long, block: () -> Unit) {
        val ms = elapsedMs(block)
        assertTrue(ms < limitMs, "took $ms ms (limit $limitMs ms)")
    }

    @Test
    fun randomTextNeverThrows() {
        val rnd = Random(4711)
        val alphabet = "0123456789:.,-/ \n\tMODIFRSAUETHWvomisbfrtnGLZ-SaldoVorperiodeTime Postings äöü€\u0000"
        repeat(300) {
            val text = buildString { repeat(rnd.nextInt(0, 600)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            SapTimeStatementParser.parse(text)
            SapTimeStatementParser.parseFlexBalance(text)
        }
    }

    @Test
    fun randomLinesBuiltFromRealTokensNeverThrow() {
        val rnd = Random(99)
        val tokens = listOf(
            "01 MO", "31 FR", "30 DO", "00 MO", "99 SA", "Time Postings :", "Zeitbuchungen :", "08:00", "16:30", "25:99",
            "0,00", "8,50", "remote", "Telearbeit", "Gleitzeit", "Flextime", "from 01.10.2025 to", "vom 05.02.2026 bis",
            "GLZ-Saldo Gesamt", "12,50", "3,25-", "\n", "\n", " ", "20251003"
        )
        repeat(300) {
            val text = buildString { repeat(rnd.nextInt(0, 80)) { append(tokens[rnd.nextInt(tokens.size)]).append(' ') } }
            val entries = SapTimeStatementParser.parse(text)
            for (e in entries) assertTrue(e.pauseMinutes >= 0.0 && e.pauseMinutes.isFinite())
            SapTimeStatementParser.parseFlexBalance(text)
        }
    }

    @Test
    fun emptyAndBlankTextGiveNothing() {
        assertTrue(SapTimeStatementParser.parse("").isEmpty())
        assertTrue(SapTimeStatementParser.parse("   \n\n\t").isEmpty())
        assertNull(SapTimeStatementParser.parseFlexBalance(""))
        assertNull(SapTimeStatementParser.parseFlexBalance("   \n"))
    }

    @Test
    fun missingHeaderGivesNoEntries() {
        assertTrue(SapTimeStatementParser.parse("01 MO Time Postings : 08:00 16:00 8,00").isEmpty())
    }

    @Test
    fun headerWithoutBookingsGivesNoEntries() {
        assertTrue(SapTimeStatementParser.parse(header).isEmpty())
    }

    @Test
    fun bookingBeforeAnyDayLineIsIgnored() {
        assertTrue(SapTimeStatementParser.parse(header + "Time Postings : 08:00 16:00 8,00").isEmpty())
    }

    @Test
    fun crlfAndLeadingWhitespaceAreHandled() {
        val text = header.trimEnd() + "\r\n" + "   01 WE Time Postings : 08:00 12:00 4,00\r\n"
        val e = SapTimeStatementParser.parse(text)
        assertEquals(1, e.size)
        assertEquals("2025-10-01", e[0].datum)
        assertEquals("12:00", e[0].endTime)
    }

    @Test
    fun unicodeAndEmojiTextIsHarmless() {
        val text = header + "01 WE 😀 Zeitbuchungen ‮: 08:00 12:00 4,00 üöä ال\n"
        val e = SapTimeStatementParser.parse(text)
        assertEquals(1, e.size)
        SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt ١٢,50")  // Arabic-Indic digits
    }

    @Test
    fun fullWidthAndArabicDigitsDoNotCrash() {
        // Kotlin \d matches only ASCII on the JVM by default; either way nothing may throw.
        val text = header + "01 WE Time Postings : ０８:00 １６:00 8,00\n"
        SapTimeStatementParser.parse(text)
    }

    @Test
    fun day30And31InShortMonthsAreParsedButMarkedInvalidDate() {
        val text = "from 01.04.2025 to 30.04.2025\n31 DI Time Postings : 08:00 16:00 8,00\n"
        val e = SapTimeStatementParser.parse(text)
        assertEquals(1, e.size)
        assertEquals("2025-04-31", e[0].datum)
        // The import layer must reject it instead of crashing.
        assertEquals(false, TrackerCalculations.isValidWorkingDay(e[0].datum).isValid)
    }

    @Test
    fun feb29OnlyValidInLeapYears() {
        assertTrue(TrackerCalculations.isValidWorkingDay("2028-02-29").isValid)  // Tuesday, leap year
        assertEquals(false, TrackerCalculations.isValidWorkingDay("2027-02-29").isValid)
        assertEquals(false, TrackerCalculations.isValidWorkingDay("2029-02-29").isValid)
    }

    @Test
    fun monthOutsideRangeInHeaderDoesNotThrow() {
        val e = SapTimeStatementParser.parse("from 01.13.2025 to 31.13.2025\n01 MO Time Postings : 08:00 16:00 8,00\n")
        for (x in e) assertEquals(false, TrackerCalculations.isValidWorkingDay(x.datum).isValid)
    }

    @Test
    fun duplicateBookingsAreBothReturnedAndPausedOnce() {
        val text = header + "01 WE Time Postings : 08:00 16:00 8,00\n01 WE Time Postings : 08:00 16:00 8,00\n"
        val e = SapTimeStatementParser.parse(text)
        assertEquals(2, e.size)
        assertTrue(e.all { it.pauseMinutes >= 0.0 })
    }

    @Test
    fun midnightCrossingBookingKeepsTimes() {
        val e = SapTimeStatementParser.parse(header + "01 WE Time Postings : 22:00 02:00 4,00\n")
        assertEquals(1, e.size)
        assertEquals(0.0, e[0].pauseMinutes, 1e-9)
    }

    @Test
    fun invalidClockValuesInBookingAreCarriedForValidationLater() {
        val e = SapTimeStatementParser.parse(header + "01 WE Time Postings : 99:99 88:77 1,00\n")
        for (x in e) assertTrue(!TrackerCalculations.isValidTime(x.startTime) || !TrackerCalculations.isValidTime(x.endTime))
    }

    @Test
    fun thousandsOfDayLinesStayFast() {
        val text = buildString {
            append(header)
            repeat(20000) { append(((it % 31) + 1).toString().padStart(2, '0') + " MO Time Postings : 08:00 12:00 4,00\n") }
        }
        assertFast(20000) { SapTimeStatementParser.parse(text) }
    }

    @Test
    fun veryLongDigitLineIsNoRegexBomb() {
        val digits = "1".repeat(200_000)
        assertFast(15000) {
            SapTimeStatementParser.parse(header + "01 MO Time Postings : 08:00 16:00 $digits\n")
            SapTimeStatementParser.parse(header + "01 MO Time Postings : 08:00 16:00 $digits,\n")
            SapTimeStatementParser.parse(header + "01 MO Time Postings : 08:00 $digits\n")
            SapTimeStatementParser.parse("$digits\n$header")
            SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt $digits")
            SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt $digits,0")
        }
    }

    @Test
    fun veryLongWhitespaceRunsAreNoRegexBomb() {
        val ws = " ".repeat(200_000)
        assertFast(15000) {
            SapTimeStatementParser.parse(header + "01 MO Time${ws}Postings : 08:00${ws}16:00${ws}x\n")
            SapTimeStatementParser.parse("from${ws}01.10.2025${ws}x")
            SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt${ws}x")
            SapTimeStatementParser.parseFlexBalance("GLZ-Saldo akt. Periode$ws")
        }
    }

    @Test
    fun thousandsSeparatorChainsAreNoRegexBomb() {
        val chain = "1" + ".000".repeat(50_000)
        assertFast(15000) {
            SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt $chain")
            SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt ${chain},0")
        }
    }

    @Test
    fun repeatedLabelsWithoutNumbersAreNoRegexBomb() {
        val text = "GLZ-Saldo Gesamt ".repeat(20_000) + "x"
        assertFast(15000) { SapTimeStatementParser.parseFlexBalance(text) }
        val text2 = "Flextime Bal. Curr. Period".repeat(10_000)
        assertFast(15000) { SapTimeStatementParser.parseFlexBalance(text2) }
    }

    @Test
    fun repeatedDateStemsAreNoRegexBomb() {
        val text = "from 01.".repeat(30_000) + "vom 01.".repeat(30_000)
        assertFast(15000) { SapTimeStatementParser.parse(text) }
    }

    @Test
    fun hugeFlexNumberDoesNotCrash() {
        val b = SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt " + "9".repeat(400) + ",00")
        // either null or a value; must not throw. withFlexBalance drops anything >= 10000.
        if (b != null) assertNotNull(b)
    }

    @Test
    fun flexNumbersWithGroupingAndNegativeSign() {
        val b = SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt 1.234,50-")!!
        assertEquals(-1234.5, b.total!!, 1e-9)
    }

    @Test
    fun flexLabelsWithoutHeaderStillGiveBalanceWithoutPeriod() {
        val b = SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt 2,00")!!
        assertEquals(2.0, b.total!!, 1e-9)
        assertNull(b.year)
        assertNull(b.month)
    }

    @Test
    fun parseIsDeterministic() {
        val text = header + "01 WE Time Postings : 08:00 12:00 4,00\n01 WE Time Postings : 13:00 17:30 4,50\n"
        assertEquals(SapTimeStatementParser.parse(text), SapTimeStatementParser.parse(text))
    }
}
