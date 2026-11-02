package com.remotework.tracker.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.remotework.tracker.LocalStorage
import com.remotework.tracker.MonthData
import com.remotework.tracker.TrackerRepository
import com.remotework.tracker.TrackerState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Cross-checks the app's PDF parsing (PDFBox text extraction + SapTimeStatementParser) against
 * an independent parse of the same real statements (pdftotext + a separate script).
 *
 * The statements contain personal data and are NOT part of the repository. The test looks for
 *   <app cache>/realpdfs/expected.json   {"<file>.pdf": [{"datum","start","end","remote"}, ...]}
 *   <app cache>/realpdfs/<file>.pdf
 * and is skipped when they are not there. Put them there with `adb shell run-as <pkg> ...`.
 */
@RunWith(AndroidJUnit4::class)
class RealStatementsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "realpdfs")

    private data class Row(val datum: String, val start: String, val end: String, val remote: Boolean)

    private fun expected(): Map<String, List<Row>> {
        val file = File(dir, "expected.json")
        assumeTrue("no real statements on this device", file.exists())
        val json = JSONObject(file.readText())
        return json.keys().asSequence().associateWith { name ->
            val arr = json.getJSONArray(name)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Row(o.getString("datum"), o.getString("start"), o.getString("end"), o.getBoolean("remote"))
            }
        }
    }

    @Test
    fun parserFindsExactlyTheBookingsOfEveryRealStatement() {
        val problems = mutableListOf<String>()
        var checked = 0
        for ((name, rows) in expected()) {
            val pdf = File(dir, name)
            if (!pdf.exists()) continue
            checked++
            val result = PdfParser(context).parsePdf(Uri.fromFile(pdf))
            val actual = result.entries.map { Row(it.datum, it.startTime, it.endTime, it.isRemote) }
            if (actual != rows) {
                val missing = rows - actual.toSet()
                val extra = actual - rows.toSet()
                problems += "$name: expected ${rows.size}, parsed ${actual.size}; missing=$missing extra=$extra errors=${result.errors}"
            }
        }
        assertTrue("no statement PDF found next to expected.json", checked > 0)
        assertEquals("parser differs from the independent parse:\n" + problems.joinToString("\n"), emptyList<String>(), problems)
    }

    @Test
    fun everyParsedWorkdayBookingCanBeImportedWithoutErrors() {
        val repository = TrackerRepository(LocalStorage(context))
        val failures = mutableListOf<String>()
        var imported = 0
        for ((name, _) in expected()) {
            val pdf = File(dir, name)
            if (!pdf.exists()) continue
            val parsed = PdfParser(context).parsePdf(Uri.fromFile(pdf)).entries
            // one fresh state per statement, like importing a single file
            var state: TrackerState = repository.recalculateMonthlyTotals(
                TrackerState(
                    aktuellerMonat = "Januar", aktuellesJahr = 2026,
                    currentMonthData = MonthData("Januar", 2026, 40.0, 0.0, 0.0, 0.0, 0.0),
                    currentQuartal = "Q1/2026"
                )
            )
            for (e in parsed) {
                val r = repository.addWorkEntry(state, e.datum, e.startTime, e.endTime, e.pauseMinutes, if (e.isRemote) null else 0.0)
                val next = r.state
                if (next != null) { state = next; imported++ } else failures += "$name ${e.datum} ${e.startTime}-${e.endTime}: ${r.error}"
            }
        }
        assertTrue("nothing imported", imported > 0)
        assertEquals("import errors:\n" + failures.joinToString("\n"), emptyList<String>(), failures)
    }
}
