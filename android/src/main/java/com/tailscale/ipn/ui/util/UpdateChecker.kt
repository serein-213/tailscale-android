// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tailscale.ipn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks this project's GitHub releases for a build newer than the one that is running.
 *
 * Builds are stamped with the commit they were built from (VERSION_LONG ends in "-g<sha>") and
 * releases are tagged on that same commit, so comparing the two is enough to decide whether the
 * installed build is the latest published one. When the hashes differ the GitHub compare API is
 * consulted, which keeps locally built (ahead) builds from being offered a downgrade.
 */
object UpdateChecker {
  private const val REPO = "serein-213/tailscale-android"
  private const val API = "https://api.github.com/repos/$REPO"
  private const val ARM64_ASSET = "android-arm64-v8a-release.apk"
  private const val UNIVERSAL_ASSET = "android-universal-release.apk"
  private const val APK_MIME = "application/vnd.android.package-archive"

  private val json = Json { ignoreUnknownKeys = true }

  @Serializable
  data class Asset(
      val name: String = "",
      @SerialName("browser_download_url") val downloadUrl: String = "",
      val size: Long = 0,
  )

  @Serializable
  data class Release(
      @SerialName("tag_name") val tag: String = "",
      @SerialName("html_url") val htmlUrl: String = "",
      @SerialName("published_at") val publishedAt: String? = null,
      val body: String? = null,
      val assets: List<Asset> = emptyList(),
  )

  @Serializable private data class Commit(val sha: String = "")

  @Serializable private data class Compare(val status: String = "")

  sealed class Result {
    object UpToDate : Result()

    data class Available(val release: Release) : Result()

    data class Failed(val message: String) : Result()
  }

  /** Commit hash embedded in the version name, e.g. "1.102.4-t3caf7d9e7-g065e67dda". */
  fun currentCommit(): String? =
      Regex("-g([0-9a-f]{7,40})").find(BuildConfig.VERSION_NAME)?.groupValues?.get(1)

  suspend fun check(): Result =
      withContext(Dispatchers.IO) {
        try {
          val release = getJson("$API/releases/latest", Release.serializer())
          if (release.tag.isBlank()) {
            return@withContext Result.Failed("release tag missing")
          }
          val local = currentCommit()
          if (local == null) {
            // No commit stamp (unexpected for this fork's builds): fall back to the base version.
            return@withContext if (release.tag.startsWith(AppVersion.Short())) Result.UpToDate
            else Result.Available(release)
          }
          val remote = getJson("$API/commits/${release.tag}", Commit.serializer()).sha
          val state =
              when {
                remote.isEmpty() -> ""
                remote.startsWith(local) -> "identical"
                else -> getJson("$API/compare/$remote...$local", Compare.serializer()).status
              }
          // "behind" means the published release contains commits this build lacks; "diverged"
          // means the histories no longer line up at all, which is worth flagging as well.
          if (state == "behind" || state == "diverged") Result.Available(release)
          else Result.UpToDate
        } catch (e: Exception) {
          Result.Failed(e.message ?: e.javaClass.simpleName)
        }
      }

  /** The asset that matches this device's CPU architecture, falling back to the universal APK. */
  fun assetForThisDevice(release: Release): Asset? {
    val preferred =
        if (Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }) ARM64_ASSET
        else UNIVERSAL_ASSET
    return release.assets.firstOrNull { it.name == preferred }
        ?: release.assets.firstOrNull { it.name == UNIVERSAL_ASSET }
        ?: release.assets.firstOrNull { it.name.endsWith("-release.apk") }
  }

  /**
   * Downloads the release APK through the system download manager, then hands it to the package
   * installer. Throws if the download fails or the installer cannot be reached.
   */
  suspend fun downloadAndInstall(context: Context, release: Release) {
    val asset = assetForThisDevice(release) ?: error("no APK asset in release ${release.tag}")
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val fileName = asset.name.ifBlank { "tailscale-update.apk" }
    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
      File(dir, fileName).takeIf { it.exists() }?.delete()
    }
    val request =
        DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Tailscale ${release.tag}")
            .setMimeType(APK_MIME)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    val id = manager.enqueue(request)
    while (true) {
      delay(1000)
      val status =
          manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
          } ?: error("download disappeared")
      when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> {
          val apk =
              context
                  .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                  ?.let { File(it, fileName) }
          if (apk == null || !apk.exists()) error("download completed but file is missing")
          install(context, apk)
          return
        }
        DownloadManager.STATUS_FAILED -> error("download failed")
      }
    }
  }

  /** Opens the system package installer for [apk], asking for install permission if needed. */
  private fun install(context: Context, apk: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
      context.startActivity(
          Intent(
                  Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                  Uri.parse("package:${context.packageName}"))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
  }

  private fun <T> getJson(url: String, serializer: KSerializer<T>): T {
    val connection =
        (URL(url).openConnection() as HttpURLConnection).apply {
          connectTimeout = 10_000
          readTimeout = 15_000
          setRequestProperty("Accept", "application/vnd.github+json")
          setRequestProperty("User-Agent", "tailscale-android-mod/${BuildConfig.VERSION_NAME}")
        }
    try {
      if (connection.responseCode !in 200..299) {
        throw IllegalStateException("HTTP ${connection.responseCode}")
      }
      val body = connection.inputStream.bufferedReader().use { it.readText() }
      return json.decodeFromString(serializer, body)
    } finally {
      connection.disconnect()
    }
  }
}
