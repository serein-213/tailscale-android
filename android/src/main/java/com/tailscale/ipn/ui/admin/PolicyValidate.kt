// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Catches the rule mistakes headscale is known to reject, before the round trip.
 *
 * Only rules taken from the server's own messages are checked - dst entries are parsed as
 * alias-with-ports ("port range \"self\": invalid first integer"), and autogroup:self belongs on the
 * dst side only ("not valid on the src side of a rule"). Anything less certain stays the server's
 * call: it validates against the policy's own tests section and refuses to store a policy that
 * fails them.
 */
object PolicyValidate {
  /** A rule-level problem, with the index of the offending rule in acls. */
  data class Problem(val ruleIndex: Int, val message: String)

  private val numericPort = Regex("^\\d+(-\\d+)?$")

  fun problems(policy: JsonObject): List<Problem> {
    val found = mutableListOf<Problem>()
    // ssh rules need users: "users must be specified" when they are missing or empty.
    (policy["ssh"] as? JsonArray)?.forEachIndexed { index, element ->
      val rule = element as? JsonObject ?: return@forEachIndexed
      if (PolicyDoc.stringsOf(rule["users"]).isEmpty()) {
        found += Problem(index, "ssh 规则必须指定 users（例如 root 或 autogroup:nonroot）")
      }
    }
    val acls = policy["acls"] as? JsonArray ?: return found
    acls.forEachIndexed { index, element ->
      val rule = element as? JsonObject ?: return@forEachIndexed
      val action = rule["action"]?.jsonPrimitive?.contentOrNull
      if (action != null && action != "accept" && action != "deny") {
        found += Problem(index, "action 只能是 accept 或 deny：$action")
      }
      PolicyDoc.stringsOf(rule["src"]).forEach { value ->
        if (value == "autogroup:self") {
          found += Problem(index, "autogroup:self 不能用在 src 侧")
        }
      }
      PolicyDoc.stringsOf(rule["dst"]).forEach { value ->
        val port =
            when {
              value.contains("]:") -> value.substringAfterLast("]:")
              value.contains(':') -> value.substringAfterLast(':')
              else -> ""
            }
        when {
          port.isEmpty() -> found += Problem(index, "dst 必须带端口，例如 $value:*")
          port == "*" || numericPort.matches(port) -> Unit
          // "autogroup:self" reads as alias "autogroup" with port "self"; the intent is almost
          // always the whole port range of that alias.
          value.startsWith("autogroup:") ->
              found += Problem(index, "dst 少了端口：$value（要写 $value:* 才是该自动组的全部端口）")
          else -> found += Problem(index, "dst 的端口必须是数字或 *：$value")
        }
      }
    }
    return found
  }
}
