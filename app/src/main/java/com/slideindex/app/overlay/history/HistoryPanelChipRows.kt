package com.slideindex.app.overlay.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.stash.StashTag

/**
 * 页签下方那一排筛选胶囊（设计稿 `.stream .chips`）。
 *
 * 两个页签**同一位置**、同一套 `HistoryChip`：
 * - 闪念 = 标签筛选（「全部」+ 各标签 + 末尾一枚「＋」进标签管理）；
 * - 剪贴板 = 固定筛选（全部 / 图片 / 链接 / 富文本 / 文件）。
 *
 * 用 `FlowRow` 而不是横向滚动：横滑会把首尾胶囊切掉（用户反馈过"直接被截断 / 最后一个胶囊
 * 形态是什么鬼"），换行布局永远不会裁内容。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HistoryTagChips(
    tags: List<StashTag>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    onManageTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HistoryChip(
            label = stringResource(R.string.stash_tag_filter_all),
            dotColor = null,
            selected = selectedTag == null,
            onClick = { onTagSelected(null) },
        )
        tags.forEach { tag ->
            HistoryChip(
                label = tag.name,
                dotColor = Color(tag.colorArgb),
                selected = selectedTag == tag.name,
                onClick = { onTagSelected(tag.name) },
            )
        }
        // 标签行末尾的「＋」（§0.16.4 待办 2）：进标签管理浮窗。永远在最后，跟着换行走。
        HistoryChip(
            label = "＋",
            dotColor = null,
            selected = false,
            onClick = onManageTags,
        )
    }
}

/**
 * 剪贴板页签的固定筛选行（§0.16.4 待办 3）。
 *
 * 这里**不显示各筛选项的条数**：条数要靠 SQL `COUNT` 才算得准（内存里只有已加载的几页），
 * 5 枚胶囊各挂一个数字既挤又要多 5 次查询，而头部那个总数已经把"有没有东西"说清楚了。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HistoryClipboardFilterChips(
    selected: ClipboardHistoryFilter,
    onSelected: (ClipboardHistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ClipboardHistoryFilter.entries.forEach { filter ->
            HistoryChip(
                label = stringResource(clipboardFilterLabelRes(filter)),
                dotColor = null,
                selected = filter == selected,
                onClick = { onSelected(filter) },
            )
        }
    }
}

private fun clipboardFilterLabelRes(filter: ClipboardHistoryFilter): Int = when (filter) {
    ClipboardHistoryFilter.All -> R.string.stash_tag_filter_all
    ClipboardHistoryFilter.Image -> R.string.clipboard_filter_image
    ClipboardHistoryFilter.Link -> R.string.clipboard_filter_link
    ClipboardHistoryFilter.RichText -> R.string.clipboard_filter_rich
    ClipboardHistoryFilter.File -> R.string.clipboard_filter_file
}
