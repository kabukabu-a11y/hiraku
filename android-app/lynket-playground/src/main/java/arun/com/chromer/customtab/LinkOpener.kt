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

package arun.com.chromer.customtab

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import arun.com.chromer.R
import arun.com.chromer.home.HomeActivity
import arun.com.chromer.link.LinkHandlerActivity
import org.json.JSONObject

/**
 * How a link gets opened. The first nine entries are the user-facing
 * methods from the v2 concept; ASK is the extra "choose every time"
 * pseudo-method that shows the chooser UI instead of opening directly.
 */
enum class OpenMethod(@StringRes val labelRes: Int) {
  CUSTOM_TAB_PRIMARY(R.string.method_custom_tab_primary),
  CUSTOM_TAB_SECONDARY(R.string.method_custom_tab_secondary),
  NORMAL_PRIMARY(R.string.method_normal_primary),
  NORMAL_SECONDARY(R.string.method_normal_secondary),
  CUSTOM_TAB_SYSTEM(R.string.method_custom_tab_system),
  STAY_OVERLAY(R.string.method_stay_overlay),
  BUBBLE(R.string.method_bubble),
  SHARE(R.string.method_share),
  CANCEL(R.string.method_cancel),
  ASK(R.string.method_ask);

  companion object {
    /** The nine concrete methods, in the order the user defined them. */
    val CHOICES: List<OpenMethod> = listOf(
      CUSTOM_TAB_PRIMARY,
      CUSTOM_TAB_SECONDARY,
      NORMAL_PRIMARY,
      NORMAL_SECONDARY,
      CUSTOM_TAB_SYSTEM,
      STAY_OVERLAY,
      BUBBLE,
      SHARE,
      CANCEL
    )

    fun fromName(name: String?): OpenMethod? {
      if (name == null) return null
      return try {
        valueOf(name)
      } catch (_: IllegalArgumentException) {
        null
      }
    }
  }
}

/** Legacy mode enum, kept only to migrate old settings to [OpenMethod]. */
enum class OpenMode {
  IN_APP,
  ASK,
  PROVIDER,
  OVERLAY,
  WEB_HEAD
}

object LinkPreferences {
  private const val PREFS_NAME = "link_prefs"
  private const val KEY_OPEN_MODE = "open_mode"
  private const val KEY_PROVIDER_PACKAGE = "provider_package"
  private const val KEY_SECONDARY_PROVIDER_PACKAGE = "secondary_provider_package"
  private const val KEY_HIDE_BAR_ON_SCROLL = "hide_bar_on_scroll"
  private const val KEY_DEFAULT_METHOD = "default_method"
  private const val KEY_APP_RULES = "app_rules"
  private const val KEY_BAR_ON_TOP = "bar_on_top"
  private const val KEY_OVERLAY_BLUR = "overlay_blur"
  private const val KEY_PREVIEW_ENABLED = "preview_enabled"
  private const val KEY_ADBLOCK_ENABLED = "adblock_enabled"
  private const val KEY_LANGUAGE = "app_language"

  private fun prefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun getMode(context: Context): OpenMode {
    val value = prefs(context).getString(KEY_OPEN_MODE, null) ?: return OpenMode.PROVIDER
    return try {
      OpenMode.valueOf(value)
    } catch (_: IllegalArgumentException) {
      OpenMode.PROVIDER
    }
  }

  fun setMode(context: Context, mode: OpenMode) {
    prefs(context).edit().putString(KEY_OPEN_MODE, mode.name).apply()
  }

  fun getProviderPackage(context: Context): String? {
    return prefs(context).getString(KEY_PROVIDER_PACKAGE, null)
  }

  fun setProviderPackage(context: Context, packageName: String?) {
    prefs(context).edit().apply {
      if (packageName == null) {
        remove(KEY_PROVIDER_PACKAGE)
      } else {
        putString(KEY_PROVIDER_PACKAGE, packageName)
      }
    }.apply()
  }

  fun getSecondaryProviderPackage(context: Context): String? {
    return prefs(context).getString(KEY_SECONDARY_PROVIDER_PACKAGE, null)
  }

  fun setSecondaryProviderPackage(context: Context, packageName: String?) {
    prefs(context).edit().apply {
      if (packageName == null) {
        remove(KEY_SECONDARY_PROVIDER_PACKAGE)
      } else {
        putString(KEY_SECONDARY_PROVIDER_PACKAGE, packageName)
      }
    }.apply()
  }

  fun getHideBarOnScroll(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_HIDE_BAR_ON_SCROLL, false)
  }

  fun setHideBarOnScroll(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_HIDE_BAR_ON_SCROLL, enabled).apply()
  }

  // ----- v2: default method and per-app rules -----

  /**
   * The standard way links open. Until the user picks one explicitly,
   * the standard is the overlay (stay-in-app) method, per the v2
   * concept: everything opens overlaid unless a browser method is
   * chosen on purpose.
   */
  fun getDefaultMethod(context: Context): OpenMethod {
    val stored = OpenMethod.fromName(prefs(context).getString(KEY_DEFAULT_METHOD, null))
    if (stored != null) return stored
    return OpenMethod.STAY_OVERLAY
  }

  fun setDefaultMethod(context: Context, method: OpenMethod) {
    prefs(context).edit().putString(KEY_DEFAULT_METHOD, method.name).apply()
  }

  fun getAppRules(context: Context): Map<String, OpenMethod> {
    val raw = prefs(context).getString(KEY_APP_RULES, null) ?: return emptyMap()
    return try {
      val obj = JSONObject(raw)
      val result = LinkedHashMap<String, OpenMethod>()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val method = OpenMethod.fromName(obj.optString(key, null))
        if (method != null && method != OpenMethod.ASK) {
          result[key] = method
        }
      }
      result
    } catch (_: Exception) {
      emptyMap()
    }
  }

  fun getAppRule(context: Context, packageName: String?): OpenMethod? {
    if (packageName.isNullOrBlank()) return null
    return getAppRules(context)[packageName]
  }

  fun setAppRule(context: Context, packageName: String, method: OpenMethod?) {
    val rules = getAppRules(context).toMutableMap()
    if (method == null) {
      rules.remove(packageName)
    } else {
      rules[packageName] = method
    }
    val obj = JSONObject()
    for ((pkg, m) in rules) {
      obj.put(pkg, m.name)
    }
    prefs(context).edit().putString(KEY_APP_RULES, obj.toString()).apply()
  }

  /** Resolves the method for a link coming from [referrerPackage]. */
  fun resolveMethod(context: Context, referrerPackage: String?): OpenMethod {
    return getAppRule(context, referrerPackage) ?: getDefaultMethod(context)
  }

  // ----- v2: display / browsing preferences -----

  fun isBarOnTop(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_BAR_ON_TOP, false)
  }

  fun setBarOnTop(context: Context, onTop: Boolean) {
    prefs(context).edit().putBoolean(KEY_BAR_ON_TOP, onTop).apply()
  }

  fun isOverlayBlurEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_OVERLAY_BLUR, true)
  }

  fun setOverlayBlurEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_OVERLAY_BLUR, enabled).apply()
  }

  fun isPreviewEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_PREVIEW_ENABLED, true)
  }

  fun setPreviewEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_PREVIEW_ENABLED, enabled).apply()
  }

  fun isAdBlockEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_ADBLOCK_ENABLED, true)
  }

  fun setAdBlockEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_ADBLOCK_ENABLED, enabled).apply()
  }

  // ----- v2.0.1: chooser screen customization -----

  private const val KEY_CHOOSER_ORDER = "chooser_order"
  private const val KEY_CHOOSER_HIDDEN = "chooser_hidden"

  /** All concrete methods in the user's chosen order. */
  fun getChooserOrder(context: Context): List<OpenMethod> {
    val all = OpenMethod.CHOICES
    val raw = prefs(context).getString(KEY_CHOOSER_ORDER, null)
    if (raw.isNullOrBlank()) return all
    val stored = raw.split(",")
      .mapNotNull { OpenMethod.fromName(it.trim()) }
      .filter { it in all }
      .distinct()
    return stored + all.filter { it !in stored }
  }

  fun setChooserOrder(context: Context, methods: List<OpenMethod>) {
    prefs(context).edit()
      .putString(KEY_CHOOSER_ORDER, methods.joinToString(",") { it.name })
      .apply()
  }

  fun getHiddenMethods(context: Context): Set<OpenMethod> {
    val raw = prefs(context).getString(KEY_CHOOSER_HIDDEN, null)
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(",")
      .mapNotNull { OpenMethod.fromName(it.trim()) }
      .toSet()
  }

  fun setMethodHidden(context: Context, method: OpenMethod, hidden: Boolean) {
    val current = getHiddenMethods(context).toMutableSet()
    if (hidden) {
      current.add(method)
    } else {
      current.remove(method)
    }
    prefs(context).edit()
      .putString(KEY_CHOOSER_HIDDEN, current.joinToString(",") { it.name })
      .apply()
  }

  /** Methods shown in chooser UIs: user order, hidden ones removed. */
  fun getChooserMethods(context: Context): List<OpenMethod> {
    val hidden = getHiddenMethods(context)
    val visible = getChooserOrder(context).filter { it !in hidden }
    return visible.ifEmpty { listOf(OpenMethod.STAY_OVERLAY) }
  }

  /** "system", "ja" or "en". */
  fun getLanguage(context: Context): String {
    return prefs(context).getString(KEY_LANGUAGE, "system") ?: "system"
  }

  fun setLanguage(context: Context, language: String) {
    prefs(context).edit().putString(KEY_LANGUAGE, language).apply()
  }

  // ----- v2.1.0: security check -----

  private const val KEY_SECURITY_CHECK = "security_check_enabled"

  fun isSecurityCheckEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_SECURITY_CHECK, true)
  }

  fun setSecurityCheckEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_SECURITY_CHECK, enabled).apply()
  }

  // ----- v2.1.0: secret mode -----

  private const val KEY_SECRET_GLOBAL = "secret_global"
  private const val KEY_SECRET_APPS = "secret_apps"

  fun isSecretGlobal(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_SECRET_GLOBAL, false)
  }

  fun setSecretGlobal(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_SECRET_GLOBAL, enabled).apply()
  }

  fun getSecretApps(context: Context): Set<String> {
    val raw = prefs(context).getString(KEY_SECRET_APPS, null)
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }

  fun setAppSecret(context: Context, packageName: String, secret: Boolean) {
    val current = getSecretApps(context).toMutableSet()
    if (secret) {
      current.add(packageName)
    } else {
      current.remove(packageName)
    }
    prefs(context).edit().putString(KEY_SECRET_APPS, current.joinToString(",")).apply()
  }

  /** Secret mode applies globally or to links coming from [referrerPackage]. */
  fun isSecret(context: Context, referrerPackage: String?): Boolean {
    if (isSecretGlobal(context)) return true
    if (referrerPackage.isNullOrBlank()) return false
    return getSecretApps(context).contains(referrerPackage)
  }

  // ----- v2.1.0: link share targets (up to 5 apps) -----

  private const val KEY_SHARE_TARGETS = "share_targets"
  const val MAX_SHARE_TARGETS = 5

  fun getShareTargets(context: Context): List<String> {
    val raw = prefs(context).getString(KEY_SHARE_TARGETS, null)
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
      .take(MAX_SHARE_TARGETS)
  }

  fun setShareTargets(context: Context, packages: List<String>) {
    val trimmed = packages.filter { it.isNotBlank() }.take(MAX_SHARE_TARGETS)
    prefs(context).edit().putString(KEY_SHARE_TARGETS, trimmed.joinToString(",")).apply()
  }

  // ----- v2.1.0: bubble position memory -----

  private const val KEY_BUBBLE_REMEMBER = "bubble_remember_pos"
  private const val KEY_BUBBLE_SIDE = "bubble_side"
  private const val KEY_BUBBLE_Y = "bubble_y"

  fun isBubbleRememberPos(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_BUBBLE_REMEMBER, true)
  }

  fun setBubbleRememberPos(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_BUBBLE_REMEMBER, enabled).apply()
  }

  /** Saved dock side: 0 = left, 1 = right, -1 = never saved. */
  fun getBubbleSide(context: Context): Int {
    return prefs(context).getInt(KEY_BUBBLE_SIDE, -1)
  }

  fun getBubbleY(context: Context): Int {
    return prefs(context).getInt(KEY_BUBBLE_Y, -1)
  }

  fun saveBubblePosition(context: Context, side: Int, y: Int) {
    prefs(context).edit()
      .putInt(KEY_BUBBLE_SIDE, side)
      .putInt(KEY_BUBBLE_Y, y)
      .apply()
  }

  // ----- v2.1.0: overlay window geometry memory -----

  private const val KEY_OVERLAY_X = "overlay_geo_x"
  private const val KEY_OVERLAY_Y = "overlay_geo_y"
  private const val KEY_OVERLAY_W = "overlay_geo_w"
  private const val KEY_OVERLAY_H = "overlay_geo_h"

  /** Saved overlay geometry as [x, y, width, height] in px, or null. */
  fun getOverlayGeometry(context: Context): IntArray? {
    val p = prefs(context)
    if (!p.contains(KEY_OVERLAY_X)) return null
    return intArrayOf(
      p.getInt(KEY_OVERLAY_X, 0),
      p.getInt(KEY_OVERLAY_Y, 0),
      p.getInt(KEY_OVERLAY_W, 0),
      p.getInt(KEY_OVERLAY_H, 0)
    )
  }

  fun saveOverlayGeometry(context: Context, x: Int, y: Int, width: Int, height: Int) {
    prefs(context).edit()
      .putInt(KEY_OVERLAY_X, x)
      .putInt(KEY_OVERLAY_Y, y)
      .putInt(KEY_OVERLAY_W, width)
      .putInt(KEY_OVERLAY_H, height)
      .apply()
  }

  fun clearOverlayGeometry(context: Context) {
    prefs(context).edit()
      .remove(KEY_OVERLAY_X)
      .remove(KEY_OVERLAY_Y)
      .remove(KEY_OVERLAY_W)
      .remove(KEY_OVERLAY_H)
      .apply()
  }

  // ----- v2.2.0: per-site (domain) rules -----

  private const val KEY_SITE_RULES = "site_rules"

  /** Normalizes a user-typed domain or URL to a bare lowercase host. */
  fun normalizeHost(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var host = raw.trim().lowercase()
    host = host.removePrefix("https://").removePrefix("http://")
    host = host.substringBefore('/').substringBefore('?').substringBefore('#')
    host = host.substringAfterLast('@')
    host = host.substringBefore(':')
    if (host.isBlank() || !host.contains('.')) return null
    return host
  }

  fun getSiteRules(context: Context): Map<String, OpenMethod> {
    val raw = prefs(context).getString(KEY_SITE_RULES, null) ?: return emptyMap()
    return try {
      val obj = JSONObject(raw)
      val result = LinkedHashMap<String, OpenMethod>()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val method = OpenMethod.fromName(obj.optString(key, null))
        if (method != null && method != OpenMethod.ASK) {
          result[key] = method
        }
      }
      result
    } catch (_: Exception) {
      emptyMap()
    }
  }

  fun getSiteRule(context: Context, url: String?): OpenMethod? {
    val host = try {
      Uri.parse(url.orEmpty()).host?.lowercase()
    } catch (_: Exception) {
      null
    } ?: return null
    val rules = getSiteRules(context)
    rules[host]?.let { return it }
    // Also try the parent domain (e.g. a rule for example.com applies
    // to www.example.com), but never collapse to a bare TLD.
    val parts = host.split('.')
    if (parts.size > 2) {
      val parent = parts.drop(1).joinToString(".")
      rules[parent]?.let { return it }
    }
    return null
  }

  fun setSiteRule(context: Context, host: String, method: OpenMethod?) {
    val normalized = normalizeHost(host) ?: return
    val rules = getSiteRules(context).toMutableMap()
    if (method == null) {
      rules.remove(normalized)
    } else {
      rules[normalized] = method
    }
    val obj = JSONObject()
    for ((domain, m) in rules) {
      obj.put(domain, m.name)
    }
    prefs(context).edit().putString(KEY_SITE_RULES, obj.toString()).apply()
  }

  /**
   * Resolution with the URL in hand: per-app rule first, then the
   * per-site rule for the URL's host, then the standard method.
   */
  fun resolveMethodForUrl(context: Context, referrerPackage: String?, url: String): OpenMethod {
    return getAppRule(context, referrerPackage)
      ?: getSiteRule(context, url)
      ?: getDefaultMethod(context)
  }

  // ----- v2.2.0: browsing display preferences -----

  private const val KEY_TEXT_ZOOM = "text_zoom"
  private const val KEY_FORCE_DARK = "force_dark_pages"
  private const val KEY_DESKTOP_DEFAULT = "desktop_default"

  /** WebView text zoom in percent (100 = page default). */
  fun getTextZoom(context: Context): Int {
    return prefs(context).getInt(KEY_TEXT_ZOOM, 100)
  }

  fun setTextZoom(context: Context, percent: Int) {
    prefs(context).edit().putInt(KEY_TEXT_ZOOM, percent).apply()
  }

  /** Force dark styling for web content where the OS allows it. */
  fun isForceDark(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_FORCE_DARK, false)
  }

  fun setForceDark(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_FORCE_DARK, enabled).apply()
  }

  /** Start browser / overlay sessions in desktop mode. */
  fun isDesktopDefault(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_DESKTOP_DEFAULT, false)
  }

  fun setDesktopDefault(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_DESKTOP_DEFAULT, enabled).apply()
  }

  // ----- v2.2.0: animation level -----

  private const val KEY_ANIM_LEVEL = "anim_level"
  const val ANIM_OFF = 0
  const val ANIM_WEAK = 1
  const val ANIM_STRONG = 2

  /** 0 = off, 1 = weak (fades only), 2 = strong (springs + stagger). */
  fun getAnimLevel(context: Context): Int {
    return prefs(context).getInt(KEY_ANIM_LEVEL, ANIM_STRONG)
  }

  fun setAnimLevel(context: Context, level: Int) {
    prefs(context).edit().putInt(KEY_ANIM_LEVEL, level.coerceIn(0, 2)).apply()
  }

  // ----- v2.2.0: overlay extras -----

  private const val KEY_OVERLAY_FREE_RESIZE = "overlay_free_resize"
  private const val KEY_OVERLAY_REMEMBER = "overlay_remember_geo"
  private const val KEY_PASSTHROUGH_TAP = "passthrough_tap_surround"

  /** PC-like resizing from any edge/corner of the overlay window. */
  fun isOverlayFreeResize(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_OVERLAY_FREE_RESIZE, true)
  }

  fun setOverlayFreeResize(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_OVERLAY_FREE_RESIZE, enabled).apply()
  }

  fun isOverlayRemember(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_OVERLAY_REMEMBER, true)
  }

  fun setOverlayRemember(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_OVERLAY_REMEMBER, enabled).apply()
  }

  /** Tapping the blurred surround enters "use the app underneath". */
  fun isPassthroughTapEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_PASSTHROUGH_TAP, true)
  }

  fun setPassthroughTapEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_PASSTHROUGH_TAP, enabled).apply()
  }

  // ----- v2.2.0: settings backup / restore -----

  /**
   * Serializes every link preference (methods, rules, display and
   * privacy settings) into one JSON document for backup files.
   */
  fun exportSettings(context: Context): String {
    val all = prefs(context).all
    val entries = JSONObject()
    for ((key, value) in all) {
      val entry = JSONObject()
      when (value) {
        is String -> {
          entry.put("t", "s")
          entry.put("v", value)
        }
        is Boolean -> {
          entry.put("t", "b")
          entry.put("v", value)
        }
        is Int -> {
          entry.put("t", "i")
          entry.put("v", value)
        }
        is Long -> {
          entry.put("t", "l")
          entry.put("v", value)
        }
        is Float -> {
          entry.put("t", "f")
          entry.put("v", value.toDouble())
        }
        is Set<*> -> {
          entry.put("t", "ss")
          entry.put("v", org.json.JSONArray(value.map { it.toString() }))
        }
        else -> continue
      }
      entries.put(key, entry)
    }
    return JSONObject()
      .put("app", "hiraku-settings")
      .put("version", 1)
      .put("prefs", entries)
      .toString(2)
  }

  /** Restores a document produced by [exportSettings]. */
  fun importSettings(context: Context, json: String): Boolean {
    return try {
      val root = JSONObject(json)
      if (root.optString("app") != "hiraku-settings") return false
      val entries = root.getJSONObject("prefs")
      val editor = prefs(context).edit()
      editor.clear()
      val keys = entries.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val entry = entries.getJSONObject(key)
        when (entry.optString("t")) {
          "s" -> editor.putString(key, entry.optString("v"))
          "b" -> editor.putBoolean(key, entry.optBoolean("v"))
          "i" -> editor.putInt(key, entry.optInt("v"))
          "l" -> editor.putLong(key, entry.optLong("v"))
          "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
          "ss" -> {
            val array = entry.optJSONArray("v")
            val set = HashSet<String>()
            if (array != null) {
              for (i in 0 until array.length()) {
                set.add(array.optString(i))
              }
            }
            editor.putStringSet(key, set)
          }
        }
      }
      editor.apply()
      true
    } catch (_: Exception) {
      false
    }
  }
}

/**
 * The label shown for this method in chooser UIs and menus. For the
 * browser methods, the generic wording is replaced by the actual
 * browser app name the user picked (v2.1.0, item 8).
 */
fun OpenMethod.displayLabel(context: Context): String {
  val base = context.getString(labelRes)
  fun appLabel(packageName: String?): String? {
    if (packageName.isNullOrBlank() || packageName == context.packageName) return null
    return try {
      context.packageManager
        .getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0))
        .toString()
    } catch (_: Exception) {
      null
    }
  }
  return when (this) {
    OpenMethod.CUSTOM_TAB_PRIMARY ->
      appLabel(CustomTabProviders.resolveProvider(context))
        ?.let { "$it (Custom Tab)" } ?: base
    OpenMethod.NORMAL_PRIMARY ->
      appLabel(CustomTabProviders.resolveProvider(context)) ?: base
    OpenMethod.CUSTOM_TAB_SECONDARY ->
      appLabel(LinkPreferences.getSecondaryProviderPackage(context))
        ?.let { "$it (Custom Tab)" } ?: base
    OpenMethod.NORMAL_SECONDARY ->
      appLabel(LinkPreferences.getSecondaryProviderPackage(context)) ?: base
    OpenMethod.CUSTOM_TAB_SYSTEM ->
      appLabel(CustomTabProviders.getSystemDefaultProvider(context))
        ?.let { "$it (Custom Tab)" } ?: base
    else -> base
  }
}

/**
 * The browser package whose real app icon represents this method in
 * chooser UIs (v2.2.0), or null for the function-style methods
 * (overlay / bubble / share / cancel), which get simple glyphs.
 */
fun OpenMethod.iconPackage(context: Context): String? {
  return when (this) {
    OpenMethod.CUSTOM_TAB_PRIMARY, OpenMethod.NORMAL_PRIMARY ->
      CustomTabProviders.resolveProvider(context)
    OpenMethod.CUSTOM_TAB_SECONDARY, OpenMethod.NORMAL_SECONDARY ->
      LinkPreferences.getSecondaryProviderPackage(context)
    OpenMethod.CUSTOM_TAB_SYSTEM ->
      CustomTabProviders.getSystemDefaultProvider(context)
        ?.takeIf { it != context.packageName }
        ?: CustomTabProviders.resolveProvider(context)
    else -> null
  }
}

data class ProviderInfo(
  val packageName: String,
  val label: String
)

object CustomTabProviders {
  const val CUSTOM_TABS_SERVICE_ACTION =
    "android.support.customtabs.action.CustomTabsService"

  @Suppress("DEPRECATION")
  fun findProviders(context: Context): List<ProviderInfo> {
    val packageManager = context.packageManager
    val packages = LinkedHashSet<String>()

    // Method 1: ask directly who provides the Custom Tabs service.
    try {
      val serviceIntent = Intent(CUSTOM_TABS_SERVICE_ACTION)
      for (resolveInfo in packageManager.queryIntentServices(serviceIntent, 0)) {
        val pkg = resolveInfo.serviceInfo?.packageName ?: continue
        if (pkg != context.packageName) {
          packages.add(pkg)
        }
      }
    } catch (_: Exception) {
    }

    // Method 2: VIEW handlers that also expose the service.
    try {
      val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
      }
      for (resolveInfo in packageManager.queryIntentActivities(viewIntent, 0)) {
        val pkg = resolveInfo.activityInfo?.packageName ?: continue
        if (pkg == context.packageName || packages.contains(pkg)) continue
        val check = Intent(CUSTOM_TABS_SERVICE_ACTION).setPackage(pkg)
        if (packageManager.resolveService(check, 0) != null) {
          packages.add(pkg)
        }
      }
    } catch (_: Exception) {
    }

    return packages.map { pkg ->
      val label = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
      } catch (_: Exception) {
        pkg
      }
      ProviderInfo(packageName = pkg, label = label)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
  }

  fun getSystemDefaultProvider(context: Context): String? {
    return CustomTabsClient.getPackageName(context, null)
  }

  /**
   * Resolves which package should provide the Custom Tab, in order:
   * the user's stored choice, the first detected Custom Tabs browser,
   * then the system default. Never returns this app's own package:
   * when Hiraku itself is the system default browser, the system
   * default lookup is useless (and can return null), so a real
   * browser must be picked from the detected list instead.
   */
  fun resolveProvider(context: Context): String? {
    val stored = LinkPreferences.getProviderPackage(context)
    if (stored != null && stored != context.packageName) {
      return stored
    }
    val found = findProviders(context)
    if (found.isNotEmpty()) {
      return found[0].packageName
    }
    return getSystemDefaultProvider(context)?.takeIf { it != context.packageName }
  }

  private const val REQUEST_CODE_OPTIONS = 1001
  private const val REQUEST_CODE_SECONDARY = 1002

  fun launchCustomTab(activity: Activity, url: String, providerPackage: String?): Boolean {
    if (providerPackage != null && providerPackage == activity.packageName) {
      return false
    }
    val builder = CustomTabsIntent.Builder()
      .setShowTitle(true)

    // Menu item: jump back to Hiraku's home (options) screen.
    try {
      val optionsIntent = Intent(activity, HomeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      val optionsPendingIntent = PendingIntent.getActivity(
        activity,
        REQUEST_CODE_OPTIONS,
        optionsIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      builder.addMenuItem(activity.getString(R.string.ct_options), optionsPendingIntent)
    } catch (_: Exception) {
    }

    // Menu item: reopen the current page in the secondary browser.
    // The browser fills in the current page URL as the intent data when
    // the item is tapped, so the PendingIntent must stay mutable.
    val secondary = LinkPreferences.getSecondaryProviderPackage(activity)
    if (secondary != null && secondary != providerPackage && secondary != activity.packageName) {
      try {
        val secondaryIntent = Intent(activity, MenuActionReceiver::class.java)
          .setAction(MenuActionReceiver.ACTION_OPEN_SECONDARY)
        val secondaryPendingIntent = PendingIntent.getBroadcast(
          activity,
          REQUEST_CODE_SECONDARY,
          secondaryIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val secondaryLabel = try {
          activity.packageManager
            .getApplicationLabel(activity.packageManager.getApplicationInfo(secondary, 0))
            .toString()
        } catch (_: Exception) {
          secondary
        }
        builder.addMenuItem(
          activity.getString(R.string.ct_open_in, secondaryLabel),
          secondaryPendingIntent
        )
      } catch (_: Exception) {
      }
    }

    val customTabsIntent = builder.build()
    if (providerPackage != null) {
      customTabsIntent.intent.setPackage(providerPackage)
    }
    return try {
      customTabsIntent.launchUrl(activity, Uri.parse(url))
      true
    } catch (_: Exception) {
      false
    }
  }

  /** Opens the browser app itself (its normal UI) showing [url]. */
  fun launchNormalBrowser(context: Context, url: String, providerPackage: String?): Boolean {
    if (providerPackage != null && providerPackage == context.packageName) {
      return false
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addCategory(Intent.CATEGORY_BROWSABLE)
      if (providerPackage != null) {
        setPackage(providerPackage)
      }
      if (context !is Activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
    return try {
      context.startActivity(intent)
      true
    } catch (_: Exception) {
      false
    }
  }
}

/**
 * Handles the Custom Tab menu item that reopens the current page in the
 * secondary browser. The browser fills in the current page URL as the
 * intent data when the menu item is tapped.
 */
class MenuActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_OPEN_SECONDARY) return
    val url = intent.dataString
    if (url.isNullOrBlank()) return
    try {
      context.startActivity(LinkHandlerActivity.createDispatchIntent(context, url, OpenMethod.CUSTOM_TAB_SECONDARY))
    } catch (_: Exception) {
    }
  }

  companion object {
    const val ACTION_OPEN_SECONDARY = "arun.com.chromer.action.OPEN_SECONDARY"
  }
}
