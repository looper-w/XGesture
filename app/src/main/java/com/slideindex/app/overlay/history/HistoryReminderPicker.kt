package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.Text

/**
 * 提醒时间选择浮窗（§0.16.9）。
 *
 * 之前「提醒」只有开/关：设 = 硬编码的"明天 09:00"、再点 = 清掉，**用户改不了时间**（用户实测反馈）。
 * 现在编辑条的「提醒」和加号弹窗的 ⏰ 胶囊都打开这块浮窗。
 *
 * 壳子与其它浮窗**同一套**（96% 宽 / 最大 720dp / 圆角 28 / 投影 18 / 内边距 26·26·26·22），
 * 且卡片本体带 [historyConsumeTaps] —— 否则点在卡片空白处会穿到下面那层"点空白关闭"的遮罩上。
 *
 * ⚠️ **仍缺"任意日期/时间"**：这里给的是"从此刻起 N 分钟/N 小时 + 明天 09:00"这类锚点。
 * overlay 窗里塞 M3 的 DatePicker/TimePicker（约 400dp 高）在 320dp 宽的面板里会溢出，
 * 还要处理时区与"选到过去时间"的校验 —— 单独一轮再做（见计划遗留）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryReminderPickerModal(
    open: Boolean,
    /** 当前已设的提醒（null = 没设）；用来标出选中项并决定要不要显示「清除」。 */
    currentAtMs: Long?,
    imeBottom: Dp,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    // "从现在起 N 分钟"这类选项必须在**打开那一刻**算好，否则浮窗开着不动，值会一直往后漂。
    val openedAtMs = remember(open) { System.currentTimeMillis() }
    val options = remember(open) {
        listOf(
            HistoryReminderOption(minutes = 5, atMs = openedAtMs + 5 * 60_000L),
            HistoryReminderOption(minutes = 15, atMs = openedAtMs + 15 * 60_000L),
            HistoryReminderOption(minutes = 30, atMs = openedAtMs + 30 * 60_000L),
            HistoryReminderOption(hours = 1, atMs = openedAtMs + 60 * 60_000L),
            HistoryReminderOption(hours = 3, atMs = openedAtMs + 3 * 60 * 60_000L),
            HistoryReminderOption(atMs = historyDefaultReminderAt(), isTomorrowMorning = true),
        )
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
                .historyConsumeTaps()
                .background(theme.glassSolid)
                .border(1.dp, theme.glassBorder, shape)
                .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stash_remind_toggle),
                    style = TextStyle(fontSize = HistoryFontSizes.body),
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                HistoryHeaderCircleButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.panel_close),
                    onClick = onDismiss,
                )
            }
            currentAtMs?.let { at ->
                Text(
                    text = stringResource(R.string.stash_remind_current, formatReminderTime(at)),
                    style = TextStyle(fontSize = HistoryFontSizes.tiny),
                    color = theme.sub,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { option ->
                    HistoryChip(
                        label = option.label(),
                        dotColor = null,
                        // 只把"同一档锚点"标成选中：分钟/小时这种相对值不参与回选（每次打开都新算）。
                        selected = option.isTomorrowMorning && currentAtMs == option.atMs,
                        onClick = { onPick(option.atMs) },
                    )
                }
            }
            if (currentAtMs != null) {
                HistoryEditActionButton(
                    label = stringResource(R.string.stash_remind_clear),
                    icon = Icons.Default.Delete,
                    onClick = { onPick(null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
        }
    }
}

/** 选择器里的一档：要么是"从打开那一刻起 N 分钟/小时"，要么是"明天 09:00"这个固定锚点。 */
private data class HistoryReminderOption(
    val atMs: Long,
    val minutes: Int = 0,
    val hours: Int = 0,
    val isTomorrowMorning: Boolean = false,
) {
    @Composable
    fun label(): String = when {
        isTomorrowMorning -> stringResource(R.string.stash_remind_tomorrow)
        minutes > 0 -> stringResource(R.string.stash_remind_in_minutes, minutes)
        else -> stringResource(R.string.stash_remind_in_hours, hours)
    }
}
