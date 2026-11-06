package com.remotework.tracker.android

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.remotework.tracker.FlexBalance
import com.remotework.tracker.Flextime
import com.remotework.tracker.LocalStorage
import com.remotework.tracker.MonthData
import com.remotework.tracker.MonthRef
import com.remotework.tracker.TrackerCalculations
import com.remotework.tracker.TrackerRepository
import com.remotework.tracker.TrackerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * The flextime view on the emulator: opening it from the dashboard, chart, proposals, month analysis, the report
 * and the language switch. The tests seed fictional states; with real statements in <app cache>/realpdfs/ (not part
 * of the repository) the last test also loads them and takes screenshots of the real figures. Screenshots go to
 * <external files>/shots/.
 */
@RunWith(AndroidJUnit4::class)
class FlexInstrumentedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = TrackerRepository(LocalStorage(context))
    private var scenario: ActivityScenario<MainActivity>? = null
    private val shots = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }

    @Before
    fun clearStore() {
        context.getSharedPreferences("remote_work_tracker", 0).edit().clear().commit()
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    private fun launch(seed: TrackerState? = null) {
        if (seed != null) repository.saveState(seed)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(shots, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun base(month: String = "Oktober", year: Int = 2025, lang: String = "de"): TrackerState = repository.recalculateMonthlyTotals(
        TrackerState(
            aktuellerMonat = month, aktuellesJahr = year,
            currentMonthData = MonthData(month, year, 40.0, 176.0, 70.4, 0.0, 0.0),
            currentQuartal = TrackerCalculations.getQuartalForMonth(month, year), language = lang
        )
    )

    /** Fictional balances Jan..Oct 2026 with long days in October (the latest month). */
    private fun overtimeState(lang: String = "de"): TrackerState {
        val balances = listOf(-48.0, -62.0, -35.0, -10.0, 18.0, 36.0, 47.0, 58.0, 64.0, 72.0)
        var s = base("Oktober", 2026, lang)
        var previous = 0.0
        balances.forEachIndexed { i, total ->
            s = Flextime.record(s, FlexBalance(previous, total - previous, total, 2026, i + 1))
            previous = total
        }
        for ((day, times) in listOf(
            "2026-10-01" to ("08:00" to "17:40"), "2026-10-02" to ("08:00" to "17:40"), "2026-10-05" to ("06:30" to "19:10"),
            "2026-10-06" to ("06:30" to "19:10"), "2026-10-07" to ("08:00" to "17:40"), "2026-10-09" to ("12:00" to "20:45")
        )) {
            s = repository.importWorkEntry(s, day, times.first, times.second, 30.0, null).state ?: s
        }
        return s
    }

    /** The scrollable column of the flextime dialog (the dashboard behind it has its own list). */
    private fun flexDialogScroll() = compose.onNodeWithTag("flex_scroll")

    /** Scrolls the dialog until the text is composed and then until it is fully on screen. */
    private fun scrollFlexDialogTo(text: String) {
        flexDialogScroll().performScrollToNode(hasText(text, substring = true))
        compose.onAllNodes(hasText(text, substring = true)).onFirst().performScrollTo()
    }

    /** The dashboard is a lazy list: items far down are only composed after scrolling to them. */
    private fun scrollDashboardTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    /** A text inside the flextime dialog (the dashboard behind it shows some of the same words). */
    private fun inFlexDialog(text: String) = hasText(text).and(hasAnyAncestor(hasTestTag("flex_scroll")))

    private fun SemanticsNodeInteraction.assertPresent() = assertTrue(fetchSemanticsNode() != null)

    // ---- usage ----

    @Test
    fun withoutFlextimeThereIsNoButton() {
        launch(base())
        assertEquals(0, compose.onAllNodesWithText("Gleitzeit-Verlauf").fetchSemanticsNodes().size)
    }

    @Test
    fun theDashboardButtonOpensChartProposalsAndMonthAnalysis() {
        launch(overtimeState())
        compose.onNodeWithText("Gleitzeit-Verlauf").performScrollTo().assertIsDisplayed()
        screenshot("android-01-dashboard")
        compose.onNodeWithText("Gleitzeit-Verlauf").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Gleitzeit-Saldo pro Monat", substring = true).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Rot: über 60 h", substring = true).fetchSemanticsNodes().isNotEmpty())   // summary and badge
        screenshot("android-02-verlauf-oben")
        scrollFlexDialogTo("Empfehlungen")
        compose.onNodeWithText("Empfehlungen").assertPresent()

        scrollFlexDialogTo("Monatsanalyse")
        compose.onNodeWithText("Monatsanalyse").assertPresent()
        scrollFlexDialogTo("Dringend")
        screenshot("android-03-empfehlungen")
        scrollFlexDialogTo("Netto minus Soll")
        compose.onNode(inFlexDialog("Oktober 2026")).assertPresent()
        screenshot("android-04-monatsanalyse")
        flexDialogScroll().performTouchInput { swipeUp() }
        screenshot("android-05-tage")

        compose.onNodeWithText("Schließen").performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Empfehlungen").fetchSemanticsNodes().size)
    }

    @Test
    fun theProposalsNameTheHoursToReduceAndTheCauses() {
        launch(overtimeState())
        compose.onNodeWithText("Gleitzeit-Verlauf").performScrollTo().performClick()
        scrollFlexDialogTo("Gleitzeittage")
        compose.onNodeWithText("Gleitzeittage nehmen", substring = true).assertPresent()
        compose.onNodeWithText("Das Saldo liegt über 60 h", substring = true).assertPresent()
        assertTrue(compose.onAllNodesWithText("Dringend").fetchSemanticsNodes().size >= 3)      // cap, reduce, flextime days, shorter days
    }

    @Test
    fun aMonthChipSwitchesTheAnalysis() {
        launch(overtimeState())
        compose.onNodeWithText("Gleitzeit-Verlauf").performScrollTo().performClick()
        scrollFlexDialogTo("Monatsanalyse")
        flexDialogScroll().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.5f) }        // bring the chips into view
        compose.onAllNodes(isSelectable().and(hasAnyAncestor(hasTestTag("flex_scroll"))))[1].performClick()   // the month before the latest
        compose.waitForIdle()
        compose.onNode(inFlexDialog(monthYearText(MonthRef(2026, 9), "de"))).assertPresent()
        // September has a balance but no daily entries: only the figures of the statement, no "net minus target" of zero entries
        compose.onNode(hasText("keine Tageseinträge mehr", substring = true)).assertPresent()
        assertEquals(0, compose.onAllNodesWithText("Netto minus Soll", substring = true).fetchSemanticsNodes().size)
        screenshot("android-08-monat-ohne-eintraege")
    }

    @Test
    fun theFinalReportShowsFlextimePerQuarter() {
        var s = overtimeState()
        for (d in listOf("2026-07-01", "2026-08-03", "2026-09-01")) s = repository.importWorkEntry(s, d, "08:00", "16:30", 30.0, null).state ?: s
        for (m in 7..9) {
            val (closed, message) = repository.closeSpecificMonth(s, 2026, m)
            s = closed
            println("close 2026-$m: ${message ?: "ok"}, history ${s.monatlicheHistorie.size}")
        }
        assertTrue("no month was closed into the history", s.monatlicheHistorie.isNotEmpty())
        launch(s)
        scrollDashboardTo("Abschlussbericht")
        compose.onNodeWithText("Abschlussbericht").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gleitzeit Quartalsende", substring = true).assertPresent()
        screenshot("android-06-abschlussbericht")
    }

    @Test
    fun englishLabels() {
        launch(overtimeState("en"))
        compose.onNodeWithText("Flextime trend").performScrollTo().performClick()
        scrollFlexDialogTo("Recommendations")
        compose.onNodeWithText("Recommendations").assertPresent()
        scrollFlexDialogTo("Month analysis")
        compose.onNodeWithText("Month analysis").assertPresent()
        screenshot("android-07-english")
    }

    // ---- real statements (optional, local only) ----

    @Test
    fun realStatementsScreens() {
        val dir = File(context.cacheDir, "realpdfs")
        val pdfs = dir.listFiles { f -> f.extension == "pdf" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no real statements on this device", pdfs.isNotEmpty())
        var s = base("November", 2026)
        s = repository.updateSettings(s, 60.0, 8.0).state ?: s
        for (pdf in pdfs) {
            val parsed = PdfParser(context).parsePdf(Uri.fromFile(pdf))
            for (e in parsed.entries) {
                s = repository.importWorkEntry(s, e.datum, e.startTime, e.endTime, e.pauseMinutes, if (e.isRemote) null else 0.0).state ?: s
            }
            s = repository.withFlexBalance(s, parsed.flexBalance)
        }
        // close every month except the newest one, so that the month analysis can show the days of one real month
        val months = repository.getMonthsFromEntries(s)
        val newest = months.maxOf { (year, month) -> year * 12 + month }
        for ((year, month) in months) {
            if (year * 12 + month < newest) s = repository.closeSpecificMonth(s, year, month).first
        }
        assertTrue("flextime series is empty", Flextime.series(s).size >= 10)
        launch(s)
        compose.onNodeWithText("Gleitzeit-Verlauf").performScrollTo().performClick()
        compose.waitForIdle()
        screenshot("android-real-01-verlauf")
        scrollFlexDialogTo("Empfohlen")
        screenshot("android-real-02-empfehlungen")
        scrollFlexDialogTo("Netto minus Soll")
        screenshot("android-real-03-monatsanalyse")
        compose.onNodeWithText("Schließen").performClick()
        compose.waitForIdle()
        scrollDashboardTo("Abschlussbericht")
        compose.onNodeWithText("Abschlussbericht").performClick()
        compose.waitForIdle()
        screenshot("android-real-04-abschlussbericht")
    }
}
