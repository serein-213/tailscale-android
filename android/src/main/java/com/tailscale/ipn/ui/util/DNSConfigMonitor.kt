// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import com.tailscale.ipn.ui.model.Tailcfg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** DNS-only updates have no bus payload; refresh while this page is visible and retry failures. */
class DNSConfigMonitor(
    private val scope: CoroutineScope,
    private val identity: Flow<Pair<String, String>?>,
    private val fetch: suspend () -> Result<Tailcfg.DNSConfig>,
    private val onConfig: (Tailcfg.DNSConfig?) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val intervalMillis: Long = 5_000,
) {
  private var job: Job? = null

  fun start() {
    if (job?.isActive == true) return
    job = scope.launch {
      identity.distinctUntilChanged().collectLatest { current ->
        onConfig(null)
        if (current == null) return@collectLatest
        while (isActive) {
          fetch().onSuccess(onConfig).onFailure(onFailure)
          delay(intervalMillis)
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }
}
