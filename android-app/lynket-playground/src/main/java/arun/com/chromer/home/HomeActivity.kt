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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import arun.com.chromer.apps.AppRulesActivity
import arun.com.chromer.browser.DefaultBrowser
import arun.com.chromer.customtab.CustomTabProviders
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.di.HasInjector
import arun.com.chromer.history.HistoryActivity
import arun.com.chromer.link.LinkHandlerActivity
import arun.com.chromer.settings.SettingsActivity
import arun.com.chromer.setup.SetupActivity
import arun.com.chromer.setup.SetupPreferences
import arun.com.chromer.tabs.TabsActivity
import arun.com.chromer.theme.LynketTheme
import com.deliveryhero.whetstone.activity.ContributesActivityInjector
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

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
              startActivity(LinkHandlerActivity.createOpenIntent(this, url))
            }
          )
        }
      }
    }
    if (!SetupPreferences.isCompleted(this)) {
      startActivity(Intent(this, SetupActivity::class.java))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
  onOpenUrl: (String) -> Unit
) {
  var urlInput by remember { mutableStateOf("") }
  var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
  LaunchedEffect(urlInput) {
    val q = urlInput.trim()
    if (q.isBlank() || (!q.contains(" ") && q.contains("."))) {
      suggestions = emptyList()
      return@LaunchedEffect
    }
    delay(300)
    suggestions = fetchSearchSuggestions(q)
  }
  val context = LocalContext.current
  val providers = remember { CustomTabProviders.findProviders(context) }
  var defaultMethod by remember { mutableStateOf(LinkPreferences.getDefaultMethod(context)) }
  var providerPackage by remember { mutableStateOf(LinkPreferences.getProviderPackage(context)) }
  var secondaryPackage by remember { mutableStateOf(LinkPreferences.getSecondaryProviderPackage(context)) }
  var isDefault by remember { mutableStateOf(DefaultBrowser.isDefaultBrowser(context)) }
  val versionName = remember {
    try {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) {
      null
    }
  }
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
        defaultMethod = LinkPreferences.getDefaultMethod(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Hiraku") }
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
              label = { Text(text = "URLまたは検索ワード") },
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
          if (suggestions.isNotEmpty()) {
            Column {
              suggestions.take(5).forEach { suggestion ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUrl(suggestion) }
                    .padding(vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Filled.Search, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = suggestion,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
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
            text = "デフォルトにすると、他のアプリでリンクをタップしたときにこのアプリが受け取り、「標準の開き方」やアプリごとの設定で開きます。リンクが元のアプリの中で開かれてしまう場合は、そのアプリの設定で「リンクをアプリ内で開く」をオフにしてください",
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
            text = "標準の開き方",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "アプリごとの設定がないリンクは、この方法で開きます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          for (method in OpenMethod.CHOICES) {
            LinkOptionRow(
              label = method.label,
              selected = defaultMethod == method,
              onSelect = {
                LinkPreferences.setDefaultMethod(context, method)
                defaultMethod = method
              }
            )
          }
          LinkOptionRow(
            label = OpenMethod.ASK.label,
            selected = defaultMethod == OpenMethod.ASK,
            onSelect = {
              LinkPreferences.setDefaultMethod(context, OpenMethod.ASK)
              defaultMethod = OpenMethod.ASK
            }
          )
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "第1のブラウザ",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "「選んだブラウザ」で使うブラウザです",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LinkOptionRow(
            label = "自動（検出された最初のブラウザ）",
            selected = providerPackage == null,
            onSelect = {
              LinkPreferences.setProviderPackage(context, null)
              providerPackage = null
            }
          )
          providers.forEach { provider ->
            LinkOptionRow(
              label = provider.label,
              iconPackage = provider.packageName,
              selected = providerPackage == provider.packageName,
              onSelect = {
                LinkPreferences.setProviderPackage(context, provider.packageName)
                providerPackage = provider.packageName
              }
            )
          }
          if (providers.isEmpty()) {
            Text(
              text = "※ Custom Tab対応のブラウザ（Chrome等）が見つかりませんでした",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "第2のブラウザ",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "Custom Tabのメニューに「○○で開く」が追加され、開いているページを別のブラウザで開き直せます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LinkOptionRow(
            label = "なし",
            selected = secondaryPackage == null,
            onSelect = {
              LinkPreferences.setSecondaryProviderPackage(context, null)
              secondaryPackage = null
            }
          )
          providers.forEach { provider ->
            LinkOptionRow(
              label = provider.label,
              iconPackage = provider.packageName,
              selected = secondaryPackage == provider.packageName,
              onSelect = {
                LinkPreferences.setSecondaryProviderPackage(context, provider.packageName)
                secondaryPackage = provider.packageName
              }
            )
          }
        }
      }

      Button(
        onClick = { context.startActivity(Intent(context, AppRulesActivity::class.java)) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("アプリごとの設定")
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "タブと履歴",
            style = MaterialTheme.typography.titleMedium
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { context.startActivity(Intent(context, TabsActivity::class.java)) },
              modifier = Modifier.weight(1f)
            ) { Text("タブ一覧") }
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, HistoryActivity::class.java)) },
              modifier = Modifier.weight(1f)
            ) { Text("履歴") }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
          modifier = Modifier.weight(1f)
        ) {
          Text("設定")
        }
        OutlinedButton(
          onClick = { context.startActivity(Intent(context, SetupActivity::class.java)) },
          modifier = Modifier.weight(1f)
        ) {
          Text("権限の管理")
        }
      }

      Text(
        text = "バージョン ${versionName ?: "-"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )
    }
  }
}

@Composable
private fun LinkOptionRow(
  label: String,
  selected: Boolean,
  onSelect: () -> Unit,
  iconPackage: String? = null
) {
  val context = LocalContext.current
  val iconBitmap = remember(iconPackage) {
    if (iconPackage == null) {
      null
    } else {
      try {
        val drawable = context.packageManager.getApplicationIcon(iconPackage)
        val iconWidth = drawable.intrinsicWidth.coerceAtLeast(1)
        val iconHeight = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(iconWidth, iconHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, iconWidth, iconHeight)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
      } catch (_: Exception) {
        null
      }
    }
  }
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
    if (iconBitmap != null) {
      Image(
        bitmap = iconBitmap,
        contentDescription = null,
        modifier = Modifier.size(28.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = label)
  }
}

private suspend fun fetchSearchSuggestions(query: String): List<String> =
  withContext(Dispatchers.IO) {
    try {
      val encoded = URLEncoder.encode(query, "UTF-8")
      val connection = URL(
        "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
      ).openConnection() as HttpURLConnection
      connection.connectTimeout = 3000
      connection.readTimeout = 3000
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
      val text = connection.inputStream.bufferedReader().use { it.readText() }
      connection.disconnect()
      val root = JSONArray(text)
      val array = root.optJSONArray(1) ?: return@withContext emptyList()
      val result = ArrayList<String>()
      for (i in 0 until array.length()) {
        val item = array.optString(i)
        if (item.isNotBlank()) {
          result.add(item)
        }
      }
      result
    } catch (_: Exception) {
      emptyList()
    }
  }
