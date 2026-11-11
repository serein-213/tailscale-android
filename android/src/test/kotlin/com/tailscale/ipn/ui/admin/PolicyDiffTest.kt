// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import org.junit.Assert.*
import org.junit.Test

class PolicyDiffTest {
  @Test
  fun replacementDisplaysActualRemovedAndAddedText() {
    val diff =
        PolicyDiff.compare(
            "{\n  // keep comment\n  \"groups\": {}\n}",
            "{\n  // keep comment\n  \"groups\": {\"group:ops\": []}\n}",
        )
    assertEquals(1, diff.added)
    assertEquals(1, diff.removed)
    assertEquals(
        listOf("  \"groups\": {}"),
        diff.lines.filter { it.kind == PolicyDiff.Kind.REMOVED }.map { it.text },
    )
    assertEquals(
        listOf("  \"groups\": {\"group:ops\": []}"),
        diff.lines.filter { it.kind == PolicyDiff.Kind.ADDED }.map { it.text },
    )
    assertFalse(diff.broadReplacement)
  }

  @Test
  fun repeatedLinesDoNotInventExtraChanges() {
    val diff = PolicyDiff.compare("same\nremove\nsame\nkeep", "same\nsame\nadd\nkeep")
    assertEquals(
        listOf("remove"),
        diff.lines.filter { it.kind == PolicyDiff.Kind.REMOVED }.map { it.text },
    )
    assertEquals(
        listOf("add"),
        diff.lines.filter { it.kind == PolicyDiff.Kind.ADDED }.map { it.text },
    )
  }

  @Test
  fun emptyAndTrailingNewlineChangesRemainVisible() {
    assertEquals(0, PolicyDiff.compare("", "").added)
    assertEquals(1, PolicyDiff.compare("", "{}").added)
    assertEquals(1, PolicyDiff.compare("{}", "").removed)
    val newline = PolicyDiff.compare("{}", "{}\n")
    assertEquals(
        listOf(PolicyDiff.Line(PolicyDiff.Kind.ADDED, "")),
        newline.lines.filter { it.kind == PolicyDiff.Kind.ADDED },
    )
    val crlf = PolicyDiff.compare("{}\n", "{}\r\n")
    assertEquals("{}\r", crlf.lines.single { it.kind == PolicyDiff.Kind.ADDED }.text)
  }

  @Test
  fun distantEditsOmitOnlyUnchangedLines() {
    val before = (0..20).joinToString("\n")
    val after = before.replace("1\n", "one\n").replace("19\n", "nineteen\n")
    val diff = PolicyDiff.compare(before, after)
    assertTrue(diff.lines.any { it.kind == PolicyDiff.Kind.GAP })
    assertTrue(diff.lines.any { it.kind == PolicyDiff.Kind.ADDED && it.text == "nineteen" })
  }

  @Test
  fun veryLargeReplacementUsesBoundedComparisonAndShowsExactRegion() {
    val old = (0..600).joinToString("\n") { "old $it" }
    val new = (0..600).joinToString("\n") { "new $it" }
    val diff = PolicyDiff.compare(old, new)
    assertTrue(diff.broadReplacement)
    assertEquals(
        old,
        diff.lines.filter { it.kind == PolicyDiff.Kind.REMOVED }.joinToString("\n") { it.text },
    )
    assertEquals(
        new,
        diff.lines.filter { it.kind == PolicyDiff.Kind.ADDED }.joinToString("\n") { it.text },
    )
  }
}
