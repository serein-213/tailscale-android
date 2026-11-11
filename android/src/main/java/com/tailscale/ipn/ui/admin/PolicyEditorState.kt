// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** The editing session belongs to the console, not to the currently visible tab. */
class PolicyEditorState(
    source: String = "",
    draft: String = source,
    editing: Boolean = false,
    baseline: String = source,
) {
  var source by mutableStateOf(source)
    private set

  /** Original server text for this draft; refreshing must not silently rebase unsaved edits. */
  var baseline by mutableStateOf(baseline)
    private set

  var draft by mutableStateOf(draft)
  var editing by mutableStateOf(editing)
  val dirty: Boolean
    get() = draft != source

  fun receive(text: String) {
    if (!dirty) {
      draft = text
      baseline = text
    }
    source = text
    if (!dirty) baseline = text
  }

  fun saved(text: String) {
    source = text
    discardToSource()
  }

  /** Explicitly accept the latest received server policy as a fresh editing baseline. */
  fun discardToSource() {
    baseline = source
    draft = source
    editing = false
  }

  companion object {
    val Saver =
        listSaver<PolicyEditorState, Any>(
            save = { listOf(it.source, it.draft, it.editing, it.baseline) },
            restore = {
              PolicyEditorState(
                  it[0] as String,
                  it[1] as String,
                  it[2] as Boolean,
                  it[3] as String,
              )
            },
        )
  }
}
