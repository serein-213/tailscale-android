// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

/**
 * Device list helpers. Online devices sort first (that is what people look for), then A→Z; the
 * search matches name, owning user, addresses and tags, since any of those is what someone has in
 * mind when hunting for a device.
 */
object NodeList {
  fun sort(nodes: List<AdminApi.HsNode>): List<AdminApi.HsNode> =
      nodes.sortedWith(
          compareByDescending<AdminApi.HsNode> { it.online }
              .thenBy { it.displayName.lowercase() })

  fun filter(nodes: List<AdminApi.HsNode>, query: String): List<AdminApi.HsNode> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return nodes
    return nodes.filter { node ->
      node.displayName.lowercase().contains(needle) ||
          node.name.lowercase().contains(needle) ||
          node.user?.name?.lowercase()?.contains(needle) == true ||
          node.ipAddresses.any { it.lowercase().contains(needle) } ||
          node.tags.any { it.lowercase().contains(needle) } ||
          node.validTags.any { it.lowercase().contains(needle) }
    }
  }

  fun search(nodes: List<AdminApi.HsNode>, query: String): List<AdminApi.HsNode> =
      sort(filter(nodes, query))
}
