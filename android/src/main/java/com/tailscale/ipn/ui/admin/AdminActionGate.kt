// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import kotlinx.coroutines.sync.Mutex

/** Drop repeated actions until the current mutation and its state update have finished. */
class AdminActionGate {
  private val mutex = Mutex()

  suspend fun run(action: suspend () -> Unit): Boolean {
    if (!mutex.tryLock()) return false
    try {
      action()
      return true
    } finally {
      mutex.unlock()
    }
  }
}
