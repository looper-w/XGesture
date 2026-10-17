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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.slideindex.app.clipboard.ClipboardDragShareFallback
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardHtmlParser
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.displayTypeLabelKey
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.canBlurInOverlay
import com.slideindex.app.overlay.history.HistoryEntryDragHelper
import com.slideindex.app.overlay.history.HistoryPanelColors
import com.slideindex.app.overlay.history.recordHistoryCardSnapshot
import com.slideindex.app.overlay.history.rememberHistoryCardSnapshot
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
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
private fun clipboardFloatCardColor(glassActive: Boolean): Color {
    val base = HistoryPanelColors.cardBackground(starred = false)
    return if (glassActive) base.copy(alpha = ClipboardFloatGlassCardAlpha) else base
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
    onResizeWindow: (Float, Float) -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
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
                        onResizeWindow = onResizeWindow,
                        onSearchActiveChanged = onSearchActiveChanged,
                        onEntryClick = onEntryClick,
                        onEntryLongClick = onEntryLongClick,
                        onEntryDragStart = onEntryDragStart,
                        onEntryDragEnd = onEntryDragEnd,
                        onEntryAction = onEntryAction,
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
    onResizeWindow: (Float, Float) -> Unit,
    onSearchActiveChanged: (Boolean) -> Unit,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val glass = LocalClipboardFloatGlass.current
    val searchQuery by listController.searchQuery.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    var showAlphaSlider by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

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
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        // 已钉住时换成「取消钉住」（Unpin），点一下就知道能取消；
                        // 之前只换粗细（Medium/Regular）差别太细，看着像没变。
                        imageVector = if (pinned) MiuixIcons.Medium.Unpin else MiuixIcons.Medium.Pin,
                        contentDescription = stringResource(R.string.clipboard_float_keep_visible),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { showAlphaSlider = !showAlphaSlider },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Tune,
                        contentDescription = stringResource(R.string.clipboard_float_opacity_title),
                        tint = if (showAlphaSlider || panelAlpha < 0.99f) scheme.primary else scheme.onSurface,
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
                Text(
                    text = stringResource(R.string.clipboard_float_title),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    style = MiuixTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Remove,
                        contentDescription = stringResource(R.string.clipboard_float_collapse),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = MiuixIcons.Medium.Close,
                        contentDescription = stringResource(R.string.clipboard_float_close),
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                    entryLongPressAction = entryLongPressAction,
                    entryHoldAction = entryHoldAction,
                    actionRow = actionRow,
                    onEntryClick = onEntryClick,
                    onEntryLongClick = onEntryLongClick,
                    onEntryDragStart = onEntryDragStart,
                    onEntryDragEnd = onEntryDragEnd,
                    onEntryAction = onEntryAction,
                )
            }
            }
            ClipboardFloatResizeCornerHandle(
                cornerRadius = ClipboardFloatPanelCornerRadius,
                color = scheme.primary,
                onResizeWindow = onResizeWindow
            )
        }
    }
}

@Composable
private fun BoxScope.ClipboardFloatResizeCornerHandle(
    cornerRadius: Dp,
    color: Color,
    onResizeWindow: (Float, Float) -> Unit
) {
    val touchSize = (cornerRadius.value + 12f).coerceAtLeast(28f).dp
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(touchSize)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResizeWindow(dragAmount.x, dragAmount.y)
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
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    actionRow: ClipboardFloatActionRowState,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
) {
    val entries by listController.filteredEntries.collectAsState()
    val loading by listController.loading.collectAsState()
    val searchQuery by listController.searchQuery.collectAsState()
    val isSearching = searchQuery.isNotBlank()
    val columnCount = ClipboardFloatWindowMetrics.columnCount(windowWidthDp)

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
                    if (isSearching) R.string.clipboard_search_empty else R.string.clipboard_empty
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        return
    }

    when (listStyle) {
        ClipboardFloatListStyle.CARD -> {
            ClipboardFloatGrid(
                entries = entries,
                windowWidthDp = windowWidthDp,
                columnCount = columnCount,
                entryLongPressAction = entryLongPressAction,
                entryHoldAction = entryHoldAction,
                actionRow = actionRow,
                onEntryClick = onEntryClick,
                onEntryLongClick = onEntryLongClick,
                onEntryDragStart = onEntryDragStart,
                onEntryDragEnd = onEntryDragEnd,
                onEntryAction = onEntryAction,
            )
        }
        ClipboardFloatListStyle.SINGLE_LINE -> {
            ClipboardFloatSingleLineList(
                entries = entries,
                windowWidthDp = windowWidthDp,
                entryLongPressAction = entryLongPressAction,
                onEntryClick = onEntryClick,
                onEntryLongClick = onEntryLongClick,
                onEntryDragStart = onEntryDragStart,
                onEntryDragEnd = onEntryDragEnd,
            )
        }
    }
}

@Composable
private fun ClipboardFloatSingleLineList(
    entries: List<ClipboardEntry>,
    windowWidthDp: Int,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            ClipboardFloatSingleLineRow(
                entry = entry,
                entryLongPressAction = entryLongPressAction,
                onClick = { onEntryClick(entry) },
                onWordTapLongClick = { onEntryLongClick(entry) },
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
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
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
    val isLong = rawText.length > 30 || rawText.contains('\n') || hasImage
    var expanded by remember(entry.id) { mutableStateOf(false) }

    val density = LocalDensity.current
    val thumbHeightPx = with(density) { 60.dp.roundToPx() }
    // 长按拖拽：把**整张卡**当拖影。抓图是 suspend 的，所以要起协程。
    val dragSnapshot = rememberHistoryCardSnapshot()
    val dragScope = rememberCoroutineScope()
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
            .recordHistoryCardSnapshot(dragSnapshot)
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
        color = clipboardFloatCardColor(LocalClipboardFloatGlass.current.active),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.05f))
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
                    modifier = Modifier.weight(1f)
                )

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
    entries: List<ClipboardEntry>,
    windowWidthDp: Int,
    columnCount: Int,
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    actionRow: ClipboardFloatActionRowState,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
    onEntryDragStart: () -> Unit,
    onEntryDragEnd: () -> Unit,
    onEntryAction: (ClipboardEntry, ClipboardFloatEntryAction) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // 滚动列表也是"其他交互" → 收起操作按钮。
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) actionRow.collapse() }
    }
    LazyVerticalGrid(
        state = gridState,
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
                entryLongPressAction = entryLongPressAction,
                entryHoldAction = entryHoldAction,
                actionRowVisible = actionRow.entryId == entry.id,
                onActionRowBounds = { bounds -> actionRow.boundsInWindow = bounds },
                onClick = {
                    actionRow.collapse()
                    onEntryClick(entry)
                },
                onWordTapLongClick = {
                    actionRow.collapse()
                    onEntryLongClick(entry)
                },
                onStationaryLongPress = {
                    if (entryHoldAction == ClipboardFloatEntryHoldAction.OPERATE_ENTRY) {
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
    entryLongPressAction: ClipboardFloatEntryLongPressAction,
    entryHoldAction: ClipboardFloatEntryHoldAction,
    /** 「操作条目」那排按钮是否展开（由列表级状态决定）。 */
    actionRowVisible: Boolean,
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
    val previewLines = if (columnCount <= 1) 3 else 2
    val typeLabel = clipboardFloatTypeLabel(entry.displayTypeLabelKey())
    val density = LocalDensity.current
    val cardWidthPx = with(density) {
        val contentWidthDp = (windowWidthDp - 20 - (columnCount - 1) * 8) / columnCount
        contentWidthDp.dp.roundToPx().coerceAtLeast(1)
    }
    val thumbHeightPx = with(density) { 64.dp.roundToPx() }
    // 长按拖拽：把**整张卡**当拖影。抓图是 suspend 的，所以要起协程。
    val dragSnapshot = rememberHistoryCardSnapshot()
    val dragScope = rememberCoroutineScope()
    val thumbnail = rememberClipboardFloatThumbnail(
        entry = entry,
        enabled = hasImage,
        cardWidthPx = cardWidthPx,
        thumbHeightPx = thumbHeightPx
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 整张卡录一份：长按拖拽时"把整张卡拿起来"（见 HistoryCardSnapshot）。
            .recordHistoryCardSnapshot(dragSnapshot)
            .clipboardFloatCardGestures(
                dragAction = entryLongPressAction,
                onClick = onClick,
                onStationaryLongPress = {
                    // 「长按条目」= 原地按住不动 → 展开操作按钮（目前只有这一档动作）。
                    if (entryHoldAction == ClipboardFloatEntryHoldAction.OPERATE_ENTRY) {
                        onStationaryLongPress()
                    }
                },
                onDragAction = {
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
                },
            ),
        shape = RoundedCornerShape(12.dp),
        color = clipboardFloatCardColor(LocalClipboardFloatGlass.current.active),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.primary,
                    maxLines = 1
                )
                Text(
                    text = formatClipboardFloatRelativeTime(entry.createdAtEpochMs),
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = stringResource(R.string.clipboard_float_meta_image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                val imageMeta = buildClipboardFloatImageMeta(entry)
                if (imageMeta.isNotEmpty()) {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                if (bodyText.isNotEmpty()) {
                    Text(
                        text = bodyText,
                        style = MiuixTheme.textStyles.body2,
                        color = scheme.onSurface,
                        maxLines = previewLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val metaText = buildClipboardFloatTextMeta(entry, bodyText)
                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
                        style = MiuixTheme.textStyles.footnote2,
                        color = scheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 「长按条目」→ 操作条目：卡片底部展开的一排操作按钮。
            // 这一排自己会 consume 按下的 down（见 ClipboardFloatEntryActionRow），
            // 所以点按钮不会顺带触发卡片的单击 / 拖动。
            AnimatedVisibility(
                visible = actionRowVisible,
                enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
            ) {
                ClipboardFloatEntryActionRow(
                    showSaveImage = hasImage,
                    onActionRowBounds = onActionRowBounds,
                    onAction = onEntryAction,
                )
            }
        }
    }
}

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
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = if (destructive) scheme.error else scheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
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
    thumbHeightPx: Int
): Bitmap? {
    val context = LocalContext.current
    var thumbnail by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id, enabled, cardWidthPx, thumbHeightPx) {
        if (!enabled) {
            thumbnail = null
            return@LaunchedEffect
        }
        thumbnail = withContext(Dispatchers.IO) {
            ClipboardThumbnailCache.loadEntryThumbnailsForCard(
                context = context,
                entry = entry,
                targetWidthPx = cardWidthPx,
                maxVisibleHeightPx = thumbHeightPx
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
