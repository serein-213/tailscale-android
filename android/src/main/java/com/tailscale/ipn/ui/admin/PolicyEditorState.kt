// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** The editing session belongs to the console, not to the currently visible tab. */
class PolicyEditorState(source: String = "", draft: String = source, editing: Boolean = false) {
  var source by mutableStateOf(source)
    private set

  var draft by mutableStateOf(draft)
  var editing by mutableStateOf(editing)
  val dirty: Boolean
    get() = draft != source

  fun receive(text: String) {
    if (!dirty) draft = text
    source = text
  }

  fun saved(text: String) {
    source = text
    draft = text
    editing = false
  }

  companion object {
    val Saver =
        listSaver<PolicyEditorState, Any>(
            save = { listOf(it.source, it.draft, it.editing) },
            restore = { PolicyEditorState(it[0] as String, it[1] as String, it[2] as Boolean) },
        )
  }
}
