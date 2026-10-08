package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.external.AppLinks
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashReminderReceiver
import com.slideindex.app.stash.StashReminderScheduler
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StashClipboardTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        handleOpenIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        // 「完成」是通知上的按钮，不是"打开面板"：走另一条路，**不要**过下面的
        // 无障碍/服务开关门槛（那条路会 toast 权限提示、跳设置页，用户在通知上点"完成"
        // 时既没必要也不该被打断）。
        if (intent?.action == StashReminderReceiver.ACTION_STASH_REMIND_DONE) {
            handleRemindDone(intent)
            return
        }
        val initialTab = resolveInitialTab(intent)
        val searchQuery = resolveSearchQuery(intent)
        lifecycleScope.launch {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(this@StashClipboardTrampolineActivity)) {
                toast(R.string.gesture_action_stash_panel_permission)
                startActivity(PermissionHelper.accessibilitySettingsIntent())
                finishTransparent()
                return@launch
            }

            val settings = deps.settingsRepository.settings.first()
            if (!settings.serviceEnabled) {
                toast(R.string.gesture_action_stash_panel_permission)
                finishTransparent()
                return@launch
            }

            OverlayServiceLifecycle.syncFromSettings(
                this@StashClipboardTrampolineActivity,
                deps.settingsRepository
            )

            val shown = retryShowPanel(initialTab, searchQuery)
            if (shown) {
                reportShortcutUsage(initialTab)
            } else {
                toast(R.string.shortcut_panel_open_failed)
            }
            finishTransparent()
        }
    }

    private suspend fun retryShowPanel(tab: StashPanelInitialTab, searchQuery: String?): Boolean {
        repeat(SHOW_RETRY_ATTEMPTS) { attempt ->
            if (FloatBallStashPanel.show(this, initialTab = tab, searchQuery = searchQuery)) {
                return true
            }
            if (attempt < SHOW_RETRY_ATTEMPTS - 1) {
                delay(SHOW_RETRY_DELAY_MS)
            }
        }
        return false
    }

    /**
     * 通知上的「完成」按钮走这里：把该条闪念标为已完成 + 取消它的提醒，**不打开面板**。
     *
     * 为什么用「中转 Activity」而不是让 `StashReminderReceiver` 直接写数据层：
     * 广播那条路是刻意不碰数据层的（进程可能是被广播冷启动的，Hilt 依赖图还没构造好，
     * `stash_meta.json` 还要过跨进程文件锁）。这里不一样：Hilt 已经通过
     * `AppDependencies` 进了这个 Activity 的进程，跟面板里 `metaRepo?.setDone(...)` 是同一条路。
     */
    private fun handleRemindDone(intent: Intent) {
        val entryId = intent.getStringExtra(StashReminderReceiver.EXTRA_ENTRY_ID)
        if (entryId.isNullOrBlank()) {
            Log.w(TAG, "STASH_REMIND_DONE 缺少 ${StashReminderReceiver.EXTRA_ENTRY_ID}")
            finishTransparent()
            return
        }
        val metaRepo = StashAccess.metaRepository
        if (metaRepo == null) {
            // 数据层还没挂上（依赖图还在构造）。**不 toast、不写盘**：本轮只允许新增一个字符串资源
            // （`stash_remind_action_done`），编不出提示文案；而通知还在通知栏里
            // （`setAutoCancel` 只在点通知**本体**时生效，点 action 不会撤掉它），用户再点一次即可。
            Log.w(TAG, "STASH_REMIND_DONE: 数据层未就绪，忽略 entryId=$entryId（通知仍在，可重试）")
            finishTransparent()
            return
        }
        val appContext = applicationContext
        // ⚠️ 落盘放到**应用级 scope**（不是 lifecycleScope）：这个 Activity 立刻就要 finish，
        // 而 `StashMetaRepository` 的写盘会 `withContext(Dispatchers.IO)` 挂起一次 ——
        // 挂在 lifecycleScope 上时，`finish()` 之后那次挂起会被取消，`stash_meta.json` 可能根本没写成。
        deps.applicationScope.launch {
            markRemindDone(metaRepo, entryId, appContext)
        }
        finishTransparent()
    }

    /**
     * 真正落盘的三个动作，顺序有讲究：
     *
     * 1. **先判断这条还存不存在提醒**：`setDone(true)` 会往 meta 里写一条 `doneAt`，
     *    对一条早就没有提醒的条目（比如面板里已经点过完成、或条目已删）写就是凭空造出孤儿元数据；
     * 2. `setDone(true)` + `setReminder(null)`；
     * 3. 最后才取消闹钟 —— [StashReminderScheduler.cancel] 里连镜像和 snooze override 一起清了，
     *    所以不用在这里再手动碰 [com.slideindex.app.stash.StashReminderMirror]。
     *    放在最后是因为"取消闹钟"是唯一不可逆的一步（镜像一清，重启也补不回来）。
     */
    private suspend fun markRemindDone(repo: StashMetaRepository, entryId: String, appContext: Context) {
        if (repo.reminderOf(entryId) == null) {
            // 没有提醒可取消 = 这条通知已经过期/已被别处处理，什么都不做（尤其不要再写 setDone）。
            Log.i(TAG, "STASH_REMIND_DONE: entryId=$entryId 没有待处理提醒，忽略")
            return
        }
        runCatching { repo.setDone(entryId, true) }
            .onFailure { Log.e(TAG, "STASH_REMIND_DONE: setDone 失败 entryId=$entryId", it) }
        runCatching { repo.setReminder(entryId, null) }
            .onFailure { Log.e(TAG, "STASH_REMIND_DONE: setReminder(null) 失败 entryId=$entryId", it) }
        StashReminderScheduler.cancel(appContext, entryId)
        Log.i(TAG, "STASH_REMIND_DONE: entryId=$entryId 已标完成并取消提醒")
    }

    private fun reportShortcutUsage(tab: StashPanelInitialTab) {
        val shortcutId = when (tab) {
            StashPanelInitialTab.Stash -> SHORTCUT_ID_STASH
            StashPanelInitialTab.Clipboard -> SHORTCUT_ID_CLIPBOARD
        }
        ShortcutManagerCompat.reportShortcutUsed(this, shortcutId)
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun finishTransparent() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_OPEN_STASH = "com.slideindex.app.action.OPEN_STASH_PANEL"
        const val ACTION_OPEN_CLIPBOARD = "com.slideindex.app.action.OPEN_CLIPBOARD_PANEL"

        const val SHORTCUT_ID_STASH = "stash_panel"
        const val SHORTCUT_ID_CLIPBOARD = "clipboard_panel"

        private const val TAG = "StashTrampoline"

        private const val QUERY_PARAM = AppLinks.QUERY_PARAM

        private const val SHOW_RETRY_ATTEMPTS = 5
        private const val SHOW_RETRY_DELAY_MS = 150L

        fun uriFor(tab: StashPanelInitialTab, query: String? = null): Uri =
            AppLinks.uri(
                path = when (tab) {
                    StashPanelInitialTab.Stash -> AppLinks.PATH_STASH
                    StashPanelInitialTab.Clipboard -> AppLinks.PATH_CLIPBOARD
                },
                query = query
            )

        fun createIntent(context: Context, tab: StashPanelInitialTab, query: String? = null): Intent =
            Intent(Intent.ACTION_VIEW, uriFor(tab, query)).apply {
                setClass(context, StashClipboardTrampolineActivity::class.java)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }

        fun createActionIntent(context: Context, tab: StashPanelInitialTab): Intent =
            Intent(context, StashClipboardTrampolineActivity::class.java).apply {
                action = when (tab) {
                    StashPanelInitialTab.Stash -> ACTION_OPEN_STASH
                    StashPanelInitialTab.Clipboard -> ACTION_OPEN_CLIPBOARD
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }

        fun resolveInitialTab(intent: Intent?): StashPanelInitialTab {
            intent?.data?.let { uri ->
                if (AppLinks.isAppLink(uri)) {
                    return when (uri.pathSegments.firstOrNull()?.lowercase()) {
                        AppLinks.PATH_CLIPBOARD -> StashPanelInitialTab.Clipboard
                        AppLinks.PATH_STASH -> StashPanelInitialTab.Stash
                        else -> StashPanelInitialTab.Stash
                    }
                }
            }
            return when (intent?.action) {
                ACTION_OPEN_CLIPBOARD -> StashPanelInitialTab.Clipboard
                else -> StashPanelInitialTab.Stash
            }
        }

        fun resolveSearchQuery(intent: Intent?): String? {
            intent ?: return null
            intent.getStringExtra(QUERY_PARAM)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

            val data = intent.data
            if (data != null) {
                data.getQueryParameter(QUERY_PARAM)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
                parseRawQueryParam(data.encodedQuery ?: data.query)?.let { return it }
            }

            // am start 等场景下 Uri 可能丢 query，兜底从 dataString 解析
            val dataString = intent.dataString ?: return null
            val marker = "?$QUERY_PARAM="
            val idx = dataString.indexOf(marker, ignoreCase = true)
            if (idx < 0) return null
            val start = idx + marker.length
            val end = dataString.indexOf('&', start).let { if (it < 0) dataString.length else it }
            return Uri.decode(dataString.substring(start, end)).trim().takeIf { it.isNotEmpty() }
        }

        private fun parseRawQueryParam(rawQuery: String?): String? {
            if (rawQuery.isNullOrEmpty()) return null
            for (part in rawQuery.split('&')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val key = Uri.decode(part.substring(0, eq))
                if (!key.equals(QUERY_PARAM, ignoreCase = true)) continue
                return Uri.decode(part.substring(eq + 1)).trim().takeIf { it.isNotEmpty() }
            }
            return null
        }
    }
}
