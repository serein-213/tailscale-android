// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeListTest {
  private fun node(
      id: String,
      name: String,
      online: Boolean = false,
      user: String = "echo",
      ips: List<String> = listOf("100.64.0.$id"),
      tags: List<String> = emptyList(),
  ) =
      AdminApi.HsNode(
          id = id,
          name = name,
          givenName = name,
          user = AdminApi.HsUser(id = "1", name = user),
          online = online,
          ipAddresses = ips,
          tags = tags)

  private val nodes =
      listOf(
          node("2", "chen-2"),
          node("7", "istoreos", online = true, tags = listOf("tag:router")),
          node("28", "xiaomi-17", online = true, ips = listOf("100.64.0.28", "fd7a::28")),
          node("10", "duck", user = "duck"))

  @Test
  fun sortsOnlineFirstThenAlphabetically() {
    assertEquals(
        listOf("istoreos", "xiaomi-17", "chen-2", "duck"),
        NodeList.sort(nodes).map { it.name })
  }

  @Test
  fun matchesNameUserAddressAndTag() {
    assertEquals(listOf("chen-2"), NodeList.search(nodes, "chen").map { it.name })
    assertEquals(listOf("duck"), NodeList.search(nodes, "duck").map { it.name })
    assertEquals(listOf("xiaomi-17"), NodeList.search(nodes, "100.64.0.28").map { it.name })
    assertEquals(listOf("istoreos"), NodeList.search(nodes, "tag:router").map { it.name })
    assertEquals(listOf("xiaomi-17"), NodeList.search(nodes, "FD7A").map { it.name })
  }

  @Test
  fun blankQueryKeepsEveryDevice() {
    assertEquals(4, NodeList.search(nodes, "  ").size)
    assertEquals(4, NodeList.search(nodes, "").size)
  }

  @Test
  fun unknownQueryMatchesNothing() {
    assertEquals(emptyList<String>(), NodeList.search(nodes, "nope").map { it.name })
  }
}
