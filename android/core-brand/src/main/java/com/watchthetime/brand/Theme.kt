package com.watchthetime.brand

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object WttFonts {
    val Barlow = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_semibold, FontWeight.Normal),
        Font(R.font.barlow_condensed_semibold, FontWeight.Medium),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
        Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    )

    /** Seven-segment digits. '!' renders as a blank of digit width. */
    val Segment = FontFamily(Font(R.font.dseg7_classic_bold, FontWeight.Bold))
}

private fun barlow(size: Int, weight: FontWeight = FontWeight.SemiBold, spacing: Double = 0.0) = TextStyle(
    fontFamily = WttFonts.Barlow,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.sp,
)

val WttTypography = Typography(
    displayLarge = barlow(56, FontWeight.ExtraBold),
    displayMedium = barlow(44, FontWeight.ExtraBold),
    displaySmall = barlow(36, FontWeight.ExtraBold),
    headlineLarge = barlow(32, FontWeight.ExtraBold, 0.5),
    headlineMedium = barlow(28, FontWeight.Bold, 0.5),
    headlineSmall = barlow(24, FontWeight.Bold, 0.5),
    titleLarge = barlow(22, FontWeight.Bold, 1.0),
    titleMedium = barlow(18, FontWeight.Bold, 1.0),
    titleSmall = barlow(16, FontWeight.Bold, 1.0),
    bodyLarge = barlow(18),
    bodyMedium = barlow(16),
    bodySmall = barlow(14),
    labelLarge = barlow(16, FontWeight.Bold, 1.5),
    labelMedium = barlow(14, FontWeight.Bold, 1.5),
    labelSmall = barlow(12, FontWeight.Bold, 1.5),
)

private val Square = RoundedCornerShape(0.dp)

private val WttColors = darkColorScheme(
    primary = Wtt.Amber,
    onPrimary = Wtt.Black,
    primaryContainer = Wtt.Amber,
    onPrimaryContainer = Wtt.Black,
    secondary = Wtt.OffWhite,
    onSecondary = Wtt.Black,
    secondaryContainer = Wtt.PanelHigh,
    onSecondaryContainer = Wtt.OffWhite,
    tertiary = Wtt.Amber,
    onTertiary = Wtt.Black,
    background = Wtt.Black,
    onBackground = Wtt.OffWhite,
    surface = Wtt.Black,
    onSurface = Wtt.OffWhite,
    surfaceVariant = Wtt.Panel,
    onSurfaceVariant = Wtt.Muted,
    surfaceContainerLowest = Wtt.Black,
    surfaceContainerLow = Wtt.Panel,
    surfaceContainer = Wtt.Panel,
    surfaceContainerHigh = Wtt.PanelHigh,
    surfaceContainerHighest = Wtt.PanelHigh,
    surfaceTint = Wtt.Black,
    error = Wtt.Red,
    onError = Wtt.OffWhite,
    outline = Wtt.Line,
    outlineVariant = Wtt.Line,
    inverseSurface = Wtt.OffWhite,
    inverseOnSurface = Wtt.Black,
    scrim = Wtt.Black,
)

@Composable
fun WttTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WttColors,
        typography = WttTypography,
        shapes = Shapes(Square, Square, Square, Square, Square),
        content = content,
    )
}
