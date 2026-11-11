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

/** 0.0.0.0/0 and ::/0 are the two halves of "be an exit node"; everything else is a subnet. */
internal fun isExitRoute(route: String): Boolean = route == "0.0.0.0/0" || route == "::/0"

/** Every route worth a row: advertised now, or approved earlier. */
internal fun AdminApi.HsNode.allRoutes(): List<String> =
    (availableRoutes + approvedRoutes).distinct().sorted()

/** True only when every exit route the device has is approved; half an exit node is not one. */
internal fun AdminApi.HsNode.exitRoutesApproved(): Boolean {
  val exit = allRoutes().filter(::isExitRoute)
  return exit.isNotEmpty() && exit.all { it in approvedRoutes }
}

/**
 * The approved set after toggling [route]. Exit routes travel together, so approving the IPv4 half
 * can never leave the IPv6 half behind (and a half-approved device is repaired by one tap).
 */
internal fun AdminApi.HsNode.withRouteToggled(route: String): List<String> {
  val all = allRoutes()
  val next =
      if (isExitRoute(route)) {
        val exit = all.filter(::isExitRoute).toSet()
        if (exit.all { it in approvedRoutes }) approvedRoutes - exit else approvedRoutes + exit
      } else if (route in approvedRoutes) {
        approvedRoutes - route
      } else {
        approvedRoutes + route
      }
  return all.filter { it in next }
}

internal data class ConsoleLoadState(
    val loading: Boolean = false,
    val refreshedAt: Instant? = null,
    val error: String? = null,
)

internal data class BulkNodeFailure(val nodeId: String, val name: String, val reason: String)
