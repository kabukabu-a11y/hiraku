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

package arun.com.chromer.bubbles

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import arun.com.chromer.R
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.home.HomeActivity
import arun.com.chromer.overlay.OverlayService
import arun.com.chromer.ui.IosSpringInterpolator
import arun.com.chromer.ui.glassViewColor
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.hypot

class BubbleService : Service() {

  private class BubbleItem(
    val url: String,
    val view: View,
    val params: WindowManager.LayoutParams,
    val secret: Boolean = false,
    var background: GradientDrawable? = null,
    var letterView: TextView? = null,
    var badgeImage: ImageView? = null,
    var badgeLetter: TextView? = null,
    var animator: ValueAnimator? = null
  )

  private lateinit var windowManager: WindowManager
  private val bubbles = mutableListOf<BubbleItem>()
  // Top edge of the bubble stack; restored from the remembered
  // position when the memory setting is on (v2.1.0).
  private var stackBaseY: Int = -1
  private var trashView: View? = null
  private var trashCircleView: View? = null
  private var trashHot: Boolean = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private var isTrashShown = false

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_ADD -> {
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
          if (bubbles.isEmpty()) stopSelf()
          return START_NOT_STICKY
        }
        addBubble(url, intent.getBooleanExtra(EXTRA_SECRET, false))
      }
      ACTION_CLEAR -> {
        clearAllBubbles()
        stopSelf()
      }
      else -> {
        if (bubbles.isEmpty()) stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    hideTrash()
    clearAllBubbles()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun addBubble(url: String, secret: Boolean = false) {
    if (bubbles.isEmpty()) {
      stackBaseY = if (LinkPreferences.isBubbleRememberPos(this) &&
        LinkPreferences.getBubbleY(this) >= 0
      ) {
        LinkPreferences.getBubbleY(this)
      } else {
        dp(120)
      }
      // A remembered Y can outlive the screen it was saved on
      // (rotation, a different device): never let the stack start
      // outside the display (v2.3.2 item 73).
      stackBaseY = clampedBaseY(stackBaseY)
    }
    if (bubbles.size >= MAX_BUBBLES) {
      val oldest = bubbles.removeAt(0)
      try {
        windowManager.removeView(oldest.view)
      } catch (_: Exception) {
      }
    }

    val size = dp(BUBBLE_SIZE_DP)
    val screenWidth = resources.displayMetrics.widthPixels
    val host = Uri.parse(url).host
    val letter = host?.takeIf { it.isNotBlank() }?.first()?.uppercaseChar()?.toString() ?: "?"
    val colorIndex = if (host.isNullOrBlank()) {
      0
    } else {
      (host.hashCode() and Int.MAX_VALUE) % BUBBLE_PALETTE.size
    }
    val glassOn = LinkPreferences.isGlassEnabled(this)

    // v2.3.0: the whole bubble is glass tinted with the site color;
    // only the center letter uses a contrasting color. A small
    // favicon badge sits at the bottom-right.
    val background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(tintedColor(BUBBLE_PALETTE[colorIndex], glassOn))
      setStroke(dp(1), 0x66FFFFFF)
    }
    val letterView = TextView(this).apply {
      gravity = Gravity.CENTER
      setTextColor(contrastColor(BUBBLE_PALETTE[colorIndex]))
      typeface = Typeface.DEFAULT_BOLD
      textSize = 22f
      text = letter
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    val badgeLetter = TextView(this).apply {
      gravity = Gravity.CENTER
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      textSize = 10f
      text = letter
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.CENTER
      }
    }
    val badgeImage = ImageView(this).apply {
      scaleType = ImageView.ScaleType.CENTER_CROP
      visibility = View.GONE
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    val badge = FrameLayout(this).apply {
      this.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(BUBBLE_PALETTE[colorIndex])
        setStroke(dp(1), 0x88FFFFFF.toInt())
      }
      clipToOutline = true
      layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply {
        gravity = Gravity.BOTTOM or Gravity.END
      }
      addView(badgeLetter)
      addView(badgeImage)
    }
    val view = FrameLayout(this).apply {
      this.background = background
      elevation = dp(4).toFloat()
      addView(letterView)
      addView(badge)
    }

    val params = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      // v2.3.9 (item 97): glass bubbles. True refraction of
      // the app underneath is impossible (Android never hands
      // another app's pixels to us), so the platform backdrop
      // blur stands in for it, under the existing site-color
      // glass tint — the same mechanism the overlay uses.
      if (glassOn && Build.VERSION.SDK_INT >= 31) {
        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        blurBehindRadius = dp(
          LinkPreferences.glassBlurRadiusDp(
            LinkPreferences.getGlassStrength(this@BubbleService)
          )
        )
      }
      gravity = Gravity.TOP or Gravity.START
      val remember = LinkPreferences.isBubbleRememberPos(this@BubbleService)
      x = if (remember && LinkPreferences.getBubbleSide(this@BubbleService) == 0) {
        0
      } else {
        screenWidth - size
      }
      // The dock never enters the system gesture strip
      // (v2.3.3 item 80).
      y = (stackBaseY + bubbles.size * (size + dp(8)))
        .coerceAtMost(maxDockY())
    }

    val item = BubbleItem(
      url = url,
      view = view,
      params = params,
      secret = secret,
      background = background,
      letterView = letterView,
      badgeImage = badgeImage,
      badgeLetter = badgeLetter
    )
    setupTouchListener(item)
    bubbles.add(item)
    windowManager.addView(view, params)
    // Entry animation: pop in with an iOS spring curve instead of
    // appearing instantly (v2.3.2 item 78c; the spec is an
    // in-between (damping, response) pair in the range of
    // Glasmio's LiquidMenuMorphMotion springs).
    if (LinkPreferences.getAnimLevel(this) != LinkPreferences.ANIM_OFF) {
      view.scaleX = 0.4f
      view.scaleY = 0.4f
      view.alpha = 0f
      view.animate()
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setDuration(380)
        .setInterpolator(IosSpringInterpolator(0.7f, 0.4f, 380))
        .start()
    }
    relayoutBubbles()
    updateNotification()
    if (!host.isNullOrBlank()) {
      loadSiteVisuals(item, host, BUBBLE_PALETTE[colorIndex])
    }
  }

  /** The site color as bubble glass: translucent while glass is on. */
  private fun tintedColor(color: Int, glassOn: Boolean): Int {
    return if (glassOn) {
      Color.argb(225, Color.red(color), Color.green(color), Color.blue(color))
    } else {
      color
    }
  }

  /** A letter color that stays readable on the given site color. */
  private fun contrastColor(color: Int): Int {
    val luminance = (0.299 * Color.red(color) +
      0.587 * Color.green(color) +
      0.114 * Color.blue(color)) / 255.0
    return if (luminance > 0.62) 0xFF202124.toInt() else Color.WHITE
  }

  /**
   * Fetches the site's favicon off the main thread: the badge shows
   * it, and its dominant color retints the bubble glass.
   */
  private fun loadSiteVisuals(item: BubbleItem, host: String, fallbackColor: Int) {
    Thread {
      val bitmap = try {
        val connection = URL(
          "https://www.google.com/s2/favicons?domain=$host&sz=128"
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = 2500
        connection.readTimeout = 2500
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
      } catch (_: Exception) {
        null
      }
      if (bitmap != null) {
        val dominant = dominantColor(bitmap)
        mainHandler.post {
          if (bubbles.contains(item)) {
            item.badgeImage?.setImageBitmap(bitmap)
            item.badgeImage?.visibility = View.VISIBLE
            item.badgeLetter?.visibility = View.GONE
            if (dominant != null) {
              val glassOn = LinkPreferences.isGlassEnabled(this@BubbleService)
              item.background?.setColor(tintedColor(dominant, glassOn))
              item.letterView?.setTextColor(contrastColor(dominant))
            }
          }
        }
      }
    }.start()
  }

  /** Average of the bitmap's opaque pixels, ignoring near-white ones. */
  private fun dominantColor(bitmap: Bitmap): Int? {
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 16)
    var x = 0
    while (x < bitmap.width) {
      var y = 0
      while (y < bitmap.height) {
        val pixel = bitmap.getPixel(x, y)
        if (Color.alpha(pixel) >= 200) {
          val r = Color.red(pixel)
          val g = Color.green(pixel)
          val b = Color.blue(pixel)
          // Skip near-white / near-black noise (favicon padding).
          if (!(r > 242 && g > 242 && b > 242) && !(r < 14 && g < 14 && b < 14)) {
            red += r
            green += g
            blue += b
            count++
          }
        }
        y += step
      }
      x += step
    }
    if (count == 0L) return null
    return Color.rgb(
      (red / count).toInt(),
      (green / count).toInt(),
      (blue / count).toInt()
    )
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setupTouchListener(item: BubbleItem) {
    var downX = 0
    var downY = 0
    var rawDownX = 0f
    var rawDownY = 0f
    var moved = false
    var longPressed = false
    val handler = Handler(Looper.getMainLooper())
    val longPressRunnable = Runnable {
      if (!moved) {
        longPressed = true
        openPreview(item)
      }
    }

    item.view.setOnTouchListener { _, event ->
      val size = dp(BUBBLE_SIZE_DP)
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          item.animator?.cancel()
          item.animator = null
          downX = item.params.x
          downY = item.params.y
          rawDownX = event.rawX
          rawDownY = event.rawY
          moved = false
          longPressed = false
          handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = (event.rawX - rawDownX).toInt()
          val dy = (event.rawY - rawDownY).toInt()
          if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
            moved = true
            handler.removeCallbacks(longPressRunnable)
          }
          item.params.x = downX + dx
          item.params.y = downY + dy
          try {
            windowManager.updateViewLayout(item.view, item.params)
          } catch (_: Exception) {
          }
          if (moved) {
            showTrash()
            // Hit test in real screen coordinates (v2.3.2 item
            // 74): both views report their on-screen location in
            // the same space, so the test cannot drift. The
            // v2.3.1 test mixed spaces — the bubble's window
            // params (origin below the status bar, inside the
            // overlay area) against a trash center computed from
            // the full display height — so the effective target
            // sat a system-bar height away from the visible ✕
            // and the drop rarely registered as hot.
            setTrashHot(isOverTrash(item), item.view)
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          handler.removeCallbacks(longPressRunnable)
          // Recompute the hot state in screen coordinates BEFORE
          // resetting it: v2.3.0 reset trashHot first, so the
          // drop-on-trash branch below could never fire and
          // bubbles never deleted (item 61).
          val wasHot = isOverTrash(item)
          setTrashHot(false, item.view)
          hideTrash()
          if (longPressed) {
            // Preview already opened; keep the bubble where it is.
          } else if (!moved) {
            openBubble(item)
          } else if (wasHot) {
            removeBubble(item, animate = true)
          } else {
            // Snap back to the docked edge and the bubble's stack
            // slot with a spring (v2.3.1 item 60) instead of the
            // v2.3.0 instant teleport.
            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val bubbleCenterX = item.params.x + size / 2
            val toLeft = bubbleCenterX < screenWidth / 2
            val targetX = if (toLeft) 0 else screenWidth - size
            val index = bubbles.indexOf(item).coerceAtLeast(0)
            val baseY = clampedBaseY(
              if (stackBaseY >= 0) stackBaseY else dp(120)
            )
            val targetY = (baseY + index * (size + dp(8)))
              .coerceAtMost(maxDockY())
            if (LinkPreferences.getAnimLevel(this@BubbleService) ==
              LinkPreferences.ANIM_OFF
            ) {
              item.params.x = targetX
              item.params.y = targetY
              try {
                windowManager.updateViewLayout(item.view, item.params)
              } catch (_: Exception) {
              }
            } else {
              // iOS EjectSpring (0.75, 0.5) from Glasmio's
              // LiquidMenuMorphMotion (v2.3.2 item 78c).
              animateBubbleTo(
                item,
                targetX,
                targetY,
                550L,
                IosSpringInterpolator(0.75f, 0.5f, 550)
              )
            }
            if (LinkPreferences.isBubbleRememberPos(this@BubbleService)) {
              if (bubbles.firstOrNull() === item) {
                stackBaseY = targetY
              }
              LinkPreferences.saveBubblePosition(
                this@BubbleService,
                if (toLeft) 0 else 1,
                stackBaseY
              )
            }
          }
          true
        }
        MotionEvent.ACTION_CANCEL -> {
          handler.removeCallbacks(longPressRunnable)
          setTrashHot(false, item.view)
          hideTrash()
          true
        }
        else -> false
      }
    }
  }

  /** Animates a bubble's window position (snap-back, relayout). */
  private fun animateBubbleTo(
    item: BubbleItem,
    toX: Int,
    toY: Int,
    duration: Long,
    interpolator: Interpolator
  ) {
    item.animator?.cancel()
    val fromX = item.params.x
    val fromY = item.params.y
    if (fromX == toX && fromY == toY) {
      item.animator = null
      return
    }
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
      this.duration = duration
      this.interpolator = interpolator
      addUpdateListener { animation ->
        val t = animation.animatedValue as Float
        item.params.x = (fromX + (toX - fromX) * t).toInt()
        item.params.y = (fromY + (toY - fromY) * t).toInt()
        try {
          windowManager.updateViewLayout(item.view, item.params)
        } catch (_: Exception) {
        }
      }
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          if (item.animator === animation) {
            item.animator = null
          }
        }
      })
    }
    item.animator = animator
    animator.start()
  }

  private fun relayoutBubbles() {
    val size = dp(BUBBLE_SIZE_DP)
    val metrics = resources.displayMetrics
    val baseY = clampedBaseY(if (stackBaseY >= 0) stackBaseY else dp(120))
    val maxY = maxDockY()
    val animate = LinkPreferences.getAnimLevel(this) != LinkPreferences.ANIM_OFF
    bubbles.forEachIndexed { index, item ->
      // Never fight a running animation (v2.3.2 item 73): the
      // v2.3.1 relayout replaced the animator of a bubble that
      // was mid snap-back and froze its X wherever it happened
      // to be — bubbles ended up stranded mid-screen. A bubble
      // whose animator is running already has a docked target;
      // it will land correctly on its own.
      if (item.animator != null) return@forEachIndexed
      val targetY = (baseY + index * (size + dp(8))).coerceAtMost(maxY)
      // Keep the bubble on its docked edge: derive the target X
      // from the side it currently sits on instead of trusting a
      // possibly mid-flight params.x.
      val dockedX = if (item.params.x + size / 2 < metrics.widthPixels / 2) {
        0
      } else {
        metrics.widthPixels - size
      }
      if (animate) {
        // Slide into the new slot instead of teleporting when a
        // bubble is added or removed (item 60), on the iOS
        // CollapseSpring curve (1, 0.3) from Glasmio's
        // LiquidMenuMorphMotion (v2.3.2 item 78c).
        animateBubbleTo(
          item,
          dockedX,
          targetY,
          400L,
          IosSpringInterpolator(1f, 0.3f, 400)
        )
      } else {
        item.params.x = dockedX
        item.params.y = targetY
        try {
          windowManager.updateViewLayout(item.view, item.params)
        } catch (_: Exception) {
        }
      }
    }
  }

  /** Keeps the stack's top edge inside the display (item 73). */
  private fun clampedBaseY(baseY: Int): Int {
    return baseY.coerceIn(0, maxDockY())
  }

  /**
   * How much of the bottom edge overlay windows must stay out
   * of: the system gesture strip (same rule as OverlayService,
   * v2.3.2 item 75), at the same 64dp-class minimum (v2.3.3
   * item 80: ColorOS's gesture area is wider than the nominal
   * navigation bar height). A bubble docked inside the strip
   * sits under the user's home swipe and eats it, so dock
   * targets clamp above it. A bubble being dragged may pass
   * through the strip; only the docked position is clamped.
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

  /** The lowest Y a docked bubble may occupy (item 80). */
  private fun maxDockY(): Int {
    val size = dp(BUBBLE_SIZE_DP)
    return (resources.displayMetrics.heightPixels - size -
      bottomGestureExclusionPx()).coerceAtLeast(0)
  }

  /**
   * Whether the dragged bubble is over the trash target, tested
   * in real screen coordinates (v2.3.2 item 74): both views are
   * separate overlay windows, and [View.getLocationOnScreen]
   * reports both in the one display space, so insets and window
   * origins cannot skew the test the way the old params-vs-
   * displayMetrics math did.
   */
  private fun isOverTrash(item: BubbleItem): Boolean {
    val trash = trashView ?: return false
    if (!isTrashShown) return false
    val bubbleLoc = IntArray(2)
    val trashLoc = IntArray(2)
    item.view.getLocationOnScreen(bubbleLoc)
    trash.getLocationOnScreen(trashLoc)
    val bubbleCx = bubbleLoc[0] + dp(BUBBLE_SIZE_DP) / 2.0
    val bubbleCy = bubbleLoc[1] + dp(BUBBLE_SIZE_DP) / 2.0
    // The visible circle is centered in its (larger) window, so
    // the window's real center IS the circle's center (v2.3.3
    // item 82); use the measured size, not the circle size.
    val trashCx = trashLoc[0] + trash.width / 2.0
    val trashCy = trashLoc[1] + trash.height / 2.0
    return hypot(bubbleCx - trashCx, bubbleCy - trashCy) <= dp(140)
  }

  /** Highlights the trash target (red + haptic) while a bubble hovers it. */
  private fun setTrashHot(hot: Boolean, anchor: View) {
    if (trashHot == hot) return
    trashHot = hot
    val drawable = trashCircleView?.background as? GradientDrawable
    drawable?.setColor(
      if (hot) {
        0xE6FF3B30.toInt()
      } else {
        glassViewColor(
          true,
          LinkPreferences.isGlassEnabled(this),
          LinkPreferences.getGlassStrength(this)
        )
      }
    )
    // The target grows while hot, matching the enlarged hit
    // area. The scale is applied to the inner circle view, whose
    // window carries TRASH_PAD_DP of slack on every side, so
    // the grown circle never clips against the window surface
    // the way scaling the window-sized view did (v2.3.3
    // item 82).
    trashCircleView?.animate()
      ?.scaleX(if (hot) 1.18f else 1f)
      ?.scaleY(if (hot) 1.18f else 1f)
      ?.setDuration(150)
      ?.start()
    if (hot) {
      try {
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      } catch (_: Exception) {
      }
    }
  }

  private fun showTrash() {
    if (isTrashShown) return
    // v2.3.3 (item 82): the window is larger than the visible
    // circle by TRASH_PAD_DP on every side. Up to v2.3.2 the
    // window was exactly the circle's size and setTrashHot
    // scaled the window's own view to 1.18x, so the grown
    // circle's rim was clipped by the window surface. The
    // circle now lives in a centered inner view and the scale
    // applies to it, inside the padded window.
    val circleSize = dp(TRASH_SIZE_DP)
    val windowSize = circleSize + dp(TRASH_PAD_DP) * 2
    val view = trashView ?: FrameLayout(this).apply {
      val circle = FrameLayout(this@BubbleService).apply {
        background = GradientDrawable().apply {
          shape = GradientDrawable.OVAL
          setColor(
            glassViewColor(
              true,
              LinkPreferences.isGlassEnabled(this@BubbleService),
              LinkPreferences.getGlassStrength(this@BubbleService)
            )
          )
          setStroke(dp(1), 0x66FFFFFF)
        }
        layoutParams = FrameLayout.LayoutParams(circleSize, circleSize).apply {
          gravity = Gravity.CENTER
        }
        val icon = ImageView(this@BubbleService).apply {
          setImageResource(R.drawable.ic_ov_close)
          layoutParams = FrameLayout.LayoutParams(dp(26), dp(26)).apply {
            gravity = Gravity.CENTER
          }
        }
        addView(icon)
      }
      trashCircleView = circle
      addView(circle)
    }.also { trashView = it }

    val params = WindowManager.LayoutParams(
      windowSize,
      windowSize,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      // The circle is centered in the padded window, so its
      // bottom edge sits TRASH_PAD_DP above the window's;
      // lowering the window by the pad keeps the visible
      // circle exactly where it was (24dp above the bottom).
      y = dp(24) - dp(TRASH_PAD_DP)
    }

    try {
      windowManager.addView(view, params)
      isTrashShown = true
    } catch (_: Exception) {
    }
  }

  private fun hideTrash() {
    if (!isTrashShown) return
    val view = trashView ?: return
    try {
      windowManager.removeView(view)
    } catch (_: Exception) {
    }
    isTrashShown = false
  }

  private fun openBubble(item: BubbleItem) {
    val size = dp(BUBBLE_SIZE_DP)
    val fromRect = intArrayOf(item.params.x, item.params.y, size, size)
    removeBubble(item)
    // Tapping a bubble opens the overlay window directly, staying on
    // top of the current app instead of switching to a full activity.
    // The window grows out of the bubble along a curve (v2.3.0).
    if (OverlayService.open(this, item.url, secret = item.secret, fromRect = fromRect)) {
      return
    }
    val intent = BrowserActivity.createDirectIntent(this, item.url, item.secret)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
  }

  private fun openPreview(item: BubbleItem) {
    // Long press: peek at the page in a short overlay window. The
    // bubble stays, so closing the preview returns to just the bubble.
    if (!LinkPreferences.isPreviewEnabled(this)) {
      openBubble(item)
      return
    }
    val size = dp(BUBBLE_SIZE_DP)
    val fromRect = intArrayOf(item.params.x, item.params.y, size, size)
    if (!OverlayService.open(
        this,
        item.url,
        peek = true,
        secret = item.secret,
        fromRect = fromRect
      )
    ) {
      openBubble(item)
    }
  }

  private fun removeBubble(item: BubbleItem, animate: Boolean = false) {
    item.animator?.cancel()
    item.animator = null
    bubbles.remove(item)
    relayoutBubbles()
    if (animate &&
      LinkPreferences.getAnimLevel(this) != LinkPreferences.ANIM_OFF
    ) {
      // Shrink away before the view leaves the window (item 60).
      val wasLast = bubbles.isEmpty()
      item.view.animate()
        .scaleX(0.3f)
        .scaleY(0.3f)
        .alpha(0f)
        .setDuration(220)
        .setInterpolator(IosSpringInterpolator(1f, 0.2f, 220))
        .withEndAction {
          try {
            windowManager.removeView(item.view)
          } catch (_: Exception) {
          }
          if (wasLast) {
            stopSelf()
          }
        }
        .start()
      if (!wasLast) {
        updateNotification()
      }
      return
    }
    try {
      windowManager.removeView(item.view)
    } catch (_: Exception) {
    }
    if (bubbles.isEmpty()) {
      stopSelf()
    } else {
      updateNotification()
    }
  }

  private fun clearAllBubbles() {
    for (item in bubbles) {
      item.animator?.cancel()
      item.animator = null
      try {
        windowManager.removeView(item.view)
      } catch (_: Exception) {
      }
    }
    bubbles.clear()
  }

  private fun createNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Web heads",
      NotificationManager.IMPORTANCE_LOW
    )
    manager.createNotificationChannel(channel)
  }

  private fun updateNotification() {
    val contentText = if (bubbles.size == 1) {
      bubbles[0].url
    } else {
      getString(R.string.bubbles_count, bubbles.size)
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, HomeActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE
    )
    val notification = Notification.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle("Web heads")
      .setContentText(contentText)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  companion object {
    const val EXTRA_URL = "arun.com.chromer.bubbles.EXTRA_URL"
    const val EXTRA_SECRET = "arun.com.chromer.bubbles.EXTRA_SECRET"
    private const val ACTION_ADD = "arun.com.chromer.bubbles.ACTION_ADD"
    private const val ACTION_CLEAR = "arun.com.chromer.bubbles.ACTION_CLEAR"

    private const val LONG_PRESS_MS = 550L

    private const val MAX_BUBBLES = 5
    private const val BUBBLE_SIZE_DP = 56
    private const val TRASH_SIZE_DP = 72
    // Slack around the trash circle inside its window, so the
    // hot-state scale never clips (v2.3.3 item 82).
    private const val TRASH_PAD_DP = 20
    private const val CHANNEL_ID = "web_heads"
    private const val NOTIFICATION_ID = 1002

    private val BUBBLE_PALETTE = intArrayOf(
      0xFFEF5350.toInt(),
      0xFFAB47BC.toInt(),
      0xFF5C6BC0.toInt(),
      0xFF29B6F6.toInt(),
      0xFF26A69A.toInt(),
      0xFF9CCC65.toInt(),
      0xFFFFCA28.toInt(),
      0xFFFF7043.toInt()
    )

    fun open(context: Context, url: String, secret: Boolean = false): Boolean {
      if (!OverlayService.canDrawOverlays(context)) return false
      return try {
        context.startForegroundService(
          Intent(context, BubbleService::class.java)
            .setAction(ACTION_ADD)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_SECRET, secret)
        )
        true
      } catch (_: Exception) {
        false
      }
    }

    fun clear(context: Context) {
      try {
        context.startService(
          Intent(context, BubbleService::class.java).setAction(ACTION_CLEAR)
        )
      } catch (_: Exception) {
      }
    }
  }
}

