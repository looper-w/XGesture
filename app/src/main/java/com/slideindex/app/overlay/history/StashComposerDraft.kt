package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * 「记一条」草稿里的一个**块**（§0.16.16 块编辑器）。
 *
 * 为什么需要它：本仓库解析到的 foundation（1.13.0-alpha03）里
 * `BasicTextField(state = TextFieldState)` **没有** `inlineContent` 参数，
 * `TextFieldState` 上也**没有** `appendInlineContent`（那个只存在于 `AnnotatedString.Builder`，
 * 且只被 `BasicText` 消费）。也就是说"文字流里嵌一张图"这条路在本版本里走不通 ——
 * 想让图片**真的出现在正文里**、和文字按顺序排，只能把正文拆成**有序的块**：
 * 每个块要么是一段文字，要么是一张图。
 *
 * ⚠️ 块顺序 = 落盘 `contentBlocks` 的顺序（卡片展开时也按这个顺序画），
 * 所以 `blocks` 是**唯一真相**，见 [StashComposerDraft] 的说明。
 */
internal sealed interface DraftBlock {
    /** 稳定 id：Compose 的 `key`、每块的 `FocusRequester` 表、焦点衔接都要用它。 */
    val id: String

    /** 一段文字（可以是空串 —— 空块正是"图片下面还能点到光标"的落点）。 */
    data class Text(override val id: String, val value: String) : DraftBlock

    /** 一张图：trampoline 解码后落在 cache 里的临时文件绝对路径。 */
    data class Image(override val id: String, val path: String) : DraftBlock
}

/**
 * 「记一条」的草稿（§0.16.14，§0.16.16 起正文改成块序列）。
 *
 * 为什么是**进程级单例**而不是 `remember { mutableStateOf(...) }`：
 * 面板是一个 overlay 窗，切前台 App / 拉起系统相册时系统会把整个窗摘掉
 * （§0.16.13 的 `onViewDetached`），下次打开是**全新的 ComposeView + 全新的组合** ——
 * `remember` 的草稿这时全没了。用户实际感受就是"选完图回来，正文/标签/已选图全丢"。
 *
 * 放在单例里之后：窗没了，草稿还在；面板重建后 `HistoryPanelScreen` 把这几条重新接回同一份
 * `MutableState`（`by StashComposerDraft.xxx`）—— ⚠️ **只有** [blocks] 例外：它是
 * `SnapshotStateList`（`StateObject`，不是 `State`），**不能** `by`，那边是 `val composerBlocks = StashComposerDraft.blocks`
 * 直接引用（它本身 snapshot-aware，读写照样被追踪、照样触发重组）。
 *
 * ⚠️ 明说的取舍（§0.16.16 起有变化，别再按老印象理解）："活得比组合长"是为了扛
 * **系统把窗摘掉**（切前台 App / 拉相册），不是为了"关掉之后还留着"。所以：
 * - 窗被系统摘掉 → 草稿原样还在（这正是本单例存在的理由）；
 * - 用户**主动关掉**弹窗（点 FAB 收起）→ 正文块 + 标签 + 提醒 + 临时图**一起清空**
 *   （见 `HistoryPanelScreen` 的 `onOpenChange`）。老实现是"清标签/提醒/图、**留正文**"，
 *   而现在正文里有图块、图块的 cache 文件又已经被删掉了 —— 只留文字、丢掉图块，
 *   等于把用户"图在正文第 3 块"的编辑状态悄悄改掉；整份丢掉反而可预期。
 *
 * ---
 * ## §0.16.16：正文为什么是"块"而不是"一段字符串 + 一串图"
 *
 * 老模型（本次改掉）是 `text: String` 加 `imagePaths: List<String>` 两条并列的草稿：
 * 图只能排在一行缩略图里"另立门户"，存下时还得靠**光标位置**把正文切成两半再把图塞进去
 * （§0.16.15 那套 `head + images + tail`）—— 用户看到的输入框里**没有图**，
 * 也永远表达不出"图 A、文字、图 B"这种交错顺序。
 *
 * 新模型把正文本身变成 `List<DraftBlock>`：文字块与图片块**同一列、按顺序**，
 * 插入点就是光标、删除就是退格，存下时**照着这个顺序**落 `StashRichPart`。
 *
 * ### 镜像还是废弃？—— 选了**保留镜像**（`text` / `imagePaths` 只读、由 [updateBlocks] 同步）
 *
 * - [text] 和 [imagePaths] **不再由 UI 写**，它们是 `blocks` 的投影，只有 [updateBlocks] 会改它们；
 * - 为什么留：这两个名字在 `HistoryPanelScreen` 之外也还有读者（"空内容"判断之外的旁路、
 *   以及任何按 `String` 拿正文的地方），一次性全改成按块读会把改动面推到本次允许改的三个文件之外，
 *   而本次的目标是"图片进正文"，不是"一次性清掉老 API"。镜像同步的代价是
 *   **每处改块都必须走 [updateBlocks]** —— 这是本文件唯一的写入口，别处不要直接 `blocks.value = ...`。
 * - 语义（老的 `text` 是"整段正文"，现在没有整段了）：
 *   [text] = 所有**非空**文字块按顺序用 `\n` 连起来（正好等于 `StashCoordinator.addRich` 里
 *   通知/提醒用的 `joinToString("\n")`），[imagePaths] = 所有图片块的路径、按顺序。
 *
 * ### 空块不变式
 *
 * [updateBlocks] 每次收尾都会保证"**至少有一个文字块**"（见
 * [normalizeComposerBlocks]）—— UI 侧完全不做这个兜底：光标要有个可放的地方、
 * 「＋图片」也得有个块可切，否则"全是图"的草稿上用户会发现自己打不了字。
 */
internal object StashComposerDraft {
    /**
     * 正文的**唯一真相**：有序块序列（文字 / 图片交错）。
     *
     * 用 `SnapshotStateList` 而不是 `List`：UI 里每块一个 `BasicTextField`，
     * 只认领自己那一块的变化就够了 —— 换成整份 `List` 会让"打字"重组**所有**块
     * （每块还各自持着一个 `TextFieldValue`），块一多就白掉帧。
     */
    val blocks: SnapshotStateList<DraftBlock> = listOf(newEmptyTextBlock()).toMutableStateList()

    /**
     * 正文的**只读投影**（所有非空文字块用 `\n` 连接），由 [updateBlocks] 同步。
     *
     * ⚠️ 不要在 UI 里写它：写 [blocks]（经 [updateBlocks]）才是改正文的唯一方式。
     */
    val text = mutableStateOf("")

    /** 快速选中的标签（存下时落到新条目）。 */
    val tags = mutableStateOf<Set<String>>(emptySet())

    /** 预设的提醒时间（null = 没设）。 */
    val reminderAtMs = mutableStateOf<Long?>(null)

    /**
     * 已选图片的**只读投影**（所有图片块的 cache 临时文件路径，按正文顺序），由 [updateBlocks] 同步。
     *
     * ⚠️ 同上：改图请改 [blocks]。
     */
    val imagePaths = mutableStateOf<List<String>>(emptyList())

    /** 单调递增的块 id 来源。进程级计数器，重启后从头来过也无所谓（id 只在本进程内有意义）。 */
    private var nextId = 1

    /** 取一个新的块 id（`c1` / `c2` …，人肉读日志时比 UUID 好认）。 */
    fun newBlockId(): String = "c${nextId++}"

    /**
     * 一个新的**空**文字块。
     *
     * ⚠️ 它**不**经 [updateBlocks]：调用方拿到的是"还没插进正文的块"（光标处切块、
     * 删图之后补位都要它），插进去那一步才走 [updateBlocks]。id 走 [newBlockId] 全局单调，
     * 所以不会和现有块撞（Compose 的 `key` / 焦点表都按 id 认块）。
     */
    fun newEmptyTextBlock(): DraftBlock.Text = DraftBlock.Text(id = newBlockId(), value = "")

    /**
     * 改块序列的**唯一入口**：写完顺序收尾（补齐空块、刷新镜像）。
     *
     * 为什么要一个入口：镜像（[text] / [imagePaths]）必须和块**永远一致** ——
     * 散着改的话，只要漏掉一处同步，"空内容判断"和"存下时的顺序"就会各说各话。
     */
    fun updateBlocks(transform: (List<DraftBlock>) -> List<DraftBlock>) {
        val next = normalizeComposerBlocks(transform(blocks.toList()))
        blocks.clear()
        blocks.addAll(next)
        text.value = next.filterIsInstance<DraftBlock.Text>()
            .map { it.value }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        imagePaths.value = next.filterIsInstance<DraftBlock.Image>().map { it.path }
    }
}

/**
 * 收尾：一张 `DraftBlock` 都没有时补一个空文字块。
 *
 * 为什么**只**保证"至少一个文字块"、不去合并相邻空块：用户手上有两个挨着的空块是
 * 完全合法的中途状态（删掉中间一张图就会留下它），这时硬合并会把光标/焦点从用户
 * 刚站住的那个块上挪走。真正的合并交给"存下"那一步（空文字块本来就会被跳过）。
 */
private fun normalizeComposerBlocks(blocks: List<DraftBlock>): List<DraftBlock> =
    blocks.ifEmpty { listOf(StashComposerDraft.newEmptyTextBlock()) }
