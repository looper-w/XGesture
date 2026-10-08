package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf

/**
 * 「记一条」的草稿（§0.16.14）。
 *
 * 为什么是**进程级单例**而不是 `remember { mutableStateOf(...) }`：
 * 面板是一个 overlay 窗，切前台 App / 拉起系统相册时系统会把整个窗摘掉
 * （§0.16.13 的 `onViewDetached`），下次打开是**全新的 ComposeView + 全新的组合** ——
 * `remember` 的草稿这时全没了。用户实际感受就是"选完图回来，正文/标签/已选图全丢"。
 *
 * 放在单例里之后：窗没了，草稿还在；面板重建后 `HistoryPanelScreen` 直接把这几条
 * 重新代理到同一份 `MutableState`（读法见那边的 `by StashComposerDraft.text`）。
 *
 * ⚠️ 是有意的取舍：它同时也意味着**显式关掉面板/弹窗之后正文草稿也会留着**
 * （`onOpenChange(false)` 仍然照旧清标签 / 提醒 / 已选图，但不清正文）。
 */
internal object StashComposerDraft {
    /** 正文。 */
    val text = mutableStateOf("")

    /** 快速选中的标签（存下时落到新条目）。 */
    val tags = mutableStateOf<Set<String>>(emptySet())

    /** 预设的提醒时间（null = 没设）。 */
    val reminderAtMs = mutableStateOf<Long?>(null)

    /** 已选图片：trampoline 落在 cache 里的临时文件绝对路径。 */
    val imagePaths = mutableStateOf<List<String>>(emptyList())
}
