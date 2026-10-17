package com.slideindex.app.settings

/**
 * 浮动剪贴板「多行卡片」样式的**「长按条目」动作** —— 指"按住不动"（原地长按）。
 *
 * 与之相对的是 [ClipboardFloatEntryLongPressAction]（界面上叫**「按住拖动」**）：那个只有
 * "按住之后真的拖动超过死区"才触发。两者由同一套手势状态机区分：
 *
 * ```
 * 按下 ──未超死区就抬手────────▶ 单击条目（ClipboardFloatEntryClickAction）
 *   ├── 超过长按阈值且没动 ────▶ 本枚举（长按条目）
 *   └── 长按成立后移动 >死区 ──▶ ClipboardFloatEntryLongPressAction（按住拖动）
 * ```
 *
 * 目前只有一个选项（[OPERATE_ENTRY]）；留着枚举是为了以后像"单击条目"那样扩成多档。
 */
enum class ClipboardFloatEntryHoldAction(val storageValue: String) {
    /** 在该条目下方展开一排操作按钮（复制 / 暂存 / 钉在屏幕 / 分享 / 保存图片 / 删除 / 取词）。 */
    OPERATE_ENTRY("operate_entry"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardFloatEntryHoldAction =
            entries.firstOrNull { it.storageValue == value } ?: OPERATE_ENTRY
    }
}
