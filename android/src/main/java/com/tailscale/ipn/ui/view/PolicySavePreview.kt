// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.admin.PolicyDiff

@Composable
internal fun PolicySavePreview(
    before: String,
    after: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
  val diff = remember(before, after) { PolicyDiff.compare(before, after) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.policy_save_title)) },
      text = {
        SelectionContainer {
          LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            item {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.policy_save_warning))
                Text(
                    stringResource(R.string.policy_save_preflight_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.policy_save_diff_summary, diff.added, diff.removed),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.policy_save_diff_legend),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (diff.broadReplacement) {
                  Text(
                      stringResource(R.string.policy_save_diff_broad),
                      style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
            }
            items(diff.lines) { line ->
              val prefix =
                  when (line.kind) {
                    PolicyDiff.Kind.ADDED -> "+ "
                    PolicyDiff.Kind.REMOVED -> "− "
                    PolicyDiff.Kind.CONTEXT -> "  "
                    PolicyDiff.Kind.GAP -> "…"
                  }
              Text(
                  prefix + line.text.replace("\r", "␍"),
                  fontFamily = FontFamily.Monospace,
                  style = MaterialTheme.typography.bodySmall,
                  color =
                      when (line.kind) {
                        PolicyDiff.Kind.ADDED -> MaterialTheme.colorScheme.primary
                        PolicyDiff.Kind.REMOVED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                      },
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(enabled = !saving && before != after, onClick = onConfirm) {
          Text(stringResource(R.string.policy_save_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
      },
  )
}
