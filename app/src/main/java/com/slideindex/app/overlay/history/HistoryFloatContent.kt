@file:OptIn(ExperimentalFoundationApi::class)

package com.slideindex.app.overlay.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
 * - **视觉** 9×28dp、圆角 5dp、距屏幕右缘 12dp
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
    OverlayAwareModuleTheme {
        if (handleVisible) {
            HistoryFloatHandle(
                alert = handleAlert,
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
    saveCount: Int,
    haptics: HistoryHaptics,
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
    onMoveHandleEnd: () -> Unit = {},
    onRevealStart: () -> Boolean = { false },
    onRevealEnd: (Boolean) -> Unit = {},
    onQuickNote: () -> Unit,
) {
    var active by remember { mutableStateOf(false) }
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
                        active = true
                    },
                    onDragEnd = {
                        finishDrag()
                        scope.launch {
                            delay(500)
                            active = false
                        }
                    },
                    onDragCancel = {
                        finishDrag()
                        scope.launch {
                            delay(500)
                            active = false
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
                    active = true
                    scope.launch {
                        delay(500)
                        active = false
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
        }
    }
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

/** 视觉条距屏幕右缘的距离。 */
private const val HANDLE_EDGE_GAP_DP = 12

/** 存下后的脉冲：设计稿 `pippulse 900ms`。 */
private const val HANDLE_PULSE_DURATION_MS = 900
private const val HANDLE_PULSE_SCALE_Y = 0.18f
private const val HANDLE_PULSE_SHIFT_DP = 3f
