// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.AdminApi
import com.tailscale.ipn.util.TSLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only view of the tailnet access policy. A full screen rather than a dialog: policies are
 * thousands of characters of huJSON and never fit in an alert.
 */
@Composable
fun AdminPolicyView(backTo: BackNavigation) {
  val TAG = "AdminPolicyView"
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current

  var policy by remember { mutableStateOf<AdminApi.HsPolicy?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(true) }

  fun load() {
    scope.launch {
      loading = true
      runCatching { withContext(Dispatchers.IO) { AdminApi.policy() } }
          .onSuccess {
            policy = it
            error = null
          }
          .onFailure {
            TSLog.w(TAG, "policy load failed: ${it.message}")
            error = it.message ?: it.javaClass.simpleName
          }
      loading = false
    }
  }

  LaunchedEffect(Unit) { load() }

  val body = policy?.policy.orEmpty()

  Scaffold(
      topBar = {
        Header(
            R.string.admin_policy,
            onBack = backTo,
            actions = {
              TextButton(onClick = { load() }, enabled = !loading) {
                Text(stringResource(R.string.admin_refresh))
              }
              TextButton(
                  enabled = body.isNotEmpty(),
                  onClick = { clipboard.setText(AnnotatedString(body)) }) {
                    Text(stringResource(R.string.copy_to_clipboard))
                  }
            })
      }) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding)) {
      when {
        error != null -> {
          Text(
              error!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(16.dp))
        }
        body.isEmpty() && loading -> {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(32.dp))
          }
        }
        else -> {
          Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                policy?.updatedAt?.let {
                  Text(
                      stringResource(R.string.admin_policy_updated, it.replace("T", " ").take(19)),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(top = 16.dp))
                }
              }
        }
      }
    }
  }
}
