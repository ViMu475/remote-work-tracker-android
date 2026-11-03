package com.remotework.tracker

import kotlinx.serialization.Serializable

data class ParsedWorkEntry(
    val datum: String,
    val startTime: String,
    val endTime: String,
    val pauseMinutes: Double,
    val remoteHours: Double?,
    val isRemote: Boolean = false
)

/**
 * Flextime balance (hours) printed in the summary of a statement. Positive = credit.
 * [year]/[month] name the statement period the figures belong to.
 */
@Serializable
data class FlexBalance(
    val previous: Double? = null,
    val current: Double? = null,
    val total: Double? = null,
    val year: Int? = null,
    val month: Int? = null
)

// Pure text parsing of SAP time statements (no Android dependencies, unit-testable)
object SapTimeStatementParser {
    private val dateRangePatternEn = Regex("""from\s+\d{2}\.(\d{2})\.(\d{4})\s+to""", RegexOption.IGNORE_CASE)
    private val dateRangePatternDe = Regex("""vom\s+\d{2}\.(\d{2})\.(\d{4})\s+bis""", RegexOption.IGNORE_CASE)

    // Fallback: a plausible YYYYMMDD (e.g. from a file name in the text). The old "any 8 digits" pattern
    // also matched personnel numbers and invented a month and year from them.
    private val filenamePattern = Regex("""(?<!\d)(20\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(?!\d)""")

    // English: "01 MO|TU|WE|TH|FR|SA|SU", German: "01 MO|DI|MI|DO|FR|SA|SO"
    private val dayPattern = Regex("""^(\d{2})\s+(MO|TU|WE|TH|FR|SA|SU|DI|MI|DO|SO)""", RegexOption.IGNORE_CASE)
    private val bookingLabel = Regex("""time\s+postings?|zeitbuchung""", RegexOption.IGNORE_CASE)

    private const val NUM = """(\d{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2})(-)?"""
    private val flexPrevious = Regex("""(?:GLZ-Saldo\s+Vorperiode|Flex\.?\s*Bal\.?\s*Prev\.?\s*Period)\s+$NUM""", RegexOption.IGNORE_CASE)
    private val flexCurrent = Regex("""(?:GLZ-Saldo\s+akt\.?\s*Periode|Flextime\s+Bal\.?\s*Curr\.?\s*Period)\s*$NUM""", RegexOption.IGNORE_CASE)
    private val flexTotal = Regex("""(?:GLZ-Saldo\s+Gesamt|Flextime\s+Balance\s+Total)\s+$NUM""", RegexOption.IGNORE_CASE)

    private fun flexValue(regex: Regex, text: String): Double? {
        val m = regex.find(text) ?: return null   // first match only: the labels repeat in the overtime table
        val value = m.groupValues[1].replace(".", "").replace(",", ".").toDouble()
        return if (m.groupValues[2].isNotEmpty()) -value else value
    }

    /** Flextime balance of the statement, or null when the text has none. A trailing minus means negative. */
    fun parseFlexBalance(text: String): FlexBalance? {
        val previous = flexValue(flexPrevious, text)
        val current = flexValue(flexCurrent, text)
        val total = flexValue(flexTotal, text)
        if (previous == null && current == null && total == null) return null
        val range = dateRangePatternEn.find(text) ?: dateRangePatternDe.find(text)
        return FlexBalance(
            previous = previous, current = current, total = total,
            year = range?.groupValues?.get(2)?.toIntOrNull(),
            month = range?.groupValues?.get(1)?.toIntOrNull()
        )
    }

    private val timePattern = Regex("""(\d{2}):(\d{2})\s+(\d{2}):(\d{2})\s+(\d+[,\.]\d+)""")

    fun parse(text: String): List<ParsedWorkEntry> {

        // Extract month and year from header
        // English: "from DD.MM.YYYY to DD.MM.YYYY"
        // German: "vom DD.MM.YYYY bis DD.MM.YYYY"
        var dateRangeMatch = dateRangePatternEn.find(text)
        if (dateRangeMatch == null) {
            dateRangeMatch = dateRangePatternDe.find(text)
        }

        // If still no match, try to extract from filename pattern in text
        if (dateRangeMatch == null) {
            val filenameMatch = filenamePattern.find(text)
            if (filenameMatch != null) {
                val year = filenameMatch.groupValues[1]
                val month = filenameMatch.groupValues[2]
                return parseWithDate(text, month, year)
            }
            return emptyList()
        }

        val month = dateRangeMatch.groupValues[1]
        val year = dateRangeMatch.groupValues[2]

        return parseWithDate(text, month, year)
    }

    private fun parseWithDate(text: String, month: String, year: String): List<ParsedWorkEntry> {
        val entries = mutableListOf<ParsedWorkEntry>()
        val lines = text.split("\n")

        // Pattern for SAP TimeStatement entries with both English and German weekdays:
        // English: "01 MO|TU|WE|TH|FR|SA|SU"
        // German: "01 MO|DI|MI|DO|FR|SA|SO"

        var currentDay: String? = null
        // Remote flag of the day line; used only for continuation lines without their own keyword
        var dayIsRemote = false

        for (line in lines) {
            val trimmedLine = line.trim()

            // Check if line starts with day number
            val dayMatch = dayPattern.find(trimmedLine)
            if (dayMatch != null) {
                currentDay = dayMatch.groupValues[1]
                dayIsRemote = isRemoteWorkLine(trimmedLine)
            }

            // Check for time entries
            if (currentDay != null) {
                val timeMatch = timePattern.find(trimmedLine)
                if (timeMatch != null) {
                    // Only real bookings count. Leave, flextime compensation and other absence
                    // lines also carry times (e.g. "Leave 08:00 16:30") but are not worked time.
                    if (!isBookingLine(trimmedLine) || isNonWorkLine(trimmedLine)) continue

                    val startHour = timeMatch.groupValues[1]
                    val startMin = timeMatch.groupValues[2]
                    val endHour = timeMatch.groupValues[3]
                    val endMin = timeMatch.groupValues[4]

                    val datum = "$year-$month-$currentDay"
                    val startTime = "$startHour:$startMin"
                    val endTime = "$endHour:$endMin"

                    // Each booking line decides for itself; only lines without a keyword
                    // inherit the day line's flag
                    val lineIsRemote = when {
                        isRemoteWorkLine(trimmedLine) -> true
                        isOfficeWorkLine(trimmedLine) -> false
                        else -> dayIsRemote
                    }

                    entries.add(ParsedWorkEntry(
                        datum = datum,
                        startTime = startTime,
                        endTime = endTime,
                        pauseMinutes = 0.0,   // set below from all sessions of the day
                        remoteHours = null,
                        isRemote = lineIsRemote
                    ))
                }
            }
        }

        return withStatementBreaks(entries)
    }

    /**
     * The statement's "Istz" is the booked time minus the legal break. The break of a day depends on
     * ALL of its bookings (gaps count as break), so it is assigned per day, not per booking.
     */
    private fun withStatementBreaks(entries: List<ParsedWorkEntry>): List<ParsedWorkEntry> {
        val result = entries.toMutableList()
        for ((_, indices) in entries.indices.groupBy { entries[it].datum }) {
            val pauses = TrackerCalculations.assignStatementBreaks(indices.map { entries[it].startTime to entries[it].endTime })
            indices.forEachIndexed { n, i -> result[i] = entries[i].copy(pauseMinutes = pauses[n]) }
        }
        return result
    }

    // Check if a line indicates remote work (English or German)
    private fun isRemoteWorkLine(line: String): Boolean {
        val lowerLine = line.lowercase()
        val remoteKeywords = listOf("remote work", "remote", "telearbeit", "home office", "homeoffice")
        return remoteKeywords.any { lowerLine.contains(it) }
    }

    // A booking row is labelled "Time Postings : ..." (EN) or "Zeitbuchungen : ..." (DE)
    private fun isBookingLine(line: String): Boolean = bookingLabel.containsMatchIn(line)

    // Lines with times that are not worked time (flextime compensation)
    private fun isNonWorkLine(line: String): Boolean {
        val lowerLine = line.lowercase()
        return listOf("gleitzeit", "flextime", "flex time", "flexitime").any { lowerLine.contains(it) }
    }

    // Check if a line indicates office work (English or German)
    private fun isOfficeWorkLine(line: String): Boolean {
        val lowerLine = line.lowercase()
        val officeKeywords = listOf("at work", "anwesend", "im büro", "büro")
        return officeKeywords.any { lowerLine.contains(it) }
    }
}
