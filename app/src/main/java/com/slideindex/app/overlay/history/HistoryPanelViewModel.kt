package com.slideindex.app.overlay.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashMetaStore
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.stash.StashTag
import com.slideindex.app.stash.matchesQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview

enum class HistoryPanelTab {
    Stash,
    Clipboard,
}

@OptIn(FlowPreview::class)
class HistoryPanelViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val stashRepository: StashRepository?,
    private val clipboardRepository: ClipboardHistoryRepository?,
    private val metaRepository: StashMetaRepository? = null,
) : ViewModel() {

    val stashSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_STASH_SEARCH, "")

    val clipboardSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_CLIPBOARD_SEARCH, "")

    val selectedTab: StateFlow<HistoryPanelTab> =
        savedStateHandle.getStateFlow(KEY_SELECTED_TAB, HistoryPanelTab.Stash)

    /** 标签筛选：null = 「全部」。 */
    val selectedTag: StateFlow<String?> =
        savedStateHandle.getStateFlow<String?>(KEY_SELECTED_TAG, null)

    /** 元数据（标签定义 / 标签绑定 / 完成态 / 追加内容 / 来源）。见 `StashMetaRepository`。 */
    private val metaStore: StateFlow<StashMetaStore> =
        metaRepository?.store ?: MutableStateFlow(StashMetaStore())

    /** 卡片渲染要读的元数据（完成态 / 标签 / 追加 / 来源），与筛选共用同一份。 */
    val stashMeta: StateFlow<StashMetaStore> get() = metaStore

    /** 供筛选行渲染的标签定义，按 order 排序。 */
    val availableTags: StateFlow<List<StashTag>> = metaStore
        .map { store -> store.tags.sortedBy { it.order } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stashEntries: StateFlow<List<StashEntry>> = stashRepository?.entries
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())

    private val _debouncedStashSearchQuery = MutableStateFlow("")

    val filteredStashEntries: StateFlow<List<StashEntry>> = combine(
        stashEntries,
        _debouncedStashSearchQuery,
        selectedTag,
        metaStore,
    ) { entries, query, tag, meta ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            // 没在搜索：标签筛选生效（null = 「全部」）。
            if (tag == null) entries else entries.filter { tag in meta.tagsOf(it.id) }
        } else {
            // 搜索时**忽略标签筛选**（计划 §2 与设计稿 `visible()` 同款）：
            // 搜索是"就近找到那条"，再叠加标签只会让人搜不到自己刚存的东西。
            // 标签名照样参与命中（`matchesQuery` 的 tagNames 形参）。
            entries.filter { it.matchesQuery(trimmed, meta.tagsOf(it.id)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clipboardEntryCount: StateFlow<Int> = clipboardRepository?.entryCount
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
        ?: MutableStateFlow(0)

    private val _clipboardPagedEntries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val _clipboardSearchResults = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val _clipboardListLoading = MutableStateFlow(false)
    private var clipboardReachedEnd = false
    private var clipboardLoadJob: Job? = null
    private var clipboardActivateJob: Job? = null
    private var clipboardPagesInitialized = false

    val clipboardListLoading: StateFlow<Boolean> = _clipboardListLoading.asStateFlow()

    val filteredClipboardEntries: StateFlow<List<ClipboardEntry>> = combine(
        _clipboardPagedEntries,
        _clipboardSearchResults,
        clipboardSearchQuery,
    ) { paged, searched, query ->
        if (query.trim().isEmpty()) paged else searched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expandedEntryIds: StateFlow<Set<String>> =
        savedStateHandle.getStateFlow(KEY_EXPANDED_IDS, emptySet())

    val selectedImageIndices: StateFlow<Map<String, Int>> =
        savedStateHandle.getStateFlow(KEY_IMAGE_INDICES, emptyMap())

    init {
        // 元数据孤儿清理：条目会因为删除 / `MAX_ENTRIES = 200` 裁剪而消失，元数据得跟着收敛。
        // **只在拿到非空列表时做** —— 空列表可能只是还没加载，照它清会把用户元数据抹光。
        viewModelScope.launch {
            val ids = stashRepository?.entries?.value?.map { it.id }.orEmpty()
            if (ids.isNotEmpty()) {
                runCatching { metaRepository?.pruneOrphans(ids) }
            }
        }
        viewModelScope.launch {
            stashSearchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { _debouncedStashSearchQuery.value = it }
        }
        viewModelScope.launch {
            clipboardSearchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { query ->
                    runSearch(query)
                }
        }
        viewModelScope.launch {
            clipboardRepository?.revision?.collect { revision ->
                if (revision == 0L) return@collect
                if (clipboardSearchQuery.value.isNotBlank()) {
                    runSearch(clipboardSearchQuery.value)
                    return@collect
                }
                syncPagedListAfterRevision()
            }
        }
    }

    fun setStashSearchQuery(query: String) {
        savedStateHandle[KEY_STASH_SEARCH] = query
    }

    fun setClipboardSearchQuery(query: String) {
        savedStateHandle[KEY_CLIPBOARD_SEARCH] = query
    }

    fun setSelectedTab(tab: HistoryPanelTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
    }

    fun setSelectedTag(tag: String?) {
        savedStateHandle[KEY_SELECTED_TAG] = tag
    }

    fun toggleExpanded(entryId: String) {
        val current = expandedEntryIds.value
        savedStateHandle[KEY_EXPANDED_IDS] = if (entryId in current) {
            current - entryId
        } else {
            current + entryId
        }
    }

    fun setSelectedImageIndex(entryId: String, index: Int) {
        savedStateHandle[KEY_IMAGE_INDICES] = selectedImageIndices.value + (entryId to index)
    }

    fun onClipboardTabActivated(context: android.content.Context) {
        clipboardActivateJob?.cancel()
        clipboardActivateJob = viewModelScope.launch {
            delay(CLIPBOARD_TAB_ACTIVATE_DELAY_MS)
            // 打开剪贴板面板就是「我要看最新的」：主动补读一次，且不受截图保护 / 自己写剪贴板的 skip 影响。
            clipboardRepository?.catchUpLatestClipboard(
                triggerContext = context,
                skipWhenListening = false,
            )
            if (!clipboardPagesInitialized || _clipboardPagedEntries.value.isEmpty()) {
                refreshClipboardPages(showInitialLoading = _clipboardPagedEntries.value.isEmpty())
            }
        }
    }

    fun ensureClipboardPagesLoaded() {
        if (_clipboardListLoading.value) return
        if (!clipboardPagesInitialized || _clipboardPagedEntries.value.isEmpty()) {
            refreshClipboardPages(showInitialLoading = _clipboardPagedEntries.value.isEmpty())
        }
    }

    fun refreshClipboardPages(showInitialLoading: Boolean = false) {
        if (_clipboardListLoading.value) return
        clipboardLoadJob?.cancel()
        clipboardLoadJob = viewModelScope.launch {
            if (showInitialLoading) {
                _clipboardListLoading.value = true
            }
            try {
                val loaded = loadClipboardPageBatch(more = false)
                _clipboardPagedEntries.value = loaded
                clipboardPagesInitialized = true
            } finally {
                _clipboardListLoading.value = false
            }
        }
    }

    fun loadMoreClipboard() {
        if (_clipboardListLoading.value || clipboardReachedEnd) return
        if (clipboardSearchQuery.value.isNotBlank()) return
        clipboardLoadJob?.cancel()
        clipboardLoadJob = viewModelScope.launch {
            _clipboardListLoading.value = true
            try {
                loadClipboardPageBatch(more = true)
            } finally {
                _clipboardListLoading.value = false
            }
        }
    }

    private suspend fun runSearch(query: String) {
        val repo = clipboardRepository ?: return
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _clipboardSearchResults.value = emptyList()
            return
        }
        _clipboardListLoading.value = true
        try {
            _clipboardSearchResults.value = withContext(Dispatchers.IO) {
                repo.searchHistory(trimmed)
            }
        } finally {
            _clipboardListLoading.value = false
        }
    }

    private suspend fun syncPagedListAfterRevision() {
        val repo = clipboardRepository ?: return
        val current = _clipboardPagedEntries.value
        val freshTop = withContext(Dispatchers.IO) {
            repo.loadHistoryPage(
                createdBeforeMs = null,
                limit = HistoryFloatPagination.PAGE_SIZE,
            )
        }.entries
        if (freshTop.isEmpty()) {
            _clipboardPagedEntries.value = emptyList()
            clipboardPagesInitialized = true
            clipboardReachedEnd = true
            return
        }
        val lastCreated = freshTop.lastOrNull()?.createdAtEpochMs
        val preservedTail = if (lastCreated != null && current.size > freshTop.size) {
            current.filter { it.createdAtEpochMs < lastCreated }
        } else {
            emptyList()
        }
        _clipboardPagedEntries.value = freshTop + preservedTail
        clipboardPagesInitialized = true
        clipboardReachedEnd = freshTop.size < HistoryFloatPagination.PAGE_SIZE && preservedTail.isEmpty()
    }

    private suspend fun loadClipboardPageBatch(more: Boolean): List<ClipboardEntry> {
        val repo = clipboardRepository ?: return emptyList()
        if (!more) {
            clipboardReachedEnd = false
        } else if (clipboardReachedEnd) {
            return _clipboardPagedEntries.value
        }
        val current = if (more) _clipboardPagedEntries.value.toMutableList() else mutableListOf()
        var batchCount = 0
        var cursor: Long? = if (more) current.lastOrNull()?.createdAtEpochMs else null
        while (!clipboardReachedEnd) {
            val page = withContext(Dispatchers.IO) {
                repo.loadHistoryPage(
                    createdBeforeMs = cursor,
                    limit = HistoryFloatPagination.PAGE_SIZE,
                )
            }
            if (page.entries.isEmpty()) {
                clipboardReachedEnd = true
                break
            }
            val existingIds = current.mapTo(mutableSetOf()) { it.id }
            val itemsToAdd = if (current.isEmpty()) {
                page.entries.distinctBy { it.id }
            } else {
                page.entries.filter { existingIds.add(it.id) }
            }
            if (itemsToAdd.isNotEmpty()) {
                current.addAll(itemsToAdd)
                batchCount += itemsToAdd.size
            }
            clipboardReachedEnd = !page.hasMore
            if (batchCount >= HistoryFloatPagination.PAGE_SIZE) break
            if (itemsToAdd.isEmpty()) {
                clipboardReachedEnd = true
                break
            }
            cursor = current.lastOrNull()?.createdAtEpochMs
        }
        if (more) {
            _clipboardPagedEntries.value = current
        }
        clipboardPagesInitialized = current.isNotEmpty() || clipboardReachedEnd
        return current
    }

    companion object {
        private const val KEY_STASH_SEARCH = "stash_search"
        private const val KEY_CLIPBOARD_SEARCH = "clipboard_search"
        private const val KEY_EXPANDED_IDS = "expanded_entry_ids"
        private const val KEY_IMAGE_INDICES = "selected_image_indices"
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val KEY_SELECTED_TAG = "selected_tag"
        /** 侧栏入场动画 + chrome z-order 抬升后再刷新剪贴板，避免与 WM/DB 并发。 */
        private const val CLIPBOARD_TAB_ACTIVATE_DELAY_MS = 450L
    }
}
