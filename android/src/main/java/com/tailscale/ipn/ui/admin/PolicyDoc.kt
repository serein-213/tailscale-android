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
          "sshTests",
      )

  /** Parses huJSON, or null when the text is not a JSON object at all. */
  fun parse(text: String): JsonObject? = runCatching {
    Json.parseToJsonElement(stripTrailingCommas(stripComments(text)))
  }
      .getOrNull()
      .let { it as? JsonObject }

  /** Where and why [parse] gave up. [line] and [column] are 1-based, null when unknown. */
  data class ParseError(val line: Int?, val column: Int?, val message: String)

  /**
   * Explains why [text] does not parse, or null when it does. Comments and trailing commas are
   * blanked rather than removed here, so the parser's offset still points into the text the user is
   * looking at.
   */
  fun parseError(text: String): ParseError? {
    val sameLength = stripTrailingCommas(stripComments(text, keepLength = true), keepLength = true)
    val failure = runCatching {
      Json.parseToJsonElement(sameLength)
    }
        .fold(
            onSuccess = {
              if (it is JsonObject) return null
              return ParseError(null, null, "the policy must be a JSON object")
            },
            onFailure = { it },
        )
    val summary = failure.message.orEmpty().lineSequence().firstOrNull().orEmpty()
    val offset =
        Regex("offset (\\d+)")
            .find(summary)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.coerceIn(
                0,
                text.length,
            )
    val message = summary.replace(Regex("^.*?offset \\d+:?\\s*"), "").ifBlank { summary }
    if (offset == null) return ParseError(null, null, message)
    val before = text.substring(0, offset)
    return ParseError(
        line = before.count { it == '\n' } + 1,
        column = offset - (before.lastIndexOf('\n') + 1) + 1,
        message = message,
    )
  }

  /** Sections to render: known ones first, then anything else the policy defines. */
  fun sections(policy: JsonObject): List<String> {
    val visible = visibleKeys(policy)
    return knownSections.filter { it in visible } + visible.filterNot { it in knownSections }
  }

  /**
   * Keeps only the entries whose JSON mentions [query] (case-insensitive), so a rule can be found
   * in an 8KB policy without reading it. A section is kept whole when its own name matches, and
   * dropped when nothing inside it does.
   */
  fun filter(policy: JsonObject, query: String): JsonObject {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return policy
    val kept = LinkedHashMap<String, JsonElement>()
    for ((key, value) in policy) {
      if (key.startsWith("#")) continue
      if (key.lowercase().contains(needle)) {
        kept[key] = value
        continue
      }
      when {
        key.lowercase().contains(needle) -> kept[key] = value
        value is JsonArray -> {
          val matches = value.filter { it.toString().lowercase().contains(needle) }
          if (matches.isNotEmpty()) kept[key] = JsonArray(matches)
        }
        value is JsonObject -> {
          val matches = value.filterKeys {
            it.lowercase().contains(needle) ||
                value.getValue(it).toString().lowercase().contains(needle)
          }
          if (matches.isNotEmpty()) kept[key] = JsonObject(matches)
        }
        value.toString().lowercase().contains(needle) -> kept[key] = value
      }
    }
    return JsonObject(kept)
  }

  /** Original indices survive filtering so edits still address the intended rule. */
  fun matchingIndices(section: String, values: JsonArray, query: String): List<Int> {
    val needle = query.trim().lowercase()
    return values.indices.filter {
      needle.isEmpty() ||
          section.lowercase().contains(needle) ||
          values[it].toString().lowercase().contains(needle)
    }
  }

  /** Total entries a policy holds, for the "N of M" hint while searching. */
  fun size(policy: JsonObject): Int = visibleKeys(policy).sumOf { countOf(policy[it]) ?: 0 }

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

  /**
   * Drops `//` and `/* */` comments, leaving strings (and escapes) untouched. With [keepLength] the
   * comment is overwritten with spaces instead (newlines stay), so offsets keep their meaning.
   */
  internal fun stripComments(text: String, keepLength: Boolean = false): String {
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
          val start = i
          while (i < text.length && text[i] != '\n') i++
          if (keepLength) repeat(i - start) { out.append(' ') }
        }
        c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
          val start = i
          i += 2
          while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
          i = if (i + 1 < text.length) i + 2 else text.length
          if (keepLength) {
            for (j in start until i) out.append(if (text[j] == '\n') '\n' else ' ')
          }
        }
        else -> {
          out.append(c)
          i++
        }
      }
    }
    return out.toString()
  }

  /** Drops (or, with [keepLength], blanks) a comma that only precedes `}` or `]`. */
  internal fun stripTrailingCommas(text: String, keepLength: Boolean = false): String {
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
          if (keepLength) out.append(' ')
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
