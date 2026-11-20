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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import arun.com.chromer.R
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.home.HomeActivity
import arun.com.chromer.overlay.OverlayService
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
    var badgeLetter: TextView? = null
  )

  private lateinit var windowManager: WindowManager
  private val bubbles = mutableListOf<BubbleItem>()
  // Top edge of the bubble stack; restored from the remembered
  // position when the memory setting is on (v2.1.0).
  private var stackBaseY: Int = -1
  private var trashView: View? = null
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
        gravity = Gravity.BOTTOM or Gravity.END
      }
    }
    val view = FrameLayout(this).apply {
      this.background = background
      elevation = dp(4).toFloat()
      addView(letterView)
      addView(badgeLetter)
    }

    val params = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      val remember = LinkPreferences.isBubbleRememberPos(this@BubbleService)
      x = if (remember && LinkPreferences.getBubbleSide(this@BubbleService) == 0) {
        0
      } else {
        screenWidth - size
      }
      y = stackBaseY + bubbles.size * (size + dp(8))
    }

    val item = BubbleItem(
      url = url,
      view = view,
      params = params,
      secret = secret,
      background = background,
      letterView = letterView,
      badgeLetter = badgeLetter
    )
    setupTouchListener(item)
    bubbles.add(item)
    windowManager.addView(view, params)
    relayoutBubbles()
    updateNotification()
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
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          handler.removeCallbacks(longPressRunnable)
          hideTrash()
          if (longPressed) {
            // Preview already opened; keep the bubble where it is.
          } else if (!moved) {
            openBubble(item)
          } else {
            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val bubbleCenterX = item.params.x + size / 2
            val bubbleCenterY = item.params.y + size / 2
            val trashCenterX = screenWidth / 2
            val trashCenterY = screenHeight - dp(24) - dp(TRASH_SIZE_DP) / 2
            val distance = hypot(
              (bubbleCenterX - trashCenterX).toDouble(),
              (bubbleCenterY - trashCenterY).toDouble()
            )
            if (distance <= dp(80)) {
              removeBubble(item)
            } else {
              val toLeft = bubbleCenterX < screenWidth / 2
              item.params.x = if (toLeft) 0 else screenWidth - size
              try {
                windowManager.updateViewLayout(item.view, item.params)
              } catch (_: Exception) {
              }
              if (LinkPreferences.isBubbleRememberPos(this@BubbleService)) {
                if (bubbles.firstOrNull() === item) {
                  stackBaseY = item.params.y
                }
                LinkPreferences.saveBubblePosition(
                  this@BubbleService,
                  if (toLeft) 0 else 1,
                  stackBaseY
                )
              }
            }
          }
          true
        }
        MotionEvent.ACTION_CANCEL -> {
          handler.removeCallbacks(longPressRunnable)
          hideTrash()
          true
        }
        else -> false
      }
    }
  }

  private fun relayoutBubbles() {
    val size = dp(BUBBLE_SIZE_DP)
    val baseY = if (stackBaseY >= 0) stackBaseY else dp(120)
    bubbles.forEachIndexed { index, item ->
      item.params.y = baseY + index * (size + dp(8))
      try {
        windowManager.updateViewLayout(item.view, item.params)
      } catch (_: Exception) {
      }
    }
  }

  private fun showTrash() {
    if (isTrashShown) return
    val size = dp(TRASH_SIZE_DP)
    val view = trashView ?: TextView(this).apply {
      text = "✕"
      setTextColor(Color.WHITE)
      textSize = 28f
      gravity = Gravity.CENTER
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xCC424242.toInt())
      }
    }.also { trashView = it }

    val params = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      y = dp(24)
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
    removeBubble(item)
    // Tapping a bubble opens the overlay window directly, staying on
    // top of the current app instead of switching to a full activity.
    if (OverlayService.open(this, item.url, secret = item.secret)) {
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
    if (!OverlayService.open(this, item.url, peek = true, secret = item.secret)) {
      openBubble(item)
    }
  }

  private fun removeBubble(item: BubbleItem) {
    try {
      windowManager.removeView(item.view)
    } catch (_: Exception) {
    }
    bubbles.remove(item)
    relayoutBubbles()
    if (bubbles.isEmpty()) {
      stopSelf()
    } else {
      updateNotification()
    }
  }

  private fun clearAllBubbles() {
    for (item in bubbles) {
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

