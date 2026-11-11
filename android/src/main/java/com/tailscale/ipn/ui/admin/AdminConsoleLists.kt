// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import java.time.Instant

internal enum class DevicePresence {
  ALL,
  ONLINE,
  OFFLINE,
}

internal enum class DeviceSort {
  ONLINE_FIRST,
  NAME,
  LAST_SEEN,
}

internal fun filterDevices(
    nodes: List<AdminApi.HsNode>,
    query: String,
    presence: DevicePresence,
    userId: String?,
    tag: String?,
    sort: DeviceSort,
): List<AdminApi.HsNode> {
  val matching =
      NodeList.filter(nodes, query).filter {
        (presence == DevicePresence.ALL || it.online == (presence == DevicePresence.ONLINE)) &&
            (userId == null || it.user?.id == userId) &&
            (tag == null || tag in it.tags || tag in it.validTags)
      }
  return when (sort) {
    DeviceSort.ONLINE_FIRST -> NodeList.sort(matching)
    DeviceSort.NAME ->
        matching.sortedWith(
            compareBy<AdminApi.HsNode> { it.displayName.lowercase() }.thenBy { it.id }
        )
    DeviceSort.LAST_SEEN ->
        matching.sortedWith(
            compareByDescending<AdminApi.HsNode> {
                  it.lastSeen
                      ?.takeUnless { time -> time.startsWith("0001-01-01") }
                      ?.let { time -> runCatching { Instant.parse(time) }.getOrNull() }
                }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.id }
        )
  }
}

/** Pending routes are advertised but not approved; stale approvals are not pending requests. */
internal fun AdminApi.HsNode.pendingRoutes(): List<String> =
    availableRoutes.distinct().filterNot { it in approvedRoutes }

internal data class ConsoleLoadState(
    val loading: Boolean = false,
    val refreshedAt: Instant? = null,
    val error: String? = null,
)

internal data class BulkNodeFailure(val nodeId: String, val name: String, val reason: String)
