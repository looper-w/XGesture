package com.slideindex.app.util

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.isLandscapeConfiguration
import com.slideindex.app.settings.resolvedFreeWindowLayout
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.usesNubiaFreeformIdentifier

import android.content.ComponentName
import android.content.pm.PackageManager
import com.slideindex.app.service.FreeWindowShareProxyActivity
import com.slideindex.app.settings.FreeWindowMode
import kotlin.math.roundToInt

object FreeWindowLauncher {
    private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
    private const val NUBIA_FREEFORM_INTENT_IDENTIFIER = "_WindowReply"
    private const val HUAWEI_FREEFORM_STACK_ID = 2
    private const val MIUI_PORTRAIT_SCALE = 0.7f
    private const val MIUI_LANDSCAPE_SCALE = 0.555f

    fun launch(context: Context, intent: Intent, settings: AppSettings, fullscreen: Boolean) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!fullscreen && settings.freeWindowEnabled) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (fullscreen || !settings.freeWindowEnabled) {
            context.startActivity(intent)
            return
        }

        val mode = settings.resolvedFreeWindowMode()
        if (mode.usesNubiaFreeformIdentifier()) {
            launchNubiaFreeform(context, intent)
            return
        }

        if (mode == FreeWindowMode.ORIGINOS) {
            if (launchOriginOsShareProxy(context, intent)) {
                return
            }
        }

        val bundle = launchOptionsBundle(context, settings) ?: Bundle()
        runCatching {
            context.startActivity(intent, bundle)
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "startActivity failed", error)
        }
    }

    /**
     * 以小窗启动某个应用（照搬 SideGesture「应用小窗(7.0+)」的做法）：
     * 解析该包的 launcher Activity → `Intent(ACTION_MAIN + CATEGORY_LAUNCHER)` → 一次
     * `startActivity(intent, ActivityOptions)`。
     *
     * 与 [launch] 的差别只有 flags：这里只带 NEW_TASK，不叠加
     * REORDER_TO_FRONT / SINGLE_TOP，也不叠加 MULTIPLE_TASK —— 复用还是新建任务交给系统；
     * 不校验、不重试、不搬移已有任务。
     */
    fun launchPackageInFreeWindow(
        context: Context,
        packageName: String,
        settings: AppSettings,
    ): Boolean {
        if (packageName.isBlank()) return false
        val component = resolveLauncherComponent(context, packageName) ?: return false
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            this.component = component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val mode = settings.resolvedFreeWindowMode()
        if (mode.usesNubiaFreeformIdentifier()) {
            launchNubiaFreeform(context, intent)
            return true
        }
        if (mode == FreeWindowMode.ORIGINOS && launchOriginOsShareProxy(context, intent)) {
            return true
        }

        val bundle = launchOptionsBundle(context, settings) ?: return false
        return runCatching {
            context.startActivity(intent, bundle)
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "launchPackageInFreeWindow($packageName) failed", error)
        }.isSuccess
    }

    /** ACTION_MAIN + CATEGORY_LAUNCHER 查询该包的 launcher Activity（对齐 SideGesture）。 */
    private fun resolveLauncherComponent(context: Context, packageName: String): ComponentName? {
        val query = Intent(Intent.ACTION_MAIN).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val className = runCatching {
            context.packageManager
                .queryIntentActivitiesCompat(query, PackageManager.MATCH_ALL)
                .firstOrNull()
                ?.activityInfo
                ?.name
        }.getOrNull()
        return className
            ?.takeIf { it.isNotBlank() }
            ?.let { ComponentName.createRelative(packageName, it) }
    }

    private fun launchOriginOsShareProxy(context: Context, targetIntent: Intent): Boolean {
        return runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                component = ComponentName(context, FreeWindowShareProxyActivity::class.java)
                type = "text/plain"
                putExtra(Intent.EXTRA_INTENT, Intent(targetIntent))
                val pkgName = targetIntent.`package` ?: targetIntent.component?.packageName.orEmpty()
                putExtra(Intent.EXTRA_TEXT, pkgName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { e ->
            android.util.Log.e("FreeWindowLauncher", "OriginOS share proxy launch failed", e)
            false
        }
    }

    private fun launchNubiaFreeform(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier = NUBIA_FREEFORM_INTENT_IDENTIFIER
        }
        runCatching {
            context.startActivity(intent, Bundle())
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "nubia freeform startActivity failed", error)
        }
    }

    fun launchOptionsBundle(context: Context, settings: AppSettings): Bundle? {
        if (!settings.freeWindowEnabled) return null
        val options = ActivityOptions.makeBasic()
        val mode = settings.resolvedFreeWindowMode()
        applyWindowingMode(options, mode.windowingMode)
        if (mode == FreeWindowMode.MAGICOS) {
            applyStackId(options, HUAWEI_FREEFORM_STACK_ID)
        }
        options.setLaunchBounds(launchBounds(context, settings))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val backgroundStartMode = when {
                Build.VERSION.SDK_INT >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                else -> @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            options.pendingIntentBackgroundActivityStartMode = backgroundStartMode
        }
        val bundle = options.toBundle() ?: Bundle()
        if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
            bundle.putInt(KEY_WINDOWING_MODE, mode.windowingMode)
        }
        return bundle
    }

    /**
     * 启动用的 launch bounds，坐标语义对齐 LinkGo-X 的 `WindowConfig.calculateBounds`：
     * 用户配置的百分比 = **真机上看得见的尺寸**，补偿系数只作用在发给系统的逻辑矩形上
     * （小米的 freeform 会按 `original_scale` 把逻辑尺寸缩到约 0.7 再显示，见
     * `MiuiMultiWindowUtils` 的 freeform args 档位表），所以逻辑尺寸 = 可见尺寸 / 补偿。
     *
     * 与 LinkGo-X 的唯一差别在这里：LinkGo-X 允许逻辑矩形溢出屏幕、也不夹 left/top，
     * 结果小米的 fit-in-display（AOSP `LaunchParamsUtil.adjustBoundsToFitInDisplayArea`）
     * 会把窗口整体推回屏内 —— 一旦溢出就贴左，位置配置等于失效。本实现先把**可见**
     * 尺寸夹进屏幕，再反推逻辑尺寸，位置百分比因此始终生效。
     */
    fun launchBounds(context: Context, settings: AppSettings): Rect {
        val metrics = context.resources.displayMetrics
        val displayWidth = metrics.widthPixels.coerceAtLeast(1)
        val displayHeight = metrics.heightPixels.coerceAtLeast(1)
        val isLandscape = context.isLandscapeConfiguration()
        val layout = settings.resolvedFreeWindowLayout(isLandscape)

        // 用户想要看到的可见尺寸与位置（位置是左上角，左/上留白直接用配置值）。
        val visualWidth = (displayWidth * layout.widthFraction).toInt()
            .coerceIn(1, displayWidth)
        val visualHeight = (displayHeight * layout.heightFraction).toInt()
            .coerceIn(1, displayHeight)
        val visualLeft = (displayWidth * layout.leftFraction).toInt()
            .coerceIn(0, (displayWidth - visualWidth).coerceAtLeast(0))
        val visualTop = (displayHeight * layout.topFraction).toInt()
            .coerceIn(0, (displayHeight - visualHeight).coerceAtLeast(0))

        // 逻辑尺寸 = 可见尺寸 / 补偿；再夹一次屏幕，保证逻辑矩形也不会溢屏。
        val scale = resolveScaleCompensation(settings, isLandscape)
        val logicalWidth = (visualWidth / scale).roundToInt().coerceIn(1, displayWidth)
        val logicalHeight = (visualHeight / scale).roundToInt().coerceIn(1, displayHeight)

        // left/top 按可见坐标原样下发，不做溢出式夹取：可见尺寸已保证放得下。
        return Rect(visualLeft, visualTop, visualLeft + logicalWidth, visualTop + logicalHeight)
    }

    private fun resolveScaleCompensation(settings: AppSettings, isLandscape: Boolean): Float {
        val mode = settings.resolvedFreeWindowMode()
        val isXiaomi = mode == FreeWindowMode.STANDARD &&
            (Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
             Build.BRAND.contains("xiaomi", ignoreCase = true) ||
             Build.BRAND.contains("redmi", ignoreCase = true))
        return if (isXiaomi) {
            if (isLandscape) MIUI_LANDSCAPE_SCALE else MIUI_PORTRAIT_SCALE
        } else {
            1f
        }
    }

    private fun applyWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, mode)
        } catch (_: Exception) {
            // Hidden API unavailable; bundle fallback applied in launch().
        }
    }

    private fun applyStackId(options: ActivityOptions, stackId: Int) {
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchStackId",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, stackId)
        } catch (_: Exception) {
            // Ignored on platforms without setLaunchStackId
        }
    }
}
