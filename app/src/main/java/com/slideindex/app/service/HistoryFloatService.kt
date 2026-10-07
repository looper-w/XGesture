package com.slideindex.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.HistoryFloatHandleGestureExclusion
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.history.HistoryFloatContent
import com.slideindex.app.overlay.history.HistoryNoteSlotWindow
import com.slideindex.app.overlay.history.HistorySavePeekWindow
import com.slideindex.app.overlay.history.HistorySaveSignal
import com.slideindex.app.settings.HistoryFloatHandlePosition
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFloatService : Service() {
    @Inject lateinit var deps: AppDependencies

    private lateinit var windowManager: WindowManager
    private lateinit var mainParams: LayoutParams
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var handleVisible by mutableStateOf(true)
    private var handleWidth by mutableIntStateOf(HistoryFloatHandleWidth.DEFAULT_DP)
    private var lockLoc = true
    private var landscapeEnabled = false
    private var positionY = 0
    private var viewAdded = false
    private var hiddenForFullscreen = false
    private var hiddenForLandscape = false
    private var hiddenForScreenOff = false
    /** 有待办未完成 → 把手整条变色（不出数字、不加宽）。 */
    private var handleAlert by mutableStateOf(false)
    /** 存下后的内容预览（懒创建：没人存东西就不建窗）。 */
    private var peekWindow: HistorySavePeekWindow? = null
    /** 长按把手的就地输入槽（懒创建）。 */
    private var slotWindow: HistoryNoteSlotWindow? = null
    /** [HistorySaveSignal] 的普通回调（Service 里没有组合上下文）。 */
    private val saveListener: (String) -> Unit = { text ->
        // 把手自己都被藏起来时（全屏/横屏/息屏）不要凭空冒出一个预览。
        if (!hiddenForFullscreen && !hiddenForLandscape && !hiddenForScreenOff) {
            ensurePeekWindow().show(text, handleCenterY())
        }
    }
    private val visibleDisplayFrame = Rect()
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 息屏时把手没必要留在屏上（对照 ClipboardFloatService 的做法）。 */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                hiddenForScreenOff = true
                applyFloatVisibility()
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                hiddenForScreenOff = false
                applyFloatVisibility()
            }
        }
    }
    private val fullscreenCheckRunnable = object : Runnable {
        override fun run() {
            updateFullscreenVisibility()
            refreshHandleAlert()
            mainHandler.postDelayed(this, FULLSCREEN_CHECK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mainParams = LayoutParams()
        val overlayContext = OverlayCompose.themedContext(this)
        val owner = OverlayComposeOwner()
        composeOwner = owner
        composeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
            setContent {
                HistoryFloatContent(
                    handleVisible = handleVisible,
                    handleAlert = handleAlert,
                    onOpenPanel = { openClipboardPanel() },
                    onMoveHandle = { moveHandle(it) },
                    onMoveHandleEnd = { persistHandlePosition() },
                    // 跟手拉出：横向拖过阈值 → 面板窗从屏幕外开始跟着手指走（见 HistoryPanelReveal）。
                    onRevealStart = { StashCoordinator.beginHandleReveal(this@HistoryFloatService) },
                    onRevealEnd = { commit -> StashCoordinator.endHandleReveal(commit) },
                    // 长按 = 就地记一条（设计稿 `.slot`），不再只是"打开面板"。
                    onQuickNote = { showNoteSlot() },
                )
            }
        }
        HistorySaveSignal.addListener(saveListener)
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOCK_POSITION -> {
                lockLoc = intent.getBooleanExtra(EXTRA_LOCK_POSITION, lockLoc)
                return START_STICKY
            }
            ACTION_SET_HANDLE_WIDTH -> {
                handleWidth = intent.getIntExtra(
                    EXTRA_HANDLE_WIDTH_DP,
                    HistoryFloatHandleWidth.DEFAULT_DP
                )
                return START_STICKY
            }
            ACTION_SET_LANDSCAPE_ENABLED -> {
                landscapeEnabled = intent.getBooleanExtra(EXTRA_LANDSCAPE_ENABLED, landscapeEnabled)
                updateLandscapeVisibility()
                return START_STICKY
            }
        }
        handleWidth = intent?.getIntExtra(EXTRA_HANDLE_WIDTH_DP, handleWidth) ?: handleWidth
        lockLoc = intent?.getBooleanExtra(EXTRA_LOCK_POSITION, lockLoc) ?: lockLoc
        landscapeEnabled = intent?.getBooleanExtra(EXTRA_LANDSCAPE_ENABLED, landscapeEnabled) ?: landscapeEnabled
        showFloatWindow()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(fullscreenCheckRunnable)
        runCatching { unregisterReceiver(screenOffReceiver) }
        HistorySaveSignal.removeListener(saveListener)
        slotWindow?.destroy()
        slotWindow = null
        peekWindow?.destroy()
        peekWindow = null
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        OverlayCompose.teardownOverlayCompose(composeView, composeOwner)
        composeOwner = null
        composeView = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (viewAdded) {
            applyHandlePosition()
            composeView?.let { windowManager.updateViewLayout(it, mainParams) }
        }
        updateLandscapeVisibility()
        updateFullscreenVisibility()
    }

    private fun showFloatWindow() {
        val view = composeView ?: return
        if (!Settings.canDrawOverlays(this) || viewAdded) {
            return
        }

        mainParams.type = OverlayWindowTypes.overlayWindowType(this)
        mainParams.format = PixelFormat.RGBA_8888
        mainParams.width = LayoutParams.WRAP_CONTENT
        mainParams.height = LayoutParams.WRAP_CONTENT
        mainParams.flags = BASE_WINDOW_FLAGS
        mainParams.gravity = Gravity.END or Gravity.TOP
        OverlayWindowTypes.ensureNoBrightnessOverride(mainParams)
        applyHandlePosition()
        windowManager.addView(view, mainParams)
        viewAdded = true
        // 把手落在右侧 48dp 系统返回手势区里，必须为**自己这块足迹**申请排除区，
        // 否则用户想从把手位置返回时会滑不动（其上下方的返回手势照常可用）。
        HistoryFloatHandleGestureExclusion.attach(view)
        view.post {
            if (!viewAdded) return@post
            applyHandlePosition()
            runCatching { windowManager.updateViewLayout(view, mainParams) }
        }
        updateLandscapeVisibility()
        updateFullscreenVisibility()
        mainHandler.removeCallbacks(fullscreenCheckRunnable)
        mainHandler.post(fullscreenCheckRunnable)
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun storedHandleY(): Int {
        val snapshot = deps.settingsRepository.readSnapshot()
        return if (isLandscape()) {
            snapshot.clipboardHistoryFloatHandleYLandscape
        } else {
            snapshot.clipboardHistoryFloatHandleYPortrait
        }
    }

    private fun estimateHandleHeightPx(): Int {
        val measured = composeView?.height?.takeIf { it > 0 }
        if (measured != null) return measured
        return (96f * resources.displayMetrics.density).roundToInt()
    }

    private fun screenHeightPx(): Int = resources.displayMetrics.heightPixels

    private fun applyHandlePosition() {
        positionY = HistoryFloatHandlePosition.resolveY(
            storedY = storedHandleY(),
            screenHeightPx = screenHeightPx(),
            handleHeightPx = estimateHandleHeightPx()
        )
        mainParams.x = 0
        mainParams.y = positionY
    }

    private fun moveHandle(dy: Float) {
        val view = composeView ?: return
        if (lockLoc || !viewAdded) {
            return
        }
        positionY = HistoryFloatHandlePosition.clampY(
            y = positionY + dy.roundToInt(),
            screenHeightPx = screenHeightPx(),
            handleHeightPx = estimateHandleHeightPx()
        )
        mainParams.x = 0
        mainParams.y = positionY
        windowManager.updateViewLayout(view, mainParams)
    }

    private fun persistHandlePosition() {
        if (lockLoc || !viewAdded) {
            return
        }
        val landscape = isLandscape()
        val y = positionY
        deps.applicationScope.launch {
            deps.settingsRepository.setClipboardHistoryFloatHandleY(y, landscape)
        }
    }

    private fun openClipboardPanel() {
        StashCoordinator.openClipboardPanel(applicationContext)
    }

    /** 把手纵向中心（px）：peek 与输入槽都贴着它对齐。 */
    private fun handleCenterY(): Int = positionY + estimateHandleHeightPx() / 2

    /** 长按把手：弹出就地输入槽（懒创建窗口）。 */
    private fun showNoteSlot() {
        val window = slotWindow ?: HistoryNoteSlotWindow(this, windowManager).also {
            slotWindow = it
        }
        window.show(handleCenterY())
    }

    private fun ensurePeekWindow(): HistorySavePeekWindow =
        peekWindow ?: HistorySavePeekWindow(this, windowManager).also { peekWindow = it }

    /**
     * 有待办未完成 → 把手变色。
     *
     * 数据来自 [StashAccess.metaRepository]（标签绑定 + 完成态）。这里跟随既有的
     * 500ms 轮询顺手刷新，而不是订阅 Flow —— 因为元数据仓库可能比本 Service 晚初始化，
     * 轮询天然容忍顺序问题，代价也只是一次内存 Map 遍历。
     */
    private fun refreshHandleAlert() {
        val alert = StashAccess.metaRepository?.pendingTodoCount()?.let { it > 0 } ?: false
        if (alert != handleAlert) {
            handleAlert = alert
        }
    }

    private fun updateFullscreenVisibility() {
        if (!viewAdded) {
            return
        }
        val isFullscreen = isSystemFullscreen()
        if (hiddenForFullscreen == isFullscreen) {
            return
        }
        hiddenForFullscreen = isFullscreen
        applyFloatVisibility()
    }

    private fun updateLandscapeVisibility() {
        if (!viewAdded) {
            return
        }
        val isLandscape = isLandscape()
        val shouldHide = isLandscape && !landscapeEnabled
        if (hiddenForLandscape == shouldHide) {
            return
        }
        hiddenForLandscape = shouldHide
        applyFloatVisibility()
    }

    private fun applyFloatVisibility() {
        val view = composeView ?: return
        val hidden = hiddenForFullscreen || hiddenForLandscape || hiddenForScreenOff
        val expectedFlags = if (hidden) {
            BASE_WINDOW_FLAGS or LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            BASE_WINDOW_FLAGS
        }
        if (viewAdded && mainParams.flags != expectedFlags) {
            mainParams.flags = expectedFlags
            windowManager.updateViewLayout(view, mainParams)
        }
        view.alpha = if (hidden) 0f else 1f
        view.visibility = View.VISIBLE
        // 把手藏起来时，贴在它旁边的两个小窗（预览 / 输入槽）也要收掉。
        if (hidden) {
            peekWindow?.hide()
            slotWindow?.hide()
        }
    }

    private fun isSystemFullscreen(): Boolean {
        val view = composeView ?: return false
        view.getWindowVisibleDisplayFrame(visibleDisplayFrame)
        val statusBarHeight = getStatusBarHeight()
        if (statusBarHeight <= 0) {
            return false
        }
        return visibleDisplayFrame.top <= statusBarHeight / 2
    }

    private fun getStatusBarHeight(): Int {
        if (!viewAdded) return 0
        val view = composeView ?: return 0
        return ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
    }

    companion object {
        const val ACTION_LOCK_POSITION = "com.slideindex.app.history_float.LOCK_POSITION"
        const val ACTION_SET_HANDLE_WIDTH = "com.slideindex.app.history_float.SET_HANDLE_WIDTH"
        const val ACTION_SET_LANDSCAPE_ENABLED = "com.slideindex.app.history_float.SET_LANDSCAPE_ENABLED"
        const val EXTRA_HANDLE_WIDTH_DP = "handle_width_dp"
        const val EXTRA_LOCK_POSITION = "lock_position"
        const val EXTRA_LANDSCAPE_ENABLED = "landscape_enabled"

        private const val BASE_WINDOW_FLAGS =
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_HARDWARE_ACCELERATED
        private const val FULLSCREEN_CHECK_INTERVAL_MS = 500L
    }
}
