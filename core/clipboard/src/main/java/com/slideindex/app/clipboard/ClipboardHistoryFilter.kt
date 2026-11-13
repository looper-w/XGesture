package com.slideindex.app.clipboard

/**
 * 剪贴板页签的固定筛选（`docs/capsule-refactor-plan.md` §0.16.4 待办 3）。
 *
 * 分类**必须能可靠推出**，否则宁可不做。这里每一类都落在 `ClipboardEntry` 已经持久化的字段上
 * （`ClipboardHistoryStore` 的 `entry_type` / `has_image` 两个轻量列，两者都是入库时算好、
 * 老库升级时回填的）。
 *
 * 存在**两套口径**，只差「图文混排」这一条，别互相带跑：
 *
 * - **浮窗口径**（下表，实现 [labelAlignedSqlWhere]）：跟**卡片左上角类型标签**一致
 *   （`ClipboardEntry.displayTypeLabelKey()`：有图 + 有文字 → 富文本）；
 * - **原项目口径**（`ClipboardHistoryStore.sqlWhere()`）：有图即图片，原项目的历史 / 暂存面板在用。
 *
 * | 筛选项 | 判据（浮窗口径） | 依据 |
 * | --- | --- | --- |
 * | [Image] | `has_image = 1` 且 `entry_type ≠ 'HTML'` | `ClipboardEntry.hasImageContent()` 为真，且不是网页/富文本（HTML 一律归富文本） |
 * | [File] | `has_image = 0` 且 `entry_type = 'URI'` | `ClipboardReader` 只在「有 `content://`/`file://` 文件」和「有图片」两条路上产出 `URI`；`isLocalFileUriEntry()` 同义 |
 * | [RichText] | `entry_type = 'HTML'` 且不是整条链接 | `ClipboardReader` 只有「HTML + 文本/图片」那条路给 `HTML`，且必然带 `htmlText` |
 * | [Link] | `entry_type ∈ {TEXT, HTML}` 且整条内容以 `http://` / `https://` / `www.` 开头 | 复制的链接由浏览器写成「纯文本 = 链接」或「HTML + 文本 = 链接」 |
 * | [Text] | `has_image = 0` 且 `entry_type ∉ {URI, HTML}` 且不是整条链接 | 兜底：剩下的都算文本（含 `INTENT`） |
 *
 * 浮窗口径里 **HTML 优先于图片**：图文混排（一段文字 + 内嵌图，`has_image = 1`）归 [RichText]，
 * 不因为"有图"被算进 [Image]。这也是「按规则清理」面板的划分
 * （见 `ClipboardHistoryStore.ClipboardCleanupCategory.sqlWhere()`）。
 *
 * **刻意不做**的两件事（写了原因，不是漏了）：
 * 1. 长文**中间夹带**的链接不算 [Link] —— 判断得跑正则逐条扫文本，SQLite 侧做不到，
 *    只能在内存里扫「已加载的那几页」，条数就会随滚动变化（正是这次要修的病）。
 *    所以 [Link] 只认「整条就是一条链接」。
 * 2. `INTENT`（`intent://` / 分享出来的 app 链接）不单列一类：它既不是文件也不是网页链接，
 *    硬塞进 [Link] 会让「打开链接」的行为对不上。它落在 [Text] 这个兜底类里。
 *
 * 五类**互斥且完备**：任意一条有且只落在一类里，因此「全部」= 五类之和。
 * 这五个筛选项是"看某一类"，不是把库切成分区。
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

