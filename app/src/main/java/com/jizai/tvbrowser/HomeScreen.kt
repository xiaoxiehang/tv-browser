package com.jizai.tvbrowser

import android.app.AlertDialog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 首页：品牌栏 + 搜索 Hero + 快捷操作 + 书签横排 + 历史横排 */
class HomeScreen(private val act: MainActivity, parent: ViewGroup) {

    private val store = act.store
    private val d = act.resources.displayMetrics.density
    val root: View = LayoutInflater.from(act).inflate(R.layout.view_home, parent, false)
    private val urlInput: EditText = root.findViewById(R.id.urlInput)
    private val searchBox: View = root.findViewById(R.id.searchBox)
    private val bookmarkRow: LinearLayout = root.findViewById(R.id.bookmarkRow)
    private val historyRow: LinearLayout = root.findViewById(R.id.historyRow)
    private val emptyBookmark: View = root.findViewById(R.id.emptyBookmark)
    private val emptyHistory: View = root.findViewById(R.id.emptyHistory)
    private val bookmarkCount: TextView = root.findViewById(R.id.bookmarkCount)
    private val historyCount: TextView = root.findViewById(R.id.historyCount)

    private fun dp(n: Int) = (n * d).toInt()

    init {
        parent.addView(root)

        root.findViewById<TextView>(R.id.verLabel).text =
            "${act.getString(R.string.app_tagline)} · v${versionName()}"

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { go(); true } else false
        }
        urlInput.setOnFocusChangeListener { _, hasFocus ->
            searchBox.setBackgroundResource(
                if (hasFocus) R.drawable.bg_search_focus else R.drawable.bg_search_idle
            )
        }
        root.findViewById<View>(R.id.goBtn).setOnClickListener { go() }
        val voiceBtn = root.findViewById<View>(R.id.voiceBtn)
        FocusKit.lift(voiceBtn, 1.08f)
        voiceBtn.setOnClickListener { startVoice() }

        val companionCard = root.findViewById<View>(R.id.companionCard)
        val addCard = root.findViewById<View>(R.id.addCard)
        val clearCard = root.findViewById<View>(R.id.clearCard)
        val videoCard = root.findViewById<View>(R.id.videoCard)
        FocusKit.lift(companionCard)
        FocusKit.lift(addCard)
        FocusKit.lift(clearCard)
        FocusKit.lift(videoCard)
        companionCard.setOnClickListener { QrDialog.show(act) }
        addCard.setOnClickListener { showAddDialog() }
        videoCard.setOnClickListener { act.showVideoSources() }
        clearCard.setOnClickListener {
            AlertDialog.Builder(act)
                .setTitle("清空历史记录？")
                .setMessage("将删除全部浏览记录，书签不受影响。")
                .setPositiveButton("清空") { _, _ -> store.clearHistory(); refresh() }
                .setNegativeButton("取消", null)
                .show()
        }

        refresh()
    }

    private fun versionName(): String = try {
        act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    private fun go() {
        val t = urlInput.text.toString().trim()
        if (t.isNotEmpty()) act.openUrl(t)
    }

    /** 语音搜索：识别结果填入搜索框，不自动提交 */
    private fun startVoice() {
        act.requestVoiceInput { text ->
            urlInput.setText(text)
            urlInput.setSelection(text.length)
        }
    }

    fun refresh() {
        val bms = store.list()
        bookmarkRow.removeAllViews()
        bms.forEach { b ->
            val card = makeBookmarkCard(b)
            FocusKit.lift(card, 1.06f)
            bookmarkRow.addView(card)
        }
        emptyBookmark.visibility = if (bms.isEmpty()) View.VISIBLE else View.GONE
        bookmarkCount.text = if (bms.isEmpty()) "" else "${bms.size} 个"

        val his = store.history()
        historyRow.removeAllViews()
        his.forEach { h ->
            val card = makeHistoryCard(h)
            FocusKit.lift(card, 1.05f)
            historyRow.addView(card)
        }
        emptyHistory.visibility = if (his.isEmpty()) View.VISIBLE else View.GONE
        historyCount.text = if (his.isEmpty()) "" else "${his.size} 条"
    }

    /** 书签卡片：标题 + 域名，纯白极简 */
    private fun makeBookmarkCard(b: Bookmark): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            isFocusable = true
            isClickable = true
            elevation = dp(2).toFloat()
            setPadding(dp(26), dp(24), dp(26), dp(24))
            gravity = Gravity.CENTER_VERTICAL
        }
        card.layoutParams = LinearLayout.LayoutParams(dp(280), dp(148)).apply {
            setMargins(dp(8), dp(8), dp(8), dp(8))
        }

        val title = b.title.ifBlank { b.url }
        card.addView(TextView(act).apply {
            text = title
            textSize = 21f
            setTextColor(0xFF1D1D1F.toInt())
            maxLines = 1
        })
        card.addView(TextView(act).apply {
            text = domainOf(b.url)
            textSize = 14f
            setTextColor(0xFFA1A1A6.toInt())
            maxLines = 1
            setPadding(0, dp(8), 0, 0)
        })

        card.setOnClickListener { act.openUrl(b.url) }
        card.setOnLongClickListener {
            AlertDialog.Builder(act)
                .setTitle("删除书签？")
                .setMessage("「${title}」\n${b.url}")
                .setPositiveButton("删除") { _, _ -> store.remove(b.url); refresh() }
                .setNegativeButton("取消", null)
                .show()
            true
        }
        return card
    }

    /** 历史卡片：标题 + 网址，更扁平 */
    private fun makeHistoryCard(h: Bookmark): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            isFocusable = true
            isClickable = true
            elevation = dp(2).toFloat()
            setPadding(dp(22), dp(18), dp(22), dp(18))
            gravity = Gravity.CENTER_VERTICAL
        }
        card.layoutParams = LinearLayout.LayoutParams(dp(330), dp(132)).apply {
            setMargins(dp(8), dp(8), dp(8), dp(8))
        }
        val title = h.title.ifBlank { h.url }
        card.addView(TextView(act).apply {
            text = title
            textSize = 19f
            setTextColor(0xFF1D1D1F.toInt())
            maxLines = 1
        })
        card.addView(TextView(act).apply {
            text = domainOf(h.url)
            textSize = 14f
            setTextColor(0xFFA1A1A6.toInt())
            maxLines = 1
            setPadding(0, dp(6), 0, 0)
        })
        card.setOnClickListener { act.openUrl(h.url) }
        return card
    }

    private fun domainOf(url: String): String = try {
        var host = java.net.URI(url).host ?: url
        if (host.startsWith("www.")) host = host.substring(4)
        host
    } catch (_: Exception) { url }

    private fun styledInput(hint: String): EditText {
        return EditText(act).apply {
            this.hint = hint
            textSize = 20f
            setTextColor(0xFF1D1D1F.toInt())
            setHintTextColor(0xFFA1A1A6.toInt())
            setBackgroundResource(R.drawable.bg_search_idle)
            setPadding(dp(24), dp(28), dp(24), dp(28))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(10), 0, dp(10))
            layoutParams = lp
        }
    }

    private fun showAddDialog() {
        val layout = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(8))
        }
        val titleEt = styledInput("名称（比如：B站）")
        val urlEt = styledInput("网址（比如：bilibili.com）")
        layout.addView(titleEt)
        layout.addView(urlEt)
        AlertDialog.Builder(act)
            .setTitle("添加书签")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val url = urlEt.text.toString().trim()
                if (url.isNotEmpty()) {
                    store.add(titleEt.text.toString().trim(), url)
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
