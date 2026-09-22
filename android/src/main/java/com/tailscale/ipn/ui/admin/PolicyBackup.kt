// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import com.tailscale.ipn.App
import com.tailscale.ipn.util.TSLog

/**
 * Keeps the last policy that was fetched from, or successfully written to, the server. A policy
 * mistake can cut devices off, so the editor always offers this version as a way back.
 */
object PolicyBackup {
  private const val TAG = "PolicyBackup"
  private const val PREF_KEY = "last_applied_policy"

  fun remember(text: String) {
    if (text.isBlank()) return
    runCatching {
          App.get().getEncryptedPrefs().edit().putString(PREF_KEY, text).apply()
        }
        .onFailure { TSLog.w(TAG, "could not store policy backup: $it") }
  }

  fun last(): String? =
      runCatching { App.get().getEncryptedPrefs().getString(PREF_KEY, null)?.takeIf { it.isNotBlank() } }
          .onFailure { TSLog.w(TAG, "could not read policy backup: $it") }
          .getOrNull()
}
