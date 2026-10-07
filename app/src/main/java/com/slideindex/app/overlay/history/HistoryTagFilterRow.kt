package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.stash.StashTag
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 标签筛选行 —— 设计稿里页签下方那一排 chip（`ui_demo_capsule.html` 的 `.stream .chips`）。
 *
 * 只做单选筛选（「全部」= null）。标签定义来自 `StashMetaRepository`（独立文件
 * `stash_meta.json`），所以用户增删标签后这一行自动跟着变，不需要额外的状态同步。
 *
 * ⚠️ 这里是**横向滚动**，不要加滚动条 —— Android 上横滑即可（`docs/ui-guidelines.md` 的一贯做法）。
 */
@Composable
internal fun HistoryTagFilterRow(
    tags: List<StashTag>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryTagChip(
            label = stringResource(R.string.stash_tag_filter_all),
            dotColor = null,
            selected = selectedTag == null,
            onClick = { onTagSelected(null) },
        )
        tags.forEach { tag ->
            HistoryTagChip(
                label = tag.name,
                dotColor = Color(tag.colorArgb),
                selected = selectedTag == tag.name,
                onClick = { onTagSelected(if (selectedTag == tag.name) null else tag.name) },
            )
        }
    }
}

@Composable
private fun HistoryTagChip(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    scheme.primaryVariant.copy(alpha = 0.35f)
                } else {
                    scheme.surfaceContainer
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    scheme.primary.copy(alpha = 0.45f)
                } else {
                    scheme.dividerLine
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            color = if (selected) scheme.onBackground else scheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
