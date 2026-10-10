package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.launcher.QuickLauncherItemCodec

object CornerRadialMenuCodec {
    /**
     * 五层槽位：内 3 / 5 / 7 / 9 / 11，共 35。
     * 前三层始终启用；第 4、5 层由设置开关决定（见 [CornerGestureSettings.enabledLayerCount]）。
     */
    const val SLOT_COUNT = 35
    const val LEGACY_SLOT_COUNT = 8

    /** 始终启用的层数，第 4 层起需要用户显式开启。 */
    const val BASE_LAYER_COUNT = 3
    val LAYER_SLOT_COUNTS = intArrayOf(3, 5, 7, 9, 11)

    private val LAYER_START_INDICES = IntArray(LAYER_SLOT_COUNTS.size).also { starts ->
        var acc = 0
        LAYER_SLOT_COUNTS.forEachIndexed { index, count ->
            starts[index] = acc
            acc += count
        }
    }

    private const val SEP = "\u001D"

    fun layerCount(): Int = LAYER_SLOT_COUNTS.size

    fun layerOf(globalIndex: Int): Int {
        if (globalIndex !in 0 until SLOT_COUNT) return -1
        return LAYER_START_INDICES.indexOfLast { it <= globalIndex }
    }

    fun layerLocalIndex(globalIndex: Int): Int {
        val layer = layerOf(globalIndex)
        return if (layer < 0) -1 else globalIndex - LAYER_START_INDICES[layer]
    }

    fun layerStartIndex(layer: Int): Int = LAYER_START_INDICES.getOrElse(layer) { 0 }

    fun slotCountInLayer(layer: Int): Int = LAYER_SLOT_COUNTS.getOrElse(layer) { 0 }

    /** [layerCount] 层（含）以内的最后一个槽位下标。 */
    fun lastSlotIndexInLayerCount(layerCount: Int): Int {
        val layer = (layerCount - 1).coerceIn(0, LAYER_SLOT_COUNTS.size - 1)
        return layerStartIndex(layer) + slotCountInLayer(layer) - 1
    }

    fun defaultLeftSlots(): List<GestureAction> = listOf(
        // layer 1 — 3
        GestureAction.OpenIndex,
        GestureAction.QuickLauncher(),
        GestureAction.Screenshot,
        // layer 2 — 5
        GestureAction.FreeWindowCurrentApp,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.LaunchAssistant,
        // layer 3 — 7
        GestureAction.OpenQuickSettings,
        GestureAction.TaskSwitcher,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
    )

    fun defaultRightSlots(): List<GestureAction> = listOf(
        GestureAction.FreeWindowCurrentApp,
        GestureAction.QuickLauncher(),
        GestureAction.OpenIndex,
        GestureAction.Screenshot,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.LaunchAssistant,
        GestureAction.OpenQuickSettings,
        GestureAction.TaskSwitcher,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
    )

    fun decode(encoded: Set<String>, defaults: List<GestureAction>): List<GestureAction> {
        if (encoded.isEmpty()) return normalizeSlots(defaults)
        val byIndex = encoded.mapNotNull { entry ->
            val sep = entry.indexOf(SEP)
            if (sep <= 0) return@mapNotNull null
            val index = entry.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val payload = entry.substring(sep + 1)
            val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return@mapNotNull null
            index to sanitizeSlotAction(action)
        }.toMap()
        val base = normalizeSlots(defaults)
        return List(SLOT_COUNT) { index ->
            byIndex[index] ?: base.getOrElse(index) { GestureAction.None }
        }
    }

    fun encode(slots: List<GestureAction>): Set<String> =
        normalizeSlots(slots).mapIndexed { index, action ->
            "$index$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeSlotAction(action))}"
        }.toSet()

    fun normalizeSlots(slots: List<GestureAction>): List<GestureAction> =
        List(SLOT_COUNT) { index -> slots.getOrElse(index) { GestureAction.None } }

    fun layerTitleResLayer(layer: Int): Int = layer + 1

    private fun sanitizeSlotAction(action: GestureAction): GestureAction =
        action.sanitizeForSlotPicker(SlotPickerKind.CornerWheel)
}
