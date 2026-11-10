package com.driveperf.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalDataColors = staticCompositionLocalOf { DarkDataColors }
private val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

/** Entry points for the colours Material 3 has no role for. Use these instead of hard-coding hex values. */
public object DrivePerfTheme {
    /** Chart, replay and event colours. Fixed per theme: they never follow dynamic colour (tokens.json meta.rules). */
    public val dataColors: DpDataColors
        @Composable @ReadOnlyComposable get() = LocalDataColors.current

    /** Warning, record (Sodium gold), live and similar semantic colours. */
    public val extendedColors: DpExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}

/**
 * M3 Expressive "Night Drive" theme built from `docs/design/system/tokens.json`.
 *
 * Dynamic colour is optional and off by default: the Glide mint accent is the brand. Data colours stay fixed either way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun DrivePerfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DrivePerfDarkColorScheme
        else -> DrivePerfLightColorScheme
    }
    CompositionLocalProvider(
        LocalDataColors provides if (darkTheme) DarkDataColors else LightDataColors,
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = DrivePerfShapes,
            typography = DrivePerfTypography,
            content = content,
        )
    }
}

/** Convenience for previews and tests that want the theme's colour scheme without a Composable context. */
@Composable
@ReadOnlyComposable
internal fun currentColorScheme() = MaterialTheme.colorScheme
