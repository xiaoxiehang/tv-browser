package com.jizai.tvbrowser

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jizai.tvbrowser.companion.CompanionListener
import com.jizai.tvbrowser.companion.CompanionServer

/**
 * 单 Activity 架构（零 AndroidX 依赖）：
 * 首页 / 浏览器页都是普通 View，手动切换；返回键手动处理。
 * 浏览器支持多标签页：tabs 列表 + 顶部标签栏，遥控器方向键可操作。
 */
class MainActivity : Activity(), CompanionListener {

    companion object {
        private const val REQ_AUDIO = 101
        private const val REQ_NOTIFY = 102
        private const val REQ_STORAGE = 103
    }

    lateinit var companion: CompanionServer
        private set
    lateinit var store: BookmarkStore
        private set

    private lateinit var container: FrameLayout
    private lateinit var tabBarScroll: HorizontalScrollView
    private lateinit var tabBar: LinearLayout
    private var homeView: android.view.View? = null
    private var videoView: android.view.View? = null

    private val tabs = mutableListOf<BrowserScreen>()
    private var currentTab = -1
    private var lastTabTitles: List<String> = emptyList()

    private data class PendingDownload(
        val url: String, val userAgent: String, val contentDisposition: String, val mimeType: String
    )
    private var pendingDownload: PendingDownload? = null
    private var voiceCallback: ((String) -> Unit)? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(st: Bundle?) {
        super.onCreate(st)
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.container)
        tabBarScroll = findViewById(R.id.tabBarScroll)
        tabBar = findViewById(R.id.tabBar)
        store = BookmarkStore(this)
        AdBlocker.init(this)

        val html = resources.openRawResource(R.raw.remote).bufferedReader().readText()
        companion = CompanionServer(html, this)
        companion.start()

        showHome()
    }

    override fun onDestroy() {
        tabs.forEach { it.destroy() }
        tabs.clear()
        try {
            AudioService.target = null
            stopService(android.content.Intent(this, AudioService::class.java))
        } catch (_: Exception) {
        }
        companion.stop()
        super.onDestroy()
    }

    // ---------- 首页 / 标签页 / 视频源 ----------
    fun showHome() {
        if (homeView == null) {
            HomeScreen(this, container)
            homeView = container.getChildAt(container.childCount - 1)
        }
        currentTab = -1
        showOnly(homeView!!)
    }

    fun showVideoSources() {
        if (videoView == null) {
            VideoSourceScreen(this, container)
            videoView = container.getChildAt(container.childCount - 1)
        }
        currentTab = -1
        showOnly(videoView!!)
    }

    fun openUrl(raw: String) {
        val url = normalizeUrl(raw)
        if (currentTab in tabs.indices) {
            tabs[currentTab].load(url)
            showOnly(tabs[currentTab].root)
        } else {
            newTab(url)
            return
        }
        refreshTabBar()
    }

    fun newTab(raw: String) {
        val url = if (raw.trim() == "about:blank") "about:blank" else normalizeUrl(raw)
        val tab = BrowserScreen(this, container, url)
        tab.onTabUpdate = { refreshTabBar() }
        tabs.add(tab)
        currentTab = tabs.lastIndex
        showOnly(tab.root)
        refreshTabBar()
    }

    fun closeCurrentTab() = closeTab(currentTab)

    fun closeTab(i: Int) {
        if (i !in tabs.indices) return
        tabs.removeAt(i).destroy()
        if (tabs.isEmpty()) {
            currentTab = -1
            showHome()
        } else {
            currentTab = i.coerceAtMost(tabs.lastIndex)
            showOnly(tabs[currentTab].root)
        }
        refreshTabBar(force = true)
    }

    fun switchTab(i: Int) {
        if (i !in tabs.indices || i == currentTab) return
        currentTab = i
        showOnly(tabs[i].root)
        refreshTabBar()
    }

    private fun currentBrowser(): BrowserScreen? = tabs.getOrNull(currentTab)

    private fun showOnly(v: android.view.View) {
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            c.visibility = if (c === v) android.view.View.VISIBLE else android.view.View.GONE
        }
        tabBarScroll.visibility =
            if (v !== homeView && v !== videoView && tabs.isNotEmpty()) android.view.View.VISIBLE
            else android.view.View.GONE
    }

    /** 顶部标签栏：标题 chip + × 关闭 + ＋ 新建，全部可聚焦 */
    private fun refreshTabBar(force: Boolean = false) {
        val titles = tabs.map { it.pageTitle().ifBlank { "新标签页" } }
        if (!force && titles == lastTabTitles && tabBar.childCount == titles.size + 1) return
        lastTabTitles = titles
        tabBar.removeAllViews()
        titles.forEachIndexed { i, t ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                isFocusable = true
                isClickable = true
                elevation = dp(2).toFloat()
                setPadding(dp(18), dp(12), dp(10), dp(12))
            }
            chip.addView(TextView(this).apply {
                text = t.take(10)
                textSize = 17f
                setTextColor(if (i == currentTab) 0xFFFF385C.toInt() else 0xFF1D1D1F.toInt())
                maxLines = 1
            })
            val close = TextView(this).apply {
                text = " ×"
                textSize = 20f
                setTextColor(0xFFA1A1A6.toInt())
                isFocusable = true
                isClickable = true
                setPadding(dp(10), dp(6), dp(6), dp(6))
            }
            close.setOnClickListener { closeTab(i) }
            chip.addView(close)
            chip.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(12), 0) }
            FocusKit.lift(chip, 1.04f)
            chip.setOnClickListener { switchTab(i) }
            tabBar.addView(chip)
        }
        val add = TextView(this).apply {
            text = "＋"
            textSize = 22f
            setTextColor(0xFFFF385C.toInt())
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_card)
            isFocusable = true
            isClickable = true
            elevation = dp(2).toFloat()
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        FocusKit.lift(add, 1.06f)
        add.setOnClickListener { newTab("about:blank") }
        tabBar.addView(add)
    }

    @Deprecated("framework back handling")
    override fun onBackPressed() {
        val b = currentBrowser()
        if (b != null) {
            if (b.goBack()) return
            showHome()
            return
        }
        if (videoView?.visibility == android.view.View.VISIBLE) {
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

    // ---------- 下载管理 ----------
    fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
            return
        }
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }
        enqueueDownload(url, userAgent, contentDisposition, mimeType)
    }

    private fun enqueueDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        try {
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(name)
                setDescription("鸡仔浏览器下载")
                if (mimeType.isNotBlank()) setMimeType(mimeType)
                if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            dm.enqueue(req)
            Toast.makeText(this, "开始下载：$name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 语音搜索 ----------
    fun requestVoiceInput(cb: (String) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voiceCallback = cb
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        startVoiceInternal(cb)
    }

    private fun startVoiceInternal(cb: (String) -> Unit) {
        Toast.makeText(this, "请说话…", Toast.LENGTH_SHORT).show()
        VoiceInput.start(
            this,
            onResult = { runOnUiThread { cb(it) } },
            onError = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }
        )
    }

    @Deprecated("use ActivityResultLauncher")
    override fun onRequestPermissionsResult(req: Int, perms: Array<String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        val granted = res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED
        when (req) {
            REQ_AUDIO -> {
                val cb = voiceCallback
                voiceCallback = null
                if (granted && cb != null) startVoiceInternal(cb)
                else if (!granted) Toast.makeText(this, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
            }
            REQ_NOTIFY, REQ_STORAGE -> {
                val p = pendingDownload
                pendingDownload = null
                if (granted && p != null) enqueueDownload(p.url, p.userAgent, p.contentDisposition, p.mimeType)
                else if (!granted) Toast.makeText(this, "需要权限才能下载", Toast.LENGTH_SHORT).show()
            }
        }
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
        if (key == "HOME") {
            showHome()
            return
        }
        val code = keyMap[key] ?: return
        val now = android.os.SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    override fun onRemoteText(text: String) {
        currentBrowser()?.onRemoteText(text) ?: run {
            // 在首页：把文本填进地址栏
            (homeView?.findViewById<android.widget.EditText>(R.id.urlInput))?.let {
                it.setText(text)
                it.setSelection(text.length)
            }
        }
    }

    override fun onRemoteOpen(url: String) {
        openUrl(url)
    }

    /** 投屏：手机发来视频 URL，电视端打开并尝试自动全屏播放 */
    override fun onCast(url: String) {
        if (url.isBlank()) return
        openUrl(url)
        currentBrowser()?.autoPlayVideo()
        Toast.makeText(this, "正在投屏…", Toast.LENGTH_SHORT).show()
    }
}
