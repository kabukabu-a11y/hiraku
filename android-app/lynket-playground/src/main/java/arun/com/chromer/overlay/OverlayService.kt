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

package arun.com.chromer.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import arun.com.chromer.home.HomeActivity

class OverlayService : Service() {

  private var windowManager: WindowManager? = null
  private var rootView: LinearLayout? = null
  private var titleView: TextView? = null
  private var backButton: TextView? = null
  private var forwardButton: TextView? = null
  private var progressBar: ProgressBar? = null
  private var webView: WebView? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private var expandedHeight: Int = 0
  private var isMinimized: Boolean = false
  private var currentUrl: String = ""

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WindowManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      "オーバーレイ表示",
      NotificationManager.IMPORTANCE_LOW
    )
    getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_OPEN -> {
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
          stopSelf()
          return START_NOT_STICKY
        }
        currentUrl = url
        startForegroundWithNotification(url)
        if (rootView != null) {
          webView?.loadUrl(url)
        } else {
          createAndShowOverlay(url)
        }
      }

      ACTION_CLOSE -> {
        closeOverlay()
      }

      else -> {
        if (rootView == null) {
          stopSelf()
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    cleanup()
    super.onDestroy()
  }

  private fun startForegroundWithNotification(url: String) {
    val contentIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, HomeActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE
    )
    val notification = Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle("オーバーレイ表示中")
      .setContentText(url)
      .setOngoing(true)
      .setContentIntent(contentIntent)
      .build()
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
  private fun createAndShowOverlay(initialUrl: String) {
    val metrics = resources.displayMetrics
    val displayWidth = metrics.widthPixels
    expandedHeight = (metrics.heightPixels * 0.6f).toInt()
    isMinimized = false

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      isFocusableInTouchMode = true
    }

    val header = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setBackgroundColor(0xFF202124.toInt())
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(48)
      )
    }

    val title = TextView(this).apply {
      setTextColor(Color.WHITE)
      text = initialUrl
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(8), 0)
      layoutParams = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.MATCH_PARENT,
        1f
      )
    }

    val back = createHeaderButton("◀").apply {
      isEnabled = false
      alpha = 0.4f
      setOnClickListener {
        if (webView?.canGoBack() == true) {
          webView?.goBack()
        }
      }
    }

    val forward = createHeaderButton("▶").apply {
      isEnabled = false
      alpha = 0.4f
      setOnClickListener {
        if (webView?.canGoForward() == true) {
          webView?.goForward()
        }
      }
    }

    val reload = createHeaderButton("⟳").apply {
      setOnClickListener {
        webView?.reload()
      }
    }

    val minimize = createHeaderButton("▁").apply {
      setOnClickListener {
        toggleMinimize()
      }
    }

    val close = createHeaderButton("✕").apply {
      setOnClickListener {
        closeOverlay()
      }
    }

    header.addView(title)
    header.addView(back)
    header.addView(forward)
    header.addView(reload)
    header.addView(minimize)
    header.addView(close)

    val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 100
      visibility = View.GONE
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }

    val wv = WebView(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f
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
            startActivity(
              Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
          } catch (_: Exception) {
          }
          return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
          if (!url.isNullOrBlank()) {
            currentUrl = url
          }
          val displayTitle = view?.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
          if (displayTitle.isNotEmpty()) {
            titleView?.text = displayTitle
          }
          updateNavigationButtons(view)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          if (!url.isNullOrBlank()) {
            currentUrl = url
          }
          val displayTitle = view?.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
          if (displayTitle.isNotEmpty()) {
            titleView?.text = displayTitle
          }
          updateNavigationButtons(view)
        }
      }
      webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
          progressBar?.progress = newProgress
          progressBar?.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
          if (!title.isNullOrBlank()) {
            titleView?.text = title
          }
        }
      }
    }

    root.addView(header)
    root.addView(progress)
    root.addView(wv)

    val params = WindowManager.LayoutParams(
      displayWidth,
      expandedHeight,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM
      x = 0
      y = 0
    }

    var initialY = 0
    var initialRawY = 0f
    title.setOnTouchListener { v, event ->
      val wm = windowManager ?: return@setOnTouchListener false
      val r = rootView ?: return@setOnTouchListener false
      val p = layoutParams ?: return@setOnTouchListener false
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          initialY = p.y
          initialRawY = event.rawY
          true
        }

        MotionEvent.ACTION_MOVE -> {
          p.y = initialY - (event.rawY - initialRawY).toInt()
          wm.updateViewLayout(r, p)
          true
        }

        MotionEvent.ACTION_UP -> {
          v.performClick()
          true
        }

        else -> false
      }
    }

    root.setOnKeyListener { _, keyCode, event ->
      if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
        val currentWebView = webView
        if (currentWebView != null && currentWebView.canGoBack()) {
          currentWebView.goBack()
        } else {
          closeOverlay()
        }
        true
      } else {
        false
      }
    }

    rootView = root
    titleView = title
    backButton = back
    forwardButton = forward
    progressBar = progress
    webView = wv
    layoutParams = params

    windowManager?.addView(root, params)
    root.requestFocus()
    wv.loadUrl(initialUrl)
  }

  private fun createHeaderButton(label: String): TextView {
    return TextView(this).apply {
      text = label
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      setPadding(dp(12), 0, dp(12), 0)
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }
  }

  private fun updateNavigationButtons(view: WebView?) {
    val canBack = view?.canGoBack() == true
    val canForward = view?.canGoForward() == true
    backButton?.apply {
      isEnabled = canBack
      alpha = if (canBack) 1.0f else 0.4f
    }
    forwardButton?.apply {
      isEnabled = canForward
      alpha = if (canForward) 1.0f else 0.4f
    }
  }

  private fun toggleMinimize() {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val params = layoutParams ?: return
    val wv = webView ?: return

    isMinimized = !isMinimized
    if (isMinimized) {
      wv.visibility = View.GONE
      params.height = WindowManager.LayoutParams.WRAP_CONTENT
    } else {
      wv.visibility = View.VISIBLE
      params.height = expandedHeight
    }
    wm.updateViewLayout(root, params)
  }

  private fun closeOverlay() {
    cleanup()
    stopSelf()
  }

  private fun cleanup() {
    rootView?.let { root ->
      try {
        windowManager?.removeView(root)
      } catch (_: Exception) {
      }
    }
    webView?.let { wv ->
      wv.stopLoading()
      wv.destroy()
    }
    rootView = null
    titleView = null
    backButton = null
    forwardButton = null
    progressBar = null
    webView = null
    layoutParams = null
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  companion object {
    const val EXTRA_URL = "arun.com.chromer.overlay.EXTRA_URL"
    private const val ACTION_OPEN = "arun.com.chromer.overlay.ACTION_OPEN"
    private const val ACTION_CLOSE = "arun.com.chromer.overlay.ACTION_CLOSE"
    private const val CHANNEL_ID = "overlay_browsing"
    private const val NOTIFICATION_ID = 1001

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun createPermissionIntent(context: Context): Intent =
      Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))

    fun open(context: Context, url: String): Boolean {
      if (!canDrawOverlays(context)) return false
      return try {
        context.startForegroundService(
          Intent(context, OverlayService::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_URL, url)
        )
        true
      } catch (_: Exception) {
        false
      }
    }

    fun close(context: Context) {
      try {
        context.startService(
          Intent(context, OverlayService::class.java)
            .setAction(ACTION_CLOSE)
        )
      } catch (_: Exception) {
      }
    }
  }
}

