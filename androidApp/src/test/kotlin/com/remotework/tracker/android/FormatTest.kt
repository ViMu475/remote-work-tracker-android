package com.remotework.tracker.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test fun numbersFollowTheLanguage() {
        assertEquals("8,00", fmtL(8.0, 2, "de"))
        assertEquals("8.00", fmtL(8.0, 2, "en"))
        assertEquals("1234,5", fmtL(1234.5, 1, "de"))
    }

    @Test fun datesAreGermanOrIso() {
        assertEquals("06.10.2025", fmtDate("2025-10-06", "de"))
        assertEquals("2025-10-06", fmtDate("2025-10-06", "en"))
        assertEquals("kein Datum", fmtDate("kein Datum", "de"))
    }

    @Test fun pluralForms() {
        assertEquals("1 Monat über Limit", monthsOverText(1, "de"))
        assertEquals("2 Monate über Limit", monthsOverText(2, "de"))
        assertEquals("0 Monate über Limit", monthsOverText(0, "de"))
        assertEquals("1 month over limit", monthsOverText(1, "en"))
        assertEquals("2 months over limit", monthsOverText(2, "en"))
        assertEquals("1 Monat", monthsText(1, "de"))
        assertEquals("3 quarters", quartersText(3, "en"))
    }

    @Test fun pauseWarningIsTranslatedForDisplayOnly() {
        val raw = "WARNUNG: Pause (0.5h) < gesetzl. Minimum (0.75h)!"
        assertEquals("WARNUNG: Pause (0,5h) < gesetzl. Minimum (0,75h)!", localizePauseWarning(raw, "de"))
        assertEquals("WARNING: break (0.5h) shorter than statutory minimum (0.75h)!", localizePauseWarning(raw, "en"))
        assertEquals("anderer Text", localizePauseWarning("anderer Text", "en"))
    }

    @Test fun fieldValidation() {
        assertTrue(isValidIsoDate("2025-10-06"))
        assertFalse(isValidIsoDate("2025-13-45"))
        assertFalse(isValidIsoDate("06.10.2025"))
        assertTrue(isValidTime("08:00")); assertTrue(isValidTime("8:30")); assertTrue(isValidTime("23:59"))
        assertFalse(isValidTime("24:00")); assertFalse(isValidTime("99:99")); assertFalse(isValidTime("0800"))
    }
}
