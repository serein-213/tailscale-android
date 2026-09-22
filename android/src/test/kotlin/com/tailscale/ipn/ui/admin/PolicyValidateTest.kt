// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case here comes from a message the server actually produced. */
class PolicyValidateTest {
  private fun policy(acls: String): JsonObject =
      requireNotNull(PolicyDoc.parse("""{ "acls": $acls }"""))

  @Test
  fun acceptsWhatTheServerAccepted() {
    // The rule the add-rule button appends, and the wildcards the live policy already uses.
    val ok = policy("""[ { "action": "accept", "src": [], "dst": [] }, { "action": "accept", "src": ["*"], "dst": ["*:*"] }, { "action": "deny", "src": ["tag:x"], "dst": ["vps:22", "nas:5000-5010", "autogroup:internet:*"] } ]""")
    assertEquals(emptyList<PolicyValidate.Problem>(), PolicyValidate.problems(ok))
  }

  @Test
  fun catchesTheBareAliasOnDstTheServerRejected() {
    // dst="autogroup:self": port range "self": invalid first integer
    val problems = PolicyValidate.problems(policy("""[ { "action": "accept", "src": ["*"], "dst": ["autogroup:self"] } ]"""))
    assertEquals(1, problems.size)
    assertEquals(0, problems.single().ruleIndex)
    assertTrue(problems.single().message.contains("autogroup:self:*"))
  }

  @Test
  fun catchesAutogroupSelfOnSrc() {
    // "autogroup:self" not valid on the src side of a rule
    val problems = PolicyValidate.problems(policy("""[ { "action": "accept", "src": ["autogroup:self"], "dst": ["*:*"] } ]"""))
    assertEquals(1, problems.size)
    assertTrue(problems.single().message.contains("src"))
  }

  @Test
  fun reportsTheOffendingRuleAndOtherFields() {
    val problems = PolicyValidate.problems(policy("""[ { "action": "accept", "src": [], "dst": [] }, { "action": "maybe", "src": [], "dst": ["nas"] } ]"""))
    assertEquals(2, problems.size)
    assertTrue(problems.all { it.ruleIndex == 1 })
    assertTrue(problems.any { it.message.contains("accept") })
    assertTrue(problems.any { it.message.contains("端口") })
  }
}
