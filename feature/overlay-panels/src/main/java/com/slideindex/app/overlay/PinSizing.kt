package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 区域截图时的坐标映射元数据（逻辑屏幕 ↔ takeScreenshot buffer）。 */
data class ScreenshotLayoutMeta(
    val screenWidth: Int,
    val screenHeight: Int,
    val captureWidth: Int,
    val captureHeight: Int
)

data class RegionalScreenshotCrop(
    val bitmap: Bitmap,
    val layoutMeta: ScreenshotLayoutMeta
)

private const val IMAGE_PIN_MAX_WIDTH_FRACTION = 0.55f
private const val IMAGE_PIN_MAX_HEIGHT_FRACTION = 0.55f

/** Maps pick [screenRect] into screenshot / overlay coordinates when sizes differ. */
fun resolvePinPlacementRect(
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?
): Rect? {
    if (screenRect == null || screenRect.isEmpty) return null
    if (layoutMeta == null ||
        (layoutMeta.screenWidth == layoutMeta.captureWidth &&
            layoutMeta.screenHeight == layoutMeta.captureHeight)
    ) {
        return Rect(screenRect)
    }
    return FloatBallOcrRegions.mapScreenRectToBitmap(
        screenRect = screenRect,
        screenWidth = layoutMeta.screenWidth,
        screenHeight = layoutMeta.screenHeight,
        bitmapWidth = layoutMeta.captureWidth,
        bitmapHeight = layoutMeta.captureHeight
    )
}

fun resolvePinImageDisplaySizePx(
    bitmap: Bitmap,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?,
    screenWidthPx: Int,
    screenHeightPx: Int
): Pair<Int, Int> {
    resolvePinPlacementRect(screenRect, layoutMeta)?.takeUnless { it.isEmpty }?.let { placement ->
        return placement.width().coerceAtLeast(1) to placement.height().coerceAtLeast(1)
    }
    val maxW = (screenWidthPx * IMAGE_PIN_MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
    val maxH = (screenHeightPx * IMAGE_PIN_MAX_HEIGHT_FRACTION).roundToInt().coerceAtLeast(1)
    val bmpW = bitmap.width.coerceAtLeast(1)
    val bmpH = bitmap.height.coerceAtLeast(1)
    val scale = min(maxW.toFloat() / bmpW, maxH.toFloat() / bmpH).coerceAtMost(1f)
    return max((bmpW * scale).roundToInt(), 1) to max((bmpH * scale).roundToInt(), 1)
}

/**
 * 右下角手柄拖动 → 新的内容显示尺寸（px）。
 *
 * [naturalAspect] 非空（图片钉图）时**保持该宽高比**：把「当前尺寸 + 拖动增量」当成候选框，
 * 两轴各自算出的缩放**取平均**（拖哪个方向都有效，横竖两个方向都会参与决定最终大小）；
 * 为空（文字 / 富内容）时宽高各自随拖动变化。
 *
 * 夹取时统一换算到「宽」这一个口径（[minW]/[minH]、[maxW]/[maxH] 同时满足），
 * 保证保持比例时不会把任一边挤到上下限之外。
 */
fun resolvePinResizeByDrag(
    currentW: Int,
    currentH: Int,
    deltaW: Float,
    deltaH: Float,
    naturalAspect: Float?,
    minW: Int,
    maxW: Int,
    minH: Int,
    maxH: Int,
): Pair<Int, Int> {
    val safeW = currentW.coerceAtLeast(1)
    val safeH = currentH.coerceAtLeast(1)
    val candidateW = (safeW + deltaW.roundToInt()).coerceAtLeast(1)
    val candidateH = (safeH + deltaH.roundToInt()).coerceAtLeast(1)

    val aspect = naturalAspect?.takeIf { it.isFinite() && it > 0f }
        ?: return candidateW.coerceIn(minW, maxW) to candidateH.coerceIn(minH, maxH)

    val scale = (candidateW.toFloat() / safeW + candidateH.toFloat() / safeH) / 2f
    val driveLo = maxOf(minW.toFloat(), minH * aspect)
    val driveHi = minOf(maxW.toFloat(), maxH * aspect)
    val targetW = (safeW * scale).coerceIn(minOf(driveLo, driveHi), maxOf(driveLo, driveHi))
    return targetW.roundToInt().coerceAtLeast(1) to
        (targetW / aspect).roundToInt().coerceAtLeast(1)
}

fun buildScreenshotLayoutMeta(
    bitmap: Bitmap,
    screenWidthPx: Int,
    screenHeightPx: Int
): ScreenshotLayoutMeta {
    return ScreenshotLayoutMeta(
        screenWidth = screenWidthPx,
        screenHeight = screenHeightPx,
        captureWidth = bitmap.width.coerceAtLeast(1),
        captureHeight = bitmap.height.coerceAtLeast(1)
    )
}
