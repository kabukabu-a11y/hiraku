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
import arun.com.chromer.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import arun.com.chromer.browser.AdBlocker
import arun.com.chromer.customtab.LinkDispatcher
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.home.HomeActivity
import arun.com.chromer.link.LinkHandlerActivity
import arun.com.chromer.settings.SettingsActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * The overlay browser: a near-full-screen floating window (device
 * aspect ratio with small margins and rounded corners) shown over the
 * current app, so the app underneath is never switched away from.
 * One bar, at the top or bottom, carries navigation, the ad-block
 * status and the menu.
 */
class OverlayService : Service() {

  private var windowManager: WindowManager? = null
  private var rootView: LinearLayout? = null
  private var titleView: TextView? = null
  private var urlView: TextView? = null
  private var backButton: TextView? = null
  private var shieldView: TextView? = null
  private var shieldCountView: TextView? = null
  private var progressBar: ProgressBar? = null
  private var webView: WebView? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private var fullHeight: Int = 0
  private var peekHeight: Int = 0
  private var isPeek: Boolean = false
  private var isMinimized: Boolean = false
  private var currentUrl: String = ""
  private var desktopMode: Boolean = false
  private var adBlockEnabled: Boolean = true
  // Incremented from WebView request threads; UI updates go to main.
  private val blockedCount = AtomicInteger(0)
  private val mainHandler = Handler(Looper.getMainLooper())

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WindowManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.overlay_channel),
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
          createAndShowOverlay(url, intent.getBooleanExtra(EXTRA_PEEK, false))
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
      .setContentTitle(getString(R.string.overlay_showing))
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
  private fun createAndShowOverlay(initialUrl: String, startInPeek: Boolean) {
    val metrics = resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels
    val marginX = dp(10)
    val marginBottom = dp(14)
    val topSpace = statusBarHeight() + dp(10)
    fullHeight = screenHeight - topSpace - marginBottom
    peekHeight = (screenHeight * 0.42f).toInt()
    isPeek = startInPeek
    isMinimized = false
    desktopMode = false
    adBlockEnabled = LinkPreferences.isAdBlockEnabled(this)
    blockedCount.set(0)
    AdBlocker.ensureLoaded(this)

    val cornerRadius = dp(28).toFloat()
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = GradientDrawable().apply {
        setColor(BAR_COLOR)
        cornerRadii = floatArrayOf(
          cornerRadius, cornerRadius,
          cornerRadius, cornerRadius,
          cornerRadius, cornerRadius,
          cornerRadius, cornerRadius
        )
      }
      outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
          outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
        }
      }
      clipToOutline = true
      isFocusableInTouchMode = true
    }

    val bar = buildBar()
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
        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?
        ): WebResourceResponse? {
          val url = request?.url?.toString()
          if (adBlockEnabled && AdBlocker.isAdUrl(url)) {
            // This callback runs on a background thread: never touch
            // views here, post the badge update to the main thread.
            blockedCount.incrementAndGet()
            mainHandler.post { updateShield() }
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
            urlView?.text = url
          }
          blockedCount.set(0)
          updateShield()
          updateNavigationButtons(view)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          if (!url.isNullOrBlank()) {
            currentUrl = url
            urlView?.text = url
          }
          val displayTitle = view?.title?.takeIf { it.isNotBlank() } ?: url.orEmpty()
          if (displayTitle.isNotEmpty()) {
            titleView?.text = displayTitle
          }
          updateNavigationButtons(view)
          val finishedUrl = view?.url ?: url
          if (finishedUrl != null &&
            (finishedUrl.startsWith("http://") || finishedUrl.startsWith("https://"))
          ) {
            HistoryStore.record(
              this@OverlayService,
              finishedUrl,
              view?.title ?: finishedUrl
            )
          }
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

    if (LinkPreferences.isBarOnTop(this)) {
      root.addView(bar)
      root.addView(progress)
      root.addView(wv)
    } else {
      root.addView(wv)
      root.addView(progress)
      root.addView(bar)
    }

    var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    var dim = 0f
    if (LinkPreferences.isOverlayBlurEnabled(this) && Build.VERSION.SDK_INT >= 31) {
      flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
      dim = 0.2f
    }
    val params = WindowManager.LayoutParams(
      screenWidth - marginX * 2,
      if (isPeek) peekHeight else fullHeight,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      flags,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      x = 0
      y = marginBottom
      if (Build.VERSION.SDK_INT >= 31) {
        blurBehindRadius = dp(36)
      }
      dimAmount = dim
    }

    // Dragging the title area moves the window vertically.
    var initialY = 0
    var initialRawY = 0f
    titleView?.setOnTouchListener { v, event ->
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
          val maxY = (screenHeight - p.height).coerceAtLeast(0)
          p.y = (initialY - (event.rawY - initialRawY).toInt()).coerceIn(0, maxY)
          try {
            wm.updateViewLayout(r, p)
          } catch (_: Exception) {
          }
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
    progressBar = progress
    webView = wv
    layoutParams = params

    windowManager?.addView(root, params)
    root.requestFocus()
    wv.loadUrl(initialUrl)
  }

  private fun buildBar(): LinearLayout {
    val bar = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setBackgroundColor(BAR_COLOR)
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(64)
      )
      setPadding(dp(4), 0, dp(4), 0)
    }

    val close = createBarButton("✕").apply {
      setOnClickListener { closeOverlay() }
    }

    val back = createBarButton("◀").apply {
      isEnabled = false
      alpha = 0.4f
      setOnClickListener {
        if (webView?.canGoBack() == true) {
          webView?.goBack()
        }
      }
    }

    val titleColumn = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      setPadding(dp(6), 0, dp(6), 0)
    }
    val title = TextView(this).apply {
      setTextColor(Color.WHITE)
      text = currentUrl
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      textSize = 14f
    }
    val url = TextView(this).apply {
      setTextColor(0xFFBDC1C6.toInt())
      text = currentUrl
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      textSize = 10f
    }
    titleColumn.addView(title)
    titleColumn.addView(url)

    val shield = TextView(this).apply {
      text = "AD"
      setTextColor(Color.WHITE)
      textSize = 12f
      gravity = Gravity.CENTER
      background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(8).toFloat()
        setColor(0xFF5F6368.toInt())
      }
      layoutParams = LinearLayout.LayoutParams(dp(38), dp(28)).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
      }
      setOnClickListener {
        toggleAdBlock()
      }
    }
    val shieldCount = TextView(this).apply {
      setTextColor(0xFF8AB4F8.toInt())
      textSize = 11f
      visibility = View.GONE
    }

    val reload = createBarButton("⟳").apply {
      setOnClickListener { webView?.reload() }
    }

    val expand = createBarButton("⤢").apply {
      setOnClickListener { togglePeek() }
    }

    val menu = createBarButton("︙").apply {
      setOnClickListener { showMenu(this) }
    }

    bar.addView(close)
    bar.addView(back)
    bar.addView(titleColumn)
    bar.addView(shield)
    bar.addView(shieldCount)
    bar.addView(reload)
    bar.addView(expand)
    bar.addView(menu)

    titleView = title
    urlView = url
    backButton = back
    shieldView = shield
    shieldCountView = shieldCount
    updateShield()
    return bar
  }

  private fun createBarButton(label: String): TextView {
    return TextView(this).apply {
      text = label
      setTextColor(Color.WHITE)
      textSize = 19f
      gravity = Gravity.CENTER
      layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT)
    }
  }

  private fun updateShield() {
    val chip = shieldView ?: return
    val drawable = chip.background as? GradientDrawable
    drawable?.setColor(if (adBlockEnabled) 0xFF8AB4F8.toInt() else 0xFF5F6368.toInt())
    chip.setTextColor(if (adBlockEnabled) 0xFF202124.toInt() else Color.WHITE)
    val count = shieldCountView ?: return
    val blocked = blockedCount.get()
    if (blocked > 0) {
      count.text = blocked.toString()
      count.visibility = View.VISIBLE
    } else {
      count.visibility = View.GONE
    }
  }

  private fun toggleAdBlock() {
    adBlockEnabled = !adBlockEnabled
    LinkPreferences.setAdBlockEnabled(this, adBlockEnabled)
    updateShield()
    Toast.makeText(
      this,
      if (adBlockEnabled) getString(R.string.adblock_on) else getString(R.string.adblock_off),
      Toast.LENGTH_SHORT
    ).show()
    webView?.reload()
  }

  private fun togglePeek() {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val params = layoutParams ?: return
    if (isMinimized) {
      isMinimized = false
      webView?.visibility = View.VISIBLE
    }
    isPeek = !isPeek
    params.height = if (isPeek) peekHeight else fullHeight
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
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
      params.height = if (isPeek) peekHeight else fullHeight
    }
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
    }
  }

  private fun showMenu(anchor: View) {
    val popup = PopupMenu(this, anchor)
    val menu = popup.menu
    val forwardItem = menu.add(0, MENU_FORWARD, 0, getString(R.string.act_forward))
    forwardItem.isEnabled = webView?.canGoForward() == true
    menu.add(0, MENU_OPEN_PRIMARY, 1, getString(R.string.overlay_open_primary))
    menu.add(
      0,
      MENU_DESKTOP,
      2,
      if (desktopMode) "✓ " + getString(R.string.site_desktop) else getString(R.string.site_desktop)
    )
    menu.add(
      0,
      MENU_ADBLOCK,
      3,
      if (adBlockEnabled) "✓ " + getString(R.string.adblock_title) else getString(R.string.adblock_title)
    )
    menu.add(0, MENU_MINIMIZE, 4, getString(R.string.overlay_minimize))
    menu.add(0, MENU_FULLSCREEN, 5, getString(R.string.overlay_open_in_app))
    val sub = menu.addSubMenu(0, MENU_OTHER_METHODS, 6, getString(R.string.menu_other_methods))
    // Same customized list as the chooser screens, minus the methods
    // that make no sense from inside the overlay itself.
    val methods = LinkPreferences.getChooserMethods(this)
      .filter { it != OpenMethod.STAY_OVERLAY && it != OpenMethod.CANCEL }
    for ((index, method) in methods.withIndex()) {
      sub.add(1, MENU_METHOD_BASE + index, index, getString(method.labelRes))
    }
    menu.add(0, MENU_COPY, 7, getString(R.string.menu_copy_link))
    menu.add(0, MENU_SHARE, 8, getString(R.string.act_share))
    menu.add(0, MENU_SETTINGS, 9, getString(R.string.overlay_settings))

    popup.setOnMenuItemClickListener { item: MenuItem ->
      when {
        item.itemId >= MENU_METHOD_BASE -> {
          val method = methods.getOrNull(item.itemId - MENU_METHOD_BASE)
          if (method != null) {
            val url = currentUrl
            if (method != OpenMethod.SHARE) {
              closeOverlay()
            }
            LinkDispatcher.open(this, url, method)
          }
          true
        }
        item.itemId == MENU_FORWARD -> {
          if (webView?.canGoForward() == true) webView?.goForward()
          true
        }
        item.itemId == MENU_OPEN_PRIMARY -> {
          // Route through the handler activity so the chosen browser
          // opens as a real Custom Tab (needs an activity context).
          val url = currentUrl
          closeOverlay()
          try {
            startActivity(
              LinkHandlerActivity.createDispatchIntent(
                this,
                url,
                OpenMethod.CUSTOM_TAB_PRIMARY
              )
            )
          } catch (_: Exception) {
          }
          true
        }
        item.itemId == MENU_DESKTOP -> {
          desktopMode = !desktopMode
          webView?.let { view ->
            view.settings.userAgentString = if (desktopMode) DESKTOP_UA else null
            view.settings.useWideViewPort = desktopMode
            view.reload()
          }
          true
        }
        item.itemId == MENU_ADBLOCK -> {
          toggleAdBlock()
          true
        }
        item.itemId == MENU_MINIMIZE -> {
          toggleMinimize()
          true
        }
        item.itemId == MENU_FULLSCREEN -> {
          val url = currentUrl
          closeOverlay()
          LinkDispatcher.openInApp(this, url)
          true
        }
        item.itemId == MENU_COPY -> {
          try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
            Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
          } catch (_: Exception) {
          }
          true
        }
        item.itemId == MENU_SHARE -> {
          LinkDispatcher.share(this, currentUrl)
          true
        }
        item.itemId == MENU_SETTINGS -> {
          try {
            startActivity(
              Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
          } catch (_: Exception) {
          }
          true
        }
        else -> false
      }
    }
    popup.show()
  }

  private fun updateNavigationButtons(view: WebView?) {
    val canBack = view?.canGoBack() == true
    backButton?.apply {
      isEnabled = canBack
      alpha = if (canBack) 1.0f else 0.4f
    }
  }

  private fun statusBarHeight(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
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
    urlView = null
    backButton = null
    shieldView = null
    shieldCountView = null
    progressBar = null
    webView = null
    layoutParams = null
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  companion object {
    const val EXTRA_URL = "arun.com.chromer.overlay.EXTRA_URL"
    const val EXTRA_PEEK = "arun.com.chromer.overlay.EXTRA_PEEK"
    private const val ACTION_OPEN = "arun.com.chromer.overlay.ACTION_OPEN"
    private const val ACTION_CLOSE = "arun.com.chromer.overlay.ACTION_CLOSE"
    private const val CHANNEL_ID = "overlay_browsing"
    private const val NOTIFICATION_ID = 1001

    private const val BAR_COLOR = 0xFF202124.toInt()
    private const val DESKTOP_UA =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private const val MENU_FORWARD = 1
    private const val MENU_OPEN_PRIMARY = 10
    private const val MENU_DESKTOP = 2
    private const val MENU_ADBLOCK = 3
    private const val MENU_MINIMIZE = 4
    private const val MENU_FULLSCREEN = 5
    private const val MENU_OTHER_METHODS = 6
    private const val MENU_COPY = 7
    private const val MENU_SHARE = 8
    private const val MENU_SETTINGS = 9
    private const val MENU_METHOD_BASE = 100

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun createPermissionIntent(context: Context): Intent =
      Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))

    fun open(context: Context, url: String, peek: Boolean = false): Boolean {
      if (!canDrawOverlays(context)) return false
      return try {
        context.startForegroundService(
          Intent(context, OverlayService::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_PEEK, peek)
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
