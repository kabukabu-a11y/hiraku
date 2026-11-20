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

package arun.com.chromer.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import arun.com.chromer.browser.DefaultBrowser
import arun.com.chromer.overlay.OverlayService
import arun.com.chromer.theme.LynketTheme

object SetupPreferences {
  private const val PREFS_NAME = "app_setup"
  private const val KEY_COMPLETED = "setup_completed"

  fun isCompleted(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_COMPLETED, false)

  fun setCompleted(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_COMPLETED, true)
      .apply()
  }
}

class SetupActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        SetupScreen(onDone = {
          SetupPreferences.setCompleted(this)
          finish()
        })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(onDone: () -> Unit) {
  val context = LocalContext.current
  var refreshTick by remember { mutableStateOf(0) }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshTick++
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val overlayGranted = remember(refreshTick) { OverlayService.canDrawOverlays(context) }
  val isDefault = remember(refreshTick) { DefaultBrowser.isDefaultBrowser(context) }
  val notificationsGranted = remember(refreshTick) {
    if (Build.VERSION.SDK_INT < 33) {
      true
    } else {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  val roleLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { refreshTick++ }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    refreshTick++
    if (!granted) {
      // The dialog could not grant it (denied before, restricted
      // settings, OEM quirks): fall back to the settings page.
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
  }
  Scaffold(
    topBar = {
      TopAppBar(title = { Text(stringResource(R.string.setup_title)) })
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = stringResource(R.string.setup_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      SetupItemCard(
        title = stringResource(R.string.setup_overlay),
        description = stringResource(R.string.setup_overlay_desc),
        granted = overlayGranted,
        onAction = {
          try {
            context.startActivity(OverlayService.createPermissionIntent(context))
          } catch (_: Exception) {
          }
        }
      )
      SetupItemCard(
        title = stringResource(R.string.home_default_browser),
        description = stringResource(R.string.setup_default_desc),
        granted = isDefault,
        actionLabel = stringResource(R.string.setup_set),
        onAction = {
          try {
            roleLauncher.launch(DefaultBrowser.createRequestIntent(context))
          } catch (_: Exception) {
          }
        }
      )
      SetupItemCard(
        title = stringResource(R.string.settings_notifications),
        description = stringResource(R.string.setup_notifications_desc),
        granted = notificationsGranted,
        onAction = {
          if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
      )
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = stringResource(R.string.setup_trouble),
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = stringResource(R.string.setup_restricted_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(stringResource(R.string.setup_start))
      }
    }
  }
}

@Composable
private fun SetupItemCard(
  title: String,
  description: String,
  granted: Boolean,
  actionLabel: String? = null,
  onAction: () -> Unit
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = stringResource(if (granted) R.string.setup_status_granted else R.string.setup_status_not_set),
        style = MaterialTheme.typography.bodySmall,
        color = if (granted) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.error
        }
      )
      Button(
        onClick = onAction,
        enabled = !granted
      ) {
        Text(if (granted) stringResource(R.string.setup_done) else (actionLabel ?: stringResource(R.string.setup_allow)))
      }
    }
  }
}
