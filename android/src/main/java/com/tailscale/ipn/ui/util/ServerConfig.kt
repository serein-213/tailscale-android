// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import android.content.Context
import com.tailscale.ipn.App
import com.tailscale.ipn.ui.Links
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ServerConfig {
    private const val PREF_KEY_CUSTOM_ADMIN_URL = "custom_admin_url"
    private val _customAdminUrl = MutableStateFlow<String?>(null)
    val customAdminUrl: StateFlow<String?> = _customAdminUrl

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        _customAdminUrl.value = prefs.getString(PREF_KEY_CUSTOM_ADMIN_URL, null)
    }

    fun setCustomAdminUrl(url: String?) {
        val cleanUrl = if (url.isNullOrBlank()) null else url.trim()
        _customAdminUrl.value = cleanUrl
        val prefs = App.get().getSharedPreferences("unencrypted_preferences", Context.MODE_PRIVATE)
        if (cleanUrl == null) {
            prefs.edit().remove(PREF_KEY_CUSTOM_ADMIN_URL).apply()
        } else {
            prefs.edit().putString(PREF_KEY_CUSTOM_ADMIN_URL, cleanUrl).apply()
        }
    }

    /**
     * Returns the Admin URL to use. If a custom one is set, returns that.
     * Otherwise returns the default Tailscale Admin URL.
     */
    fun getAdminUrl(): String {
        return _customAdminUrl.value ?: Links.ADMIN_URL
    }
}
