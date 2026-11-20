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

import android.animation.ValueAnimator
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
import android.graphics.RectF
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
import arun.com.chromer.ui.IosSpringInterpolator
import kotlin.math.hypot
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
  private var barView: View? = null
  // The bar's corner buttons, kept as fields so the bar-corner
  // resize grips can forward taps to them (item 91).
  private var barCloseButton: View? = null
  private var barMenuButton: View? = null
  private var menuView: View? = null
  private var menuParams: WindowManager.LayoutParams? = null
  private var menuContent: LinearLayout? = null
  private var menuCatcherView: View? = null
  private var menuAnchorView: View? = null
  private var menuAboveAnchor: Boolean = true
  private var menuPivotX: Float = 0f
  private var menuPivotY: Float = 0f
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
  // Backdrop-blur state (v2.3.3 items 81/86/87): whether the
  // settings want the blur on this window, whether it is
  // currently applied, and how many pause holds (geometry
  // drags, passthrough) are outstanding. The blur is applied
  // only when it is desired and no hold is outstanding.
  private var blurDesired: Boolean = false
  private var blurApplied: Boolean = false
  private var blurPauseHolds: Int = 0
  private var windowAnimator: ValueAnimator? = null

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
          createAndShowOverlay(
            url,
            intent.getBooleanExtra(EXTRA_PEEK, false),
            intent.getIntArrayExtra(EXTRA_FROM_RECT)
          )
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
    return glassViewColor(
      isDarkMode(),
      LinkPreferences.isGlassEnabled(this),
      LinkPreferences.getGlassStrength(this)
    )
  }

  /**
   * Whether bar/menu content should use the light-on-dark
   * palette. The glass tint is the same in light and dark mode
   * now (v2.3.1 item 67), so with glass on the content is always
   * the light palette; with glass off it follows the theme.
   */
  private fun onDarkSurface(): Boolean {
    return LinkPreferences.isGlassEnabled(this) || isDarkMode()
  }

  private fun createAndShowOverlay(
    initialUrl: String,
    startInPeek: Boolean,
    fromRect: IntArray? = null
  ) {
    val metrics = resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels
    val marginX = dp(10)
    // v2.3.3 (item 80): the default bottom margin IS the
    // gesture exclusion. v2.3.2 excluded the gesture strip only
    // from the catcher windows; the main window's default
    // geometry kept a 14dp bottom margin and its move/resize
    // clamps let its bottom edge reach the very bottom of the
    // screen, so a touchable Hiraku window still sat inside the
    // strip the home swipe starts in. The rule is now uniform:
    // no long-lived touchable window enters the strip.
    val marginBottom = bottomGestureExclusionPx()
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
    barView = bar
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

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
          if (icon != null) {
            faviconView?.setImageBitmap(icon)
          }
        }
      }
    }

    wv.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
      if (!downloadUrl.isNullOrBlank()) {
        LinkDispatcher.handleDownload(
          this@OverlayService,
          downloadUrl,
          userAgent,
          contentDisposition,
          mimeType
        )
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
    // v2.3.2 (item 76): the arc is drawn with the window's own
    // corner radius (28dp) centered on the real corner, so the
    // stroke traces the frame instead of floating inside a
    // smaller box the way the v2.3.1 grip did.
    // v2.3.3 (item 81): the box grows from 48dp to 64dp. The
    // arc's RectF spans the full 2r (56dp), which overflowed
    // the 48dp box, and the stroke (2dp over a 3.4dp underlay,
    // round caps) was clipped at the box edges, leaving the
    // arc's ends looking cut off. In a 64dp box the whole
    // RectF plus stroke fits inside, still anchored to the
    // real corner, so the arc traces the window's corner
    // circle exactly.
    val grip = ArcGripView(
      this,
      flipVertical = barOnTop,
      flipHorizontal = false,
      cornerRadiusPx = cornerRadius
    ).apply {
      layoutParams = FrameLayout.LayoutParams(dp(64), dp(64)).apply {
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
    // The blur state machine (items 81/87) starts from the
    // settings-derived state of the freshly built window.
    blurDesired = (flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
    blurApplied = blurDesired
    blurPauseHolds = 0

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
    // A remembered geometry predates the gesture-strip rule
    // (and peek reuses the remembered Y): clamp the initial
    // shape, whatever its source, so the window's bottom edge
    // starts above the strip (item 80).
    val maxBottomEdge = screenHeight - bottomGestureExclusionPx()
    if (startY + startH > maxBottomEdge) {
      startY = (maxBottomEdge - startH).coerceAtLeast(0)
      if (startY + startH > maxBottomEdge) {
        startH = (maxBottomEdge - startY).coerceAtLeast(dp(160))
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
        // Backdrop blur follows the glass strength (item 65).
        blurBehindRadius = dp(
          LinkPreferences.glassBlurRadiusDp(
            LinkPreferences.getGlassStrength(this@OverlayService)
          )
        )
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
          setBlurPaused(true)
          initialX = p.x
          initialY = p.y
          initialRawX = event.rawX
          initialRawY = event.rawY
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val maxX = (screenWidth - p.width).coerceAtLeast(0)
          // The bottom edge stays above the gesture strip
          // (item 80).
          val maxY = (screenHeight - bottomGestureExclusionPx() - p.height)
            .coerceAtLeast(0)
          p.x = (initialX + (event.rawX - initialRawX).toInt()).coerceIn(0, maxX)
          p.y = (initialY + (event.rawY - initialRawY).toInt()).coerceIn(0, maxY)
          try {
            wm.updateViewLayout(r, p)
          } catch (_: Exception) {
          }
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          setBlurPaused(false)
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
            // The grip is on the bottom edge here: growing the
            // height moves the bottom edge, which must stay
            // above the gesture strip (item 80).
            p.height = (gripStartH + dy).coerceIn(
              dp(200),
              (screenHeight - bottomGestureExclusionPx() - p.y)
                .coerceAtLeast(dp(200))
            )
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

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          setBlurPaused(false)
          saveGeometry()
          v.performClick()
          true
        }

        else -> false
      }
    }

    // v2.3.3 (item 86, ColorOS 17 mini-window style): grips on
    // BOTH corners of the bar's own edge, so the window resizes
    // from the bar side too. Up to v2.3.2 the only resize
    // handles were the far corner grip and the far edge strip —
    // with the bar at the bottom, resizing was only possible
    // from the top. A bar-side grip keeps the far edge fixed
    // and moves the bar edge (and its own side edge) with the
    // finger. The grips are small (32dp) and hug the window's
    // corners, clear of the bar buttons' centers, so the bar's
    // taps keep working as before outside them. Like the far
    // grip, they are always available, whether or not the
    // free-resize (edge strips) setting is on.
    val barGrips = mutableListOf<ArcGripView>()
    fun setupBarGrip(gripView: View, startSide: Boolean) {
      var startX = 0
      var startY = 0
      var startW = 0
      var startH = 0
      var startRawX = 0f
      var startRawY = 0f
      var gripDragging = false
      gripView.setOnTouchListener { v, event ->
        val wm = windowManager ?: return@setOnTouchListener false
        val r = rootView ?: return@setOnTouchListener false
        val p = layoutParams ?: return@setOnTouchListener false
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            // v2.3.5 (items 89/91): the grip overlaps the bar's
            // corner button (close at START, menu at END), and
            // the two need opposite treatment of the same touch.
            // v2.3.3 let the whole 32dp grip consume, which
            // killed the buttons at phone sizes; v2.3.4's fix —
            // only an outermost-14dp corner sliver counts as a
            // grip touch — made the grip itself nearly
            // ungrabbable, so resizing died instead (a rejected
            // DOWN loses the whole gesture). Now the grip takes
            // every DOWN and decides by movement: a drag past
            // the slop becomes a resize; a tap (up within the
            // slop) is forwarded to the button underneath.
            gripDragging = false
            startX = p.x
            startY = p.y
            startW = p.width
            startH = p.height
            startRawX = event.rawX
            startRawY = event.rawY
            true
          }

          MotionEvent.ACTION_MOVE -> {
            if (!gripDragging) {
              val moved = maxOf(
                kotlin.math.abs(event.rawX - startRawX),
                kotlin.math.abs(event.rawY - startRawY)
              )
              if (moved < dp(9)) {
                // Not a drag yet — could still become a tap.
                return@setOnTouchListener true
              }
              gripDragging = true
              setBlurPaused(true)
            }
            if (isMinimized) {
              isMinimized = false
              webView?.visibility = View.VISIBLE
            }
            isPeek = false
            val dx = (event.rawX - startRawX).toInt()
            val dy = (event.rawY - startRawY).toInt()
            val maxBottom = screenHeight - bottomGestureExclusionPx()
            var newW = if (startSide) startW - dx else startW + dx
            newW = newW.coerceIn(dp(260), screenWidth)
            var newH = if (barOnTop) startH - dy else startH + dy
            newH = newH.coerceIn(dp(200), maxBottom.coerceAtLeast(dp(200)))
            p.width = newW
            p.height = newH
            // The far edge stays fixed: a START grip moves the
            // left edge (the right edge is derived from the
            // clamped width), an END grip keeps the left edge.
            p.x = if (startSide) {
              (startX + startW - newW)
                .coerceIn(0, (screenWidth - newW).coerceAtLeast(0))
            } else {
              startX.coerceIn(0, (screenWidth - newW).coerceAtLeast(0))
            }
            // Same for the vertical axis: with the bar on top
            // the bottom edge is fixed and the top edge moves;
            // with the bar at the bottom the top edge is fixed.
            // Either way the bottom edge respects the gesture
            // strip (item 80).
            p.y = if (barOnTop) {
              (startY + startH - newH)
                .coerceIn(0, (maxBottom - newH).coerceAtLeast(0))
            } else {
              startY.coerceIn(0, (maxBottom - newH).coerceAtLeast(0))
            }
            try {
              wm.updateViewLayout(r, p)
            } catch (_: Exception) {
            }
            true
          }

          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (gripDragging) {
              setBlurPaused(false)
              saveGeometry()
            } else if (event.actionMasked == MotionEvent.ACTION_UP) {
              // A tap that started on the grip belongs to the
              // bar's corner button underneath it (item 91).
              if (startSide) {
                barCloseButton?.performClick()
              } else {
                barMenuButton?.performClick()
              }
            }
            gripDragging = false
            v.performClick()
            true
          }

          else -> false
        }
      }
    }
    for (startSide in listOf(true, false)) {
      val barGrip = ArcGripView(
        this,
        flipVertical = !barOnTop,
        flipHorizontal = startSide,
        cornerRadiusPx = cornerRadius
      ).apply {
        layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
          gravity = (if (barOnTop) Gravity.TOP else Gravity.BOTTOM) or
            (if (startSide) Gravity.START else Gravity.END)
        }
      }
      root.addView(barGrip)
      barGrips.add(barGrip)
      setupBarGrip(barGrip, startSide)
    }

    // v2.2.0: PC-like free resizing from the window edges. Thin
    // invisible strips on the left, right and far edge (the edge
    // opposite the bar) resize the window; blur pauses while sizing.
    // v2.3.1 (item 70): strips are 20dp (16dp was easy to miss) and
    // the side strips stop exactly at the bar's real height
    // (46 + 52 = 98dp, +2dp clearance) instead of the old 106dp
    // guess that left a dead band above the bar.
    if (LinkPreferences.isOverlayFreeResize(this)) {
      val edgeThickness = dp(20)
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
            // is at the very edge) stay fully tappable. The stop
            // margin is the bar's real height — derived from the
            // same constants buildBar uses (v2.3.2 item 76) —
            // plus 2dp of clearance.
            if (height == ViewGroup.LayoutParams.MATCH_PARENT) {
              if (barOnTop) {
                topMargin = dp(BAR_TOP_ROW_DP + BAR_ICON_ROW_DP + 2)
              } else {
                bottomMargin = dp(BAR_TOP_ROW_DP + BAR_ICON_ROW_DP + 2)
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
              // The bottom edge stays above the gesture strip
              // (item 80): clamp Y against the strip-aware
              // bottom, then shrink the height if it still
              // crosses (the far-edge strip grows the height
              // directly).
              val maxBottom = screenHeight - bottomGestureExclusionPx()
              p.y = p.y.coerceIn(0, (maxBottom - p.height).coerceAtLeast(0))
              if (p.y + p.height > maxBottom) {
                p.height = (maxBottom - p.y).coerceAtLeast(dp(200))
              }
              try {
                wm.updateViewLayout(r, p)
              } catch (_: Exception) {
              }
              true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
      // Keep the corner grips reachable above the edge strips.
      grip.bringToFront()
      barGrips.forEach { it.bringToFront() }
    }


    // v2.3.0: a subtle move handle pill overlapping the page at
    // the edge opposite the bar (no dedicated band).
    addMoveHandle(root, barOnTop, screenWidth, screenHeight)

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
    if (fromRect == null &&
      LinkPreferences.getAnimLevel(this) != LinkPreferences.ANIM_OFF
    ) {
      params.windowAnimations = R.style.OverlayWindowAnim
    }
    windowManager?.addView(root, params)
    root.requestFocus()
    wv.loadUrl(initialUrl)
    if (fromRect != null && fromRect.size == 4) {
      animateWindowFrom(fromRect, params)
    }
  }

  /**
   * Bubble -> overlay (v2.3.0): the window grows out of the
   * bubble's rect, its center travelling along a gentle curve.
   */
  private fun animateWindowFrom(fromRect: IntArray, params: WindowManager.LayoutParams) {
    val wm = windowManager ?: return
    val root = rootView ?: return
    val animLevel = LinkPreferences.getAnimLevel(this)
    if (animLevel == LinkPreferences.ANIM_OFF) return
    params.windowAnimations = 0
    val targetX = params.x
    val targetY = params.y
    val targetW = params.width
    val targetH = params.height
    val fromCx = fromRect[0] + fromRect[2] / 2f
    val fromCy = fromRect[1] + fromRect[3] / 2f
    val targetCx = targetX + targetW / 2f
    val targetCy = targetY + targetH / 2f
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
      // iOS CollapseSpring curve (1, 0.3) from Glasmio's
      // LiquidMenuMorphMotion (v2.3.2 item 78c).
      duration = if (animLevel == LinkPreferences.ANIM_WEAK) 260L else 420L
      interpolator = IosSpringInterpolator(1f, 0.3f, duration)
      addUpdateListener { animation ->
        val t = animation.animatedValue as Float
        val center = arcPoint(fromCx, fromCy, targetCx, targetCy, t)
        val w = lerp(fromRect[2].toFloat(), targetW.toFloat(), t)
        val h = lerp(fromRect[3].toFloat(), targetH.toFloat(), t)
        params.width = w.toInt()
        params.height = h.toInt()
        params.x = (center[0] - w / 2f).toInt()
        params.y = (center[1] - h / 2f).toInt()
        try {
          wm.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
      }
    }
    windowAnimator = animator
    animator.start()
  }

  /**
   * Center path for the bubble <-> overlay morph (v2.3.1 item
   * 62): a cubic Bézier whose control points sit a third and two
   * thirds along the straight path and are pushed far out
   * perpendicular to it (38% of the travel distance, capped), so
   * the window sweeps one broad, gentle arc — OPPO-style —
   * instead of the v2.3.0 quadratic curve that read as a right
   * angle. The bulge always goes toward the top of the screen.
   */
  private fun arcPoint(
    x0: Float,
    y0: Float,
    x3: Float,
    y3: Float,
    t: Float
  ): FloatArray {
    val dx = x3 - x0
    val dy = y3 - y0
    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    var px = -dy
    var py = dx
    if (py > 0f) {
      px = -px
      py = -py
    }
    val plen = hypot(px.toDouble(), py.toDouble()).toFloat()
    val nx = if (plen > 0f) px / plen else 0f
    val ny = if (plen > 0f) py / plen else -1f
    val lift = minOf(dist * 0.38f, dp(240).toFloat())
    val c1x = x0 + dx / 3f + nx * lift
    val c1y = y0 + dy / 3f + ny * lift
    val c2x = x0 + dx * 2f / 3f + nx * lift
    val c2y = y0 + dy * 2f / 3f + ny * lift
    val u = 1f - t
    val x = u * u * u * x0 + 3f * u * u * t * c1x +
      3f * u * t * t * c2x + t * t * t * x3
    val y = u * u * u * y0 + 3f * u * u * t * c1y +
      3f * u * t * t * c2y + t * t * t * y3
    return floatArrayOf(x, y)
  }

  private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  /**
   * The subtle move handle (v2.3.0): a small translucent pill
   * overlapping the page at the edge opposite the bar. Dragging
   * it moves the window, like dragging the bar's top row.
   */
  @SuppressLint("ClickableViewAccessibility")
  private fun addMoveHandle(
    root: FrameLayout,
    barOnTop: Boolean,
    screenWidth: Int,
    screenHeight: Int
  ) {
    // v2.3.1 (item 70): the v2.3.0 handle was a bare 40x5dp pill —
    // a 5dp-tall touch target is nearly impossible to grab, and
    // misses landed on the resize edge strip behind it. The handle
    // is now a 64x26dp touch area with the same 40x5dp pill drawn
    // at its edge side; the pill's on-page position is unchanged.
    val handle = FrameLayout(this)
    val pill = View(this)
    pill.background = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = dp(3).toFloat()
      setColor(0x59FFFFFF)
    }
    val pillLp = FrameLayout.LayoutParams(dp(40), dp(5)).apply {
      gravity = (if (barOnTop) Gravity.BOTTOM else Gravity.TOP) or
        Gravity.CENTER_HORIZONTAL
      if (barOnTop) {
        bottomMargin = dp(4)
      } else {
        topMargin = dp(4)
      }
    }
    pill.layoutParams = pillLp
    handle.addView(pill)
    val lp = FrameLayout.LayoutParams(dp(64), dp(26))
    lp.gravity = (if (barOnTop) Gravity.BOTTOM else Gravity.TOP) or
      Gravity.CENTER_HORIZONTAL
    handle.layoutParams = lp
    root.addView(handle)
    var startX = 0
    var startY = 0
    var rawX = 0f
    var rawY = 0f
    handle.setOnTouchListener { v, event ->
      val wm = windowManager
      val r = rootView
      val p = layoutParams
      if (wm == null || r == null || p == null) {
        return@setOnTouchListener false
      }
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          setBlurPaused(true)
          startX = p.x
          startY = p.y
          rawX = event.rawX
          rawY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val maxX = (screenWidth - p.width).coerceAtLeast(0)
          // The bottom edge stays above the gesture strip
          // (item 80).
          val maxY = (screenHeight - bottomGestureExclusionPx() - p.height)
            .coerceAtLeast(0)
          p.x = (startX + (event.rawX - rawX).toInt()).coerceIn(0, maxX)
          p.y = (startY + (event.rawY - rawY).toInt()).coerceIn(0, maxY)
          try {
            wm.updateViewLayout(r, p)
          } catch (_: Exception) {
          }
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          setBlurPaused(false)
          saveGeometry()
          v.performClick()
          true
        }
        else -> false
      }
    }
    handle.bringToFront()
  }

  private fun saveGeometry() {
    if (!LinkPreferences.isOverlayRemember(this)) return
    val p = layoutParams ?: return
    if (isMinimized) return
    LinkPreferences.saveOverlayGeometry(this, p.x, p.y, p.width, p.height)
  }

  // ----- v2.2.0: blur is paused while resizing (cheaper + clearer) -----

  // ----- backdrop blur state (v2.3.3 items 81/86/87) -----

  /** Takes one blur-pause hold; the blur stays off until every hold is released. */
  private fun acquireBlurPause(reattach: Boolean) {
    blurPauseHolds++
    applyBlurState(reattach)
  }

  /** Releases one blur-pause hold. */
  private fun releaseBlurPause(reattach: Boolean) {
    if (blurPauseHolds > 0) blurPauseHolds--
    applyBlurState(reattach)
  }

  /**
   * Applies the effective blur state: on only when the settings
   * want it ([blurDesired]) and no pause hold is outstanding.
   *
   * History: v2.2.0–v2.3.2 edited the window's LayoutParams
   * (clearing FLAG_BLUR_BEHIND, zeroing the radius) and called
   * updateViewLayout, and v2.3.2 additionally covered the whole
   * window with an opaque view during drags. On the user's
   * ColorOS device the flag edits were never re-applied by the
   * window manager — the blur never actually stopped — and the
   * cover blacked out the page being resized. v2.3.3 (items 81
   * and 86): the cover is gone, and a state flip instead
   * re-attaches the window — removeView + addView reusing the
   * same root view and the same params object with only the
   * blur attributes changed — so the window is created with the
   * attributes it should have, whatever the ROM does with
   * in-place updates. The view hierarchy (and with it the
   * WebView's page state) is untouched by the re-attach.
   */
  private fun applyBlurState(reattach: Boolean) {
    if (Build.VERSION.SDK_INT < 31) return
    val want = blurDesired && blurPauseHolds == 0
    if (want == blurApplied) return
    val wm = windowManager ?: return
    val root = rootView ?: return
    val p = layoutParams ?: return
    val blurFlag = WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    if (want) {
      p.flags = p.flags or blurFlag
      p.blurBehindRadius = dp(
        LinkPreferences.glassBlurRadiusDp(LinkPreferences.getGlassStrength(this))
      )
      p.dimAmount = 0.2f
    } else {
      p.flags = p.flags and blurFlag.inv()
      p.blurBehindRadius = 0
      p.dimAmount = 0f
    }
    // v2.3.4 (item 88): a state flip caused by a geometry drag
    // must never re-attach the window. v2.3.3 re-attached on
    // every drag start/end, and on the user's ColorOS device
    // adjusting the window then crashed the service (the window
    // is removed and re-added while one of its children is in
    // the middle of dispatching the very gesture that drives
    // the drag). Drag flips only update the layout params in
    // place; the re-attach is kept for passthrough transitions,
    // which happen outside any drag and worked on the device.
    if (reattach) {
      reattachWindow(wm, root, p)
    } else {
      try {
        wm.updateViewLayout(root, p)
      } catch (_: Exception) {
      }
    }
    blurApplied = want
  }

  /**
   * Removes and re-adds the overlay window with the same root
   * view and params object. The window animation is suppressed
   * for the re-add (and restored on the params afterwards) so
   * the window does not replay its entry animation, and focus
   * is re-requested so the root keeps receiving keys.
   */
  private fun reattachWindow(
    wm: WindowManager,
    root: View,
    p: WindowManager.LayoutParams
  ) {
    val animations = p.windowAnimations
    p.windowAnimations = 0
    try {
      wm.removeView(root)
    } catch (_: Exception) {
    }
    try {
      wm.addView(root, p)
    } catch (_: Exception) {
    }
    p.windowAnimations = animations
    root.requestFocus()
  }

  /**
   * The drag-time blur pause (geometry drags: title bar, move
   * handle, corner grips, edge strips). Takes a pause hold for
   * the duration of the drag; while this drag actually stopped
   * an applied blur, the window's own surface also goes opaque
   * (the root/bar background color, as in v2.3.1) so the window
   * does not turn translucent mid-drag — without covering the
   * page the way the removed drag cover did (item 86).
   */
  private fun setBlurPaused(paused: Boolean) {
    if (Build.VERSION.SDK_INT < 31) return
    if (paused) {
      val wasApplied = blurApplied
      acquireBlurPause(reattach = false)
      if (wasApplied) {
        val opaque = (0xFF shl 24) or (glassBarColor() and 0xFFFFFF)
        (rootView?.background as? GradientDrawable)?.setColor(opaque)
        barView?.setBackgroundColor(opaque)
      }
    } else {
      releaseBlurPause(reattach = false)
      (rootView?.background as? GradientDrawable)?.setColor(glassBarColor())
      barView?.setBackgroundColor(glassBarColor())
    }
  }

  /**
   * How much of the bottom edge overlay windows must stay out
   * of: the system gesture strip (item 75). Overlay windows
   * receive touches even inside the gesture area, so a
   * full-height touchable catcher swallows the home gesture for
   * as long as it lives. Sized at a 64dp-class minimum (item 80):
   * ColorOS's gesture detection area is wider than the nominal
   * navigation bar height — with the smaller navBar + 8dp margin
   * the home gesture still died on device, even while only the
   * main app was in use — so every resident window (this main
   * window, the catchers, the menu, the bubbles) keeps this
   * whole strip clear.
   */
  private fun bottomGestureExclusionPx(): Int {
    val resId = resources.getIdentifier(
      "navigation_bar_height",
      "dimen",
      "android"
    )
    val navBar = if (resId > 0) {
      resources.getDimensionPixelSize(resId)
    } else {
      dp(24)
    }
    return maxOf(navBar, dp(56)) + dp(8)
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
    // v2.3.2 (item 75): the catcher stops above the system
    // gesture strip. The v2.3.1 catcher was full-screen and
    // touchable for the overlay's whole lifetime, so touches
    // starting in the bottom gesture area landed on it (and its
    // ACTION_UP even entered passthrough) instead of reaching
    // the system's home gesture — after opening an app, which
    // shows the overlay, the home button/gesture stopped
    // working until the overlay went away.
    val metrics = resources.displayMetrics
    val cp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      metrics.heightPixels - bottomGestureExclusionPx(),
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }
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
        dp(BAR_TOP_ROW_DP)
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
    if (!onDarkSurface()) {
      title.setTextColor(0xFF202124.toInt())
      url.setTextColor(0xFF5F6368.toInt())
    }

    val iconRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(BAR_ICON_ROW_DP)
      )
    }

    val close = createBarIconButton(R.drawable.ic_ov_close).apply {
      contentDescription = getString(R.string.act_close)
      setOnClickListener { closeOverlay() }
    }
    barCloseButton = close
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
      setOnClickListener {
        // Toggle (v2.3.1 item 69): pressing ⋮ while the menu is
        // open closes it (with the close animation) instead of
        // reopening it.
        if (menuView != null) {
          hideMenu(animated = true)
        } else {
          showMenu(this)
        }
      }
    }
    barMenuButton = menu

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
      if (!onDarkSurface()) {
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
    val wm = windowManager
    val root = rootView
    val params = layoutParams
    if (wm == null || root == null || params == null ||
      LinkPreferences.getAnimLevel(this) == LinkPreferences.ANIM_OFF
    ) {
      BubbleService.open(this, url, secret)
      closeOverlay()
      return
    }
    // Overlay -> bubble (v2.3.0): shrink along a curve toward the
    // bubble's docking spot, then swap to the real bubble.
    val metrics = resources.displayMetrics
    val size = dp(56)
    val rememberPos = LinkPreferences.isBubbleRememberPos(this)
    val toLeft = rememberPos && LinkPreferences.getBubbleSide(this) == 0
    val targetX = if (toLeft) 0 else metrics.widthPixels - size
    // A remembered bubble Y can be stale (rotation, another
    // screen): clamp the morph target into the display.
    val targetY = (if (rememberPos && LinkPreferences.getBubbleY(this) >= 0) {
      LinkPreferences.getBubbleY(this)
    } else {
      dp(120)
    }).coerceIn(0, (metrics.heightPixels - size).coerceAtLeast(0))
    val startX = params.x
    val startY = params.y
    val startW = params.width
    val startH = params.height
    val startCx = startX + startW / 2f
    val startCy = startY + startH / 2f
    val targetCx = targetX + size / 2f
    val targetCy = targetY + size / 2f
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
      // iOS CollapseSpring curve (1, 0.3) from Glasmio's
      // LiquidMenuMorphMotion (v2.3.2 item 78c).
      duration = if (LinkPreferences.getAnimLevel(this@OverlayService) ==
        LinkPreferences.ANIM_WEAK
      ) {
        240L
      } else {
        420L
      }
      interpolator = IosSpringInterpolator(1f, 0.3f, duration)
      addUpdateListener { animation ->
        val t = animation.animatedValue as Float
        val center = arcPoint(startCx, startCy, targetCx, targetCy, t)
        val w = lerp(startW.toFloat(), size.toFloat(), t)
        val h = lerp(startH.toFloat(), size.toFloat(), t)
        params.width = w.toInt()
        params.height = h.toInt()
        params.x = (center[0] - w / 2f).toInt()
        params.y = (center[1] - h / 2f).toInt()
        try {
          wm.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
      }
      addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
          BubbleService.open(this@OverlayService, url, secret)
          closeOverlay()
        }
      })
    }
    windowAnimator = animator
    animator.start()
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
    val targetH = if (isPeek) peekHeight else fullHeight
    val startH = params.height
    if (LinkPreferences.getAnimLevel(this) == LinkPreferences.ANIM_OFF ||
      startH == targetH || startH <= 0
    ) {
      params.height = targetH
      val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
      if (params.y > maxY) {
        params.y = maxY
      }
      try {
        wm.updateViewLayout(root, params)
      } catch (_: Exception) {
      }
      saveGeometry()
      return
    }
    // Animate the height change (v2.3.1 item 66) instead of the
    // v2.3.0 instant jump, on the iOS BlurOut spring curve
    // (0.8, 0.45) from Glasmio's LiquidMenuMorphMotion
    // (v2.3.2 item 78c).
    windowAnimator?.cancel()
    val animator = ValueAnimator.ofInt(startH, targetH).apply {
      duration = 460L
      interpolator = IosSpringInterpolator(0.8f, 0.45f, 460)
      addUpdateListener { animation ->
        params.height = animation.animatedValue as Int
        val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
        if (params.y > maxY) {
          params.y = maxY
        }
        try {
          wm.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
      }
      addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
          saveGeometry()
        }
      })
    }
    windowAnimator = animator
    animator.start()
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
    // v2.3.3 (item 87): the backdrop blur is always off while
    // passthrough lasts — the user reads and operates the app
    // underneath through the surround, and a blurred surround
    // made it unreadable. This takes a blur-pause hold (the
    // same mechanism as the drag pause); exitPassthrough
    // releases it, and the blur returns to whatever the setting
    // says. The re-attach inside applyBlurState happens before
    // the return handle is added below, and the catcher was
    // already added behind this window, so neither the
    // passthrough state nor the window z-order is disturbed.
    acquireBlurPause(reattach = true)

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
    // Release the passthrough blur-pause hold (item 87): the
    // blur returns to the settings-derived state — unless a
    // geometry drag is somehow still holding it. The re-attach
    // inside applyBlurState re-requests focus on the root, so
    // the window keeps receiving keys afterwards.
    releaseBlurPause(reattach = true)
  }

  // ----- v2.3.0: the menu is a glass panel that pops out of the bar -----

  private fun showMenu(anchor: View) {
    hideMenu()
    val wm = windowManager ?: return
    menuAnchorView = anchor
    val dark = onDarkSurface()
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

    val panel = FrameLayout(this).apply {
      addView(content)
    }
    menuView = panel
    val params = WindowManager.LayoutParams(
      dp(288),
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      // v2.3.2 (item 79): no FLAG_WATCH_OUTSIDE_TOUCH and no
      // NOT_TOUCH_MODAL. The v2.3.1 panel watched for outside
      // touches: a second tap on the ⋮ button was delivered to
      // the panel as ACTION_OUTSIDE first, which hid the menu,
      // and the button's own click then saw menuView == null and
      // re-opened it — the toggle could never close the menu.
      // Outside taps are now consumed by a dedicated catcher
      // window behind the panel (see addMenuCatcher), so the
      // button never receives the closing tap at all.
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      // Anchored to the ⋮ button's real position by
      // positionMenu(); x/y are the panel's top-left corner.
      gravity = Gravity.TOP or Gravity.START
      if (LinkPreferences.getAnimLevel(this@OverlayService) != LinkPreferences.ANIM_OFF) {
        windowAnimations = R.style.OverlayMenuAnim
      }
    }
    menuParams = params
    addMenuCatcher()
    showMenuPage(MenuPage.MAIN)
    positionMenu()
    try {
      wm.addView(panel, params)
    } catch (_: Exception) {
      menuView = null
      menuParams = null
      menuContent = null
      menuAnchorView = null
      removeMenuCatcher()
    }
  }

  /**
   * Places the menu panel relative to its ⋮ anchor (item 79):
   * above the button by default, flipped below when it would
   * leave the top of the screen, and clamped on every side so
   * the panel is never clipped. Called when the menu opens and
   * again whenever its content (and therefore height) changes,
   * so the panel follows the button wherever the window moves.
   */
  private fun positionMenu() {
    val panel = menuView ?: return
    val params = menuParams ?: return
    val anchor = menuAnchorView ?: return
    val metrics = resources.displayMetrics
    val screenW = metrics.widthPixels
    val screenH = metrics.heightPixels
    val widthSpec = View.MeasureSpec.makeMeasureSpec(
      params.width,
      View.MeasureSpec.EXACTLY
    )
    val heightSpec = View.MeasureSpec.makeMeasureSpec(
      0,
      View.MeasureSpec.UNSPECIFIED
    )
    panel.measure(widthSpec, heightSpec)
    val menuW = params.width
    val menuH = panel.measuredHeight
    val anchorLoc = IntArray(2)
    anchor.getLocationOnScreen(anchorLoc)
    val gap = dp(6)
    val margin = dp(8)
    val topSafe = statusBarHeight() + dp(4)
    var x = anchorLoc[0] + anchor.width - menuW
    x = x.coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))
    val aboveY = anchorLoc[1] - gap - menuH
    menuAboveAnchor = aboveY >= topSafe
    var y = if (menuAboveAnchor) {
      aboveY
    } else {
      anchorLoc[1] + anchor.height + gap
    }
    // The menu's bottom edge also stays out of the system gesture
    // strip (item 80): the panel is a touchable window, and near the
    // bottom of the screen it would otherwise reach into the strip.
    val maxY = (screenH - menuH - bottomGestureExclusionPx()).coerceAtLeast(topSafe)
    y = y.coerceIn(topSafe, maxY)
    params.x = x
    params.y = y
    menuPivotX = (anchorLoc[0] + anchor.width / 2f - x)
      .coerceIn(0f, menuW.toFloat())
    menuPivotY = if (menuAboveAnchor) menuH.toFloat() else 0f
    if (panel.isAttachedToWindow) {
      try {
        windowManager?.updateViewLayout(panel, params)
      } catch (_: Exception) {
      }
    }
  }

  /**
   * A transparent window behind the menu panel that consumes
   * every touch outside the panel and closes the menu (item 79).
   * Because the closing tap is consumed here, it can never reach
   * the ⋮ button and re-open the menu — the race that broke the
   * v2.3.1 toggle. Like the passthrough catcher, it stops above
   * the system gesture strip (item 75).
   */
  @SuppressLint("ClickableViewAccessibility")
  private fun addMenuCatcher() {
    removeMenuCatcher()
    val wm = windowManager ?: return
    val catcher = View(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
          hideMenu()
        }
        true
      }
    }
    val metrics = resources.displayMetrics
    val cp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      metrics.heightPixels - bottomGestureExclusionPx(),
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }
    try {
      wm.addView(catcher, cp)
      menuCatcherView = catcher
    } catch (_: Exception) {
    }
  }

  private fun removeMenuCatcher() {
    menuCatcherView?.let { catcher ->
      try {
        windowManager?.removeView(catcher)
      } catch (_: Exception) {
      }
    }
    menuCatcherView = null
  }

  private fun hideMenu(animated: Boolean = false) {
    removeMenuCatcher()
    menuAnchorView = null
    val panel = menuView ?: return
    menuView = null
    menuParams = null
    menuContent = null
    if (animated &&
      LinkPreferences.getAnimLevel(this) != LinkPreferences.ANIM_OFF
    ) {
      // Close animation (item 69): fade and shrink toward the
      // ⋮ button — the pivot is the button's real position
      // inside the panel, computed by positionMenu (item 79).
      panel.pivotX = menuPivotX
      panel.pivotY = menuPivotY
      panel.animate()
        .alpha(0f)
        .scaleX(0.92f)
        .scaleY(0.92f)
        .setDuration(170)
        .setInterpolator(IosSpringInterpolator(1f, 0.2f, 170))
        .withEndAction {
          try {
            windowManager?.removeView(panel)
          } catch (_: Exception) {
          }
        }
        .start()
      return
    }
    try {
      windowManager?.removeView(panel)
    } catch (_: Exception) {
    }
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
    // The page changed the panel's height: re-anchor it to the
    // ⋮ button so it stays attached to the button (item 79).
    // During the initial showMenu call this is a no-op position
    // pass; showMenu positions once more before adding the view.
    if (menuParams != null) {
      positionMenu()
    }
  }

  private fun menuTextColor(): Int = if (onDarkSurface()) Color.WHITE else 0xFF202124.toInt()

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
      if (!onDarkSurface()) {
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
        setColor(if (onDarkSurface()) 0x26FFFFFF else 0x14000000)
        setStroke(dp(1), if (onDarkSurface()) 0x30FFFFFF else 0x1A000000)
      }
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }
    val icon = ImageView(this).apply {
      setImageResource(iconRes)
      layoutParams = FrameLayout.LayoutParams(dp(21), dp(21)).apply {
        gravity = Gravity.CENTER
      }
      if (!onDarkSurface()) {
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
    windowAnimator?.cancel()
    windowAnimator = null
    hideMenu()
    removeCatcher()
    blurPauseHolds = 0
    findBarView?.let { bar ->
      try {
        windowManager?.removeView(bar)
      } catch (_: Exception) {
      }
    }
    findBarView = null
    findBarParams = null
    barView = null
    barTopRow = null
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
    const val EXTRA_FROM_RECT = "arun.com.chromer.overlay.EXTRA_FROM_RECT"
    private const val ACTION_OPEN = "arun.com.chromer.overlay.ACTION_OPEN"
    private const val ACTION_CLOSE = "arun.com.chromer.overlay.ACTION_CLOSE"
    private const val CHANNEL_ID = "overlay_browsing"
    private const val NOTIFICATION_ID = 1001

    private const val BAR_COLOR = 0xFF202124.toInt()
    // The bar's real row heights: buildBar builds exactly these,
    // and the resize strips stop at their sum (item 76).
    private const val BAR_TOP_ROW_DP = 46
    private const val BAR_ICON_ROW_DP = 52
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
      secret: Boolean = false,
      fromRect: IntArray? = null
    ): Boolean {
      if (!canDrawOverlays(context)) return false
      return try {
        context.startForegroundService(
          Intent(context, OverlayService::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_PEEK, peek)
            .putExtra(EXTRA_SECRET, secret)
            .putExtra(EXTRA_FROM_RECT, fromRect)
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

/**
 * The resize corner grip: a small gentle arc tracing the window's
 * rounded corner. v2.3.2 (item 76): the arc is a true circular arc
 * with the window's own corner radius, centered one radius in from
 * the corner point — the v2.3.1 grip drew a 22dp quadratic curve
 * inset 3dp inside a 38dp box, so it floated off the 28dp frame it
 * was supposed to trace and looked detached from the corner.
 * v2.3.3 (item 86): [flipHorizontal] mirrors the arc for the
 * START-side corners — the bar-side grips sit on both bottom (or
 * top) corners, not only the END one.
 */
private class ArcGripView(
  context: Context,
  private val flipVertical: Boolean,
  private val flipHorizontal: Boolean,
  private val cornerRadiusPx: Float
) : View(context) {
  private val density = context.resources.displayMetrics.density
  private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 3.4f * density
    color = 0x59000000
    strokeCap = Paint.Cap.ROUND
  }
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 2f * density
    color = 0xD9FFFFFF.toInt()
    strokeCap = Paint.Cap.ROUND
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    val r = cornerRadiusPx
    // The view sits flush in the window's corner, so the corner
    // arc of the window frame is the circle of radius r centered
    // one radius in from this view's outer corner. The RectF is
    // anchored to that outer corner on both axes.
    val left = if (flipHorizontal) 0f else w - 2 * r
    val top = if (flipVertical) h - 2 * r else 0f
    val rect = RectF(left, top, left + 2 * r, top + 2 * r)
    // Sweep the quarter from one window edge to the other
    // (angles: 0 at 3 o'clock, positive clockwise, y down).
    val startAngle = when {
      // Bottom-left: from the bottom edge (90) to the left
      // edge (180).
      flipVertical && flipHorizontal -> 90f
      // Bottom-right: from the right edge (0) down to the
      // bottom edge (90).
      flipVertical -> 0f
      // Top-left: from the left edge (180) to the top edge
      // (270).
      flipHorizontal -> 180f
      // Top-right: from the top edge (-90) to the right edge
      // (0).
      else -> -90f
    }
    canvas.drawArc(rect, startAngle, 90f, false, outlinePaint)
    canvas.drawArc(rect, startAngle, 90f, false, paint)
  }
}
