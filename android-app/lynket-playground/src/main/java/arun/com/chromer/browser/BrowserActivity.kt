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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.net.URLEncoder
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import arun.com.chromer.article.ArticleActivity
import arun.com.chromer.customtab.LinkDispatcher
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.overlay.OverlayService
import arun.com.chromer.tabs.TabStore
import arun.com.chromer.theme.LynketTheme

/**
 * The full-screen in-app browser. Link routing lives in
 * LinkHandlerActivity; this activity only displays pages. It runs in
 * its own task so Back at the root returns to the previous app.
 */
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

    val initialUrl = normalizeUrl(extractUrlFromIntent(intent).orEmpty())
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
    return intent.dataString
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @SuppressLint("SetJavaScriptEnabled")
  @Composable
  private fun BrowserScreen(initialUrl: String) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var pageIcon by remember { mutableStateOf<Bitmap?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(true) }
    var desktopMode by remember { mutableStateOf(false) }
    var adBlockEnabled by remember { mutableStateOf(LinkPreferences.isAdBlockEnabled(context)) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var chooserVisible by remember { mutableStateOf(false) }
    val hideBarEnabled = remember { LinkPreferences.getHideBarOnScroll(context) }
    val barOnTop = remember { LinkPreferences.isBarOnTop(context) }
    val previewEnabled = remember { LinkPreferences.isPreviewEnabled(context) }
    val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    val adBlockOnText = stringResource(R.string.adblock_on)
    val adBlockOffText = stringResource(R.string.adblock_off)

    val barContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        IconButton(onClick = { finish() }) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.act_close)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = pageTitle?.takeIf { it.isNotBlank() } ?: currentUrl,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = currentUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(
          onClick = {
            val newValue = !adBlockEnabled
            adBlockEnabled = newValue
            LinkPreferences.setAdBlockEnabled(context, newValue)
            Toast.makeText(
              context,
              if (newValue) adBlockOnText else adBlockOffText,
              Toast.LENGTH_SHORT
            ).show()
            webView?.reload()
          }
        ) {
          Icon(
            imageVector = ShieldIcon,
            contentDescription = stringResource(R.string.adblock_title),
            tint = if (adBlockEnabled) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        }
        if (blockedCount > 0) {
          Text(
            text = blockedCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )
        }
        IconButton(
          onClick = { webView?.goBack() },
          enabled = canGoBack
        ) {
          Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = stringResource(R.string.act_back)
          )
        }
        IconButton(
          onClick = { webView?.goForward() },
          enabled = canGoForward
        ) {
          Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = stringResource(R.string.act_forward)
          )
        }
        IconButton(onClick = { webView?.reload() }) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Reload"
          )
        }
        Box {
          IconButton(onClick = { menuExpanded = true }) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = "Menu"
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text(if (adBlockEnabled) stringResource(R.string.adblock_on) else stringResource(R.string.adblock_off)) },
              onClick = {
                menuExpanded = false
                val newValue = !adBlockEnabled
                adBlockEnabled = newValue
                LinkPreferences.setAdBlockEnabled(context, newValue)
                webView?.reload()
              }
            )
            DropdownMenuItem(
              text = { Text(if (desktopMode) stringResource(R.string.site_mobile) else stringResource(R.string.site_desktop)) },
              onClick = {
                menuExpanded = false
                val newDesktopMode = !desktopMode
                desktopMode = newDesktopMode
                webView?.let { view ->
                  view.settings.userAgentString = if (newDesktopMode) desktopUserAgent else null
                  view.settings.useWideViewPort = newDesktopMode
                  view.reload()
                }
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.article_title)) },
              onClick = {
                menuExpanded = false
                val target = webView?.url ?: initialUrl
                context.startActivity(ArticleActivity.createIntent(context, target))
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_overlay)) },
              onClick = {
                menuExpanded = false
                LinkDispatcher.openOverlay(context, currentUrl)
                finish()
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_other_methods)) },
              onClick = {
                menuExpanded = false
                chooserVisible = true
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.act_share)) },
              onClick = {
                menuExpanded = false
                LinkDispatcher.share(context, currentUrl)
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.menu_copy_link)) },
              onClick = {
                menuExpanded = false
                try {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                  Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                }
              }
            )
          }
        }
    }

    if (chooserVisible) {
      AlertDialog(
        onDismissRequest = { chooserVisible = false },
        title = { Text(stringResource(R.string.menu_other_methods)) },
        text = {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
          ) {
            if (previewEnabled) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (pageIcon != null) {
                  Image(
                    bitmap = pageIcon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = pageTitle?.takeIf { it.isNotBlank() } ?: currentUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = currentUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
            }
            for (method in LinkPreferences.getChooserMethods(context)) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    chooserVisible = false
                    if (method != OpenMethod.CANCEL) {
                      LinkDispatcher.open(this@BrowserActivity, currentUrl, method)
                      finish()
                    }
                  }
                  .padding(vertical = 10.dp)
              ) {
                Text(
                  text = stringResource(method.labelRes),
                  style = MaterialTheme.typography.bodyLarge
                )
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { chooserVisible = false }) {
            Text(stringResource(R.string.act_close))
          }
        }
      )
    }

    Scaffold(
      topBar = {
        if (barOnTop) {
          AnimatedVisibility(
            visible = barVisible || !hideBarEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
          ) {
            androidx.compose.material3.Surface(
              color = MaterialTheme.colorScheme.surface,
              tonalElevation = 3.dp
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                barContent()
              }
            }
          }
        }
      },
      bottomBar = {
        if (!barOnTop) {
          AnimatedVisibility(
            visible = barVisible || !hideBarEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
          ) {
            BottomAppBar { barContent() }
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
          factory = { factoryContext ->
            WebView(factoryContext).apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.loadsImagesAutomatically = true
              AdBlocker.ensureLoaded(factoryContext)
              webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                  view: WebView?,
                  request: WebResourceRequest?
                ): WebResourceResponse? {
                  val url = request?.url?.toString()
                  if (adBlockEnabled && AdBlocker.isAdUrl(url)) {
                    blockedCount++
                    return AdBlocker.emptyResponse()
                  }
                  return super.shouldInterceptRequest(view, request)
                }

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
                  blockedCount = 0
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
                  val finishedUrl = view?.url ?: url
                  if (finishedUrl != null && (finishedUrl.startsWith("http://") || finishedUrl.startsWith("https://"))) {
                    val finishedTitle = view?.title ?: finishedUrl
                    HistoryStore.record(context, finishedUrl, finishedTitle)
                    TabStore.upsert(context, finishedUrl, finishedTitle)
                  }
                }
              }
              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  progress = newProgress
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                  pageTitle = title?.takeIf { it.isNotBlank() }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                  if (icon != null) {
                    pageIcon = icon
                  }
                }
              }
              setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (hideBarEnabled) {
                  if (scrollY > oldScrollY + 6 && scrollY > 100) {
                    barVisible = false
                  } else if (scrollY < oldScrollY - 6) {
                    barVisible = true
                  }
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
    const val EXTRA_FORCE_IN_APP = "arun.com.chromer.extra.FORCE_IN_APP"

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")

    fun createIntent(context: Context, url: String): Intent {
      return Intent(context, BrowserActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
      }
    }

    fun createDirectIntent(context: Context, url: String): Intent =
      createIntent(context, url).apply { putExtra(EXTRA_FORCE_IN_APP, true) }

    fun normalizeUrl(rawUrl: String): String {
      val trimmed = rawUrl.trim()
      if (trimmed.isEmpty()) return ""
      if (SCHEME_REGEX.matches(trimmed)) return trimmed
      val looksLikeHost = !trimmed.contains(" ") && trimmed.contains(".")
      return if (looksLikeHost) {
        "https://$trimmed"
      } else {
        "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
      }
    }
  }
}

private val ShieldIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "Shield",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
  ).apply {
    path(fill = SolidColor(Color.Black)) {
      moveTo(12f, 1f)
      lineTo(3f, 5f)
      verticalLineTo(11f)
      curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
      curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
      verticalLineTo(5f)
      lineTo(12f, 1f)
      close()
    }
  }.build()
}
