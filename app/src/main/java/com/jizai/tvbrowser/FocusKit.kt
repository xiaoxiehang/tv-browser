package com.jizai.tvbrowser

import android.view.View

/** TV 焦点动效：聚焦时轻微放大 + 抬起阴影，失焦复原 */
object FocusKit {
    private const val DUR = 160L

    fun lift(v: View, scale: Float = 1.05f) {
        val dz = 10f * v.resources.displayMetrics.density
        v.setOnFocusChangeListener { view, hasFocus ->
            view.animate().cancel()
            view.animate()
                .scaleX(if (hasFocus) scale else 1f)
                .scaleY(if (hasFocus) scale else 1f)
                .translationZ(if (hasFocus) dz else 0f)
                .setDuration(DUR)
                .start()
        }
    }
}
