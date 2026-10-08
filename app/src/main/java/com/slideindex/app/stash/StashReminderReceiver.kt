package com.slideindex.app.stash

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.R
import com.slideindex.app.service.StashClipboardTrampolineActivity

/**
 * 闪念提醒到点：发一条通知。
 *
 * §0.16.14（用户实测"提醒完全没效果"之后）改了三件事：
 *
 * 1. **加日志**。以前这里一行日志都没有 —— 真机证据是广播**确实送达了**
 *    （`AlarmManager: Alarm deliverLocked` + `IntentFirewall: CHECK INTENT … StashReminderReceiver`），
 *    但通知栏里没有通知，于是"断在哪一步"完全查不出来。现在每一步都有 Log。
 * 2. **渠道换 id + `IMPORTANCE_HIGH` + 震动**。老渠道 `stash_remind` 是 DEFAULT（**不弹横幅、不震动**），
 *    而且**渠道重要性创建之后就改不了**，只能换 id 重建（旧渠道留着不用）。
 * 3. **多一个「稍后 10 分钟」按钮**，直接把这个闹钟往后重排 —— 不需要碰数据层
 *    （进程可能是被这条广播拉起来的，那时 Hilt 仓库还没构造好）。
 *
 * ⚠️ 仍然刻意**不碰数据层**：只读 Intent 里带的 entryId / 正文。
 * 也仍然**不发"响铃页"**：这是"轻提醒"，不是闹钟（要闹钟级体验得另开一档，见计划 §0.16.14）。
 */
class StashReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        if (entryId == null) {
            // 这条路以前是"静默返回"，出了事谁也看不见 —— 现在至少留一行。
            Log.w(TAG, "onReceive: Intent 里没有 $EXTRA_ENTRY_ID（会什么都不发）")
            return
        }
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().trim()
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 0)
        Log.i(TAG, "onReceive: entryId=$entryId snooze=${snoozeMinutes}min textLen=${text.length}")

        if (snoozeMinutes > 0) {
            snooze(context = context, entryId = entryId, text = text, minutes = snoozeMinutes)
            runCatching { NotificationManagerCompat.from(context).cancel(notifyId(entryId)) }
            return
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "onReceive: 通知总开关是关的（areNotificationsEnabled=false）→ 什么都不发")
            return
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.stash_remind_notify_title))
            .setContentText(text.ifBlank { context.getString(R.string.stash_remind_notify_body_empty) })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    text.ifBlank { context.getString(R.string.stash_remind_notify_body_empty) },
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPanelIntent(context, entryId))
            .addAction(
                0,
                context.getString(R.string.stash_remind_snooze_10),
                snoozeIntent(context, entryId, text),
            )
            .setAutoCancel(true)
            .build()
        // ⚠️ 这里**不再**用 runCatching 吞异常：发不出去必须留下痕迹（用户就是这么踩的坑）。
        try {
            manager.notify(notifyId(entryId), notification)
            Log.i(TAG, "notify 已提交 id=${notifyId(entryId)} channel=$CHANNEL_ID")
        } catch (t: Throwable) {
            Log.e(TAG, "notify 失败（渠道/权限/ROM 拦截？）id=${notifyId(entryId)}", t)
        }
    }

    /** 稍后提醒：把同一个 PendingIntent 重排到 N 分钟后（闹钟已经响过、已出队，直接重排即可）。 */
    private fun snooze(context: Context, entryId: String, text: String, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pending = PendingIntent.getBroadcast(
            context,
            StashReminderScheduler.requestCodeOf(entryId),
            buildIntent(context, entryId, text).apply { removeExtra(EXTRA_SNOOZE_MINUTES) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
            Log.i(TAG, "snooze: entryId=$entryId 已重排到 +${minutes}min")
        }.onFailure { Log.e(TAG, "snooze 失败 entryId=$entryId", it) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 换 id 是必须的：渠道的重要性/震动在**创建时**定死，改不了旧的（旧渠道留给历史通知用）。
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stash_remind_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.stash_remind_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 点通知本体 = 打开收纳面板（走中转 Activity，不需要数据层）。 */
    private fun openPanelIntent(context: Context, entryId: String): PendingIntent = PendingIntent.getActivity(
        context,
        notifyId(entryId),
        Intent(context, StashClipboardTrampolineActivity::class.java).apply {
            action = ACTION_OPEN_STASH_PANEL
            // trampoline 是"动作载体"：让它的 onCreate 一定重新跑（否则它只会把旧 task 提到前台，
            // 面板不会真的打开 —— adb 里踩过这个坑）。
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_ENTRY_ID, entryId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 「稍后 10 分钟」：广播回自己，带一个 snooze 标记（request code 与闹钟本体错开，别互相覆盖）。 */
    private fun snoozeIntent(context: Context, entryId: String, text: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        SNOOZE_REQUEST_BASE + entryId.hashCode(),
        buildIntent(context, entryId, text).putExtra(EXTRA_SNOOZE_MINUTES, SNOOZE_MINUTES),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildIntent(context: Context, entryId: String, text: String): Intent =
        Intent(context, StashReminderReceiver::class.java).apply {
            action = ACTION_STASH_REMIND
            putExtra(EXTRA_ENTRY_ID, entryId)
            putExtra(EXTRA_TEXT, text)
        }

    private fun notifyId(entryId: String): Int = NOTIFY_ID_BASE + entryId.hashCode()

    companion object {
        private const val TAG = "StashReminder"

        const val ACTION_STASH_REMIND = "com.slideindex.app.action.STASH_REMIND"
        const val ACTION_OPEN_STASH_PANEL = "com.slideindex.app.action.OPEN_STASH_PANEL"
        const val EXTRA_ENTRY_ID = "extra_stash_entry_id"
        const val EXTRA_TEXT = "extra_stash_entry_text"
        const val EXTRA_SNOOZE_MINUTES = "extra_stash_remind_snooze_minutes"

        /** 「稍后 10 分钟」的默认时长。 */
        const val SNOOZE_MINUTES = 10

        /**
         * 渠道 id 从 `stash_remind` 换成 `stash_remind_v2`：老渠道是 DEFAULT（不弹横幅不震动），
         * 而**渠道重要性创建后不可改**，只能换 id 重建。
         */
        private const val CHANNEL_ID = "stash_remind_v2"
        private const val NOTIFY_ID_BASE = 24_100
        private const val SNOOZE_REQUEST_BASE = 26_000
    }
}
