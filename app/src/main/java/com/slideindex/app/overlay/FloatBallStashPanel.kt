package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.history.HistoryPanelReveal
import com.slideindex.app.overlay.history.HistoryPanelScreen
import com.slideindex.app.overlay.history.StashPanelLaunchState
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

enum class StashPanelInitialTab {
    Stash,
    Clipboard,
}

/**
 * Stash / clipboard side panel. Window lifecycle is handled by [OverlaySidePanelHost].
 */
object FloatBallStashPanel {
    private val sideHost = OverlaySidePanelHost(TAG)

    private var pendingInitialTab: HistoryFloatingTab = HistoryFloatingTab.Stash
    private val requestedTabOrdinal = mutableIntStateOf(HistoryFloatingTab.Stash.ordinal)
    /** 与 [StashPanelLaunchState.epoch] 同步，供 Compose 订阅。 */
    private val searchBootstrapEpoch = mutableIntStateOf(0)

    val isShowing: Boolean get() = sideHost.isShowing

    /**
     * Attaches the panel window below float-ball chrome so opening it later avoids z-order bumps.
     */
    fun warmUpBelowChrome(context: android.content.Context) {
        if (sideHost.isAttached) return
        sideHost.attachHidden(
            context = context,
            initialGravityEnd = true,
            content = ::PanelContent,
            dragReveal = { HistoryPanelReveal.dragSession },
            // 窗口跟手：进度给宿主，由它换算成窗口左边缘坐标（窗口只有 78% 宽，见 §0.16.3）。
            revealProgress = { HistoryPanelReveal.windowProgress }
        )
    }

    fun show(
        context: android.content.Context,
        initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash,
        panelSide: PanelSide? = null,
        searchQuery: String? = null
    ): Boolean {
        // 这是"点击/深链打开"这条路：清掉拖动会话，入场交给宿主的滑入动画。
        HistoryPanelReveal.dragSession = false
        HistoryPanelReveal.dragging = false
        HistoryPanelReveal.retractPending = false
        HistoryPanelReveal.open = true
        pendingInitialTab = initialTab.toHistoryFloatingTab()
        requestedTabOrdinal.intValue = pendingInitialTab.ordinal
        val q = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        // 先 show（把 targetVisible=true），再写入 pending/epoch，
        // 避免退出动画中的旧组合在 visible=false 时先抢 consume。
        val shown = sideHost.show(
            context = context,
            initialGravityEnd = panelSide.toStashPanelGravityEnd(),
            content = ::PanelContent,
            dragReveal = { HistoryPanelReveal.dragSession },
            // 窗口跟手：进度给宿主，由它换算成窗口左边缘坐标（窗口只有 78% 宽，见 §0.16.3）。
            revealProgress = { HistoryPanelReveal.windowProgress }
        )
        if (shown && q != null) {
            StashPanelLaunchState.setPendingSearch(
                tabOrdinal = pendingInitialTab.ordinal,
                query = q
            )
            searchBootstrapEpoch.intValue = StashPanelLaunchState.epoch
        }
        return shown
    }

    /**
     * 跟手拉出：手指在把手上横向拖过阈值时调用（把手侧 `HistoryFloatContent`）。
     *
     * **先把共享状态置上再 show** —— 面板要从屏幕外开始跟手，不能先自己滑进来。
     * 拖动期间面板窗切到"看得见但不吃触摸"，否则它（MATCH_PARENT）会把后续 MOVE 从把手手里抢走。
     */
    fun beginDragReveal(context: android.content.Context): Boolean {
        if (sideHost.isShowing) return false
        HistoryPanelReveal.dragSession = true
        HistoryPanelReveal.dragging = true
        HistoryPanelReveal.dragProgress = 0f
        // 窗口位置由 `windowProgress` 驱动；先显式置 0，保证窗口**第一帧就在屏外**
        // （面板的组合还没跑，值可能还是上一次的 1 ✗）。
        HistoryPanelReveal.windowProgress = 0f
        HistoryPanelReveal.retractPending = false
        HistoryPanelReveal.open = true
        val shown = sideHost.show(
            context = context,
            initialGravityEnd = true,
            content = ::PanelContent,
            dragReveal = { HistoryPanelReveal.dragSession },
            // 窗口跟手：进度给宿主，由它换算成窗口左边缘坐标（窗口只有 78% 宽，见 §0.16.3）。
            revealProgress = { HistoryPanelReveal.windowProgress }
        )
        if (shown) sideHost.setTouchable(false)
        return shown
    }

    /**
     * 松手：
     * - [commit] = 过半 → 面板弹簧归位（进度 1），窗保持可触摸；
     * - 否则 → 弹簧收回（进度 0），**收回动画播完**再由面板侧叫宿主关窗
     *   （立刻关会看到面板"啪"地消失，而不是滑回去）。
     */
    fun endDragReveal(commit: Boolean) {
        HistoryPanelReveal.dragging = false
        sideHost.setTouchable(true)
        if (commit) {
            HistoryPanelReveal.open = true
        } else {
            HistoryPanelReveal.open = false
            HistoryPanelReveal.retractPending = true
        }
    }

    fun dismiss() {
        HistoryPanelReveal.open = false
        HistoryPanelReveal.dragging = false
        HistoryPanelReveal.retractPending = false
        sideHost.dismiss()
    }

    /** 面板收回动画播完（面板侧回调）：这时才真正关窗。 */
    fun dismissAfterRetract() {
        sideHost.dismiss()
    }

    fun destroy() {
        sideHost.destroy()
        pendingInitialTab = HistoryFloatingTab.Stash
        requestedTabOrdinal.intValue = HistoryFloatingTab.Stash.ordinal
        StashPanelLaunchState.clearPendingSearch()
        searchBootstrapEpoch.intValue = 0
        sideHost.setPanelBackInterceptor(null)
    }

    /** 应用内语言切换后销毁预热壳，下次打开收纳面板时用新 Locale 重建。 */
    fun releaseWarmUpForLocale() {
        destroy()
    }

    fun updateWindowInputActiveForClipboard(active: Boolean) {
        sideHost.setClipboardInputActive(active)
    }

    fun setDragHidden(hidden: Boolean) {
        sideHost.setDragHidden(hidden)
    }

    @Composable
    private fun PanelContent(
        gravityEnd: Boolean,
        panelTargetVisible: Boolean,
        onToggleSide: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        var settings by remember { mutableStateOf(AppSettings()) }
        val settingsFlow = remember(context) {
            OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository?.settings
        }
        LaunchedEffect(settingsFlow) {
            settingsFlow?.collect { settings = it }
        }
        val panelBlurActive = settings.stashPanelBackgroundBlurEnabled
        val blurRadiusDp = settings.stashPanelBackgroundBlurRadiusDp

        // 系统级背景模糊（API 31+，`FLAG_BLUR_BEHIND` + `setBackgroundBlurRadius`）：
        // 合成器**每帧**重算，所以窗口跟手移动时也是实时模糊 —— 这正是面板内那层自绘
        // 系统级背景模糊（§0.16.3）已按用户要求整批回退：面板内继续用 App 自绘的磨砂罩
        // （`HistoryPanelScreen` 里的 `LocalFrostedGlassBackdrop`），窗口也不再压窄/移动。

        OverlayAwareModuleTheme {
            HistoryPanelScreen(
                gravityEnd = gravityEnd,
                panelTargetVisible = panelTargetVisible,
                panelBlurActive = panelBlurActive,
                blurRadiusDp = blurRadiusDp,
                onDismiss = onDismiss,
                onToggleSide = onToggleSide,
                requestedTabOrdinal = requestedTabOrdinal,
                searchBootstrapEpoch = searchBootstrapEpoch,
                onSearchFocusChanged = { active ->
                    updateWindowInputActiveForClipboard(active)
                },
                onRegisterBackInterceptor = { interceptor ->
                    sideHost.setPanelBackInterceptor(interceptor)
                }
            )
            DisposableEffect(Unit) {
                onDispose {
                    sideHost.setPanelBackInterceptor(null)
                }
            }
        }
    }

    private fun PanelSide?.toStashPanelGravityEnd(): Boolean = when (this) {
        PanelSide.LEFT -> false
        PanelSide.RIGHT -> true
        PanelSide.BOTTOM, PanelSide.TOP, null -> true
    }

    private fun StashPanelInitialTab.toHistoryFloatingTab(): HistoryFloatingTab = when (this) {
        StashPanelInitialTab.Stash -> HistoryFloatingTab.Stash
        StashPanelInitialTab.Clipboard -> HistoryFloatingTab.Clipboard
    }

    private enum class HistoryFloatingTab {
        Stash,
        Clipboard,
    }

    private const val TAG = "FloatBallStashPanel"
}
