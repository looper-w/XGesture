package com.slideindex.app.clipboard

import kotlinx.serialization.Serializable

/** 一条剪贴板历史。 */
@Serializable
data class ClipboardEntry(
    val id: String,
    val type: ClipboardEntryType = ClipboardEntryType.TEXT,
    val text: String,
    val uri: String? = null,
    val intentUri: String? = null,
    val htmlText: String? = null,
    val mimeType: String? = null,
    val imageFileName: String? = null,
    val imageFileNames: List<String> = emptyList(),
    val contentBlocks: List<ClipboardContentBlock> = emptyList(),
    val createdAtEpochMs: Long,
    /**
     * 来源应用包名。
     *
     * - 普通复制：复制那一刻的前台应用（推断）；
     * - 取词 / 截图：发起那一刻的应用（精确）。
     *
     * null = 未知（升级前的老记录）。见 [ClipboardSourceKind]。
     */
    val sourcePackage: String? = null,
    /** 来源方式；老数据反序列化后为 [ClipboardSourceKind.UNKNOWN]。 */
    val sourceKind: ClipboardSourceKind = ClipboardSourceKind.UNKNOWN,
    /**
     * 是否已收藏（星标）。
     *
     * 只影响**展示与筛选**，不改变删除语义：删除照旧删，只有"按规则清理"和"清空"才区分。
     * 存在行内的 `starred` 列里（便于 SQL 计数/筛选），JSON 里也带一份（读出来直接用）。
     */
    val starred: Boolean = false,
) {
    fun contentKey(): String = ClipboardContentKey.forEntry(this)

    fun matchesQuery(query: String): Boolean {
        val lower = query.lowercase()
        return text.contains(lower, ignoreCase = true) ||
            uri?.contains(lower, ignoreCase = true) == true ||
            intentUri?.contains(lower, ignoreCase = true) == true
    }
}
