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

package arun.com.chromer.customtab

import android.net.Uri
import androidx.annotation.StringRes
import arun.com.chromer.R

/**
 * Lightweight, fully on-device URL safety check (v2.1.0). Flags the
 * classic look-alike / deception patterns: IP-literal hosts, punycode
 * (xn--) domain labels, and user info ("@") hiding the real host.
 * No network lookup is performed.
 */
object UrlSafety {

  /** String resources describing each concern found in [url]. */
  @StringRes
  fun warnings(url: String): List<Int> {
    val uri = try {
      Uri.parse(url)
    } catch (_: Exception) {
      return emptyList()
    }
    val result = mutableListOf<Int>()
    val host = uri.host?.lowercase().orEmpty()
    if (host.isNotBlank()) {
      if (isIpLiteral(host)) {
        result.add(R.string.safety_ip_host)
      }
      if (host.split('.').any { it.startsWith("xn--") }) {
        result.add(R.string.safety_punycode)
      }
    }
    if (!uri.userInfo.isNullOrBlank()) {
      result.add(R.string.safety_userinfo)
    }
    return result
  }

  private fun isIpLiteral(host: String): Boolean {
    if (host.contains(':')) return true // IPv6 literal
    val parts = host.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
      part.isNotEmpty() && part.all { it.isDigit() } &&
        (part.toIntOrNull() ?: 256) in 0..255
    }
  }
}
