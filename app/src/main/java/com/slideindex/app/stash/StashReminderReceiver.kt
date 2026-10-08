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
import com.slideindex.app.overlay.history.StashReminderPendingState
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
 *
 * §0.16.15（用户实测"点了稍后，卡片上的 ⏰ 消失了"）：**「稍后」也改成走 trampoline 动作**。
 * 老实现是"广播回自己、只重排系统闹钟 + 写 snooze override"，于是数据层里那条提醒的时间还是
 * **过去**时间 —— 卡片按数据画，看起来就是"提醒没了"（要等下次面板打开对账才把它改回未来）。
 * 现在「稍后」和「完成」一样由 [StashClipboardTrampolineActivity] 立刻 `setReminder(now + N)`，
 * 数据层当场就是对的。**广播那条老分支保留**当兜底（trampoline 起不来时至少闹钟还重排上了）：
 * 两条路都是幂等的且互相覆盖同一个值 —— 都写 `now + N 分钟`，第二次写只是重复一次同样的
 * `setReminder` / `schedule`（`schedule` 的 `FLAG_UPDATE_CURRENT` 就是原地替换闹钟）。
 * 唯一的代价是"同一个值写两遍"，换来的是"Activity 被 ROM 拦掉也不至于漏一整个提醒周期"。
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
            runCatching { NotificationManagerCompat.from(context).cancel(notifyIdOf(entryId)) }
            // 通知被撤掉 + 新时间进了镜像 → 指示条该灭了（这条老路上数据层可能没挂上，
            // 兜底判据里那条"稍后到未来"就是为它准备的）。
            runCatching { StashReminderPendingState.refresh(context) }
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
            // 划掉通知 = 用户"知道了"：只让指示条熄掉（`StashReminderPendingState`），**不碰数据**。
            .setDeleteIntent(dismissedIntent(context, entryId))
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
            manager.notify(notifyIdOf(entryId), notification)
            // 记下"这条通知真的提交了"：指示条（StashReminderPendingState）要靠它区分
            // "通知还在栏里"和"用户已经划掉了"。放在 notify 之后、**成功才写** —— 写早了会把
            // 一次被 ROM 吞掉的发送也当成"通知在栏里"，指示条就会一直亮着不灭。
            StashReminderMirror.putNotifiedAt(context, entryId, System.currentTimeMillis())
            // ⚠️ 指示条要**当场**点亮：`hasPending` 是 Compose state，而通知到点时用户大概率
            // 没打开面板（那才是常态）—— 面板里那条 2 秒轮询根本不在跑。这里不用 `refresh`：
            // 那一刻"数据层挂上了没有 / 通知栏列出来了没有"都还没准（进程可能正是被这条广播
            // 拉起来的），而"我们刚为一条未处理的提醒发了通知"是确定的。
            runCatching { StashReminderPendingState.markPending() }
            Log.i(TAG, "notify 已提交 id=${notifyIdOf(entryId)} channel=$CHANNEL_ID")
        } catch (t: Throwable) {
            Log.e(TAG, "notify 失败（渠道/权限/ROM 拦截？）id=${notifyIdOf(entryId)}", t)
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
        notifyIdOf(entryId),
        Intent(context, StashClipboardTrampolineActivity::class.java).apply {
            action = ACTION_OPEN_STASH_PANEL
            // trampoline 是"动作载体"：让它的 onCreate 一定重新跑（否则它只会把旧 task 提到前台，
            // 面板不会真的打开 —— adb 里踩过这个坑）。
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_ENTRY_ID, entryId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 「稍后 10 分钟」：走 [StashClipboardTrampolineActivity]（和「完成」同一条路），由它
     * `setReminder(now + N)` + [StashReminderScheduler.schedule]。
     *
     * ⚠️ 为什么要绕这一圈而不是像老版本那样"广播回自己重排闹钟"：老路只改系统闹钟、不写数据，
     * 卡片严格按数据画 —— 于是用户点完「稍后」看到的是"⏰ 没了"（§0.16.15 的 bug）。
     *
     * ⚠️ 这里**不**用 `FLAG_ACTIVITY_CLEAR_TASK` 之外的任何"清任务"花招，也**不**打开面板；
     * request code 用 [StashReminderScheduler.SNOOZE_REQUEST_BASE]（26_000+）另起一段，
     * 与闹钟本体（24_000+）、通知本体（24_100+）、完成（27_000+）都错开。
     */
    private fun snoozeIntent(context: Context, entryId: String, text: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            StashReminderScheduler.SNOOZE_REQUEST_BASE + entryId.hashCode(),
            Intent(context, StashClipboardTrampolineActivity::class.java).apply {
                action = ACTION_STASH_REMIND_SNOOZE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_SNOOZE_MINUTES, SNOOZE_MINUTES)
                // 正文随 Intent 带过去：trampoline 排闹钟时用它（镜像里也有，但那是"另一份数据"，
                // 能带就带，免得依赖"镜像一定写过"）。
                putExtra(EXTRA_TEXT, text)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 通知被**划掉**：走 trampoline 的新动作 [ACTION_STASH_REMIND_DISMISSED]（只清 pending、
     * 绝不改数据）。`setAutoCancel(true)` 只在点通知本体时自动撤掉它，划掉这条路由
     * [android.app.Notification.deleteIntent] 走到这里。
     *
     * request code 用 [DISMISS_REQUEST_BASE]（28_000+）：与上面四段都错开。
     */
    private fun dismissedIntent(context: Context, entryId: String): PendingIntent = PendingIntent.getActivity(
        context,
        DISMISS_REQUEST_BASE + entryId.hashCode(),
        Intent(context, StashClipboardTrampolineActivity::class.java).apply {
            action = ACTION_STASH_REMIND_DISMISSED
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_ENTRY_ID, entryId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 「稍后」的**广播兜底**（老实现）：广播回自己、只重排闹钟 + 写镜像。
     *
     * ⚠️ 现在**没有**任何通知按钮指向它了（按钮已经换成 [snoozeIntent] 的 trampoline 动作）。
     * 之所以还留着这一条路：[onReceive] 里 `snoozeMinutes > 0` 的分支是一条**独立可用**的入口，
     * 万一某个 ROM 上"从通知点 Activity"被拦掉，把按钮挂回来就恢复了 —— 比到时候重写一遍便宜。
     * 两条路也不会互相打架：都以 [SNOOZE_MINUTES] 为步长、都以"现在"为基准算新时间，重复执行
     * 只是重复写一次**同样的值**（`setReminder` 值相同就不写盘，`schedule` 的
     * `FLAG_UPDATE_CURRENT` 是原地替换闹钟，不会变成两个闹钟）。
     *
     * ⚠️ 它**故意**用 [StashReminderScheduler.SNOOZE_REQUEST_BASE] 同一个段位值：
     * 这条 Intent 与 trampoline 那条是**不同类型**（`getBroadcast` vs `getActivity`），
     * PendingIntent 的同一性判定是"type + requestCode + Intent.filterEquals()"，
     * 两者 action/component 都不同，不会互相覆盖。这样"按钮在哪一条路上"都只占用一个段位值。
     */
    @Suppress("unused") // 保留的兜底入口：挂回通知按钮就能用，见上面的取舍（故意不留"死代码"以外的痕迹）。
    private fun snoozeBroadcastFallback(context: Context, entryId: String, text: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            StashReminderScheduler.SNOOZE_REQUEST_BASE + entryId.hashCode(),
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
     * 通知本体（[notifyIdOf]）、稍后（[StashReminderScheduler.SNOOZE_REQUEST_BASE]）都错开，避免相互覆盖。
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

    /** 通知 id 的唯一实现在 [StashReminderScheduler.notifyIdOf]（`StashReminderPendingState` 也要用）。 */
    private fun notifyIdOf(entryId: String): Int = StashReminderScheduler.notifyIdOf(entryId)

    companion object {
        private const val TAG = "StashReminder"

        const val ACTION_STASH_REMIND = "com.slideindex.app.action.STASH_REMIND"
        const val ACTION_OPEN_STASH_PANEL = "com.slideindex.app.action.OPEN_STASH_PANEL"

        /**
         * 通知上的「完成」按钮动作。由 [StashClipboardTrampolineActivity] 处理：
         * 把条目标为已完成 + 取消它的提醒（**不打开面板**）。
         */
        const val ACTION_STASH_REMIND_DONE = "com.slideindex.app.action.STASH_REMIND_DONE"

        /**
         * 通知上的「稍后 N 分钟」按钮动作（§0.16.15）。由 [StashClipboardTrampolineActivity] 处理：
         * `setReminder(entryId, now + N)` + 重排闹钟（**不打开面板**）。
         *
         * 与 [ACTION_STASH_REMIND_DONE] 分开成两个 action 而不是"一个 action + 一个分钟数 extra"：
         * trampoline 的分派是按 action 走的，多一个 action 就多一条一眼能看懂的路径；
         * 而分钟数仍然是 extra（[EXTRA_SNOOZE_MINUTES]），这样"稍后 30 分钟"将来不用改 action。
         */
        const val ACTION_STASH_REMIND_SNOOZE = "com.slideindex.app.action.STASH_REMIND_SNOOZE"

        /**
         * 通知被**划掉**（`setDeleteIntent`）。由 [StashClipboardTrampolineActivity] 处理：
         * 只把 `StashReminderPendingState` 熄掉，**不碰数据层**。
         */
        const val ACTION_STASH_REMIND_DISMISSED = "com.slideindex.app.action.STASH_REMIND_DISMISSED"

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

        /**
         * 「完成」的 PendingIntent request code 段：另起一段，跟其它段都错开（见 [doneIntent]）。
         *
         * 段位总表（改这里之前先把四个都看一遍，重复了会造成"点 A 触发 B"）：
         * 闹钟本体 `24_000+`（[StashReminderScheduler.requestCodeOf]）、
         * 通知本体 `24_100+`（[StashReminderScheduler.notifyIdOf]）、
         * 稍后 `26_000+`（[StashReminderScheduler.SNOOZE_REQUEST_BASE]）、
         * 完成 `27_000+`（本条）、划掉 `28_000+`（[DISMISS_REQUEST_BASE]）。
         */
        private const val DONE_REQUEST_BASE = 27_000

        /** 通知「划掉」的 PendingIntent request code 段（见 [dismissedIntent]）。 */
        private const val DISMISS_REQUEST_BASE = 28_000
    }
}
