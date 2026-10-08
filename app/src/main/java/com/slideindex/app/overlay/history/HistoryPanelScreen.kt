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
     *
     * ⚠️ 唯一的例外是正文 [composerBlocks]（§0.16.16）：它是 `SnapshotStateList`、**不是** `MutableState`，
     * 代理不了，只能直接引用（理由见那条自己的注释）。
     */
    var composerTags by StashComposerDraft.tags
    /** 加号弹窗里预设的提醒时间（null = 没设）；存下时写到新条目上（§0.16.9）。 */
    var composerReminderAt by StashComposerDraft.reminderAtMs
    /**
     * 「记一条」弹窗的正文草稿（§0.16.16 起是**有序块序列**：文字块与图片块交错）。
     *
     * ⚠️ 它与下面 [composerImagePaths] / [composerText] 两条的关系，就是
     * `StashComposerDraft` KDoc 里写的"**保留镜像**"：
     * - 改正文**只经 `StashComposerDraft.updateBlocks`**（弹窗把 `onBlocksChange` 直接交给它）；
     * - [composerText] / [composerImagePaths] 是它的**只读投影**，由 `updateBlocks` 同步，
     *   本文件**不要**再写它们（写回去会被下一次同步覆盖，看起来就是"图忽然回来了"）。
     *
     * 之所以不把本文件所有读点都改成按块读：那会把"空内容判断 / 存下 / 关窗清理"三处
     * 一起推倒重写，而本次要的是"图片进正文"；镜像只多一层同步、语义与老 `text` 完全一致（非空文字块 `\n` 连接）。
     *
     * ⚠️ **直接引用，不要用 `by`**：`SnapshotStateList` 实现的是 `StateObject`、**不是** `State` ——
     * `by` 需要 `getValue(Nothing?, KMutableProperty0<*>)`，写 `by` 直接编译不过
     * （`val blocks: SnapshotStateList<…>` 也一样，别把它声明成 `MutableState<…>`）。
     * 好在它本身就**是** snapshot-aware 的：组合里读它会被正常追踪、`add/remove/set` 也会触发重组，
     * 所以这里一把 `val` 就够；**所有**改动都走 `StashComposerDraft.updateBlocks`（含下面的 `resetComposerBlocks`）。
     */
    val composerBlocks = StashComposerDraft.blocks
    /**
     * 加号弹窗里已选图片（trampoline 解码后落在 cache 的临时文件路径，§0.16.12）。
     *
     * ⚠️ §0.16.16 起它是 [composerBlocks] 里图片块的**只读投影** —— 写它没有意义，
     * 图片现在只活在正文里（插入/删除都在块序列上做）。留这个代理是因为"关窗清临时图"
     * 那一处读它最顺，也方便以后按"有没有图"做判断。
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
     * 把正文草稿清回"一个空文字块"（§0.16.16）。
     *
     * 为什么必须走 `StashComposerDraft.updateBlocks` 而不是 `composerBlocks = emptyList()`：
     * ① 空块不变式（"至少一个文字块"）只在那里维护 —— 直接清空会让弹窗没有任何可放光标的地方；
     * ② `text` / `imagePaths` 两条镜像的同步也挂在那儿，绕过它就会留下"块空了、正文串还在"的假状态。
     */
    val resetComposerBlocks: () -> Unit = {
        StashComposerDraft.updateBlocks { listOf(StashComposerDraft.newEmptyTextBlock()) }
    }
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
            // §0.16.16：清空的判据从"正文串"换成"块序列"—— 回到"一个空文字块"，
            // 镜像（composerText / composerImagePaths）由 `updateBlocks` 一起归零。
            resetComposerBlocks()
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
    /**
     * 存下"一条新闪念"，**按正文里的块顺序**落成有序块（§0.16.12 多图条目 + §0.16.16 块编辑器）。
     *
     * **块顺序**（这才是用户要的"按顺序"）：[StashCoordinator.addRich] 的 `parts` 就是**有序块**，
     * 落盘的 `contentBlocks` 与它一一对应，卡片展开时也按这个顺序画 —— 正文里是什么顺序，
     * 存下来就是什么顺序（老实现要靠光标把正文切成两半再把图夹进去，现在切块在插入那一刻就做完了）。
     *
     * 与老实现的三个差别：
     * - 正文不再有"整段字符串"可言：非空文字块各自成一个 `StashRichPart.Text`（**trim 后**判空）；
     * - 空文字块**跳过**（它是"图片上下还能点到光标"的落点，不是内容）；
     * - 解码失败的图**跳过**并计数（`decodeStashImageFile` 对损坏/已删的临时文件返回 null）。
     *
     * ⚠️ 这里**不**再自己清块：清块交给 [onComposerDone]（它同时负责标签/提醒/筛选）。
     */
    suspend fun submitComposerBlocks() {
        val snapshot = composerBlocks.toList()
        // 解码离开主线程：一张长边 2048 的图解码不便宜。
        // `failedImages` 是普通计数器：它在**同一个** withContext 块里写、块外才读，
        // 不存在跨线程可见性问题（别为它引入 Atomic）。
        var failedImages = 0
        val parts = withContext(Dispatchers.IO) {
            buildList<StashRichPart> {
                // ⚠️ 按**块顺序**走：文字与图片的交错顺序就是用户看到的正文顺序。
                snapshot.forEach { block ->
                    when (block) {
                        is DraftBlock.Text -> {
                            // ⚠️ 空串/纯空白的段不要加：`addRich` 自己也会丢空文本块并 trim，
                            // 但在这里先判一次能让下面的 `parts.isEmpty()` 判断更准。
                            val text = block.value.trim()
                            if (text.isNotEmpty()) add(StashRichPart.Text(text))
                        }

                        is DraftBlock.Image -> {
                            val bitmap = decodeStashImageFile(block.path)
                            if (bitmap == null) failedImages++ else add(StashRichPart.Image(bitmap))
                        }
                    }
                }
            }
        }
        if (parts.isEmpty()) {
            // "全是空块"和"图全解不出来"都落在这里：对用户来说都是"没有可存的内容"，
            // 与老实现的 `stash_composer_empty` 提示一致（本仓库不许新增字符串 key）。
            if (failedImages > 0) showPanelMessage(R.string.stash_save_failed)
            else showPanelMessage(R.string.stash_composer_empty)
            return
        }
        val summary = snapshot.filterIsInstance<DraftBlock.Text>()
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        StashCoordinator.addRich(
            parts = parts,
            onSaved = { newEntryId -> onComposerSaved(newEntryId, summary) },
            onDone = { success ->
                onComposerDone(success)
                if (success) {
                    // 图已经拷进仓库了，cache 里这份临时文件可以删（块的清空在 onComposerDone 里）。
                    snapshot.filterIsInstance<DraftBlock.Image>()
                        .forEach { image -> runCatching { File(image.path).delete() } }
                }
            },
        )
    }
    val submitComposer: () -> Unit = {
        // §0.16.16：判空按**块序列**来 —— 有图块就算有内容（老实现是 `composerImagePaths.isEmpty()`）。
        val hasImage = composerBlocks.any { it is DraftBlock.Image }
        val plainText = composerText.trim()
        when {
            plainText.isEmpty() && !hasImage -> showPanelMessage(R.string.stash_composer_empty)
            // 没有图块：走老的单文本落库路径（`addText`），正文语义与以前完全一致。
            !hasImage -> submitComposerText(plainText)
            else -> scope.launch { submitComposerBlocks() }
        }
    }
    // 重启/更新后 AlarmManager 里的提醒会丢：进面板时补排一次（`StashReminderBootReceiver` 也会在开机时补）。
    // 同一处还负责两件对账（§0.16.15）：把「稍后 10 分钟」写回显示、清掉已过期的提醒。
    /**
     * 提醒的"对账 + 补排"，两处 LaunchedEffect（数据变化 / 面板变可见）共用同一份实现。
     *
     * 顺序不能反（这是这个方法存在的第一理由）：先并回 snooze override，再清过期 ——
     * 反了会把"有效提醒"连 override 一起清掉、救不回来（见 `clearExpiredReminders` 的注释）。
     *
     * ⚠️ 内部的 [stashEntries] / [stashMeta] 是**每次调用时现读**的（它们是 Compose 侧快照，
     * 会随重组更新）：补排要用最新一批条目的正文。
     */
    suspend fun reconcileReminders() {
        // ⚠️ 顺序不能反：先并回 snooze override，再清过期 —— 反了会把"有效提醒"连 override 一起清掉、救不回来。
        val overrides = com.slideindex.app.stash.StashReminderMirror.snoozeOverrides(appContext)
        metaRepo?.mergeSnoozeOverrides(overrides)
        metaRepo?.clearExpiredReminders()
        // 用 `pendingReminders()` 读刚更新过的 store：`stashMeta` 是 Compose 侧快照，拿它会按旧时间再排一遍。
        val reminders = metaRepo?.pendingReminders() ?: stashMeta.reminders
        if (reminders.isEmpty()) return
        val texts = stashEntries.associate { entry ->
            entry.id to (entry.text ?: entry.combinedText())
        }
        StashReminderScheduler.rescheduleAll(appContext, reminders) { entryId ->
            texts[entryId].orEmpty()
        }
    }

    LaunchedEffect(stashEntries.isNotEmpty()) {
        if (stashEntries.isEmpty()) return@LaunchedEffect
        reconcileReminders()
        StashReminderPendingState.refresh(appContext)
    }

    /**
     * 面板**每次变成可见**都跑一次对账（§0.16.15 的第 3 件事）。
     *
     * 为什么必须单独挂一个 effect：上面那条挂在 `stashEntries.isNotEmpty()` 上，
     * 而面板**一直开着**时这个 key 不会变 —— 用户在通知上点了「稍后」（数据层此刻写不进去、
     * 只落在 `StashReminderMirror` 的 snooze override 里），回来一看时间还是旧的；
     * 过期提醒也一直挂着不清理。可见性这个 key 才是"用户现在要看数据了"的正确信号。
     *
     * ⚠️ key 只用 `panelTargetVisible`（一个 Boolean）：它一变只跑一次，不会每帧重跑。
     */
    LaunchedEffect(panelTargetVisible) {
        if (!panelTargetVisible) return@LaunchedEffect
        reconcileReminders()
        StashReminderPendingState.refresh(appContext)
    }

    /**
     * 面板可见期间**慢轮询**指示条状态（§0.16.15）。
     *
     * 为什么还需要它：`hasPending` 的两条判据里，"通知栏里有没有我们那条通知"这件事
     * **不会让 Compose 重组**（它既不来自数据层、也不是 state）—— 提醒在面板开着的时候到点、
     * 通知弹出来，屏幕上唯一的信号就是这条轮询。2 秒是"用户几乎察觉不到延迟"和
     * "别把面板拖慢"之间的折中；一次循环只是一次 `getActiveNotifications` + 一遍提醒表。
     *
     * ⚠️ 轮询体在**协程**里跑（不在组合里）：这里读 `stashMeta` 只取值、不会订阅，所以
     * 不会把面板拖进重组；但**写** state 必须有节制 —— `refresh` 只在值真的变了时才写
     * `hasPending`，`reminderClockMs` 也只在"还有未来提醒"时才推进（见下）。
     *
     * [reminderClockMs] 是同一拍里顺手推进的"卡片用时钟"：卡片那行 ⏰ 是不是该画成灰色
     * 「已提醒」取决于"现在有没有过点"，而过点这件事**不会改任何数据**（`clearExpiredReminders`
     * 要等下一次对账），所以必须有个东西让那一行重组。**只在"还有一条未来的提醒"时才推进**：
     * 没有提醒、或提醒全都已经过点时一次都不写 —— 那两种情况下这一行不需要跟着时钟走。
     */
    var reminderClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(panelTargetVisible) {
        if (!panelTargetVisible) return@LaunchedEffect
        while (true) {
            delay(ReminderPendingPollIntervalMs)
            StashReminderPendingState.refresh(appContext)
            if (stashMeta.reminders.values.any { it > reminderClockMs }) {
                reminderClockMs = System.currentTimeMillis()
            }
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
                                nowMs = reminderClockMs,
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
                            // 关掉弹窗时把**没存下**的正文草稿一起丢掉（老实现只丢图、留正文，
                            // §0.16.16 起正文里就有图块了，而图块的 cache 文件正要被删掉 ——
                            // 只留文字会是"半份草稿"，见 `StashComposerDraft` 的 KDoc）：
                            // 临时图先删文件（cache 里不留垃圾），再经 `updateBlocks` 把块序列
                            // 与镜像（composerText / composerImagePaths）一起归零。
                            //
                            // ⚠️ 顺序不能反：`composerImagePaths` 是 `composerBlocks` 的投影，
                            // 先清块就再也拿不到那批路径，文件会留在 cache 里。
                            composerImagePaths.forEach { path -> runCatching { File(path).delete() } }
                            resetComposerBlocks()
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
                // §0.16.16：正文按**块序列**进出 —— 弹窗内部的插入/切块/退格删图都通过
                // `onBlocksChange` 落回单例的 `updateBlocks`（它同时刷新 text / imagePaths 两条镜像）。
                blocks = composerBlocks,
                onBlocksChange = { transform -> StashComposerDraft.updateBlocks(transform) },
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
                onAddImage = { onPicked ->
                    // §0.16.12：overlay 里不能直接拉系统选图，走中转 Activity（回来的是本地文件路径）。
                    // §0.16.14：先把面板窗挂起（它是无障碍覆盖层，不挂起会盖在相册上面），
                    // 回调里**第一件事**就是恢复 —— 取消（picked 为空）也要恢复。
                    //
                    // §0.16.16：**不在这里插块** —— 路径原样交回弹窗，由它按"当前光标"切块插入
                    // （光标/焦点只活在弹窗里，这里插只能追加到末尾，那就退回老行为了）。
                    StashPanelExternalUi.suspend?.invoke()
                    StashComposerImageTrampolineActivity.launch(appContext) { picked ->
                        StashPanelExternalUi.resume?.invoke()
                        if (picked.isNotEmpty()) onPicked(picked.distinct())
                    }
                },
                onRemoveImage = { path ->
                    // 块的增删已经由弹窗做完了（这里是"删了之后要干什么"）：只负责把 cache 里
                    // 那份临时文件删掉，别留垃圾。`composerImagePaths` 是块的投影，不用再手动减。
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

/**
 * 卡片外那行提醒要怎么画（§0.16.15）：`null` = 这条没有可画的提醒。
 *
 * 用 data class 而不是 `Pair<Long, Boolean>`：那两个字段都是"时间/布尔"，`Pair` 的
 * `.first` / `.second` 在调用点读起来是"猜"，而这个规则本身已经有 4 条分支了。
 */
private data class HistoryReminderRow(
    /** 要显示的时间（过期时就是"响过的那个时间"）。 */
    val atMs: Long,
    /** 已经过期：灰掉 + 前缀「已提醒」。 */
    val overdue: Boolean,
)

/**
 * 把 `reminders` 与 `firedAt` 两个 key 合起来，算出**这一行该怎么画**。
 *
 * 为什么需要两个 key 才知道事实：
 * - [reminderAtMs]（`StashMetaStore.reminders`）= **还没响**的提醒；
 * - [firedAtMs]（`StashMetaStore.firedAt`）= **响过了**的提醒 —— 记录它的原因是数据层
 *   `clearExpiredReminders` 会把过点的那条从 `reminders` 里删掉（不然闹钟一直挂着），
 *   而**删掉不等于没发生过**：用户要看到「已提醒」。
 *
 * 取值规则：
 * 1. 有 `reminders` 条目 → 画它的时间；`<= now` 而且已经响过（说明处在"到点"与"面板对账"
 *    之间那段窗口里）也算过期，这样"刚响、面板还开着"时就已经是灰的了；
 * 2. 只有 `firedAt` → 画"响过的时间"（用户看到的是"已提醒 昨天 21:30"）。
 *
 * 纯函数放在屏幕这一层（而不是塞进 `HistoryTimelineEntryRow`）：卡片那一层只该关心
 * "画成什么颜色"，"什么算过期"是数据语义，留在数据边上更好查。
 */
private fun stashReminderRow(
    reminderAtMs: Long?,
    firedAtMs: Long?,
    nowMs: Long,
): HistoryReminderRow? {
    if (reminderAtMs != null) {
        val overdue = firedAtMs != null || reminderAtMs <= nowMs
        return HistoryReminderRow(atMs = reminderAtMs, overdue = overdue)
    }
    firedAtMs?.let { return HistoryReminderRow(atMs = it, overdue = true) }
    return null
}

@Composable
private fun HistoryStashTabBody(
    allEntries: List<com.slideindex.app.stash.StashEntry>,
    filteredEntries: List<com.slideindex.app.stash.StashEntry>,
    searchQuery: String,
    selectedTag: String?,
    meta: com.slideindex.app.stash.StashMetaStore,
    /**
     * 卡片那行 ⏰ 的"现在"（§0.16.15）。由宿主每 2 秒推进一次（只在"还有未来提醒"时），
     * 这样提醒到点的那一刻，这一行**当场**从主题色变成灰色「已提醒」，而不是等到下次重组。
     */
    nowMs: Long,
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
                            // 提醒那一行（§0.16.15）：**过期的提醒也画**，只是灰掉 + 「已提醒」。
                            // 值来自两个 key：`reminders`（还没响的）与 `firedAt`（响过了、数据层已把
                            // `reminders` 里那条收尾删掉）。只读前者的话，提醒一响这行就整行消失，
                            // 用户看到的就是"我设的提醒不见了"。
                            val reminder = stashReminderRow(
                                reminderAtMs = meta.reminderOf(entry.id),
                                firedAtMs = meta.firedAtOf(entry.id),
                                nowMs = nowMs,
                            )
                            HistoryTimelineEntryRow(
                                entry = entry,
                                group = row.group,
                                lineAlpha = row.lineAlpha,
                                staggerDelayMs = staggerDelay,
                                reminderAtMs = reminder?.atMs,
                                reminderOverdue = reminder?.overdue == true,
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

/**
 * 面板可见期间重新计算「有没有已提醒未处理的条目」的间隔（§0.16.15，给指示条用）。
 *
 * 为什么是轮询而不是事件：那条判据里"通知栏里有我们那条通知"读的是系统 API，**不会**触发
 * Compose 重组（它既不是数据层、也不是 state）。2 秒 = 用户几乎感觉不到的延迟，
 * 而一次循环只是一次 `getActiveNotifications` + 一遍提醒表，代价可以忽略。
 */
private const val ReminderPendingPollIntervalMs = 2_000L