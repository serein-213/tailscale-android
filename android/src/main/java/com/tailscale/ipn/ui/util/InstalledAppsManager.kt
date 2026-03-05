// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledApp(val name: String, val packageName: String, val isSystemApp: Boolean)

class InstalledAppsManager(
    val packageManager: PackageManager,
) {
  fun fetchInstalledApps(): List<InstalledApp> {
    return packageManager
        .getInstalledApplications(PackageManager.GET_META_DATA)
        .filter(appIsIncluded)
        .map {
          val isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                         (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
          InstalledApp(
              name = it.loadLabel(packageManager).toString(),
              packageName = it.packageName,
              isSystemApp = isSystem
          )
        }
        .sortedBy { it.name }
  }

  private val appIsIncluded: (ApplicationInfo) -> Boolean = { app ->
    app.packageName != "com.tailscale.ipn" &&
        // Only show apps that can access the Internet
        packageManager.checkPermission(Manifest.permission.INTERNET, app.packageName) ==
            PackageManager.PERMISSION_GRANTED
  }
}
