package com.slideindex.app.stash

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 闪念条目的「到点提醒」。
 *
 * ⚠️ 与 [com.slideindex.app.remind.RemindAlarmScheduler]（手势的"x 分钟后提醒我"）**不是一套**：
 * 那个是相对分钟数 + 全屏响铃浮层，`PendingIntent` 里只带 `minutes`，装不下"哪条闪念、正文是什么"。
 * 这里用**绝对时间 + 每条一个 request code**，到点只发一条通知（[StashReminderReceiver]）。
 *
 * 重启后闹钟会丢，所以有**两个**补排点：
 *
 * - [StashReminderBootReceiver]：开机 / 应用更新 / 改时区时自动补排（数据源是
 *   [StashReminderMirror]，不碰 Hilt）；
 * - [rescheduleAll]：面板打开时按数据层再补一遍（用户必然会进的地方，兜底）。
 *
 * 另外每次 [schedule] / [cancel] 都会同步写一遍镜像 —— 镜像必须在**所有**改闹钟的路径上跟着动，
 * 否则重启后补排的是过期状态。
 */
internal object StashReminderScheduler {
    private const val TAG = "StashReminderSched"

    private const val REQUEST_CODE_BASE = 24_000

    /** 「稍后 N 分钟」通知按钮的 `PendingIntent` request code 段位（与闹钟本体错开）。 */
    internal const val SNOOZE_REQUEST_BASE = 26_000

    /**
     * 通知 id 的**唯一出处**（与 [requestCodeOf] 同一个理由，§0.16.x）。
     *
     * [StashReminderReceiver] 发通知用它，`StashReminderPendingState` 判断"这条提醒的通知
     * 还在不在通知栏里"也用它 —— 这种"必须两处算出同一个数"的规则**只能有一份实现**，
     * 各写一遍迟早会漂移，而漂移的症状是"指示条永远不亮/永远不灭"，很难查。
     */
    internal fun notifyIdOf(entryId: String): Int = NOTIFY_ID_BASE + entryId.hashCode()

    private const val NOTIFY_ID_BASE = 24_100

    fun schedule(context: Context, entryId: String, atEpochMs: Long, text: String) {
        // 先写镜像再排闹钟：反过来的话，进程如果在中间死掉，就会出现"闹钟在、镜像没有"，
        // 重启后这条提醒永远补不回来。写镜像成功而排闹钟失败则只是多一份无用记录，无害。
        StashReminderMirror.put(context, entryId, atEpochMs, text)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(
            context = context,
            entryId = entryId,
            atEpochMs = atEpochMs,
            text = text,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        setAlarmDegradingToInexact(alarmManager, atEpochMs, pendingIntent)
    }

    /**
     * 排一条闹钟：**优先精确，被系统拒绝时退化成不精确**。
     *
     * 精确 / 不精确两条路都收在这里，是因为 `canScheduleExactAlarms()` 只是**调用前**的快照：
     * 权限可能在"判定"与"调用"之间被撤销，个别 ROM 也会口是心非直接抛 `SecurityException`。
     * 老实现（本文件与 [StashReminderReceiver.snooze] 各写一份 `runCatching { 精确 else 不精确 }`）
     * 在精确那条一抛时会把 `else` 分支整段跳过 —— 结果是"这次提醒**完全没有闹钟**"，
     * 而正确结果是"晚几分钟响"。这两者的差别是"功能在不在"，不是体面问题。
     *
     * ⚠️ 这里抑制 `MissingPermission` 的理由与 `RemindAlarmScheduler.scheduleExactAlarm` /
     * `ForegroundAppTracker` 同款：lint 认不出 `canScheduleExactAlarms()` 这种"特殊访问"守卫
     * （它只认内联的 `checkSelfPermission` 或显式 `catch (SecurityException)`）；
     * 下面两个 `catch` 是**真的**兜底，不是为了糊弄 lint 才写的。
     */
    @SuppressLint("MissingPermission")
    internal fun setAlarmDegradingToInexact(
        alarmManager: AlarmManager,
        triggerAtMs: Long,
        pendingIntent: PendingIntent,
    ) {
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "精确闹钟排不上，退化成不精确闹钟 triggerAt=$triggerAtMs", t)
            }
        }
        // 没有精确闹钟权限时退化成不精确闹钟：提醒晚几分钟好过完全不响。
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }.onFailure { Log.e(TAG, "不精确闹钟也排不上，这条提醒这次不会响 triggerAt=$triggerAtMs", it) }
    }

    /**
     * 取消一条提醒的闹钟**并清掉它的镜像**。
     *
     * 镜像必须一起清：否则重启后 [StashReminderBootReceiver] 会照着镜像把已经取消的提醒重新排上，
     * 用户会觉得"取消了还响"。稍后时间（snooze override）同理 —— 留着它下次并回 meta 时会把
     * 提醒又写回去。
     *
     * ⚠️ 先去镜像再取消闹钟：这个顺序最坏是"闹钟还在但镜像没了"（重启前多响一次、
     * 重启后不会复活），反过来则会出现"用户以为取消了、重启后又响"。
     */
    fun cancel(context: Context, entryId: String) {
        StashReminderMirror.remove(context, entryId)
        StashReminderMirror.removeSnoozeOverride(context, entryId)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(
            context = context,
            entryId = entryId,
            atEpochMs = 0L,
            text = "",
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * 把 [reminders] 里所有**还没到点**的提醒补排一次（进程/设备重启后自愈）。
     *
     * 已经过点的直接跳过 —— 用户重新打开面板时再补一条过期通知只会变成噪音。
     *
     * 调用方负责传入"已经并过 snooze override 的时间"（见
     * [StashMetaRepository.clearExpiredReminders] / [StashMetaRepository.mergeSnoozeOverrides]）：
     * 传旧时间会让闹钟立刻响一次，那也是 `rescheduleAll` 唯一会造成的可见副作用。
     */
    fun rescheduleAll(
        context: Context,
        reminders: Map<String, Long>,
        textOf: (String) -> String,
    ) {
        val now = System.currentTimeMillis()
        reminders.forEach { (entryId, atEpochMs) ->
            if (atEpochMs > now) {
                schedule(context, entryId, atEpochMs, textOf(entryId))
            }
        }
    }

    /**
     * 照 [StashReminderMirror] 补排一次 —— 给 [StashReminderBootReceiver] 用（**不碰 Hilt**）。
     *
     * 条目时间以**snooze override 优先**：用户点了「稍后」之后 meta 里还是旧时间，
     * 照旧时间排会在开机后立刻响一次。
     *
     * 已经过点的一律跳过（跟 [rescheduleAll] 一致）：关机三天后开机不该补三条过期通知，
     * 用户下次打开面板时 meta 侧会用 [StashMetaRepository.clearExpiredReminders] 收尾。
     *
     * @return 真正补排的条数（只用于日志）。
     */
    fun rescheduleFromMirror(context: Context): Int {
        val now = System.currentTimeMillis()
        val snooze = StashReminderMirror.snoozeOverrides(context)
        var scheduled = 0
        StashReminderMirror.all(context).forEach { (entryId, atEpochMs, text) ->
            val effective = snooze[entryId] ?: atEpochMs
            if (effective <= now) return@forEach
            schedule(context, entryId, effective, text)
            scheduled++
        }
        return scheduled
    }

    private fun pendingIntent(
        context: Context,
        entryId: String,
        atEpochMs: Long,
        text: String,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, StashReminderReceiver::class.java).apply {
            action = StashReminderReceiver.ACTION_STASH_REMIND
            putExtra(StashReminderReceiver.EXTRA_ENTRY_ID, entryId)
            putExtra(StashReminderReceiver.EXTRA_TEXT, text)
        }
        return PendingIntent.getBroadcast(context, requestCodeOf(entryId), intent, flags)
    }

    /**
     * 每条闪念一个 request code。
     *
     * ⚠️ `StashReminderReceiver` 里「稍后 10 分钟」重排同一个闹钟时**必须用同一个值**，
     * 否则会新建一个 PendingIntent、旧的那个还挂着（到点响两次）。所以这里是唯一出处。
     *
     * 通知 id 在 [notifyIdOf]（另起一段 24_100+），与这里错开。
     */
    internal fun requestCodeOf(entryId: String): Int = REQUEST_CODE_BASE + entryId.hashCode()
}
