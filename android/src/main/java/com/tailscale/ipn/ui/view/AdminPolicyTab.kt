// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.PolicyDoc
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.util.Lists
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The access policy, laid out instead of dumped: sections that expand, one block per rule, and a
 * raw view for when the text is not parseable or someone wants the original.
 */
@Composable
fun AdminPolicyTab(policyText: String, updatedAt: String?, loading: Boolean) {
  val clipboard = LocalClipboardManager.current
  var showRaw by remember { mutableStateOf(false) }
  val doc = remember(policyText) { PolicyDoc.parse(policyText) }

  Column(Modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
          if (doc != null) {
            ViewToggle(
                label = stringResource(R.string.policy_view_structured),
                selected = !showRaw,
                onClick = { showRaw = false })
            ViewToggle(
                label = stringResource(R.string.policy_view_raw),
                selected = showRaw,
                onClick = { showRaw = true })
          }
          Box(Modifier.weight(1f))
          TextButton(
              enabled = policyText.isNotEmpty(),
              onClick = { clipboard.setText(AnnotatedString(policyText)) }) {
                Text(stringResource(R.string.copy_to_clipboard), color = MaterialTheme.colorScheme.link)
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
      else -> StructuredPolicy(doc)
    }
  }
}

@Composable
private fun ViewToggle(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
      Modifier.padding(top = 4.dp, end = 4.dp)
          .background(
              if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              RoundedCornerShape(50))
          .clickable(onClick = onClick)
          .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
      }
}

@Composable
private fun RawPolicyText(text: String) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun StructuredPolicy(doc: JsonObject) {
  val sections = remember(doc) { PolicyDoc.sections(doc) }
  // Rules are what people come here for; the rest stays collapsed.
  val expanded = remember(doc) { mutableStateMapOf<String, Boolean>().apply { put("acls", true) } }

  LazyColumn(Modifier.fillMaxSize()) {
    sections.forEach { key ->
      val value = doc[key]
      val open = expanded[key] == true
      item("section-$key") {
        SectionHeader(
            name = key,
            value = value,
            open = open,
            onToggle = { expanded[key] = !open })
      }
      if (open) {
        when (value) {
          is JsonArray ->
              items(value.size, key = { "$key-$it" }) { index ->
                EntryBlock(section = key, value = value[index])
              }
          is JsonObject ->
              items(value.entries.toList(), key = { "$key-${it.key}" }) { (name, v) ->
                NamedValueRow(name = name, value = v)
              }
          else -> item("$key-value") { NamedValueRow(name = key, value = value) }
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
private fun EntryBlock(section: String, value: JsonElement) {
  val rule = value as? JsonObject ?: run {
    NamedValueRow(name = section, value = value)
    return
  }
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
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
private fun NamedValueRow(name: String, value: JsonElement?) {
  val strings = PolicyDoc.stringsOf(value)
  ListItemShell {
    Column(Modifier.fillMaxWidth()) {
      Text(
          name,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis)
      val text = if (strings.isNotEmpty()) strings.joinToString(", ") else compactJson(value)
      Text(
          text,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(top = 2.dp))
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
