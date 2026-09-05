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
    OLED,
    MANGA
}

object ThemeConfig {
    private const val PREF_KEY_THEME = "app_theme_mode"
    private const val PREF_KEY_MANGA_PAPER = "manga_paper"
    private const val PREF_KEY_MANGA_ACCENT = "manga_accent"
    private val _theme = MutableStateFlow(AppThemeMode.SYSTEM)
    private val _mangaPaper = MutableStateFlow(MangaPaper.DAY)
    private val _mangaAccent = MutableStateFlow(MangaAccent.CRIMSON)
    val theme: StateFlow<AppThemeMode> = _theme
    val mangaPaper: StateFlow<MangaPaper> = _mangaPaper
    val mangaAccent: StateFlow<MangaAccent> = _mangaAccent

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        val themeName = prefs.getString(PREF_KEY_THEME, AppThemeMode.SYSTEM.name)
        _theme.value = try {
            AppThemeMode.valueOf(themeName ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
        _mangaPaper.value = try {
            MangaPaper.valueOf(prefs.getString(PREF_KEY_MANGA_PAPER, MangaPaper.DAY.name) ?: MangaPaper.DAY.name)
        } catch (e: Exception) {
            MangaPaper.DAY
        }
        _mangaAccent.value = try {
            MangaAccent.valueOf(prefs.getString(PREF_KEY_MANGA_ACCENT, MangaAccent.CRIMSON.name) ?: MangaAccent.CRIMSON.name)
        } catch (e: Exception) {
            MangaAccent.CRIMSON
        }
    }

    fun setTheme(mode: AppThemeMode) {
        _theme.value = mode
        val prefs = App.get().getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_THEME, mode.name).apply()
    }

    fun setMangaPaper(paper: MangaPaper) {
        _mangaPaper.value = paper
        val prefs = App.get().getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_MANGA_PAPER, paper.name).apply()
    }

    fun setMangaAccent(accent: MangaAccent) {
        _mangaAccent.value = accent
        val prefs = App.get().getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_MANGA_ACCENT, accent.name).apply()
    }
}
