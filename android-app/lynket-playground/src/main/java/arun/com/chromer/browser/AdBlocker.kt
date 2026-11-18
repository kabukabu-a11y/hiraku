/*
 *
 *  Lynket
 *
 *  Copyright (C) 2023 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Domain-based ad blocking for the in-app WebViews. The bundled list
 * (assets/ad_domains.txt) is built from the pgl.yoyo.org ad server
 * list, a long-running open source blocklist, plus major ad networks.
 * Requests to a listed domain (or any subdomain of one) are answered
 * with an empty response.
 */
object AdBlocker {

  private const val ASSET_NAME = "ad_domains.txt"

  @Volatile
  private var domains: Set<String>? = null

  fun ensureLoaded(context: Context) {
    if (domains != null) return
    synchronized(this) {
      if (domains != null) return
      domains = try {
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
          lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toHashSet()
        }
      } catch (_: Exception) {
        emptySet()
      }
    }
  }

  fun isAdHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val set = domains ?: return false
    if (set.isEmpty()) return false
    var candidate = host.lowercase()
    while (true) {
      if (set.contains(candidate)) return true
      val dot = candidate.indexOf('.')
      if (dot < 0) return false
      candidate = candidate.substring(dot + 1)
      // Stop before treating a bare TLD as a candidate.
      if (!candidate.contains('.')) return set.contains(candidate)
    }
  }

  fun isAdUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return try {
      isAdHost(Uri.parse(url).host)
    } catch (_: Exception) {
      false
    }
  }

  fun emptyResponse(): WebResourceResponse {
    return WebResourceResponse(
      "text/plain",
      "utf-8",
      ByteArrayInputStream(ByteArray(0))
    )
  }
}
