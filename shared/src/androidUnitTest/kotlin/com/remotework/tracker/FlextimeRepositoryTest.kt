package com.remotework.tracker

import android.content.Context
import android.content.SharedPreferences
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Flextime per month through the repository: statement import, backup, restore, clearing (fictional figures). */
class FlextimeRepositoryTest {

    private val store = mutableMapOf<String, String>()

    private fun newRepository(): TrackerRepository {
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(any(), any())).thenAnswer { store[it.arguments[0] as String] = it.arguments[1] as String; editor }
        `when`(editor.remove(any())).thenAnswer { store.remove(it.arguments[0] as String); editor }
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString(any(), any())).thenAnswer { store[it.arguments[0] as String] }
        `when`(prefs.edit()).thenReturn(editor)
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(any(), anyInt())).thenReturn(prefs)
        return TrackerRepository(LocalStorage(context))
    }

    private val repo = newRepository()

    private fun state() = repo.recalculateMonthlyTotals(
        TrackerState(
            aktuellerMonat = "Oktober", aktuellesJahr = 2025,
            currentMonthData = MonthData("Oktober", 2025, 40.0, 0.0, 0.0, 0.0, 0.0),
            currentQuartal = "Q4/2025"
        )
    )

    @Test
    fun importingAStatementKeepsTheLatestBalanceAndTheFiguresOfItsMonth() {
        val s = repo.withFlexBalance(state(), FlexBalance(12.5, 3.25, 15.75, 2026, 5))
        assertEquals(15.75, s.gleitzeitSaldo!!.total!!, 1e-9)
        assertEquals(listOf(FlexEntry(2026, 5, 12.5, 3.25, 15.75)), s.gleitzeitVerlauf)
    }

    @Test
    fun everyStatementMonthKeepsItsOwnPointAndTheSeriesStaysSorted() {
        var s = repo.withFlexBalance(state(), FlexBalance(1.0, 2.0, 3.0, 2025, 11))
        s = repo.withFlexBalance(s, FlexBalance(-1.0, 1.0, 0.0, 2025, 10))
        assertEquals(listOf(10, 11), s.gleitzeitVerlauf.map { it.monat })
        assertEquals(0.0, s.gleitzeitSaldo!!.total!!, 1e-9)      // the balance of the last imported statement
    }

    @Test
    fun aBalanceWithoutAMonthOnlyUpdatesTheLatestBalance() {
        val s = repo.withFlexBalance(state(), FlexBalance(total = 5.0))
        assertEquals(5.0, s.gleitzeitSaldo!!.total!!, 1e-9)
        assertTrue(s.gleitzeitVerlauf.isEmpty())
    }

    @Test
    fun implausibleFiguresAreNotStored() {
        val s = repo.withFlexBalance(state(), FlexBalance(Double.NaN, 1e9, Double.POSITIVE_INFINITY, 2025, 10))
        assertEquals(state(), s)
    }

    @Test
    fun importingTheSameMonthAgainReplacesIt() {
        var s = repo.withFlexBalance(state(), FlexBalance(total = 3.0, year = 2025, month = 11))
        s = repo.withFlexBalance(s, FlexBalance(total = 5.0, year = 2025, month = 11))
        assertEquals(listOf(5.0), s.gleitzeitVerlauf.map { it.gesamt })
    }

    @Test
    fun theSeriesSurvivesBackupAndRestore() {
        var s = repo.withFlexBalance(state(), FlexBalance(1.0, 2.0, 3.0, 2025, 10))
        s = repo.withFlexBalance(s, FlexBalance(3.0, 4.0, 7.0, 2025, 11))
        val restored = repo.restoreState(repo.serializeState(s))
        assertEquals(s.gleitzeitVerlauf, restored.state!!.gleitzeitVerlauf)
    }

    @Test
    fun aBackupWithoutTheSeriesRestoresWithAnEmptySeries() {
        val json = repo.serializeState(state()).replace(Regex(""","gleitzeitVerlauf":\[[^\]]*\]"""), "")
        assertTrue("gleitzeitVerlauf" !in json)
        val restored = repo.restoreState(json)
        assertNotNull(restored.state, restored.error)
        assertTrue(restored.state!!.gleitzeitVerlauf.isEmpty())
    }

    @Test
    fun restoreDropsDamagedEntries() {
        val damaged = state().copy(gleitzeitVerlauf = listOf(FlexEntry(2025, 11, null, null, 5.0), FlexEntry(2025, 13, null, null, 1.0),
            FlexEntry(1800, 5, null, null, 1.0), FlexEntry(2025, 12, null, null, 1e9), FlexEntry(2025, 9, null, null, 2.0)))
        val restored = repo.restoreState(repo.serializeState(damaged)).state!!
        assertEquals(listOf(9, 11), restored.gleitzeitVerlauf.map { it.monat })
    }

    @Test
    fun clearingTheHistoryAlsoClearsTheSeriesAndTheLatestBalance() {
        val s = repo.withFlexBalance(state(), FlexBalance(1.0, 2.0, 3.0, 2025, 10))
        val cleared = repo.clearAllHistory(s)
        assertTrue(cleared.gleitzeitVerlauf.isEmpty())
        assertEquals(null, cleared.gleitzeitSaldo)
    }

    @Test
    fun theStoredStateKeepsTheSeriesAcrossALoad() {
        val s = repo.withFlexBalance(state(), FlexBalance(1.0, 2.0, 3.0, 2025, 10))
        repo.saveState(s)
        assertEquals(s.gleitzeitVerlauf, repo.loadState().gleitzeitVerlauf)
    }
}
