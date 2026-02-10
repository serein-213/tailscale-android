// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import android.os.Build

@Composable
fun AppTheme(content: @Composable() () -> Unit) {
  val themeMode by ThemeConfig.theme.collectAsState()
  val context = LocalContext.current
  val systemInDarkTheme = isSystemInDarkTheme()
  
  val useDarkTheme = when (themeMode) {
    AppThemeMode.SYSTEM -> systemInDarkTheme
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK, AppThemeMode.DRACULA, AppThemeMode.SOLARIZED, AppThemeMode.OLED -> true
    AppThemeMode.MONET -> systemInDarkTheme
  }

  val colors = when (themeMode) {
    AppThemeMode.SYSTEM -> if (systemInDarkTheme) DarkColors else LightColors
    AppThemeMode.LIGHT -> LightColors
    AppThemeMode.DARK -> DarkColors
    AppThemeMode.DRACULA -> DraculaColors
    AppThemeMode.SOLARIZED -> SolarizedDarkColors
    AppThemeMode.OLED -> OLEDDarkColors
    AppThemeMode.MONET -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (systemInDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else {
        if (systemInDarkTheme) DarkColors else LightColors
      }
    }
  }

  val typography =
      Typography(
          // titleMedium is styled to be slightly larger than bodyMedium for emphasis
          titleMedium =
              MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
          // bodyMedium is styled to use same line height as titleMedium to ensure even vertical
          // margins in list items.
          bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp))

  // TODO: Migrate to Activity.enableEdgeToEdge
  @Suppress("deprecation") val systemUiController = rememberSystemUiController()

  DisposableEffect(systemUiController, useDarkTheme) {
    systemUiController.setStatusBarColor(color = colors.surfaceContainer, darkIcons = !useDarkTheme)
    systemUiController.setNavigationBarColor(color = Color.Black)
    onDispose {}
  }

  MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF4B70CC), // blue-500
        onPrimary = Color(0xFFFFFFFF), // white
        primaryContainer = Color(0xFFF0F5FF), // blue-0
        onPrimaryContainer = Color(0xFF3E5DB3), // blue-600
        error = Color(0xFFB22C30), // red-500
        onError = Color(0xFFFFFFFF), // white
        errorContainer = Color(0xFFFEF6F3), // red-0
        onErrorContainer = Color(0xFF930921), // red-600
        surfaceDim = Color(0xFFF7F5F4), // gray-100
        surface = Color(0xFFFFFFFF), // white,
        background = Color(0xFFF7F5F4), // gray-100
        surfaceBright = Color(0xFFFFFFFF), // white
        surfaceContainerLowest = Color(0xFFFFFFFF), // white
        surfaceContainerLow = Color(0xFFF7F5F4), // gray-100
        surfaceContainer = Color(0xFFF7F5F4), // gray-100
        surfaceContainerHigh = Color(0xFFF7F5F4), // gray-100
        surfaceContainerHighest = Color(0xFFEDEBEA), // gray-200 (for search bar)
        surfaceVariant = Color(0xFFF7F5F4), // gray-100,
        onSurface = Color(0xFF232222), // gray-800
        onSurfaceVariant = Color(0xFF706E6D), // gray-500
        outline = Color(0xFF706E6D), // gray-500
        outlineVariant = Color(0xFFEDEBEA), // gray-200
        inverseSurface = Color(0xFF232222), // gray-800
        inverseOnSurface = Color(0xFFFFFFFF), // white
        scrim = Color(0xAA000000), // black
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF3E5DB3), // blue-600
        onPrimary = Color(0xFFFFFFFF), // white
        primaryContainer = Color(0xFFf0f5ff), // blue-0
        onPrimaryContainer = Color(0xFF5A82DC), // blue-400
        error = Color(0xFFEF5350), // red-400
        onError = Color(0xFFFFFFFF), // white
        errorContainer = Color(0xFFfff6f4), // red-0
        onErrorContainer = Color(0xFF940822), // red-600
        surfaceDim = Color(0xFF1f1e1e), // gray-900
        surface = Color(0xFF232222), // gray-800
        background = Color(0xFF181717), // gray-1000
        surfaceBright = Color(0xFF444342), // gray-600
        surfaceContainerLowest = Color(0xFF1f1e1e), // gray-900
        surfaceContainerLow = Color(0xFF232222), // gray-800
        surfaceContainer = Color(0xFF181717), // gray-1000
        surfaceContainerHigh = Color(0xFF232222), // gray-800
        surfaceContainerHighest = Color(0xFF2e2d2d), // gray-700
        surfaceVariant = Color(0xFF1f1e1e), // gray-900
        onSurface = Color(0xFFfaf9f8), // gray-0
        onSurfaceVariant = Color(0xFFafacab), // gray-400
        outline = Color(0xFF706E6D), // gray-500
        outlineVariant = Color(0xFF2E2D2D), // gray-700
        inverseSurface = Color(0xFFEDEBEA), // gray-200
        inverseOnSurface = Color(0xFF000000), // black
        scrim = Color(0xAA000000), // black
    )

private val DraculaColors =
    darkColorScheme(
        primary = Color(0xFFBD93F9), // purple
        onPrimary = Color(0xFF282A36),
        primaryContainer = Color(0xFF44475A),
        onPrimaryContainer = Color(0xFFF8F8F2),
        secondary = Color(0xFF50FA7B), // green
        onSecondary = Color(0xFF282A36),
        secondaryContainer = Color(0xFF44475A),
        onSecondaryContainer = Color(0xFF50FA7B),
        tertiary = Color(0xFF8BE9FD), // cyan
        onTertiary = Color(0xFF282A36),
        tertiaryContainer = Color(0xFF44475A),
        onTertiaryContainer = Color(0xFF8BE9FD),
        error = Color(0xFFFF5555), // red
        onError = Color(0xFF282A36),
        background = Color(0xFF282A36), // main background
        onBackground = Color(0xFFF8F8F2),
        surface = Color(0xFF282A36),
        onSurface = Color(0xFFF8F8F2),
        surfaceVariant = Color(0xFF44475A), // selection
        onSurfaceVariant = Color(0xFFAEB9E0), // adjusted comment color for better legibility
        outline = Color(0xFF6272A4),
        surfaceDim = Color(0xFF21222C),
        surfaceBright = Color(0xFF6272A4),
        surfaceContainerLowest = Color(0xFF21222C),
        surfaceContainerLow = Color(0xFF21222C),
        surfaceContainer = Color(0xFF343746), // Top bar & Status bar (lighter than background)
        surfaceContainerHigh = Color(0xFF44475A),
        surfaceContainerHighest = Color(0xFF44475A), // Search Bar
        inverseSurface = Color(0xFFF8F8F2),
        inverseOnSurface = Color(0xFF282A36),
    )

private val SolarizedDarkColors =
    darkColorScheme(
        primary = Color(0xFF268BD2), // blue
        onPrimary = Color(0xFFFDF6E3), // base3
        primaryContainer = Color(0xFF073642), // base02
        onPrimaryContainer = Color(0xFF93A1A1), // base1
        secondary = Color(0xFF2AA198), // cyan
        onSecondary = Color(0xFFFDF6E3),
        error = Color(0xFFDC322F), // red
        onError = Color(0xFFFDF6E3),
        background = Color(0xFF002B36), // base03
        onBackground = Color(0xFF839496), // base0
        surface = Color(0xFF073642), // base02
        onSurface = Color(0xFF93A1A1), // base1
        surfaceVariant = Color(0xFF073642),
        onSurfaceVariant = Color(0xFF839496),
        outline = Color(0xFF586E75), // base01
        surfaceContainer = Color(0xFF002B36),
        surfaceContainerHigh = Color(0xFF073642),
        surfaceContainerHighest = Color(0xFF073642), // base02
    )

private val OLEDDarkColors =
    darkColorScheme(
        primary = Color(0xFF3E5DB3), // same as dark
        onPrimary = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF121212),
        onSurfaceVariant = Color(0xFFAFACAB),
        outline = Color(0xFF706E6D),
        surfaceContainer = Color(0xFF000000),
        surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF1C1C1C),
    )

val ColorScheme.warning: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFFBB5504) // yellow-400
      } else {
        Color(0xFFD97917) // yellow-300
      }

val ColorScheme.onWarning: Color
  get() = Color(0xFFFFFFFF) // white

val ColorScheme.warningContainer: Color
  get() = Color(0xFFFFFAEE) // orange-0

val ColorScheme.onWarningContainer: Color
  get() = Color(0xFF7E1E22) // orange-600

val ColorScheme.success: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF81C784) // Lighter green for dark themes
      } else {
        Color(0xFF0A825D) // green-400
      }

val ColorScheme.onSuccess: Color
  get() = Color(0xFFFFFFFF) // white

val ColorScheme.successContainer: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF1B5E20)
      } else {
        Color(0xFFEFFEEC) // green-0
      }

val ColorScheme.onSuccessContainer: Color
  get() = Color(0xFF0E4B3B) // green-600

val ColorScheme.on: Color
  get() = Color(0xFF1CA672) // green-300

val ColorScheme.off: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF444342) // gray-600
      } else {
        Color(0xFFD9D6D5) // gray-300
      }

val ColorScheme.link: Color
  get() = onPrimaryContainer

val ColorScheme.customError: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF940821) // red-600
      } else {
        Color(0xFFB22D30) // red-500
      }

val ColorScheme.customErrorContainer: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF760012) // red-700
      } else {
        Color(0xFF940821) // red-600
      }

/**
 * Main color scheme for list items, uses onPrimaryContainer color for leading and trailing icons.
 */
val ColorScheme.listItem: ListItemColors
  @Composable
  get() {
    val default = ListItemDefaults.colors()
    return ListItemColors(
        containerColor = default.containerColor,
        headlineColor = default.headlineColor,
        leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        overlineColor = default.overlineColor,
        supportingTextColor = default.supportingTextColor,
        trailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Like listItem, but with the overline content using the onSurface color. */
val ColorScheme.titledListItem: ListItemColors
  @Composable
  get() {
    val default = listItem
    return ListItemColors(
        containerColor = default.containerColor,
        headlineColor = default.headlineColor,
        leadingIconColor = default.leadingIconColor,
        overlineColor = MaterialTheme.colorScheme.onSurface,
        supportingTextColor = default.supportingTextColor,
        trailingIconColor = default.trailingIconColor,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Color scheme for disabled list items. */
val ColorScheme.disabledListItem: ListItemColors
  @Composable
  get() {
    val default = ListItemDefaults.colors()
    return ListItemColors(
        containerColor = default.containerColor,
        headlineColor = MaterialTheme.colorScheme.disabled,
        leadingIconColor = default.leadingIconColor,
        overlineColor = default.overlineColor,
        supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingIconColor = default.trailingIconColor,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Color scheme for list items that should be styled as a surface container. */
val ColorScheme.surfaceContainerListItem: ListItemColors
  @Composable
  get() {
    val default = ListItemDefaults.colors()
    return ListItemColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        headlineColor = MaterialTheme.colorScheme.onSurface,
        leadingIconColor = MaterialTheme.colorScheme.onSurface,
        overlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingIconColor = MaterialTheme.colorScheme.onSurface,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Color scheme for list items that should be styled as a primary item. */
val ColorScheme.primaryListItem: ListItemColors
  @Composable
  get() {
    val default = ListItemDefaults.colors()
    return ListItemColors(
        containerColor = MaterialTheme.colorScheme.primary,
        headlineColor = MaterialTheme.colorScheme.onPrimary,
        leadingIconColor = MaterialTheme.colorScheme.onPrimary,
        overlineColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        supportingTextColor = MaterialTheme.colorScheme.onPrimary,
        trailingIconColor = MaterialTheme.colorScheme.onPrimary,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Color scheme for list items that should be styled as a warning item. */
val ColorScheme.warningListItem: ListItemColors
  @Composable
  get() {
    val default = ListItemDefaults.colors()
    return ListItemColors(
        containerColor = MaterialTheme.colorScheme.warning,
        headlineColor = MaterialTheme.colorScheme.onPrimary,
        leadingIconColor = MaterialTheme.colorScheme.onPrimary,
        overlineColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        supportingTextColor = MaterialTheme.colorScheme.onPrimary,
        trailingIconColor = MaterialTheme.colorScheme.onPrimary,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Color scheme for list items that should be styled as an error item. */
val ColorScheme.errorListItem: ListItemColors
  @Composable
  get() {
    val default = ListItemDefaults.colors()
    return ListItemColors(
        containerColor = MaterialTheme.colorScheme.customError,
        headlineColor = MaterialTheme.colorScheme.onPrimary,
        leadingIconColor = MaterialTheme.colorScheme.onPrimary,
        overlineColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        supportingTextColor = MaterialTheme.colorScheme.onPrimary,
        trailingIconColor = MaterialTheme.colorScheme.onPrimary,
        disabledHeadlineColor = default.disabledHeadlineColor,
        disabledLeadingIconColor = default.disabledLeadingIconColor,
        disabledTrailingIconColor = default.disabledTrailingIconColor)
  }

/** Main color scheme for top app bar, styles it as a surface container. */
@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.topAppBar: TopAppBarColors
  @Composable
  get() =
      TopAppBarDefaults.topAppBarColors()
          .copy(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
          )

val ColorScheme.secondaryButton: ButtonColors
  @Composable
  get() {
    val defaults = ButtonDefaults.buttonColors()
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
      return ButtonColors(
          containerColor = Color(0xFF4B70CC), // blue-500
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    } else {
      return ButtonColors(
          containerColor = Color(0xFF5A82DC), // blue-400
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    }
  }

val ColorScheme.errorButton: ButtonColors
  @Composable
  get() {
    val defaults = ButtonDefaults.buttonColors()
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
      return ButtonColors(
          containerColor = Color(0xFFB22D30), // red-500
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    } else {
      return ButtonColors(
          containerColor = Color(0xFFD04841), // red-400
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    }
  }

val ColorScheme.warningButton: ButtonColors
  @Composable
  get() {
    val defaults = ButtonDefaults.buttonColors()
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
      return ButtonColors(
          containerColor = Color(0xFFD97917), // yellow-300
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    } else {
      return ButtonColors(
          containerColor = Color(0xFFE5993E), // yellow-200
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    }
  }

val ColorScheme.defaultTextColor: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color.White
      } else {
        Color.Black
      }

val ColorScheme.logoBackground: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFFFFFFFF) // white
      } else {
        Color(0xFF1F1E1E)
      }

val ColorScheme.standaloneLogoDotEnabled: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFFFFFFFF)
      } else {
        Color(0xFF000000)
      }

val ColorScheme.standaloneLogoDotDisabled: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0x66FFFFFF)
      } else {
        Color(0x661F1E1E)
      }

val ColorScheme.onBackgroundLogoDotEnabled: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF141414)
      } else {
        Color(0xFFFFFFFF)
      }

val ColorScheme.onBackgroundLogoDotDisabled: Color
  @Composable
  get() =
      if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0x66141414)
      } else {
        Color(0x66FFFFFF)
      }

val ColorScheme.exitNodeToggleButton: ButtonColors
  @Composable
  get() {
    val defaults = ButtonDefaults.buttonColors()
    return if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
      ButtonColors(
          containerColor = Color(0xFF444342), // grey-600
          contentColor = Color(0xFFFFFFFF), // white
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    } else {
      ButtonColors(
          containerColor = Color(0xFFEDEBEA), // grey-300
          contentColor = Color(0xFF000000), // black
          disabledContainerColor = defaults.disabledContainerColor,
          disabledContentColor = defaults.disabledContentColor)
    }
  }

val ColorScheme.disabled: Color
  get() = Color(0xFFAFACAB) // gray-400

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.searchBarColors: TextFieldColors
  @Composable
  get() {
    return OutlinedTextFieldDefaults.colors(
        focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent)
  }

val TextStyle.short: TextStyle
  get() = copy(lineHeight = 20.sp)

val Typography.minTextSize: TextUnit
  get() = 10.sp
