package com.slideindex.app.clipboard



data class ClipboardPayload(

    val type: ClipboardEntryType,

    val text: String,

    val uri: String? = null,

    val intentUri: String? = null,

    val htmlText: String? = null,

    val mimeType: String? = null,

    val imageFileName: String? = null,

    val imageFileNames: List<String> = emptyList(),

    val imageUris: List<String> = emptyList(),

    /**
     * **显式来源**：由本应用主动发起的动作（取词 / 截图）填这里。
     * 非 null 时不会再按"当前前台应用"推断，也不会被"自我写入"回声抑制吞掉。
     */
    val sourcePackage: String? = null,

    val sourceKind: ClipboardSourceKind = ClipboardSourceKind.UNKNOWN,

) {

    fun contentKey(): String = ClipboardContentKey.forPayload(this)



    fun resolvedImageUris(): List<String> {

        val fromList = imageUris.filter { it.isNotBlank() }.distinct()

        if (fromList.isNotEmpty()) return fromList

        return uri?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

    }



    fun resolvedImageFileNames(): List<String> {

        val fromList = imageFileNames.filter { it.isNotBlank() }

        if (fromList.isNotEmpty()) return fromList

        return imageFileName?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

    }



    fun hasImageContent(): Boolean =

        resolvedImageFileNames().isNotEmpty() ||

            resolvedImageUris().isNotEmpty() ||

            (mimeType?.startsWith("image/") == true && !uri.isNullOrBlank()) ||

            (!htmlText.isNullOrBlank() && ClipboardHtmlParser.imageSources(htmlText).isNotEmpty())



    fun toEntry(
        id: String,
        createdAtEpochMs: Long,
        sourcePackageOverride: String? = this.sourcePackage,
        sourceKindOverride: ClipboardSourceKind = this.sourceKind,
    ): ClipboardEntry {

        val fileNames = resolvedImageFileNames()

        return ClipboardEntry(

            id = id,

            type = type,

            text = text,

            uri = uri,

            intentUri = intentUri,

            htmlText = htmlText,

            mimeType = mimeType,

            imageFileName = fileNames.firstOrNull() ?: imageFileName,

            imageFileNames = fileNames,

            createdAtEpochMs = createdAtEpochMs,

            sourcePackage = sourcePackageOverride,

            sourceKind = sourceKindOverride,

        )

    }

}



fun ClipboardEntry.resolvedImageFileNames(): List<String> {

    val fromList = imageFileNames.filter { it.isNotBlank() }

    if (fromList.isNotEmpty()) return fromList

    return imageFileName?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

}



fun ClipboardEntry.hasImageContent(): Boolean =

    resolvedImageFileNames().isNotEmpty() ||

        (type == ClipboardEntryType.URI && mimeType?.startsWith("image/") == true) ||

        (type == ClipboardEntryType.HTML && !htmlText.isNullOrBlank() &&

            ClipboardHtmlParser.imageSources(htmlText).isNotEmpty())



fun ClipboardEntry.displayTypeLabelKey(): ClipboardEntryType = when {

    hasImageContent() && text.isNotBlank() -> ClipboardEntryType.HTML

    else -> type

}

/** 本地 `content://` / `file://` 条目代表文件本身，不能按文本/富文本粘贴。 */
fun ClipboardEntry.isLocalFileUriEntry(): Boolean {
    if (type != ClipboardEntryType.URI) return false
    val raw = uri?.trim().orEmpty()
    return raw.startsWith("content://", ignoreCase = true) ||
        raw.startsWith("file://", ignoreCase = true)
}

