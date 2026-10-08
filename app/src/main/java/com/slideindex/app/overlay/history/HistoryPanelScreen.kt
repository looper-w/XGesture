package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.ui.miuix.MiuixBlurredTopBar
import com.slideindex.app.ui.miuix.miuixAppBarColor
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.StashPanelExternalUi
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.StashReminderScheduler
import com.slideindex.app.stash.StashRichPart
import com.slideindex.app.service.StashComposerImageTrampolineActivity
import com.slideindex.app.service.decodeStashImageFile
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.combinedText
import com.slideindex.app.ui.miuix.MiuixSearchField
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

data class HistorySearchBootstrap(
    val tabOrdinal: Int,
    val query: String,
)

@Composable
internal fun HistoryPanelScreen(
    gravityEnd: Boolean,
    panelTargetVisible: Boolean,
    panelBlurActive: Boolean = false,
    blurRadiusDp: Int = 57,
    onDismiss: () -> Unit,
    onToggleSide: () -> Unit,
    requestedTabOrdinal: MutableIntState,
    searchBootstrapEpoch: MutableIntState,
    onSearchFocusChanged: (Boolean) -> Unit,
    onRegisterBackInterceptor: ((() -> Boolean)?) -> Unit,
) {
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: HistoryPanelViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = remember(savedStateOwner) { HistoryPanelViewModelFactory(savedStateOwner) },
    )
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val stashRepo = StashAccess.repository
    val clipboardRepo = ClipboardAccess.repository

    val stashEntries by viewModel.stashEntries.collectAsStateWithLifecycle()
    val filteredStashEntries by viewModel.filteredStashEntries.collectAsStateWithLifecycle()
    val stashSearchQuery by viewModel.stashSearchQuery.collectAsStateWithLifecycle()
    /** 剪贴板**完整**条数（SQL COUNT，含当前固定筛选），不是"已加载条数"。 */
    val clipboardViewCount by viewModel.clipboardViewCount.collectAsStateWithLifecycle()
    val clipboardFilter by viewModel.clipboardFilter.collectAsStateWithLifecycle()
    val filteredClipboardEntries by viewModel.filteredClipboardEntries.collectAsStateWithLifecycle()
    val clipboardSearchQuery by viewModel.clipboardSearchQuery.collectAsStateWithLifecycle()
    val clipboardListLoading by viewModel.clipboardListLoading.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val stashMeta by viewModel.stashMeta.collectAsStateWithLifecycle()
    // 标签名 -> 颜色：卡片底部的标签 chip 与筛选行用的是同一份定义。
    val tagColors = remember(availableTags) { availableTags.associate { it.name to it.colorArgb } }
    val haptics = rememberHistoryHaptics()

    val expandedEntryIds by viewModel.expandedEntryIds.collectAsStateWithLifecycle()
    val selectedImageIndices by viewModel.selectedImageIndices.collectAsStateWithLifecycle()
    var searchFocused by remember { mutableStateOf(false) }
    /**
     * 当前页的列表是否已经滑离顶部 —— 决定头部第一行收不收起来（§0.16.5）。
     *
     * 由**两个页签各自的列表**上报（只有"自己这一页是当前页"时才报，否则两页会互相打架），
     * 因为 `LazyListState` 现在还留在各自的 tab body 里（它是 load-more 判断、新条目回顶等
     * 逻辑的锚点，搬出来动的地方比这个功能本身还多）。
     */
    var listScrolled by remember { mutableStateOf(false) }
    /** 深链带 query 进来时把光标请进搜索框（现在搜索框是常驻的，不再有"展开"这回事）。 */
    val searchFocusRequester = remember { FocusRequester() }
    val searchFocusScope = rememberCoroutineScope()

    /* ---- 面板内直接记（设计稿 `.fab` + `.composer`） ---- */
    var composerOpen by remember { mutableStateOf(false) }
    /**
     * 输入条里快速选中的标签（存下时落到新条目）。
     *
     * ⚠️ 草稿（正文 / 标签 / 提醒 / 已选图）放在**进程级单例** [StashComposerDraft] 里（§0.16.14）：
     * 面板是 overlay 窗，切前台 App / 拉起系统相册时会被系统整个摘掉，下次打开是**全新的组合**，
     * `remember` 的草稿那时全丢（用户感受就是"选完图回来草稿没了"）。这里用
     * `by StashComposerDraft.xxx` 直接代理到单例的 `MutableState` —— 下面所有读写点与以前
     * **一字不差**，只是不再随组合生灭。
     */
    var composerTags by StashComposerDraft.tags
    /** 加号弹窗里预设的提醒时间（null = 没设）；存下时写到新条目上（§0.16.9）。 */
    var composerReminderAt by StashComposerDraft.reminderAtMs
    /**
     * 加号弹窗里已选的图片（trampoline 解码后落在 cache 的临时文件路径，§0.16.12）。
     * 未存下之前只存在草稿里；存下时解码成 `StashRichPart.Image`，一次写成**一条多图条目**。
     */
    var composerImagePaths by StashComposerDraft.imagePaths
    /**
     * 提醒时间选择器为谁而开：`entryId = null` = 加号弹窗里"还没存下的那条"，
     * 非 null = 已经在编辑的某条。
     */
    var reminderPicker by remember { mutableStateOf<HistoryReminderPickerTarget?>(null) }
    var composerText by StashComposerDraft.text
    var composerBarHeight by remember { mutableStateOf(0.dp) }
    /**
     * 就地编辑条（设计稿 `.editbar`）：非 null 就是打开着，且是打开时的快照
     * （正文 / 标签 / 完成态 / 提醒时间的**原值**）。
     *
     * ⚠️ 它**允许**继续是组合内的 state：它整份都能从数据层重建（`entry` + `stashMeta`），
     * 窗被摘掉之后重开编辑条就是重新快照一次而已。真正必须活得比组合长的是
     * "用户改过的那部分" —— 正文 / 标签 / 已选图，那些在 [EditSessionDraft] 里（§0.16.14），
     * 而且是**实时**写进去的（编辑条的 `onTextChange` / `onTagsChange`）。
     *
     * 喂回去的路（窗被系统摘掉 → 组合重建 → 用户再点这条编辑）：
     * ① `openEdit` → [EditSessionDraft.begin]（同 id 保留草稿）→ `editTarget` 重新快照；
     * ② 正文 / 标签走下面的 `barTarget`（用草稿覆盖 `target` 的 `text` / `tagNames`，
     *    编辑条 `remember(target.entryId)` 时取到的就是草稿）；
     * ③ 图片走 [editImagePaths]（本来就是 [EditSessionDraft.imagePaths] 的代理）。
     */
    var editTarget by remember { mutableStateOf<HistoryEditTarget?>(null) }
    var editBarHeight by remember { mutableStateOf(0.dp) }
    /**
     * 就地编辑条里"补图"已选的图片（§0.16.14，与加号弹窗同一套 trampoline 路径）。
     *
     * ⚠️ 它现在代理到**进程级单例** [EditSessionDraft]（和 [StashComposerDraft] 同一套做法）：
     * 以前是 `remember(editTarget?.entryId)`，窗被系统摘掉（切前台 App / 拉起相册，§0.16.13）
     * 之后组合重建，刚选好的图就没了 —— 用户感受是"选完图回来图全丢"。
     * 换条目的清空改由 [EditSessionDraft.begin] 负责（它同时把上一条遗留的临时图删掉），
     * 所以这里不再需要 `remember(entryId)`，下面所有读写点一字不差。
     */
    var editImagePaths by EditSessionDraft.imagePaths
    /** 标签管理浮窗（§0.16.4 待办 2）：与输入条/编辑条**同一套居中模态壳**。 */
    var tagManagerOpen by remember { mutableStateOf(false) }
    val composerFocusRequester = remember { FocusRequester() }
    // overlay 窗里 WindowInsets.ime 常常是 0，必须用这个（设计稿里的 IME 说明也点了名）。
    val overlayImeBottom = com.slideindex.app.overlay.rememberOverlayImeBottomHeight()
    // 只在闪念页签有 FAB（设计稿 `canAdd = ptab === 'stream'`）；编辑条开着时让位给它。
    val composerVisible = selectedTab == HistoryPanelTab.Stash && editTarget == null
    LaunchedEffect(selectedTab) {
        if (selectedTab != HistoryPanelTab.Stash) {
            composerOpen = false
            // 切页签只是把编辑条藏起来：草稿（正文/标签/已选图）留在 [EditSessionDraft] 里，
            // 切回闪念页再点同一条编辑还能接着改。
            editTarget = null
        }
    }
    // 底部给输入条/编辑条让位（两者互斥，取较大者）。
    val bottomSheetHeight = maxOf(
        if (composerOpen) composerBarHeight else 0.dp,
        if (editTarget != null) editBarHeight else 0.dp,
    )

    val activeSearchQuery = when (selectedTab) {
        HistoryPanelTab.Stash -> stashSearchQuery
        HistoryPanelTab.Clipboard -> clipboardSearchQuery
    }
    val onActiveSearchQueryChange: (String) -> Unit = when (selectedTab) {
        HistoryPanelTab.Stash -> viewModel::setStashSearchQuery
        HistoryPanelTab.Clipboard -> viewModel::setClipboardSearchQuery
    }
    val searchHintResId = when (selectedTab) {
        HistoryPanelTab.Stash -> R.string.stash_search_hint
        HistoryPanelTab.Clipboard -> R.string.clipboard_search_hint
    }
    // 条数（设计稿 `.srchrow .n`）：没搜索也没筛标签时给「N 条 · 今天 M」，否则只给「N 条」。
    //
    // ⚠️ N 一律是**完整条数**，不是"已经加载的条数"（§0.16.4 待办 1）：
    // - 闪念：列表整份在内存里（`StashRepository.MAX_ENTRIES = 200`），所以 `stashEntries.size`
    //   就是库总数，"今天 M"也从**全量**数（筛标签时若从筛选结果里数，数字会跟着筛选跳）；
    // - 剪贴板：分页加载，必须问数据库（`clipboardViewCount` = 当前筛选下的 SQL COUNT），
    //   否则往下滑数字会一直涨（用户实测："条数越滑越大"）。
    //
    // 筛标签 / 搜索时给的是**命中条数**（同样是完整值，不随滚动变）：这时若还显示库总数，
    // 用户筛出 3 条却看到"200 条"，只会更困惑。
    val countLabel = when (selectedTab) {
        HistoryPanelTab.Stash -> {
            val todayCount = stashEntries.count {
                historyDayGroupOf(it.createdAtEpochMs, System.currentTimeMillis()) == HistoryDayGroup.Today
            }
            if (stashSearchQuery.isBlank() && selectedTag == null) {
                stringResource(R.string.stash_count_today, stashEntries.size, todayCount)
            } else {
                stringResource(R.string.stash_count, filteredStashEntries.size)
            }
        }
        HistoryPanelTab.Clipboard -> {
            stringResource(R.string.stash_count, clipboardViewCount)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = requestedTabOrdinal.intValue,
        pageCount = { HistoryPanelTab.entries.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setSelectedTab(HistoryPanelTab.entries[pagerState.currentPage])
    }
    LaunchedEffect(requestedTabOrdinal.intValue) {
        val target = requestedTabOrdinal.intValue
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage, panelTargetVisible) {
        if (panelTargetVisible && pagerState.settledPage == HistoryPanelTab.Clipboard.ordinal) {
            viewModel.onClipboardTabActivated(context)
        }
    }
    // 搜索框与输入条/编辑条都需要窗口临时可聚焦（overlay 窗默认 FLAG_NOT_FOCUSABLE）。
    LaunchedEffect(searchFocused, composerOpen, editTarget, tagManagerOpen) {
        onSearchFocusChanged(
            searchFocused || composerOpen || editTarget != null || tagManagerOpen,
        )
    }

    LaunchedEffect(searchBootstrapEpoch.intValue, panelTargetVisible) {
        // 退出动画期间旧组合仍在：绝不能 consume，否则会偷走深链 ?q=。
        if (!panelTargetVisible) return@LaunchedEffect
        if (searchBootstrapEpoch.intValue == 0) return@LaunchedEffect
        val pending = StashPanelLaunchState.consumePendingSearch() ?: return@LaunchedEffect
        val tab = HistoryPanelTab.entries.getOrNull(pending.tabOrdinal) ?: HistoryPanelTab.Stash
        // 先落到目标 Tab，再写 query / 展开，避免搜索框短暂绑到错误 Tab 空串并把 VM 写空。
        requestedTabOrdinal.intValue = pending.tabOrdinal
        if (pagerState.currentPage != pending.tabOrdinal) {
            pagerState.scrollToPage(pending.tabOrdinal)
        }
        viewModel.setSelectedTab(tab)
        when (tab) {
            HistoryPanelTab.Stash -> viewModel.setStashSearchQuery(pending.query)
            HistoryPanelTab.Clipboard -> viewModel.setClipboardSearchQuery(pending.query)
        }
        // 深链带 query 进来：等窗口就绪后把光标请进搜索框（和点击搜索框同一条路）。
        onSearchFocusChanged(true)
        searchFocusScope.launch {
            delay(180)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(
        activeSearchQuery,
        selectedTab,
        composerOpen,
        editTarget,
        tagManagerOpen,
        reminderPicker,
    ) {
        onRegisterBackInterceptor {
            // 返回键依次收：提醒选择器 → 标签管理 → 编辑条 → 输入条 → 清搜索（→ 关面板）。
            when {
                reminderPicker != null -> {
                    reminderPicker = null
                    true
                }
                tagManagerOpen -> {
                    tagManagerOpen = false
                    true
                }
                editTarget != null -> {
                    // ⚠️ **刻意不碰 [EditSessionDraft]**：关掉编辑条 != 放弃这份草稿。
                    // 理由：这条路上"关"的成因太多了 —— 换页签、点空白、把面板收起、
                    // 甚至只是被系统摘掉窗（那时这个 lambda 根本不会被调用），代码分不清
                    // "用户不要了"还是"窗没了"。而**留着的代价只是下次打开这条编辑条看到
                    // 上次改到一半的内容**（数据层没被写过，用户再点保存才生效），
                    // 清掉的代价却是"窗被摘掉回来草稿没了"—— 正是这次要修的毛病。
                    // 清草稿只有两种时机：①保存成功（[EditSessionDraft.clear]）；
                    // ②这条条目本身不在了 —— 删掉它（[EditSessionDraft.clear]），
                    // 或者改去编辑另一条（[EditSessionDraft.begin] 换 entryId 时会作废旧草稿）。
                    editTarget = null
                    true
                }
                composerOpen -> {
                    composerOpen = false
                    true
                }
                else -> consumeExpandableSearchBack(
                    // 搜索框现在是常驻的：只有"有内容"才需要返回键介入（清空查询）。
                    expanded = activeSearchQuery.isNotBlank(),
                    query = activeSearchQuery,
                    onExpandedChange = {},
                    onQueryChange = onActiveSearchQueryChange,
                )
            }
        }
        onDispose { onRegisterBackInterceptor(null) }
    }

    val resources = androidx.compose.ui.platform.LocalResources.current
    // 提示条（设计稿 `.toast`）：**自己实现**，不用 miuix 的 Snackbar —— demo 的位置/配色/时长都不一样：
    // 居中对齐**整块屏幕**的底部 104dp、玻璃底、圆角 14、撤销按钮 h30；
    // 而且**有撤销 4200ms、无撤销 2200ms**（miuix Snackbar 是固定 ~几秒）。
    var toast by remember { mutableStateOf<HistoryToastState?>(null) }
    val scheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val tabLabels = listOf(
        stringResource(R.string.stash_panel_tab),
        stringResource(R.string.clipboard_panel_tab),
    )
    val toastJob = remember { mutableStateOf<Job?>(null) }
    val showPanelMessage: (Int) -> Unit = { messageResId ->
        toastJob.value?.cancel()
        toast = HistoryToastState(resources.getString(messageResId), onUndo = null)
        toastJob.value = scope.launch {
            delay(HistoryToastPlainDurationMs)
            toast = null
        }
    }
    /**
     * 提示 + 撤销。
     *
     * 撤销只有**一个槽**（跟设计稿的 `undoFn` 一致：那里的「撤销」永远指"上一步"）——
     * 计划里写的"撤销栈"在 demo 里就是这个单槽，`SnackbarHostState` 也只暴露一个动作按钮。
     */
    val undoLabel = stringResource(R.string.stash_undo)
    // 上一条提示的协程：新动作来时**取消它**，`showSnackbar` 的清理会顺手把旧提示撤掉。
    // 不用 `newestSnackbarData()`：那是 suspend 方法，语义（没有提示时是返回 null 还是挂着等）
    // 不适合放在"每次动作都要调一次"的路径上，取消协程的行为是确定的。
    val messageJob = remember { mutableStateOf<Job?>(null) }
    val showUndoMessage: (Int, (() -> Unit)?) -> Unit = { messageResId, onUndo ->
        val message = resources.getString(messageResId)
        messageJob.value?.cancel()
        toastJob.value?.cancel()
        toast = HistoryToastState(message, onUndo = onUndo)
        messageJob.value = scope.launch {
            delay(if (onUndo != null) HistoryToastUndoDurationMs else HistoryToastPlainDurationMs)
            toast = null
        }
    }
    val metaRepo = StashAccess.metaRepository

    /* ---------------- 标签管理（§0.16.4 待办 2） ---------------- */

    /** 打开标签管理：输入条/编辑条与它互斥（都是"屏幕居中模态"，同时开着会叠在一起）。 */
    val openTagManager: () -> Unit = {
        composerOpen = false
        editTarget = null
        tagManagerOpen = true
    }
    val addTag: (String, Long) -> Unit = { name, colorArgb ->
        scope.launch {
            val added = metaRepo?.addTag(name, colorArgb) ?: false
            // 重名时数据层什么都不做（不会有两枚同名标签），这里给个为什么没反应。
            if (added) haptics.tick() else showPanelMessage(R.string.stash_tag_exists)
        }
    }
    val renameTag: (String, String) -> Unit = { oldName, newName ->
        haptics.confirm()
        scope.launch { metaRepo?.renameTag(oldName, newName) }
    }
    val setTagColor: (String, Long) -> Unit = { name, colorArgb ->
        haptics.tick()
        scope.launch { metaRepo?.setTagColor(name, colorArgb) }
    }
    /**
     * 拖拽排序落盘（§0.16.5 数据层 + §0.16.6 UI）。
     *
     * 浮窗那边是**本地实时换位**、落下时给最终下标；`moveTag` 的语义（移除后插到第 N 位）
     * 与拖拽过程每一步一致，所以不会出现"看着落在第 2 位、落盘跑到别处"。
     */
    val moveTag: (String, Int) -> Unit = { name, targetIndex ->
        haptics.confirm()
        scope.launch { metaRepo?.moveTag(name, targetIndex) }
    }
    /**
     * 删除标签 + 撤销。
     *
     * `removeTag` 会**连带清掉所有条目的绑定**，所以撤销不能只把标签定义加回来 ——
     * 还要把"原来哪些条目挂着它"原样补回去（快照在删除前取）。
     * 「待办」是硬编码关键字（完成态 / `isTodo` / 把手 `pendingTodoCount` 全靠它），这里兜底拒绝。
     */
    val deleteTag: (String) -> Unit = { name ->
        val tag = availableTags.firstOrNull { it.name == name }
        if (tag == null || com.slideindex.app.stash.StashTagEdits.isProtected(name)) {
            showPanelMessage(R.string.stash_tag_protected_hint)
        } else {
            val affected = stashMeta.assignments.filterValues { name in it }.keys.toList()
            haptics.confirm()
            scope.launch {
                metaRepo?.removeTag(name)
                showUndoMessage(R.string.stash_tag_deleted) {
                    scope.launch {
                        metaRepo?.addTag(name, tag.colorArgb)
                        affected.forEach { entryId ->
                            val restored = metaRepo?.tagsOf(entryId).orEmpty() + name
                            metaRepo?.setTags(entryId, restored.distinct())
                        }
                    }
                }
            }
        }
    }

    /**
     * 打开就地编辑条：快照当前正文 / 标签 / 完成态（设计稿 `openEdit`）。
     *
     * §0.16.14：先 [EditSessionDraft.begin] —— 换条目时旧草稿作废（连带删掉它遗留的 cache 临时图），
     * 同一条重复打开**保持**已有草稿（这正是"窗被摘掉再回来，改了一半的正文/勾过的标签/刚选的图
     * 还在"的关键）。快照本身仍然只取数据层原值，草稿的优先级由渲染处的 seed 决定。
     */
    val openEdit: (com.slideindex.app.stash.StashEntry) -> Unit = { entry ->
        composerOpen = false
        EditSessionDraft.begin(entry.id)
        editTarget = HistoryEditTarget(
            entryId = entry.id,
            text = entry.text.orEmpty(),
            tagNames = stashMeta.tagsOf(entry.id),
            done = stashMeta.isDone(entry.id),
            createdAtEpochMs = entry.createdAtEpochMs,
            reminderAtMs = stashMeta.reminderOf(entry.id),
        )
    }

    /**
     * 删除 + 撤销：撤销靠 [com.slideindex.app.stash.StashRepository.restore] 放回**原位置**
     * （`delete` 故意不删图片文件，所以图片条目也能真的恢复）。提醒闹钟跟着一起撤/补。
     */
    val deleteEntry: (com.slideindex.app.stash.StashEntry) -> Unit = { entry ->
        val index = stashEntries.indexOfFirst { it.id == entry.id }
        val reminderAt = stashMeta.reminderOf(entry.id)
        haptics.confirm()
        scope.launch {
            StashReminderScheduler.cancel(appContext, entry.id)
            stashRepo?.delete(entry.id)
            showUndoMessage(R.string.stash_deleted) {
                scope.launch {
                    stashRepo?.restore(entry, index.coerceAtLeast(0))
                    // 撤销删除时把提醒也排回来（meta 里的 reminders 已被 forget 清掉，所以重设）
                    if (reminderAt != null) {
                        metaRepo?.setReminder(entry.id, reminderAt)
                        if (reminderAt > System.currentTimeMillis()) {
                            StashReminderScheduler.schedule(
                                context = appContext,
                                entryId = entry.id,
                                atEpochMs = reminderAt,
                                text = entry.text.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
    val toggleStar: (com.slideindex.app.stash.StashEntry) -> Unit = { entry ->
        haptics.confirm()
        scope.launch {
            stashRepo?.toggleStar(entry.id)
            showUndoMessage(
                if (entry.starred) R.string.stash_star_cleared else R.string.stash_star_marked,
            ) {
                scope.launch { stashRepo?.toggleStar(entry.id) }
            }
        }
    }
    /** 设/清提醒（§0.16.9 起：时间由 [reminderPicker] 选，不再只有"明天 09:00"一档）。 */
    val applyReminder: (Long?) -> Unit = applyReminder@{ at ->
        val target = reminderPicker ?: return@applyReminder
        reminderPicker = null
        val entryId = target.entryId
        if (entryId == null) {
            // 加号弹窗那条还没存下的新条目：先记在本地，存下后再落盘。
            composerReminderAt = at
            return@applyReminder
        }
        val entry = stashEntries.firstOrNull { it.id == entryId }
        val before = stashMeta.reminderOf(entryId)
        haptics.tick()
        scope.launch {
            metaRepo?.setReminder(entryId, at)
            if (at == null) {
                StashReminderScheduler.cancel(appContext, entryId)
            } else {
                StashReminderScheduler.schedule(
                    context = appContext,
                    entryId = entryId,
                    atEpochMs = at,
                    text = entry?.text.orEmpty(),
                )
            }
            // 编辑条的胶囊要立刻反映新时间（它读的是打开时的快照）。
            if (editTarget?.entryId == entryId) editTarget = editTarget?.copy(reminderAtMs = at)
            // 撤销 = 把原来那个时间设回去（注意别递归引用自己：局部 val 不能引用自身）。
            showUndoMessage(if (at == null) R.string.stash_remind_cleared else R.string.stash_remind_set) {
                scope.launch {
                    metaRepo?.setReminder(entryId, before)
                    if (before != null && before > System.currentTimeMillis()) {
                        StashReminderScheduler.schedule(
                            context = appContext,
                            entryId = entryId,
                            atEpochMs = before,
                            text = entry?.text.orEmpty(),
                        )
                    } else {
                        StashReminderScheduler.cancel(appContext, entryId)
                    }
                    if (editTarget?.entryId == entryId) {
                        editTarget = editTarget?.copy(reminderAtMs = before)
                    }
                }
            }
        }
    }
    val setDone: (com.slideindex.app.stash.StashEntry, Boolean) -> Unit = { entry, done ->
        val reminderAt = stashMeta.reminderOf(entry.id)
        haptics.confirm()
        scope.launch {
            if (done) {
                // 设计稿：`if (s.done) s.remind = null` —— 完成即取消提醒，否则到点还响。
                StashReminderScheduler.cancel(appContext, entry.id)
                if (reminderAt != null) metaRepo?.setReminder(entry.id, null)
            }
            metaRepo?.setDone(entry.id, done)
            showUndoMessage(
                if (done) R.string.stash_done_marked else R.string.stash_undone_marked,
            ) {
                scope.launch {
                    metaRepo?.setDone(entry.id, !done)
                    if (done && reminderAt != null && reminderAt > System.currentTimeMillis()) {
                        metaRepo?.setReminder(entry.id, reminderAt)
                        StashReminderScheduler.schedule(
                            context = appContext,
                            entryId = entry.id,
                            atEpochMs = reminderAt,
                            text = entry.text.orEmpty(),
                        )
                    }
                }
            }
        }
    }
    // 存下：设计稿 `addFromComposer()` —— 空内容只提示；成功后清空输入、**清掉搜索与标签筛选**
    // （否则新条目可能被筛掉、看不见），并保持输入条打开以便连着记。
    //
    // §0.16.12：带图片时走 `addRich`（一条多图 —— 条目本身早就支持多图块）。
    // 三个 lambda **先声明后引用**：Kotlin 的局部 lambda 不能向前引用。
    val onComposerSaved: (String, String) -> Unit = { newEntryId, text ->
        haptics.confirm()
        // 快速选的标签落到新条目上（有选才写，避免无谓的元数据写入）。
        if (composerTags.isNotEmpty()) {
            val tagsToApply = composerTags.toList()
            scope.launch { metaRepo?.setTags(newEntryId, tagsToApply) }
        }
        // §0.16.9：加号弹窗里预设的提醒，也在拿到新条目 id 之后落盘 + 排闹钟。
        composerReminderAt?.let { at ->
            scope.launch {
                metaRepo?.setReminder(newEntryId, at)
                StashReminderScheduler.schedule(
                    context = appContext,
                    entryId = newEntryId,
                    atEpochMs = at,
                    text = text,
                )
            }
        }
        showUndoMessage(R.string.stash_saved) {
            scope.launch { stashRepo?.delete(newEntryId) }
        }
    }
    val onComposerDone: (Boolean) -> Unit = { success ->
        if (success) {
            composerText = ""
            composerTags = emptySet()
            composerReminderAt = null
            // 设计稿 `addFromComposer()` 里 `filter = null; query = ''`：
            // 不清筛选的话新条目可能正好落在筛选之外，用户会以为没存上。
            //
            // ⚠️ 这里**只清查询、不收起搜索框** —— 收起会触发搜索框自己的
            // `focusManager.clearFocus()`，把刚拿到焦点的输入条连输入法一起踢掉。
            viewModel.setStashSearchQuery("")
            viewModel.setSelectedTag(null)
        } else {
            showPanelMessage(R.string.stash_save_failed)
        }
    }
    val submitComposerText: (String) -> Unit = { value ->
        StashCoordinator.addText(
            value,
            onSaved = { newEntryId -> onComposerSaved(newEntryId, value) },
            onDone = onComposerDone,
        )
    }
    val submitComposerRich: (String, List<String>) -> Unit = { value, images ->
        scope.launch {
            // 解码离开主线程：一张长边 2048 的图解码不便宜。
            val parts = withContext(Dispatchers.IO) {
                buildList<StashRichPart> {
                    if (value.isNotEmpty()) add(StashRichPart.Text(value))
                    images.forEach { path ->
                        decodeStashImageFile(path)?.let { add(StashRichPart.Image(it)) }
                    }
                }
            }
            if (parts.isEmpty()) {
                showPanelMessage(R.string.stash_save_failed)
                return@launch
            }
            StashCoordinator.addRich(
                parts = parts,
                onSaved = { newEntryId -> onComposerSaved(newEntryId, value) },
                onDone = { success ->
                    onComposerDone(success)
                    if (success) {
                        composerImagePaths = emptyList()
                        // 图已经拷进仓库了，cache 里这份临时文件可以删。
                        images.forEach { path -> runCatching { File(path).delete() } }
                    }
                },
            )
        }
    }
    val submitComposer: () -> Unit = {
        val value = composerText.trim()
        val images = composerImagePaths
        when {
            value.isEmpty() && images.isEmpty() -> {
                showPanelMessage(R.string.stash_composer_empty)
            }
            images.isEmpty() -> submitComposerText(value)
            else -> submitComposerRich(value, images)
        }
    }
    // 重启/更新后 AlarmManager 里的提醒会丢：进面板时补排一次（`StashReminderBootReceiver` 也会在开机时补）。
    // 同一处还负责两件对账（§0.16.15）：把「稍后 10 分钟」写回显示、清掉已过期的提醒。
    LaunchedEffect(stashEntries.isNotEmpty()) {
        if (stashEntries.isEmpty()) return@LaunchedEffect
        // ⚠️ 顺序不能反：先并回 snooze override，再清过期 —— 反了会把"有效提醒"连 override 一起清掉、救不回来。
        val overrides = com.slideindex.app.stash.StashReminderMirror.snoozeOverrides(appContext)
        metaRepo?.mergeSnoozeOverrides(overrides)
        metaRepo?.clearExpiredReminders()
        // 用 `pendingReminders()` 读刚更新过的 store：`stashMeta` 是 Compose 侧快照，拿它会按旧时间再排一遍。
        val reminders = metaRepo?.pendingReminders() ?: stashMeta.reminders
        if (reminders.isEmpty()) return@LaunchedEffect
        val texts = stashEntries.associate { entry ->
            entry.id to (entry.text ?: entry.combinedText())
        }
        StashReminderScheduler.rescheduleAll(appContext, reminders) { entryId ->
            texts[entryId].orEmpty()
        }
    }

    // ⚠️ 宽度用**布局约束**（maxWidth），不用 `LocalWindowInfo.containerSize`：
    // 真机上后者会返回旋转过的显示尺寸（实测 2340x1080 vs 窗口 1080x2340），
    // 算出来比窗口还宽 → 面板被裁成满屏。见 `panelWidthOf` 的注释。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = if (gravityEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // 设计稿是 26dp（--r-2xl），且只圆内侧。
        val panelShape = if (gravityEnd) {
            RoundedCornerShape(
                topStart = HistoryPanelCornerRadius,
                bottomStart = HistoryPanelCornerRadius,
            )
        } else {
            RoundedCornerShape(
                topEnd = HistoryPanelCornerRadius,
                bottomEnd = HistoryPanelCornerRadius,
            )
        }
        val isLocalBlurActive = panelBlurActive
        val density = LocalDensity.current
        val cornerPx = with(density) { HistoryPanelCornerRadius.toPx() }
        val blurRadiusPx = with(density) { blurRadiusDp.toFloat().dp.toPx() }.roundToInt()
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val frostedTint = if (isDark) 0x661C1C1E.toInt() else 0x66F5F5F7.toInt()
        // 跟手拉出的进度（0..1）：拖动时贴手指、松手后弹簧收尾。
        // 收起动画播完才叫宿主关窗（见 rememberPanelRevealProgress）。
        val revealProgress = rememberPanelRevealProgress(
            panelTargetVisible = panelTargetVisible,
            onRetracted = onDismiss,
        )
        val theme = historyTheme()
        // 面板宽度 = 窗口宽 × 78%（窗口又回到满屏了，见 `OverlayPanelLayoutParams`）。
        val panelWidth = panelWidthOf(maxWidth)
        val panelWidthPx = with(density) { panelWidth.toPx() }
        // 遮罩：整屏压暗 20%，跟手时按进度淡入（拖动期间不压暗，避免"手指刚动整屏先暗"）。
        val scrimAlpha by animateFloatAsState(
            targetValue = if (HistoryPanelReveal.dragging) 0f else theme.scrim.alpha * revealProgress,
            animationSpec = tween(HistoryDurations.d2),
            label = "panelScrim",
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(theme.scrim.copy(alpha = scrimAlpha)),
        )

        // 提示条挂在**全屏那一层**：设计稿 `.toast { left:50%; bottom:104px }` 是相对整块手机屏居中的，
        // 不是相对面板 —— 面板只有 78% 宽，放里面就会贴着面板左缘。
        Box(
            modifier = Modifier
                // 必须压在面板之上：它和面板是兄弟节点，只靠声明顺序会被面板盖住（用户实测）。
                .zIndex(2f)
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = HistoryToastBottomPadding +
                        if (bottomSheetHeight > 0.dp) bottomSheetHeight else 0.dp,
                ),
        ) {
            HistoryToast(
                state = toast,
                onUndo = { toast = null },
                onSwipeDismiss = {
                    toast = null
                    messageJob.value?.cancel()
                    toastJob.value?.cancel()
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(panelWidth)
                // ⚠️ 位移必须放在 background/border **之前**：`graphicsLayer` 只包住它之后的绘制，
                // 放在后面面板底色会留在最终位置不动，只有内容跟着手指跑（踩过）。
                .graphicsLayer {
                    translationX = if (HistoryPanelReveal.dragSession) {
                        HistoryPanelReveal.offsetPx(
                            progress = revealProgress,
                            spanPx = panelWidthPx,
                            gravityEnd = gravityEnd,
                        )
                    } else {
                        0f
                    }
                }
                // 设计稿 `.g`：玻璃底 + 白描边 + 上下内高光 + 两层投影。
                .shadow(
                    elevation = 18.dp,
                    shape = panelShape,
                    ambientColor = theme.glassShadow,
                    spotColor = theme.glassShadow,
                )
                .clip(panelShape)
                // 设计稿 `[data-glass="off"] .g { background: var(--g-solid) }`：
                // 毛玻璃关掉时必须换成**不透明**底，否则底下的 App 会清清楚楚透出来。
                .background(theme.glassFill)
                .border(width = 1.dp, color = theme.glassBorder, shape = panelShape)
                // `.g::before`：左上一团白色高光（径向渐变）+ 顶部一层竖向高光。
                .drawWithContent {
                    drawContent()
                    val gloss = Color.White.copy(alpha = if (theme.isDark) 0.13f else 0.55f)
                    // `.g::before` 第一条：`radial-gradient(130% 86% at 16% -22%, g-gloss, transparent 56%)`
                    // ⚠️ 那个 **56% 的透明停靠点不能省**：Compose 的 radialGradient 只给两个颜色时是
                    // 从圆心线性淡到半径，白纱会罩满整块面板，**所有文字都会发灰**（这就是"该黑的地方发灰"的根因）。
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to gloss,
                                0.56f to Color.Transparent,
                            ),
                            center = Offset(size.width * 0.16f, -size.height * 0.22f),
                            radius = size.width * 1.30f,
                        ),
                    )
                    // 第二条：`linear-gradient(180deg, g-gloss 55%, transparent 38%)` —— 顶部一层很淡的竖向高光。
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to gloss.copy(alpha = gloss.alpha * 0.55f),
                                0.38f to Color.Transparent,
                            ),
                        ),
                    )
                }

                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            // 自绘磨砂罩（App 层 RenderEffect）：窗口稳定时是真模糊，拖动中只剩 tint（那层"雾"）——
            // 这是用户认可的那一版观感，别再用系统模糊替换（系统模糊那版已被打回，见 §0.16.3）。
            if (isLocalBlurActive) {
                com.slideindex.app.overlay.LocalFrostedGlassBackdrop(
                    modifier = Modifier.matchParentSize(),
                    cornerRadiusPx = cornerPx,
                    blurRadiusPx = blurRadiusPx,
                    tintColor = frostedTint,
                    enabled = true,
                )
            }
            val density = LocalDensity.current
            // 设计稿的头部是**独立的 flex 行**（`.head { flex: 0 0 auto }`），列表在它下面滚动，
            // 不是"内容从顶栏下面穿过去"。所以这里用 Column，不做顶栏覆盖，也就不需要
            // 顶栏毛玻璃（那层毛玻璃是 App 自己的旧做法，见 plan §0.16）。
            Column(modifier = Modifier.fillMaxSize()) {
                HistoryPanelHeader(
                    tabLabels = tabLabels,
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    searchQuery = activeSearchQuery,
                    onSearchQueryChange = onActiveSearchQueryChange,
                    searchHint = stringResource(searchHintResId),
                    searchFocusRequester = searchFocusRequester,
                    countLabel = countLabel,
                    onSearchFocusChanged = { searchFocused = it },
                    // ⚠️ 不走"计数器 + LaunchedEffect"：真机上那个 effect 压根没被触发（日志实测），
                    // 直接在回调里做「让窗口可聚焦 → 延时抢焦点」，和输入条那条路一样。
                    // 只负责把窗口切成可聚焦；抢焦点由搜索框自己在 `LaunchedEffect(editorEnabled)` 里做。
                    onSearchRequestFocus = { onSearchFocusChanged(true) },
                    onDismiss = onDismiss,
                    // 收起条件：滑离顶部 **且** 没在搜索（有查询词时那颗词得一直看得见，
                    // 输入框聚焦时更不能收 —— 收起会把输入框从组合里摘掉，输入法会当场掉）。
                    collapsed = listScrolled && activeSearchQuery.isBlank() && !searchFocused,
                    chipRow = {
                        // 两个页签同一位置的一行胶囊：闪念 = 标签（末尾 ＋ 进管理），剪贴板 = 固定筛选。
                        when (selectedTab) {
                            HistoryPanelTab.Stash -> HistoryTagChips(
                                tags = availableTags,
                                selectedTag = selectedTag,
                                onTagSelected = viewModel::setSelectedTag,
                                onManageTags = openTagManager,
                            )
                            HistoryPanelTab.Clipboard -> HistoryClipboardFilterChips(
                                selected = clipboardFilter,
                                onSelected = viewModel::setClipboardFilter,
                            )
                        }
                    },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    beyondViewportPageCount = 0,
                ) { page ->
                    // 换页动效（设计稿 `.scroll.tabin-r/.tabin-l`）：位移 16dp + 淡入。
                    // 翻页比例在 graphicsLayer 的块里读 —— 那是「绘制阶段」的读取，
                    // 只会重绘不会重组（拿它当普通值读会每帧重组整页）。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                                alpha = (1f - abs(pageOffset) * PAGE_SWITCH_FADE).coerceIn(
                                    PAGE_SWITCH_MIN_ALPHA,
                                    1f,
                                )
                                translationX = -pageOffset * PAGE_SWITCH_SLIDE.toPx()
                            },
                    ) {
                        when (HistoryPanelTab.entries[page]) {
                            HistoryPanelTab.Stash -> HistoryStashTabBody(
                                allEntries = stashEntries,
                                filteredEntries = filteredStashEntries,
                                searchQuery = stashSearchQuery,
                                selectedTag = selectedTag,
                                meta = stashMeta,
                                tagColors = tagColors,
                                haptics = haptics,
                                isActive = selectedTab == HistoryPanelTab.Stash,
                                panelBlurActive = panelBlurActive,
                                listBottomPadding = bottomSheetHeight,
                                repo = stashRepo,
                                expandedEntryIds = expandedEntryIds,
                                selectedImageIndices = selectedImageIndices,
                                onToggleExpanded = viewModel::toggleExpanded,
                                onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                                onSetDone = setDone,
                                onToggleStar = toggleStar,
                                onDeleteEntry = deleteEntry,
                                onEditEntry = openEdit,
                                onClearSearch = { viewModel.setStashSearchQuery("") },
                                onClearTagFilter = { viewModel.setSelectedTag(null) },
                                onShowMessage = showPanelMessage,
                                onListScrolledChange = { listScrolled = it },
                            )
                            HistoryPanelTab.Clipboard -> HistoryClipboardTabBody(
                                totalCount = clipboardViewCount,
                                filter = clipboardFilter,
                                filteredEntries = filteredClipboardEntries,
                                searchQuery = clipboardSearchQuery,
                                haptics = haptics,
                                isActive = selectedTab == HistoryPanelTab.Clipboard,
                                panelBlurActive = panelBlurActive,
                                loading = clipboardListLoading,
                                clipboardRepo = clipboardRepo,
                                expandedEntryIds = expandedEntryIds,
                                selectedImageIndices = selectedImageIndices,
                                onToggleExpanded = viewModel::toggleExpanded,
                                onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                                onEnsureLoaded = viewModel::ensureClipboardPagesLoaded,
                                onLoadMore = viewModel::loadMoreClipboard,
                                onShowMessage = showPanelMessage,
                                onListScrolledChange = { listScrolled = it },
                            )
                        }
                    }
                }
            }
            // 浮层（提示条 / 输入条 / 编辑条）叠在列表之上；它们要 BottomCenter 对齐，
            // 所以必须待在 BoxScope 里（Column 没有 align）。
            Box(modifier = Modifier.fillMaxSize()) {
                // 设计稿的提示条是**相对整屏**底部 104dp 居中（不是相对面板）；
                // 输入条/编辑条打开时再顶上去，免得被盖住。
                // 面板内只留 FAB（右下角）。输入条与编辑条都搬到"全屏居中模态层"了
                // （用户要求：别局限在面板里）。
                HistoryComposerFabSlot(
                    visible = composerVisible,
                    open = composerOpen,
                    onOpenChange = {
                        composerOpen = it
                        if (!it) {
                            composerTags = emptySet()
                            composerReminderAt = null
                            // 关掉弹窗时把没存下的临时图一起删掉（cache 里不留垃圾）。
                            composerImagePaths.forEach { path -> runCatching { File(path).delete() } }
                            composerImagePaths = emptyList()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // ---------------- 全屏居中模态层 ----------------
        // 窗口是满屏的，所以输入条 / 编辑条 / 标签管理都能真正居中在**屏幕**上（而不是面板那 78% 里）；
        // 底下那层压暗同样是满屏的，点空白即关闭当前浮窗。
        val modalOpen = composerOpen || editTarget != null || tagManagerOpen || reminderPicker != null
        if (modalOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(3f)
                    .background(theme.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // 点空白 = 主动取消编辑：**保留** [EditSessionDraft]（理由见返回键那条路
                        // 的注释：分不清"不要了"和"窗被摘掉"，而保留的代价只是下次接着改）。
                        when {
                            reminderPicker != null -> reminderPicker = null
                            tagManagerOpen -> tagManagerOpen = false
                            editTarget != null -> editTarget = null
                            else -> composerOpen = false
                        }
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(4f)
                .width(maxWidth * 0.96f)
                .widthIn(max = 720.dp),
        ) {
            // 提醒选择器开着时，其它几块浮窗先不渲染：它们全是"屏幕居中卡片"，叠在一起会糊成一片。
            val showPanelLayers = reminderPicker == null
            HistoryTagManagerModal(
                open = tagManagerOpen && showPanelLayers,
                tags = availableTags,
                imeBottom = overlayImeBottom,
                haptics = haptics,
                onDismiss = { tagManagerOpen = false },
                onAdd = addTag,
                onRename = renameTag,
                onSetColor = setTagColor,
                onDelete = deleteTag,
                onMove = moveTag,
                modifier = Modifier.fillMaxWidth(),
            )
            HistoryComposerModal(
                open = composerOpen && showPanelLayers,
                text = composerText,
                onTextChange = { composerText = it },
                onSubmit = submitComposer,
                onVoiceError = showPanelMessage,
                availableTags = availableTags,
                selectedTags = composerTags,
                onToggleTag = { name ->
                    composerTags = if (name in composerTags) composerTags - name else composerTags + name
                },
                reminderAtMs = composerReminderAt,
                onReminderClick = {
                    reminderPicker = HistoryReminderPickerTarget(
                        entryId = null,
                        initialAtMs = composerReminderAt,
                    )
                },
                imagePaths = composerImagePaths,
                onAddImage = {
                    // §0.16.12：overlay 里不能直接拉系统选图，走中转 Activity（回来的是本地文件路径）。
                    // §0.16.14：先把面板窗挂起（它是无障碍覆盖层，不挂起会盖在相册上面），
                    // 回调里**第一件事**就是恢复 —— 取消（picked 为空）也要恢复。
                    StashPanelExternalUi.suspend?.invoke()
                    StashComposerImageTrampolineActivity.launch(appContext) { picked ->
                        StashPanelExternalUi.resume?.invoke()
                        if (picked.isNotEmpty()) {
                            composerImagePaths = (composerImagePaths + picked).distinct()
                        }
                    }
                },
                onRemoveImage = { path ->
                    composerImagePaths = composerImagePaths - path
                    runCatching { File(path).delete() }
                },
                imeBottom = overlayImeBottom,
                focusRequester = composerFocusRequester,
                onBarHeightChanged = { composerBarHeight = it },
            )                // 就地编辑条（设计稿 `.editbar`）：改正文 / 改标签 / 追加 / 完成 / 删除。
                if (showPanelLayers) editTarget?.let { target ->
                    /**
                     * 草稿只有属于**当前这条**时才算数：`EditSessionDraft` 是进程级单例，
                     * 万一残留的是别的条目的（理论上 `begin` 已经处理过，这里再兜一道），
                     * 拿它当初始值就会把别人的正文/标签灌进这一条 —— 那是数据串条，比丢草稿严重得多。
                     */
                    val draft = EditSessionDraft.takeIf { it.entryId.value == target.entryId }
                    /**
                     * 喂给编辑条的**初始值**：有草稿用草稿，没有就用条目原值。
                     *
                     * ⚠️ 只能走"换一个 target 实例"这条缝 —— `HistoryPanelEditBar` 不允许改，
                     * 它的正文/标签是内部的 `remember(target.entryId)`（只在 entryId 变时重新取初值），
                     * 所以这里的 copy **必须保持 entryId 不变**：否则每打一个字都会重组出一个新
                     * entryId 快照，把输入框里的内容整段冲掉（比丢草稿还糟）。
                     *
                     * 这也正是"窗被系统摘掉后重开"能自愈的原因：组合重建 = 编辑条重新 `remember`，
                     * 它就会把草稿里那份正文/标签读回去。
                     */
                    val barTarget = target.copy(
                        text = draft?.text?.value ?: target.text,
                        tagNames = draft?.tags?.value?.toList() ?: target.tagNames,
                    )
                    HistoryPanelEditBar(
                        target = barTarget,
                        availableTags = availableTags,
                        imeBottom = overlayImeBottom,
                        blurActive = panelBlurActive,
                        /**
                         * 正文改成**实时**进草稿（§0.16.14）：编辑条每敲一个字都回调，
                         * 所以"打了一半就切走 App / 拉相册"也不丢 —— 窗被系统摘掉后组合重建，
                         * 用户再点这条编辑时 `barTarget` 就会把这份正文喂回去。
                         *
                         * 空串回落 `null`：老语义是"留空 = 不改正文"（`onSave` 里仍按 `isBlank()`
                         * 判断是否写库），草稿也照这个语义表达"没改过正文"，免得下次打开拿一份
                         * 空草稿把编辑条清空。
                         */
                        // 删空正文时**存空串**，不要回落 null：null 只表示"从没改过正文"，
                        // 否则"我把正文删光了"下次打开会看到原正文复活（D 交接时点出的语义坑）。
                        onTextChange = { value -> EditSessionDraft.text.value = value },
                        onTagsChange = { value -> EditSessionDraft.tags.value = value },
                        onSave = { text, tags ->
                            val entry = stashEntries.firstOrNull { it.id == target.entryId }
                            val beforeText = entry?.text.orEmpty()
                            val beforeTags = stashMeta.tagsOf(target.entryId)
                            // 待追加的图片在**动手之前**取快照：下面会把 editTarget 清掉，
                            // 而草稿（[EditSessionDraft]）可能在保存成功那一刻被整体清掉
                            // （取晚了就读到空的了）。
                            val pendingImages = editImagePaths
                            // ⚠️ 正文/标签**不在这里写草稿**：下面 `onTextChange` / `onTagsChange`
                            // 已经逐字、逐次实时写过了（§0.16.14），到这里草稿就是最新的。
                            scope.launch {
                                // 正文留空 = 不改正文（设计稿 `if (v) s.text = v`），只存标签。
                                val ok = if (text.isBlank()) true else {
                                    stashRepo?.updateText(target.entryId, text) ?: false
                                }
                                metaRepo?.setTags(target.entryId, tags)
                                editTarget = null
                                haptics.confirm()
                                // 正文/标签这条路走完了，草稿的使命就结束了 —— 立刻清掉，
                                // 否则下次打开这条编辑条会拿着一份"已经落盘的旧草稿"当初始值。
                                // （下面还有待追加图片时不清：那份草稿要留到追加成功，见那里。）
                                if (pendingImages.isEmpty()) EditSessionDraft.clear()
                                // 补图这条路**不给撤销**：仓储的追加接口没有"删掉刚追加的那几张"，
                                // 所以这里只提示、不摆一个"撤销"按钮出来骗人（§0.16.14）。
                                if (ok && pendingImages.isEmpty()) {
                                    showUndoMessage(R.string.stash_edit_saved) {
                                        scope.launch {
                                            if (beforeText.isNotBlank()) {
                                                stashRepo?.updateText(target.entryId, beforeText)
                                            }
                                            metaRepo?.setTags(target.entryId, beforeTags)
                                        }
                                    }
                                }
                                if (pendingImages.isNotEmpty()) {
                                    // 解码离开主线程：一张长边 2048 的图解码不便宜。
                                    val bitmaps = withContext(Dispatchers.IO) {
                                        pendingImages.mapNotNull { decodeStashImageFile(it) }
                                    }
                                    if (bitmaps.isEmpty()) {
                                        // 一张都没解出来：正文/标签已经存下了，但图没进去，不能谎称成功。
                                        // 草稿**留着**（临时图也不删）：用户收起再点同一条编辑还能接着重试。
                                        showPanelMessage(R.string.stash_save_failed)
                                    } else {
                                        StashCoordinator.appendImages(target.entryId, bitmaps) { appended ->
                                            if (appended) {
                                                showPanelMessage(R.string.stash_edit_saved)
                                                // 图已经拷进仓库了：cache 里这份临时文件可以删，整份草稿也清掉
                                                // （`clear()` 自己会删 [EditSessionDraft.imagePaths] 里的图，
                                                //  但这里的 `pendingImages` 是保存前的快照，两边都删一道更保险：
                                                //  追加成功时两者本来是同一批路径）。
                                                EditSessionDraft.clear()
                                                pendingImages.forEach { path ->
                                                    runCatching { File(path).delete() }
                                                }
                                            } else {
                                                // 失败：草稿**留着**（临时图也不删），用户可以再点一次保存重试。
                                                showPanelMessage(R.string.stash_save_failed)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onAppend = { appended ->
                            scope.launch {
                                metaRepo?.appendText(target.entryId, appended)
                                haptics.tick()
                                showUndoMessage(R.string.stash_appended) {
                                    scope.launch { metaRepo?.removeLastAppend(target.entryId) }
                                }
                            }
                        },
                        onToggleDone = {
                            val next = !target.done
                            editTarget = target.copy(done = next)
                            stashEntries.firstOrNull { it.id == target.entryId }
                                ?.let { setDone(it, next) }
                        },
                        onToggleReminder = {
                            // §0.16.9：不再"一点就明天 09:00 / 再点清掉"，改成打开时间选择器。
                            reminderPicker = HistoryReminderPickerTarget(
                                entryId = target.entryId,
                                initialAtMs = target.reminderAtMs,
                            )
                        },
                        onDelete = {
                            editTarget = null
                            // 这条都没了，草稿（含 cache 临时图）留着只会挡住下一条 —— 立即作废。
                            EditSessionDraft.clear()
                            stashEntries.firstOrNull { it.id == target.entryId }
                                ?.let { deleteEntry(it) }
                        },
                        onVoiceError = showPanelMessage,
                        imagePaths = editImagePaths,
                        onAddImage = {
                            // §0.16.14：与加号弹窗同一条路 —— 先挂起面板窗，再走中转 Activity 选图；
                            // 回调里第一件事是恢复（取消也要恢复，否则面板一直不可见）。
                            StashPanelExternalUi.suspend?.invoke()
                            StashComposerImageTrampolineActivity.launch(appContext) { picked ->
                                StashPanelExternalUi.resume?.invoke()
                                if (picked.isNotEmpty()) {
                                    editImagePaths = (editImagePaths + picked).distinct()
                                }
                            }
                        },
                        onRemoveImage = { path ->
                            editImagePaths = editImagePaths - path
                            runCatching { File(path).delete() }
                        },
                        onHeightChanged = { editBarHeight = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            // 提醒时间选择器（§0.16.9）：编辑条的「提醒」与加号弹窗的 ⏰ 胶囊都打开它。
            HistoryReminderPickerModal(
                open = reminderPicker != null,
                currentAtMs = reminderPicker?.initialAtMs,
                imeBottom = overlayImeBottom,
                onPick = applyReminder,
                onDismiss = { reminderPicker = null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 提醒时间选择器"为谁而开"（§0.16.9）。
 *
 * `entryId == null` = 加号弹窗里那条**还没存下**的新条目：选到的时间先记在草稿里
 * （`StashComposerDraft.reminderAtMs`，§0.16.14 起它活得比组合长），存下拿到 id 之后再落盘。
 */
private data class HistoryReminderPickerTarget(
    val entryId: String?,
    val initialAtMs: Long?,
)

@Composable
private fun HistoryStashTabBody(
    allEntries: List<com.slideindex.app.stash.StashEntry>,
    filteredEntries: List<com.slideindex.app.stash.StashEntry>,
    searchQuery: String,
    selectedTag: String?,
    meta: com.slideindex.app.stash.StashMetaStore,
    tagColors: Map<String, Long>,
    haptics: HistoryHaptics,
    isActive: Boolean,
    panelBlurActive: Boolean,
    /** 输入条开着时给它让位，否则最后一条会被盖住。 */
    listBottomPadding: Dp,
    repo: com.slideindex.app.stash.StashRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onSetDone: (com.slideindex.app.stash.StashEntry, Boolean) -> Unit,
    onToggleStar: (com.slideindex.app.stash.StashEntry) -> Unit,
    onDeleteEntry: (com.slideindex.app.stash.StashEntry) -> Unit,
    onEditEntry: (com.slideindex.app.stash.StashEntry) -> Unit,
    onClearSearch: () -> Unit,
    onClearTagFilter: () -> Unit,
    onShowMessage: (Int) -> Unit,
    /** 列表滑离顶部 → 上报给宿主决定头部收不收（§0.16.5）。 */
    onListScrolledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collapseThresholdPx = with(LocalDensity.current) { HistoryHeaderCollapseThreshold.roundToPx() }
    // 只有"这一页是当前页"时才上报：两个页签各有一条列表，同时上报会让头部抖。
    LaunchedEffect(isActive, listState) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
            .distinctUntilChanged()
            .collect(onListScrolledChange)
    }
    val topEntryId = allEntries.firstOrNull()?.id
    var previousTopId by remember { mutableStateOf<String?>(null) }
    var previousIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 刚存下的那条：闪一下高亮环（设计稿 `.item.flash` + `setTimeout(1500)`）。
    var flashEntryId by remember { mutableStateOf<String?>(null) }
    // 首屏错开淡入：只在"这一页刚变成当前页"的这段时间里给行加延迟，
    // 过了窗口就不加 —— 否则往下滚出来的每一行都会再演一遍进场。
    var staggerPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        staggerPlaying = true
        delay(HistoryRowStaggerStepMs * HistoryRowStaggerMaxCount + STAGGER_TAIL_MS)
        staggerPlaying = false
    }
    LaunchedEffect(flashEntryId) {
        val id = flashEntryId ?: return@LaunchedEffect
        delay(HistoryCardFlashVisibleMs)
        if (flashEntryId == id) flashEntryId = null
    }
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || searchQuery.isNotBlank()) {
            previousTopId = topEntryId
            previousIds = allEntries.mapTo(HashSet()) { it.id }
            return@LaunchedEffect
        }
        val prevTop = previousTopId
        val prevIds = previousIds
        previousTopId = topEntryId
        previousIds = allEntries.mapTo(HashSet()) { it.id }
        if (prevTop != null && topEntryId != prevTop && topEntryId !in prevIds) {
            listState.animateScrollToItem(0)
            flashEntryId = topEntryId
        }
    }
    when {
        filteredEntries.isEmpty() -> {
            // 三种成因给三种文案 + 出口（设计稿 `emptyHtml()`）。
            val emptyModifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp, bottom = listBottomPadding)
                
            when {
                allEntries.isEmpty() -> HistoryEmptyState(
                    title = stringResource(R.string.stash_empty_all_title),
                    hint = stringResource(R.string.stash_empty_all_hint),
                    showArrow = true,
                    modifier = emptyModifier,
                )
                searchQuery.isNotBlank() -> HistoryEmptyState(
                    title = stringResource(R.string.stash_empty_search_title, searchQuery),
                    hint = stringResource(R.string.stash_empty_search_hint),
                    actionLabel = stringResource(R.string.stash_empty_search_action),
                    onAction = onClearSearch,
                    modifier = emptyModifier,
                )
                selectedTag != null -> HistoryEmptyState(
                    title = stringResource(R.string.stash_empty_tag_title, selectedTag),
                    hint = stringResource(R.string.stash_empty_tag_hint),
                    actionLabel = stringResource(R.string.stash_empty_tag_action),
                    onAction = onClearTagFilter,
                    modifier = emptyModifier,
                )
                // 兜底：三段都不成立（理论上到不了），沿用既有文案。
                else -> HistoryEmptyState(
                    title = stringResource(R.string.stash_search_empty),
                    modifier = emptyModifier,
                )
            }
        }
        else -> {
            val scheme = MiuixTheme.colorScheme
            // 时间轴槽：分组表头 + 条目平铺成同一条 Lazy 列表（见 HistoryTimelineGrouping.kt）。
            val timelineRows = remember(filteredEntries) { buildHistoryTimelineRows(filteredEntries) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    // 左侧不留白：时间轴槽自己就是左边距（设计稿 `.stream .scroll{padding-left:0}`
                    // + `.grp{padding-left:62px}`）。
                    start = 0.dp,
                    end = 12.dp,
                    top = 2.dp,
                    bottom = 8.dp + listBottomPadding,
                ),
                // 行间距必须为 0：竖线是逐行画的，用 verticalArrangement 留缝会让线断口。
                // 间距改由行内的 padding 提供（分组表头 18dp / 条目 10dp，照设计稿）。
            ) {
                itemsIndexed(timelineRows, key = { _, row -> row.key }) { index, row ->
                    // 首屏错开淡入：只在这段时间里给前若干行加延迟（第 0 行也延一拍，
                    // 这样第一行同样有进场，而不是"只有后面几行在动"）。
                    val staggerDelay = if (staggerPlaying && index < HistoryRowStaggerMaxCount) {
                        (index + 1) * HistoryRowStaggerStepMs
                    } else {
                        0
                    }
                    when (row) {
                        is HistoryTimelineRow.GroupHeader -> HistoryTimelineGroupHeader(
                            group = row.group,
                            lineAlpha = row.lineAlpha,
                            staggerDelayMs = staggerDelay,
                        )
                        is HistoryTimelineRow.Entry -> {
                            val entry = row.entry
                            val expanded = entry.id in expandedEntryIds
                            val selectedIndex = selectedImageIndices[entry.id] ?: 0
                            HistoryTimelineEntryRow(
                                entry = entry,
                                group = row.group,
                                lineAlpha = row.lineAlpha,
                                staggerDelayMs = staggerDelay,
                                reminderAtMs = meta.reminderOf(entry.id),
                            ) {
                                HistoryStashEntryCard(
                                    entry = entry,
                                    expanded = expanded,
                                    onExpandedChange = { onToggleExpanded(entry.id) },
                                    selectedImageIndex = selectedIndex,
                                    onSelectedImageIndexChange = { onSelectedImageIndexChange(entry.id, it) },
                                    meta = meta,
                                    tagColors = tagColors,
                                    onSetDone = { done -> onSetDone(entry, done) },
                                    flash = entry.id == flashEntryId,
                                    dayGroup = row.group,
                                    haptics = haptics,
                                    onEdit = { onEditEntry(entry) },
                                    onShowMessage = onShowMessage,
                                    onPin = {
                                        when (entry.type) {
                                            StashEntryType.TEXT -> StashCoordinator.pinTextToScreen(context, entry.text.orEmpty())
                                            StashEntryType.IMAGE -> {
                                                val bitmap = repo?.loadImage(entry) ?: return@HistoryStashEntryCard
                                                StashCoordinator.pinImageFromStash(context, entry, bitmap)
                                            }
                                            StashEntryType.RICH -> StashCoordinator.pinRichFromStash(context, entry)
                                        }
                                    },
                                    onCopy = {
                                        val ok = StashCoordinator.copyStashEntry(context, entry)
                                        if (ok && entry.type != StashEntryType.IMAGE) {
                                            onShowMessage(R.string.float_ball_text_copied)
                                        }
                                    },
                                    onShare = {
                                        when (entry.type) {
                                            StashEntryType.TEXT -> FloatBallTextPick.shareText(context, entry.text.orEmpty())
                                            StashEntryType.IMAGE -> {
                                                val bitmap = repo?.loadImage(entry) ?: return@HistoryStashEntryCard
                                                FloatBallTextPick.shareScreenshot(context, bitmap)
                                            }
                                            StashEntryType.RICH -> {
                                                val combined = entry.combinedText()
                                                if (combined.isNotBlank()) {
                                                    FloatBallTextPick.shareText(context, combined)
                                                } else {
                                                    val fileName = entry.allImageFileNames().firstOrNull()
                                                    val bitmap = fileName?.let { repo?.loadBitmapByFileName(it) }
                                                        ?: return@HistoryStashEntryCard
                                                    FloatBallTextPick.shareScreenshot(context, bitmap)
                                                }
                                            }
                                        }
                                    },
                                    onToggleStar = { onToggleStar(entry) },
                                    onDelete = { onDeleteEntry(entry) },
                                )
                            }
                        }
                    }
                }
                item(key = "stash_list_footer") {
                    Spacer(modifier = Modifier.height(HistoryListFooterPadding))
                }
            }
        }
    }
}

@Composable
private fun HistoryClipboardTabBody(
    /** 当前筛选下的**完整**条数（SQL COUNT），与"已加载条数"无关。 */
    totalCount: Int,
    /** 当前固定筛选：只影响空状态的文案（列表本身已经是筛过的）。 */
    filter: ClipboardHistoryFilter,
    filteredEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    searchQuery: String,
    haptics: HistoryHaptics,
    isActive: Boolean,
    panelBlurActive: Boolean,
    loading: Boolean,
    clipboardRepo: com.slideindex.app.clipboard.ClipboardHistoryRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onEnsureLoaded: () -> Unit,
    onLoadMore: () -> Unit,
    onShowMessage: (Int) -> Unit,
    /** 列表滑离顶部 → 上报给宿主决定头部收不收（§0.16.5）。 */
    onListScrolledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collapseThresholdPx = with(LocalDensity.current) { HistoryHeaderCollapseThreshold.roundToPx() }
    LaunchedEffect(isActive, listState) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
            .distinctUntilChanged()
            .collect(onListScrolledChange)
    }
    val scheme = MiuixTheme.colorScheme
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyClipboardCardPreviewHeightPx()
    val isSearching = searchQuery.isNotBlank()
    val topEntryId = filteredEntries.firstOrNull()?.id
    val shouldLoadMore by remember {
        derivedStateOf {
            if (isSearching || loading) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            filteredEntries.isNotEmpty() && lastVisible >= filteredEntries.lastIndex - 2
        }
    }
    LaunchedEffect(isActive) {
        if (isActive && !isSearching) {
            onEnsureLoaded()
        }
    }
    var previousTopId by remember { mutableStateOf<String?>(null) }
    var previousIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || isSearching) {
            previousTopId = topEntryId
            previousIds = filteredEntries.mapTo(HashSet()) { it.id }
            return@LaunchedEffect
        }
        val prevTop = previousTopId
        val prevIds = previousIds
        previousTopId = topEntryId
        previousIds = filteredEntries.mapTo(HashSet()) { it.id }
        if (prevTop != null && topEntryId != prevTop && topEntryId !in prevIds) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    when {
        filteredEntries.isEmpty() && !loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp)
                    ,
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // 只有"真的什么都没有"才是空历史；筛了分类却一条都没有 = 筛不出来，
                    // 该说"没有匹配"而不是"还没有记录"（闪念页签同款三选一）。
                    text = stringResource(
                        if (totalCount == 0 && filter == ClipboardHistoryFilter.All) {
                            R.string.clipboard_empty
                        } else {
                            R.string.clipboard_search_empty
                        },
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }
        filteredEntries.isEmpty() && loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp)
                    ,
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    // 顶部只留 2dp：上面那行筛选胶囊已经给出了间距（这里原本有 18dp 的"补偿"，
                    // 那是剪贴板页**没有**筛选行时的权宜，现在两个页签都有行了，补偿要撤掉）。
                    top = 2.dp,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = filteredEntries,
                    key = { it.id },
                    contentType = { "clipboard_entry" },
                ) { entry ->
                    val expanded = entry.id in expandedEntryIds
                    val selectedIndex = selectedImageIndices[entry.id] ?: 0
                    HistoryClipboardEntryCard(
                        entry = entry,
                        expanded = expanded,
                        onExpandedChange = { onToggleExpanded(entry.id) },
                        selectedImageIndex = selectedIndex,
                        onSelectedImageIndexChange = { onSelectedImageIndexChange(entry.id, it) },
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        // 剪贴板卡片也按时间分档（设计稿 `clipItemHtml` 用的是同一套 .fresh/.mid/.old）。
                        dayGroup = historyDayGroupOf(entry.createdAtEpochMs, System.currentTimeMillis()),
                        haptics = haptics,
                        onShowMessage = onShowMessage,
                        onCopy = {
                            ClipboardWriter.write(context, entry)
                            onShowMessage(R.string.float_ball_text_copied)
                        },
                        onStash = {
                            StashCoordinator.addFromClipboard(context, entry) { success ->
                                onShowMessage(if (success) R.string.stash_saved else R.string.stash_save_failed)
                            }
                        },
                        onDelete = {
                            ClipboardThumbnailCache.evictEntry(entry)
                            scope.launch { clipboardRepo?.delete(entry.id) }
                        },
                    )
                }
                if (!isSearching && totalCount > 0) {
                    item(key = "clipboard_record_count") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.clipboard_history_float_record_count,
                                totalCount,
                                totalCount,
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = scheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                if (!isSearching) {
                    item(key = "clipboard_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 动效常量（P4） ---------------- */

/** 换页位移（设计稿 `.tabin-r/.tabin-l` 的 16px）。 */
private val PAGE_SWITCH_SLIDE = 16.dp

/** 换页时页内容淡到多透（= 1 - 位移比例 × 这个系数，下限见下）。 */
private const val PAGE_SWITCH_FADE = 0.55f
private const val PAGE_SWITCH_MIN_ALPHA = 0.45f

/** 新条目的高亮保持多久再清状态（设计稿 `setTimeout(() => flashId = null, 1500)`）。 */
private const val HistoryCardFlashVisibleMs = 1_500L

/** 错开淡入窗口的尾量：最后一行延迟之外再留一点动画时间。 */
private const val STAGGER_TAIL_MS = 400L

/** 列表滑离顶部多少距离后收起头部第一行（太小会"一碰就收"，大了又像没收）。 */
private val HistoryHeaderCollapseThreshold = 16.dp