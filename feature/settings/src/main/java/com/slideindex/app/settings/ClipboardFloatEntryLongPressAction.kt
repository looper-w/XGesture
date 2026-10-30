package com.slideindex.app.settings

/**
 * 浮动剪贴板条目的**「按住拖动」动作**（界面上以前叫"长按条目"）。
 *
 * ⚠️ 语义在 2026-10 收紧：**只有按住之后真的拖动超过抖动死区才触发**；
 * "按住不动"归 [ClipboardFloatEntryHoldAction]（界面上的「长按条目」）管。
 * 设置项的存储键没变（`clipboard_float_entry_long_press_action_card`），所以老设置不需要迁移。
 */
enum class ClipboardFloatEntryLongPressAction(val storageValue: String) {
    WORD_TAP("word_tap"),
    DRAG_DROP("drag_drop"),
    NONE("none"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardFloatEntryLongPressAction =
            entries.firstOrNull { it.storageValue == value } ?: WORD_TAP
    }
}
