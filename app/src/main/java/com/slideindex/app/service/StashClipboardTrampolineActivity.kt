package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.external.AppLinks
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.history.StashReminderPendingState
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashReminderMirror
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
        // 「完成 / 稍后 / 划掉」都是通知上的动作，不是"打开面板"：走另一条路，**不要**过下面的
        // 无障碍/服务开关门槛（那条路会 toast 权限提示、跳设置页，用户在通知上点"完成"
        // 时既没必要也不该被打断）。
        when (intent?.action) {
            StashReminderReceiver.ACTION_STASH_REMIND_DONE -> {
                handleRemindDone(intent)
                return
            }
            StashReminderReceiver.ACTION_STASH_REMIND_SNOOZE -> {
                handleRemindSnooze(intent)
                return
            }
            StashReminderReceiver.ACTION_STASH_REMIND_DISMISSED -> {
                handleRemindDismissed(intent)
                return
            }
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
        applyRemindAction(intent) { repo, entryId, appContext ->
            if (repo.reminderOf(entryId) == null) {
                // 没有提醒可取消 = 这条通知已经过期/已被别处处理，什么都不做（尤其不要再写 setDone）。
                Log.i(TAG, "STASH_REMIND_DONE: entryId=$entryId 没有待处理提醒，忽略")
                return@applyRemindAction
            }
            runCatching { repo.setDone(entryId, true) }
                .onFailure { Log.e(TAG, "STASH_REMIND_DONE: setDone 失败 entryId=$entryId", it) }
            runCatching { repo.setReminder(entryId, null) }
                .onFailure { Log.e(TAG, "STASH_REMIND_DONE: setReminder(null) 失败 entryId=$entryId", it) }
            StashReminderScheduler.cancel(appContext, entryId)
            // "通知状态"这一笔（发出时间 / 划掉时间）到这儿也没意义了：留着会让这条 id
            // 一直挂在"我关心过的通知"清单里（判据 1 的候选集），虽然实际不会误判，但没必要攒垃圾。
            StashReminderMirror.clearNotified(appContext, entryId)
            Log.i(TAG, "STASH_REMIND_DONE: entryId=$entryId 已标完成并取消提醒")
        }
    }

    /**
     * 通知上的「稍后 N 分钟」按钮走这里（§0.16.15）：把提醒**写进数据层**，然后重排闹钟，
     * **不打开面板**。
     *
     * 为什么必须写数据层：老实现只重排了系统闹钟，数据层里那条提醒的时间还是**过去**时间，
     * 而卡片严格按数据画 —— 用户点完「稍后」看到的是"⏰ 消失了"（这正是这次要修的 bug）。
     *
     * 与 [handleRemindDone] 不同，这里**不要求**"当前必须已经有提醒"：`setReminder` 是
     * 幂等的"设成这个时间"，条目还在、提醒被别处清过（面板对账删过期）时照写不误，
     * 用户点「稍后」的意图就是"这条 10 分钟后再提醒我"。
     */
    private fun handleRemindSnooze(intent: Intent) {
        val minutes = intent.getIntExtra(StashReminderReceiver.EXTRA_SNOOZE_MINUTES, 0)
            .takeIf { it > 0 } ?: StashReminderReceiver.SNOOZE_MINUTES
        val text = intent.getStringExtra(StashReminderReceiver.EXTRA_TEXT).orEmpty()
        applyRemindAction(intent) { repo, entryId, appContext ->
            val at = System.currentTimeMillis() + minutes * 60_000L
            runCatching { repo.setReminder(entryId, at) }
                .onFailure { Log.e(TAG, "STASH_REMIND_SNOOZE: setReminder 失败 entryId=$entryId", it) }
            // 镜像里那份"通知状态"必须一起清：划掉/完成的记录留着，会让新排的这次提醒
            // 在指示条（StashReminderPendingState）那边被当成"已经被划掉过"。
            StashReminderMirror.clearNotified(appContext, entryId)
            // schedule 会先写镜像（新时间 + 正文）再排闹钟，所以不用手动碰 snooze override；
            // 但**要显式清一次**旧 override：用户可能先点「稍后」、下次打开面板前又点一次，
            // 残留的旧 override 会被 mergeSnoozeOverrides 当成"往前挪"的依据（它只前进不后退，
            // 所以其实无害），清掉只是让偏好文件不留脏数据。
            StashReminderMirror.removeSnoozeOverride(appContext, entryId)
            StashReminderScheduler.schedule(appContext, entryId, at, text)
            Log.i(TAG, "STASH_REMIND_SNOOZE: entryId=$entryId 已改到 +${minutes}min 并重排闹钟")
        }
    }

    /**
     * 通知被**划掉**（`setDeleteIntent`）：只熄掉指示条，**绝不碰数据层**。
     *
     * 为什么不动数据：用户划掉一条提醒通知，语义是"我知道了"，不是"这条不用做了"、
     * 更不是"完成"。数据层保持"提醒已过、条目未完成"这一事实，卡片上那行灰色「已提醒」
     * 也照旧展示；只是"指示条还要不要亮"这件事被记成"用户已经处理过这次通知"
     * （[StashReminderMirror.markDismissed]）。
     */
    private fun handleRemindDismissed(intent: Intent) {
        val entryId = intent.getStringExtra(StashReminderReceiver.EXTRA_ENTRY_ID)
        val appContext = applicationContext
        if (entryId.isNullOrBlank()) {
            Log.w(TAG, "STASH_REMIND_DISMISSED 缺少 ${StashReminderReceiver.EXTRA_ENTRY_ID}")
            finishTransparent()
            return
        }
        StashReminderMirror.markDismissed(appContext, entryId, System.currentTimeMillis())
        Log.i(TAG, "STASH_REMIND_DISMISSED: entryId=$entryId 已记为用户划掉")
        // "指示条还要不要亮"的判据变了（多了一条"用户已经划掉"），所以**当场**推一次状态：
        // `hasPending` 是 Compose 里的 MutableState，这里没有组合环境可读，但**可以写**
        // （它就是给别的组合读的）；不推的话，用户划掉通知回到桌面，流光还会亮到下一次 refresh。
        runCatching { StashReminderPendingState.refresh(appContext) }
        finishTransparent()
    }

    /**
     * 「完成 / 稍后」共用的外壳：取 entryId → 拿数据层 → 在**应用级 scope** 上落盘 → 立刻 finish。
     *
     * ⚠️ 落盘放到 `deps.applicationScope`（不是 lifecycleScope）：这个 Activity 立刻就要 finish，
     * 而 `StashMetaRepository` 的写盘会 `withContext(Dispatchers.IO)` 挂起一次 ——
     * 挂在 lifecycleScope 上时，`finish()` 之后那次挂起会被取消，`stash_meta.json` 可能根本没写成。
     *
     * 数据层没挂上（依赖图还在构造）时**不 toast、不写盘**：通知还在通知栏里
     * （`setAutoCancel` 只在点通知**本体**时生效，点 action 不会撤掉它），用户再点一次即可。
     */
    private fun applyRemindAction(
        intent: Intent,
        action: suspend (StashMetaRepository, String, Context) -> Unit,
    ) {
        val entryId = intent.getStringExtra(StashReminderReceiver.EXTRA_ENTRY_ID)
        if (entryId.isNullOrBlank()) {
            Log.w(TAG, "${intent.action} 缺少 ${StashReminderReceiver.EXTRA_ENTRY_ID}")
            finishTransparent()
            return
        }
        val metaRepo = StashAccess.metaRepository
        if (metaRepo == null) {
            Log.w(TAG, "${intent.action}: 数据层未就绪，忽略 entryId=$entryId（通知仍在，可重试）")
            finishTransparent()
            return
        }
        val appContext = applicationContext
        deps.applicationScope.launch {
            runCatching { action(metaRepo, entryId, appContext) }
                .onFailure { Log.e(TAG, "${intent.action}: 处理失败 entryId=$entryId", it) }
            // 用户已经在通知上按了「完成 / 稍后」→ **这条通知必须撤掉**。
            // 这一步不能省（两件事都靠它）：
            // 1. `setAutoCancel(true)` 只在点通知**本体**时生效，点 action 不会自动撤 ——
            //    不撤的话用户点完「稍后」，那条"该提醒"的通知还杵在通知栏里；
            // 2. 指示条（`StashReminderPendingState`）判据 1 就是"通知还在不在栏里"：
            //    不撤的话「完成」之后它还会亮着（判据 1 命中），灭不掉。
            runCatching {
                NotificationManagerCompat.from(appContext)
                    .cancel(StashReminderScheduler.notifyIdOf(entryId))
            }
            // 落盘 + 撤通知之后**当场**把指示条状态推一次：`hasPending` 是 Compose state，
            // 而这里没有组合环境可读；不推的话，用户点完「完成 / 稍后」回到桌面，指示条还会亮着
            // 直到下一次 refresh（面板再打开 / 2 秒轮询）。同步、廉价。
            runCatching { StashReminderPendingState.refresh(appContext) }
        }
        finishTransparent()
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
