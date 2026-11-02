package com.remotework.tracker

import kotlinx.serialization.Serializable

@Serializable
data class WorkEntry(
    val id: String,
    val datum: String,
    val startTime: String,
    val endTime: String,
    val pauseMinutes: Double,
    val remoteStundenHeute: Double,
    val totalStundenHeute: Double,
    val pauseWarning: String? = null,
    /** [SOURCE_PDF_IMPORT] for entries loaded from a statement, null for manual entries. */
    val source: String? = null
)

const val SOURCE_PDF_IMPORT = "pdf_import"

@Serializable
data class MonthData(
    val monat: String,
    val jahr: Int,
    val individuelleQuote: Double,
    val sollStundenGesamt: Double,
    val maxRemoteStunden: Double,
    val istRemoteStundenLaufend: Double,
    val istArbeitszeitLaufend: Double,
    val tatsaechlicheRemoteQuote: Double = 0.0,
    val quartal: String? = null
)

@Serializable
data class TrackerState(
    val aktuellerMonat: String,
    val aktuellesJahr: Int,
    val currentMonthData: MonthData,
    val currentQuartal: String,
    val dailyRecords: Map<String, List<WorkEntry>> = emptyMap(),
    val monatlicheHistorie: List<MonthData> = emptyList(),
    val individuelleQuoteProzent: Double = 40.0,
    val sollStundenProTag: Double = 8.0,
    val language: String = "de",
    val darkMode: Boolean = false
)

object MonthNames {
    val german = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )

    val monthNumbers = german.mapIndexed { index, name -> name to index + 1 }.toMap()

    fun getMonthNumber(name: String): Int = monthNumbers[name] ?: 1
    fun getMonthName(number: Int): String = german.getOrElse(number - 1) { "Januar" }
}

// Cross-platform Result wrapper (Kotlin Result<T> extension functions aren't exported to Swift)
data class OperationResult(
    val state: TrackerState? = null,
    val error: String? = null
) {
    val isSuccess: Boolean get() = state != null
}

object BavarianHolidays {
    val holidays2025 = setOf(
        "2025-01-01", "2025-01-06", "2025-04-18", "2025-04-21",
        "2025-05-01", "2025-05-29", "2025-06-09", "2025-06-19",
        "2025-08-15", "2025-10-03", "2025-11-01", "2025-12-25", "2025-12-26"
    )

    val holidays2026 = setOf(
        "2026-01-01", "2026-01-06", "2026-04-03", "2026-04-06",
        "2026-05-01", "2026-05-14", "2026-05-25", "2026-06-04",
        "2026-08-15", "2026-10-03", "2026-11-01", "2026-12-25", "2026-12-26"
    )

    val holidays2027 = setOf(
        "2027-01-01", "2027-01-06", "2027-03-26", "2027-03-29",
        "2027-05-01", "2027-05-06", "2027-05-17", "2027-05-27",
        "2027-08-15", "2027-10-03", "2027-11-01", "2027-12-25", "2027-12-26"
    )

    val holidays2028 = setOf(
        "2028-01-01", "2028-01-06", "2028-04-14", "2028-04-17",
        "2028-05-01", "2028-05-25", "2028-06-05", "2028-06-15",
        "2028-08-15", "2028-10-03", "2028-11-01", "2028-12-25", "2028-12-26"
    )

    val holidays2029 = setOf(
        "2029-01-01", "2029-01-06", "2029-03-30", "2029-04-02",
        "2029-05-01", "2029-05-10", "2029-05-21", "2029-05-31",
        "2029-08-15", "2029-10-03", "2029-11-01", "2029-12-25", "2029-12-26"
    )

    val holidays2030 = setOf(
        "2030-01-01", "2030-01-06", "2030-04-19", "2030-04-22",
        "2030-05-01", "2030-05-30", "2030-06-10", "2030-06-20",
        "2030-08-15", "2030-10-03", "2030-11-01", "2030-12-25", "2030-12-26"
    )

    // Only 2025-2030 are covered; other years have no holidays and need a new table.
    // (The Flask web app generates the same dates for 2025-2030 in config.py.)
    fun isHoliday(date: String): Boolean =
        date in holidays2025 || date in holidays2026 || date in holidays2027 ||
            date in holidays2028 || date in holidays2029 || date in holidays2030
}
