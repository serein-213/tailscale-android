// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.PolicyEdit

/**
 * A structured change to the policy. The tab applies these through [PolicyEdit], so every edit goes
 * down the same byte-splicing path the unit tests cover; this file never touches policy text.
 */
sealed interface StructuredEdit {
  /** accept ↔ deny on an existing rule. */
  data class SetAction(val path: List<PolicyEdit.Step>, val action: String) : StructuredEdit

  /** A list field (a rule's src/dst, the members of a group) gets a new set of values. */
  data class SetList(val path: List<PolicyEdit.Step>, val values: List<String>) : StructuredEdit

  /** A single value, such as a host's address, is replaced. */
  data class SetValue(val path: List<PolicyEdit.Step>, val value: String) : StructuredEdit

  /** The rule, member or hosts entry at [path] goes away. */
  data class RemoveEntry(val path: List<PolicyEdit.Step>) : StructuredEdit

  /** A new named entry (a host, a group, a tag owner) is added to [section]. */
  data class AddEntry(val section: String, val name: String, val value: String? = null) :
      StructuredEdit

  /** A new rule is appended to [section]. */
  data class AddRule(
      val section: String,
      val action: String,
      val src: List<String>,
      val dst: List<String>,
  ) : StructuredEdit
}

/** What the structured view may offer, and who to tell when the user picks something. */
data class StructuredEditor(
    val users: List<String>,
    val tags: List<String>,
    val onEdit: (StructuredEdit) -> Boolean,
)

/** What the entry sheet is looking at: a rule, a list, or a single value. */
data class EntryTarget(
    val title: String,
    val path: List<PolicyEdit.Step>,
    val action: String? = null,
    val src: List<String>? = null,
    val dst: List<String>? = null,
    val value: String? = null,
    /** ssh rules carry the local users that may log in; acls rules do not. */
    val users: List<String>? = null,
    /** Which section the entry lives in: dst means different things in acls and ssh. */
    val section: String? = null,
    val srcPath: List<PolicyEdit.Step> = path + PolicyEdit.Step.Key("src"),
)

/** Local users an ssh rule can grant: the usual ones plus whoever the console knows. */
fun sshUserSuggestions(editor: StructuredEditor): List<String> = buildList {
  add("root")
  add("autogroup:nonroot")
  addAll(editor.users)
}
    .distinct()
    .sorted()

/** Suggestion list for a value: what the policy already defines, plus the console's own data. */
fun policySuggestions(
    users: List<String>,
    tags: List<String>,
    groups: List<String>,
    hosts: List<String>,
    tagOwners: List<String>,
): List<String> = buildList {
  add("*")
  addAll(
      listOf(
          "autogroup:internet",
          "autogroup:member",
          "autogroup:self",
          "autogroup:tagged",
      )
  )
  addAll(groups)
  addAll(tagOwners)
  addAll(hosts)
  addAll(tags)
  addAll(users)
}
    .distinct()
    .sorted()

/**
 * Values a dst may take: what the policy already uses plus every host with a wildcard port, since a
 * dst without a port is the mistake the server reports first.
 */
fun dstSuggestions(
    hosts: List<String>,
    existingDst: List<String>,
    base: List<String>,
): List<String> = buildList {
  addAll(base.filterNot { it.contains('.') || it == "*" })
  add("*:*")
  add("autogroup:internet:*")
  addAll(existingDst)
  addAll(hosts.map { "$it:*" })
}
    .distinct()
    .sorted()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyEntrySheet(
    target: EntryTarget,
    editor: StructuredEditor,
    suggestions: List<String>,
    dstChoices: List<String>,
    onDismiss: () -> Unit,
) {
  var action by remember(target) { mutableStateOf(target.action ?: "accept") }
  var src by remember(target) { mutableStateOf(target.src.orEmpty()) }
  var dst by remember(target) { mutableStateOf(target.dst.orEmpty()) }
  var value by remember(target) { mutableStateOf(target.value.orEmpty()) }
  var users by remember(target) { mutableStateOf(target.users.orEmpty()) }
  var confirmRemove by remember(target) { mutableStateOf(false) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
      Text(
          target.title,
          style = MaterialTheme.typography.headlineSmall,
          fontFamily = FontFamily.Monospace,
      )

      if (target.action != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
          listOf("accept", "deny").forEach { choice ->
            FilterChip(
                selected = action == choice,
                onClick = {
                  if (editor.onEdit(StructuredEdit.SetAction(target.path, choice))) action = choice
                },
                label = { Text(choice) },
            )
          }
        }
      }

      if (target.src != null) {
        ValueEditor(
            label = stringResource(R.string.policy_field_src),
            values = src,
            suggestions = suggestions,
            onAdd = { picked ->
              val next = (src + picked).distinct()
              if (next != src && editor.onEdit(StructuredEdit.SetList(target.srcPath, next))) {
                src = next
              }
            },
            onRemove = { item ->
              val next = src - item
              if (editor.onEdit(StructuredEdit.SetList(target.srcPath, next))) src = next
            },
        )
      }

      if (target.dst != null) {
        ValueEditor(
            label = stringResource(R.string.policy_field_dst),
            values = dst,
            suggestions = dstChoices,
            onAdd = { picked ->
              val next = (dst + picked).distinct()
              if (
                  next != dst &&
                      editor.onEdit(
                          StructuredEdit.SetList(target.path + PolicyEdit.Step.Key("dst"), next)
                      )
              ) {
                dst = next
              }
            },
            onRemove = { item ->
              val next = dst - item
              if (
                  editor.onEdit(
                      StructuredEdit.SetList(target.path + PolicyEdit.Step.Key("dst"), next)
                  )
              ) {
                dst = next
              }
            },
        )
      }

      if (target.users != null) {
        ValueEditor(
            label = stringResource(R.string.policy_field_users),
            values = users,
            suggestions = sshUserSuggestions(editor),
            onAdd = { picked ->
              val next = (users + picked).distinct()
              if (
                  next != users &&
                      editor.onEdit(
                          StructuredEdit.SetList(target.path + PolicyEdit.Step.Key("users"), next)
                      )
              ) {
                users = next
              }
            },
            onRemove = { item ->
              val next = users - item
              if (
                  editor.onEdit(
                      StructuredEdit.SetList(target.path + PolicyEdit.Step.Key("users"), next)
                  )
              ) {
                users = next
              }
            },
        )
      }

      if (target.value != null) {
        ValueEditor(
            label = stringResource(R.string.policy_edit_value),
            values = listOf(value).filter { it.isNotEmpty() },
            suggestions = suggestions,
            // A single value: the first pick replaces it, there is nothing to collect.
            multiple = false,
            onAdd = { picked ->
              picked.firstOrNull()?.let {
                if (editor.onEdit(StructuredEdit.SetValue(target.path, it))) value = it
              }
            },
            onRemove = {
              // Clearing a value is not a thing: the entry itself goes.
              if (editor.onEdit(StructuredEdit.RemoveEntry(target.path))) onDismiss()
            },
        )
      }

      TextButton(onClick = { confirmRemove = true }, modifier = Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(R.string.policy_edit_remove_entry),
            color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }

  if (confirmRemove) {
    AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(stringResource(R.string.policy_edit_remove_title)) },
        text = { Text(target.title) },
        confirmButton = {
          TextButton(
              onClick = {
                confirmRemove = false
                if (editor.onEdit(StructuredEdit.RemoveEntry(target.path))) onDismiss()
              }
          ) {
            Text(
                stringResource(R.string.policy_edit_remove_entry),
                color = MaterialTheme.colorScheme.error,
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
  }
}

/** A list field: its values, an add button that opens the picker, and a remove button per value. */
@Composable
private fun ValueEditor(
    label: String,
    values: List<String>,
    suggestions: List<String>,
    multiple: Boolean = true,
    onAdd: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
) {
  var picking by remember { mutableStateOf(false) }
  Row(
      Modifier.fillMaxWidth().padding(top = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = { picking = true }) {
      Text(stringResource(R.string.policy_edit_add_value))
    }
  }
  if (values.isEmpty()) {
    Text(
        stringResource(R.string.policy_edit_none),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  values.forEach { item -> ValueRow(item) { onRemove(item) } }
  if (picking) {
    ValuePickerDialog(
        current = values,
        suggestions = suggestions,
        multiple = multiple,
        onDismiss = { picking = false },
        onPick = { picked ->
          picking = false
          onAdd(picked)
        },
    )
  }
}

@Composable
private fun ValueRow(value: String, onRemove: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onRemove) {
      Text(stringResource(R.string.policy_edit_remove), color = MaterialTheme.colorScheme.error)
    }
  }
}

/**
 * Name (and for hosts an address) for a new entry. The name and initial value are spliced together,
 * so a failed edit cannot leave behind a partially created host.
 */
@Composable
fun AddEntryDialog(
    section: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, value: String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var value by remember { mutableStateOf("") }
  val hosts = section == "hosts"
  val validName = name.isNotBlank() && !name.contains(' ')

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.policy_edit_new_entry, section)) },
      text = {
        Column {
          OutlinedTextField(
              value = name,
              onValueChange = { name = it },
              singleLine = true,
              label = { Text(stringResource(R.string.policy_edit_entry_name)) },
              modifier = Modifier.fillMaxWidth(),
              textStyle =
                  MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          )
          if (hosts) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.policy_edit_entry_address)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
          }
        }
      },
      confirmButton = {
        TextButton(enabled = validName, onClick = { onAdd(name.trim(), value.trim()) }) {
          Text(stringResource(R.string.policy_edit_add))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

/**
 * Free text and/or the values the policy already knows about. With [multiple], several can be
 * ticked and added in one go: a rule with three sources should not take three trips through here.
 */
@Composable
private fun ValuePickerDialog(
    current: List<String>,
    suggestions: List<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onPick: (List<String>) -> Unit,
) {
  var typed by remember { mutableStateOf("") }
  var ticked by remember { mutableStateOf(emptyList<String>()) }
  // Typing narrows the list, so a long tailnet is still one or two keystrokes away.
  val shown = suggestions.filter { it !in current && it.contains(typed.trim(), ignoreCase = true) }
  val picked = (ticked + typed.trim()).filter { it.isNotEmpty() }.distinct()
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.policy_edit_add_value)) },
      text = {
        Column {
          OutlinedTextField(
              value = typed,
              onValueChange = { typed = it },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions =
                  KeyboardActions(onDone = { if (picked.isNotEmpty()) onPick(picked) }),
              modifier = Modifier.fillMaxWidth(),
              textStyle =
                  MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          )
          LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            items(shown, key = { it }) { suggestion ->
              Row(
                  Modifier.fillMaxWidth().clickable {
                    if (!multiple) {
                      onPick(listOf(suggestion))
                    } else {
                      ticked =
                          if (suggestion in ticked) ticked - suggestion else ticked + suggestion
                    }
                  },
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                if (multiple) Checkbox(checked = suggestion in ticked, onCheckedChange = null)
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(enabled = picked.isNotEmpty(), onClick = { onPick(picked) }) {
          Text(
              if (picked.size > 1) "${stringResource(R.string.policy_edit_add)} (${picked.size})"
              else stringResource(R.string.policy_edit_add)
          )
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
