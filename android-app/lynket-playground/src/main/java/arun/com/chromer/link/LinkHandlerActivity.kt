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

package arun.com.chromer.link

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.customtab.LinkDispatcher
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.theme.LynketTheme
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point for links from other apps (the default-browser target).
 * Resolves the open method from the per-app rule / default, dispatches,
 * and finishes, so Back from wherever the link opened returns to the
 * app the user came from. When the method is ASK, shows the chooser
 * sheet with a page preview instead.
 */
class LinkHandlerActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val url = extractUrl(intent)
    if (url.isNullOrBlank()) {
      finish()
      return
    }
    val normalized = BrowserActivity.normalizeUrl(url)
    if (normalized.isEmpty()) {
      finish()
      return
    }

    val forced = OpenMethod.fromName(intent.getStringExtra(EXTRA_METHOD))
    val method = forced ?: LinkPreferences.resolveMethod(this, referrerPackage())

    if (method == OpenMethod.ASK && forced == null) {
      setContent {
        LynketTheme {
          ChooserScreen(url = normalized)
        }
      }
      return
    }

    if (method != OpenMethod.CANCEL) {
      HistoryStore.record(this, normalized, normalized)
    }
    LinkDispatcher.open(this, normalized, method)
    finish()
  }

  private fun extractUrl(intent: Intent?): String? {
    if (intent == null) return null
    val extra = intent.getStringExtra(EXTRA_URL)
    if (!extra.isNullOrBlank()) return extra
    return intent.dataString
  }

  private fun referrerPackage(): String? {
    return try {
      val ref = referrer
      if (ref != null && ref.scheme == "android-app") ref.host else null
    } catch (_: Exception) {
      null
    }
  }

  @Composable
  private fun ChooserScreen(url: String) {
    val context = LocalContext.current
    val previewEnabled = remember { LinkPreferences.isPreviewEnabled(context) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var favicon by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
      if (previewEnabled) {
        val host = try {
          Uri.parse(url).host
        } catch (_: Exception) {
          null
        }
        if (!host.isNullOrBlank()) {
          favicon = loadFavicon(host)
        }
        pageTitle = fetchPageTitle(url)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0x99000000))
        .clickable { finish() },
      contentAlignment = Alignment.BottomCenter
    ) {
      Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clickable(enabled = false) { }
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = stringResource(R.string.chooser_title),
            style = MaterialTheme.typography.titleMedium
          )
          if (previewEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (favicon != null) {
                Image(
                  bitmap = favicon!!.asImageBitmap(),
                  contentDescription = null,
                  modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = pageTitle?.takeIf { it.isNotBlank() } ?: url,
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = url,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          } else {
            Text(
              text = url,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          for (method in LinkPreferences.getChooserMethods(context)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (method != OpenMethod.CANCEL) {
                    HistoryStore.record(context, url, pageTitle ?: url)
                  }
                  LinkDispatcher.open(this@LinkHandlerActivity, url, method)
                  finish()
                }
                .padding(vertical = 12.dp)
            ) {
              Text(
                text = stringResource(method.labelRes),
                style = MaterialTheme.typography.bodyLarge
              )
            }
          }
        }
      }
    }
  }

  companion object {
    const val EXTRA_URL = "arun.com.chromer.link.EXTRA_URL"
    const val EXTRA_METHOD = "arun.com.chromer.link.EXTRA_METHOD"

    fun createDispatchIntent(context: Context, url: String, method: OpenMethod): Intent {
      return Intent(context, LinkHandlerActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
        putExtra(EXTRA_METHOD, method.name)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }

    /** Opens [url] using the resolved default / per-app method. */
    fun createOpenIntent(context: Context, url: String): Intent {
      return Intent(context, LinkHandlerActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }
}

private suspend fun loadFavicon(host: String): Bitmap? =
  withContext(Dispatchers.IO) {
    try {
      val connection = URL(
        "https://www.google.com/s2/favicons?domain=$host&sz=128"
      ).openConnection() as HttpURLConnection
      connection.connectTimeout = 2500
      connection.readTimeout = 2500
      connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
      null
    }
  }

private suspend fun fetchPageTitle(url: String): String? =
  withContext(Dispatchers.IO) {
    try {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.connectTimeout = 2500
      connection.readTimeout = 2500
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
      val text = connection.inputStream.bufferedReader().use { reader ->
        val buffer = CharArray(16384)
        val read = reader.read(buffer)
        if (read > 0) String(buffer, 0, read) else ""
      }
      connection.disconnect()
      val match = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
        .find(text)
      match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    }
  }
