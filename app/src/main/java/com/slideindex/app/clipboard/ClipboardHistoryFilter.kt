package com.slideindex.app.clipboard

/**
 * 剪贴板页签的固定筛选（`docs/capsule-refactor-plan.md` §0.16.4 待办 3）。
 *
 * 分类**必须能可靠推出**，否则宁可不做。这里每一类都落在 `ClipboardEntry` 已经持久化的字段上
 * （`ClipboardHistoryStore` 的 `entry_type` / `has_image` 两个轻量列，两者都是入库时算好、
 * 老库升级时回填的）：
 *
 * | 筛选项 | 判据 | 依据 |
 * | --- | --- | --- |
 * | [Image] | `has_image = 1` | `ClipboardEntry.hasImageContent()`（图片文件 / mime 以 `image/` 开头 / HTML 里的 `<img>`） |
 * | [File] | `has_image = 0` 且 `entry_type = 'URI'` | `ClipboardReader` 只在「有 `content://`/`file://` 文件」和「有图片」两条路上产出 `URI`；`isLocalFileUriEntry()` 同义 |
 * | [RichText] | `has_image = 0` 且 `entry_type = 'HTML'` | `ClipboardReader` 只有「HTML + 文本/图片」那条路给 `HTML`，且必然带 `htmlText` |
 * | [Link] | `has_image = 0` 且 `entry_type ∈ {TEXT, HTML}` 且整条内容以 `http://` / `https://` / `www.` 开头 | 复制的链接由浏览器写成「纯文本 = 链接」或「HTML + 文本 = 链接」 |
 * | [Text] | `has_image = 0` 且 `entry_type ∉ {URI, HTML}` 且不是整条链接 | 兜底：剩下的都算文本（含 `INTENT`） |
 *
 * **刻意不做**的两件事（写了原因，不是漏了）：
 * 1. 长文**中间夹带**的链接不算 [Link] —— 判断得跑正则逐条扫文本，SQLite 侧做不到，
 *    只能在内存里扫「已加载的那几页」，条数就会随滚动变化（正是这次要修的病）。
 *    所以 [Link] 只认「整条就是一条链接」。
 * 2. `INTENT`（`intent://` / 分享出来的 app 链接）不单列一类：它既不是文件也不是网页链接，
 *    硬塞进 [Link] 会让「打开链接」的行为对不上。它落在 [Text] 这个兜底类里。
 *
 * 五类**互斥且完备**（都带 `has_image` / `entry_type` 的限定）：任意一条有且只落在一类里，
 * 因此「全部」= 五类之和。这五个筛选项是"看某一类"，不是把库切成分区。
 */
enum class ClipboardHistoryFilter {
    All,

    /**
     * 纯文本**兜底**：非图片、非文件 URI、非 HTML，且不是「整条就是一条链接」。
     *
     * 与「按规则清理」面板的「纯文本」同一套判定（含 `INTENT`），两处对"文本类有多少条"
     * 的答案不会打架。
     */
    Text,

    Image,
    Link,
    RichText,
    File,
}

/**
 * [ClipboardHistoryFilter] 的**内存版判据**（与 `ClipboardHistoryStore.sqlWhere()` 逐条对应）。
 *
 * 分页列表走 SQL 更准（[ClipboardEntry] 只有已加载的那几条），这份实现是给**搜索结果**用的：
 * 搜索结果是仓库一次性给回的内存列表，套不进分页查询，就在内存里按同一套规则再筛一遍。
 */
fun ClipboardEntry.matchesHistoryFilter(filter: ClipboardHistoryFilter): Boolean {
    val hasImage = hasImageContent()
    val isLink = isWholeLinkEntry()
    return when (filter) {
        ClipboardHistoryFilter.All -> true
        // 文本兜底：非图片、非文件 URI、非 HTML，且不是整条链接（与 SQL 的 Text 分支同义，含 INTENT）。
        ClipboardHistoryFilter.Text ->
            !hasImage &&
                type != ClipboardEntryType.URI &&
                type != ClipboardEntryType.HTML &&
                !isLink
        // 图片：只看 `has_image`，与 SQL 一致（图文混排也算图片）。
        ClipboardHistoryFilter.Image -> hasImage
        ClipboardHistoryFilter.File -> !hasImage && type == ClipboardEntryType.URI
        // 富文本：非图片的 HTML，且排除「整条就是一条链接」。
        ClipboardHistoryFilter.RichText ->
            !hasImage && type == ClipboardEntryType.HTML && !isLink
        ClipboardHistoryFilter.Link ->
            !hasImage &&
                (type == ClipboardEntryType.TEXT || type == ClipboardEntryType.HTML) &&
                isLink
    }
}

/**
 * 是否「整条内容就是一条链接」。
 *
 * 与 SQL 的 `LINK_PREDICATE` 对齐：那边看的是 `search_blob`（= 正文预览 + uri + intentUri）的前缀，
 * 正文为空时预览才退到 uri，所以这里也只取**首位**候选（正文优先），不逐个字段判断 ——
 * 否则「正文正常 + 顺带带个 uri」的条目会被内存版误判成链接，和列表里的结果对不上。
 */
private fun ClipboardEntry.isWholeLinkEntry(): Boolean {
    val head = text.trim()
        .ifBlank { uri?.trim().orEmpty() }
        .ifBlank { intentUri?.trim().orEmpty() }
        .lowercase()
    return head.startsWith("http://") || head.startsWith("https://") || head.startsWith("www.")
}
