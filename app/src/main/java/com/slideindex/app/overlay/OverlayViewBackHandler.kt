package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.slideindex.app.di.OverlayDependencyAccess

/**
 * Routes system back (gesture + key) to overlay [ComposeView] windows.
 *
 * When predictive back is enabled for the app (API 33+), uses [OnBackInvokedCallback] only.
 * Flyme must not combine that with legacy key listeners: it registers
 * [android.view.ViewRootImpl.registerCompatOnBackInvokedCallback] for them, which loops
 * with [android.view.ViewRootImpl.injectBackKeyEvents].
 *
 * When predictive back is off at the app level, [OnBackInvokedCallback] is not dispatched;
 * legacy [OnUnhandledKeyEventListenerCompat] handles injected [KeyEvent.KEYCODE_BACK].
 *
 * **键盘优先**（§0.16.7）：键盘弹着时，这一次返回先收键盘、**不**交给浮窗自己的 [onBack]。
 * 普通 App 里这一档是输入法自己吃掉的（IME 有返回回调），而我们是 `TYPE_*_OVERLAY` 浮窗、
 * 且应用级 predictive-back 关掉时返回走"注入 `KEYCODE_BACK`"的兼容路径 —— 返回键**直接注给
 * 聚焦的那个浮窗**，输入法没机会先吃，于是"键盘弹着按返回"会直接把弹窗关掉（用户报的就是这个）。
 * 判断与处理都放在 [dispatchBack] 这个唯一漏斗里，所以**所有用它的浮窗一起受益**。
 */
internal class OverlayViewBackHandler(
    private val view: View,
    private val onBack: () -> Unit,
) {
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var unhandledKeyListener: ViewCompat.OnUnhandledKeyEventListenerCompat? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var usesUnhandledKeyBackListener = false
    private var handlingBack = false
    private var registerAttempts = 0
    private var predictiveBackEnabled = false

    fun attach(requestViewFocus: Boolean = true) {
        predictiveBackEnabled = resolvePredictiveBackEnabled(view.context)
        if (requestViewFocus) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
        }
        if (shouldUseOnBackInvoked()) {
            registerAttempts = 0
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    /** Retry registration after the overlay window becomes focusable or predictive-back toggles. */
    fun refresh() {
        predictiveBackEnabled = resolvePredictiveBackEnabled(view.context)
        detachInternal(clearHandlingFlag = false)
        if (shouldUseOnBackInvoked()) {
            registerAttempts = 0
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    private fun shouldUseOnBackInvoked(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && predictiveBackEnabled

    private fun scheduleRegisterOnBackInvoked() {
        fun tryRegister() {
            if (backInvokedCallback != null || usesUnhandledKeyBackListener) return
            val dispatcher = resolveBackInvokedDispatcher()
            if (dispatcher != null) {
                registerOnBackInvoked(dispatcher)
                return
            }
            if (!view.isAttachedToWindow) return
            if (registerAttempts++ < MAX_ON_BACK_REGISTER_ATTEMPTS) {
                view.post { tryRegister() }
            }
        }

        if (view.isAttachedToWindow) {
            view.post { tryRegister() }
            return
        }

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                view.removeOnAttachStateChangeListener(this)
                attachListener = null
                view.post { tryRegister() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        attachListener = listener
        view.addOnAttachStateChangeListener(listener)
    }

    private fun resolveBackInvokedDispatcher(): OnBackInvokedDispatcher? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        view.findOnBackInvokedDispatcher()?.let { return it }
        val root = view.rootView
        if (root !== view) {
            root.findOnBackInvokedDispatcher()?.let { return it }
        }
        return null
    }

    private fun registerOnBackInvoked(dispatcher: OnBackInvokedDispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback { dispatchBack() }
        backInvokedCallback = callback
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
    }

    private fun dispatchBack() {
        if (handlingBack) return
        handlingBack = true
        try {
            // 键盘弹着 → 这一次返回归键盘（见类注释）。收完就消费掉，不给浮窗自己的 onBack。
            if (hideImeAndConsumeBack()) return
            onBack()
        } finally {
            view.post { handlingBack = false }
        }
    }

    /**
     * 键盘弹着就收键盘并返回 `true`（这一次返回被消费掉）。
     *
     * ⚠️ **只收键盘，绝不碰焦点**：`clearFocus()` / 把窗口切回"不与输入法交互"的状态之后，
     * 用户再点同一个输入框，键盘可能就弹不出来了（面板那边的输入态是靠状态变化驱动的）。
     */
    private fun hideImeAndConsumeBack(): Boolean {
        if (!isImeVisible()) return false
        val token = view.windowToken ?: return false
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
        runCatching { imm.hideSoftInputFromWindow(token, 0) }
        return true
    }

    /**
     * 键盘是否弹着。
     *
     * 两步判据（与 `OverlayImeInsets.rememberOverlayImeBottomHeight` 同一套）：
     * ① 窗口 insets 直接报了 IME 高度 → 最权威；
     * ② 覆盖窗上 `WindowInsets.ime` 常常是 0，退回"可见区域比根视图矮一截"。
     *
     * ⚠️ ② 的门槛**不能是 >0**：手势条/导航栏本身就会占掉几十 px，那样键盘没弹也会被误判成
     * "弹着"，于是把第一下返回吞掉（表现成"按返回没反应"）。键盘占屏高 30% 以上，
     * 这里要求超过 [IME_MIN_HEIGHT_FRACTION] 才认。
     */
    private fun isImeVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(view)
        if ((insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0) > 0) return true

        val rootHeight = view.rootView.height
        if (rootHeight <= 0) return false
        val visibleFrame = Rect()
        view.getWindowVisibleDisplayFrame(visibleFrame)
        return rootHeight - visibleFrame.bottom > rootHeight * IME_MIN_HEIGHT_FRACTION
    }

    private fun registerUnhandledKeyBackListener() {
        if (usesUnhandledKeyBackListener) return
        usesUnhandledKeyBackListener = true
        val keyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            if (event.keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@OnUnhandledKeyEventListenerCompat false
            }
            dispatchBack()
            true
        }
        unhandledKeyListener = keyListener
        ViewCompat.addOnUnhandledKeyEventListener(view, keyListener)
    }

    fun detach() {
        detachInternal(clearHandlingFlag = true)
    }

    private fun detachInternal(clearHandlingFlag: Boolean) {
        attachListener?.let { view.removeOnAttachStateChangeListener(it) }
        attachListener = null
        registerAttempts = 0
        if (usesUnhandledKeyBackListener) {
            unhandledKeyListener?.let { listener ->
                ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
            }
            unhandledKeyListener = null
            usesUnhandledKeyBackListener = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                resolveBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
            }
        }
        backInvokedCallback = null
        if (clearHandlingFlag) {
            handlingBack = false
        }
    }

    private companion object {
        private const val MAX_ON_BACK_REGISTER_ATTEMPTS = 12

        /**
         * 「可见区域矮了多少」才算键盘弹着（占根视图高度的比例）。
         *
         * 键盘一般占 30%~45%，手势条/导航栏只有 2%~5%，取 15% 两头都安全。
         */
        private const val IME_MIN_HEIGHT_FRACTION = 0.15f

        private fun resolvePredictiveBackEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
            return OverlayDependencyAccess.overlayDependencies(context.applicationContext)
                ?.settingsRepository
                ?.readSnapshot()
                ?.predictiveBackEnabled
                ?: false
        }
    }
}
