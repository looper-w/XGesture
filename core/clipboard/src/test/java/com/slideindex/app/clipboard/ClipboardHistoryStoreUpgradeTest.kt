package com.slideindex.app.clipboard

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 老库升级（v4 → 最新）时必须先清掉超大行，不能等第一次 `SELECT entry_json` 才炸；
 * 以及 v5（上游 1.66.0 的库，没有 `starred` 列）→ 最新时必须补上该列，否则 INSERT 直接抛异常。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ClipboardHistoryStoreUpgradeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun upgradeFromV4_purgesOversizedRowBeforeQuery() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE clipboard_entries (
                    id TEXT PRIMARY KEY NOT NULL,
                    created_at_ms INTEGER NOT NULL,
                    search_blob TEXT NOT NULL,
                    content_key TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    entry_type TEXT NOT NULL DEFAULT 'TEXT',
                    preview_text TEXT NOT NULL DEFAULT '',
                    has_image INTEGER NOT NULL DEFAULT 0,
                    entry_json TEXT NOT NULL
                )
                """.trimIndent()
            )
            val hugeJson = "{\"id\":\"bad\",\"type\":\"TEXT\",\"text\":\"" +
                "x".repeat(2_000_000) +
                "\",\"createdAtEpochMs\":1}"
            db.insert(
                "clipboard_entries",
                null,
                ContentValues().apply {
                    put("id", "bad")
                    put("created_at_ms", 1L)
                    put("search_blob", "bad")
                    put("content_key", "bad")
                    put("fingerprint", "bad")
                    put("entry_type", "TEXT")
                    put("preview_text", "bad")
                    put("has_image", 0)
                    put("entry_json", hugeJson)
                },
            )
            db.version = 4
        }

        val store = ClipboardHistoryStore(context, json)
        assertEquals(emptyList<ClipboardEntry>(), store.queryAll())
        assertEquals(0, store.count())
    }

    /**
     * 回归：升级自「上游 1.66.0 的库」。
     *
     * 那种库的 `user_version` 已经是 5，但表里**没有 `starred` 列**。收藏功能如果用版本号 5
     * 发布，`onUpgrade` 永远不触发，之后每次 INSERT 都会报
     * `table clipboard_entries has no column named starred` —— 表现出来就是启动即闪退。
     */
    @Test
    fun upgradeFromV5WithoutStarred_addsColumnAndAllowsInsert() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE clipboard_entries (
                    id TEXT PRIMARY KEY NOT NULL,
                    created_at_ms INTEGER NOT NULL,
                    search_blob TEXT NOT NULL,
                    content_key TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    entry_type TEXT NOT NULL DEFAULT 'TEXT',
                    preview_text TEXT NOT NULL DEFAULT '',
                    has_image INTEGER NOT NULL DEFAULT 0,
                    entry_json TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.version = 5
        }

        val store = ClipboardHistoryStore(context, json)
        // 建表时没有 starred，升级后必须补上；否则这个 insert 就会抛 SQLiteException。
        store.insert(
            ClipboardEntry(
                id = "after-upgrade",
                type = ClipboardEntryType.TEXT,
                text = "hello",
                createdAtEpochMs = 1L,
            )
        )

        assertEquals(1, store.count())
        store.setStarred("after-upgrade", true)
        assertEquals(true, store.queryById("after-upgrade")?.starred)
        assertEquals(1, store.count(ClipboardHistoryFilter.All, onlyStarred = true))
    }

    private companion object {
        const val DB_NAME = "clipboard_history.db"
    }
}
