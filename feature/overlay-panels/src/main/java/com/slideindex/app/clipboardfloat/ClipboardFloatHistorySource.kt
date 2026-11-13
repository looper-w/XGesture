package com.slideindex.app.clipboardfloat

import android.content.Context
import com.slideindex.app.clipboard.ClipboardCleanupCategory
import com.slideindex.app.clipboard.ClipboardCleanupPreview
import com.slideindex.app.clipboard.ClipboardCleanupResult
import com.slideindex.app.clipboard.ClipboardCleanupScope
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import kotlinx.coroutines.flow.Flow

/**
 * 剪贴板浮窗的数据源端口。
 *
 * 历史仓库（`ClipboardHistoryRepository`）是 `:app` 的 dagger 单例，模块侧只消费这 3 个入口。
 */
interface ClipboardFloatHistorySource {
    /** 历史变更版本号。 */
    val revision: Flow<Long>

    /**
     * 取最新一页（新的在前）。
     *
     * [filter] / [onlyStarred] 是浮窗类型栏的筛选条件：切换时**重新查库**而不是过滤已加载的几条，
     * 否则会出现"翻两页就空了"的错觉。
     */
    suspend fun loadPage(
        limit: Int,
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
        onlyStarred: Boolean = false,
    ): List<ClipboardEntry>

    /** 搜索历史。筛选与分页列表同一套 SQL 谓词（[filter] 为类型栏当前选中项）。 */
    suspend fun search(
        query: String,
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
    ): List<ClipboardEntry>

    /** 历史里出现过的来源包名（去重），供「按来源筛选」用。 */
    suspend fun distinctSourcePackages(): List<String>

    /** 「按规则清理」面板的预览：五类各自的条数与字节数。 */
    suspend fun previewCleanup(
        categories: Collection<ClipboardCleanupCategory>,
        scope: ClipboardCleanupScope,
    ): ClipboardCleanupPreview

    /** 按规则清理；返回实际删除条数与释放字节。 */
    suspend fun performCleanup(
        categories: Collection<ClipboardCleanupCategory>,
        scope: ClipboardCleanupScope,
    ): ClipboardCleanupResult
}

/** 模块内服务定位点：`:app` 启动时注入。 */
object ClipboardFloatHistoryAccess {

    @Volatile
    var source: ((Context) -> ClipboardFloatHistorySource?)? = null
}
