// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyFilterTest {
  private val now = Instant.parse("2026-09-21T12:00:00Z")

  private fun key(
      id: String,
      userId: String = "1",
      userName: String = "echo",
      expiration: String? = "2026-12-01T00:00:00Z",
      used: Boolean = false,
      reusable: Boolean = false,
  ) =
      AdminApi.HsPreAuthKey(
          id = id,
          key = "hskey-auth-$id",
          user = AdminApi.HsUser(id = userId, name = userName),
          reusable = reusable,
          used = used,
          expiration = expiration)

  @Test
  fun classifiesUsableUsedAndExpired() {
    assertEquals(KeyStatus.USABLE, key("a").status(now))
    assertEquals(KeyStatus.USED, key("b", used = true).status(now))
    // A reusable key that was used is still usable.
    assertEquals(KeyStatus.USABLE, key("c", used = true, reusable = true).status(now))
    assertEquals(KeyStatus.EXPIRED, key("d", expiration = "2026-01-01T00:00:00Z").status(now))
    // Headscale's "no expiry" zero timestamp never worked; it reads as expired.
    assertEquals(KeyStatus.EXPIRED, key("e", expiration = "0001-01-01T00:00:00Z").status(now))
    assertEquals(KeyStatus.USABLE, key("f", expiration = null).status(now))
    assertEquals(
        KeyStatus.EXPIRED,
        key("g", expiration = "2026-01-01T00:00:00Z", used = true, reusable = true).status(now))
  }

  @Test
  fun filtersByStatusAndOwner() {
    val keys =
        listOf(
            key("a"),
            key("b", userId = "2", userName = "duck"),
            key("c", expiration = "2026-01-01T00:00:00Z"),
            key("d", used = true))

    // Sorted by expiration, newest first, so the long-expired key lands last.
    assertEquals(listOf("a", "b", "d", "c"), filterPreAuthKeys(keys, KeyStatus.ALL, null, now).map { it.id })
    assertEquals(listOf("a", "b"), filterPreAuthKeys(keys, KeyStatus.USABLE, null, now).map { it.id })
    assertEquals(listOf("c"), filterPreAuthKeys(keys, KeyStatus.EXPIRED, null, now).map { it.id })
    assertEquals(listOf("d"), filterPreAuthKeys(keys, KeyStatus.USED, null, now).map { it.id })
    assertEquals(listOf("b"), filterPreAuthKeys(keys, KeyStatus.ALL, "2", now).map { it.id })
  }

  @Test
  fun sortsByExpirationDescending() {
    val keys =
        listOf(
            key("soon", expiration = "2026-10-01T00:00:00Z"),
            key("later", expiration = "2027-01-01T00:00:00Z"),
            key("none", expiration = null))
    assertEquals(listOf("later", "soon", "none"), filterPreAuthKeys(keys, KeyStatus.ALL, null, now).map { it.id })
  }
}
