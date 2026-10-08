package com.slideindex.app.overlay

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Side-sliding overlay panel host (stash / clipboard history). Wraps [OverlayFullScreenPanelHost]
 * with horizontal enter/exit animation and optional left/right gravity.
 */
class OverlaySidePanelHost(
    private val tag: String = "OverlaySidePanelHost"
) : OverlayPanelVisibility {
    private val panelHost = OverlayFullScreenPanelHost(
        tag = tag,
        layoutParamsFactory = { context, focusable ->
            OverlayPanelLayoutParams.stashClipboardSidePanel(context, focusable)
        },
        onScreenOff = { dismiss() },
        excludeLeftBackEdge = false
    )

    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var panelTargetVisibleState: MutableState<Boolean>? = null
    private var gravityEndState: MutableState<Boolean>? = null
    /**
     * 本次打开是否由「跟手拖动」发起。
     *
     * 是的话**不能**再走滑入/滑出动画：那会和手指驱动的位移叠加，看着就是面板自己往前窜。
     * 由调用方给一个 lambda（host 本身不认识"拖动"这件事，只负责问一句），
     * 内容里用 `snapshotFlow` 盯着它 —— 拖动结束（手指离开、弹簧收尾）后它会自己变回 false，
     * 于是**下一次**关闭又走正常的滑出动画。
     */
    private var dragRevealQuery: () -> Boolean = { false }
    /**
     * 跟手拖出的进度（0 = 完全收起在屏外，1 = 完全拉出）。
     *
     * ⚠️ §0.16.3 曾用它驱动**窗口本身**的 x（`applyPanelX` → `setRevealOffsetPx`），
     * **已被用户打回**：现在窗口满屏且不动，跟手位移由面板内容自己的 `graphicsLayer` 做。
     * 这个查询参数先留着（`show` 的默认值是 `{ 0f }`，没人传就无所谓）。
     */
    private var revealProgressQuery: () -> Float = { 0f }
    /** 算窗口宽度/位置要用（show/attach 时给的宿主 context）。 */
    private var layoutContext: Context? = null
    /** 一次性日志用：拖动路径是否已经离开过最终位置。 */
    private var loggedRevealMove = false
    private var attachedBelowChrome = false
    private var lastShowAttemptElapsedMs = 0L
    private var clipboardInputActive = false
    private var panelBackInterceptor: (() -> Boolean)? = null
    private var backHandler: OverlayViewBackHandler? = null

    override val isAttached: Boolean get() = panelHost.isAttached

    override val isUserVisible: Boolean
        get() = panelHost.isAttached &&
            panelVisibilityState?.currentState == true &&
            panelHost.isViewVisible()

    /** User-visible panel; use [isAttached] for warm-up / attach guards. */
    val isShowing: Boolean get() = isUserVisible

    /**
     * Pre-attaches the panel window (GONE) so float-ball chrome added later stays on top
     * without remove/add z-order bumps.
     */
    fun attachHidden(
        context: Context,
        initialGravityEnd: Boolean = true,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
        dragReveal: () -> Boolean = { false },
        revealProgress: () -> Float = { 0f }
    ): Boolean {
        if (panelHost.isAttached) {
            attachedBelowChrome = true
            return true
        }
        if (!onAccessibilityRequired()) {
            Log.w(tag, "attachHidden: accessibility service not enabled")
            return false
        }
        val hostContext = onHostContext() ?: run {
            Log.w(tag, "attachHidden: overlay host not connected")
            return false
        }
        dragRevealQuery = dragReveal
        revealProgressQuery = revealProgress
        val attached = attachPanelWindow(
            hostContext = hostContext,
            initialGravityEnd = initialGravityEnd,
            content = content
        )
        if (!attached) return false
        panelHost.setViewVisible(false)
        attachedBelowChrome = true
        return attached
    }

    fun show(
        context: Context,
        initialGravityEnd: Boolean = true,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
        onShown: () -> Unit = { FloatBallOverlay.scheduleChromeAbovePanels() },
        dragReveal: () -> Boolean = { false },
        revealProgress: () -> Float = { 0f }
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            panelHost.runOnMain {
                result = show(
                    context = context,
                    initialGravityEnd = initialGravityEnd,
                    content = content,
                    onAccessibilityRequired = onAccessibilityRequired,
                    onHostContext = onHostContext,
                    onShown = onShown,
                    dragReveal = dragReveal,
                    revealProgress = revealProgress
                )
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        dragRevealQuery = dragReveal
        revealProgressQuery = revealProgress

        val now = SystemClock.elapsedRealtime()
        if (now - lastShowAttemptElapsedMs < SHOW_DEBOUNCE_MS) {
            if (panelHost.isAttached) {
                gravityEndState?.value = initialGravityEnd
                setPanelTargetVisible(true)
                panelHost.setViewVisible(true)
                panelHost.composeView?.post {
                    panelVisibilityState?.targetState = true
                }
                notifyPanelShown(onShown)
                return true
            }
            return false
        }
        lastShowAttemptElapsedMs = now

        if (panelHost.isAttached) {
            gravityEndState?.value = initialGravityEnd
            setPanelTargetVisible(true)
            panelHost.setViewVisible(true)
            panelHost.composeView?.post {
                panelVisibilityState?.targetState = true
            }
            notifyPanelShown(onShown)
            return true
        }

        if (!onAccessibilityRequired()) {
            Log.w(tag, "show: accessibility service not enabled")
            return false
        }
        val hostContext = onHostContext() ?: run {
            Log.w(tag, "show: overlay host not connected")
            return false
        }

        val attached = attachPanelWindow(
            hostContext = hostContext,
            initialGravityEnd = initialGravityEnd,
            content = content
        )
        if (!attached) return false
        attachedBelowChrome = false

        setPanelTargetVisible(true)
        panelHost.setViewVisible(true)
        panelHost.composeView?.post {
            panelVisibilityState?.targetState = true
        }
        notifyPanelShown(onShown)
        return attached
    }

    private fun setPanelTargetVisible(visible: Boolean) {
        panelTargetVisibleState?.value = visible
    }

    private fun attachPanelWindow(
        hostContext: Context,
        initialGravityEnd: Boolean,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit
        ) -> Unit
    ): Boolean {
        layoutContext = hostContext
        val gravityEndHolder = mutableStateOf(initialGravityEnd)
        gravityEndState = gravityEndHolder
        val visibleState = MutableTransitionState(false)
        panelVisibilityState = visibleState
        val targetVisibleHolder = mutableStateOf(false)
        panelTargetVisibleState = targetVisibleHolder

        panelHost.ensureWindow(hostContext, focusable = false) {
            val gravityEnd by gravityEndHolder
            val panelTargetVisible by targetVisibleHolder
            val dragRevealHolder = remember { mutableStateOf(dragRevealQuery()) }
            LaunchedEffect(Unit) {
                snapshotFlow { dragRevealQuery() }.collect { dragRevealHolder.value = it }
            }
            val dragReveal by dragRevealHolder
            // 窗口位置曾经在这里跟着拖动进度走（§0.16.3 的移动窗口方案），已被用户打回：
            // 现在窗口不动（满屏），跟手位移由面板内容自己的 graphicsLayer 做。applyPanelX 保留但无人调用。
            AnimatedVisibility(
                visibleState = visibleState,
                enter = if (dragReveal) {
                    // 跟手拖动：面板位置完全由手指/弹簧驱动的 translationX 决定，这里不能再来一次滑入。
                    EnterTransition.None
                } else {
                    slideInHorizontally(
                        initialOffsetX = { if (gravityEnd) it else -it },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                    ) + fadeIn(animationSpec = tween(250))
                },
                exit = if (dragReveal) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { if (gravityEnd) it else -it },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                    ) + fadeOut(animationSpec = tween(250))
                }
            ) {
                content(
                    gravityEnd,
                    panelTargetVisible,
                    { gravityEndHolder.value = !gravityEndHolder.value },
                    { dismiss() }
                )
            }
        } ?: return false

        val composeView = panelHost.composeView ?: return false
        backHandler?.detach()
        backHandler = OverlayViewBackHandler(composeView, ::handlePanelBack).also {
            it.attach(requestViewFocus = false)
        }
        return true
    }

    /**
     * 浏览态（没有输入框 / 浮窗）的窗口输入状态。
     *
     * ⚠️ **必须可聚焦**：`FLAG_NOT_FOCUSABLE` 的窗口**收不到系统返回派发** —— 面板开着时按返回 /
     * 边缘手势返回，事件会落到下面那个 App 上（真机实测："返回把底下界面退了，面板还浮在上面"；
     * 而 `handlePanelBack` 本身是好的，只是没人把事件交给它）。
     *
     * 可聚焦 + `FLAG_ALT_FOCUSABLE_IM` 才是想要的组合：**我们收得到返回，但不把输入法抢过来**
     * —— 这个 flag 的语义正是"可聚焦，但不与输入法交互"（`OverlayFullScreenPanelHost.setAltFocusableIm`）。
     * 要打字时再由 [activatePanelInputFocus] 清掉它、把输入法指向本窗（原有行为不变）。
     */
    private fun ensurePanelBrowsingInput() {
        panelHost.setInputActive(active = true, requestRootFocus = false)
        panelHost.setAltFocusableIm(enabled = true)
        val view = panelHost.composeView ?: return
        val handler = backHandler
        if (handler == null) {
            backHandler = OverlayViewBackHandler(view, ::handlePanelBack).also {
                // 现在是真的要收返回键：视图焦点也一并给上（旧版 OnUnhandledKeyEventListener 那条路要用）。
                it.attach(requestViewFocus = true)
            }
        } else {
            // 窗口刚变成可聚焦，注册得重来一次（`OnBackInvokedCallback` 要窗口有 dispatcher）。
            handler.refresh()
        }
    }

    private fun activatePanelInputFocus() {
        panelHost.setInputActive(active = true, requestRootFocus = true)
    }

    private fun handlePanelBack() {
        if (panelBackInterceptor?.invoke() == true) return
        if (clipboardInputActive) {
            setClipboardInputActive(false)
            return
        }
        dismiss()
    }

    fun setPanelBackInterceptor(interceptor: (() -> Boolean)?) {
        panelBackInterceptor = interceptor
    }

    /**
     * 把「拖动进度」换算成窗口左边缘坐标并套用。
     *
     * 窗口宽 = 屏宽 × [OverlayPanelLayoutParams.SIDE_PANEL_WIDTH_FRACTION]；gravity 是 TOP|START，
     * 所以：
     * - 贴右侧（gravityEnd）：完全拉出 x = 屏宽 − 窗宽，完全收起 x = 屏宽（整个推出右边界）
     * - 贴左侧：完全拉出 x = 0，完全收起 x = −窗宽
     * 中间线性插值 —— 手指拖 130dp 就拉满（进度由把手侧给，见 `PANEL_REVEAL_DRAG_DP`）。
     */
    private fun applyPanelX(progress: Float, gravityEnd: Boolean) {
        val context = layoutContext ?: return
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val panelWidthPx = (screenWidthPx * OverlayPanelLayoutParams.SIDE_PANEL_WIDTH_FRACTION).toInt()
        val visibleX = if (gravityEnd) screenWidthPx - panelWidthPx else 0
        val hiddenX = if (gravityEnd) screenWidthPx else -panelWidthPx
        val p = progress.coerceIn(0f, 1f)
        val x = (hiddenX + (visibleX - hiddenX) * p).toInt()
        // 只在"第一次真的离开最终位置"时打一条：跟手拖动没法在自动化里验，真机排查靠它。
        if (x != visibleX && !loggedRevealMove) {
            loggedRevealMove = true
            android.util.Log.i(tag, "跟手拖动生效：progress=$p windowX=$x (visibleX=$visibleX hiddenX=$hiddenX)")
        } else if (x == visibleX) {
            loggedRevealMove = false
        }
        panelHost.setRevealOffsetPx(x)
    }

    private fun notifyPanelShown(onShown: () -> Unit) {
        FloatBallOverlay.notifyPanelAttachedAboveChrome()
        ensurePanelBrowsingInput()
        onShown()
        panelHost.composeView?.post {
            onShown()
        }
        panelHost.composeView?.postDelayed({
            onShown()
        }, 360L)
    }

    fun dismiss() {
        panelHost.runOnMain {
            if (!panelHost.isAttached) return@runOnMain
            val visibleState = panelVisibilityState
            setPanelTargetVisible(false)
            visibleState?.targetState = false
            val view = panelHost.composeView
            val owner = panelHost.owner
            if (view == null || owner == null || visibleState == null) return@runOnMain
            panelHost.setInputActive(false)
            panelHost.setAltFocusableIm(enabled = true)
            clipboardInputActive = false
            owner.lifecycleScope.launch(Dispatchers.Main) {
                delay(300)
                if (visibleState.targetState) return@launch
                view.visibility = View.GONE
            }
        }
    }

    fun destroy() {
        panelHost.runOnMain {
            backHandler?.detach()
            backHandler = null
            panelHost.destroy()
            panelVisibilityState = null
            panelTargetVisibleState = null
            gravityEndState = null
            attachedBelowChrome = false
            clipboardInputActive = false
            lastShowAttemptElapsedMs = 0L
        }
    }

    fun setInputActive(active: Boolean, requestRootFocus: Boolean = true) {
        panelHost.setInputActive(active, requestRootFocus)
    }

    fun setClipboardInputActive(active: Boolean) {
        // ⚠️ 幂等：重复调用（组合每次重组都会调一次）会**再抢一次根视图焦点**，
        // 把手里的输入框焦点顶掉 → 字段失焦 → 又调回 false，形成乒乓。
        if (active == clipboardInputActive) return
        clipboardInputActive = active
        if (active) {
            activatePanelInputFocus()
        } else {
            panelHost.composeView?.clearFocus()
            ensurePanelBrowsingInput()
        }
    }

    fun setDragHidden(hidden: Boolean) {
        panelHost.setDragHidden(hidden)
    }

    /** 跟手拖动期间面板窗要"看得见但不吃触摸"（见 [OverlayFullScreenPanelHost.setTouchable]）。 */
    fun setTouchable(touchable: Boolean) {
        panelHost.setTouchable(touchable)
    }

    fun updateBackgroundBlur(context: Context, blurRadiusDp: Int): Boolean =
        panelHost.updateBackgroundBlur(context, blurRadiusDp)

    companion object {
        private const val SHOW_DEBOUNCE_MS = 300L
    }
}
