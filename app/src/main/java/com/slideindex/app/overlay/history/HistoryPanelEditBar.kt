package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import com.slideindex.app.R
import com.slideindex.app.stash.StashTag
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 就地编辑条（设计稿 `.editbar`）：改正文、改标签、追加一段、标记完成、删除。
 *
 * 与设计稿的差异（**刻意**）：
 * - 设计稿挂在卡片旁边（`openEdit` 里按卡片的 `getBoundingClientRect` 定位），这里做成
 *   **面板底部的一条**：面板是 `LazyColumn` + 输入法抬升，跟着卡片定位在滚动/键盘下很容易跑偏。
 * - 设计稿把"追加"折叠进正文输入框（提示"光标已在末尾，可直接追加"），这里**单独给一个追加框
 *   —— 数据层本来就是分开的（`StashMetaRepository.appendText`），追加块也因此永远不覆盖原文。
 */
internal data class HistoryEditTarget(
    val entryId: String,
    val text: String,
    val tagNames: List<String>,
    val done: Boolean,
    val createdAtEpochMs: Long,
    /** 已设的提醒时间（null = 没设）。 */
    val reminderAtMs: Long? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Suppress("DEPRECATION") // BasicTextField 的 state 版重载在这个 foundation 版本里不存在，见输入条的同类注释
@Composable
internal fun HistoryPanelEditBar(
    target: HistoryEditTarget,
    availableTags: List<StashTag>,
    imeBottom: Dp,
    blurActive: Boolean,
    onSave: (text: String, tags: List<String>) -> Unit,
    onAppend: (String) -> Unit,
    onToggleDone: () -> Unit,
    onToggleReminder: () -> Unit,
    onDelete: () -> Unit,
    onVoiceError: (Int) -> Unit = {},
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val textFocusRequester = remember { FocusRequester() }
    var text by remember(target.entryId) {
        // 光标停在末尾：设计稿「光标已在末尾，可直接追加」。
        mutableStateOf(TextFieldValue(target.text, TextRange(target.text.length)))
    }
    var append by remember(target.entryId) { mutableStateOf("") }
    var tags by remember(target.entryId) { mutableStateOf(target.tagNames) }

    LaunchedEffect(target.entryId) {
        delay(EDIT_BAR_FOCUS_DELAY_MS)
        textFocusRequester.requestFocus()
    }

    val theme = historyTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = imeBottom)
            .onGloballyPositioned { onHeightChanged(with(density) { it.size.height.toDp() }) }
            .clip(
                // 现在是**屏幕居中的大浮窗**（不再是贴底抽屉）：四角都圆。
                RoundedCornerShape(28.dp),
            )
            // 设计稿的就地编辑条是 `.editbar.g` —— 同样用玻璃底。
            .background(theme.glassSolid)
            // 设计稿编辑条是 `padding: 14px 14px 12px`；这里是贴底抽屉，所以底部留出安全边。
            .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Text(
                text = formatHistoryRelativeTime(target.createdAtEpochMs),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.tiny),
                color = scheme.onSurfaceVariantSummary,
            )
            top.yukonga.miuix.kmp.basic.Text(
                text = stringResource(R.string.stash_edit_meta_hint),
                style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.tiny),
                color = scheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            // 语音：设计稿 `.editbar .meta .mic`，识别结果追加到正文（光标跟着到末尾）。
            HistoryVoiceMicButton(
                onResult = { recognized ->
                    val current = text.text
                    val merged = if (current.isBlank()) recognized else "$current $recognized"
                    text = TextFieldValue(merged, TextRange(merged.length))
                },
                onError = onVoiceError,
                // 设计稿 `.editbar .meta .mic { width:30px; height:30px }`，图标 16px。
                size = 34.dp,
                iconSize = 16.dp,
            )
            // 已设提醒时把时间摆出来（点击按钮可取消）。
            target.reminderAtMs?.let { at ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = formatReminderTime(at),
                        style = HistoryPanelTypography.meta(),
                        color = scheme.primary,
                    )
                }
            }
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 160.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceContainer)
                .border(width = 1.dp, color = scheme.dividerLine, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .focusRequester(textFocusRequester),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 21.6.sp,
                color = scheme.onSurface,
            ),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            availableTags.forEach { tag ->
                val selected = tag.name in tags
                HistoryEditChip(
                    label = tag.name,
                    dotColor = Color(tag.colorArgb),
                    selected = selected,
                    onClick = {
                        tags = if (selected) tags - tag.name else tags + tag.name
                    },
                )
            }
        }
        HistoryEditAppendRow(
            value = append,
            onValueChange = { append = it },
            onSubmit = {
                val trimmed = append.trim()
                if (trimmed.isNotEmpty()) {
                    append = ""
                    onAppend(trimmed)
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryEditActionButton(
                label = stringResource(R.string.stash_remind_toggle),
                icon = Icons.Outlined.Schedule,
                onClick = onToggleReminder,
                active = target.reminderAtMs != null,
                modifier = Modifier.weight(1f),
            )
            HistoryEditActionButton(
                label = stringResource(
                    if (target.done) R.string.stash_action_mark_undone else R.string.stash_action_mark_done,
                ),
                icon = Icons.Outlined.CheckCircle,
                onClick = onToggleDone,
                modifier = Modifier.weight(1f),
            )
            HistoryEditActionButton(
                label = stringResource(R.string.stash_action_delete),
                icon = Icons.Default.Delete,
                onClick = onDelete,
                danger = true,
                modifier = Modifier.weight(1f),
            )
            HistoryEditActionButton(
                label = stringResource(R.string.stash_edit_save),
                icon = Icons.Default.Check,
                onClick = { onSave(text.text.trim(), tags) },
                primary = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryEditChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            // 选中 = accent-soft 实底，未选中 = 玻璃渐变（Color 与 Brush 不能混在一个 if 里）。
            .then(
                if (selected) {
                    Modifier.background(theme.accentSoft)
                } else {
                    Modifier.background(theme.glassFill)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) theme.accent.copy(alpha = 0.55f) else theme.glassBorder,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(HistoryRadii.pill))
                .background(dotColor),
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = label,
            // `.chip { font-size: var(--f-meta) }`
            style = androidx.compose.ui.text.TextStyle(
                fontSize = HistoryFontSizes.meta,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = theme.text,
        )
    }
}

/** 「追加一句」：回车即追加，不覆盖原文（数据层是独立的追加列表）。 */
@Suppress("DEPRECATION") // 同上：state 版 BasicTextField 重载在这个 foundation 版本里不存在
@Composable
private fun HistoryEditAppendRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(width = 1.dp, color = scheme.dividerLine, shape = shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = scheme.onSurfaceVariantSummary,
            modifier = Modifier.size(16.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                lineHeight = 21.6.sp,
                color = scheme.onSurface,
            ),
            cursorBrush = SolidColor(scheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = stringResource(R.string.stash_edit_append_hint),
                            style = HistoryPanelTypography.content(),
                            color = scheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (value.isNotBlank()) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSubmit,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.stash_edit_append_hint),
                tint = if (value.isNotBlank()) scheme.primary else scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 设计稿 `.actbtn`：flex 1、高 38dp、圆角 12dp。 */
@Composable
internal fun HistoryEditActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    /** 已生效的状态（如"已设提醒"）：描边与文字用主题色。 */
    active: Boolean = false,
) {
    val theme = historyTheme()
    // `.actbtn { height:38px; border-radius: var(--r-sm)=12; border:1px solid var(--btn-bd);
    //            background: var(--btn-bg); font-size: var(--f-meta); gap:5px; icon 13px }`
    val shape = RoundedCornerShape(HistoryRadii.sm)
    val container = when {
        primary -> theme.accentSolid
        active -> theme.accentSoft
        else -> theme.btnBg
    }
    val content = when {
        primary -> Color.White
        danger -> theme.danger
        active -> theme.accent
        else -> theme.text
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(
                if (primary) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = if (active) theme.accent.copy(alpha = 0.55f) else theme.btnBd,
                        shape = shape,
                    )
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // `.actbtn { height: 38px; gap: 5px; svg 13px; font-size: var(--f-meta) }`
            .height(52.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(13.dp),
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = label,
            style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
            color = content,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
        )
    }
}

/** 与搜索框/输入条同一套抢焦点节奏。 */
private const val EDIT_BAR_FOCUS_DELAY_MS = 180L
