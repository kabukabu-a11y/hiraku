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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import arun.com.chromer.customtab.LinkPreferences

/**
 * Liquid Glass building blocks (v2.3.0, re-based in v2.3.2 item
 * 78). The original plan was to use the Glasmio library like MeiloX
 * does, but Glasmio 0.1.3 is built for the Kotlin 2.x / Compose 1.9+
 * toolchain and cannot be resolved against this project's
 * Kotlin 1.9 / Compose 1.5 setup, so the glass material is
 * implemented in-house instead: translucent tint, a light-catching
 * gradient border and — where the window allows it (chooser sheet,
 * overlay windows) — a real backdrop blur from the platform
 * (FLAG_BLUR_BEHIND / background blur radius on API 31+).
 * Everything degrades to a plain solid surface when the user turns
 * the glass effect off.
 *
 * v2.3.2: the values are no longer in-house guesses. They are
 * ported from the actual Glasmio 0.1.3 sources (the version MeiloX
 * pins) — see [IosMotion] and the glass parameter notes below.
 * What could NOT be ported is Glasmio's rendering pipeline itself
 * (backdrop capture + AGSL lens/refraction shaders), which needs
 * the newer Compose runtime; the tints, alphas, geometry and
 * spring constants below are the real measured values.
 */

/** The yellow used for ON toggles so the on state stands out. */
val ToggleOnYellow = Color(0xFFFFD60A)

private val CheckBlue = Color(0xFF0A84FF)

/**
 * OCC motion curve (ColorOS-style, v2.3.1 item 66): a fast attack
 * with a long, gentle settle. Used for Compose animations; the
 * View-based windows use [occInterpolator] with the same control
 * points.
 */
val OccEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** The same OCC curve as a platform interpolator. */
fun occInterpolator(): Interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)

/**
 * Motion constants ported from Glasmio 0.1.3 — the library MeiloX
 * is built on (v2.3.2 items 78/78b: prefer the Apple-side values).
 * Sources:
 *  - glasmio-glass `DampedDragAnimation.kt`: the drag/value/press
 *    springs of the tab bar and native controls.
 *  - glasmio-glass `InteractiveHighlight.kt`: the press highlight
 *    spring.
 *  - glasmio-glass `LiquidMenuMorphMotion.kt`: iOS-style
 *    (dampingRatio, response) spring specs; a response converts to
 *    a Compose stiffness with (2π / response)².
 *  - glasmio-scaffold `SheetMotion.kt` (GlasmioSheetDefaults): the
 *    sheet spring, damping 1 / stiffness 300.
 */
object IosMotion {
  /** Tab value spring: spring(1f, 1000f) in DampedDragAnimation. */
  const val VALUE_DAMPING = 1f
  const val VALUE_STIFFNESS = 1000f

  /** Press progress spring: spring(1f, 1000f, 0.001f), same source. */
  const val PRESS_DAMPING = 1f
  const val PRESS_STIFFNESS = 1000f

  /** Indicator lift/unlift (TabBarIndicator lens): spring(1f, 631.65468f, 0.001f). */
  const val INDICATOR_DAMPING = 1f
  const val INDICATOR_STIFFNESS = 631.65468f

  /** Segmented indicator lift: also spring(1f, 631.65468f, 0.001f). */
  const val SEGMENTED_DAMPING = 1f
  const val SEGMENTED_STIFFNESS = 631.65468f

  /** Press highlight: spring(0.5f, 300f, 0.001f) in InteractiveHighlight. */
  const val HIGHLIGHT_DAMPING = 0.5f
  const val HIGHLIGHT_STIFFNESS = 300f

  /** Indicator scale springs while pressed: X spring(0.6f, 250f), Y spring(0.7f, 250f). */
  const val SCALE_X_DAMPING = 0.6f
  const val SCALE_X_STIFFNESS = 250f
  const val SCALE_Y_DAMPING = 0.7f
  const val SCALE_Y_STIFFNESS = 250f

  /** UIKit switch thumb: lift spring(.625f, 541.54208f), unlift spring(.7f, 157.91367f). */
  const val SWITCH_ON_DAMPING = 0.625f
  const val SWITCH_ON_STIFFNESS = 541.54208f
  const val SWITCH_OFF_DAMPING = 0.7f
  const val SWITCH_OFF_STIFFNESS = 157.91367f

  /** Sheet spring (GlasmioSheetDefaults): damping 1, stiffness 300. */
  const val SHEET_DAMPING = 1f
  const val SHEET_STIFFNESS = 300f

  /** Ready-made sheet specs: Float values and IntOffset slides. */
  val SHEET: AnimationSpec<Float> = spring(SHEET_DAMPING, SHEET_STIFFNESS)
  val SHEET_OFFSET: AnimationSpec<IntOffset> =
    spring(SHEET_DAMPING, SHEET_STIFFNESS)

  /** Velocity squash cap of the tab indicator layer: ±0.2 (GlassBottomBar). */
  const val VELOCITY_SQUASH_CAP = 0.2f

  /** iOS response (seconds) -> Compose stiffness: (2π / response)². */
  fun stiffnessForResponse(response: Float): Float {
    val omega = 2f * Math.PI.toFloat() / response
    return omega * omega
  }
}

/**
 * An iOS-style spring as a platform [Interpolator] for the
 * View-based windows (bubbles, overlay), where Compose springs do
 * not reach. The curve is the analytic damped-spring step response
 * for the given (dampingRatio, response) pair — the same spec form
 * Glasmio's LiquidMenuMorphMotion uses (e.g. EjectSpring is
 * (0.75, 0.5), BlurInSpring is (1, 0.3)). [durationMs] is the
 * window over which the spring is sampled; pick it near the
 * spring's settle time so the sampled end lands on 1.
 */
class IosSpringInterpolator(
  private val dampingRatio: Float,
  response: Float,
  private val durationMs: Long
) : Interpolator {
  private val omega = 2f * Math.PI.toFloat() / response
  private val alpha = dampingRatio * omega
  private val omegaD = if (dampingRatio < 1f) {
    omega * kotlin.math.sqrt(1f - dampingRatio * dampingRatio)
  } else {
    0f
  }

  override fun getInterpolation(input: Float): Float {
    if (input <= 0f) return 0f
    if (input >= 1f) return 1f
    val t = input * durationMs / 1000f
    return if (dampingRatio < 1f) {
      val envelope = kotlin.math.exp(-alpha * t)
      1f - envelope * (
        kotlin.math.cos(omegaD * t) +
          (alpha / omegaD) * kotlin.math.sin(omegaD * t)
        )
    } else {
      1f - kotlin.math.exp(-omega * t) * (1f + omega * t)
    }
  }
}

/**
 * The one Liquid Glass tint (v2.3.1 item 67): the glass material
 * keeps this exact color in light and dark mode alike — only the
 * strength setting changes how present it is. Content drawn on
 * glass uses the fixed light palette below so it stays readable
 * on the glass in either theme.
 *
 * v2.3.2: the base color is Glasmio's dark container color,
 * 0xFF1C1C1E (glasmio-core GlassTokens.defaultGlassColors).
 */
private val GlassTintBase = Color(0xFF1C1C1E)

/** Primary content color on glass surfaces (theme-independent). */
val GlassContent = Color(0xFFF5F5F7)

/**
 * Secondary content color on glass surfaces (theme-independent).
 * v2.3.2: Glasmio's dark secondaryContent, 0xB2EBEBF5.
 */
val GlassContentSecondary = Color(0xB2EBEBF5)

/** How present the glass material is at each strength level. */
private data class GlassParams(
  val tintAlpha: Float,
  val sheenAlpha: Float,
  val borderTopAlpha: Float
)

private fun glassParams(strength: Int): GlassParams {
  return when (strength) {
    LinkPreferences.GLASS_WEAK -> GlassParams(0.42f, 0.06f, 0.12f)
    LinkPreferences.GLASS_STRONG -> GlassParams(0.68f, 0.16f, 0.30f)
    // Medium = Glasmio's measured dark container (GlassTokens
    // .defaultGlassColors): 0xFF1C1C1E at alpha 0.54, with the
    // subtle stroke 0x2BFFFFFF (alpha 0.17) along the edge. The
    // v2.3.1 in-house values (alpha 0.76, border 0.62) read as a
    // solid dark slab next to MeiloX's bar (item 72).
    else -> GlassParams(0.54f, 0.10f, 0.17f)
  }
}

@Composable
fun glassEnabled(): Boolean {
  val context = LocalContext.current
  return remember { LinkPreferences.isGlassEnabled(context) }
}

@Composable
fun glassStrength(): Int {
  val context = LocalContext.current
  return remember { LinkPreferences.getGlassStrength(context) }
}

/**
 * Glass surface: translucent fill, a specular sheen across the top
 * and a gradient border that catches light at the top edge. The
 * tint color does not change between light and dark mode (item
 * 67). Falls back to the solid Material surface when glass is
 * disabled.
 */
@Composable
fun Modifier.glassBackground(
  shape: Shape,
  enabled: Boolean = glassEnabled(),
  dark: Boolean = isSystemInDarkTheme(),
  tint: Color? = null,
  strength: Int = glassStrength()
): Modifier {
  if (!enabled) {
    return this.background(MaterialTheme.colorScheme.surface, shape)
  }
  val params = glassParams(strength)
  val base = tint ?: GlassTintBase.copy(alpha = params.tintAlpha)
  val sheen = Brush.verticalGradient(
    listOf(
      Color.White.copy(alpha = params.sheenAlpha),
      Color.White.copy(alpha = 0f)
    )
  )
  val borderBrush = Brush.verticalGradient(
    listOf(
      Color.White.copy(alpha = params.borderTopAlpha),
      Color.White.copy(alpha = 0.10f)
    )
  )
  return this
    .background(base, shape)
    .background(sheen, shape)
    .border(1.dp, borderBrush, shape)
}

/**
 * Solid color to paint glass in the View-based overlay windows.
 * Same rule as the Compose glass: one tint for light and dark
 * (item 67); [dark] only picks the non-glass fallback.
 */
fun glassViewColor(
  dark: Boolean,
  glassOn: Boolean,
  strength: Int = LinkPreferences.GLASS_MEDIUM
): Int {
  if (!glassOn) {
    return if (dark) 0xFF202124.toInt() else 0xFFF2F2F7.toInt()
  }
  val alpha = when (strength) {
    LinkPreferences.GLASS_WEAK -> 0x73
    LinkPreferences.GLASS_STRONG -> 0xC8
    // Medium sits just above Glasmio's container alpha (0.54):
    // these surfaces float over the platform backdrop blur of
    // their window, so most of the glass read comes from the
    // blur, not from the tint (v2.3.2 item 78).
    else -> 0x99
  }
  return (alpha shl 24) or 0x1C1C1E
}

/**
 * MeiloX-style on/off toggle (v2.3.0). The knob springs across the
 * track; the ON state is a standout yellow so it is obvious at a
 * glance which features are active.
 */
@Composable
fun HirakuToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  val knobOffset by animateDpAsState(
    targetValue = if (checked) 22.dp else 2.dp,
    animationSpec = when (animLevel) {
      LinkPreferences.ANIM_OFF -> tween(durationMillis = 1)
      LinkPreferences.ANIM_WEAK -> tween(durationMillis = 160)
      // UIKit switch thumb springs, ported from Glasmio's
      // DampedDragAnimation (v2.3.2 item 78c): turning on uses the
      // lift spec, turning off the softer unlift spec.
      else -> spring(
        dampingRatio = if (checked) {
          IosMotion.SWITCH_ON_DAMPING
        } else {
          IosMotion.SWITCH_OFF_DAMPING
        },
        stiffness = if (checked) {
          IosMotion.SWITCH_ON_STIFFNESS
        } else {
          IosMotion.SWITCH_OFF_STIFFNESS
        }
      )
    },
    label = "toggleKnob"
  )
  Box(
    modifier = modifier
      .size(52.dp, 32.dp)
      .clip(CircleShape)
      .background(
        if (checked) {
          ToggleOnYellow
        } else {
          Color(0xFF787880).copy(alpha = 0.45f)
        }
      )
      .clickable { onCheckedChange(!checked) }
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.CenterStart)
        .offset(x = knobOffset)
        .size(28.dp)
        .shadow(2.dp, CircleShape)
        .background(Color.White, CircleShape)
    )
  }
}

/** One selectable row inside a [GlassSelectPopup]. */
data class GlassOption(
  val label: String,
  val icon: ImageVector? = null,
  /** Raster icon (e.g. an app icon for the download-app list, item 77). */
  val iconBitmap: ImageBitmap? = null
)

private val PopupRowHeight = 48.dp

/**
 * The Liquid Glass selection popup used for settings choices
 * (v2.3.0): dark glass, an icon per row, a blue check on the current
 * value, and a pill highlight that slides after the finger while
 * dragging — releasing selects the row under the finger. No divider
 * lines; the moving highlight is the separator.
 */
@Composable
fun GlassSelectPopup(
  options: List<GlassOption>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  onDismiss: () -> Unit,
  footer: (@Composable () -> Unit)? = null
) {
  if (options.isEmpty()) return
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  var shown by remember { mutableStateOf(animLevel == LinkPreferences.ANIM_OFF) }
  LaunchedEffect(Unit) { shown = true }
  var hoverIndex by remember { mutableStateOf<Int?>(null) }
  val displayIndex = (hoverIndex ?: selectedIndex).coerceIn(0, options.lastIndex)
  val density = LocalDensity.current
  val rowPx = with(density) { PopupRowHeight.toPx() }
  val pillOffset by animateDpAsState(
    targetValue = PopupRowHeight * displayIndex + 4.dp,
    animationSpec = when (animLevel) {
      LinkPreferences.ANIM_OFF -> tween(durationMillis = 1)
      LinkPreferences.ANIM_WEAK -> tween(durationMillis = 140, easing = OccEasing)
      // No overshoot: unlike the bottom bar's liquid, this pill
      // must stay inside the popup frame at all times (item 63).
      // The spec is Glasmio's TabBarIndicator lift spring —
      // critically damped, fast, no bounce (v2.3.2 item 78c).
      else -> spring(
        dampingRatio = IosMotion.INDICATOR_DAMPING,
        stiffness = IosMotion.INDICATOR_STIFFNESS
      )
    },
    label = "popupPill"
  )

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 40.dp),
      contentAlignment = Alignment.Center
    ) {
      AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(160)) + scaleIn(
          initialScale = 0.92f,
          animationSpec = spring(
            dampingRatio = IosMotion.SHEET_DAMPING,
            stiffness = IosMotion.SHEET_STIFFNESS
          )
        )
      ) {
        val shape = RoundedCornerShape(26.dp)
        Box(
          modifier = Modifier
            .width(300.dp)
            // Clip everything (above all the sliding pill) to the
            // popup's rounded frame (item 63).
            .clip(shape)
            .glassBackground(
              shape = shape,
              enabled = true,
              dark = true,
              tint = GlassTintBase.copy(alpha = 0.82f)
            )
        ) {
          // Sliding pill highlight behind the rows.
          Box(
            modifier = Modifier
              .offset(y = pillOffset)
              .padding(horizontal = 6.dp)
              .fillMaxWidth()
              .height(PopupRowHeight - 8.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color.White.copy(alpha = 0.14f))
          )
          Column(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .pointerInput(options.size) {
                var startY = 0f
                var totalDrag = 0f
                detectVerticalDragGestures(
                  onDragStart = { offset ->
                    startY = offset.y
                    totalDrag = 0f
                    hoverIndex = (offset.y / rowPx).toInt()
                      .coerceIn(0, options.lastIndex)
                  },
                  onVerticalDrag = { _, dragAmount ->
                    totalDrag += dragAmount
                    hoverIndex = ((startY + totalDrag) / rowPx).toInt()
                      .coerceIn(0, options.lastIndex)
                  },
                  onDragEnd = {
                    hoverIndex?.let { onSelect(it) }
                    hoverIndex = null
                  },
                  onDragCancel = { hoverIndex = null }
                )
              }
          ) {
            // When any row carries an icon, rows without one keep
            // an empty slot so the labels stay aligned (item 77:
            // the download-app list mixes the icon-less "system"
            // row with app rows).
            val anyIcons = options.any { it.iconBitmap != null || it.icon != null }
            options.forEachIndexed { index, option ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(PopupRowHeight)
                  .clickable {
                    onSelect(index)
                  }
                  .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (option.iconBitmap != null) {
                  androidx.compose.foundation.Image(
                    bitmap = option.iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                      .size(24.dp)
                      .clip(RoundedCornerShape(6.dp))
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                } else if (option.icon != null) {
                  Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                } else if (anyIcons) {
                  Box(modifier = Modifier.size(24.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                  text = option.label,
                  color = Color.White,
                  style = MaterialTheme.typography.bodyLarge,
                  modifier = Modifier.weight(1f)
                )
                if (index == selectedIndex) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = CheckBlue,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
          }
          if (footer != null) {
            footer()
          }
          }
        }
      }
    }
  }
}

/**
 * Top-of-screen switch (Apps / Sites, History / Bookmarks): a
 * glass pill with a highlight that slides between the segments.
 */
@Composable
fun GlassTabSwitch(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  val shape = RoundedCornerShape(50)
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .height(46.dp)
      .glassBackground(shape)
      .padding(4.dp)
  ) {
    val segmentWidth = (maxWidth - 8.dp) / options.size
    val offset by animateDpAsState(
      targetValue = segmentWidth * selectedIndex,
      animationSpec = if (animLevel == LinkPreferences.ANIM_OFF) {
        snap()
      } else {
        // Glasmio's SegmentedIndicator lift spring — critically
        // damped, no bounce (v2.3.2 item 78c).
        spring(
          dampingRatio = IosMotion.SEGMENTED_DAMPING,
          stiffness = IosMotion.SEGMENTED_STIFFNESS
        )
      },
      label = "glassTabSwitch"
    )
    // The highlight is a glass capsule, not a solid theme color:
    // Glasmio's tab indicator base is white at 0.10 alpha over the
    // glass (GlassBottomBar onDrawSurface), with a light edge.
    Box(
      modifier = Modifier
        .offset(x = offset)
        .width(segmentWidth)
        .fillMaxHeight()
        .clip(shape)
        .background(Color.White.copy(alpha = 0.10f))
        .border(1.dp, Color.White.copy(alpha = 0.17f), shape)
    )
    Row(modifier = Modifier.fillMaxSize()) {
      options.forEachIndexed { index, label ->
        Box(
          modifier = Modifier
            .width(segmentWidth)
            .fillMaxHeight()
            .clickable { onSelect(index) },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            color = if (index == selectedIndex) {
              GlassContent
            } else {
              GlassContentSecondary
            },
            style = MaterialTheme.typography.labelLarge
          )
        }
      }
    }
  }
}

/**
 * A settings row that shows the current value and opens a
 * [GlassSelectPopup] when tapped (replaces segmented controls).
 */
@Composable
fun GlassSelectRow(
  label: String,
  value: String,
  description: String? = null,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      if (description != null) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Icon(
      imageVector = Icons.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/** Rasterized app icon for [packageName], loaded once per composition. */
@Composable
fun rememberAppIcon(packageName: String?): ImageBitmap? {
  val context = LocalContext.current
  return remember(packageName) {
    if (packageName == null) {
      null
    } else {
      try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
      } catch (_: Exception) {
        null
      }
    }
  }
}

/** Human-readable app label, falling back to the package name. */
fun appLabel(context: Context, packageName: String): String {
  return try {
    context.packageManager
      .getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0))
      .toString()
  } catch (_: Exception) {
    packageName
  }
}
