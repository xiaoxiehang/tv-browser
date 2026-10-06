package com.jizai.tvbrowser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * 极简广告拦截：按域名后缀匹配 res/raw/adblock_rules.txt。
 * 规则只做子资源拦截（主页面导航不拦），匹配在后台线程执行，规则集加载后不可变。
 */
object AdBlocker {
    @Volatile
    private var rules: Set<String>? = null

    fun init(ctx: Context) {
        if (rules != null) return
        synchronized(this) {
            if (rules != null) return
            rules = try {
                ctx.resources.openRawResource(R.raw.adblock_rules).bufferedReader().readLines()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }
    }

    fun shouldBlock(url: String?): Boolean {
        val r = rules ?: return false
        if (r.isEmpty() || url.isNullOrBlank()) return false
        val host = try {
            java.net.URI(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }
        var h: String? = host
        while (h != null) {
            if (r.contains(h)) return true
            val rest = h.substringAfter('.', "")
            h = rest.ifEmpty { null }
        }
        return false
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain", "utf-8", 204, "No Content",
            mutableMapOf(), ByteArrayInputStream(ByteArray(0))
        )
}
