package com.slideindex.app.overlay.history

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType

/**
 * 面板头部 —— **逐条对齐设计稿 `.stream .head`**：
 *
 * ```
 * .head { padding: 40px 16px 0 }
 *   .srchrow { gap: 8px }  .srch(搜索, h40, pill)  .n(条数, 10.5px)  .x(关闭, 40×40 圆)
 *   .ptabs  { padding-top: 14px; gap: 4px }  .tabind(h34, r12, 文字 5% 底)  button(h34, r12)
 *   .chips  { gap: 6px; padding: 12px 0 8px; 右端渐隐 mask }
 * ```
 *
 * 注意 demo **没有标题行、也没有图钉**（标题由页签表达）。这里只多保留了 App 自己的
 * "切换左右侧"按钮，样式照 `.x` 抄（见 `docs/capsule-refactor-plan.md` §0.16）。
 */
@Composable
internal fun HistoryPanelHeader(
    tabLabels: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchHint: String,
    searchFocusRequester: FocusRequester,
    countLabel: String,
    onSearchFocusChanged: (Boolean) -> Unit,
    /** 点搜索框时请宿主先"让窗口可聚焦 + 延时抢焦点"（overlay 窗默认 NOT_FOCUSABLE，见 Screen 里的 effect）。 */
    onSearchRequestFocus: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * 页签下方那一排胶囊（闪念 = 标签筛选，剪贴板 = 固定筛选）。
     *
     * 做成**插槽**而不是往头部塞两套参数：两个页签的行长得一样（`HistoryPanelChipRows.kt`），
     * 但内容与语义完全不同，头部只管"这里有一行、留多少间距"。
     */
    chipRow: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    Column(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 40.dp)) {
        // ---- 第一行：搜索 + 条数 + （切边） + 关闭 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistorySearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hint = searchHint,
                focusRequester = searchFocusRequester,
                onFocusChanged = onSearchFocusChanged,
                onRequestFocus = onSearchRequestFocus,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = countLabel,
                style = TextStyle(fontSize = HistoryFontSizes.tiny),
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            // 设计稿头部就是 [搜索][条数][✕] 三样，**没有图钉**。
            // （"面板在左还是在右"是**手势动作的参数**，见 `ActionExecutor.side`，不靠面板里的按钮切。）
            HistoryHeaderCircleButton(
                icon = Icons.Default.Close,
                contentDescription = null,
                onClick = onDismiss,
            )
        }

        // ---- 页签：34dp 分段控件 + 滑动指示片 ----
        HistoryPanelTabs(
            labels = tabLabels,
            selectedIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(top = 14.dp),
        )

        // ---- 筛选行（闪念 = 标签，剪贴板 = 固定分类）----
        if (chipRow != null) {
            Box(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                chipRow()
            }
        }
    }
}

/** `.srch`：h40、pill、`--btn-bd` 描边 + `--btn-bg` 底；聚焦时换成 accent 60% 描边 + 6% 底。 */
@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onRequestFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val focusManager = LocalFocusManager.current
    // 可编辑态：overlay 窗在"没有输入态"时是 NOT_FOCUSABLE，光有一个 BasicTextField 是**点不动**的
    // （点了也拿不到焦点，因为窗口本身不可聚焦）。所以先只读，用户一点 → 请宿主把窗口切成可聚焦
    // → 延时后再 requestFocus，这时才真的能打字。
    var editorEnabled by remember { mutableStateOf(false) }
    var everFocused by remember { mutableStateOf(false) }
    // 和输入条（`.composer`）**同一套抢焦点节奏**：状态翻成可编辑后，在**字段内部**延时抢焦点。
    // 之前在点击回调里同步抢，真机上拿不到焦点（日志实测 onFocusChanged 一直是 false）。
    LaunchedEffect(editorEnabled) {
        if (!editorEnabled) return@LaunchedEffect
        delay(180)
        runCatching { focusRequester.requestFocus() }
    }
    var focused by remember { mutableStateOf(false) }
    // 用户反馈边框也是灰的 → 不再是 --btn-bd 灰边，改成白描边（和玻璃同一套）。
    val borderColor = if (focused) theme.accent.copy(alpha = 0.70f) else Color.White.copy(alpha = if (theme.isDark) 0.16f else 0.92f)
    val background = if (focused) theme.accent.copy(alpha = 0.08f) else Color.White.copy(alpha = if (theme.isDark) 0.10f else 0.55f)
    val shape = RoundedCornerShape(HistoryRadii.pill)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .shadow(2.dp, shape)
            // 只读态：点整条药丸 = "我要开始搜"（字段这时是 disabled 的，不抢指针）。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !editorEnabled,
            ) {
                editorEnabled = true
                onRequestFocus()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = theme.text.copy(alpha = 0.80f),
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            // 只读态**不渲染 BasicTextField**：即使 enabled = false，它也会把点击吃掉，
            // 外面那层 clickable 永远收不到 —— 这就是搜索框点了没反应的原因。
            if (!editorEnabled) {
                Text(
                    text = if (query.isEmpty()) hint else query,
                    style = TextStyle(fontSize = HistoryFontSizes.sm),
                    color = if (query.isEmpty()) theme.text.copy(alpha = 0.72f) else theme.text,
                    maxLines = 1,
                )
            } else {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = HistoryFontSizes.sm, color = theme.text),
                cursorBrush = SolidColor(theme.accentSolid),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        focused = focusState.isFocused
                        // ⚠️ 只有"**曾经拿到过**焦点又失去"才退回只读态。
                        // 刚组合出来时会回调一次 focused=false，如果那时就 `editorEnabled = false`，
                        // 正在跑的 `LaunchedEffect(editorEnabled)`（负责延时抢焦点）会被当场取消 ——
                        // 搜索框两轮"点了没反应"就是这么来的。
                        if (focusState.isFocused) {
                            everFocused = true
                        } else if (everFocused) {
                            everFocused = false
                            editorEnabled = false
                        }
                        onFocusChanged(focusState.isFocused)
                    },
                )
            }
        }
        // `.srch .clr`：有内容才出现（22×22 圆，文字 12% 底）
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(theme.text.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = theme.text,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** `.stream .x`：40×40 圆、透明底、sub 色。标签管理浮窗的关闭键也复用它。 */
@Composable
internal fun HistoryHeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = theme.text.copy(alpha = 0.92f),
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * `.ptabs`：**34dp 高的分段控件**（不是 miuix 的 TabRow）。
 * 指示片宽 `calc(50% - 2px)`、左侧起 0 / `calc(50% + 2px)`，`left` 用 280ms 缓动滑过去。
 */
@Composable
private fun HistoryPanelTabs(
    labels: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 滑块要"嵌"在轨道里：四周留同样的 3dp 内缩，圆角 = 轨道圆角(12) − 内缩(3) = 9，
        // 宽度也把两侧内缩扣掉。之前左侧从 0 起算、圆角又是 8，所以滑块边缘和轨道轮廓对不上。
        val inset = 3.dp
        val gap = 4.dp
        val half = (maxWidth - inset * 2 - gap) / 2
        val indicatorLeft by animateDpAsState(
            targetValue = inset + if (selectedIndex <= 0) 0.dp else half + gap,
            animationSpec = tween(HistoryDurations.d3, easing = HistoryEasing.out),
            label = "tabind",
        )
        // 精致版分段控件：整条是轨道（segTrack + segBorder），选中片是**白底 + 3dp 投影**。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(HistoryRadii.sm))
                .background(theme.segTrack)
                .border(1.dp, theme.segBorder, RoundedCornerShape(HistoryRadii.sm)),
        )
        Box {
            Box(
                modifier = Modifier
                    .padding(start = indicatorLeft, top = inset, bottom = inset)
                    .width(half)
                    .height(34.dp - inset * 2)
                    .shadow(3.dp, RoundedCornerShape(HistoryRadii.sm - inset))
                    .clip(RoundedCornerShape(HistoryRadii.sm - inset))
                    .background(theme.pillSelected),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    val on = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(HistoryRadii.sm))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onTabSelected(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontSize = HistoryFontSizes.sm,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (on) theme.text else theme.text.copy(alpha = 0.88f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * `.chip`：h30、pill、玻璃底 + 白描边 + 顶部高光；选中态见 `.chip.sel`。
 * [mini] 对应 `.chip.mini`（h22，用在卡片脚注的标签上）。
 */
@Composable
internal fun HistoryChip(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    mini: Boolean = false,
    ghost: Boolean = false,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    val height = if (mini) 22.dp else 30.dp
    val horizontal = if (mini) 8.dp else 11.dp
    val fontSize = if (mini) HistoryFontSizes.tiny else HistoryFontSizes.meta
    val dotSize = if (mini) 4.5.dp else 5.dp
    // 选中 = 实心 accent 底 + 白字白点（淡紫底那套看不清）。
    val background = if (selected) theme.accentSolid else theme.glassFillTop
    val brush = if (selected) null else theme.glassFill
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(background))
            .border(
                width = 1.dp,
                // 选中：实心 accent 描边。
                color = if (selected) theme.accentSolid else theme.glassBorder,
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (mini) 4.dp else 5.dp),
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    // 选中时圆点也变 accent：否则绿/黄圆点压在淡紫底上会显脏。
                    .background(if (selected) androidx.compose.ui.graphics.Color.White else dotColor),
            )
        }
        Text(
            text = label,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            // 选中时文字也走 accent（原来还是正文色，看着像没选中）。
            color = when {
                selected -> androidx.compose.ui.graphics.Color.White
                selected -> theme.accent
                ghost -> theme.sub
                else -> theme.text
            },
            maxLines = 1,
        )
        // mini 的上下留白要跟着 h22 收
    }
}

/** `.chip.mini.ghost`：脚注里的来源（暂存 / 剪贴板 / 图片 / 取词）。 */
@Composable
internal fun HistorySourceChip(label: String, modifier: Modifier = Modifier) {
    HistoryChip(
        label = label,
        dotColor = null,
        selected = false,
        onClick = null,
        modifier = modifier.defaultMinSize(minHeight = 22.dp),
        mini = true,
        ghost = true,
    )
}
