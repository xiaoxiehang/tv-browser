package com.jizai.tvbrowser

import android.app.AlertDialog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 视频源页：源管理 + 内容宫格 + 详情弹窗。
 * - 源配置 JSON 存在本地；只支持合法视频源。
 * - tmdb 类型走 TMDB 官方 API（Key 用户自己填）；json 类型按 { list:[{title,cover,url,desc}] } 约定解析。
 * - 点击内容用现有 WebView 打开播放页。
 */
class VideoSourceScreen(private val act: MainActivity, parent: ViewGroup) {

    private val store = VideoSourceStore(act)
    private val d = act.resources.displayMetrics.density
    val root: View = LayoutInflater.from(act).inflate(R.layout.view_videosource, parent, false)

    private val sourceRow: LinearLayout = root.findViewById(R.id.sourceRow)
    private val contentTitle: TextView = root.findViewById(R.id.contentTitle)
    private val contentGrid: GridLayout = root.findViewById(R.id.contentGrid)
    private val emptyView: TextView = root.findViewById(R.id.emptyView)

    private var currentIndex = -1
    private var loading = false

    private data class VideoItem(
        val title: String,
        val cover: String,
        val url: String,
        val desc: String
    )

    private fun dp(n: Int) = (n * d).toInt()

    init {
        parent.addView(root)
        refreshSources()
    }

    // ---------- 源列表 ----------
    private fun refreshSources() {
        sourceRow.removeAllViews()
        val sources = store.list()
        sources.forEachIndexed { i, s ->
            sourceRow.addView(makeSourceChip(s, i))
        }
        // ＋ 添加源
        val add = TextView(act).apply {
            text = "＋ 添加源"
            textSize = 20f
            setTextColor(0xFFFF385C.toInt())
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_card)
            isFocusable = true
            isClickable = true
            elevation = dp(2).toFloat()
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        add.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }
        FocusKit.lift(add, 1.05f)
        add.setOnClickListener { showEditDialog(-1) }
        sourceRow.addView(add)

        if (currentIndex !in sources.indices) {
            currentIndex = sources.indexOfFirst { it.enabled }.takeIf { it >= 0 } ?: -1
        }
        if (currentIndex in sources.indices) loadSource(currentIndex)
        else showEmpty("还没有可用的视频源\n点「＋ 添加源」添加合法视频源")
    }

    private fun makeSourceChip(s: VideoSource, i: Int): View {
        val chip = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            isFocusable = true
            isClickable = true
            elevation = dp(2).toFloat()
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }
        chip.addView(TextView(act).apply {
            text = (if (s.enabled) "" else "（停用）") + s.name
            textSize = 20f
            setTextColor(if (i == currentIndex) 0xFFFF385C.toInt() else 0xFF1D1D1F.toInt())
            maxLines = 1
        })
        FocusKit.lift(chip, 1.05f)
        chip.setOnClickListener {
            if (!s.enabled) {
                Toast.makeText(act, "该源已停用，长按可重新启用", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentIndex = i
            refreshSources()
        }
        chip.setOnLongClickListener { showManageMenu(i); true }
        return chip
    }

    private fun showManageMenu(i: Int) {
        val s = store.list().getOrNull(i) ?: return
        val items = arrayOf("编辑", if (s.enabled) "停用" else "启用", "删除")
        AlertDialog.Builder(act)
            .setTitle(s.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showEditDialog(i)
                    1 -> {
                        store.setEnabled(i, !s.enabled)
                        if (!s.enabled) currentIndex = i
                        refreshSources()
                    }
                    2 -> AlertDialog.Builder(act)
                        .setTitle("删除视频源？")
                        .setMessage("「${s.name}」")
                        .setPositiveButton("删除") { _, _ ->
                            store.remove(i)
                            if (currentIndex == i) currentIndex = -1
                            refreshSources()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .show()
    }

    // ---------- 添加 / 编辑源 ----------
    private fun showEditDialog(index: Int) {
        val existing = store.list().getOrNull(index)
        var type = existing?.type?.takeIf { it == "json" } ?: "tmdb"
        val layout = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(8))
        }
        val nameEt = styledInput("源名称（比如：TMDB 热门电影）", existing?.name ?: "")
        val typeBtn = TextView(act).apply {
            textSize = 20f
            setTextColor(0xFFFF385C.toInt())
            setPadding(0, dp(16), 0, dp(8))
        }
        val urlEt = styledInput(
            "接口地址", existing?.apiUrl ?: "https://api.themoviedb.org/3"
        )
        val keyEt = styledInput("API Key（TMDB 在 themoviedb.org 申请）", existing?.apiKey ?: "")
        fun refreshType() {
            typeBtn.text = "类型：${if (type == "tmdb") "TMDB" else "通用 JSON"}（点击切换）"
            urlEt.hint = if (type == "tmdb") "接口地址" else "JSON 地址（返回 {\"list\":[…]}）"
        }
        refreshType()
        typeBtn.setOnClickListener {
            type = if (type == "tmdb") "json" else "tmdb"
            refreshType()
        }
        layout.addView(nameEt)
        layout.addView(typeBtn)
        layout.addView(urlEt)
        layout.addView(keyEt)
        layout.addView(TextView(act).apply {
            text = "请只添加合法视频源"
            textSize = 15f
            setTextColor(0xFFA1A1A6.toInt())
            setPadding(0, dp(16), 0, 0)
        })
        AlertDialog.Builder(act)
            .setTitle(if (index < 0) "添加视频源" else "编辑视频源")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = nameEt.text.toString().trim()
                val apiUrl = urlEt.text.toString().trim()
                if (name.isEmpty() || apiUrl.isEmpty()) {
                    Toast.makeText(act, "名称和接口地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val s = VideoSource(name, type, apiUrl, keyEt.text.toString().trim(), true)
                if (index < 0) {
                    store.add(s)
                    currentIndex = store.list().lastIndex
                } else store.update(index, s)
                refreshSources()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun styledInput(hint: String, value: String = ""): EditText {
        return EditText(act).apply {
            this.hint = hint
            setText(value)
            textSize = 20f
            setTextColor(0xFF1D1D1F.toInt())
            setHintTextColor(0xFFA1A1A6.toInt())
            setBackgroundResource(R.drawable.bg_search_idle)
            setPadding(dp(24), dp(26), dp(24), dp(26))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(10), 0, dp(10))
            layoutParams = lp
        }
    }

    // ---------- 内容加载 ----------
    private fun loadSource(i: Int) {
        val s = store.list().getOrNull(i) ?: return
        if (s.type == "tmdb" && s.apiKey.isBlank()) {
            promptTmdbKey(i, s)
            return
        }
        contentTitle.text = s.name
        showEmpty("加载中…")
        loading = true
        val fetcher = if (s.type == "tmdb") ::fetchTmdb else ::fetchJson
        fetcher(s) { items ->
            loading = false
            if (items.isEmpty()) showEmpty("没有内容\n检查接口地址和 Key 是否正确")
            else renderGrid(items)
        }
    }

    private fun promptTmdbKey(i: Int, s: VideoSource) {
        contentTitle.text = s.name
        showEmpty("该源需要 TMDB API Key")
        val et = styledInput("粘贴你的 TMDB API Key")
        AlertDialog.Builder(act)
            .setTitle("填写 API Key")
            .setMessage("在 themoviedb.org 注册后免费获取")
            .setView(LinearLayout(act).apply {
                setPadding(dp(48), dp(16), dp(48), dp(8))
                addView(et)
            })
            .setPositiveButton("保存") { _, _ ->
                val key = et.text.toString().trim()
                if (key.isNotEmpty()) {
                    store.update(i, s.copy(apiKey = key))
                    refreshSources()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEmpty(msg: String) {
        contentGrid.removeAllViews()
        emptyView.visibility = View.VISIBLE
        emptyView.text = msg
    }

    private fun renderGrid(items: List<VideoItem>) {
        emptyView.visibility = View.GONE
        contentGrid.removeAllViews()
        val cols = 4
        val gap = dp(24)
        val cardW = (act.resources.displayMetrics.widthPixels - dp(128) - gap * (cols - 1)) / cols
        val imgH = cardW * 3 / 2
        items.forEach { item ->
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                isFocusable = true
                isClickable = true
                elevation = dp(2).toFloat()
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            val lp = GridLayout.LayoutParams().apply {
                width = cardW
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(0, 0, gap, dp(24))
            }
            card.layoutParams = lp
            val iv = ImageView(act).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, imgH
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFF1F1F3.toInt())
            }
            card.addView(iv)
            VideoImageLoader.load(item.cover, iv)
            card.addView(TextView(act).apply {
                text = item.title
                textSize = 19f
                setTextColor(0xFF1D1D1F.toInt())
                maxLines = 2
                setPadding(0, dp(10), 0, dp(4))
            })
            FocusKit.lift(card, 1.04f)
            card.setOnClickListener { showDetail(item) }
            contentGrid.addView(card)
        }
    }

    // ---------- 详情弹窗 ----------
    private fun showDetail(item: VideoItem) {
        val layout = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(8))
        }
        layout.addView(TextView(act).apply {
            text = item.desc
            textSize = 19f
            setTextColor(0xFF6E6E73.toInt())
            setLineSpacingExtra(dp(6).toFloat())
        })
        AlertDialog.Builder(act)
            .setTitle(item.title)
            .setView(layout)
            .setPositiveButton("在浏览器中打开") { _, _ ->
                if (item.url.isNotBlank()) act.openUrl(item.url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 网络 ----------
    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Accept", "application/json")
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun fetchTmdb(s: VideoSource, cb: (List<VideoItem>) -> Unit) {
        Thread {
            try {
                val url = "${s.apiUrl.trimEnd('/')}/movie/popular" +
                        "?api_key=${s.apiKey}&language=zh-CN&page=1"
                val arr = JSONObject(httpGet(url)).optJSONArray("results") ?: JSONArray()
                val items = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val poster = o.optString("poster_path")
                    items += VideoItem(
                        title = o.optString("title"),
                        cover = if (poster.isNotBlank() && poster != "null")
                            "https://image.tmdb.org/t/p/w342$poster" else "",
                        url = "https://www.themoviedb.org/movie/${o.optInt("id")}",
                        desc = "评分 ${o.optDouble("vote_average")} · ${o.optString("overview")}"
                    )
                }
                act.runOnUiThread { cb(items) }
            } catch (_: Exception) {
                act.runOnUiThread { cb(emptyList()) }
            }
        }.start()
    }

    private fun fetchJson(s: VideoSource, cb: (List<VideoItem>) -> Unit) {
        Thread {
            try {
                var url = s.apiUrl
                if (s.apiKey.isNotBlank()) {
                    url += (if (url.contains("?")) "&" else "?") + "api_key=${s.apiKey}"
                }
                val arr = JSONObject(httpGet(url)).optJSONArray("list") ?: JSONArray()
                val items = mutableListOf<VideoItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items += VideoItem(
                        title = o.optString("title"),
                        cover = o.optString("cover"),
                        url = o.optString("url"),
                        desc = o.optString("desc")
                    )
                }
                act.runOnUiThread { cb(items) }
            } catch (_: Exception) {
                act.runOnUiThread { cb(emptyList()) }
            }
        }.start()
    }
}
