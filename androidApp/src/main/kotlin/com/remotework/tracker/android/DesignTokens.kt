package com.remotework.tracker.android

/**
 * Colour tokens as plain ARGB longs (no Compose types) so that the WCAG contrast of every
 * text/surface pair can be checked in a JVM unit test (see DesignTokensTest).
 */
internal data class Palette(
    val primary: Long, val onPrimary: Long,
    val primaryContainer: Long, val onPrimaryContainer: Long,
    val background: Long, val onBackground: Long,
    val surface: Long, val onSurface: Long,
    /** Card surface (entries, statistics, history). */
    val card: Long, val onCard: Long, val onCardMuted: Long,
    val onSurfaceMuted: Long,
    val outline: Long, val outlineVariant: Long,
    val error: Long, val onError: Long,
    val errorContainer: Long, val onErrorContainer: Long,
    val success: Long, val onSuccess: Long,
    val successContainer: Long, val onSuccessContainer: Long,
    val warning: Long, val onWarning: Long,
    val warningContainer: Long, val onWarningContainer: Long,
    val info: Long, val onInfo: Long
)

internal object DesignTokens {
    val light = Palette(
        primary = 0xFF4F46E5, onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFE0E7FF, onPrimaryContainer = 0xFF312E81,
        background = 0xFFFFFFFF, onBackground = 0xFF111827,
        surface = 0xFFFFFFFF, onSurface = 0xFF111827,
        card = 0xFFF3F4F6, onCard = 0xFF111827, onCardMuted = 0xFF4B5563,
        onSurfaceMuted = 0xFF4B5563,
        outline = 0xFF6B7280, outlineVariant = 0xFFD1D5DB,
        error = 0xFFB91C1C, onError = 0xFFFFFFFF,
        errorContainer = 0xFFFEE2E2, onErrorContainer = 0xFF7F1D1D,
        success = 0xFF047857, onSuccess = 0xFFFFFFFF,
        successContainer = 0xFFDCFCE7, onSuccessContainer = 0xFF14532D,
        warning = 0xFFB45309, onWarning = 0xFFFFFFFF,
        warningContainer = 0xFFFEF9C3, onWarningContainer = 0xFF713F12,
        info = 0xFF1D4ED8, onInfo = 0xFFFFFFFF
    )

    val dark = Palette(
        primary = 0xFFA5B4FC, onPrimary = 0xFF1E1B4B,
        primaryContainer = 0xFF312E81, onPrimaryContainer = 0xFFE0E7FF,
        background = 0xFF111827, onBackground = 0xFFE6E9EF,
        surface = 0xFF1F2937, onSurface = 0xFFE6E9EF,
        card = 0xFF374151, onCard = 0xFFE6E9EF, onCardMuted = 0xFFD1D5DB,
        onSurfaceMuted = 0xFF9CA3AF,
        outline = 0xFF9CA3AF, outlineVariant = 0xFF4B5563,
        error = 0xFFFCA5A5, onError = 0xFF450A0A,
        errorContainer = 0xFF4A1D1D, onErrorContainer = 0xFFFECACA,
        success = 0xFF34D399, onSuccess = 0xFF052E1C,
        successContainer = 0xFF14391F, onSuccessContainer = 0xFF86EFAC,
        warning = 0xFFFBBF24, onWarning = 0xFF1F1300,
        warningContainer = 0xFF3D3410, onWarningContainer = 0xFFFDE68A,
        info = 0xFF93C5FD, onInfo = 0xFF0B1B4D
    )

    /** Pairs (name, foreground, background) that carry normal-size text: need >= 4.5:1. */
    fun textPairs(p: Palette): List<Triple<String, Long, Long>> = listOf(
        Triple("onBackground/background", p.onBackground, p.background),
        Triple("onSurface/surface", p.onSurface, p.surface),
        Triple("onSurfaceMuted/surface", p.onSurfaceMuted, p.surface),
        Triple("onSurfaceMuted/background", p.onSurfaceMuted, p.background),
        Triple("onCard/card", p.onCard, p.card),
        Triple("onCardMuted/card", p.onCardMuted, p.card),
        Triple("onPrimary/primary", p.onPrimary, p.primary),
        Triple("primary/background", p.primary, p.background),
        Triple("onPrimaryContainer/primaryContainer", p.onPrimaryContainer, p.primaryContainer),
        Triple("onError/error", p.onError, p.error),
        Triple("error/background", p.error, p.background),
        Triple("onErrorContainer/errorContainer", p.onErrorContainer, p.errorContainer),
        Triple("onSuccess/success", p.onSuccess, p.success),
        Triple("onSuccessContainer/successContainer", p.onSuccessContainer, p.successContainer),
        Triple("onWarning/warning", p.onWarning, p.warning),
        Triple("onWarningContainer/warningContainer", p.onWarningContainer, p.warningContainer),
        Triple("onInfo/info", p.onInfo, p.info),
        Triple("error/card", p.error, p.card),
        Triple("success/surface", p.success, p.surface),
        Triple("warning/surface", p.warning, p.surface),
        Triple("error/surface", p.error, p.surface),
        Triple("primary/surface", p.primary, p.surface)
    )

    /** Graphics and large text (progress bar, outlines, icons): need >= 3:1. */
    fun graphicPairs(p: Palette): List<Triple<String, Long, Long>> = listOf(
        Triple("success fill/track", p.success, p.outlineVariant),
        Triple("warning fill/track", p.warning, p.outlineVariant),
        Triple("error fill/track", p.error, p.outlineVariant),
        Triple("outline/background", p.outline, p.background),
        Triple("primary icon/card", p.primary, p.card),
        Triple("error icon/card", p.error, p.card),
        Triple("info icon/card", p.info, p.card)
    )

    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG 2.x contrast ratio, 1.0 .. 21.0. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
