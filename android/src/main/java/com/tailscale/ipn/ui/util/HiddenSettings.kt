// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import android.content.Context
import com.tailscale.ipn.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which settings rows the user hid by long-pressing them. Kept in plain preferences: it is UI
 * state, not a secret, and it must be readable before the tunnel starts.
 */
object HiddenSettings {
  private const val PREFS = "settings_visibility"
  private const val KEY_HIDDEN_ROWS = "hidden_rows"

  private val _hidden = MutableStateFlow<Set<String>>(emptySet())
  val hidden: StateFlow<Set<String>> = _hidden

  /** Long-pressing About reveals hidden rows so they can be brought back. */
  private val _revealing = MutableStateFlow(false)
  val revealing: StateFlow<Boolean> = _revealing

  fun toggleReveal() {
    _revealing.value = !_revealing.value
  }

  /** Long-press toggles straight away: hiding needs no confirmation. */
  fun toggleHidden(id: String) {
    setHidden(id, id !in _hidden.value)
  }

  fun init(context: Context) {
    _hidden.value = load(context)
  }

  fun setHidden(id: String, hidden: Boolean) {
    val next = if (hidden) _hidden.value + id else _hidden.value - id
    if (next == _hidden.value) return
    _hidden.value = next
    App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_HIDDEN_ROWS, next).apply()
  }

  private fun load(context: Context): Set<String> =
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          .getStringSet(KEY_HIDDEN_ROWS, null)
          ?.toSet()
          .orEmpty()
}
