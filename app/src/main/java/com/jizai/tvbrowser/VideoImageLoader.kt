package com.jizai.tvbrowser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL

/** 极简图片加载：内存缓存 + 后台线程，不引入第三方库 */
object VideoImageLoader {
    private val cache = LruCache<String, Bitmap>(48)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, view: ImageView) {
        if (url.isBlank()) return
        view.tag = url
        cache.get(url)?.let {
            if (view.tag == url) view.setImageBitmap(it)
            return
        }
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()
                if (bmp != null) {
                    cache.put(url, bmp)
                    main.post { if (view.tag == url) view.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {
            }
        }.start()
    }
}
