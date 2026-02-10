// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.theme

import android.content.Context
import com.tailscale.ipn.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    MONET,
    DRACULA,
    SOLARIZED,
    OLED
}

object ThemeConfig {
    private const val PREF_KEY_THEME = "app_theme_mode"
    private val _theme = MutableStateFlow(AppThemeMode.SYSTEM)
    val theme: StateFlow<AppThemeMode> = _theme

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        val themeName = prefs.getString(PREF_KEY_THEME, AppThemeMode.SYSTEM.name)
        _theme.value = try {
            AppThemeMode.valueOf(themeName ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setTheme(mode: AppThemeMode) {
        _theme.value = mode
        val prefs = App.get().getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_THEME, mode.name).apply()
    }
}
