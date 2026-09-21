// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.AdminApi
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.LoadingIndicator
import com.tailscale.ipn.ui.util.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app tailnet administration (read + the common write actions) backed by the Headscale
 * HTTP API. Deliberately a small subset of the web console: device actions, pre-auth keys
 * and tailnet state at a glance.
 */
@Composable
fun AdminConsoleView(backToSettings: BackNavigation) {
  val scope = rememberCoroutineScope()

  var baseUrl by remember { mutableStateOf(AdminApi.baseUrl() ?: defaultBaseUrl()) }
  var apiKey by remember { mutableStateOf(AdminApi.apiKey().orEmpty()) }
  var status by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var configured by remember { mutableStateOf(AdminApi.isConfigured()) }

  val nodes = remember { mutableStateListOf<AdminApi.HsNode>() }
  val keys = remember { mutableStateListOf<AdminApi.HsPreAuthKey>() }

  var pendingExpire by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingDelete by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingRename by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingExpireKey by remember { mutableStateOf<AdminApi.HsPreAuthKey?>(null) }
  var showCreateKey by remember { mutableStateOf(false) }

  fun reload() {
    scope.launch {
      busy = true
      status = null
      try {
        val n = withContext(Dispatchers.IO) { AdminApi.nodes() }
        nodes.clear()
        nodes.addAll(n.sortedBy { it.displayName.lowercase() })
        val k = withContext(Dispatchers.IO) { AdminApi.preAuthKeys() }
        keys.clear()
        keys.addAll(k)
        configured = true
        status = null
      } catch (e: Exception) {
        status = e.message ?: e.javaClass.simpleName
      } finally {
        busy = false
      }
    }
  }

  LaunchedEffect(configured) {
    if (configured && nodes.isEmpty() && !busy) reload()
  }

  Scaffold(topBar = { Header(R.string.in_app_admin, onBack = backToSettings) }) { innerPadding ->
    LoadingIndicator.Wrap {
      LazyColumn(Modifier.padding(innerPadding)) {
        item("connection") {
          Lists.SectionDivider(stringResource(R.string.admin_connection))
          Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.admin_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.admin_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Button(
                  enabled = !busy,
                  onClick = {
                    AdminApi.saveConnection(baseUrl, apiKey)
                    reload()
                  }) {
                    Text(stringResource(R.string.admin_save_and_test))
                  }
              status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3)
              }
            }
          }
        }

        if (!configured) {
          item("hint") {
            Text(
                stringResource(R.string.admin_needs_config),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
          }
        } else {
          item("nodesHeader") {
            Lists.SectionDivider(stringResource(R.string.admin_nodes))
          }
          items(nodes, key = { it.id }) { node -> NodeRow(node, busy, onExpire = { pendingExpire = node }, onDelete = { pendingDelete = node }, onRename = { pendingRename = node }) }

          item("keysHeader") {
            Lists.SectionDivider(stringResource(R.string.admin_preauth_keys))
          }
          items(keys, key = { it.id }) { key -> PreAuthKeyRow(key, onExpire = { pendingExpireKey = key }) }
          item("newKey") {
            TextButton(
                onClick = { showCreateKey = true },
                modifier = Modifier.padding(horizontal = 8.dp)) {
                  Text(stringResource(R.string.admin_new_preauth_key), color = MaterialTheme.colorScheme.link)
                }
          }
        }
      }
    }
  }

  pendingExpire?.let { node ->
    ConfirmDialog(
        title = stringResource(R.string.admin_action_expire),
        message = node.displayName,
        confirmLabel = stringResource(R.string.admin_action_expire),
        onDismiss = { pendingExpire = null },
        onConfirm = {
          pendingExpire = null
          scope.launch {
            runAction { AdminApi.expireNode(node.id) }
            reload()
          }
        })
  }

  pendingDelete?.let { node ->
    ConfirmDialog(
        title = stringResource(R.string.admin_confirm_delete),
        message = node.displayName,
        confirmLabel = stringResource(R.string.admin_action_delete),
        onDismiss = { pendingDelete = null },
        onConfirm = {
          pendingDelete = null
          scope.launch {
            runAction { AdminApi.deleteNode(node.id) }
            reload()
          }
        })
  }

  pendingRename?.let { node ->
    var newName by remember { mutableStateOf(node.displayName) }
    AlertDialog(
        onDismissRequest = { pendingRename = null },
        title = { Text(stringResource(R.string.admin_action_rename)) },
        text = {
          OutlinedTextField(
              value = newName, onValueChange = { newName = it }, singleLine = true)
        },
        confirmButton = {
          TextButton(
              onClick = {
                pendingRename = null
                scope.launch {
                  runAction { AdminApi.renameNode(node.id, newName) }
                  reload()
                }
              }) {
                Text(stringResource(R.string.admin_action_rename))
              }
        },
        dismissButton = {
          TextButton(onClick = { pendingRename = null }) { Text(stringResource(R.string.cancel)) }
        })
  }

  pendingExpireKey?.let { key ->
    ConfirmDialog(
        title = stringResource(R.string.admin_action_expire),
        message = key.key,
        confirmLabel = stringResource(R.string.admin_action_expire),
        onDismiss = { pendingExpireKey = null },
        onConfirm = {
          pendingExpireKey = null
          scope.launch {
            runAction { AdminApi.expirePreAuthKey(key.key) }
            reload()
          }
        })
  }

  if (showCreateKey) {
    CreatePreAuthKeyDialog(
        onDismiss = { showCreateKey = false },
        onCreate = { user, reusable, ephemeral, days ->
          showCreateKey = false
          scope.launch {
            runAction {
              AdminApi.createPreAuthKey(
                  user = user,
                  reusable = reusable,
                  ephemeral = ephemeral,
                  expirationRfc3339 =
                      if (days <= 0) null
                      else java.time.Instant.now().plusSeconds(days * 86400L).toString())
            }
            reload()
          }
        })
  }
}

/** Runs an API call and reports failures through the shared status line. */
private suspend fun runAction(block: suspend () -> Unit) {
  try {
    withContext(Dispatchers.IO) { block() }
  } catch (e: Exception) {
    // Surfaced by the caller's reload(); log for debugging.
    com.tailscale.ipn.util.TSLog.e("AdminConsoleView", "admin action failed", e)
  }
}

@Composable
private fun NodeRow(
    node: AdminApi.HsNode,
    busy: Boolean,
    onExpire: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  ListItem(
      headlineContent = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(node.displayName, style = MaterialTheme.typography.bodyMedium)
          Text(
              if (node.online) "●" else "○",
              style = MaterialTheme.typography.bodySmall,
              color = if (node.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      supportingContent = {
        val parts = mutableListOf<String>()
        node.user?.name?.takeIf { it.isNotBlank() }?.let { parts += it }
        node.ipAddresses.firstOrNull()?.let { parts += it }
        node.expiry?.take(10)?.let { parts += it }
        node.validTags.takeIf { it.isNotEmpty() }?.let { parts += it.joinToString(" ") }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2)
      },
      trailingContent = {
        Box {
          IconButton(onClick = { menuOpen = true }, enabled = !busy) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.admin_device_actions))
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_action_rename)) },
                onClick = {
                  menuOpen = false
                  onRename()
                })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_action_expire)) },
                onClick = {
                  menuOpen = false
                  onExpire()
                })
            DropdownMenuItem(
                text = {
                  Text(stringResource(R.string.admin_action_delete), color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                  menuOpen = false
                  onDelete()
                })
          }
        }
      })
}

@Composable
private fun PreAuthKeyRow(key: AdminApi.HsPreAuthKey, onExpire: () -> Unit) {
  val clipboard = LocalClipboardManager.current
  var menuOpen by remember { mutableStateOf(false) }
  ListItem(
      headlineContent = {
        Text(key.key, style = MaterialTheme.typography.bodySmall, maxLines = 2)
      },
      supportingContent = {
        val parts = mutableListOf<String>()
        key.user?.name?.let { parts += it }
        if (key.reusable) parts += stringResource(R.string.admin_reusable)
        if (key.ephemeral) parts += stringResource(R.string.admin_ephemeral)
        key.expiration?.take(10)?.let { parts += it }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2)
      },
      trailingContent = {
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.admin_key_actions))
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_to_clipboard)) },
                onClick = {
                  menuOpen = false
                  clipboard.setText(AnnotatedString(key.key))
                })
            DropdownMenuItem(
                text = {
                  Text(stringResource(R.string.admin_action_expire), color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                  menuOpen = false
                  onExpire()
                })
          }
        }
      })
}

@Composable
private fun CreatePreAuthKeyDialog(
    onDismiss: () -> Unit,
    onCreate: (user: String, reusable: Boolean, ephemeral: Boolean, days: Long) -> Unit,
) {
  var user by remember { mutableStateOf("") }
  var reusable by remember { mutableStateOf(true) }
  var ephemeral by remember { mutableStateOf(false) }
  var days by remember { mutableStateOf("30") }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.admin_new_preauth_key)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = user,
              onValueChange = { user = it },
              label = { Text(stringResource(R.string.admin_user)) },
              singleLine = true)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = reusable, onCheckedChange = { reusable = it })
            Text(stringResource(R.string.admin_reusable))
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ephemeral, onCheckedChange = { ephemeral = it })
            Text(stringResource(R.string.admin_ephemeral))
          }
          OutlinedTextField(
              value = days,
              onValueChange = { days = it.filter(Char::isDigit) },
              label = { Text(stringResource(R.string.admin_expiration_days)) },
              singleLine = true)
        }
      },
      confirmButton = {
        TextButton(
            enabled = user.isNotBlank(),
            onClick = { onCreate(user.trim(), reusable, ephemeral, days.toLongOrNull() ?: 0L) }) {
              Text(stringResource(R.string.admin_create))
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = onConfirm) {
          Text(confirmLabel, color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Pre-fill the API base URL from the configured admin console URL, if any. */
private fun defaultBaseUrl(): String {
  val admin = ServerConfig.getAdminUrl().trimEnd('/')
  return if (admin.endsWith("/admin")) admin.removeSuffix("/admin") else admin
}
