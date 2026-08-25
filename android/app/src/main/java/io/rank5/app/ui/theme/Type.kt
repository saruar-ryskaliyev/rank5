package io.rank5.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

// Six styles, one system font. Material slots deliberately alias these values so
// controls cannot silently introduce a seventh treatment.
private val Hero = TextStyle(
    fontSize = 48.sp,
    fontWeight = FontWeight.ExtraBold,
    lineHeight = 52.sp,
    platformStyle = NoFontPadding,
)
private val ScreenTitle = TextStyle(
    fontSize = 32.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 38.sp,
    platformStyle = NoFontPadding,
)
private val Heading = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 30.sp,
    platformStyle = NoFontPadding,
)
private val Title = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 22.sp,
    platformStyle = NoFontPadding,
)
private val Body = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 22.sp,
    platformStyle = NoFontPadding,
)
private val Label = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 18.sp,
    platformStyle = NoFontPadding,
)

val Rank5Typography = Typography(
    displayLarge = Hero,
    displayMedium = Hero,
    displaySmall = ScreenTitle,
    headlineLarge = ScreenTitle,
    headlineMedium = Heading,
    headlineSmall = Heading,
    titleLarge = Title,
    titleMedium = Title,
    titleSmall = Label,
    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = Label,
    labelLarge = Title,
    labelMedium = Label,
    labelSmall = Label,
)
