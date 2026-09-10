package io.rank5.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Two chromatic families only: violet (brand/actions) and rose (celebration/error).
// Everything else is a black/white neutral variation.
private val PaperBg = Color(0xFFF8F7FC)
private val PaperSurface = Color.White
private val PaperSurfaceVariant = Color(0xFFEFEDF7)
private val InkLight = Color(0xFF17151F)
private val MutedLight = Color(0xFF625F6B)
// Interactive field boundaries retain >=3:1 contrast; decorative cards use outlineVariant.
private val OutlineLight = Color(0xFF888291)
private val Violet = Color(0xFF5A3FD6)
private val VioletDarkText = Color(0xFF241178)
private val VioletContainer = Color(0xFFE6E0FF)
private val Rose = Color(0xFFB3264B)
private val RoseDarkText = Color(0xFF641A30)
private val RoseContainer = Color(0xFFF9DCE4)

private val InkBg = Color(0xFF111016)
private val InkSurface = Color(0xFF1B1922)
private val InkSurfaceVariant = Color(0xFF272431)
private val WarmWhite = Color(0xFFF8F7FB)
private val MutedDark = Color(0xFFB9B5C4)
private val OutlineDark = Color(0xFF8C859A)
private val VioletDark = Color(0xFFA998FF)
private val VioletDarkContainer = Color(0xFF3B2B83)
private val RoseDark = Color(0xFFFF8BA5)
private val RoseDarkContainer = Color(0xFF5B2937)

/** Brand colors that have no MaterialTheme role; resolved per light/dark theme. */
@Immutable
data class Rank5Extras(
    val highlight: Color,
    val highlightText: Color,
    val onHighlight: Color,
    /** Accent used AS TEXT (timer, links, outlined buttons) — AA-safe on bg/cards. */
    val accentText: Color,
    /** Player identity hues; every swatch keeps a white initial >=3:1. */
    val avatarPalette: List<Color>,
)

/** Violet/rose variations; each swatch keeps a white initial >=3:1. */
private val AvatarPalette = listOf(
    Violet, Color(0xFF4931B8), Color(0xFF7559E1),
    Rose, Color(0xFF92213F), Color(0xFF8D4962),
)

private val LightExtras = Rank5Extras(
    highlight = RoseContainer,
    highlightText = RoseDarkText,
    onHighlight = InkLight,
    accentText = Rose,
    avatarPalette = AvatarPalette,
)

private val DarkExtras = Rank5Extras(
    highlight = RoseDarkContainer,
    highlightText = RoseDark,
    onHighlight = WarmWhite,
    accentText = RoseDark,
    avatarPalette = AvatarPalette,
)

val LocalRank5Extras = staticCompositionLocalOf { LightExtras }

private val LightScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    // Container roles stay warm — M3 baseline defaults are lavender/purple.
    primaryContainer = VioletContainer,
    onPrimaryContainer = VioletDarkText,
    secondary = Rose,
    onSecondary = Color.White,
    secondaryContainer = RoseContainer,
    onSecondaryContainer = RoseDarkText,
    tertiary = Violet,
    onTertiary = Color.White,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = VioletDarkText,
    error = Rose,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = RoseDarkText,
    background = PaperBg,
    onBackground = InkLight,
    surface = PaperSurface,
    onSurface = InkLight,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = MutedLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE8E5EF),
    // Keep M3 container roles (dialogs, menus) on-palette instead of baseline gray.
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperBg,
    surfaceContainer = PaperSurfaceVariant,
    surfaceContainerHigh = PaperSurfaceVariant,
    surfaceContainerHighest = PaperSurfaceVariant,
)

private val DarkScheme = darkColorScheme(
    primary = VioletDark,
    onPrimary = InkBg,
    primaryContainer = VioletDarkContainer,
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = RoseDark,
    onSecondary = InkBg,
    secondaryContainer = RoseDarkContainer,
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = VioletDark,
    onTertiary = InkBg,
    tertiaryContainer = VioletDarkContainer,
    onTertiaryContainer = Color(0xFFE7E1FF),
    error = RoseDark,
    onError = InkBg,
    errorContainer = RoseDarkContainer,
    onErrorContainer = Color(0xFFFFD9E2),
    background = InkBg,
    onBackground = WarmWhite,
    surface = InkSurface,
    onSurface = WarmWhite,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = MutedDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF34313D),
    surfaceContainerLowest = InkBg,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceVariant,
    surfaceContainerHighest = InkSurfaceVariant,
)

private val Rank5Shapes = Shapes(
    small = RoundedCornerShape(Corners.compact),
    medium = RoundedCornerShape(Corners.compact),
    large = RoundedCornerShape(Corners.roomy),
)

@Composable
fun Rank5Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark = darkTheme
    CompositionLocalProvider(LocalRank5Extras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Rank5Typography,
            shapes = Rank5Shapes,
            content = content,
        )
    }
}
