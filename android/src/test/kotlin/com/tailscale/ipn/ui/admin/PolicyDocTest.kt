// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertSame
import org.junit.Test

class PolicyDocTest {
  // Same shapes as a live policy: an admin-console bookkeeping key, lists and nested objects.
  private val huJson =
      """
      {
        // everyone in the family
        "groups": { "group:family": ["a@example.com", "b@example.com"], },
        "hosts": { "nas": "100.64.0.9", /* inline */ "router": "100.64.0.7" },
        "acls": [
          { "action": "accept", "src": ["group:family"], "dst": ["nas:445"], "#ha-meta": {"id": 7} },
          { "action": "accept", "src": ["*"], "dst": ["*:*"], },
        ],
        "ssh": [ { "action": "accept", "src": ["group:family"], "dst": ["router"], "users": ["root"] } ],
        "tests": [ { "src": "a@example.com", "accept": ["nas:445"] } ],
        "randomizeClientPort": true,
      }
      """
          .trimIndent()

  @Test
  fun parsesHuJsonWithCommentsAndTrailingCommas() {
    val doc = PolicyDoc.parse(huJson)
    assertEquals(listOf("groups", "hosts", "acls", "ssh", "tests", "randomizeClientPort"), doc!!.keys.toList())
    assertEquals(2, doc["acls"]!!.jsonArray.size)
  }

  @Test
  fun keepsDoubleSlashesInsideStrings() {
    val text = """{"url": "https://h.example.com/admin", "n": 1}"""
    assertEquals(text, PolicyDoc.stripComments(text))

    // A comment after a string that contains "//" is still removed.
    assertEquals(
        """{"url": "https://x" }""",
        PolicyDoc.stripComments("""{"url": "https://x" } // trailing""").trimEnd())
  }

  @Test
  fun stripsOnlyStructuralTrailingCommas() {
    assertEquals(
        """{"a": [1, 2], "b": "x,y"}""",
        PolicyDoc.stripTrailingCommas("""{"a": [1, 2], "b": "x,y",}"""))
    assertEquals(
        """{"a": [1, 2] }""", PolicyDoc.stripTrailingCommas("""{"a": [1, 2,], }"""))
  }

  @Test
  fun rejectsNonObjects() {
    assertNull(PolicyDoc.parse("not json at all"))
    assertNull(PolicyDoc.parse("[1, 2, 3]"))
    assertEquals(2, PolicyDoc.parse("""{"a": 1, "b": 2}""")!!.size)
  }

  @Test
  fun listsSectionsKnownFirstThenExtras() {
    val doc = PolicyDoc.parse(huJson)!!
    assertEquals(listOf("acls", "groups", "hosts", "ssh", "tests", "randomizeClientPort"), PolicyDoc.sections(doc))
  }

  @Test
  fun hidesBookkeepingKeys() {
    val rule = PolicyDoc.parse(huJson)!!["acls"]!!.jsonArray.first() as JsonObject
    assertEquals(listOf("action", "src", "dst"), PolicyDoc.ruleFields(rule).map { it.first })
  }

  @Test
  fun readsStringsAndCounts() {
    val doc = PolicyDoc.parse(huJson)!!
    val firstRule = doc["acls"]!!.jsonArray.first() as JsonObject
    assertEquals(listOf("group:family"), PolicyDoc.stringsOf(firstRule["src"]))
    val firstSsh = doc["ssh"]!!.jsonArray.first() as JsonObject
    assertEquals(listOf("root"), PolicyDoc.stringsOf(firstSsh["users"]))
    assertEquals(1, PolicyDoc.countOf(doc["groups"]))
    assertEquals(2, PolicyDoc.countOf(doc["acls"]))
    assertNull(PolicyDoc.countOf(doc["randomizeClientPort"]))
    assertTrue(PolicyDoc.stringsOf(null).isEmpty())
  }

  // ------------------------------------------------------------------ search

  private val policy =
      PolicyDoc.parse(
          """
          {
            // bookkeeping
            "#tags": ["#tag:keep"],
            "groups": { "group:family": ["echo@", "duck@"], "group:work": ["fjj@"] },
            "hosts": { "nas": "100.64.0.9" },
            "acls": [
              { "action": "accept", "src": ["group:family"], "dst": ["nas:8000"] },
              { "action": "accept", "src": ["group:work"], "dst": ["*:*"] }
            ]
          }
          """
              .trimIndent())!!

  @Test
  fun filterKeepsOnlyMatchingRules() {
    val hit = PolicyDoc.filter(policy, "nas")
    // Both the host definition and the rule that references it.
    assertEquals(listOf("hosts", "acls"), PolicyDoc.visibleKeys(hit))
    assertEquals("100.64.0.9", (hit["hosts"] as JsonObject).getValue("nas").toString().trim('"'))
    val rules = hit["acls"] as JsonArray
    assertEquals(1, rules.size)
    assertTrue(rules[0].toString().contains("group:family"))
  }

  @Test
  fun filterIsCaseInsensitiveAndSeesNestedValues() {
    assertTrue(PolicyDoc.filter(policy, "DUCK").toString().contains("group:family"))
    assertTrue(PolicyDoc.filter(policy, "8000").toString().contains("acls"))
  }

  @Test
  fun filterKeepsSectionsNamedByTheQuery() {
    val hit = PolicyDoc.filter(policy, "groups")
    assertEquals(listOf("groups"), PolicyDoc.visibleKeys(hit))
    assertEquals(2, (hit["groups"] as JsonObject).size)
  }

  @Test
  fun filterDropsEverythingWhenNothingMatches() {
    assertEquals(0, PolicyDoc.visibleKeys(PolicyDoc.filter(policy, "zzz")).size)
    assertEquals(0, PolicyDoc.visibleKeys(PolicyDoc.filter(policy, "#tags")).size)
  }

  @Test
  fun filterWithBlankQueryReturnsTheWholePolicy() {
    assertSame(policy, PolicyDoc.filter(policy, "   "))
  }
}
