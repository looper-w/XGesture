package com.slideindex.app.clipboard

import kotlinx.serialization.Serializable

/**
 * 剪贴板条目的**来源方式**。
 *
 * ⚠️ Android 没有"这条剪贴板是谁写的"的公开 API，所以来源分两种精度：
 * - [PICKED] / [SCREENSHOT]：**由本应用自己发起**（取词 / 截图），发起那一刻我们就知道在哪个应用里 → 精确；
 * - [APP]：用户在其他应用里复制，只能取"剪贴板变化那一瞬间的前台应用" → 推断（正常情况很准，
 *   但后台静默写剪贴板、输入法候选词"复制"、分屏同时可见时会失真）。
 */
@Serializable
enum class ClipboardSourceKind {
    /** 普通复制：来源 = 复制那一刻的前台应用（推断）。 */
    APP,

    /** 取词后复制：来源 = 取词时所在的应用（精确）。 */
    PICKED,

    /** 截图：来源 = 截图触发时所在的应用（精确）。 */
    SCREENSHOT,

    /** 本应用自己的界面写入（例如闪念条目复制）。 */
    SELF,

    /** 无法判定（升级前的老记录 / 当时没有前台应用）。 */
    UNKNOWN,
}

/**
 * 来源精度排序：取词 / 截图（精确，3）> 普通复制（推断，2）> 本应用（1）> 未知（0）。
 *
 * 用途：同一条记录被"同内容回声"再次提到最前时，**低精度不得覆盖高精度**
 * （否则刚写好的「微信 · 取词」会被冲成「本应用」）。
 */
internal fun ClipboardSourceKind.precisionRank(): Int = when (this) {
    ClipboardSourceKind.PICKED, ClipboardSourceKind.SCREENSHOT -> 3
    ClipboardSourceKind.APP -> 2
    ClipboardSourceKind.SELF -> 1
    ClipboardSourceKind.UNKNOWN -> 0
}

/** 一条剪贴板记录的来源：包名 + 方式。 */
data class ClipboardSource(
    val packageName: String?,
    val kind: ClipboardSourceKind = ClipboardSourceKind.UNKNOWN,
) {
    /** 什么都没判定出来。 */
    val isUnknown: Boolean
        get() = packageName.isNullOrBlank() && kind == ClipboardSourceKind.UNKNOWN

    companion object {
        val Unknown = ClipboardSource(null, ClipboardSourceKind.UNKNOWN)
    }
}
