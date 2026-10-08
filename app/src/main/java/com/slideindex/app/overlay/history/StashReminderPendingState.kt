package com.slideindex.app.overlay.history

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashReminderMirror
import com.slideindex.app.stash.StashReminderScheduler

/**
 * 「有没有一条**已提醒、但用户还没处理**的闪念」——给指示条（流光）用。
 *
 * §0.16.15 新增。为什么不是简单的"有没有待触发的提醒"（看看 [StashAccess.metaRepository] 的
 * `reminders` 就够了）：用户要的是"**到点了、在提醒你**"这件事的可见性 —— 而到点之后提醒就
 * 从待触发表里消失了（`clearExpiredReminders` 收尾）。所以这里有两条独立的判据，**满足一条就算**：
 *
 * 1. **通知还在通知栏里**（[NotificationManager.getActiveNotifications] 里有我们那条通知的 id）。
 *    这是最准的一条：它同时也覆盖了"通知被 ROM 静默吞掉"之外的所有情况。
 *    候选 id 来自"数据层里的待触发提醒 ∪ [StashReminderMirror.notifiedEntryIds]"——后者是
 *    数据层还没挂上时的唯一一份（进程刚被提醒广播拉起来就是这种时序）。
 * 2. **数据层兜底**：`提醒时间已过` **或** `firedAt 记着"响过了"`，且条目**未完成**、
 *    用户也没把这条通知划掉（`dismissedSinceNotified`）、也没有一条"稍后"把它推到未来。
 *    防的是国产 ROM "通知发出去但没进通知栏"（那样第 1 条永远为假）—— 没有这一条，
 *    指示条在那些机器上永远不亮；而"提醒时间已过"这一半是给**从没打开过面板**的时序用的
 *    （那时数据层还没被 `clearExpiredReminders` 收尾，`firedAt` 也还没写上）。
 *
 * 变回 false 的路径（三种都覆盖了）：
 * - 点「完成」：通知被系统撤掉 + `setReminder(null)`（连带清掉 `firedAt`）→ 两条判据都不成立；
 * - 点「稍后」：通知被撤掉 + `setReminder(now + N)` 把时间推到未来 → 第 1 条不成立、
 *   第 2 条的"响过了"也被清掉（trampoline 里 `clearNotified`）→ **先灭，到点再亮**（用户确认的语义）；
 * - **划掉通知**：通知没了，但数据层刻意不动（用户只是"知道了"，不是"做完了"）——
 *   这一条靠 [StashReminderMirror.markDismissed] 的"划掉时间"把第 2 条也按住，
 *   否则下一次 [refresh] 会立刻用兜底规则把刚灭掉的状态重新点亮。
 *
 * ⚠️ **不要在 object 初始化里读 Context**（[refresh] 全都要由调用方把 Context 传进来）：
 * 这个 object 会被 `HistoryPanelScreen` 的组合直接引用，而它同时也会被"没有 Compose 环境"
 * 的地方（比如 trampoline 的日志/测试）碰到过；一旦初始化里有 Context 就会变成隐式的
 * "谁先碰谁负责"，进程冷启动时会直接崩。
 *
 * ⚠️ [hasPending] 只会在**值真的变了**的时候写：它被 Compose 当 state 观察，每帧都写一次
 * 会让整块面板每帧重组（`refresh` 会被周期性调用，见调用点）。
 */
internal object StashReminderPendingState {

    private const val TAG = "StashRemindPending"

    /** 是否存在"已提醒但用户还没划掉/完成"的条目。 */
    val hasPending: MutableState<Boolean> = mutableStateOf(false)

    /**
     * 重新计算（面板打开 / 通知变动 / 数据变动 / 把手服务启动与慢轮询时调用）。
     *
     * 同步、廉价（一次 `getActiveNotifications` + 一次偏好读 + 一遍提醒表），可以从组合里直接调：
     * 它**不**做任何 suspend 工作，也就不需要 `LaunchedEffect`。写 state 放在最后、且只在值变化时写。
     *
     * @return 本次是否**真的改变了** [hasPending]。
     * 返回 Boolean 是给把手服务那条"自愈轮询"用的（变化了就重置退避、没变化就慢慢退避到 240s）；
     * **忽略返回值的既有调用点全都不受影响**。
     *
     * ⚠️ 本方法每次都**从零重算**（通知栏 + 镜像 + 数据层），不读内存里 [markPending] 的残留 ——
     * 所以进程重启之后再调用它，照样能得到正确结果。这正是"通知还挂在栏里、指示条却永远不亮"
     * 那类问题的关键：内存标记只配当加速/兜底，**不能是唯一来源**。
     */
    fun refresh(context: Context): Boolean {
        val appContext = context.applicationContext
        val next = runCatching { compute(appContext) }.getOrElse { cause ->
            Log.w(TAG, "refresh 计算失败，按'没有待处理'处理", cause)
            false
        }
        if (hasPending.value != next) {
            Log.i(TAG, "hasPending -> $next")
            hasPending.value = next
            return true
        }
        return false
    }

    /**
     * 提醒通知**刚刚真的发出去了**（`StashReminderReceiver` 在 `notify` 成功后调用）→ 直接点亮。
     *
     * 为什么不用 [refresh] 代替：那一刻算出来的结果**不保证**是 true ——
     * - `getActiveNotifications` 与 `notify` 之间存在时序空档（刚提交、系统那边可能还没列出来）；
     * - "数据层兜底"要求 `StashAccess.metaRepository` 已挂上，而进程可能正是被这条广播
     *   拉起来的（Hilt 图还在构造）—— 那时两个判据都会落空，指示条会**错过**整次提醒。
     *
     * 而"我们刚刚为一条未处理的提醒发了通知"这件事本身就是确定的，直接写 true 最准。
     * 之后任何一个"用户处理了它"的动作（完成 / 稍后 / 划掉）都会走 [refresh] 把它算回 false。
     */
    fun markPending() {
        if (!hasPending.value) {
            Log.i(TAG, "hasPending -> true（提醒通知已提交）")
            hasPending.value = true
        }
    }

    /**
     * 真正的判据。`repo == null`（数据层还没挂上）时**只看通知栏**：
     * 这时任何"兜底"都只能靠数据层，而数据层不可用本来就意味着"这个进程刚起来"，
     * 通知栏那一份反而是最可信的（候选 id 取自 [StashReminderMirror.notifiedEntryIds]）。
     *
     * ⚠️ 这一段判据是**整条流光链路的唯一状态来源**，改它之前请先想清楚两条判据各自的
     * 失效场景（顶部 KDoc 列了）。排查期间曾被加过一层"中间量收集器"，已清理；
     * 若将来又要排查，别改判定逻辑本身，只在外面加可观测性。
     */
    private fun compute(context: Context): Boolean {
        val repo = StashAccess.metaRepository
        val activeIds = activeNotificationIds(context)

        // 判据 1：通知还在栏里 —— 只认"我们的提醒通知"，不认通知栏里别人的通知。
        //
        // ⚠️ `getActiveNotifications` 拿不到通知的 extras，没法反查 entryId，所以只能拿
        // "我关心的这批 id"去对。候选集是两处的并集：
        // - `reminders`（数据层里还没收尾的），数据层在时它最准；
        // - [StashReminderMirror.notifiedEntryIds]（真的发出去过的），**数据层没挂上时唯一的一份**
        //   —— 少了它，进程刚被广播拉起来的那次 refresh 会算成"没有待处理"，指示条在
        //   "提醒到点、但用户还没打开过面板"的时序下永远点不亮。
        val candidateIds = buildSet<String> {
            repo?.pendingReminders()?.keys?.let { addAll(it) }
            val notified = runCatching { StashReminderMirror.notifiedEntryIds(context) }
                .getOrDefault(emptySet())
            addAll(notified)
        }
        candidateIds.forEach { entryId ->
            if (StashReminderScheduler.notifyIdOf(entryId) in activeIds) return true
        }

        val store = repo?.store?.value ?: return false
        // "稍后"到未来的那些：数据层可能还不知道（用户是在通知上点的，只落在镜像的 override 里），
        // 所以兜底判据也要认它 —— 不然点完「稍后」刚灭掉的指示条会被这条规则立刻重新点亮。
        val now = System.currentTimeMillis()
        val snoozed = runCatching { StashReminderMirror.snoozeOverrides(context) }
            .getOrDefault(emptyMap())

        // 判据 2：数据层兜底 —— "提醒时间已过（或已经响过）且条目未完成"，且用户没把它划掉。
        //
        // 两个来源合成一条规则，缺一不可：
        // - `reminders[entryId] <= now`：闹钟已到点、但还没被 `clearExpiredReminders` 收尾
        //   （面板从没打开过时就是这种状态）；
        // - `firedAt[entryId]`：已经收尾过（"删提醒"和"记下它响过"是一件事的两半）。
        val pending = store.reminders.keys + store.firedAt.keys
        pending.forEach { entryId ->
            if (store.isDone(entryId)) return@forEach
            if (StashReminderMirror.dismissedSinceNotified(context, entryId)) return@forEach
            if ((snoozed[entryId] ?: 0L) > now) return@forEach
            val atMs = store.reminders[entryId]
            val firedAtMs = store.firedAt[entryId]
            val overdue = (atMs != null && atMs <= now) || (firedAtMs != null && firedAtMs > 0L)
            if (overdue) return true
        }
        return false
    }

    /**
     * 我们自己的通知里，现在还在通知栏的那批 id。
     *
     * ⚠️ 这个 API 在国产 ROM 上**不保证准**（可能返回空、可能返回被折叠/收纳后的子集），
     * 所以它只是判据 1，永远有判据 2 兜底。异常一律吞掉（返回空集）：`getActiveNotifications`
     * 在个别 ROM 上会抛 `SecurityException`，而"算不出待处理"最坏只是指示条不亮，
     * 不该让面板的组合直接崩。
     */
    private fun activeNotificationIds(context: Context): Set<Int> {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return emptySet()
        val active = runCatching { manager.activeNotifications }.getOrElse { cause ->
            Log.w(TAG, "getActiveNotifications 失败，按'通知栏为空'处理", cause)
            return emptySet()
        }
        val ids = HashSet<Int>(active.size)
        active.forEach { ids += it.id }
        return ids
    }
}
