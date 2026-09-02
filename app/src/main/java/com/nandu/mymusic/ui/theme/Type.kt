package com.nandu.mymusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.R

/** Inter — closest legal substitute for Apple's SF Pro (SIL OFL 1.1). */
val AppleFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** iOS text styles (sp / lineHeight sp / letterSpacing sp), Apple Music conventions. */
object AppleText {
    val LargeTitle = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp)
    val Title1 = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp)
    val Title2 = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp)
    val Title3 = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp)
    val Headline = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp)
    val Callout = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp)
    val Subhead = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val Footnote = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption1 = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Caption2 = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp)
    /** Section headers inside scrolling content (Apple Music style). */
    val SectionHeader = TextStyle(fontFamily = AppleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp)
}

/**
 * Every Material slot maps to the iOS scale. Because `Text` merges explicit
 * params into `LocalTextStyle`, hardcoded `fontSize = X.sp` call sites inherit
 * the Inter family from these defaults without any changes.
 */
val Typography = Typography(
    displayLarge = AppleText.LargeTitle,
    displayMedium = AppleText.Title1,
    displaySmall = AppleText.Title2,
    headlineLarge = AppleText.Title3,
    headlineMedium = AppleText.Headline,
    headlineSmall = AppleText.Headline,
    titleLarge = AppleText.Title2,
    titleMedium = AppleText.Headline,
    titleSmall = AppleText.Subhead.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = AppleText.Body,
    bodyMedium = AppleText.Callout,
    bodySmall = AppleText.Footnote,
    labelLarge = AppleText.Subhead,
    labelMedium = AppleText.Caption1,
    labelSmall = AppleText.Caption2,
)
