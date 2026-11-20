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
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import arun.com.chromer.browser.AdBlocker
import arun.com.chromer.bubbles.BubbleService
import arun.com.chromer.customtab.LinkDispatcher
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.customtab.displayLabel
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.home.HomeActivity
import arun.com.chromer.link.LinkHandlerActivity
import arun.com.chromer.history.HistoryActivity
import arun.com.chromer.readlater.ReadLaterStore
import arun.com.chromer.settings.SettingsActivity
import arun.com.chromer.ui.glassViewColor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * The overlay browser: a floating window shown over the current app,
 * so the app underneath is never switched away from. One bar, at the
 * top or bottom, carries navigation, the ad-block status and the menu.
 *
 * v2.1.0: the window uses absolute coordinates — it can be dragged
 * freely by its title area, resized from a corner grip (any size /
 * aspect ratio), and its geometry is remembered. A button next to ✕
 * turns it into a bubble, and "use app underneath" makes the window
 * click-through so the app below stays visible AND usable while the
 * page stays on screen.
 */
class OverlayService : Service() {

  private var windowManager: WindowManager? = null
  private var rootView: FrameLayout? = null
  private var titleView: TextView? = null
  private var urlView: TextView? = null
  private var backButton: View? = null
  private var faviconView: ImageView? = null
  private var barTopRow: View? = null
  private var menuView: View? = null
  private var menuParams: WindowManager.LayoutParams? = null
  private var menuContent: LinearLayout? = null
  private var shieldView: TextView? = null
  private var shieldCountView: TextView? = null
  private var progressBar: ProgressBar? = null
  private var webView: WebView? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var handleView: TextView? = null
  private var handleParams: WindowManager.LayoutParams? = null
  private var catcherView: View? = null
  private var findBarView: View? = null
  private var findBarParams: WindowManager.LayoutParams? = null
  private var blurWasOn: Boolean = false

  private var fullHeight: Int = 0
  private var peekHeight: Int = 0
  private var isPeek: Boolean = false
  private var isMinimized: Boolean = false
  private var isPassthrough: Boolean = false
  private var secretSession: Boolean = false
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
        secretSession = intent.getBooleanExtra(EXTRA_SECRET, false)
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
      .setContentText(if (secretSession) getString(R.string.secret_browsing) else url)
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

  /** Whether the device is in dark mode (for glass tinting). */
  private fun isDarkMode(): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
  }

  /** The current glass surface color for the overlay windows. */
  private fun glassBarColor(): Int {
    return glassViewColor(isDarkMode(), LinkPreferences.isGlassEnabled(this))
  }

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
    isPassthrough = false
    desktopMode = LinkPreferences.isDesktopDefault(this)
    adBlockEnabled = LinkPreferences.isAdBlockEnabled(this)
    blockedCount.set(0)
    AdBlocker.ensureLoaded(this)

    val cornerRadius = dp(28).toFloat()
    val root = FrameLayout(this).apply {
      background = GradientDrawable().apply {
        setColor(glassBarColor())
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
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
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
      settings.textZoom = LinkPreferences.getTextZoom(this@OverlayService)
      if (Build.VERSION.SDK_INT >= 33) {
        settings.isAlgorithmicDarkeningAllowed =
          LinkPreferences.isForceDark(this@OverlayService)
      }
      if (desktopMode) {
        settings.userAgentString = DESKTOP_UA
        settings.useWideViewPort = true
      }
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
          if (!secretSession && finishedUrl != null &&
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


    val barOnTop = LinkPreferences.isBarOnTop(this)
    if (barOnTop) {
      column.addView(bar)
      column.addView(progress)
      column.addView(wv)
    } else {
      column.addView(wv)
      column.addView(progress)
      column.addView(bar)
    }
    root.addView(column)

    // Resize grip on the corner opposite the bar, so it never covers
    // the bar buttons. Dragging it resizes the window freely.
    // v2.3.0: the corner grip is a small gentle arc sitting on the
    // transparent rounded corner — visually outside the page frame.
    val grip = TextView(this).apply {
      text = "◢"
      setTextColor(0x99FFFFFF.toInt())
      textSize = 14f
      gravity = Gravity.CENTER
      layoutParams = FrameLayout.LayoutParams(dp(30), dp(30)).apply {
        gravity = (if (barOnTop) Gravity.BOTTOM else Gravity.TOP) or Gravity.END
      }
    }
    root.addView(grip)

    var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    var dim = 0f
    if (LinkPreferences.isOverlayBlurEnabled(this) && Build.VERSION.SDK_INT >= 31) {
      flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
      dim = 0.2f
    }

    // Initial geometry: the remembered one when it still fits,
    // otherwise the classic near-full-screen layout.
    var startX = marginX
    var startY = topSpace
    var startW = screenWidth - marginX * 2
    var startH = if (isPeek) peekHeight else fullHeight
    val saved = LinkPreferences.getOverlayGeometry(this)
    if (saved != null && saved[2] >= dp(200) && saved[3] >= dp(160)) {
      startW = saved[2].coerceAtMost(screenWidth)
      startH = saved[3].coerceAtMost(screenHeight)
      startX = saved[0].coerceIn(0, (screenWidth - startW).coerceAtLeast(0))
      startY = saved[1].coerceIn(0, (screenHeight - startH).coerceAtLeast(0))
      if (isPeek) {
        startH = peekHeight
      }
    }

    val params = WindowManager.LayoutParams(
      startW,
      startH,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      flags,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = startX
      y = startY
      if (Build.VERSION.SDK_INT >= 31) {
        blurBehindRadius = dp(36)
      }
      dimAmount = dim
    }

    // Dragging the title area moves the window freely (both axes).
    var initialX = 0
    var initialY = 0
    var initialRawX = 0f
    var initialRawY = 0f
    (barTopRow ?: titleView)?.setOnTouchListener { v, event ->
      val wm = windowManager ?: return@setOnTouchListener false
      val r = rootView ?: return@setOnTouchListener false
      val p = layoutParams ?: return@setOnTouchListener false
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          initialX = p.x
          initialY = p.y
          initialRawX = event.rawX
          initialRawY = event.rawY
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val maxX = (screenWidth - p.width).coerceAtLeast(0)
          val maxY = (screenHeight - p.height).coerceAtLeast(0)
          p.x = (initialX + (event.rawX - initialRawX).toInt()).coerceIn(0, maxX)
          p.y = (initialY + (event.rawY - initialRawY).toInt()).coerceIn(0, maxY)
          try {
            wm.updateViewLayout(r, p)
          } catch (_: Exception) {
          }
          true
        }

        MotionEvent.ACTION_UP -> {
          saveGeometry()
          v.performClick()
          true
        }

        else -> false
      }
    }

    // Dragging the grip resizes the window (free size / aspect).
    var gripStartW = 0
    var gripStartH = 0
    var gripRawX = 0f
    var gripRawY = 0f
    grip.setOnTouchListener { v, event ->
      val wm = windowManager ?: return@setOnTouchListener false
      val r = rootView ?: return@setOnTouchListener false
      val p = layoutParams ?: return@setOnTouchListener false
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          setBlurPaused(true)
          gripStartW = p.width
          gripStartH = p.height
          gripRawX = event.rawX
          gripRawY = event.rawY
          true
        }

        MotionEvent.ACTION_MOVE -> {
          if (isMinimized) {
            isMinimized = false
            webView?.visibility = View.VISIBLE
          }
          isPeek = false
          val dx = (event.rawX - gripRawX).toInt()
          // When the bar is at the bottom the grip sits at the top:
          // dragging up grows the window (its top edge follows).
          val dy = (event.rawY - gripRawY).toInt()
          p.width = (gripStartW + dx).coerceIn(dp(260), screenWidth)
          if (barOnTop) {
            p.height = (gripStartH + dy).coerceIn(dp(200), screenHeight - p.y)
          } else {
            val newH = (gripStartH - dy).coerceIn(dp(200), screenHeight)
            p.y = (p.y + (p.height - newH)).coerceAtLeast(0)
            p.height = newH
          }
          try {
            wm.updateViewLayout(r, p)
          } catch (_: Exception) {
          }
          true
        }

        MotionEvent.ACTION_UP -> {
          setBlurPaused(false)
          saveGeometry()
          v.performClick()
          true
        }

        else -> false
      }
    }

    // v2.2.0: PC-like free resizing from the window edges. Thin
    // invisible strips on the left, right and far edge (the edge
    // opposite the bar) resize the window; blur pauses while sizing.
    if (LinkPreferences.isOverlayFreeResize(this)) {
      val edgeThickness = dp(16)
      fun edgeGrip(
        layoutGravity: Int,
        width: Int,
        height: Int,
        onDrag: (dx: Int, dy: Int, p: WindowManager.LayoutParams) -> Unit
      ) {
        val strip = View(this).apply {
          layoutParams = FrameLayout.LayoutParams(width, height).apply {
            gravity = layoutGravity
            // Keep the side strips off the bar so its buttons (close
            // is at the very edge) stay fully tappable.
            if (height == ViewGroup.LayoutParams.MATCH_PARENT) {
              if (barOnTop) {
                topMargin = dp(106)
              } else {
                bottomMargin = dp(106)
              }
            }
          }
        }
        var startX = 0
        var startY = 0
        var startW = 0
        var startH = 0
        var startRawX = 0f
        var startRawY = 0f
        strip.setOnTouchListener { v, event ->
          val wm = windowManager ?: return@setOnTouchListener false
          val r = rootView ?: return@setOnTouchListener false
          val p = layoutParams ?: return@setOnTouchListener false
          when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
              setBlurPaused(true)
              startX = p.x
              startY = p.y
              startW = p.width
              startH = p.height
              startRawX = event.rawX
              startRawY = event.rawY
              true
            }

            MotionEvent.ACTION_MOVE -> {
              if (isMinimized) {
                isMinimized = false
                webView?.visibility = View.VISIBLE
              }
              isPeek = false
              // Re-base on the drag start so onDrag can be pure.
              p.x = startX
              p.y = startY
              p.width = startW
              p.height = startH
              onDrag(
                (event.rawX - startRawX).toInt(),
                (event.rawY - startRawY).toInt(),
                p
              )
              p.width = p.width.coerceIn(dp(260), screenWidth)
              p.height = p.height.coerceIn(dp(200), screenHeight)
              p.x = p.x.coerceIn(0, (screenWidth - p.width).coerceAtLeast(0))
              p.y = p.y.coerceIn(0, (screenHeight - p.height).coerceAtLeast(0))
              try {
                wm.updateViewLayout(r, p)
              } catch (_: Exception) {
              }
              true
            }

            MotionEvent.ACTION_UP -> {
              setBlurPaused(false)
              saveGeometry()
              v.performClick()
              true
            }

            else -> false
          }
        }
        root.addView(strip)
      }

      edgeGrip(
        Gravity.START or Gravity.CENTER_VERTICAL,
        edgeThickness,
        ViewGroup.LayoutParams.MATCH_PARENT
      ) { dx, _, p ->
        p.x += dx
        p.width -= dx
      }
      edgeGrip(
        Gravity.END or Gravity.CENTER_VERTICAL,
        edgeThickness,
        ViewGroup.LayoutParams.MATCH_PARENT
      ) { dx, _, p ->
        p.width += dx
      }
      if (barOnTop) {
        edgeGrip(
          Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
          ViewGroup.LayoutParams.MATCH_PARENT,
          edgeThickness
        ) { _, dy, p ->
          p.height += dy
        }
      } else {
        edgeGrip(
          Gravity.TOP or Gravity.CENTER_HORIZONTAL,
          ViewGroup.LayoutParams.MATCH_PARENT,
          edgeThickness
        ) { _, dy, p ->
          p.y += dy
          p.height -= dy
        }
      }
      // Keep the corner grip reachable above the edge strips.
      grip.bringToFront()
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

    // The surround catcher must be added BEFORE the overlay window so
    // it sits underneath it in z-order (v2.2.0).
    addCatcher()
    if (LinkPreferences.getAnimLevel(this) != LinkPreferences.ANIM_OFF) {
      params.windowAnimations = R.style.OverlayWindowAnim
    }
    windowManager?.addView(root, params)
    root.requestFocus()
    wv.loadUrl(initialUrl)
  }

  private fun saveGeometry() {
    if (!LinkPreferences.isOverlayRemember(this)) return
    val p = layoutParams ?: return
    if (isMinimized) return
    LinkPreferences.saveOverlayGeometry(this, p.x, p.y, p.width, p.height)
  }

  // ----- v2.2.0: blur is paused while resizing (cheaper + clearer) -----

  private fun setBlurPaused(paused: Boolean) {
    if (Build.VERSION.SDK_INT < 31) return
    val wm = windowManager ?: return
    val root = rootView ?: return
    val p = layoutParams ?: return
    val blurFlag = WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    if (paused) {
      blurWasOn = (p.flags and blurFlag) != 0
      if (blurWasOn) {
        p.flags = p.flags and blurFlag.inv()
        p.blurBehindRadius = 0
        p.dimAmount = 0f
        try {
          wm.updateViewLayout(root, p)
        } catch (_: Exception) {
        }
      }
    } else if (blurWasOn) {
      p.flags = p.flags or blurFlag
      p.blurBehindRadius = dp(36)
      p.dimAmount = 0.2f
      try {
        wm.updateViewLayout(root, p)
      } catch (_: Exception) {
      }
      blurWasOn = false
    }
  }

  // ----- v2.2.0: tapping the blurred surround enters passthrough -----

  private fun addCatcher() {
    if (catcherView != null) return
    if (!LinkPreferences.isPassthroughTapEnabled(this)) return
    val wm = windowManager ?: return
    val catcher = View(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_UP) {
          enterPassthrough()
        }
        true
      }
    }
    val cp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    )
    try {
      wm.addView(catcher, cp)
      catcherView = catcher
    } catch (_: Exception) {
    }
  }

  private fun setCatcherTouchable(touchable: Boolean) {
    val catcher = catcherView ?: return
    val wm = windowManager ?: return
    val cp = catcher.layoutParams as? WindowManager.LayoutParams ?: return
    val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val newFlags = if (touchable) {
      cp.flags and flag.inv()
    } else {
      cp.flags or flag
    }
    if (newFlags == cp.flags) return
    cp.flags = newFlags
    try {
      wm.updateViewLayout(catcher, cp)
    } catch (_: Exception) {
    }
  }

  private fun removeCatcher() {
    catcherView?.let { catcher ->
      try {
        windowManager?.removeView(catcher)
      } catch (_: Exception) {
      }
    }
    catcherView = null
  }

  // ----- v2.2.0: find-in-page bar -----

  @SuppressLint("ClickableViewAccessibility")
  private fun showFindBar() {
    if (findBarView != null) return
    val wm = windowManager ?: return
    val metrics = resources.displayMetrics
    val barLayout = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(10), dp(4), dp(4), dp(4))
      background = GradientDrawable().apply {
        setColor(glassBarColor())
        cornerRadius = dp(20).toFloat()
      }
    }
    val input = android.widget.EditText(this).apply {
      hint = getString(R.string.find_hint)
      setTextColor(Color.WHITE)
      setHintTextColor(0xFFBDC1C6.toInt())
      textSize = 14f
      isSingleLine = true
      layoutParams = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
      )
      addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
          val query = s?.toString().orEmpty()
          if (query.isEmpty()) {
            webView?.clearMatches()
          } else {
            webView?.findAllAsync(query)
          }
        }
      })
    }
    fun navButton(label: String, action: () -> Unit): TextView {
      return TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 17f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { action() }
      }
    }
    barLayout.addView(input)
    barLayout.addView(navButton("↑") { webView?.findNext(false) })
    barLayout.addView(navButton("↓") { webView?.findNext(true) })
    barLayout.addView(navButton("✕") { hideFindBar() })

    val fp = WindowManager.LayoutParams(
      metrics.widthPixels - dp(20),
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = dp(10)
      y = statusBarHeight() + dp(6)
    }
    try {
      wm.addView(barLayout, fp)
      findBarView = barLayout
      findBarParams = fp
      input.requestFocus()
      val imm = getSystemService(
        android.view.inputmethod.InputMethodManager::class.java
      )
      imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    } catch (_: Exception) {
    }
  }

  private fun hideFindBar() {
    findBarView?.let { bar ->
      try {
        windowManager?.removeView(bar)
      } catch (_: Exception) {
      }
    }
    findBarView = null
    findBarParams = null
    webView?.clearMatches()
    rootView?.requestFocus()
  }

  private fun buildBar(): LinearLayout {
    // v2.3.0: two rows. Top row: site icon + title + URL (dragging
    // it moves the window). Bottom row: seven evenly spaced drawn
    // icons. The whole bar is Liquid Glass.
    val bar = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(glassBarColor())
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }

    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(46)
      )
      setPadding(dp(12), 0, dp(12), 0)
    }
    val favicon = ImageView(this).apply {
      layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
      scaleType = ImageView.ScaleType.FIT_CENTER
    }
    val titleColumn = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      setPadding(dp(8), 0, 0, 0)
    }
    val title = TextView(this).apply {
      setTextColor(Color.WHITE)
      text = currentUrl
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      textSize = 13f
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
    topRow.addView(favicon)
    topRow.addView(titleColumn)
    if (!isDarkMode()) {
      title.setTextColor(0xFF202124.toInt())
      url.setTextColor(0xFF5F6368.toInt())
    }

    val iconRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(52)
      )
    }

    val close = createBarIconButton(R.drawable.ic_ov_close).apply {
      contentDescription = getString(R.string.act_close)
      setOnClickListener { closeOverlay() }
    }
    val toBubble = createBarIconButton(R.drawable.ic_ov_minimize).apply {
      contentDescription = getString(R.string.overlay_to_bubble)
      setOnClickListener { convertToBubble() }
    }
    val back = createBarIconButton(R.drawable.ic_ov_back).apply {
      isEnabled = false
      alpha = 0.4f
      contentDescription = getString(R.string.act_back)
      setOnClickListener {
        if (webView?.canGoBack() == true) {
          webView?.goBack()
        }
      }
    }

    // The AD chip keeps its cell like the other six items.
    val shieldCell = FrameLayout(this).apply {
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    }
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
      layoutParams = FrameLayout.LayoutParams(dp(38), dp(28)).apply {
        gravity = Gravity.CENTER
      }
      setOnClickListener {
        toggleAdBlock()
      }
    }
    val shieldCount = TextView(this).apply {
      setTextColor(0xFF8AB4F8.toInt())
      textSize = 10f
      visibility = View.GONE
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.TOP or Gravity.END
        marginEnd = dp(6)
        topMargin = dp(4)
      }
    }
    shieldCell.addView(shield)
    shieldCell.addView(shieldCount)

    val reload = createBarIconButton(R.drawable.ic_ov_reload).apply {
      contentDescription = getString(R.string.act_reload)
      setOnClickListener { webView?.reload() }
    }
    val expand = createBarIconButton(R.drawable.ic_ov_expand).apply {
      contentDescription = getString(R.string.overlay_expand)
      setOnClickListener { togglePeek() }
    }
    val menu = createBarIconButton(R.drawable.ic_ov_more).apply {
      contentDescription = getString(R.string.act_menu)
      setOnClickListener { showMenu(this) }
    }

    iconRow.addView(close)
    iconRow.addView(toBubble)
    iconRow.addView(back)
    iconRow.addView(shieldCell)
    iconRow.addView(reload)
    iconRow.addView(expand)
    iconRow.addView(menu)

    bar.addView(topRow)
    bar.addView(iconRow)

    titleView = title
    urlView = url
    backButton = back
    faviconView = favicon
    barTopRow = topRow
    shieldView = shield
    shieldCountView = shieldCount
    updateShield()
    return bar
  }

  /** One cell of the bar's icon row: a drawn vector icon, evenly sized. */
  private fun createBarIconButton(drawableRes: Int): ImageView {
    return ImageView(this).apply {
      setImageResource(drawableRes)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      setPadding(dp(11), dp(11), dp(11), dp(11))
      layoutParams = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.MATCH_PARENT,
        1f
      )
      if (!isDarkMode()) {
        setColorFilter(0xFF202124.toInt())
      }
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

  private fun convertToBubble() {
    val url = currentUrl
    val secret = secretSession
    BubbleService.open(this, url, secret)
    closeOverlay()
  }

  private fun togglePeek() {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val params = layoutParams ?: return
    val metrics = resources.displayMetrics
    if (isMinimized) {
      isMinimized = false
      webView?.visibility = View.VISIBLE
    }
    isPeek = !isPeek
    params.height = if (isPeek) peekHeight else fullHeight
    val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
    if (params.y > maxY) {
      params.y = maxY
    }
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
    }
    saveGeometry()
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

  private fun applyAspect(ratio: Float) {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val params = layoutParams ?: return
    val metrics = resources.displayMetrics
    if (isMinimized) {
      isMinimized = false
      webView?.visibility = View.VISIBLE
    }
    isPeek = false
    val newHeight = (params.width / ratio).toInt()
      .coerceIn(dp(200), metrics.heightPixels)
    params.height = newHeight
    val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
    if (params.y > maxY) {
      params.y = maxY
    }
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
    }
    saveGeometry()
  }

  private fun resetGeometry() {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val params = layoutParams ?: return
    val metrics = resources.displayMetrics
    LinkPreferences.clearOverlayGeometry(this)
    if (isMinimized) {
      isMinimized = false
      webView?.visibility = View.VISIBLE
    }
    isPeek = false
    params.x = dp(10)
    params.y = statusBarHeight() + dp(10)
    params.width = metrics.widthPixels - dp(20)
    params.height = fullHeight
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
    }
    saveGeometry()
  }

  // ----- "Use the app underneath": click-through while visible -----

  @SuppressLint("ClickableViewAccessibility")
  private fun enterPassthrough() {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val params = layoutParams ?: return
    if (isPassthrough) return
    isPassthrough = true
    setCatcherTouchable(false)
    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
    }

    val size = dp(48)
    val metrics = resources.displayMetrics
    val handle = TextView(this).apply {
      text = "↩"
      setTextColor(Color.WHITE)
      textSize = 20f
      gravity = Gravity.CENTER
      contentDescription = getString(R.string.passthrough_handle)
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(glassBarColor())
        setStroke(dp(1), 0x66FFFFFF)
      }
      elevation = dp(6).toFloat()
    }
    val hp = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = metrics.widthPixels - size - dp(10)
      y = metrics.heightPixels / 2
    }

    var downX = 0
    var downY = 0
    var rawDownX = 0f
    var rawDownY = 0f
    var moved = false
    handle.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downX = hp.x
          downY = hp.y
          rawDownX = event.rawX
          rawDownY = event.rawY
          moved = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - rawDownX).toInt()
          val dy = (event.rawY - rawDownY).toInt()
          if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
            moved = true
          }
          hp.x = (downX + dx).coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
          hp.y = (downY + dy).coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
          try {
            wm.updateViewLayout(handle, hp)
          } catch (_: Exception) {
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          if (!moved) {
            exitPassthrough()
          }
          v.performClick()
          true
        }
        else -> false
      }
    }

    handleView = handle
    handleParams = hp
    try {
      wm.addView(handle, hp)
    } catch (_: Exception) {
    }
    Toast.makeText(this, getString(R.string.overlay_passthrough_on), Toast.LENGTH_SHORT).show()
  }

  private fun exitPassthrough() {
    val wm = windowManager ?: return
    handleView?.let { handle ->
      try {
        wm.removeView(handle)
      } catch (_: Exception) {
      }
    }
    handleView = null
    handleParams = null
    if (!isPassthrough) return
    isPassthrough = false
    setCatcherTouchable(true)
    val root = rootView ?: return
    val params = layoutParams ?: return
    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    try {
      wm.updateViewLayout(root, params)
    } catch (_: Exception) {
    }
    root.requestFocus()
  }

  // ----- v2.3.0: the menu is a glass panel that pops out of the bar -----

  private fun showMenu(anchor: View) {
    hideMenu()
    val wm = windowManager ?: return
    val dark = isDarkMode()
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = GradientDrawable().apply {
        setColor(glassBarColor())
        cornerRadius = dp(22).toFloat()
        setStroke(dp(1), if (dark) 0x38FFFFFF else 0x22000000)
      }
      elevation = dp(10).toFloat()
      setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    menuContent = content
    showMenuPage(MenuPage.MAIN)

    val params = WindowManager.LayoutParams(
      dp(288),
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
      PixelFormat.TRANSLUCENT
    ).apply {
      val barOnTop = LinkPreferences.isBarOnTop(this@OverlayService)
      gravity = (if (barOnTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.END
      x = dp(8)
      y = if (barOnTop) {
        statusBarHeight() + dp(120)
      } else {
        dp(120)
      }
      if (LinkPreferences.getAnimLevel(this@OverlayService) != LinkPreferences.ANIM_OFF) {
        windowAnimations = R.style.OverlayMenuAnim
      }
    }
    val panel = FrameLayout(this).apply {
      addView(content)
      setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
          hideMenu()
          true
        } else {
          false
        }
      }
    }
    menuView = panel
    menuParams = params
    try {
      wm.addView(panel, params)
    } catch (_: Exception) {
      menuView = null
      menuParams = null
      menuContent = null
    }
  }

  private fun hideMenu() {
    val panel = menuView ?: return
    try {
      windowManager?.removeView(panel)
    } catch (_: Exception) {
    }
    menuView = null
    menuParams = null
    menuContent = null
  }

  private enum class MenuPage { MAIN, METHODS, ASPECT }

  private fun showMenuPage(page: MenuPage) {
    val content = menuContent ?: return
    content.removeAllViews()
    when (page) {
      MenuPage.MAIN -> buildMainMenuPage(content)
      MenuPage.METHODS -> buildMethodsMenuPage(content)
      MenuPage.ASPECT -> buildAspectMenuPage(content)
    }
  }

  private fun menuTextColor(): Int = if (isDarkMode()) Color.WHITE else 0xFF202124.toInt()

  private fun menuRow(
    iconRes: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
  ): View {
    val row = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(48)
      )
      setPadding(dp(10), 0, dp(10), 0)
      alpha = if (enabled) 1f else 0.4f
      if (enabled) {
        setOnClickListener { onClick() }
      }
    }
    val icon = ImageView(this).apply {
      setImageResource(iconRes)
      layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
      if (!isDarkMode()) {
        setColorFilter(0xFF202124.toInt())
      }
    }
    val text = TextView(this).apply {
      text = label
      setTextColor(menuTextColor())
      textSize = 14f
      isSingleLine = true
      ellipsize = TextUtils.TruncateAt.END
      layoutParams = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
      ).apply {
        marginStart = dp(12)
      }
    }
    row.addView(icon)
    row.addView(text)
    return row
  }

  private fun buildMainMenuPage(content: LinearLayout) {
    val checkSuffix = " ✓"
    content.addView(
      menuRow(R.drawable.ic_ov_forward, getString(R.string.act_forward),
        enabled = webView?.canGoForward() == true) {
        hideMenu()
        if (webView?.canGoForward() == true) webView?.goForward()
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_open_external, getString(R.string.overlay_open_primary)) {
        hideMenu()
        val url = currentUrl
        closeOverlay()
        try {
          startActivity(
            LinkHandlerActivity.createDispatchIntent(
              this,
              url,
              OpenMethod.CUSTOM_TAB_PRIMARY,
              secretSession
            )
          )
        } catch (_: Exception) {
        }
      }
    )
    content.addView(
      menuRow(
        R.drawable.ic_ov_desktop,
        getString(R.string.site_desktop) + if (desktopMode) checkSuffix else ""
      ) {
        desktopMode = !desktopMode
        LinkPreferences.setDesktopDefault(this, desktopMode)
        webView?.let { view ->
          view.settings.userAgentString = if (desktopMode) DESKTOP_UA else null
          view.settings.useWideViewPort = desktopMode
          view.reload()
        }
        showMenuPage(MenuPage.MAIN)
      }
    )
    content.addView(
      menuRow(
        R.drawable.ic_ov_shield,
        getString(R.string.adblock_title) + if (adBlockEnabled) checkSuffix else ""
      ) {
        toggleAdBlock()
        showMenuPage(MenuPage.MAIN)
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_passthrough, getString(R.string.overlay_passthrough)) {
        hideMenu()
        enterPassthrough()
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_other, getString(R.string.menu_other_methods)) {
        showMenuPage(MenuPage.METHODS)
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_aspect, getString(R.string.overlay_aspect)) {
        showMenuPage(MenuPage.ASPECT)
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_copy, getString(R.string.menu_copy_link)) {
        hideMenu()
        try {
          val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
          Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_find, getString(R.string.overlay_find)) {
        hideMenu()
        showFindBar()
      }
    )
    content.addView(
      menuRow(R.drawable.ic_ov_screenshot, getString(R.string.overlay_screenshot)) {
        hideMenu()
        webView?.let { view ->
          arun.com.chromer.browser.ScreenshotShare.captureAndShare(this, view)
        }
      }
    )

    // Bottom round icon row: share / bookmark / history / settings.
    val iconRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = dp(6)
      }
    }
    iconRow.addView(
      menuRoundButton(R.drawable.ic_ov_share_nodes, getString(R.string.overlay_share_row)) {
        hideMenu()
        LinkDispatcher.share(this, currentUrl)
      }
    )
    iconRow.addView(
      menuRoundButton(R.drawable.ic_ov_bookmark, getString(R.string.overlay_bookmark)) {
        ReadLaterStore.add(
          this,
          currentUrl,
          titleView?.text?.toString() ?: currentUrl
        )
        Toast.makeText(this, getString(R.string.read_later_added), Toast.LENGTH_SHORT).show()
        hideMenu()
      }
    )
    iconRow.addView(
      menuRoundButton(R.drawable.ic_ov_history, getString(R.string.overlay_history)) {
        hideMenu()
        try {
          startActivity(
            Intent(this, HistoryActivity::class.java)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        } catch (_: Exception) {
        }
      }
    )
    iconRow.addView(
      menuRoundButton(R.drawable.ic_ov_settings, getString(R.string.overlay_settings)) {
        hideMenu()
        try {
          startActivity(
            Intent(this, SettingsActivity::class.java)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        } catch (_: Exception) {
        }
      }
    )
    content.addView(iconRow)
  }

  private fun menuRoundButton(iconRes: Int, label: String, onClick: () -> Unit): View {
    val cell = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      setPadding(0, dp(4), 0, dp(2))
      setOnClickListener { onClick() }
    }
    val circle = FrameLayout(this).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (isDarkMode()) 0x26FFFFFF else 0x14000000)
        setStroke(dp(1), if (isDarkMode()) 0x30FFFFFF else 0x1A000000)
      }
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }
    val icon = ImageView(this).apply {
      setImageResource(iconRes)
      layoutParams = FrameLayout.LayoutParams(dp(21), dp(21)).apply {
        gravity = Gravity.CENTER
      }
      if (!isDarkMode()) {
        setColorFilter(0xFF202124.toInt())
      }
    }
    circle.addView(icon)
    val text = TextView(this).apply {
      text = label
      setTextColor(menuTextColor())
      textSize = 9f
      gravity = Gravity.CENTER
    }
    cell.addView(circle)
    cell.addView(text)
    return cell
  }

  private fun buildMethodsMenuPage(content: LinearLayout) {
    content.addView(
      menuRow(R.drawable.ic_ov_back, getString(R.string.act_back)) {
        showMenuPage(MenuPage.MAIN)
      }
    )
    val methods = LinkPreferences.getChooserMethods(this)
      .filter { it != OpenMethod.STAY_OVERLAY && it != OpenMethod.CANCEL }
    for (method in methods) {
      content.addView(
        menuRow(R.drawable.ic_ov_globe, method.displayLabel(this)) {
          hideMenu()
          val url = currentUrl
          if (method != OpenMethod.SHARE) {
            closeOverlay()
          }
          LinkDispatcher.open(this, url, method, secretSession)
        }
      )
    }
  }

  private fun buildAspectMenuPage(content: LinearLayout) {
    content.addView(
      menuRow(R.drawable.ic_ov_back, getString(R.string.act_back)) {
        showMenuPage(MenuPage.MAIN)
      }
    )
    for ((label, ratio) in ASPECT_RATIOS) {
      content.addView(
        menuRow(R.drawable.ic_ov_aspect, label) {
          hideMenu()
          applyAspect(ratio)
        }
      )
    }
    content.addView(
      menuRow(R.drawable.ic_ov_expand, getString(R.string.aspect_full)) {
        hideMenu()
        resetGeometry()
      }
    )
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
    hideMenu()
    removeCatcher()
    findBarView?.let { bar ->
      try {
        windowManager?.removeView(bar)
      } catch (_: Exception) {
      }
    }
    findBarView = null
    findBarParams = null
    handleView?.let { handle ->
      try {
        windowManager?.removeView(handle)
      } catch (_: Exception) {
      }
    }
    handleView = null
    handleParams = null
    isPassthrough = false
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
    barTopRow = null
    faviconView = null
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
    const val EXTRA_SECRET = "arun.com.chromer.overlay.EXTRA_SECRET"
    private const val ACTION_OPEN = "arun.com.chromer.overlay.ACTION_OPEN"
    private const val ACTION_CLOSE = "arun.com.chromer.overlay.ACTION_CLOSE"
    private const val CHANNEL_ID = "overlay_browsing"
    private const val NOTIFICATION_ID = 1001

    private const val BAR_COLOR = 0xFF202124.toInt()
    private const val DESKTOP_UA =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val ASPECT_RATIOS = listOf(
      "16:9" to (16f / 9f),
      "3:2" to (3f / 2f),
      "4:3" to (4f / 3f),
      "1:1" to 1f
    )

    private const val MENU_FORWARD = 1
    private const val MENU_OPEN_PRIMARY = 10
    private const val MENU_DESKTOP = 2
    private const val MENU_ADBLOCK = 3
    private const val MENU_PASSTHROUGH = 12
    private const val MENU_MINIMIZE = 4
    private const val MENU_FULLSCREEN = 5
    private const val MENU_OTHER_METHODS = 6
    private const val MENU_ASPECT = 13
    private const val MENU_COPY = 7
    private const val MENU_SHARE = 8
    private const val MENU_SETTINGS = 9
    private const val MENU_ASPECT_BASE = 200
    private const val MENU_ASPECT_FULL = 210
    private const val MENU_METHOD_BASE = 100
    private const val MENU_FIND = 14
    private const val MENU_READ_LATER = 15
    private const val MENU_SCREENSHOT = 16

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun createPermissionIntent(context: Context): Intent =
      Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))

    fun open(
      context: Context,
      url: String,
      peek: Boolean = false,
      secret: Boolean = false
    ): Boolean {
      if (!canDrawOverlays(context)) return false
      return try {
        context.startForegroundService(
          Intent(context, OverlayService::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_PEEK, peek)
            .putExtra(EXTRA_SECRET, secret)
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
