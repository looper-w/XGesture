package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.LaunchWindowMode

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

/**
 * 单个绑定自带启动形态时优先于全局策略；[LaunchWindowMode.FOLLOW_GLOBAL] 回落到
 * [shouldLaunchFullscreen]。
 */
fun AppSettings.shouldLaunchFullscreen(
    windowMode: LaunchWindowMode,
    longPressTriggered: Boolean,
): Boolean = when (windowMode) {
    LaunchWindowMode.FOLLOW_GLOBAL -> shouldLaunchFullscreen(longPressTriggered)
    LaunchWindowMode.ALWAYS_FULLSCREEN -> true
    LaunchWindowMode.ALWAYS_FREE_WINDOW -> !freeWindowEnabled
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
 * ⚠️ 配套用法：容器显式选形态时，还要把**动作自带**的启动形态归零（见 [withoutLaunchWindowMode]），
 * 否则"动作=总是小窗"会盖过容器的"始终全屏"。
 *
 * - [QuickWheelLaunchMode.INHERIT] → 原样返回（完全交给动作自带设置 + 「应用与启动」）；
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

/**
 * 把**动作自带**的启动形态清成"跟随"。
 *
 * 容器「打开方式」是启动形态的**唯一来源**：上游那个"选应用时的启动形态弹窗"只是容器的
 * **首次设置入口**（结果会被收编进容器字段，见容器编辑页的 `applyPickedAction`），动作不该再自带形态 ——
 * 它一旦是 `ALWAYS_*`，会在 `ActionExecutor` 里短路掉容器与「应用与启动」。
 */
fun GestureAction.withoutLaunchWindowMode(): GestureAction =
    if (this is GestureAction.LaunchApp && windowMode != LaunchWindowMode.FOLLOW_GLOBAL) {
        copy(windowMode = LaunchWindowMode.FOLLOW_GLOBAL)
    } else {
        this
    }

/**
 * 该动作是否"真的会打开某个东西"。
 *
 * 只有这类动作才与「打开方式」有关：
 * - [GestureAction.LaunchApp]：挑应用（上游那个启动形态弹窗就是它的首次设置入口）；
 * - [GestureAction.LaunchShortcut]：启动快捷方式 —— 上游没有单独的弹窗，但它的启动链路
 *   （`ActionExecutorLaunch` / `ActivityShortcutLauncher`）只读 `shouldLaunchFullscreen(...)`，
 *   所以容器的三档对它同样有效，且比上游更灵活（上游只能跟随全局）；
 * - [GestureAction.OpenLink]：打开链接（同上，走启动链路）。
 *
 * 其余动作（返回 / 主屏幕 / 多任务 / 执行命令 / 剪贴板 / 打开轮盘…）**根本没有"启动"这一步**，
 * 容器「打开方式」对它们没有意义 → 容器编辑页把三档置灰并写明原因。
 */
fun GestureAction.opensSomething(): Boolean =
    this is GestureAction.LaunchApp ||
        this is GestureAction.LaunchShortcut ||
        this is GestureAction.OpenLink

/** 上游「动作自带启动形态」→ 容器「打开方式」：两者本就是同一个概念，只是入口不同。 */
fun LaunchWindowMode.toQuickWheelLaunchMode(): QuickWheelLaunchMode = when (this) {
    LaunchWindowMode.FOLLOW_GLOBAL -> QuickWheelLaunchMode.INHERIT
    LaunchWindowMode.ALWAYS_FULLSCREEN -> QuickWheelLaunchMode.FULLSCREEN
    LaunchWindowMode.ALWAYS_FREE_WINDOW -> QuickWheelLaunchMode.FREE_WINDOW
}

/** 容器「打开方式」→ 上游形态（仅用于把容器当前设置投影给动作选择器 / 那个弹窗，便于预选与显示）。 */
fun QuickWheelLaunchMode.toLaunchWindowMode(): LaunchWindowMode = when (this) {
    QuickWheelLaunchMode.INHERIT -> LaunchWindowMode.FOLLOW_GLOBAL
    QuickWheelLaunchMode.FULLSCREEN -> LaunchWindowMode.ALWAYS_FULLSCREEN
    QuickWheelLaunchMode.FREE_WINDOW -> LaunchWindowMode.ALWAYS_FREE_WINDOW
}

