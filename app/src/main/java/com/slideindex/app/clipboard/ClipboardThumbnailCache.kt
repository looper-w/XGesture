package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

/**
 * LRU cache for clipboard list preview thumbnails so [LazyColumn] recycle does not re-decode.
 */
object ClipboardThumbnailCache {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024

    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 原图尺寸缓存（只存 w×h，一条几十字节）：折叠态占位高度要用，避免反复读 header。 */
    private val originalSizeCache = LruCache<String, Pair<Int, Int>>(256)

    fun evictAll() {
        bitmapCache.evictAll()
    }

    fun loadEntryThumbnailsForPreview(
        context: Context,
        entry: ClipboardEntry,
        maxSidePx: Int
    ): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { fileName ->
                getOrLoadFile(context, fileName, maxSidePx)
            }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return getOrLoadUri(context, entry.uri, maxSidePx)?.let { listOf(it) } ?: emptyList()
    }

    fun loadBlockThumbnail(context: Context, fileName: String, maxSidePx: Int): Bitmap? =
        getOrLoadFile(context, fileName, maxSidePx)

    fun loadEntryThumbnailsForCard(
        context: Context,
        entry: ClipboardEntry,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
        /** true = 整图等比缩进目标框、**不裁切**（浮窗卡片样式）。 */
        fitWhole: Boolean = false,
    ): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { fileName ->
                getOrLoadFileForCard(context, fileName, targetWidthPx, maxVisibleHeightPx, fitWhole)
            }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return getOrLoadUriForCard(context, entry.uri, targetWidthPx, maxVisibleHeightPx, fitWhole)
            ?.let { listOf(it) }
            ?: emptyList()
    }

    /**
     * 原图尺寸（px），用于"展开时不放大"的尺寸计算。
     * 文件图读本地文件 bounds，URI 图读流 bounds；都拿不到时返回 null（调用方退回旧行为）。
     */
    fun loadEntryOriginalSize(context: Context, entry: ClipboardEntry): Pair<Int, Int>? {
        val fileName = entry.resolvedImageFileNames().firstOrNull()
        val uri = entry.uri?.takeIf { it.isNotBlank() }
        val key = fileName?.let { "file:$it" } ?: uri?.let { "uri:$it" } ?: return null
        originalSizeCache.get(key)?.let { return it }
        val size = if (fileName != null) {
            ClipboardImageStore.imageDimensions(context, fileName)
        } else {
            ClipboardImageStore.uriImageDimensions(context, uri.orEmpty())
        } ?: return null
        originalSizeCache.put(key, size)
        return size
    }

    /**
     * 单张图片文件的**原图尺寸**（px）。
     *
     * 多图富文本展开时，每张图都要各算自己的"完整显示、不放大"尺寸，所以不能只取首图
     * （[loadEntryOriginalSize] 只返回第一张）。同样走 `originalSizeCache`，只存宽高。
     */
    fun loadBlockOriginalSize(context: Context, fileName: String): Pair<Int, Int>? {
        if (fileName.isBlank()) return null
        val key = "file:$fileName"
        originalSizeCache.get(key)?.let { return it }
        val size = ClipboardImageStore.imageDimensions(context, fileName) ?: return null
        originalSizeCache.put(key, size)
        return size
    }

    fun loadBlockThumbnailForCard(
        context: Context,
        fileName: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
        /** true = 整图等比缩进目标框、**不裁切**（浮窗折叠态轮播用）。 */
        fitWhole: Boolean = false,
    ): Bitmap? = getOrLoadFileForCard(context, fileName, targetWidthPx, maxVisibleHeightPx, fitWhole)

    fun evictEntry(entry: ClipboardEntry) {
        val fileNames = entry.resolvedImageFileNames()
        val uri = entry.uri?.takeIf { it.isNotBlank() }
        val keysToRemove = bitmapCache.snapshot().keys.filter { key ->
            fileNames.any { fileName -> key.startsWith("file:$fileName:") } ||
                (uri != null && key.startsWith("uri:$uri:"))
        }
        keysToRemove.forEach { bitmapCache.remove(it) }
        fileNames.forEach { originalSizeCache.remove("file:$it") }
        uri?.let { originalSizeCache.remove("uri:$it") }
    }

    fun clear() {
        bitmapCache.evictAll()
    }

    private fun getOrLoadFile(context: Context, fileName: String, maxSidePx: Int): Bitmap? {
        val key = fileKey(fileName, maxSidePx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadBitmapScaled(context, fileName, maxSidePx) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadUri(context: Context, uri: String, maxSidePx: Int): Bitmap? {
        val key = uriKey(uri, maxSidePx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadUriBitmapScaled(context, uri, maxSidePx) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadFileForCard(
        context: Context,
        fileName: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
        fitWhole: Boolean = false,
    ): Bitmap? {
        val key = fileCardKey(fileName, targetWidthPx, maxVisibleHeightPx, fitWhole)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadThumbnailForCard(
            context,
            fileName,
            targetWidthPx,
            maxVisibleHeightPx,
            fitWhole
        ) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadUriForCard(
        context: Context,
        uri: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
        fitWhole: Boolean = false,
    ): Bitmap? {
        val key = uriCardKey(uri, targetWidthPx, maxVisibleHeightPx, fitWhole)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadUriThumbnailForCard(
            context,
            uri,
            targetWidthPx,
            maxVisibleHeightPx,
            fitWhole
        ) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun fileKey(fileName: String, maxSidePx: Int = 0): String = "file:$fileName:$maxSidePx"

    private fun uriKey(uri: String, maxSidePx: Int = 0): String = "uri:$uri:$maxSidePx"

    private fun fileCardKey(
        fileName: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
        fitWhole: Boolean = false,
    ): String = "file:$fileName:w$targetWidthPx:h$maxVisibleHeightPx${if (fitWhole) ":fit" else ""}"

    private fun uriCardKey(
        uri: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int,
        fitWhole: Boolean = false,
    ): String = "uri:$uri:w$targetWidthPx:h$maxVisibleHeightPx${if (fitWhole) ":fit" else ""}"
}
