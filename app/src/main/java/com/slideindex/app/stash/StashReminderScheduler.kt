package com.slideindex.app.stash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 闪念条目的「到点提醒」。
 *
 * ⚠️ 与 [com.slideindex.app.remind.RemindAlarmScheduler]（手势的"x 分钟后提醒我"）**不是一套**：
 * 那个是相对分钟数 + 全屏响铃浮层，`PendingIntent` 里只带 `minutes`，装不下"哪条闪念、正文是什么"。
 * 这里用**绝对时间 + 每条一个 request code**，到点只发一条通知（[StashReminderReceiver]）。
 *
 * 重启后闹钟会丢，所以面板打开时会用 [rescheduleAll] 把未来的提醒补排一次
 * （不做 BOOT_COMPLETED 接收器：补排点放在用户必然会进的面板里，少一个常驻入口）。
 */
internal object StashReminderScheduler {
    private const val REQUEST_CODE_BASE = 24_000

    fun schedule(context: Context, entryId: String, atEpochMs: Long, text: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(
            context = context,
            entryId = entryId,
            atEpochMs = atEpochMs,
            text = text,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        runCatching {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pendingIntent)
            } else {
                // 没有精确闹钟权限时退化成不精确闹钟：提醒晚几分钟好过完全不响。
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pendingIntent)
            }
        }
    }

    fun cancel(context: Context, entryId: String) {
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
     * 把当前所有**还没到点**的提醒补排一次（进程重启/设备重启后自愈）。
     *
     * 已经过点的直接跳过 —— 用户重新打开面板时再补一条过期通知只会变成噪音。
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
     */
    internal fun requestCodeOf(entryId: String): Int = REQUEST_CODE_BASE + entryId.hashCode()
}
