// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Tailcfg
import com.tailscale.ipn.ui.theme.off
import com.tailscale.ipn.ui.theme.on
import kotlin.math.absoluteValue

@Composable
fun PeerView(
    peer: Tailcfg.Node,
    selfPeer: String? = null,
    stateVal: Ipn.State? = null,
    subtitle: () -> String = { peer.primaryIPv4Address ?: peer.primaryIPv6Address ?: "" },
    onClick: (Tailcfg.Node) -> Unit = {},
    trailingContent: @Composable () -> Unit = {}
) {
  val disabled = !(peer.Online ?: false)
  val textColor = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified

  ListItem(
      modifier = Modifier.clickable { onClick(peer) },
      headlineContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // By definition, SelfPeer is online since we will not show the peer list
          // unless you're connected.
          val isSelfAndRunning = (peer.StableID == selfPeer && stateVal == Ipn.State.Running)
          val color: Color =
              if ((peer.Online == true) || isSelfAndRunning) {
                MaterialTheme.colorScheme.on
              } else {
                MaterialTheme.colorScheme.off
              }
          Box(
              modifier =
                  Modifier.size(8.dp)
                      .background(color = color, shape = RoundedCornerShape(percent = 50))) {}
          Spacer(modifier = Modifier.size(8.dp))
          Text(
              text = peer.displayName,
              style = MaterialTheme.typography.titleMedium,
              color = textColor)
        }
      },
      supportingContent = {
        Text(text = subtitle(), style = MaterialTheme.typography.bodyMedium, color = textColor)
      },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          peer.Tags?.let { allTags ->
            val displayTags = allTags.take(3)
            val hasMore = allTags.size > 3

            displayTags.forEach { fullTag ->
              val tagName = fullTag.removePrefix("tag:")
              AnimatedTagItem(name = tagName)
              Spacer(modifier = Modifier.size(4.dp))
            }

            if (hasMore) {
              Text(
                  text = "+${allTags.size - 3}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.size(4.dp))
            }
          }
          trailingContent()
        }
      })
}

@Composable
fun AnimatedTagItem(name: String) {
  // Simple animation when the tag enters the screen
  AnimatedVisibility(
      visible = true,
      enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
      label = "TagFadeIn") {
    TagItem(name = name)
  }
}

@Composable
fun TagItem(name: String) {
  // Generate a consistent base color from the tag name
  // Use HSL to ensure colors are vibrant but readable
  val baseColor = remember(name) {
    val hash = name.hashCode().absoluteValue
    // Saturation 0.65, Lightness 0.45 provides good contrast for text on both light/dark themes usually
    // because we use it as the text color primarily.
    Color.hsl(hue = (hash % 360).toFloat(), saturation = 0.65f, lightness = 0.45f)
  }

  Surface(
      shape = RoundedCornerShape(6.dp),
      // Use a very light opacity of the base color for the background container
      color = baseColor.copy(alpha = 0.12f),
      // No border for "Tonal" style chips
      border = null) {
    Text(
        text = name,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        // Text is the full opacity base color
        color = baseColor)
  }
}
