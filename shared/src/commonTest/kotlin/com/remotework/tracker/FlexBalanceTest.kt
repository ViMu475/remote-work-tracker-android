package com.remotework.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Flextime balance of a statement (fictional figures). */
class FlexBalanceTest {

    private val tol = 1e-9

    @Test
    fun germanSummary() {
        val b = SapTimeStatementParser.parseFlexBalance(
            "vom 01.12.2025 bis 31.12.2025\n" +
                "GLZ-Saldo Vorperiode 12,50 Mehrarbeit Kompensation 0,00\n" +
                "GLZ-Saldo akt. Periode 3,25- Mehrarbeit Vergütung 0,00\n" +
                "GLZ-Saldo Gesamt 9,25 Mehrarbeit Grundl./Freiz. 0,00\n"
        )!!
        assertEquals(12.5, b.previous!!, tol)
        assertEquals(-3.25, b.current!!, tol)
        assertEquals(9.25, b.total!!, tol)
        assertEquals(2025, b.year)
        assertEquals(12, b.month)
    }

    @Test
    fun englishSummaryTakesTheFirstMatchOnly() {
        val b = SapTimeStatementParser.parseFlexBalance(
            "Flex. Bal. Prev. Period 10,00 Overtime Compensated 0,00\n" +
                "Flextime Bal. Curr. Period 2,50 Overtime Remunerated 0,00\n" +
                "Flextime Balance Total 12,50 Overtime Basic Pay/Time Off 0,00\n" +
                "Credit Days 0,00 Flex. Bal. Prev. Period 7,77\n"
        )!!
        assertEquals(10.0, b.previous!!, tol)
        assertEquals(2.5, b.current!!, tol)
        assertEquals(12.5, b.total!!, tol)
    }

    @Test
    fun minusSignGluedToTheNextColumn() {
        val b = SapTimeStatementParser.parseFlexBalance("GLZ-Saldo akt. Periode 4,00-Mehrarbeit Vergütung 0,00\n")!!
        assertEquals(-4.0, b.current!!, tol)
        assertNull(b.total)
    }

    @Test
    fun thousandsSeparator() {
        assertEquals(1234.5, SapTimeStatementParser.parseFlexBalance("GLZ-Saldo Gesamt 1.234,50\n")!!.total!!, tol)
    }

    @Test
    fun noSummaryGivesNull() {
        assertNull(SapTimeStatementParser.parseFlexBalance("01 MO Zeitbuchungen : anwesend 08:00 16:30 8,50\n"))
    }
}
