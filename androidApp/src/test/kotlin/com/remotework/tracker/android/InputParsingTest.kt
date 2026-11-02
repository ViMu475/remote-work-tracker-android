package com.remotework.tracker.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class InputParsingTest {

    // --- parseDecimalOrNull -----------------------------------------------------------------------

    @Test
    fun decimal_acceptsDotAndCommaAndIntegers() {
        assertEquals(4.5, parseDecimalOrNull("4,5")!!, 1e-9)
        assertEquals(4.5, parseDecimalOrNull("4.5")!!, 1e-9)
        assertEquals(30.0, parseDecimalOrNull("30")!!, 1e-9)
        assertEquals(0.0, parseDecimalOrNull("0")!!, 1e-9)
        assertEquals(7.5, parseDecimalOrNull("  7,5 ")!!, 1e-9)
    }

    @Test
    fun decimal_rejectsEverythingElse() {
        for (bad in listOf("", " ", "abc", "NaN", "Infinity", "-Infinity", "-5", "+5", "1e3", "5d", "5f", "4,5,6", "4..5", ",5", "5,", "0x10", "1 000")) {
            assertNull("expected null for '$bad'", parseDecimalOrNull(bad))
        }
    }

    // --- formatting -------------------------------------------------------------------------------

    @Test
    fun fmt_isIndependentOfTheDefaultLocale() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("8.50", fmt(8.5, 2))
            assertEquals("7.5", fmt(7.5, 1))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test
    fun minutesText_keepsFractionsInsteadOfTruncating() {
        assertEquals("30", minutesText(30.0))
        assertEquals("7.5", minutesText(7.5))
        assertEquals("0", minutesText(0.0))
    }

    // --- file names -------------------------------------------------------------------------------

    @Test
    fun safeFileName_transliteratesUmlautsAndStripsOddCharacters() {
        assertEquals("Monatsbericht_Maerz_2026.pdf", safeFileName("Monatsbericht_März_2026.pdf"))
        assertEquals("Export_Oktober_2025.csv", safeFileName("Export_Oktober_2025.csv"))
        assertEquals("a_b_c.txt", safeFileName("a b/c.txt"))
        assertEquals("Strasse", safeFileName("Straße"))
    }

    // --- remaining level --------------------------------------------------------------------------

    @Test
    fun remainingLevel_overQuotaIsDistinctFromLow() {
        assertEquals(RemainingLevel.OVER_QUOTA, remainingLevel(-0.1))
        assertEquals(RemainingLevel.OVER_QUOTA, remainingLevel(-50.0))
        assertEquals(RemainingLevel.LOW, remainingLevel(0.0))
        assertEquals(RemainingLevel.LOW, remainingLevel(9.99))
        assertEquals(RemainingLevel.OK, remainingLevel(10.0))
        assertEquals(RemainingLevel.OK, remainingLevel(70.4))
    }

    // --- bounded reading --------------------------------------------------------------------------

    @Test
    fun readTextLimited_readsSmallInputAndRefusesLargeInput() {
        assertEquals("Grüße", readTextLimited(ByteArrayInputStream("Grüße".toByteArray()), 100))
        assertEquals("", readTextLimited(ByteArrayInputStream(ByteArray(0)), 100))
        assertNull(readTextLimited(ByteArrayInputStream(ByteArray(101)), 100))
        assertTrue(readTextLimited(ByteArrayInputStream(ByteArray(100)), 100) != null)
    }
}
