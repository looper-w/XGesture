package com.slideindex.app.clipboardfloat

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.clipboard.ClipboardSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 剪贴板条目的来源展示（图标 + 名称 + 方式）。
 *
 * 来源信息的精度见 [ClipboardSourceKind]：
 * - 普通复制 = 复制那一刻的前台应用（推断，绝大多数情况正确）；
 * - 取词 / 截图 = 发起那一刻的应用（精确）。
 */
internal data class ClipboardFloatSourceInfo(
    val label: String,
    val icon: ImageBitmap?,
    val kind: ClipboardSourceKind,
)

/** 来源图标缓存：一次历史里通常只有十几个不同 App，按包名缓存即可。 */
private val sourceIconCache = LruCache<String, ImageBitmap>(64)

private suspend fun loadSourceInfo(context: Context, packageName: String): ClipboardFloatSourceInfo =
    withContext(Dispatchers.IO) {
        val pm = context.applicationContext.packageManager
        val label = runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val icon = sourceIconCache.get(packageName) ?: runCatching {
            pm.getApplicationIcon(packageName).toBitmap(width = 72, height = 72).asImageBitmap()
        }.getOrNull()?.also { sourceIconCache.put(packageName, it) }
        ClipboardFloatSourceInfo(label = label, icon = icon, kind = ClipboardSourceKind.APP)
    }

/**
 * 解析一条记录的来源；返回 null 表示"没有任何可展示的信息"（理论上只有老数据 + 未知方式）。
 *
 * 图标解码在 IO 线程做，并按包名缓存；Compose 侧用 [produceState] 拿到后自动重组。
 */
@Composable
internal fun rememberClipboardFloatSourceInfo(entry: ClipboardEntry): ClipboardFloatSourceInfo? {
    val context = LocalContext.current
    val packageName = entry.sourcePackage
    val unknownLabel = stringResource(R.string.clipboard_float_source_unknown)
    val selfLabel = stringResource(R.string.clipboard_float_source_self)
    val info by produceState<ClipboardFloatSourceInfo?>(
        initialValue = null,
        packageName,
        entry.sourceKind,
        unknownLabel,
        selfLabel,
    ) {
        value = when {
            !packageName.isNullOrBlank() -> {
                val loaded = loadSourceInfo(context, packageName)
                loaded.copy(kind = entry.sourceKind)
            }

            entry.sourceKind == ClipboardSourceKind.SELF ->
                ClipboardFloatSourceInfo(selfLabel, null, ClipboardSourceKind.SELF)

            entry.sourceKind == ClipboardSourceKind.UNKNOWN ->
                ClipboardFloatSourceInfo(unknownLabel, null, ClipboardSourceKind.UNKNOWN)

            else -> ClipboardFloatSourceInfo(unknownLabel, null, entry.sourceKind)
        }
    }
    return info
}

/** 卡片 / 单行里的小徽标：`[图标] 微信 · 取词`。 */
@Composable
internal fun ClipboardFloatSourceBadge(
    entry: ClipboardEntry,
    modifier: Modifier = Modifier,
    maxWidthLabel: Boolean = false,
) {
    val info = rememberClipboardFloatSourceInfo(entry) ?: return
    val scheme = MiuixTheme.colorScheme
    val kindLabel = when (info.kind) {
        ClipboardSourceKind.PICKED -> stringResource(R.string.clipboard_float_source_picked)
        ClipboardSourceKind.SCREENSHOT -> stringResource(R.string.clipboard_float_source_screenshot)
        else -> null
    }
    val text = if (kindLabel.isNullOrBlank()) info.label else "${info.label} · $kindLabel"
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        info.icon?.let { icon ->
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = scheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (maxWidthLabel) Modifier.weight(1f) else Modifier,
        )
    }
}

/**
 * 按来源筛选的一行 chip：`全部` + 每个出现过的 App。
 *
 * @param packages 历史里出现过的来源包名（[ClipboardFloatListController.availableSources]）
 * @param selected null = 全部
 */
@Composable
internal fun ClipboardFloatSourceFilterRow(
    packages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (packages.isEmpty()) return
    val context = LocalContext.current
    val allLabel = stringResource(R.string.clipboard_float_filter_all)
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        item(key = "all") {
            ClipboardFloatSourceChip(
                label = allLabel,
                icon = null,
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(packages, key = { it }) { pkg ->
            val info by produceState<ClipboardFloatSourceInfo?>(initialValue = null, pkg) {
                value = loadSourceInfo(context, pkg)
            }
            ClipboardFloatSourceChip(
                label = info?.label ?: pkg,
                icon = info?.icon,
                selected = selected == pkg,
                onClick = { onSelect(if (selected == pkg) null else pkg) },
            )
        }
    }
}

/**
 * 「类型栏」：工具栏正下方**常显**的一行胶囊
 * （全部 / **收藏** / 文本 / 图片 / 链接 / 富文本；「文件」暂不展示）。
 *
 * 胶囊样式与「按来源筛选」完全一致（同一个 [ClipboardFloatSourceChip]），只是内容换成固定分类；
 * 单选、语义与侧边历史面板的剪贴板筛选行（`HistoryClipboardFilterChips`）相同，判据也共用
 * [ClipboardHistoryFilter]，两边筛出来的条数不会打架。
 *
 * **「收藏」排第二**（紧跟「全部」）：它是"跨类型的视图"，和类型同处一行且**互斥** ——
 * 选中收藏时类型回到「全部」，选中类型时取消收藏（由 [onSelect] 一次改完，见
 * `ClipboardFloatListController.selectTypeBar`）。排在类型之前，是因为它比"某一类"更常用。
 *
 * 空库时也照常显示（不像来源栏那样在没数据时整行消失）：它是"分类入口"，不是"筛选结果"。
 */
@Composable
internal fun ClipboardFloatTypeFilterRow(
    selected: ClipboardHistoryFilter,
    onlyStarred: Boolean,
    onSelect: (ClipboardHistoryFilter, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // 「全部」与各类型互斥于「收藏」：收藏亮着时它们一律不亮。
        item(key = "all") {
            ClipboardFloatSourceChip(
                label = stringResource(R.string.clipboard_float_filter_all),
                icon = null,
                selected = !onlyStarred && selected == ClipboardHistoryFilter.All,
                onClick = { onSelect(ClipboardHistoryFilter.All, false) },
            )
        }
        item(key = "starred") {
            ClipboardFloatSourceChip(
                label = stringResource(R.string.clipboard_float_favorite),
                icon = null,
                selected = onlyStarred,
                // 再点一次已选中的「收藏」= 回到「全部」，与「全部」胶囊同义，少一次绕路。
                onClick = { onSelect(ClipboardHistoryFilter.All, !onlyStarred) },
            )
        }
        // 工具栏这一行只留常用四类：文本 / 图片 / 链接 / 富文本（+ 前面的「收藏」）。
        // 「文件」暂时不展示 —— 枚举与 SQL 里仍保留 File，侧边历史面板的筛选行照旧有它，
        // 以后想加回来把这里的过滤条件去掉即可。
        items(
            ClipboardHistoryFilter.entries.filter {
                it != ClipboardHistoryFilter.All && it != ClipboardHistoryFilter.File
            },
            key = { it.name },
        ) { filter ->
            ClipboardFloatSourceChip(
                label = stringResource(clipboardFloatTypeFilterLabel(filter)),
                icon = null,
                selected = !onlyStarred && filter == selected,
                onClick = { onSelect(filter, false) },
            )
        }
    }
}

private fun clipboardFloatTypeFilterLabel(filter: ClipboardHistoryFilter): Int = when (filter) {
    ClipboardHistoryFilter.All -> R.string.clipboard_float_filter_all
    // 复用条目类型标识的文案（文本 / Text / テキスト / نص），四语已就绪。
    ClipboardHistoryFilter.Text -> R.string.clipboard_entry_type_text
    ClipboardHistoryFilter.Image -> R.string.clipboard_filter_image
    ClipboardHistoryFilter.Link -> R.string.clipboard_filter_link
    ClipboardHistoryFilter.RichText -> R.string.clipboard_filter_rich
    ClipboardHistoryFilter.File -> R.string.clipboard_filter_file
}

@Composable
private fun ClipboardFloatSourceChip(
    label: String,
    icon: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceContainerHigh.copy(alpha = 0.7f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * 多选删除的**列表级状态**。
 *
 * 交互规则（与现有长按手势不冲突）：
 * - 由标题栏的「选择」按钮进入；进入后**单击 = 勾选**，长按 / 拖动禁用；
 * - 顶部操作条：全选 / 删除（二次确认）/ 取消。
 */
internal class ClipboardFloatSelectionState {
    var active by mutableStateOf(false)
        private set

    var ids by mutableStateOf<Set<String>>(emptySet())
        private set

    fun enter() {
        active = true
        ids = emptySet()
    }

    fun exit() {
        active = false
        ids = emptySet()
    }

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun replaceAll(all: Collection<String>) {
        ids = all.toSet()
    }
}
