// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import java.time.Instant

/** What a pre-auth key can still be used for, as far as the app can tell. */
enum class KeyStatus {
  ALL,
  USABLE,
  USED,
  EXPIRED,
}

/**
 * A key is expired once its expiration has passed, consumed when it was used and cannot be reused,
 * usable otherwise. Headscale reports "no expiry" as the zero timestamp, which counts as expired:
 * such keys were created without one and never worked.
 */
fun AdminApi.HsPreAuthKey.status(now: Instant): KeyStatus =
    when {
      isExpired(now) -> KeyStatus.EXPIRED
      used && !reusable -> KeyStatus.USED
      else -> KeyStatus.USABLE
    }

fun AdminApi.HsPreAuthKey.isExpired(now: Instant): Boolean {
  val expiration = expiration?.takeIf { it.isNotBlank() } ?: return false
  val instant = runCatching { Instant.parse(expiration) }.getOrNull() ?: return false
  return !instant.isAfter(now)
}

/** Keys matching both the status and (optionally) the owning user, newest expiration first. */
fun filterPreAuthKeys(
    keys: List<AdminApi.HsPreAuthKey>,
    status: KeyStatus,
    userId: String?,
    now: Instant,
): List<AdminApi.HsPreAuthKey> =
    keys.filter { key ->
          (status == KeyStatus.ALL || key.status(now) == status) &&
              (userId == null || key.user?.id == userId)
        }
        .sortedByDescending { it.expiration ?: "" }
