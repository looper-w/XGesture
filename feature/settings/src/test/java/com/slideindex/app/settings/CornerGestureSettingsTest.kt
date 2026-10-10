package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CornerGestureSettingsTest {

    @Test
    fun enabledLayerCount_defaultsToBaseLayers() {
        assertEquals(CornerRadialMenuCodec.BASE_LAYER_COUNT, CornerGestureSettings().enabledLayerCount)
        assertEquals(3, CornerGestureSettings().enabledLayerCount)
    }

    @Test
    fun enabledLayerCount_followsWheelLayerCount() {
        assertEquals(3, CornerGestureSettings(wheelLayerCount = 3).enabledLayerCount)
        assertEquals(4, CornerGestureSettings(wheelLayerCount = 4).enabledLayerCount)
        assertEquals(5, CornerGestureSettings(wheelLayerCount = 5).enabledLayerCount)
    }

    @Test
    fun enabledLayerCount_clampsOutOfRangeValues() {
        // 坏存档 / 手改：越界值钳回 3..5，不放大也不缩小。
        assertEquals(3, CornerGestureSettings(wheelLayerCount = 0).enabledLayerCount)
        assertEquals(3, CornerGestureSettings(wheelLayerCount = -7).enabledLayerCount)
        assertEquals(5, CornerGestureSettings(wheelLayerCount = 9).enabledLayerCount)
        assertEquals(5, CornerGestureSettings(wheelLayerCount = Int.MAX_VALUE).enabledLayerCount)
    }

    @Test
    fun clampWheelLayerCount_matchesLayerTableSize() {
        assertEquals(CornerRadialMenuCodec.BASE_LAYER_COUNT, CornerGestureSettings.clampWheelLayerCount(1))
        assertEquals(CornerRadialMenuCodec.layerCount(), CornerGestureSettings.clampWheelLayerCount(99))
    }

    @Test
    fun clampRingSpacingDp_floorFollowsBubbleDiameter() {
        // 下限 = 气泡直径：小于它相邻两环必然重叠。
        assertEquals(34f, CornerGestureSettings.clampRingSpacingDp(20f, 17f))
        assertEquals(24f, CornerGestureSettings.clampRingSpacingDp(10f, 12f))
        assertEquals(56f, CornerGestureSettings.clampRingSpacingDp(30f, 28f))
        assertEquals(
            CornerGestureSettings.MAX_RING_SPACING_DP,
            CornerGestureSettings.clampRingSpacingDp(999f, 17f),
        )
        // 正常值原样保留。
        assertEquals(60f, CornerGestureSettings.clampRingSpacingDp(60f, 17f))
    }

    @Test
    fun clampInnerDiameterDp_isFixedRange() {
        assertEquals(40f, CornerGestureSettings.clampInnerDiameterDp(0f))
        assertEquals(400f, CornerGestureSettings.clampInnerDiameterDp(999f))
        assertEquals(120f, CornerGestureSettings.clampInnerDiameterDp(120f))
    }
}
