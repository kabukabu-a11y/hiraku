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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.customtab.displayLabel
import arun.com.chromer.theme.LynketTheme
import arun.com.chromer.ui.GlassOption
import arun.com.chromer.ui.GlassSelectPopup
import arun.com.chromer.ui.HirakuBottomBar
import arun.com.chromer.ui.HirakuToggle
import arun.com.chromer.ui.openHomeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
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
    val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_RULES
    setContent {
      LynketTheme {
        AppRulesScreen(mode = mode, onBack = { finish() })
      }
    }
  }

  companion object {
    const val EXTRA_MODE = "arun.com.chromer.apps.EXTRA_MODE"
    const val MODE_RULES = "rules"
    const val MODE_SECRET = "secret"

    fun createIntent(context: android.content.Context, mode: String): Intent {
      return Intent(context, AppRulesActivity::class.java).apply {
        putExtra(EXTRA_MODE, mode)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRulesScreen(mode: String, onBack: () -> Unit) {
  val context = LocalContext.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(
              when (mode) {
                AppRulesActivity.MODE_SECRET -> R.string.secret_apps_title
                else -> R.string.app_rules_title
              }
            )
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        }
      )
    },
    bottomBar = {
      HirakuBottomBar(
        selectedIndex = 1,
        onSelect = { index ->
          if (index != 1) {
            openHomeTab(context, index)
            (context as? Activity)?.finish()
          }
        }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      AppRulesBody(mode = mode)
    }
  }
}

/**
 * The app list with search and per-mode editing, shared between the
 * standalone screen and the main screen's Apps tab (v2.3.0).
 */
@Composable
fun AppRulesBody(mode: String) {
  val context = LocalContext.current
  // Loading the app list (PackageManager query + icon rasterizing)
  // used to run on the main thread during the first composition,
  // which made this screen stutter on open. It now loads on IO and
  // the list appears when ready (v2.1.0).
  var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    loading = false
  }
  val defaultMethod = remember { LinkPreferences.getDefaultMethod(context) }
  var rules by remember { mutableStateOf(LinkPreferences.getAppRules(context)) }
  var secretApps by remember { mutableStateOf(LinkPreferences.getSecretApps(context)) }
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

  Column(modifier = Modifier.fillMaxSize()) {
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      label = { Text(stringResource(R.string.app_rules_search)) },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    )
    Text(
      text = when (mode) {
        AppRulesActivity.MODE_SECRET -> stringResource(R.string.secret_apps_desc) +
          "\n" + stringResource(R.string.secret_note)
        else -> stringResource(
          R.string.app_rules_default_note,
          defaultMethod.displayLabel(context)
        )
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp)
    )
    if (loading) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        CircularProgressIndicator()
      }
    }
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 96.dp)
    ) {
      items(filtered, key = { it.packageName }) { app ->
        val rule = rules[app.packageName]
        val isSecret = app.packageName in secretApps
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              when (mode) {
                AppRulesActivity.MODE_SECRET -> {
                  val newValue = !isSecret
                  LinkPreferences.setAppSecret(context, app.packageName, newValue)
                  secretApps = LinkPreferences.getSecretApps(context)
                }
                else -> editingApp = app
              }
            }
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
            if (mode == AppRulesActivity.MODE_RULES) {
              Text(
                text = rule?.let { it.displayLabel(context) }
                  ?: stringResource(R.string.app_rules_default_badge),
                style = MaterialTheme.typography.bodySmall,
                color = if (rule != null) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
              if (isSecret) {
                Text(
                  text = stringResource(R.string.secret_mode),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
          if (mode == AppRulesActivity.MODE_SECRET) {
            HirakuToggle(
              checked = isSecret,
              onCheckedChange = { newValue ->
                LinkPreferences.setAppSecret(context, app.packageName, newValue)
                secretApps = LinkPreferences.getSecretApps(context)
              }
            )
          }
        }
      }
    }
  }

  val app = editingApp
  if (app != null) {
    // v2.3.0: the same glass selection popup as Settings, with the
    // per-app secret toggle in the footer.
    val current = rules[app.packageName]
    var secretChecked by remember(app.packageName) {
      mutableStateOf(app.packageName in secretApps)
    }
    val options = listOf(
      GlassOption(
        label = stringResource(
          R.string.app_rules_default_option,
          defaultMethod.displayLabel(context)
        )
      )
    ) + OpenMethod.CHOICES.map { GlassOption(label = it.displayLabel(context)) }
    val selectedIndex = if (current == null) {
      0
    } else {
      OpenMethod.CHOICES.indexOf(current) + 1
    }
    GlassSelectPopup(
      options = options,
      selectedIndex = selectedIndex,
      onSelect = { index ->
        if (index == 0) {
          LinkPreferences.setAppRule(context, app.packageName, null)
        } else {
          LinkPreferences.setAppRule(
            context,
            app.packageName,
            OpenMethod.CHOICES[index - 1]
          )
        }
        rules = LinkPreferences.getAppRules(context)
        editingApp = null
      },
      onDismiss = { editingApp = null },
      footer = {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.secret_mode),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
          )
          HirakuToggle(
            checked = secretChecked,
            onCheckedChange = { value ->
              secretChecked = value
              LinkPreferences.setAppSecret(context, app.packageName, value)
              secretApps = LinkPreferences.getSecretApps(context)
            }
          )
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

fun loadInstalledApps(context: android.content.Context): List<InstalledApp> {
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
      val size = 96
      val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, size, size)
      drawable.draw(canvas)
      bitmap.asImageBitmap()
    } catch (_: Exception) {
      null
    }
    byPackage[pkg] = InstalledApp(packageName = pkg, label = label, icon = icon)
  }
  return byPackage.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
