package com.jizai.tvbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.webkit.WebView

/**
 * 后台音频：前台 Service 保活，通知栏提供 暂停/继续、关闭。
 * target 指向当前标签页的 WebView（同一进程直接引用）；暂停/继续用 JS 控制页面内媒体。
 */
class AudioService : Service() {

    companion object {
        const val ACTION_PAUSE = "com.jizai.tvbrowser.audio.PAUSE"
        const val ACTION_CLOSE = "com.jizai.tvbrowser.audio.CLOSE"
        private const val CH_ID = "audio_playback"
        private const val NOTIF_ID = 1001

        /** 当前在播的 WebView，由浏览器页在启动服务时赋值 */
        var target: WebView? = null
        var playing = true

        fun mediaJs(pause: Boolean): String =
            "(function(){var els=document.querySelectorAll('video,audio');" +
                    "els.forEach(function(e){try{" +
                    (if (pause) "e.pause();" else "e.play();") +
                    "}catch(_){}});return els.length})()"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH_ID, "后台音频", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                playing = !playing
                safeJs(mediaJs(!playing))
                startForegroundCompat(buildNotif())
            }
            ACTION_CLOSE -> {
                safeJs(mediaJs(true))
                target = null
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
                else @Suppress("DEPRECATION") stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                playing = true
                startForegroundCompat(buildNotif())
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notif: Notification) {
        try {
            startForeground(NOTIF_ID, notif)
        } catch (_: Exception) {
            // 极少数设备上前台启动失败也不崩溃
        }
    }

    private fun safeJs(js: String) {
        try {
            target?.evaluateJavascript(js, null)
        } catch (_: Exception) {
        }
    }

    private fun pendingService(action: String, req: Int): PendingIntent {
        val it = Intent(this, AudioService::class.java).setAction(action)
        val f = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, req, it, f)
    }

    private fun buildNotif(): Notification {
        val title = try {
            target?.title?.ifBlank { "鸡仔浏览器" } ?: "鸡仔浏览器"
        } catch (_: Exception) {
            "鸡仔浏览器"
        }
        val openApp = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CH_ID)
                .setContentTitle(if (playing) "正在后台播放" else "已暂停")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openApp)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    if (playing) "暂停" else "继续",
                    pendingService(ACTION_PAUSE, 1)
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "关闭",
                    pendingService(ACTION_CLOSE, 2)
                )
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(if (playing) "正在后台播放" else "已暂停")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openApp)
                .addAction(
                    android.R.drawable.ic_media_pause,
                    if (playing) "暂停" else "继续",
                    pendingService(ACTION_PAUSE, 1)
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "关闭",
                    pendingService(ACTION_CLOSE, 2)
                )
                .setOngoing(true)
                .build()
        }
    }
}
