package com.slideindex.app.clipboardfloat

/**
 * 浮动剪贴板「多行卡片」样式里，**「长按条目 → 操作条目」**展开的那排按钮。
 *
 * 种类与顺序对齐右侧侧栏剪贴板卡片（`HistoryClipboardEntryCard`）支持的那套动作：
 * 复制、暂存，以及更多菜单里的钉在屏幕 / 分享 / 保存图片 / 删除，另加"取词"。
 * 具体的落地在 `ClipboardFloatService.onEntryAction`（复用侧栏那套 API）。
 */
enum class ClipboardFloatEntryAction {
    /** 写进系统剪贴板。 */
    COPY,

    /** 存进暂存（收纳面板）。 */
    STASH,

    /** 钉在屏幕上（图 / 文 / 富内容各按内容类型走）。 */
    PIN,

    /** 分享（图片用截图分享，文本用文字分享）。 */
    SHARE,

    /** 保存图片到相册（只有带图的条目才有这颗）。 */
    SAVE_IMAGE,

    /** 打开取词面板（与侧栏卡片头部的"取词"一致）。 */
    OPEN_PICK,

    /** 删除这条剪贴板记录。 */
    DELETE,
}
