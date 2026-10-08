@file:OptIn(ExperimentalFoundationApi::class)

package com.slideindex.app.overlay.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 剪贴板历史边缘把手：点击/左滑/长按打开 [com.slideindex.app.overlay.FloatBallStashPanel]。
 *
 * 形态按设计稿（`ui_demo_capsule.html`）：
 * - **视觉** 9×28dp、圆角 5dp、距屏幕右缘 6dp（原设计稿 12dp，用户反馈"间距太大"→ 减半）
 * - **命中区** 48×48dp（Android 最小触摸目标）
 * - 有待办未完成时**整条变色**（不出数字、不加宽）
 */
@Composable
fun HistoryFloatContent(
    handleVisible: Boolean,
    handleAlert: Boolean,
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
    onMoveHandleEnd: () -> Unit = {},
    onRevealStart: () -> Boolean = { false },
    onRevealEnd: (Boolean) -> Unit = {},
    /** 长按把手：就地记一条（弹出输入槽）。默认退回"打开面板"。 */
    onQuickNote: () -> Unit = onOpenPanel,
) {
    // 「刚存下」信号（面板存下一条 → 把手脉冲一下，设计稿 `.pip.pulse`）。
    // 读它 = 订阅：`HistorySaveSignal` 的属性是 Compose 状态。
    val saveCount = HistorySaveSignal.saveCount
    val haptics = rememberHistoryHaptics()
    // 被按住 / 拖动中：把手轻微放大 + 提高不透明度（手柄自身的手感反馈）。
    // 状态提到这一层，是因为流光也要读它（拖动时不画，见 glowActive）。
    var active by remember { mutableStateOf(false) }
    // 「已提醒但用户还没划掉/完成」→ 慢速流光。
    // ⚠️ 这个面板状态由别人维护（`StashReminderPendingState`），这里**只读**，不建也不改这个文件。
    // 用 `derivedStateOf` 把三个闸门合成一个 Boolean：它只在真正切换时让 Compose 失效，
    // 所以"无提醒"的常驻状态下是**零动画、零重绘**的。
    val glowActive by remember {
        derivedStateOf {
            // 1) 有未处理的提醒；2) 窗口可见（服务在隐藏时会直接 return，这是兜底）；
            // 3) 没在拖动/按住（跟手时优先给手感和跟手，不叠动画）。
            StashReminderPendingState.hasPending.value && handleVisible && !active
        }
    }
    OverlayAwareModuleTheme {
        if (handleVisible) {
            HistoryFloatHandle(
                alert = handleAlert,
                glowActive = glowActive,
                active = active,
                onActiveChange = { active = it },
                saveCount = saveCount,
                haptics = haptics,
                onOpenPanel = onOpenPanel,
                onMoveHandle = onMoveHandle,
                onMoveHandleEnd = onMoveHandleEnd,
                onRevealStart = onRevealStart,
                onRevealEnd = onRevealEnd,
                onQuickNote = onQuickNote,
            )
        }
    }
}

@Composable
private fun HistoryFloatHandle(
    alert: Boolean,
    /** true = 播放慢速流光（已提醒未处理 + 可见 + 未拖动）。 */
    glowActive: Boolean,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    saveCount: Int,
    haptics: HistoryHaptics,
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
    onMoveHandleEnd: () -> Unit = {},
    onRevealStart: () -> Boolean = { false },
    onRevealEnd: (Boolean) -> Unit = {},
    onQuickNote: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 激活（被按住 / 拖动中）时轻微放大 + 提高不透明度。
    val barWidth by animateDpAsState(
        targetValue = if (active) 11.dp else 9.dp,
        label = "handleBarWidth",
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (active) 0.95f else 0.72f,
        label = "handleBarAlpha",
    )
    // 存下后的脉冲：设计稿 `.pip.pulse` = `pippulse 900ms`，35% 处 `translateX(-3px) scaleY(1.18)`。
    // `lastPulsed` 以当前值为初值：否则面板里之前存过的东西会让把手在**启动时**凭空脉冲一下。
    var lastPulsed by remember { mutableIntStateOf(saveCount) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(saveCount) {
        if (saveCount == lastPulsed) return@LaunchedEffect
        lastPulsed = saveCount
        pulse.snapTo(0f)
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = HANDLE_PULSE_DURATION_MS
                1f at (HANDLE_PULSE_DURATION_MS * 35 / 100) using CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
            },
        )
    }
    val scheme = MiuixTheme.colorScheme
    // 拉满 = 面板宽度（px）。用屏幕宽度而不是 LocalWindowInfo.containerSize —— 把手窗只有 48dp 宽，
    // containerSize 是把手自己，不是屏幕。
    val revealDistanceState = rememberUpdatedState(
        historyPanelRevealDistancePx(LocalContext.current),
    )
    // 手势 lambda 被 `pointerInput(Unit)` 抓一次就不再更新，所以触觉对象也要走 updatedState。
    val latestHaptics = rememberUpdatedState(haptics)

    Box(
        modifier = Modifier
            .size(HANDLE_HIT_DP.dp)
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                // 前 8dp 锁定主方向（设计稿注释：横向=拉出面板，纵向=挪位置）。
                var axis: DragAxis? = null
                var revealing = false
                var crossedHalf = false
                // 拖动到底或中途被系统手势（边缘返回）抢走都会走收尾：
                // 之前只处理 onDragEnd，被抢走时 onDragCancel 不打开面板 → 「拖动打不开」。
                val finishDrag = {
                    if (revealing) {
                        val commit = HistoryPanelReveal.dragProgress >= REVEAL_COMMIT_FRACTION
                        onRevealEnd(commit)
                    } else if (totalX <= OPEN_DRAG_THRESHOLD_X) {
                        // 没走到跟手（轴锁定前就松手 / 面板已在显示）：沿用原来的"拖过阈值就开"。
                        onOpenPanel()
                    }
                    onMoveHandleEnd()
                }
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        axis = null
                        revealing = false
                        crossedHalf = false
                        onActiveChange(true)
                    },
                    onDragEnd = {
                        finishDrag()
                        scope.launch {
                            delay(500)
                            onActiveChange(false)
                        }
                    },
                    onDragCancel = {
                        finishDrag()
                        scope.launch {
                            delay(500)
                            onActiveChange(false)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    if (axis == null) {
                        val lockedX = abs(totalX) >= HANDLE_AXIS_LOCK_PX
                        val lockedY = abs(totalY) >= HANDLE_AXIS_LOCK_PX
                        if (lockedX || lockedY) {
                            axis = if (abs(totalX) >= abs(totalY)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                        }
                    }
                    when (axis) {
                        DragAxis.HORIZONTAL, null -> {
                            // 跟手拉出：第一次真的往左拖时才把面板窗叫出来（面板从屏幕外开始跟着手指走）。
                            if (!revealing && totalX < -HANDLE_AXIS_LOCK_PX) {
                                revealing = onRevealStart()
                            }
                            if (revealing) {
                                val distance = revealDistanceState.value
                                HistoryPanelReveal.dragProgress =
                                    (-totalX / distance).coerceIn(0f, 1f)
                                // 过半轻震（与跟手阈值同一个手感点）。
                                val half = HistoryPanelReveal.dragProgress >= REVEAL_COMMIT_FRACTION
                                if (half != crossedHalf) {
                                    crossedHalf = half
                                    if (half) latestHaptics.value.tick()
                                }
                            }
                        }
                        DragAxis.VERTICAL -> onMoveHandle(dragAmount.y)
                    }
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // 单击也能打开：边缘横向拖动会被系统返回手势抢走，点按永远能到应用手里。
                onClick = { onOpenPanel() },
                // 长按 = 就地记一条（设计稿 `.slot` 输入槽）。
                onLongClick = {
                    haptics.longPress()
                    onQuickNote()
                },
                onDoubleClick = {
                    onActiveChange(true)
                    scope.launch {
                        delay(500)
                        onActiveChange(false)
                    }
                    onOpenPanel()
                },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                .padding(end = HANDLE_EDGE_GAP_DP.dp)
                .size(width = barWidth, height = 28.dp)
                .graphicsLayer {
                    scaleY = 1f + HANDLE_PULSE_SCALE_Y * pulse.value
                    translationX = -HANDLE_PULSE_SHIFT_DP.dp.toPx() * pulse.value
                },
            shape = RoundedCornerShape(5.dp),
            color = if (alert) {
                // 有待办未完成：整条变成主题色（对应设计稿的 --accent-solid）
                scheme.primary.copy(alpha = barAlpha)
            } else {
                scheme.onSurface.copy(alpha = barAlpha * 0.28f)
            },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (alert) {
                    scheme.primary.copy(alpha = 0.55f)
                } else {
                    scheme.onSurface.copy(alpha = if (active) 0.24f else 0.12f)
                },
            ),
        ) {
            Box(
                modifier = Modifier.background(
                    if (alert) {
                        scheme.onPrimary.copy(alpha = 0.10f)
                    } else {
                        scheme.onSurface.copy(alpha = 0.02f)
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {}
            // 「已提醒但未处理」时的慢速流光。
            // 注意：这个 composable 调用**在 if 内部**，所以不成立时整个
            // `rememberInfiniteTransition` 都不存在于组合里 —— 没有提醒就真的一帧都不跑。
            if (glowActive) {
                HistoryHandleGlowSweep(
                    color = scheme.primary,
                    shape = RoundedCornerShape(5.dp),
                )
            }
        }
    }
}

/**
 * 指示条上的「慢速流光」高光层。
 *
 * 为什么这么做（性能取舍，这是常驻悬浮窗，不能拿整条无限重绘去换效果）：
 * - **不是彩虹色**：用户说的是"炫光流彩"，但彩虹色在 9dp 宽的竖条上既看不清又费电；
 *   这里用**主题色（accent = `scheme.primary`）的一段高光**沿条扫过，观感更干净、也不引入新色板。
 * - **不是 `infiniteRepeatable` 全帧重绘整条**：只有这一层 9×28dp 的叠加层带
 *   `Brush.linearGradient`，动画只是它的 `graphicsLayer.translationX`
 *   —— 走的是 RenderNode 的 transform，**不触发重组，也不重绘把手本体**。
 * - 周期 2200ms（"慢速"），`LinearEasing` 匀速，中间是主题色、两头渐隐到透明，
 *   所以看起来是一道柔和的光扫过去，而不是一根硬边亮条。
 */
@Composable
private fun HistoryHandleGlowSweep(
    color: Color,
    shape: Shape,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { HANDLE_BAR_WIDTH_DP.dp.toPx() }
    val transition = rememberInfiniteTransition(label = "handleGlow")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HANDLE_GLOW_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "handleGlowSweep",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .graphicsLayer {
                // 高光带比条宽一点，扫出边界时不会有硬切。
                translationX = -widthPx * HANDLE_GLOW_BAND_SCALE +
                    widthPx * (1f + 2f * HANDLE_GLOW_BAND_SCALE) * sweep
            }
            .drawBehind {
                val band = size.width * HANDLE_GLOW_BAND_SCALE
                // 亮带画在**本地原点**（中心 = x 0），扫动全交给上面的 `translationX`：
                // s=0 时中心在 `-0.85w`（整条在条左侧外，只有右尾刚碰到左边缘）、
                // s=1 时中心在 `1.85w`（整条在条右侧外）。这样"一道光从左扫到右"刚好铺满
                // 一个周期 —— 如果把亮带画在右边缘，光只会在前 1/3 个周期里出现、剩下全黑。
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            color.copy(alpha = HANDLE_GLOW_ALPHA),
                            Color.Transparent,
                        ),
                        start = Offset(-band, 0f),
                        end = Offset(band, 0f),
                    ),
                )
            },
    )
}

/** 向左拖动超过这个距离就认为用户想拉出收纳面板（未进入跟手时的兜底判定）。 */
private const val OPEN_DRAG_THRESHOLD_X = -20f

/** 前这么多 dp 内锁定主方向：横向 = 跟手拉出面板，纵向 = 挪把手（设计稿注释同款）。 */
private const val HANDLE_AXIS_LOCK_PX = 8f

/** 过半就提交（松手后面板归位，否则弹回）。 */
private const val REVEAL_COMMIT_FRACTION = 0.5f

/** 拖动的主方向。 */
private enum class DragAxis { HORIZONTAL, VERTICAL }

/** 命中区边长（Android 最小触摸目标）。 */
private const val HANDLE_HIT_DP = 48

/**
 * 视觉条距屏幕右缘的距离。
 *
 * §间距收紧（用户反馈"指示条离右边太远/间距太大"）：
 * **原来 12dp → 现在 6dp（减半）**。只动"距屏幕边缘的间距"，条的**宽度 9dp 不变**
 * （宽度另有 `HistoryFloatHandleWidth` 设置项，那条线本次没碰）。
 */
private const val HANDLE_EDGE_GAP_DP = 6

/** 条宽（视觉）：与 [HistoryFloatHandle] 里 `barWidth` 的静止值保持一致，流光层要按它算扫过距离。 */
private const val HANDLE_BAR_WIDTH_DP = 9

/** 流光周期（"慢速"）：2.2s 扫一遍。 */
private const val HANDLE_GLOW_PERIOD_MS = 2200

/** 亮带宽度 = 条宽的这个比例（取小值是为了在 9dp 窄条上也像"一段光"而不是"整条亮"）。 */
private const val HANDLE_GLOW_BAND_SCALE = 0.85f

/** 高光峰值不透明度：常驻元素，别太扎眼。 */
private const val HANDLE_GLOW_ALPHA = 0.55f

/** 存下后的脉冲：设计稿 `pippulse 900ms`。 */
private const val HANDLE_PULSE_DURATION_MS = 900
private const val HANDLE_PULSE_SCALE_Y = 0.18f
private const val HANDLE_PULSE_SHIFT_DP = 3f
