package com.slideindex.app.overlay.corner

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CornerRadialMenuGeometryTest {
    private val settings = CornerGestureSettings()
    private val density = 3f
    private val slots = CornerRadialMenuCodec.defaultLeftSlots()

    @Test
    fun slotIndexAt_hitsMiddleAndOuterLayers() {
        val anchorX = 0f
        val anchorY = 2400f
        for (slot in listOf(0, 3, 5, 8, 9)) {
            val center = CornerRadialMenuGeometry.bubbleCenterForSlot(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                slotIndex = slot,
                settings = settings,
                density = density,
            )
            val hit = CornerRadialMenuGeometry.slotIndexAt(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                fingerX = center.x,
                fingerY = center.y,
                settings = settings,
                density = density,
                slots = slots,
                editMode = false,
                activeLayerCount = 3,
                revealProgress = 1f,
            )
            assertNotNull("slot $slot should be hit at its center", hit)
            assertEquals(slot, hit)
        }
    }

    @Test
    fun slotIndexAt_hitsFourthAndFifthLayerWhenEnabled() {
        val anchorX = 0f
        val anchorY = 2400f
        val fiveLayers = settings.copy(wheelLayerCount = 5)
        val bound = CornerRadialMenuCodec.normalizeSlots(CornerRadialMenuCodec.defaultLeftSlots())
            .toMutableList()
        for (slot in listOf(15, 23, 24, 34)) {
            bound[slot] = GestureAction.Screenshot
        }
        for (slot in listOf(15, 23, 24, 34)) {
            val center = CornerRadialMenuGeometry.bubbleCenterForSlot(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                slotIndex = slot,
                settings = fiveLayers,
                density = density,
            )
            val hit = CornerRadialMenuGeometry.slotIndexAt(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                fingerX = center.x,
                fingerY = center.y,
                settings = fiveLayers,
                density = density,
                slots = bound,
                editMode = false,
                activeLayerCount = 5,
                revealProgress = 1f,
            )
            assertNotNull("slot $slot should be hit at its center", hit)
            assertEquals(slot, hit)
        }
    }

    @Test
    fun lastVisibleSlotIndex_coversFiveLayers() {
        assertEquals(2, CornerRadialMenuGeometry.lastVisibleSlotIndex(1))
        assertEquals(7, CornerRadialMenuGeometry.lastVisibleSlotIndex(2))
        assertEquals(14, CornerRadialMenuGeometry.lastVisibleSlotIndex(3))
        assertEquals(23, CornerRadialMenuGeometry.lastVisibleSlotIndex(4))
        assertEquals(34, CornerRadialMenuGeometry.lastVisibleSlotIndex(5))
        assertEquals(34, CornerRadialMenuGeometry.lastVisibleSlotIndex(9))
    }

    @Test
    fun displayLayerCount_expandsForHighlightedOuterSlot() {
        assertEquals(3, CornerRadialMenuGeometry.displayLayerCount(1, 8))
        assertEquals(2, CornerRadialMenuGeometry.displayLayerCount(1, 5))
        assertEquals(1, CornerRadialMenuGeometry.displayLayerCount(1, -1))
    }

    @Test
    fun displayLayerCount_respectsEnabledLayerCeiling() {
        // 第 4/5 层没开时，即使高亮落在更深层的槽位也只显示到第 3 层。
        assertEquals(3, CornerRadialMenuGeometry.displayLayerCount(1, 15, maxLayerCount = 3))
        assertEquals(3, CornerRadialMenuGeometry.displayLayerCount(1, 34, maxLayerCount = 3))
        assertEquals(4, CornerRadialMenuGeometry.displayLayerCount(1, 15, maxLayerCount = 4))
        assertEquals(5, CornerRadialMenuGeometry.displayLayerCount(3, 34, maxLayerCount = 5))
        assertEquals(4, CornerRadialMenuGeometry.displayLayerCount(3, 34, maxLayerCount = 4))
        // 没有高亮槽位时只显示已展开的层数。
        assertEquals(1, CornerRadialMenuGeometry.displayLayerCount(1, -1, maxLayerCount = 5))
        assertEquals(3, CornerRadialMenuGeometry.displayLayerCount(3, -1, maxLayerCount = 5))
    }
}
