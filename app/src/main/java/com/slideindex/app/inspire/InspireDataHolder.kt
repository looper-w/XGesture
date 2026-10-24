package com.slideindex.app.inspire

import android.graphics.Rect
import com.slideindex.app.overlay.ScreenshotLayoutMeta

/**
 * GestureEVO InspireDataHolder — passes pick results from overlay to content UI.
 */
object InspireDataHolder {
    private var screenshotBitmap: ManagedBitmap? = null
    var screenshotLayoutMeta: ScreenshotLayoutMeta? = null
        private set
    var accessibilityContent: List<String>? = null
        private set
    var dragRect: Rect? = null
        private set
    var forceImageTextSelection: Boolean = false
        private set

    /**
     * 本次取词**发起瞬间**的前台应用包名（精确来源）。
     *
     * 必须在手势按下/开始时记录：等面板出来再读就可能是别的应用了
     * （用户可能已经切走）。见 `FloatBallPickResult.sourcePackage`。
     */
    var pickedSourcePackage: String? = null
        private set

    fun setPickedSourcePackage(value: String?) {
        pickedSourcePackage = value?.takeIf { it.isNotBlank() }
    }

    fun setAccessibilityContent(value: List<String>?) {
        accessibilityContent = value
    }

    fun setDragRect(rect: Rect?) {
        dragRect = rect
    }

    fun setForceImageTextSelection(value: Boolean) {
        forceImageTextSelection = value
    }

    fun replaceScreenshotBitmap(handle: ManagedBitmap?, layoutMeta: ScreenshotLayoutMeta? = null) {
        screenshotBitmap?.close()
        screenshotBitmap = null
        screenshotBitmap = handle?.acquire()
        screenshotLayoutMeta = layoutMeta
    }

    fun acquireScreenshotBitmap(): ManagedBitmap? = screenshotBitmap?.acquire()

    fun clearScreenshotBitmap() {
        screenshotBitmap?.close()
        screenshotBitmap = null
        screenshotLayoutMeta = null
    }

    fun clear() {
        clearScreenshotBitmap()
        // ⚠️ 这里**不能**清 pickedSourcePackage：取词流程是
        // 「手势发起 → 记录来源 → processScreenContent() → clear() → 构建结果」，
        // 在这里清掉会让结果里的来源永远是 null（历史里显示"未知来源 · 取词"）。
        // 它每次取词发起时都会被重新 set，不存在串场。
        accessibilityContent = null
        forceImageTextSelection = false
        dragRect = null
    }
}
