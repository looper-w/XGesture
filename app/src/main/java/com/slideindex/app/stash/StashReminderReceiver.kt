package com.slideindex.app.stash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.R

/**
 * 闪念提醒到点：发一条通知（**不响铃不震动**，避免变成"闹钟"）。
 *
 * 只读 Intent 里带的正文，不碰数据层 —— 进程可能是被这条广播**拉起来**的，
 * 那时 Hilt 仓库还没构造好，去读写元数据文件反而危险。
 */
class StashReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty().trim()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
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
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID_BASE + entryId.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stash_remind_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.stash_remind_channel_desc)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STASH_REMIND = "com.slideindex.app.action.STASH_REMIND"
        const val EXTRA_ENTRY_ID = "extra_stash_entry_id"
        const val EXTRA_TEXT = "extra_stash_entry_text"

        private const val CHANNEL_ID = "stash_remind"
        private const val NOTIFY_ID_BASE = 24_100
    }
}
