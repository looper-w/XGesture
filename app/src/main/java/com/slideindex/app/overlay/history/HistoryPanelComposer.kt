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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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
 */
// 与下面的 `HistoryComposerInput` 同一个理由：本仓库解析到的 foundation 只有
// `BasicTextField(value, onValueChange)` 这批**已废弃**的重载（没有 state 版），
// 而"插到光标处"必须拿到 `selection`，所以这里用 `TextFieldValue` 那个重载。
@Suppress("DEPRECATION")
@Composable
internal fun HistoryComposerModal(
    open: Boolean,
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceError: (Int) -> Unit = {},
    availableTags: List<com.slideindex.app.stash.StashTag> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    onToggleTag: (String) -> Unit = {},
    /** 已预设的提醒（null = 没设）；显示在标签行末尾那枚 ⏰ 胶囊上。 */
    reminderAtMs: Long? = null,
    /** 点那枚 ⏰ 胶囊：打开提醒时间选择器（§0.16.9）。 */
    onReminderClick: () -> Unit = {},
    /** 已选图片的本地路径（trampoline 落下来的，见 §0.16.12）。 */
    imagePaths: List<String> = emptyList(),
    /** 点「＋ 图片」：走中转 Activity 选图（overlay 里不能直接拉系统选择器）。 */
    onAddImage: () -> Unit = {},
    onRemoveImage: (String) -> Unit = {},
    /**
     * 正文光标位置变了（§0.16.15 加图"插到光标处"要用它）。
     *
     * 带默认值 = **向后兼容**：这个组件在别处也有调用点（当时没有"插到光标处"这个需求），
     * 不传就等于不需要光标。
     */
    onSelectionChange: (Int) -> Unit = {},
    imeBottom: Dp,
    focusRequester: FocusRequester,
    onBarHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    /**
     * 输入框的**内部**值（而不是直接用入参 [text]）：光标位置只存在于 `TextFieldValue.selection`，
     * 而"加图插到光标处"（§0.16.15）必须知道它。对外 API 仍是 `String` + `onTextChange`
     * （既有调用点和草稿那套都按 String 走），这里换一层只是为了把 `selection` 留住。
     */
    var fieldValue by remember(open) { mutableStateOf(TextFieldValue(text)) }
    /**
     * 上一次"我们自己发出去"的文本。
     *
     * 用途是分辨 [text] 的两种变化：
     * - **我们自己的输入回环**（用户打字 → `onTextChange` → 父级 → `text` 又回来）：
     *   这时绝不能拿 `TextFieldValue(text)` 重建内部值 —— 那会把光标**重置到开头**
     *   （中文输入法里等于每打一个字光标就跳一次）；
     * - **父级主动清空**（保存成功时 `composerText = ""`）：这时必须重建，否则输入框里留着旧字。
     *
     * 判据就是"父级给的 text 和我们最后发出去的不一样"，只有那种情况才认作"外部改动"。
     */
    var lastEmitted by remember(open) { mutableStateOf(text) }
    if (text != lastEmitted) {
        // 外部改动：正文与光标一起重置（`TextFieldValue(text)` 的 selection 落在 0）。
        // ⚠️ 在组合里直接赋值 state 是安全的（这是 Compose 官方的"从入参推导 state"写法）：
        // 它只会在本次组合里立刻生效，不会无限触发重组。
        fieldValue = TextFieldValue(text)
        lastEmitted = text
    }
    // 初始位置（尤其"打开时草稿里已经有正文"那条路）也要报出去一次，否则父级手上是上一次的
    // 光标位置，用户不打字、直接点选图 → 插到错的地方。
    LaunchedEffect(fieldValue.text, fieldValue.selection) {
        onSelectionChange(fieldValue.selection.start)
    }
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
            // ---- 复用就地编辑条那一套壳：标题行 / 多行正文 / 标签行 / 底部整行主按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stash_composer_fab),
                    style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
                    color = theme.sub,
                    modifier = Modifier.weight(1f),
                )
                HistoryVoiceMicButton(
                    onResult = { recognized ->
                        onTextChange(if (text.isBlank()) recognized else "$text $recognized")
                    },
                    onError = onVoiceError,
                    size = 34.dp,
                    iconSize = 16.dp,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stash_composer_hint),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp),
                        color = theme.sub,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    // ⚠️ 这里用 `TextFieldValue` 的重载（与下面 `HistoryComposerInput` 文档里写的
                    // "本仓库的 foundation 没有 state 版重载"不冲突：`value = TextFieldValue` 是
                    // 另一个**已废弃**的重载，一直存在）。选它纯粹是为了拿到 `selection`。
                    value = fieldValue,
                    onValueChange = { updated ->
                        // 先记"我们自己发的这份"，再往上传：父级拿到 text 之后会原样回传，
                        // 上面的 `text != lastEmitted` 判据就是靠这一行避免"每打一个字光标跳回开头"。
                        lastEmitted = updated.text
                        fieldValue = updated
                        onTextChange(updated.text)
                    },
                    singleLine = false,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = theme.text,
                    ),
                    cursorBrush = SolidColor(theme.accentSolid),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .focusRequester(focusRequester),
                )
            }
            // 加图片（§0.16.12）：一枚「＋ 图片」胶囊 + 已选缩略图（每张右上角可删）。
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HistoryChip(
                    label = stringResource(R.string.stash_composer_image_add),
                    dotColor = null,
                    selected = false,
                    onClick = onAddImage,
                )
                imagePaths.forEach { path ->
                    HistoryComposerThumbnail(
                        path = path,
                        onRemove = { onRemoveImage(path) },
                    )
                }
            }
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

/** 与 `MiuixExpandableSearch` 的 `ExpandableSearchFocusDelayMs` 同值。 */
private const val HistoryComposerFocusDelayMs = 180L

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
private fun decodeComposerThumbnail(path: String): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 160 || bounds.outHeight / sample > 160) {
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
