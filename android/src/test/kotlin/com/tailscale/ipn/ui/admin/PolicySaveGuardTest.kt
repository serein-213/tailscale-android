// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class PolicySaveGuardTest {
  @Test
  fun staleTextIncludingFormattingRefusesWriteAndReturnsLatest() {
    var writes = 0
    val latest = AdminApi.HsPolicy("{ }", "new timestamp")
    try {
      PolicySaveGuard.save(
          "{}",
          "draft",
          { latest },
          {
            writes++
            AdminApi.HsPolicy(it)
          },
      )
      fail("Stale saves must not write")
    } catch (e: PolicySaveGuard.Conflict) {
      assertSame(latest, e.latest)
    }
    assertEquals(0, writes)
  }

  @Test
  fun failedPreflightNeverWrites() {
    var writes = 0
    val failure = IOException("offline")
    try {
      PolicySaveGuard.save(
          "original",
          "draft",
          { throw failure },
          {
            writes++
            AdminApi.HsPolicy(it)
          },
      )
      fail("Fetch failure must be propagated")
    } catch (e: IOException) {
      assertSame(failure, e)
    }
    assertEquals(0, writes)
  }

  @Test
  fun writeFailureRemainsAnError() {
    val failure = IOException("rejected")
    try {
      PolicySaveGuard.save(
          "original",
          "draft",
          { AdminApi.HsPolicy("original") },
          { throw failure },
      )
      fail("Write failure must be propagated")
    } catch (e: IOException) {
      assertSame(failure, e)
    }
  }
}
