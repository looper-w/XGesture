package com.slideindex.app.stash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机 / 应用更新 / 改系统时间或时区之后，把闪念提醒重新排进 `AlarmManager`。
 *
 * 为什么不能只靠"进面板时补排"（[StashReminderScheduler.rescheduleAll] 那个补排点仍在）：
 * 重启后 `AlarmManager` 里什么都不剩，如果用户开机后一周没打开收纳面板，
 * 这一周里所有闪念提醒**一条都不会响** —— 而"提醒"这个功能恰恰是"用户不打开 App 也要生效"的。
 *
 * 数据源是 [StashReminderMirror]（`SharedPreferences`）而**不是** [StashMetaRepository]：
 * 进程此刻是被广播冷启动的，Hilt 图还没建、`stash_meta.json` 还要过跨进程文件锁，
 * 为一个补排动作拉整条依赖链既慢又脆。镜像里已经带了排闹钟需要的全部信息（id / 时间 / 正文）。
 *
 * 这里**刻意不做** `goAsync()` + 协程：全部工作是几次同步文件读 + 若干次 `AlarmManager.set*`，
 * 毫秒级、没有 suspend 点；真需要 `goAsync` 的场合是网络/数据库，不是这个。
 * （`onReceive` 里抛异常会让整个广播失败，所以外面套了 `runCatching`。）
 */
class StashReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        runCatching {
            val scheduled = StashReminderScheduler.rescheduleFromMirror(appContext)
            Log.i(TAG, "$action 后补排闪念提醒 $scheduled 条")
        }.onFailure { Log.e(TAG, "$action 后补排闪念提醒失败", it) }
    }

    private companion object {
        const val TAG = "StashReminderBoot"

        /**
         * `MY_PACKAGE_REPLACED` 也要收：应用更新会杀掉进程，闹钟随之丢失（跟重启同款后果）。
         *
         * `TIME_SET` / `TIMEZONE_CHANGED` 严格说不是必须的（我们存的是 epoch 绝对时刻，
         * 时区变了"几点响"不变；`RTC_WAKEUP` 闹钟在系统时间被改后由系统自己重算），
         * 但用户手动改表时系统可能把闹钟算糊，重排一遍成本极低、能兜住这个边界。
         */
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
