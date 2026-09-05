// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause
// Manga colors and typography direction ported from Komi Store's Manga personality.

package com.tailscale.ipn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MangaPaper {
    DAY,
    NIGHT,
    NORD,
}

enum class MangaAccent {
    MONO,
    CRIMSON,
    COBALT,
    SUN,
    FROST,
}

private data class MangaInk(
    val page: Color,
    val panel: Color,
    val well: Color,
    val ink: Color,
    val muted: Color,
    val shadow: Color,
    val error: Color,
    val onError: Color,
)

private fun mangaInk(paper: MangaPaper): MangaInk =
    when (paper) {
        MangaPaper.DAY ->
            MangaInk(
                page = Color(0xFFF1EADC),
                panel = Color(0xFFFAF5EA),
                well = Color(0xFFE7DEC9),
                ink = Color(0xFF1B150D),
                muted = Color(0xFF695F50),
                shadow = Color(0xFF1B150D),
                error = Color(0xFFB3261E),
                onError = Color(0xFFFFFFFF),
            )

        MangaPaper.NIGHT ->
            MangaInk(
                page = Color(0xFF0C0A07),
                panel = Color(0xFF16120C),
                well = Color(0xFF211B12),
                ink = Color(0xFFF0E9DA),
                muted = Color(0xFF968B77),
                shadow = Color(0xFF000000),
                error = Color(0xFFFF6B5E),
                onError = Color(0xFF1B150D),
            )

        MangaPaper.NORD ->
            MangaInk(
                page = Color(0xFF2E3440),
                panel = Color(0xFF3B4252),
                well = Color(0xFF434C5E),
                ink = Color(0xFFECEFF4),
                muted = Color(0xFF9AA5BD),
                shadow = Color(0xFF20242E),
                error = Color(0xFFE5818A),
                onError = Color(0xFF20242E),
            )
    }

private fun mangaAccentSwatch(accent: MangaAccent): Pair<Color, Color>? =
    when (accent) {
        MangaAccent.MONO -> null
        MangaAccent.CRIMSON -> Color(0xFFD8202A) to Color(0xFFFFFFFF)
        MangaAccent.COBALT -> Color(0xFF1F4ED8) to Color(0xFFFFFFFF)
        MangaAccent.SUN -> Color(0xFFF5A300) to Color(0xFF1B150D)
        MangaAccent.FROST -> Color(0xFF88C0D0) to Color(0xFF2E3440)
    }

fun mangaAccentColor(accent: MangaAccent): Color? = mangaAccentSwatch(accent)?.first

fun mangaAccentOnColor(accent: MangaAccent): Color = mangaAccentSwatch(accent)?.second ?: Color.Transparent

fun mangaColorScheme(paper: MangaPaper, accent: MangaAccent): ColorScheme {
    val ink = mangaInk(paper)
    val primary =
        mangaAccentSwatch(accent) ?: (ink.ink to ink.page)
    val base = if (paper == MangaPaper.DAY) lightColorScheme() else darkColorScheme()

    return base.copy(
        primary = primary.first,
        onPrimary = primary.second,
        primaryContainer = primary.first,
        onPrimaryContainer = primary.first,
        secondary = primary.first,
        onSecondary = primary.second,
        secondaryContainer = primary.first,
        onSecondaryContainer = primary.first,
        tertiary = primary.first,
        onTertiary = primary.second,
        tertiaryContainer = primary.first,
        onTertiaryContainer = primary.first,
        background = ink.page,
        onBackground = ink.ink,
        surface = ink.panel,
        onSurface = ink.ink,
        surfaceDim = ink.page,
        surfaceBright = ink.panel,
        surfaceContainerLowest = ink.page,
        surfaceContainerLow = ink.panel,
        surfaceContainer = ink.panel,
        surfaceContainerHigh = ink.well,
        surfaceContainerHighest = ink.well,
        surfaceVariant = ink.well,
        onSurfaceVariant = ink.muted,
        outline = ink.ink,
        outlineVariant = ink.muted,
        error = ink.error,
        onError = ink.onError,
        errorContainer = ink.well,
        onErrorContainer = ink.ink,
        inverseSurface = ink.ink,
        inverseOnSurface = ink.page,
        scrim = ink.shadow,
    )
}

val MangaShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )

private val MangaTypography: Typography =
    Typography().copy(
        displayLarge =
            Typography().displayLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = 0.02.em,
            ),
        displayMedium =
            Typography().displayMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.02.em,
            ),
        displaySmall =
            Typography().displaySmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.02.em,
            ),
        headlineLarge =
            Typography().headlineLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.02.em,
            ),
        headlineMedium =
            Typography().headlineMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.02.em,
            ),
        headlineSmall =
            Typography().headlineSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.02.em,
            ),
        titleLarge =
            Typography().titleLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.02.em,
            ),
        titleMedium =
            Typography().titleMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.01.em,
            ),
        titleSmall =
            Typography().titleSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        bodyLarge =
            Typography().bodyLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            Typography().bodyMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            Typography().bodySmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            Typography().labelLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            Typography().labelMedium.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelSmall =
            Typography().labelSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
    )

fun mangaTypography(): Typography = MangaTypography
