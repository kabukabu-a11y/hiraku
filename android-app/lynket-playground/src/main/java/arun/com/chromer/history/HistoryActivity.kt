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

package arun.com.chromer.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import arun.com.chromer.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.theme.LynketTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        HistoryScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var entries by remember { mutableStateOf(HistoryStore.getAll(context)) }
  var query by remember { mutableStateOf("") }
  var periodMenu by remember { mutableStateOf(false) }
  val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }
  val filtered = remember(entries, query) {
    val q = query.trim()
    if (q.isEmpty()) {
      entries
    } else {
      entries.filter {
        it.title.contains(q, ignoreCase = true) ||
          it.url.contains(q, ignoreCase = true)
      }
    }
  }

  fun refresh() {
    entries = HistoryStore.getAll(context)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.history_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        },
        actions = {
          Box {
            IconButton(
              onClick = { periodMenu = true },
              enabled = entries.isNotEmpty()
            ) {
              Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.history_delete_period)
              )
            }
            DropdownMenu(
              expanded = periodMenu,
              onDismissRequest = { periodMenu = false }
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete_hour)) },
                onClick = {
                  periodMenu = false
                  HistoryStore.deleteVisitedAfter(
                    context,
                    System.currentTimeMillis() - 60 * 60 * 1000L
                  )
                  refresh()
                }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete_today)) },
                onClick = {
                  periodMenu = false
                  val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                  }
                  HistoryStore.deleteVisitedAfter(context, cal.timeInMillis)
                  refresh()
                }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete_week)) },
                onClick = {
                  periodMenu = false
                  HistoryStore.deleteVisitedAfter(
                    context,
                    System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                  )
                  refresh()
                }
              )
              DropdownMenuItem(
                text = { Text(stringResource(R.string.history_delete_all)) },
                onClick = {
                  periodMenu = false
                  HistoryStore.clear(context)
                  refresh()
                }
              )
            }
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.history_search_hint)) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      )
      if (filtered.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = stringResource(
              if (entries.isEmpty()) {
                R.string.history_empty
              } else {
                R.string.history_no_results
              }
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(filtered, key = { it.url + "@" + it.visitedAt }) { entry ->
            HistoryRow(
              entry = entry,
              dateFormat = dateFormat,
              onOpen = {
                context.startActivity(BrowserActivity.createDirectIntent(context, entry.url))
              },
              onDelete = {
                HistoryStore.remove(context, entry)
                refresh()
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryRow(
  entry: HistoryEntry,
  dateFormat: SimpleDateFormat,
  onOpen: () -> Unit,
  onDelete: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entry.title.ifBlank { entry.url },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = entry.url,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = dateFormat.format(Date(entry.visitedAt)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    IconButton(onClick = onDelete) {
      Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.act_delete))
    }
  }
}

