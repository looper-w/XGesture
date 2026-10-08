package com.slideindex.app.overlay.history

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.stash.StashAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class HistoryImageSource {
    Clipboard,
    Stash,
}

/**
 * 卡片内图片的**解码超前系数**（§0.16.19）。
 *
 * 1.5 = 用户给的 1.5~2 里偏保守的一档：卡片图是 `ContentScale.Fit` + 高度上限，
 * 竖图的实际渲染宽度只有"高度 × 宽高比"，再叠上 `inSampleSize` 只能取 2 的幂，
 * 不超前就会掉档。取 1.5 而不是 2.0 是因为卡片的解码结果会**同时**留在
 * `thumbnailCache`（`LruCache`）里，列表里几十张的量级下要让缓存装得下。
 */
private const val HistoryCardImageOversample = 1.5f

@Composable
internal fun HistoryContentBlockView(
    block: ClipboardContentBlock,
    imageSource: HistoryImageSource,
    entryId: String,
    context: Context,
    previewWidthPx: Int,
    previewHeightPx: Int,
    expanded: Boolean,
) {
    when (block.kind) {
        ClipboardBlockKind.TEXT -> {
            Text(
                text = block.text,
                style = HistoryPanelTypography.content(),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
        ClipboardBlockKind.IMAGE -> {
            // §0.16.19：解码目标 = **显示宽度 × 超前系数**。
            //
            // `previewWidthPx` 是卡片内容的显示宽度（由 `historyPreviewWidthPx()` 按面板真实宽度算），
            // 这里再乘 [HistoryCardImageOversample]：下面那张图是 `ContentScale.Fit` +
            // `heightIn(max = 150/200.dp)`，**竖图**会被"高度塞满"→ 实际渲染宽度只有
            // `150dp × 宽高比`（3:4 竖图 ≈ 405px），而 `inSampleSize` 只能取 2 的幂，
            // 不超前一点就会掉到一半那一档 —— 用户看到的"缩略图也糊"多半是这种竖图。
            //
            // 内存：卡片的解码结果都进 `thumbnailCache`（`LruCache`，1/8 堆），且旧值会被换出；
            // 单个 1266×1728 的条目 ≈ 8.7MB，在 1/8 堆（通常数十 MB）之内。
            val decodeWidthPx = if (expanded) {
                previewWidthPx
            } else {
                (previewWidthPx * HistoryCardImageOversample).toInt()
            }
            val decodeMaxSidePx = if (expanded) historyExpandedImageMaxSidePx() else decodeWidthPx
            var bitmap by remember(entryId, block.fileName, decodeMaxSidePx, previewHeightPx, expanded, imageSource) {
                mutableStateOf<Bitmap?>(null)
            }
            LaunchedEffect(entryId, block.fileName, decodeMaxSidePx, previewHeightPx, expanded, imageSource) {
                if (block.fileName.isBlank()) return@LaunchedEffect
                bitmap = withContext(Dispatchers.IO) {
                    when (imageSource) {
                        HistoryImageSource.Clipboard -> {
                            if (expanded) {
                                ClipboardThumbnailCache.loadBlockThumbnail(context, block.fileName, decodeMaxSidePx)
                            } else {
                                ClipboardThumbnailCache.loadBlockThumbnailForCard(
                                    context,
                                    block.fileName,
                                    decodeMaxSidePx,
                                    previewHeightPx,
                                )
                            }
                        }
                        HistoryImageSource.Stash -> {
                            StashAccess.repository?.loadThumbnailByFileName(
                                entryId,
                                block.fileName,
                                decodeMaxSidePx,
                            )
                        }
                    }
                }
            }
            val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
            if (imageBitmap != null) {
                if (expanded) {
                    HistoryExpandedImage(imageBitmap = imageBitmap)
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (imageSource == HistoryImageSource.Stash) 150.dp else 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.clipboard_image_unavailable),
                    style = HistoryPanelTypography.hint(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
internal fun HistoryExpandedImage(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
internal fun rememberHistoryImageBitmap(bitmap: Bitmap?): ImageBitmap? =
    remember(bitmap) { bitmap?.asImageBitmap() }
