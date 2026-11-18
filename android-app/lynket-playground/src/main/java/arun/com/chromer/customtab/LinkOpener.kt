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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

enum class OpenMode {
  IN_APP,
  ASK,
  PROVIDER
}

object LinkPreferences {
  private const val PREFS_NAME = "link_prefs"
  private const val KEY_OPEN_MODE = "open_mode"
  private const val KEY_PROVIDER_PACKAGE = "provider_package"

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
    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).apply {
      addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val resolvedActivities = packageManager.queryIntentActivities(viewIntent, 0)
    val seenPackages = HashSet<String>()
    val providers = ArrayList<ProviderInfo>()

    for (resolveInfo in resolvedActivities) {
      val pkg = resolveInfo.activityInfo?.packageName ?: continue
      if (pkg == context.packageName) continue
      if (!seenPackages.add(pkg)) continue

      val serviceIntent = Intent(CUSTOM_TABS_SERVICE_ACTION).setPackage(pkg)
      if (packageManager.resolveService(serviceIntent, 0) != null) {
        val label = try {
          packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
          pkg
        }
        providers.add(ProviderInfo(packageName = pkg, label = label))
      }
    }

    return providers.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
  }

  fun getSystemDefaultProvider(context: Context): String? {
    return CustomTabsClient.getPackageName(context, null)
  }

  fun launchCustomTab(activity: Activity, url: String, providerPackage: String?): Boolean {
    val customTabsIntent = CustomTabsIntent.Builder()
      .setShowTitle(true)
      .build()
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
