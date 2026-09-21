// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.AdminApi
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.LoadingIndicator
import com.tailscale.ipn.ui.util.ServerConfig
import com.tailscale.ipn.util.TSLog
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
  val TAG = "AdminConsoleView"
  val scope = rememberCoroutineScope()

  var baseUrl by remember { mutableStateOf(AdminApi.baseUrl() ?: defaultBaseUrl()) }
  var apiKey by remember { mutableStateOf(AdminApi.apiKey().orEmpty()) }
  var status by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var configured by remember { mutableStateOf(AdminApi.isConfigured()) }

  val nodes = remember { mutableStateListOf<AdminApi.HsNode>() }
  val keys = remember { mutableStateListOf<AdminApi.HsPreAuthKey>() }
  val users = remember { mutableStateListOf<AdminApi.HsUser>() }

  var selectedTab by remember { mutableIntStateOf(0) }
  var showConnection by remember { mutableStateOf(!AdminApi.isConfigured()) }
  var policy by remember { mutableStateOf<AdminApi.HsPolicy?>(null) }
  var policyLoading by remember { mutableStateOf(false) }

  var pendingExpire by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingDelete by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingRename by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingRoutes by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingTags by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingExpireKey by remember { mutableStateOf<AdminApi.HsPreAuthKey?>(null) }
  var pendingRenameUser by remember { mutableStateOf<AdminApi.HsUser?>(null) }
  var pendingDeleteUser by remember { mutableStateOf<AdminApi.HsUser?>(null) }
  var showCreateKey by remember { mutableStateOf(false) }
  var showCreateUser by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }

  fun reload() {
    scope.launch {
      busy = true
      try {
        val n = withContext(Dispatchers.IO) { AdminApi.nodes() }
        nodes.clear()
        nodes.addAll(n.sortedBy { it.displayName.lowercase() })
        val k = withContext(Dispatchers.IO) { AdminApi.preAuthKeys() }
        keys.clear()
        keys.addAll(k)
        val u = withContext(Dispatchers.IO) { AdminApi.users() }
        users.clear()
        users.addAll(u)
        configured = true
        status = null
      } catch (e: Exception) {
        status = e.message ?: e.javaClass.simpleName
      } finally {
        busy = false
      }
    }
  }

  /** Policies are large and only shown on demand: load them when that tab is opened. */
  fun loadPolicy() {
    scope.launch {
      policyLoading = true
      runCatching { withContext(Dispatchers.IO) { AdminApi.policy() } }
          .onSuccess { policy = it }
          .onFailure { TSLog.w(TAG, "policy load failed: ${it.message}") }
      policyLoading = false
    }
  }

  fun refresh() {
    reload()
    if (selectedTab == 3) loadPolicy()
  }

  /**
   * Runs a mutating call and surfaces failures. Returns false when it failed so the caller skips
   * the follow-up reload, which would otherwise wipe the error from the screen.
   */
  suspend fun runAction(block: suspend () -> Unit): Boolean =
      try {
        withContext(Dispatchers.IO) { block() }
        true
      } catch (e: Exception) {
        val message = e.message ?: e.javaClass.simpleName
        TSLog.e(TAG, "admin action failed", e)
        snackbarHostState.showSnackbar(message, withDismissAction = true)
        false
      }

  LaunchedEffect(configured) {
    if (configured && nodes.isEmpty() && !busy) reload()
  }

  Scaffold(
      topBar = {
        Header(
            R.string.in_app_admin,
            onBack = backToSettings,
            actions = {
              TextButton(onClick = { showConnection = true }) {
                Text(stringResource(R.string.admin_connection))
              }
              TextButton(onClick = { refresh() }, enabled = configured && !busy) {
                Text(stringResource(R.string.admin_refresh))
              }
            })
      },
      snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LoadingIndicator.Wrap {
          Column(Modifier.fillMaxSize().padding(innerPadding)) {
            status?.let {
              Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  maxLines = 3,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            if (!configured) {
              Text(
                  stringResource(R.string.admin_needs_config),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(16.dp))
              TextButton(
                  onClick = { showConnection = true },
                  modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        stringResource(R.string.admin_connection),
                        color = MaterialTheme.colorScheme.link)
                  }
            } else {
              // One section at a time: a 26-device tailnet plus users, keys and policy in a single
              // scroll made everything hard to find.
              TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${stringResource(R.string.admin_nodes)} (${nodes.size})") })
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${stringResource(R.string.admin_users)} (${users.size})") })
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("${stringResource(R.string.admin_keys_short)} (${keys.size})") })
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(stringResource(R.string.admin_policy)) })
              }

              when (selectedTab) {
                0 ->
                    LazyColumn(Modifier.fillMaxSize()) {
                      items(nodes, key = { it.id }) { node ->
                        NodeRow(
                            node = node,
                            busy = busy,
                            onRename = { pendingRename = node },
                            onRoutes = { pendingRoutes = node },
                            onTags = { pendingTags = node },
                            onExpire = { pendingExpire = node },
                            onDelete = { pendingDelete = node })
                      }
                    }
                1 ->
                    LazyColumn(Modifier.fillMaxSize()) {
                      items(users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            onRename = { pendingRenameUser = user },
                            onDelete = { pendingDeleteUser = user })
                      }
                      item("newUser") {
                        TextButton(
                            onClick = { showCreateUser = true },
                            modifier = Modifier.padding(horizontal = 8.dp)) {
                              Text(
                                  stringResource(R.string.admin_new_user),
                                  color = MaterialTheme.colorScheme.link)
                            }
                      }
                    }
                2 ->
                    LazyColumn(Modifier.fillMaxSize()) {
                      items(keys, key = { it.id }) { key ->
                        PreAuthKeyRow(key, onExpire = { pendingExpireKey = key })
                      }
                      item("newKey") {
                        TextButton(
                            onClick = { showCreateKey = true },
                            modifier = Modifier.padding(horizontal = 8.dp)) {
                              Text(
                                  stringResource(R.string.admin_new_preauth_key),
                                  color = MaterialTheme.colorScheme.link)
                            }
                      }
                    }
                else -> {
                  LaunchedEffect(Unit) { if (policy == null) loadPolicy() }
                  AdminPolicyTab(
                      policyText = policy?.policy.orEmpty(),
                      updatedAt = policy?.updatedAt,
                      loading = policyLoading)
                }
              }
            }
          }
        }
      }

  if (showConnection) {
    ConnectionDialog(
        baseUrl = baseUrl,
        apiKey = apiKey,
        busy = busy,
        status = status,
        onBaseUrlChange = { baseUrl = it },
        onApiKeyChange = { apiKey = it },
        onDismiss = { showConnection = false },
        onSave = {
          AdminApi.saveConnection(baseUrl, apiKey)
          showConnection = false
          reload()
        })
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
            if (runAction { AdminApi.expireNode(node.id) }) reload()
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
            if (runAction { AdminApi.deleteNode(node.id) }) reload()
          }
        })
  }

  pendingRename?.let { node ->
    TextInputDialog(
        titleRes = R.string.admin_action_rename,
        initial = node.displayName,
        confirmLabelRes = R.string.admin_action_rename,
        onDismiss = { pendingRename = null },
        onConfirm = { newName ->
          pendingRename = null
          scope.launch {
            if (runAction { AdminApi.renameNode(node.id, newName) }) reload()
          }
        })
  }

  pendingRoutes?.let { node ->
    NodeRoutesDialog(
        node = node,
        onDismiss = { pendingRoutes = null },
        onApply = { routes ->
          pendingRoutes = null
          scope.launch {
            if (runAction { AdminApi.approveRoutes(node.id, routes) }) reload()
          }
        })
  }

  pendingTags?.let { node ->
    TextInputDialog(
        titleRes = R.string.admin_node_tags,
        initial = node.tags.joinToString(", "),
        confirmLabelRes = R.string.admin_save,
        subtitle = stringResource(R.string.admin_tags_hint),
        onDismiss = { pendingTags = null },
        onConfirm = { value ->
          pendingTags = null
          val tags = value.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
          scope.launch {
            if (runAction { AdminApi.setNodeTags(node.id, tags) }) reload()
          }
        })
  }

  if (showCreateUser) {
    TextInputDialog(
        titleRes = R.string.admin_new_user,
        initial = "",
        confirmLabelRes = R.string.admin_create,
        onDismiss = { showCreateUser = false },
        onConfirm = { name ->
          showCreateUser = false
          scope.launch {
            if (runAction { AdminApi.createUser(name) }) reload()
          }
        })
  }

  pendingRenameUser?.let { user ->
    TextInputDialog(
        titleRes = R.string.admin_action_rename,
        initial = user.name,
        confirmLabelRes = R.string.admin_action_rename,
        onDismiss = { pendingRenameUser = null },
        onConfirm = { name ->
          pendingRenameUser = null
          scope.launch {
            if (runAction { AdminApi.renameUser(user.id, name) }) reload()
          }
        })
  }

  pendingDeleteUser?.let { user ->
    ConfirmDialog(
        title = stringResource(R.string.admin_confirm_delete_user),
        message = user.name,
        confirmLabel = stringResource(R.string.admin_action_delete),
        onDismiss = { pendingDeleteUser = null },
        onConfirm = {
          pendingDeleteUser = null
          scope.launch {
            if (runAction { AdminApi.deleteUser(user.id) }) reload()
          }
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
            if (runAction { AdminApi.expirePreAuthKey(key.id) }) reload()
          }
        })
  }

  if (showCreateKey) {
    CreatePreAuthKeyDialog(
        users = users,
        onDismiss = { showCreateKey = false },
        onCreate = { user, reusable, ephemeral, days ->
          showCreateKey = false
          scope.launch {
            if (runAction {
              AdminApi.createPreAuthKey(
                  userId = user.id,
                  reusable = reusable,
                  ephemeral = ephemeral,
                  expirationRfc3339 =
                      if (days <= 0) null
                      else java.time.Instant.now().plusSeconds(days * 86400L).toString())

            }) reload()
          }
        })
  }
}


@Composable
private fun NodeRow(
    node: AdminApi.HsNode,
    busy: Boolean,
    onExpire: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onRoutes: () -> Unit,
    onTags: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  ListItem(
      leadingContent = {
        Box(
            Modifier.size(10.dp)
                .background(
                    if (node.online) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape))
      },
      headlineContent = {
        Text(node.displayName, style = MaterialTheme.typography.bodyMedium)
      },
      supportingContent = {
        val parts = mutableListOf<String>()
        node.user?.name?.takeIf { it.isNotBlank() }?.let { parts += it }
        node.ipAddresses.firstOrNull()?.let { parts += it }
        node.expiry?.take(10)?.takeIf { !isZeroTime(it) }?.let { parts += it }
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
                text = { Text(stringResource(R.string.admin_routes)) },
                onClick = {
                  menuOpen = false
                  onRoutes()
                })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_node_tags)) },
                onClick = {
                  menuOpen = false
                  onTags()
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
        key.expiration?.take(10)?.takeIf { !isZeroTime(it) }?.let { parts += it }
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
private fun UserRow(
    user: AdminApi.HsUser,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  ListItem(
      headlineContent = { Text(user.name, style = MaterialTheme.typography.bodyMedium) },
      trailingContent = {
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.admin_user_actions))
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_action_rename)) },
                onClick = {
                  menuOpen = false
                  onRename()
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
private fun CreatePreAuthKeyDialog(
    users: List<AdminApi.HsUser>,
    onDismiss: () -> Unit,
    onCreate: (user: AdminApi.HsUser, reusable: Boolean, ephemeral: Boolean, days: Long) -> Unit,
) {
  var user by remember { mutableStateOf(users.firstOrNull()) }
  var pickerOpen by remember { mutableStateOf(false) }
  var reusable by remember { mutableStateOf(true) }
  var ephemeral by remember { mutableStateOf(false) }
  var days by remember { mutableStateOf("30") }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.admin_new_preauth_key)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Box {
            OutlinedTextField(
                value = user?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.admin_user)) },
                modifier = Modifier.fillMaxWidth())
            // Transparent overlay: tapping the (read-only) field opens the picker.
            Box(Modifier.matchParentSize().clickable { pickerOpen = true })
            DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
              users.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.name) },
                    onClick = {
                      user = candidate
                      pickerOpen = false
                    })
              }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = reusable, onCheckedChange = { reusable = it })
            Text(stringResource(R.string.admin_reusable))
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ephemeral, onCheckedChange = { ephemeral = it })
            Text(stringResource(R.string.admin_ephemeral))
          }
          val daysValid = (days.toLongOrNull() ?: 0L) > 0
          OutlinedTextField(
              value = days,
              onValueChange = { days = it.filter(Char::isDigit) },
              label = { Text(stringResource(R.string.admin_expiration_days)) },
              isError = !daysValid,
              supportingText = if (daysValid) null else ({ Text(stringResource(R.string.admin_days_required)) }),
              singleLine = true)
        }
      },
      confirmButton = {
        val daysValid = (days.toLongOrNull() ?: 0L) > 0
        TextButton(
            enabled = user != null && daysValid,
            onClick = {
              val selected = user
              if (selected != null && daysValid) {
                onCreate(selected, reusable, ephemeral, days.toLongOrNull() ?: 0L)
              }
            }) {
              Text(stringResource(R.string.admin_create))
            }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Routes advertised by a subnet router / exit node; the API replaces the approved set. */
@Composable
private fun NodeRoutesDialog(
    node: AdminApi.HsNode,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
  val all = remember(node) { (node.availableRoutes + node.approvedRoutes).distinct().sorted() }
  val selected = remember(node) { mutableStateListOf<String>().apply { addAll(node.approvedRoutes) } }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.admin_routes)) },
      text = {
        if (all.isEmpty()) {
          Text(
              stringResource(R.string.admin_no_routes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            all.forEach { route ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected.contains(route),
                    onCheckedChange = { checked ->
                      if (checked) selected.add(route) else selected.remove(route)
                    })
                Text(route, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onApply(selected.toList()) }) {
          Text(stringResource(R.string.admin_save))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun TextInputDialog(
    @StringRes titleRes: Int,
    initial: String,
    @StringRes confirmLabelRes: Int,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  var value by remember { mutableStateOf(initial) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(titleRes)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          OutlinedTextField(
              value = value, onValueChange = { value = it }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
          Text(stringResource(confirmLabelRes))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ConnectionDialog(
    baseUrl: String,
    apiKey: String,
    busy: Boolean,
    status: String?,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.admin_connection)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = baseUrl,
              onValueChange = onBaseUrlChange,
              label = { Text(stringResource(R.string.admin_base_url)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth())
          OutlinedTextField(
              value = apiKey,
              onValueChange = onApiKeyChange,
              label = { Text(stringResource(R.string.admin_api_key)) },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth())
          status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3)
          }
        }
      },
      confirmButton = {
        TextButton(enabled = !busy && baseUrl.isNotBlank(), onClick = onSave) {
          Text(stringResource(R.string.admin_save_and_test))
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

/** Headscale reports "no expiry" as the zero timestamp; showing 0001-01-01 helps nobody. */
private fun isZeroTime(isoDate: String) = isoDate.startsWith("0001-01-01")

/** Pre-fill the API base URL from the configured admin console URL, if any. */
private fun defaultBaseUrl(): String {
  val admin = ServerConfig.getAdminUrl().trimEnd('/')
  return if (admin.endsWith("/admin")) admin.removeSuffix("/admin") else admin
}
