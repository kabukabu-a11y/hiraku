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

package arun.com.chromer.readlater

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.theme.LynketTheme
import arun.com.chromer.ui.HirakuBottomBar
import arun.com.chromer.ui.openHomeTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The "read later" list (v2.2.0), also the Bookmarks tab (v2.3.0). */
class ReadLaterActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        ReadLaterScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadLaterScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.read_later_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        }
      )
    },
    bottomBar = {
      HirakuBottomBar(
        selectedIndex = 2,
        onSelect = { index ->
          if (index != 2) {
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
      ReadLaterList()
    }
  }
}

/**
 * The read-later / bookmarks list, shared between the standalone
 * screen and the main screen's History tab (v2.3.0).
 */
@Composable
fun ReadLaterList() {
  val context = LocalContext.current
  var entries by remember { mutableStateOf(ReadLaterStore.getAll(context)) }
  val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN) }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(modifier = Modifier.weight(1f))
      TextButton(
        onClick = {
          ReadLaterStore.clear(context)
          entries = emptyList()
        },
        enabled = entries.isNotEmpty()
      ) {
        Text(stringResource(R.string.read_later_clear_all))
      }
    }
    if (entries.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 96.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(R.string.read_later_empty),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
      ) {
        items(entries, key = { it.url + "@" + it.savedAt }) { entry ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                context.startActivity(BrowserActivity.createDirectIntent(context, entry.url))
              }
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
                text = dateFormat.format(Date(entry.savedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            IconButton(
              onClick = {
                ReadLaterStore.remove(context, entry)
                entries = ReadLaterStore.getAll(context)
              }
            ) {
              Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.act_delete))
            }
          }
        }
      }
    }
  }
}
