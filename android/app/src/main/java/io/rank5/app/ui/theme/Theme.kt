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
import androidx.compose.ui.unit.dp

private val PaperBg = Color(0xFFF8F7FC)
private val PaperSurface = Color(0xFFFFFFFF)
private val PaperSurfaceVariant = Color(0xFFEFEDF7)
private val InkLight = Color(0xFF17151F)
private val MutedLight = Color(0xFF625F6B)
private val VioletLight = Color(0xFF5A3FD6)
private val CelebrationLight = Color(0xFFC33D5C)
private val SuccessLight = Color(0xFF0B6B61)
private val ErrorLight = Color(0xFFB3261E)
private val OutlineLight = Color(0xFFD8D4E3)

private val InkBg = Color(0xFF111016)
private val InkSurface = Color(0xFF1B1922)
private val InkSurfaceVariant = Color(0xFF272431)
private val WarmWhite = Color(0xFFF8F7FB)
private val MutedDark = Color(0xFFB9B5C4)
private val VioletDark = Color(0xFFA998FF)
private val CelebrationDark = Color(0xFFFF8BA5)
private val SuccessDark = Color(0xFF5ED6C2)
private val ErrorDark = Color(0xFFFF8A80)
private val OutlineDark = Color(0xFF45414F)

/** Brand colors that have no MaterialTheme role; resolved per light/dark theme. */
@Immutable
data class Rank5Extras(
    val gold: Color,
    val goldText: Color,
    val onGold: Color,
    /** Accent used AS TEXT (timer, links, outlined buttons) — AA-safe on bg/cards. */
    val accentText: Color,
    val urgent: Color,
    /** Player identity hues; every swatch keeps a white initial >=3:1. */
    val avatarPalette: List<Color>,
    /** Kept as identical endpoints so legacy callers render a flat brand background. */
    val gradientTop: Color,
    val gradientBottom: Color,
)

/** Muted, warm hues shared by both themes; each verified >=3:1 against white. */
private val AvatarPalette = listOf(
    Color(0xFF5A3FD6), Color(0xFF0B6B61), Color(0xFF9B4A67),
    Color(0xFF536E9F), Color(0xFF7954A8), Color(0xFF8A5C22),
)

private val LightExtras = Rank5Extras(
    gold = Color(0xFFFFE5A3),
    goldText = Color(0xFF755500),
    onGold = InkLight,
    accentText = CelebrationLight,
    urgent = ErrorLight,
    avatarPalette = AvatarPalette,
    gradientTop = PaperBg,
    gradientBottom = PaperBg,
)

private val DarkExtras = Rank5Extras(
    gold = Color(0xFF5A4821),
    goldText = Color(0xFFFFD978),
    onGold = WarmWhite,
    accentText = CelebrationDark,
    urgent = ErrorDark,
    avatarPalette = AvatarPalette,
    gradientTop = InkBg,
    gradientBottom = InkBg,
)

val LocalRank5Extras = staticCompositionLocalOf { LightExtras }

private val LightScheme = lightColorScheme(
    primary = VioletLight,
    onPrimary = Color.White,
    // Container roles stay warm — M3 baseline defaults are lavender/purple.
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF241178),
    secondary = CelebrationLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF9DCE4),
    onSecondaryContainer = Color(0xFF641A30),
    tertiary = SuccessLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E8D4),
    onTertiaryContainer = Color(0xFF1F4A27),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF8C1D18),
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
    primaryContainer = Color(0xFF3B2B83),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = CelebrationDark,
    onSecondary = InkBg,
    secondaryContainer = Color(0xFF5B2937),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = SuccessDark,
    onTertiary = InkBg,
    tertiaryContainer = Color(0xFF164D45),
    onTertiaryContainer = Color(0xFFC4F5EA),
    error = ErrorDark,
    onError = InkBg,
    errorContainer = Color(0xFF5C2019),
    onErrorContainer = Color(0xFFFFDAD4),
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
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
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
