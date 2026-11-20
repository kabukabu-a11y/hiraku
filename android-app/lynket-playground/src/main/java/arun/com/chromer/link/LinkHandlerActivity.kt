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

package arun.com.chromer.link

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.customtab.LinkDispatcher
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.customtab.UrlSafety
import arun.com.chromer.customtab.displayLabel
import arun.com.chromer.customtab.iconPackage
import arun.com.chromer.history.HistoryStore
import arun.com.chromer.theme.LynketTheme
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point for links from other apps (the default-browser target).
 * Resolves the open method from the per-app rule / default, dispatches,
 * and finishes, so Back from wherever the link opened returns to the
 * app the user came from. When the method is ASK, shows the chooser
 * sheet with a page preview instead.
 *
 * v2.1.0: an on-device URL safety check can warn before opening, and
 * secret mode (global or per source app) suppresses history records.
 */
class LinkHandlerActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val url = extractUrl(intent)
    if (url.isNullOrBlank()) {
      finish()
      return
    }
    val normalized = BrowserActivity.normalizeUrl(url)
    if (normalized.isEmpty()) {
      finish()
      return
    }

    val forced = OpenMethod.fromName(intent.getStringExtra(EXTRA_METHOD))
    val referrer = referrerPackage()
    val secret = intent.getBooleanExtra(EXTRA_SECRET, false) ||
      LinkPreferences.isSecretForUrl(this, referrer, normalized)

    // Safety check: only for links arriving from other apps, not for
    // Hiraku's own internal dispatches.
    if (forced == null &&
      intent.action == Intent.ACTION_VIEW &&
      LinkPreferences.isSecurityCheckEnabled(this)
    ) {
      val warnings = UrlSafety.warnings(normalized)
      if (warnings.isNotEmpty()) {
        setContent {
          LynketTheme {
            SafetyScreen(
              url = normalized,
              warnings = warnings,
              onProceed = { proceed(normalized, forced, referrer, secret) },
              onCancel = { finish() }
            )
          }
        }
        return
      }
    }

    proceed(normalized, forced, referrer, secret)
  }

  private fun proceed(
    normalized: String,
    forced: OpenMethod?,
    referrer: String?,
    secret: Boolean
  ) {
    val method = forced ?: LinkPreferences.resolveMethodForUrl(this, referrer, normalized)

    if (method == OpenMethod.ASK && forced == null) {
      val referrerLabel = referrer?.let { appLabel(it) }
      setContent {
        LynketTheme {
          ChooserScreen(
            url = normalized,
            referrerPackage = referrer,
            referrerLabel = referrerLabel,
            secret = secret
          )
        }
      }
      return
    }

    if (method != OpenMethod.CANCEL && !secret) {
      HistoryStore.record(this, normalized, normalized)
    }
    LinkDispatcher.open(this, normalized, method, secret)
    finish()
  }

  private fun extractUrl(intent: Intent?): String? {
    if (intent == null) return null
    val extra = intent.getStringExtra(EXTRA_URL)
    if (!extra.isNullOrBlank()) return extra
    return intent.dataString
  }

  private fun referrerPackage(): String? {
    return try {
      val ref = referrer
      if (ref != null && ref.scheme == "android-app") ref.host else null
    } catch (_: Exception) {
      null
    }
  }

  private fun appLabel(packageName: String): String? {
    return try {
      packageManager
        .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
        .toString()
    } catch (_: Exception) {
      null
    }
  }

  @Composable
  private fun SafetyScreen(
    url: String,
    warnings: List<Int>,
    onProceed: () -> Unit,
    onCancel: () -> Unit
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0x99000000))
        .clickable { onCancel() },
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
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = stringResource(R.string.safety_title),
            style = MaterialTheme.typography.titleMedium
          )
          for (warning in warnings) {
            Text(
              text = "• " + stringResource(warning),
              style = MaterialTheme.typography.bodyMedium
            )
          }
          Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            OutlinedButton(
              onClick = onCancel,
              modifier = Modifier.weight(1f)
            ) {
              Text(stringResource(R.string.method_cancel))
            }
            Button(
              onClick = onProceed,
              modifier = Modifier.weight(1f)
            ) {
              Text(stringResource(R.string.safety_open_anyway))
            }
          }
        }
      }
    }
  }

  @Composable
  private fun ChooserScreen(
    url: String,
    referrerPackage: String?,
    referrerLabel: String?,
    secret: Boolean
  ) {
    val context = LocalContext.current
    val previewEnabled = remember { LinkPreferences.isPreviewEnabled(context) }
    val animLevel = remember { LinkPreferences.getAnimLevel(context) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var favicon by remember { mutableStateOf<Bitmap?>(null) }
    var collapsed by remember { mutableStateOf(false) }
    var fixForApp by remember { mutableStateOf(false) }
    var fixForSite by remember { mutableStateOf(false) }
    var secretOn by remember { mutableStateOf(secret) }
    var sheetShown by remember { mutableStateOf(animLevel == LinkPreferences.ANIM_OFF) }
    var rowsShown by remember { mutableIntStateOf(0) }

    val host = remember(url) {
      try {
        Uri.parse(url).host
      } catch (_: Exception) {
        null
      }
    }
    val safetyWarnings = remember(url) {
      if (LinkPreferences.isSecurityCheckEnabled(context)) {
        UrlSafety.warnings(url)
      } else {
        emptyList()
      }
    }
    val securityCheckOn = remember { LinkPreferences.isSecurityCheckEnabled(context) }

    // The standard method is pinned to the top (v2.1.0); the rest keep
    // the user's customized order.
    val methods = remember {
      val visible = LinkPreferences.getChooserMethods(context)
      val default = LinkPreferences.getDefaultMethod(context)
      if (default in visible) {
        listOf(default) + visible.filter { it != default }
      } else {
        visible
      }
    }
    val labels = remember {
      methods.associateWith { it.displayLabel(context) }
    }
    val iconPackages = remember {
      methods.associateWith { it.iconPackage(context) }
    }

    LaunchedEffect(url) {
      sheetShown = true
      if (animLevel == LinkPreferences.ANIM_STRONG) {
        for (i in methods.indices) {
          kotlinx.coroutines.delay(45)
          rowsShown = i + 1
        }
      } else {
        rowsShown = methods.size
      }
      if (previewEnabled) {
        if (!host.isNullOrBlank()) {
          favicon = loadFavicon(host)
        }
        pageTitle = fetchPageTitle(url)
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0x99000000))
        .clickable { finish() },
      contentAlignment = Alignment.BottomCenter
    ) {
      AnimatedVisibility(
        visible = sheetShown,
        enter = when (animLevel) {
          LinkPreferences.ANIM_OFF -> EnterTransition.None
          LinkPreferences.ANIM_WEAK -> fadeIn(tween(150))
          else -> slideInVertically(
            animationSpec = spring(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow
            ),
            initialOffsetY = { it }
          ) + fadeIn(tween(200))
        },
        modifier = Modifier
          .padding(horizontal = 20.dp, vertical = 20.dp)
          .widthIn(max = 440.dp)
      ) {
        Card(
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = false) { }
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            // v2.2.0: a long drag bar replaces the collapse button.
            // Drag it down to shrink the sheet, up to expand it.
            var dragTotal by remember { mutableStateOf(0f) }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                  detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dragAmount ->
                      dragTotal += dragAmount
                      if (dragTotal > 56f && !collapsed) {
                        collapsed = true
                        dragTotal = 0f
                      } else if (dragTotal < -56f && collapsed) {
                        collapsed = false
                        dragTotal = 0f
                      }
                    }
                  )
                }
                .clickable { collapsed = !collapsed },
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .width(96.dp)
                  .height(5.dp)
                  .clip(RoundedCornerShape(50))
                  .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.chooser_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
              )
              Text(
                text = stringResource(R.string.secret_mode),
                style = MaterialTheme.typography.bodyMedium
              )
              Spacer(modifier = Modifier.width(6.dp))
              Switch(
                checked = secretOn,
                onCheckedChange = { secretOn = it }
              )
            }
            if (!collapsed) {
              if (previewEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (favicon != null) {
                    Image(
                      bitmap = favicon!!.asImageBitmap(),
                      contentDescription = null,
                      modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = pageTitle?.takeIf { it.isNotBlank() } ?: url,
                      style = MaterialTheme.typography.bodyLarge,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = url,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              } else {
                Text(
                  text = url,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              // v2.2.0: the URL safety result lives inside the sheet.
              if (securityCheckOn) {
                Spacer(modifier = Modifier.height(6.dp))
                if (safetyWarnings.isEmpty()) {
                  Text(
                    text = "✓ " + stringResource(R.string.safety_ok),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                  )
                } else {
                  Text(
                    text = "⚠ " + stringResource(R.string.safety_warn_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                  for (warning in safetyWarnings) {
                    Text(
                      text = "• " + stringResource(warning),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error
                    )
                  }
                }
              }
              if (referrerPackage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fixForApp = !fixForApp }
                ) {
                  Checkbox(
                    checked = fixForApp,
                    onCheckedChange = { fixForApp = it }
                  )
                  Text(
                    text = stringResource(
                      R.string.chooser_fix_for_app,
                      referrerLabel ?: referrerPackage
                    ),
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              if (!host.isNullOrBlank()) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fixForSite = !fixForSite }
                ) {
                  Checkbox(
                    checked = fixForSite,
                    onCheckedChange = { fixForSite = it }
                  )
                  Text(
                    text = stringResource(R.string.chooser_fix_for_site) + "（$host）",
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              methods.forEachIndexed { index, method ->
                AnimatedVisibility(
                  visible = index < rowsShown,
                  enter = if (animLevel == LinkPreferences.ANIM_STRONG) {
                    fadeIn(tween(180)) + slideInVertically(
                      animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                      ),
                      initialOffsetY = { it / 2 }
                    )
                  } else {
                    fadeIn(tween(120))
                  }
                ) {
                  MethodRow(
                    method = method,
                    label = labels[method] ?: stringResource(method.labelRes),
                    iconPackage = iconPackages[method],
                    animLevel = animLevel,
                    onClick = {
                      if (method != OpenMethod.CANCEL && method != OpenMethod.ASK) {
                        if (fixForApp && referrerPackage != null) {
                          LinkPreferences.setAppRule(context, referrerPackage, method)
                        }
                        if (fixForSite && !host.isNullOrBlank()) {
                          LinkPreferences.setSiteRule(context, host, method)
                        }
                      }
                      if (method != OpenMethod.CANCEL && !secretOn) {
                        HistoryStore.record(context, url, pageTitle ?: url)
                      }
                      LinkDispatcher.open(
                        this@LinkHandlerActivity,
                        url,
                        method,
                        secretOn
                      )
                      finish()
                    }
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun MethodRow(
    method: OpenMethod,
    label: String,
    iconPackage: String?,
    animLevel: Int,
    onClick: () -> Unit
  ) {
    val context = LocalContext.current
    val iconBitmap = remember(iconPackage) {
      if (iconPackage == null) {
        null
      } else {
        try {
          val drawable = context.packageManager.getApplicationIcon(iconPackage)
          val w = drawable.intrinsicWidth.coerceAtLeast(1)
          val h = drawable.intrinsicHeight.coerceAtLeast(1)
          val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
          val canvas = android.graphics.Canvas(bitmap)
          drawable.setBounds(0, 0, w, h)
          drawable.draw(canvas)
          bitmap.asImageBitmap()
        } catch (_: Exception) {
          null
        }
      }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .scale(if (pressed && animLevel != LinkPreferences.ANIM_OFF) 0.97f else 1f)
        .clickable(
          interactionSource = interaction,
          indication = null,
          onClick = onClick
        )
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap,
          contentDescription = null,
          modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
        )
      } else {
        MethodGlyph(method)
      }
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }

  /** Simple one-color glyphs for the function-style methods. */
  @Composable
  private fun MethodGlyph(method: OpenMethod) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
      modifier = Modifier
        .size(30.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      when (method) {
        OpenMethod.STAY_OVERLAY -> Canvas(modifier = Modifier.size(18.dp)) {
          val stroke = 2.dp.toPx()
          val r = 4.dp.toPx()
          drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(1.dp.toPx(), 5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 12.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = Stroke(width = stroke)
          )
          drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 12.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = Stroke(width = stroke)
          )
        }
        OpenMethod.BUBBLE -> Canvas(modifier = Modifier.size(18.dp)) {
          drawCircle(
            color = tint,
            radius = 7.dp.toPx(),
            style = Stroke(width = 2.dp.toPx())
          )
        }
        OpenMethod.SHARE -> Icon(
          imageVector = Icons.Filled.Share,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(18.dp)
        )
        OpenMethod.CANCEL -> Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(18.dp)
        )
        else -> Text(
          text = "•",
          color = tint,
          style = MaterialTheme.typography.bodyLarge
        )
      }
    }
  }

  companion object {
    const val EXTRA_URL = "arun.com.chromer.link.EXTRA_URL"
    const val EXTRA_METHOD = "arun.com.chromer.link.EXTRA_METHOD"
    const val EXTRA_SECRET = "arun.com.chromer.link.EXTRA_SECRET"

    fun createDispatchIntent(
      context: Context,
      url: String,
      method: OpenMethod,
      secret: Boolean = false
    ): Intent {
      return Intent(context, LinkHandlerActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
        putExtra(EXTRA_METHOD, method.name)
        putExtra(EXTRA_SECRET, secret)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }

    /** Opens [url] using the resolved default / per-app method. */
    fun createOpenIntent(context: Context, url: String): Intent {
      return Intent(context, LinkHandlerActivity::class.java).apply {
        putExtra(EXTRA_URL, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }
}

private suspend fun loadFavicon(host: String): Bitmap? =
  withContext(Dispatchers.IO) {
    try {
      val connection = URL(
        "https://www.google.com/s2/favicons?domain=$host&sz=128"
      ).openConnection() as HttpURLConnection
      connection.connectTimeout = 2500
      connection.readTimeout = 2500
      connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
      null
    }
  }

private suspend fun fetchPageTitle(url: String): String? =
  withContext(Dispatchers.IO) {
    try {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.connectTimeout = 2500
      connection.readTimeout = 2500
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
      val text = connection.inputStream.bufferedReader().use { reader ->
        val buffer = CharArray(16384)
        val read = reader.read(buffer)
        if (read > 0) String(buffer, 0, read) else ""
      }
      connection.disconnect()
      val match = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
        .find(text)
      match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    }
  }
