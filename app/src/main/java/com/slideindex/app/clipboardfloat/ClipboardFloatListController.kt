package com.slideindex.app.clipboardfloat

import com.slideindex.app.clipboard.ClipboardCleanupCategory
import com.slideindex.app.clipboard.ClipboardCleanupPreview
import com.slideindex.app.clipboard.ClipboardCleanupResult
import com.slideindex.app.clipboard.ClipboardCleanupScope
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.clipboard.matchesHistoryFilter
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class ClipboardFloatListController(
    private val repository: ClipboardHistoryRepository?,
    private val scope: CoroutineScope
) {
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ClipboardEntry>>(emptyList())

    /** 按来源应用筛选：null = 全部。 */
    private val _sourceFilter = MutableStateFlow<String?>(null)
    val sourceFilter: StateFlow<String?> = _sourceFilter.asStateFlow()

    /** 当前历史里出现过的来源包名（App chip 的来源）。 */
    private val _availableSources = MutableStateFlow<List<String>>(emptyList())
    val availableSources: StateFlow<List<String>> = _availableSources.asStateFlow()

    /** 只看收藏（类型栏里的「收藏」胶囊）。为 true 时从库里按 `starred = 1` 取一页。 */
    private val _onlyStarred = MutableStateFlow(false)
    val onlyStarred: StateFlow<Boolean> = _onlyStarred.asStateFlow()

    /**
     * **类型筛选**（工具栏下方的类型栏）：全部 / 图片 / 链接 / 富文本 / 文件。
     *
     * 切换时**重新查库**（[reload]），而不是在已加载的几条里过一遍 ——
     * 判据落在 `has_image` / `entry_type` 两个轻量列上，交给 SQL 才不会有"翻了两页就空了"的错觉。
     * 选择只存在于本次会话（关掉浮窗回到「全部」，也就是默认值）。
     */
    private val _historyFilter = MutableStateFlow(ClipboardHistoryFilter.All)
    val historyFilter: StateFlow<ClipboardHistoryFilter> = _historyFilter.asStateFlow()

    /**
     * 类型栏统一的入口：`filter` 与 `onlyStarred` **一次改完、只重新查一次库**。
     *
     * 两者在**同一枚胶囊行里互斥**（只能亮一枚）：选「收藏」时类型回到「全部」，选类型时取消收藏。
     * 所以必须走这个入口 —— 分别调用 [setHistoryFilter] 与 [setOnlyStarred] 会连发两次查询，
     * 列表会先按类型刷一遍、再按收藏刷一遍，肉眼能看到跳动。
     */
    fun selectTypeBar(filter: ClipboardHistoryFilter, onlyStarred: Boolean) {
        val nextFilter = if (onlyStarred) ClipboardHistoryFilter.All else filter
        if (_historyFilter.value == nextFilter && _onlyStarred.value == onlyStarred) return
        _historyFilter.value = nextFilter
        _onlyStarred.value = onlyStarred
        if (_searchQuery.value.isBlank()) {
            reload()
        } else {
            runSearch(_searchQuery.value)
        }
    }

    fun setHistoryFilter(filter: ClipboardHistoryFilter) =
        selectTypeBar(filter, onlyStarred = false)

    fun setOnlyStarred(enabled: Boolean) =
        selectTypeBar(ClipboardHistoryFilter.All, onlyStarred = enabled)

    /** 清理面板的预览（五类条数 + 预计字节数）。 */
    suspend fun previewCleanup(
        categories: Collection<ClipboardCleanupCategory>,
        scope: ClipboardCleanupScope,
    ): ClipboardCleanupPreview =
        repository?.previewCleanup(categories, scope)
            ?: ClipboardCleanupPreview(emptyMap(), emptyMap())

    /** 按规则清理；返回实际删除条数与释放字节。 */
    suspend fun performCleanup(
        categories: Collection<ClipboardCleanupCategory>,
        scope: ClipboardCleanupScope,
    ): ClipboardCleanupResult =
        repository?.performCleanup(categories, scope) ?: ClipboardCleanupResult(0, 0L)

    val filteredEntries: StateFlow<List<ClipboardEntry>> = combine(
        _entries,
        _searchResults,
        _searchQuery,
        _sourceFilter,
    ) { entries, searched, query, sourceFilter ->
        val base = if (query.trim().isEmpty()) entries else searched
        if (sourceFilter == null) base else base.filter { it.sourcePackage == sourceFilter }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        scope.launch {
            _searchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
        scope.launch {
            repository?.revision?.collect {
                if (it == 0L) return@collect
                if (_searchQuery.value.isNotBlank()) {
                    runSearch(_searchQuery.value)
                } else {
                    reload()
                }
                refreshAvailableSources()
            }
        }
        reload()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSourceFilter(packageName: String?) {
        _sourceFilter.value = packageName
    }

    private suspend fun refreshAvailableSources() {
        val repo = repository ?: return
        _availableSources.value = runCatching { repo.distinctSourcePackages() }.getOrDefault(emptyList())
    }

    fun reload() {
        if (repository == null) return
        loadJob?.cancel()
        loadJob = scope.launch {
            val showLoading = _entries.value.isEmpty() && _searchQuery.value.isBlank()
            if (showLoading) _loading.value = true
            try {
                val page = withContext(Dispatchers.IO) {
                    repository.loadHistoryPage(
                        createdBeforeMs = null,
                        limit = ClipboardFloatWindowMetrics.PAGE_SIZE,
                        filter = _historyFilter.value,
                        onlyStarred = _onlyStarred.value,
                    )
                }
                _entries.value = page.entries
                refreshAvailableSources()
            } finally {
                if (showLoading) _loading.value = false
            }
        }
    }

    private fun runSearch(query: String) {
        val repo = repository ?: return
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            _loading.value = true
            try {
                _searchResults.value = withContext(Dispatchers.IO) {
                    // 搜索结果是一次性给回的内存列表，套不进分页 SQL，用同一套规则在内存里再筛一遍。
                    repo.searchHistory(trimmed).filter { it.matchesHistoryFilter(_historyFilter.value) }
                }
            } finally {
                _loading.value = false
            }
        }
    }
}
