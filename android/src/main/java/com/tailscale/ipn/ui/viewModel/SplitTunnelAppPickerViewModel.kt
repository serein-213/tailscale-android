// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.mdm.MDMSettings
import com.tailscale.ipn.mdm.SettingState
import com.tailscale.ipn.ui.util.InstalledApp
import com.tailscale.ipn.ui.util.InstalledAppsManager
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class SplitTunnelAppPickerViewModel : ViewModel() {
  val installedAppsManager = InstalledAppsManager(packageManager = App.get().packageManager)
  val excludedPackageNames = MutableStateFlow<List<String>>(listOf())
  val allInstalledApps = MutableStateFlow<List<InstalledApp>>(listOf())
  
  val searchQuery = MutableStateFlow("")
  val showSystemApps = MutableStateFlow(false)

  val filteredApps: StateFlow<List<InstalledApp>> = combine(allInstalledApps, searchQuery, showSystemApps) { apps, query, showSystem ->
    apps.filter { app ->
      (app.isSystemApp == showSystem) && 
      (query.isEmpty() || app.name.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf())

  val mdmExcludedPackages: StateFlow<SettingState<String?>> = MDMSettings.excludedPackages.flow
  val mdmIncludedPackages: StateFlow<SettingState<String?>> = MDMSettings.includedPackages.flow

  private var saveJob: Job? = null

  init {
    val apps = installedAppsManager.fetchInstalledApps()
    allInstalledApps.value = apps
    excludedPackageNames.value = App.get()
            .disallowedPackageNames()
            .intersect(apps.map { it.packageName }.toSet())
            .toList()
  }

  fun exclude(packageName: String) {
    if (excludedPackageNames.value.contains(packageName)) return
    excludedPackageNames.value = excludedPackageNames.value + packageName
    debounceSave()
  }

  fun unexclude(packageName: String) {
    excludedPackageNames.value = excludedPackageNames.value - packageName
    debounceSave()
  }

  fun selectAll() {
    val currentFiltered = filteredApps.value.map { it.packageName }
    excludedPackageNames.value = (excludedPackageNames.value.toSet() + currentFiltered.toSet()).toList()
    debounceSave()
  }

  fun deselectAll() {
    val currentFiltered = filteredApps.value.map { it.packageName }.toSet()
    excludedPackageNames.value = excludedPackageNames.value.filter { !currentFiltered.contains(it) }
    debounceSave()
  }

  fun toggleAll() {
    val currentFiltered = filteredApps.value.map { it.packageName }
    val currentExcluded = excludedPackageNames.value.toSet()
    val nextExcluded = excludedPackageNames.value.toMutableList()
    
    currentFiltered.forEach { pkg ->
      if (currentExcluded.contains(pkg)) {
        nextExcluded.remove(pkg)
      } else {
        nextExcluded.add(pkg)
      }
    }
    excludedPackageNames.value = nextExcluded
    debounceSave()
  }

  private fun debounceSave() {
    saveJob?.cancel()
    saveJob =
        viewModelScope.launch {
          delay(500) // Wait to batch multiple rapid updates
          App.get().updateUserDisallowedPackageNames(excludedPackageNames.value)
        }
  }
}
