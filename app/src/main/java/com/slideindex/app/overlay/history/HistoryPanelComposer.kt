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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
                    value = text,
                    onValueChange = onTextChange,
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
