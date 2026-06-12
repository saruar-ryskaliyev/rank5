package io.rank5.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

val Rank5Typography = Typography(
    // Big score / room-code hero
    displayLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        platformStyle = NoFontPadding,
    ),
    // Screen title
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        platformStyle = NoFontPadding,
    ),
    // Prompt / question
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        platformStyle = NoFontPadding,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        platformStyle = NoFontPadding,
    ),
    // Card title / list item
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        platformStyle = NoFontPadding,
    ),
    // Big body / field text
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        platformStyle = NoFontPadding,
    ),
    // Body
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding,
    ),
    // Button
    labelLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        platformStyle = NoFontPadding,
    ),
    // Section label / overline (ALL CAPS via copy)
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        platformStyle = NoFontPadding,
    ),
    // Caption / hints
    labelSmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        platformStyle = NoFontPadding,
    ),
)
