package com.jizai.tvbrowser

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.jizai.tvbrowser.companion.QrUtil

/** 手机遥控配对弹窗（首页 / 浏览器页共用） */
object QrDialog {

    fun show(act: MainActivity) {
        val d = act.resources.displayMetrics.density
        val layout = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((48 * d).toInt(), (36 * d).toInt(), (48 * d).toInt(), (28 * d).toInt())
        }

        layout.addView(ImageView(act).apply {
            val px = (300 * d).toInt()
            layoutParams = LinearLayout.LayoutParams(px, px)
            setImageBitmap(QrUtil.make(act.companion.pairUrl(), 900))
            setBackgroundColor(Color.WHITE)
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        })

        layout.addView(TextView(act).apply {
            text = "配对码"
            textSize = 16f
            setTextColor(0xFF6E6E73.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (28 * d).toInt(), 0, 0)
        })

        layout.addView(TextView(act).apply {
            text = act.companion.code.chunked(3).joinToString(" ")
            textSize = 46f
            setTextColor(0xFFFF385C.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (4 * d).toInt(), 0, 0)
            isAllCaps = false
            letterSpacing = 0.12f
        })

        layout.addView(TextView(act).apply {
            text = "手机和电视连上同一个 Wi-Fi\n用手机浏览器扫码，输入配对码即可连接"
            textSize = 16f
            setTextColor(0xFF6E6E73.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (20 * d).toInt(), 0, 0)
            setLineSpacing(6 * d, 1f)
        })

        AlertDialog.Builder(act)
            .setTitle("手机遥控")
            .setView(layout)
            .setPositiveButton("完成", null)
            .show()
    }
}
