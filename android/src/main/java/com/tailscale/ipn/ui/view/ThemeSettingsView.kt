// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.theme.MangaAccent
import com.tailscale.ipn.ui.theme.MangaPaper
import com.tailscale.ipn.ui.theme.AppThemeMode
import com.tailscale.ipn.ui.theme.ThemeConfig
import com.tailscale.ipn.ui.theme.listItem
import com.tailscale.ipn.ui.theme.mangaAccentColor

@Composable
fun ThemeSettingsView(onBack: () -> Unit) {
    val currentThemeMode by ThemeConfig.theme.collectAsState()
    val currentMangaPaper by ThemeConfig.mangaPaper.collectAsState()
    val currentMangaAccent by ThemeConfig.mangaAccent.collectAsState()

    Scaffold(
        topBar = {
            Header(titleRes = R.string.theme_setting, onBack = onBack)
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
            ThemeOption(R.string.theme_auto, AppThemeMode.SYSTEM, currentThemeMode)
            ThemeOption(R.string.theme_light, AppThemeMode.LIGHT, currentThemeMode)
            ThemeOption(R.string.theme_dark, AppThemeMode.DARK, currentThemeMode)
            ThemeOption(R.string.theme_monet, AppThemeMode.MONET, currentThemeMode)
            ThemeOption(R.string.theme_manga, AppThemeMode.MANGA, currentThemeMode)
            ThemeOption(R.string.theme_dracula, AppThemeMode.DRACULA, currentThemeMode)
            ThemeOption(R.string.theme_solarized, AppThemeMode.SOLARIZED, currentThemeMode)
            ThemeOption(R.string.theme_oled, AppThemeMode.OLED, currentThemeMode)

            if (currentThemeMode == AppThemeMode.MANGA) {
                SectionLabel(R.string.manga_paper)
                MangaPaperOption(R.string.manga_paper_day, MangaPaper.DAY, currentMangaPaper)
                MangaPaperOption(R.string.manga_paper_night, MangaPaper.NIGHT, currentMangaPaper)
                MangaPaperOption(R.string.manga_paper_nord, MangaPaper.NORD, currentMangaPaper)

                SectionLabel(R.string.manga_accent)
                MangaAccentOption(R.string.manga_accent_mono, MangaAccent.MONO, currentMangaAccent)
                MangaAccentOption(R.string.manga_accent_crimson, MangaAccent.CRIMSON, currentMangaAccent)
                MangaAccentOption(R.string.manga_accent_cobalt, MangaAccent.COBALT, currentMangaAccent)
                MangaAccentOption(R.string.manga_accent_sun, MangaAccent.SUN, currentMangaAccent)
                MangaAccentOption(R.string.manga_accent_frost, MangaAccent.FROST, currentMangaAccent)
            }
        }
    }
}

@Composable
private fun ThemeOption(titleRes: Int, mode: AppThemeMode, currentMode: AppThemeMode) {
    ListItem(
        modifier = Modifier.clickable { ThemeConfig.setTheme(mode) },
        colors = MaterialTheme.colorScheme.listItem,
        headlineContent = {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            RadioButton(selected = mode == currentMode, onClick = { ThemeConfig.setTheme(mode) })
        }
    )
}

@Composable
private fun SectionLabel(titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MangaPaperOption(
    titleRes: Int,
    paper: MangaPaper,
    currentPaper: MangaPaper,
) {
    ListItem(
        modifier = Modifier.clickable { ThemeConfig.setMangaPaper(paper) },
        colors = MaterialTheme.colorScheme.listItem,
        headlineContent = {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            RadioButton(
                selected = paper == currentPaper,
                onClick = { ThemeConfig.setMangaPaper(paper) },
            )
        },
    )
}

@Composable
private fun MangaAccentOption(
    titleRes: Int,
    accent: MangaAccent,
    currentAccent: MangaAccent,
) {
    val accentColor = mangaAccentColor(accent) ?: MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable { ThemeConfig.setMangaAccent(accent) },
        colors = MaterialTheme.colorScheme.listItem,
        headlineContent = {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(accentColor),
                )
                Spacer(modifier = Modifier.size(8.dp))
                RadioButton(
                    selected = accent == currentAccent,
                    onClick = { ThemeConfig.setMangaAccent(accent) },
                )
            }
        },
    )
}
