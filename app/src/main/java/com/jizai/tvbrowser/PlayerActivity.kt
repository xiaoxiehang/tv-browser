package com.jizai.tvbrowser

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * 直播播放器：ExoPlayer 全屏播放。
 * - D-pad 上/下：上一个/下一个频道（控制条隐藏时）
 * - D-pad 中键：调出播放控制条（暂停/进度等）
 * - 返回键：先收控制条，再按退出
 * - 支持 HLS(.m3u8) 与 MP4 等直链
 * - 播放失败只 Toast 提示，不自动切源
 */
class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var titles: List<String> = emptyList()
    private var urls: List<String> = emptyList()
    private var index = 0

    override fun onCreate(st: Bundle?) {
        super.onCreate(st)
        titles = intent.getStringArrayListExtra("titles") ?: emptyList()
        urls = intent.getStringArrayListExtra("urls") ?: emptyList()
        index = intent.getIntExtra("index", 0)
        if (urls.isEmpty()) {
            Toast.makeText(this, "没有可播放的地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        index = index.coerceIn(urls.indices)

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // 显示控制条：暂停/进度等走标准交互
            useController = true
            // 遥控器可操作控制条
            isFocusable = true
        }
        setContentView(playerView)

        val p = ExoPlayer.Builder(this).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "播放失败：${titles.getOrNull(index) ?: ""}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        player = p
        playerView.player = p
        playerView.requestFocus()
        playAt(index)
    }

    /** 切换频道（循环） */
    private fun playAt(i: Int) {
        if (urls.isEmpty()) return
        index = ((i % urls.size) + urls.size) % urls.size
        val url = urls[index]
        val lower = url.lowercase().substringBefore("?").substringBefore("#")
        val mime = if (lower.endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8 else null
        val item = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .apply { if (mime != null) setMimeType(mime) }
            .build()
        player?.setMediaItem(item)
        player?.prepare()
        player?.play()
        val name = titles.getOrNull(index).orEmpty()
        if (name.isNotBlank()) Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 控制条显示时，方向键先给控制条用；隐藏时上下键换台
        if (playerView.isControllerFullyVisible) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { playAt(index - 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { playAt(index + 1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        if (::playerView.isInitialized) playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
