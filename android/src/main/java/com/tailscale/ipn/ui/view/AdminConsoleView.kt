// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.App
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.AdminApi
import com.tailscale.ipn.ui.admin.KeyStatus
import com.tailscale.ipn.ui.admin.NodeList
import com.tailscale.ipn.ui.admin.filterPreAuthKeys
import com.tailscale.ipn.ui.admin.isExpired
import com.tailscale.ipn.ui.admin.status
import com.tailscale.ipn.ui.admin.PolicyBackup
import com.tailscale.ipn.ui.admin.PolicyDoc
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.ServerConfig
import com.tailscale.ipn.util.TSLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * In-app tailnet administration (read + the common write actions) backed by the Headscale
 * HTTP API. Deliberately a small subset of the web console: device actions, pre-auth keys
 * and tailnet state at a glance.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
  var expandedUser by remember { mutableStateOf<String?>(null) }
  var keyStatus by remember { mutableStateOf(KeyStatus.ALL) }
  var keyUserId by remember { mutableStateOf<String?>(null) }
  var nodeQuery by remember { mutableStateOf("") }
  var expandedNode by remember { mutableStateOf<String?>(null) }
  var showConnection by remember { mutableStateOf(!AdminApi.isConfigured()) }
  var policy by remember { mutableStateOf<AdminApi.HsPolicy?>(null) }
  var policyLoading by remember { mutableStateOf(false) }

  var pendingExpire by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingDelete by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingRename by remember { mutableStateOf<AdminApi.HsNode?>(null) }
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
        nodes.addAll(NodeList.sort(n))
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
          .onSuccess {
            policy = it
            // The last policy known to be on the server is the way back from a bad edit.
            PolicyBackup.remember(it.policy)
          }
          .onFailure { TSLog.w(TAG, "policy load failed: ${it.message}") }
      policyLoading = false
    }
  }

  /**
   * Writes the policy back. Headscale validates it server-side; on success the response is the new
   * policy, which is what the editor waits for before leaving edit mode.
   */
  fun savePolicy(text: String) {
    if (!AdminApi.isConfigured()) return
    scope.launch {
      busy = true
      status = null
      try {
        val applied = withContext(Dispatchers.IO) { AdminApi.updatePolicy(text) }
        policy = applied
        // The applied policy is the new way back.
        PolicyBackup.remember(applied.policy)
        snackbarHostState.showSnackbar(App.get().getString(R.string.policy_saved))
      } catch (e: Exception) {
        val message = e.message ?: e.javaClass.simpleName
        TSLog.e(TAG, "policy save failed", e)
        status = message
        snackbarHostState.showSnackbar(message, withDismissAction = true)
      }
      busy = false
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

  /** Devices that advertise or already have routes: the route page is about these. */
  val routableNodes =
      nodes.filter { it.availableRoutes.isNotEmpty() || it.approvedRoutes.isNotEmpty() }

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
      snackbarHost = { SnackbarHost(snackbarHostState) },
      floatingActionButton = {
        when {
          !configured -> Unit
          selectedTab == 1 ->
              FloatingActionButton(onClick = { showCreateUser = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.admin_new_user))
              }
          selectedTab == 2 ->
              FloatingActionButton(onClick = { showCreateKey = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.admin_new_preauth_key))
              }
          else -> Unit
        }
      }) { innerPadding ->
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
                    text = { Text("${stringResource(R.string.admin_routes_short)} (${routableNodes.size})") })
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text(stringResource(R.string.admin_policy_tab)) })
              }

              if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
              PullToRefreshBox(isRefreshing = busy, onRefresh = { refresh() }) {
              when (selectedTab) {
                0 -> {
                  val visibleNodes = NodeList.search(nodes, nodeQuery)
                  Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = nodeQuery,
                        onValueChange = { nodeQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                          if (nodeQuery.isNotEmpty()) {
                            IconButton(onClick = { nodeQuery = "" }) {
                              Icon(
                                  Icons.Default.Close,
                                  contentDescription = stringResource(R.string.admin_clear))
                            }
                          }
                        },
                        placeholder = { Text(stringResource(R.string.admin_search_devices)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)) {
                          if (visibleNodes.isEmpty()) {
                            item("noNodes") {
                              EmptyHint(
                                  if (nodeQuery.isBlank()) R.string.admin_no_nodes
                                  else R.string.admin_no_matches)
                            }
                          }
                          items(visibleNodes, key = { it.id }) { node ->
                            NodeRow(
                                node = node,
                                busy = busy,
                                expanded = expandedNode == node.id,
                                onToggleExpanded = {
                                  expandedNode = if (expandedNode == node.id) null else node.id
                                },
                                onRename = { pendingRename = node },
                                onTags = { pendingTags = node },
                                onExpire = { pendingExpire = node },
                                onDelete = { pendingDelete = node })
                          }
                        }
                  }
                }
                1 ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)) {
                      items(users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            expanded = expandedUser == user.id,
                            devices = nodes.filter { it.user?.name == user.name },
                            keys = keys.filter { it.user?.name == user.name },
                            tags = tagsOwnedBy(policy, user),
                            onToggleExpanded = {
                              val opening = expandedUser != user.id
                              expandedUser = if (opening) user.id else null
                              // Tag ownership lives in the policy, which is loaded on demand.
                              if (opening && policy == null) loadPolicy()
                            },
                            onRename = { pendingRenameUser = user },
                            onDelete = { pendingDeleteUser = user })
                      }
                      if (users.isEmpty()) {
                        item("noUsers") { EmptyHint(R.string.admin_no_users) }
                      }
                    }
                2 -> {
                  val visibleKeys = filterPreAuthKeys(keys, keyStatus, keyUserId, Instant.now())
                  Column(Modifier.fillMaxSize()) {
                    KeyFiltersRow(
                        keys = keys,
                        users = users,
                        status = keyStatus,
                        onStatus = { keyStatus = it },
                        userId = keyUserId,
                        onUser = { keyUserId = it })
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)) {
                      items(visibleKeys, key = { it.id }) { key ->
                        PreAuthKeyRow(key, onExpire = { pendingExpireKey = key })
                      }
                      if (visibleKeys.isEmpty()) {
                        item("noKeys") {
                          Text(
                              stringResource(R.string.admin_no_keys),
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.padding(16.dp))
                        }
                      }
                    }
                  }
                }
                3 ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp)) {
                      if (routableNodes.isEmpty()) {
                        item("noRoutes") {
                          Text(
                              stringResource(R.string.admin_no_route_nodes),
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.padding(16.dp))
                        }
                      }
                      items(routableNodes, key = { it.id }) { node ->
                        RouteNodeBlock(
                            node = node,
                            busy = busy,
                            onApply = { routes ->
                              scope.launch {
                                if (runAction { AdminApi.approveRoutes(node.id, routes) }) reload()
                              }
                            })
                      }
                    }
                else -> {
                  LaunchedEffect(Unit) { if (policy == null) loadPolicy() }
                  AdminPolicyTab(
                      policyText = policy?.policy.orEmpty(),
                      updatedAt = policy?.updatedAt,
                      loading = policyLoading,
                      saving = busy,
                      onSave = { savePolicy(it) })
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
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onExpire: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onTags: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  val onlineLabel = stringResource(R.string.admin_device_online)
  val offlineLabel = stringResource(R.string.admin_device_offline)
  ListItem(
      leadingContent = {
        Box(
            Modifier.size(10.dp)
                .background(
                    if (node.online) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape)
                .semantics {
                  contentDescription =
                      if (node.online) onlineLabel else offlineLabel
                })
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
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onToggleExpanded) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.admin_device_details))
          }
          Box {
          IconButton(onClick = { menuOpen = true }, enabled = !busy) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.admin_device_actions))
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            CompactMenuItem(stringResource(R.string.admin_action_rename)) {
              menuOpen = false
              onRename()
            }
            CompactMenuItem(stringResource(R.string.admin_node_tags)) {
              menuOpen = false
              onTags()
            }
            CompactMenuItem(stringResource(R.string.admin_action_expire)) {
              menuOpen = false
              onExpire()
            }
            CompactMenuItem(stringResource(R.string.admin_action_delete), destructive = true) {
              menuOpen = false
              onDelete()
            }
          }
          }
        }
      })

  if (!expanded) return

  Lists.MutedHeader(text = stringResource(R.string.admin_ip_addresses))
  node.ipAddresses.forEach { DetailLine(text = it, trailing = null) }
  if (node.tags.isNotEmpty()) {
    Lists.MutedHeader(text = stringResource(R.string.admin_node_tags))
    node.tags.forEach { DetailLine(text = it, trailing = null) }
  }
  node.lastSeen?.takeIf { !isZeroTime(it) }?.let {
    DetailLine(
        text = stringResource(R.string.admin_last_seen),
        trailing = it.replace("T", " ").take(16))
  }
  node.expiry?.take(10)?.takeIf { !isZeroTime(it) }?.let {
    DetailLine(text = stringResource(R.string.admin_expires_at), trailing = it)
  }
  Spacer(Modifier.size(6.dp))
}

/** Status and owner filters for the key list, with counts so the numbers add up at a glance. */
@Composable
private fun KeyFiltersRow(
    keys: List<AdminApi.HsPreAuthKey>,
    users: List<AdminApi.HsUser>,
    status: KeyStatus,
    onStatus: (KeyStatus) -> Unit,
    userId: String?,
    onUser: (String?) -> Unit,
) {
  val now = Instant.now()
  Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)) {
          KeyStatus.entries.forEach { candidate ->
            FilterChip(
                selected = status == candidate,
                onClick = { onStatus(candidate) },
                label = {
                  Text(
                      "${stringResource(keyStatusLabel(candidate))} (${filterPreAuthKeys(keys, candidate, userId, now).size})")
                },
                modifier = Modifier.padding(end = 6.dp))
          }
        }
    if (users.size > 1) {
      Row(
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 2.dp)) {
            FilterChip(
                selected = userId == null,
                onClick = { onUser(null) },
                label = {
                  Text(
                      "${stringResource(R.string.admin_all_users)} (${filterPreAuthKeys(keys, status, null, now).size})")
                },
                modifier = Modifier.padding(end = 6.dp))
            users.forEach { user ->
              FilterChip(
                  selected = userId == user.id,
                  onClick = { onUser(user.id) },
                  label = {
                    Text("${user.name} (${filterPreAuthKeys(keys, status, user.id, now).size})")
                  },
                  modifier = Modifier.padding(end = 6.dp))
            }
          }
    }
    Spacer(Modifier.size(4.dp))
  }
}

private fun keyStatusLabel(status: KeyStatus): Int =
    when (status) {
      KeyStatus.ALL -> R.string.admin_key_status_all
      KeyStatus.USABLE -> R.string.admin_key_status_usable
      KeyStatus.USED -> R.string.admin_key_status_used
      KeyStatus.EXPIRED -> R.string.admin_key_status_expired
    }

/** Shown when a tab has nothing to list, or nothing matches the filter. */
@Composable
private fun EmptyHint(@StringRes textRes: Int) {
  Text(
      stringResource(textRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(16.dp))
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
        Column {
          val parts = mutableListOf<String>()
          key.user?.name?.let { parts += it }
          if (key.reusable) parts += stringResource(R.string.admin_reusable)
          if (key.ephemeral) parts += stringResource(R.string.admin_ephemeral)
          if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
          }
          val created = key.createdAt?.take(10)?.takeIf { !isZeroTime(it) }
          val expires = key.expiration?.take(10)?.takeIf { !isZeroTime(it) }
          val times =
              buildList {
                created?.let { add("${stringResource(R.string.admin_created_at)} $it") }
                expires?.let { add("${stringResource(R.string.admin_expires_at)} $it") }
              }
          if (times.isNotEmpty()) {
            Text(
                times.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (key.isExpired(Instant.now())) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
          }
        }
      },
      trailingContent = {
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.admin_key_actions))
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            CompactMenuItem(stringResource(R.string.admin_copy)) {
              menuOpen = false
              clipboard.setText(AnnotatedString(key.key))
            }
            CompactMenuItem(stringResource(R.string.admin_action_expire), destructive = true) {
              menuOpen = false
              onExpire()
            }
          }
        }
      })
}

@Composable
private fun UserRow(
    user: AdminApi.HsUser,
    expanded: Boolean,
    devices: List<AdminApi.HsNode>,
    keys: List<AdminApi.HsPreAuthKey>,
    tags: List<String>,
    onToggleExpanded: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  ListItem(
      headlineContent = { Text(user.name, style = MaterialTheme.typography.bodyMedium) },
      supportingContent = {
        val parts =
            mutableListOf(
                "${stringResource(R.string.admin_nodes)} ${devices.size}",
                "${stringResource(R.string.admin_keys_short)} ${keys.size}")
        if (tags.isNotEmpty()) {
          parts += "${stringResource(R.string.admin_node_tags)} ${tags.size}"
        }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
      },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onToggleExpanded) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null)
          }
          Box {
            IconButton(onClick = { menuOpen = true }) {
              Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.admin_user_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              CompactMenuItem(stringResource(R.string.admin_action_rename)) {
                menuOpen = false
                onRename()
              }
              CompactMenuItem(stringResource(R.string.admin_action_delete), destructive = true) {
                menuOpen = false
                onDelete()
              }
            }
          }
        }
      })

  if (!expanded) return

  if (devices.isNotEmpty()) {
    Lists.MutedHeader(text = stringResource(R.string.admin_nodes))
    devices.forEach { node ->
      DetailLine(text = node.displayName, trailing = node.ipAddresses.firstOrNull())
    }
  }
  if (keys.isNotEmpty()) {
    Lists.MutedHeader(text = stringResource(R.string.admin_preauth_keys))
    keys.forEach { key ->
      DetailLine(
          text = key.key,
          trailing = key.expiration?.take(10)?.takeIf { !isZeroTime(it) })
    }
  }
  Lists.MutedHeader(text = stringResource(R.string.admin_node_tags))
  if (tags.isEmpty()) {
    Text(
        stringResource(R.string.admin_tags_none),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, bottom = 6.dp))
  } else {
    tags.forEach { tag -> DetailLine(text = tag, trailing = null) }
  }
}

@Composable
private fun DetailLine(text: String, trailing: String?) {
  Row(
      Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        trailing?.let {
          Text(
              it,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
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
                CompactMenuItem(candidate.name) {
                  user = candidate
                  pickerOpen = false
                }
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

/** One route in the grid: checkbox, the route itself, and whether it is a subnet or exit route. */
@Composable
private fun RouteCell(
    route: String,
    approved: Boolean,
    busy: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
  Row(
      modifier.clickable(enabled = !busy, onClick = onToggle).padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = approved, onCheckedChange = null)
        Text(
            route,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color =
                if (approved) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.weight(1f))
        Text(
            stringResource(routeTypeLabel(route)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, end = 4.dp))
      }
}

/** 0.0.0.0/0 and ::/0 are exit-node routes; everything else is a subnet route. */
private fun routeTypeLabel(route: String): Int =
    if (route == "0.0.0.0/0" || route == "::/0") R.string.admin_route_exit
    else R.string.admin_route_subnet

/** One device's advertised routes, toggled in place; the API takes the whole approved set. */
@Composable
private fun RouteNodeBlock(
    node: AdminApi.HsNode,
    busy: Boolean,
    onApply: (List<String>) -> Unit,
) {
  val routes = remember(node) { (node.availableRoutes + node.approvedRoutes).distinct().sorted() }
  val isExitNode =
      remember(node) { node.approvedRoutes.any { it == "0.0.0.0/0" || it == "::/0" } }

  Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Box(
              Modifier.size(10.dp)
                  .background(
                      if (node.online) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant,
                      CircleShape))
          Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(node.displayName, style = MaterialTheme.typography.bodyMedium)
            node.user?.name?.takeIf { it.isNotBlank() }?.let {
              Text(
                  it,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Text(
              stringResource(R.string.admin_routes_approved, node.approvedRoutes.size, routes.size),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (isExitNode) {
            Box(
                Modifier.padding(start = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp)) {
                  Text(
                      stringResource(R.string.admin_exit_node),
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary)
                }
          }
        }
    // Two routes per row: they are short, and a full row each left the screen half empty.
    routes.chunked(2).forEach { pair ->
      Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp)) {
        pair.forEach { route ->
          RouteCell(
              route = route,
              approved = node.approvedRoutes.contains(route),
              busy = busy,
              modifier = Modifier.weight(1f).padding(end = 14.dp),
              onToggle = {
                val next =
                    if (node.approvedRoutes.contains(route)) node.approvedRoutes - route
                    else node.approvedRoutes + route
                onApply(routes.filter { it in next })
              })
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }
    Spacer(Modifier.size(6.dp))
    Lists.ItemDivider()
  }
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
          OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
          Text(stringResource(confirmLabelRes))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/**
 * A menu entry that hugs its label. Material's DropdownMenuItem fills the popup's maximum width,
 * which left short labels stranded on the left of a mostly empty menu.
 */
@Composable
private fun CompactMenuItem(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
  Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color =
          if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp))
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

/**
 * Tags whose owner list names this user, directly or through one of the groups they belong to.
 * Returns nothing until the policy has been fetched.
 */
private fun tagsOwnedBy(policy: AdminApi.HsPolicy?, user: AdminApi.HsUser): List<String> {
  val doc = policy?.let { PolicyDoc.parse(it.policy) } ?: return emptyList()
  val owners = doc["tagOwners"] as? JsonObject ?: return emptyList()
  val groups = doc["groups"] as? JsonObject

  fun matches(owner: String): Boolean {
    if (owner == user.name) return true
    if (!owner.startsWith("group:")) return false
    val members = groups?.get(owner) ?: return false
    return PolicyDoc.stringsOf(members).any {
      it == user.name || (user.email.isNotBlank() && it.equals(user.email, ignoreCase = true))
    }
  }

  return owners.entries
      .filter { (_, value) -> PolicyDoc.stringsOf(value).any(::matches) }
      .map { it.key }
      .sorted()
}

/** Headscale reports "no expiry" as the zero timestamp; showing 0001-01-01 helps nobody. */
private fun isZeroTime(isoDate: String) = isoDate.startsWith("0001-01-01")

/** Pre-fill the API base URL from the configured admin console URL, if any. */
private fun defaultBaseUrl(): String {
  val admin = ServerConfig.getAdminUrl().trimEnd('/')
  return if (admin.endsWith("/admin")) admin.removeSuffix("/admin") else admin
}
