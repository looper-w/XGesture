package com.slideindex.app.overlay.corner

import com.slideindex.app.settings.CornerGestureSettings
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerWheelLayoutTest {
    private val settings = CornerGestureSettings()
    private val density = 3f
    private val anchorX = 0f
    private val anchorY = 2400f

    @Test
    fun activeLayerCount_progressiveFromActivation_startsAtInnerLayer() {
        val activationDist = 420f
        val activation = fingerAtRadius(activationDist)
        assertEquals(
            1,
            layerCountAt(activation, activationDist),
        )
    }

    @Test
    fun activeLayerCount_progressiveFromActivation_expandsWithOutwardSwipe() {
        val innerR = CornerWheelLayout.layerRadiusPx(settings, density, 0)
        val middleR = CornerWheelLayout.layerRadiusPx(settings, density, 1)
        val outerR = CornerWheelLayout.layerRadiusPx(settings, density, 2)
        val activationDist = middleR + 80f
        val bandInnerToMiddle = middleR - innerR
        val bandMiddleToOuter = outerR - middleR

        val atActivation = fingerAtRadius(activationDist)
        assertEquals(
            1,
            layerCountAt(atActivation, activationDist),
        )

        val revealMiddle = fingerAtRadius(activationDist + bandInnerToMiddle * 0.6f)
        assertEquals(1, layerCountAt(revealMiddle, activationDist))

        val revealOuter = fingerAtRadius(activationDist + bandInnerToMiddle + bandMiddleToOuter * 0.6f)
        assertEquals(2, layerCountAt(revealOuter, activationDist))

        val fullOuter = fingerAtRadius(activationDist + bandInnerToMiddle + bandMiddleToOuter + 120f)
        assertEquals(3, layerCountAt(fullOuter, activationDist))
    }

    @Test
    fun layerRadiusPx_firstRingHugsInnerHoleThenStepsBySpacing() {
        val firstRingDp = settings.innerDiameterDp / 2f +
            settings.bubbleSizeDp +
            CornerGestureSettings.FIRST_RING_MARGIN_DP
        // 半径完全由「内径 + 气泡 + 余量 + i × 层间距」决定，与层数无关。
        for (layer in 0..4) {
            assertEquals(
                (firstRingDp + layer * settings.ringSpacingDp) * density,
                CornerWheelLayout.layerRadiusPx(settings, density, layer),
                0.01f,
            )
        }
    }

    @Test
    fun wheelOuterEdgeDp_isReachFromCorner() {
        val five = settings.copy(wheelLayerCount = 5)
        // 外沿 = 最外层环心到角的距离 + 一个气泡半径（不是直径）。
        val expected = CornerWheelLayout.layerRadiusDp(five, 4) + five.bubbleSizeDp
        assertEquals(expected, CornerWheelLayout.wheelOuterEdgeDp(five), 0.01f)
        assertTrue(CornerWheelLayout.wheelOuterEdgeDp(five) > CornerWheelLayout.wheelOuterEdgeDp(settings))
    }

    @Test
    fun layerRadiusPx_extraLayers_growOutwardAndKeepExistingRingsStill() {
        val four = settings.copy(wheelLayerCount = 4)
        val five = settings.copy(wheelLayerCount = 5)
        val threeRadii = List(3) { CornerWheelLayout.layerRadiusPx(settings, density, it) }
        val fourRadii = List(4) { CornerWheelLayout.layerRadiusPx(four, density, it) }
        val fiveRadii = List(5) { CornerWheelLayout.layerRadiusPx(five, density, it) }

        // 加层不挪动任何已有环：前 3 环在三/四/五层下完全相同。
        assertEquals(threeRadii, fourRadii.take(3))
        assertEquals(threeRadii, fiveRadii.take(3))

        // 新环接在外面，整体单调外扩。
        assertTrue(threeRadii.zipWithNext().all { (a, b) -> b > a })
        assertTrue(fourRadii.zipWithNext().all { (a, b) -> b > a })
        assertTrue(fiveRadii.zipWithNext().all { (a, b) -> b > a })
        assertTrue(threeRadii.last() < fourRadii.last())
        assertTrue(fourRadii.last() < fiveRadii.last())

        // 环间距 = 设置值，与层数无关，且不小于气泡直径（径向不叠）。
        val bubbleDiameter = settings.bubbleSizeDp * density * 2f
        assertEquals(settings.ringSpacingDp * density, ringGap(threeRadii), 0.01f)
        assertEquals(ringGap(threeRadii), ringGap(fourRadii), 0.01f)
        assertEquals(ringGap(fourRadii), ringGap(fiveRadii), 0.01f)
        assertTrue(ringGap(fiveRadii) >= bubbleDiameter)

        // 外缘判定跟着最外层走。
        assertTrue(
            CornerWheelLayout.wheelOuterHitRadiusPx(five, density) >
                CornerWheelLayout.wheelOuterHitRadiusPx(settings, density),
        )
    }

    @Test
    fun activeLayerCount_nonProgressive_returnsEnabledLayerCount() {
        val four = settings.copy(wheelLayerCount = 4)
        val five = settings.copy(wheelLayerCount = 5)
        assertEquals(3, layerCountAt(fingerAtRadius(200f), 200f, progressive = false))
        assertEquals(4, layerCountAt(fingerAtRadius(200f), 200f, four, progressive = false))
        assertEquals(5, layerCountAt(fingerAtRadius(200f), 200f, five, progressive = false))
    }

    @Test
    fun activeLayerCount_progressive_reachesEveryEnabledLayer() {
        val four = settings.copy(wheelLayerCount = 4)
        val five = settings.copy(wheelLayerCount = 5)
        val activationDist = 300f
        val far = fingerAtRadius(activationDist + 4000f)
        assertEquals(4, layerCountAt(far, activationDist, four))
        assertEquals(5, layerCountAt(far, activationDist, five))
    }

    private fun ringGap(radii: List<Float>): Float =
        radii.zipWithNext().minOf { (inner, outer) -> outer - inner }

    private fun fingerAtRadius(radius: Float): Pair<Float, Float> {
        val radians = Math.toRadians(315.0)
        return (
            anchorX + (cos(radians) * radius).toFloat() to
                anchorY + (sin(radians) * radius).toFloat()
            )
    }

    private fun layerCountAt(
        finger: Pair<Float, Float>,
        activationDist: Float,
        settings: CornerGestureSettings = this.settings,
        progressive: Boolean = true,
    ): Int =
        CornerWheelLayout.activeLayerCount(
            anchor = CornerAnchor.LEFT,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = finger.first,
            fingerY = finger.second,
            settings = settings,
            density = density,
            progressive = progressive,
            activationRadDist = activationDist,
        )
}
