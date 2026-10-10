package com.slideindex.app.util

import android.content.Context

/**
 * Synchronous mirror of [com.slideindex.app.settings.AppSettings.predictiveBackEnabled] for
 * `Application.attachBaseContext` — 见 [PredictiveBackHelper.applyFromStartupSetting]。
 *
 * 为什么需要它：这个开关要在**进程最早**被读一次，用来写 `ApplicationInfo` 上
 * `FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK`；而它决定 `ViewRootImpl` 给返回键派发哪条路，
 * **视图挂载后就改不动了**。但启动路径上不能同步读 DataStore（冷启读可能要好秒，
 * 会把前台服务的 `startForeground` 5 秒窗口挤爆 —— 同 `AppLocaleApplier.primeFromStorage`
 * 注释里的真机事故），所以照 [ServiceEnabledStore] 的做法落一个极轻的 SharedPreferences 镜像，
 * 写入用 [commit] 保证立刻落盘。
 *
 * ⚠️ 还没写过（首次安装 / 覆盖安装后第一次启动）时 [readOrNull] 返回 `null`，
 * 调用方按默认 `false`（= 关预测性返回、返回走注入键）处理 —— 与修复前的默认口径一致。
 */
object PredictiveBackStartupStore {
    private const val PREFS_NAME = "predictive_back_startup_mirror"
    private const val KEY_PREDICTIVE_BACK_ENABLED = "predictive_back_enabled"

    @Volatile
    private var memoryCache: Boolean? = null

    /** @return 镜像值；从未写过 → `null`（调用方自行决定退回口径） */
    fun readOrNull(context: Context): Boolean? {
        memoryCache?.let { return it }
        // ⚠️ 必须用传进来的 context 本身，**不能**用 context.applicationContext：
        // 本方法会在 `Application.attachBaseContext` 里被调用，那个时点 Application 还没 attach 完，
        // `getApplicationContext()` 返回 null（真机启动即崩：NPE on getSharedPreferences）。
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_PREDICTIVE_BACK_ENABLED)) return null
        return prefs.getBoolean(KEY_PREDICTIVE_BACK_ENABLED, false).also { memoryCache = it }
    }

    fun write(context: Context, enabled: Boolean) {
        memoryCache = enabled
        // 同上：不要走 applicationContext（本类的读路径在 attachBaseContext 里）。
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREDICTIVE_BACK_ENABLED, enabled)
            .commit()
    }
}
