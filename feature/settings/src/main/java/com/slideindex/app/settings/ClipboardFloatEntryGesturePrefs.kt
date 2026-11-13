package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences

object ClipboardFloatEntryGesturePrefs {
    fun readClickAction(
        prefs: Preferences,
        style: ClipboardFloatListStyle,
    ): ClipboardFloatEntryClickAction {
        val legacy = prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_CLICK_ACTION]?.let {
            ClipboardFloatEntryClickAction.fromStorage(it)
        }
        val key = when (style) {
            ClipboardFloatListStyle.SINGLE_LINE -> SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_CLICK_ACTION_SINGLE_LINE
            ClipboardFloatListStyle.CARD -> SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_CLICK_ACTION_CARD
        }
        return prefs[key]?.let { ClipboardFloatEntryClickAction.fromStorage(it) } ?: legacy ?: ClipboardFloatEntryClickAction.PASTE
    }

    /**
     * 「长按条目」（**原地按住不动**）动作 —— 只有多行卡片样式有这一项
     * （单行样式不拆"原地长按 / 按住拖动"）。
     */
    fun readHoldAction(prefs: Preferences): ClipboardFloatEntryHoldAction =
        prefs[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_HOLD_ACTION_CARD]
            ?.let { ClipboardFloatEntryHoldAction.fromStorage(it) }
            ?: ClipboardFloatEntryHoldAction.OPERATE_ENTRY

    fun readLongPressAction(
        prefs: Preferences,
        style: ClipboardFloatListStyle,
    ): ClipboardFloatEntryLongPressAction {
        val key = when (style) {
            ClipboardFloatListStyle.SINGLE_LINE -> SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_LONG_PRESS_ACTION_SINGLE_LINE
            ClipboardFloatListStyle.CARD -> SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_LONG_PRESS_ACTION_CARD
        }
        val stored = prefs[key]
        if (stored != null) {
            return ClipboardFloatEntryLongPressAction.fromStorage(stored)
        }
        return when (style) {
            ClipboardFloatListStyle.SINGLE_LINE -> ClipboardFloatEntryLongPressAction.WORD_TAP
            ClipboardFloatListStyle.CARD -> ClipboardFloatEntryLongPressAction.DRAG_DROP
        }
    }
}
