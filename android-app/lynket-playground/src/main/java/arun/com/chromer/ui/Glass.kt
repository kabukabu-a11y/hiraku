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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import arun.com.chromer.customtab.LinkPreferences

/**
 * Liquid Glass building blocks (v2.3.0). The original plan was to use
 * the Glasmio library like MeiloX does, but Glasmio 0.1.3 is built
 * for the Kotlin 2.x / Compose 1.9+ toolchain and cannot be resolved
 * against this project's Kotlin 1.9 / Compose 1.5 setup, so the glass
 * material is implemented in-house instead: translucent tint, a
 * light-catching gradient border and — where the window allows it
 * (chooser sheet, overlay windows) — a real backdrop blur from the
 * platform (FLAG_BLUR_BEHIND / background blur radius on API 31+).
 * Everything degrades to a plain solid surface when the user turns
 * the glass effect off.
 */

/** The yellow used for ON toggles so the on state stands out. */
val ToggleOnYellow = Color(0xFFFFD60A)

private val CheckBlue = Color(0xFF0A84FF)

@Composable
fun glassEnabled(): Boolean {
  val context = LocalContext.current
  return remember { LinkPreferences.isGlassEnabled(context) }
}

/**
 * Glass surface: translucent fill plus a vertical gradient border
 * that catches light at the top edge. Falls back to the solid
 * Material surface when glass is disabled.
 */
@Composable
fun Modifier.glassBackground(
  shape: Shape,
  enabled: Boolean = glassEnabled(),
  dark: Boolean = isSystemInDarkTheme(),
  tint: Color? = null
): Modifier {
  if (!enabled) {
    return this.background(MaterialTheme.colorScheme.surface, shape)
  }
  val base = tint ?: if (dark) {
    Color(0xFF17171D).copy(alpha = 0.68f)
  } else {
    Color(0xFFF5F5FA).copy(alpha = 0.66f)
  }
  val borderBrush = Brush.verticalGradient(
    listOf(
      Color.White.copy(alpha = if (dark) 0.42f else 0.7f),
      Color.White.copy(alpha = 0.10f)
    )
  )
  return this
    .background(base, shape)
    .border(1.dp, borderBrush, shape)
}

/** Solid color to paint glass in the View-based overlay windows. */
fun glassViewColor(dark: Boolean, glassOn: Boolean): Int {
  return if (glassOn) {
    if (dark) 0xD91B1B21.toInt() else 0xD9F2F2F7.toInt()
  } else {
    if (dark) 0xFF202124.toInt() else 0xFFF2F2F7.toInt()
  }
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
      else -> spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
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
  val icon: ImageVector? = null
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
  onDismiss: () -> Unit
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
      LinkPreferences.ANIM_WEAK -> tween(durationMillis = 140)
      else -> spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
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
          initialScale = 0.9f,
          animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
          )
        )
      ) {
        val shape = RoundedCornerShape(26.dp)
        Box(
          modifier = Modifier
            .width(300.dp)
            .glassBackground(
              shape = shape,
              enabled = true,
              dark = true,
              tint = Color(0xFF14141B).copy(alpha = 0.82f)
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
                if (option.icon != null) {
                  Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                  )
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
