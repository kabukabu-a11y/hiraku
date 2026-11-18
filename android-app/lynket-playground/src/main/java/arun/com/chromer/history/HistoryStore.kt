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

package arun.com.chromer.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
  val url: String,
  val title: String,
  val visitedAt: Long
)

object HistoryStore {
  private const val PREFS_NAME = "browsing_history"
  private const val KEY_ENTRIES = "entries"
  private const val MAX_ENTRIES = 200

  private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun load(context: Context): MutableList<HistoryEntry> {
    val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return mutableListOf()
    return try {
      val array = JSONArray(raw)
      val list = ArrayList<HistoryEntry>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          HistoryEntry(
            url = obj.getString("url"),
            title = obj.optString("title", ""),
            visitedAt = obj.getLong("visitedAt")
          )
        )
      }
      list
    } catch (_: Exception) {
      mutableListOf()
    }
  }

  private fun save(context: Context, entries: List<HistoryEntry>) {
    val array = JSONArray()
    for (entry in entries) {
      val obj = JSONObject().apply {
        put("url", entry.url)
        put("title", entry.title)
        put("visitedAt", entry.visitedAt)
      }
      array.put(obj)
    }
    prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
  }

  fun record(context: Context, url: String, title: String) {
    if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
      return
    }
    val entries = load(context)
    val newEntry = HistoryEntry(
      url = url,
      title = title.ifBlank { url },
      visitedAt = System.currentTimeMillis()
    )
    if (entries.isNotEmpty() && entries[0].url == url) {
      entries[0] = newEntry
    } else {
      entries.add(0, newEntry)
    }
    val trimmed = if (entries.size > MAX_ENTRIES) {
      entries.subList(0, MAX_ENTRIES)
    } else {
      entries
    }
    save(context, trimmed)
  }

  fun getAll(context: Context): List<HistoryEntry> = load(context)

  fun remove(context: Context, entry: HistoryEntry) {
    val entries = load(context)
    entries.removeAll { it.url == entry.url && it.visitedAt == entry.visitedAt }
    save(context, entries)
  }

  fun clear(context: Context) {
    prefs(context).edit().remove(KEY_ENTRIES).apply()
  }
}

