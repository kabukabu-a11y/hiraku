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

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.theme.LynketTheme

private data class InstalledApp(
  val packageName: String,
  val label: String,
  val icon: ImageBitmap?
)

/**
 * Per-app open-method rules: search installed apps and choose how
 * links coming from each app should open. Apps without a rule follow
 * the standard method.
 */
class AppRulesActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        AppRulesScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRulesScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val apps = remember { loadInstalledApps(context) }
  val defaultMethod = remember { LinkPreferences.getDefaultMethod(context) }
  var rules by remember { mutableStateOf(LinkPreferences.getAppRules(context)) }
  var query by remember { mutableStateOf("") }
  var editingApp by remember { mutableStateOf<InstalledApp?>(null) }

  val filtered = remember(query, apps) {
    val q = query.trim()
    if (q.isEmpty()) {
      apps
    } else {
      apps.filter {
        it.label.contains(q, ignoreCase = true) ||
          it.packageName.contains(q, ignoreCase = true)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("アプリごとの設定") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("アプリを検索") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      )
      Text(
        text = "設定がないアプリは「標準（${defaultMethod.label}）」で開きます",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
      )
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filtered, key = { it.packageName }) { app ->
          val rule = rules[app.packageName]
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { editingApp = app }
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (app.icon != null) {
              Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = rule?.label ?: "標準",
                style = MaterialTheme.typography.bodySmall,
                color = if (rule != null) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            }
          }
        }
      }
    }
  }

  val app = editingApp
  if (app != null) {
    val current = rules[app.packageName]
    AlertDialog(
      onDismissRequest = { editingApp = null },
      title = { Text(app.label) },
      text = {
        Column {
          MethodChoiceRow(
            label = "標準（${defaultMethod.label}）",
            selected = current == null,
            onSelect = {
              LinkPreferences.setAppRule(context, app.packageName, null)
              rules = LinkPreferences.getAppRules(context)
              editingApp = null
            }
          )
          for (method in OpenMethod.CHOICES) {
            MethodChoiceRow(
              label = method.label,
              selected = current == method,
              onSelect = {
                LinkPreferences.setAppRule(context, app.packageName, method)
                rules = LinkPreferences.getAppRules(context)
                editingApp = null
              }
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { editingApp = null }) {
          Text("閉じる")
        }
      }
    )
  }
}

@Composable
private fun MethodChoiceRow(
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

private fun loadInstalledApps(context: android.content.Context): List<InstalledApp> {
  val packageManager = context.packageManager
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val resolved = try {
    packageManager.queryIntentActivities(intent, 0)
  } catch (_: Exception) {
    emptyList()
  }
  val byPackage = LinkedHashMap<String, InstalledApp>()
  for (info in resolved) {
    val pkg = info.activityInfo?.packageName ?: continue
    if (pkg == context.packageName || byPackage.containsKey(pkg)) continue
    val label = try {
      info.loadLabel(packageManager).toString()
    } catch (_: Exception) {
      pkg
    }
    val icon = try {
      val drawable = packageManager.getApplicationIcon(pkg)
      val width = drawable.intrinsicWidth.coerceAtLeast(1)
      val height = drawable.intrinsicHeight.coerceAtLeast(1)
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, width, height)
      drawable.draw(canvas)
      bitmap.asImageBitmap()
    } catch (_: Exception) {
      null
    }
    byPackage[pkg] = InstalledApp(packageName = pkg, label = label, icon = icon)
  }
  return byPackage.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
