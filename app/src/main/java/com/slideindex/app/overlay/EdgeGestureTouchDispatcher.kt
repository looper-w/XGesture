package com.slideindex.app.overlay

import android.view.MotionEvent
import com.slideindex.app.copy.UniversalCopyOverlay
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.gesture.GestureSession

internal class EdgeGestureTouchDispatcher(
    private val gestureSession: GestureSession,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val taskSwitcherController: TaskSwitcherOverlayController,
    private val indexPanelRenderer: IndexPanelRenderer,
    private val gestureAnimationCoordinator: GestureAnimationCoordinator,
    private val rawToLocal: (Float, Float) -> Pair<Float, Float>,
    private val forEachGesturePoint: (
        MotionEvent,
        Float,
        Float,
        Boolean,
        (Float, Float, Float, Float) -> Unit
    ) -> Unit,
    private val onGestureTrackingStart: () -> Unit,
    private val onSyncZoneLayout: () -> Unit,
    private val onForceRecoverInteractionState: () -> Unit,
    private val edgeCaptureTouchActive: () -> Boolean,
    private val setEdgeCaptureTouchActive: (Boolean) -> Unit,
    private val composeOverlayDialogShowing: () -> Boolean
) {
    fun beginCaptureStripTouch(
        handleId: String,
        rawX: Float,
        rawY: Float,
        localX: Float,
        localY: Float
    ): Boolean {
        if (UniversalCopyOverlay.isShowing) return false
        if (composeOverlayDialogShowing()) return false
        if (gestureSession.panelMode() != OverlayPanelMode.NONE && !gestureSession.isActive()) {
            EdgeDiag.logStack(
                "touch",
                "捕获条 DOWN：面板 ${gestureSession.panelMode()} 仍是 leave-open → " +
                    "forceReset(notifySessionEnd=true)（会直接把面板关掉）"
            )
            gestureSession.forceReset(notifySessionEnd = true)
        }
        if (gestureSession.isActive()) {
            shellCoordinator.closePanelTrampolineIfContinuous()
            gestureSession.forceReset(notifySessionEnd = false)
        }
        if (!gestureSession.onTouchDownForHandle(handleId, rawX, rawY, localX, localY)) {
            return false
        }
        FloatingPointerAreaPreviewOverlay.onEdgeTriggerTouch(rawX, rawY)
        setEdgeCaptureTouchActive(true)
        onSyncZoneLayout()
        onGestureTrackingStart()
        gestureAnimationCoordinator.onTouchDown(rawX, rawY)
        return true
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (UniversalCopyOverlay.isShowing) return false
        if (composeOverlayDialogShowing()) return false
        val (localX, localY) = rawToLocal(event.rawX, event.rawY)
        EdgeDiag.log(
            "route",
            "handleTouch action=${MotionEvent.actionToString(event.actionMasked)} " +
                "panelMode=${gestureSession.panelMode()} active=${gestureSession.isActive()} " +
                "edgeTouchActive=${edgeCaptureTouchActive()} raw=(${event.rawX},${event.rawY})"
        )
        // 调节面板停在屏幕上等待交互时，触摸优先交给它。
        //
        // 这里**不能**沿用 `!gestureSession.isActive()`：面板显示时
        // `AdjustPanelOverlayController.showAdjustPanel()` 会调 `host.onSessionStart()`，
        // 会话因此保持 active，于是面板出现后的第一次按下会掉进下面的
        // `gestureSession.isActive() -> forceReset(...)` 分支被吞掉（只 reset、不返回 true），
        // 表现为"面板上第一次滑没反应、第二次才行"（ColorOS 实测：handleTouch handled=false）。
        // 边缘手势本身走 beginCaptureStripTouch（那条路径自己会处理 active 会话），不受影响。
        if (adjustPanelController.hasAdjustPanel() && !edgeCaptureTouchActive()) {
            if (adjustPanelController.handleTouch(event, localX, localY)) return true
        }
        when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER ->
                return quickLauncherController.handleTouch(event, localX, localY)
            OverlayPanelMode.SHELL_COMMANDS ->
                return shellCoordinator.handleTouch(event, localX, localY)
            OverlayPanelMode.TASK_SWITCHER ->
                return taskSwitcherController.handleTouch(event, localX, localY)
            OverlayPanelMode.INDEX -> return indexPanelRenderer.handleTouch(event, localX, localY)
            OverlayPanelMode.NONE -> Unit
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    adjustPanelController.hasAdjustPanel() &&
                        !gestureSession.isActive() &&
                        !adjustPanelController.isDismissing() ->
                        onForceRecoverInteractionState()
                    gestureSession.panelMode() != OverlayPanelMode.NONE && !gestureSession.isActive() ->
                        gestureSession.forceReset(notifySessionEnd = true)
                    gestureSession.isActive() -> {
                        shellCoordinator.closePanelTrampolineIfContinuous()
                        gestureSession.forceReset(notifySessionEnd = false)
                    }
                }
                if (gestureSession.onTouchDown(event.rawX, event.rawY, localX, localY)) {
                    FloatingPointerAreaPreviewOverlay.onEdgeTriggerTouch(event.rawX, event.rawY)
                    setEdgeCaptureTouchActive(true)
                    onSyncZoneLayout()
                    onGestureTrackingStart()
                    gestureAnimationCoordinator.onTouchDown(event.rawX, event.rawY)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!edgeCaptureTouchActive()) return false
                if (!gestureSession.isActive()) {
                    gestureAnimationCoordinator.dismissForContinuedOverlayHandoff()
                    forwardContinuedOverlayTouch(event)
                    return true
                }
                forEachGesturePoint(event, localX, localY, false) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    if (isContinuedOverlayTouchActive()) {
                        gestureAnimationCoordinator.dismissForContinuedOverlayHandoff()
                    } else {
                        gestureAnimationCoordinator.onTouchMove(rawX, rawY)
                    }
                }
                if (isContinuedOverlayTouchActive()) {
                    gestureAnimationCoordinator.dismissForContinuedOverlayHandoff()
                    forwardContinuedOverlayTouch(event)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!gestureSession.isActive()) {
                    val consumed = edgeCaptureTouchActive()
                    val handoff = forwardContinuedOverlayTouch(event)
                    if (consumed) {
                        setEdgeCaptureTouchActive(false)
                        gestureAnimationCoordinator.onTouchUp(event.rawX, event.rawY)
                    }
                    return consumed || handoff || event.actionMasked == MotionEvent.ACTION_CANCEL
                }
                setEdgeCaptureTouchActive(false)
                val canceled = event.actionMasked == MotionEvent.ACTION_CANCEL
                val continuedHandoff = isContinuedOverlayTouchActive()
                if (continuedHandoff) {
                    forwardContinuedOverlayTouch(event)
                    gestureAnimationCoordinator.onTouchCanceled()
                    gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                    return true
                }
                forEachGesturePoint(event, localX, localY, true) { rawX, rawY, lx, ly ->
                    gestureSession.onTouchMove(rawX, rawY, lx, ly)
                    gestureAnimationCoordinator.onTouchMove(rawX, rawY)
                }
                gestureAnimationCoordinator.onTouchUp(event.rawX, event.rawY)
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                forwardContinuedOverlayTouch(event)
                if (canceled) {
                    gestureAnimationCoordinator.onTouchCanceled()
                }
                return true
            }
        }
        return false
    }

    private fun isContinuedOverlayTouchActive(): Boolean =
        EdgeContinuedOverlayLaunchCoordinator.isHandoffActive() ||
            FloatingPointerOverlayWindow.isConsumingEdgeGestureTouch() ||
            RegionalPickOverlay.isConsumingEdgeGestureTouch()

    private fun forwardContinuedOverlayTouch(event: MotionEvent): Boolean {
        if (EdgeContinuedOverlayLaunchCoordinator.onEdgeMoveWhileHandoff(event)) return true
        if (RegionalPickOverlay.forwardContinuedTouch(event)) return true
        return FloatingPointerOverlayWindow.forwardContinuedTouch(event)
    }
}
