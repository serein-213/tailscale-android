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

  internal fun keyFor(serverId: String): String =
      PREF_KEY +
          ":" +
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(serverId.toByteArray(Charsets.UTF_8))
              .joinToString("") { "%02x".format(it) }

  fun remember(serverId: String, text: String) {
    if (text.isBlank()) return
    runCatching {
      App.get().getEncryptedPrefs().edit().putString(keyFor(serverId), text).apply()
    }
        .onFailure { TSLog.w(TAG, "could not store policy backup: $it") }
  }

  fun last(serverId: String): String? = runCatching {
    App.get().getEncryptedPrefs().getString(keyFor(serverId), null)?.takeIf { it.isNotBlank() }
  }.onFailure { TSLog.w(TAG, "could not read policy backup: $it") }.getOrNull()
}
