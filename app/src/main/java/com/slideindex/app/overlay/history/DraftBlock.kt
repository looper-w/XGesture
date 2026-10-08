package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 块编辑器里的一个**块**（§0.16.16）：要么一段文字、要么一张图。
 *
 * 为什么需要它：本仓库解析到的 foundation（1.13.0-alpha03）里
 * `BasicTextField(state = TextFieldState)` **没有** `inlineContent` 参数，
 * `TextFieldState` 上也**没有** `appendInlineContent`（那个只存在于 `AnnotatedString.Builder`，
 * 且只被 `BasicText` 消费）。也就是说"文字流里嵌一张图"这条路在本版本里走不通 ——
 * 想让图片**真的出现在正文里**、和文字按顺序排，只能把正文拆成**有序的块**。
 *
 * ⚠️ 块顺序 = 落盘 `contentBlocks` 的顺序（卡片展开时也按这个顺序画），
 * 所以"块序列"是唯一真相，**不要**再在旁边维护一份"整段正文 + 一串图"。
 *
 * ---
 * ## 为什么单独一个文件、而不是塞在 [StashComposerDraft] 里
 *
 * 它现在有**两个**使用方：「记一条」弹窗（[StashComposerDraft]）与就地编辑条
 * （[EditSessionDraft]）。放在任何一边都会逼另一边去 import "对方那个草稿对象"才有类型可用，
 * 那是假的依赖关系。放这里 = **只有一个真源**（不允许各自复制一份 `DraftBlock`）。
 *
 * ## `Image.path` 的两种含义（一个字段、两种来源）
 *
 * - **新选的图**（trampoline / 相册回来）：`path` 是 cache 里的**临时文件绝对路径**；
 * - **条目里已有的图**（编辑条回填）：`path` 是暂存夹图片目录下的**文件名**
 *   （`ClipboardContentBlock.fileName`，落盘时的格式就是它）。
 *
 * 区分方式**不靠猜**：写回时先 decode、再交给仓储的"整体替换块序列"接口，由它决定
 * "这张是新的（要落盘、不能覆盖同名文件）还是老的（文件名照用）"—— 见
 * `StashRepository.replaceBlocks`。UI 侧只多一件事：**显示**前要把文件名拼成可读路径
 * （`StashRepository.imageFilePath`）。
 */
internal sealed interface DraftBlock {
    /** 稳定 id：Compose 的 `key`、每块的 `FocusRequester` 表、焦点衔接都要用它。 */
    val id: String

    /** 一段文字（可以是空串 —— 空块正是"图片下面还能点到光标"的落点）。 */
    data class Text(override val id: String, val value: String) : DraftBlock

    /** 一张图，见接口 KDoc 里"`path` 的两种含义"。 */
    data class Image(override val id: String, val path: String) : DraftBlock
}

/**
 * 块序列的 id 发号器。
 *
 * 为什么是**进程级**计数器：id 只在本进程内有意义（Compose 的 `key`、焦点表），
 * 重启后从头来过无所谓；两个草稿（弹窗 / 编辑条）共用一条序列也不会撞。
 */
private var blockIdSeq = 1

/** 取一个新的块 id（`c1` / `c2` …，人肉读日志时比 UUID 好认）。 */
internal fun newDraftBlockId(): String = "c${blockIdSeq++}"

/**
 * 一个新的**空**文字块（还没插进任何草稿）。
 *
 * 光标处切块、删图之后补位都要它；插进去那一步才走草稿自己的 `updateBlocks`。
 */
internal fun newEmptyDraftTextBlock(): DraftBlock.Text =
    DraftBlock.Text(id = newDraftBlockId(), value = "")

/**
 * 块序列的收尾规则 + 两条"只读镜像"的重算。**两个草稿共用这一份**，别各写一遍。
 *
 * 收尾只保证一件事：**至少有一个文字块**（见 [normalizeDraftBlocks]）。
 *
 * 镜像用 `MutableState` 装（[textMirror] / [imagePathsMirror]）：它们在组合里被读，
 * 必须各自是一个可观察对象；`SnapshotStateList` 装不了"派生字符串"。
 *
 * @param blocks 收尾之后的块序列（调用方负责写回自己的 `SnapshotStateList`）。
 */
internal fun syncDraftMirrors(
    blocks: List<DraftBlock>,
    textMirror: androidx.compose.runtime.MutableState<String>,
    imagePathsMirror: androidx.compose.runtime.MutableState<List<String>>,
): List<DraftBlock> {
    val next = normalizeDraftBlocks(blocks)
    textMirror.value = next.filterIsInstance<DraftBlock.Text>()
        .map { it.value }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    imagePathsMirror.value = next.filterIsInstance<DraftBlock.Image>().map { it.path }
    return next
}

/**
 * 收尾：一张 `DraftBlock` 都没有时补一个空文字块。
 *
 * 为什么**只**保证"至少一个文字块"、不去合并相邻空块：用户手上有两个挨着的空块是
 * 完全合法的中途状态（删掉中间一张图就会留下它），这时硬合并会把光标/焦点从用户
 * 刚站住的那个块上挪走。真正的合并交给"存下"那一步（空文字块本来就会被跳过）。
 */
internal fun normalizeDraftBlocks(blocks: List<DraftBlock>): List<DraftBlock> =
    blocks.ifEmpty { listOf(newEmptyDraftTextBlock()) }

/** 一份块序列的**只读投影**里"文字"那一半的语义（非空文字块用 `\n` 连接）。 */
internal fun draftTextOf(blocks: List<DraftBlock>): String =
    blocks.filterIsInstance<DraftBlock.Text>()
        .map { it.value }
        .filter { it.isNotBlank() }
        .joinToString("\n")

/** 供 [SnapshotStateList] 之外的调用方（保存前的快照）一次性拿到两条投影。 */
internal fun draftImagePathsOf(blocks: List<DraftBlock>): List<String> =
    blocks.filterIsInstance<DraftBlock.Image>().map { it.path }

/** 空的只读镜像（给"没有草稿"的调用方当默认值用，省得到处 `mutableStateOf(emptyList())`）。 */
internal fun emptyDraftImagePathsMirror(): androidx.compose.runtime.MutableState<List<String>> =
    mutableStateOf(emptyList())
