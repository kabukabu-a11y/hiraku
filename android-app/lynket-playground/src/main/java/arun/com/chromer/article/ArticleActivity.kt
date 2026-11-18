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

package arun.com.chromer.article

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import arun.com.chromer.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.theme.LynketTheme
import org.json.JSONObject
import org.json.JSONTokener

class ArticleActivity : ComponentActivity() {
  companion object {
    const val EXTRA_URL = "arun.com.chromer.article.EXTRA_URL"

    fun createIntent(context: Context, url: String): Intent =
      Intent(context, ArticleActivity::class.java).putExtra(EXTRA_URL, url)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: run {
      finish()
      return
    }
    setContent {
      LynketTheme {
        ArticleScreen(url = url, onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleScreen(url: String, onBack: () -> Unit) {
  val context = LocalContext.current
  val defaultTitle = stringResource(R.string.article_title)
  var title by remember { mutableStateOf(defaultTitle) }
  var loading by remember { mutableStateOf(true) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        },
        actions = {
          TextButton(onClick = {
            context.startActivity(BrowserActivity.createDirectIntent(context, url))
          }) {
            Text(stringResource(R.string.article_original))
          }
        }
      )
    }
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      AndroidView(
        factory = { ctx ->
          createArticleWebView(
            ctx,
            url,
            onTitle = { title = it },
            onLoading = { loading = it },
            onExtractFailed = {
              Toast.makeText(ctx, ctx.getString(R.string.article_failed), Toast.LENGTH_SHORT).show()
            }
          )
        },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createArticleWebView(
  context: Context,
  url: String,
  onTitle: (String) -> Unit,
  onLoading: (Boolean) -> Unit,
  onExtractFailed: () -> Unit
): WebView {
  var extracted = false
  return WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onLoading(true)
      }

      override fun onPageFinished(view: WebView?, finishedUrl: String?) {
        if (extracted) {
          onLoading(false)
          return
        }
        extracted = true
        view?.evaluateJavascript(EXTRACTION_JS) { result ->
          val articleHtml = parseExtractionResult(result)
          if (articleHtml != null) {
            val (pageTitle, bodyHtml) = articleHtml
            if (pageTitle.isNotBlank()) onTitle(pageTitle)
            view.loadDataWithBaseURL(url, wrapReaderHtml(bodyHtml), "text/html", "UTF-8", null)
          } else {
            onExtractFailed()
          }
          onLoading(false)
        }
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val target = request?.url ?: return false
        return if (target.scheme == "http" || target.scheme == "https") {
          false
        } else {
          try {
            context.startActivity(Intent(Intent.ACTION_VIEW, target))
          } catch (_: Exception) {
          }
          true
        }
      }
    }
    loadUrl(url)
  }
}

private fun parseExtractionResult(result: String?): Pair<String, String>? {
  if (result == null) return null
  val decoded = try {
    JSONTokener(result).nextValue() as? String
  } catch (_: Exception) {
    null
  } ?: return null
  val obj = try {
    JSONObject(decoded)
  } catch (_: Exception) {
    return null
  }
  if (!obj.optBoolean("ok")) return null
  return Pair(obj.optString("title", ""), obj.optString("html", "")).takeIf {
    it.second.isNotBlank()
  }
}

private fun wrapReaderHtml(bodyHtml: String): String {
  return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
body { font-family: sans-serif; line-height: 1.8; padding: 16px; margin: 0 auto; max-width: 720px; word-wrap: break-word; }
img { max-width: 100%; height: auto; }
a { color: #1a73e8; }
pre, code { white-space: pre-wrap; }
@media (prefers-color-scheme: dark) {
  body { background: #121212; color: #e8e8e8; }
  a { color: #8ab4f8; }
}
</style>
</head>
<body>
$bodyHtml
</body>
</html>"""
}

private const val EXTRACTION_JS = """(function() {
  var candidates = [];
  var article = document.querySelector('article');
  if (article) { candidates.push(article); }
  var main = document.querySelector('main');
  if (main) { candidates.push(main); }
  var divs = document.querySelectorAll('div, section');
  for (var i = 0; i < divs.length; i++) { candidates.push(divs[i]); }
  var best = null;
  var bestScore = 0;
  for (var j = 0; j < candidates.length; j++) {
    var el = candidates[j];
    var ps = el.querySelectorAll('p');
    var score = 0;
    for (var k = 0; k < ps.length; k++) { score += (ps[k].innerText || '').length; }
    if (score > bestScore) { bestScore = score; best = el; }
  }
  if (!best || bestScore < 200) { return JSON.stringify({ ok: false }); }
  var clone = best.cloneNode(true);
  var junk = clone.querySelectorAll('script, style, nav, aside, footer, form, iframe, noscript, button, svg');
  for (var m = 0; m < junk.length; m++) { junk[m].remove(); }
  var title = document.title || '';
  var html = '<h1>' + title + '</h1>' + clone.innerHTML;
  return JSON.stringify({ ok: true, title: title, html: html });
})()"""

