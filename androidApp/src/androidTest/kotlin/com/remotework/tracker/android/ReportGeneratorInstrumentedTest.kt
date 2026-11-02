package com.remotework.tracker.android

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.remotework.tracker.MonthData
import com.remotework.tracker.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportGeneratorInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val month = MonthData("Oktober", 2025, 40.0, 176.0, 70.4, 8.0, 8.0)

    private fun entry(day: Int) = WorkEntry(
        id = "e$day", datum = "2025-10-%02d".format(day), startTime = "08:00", endTime = "16:30",
        pauseMinutes = 30.0, remoteStundenHeute = 8.0, totalStundenHeute = 8.0
    )

    private fun pageCount(file: java.io.File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }

    @Test
    fun report_isAValidPdfNamedAfterTheMonth() {
        val file = ReportGenerator.generateMonthlyPdf(context, month, listOf(entry(6)))
        assertEquals("Monatsbericht_Oktober_2025.pdf", file.name)
        assertTrue(file.length() > 0)
        assertEquals("%PDF", file.inputStream().use { String(it.readNBytes(4)) })
        assertEquals(1, pageCount(file))
    }

    @Test
    fun report_fileNameIsAsciiForMonthsWithUmlauts() {
        val file = ReportGenerator.generateMonthlyPdf(context, month.copy(monat = "März"), emptyList())
        assertEquals("Monatsbericht_Maerz_2025.pdf", file.name)
    }

    @Test
    fun report_keepsOnlyTheLatestExportInTheSharedFolder() {
        ReportGenerator.generateMonthlyPdf(context, month, listOf(entry(6)))
        ReportGenerator.generateMonthlyPdf(context, month.copy(monat = "November"), listOf(entry(6)))
        assertEquals(listOf("Monatsbericht_November_2025.pdf"), exportDir(context).list()!!.toList())
        assertTrue(ReportGenerator.generateMonthlyPdf(context, month, emptyList()).parentFile == exportDir(context))
    }

    @Test
    fun report_withoutEntriesStillHasOnePage() {
        val file = ReportGenerator.generateMonthlyPdf(context, month, emptyList())
        assertEquals(1, pageCount(file))
    }

    @Test
    fun report_withManyEntriesBreaksIntoSeveralPages() {
        val entries = (1..120).map { entry(it) }
        val file = ReportGenerator.generateMonthlyPdf(context, month, entries)
        assertTrue("expected more than one page", pageCount(file) > 1)
    }

    @Test
    fun report_overwritesAnExistingFileForTheSameMonth() {
        val first = ReportGenerator.generateMonthlyPdf(context, month, listOf(entry(6)))
        val sizeFirst = first.length()
        val second = ReportGenerator.generateMonthlyPdf(context, month, (1..40).map { entry(it) })
        assertEquals(first.absolutePath, second.absolutePath)
        assertTrue(second.length() != sizeFirst)
    }
}
