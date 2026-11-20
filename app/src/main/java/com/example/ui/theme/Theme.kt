package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BillRailLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = PaperWhite,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = EmeraldTrust,
    onSecondary = PaperWhite,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = AmberPending,
    onTertiary = PaperWhite,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = CrimsonError,
    onError = PaperWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer,
    background = PaperWhite,
    onBackground = InkPrimary,
    surface = PaperWhite,
    onSurface = InkPrimary,
    surfaceVariant = SlateBand,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLowest = PaperWhite,
    surfaceContainerLow = SlateBand,
    surfaceContainer = SlateBand,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder
)

private val BillRailDarkColorScheme = darkColorScheme(
    primary = DarkCobaltPrimary,
    onPrimary = CobaltDark,
    primaryContainer = DarkCobaltContainer,
    onPrimaryContainer = DarkInk,
    secondary = DarkEmerald,
    onSecondary = OnEmeraldContainer,
    secondaryContainer = DarkEmeraldContainer,
    onSecondaryContainer = DarkEmerald,
    tertiary = DarkAmber,
    onTertiary = OnAmberContainer,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkAmber,
    error = DarkCrimson,
    onError = OnCrimsonContainer,
    errorContainer = DarkCrimsonContainer,
    onErrorContainer = DarkCrimson,
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkHairline,
    outlineVariant = DarkHairline
)

val BillRailShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun BillRailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Use intentional BillRail utilitarian fintech palette consistently across devices
    val colorScheme = if (darkTheme) BillRailDarkColorScheme else BillRailLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BillRailShapes,
        content = content
    )
}
