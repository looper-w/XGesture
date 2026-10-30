package com.slideindex.app.clipboard

/**
 * 「清理剪贴板历史」面板里的**五个内容类型**。
 *
 * 三条硬约束：
 * 1. **互斥**：一条记录只会落在其中一类里（图片优先，其次文件/链接/富文本，最后才是纯文本兜底）；
 * 2. **完备**：五类合起来 = 全部记录 —— 所以"全勾 + 全部范围"等价于清空整个历史；
 * 3. **判定与筛选同源**：前四类复用 [ClipboardHistoryFilter] 那套 SQL 谓词（`ClipboardHistoryStore` 里
 *    只有一份实现），面板上的数字不会和头部筛选的条数打架。
 */
enum class ClipboardCleanupCategory {
    /** 纯文本兜底：既不是链接、也不是富文本 / 图片 / 文件的一切（含 INTENT）。 */
    TEXT,

    /** 整条内容就是一条链接。 */
    LINK,

    /** HTML 富文本（不含图片）。 */
    RICH_TEXT,

    /** 带图片的记录（图片文件 + 图文混排）。 */
    IMAGE,

    /** 文件类 URI（非图片）。 */
    FILE,
}

/** 清理范围：只清未收藏 / 只清已收藏 / 全部。 */
enum class ClipboardCleanupScope {
    UNSTARRED,
    STARRED,
    ALL,
}

/**
 * 面板里"预计清理 N 条 / 预计释放 X 字节"的预览数据。
 *
 * 只是**预估**：真正执行时会用同一条 SQL 重新算，并以实际删除数为准（预览与执行之间可能有新记录写入）。
 */
data class ClipboardCleanupPreview(
    val counts: Map<ClipboardCleanupCategory, Int>,
    /**
     * 每类的占用字节数。
     *
     * 一定要**按类分开**存：面板要按"当前勾选了哪几类"实时汇总，而勾选集合可以为空 ——
     * 只算总数的话，用户把最后一类取消勾选时数字全归零，行会跟着变灰、再也点不回来。
     */
    val bytesByCategory: Map<ClipboardCleanupCategory, Long> = emptyMap(),
) {
    val totalCount: Int get() = counts.values.sum()

    fun bytesOf(categories: Collection<ClipboardCleanupCategory>): Long =
        categories.sumOf { bytesByCategory[it] ?: 0L }
}

/** 清理执行结果。 */
data class ClipboardCleanupResult(
    val deletedCount: Int,
    val freedBytes: Long,
)
