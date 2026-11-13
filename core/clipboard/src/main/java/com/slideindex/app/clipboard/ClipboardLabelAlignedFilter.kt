package com.slideindex.app.clipboard

/**
 * 「跟卡片左上角类型标签一致」的筛选谓词。
 *
 * 原项目有两套判断，对「图文混排」的结论相反：
 *
 * | 位置 | 规则 | 图文混排 |
 * | --- | --- | --- |
 * | 卡片左上角类型标签（`ClipboardEntry.displayTypeLabelKey()`） | 有图 + 有文字 → 富文本 | **富文本** |
 * | 数据层筛选谓词（[ClipboardHistoryStore] 的默认实现） | 有图即图片 | **图片** |
 *
 * 剪贴板浮窗的类型栏是我们新增的界面，用户看到的是卡片左上角的标签，所以浮窗用这份谓词
 * 跟标签对齐；**原项目的历史面板继续走默认谓词，行为完全不变**。
 *
 * 唯一保留的细分：整条内容就是一条链接的 HTML 仍归「链接」（否则同一条会同时出现在
 * 「链接」和「富文本」两处）。这不会影响「图片 / 富文本」之争。
 *
 * 列名直接写字面量：它们与 `ClipboardHistoryStore` 的私有常量一一对应
 * （`entry_type` / `has_image` / `search_blob`），属于数据库 schema，改名要同时改读写两侧。
 */
internal fun ClipboardHistoryFilter.labelAlignedSqlWhere(): String? = when (this) {
    ClipboardHistoryFilter.All -> null

    // 富文本 = 所有 HTML（含图文混排），排除「整条就是链接」的那些。
    ClipboardHistoryFilter.RichText ->
        "$COL_TYPE_NAME = 'HTML' AND NOT $WHOLE_LINK_PREDICATE"

    // 图片 = 带图且**不是 HTML**：HTML 一律归富文本/链接，与卡片标签的口径一致。
    ClipboardHistoryFilter.Image ->
        "$COL_HAS_IMAGE_NAME = 1 AND $COL_TYPE_NAME <> 'HTML'"

    ClipboardHistoryFilter.File ->
        "$COL_HAS_IMAGE_NAME = 0 AND $COL_TYPE_NAME = 'URI'"

    ClipboardHistoryFilter.Link ->
        "$COL_TYPE_NAME IN ('TEXT', 'HTML') AND $WHOLE_LINK_PREDICATE"

    // 文本兜底：无图、非 HTML、非整条链接。
    // 刻意**包含** URI：本应用没有「文件」这一类，非图片的文件 URI 也归文本，否则它在类型栏里
    // 哪个 chip 都进不去（只在「全部」里出现），跟清理面板的划分也会对不上。
    ClipboardHistoryFilter.Text ->
        "$COL_HAS_IMAGE_NAME = 0" +
            " AND $COL_TYPE_NAME <> 'HTML'" +
            " AND NOT $WHOLE_LINK_PREDICATE"
}

private const val COL_TYPE_NAME = "entry_type"
private const val COL_HAS_IMAGE_NAME = "has_image"
private const val COL_SEARCH_NAME = "search_blob"

/** 与 [ClipboardHistoryStore] 的 `LINK_PREDICATE` 等价：只看 `search_blob` 的**前缀**。 */
private const val WHOLE_LINK_PREDICATE =
    "($COL_SEARCH_NAME LIKE 'http://%'" +
        " OR $COL_SEARCH_NAME LIKE 'https://%'" +
        " OR $COL_SEARCH_NAME LIKE 'www.%')"
