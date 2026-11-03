package com.remotework.tracker.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {

    private val palettes = mapOf("light" to DesignTokens.light, "dark" to DesignTokens.dark)

    @Test
    fun contrastFormulaMatchesKnownWcagValues() {
        assertEquals(21.0, DesignTokens.contrast(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, DesignTokens.contrast(0xFF777777, 0xFF777777), 0.001)
        // well-known reference: #767676 on white is the lightest grey that passes AA (4.54:1)
        assertEquals(4.54, DesignTokens.contrast(0xFF767676, 0xFFFFFFFF), 0.01)
    }

    @Test
    fun everyTextPairReachesAA_4_5() {
        for ((name, p) in palettes) {
            for ((pair, fg, bg) in DesignTokens.textPairs(p)) {
                val ratio = DesignTokens.contrast(fg, bg)
                assertTrue("$name $pair: %.2f:1 < 4.5:1".format(ratio), ratio >= 4.5)
            }
        }
    }

    @Test
    fun everyGraphicOrLargeTextPairReaches3() {
        for ((name, p) in palettes) {
            for ((pair, fg, bg) in DesignTokens.graphicPairs(p)) {
                val ratio = DesignTokens.contrast(fg, bg)
                assertTrue("$name $pair: %.2f:1 < 3:1".format(ratio), ratio >= 3.0)
            }
        }
    }

    @Test
    fun brandColourStaysInTheSameFamilyInLightAndDark() {
        // indigo-600 in light, indigo-300 in dark
        assertEquals(0xFF4F46E5, DesignTokens.light.primary)
        assertEquals(0xFFA5B4FC, DesignTokens.dark.primary)
    }

    @Test
    fun agreedTokenValuesAreUsed() {
        val l = DesignTokens.light
        assertEquals(0xFF047857, l.success)
        assertEquals(0xFFB45309, l.warning)
        assertEquals(0xFFB91C1C, l.error)
        assertEquals(0xFF1D4ED8, l.info)
        assertEquals(0xFFF3F4F6, l.card)
        val d = DesignTokens.dark
        assertEquals(0xFF111827, d.background)
        assertEquals(0xFF1F2937, d.surface)
        assertEquals(0xFF374151, d.card)
        assertEquals(0xFF34D399, d.success)
        assertEquals(0xFFFBBF24, d.warning)
        assertEquals(0xFFFCA5A5, d.error)
    }

    @Test
    fun statusColoursWithWhiteTextInLightModeReachFiveToOne() {
        val l = DesignTokens.light
        for (c in listOf(l.success, l.warning, l.error, l.info, l.primary)) {
            assertTrue(DesignTokens.contrast(0xFFFFFFFF, c) >= 5.0)
        }
    }
}
