package com.slideindex.app.settings

import kotlin.math.roundToInt

object ClipboardFloatWindowMetrics {
    const val UNSET_POSITION = -1

    const val MIN_WIDTH_DP = 180
    const val MAX_WIDTH_DP = 600
    const val DEFAULT_WIDTH_DP = 320

    const val MIN_HEIGHT_DP = 160
    const val MAX_HEIGHT_DP = 720
    const val DEFAULT_HEIGHT_DP = 280

    const val PAGE_SIZE = 40

    /** 浮窗贴边留白 */
    const val EDGE_MARGIN_DP = 12

    /** chip 内容边长（Compose 绘制区域，不含 shadow inset） */
    const val CHIP_SIZE_DP = 44

    /** chip Compose shadow 高度 */
    const val CHIP_SHADOW_ELEVATION_DP = 4

    /** chip Window 相对内容各边扩大的 dp，给 outward shadow 留绘制空间 */
    const val CHIP_SHADOW_INSET_DP = 6

    /** chip 默认锚点：在键盘顶缘之上额外抬高，避免被键盘遮挡 */
    const val CHIP_ABOVE_IME_EXTRA_DP = 56

    /** 大窗默认锚点：相对屏幕垂直居中再向上偏移 */
    const val PANEL_DEFAULT_ABOVE_CENTER_DP = 72

    /** 大窗圆角，与 Compose RoundedCornerShape 保持一致 */
    const val PANEL_CORNER_RADIUS_DP = 16

    /** 大窗 Compose shadow 高度 */
    const val PANEL_SHADOW_ELEVATION_DP = 10

    /** 大窗 Window 相对内容各边扩大的 dp，给 outward shadow 留绘制空间 */
    const val PANEL_SHADOW_INSET_DP = 12

    fun chipShadowInsetPx(density: Float): Int =
        (CHIP_SHADOW_INSET_DP * density).roundToInt()

    fun chipWindowSizePx(density: Float): Int =
        (CHIP_SIZE_DP * density).roundToInt() + chipShadowInsetPx(density) * 2

    fun panelShadowInsetPx(density: Float): Int =
        (PANEL_SHADOW_INSET_DP * density).roundToInt()

    fun expandedPanelWindowWidthPx(contentWidthDp: Int, density: Float): Int =
        (contentWidthDp * density).roundToInt() + panelShadowInsetPx(density) * 2

    fun expandedPanelWindowHeightPx(contentHeightDp: Int, density: Float): Int =
        (contentHeightDp * density).roundToInt() + panelShadowInsetPx(density) * 2

    fun contentWidthDpFromWindowWidthPx(windowWidthPx: Int, density: Float): Int =
        coerceWidth(((windowWidthPx - panelShadowInsetPx(density) * 2) / density).roundToInt())

    fun contentHeightDpFromWindowHeightPx(windowHeightPx: Int, density: Float): Int =
        coerceHeight(((windowHeightPx - panelShadowInsetPx(density) * 2) / density).roundToInt())

    fun coerceWidth(value: Int): Int = value.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    fun coerceHeight(value: Int): Int = value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    /**
     * 多行卡片模式的列数：**恒为 1**（单栏铺满）。
     *
     * 早先是响应式的 1/2/3 栏（内容宽 ≥260dp 两栏、≥420dp 三栏）。改成单栏的原因：
     * 多栏时卡片很窄、预览只有 2 行（单栏是 3 行），正文几乎看不全；卡片模式的价值是"看清内容"，
     * 而不是"一屏塞进更多条"。窗口本身的宽度仍由用户拖拽决定（[MIN_WIDTH_DP] ~ [MAX_WIDTH_DP]）。
     *
     * 单行列表样式（SINGLE_LINE）走 LazyColumn，与列数无关。
     */
    fun columnCount(@Suppress("UNUSED_PARAMETER") contentWidthDp: Int): Int = 1
}
