// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

/**
 * Best-effort stale-draft protection for Headscale v0.29.3. Its SetPolicyRequest contains only
 * policy text, with no revision/conditional-write field. Another writer can still win the race
 * between this GET and PUT: this is deliberately not described as an atomic conditional update.
 */
object PolicySaveGuard {
  class Conflict(val latest: AdminApi.HsPolicy) : Exception("The server policy has changed")

  fun save(
      expected: String,
      draft: String,
      fetch: () -> AdminApi.HsPolicy,
      update: (String) -> AdminApi.HsPolicy,
  ): AdminApi.HsPolicy {
    val latest = fetch()
    if (latest.policy != expected) throw Conflict(latest)
    return update(draft)
  }
}
