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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SplitTunnelAppPickerViewModel : ViewModel() {
  val installedAppsManager = InstalledAppsManager(packageManager = App.get().packageManager)

  val allInstalledApps = MutableStateFlow<List<InstalledApp>>(listOf())
  val selectedPackageNames: StateFlow<List<String>> = MutableStateFlow(listOf())

  val allowSelected: StateFlow<Boolean> = MutableStateFlow(App.get().allowSelectedPackages())
  val showHeaderMenu: StateFlow<Boolean> = MutableStateFlow(false)
  val showSwitchDialog: StateFlow<Boolean> = MutableStateFlow(false)

  val searchQuery = MutableStateFlow("")
  val showSystemApps = MutableStateFlow(false)

  val filteredApps: StateFlow<List<InstalledApp>> =
      combine(allInstalledApps, searchQuery, showSystemApps) { apps, query, showSystem ->
            apps.filter { app ->
              (app.isSystemApp == showSystem) &&
                  (query.isEmpty() ||
                      app.name.contains(query, ignoreCase = true) ||
                      app.packageName.contains(query, ignoreCase = true))
            }
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf())

  val mdmExcludedPackages: StateFlow<SettingState<String?>> = MDMSettings.excludedPackages.flow
  val mdmIncludedPackages: StateFlow<SettingState<String?>> = MDMSettings.includedPackages.flow

  private var saveJob: Job? = null

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val apps = installedAppsManager.fetchInstalledApps()
      allInstalledApps.value = apps
      initSelectedPackageNames()
    }
  }

  private fun initSelectedPackageNames() {
    allowSelected.set(App.get().allowSelectedPackages())
    selectedPackageNames.set(
        App.get()
            .selectedPackageNames()
            .let {
              if (!allowSelected.value) {
                it.union(App.get().builtInDisallowedPackageNames)
              } else {
                it
              }
            }
            .intersect(allInstalledApps.value.map { it.packageName }.toSet())
            .toList())
  }

  fun performSelectionSwitch() {
    App.get().switchUserSelectedPackages()
    initSelectedPackageNames()
  }

  fun select(packageName: String) {
    if (selectedPackageNames.value.contains(packageName)) return

    selectedPackageNames.set(selectedPackageNames.value + packageName)
    debounceSave()
  }

  fun deselect(packageName: String) {
    selectedPackageNames.set(selectedPackageNames.value - packageName)
    debounceSave()
  }

  fun selectAll() {
    val currentFiltered = filteredApps.value.map { it.packageName }
    selectedPackageNames.set((selectedPackageNames.value.toSet() + currentFiltered.toSet()).toList())
    debounceSave()
  }

  fun deselectAll() {
    val currentFiltered = filteredApps.value.map { it.packageName }.toSet()
    selectedPackageNames.set(selectedPackageNames.value.filter { !currentFiltered.contains(it) })
    debounceSave()
  }

  fun toggleAll() {
    val currentFiltered = filteredApps.value.map { it.packageName }
    val currentSelected = selectedPackageNames.value.toSet()
    val nextSelected = selectedPackageNames.value.toMutableList()

    currentFiltered.forEach { pkg ->
      if (currentSelected.contains(pkg)) {
        nextSelected.remove(pkg)
      } else {
        nextSelected.add(pkg)
      }
    }
    selectedPackageNames.set(nextSelected)
    debounceSave()
  }

  private fun debounceSave() {
    saveJob?.cancel()
    saveJob =
        viewModelScope.launch {
          delay(500) // Wait to batch multiple rapid updates
          App.get().updateUserSelectedPackages(selectedPackageNames.value)
        }
  }
}
