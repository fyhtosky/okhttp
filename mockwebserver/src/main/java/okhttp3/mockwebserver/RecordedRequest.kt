/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okhttp3.mockwebserver

import okhttp3.Handshake
import okhttp3.Handshake.Companion.handshake
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.TlsVersion
import okio.Buffer
import java.io.IOException
import java.net.Inet6Address
import java.net.Socket
import javax.net.ssl.SSLSocket

/** An HTTP request that came into the mock web server.  */
class RecordedRequest(
  val requestLine: String,
  /** All headers. */
  val headers: Headers,
  /**
   * The sizes of the chunks of this request's body, or an empty list if the request's body
   * was empty or unchunked.
   */
  val chunkSizes: List<Int>,
  /** The total size of the body of this POST request (before truncation).*/
  val bodySize: Long,
  /** The body of this POST request. This may be truncated. */
  val body: Buffer,
  /**
   * The index of this request on its HTTP connection. Since a single HTTP connection may
   * serve multiple requests, each request is assigned its own sequence number.
   */
  val sequenceNumber: Int,
  socket: Socket
) {
  val method: String?
  val path: String?
  /**
   * The TLS handshake of the connection that carried this request, or null if the request
   * was received without TLS.
   */
  val handshake: Handshake?
  val requestUrl: HttpUrl?

  val utf8Body: String
    @Deprecated("Use {@link #getBody() getBody().readUtf8()}. ")
    get() = body.readUtf8()

  /** Returns the connection's TLS version or null if the connection doesn't use SSL.  */
  val tlsVersion: TlsVersion?
    get() = handshake?.tlsVersion

  init {
    if (socket is SSLSocket) {
      try {
        this.handshake = socket.session.handshake()
      } catch (e: IOException) {
        throw IllegalArgumentException(e)
      }
    } else {
      this.handshake = null
    }

    if (requestLine.isNotEmpty()) {
      val methodEnd = requestLine.indexOf(' ')
      val pathEnd = requestLine.indexOf(' ', methodEnd + 1)
      this.method = requestLine.substring(0, methodEnd)
      var path = requestLine.substring(methodEnd + 1, pathEnd)
      if (!path.startsWith("/")) {
        path = "/"
      }
      this.path = path

      val scheme = if (socket is SSLSocket) "https" else "http"
      val inetAddress = socket.localAddress

      var hostname = inetAddress.hostName
      if (inetAddress is Inet6Address) {
        hostname = "[$hostname]"
      }

      val localPort = socket.localPort
      // Allow null in failure case to allow for testing bad requests
      this.requestUrl = "$scheme://$hostname:$localPort$path".toHttpUrlOrNull()
    } else {
      this.requestUrl = null
      this.method = null
      this.path = null
    }
  }

  /** Returns the first header named [name], or null if no such header exists.  */
  fun getHeader(name: String): String? {
    return headers.values(name).firstOrNull()
  }

  override fun toString(): String {
    return requestLine
  }
}
