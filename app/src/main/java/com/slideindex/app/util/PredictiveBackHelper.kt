package com.slideindex.app.util

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log

object PredictiveBackHelper {
    /**
     * 把"应用级预测性返回"这个开关**运行时**刷成 [enabled]（manifest 里声明的是 true）。
     *
     * ⚠️ §0.16.22：这里原来是**静默** `runCatching`。但它是浮窗返回键的命门：
     * `OverlayViewBackHandler` 的取值口径是"用户设置"（默认 false）→ 它只装 legacy 的
     * "注入 KEYCODE_BACK"监听；而 `ViewRootImpl` 走哪条路要看**本方法有没有真的把
     * ApplicationInfo 那个 flag 改成 false**。改失败（隐藏 API 被拦 / ROM 无此方法）时两边就
     * 对不上：系统按 OnBackInvoked 派发、我们却只等注入按键 → **所有浮窗的返回键静默失效**
     * （真机症状：面板开着按返回毫无反应，点遮罩却能关）。
     * 所以失败**必须留痕**（`adb logcat -s PredictiveBack`），别再默默吞掉。
     */
    fun applyEnabled(appInfo: ApplicationInfo, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(appInfo, enabled)
            Log.i(TAG, "applyEnabled($enabled): 已写入 ApplicationInfo（浮窗返回键走 ${
                if (enabled) "OnBackInvoked" else "注入 KEYCODE_BACK"
            }）")
        }.onFailure { error ->
            Log.w(
                TAG,
                "applyEnabled($enabled) 失败：ApplicationInfo 的预测性返回 flag 保持 manifest 的 true，" +
                    "而 OverlayViewBackHandler 会按用户设置(false)只装 legacy 按键监听 —— " +
                    "两边对不上时所有浮窗的返回键都会失效（真机事故 §0.16.22）",
                error,
            )
        }
    }

    private const val TAG = "PredictiveBack"
}
