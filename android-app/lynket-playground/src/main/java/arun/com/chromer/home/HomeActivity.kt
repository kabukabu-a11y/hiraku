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
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import arun.com.chromer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import arun.com.chromer.apps.AppRulesActivity
import arun.com.chromer.apps.AppRulesBody
import arun.com.chromer.apps.SiteRulesBody
import arun.com.chromer.browser.DefaultBrowser
import arun.com.chromer.customtab.CustomTabProviders
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.di.HasInjector
import arun.com.chromer.history.HistoryActivity
import arun.com.chromer.history.HistoryList
import arun.com.chromer.link.LinkHandlerActivity
import arun.com.chromer.readlater.ReadLaterActivity
import arun.com.chromer.readlater.ReadLaterList
import arun.com.chromer.readlater.ReadLaterStore
import arun.com.chromer.settings.SettingsActivity
import arun.com.chromer.settings.SettingsScreen
import arun.com.chromer.setup.SetupActivity
import arun.com.chromer.setup.SetupPreferences
import arun.com.chromer.tabs.TabsActivity
import arun.com.chromer.theme.LynketTheme
import arun.com.chromer.ui.HirakuBottomBar
import arun.com.chromer.ui.HirakuTabLabels
import arun.com.chromer.ui.IosMotion
import arun.com.chromer.ui.GlassTabSwitch
import com.deliveryhero.whetstone.activity.ContributesActivityInjector
import com.qmdeve.liquidglass.widget.LiquidGlassView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * The main screen (v2.3.0): a tab host for Home / Apps & Sites /
 * History / Settings. The glass bottom bar stays on screen the
 * whole time and tab changes animate underneath it, so navigation
 * never makes the bar disappear or the page jump.
 */
@ContributesActivityInjector
class HomeActivity : ComponentActivity(), HasInjector {

  private val tabState = mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tabState.intValue = intent.getIntExtra(EXTRA_TAB, 0).coerceIn(0, 3)
    // v2.3.6 trial: on Android 13+ with glass enabled, the main
    // bar's body is a real liquid-glass view (QmDeve
    // AndroidLiquidGlassView — refraction + dispersion) layered
    // between the tab content and the bar composition, which
    // then draws only its shadow, pill and items. Older devices
    // and glass-off keep the classic single-composition path
    // with the in-house glass.
    if (Build.VERSION.SDK_INT >= 33 && LinkPreferences.isGlassEnabled(this)) {
      setupLayeredGlass()
    } else {
      setContent {
        LynketTheme {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            HomeHost(
              tab = tabState.intValue,
              onTabChange = { tabState.intValue = it },
              onOpenUrl = { url ->
                startActivity(LinkHandlerActivity.createOpenIntent(this, url))
              }
            )
          }
        }
      }
    }
    if (!SetupPreferences.isCompleted(this)) {
      startActivity(Intent(this, SetupActivity::class.java))
    }
  }

  /**
   * The layered home screen for the liquid-glass trial: tab
   * content in its own ComposeView (the glass view's sampling
   * source), the LiquidGlassView aligned exactly with the bar
   * body (20dp side margins, 62dp tall, 8dp above the bottom —
   * the MeiloX / Glasmio metrics HirakuBottomBar uses since
   * v2.3.8), and the bar in a
   * second ComposeView on top.
   */
  private fun setupLayeredGlass() {
    val density = resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()
    val root = FrameLayout(this)

    val contentView = ComposeView(this).apply {
      setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
      )
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
      setContent {
        LynketTheme {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            HomeContent(
              tab = tabState.intValue,
              onOpenUrl = { url ->
                startActivity(
                  LinkHandlerActivity.createOpenIntent(this@HomeActivity, url)
                )
              }
            )
          }
        }
      }
    }
    root.addView(contentView)

    val glassView = LiquidGlassView(this).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        dp(62)
      ).apply {
        gravity = Gravity.BOTTOM
        leftMargin = dp(20)
        rightMargin = dp(20)
        bottomMargin = dp(8)
      }
      setCornerRadius(dp(31).toFloat())
      // The in-house glass tint (0xFF1C1C1E), unchanged in dark
      // mode per the design rule; alpha matches its strength.
      setTintColorRed(0x1C / 255f)
      setTintColorGreen(0x1C / 255f)
      setTintColorBlue(0x1E / 255f)
      setTintAlpha(0.54f)
      setBlurRadius(24f)
    }
    root.addView(glassView)

    // v2.3.9 (item 96): a second, pill-sized liquid-glass
    // view layered over the bar glass and under the bar
    // composition. It slides with the indicator (fed by
    // HirakuBottomBar's onPillPosition), so the refraction
    // follows the pill instead of staying fixed to the bar
    // body. Its width is set in root.post, once the real
    // item width is known.
    var pillItemWidthPx = 0f
    val pillGlassView = LiquidGlassView(this).apply {
      layoutParams = FrameLayout.LayoutParams(0, dp(54)).apply {
        gravity = Gravity.BOTTOM or Gravity.START
        leftMargin = dp(24)
        bottomMargin = dp(12)
      }
      setCornerRadius(dp(27).toFloat())
      setTintColorRed(1f)
      setTintColorGreen(1f)
      setTintColorBlue(1f)
      setTintAlpha(0.10f)
      setBlurRadius(24f)
      isClickable = false
      isFocusable = false
    }
    root.addView(pillGlassView)

    val barView = ComposeView(this).apply {
      setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
      )
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.BOTTOM
      }
      setContent {
        LynketTheme {
          HirakuBottomBar(
            selectedIndex = tabState.intValue,
            onSelect = { tabState.intValue = it },
            externalGlass = true,
            onPillPosition = { pos ->
              pillGlassView.translationX = pos * pillItemWidthPx
            }
          )
        }
      }
    }
    root.addView(barView)

    setContentView(root)
    root.post {
      glassView.bind(contentView)
      pillGlassView.bind(contentView)
      val itemWidthPx =
        (root.width - dp(40)).toFloat() / HirakuTabLabels.size
      pillItemWidthPx = itemWidthPx
      val lp = pillGlassView.layoutParams as FrameLayout.LayoutParams
      lp.width = (itemWidthPx - dp(8)).toInt().coerceAtLeast(0)
      pillGlassView.layoutParams = lp
      // Settle the pill glass under the current tab.
      pillGlassView.translationX =
        tabState.intValue.toFloat() * itemWidthPx
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.hasExtra(EXTRA_TAB)) {
      tabState.intValue = intent.getIntExtra(EXTRA_TAB, 0).coerceIn(0, 3)
    }
  }

  companion object {
    const val EXTRA_TAB = "arun.com.chromer.home.EXTRA_TAB"
  }
}

@Composable
private fun HomeHost(
  tab: Int,
  onTabChange: (Int) -> Unit,
  onOpenUrl: (String) -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    HomeContent(tab = tab, onOpenUrl = onOpenUrl)
    HirakuBottomBar(
      selectedIndex = tab,
      onSelect = onTabChange,
      modifier = Modifier.align(Alignment.BottomCenter)
    )
  }
}

/**
 * The tab content alone (no bar). Split out of HomeHost in
 * v2.3.6 so the layered liquid-glass setup can host it in its
 * own ComposeView as the glass view's sampling source.
 */
@Composable
private fun HomeContent(
  tab: Int,
  onOpenUrl: (String) -> Unit
) {
  val context = LocalContext.current
  val animLevel = remember { LinkPreferences.getAnimLevel(context) }
  // Tab content is composed directly per tab, with only a
  // small keyed enter animation (fade + slight slide, iOS
  // CollapseSpring stiffness, response 0.3) that has no shared
  // transition state and cannot wedge. History: this replaced
  // AnimatedContent in v2.3.4 under the (wrong) theory that
  // its transition was wedging — the actual item 80 root cause
  // was in the bottom bar's gesture handler, which compared
  // taps against a selectedIndex frozen at the app-start tab
  // (see ui/BottomBar.kt, fixed in v2.3.5). The direct
  // composition stays because it is simpler and cannot wedge.
  var prevTab by remember { mutableIntStateOf(tab) }
  val direction = if (tab >= prevTab) 1 else -1
  LaunchedEffect(tab) { prevTab = tab }
  key(tab) {
    // Freeze this entry's direction at its first composition:
    // the prevTab update above recomposes this group a frame
    // later, and re-reading `direction` then would flip the
    // slide direction mid-animation.
    val entryDirection = remember { direction }
    val progress = remember {
      Animatable(if (animLevel == LinkPreferences.ANIM_OFF) 1f else 0f)
    }
    LaunchedEffect(Unit) {
      if (animLevel != LinkPreferences.ANIM_OFF) {
        progress.animateTo(
          1f,
          spring(
            dampingRatio = 1f,
            stiffness = IosMotion.stiffnessForResponse(0.3f)
          )
        )
      }
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          alpha = progress.value
          translationX = (1f - progress.value) * entryDirection * 24.dp.toPx()
        }
    ) {
      when (tab) {
        0 -> HomeTab(onOpenUrl = onOpenUrl)
        1 -> AppsSitesTab()
        2 -> HistoryTab()
        else -> SettingsScreen(
          onBack = {},
          showBack = false,
          showBottomBar = false
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(onOpenUrl: (String) -> Unit) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(title = { Text(text = "Hiraku") })
    HomeTabContent(onOpenUrl = onOpenUrl)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsSitesTab() {
  var section by remember { mutableIntStateOf(0) }
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(title = { Text(stringResource(R.string.apps_sites_title)) })
    GlassTabSwitch(
      options = listOf(
        stringResource(R.string.apps_tab_apps),
        stringResource(R.string.apps_tab_sites)
      ),
      selectedIndex = section,
      onSelect = { section = it },
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Box(modifier = Modifier.fillMaxSize()) {
      if (section == 0) {
        AppRulesBody(mode = AppRulesActivity.MODE_RULES)
      } else {
        SiteRulesBody()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTab() {
  var section by remember { mutableIntStateOf(0) }
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(title = { Text(stringResource(R.string.history_title)) })
    GlassTabSwitch(
      options = listOf(
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_bookmarks)
      ),
      selectedIndex = section,
      onSelect = { section = it },
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Box(modifier = Modifier.fillMaxSize()) {
      if (section == 0) {
        HistoryList()
      } else {
        ReadLaterList()
      }
    }
  }
}

@Composable
private fun HomeTabContent(
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
        refreshClipboard()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  LaunchedEffect(Unit) { refreshClipboard() }

  Column(
    modifier = Modifier
      .fillMaxSize()
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

    // Clearance so the last rows are never hidden behind the
    // floating bottom bar (v2.3.0).
    Spacer(modifier = Modifier.height(78.dp))
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
