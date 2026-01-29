// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn

import android.content.Context
import android.os.Build
import android.util.Log

object AppSourceChecker {

  const val TAG = "AppSourceChecker"

  fun getInstallSource(context: Context): String {
    return "unknown"
  }
}
