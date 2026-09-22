// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import com.tailscale.ipn.ui.admin.PolicyEdit.Step
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's contract: structured edits never reformat or lose the rest of the document, and
 * every result still parses as huJSON.
 */
class PolicyEditTest {
  private val policy =
      """
      {
        "groups": {
          "group:family": [
            "echo@",
            "duck@",
            "fjj@"
          ]
        },
        "hosts": {
          "nas": "100.64.0.9",
          "router": "100.64.0.1"
        },
        "acls": [
          // family may reach the NAS
          { "action": "accept", "src": ["group:family"], "dst": ["nas:8000"] },
          { "action": "deny", "src": ["group:family"], "dst": ["router:*"] },
          { "action": "accept", "src": ["*"], "dst": ["*:*"] }
        ]
      }
      """
          .trimIndent()

  private fun parse(text: String): JsonObject =
      requireNotNull(PolicyDoc.parse(text)) { "result no longer parses:\n$text" }

  private fun acls(text: String): JsonArray = parse(text)["acls"] as JsonArray

  @Test
  fun editsLeaveCommentsAndFormattingAlone() {
    val edited =
        requireNotNull(
            PolicyEdit.replace(policy, listOf(step("acls"), Step.Index(2), step("action")), "\"deny\""))

    assertTrue(edited.contains("// family may reach the NAS"))
    assertEquals(edited.lines().size, policy.lines().size)
    assertEquals("deny", (acls(edited)[2] as JsonObject).getValue("action").jsonPrimitive.content)
  }

  @Test
  fun removesTheMiddleLastAndOnlyElements() {
    val withoutMiddle = requireNotNull(PolicyEdit.remove(policy, listOf(step("acls"), Step.Index(1))))
    assertEquals(2, acls(withoutMiddle).size)
    assertTrue(withoutMiddle.contains("// family may reach the NAS"))

    val withoutLast = requireNotNull(PolicyEdit.remove(policy, listOf(step("acls"), Step.Index(2))))
    assertEquals(2, acls(withoutLast).size)

    val family =
        requireNotNull(
            PolicyEdit.remove(policy, listOf(step("groups"), step("group:family"), Step.Index(1))))
    val members = (parse(family)["groups"] as JsonObject).getValue("group:family") as JsonArray
    assertEquals(2, members.size)
    assertTrue(members.toString().contains("echo@") && members.toString().contains("fjj@"))
  }

  @Test
  fun removesObjectMembersAndKeepsTheirNeighbours() {
    val edited = requireNotNull(PolicyEdit.remove(policy, listOf(step("hosts"), step("nas"))))
    val hosts = parse(edited)["hosts"] as JsonObject
    assertEquals(setOf("router"), hosts.keys)
    assertEquals("100.64.0.1", hosts.getValue("router").jsonPrimitive.content)
  }

  @Test
  fun replacesValuesInPlace() {
    val edited =
        requireNotNull(
            PolicyEdit.replace(policy, listOf(step("hosts"), step("nas")), "\"10.0.0.5\""))
    val hosts = parse(edited)["hosts"] as JsonObject
    assertEquals("10.0.0.5", hosts.getValue("nas").jsonPrimitive.content)
    assertEquals("100.64.0.1", hosts.getValue("router").jsonPrimitive.content)
    assertTrue(edited.contains("  \"hosts\": {"))
  }

  @Test
  fun appendsToArraysAndArraysOnly() {
    val rule = """{ "action": "accept", "src": ["group:family"], "dst": ["nas:443"] }"""
    val edited = requireNotNull(PolicyEdit.appendTo(policy, listOf(step("acls")), rule))
    assertEquals(4, acls(edited).size)
    assertTrue(edited.contains("// family may reach the NAS"))
    val appended = (acls(edited)[3] as JsonObject).getValue("dst") as JsonArray
    assertEquals("nas:443", appended[0].jsonPrimitive.content)

    assertNull(PolicyEdit.appendTo(policy, listOf(step("hosts")), rule))
  }

  @Test
  fun replaceArrayKeepsTheOriginalShape() {
    val inline =
        requireNotNull(
            PolicyEdit.replaceArray(
                policy, listOf(step("acls"), Step.Index(0), step("dst")), listOf("nas:8000", "nas:443")))
    // The one-liner rule stays a one-liner, and its neighbours are untouched.
    assertTrue(inline.lines().any { it.trim().endsWith("{ \"action\": \"accept\", \"src\": [\"group:family\"], \"dst\": [\"nas:8000\", \"nas:443\"] },") })
    assertEquals(3, acls(inline).size)
    assertEquals(1, inline.lines().count { it.contains("\"router:*\"") })

    val multi =
        requireNotNull(
            PolicyEdit.replaceArray(
                policy, listOf(step("groups"), step("group:family")), listOf("echo@", "game@")))
    val members = (parse(multi)["groups"] as JsonObject).getValue("group:family") as JsonArray
    assertEquals(2, members.size)
    // Multi-line stays multi-line: one value per line.
    assertTrue(multi.lines().any { it.trim() == "\"echo@\"," })
    assertTrue(multi.lines().any { it.trim() == "\"game@\"" })
  }

  @Test
  fun quotedValuesCannotBreakOut() {
    val edited = requireNotNull(PolicyEdit.replace(policy, listOf(step("hosts"), step("nas")), PolicyEdit.quote("a}b\n\"c\"")))
    val hosts = parse(edited)["hosts"] as JsonObject
    assertEquals("a}b\n\"c\"", hosts.getValue("nas").jsonPrimitive.content)
  }

  @Test
  fun handlesMinifiedInput() {
    val tiny = """{"a":[1,2,3],"b":{"c":"d"}}"""
    val removed = requireNotNull(PolicyEdit.remove(tiny, listOf(step("a"), Step.Index(1))))
    assertEquals("[1,3]", (parse(removed)["a"] as JsonArray).toString())
    val edited = requireNotNull(PolicyEdit.replace(tiny, listOf(step("b"), step("c")), "\"e\""))
    assertEquals("e", (parse(edited)["b"] as JsonObject).getValue("c").jsonPrimitive.content)
  }

  @Test
  fun refusesPathsThatDoNotAddressAValue() {
    assertNull(PolicyEdit.rangeOf(policy, listOf(step("nope"))))
    assertNull(PolicyEdit.remove(policy, listOf(step("acls"), Step.Index(9))))
    assertNull(PolicyEdit.replace("not json", listOf(step("hosts")), "\"x\""))
    assertNull(PolicyEdit.replace(policy, listOf(step("acls"), Step.Index(9)), "\"x\""))
    assertNull(PolicyEdit.appendTo(policy, listOf(step("acls"), Step.Index(0), step("action")), "1"))
  }

  private fun step(name: String) = PolicyEdit.Step.Key(name)
}
