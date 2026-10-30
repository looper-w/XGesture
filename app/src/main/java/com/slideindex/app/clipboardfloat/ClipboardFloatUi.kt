package com.slideindex.app.clipboardfloat

import android.content.Context
import android.graphics.Bitmap
import android.icu.text.SimpleDateFormat
import android.view.View
import android.widget.Toast
import android.icu.util.Calendar
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.sp
import com.slideindex.app.settings.ClipboardFloatEntryHoldAction
import com.slideindex.app.settings.ClipboardFloatEntryLongPressAction
import com.slideindex.app.settings.ClipboardFloatListStyle
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardCleanupCategory
import com.slideindex.app.clipboard.ClipboardCleanupPreview
import com.slideindex.app.clipboard.ClipboardCleanupResult
import com.slideindex.app.clipboard.ClipboardCleanupScope
import com.slideindex.app.clipboard.ClipboardDragShareFallback
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.clipboard.ClipboardHtmlParser
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.displayTypeLabelKey
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.canBlurInOverlay
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.history.HistoryEntryDragHelper
import com.slideindex.app.overlay.history.HistoryPanelColors
import com.slideindex.app.overlay.history.recordHistoryCardSnapshot
import com.slideindex.app.overlay.history.rememberHistoryCardSnapshot
import com.slideindex.app.service.ClipboardFloatService
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Date
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Unpin

enum class ClipboardFloatDisplayMode {
    Chip,
    Expanded,
}

private val ClipboardFloatPanelCornerRadius =
    ClipboardFloatWindowMetrics.PANEL_CORNER_RADIUS_DP.dp
private val ClipboardFloatPanelShape = RoundedCornerShape(ClipboardFloatPanelCornerRadius)
private val ClipboardFloatChipCornerRadius = 16.dp
private val ClipboardFloatChipShape = RoundedCornerShape(ClipboardFloatChipCornerRadius)

/**
 * 剪贴板浮窗的「玻璃底」参数，经 [LocalClipboardFloatGlass] 下发给 chip / 大窗 / 条目卡。
 *
 * 只表达**最终能不能糊**：本项开关 × 半径 > 0 × 全局「悬浮窗模糊」开关 × 系统跨窗模糊能力，
 * 全部条件在 [rememberClipboardFloatGlass] 里一次算完。调用点不做二次判断 —— 否则
 * 「底色按能糊画成半透明、backdrop 却退化成实色」这类错配会立刻变成一片灰。
 */
private data class ClipboardFloatGlass(
    val active: Boolean = false,
    val radiusDp: Int = 0,
)

private val LocalClipboardFloatGlass = staticCompositionLocalOf { ClipboardFloatGlass() }

/** 与大窗同一套磨砂 tint（对齐收纳面板 `HistoryPanelScreen` 的取值）。 */
private val ClipboardFloatGlassTintDark = 0x661C1C1E
private val ClipboardFloatGlassTintLight = 0x66F5F5F7

/**
 * 玻璃态下面板底色压到多实。
 *
 * 这层**在**磨砂层之下，太实（比如直接沿用不透明的 `panelChrome()`，或收纳面板那档 0.72）
 * 会把 blur 整个盖掉，开关看起来就"没反应"；太透又会看不清文字。0.42 是这两者的中点。
 */
private const val ClipboardFloatGlassBaseAlpha = 0.42f

/**
 * 玻璃态下条目卡的底色透明度。
 *
 * 卡片本身不透明时，面板底糊得再好也会被一张张卡片盖住，用户只会看到卡片缝里那点模糊。
 */
private const val ClipboardFloatGlassCardAlpha = 0.62f

@Composable
private fun clipboardFloatCardColor(glassActive: Boolean, starred: Boolean): Color {
    val base = HistoryPanelColors.cardBackground(starred = false)
    val solid = if (glassActive) base.copy(alpha = ClipboardFloatGlassCardAlpha) else base
    if (!starred) return solid
    // 收藏卡：**不换底色，只把正常卡底朝主色混一点**（18%）。
    // 用 lerp 而不是"直接给 alpha 很低的 primary"，是为了让透明度和普通卡保持一致 ——
    // 玻璃态下收藏卡不会因为更透而透出背景，只是整卡偏暖；一列扫下去是"暖色卡"而不是"亮星星"。
    // 混色比例刻意低于多选高亮（0.14 的纯主色底），避免和"已勾选"混淆。
    return lerp(solid, MiuixTheme.colorScheme.primaryVariant, 0.18f).copy(alpha = solid.alpha)
}

/**
 * 读设置并做模糊决策。
 *
 * 走 `settingsRepository.settings` 而不是服务启动时的快照：设置页改完开关，已经开着的浮窗
 * 要立刻跟着变（服务侧那条快照只在建窗时读一次）。
 */
@Composable
private fun rememberClipboardFloatGlass(
    initialEnabled: Boolean,
    initialRadiusDp: Int,
): ClipboardFloatGlass {
    val context = LocalContext.current
    val settingsFlow = remember(context) {
        OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository?.settings
    }
    // 初值来自建窗前的快照（服务已经读过），不能从 false 起步：这扇窗是冷启动的，
    // 首帧没有 backdrop 就会先画出 0.42 半透明底，而窗口就位后完全静止、不会再有一帧
    // 把它纠正过来（见 ClipboardFloatService.blurEnabled 的注释）。
    var enabled by remember { mutableStateOf(initialEnabled) }
    var radiusDp by remember { mutableIntStateOf(initialRadiusDp) }
    LaunchedEffect(settingsFlow) {
        settingsFlow?.collect { settings ->
            enabled = settings.clipboardFloatBlurEnabled
            radiusDp = settings.clipboardFloatBlurRadiusDp
        }
    }
    // 系统那半边（总开关 + ROM 跨窗模糊）交给 OverlayBlurGate 的唯一入口，这里不问第二遍。
    val systemAllows = canBlurInOverlay()
    return if (enabled && radiusDp > 0 && systemAllows) {
        ClipboardFloatGlass(active = true, radiusDp = radiusDp)
    } else {
        ClipboardFloatGlass()
    }
}

@Composable
fun ClipboardFloatRoot(
    mode: ClipboardFloatDisplayMode,
    pinned: Boolean,
    panelAlpha: Float = 1.0f,
    /** 建窗快照里的玻璃底状态；首帧就要带上，见 [rememberClipboardFloatGlass]。 */
    blurEnabled: Boolean = false,
    blurRadiusDp: Int = 0,
    listController: ClipboardFloatListController,
    windowWidthDp: Int,
    listStyle: ClipboardFloatListStyle,
    /** true = 最新一条在最底部（倒序），false = 最新在顶部。 */
    newestAtBottom: Boolean = true,
    /** true = 类型栏放在面板最底部；false = 工具栏下方（默认）。 */
    typeBarAtBottom: Boolean = false,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    onOpenExpanded: () -> Unit,
    onTogglePin: () -> Unit,
    onAlphaChange: (Float) -> Unit = {},
    onOpenStashPanel: () -> Unit,
    onToggleListStyle: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onDragWindowStart: () -> Unit,
    onDragWindowEnd: () -> Unit,
    /** 右下角把手真正开始拖动时调用一次。 */
    onResizeStart: () -> Unit = {},
    /** 面板右下角的**目标位置**（窗口坐标 px）：严格跟随手指。 */
    onResizeWindow: (Float, Float) -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
    /** 多选删除：一次性删掉这批条目（service 负责清缩略图缓存 + 落库）。 */
    onDeleteEntries: (List<ClipboardEntry>) -> Unit,
    onUserInteraction: () -> Unit = {}
) {
    val glass = rememberClipboardFloatGlass(
        initialEnabled = blurEnabled,
        initialRadiusDp = blurRadiusDp,
    )
    OverlayAwareModuleTheme {
        // 「操作条目」那排按钮的展开状态（列表级）：只要按下点不在按钮上，就收起。
        val actionRow = remember { ClipboardFloatActionRowState() }
        // 上游新增的玻璃层容器：必须保留，否则卡片/chip 的模糊会整体失效。
        CompositionLocalProvider(LocalClipboardFloatGlass provides glass) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(actionRow) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    onUserInteraction()
                                    val pressPosition = event.changes.firstOrNull()?.position
                                    if (pressPosition != null && !actionRow.contains(pressPosition)) {
                                        actionRow.collapse()
                                    }
                                }
                            }
                        }
                    }
            ) {
                when (mode) {
                    ClipboardFloatDisplayMode.Chip -> ClipboardFloatChip(
                        onClick = onOpenExpanded,
                        onDragWindow = onDragWindow,
                        onDragWindowStart = onDragWindowStart,
                        onDragWindowEnd = onDragWindowEnd
                    )
                    ClipboardFloatDisplayMode.Expanded -> ClipboardFloatExpandedChrome(
                        pinned = pinned,
                        panelAlpha = panelAlpha,
                        listController = listController,
                        windowWidthDp = windowWidthDp,
                        listStyle = listStyle,
                        newestAtBottom = newestAtBottom,
                        typeBarAtBottom = typeBarAtBottom,
                        entryLongPressAction = entryLongPressAction,
                        entryHoldAction = entryHoldAction,
                        actionRow = actionRow,
                        onTogglePin = onTogglePin,
                        onAlphaChange = onAlphaChange,
                        onOpenStashPanel = onOpenStashPanel,
                        onToggleListStyle = onToggleListStyle,
                        onCollapse = onCollapse,
                        onClose = onClose,
                        onDragWindow = onDragWindow,
                        onDragWindowStart = onDragWindowStart,
                        onDragWindowEnd = onDragWindowEnd,
                        onResizeStart = onResizeStart,
                        onResizeWindow = onResizeWindow,
                        onSearchActiveChanged = onSearchActiveChanged,
                        onEntryClick = onEntryClick,
                        onEntryLongClick = onEntryLongClick,
                        onEntryDragStart = onEntryDragStart,
                        onEntryDragEnd = onEntryDragEnd,
                        onEntryAction = onEntryAction,
                        onDeleteEntries = onDeleteEntries,
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clipboardFloatDragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onDragStart() },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount.x, dragAmount.y)
            }
        )
    }
}

@Composable
private fun ClipboardFloatChip(
    onClick: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onDragWindowStart: () -> Unit,
    onDragWindowEnd: () -> Unit
) {
    val scheme = MiuixTheme.colorScheme
    val glass = LocalClipboardFloatGlass.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClipboardFloatWindowMetrics.CHIP_SHADOW_INSET_DP.dp)
    ) {
        Box(
            modifier = Modifier
                .size(ClipboardFloatWindowMetrics.CHIP_SIZE_DP.dp)
                .shadow(
                    ClipboardFloatWindowMetrics.CHIP_SHADOW_ELEVATION_DP.dp,
                    ClipboardFloatChipShape,
                    clip = false
                )
                .clip(ClipboardFloatChipShape)
                .background(
                    if (glass.active) {
                        scheme.surfaceContainer.copy(alpha = ClipboardFloatGlassBaseAlpha)
                    } else {
                        scheme.surfaceContainer
                    }
                )
                .border(0.5.dp, scheme.onSurface.copy(alpha = 0.12f), ClipboardFloatChipShape)
                .clipboardFloatDragHandle(onDragWindowStart, onDragWindow, onDragWindowEnd)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (glass.active) {
                // chip 只有 44dp：用大窗那档半径会糊成一块纯 tint，这里单独压到 16dp。
                ClipboardFloatGlassBackdrop(
                    cornerRadiusDp = ClipboardFloatChipCornerRadius.value.roundToInt(),
                    radiusDp = glass.radiusDp.coerceAtMost(
                        ClipboardFloatWindowMetrics.CHIP_BLUR_MAX_RADIUS_DP,
                    ),
                )
            }
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = stringResource(R.string.clipboard_float_open),
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 面板/大窗共用的磨砂底。
 *
 * 必须画在 `.background(...)` 之后、内容之前：它的 bounds 就是圆角面板本身，
 * 放到 clip 外面会漏出一圈方角模糊。
 */
@Composable
private fun BoxScope.ClipboardFloatGlassBackdrop(cornerRadiusDp: Int, radiusDp: Int) {
    val density = LocalDensity.current
    LocalFrostedGlassBackdrop(
        modifier = Modifier.matchParentSize(),
        cornerRadiusPx = with(density) { cornerRadiusDp.dp.toPx() },
        blurRadiusPx = with(density) { radiusDp.dp.toPx() }.roundToInt(),
        tintColor = if (isSystemInDarkTheme()) {
            ClipboardFloatGlassTintDark
        } else {
            ClipboardFloatGlassTintLight
        },
    )
}

@Composable
private fun ClipboardFloatExpandedChrome(
    pinned: Boolean,
    panelAlpha: Float,
    listController: ClipboardFloatListController,
    windowWidthDp: Int,
    listStyle: ClipboardFloatListStyle,
    newestAtBottom: Boolean,
    /** true = 类型栏贴在面板最底部；false = 紧贴工具栏下方（默认）。 */
    typeBarAtBottom: Boolean,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    actionRow: ClipboardFloatActionRowState,
    onTogglePin: () -> Unit,
    onAlphaChange: (Float) -> Unit,
    onOpenStashPanel: () -> Unit,
    onToggleListStyle: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onDragWindowStart: () -> Unit,
    onDragWindowEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResizeWindow: (Float, Float) -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
    onDeleteEntries: (List<ClipboardEntry>) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val glass = LocalClipboardFloatGlass.current
    val searchQuery by listController.searchQuery.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    var showAlphaSlider by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // 来源筛选 + 多选删除（选择态由标题栏「选择」按钮进入，避免和长按手势冲突）。
    val availableSources by listController.availableSources.collectAsState()
    val sourceFilter by listController.sourceFilter.collectAsState()
    // 类型栏的当前选择（默认「全部」）：切换时控制器会重新查库，不只是过滤已加载的几条。
    val historyFilter by listController.historyFilter.collectAsState()
    val onlyStarred by listController.onlyStarred.collectAsState()
    val entries by listController.filteredEntries.collectAsState()
    val selection = remember { ClipboardFloatSelectionState() }
    var showSourceFilter by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 「按规则清理」面板（长按删除按钮打开）。它作用于整个历史，与当前筛选/已选无关。
    var showCleanup by remember { mutableStateOf(false) }

    LaunchedEffect(searchExpanded) {
        onSearchActiveChanged(searchExpanded)
    }
    DisposableEffect(Unit) {
        onDispose { onSearchActiveChanged(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClipboardFloatWindowMetrics.PANEL_SHADOW_INSET_DP.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    ClipboardFloatWindowMetrics.PANEL_SHADOW_ELEVATION_DP.dp,
                    ClipboardFloatPanelShape,
                    clip = false
                )
                .clip(ClipboardFloatPanelShape)
                .background(
                    if (glass.active) {
                        // 这层在磨砂层之下，透明度决定"看得到多少模糊"：见 ClipboardFloatGlassBaseAlpha。
                        scheme.surfaceContainer.copy(alpha = ClipboardFloatGlassBaseAlpha)
                    } else {
                        HistoryPanelColors.panelChrome()
                    }
                )
                .border(0.5.dp, scheme.onSurface.copy(alpha = 0.10f), ClipboardFloatPanelShape)
        ) {
            if (glass.active) {
                ClipboardFloatGlassBackdrop(
                    cornerRadiusDp = ClipboardFloatWindowMetrics.PANEL_CORNER_RADIUS_DP,
                    radiusDp = glass.radiusDp,
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp)
                    .clipboardFloatDragHandle(onDragWindowStart, onDragWindow, onDragWindowEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTogglePin,
                    // 触摸区仍是 32dp（好点），但蓝色底只画在图标外一圈 24dp，
                    // 避免底色撑满 32dp 后压到相邻按钮、视觉上也超出面板。
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .then(
                                if (pinned) {
                                    Modifier
                                        .clip(CircleShape)
                                        .background(scheme.primary)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Pin,
                            contentDescription = stringResource(R.string.clipboard_float_keep_visible),
                            tint = if (pinned) scheme.onPrimary else scheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 透明度 / 打开侧边面板（主页）两颗按钮按需求隐藏（见 SHOW_FLOAT_HEADER_EXTRAS）。
                if (SHOW_FLOAT_HEADER_EXTRAS) {
                    IconButton(
                        onClick = { showAlphaSlider = !showAlphaSlider },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Tune,
                            contentDescription = stringResource(R.string.clipboard_float_opacity_title),
                            tint = if (showAlphaSlider || panelAlpha < 0.99f) {
                                scheme.primary
                            } else {
                                scheme.onSurface
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onOpenStashPanel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Home,
                            contentDescription = stringResource(R.string.clipboard_float_open_stash_panel),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 左侧一组：**状态类**开关（钉住 · 来源筛选 · 只看收藏）。
                // 标题「剪贴板」已去掉 —— 面板本身就是剪贴板，留着只是白占宽度；空出来的位置
                // 一半给按钮、一半给拖动区。
                IconButton(
                    onClick = { showSourceFilter = !showSourceFilter },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.clipboard_float_filter_source),
                        tint = if (showSourceFilter || sourceFilter != null) scheme.primary else scheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // 「只看收藏」不再占工具栏位置：它已经是类型栏里的「收藏」胶囊（常显、更好点）。
                // 左右两组之间的留白：把「状态类」与「操作类」分开，同时是一段拖动区
                // （整行都挂了 drag handle，按在空白处也能拖窗口）。
                Spacer(modifier = Modifier.weight(1f))
                // 右侧一组：**操作类**（多选 · 搜索 · 列表样式 · 关闭）。
                ClipboardFloatSelectModeButton(
                    active = selection.active,
                    tint = if (selection.active) scheme.primary else scheme.onSurface,
                    onClick = {
                        if (selection.active) {
                            selection.exit()
                            confirmDelete = false
                        } else {
                            selection.enter()
                            confirmDelete = false
                        }
                    },
                    // 长按直接打开"按规则清理"面板，不用先进入多选。
                    onLongClick = { showCleanup = true },
                )
                IconButton(
                    onClick = {
                        if (searchExpanded) {
                            if (searchQuery.isNotBlank()) {
                                listController.setSearchQuery("")
                            } else {
                                searchExpanded = false
                            }
                        } else {
                            searchExpanded = true
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.clipboard_search_hint),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onToggleListStyle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (listStyle == ClipboardFloatListStyle.SINGLE_LINE) {
                            Icons.Default.ViewAgenda
                        } else {
                            Icons.Default.ViewHeadline
                        },
                        contentDescription = stringResource(R.string.clipboard_float_style_toggle),
                        modifier = Modifier.size(18.dp)
                    )
                }
                // 收起（最小化）也一并隐藏。
                if (SHOW_FLOAT_HEADER_EXTRAS) {
                    IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Remove,
                            contentDescription = stringResource(R.string.clipboard_float_collapse),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Close,
                        contentDescription = stringResource(R.string.clipboard_float_close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // 类型栏：**常显**（不受搜索 / 来源筛选 / 多选状态影响），位置由设置决定 ——
            // 默认紧贴工具栏下方，开了「放在底部」就挪到面板最底部（离拇指更近）。
            // 只在「展开成列表」时存在；Chip 态（收起）整个面板都不渲染，所以这里不用额外判断。
            val typeBar: @Composable (Modifier) -> Unit = { barModifier ->
                ClipboardFloatTypeFilterRow(
                    selected = historyFilter,
                    onlyStarred = onlyStarred,
                    onSelect = listController::selectTypeBar,
                    modifier = barModifier,
                )
            }
            if (!typeBarAtBottom) {
                typeBar(Modifier)
            }
            AnimatedVisibility(
                visible = showAlphaSlider,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(scheme.surfaceContainerHigh.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(panelAlpha * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceSecondary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Slider(
                        value = panelAlpha,
                        onValueChange = onAlphaChange,
                        valueRange = 0.2f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = scheme.primary,
                            activeTrackColor = scheme.primary
                        )
                    )
                }
            }
            ClipboardFloatSearchFieldStrip(
                expanded = searchExpanded,
                query = searchQuery,
                onQueryChange = listController::setSearchQuery,
                focusRequester = searchFocusRequester,
                hintResId = R.string.clipboard_search_hint,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            AnimatedVisibility(
                visible = showSourceFilter,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ClipboardFloatSourceFilterRow(
                    packages = availableSources,
                    selected = sourceFilter,
                    onSelect = listController::setSourceFilter,
                )
            }
            AnimatedVisibility(
                visible = selection.active,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ClipboardFloatSelectionBar(
                    selectedCount = selection.ids.size,
                    totalCount = entries.size,
                    confirming = confirmDelete,
                    onSelectAll = { selection.replaceAll(entries.map { it.id }) },
                    onRequestDelete = { confirmDelete = true },
                    onConfirmDelete = {
                        val selected = entries.filter { it.id in selection.ids }
                        confirmDelete = false
                        selection.exit()
                        if (selected.isNotEmpty()) onDeleteEntries(selected)
                    },
                    onCancel = {
                        confirmDelete = false
                        selection.exit()
                    },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 玻璃态下这一层**不铺底色**：它画在磨砂层之上，铺了就把模糊盖住了，
                    // 只剩列表边缘一圈能看到糊（= 开关看起来没反应）。
                    .background(
                        if (glass.active) Color.Transparent else HistoryPanelColors.listBackground()
                    )
            ) {
                ClipboardFloatContentSection(
                    listController = listController,
                    windowWidthDp = windowWidthDp,
                    listStyle = listStyle,
                    newestAtBottom = newestAtBottom,
                    entryLongPressAction = entryLongPressAction,
                    entryHoldAction = entryHoldAction,
                    actionRow = actionRow,
                    selection = selection,
                    onEntryClick = onEntryClick,
                    onEntryLongClick = onEntryLongClick,
                    onEntryDragStart = onEntryDragStart,
                    onEntryDragEnd = onEntryDragEnd,
                    onEntryAction = onEntryAction,
                )
            }
            // 底部版：**在 Column 内部**排在列表之后（不能放到 Column 外面，否则会叠在面板顶部）。
            if (typeBarAtBottom) {
                // 上面压一条 0.5dp 细线，把胶囊条和列表分开（否则像浮在最后一条上）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(scheme.onSurface.copy(alpha = 0.08f))
                )
                // 右下角是缩放把手，右侧留一点空，免得最后一枚胶囊被把手抢走点击。
                typeBar(Modifier.padding(end = 18.dp))
            }
            }
            // 清理面板盖在整块浮窗之上（含标题栏），尺寸/圆角跟随浮窗本身。
            if (showCleanup) {
                ClipboardFloatCleanupPanel(
                    onDismiss = { showCleanup = false },
                    onPreview = listController::previewCleanup,
                    onPerform = listController::performCleanup,
                    onFinished = {
                        showCleanup = false
                        confirmDelete = false
                        selection.exit()
                    },
                )
            }
            ClipboardFloatResizeCornerHandle(
                cornerRadius = ClipboardFloatPanelCornerRadius,
                color = scheme.primary,
                onResizeStart = onResizeStart,
                onResizeWindow = onResizeWindow,
                // 长按把手 = 点左上角的钉住按钮（同一个回调，图标状态自动同步）。
                onLongPress = onTogglePin,
            )
        }
    }
}

@Composable
private fun BoxScope.ClipboardFloatResizeCornerHandle(
    cornerRadius: Dp,
    color: Color,
    /** 真正开始拖动时通知一次。 */
    onResizeStart: () -> Unit,
    /** 传**手指在窗口坐标里的位置**（px），作为面板右下角的目标位置（严格跟随）。 */
    onResizeWindow: (Float, Float) -> Unit,
    /** 长按把手：与左上角「钉住」按钮同一个动作（切换钉住状态）。 */
    onLongPress: () -> Unit,
) {
    val touchSize = (cornerRadius.value + 12f).coerceAtLeast(28f).dp
    // 把手在**窗口里的位置**。窗口缩放时把手会跟着右下角一起移动，而 `change.position` 是
    // 相对把手自身的局部坐标 —— 手指不动、把手在动，局部坐标也会变，直接拿它算累计位移会形成
    // "缩→把手左移→局部坐标变大→又放大→把手右移→…" 的反馈，表现为窗口闪烁。
    // 加上这个偏移换算成窗口坐标后，手指静止时坐标就恒定了。
    val nodeWindowOffset = remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .onGloballyPositioned { nodeWindowOffset.value = it.positionInWindow() }
            .size(touchSize)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    val slop = viewConfiguration.touchSlop
                    var moved = false
                    // 先等「长按成立」或「移动超过死区」，谁先到就走谁的分支：
                    // - 先动 → 进入拖拽调尺寸；
                    // - 到点 → **立刻**切换钉住（不等抬手），然后吞掉这次按压剩下的所有事件，
                    //   避免同一次按压既切了钉住又去调尺寸。
                    val longPressReached = try {
                        withTimeout(longPressTimeout) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if ((change.position - down.position).getDistance() > slop) {
                                    moved = true
                                    break
                                }
                            }
                        }
                        false
                    } catch (_: PointerEventTimeoutCancellationException) {
                        true
                    } catch (_: TimeoutCancellationException) {
                        true
                    }

                    if (longPressReached) {
                        // 按够时间就直接生效（窗口钉住状态 + 顶部按钮同时变），不需要等松手。
                        onLongPress()
                        // 之后的移动不再调尺寸，等手指抬起结束这次按压。
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                        return@awaitEachGesture
                    }

                    // 没到长按也没拖动（轻点 / 很快就抬手）：什么都不做。
                    if (!moved) return@awaitEachGesture

                    // 锚定式缩放：以"进入拖拽后的第一个位置"为原点，之后传累计位移。
                    // 这样缩到最小/最大后继续拖的溢出量不会被记下来，反向时手指必须先回到
                    // 原来的位置，窗口才开始变化 —— 边缘不会跑到手指前面。
                    // 注意：位移必须在**窗口坐标**里算（见上面的 nodeWindowOffset），
                    // 否则窗口一缩放，把手自身移动会让局部坐标漂移，形成闪烁。
                    // 严格跟随：把手指位置（窗口坐标）**直接当作面板右下角的目标位置**，
                    // 不做"相对抓取点"的偏移补偿，因此没有任何死区：
                    //   - 拖动中手指始终压在面板角落上（1:1）；
                    //   - 缩到最小/放到最大后，角落停在边界，手指必须先回到边界位置，
                    //     窗口才会继续变化（不会因为之前多划的那段而在反向时立刻变大）。
                    onResizeStart()
                    drag(down.id) { change ->
                        val fingerInWindow = change.position + nodeWindowOffset.value
                        change.consume()
                        onResizeWindow(fingerInWindow.x, fingerInWindow.y)
                    }
                }
            },
        contentAlignment = Alignment.BottomEnd
    ) {
        Canvas(modifier = Modifier.size(cornerRadius)) {
            val radius = size.minDimension
            val strokeWidth = 2.dp.toPx()
            val inset = strokeWidth / 2f
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(-radius + inset, -radius + inset),
                size = Size((radius - inset) * 2f, (radius - inset) * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun ClipboardFloatContentSection(
    listController: ClipboardFloatListController,
    windowWidthDp: Int,
    listStyle: ClipboardFloatListStyle,
    newestAtBottom: Boolean,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    actionRow: ClipboardFloatActionRowState,
    selection: ClipboardFloatSelectionState,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
) {
    val entries by listController.filteredEntries.collectAsState()
    val loading by listController.loading.collectAsState()
    val searchQuery by listController.searchQuery.collectAsState()
    val historyFilter by listController.historyFilter.collectAsState()
    // 「收藏」视图里每条都必然是收藏的，再整卡高亮纯属噪音（不言自明）—— 传给条目组件跳过着色。
    // 其它视图照旧：收藏条目仍靠暖色底 + 主色描边区分。
    val onlyStarred by listController.onlyStarred.collectAsState()
    val starredHighlight = !onlyStarred
    val isSearching = searchQuery.isNotBlank()
    val columnCount = ClipboardFloatWindowMetrics.columnCount(windowWidthDp)

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 倒序模式下是否停在"最新"一端（reverseLayout 下 index 0 = 最底部那条）。
    val atNewest by remember(newestAtBottom, listStyle) {
        derivedStateOf {
            if (!newestAtBottom) return@derivedStateOf true
            when (listStyle) {
                ClipboardFloatListStyle.CARD ->
                    gridState.firstVisibleItemIndex == 0 &&
                        gridState.firstVisibleItemScrollOffset <= 4

                ClipboardFloatListStyle.SINGLE_LINE ->
                    listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset <= 4
            }
        }
    }
    // 新条目到来时保持阅读位置：倒序下新条目插在 index 0（底部），用户若正看旧条目，
    // 绝对索引会被顶偏 —— 按"前面插入了 N 条"补偿回去。
    var prevFirstId by remember { mutableStateOf(entries.firstOrNull()?.id) }
    LaunchedEffect(entries, listStyle, newestAtBottom) {
        val oldFirstId = prevFirstId
        prevFirstId = entries.firstOrNull()?.id
        if (!newestAtBottom || oldFirstId == null || entries.isEmpty()) return@LaunchedEffect
        if (oldFirstId == entries.firstOrNull()?.id) return@LaunchedEffect
        val shift = entries.indexOfFirst { it.id == oldFirstId }
        if (shift <= 0) return@LaunchedEffect
        val maxIndex = entries.lastIndex
        when (listStyle) {
            ClipboardFloatListStyle.CARD -> {
                val index = gridState.firstVisibleItemIndex
                if (index > 0) gridState.scrollToItem((index + shift).coerceAtMost(maxIndex))
            }

            ClipboardFloatListStyle.SINGLE_LINE -> {
                val index = listState.firstVisibleItemIndex
                if (index > 0) listState.scrollToItem((index + shift).coerceAtMost(maxIndex))
            }
        }
    }

    if (loading && entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MiuixTheme.colorScheme.primary
            )
        }
        return
    }
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(
                    // 类型筛选下的空列表不能用「剪贴板暂无记录」——库里明明有记录，只是这一类没有。
                    if (isSearching || historyFilter != ClipboardHistoryFilter.All) {
                        R.string.clipboard_search_empty
                    } else {
                        R.string.clipboard_empty
                    }
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (listStyle) {
            ClipboardFloatListStyle.CARD -> {
                ClipboardFloatGrid(
                    state = gridState,
                    reverseLayout = newestAtBottom,
                    entries = entries,
                    windowWidthDp = windowWidthDp,
                    columnCount = columnCount,
                    starredHighlight = starredHighlight,
                    entryLongPressAction = entryLongPressAction,
                    entryHoldAction = entryHoldAction,
                    actionRow = actionRow,
                    selection = selection,
                    onEntryClick = onEntryClick,
                    onEntryLongClick = onEntryLongClick,
                    onEntryDragStart = onEntryDragStart,
                    onEntryDragEnd = onEntryDragEnd,
                    onEntryAction = onEntryAction,
                )
            }

            ClipboardFloatListStyle.SINGLE_LINE -> {
                ClipboardFloatSingleLineList(
                    state = listState,
                    newestAtBottom = newestAtBottom,
                    entries = entries,
                    windowWidthDp = windowWidthDp,
                    starredHighlight = starredHighlight,
                    entryLongPressAction = entryLongPressAction,
                    selection = selection,
                    onEntryClick = onEntryClick,
                    onEntryLongClick = onEntryLongClick,
                    onEntryDragStart = onEntryDragStart,
                    onEntryDragEnd = onEntryDragEnd,
                )
            }
        }
        // 「回到最新」气泡：只在倒序模式、且已滚离底部时出现（否则新条目会藏在屏幕外）。
        if (newestAtBottom && !atNewest) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .clickable {
                        scope.launch {
                            when (listStyle) {
                                ClipboardFloatListStyle.CARD -> gridState.animateScrollToItem(0)
                                ClipboardFloatListStyle.SINGLE_LINE -> listState.animateScrollToItem(0)
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.clipboard_float_jump_to_newest),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun ClipboardFloatSingleLineList(
    state: LazyListState,
    newestAtBottom: Boolean,
    entries: List<ClipboardEntry>,
    windowWidthDp: Int,
    /** false = 当前就在「收藏」视图：不再给每条都刷暖色底（全都是收藏，高亮没有信息量）。 */
    starredHighlight: Boolean,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    selection: ClipboardFloatSelectionState,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
) {
    LazyColumn(
        state = state,
        // 倒序：index 0 画在底部，最新一条正好落在拇指附近。
        reverseLayout = newestAtBottom,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            ClipboardFloatSingleLineRow(
                entry = entry,
                starredHighlight = starredHighlight,
                entryLongPressAction = entryLongPressAction,
                selectionActive = selection.active,
                selected = entry.id in selection.ids,
                onClick = {
                    if (selection.active) selection.toggle(entry.id) else onEntryClick(entry)
                },
                onWordTapLongClick = { if (!selection.active) onEntryLongClick(entry) },
                onEntryDragStart = onEntryDragStart,
                onEntryDragEnd = onEntryDragEnd,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardFloatSingleLineRow(
    entry: ClipboardEntry,
    /** false = 「收藏」视图：跳过收藏着色，避免整列都是暖色底。 */
    starredHighlight: Boolean,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onWordTapLongClick: () -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scheme = MiuixTheme.colorScheme
    val hasImage = entry.hasImageContent()
    val linkMeta = remember(entry.id, entry.type, entry.text, entry.uri) {
        if (hasImage) null else parseClipboardFloatLinkMeta(entry)
    }
    val rawText = if (hasImage) {
        val t = entry.text.trim()
        if (t.isNotEmpty() && !ClipboardHtmlParser.isImageSrc(t)) t else stringResource(R.string.clipboard_float_meta_image)
    } else {
        entry.text.trim().ifBlank { entry.uri.orEmpty() }
    }
    var expanded by remember(entry.id) { mutableStateOf(false) }
    // 同卡片样式：用真实溢出判定，避免"1 行放不下但字数没到 30"导致没有展开按钮。
    var textOverflow by remember(entry.id) { mutableStateOf(false) }
    val isLong = hasImage || textOverflow

    val density = LocalDensity.current
    val thumbHeightPx = with(density) { 60.dp.roundToPx() }
    // 长按拖拽：把**整张卡**当拖影。抓图是 suspend 的，所以要起协程。
    val dragSnapshot = rememberHistoryCardSnapshot()
    val dragScope = rememberCoroutineScope()
    // 单行样式没有"滚动中"的判断，只按动作类型决定是否录制（每帧离屏渲染很贵）。
    val snapshotEnabled = entryLongPressAction == ClipboardFloatEntryLongPressAction.DRAG_DROP
    val thumbnail = rememberClipboardFloatThumbnail(
        entry = entry,
        enabled = hasImage && expanded,
        cardWidthPx = with(density) { 200.dp.roundToPx() },
        thumbHeightPx = thumbHeightPx
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 整张卡录一份：长按拖拽时"把整张卡拿起来"（见 HistoryCardSnapshot）。
            .then(
                if (snapshotEnabled) Modifier.recordHistoryCardSnapshot(dragSnapshot) else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .clipboardFloatEntryGestures(
                entryLongPressAction = entryLongPressAction,
                onClick = onClick,
                onWordTapLongClick = onWordTapLongClick,
                onDragLongClick = {
                    // ⚠️ 先起协程：快照是 suspend 抓的（得先让这一帧画进 GraphicsLayer）。
                    dragScope.launch {
                        startClipboardFloatEntryDrag(
                            view = view,
                            context = context,
                            entry = entry,
                            thumbnail = thumbnail,
                            onDragStart = onEntryDragStart,
                            onDragEnd = onEntryDragEnd,
                            snapshot = dragSnapshot.capture(),
                        )
                    }
                }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            scheme.primary.copy(alpha = 0.14f)
        } else {
            // 收藏：整卡淡淡偏主色（见 clipboardFloatCardColor），取代原先那颗小星。
            clipboardFloatCardColor(
                glassActive = LocalClipboardFloatGlass.current.active,
                starred = entry.starred && starredHighlight,
            )
        },
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            when {
                selected -> scheme.primary
                // 收藏卡描边也走主色，但比多选那种细一档，只当"暖色卡的边"。
                entry.starred && starredHighlight -> scheme.primary.copy(alpha = 0.30f)
                else -> scheme.onSurface.copy(alpha = 0.05f)
            },
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (expanded) 6.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasImage) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (linkMeta != null) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = rawText.ifBlank { stringResource(R.string.clipboard_float_meta_image) },
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) textOverflow = it.hasVisualOverflow },
                    modifier = Modifier.weight(1f)
                )

                ClipboardFloatSourceBadge(entry)
                if (isLong) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) MiuixIcons.Medium.ExpandLess else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (expanded && thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = stringResource(R.string.clipboard_float_meta_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 80.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ClipboardFloatGrid(
    state: LazyGridState,
    reverseLayout: Boolean,
    entries: List<ClipboardEntry>,
    windowWidthDp: Int,
    columnCount: Int,
    /** false = 当前就在「收藏」视图：不再给每条都刷暖色底。 */
    starredHighlight: Boolean,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    actionRow: ClipboardFloatActionRowState,
    selection: ClipboardFloatSelectionState,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
) {
    // 滚动期间**不挂整卡录制**：那个 modifier 每帧都要离屏渲染一遍，是滚动掉帧的主因之一。
    // 停下后自动恢复，不影响长按拖拽。
    val isScrolling = state.isScrollInProgress
    // 滚动列表也是"其他交互" → 收起操作按钮。
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling -> if (scrolling) actionRow.collapse() }
    }
    LazyVerticalGrid(
        state = state,
        // 倒序：index 0 画在最底部，最新一条正好落在拇指附近。
        reverseLayout = reverseLayout,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            ClipboardFloatEntryCard(
                entry = entry,
                columnCount = columnCount,
                windowWidthDp = windowWidthDp,
                starredHighlight = starredHighlight,
                entryLongPressAction = entryLongPressAction,
                entryHoldAction = entryHoldAction,
                selectionActive = selection.active,
                selected = entry.id in selection.ids,
                actionRowVisible = actionRow.entryId == entry.id,
                recordCardSnapshot = !isScrolling,
                onActionRowBounds = { bounds -> actionRow.boundsInWindow = bounds },
                onClick = {
                    actionRow.collapse()
                    if (selection.active) selection.toggle(entry.id) else onEntryClick(entry)
                },
                onWordTapLongClick = {
                    if (!selection.active) {
                        actionRow.collapse()
                        onEntryLongClick(entry)
                    }
                },
                onStationaryLongPress = {
                    if (!selection.active &&
                        entryHoldAction == ClipboardFloatEntryHoldAction.OPERATE_ENTRY
                    ) {
                        actionRow.entryId = entry.id
                    }
                },
                onDragStartAction = { actionRow.collapse() },
                onEntryAction = { action -> onEntryAction(entry, action) },
                onEntryDragStart = onEntryDragStart,
                onEntryDragEnd = onEntryDragEnd,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardFloatEntryCard(
    entry: ClipboardEntry,
    columnCount: Int,
    windowWidthDp: Int,
    /** false = 「收藏」视图：跳过收藏着色（全都是收藏，高亮没有信息量）。 */
    starredHighlight: Boolean,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    /** 多选模式：禁用手势（单击 = 勾选），选中态由整卡高亮表示。 */
    selectionActive: Boolean,
    selected: Boolean,
    /** 「操作条目」那排按钮是否展开（由列表级状态决定）。 */
    actionRowVisible: Boolean,
    /**
     * 是否给这张卡挂"整卡离屏录制"（长按拖拽的拖影用）。
     *
     * 这个 modifier 会让卡片**每次重绘都先录进一个离屏层再合成**，滚动时是纯损耗；
     * 所以只在"按住拖动"模式 + 列表没有在滚动时才挂。
     */
    recordCardSnapshot: Boolean,
    /** 把这排按钮的窗口坐标回报给列表级状态（"点别处即收起"的命中判断用）。 */
    onActionRowBounds: (Rect) -> Unit,
    onClick: () -> Unit,
    onWordTapLongClick: () -> Unit,
    /** 原地长按（按住不动）：展开操作按钮。 */
    onStationaryLongPress: () -> Unit,
    /** 「按住拖动」真的开始拖动：先收起操作按钮。 */
    onDragStartAction: () -> Unit,
    onEntryAction: (ClipboardFloatEntryAction) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scheme = MiuixTheme.colorScheme
    val hasImage = entry.hasImageContent()
    val linkMeta = remember(entry.id, entry.type, entry.text, entry.uri) {
        if (hasImage) null else parseClipboardFloatLinkMeta(entry)
    }
    val bodyText = if (hasImage) {
        val t = entry.text.trim()
        if (t.isNotEmpty() && !ClipboardHtmlParser.isImageSrc(t)) t else ""
    } else {
        entry.text.trim().ifBlank { entry.uri.orEmpty() }
    }
    // 富文本（文字 + 图片）的正文：从**内容块**里取文字块，和侧边历史面板同一套解析。
    // 两点原因：① 以前 `hasImage` 分支里根本不画正文，图文条目只剩图、文字整段丢失；
    // ② 内容块已经滤掉了「截图文件名 / 图片 URI」这类纯图片条目的标签文字，不会把文件名当正文。
    val richBlocks = remember(
        entry.id, hasImage, entry.contentBlocks, entry.text, entry.htmlText,
        entry.imageFileName, entry.imageFileNames, entry.uri,
    ) { if (hasImage) entry.resolvedContentBlocks() else emptyList() }
    val richBodyText = remember(richBlocks) {
        richBlocks.filter { it.kind == ClipboardBlockKind.TEXT }
            .joinToString("\n") { it.text.trim() }
            .trim()
    }
    // 文档顺序：文字在图片之前（"一段说明 + 一张图"）时文字画在图上方，否则画在图下方。
    val richTextFirst = remember(richBlocks) {
        richBlocks.firstOrNull()?.kind == ClipboardBlockKind.TEXT
    }
    // 图片块数量：> 1 说明是多图富文本，折叠态只画首图，**必须**给展开入口（否则后几张永远看不到）。
    val richImageCount = remember(richBlocks) {
        richBlocks.count { it.kind == ClipboardBlockKind.IMAGE }
    }
    val previewLines = if (columnCount <= 1) 3 else 2
    // 展开 / 折叠（与单行极简条目同一套交互）：折叠时只看预览行，展开后可看全文 / 大图。
    var expanded by remember(entry.id) { mutableStateOf(false) }
    // 是否**真的被截断**：不再用字数阈值猜（窄窗会漏箭头、宽窗会给无效箭头），
    // 交给折叠态 Text 的 onTextLayout 判定；展开后保留上次结果，箭头不会自己消失。
    var textOverflow by remember(entry.id) { mutableStateOf(false) }
    val textMaxLines = if (expanded) Int.MAX_VALUE else previewLines
    // 正文**内容**的实测高度（px）。右侧「展开 / 折叠」要整列可点，而浮窗列表是可滚动的、高度约束无限，
    // 子项没法直接 `fillMaxHeight` —— 所以按内容高度把箭头的热区撑满。
    //
    // ⚠️ 必须测「正文 Column」而不是「外层 Row」：箭头也是 Row 的子项，若把行高（= max(内容, 箭头)）
    // 回灌给箭头，展开时行变高 → 箭头跟着变高 → 折叠后内容缩回去、箭头仍是旧高度把行撑住，
    // 高度只会增不会减，卡片就"锁死"成一大片空白。
    var bodyContentHeightPx by remember(entry.id) { mutableStateOf(0) }
    // 展开时的整图：**不裁剪**、按最长边缩放（与取词面板同一套取图逻辑）。
    // 注意：展开态只要能被"内容块"渲染（`richBlocks` 非空，绝大多数图文条目），就**不走**这里，
    // 免得为一张用不上的图多做一次 1024px 解码。
    var expandedImage by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id, expanded, hasImage, richBlocks.isNotEmpty()) {
        expandedImage = if (!expanded || !hasImage || richBlocks.isNotEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                ClipboardThumbnailCache.loadEntryThumbnailsForPreview(
                    context = context,
                    entry = entry,
                    maxSidePx = CARD_EXPANDED_IMAGE_MAX_SIDE_PX,
                ).firstOrNull()
            }
        }
    }
    // 原图尺寸（px）：折叠显示尺寸 / 折叠占位 / 展开尺寸 / "是否需要展开箭头" 都靠它算。
    var originalImageSize by remember(entry.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(entry.id, hasImage) {
        originalImageSize = if (!hasImage) {
            null
        } else {
            withContext(Dispatchers.IO) {
                ClipboardThumbnailCache.loadEntryOriginalSize(context, entry)
            }
        }
    }
    // 展开后的最大高度跟随屏幕（面板约占 55%），窄高图可以竖着展开看全。
    val expandedImageMaxDp = with(LocalConfiguration.current) {
        (screenHeightDp * 0.55f).coerceIn(220f, 420f).dp
    }
    val density = LocalDensity.current
    // 图片可显示宽 = 窗口宽 − 列表横向内边距 20 − 卡片横向内边距 20 − 右侧箭头 24（单列）。
    val imageBoxWidthDp = (windowWidthDp - 64).coerceAtLeast(1).toFloat()
    val imageBoxWidthPx = with(density) { imageBoxWidthDp.dp.roundToPx().coerceAtLeast(1) }
    // 折叠后的**实际**显示尺寸：整图等比缩进「可用宽 × 64dp」，且**不超过原尺寸**
    // （小图就按原样显示，绝不放大 —— 否则小图会被"放大到 64dp"，展开反而变小）。
    val collapsedImageSize: Pair<Float, Float> = remember(originalImageSize, imageBoxWidthDp, density) {
        val size = originalImageSize ?: return@remember imageBoxWidthDp to 64f
        val widthDp = (size.first / density.density).coerceAtLeast(1f)
        val heightDp = (size.second / density.density).coerceAtLeast(1f)
        val scale = minOf(1f, imageBoxWidthDp / widthDp, 64f / heightDp)
        (widthDp * scale) to (heightDp * scale)
    }
    // 展开后的实际显示尺寸：可用宽 × 展开高度上限，同样不超过原尺寸。
    val expandedImageSize: Pair<Float, Float> = remember(
        originalImageSize, imageBoxWidthDp, expandedImageMaxDp, density
    ) {
        val size = originalImageSize ?: return@remember imageBoxWidthDp to expandedImageMaxDp.value
        val widthDp = (size.first / density.density).coerceAtLeast(1f)
        val heightDp = (size.second / density.density).coerceAtLeast(1f)
        val scale = minOf(1f, imageBoxWidthDp / widthDp, expandedImageMaxDp.value / heightDp)
        (widthDp * scale) to (heightDp * scale)
    }
    // 小图折叠时已经是原尺寸，展开不会变大 → 不给箭头（避免"点了没反应"的死箭头）。
    // 原图尺寸读不到时保持旧行为（给箭头），不让可展开的图失去入口。
    val imageNeedsExpand = hasImage && (
        originalImageSize == null ||
            expandedImageSize.second > collapsedImageSize.second + 1f ||
            expandedImageSize.first > collapsedImageSize.first + 1f
        )
    // 多图（≥2 张）恒可展开：折叠态只显示首图，没有展开入口就等于后几张不存在。
    val expandable = imageNeedsExpand || textOverflow || richImageCount > 1
    val thumbHeightPx = with(density) { 64.dp.roundToPx() }
    // 折叠态的**多图轮播**（与侧边历史面板同一套交互：左右滑看大图 + 下方缩略图条切换）。
    // 只有多图才走这条路：单图仍然用原来那条单图管线，行为完全不变。
    var selectedImageIndex by remember(entry.id) { mutableStateOf(0) }
    val collapsedImageFileNames = remember(richBlocks) {
        richBlocks.filter { it.kind == ClipboardBlockKind.IMAGE }.map { it.fileName }
    }
    val pagerPages = rememberClipboardFloatPagerPages(
        entryId = entry.id,
        fileNames = collapsedImageFileNames,
        enabled = hasImage && !expanded && richImageCount > 1,
        maxWidthDp = imageBoxWidthDp,
        maxHeightDp = ClipboardFloatCollapsedImageHeightDp,
    )
    // 展开态走块渲染、折叠态走轮播时，那张"首图缩略图"都不会用到 —— 不加载，省一次解码。
    val thumbnailNeeded = hasImage && !(expanded && richBlocks.isNotEmpty()) &&
        !(richImageCount > 1 && !expanded)
    // 长按拖拽：把**整张卡**当拖影。抓图是 suspend 的，所以要起协程。
    val dragSnapshot = rememberHistoryCardSnapshot()
    val dragScope = rememberCoroutineScope()
    // 只有"按住拖动"模式才真的会用到拖影快照；其它模式挂上去只是每帧白录一遍。
    val snapshotEnabled = recordCardSnapshot &&
        entryLongPressAction == ClipboardFloatEntryLongPressAction.DRAG_DROP
    val thumbnail = rememberClipboardFloatThumbnail(
        entry = entry,
        enabled = thumbnailNeeded,
        cardWidthPx = imageBoxWidthPx,
        thumbHeightPx = thumbHeightPx,
        fitWhole = true,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 整张卡录一份：长按拖拽时"把整张卡拿起来"（见 HistoryCardSnapshot）。
            .then(
                if (snapshotEnabled) Modifier.recordHistoryCardSnapshot(dragSnapshot) else Modifier
            )
            .clipboardFloatCardGestures(
                dragAction = entryLongPressAction,
                onClick = onClick,
                onStationaryLongPress = {
                    // 「长按条目」= 原地按住不动 → 展开操作按钮（多选模式下不响应）。
                    if (!selectionActive &&
                        entryHoldAction == ClipboardFloatEntryHoldAction.OPERATE_ENTRY
                    ) {
                        onStationaryLongPress()
                    }
                },
                onDragAction = {
                    // 多选模式下拖动不响应，避免误触起拖。
                    if (!selectionActive) {
                        // 「按住拖动」：任何拖动都先收起操作按钮。
                        onDragStartAction()
                        when (entryLongPressAction) {
                            ClipboardFloatEntryLongPressAction.DRAG_DROP -> {
                                // ⚠️ 先起协程：快照是 suspend 抓的（得先让这一帧画进 GraphicsLayer）。
                                dragScope.launch {
                                    startClipboardFloatEntryDrag(
                                        view = view,
                                        context = context,
                                        entry = entry,
                                        thumbnail = thumbnail,
                                        onDragStart = onEntryDragStart,
                                        onDragEnd = onEntryDragEnd,
                                        snapshot = dragSnapshot.capture(),
                                    )
                                }
                            }

                            ClipboardFloatEntryLongPressAction.WORD_TAP -> onWordTapLongClick()
                            ClipboardFloatEntryLongPressAction.NONE -> Unit
                        }
                    }
                },
            ),
        shape = RoundedCornerShape(12.dp),
        // 多选：整卡高亮（不再用勾选框）—— 底色淡主色 + 描边加粗成主色。
        color = if (selected) {
            scheme.primary.copy(alpha = 0.14f)
        } else {
            // 收藏：整卡淡淡偏主色（见 clipboardFloatCardColor），取代原先那颗小星。
            clipboardFloatCardColor(
                glassActive = LocalClipboardFloatGlass.current.active,
                starred = entry.starred && starredHighlight,
            )
        },
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            when {
                selected -> scheme.primary
                // 收藏卡描边也走主色，但比多选那种细一档，只当"暖色卡的边"。
                entry.starred && starredHighlight -> scheme.primary.copy(alpha = 0.30f)
                else -> scheme.onSurface.copy(alpha = 0.06f)
            },
        )
    ) {
        // 三层：顶部（来源 + 时间）／正文（展开箭头只在这一层）／底部（长按调出的操作按钮排）。
        // 箭头只挤压正文 —— **既不影响顶部时间，也不影响底部那排按钮**。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 8.dp,
                    // 操作按钮排自己带底部留白，这里只留 4dp 间距。
                    bottom = if (actionRowVisible) 4.dp else 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 顶部：来源应用 + 剪贴时间（整条宽度，不被右侧箭头挤压）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 顶部左侧：来源应用（类型标识隐藏后，空位由来源徽标顶上）。
                ClipboardFloatSourceBadge(
                    entry = entry,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (SHOW_ENTRY_TYPE_LABEL) {
                    Text(
                        // 业务字段照常取（displayTypeLabelKey → 文本/图片/富文本/链接），只是默认不渲染。
                        text = clipboardFloatTypeLabel(entry.displayTypeLabelKey()),
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                // 已收藏不再在时间旁挂一颗小星（太跳）：改由**整卡底色**表达，
                // 见 clipboardFloatCardColor(starred = true)；这一行右侧只留时间。
                Text(
                    text = formatClipboardFloatRelativeTime(entry.createdAtEpochMs),
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }

            // 正文：展开/折叠箭头只在这一层占宽，只挤压正文内容。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { bodyContentHeightPx = it.size.height },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val fullImage = expandedImage
                    if (hasImage) {
                        if (expanded && richBlocks.isNotEmpty()) {
                            // 展开：按**内容块顺序**把文字与**每一张**图片都画出来 —— 多图富文本看到的
                            // 内容与侧边历史面板一致（折叠态仍只看首图，免得长图文把列表撑爆）。
                            richBlocks.forEach { block ->
                                when (block.kind) {
                                    ClipboardBlockKind.TEXT -> if (block.text.isNotBlank()) {
                                        Text(
                                            text = block.text,
                                            style = MiuixTheme.textStyles.body2,
                                            color = scheme.onSurface,
                                        )
                                    }

                                    ClipboardBlockKind.IMAGE -> ClipboardFloatRichImage(
                                        entryId = entry.id,
                                        fileName = block.fileName,
                                        maxWidthDp = imageBoxWidthDp,
                                        maxHeightDp = expandedImageMaxDp,
                                    )

                                    // 剪贴板条目不会产出语音 / 未知块；真遇到就跳过，别打断排版。
                                    else -> Unit
                                }
                            }
                        } else {
                            // 折叠态（以及没有内容块可渲染时的兜底）：首图 + 摘要文字。
                            // 图文条目：正文文字必须画出来（文档顺序里文字在前就先画文字）。
                            if (richBodyText.isNotEmpty() && richTextFirst) {
                                ClipboardFloatCardBodyText(
                                    text = richBodyText,
                                    expanded = expanded,
                                    maxLines = textMaxLines,
                                    onOverflow = { textOverflow = it },
                                )
                            }
                            if (pagerPages.isNotEmpty()) {
                                // 多图：折叠态就用轮播（大图左右滑 + 缩略图条切换），与侧边面板一致。
                                ClipboardFloatCollapsedPager(
                                    pages = pagerPages,
                                    selectedIndex = selectedImageIndex,
                                    onSelectedIndexChange = { selectedImageIndex = it },
                                    imageHeightDp = ClipboardFloatCollapsedImageHeightDp.dp,
                                )
                            } else if (expanded && fullImage != null) {
                                // 展开：按"原图 × min(1, 宽比, 高比)"显示 —— 完整、保持比例、不放大。
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        bitmap = fullImage.asImageBitmap(),
                                        contentDescription = stringResource(R.string.clipboard_float_meta_image),
                                        modifier = Modifier
                                            .size(expandedImageSize.first.dp, expandedImageSize.second.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            } else if (thumbnail != null) {
                                // 折叠：整图等比缩进「可用宽 × 64dp」，且**不超过原尺寸**（小图不放大）。
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        bitmap = thumbnail.asImageBitmap(),
                                        contentDescription = stringResource(R.string.clipboard_float_meta_image),
                                        modifier = Modifier
                                            .size(collapsedImageSize.first.dp, collapsedImageSize.second.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            } else if (originalImageSize != null) {
                                // 位图还在解码：先按折叠后的实际尺寸占位，保证卡片高度稳定。
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(collapsedImageSize.first.dp, collapsedImageSize.second.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(scheme.onSurface.copy(alpha = 0.04f))
                                    )
                                }
                            } else if (richBodyText.isEmpty()) {
                                // 图片彻底拿不到（文件被清理 / 外链没下载下来）且没有正文：给一行占位，
                                // 否则这条卡片只剩「来源 + 时间」，看起来就像"内容没显示出来"。
                                Text(
                                    text = stringResource(R.string.clipboard_float_meta_image),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = scheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                            }
                            if (richBodyText.isNotEmpty() && !richTextFirst) {
                                ClipboardFloatCardBodyText(
                                    text = richBodyText,
                                    expanded = expanded,
                                    maxLines = textMaxLines,
                                    onOverflow = { textOverflow = it },
                                )
                            }
                        }
                        val imageMeta = buildClipboardFloatImageMeta(entry)
                        if (SHOW_ENTRY_META_LABEL && imageMeta.isNotEmpty()) {
                            Text(
                                text = imageMeta,
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (linkMeta != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = linkMeta.host,
                                style = MiuixTheme.textStyles.body2,
                                color = scheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (linkMeta.path.isNotEmpty()) {
                            Text(
                                text = linkMeta.path,
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                                onTextLayout = { if (!expanded) textOverflow = it.hasVisualOverflow }
                            )
                        }
                    } else {
                        if (bodyText.isNotEmpty()) {
                            ClipboardFloatCardBodyText(
                                text = bodyText,
                                expanded = expanded,
                                maxLines = textMaxLines,
                                onOverflow = { textOverflow = it },
                            )
                        }
                        val metaText = buildClipboardFloatTextMeta(entry, bodyText)
                        if (SHOW_ENTRY_META_LABEL && metaText.isNotEmpty()) {
                            Text(
                                text = metaText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = scheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (expandable) {
                    // 整列热区：箭头这一整列（从正文顶部到底部）都能点，不再只圈住 24dp 的图标。
                    // 宽度仍是 24dp（不会挤压正文），高度取实测行高。
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .then(
                                if (bodyContentHeightPx > 0) {
                                    // 高度跟**正文内容**走（至少 24dp，正文只有一行时也够点）。
                                    Modifier.height(
                                        maxOf(with(density) { bodyContentHeightPx.toDp() }, 24.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = stringResource(
                                if (expanded) {
                                    R.string.clipboard_float_entry_collapse
                                } else {
                                    R.string.clipboard_float_entry_expand
                                }
                            ),
                            tint = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            // 「长按条目」→ 操作条目：卡片底部展开的一排操作按钮（**整卡宽度**）。
            // 这一排自己会 consume 按下的 down（见 ClipboardFloatEntryActionRow），
            // 所以点按钮不会顺带触发卡片的单击 / 拖动。
            AnimatedVisibility(
                visible = actionRowVisible,
                enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                ) {
                    ClipboardFloatEntryActionRow(
                        showSaveImage = hasImage,
                        starred = entry.starred,
                        onActionRowBounds = onActionRowBounds,
                        onAction = onEntryAction,
                    )
                }
            }
        }
    }
}

/** 折叠态轮播的一页：文件名 + 位图 + 按"不放大"算出的显示尺寸（dp）。 */
private class ClipboardFloatPagerPage(
    val fileName: String,
    val bitmap: Bitmap?,
    val widthDp: Float,
    val heightDp: Float,
)

/**
 * 加载折叠态多图轮播需要的位图：整图等比缩进「可用宽 × 64dp」，**不裁切、不放大**。
 *
 * 只在 [enabled]（多图 + 未展开）时才解码；[fileNames] 变化（条目内容变了）会重新加载。
 */
@Composable
private fun rememberClipboardFloatPagerPages(
    entryId: String,
    fileNames: List<String>,
    enabled: Boolean,
    maxWidthDp: Float,
    maxHeightDp: Float,
): List<ClipboardFloatPagerPage> {
    val context = LocalContext.current
    val density = LocalDensity.current
    var pages by remember(entryId, fileNames) {
        mutableStateOf<List<ClipboardFloatPagerPage>>(emptyList())
    }
    LaunchedEffect(entryId, enabled, fileNames, maxWidthDp, maxHeightDp) {
        if (!enabled || fileNames.isEmpty()) {
            pages = emptyList()
            return@LaunchedEffect
        }
        val widthPx = with(density) { maxWidthDp.dp.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeightDp.dp.roundToPx().coerceAtLeast(1) }
        pages = withContext(Dispatchers.IO) {
            fileNames.map { fileName ->
                val original = ClipboardThumbnailCache.loadBlockOriginalSize(context, fileName)
                val bitmap = ClipboardThumbnailCache.loadBlockThumbnailForCard(
                    context = context,
                    fileName = fileName,
                    targetWidthPx = widthPx,
                    maxVisibleHeightPx = heightPx,
                    fitWhole = true,
                )
                if (original == null) {
                    // 读不到原图尺寸：按容器给尺寸（与单图兜底一致）。
                    ClipboardFloatPagerPage(fileName, bitmap, maxWidthDp, maxHeightDp)
                } else {
                    val widthDp = (original.first / density.density).coerceAtLeast(1f)
                    val heightDp = (original.second / density.density).coerceAtLeast(1f)
                    val scale = minOf(1f, maxWidthDp / widthDp, maxHeightDp / heightDp)
                    ClipboardFloatPagerPage(fileName, bitmap, widthDp * scale, heightDp * scale)
                }
            }
        }
    }
    return pages
}

/**
 * 折叠态多图轮播（对齐侧边历史面板的交互）：上面是可左右滑的大图，下面是可点的缩略图条。
 *
 * 每张图各自按"完整显示、不放大"算尺寸，竖图 / 横图混排也不会互相牵制；当前页在缩略图条上高亮。
 */
@Composable
private fun ClipboardFloatCollapsedPager(
    pages: List<ClipboardFloatPagerPage>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    imageHeightDp: Dp,
) {
    if (pages.isEmpty()) return
    val scheme = MiuixTheme.colorScheme
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size },
    )
    LaunchedEffect(pagerState.settledPage) {
        onSelectedIndexChange(pagerState.settledPage.coerceIn(0, pages.lastIndex))
    }
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex.coerceIn(0, pages.lastIndex))
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeightDp),
        ) { index ->
            val page = pages.getOrNull(index)
            val bitmap = page?.bitmap
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (page != null && bitmap != null) {
                    // 图 + 右下角「当前/总数」角标：与侧边历史面板的图片轮播同一处、同一套写法。
                    // Box 尺寸钉在这张图的显示尺寸上，角标才会贴着图片右下角（而不是漂在留白里）。
                    Box(modifier = Modifier.size(page.widthDp.dp, page.heightDp.dp)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.clipboard_float_meta_image),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            text = "${index + 1}/${pages.size}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClipboardFloatPagerThumbGapDp.dp),
            ) {
                pages.forEachIndexed { index, page ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .size(ClipboardFloatPagerThumbSizeDp.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    scheme.primary
                                } else {
                                    scheme.onSurface.copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(6.dp),
                            )
                            // 点缩略图先切页：子节点先消费事件，不会顺带触发卡片的单击。
                            .clickable { onSelectedIndexChange(index) },
                    ) {
                        val thumb = page.bitmap
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 展开态的一张图片块（多图富文本按内容块逐张调用）。
 *
 * 尺寸 = 原图 × `min(1, 可用宽/原图宽, 展开高度上限/原图高)`：完整显示、保持比例、不放大。
 * 与卡片里单图展开是同一套算式，区别只是**每张图各算各的** —— 竖图和横图混排时不会互相牵制。
 */
@Composable
private fun ClipboardFloatRichImage(
    entryId: String,
    fileName: String,
    maxWidthDp: Float,
    maxHeightDp: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember(entryId, fileName) { mutableStateOf<Bitmap?>(null) }
    var originalSize by remember(entryId, fileName) { mutableStateOf<Pair<Int, Int>?>(null) }
    var loadFinished by remember(entryId, fileName) { mutableStateOf(false) }
    LaunchedEffect(entryId, fileName) {
        if (fileName.isBlank()) {
            loadFinished = true
            return@LaunchedEffect
        }
        originalSize = withContext(Dispatchers.IO) {
            ClipboardThumbnailCache.loadBlockOriginalSize(context, fileName)
        }
        bitmap = withContext(Dispatchers.IO) {
            ClipboardThumbnailCache.loadBlockThumbnail(context, fileName, CARD_EXPANDED_IMAGE_MAX_SIDE_PX)
        }
        loadFinished = true
    }
    val displaySize = remember(originalSize, maxWidthDp, maxHeightDp, density) {
        val size = originalSize ?: return@remember null
        val widthDp = (size.first / density.density).coerceAtLeast(1f)
        val heightDp = (size.second / density.density).coerceAtLeast(1f)
        val scale = minOf(1f, maxWidthDp / widthDp, maxHeightDp.value / heightDp)
        (widthDp * scale) to (heightDp * scale)
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        val size = displaySize
        if (loaded != null) {
            val imageModifier = if (size != null) {
                Modifier.size(size.first.dp, size.second.dp)
            } else {
                // 读不到原图尺寸（例如文件头损坏）：退回"按宽度铺满 + 高度上限"。
                Modifier.fillMaxWidth().heightIn(max = maxHeightDp)
            }
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = stringResource(R.string.clipboard_float_meta_image),
                modifier = imageModifier.clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else if (size != null) {
            // 原图尺寸已经到了、位图还在解码：先按最终尺寸占位，避免展开瞬间卡片高度先塌后涨。
            Box(
                modifier = Modifier
                    .size(size.first.dp, size.second.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            )
        } else if (loadFinished) {
            Text(
                text = stringResource(R.string.clipboard_image_unavailable),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 卡片样式的**正文文字**：纯文本条目与「文字 + 图片」的富文本条目共用。
 *
 * 折叠时按 [maxLines] 显示并以「…」收尾，展开时全部显示；是否需要「展开」箭头由这里回传的
 * **真实溢出**（`hasVisualOverflow`）决定，不用字数阈值猜。
 */
@Composable
private fun ClipboardFloatCardBodyText(
    text: String,
    expanded: Boolean,
    maxLines: Int,
    onOverflow: (Boolean) -> Unit,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) onOverflow(it.hasVisualOverflow) }
    )
}

/**
 * 顶部工具栏是否显示「透明度 / 打开侧边面板（主页） / 收起（最小化）」这三颗按钮。
 *
 * 2026-10-26 需求：这三颗先隐藏，只保留
 * 钉住 · 来源筛选 · 只看收藏 · 多选 · 搜索 · 列表样式 · 关闭。
 * 回调与滑杆代码都保留，改回 `true` 即可整体恢复。
 */
private const val SHOW_FLOAT_HEADER_EXTRAS = false

/**
 * 条目卡片里是否显示**类型标识**（文本 / 图片 / 富文本 / 链接 …）。
 *
 * 只是**不渲染**：`entry.displayTypeLabelKey()` 与 `clipboardFloatTypeLabel()` 都保留，
 * 后续做筛选 / 统计可以直接复用。要恢复显示把这里改回 `true` 即可。
 */
private const val SHOW_ENTRY_TYPE_LABEL = false

/**
 * 条目卡片里是否显示**元信息**（`X 字 · X 行`、图片的 `图片 · PNG`）。
 *
 * 只是**不渲染**：`buildClipboardFloatTextMeta()` / `buildClipboardFloatImageMeta()`
 * 继续保留并照常计算。要恢复显示改回 `true` 即可。
 */
private const val SHOW_ENTRY_META_LABEL = false

/** 展开后整图的解码上限（最长边 px）：≈2 倍卡片宽，足够清晰又不至于占太多内存。 */
private const val CARD_EXPANDED_IMAGE_MAX_SIDE_PX = 1024

/** 折叠态图片区的基准高度（dp）：单图与多图轮播共用，保证两种形态的卡片高度观感一致。 */
private const val ClipboardFloatCollapsedImageHeightDp = 64f

/** 折叠态多图轮播：下方缩略图条的边长（dp）与间距（dp）。 */
private const val ClipboardFloatPagerThumbSizeDp = 36f
private const val ClipboardFloatPagerThumbGapDp = 6f

/** 卡片样式「长按条目（原地不动）」与「按住拖动」之间的抖动死区。 */
private const val CARD_HOLD_DEAD_ZONE_DP = 12f

/**
 * 「操作条目」那排按钮的**列表级状态**。
 *
 * - [entryId]：当前展开到哪一条（null = 没展开）；
 * - [boundsInWindow]：这排按钮在窗口里的位置，用于"按下的点不在按钮上就收起"的命中判断。
 *
 * 收起规则（按需求）：长按出按钮后，**只有真的按到这排按钮才保留**；单击条目、拖动、
 * 滚动列表、点浮窗其它地方、长按另一条——任何其它交互都会让它收起。
 */
private class ClipboardFloatActionRowState {
    var entryId by mutableStateOf<String?>(null)
    var boundsInWindow by mutableStateOf<Rect?>(null)

    fun collapse() {
        entryId = null
    }

    fun contains(position: Offset): Boolean = boundsInWindow?.contains(position) == true
}

/**
 * 卡片样式条目的手势：把原来的"长按"拆成**原地长按**（操作条目）与**按住拖动**（原长按动作）。
 *
 * ```
 * 按下 ──未超死区就抬手────────────────▶ 单击条目（[onClick]）
 *   ├── 超过长按阈值且没动 ────────────▶ [onStationaryLongPress]（展开操作按钮）
 *   ├── 长按成立前移动 > 死区 ─────────▶ 什么都不做：让列表滚动接手
 *   └── 长按成立后移动 > 死区 ─────────▶ [onDragAction]（按住拖动；动作为"无"时只是收起按钮）
 * ```
 *
 * 单行列表样式不走这里（继续用 [clipboardFloatEntryGestures] 的 `combinedClickable`）。
 */
@Composable
private fun Modifier.clipboardFloatCardGestures(
    dragAction: ClipboardFloatEntryLongPressAction,
    onClick: () -> Unit,
    onStationaryLongPress: () -> Unit,
    onDragAction: () -> Unit,
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnStationary by rememberUpdatedState(onStationaryLongPress)
    val currentOnDrag by rememberUpdatedState(onDragAction)
    return pointerInput(dragAction) {
        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
        val deadZonePx = CARD_HOLD_DEAD_ZONE_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 这排操作按钮会先 consume 按下的 down —— 那种情况不算卡片手势。
            if (down.isConsumed) return@awaitEachGesture
            val pointerId = down.id
            var movedBeforeLongPress = false
            var liftedBeforeLongPress = false

            val longPressReached = try {
                withTimeout(longPressTimeoutMs) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null || !change.pressed) {
                            liftedBeforeLongPress = true
                            break
                        }
                        if ((change.position - down.position).getDistance() > deadZonePx) {
                            movedBeforeLongPress = true
                            break
                        }
                    }
                }
                false
            } catch (_: PointerEventTimeoutCancellationException) {
                true
            } catch (_: TimeoutCancellationException) {
                true
            }

            // 长按成立前就动了：交给列表滚动，不触发任何动作。
            if (movedBeforeLongPress) return@awaitEachGesture
            if (!longPressReached) {
                if (liftedBeforeLongPress) currentOnClick()
                return@awaitEachGesture
            }

            // 原地长按成立 → 展开操作按钮。
            currentOnStationary()

            // 长按之后继续跟：移动超过死区 = 「按住拖动」（并收起操作按钮）。
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed) break
                if ((change.position - down.position).getDistance() > deadZonePx) {
                    if (dragAction != ClipboardFloatEntryLongPressAction.NONE) {
                        change.consume()
                    }
                    currentOnDrag()
                    break
                }
            }
        }
    }
}

/**
 * 「操作条目」展开的一排按钮（复制 / 暂存 / 钉在屏幕 / 分享 / 保存图片 / 取词 / 删除）。
 *
 * ⚠️ 这排会 consume 按下的 down（见 [consumePressForParentGestures]），
 * 所以点按钮不会顺带触发卡片本体的单击 / 长按 / 拖动。
 */
@Composable
private fun ClipboardFloatEntryActionRow(
    /** 有图才给"保存图片"这一颗（与侧栏卡片一致）。 */
    showSaveImage: Boolean,
    /** 当前条目是否已收藏（决定星形图标与文案）。 */
    starred: Boolean,
    onActionRowBounds: (Rect) -> Unit,
    onAction: (ClipboardFloatEntryAction) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceContainer.copy(alpha = 0.55f))
            .consumePressForParentGestures()
            .onGloballyPositioned { coords -> onActionRowBounds(coords.boundsInWindow()) }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipboardFloatEntryActionIcon(Icons.Filled.ContentCopy, R.string.clipboard_history_float_copy) {
            onAction(ClipboardFloatEntryAction.COPY)
        }
        ClipboardFloatEntryActionIcon(
            icon = if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescriptionRes = if (starred) {
                R.string.clipboard_float_unfavorite
            } else {
                R.string.clipboard_float_favorite
            },
            // 已收藏时用主色，避免"星星明明点亮了却和别的图标一个颜色"。
            tint = if (starred) scheme.primary else null,
        ) {
            onAction(ClipboardFloatEntryAction.STAR)
        }
        ClipboardFloatEntryActionIcon(Icons.Outlined.Archive, R.string.float_ball_action_stash) {
            onAction(ClipboardFloatEntryAction.STASH)
        }
        ClipboardFloatEntryActionIcon(MiuixIcons.Medium.Pin, R.string.stash_action_pin) {
            onAction(ClipboardFloatEntryAction.PIN)
        }
        ClipboardFloatEntryActionIcon(Icons.Filled.Share, R.string.float_ball_action_share) {
            onAction(ClipboardFloatEntryAction.SHARE)
        }
        if (showSaveImage) {
            ClipboardFloatEntryActionIcon(Icons.Outlined.Save, R.string.clipboard_action_save_image) {
                onAction(ClipboardFloatEntryAction.SAVE_IMAGE)
            }
        }
        ClipboardFloatEntryActionIcon(Icons.Outlined.TextFields, R.string.stash_action_open_pick) {
            onAction(ClipboardFloatEntryAction.OPEN_PICK)
        }
        ClipboardFloatEntryActionIcon(
            icon = Icons.Filled.Delete,
            contentDescriptionRes = R.string.stash_action_delete,
            destructive = true,
        ) {
            onAction(ClipboardFloatEntryAction.DELETE)
        }
    }
}

/**
 * 让这一排按钮吃掉"按下"，父级卡片的手势就不会把这次按压当成单击 / 长按 / 拖动。
 *
 * 只 consume `pressed` 的 down（不碰抬手），所以子级按钮自己的点击判定照常；
 * 而卡片手势拿到的是已消费的 down，[clipboardFloatCardGestures] 会直接放弃。
 */
private fun Modifier.consumePressForParentGestures(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                if (change.pressed && !change.isConsumed) change.consume()
            }
        }
    }
}

@Composable
private fun ClipboardFloatEntryActionIcon(
    icon: ImageVector,
    contentDescriptionRes: Int,
    destructive: Boolean = false,
    /** 非空时覆盖默认色（收藏时用主色）。 */
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = tint ?: if (destructive) scheme.error else scheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * 标题栏的「选择」按钮。
 *
 * 单击 = 进入 / 退出多选；**长按 = 直接打开「按规则清理」面板**（不用先进多选）。
 * 长按入口放在这里而不是「删除」上：删除在多选条里，只有进了多选才看得到，
 * 而清理是针对整个历史的操作，任何时候都该能打开。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardFloatSelectModeButton(
    active: Boolean,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Done else Icons.Default.Checklist,
            contentDescription = stringResource(R.string.clipboard_float_select),
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 多选操作条：`已选 N 项 · 全选 · 删除 · 取消`。
 *
 * 删除走**两步确认**（点「删除」先把整条变成"确认删除 N 条？[取消][删除]"），
 * 因为批量删除不可撤销。
 */
@Composable
private fun ClipboardFloatSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    confirming: Boolean,
    onSelectAll: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (confirming) {
                stringResource(R.string.clipboard_float_delete_confirm, selectedCount)
            } else {
                stringResource(R.string.clipboard_float_selected_count, selectedCount)
            },
            style = MiuixTheme.textStyles.footnote2,
            color = if (confirming) scheme.error else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (confirming) {
            ClipboardFloatSelectionAction(stringResource(R.string.clipboard_float_cancel)) { onCancel() }
            ClipboardFloatSelectionAction(
                label = stringResource(R.string.clipboard_float_delete),
                destructive = true,
            ) { onConfirmDelete() }
        } else {
            ClipboardFloatSelectionAction(
                label = stringResource(R.string.clipboard_float_select_all),
                enabled = selectedCount < totalCount,
            ) { onSelectAll() }
            ClipboardFloatSelectionAction(
                label = stringResource(R.string.clipboard_float_delete),
                destructive = true,
                enabled = selectedCount > 0,
            ) { onRequestDelete() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardFloatSelectionAction(
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    /** 非空时长按也响应（「删除」用它打开按规则清理的面板）。 */
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Text(
        text = label,
        style = MiuixTheme.textStyles.body2,
        color = when {
            !enabled -> scheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
            destructive -> scheme.error
            else -> scheme.primary
        },
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongPress,
                    )
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 单行列表样式沿用 `combinedClickable` 的老手势（卡片样式已换成 [clipboardFloatCardGestures]）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clipboardFloatEntryGestures(
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    onClick: () -> Unit,
    onWordTapLongClick: () -> Unit,
    onDragLongClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return when (entryLongPressAction) {
        ClipboardFloatEntryLongPressAction.NONE -> combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        ClipboardFloatEntryLongPressAction.WORD_TAP -> combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onWordTapLongClick,
        )
        ClipboardFloatEntryLongPressAction.DRAG_DROP -> combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onDragLongClick,
        )
    }
}

private fun startClipboardFloatEntryDrag(
    view: View,
    context: Context,
    entry: ClipboardEntry,
    thumbnail: Bitmap?,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    /** "整张卡"的快照（[rememberHistoryCardSnapshot] + [recordHistoryCardSnapshot]）；null = 退回旧画法。 */
    snapshot: Bitmap? = null,
) {
    val clipData = ClipboardWriter.buildClipForEntry(context, entry) ?: run {
        Toast.makeText(context, R.string.history_drag_unsupported, Toast.LENGTH_SHORT).show()
        return
    }
    // 恢复 = 把浮窗放回来；关掉 = 恢复可见后按普通条目动作收起。
    val dragFlowSource = DragFlowSource(
        onRestore = onDragEnd,
        onDiscard = { ClipboardFloatService.discardAfterDragTransfer() },
    )
    val started = HistoryEntryDragHelper.startDrag(
        view = view,
        clipData = clipData,
        preview = HistoryEntryDragHelper.previewForClipboardEntry(
            entry = entry,
            thumbnails = listOfNotNull(thumbnail)
        ).copy(snapshot = snapshot),
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDropRejected = {
            if (ClipboardDragShareFallback.hasShareableContent(clipData) &&
                !ClipboardDragShareFallback.shareToForegroundHost(context, clipData)
            ) {
                Toast.makeText(context, R.string.history_drag_unsupported, Toast.LENGTH_SHORT).show()
            }
        },
        dragFlowSource = dragFlowSource,
    )
    if (!started) {
        if (!ClipboardDragShareFallback.shareToForegroundHost(context, clipData)) {
            Toast.makeText(context, R.string.history_drag_unsupported, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun rememberClipboardFloatThumbnail(
    entry: ClipboardEntry,
    enabled: Boolean,
    cardWidthPx: Int,
    thumbHeightPx: Int,
    /** true = 整图等比缩进目标框、**不裁切**（卡片样式用；单行样式仍走裁切条）。 */
    fitWhole: Boolean = false,
): Bitmap? {
    val context = LocalContext.current
    var thumbnail by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id, enabled, cardWidthPx, thumbHeightPx, fitWhole) {
        if (!enabled) {
            thumbnail = null
            return@LaunchedEffect
        }
        thumbnail = withContext(Dispatchers.IO) {
            ClipboardThumbnailCache.loadEntryThumbnailsForCard(
                context = context,
                entry = entry,
                targetWidthPx = cardWidthPx,
                maxVisibleHeightPx = thumbHeightPx,
                fitWhole = fitWhole,
            ).firstOrNull()
        }
    }
    return thumbnail
}

private data class ClipboardFloatLinkMeta(
    val host: String,
    val path: String
)

private fun parseClipboardFloatLinkMeta(entry: ClipboardEntry): ClipboardFloatLinkMeta? {
    if (!entry.isLinkEntry()) return null
    val raw = entry.uri?.takeIf { it.isNotBlank() } ?: entry.text.trim()
    if (raw.isBlank()) return null
    return runCatching {
        val uri = raw.toUri()
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val path = buildString {
            append(uri.encodedPath.orEmpty().ifBlank { "/" })
            uri.encodedQuery?.let { query ->
                if (query.isNotBlank()) append('?').append(query)
            }
        }
        ClipboardFloatLinkMeta(host = host, path = path)
    }.getOrNull()
}

private fun ClipboardEntry.isLinkEntry(): Boolean {
    if (type == ClipboardEntryType.URI) return true
    if (type == ClipboardEntryType.TEXT) {
        val trimmed = text.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }
    return false
}

@Composable
private fun clipboardFloatTypeLabel(type: ClipboardEntryType): String = when (type) {
    ClipboardEntryType.TEXT -> stringResource(R.string.clipboard_entry_type_text)
    ClipboardEntryType.URI -> stringResource(R.string.clipboard_entry_type_uri)
    ClipboardEntryType.INTENT -> stringResource(R.string.clipboard_entry_type_intent)
    ClipboardEntryType.HTML -> stringResource(R.string.clipboard_entry_type_html)
}

@Composable
private fun buildClipboardFloatImageMeta(entry: ClipboardEntry): String {
    val mime = entry.mimeType?.substringAfter('/')?.uppercase().orEmpty()
    return listOfNotNull(
        stringResource(R.string.clipboard_float_meta_image),
        mime.takeIf { it.isNotEmpty() }
    ).joinToString(" · ")
}

@Composable
private fun buildClipboardFloatTextMeta(entry: ClipboardEntry, bodyText: String): String {
    if (bodyText.isBlank()) return ""
    val chars = bodyText.length
    val lines = bodyText.lineSequence().count().coerceAtLeast(1)
    return listOf(
        pluralStringResource(R.plurals.clipboard_float_meta_chars, chars, chars),
        pluralStringResource(R.plurals.clipboard_float_meta_lines, lines, lines)
    ).joinToString(" · ")
}

@Composable
private fun formatClipboardFloatRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L -> stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L -> stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalConfiguration.current.locales[0]
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

private const val ClipboardFloatSearchFocusDelayMs = 120L

@Composable
private fun ClipboardFloatSearchFieldStrip(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    hintResId: Int = R.string.search_hint
) {
    val scheme = MiuixTheme.colorScheme
    val hint = stringResource(hintResId)
    val focusManager = LocalFocusManager.current

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(ClipboardFloatSearchFocusDelayMs)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    AnimatedVisibility(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        visible = expanded,
        enter = expandVertically(
            animationSpec = tween(200),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(
            animationSpec = tween(160),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(160))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = hint,
                tint = scheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = MiuixTheme.textStyles.body2.copy(color = scheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(scheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = hint,
                                style = MiuixTheme.textStyles.body2,
                                color = scheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 「按规则清理」面板（多选栏长按「删除」打开）。
 *
 * 只在浮窗**内部**覆盖绘制，不新开窗口：浮窗本身是 overlay 窗口，从它弹 Activity/Dialog 容易
 * BadToken，而这块面板也不需要活到浮窗之外。
 *
 * 交互约定：
 * - 类型多选；每类条数按当前「范围」现算（服务端同一套 SQL），切换范围时数字会变；
 * - 条数为 0 的类型置灰、不可勾选；
 * - 预览只是预估，实际删除数以执行结果为准（预览之后可能又有新记录写入）。
 */
@Composable
private fun ClipboardFloatCleanupPanel(
    onDismiss: () -> Unit,
    onPreview: suspend (
        Collection<ClipboardCleanupCategory>,
        ClipboardCleanupScope,
    ) -> ClipboardCleanupPreview,
    onPerform: suspend (
        Collection<ClipboardCleanupCategory>,
        ClipboardCleanupScope,
    ) -> ClipboardCleanupResult,
    onFinished: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(ClipboardCleanupCategory.entries.toSet()) }
    var cleanScope by remember { mutableStateOf(ClipboardCleanupScope.UNSTARRED) }
    var preview by remember { mutableStateOf<ClipboardCleanupPreview?>(null) }
    var running by remember { mutableStateOf(false) }

    // 预览**始终按全部五类**算：行上的数字不能因为"取消勾选"而变成 0 —— 否则行会跟着变灰，
    // 用户把最后一类取消后就再也勾不回来了（这是上一版的 bug）。
    LaunchedEffect(cleanScope) {
        preview = onPreview(ClipboardCleanupCategory.entries, cleanScope)
    }

    val counts = preview?.counts.orEmpty()
    val selectedTotal = selected.sumOf { counts[it] ?: 0 }
    val selectedBytes = preview?.bytesOf(selected) ?: 0L
    val canClean = selectedTotal > 0 && !running

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!running) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                // 吞掉卡片内的点击，避免穿透到遮罩把面板关掉。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.clipboard_float_cleanup_title),
                style = MiuixTheme.textStyles.subtitle,
                color = scheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.clipboard_float_cleanup_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.clipboard_float_cleanup_types),
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                ClipboardCleanupCategory.entries.forEach { category ->
                    val count = counts[category] ?: 0
                    // 勾选状态只由集合决定（与条数无关）：条数为 0 的类只是**不可点**，不是"未选中"。
                    val checked = category in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = count > 0) {
                                selected = if (checked) selected - category else selected + category
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (checked) scheme.primary else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (count > 0) {
                                        scheme.primary
                                    } else {
                                        scheme.onSurface.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = scheme.onPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(clipboardCleanupCategoryLabel(category)),
                            style = MiuixTheme.textStyles.body2,
                            color = if (count > 0) {
                                scheme.onSurface
                            } else {
                                scheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                            },
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.clipboard_float_cleanup_count, count),
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (count > 0) {
                                scheme.onSurfaceVariantSummary
                            } else {
                                scheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
                            },
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.clipboard_float_cleanup_scope),
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
                ClipboardCleanupScope.entries.forEach { option ->
                    val checked = option == cleanScope
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { cleanScope = option }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.dp,
                                    color = if (checked) {
                                        scheme.primary
                                    } else {
                                        scheme.onSurface.copy(alpha = 0.3f)
                                    },
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (checked) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(scheme.primary)
                                )
                            }
                        }
                        Text(
                            text = stringResource(clipboardCleanupScopeLabel(option)),
                            style = MiuixTheme.textStyles.body2,
                            color = scheme.onSurface,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.clipboard_float_cleanup_preview,
                    selectedTotal,
                    formatCleanupBytes(selectedBytes),
                ),
                style = MiuixTheme.textStyles.footnote2,
                color = scheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClipboardFloatSelectionAction(
                    label = stringResource(R.string.clipboard_float_cancel),
                ) { if (!running) onDismiss() }
                ClipboardFloatSelectionAction(
                    label = if (running) {
                        stringResource(R.string.clipboard_float_cleanup_running)
                    } else {
                        stringResource(R.string.clipboard_float_cleanup_action, selectedTotal)
                    },
                    destructive = true,
                    enabled = canClean,
                ) {
                    running = true
                    scope.launch {
                        val result = runCatching { onPerform(selected, cleanScope) }.getOrNull()
                        running = false
                        if (result == null) {
                            Toast.makeText(
                                context,
                                R.string.clipboard_float_cleanup_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.clipboard_float_cleanup_done,
                                    result.deletedCount,
                                    formatCleanupBytes(result.freedBytes),
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                            onFinished()
                        }
                    }
                }
            }
        }
    }
}

private fun clipboardCleanupCategoryLabel(category: ClipboardCleanupCategory): Int =
    when (category) {
        ClipboardCleanupCategory.TEXT -> R.string.clipboard_float_cleanup_category_text
        ClipboardCleanupCategory.LINK -> R.string.clipboard_float_cleanup_category_link
        ClipboardCleanupCategory.RICH_TEXT -> R.string.clipboard_float_cleanup_category_rich
        ClipboardCleanupCategory.IMAGE -> R.string.clipboard_float_cleanup_category_image
        ClipboardCleanupCategory.FILE -> R.string.clipboard_float_cleanup_category_file
    }

private fun clipboardCleanupScopeLabel(scope: ClipboardCleanupScope): Int =
    when (scope) {
        ClipboardCleanupScope.UNSTARRED -> R.string.clipboard_float_cleanup_scope_unstarred
        ClipboardCleanupScope.STARRED -> R.string.clipboard_float_cleanup_scope_starred
        ClipboardCleanupScope.ALL -> R.string.clipboard_float_cleanup_scope_all
    }

/** 字节数 → 人类可读（清理面板的"预计释放"，只要求量级直观）。 */
private fun formatCleanupBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}
