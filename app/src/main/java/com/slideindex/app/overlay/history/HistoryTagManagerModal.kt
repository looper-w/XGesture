package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.stash.StashTag
import com.slideindex.app.stash.StashTagEdits
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * 8 个预设标签色（§0.16.4 待办 2「新增（名字 + 8 个预设色）」）。
 *
 * 前 5 个就是 `StashMetaRepository.DEFAULT_TAGS` 的配色 —— 默认标签改色时能"改回原样"，
 * 后 3 个是补的：默认 5 色全是偏暖/偏冷的中饱和色，用户在粉紫之间换色时会觉得没得选。
 */
internal val HistoryTagPalette: List<Long> = listOf(
    0xFF5B8DF6,
    0xFFF0A93B,
    0xFF57B87A,
    0xFFC77DF0,
    0xFFE86E8C,
    0xFF4BC0C8,
    0xFF8A93A8,
    0xFFB0873C,
)

/**
 * 标签管理浮窗（§0.16.4 待办 2）。
 *
 * 壳子与输入条 / 编辑条**同一套**（96% 宽、最大 720dp、圆角 28、投影 18、内边距 26·26·26·22、
 * 顶部信息行 + 内容 + 底部整行主按钮）—— 用户在面板里看到的所有"居中浮窗"必须是同一个东西。
 *
 * 内容：标签列表（色点 + 名字 + 改名 / 改色 / 删除）+ 新增（名字 + 8 个预设色）。
 * **「待办」是关键字**（完成态 / `isTodo` / 把手 `pendingTodoCount` 全靠它）：改名与删除禁用并给提示，
 * 改色照旧可用（颜色只影响观感）。
 */
@Composable
internal fun HistoryTagManagerModal(
    open: Boolean,
    tags: List<StashTag>,
    imeBottom: Dp,
    onDismiss: () -> Unit,
    onAdd: (name: String, colorArgb: Long) -> Unit,
    onRename: (oldName: String, newName: String) -> Unit,
    onSetColor: (name: String, colorArgb: Long) -> Unit,
    onDelete: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    // 每次打开都从干净状态开始（关掉时把草稿丢掉，免得下次打开看见上次没提交的名字）。
    var newName by remember(open) { mutableStateOf("") }
    var newColor by remember(open) { mutableStateOf(HistoryTagPalette.first()) }
    var renaming by remember(open) { mutableStateOf<String?>(null) }
    var renameText by remember(open) { mutableStateOf("") }
    var colorPicking by remember(open) { mutableStateOf<String?>(null) }

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
                .background(theme.glassSolid)
                .border(1.dp, theme.glassBorder, shape)
                .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 22.dp),
        ) {
            // ---- 顶部信息行：标题 + 关闭 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stash_tag_manage_title),
                    style = TextStyle(fontSize = HistoryFontSizes.body, fontWeight = FontWeight.SemiBold),
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                HistoryHeaderCircleButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.panel_close),
                    onClick = onDismiss,
                )
            }

            // ---- 已有标签 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    // 标签多到一定程度就自己滚，不要把浮窗顶出屏幕。
                    .heightIn(max = 268.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                tags.forEach { tag ->
                    val protected = StashTagEdits.isProtected(tag.name)
                    TagRow(
                        tag = tag,
                        renaming = renaming == tag.name,
                        renameText = renameText,
                        onRenameTextChange = { renameText = it },
                        onRenameStart = {
                            colorPicking = null
                            renaming = tag.name
                            renameText = tag.name
                        },
                        onRenameCancel = { renaming = null },
                        onRenameConfirm = {
                            val next = renameText.trim()
                            renaming = null
                            if (next.isNotEmpty() && next != tag.name) onRename(tag.name, next)
                        },
                        onColorToggle = {
                            renaming = null
                            colorPicking = if (colorPicking == tag.name) null else tag.name
                        },
                        onDelete = { onDelete(tag.name) },
                        removable = !protected,
                        renameable = !protected,
                        protectedHint = stringResource(R.string.stash_tag_protected_hint),
                    )
                    if (colorPicking == tag.name) {
                        HistoryTagPaletteRow(
                            selected = tag.colorArgb,
                            onPick = { color ->
                                colorPicking = null
                                onSetColor(tag.name, color)
                            },
                            modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
                        )
                    }
                }
            }

            // ---- 新增 ----
            Text(
                text = stringResource(R.string.stash_tag_add),
                style = TextStyle(fontSize = HistoryFontSizes.meta),
                color = theme.sub,
                modifier = Modifier.padding(top = 14.dp),
            )
            TagNameField(
                value = newName,
                hint = stringResource(R.string.stash_tag_name_hint),
                onValueChange = { newName = it },
                onSubmit = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        onAdd(trimmed, newColor)
                        newName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            HistoryTagPaletteRow(
                selected = newColor,
                onPick = { newColor = it },
                modifier = Modifier.padding(top = 10.dp),
            )
            HistoryEditActionButton(
                label = stringResource(R.string.stash_tag_add),
                icon = Icons.Default.Check,
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        onAdd(trimmed, newColor)
                        newName = ""
                    }
                },
                primary = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/** 一行标签：色点 + 名字（或改名输入框）+ 改名 / 改色 / 删除。 */
@Composable
private fun TagRow(
    tag: StashTag,
    renaming: Boolean,
    renameText: String,
    onRenameTextChange: (String) -> Unit,
    onRenameStart: () -> Unit,
    onRenameCancel: () -> Unit,
    onRenameConfirm: () -> Unit,
    onColorToggle: () -> Unit,
    onDelete: () -> Unit,
    renameable: Boolean,
    removable: Boolean,
    protectedHint: String,
) {
    val theme = historyTheme()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(tag.colorArgb)),
            )
            if (renaming) {
                TagNameField(
                    value = renameText,
                    hint = stringResource(R.string.stash_tag_name_hint),
                    onValueChange = onRenameTextChange,
                    onSubmit = onRenameConfirm,
                    modifier = Modifier.weight(1f),
                )
                TagIconButton(
                    icon = Icons.Default.Check,
                    contentDescription = stringResource(R.string.stash_tag_rename),
                    enabled = renameText.isNotBlank(),
                    onClick = onRenameConfirm,
                )
                TagIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.panel_close),
                    enabled = true,
                    onClick = onRenameCancel,
                )
            } else {
                Text(
                    text = tag.name,
                    style = TextStyle(fontSize = HistoryFontSizes.body),
                    color = theme.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                TagIconButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.stash_tag_rename),
                    enabled = renameable,
                    onClick = onRenameStart,
                )
                TagIconButton(
                    icon = Icons.Outlined.Palette,
                    contentDescription = stringResource(R.string.stash_tag_color),
                    // 改色对「待办」也开放：颜色不参与关键字判定。
                    enabled = true,
                    onClick = onColorToggle,
                )
                TagIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.stash_tag_delete),
                    enabled = removable,
                    danger = true,
                    onClick = onDelete,
                )
            }
        }
        // 只读标签（「待办」）在被禁用时说明原因，否则用户会以为按钮坏了。
        if (!removable) {
            Text(
                text = protectedHint,
                style = TextStyle(fontSize = HistoryFontSizes.tiny),
                color = theme.sub,
                modifier = Modifier.padding(start = 19.dp, bottom = 4.dp),
            )
        }
    }
}

/** 8 个预设色的选色行；选中那枚加一圈 accent 描边。 */
@Composable
private fun HistoryTagPaletteRow(
    selected: Long,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryTagPalette.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) 24.dp else 20.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, theme.accentSolid, CircleShape)
                        } else {
                            Modifier.border(1.dp, theme.chipBorder, CircleShape)
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(color) },
            )
        }
    }
}

/** 药丸形单行输入框（与编辑条里的"追加"框同一套观感）。 */
@Suppress("DEPRECATION") // state 版 BasicTextField 重载在这个 foundation 版本里不存在，见输入条的同类注释
@Composable
private fun TagNameField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(theme.appElev)
            .border(1.dp, theme.cardBorder, shape)
            .padding(horizontal = 14.dp),
        textStyle = TextStyle(fontSize = HistoryFontSizes.body, color = theme.text),
        cursorBrush = SolidColor(theme.accentSolid),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        style = TextStyle(fontSize = HistoryFontSizes.body),
                        color = theme.sub,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 行内小图标按钮（34dp 命中区）。禁用时只是变淡，不做任何事。 */
@Composable
private fun TagIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val theme = historyTheme()
    val tint = when {
        !enabled -> theme.sub.copy(alpha = 0.38f)
        danger -> theme.danger
        else -> theme.text.copy(alpha = 0.86f)
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
