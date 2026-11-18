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
import android.widget.Toast
import arun.com.chromer.R
import arun.com.chromer.browser.BrowserActivity
import arun.com.chromer.bubbles.BubbleService
import arun.com.chromer.overlay.OverlayService

/**
 * Executes an [OpenMethod] for a URL. Browser-based methods need an
 * [Activity] for Custom Tabs; when called without one (from a service),
 * Custom Tab methods fall back to the browser's normal mode.
 */
object LinkDispatcher {

  private fun overlayPermissionMessage(context: Context): String =
    context.getString(R.string.overlay_permission_required)

  fun open(activity: Activity, url: String, method: OpenMethod) {
    when (method) {
      OpenMethod.CUSTOM_TAB_PRIMARY -> {
        val pkg = CustomTabProviders.resolveProvider(activity)
        if (pkg == null || !CustomTabProviders.launchCustomTab(activity, url, pkg)) {
          openInApp(activity, url)
        }
      }
      OpenMethod.CUSTOM_TAB_SECONDARY -> {
        val secondary = LinkPreferences.getSecondaryProviderPackage(activity)
        if (secondary == null ||
          !CustomTabProviders.launchCustomTab(activity, url, secondary)
        ) {
          val pkg = CustomTabProviders.resolveProvider(activity)
          if (pkg == null || !CustomTabProviders.launchCustomTab(activity, url, pkg)) {
            openInApp(activity, url)
          }
        }
      }
      OpenMethod.NORMAL_PRIMARY -> {
        val pkg = CustomTabProviders.resolveProvider(activity)
        if (pkg == null || !CustomTabProviders.launchNormalBrowser(activity, url, pkg)) {
          openInApp(activity, url)
        }
      }
      OpenMethod.NORMAL_SECONDARY -> {
        val secondary = LinkPreferences.getSecondaryProviderPackage(activity)
        if (secondary == null ||
          !CustomTabProviders.launchNormalBrowser(activity, url, secondary)
        ) {
          val pkg = CustomTabProviders.resolveProvider(activity)
          if (pkg == null || !CustomTabProviders.launchNormalBrowser(activity, url, pkg)) {
            openInApp(activity, url)
          }
        }
      }
      OpenMethod.CUSTOM_TAB_SYSTEM -> {
        val pkg = CustomTabProviders.getSystemDefaultProvider(activity)
          ?.takeIf { it != activity.packageName }
          ?: CustomTabProviders.resolveProvider(activity)
        if (pkg == null || !CustomTabProviders.launchCustomTab(activity, url, pkg)) {
          openInApp(activity, url)
        }
      }
      OpenMethod.STAY_OVERLAY -> openOverlay(activity, url)
      OpenMethod.BUBBLE -> openBubble(activity, url)
      OpenMethod.SHARE -> share(activity, url)
      OpenMethod.CANCEL -> Unit
      OpenMethod.ASK -> openInApp(activity, url)
    }
  }

  /** Context-only variant used from overlay / bubble services. */
  fun open(context: Context, url: String, method: OpenMethod) {
    when (method) {
      OpenMethod.STAY_OVERLAY -> openOverlay(context, url)
      OpenMethod.BUBBLE -> openBubble(context, url)
      OpenMethod.SHARE -> share(context, url)
      OpenMethod.CANCEL -> Unit
      OpenMethod.ASK -> openInApp(context, url)
      else -> {
        // Browser methods need an activity for Custom Tabs; use the
        // browser's normal mode instead when there is none.
        val pkg = when (method) {
          OpenMethod.CUSTOM_TAB_SECONDARY, OpenMethod.NORMAL_SECONDARY ->
            LinkPreferences.getSecondaryProviderPackage(context)
              ?: CustomTabProviders.resolveProvider(context)
          OpenMethod.CUSTOM_TAB_SYSTEM ->
            CustomTabProviders.getSystemDefaultProvider(context)
              ?.takeIf { it != context.packageName }
              ?: CustomTabProviders.resolveProvider(context)
          else -> CustomTabProviders.resolveProvider(context)
        }
        if (pkg == null || !CustomTabProviders.launchNormalBrowser(context, url, pkg)) {
          openInApp(context, url)
        }
      }
    }
  }

  fun openInApp(context: Context, url: String) {
    try {
      context.startActivity(
        BrowserActivity.createDirectIntent(context, url)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (_: Exception) {
    }
  }

  fun openOverlay(context: Context, url: String, peek: Boolean = false) {
    if (OverlayService.open(context, url, peek)) {
      return
    }
    Toast.makeText(context, overlayPermissionMessage(context), Toast.LENGTH_LONG).show()
    try {
      context.startActivity(
        OverlayService.createPermissionIntent(context)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (_: Exception) {
    }
    openInApp(context, url)
  }

  fun openBubble(context: Context, url: String) {
    if (BubbleService.open(context, url)) {
      return
    }
    Toast.makeText(context, overlayPermissionMessage(context), Toast.LENGTH_LONG).show()
    openInApp(context, url)
  }

  fun share(context: Context, url: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
    }
    try {
      context.startActivity(
        Intent.createChooser(sendIntent, null)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (_: Exception) {
    }
  }
}
