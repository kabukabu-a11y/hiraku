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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
 * The resident floating bottom bar: four evenly spaced items with
 * the icon above the label and a Liquid Glass body.
 *
 * v2.3.2 (items 72/78): rebuilt on the mechanics of Glasmio's tab
 * bar — the one MeiloX ships — with the values ported from the
 * Glasmio 0.1.3 sources instead of the v2.3.1 in-house two-edge
 * gooey approximation:
 *  - one value [Animatable] carries the indicator, settled with
 *    DampedDragAnimation's spring(1f, 1000f); while dragging it is
 *    re-targeted at the finger with the same spring, so the pill
 *    trails the finger with UIKit's damped feel;
 *  - the indicator is a glass capsule — white at ~0.10 alpha with
 *    a light edge (GlassBottomBar's onDrawSurface base) — not the
 *    solid theme primary the v2.3.1 bar used;
 *  - travelling deforms the capsule by velocity exactly like
 *    GlassBottomBar's indicator layer: scaleX divides by
 *    (1 − term), scaleY multiplies by (1 − term/3), the term
 *    capped at ±0.2; pressing lifts it with the press spring
 *    (1f, 1000f) and the X/Y scale springs (0.6f/0.7f, 250f).
 *    Only the press target is moderated (1.06 instead of
 *    Glasmio's 78/56) because the user capped how far the pill
 *    may deform (item 59 follow-up).
 * The bar itself never leaves the screen; screens crossfade/slide
 * underneath it.
 */
@Composable
fun HirakuBottomBar(
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  // v2.3.6 trial: when true, the bar body itself is rendered by
  // a LiquidGlassView layered underneath this composition (see
  // HomeActivity's layered setup); this composition then draws
  // only the shadow, the pill and the items.
  externalGlass: Boolean = false,
  // v2.3.9 (item 96): reports the indicator's position (in
  // tab units) on every frame it changes, so the layered
  // setup can slide a second liquid-glass view under the
  // pill and the refraction follows the pill's travel.
  onPillPosition: ((Float) -> Unit)? = null
) {
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  val glassOn = glassEnabled()
  val scope = rememberCoroutineScope()
  val position = remember { Animatable(selectedIndex.toFloat()) }
  if (onPillPosition != null) {
    LaunchedEffect(onPillPosition) {
      snapshotFlow { position.value }.collect { onPillPosition(it) }
    }
  }
  val pressProgress = remember { Animatable(0f) }
  val scaleX = remember { Animatable(1f) }
  val scaleY = remember { Animatable(1f) }
  var dragging by remember { mutableStateOf(false) }

  // v2.3.5 (item 80 — the actual root cause, verified with an
  // external code review): the gesture handler below lives in
  // pointerInput(Unit). With key Unit the pointer-input element
  // is never restarted, so the handler keeps the lambda from the
  // FIRST composition forever — and selectedIndex is a plain Int
  // captured by value. It stayed at the tab the app started on
  // (Home, 0), so after leaving Home once, every Home tap was
  // judged "already selected" (0 != 0 is false) and discarded:
  // the pill still reacted to presses and drags, but the screen
  // never switched back, and only the start tab was affected.
  // All values the handler reads now go through updated-state
  // holders so it always sees the latest ones.
  val latestSelectedIndex by rememberUpdatedState(selectedIndex)
  val latestOnSelect by rememberUpdatedState(onSelect)

  fun valueSpec(): AnimationSpec<Float> = when (animLevel) {
    LinkPreferences.ANIM_OFF -> snap()
    LinkPreferences.ANIM_WEAK -> tween(200, easing = OccEasing)
    else -> spring(
      dampingRatio = IosMotion.VALUE_DAMPING,
      stiffness = IosMotion.VALUE_STIFFNESS,
      visibilityThreshold = 0.001f
    )
  }

  fun settleTo(index: Int) {
    scope.launch { position.animateTo(index.toFloat(), valueSpec()) }
  }

  fun pressIn() {
    if (animLevel == LinkPreferences.ANIM_OFF) return
    scope.launch {
      pressProgress.animateTo(
        1f,
        spring(
          dampingRatio = IosMotion.PRESS_DAMPING,
          stiffness = IosMotion.PRESS_STIFFNESS,
          visibilityThreshold = 0.001f
        )
      )
    }
    scope.launch {
      scaleX.animateTo(
        PRESS_SCALE,
        spring(
          dampingRatio = IosMotion.SCALE_X_DAMPING,
          stiffness = IosMotion.SCALE_X_STIFFNESS,
          visibilityThreshold = 0.001f
        )
      )
    }
    scope.launch {
      scaleY.animateTo(
        PRESS_SCALE,
        spring(
          dampingRatio = IosMotion.SCALE_Y_DAMPING,
          stiffness = IosMotion.SCALE_Y_STIFFNESS,
          visibilityThreshold = 0.001f
        )
      )
    }
  }

  fun pressOut() {
    if (animLevel == LinkPreferences.ANIM_OFF) return
    // Release uses the TabBarIndicator unlift spring (1f,
    // 631.65468f) from DampedDragAnimation's native lens specs.
    val release = spring<Float>(
      dampingRatio = IosMotion.INDICATOR_DAMPING,
      stiffness = IosMotion.INDICATOR_STIFFNESS,
      visibilityThreshold = 0.001f
    )
    scope.launch { pressProgress.animateTo(0f, release) }
    scope.launch { scaleX.animateTo(1f, release) }
    scope.launch { scaleY.animateTo(1f, release) }
  }

  LaunchedEffect(selectedIndex) {
    if (!dragging) {
      position.animateTo(selectedIndex.toFloat(), valueSpec())
    }
  }

  val displayIndex = position.value.roundToInt()
    .coerceIn(0, HirakuTabLabels.lastIndex)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 8.dp)
  ) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .height(62.dp)
        .shadow(18.dp, shape, clip = false)
        .then(
          if (externalGlass) {
            Modifier.background(Color.Transparent, shape)
          } else {
            Modifier.glassBackground(shape)
          }
        )
    ) {
      val itemWidth = maxWidth / HirakuTabLabels.size
      val density = LocalDensity.current
      val itemWidthPx = with(density) { itemWidth.toPx() }
      // Same freeze problem as selectedIndex: the gesture
      // handler must read the current width (rotations, multi-
      // window), not the first composition's.
      val latestItemWidthPx by rememberUpdatedState(itemWidthPx)
      // Glasmio geometry: 4dp content padding around the
      // indicator (GlasmioTabBarDefaults.ContentPadding).
      val pillWidth = itemWidth - 8.dp

      // Glass indicator capsule (see the header note). The
      // velocity squash mirrors GlassBottomBar's layer math:
      // termX = clamp(v * 0.75), termY = clamp(v * 0.25) of the
      // same normalized velocity, both capped at ±0.2; v is the
      // indicator's speed normalized so a fast cross-bar fling
      // (~2400 px/s) reaches the cap.
      val velocityPx = position.velocity * itemWidthPx
      // v2.3.5 (item 90): the squash terms are magnitude-only.
      // With the old signed terms, moving LEFT made termY
      // negative, so scaleY GREW (up to 1.06 * 1.2) instead of
      // shrinking: the pill (56dp at the time, 54dp since
      // v2.3.8) swelled to ~71dp inside the bar (64dp then,
      // 62dp now) and its white edge poked out of the bar's top
      // and bottom — the "white line" seen when pressing or
      // travelling. The pill now stretches along X and thins
      // along Y in both directions.
      val vNorm = kotlin.math.abs(velocityPx) / 8000f
      val termX = (vNorm * 0.75f)
        .coerceAtMost(IosMotion.VELOCITY_SQUASH_CAP)
      val termY = (vNorm * 0.25f)
        .coerceAtMost(IosMotion.VELOCITY_SQUASH_CAP)
      val indicatorShape = RoundedCornerShape(50)
      Box(
        modifier = Modifier
          .offset(
            x = itemWidth * position.value + itemWidth / 2 - pillWidth / 2,
            y = 4.dp
          )
          .width(pillWidth)
          .height(54.dp)
          .graphicsLayer {
            this.scaleX = scaleX.value / (1f - termX)
            this.scaleY = scaleY.value * (1f - termY)
          }
          .then(
            if (glassOn) {
              Modifier
                .background(
                  // v2.3.5 (item 90): a single flat fill, and
                  // the 1dp white border is gone. The border
                  // double-composited with the fill into a
                  // ~0.20-alpha white outline, and the press
                  // scale made that outline swell and travel —
                  // the other half of the "white line" seen on
                  // press (the squash fix above is the rest).
                  // v2.3.3's gradient flattening (item 84) was
                  // not enough because the border remained.
                  Color.White.copy(alpha = 0.12f),
                  indicatorShape
                )
            } else {
              Modifier.background(
                MaterialTheme.colorScheme.primary,
                indicatorShape
              )
            }
          )
      )

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
                  if (glassOn) GlassContent else MaterialTheme.colorScheme.onPrimary
                } else {
                  if (glassOn) {
                    GlassContentSecondary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  }
                }
              )
              Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                  if (glassOn) GlassContent else MaterialTheme.colorScheme.onPrimary
                } else {
                  if (glassOn) {
                    GlassContentSecondary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  }
                }
              )
            }
          }
        }
      }

      // One gesture handler for the whole bar (v2.3.1 item 58):
      // a tap selects the item under the finger, and a sideways
      // drag makes the liquid follow the finger and selects the
      // item it is released over.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            awaitEachGesture {
              // v2.3.5: every value read here goes through the
              // latest* holders (see the note at their
              // declaration): this block is created once and
              // never restarted, so direct reads of
              // selectedIndex / onSelect / itemWidthPx would be
              // frozen at the first composition — the item 80
              // bug. The try/finally also guarantees the bar
              // never stays stuck in the pressed/dragging state
              // if the gesture coroutine is cancelled mid-press.
              try {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startX = down.position.x
                var lastX = startX
                var isDrag = false
                var released = false
                pressIn()
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull() ?: break
                  if (!change.pressed) {
                    released = true
                    break
                  }
                  change.consume()
                  lastX = change.position.x
                  if (!isDrag &&
                    kotlin.math.abs(change.position.x - startX).toFloat() >
                    viewConfiguration.touchSlop
                  ) {
                    isDrag = true
                    dragging = true
                  }
                  if (isDrag) {
                    val target = (change.position.x / latestItemWidthPx - 0.5f)
                      .coerceIn(0f, HirakuTabLabels.lastIndex.toFloat())
                    // AwaitPointerEventScope is a restricted
                    // suspension scope: Animatable calls must run in
                    // a real coroutine, not in the gesture block.
                    scope.launch {
                      position.animateTo(target, valueSpec())
                    }
                  }
                }
                pressOut()
                if (!released) {
                  // Gesture cancelled: settle back.
                  dragging = false
                  settleTo(latestSelectedIndex)
                } else if (!isDrag) {
                  val index = (startX / latestItemWidthPx).toInt()
                    .coerceIn(0, HirakuTabLabels.lastIndex)
                  if (index != latestSelectedIndex) {
                    latestOnSelect(index)
                  }
                } else {
                  // Judge the drop by where the FINGER is, not
                  // by the still-travelling pill position: a
                  // fast swipe used to snap back because the
                  // spring had not carried the pill past the
                  // halfway point yet.
                  val target = (lastX / latestItemWidthPx - 0.5f).roundToInt()
                    .coerceIn(0, HirakuTabLabels.lastIndex)
                  dragging = false
                  if (target != latestSelectedIndex) {
                    latestOnSelect(target)
                  } else {
                    settleTo(latestSelectedIndex)
                  }
                }
              } finally {
                dragging = false
                pressOut()
              }
            }
          }
      )
    }
  }
}

/**
 * Press lift of the indicator. Glasmio's tab animation uses
 * 78f/56f here; the user's shape-keeping cap (item 59 follow-up)
 * wins, so the lift is a gentle 1.06 and the travelling squash
 * (capped at ±0.2 like Glasmio) supplies the liquid feel.
 */
private const val PRESS_SCALE = 1.06f
