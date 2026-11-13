package com.slideindex.app.overlay

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PinSizingTest {
    @Test
    fun resolvePinPlacementRect_mapsLogicalRectToCaptureSpace() {
        val screenRect = Rect(259, 2186, 817, 2318)
        val layoutMeta = ScreenshotLayoutMeta(
            screenWidth = 1080,
            screenHeight = 2400,
            captureWidth = 1080,
            captureHeight = 2280,
        )
        val placement = resolvePinPlacementRect(screenRect, layoutMeta)!!
        assertEquals(
            FloatBallOcrRegions.mapScreenRectToBitmap(
                screenRect,
                1080,
                2400,
                1080,
                2280,
            ),
            placement,
        )
    }

    @Test
    fun resolvePinImageDisplaySizePx_usesMappedPlacementWhenMetaDiffers() {
        val screenRect = Rect(100, 200, 400, 500)
        val layoutMeta = ScreenshotLayoutMeta(
            screenWidth = 1080,
            screenHeight = 2400,
            captureWidth = 1080,
            captureHeight = 2280,
        )
        val (width, height) = resolvePinImageDisplaySizePx(
            bitmap = android.graphics.Bitmap.createBitmap(280, 280, android.graphics.Bitmap.Config.ARGB_8888),
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
        )
        val placement = resolvePinPlacementRect(screenRect, layoutMeta)!!
        assertEquals(placement.width(), width)
        assertEquals(placement.height(), height)
    }

    @Test
    fun resolvePinResizeByDrag_keepsAspectAndUsesBothAxes() {
        // 图片钉图（比例 2:1）：横向拖 100px → 变大且比例不变（两轴取平均：1.167 与 1.0 → 1.083）。
        val (w1, h1) = resolvePinResizeByDrag(
            currentW = 600,
            currentH = 300,
            deltaW = 100f,
            deltaH = 0f,
            naturalAspect = 2f,
            minW = 240,
            maxW = 2850,
            minH = 180,
            maxH = 2160,
        )
        assertEquals(650, w1)
        assertEquals(325, h1)

        // 纵向拖 300px 也一样有效（1.0 与 2.0 → 1.5）。
        val (w2, h2) = resolvePinResizeByDrag(
            currentW = 600,
            currentH = 300,
            deltaW = 0f,
            deltaH = 300f,
            naturalAspect = 2f,
            minW = 240,
            maxW = 2850,
            minH = 180,
            maxH = 2160,
        )
        assertEquals(900, w2)
        assertEquals(450, h2)
    }

    @Test
    fun resolvePinResizeByDrag_clampsToBoundsKeepingAspect() {
        // 缩到最小：宽 ≥240 且高 ≥180（比例 2 → 宽 ≥360）→ 取 360×180。
        val (wMin, hMin) = resolvePinResizeByDrag(
            currentW = 600,
            currentH = 300,
            deltaW = -5000f,
            deltaH = -5000f,
            naturalAspect = 2f,
            minW = 240,
            maxW = 2850,
            minH = 180,
            maxH = 2160,
        )
        assertEquals(360, wMin)
        assertEquals(180, hMin)

        // 放到最大：宽上限 2850 生效（高 1425 仍未超高上限）。
        val (wMax, hMax) = resolvePinResizeByDrag(
            currentW = 600,
            currentH = 300,
            deltaW = 5000f,
            deltaH = 5000f,
            naturalAspect = 2f,
            minW = 240,
            maxW = 2850,
            minH = 180,
            maxH = 2160,
        )
        assertEquals(2850, wMax)
        assertEquals(1425, hMax)
    }

    @Test
    fun resolvePinResizeByDrag_textLikeContentResizesBothAxes() {
        // 文字 / 富内容没有比例约束：宽高各自跟随拖动（只做上下限夹取）。
        val (width, height) = resolvePinResizeByDrag(
            currentW = 600,
            currentH = 300,
            deltaW = 60f,
            deltaH = -40f,
            naturalAspect = null,
            minW = 240,
            maxW = 2850,
            minH = 180,
            maxH = 2160,
        )
        assertEquals(660, width)
        assertEquals(260, height)
    }

    @Test
    fun measureTextPinSizePx_keepsShortTextCompact() {
        val (width, height) = measureTextPinSizePx(
            text = "短文本",
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            scaledDensity = 3f,
        )

        // 旧实现固定 55% 屏宽 × 240dp，短文本应明显更小。
        assertTrue(width < (1080 * 0.55f).roundToInt())
        assertTrue(height < (240 * 3f).roundToInt())
    }

    @Test
    fun measureTextPinSizePx_clampsLongTextToMaxBox() {
        val (width, height) = measureTextPinSizePx(
            text = "很长的钉图文字内容，需要换行显示。".repeat(120),
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            scaledDensity = 3f,
        )

        assertTrue(width <= (1080 * 0.55f).roundToInt())
        assertTrue(height <= (2400 * 0.5f).roundToInt())
    }
}
