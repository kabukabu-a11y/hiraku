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

package arun.com.chromer.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.customtab.LinkDispatcher
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.theme.LynketTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ShareTarget(
  val packageName: String,
  val label: String,
  val icon: ImageBitmap?
)

/**
 * Quick share sheet (v2.1.0): the user's pinned share-target apps
 * (up to 5) share the link directly; the last row falls back to the
 * system chooser.
 */
class SharePickerActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val url = intent.getStringExtra(EXTRA_URL)
    if (url.isNullOrBlank()) {
      finish()
      return
    }
    setContent {
      LynketTheme {
        ShareSheet(url = url)
      }
    }
  }

  @Composable
  private fun ShareSheet(url: String) {
    val context = LocalContext.current
    var targets by remember { mutableStateOf<List<ShareTarget>>(emptyList()) }

    LaunchedEffect(Unit) {
      targets = withContext(Dispatchers.IO) { loadTargets(context) }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0x99000000))
        .clickable { finish() },
      contentAlignment = Alignment.BottomCenter
    ) {
      Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
          .fillMaxWidth()
          .clickable(enabled = false) { }
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
        ) {
          Text(
            text = stringResource(R.string.share_title),
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(modifier = Modifier.height(8.dp))
          for (target in targets) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  shareToApp(target.packageName, url)
                  finish()
                }
                .padding(vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (target.icon != null) {
                Image(
                  bitmap = target.icon,
                  contentDescription = null,
                  modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
              }
              Text(
                text = target.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                LinkDispatcher.shareSystem(this@SharePickerActivity, url)
                finish()
              }
              .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(R.string.share_other_apps),
              style = MaterialTheme.typography.bodyLarge
            )
          }
        }
      }
    }
  }

  private fun shareToApp(packageName: String, url: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
      setPackage(packageName)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      startActivity(sendIntent)
    } catch (_: Exception) {
      LinkDispatcher.shareSystem(this, url)
    }
  }

  private fun loadTargets(context: Context): List<ShareTarget> {
    val packageManager = context.packageManager
    return LinkPreferences.getShareTargets(context).mapNotNull { pkg ->
      try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        val label = packageManager.getApplicationLabel(info).toString()
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
        ShareTarget(packageName = pkg, label = label, icon = icon)
      } catch (_: Exception) {
        null
      }
    }
  }

  companion object {
    const val EXTRA_URL = "arun.com.chromer.share.EXTRA_URL"

    fun createIntent(context: Context, url: String): Intent {
      return Intent(context, SharePickerActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
      }
    }
  }
}
