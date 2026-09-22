// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

/**
 * Path-addressed edits on a huJSON policy that leave every byte they do not touch alone: comments,
 * indentation and trailing commas survive, which matters because the server stores the text
 * verbatim. The UI can therefore offer structured edits (delete this rule, add this group member)
 * without ever reformatting the policy behind the user's back.
 *
 * Positions are string indices and ranges are half-open at the end. Malformed input is never
 * guessed at: every function returns null instead of producing something unparseable.
 */
object PolicyEdit {
  /** One hop into the document: an object member by name, or an array element by index. */
  sealed interface Step {
    data class Key(val name: String) : Step

    data class Index(val index: Int) : Step
  }

  /** The value at [path], or null when the path does not address one. */
  fun rangeOf(text: String, path: List<Step>): IntRange? {
    val member = locate(text, path) ?: return null
    return member.valueStart until member.valueEnd
  }

  /** Replaces the value at [path] with [json], which must be valid JSON. */
  fun replace(text: String, path: List<Step>, json: String): String? {
    val member = locate(text, path) ?: return null
    return text.substring(0, member.valueStart) + json + text.substring(member.valueEnd)
  }

  /**
   * Removes the value at [path]. A member on a line of its own takes the line with it (that is what
   * the console produces); anything else loses just the value and one comma, which stays parseable
   * because huJSON allows a trailing comma.
   */
  fun remove(text: String, path: List<Step>): String? {
    val member = locate(text, path) ?: return null
    val (from, to) = removalRange(text, member)
    return text.substring(0, from) + text.substring(to)
  }

  /** Appends [json] as the last element of the array at [path], matching the local indentation. */
  fun appendTo(text: String, path: List<Step>, json: String): String? {
    val array = locate(text, path) ?: return null
    val open = array.valueStart
    if (text.getOrNull(open) != '[') return null
    // matchingBracket reports one past the closing bracket; the splice needs the bracket itself.
    val close = (matchingBracket(text, open) ?: return null) - 1
    val indent = lineIndent(text, open)
    val element = "$indent  "
    if (text.substring(open + 1, close).isBlank()) {
      return text.substring(0, open + 1) +
          "\n$element$json\n$indent" +
          text.substring(open + 1)
    }
    // Splice right after the last element so the original line break before "]" stays put.
    val lastEnd = lastElementEnd(text, open, close) ?: return null
    val between = text.substring(lastEnd, close)
    val comma = if (between.contains(',')) "" else ","
    return text.substring(0, lastEnd) + "$comma\n$element$json" + text.substring(lastEnd)
  }

  /**
   * Adds `"name": json` to the object at [path], or to the document root when [path] is empty.
   * Refuses a name that is already taken, so an add can never silently shadow an entry.
   */
  fun addKey(text: String, path: List<Step>, name: String, json: String): String? {
    val open =
        if (path.isEmpty()) {
          skipTrivia(text, 0).takeIf { text.getOrNull(it) == '{' } ?: return null
        } else {
          val member = locate(text, path) ?: return null
          member.valueStart.takeIf { text.getOrNull(it) == '{' } ?: return null
        }
    if (child(text, open, Step.Key(name)) != null) return null
    val close = (matchingBracket(text, open) ?: return null) - 1
    val indent = lineIndent(text, open)
    val entry = "$indent  ${quote(name)}: $json"
    if (text.substring(open + 1, close).isBlank()) {
      return text.substring(0, open + 1) + "\n$entry\n$indent" + text.substring(open + 1)
    }
    // Splice after the last member so the original line break before "}" stays put.
    val lastEnd = lastMemberEnd(text, open, close) ?: return null
    val between = text.substring(lastEnd, close)
    val comma = if (between.contains(',')) "" else ","
    return text.substring(0, lastEnd) + "$comma\n$entry" + text.substring(lastEnd)
  }

  /** End (exclusive) of the last member's value in the object between [open] and [close]. */
  private fun lastMemberEnd(text: String, open: Int, close: Int): Int? {
    var last: Int? = null
    var i = skipTrivia(text, open + 1)
    while (i < close && text[i] != '}') {
      val keyEnd = matchString(text, i)
      if (keyEnd < 0) return last
      val colon = skipTrivia(text, keyEnd)
      if (text.getOrNull(colon) != ':') return last
      val valueStart = skipTrivia(text, colon + 1)
      val valueEnd = valueEnd(text, valueStart) ?: return last
      last = valueEnd
      i = skipTrivia(text, valueEnd)
      if (text.getOrNull(i) == ',') i = skipTrivia(text, i + 1)
    }
    return last
  }

  /** Replaces the array at [path] with [values], rendered in the policy's own style. */
  fun replaceArray(text: String, path: List<Step>, values: List<String>): String? {
    val member = locate(text, path) ?: return null
    if (text.getOrNull(member.valueStart) != '[') return null
    val original = text.substring(member.valueStart, member.valueEnd)
    val json =
        when {
          values.isEmpty() -> "[]"
          // Match what is already there: a one-liner stays a one-liner.
          !original.contains('\n') -> values.joinToString(", ", "[", "]") { quote(it) }
          else -> {
            val inner = "${lineIndent(text, member.valueStart)}  "
            values.joinToString(",\n", "[\n", "\n${lineIndent(text, member.valueStart)}]") {
              "$inner${quote(it)}"
            }
          }
        }
    return replace(text, path, json)
  }

  /** JSON string literal for [value], so an edit can never break out of its own quotes. */
  fun quote(value: String): String {
    val out = StringBuilder("\"")
    for (c in value) {
      when (c) {
        '\\' -> out.append("\\\\")
        '"' -> out.append("\\\"")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\t' -> out.append("\\t")
        else -> out.append(c)
      }
    }
    return out.append('"').toString()
  }

  // ------------------------------------------------------------------- scanning

  /** [valueStart]..[valueEnd] is the value; [start]..[end] covers the member plus its comma. */
  private data class Member(val valueStart: Int, val valueEnd: Int, val start: Int, val end: Int)

  /** Walks [path] from the document root and returns the addressed member. */
  private fun locate(text: String, path: List<Step>): Member? {
    var scope = skipTrivia(text, 0)
    if (text.getOrNull(scope) != '{') return null
    var found: Member? = null
    for (step in path) {
      val member = child(text, scope, step) ?: return null
      found = member
      scope = member.valueStart
    }
    return found
  }

  private fun child(text: String, container: Int, step: Step): Member? =
      when (step) {
        is Step.Key -> if (text.getOrNull(container) == '{') member(text, container, step.name) else null
        is Step.Index ->
            if (text.getOrNull(container) == '[') element(text, container, step.index) else null
      }

  private fun member(text: String, open: Int, name: String): Member? {
    var i = skipTrivia(text, open + 1)
    while (i < text.length && text[i] != '}') {
      val keyEnd = matchString(text, i)
      if (keyEnd < 0) return null
      val key = unquote(text.substring(i, keyEnd))
      val colon = skipTrivia(text, keyEnd)
      if (text.getOrNull(colon) != ':') return null
      val valueStart = skipTrivia(text, colon + 1)
      val valueEnd = valueEnd(text, valueStart) ?: return null
      val afterValue = skipTrivia(text, valueEnd)
      val hasComma = text.getOrNull(afterValue) == ','
      // end stays on the member's own line: it is a value end, or one past the adjacent comma.
      val end = if (hasComma) afterValue + 1 else valueEnd
      if (key == name) return Member(valueStart, valueEnd, i, end)
      i = skipTrivia(text, if (hasComma) afterValue + 1 else valueEnd)
    }
    return null
  }

  private fun element(text: String, open: Int, index: Int): Member? {
    var i = skipTrivia(text, open + 1)
    var seen = 0
    while (i < text.length && text[i] != ']') {
      val end = valueEnd(text, i) ?: return null
      val afterValue = skipTrivia(text, end)
      val hasComma = text.getOrNull(afterValue) == ','
      if (seen == index) {
        return Member(i, end, i, if (hasComma) afterValue + 1 else end)
      }
      seen++
      val next = skipTrivia(text, if (hasComma) afterValue + 1 else end)
      if (next <= i) return null
      i = next
    }
    return null
  }

  /** End (exclusive) of the value starting at [start], or null when it is malformed. */
  private fun valueEnd(text: String, start: Int): Int? {
    if (start >= text.length) return null
    return when (text[start]) {
      '"' -> matchString(text, start).takeIf { it > 0 }
      '{', '[' -> matchingBracket(text, start)
      else -> {
        var i = start
        while (i < text.length && text[i] != ',' && text[i] != '}' && text[i] != ']') {
          if (text.startsWith("//", i) || text.startsWith("/*", i)) break
          i++
        }
        i.takeIf { it > start }
      }
    }
  }

  /** End (exclusive) of the balanced container at [open], skipping strings and comments. */
  private fun matchingBracket(text: String, open: Int): Int? {
    val closing = if (text[open] == '{') '}' else ']'
    var depth = 0
    var i = open
    while (i < text.length) {
      val c = text[i]
      when {
        text.startsWith("//", i) -> i = lineEnd(text, i)
        text.startsWith("/*", i) -> i = blockCommentEnd(text, i)
        c == '"' -> i = matchString(text, i).takeIf { it > 0 } ?: return null
        c == text[open] -> {
          depth++
          i++
        }
        c == closing -> {
          depth--
          i++
          if (depth == 0) return i
        }
        else -> i++
      }
    }
    return null
  }

  /** End (exclusive) of the string literal at [start], or -1 when there is none. */
  private fun matchString(text: String, start: Int): Int {
    if (text.getOrNull(start) != '"') return -1
    var i = start + 1
    while (i < text.length) {
      when (text[i]) {
        '\\' -> i += 2
        '"' -> return i + 1
        else -> i++
      }
    }
    return -1
  }

  /** End (exclusive) of the last element of the array whose brackets are [open] and [close]. */
  private fun lastElementEnd(text: String, open: Int, close: Int): Int? {
    var last: Int? = null
    var i = skipTrivia(text, open + 1)
    while (i < close && text[i] != ']') {
      val end = valueEnd(text, i) ?: return last
      last = end
      i = skipTrivia(text, end)
      if (text.getOrNull(i) == ',') i = skipTrivia(text, i + 1)
    }
    return last
  }

  private fun skipTrivia(text: String, from: Int): Int {
    var i = from
    while (i < text.length) {
      val c = text[i]
      when {
        c.isWhitespace() -> i++
        text.startsWith("//", i) -> i = lineEnd(text, i)
        text.startsWith("/*", i) -> i = blockCommentEnd(text, i)
        else -> return i
      }
    }
    return i
  }

  private fun lineEnd(text: String, from: Int): Int {
    val nl = text.indexOf('\n', from)
    return if (nl < 0) text.length else nl + 1
  }

  private fun lineStart(text: String, from: Int): Int {
    val nl = text.lastIndexOf('\n', from - 1)
    return if (nl < 0) 0 else nl + 1
  }

  private fun blockCommentEnd(text: String, from: Int): Int {
    val end = text.indexOf("*/", from + 2)
    return if (end < 0) text.length else end + 2
  }

  /** Indentation of the line the value at [at] sits on. */
  private fun lineIndent(text: String, at: Int): String {
    val start = lineStart(text, at)
    return text.substring(start, at).takeWhile { it == ' ' || it == '\t' }
  }

  private fun removalRange(text: String, member: Member): Pair<Int, Int> {
    val valueEnd = member.valueEnd
    val from = lineStart(text, member.start)
    val to = lineEnd(text, valueEnd)
    val aloneOnItsLine =
        text.substring(from, member.start).isBlank() &&
            text.substring(member.end, to).trim().isEmpty()
    return if (aloneOnItsLine) from to to
    else if (member.end > valueEnd) member.start to member.end
    else member.start to valueEnd
  }

  private fun unquote(raw: String): String =
      if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
        raw.substring(1, raw.length - 1)
      } else {
        raw
      }
}
