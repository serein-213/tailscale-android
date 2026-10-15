// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import com.tailscale.ipn.ui.model.Tailcfg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DNSConfigMonitorTest {
  @Test
  fun retriesAndRefreshesWithoutIdentityChangeAndStopsOffScreen() = runTest {
    val identity = MutableStateFlow<Pair<String, String>?>("node" to "tailnet")
    var calls = 0
    var config: Tailcfg.DNSConfig? = null
    val fresh = Tailcfg.DNSConfig()
    val monitor =
        DNSConfigMonitor(
            backgroundScope,
            identity,
            fetch = {
              calls++
              if (calls == 1) Result.failure(Exception("offline")) else Result.success(fresh)
            },
            onConfig = { config = it },
            onFailure = {},
        )
    monitor.start()
    runCurrent()
    assertEquals(1, calls)
    advanceTimeBy(5_000)
    runCurrent()
    assertSame(fresh, config)
    monitor.stop()
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(2, calls)
    monitor.start()
    runCurrent()
    assertEquals(3, calls)
    identity.value = null
    runCurrent()
    assertNull(config)
  }

  @Test
  fun oldAccountRequestCannotPopulateNewAccount() = runTest {
    val identity = MutableStateFlow<Pair<String, String>?>("old" to "tailnet")
    val oldRequest = CompletableDeferred<Result<Tailcfg.DNSConfig>>()
    val fresh = Tailcfg.DNSConfig()
    var calls = 0
    var config: Tailcfg.DNSConfig? = null
    val monitor =
        DNSConfigMonitor(
            backgroundScope,
            identity,
            fetch = {
              calls++
              if (calls == 1) oldRequest.await() else Result.success(fresh)
            },
            onConfig = { config = it },
            onFailure = {},
        )
    monitor.start()
    runCurrent()
    identity.value = "new" to "tailnet"
    runCurrent()
    oldRequest.complete(Result.success(Tailcfg.DNSConfig()))
    runCurrent()
    assertSame(fresh, config)
  }
}
