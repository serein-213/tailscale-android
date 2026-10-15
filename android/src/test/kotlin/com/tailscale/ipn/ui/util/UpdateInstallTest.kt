// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.*

class UpdateInstallTest {
  @Test
  fun permissionReturnInstallsDownloadedFileExactlyOnce() {
    val directory = Files.createTempDirectory("update-install-test").toFile()
    try {
      val apk = directory.resolve("update.apk").apply { writeText("test") }
      val context = mock<Context>()
      val manager = mock<PackageManager>()
      val prefs = mock<SharedPreferences>()
      val editor = mock<SharedPreferences.Editor>(defaultAnswer = Answers.RETURNS_SELF)
      var pending: String? = null
      whenever(context.packageManager).thenReturn(manager)
      whenever(context.packageName).thenReturn("com.tailscale.ipn")
      whenever(context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)).thenReturn(prefs)
      whenever(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)).thenReturn(directory)
      whenever(prefs.edit()).thenReturn(editor)
      whenever(prefs.getString("pending_update_apk", null)).thenAnswer { pending }
      whenever(editor.putString(eq("pending_update_apk"), any())).thenAnswer {
        pending = it.getArgument(1)
        editor
      }
      whenever(editor.remove("pending_update_apk")).thenAnswer {
        pending = null
        editor
      }
      val uri = mock<Uri>()
      val actions = mutableListOf<String>()
      Mockito.mockStatic(Uri::class.java).use { uris ->
        uris.`when`<Uri> { Uri.parse("package:com.tailscale.ipn") }.thenReturn(uri)
        Mockito.mockStatic(FileProvider::class.java).use { provider ->
          provider
              .`when`<Uri> {
                FileProvider.getUriForFile(context, "com.tailscale.ipn.fileprovider", apk)
              }
              .thenReturn(uri)
          Mockito.mockConstruction(
                  Intent::class.java,
                  Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF),
              ) { _, call ->
                actions += call.arguments()[0] as String
              }
              .use {
                whenever(manager.canRequestPackageInstalls()).thenReturn(false)
                UpdateChecker.installDownloadedApk(context, apk)
                assertEquals("update.apk", pending)
                assertFalse(UpdateChecker.resumePendingInstall(context))
                whenever(manager.canRequestPackageInstalls()).thenReturn(true)
                assertTrue(UpdateChecker.resumePendingInstall(context))
                assertFalse(UpdateChecker.resumePendingInstall(context))
                assertNull(pending)
                assertEquals(
                    listOf(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Intent.ACTION_VIEW),
                    actions,
                )
                verify(context, times(2)).startActivity(any())
              }
        }
      }
    } finally {
      directory.deleteRecursively()
    }
  }
}
