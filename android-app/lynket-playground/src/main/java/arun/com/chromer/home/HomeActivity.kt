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

package arun.com.chromer.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.browser.DefaultBrowser
import arun.com.chromer.customtab.CustomTabProviders
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMode
import arun.com.chromer.di.HasInjector
import arun.com.chromer.theme.LynketTheme
import com.deliveryhero.whetstone.activity.ContributesActivityInjector

@ContributesActivityInjector
class HomeActivity : ComponentActivity(), HasInjector {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          HomeContent(
            onOpenUrl = { url ->
              startActivity(BrowserActivity.createIntent(this, url))
            }
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
  onOpenUrl: (String) -> Unit
) {
  var urlInput by remember { mutableStateOf("") }
  val context = LocalContext.current
  val providers = remember { CustomTabProviders.findProviders(context) }
  var mode by remember { mutableStateOf(LinkPreferences.getMode(context)) }
  var providerPackage by remember { mutableStateOf(LinkPreferences.getProviderPackage(context)) }
  var isDefault by remember { mutableStateOf(DefaultBrowser.isDefaultBrowser(context)) }
  val requestLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    isDefault = DefaultBrowser.isDefaultBrowser(context)
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        isDefault = DefaultBrowser.isDefaultBrowser(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Lynket") }
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
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "URLを開く",
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = urlInput,
              onValueChange = { urlInput = it },
              label = { Text(text = "URL") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
              ),
              keyboardActions = KeyboardActions(
                onGo = {
                  if (urlInput.isNotBlank()) {
                    onOpenUrl(urlInput)
                  }
                }
              ),
              modifier = Modifier.weight(1f)
            )
            Button(
              onClick = {
                if (urlInput.isNotBlank()) {
                  onOpenUrl(urlInput)
                }
              }
            ) {
              Text(text = "Open")
            }
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "デフォルトブラウザ",
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (isDefault) {
              "このアプリがデフォルトブラウザに設定されています"
            } else {
              "このアプリはデフォルトブラウザではありません"
            }
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "デフォルトにすると、他のアプリでリンクをタップしたときにこのアプリで開き、「リンクの開き方」で選んだブラウザや表示方法が使われます",
            style = MaterialTheme.typography.bodySmall
          )
          Spacer(modifier = Modifier.height(12.dp))
          if (!isDefault) {
            Button(
              onClick = {
                try {
                  requestLauncher.launch(DefaultBrowser.createRequestIntent(context))
                } catch (_: Exception) {
                  try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                  } catch (_: Exception) {
                  }
                }
              }
            ) {
              Text(text = "デフォルトに設定")
            }
          } else {
            OutlinedButton(
              onClick = {
                try {
                  context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: Exception) {
                }
              }
            ) {
              Text(text = "デフォルトアプリ設定を開く")
            }
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "リンクの開き方",
            style = MaterialTheme.typography.titleMedium
          )
          LinkOptionRow(
            label = "システム既定のブラウザ",
            selected = mode == OpenMode.PROVIDER && providerPackage == null,
            onSelect = {
              LinkPreferences.setMode(context, OpenMode.PROVIDER)
              LinkPreferences.setProviderPackage(context, null)
              mode = OpenMode.PROVIDER
              providerPackage = null
            }
          )
          providers.forEach { provider ->
            LinkOptionRow(
              label = provider.label,
              selected = mode == OpenMode.PROVIDER && providerPackage == provider.packageName,
              onSelect = {
                LinkPreferences.setMode(context, OpenMode.PROVIDER)
                LinkPreferences.setProviderPackage(context, provider.packageName)
                mode = OpenMode.PROVIDER
                providerPackage = provider.packageName
              }
            )
          }
          LinkOptionRow(
            label = "開くたびに選ぶ",
            selected = mode == OpenMode.ASK,
            onSelect = {
              LinkPreferences.setMode(context, OpenMode.ASK)
              mode = OpenMode.ASK
            }
          )
          LinkOptionRow(
            label = "アプリ内ブラウザ",
            selected = mode == OpenMode.IN_APP,
            onSelect = {
              LinkPreferences.setMode(context, OpenMode.IN_APP)
              mode = OpenMode.IN_APP
            }
          )
          LinkOptionRow(
            label = "オーバーレイ表示",
            selected = mode == OpenMode.OVERLAY,
            onSelect = {
              LinkPreferences.setMode(context, OpenMode.OVERLAY)
              mode = OpenMode.OVERLAY
            }
          )
          LinkOptionRow(
            label = "Web heads（バブル）",
            selected = mode == OpenMode.WEB_HEAD,
            onSelect = {
              LinkPreferences.setMode(context, OpenMode.WEB_HEAD)
              mode = OpenMode.WEB_HEAD
            }
          )
        }
      }
    }
  }
}

@Composable
private fun LinkOptionRow(
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
    RadioButton(
      selected = selected,
      onClick = onSelect
    )
    Text(text = label)
  }
}

