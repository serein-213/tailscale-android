// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.theme.AppThemeMode
import com.tailscale.ipn.ui.theme.ThemeConfig
import com.tailscale.ipn.ui.theme.listItem

@Composable
fun ThemeSettingsView(onBack: () -> Unit) {
    val currentThemeMode by ThemeConfig.theme.collectAsState()

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
            ThemeOption(R.string.theme_dracula, AppThemeMode.DRACULA, currentThemeMode)
            ThemeOption(R.string.theme_solarized, AppThemeMode.SOLARIZED, currentThemeMode)
            ThemeOption(R.string.theme_oled, AppThemeMode.OLED, currentThemeMode)
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
