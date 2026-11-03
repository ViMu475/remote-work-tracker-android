package com.remotework.tracker.android

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.remotework.tracker.FlexBalance
import com.remotework.tracker.ParsedWorkEntry
import com.remotework.tracker.SapTimeStatementParser
import java.io.InputStream

data class PdfParseResult(
    val entries: List<ParsedWorkEntry>,
    val errors: List<String>,
    val flexBalance: FlexBalance? = null
)

class PdfParser(private val context: Context) {

    /** Call from a background thread: the PDFBox resource loader reads assets on first use. */
    fun parsePdf(uri: Uri): PdfParseResult {
        val entries = mutableListOf<ParsedWorkEntry>()
        val errors = mutableListOf<String>()
        var flexBalance: FlexBalance? = null

        try {
            PDFBoxResourceLoader.init(context)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val document = PDDocument.load(stream)
                document.use { doc ->
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(doc)
                    flexBalance = SapTimeStatementParser.parseFlexBalance(text)

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

        return PdfParseResult(entries, errors, flexBalance)
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
