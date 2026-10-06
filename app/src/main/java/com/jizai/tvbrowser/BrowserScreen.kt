package com.jizai.tvbrowser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * 浏览器页（纯 View，无 Fragment）：
 * - 虚拟光标：方向键移动光标，OK 在光标处点击
 * - 视频全屏：WebView customView 机制
 * - 悬浮胶囊工具栏：无操作 4 秒自动隐藏，任意按键唤回
 * - 菜单：自定义大按钮列表对话框
 */
class BrowserScreen(private val act: MainActivity, parent: ViewGroup, startUrl: String) {

    companion object {
        private const val STEP = 64f
        private const val HIDE_DELAY = 4000L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val FOCUS_CSS =
            "(function(){var s=document.getElementById('jizai-focus');" +
                    "if(!s){s=document.createElement('style');s.id='jizai-focus';" +
                    "s.textContent='*:focus{outline:3px solid #FF385C!important;outline-offset:2px!important}';" +
                    "document.head.appendChild(s);}})()"
    }

    private val root: View = LayoutInflater.from(act).inflate(R.layout.view_browser, parent, false)
    private val webView: WebView = root.findViewById(R.id.webView)
    private val cursor: View = root.findViewById(R.id.cursor)
    private val urlBar: TextView = root.findViewById(R.id.urlBar)
    private val progress: ProgressBar = root.findViewById(R.id.progress)
    private val topBar: View = root.findViewById(R.id.topBar)
    private val d = act.resources.displayMetrics.density
    private var mobileUa: String = ""
    private var desktopMode = true
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorShown = false
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var destroyed = false

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideTopBar() }

    private fun dp(n: Int) = (n * d).toInt()

    init {
        parent.addView(root)
        initWebView()

        val homeBtn = root.findViewById<View>(R.id.homeBtn)
        val menuBtn = root.findViewById<View>(R.id.menuBtn)
        FocusKit.lift(homeBtn, 1.08f)
        FocusKit.lift(urlBar, 1.02f)
        FocusKit.lift(menuBtn, 1.08f)
        homeBtn.setOnClickListener { pokeTopBar(); act.showHome() }
        urlBar.setOnClickListener { pokeTopBar(); showUrlDialog() }
        menuBtn.setOnClickListener { showMenu() }

        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                pokeTopBar()
                handleKey(keyCode)
            } else false
        }
        webView.loadUrl(startUrl)
        root.post { showCursor(); scheduleHide() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView.settings.let { ws ->
            ws.javaScriptEnabled = true
            ws.domStorageEnabled = true
            ws.mediaPlaybackRequiresUserGesture = false
            ws.setSupportZoom(true)
            ws.builtInZoomControls = true
            ws.displayZoomControls = false
            ws.loadWithOverviewMode = true
            ws.useWideViewPort = true
            mobileUa = ws.userAgentString
            ws.userAgentString = DESKTOP_UA
        }
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urlBar.text = prettyUrl(url)
                progress.visibility = View.VISIBLE
                pokeTopBar()
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                view.evaluateJavascript(FOCUS_CSS, null)
                val t = view.title
                val u = view.url
                if (!t.isNullOrBlank() && !u.isNullOrBlank()) act.store.addHistory(t, u)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progress.progress = p
                if (p >= 100) progress.visibility = View.GONE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customCallback = callback
                (act.window.decorView as ViewGroup).addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                topBar.visibility = View.GONE
                hideCursor()
            }

            override fun onHideCustomView() {
                customView?.let { (act.window.decorView as ViewGroup).removeView(it) }
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                if (!destroyed) {
                    showTopBar()
                    showCursor()
                }
            }
        }
    }

    private fun prettyUrl(url: String): String = url

    fun destroy() {
        if (destroyed) return
        destroyed = true
        hideHandler.removeCallbacks(hideRunnable)
        customView?.let { (act.window.decorView as ViewGroup).removeView(it) }
        customView = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    /** 返回键：视频全屏先退全屏；网页能后退就后退；否则返回 false 让 Activity 回首页 */
    fun goBack(): Boolean {
        if (customView != null) {
            (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return true
        }
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    // ---------- 悬浮工具栏自动隐藏 ----------
    private fun pokeTopBar() {
        if (destroyed || customView != null) return
        showTopBar()
        scheduleHide()
    }

    private fun showTopBar() {
        if (topBar.visibility != View.VISIBLE) {
            topBar.visibility = View.VISIBLE
            topBar.alpha = 0f
            topBar.translationY = -24f
            topBar.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
    }

    private fun hideTopBar() {
        if (destroyed || customView != null || topBar.visibility != View.VISIBLE) return
        topBar.animate().alpha(0f).translationY(-24f).setDuration(250)
            .withEndAction { topBar.visibility = View.GONE }.start()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    // ---------- 虚拟光标 ----------
    private fun showCursor() {
        if (destroyed) return
        cursorShown = true
        cursor.visibility = View.VISIBLE
        if (cursorX == 0f && cursorY == 0f) {
            cursorX = webView.width / 2f
            cursorY = webView.height / 2f
        }
        placeCursor()
    }

    private fun hideCursor() {
        cursorShown = false
        cursor.visibility = View.GONE
    }

    private fun placeCursor() {
        val half = 28 * d
        cursor.x = cursorX - half
        cursor.y = cursorY - half
    }

    private fun moveCursor(dx: Float, dy: Float) {
        if (!cursorShown) { showCursor(); return }
        var nx = cursorX + dx
        var ny = cursorY + dy
        val w = webView.width.toFloat()
        val h = webView.height.toFloat()
        var sx = 0
        var sy = 0
        if (nx < 0) { sx = nx.toInt(); nx = 0f }
        if (nx > w) { sx = (nx - w).toInt(); nx = w }
        if (ny < 0) { sy = ny.toInt(); ny = 0f }
        if (ny > h) { sy = (ny - h).toInt(); ny = h }
        if (sx != 0 || sy != 0) webView.scrollBy(sx, sy)
        cursorX = nx
        cursorY = ny
        placeCursor()
    }

    private fun tapCursor() {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
        val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun handleKey(keyCode: Int): Boolean {
        if (customView != null) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveCursor(0f, -STEP)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursor(0f, STEP)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCursor(-STEP, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursor(STEP, 0f)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> tapCursor()
            KeyEvent.KEYCODE_MENU -> showMenu()
            else -> return false
        }
        return true
    }

    // ---------- 对话框 ----------
    private fun showUrlDialog() {
        val et = EditText(act).apply {
            setText(webView.url ?: "")
            textSize = 20f
            setTextColor(0xFF1D1D1F.toInt())
            setHintTextColor(0xFFA1A1A6.toInt())
            setBackgroundResource(R.drawable.bg_search_idle)
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelection(text.length)
        }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                act.openUrl(et.text.toString())
                true
            } else false
        }
        AlertDialog.Builder(act)
            .setTitle("打开网址")
            .setView(et)
            .setPositiveButton("前往") { _, _ -> act.openUrl(et.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 自定义菜单：大按钮列表 */
    private fun showMenu() {
        pokeTopBar()
        val uaItem = if (desktopMode) "切换 UA：手机版" else "切换 UA：桌面版"
        val defs = listOf(
            "刷新" to { webView.reload() },
            "前进" to { if (webView.canGoForward()) webView.goForward() },
            uaItem to {
                desktopMode = !desktopMode
                webView.settings.userAgentString = if (desktopMode) DESKTOP_UA else mobileUa
                webView.reload()
            },
            "放大" to { webView.zoomIn() },
            "缩小" to { webView.zoomOut() },
            "加入书签" to {
                val u = webView.url
                if (!u.isNullOrBlank()) {
                    act.store.add(webView.title ?: u, u)
                    Toast.makeText(act, "已加入书签", Toast.LENGTH_SHORT).show()
                }
            },
            "手机遥控" to { QrDialog.show(act) },
            "返回主页" to { act.showHome() },
        )

        val list = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle("菜单")
            .setView(list)
            .create()

        defs.forEach { (label, action) ->
            val row = TextView(act).apply {
                text = label
                textSize = 22f
                setTextColor(0xFF1D1D1F.toInt())
                setBackgroundResource(R.drawable.bg_card)
                isFocusable = true
                isClickable = true
                setPadding(dp(24), dp(26), dp(24), dp(26))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(6), 0, dp(6)) }
            }
            FocusKit.lift(row, 1.03f)
            row.setOnClickListener { dlg.dismiss(); action() }
            list.addView(row)
        }
        dlg.show()
    }

    /** 手机伴侣发来的文本：注入到网页输入框 */
    fun onRemoteText(text: String) {
        val q = JSONObject.quote(text)
        webView.evaluateJavascript(
            "(function(){var T=$q;var el=document.activeElement;" +
                    "if(el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA')){" +
                    "el.focus();var s=el.selectionStart||el.value.length,e=el.selectionEnd||s;" +
                    "el.value=el.value.slice(0,s)+T+el.value.slice(e);" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));}" +
                    "else if(el&&el.isContentEditable){el.focus();document.execCommand('insertText',false,T);}" +
                    "})()", null
        )
    }
}
