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

package arun.com.chromer.settings

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.tabs.TabStore
import arun.com.chromer.theme.LynketTheme

/**
 * All the fine-grained settings in one place: display, browsing,
 * language, notifications and data. The home screen stays simple;
 * everything tweakable lives here.
 */
class SettingsActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        SettingsScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var barOnTop by remember { mutableStateOf(LinkPreferences.isBarOnTop(context)) }
  var blurEnabled by remember { mutableStateOf(LinkPreferences.isOverlayBlurEnabled(context)) }
  var hideBar by remember { mutableStateOf(LinkPreferences.getHideBarOnScroll(context)) }
  var adBlock by remember { mutableStateOf(LinkPreferences.isAdBlockEnabled(context)) }
  var preview by remember { mutableStateOf(LinkPreferences.isPreviewEnabled(context)) }
  var language by remember { mutableStateOf(LinkPreferences.getLanguage(context)) }
  var chooserOrder by remember { mutableStateOf(LinkPreferences.getChooserOrder(context)) }
  var hiddenMethods by remember { mutableStateOf(LinkPreferences.getHiddenMethods(context)) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      SettingsCard(title = stringResource(R.string.settings_display)) {
        Text(
          text = stringResource(R.string.settings_bar_position),
          style = MaterialTheme.typography.bodyLarge
        )
        RadioRow(
          label = stringResource(R.string.settings_bar_bottom),
          selected = !barOnTop,
          onSelect = {
            LinkPreferences.setBarOnTop(context, false)
            barOnTop = false
          }
        )
        RadioRow(
          label = stringResource(R.string.settings_bar_top),
          selected = barOnTop,
          onSelect = {
            LinkPreferences.setBarOnTop(context, true)
            barOnTop = true
          }
        )
        SwitchRow(
          title = stringResource(R.string.settings_blur),
          description = stringResource(R.string.settings_blur_desc),
          checked = blurEnabled,
          onCheckedChange = {
            LinkPreferences.setOverlayBlurEnabled(context, it)
            blurEnabled = it
          }
        )
        SwitchRow(
          title = stringResource(R.string.settings_hide_bar),
          description = stringResource(R.string.settings_hide_bar_desc),
          checked = hideBar,
          onCheckedChange = {
            LinkPreferences.setHideBarOnScroll(context, it)
            hideBar = it
          }
        )
      }

      SettingsCard(title = stringResource(R.string.settings_browsing)) {
        SwitchRow(
          title = stringResource(R.string.adblock_title),
          description = stringResource(R.string.settings_adblock_desc),
          checked = adBlock,
          onCheckedChange = {
            LinkPreferences.setAdBlockEnabled(context, it)
            adBlock = it
          }
        )
        SwitchRow(
          title = stringResource(R.string.settings_preview),
          description = stringResource(R.string.settings_preview_desc),
          checked = preview,
          onCheckedChange = {
            LinkPreferences.setPreviewEnabled(context, it)
            preview = it
          }
        )
      }

      SettingsCard(title = stringResource(R.string.settings_chooser)) {
        Text(
          text = stringResource(R.string.settings_chooser_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        chooserOrder.forEachIndexed { index, method ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = stringResource(method.labelRes),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodyMedium
            )
            IconButton(
              enabled = index > 0,
              onClick = {
                val newOrder = chooserOrder.toMutableList()
                val tmp = newOrder[index - 1]
                newOrder[index - 1] = newOrder[index]
                newOrder[index] = tmp
                LinkPreferences.setChooserOrder(context, newOrder)
                chooserOrder = newOrder
              }
            ) {
              Text("↑")
            }
            IconButton(
              enabled = index < chooserOrder.lastIndex,
              onClick = {
                val newOrder = chooserOrder.toMutableList()
                val tmp = newOrder[index + 1]
                newOrder[index + 1] = newOrder[index]
                newOrder[index] = tmp
                LinkPreferences.setChooserOrder(context, newOrder)
                chooserOrder = newOrder
              }
            ) {
              Text("↓")
            }
            Switch(
              checked = method !in hiddenMethods,
              onCheckedChange = { visible ->
                LinkPreferences.setMethodHidden(context, method, !visible)
                hiddenMethods = LinkPreferences.getHiddenMethods(context)
              }
            )
          }
        }
      }

      SettingsCard(title = stringResource(R.string.settings_language)) {
        val options = listOf(
          "system" to stringResource(R.string.lang_system),
          "ja" to stringResource(R.string.lang_ja),
          "en" to "English"
        )
        for ((value, label) in options) {
          RadioRow(
            label = label,
            selected = language == value,
            onSelect = {
              LinkPreferences.setLanguage(context, value)
              language = value
              applyLanguage(context, value)
            }
          )
        }
        Text(
          text = stringResource(R.string.settings_language_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      SettingsCard(title = stringResource(R.string.settings_notifications)) {
        Text(
          text = stringResource(R.string.settings_notifications_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = {
            try {
              val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
              context.startActivity(intent)
            } catch (_: Exception) {
              try {
                context.startActivity(
                  Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                  )
                )
              } catch (_: Exception) {
              }
            }
          }
        ) {
          Text(stringResource(R.string.settings_open_notification_settings))
        }
      }

      SettingsCard(title = stringResource(R.string.settings_data)) {
        OutlinedButton(
          onClick = {
            HistoryStore.clear(context)
            TabStore.clear(context)
            Toast.makeText(context, context.getString(R.string.settings_data_cleared), Toast.LENGTH_SHORT).show()
          }
        ) {
          Text(stringResource(R.string.settings_clear_data))
        }
      }
    }
  }
}

@Composable
private fun SettingsCard(
  title: String,
  content: @Composable () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
      )
      Spacer(modifier = Modifier.height(8.dp))
      content()
    }
  }
}

@Composable
private fun RadioRow(
  label: String,
  selected: Boolean,
  onSelect: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Text(text = label)
  }
}

@Composable
private fun SwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title)
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

private fun applyLanguage(context: android.content.Context, language: String) {
  if (Build.VERSION.SDK_INT < 33) return
  try {
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    manager.applicationLocales = if (language == "system") {
      LocaleList.getEmptyLocaleList()
    } else {
      LocaleList.forLanguageTags(language)
    }
  } catch (_: Exception) {
  }
}
