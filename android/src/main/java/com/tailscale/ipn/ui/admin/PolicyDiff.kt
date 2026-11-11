// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

/**
 * Text-only comparison: a replacement is a removal plus an addition, not a semantic rule change.
 */
data class PolicyDiff(
    val lines: List<Line>,
    val added: Int,
    val removed: Int,
    val broadReplacement: Boolean,
) {
  enum class Kind {
    CONTEXT,
    ADDED,
    REMOVED,
    GAP,
  }

  data class Line(val kind: Kind, val text: String)

  companion object {
    // Bound both memory and work for large policies. The fallback still shows the exact old/new
    // region, but does not claim that every line in that region changed independently.
    private const val MAX_CELLS = 250_000L

    fun compare(before: String, after: String): PolicyDiff {
      if (before == after) return PolicyDiff(emptyList(), 0, 0, false)
      val old = if (before.isEmpty()) emptyList() else before.split('\n')
      val new = if (after.isEmpty()) emptyList() else after.split('\n')
      var start = 0
      while (start < old.size && start < new.size && old[start] == new[start]) start++
      var end = 0
      while (
          end < old.size - start &&
              end < new.size - start &&
              old[old.lastIndex - end] == new[new.lastIndex - end]
      ) end++
      val oldCount = old.size - start - end
      val newCount = new.size - start - end
      val broad = (oldCount.toLong() + 1) * (newCount.toLong() + 1) > MAX_CELLS
      val changes = mutableListOf<Line>()
      if (start > 2) changes += Line(Kind.GAP, "")
      for (i in maxOf(0, start - 2) until start) changes += Line(Kind.CONTEXT, old[i])
      if (broad) {
        for (i in start until old.size - end) changes += Line(Kind.REMOVED, old[i])
        for (i in start until new.size - end) changes += Line(Kind.ADDED, new[i])
      } else {
        val width = newCount + 1
        val lengths = IntArray((oldCount + 1) * width)
        for (i in oldCount - 1 downTo 0) {
          for (j in newCount - 1 downTo 0) {
            lengths[i * width + j] =
                if (old[start + i] == new[start + j]) lengths[(i + 1) * width + j + 1] + 1
                else maxOf(lengths[(i + 1) * width + j], lengths[i * width + j + 1])
          }
        }
        var i = 0
        var j = 0
        while (i < oldCount || j < newCount) {
          when {
            i < oldCount && j < newCount && old[start + i] == new[start + j] -> {
              changes += Line(Kind.CONTEXT, old[start + i])
              i++
              j++
            }
            i < oldCount &&
                (j == newCount || lengths[(i + 1) * width + j] >= lengths[i * width + j + 1]) -> {
              changes += Line(Kind.REMOVED, old[start + i])
              i++
            }
            else -> {
              changes += Line(Kind.ADDED, new[start + j])
              j++
            }
          }
        }
      }
      for (i in old.size - end until old.size - end + minOf(2, end)) {
        changes += Line(Kind.CONTEXT, old[i])
      }
      if (end > 2) changes += Line(Kind.GAP, "")
      val visible = mutableListOf<Line>()
      var index = 0
      while (index < changes.size) {
        if (changes[index].kind != Kind.CONTEXT) {
          visible += changes[index++]
          continue
        }
        val runStart = index
        while (index < changes.size && changes[index].kind == Kind.CONTEXT) index++
        val leading = if (runStart > 0) minOf(2, index - runStart) else 0
        val trailing = if (index < changes.size) minOf(2, index - runStart - leading) else 0
        visible += changes.subList(runStart, runStart + leading)
        if (index - runStart > leading + trailing) visible += Line(Kind.GAP, "")
        visible += changes.subList(index - trailing, index)
      }
      return PolicyDiff(
          visible,
          changes.count { it.kind == Kind.ADDED },
          changes.count { it.kind == Kind.REMOVED },
          broad,
      )
    }
  }
}
