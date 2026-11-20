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

package arun.com.chromer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.home.HomeActivity
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The four main destinations, in bar order (v2.3.0). */
val HirakuTabLabels = listOf(
  R.string.nav_home,
  R.string.nav_apps,
  R.string.nav_history,
  R.string.nav_settings
)

val HirakuTabIcons = listOf(
  Icons.Filled.Home,
  Icons.Filled.Apps,
  Icons.Filled.History,
  Icons.Filled.Settings
)

/** Opens the main screen on the given tab (used by secondary screens). */
fun openHomeTab(context: Context, index: Int) {
  val intent = Intent(context, HomeActivity::class.java).apply {
    putExtra(HomeActivity.EXTRA_TAB, index)
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (context !is Activity) {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }
  try {
    context.startActivity(intent)
  } catch (_: Exception) {
  }
}

/**
 * The resident floating bottom bar (v2.3.0): four evenly spaced
 * items with the icon above the label, a Liquid Glass body, and a
 * gooey highlight that springs between items — or follows the
 * finger while the bar is dragged sideways; releasing picks the
 * item under the finger. The bar itself never leaves the screen;
 * screens crossfade/slide underneath it.
 */
@Composable
fun HirakuBottomBar(
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  val fast = remember { Animatable(selectedIndex.toFloat()) }
  val slow = remember { Animatable(selectedIndex.toFloat()) }
  var dragging by remember { mutableStateOf(false) }

  LaunchedEffect(selectedIndex) {
    if (!dragging) {
      // The liquid highlight mostly keeps its shape while it
      // slides (v2.3.1 items 59/66): the leading edge springs with
      // a hint of overshoot, the trailing edge follows without
      // overshoot, and the render caps how far the pill may
      // stretch (see below).
      val fastSpec = when (animLevel) {
        LinkPreferences.ANIM_OFF -> tween<Float>(durationMillis = 1)
        LinkPreferences.ANIM_WEAK -> tween(durationMillis = 160, easing = OccEasing)
        else -> spring(
          dampingRatio = 0.82f,
          stiffness = Spring.StiffnessMedium
        )
      }
      val slowSpec = when (animLevel) {
        LinkPreferences.ANIM_OFF -> tween<Float>(durationMillis = 1)
        LinkPreferences.ANIM_WEAK -> tween(durationMillis = 240, easing = OccEasing)
        else -> spring(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
      }
      launch { fast.animateTo(selectedIndex.toFloat(), fastSpec) }
      launch { slow.animateTo(selectedIndex.toFloat(), slowSpec) }
    }
  }

  val displayIndex = if (dragging) {
    fast.value.roundToInt().coerceIn(0, HirakuTabLabels.lastIndex)
  } else {
    selectedIndex
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 10.dp)
  ) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .shadow(14.dp, shape, clip = false)
        .glassBackground(shape)
    ) {
      val itemWidth = maxWidth / HirakuTabLabels.size
      val density = LocalDensity.current
      val itemWidthPx = with(density) { itemWidth.toPx() }
      val pillColor = MaterialTheme.colorScheme.primary

      // Liquid highlight (v2.3.1): one pill whose position is the
      // average of a fast and a slow edge. The trailing edge's lag
      // widens the pill a little while it travels, but the stretch
      // is capped (about 1.15x in normal moves, never past 1.3x)
      // so the pill keeps its shape and reads as sliding glass,
      // not as a stretching blob. The gooey render effect only
      // softens the pill's rim.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .then(gooeyLayerModifier(animLevel == LinkPreferences.ANIM_STRONG))
      ) {
        val center = (fast.value + slow.value) / 2f
        val span = kotlin.math.abs(fast.value - slow.value)
        val stretch = minOf(span * 0.45f, 0.30f)
        val pillWidth = itemWidth * (1f + stretch) - 8.dp
        Box(
          modifier = Modifier
            .offset(
              x = itemWidth * center + itemWidth / 2 - pillWidth / 2,
              y = 4.dp
            )
            .width(pillWidth)
            .height(56.dp)
            .background(pillColor, RoundedCornerShape(50))
        )
        Box(
          modifier = Modifier
            .offset(
              x = itemWidth * slow.value + itemWidth / 2 - 12.dp,
              y = 20.dp
            )
            .size(24.dp)
            .background(pillColor, CircleShape)
        )
      }

      Row(modifier = Modifier.fillMaxSize()) {
        HirakuTabLabels.forEachIndexed { index, labelRes ->
          val selected = index == displayIndex
          Box(
            modifier = Modifier
              .width(itemWidth)
              .fillMaxHeight(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = HirakuTabIcons[index],
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) {
                  MaterialTheme.colorScheme.onPrimary
                } else {
                  GlassContentSecondary
                }
              )
              Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                  MaterialTheme.colorScheme.onPrimary
                } else {
                  GlassContentSecondary
                }
              )
            }
          }
        }
      }

      // One gesture handler for the whole bar (v2.3.1 item 58):
      // the v2.3.0 split — clickable items under a drag-only
      // overlay — swallowed plain taps, so only drags switched
      // tabs. Now a tap selects the item under the finger, and a
      // sideways drag makes the liquid follow the finger and
      // selects the item it is released over.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              val startX = down.position.x
              var isDrag = false
              var released = false
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) {
                  released = true
                  break
                }
                change.consume()
                if (!isDrag &&
                  kotlin.math.abs(change.position.x - startX).toFloat() >
                  viewConfiguration.touchSlop
                ) {
                  isDrag = true
                  dragging = true
                }
                if (isDrag) {
                  val target = (change.position.x / itemWidthPx - 0.5f)
                    .coerceIn(0f, HirakuTabLabels.lastIndex.toFloat())
                  fast.snapTo(target)
                  // Trailing edge: low-pass follow, so the pill
                  // stretches only a little while travelling.
                  slow.snapTo(slow.value + (target - slow.value) * 0.38f)
                }
              }
              if (!released) {
                // Gesture cancelled: settle back.
                dragging = false
                fast.animateTo(
                  selectedIndex.toFloat(),
                  spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                )
                slow.animateTo(
                  selectedIndex.toFloat(),
                  spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                  )
                )
              } else if (!isDrag) {
                val index = (startX / itemWidthPx).toInt()
                  .coerceIn(0, HirakuTabLabels.lastIndex)
                if (index != selectedIndex) {
                  onSelect(index)
                }
              } else {
                val target = fast.value.roundToInt()
                  .coerceIn(0, HirakuTabLabels.lastIndex)
                dragging = false
                if (target != selectedIndex) {
                  onSelect(target)
                } else {
                  fast.animateTo(
                    selectedIndex.toFloat(),
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                  )
                  slow.animateTo(
                    selectedIndex.toFloat(),
                    spring(
                      dampingRatio = Spring.DampingRatioNoBouncy,
                      stiffness = Spring.StiffnessMediumLow
                    )
                  )
                }
              }
            }
          }
      )
    }
  }
}

/** Blur + alpha contrast, the classic gooey merge (API 31+). */
fun gooeyLayerModifier(enabled: Boolean): Modifier {
  if (!enabled || Build.VERSION.SDK_INT < 31) return Modifier
  val effect = try {
    val blur = android.graphics.RenderEffect.createBlurEffect(
      10f,
      10f,
      android.graphics.Shader.TileMode.CLAMP
    )
    val matrix = android.graphics.ColorMatrix(
      floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 30f, -3600f
      )
    )
    val contrast = android.graphics.RenderEffect.createColorFilterEffect(
      android.graphics.ColorMatrixColorFilter(matrix)
    )
    android.graphics.RenderEffect.createChainEffect(contrast, blur)
  } catch (_: Exception) {
    null
  }
  return if (effect == null) {
    Modifier
  } else {
    Modifier.graphicsLayer { renderEffect = effect.asComposeRenderEffect() }
  }
}
