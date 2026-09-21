// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test for the Headscale API payloads the in-app admin console parses. Fixtures are
 * verbatim responses from a Headscale v0.29.3 server, with key material redacted.
 */
class AdminApiParsingTest {
  private val nodesJson =
      """
      {"nodes":[{"id":"2","machineKey":"","nodeKey":"","discoKey":"","ipAddresses":["100.64.0.2","fd7a:115c:a1e0::2"],"name":"Chen-2","user":{"id":"1","name":"echo","displayName":"","email":"","providerId":"","provider":"","profilePicUrl":""},"lastSeen":"2026-09-19T05:51:59.622504076Z","expiry":null,"preAuthKey":"","createdAt":"0001-01-01T00:00:00Z","registerMethod":"REGISTER_METHOD_AUTH_KEY","givenName":"chen-2","online":false,"approvedRoutes":[],"availableRoutes":[],"subnetRoutes":[],"tags":[]}]}
      """
          .trimIndent()

  private val preAuthKeysJson =
      """
      {"preAuthKeys":[{"user":{"id":"2","name":"duck","createdAt":"2025-09-26T06:34:09.254955375Z","displayName":"","email":"","providerId":"","provider":"","profilePicUrl":""},"id":"12","key":"hskey-auth-EXAMPLE-00000000000000000000000","reusable":true,"ephemeral":false,"used":false,"expiration":"2026-02-25T07:47:00Z","createdAt":"2026-02-25T06:47:59.948224731Z","aclTags":[]}]}
      """
          .trimIndent()

  @Test
  fun parsesNodeList() {
    val nodes = AdminApi.parseNodes(nodesJson)
    assertEquals(1, nodes.size)

    val node = nodes.single()
    assertEquals("2", node.id)
    assertEquals("chen-2", node.givenName)
    assertEquals("echo", node.user?.name)
    assertEquals(listOf("100.64.0.2", "fd7a:115c:a1e0::2"), node.ipAddresses)
    assertFalse(node.online)
    assertNull(node.expiry)
    assertEquals("REGISTER_METHOD_AUTH_KEY", node.registerMethod)
    // validTags is absent from the wire payload; defaults must still be usable.
    assertTrue(node.validTags.isEmpty())
    assertTrue(node.tags.isEmpty())
    assertEquals("2026-09-19T05:51:59.622504076Z", node.lastSeen)
  }

  @Test
  fun displayNamePrefersGivenName() {
    val node = AdminApi.parseNodes(nodesJson).single()
    assertEquals("chen-2", node.displayName)

    val unnamed = AdminApi.HsNode(id = "7", name = "host-7")
    assertEquals("host-7", unnamed.displayName)

    val bare = AdminApi.HsNode(id = "9")
    assertEquals("9", bare.displayName)
  }

  @Test
  fun parsesPreAuthKeys() {
    val keys = AdminApi.parsePreAuthKeys(preAuthKeysJson)
    assertEquals(1, keys.size)

    val key = keys.single()
    assertEquals("12", key.id)
    assertEquals("duck", key.user?.name)
    assertTrue(key.reusable)
    assertFalse(key.ephemeral)
    assertEquals("2026-02-25T07:47:00Z", key.expiration)
    assertTrue(key.key.startsWith("hskey-auth-"))
  }
}
