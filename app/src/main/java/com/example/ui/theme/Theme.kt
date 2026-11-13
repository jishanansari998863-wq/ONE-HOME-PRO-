package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.localization.AppLanguage

private val LightColors = lightColorScheme(
    primary = QatarNavy,
    onPrimary = PureWhite,
    primaryContainer = QatarNavyContainer,
    onPrimaryContainer = QatarNavyDark,
    secondary = ArchitecturalGold,
    onSecondary = PureWhite,
    secondaryContainer = ArchitecturalGoldContainer,
    onSecondaryContainer = ArchitecturalGoldDark,
    tertiary = SlateTeal,
    onTertiary = PureWhite,
    tertiaryContainer = SlateTealContainer,
    onTertiaryContainer = ColorTokens.TealDark,
    background = IvoryBackground,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = OutlineSubtle,
    error = CrimsonError,
    onError = PureWhite,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonError
)

private val DarkColors = darkColorScheme(
    primary = ColorTokens.NavyBright,
    onPrimary = QatarNavyDark,
    primaryContainer = QatarNavy,
    onPrimaryContainer = QatarNavyContainer,
    secondary = ArchitecturalGold,
    onSecondary = ObsidianBackground,
    secondaryContainer = ColorTokens.GoldDarkContainer,
    onSecondaryContainer = ArchitecturalGoldContainer,
    tertiary = ColorTokens.TealBright,
    onTertiary = ObsidianBackground,
    tertiaryContainer = SlateTeal,
    onTertiaryContainer = SlateTealContainer,
    background = ObsidianBackground,
    onBackground = LightInk,
    surface = ObsidianSurface,
    onSurface = LightInk,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = LightInkMuted,
    outline = ObsidianOutline,
    error = ColorTokens.CrimsonBright,
    onError = ObsidianBackground,
    errorContainer = ColorTokens.CrimsonDarkContainer,
    onErrorContainer = CrimsonContainer
)

private object ColorTokens {
    val TealDark = androidx.compose.ui.graphics.Color(0xFF11464D)
    val NavyBright = androidx.compose.ui.graphics.Color(0xFF7FB3EA)
    val GoldDarkContainer = androidx.compose.ui.graphics.Color(0xFF4F3B12)
    val TealBright = androidx.compose.ui.graphics.Color(0xFF72CBD6)
    val CrimsonBright = androidx.compose.ui.graphics.Color(0xFFFCA5A5)
    val CrimsonDarkContainer = androidx.compose.ui.graphics.Color(0xFF5C1515)
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun OneHomeProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: AppLanguage = AppLanguage.EN,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val layoutDirection = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildAppTypography(isArabic = language == AppLanguage.AR)

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
