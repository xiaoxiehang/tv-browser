package com.jizai.tvbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String)

/** 书签 + 历史记录：SharedPreferences 存 JSON，MVP 够用 */
class BookmarkStore(ctx: Context) {
    private val bmPrefs = ctx.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val hiPrefs = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun list(): List<Bookmark> = readList(bmPrefs.getString("list", "[]") ?: "[]")

    fun add(title: String, url: String) {
        val cur = list().filterNot { it.url == url }.toMutableList()
        cur.add(0, Bookmark(title.ifBlank { url }, url))
        bmPrefs.edit().putString("list", writeList(cur)).apply()
    }

    fun remove(url: String) {
        bmPrefs.edit().putString("list", writeList(list().filterNot { it.url == url })).apply()
    }

    fun history(): List<Bookmark> = readList(hiPrefs.getString("list", "[]") ?: "[]")

    fun clearHistory() {
        hiPrefs.edit().remove("list").apply()
    }

    fun addHistory(title: String, url: String) {
        val cur = history().filterNot { it.url == url }.toMutableList()
        cur.add(0, Bookmark(title.ifBlank { url }, url))
        hiPrefs.edit().putString("list", writeList(cur.take(50))).apply()
    }

    private fun readList(json: String): List<Bookmark> = try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Bookmark(o.optString("title"), o.optString("url"))
        }
    } catch (_: Exception) { emptyList() }

    private fun writeList(list: List<Bookmark>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        return arr.toString()
    }
}
