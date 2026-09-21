// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import com.tailscale.ipn.App
import com.tailscale.ipn.util.TSLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal client for the Headscale HTTP API (v1) used by the in-app tailnet admin screens.
 *
 * The API base URL and the API key are entered once in [AdminConsoleView] and kept in
 * EncryptedSharedPreferences. Endpoint shapes verified against Headscale v0.29.3.
 */
object AdminApi {
  private const val TAG = "AdminApi"
  private const val PREF_KEY_API_KEY = "headscale_admin_api_key"
  private const val PREF_KEY_BASE_URL = "headscale_admin_base_url"

  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  // ---------------------------------------------------------------- models

  @Serializable
  data class HsUser(val id: String = "", val name: String = "", val email: String = "")

  @Serializable
  data class HsNode(
      val id: String = "",
      val name: String = "",
      val givenName: String = "",
      val user: HsUser? = null,
      val ipAddresses: List<String> = emptyList(),
      val online: Boolean = false,
      val lastSeen: String? = null,
      val expiry: String? = null,
      val validTags: List<String> = emptyList(),
      val tags: List<String> = emptyList(),
      val approvedRoutes: List<String> = emptyList(),
      val availableRoutes: List<String> = emptyList(),
      @SerialName("registerMethod") val registerMethod: String = "",
  ) {
    val displayName: String
      get() = givenName.ifBlank { name }.ifBlank { id }
  }

  @Serializable
  data class HsPreAuthKey(
      val id: String = "",
      val key: String = "",
      val user: HsUser? = null,
      val reusable: Boolean = false,
      val ephemeral: Boolean = false,
      val used: Boolean = false,
      val expiration: String? = null,
      val createdAt: String? = null,
  )

  @Serializable private data class NodesResponse(val nodes: List<HsNode> = emptyList())
  @Serializable private data class UsersResponse(val users: List<HsUser> = emptyList())

  @Serializable
  data class HsPolicy(val policy: String = "", val updatedAt: String? = null)

  @Serializable private data class CreateUserRequest(val name: String)
  @Serializable private data class CreateUserResponse(val user: HsUser? = null)

  @Serializable private data class NodeResponse(val node: HsNode? = null)
  @Serializable private data class SetTagsRequest(val tags: List<String>)
  @Serializable private data class ApproveRoutesRequest(val routes: List<String>)

  @Serializable
  private data class PreAuthKeysResponse(val preAuthKeys: List<HsPreAuthKey> = emptyList())

  @Serializable private data class PreAuthKeyResponse(val preAuthKey: HsPreAuthKey? = null)

  @Serializable
  private data class CreatePreAuthKeyRequest(
      val user: String,
      val reusable: Boolean,
      val ephemeral: Boolean,
      val expiration: String? = null,
  )

  @Serializable private data class IdRequest(val id: String)

  // ------------------------------------------------------------- settings

  fun baseUrl(): String? = prefs()?.getString(PREF_KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }

  fun apiKey(): String? = prefs()?.getString(PREF_KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

  fun isConfigured(): Boolean = baseUrl() != null && apiKey() != null

  /** Stores the connection settings; pass null to clear them. */
  fun saveConnection(baseUrl: String?, apiKey: String?) {
    val p = prefs() ?: return
    p.edit()
        .apply {
          if (baseUrl.isNullOrBlank()) remove(PREF_KEY_BASE_URL)
          else putString(PREF_KEY_BASE_URL, normalizeBaseUrl(baseUrl))
          if (apiKey.isNullOrBlank()) remove(PREF_KEY_API_KEY) else putString(PREF_KEY_API_KEY, apiKey.trim())
        }
        .apply()
  }

  private fun normalizeBaseUrl(url: String): String = normalizeBaseUrlInternal(url)

  private fun prefs() = runCatching { App.get().getEncryptedPrefs() }
      .onFailure { TSLog.e(TAG, "encrypted prefs unavailable", it) }
      .getOrNull()

  // ----------------------------------------------------------------- API

  fun nodes(): List<HsNode> = parseNodes(request("GET", "/api/v1/node"))

  fun users(): List<HsUser> = parseUsers(request("GET", "/api/v1/user"))

  fun preAuthKeys(): List<HsPreAuthKey> =
      parsePreAuthKeys(request("GET", "/api/v1/preauthkey"))

  fun policy(): HsPolicy = parsePolicy(request("GET", "/api/v1/policy"))

  fun expireNode(id: String) {
    request("POST", "/api/v1/node/$id/expire")
  }

  fun deleteNode(id: String) {
    request("DELETE", "/api/v1/node/$id")
  }

  fun renameNode(id: String, newName: String) {
    request("POST", "/api/v1/node/$id/rename/${urlEncode(newName)}")
  }

  /** Replaces the node's tags; the "tag:" prefix is added when missing. */
  fun setNodeTags(id: String, tags: List<String>) {
    val body = json.encodeToString(SetTagsRequest.serializer(), SetTagsRequest(normalizeTags(tags)))
    request("POST", "/api/v1/node/$id/tags", body)
  }

  /** Approves exactly [routes]: the API replaces the approved set instead of merging into it. */
  fun approveRoutes(id: String, routes: List<String>) {
    val body = json.encodeToString(ApproveRoutesRequest.serializer(), ApproveRoutesRequest(routes))
    request("POST", "/api/v1/node/$id/approve_routes", body)
  }

  fun createUser(name: String): HsUser? {
    val body = json.encodeToString(CreateUserRequest.serializer(), CreateUserRequest(name))
    return json.decodeFromString<CreateUserResponse>(request("POST", "/api/v1/user", body)).user
  }

  fun renameUser(id: String, newName: String) {
    request("POST", "/api/v1/user/$id/rename/${urlEncode(newName)}")
  }

  fun deleteUser(id: String) {
    request("DELETE", "/api/v1/user/$id")
  }

  fun expirePreAuthKey(id: String) {
    request(
        "POST",
        "/api/v1/preauthkey/expire",
        json.encodeToString(IdRequest.serializer(), IdRequest(id)))
  }

  /** [userId] is the numeric Headscale user id: the API rejects user names here. */
  fun createPreAuthKey(
      userId: String,
      reusable: Boolean,
      ephemeral: Boolean,
      expirationRfc3339: String?,
  ): HsPreAuthKey? {
    val body =
        json.encodeToString(
            CreatePreAuthKeyRequest.serializer(),
            CreatePreAuthKeyRequest(userId, reusable, ephemeral, expirationRfc3339))
    return json.decodeFromString<PreAuthKeyResponse>(request("POST", "/api/v1/preauthkey", body))
        .preAuthKey
  }

  // ------------------------------------------------------------- parsing (unit-tested)

  internal fun parseNodes(body: String): List<HsNode> =
      json.decodeFromString<NodesResponse>(body).nodes

  internal fun parseUsers(body: String): List<HsUser> =
      json.decodeFromString<UsersResponse>(body).users

  internal fun parsePolicy(body: String): HsPolicy = json.decodeFromString<HsPolicy>(body)

  internal fun parsePreAuthKeys(body: String): List<HsPreAuthKey> =
      json.decodeFromString<PreAuthKeysResponse>(body).preAuthKeys

  // ------------------------------------------------------------- helpers (unit-tested)

  /**
   * Percent-encodes a path segment. [URLEncoder] is form-encoding, so it emits "+" for spaces,
   * which the server would read literally in a path; spaces must be "%20".
   */
  internal fun urlEncode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  /** Adds the required "tag:" prefix and drops duplicates. */
  internal fun normalizeTags(tags: List<String>): List<String> =
      tags.map { it.trim() }
          .filter { it.isNotEmpty() }
          .map { if (it.startsWith("tag:")) it else "tag:$it" }
          .distinct()

  /**
   * Accepts what users paste for the API base: the admin console URL, the API URL itself, or a
   * bare host. The API always lives at the server root.
   */
  internal fun normalizeBaseUrlInternal(url: String): String {
    var u = url.trim().trimEnd('/')
    if (u.isEmpty()) return u
    if (!u.contains("://")) u = "https://$u"
    for (suffix in listOf("/admin", "/api/v1", "/api")) {
      if (u.endsWith(suffix)) {
        u = u.removeSuffix(suffix).trimEnd('/')
        break
      }
    }
    return u
  }

  // ------------------------------------------------------------- plumbing

  /** Performs a request and returns the raw response body ("" for empty responses). */
  private fun request(method: String, path: String, body: String? = null): String {
    val base = baseUrl() ?: throw IOException("Admin API base URL is not configured")
    val key = apiKey() ?: throw IOException("Admin API key is not configured")
    return requestWith(method, path, body, base, key)
  }

  /** Transport, with the connection settings injected so it can be tested on the JVM. */
  internal fun requestWith(
      method: String,
      path: String,
      body: String?,
      base: String,
      key: String,
  ): String {

    val conn = URL("$base$path").openConnection() as HttpURLConnection
    try {
      conn.requestMethod = method
      conn.connectTimeout = 10_000
      conn.readTimeout = 20_000
      conn.setRequestProperty("Authorization", "Bearer $key")
      conn.setRequestProperty("Accept", "application/json")
      if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
      }

      val code = conn.responseCode
      if (code !in 200..299) {
        val message = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw IOException("$method $path failed: HTTP $code ${message.take(300)}")
      }

      val text = conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
      return text
    } finally {
      conn.disconnect()
    }
  }
}
