// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.AdminApi
import com.tailscale.ipn.ui.admin.ConsoleLoadState
import com.tailscale.ipn.ui.admin.DevicePresence
import com.tailscale.ipn.ui.admin.DeviceSort
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ConsoleError(message: String, onRetry: (() -> Unit)? = null) {
  var expanded by remember(message) { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    SelectionContainer {
      Text(
          message,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          maxLines = if (expanded) Int.MAX_VALUE else 2,
          modifier =
              if (expanded) Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())
              else Modifier,
      )
    }
    Row(Modifier.horizontalScroll(rememberScrollState())) {
      TextButton(onClick = { expanded = !expanded }) {
        Text(
            stringResource(
                if (expanded) R.string.admin_error_collapse else R.string.admin_error_expand
            )
        )
      }
      TextButton(onClick = { clipboard.setText(AnnotatedString(message)) }) {
        Text(stringResource(R.string.admin_copy))
      }
      onRetry?.let { retry ->
        TextButton(onClick = retry) { Text(stringResource(R.string.admin_retry)) }
      }
    }
  }
}

@Composable
internal fun ConsoleLoadInfo(label: String, state: ConsoleLoadState, onRetry: () -> Unit) {
  val timestamp =
      state.refreshedAt?.let {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(it)
      }
  Text(
      "$label · " +
          when {
            state.loading -> stringResource(R.string.admin_loading_data)
            timestamp != null -> stringResource(R.string.admin_refreshed_at, timestamp)
            else -> stringResource(R.string.admin_not_loaded)
          },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
  )
  if (state.loading && timestamp != null) {
    Text(
        stringResource(R.string.admin_refreshed_at, timestamp),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
  }
  state.error?.let { ConsoleError(it, if (state.loading) null else onRetry) }
}

/** A bounded, searchable list, shared by filters and the key creation dialog. */
@Composable
internal fun ConsoleUserPicker(
    users: List<AdminApi.HsUser>,
    selectedId: String?,
    allowAll: Boolean = true,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val matches =
      users
          .filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                it.email.contains(query.trim(), ignoreCase = true)
          }
          .sortedBy { it.name.lowercase() }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.admin_user)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              singleLine = true,
              label = { Text(stringResource(R.string.admin_search_users)) },
          )
          LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (allowAll)
                item {
                  ListItem(
                      headlineContent = { Text(stringResource(R.string.admin_all_users)) },
                      supportingContent =
                          if (selectedId == null)
                              ({ Text(stringResource(R.string.admin_filter_selected)) })
                          else null,
                      modifier = Modifier.clickable { onSelect(null) },
                  )
                }
            items(matches, key = { it.id }) { user ->
              ListItem(
                  headlineContent = { Text(user.name) },
                  supportingContent =
                      if (selectedId == user.id)
                          ({ Text(stringResource(R.string.admin_filter_selected)) })
                      else null,
                  modifier = Modifier.clickable { onSelect(user.id) },
              )
            }
            if (matches.isEmpty()) item { Text(stringResource(R.string.admin_no_matches)) }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
internal fun ConsoleUserFilter(
    users: List<AdminApi.HsUser>,
    userId: String?,
    onUser: (String?) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  FilterChip(
      selected = userId != null,
      onClick = { open = true },
      label = {
        Text(
            users.firstOrNull { it.id == userId }?.name
                ?: userId
                ?: stringResource(R.string.admin_all_users)
        )
      },
  )
  if (open)
      ConsoleUserPicker(
          users,
          userId,
          onDismiss = { open = false },
          onSelect = {
            onUser(it)
            open = false
          },
      )
}

@Composable
internal fun ConsoleDeviceFilters(
    users: List<AdminApi.HsUser>,
    tags: List<String>,
    presence: DevicePresence,
    onPresence: (DevicePresence) -> Unit,
    userId: String?,
    onUser: (String?) -> Unit,
    tag: String?,
    onTag: (String?) -> Unit,
    sort: DeviceSort,
    onSort: (DeviceSort) -> Unit,
) {
  var tagPicker by remember { mutableStateOf(false) }
  var tagQuery by remember { mutableStateOf("") }
  Column {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      DevicePresence.entries.forEach { candidate ->
        FilterChip(
            selected = candidate == presence,
            onClick = { onPresence(candidate) },
            label = {
              Text(
                  stringResource(
                      when (candidate) {
                        DevicePresence.ALL -> R.string.admin_key_status_all
                        DevicePresence.ONLINE -> R.string.admin_device_online
                        DevicePresence.OFFLINE -> R.string.admin_device_offline
                      }
                  )
              )
            },
        )
      }
      ConsoleUserFilter(users, userId, onUser)
      FilterChip(
          selected = tag != null,
          onClick = { tagPicker = true },
          label = { Text(tag ?: stringResource(R.string.admin_all_tags)) },
      )
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      DeviceSort.entries.forEach { candidate ->
        FilterChip(
            selected = candidate == sort,
            onClick = { onSort(candidate) },
            label = {
              Text(
                  stringResource(
                      when (candidate) {
                        DeviceSort.ONLINE_FIRST -> R.string.admin_sort_online
                        DeviceSort.NAME -> R.string.admin_sort_name
                        DeviceSort.LAST_SEEN -> R.string.admin_sort_seen
                      }
                  )
              )
            },
        )
      }
    }
  }
  if (tagPicker)
      AlertDialog(
          onDismissRequest = { tagPicker = false },
          title = { Text(stringResource(R.string.admin_node_tags)) },
          text = {
            Column {
              OutlinedTextField(
                  value = tagQuery,
                  onValueChange = { tagQuery = it },
                  singleLine = true,
                  label = { Text(stringResource(R.string.admin_search_tags)) },
              )
              LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item {
                  TextButton(
                      onClick = {
                        onTag(null)
                        tagPicker = false
                      }
                  ) {
                    Text(stringResource(R.string.admin_all_tags))
                  }
                }
                items(tags.filter { it.contains(tagQuery.trim(), true) }) { candidate ->
                  TextButton(
                      onClick = {
                        onTag(candidate)
                        tagPicker = false
                      }
                  ) {
                    Text(candidate)
                  }
                }
              }
            }
          },
          confirmButton = {
            TextButton(onClick = { tagPicker = false }) { Text(stringResource(R.string.cancel)) }
          },
      )
}
