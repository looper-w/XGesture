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
 * §0.16.x（闪念提醒可靠性）又加了「完成」按钮：它同样**不碰数据层**，而是用
 * `PendingIntent.getActivity` 把动作交给 [StashClipboardTrampolineActivity]（那边有稳定的注入点）。
 * 「稍后」这一次点击则落进 [StashReminderMirror]：既写新时间，也写一个 snooze override，
 * 让下次打开面板时能把显示的时间纠正过来。
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
            .addAction(
                0,
                context.getString(R.string.stash_remind_action_done),
                doneIntent(context, entryId),
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
        // 镜像写成新时间：重启后补排用的就是它（不然补排会拿 meta 里的旧时间来排，立刻响一次）。
        // ⚠️ 放在排闹钟之后（不放进 runCatching）：排闹钟失败也该把用户点的"稍后"记下来，
        // 否则这次点击在数据上等于没发生。
        StashReminderMirror.put(context, entryId, at, text)
        // 另外记一个"表里的时间旧了"的标记：这个进程大概率没装 Hilt 数据层，
        // 写不进 `stash_meta.json`，只能等下次面板打开时由
        // StashMetaRepository.mergeSnoozeOverrides / clearExpiredReminders 并回去。
        StashReminderMirror.putSnoozeOverride(context, entryId, at)
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

    /**
     * 「完成」：走 [StashClipboardTrampolineActivity]（它已经是现成的"动作载体"，
     * 见那边的 `ACTION_OPEN_STASH_PANEL` 写法），由它去写数据层。
     *
     * ⚠️ 这里**不能**直接写 `stash_meta.json`：广播的进程可能被 Hilt 都还没构造好，
     * 而且 `BroadcastReceiver.onReceive` 也不适合承担"读整表 → 改 → 整表写回"。
     *
     * request code 用 [DONE_REQUEST_BASE] 另起一段：与闹钟（[StashReminderScheduler.requestCodeOf]）、
     * 通知本体（[notifyId]）、稍后（[SNOOZE_REQUEST_BASE]）都错开，避免相互覆盖。
     */
    private fun doneIntent(context: Context, entryId: String): PendingIntent = PendingIntent.getActivity(
        context,
        DONE_REQUEST_BASE + entryId.hashCode(),
        Intent(context, StashClipboardTrampolineActivity::class.java).apply {
            action = ACTION_STASH_REMIND_DONE
            // 与 openPanelIntent 同理：让 trampoline 的 onCreate 一定重新跑
            // （否则 singleTask + noHistory 的它只会把旧 task 提到前台，动作不执行）。
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_ENTRY_ID, entryId)
        },
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

        /**
         * 通知上的「完成」按钮动作。由 [StashClipboardTrampolineActivity] 处理：
         * 把条目标为已完成 + 取消它的提醒（**不打开面板**）。
         */
        const val ACTION_STASH_REMIND_DONE = "com.slideindex.app.action.STASH_REMIND_DONE"

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

        /** 「完成」的 PendingIntent request code 段：另起一段，跟上面三段都错开（见 [doneIntent]）。 */
        private const val DONE_REQUEST_BASE = 27_000
    }
}
