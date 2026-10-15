// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import androidx.compose.runtime.saveable.SaverScope
import com.tailscale.ipn.ui.view.EntryTarget
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class PolicyRegressionTest {
  private fun key(name: String) = PolicyEdit.Step.Key(name)

  private val original = """{"acls":[{"src":["alice"]},{"src":["bob"]},{"src":["carol"]}]}"""

  @Test
  fun searchingAndDeletingKeepsOriginalIndices() {
    val rules = PolicyDoc.parse(original)!!["acls"] as JsonArray
    val index = PolicyDoc.matchingIndices("acls", rules, "bob").single()
    val edited = PolicyEdit.remove(original, listOf(key("acls"), PolicyEdit.Step.Index(index)))!!
    assertEquals(1, index)
    assertTrue(edited.contains("alice"))
    assertTrue(edited.contains("carol"))
    assertFalse(edited.contains("bob"))
    assertEquals(listOf(0, 1, 2), PolicyDoc.matchingIndices("acls", rules, "acls"))
  }

  @Test
  fun subsequentEditsResolveAgainstCurrentDraft() {
    val state = PolicyEditorState(original)
    state.draft = PolicyEdit.remove(state.draft, listOf(key("acls"), PolicyEdit.Step.Index(0)))!!
    val rules = PolicyDoc.parse(state.draft)!!["acls"] as JsonArray
    val index = PolicyDoc.matchingIndices("acls", rules, "bob").single()
    state.draft =
        PolicyEdit.remove(state.draft, listOf(key("acls"), PolicyEdit.Step.Index(index)))!!
    val remaining = PolicyDoc.parse(state.draft)!!["acls"] as JsonArray
    assertEquals(1, remaining.size)
    assertTrue(remaining.single().toString().contains("carol"))
  }

  @Test
  fun appendsWithTrailingCommasAndComments() {
    for (tail in listOf(",", ", // note, with comma\n", " /* comma, in comment */", "")) {
      val array = "{\"acls\":[{\"src\":[\"alice\"]}$tail]}"
      val result = PolicyEdit.appendTo(array, listOf(key("acls")), "{\"src\":[\"bob\"]}")!!
      assertEquals(result, 2, (PolicyDoc.parse(result)!!["acls"] as JsonArray).size)
      val obj = "{\"hosts\":{\"a\":\"100.64.0.1\"$tail}}"
      val added = PolicyEdit.addKey(obj, listOf(key("hosts")), "b", "\"100.64.0.2\"")!!
      assertEquals(added, 2, (PolicyDoc.parse(added)!!["hosts"] as JsonObject).size)
    }
  }

  @Test
  fun appendsToCommentOnlyContainers() {
    val result = PolicyEdit.appendTo("{\"acls\":[/* reserved */]}", listOf(key("acls")), "{}")!!
    assertEquals(1, (PolicyDoc.parse(result)!!["acls"] as JsonArray).size)
    val added = PolicyEdit.addKey("{/* reserved */}", emptyList(), "hosts", "{}")!!
    assertNotNull(PolicyDoc.parse(added)!!["hosts"])
  }

  @Test
  fun groupAndTagOwnerListsAddressTheirOwnArray() {
    for (section in listOf("groups", "tagOwners")) {
      val path = listOf(key(section), key("entry"))
      val target = EntryTarget(title = "entry", path = path, src = listOf("alice"), srcPath = path)
      val text = "{\"$section\":{\"entry\":[\"alice\"]}}"
      val edited = PolicyEdit.replaceArray(text, target.srcPath, listOf("alice", "bob"))!!
      assertTrue(edited.contains("bob"))
    }
    val rule = EntryTarget(title = "rule", path = listOf(key("acls"), PolicyEdit.Step.Index(0)))
    assertEquals(rule.path + key("src"), rule.srcPath)
  }

  @Test
  fun refreshPreservesDraftAndRestorationPreservesEditingSession() {
    val state = PolicyEditorState("old")
    state.draft = "unsaved"
    state.editing = true
    state.receive("server changed")
    assertEquals("unsaved", state.draft)
    val scope =
        object : SaverScope {
          override fun canBeSaved(value: Any) = true
        }
    val saved = with(PolicyEditorState.Saver) { scope.save(state) }!!
    val restored = PolicyEditorState.Saver.restore(saved)!!
    assertEquals("unsaved", restored.draft)
    assertEquals("server changed", restored.source)
    assertTrue(restored.editing)
    assertTrue(restored.dirty)
    restored.saved("normalized by server")
    assertFalse(restored.dirty)
    assertFalse(restored.editing)
  }

  @Test
  fun backupsAreScopedToServer() {
    assertNotEquals(
        PolicyBackup.keyFor("https://a.example"),
        PolicyBackup.keyFor("https://b.example"),
    )
    assertEquals(PolicyBackup.keyFor("https://a.example"), PolicyBackup.keyFor("https://a.example"))
  }
}
