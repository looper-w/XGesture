package com.slideindex.app.overlay.history

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashReminderMirror
import com.slideindex.app.stash.StashReminderScheduler
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

    /**
     * 排查开关：把 [compute] 的中间量写进 `filesDir/reminder_pending_debug.txt`。
     *
     * ⚠️ 临时排查代码（起因：实测"通知还在栏里、指示条却永远不亮"，而只看一个
     * `hasPending=false` 完全分不清卡在哪一条判据）。定位完把这里改 false 即可零开销
     * （那时 [ComputeDiag] 根本不构造、也不写文件）。与 `HistoryFloatContent` 里的
     * `GLOW_DEBUG` 是**两个独立开关**：绘制侧那个管"光画没画"，这个管"状态算对没有"。
     * ⚠️ 用 `const` 而不是 `BuildConfig.DEBUG`：本文件在 main 源集、没有 debug/release 之分，
     * 而且发布包里必须能带着它去查现场（`DEBUG_PROBE=false` 时就完全零开销）。
     */
    private const val DEBUG_PROBE = true

    /** 探针文件名（`filesDir` 下）。读：`adb shell run-as <pkg> cat files/reminder_pending_debug.txt`。 */
    private const val DEBUG_PROBE_FILE = "reminder_pending_debug.txt"

    /** 探针文件行数上限；超了就只保留最后 [DEBUG_PROBE_KEEP_LINES] 行，避免无限增长。 */
    private const val DEBUG_PROBE_MAX_LINES = 300
    private const val DEBUG_PROBE_KEEP_LINES = 60

    /** 每条候选/条目最多列几个，避免一行过长、文件爆炸。 */
    private const val DEBUG_PROBE_MAX_DETAILS = 3

    /** 探针每行的时间戳格式（与 `EdgeDiag` 同一套 `java.time`；minSdk 31 无需 desugar）。 */
    private val DEBUG_PROBE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** 是否存在"已提醒但用户还没划掉/完成"的条目。 */
    val hasPending: MutableState<Boolean> = mutableStateOf(false)

    /**
     * 重新计算（面板打开 / 通知变动 / 数据变动 / 把手服务启动与慢轮询时调用）。
     *
     * 同步、廉价（一次 `getActiveNotifications` + 一次偏好读 + 一遍提醒表），可以从组合里直接调：
     * 它**不**做任何 suspend 工作，也就不需要 `LaunchedEffect`。写 state 放在最后、且只在值变化时写。
     *
     * @return 本次是否**真的改变了** [hasPending]。
     * 以前返回 `Unit`，现在返回 Boolean 是给把手服务那条"自愈轮询"用的（变化了就重置退避、
     * 没变化就慢慢退避到 240s）；**忽略返回值的既有调用点全都不受影响**。
     *
     * ⚠️ 本方法每次都**从零重算**（通知栏 + 镜像 + 数据层），不读内存里 [markPending] 的残留 ——
     * 所以进程重启之后再调用它，照样能得到正确结果。这正是"通知还挂在栏里、指示条却永远不亮"
     * 那类问题的关键：内存标记只配当加速/兜底，**不能是唯一来源**。
     */
    fun refresh(context: Context): Boolean {
        val appContext = context.applicationContext
        // 排查用：把 compute 的中间量收下来（`DEBUG_PROBE=false` 时它是 null，零开销）。
        val diag = if (DEBUG_PROBE) ComputeDiag() else null
        val next = runCatching { compute(appContext, diag) }.getOrElse { cause ->
            Log.w(TAG, "refresh 计算失败，按'没有待处理'处理", cause)
            diag?.computeError = cause.javaClass.simpleName
            false
        }
        diag?.let { d ->
            d.result = next
            // 每次 refresh 都记一行（而不是只在状态翻转时）：实测"永远是 false"的情形下
            // 翻转永远不会发生，只记翻转就等于什么都没有。写入频率由调用方决定
            //（服务侧 30s 一次 + 面板可见时 2s 一次），文件有行数上限，不会失控。
            // `DEBUG_PROBE=false` 时 `diag` 本身就是 null → 这里连 lambda 都不会执行。
            if (DEBUG_PROBE) runCatching { appendDebugProbe(appContext, d) }
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
     * [diag]：纯排查用的中间量收集器，传 null 时行为与以前完全一致（零开销路径）。
     * 为什么要把中间量扒出来：实测出现过"通知明明在栏里、指示条却不亮"，
     * 而光看一个 `hasPending=false` 根本分不清是候选集为空、还是 id 对不上、还是通知栏读失败。
     */
    private fun compute(context: Context, diag: ComputeDiag? = null): Boolean {
        val repo = StashAccess.metaRepository
        val activeIds = activeNotificationIds(context, diag)
        diag?.activeIds = activeIds

        // 判据 1：通知还在栏里 —— 只认"我们的提醒通知"，不认通知栏里别人的通知。
        //
        // ⚠️ `getActiveNotifications` 拿不到通知的 extras，没法反查 entryId，所以只能拿
        // "我关心的这批 id"去对。候选集是两处的并集：
        // - `reminders`（数据层里还没收尾的），数据层在时它最准；
        // - [StashReminderMirror.notifiedEntryIds]（真的发出去过的），**数据层没挂上时唯一的一份**
        //   —— 少了它，进程刚被广播拉起来的那次 refresh 会算成"没有待处理"，指示条在
        //   "提醒到点、但用户还没打开过面板"的时序下永远点不亮。
        val metaKeys = repo?.pendingReminders()?.keys.orEmpty()
        val notified = runCatching { StashReminderMirror.notifiedEntryIds(context) }
            .getOrElse { cause ->
                diag?.notes?.add("mirrorRead失败:${cause.javaClass.simpleName}")
                emptySet()
            }
        val candidateIds = buildSet<String> {
            addAll(metaKeys)
            addAll(notified)
        }
        diag?.apply {
            repoPresent = repo != null
            metaCount = metaKeys.size
            mirrorCount = notified.size
        }
        candidateIds.forEach { entryId ->
            val hit = StashReminderScheduler.notifyIdOf(entryId) in activeIds
            diag?.addCandidate(entryId, hit)
            if (hit) return true
        }

        val store = repo?.store?.value
        diag?.storePresent = store != null
        if (store == null) return false
        diag?.apply {
            remindersCount = store.reminders.size
            firedAtCount = store.firedAt.size
        }
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
            val done = store.isDone(entryId)
            if (done) {
                diag?.addEntry(entryId, store.reminders[entryId], store.firedAt[entryId], true, false, 0L, false)
                return@forEach
            }
            val dismissed = StashReminderMirror.dismissedSinceNotified(context, entryId)
            val snoozeAt = snoozed[entryId] ?: 0L
            if (dismissed) {
                diag?.addEntry(entryId, store.reminders[entryId], store.firedAt[entryId], false, true, snoozeAt, false)
                return@forEach
            }
            if (snoozeAt > now) {
                diag?.addEntry(entryId, store.reminders[entryId], store.firedAt[entryId], false, false, snoozeAt, false)
                return@forEach
            }
            val atMs = store.reminders[entryId]
            val firedAtMs = store.firedAt[entryId]
            val overdue = (atMs != null && atMs <= now) || (firedAtMs != null && firedAtMs > 0L)
            diag?.addEntry(entryId, atMs, firedAtMs, false, false, snoozeAt, overdue)
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
     *
     * §排查补充：异常以前只进 logcat，而实测机型会把 `Log.w` 也吞掉 —— 于是"通知栏读失败"
     * 在探针里和"通知栏真的是空的"长得一模一样。现在把异常**也带进 [ComputeDiag]**，
     * 并且额外记下"我们自己这个包在通知栏里到底有几条"（`pkgTotalCount`）作为交叉验证。
     */
    private fun activeNotificationIds(context: Context, diag: ComputeDiag? = null): Set<Int> {
        val manager = context.getSystemService(NotificationManager::class.java) ?: run {
            diag?.activeError = "noService"
            return emptySet()
        }
        val active = runCatching { manager.activeNotifications }.getOrElse { cause ->
            Log.w(TAG, "getActiveNotifications 失败，按'通知栏为空'处理", cause)
            diag?.activeError = cause.javaClass.simpleName
            return emptySet()
        }
        val ids = HashSet<Int>(active.size)
        active.forEach { ids += it.id }
        diag?.apply {
            pkgTotalCount = active.count { it.packageName == context.packageName }
        }
        return ids
    }

    // ─────────────── 排查用：中间量收集 + 写文件（定位完可整段删） ───────────────

    /**
     * [compute] 的中间量快照。**只是一包数据**，不带任何行为依赖，
     * 所以 `DEBUG_PROBE=false` 时它连构造都不会发生（`refresh` 里是 `if (DEBUG_PROBE)`）。
     */
    class ComputeDiag {
        var result: Boolean = false
        var computeError: String? = null
        var repoPresent: Boolean = false
        var storePresent: Boolean = false

        /** 判据 1 的输入：候选 id 的两处来源各有多少（对不上 id 时先看这个）。 */
        var metaCount: Int = 0
        var mirrorCount: Int = 0

        /** 通知栏：我们的 id 全集、我们包在栏里的条数、以及读取异常。 */
        var activeIds: Set<Int> = emptySet()
        var pkgTotalCount: Int = 0
        var activeError: String? = null

        /** 判据 2 的输入。 */
        var remindersCount: Int = 0
        var firedAtCount: Int = 0

        var notes: MutableList<String> = mutableListOf()

        /** 判据 1 的逐候选明细：[entryId] → 它算出的通知 id 在不在栏里。 */
        val candidates = mutableListOf<Pair<String, Boolean>>()

        /** 判据 2 的逐条目明细。 */
        val entries = mutableListOf<EntryDiag>()

        fun addCandidate(entryId: String, live: Boolean) {
            candidates += entryId to live
        }

        fun addEntry(
            entryId: String,
            reminderAt: Long?,
            firedAt: Long?,
            done: Boolean,
            dismissed: Boolean,
            snoozeAt: Long,
            overdue: Boolean,
        ) {
            if (entries.size <= DEBUG_PROBE_MAX_DETAILS) {
                entries += EntryDiag(entryId, reminderAt, firedAt, done, dismissed, snoozeAt, overdue)
            }
        }
    }

    /** 判据 2 里单条条目的判定输入（[reason] 由写侧根据这些字段渲染）。 */
    class EntryDiag(
        val entryId: String,
        val reminderAt: Long?,
        val firedAt: Long?,
        val done: Boolean,
        val dismissed: Boolean,
        val snoozeAt: Long,
        val overdue: Boolean,
    )

    /**
     * 把一次 [compute] 的中间量格式化成**一行**追加到探针文件。
     *
     * 为什么是一行而不是多行：`adb shell run-as … cat` 一眼扫过去就能对齐看，
     * 也不会把"同一次计算"的多行混到别的计算里（这个文件是跨线程写、还可能并发）。
     * 为什么整读整写而不是 `appendText`：文件只有几十~300 行，整读整写代价可忽略，
     * 而且覆盖写不会出现半行；顺便解决了"无限增长"（超上限就只留最后若干行）。
     *
     * ⚠️ 调用方已经包了 `runCatching`（磁盘满/权限异常都不该影响状态计算）。
     * ⚠️ 这个文件**跨线程写**（服务侧在 `Dispatchers.Default`、面板侧在主线程，两边都在刷）：
     * 没有加锁 —— 并发时最坏是其中一行被覆盖丢掉。对一个诊断文件来说这个代价可以接受，
     * 而加锁会让"算状态"这条热路径去等 IO，不划算（何况每次都刷新的是同一份状态，丢一行不影响判断）。
     */
    private fun appendDebugProbe(context: Context, diag: ComputeDiag) {
        val line = buildString {
            append(LocalTime.now().format(DEBUG_PROBE_TIME_FORMAT))
            append(" hasPending=").append(diag.result)
            append(" meta=").append(diag.metaCount)
            append(" mirror=").append(diag.mirrorCount)
            append(" reminders=").append(diag.remindersCount)
            append(" firedAt=").append(diag.firedAtCount)
            append(" pkgNotifs=").append(diag.pkgTotalCount)
            append(" activeIds=").append(diag.activeIds.size)
            append(" repo=").append(diag.repoPresent)
            append(" store=").append(diag.storePresent)
            diag.activeError?.let { append(" activeErr=").append(it) }
            diag.computeError?.let { append(" computeErr=").append(it) }
            diag.notes.takeIf { it.isNotEmpty() }?.let { append(" notes=").append(it.joinToString(",")) }
            if (diag.candidates.isNotEmpty()) {
                append(" cand=[")
                append(
                    diag.candidates.take(DEBUG_PROBE_MAX_DETAILS)
                        .joinToString(",") { (id, live) -> "${id.take(8)}:${if (live) "LIVE" else "miss"}" },
                )
                append("]")
            }
            if (diag.entries.isNotEmpty()) {
                append(" items=[")
                append(
                    diag.entries.take(DEBUG_PROBE_MAX_DETAILS).joinToString(",") { e ->
                        val reason = when {
                            e.done -> "done"
                            e.dismissed -> "dismissed"
                            e.snoozeAt > System.currentTimeMillis() -> "snoozed"
                            e.overdue -> "OVERDUE"
                            else -> "notOverdue"
                        }
                        "${e.entryId.take(8)}(rem=${e.reminderAt ?: "-"},fired=${e.firedAt ?: "-"})→$reason"
                    },
                )
                append("]")
            }
        }
        Log.d(TAG, line)
        val file = File(context.filesDir, DEBUG_PROBE_FILE)
        val existing = if (file.exists()) {
            runCatching { file.readLines() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val kept = if (existing.size >= DEBUG_PROBE_MAX_LINES) {
            existing.takeLast(DEBUG_PROBE_KEEP_LINES)
        } else {
            existing
        }
        file.writeText((kept + line).joinToString(separator = "\n", postfix = "\n"))
    }
}
