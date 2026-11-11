// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.App
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.AdminActionGate
import com.tailscale.ipn.ui.admin.AdminApi
import com.tailscale.ipn.ui.admin.BulkNodeFailure
import com.tailscale.ipn.ui.admin.ConsoleLoadState
import com.tailscale.ipn.ui.admin.DevicePresence
import com.tailscale.ipn.ui.admin.DeviceSort
import com.tailscale.ipn.ui.admin.KeyStatus
import com.tailscale.ipn.ui.admin.NodeList
import com.tailscale.ipn.ui.admin.PolicyBackup
import com.tailscale.ipn.ui.admin.PolicyDoc
import com.tailscale.ipn.ui.admin.PolicyEditorState
import com.tailscale.ipn.ui.admin.PolicySaveGuard
import com.tailscale.ipn.ui.admin.allRoutes
import com.tailscale.ipn.ui.admin.exitRoutesApproved
import com.tailscale.ipn.ui.admin.filterDevices
import com.tailscale.ipn.ui.admin.filterPreAuthKeys
import com.tailscale.ipn.ui.admin.isExitRoute
import com.tailscale.ipn.ui.admin.isExpired
import com.tailscale.ipn.ui.admin.pendingRoutes
import com.tailscale.ipn.ui.admin.status
import com.tailscale.ipn.ui.admin.withRouteToggled
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.ServerConfig
import com.tailscale.ipn.util.TSLog
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * In-app tailnet administration (read + the common write actions) backed by the Headscale HTTP API.
 * Deliberately a small subset of the web console: device actions, pre-auth keys and tailnet state
 * at a glance.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AdminConsoleView(backToSettings: BackNavigation) {
  var connectionVersion by rememberSaveable { mutableIntStateOf(0) }
  key(connectionVersion) {
    AdminConsoleContent(backToSettings, onConnectionChanged = { connectionVersion++ })
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AdminConsoleContent(backToSettings: BackNavigation, onConnectionChanged: () -> Unit) {
  val TAG = "AdminConsoleView"
  val scope = rememberCoroutineScope()
  val actionGate = remember { AdminActionGate() }
  val api = remember { AdminApi.Session(AdminApi.baseUrl().orEmpty(), AdminApi.apiKey().orEmpty()) }
  val editorState = rememberSaveable(saver = PolicyEditorState.Saver) { PolicyEditorState() }
  var confirmLeave by remember { mutableStateOf(false) }
  var confirmConnectionChange by remember { mutableStateOf(false) }
  fun leave() {
    if (editorState.dirty) confirmLeave = true else backToSettings()
  }

  // What the user chose (tab, filters, what is expanded) is saveable: the activity is recreated on
  // a dark-mode or language switch, and none of that should send them back to the first tab. The
  // API key is deliberately not: saved state is not where a secret belongs.
  var baseUrl by rememberSaveable { mutableStateOf(AdminApi.baseUrl() ?: defaultBaseUrl()) }
  var apiKey by remember { mutableStateOf(AdminApi.apiKey().orEmpty()) }
  var status by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var configured by remember { mutableStateOf(AdminApi.isConfigured()) }

  val nodes = remember { mutableStateListOf<AdminApi.HsNode>() }
  val keys = remember { mutableStateListOf<AdminApi.HsPreAuthKey>() }
  val users = remember { mutableStateListOf<AdminApi.HsUser>() }

  // The pager owns the selected tab (and saves it), so swiping and tapping stay in step.
  val pagerState = rememberPagerState { TAB_COUNT }
  val selectedTab = pagerState.currentPage
  var expandedUser by rememberSaveable { mutableStateOf<String?>(null) }
  var keyStatus by rememberSaveable { mutableStateOf(KeyStatus.ALL) }
  var keyUserId by rememberSaveable { mutableStateOf<String?>(null) }
  var nodeQuery by rememberSaveable { mutableStateOf("") }
  var nodePresence by rememberSaveable { mutableStateOf(DevicePresence.ALL) }
  var nodeUserId by rememberSaveable { mutableStateOf<String?>(null) }
  var nodeTag by rememberSaveable { mutableStateOf<String?>(null) }
  var nodeSort by rememberSaveable { mutableStateOf(DeviceSort.ONLINE_FIRST) }
  var pendingRoutesOnly by rememberSaveable { mutableStateOf(false) }
  var pendingRouteId by remember { mutableStateOf<String?>(null) }
  val loadStates = remember { mutableStateMapOf<Int, ConsoleLoadState>() }
  val queuedReloads = remember { mutableSetOf<Int>() }
  val nodeListState = rememberLazyListState()
  var bulkFailures by remember { mutableStateOf(emptyList<BulkNodeFailure>()) }
  var failedBulkAction by remember { mutableStateOf<BulkAction?>(null) }
  var expandedNode by rememberSaveable { mutableStateOf<String?>(null) }
  var showConnection by rememberSaveable { mutableStateOf(!AdminApi.isConfigured()) }
  var createdKey by remember { mutableStateOf<AdminApi.HsPreAuthKey?>(null) }
  var topMenuOpen by remember { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current
  var policy by remember { mutableStateOf<AdminApi.HsPolicy?>(null) }
  var policyLoading by remember { mutableStateOf(false) }

  var selecting by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf(emptySet<String>()) }
  var pendingBulk by remember { mutableStateOf<BulkAction?>(null) }
  var pendingExpire by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingDelete by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingRename by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingTags by remember { mutableStateOf<AdminApi.HsNode?>(null) }
  var pendingExpireKey by remember { mutableStateOf<AdminApi.HsPreAuthKey?>(null) }
  var pendingRenameUser by remember { mutableStateOf<AdminApi.HsUser?>(null) }
  var pendingDeleteUser by remember { mutableStateOf<AdminApi.HsUser?>(null) }
  var editPolicyRequest by remember { mutableStateOf(false) }
  var showCreateKey by remember { mutableStateOf(false) }
  var showCreateUser by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }

  // Back undoes the innermost thing first. The policy tab registers its own handler (search, raw
  // editing) below this one, so it wins while that tab is showing.
  BackHandler {
    if (selecting && pagerState.currentPage == 0) {
      selecting = false
      selected = emptySet()
    } else {
      leave()
    }
  }

  fun selectTab(index: Int) {
    scope.launch { pagerState.animateScrollToPage(index) }
  }

  /** Android 13+ confirms a copy by itself; older versions say nothing, so we do. */
  fun copy(text: String) {
    clipboard.setText(AnnotatedString(text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      scope.launch { snackbarHostState.showSnackbar(App.get().getString(R.string.admin_copied)) }
    }
  }

  fun reload(vararg datasets: Int) {
    datasets.distinct().forEach { dataset ->
      val previous = loadStates[dataset] ?: ConsoleLoadState()
      if (previous.loading) {
        queuedReloads += dataset
        return@forEach
      }
      loadStates[dataset] = previous.copy(loading = true, error = null)
      scope.launch {
        try {
          when (dataset) {
            0 -> {
              val result = withContext(Dispatchers.IO) { api.nodes() }
              nodes.clear()
              nodes.addAll(NodeList.sort(result))
            }
            1 -> {
              val result = withContext(Dispatchers.IO) { api.users() }
              users.clear()
              users.addAll(result)
            }
            2 -> {
              val result = withContext(Dispatchers.IO) { api.preAuthKeys() }
              keys.clear()
              keys.addAll(result)
            }
          }
          loadStates[dataset] = ConsoleLoadState(refreshedAt = Instant.now())
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          loadStates[dataset] = previous.copy(error = e.message ?: e.javaClass.simpleName)
        } finally {
          loadStates[dataset] = loadStates.getValue(dataset).copy(loading = false)
          if (queuedReloads.remove(dataset)) reload(dataset)
        }
      }
    }
  }

  /** Policies are large and only shown on demand: load them when that tab is opened. */
  fun loadPolicy() {
    if (policyLoading) return
    policyLoading = true
    val previous = loadStates[POLICY_TAB] ?: ConsoleLoadState()
    loadStates[POLICY_TAB] = previous.copy(loading = true, error = null)
    scope.launch {
      runCatching { withContext(Dispatchers.IO) { api.policy() } }
          .onSuccess {
            policy = it
            // The last policy known to be on the server is the way back from a bad edit.
            editorState.receive(it.policy)
            PolicyBackup.remember(api.serverId, it.policy)
            loadStates[POLICY_TAB] = ConsoleLoadState(refreshedAt = Instant.now())
          }
          .onFailure {
            if (it is CancellationException) throw it
            loadStates[POLICY_TAB] = previous.copy(error = it.message ?: it.javaClass.simpleName)
            TSLog.w(TAG, "policy load failed: ${it.message}")
          }
      policyLoading = false
      loadStates[POLICY_TAB] = loadStates.getValue(POLICY_TAB).copy(loading = false)
    }
  }

  /**
   * Writes the policy back. Headscale validates it server-side; on success the response is the new
   * policy, which is what the editor waits for before leaving edit mode.
   */
  fun savePolicy(text: String) {
    if (busy || policyLoading || !configured || PolicyDoc.parse(text) == null) return
    val expected = editorState.baseline
    busy = true
    scope.launch {
      status = null
      try {
        val applied =
            withContext(Dispatchers.IO) {
              PolicySaveGuard.save(expected, text, api::policy, api::updatePolicy)
            }
        policy = applied
        // The applied policy is the new way back.
        editorState.saved(applied.policy)
        PolicyBackup.remember(api.serverId, applied.policy)
        loadStates[POLICY_TAB] = ConsoleLoadState(refreshedAt = Instant.now())
        snackbarHostState.showSnackbar(App.get().getString(R.string.policy_saved))
      } catch (e: CancellationException) {
        throw e
      } catch (e: PolicySaveGuard.Conflict) {
        policy = e.latest
        editorState.receive(e.latest.policy)
        PolicyBackup.remember(api.serverId, e.latest.policy)
        // Failures get the banner (expandable, copyable, dismissable), not a snackbar on top of it.
        val message = App.get().getString(R.string.policy_save_conflict)
        loadStates[POLICY_TAB] = ConsoleLoadState(refreshedAt = Instant.now(), error = message)
      } catch (e: Exception) {
        val message = e.message ?: e.javaClass.simpleName
        TSLog.e(TAG, "policy save failed", e)
        status = message
      } finally {
        busy = false
      }
    }
  }

  fun refresh() {
    if (busy) return
    when (pagerState.currentPage) {
      0,
      3 -> reload(0)
      1 -> reload(1, 0, 2)
      2 -> reload(2, 1)
      else -> loadPolicy()
    }
  }

  /**
   * Runs a mutating call and surfaces failures. Returns false when it failed so the caller skips
   * the follow-up reload, which would otherwise wipe the error from the screen.
   */
  suspend fun runAction(block: suspend () -> Unit): Boolean {
    if (busy) return false
    busy = true
    status = null
    return try {
      actionGate.run { withContext(Dispatchers.IO) { block() } }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val message = e.message ?: e.javaClass.simpleName
      status = message
      TSLog.e(TAG, "admin action failed", e)
      false
    } finally {
      busy = false
    }
  }

  /**
   * Resolve selection from the live list, preserving failed IDs even if a refresh no longer lists
   * them.
   */
  val selectedNodes = buildList {
    addAll(nodes.filter { it.id in selected })
    bulkFailures
        .filter { failure -> failure.nodeId in selected && nodes.none { it.id == failure.nodeId } }
        .forEach { failure -> add(AdminApi.HsNode(id = failure.nodeId, givenName = failure.name)) }
  }

  /**
   * Expires or deletes every selected device, best effort: one failure does not stop the rest, and
   * the snackbar reports both numbers. Expiring is reversible (the device signs in again); deleting
   * is not.
   */
  fun runBulk(action: BulkAction) {
    if (busy) return
    val targets = selectedNodes
    if (targets.isEmpty()) return
    busy = true
    scope.launch {
      val failures = mutableListOf<BulkNodeFailure>()
      for (node in targets) {
        try {
          withContext(Dispatchers.IO) {
            when (action) {
              BulkAction.EXPIRE -> api.expireNode(node.id)
              BulkAction.DELETE -> api.deleteNode(node.id)
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          failures +=
              BulkNodeFailure(node.id, node.displayName, e.message ?: e.javaClass.simpleName)
          TSLog.w(
              TAG,
              "bulk ${action.name.lowercase()} failed for ${node.displayName}: ${e.message}",
          )
        }
      }
      busy = false
      bulkFailures = failures
      failedBulkAction = action.takeIf { failures.isNotEmpty() }
      selecting = failures.isNotEmpty()
      selected = failures.map { it.nodeId }.toSet()
      reload(0)
      val failed = failures.size
      val done = targets.size - failed
      val app = App.get()
      snackbarHostState.showSnackbar(
          when {
            failed > 0 -> app.getString(R.string.admin_bulk_partly_failed, done, failed)
            action == BulkAction.EXPIRE -> app.getString(R.string.admin_bulk_expired, done)
            else -> app.getString(R.string.admin_bulk_deleted, done)
          },
          withDismissAction = true,
      )
    }
  }

  /** Devices that advertise or already have routes: the route page is about these. */
  val routableNodes = nodes.filter {
    it.availableRoutes.isNotEmpty() || it.approvedRoutes.isNotEmpty()
  }

  LaunchedEffect(configured) { if (configured) reload(0, 1, 2) }
  LaunchedEffect(selectedTab, expandedNode) {
    if (selectedTab == 0 && expandedNode != null) {
      val index =
          filterDevices(nodes, nodeQuery, nodePresence, nodeUserId, nodeTag, nodeSort)
              .indexOfFirst { it.id == expandedNode }
      if (index >= 0) nodeListState.scrollToItem(index)
    }
  }

  Scaffold(
      topBar = {
        Header(
            R.string.in_app_admin,
            onBack = { leave() },
            actions = {
              IconButton(onClick = { refresh() }, enabled = configured && !busy && !policyLoading) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.admin_refresh),
                )
              }
              // The connection is set up once; it does not need a permanent spot in the bar.
              Box {
                IconButton(onClick = { topMenuOpen = true }) {
                  Icon(
                      Icons.Default.MoreVert,
                      contentDescription = stringResource(R.string.more_actions),
                  )
                }
                DropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.admin_connection)) },
                      enabled = !busy && !policyLoading,
                      onClick = {
                        topMenuOpen = false
                        showConnection = true
                      },
                  )
                  if (configured) {
                    // Informational: when the list on screen was last loaded.
                    DropdownMenuItem(
                        text = {
                          Text(
                              consoleLoadSummary(
                                  loadStates[primaryDataset(selectedTab)] ?: ConsoleLoadState()
                              ),
                              style = MaterialTheme.typography.labelMedium,
                          )
                        },
                        enabled = false,
                        onClick = {},
                    )
                  }
                }
              }
            },
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      floatingActionButton = {
        when {
          !configured -> Unit
          // Extended, so the primary action says what it does instead of hiding behind an icon.
          selectedTab == 1 ->
              ExtendedFloatingActionButton(
                  onClick = { showCreateUser = true },
                  icon = { Icon(Icons.Default.Add, contentDescription = null) },
                  text = { Text(stringResource(R.string.admin_new_user)) },
              )
          selectedTab == 2 ->
              ExtendedFloatingActionButton(
                  onClick = { showCreateKey = true },
                  icon = { Icon(Icons.Default.Add, contentDescription = null) },
                  text = { Text(stringResource(R.string.admin_new_preauth_key)) },
              )
          // Editing the policy is this tab's primary action; it is hidden once editing starts,
          // because the save/discard bar above already owns that state.
          selectedTab == POLICY_TAB && !editorState.editing && !editorState.dirty ->
              ExtendedFloatingActionButton(
                  onClick = { editPolicyRequest = true },
                  icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                  text = { Text(stringResource(R.string.policy_edit_policy)) },
              )
          else -> Unit
        }
      },
  ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding)) {
      status?.let { ConsoleError(it, onDismiss = { status = null }) }

      if (!configured) {
        Text(
            stringResource(R.string.admin_needs_config),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        TextButton(
            onClick = { showConnection = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
          Text(
              stringResource(R.string.admin_connection),
              color = MaterialTheme.colorScheme.link,
          )
        }
      } else {
        // One section at a time: a 26-device tailnet plus users, keys and policy in a single
        // scroll made everything hard to find.
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
          Tab(
              selected = selectedTab == 0,
              onClick = { selectTab(0) },
              text = { Text("${stringResource(R.string.admin_nodes)} (${nodes.size})") },
          )
          Tab(
              selected = selectedTab == 1,
              onClick = { selectTab(1) },
              text = { Text("${stringResource(R.string.admin_users)} (${users.size})") },
          )
          Tab(
              selected = selectedTab == 2,
              onClick = { selectTab(2) },
              text = { Text("${stringResource(R.string.admin_keys_short)} (${keys.size})") },
          )
          Tab(
              selected = selectedTab == 3,
              onClick = { selectTab(3) },
              text = {
                Text(stringResource(R.string.admin_route_device_count, routableNodes.size))
              },
          )
          Tab(
              selected = selectedTab == POLICY_TAB,
              onClick = { selectTab(POLICY_TAB) },
              text = { Text(stringResource(R.string.admin_policy_tab)) },
          )
        }

        val datasets =
            when (selectedTab) {
              1 -> listOf(1, 0, 2)
              2 -> listOf(2, 1)
              POLICY_TAB -> listOf(POLICY_TAB)
              else -> listOf(0)
            }
        val knownTags = nodes.flatMap { it.tags + it.validTags }.distinct().sorted()
        datasets.forEach { dataset ->
          ConsoleLoadInfo(
              label =
                  stringResource(
                      when (dataset) {
                        0 -> R.string.admin_nodes
                        1 -> R.string.admin_users
                        2 -> R.string.admin_keys_short
                        else -> R.string.admin_policy_tab
                      }
                  ),
              state = loadStates[dataset] ?: ConsoleLoadState(),
              onRetry = { if (dataset == POLICY_TAB) loadPolicy() else reload(dataset) },
          )
        }
        val refreshing = datasets.any { loadStates[it]?.loading == true }
        // Always the same height, so starting or finishing an action never nudges the list.
        // Refreshing is the pull indicator's job; this bar is for changes being sent.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
          if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refresh() }) {
          HorizontalPager(
              state = pagerState,
              modifier = Modifier.fillMaxSize(),
              verticalAlignment = Alignment.Top,
              // A sideways drag while selecting devices or editing text should not change tabs.
              userScrollEnabled = !selecting && !editorState.editing,
          ) { page ->
            when (page) {
              0 -> {
                val visibleNodes =
                    filterDevices(
                        nodes,
                        nodeQuery,
                        nodePresence,
                        nodeUserId,
                        nodeTag,
                        nodeSort,
                    )
                Column(Modifier.fillMaxSize()) {
                  if (bulkFailures.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                      Text(
                          stringResource(R.string.admin_bulk_failure_details, bulkFailures.size),
                          style = MaterialTheme.typography.labelMedium,
                      )
                      ConsoleError(
                          bulkFailures.joinToString("\n") {
                            "${it.name} (${it.nodeId}): ${it.reason}"
                          }
                      )
                      Row {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                              selected = bulkFailures.map { it.nodeId }.toSet()
                              selecting = true
                              pendingBulk = failedBulkAction
                            },
                        ) {
                          Text(stringResource(R.string.admin_retry_failed))
                        }
                        TextButton(enabled = !busy, onClick = { bulkFailures = emptyList() }) {
                          Text(stringResource(R.string.admin_dismiss_results))
                        }
                      }
                    }
                  }
                  if (selecting) {
                    SelectionBar(
                        count = selected.size,
                        allSelected =
                            visibleNodes.isNotEmpty() && visibleNodes.all { it.id in selected },
                        enabled = !busy && visibleNodes.isNotEmpty(),
                        onToggleAll = {
                          val ids = visibleNodes.map { it.id }.toSet()
                          selected =
                              if (ids.all { it in selected }) selected - ids else selected + ids
                        },
                        onCancel = {
                          selecting = false
                          selected = emptySet()
                        },
                        onExpire = { pendingBulk = BulkAction.EXPIRE },
                        onDelete = { pendingBulk = BulkAction.DELETE },
                    )
                  } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                                    contentDescription = stringResource(R.string.admin_clear),
                                )
                              }
                            }
                          },
                          placeholder = { Text(stringResource(R.string.admin_search_devices)) },
                          modifier =
                              Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                      )
                      TextButton(
                          enabled = nodes.isNotEmpty() && !busy,
                          onClick = {
                            selecting = true
                            selected = emptySet()
                          },
                      ) {
                        Text(stringResource(R.string.admin_select))
                      }
                    }
                  }
                  ConsoleDeviceFilters(
                      users = users,
                      tags = knownTags,
                      presence = nodePresence,
                      onPresence = { nodePresence = it },
                      userId = nodeUserId,
                      onUser = { nodeUserId = it },
                      tag = nodeTag,
                      onTag = { nodeTag = it },
                      sort = nodeSort,
                      onSort = { nodeSort = it },
                  )
                  LazyColumn(
                      Modifier.fillMaxSize(),
                      state = nodeListState,
                      contentPadding = PaddingValues(bottom = 96.dp),
                  ) {
                    if (visibleNodes.isEmpty()) {
                      item("noNodes") {
                        EmptyHint(
                            if (loadStates[0]?.refreshedAt == null) R.string.admin_not_loaded
                            else if (nodes.isEmpty()) R.string.admin_no_nodes
                            else R.string.admin_no_matches
                        )
                      }
                    }
                    items(visibleNodes, key = { it.id }) { node ->
                      NodeRow(
                          node = node,
                          // Only a change in flight locks the menus; a refresh does not.
                          busy = busy,
                          onCopy = { copy(it) },
                          selecting = selecting,
                          selected = node.id in selected,
                          onToggleSelected = {
                            selected =
                                if (node.id in selected) selected - node.id else selected + node.id
                          },
                          onLongPress = {
                            selecting = true
                            selected = setOf(node.id)
                          },
                          expanded = expandedNode == node.id,
                          onToggleExpanded = {
                            expandedNode = if (expandedNode == node.id) null else node.id
                          },
                          onRename = { pendingRename = node },
                          onTags = { pendingTags = node },
                          onExpire = { pendingExpire = node },
                          onDelete = { pendingDelete = node },
                      )
                    }
                  }
                }
              }
              1 ->
                  LazyColumn(
                      Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(bottom = 96.dp),
                  ) {
                    items(users, key = { it.id }) { user ->
                      UserRow(
                          user = user,
                          expanded = expandedUser == user.id,
                          devices = nodes.filter { it.user?.id == user.id },
                          keys = keys.filter { it.user?.id == user.id },
                          tags = tagsOwnedBy(policy, user),
                          onToggleExpanded = {
                            val opening = expandedUser != user.id
                            expandedUser = if (opening) user.id else null
                            // Tag ownership lives in the policy, which is loaded on demand.
                            if (opening && policy == null) loadPolicy()
                          },
                          onRename = { pendingRenameUser = user },
                          onDelete = { pendingDeleteUser = user },
                          busy = busy,
                          onCopy = { copy(it) },
                          onExpireKey = { pendingExpireKey = it },
                          onDevice = { node ->
                            nodeQuery = ""
                            nodePresence = DevicePresence.ALL
                            nodeUserId = null
                            nodeTag = null
                            expandedNode = node.id
                            selectTab(0)
                          },
                      )
                    }
                    if (users.isEmpty()) {
                      item("noUsers") {
                        EmptyHint(
                            if (loadStates[1]?.refreshedAt == null) R.string.admin_not_loaded
                            else R.string.admin_no_users
                        )
                      }
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
                      onUser = { keyUserId = it },
                  )
                  LazyColumn(
                      Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(bottom = 96.dp),
                  ) {
                    items(visibleKeys, key = { it.id }) { key ->
                      PreAuthKeyRow(
                          key,
                          busy = busy,
                          onCopy = { copy(it) },
                          onExpire = { pendingExpireKey = key },
                      )
                    }
                    if (visibleKeys.isEmpty()) {
                      item("noKeys") {
                        Text(
                            stringResource(
                                if (loadStates[2]?.refreshedAt == null) R.string.admin_not_loaded
                                else R.string.admin_no_keys
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                      }
                    }
                  }
                }
              }
              3 ->
                  Column(Modifier.fillMaxSize()) {
                    FilterChip(
                        selected = pendingRoutesOnly,
                        onClick = { pendingRoutesOnly = !pendingRoutesOnly },
                        label = { Text(stringResource(R.string.admin_routes_pending_only)) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    val visibleRoutes = routableNodes.filter {
                      !pendingRoutesOnly || it.pendingRoutes().isNotEmpty()
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                      item("routeCounts") {
                        Text(
                            stringResource(R.string.admin_route_count_semantics),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                      }
                      if (visibleRoutes.isEmpty()) {
                        item("noRoutes") {
                          Text(
                              stringResource(
                                  when {
                                    loadStates[0]?.refreshedAt == null -> R.string.admin_not_loaded
                                    routableNodes.isEmpty() -> R.string.admin_no_route_nodes
                                    else -> R.string.admin_no_matches
                                  }
                              ),
                              style = MaterialTheme.typography.bodyMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.padding(16.dp),
                          )
                        }
                      }
                      items(visibleRoutes, key = { it.id }) { node ->
                        RouteNodeBlock(
                            node = node,
                            busy = busy,
                            pending = pendingRouteId == node.id,
                            onApply = { routes ->
                              if (!busy)
                                  scope.launch {
                                    pendingRouteId = node.id
                                    // The checkbox flips at once; a rejected change flips it back.
                                    val before = node.approvedRoutes
                                    fun show(approved: List<String>) {
                                      val at = nodes.indexOfFirst { it.id == node.id }
                                      if (at >= 0) {
                                        nodes[at] = nodes[at].copy(approvedRoutes = approved)
                                      }
                                    }
                                    show(routes)
                                    try {
                                      if (runAction { api.approveRoutes(node.id, routes) }) {
                                        reload(0)
                                      } else {
                                        show(before)
                                      }
                                    } finally {
                                      pendingRouteId = null
                                    }
                                  }
                            },
                        )
                      }
                    }
                  }
              else -> {
                LaunchedEffect(Unit) { if (policy == null) loadPolicy() }
                AdminPolicyTab(
                    active = selectedTab == POLICY_TAB,
                    policyText = policy?.policy.orEmpty(),
                    editorState = editorState,
                    serverId = api.serverId,
                    updatedAt = policy?.updatedAt,
                    loading = policyLoading,
                    saving = busy || policyLoading,
                    // What a rule can legitimately point at, from the tailnet's own data.
                    users = users.map { it.name }.filter { it.isNotBlank() },
                    tags = knownTags,
                    editPolicyRequest = editPolicyRequest,
                    onEditPolicyHandled = { editPolicyRequest = false },
                    onSave = { savePolicy(it) },
                )
              }
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
          if (editorState.dirty) {
            confirmConnectionChange = true
          } else {
            AdminApi.saveConnection(baseUrl, apiKey)
            onConnectionChanged()
          }
        },
    )
  }

  if (confirmLeave || confirmConnectionChange) {
    ConfirmDialog(
        title = stringResource(R.string.policy_edit_discard_title),
        message = stringResource(R.string.policy_edit_discard_message),
        confirmLabel = stringResource(R.string.policy_edit_discard),
        onDismiss = {
          confirmLeave = false
          confirmConnectionChange = false
        },
        onConfirm = {
          if (confirmConnectionChange) {
            AdminApi.saveConnection(baseUrl, apiKey)
            onConnectionChanged()
          } else {
            backToSettings()
          }
          confirmLeave = false
          confirmConnectionChange = false
        },
    )
  }

  pendingBulk?.let { action ->
    val targets = selectedNodes
    val deleting = action == BulkAction.DELETE
    ConfirmDialog(
        title =
            stringResource(
                if (deleting) R.string.admin_bulk_delete_title
                else R.string.admin_bulk_expire_title,
                targets.size,
            ),
        // Name what is about to go: a count alone does not show a mis-tap in the selection.
        message =
            stringResource(
                if (deleting) R.string.admin_bulk_delete_message
                else R.string.admin_bulk_expire_message
            ) +
                "\n\n" +
                targets.take(BULK_NAMES_SHOWN).joinToString("\n") { "• ${it.displayName}" } +
                if (targets.size > BULK_NAMES_SHOWN) "\n…" else "",
        confirmLabel =
            stringResource(
                if (deleting) R.string.admin_action_delete else R.string.admin_action_expire
            ),
        onDismiss = { pendingBulk = null },
        onConfirm = {
          pendingBulk = null
          runBulk(action)
        },
    )
  }

  pendingExpire?.let { node ->
    ConfirmDialog(
        title = stringResource(R.string.admin_action_expire),
        message = node.displayName + "\n\n" + stringResource(R.string.admin_bulk_expire_message),
        confirmLabel = stringResource(R.string.admin_action_expire),
        onDismiss = { pendingExpire = null },
        onConfirm = {
          pendingExpire = null
          scope.launch { if (runAction { api.expireNode(node.id) }) reload(0) }
        },
    )
  }

  pendingDelete?.let { node ->
    ConfirmDialog(
        title = stringResource(R.string.admin_confirm_delete),
        message = node.displayName + "\n\n" + stringResource(R.string.admin_bulk_delete_message),
        confirmLabel = stringResource(R.string.admin_action_delete),
        onDismiss = { pendingDelete = null },
        onConfirm = {
          pendingDelete = null
          scope.launch { if (runAction { api.deleteNode(node.id) }) reload(0) }
        },
    )
  }

  pendingRename?.let { node ->
    TextInputDialog(
        titleRes = R.string.admin_action_rename,
        initial = node.displayName,
        confirmLabelRes = R.string.admin_action_rename,
        requireChange = true,
        onDismiss = { pendingRename = null },
        onConfirm = { newName ->
          pendingRename = null
          scope.launch { if (runAction { api.renameNode(node.id, newName) }) reload(0) }
        },
    )
  }

  pendingTags?.let { node ->
    ConsoleTagsDialog(
        initial = node.tags,
        // Tags other devices carry, plus the ones the policy defines owners for (once loaded).
        known =
            remember(policy, nodes.toList()) {
              val owned =
                  policy
                      ?.let { PolicyDoc.parse(it.policy) }
                      ?.get("tagOwners")
                      ?.let { it as? JsonObject }
                      ?.keys
                      .orEmpty()
              (nodes.flatMap { it.tags + it.validTags } + owned).distinct().sorted()
            },
        onDismiss = { pendingTags = null },
        onConfirm = { tags ->
          pendingTags = null
          scope.launch { if (runAction { api.setNodeTags(node.id, tags) }) reload(0) }
        },
    )
  }

  if (showCreateUser) {
    TextInputDialog(
        titleRes = R.string.admin_new_user,
        initial = "",
        confirmLabelRes = R.string.admin_create,
        onDismiss = { showCreateUser = false },
        onConfirm = { name ->
          showCreateUser = false
          scope.launch { if (runAction { api.createUser(name) }) reload(1) }
        },
    )
  }

  pendingRenameUser?.let { user ->
    TextInputDialog(
        titleRes = R.string.admin_action_rename,
        initial = user.name,
        confirmLabelRes = R.string.admin_action_rename,
        requireChange = true,
        onDismiss = { pendingRenameUser = null },
        onConfirm = { name ->
          pendingRenameUser = null
          scope.launch { if (runAction { api.renameUser(user.id, name) }) reload(1, 0, 2) }
        },
    )
  }

  pendingDeleteUser?.let { user ->
    ConfirmDialog(
        title = stringResource(R.string.admin_confirm_delete_user),
        message = user.name,
        confirmLabel = stringResource(R.string.admin_action_delete),
        onDismiss = { pendingDeleteUser = null },
        onConfirm = {
          pendingDeleteUser = null
          scope.launch { if (runAction { api.deleteUser(user.id) }) reload(1, 0, 2) }
        },
    )
  }

  pendingExpireKey?.let { key ->
    ConfirmDialog(
        title = stringResource(R.string.admin_action_expire),
        message = stringResource(R.string.admin_key_identifier, key.id),
        confirmLabel = stringResource(R.string.admin_action_expire),
        onDismiss = { pendingExpireKey = null },
        onConfirm = {
          pendingExpireKey = null
          scope.launch { if (runAction { api.expirePreAuthKey(key.id) }) reload(2) }
        },
    )
  }

  if (showCreateKey) {
    CreatePreAuthKeyDialog(
        users = users,
        onDismiss = { showCreateKey = false },
        onCreate = { user, reusable, ephemeral, days ->
          showCreateKey = false
          scope.launch {
            var created: AdminApi.HsPreAuthKey? = null
            if (
                runAction {
                  created =
                      api.createPreAuthKey(
                          userId = user.id,
                          reusable = reusable,
                          ephemeral = ephemeral,
                          expirationRfc3339 =
                              if (days <= 0) null
                              else java.time.Instant.now().plusSeconds(days * 86400L).toString(),
                      )
                }
            ) {
              reload(2)
              // The creation response may be the only time the server hands out the whole key.
              createdKey = created?.takeIf { it.key.isNotBlank() }
            }
          }
        },
    )
  }

  createdKey?.let { key ->
    AlertDialog(
        // Not dismissable by a stray tap outside: closing it can mean losing the key.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.admin_key_created_title)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.admin_key_created_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer {
              Text(
                  key.key,
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = FontFamily.Monospace,
                  modifier =
                      Modifier.fillMaxWidth()
                          .background(
                              MaterialTheme.colorScheme.surfaceVariant,
                              RoundedCornerShape(8.dp),
                          )
                          .padding(12.dp),
              )
            }
          }
        },
        confirmButton = {
          TextButton(
              onClick = {
                copy(key.key)
                createdKey = null
              }
          ) {
            Text(stringResource(R.string.admin_copy))
          }
        },
        dismissButton = {
          TextButton(onClick = { createdKey = null }) {
            Text(stringResource(R.string.admin_dismiss))
          }
        },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(
    node: AdminApi.HsNode,
    busy: Boolean,
    onCopy: (String) -> Unit,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onLongPress: () -> Unit,
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
      modifier =
          if (selecting) {
            Modifier.clickable(onClick = onToggleSelected)
          } else {
            // Tap expands, like a user row; long press starts selecting.
            Modifier.combinedClickable(onClick = onToggleExpanded, onLongClick = onLongPress)
          },
      leadingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
                modifier = Modifier.semantics { contentDescription = node.displayName },
            )
          }
          Box(
              Modifier.size(10.dp)
                  .background(
                      if (node.online) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant,
                      CircleShape,
                  )
                  .semantics { contentDescription = if (node.online) onlineLabel else offlineLabel }
          )
        }
      },
      headlineContent = { Text(node.displayName, style = MaterialTheme.typography.bodyMedium) },
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
            maxLines = 2,
        )
      },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!selecting) {
            // The whole row expands; this only shows which way, so it is not a second button.
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.admin_device_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
              IconButton(onClick = { menuOpen = true }, enabled = !busy) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.admin_device_actions),
                )
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
        }
      },
  )

  if (!expanded) return

  Lists.MutedHeader(text = stringResource(R.string.admin_ip_addresses))
  // Addresses are what people come here to grab: one tap copies.
  node.ipAddresses.forEach { ip ->
    DetailLine(
        text = ip,
        trailing = stringResource(R.string.admin_copy),
        onClick = { onCopy(ip) },
    )
  }
  if (node.tags.isNotEmpty()) {
    Lists.MutedHeader(text = stringResource(R.string.admin_node_tags))
    node.tags.forEach { DetailLine(text = it, trailing = null) }
  }
  node.lastSeen
      ?.takeIf { !isZeroTime(it) }
      ?.let {
        DetailLine(
            text = stringResource(R.string.admin_last_seen),
            trailing = it.replace("T", " ").take(16),
        )
      }
  node.expiry
      ?.take(10)
      ?.takeIf { !isZeroTime(it) }
      ?.let { DetailLine(text = stringResource(R.string.admin_expires_at), trailing = it) }
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
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)
    ) {
      KeyStatus.entries.forEach { candidate ->
        FilterChip(
            selected = status == candidate,
            onClick = { onStatus(candidate) },
            label = {
              Text(
                  "${stringResource(keyStatusLabel(candidate))} (${filterPreAuthKeys(keys, candidate, userId, now).size})"
              )
            },
            modifier = Modifier.padding(end = 6.dp),
        )
      }
    }
    Row(Modifier.padding(horizontal = 12.dp)) { ConsoleUserFilter(users, userId, onUser) }
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
      modifier = Modifier.padding(16.dp),
  )
}

@Composable
private fun PreAuthKeyRow(
    key: AdminApi.HsPreAuthKey,
    busy: Boolean,
    onCopy: (String) -> Unit,
    onExpire: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  var revealed by remember(key.key) { mutableStateOf(false) }
  ListItem(
      headlineContent = {
        Text(
            if (revealed) key.key else stringResource(R.string.admin_key_masked, key.id),
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (revealed) Int.MAX_VALUE else 2,
        )
      },
      supportingContent = {
        Column {
          val parts = mutableListOf<String>()
          parts += stringResource(keyStatusLabel(key.status(Instant.now())))
          key.user?.name?.let { parts += it }
          if (key.reusable) parts += stringResource(R.string.admin_reusable)
          if (key.ephemeral) parts += stringResource(R.string.admin_ephemeral)
          if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
          }
          val created = key.createdAt?.take(10)?.takeIf { !isZeroTime(it) }
          val expires = key.expiration?.take(10)?.takeIf { !isZeroTime(it) }
          val times = buildList {
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
                maxLines = 1,
            )
          }
        }
      },
      trailingContent = {
        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.admin_key_actions),
            )
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            CompactMenuItem(
                stringResource(
                    if (revealed) R.string.admin_hide_secret else R.string.admin_reveal_secret
                )
            ) {
              menuOpen = false
              revealed = !revealed
            }
            CompactMenuItem(stringResource(R.string.admin_copy)) {
              menuOpen = false
              onCopy(key.key)
            }
            if (!busy)
                CompactMenuItem(stringResource(R.string.admin_action_expire), destructive = true) {
                  menuOpen = false
                  onExpire()
                }
          }
        }
      },
  )
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
    busy: Boolean,
    onCopy: (String) -> Unit,
    onDevice: (AdminApi.HsNode) -> Unit,
    onExpireKey: (AdminApi.HsPreAuthKey) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  ListItem(
      // Same gesture as a device row: tap anywhere to expand.
      modifier = Modifier.clickable(onClick = onToggleExpanded),
      headlineContent = { Text(user.name, style = MaterialTheme.typography.bodyMedium) },
      supportingContent = {
        val parts =
            mutableListOf(
                "${stringResource(R.string.admin_nodes)} ${devices.size}",
                "${stringResource(R.string.admin_keys_short)} ${keys.size}",
            )
        if (tags.isNotEmpty()) {
          parts += "${stringResource(R.string.admin_node_tags)} ${tags.size}"
        }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
              if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Box {
            IconButton(onClick = { menuOpen = true }, enabled = !busy) {
              Icon(
                  Icons.Default.MoreVert,
                  contentDescription = stringResource(R.string.admin_user_actions),
              )
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
      },
  )

  if (!expanded) return

  if (devices.isNotEmpty()) {
    Lists.MutedHeader(text = stringResource(R.string.admin_nodes))
    devices.forEach { node ->
      ListItem(
          headlineContent = { Text(node.displayName, style = MaterialTheme.typography.bodySmall) },
          supportingContent = { Text(node.ipAddresses.firstOrNull().orEmpty()) },
          modifier = Modifier.clickable { onDevice(node) },
      )
    }
  }
  if (keys.isNotEmpty()) {
    Lists.MutedHeader(text = stringResource(R.string.admin_preauth_keys))
    keys.forEach { authKey ->
      key(authKey.id) {
        PreAuthKeyRow(
            authKey,
            busy = busy,
            onCopy = onCopy,
            onExpire = { onExpireKey(authKey) },
        )
      }
    }
  }
  Lists.MutedHeader(text = stringResource(R.string.admin_node_tags))
  if (tags.isEmpty()) {
    Text(
        stringResource(R.string.admin_tags_none),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, bottom = 6.dp),
    )
  } else {
    tags.forEach { tag -> DetailLine(text = tag, trailing = null) }
  }
}

@Composable
private fun DetailLine(text: String, trailing: String?, onClick: (() -> Unit)? = null) {
  Row(
      Modifier.fillMaxWidth()
          .then(
              if (onClick != null) {
                // Tappable lines get a real touch target, not a 2dp sliver.
                Modifier.clickable(
                        onClickLabel = stringResource(R.string.admin_tap_to_copy),
                        onClick = onClick,
                    )
                    .padding(vertical = 8.dp)
              } else {
                Modifier
              }
          )
          .padding(start = 28.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    trailing?.let {
      Text(
          it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent overlay: tapping the (read-only) field opens the picker.
            Box(Modifier.matchParentSize().clickable { pickerOpen = true })
            if (pickerOpen)
                ConsoleUserPicker(
                    users = users,
                    selectedId = user?.id,
                    allowAll = false,
                    onDismiss = { pickerOpen = false },
                    onSelect = { id ->
                      user = users.firstOrNull { it.id == id }
                      pickerOpen = false
                    },
                )
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
              supportingText =
                  if (daysValid) null else ({ Text(stringResource(R.string.admin_days_required)) }),
              singleLine = true,
          )
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
            },
        ) {
          Text(stringResource(R.string.admin_create))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

/** One route in the grid: checkbox, the route itself, and whether it is a subnet or exit route. */
@Composable
private fun RouteCell(
    route: String,
    exit: Boolean,
    approved: Boolean,
    busy: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
  Row(
      modifier.clickable(enabled = !busy, onClick = onToggle).padding(vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = approved, onCheckedChange = null)
    Text(
        route,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color =
            if (approved) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        modifier = Modifier.weight(1f),
    )
    Text(
        stringResource(if (exit) R.string.admin_route_exit else R.string.admin_route_subnet),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, end = 4.dp),
    )
  }
}

/** One checkbox in the route grid; [route] is what gets toggled, [label] what is shown. */
private data class RouteChoice(val route: String, val label: String, val approved: Boolean)

/** One device's advertised routes, toggled in place; the API takes the whole approved set. */
@Composable
private fun RouteNodeBlock(
    node: AdminApi.HsNode,
    busy: Boolean,
    pending: Boolean,
    onApply: (List<String>) -> Unit,
) {
  // 0.0.0.0/0 and ::/0 are one decision ("may this be an exit node"), so they share one checkbox.
  val exitRoutes = remember(node) { node.allRoutes().filter(::isExitRoute) }
  val cells =
      remember(node) {
        node.allRoutes().filterNot(::isExitRoute).map {
          RouteChoice(it, it, it in node.approvedRoutes)
        } +
            if (exitRoutes.isEmpty()) emptyList()
            else
                listOf(
                    RouteChoice(
                        exitRoutes.first(),
                        exitRoutes.joinToString(" · "),
                        node.exitRoutesApproved(),
                    )
                )
      }
  val isExitNode = remember(node) { node.approvedRoutes.any(::isExitRoute) }

  Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          Modifier.size(10.dp)
              .background(
                  if (node.online) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
                  CircleShape,
              )
      )
      Column(Modifier.padding(start = 10.dp).weight(1f)) {
        Text(node.displayName, style = MaterialTheme.typography.bodyMedium)
        node.user
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let {
              Text(
                  it,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
      }
      Column(Modifier.weight(1f).padding(start = 8.dp)) {
        Text(
            stringResource(
                R.string.admin_route_row_counts,
                node.availableRoutes.distinct().size,
                node.approvedRoutes.distinct().size,
                node.pendingRoutes().size,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pending) {
          Text(
              stringResource(R.string.admin_route_applying),
              style = MaterialTheme.typography.labelSmall,
          )
          LinearProgressIndicator(Modifier.fillMaxWidth())
        }
      }
      if (isExitNode) {
        Box(
            Modifier.padding(start = 8.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
          Text(
              stringResource(R.string.admin_exit_node),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
    // Two routes per row: they are short, and a full row each left the screen half empty.
    cells.chunked(2).forEach { pair ->
      Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp)) {
        pair.forEach { cell ->
          RouteCell(
              route = cell.label,
              exit = isExitRoute(cell.route),
              approved = cell.approved,
              busy = busy,
              modifier = Modifier.weight(1f).padding(end = 14.dp),
              onToggle = { onApply(node.withRouteToggled(cell.route)) },
          )
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
              modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
              value = apiKey,
              onValueChange = onApiKeyChange,
              label = { Text(stringResource(R.string.admin_api_key)) },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth(),
          )
          status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
            )
          }
        }
      },
      confirmButton = {
        TextButton(
            enabled = !busy && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            onClick = onSave,
        ) {
          Text(stringResource(R.string.admin_save_and_test))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun TextInputDialog(
    @StringRes titleRes: Int,
    initial: String,
    @StringRes confirmLabelRes: Int,
    subtitle: String? = null,
    /** For renames: confirming the unchanged name would be a pointless request. */
    requireChange: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  // Opens ready to type: focused, keyboard up, and the old value selected so typing replaces it.
  var field by remember {
    mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
  }
  val value = field.text
  val valid = value.isNotBlank() && (!requireChange || value.trim() != initial.trim())
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(titleRes)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedTextField(
              value = field,
              onValueChange = { field = it },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(value.trim()) }),
              modifier = Modifier.focusRequester(focusRequester),
          )
        }
      },
      confirmButton = {
        TextButton(enabled = valid, onClick = { onConfirm(value.trim()) }) {
          Text(stringResource(confirmLabelRes))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
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
      modifier =
          Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
  )
}

/** Bulk operations offered while devices are selected. */
private const val POLICY_TAB = 4
private const val TAB_COUNT = 5

/** How many device names a bulk confirmation spells out before trailing off. */
private const val BULK_NAMES_SHOWN = 8

/** The dataset a tab is mainly about; its load time is the one worth showing. */
private fun primaryDataset(tab: Int): Int = if (tab == 3) 0 else tab

private enum class BulkAction {
  EXPIRE,
  DELETE,
}

/** Replaces the search row while devices are selected: count, select-all and the two operations. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
    onExpire: () -> Unit,
    onDelete: () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.secondaryContainer)
          .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onCancel) {
      Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
    }
    Text(
        stringResource(R.string.admin_selected_count, count),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onToggleAll, enabled = enabled) {
      Text(
          stringResource(if (allSelected) R.string.admin_select_none else R.string.admin_select_all)
      )
    }
    TextButton(onClick = onExpire, enabled = enabled && count > 0) {
      Text(stringResource(R.string.admin_action_expire))
    }
    TextButton(onClick = onDelete, enabled = enabled && count > 0) {
      Text(
          stringResource(R.string.admin_action_delete),
          color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

/** Shared by every dialog that asks before doing something irreversible. */
@Composable
internal fun ConfirmDialog(
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
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
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
