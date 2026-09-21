// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Headscale policies are huJSON: JSON with `//` and `/* */` comments and trailing commas. This
 * turns one into a tree the UI can lay out by section, and keeps every extraction rule testable.
 */
object PolicyDoc {
  /** Sections the policy format defines, in the order they are worth reading. */
  val knownSections =
      listOf(
          "acls",
          "groups",
          "hosts",
          "tagOwners",
          "autoApprovers",
          "ssh",
          "nodeAttrs",
          "postures",
          "tests",
          "sshTests")

  /** Parses huJSON, or null when the text is not a JSON object at all. */
  fun parse(text: String): JsonObject? =
      runCatching { Json.parseToJsonElement(stripTrailingCommas(stripComments(text))) }
          .getOrNull()
          .let { it as? JsonObject }

  /** Sections to render: known ones first, then anything else the policy defines. */
  fun sections(policy: JsonObject): List<String> {
    val visible = visibleKeys(policy)
    return knownSections.filter { it in visible } + visible.filterNot { it in knownSections }
  }

  /** Keys the admin console stores as bookkeeping ("#…") are not part of the policy. */
  fun visibleKeys(obj: JsonObject): List<String> = obj.keys.filterNot { it.startsWith("#") }

  /** Number of entries a value holds, or null for scalars. */
  fun countOf(value: JsonElement?): Int? =
      when (value) {
        is JsonArray -> value.size
        is JsonObject -> value.size
        else -> null
      }

  /** Flattens a value that is either a list of strings or a single string. */
  fun stringsOf(value: JsonElement?): List<String> =
      when (value) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        else -> emptyList()
      }

  /** Fields of a rule that are worth a line: everything except bookkeeping. */
  fun ruleFields(rule: JsonObject): List<Pair<String, JsonElement>> =
      rule.entries.filterNot { it.key.startsWith("#") }.map { it.key to it.value }

  // ------------------------------------------------------------ huJSON input

  /** Drops `//` and `/* */` comments, leaving strings (and escapes) untouched. */
  internal fun stripComments(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    var inString = false
    while (i < text.length) {
      val c = text[i]
      if (inString) {
        out.append(c)
        if (c == '\\' && i + 1 < text.length) {
          out.append(text[i + 1])
          i += 2
          continue
        }
        if (c == '"') inString = false
        i++
        continue
      }
      when {
        c == '"' -> {
          inString = true
          out.append(c)
          i++
        }
        c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
          while (i < text.length && text[i] != '\n') i++
        }
        c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
          i += 2
          while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
          i = if (i + 1 < text.length) i + 2 else text.length
        }
        else -> {
          out.append(c)
          i++
        }
      }
    }
    return out.toString()
  }

  /** Drops a comma that only precedes `}` or `]`. */
  internal fun stripTrailingCommas(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    var inString = false
    while (i < text.length) {
      val c = text[i]
      if (inString) {
        out.append(c)
        if (c == '\\' && i + 1 < text.length) {
          out.append(text[i + 1])
          i += 2
          continue
        }
        if (c == '"') inString = false
        i++
        continue
      }
      if (c == '"') {
        inString = true
        out.append(c)
        i++
        continue
      }
      if (c == ',') {
        var j = i + 1
        while (j < text.length && text[j].isWhitespace()) j++
        if (j < text.length && (text[j] == '}' || text[j] == ']')) {
          i++
          continue
        }
      }
      out.append(c)
      i++
    }
    return out.toString()
  }
}
