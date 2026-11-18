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

package arun.com.chromer.browser

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object DefaultBrowser {

  fun isDefaultBrowser(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    } else {
      getDefaultBrowserPackage(context) == context.packageName
    }
  }

  fun getDefaultBrowserPackage(context: Context): String? {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).apply {
      addCategory(Intent.CATEGORY_BROWSABLE)
    }
    return try {
      context.packageManager
        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName
    } catch (_: Exception) {
      null
    }
  }

  fun createRequestIntent(context: Context): Intent {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
          return roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
        }
      }
      Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    } catch (_: Exception) {
      Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }
  }
}

