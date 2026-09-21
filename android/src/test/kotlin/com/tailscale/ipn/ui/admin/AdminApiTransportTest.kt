// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.admin

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [AdminApi]'s HTTP transport against a real socket: request shape, bearer token, JSON
 * bodies and how server errors reach the user. Hand-rolled HTTP because the Android unit-test
 * classpath has no server API.
 */
class AdminApiTransportTest {
  private data class Recorded(
      val method: String,
      val path: String,
      val authorization: String?,
      val contentType: String?,
      val body: String,
  )

  /** Minimal HTTP/1.1 responder that records what it received. */
  private class TestServer : Closeable {
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val requests: MutableList<Recorded> = Collections.synchronizedList(mutableListOf())
    val port: Int get() = socket.localPort

    @Volatile var responseCode = 200
    @Volatile var responseBody = "{}"

    private val worker = thread(isDaemon = true, name = "admin-api-test-server") {
      while (!socket.isClosed) {
        try {
          socket.accept().use { handle(it) }
        } catch (_: IOException) {
          return@thread
        }
      }
    }

    private fun handle(client: Socket) {
      client.soTimeout = 5_000
      val input = client.getInputStream()
      val output = client.getOutputStream()

      val requestLine = readLine(input) ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      var contentLength = 0
      var authorization: String? = null
      var contentType: String? = null
      while (true) {
        val header = readLine(input) ?: return
        if (header.isEmpty()) break
        val name = header.substringBefore(":").trim()
        val value = header.substringAfter(":", "").trim()
        when {
          name.equals("Content-Length", ignoreCase = true) -> contentLength = value.toIntOrNull() ?: 0
          name.equals("Authorization", ignoreCase = true) -> authorization = value
          name.equals("Content-Type", ignoreCase = true) -> contentType = value
        }
      }
      val body = if (contentLength > 0) input.readBytes(contentLength) else ByteArray(0)

      requests +=
          Recorded(
              method = parts[0],
              path = parts[1],
              authorization = authorization,
              contentType = contentType,
              body = String(body, StandardCharsets.UTF_8))

      val payload = responseBody.toByteArray(StandardCharsets.UTF_8)
      val head =
          "HTTP/1.1 $responseCode X\r\n" +
              "Content-Type: application/json\r\n" +
              "Content-Length: ${payload.size}\r\n" +
              "Connection: close\r\n\r\n"
      output.write(head.toByteArray(StandardCharsets.ISO_8859_1))
      output.write(payload)
      output.flush()
    }

    /** Reads a CRLF-terminated header line as bytes, so bodies stay binary-safe. */
    private fun readLine(input: InputStream): String? {
      val line = StringBuilder()
      while (true) {
        val next = input.read()
        if (next == -1) return if (line.isEmpty()) null else line.toString()
        if (next == '\n'.code) return line.toString().trimEnd('\r')
        line.append(next.toChar())
      }
    }

    private fun InputStream.readBytes(count: Int): ByteArray {
      val buffer = ByteArray(count)
      var read = 0
      while (read < count) {
        val step = read(buffer, read, count - read)
        if (step < 0) break
        read += step
      }
      return buffer.copyOf(read)
    }

    override fun close() {
      socket.close()
    }
  }

  private lateinit var server: TestServer

  @Before
  fun startServer() {
    server = TestServer()
  }

  @After
  fun stopServer() {
    server.close()
  }

  private fun base() = "http://127.0.0.1:${server.port}"

  @Test
  fun sendsBearerTokenAndParsesResponse() {
    server.responseBody = """{"nodes":[{"id":"2","givenName":"chen-2"}]}"""

    val body = AdminApi.requestWith("GET", "/api/v1/node", null, base(), "test-key")

    assertEquals("2", AdminApi.parseNodes(body).single().id)
    val request = server.requests.single()
    assertEquals("GET", request.method)
    assertEquals("/api/v1/node", request.path)
    assertEquals("Bearer test-key", request.authorization)
  }

  @Test
  fun reportsHttpErrorsWithServerMessage() {
    server.responseCode = 401
    server.responseBody = """{"code":16,"message":"invalid API key"}"""

    val error =
        assertThrows(IOException::class.java) {
          AdminApi.requestWith("GET", "/api/v1/not-there", null, base(), "bad-key")
        }
    assertTrue(error.message!!.contains("HTTP 401"))
    assertTrue(error.message!!.contains("invalid API key"))
  }

  @Test
  fun postsJsonBodyWithContentType() {
    AdminApi.requestWith("POST", "/api/v1/preauthkey", """{"user":"1"}""", base(), "k")

    val request = server.requests.single()
    assertEquals("POST", request.method)
    assertEquals("""{"user":"1"}""", request.body)
    assertEquals("application/json", request.contentType)
  }

  @Test
  fun deletesWithoutBody() {
    AdminApi.requestWith("DELETE", "/api/v1/node/2", null, base(), "k")

    val request = server.requests.single()
    assertEquals("DELETE", request.method)
    assertEquals("/api/v1/node/2", request.path)
    assertEquals("", request.body)
  }
}
