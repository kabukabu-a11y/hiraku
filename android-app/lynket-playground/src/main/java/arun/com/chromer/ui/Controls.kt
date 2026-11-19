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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import arun.com.chromer.customtab.LinkPreferences

/**
 * Interactive segmented control with a sliding indicator (v2.2.0).
 * The highlight pill springs to the selected segment; the motion
 * follows the app's animation level setting (off / weak / strong).
 */
@Composable
fun SegmentedControl(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  if (options.isEmpty()) return
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  val index = selectedIndex.coerceIn(0, options.lastIndex)
  val shape = RoundedCornerShape(50)
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(4.dp)
  ) {
    val segmentWidth = (maxWidth - 8.dp) / options.size
    val indicatorOffset by animateDpAsState(
      targetValue = segmentWidth * index,
      animationSpec = when (animLevel) {
        LinkPreferences.ANIM_OFF -> tween(durationMillis = 1)
        LinkPreferences.ANIM_WEAK -> tween(durationMillis = 160)
        else -> spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
      },
      label = "segmentedIndicator"
    )
    Box(
      modifier = Modifier
        .offset(x = indicatorOffset)
        .width(segmentWidth)
        .fillMaxHeight()
        .clip(shape)
        .background(MaterialTheme.colorScheme.primary)
    )
    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
      options.forEachIndexed { i, label ->
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
          modifier = Modifier
            .width(segmentWidth)
            .fillMaxHeight()
            .scale(if (pressed && animLevel != LinkPreferences.ANIM_OFF) 0.94f else 1f)
            .clickable(
              interactionSource = interaction,
              indication = null
            ) { onSelect(i) },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (i == index) {
              MaterialTheme.colorScheme.onPrimary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        }
      }
    }
  }
}
