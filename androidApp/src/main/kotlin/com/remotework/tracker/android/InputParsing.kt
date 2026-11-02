package com.remotework.tracker.android

import java.util.Locale

private val decimalPattern = Regex("""^\d+([.,]\d+)?$""")

/**
 * Parses a user-typed non-negative decimal. Accepts "," and "." as separator and rejects
 * everything else (blank, "NaN", "Infinity", signs, exponents, trailing letters).
 */
internal fun parseDecimalOrNull(text: String): Double? {
    val trimmed = text.trim()
    if (!decimalPattern.matches(trimmed)) return null
    return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Fixed-locale number formatting so the display does not depend on the device locale. */
internal fun fmt(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)

/** "30" for whole minutes, otherwise the exact value (no silent truncation when editing). */
internal fun minutesText(minutes: Double): String =
    if (minutes == Math.floor(minutes) && minutes < 1_000_000) minutes.toLong().toString() else minutes.toString()

/** ASCII-only file name part (umlauts transliterated) that is safe for receiving apps. */
internal fun safeFileName(name: String): String =
    name.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
        .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")

internal enum class RemainingLevel { OVER_QUOTA, LOW, OK }

/** [rawRemaining] is max - used and may be negative (over quota); only the display is clamped. */
internal fun remainingLevel(rawRemaining: Double): RemainingLevel = when {
    rawRemaining < 0 -> RemainingLevel.OVER_QUOTA
    rawRemaining < 10 -> RemainingLevel.LOW
    else -> RemainingLevel.OK
}
