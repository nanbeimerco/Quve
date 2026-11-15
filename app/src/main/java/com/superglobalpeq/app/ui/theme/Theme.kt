package com.superglobalpeq.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.data.QuveThemeMode
import com.superglobalpeq.app.data.QuveThemePreset

/**
 * Ambient composition-local indicating whether Quve's active UI surface is dark.
 * Liquid Glass palettes read this instead of OS `isSystemInDarkTheme()` so user-selected
 * Dark AMOLED / Light overrides always keep legible contrast.
 */
val LocalQuveDarkTheme = staticCompositionLocalOf { true }

/**
 * Provides the active Material 3 Expressive [ColorScheme] before any [GlassModalBottomSheet]
 * monochrome tint override, so Expressive controls inside glass sheets retain the selected
 * accent palette.
 */
val LocalQuveExpressiveColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

// ═══════════════════════════════════════════════════════════════════════════
//  1. Quve Default Palette: Vermilion Orange × Pitch Black
// ═══════════════════════════════════════════════════════════════════════════

private val QuveVermilionDark = darkColorScheme(
    primary = Color(0xFFFF5935),
    onPrimary = Color(0xFF1A0400),
    primaryContainer = Color(0xFF5D1906),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFFF8F6D),
    onSecondary = Color(0xFF3E1004),
    secondaryContainer = Color(0xFF361913),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF002114),
    tertiaryContainer = Color(0xFF0D3827),
    onTertiaryContainer = Color(0xFF9DF6CB),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF09090B),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF1F1B1D),
    onSurfaceVariant = Color(0xFFB5B0B3),
    surfaceContainerLowest = Color(0xFF050507),
    surfaceContainerLow = Color(0xFF111114),
    surfaceContainer = Color(0xFF17171B),
    surfaceContainerHigh = Color(0xFF202026),
    surfaceContainerHighest = Color(0xFF2B2B33),
    outline = Color(0xFF565156),
    outlineVariant = Color(0xFF2D2B30),
    error = Color(0xFFFF5C5C),
    onError = Color(0xFF370003),
    errorContainer = Color(0xFF5C0A11),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val QuveVermilionLight = lightColorScheme(
    primary = Color(0xFFD93E1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3B0900),
    secondary = Color(0xFF9E4229),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDE8),
    onSecondaryContainer = Color(0xFF3B0900),
    tertiary = Color(0xFF0E7A55),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F3D9),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFFAF7F6),
    onBackground = Color(0xFF1C1817),
    surface = Color(0xFFFAF7F6),
    onSurface = Color(0xFF1C1817),
    surfaceVariant = Color(0xFFF0E4E1),
    onSurfaceVariant = Color(0xFF544643),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F0EE),
    surfaceContainer = Color(0xFFEFE9E7),
    surfaceContainerHigh = Color(0xFFE7DFDD),
    surfaceContainerHighest = Color(0xFFDFD5D3),
    outline = Color(0xFF87736F),
    outlineVariant = Color(0xFFD8C6C2),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

// ═══════════════════════════════════════════════════════════════════════════
//  2-6. Additional Quve Expressive Palettes
// ═══════════════════════════════════════════════════════════════════════════

private fun expressiveDarkFromSeed(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    tertiary: Color,
    bgTint: Color = Color(0xFF0D0E12),
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onPrimary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    tertiary = tertiary,
    onTertiary = Color(0xFF091B14),
    tertiaryContainer = Color(0xFF15382B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = bgTint,
    onBackground = Color(0xFFF2F2F7),
    surface = bgTint,
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1D1F27),
    onSurfaceVariant = Color(0xFFACAFB9),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF121318),
    surfaceContainer = Color(0xFF181920),
    surfaceContainerHigh = Color(0xFF21232C),
    surfaceContainerHighest = Color(0xFF2B2E38),
    outline = Color(0xFF515562),
    outlineVariant = Color(0xFF2C2F39),
)

private fun expressiveLightFromSeed(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    tertiary: Color,
    bg: Color = Color(0xFFF7F8FC),
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDF4DF),
    onTertiaryContainer = Color(0xFF002116),
    background = bg,
    onBackground = Color(0xFF16181D),
    surface = bg,
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFE4E7F0),
    onSurfaceVariant = Color(0xFF474B57),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFE9ECF4),
    surfaceContainerHigh = Color(0xFFE0E4EE),
    surfaceContainerHighest = Color(0xFFD6DBE7),
    outline = Color(0xFF767B89),
    outlineVariant = Color(0xFFC5C9D6),
)

private val MidnightStudioDark = expressiveDarkFromSeed(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF002956),
    primaryContainer = Color(0xFF0E3E75),
    onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = Color(0xFF38BDF8),
    secondaryContainer = Color(0xFF13334C),
    tertiary = Color(0xFF34D399),
    bgTint = Color(0xFF0B0E14),
)

private val MidnightStudioLight = expressiveLightFromSeed(
    primary = Color(0xFF1D63D8),
    primaryContainer = Color(0xFFD7E5FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF0284C7),
    secondaryContainer = Color(0xFFD6EFFF),
    tertiary = Color(0xFF0D8F63),
)

private val SakuraNeonDark = expressiveDarkFromSeed(
    primary = Color(0xFFFF6B9D),
    onPrimary = Color(0xFF47001B),
    primaryContainer = Color(0xFF6E0A32),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFF472B6),
    secondaryContainer = Color(0xFF3E182A),
    tertiary = Color(0xFF67E8F9),
    bgTint = Color(0xFF100B0F),
)

private val SakuraNeonLight = expressiveLightFromSeed(
    primary = Color(0xFFD12667),
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3F0018),
    secondary = Color(0xFFAE3D6A),
    secondaryContainer = Color(0xFFFFE5ED),
    tertiary = Color(0xFF0891B2),
    bg = Color(0xFFFCF7F9),
)

private val CyberAmberDark = expressiveDarkFromSeed(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF3A2500),
    primaryContainer = Color(0xFF5A3C00),
    onPrimaryContainer = Color(0xFFFFE8AD),
    secondary = Color(0xFFF59E0B),
    secondaryContainer = Color(0xFF38290C),
    tertiary = Color(0xFF38BDF8),
    bgTint = Color(0xFF0E0D0A),
)

private val CyberAmberLight = expressiveLightFromSeed(
    primary = Color(0xFFB77400),
    primaryContainer = Color(0xFFFFE6AC),
    onPrimaryContainer = Color(0xFF341E00),
    secondary = Color(0xFF925F11),
    secondaryContainer = Color(0xFFFFEFC7),
    tertiary = Color(0xFF0284C7),
    bg = Color(0xFFFCF9F2),
)

private val NordicSlateDark = expressiveDarkFromSeed(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF003324),
    primaryContainer = Color(0xFF0B4F3B),
    onPrimaryContainer = Color(0xFFB8F8DD),
    secondary = Color(0xFF94A3B8),
    secondaryContainer = Color(0xFF1E2936),
    tertiary = Color(0xFF93C5FD),
    bgTint = Color(0xFF0B1012),
)

private val NordicSlateLight = expressiveLightFromSeed(
    primary = Color(0xFF0F7659),
    primaryContainer = Color(0xFFBDF5DF),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF475569),
    secondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF2563EB),
    bg = Color(0xFFF5F8F7),
)

private val ElectricVioletDark = expressiveDarkFromSeed(
    primary = Color(0xFFC0B6FF),
    onPrimary = Color(0xFF291D6E),
    primaryContainer = Color(0xFF41358C),
    onPrimaryContainer = Color(0xFFE7E2FF),
    secondary = Color(0xFFA78BFA),
    secondaryContainer = Color(0xFF29214A),
    tertiary = Color(0xFF34D399),
    bgTint = Color(0xFF0D0B14),
)

private val ElectricVioletLight = expressiveLightFromSeed(
    primary = Color(0xFF5B4BDB),
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF190C63),
    secondary = Color(0xFF7C3AED),
    secondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFF0D8F63),
    bg = Color(0xFFF8F7FC),
)

// ═══════════════════════════════════════════════════════════════════════════
//  7-14. RootlessJamesDSP Inspired Palettes (Exact Color Tokens)
//  Adapted from ThePBone/RootlessJamesDSP (GPL-3.0), modified for Quve (2026-11-15).
//  Upstream palettes in RootlessJamesDSP were adapted from Tachiyomi / Mihon (Apache-2.0).
// ═══════════════════════════════════════════════════════════════════════════

private val TealTurquoiseDark = darkColorScheme(
    primary = Color(0xFF40E0D0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF40E0D0),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF40E0D0),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF18544E),
    onSecondaryContainer = Color(0xFF40E0D0),
    tertiary = Color(0xFFBF1F2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF200508),
    onTertiaryContainer = Color(0xFFBF1F2F),
    background = Color(0xFF202125),
    onBackground = Color(0xFFDFDEDA),
    surface = Color(0xFF202125),
    onSurface = Color(0xFFDFDEDA),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFDFDEDA),
    outline = Color(0xFF899391),
    surfaceContainerLowest = Color(0xFF141518),
    surfaceContainerLow = Color(0xFF1B1C20),
    surfaceContainer = Color(0xFF222429),
    surfaceContainerHigh = Color(0xFF2A2D32),
    surfaceContainerHighest = Color(0xFF34383E),
)

private val TealTurquoiseLight = lightColorScheme(
    primary = Color(0xFF008080),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF008080),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF008080),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFDFDF),
    onSecondaryContainer = Color(0xFF008080),
    tertiary = Color(0xFFFF7F7F),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2A1616),
    onTertiaryContainer = Color(0xFFFF7F7F),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF050505),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF050505),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF050505),
    outline = Color(0xFF6F7977),
)

private val GreenAppleDark = darkColorScheme(
    primary = Color(0xFF7ADB8F),
    onPrimary = Color(0xFF003915),
    primaryContainer = Color(0xFF005322),
    onPrimaryContainer = Color(0xFF96F8A9),
    secondary = Color(0xFF7ADB8F),
    onSecondary = Color(0xFF003915),
    secondaryContainer = Color(0xFF005322),
    onSecondaryContainer = Color(0xFF96F8A9),
    tertiary = Color(0xFFFFB3AA),
    onTertiary = Color(0xFF680006),
    tertiaryContainer = Color(0xFF93000D),
    onTertiaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE1E3DD),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C8BE),
    outline = Color(0xFF8B9389),
    surfaceContainerLow = Color(0xFF1E211D),
    surfaceContainer = Color(0xFF232722),
    surfaceContainerHigh = Color(0xFF2A2E29),
    surfaceContainerHighest = Color(0xFF333832),
)

private val GreenAppleLight = lightColorScheme(
    primary = Color(0xFF006D2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF96F8A9),
    onPrimaryContainer = Color(0xFF002109),
    secondary = Color(0xFF006D2F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF96F8A9),
    onSecondaryContainer = Color(0xFF002109),
    tertiary = Color(0xFFB91D22),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD5),
    onTertiaryContainer = Color(0xFF410003),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717970),
)

private val HoneyDark = darkColorScheme(
    primary = Color(0xFFFFB869),
    onPrimary = Color(0xFF482900),
    primaryContainer = Color(0xFF673D00),
    onPrimaryContainer = Color(0xFFFFDCBB),
    secondary = Color(0xFFFFB68B),
    onSecondary = Color(0xFF522300),
    secondaryContainer = Color(0xFF753400),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFFFFADE2),
    onTertiary = Color(0xFF581149),
    tertiaryContainer = Color(0xFF742A61),
    onTertiaryContainer = Color(0xFFFFD8EE),
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFEBE1D9),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFEBE1D9),
    surfaceVariant = Color(0xFF50453A),
    onSurfaceVariant = Color(0xFFD4C4B5),
    outline = Color(0xFF9D8E81),
    surfaceContainerLow = Color(0xFF231F19),
    surfaceContainer = Color(0xFF28231D),
    surfaceContainerHigh = Color(0xFF302A23),
    surfaceContainerHighest = Color(0xFF39322A),
)

private val HoneyLight = lightColorScheme(
    primary = Color(0xFF885200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFC996),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF994700),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF321300),
    tertiary = Color(0xFF90427A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF3B002F),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF1DFD0),
    onSurfaceVariant = Color(0xFF50453A),
    outline = Color(0xFF827568),
)

private val StrawberryDark = darkColorScheme(
    primary = Color(0xFFFFB2B9),
    onPrimary = Color(0xFF67001B),
    primaryContainer = Color(0xFF91002A),
    onPrimaryContainer = Color(0xFFFFDADD),
    secondary = Color(0xFFFFB2B9),
    onSecondary = Color(0xFF67001B),
    secondaryContainer = Color(0xFF91002A),
    onSecondaryContainer = Color(0xFFFFDADD),
    tertiary = Color(0xFFE8C08E),
    onTertiary = Color(0xFF432C06),
    tertiaryContainer = Color(0xFF5D421B),
    onTertiaryContainer = Color(0xFFFFDDB1),
    background = Color(0xFF201A1A),
    onBackground = Color(0xFFECDFDF),
    surface = Color(0xFF201A1A),
    onSurface = Color(0xFFECDFDF),
    surfaceVariant = Color(0xFF534344),
    onSurfaceVariant = Color(0xFFD7C1C2),
    outline = Color(0xFFA08C8D),
    surfaceContainerLow = Color(0xFF241D1D),
    surfaceContainer = Color(0xFF292121),
    surfaceContainerHigh = Color(0xFF312828),
    surfaceContainerHighest = Color(0xFF3B3131),
)

private val StrawberryLight = lightColorScheme(
    primary = Color(0xFFB61E40),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8727B),
    onPrimaryContainer = Color(0xFF40000D),
    secondary = Color(0xFFB61E40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADD),
    onSecondaryContainer = Color(0xFF40000D),
    tertiary = Color(0xFF775930),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB1),
    onTertiaryContainer = Color(0xFF2A1800),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF201A1A),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF201A1A),
    surfaceVariant = Color(0xFFF4DDDD),
    onSurfaceVariant = Color(0xFF534344),
    outline = Color(0xFF857374),
)

private val TidalWaveDark = darkColorScheme(
    primary = Color(0xFF5ED4FC),
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = Color(0xFFB8EAFF),
    secondary = Color(0xFF5ED4FC),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D61),
    onSecondaryContainer = Color(0xFFB8EAFF),
    tertiary = Color(0xFF92F7BC),
    onTertiary = Color(0xFF001C3B),
    tertiaryContainer = Color(0xFFC3FADA),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFF001C3B),
    onBackground = Color(0xFFD5E3FF),
    surface = Color(0xFF001C3B),
    onSurface = Color(0xFFD5E3FF),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF8A9296),
    surfaceContainerLow = Color(0xFF062141),
    surfaceContainer = Color(0xFF0B2748),
    surfaceContainerHigh = Color(0xFF122F52),
    surfaceContainerHighest = Color(0xFF1A385E),
)

private val TidalWaveLight = lightColorScheme(
    primary = Color(0xFF006780),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4D4DF),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF006780),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8EAFF),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFF92F7BC),
    onTertiary = Color(0xFF001C3B),
    tertiaryContainer = Color(0xFFC3FADA),
    onTertiaryContainer = Color(0xFF002114),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF001C3B),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF001C3B),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787C),
)

private val YinYangDark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF222222),
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF222222),
    secondaryContainer = Color(0xFF717171),
    onSecondaryContainer = Color(0xFFE4E4E4),
    tertiary = Color(0xFF333333),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF00419E),
    onTertiaryContainer = Color(0xFFD8E2FF),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF4E4E4E),
    onSurfaceVariant = Color(0xFFD1D1D1),
    outline = Color(0xFF999999),
    surfaceContainerLow = Color(0xFF222222),
    surfaceContainer = Color(0xFF282828),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF3A3A3A),
)

private val YinYangLight = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF000000),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFAAAAAA),
    onSecondaryContainer = Color(0xFF0C0C0C),
    tertiary = Color(0xFFDDDDDD),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFD8E2FF),
    onTertiaryContainer = Color(0xFF001947),
    background = Color(0xFFFDFDFD),
    onBackground = Color(0xFF222222),
    surface = Color(0xFFFDFDFD),
    onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFCCCCCC),
    onSurfaceVariant = Color(0xFF515151),
    outline = Color(0xFF838383),
)

private val YotsubaDark = darkColorScheme(
    primary = Color(0xFFFFB59E),
    onPrimary = Color(0xFF5E1700),
    primaryContainer = Color(0xFF852400),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFFFB59E),
    onSecondary = Color(0xFF5E1700),
    secondaryContainer = Color(0xFF852400),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFE4C44A),
    onTertiary = Color(0xFF3B2F00),
    tertiaryContainer = Color(0xFF554500),
    onTertiaryContainer = Color(0xFFFFE173),
    background = Color(0xFF3D0700),
    onBackground = Color(0xFFFFDAD2),
    surface = Color(0xFF3D0700),
    onSurface = Color(0xFFFFDAD2),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C87),
    surfaceContainerLow = Color(0xFF330600),
    surfaceContainer = Color(0xFF421008),
    surfaceContainerHigh = Color(0xFF4B160D),
    surfaceContainerHighest = Color(0xFF561D13),
)

private val YotsubaLight = lightColorScheme(
    primary = Color(0xFFAE3200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBC6B6),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFFA63B16),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3A0B00),
    tertiary = Color(0xFF705D00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE173),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF3D0700),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF3D0700),
    surfaceVariant = Color(0xFFF5DED7),
    onSurfaceVariant = Color(0xFF53433F),
    outline = Color(0xFF85736E),
)

/**
 * Preview primary swatch for each of the 14 presets (used in Appearance Settings sheet).
 */
fun QuveThemePreset.swatchPrimary(dark: Boolean): Color = when (this) {
    QuveThemePreset.QUVE_VERMILION -> if (dark) Color(0xFFFF5935) else Color(0xFFD93E1C)
    QuveThemePreset.MIDNIGHT_STUDIO -> if (dark) Color(0xFF60A5FA) else Color(0xFF1D63D8)
    QuveThemePreset.SAKURA_NEON -> if (dark) Color(0xFFFF6B9D) else Color(0xFFD12667)
    QuveThemePreset.CYBER_AMBER -> if (dark) Color(0xFFFBBF24) else Color(0xFFB77400)
    QuveThemePreset.NORDIC_SLATE -> if (dark) Color(0xFF6EE7B7) else Color(0xFF0F7659)
    QuveThemePreset.ELECTRIC_VIOLET -> if (dark) Color(0xFFC0B6FF) else Color(0xFF5B4BDB)
    QuveThemePreset.MONET -> Color(0xFF7C9EFF)
    QuveThemePreset.TEAL_TURQUOISE -> if (dark) Color(0xFF40E0D0) else Color(0xFF008080)
    QuveThemePreset.GREEN_APPLE -> if (dark) Color(0xFF7ADB8F) else Color(0xFF006D2F)
    QuveThemePreset.HONEY -> if (dark) Color(0xFFFFB869) else Color(0xFF885200)
    QuveThemePreset.STRAWBERRY_DAIQUIRI -> if (dark) Color(0xFFFFB2B9) else Color(0xFFB61E40)
    QuveThemePreset.TIDAL_WAVE -> if (dark) Color(0xFF5ED4FC) else Color(0xFF006780)
    QuveThemePreset.YIN_YANG -> if (dark) Color(0xFFFFFFFF) else Color(0xFF000000)
    QuveThemePreset.YOTSUBA -> if (dark) Color(0xFFFFB59E) else Color(0xFFAE3200)
}

private fun ColorScheme.withAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0D),
    surfaceContainer = Color(0xFF111116),
    surfaceContainerHigh = Color(0xFF1A1A20),
    surfaceContainerHighest = Color(0xFF23232B),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val ExpressiveTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuveTheme(content: @Composable () -> Unit) {
    val mode by QuveSettings.themeMode.collectAsState()
    val preset by QuveSettings.themePreset.collectAsState()
    val amoled by QuveSettings.amoledBlack.collectAsState()

    val dark = when (mode) {
        QuveThemeMode.DARK -> true
        QuveThemeMode.LIGHT -> false
        QuveThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val baseScheme = when (preset) {
        QuveThemePreset.QUVE_VERMILION -> if (dark) QuveVermilionDark else QuveVermilionLight
        QuveThemePreset.MIDNIGHT_STUDIO -> if (dark) MidnightStudioDark else MidnightStudioLight
        QuveThemePreset.SAKURA_NEON -> if (dark) SakuraNeonDark else SakuraNeonLight
        QuveThemePreset.CYBER_AMBER -> if (dark) CyberAmberDark else CyberAmberLight
        QuveThemePreset.NORDIC_SLATE -> if (dark) NordicSlateDark else NordicSlateLight
        QuveThemePreset.ELECTRIC_VIOLET -> if (dark) ElectricVioletDark else ElectricVioletLight
        QuveThemePreset.MONET -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        QuveThemePreset.TEAL_TURQUOISE -> if (dark) TealTurquoiseDark else TealTurquoiseLight
        QuveThemePreset.GREEN_APPLE -> if (dark) GreenAppleDark else GreenAppleLight
        QuveThemePreset.HONEY -> if (dark) HoneyDark else HoneyLight
        QuveThemePreset.STRAWBERRY_DAIQUIRI -> if (dark) StrawberryDark else StrawberryLight
        QuveThemePreset.TIDAL_WAVE -> if (dark) TidalWaveDark else TidalWaveLight
        QuveThemePreset.YIN_YANG -> if (dark) YinYangDark else YinYangLight
        QuveThemePreset.YOTSUBA -> if (dark) YotsubaDark else YotsubaLight
    }

    val colorScheme = if (dark && amoled) baseScheme.withAmoledBlack() else baseScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalQuveDarkTheme provides dark,
        LocalQuveExpressiveColorScheme provides colorScheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = ExpressiveTypography,
            shapes = ExpressiveShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
