// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.App
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.viewModel.SplitTunnelAppPickerViewModel

@Composable
fun SplitTunnelAppPickerView(
    backToSettings: BackNavigation,
    model: SplitTunnelAppPickerViewModel = viewModel()
) {
  val installedApps by model.filteredApps.collectAsState()
  val excludedPackageNames by model.excludedPackageNames.collectAsState()
  val searchQuery by model.searchQuery.collectAsState()
  val showSystemApps by model.showSystemApps.collectAsState()
  val builtInDisallowedPackageNames: List<String> = App.get().builtInDisallowedPackageNames
  val mdmIncludedPackages by model.mdmIncludedPackages.collectAsState()
  val mdmExcludedPackages by model.mdmExcludedPackages.collectAsState()

  Scaffold(topBar = { Header(titleRes = R.string.split_tunneling, onBack = backToSettings) }) {
      innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
      LazyColumn(modifier = Modifier.weight(1f)) {
        item(key = "header") {
          ListItem(
              headlineContent = {
                Text(
                    stringResource(
                        R.string
                            .selected_apps_will_access_the_internet_directly_without_using_tailscale))
              })
        }

        item(key = "search") {
          OutlinedTextField(
              value = searchQuery,
              onValueChange = { model.searchQuery.value = it },
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 8.dp),
              placeholder = { Text("搜索应用...") },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                  IconButton(onClick = { model.searchQuery.value = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                  }
                }
              },
              singleLine = true,
              shape = MaterialTheme.shapes.medium
          )
        }

        item(key = "system_toggle") {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
                checked = showSystemApps,
                onCheckedChange = { model.showSystemApps.value = it }
            )
            Text("显示系统应用", style = MaterialTheme.typography.bodyMedium)
          }
        }

        if (mdmExcludedPackages.value?.isNotEmpty() == true) {
          item("mdmExcludedNotice") {
            ListItem(
                headlineContent = {
                  Text(stringResource(R.string.certain_apps_are_not_routed_via_tailscale))
                })
          }
        } else if (mdmIncludedPackages.value?.isNotEmpty() == true) {
          item("mdmIncludedNotice") {
            ListItem(
                headlineContent = {
                  Text(stringResource(R.string.only_specific_apps_are_routed_via_tailscale))
                })
          }
        } else {
          item("resolversHeader") {
            val allVisibleSelected = installedApps.isNotEmpty() && installedApps.all { excludedPackageNames.contains(it.packageName) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Box(modifier = Modifier.weight(1f)) {
                Lists.SectionDivider(
                    stringResource(R.string.count_excluded_apps, excludedPackageNames.count()))
              }
              
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(top = 12.dp, end = 16.dp) // 与 SectionDivider 的内部 padding 抵消对齐
              ) {
                Checkbox(
                    checked = allVisibleSelected,
                    onCheckedChange = { checked ->
                      if (checked) model.selectAll() else model.deselectAll()
                    }
                )
                Text("全选", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.width(4.dp))
                
                TextButton(
                    onClick = { model.toggleAll() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                  Text("反选", style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
          
          items(installedApps, key = { it.packageName }) { app ->
            ListItem(
                headlineContent = { Text(app.name, fontWeight = FontWeight.SemiBold) },
                leadingContent = {
                  Image(
                      bitmap =
                          model.installedAppsManager.packageManager
                              .getApplicationIcon(app.packageName)
                              .toBitmap()
                              .asImageBitmap(),
                      contentDescription = null,
                      modifier = Modifier.width(40.dp).height(40.dp))
                },
                supportingContent = {
                  Text(
                      app.packageName + if (app.isSystemApp) " (系统)" else "",
                      color = MaterialTheme.colorScheme.secondary,
                      fontSize = MaterialTheme.typography.bodySmall.fontSize,
                      letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing)
                },
                trailingContent = {
                  Checkbox(
                      checked = excludedPackageNames.contains(app.packageName),
                      enabled = !builtInDisallowedPackageNames.contains(app.packageName),
                      onCheckedChange = { checked ->
                        if (checked) {
                          model.exclude(packageName = app.packageName)
                        } else {
                          model.unexclude(packageName = app.packageName)
                        }
                      })
                })
            Lists.ItemDivider()
          }
        }
      }
    }
  }
}
