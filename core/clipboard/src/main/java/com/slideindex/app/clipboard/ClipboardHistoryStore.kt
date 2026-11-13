package com.slideindex.app.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite-backed clipboard history: keyset pagination, optional FTS5 search, indexed dedup.
 */
class ClipboardHistoryStore(
    context: Context,
    private val json: Json
) {
    private val openHelper = StoreOpenHelper(context.applicationContext, json)

    private val ftsEnabled: Boolean
        get() = openHelper.ftsEnabled

    /**
     * 条数。传 [filter] 就是「该筛选下的**完整**条数」—— 面板头部的条数靠它，
     * 与「已经加载了几页」无关（§0.16.4 待办 1）。
     */
    fun count(
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
        onlyStarred: Boolean = false,
        /**
         * 分类口径：
         * - `false`（默认）= **原项目口径**（有图即图片）—— 历史 / 暂存面板用；
         * - `true` = **跟卡片左上角类型标签一致**（图文混排算富文本）—— 剪贴板浮窗用。
         */
        labelAligned: Boolean = false,
    ): Int {
        // 两边各自加了一个条件：上游的 JSON_SIZE_GUARD（超大 JSON 不参与计数）+ 本地的收藏筛选。
        // JSON_SIZE_GUARD 恒非空，所以 where 一定不为空（与上游去掉 takeIf 的理由一致）。
        val where = listOfNotNull(
            filter.whereFor(labelAligned),
            if (onlyStarred) "$COL_STARRED = 1" else null,
            JSON_SIZE_GUARD,
        ).joinToString(" AND ")
        val db = readableDatabase
        val sql = if (where.isEmpty()) {
            "SELECT COUNT(*) FROM $TABLE"
        } else {
            "SELECT COUNT(*) FROM $TABLE WHERE $where"
        }
        return db.rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) 0 else cursor.getInt(0)
        }
    }

    fun queryLatest(): ClipboardEntry? = queryPageBefore(createdBeforeMs = null, limit = 1).firstOrNull()

    fun queryPageBefore(
        createdBeforeMs: Long?,
        limit: Int,
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
        /** true = 只看已收藏（浮窗的「收藏」筛选）。 */
        onlyStarred: Boolean = false,
        /** 分类口径，语义见 [count] 的同名参数。 */
        labelAligned: Boolean = false,
    ): List<ClipboardEntry> {
        val pageSize = limit.coerceAtLeast(1)
        val cursorSelection = if (createdBeforeMs == null) null else "$COL_CREATED < ?"
        val filterSelection = filter.whereFor(labelAligned)
        val starredSelection = if (onlyStarred) "$COL_STARRED = 1" else null
        // 游标条件与筛选条件是 AND：两者都不带用户输入（筛选条件是常量字面量，没有占位符），
        // 所以拼字符串不会有注入面，参数表也只需管游标那一个。
        // 同样：游标 + 类型筛选 + 收藏筛选 + 上游的超大 JSON 保护，四个条件并存。
        val selection = listOfNotNull(cursorSelection, filterSelection, starredSelection, JSON_SIZE_GUARD)
            .joinToString(" AND ")
            .takeIf { it.isNotEmpty() }
        val selectionArgs = if (createdBeforeMs == null) null else arrayOf(createdBeforeMs.toString())
        val db = readableDatabase
        return db.query(
            TABLE,
            arrayOf(COL_JSON),
            selection,
            selectionArgs,
            null,
            null,
            "$COL_CREATED DESC",
            pageSize.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    decodeEntry(cursor.getString(0))?.let(::add)
                }
            }
        }
    }

    fun queryPage(offset: Int, limit: Int): List<ClipboardEntry> =
        queryPageBefore(createdBeforeMs = null, limit = limit + offset.coerceAtLeast(0))
            .drop(offset.coerceAtLeast(0))

    fun queryById(id: String): ClipboardEntry? {
        val db = readableDatabase
        return db.query(
            TABLE,
            arrayOf(COL_JSON),
            "$COL_ID = ? AND $JSON_SIZE_GUARD",
            arrayOf(id),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else decodeEntry(cursor.getString(0))
        }
    }

    fun queryByIds(ids: List<String>, extraWhere: String? = null): List<ClipboardEntry> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val db = readableDatabase
        // [extraWhere] 只用于搜索的类型筛选：常量字面量、无占位符，所以参数表仍然只有 id。
        val selection = "$COL_ID IN ($placeholders) AND $JSON_SIZE_GUARD" +
            (extraWhere?.let { " AND ($it)" } ?: "")
        val byId = db.query(
            TABLE,
            arrayOf(COL_ID, COL_JSON),
            selection,
            ids.toTypedArray(),
            null,
            null,
            null,
            null
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    decodeEntry(cursor.getString(1))?.let { put(id, it) }
                }
            }
        }
        return ids.mapNotNull { byId[it] }
    }

    fun findLatestByFingerprint(fingerprint: String): ClipboardEntry? {
        if (fingerprint.isEmpty()) return null
        return queryWhere("$COL_FINGERPRINT = ?", arrayOf(fingerprint), 1).firstOrNull()
    }

    fun findLatestByContentKey(contentKey: String): ClipboardEntry? {
        if (contentKey.isEmpty()) return null
        return queryWhere("$COL_CONTENT_KEY = ?", arrayOf(contentKey), 1).firstOrNull()
    }

    fun queryByFingerprint(fingerprint: String): List<ClipboardEntry> {
        if (fingerprint.isEmpty()) return emptyList()
        return queryWhere("$COL_FINGERPRINT = ?", arrayOf(fingerprint))
    }

    fun queryByContentKey(contentKey: String): List<ClipboardEntry> {
        if (contentKey.isEmpty()) return emptyList()
        return queryWhere("$COL_CONTENT_KEY = ?", arrayOf(contentKey))
    }

    /**
     * 全文搜索。
     *
     * [filter] / [labelAligned] 与分页列表用**同一套 SQL 谓词**：搜索结果不再回内存里逐条筛，
     * 所以"富文本怎么判"在整个模块里只有一份实现，不会出现搜索和列表归类不一致。
     */
    fun search(
        query: String,
        limit: Int = SEARCH_RESULT_LIMIT,
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
        /** 分类口径，语义见 [count]。 */
        labelAligned: Boolean = false,
    ): List<ClipboardEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val safeLimit = limit.coerceAtLeast(1)
        val filterWhere = filter.whereFor(labelAligned)
        if (ftsEnabled) {
            val ftsResults = runCatching { searchFts(trimmed, safeLimit, filterWhere) }
                .getOrDefault(emptyList())
            if (ftsResults.isNotEmpty()) return ftsResults
        }
        return searchLike(trimmed, safeLimit, filterWhere)
    }

    fun insert(entry: ClipboardEntry) {
        val safeEntry = ClipboardEntrySizeLimit.sanitize(entry, json) ?: return
        val db = writableDatabase
        db.transaction {
            insertWithOnConflict(TABLE, null, entryToValues(safeEntry), SQLiteDatabase.CONFLICT_REPLACE)
            if (ftsEnabled) syncFts(this, safeEntry)
        }
    }

    fun delete(id: String) {
        val db = writableDatabase
        db.transaction {
            if (ftsEnabled) deleteFtsByEntryId(this, id)
            delete(TABLE, "$COL_ID = ?", arrayOf(id))
        }
    }

    /** 批量删除：一条事务里清 FTS 与行（清理面板一次删几百条时不要逐条开事务）。 */
    fun deleteMany(ids: Collection<String>): Int {
        if (ids.isEmpty()) return 0
        val db = writableDatabase
        return db.transaction {
            ids.forEach { id -> if (ftsEnabled) deleteFtsByEntryId(this, id) }
            ids.chunked(400).sumOf { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                delete(TABLE, "$COL_ID IN ($placeholders)", chunk.toTypedArray())
            }
        }
    }

    /** 清理面板：按类型 + 范围计数（每种类型一条 `COUNT(*)`，五类互斥）。 */
    fun countByCleanup(
        categories: Collection<ClipboardCleanupCategory>,
        scope: ClipboardCleanupScope,
    ): Map<ClipboardCleanupCategory, Int> {
        if (categories.isEmpty()) return emptyMap()
        val db = readableDatabase
        return categories.associateWith { category ->
            val where = listOfNotNull(category.sqlWhere(), scope.sqlWhere())
                .joinToString(" AND ")
            db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE $where", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    /**
     * 清理面板：把命中的条目整条查出来。
     *
     * 用途有两个：① 估算体积（图片要读文件大小）；② 删除前拿到图片文件名（先删文件，避免孤儿）。
     */
    fun queryForCleanup(
        categories: Collection<ClipboardCleanupCategory>,
        scope: ClipboardCleanupScope,
    ): List<ClipboardEntry> {
        if (categories.isEmpty()) return emptyList()
        val where = listOfNotNull(categoriesSql(categories), scope.sqlWhere())
            .joinToString(" AND ")
        return readableDatabase.query(
            TABLE,
            arrayOf(COL_JSON),
            where,
            null,
            null,
            null,
            "$COL_CREATED DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    decodeEntry(cursor.getString(0))?.let(::add)
                }
            }
        }
    }

    private fun categoriesSql(categories: Collection<ClipboardCleanupCategory>): String =
        categories.joinToString(" OR ", prefix = "(", postfix = ")") { it.sqlWhere() }

    /**
     * 四类判定（互斥且完备），与浮窗口径（[labelAlignedSqlWhere]）同一套规则：
     * - **富文本** = 所有 HTML（含图文混排）；
     * - **图片记录** = 带图但**不是 HTML** 的（截图 / 相册图这类纯图片）。
     *
     * 纯文本是**兜底**：排除其余三类后剩下的（`TEXT` 非链接、`INTENT`、非图片的文件 URI）。
     * 没有「文件」类——本应用不把文件当独立类型展示，这类记录由文本兜底收走，不会漏。
     */
    private fun ClipboardCleanupCategory.sqlWhere(): String = when (this) {
        ClipboardCleanupCategory.IMAGE ->
            "$COL_HAS_IMAGE = 1 AND $COL_TYPE <> '${ClipboardEntryType.HTML.name}'"

        // 不看 has_image：带图但整条就是链接的 HTML 也要收进来，否则它四类都不落（不完备）。
        ClipboardCleanupCategory.LINK ->
            "$COL_TYPE IN ('${ClipboardEntryType.TEXT.name}', '${ClipboardEntryType.HTML.name}')" +
                " AND $LINK_PREDICATE"

        // HTML 全归富文本（不管有没有图）：这样"文字 + 图片"的混排条目不再被算成纯图片。
        ClipboardCleanupCategory.RICH_TEXT ->
            "$COL_TYPE = '${ClipboardEntryType.HTML.name}' AND NOT $LINK_PREDICATE"

        // 兜底：不是 HTML（HTML 已被链接/富文本分完）、不是图片、也不是整条链接。
        ClipboardCleanupCategory.TEXT ->
            "$COL_HAS_IMAGE = 0" +
                " AND $COL_TYPE <> '${ClipboardEntryType.HTML.name}'" +
                " AND NOT $LINK_PREDICATE"
    }

    private fun ClipboardCleanupScope.sqlWhere(): String? = when (this) {
        ClipboardCleanupScope.UNSTARRED -> "$COL_STARRED = 0"
        ClipboardCleanupScope.STARRED -> "$COL_STARRED = 1"
        ClipboardCleanupScope.ALL -> null
    }

    /**
     * 设置收藏状态。
     *
     * 列（SQL 计数/筛选用）与行内 JSON（UI 直接读）**必须同时更新**：只改列的话列表 decode 出来
     * 还是旧值，会出现"筛选里能看到、卡片上没星标"的错位。
     */
    fun setStarred(id: String, starred: Boolean): ClipboardEntry? {
        val entry = queryById(id) ?: return null
        val updated = entry.copy(starred = starred)
        val values = ContentValues().apply {
            put(COL_STARRED, if (starred) 1 else 0)
            put(COL_JSON, json.encodeToString(updated))
        }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
        return updated
    }

    fun deleteByFingerprint(fingerprint: String) {
        if (fingerprint.isEmpty()) return
        deleteWhere("$COL_FINGERPRINT = ?", arrayOf(fingerprint))
    }

    fun deleteByContentKey(contentKey: String) {
        if (contentKey.isEmpty()) return
        deleteWhere("$COL_CONTENT_KEY = ?", arrayOf(contentKey))
    }

    fun deleteAll() {
        val db = writableDatabase
        db.transaction {
            if (ftsEnabled) delete(FTS_TABLE, null, null)
            delete(TABLE, null, null)
        }
    }

    /** 备份用：按时间倒序取出全部条目。 */
    fun queryAll(): List<ClipboardEntry> {
        val db = readableDatabase
        return db.query(
            TABLE,
            arrayOf(COL_JSON),
            JSON_SIZE_GUARD,
            null,
            null,
            null,
            "$COL_CREATED DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    decodeEntry(cursor.getString(0))?.let(::add)
                }
            }
        }
    }

    /**
     * 恢复用：整表替换。
     *
     * 走数据库事务而不是覆盖 `.db` 文件——文件里还有 WAL/FTS 辅助表，直接拷文件在进程打开时
     * 会读到半截数据；这里按条目重建，FTS 也会一起同步。
     */
    fun replaceAll(entries: List<ClipboardEntry>) {
        val safeEntries = entries.mapNotNull { ClipboardEntrySizeLimit.sanitize(it, json) }
        val db = writableDatabase
        db.transaction {
            if (ftsEnabled) delete(FTS_TABLE, null, null)
            delete(TABLE, null, null)
            safeEntries.forEach { entry ->
                insertWithOnConflict(TABLE, null, entryToValues(entry), SQLiteDatabase.CONFLICT_REPLACE)
                if (ftsEnabled) syncFts(this, entry)
            }
        }
    }

    fun trimToMax(max: Int): List<ClipboardEntry> {
        if (max < 0) return emptyList()
        openHelper.purgeOversizedRows(writableDatabase)
        if (count() <= max) return emptyList()
        val db = readableDatabase
        val toRemove = db.rawQuery(
            """
                SELECT $COL_JSON FROM $TABLE
                WHERE $COL_ID IN (
                    SELECT $COL_ID FROM $TABLE
                    ORDER BY $COL_CREATED DESC
                    LIMIT -1 OFFSET ?
                )
                """.trimIndent(),
            arrayOf(max.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    decodeEntry(cursor.getString(0))?.let(::add)
                }
            }
        }
        if (toRemove.isEmpty()) return emptyList()
        val writeDb = writableDatabase
        writeDb.transaction {
            if (ftsEnabled) toRemove.forEach { deleteFtsByEntryId(this, it.id) }
            val ids = toRemove.map { it.id }.toTypedArray()
            val placeholders = ids.joinToString(",") { "?" }
            delete(TABLE, "$COL_ID IN ($placeholders)", ids)
        }
        return toRemove
    }

    fun migrateFromJsonIndex(jsonEntries: List<ClipboardEntry>) {
        if (jsonEntries.isEmpty()) return
        val safeEntries = jsonEntries.mapNotNull { ClipboardEntrySizeLimit.sanitize(it, json) }
        if (safeEntries.isEmpty()) return
        val db = writableDatabase
        db.transaction {
            safeEntries.forEach { entry ->
                insertWithOnConflict(TABLE, null, entryToValues(entry), SQLiteDatabase.CONFLICT_REPLACE)
                if (ftsEnabled) syncFts(this, entry)
            }
        }
    }

    private fun searchFts(query: String, limit: Int, filterWhere: String?): List<ClipboardEntry> {
        val match = buildFtsMatchQuery(query) ?: return emptyList()
        val db = readableDatabase
        // 带类型筛选时多取一些 id：FTS 先按相关度截断，过滤掉不匹配的之后还要够 limit 条。
        val idLimit = if (filterWhere == null) limit else limit * 4
        val ids = db.rawQuery(
            "SELECT $FTS_COL_ENTRY_ID FROM $FTS_TABLE WHERE $FTS_COL_SEARCH_TEXT MATCH ? LIMIT ?",
            arrayOf(match, idLimit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
        return queryByIds(ids, filterWhere).take(limit)
    }

    private fun searchLike(query: String, limit: Int, filterWhere: String?): List<ClipboardEntry> {
        val pattern = "%${query.lowercase()}%"
        val db = readableDatabase
        val selection = "$COL_SEARCH LIKE ? AND $JSON_SIZE_GUARD" +
            (filterWhere?.let { " AND ($it)" } ?: "")
        return db.query(
            TABLE,
            arrayOf(COL_JSON),
            selection,
            arrayOf(pattern),
            null,
            null,
            "$COL_CREATED DESC",
            limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    decodeEntry(cursor.getString(0))?.let(::add)
                }
            }
        }
    }

    private fun deleteWhere(where: String, args: Array<String>) {
        val db = writableDatabase
        db.transaction {
            if (ftsEnabled) deleteFtsForWhere(this, where, args)
            delete(TABLE, where, args)
        }
    }

    private fun deleteFtsForWhere(db: SQLiteDatabase, where: String, args: Array<String>) {
        db.query(TABLE, arrayOf(COL_ID), where, args, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                deleteFtsByEntryId(db, cursor.getString(0))
            }
        }
    }

    private fun deleteFtsByEntryId(db: SQLiteDatabase, entryId: String) {
        db.delete(FTS_TABLE, "$FTS_COL_ENTRY_ID = ?", arrayOf(entryId))
    }

    private fun syncFts(db: SQLiteDatabase, entry: ClipboardEntry) {
        deleteFtsByEntryId(db, entry.id)
        val values = ContentValues().apply {
            put(FTS_COL_ENTRY_ID, entry.id)
            put(FTS_COL_SEARCH_TEXT, searchBlob(entry))
        }
        db.insert(FTS_TABLE, null, values)
    }

    private fun buildFtsMatchQuery(raw: String): String? {
        val tokens = raw.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token ->
            val escaped = token.replace("\"", "\"\"")
            "\"$escaped\"*"
        }
    }

    /**
     * 筛选 → SQL 谓词。**这是全部筛选语义的唯一实现**：面板头部的条数（[count]）与列表分页
     * （[queryPageBefore]）共用它，不会出现"数字和列表对不上"。
     *
     * 五类**互斥且完备**地落在 `has_image` / `entry_type` 两个列上（口语化说明见
     * `ClipboardHistoryFilter` 的文档表）：[ClipboardHistoryFilter.RichText] 特意排除了
     * "整条就是链接"的 HTML（浏览器复制链接就是 HTML + 文本），否则同一条会同时出现在
     * 「链接」和「富文本」两处；[ClipboardHistoryFilter.Text] 是兜底，把剩下的都收进来。
     *
     * ⚠️ 这是**原项目口径**（「有图即图片」），原项目的历史 / 暂存面板在用，**不要改**。
     * 剪贴板浮窗的类型栏要用「跟卡片左上角标签一致」的口径，见 [labelAlignedSqlWhere]
     * —— 两者只差「图文混排」这一条：这里算图片，那边算富文本。
     */
    private fun ClipboardHistoryFilter.sqlWhere(): String? = when (this) {
        ClipboardHistoryFilter.All -> null
        // 文本兜底：排除文件 URI / HTML，以及「整条就是一条链接」的文本（那些归 Link）。
        ClipboardHistoryFilter.Text ->
            "$COL_HAS_IMAGE = 0" +
                " AND $COL_TYPE NOT IN ('${ClipboardEntryType.URI.name}', '${ClipboardEntryType.HTML.name}')" +
                " AND NOT $LINK_PREDICATE"
        ClipboardHistoryFilter.Image -> "$COL_HAS_IMAGE = 1"
        ClipboardHistoryFilter.File ->
            "$COL_HAS_IMAGE = 0 AND $COL_TYPE = '${ClipboardEntryType.URI.name}'"
        ClipboardHistoryFilter.RichText ->
            "$COL_HAS_IMAGE = 0 AND $COL_TYPE = '${ClipboardEntryType.HTML.name}'" +
                " AND NOT $LINK_PREDICATE"
        ClipboardHistoryFilter.Link ->
            "$COL_HAS_IMAGE = 0" +
                " AND $COL_TYPE IN ('${ClipboardEntryType.TEXT.name}', '${ClipboardEntryType.HTML.name}')" +
                " AND $LINK_PREDICATE"
    }

    /**
     * 按口径选谓词：[labelAligned] = false 走原项目口径（历史 / 暂存面板），
     * true 走「跟卡片左上角类型标签一致」的口径（剪贴板浮窗）。
     */
    private fun ClipboardHistoryFilter.whereFor(labelAligned: Boolean): String? =
        if (labelAligned) labelAlignedSqlWhere() else sqlWhere()

    private fun queryWhere(where: String, args: Array<String>, limit: Int? = null): List<ClipboardEntry> {
        val db = readableDatabase
        return db.query(
            TABLE,
            arrayOf(COL_JSON),
            "($where) AND $JSON_SIZE_GUARD",
            args,
            null,
            null,
            "$COL_CREATED DESC",
            limit?.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    decodeEntry(cursor.getString(0))?.let(::add)
                }
            }
        }
    }

    private fun entryToValues(entry: ClipboardEntry): ContentValues = ContentValues().apply {
        put(COL_ID, entry.id)
        put(COL_CREATED, entry.createdAtEpochMs)
        put(COL_SEARCH, searchBlob(entry))
        put(COL_CONTENT_KEY, ClipboardContentKey.forEntry(entry))
        put(COL_FINGERPRINT, ClipboardContentEquivalence.fingerprint(entry))
        put(COL_TYPE, entry.type.name)
        put(COL_PREVIEW, previewText(entry))
        put(COL_HAS_IMAGE, if (entry.hasImageContent()) 1 else 0)
        put(COL_STARRED, if (entry.starred) 1 else 0)
        put(COL_JSON, json.encodeToString(entry))
    }

    private fun previewText(entry: ClipboardEntry): String {
        val text = entry.text.trim().ifBlank { entry.uri ?: entry.intentUri.orEmpty() }
        return text.take(PREVIEW_MAX_LEN)
    }

    private fun searchBlob(entry: ClipboardEntry): String = buildString {
        append(entry.text)
        entry.uri?.let { append('\n').append(it) }
        entry.intentUri?.let { append('\n').append(it) }
    }.lowercase()

    private fun decodeEntry(raw: String): ClipboardEntry? = runCatching {
        json.decodeFromString<ClipboardEntry>(raw)
    }.getOrNull()

    private val readableDatabase: SQLiteDatabase
        get() = openHelper.readableDatabase

    private val writableDatabase: SQLiteDatabase
        get() = openHelper.writableDatabase

    private class StoreOpenHelper(
        context: Context,
        private val json: Json
    ) : SQLiteOpenHelper(
        context,
        DB_NAME,
        null,
        DB_VERSION
    ) {
        var ftsEnabled: Boolean = false
            private set

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
            db.rawQuery("PRAGMA journal_mode=WAL", null).close()
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            ftsEnabled = readMetaFtsEnabled(db)
            // 兜底：旧版本可能已经写入过超大行，读表之前先把它们清掉。
            runCatching { purgeOversizedRows(db) }
        }

        override fun onCreate(db: SQLiteDatabase) {
            createTable(db)
            createIndexes(db)
            createMetaTable(db)
            val enabled = tryEnableFtsAndRebuild(db)
            writeMetaFtsEnabled(db, enabled)
            ftsEnabled = enabled
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // 先清坏行，再做任何会读 entry_json 的补列/建 FTS 迁移；
            // 否则老库里一条超大行会让升级本身也崩在 moveToNext()。
            runCatching { purgeOversizedRows(db) }
            if (oldVersion < 2) {
                migrateToV2(db)
            }
            if (oldVersion < 3) {
                migrateToV3(db)
            }
            if (oldVersion < 4) {
                migrateToV4(db)
            }
            if (oldVersion < 6) {
                migrateToV6(db)
            }
        }

        private fun createTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_CREATED INTEGER NOT NULL,
                    $COL_SEARCH TEXT NOT NULL,
                    $COL_CONTENT_KEY TEXT NOT NULL,
                    $COL_FINGERPRINT TEXT NOT NULL,
                    $COL_TYPE TEXT NOT NULL DEFAULT 'TEXT',
                    $COL_PREVIEW TEXT NOT NULL DEFAULT '',
                    $COL_HAS_IMAGE INTEGER NOT NULL DEFAULT 0,
                    $COL_STARRED INTEGER NOT NULL DEFAULT 0,
                    $COL_JSON TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun createMetaTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $META_TABLE (
                    $META_COL_KEY TEXT PRIMARY KEY NOT NULL,
                    $META_COL_VALUE TEXT NOT NULL
                )
                """.trimIndent()
            )
        }

        private fun createFtsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(
                    $FTS_COL_ENTRY_ID UNINDEXED,
                    $FTS_COL_SEARCH_TEXT,
                    tokenize='unicode61'
                )
                """.trimIndent()
            )
        }

        private fun createIndexes(db: SQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${TABLE}_created ON $TABLE($COL_CREATED DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_${TABLE}_search ON $TABLE($COL_SEARCH)")
            createDedupIndexes(db)
        }

        private fun createDedupIndexes(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${TABLE}_fingerprint_created " +
                    "ON $TABLE($COL_FINGERPRINT, $COL_CREATED DESC)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${TABLE}_content_key_created " +
                    "ON $TABLE($COL_CONTENT_KEY, $COL_CREATED DESC)"
            )
        }

        private fun migrateToV2(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_CONTENT_KEY TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_FINGERPRINT TEXT NOT NULL DEFAULT ''")
            createIndexes(db)
            backfillDedupColumns(db)
        }

        private fun migrateToV3(db: SQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS idx_${TABLE}_fingerprint")
            db.execSQL("DROP INDEX IF EXISTS idx_${TABLE}_content_key")
            createDedupIndexes(db)
        }

        private fun migrateToV4(db: SQLiteDatabase) {
            createMetaTable(db)
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_TYPE TEXT NOT NULL DEFAULT 'TEXT'")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_PREVIEW TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_HAS_IMAGE INTEGER NOT NULL DEFAULT 0")
            backfillLightweightColumns(db)
            val enabled = tryEnableFtsAndRebuild(db)
            writeMetaFtsEnabled(db, enabled)
            ftsEnabled = enabled
        }

        /**
         * v6：收藏（星标）。
         *
         * 只加一列，老数据默认 `starred = 0`；行内 JSON 不需要回填（缺字段时反序列化成 false）。
         * 加列是幂等的：SQLite 没有 `ADD COLUMN IF NOT EXISTS`，所以先查一次列是否存在，
         * 避免异常升级路径重复执行时把库搞坏。
         *
         * ⚠️ 必须是**新版本号**：上游早于本改动发布的库 `user_version` 已经是 5，
         * 而那一版的表里没有 `starred`。如果沿用 5，`onUpgrade` 不会触发，
         * 之后每次 INSERT 都会因为缺列报 `SQLiteException`（启动即崩）。
         */
        private fun migrateToV6(db: SQLiteDatabase) {
            if (columnExists(db, TABLE, COL_STARRED)) return
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_STARRED INTEGER NOT NULL DEFAULT 0")
        }

        private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean =
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return true
                }
                false
            }

        private fun tryEnableFtsAndRebuild(db: SQLiteDatabase): Boolean {
            if (!probeFts5(db)) return false
            return runCatching {
                createFtsTable(db)
                rebuildFts(db)
                true
            }.getOrDefault(false)
        }

        /**
         * 删除 `entry_json` 超过上限的行。只查 id + length，不读 JSON 本身。
         *
         * 公开给外层 [ClipboardHistoryStore.trimToMax] 复用：删旧条目前先把坏行清掉，
         * 后面的 `SELECT entry_json` 才不会踩到窗口上限。
         */
        fun purgeOversizedRows(db: SQLiteDatabase) {
            val ids = db.rawQuery(
                "SELECT $COL_ID FROM $TABLE WHERE length(CAST($COL_JSON AS BLOB)) > ?",
                arrayOf(ClipboardEntrySizeLimit.MAX_ENTRY_JSON_BYTES.toString())
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            if (ids.isEmpty()) return
            val hasFts = tableExists(db, FTS_TABLE)
            // onUpgrade 本身已经在 SQLiteOpenHelper 的事务里，不能再嵌套 beginTransaction。
            val ownsTransaction = !db.inTransaction()
            if (ownsTransaction) db.beginTransaction()
            try {
                if (hasFts) {
                    ids.forEach { entryId ->
                        db.delete(FTS_TABLE, "$FTS_COL_ENTRY_ID = ?", arrayOf(entryId))
                    }
                }
                ids.chunked(500).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    db.delete(TABLE, "$COL_ID IN ($placeholders)", chunk.toTypedArray())
                }
                if (ownsTransaction) db.setTransactionSuccessful()
            } finally {
                if (ownsTransaction) db.endTransaction()
            }
        }

        private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table)
            ).use { it.moveToFirst() }

        private fun probeFts5(db: SQLiteDatabase): Boolean = runCatching {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_PROBE_TABLE USING fts5(x)")
            db.execSQL("DROP TABLE IF EXISTS $FTS_PROBE_TABLE")
            true
        }.getOrDefault(false)

        private fun writeMetaFtsEnabled(db: SQLiteDatabase, enabled: Boolean) {
            val values = ContentValues().apply {
                put(META_COL_KEY, META_KEY_FTS_ENABLED)
                put(META_COL_VALUE, if (enabled) META_VALUE_TRUE else META_VALUE_FALSE)
            }
            db.insertWithOnConflict(META_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }

        private fun readMetaFtsEnabled(db: SQLiteDatabase): Boolean {
            if (!metaTableExists(db)) return false
            db.query(
                META_TABLE,
                arrayOf(META_COL_VALUE),
                "$META_COL_KEY = ?",
                arrayOf(META_KEY_FTS_ENABLED),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) return false
                return cursor.getString(0) == META_VALUE_TRUE
            }
        }

        private fun metaTableExists(db: SQLiteDatabase): Boolean =
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(META_TABLE)
            ).use { it.moveToFirst() }

        private fun backfillDedupColumns(db: SQLiteDatabase) {
            db.query(
                TABLE,
                arrayOf(COL_ID, COL_JSON),
                JSON_SIZE_GUARD,
                null,
                null,
                null,
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val entry = decodeEntryStatic(json, cursor.getString(1)) ?: continue
                    val values = ContentValues().apply {
                        put(COL_CONTENT_KEY, ClipboardContentKey.forEntry(entry))
                        put(COL_FINGERPRINT, ClipboardContentEquivalence.fingerprint(entry))
                    }
                    db.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
                }
            }
        }

        private fun backfillLightweightColumns(db: SQLiteDatabase) {
            db.query(
                TABLE,
                arrayOf(COL_ID, COL_JSON),
                JSON_SIZE_GUARD,
                null,
                null,
                null,
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val entry = decodeEntryStatic(json, cursor.getString(1)) ?: continue
                    val values = ContentValues().apply {
                        put(COL_TYPE, entry.type.name)
                        put(COL_PREVIEW, previewTextStatic(entry))
                        put(COL_HAS_IMAGE, if (entry.hasImageContent()) 1 else 0)
                    }
                    db.update(TABLE, values, "$COL_ID = ?", arrayOf(id))
                }
            }
        }

        private fun rebuildFts(db: SQLiteDatabase) {
            db.delete(FTS_TABLE, null, null)
            db.query(
                TABLE,
                arrayOf(COL_ID, COL_JSON),
                JSON_SIZE_GUARD,
                null,
                null,
                null,
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val entry = decodeEntryStatic(json, cursor.getString(1)) ?: continue
                    val values = ContentValues().apply {
                        put(FTS_COL_ENTRY_ID, entry.id)
                        put(FTS_COL_SEARCH_TEXT, searchBlobStatic(entry))
                    }
                    db.insert(FTS_TABLE, null, values)
                }
            }
        }
    }

    companion object {
        private const val DB_NAME = "clipboard_history.db"
        private const val DB_VERSION = 6
        private const val TABLE = "clipboard_entries"
        private const val META_TABLE = "clipboard_meta"
        private const val FTS_TABLE = "clipboard_fts"
        private const val FTS_PROBE_TABLE = "_clipboard_fts5_probe"
        private const val COL_ID = "id"
        private const val COL_CREATED = "created_at_ms"
        private const val COL_SEARCH = "search_blob"
        private const val COL_CONTENT_KEY = "content_key"
        private const val COL_FINGERPRINT = "fingerprint"
        private const val COL_TYPE = "entry_type"
        private const val COL_PREVIEW = "preview_text"
        private const val COL_HAS_IMAGE = "has_image"
        private const val COL_STARRED = "starred"
        private const val COL_JSON = "entry_json"
        private const val JSON_SIZE_GUARD =
            "length(CAST($COL_JSON AS BLOB)) <= ${ClipboardEntrySizeLimit.MAX_ENTRY_JSON_BYTES}"
        private const val META_COL_KEY = "meta_key"
        private const val META_COL_VALUE = "meta_value"
        private const val META_KEY_FTS_ENABLED = "fts_enabled"
        private const val META_VALUE_TRUE = "1"
        private const val META_VALUE_FALSE = "0"
        private const val FTS_COL_ENTRY_ID = "entry_id"
        private const val FTS_COL_SEARCH_TEXT = "search_text"
        private const val PREVIEW_MAX_LEN = 500
        const val SEARCH_RESULT_LIMIT = 500

        /**
         * "整条内容就是一条链接"。
         *
         * `search_blob` = 正文 + uri + intentUri（入库时拼好并 lower 过），而 TEXT / HTML 两类条目的
         * 正文**必然非空**（`ClipboardReader` 只在有文本时才产出这两类），所以 `LIKE 'http://%'`
         * 等价于"正文以它开头"。夹在长文中间的链接**不算**（理由见 `ClipboardHistoryFilter`）。
         */
        private const val LINK_PREDICATE =
            "($COL_SEARCH LIKE 'http://%' OR $COL_SEARCH LIKE 'https://%'" +
                " OR $COL_SEARCH LIKE 'www.%')"

        private fun decodeEntryStatic(json: Json, raw: String): ClipboardEntry? = runCatching {
            json.decodeFromString<ClipboardEntry>(raw)
        }.getOrNull()

        private fun previewTextStatic(entry: ClipboardEntry): String {
            val text = entry.text.trim().ifBlank { entry.uri ?: entry.intentUri.orEmpty() }
            return text.take(PREVIEW_MAX_LEN)
        }

        private fun searchBlobStatic(entry: ClipboardEntry): String = buildString {
            append(entry.text)
            entry.uri?.let { append('\n').append(it) }
            entry.intentUri?.let { append('\n').append(it) }
        }.lowercase()
    }
}
