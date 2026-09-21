// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.App
import com.tailscale.ipn.BuildConfig
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.Links
import com.tailscale.ipn.ui.theme.link
import com.tailscale.ipn.ui.theme.logoBackground
import com.tailscale.ipn.ui.util.AppVersion
import com.tailscale.ipn.ui.util.UpdateChecker
import com.tailscale.ipn.util.BrowserOpener
import kotlinx.coroutines.launch

@Composable
fun AboutView(backToSettings: BackNavigation) {
  val localClipboardManager = LocalClipboardManager.current

  // In-app update check against this fork's GitHub releases (moved here from Settings).
  val context = LocalContext.current
  val updateScope = rememberCoroutineScope()
  var updateChecking by remember { mutableStateOf(false) }
  var updateState by remember { mutableStateOf<UpdateChecker.Result?>(null) }
  var showUpdateDialog by remember { mutableStateOf(false) }
  var updateErrorMessage by remember { mutableStateOf<String?>(null) }

  Scaffold(topBar = { Header(R.string.about_view_header, onBack = backToSettings) }) { innerPadding
    ->
    Column(
        verticalArrangement =
            Arrangement.spacedBy(space = 20.dp, alignment = Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())) {
          TailscaleLogoView(
              usesOnBackgroundColors = true,
              modifier =
                  Modifier.width(100.dp)
                      .height(100.dp)
                      .clip(RoundedCornerShape(50))
                      .background(MaterialTheme.colorScheme.logoBackground)
                      .padding(25.dp))

          Column(
              verticalArrangement =
                  Arrangement.spacedBy(space = 2.dp, alignment = Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.about_view_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize)
                Text(
                    modifier =
                        Modifier.clickable {
                          // When users tap on the version number, the extended version string
                          // (including commit hashes) is copied to the clipboard.
                          // This may be useful for debugging purposes...
                          localClipboardManager.setText(AnnotatedString(BuildConfig.VERSION_NAME))
                        },
                    // ... but we always display the short version in the UI to avoid user
                    // confusion.
                    text = "${stringResource(R.string.version)} ${AppVersion.Short()}",
                    fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize)
              }

          TextButton(
              enabled = !updateChecking,
              onClick = {
                updateChecking = true
                updateScope.launch {
                  updateState = UpdateChecker.check()
                  updateChecking = false
                  showUpdateDialog = true
                }
              }) {
                Text(
                    when {
                      updateChecking -> stringResource(R.string.update_checking)
                      updateState is UpdateChecker.Result.Available ->
                          stringResource(
                              R.string.update_available_subtitle,
                              (updateState as UpdateChecker.Result.Available).release.tag)
                      else -> stringResource(R.string.check_for_updates)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.link,
                    textDecoration = TextDecoration.Underline)
              }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OpenURLButton(stringResource(R.string.acknowledgements), Links.LICENSES_URL)
            OpenURLButton(stringResource(R.string.privacy_policy), Links.PRIVACY_POLICY_URL)
            OpenURLButton(stringResource(R.string.terms_of_service), Links.TERMS_URL)
          }

          Text(
              stringResource(R.string.about_view_footnotes),
              fontWeight = FontWeight.Normal,
              fontSize = MaterialTheme.typography.labelMedium.fontSize,
              textAlign = TextAlign.Center)
        }
  }

  val checkedState = updateState
  if (showUpdateDialog && checkedState != null) {
    when (checkedState) {
      is UpdateChecker.Result.Available ->
          UpdateAvailableDialog(
              release = checkedState.release,
              onDismiss = { showUpdateDialog = false },
              onOpenInBrowser = {
                showUpdateDialog = false
                BrowserOpener.openInDefaultBrowser(context, Uri.parse(checkedState.release.htmlUrl))
              },
              onDownload = {
                showUpdateDialog = false
                Toast.makeText(context, R.string.update_download_started, Toast.LENGTH_SHORT).show()
                // Run on the app scope so the download/install survives leaving this screen.
                App.get().applicationScope.launch {
                  try {
                    UpdateChecker.downloadAndInstall(context, checkedState.release)
                  } catch (e: Exception) {
                    updateErrorMessage = e.message ?: e.javaClass.simpleName
                  }
                }
              })
      is UpdateChecker.Result.UpToDate ->
          AlertDialog(
              onDismissRequest = { showUpdateDialog = false },
              title = { Text(stringResource(R.string.update_up_to_date_title)) },
              text = { Text(stringResource(R.string.update_up_to_date_message, AppVersion.Short())) },
              confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                  Text(stringResource(R.string.ok))
                }
              })
      is UpdateChecker.Result.Failed ->
          AlertDialog(
              onDismissRequest = { showUpdateDialog = false },
              title = { Text(stringResource(R.string.update_check_failed_title)) },
              text = { Text(checkedState.message) },
              confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                  Text(stringResource(R.string.ok))
                }
              })
    }
  }

  updateErrorMessage?.let { message ->
    AlertDialog(
        onDismissRequest = { updateErrorMessage = null },
        title = { Text(stringResource(R.string.update_download_failed_title)) },
        text = { Text(message) },
        confirmButton = {
          TextButton(onClick = { updateErrorMessage = null }) { Text(stringResource(R.string.ok)) }
        })
  }
}

@Composable
private fun UpdateAvailableDialog(
    release: UpdateChecker.Release,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.update_available_title, release.tag)) },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          release.publishedAt?.let {
            Text(
                stringResource(R.string.update_published_at, it.take(10)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          release.body?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDownload) {
          Text(stringResource(R.string.update_download_and_install))
        }
      },
      dismissButton = {
        TextButton(onClick = onOpenInBrowser) {
          Text(stringResource(R.string.update_open_in_browser))
        }
      })
}

@Preview
@Composable
fun AboutPreview() {
  AboutView({})
}
