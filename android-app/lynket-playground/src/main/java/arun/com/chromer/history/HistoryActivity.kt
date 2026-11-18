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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
  val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("履歴") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
          }
        },
        actions = {
          TextButton(
            onClick = {
              HistoryStore.clear(context)
              entries = emptyList()
            },
            enabled = entries.isNotEmpty()
          ) {
            Text("すべて消去")
          }
        }
      )
    }
  ) { padding ->
    if (entries.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "履歴はまだありません",
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
      ) {
        items(entries, key = { it.url + "@" + it.visitedAt }) { entry ->
          HistoryRow(
            entry = entry,
            dateFormat = dateFormat,
            onOpen = {
              context.startActivity(BrowserActivity.createDirectIntent(context, entry.url))
            },
            onDelete = {
              HistoryStore.remove(context, entry)
              entries = HistoryStore.getAll(context)
            }
          )
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
      Icon(Icons.Filled.Close, contentDescription = "削除")
    }
  }
}

