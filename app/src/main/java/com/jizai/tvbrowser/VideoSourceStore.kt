package com.jizai.tvbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 视频源配置。
 * - type = "tmdb"：TMDB 官方 API，apiUrl 如 https://api.themoviedb.org/3，apiKey 用户自己填
 * - type = "json"：通用 JSON 源，接口返回 { "list": [ { "title", "cover", "url", "desc" } ] }
 * - type = "m3u"：M3U 直播源，apiUrl 直接填 M3U 播放列表地址（如 https://example.com/live.m3u），
 *   解析 #EXTINF 频道列表（tvg-name/tvg-logo/group-title）。不内置任何源地址，用户自己添加。
 *
 * 只支持合法视频源：不在代码里内置、推荐任何未授权片源地址。
 */
data class VideoSource(
    val name: String,
    val type: String,
    val apiUrl: String,
    val apiKey: String,
    val enabled: Boolean
)

/** 视频源：SharedPreferences 存 JSON 数组 */
class VideoSourceStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("videosources", Context.MODE_PRIVATE)

    fun list(): List<VideoSource> {
        ensureSeeded()
        return readList(prefs.getString("list", "[]") ?: "[]")
    }

    fun add(s: VideoSource) = save(list() + s)

    fun update(index: Int, s: VideoSource) {
        val cur = list().toMutableList()
        if (index in cur.indices) {
            cur[index] = s
            save(cur)
        }
    }

    fun remove(index: Int) {
        val cur = list().toMutableList()
        if (index in cur.indices) {
            cur.removeAt(index)
            save(cur)
        }
    }

    fun setEnabled(index: Int, enabled: Boolean) {
        val cur = list()
        if (index in cur.indices) update(index, cur[index].copy(enabled = enabled))
    }

    private fun save(list: List<VideoSource>) {
        prefs.edit().putString("list", writeList(list)).apply()
    }

    /** 首次运行只预置一个 TMDB 示例（Key 为空，用户自己填） */
    private fun ensureSeeded() {
        if (prefs.contains("seeded")) return
        prefs.edit().putBoolean("seeded", true).apply()
        save(
            listOf(
                VideoSource(
                    name = "TMDB 热门电影",
                    type = "tmdb",
                    apiUrl = "https://api.themoviedb.org/3",
                    apiKey = "",
                    enabled = true
                )
            )
        )
    }

    private fun readList(json: String): List<VideoSource> = try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            VideoSource(
                o.optString("name"),
                o.optString("type"),
                o.optString("apiUrl"),
                o.optString("apiKey"),
                o.optBoolean("enabled", true)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeList(list: List<VideoSource>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("type", it.type)
                    .put("apiUrl", it.apiUrl)
                    .put("apiKey", it.apiKey)
                    .put("enabled", it.enabled)
            )
        }
        return arr.toString()
    }
}
