package com.slideindex.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局悬浮窗模糊总开关的决策矩阵：用户开关 × 系统跨窗模糊能力。
 */
class OverlayBlurGateTest {

    @Test
    fun blurAllowed_onlyWhenUserAndSystemBothAllow() {
        assertTrue(OverlayBlurGate.isBlurAllowed(userEnabled = true, systemBlurEnabled = true))
        assertFalse(OverlayBlurGate.isBlurAllowed(userEnabled = false, systemBlurEnabled = true))
        assertFalse(OverlayBlurGate.isBlurAllowed(userEnabled = true, systemBlurEnabled = false))
        assertFalse(OverlayBlurGate.isBlurAllowed(userEnabled = false, systemBlurEnabled = false))
    }

    @Test
    fun effectiveRadius_zeroedWhenUserDisablesBlur() {
        assertEquals(0, OverlayBlurGate.effectiveBlurRadiusDp(userEnabled = false, radiusDp = 57))
        assertEquals(0, OverlayBlurGate.effectiveBlurRadiusDp(userEnabled = false, radiusDp = 0))
    }

    @Test
    fun effectiveRadius_keptUntouchedWhenUserAllowsBlur() {
        assertEquals(57, OverlayBlurGate.effectiveBlurRadiusDp(userEnabled = true, radiusDp = 57))
        assertEquals(0, OverlayBlurGate.effectiveBlurRadiusDp(userEnabled = true, radiusDp = 0))
    }
}
