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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
  Scaffold(
    topBar = {
      TopAppBar(title = { Text("セットアップ") })
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
        text = "このアプリが使う権限を確認します。あとからホーム画面の「権限の管理」でも変更できます。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      SetupItemCard(
        title = "上に重ねて表示",
        description = "オーバーレイ表示とWeb heads（バブル）に必要です",
        granted = overlayGranted,
        onAction = {
          try {
            context.startActivity(OverlayService.createPermissionIntent(context))
          } catch (_: Exception) {
          }
        }
      )
      SetupItemCard(
        title = "デフォルトブラウザ",
        description = "他のアプリのリンクをこのアプリで受け取れるようになります",
        granted = isDefault,
        actionLabel = "設定する",
        onAction = {
          try {
            roleLauncher.launch(DefaultBrowser.createRequestIntent(context))
          } catch (_: Exception) {
          }
        }
      )
      SetupItemCard(
        title = "通知",
        description = "バブルやオーバーレイの状態を通知で知らせます",
        granted = notificationsGranted,
        onAction = {
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
      )
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "うまく許可できないとき",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "APKから直接インストールしたアプリは、Androidの「制限付き設定」によって一部の許可がブロックされることがあります。その場合は、設定 → アプリ → Hiraku → 右上の︙メニュー →「制限付き設定を許可」を押してから、もう一度この画面で許可してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("はじめる")
      }
    }
  }
}

@Composable
private fun SetupItemCard(
  title: String,
  description: String,
  granted: Boolean,
  actionLabel: String = "許可する",
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
        text = if (granted) "状態：許可済み" else "状態：未設定",
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
        Text(if (granted) "設定済み" else actionLabel)
      }
    }
  }
}
