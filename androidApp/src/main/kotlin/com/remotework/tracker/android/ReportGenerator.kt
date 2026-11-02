package com.remotework.tracker.android

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.remotework.tracker.MonthData
import com.remotework.tracker.WorkEntry
import java.io.File
import java.util.Locale

object ReportGenerator {
    fun generateMonthlyPdf(
        context: Context,
        monthData: MonthData,
        entries: List<WorkEntry>
    ): File {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPos = 50f

            val titlePaint = Paint().apply { textSize = 20f; color = Color.BLACK; isFakeBoldText = true }
            val headerPaint = Paint().apply { textSize = 12f; color = Color.DKGRAY; isFakeBoldText = true }
            val textPaint = Paint().apply { textSize = 11f; color = Color.BLACK }
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

            // Title
            canvas.drawText("Monatsbericht ${monthData.monat} ${monthData.jahr}", 40f, yPos, titlePaint)
            yPos += 35f

            // Summary
            canvas.drawText("Soll-Stunden: ${String.format(Locale.US, "%.1f", monthData.sollStundenGesamt)}h", 40f, yPos, textPaint)
            yPos += 18f
            canvas.drawText("Ist Arbeitszeit: ${String.format(Locale.US, "%.1f", monthData.istArbeitszeitLaufend)}h", 40f, yPos, textPaint)
            yPos += 18f
            canvas.drawText("Ist Remote: ${String.format(Locale.US, "%.1f", monthData.istRemoteStundenLaufend)}h", 40f, yPos, textPaint)
            yPos += 18f
            canvas.drawText("Max Remote: ${String.format(Locale.US, "%.1f", monthData.maxRemoteStunden)}h", 40f, yPos, textPaint)
            yPos += 18f
            canvas.drawText("Quote: ${String.format(Locale.US, "%.1f", monthData.individuelleQuote)}%", 40f, yPos, textPaint)
            yPos += 30f

            // Line
            canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
            yPos += 20f

            // Table header
            canvas.drawText("Datum", 40f, yPos, headerPaint)
            canvas.drawText("Start", 160f, yPos, headerPaint)
            canvas.drawText("Ende", 230f, yPos, headerPaint)
            canvas.drawText("Pause", 300f, yPos, headerPaint)
            canvas.drawText("Remote", 370f, yPos, headerPaint)
            canvas.drawText("Gesamt", 450f, yPos, headerPaint)
            yPos += 5f
            canvas.drawLine(40f, yPos, 555f, yPos, linePaint)
            yPos += 15f

            // Entries
            val sortedEntries = entries.sortedBy { it.datum }
            for (entry in sortedEntries) {
                if (yPos > 790f) {
                    document.finishPage(page)
                    val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                    page = document.startPage(newPageInfo)
                    canvas = page.canvas
                    yPos = 50f
                }
                canvas.drawText(entry.datum, 40f, yPos, textPaint)
                canvas.drawText(entry.startTime, 160f, yPos, textPaint)
                canvas.drawText(entry.endTime, 230f, yPos, textPaint)
                canvas.drawText("${entry.pauseMinutes.toInt()} min", 300f, yPos, textPaint)
                canvas.drawText("${String.format(Locale.US, "%.1f", entry.remoteStundenHeute)}h", 370f, yPos, textPaint)
                canvas.drawText("${String.format(Locale.US, "%.1f", entry.totalStundenHeute)}h", 450f, yPos, textPaint)
                yPos += 16f
            }

            document.finishPage(page)

            val file = newExportFile(context, "Monatsbericht_${monthData.monat}_${monthData.jahr}.pdf")
            file.outputStream().use { document.writeTo(it) }
            return file
        } finally {
            document.close()
        }
    }
}
