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
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.home.HomeActivity

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
      builder.addMenuItem("Hiraku のオプション", optionsPendingIntent)
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
        builder.addMenuItem("$secondaryLabel で開く", secondaryPendingIntent)
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
      context.startActivity(BrowserActivity.createSecondaryIntent(context, url))
    } catch (_: Exception) {
    }
  }

  companion object {
    const val ACTION_OPEN_SECONDARY = "arun.com.chromer.action.OPEN_SECONDARY"
  }
}

