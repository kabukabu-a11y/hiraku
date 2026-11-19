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

package arun.com.chromer.readlater

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReadLaterEntry(
  val url: String,
  val title: String,
  val savedAt: Long
)

/** Pages the user parked with "read later" (v2.2.0). */
object ReadLaterStore {
  private const val PREFS_NAME = "read_later"
  private const val KEY_ENTRIES = "entries"
  private const val MAX_ENTRIES = 200

  private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun load(context: Context): MutableList<ReadLaterEntry> {
    val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return mutableListOf()
    return try {
      val array = JSONArray(raw)
      val list = ArrayList<ReadLaterEntry>(array.length())
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          ReadLaterEntry(
            url = obj.getString("url"),
            title = obj.optString("title", ""),
            savedAt = obj.getLong("savedAt")
          )
        )
      }
      list
    } catch (_: Exception) {
      mutableListOf()
    }
  }

  private fun save(context: Context, entries: List<ReadLaterEntry>) {
    val array = JSONArray()
    for (entry in entries) {
      val obj = JSONObject().apply {
        put("url", entry.url)
        put("title", entry.title)
        put("savedAt", entry.savedAt)
      }
      array.put(obj)
    }
    prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
  }

  /** Adds (or refreshes) a page. Returns true when it was newly added. */
  fun add(context: Context, url: String, title: String): Boolean {
    if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
      return false
    }
    val entries = load(context)
    val existed = entries.any { it.url == url }
    entries.removeAll { it.url == url }
    entries.add(
      0,
      ReadLaterEntry(
        url = url,
        title = title.ifBlank { url },
        savedAt = System.currentTimeMillis()
      )
    )
    val trimmed = if (entries.size > MAX_ENTRIES) {
      entries.subList(0, MAX_ENTRIES)
    } else {
      entries
    }
    save(context, trimmed)
    return !existed
  }

  fun getAll(context: Context): List<ReadLaterEntry> = load(context)

  fun count(context: Context): Int = load(context).size

  fun remove(context: Context, entry: ReadLaterEntry) {
    val entries = load(context)
    entries.removeAll { it.url == entry.url && it.savedAt == entry.savedAt }
    save(context, entries)
  }

  fun clear(context: Context) {
    prefs(context).edit().remove(KEY_ENTRIES).apply()
  }
}
