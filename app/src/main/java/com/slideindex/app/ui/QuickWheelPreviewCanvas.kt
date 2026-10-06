package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.layout.QuickWheelLayout
import com.slideindex.app.overlay.layout.QuickWheelAdaptiveScreen
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelPlacedSlot
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.overlay.quickwheel.PlacedContainer
import com.slideindex.app.overlay.quickwheel.QuickWheelAddContainer
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.QuickWheelSlot
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private val PreviewHintPillBackground = Color(0xCC202020)

/** 「有二级容器」的一级容器右上角红点。 */
private val SecondaryDotSize = 8.dp
private val SecondaryDotColor = Color(0xFFE53935)

/**
 * 构造「预览 / 画布」用的布局。
 *
 * 与浮层共用 [QuickWheelLayoutEngine]，区别是按调用方给定的锚点铺开（不做屏幕裁剪），
 * 便于配置页实时预览与画布页编辑。
 */
fun buildQuickWheelLayout(
    wheel: QuickWheel,
    expandedPrimaryIndex: Int,
    anchorX: Float,
    anchorY: Float,
    density: Float,
    screenWidthPx: Float,
    /** 屏幕高（px）：二级要按屏幕求解，只给宽度会漏掉上下越界。 */
    screenHeightPx: Float,
    /**
     * 本页在屏幕中的原点（px）：布局坐标是**本地坐标**（锚点已扣掉它），
     * 所以二级求解也必须用"本地可用区域"，否则会出现"预览里贴边、真机却越界"。
     */
    screenOriginX: Float = 0f,
    screenOriginY: Float = 0f,
    /**
     * 末尾是否额外算出一个「+」追加位（编辑层用），其 `index = 真实数量`。
     *
     * ⚠️ 追加位**不参与**真实容器的排布：真实容器位置与不追加时逐个完全一致
     * （编辑层因此与「轮盘预览 / 真实呼出」共用同一套布局）。
     */
    appendPrimaryAddSlot: Boolean = false,
    appendSecondaryAddSlot: Boolean = false,
    /**
     * 非 null 时用它替代"被展开容器真实的子容器数量"。
     *
     * 配置页调**二级**外观参数时用：配合 [QuickWheelLayoutEngine.pickPreviewSecondaryParentIndex]
     * 选出的父容器格位，就会显示一组样本二级容器（圆形 6 个 / 矩形 2 行），参数一动即可见效果。
     */
    previewSecondarySlots: Int? = null,
): QuickWheelLayout {
    val visible = wheel.slots
    val secondaryCount = previewSecondarySlots
        ?: (visible.getOrNull(expandedPrimaryIndex)?.subSlots?.size ?: 0)
    return QuickWheelLayoutEngine.buildLayout(
        primaryCount = visible.size,
        secondaryCount = secondaryCount,
        expandedPrimaryIndex = expandedPrimaryIndex,
        primaryShape = wheel.primaryShape,
        secondaryShape = wheel.secondaryShape,
        primaryStyle = wheel.primaryStyle,
        secondaryStyle = wheel.secondaryStyle,
        anchorX = anchorX,
        anchorY = anchorY,
        density = density,
        screenWidthPx = screenWidthPx,
        // 二级按屏幕求解：一级仍按配置扇区渲染（WYSIWYG），二级与真机一致（锚点 = 父容器中心）。
        secondaryAdaptive = QuickWheelAdaptiveScreen(
            widthPx = (screenWidthPx - screenOriginX).coerceAtLeast(1f),
            heightPx = (screenHeightPx - screenOriginY).coerceAtLeast(1f),
            marginPx = QuickWheelLayoutEngine.ADAPTIVE_MARGIN_DP * density,
        ),
        sectorMask = wheel.sectorMask,
        appendPrimaryAddSlot = appendPrimaryAddSlot,
        appendSecondaryAddSlot = appendSecondaryAddSlot,
    )
}

/**
 * 配置页预览（实时预览 / 「轮盘预览」浮层）该用的锚点。
 *
 * - **圆形**：按"扇区测试选择"**反推理论位置**——选左半圆 → 屏幕右边缘中点，选左下 90° → 右上角，
 *   整圆 → 屏幕正中；这样测试扇区时就能一眼看到"它实际会出现在哪儿"。
 * - **矩形**：屏幕正中（矩形没有扇区测试项，按默认的"居中向下"铺开）。
 *
 * ⚠️ 真实手势呼出**不用**这个锚点：运行时用触发点做锚点，并由引擎自适应求解扇区 / 基准角。
 */
fun quickWheelPreviewAnchor(
    wheel: QuickWheel,
    density: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
): Pair<Float, Float> {
    // 选了什么扇区就按它算理论位置；整圆 / 对角两个这类没有单一朝向的形态退回屏幕正中。
    val mask = QuickWheelLayoutEngine.normalizeSectorMask(wheel.sectorMask)
    return if (wheel.primaryShape == QuickWheelShape.CIRCLE &&
        mask != QuickWheelLayoutEngine.SECTOR_ALL
    ) {
        QuickWheelLayoutEngine.theoreticalAnchorForMask(
            sectorMask = mask,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    } else {
        QuickWheelLayoutEngine.resolveAnchor(
            primaryStyle = wheel.primaryStyle,
            density = density,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }
}

/** 让「整轮盘」收进给定画布所需的缩放比（自动适配预览高度）。 */
fun QuickWheelLayout.fitScale(widthPx: Float, heightPx: Float): Float {
    fun slotExtent(placed: QuickWheelPlacedSlot): Float =
        hypot(placed.centerX - centerX, placed.centerY - centerY) + placed.sizePx / 2f

    val extent = maxOf(
        centerSizePx / 2f,
        primarySlots.maxOfOrNull(::slotExtent) ?: 0f,
        secondarySlots.maxOfOrNull(::slotExtent) ?: 0f,
    )
    val available = minOf(widthPx, heightPx) * 0.92f
    if (extent <= 0f || available <= 0f) return 1f
    return (available / (extent * 2f)).coerceIn(0.25f, 1f)
}

/**
 * 轮盘渲染器：中心容器 + 一级容器（+ 展开时的二级容器）。
 *
 * 手势一律由调用方通过各 `modifier` 注入，本组件只负责摆位与绘制顺序。
 */
@Composable
fun QuickWheelWheelRenderer(
    layout: QuickWheelLayout,
    wheel: QuickWheel,
    modifier: Modifier = Modifier,
    centerModifier: Modifier = Modifier,
    primaryModifier: @Composable (QuickWheelPlacedSlot, QuickWheelSlot) -> Modifier = { _, _ -> Modifier },
    secondaryModifier: @Composable (QuickWheelPlacedSlot, QuickWheelSlot) -> Modifier = { _, _ -> Modifier },
    highlightCenter: Boolean = false,
    highlightPrimaryIndex: Int = -1,
    highlightSecondaryIndex: Int = -1,
    /** 预览 / 配置里为 true：用户留出的空位显示为"空白阴影"；真实呼出时保持透明。 */
    showPlaceholders: Boolean = false,
    /** 一级容器若有二级容器，在其右上角画一个红点提示（替代原来的「二级 N」角标）。 */
    showSecondaryDots: Boolean = false,
    /**
     * 允许"没有真实数据的二级槽位"渲染为**样本容器**（外观 = 加号容器）。
     *
     * 配置页调**二级**外观参数时用：样本父容器特意选的是"没有子容器"的格位（不掩盖真实二级），
     * 因此布局里的二级槽位会多于真实数据，这里必须允许兜底，否则样本会被整体跳过
     * （曾导致"调二级参数时预览里看不到样本，像是没生效"）。
     *
     * 样本画成 [QuickWheelAddContainer]（白底细线加号）——与编辑层末尾的"新增容器"同款外观，
     * 一眼看出是虚拟样本而非真实容器；位置 / 尺寸 / 圆角仍取布局的真实几何。
     *
     * 默认 `false` = 保持原行为：没有真实数据的槽位一律不绘制（编辑层末尾的「+」正是靠这条规则
     * 由 `QuickWheelAddContainer` 单独画成加号，位置必须始终稳定）。
     */
    allowDataLessSecondarySlots: Boolean = false,
    /**
     * 展开进度（0 = 还在锚点、1 = 完全就位）。
     *
     * 用 **lambda** 而不是 Float：动画期间由调用方在**布局 / 绘制阶段**读取，避免每帧重组
     * （与 HUD 长按进度同一条约定）。不传 = 恒 1f（预览 / 编辑层直接就位，行为不变）。
     */
    appearProgress: () -> Float = { 1f },
    /**
     * **二级**容器的展开进度（与 [appearProgress] 分开：二级在"长按一级容器把它展开"那一刻才播）。
     *
     * 起点 = 被展开的那个一级容器的中心（二级环围着它铺开）。默认 1f = 直接就位。
     */
    appearSecondaryProgress: () -> Float = { 1f },
    /**
     * 是否绘制中心大圆容器。
     *
     * 中心容器**只在圆形形态存在**（矩形形态恒不绘制）；
     * 且**真实手势呼出时为 false**——中心全透明不显示，但其动作仍可通过命中触发；
     * 预览 / 编辑层用 true 以便查看与编辑。
     */
    showCenter: Boolean = true,
) {
    Box(modifier = modifier) {
        // 中心大圆：仅圆形形态 + 需要显示时绘制。
        if (showCenter && wheel.primaryShape == QuickWheelShape.CIRCLE) {
            PlacedContainer(
                placed = QuickWheelPlacedSlot(
                    index = QuickWheelLayoutEngine.CENTER_INDEX,
                    centerX = layout.centerX,
                    centerY = layout.centerY,
                    sizePx = layout.centerSizePx,
                    cornerPx = layout.centerCornerPx,
                    ringIndex = -1,
                    indexInRing = -1,
                    startAngleDeg = 0f,
                    endAngleDeg = 360f,
                    minRadiusPx = 0f,
                    maxRadiusPx = layout.centerSizePx / 2f,
                ),
                slot = wheel.centerSlot,
                style = wheel.primaryStyle,
                highlighted = highlightCenter,
                // 中心大圆的锚点就是它自己 → 不传 origin，表现为"原地弹出"。
                appearProgress = appearProgress,
                modifier = centerModifier,
                showPlaceholders = showPlaceholders,
            )
        }

        layout.primarySlots.forEach { placed ->
            val slot = wheel.slots.getOrNull(placed.index) ?: return@forEach
            PlacedContainer(
                placed = placed,
                slot = slot,
                style = wheel.primaryStyle,
                highlighted = placed.index == highlightPrimaryIndex,
                appearProgress = appearProgress,
                // 一级：从轮盘锚点（= 呼出点）向外铺开。
                appearOriginX = layout.centerX,
                appearOriginY = layout.centerY,
                modifier = primaryModifier(placed, slot),
                showPlaceholders = showPlaceholders,
            )
        }

        // 有二级容器的一级容器：右上角一个红点（替代原来的「二级 N」角标）。
        if (showSecondaryDots) {
            val dotPx = with(LocalDensity.current) { SecondaryDotSize.toPx() }
            layout.primarySlots.forEach { placed ->
                val slot = wheel.slots.getOrNull(placed.index) ?: return@forEach
                if (slot.subSlots.isEmpty()) return@forEach
                Box(
                    modifier = Modifier
                        .offset {
                            // 红点跟着容器的展开位置走（否则它会先出现在终点、容器随后飞过来）。
                            val p = appearProgress().coerceIn(0f, 1f)
                            val cx = layout.centerX + (placed.centerX - layout.centerX) * p
                            val cy = layout.centerY + (placed.centerY - layout.centerY) * p
                            IntOffset(
                                x = (cx + placed.sizePx / 2f - dotPx).roundToInt(),
                                y = (cy - placed.sizePx / 2f).roundToInt(),
                            )
                        }
                        .size(SecondaryDotSize)
                        .clip(RoundedCornerShape(50))
                        .background(SecondaryDotColor),
                )
            }
        }

        val expanded = layout.expandedPrimaryIndex
        if (expanded >= 0) {
            val expandedSlots = wheel.slots.getOrNull(expanded)?.subSlots.orEmpty()
            // 二级从**被展开的那个一级容器**向外铺开（找不到就退回轮盘锚点）。
            val expandedOrigin = layout.primarySlots.firstOrNull { it.index == expanded }
            layout.secondarySlots.forEach { placed ->
                val slot = expandedSlots.getOrNull(placed.index)
                if (slot == null) {
                    // 样本二级：画成「+」容器（与编辑层末尾的"新增容器"同款外观），
                    // 一眼看出是虚拟样本；位置 / 尺寸 / 圆角仍取布局的真实几何。
                    if (allowDataLessSecondarySlots) PlacedAddContainer(placed = placed)
                    // 否则保持原行为：没有真实数据的槽位不绘制。
                    return@forEach
                }
                PlacedContainer(
                    placed = placed,
                    slot = slot,
                    style = wheel.secondaryStyle,
                    highlighted = placed.index == highlightSecondaryIndex,
                    appearProgress = appearSecondaryProgress,
                    appearOriginX = expandedOrigin?.centerX ?: layout.centerX,
                    appearOriginY = expandedOrigin?.centerY ?: layout.centerY,
                    modifier = secondaryModifier(placed, slot),
                    showPlaceholders = showPlaceholders,
                )
            }
        }
    }
}

/**
 * 把「样本二级」的加号容器摆到布局给出的槽位（摆位规则与 [PlacedContainer] 一致）。
 *
 * 只用于配置页调二级外观参数时的虚拟样本：不参与交互，因此不经过 `secondaryModifier`。
 */
@Composable
private fun PlacedAddContainer(
    placed: QuickWheelPlacedSlot,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (placed.centerX - placed.sizePx / 2f).roundToInt(),
                    (placed.centerY - placed.sizePx / 2f).roundToInt(),
                )
            }
            .then(modifier),
    ) {
        QuickWheelAddContainer(sizePx = placed.sizePx, cornerPx = placed.cornerPx)
    }
}

/** 页面顶部提示条（深色胶囊 + 白字），画布编辑页等使用。 */
@Composable
internal fun QuickWheelHintPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(PreviewHintPillBackground)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 可点选的「一级轮盘扇区环」：4 个 90° 扇区，点选 / 取消点选，至少保留一个。
 *
 * 扇区 0 自正上方起顺时针编号，与 [QuickWheelLayoutEngine] 的布点角度一致。
 */
@Composable
fun QuickWheelSectorRing(
    sectorMask: Int,
    onToggleSector: (Int) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(sectorMask) {
            detectTapGestures { position ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val deltaX = position.x - centerX
                val deltaY = position.y - centerY
                val distance = hypot(deltaX, deltaY)
                val outer = minOf(size.width, size.height) / 2f
                val inner = outer * 0.46f
                if (distance < inner || distance > outer) return@detectTapGestures
                val mathDeg = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
                val normalized = ((mathDeg + 90f) % 360f + 360f) % 360f
                onToggleSector((normalized / QuickWheelLayoutEngine.SECTOR_ANGLE_DEG).toInt().coerceIn(0, 3))
            }
        },
    ) {
        val radius = minOf(size.width, size.height) / 2f * 0.94f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val stroke = radius * 0.5f
        val arcRadius = radius - stroke / 2f
        val sweep = QuickWheelLayoutEngine.SECTOR_ANGLE_DEG
        val gap = 2.4f
        for (index in 0 until QuickWheelLayoutEngine.SECTOR_COUNT) {
            val selected = QuickWheelLayoutEngine.isSectorSelected(sectorMask, index)
            drawArc(
                color = if (selected) activeColor else inactiveColor,
                startAngle = QuickWheelLayoutEngine.SECTOR_START_DEG + sweep * index + gap,
                sweepAngle = sweep - gap * 2f,
                useCenter = false,
                topLeft = Offset(centerX - arcRadius, centerY - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = stroke),
            )
        }
        drawCircle(
            color = outlineColor,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5f),
        )
        drawCircle(
            color = outlineColor,
            radius = radius - stroke,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5f),
        )
    }
}
