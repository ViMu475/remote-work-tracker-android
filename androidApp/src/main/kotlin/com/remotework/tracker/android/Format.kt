package com.remotework.tracker.android

import java.util.Locale

/** Display formatting that follows the app language (not the device locale). */
internal fun localeOf(lang: String): Locale = if (lang == "de") Locale.GERMANY else Locale.US

/** DE: decimal comma ("8,00"), EN: decimal point ("8.00"). */
internal fun fmtL(value: Double, digits: Int, lang: String): String =
    String.format(localeOf(lang), "%.${digits}f", value)

private val isoDate = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/** DE: "06.10.2025"; EN (and anything that is not an ISO date) stays as is. */
internal fun fmtDate(iso: String, lang: String): String {
    if (lang != "de") return iso
    val m = isoDate.matchEntire(iso) ?: return iso
    return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
}

private val pauseWarningDe = Regex("""^WARNUNG: Pause \((.+?)h\) < gesetzl\. Minimum \((.+?)h\)!$""")

/**
 * The warning text is produced by the shared module (German, decimal point). Only the display is
 * translated / localised here; unknown texts are shown unchanged.
 */
internal fun localizePauseWarning(text: String, lang: String): String {
    val m = pauseWarningDe.matchEntire(text) ?: return text
    fun num(s: String) = if (lang == "de") s.replace('.', ',') else s
    return t("pause_warning", lang).format(num(m.groupValues[1]), num(m.groupValues[2]))
}

/** "1 Monat" / "2 Monate", "1 month" / "2 months". */
internal fun monthsText(n: Int, lang: String): String =
    "$n " + if (n == 1) t("report_month_one", lang) else t("report_months", lang)

internal fun quartersText(n: Int, lang: String): String =
    "$n " + if (n == 1) t("report_quarter_one", lang) else t("report_quarters", lang)

/** "1 Monat über Limit" / "2 Monate über Limit". */
internal fun monthsOverText(n: Int, lang: String): String =
    "$n " + if (n == 1) t("report_month_over_one", lang) else t("report_months_over", lang)

private val timePattern = Regex("""^([01]?\d|2[0-3]):[0-5]\d$""")

internal fun isValidTime(text: String): Boolean = timePattern.matches(text.trim())

internal fun isValidIsoDate(text: String): Boolean {
    if (!isoDate.matches(text.trim())) return false
    return try {
        java.time.LocalDate.parse(text.trim())
        true
    } catch (e: Exception) {
        false
    }
}
