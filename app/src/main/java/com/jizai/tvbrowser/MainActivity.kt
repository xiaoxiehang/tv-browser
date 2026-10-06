package com.jizai.tvbrowser

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import com.jizai.tvbrowser.companion.CompanionListener
import com.jizai.tvbrowser.companion.CompanionServer

/**
 * 单 Activity 架构（零 AndroidX 依赖）：
 * 首页 / 浏览器页都是普通 View，手动切换；返回键手动处理。
 */
class MainActivity : Activity(), CompanionListener {

    lateinit var companion: CompanionServer
        private set
    lateinit var store: BookmarkStore
        private set

    private lateinit var container: FrameLayout
    private var browser: BrowserScreen? = null

    override fun onCreate(st: Bundle?) {
        super.onCreate(st)
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.container)
        store = BookmarkStore(this)

        val html = resources.openRawResource(R.raw.remote).bufferedReader().readText()
        companion = CompanionServer(html, this)
        companion.start()

        showHome()
    }

    override fun onDestroy() {
        browser?.destroy()
        companion.stop()
        super.onDestroy()
    }

    fun showHome() {
        browser?.destroy()
        browser = null
        container.removeAllViews()
        HomeScreen(this, container)
    }

    fun openUrl(raw: String) {
        val url = normalizeUrl(raw)
        browser?.destroy()
        container.removeAllViews()
        browser = BrowserScreen(this, container, url)
    }

    @Deprecated("framework back handling")
    override fun onBackPressed() {
        val b = browser
        if (b != null) {
            if (b.goBack()) return
            showHome()
            return
        }
        super.onBackPressed()
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return if (t.matches(Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$"))) "https://$t"
        else "https://www.bing.com/search?q=${java.net.URLEncoder.encode(t, "UTF-8")}"
    }

    // ---------- 手机伴侣指令 ----------
    private val keyMap = mapOf(
        "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP,
        "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
        "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ENTER" to KeyEvent.KEYCODE_DPAD_CENTER,
        "BACK" to KeyEvent.KEYCODE_BACK,
    )

    override fun onRemoteKey(key: String) {
        if (key == "HOME") { showHome(); return }
        val code = keyMap[key] ?: return
        val now = android.os.SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    override fun onRemoteText(text: String) {
        browser?.onRemoteText(text) ?: run {
            // 在首页：把文本填进地址栏
            (container.getChildAt(0)?.findViewById<android.widget.EditText>(R.id.urlInput))?.let {
                it.setText(text)
                it.setSelection(text.length)
            }
        }
    }

    override fun onRemoteOpen(url: String) {
        openUrl(url)
    }
}
