package com.remotework.tracker.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** Semantic colours that Material 3 has no role for (success / warning / info). */
@Immutable
data class StatusColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val info: Color, val onInfo: Color
)

val LocalStatusColors = staticCompositionLocalOf {
    DesignTokens.light.toStatusColors()
}

/** Convenience accessor: MaterialTheme.status.success etc. */
val MaterialTheme.status: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current

private fun Long.c() = Color(this)

internal fun Palette.toStatusColors() = StatusColors(
    success.c(), onSuccess.c(), successContainer.c(), onSuccessContainer.c(),
    warning.c(), onWarning.c(), warningContainer.c(), onWarningContainer.c(),
    info.c(), onInfo.c()
)

private fun Palette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary.c(), onPrimary = onPrimary.c(),
        primaryContainer = primaryContainer.c(), onPrimaryContainer = onPrimaryContainer.c(),
        secondary = success.c(), onSecondary = onSuccess.c(),
        secondaryContainer = successContainer.c(), onSecondaryContainer = onSuccessContainer.c(),
        tertiary = info.c(), onTertiary = onInfo.c(),
        background = background.c(), onBackground = onBackground.c(),
        surface = surface.c(), onSurface = onSurface.c(),
        surfaceVariant = card.c(), onSurfaceVariant = onCardMuted.c(),
        surfaceTint = primary.c(),
        inverseSurface = onSurface.c(), inverseOnSurface = surface.c(), inversePrimary = primary.c(),
        error = error.c(), onError = onError.c(),
        errorContainer = errorContainer.c(), onErrorContainer = onErrorContainer.c(),
        outline = outline.c(), outlineVariant = outlineVariant.c(),
        scrim = Color.Black
    )
}

/** Tabular figures so that columns of numbers line up. */
private fun TextStyle.tnum() = copy(fontFeatureSettings = "tnum")

private fun appTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.tnum(), displayMedium = t.displayMedium.tnum(), displaySmall = t.displaySmall.tnum(),
        headlineLarge = t.headlineLarge.tnum(), headlineMedium = t.headlineMedium.tnum(), headlineSmall = t.headlineSmall.tnum(),
        titleLarge = t.titleLarge.tnum(), titleMedium = t.titleMedium.tnum(), titleSmall = t.titleSmall.tnum(),
        bodyLarge = t.bodyLarge.tnum(), bodyMedium = t.bodyMedium.tnum(), bodySmall = t.bodySmall.tnum(),
        labelLarge = t.labelLarge.tnum(), labelMedium = t.labelMedium.tnum(), labelSmall = t.labelSmall.tnum()
    )
}

/**
 * App theme. [darkMode] is the stored switch (TrackerState.darkMode); the format is unchanged, so a
 * stored "false" cannot be told apart from "never chosen" and the system setting is not consulted
 * ([followSystem] exists for that and defaults to false).
 */
@Composable
fun RemoteWorkTrackerTheme(
    darkMode: Boolean = false,
    followSystem: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = if (followSystem) isSystemInDarkTheme() else darkMode
    val palette = if (dark) DesignTokens.dark else DesignTokens.light
    CompositionLocalProvider(LocalStatusColors provides palette.toStatusColors()) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(dark),
            typography = appTypography(),
            content = content
        )
    }
}

/** Cards of every kind (entries, statistics, history, export) use the same surface role. */
@Composable
fun appCardColors(container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurface) =
    CardDefaults.cardColors(containerColor = container, contentColor = content)
