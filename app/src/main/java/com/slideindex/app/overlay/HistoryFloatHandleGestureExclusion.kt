package com.slideindex.app.overlay

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat

/**
 * 给**贴边把手**用的系统返回手势排除区。
 *
 * 为什么不复用 [OverlayPanelSystemGestureExclusion]：那个工具
 * 1. 只构造**左侧** 48dp 矩形 + 底部导航条矩形（`Rect(0, 0, backEdgePx, height)`），
 *    **没有右缘矩形** —— 而把手在屏幕右侧，正好落在系统返回手势区里；
 * 2. `updateExclusionRects` 是 private，重算只在 layout change / attach 时发生，
 *    外部没有手动刷新入口；重复 `attach()` 还会叠加 `OnLayoutChangeListener`。
 *
 * 而把手窗是 `WRAP_CONTENT` + `gravity = END|TOP`，**view 本身就是把手**，
 * 所以排除区就是 view 自己的 bounds —— 用 view 本地坐标表达，系统会按 view
 * 当前的屏幕位置换算，**窗移动（改 `params.y`）后无需重算**。
 *
 * 代价说明白：把手所在的那一小段右缘会失去系统返回手势，**其上下方照常可用**。
 * 这是"右缘放常驻物"必然要付的代价。
 */
internal object HistoryFloatHandleGestureExclusion {

    private val handleTagKey: Int = "history_float_handle_gesture_exclusion".hashCode()

    fun attach(view: View) {
        view.setTag(handleTagKey, true)
        val layoutListener = View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ ->
            applyRects(target)
        }
        view.addOnLayoutChangeListener(layoutListener)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    applyRects(v)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnLayoutChangeListener(layoutListener)
                    v.setTag(handleTagKey, null)
                    ViewCompat.setSystemGestureExclusionRects(v, emptyList())
                }
            }
        )
        if (view.isAttachedToWindow) {
            applyRects(view)
        }
    }

    private fun applyRects(view: View) {
        if (view.width <= 0 || view.height <= 0) {
            ViewCompat.setSystemGestureExclusionRects(view, emptyList())
            return
        }
        // view 本地坐标：整个把手就是我们要保留的那块。
        ViewCompat.setSystemGestureExclusionRects(
            view,
            listOf(Rect(0, 0, view.width, view.height)),
        )
    }
}
