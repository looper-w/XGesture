package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * 就地编辑条（`.editbar`）的草稿：**"正在编辑哪一条 + 这一条改到一半的东西"**。
 *
 * 与 [StashComposerDraft] 同一个理由（§0.16.14）：面板是 overlay 窗，切前台 App / 拉起
 * 系统相册时系统会把整个窗摘掉（§0.16.13 的 `onViewDetached`），下次打开是**全新的
 * ComposeView + 全新的组合** —— `remember` 的东西这时全丢。用户感受就是
 * "选完图（或切出去再回来）编辑条里改了一半的正文、刚勾的标签全没了"。
 *
 * 为什么草稿要**按 entryId** 存，而不是像加号弹窗那样一份就够：
 * 加号弹窗只有"一条还没存下的新条目"这唯一目标；编辑条是"对某一条已有条目"的编辑，
 * 用户可能这条改一半、又去开另一条。所以这里记住 [entryId]，换条目时旧草稿整体作废
 * （见 [begin] 的两条规则）。
 *
 * ⚠️ 与 [StashComposerDraft] 一样是**进程级**单例：它同时也意味着
 * "退出面板 / 按返回键收起编辑条之后草稿仍留着"（见 [HistoryPanelScreen] 的 KDoc 选择说明）。
 *
 * **写入是实时的，不是"保存时才写一次"**：`HistoryPanelEditBar` 每次正文变化（打字、语音）
 * 都会回调 `onTextChange`，每次勾/取消标签都会回调 `onTagsChange`，`HistoryPanelScreen`
 * 把它们立刻落到这里的 [text] / [tags]；图片则从一开始就直接读写 [imagePaths]。
 * 所以"打了一半的正文"也是活的：窗被系统摘掉、组合重建之后，只要再点这条的编辑条，
 * 三者（正文 / 标签 / 图片）都能原样喂回去。
 */
internal object EditSessionDraft {
    /**
     * 当前草稿属于哪一条条目；null = 没有草稿。
     *
     * 它同时是"要不要把草稿喂回编辑条"的总开关：只有 `entryId` 与本次打开的条目一致时，
     * [text] / [tags] / [imagePaths] 才是这一条的草稿（否则是上一条的残留，必须忽略）。
     */
    val entryId = mutableStateOf<String?>(null)

    /**
     * 正文草稿；每次输入都会更新（见类注释）。
     *
     * `null` = "没有可喂回编辑条的正文"：调用方在回调里把**空串回落成 null**
     * （`onTextChange = { EditSessionDraft.text.value = it.ifBlank { null } }`），
     * 一来与保存时"留空 = 不改正文"的老语义（设计稿 `if (v) s.text = v`）对齐，
     * 二来避免下一次打开编辑条被一份空草稿把输入框清空。
     */
    val text = mutableStateOf<String?>(null)

    /**
     * 标签草稿；每次勾选/取消都会更新（见类注释）。
     *
     * `null` = 用户没动过标签（用条目当前绑定的标签）。
     */
    val tags = mutableStateOf<Set<String>?>(null)

    /**
     * 已选图片：trampoline 落在 cache 里的临时文件绝对路径（§0.16.12）。
     *
     * 这份**不是** `null` 表示"没改过"—— 它天然是"只增不减的待追加列表"：空列表就是
     * 没有待追加的图，没有"原值"需要区分。
     */
    val imagePaths = mutableStateOf<List<String>>(emptyList())

    /**
     * 开始编辑 [entryId]。
     *
     * 两条规则（都在解决"用户实际感受"而不是"代码对称"）：
     * 1. **同一个 entryId 重复调用不清空** —— 这条最重要：窗被摘掉后重开、或者用户收起编辑条
     *    再点同一条的编辑，都会再调一次 [begin]；这时必须把上一份草稿原样留着，
     *    否则"窗被摘掉再回来草稿还在"这件事根本不成立。
     * 2. **换了 entryId 就整体作废上一份**，并且把上一份遗留的 cache 临时图**删掉**：
     *    那些图是"给上一条待追加的"，留着既没人认领（草稿已经作废），又白占 cache。
     */
    fun begin(entryId: String) {
        if (this.entryId.value == entryId) return
        discardCurrent()
        this.entryId.value = entryId
    }

    /** 清掉草稿（保存成功、或主动放弃这一条）。cache 临时图一并删除。 */
    fun clear() {
        discardCurrent()
    }

    /** 丢弃当前草稿的全部内容（含 cache 临时图），但不改变谁在编辑 —— [begin] 内部也用它。 */
    private fun discardCurrent() {
        // 先删图再清空列表：反过来的话路径就找不回来了（cache 里会留垃圾）。
        imagePaths.value.forEach { path -> runCatching { File(path).delete() } }
        entryId.value = null
        text.value = null
        tags.value = null
        imagePaths.value = emptyList()
    }
}
