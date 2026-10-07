package com.slideindex.app.stash

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一枚标签的定义。 */
@Serializable
data class StashTag(
    val name: String,
    /** 颜色，0xAARRGGBB 的十进制值。用 Long 存，避免 JSON 里出现负数歧义的 Int。 */
    val colorArgb: Long,
    /** 排序权重，小的在前。 */
    val order: Int = 0,
)

/** 一条「追加」—— 在原记录上后来续写的一段。 */
@Serializable
data class StashAppend(
    val text: String,
    val atEpochMs: Long,
)

/**
 * 元数据文件的编解码器（**单例，放在文件顶层**）。
 *
 * - `ignoreUnknownKeys`：老版本读到新字段不能炸。
 * - `encodeDefaults = true`：与 `index.json` 相反 —— 这个文件由我们独占，字段完整落盘才好排查。
 *
 * 放在顶层还有一个好处：单测能直接引用它，测的就是线上这份配置，不会和实现跑偏。
 */
internal val StashMetaJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 所有**新**的条目元数据都放这里，**独立文件存放，绝不写进 `index.json`**。
 *
 * 为什么必须独立（`docs/capsule-refactor-p0-findings.md` §4.1）：
 * `index.json` 是 `List<StashEntry>` 的序列化结果，老版本 App 用
 * `Json { ignoreUnknownKeys = true }` 读、再**整体重写**整张表 —— 新字段会被忽略并抹掉；
 * 而且它的配置是 `encodeDefaults = false`，等于默认值的字段根本不落盘。
 *
 * 为什么合成**一个**文件而不是三个：三者都是「按 entryId 索引的条目元数据」，
 * 同生命周期、同清理时机。合起来只有一把锁、一个监听、一次重载，
 * 也不会出现「标签删了但完成态还留着孤儿」这类跨文件不一致。
 */
@Serializable
data class StashMetaStore(
    val version: Int = 1,
    val tags: List<StashTag> = emptyList(),
    /** entryId -> 该条的标签名列表。 */
    val assignments: Map<String, List<String>> = emptyMap(),
    /** entryId -> 完成时间戳 ms。**存在即已完成**，不存 false。 */
    val doneAt: Map<String, Long> = emptyMap(),
    /** entryId -> 追加内容（按时间正序）。 */
    val appends: Map<String, List<StashAppend>> = emptyMap(),
    /**
     * entryId -> 来源键（[StashMetaRepository.SOURCE_CLIPBOARD] 等）。
     *
     * 存**字符串键而不是 enum**：老版本遇到不认识的 enum 值会在解码**整个文件**时抛异常，
     * 而这个文件的读失败会连带禁用所有写入（见 `writeToDisk` 的保护）—— 于是一个新来源键
     * 就能让旧版本的标签/完成态全部写不进去。字符串键读不懂就当"没有来源"，最多少一枚 chip。
     */
    val sources: Map<String, String> = emptyMap(),
    /**
     * entryId -> 提醒触发时间戳（ms）。**存在即已设提醒**（与 [doneAt] 同款约定）。
     *
     * 同样存进这个独立文件：`index.json` 会被老版本整体重写，提醒会丢。
     */
    val reminders: Map<String, Long> = emptyMap(),
) {
    fun isDone(entryId: String): Boolean = doneAt.containsKey(entryId)

    fun tagsOf(entryId: String): List<String> = assignments[entryId].orEmpty()

    fun appendsOf(entryId: String): List<StashAppend> = appends[entryId].orEmpty()

    fun sourceOf(entryId: String): String? = sources[entryId]

    fun reminderOf(entryId: String): Long? = reminders[entryId]
}

@Singleton
class StashMetaRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, META_FILE_NAME)
    // 跨进程安全：进程内互斥 + 跨进程文件锁 + 写完广播。
    // per-file 锁，**不要**和 index.json 共用。
    private val mutex =
        com.slideindex.app.util.CrossProcessStore.CrossProcessMutex(appContext, storeFile)
    // 编解码器见文件顶部 StashMetaJson（单测直接用它，保证测的就是线上配置）。
    private val json = StashMetaJson

    private val _store = MutableStateFlow(StashMetaStore())
    val store: StateFlow<StashMetaStore> = _store.asStateFlow()

    init {
        com.slideindex.app.util.CrossProcessStore.registerListener(appContext, storeFile) {
            _store.value = readFromDiskSync()
        }
        val loaded = readFromDiskSync()
        if (loaded.tags.isEmpty() && !storeFile.exists()) {
            // 仅首次运行灌默认标签；之后以文件为准，用户删掉的标签不会被回灌。
            val seeded = loaded.copy(tags = DEFAULT_TAGS)
            runCatching { writeToDiskSync(seeded) }
                .onFailure { Log.w(TAG, "seed stash meta failed", it) }
            _store.value = if (storeFile.exists()) readFromDiskSync() else seeded
        } else {
            _store.value = loaded
        }
        StashAccess.metaRepository = this
    }

    /* ---------------- 标签 ---------------- */

    fun tags(): List<StashTag> = _store.value.tags.sortedBy { it.order }

    fun tagsOf(entryId: String): List<String> = _store.value.tagsOf(entryId)

    fun isTagged(entryId: String, name: String): Boolean = name in tagsOf(entryId)

    suspend fun setTags(entryId: String, names: List<String>) = mutate { current ->
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val next = current.assignments.toMutableMap()
        if (cleaned.isEmpty()) next.remove(entryId) else next[entryId] = cleaned
        current.copy(assignments = next)
    }

    suspend fun toggleTag(entryId: String, name: String) = mutate { current ->
        val now = current.assignments[entryId].orEmpty()
        val updated = if (name in now) now - name else now + name
        val next = current.assignments.toMutableMap()
        if (updated.isEmpty()) next.remove(entryId) else next[entryId] = updated
        current.copy(assignments = next)
    }

    /**
     * 新增一枚标签定义。@return 是否真的加上了（重名 / 空名 → false，UI 据此提示"已有同名标签"）。
     */
    suspend fun addTag(name: String, colorArgb: Long): Boolean =
        mutateIfChanged { StashTagEdits.add(it, name, colorArgb) }

    /** 删除一枚标签定义，同时清掉所有条目对它的绑定。「待办」拒绝删除。 */
    suspend fun removeTag(name: String): Boolean =
        mutateIfChanged { StashTagEdits.remove(it, name) }

    /**
     * 改名，**连带改写所有条目的绑定**（否则旧名会变成孤儿）；目标名已存在时按合并处理。
     *
     * 具体规则与"为什么"都在 [StashTagEdits.rename]（纯函数，有单测）。
     *
     * @return 是否真的改了。
     */
    suspend fun renameTag(oldName: String, newName: String): Boolean =
        mutateIfChanged { StashTagEdits.rename(it, oldName, newName) }

    /** 改色（「待办」也允许：颜色不参与关键字判定）。 */
    suspend fun setTagColor(name: String, colorArgb: Long): Boolean =
        mutateIfChanged { StashTagEdits.setColor(it, name, colorArgb) }

    /* ---------------- 完成态 ---------------- */

    fun isDone(entryId: String): Boolean = _store.value.isDone(entryId)

    suspend fun setDone(entryId: String, done: Boolean) = mutate { current ->
        val next = current.doneAt.toMutableMap()
        if (done) next[entryId] = System.currentTimeMillis() else next.remove(entryId)
        current.copy(doneAt = next)
    }

    /** 带「待办」标签**且未完成**的条数 —— 把手据此变色。 */
    fun pendingTodoCount(): Int = _store.value.run {
        assignments.count { (entryId, names) ->
            TODO_TAG_NAME in names && !isDone(entryId)
        }
    }

    /* ---------------- 追加 ---------------- */

    fun appendsOf(entryId: String): List<StashAppend> = _store.value.appendsOf(entryId)

    suspend fun appendText(entryId: String, text: String) = mutate { current ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            current
        } else {
            val next = current.appends.toMutableMap()
            val list = next[entryId].orEmpty() + StashAppend(trimmed, System.currentTimeMillis())
            next[entryId] = list
            current.copy(appends = next)
        }
    }

    suspend fun removeAppends(entryId: String) = mutate { current ->
        if (entryId !in current.appends) current
        else current.copy(appends = current.appends - entryId)
    }

    /** 去掉最后一段追加（"追加"的撤销）。 */
    suspend fun removeLastAppend(entryId: String) = mutate { current ->
        val list = current.appends[entryId].orEmpty()
        if (list.isEmpty()) {
            current
        } else {
            val next = current.appends.toMutableMap()
            val remaining = list.dropLast(1)
            if (remaining.isEmpty()) next.remove(entryId) else next[entryId] = remaining
            current.copy(appends = next)
        }
    }

    /* ---------------- 来源 ---------------- */

    fun sourceOf(entryId: String): String? = _store.value.sourceOf(entryId)

    /**
     * 记下条目是从哪来的（剪贴板 / 取词 / 图片）。传 null 或空串等于清掉。
     *
     * 只有**非「纯闪念」**的来源才需要记：用户自己记下来的条目没有来源 chip，
     * 这正是设计稿里 `src === 'stash'` 才显示 chip 的语义。
     */
    suspend fun setSource(entryId: String, source: String?) = mutate { current ->
        val next = current.sources.toMutableMap()
        if (source.isNullOrBlank()) next.remove(entryId) else next[entryId] = source
        if (next == current.sources) current else current.copy(sources = next)
    }

    /* ---------------- 提醒 ---------------- */

    fun reminderOf(entryId: String): Long? = _store.value.reminderOf(entryId)

    /** 当前所有待触发提醒（面板打开时用它把闹钟补排一次，见 `StashReminderScheduler`）。 */
    fun pendingReminders(): Map<String, Long> = _store.value.reminders

    /** 设/清提醒。传 null 就是取消。 */
    suspend fun setReminder(entryId: String, atEpochMs: Long?) = mutate { current ->
        val next = current.reminders.toMutableMap()
        if (atEpochMs == null) next.remove(entryId) else next[entryId] = atEpochMs
        if (next == current.reminders) current else current.copy(reminders = next)
    }

    /* ---------------- 清理 ---------------- */

    /** 条目被删除时调用：把它的标签绑定 / 完成态 / 追加内容 / 来源 / 提醒一并清掉。 */
    suspend fun forget(entryId: String) = mutate { current ->
        if (entryId !in current.assignments &&
            entryId !in current.doneAt &&
            entryId !in current.appends &&
            entryId !in current.sources &&
            entryId !in current.reminders
        ) {
            current
        } else {
            current.copy(
                assignments = current.assignments - entryId,
                doneAt = current.doneAt - entryId,
                appends = current.appends - entryId,
                sources = current.sources - entryId,
                reminders = current.reminders - entryId,
            )
        }
    }

    /**
     * 清掉所有指向已不存在条目的孤儿元数据。
     *
     * 需要用这个的原因：条目会因为 `MAX_ENTRIES = 200` 被裁掉（`StashRepository.trimToMax`），
     * 而且删除路径不止一处 —— 与其在每个删除点都记得调 `forget`，不如定期按有效 id 收敛。
     */
    suspend fun pruneOrphans(validEntryIds: Collection<String>) = mutate { current ->
        val keep = validEntryIds.toSet()
        val assignments = current.assignments.filterKeys { it in keep }
        val doneAt = current.doneAt.filterKeys { it in keep }
        val appends = current.appends.filterKeys { it in keep }
        val sources = current.sources.filterKeys { it in keep }
        val reminders = current.reminders.filterKeys { it in keep }
        if (assignments.size == current.assignments.size &&
            doneAt.size == current.doneAt.size &&
            appends.size == current.appends.size &&
            sources.size == current.sources.size &&
            reminders.size == current.reminders.size
        ) {
            current
        } else {
            current.copy(
                assignments = assignments,
                doneAt = doneAt,
                appends = appends,
                sources = sources,
                reminders = reminders,
            )
        }
    }

    /* ---------------- 底层 ---------------- */

    private suspend fun mutate(transform: (StashMetaStore) -> StashMetaStore) {
        mutex.withLock {
            val next = transform(readFromDiskSync())
            writeToDisk(next)
            _store.value = next
        }
    }

    /**
     * 与 [mutate] 同一条写路径，但允许 [transform] 用 `null` 表示"没变化"：
     * 这时**不写盘、不广播**，只回一个 false 给 UI。
     */
    private suspend fun mutateIfChanged(transform: (StashMetaStore) -> StashMetaStore?): Boolean =
        mutex.withLock {
            val current = readFromDiskSync()
            val next = transform(current) ?: return@withLock false
            writeToDisk(next)
            _store.value = next
            true
        }

    private fun readFromDiskSync(): StashMetaStore {
        if (!storeFile.exists()) return StashMetaStore()
        return runCatching { json.decodeFromString<StashMetaStore>(storeFile.readText()) }
            .getOrElse { cause ->
                Log.w(TAG, "stash meta unreadable; refusing to overwrite it", cause)
                readFailed = true
                StashMetaStore()
            }
    }

    /**
     * 读不出来时**不要**写。
     *
     * 所有写路径都是「读全表 → 改 → 整表写回」，读失败返回空表会让下一次写入
     * 把用户的全部元数据覆盖成空。这里直接跳过写入；只要之后有一次成功读取，
     * 标志位就会被清掉，写入自动恢复。
     */
    private suspend fun writeToDisk(store: StashMetaStore) {
        if (readFailed) {
            Log.w(TAG, "skip meta write: last read failed")
            return
        }
        withContext(Dispatchers.IO) { writeToDiskSync(store) }
    }

    private fun writeToDiskSync(store: StashMetaStore) {
        storeFile.writeText(json.encodeToString(store))
        readFailed = false
    }

    @Volatile
    private var readFailed = false

    companion object {
        private const val TAG = "StashMetaRepository"
        private const val META_FILE_NAME = "stash_meta.json"

        /** 与设计稿一致的五枚默认标签。 */
        val DEFAULT_TAGS: List<StashTag> = listOf(
            StashTag("工作", 0xFF5B8DF6, 0),
            StashTag("想法", 0xFFF0A93B, 1),
            StashTag("待办", 0xFF57B87A, 2),
            StashTag("灵感", 0xFFC77DF0, 3),
            StashTag("设计", 0xFFE86E8C, 4),
        )

        /** 「完成」动作只出现在带这枚标签的条目上。 */
        const val TODO_TAG_NAME = "待办"

        /** 来源：从剪贴板「暂存到闪念」。 */
        const val SOURCE_CLIPBOARD = "clipboard"

        /** 来源：取词面板里暂存。 */
        const val SOURCE_PICK = "pick"

        /** 来源：图片（编辑器 / 截图）存进闪念。 */
        const val SOURCE_IMAGE = "image"
    }
}
