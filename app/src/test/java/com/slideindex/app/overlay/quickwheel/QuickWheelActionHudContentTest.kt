package com.slideindex.app.overlay.quickwheel

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.QuickWheelSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动作提示条（HUD）的"内容推导"单测。
 *
 * 只测纯函数（[quickWheelHudContent] / [quickWheelHudModeFor]），不涉及 Compose 渲染。
 */
class QuickWheelActionHudContentTest {

    private val tapLine = "TAP_LINE"
    private val longPressLine = "LONG_LINE"

    private fun content(
        slot: QuickWheelSlot,
        mode: QuickWheelHudMode,
        hasSubSlots: Boolean = false,
    ) = quickWheelHudContent(
        slot = slot,
        mode = mode,
        tapLine = tapLine,
        longPressLine = longPressLine,
        hasSubSlots = hasSubSlots,
    )

    // ── 模式判定（必须与 handleRelease 的语义一致）──────────────

    @Test
    fun modeFor_plainHover_isTap() {
        assertEquals(
            QuickWheelHudMode.TAP,
            quickWheelHudModeFor(hitIsParentWhileExpanded = false, armed = false),
        )
    }

    @Test
    fun modeFor_armed_isLongPress() {
        assertEquals(
            QuickWheelHudMode.LONG_PRESS,
            quickWheelHudModeFor(hitIsParentWhileExpanded = false, armed = true),
        )
    }

    @Test
    fun modeFor_parentWhileExpanded_isLongPress() {
        // 二级展开时命中父容器 → 松手执行的是父容器的长按动作（handleRelease）。
        assertEquals(
            QuickWheelHudMode.LONG_PRESS,
            quickWheelHudModeFor(hitIsParentWhileExpanded = true, armed = false),
        )
    }

    // ── TAP：主行 = 容器外观，副行预告长按 ──────────────────────

    @Test
    fun tapMode_withName_showsNameAndLongPressPreview() {
        val hud = content(
            slot = QuickWheelSlot(
                name = "相机",
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Home,
            ),
            mode = QuickWheelHudMode.TAP,
        )
        assertFalse(hud.primaryIsLongPress)
        assertEquals("相机", hud.primaryText)
        assertEquals(longPressLine, hud.secondaryText)
        // 有长按动作 → "按满"是有事发生的，要显示进度。
        assertTrue(hud.showProgress)
    }

    @Test
    fun tapMode_withoutName_fallsBackToTapLine() {
        val hud = content(
            slot = QuickWheelSlot(
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Home,
            ),
            mode = QuickWheelHudMode.TAP,
        )
        assertEquals(tapLine, hud.primaryText)
    }

    @Test
    fun tapMode_sameTapAndLongPress_showsSingleLine() {
        // 新建容器默认 tap == longPress：不该重复预告第二行。
        val hud = content(
            slot = QuickWheelSlot(
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Back,
            ),
            mode = QuickWheelHudMode.TAP,
        )
        assertNull(hud.secondaryText)
        assertTrue(hud.showProgress)
    }

    @Test
    fun tapMode_subSlotsOnly_showsProgressWithoutPreview() {
        // 只配了二级子盘（长按＝展开二级）：没有"另一个动作"可预告，但按满同样有事发生。
        val hud = content(
            slot = QuickWheelSlot(tapAction = GestureAction.Back),
            mode = QuickWheelHudMode.TAP,
            hasSubSlots = true,
        )
        assertNull(hud.secondaryText)
        assertTrue(hud.showProgress)
    }

    @Test
    fun tapMode_noActionAtAll_hidesProgress() {
        val hud = content(
            slot = QuickWheelSlot(),
            mode = QuickWheelHudMode.TAP,
        )
        assertEquals(tapLine, hud.primaryText)
        assertNull(hud.secondaryText)
        assertFalse(hud.showProgress)
    }

    // ── LONG_PRESS：主行 = 长按动作，副行预告单击 ───────────────

    @Test
    fun longPressMode_swapsPrimaryAndSecondary() {
        val hud = content(
            slot = QuickWheelSlot(
                name = "相机",
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Home,
            ),
            mode = QuickWheelHudMode.LONG_PRESS,
        )
        assertTrue(hud.primaryIsLongPress)
        assertEquals(longPressLine, hud.primaryText)
        assertEquals(tapLine, hud.secondaryText)
        // 已按满 → 不再需要进度（进度条只在"还没按满"时有意义）。
        assertFalse(hud.showProgress)
    }

    @Test
    fun longPressMode_sameActions_hidesSecondary() {
        val hud = content(
            slot = QuickWheelSlot(
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Back,
            ),
            mode = QuickWheelHudMode.LONG_PRESS,
        )
        assertTrue(hud.primaryIsLongPress)
        assertEquals(longPressLine, hud.primaryText)
        assertNull(hud.secondaryText)
    }
}
