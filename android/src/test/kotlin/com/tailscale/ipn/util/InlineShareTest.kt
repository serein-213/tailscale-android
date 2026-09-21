// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inline-share wire format: filenames are content hashes, .txt carries UTF-8 text and .url a
 * Windows Internet Shortcut. Both ends of a Taildrop depend on these staying in sync.
 */
class InlineShareTest {
  private val hashOfHello = "5d41402abc4b2a76b9719d911017c592"

  @Test
  fun matchesOnlyContentHashFilenames() {
    assertTrue(InlineShare.matches("$hashOfHello.txt"))
    assertTrue(InlineShare.matches("${hashOfHello.uppercase()}.URL"))
    assertFalse(InlineShare.matches("$hashOfHello.partial"))
    assertFalse(InlineShare.matches("$hashOfHello.bin"))
    assertFalse(InlineShare.matches("notes.txt"))
    assertFalse(InlineShare.matches(hashOfHello.dropLast(1) + ".txt"))
    assertFalse(InlineShare.matches("$hashOfHello.txt.bak"))
  }

  @Test
  fun hashesContentLikeMd5sum() {
    assertEquals("$hashOfHello.txt", InlineShare.suggestedFilename(InlineShare.Kind.TEXT, "hello"))
    // Non-ASCII and multi-line content must hash identically to md5sum.
    assertEquals(
        "6eaf9c1f43827c6aa58243ce0c412d8b.txt",
        InlineShare.suggestedFilename(InlineShare.Kind.TEXT, "hello, 世界\nsecond line"))
  }

  @Test
  fun textRoundTrip() {
    val share = InlineShare(InlineShare.Kind.TEXT, "hello, 世界\nsecond line")
    val name = InlineShare.suggestedFilename(share.kind, share.content)
    assertEquals(share, InlineShare.decode(name, share.encoded()))
  }

  @Test
  fun urlRoundTripUsesWindowsShortcutFormat() {
    val share = InlineShare(InlineShare.Kind.URL, "https://example.com/a?b=1&c=2")

    val encoded = share.encoded().toString(Charsets.UTF_8)
    assertTrue("expected CRLF shortcut, got: $encoded", encoded.startsWith("[InternetShortcut]\r\nURL="))
    assertEquals(
        share, InlineShare.decode(InlineShare.suggestedFilename(share.kind, share.content), share.encoded()))
  }

  @Test
  fun parsesShortcutVariants() {
    val name = "$hashOfHello.url"
    // LF-only and lowercase key (some third-party senders).
    assertEquals(
        InlineShare(InlineShare.Kind.URL, "https://x.example"),
        InlineShare.decode(name, "[internetshortcut]\nurl=https://x.example\n".toByteArray()))
    // Extra INI keys after the URL line.
    assertEquals(
        InlineShare(InlineShare.Kind.URL, "https://y.example"),
        InlineShare.decode(name, "[InternetShortcut]\r\nURL=https://y.example\r\nIconIndex=0\r\n".toByteArray()))
  }

  @Test
  fun rejectsUnusablePayloads() {
    val name = "$hashOfHello.txt"
    assertNull(InlineShare.decode(name, ByteArray(0)))
    assertNull(
        InlineShare.decode("$hashOfHello.url", "[InternetShortcut]\r\nIconIndex=0\r\n".toByteArray()))
    assertNull(InlineShare.decode("$hashOfHello.bin", "whatever".toByteArray()))
  }
}
