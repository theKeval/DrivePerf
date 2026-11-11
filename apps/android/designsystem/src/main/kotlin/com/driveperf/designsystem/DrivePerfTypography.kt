package com.driveperf.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * M3 Expressive type scale from tokens.json `type.android`. The brand face is Google Sans Flex, which is not bundled
 * yet (font licensing and the variable-font axes are a separate task), so the system sans stays in place until then.
 */
private fun style(size: Int, line: Int, weight: Int, tracking: Float): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.toEm(),
)

private fun Float.toEm(): TextUnit = (this / 16f).em

internal val DrivePerfTypography: Typography = Typography(
    displayLarge = style(57, 64, 400, -0.25f),
    displayMedium = style(45, 52, 400, 0f),
    displaySmall = style(36, 44, 400, 0f),
    headlineLarge = style(32, 40, 400, 0f),
    headlineMedium = style(28, 36, 400, 0f),
    headlineSmall = style(24, 32, 400, 0f),
    titleLarge = style(22, 28, 400, 0f),
    titleMedium = style(16, 24, 500, 0.15f),
    titleSmall = style(14, 20, 500, 0.1f),
    bodyLarge = style(16, 24, 400, 0.5f),
    bodyMedium = style(14, 20, 400, 0.25f),
    bodySmall = style(12, 16, 400, 0.4f),
    labelLarge = style(14, 20, 500, 0.1f),
    labelMedium = style(12, 16, 500, 0.5f),
    labelSmall = style(11, 16, 500, 0.5f),
    displayLargeEmphasized = style(57, 64, 600, -0.25f),
    headlineLargeEmphasized = style(32, 40, 600, 0f),
    headlineMediumEmphasized = style(28, 36, 600, 0f),
    titleLargeEmphasized = style(22, 28, 600, 0f),
    titleMediumEmphasized = style(16, 24, 700, 0.15f),
    labelLargeEmphasized = style(14, 20, 700, 0.1f),
)
