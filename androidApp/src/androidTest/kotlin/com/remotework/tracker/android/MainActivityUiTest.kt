package com.remotework.tracker.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.remotework.tracker.LocalStorage
import com.remotework.tracker.MonthData
import com.remotework.tracker.TrackerRepository
import com.remotework.tracker.TrackerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI flows on the emulator. The app state lives in SharedPreferences, so every test starts
 * from a cleared store and (optionally) a seeded state before the activity is launched.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = TrackerRepository(LocalStorage(context))
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clearStore() {
        context.getSharedPreferences("remote_work_tracker", 0).edit().clear().commit()
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    /** October 2025 with one 8 h remote entry on 2025-10-06; deterministic regardless of today's date. */
    private fun seededState(): TrackerState {
        val base = repository.recalculateMonthlyTotals(
            TrackerState(
                aktuellerMonat = "Oktober", aktuellesJahr = 2025,
                currentMonthData = MonthData("Oktober", 2025, 40.0, 176.0, 70.4, 0.0, 0.0),
                currentQuartal = "Q4/2025"
            )
        )
        return repository.addWorkEntry(base, "2025-10-06", "08:00", "16:30", 30.0, null).state!!
    }

    private fun launch(seed: TrackerState? = null) {
        if (seed != null) repository.saveState(seed)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun storedState() = repository.loadState()

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    // --- start screen -----------------------------------------------------------------------------

    @Test
    fun freshStart_showsGermanDashboardWithoutEntries() {
        launch()
        compose.onNodeWithText("Remote Work Tracker").assertIsDisplayed()
        compose.onNodeWithText("Remote-Stunden").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Einträge").assertIsDisplayed()
        compose.onNodeWithText("EN").assertIsDisplayed() // toggle shows the language to switch to
    }

    @Test
    fun seededEntry_isListedWithItsHours() {
        launch(seededState())
        compose.onNodeWithText("2025-10-06").assertIsDisplayed()
        compose.onNodeWithText("Remote: 8.00h | Netto: 8.00h").assertIsDisplayed()
    }

    // --- add entry --------------------------------------------------------------------------------

    @Test
    fun addEntry_savesAndSwitchesToTheEntryMonth() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Arbeitszeit erfassen").assertIsDisplayed()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-06")
        compose.onNodeWithText("Speichern").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("2025-10-06").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Einträge (Oktober 2025)").assertIsDisplayed()
        assertEquals(1, storedState().dailyRecords["2025-10-06"]?.size)
        // the card shows the new month's totals (the automatic month switch itself is not persisted)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("8.0h").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun addEntry_onAHolidayShowsTheErrorAndSavesNothing() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-03")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Arbeitszeitprotokollierung an einem Feiertag ist nicht zulässig.").assertIsDisplayed()
        assertTrue(storedState().dailyRecords.isEmpty())
    }

    @Test
    fun addEntry_overlappingAnExistingEntryIsRejected() {
        launch(seededState())
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-06")
        compose.onNodeWithText("Start (HH:MM)").performTextReplacement("10:00")
        compose.onNodeWithText("Ende (HH:MM)").performTextReplacement("12:00")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Zeitüberlappung", substring = true).assertIsDisplayed()
        assertEquals(1, storedState().dailyRecords["2025-10-06"]?.size)
    }

    // --- delete entry -----------------------------------------------------------------------------

    @Test
    fun deleteEntry_asksForConfirmationAndRemovesTheEntry() {
        launch(seededState())
        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.onNodeWithText("Wirklich löschen?").assertIsDisplayed()
        assertEquals("nothing deleted before confirming", 1, storedState().dailyRecords.size)

        // the dialog title is also "Löschen": pick the button
        compose.onNode(hasText("Löschen") and hasClickAction()).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Noch keine Einträge").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(storedState().dailyRecords.isEmpty())
    }

    @Test
    fun deleteEntry_cancelKeepsTheEntry() {
        launch(seededState())
        compose.onNodeWithContentDescription("Löschen").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(1, storedState().dailyRecords.size)
        compose.onNodeWithText("2025-10-06").assertIsDisplayed()
    }

    // --- clear all with confirmation ---------------------------------------------------------------

    @Test
    fun clearAll_askForConfirmationAndCancelKeepsTheData() {
        launch(seededState())
        scrollTo("Alles löschen")
        compose.onNodeWithText("Alles löschen").performClick()

        compose.onNodeWithText(
            "Wirklich alles löschen? Historie, Einträge und Einstellungen werden zurückgesetzt."
        ).assertIsDisplayed()
        assertEquals("nothing deleted before confirming", 1, storedState().dailyRecords.size)

        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Wirklich alles löschen?", substring = true).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, storedState().dailyRecords.size)
        scrollTo("2025-10-06")
        compose.onNodeWithText("2025-10-06").assertIsDisplayed()
    }

    @Test
    fun clearAll_confirmDeletesEverythingAndResetsSettings() {
        val seed = seededState().copy(language = "de", darkMode = true, individuelleQuoteProzent = 55.0)
        launch(seed)
        scrollTo("Alles löschen")
        compose.onNodeWithText("Alles löschen").performClick()
        compose.onNodeWithText("Löschen").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Noch keine Einträge").fetchSemanticsNodes().isNotEmpty()
        }
        val after = storedState()
        assertTrue(after.dailyRecords.isEmpty())
        assertEquals(40.0, after.individuelleQuoteProzent, 1e-9)
    }

    // --- close month ------------------------------------------------------------------------------

    @Test
    fun closeMonth_movesTheMonthIntoTheHistory() {
        launch(seededState())
        scrollTo("Monat abschließen")
        compose.onNodeWithText("Monat abschließen").performClick()
        // the month is only closed after confirming
        compose.onNodeWithText("Monat wirklich abschließen?", substring = true).assertIsDisplayed()
        assertTrue(storedState().monatlicheHistorie.isEmpty())
        compose.onNodeWithText("Ja").performClick()

        compose.waitUntil(5_000) { storedState().monatlicheHistorie.isNotEmpty() }
        val history = storedState().monatlicheHistorie
        assertEquals(1, history.size)
        assertEquals("Oktober", history[0].monat)
        assertTrue(storedState().dailyRecords.isEmpty())
        scrollTo("Historie")
        compose.onNodeWithText("Historie").assertIsDisplayed()
    }

    @Test
    fun closeMonth_cancelKeepsTheMonthOpen() {
        launch(seededState())
        scrollTo("Monat abschließen")
        compose.onNodeWithText("Monat abschließen").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertTrue(storedState().monatlicheHistorie.isEmpty())
        assertEquals(1, storedState().dailyRecords.size)
    }

    // --- input handling -----------------------------------------------------------------------------

    @Test
    fun addEntry_acceptsDecimalCommaForRemoteHours() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-06")
        compose.onNodeWithText("Remote Stunden (leer = alles)").performTextReplacement("4,5")
        compose.onNodeWithText("Speichern").performClick()

        compose.waitUntil(5_000) { storedState().dailyRecords.isNotEmpty() }
        val e = storedState().dailyRecords["2025-10-06"]!!.single()
        assertEquals(4.5, e.remoteStundenHeute, 1e-9)
        assertEquals(8.0, e.totalStundenHeute, 1e-9)
    }

    @Test
    fun addEntry_invalidNumberShowsAFieldErrorAndKeepsTheDialogOpen() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-06")
        compose.onNodeWithText("Pause (Minuten)").performTextReplacement("NaN")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Ungültige Zahl").assertIsDisplayed()
        compose.onNodeWithText("Arbeitszeit erfassen").assertIsDisplayed()
        assertTrue(storedState().dailyRecords.isEmpty())
    }

    @Test
    fun addEntry_wrongRemoteTextIsRejectedInsteadOfBookingTheWholeDay() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-06")
        compose.onNodeWithText("Remote Stunden (leer = alles)").performTextReplacement("vier")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Ungültige Zahl").assertIsDisplayed()
        assertTrue(storedState().dailyRecords.isEmpty())
    }

    @Test
    fun addEntry_malformedTimeIsReportedInsideTheDialog() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Datum (YYYY-MM-DD)").performTextReplacement("2025-10-06")
        compose.onNodeWithText("Start (HH:MM)").performTextReplacement("99:99")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Ungültige Zeitangabe!").assertIsDisplayed()
        assertTrue(storedState().dailyRecords.isEmpty())
    }

    @Test
    fun addDialog_keepsItsContentAfterRotation() {
        launch()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("Pause (Minuten)").performTextReplacement("45")
        scenario!!.recreate()

        compose.onNodeWithText("Arbeitszeit erfassen").assertIsDisplayed()
        compose.onNodeWithText("45").assertIsDisplayed()
    }

    // --- language, dark mode, settings ---------------------------------------------------------------

    @Test
    fun languageToggle_switchesLabelsAndIsPersisted() {
        launch(seededState())
        compose.onNodeWithText("EN").performClick()
        compose.onNodeWithText("Remote Hours").assertIsDisplayed()
        compose.onNodeWithText("DE").assertIsDisplayed()
        assertEquals("en", storedState().language)

        compose.onNodeWithText("DE").performClick()
        compose.onNodeWithText("Remote-Stunden").assertIsDisplayed()
        assertEquals("de", storedState().language)
    }

    @Test
    fun darkMode_togglePersistsAndStatCardsBecomeDark() {
        launch(seededState())
        fun statCardLuminance(): Double {
            val label = compose.onNodeWithText("Soll").fetchSemanticsNode().boundsInRoot
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            // a pixel left of the centred label lies inside the card padding, i.e. on the card background
            val x = (label.left - 6f).toInt().coerceAtLeast(0)
            val y = label.center.y.toInt()
            val p = bitmap.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }

        compose.waitForIdle()
        assertTrue("light mode card should be light", statCardLuminance() > 0.7)

        compose.onNodeWithText("☽").performClick()
        compose.waitForIdle()
        assertTrue(storedState().darkMode)
        assertTrue("dark mode card must not stay light (unreadable text)", statCardLuminance() < 0.4)

        compose.onNodeWithText("☀").performClick()
        compose.waitForIdle()
        assertFalse(storedState().darkMode)
    }

    @Test
    fun settings_changingTargetHoursRecalculatesMaxRemote() {
        launch(seededState())
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Soll-Stunden/Tag").performTextReplacement("7.5")
        compose.onAllNodesWithText("Speichern").onLast().performClick()

        // October 2025: 22 working days * 7.5 h = 165 h, 40 % -> 66.0 h
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("66.0h").fetchSemanticsNodes().isNotEmpty()
        }
        val saved = storedState()
        assertEquals(7.5, saved.sollStundenProTag, 1e-9)
        assertEquals(66.0, saved.currentMonthData.maxRemoteStunden, 1e-9)
    }

    @Test
    fun settings_acceptsDecimalComma() {
        launch(seededState())
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Soll-Stunden/Tag").performTextReplacement("7,5")
        compose.onAllNodesWithText("Speichern").onLast().performClick()
        compose.waitUntil(5_000) { storedState().sollStundenProTag == 7.5 }
    }

    @Test
    fun settings_nonNumericInputShowsAnErrorAndSavesNothing() {
        launch(seededState())
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Soll-Stunden/Tag").performTextReplacement("NaN")
        compose.onAllNodesWithText("Speichern").onLast().performClick()
        compose.onNodeWithText("Ungültige Zahl").assertIsDisplayed()
        assertEquals(8.0, storedState().sollStundenProTag, 1e-9)
    }

    @Test
    fun settings_rejectedValueKeepsTheOldSettings() {
        launch(seededState())
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Soll-Stunden/Tag").performTextReplacement("15")
        compose.onAllNodesWithText("Speichern").onLast().performClick()

        compose.waitForIdle()
        assertEquals(8.0, storedState().sollStundenProTag, 1e-9)
    }

    // --- export buttons ------------------------------------------------------------------------------

    @Test
    fun exportButtons_areVisibleOnOneLineEach() {
        launch(seededState())
        scrollTo("Wiederherstellen")
        for (label in listOf("CSV", "Backup", "Wiederherstellen")) {
            compose.onAllNodesWithText(label).onFirst().assertIsDisplayed()
        }
        // regression: "Wiederherstellen" used to wrap into two lines (the button's minimum height hides that,
        // so the text layout is checked instead)
        val textNode = compose.onNodeWithText("Wiederherstellen", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        assertTrue("no text layout available", layouts.isNotEmpty())
        assertEquals("label must stay on one line", 1, layouts[0].lineCount)
    }
}
