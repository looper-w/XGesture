package com.slideindex.app.overlay

/**
 * 「拉起外部系统 UI（相册选择器）」期间挂起 / 恢复收纳面板窗的进程级把手（§0.16.14）。
 *
 * 为什么需要它：面板是**无障碍覆盖层**，比系统相册高一层 —— 不挂起的话，用户选图时我们的面板
 * 就盖在相册上面（点不着相册）。而发起选图的地方是 overlay 里的 Compose 代码
 * （`HistoryPanelScreen`），它拿不到 [FloatBallStashPanel] 手里的 `sideHost`，
 * 所以由 [FloatBallStashPanel] 在窗口挂上之后把两个动作注册到这里。
 *
 * ⚠️ 调用方一律写成 `StashPanelExternalUi.suspend?.invoke()`：面板可能已经被系统摘掉
 * （宿主把这两个字段置回了 null），这时选图照常走，只是没什么可挂起的。
 */
internal object StashPanelExternalUi {
    /** 拉起外部 UI **之前**调用：挂起面板窗（不可见 + 不吃触摸）。 */
    var suspend: (() -> Unit)? = null

    /** 外部 UI 的**回调里第一件事**：恢复面板窗。 */
    var resume: (() -> Unit)? = null

    /** 面板窗被摘掉 / 销毁时清空（不残留指向已失效窗口的动作）。 */
    fun clear() {
        suspend = null
        resume = null
    }
}
