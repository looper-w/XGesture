package com.slideindex.app.overlay.history

import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间轴分组（设计稿 `.grp` 的今天 / 昨天 / 更早）。
 *
 * 覆盖的是纯逻辑：分组边界（含夏令时）与"扁平化成 Lazy 行"的顺序 / key 唯一性 /
 * 竖线强度单调递减。
 */
class HistoryTimelineGroupingTest {

    private val shanghai: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val newYork: TimeZone = TimeZone.getTimeZone("America/New_York")

    private fun at(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun entry(id: String, createdAtMs: Long, starred: Boolean = false) = StashEntry(
        id = id,
        type = StashEntryType.TEXT,
        text = id,
        createdAtEpochMs = createdAtMs,
        starred = starred,
    )

    @Test
    fun `day groups are cut at local midnight`() {
        val now = at(shanghai, 2026, 3, 10, 10)
        assertEquals(HistoryDayGroup.Today, historyDayGroupOf(at(shanghai, 2026, 3, 10, 0), now, shanghai))
        assertEquals(HistoryDayGroup.Today, historyDayGroupOf(at(shanghai, 2026, 3, 10, 9, 59), now, shanghai))
        assertEquals(HistoryDayGroup.Yesterday, historyDayGroupOf(at(shanghai, 2026, 3, 9, 23, 59), now, shanghai))
        assertEquals(HistoryDayGroup.Yesterday, historyDayGroupOf(at(shanghai, 2026, 3, 9, 0), now, shanghai))
        assertEquals(HistoryDayGroup.Earlier, historyDayGroupOf(at(shanghai, 2026, 3, 8, 23, 59), now, shanghai))
    }

    @Test
    fun `future timestamps count as today`() {
        val now = at(shanghai, 2026, 3, 10, 10)
        // 时钟回拨 / 记录时间被改到未来时不该掉进"更早"。
        assertEquals(HistoryDayGroup.Today, historyDayGroupOf(at(shanghai, 2026, 3, 11, 3), now, shanghai))
    }

    @Test
    fun `yesterday boundary survives a dst fall back`() {
        // 2026-11-01 美东回拨（EDT→EST）：10-31 到 11-01 之间是 25 小时。
        // 如果用 "今天零点 - 86400000" 算昨天零点，10-31 00:30 会被错判成「更早」。
        val now = at(newYork, 2026, 11, 1, 12)
        assertEquals(HistoryDayGroup.Yesterday, historyDayGroupOf(at(newYork, 2026, 10, 31, 0, 30), now, newYork))
        assertEquals(HistoryDayGroup.Earlier, historyDayGroupOf(at(newYork, 2026, 10, 30, 23, 30), now, newYork))
    }

    @Test
    fun `rows interleave group headers and keep entry order`() {
        val rows = buildHistoryTimelineRows(
            entries = listOf(
                entry("today-new", at(shanghai, 2026, 3, 10, 9)),
                entry("today-old", at(shanghai, 2026, 3, 10, 8)),
                entry("yesterday", at(shanghai, 2026, 3, 9, 20)),
                entry("earlier", at(shanghai, 2026, 3, 1, 20)),
            ),
            nowMs = at(shanghai, 2026, 3, 10, 10),
            zone = shanghai,
        )
        assertEquals(
            listOf(
                "header:Today",
                "entry:today-new",
                "entry:today-old",
                "header:Yesterday",
                "entry:yesterday",
                "header:Earlier",
                "entry:earlier",
            ),
            rows.map { row ->
                when (row) {
                    is HistoryTimelineRow.GroupHeader -> "header:${row.group}"
                    is HistoryTimelineRow.Entry -> "entry:${row.entry.id}"
                }
            },
        )
    }

    @Test
    fun `line alpha fades monotonically inside a group`() {
        val rows = buildHistoryTimelineRows(
            entries = listOf(
                entry("a", at(shanghai, 2026, 3, 10, 9)),
                entry("b", at(shanghai, 2026, 3, 10, 8)),
                entry("c", at(shanghai, 2026, 3, 10, 7)),
            ),
            nowMs = at(shanghai, 2026, 3, 10, 10),
            zone = shanghai,
        )
        // 表头最亮，往下依次变暗 —— 逐行画竖线时这才不会出现"每行各渐变一次"的接缝。
        assertEquals(1f, rows[0].lineAlpha, 0.0001f)
        assertTrue(rows[1].lineAlpha < rows[0].lineAlpha)
        assertTrue(rows[2].lineAlpha < rows[1].lineAlpha)
        assertTrue(rows[3].lineAlpha < rows[2].lineAlpha)
        assertTrue(rows[3].lineAlpha > 0f)
    }

    @Test
    fun `every entry carries its own group for node colouring`() {
        val rows = buildHistoryTimelineRows(
            entries = listOf(
                entry("today", at(shanghai, 2026, 3, 10, 9)),
                entry("earlier", at(shanghai, 2026, 2, 1, 9)),
            ),
            nowMs = at(shanghai, 2026, 3, 10, 10),
            zone = shanghai,
        )
        val groups = rows.filterIsInstance<HistoryTimelineRow.Entry>().associate { it.entry.id to it.group }
        assertEquals(HistoryDayGroup.Today, groups["today"])
        assertEquals(HistoryDayGroup.Earlier, groups["earlier"])
    }

    @Test
    fun `keys stay unique even when a group appears twice`() {
        // 数据层没承诺排序，一旦同一组被拆成不连续的两段，重复 key 会让 LazyColumn 直接崩。
        val rows = buildHistoryTimelineRows(
            entries = listOf(
                entry("today-1", at(shanghai, 2026, 3, 10, 9)),
                entry("earlier-1", at(shanghai, 2026, 2, 1, 9)),
                entry("today-2", at(shanghai, 2026, 3, 10, 8)),
            ),
            nowMs = at(shanghai, 2026, 3, 10, 10),
            zone = shanghai,
        )
        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        // 每一段都要有自己的表头，否则第二段「今天」的条目会挂在「更早」表头下面。
        assertEquals(
            listOf(
                "header:Today",
                "entry:today-1",
                "header:Earlier",
                "entry:earlier-1",
                "header:Today",
                "entry:today-2",
            ),
            rows.map { row ->
                when (row) {
                    is HistoryTimelineRow.GroupHeader -> "header:${row.group}"
                    is HistoryTimelineRow.Entry -> "entry:${row.entry.id}"
                }
            },
        )
    }

    @Test
    fun `empty list produces no rows`() {
        assertTrue(buildHistoryTimelineRows(emptyList(), nowMs = 0L, zone = shanghai).isEmpty())
    }
}
