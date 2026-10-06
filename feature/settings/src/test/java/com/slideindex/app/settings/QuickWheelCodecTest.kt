package com.slideindex.app.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.layout.QuickWheelOpenAnimation
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.overlay.layout.QuickWheelStyleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuickWheelCodec] 与按路径读写容器（[slotAt] / [updateSlot] / 增删换位）的单测。
 *
 * 编解码走 `MutablePreferences` 内存实例，不碰磁盘；整条链路纯 JVM，无需 Robolectric。
 */
class QuickWheelCodecTest {

    private val primaryCircleStyle = QuickWheelStyleSpec(
        showLabels = true,
        initialRadiusDp = 120f,
        containerSizeDp = 48f,
        columnCount = 5,
    )
    private val primaryRectStyle = QuickWheelStyleSpec(
        initialRadiusDp = 140f,
        containerSizeDp = 52f,
        containerGapDp = 16f,
        columnCount = 7,
    )
    private val secondaryCircleStyle = QuickWheelStyleSpec(
        initialRadiusDp = 90f,
        containerSizeDp = 44f,
        ringGapDp = 50f,
        columnCount = 4,
    )
    private val secondaryRectStyle = QuickWheelStyleSpec(
        initialRadiusDp = 96f,
        containerSizeDp = 40f,
        containerGapDp = 12f,
        columnCount = 6,
    )

    private fun roundTrip(wheels: List<QuickWheel>): List<QuickWheel> {
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(wheels, prefs)
        return QuickWheelCodec.decode(prefs)
    }

    /** 一级=矩形、二级=圆形，且两套形态参数刻意不同，用以校验"切换形态不丢参数"。 */
    private fun sampleWheel(): QuickWheel = QuickWheel(
        id = "wheel-1",
        name = "  主力轮盘  ", // 期望写出 / 读回时被 trim
        primaryShape = QuickWheelShape.RECT,
        secondaryShape = QuickWheelShape.CIRCLE,
        primaryStyle = primaryRectStyle,
        secondaryStyle = secondaryCircleStyle,
        primaryCircleStyle = primaryCircleStyle,
        primaryRectStyle = primaryRectStyle,
        secondaryCircleStyle = secondaryCircleStyle,
        secondaryRectStyle = secondaryRectStyle,
        sectorMask = 0b0101,
        centerSlot = QuickWheelSlot(
            iconSource = QuickWheelIconSource.APP_ICON,
            iconValue = "com.example.center",
            name = "中心",
            tapAction = GestureAction.OpenQuickSettings,
        ),
        slots = listOf(
            QuickWheelSlot(
                iconSource = QuickWheelIconSource.TEXT,
                iconValue = "回",
                name = "返回",
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Home,
                tapTrigger = QuickWheelTapTrigger.ON_TOUCH,
                longPressTrigger = QuickWheelLongPressTrigger.ON_TIMEOUT,
                subSlots = listOf(
                    QuickWheelSlot(tapAction = GestureAction.Recents),
                    QuickWheelSlot(placeholder = true),
                ),
            ),
            QuickWheelSlot(placeholder = true),
            QuickWheelSlot(),
        ),
    )

    // ── 编解码往返 ──────────────────────────────────────────

    @Test
    fun roundTrip_preservesEveryPersistedField() {
        val wheel = sampleWheel()
        val decoded = roundTrip(listOf(wheel)).single()

        // 整对象相等：元数据（形态 / 两套样式 / 扇区 / 名称）、中心容器、一级与二级容器。
        assertEquals(wheel.normalized(), decoded)
    }

    @Test
    fun roundTrip_preservesBothShapeStylesForEachLevel() {
        val decoded = roundTrip(listOf(sampleWheel())).single()

        // 当前形态用 rect → primaryStyle 取矩形那套；圆形那套作为"另一形态"原样保留。
        assertEquals(QuickWheelLayoutEngine.normalize(primaryRectStyle), decoded.primaryStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(primaryRectStyle), decoded.primaryRectStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(primaryCircleStyle), decoded.primaryCircleStyle)

        assertEquals(QuickWheelLayoutEngine.normalize(secondaryCircleStyle), decoded.secondaryStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(secondaryCircleStyle), decoded.secondaryCircleStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(secondaryRectStyle), decoded.secondaryRectStyle)
    }

    @Test
    fun roundTrip_preservesRectColumnCount() {
        val decoded = roundTrip(listOf(sampleWheel())).single()
        assertEquals(primaryRectStyle.columnCount, decoded.primaryRectStyle.columnCount)
        assertEquals(secondaryRectStyle.columnCount, decoded.secondaryRectStyle.columnCount)
    }

    @Test
    fun roundTrip_preservesLongPressMs() {
        val decoded = roundTrip(listOf(sampleWheel().copy(longPressMs = 900))).single()
        assertEquals(900, decoded.longPressMs)
    }

    @Test
    fun roundTrip_preservesBackdropBlurAndDim() {
        val decoded = roundTrip(
            listOf(sampleWheel().copy(backdropBlurDp = 24, backdropDimPercent = 35)),
        ).single()
        assertEquals(24, decoded.backdropBlurDp)
        assertEquals(35, decoded.backdropDimPercent)
    }

    @Test
    fun decode_recordWithoutBackdropFallsBackToZero() {
        // 9 段老记录（有 longPressMs、没有背景 / 动画参数）。
        // 当前 13 段 = 0..5 元数据 + 6 sector + 7 longPress + 8/9 背景 + 10/11 动画 + 12 名称
        // → 去掉 8..11 即得 9 段（名称仍留在最后）。
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(listOf(sampleWheel().copy(backdropBlurDp = 30)), prefs)
        val entry = prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES]!!.first()
        val fieldSep = "\u001D"
        val parts = entry.split(fieldSep)
        val legacy = (parts.take(8) + parts.drop(12)).joinToString(fieldSep)
        val decoded = QuickWheelCodec.decodeEntry(legacy)!!
        assertEquals(0, decoded.backdropBlurDp)
        assertEquals(0, decoded.backdropDimPercent)
        // 名称仍然是最后一段，不能被错位读成背景参数。
        assertEquals("主力轮盘", decoded.name)
    }

    @Test
    fun decode_legacyRecordWithoutLongPressMsFallsBackToDefault() {
        // 模拟"含 sectorMask、没有 longPressMs 也没有背景 / 动画参数"的旧记录：
        // 当前 13 段 → 去掉 7..11（longPress + 背景 + 动画）得到旧的 8 段（元数据 + sector + 名称）。
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(listOf(sampleWheel()), prefs)
        val entry = prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES]!!.first()
        val fieldSep = "\u001D"
        val parts = entry.split(fieldSep)
        val legacy = (parts.take(7) + parts.drop(12)).joinToString(fieldSep)
        val decoded = QuickWheelCodec.decodeEntry(legacy)!!
        assertEquals(QuickWheelLayoutEngine.DEFAULT_LONG_PRESS_MS, decoded.longPressMs)
        assertEquals(QuickWheelLayoutEngine.DEFAULT_BACKDROP_BLUR_DP, decoded.backdropBlurDp)
        assertEquals(
            QuickWheelLayoutEngine.DEFAULT_BACKDROP_DIM_PERCENT,
            decoded.backdropDimPercent,
        )
        assertEquals("主力轮盘", decoded.name)
    }

    @Test
    fun roundTrip_trimsName() {
        val decoded = roundTrip(listOf(sampleWheel())).single()
        assertEquals("主力轮盘", decoded.name)
    }

    @Test
    fun roundTrip_preservesPlaceholderAtPrimaryAndSecondary() {
        val decoded = roundTrip(listOf(sampleWheel())).single()
        assertTrue(decoded.slots[1].placeholder)
        assertTrue(decoded.slots[0].subSlots[1].placeholder)
    }

    @Test
    fun roundTrip_preservesActionPayloadContainingSeparators() {
        // 载荷故意混入全部结构分隔符，验证它们不会破坏记录切分。
        val tricky = "com.foo:bar\u001C\u001D\u001E\u001Fbaz"
        val wheel = QuickWheel(
            id = "wheel-sep",
            slots = listOf(QuickWheelSlot(tapAction = GestureAction.LaunchApp(tricky))),
        )
        val decoded = roundTrip(listOf(wheel)).single()
        assertEquals(tricky, decoded.slots[0].tapAction.payload)
    }

    @Test
    fun writeToPreferences_clampsToMaxWheelsAndRewritesOrder() {
        val wheels = (0 until QuickWheel.MAX_WHEELS + 3).map { index ->
            QuickWheel(
                id = "wheel-$index",
                slots = listOf(QuickWheelSlot(tapAction = GestureAction.Back)),
            )
        }
        val decoded = roundTrip(wheels)
        assertEquals(QuickWheel.MAX_WHEELS, decoded.size)
        assertEquals((0 until QuickWheel.MAX_WHEELS).toList(), decoded.map { it.order })
    }

    @Test
    fun decode_emptyPreferencesYieldsNoWheels() {
        assertTrue(QuickWheelCodec.decode(mutablePreferencesOf()).isEmpty())
    }

    @Test
    fun newWheel_seedsNavigationSlotsAndName() {
        val wheel = QuickWheelCodec.newWheel(id = "w", ordinal = 2, order = 1)
        assertEquals("w", wheel.id)
        assertEquals(1, wheel.order)
        assertEquals(QuickWheel.defaultName(2), wheel.name)
        assertEquals(QuickWheel.defaultSlots(), wheel.slots)
        assertEquals(QuickWheel.DEFAULT_SLOT_COUNT, wheel.slots.size)
    }

    @Test
    fun openAnimation_roundTripsAndFallsBackForLegacyRecords() {
        val wheel = QuickWheel(
            id = "w",
            openAnimation = QuickWheelOpenAnimation.NONE,
            animationSpeedPercent = 175,
        )
        val decoded = roundTrip(listOf(wheel)).single()
        assertEquals(QuickWheelOpenAnimation.NONE, decoded.openAnimation)
        assertEquals(175, decoded.animationSpeedPercent)

        // 旧记录（没有这两段）→ 取默认值：从中心展开 + 100%。
        val legacyRaw = QuickWheelCodec.encodeEntry(wheel).let { raw ->
            val parts = raw.split('\u001D')
            (parts.take(10) + parts.last()).joinToString("\u001D")
        }
        val legacy = QuickWheelCodec.decodeEntry(legacyRaw)
        assertEquals(QuickWheelOpenAnimation.EXPAND_FROM_CENTER, legacy?.openAnimation)
        assertEquals(
            QuickWheelLayoutEngine.DEFAULT_ANIMATION_SPEED_PERCENT,
            legacy?.animationSpeedPercent,
        )
    }

    @Test
    fun newWheel_labelsDefaultSlotsSoTheyMatchManuallyAddedContainers() {
        // 默认三容器除"是轮盘自带的"外不应有任何特征差异：名称也要与
        // 「自己新建容器后设置同一动作」一致（编辑页会把名称填成动作文案）。
        val wheel = QuickWheelCodec.newWheel(id = "w", ordinal = 1, order = 0) { action ->
            "name:${action.type.name}"
        }
        assertEquals(
            listOf("name:BACK", "name:HOME", "name:RECENTS"),
            wheel.slots.map { it.name },
        )
    }

    // ── 路径读写 ────────────────────────────────────────────

    @Test
    fun slotAt_resolvesCenterPrimaryAndSecondary() {
        val wheel = sampleWheel()
        assertEquals(wheel.centerSlot, wheel.slotAt(QuickWheelCodec.PATH_CENTER))
        assertEquals(wheel.slots[0], wheel.slotAt(QuickWheelCodec.primaryPath(0)))
        assertEquals(wheel.slots[0].subSlots[1], wheel.slotAt(QuickWheelCodec.secondaryPath(0, 1)))
        assertNull(wheel.slotAt(QuickWheelCodec.primaryPath(99)))
    }

    @Test
    fun updateSlot_appendsPrimaryAtEndIndex() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots())
        val appended = wheel.updateSlot(QuickWheelCodec.primaryPath(wheel.slots.size)) {
            it.copy(tapAction = GestureAction.Back)
        }
        assertEquals(wheel.slots.size + 1, appended.slots.size)
        assertEquals(GestureAction.Back, appended.slots.last().tapAction)
    }

    @Test
    fun updateSlot_appendsSecondaryAtEndIndex() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(QuickWheelSlot(tapAction = GestureAction.Back)),
        )
        val appended = wheel.updateSlot(QuickWheelCodec.secondaryPath(0, 0)) {
            it.copy(tapAction = GestureAction.Home)
        }
        assertEquals(1, appended.slots[0].subSlots.size)
        assertEquals(GestureAction.Home, appended.slots[0].subSlots[0].tapAction)
    }

    @Test
    fun updateSlot_overwritesExistingPrimaryInPlace() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots())
        val updated = wheel.updateSlot(QuickWheelCodec.primaryPath(1)) {
            it.copy(name = "改名")
        }
        assertEquals(wheel.slots.size, updated.slots.size)
        assertEquals("改名", updated.slots[1].name)
    }

    @Test
    fun updateSlot_centerPathPatchesCenterSlot() {
        val wheel = QuickWheel(id = "w")
        val updated = wheel.updateSlot(QuickWheelCodec.PATH_CENTER) { it.copy(name = "中心") }
        assertEquals("中心", updated.centerSlot.name)
    }

    @Test
    fun withGapInserted_appendsTransparentPlaceholderAndSurvivesRoundTrip() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots()).withGapInserted()
        assertTrue(wheel.slots.last().placeholder)

        val decoded = roundTrip(listOf(wheel)).single()
        assertTrue(decoded.slots.last().placeholder)
    }

    @Test
    fun withPrimaryRemovedAt_dropsOnlyTargetSlot() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots())
        val removed = wheel.withPrimaryRemovedAt(0)
        assertEquals(wheel.slots.size - 1, removed.slots.size)
        assertEquals(wheel.slots.drop(1), removed.slots)
    }

    @Test
    fun withPrimaryMoved_reordersSlots() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(
                QuickWheelSlot(name = "a"),
                QuickWheelSlot(name = "b"),
                QuickWheelSlot(name = "c"),
            ),
        )
        val moved = wheel.withPrimaryMoved(from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), moved.slots.map { it.name })
    }

    @Test
    fun withSecondaryMoved_reordersSubSlots() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(
                QuickWheelSlot(
                    subSlots = listOf(
                        QuickWheelSlot(name = "x"),
                        QuickWheelSlot(name = "y"),
                    ),
                ),
            ),
        )
        val moved = wheel.withSecondaryMoved(primaryIndex = 0, from = 0, to = 1)
        assertEquals(listOf("y", "x"), moved.slots[0].subSlots.map { it.name })
    }

    @Test
    fun withSectorToggled_masksOffSingleBit() {
        val wheel = QuickWheel(id = "w", sectorMask = QuickWheelLayoutEngine.SECTOR_ALL)
        val toggled = wheel.withSectorToggled(0)
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL and 0b1110, toggled.sectorMask)
    }

    @Test
    fun sectorSpanDeg_reflectsMask() {
        assertEquals(180, QuickWheel(id = "w", sectorMask = 0b0011).sectorSpanDeg)
    }

}
