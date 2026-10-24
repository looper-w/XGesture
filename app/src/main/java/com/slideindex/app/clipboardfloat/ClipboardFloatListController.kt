package com.slideindex.app.clipboardfloat

import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryRepository
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
                        limit = ClipboardFloatWindowMetrics.PAGE_SIZE
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
                    repo.searchHistory(trimmed)
                }
            } finally {
                _loading.value = false
            }
        }
    }
}
