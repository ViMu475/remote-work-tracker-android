package com.remotework.tracker.android

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

data class ParsedWorkEntry(
    val datum: String,
    val startTime: String,
    val endTime: String,
    val pauseMinutes: Double,
    val remoteHours: Double?,
    val isRemote: Boolean = false
)

data class PdfParseResult(
    val entries: List<ParsedWorkEntry>,
    val errors: List<String>
)

class PdfParser(private val context: Context) {

    /** Call from a background thread: the PDFBox resource loader reads assets on first use. */
    fun parsePdf(uri: Uri): PdfParseResult {
        val entries = mutableListOf<ParsedWorkEntry>()
        val errors = mutableListOf<String>()

        try {
            PDFBoxResourceLoader.init(context)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val document = PDDocument.load(stream)
                document.use { doc ->
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(doc)

                    // Try SAP TimeStatement format first
                    val sapEntries = SapTimeStatementParser.parse(text)
                    if (sapEntries.isNotEmpty()) {
                        entries.addAll(sapEntries)
                    } else {
                        // Fall back to generic parsing
                        val lines = text.split("\n")
                        for (line in lines) {
                            val entry = parseWorkLine(line, null)
                            if (entry != null) {
                                entries.add(entry)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("PDF Fehler: ${e.message}")
        } catch (e: OutOfMemoryError) {
            // a huge PDF must not take the whole app down
            errors.add("PDF Fehler: Datei zu groß")
        }

        if (entries.isEmpty() && errors.isEmpty()) {
            errors.add("Keine Arbeitszeit-Einträge gefunden")
        }

        return PdfParseResult(entries, errors)
    }

    private fun parseWorkLine(line: String, defaultYear: String?): ParsedWorkEntry? {
        // Pattern 1: "DD.MM.YYYY HH:MM HH:MM" or "DD.MM.YYYY HH:MM - HH:MM"
        val pattern1 = Regex("""(\d{2})\.(\d{2})\.(\d{4})\s+(\d{2}:\d{2})\s*-?\s*(\d{2}:\d{2})""")
        val match1 = pattern1.find(line)
        if (match1 != null) {
            val (day, month, year, start, end) = match1.destructured
            val datum = "$year-$month-$day"
            return ParsedWorkEntry(datum, start, end, 30.0, null)
        }

        // Pattern 2: "YYYY-MM-DD HH:MM HH:MM"
        val pattern2 = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})\s*-?\s*(\d{2}:\d{2})""")
        val match2 = pattern2.find(line)
        if (match2 != null) {
            val (datum, start, end) = match2.destructured
            return ParsedWorkEntry(datum, start, end, 30.0, null)
        }

        // Pattern 3: Date with start/end and pause info
        val pattern3 = Regex("""(\d{2})\.(\d{2})\.(\d{4})\s+(\d{2}:\d{2})\s+(\d{2}:\d{2})\s+(\d+)""")
        val match3 = pattern3.find(line)
        if (match3 != null) {
            val (day, month, year, start, end, pause) = match3.destructured
            val datum = "$year-$month-$day"
            return ParsedWorkEntry(datum, start, end, pause.toDoubleOrNull() ?: 30.0, null)
        }

        // Pattern 4: Common German timesheet format "Mo 06.01. 08:00 16:30"
        val pattern4 = Regex("""(?:Mo|Di|Mi|Do|Fr|Sa|So)\s+(\d{2})\.(\d{2})\.\s+(\d{2}:\d{2})\s+(\d{2}:\d{2})""")
        val match4 = pattern4.find(line)
        if (match4 != null) {
            val (day, month, start, end) = match4.destructured
            val year = defaultYear ?: java.time.LocalDate.now().year.toString()
            val datum = "$year-$month-$day"
            return ParsedWorkEntry(datum, start, end, 30.0, null)
        }

        // Pattern 5: Time range like "08:00-16:30" with date before or after
        val pattern5 = Regex("""(\d{2})\.(\d{2})\.(\d{4}).*?(\d{2}):(\d{2})\s*[-–]\s*(\d{2}):(\d{2})""")
        val match5 = pattern5.find(line)
        if (match5 != null) {
            val groups = match5.groupValues
            val datum = "${groups[3]}-${groups[2]}-${groups[1]}"
            val start = "${groups[4]}:${groups[5]}"
            val end = "${groups[6]}:${groups[7]}"
            return ParsedWorkEntry(datum, start, end, 30.0, null)
        }

        return null
    }
}


// Pure text parsing of SAP time statements (no Android dependencies, unit-testable)
internal object SapTimeStatementParser {
    private val dateRangePatternEn = Regex("""from\s+\d{2}\.(\d{2})\.(\d{4})\s+to""", RegexOption.IGNORE_CASE)
    private val dateRangePatternDe = Regex("""vom\s+\d{2}\.(\d{2})\.(\d{4})\s+bis""", RegexOption.IGNORE_CASE)

    // Fallback: a plausible YYYYMMDD (e.g. from a file name in the text). The old "any 8 digits" pattern
    // also matched personnel numbers and invented a month and year from them.
    private val filenamePattern = Regex("""(?<!\d)(20\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(?!\d)""")

    // English: "01 MO|TU|WE|TH|FR|SA|SU", German: "01 MO|DI|MI|DO|FR|SA|SO"
    private val dayPattern = Regex("""^(\d{2})\s+(MO|TU|WE|TH|FR|SA|SU|DI|MI|DO|SO)""", RegexOption.IGNORE_CASE)
    private val bookingLabel = Regex("""time\s+postings?|zeitbuchung""", RegexOption.IGNORE_CASE)
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
                        pauseMinutes = 30.0,
                        remoteHours = null,
                        isRemote = lineIsRemote
                    ))
                }
            }
        }

        return entries
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
