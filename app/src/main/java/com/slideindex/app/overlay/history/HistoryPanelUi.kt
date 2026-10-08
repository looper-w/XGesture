package com.slideindex.app.overlay.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.stash.StashMetaRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.TextStyle

/** 顶栏工具图标：与 [MiuixExpandableSearchIconAction] 同尺寸、同色。 */
@Composable
internal fun HistoryPanelToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/** 对齐设计稿：正文 `--f-sm` 12.5px、行高 1.6；元信息 `--f-tiny` 10.5px。 */
internal object HistoryPanelTypography {
    @Composable
    fun content(): TextStyle = MiuixTheme.textStyles.body2.copy(
        fontSize = 13.sp,
        lineHeight = 21.sp,
    )

    @Composable
    fun meta(): TextStyle = MiuixTheme.textStyles.footnote2

    @Composable
    fun hint(): TextStyle = MiuixTheme.textStyles.footnote1
}

/** 收纳面板列表滚动：MIUI 回弹 + 触底触感。 */
internal fun Modifier.historyPanelListScrollEffects(): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical(nestedScrollToParent = false)

/** 列表参与顶栏毛玻璃采样。 */
internal fun Modifier.historyPanelListBackdrop(backdrop: LayerBackdrop?): Modifier =
    historyPanelListScrollEffects().then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)

/**
 * 列表尾部留给右下角 FAB 的空白（只有闪念页签有 FAB）。
 *
 * 54dp（FAB 本体）+ 32dp（它现在的底边偏移）+ 10dp 余量 = 96dp —— 之前是 64dp，
 * 那是 FAB 还贴着屏幕底边时定的，结果最后一张卡的 ⋮ 会被 FAB 压住。
 */
internal val HistoryListFooterPadding = 96.dp

/**
 * （已废弃）面板宽度 = 可用宽度 × 78%。
 *
 * ⚠️ **现在不需要它了**：面板窗本身就已经是屏宽的 78%
 * （`OverlayPanelLayoutParams.stashClipboardSidePanel`，§0.16.3 的改造），
 * 面板内容直接铺满窗口即可 —— 再乘一次会缩成 61% ✗。
 * 保留这个函数和比例常量只是为了让 `SIDE_PANEL_WIDTH_FRACTION` 有对照，**不要再拿去算宽度**。
 */
internal fun panelWidthOf(available: Dp): Dp = available * PANEL_WIDTH_FRACTION

/** 设计稿里侧栏占屏宽的比例。 */
internal const val PANEL_WIDTH_FRACTION = 0.78f

/**
 * 跟手拉出时"拉多远算拉满" = 面板宽度（px）。
 *
 * 面板侧用的是布局约束（见上），但**把手窗只有 48dp 宽**，它读不到屏幕宽度，
 * 所以把手侧用窗口管理器的**当前窗口度量**（`currentWindowMetrics`，随旋转更新；
 * `resources.displayMetrics` 在 overlay 里可能是陈旧的旋转前尺寸）。
 */
internal fun historyPanelRevealDistancePx(context: android.content.Context): Float =
    // 设计稿 `revealStream(dx) { const p = clamp(-dx / 130) }`：**手指横向拖 130px 就拉满**，
    // 和面板宽度无关。我原先按"面板宽度(842px)"算，于是要拖满一屏才到位、手感完全不对。
    (PANEL_REVEAL_DRAG_DP * context.resources.displayMetrics.density)

/** 设计稿里"拉满"所需的手指位移（`-dx / 130`）。 */
internal const val PANEL_REVEAL_DRAG_DP = 130

/** 设计稿里侧栏的圆角（`--r-2xl` = 26dp）。 */
internal val HistoryPanelCornerRadius = 26.dp

@Composable
internal fun historyClipboardCardPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 120.dp.roundToPx() }
}

@Composable
internal fun historyPreviewWidthPx(): Int {
    val density = LocalDensity.current
    // 预览只用来给图片解码降采样。面板 = **窗口宽 × 78%**（窗口是满屏的，见 `OverlayPanelLayoutParams`）。
    // `containerSize` 在真机上可能返回旋转过的尺寸，取 min(w,h) 兜底；上限 960px。
    val windowWidth = LocalWindowInfo.current.containerSize.let { size ->
        if (size.width <= 0) size.height else minOf(size.width, size.height)
    }
    return with(density) {
        (with(density) { windowWidth.toDp() } * PANEL_WIDTH_FRACTION - 24.dp)
            .roundToPx()
            .coerceAtMost(960)
    }
}

@Composable
internal fun historyStashPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 150.dp.roundToPx() }
}

@Composable
internal fun historyExpandedImageMaxSidePx(): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height
    return with(density) {
        maxOf(
            historyPreviewWidthPx(),
            (screenHeightPx * 0.75f).toInt(),
        ).coerceAtMost(2048)
    }
}

@Composable
internal fun formatHistoryRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L -> stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L -> stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalLocale.current.platformLocale
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMs }
            val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                if (locale.language == "zh") "M月d日" else "MMM d"
            } else {
                "yyyy/M/d"
            }
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
}

/**
 * 提醒时间的展示（设计稿 `.item .remind`）：`今天 21:30` / `明天 09:00` / `3月5日 09:00`。
 *
 * 与 [formatHistoryRelativeTime] 不同：提醒是**未来**时间，所以按"今天/明天/日期"说。
 */
@Composable
internal fun formatReminderTime(epochMs: Long): String {
    val locale = LocalLocale.current.platformLocale
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = epochMs }
    val time = SimpleDateFormat("HH:mm", locale).format(Date(epochMs))
    val dayLabel = when {
        sameDay(now, target) -> stringResource(R.string.stash_group_today)
        sameDay(now, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }) -> {
            stringResource(R.string.stash_remind_tomorrow)
        }
        else -> {
            val pattern = if (locale.language == "zh") "M月d日" else "MMM d"
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
    return "$dayLabel $time"
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/**
 * 默认提醒时间：**明天 09:00**。
 *
 * 设计稿里「提醒」按钮就是这个行为（`s.remind = s.remind ? null : '明天 09:00'`）——
 * 真正的选择器是后续的事，先把"能设、看得见、到点会响"这条链打通。
 */
internal fun historyDefaultReminderAt(): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * 卡片底部那枚「来源」chip 的文案（设计稿 `.foot` 里的 `from`）。
 *
 * 认不出来的来源键返回 null = 不显示 chip。数据层刻意把来源存成**字符串键**，
 * 就是为了让"新版本写了我们还不知道的来源"最多少显示一枚 chip，而不是解码整个文件失败。
 */
@StringRes
internal fun stashSourceLabelRes(sourceKey: String?): Int? = when (sourceKey) {
    StashMetaRepository.SOURCE_CLIPBOARD -> R.string.stash_source_clipboard
    StashMetaRepository.SOURCE_PICK -> R.string.stash_source_pick
    StashMetaRepository.SOURCE_IMAGE -> R.string.stash_source_image
    else -> null
}
