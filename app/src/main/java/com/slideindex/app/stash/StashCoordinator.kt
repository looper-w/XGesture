package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.history.HistorySaveSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object StashCoordinator {
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * 记下条目来源（卡片上那枚小 chip）。
     *
     * 来源是**可选**的展示信息，所以失败只吞掉 —— 不能让"存进闪念"这个主流程跟着失败，
     * 也不能因为元数据写不进去就让用户以为没存上。传 null 的调用点 = 纯闪念，不显示 chip。
     */
    private suspend fun rememberSource(entryId: String?, source: String?) {
        if (entryId == null || source == null) return
        runCatching { StashAccess.metaRepository?.setSource(entryId, source) }
    }

    /**
     * 通知把手侧「刚存下一条」：把手脉冲（设计稿 `.pip.pulse`），之后 peek 预览也用它。
     *
     * 把手窗与面板窗是两个 window，只能靠同进程静态量传事件（见 `HistorySaveSignal`）。
     */
    private fun notifySaved(text: String) {
        HistorySaveSignal.notifySaved(text)
    }

    fun addText(
        text: String,
        source: String? = null,
        onSaved: (String) -> Unit = {},
        onDone: (Boolean) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            val entry = repo.addText(text)
            rememberSource(entry?.id, source)
            if (entry != null) {
                notifySaved(text)
                onSaved(entry.id)
            }
            onDone(entry != null)
        }
    }

    fun addImage(
        bitmap: Bitmap,
        pinDisplayWidthPx: Int? = null,
        pinDisplayHeightPx: Int? = null,
        source: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (copy == null) {
            onDone(false)
            return
        }
        scope.launch {
            val entry = repo.addImage(
                bitmap = copy,
                pinDisplayWidthPx = pinDisplayWidthPx,
                pinDisplayHeightPx = pinDisplayHeightPx
            )
            rememberSource(entry?.id, source)
            if (entry != null) notifySaved("")
            onDone(entry != null)
        }
    }

    fun addRich(
        parts: List<StashRichPart>,
        htmlText: String? = null,
        source: String? = null,
        onDone: (Boolean) -> Unit = {},
        /** 拿到新条目 id 时回调（加号弹窗的多图条目要用它挂标签 / 提醒 / 撤销）。 */
        onSaved: (String) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copied = parts.mapNotNull { part ->
            when (part) {
                is StashRichPart.Text -> part
                is StashRichPart.Image -> {
                    val copy = part.bitmap.copy(part.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    StashRichPart.Image(copy)
                }
            }
        }
        if (copied.isEmpty()) {
            onDone(false)
            return
        }
        scope.launch {
            val entry = repo.addRich(copied, htmlText)
            rememberSource(entry?.id, source)
            if (entry != null) {
                notifySaved(copied.filterIsInstance<StashRichPart.Text>().joinToString("\n") { it.text })
                onSaved(entry.id)
            }
            onDone(entry != null)
        }
    }

    fun pinImageFromStash(context: Context, entry: StashEntry, bitmap: Bitmap) {
        ScreenPinManager.pinFromStashImage(
            context = context,
            bitmap = bitmap,
            displayWidthPx = entry.pinDisplayWidthPx,
            displayHeightPx = entry.pinDisplayHeightPx
        )
    }

    fun pinRichFromStash(context: Context, entry: StashEntry) {
        ScreenPinManager.pinStashRich(context, entry)
    }

    fun copyStashEntry(context: Context, entry: StashEntry): Boolean {
        val repo = StashAccess.repository
        return when (entry.type) {
            StashEntryType.TEXT -> {
                val text = entry.text.orEmpty()
                if (text.isBlank()) return false
                FloatBallTextPick.copyText(context, text)
                true
            }
            StashEntryType.IMAGE -> {
                val bitmap = repo?.loadImage(entry) ?: return false
                FloatBallTextPick.copyImage(context, bitmap)
                true
            }
            StashEntryType.RICH -> {
                val blocks = entry.resolvedContentBlocks()
                if (blocks.isEmpty()) return false
                ClipboardWriter.writeBlocks(
                    context = context,
                    blocks = blocks,
                    htmlText = entry.htmlText,
                    resolveDataUri = { fileName -> repo?.dataUriForFile(fileName) },
                    resolveContentUri = { fileName -> repo?.uriForFile(fileName) },
                    resolveDimensions = { fileName -> repo?.imageDimensions(fileName) }
                )
            }
        }
    }

    fun openStashPanel(context: Context) {
        FloatBallStashPanel.show(context)
    }

    fun openClipboardPanel(context: Context) {
        FloatBallStashPanel.show(
            context = context,
            initialTab = StashPanelInitialTab.Clipboard
        )
    }

    fun pinTextToScreen(context: Context, text: String) {
        ScreenPinManager.pinText(context, text)
    }

    fun pinImageToScreen(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null
    ) {
        ScreenPinManager.pinImage(context, bitmap, screenRect, layoutMeta)
    }

    fun pinRichFromClipboard(context: Context, entry: ClipboardEntry) {
        ScreenPinManager.pinClipboardEntry(context, entry)
    }

    /**
     * 跟手拉出：把手横向拖过阈值时调用，返回"是否真的开始跟手"。
     *
     * false 的情况：面板本来就开着、或侧栏窗没挂上（无障碍服务没开）——
     * 此时把手的手势要退回原来的"拖过阈值就打开"逻辑。
     */
    fun beginHandleReveal(context: Context): Boolean =
        FloatBallStashPanel.beginDragReveal(context)

    /** 跟手拉出：松手（[commit] = 过半就归位，否则弹回）。 */
    fun endHandleReveal(commit: Boolean) {
        FloatBallStashPanel.endDragReveal(commit)
    }

    fun addFromClipboard(context: Context, entry: ClipboardEntry, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                val blocks = entry.resolvedContentBlocks().filter { block ->
                    when (block.kind) {
                        ClipboardBlockKind.TEXT -> block.text.isNotBlank()
                        ClipboardBlockKind.IMAGE -> block.fileName.isNotBlank()
                    }
                }
                when {
                    blocks.size > 1 -> {
                        val parts = blocks.mapNotNull { block ->
                            when (block.kind) {
                                ClipboardBlockKind.TEXT -> StashRichPart.Text(block.text)
                                ClipboardBlockKind.IMAGE -> {
                                    val bitmap = ClipboardImageStore.loadBitmap(context, block.fileName)
                                        ?: return@mapNotNull null
                                    StashRichPart.Image(bitmap)
                                }
                            }
                        }
                        parts.takeIf { it.isNotEmpty() }?.let { repo.addRich(it, entry.htmlText) }
                    }
                    blocks.size == 1 -> {
                        val only = blocks.first()
                        when (only.kind) {
                            ClipboardBlockKind.TEXT -> repo.addText(only.text)
                            ClipboardBlockKind.IMAGE -> {
                                val bitmap = ClipboardImageStore.loadBitmap(context, only.fileName)
                                bitmap?.let { repo.addImage(it) }
                            }
                        }
                    }
                    else -> {
                        val text = entry.text.trim()
                        when {
                            text.isNotEmpty() -> repo.addText(text)
                            entry.hasImageContent() -> {
                                val bitmap = ClipboardImageStore.loadEntryThumbnail(context, entry)
                                bitmap?.let { repo.addImage(it) }
                            }
                            else -> null
                        }
                    }
                }
            }
            rememberSource(created?.id, StashMetaRepository.SOURCE_CLIPBOARD)
            if (created != null) notifySaved(created.text.orEmpty())
            onDone(created != null)
        }
    }
}
