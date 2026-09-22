// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.PolicyBackup
import com.tailscale.ipn.ui.admin.PolicyDoc
import com.tailscale.ipn.ui.admin.PolicyEdit
import com.tailscale.ipn.ui.admin.PolicyValidate
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.util.Lists
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Sections whose entries can be added from the structured view. */
private val policyAddableSections = setOf("acls", "ssh", "hosts", "groups", "tagOwners")

/** Sections whose entries are rules: a new one starts as an empty match set, which is neutral. */
private val policyRuleSections = setOf("acls", "ssh")

/**
 * The access policy, laid out instead of dumped: sections that expand, one block per rule, and a
 * raw view for when the text is not parseable or someone wants the original.
 */
@Composable
fun AdminPolicyTab(
    policyText: String,
    updatedAt: String?,
    loading: Boolean,
    saving: Boolean,
    users: List<String>,
    tags: List<String>,
    editPolicyRequest: Boolean,
    onEditPolicyHandled: () -> Unit,
    onEditingState: (Boolean) -> Unit,
    onSave: (String) -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  var showRaw by remember { mutableStateOf(false) }
  val doc = remember(policyText) { PolicyDoc.parse(policyText) }
  var editing by remember { mutableStateOf(false) }
  var draft by remember { mutableStateOf(policyText) }
  // Set while a save is in flight so success (the saved text comes back from the server) can leave
  // edit mode, and failure keeps the draft.
  var pendingSave by remember { mutableStateOf<String?>(null) }
  var confirmSave by remember { mutableStateOf(false) }
  var backup by remember { mutableStateOf<String?>(null) }
  var searching by remember { mutableStateOf(false) }
  var entryTarget by remember { mutableStateOf<EntryTarget?>(null) }
  var addingTo by remember { mutableStateOf<String?>(null) }
  var confirmDiscard by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  val draftValid = remember(draft) { PolicyDoc.parse(draft) != null }
  val filtered = remember(doc, query) { doc?.let { PolicyDoc.filter(it, query) } }
  val matches = remember(filtered) { filtered?.let { PolicyDoc.size(it) } ?: 0 }
  val total = remember(doc) { doc?.let { PolicyDoc.size(it) } ?: 0 }
  val problems = remember(draft) { doc?.let { PolicyValidate.problems(it) } ?: emptyList() }
  val hosts = remember(doc) { (doc?.get("hosts") as? JsonObject)?.keys.orEmpty().toList() }
  val existingDst =
      remember(doc) {
        (doc?.get("acls") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.flatMap { PolicyDoc.stringsOf(it["dst"]) }
            .orEmpty()
      }
  val suggestions =
      remember(doc, users, tags) {
        policySuggestions(
            users = users,
            tags = tags,
            groups = (doc?.get("groups") as? JsonObject)?.keys.orEmpty().toList(),
            hosts = (doc?.get("hosts") as? JsonObject)?.keys.orEmpty().toList(),
            tagOwners = (doc?.get("tagOwners") as? JsonObject)?.keys.orEmpty().toList())
      }

  /** The engine refuses paths it cannot splice; say so instead of quietly doing nothing. */
  fun onEditFailed() {
    android.util.Log.w("AdminPolicyTab", "structured edit did not apply")
  }

  val dirty = draft != policyText
  val structured =
      remember(draft, users, tags) {
        StructuredEditor(
            users = users,
            tags = tags,
            onEdit = { edit ->
              val next =
                  when (edit) {
                    is StructuredEdit.SetAction ->
                        // The action field, never the rule itself.
                        PolicyEdit.replace(
                            draft,
                            edit.path + PolicyEdit.Step.Key("action"),
                            PolicyEdit.quote(edit.action))
                    is StructuredEdit.SetList ->
                        PolicyEdit.replaceArray(draft, edit.path, edit.values)
                    is StructuredEdit.SetValue ->
                        edit.value.takeIf { it.isNotBlank() }?.let {
                          PolicyEdit.replace(draft, edit.path, PolicyEdit.quote(it))
                        }
                    is StructuredEdit.RemoveEntry -> PolicyEdit.remove(draft, edit.path)
                    is StructuredEdit.AddEntry ->
                        PolicyEdit.addKey(
                            draft,
                            listOf(PolicyEdit.Step.Key(edit.section)),
                            edit.name,
                            when (edit.value) {
                              JsonFragment.EMPTY_ARRAY -> "[]"
                              JsonFragment.EMPTY_STRING -> "\"\""
                            })
                    is StructuredEdit.AddRule ->
                        PolicyEdit.appendTo(
                            draft,
                            listOf(PolicyEdit.Step.Key(edit.section)),
                            ruleJson(edit.section, edit.action, edit.src, edit.dst))
                  }
              if (next != null) draft = next else onEditFailed()
            })
      }

  // Matches are only visible in the structured view.
  LaunchedEffect(query) { if (query.isNotBlank()) showRaw = false }

  LaunchedEffect(policyText, editing) {
    if (!editing) draft = policyText
  }
  // Read off the main thread: the encrypted preferences open the keystore on first access.
  LaunchedEffect(policyText) { backup = withContext(Dispatchers.IO) { PolicyBackup.last() } }
  // The screen owns the FAB; entering edit mode is decided here, next to the draft.
  LaunchedEffect(editPolicyRequest) {
    if (editPolicyRequest) {
      editing = true
      onEditPolicyHandled()
    }
  }
  // The FAB is only meaningful while the policy is not being edited.
  LaunchedEffect(editing, draft != policyText) { onEditingState(editing || draft != policyText) }

  LaunchedEffect(policyText, saving, pendingSave) {
    val pending = pendingSave
    if (!saving && pending != null && policyText == pending) {
      editing = false
      pendingSave = null
      backup = PolicyBackup.last()
    }
  }

  Column(Modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.weight(1f))
          if (editing) {
            TextButton(
                enabled = backup != null && !saving,
                onClick = { backup?.let { draft = it } }) {
                  Text(stringResource(R.string.policy_edit_restore))
                }
            TextButton(enabled = !saving, onClick = { editing = false; draft = policyText }) {
              Text(stringResource(R.string.policy_edit_cancel))
            }
            TextButton(
                enabled = draftValid && draft != policyText && !saving,
                onClick = { confirmSave = true }) {
                  Text(stringResource(R.string.policy_edit_save))
                }
          } else {
            if (doc != null) {
              IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(R.string.policy_search_hint))
              }
            }
            // Secondary actions live behind the overflow, the way the rest of the app does it.
            Box {
              IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_actions))
              }
              DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (doc != null) {
                  DropdownMenuItem(
                      text = {
                        Text(
                            stringResource(
                                if (showRaw) R.string.policy_view_structured_action
                                else R.string.policy_view_raw_action))
                      },
                      onClick = {
                        menuOpen = false
                        showRaw = !showRaw
                      })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.policy_copy_all)) },
                    enabled = policyText.isNotEmpty() && !saving,
                    onClick = {
                      menuOpen = false
                      clipboard.setText(AnnotatedString(policyText))
                    })
              }
            }
          }
        }

    addingTo?.let { section ->
      AddEntryDialog(
          section = section,
          onDismiss = { addingTo = null },
          onAdd = { name, value ->
            addingTo = null
            structured.onEdit(
                StructuredEdit.AddEntry(
                    section = section, name = name, value = JsonFragment.EMPTY_ARRAY))
            if (section == "hosts" && value.isNotEmpty()) {
              draft =
                  PolicyEdit.replace(
                          draft,
                          listOf(PolicyEdit.Step.Key(section), PolicyEdit.Step.Key(name)),
                          PolicyEdit.quote(value))
                      ?: draft
            }
          })
    }

    if (dirty && !editing) {
      Row(
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(
                  stringResource(R.string.policy_edit_modified),
                  style = MaterialTheme.typography.bodySmall)
              if (problems.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.policy_edit_problems, problems.size, problems.first().message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2)
              }
            }
            TextButton(onClick = { confirmDiscard = true }) {
              Text(stringResource(R.string.policy_edit_discard))
            }
            TextButton(enabled = !saving, onClick = { confirmSave = true }) {
              Text(stringResource(R.string.policy_edit_save))
            }
          }
    }
    if (editing) {
      Text(
          stringResource(R.string.policy_edit_raw_hint),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 16.dp, top = 2.dp))
    }
    if (searching && doc != null) {
      OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.admin_clear))
              }
            }
          },
          placeholder = { Text(stringResource(R.string.policy_search_hint)) },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
      if (query.isNotBlank()) {
        Text(
            stringResource(
                if (matches == 0) R.string.policy_search_none else R.string.policy_search_count,
                matches,
                total),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (matches == 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
      }
    }
    updatedAt?.let {
      Text(
          stringResource(R.string.admin_policy_updated, it.replace("T", " ").take(19)),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
    }

    when {
      editing -> PolicyEditor(draft = draft, invalid = !draftValid, saving = saving, onChange = { draft = it })
      policyText.isEmpty() && loading -> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(Modifier.size(32.dp))
        }
      }
      policyText.isBlank() -> {
        Text(
            stringResource(R.string.policy_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp))
      }
      doc == null -> {
        Text(
            stringResource(R.string.policy_parse_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        RawPolicyText(policyText)
      }
      showRaw -> RawPolicyText(policyText)
      filtered == null ->
          StructuredPolicy(
              doc,
              editor = structured,
              onEntry = { entryTarget = it },
              onAddEntry = { section ->
                if (section in policyRuleSections) {
                  // A new rule is neutral by construction: an empty match set.
                  structured.onEdit(
                      StructuredEdit.AddRule(
                          section = section,
                          action = "accept",
                          src = emptyList(),
                          dst = emptyList()))
                } else {
                  addingTo = section
                }
              })
      query.isNotBlank() && matches == 0 ->
          Text(
              stringResource(R.string.policy_search_none),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp))
      else ->
          StructuredPolicy(
              filtered,
              forceExpanded = query.isNotBlank(),
              editor = structured,
              onEntry = { entryTarget = it },
              onAddEntry = { section ->
                if (section in policyRuleSections) {
                  // A new rule is neutral by construction: an empty match set.
                  structured.onEdit(
                      StructuredEdit.AddRule(
                          section = section,
                          action = "accept",
                          src = emptyList(),
                          dst = emptyList()))
                } else {
                  addingTo = section
                }
              })
    }
  }

  entryTarget?.let { target ->
    PolicyEntrySheet(
        target = target,
        editor = structured,
        suggestions = suggestions,
        dstChoices =
            if (entryTarget?.section == "ssh") {
              // ssh dst is an alias to log in to, not a host:port.
              suggestions
            } else {
              dstSuggestions(hosts = hosts, existingDst = existingDst, base = suggestions)
            },
        onDismiss = { entryTarget = null })
  }

  if (confirmSave) {
    ConfirmDialog(
        title = stringResource(R.string.policy_save_title),
        message = stringResource(R.string.policy_save_warning),
        confirmLabel = stringResource(R.string.policy_save_confirm),
        onDismiss = { confirmSave = false },
        onConfirm = {
          confirmSave = false
          pendingSave = draft
          onSave(draft)
        })
  }

  if (confirmDiscard) {
    ConfirmDialog(
        title = stringResource(R.string.policy_edit_discard_title),
        message = stringResource(R.string.policy_edit_discard_message),
        confirmLabel = stringResource(R.string.policy_edit_discard),
        onDismiss = { confirmDiscard = false },
        onConfirm = {
          confirmDiscard = false
          draft = policyText
        })
  }
}

/**
 * Raw huJSON editing. The same text the server returns, so nothing is lost in translation; validity
 * is checked locally first and the server rejects what slips through.
 */
@Composable
private fun PolicyEditor(draft: String, invalid: Boolean, saving: Boolean, onChange: (String) -> Unit) {
  Column(Modifier.fillMaxSize()) {
    Text(
        stringResource(R.string.policy_edit_warning),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 8.dp))
    OutlinedTextField(
        value = draft,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        placeholder = { Text(stringResource(R.string.policy_edit_hint)) },
        isError = invalid,
        enabled = !saving)
    if (invalid) {
      Text(
          stringResource(R.string.policy_edit_invalid),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
  }
}


@Composable
private fun RawPolicyText(text: String) {
  Column(
      Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun StructuredPolicy(
    doc: JsonObject,
    forceExpanded: Boolean = false,
    editor: StructuredEditor? = null,
    onEntry: (EntryTarget) -> Unit = {},
    onAddEntry: ((String) -> Unit)? = null,
) {
  val sections = remember(doc) { PolicyDoc.sections(doc) }
  // Rules are what people come here for; the rest stays collapsed.
  val expanded = remember(doc) { mutableStateMapOf<String, Boolean>().apply { put("acls", true) } }

  // Room for the screen's FAB, so the last entry is never covered by it.
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 96.dp)) {
    sections.forEach { key ->
      val value = doc[key]
      val open = forceExpanded || expanded[key] == true
      item("section-$key") {
        SectionHeader(
            name = key,
            value = value,
            open = open,
            onToggle = { expanded[key] = !open },
            onAdd = if (editor != null && key in policyAddableSections) onAddEntry else null)
      }
      if (open) {
        when (value) {
          is JsonArray ->
              items(value.size, key = { "$key-$it" }) { index ->
                EntryBlock(
                    section = key,
                    value = value[index],
                    path = listOf(PolicyEdit.Step.Key(key), PolicyEdit.Step.Index(index)),
                    editor = editor,
                    onEntry = onEntry)
              }
          is JsonObject ->
              items(value.entries.toList(), key = { "$key-${it.key}" }) { (name, v) ->
                NamedValueRow(
                    name = name,
                    value = v,
                    path = listOf(PolicyEdit.Step.Key(key), PolicyEdit.Step.Key(name)),
                    editor = editor,
                    onEntry = onEntry)
              }
          else ->
              item("$key-value") {
                NamedValueRow(
                    name = key,
                    value = value,
                    path = listOf(PolicyEdit.Step.Key(key)),
                    editor = editor,
                    onEntry = onEntry)
              }
        }
      }
      item("divider-$key") { Lists.ItemDivider() }
    }
  }
}

@Composable
private fun SectionHeader(
    name: String,
    value: JsonElement?,
    open: Boolean,
    onToggle: () -> Unit,
    onAdd: ((String) -> Unit)? = null,
) {
  val count = PolicyDoc.countOf(value)
  ListItemShell(onClick = onToggle) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
            sectionTitle(name)?.let { stringResource(it) } ?: name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      count?.let {
        Text(
            stringResource(R.string.policy_items, it),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      onAdd?.let { add ->
        TextButton(onClick = { add(name) }) { Text(stringResource(R.string.policy_edit_add_value)) }
      }
      Icon(
          if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = null,
          modifier = Modifier.padding(start = 8.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** One rule (acls/ssh/tests entry): its fields, one per line, with the action called out. */
@Composable
private fun EntryBlock(
    section: String,
    value: JsonElement,
    path: List<PolicyEdit.Step>,
    editor: StructuredEditor?,
    onEntry: (EntryTarget) -> Unit,
) {
  val rule = value as? JsonObject ?: run {
    NamedValueRow(name = section, value = value)
    return
  }
  /** Opens the editor for this rule (or does nothing when the view is read-only). */
  fun openEditor() {
    if (editor == null) return
    onEntry(
        EntryTarget(
            title = ruleTitle(rule),
            path = path,
            action = (rule["action"] as? JsonPrimitive)?.contentOrNull ?: "accept",
            src = (rule["src"] as? JsonArray)?.mapNotNull { it.stringOrNull() },
            dst = (rule["dst"] as? JsonArray)?.mapNotNull { it.stringOrNull() },
            users = (rule["users"] as? JsonArray)?.mapNotNull { it.stringOrNull() },
            section = section))
  }

  Row(
      Modifier.fillMaxWidth()
          .clickable(enabled = editor != null) { openEditor() }
          .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
    PolicyDoc.ruleFields(rule).forEach { (name, field) ->
      val strings = PolicyDoc.stringsOf(field)
      if (name == "action" && strings.size == 1) {
        ActionBadge(strings.single())
      } else if (strings.isNotEmpty()) {
        FieldLine(
            label = fieldLabel(name)?.let { stringResource(it) } ?: name,
            value = strings.joinToString(", "))
      } else {
        FieldLine(label = name, value = compactJson(field))
      }
    }
        }
        if (editor != null) {
          Icon(
              Icons.Default.Edit,
              contentDescription = stringResource(R.string.policy_edit_entry),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
      }
}

@Composable
private fun ActionBadge(action: String) {
  val accepted = action.equals("accept", ignoreCase = true)
  Box(
      Modifier.padding(vertical = 2.dp)
          .background(
              if (accepted) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
              else MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
              RoundedCornerShape(50))
          .padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(
            action,
            style = MaterialTheme.typography.labelMedium,
            color = if (accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
      }
}

@Composable
private fun FieldLine(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 10.dp))
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.weight(1f))
  }
}

/** A named member of a section: "nas: 100.64.0.9", "tag:router: [group:family]". */
@Composable
private fun NamedValueRow(
    name: String,
    value: JsonElement?,
    path: List<PolicyEdit.Step> = emptyList(),
    editor: StructuredEditor? = null,
    onEntry: (EntryTarget) -> Unit = {},
) {
  val strings = PolicyDoc.stringsOf(value)
  ListItemShell(
      onClick = {
        editor?.let {
          onEntry(
              EntryTarget(
                  title = name,
                  path = path,
                  value = if (value is JsonPrimitive) value.contentOrNull else null,
                  src = if (value is JsonArray) strings else null))
        }
      }) {
    Column(Modifier.fillMaxWidth()) {
      Text(
          name,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis)
      val text = if (strings.isNotEmpty()) strings.joinToString(", ") else compactJson(value)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f).padding(top = 2.dp))
        if (editor != null) {
          Icon(
              Icons.Default.Edit,
              contentDescription = stringResource(R.string.policy_edit_entry),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 8.dp).size(24.dp))
        }
      }
    }
  }
}

@Composable
private fun ListItemShell(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
  Box(
      Modifier.fillMaxWidth()
          .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
          .padding(horizontal = 16.dp, vertical = 8.dp)) {
        content()
      }
}

private fun compactJson(value: JsonElement?): String =
    when (value) {
      null -> ""
      is JsonPrimitive -> value.contentOrNull.orEmpty()
      else -> value.toString()
    }

/** Localized name for a policy section; unknown sections keep their raw key. */
private fun sectionTitle(key: String): Int? =
    when (key) {
      "acls" -> R.string.policy_sec_acls
      "groups" -> R.string.policy_sec_groups
      "hosts" -> R.string.policy_sec_hosts
      "tagOwners" -> R.string.policy_sec_tagowners
      "autoApprovers" -> R.string.policy_sec_autoapprovers
      "ssh" -> R.string.policy_sec_ssh
      "nodeAttrs" -> R.string.policy_sec_nodeattrs
      "postures" -> R.string.policy_sec_postures
      "tests" -> R.string.policy_sec_tests
      "sshTests" -> R.string.policy_sec_sshtests
      else -> null
    }

/** Localized name for a rule field; unknown fields keep their raw key. */
private fun fieldLabel(key: String): Int? =
    when (key) {
      "src" -> R.string.policy_field_src
      "dst" -> R.string.policy_field_dst
      "users" -> R.string.policy_field_users
      "ports" -> R.string.policy_field_ports
      "action" -> R.string.policy_field_action
      "accept" -> R.string.policy_field_accept
      "deny" -> R.string.policy_field_deny
      else -> null
    }

/** Renders a rule the way its section needs it: ssh rules carry the users that may log in. */
private fun ruleJson(section: String, action: String, src: List<String>, dst: List<String>): String {
  val lists =
      "\"action\": ${PolicyEdit.quote(action)}, " +
          "\"src\": [${src.joinToString(", ") { PolicyEdit.quote(it) }}], " +
          "\"dst\": [${dst.joinToString(", ") { PolicyEdit.quote(it) }}]"
  // headscale rejects an ssh rule without users ("users must be specified"), so the template
  // carries one; an empty match set still means the rule allows nothing until it is filled in.
  return if (section == "ssh") "{ $lists, \"users\": [\"root\"] }" else "{ $lists }"
}

/** Short one-line label for a rule, reused as the editor sheet's title. */
private fun ruleTitle(rule: JsonObject): String {
  val action = (rule["action"] as? JsonPrimitive)?.contentOrNull ?: "accept"
  val src = PolicyDoc.stringsOf(rule["src"]).joinToString(", ")
  val dst = PolicyDoc.stringsOf(rule["dst"]).joinToString(", ")
  return "$action  $src  →  $dst"
}

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
