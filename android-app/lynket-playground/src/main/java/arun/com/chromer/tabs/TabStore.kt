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

package arun.com.chromer.tabs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TabEntry(
  val url: String,
  val title: String,
  val updatedAt: Long
)

object TabStore {
  private const val PREFS_NAME = "open_tabs"
  private const val KEY_ENTRIES = "entries"
  private const val MAX_ENTRIES = 50

  private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun load(context: Context): MutableList<TabEntry> {
    val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return mutableListOf()
    return try {
      val array = JSONArray(raw)
      val list = ArrayList<TabEntry>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          TabEntry(
            url = obj.getString("url"),
            title = obj.optString("title", ""),
            updatedAt = obj.getLong("updatedAt")
          )
        )
      }
      list
    } catch (_: Exception) {
      mutableListOf()
    }
  }

  private fun save(context: Context, entries: List<TabEntry>) {
    val array = JSONArray()
    for (entry in entries) {
      val obj = JSONObject().apply {
        put("url", entry.url)
        put("title", entry.title)
        put("updatedAt", entry.updatedAt)
      }
      array.put(obj)
    }
    prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
  }

  fun upsert(context: Context, url: String, title: String) {
    if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
      return
    }
    val entries = load(context)
    entries.removeAll { it.url == url }
    entries.add(
      0,
      TabEntry(
        url = url,
        title = title.ifBlank { url },
        updatedAt = System.currentTimeMillis()
      )
    )
    val trimmed = if (entries.size > MAX_ENTRIES) {
      entries.subList(0, MAX_ENTRIES)
    } else {
      entries
    }
    save(context, trimmed)
  }

  fun getAll(context: Context): List<TabEntry> = load(context)

  fun remove(context: Context, url: String) {
    val entries = load(context)
    entries.removeAll { it.url == url }
    save(context, entries)
  }

  fun clear(context: Context) {
    prefs(context).edit().remove(KEY_ENTRIES).apply()
  }
}

