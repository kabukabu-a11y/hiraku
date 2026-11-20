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

package arun.com.chromer.apps

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import arun.com.chromer.R
import arun.com.chromer.customtab.ChooserItem
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.customtab.displayLabel
import arun.com.chromer.customtab.iconPackage
import arun.com.chromer.theme.LynketTheme
import arun.com.chromer.ui.HirakuBottomBar
import arun.com.chromer.ui.appLabel
import arun.com.chromer.ui.openHomeTab
import arun.com.chromer.ui.rememberAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chooser item manager (v2.3.0): long-press drag to reorder the
 * chooser sheet's rows with animation. Rows dragged below the
 * divider line are hidden from the sheet. Share-target apps live
 * in the same list and can be added (they start hidden) or removed.
 */
class ChooserItemsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        ChooserItemsScreen(onBack = { finish() })
      }
    }
  }
}

private sealed interface ManagerRow {
  val key: String
}

private data class ItemRow(val item: ChooserItem) : ManagerRow {
  override val key: String get() = item.id
}

private object DividerRow : ManagerRow {
  override val key: String get() = "divider"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserItemsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.chooser_items_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        }
      )
    },
    bottomBar = {
      HirakuBottomBar(
        selectedIndex = 3,
        onSelect = { index ->
          if (index != 3) {
            openHomeTab(context, index)
            (context as? Activity)?.finish()
          }
        }
      )
    }
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      ChooserItemsBody()
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChooserItemsBody() {
  val context = LocalContext.current
  var rows by remember {
    val items = LinkPreferences.getChooserItems(context)
    val hidden = LinkPreferences.getHiddenChooserItemIds(context)
    val visible = items.filter { it.id !in hidden }
    val hiddenItems = items.filter { it.id in hidden }
    mutableStateOf(
      visible.map { ItemRow(it) } + DividerRow + hiddenItems.map { ItemRow(it) }
    )
  }
  var showPicker by remember { mutableStateOf(false) }

  fun persist(newRows: List<ManagerRow>) {
    rows = newRows
    val itemRows = newRows.filterIsInstance<ItemRow>().map { it.item }
    LinkPreferences.setChooserItems(context, itemRows)
    val dividerPos = newRows.indexOf(DividerRow)
    val hiddenIds = newRows.drop(dividerPos + 1)
      .filterIsInstance<ItemRow>()
      .map { it.item.id }
      .toSet()
    itemRows.forEach {
      LinkPreferences.setChooserItemHidden(context, it.id, it.id in hiddenIds)
    }
    val visibleItems = itemRows.filter { it.id !in hiddenIds }
    val last = visibleItems.lastOrNull()
    LinkPreferences.setCancelPinned(
      context,
      last is ChooserItem.MethodItem && last.method == OpenMethod.CANCEL
    )
  }

  val rowHeight = 56.dp
  val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
  var draggingKey by remember { mutableStateOf<String?>(null) }
  var dragOffset by remember { mutableStateOf(0f) }

  Column(modifier = Modifier.fillMaxSize()) {
    Text(
      text = stringResource(R.string.chooser_items_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentPadding = PaddingValues(bottom = 8.dp)
    ) {
      itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
        when (row) {
          DividerRow -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(1.dp)
                  .background(MaterialTheme.colorScheme.outline)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.chooser_items_hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.width(8.dp))
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(1.dp)
                  .background(MaterialTheme.colorScheme.outline)
              )
            }
          }
          is ItemRow -> {
            val item = row.item
            val isDragging = draggingKey == row.key
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .animateItemPlacement(tween(200))
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                  translationY = if (isDragging) dragOffset else 0f
                }
                .background(
                  if (isDragging) {
                    MaterialTheme.colorScheme.surfaceVariant
                  } else {
                    MaterialTheme.colorScheme.surface
                  }
                )
                .pointerInput(row.key) {
                  detectDragGesturesAfterLongPress(
                    onDragStart = {
                      draggingKey = row.key
                      dragOffset = 0f
                    },
                    onDrag = { change, dragAmount ->
                      change.consume()
                      dragOffset += dragAmount.y
                      var currentIndex = rows.indexOfFirst { it.key == row.key }
                      while (dragOffset > rowHeightPx &&
                        currentIndex in 0 until rows.lastIndex
                      ) {
                        val swapped = rows.toMutableList()
                        val tmp = swapped[currentIndex]
                        swapped[currentIndex] = swapped[currentIndex + 1]
                        swapped[currentIndex + 1] = tmp
                        rows = swapped
                        currentIndex++
                        dragOffset -= rowHeightPx
                      }
                      while (dragOffset < -rowHeightPx && currentIndex > 0) {
                        val swapped = rows.toMutableList()
                        val tmp = swapped[currentIndex]
                        swapped[currentIndex] = swapped[currentIndex - 1]
                        swapped[currentIndex - 1] = tmp
                        rows = swapped
                        currentIndex--
                        dragOffset += rowHeightPx
                      }
                    },
                    onDragEnd = {
                      draggingKey = null
                      dragOffset = 0f
                      persist(rows)
                    },
                    onDragCancel = {
                      draggingKey = null
                      dragOffset = 0f
                      persist(rows)
                    }
                  )
                }
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              val iconPkg = when (item) {
                is ChooserItem.MethodItem -> item.method.iconPackage(context)
                is ChooserItem.ShareItem -> item.packageName
              }
              val icon = rememberAppIcon(iconPkg)
              if (icon != null) {
                Image(
                  bitmap = icon,
                  contentDescription = null,
                  modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = when (item) {
                      is ChooserItem.MethodItem ->
                        stringResource(item.method.labelRes).take(1)
                      is ChooserItem.ShareItem ->
                        appLabel(context, item.packageName).take(1)
                    },
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = when (item) {
                  is ChooserItem.MethodItem -> item.method.displayLabel(context)
                  is ChooserItem.ShareItem -> appLabel(context, item.packageName)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              if (item is ChooserItem.ShareItem) {
                IconButton(
                  onClick = {
                    persist(rows.filter { it.key != row.key })
                  }
                ) {
                  Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.act_delete)
                  )
                }
              }
              Icon(
                Icons.Filled.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
    Button(
      onClick = { showPicker = true },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      Text(stringResource(R.string.chooser_items_add_app))
    }
    Spacer(modifier = Modifier.height(84.dp))
  }

  if (showPicker) {
    AddAppDialog(
      existing = rows.filterIsInstance<ItemRow>()
        .mapNotNull { (it.item as? ChooserItem.ShareItem)?.packageName }
        .toSet(),
      onPick = { packageName ->
        showPicker = false
        // New apps start hidden, below the divider (v2.3.0).
        persist(rows + ItemRow(ChooserItem.ShareItem(packageName)))
      },
      onDismiss = { showPicker = false }
    )
  }
}

@Composable
private fun AddAppDialog(
  existing: Set<String>,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    loading = false
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.chooser_items_add_app)) },
    text = {
      if (loading) {
        CircularProgressIndicator()
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          items(
            apps.filter { it.packageName !in existing },
            key = { it.packageName }
          ) { app ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(app.packageName) }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (app.icon != null) {
                Image(
                  bitmap = app.icon,
                  contentDescription = null,
                  modifier = Modifier.size(36.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(text = app.label)
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.method_cancel))
      }
    }
  )
}
