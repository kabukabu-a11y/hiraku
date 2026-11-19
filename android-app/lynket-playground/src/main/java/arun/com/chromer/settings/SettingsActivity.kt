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

import android.app.Activity
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import arun.com.chromer.apps.AppRulesActivity
import arun.com.chromer.apps.SiteRulesActivity
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.tabs.TabStore
import arun.com.chromer.theme.LynketTheme
import arun.com.chromer.ui.SegmentedControl

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
  var securityCheck by remember { mutableStateOf(LinkPreferences.isSecurityCheckEnabled(context)) }
  var secretGlobal by remember { mutableStateOf(LinkPreferences.isSecretGlobal(context)) }
  var bubbleRemember by remember { mutableStateOf(LinkPreferences.isBubbleRememberPos(context)) }
  var animLevel by remember { mutableStateOf(LinkPreferences.getAnimLevel(context)) }
  var textZoom by remember { mutableStateOf(LinkPreferences.getTextZoom(context)) }
  var desktopDefault by remember { mutableStateOf(LinkPreferences.isDesktopDefault(context)) }
  var forceDark by remember { mutableStateOf(LinkPreferences.isForceDark(context)) }
  var freeResize by remember { mutableStateOf(LinkPreferences.isOverlayFreeResize(context)) }
  var overlayRemember by remember { mutableStateOf(LinkPreferences.isOverlayRemember(context)) }
  var passthroughTap by remember { mutableStateOf(LinkPreferences.isPassthroughTapEnabled(context)) }

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri != null) {
      try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(LinkPreferences.exportSettings(context).toByteArray())
        }
      } catch (_: Exception) {
      }
    }
  }
  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      val ok = try {
        val text = context.contentResolver.openInputStream(uri)
          ?.bufferedReader()
          ?.use { it.readText() }
        text != null && LinkPreferences.importSettings(context, text)
      } catch (_: Exception) {
        false
      }
      Toast.makeText(
        context,
        context.getString(
          if (ok) R.string.settings_imported else R.string.settings_import_failed
        ),
        Toast.LENGTH_SHORT
      ).show()
      if (ok) {
        (context as? Activity)?.recreate()
      }
    }
  }

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
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedControl(
          options = listOf(
            stringResource(R.string.settings_bar_bottom),
            stringResource(R.string.settings_bar_top)
          ),
          selectedIndex = if (barOnTop) 1 else 0,
          onSelect = { index ->
            val onTop = index == 1
            LinkPreferences.setBarOnTop(context, onTop)
            barOnTop = onTop
          }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.settings_animation),
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = stringResource(R.string.settings_animation_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SegmentedControl(
          options = listOf(
            stringResource(R.string.anim_off),
            stringResource(R.string.anim_weak),
            stringResource(R.string.anim_strong)
          ),
          selectedIndex = animLevel,
          onSelect = { index ->
            LinkPreferences.setAnimLevel(context, index)
            animLevel = index
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
        SwitchRow(
          title = stringResource(R.string.settings_bubble_remember),
          description = stringResource(R.string.settings_bubble_remember_desc),
          checked = bubbleRemember,
          onCheckedChange = {
            LinkPreferences.setBubbleRememberPos(context, it)
            bubbleRemember = it
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
        Text(
          text = stringResource(R.string.text_size),
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = stringResource(R.string.settings_text_size_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        val zoomOptions = listOf(75, 100, 125, 150)
        SegmentedControl(
          options = zoomOptions.map { "$it%" },
          selectedIndex = zoomOptions.indexOf(textZoom).coerceAtLeast(0),
          onSelect = { index ->
            LinkPreferences.setTextZoom(context, zoomOptions[index])
            textZoom = zoomOptions[index]
          }
        )
        SwitchRow(
          title = stringResource(R.string.settings_desktop_default),
          description = stringResource(R.string.settings_desktop_default_desc),
          checked = desktopDefault,
          onCheckedChange = {
            LinkPreferences.setDesktopDefault(context, it)
            desktopDefault = it
          }
        )
        SwitchRow(
          title = stringResource(R.string.force_dark),
          description = stringResource(R.string.force_dark_desc),
          checked = forceDark,
          onCheckedChange = {
            LinkPreferences.setForceDark(context, it)
            forceDark = it
          }
        )
      }

      SettingsCard(title = stringResource(R.string.settings_overlay)) {
        SwitchRow(
          title = stringResource(R.string.settings_free_resize),
          description = stringResource(R.string.settings_free_resize_desc),
          checked = freeResize,
          onCheckedChange = {
            LinkPreferences.setOverlayFreeResize(context, it)
            freeResize = it
          }
        )
        SwitchRow(
          title = stringResource(R.string.settings_overlay_remember),
          description = stringResource(R.string.settings_overlay_remember_desc),
          checked = overlayRemember,
          onCheckedChange = {
            LinkPreferences.setOverlayRemember(context, it)
            overlayRemember = it
          }
        )
        SwitchRow(
          title = stringResource(R.string.settings_passthrough_tap),
          description = stringResource(R.string.settings_passthrough_tap_desc),
          checked = passthroughTap,
          onCheckedChange = {
            LinkPreferences.setPassthroughTapEnabled(context, it)
            passthroughTap = it
          }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = {
            context.startActivity(Intent(context, SiteRulesActivity::class.java))
          }
        ) {
          Text(stringResource(R.string.site_rules_title))
        }
      }

      SettingsCard(title = stringResource(R.string.settings_privacy)) {
        SwitchRow(
          title = stringResource(R.string.settings_security_check),
          description = stringResource(R.string.settings_security_check_desc),
          checked = securityCheck,
          onCheckedChange = {
            LinkPreferences.setSecurityCheckEnabled(context, it)
            securityCheck = it
          }
        )
        SwitchRow(
          title = stringResource(R.string.secret_global),
          description = stringResource(R.string.secret_global_desc),
          checked = secretGlobal,
          onCheckedChange = {
            LinkPreferences.setSecretGlobal(context, it)
            secretGlobal = it
          }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = {
            context.startActivity(
              AppRulesActivity.createIntent(context, AppRulesActivity.MODE_SECRET)
            )
          }
        ) {
          Text(stringResource(R.string.secret_apps_title))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = {
            context.startActivity(
              AppRulesActivity.createIntent(context, AppRulesActivity.MODE_SHARE)
            )
          }
        ) {
          Text(stringResource(R.string.settings_share_targets))
        }
        Text(
          text = stringResource(R.string.settings_share_targets_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.settings_backup),
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = stringResource(R.string.settings_backup_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { exportLauncher.launch("hiraku-settings.json") },
            modifier = Modifier.weight(1f)
          ) {
            Text(stringResource(R.string.settings_export))
          }
          OutlinedButton(
            onClick = {
              importLauncher.launch(arrayOf("application/json", "text/plain"))
            },
            modifier = Modifier.weight(1f)
          ) {
            Text(stringResource(R.string.settings_import))
          }
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
