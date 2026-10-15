// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminActionGateTest {
  @Test
  fun repeatedRouteWritesCannotOverwriteEachOther() = runTest {
    val gate = AdminActionGate()
    val response = CompletableDeferred<Unit>()
    var approved = emptyList<String>()
    val first = async {
      gate.run {
        response.await()
        approved = listOf("A")
      }
    }
    runCurrent()
    assertFalse(gate.run { approved = listOf("B") })
    response.complete(Unit)
    assertTrue(first.await())
    assertEquals(listOf("A"), approved)
    assertTrue(gate.run { approved = approved + "B" })
    assertEquals(listOf("A", "B"), approved)
  }

  @Test
  fun cancellationReleasesMutationGate() = runTest {
    val gate = AdminActionGate()
    val started = async { gate.run { CompletableDeferred<Unit>().await() } }
    runCurrent()
    started.cancel()
    started.join()
    assertTrue(gate.run {})
  }
}
