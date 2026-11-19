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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import arun.com.chromer.readlater.ReadLaterActivity
import arun.com.chromer.readlater.ReadLaterStore
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
import kotlinx.coroutines.launch
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
  var clipboardUrl by remember { mutableStateOf<String?>(null) }
  var readLaterCount by remember {
    mutableStateOf(ReadLaterStore.count(context))
  }
  var resumeCount by remember { mutableStateOf(0) }

  fun refreshClipboard() {
    clipboardUrl = try {
      val manager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val text = manager.primaryClip
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.trim()
      text?.takeIf {
        it.length < 400 &&
          (it.startsWith("http://") || it.startsWith("https://") ||
            (!it.contains(" ") && it.contains(".")))
      }
    } catch (_: Exception) {
      null
    }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        isDefault = DefaultBrowser.isDefaultBrowser(context)
        defaultMethod = LinkPreferences.getDefaultMethod(context)
        readLaterCount = ReadLaterStore.count(context)
        resumeCount++
        refreshClipboard()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  LaunchedEffect(Unit) { refreshClipboard() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Hiraku") }
      )
    },
    bottomBar = {
      BottomNavPill(
        resetKey = resumeCount,
        onNavigate = { index ->
          when (index) {
            1 -> context.startActivity(Intent(context, TabsActivity::class.java))
            2 -> context.startActivity(Intent(context, HistoryActivity::class.java))
            3 -> context.startActivity(Intent(context, SettingsActivity::class.java))
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
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = stringResource(R.string.home_open_url),
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
              label = { Text(text = stringResource(R.string.home_url_hint)) },
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

      clipboardUrl?.let { clip ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = stringResource(R.string.clipboard_url_title),
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = clip,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { onOpenUrl(clip) }) {
              Text(stringResource(R.string.clipboard_open))
            }
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = stringResource(R.string.home_default_browser),
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (isDefault) {
              stringResource(R.string.home_is_default)
            } else {
              stringResource(R.string.home_not_default)
            }
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.home_default_desc),
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
              Text(text = stringResource(R.string.home_set_default))
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
              Text(text = stringResource(R.string.home_open_default_settings))
            }
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = stringResource(R.string.home_default_method),
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = stringResource(R.string.home_default_method_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          for (method in OpenMethod.CHOICES) {
            LinkOptionRow(
              label = stringResource(method.labelRes),
              selected = defaultMethod == method,
              onSelect = {
                LinkPreferences.setDefaultMethod(context, method)
                defaultMethod = method
              }
            )
          }
          LinkOptionRow(
            label = stringResource(OpenMethod.ASK.labelRes),
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
            text = stringResource(R.string.home_browser_primary),
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = stringResource(R.string.home_browser_primary_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LinkOptionRow(
            label = stringResource(R.string.home_browser_auto),
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
              text = stringResource(R.string.home_no_browser),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = stringResource(R.string.home_browser_secondary),
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = stringResource(R.string.home_browser_secondary_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LinkOptionRow(
            label = stringResource(R.string.home_none),
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
        Text(stringResource(R.string.app_rules_title))
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = stringResource(R.string.home_tabs_history),
            style = MaterialTheme.typography.titleMedium
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { context.startActivity(Intent(context, TabsActivity::class.java)) },
              modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.home_tabs)) }
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, HistoryActivity::class.java)) },
              modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.history_title)) }
          }
          OutlinedButton(
            onClick = {
              context.startActivity(Intent(context, ReadLaterActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(stringResource(R.string.read_later_title) + "（$readLaterCount）")
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
          modifier = Modifier.weight(1f)
        ) {
          Text(stringResource(R.string.settings_title))
        }
        OutlinedButton(
          onClick = { context.startActivity(Intent(context, SetupActivity::class.java)) },
          modifier = Modifier.weight(1f)
        ) {
          Text(stringResource(R.string.home_permissions))
        }
      }

      Text(
        text = stringResource(R.string.home_version, versionName ?: "-"),
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

/**
 * Animated bottom navigation pill (v2.2.0). The highlight stretches
 * like liquid between items: one edge springs fast, the other lags,
 * and a satellite blob rides along. On Android 12+ the shapes are
 * merged with a blur + alpha-contrast (gooey) render effect.
 */
@Composable
private fun BottomNavPill(resetKey: Int, onNavigate: (Int) -> Unit) {
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  var selected by remember { mutableIntStateOf(0) }
  LaunchedEffect(resetKey) {
    if (resetKey > 0) {
      selected = 0
    }
  }
  val labels = listOf(
    stringResource(R.string.nav_home),
    stringResource(R.string.nav_tabs),
    stringResource(R.string.nav_history),
    stringResource(R.string.nav_settings)
  )
  val scope = rememberCoroutineScope()
  Surface(tonalElevation = 3.dp) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .height(60.dp)
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      val itemWidth = maxWidth / labels.size
      val fastSpec = when (animLevel) {
        LinkPreferences.ANIM_OFF -> tween<androidx.compose.ui.unit.Dp>(durationMillis = 1)
        LinkPreferences.ANIM_WEAK -> tween(durationMillis = 160)
        else -> spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
      }
      val slowSpec = when (animLevel) {
        LinkPreferences.ANIM_OFF -> tween<androidx.compose.ui.unit.Dp>(durationMillis = 1)
        LinkPreferences.ANIM_WEAK -> tween(durationMillis = 260)
        else -> spring(
          dampingRatio = Spring.DampingRatioLowBouncy,
          stiffness = Spring.StiffnessLow
        )
      }
      val fastX by animateDpAsState(
        targetValue = itemWidth * selected,
        animationSpec = fastSpec,
        label = "navFast"
      )
      val slowX by animateDpAsState(
        targetValue = itemWidth * selected,
        animationSpec = slowSpec,
        label = "navSlow"
      )
      val left = if (fastX < slowX) fastX else slowX
      val span = if (fastX > slowX) fastX - slowX else slowX - fastX
      val pillColor = MaterialTheme.colorScheme.primary
      Box(
        modifier = Modifier
          .fillMaxSize()
          .then(gooeyLayerModifier(animLevel == LinkPreferences.ANIM_STRONG))
      ) {
        Box(
          modifier = Modifier
            .offset(x = left + 4.dp, y = 4.dp)
            .width(span + itemWidth - 8.dp)
            .height(52.dp)
            .background(pillColor, RoundedCornerShape(50))
        )
        Box(
          modifier = Modifier
            .offset(x = slowX + itemWidth / 2 - 15.dp, y = 15.dp)
            .size(30.dp)
            .background(pillColor, CircleShape)
        )
      }
      Row(modifier = Modifier.fillMaxSize()) {
        labels.forEachIndexed { index, label ->
          Box(
            modifier = Modifier
              .width(itemWidth)
              .fillMaxHeight()
              .clickable {
                if (index != selected) {
                  selected = index
                  if (index != 0) {
                    scope.launch {
                      delay(150)
                      onNavigate(index)
                    }
                  }
                }
              },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge,
              color = if (index == selected) {
                MaterialTheme.colorScheme.onPrimary
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

/** Blur + alpha contrast, the classic gooey merge (API 31+). */
private fun gooeyLayerModifier(enabled: Boolean): Modifier {
  if (!enabled || Build.VERSION.SDK_INT < 31) return Modifier
  val effect = try {
    val blur = android.graphics.RenderEffect.createBlurEffect(
      10f,
      10f,
      android.graphics.Shader.TileMode.CLAMP
    )
    val matrix = android.graphics.ColorMatrix(
      floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 30f, -3600f
      )
    )
    val contrast = android.graphics.RenderEffect.createColorFilterEffect(
      android.graphics.ColorMatrixColorFilter(matrix)
    )
    android.graphics.RenderEffect.createChainEffect(contrast, blur)
  } catch (_: Exception) {
    null
  }
  return if (effect == null) {
    Modifier
  } else {
    Modifier.graphicsLayer { renderEffect = effect.asComposeRenderEffect() }
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
