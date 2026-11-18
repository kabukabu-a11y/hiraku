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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import arun.com.chromer.theme.LynketTheme

class BrowserActivity : ComponentActivity() {

  private var webView: WebView? = null

  private val backPressedCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      val currentWebView = webView
      if (currentWebView != null && currentWebView.canGoBack()) {
        currentWebView.goBack()
      } else {
        finish()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    onBackPressedDispatcher.addCallback(this, backPressedCallback)

    val rawUrl = extractUrlFromIntent(intent)
    val initialUrl = normalizeUrl(rawUrl.orEmpty())
    if (initialUrl.isEmpty()) {
      finish()
      return
    }

    setContent {
      LynketTheme {
        BrowserScreen(initialUrl = initialUrl)
      }
    }
  }

  override fun onDestroy() {
    webView?.let { wv ->
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.stopLoading()
      wv.destroy()
      webView = null
    }
    super.onDestroy()
  }

  private fun extractUrlFromIntent(intent: Intent?): String? {
    if (intent == null) return null
    val extraUrl = intent.getStringExtra(EXTRA_URL)
    if (!extraUrl.isNullOrBlank()) return extraUrl
    if (intent.action == Intent.ACTION_VIEW) {
      return intent.dataString
    }
    return intent.dataString
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @SuppressLint("SetJavaScriptEnabled")
  @Composable
  private fun BrowserScreen(initialUrl: String) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = pageTitle?.takeIf { it.isNotBlank() } ?: currentUrl,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          },
          navigationIcon = {
            IconButton(onClick = { finish() }) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close"
              )
            }
          },
          actions = {
            IconButton(
              onClick = {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                val chooser = Intent.createChooser(viewIntent, null)
                try {
                  startActivity(chooser)
                } catch (_: Exception) {
                }
              }
            ) {
              Icon(
                imageVector = OpenInNewIcon,
                contentDescription = "Open in external browser"
              )
            }
          }
        )
      },
      bottomBar = {
        BottomAppBar {
          IconButton(
            onClick = { webView?.goBack() },
            enabled = canGoBack
          ) {
            Icon(
              imageVector = Icons.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
          IconButton(
            onClick = { webView?.goForward() },
            enabled = canGoForward
          ) {
            Icon(
              imageVector = Icons.Filled.ArrowForward,
              contentDescription = "Forward"
            )
          }
          IconButton(onClick = { webView?.reload() }) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Reload"
            )
          }
        }
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        if (progress in 1..99) {
          LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier.fillMaxWidth()
          )
        }
        AndroidView(
          factory = { context ->
            WebView(context).apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.loadsImagesAutomatically = true
              webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: WebResourceRequest?
                ): Boolean {
                  val uri = request?.url ?: return false
                  val scheme = uri.scheme?.lowercase()
                  if (scheme == "http" || scheme == "https") {
                    return false
                  }
                  try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                  } catch (_: Exception) {
                  }
                  return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  if (!url.isNullOrBlank()) {
                    currentUrl = url
                  }
                  pageTitle = null
                  canGoBack = view?.canGoBack() == true
                  canGoForward = view?.canGoForward() == true
                }

                override fun doUpdateVisitedHistory(
                  view: WebView?,
                  url: String?,
                  isReload: Boolean
                ) {
                  if (!url.isNullOrBlank()) {
                    currentUrl = url
                  }
                  canGoBack = view?.canGoBack() == true
                  canGoForward = view?.canGoForward() == true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  if (!url.isNullOrBlank()) {
                    currentUrl = url
                  }
                  val title = view?.title
                  if (!title.isNullOrBlank()) {
                    pageTitle = title
                  }
                  canGoBack = view?.canGoBack() == true
                  canGoForward = view?.canGoForward() == true
                }
              }
              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                  pageTitle = title?.takeIf { it.isNotBlank() }
                }
              }
              this@BrowserActivity.webView = this
            }
          },
          update = { view ->
            this@BrowserActivity.webView = view
            if (view.tag != initialUrl && view.url != initialUrl) {
              view.tag = initialUrl
              view.loadUrl(initialUrl)
            }
          },
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        )
      }
    }
  }

  companion object {
    const val EXTRA_URL = "arun.com.chromer.extra.URL"

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")

    fun createIntent(context: Context, url: String): Intent {
      return Intent(context, BrowserActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
      }
    }

    internal fun normalizeUrl(rawUrl: String): String {
      val trimmed = rawUrl.trim()
      if (trimmed.isEmpty()) return ""
      return if (SCHEME_REGEX.matches(trimmed)) {
        trimmed
      } else {
        "https://$trimmed"
      }
    }
  }
}

private val OpenInNewIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "OpenInNew",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
  ).apply {
    path(fill = SolidColor(Color.Black)) {
      moveTo(19f, 19f)
      horizontalLineTo(5f)
      verticalLineTo(5f)
      horizontalLineTo(12f)
      verticalLineTo(3f)
      horizontalLineTo(5f)
      curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
      verticalLineTo(19f)
      curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
      horizontalLineTo(19f)
      curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
      verticalLineTo(12f)
      horizontalLineTo(19f)
      verticalLineTo(19f)
      close()
      moveTo(14f, 3f)
      verticalLineTo(5f)
      horizontalLineTo(17.59f)
      lineTo(7.76f, 14.83f)
      lineTo(9.17f, 16.24f)
      lineTo(19f, 6.41f)
      verticalLineTo(10f)
      horizontalLineTo(21f)
      verticalLineTo(3f)
      horizontalLineTo(14f)
      close()
    }
  }.build()
}
