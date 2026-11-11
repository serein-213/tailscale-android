// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminConsoleListsTest {
  private val devices =
      listOf(
          AdminApi.HsNode(
              id = "1",
              name = "Zulu",
              online = true,
              user = AdminApi.HsUser(id = "a", name = "same"),
              tags = listOf("tag:server"),
              lastSeen = "2025-01-01T10:00:00Z",
          ),
          AdminApi.HsNode(
              id = "2",
              name = "Alpha",
              online = false,
              user = AdminApi.HsUser(id = "b", name = "same"),
              validTags = listOf("tag:server"),
              lastSeen = "2025-01-01T12:00:00+01:00",
          ),
          AdminApi.HsNode(id = "3", name = "Beta", lastSeen = "invalid"),
          AdminApi.HsNode(id = "4", name = "Gamma"),
      )

  @Test
  fun filtersCombineAndUseOwnerIdentity() {
    assertEquals(
        listOf("2"),
        filterDevices(
                devices,
                " alpha ",
                DevicePresence.OFFLINE,
                "b",
                "tag:server",
                DeviceSort.NAME,
            )
            .map { it.id },
    )
    assertEquals(
        emptyList<String>(),
        filterDevices(
                devices,
                "alpha",
                DevicePresence.ONLINE,
                "b",
                "tag:server",
                DeviceSort.NAME,
            )
            .map { it.id },
    )
    assertEquals(
        listOf("2", "1"),
        filterDevices(
                devices,
                "",
                DevicePresence.ALL,
                null,
                "tag:server",
                DeviceSort.NAME,
            )
            .map { it.id },
    )
  }

  @Test
  fun lastSeenSortUsesInstantsAndKeepsUnknownAtEnd() {
    assertEquals(
        listOf("2", "1", "3", "4"),
        filterDevices(
                devices,
                "",
                DevicePresence.ALL,
                null,
                null,
                DeviceSort.LAST_SEEN,
            )
            .map { it.id },
    )
  }

  @Test
  fun nameSortDoesNotForceOnlineDevicesFirst() {
    assertEquals(
        listOf("2", "3", "4", "1"),
        filterDevices(
                devices,
                "",
                DevicePresence.ALL,
                null,
                null,
                DeviceSort.NAME,
            )
            .map { it.id },
    )
  }

  @Test
  fun zeroTimestampIsUnknownRatherThanAnAncientLastSeen() {
    val nodes =
        listOf(
            AdminApi.HsNode(id = "1", name = "Zulu", lastSeen = "0001-01-01T00:00:00Z"),
            AdminApi.HsNode(id = "2", name = "Alpha"),
        )
    assertEquals(
        listOf("2", "1"),
        filterDevices(
                nodes,
                "",
                DevicePresence.ALL,
                null,
                null,
                DeviceSort.LAST_SEEN,
            )
            .map { it.id },
    )
  }

  @Test
  fun pendingRoutesExcludeDuplicatesAndStaleApprovals() {
    val node =
        AdminApi.HsNode(
            availableRoutes = listOf("10.0.0.0/24", "10.0.1.0/24", "10.0.1.0/24"),
            approvedRoutes = listOf("10.0.0.0/24", "192.168.0.0/24"),
        )
    assertEquals(listOf("10.0.1.0/24"), node.pendingRoutes())
    assertEquals(emptyList<String>(), node.copy(availableRoutes = emptyList()).pendingRoutes())
  }
}
