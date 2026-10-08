@file:OptIn(ExperimentalFoundationApi::class)

package com.slideindex.app.overlay.history

import android.graphics.BlurMaskFilter
import android.util.Log
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 剪贴板历史边缘把手：点击/左滑/长按打开 [com.slideindex.app.overlay.FloatBallStashPanel]。
 *
 * 形态按设计稿（`ui_demo_capsule.html`）：
 * - **视觉** 9×28dp、圆角 5dp、距屏幕右缘 3dp（原设计稿 12dp → 6dp → 3dp，用户两次要求"减半"）
 * - **命中区** 48×48dp（Android 最小触摸目标）
 * - 有待办未完成时**整条变色**（不出数字、不加宽）+ 一道"科幻 AI 风"流光
 *
 * §流光（用户："没啥存在感" → 要有存在感的科幻 AI 风）：光是**自己画**的（`drawWithCache`
 * 里 bloom + 过曝主带 + 递减拖尾），不是叠一层 `Box` 做 `translationX`。原实现只有一条
 * 单色淡带在 9dp 的条里平移，既没有拖尾也没有溢出条外，所以在真机上"看不出来"。
 * 条本身的**尺寸/位置/圆角/配色规则一个都没动**，只换了"光"的算法。
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
    // 「已提醒但用户还没划掉/完成」→ 科幻流光。
    // ⚠️ 这个面板状态由别人维护（`StashReminderPendingState`），这里**只读**，不建也不改这个文件。
    // 用 `derivedStateOf` 把三个闸门合成一个 Boolean：它只在真正切换时让 Compose 失效，
    // 所以"无提醒"的常驻状态下是**零动画、零重绘**的。
    // ⚠️ 这个 Boolean 同时也是**动画的总开关**：唯一那一个 `animateFloat` 挂在
    // `HistoryHandleGlowSweep` 里，而它只在 `glowActive == true` 时进组合
    // （`rememberInfiniteTransition` 位于 if 内）—— 没有提醒时既没有动画时钟，
    // 也没有 `drawWithCache` 的光层 block，整条把手回到"一次画完就不动"的最省电形态。
    val glowActive by remember {
        derivedStateOf {
            // 1) 有未处理的提醒；2) 窗口可见（服务在隐藏时会直接 return，这是兜底）；
            // 3) 没在拖动/按住（跟手时优先给手感和跟手，不叠动画）。
            // ⚠️ 给后来的排查者（"用户实测看不到流光"那次复盘）：**常态下这三条都成立**
            // —— 服务里的 `handleVisible` 初值是 true、全工程没有任何地方把它写成 false
            // （把手窗和面板是两个窗口，面板打开并不会藏把手），`active` 只在拖动/双击那 0.5s 为 true。
            // 所以"看不到光"如果发生，最大嫌疑是 `hasPending` 压根没被点起来
            // （它只在「提醒通知发出」/「面板可见时刷新」这两条路径上被写），而不是这里被挡住。
            // 下面的 GLOW_DEBUG 打点就是为了下次能一眼分清是"没触发"还是"触发了但看不见"。
            StashReminderPendingState.hasPending.value && handleVisible && !active
        }
    }
    // ── 临时的可观测性打点（只为定位"到底触发没触发"，不改任何 UI）──
    // 为什么用 `snapshotFlow` 而不是 `LaunchedEffect(glowActive)`：三个输入都要能单独看出来。
    // `distinctUntilChanged` 让它**只在三元组真的翻转时**打一次（这就是"节流"：
    // 平常一帧都不打，不会刷爆 logcat；进组合时先打一条当前值）。用户复现一次，
    // `adb logcat -s HandleGlow` 即可判定：
    // - 全程只有 pending=false → 提醒/通知那条链没点亮 `hasPending`（不是绘制问题）；
    // - 出现 hasPending=true handleVisible=true active=false（glowActive=true）却依然看不见
    //   → 那就是纯绘制/对比度问题，往参数上调（见文件末尾那组 HANDLE_GLOW_*）。
    if (GLOW_DEBUG) {
        LaunchedEffect(Unit) {
            snapshotFlow {
                Triple(
                    StashReminderPendingState.hasPending.value,
                    handleVisible,
                    active,
                )
            }
                .distinctUntilChanged()
                .collect { (pending, visible, dragging) ->
                    Log.d(
                        GLOW_DEBUG_TAG,
                        "inputs changed: hasPending=$pending handleVisible=$visible active=$dragging " +
                            "=> glowActive=${pending && visible && !dragging}",
                    )
                }
        }
    }
    // 流光的唯一动画时钟：**只在 `glowActive` 时进组合**。
    // `State<Float>` 可以这样建、再交给下面的绘制层去读，这正是"把逐帧读取推到绘制阶段"
    // 的关键 —— 状态读取在绘制 lambda 里，就不会每帧重组这个 composable。
    val glowPhase = if (glowActive) HistoryHandleGlowSweep() else null
    OverlayAwareModuleTheme {
        if (handleVisible) {
            HistoryFloatHandle(
                alert = handleAlert,
                glowActive = glowActive,
                glowPhase = glowPhase,
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
    /** true = 播放科幻流光（已提醒未处理 + 可见 + 未拖动）。 */
    glowActive: Boolean,
    /**
     * 流光的唯一动画进度（0→1 扫一遍）。**只有 [glowActive] 为 true 时才非 null**；
     * null 表示"当前不该有光"，绘制层直接跳过所有光相关的 draw call。
     */
    glowPhase: State<Float>?,
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
    // 条的两个颜色（每帧都要算，但 `scheme` 变化很少，放组合里算一次即可）。
    // alert（有待办未完成）= 整条主题色；否则是低透明度的 onSurface 细条。
    val barColor = if (alert) scheme.primary else scheme.onSurface
    val barFillAlpha = if (alert) 1f else 0.28f
    val barBorderColor = if (alert) {
        scheme.primary.copy(alpha = 0.55f)
    } else {
        scheme.onSurface.copy(alpha = if (active) 0.24f else 0.12f)
    }

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
            )
            // 存下后的脉冲（设计稿 `.pip.pulse`）只走 RenderNode 变换：
            // 这里用**带 lambda 的** `graphicsLayer` —— 它把 `pulse.value` 的读取推迟到绘制阶段，
            // 所以脉冲每帧变化**不会重组**这个 composable，只重画这一层。
            .graphicsLayer {
                scaleY = 1f + HANDLE_PULSE_SCALE_Y * pulse.value
                translationX = -HANDLE_PULSE_SHIFT_DP.dp.toPx() * pulse.value
            }
            // 「光」全部画在这一层里，而不是再叠一层子 composable：
            // - 原来那条亮带是 `Surface` 的**子节点**，而 Material3 的 Surface 会把自己 clip 到
            //   形状内，于是光最多只能在 9dp 的条里走 —— 这正是"没啥存在感"的根因之一；
            // - 现在 bloom（外溢柔光）/过曝主带/拖尾都由同一个 draw scope 画，顺序可控；
            //   光的绘制范围就是 48dp 的命中区，溢出量另有 [HANDLE_GLOW_BLOOM_LIMIT_RATIO]
            //   夹住（只让光往条**左侧**漏，右侧留给屏幕边缘/系统手势区）。
            // 性能：`drawWithCache` 的 block 只在**尺寸或光/配色状态变化**时重跑，
            // 逐帧变化的是 `phase`（唯一那个 `animateFloat`）、`pulse`、`barAlpha`、`barWidth`，
            // 它们全都在绘制 lambda 里读 —— 所以"每帧"只重绘这一层小区域，
            // 既不重组也不重绘别的东西。
            .drawWithCache {
                val barHeightPx = HANDLE_BAR_HEIGHT_DP.dp.toPx()
                val cornerPx = HANDLE_BAR_CORNER_DP.dp.toPx()
                val borderWidthPx = HANDLE_BAR_BORDER_DP.dp.toPx()
                // 辉光笔：Compose 的 `Paint` **没有 `maskFilter` 属性**（它的 API 只有
                // color/alpha/isAntiAlias/style/strokeWidth/blendMode/shader/colorFilter/pathEffect），
                // 所以模糊滤镜只能设在**平台**画笔上；而拿平台画笔的两条老路都不能走：
                // - `Compose Paint.asFrameworkPaint()` 在本仓库（deprecated 当错误）会直接编不过；
                // - `import android.graphics.Paint` 会与 Compose 的同名类撞（本文件本来就只 import 后者）。
                // 因此这里用**全限定名**就地构造平台画笔（不动任何现有 import）。
                // 代价是它不能传给 Compose 的 `Canvas.drawRoundRect`（那个只收 Compose Paint），
                // 所以下面改走平台画布 `canvas.nativeCanvas.drawRoundRect(...)`（见 drawIntoCanvas 处）。
                val bloomPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    maskFilter = BlurMaskFilter(
                        HANDLE_GLOW_BLOOM_RADIUS_DP.dp.toPx(),
                        BlurMaskFilter.Blur.NORMAL,
                    )
                }
                // 条本体两个颜色在这里算一次（`barAlpha` 逐帧会变，所以只缓存不透明前的基色）。
                // 原实现是 `Surface(color = 基色@barAlpha)` + 里面再叠一个"洗色"`Box`
                // （alert 时 `onPrimary@10%`，否则 `onSurface@2%`）。
                // 这里**照原样分两层画**（基色 + 洗色），而不是用 `compositeOver` 合成一个颜色：
                // 合成要把"元素的 alpha × 两次叠色的 alpha"算对才能跟前一版像素一致，
                // 而这一步没有任何收益（反正都是同一次 draw 里的两个 drawPath），不值得冒险。
                val washColor = if (alert) scheme.onPrimary.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.02f)
                val barBaseColor = barColor.copy(alpha = barAlpha * barFillAlpha)
                // 光的颜色全从主题色 accent 推出来（不许新增颜色资源）。
                val coreColor = glowCore(scheme.primary)
                // 拖尾的渐变色也只算一次：`i` 决定色相漂移量，逐帧要变的只有位置/透明度。
                val bandColors = List(HANDLE_GLOW_BAND_COUNT) { i ->
                    glowBand(scheme.primary, i)
                }
                onDrawWithContent {
                    // 条矩形每帧重算：`barWidth` 是按住时的"变宽"动画（9→11dp），
                    // 所以它也算逐帧状态。几何放这里算的代价可以忽略（几个 dp→px 换算），
                    // 换来的是条本体与光**永远用同一套几何**，不会出现"条变宽了、光还按 9dp 走"。
                    val barWidthPx = barWidth.toPx()
                    val barLeft = size.width - HANDLE_EDGE_GAP_DP.dp.toPx() - barWidthPx
                    val barTop = (size.height - barHeightPx) / 2f
                    val barRect = Rect(barLeft, barTop, barLeft + barWidthPx, barTop + barHeightPx)
                    val barPath = Path().apply {
                        addRoundRect(RoundRect(barRect, CornerRadius(cornerPx, cornerPx)))
                    }
                    // ① 条本体（原来的 Surface 背景 + 内层洗色，颜色/圆角/尺寸全不变）。
                    //    边框挪到**最后**画（见 ⑤），否则会被光盖住、条失去清晰轮廓。
                    drawPath(barPath, barBaseColor)
                    drawPath(barPath, washColor)
                    // ② 光。`glowPhase == null` 就是"当前不该有光"（没提醒 / 在拖动 / 不可见）：
                    // 这时不但不建动画时钟，连一次 draw call 都不多发。
                    // ⚠️ `glowPhase.value` 在这里读 = 在**绘制阶段**读动画状态：
                    // 每帧只让这一层 draw 失效（invalidate），不触发重组，也不重绘别处。
                    val t = glowPhase?.value ?: run {
                        // 没光的时候仍要把边框补上（上面把边框挪到最后了）。
                        drawPath(barPath, barBorderColor, style = Stroke(width = borderWidthPx))
                        return@onDrawWithContent
                    }
                    val beat = glowBeat(t)
                    // 主带位置（px）：中心从"条左缘往左 1.5 条宽"走到"条右缘往右 1.5 条宽"，
                    // 起止都在条外 → 一个周期里能看清"进来 → 经过 → 离开"，才有扫过的速度感。
                    val travel = barWidthPx * (1f + 2f * HANDLE_GLOW_TRAVEL_SCALE)
                    val bandX = barRect.right + barWidthPx * HANDLE_GLOW_TRAVEL_SCALE -
                        travel * t
                    // ③ 条内"过曝主带 + 递减拖尾"（clip 在条形状里，不会糊到条外）。
                    //    扫动靠每帧重算几何（`bandX`），不是 `translationX` ——
                    //    因为拖尾的间距/透明度/色相都要随位置变，必须逐帧重画渐变。
                    //    ⚠️ 必须**先画这一层**：它是"过曝的芯"，被下面那层柔光压过之后就发灰了。
                    clipPath(barPath) {
                        for (i in 0 until HANDLE_GLOW_BAND_COUNT) {
                            val f = i.toFloat()
                            // 拖尾在主带的**右边** = 光整体从右往左扫（"从屏幕外扫进来"）。
                            val center = bandX + f * barWidthPx * HANDLE_GLOW_SPACING_SCALE
                            // 间距与 alpha 都递减（`pow`），产生"扫过去"的速度感；
                            // 亮度统一乘 `beat` 做呼吸；`coerceIn` 只是防御（alpha 必须落 0…1）。
                            val bandAlpha = (HANDLE_GLOW_CORE_ALPHA * HANDLE_GLOW_TRAIL_DECAY.pow(f) * beat)
                                .coerceIn(0f, 1f)
                            // 主带更宽、拖尾更窄：宽的亮核 + 细的余晖，层次才拉得开。
                            val half = barWidthPx * if (i == 0) {
                                HANDLE_GLOW_CORE_HALF_SCALE
                            } else {
                                HANDLE_GLOW_TRAIL_HALF_SCALE
                            }
                            // 两端透明（start/end 都在条外）→ 不会出现硬边。
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, bandColors[i], Color.Transparent),
                                    start = Offset(center - half, barRect.center.y),
                                    end = Offset(center + half, barRect.center.y),
                                ),
                                topLeft = Offset(center - half, barRect.top),
                                size = Size(half * 2f, barRect.height),
                                alpha = bandAlpha,
                            )
                        }
                    }
                    // ④ 外层辉光（bloom）：**画在条本体之上**，而且不只是沿条宽、还往**上下左右**外扩
                    //    —— 这是"光漏出 9dp 的条"的关键，也是上一版"看不见"的主因：
                    //    上一版 bloom 只比条宽一点、纵向完全不外扩，于是它基本整块躺在条里，
                    //    再被 0.72 alpha 的条本体盖住 → 条外什么都没剩下。
                    //    现在三层由大到小叠（外层淡、内层亮），配合 9dp 模糊形成柔和光环。
                    //    ⚠️ 右边界仍然夹在条的右缘：条距屏幕右缘只有 3dp，光只能往左（和上下）走。
                    val springX = HANDLE_HIT_DP.dp.toPx() * HANDLE_GLOW_BLOOM_LIMIT_RATIO
                    val bloomTop = (barRect.top - barWidthPx * HANDLE_GLOW_BLOOM_SPILL_SCALE)
                        .coerceAtLeast(0f)
                    val bloomBottom = (barRect.bottom + barWidthPx * HANDLE_GLOW_BLOOM_SPILL_SCALE)
                        .coerceAtMost(size.height)
                    val bloomAlphaBase = HANDLE_GLOW_BLOOM_ALPHA * beat
                    drawIntoCanvas { canvas ->
                        // ⚠️ 走平台画布：`nativeCanvas` 是 `androidx.compose.ui.graphics.Canvas` 上的
                        // 扩展属性（`AndroidCanvas_androidKt.getNativeCanvas`，已 import），
                        // 只有它的 `drawRoundRect(l, t, r, b, rx, ry, Paint)` 收平台画笔；
                        // Compose 的 `Canvas.drawRoundRect` 只收 Compose `Paint`（会类型不符）。
                        // ⚠️ `bloomPaint` 是**平台**画笔，`color` 是 ARGB Int，所以要 `toArgb()`；
                        // 别改回 Compose `Paint` —— 那支笔没有 `maskFilter`，模糊会失效。
                        val native = canvas.nativeCanvas
                        for (j in HANDLE_GLOW_BLOOM_LAYER_SCALES.indices.reversed()) {
                            bloomPaint.color = coreColor.copy(
                                alpha = (bloomAlphaBase * HANDLE_GLOW_BLOOM_LAYER_ALPHAS[j])
                                    .coerceIn(0f, 1f),
                            ).toArgb()
                            val half = barWidthPx * HANDLE_GLOW_BLOOM_LAYER_SCALES[j]
                            native.drawRoundRect(
                                (bandX - half).coerceAtLeast(springX), bloomTop,
                                (bandX + half).coerceAtMost(barRect.right), bloomBottom,
                                cornerPx, cornerPx, bloomPaint,
                            )
                        }
                    }
                    // ⑤ 最后补 1dp 边框：让"发光"的条仍然有清楚轮廓，也不会被光糊掉边界。
                    drawPath(barPath, barBorderColor, style = Stroke(width = borderWidthPx))
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        // 唯一的动画时钟：只在"该有光"时才进组合（`rememberInfiniteTransition` 在 if 内），
        // 没提醒 / 拖动中 / 窗口不可见时它整个不存在 —— 真的一帧都不跑。
        if (glowActive) {
            HistoryHandleGlowSweep()
        }
    }
}

/**
 * 唯一的一个动画时钟（性能硬约束：单个 float 驱动整条流光）。
 *
 * 为什么做成"只吐一个 `State<Float>` 的 @Composable"：
 * 它唯一的产物就是返回的 `State<Float>`，而 `State` 可以在 `if` 里创建、
 * 再由 `if` 外层 `HistoryFloatHandle` 的 `drawWithCache` 绘制 lambda 去读 ——
 * 读取点在**绘制阶段**，所以每帧只让那一层 draw 失效（`invalidate`），
 * **不重组**把手、也不重绘别的节点。它自己**不画任何像素**（没有 UI）。
 * 反过来，如果把这个 `rememberInfiniteTransition` 提到 `if` 外面，
 * "没有提醒"的常驻状态下就会有一个永远跑着的动画时钟 —— 那是本次改动明确要避免的。
 *
 * 为什么亮度包络不另开一个动画：需求要"2s 亮 / 1s 暗"，但**再开一个无限动画**就多一个时钟；
 * 这里直接用同一个 `phase` 当呼吸的相位、且周期与扫描周期**不同频**（见 [glowBeat]），
 * 既有呼吸感又不会和扫光同频僵硬。
 */
@Composable
private fun HistoryHandleGlowSweep(): State<Float> {
    val transition = rememberInfiniteTransition(label = "handleGlow")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HANDLE_GLOW_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "handleGlowPhase",
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
 * 视觉条距**屏幕右缘**的距离（也是它"贴右边"的唯一决定因素）。
 *
 * 为什么这个常量就等于"到屏幕右缘的距离"：
 * - 把手窗是 `WRAP_CONTENT`（内容宽 = [HANDLE_HIT_DP] 的命中区）、
 *   `gravity = TOP or START`、`x = 0`（见 `HistoryFloatService.applyHandlePosition`）；
 * - 命中区里条是 `contentAlignment = CenterEnd` + `padding(end = 本值)`，
 *   绘制层也用 `barLeft = size.width - 本值 - 条宽` 定位；
 * - 于是"屏幕右缘 − 条右缘"= 本值。**只动间距**，条宽/高/圆角/命中区都没动
 *   （条宽另有 `HistoryFloatHandleWidth` 设置项，那条线本次没碰）。
 *
 * §间距三次收紧（用户反馈"间距太大"，两次都要求"减半"）：
 * **12dp（原设计稿）→ 6dp → 3dp**。
 *
 * ⚠️ 3dp 的风险（用户明确要求减半 → 仍按 3dp 实现，但真机上要留意）：
 * 从屏幕右缘往里滑是系统的返回/侧滑手势区（各家 ROM 大约 20–24dp），
 * 条现在几乎贴着右缘，条本身大半落在手势区里，手指落在条右侧时可能被系统抢走
 * （单击仍能命中，因为 48dp 命中区一直延伸到左边）。
 * 本次"光"也刻意只往**条左侧**溢出（见 [HANDLE_GLOW_BLOOM_LIMIT_RATIO]），
 * 就是为了不把光画到 3dp 之外的屏幕边缘手势区上。
 */
private const val HANDLE_EDGE_GAP_DP = 3

// ───────────────────────── 科幻流光（§流光）参数 ─────────────────────────
//
// 一眼参数表（真机上调观感只改这里）：
// - 速度：HANDLE_GLOW_PERIOD_MS = 2600ms 扫一遍（原 2200ms 偏"温吞"，调快一点更有"AI 在跑"的感觉）
// - 呼吸：同一个 phase 复用，按 1200ms 一个"拍"（700ms 亮 / 500ms 暗，见 [GLOW_BEAT_PERIOD]）
//   —— 故意和扫描周期**不同频**，避免"光到哪亮到哪"的僵硬感
// - 主带：亮度拉满，HANDLE_GLOW_CORE_HALF_SCALE 让它比条还宽 → "过曝"的通光感
// - 拖尾：3 条，间距 0.62×条宽、alpha 每次 ×0.55 衰减、色相每次 +0.10（青→蓝→紫）
// - 辉光：HANDLE_GLOW_BLOOM_*，把光"漏"到条外的关键（也是"存在感"的主要来源）

/** 流光周期：2.6s 扫一遍。比原来略快，扫动本身才有"速度感"。 */
private const val HANDLE_GLOW_PERIOD_MS = 2600

/**
 * 扫动的额外行程（以条宽为单位）。
 *
 * 1.5 → 主带中心在 `-0.5w … 1.5w` 之间走：起止都**完全在条外**。
 * 为什么不干脆 0（只在条内走）：那样"扫进来/扫出去"各占掉半个周期、光看起来是"弹"出来的；
 * 现在一个周期里光有明确的进入 → 经过 → 离开，速度感更连续。
 */
private const val HANDLE_GLOW_TRAVEL_SCALE = 1.5f

/**
 * 主带半宽 = 条宽的这个比例。
 *
 * §体验修正：原来 0.55 —— 主带比条宽一点点，而渐变是"两端透明"的，
 * 于是条内只有中间一小段真的亮（条宽 9dp、主带可见部分 ≈ 4.5dp），
 * 这就是"看不见"的直接原因之一。
 * 现在 1.3（主带 2.6×条宽，只有最外缘才透明）→ **整条都被灌亮**，观感即"过曝通光"。
 */
private const val HANDLE_GLOW_CORE_HALF_SCALE = 1.3f

/** 拖尾半宽 = 条宽的这个比例（比主带窄 → 宽亮核 + 细余晖，层次拉得开）。 */
private const val HANDLE_GLOW_TRAIL_HALF_SCALE = 0.45f

/** 拖尾条数（含主带）。1 主带 + 2 余晖 = 3，再多在 9dp 上就糊成一片了。 */
private const val HANDLE_GLOW_BAND_COUNT = 3

/** 拖尾之间的间距 = 条宽的这个比例。 */
private const val HANDLE_GLOW_SPACING_SCALE = 0.70f

/** 拖尾 alpha 的递减系数：第 i 条 = 主带 × 0.55^i。 */
private const val HANDLE_GLOW_TRAIL_DECAY = 0.55f

/** 主带峰值不透明度（还会再乘呼吸包络 `beat`）。拉高到 1.0：用户反馈"看不见"，宁可过曝。 */
private const val HANDLE_GLOW_CORE_ALPHA = 1.0f

/**
 * 外层辉光的不透明度峰值（同样乘 `beat`），再按 [HANDLE_GLOW_BLOOM_LAYER_ALPHAS] 逐层递减。
 *
 * §体验修正：0.55 + 5dp 模糊 + 只往左溢 0–5dp，结果是"光基本还在条里、条外几乎看不见"。
 * 现在提到 0.85 并配 9dp 模糊 + 大幅左溢 + 纵向外扩（见 [HANDLE_GLOW_BLOOM_LAYER_SCALES]
 * 与 [HANDLE_GLOW_BLOOM_SPILL_SCALE]），让条左侧有一圈**一眼能看见**的光环。
 */
private const val HANDLE_GLOW_BLOOM_ALPHA = 0.85f

/** 辉光的模糊半径（越大越柔、越"光晕"）。9dp 在 48dp 命中区里仍然收得住，不会糊成一团。 */
private const val HANDLE_GLOW_BLOOM_RADIUS_DP = 9f

/**
 * 辉光的层宽（以条宽为单位，从内到外）。三层叠出"细芯 → 亮晕 → 大范围柔光"。
 *
 * 关键：**最外层要比条宽大得多**（2.6×9dp ≈ 23dp），这样无论主带在条内哪个位置，
 * 光晕都必然从条的左缘漏出去一大截 —— 这是"存在感"的唯一来源，
 * 因为条的右侧只剩 3dp（见 [HANDLE_EDGE_GAP_DP]），光只能往左走。
 */
private val HANDLE_GLOW_BLOOM_LAYER_SCALES = floatArrayOf(0.9f, 1.7f, 2.6f)

/** 三层辉光的 alpha 系数（乘 [HANDLE_GLOW_BLOOM_ALPHA]）。外层更淡 = 边缘渐隐，不会像硬边方块。 */
private val HANDLE_GLOW_BLOOM_LAYER_ALPHAS = floatArrayOf(1.0f, 0.55f, 0.30f)

/**
 * 辉光往条**上下**外扩多少（以条宽为单位）。
 *
 * §体验修正的关键之一：上一版 bloom 的纵向范围就是条高（28dp），只沿条宽方向扩张，
 * 于是整块光晕基本躺在条里、再被条本体盖掉 —— 条外什么都看不见。
 * 现在光晕比条高出一大截（约 28dp + 2×15dp），横向又只往左，于是形成一个
 * 明显偏向左侧的大光斑，"光从条里漏出来"这件事才看得见。
 */
private const val HANDLE_GLOW_BLOOM_SPILL_SCALE = 1.7f

/**
 * 辉光允许"往条的左侧"溢出多远（以命中区宽度为单位的**左边界**）。
 *
 * ⚠️ 这是安全性参数，不是审美参数。把手窗是 `WRAP_CONTENT`（内容宽 = 48dp 命中区）
 * 且 `gravity = TOP or START`、`x = 0`，所以命中区的右缘就贴屏幕右缘，
 * 条距屏幕右缘只有 [HANDLE_EDGE_GAP_DP]（= 3dp）。
 * 也就是说条的**右侧**只剩 3dp 就到屏幕边缘（再往外就是系统侧滑/返回的手势区），
 * 光绝对不能往右边溢。所以 bloom 的右边界被夹在条的右缘，只让光从条**左侧**漏出去。
 * 0.04 这个比例 ≈ 左边界不越过 x = 1.9dp（离左窗缘还有约 1.9dp 余量）——
 * 比之前更贴边，纯粹是为了在"光只能往左走"的约束下再多挤出一点溢出宽度。
 */
private const val HANDLE_GLOW_BLOOM_LIMIT_RATIO = 0.04f

/** 条圆角（与原来 `RoundedCornerShape(5.dp)` 一致；绘制层是按形状手画的，所以要有这个常量）。 */
private const val HANDLE_BAR_CORNER_DP = 5

/** 条高：原来直接写死在 `size(width, height = 28.dp)` 里，绘制层要按它算矩形。 */
private const val HANDLE_BAR_HEIGHT_DP = 28

/** 条边框宽度（原来 `BorderStroke(1.dp, …)`）。 */
private const val HANDLE_BAR_BORDER_DP = 1f

/**
 * 主带（近白亮核）的亮度目标：accent 提亮到 0.95 左右。
 *
 * §体验修正：原来 value 0.90 + 峰值 alpha 0.9 + 渐变两端透明（条内实际只有中间一段亮），
 * 叠在 alpha 0.72 的条本体上就被压成了"一条淡色带"。现在 near-white 0.95、
 * 峰值 alpha 1.0，并让主带**比条更宽**（[HANDLE_GLOW_CORE_HALF_SCALE]），
 * 让整条都被"灌亮"，而不是只有中间一个尖峰。
 * 仍留 ~0.75 的饱和度：纯白会把主题色彻底盖掉，看起来像"贴了一条白胶带"。
 */
private const val HANDLE_GLOW_CORE_SATURATION = 0.75f
private const val HANDLE_GLOW_CORE_VALUE = 0.95f

/** 拖尾的亮度/饱和度：比主带低一档，于是"主带在过曝、余晖只是有色"。 */
private const val HANDLE_GLOW_TRAIL_SATURATION = 0.92f
private const val HANDLE_GLOW_TRAIL_VALUE = 0.80f

/** 每条拖尾相对前一条的色相漂移（1.0 = 一整圈）。0.10 × 2 条 ≈ 青 → 蓝 → 紫。 */
private const val GLOW_HUE_STEP = 0.10f

/** 蓝色在 HSV 色相上的位置（0.66 = 240°）—— 色相漂移以它为基准，两端各自偏移。 */
private const val GLOW_HUE_BASE = 0.66f

/** 呼吸包络的"拍"长（ms）。和 HANDLE_GLOW_PERIOD_MS **不同频**，见 glowBeat 的注释。 */
private const val GLOW_BEAT_PERIOD = 1200f

/** 一拍里"亮着"的比例：0.58 × 1200 ≈ 0.7s 亮 / 0.5s 暗。 */
private const val GLOW_BEAT_DUTY = 0.58f

/**
 * 暗段最低亮度。
 *
 * §体验修正：原来 0.30 —— 一拍 1200ms 里亮段只占 58%，再乘一个会掉到 0.30 的包络，
 * 实际观感是"大半时间都暗着"，用户说的"不像在发光"很可能就来自这里。
 * 现在抬到 0.62：暗谷仍然比峰谷有变化（呼吸感还在），但**任何时刻都不会低于六成亮**，
 * 符合用户"一直炫光流彩"的原话。
 */
private const val GLOW_BEAT_MIN = 0.62f

/** 亮起用的幂次（>1 = 起得慢一点 = 更像"吸气"）。 */
private const val GLOW_BEAT_ATTACK_POW = 1.6f

/**
 * 呼吸包络：把扫描进度 `t` 当相位用，返回当前亮度系数（GLOW_BEAT_MIN…1）。
 *
 * 为什么复用扫描进度而不是再开一个 `animateFloat`：需求是"单个 float 驱动"，
 * 再开一个无限动画就多一个每帧跑的值；而扫描周期（2600ms）与拍长（1200ms）**不成整数倍**，
 * 两者叠加出来的亮峰/暗谷在视觉上不会同步 → 正是要的"错开、不僵硬"。
 *
 * 形状：亮段前 42% 用 pow 慢起、后半保持接近满亮，暗段下降并保底 [GLOW_BEAT_MIN]。
 * 比正弦"更有呼吸感"（正弦太像均匀闪烁）。
 */
private fun glowBeat(t: Float): Float {
    // `t` 在进入组合的第一帧可能是 1.0（animateFloat 的初值就是 0…1 的端点），
    // 所以先取模再夹紧，避免越界。
    val p = (t % 1f + 1f) % 1f
    val beat = (p / GLOW_BEAT_PERIOD) % 1f
    return if (beat < GLOW_BEAT_DUTY) {
        // 亮：慢起 → 接近满亮。
        val q = beat / GLOW_BEAT_DUTY
        (q / 0.42f).coerceAtMost(1f).pow(GLOW_BEAT_ATTACK_POW)
    } else {
        // 暗：从满亮落回保底值。
        val q = (beat - GLOW_BEAT_DUTY) / (1f - GLOW_BEAT_DUTY)
        (1f - q).pow(1.5f).coerceAtLeast(GLOW_BEAT_MIN)
    }
}

/**
 * 过曝主带的颜色：把 accent（主题色）的亮度拉到 ~0.9。
 * **不引入任何新的颜色资源** —— 颜色全部在代码里从主题色算出来。
 *
 * 为什么把 saturation 压到 0.8：9dp 的窄条上纯白会像"贴了一条白胶带"，
 * 留一点饱和度让色相（青/蓝/紫）还在，观感是"被点亮到快爆"而不是"白色"。
 */
private fun glowCore(accent: Color): Color = accent.lit(
    saturation = HANDLE_GLOW_CORE_SATURATION,
    value = HANDLE_GLOW_CORE_VALUE,
)

/**
 * 第 [index] 条拖尾的颜色：在主带的基础上**降低亮度 + 漂移色相**（青 → 蓝 → 紫）。
 *
 * 为什么在 HSV 里改而不是直接改 RGB：色相漂移是"平移色调"，
 * 用 HSV 一个 `hue` 加法就能表达，也不需要额外的混色工具。
 * `index = 0` 时返回的就是主带颜色（所以主带/余晖共用一条颜色链）。
 *
 * 色相链：`base + 0.05 - index × 0.10` → 0.71 / 0.61 / 0.51
 * （≈ 紫蓝 → 蓝 → 青蓝），跨过蓝色向两端各偏一档。
 */
private fun glowBand(accent: Color, index: Int): Color {
    val f = index.toFloat()
    return accent.lit(
        // 饱和度也微微降一点：越远的余晖越"发白/发雾"，反而更像辉光。
        saturation = (HANDLE_GLOW_TRAIL_SATURATION - f * 0.05f).coerceIn(0f, 1f),
        value = (HANDLE_GLOW_TRAIL_VALUE - f * 0.10f).coerceIn(0f, 1f),
        hueShift = GLOW_HUE_BASE + GLOW_HUE_STEP * 0.5f - f * GLOW_HUE_STEP,
    )
}

/**
 * 把某条"光"的颜色算到 HSV 里再改成目标饱和度/亮度/色相，最后转回 sRGB。
 *
 * 为什么要有这个 helper：需求的"过曝主带 + 递减拖尾 + 色相漂移"三件事在 HSV 里各是一行；
 * 在 sRGB 里要自己写混色曲线，既长又难调。
 * [hueShift] 传 null = 不动色相（主带就是把 accent 提亮到 ~0.9，仍是主题色的色相）。
 *
 * ⚠️ 这里**自己算 HSV**，没有用 `Color.toHsv()`：本仓库锁的 Compose（1.13.0-alpha03）
 * 的 `ui-graphics` 里那个 `Hsv` 返回类型并不稳定/可能已迁走（在 api jar 里没有对应类），
 * 为了不让一个"只为了调个颜色"的辅助函数变成编译风险，就地把换算写全。
 * 纯函数、不分配额外对象（只返回一个 Color）。
 */
private fun Color.lit(
    saturation: Float,
    value: Float,
    hueShift: Float? = null,
): Color {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    // 灰（delta = 0）时色相没有定义：保持 0，此时 saturation 也会是 0，
    // 结果只由 value 决定 —— 不会出现"除以 0"或 NaN。
    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta + 6f) % 6f / 6f
        max == g -> ((b - r) / delta + 2f) / 6f
        else -> ((r - g) / delta + 4f) / 6f
    }
    val targetHue = ((hueShift ?: h) % 1f + 1f) % 1f
    val targetSat = saturation.coerceIn(0f, 1f)
    val targetValue = value.coerceIn(0f, 1f)
    // HSV → RGB（标准分段线性公式）。
    val c = targetValue * targetSat
    val hp = targetHue * 6f
    val x = c * (1f - abs(hp % 2f - 1f))
    val m = targetValue - c
    val (r2, g2, b2) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r2 + m).coerceIn(0f, 1f),
        green = (g2 + m).coerceIn(0f, 1f),
        blue = (b2 + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/** 存下后的脉冲：设计稿 `pippulse 900ms`。 */
private const val HANDLE_PULSE_DURATION_MS = 900
private const val HANDLE_PULSE_SCALE_Y = 0.18f
private const val HANDLE_PULSE_SHIFT_DP = 3f

// ───────────────── 流光可观测性（临时排查用，定位完可整段删） ─────────────────

/**
 * 是否为流光打 debug 日志。
 *
 * ⚠️ 这是**临时**排查开关（起因：用户实测"看不到流光"，但无法判断是"没触发"还是"看不见"）。
 * 默认 true；定位完把这里改 false（或把上面那段 `if (GLOW_DEBUG) { … }` 连同这两个常量一起删）
 * 即可彻底零开销 —— 注意 `false` 时 `if` 里的 `LaunchedEffect` 连组合都不进，不留协程。
 * 日志 tag 固定用 [GLOW_DEBUG_TAG]，用户复现一次后 `adb logcat -s HandleGlow` 即可。
 */
private const val GLOW_DEBUG = true
private const val GLOW_DEBUG_TAG = "HandleGlow"