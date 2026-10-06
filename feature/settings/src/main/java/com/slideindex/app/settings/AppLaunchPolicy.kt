package com.slideindex.app.settings

enum class AppLaunchPolicy(val id: Int) {
    ALWAYS_FULLSCREEN(0),
    ALWAYS_FREE_WINDOW(1),
    FULLSCREEN_LONG_PRESS_FREE_WINDOW(2),
    FREE_WINDOW_LONG_PRESS_FULLSCREEN(3),
    ;

    fun usesLongPress(): Boolean =
        this == FULLSCREEN_LONG_PRESS_FREE_WINDOW || this == FREE_WINDOW_LONG_PRESS_FULLSCREEN

    companion object {
        fun fromId(id: Int): AppLaunchPolicy =
            entries.firstOrNull { it.id == id } ?: ALWAYS_FULLSCREEN
    }
}

fun AppSettings.resolvedLaunchPolicy(): AppLaunchPolicy = AppLaunchPolicy.fromId(appLaunchPolicyId)

fun AppSettings.shouldLaunchFullscreen(longPressTriggered: Boolean): Boolean {
    if (!freeWindowEnabled) return true
    return when (resolvedLaunchPolicy()) {
        AppLaunchPolicy.ALWAYS_FULLSCREEN -> true
        AppLaunchPolicy.ALWAYS_FREE_WINDOW -> false
        AppLaunchPolicy.FULLSCREEN_LONG_PRESS_FREE_WINDOW -> !longPressTriggered
        AppLaunchPolicy.FREE_WINDOW_LONG_PRESS_FULLSCREEN -> longPressTriggered
    }
}

fun AppSettings.effectiveLongPressDurationMs(): Int =
    longPressLaunchDurationMs.coerceIn(250, 900)

fun AppSettings.launchPolicyLongPressEligible(): Boolean =
    freeWindowEnabled && resolvedLaunchPolicy().usesLongPress()

/** 蜂窝启动是否应视为「长按」以决定小窗/全屏（连续滑选为停在当前图标上的停留时长）。 */
fun AppSettings.resolveHoneycombLongPressArmed(pressDurationMs: Long): Boolean {
    if (!launchPolicyLongPressEligible()) return false
    return pressDurationMs >= effectiveLongPressDurationMs()
}

/**
 * 容器级「打开方式」→ 用于**本次启动**的设置快照。
 *
 * 只改「应用启动方式」这一个档位（其它设置原样透传），因此下游（`FreeWindowLauncher` /
 * 各启动链路）一行都不用改：它们照旧读 `shouldLaunchFullscreen(...)`，只是读到的是容器这一趟的选择。
 *
 * - [QuickWheelLaunchMode.INHERIT] → 原样返回（完全交给「应用与启动」）；
 * - [QuickWheelLaunchMode.FULLSCREEN] → 强制"始终全屏"；
 * - [QuickWheelLaunchMode.FREE_WINDOW] → 强制"始终小窗"，但**总开关关闭**或**目标被硬排除**
 *   （桌面 / 系统界面 / 本应用自身，见 `TaskExclusions`）时不生效、回落为全屏。
 */
fun AppSettings.withQuickWheelLaunchMode(
    mode: QuickWheelLaunchMode,
    targetSupportsFreeWindow: Boolean = true,
): AppSettings = when {
    mode == QuickWheelLaunchMode.FULLSCREEN ->
        copy(launcher = launcher.copy(appLaunchPolicyId = AppLaunchPolicy.ALWAYS_FULLSCREEN.id))

    mode == QuickWheelLaunchMode.FREE_WINDOW && freeWindowEnabled && targetSupportsFreeWindow ->
        copy(launcher = launcher.copy(appLaunchPolicyId = AppLaunchPolicy.ALWAYS_FREE_WINDOW.id))

    else -> this
}
