package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 面板内直接记一条（设计稿 `ui_demo_capsule.html` 的 `.fab` + `.composer`）。
 *
 * 几何照设计稿：
 * - `.fab { right:16px; bottom:92px; 54×54; 圆角 19px }` —— 打开时抬到输入条上方 48dp（`.fab.open{bottom:158px}`）
 * - `.composer { left/right:0; bottom:0; padding:12px 16px 28px; 顶部圆角 26px }`
 * - 输入条：48dp 高的胶囊（`.composer input{height:var(--hit);border-radius:var(--r-pill)}`）
 * - 送出键：48dp 圆形，有内容=主题色+白勾，空=`.dim`（灰底 + 次级色勾）
 *
 * ⚠️ 位置全部由**同一个 Column** 决定（FAB → 间隔 → 输入条），不要用绝对偏移量：
 * 这样 FAB 永远跟着输入条走，不需要量高度、也不会在展开动画里穿帮。
 *
 * ⚠️ IME：overlay 窗读不到 `WindowInsets.ime`，必须用 `rememberOverlayImeBottomHeight()`
 * 把整条抬到输入法上方（调用方传 [imeBottom]）。
 */
@Composable
internal fun HistoryComposerFabSlot(
    visible: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(modifier = modifier) {
        HistoryComposerFab(open = open, onClick = { onOpenChange(!open) })
    }
}

/**
 * 「记一条」的**屏幕居中模态**（用户要求：别局限在面板里）。
 *
 * 形状与编辑条同一套：不透明玻璃底 + `glassBorder` 描边 + 12dp 投影 + 22dp 圆角；
 * ⚠️ overlay 窗读不到 `WindowInsets.ime`，用调用方传进来的 [imeBottom] 把整块**往上抬**
 * （居中浮窗被输入法盖住就白做了）。
 *
 * ---
 * ## §0.16.16：正文是**块编辑器**（文字与图片同一列、按顺序）
 *
 * 老实现是"一个 `BasicTextField` + 框下一排缩略图"：图永远在正文**外面**，
 * 只有存下时靠光标位置把正文切成两半、把图夹进去（§0.16.15）—— 用户在输入框里
 * **看不见图**，也表达不出"图 A、文字、图 B"这种交错。本次改成**纵向块流**：
 * 每个 `DraftBlock.Text` 一个输入框、每个 `DraftBlock.Image` 一张整宽的图，
 * 顺序就是 [blocks] 的顺序（图片真的在正文里了）。
 *
 * 为什么非这样不可（不是偷懒）：本仓库解析到的 foundation 1.13.0-alpha03 里
 * `BasicTextField(state = TextFieldState)` **没有** `inlineContent` 参数，
 * `TextFieldState` / `TextFieldBuffer` 上**没有** `appendInlineContent`（那是
 * `AnnotatedString.Builder` 的、只被 `BasicText` 消费）。也就是"文字流里嵌图"
 * 在本版本走不通，块编辑器是唯一能把图**放进正文里**的做法。
 *
 * ⚠️ 代价（刻意接受，别当 bug 改）：每块是各自的输入框，"一段连续正文"的观感靠
 * 统一字号/行高 + 块间 7dp 间距维持；跨块的连续选择/换行不如单个输入框顺滑。
 *
 * ⚠️ 仍然用**已废弃**的 `BasicTextField(value/onValueChange)` 重载：
 * 块内"退格删图"必须能读到 `selection`（`TextFieldValue`），而本版本没有 state 版重载。
 */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun HistoryComposerModal(
    open: Boolean,
    /** 正文的块序列（唯一真相在 [StashComposerDraft.blocks]，这里传进来的是一份快照）。 */
    blocks: List<DraftBlock>,
    /** 改块序列：调用方落回 [StashComposerDraft.updateBlocks]（镜像同步也在那里）。 */
    onBlocksChange: ((List<DraftBlock>) -> List<DraftBlock>) -> Unit,
    onSubmit: () -> Unit,
    onVoiceError: (Int) -> Unit = {},
    availableTags: List<com.slideindex.app.stash.StashTag> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    onToggleTag: (String) -> Unit = {},
    /** 已预设的提醒（null = 没设）；显示在标签行末尾那枚 ⏰ 胶囊上。 */
    reminderAtMs: Long? = null,
    /** 点那枚 ⏰ 胶囊：打开提醒时间选择器（§0.16.9）。 */
    onReminderClick: () -> Unit = {},
    /**
     * 点「＋ 图片」：走中转 Activity 选图（overlay 里不能直接拉系统选择器）。
     *
     * 回调带回来的每张图都由**本组件**插到"当前光标处"（见 [insertImagesAtCursor]）——
     * 调用方只负责拉起选图（以及窗的挂起/恢复），不用懂块怎么切。
     */
    onAddImage: (onPicked: (List<String>) -> Unit) -> Unit = { },
    /** 删掉一张图（✕ 或退格）：调用方要把 cache 里那份临时文件也删掉，别留垃圾。 */
    onRemoveImage: (String) -> Unit = {},
    imeBottom: Dp,
    focusRequester: FocusRequester,
    onBarHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    // 与搜索框同一套节奏：先让展开动画起来再抢焦点，否则 overlay 窗里 IME 常常不弹。
    LaunchedEffect(open) {
        if (open) {
            delay(HistoryComposerFocusDelayMs)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = modifier
                .padding(bottom = imeBottom)
                .shadow(18.dp, shape)
                .clip(shape)
                // 卡片本体吃点击：不然点在卡片空白处会穿到"点空白关闭"的遮罩上（§0.16.9）。
                .historyConsumeTaps()
                .background(theme.glassSolid)
                .border(1.dp, theme.glassBorder, shape)
                .padding(horizontal = 26.dp, vertical = 24.dp)
                .onGloballyPositioned {
                    onBarHeightChanged(with(density) { it.size.height.toDp() })
                },
        ) {
            // ---- 复用就地编辑条那一套壳：标题行 / 块编辑器 / 标签行 / 底部整行主按钮 ----
            Text(
                text = stringResource(R.string.stash_composer_fab),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
                color = theme.sub,
            )
            DraftBlockEditorSurface(
                blocks = blocks,
                onBlocksChange = onBlocksChange,
                onAddImage = onAddImage,
                onRemoveNewImage = onRemoveImage,
                onVoiceError = onVoiceError,
                // 弹窗：回车就是"存下"（提示语「想点什么…回车存下」说的就是它）。
                onSubmitKey = onSubmit,
                hint = stringResource(R.string.stash_composer_hint),
                focusRequester = focusRequester,
                // 弹窗要靠它把焦点从别处拉回正文（比如插完图接着打字）。
                focusDelegation = true,
            )
            // ⏰ 提醒胶囊**永远**在（标签可以为空），按用户建议塞在标签行的行尾、不新起一行（§0.16.9）。
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableTags.forEach { tag ->
                    HistoryChip(
                        label = tag.name,
                        dotColor = Color(tag.colorArgb),
                        selected = tag.name in selectedTags,
                        onClick = { onToggleTag(tag.name) },
                    )
                }
                HistoryChip(
                    label = if (reminderAtMs == null) {
                        "⏰ " + stringResource(R.string.stash_remind_toggle)
                    } else {
                        "⏰ " + formatReminderTime(reminderAtMs)
                    },
                    dotColor = null,
                    selected = reminderAtMs != null,
                    onClick = onReminderClick,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistoryEditActionButton(
                    label = stringResource(R.string.stash_composer_send),
                    icon = Icons.Default.Check,
                    onClick = onSubmit,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 「一块标题小行（「＋图片」+ 可选语音）+ 文字/图片交错的正文列」—— **两个编辑入口共用**的块编辑器
 * （§0.16.16 弹窗，§0.16.17 起就地编辑条也用同一份）。
 *
 * 为什么提出来共用而不是各写一份：光标处插块、退格删图、每块一个 `FocusRequester` 这套
 * 协作只应该有一个真源 —— 两份实现只要有一处走偏（比如一边忘了"位置 0 才认退格"），
 * 用户就会觉得"弹窗能删、编辑条不能删"，而那种 bug 没人愿意去查第二遍。
 *
 * 两个入口的差异**全部**收在参数里（不靠内部 `if (是不是弹窗)`）：
 * - [onSubmitKey]：有值 = 回车提交（弹窗，提示语就是"回车存下"）；**null = 回车换行**（编辑条，
 *   提交只走「保存」按钮）。见 [HistoryComposerTextBlock] 里 `ImeAction` 怎么跟着它走。
 * - [hint]：只有弹窗给占位提示。
 * - [focusDelegation]：块**已经**被聚焦、只要求"把光标挪过来"时要不要再抢一次焦点。
 *   编辑条**不要**（它的主输入框是打开时就抢好焦点的，插完图再抢一次等于把 IME 关掉）；
 *   弹窗要（它靠这个把焦点从别处拉回正文）。
 * - [resolveImagePath]：块里存的路径怎么变成"能解码的路径"。弹窗里本来就是绝对路径（原样返回）；
 *   编辑条里已有图片存的是**文件名**，得拼成暂存夹里的路径。
 * - [showVoiceButton]：编辑条的语音按钮在自己的标题行上，正文这一层不要重复摆一个。
 * - [onTextInsertedAtCursor]：语音识别结果插进某个文字块之后的**回调**。
 *   编辑条的语音按钮在标题行上（本层之外），只有本层知道"插进了哪一块、光标在哪"，
 *   所以用它把这次改动转给外面（弹窗不需要：它的语音按钮就在本层里，`insertVoiceAtCursor`
 *   已经在回调里把改动交给 `onBlocksChange` 了）。
 */
@Composable
internal fun DraftBlockEditorSurface(
    blocks: List<DraftBlock>,
    onBlocksChange: ((List<DraftBlock>) -> List<DraftBlock>) -> Unit,
    onAddImage: ((onPicked: (List<String>) -> Unit) -> Unit)?,
    onRemoveNewImage: (String) -> Unit,
    onVoiceError: (Int) -> Unit,
    /** 有值 = 正文里的回车是"提交"；null = 回车换行（见函数 KDoc）。 */
    onSubmitKey: (() -> Unit)?,
    hint: String?,
    focusRequester: FocusRequester?,
    /** 块已经被聚焦时，插块/插字之后还要不要再抢一次焦点。 */
    focusDelegation: Boolean,
    /** 把块里的路径解析成"真能解码的路径"（默认恒等：弹窗里存的本来就是绝对路径）。 */
    resolveImagePath: (String) -> String = { it },
    showVoiceButton: Boolean = true,
    /** 语音识别结果被插到某块光标处之后回调（给"语音按钮在本层外面"的编辑条用）。 */
    onTextInsertedAtCursor: ((List<DraftBlock>) -> Unit)? = null,
) {
    /**
     * 每块的焦点句柄。`remember` 一次、按 id 取用：**不能**跟着块生灭 ——
     * 焦点衔接（删图后跳到上一个文字块）恰恰发生在"新块刚加进来、下一帧才要去要焦点"的那一刻。
     */
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    /**
     * 各文字块**最近一次**的选区。用途是"图片插到光标处"：点「＋图片」会让输入框失焦
     * （系统相册是另一个 Activity），那时再去问输入框要 `selection` 是拿不到的，
     * 只有这里留着的那一份是准的。
     */
    val latestValues = remember { mutableStateMapOf<String, TextFieldValue>() }
    /**
     * 焦点**将要**落到哪个块、光标放在第几个字符。
     *
     * 为什么要"将要"：插入/删除**当帧**那块还不存在（或还没测量），`requestFocus()`
     * 会静默失败（overlay 窗里尤其明显）；这里先写下来，等对应的
     * [HistoryComposerTextBlock] 组合出来之后由它自己消费。
     */
    val pendingCursor = remember { mutableStateMapOf<String, Int>() }
    /**
     * 最近一次被聚焦的文字块（**不**在失焦时清掉）。
     *
     * 点「＋图片」那一瞬间正文就失焦了，清掉的话"插到哪一块"就没了；保留它 =
     * "图插到我最后打字的那一块的光标处"，正好是用户的心智模型（[latestValues] 同理）。
     */
    var lastFocusedBlockId by remember { mutableStateOf<String?>(null) }

    /** 当前"插入/修改"的落点：优先最后聚焦那块（还在的话），否则退到最后一个文字块。 */
    fun targetTextBlockId(): String? =
        lastFocusedBlockId?.takeIf { id -> blocks.any { it.id == id && it is DraftBlock.Text } }
            ?: blocks.lastOrNull { it is DraftBlock.Text }?.id

    /** 把 [paths] 里的图**按顺序**插到当前光标处（取消选图 = 空列表 = 什么都不做）。 */
    fun insertImagesAtCursor(paths: List<String>) {
        if (paths.isEmpty()) return
        val targetId = targetTextBlockId()
        // ⚠️ `onBlocksChange` 的 transform 是在快照之外跑的纯函数，所以在**这里**（当帧）读
        // 光标与块顺序，别在 transform 里再读一次外层快照。
        val cursor = targetId?.let { latestValues[it]?.selection?.start } ?: 0
        onBlocksChange { list ->
            // 图片块在**每次** transform 里新建：id 必须由 `newDraftBlockId()` 单调发出来，
            // 复用同一份 `DraftBlock.Image` 实例会让两块撞 id（Compose 的 `key` 会当成同一块）。
            val images = paths.map { path ->
                DraftBlock.Image(id = newDraftBlockId(), path = path)
            }
            // 落点的**块下标**在这里现算一次：外面那一帧读到的顺序可能已经被另一次改动
            // （比如刚删了一张图）挪过位置。
            val index = list.indexOfFirst { it.id == targetId }
            val block = list.getOrNull(index) as? DraftBlock.Text
            // 光标还给"图后面那段文字的开头"，用户可以接着写。
            var cursorTargetId: String? = null
            val next = list.toMutableList().apply {
                if (block != null) {
                    // 光标处切块：前段留在原块、后段进新块、图夹在中间 ——
                    // 这正就是"插到正文里的光标处"。光标在开头得到 [空文字, 图, 后段]，
                    // 在结尾得到 [前段, 图, 空文字]。
                    val cut = cursor.coerceIn(0, block.value.length)
                    val head = block.copy(value = block.value.substring(0, cut))
                    val tail = newEmptyDraftTextBlock().copy(value = block.value.substring(cut))
                    set(index, head)
                    addAll(index + 1, images)
                    add(index + 1 + images.size, tail)
                    cursorTargetId = tail.id
                } else {
                    // 兜底：没有文字块可切（理论上不可能，见空块不变式）→ 图追加到最后，
                    // 并在**图后面**补一个空文字块当落点，否则用户会发现图下面打不了字。
                    addAll(images)
                    if (lastOrNull() !is DraftBlock.Text) {
                        val tail = newEmptyDraftTextBlock()
                        add(tail)
                        cursorTargetId = tail.id
                    }
                }
                if (cursorTargetId == null) {
                    // 现在图后面的那一块（有后段就是它；兜底路径里就是刚补的空文字块）。
                    val after = getOrNull(index + images.size)
                    if (after is DraftBlock.Text) cursorTargetId = after.id
                }
            }
            cursorTargetId?.let { id -> pendingCursor[id] = 0 }
            next
        }
    }

    /** 语音识别结果插到当前光标处（§0.16.16；老行为是一律追加到末尾）。 */
    fun insertVoiceAtCursor(recognized: String) {
        if (recognized.isEmpty()) return
        val targetId = targetTextBlockId() ?: return
        val current = blocks.lastOrNull { it.id == targetId } as? DraftBlock.Text ?: return
        val cursor = (latestValues[targetId]?.selection?.start ?: current.value.length)
            .coerceIn(0, current.value.length)
        onBlocksChange { list ->
            // ⚠️ 这个 lambda 必须**返回列表**（草稿的 `updateBlocks` 要拿它当新的块序列）——
            // 所以先算出新列表，再做"记下光标"这个副作用，别让赋值语句成为最后一个表达式。
            val next = list.map { item ->
                if (item.id == targetId && item is DraftBlock.Text) {
                    item.copy(value = item.value.take(cursor) + recognized + item.value.drop(cursor))
                } else {
                    item
                }
            }
            // 光标跟到识别结果后面：不然吐完一句，下一句会插到刚才那句前面去。
            pendingCursor[targetId] = cursor + recognized.length
            // §0.16.17：把这次改动转给"语音按钮在本层外面"的入口（编辑条）。
            // 传**算好的**新列表而不是让外面自己再算一遍：插入点是本层的私有状态
            // （每块最近一次选区），外面拿不到，重算必然算错地方。
            onTextInsertedAtCursor?.invoke(next)
            next
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (onAddImage != null || showVoiceButton) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 「＋ 图片」：保持老位置（正文上方、标签行之前）—— 两个入口的位置一致，
                // 用户才不会以为"这里能加图、那里不能"。
                //
                // ⚠️ 这里**只**有那枚胶囊：老实现下面还跟着一排缩略图，现在图直接进正文（§0.16.16），
                // 再排一排"框外的图"就等于同一张图在界面上出现两次。
                if (onAddImage != null) {
                    HistoryChip(
                        label = stringResource(R.string.stash_composer_image_add),
                        dotColor = null,
                        selected = false,
                        onClick = {
                            // §0.16.12：overlay 里不能直接拉系统选图，走中转 Activity（回来的是本地文件路径）。
                            // §0.16.14：先把面板窗挂起（它是无障碍覆盖层，不挂起会盖在相册上面），
                            // 回调里**第一件事**就是恢复 —— 取消（picked 为空）也要恢复。
                            //
                            // 插入点由本层自己算（它手上有每块的光标/焦点）：这里**不能**
                            // `focusManager.clearFocus()`，那会把"要插到哪个块"这条信息抹掉。
                            onAddImage { picked -> insertImagesAtCursor(picked) }
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (showVoiceButton) {
                    // 语音：识别结果插到**当前光标处**（插入只有本层算得准，所以按钮也留在本层）。
                    HistoryVoiceMicButton(
                        onResult = { recognized -> insertVoiceAtCursor(recognized) },
                        onError = onVoiceError,
                        size = 34.dp,
                        iconSize = 16.dp,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .heightIn(
                    min = HistoryComposerBodyMinHeightDp,
                    max = HistoryComposerBodyMaxHeightDp,
                ),
            // 块间距 7dp：太小看不出"这是两块"（尤其空块），太大就断成两个输入框了。
            verticalArrangement = Arrangement.spacedBy(HistoryComposerBlockGap),
        ) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is DraftBlock.Text -> key(block.id) {
                        HistoryComposerTextBlock(
                            block = block,
                            // 整份正文只剩这一块时才显示占位提示（老实现的判据是 `text.isEmpty()`）。
                            showHint = hint != null && blocks.size == 1 && block.value.isEmpty(),
                            hint = hint,
                            onValueChange = { updated ->
                                latestValues[block.id] = updated
                                onBlocksChange { list ->
                                    list.map { item ->
                                        if (item.id == block.id && item is DraftBlock.Text) {
                                            item.copy(value = updated.text)
                                        } else {
                                            item
                                        }
                                    }
                                }
                            },
                            onFocusChanged = { focused -> if (focused) lastFocusedBlockId = block.id },
                            onBackspaceOnLeadingEdge = {
                                // 位置 0 的退格 = 想删掉**前一个块**；只有前一个块是图时才需要这套
                                // 特殊处理（文字块之间的退格交给 IME 自己）。
                                val previous = blocks.getOrNull(index - 1) as? DraftBlock.Image
                                if (previous != null) {
                                    onBlocksChange { list -> list.filterNot { it.id == previous.id } }
                                    // 删的是"哪一类图"由调用方决定清哪里：新选的图要删 cache 临时文件。
                                    onRemoveNewImage(previous.path)
                                    // 焦点回到前一个文字块末尾（那块就是光标左边那段文字）。
                                    val before = blocks.getOrNull(index - 2) as? DraftBlock.Text
                                    if (before != null) pendingCursor[before.id] = before.value.length
                                }
                            },
                            focusRequester = focusRequester,
                            ownFocusRequester = focusRequesters.getOrPut(block.id) { FocusRequester() },
                            pendingCursor = pendingCursor,
                            onSubmitKey = onSubmitKey,
                            focusDelegation = focusDelegation,
                        )
                    }

                    is DraftBlock.Image -> key(block.id) {
                        DraftBlockEditorImage(
                            path = resolveImagePath(block.path),
                            onRemove = {
                                onBlocksChange { list -> list.filterNot { it.id == block.id } }
                                onRemoveNewImage(block.path)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 正文里的一个**文字块**（§0.16.16）—— 也就是一个 `BasicTextField`。
 *
 * 四件事都在这里收口（别挪到外面去）：
 * ① `TextFieldValue` 是**块内** state：一次输入的回环（打字 → 上抛 → 父级 → 回传）
 *    如果每次都拿父级的值重建内部值，光标会被重置到开头（中文输入法尤其明显），
 *    所以只认"父级的值和我们最后发出去的不一样"（真正的外部改动，比如保存成功清空）；
 * ② `pendingCursor`：插入/删除之后由 [DraftBlockEditorSurface] 写下的"下一帧光标该在哪"；
 * ③ 退格：**位置 0** 且这一帧"文本没变"时，把事件交给上一层的 [onBackspaceOnLeadingEdge]
 *    （删前一个图片块）。判据必须写在这里 —— 只有这里同时拿得到
 *    `TextFieldValue` 的 `selection` 与 `composition`；
 * ④ **回车策略**（§0.16.17 回归修复）：[onSubmitKey] 为 null 时是 `ImeAction.Default`
 *    **且完全不给 `onDone`** —— 于是回车就只是"插入一个换行"（编辑条要的就是这个）；
 *    有值时才挂 `ImeAction.Done` + `onDone`（弹窗要的"回车存下"）。
 *    ⚠️ 这里**不能**两处都硬编码：早先弹窗与编辑条共用一个写死 Done 的输入框时，
 *    编辑条的回车被吃掉、变成"确认"，用户原话就是"输入法没法换行了"。
 */
@Suppress("DEPRECATION")
@Composable
private fun HistoryComposerTextBlock(
    block: DraftBlock.Text,
    /** 是不是唯一一块且为空（占位提示）。 */
    showHint: Boolean,
    /** 占位提示的文案（null = 这个入口不给占位提示）。 */
    hint: String?,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    /**
     * 位置 0 退格。
     *
     * 为什么只给一个"发生了"的信号、不在这里直接删：切块/删块的规则只在
     * [DraftBlockEditorSurface] 那一层维护（它同时管焦点衔接），这里只当"事件源"。
     */
    onBackspaceOnLeadingEdge: () -> Unit,
    /** 弹窗那边"打开时抢一次焦点"用的共享句柄（编辑条传 null）。 */
    focusRequester: FocusRequester?,
    ownFocusRequester: FocusRequester,
    pendingCursor: MutableMap<String, Int>,
    /** 有值 = 回车提交；null = 回车换行（见函数 KDoc ④）。 */
    onSubmitKey: (() -> Unit)?,
    /** 块**已经**被聚焦时，要不要再抢一次焦点（弹窗要、编辑条不要，见 surface 的 KDoc）。 */
    focusDelegation: Boolean,
) {
    val theme = historyTheme()
    /** 与 `KeyboardOptions` 同一处决定：null ⇒ 回车换行（Default），非 null ⇒ 回车提交（Done）。 */
    var fieldValue by remember(block.id) {
        mutableStateOf(TextFieldValue(block.value, selection = TextRange(block.value.length)))
    }
    /** 上一次"我们自己发出去"的文本：用来分辨"输入回环"和"父级主动改动"。 */
    var lastEmitted by remember(block.id) { mutableStateOf(block.value) }
    /** 这个块当前有没有焦点（决定 [pendingCursor] 消费时要不要真的去抢焦点）。 */
    var isFocused by remember(block.id) { mutableStateOf(false) }
    if (block.value != lastEmitted) {
        // 外部改动（保存成功清空 / 撤销）：整块的值与光标一起重建。
        fieldValue = TextFieldValue(block.value, selection = TextRange(block.value.length))
        lastEmitted = block.value
    }
    // 消费"下一帧光标该在哪"：等这个块真的组合出来（`requestFocus` 才有节点可要焦点）再要。
    // ⚠️ 只在组合里**取走**（`remove` 有副作用但对同一个 id 幂等）：写成 `LaunchedEffect` 会晚一帧，
    // 那时用户可能已经在别处打字，光标会被拽回来。
    pendingCursor.remove(block.id)?.let { position ->
        val clamped = position.coerceIn(0, fieldValue.text.length)
        fieldValue = fieldValue.copy(selection = TextRange(clamped))
        if (!isFocused || focusDelegation) {
            SideEffect {
                // 组合结束、节点已经挂上去了，这时要焦点才要得到。
                runCatching { ownFocusRequester.requestFocus() }
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (showHint && hint != null) {
            Text(
                text = hint,
                style = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp),
                color = theme.sub,
                maxLines = 1,
            )
        }
        BasicTextField(
            // ⚠️ 用 `TextFieldValue` 的**已废弃**重载：选它纯粹是为了拿到 `selection`
            // （"图插到光标处"和"位置 0 退格"都靠它），本版本没有 state 版重载。
            value = fieldValue,
            onValueChange = { updated ->
                val previous = fieldValue
                onValueChange(updated)
                fieldValue = updated
                lastEmitted = updated.text
                // ---- 退格删图（§0.16.16）----
                // 光标在**位置 0**、且是折叠选区（没在选字）时按退格：IME 没有任何字符可删，
                // 于是回调里 `text` 与上一帧一模一样 —— 这个"空转的一次 onValueChange"
                // 就是"位置 0 的退格"。它比 `onKeyEvent` 稳：软键盘的退格在一些 IME 下
                // 根本不上报按键（只有 `onValueChange` 会来）。
                //
                // 为什么不直接用"文本没变"当判据：那会把**光标移动**也算成退格。三个附加条件排掉它：
                // ① 上一帧本来就非空 —— 空块上的退格必须是**静默无效**（否则两个空块之间会来回删）；
                // ② 没有输入法组合串 —— 组合期间 IME 会为了刷新组合区而空转，那时删图是误删；
                // ③ 上一帧光标不在 0 —— "上一帧就在 0 且文本没变"更像点选/移动光标，不认它。
                //
                // ⚠️ 没有把握的地方（简报里也写了）：② 是**两头都判**的，某些输入法在
                // "组合串刚被退格清空"和"这次退格事件"之间会有一帧仍带着组合串，
                // 那一帧会被这里放过 —— 表现就是"要再按一次退格才删掉图"。
                // 宁可多按一次，也不要在用户还在选词时把图删了。
                val backspaceOnLeadingEdge = updated.text == previous.text &&
                    updated.selection.collapsed &&
                    updated.selection.start == 0 &&
                    previous.text.isNotEmpty() &&
                    previous.selection.start != 0 &&
                    updated.composition == null &&
                    previous.composition == null
                if (backspaceOnLeadingEdge) onBackspaceOnLeadingEdge()
            },
            singleLine = false,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = theme.text,
            ),
            cursorBrush = SolidColor(theme.accentSolid),
            // §0.16.17：回车策略跟着 [onSubmitKey] 走 —— null 就是"允许换行、不挂 Done"。
            keyboardOptions = KeyboardOptions(
                imeAction = if (onSubmitKey == null) ImeAction.Default else ImeAction.Done,
            ),
            keyboardActions = if (onSubmitKey == null) {
                KeyboardActions()
            } else {
                // ⚠️ `onDone` 的类型是 `KeyboardActionScope.() -> Unit`（**不带参数**，
                // `KeyboardActionScope` 只是接收者）：写成 `{ _ -> … }` 编译不过。
                KeyboardActions(onDone = { onSubmitKey() })
            },
            modifier = Modifier
                .fillMaxWidth()
                // 空块的最小高度：不然图下面那一块只有 0 高，点不到光标（§0.16.16 的硬要求）。
                .heightIn(min = HistoryComposerEmptyBlockMinHeight)
                .focusRequester(ownFocusRequester)
                .then(
                    // 编辑条那边没有"打开时抢焦点"的共享句柄，不挂空 modifier 也行。
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                )
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    onFocusChanged(state.isFocused)
                },
        )
    }
}

/**
 * 正文里的一张**图片块**（§0.16.16）：占正文整宽、高 [HistoryComposerBodyImageHeight]、
 * 圆角同正文，右上角 ✕ 删除。
 *
 * 为什么不用老那枚 64dp 缩略图（[HistoryComposerThumbnail]）：那是"框外一排小图"的尺寸，
 * 放正文里既看不出是哪张、也表达不了"它和文字平级"。解码相应放大
 * （见 [decodeDraftImage]，仍然 `inSampleSize` 采样，不给内存添乱）。
 *
 * ⚠️ [path] 是**已经解析过**的可解码路径（暂存夹文件名 → 绝对路径那一步在
 * [DraftBlockEditorSurface] 的 `resolveImagePath` 里做完了）。
 */
@Composable
private fun DraftBlockEditorImage(
    path: String,
    onRemove: () -> Unit,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.sm)
    val bitmap = remember(path) { decodeDraftImage(path) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HistoryComposerBodyImageHeight)
            .clip(shape)
            // 解码失败也留一块同样大小的底：用户至少知道"这里有张图"，而不是正文莫名断开。
            .background(theme.fieldBg),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(theme.text.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                // 无障碍文案复用现成的（本仓库不许新增字符串 key）。
                contentDescription = stringResource(R.string.stash_tag_delete),
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** 与 `MiuixExpandableSearch` 的 `ExpandableSearchFocusDelayMs` 同值。 */
private const val HistoryComposerFocusDelayMs = 180L

/** 正文块流的最小高度（沿用老输入框的 160dp：弹窗尺寸不该因为"正文变块"而跳）。 */
private val HistoryComposerBodyMinHeightDp = 160.dp

/** 正文块流的最大高度（沿用老输入框的 320dp：再多内容也先让"存下"键留在屏内）。 */
private val HistoryComposerBodyMaxHeightDp = 320.dp

/** 块间距（6–8dp 这条带里取中间：太小看不出是两块，太大就断成两个输入框了）。 */
private val HistoryComposerBlockGap = 7.dp

/** 空文字块的最小高度：保证"图下面那一块"也点得到光标。 */
private val HistoryComposerEmptyBlockMinHeight = 40.dp

/** 正文里图片块的高度。 */
private val HistoryComposerBodyImageHeight = 120.dp

/** 加号弹窗里图片缩略图的边长。 */
private val HistoryComposerThumbnailSize = 64.dp

/**
 * 加号弹窗里的一张已选图片（§0.16.12）。
 *
 * 图是 trampoline 解码后落在 cache 里的临时文件，所以这里只用**很小的采样**读缩略图
 * （目标 160px，`remember(path)` 缓存），避免每帧重解码。右上角那枚 ✕ 负责移除。
 *
 * `internal`（而不是 private）：就地编辑条"补图"（§0.16.14）要复用**同一个**缩略图，
 * 免得两份 ✕ 的无障碍文案/尺寸各写一遍走偏。
 *
 * ⚠️ §0.16.17 起**没有任何生产调用点**了：两个编辑入口（弹窗 / 编辑条）的正文都是块编辑器
 * （正文里的图是 [DraftBlockEditorImage]，整宽 120dp），"框外一排小图"那个设计已经不存在。
 * 留在这里而不是删掉，是因为它的 ✕ 无障碍文案 / 尺寸 / 解码采样是一份"缩略图"的现成实现，
 * 下次要做"框外小图"（比如多图预览条）可以直接复用，不必再写第三遍。
 */
@Composable
internal fun HistoryComposerThumbnail(path: String, onRemove: () -> Unit) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.sm)
    val thumbnail = remember(path) { decodeComposerThumbnail(path) }
    Box(modifier = Modifier.size(HistoryComposerThumbnailSize)) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(shape),
            )
        } else {
            Box(modifier = Modifier.matchParentSize().clip(shape).background(theme.fieldBg))
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(theme.text.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.stash_tag_delete),
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** 缩略图解码：只求"看得清是哪张"，长边采样到 160px 以内。 */
private fun decodeComposerThumbnail(path: String): android.graphics.Bitmap? =
    decodeComposerImage(path, targetPx = 160)

/**
 * 块编辑器里图片块的解码：整宽 120dp 的画布上 160px 会糊，采样目标提到 480px。
 *
 * 仍然走 `inSampleSize`（2 的幂）—— 这是"看得清"和"不把内存喂爆"之间最省的折中；
 * 结果由调用方 `remember(path)` 缓存，不会每帧重解码。
 *
 * 两个编辑入口（弹窗 / 编辑条）共用它：一个吃 cache 临时文件、一个吃暂存夹文件，
 * 对 `BitmapFactory` 来说都是"一个路径"，所以没必要分两份。
 */
private fun decodeDraftImage(path: String): android.graphics.Bitmap? =
    decodeComposerImage(path, targetPx = 480)

/** 复用同一套采样逻辑：先只读尺寸、再按 2 的幂降采样解出来。失败一律给 null（调用方自己兜底）。 */
private fun decodeComposerImage(path: String, targetPx: Int): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > targetPx || bounds.outHeight / sample > targetPx) {
        sample *= 2
    }
    android.graphics.BitmapFactory.decodeFile(
        path,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    )
}.getOrNull()

/** FAB 距面板底边的距离（见 `HistoryComposerFab` 里的说明）。 */
private val HistoryComposerFabBottomPadding = 32.dp

/** 设计稿 `.fab`：54dp、圆角 19、主题色底 + 白加号；打开后变玻璃底 + 加号转 45°（成了 ×）。 */
@Composable
internal fun HistoryComposerFab(
    open: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(19.dp)
    val rotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = tween(280),
        label = "historyFabRotation",
    )
    val containerColor by animateColorAsState(
        // `.fab { background: var(--accent-solid) }` / `.fab.open { background: var(--g-fill); color: var(--text) }`
        targetValue = if (open) theme.glassFillTop else theme.accentSolid,
        animationSpec = tween(200),
        label = "historyFabContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (open) theme.text else Color.White,
        animationSpec = tween(200),
        label = "historyFabContent",
    )
    Box(
        modifier = Modifier
            // 设计稿 `.fab { right:16px; bottom:92px }`：92px 是当年"底部输入条"时代的坐标
            // （输入条现在是屏幕居中模态），但**底边偏移不能是 0** —— 之前只有 `end = 16.dp`，
            // FAB 直接贴住屏幕底边：一半压在系统 home 手势区里、右下角还被裁掉。
            // 32dp 是"离开手势区、又不飘到列表中间"的值（提示条那边用的是 104dp，属于更保守的一档）。
            .padding(end = 16.dp, bottom = HistoryComposerFabBottomPadding)
            .size(54.dp)
            .shadow(if (open) 10.dp else 18.dp, shape)
            .clip(shape)
            .background(containerColor)
            .then(
                if (open) {
                    Modifier.border(width = 1.dp, color = theme.glassBorder, shape = shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.stash_composer_fab),
            tint = contentColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun HistoryComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceError: (Int) -> Unit,
    availableTags: List<com.slideindex.app.stash.StashTag>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    blurActive: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = HistoryPanelCornerRadius,
                    topEnd = HistoryPanelCornerRadius,
                ),
            )
            // 设计稿的输入条是 `.composer.g` —— 和面板同一套**玻璃**（不是普通面色）。
            .background(theme.glassSolid)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 语音：设计稿里三个输入面都有麦克风，这里是面板输入条那个。
            HistoryVoiceMicButton(
                onResult = { recognized ->
                    onTextChange(if (text.isBlank()) recognized else "$text $recognized")
                },
                onError = onVoiceError,
                size = 40.dp,
            )
            HistoryComposerInput(
                text = text,
                onTextChange = onTextChange,
                onSubmit = onSubmit,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            HistoryComposerSend(
                enabled = text.isNotBlank(),
                onClick = onSubmit,
            )
        }
        // 快速打标签（用户提的需求）：一排可多选的标签胶囊，存下时直接落到新条目上。
        if (availableTags.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableTags.forEach { tag ->
                    HistoryChip(
                        label = tag.name,
                        dotColor = Color(tag.colorArgb),
                        selected = tag.name in selectedTags,
                        onClick = { onToggleTag(tag.name) },
                        mini = false,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.stash_composer_note),
            style = HistoryPanelTypography.meta(),
            color = theme.sub,
            modifier = Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/**
 * 设计稿 `.composer input`：48dp 高胶囊，`0 15px` 内边距，1dp 描边 + 浅底。
 *
 * ⚠️ 用的是 `BasicTextField(value/onValueChange)` 这个**已废弃**的重载：本仓库解析到的
 * foundation 版本里**没有** `TextFieldState` 那个重载（`rememberTextFieldState` /
 * `TextFieldLineLimits` 有，但 `BasicTextField(state = …)` 不存在），
 * 而 overlay 里既有的两处输入（`ClipboardFloatUi` / `PickResultInteractiveText`）也都是这个重载。
 * 升级 foundation 到有 state 重载后，这里应该换成 state 版（中文输入法的组合串更稳）。
 *
 * ⚠️ 这是**面板底部那条输入条**（现在只被 `HistoryComposerBar` 用），
 * 与「记一条」弹窗的块编辑器（[DraftBlockEditorSurface]）无关 —— 后者是 §0.16.16 的块流。
 */
@Suppress("DEPRECATION")
@Composable
private fun HistoryComposerInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    val hint = stringResource(R.string.stash_composer_hint)
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier
            .heightIn(min = 160.dp, max = 320.dp)
            .clip(shape)
            // 设计稿 `.composer input { border: 1px solid var(--btn-bd); background: var(--btn-bg);
            //                    font-size: var(--f-body) }`
            .background(theme.appElev)
            .border(width = 1.dp, color = theme.cardBorder, shape = shape)
            .focusRequester(focusRequester),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.5.sp,
            color = theme.text,
        ),
        cursorBrush = SolidColor(theme.accentSolid),
        singleLine = false,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = hint,
                        style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.body),
                        color = theme.sub,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 设计稿 `.composer .ok`：48dp 圆形。空内容时 `.dim`（点它只提示"先写点什么"）。 */
@Composable
private fun HistoryComposerSend(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(HistoryRadii.pill))
            // `.composer .ok { background: var(--accent-solid) }` / `.ok.dim { background: var(--btn-bg); color: var(--sub) }`
            .background(if (enabled) theme.accentSolid else theme.btnBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.stash_composer_send),
            tint = if (enabled) Color.White else theme.sub,
            modifier = Modifier.size(22.dp),
        )
    }
}
