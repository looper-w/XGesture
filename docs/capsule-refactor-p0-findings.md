# P0 勘察结论：按 UI demo 重构「闪念」的落位清单

> 勘察方式：4 个并行子任务 + 主 agent 关键文件精读。**全程只读，未改任何代码，未跑 gradle。**
> 设计基线：`ui_demo_capsule.2050.html`
> 计划：`docs/capsule-refactor-plan.md`（本文修正其中若干判断）

---

## 1. 决定性结论：把手与面板**是两个独立的 Window**

| | 把手 | 面板 |
| :- | :- | :- |
| 宿主 | `service/HistoryFloatService.kt` 自建 `ComposeView` | `overlay/OverlayFullScreenPanelHost.kt` |
| 挂窗 | `HistoryFloatService.kt:145` `windowManager.addView(view, mainParams)` | `OverlayFullScreenPanelHost.kt:77` `wm.addView(compose, params)` |
| 尺寸 | **`WRAP_CONTENT × WRAP_CONTENT`**（只有把手那么大）`:139-140` | **`MATCH_PARENT × MATCH_PARENT`** `OverlayPanelLayoutParams.kt:27-30` |
| 位置 | `gravity = END or TOP`，`x=0`、`y=positionY` `:142,184-185,198-199` | `gravity = TOP or START` `OverlayPanelLayoutParams.kt:42` |
| type | `overlayWindowType(this)` = `TYPE_APPLICATION_OVERLAY` `:137` | `contentPanelWindowType`（强制 `TYPE_APPLICATION_OVERLAY`）`OverlayPanelLayoutParams.kt:61` |
| **WindowManager 来源** | **Service 自己的 WM** `:65` | **无障碍服务的 WM**：`OverlaySidePanelHost.kt:80` → `OverlayDependencyAccess.kt:17` → `SlideIndexAccessibilityService.kt:696 overlayHostContext()` |

**两窗之间目前零协同**：面板链路里对 `HistoryFloatService`/把手**零引用**（子任务逐个 grep 确认）；没有共享状态、没有动画同步、没有 token、没有 z-order 协调。
唯一连接是**单向调用**：`HistoryFloatService.kt:214-216 openClipboardPanel()` → `StashCoordinator.kt:138-143 FloatBallStashPanel.show(...)`。

### 1.1 后果：「跟手拉出」现在做不到

demo 里那一下是**同一张纸上的一笔**。真机上：

- **把手窗**只有把手那么大（`WRAP_CONTENT`），**画不出面板**；
- **面板窗**是全屏窗，它的入场动画写死在 `OverlaySidePanelHost.kt:215-224`：
  `AnimatedVisibility(enter = slideInHorizontally(spring(0.8, 300f), initialOffsetX = ±整宽) + fadeIn(tween(250)), exit = ...)` —— **整块从边缘滑入，与把手没有任何进度联动**。
- 两个窗还分属两个不同的 `WindowManager`（Service vs 无障碍服务），跨窗连"同一帧"都难保证。

**三条出路**（对应计划 §4.1）：

| 方案 | 做法 | 效果 | 代价 |
| :- | :- | :- | :- |
| **A 同窗** | 把面板也放进把手那个窗，或把手与面板合并到一个新的全屏窗 | 真·跟手，最接近 demo | 要动 `HistoryFloatService` 的窗结构；把手常驻性与面板生命周期耦合；注意把手窗现在是 `FLAG_NOT_FOCUSABLE`，面板需要动态抢焦点（见 §3） |
| **B 跨窗同步** | 新建一个 `revealProgress` 共享状态，两窗各自订阅 | 接近跟手，可能有 1 帧撕裂 | 无先例，要新写；两窗不同 WM，风险中等 |
| **C 降级** | 保持现状：点/左滑 → 面板整块滑入（spring，已有） | 稳，无新风险 | 丢掉"黏手指"手感；但**现状的弹簧入场本身已经不错** |

> **我的建议：先按 C 交付（几乎零成本，因为已经存在），把 A 作为独立一期做，B 只在 A 不可行时考虑。** 理由：C 能立刻拿到大部分价值，而 A 会动到最常驻的那个窗（风险最高），应该单独隔离验证。

---

## 2. ⚠️ 重大修正：我原以为要新写的，一半已经有了

计划文档里我把这些列为"新增"，**勘察后证明已经存在**：

| 项 | 现状 | 证据 |
| :- | :- | :- |
| **面板搜索** | **已实现**：两个页签各一个 query，200ms 防抖 | `HistoryPanelViewModel.kt:38-42,53-59,89-95`；匹配函数 `StashEntry.kt:65-70 matchesQuery` |
| **两页签 + initialTab** | **已实现**，且语义已是 `Stash / Clipboard` | `FloatBallStashPanel.kt:18-21` `StashPanelInitialTab`；`HistoryPanelViewModel.kt:26-29` `HistoryPanelTab` |
| **返回键** | **已实现**，且输入态优先退 | `OverlaySidePanelHost.kt:259-266 handlePanelBack()` |
| **面板输入焦点动态激活** | **已实现**：默认 `NOT_FOCUSABLE`，需要输入时才抢 | `OverlaySidePanelHost.kt:243-257`（`ensurePanelNonFocusable` / `activatePanelInputFocus`） |
| **与悬浮球的 z-order 协同** | **已实现** | `OverlaySidePanelHost.kt:272-282` → `FloatBallOverlay.notifyPanelAttachedAboveChrome()` |
| **玻璃模糊可配** | **已实现**（开关 + 半径） | `FloatBallStashPanel.kt:115-116`；`OverlaySidePanelHost.kt:336 updateBackgroundBlur` |
| **把手位置持久化（横竖分开）** | **已实现**：两个 key | `SettingsPreferenceKeys.kt:599-602`；`HistoryFloatService.kt:161-168,203-212` |
| **把手全屏/横屏隐藏** | **已实现**（全屏 500ms 轮询 + 横屏开关） | `HistoryFloatService.kt:54-59,230-241,243-257` |
| **上限 200 / 跨进程 / 分页** | **已实现** | `StashRepository.kt:423`、`CrossProcessStore.kt`、`HistoryFloatPagination.kt` |
| **面板圆角 14dp / 半屏宽** | **已实现** | `HistoryPanelScreen.kt:217-221`、`HistoryPanelUi.kt:65-71` |

**这条修正很重要**：它意味着工期里"面板加搜索""返回键处理"这类条目**可以直接划掉**。

---

## 3. 真正要新写的（修正后）

| 项 | 说明 | 依据 |
| :- | :- | :- |
| **标签系统** | `tags.json` 独立文件 + chip 行 + 筛选 + 编辑 | `StashEntry` 10 个字段里**没有任何标签字段**（`StashEntry.kt:14-27`） |
| **完成态** | `done.json` 独立文件 + 完成动作 + 划掉样式 + 完成后取消提醒 | 同上，无该字段 |
| **标签进搜索** | 在 `matchesQuery` 里加标签命中 | 既有规划已要求：`docs/capsule-feature-prompt.md:173` |
| **编辑正文（含"追加"）** | **现在完全没有编辑正文的能力**：仓储只有 `toggleStar` 这一个窄改接口，无 `updateText` | `StashRepository.kt:211-221`；全仓搜 `updateText/appendText` 无命中 |
| **把手长按 = 速记** | **把手完全没有长按处理** | `HistoryFloatContent.kt:122-135` 只传了 `onClick`/`onDoubleClick` |
| **把手手势排除区** | **把手一个排除区都没有**，代码注释自己承认会被系统返回手势抢走 | `HistoryFloatContent.kt:89-90,125`；全仓 `setSystemGestureExclusionRects` 只在面板/边缘捕获里出现 |
| **图标行动作改造** | 主状态（完成/星标）+ 复制 + ⋮ 三个内联 + 溢出菜单 | `HistoryEntryCards.kt` 的 `actions` lambda |
| **时间轴槽（vline + node + 左侧分组）** | 视觉结构改造 | `HistoryPanelScreen.kt` |
| **面板内 FAB + 内联输入条** | 复用 `activatePanelInputFocus()` 抢焦点 | 机制已有，UI 是新写 |
| **存下预览 peek** | 独立小窗，必须 `FLAG_NOT_TOUCHABLE` | 无先例，但简单 |
| **空状态（3 种）** | 全空 / 筛不出 / 搜不到 | 新写 |
| **撤销栈** | 存下 / 删除 / 完成 / 改标签 | 新写，仅内存 |
| **动效剩余部分** | 页签指示片、内容换页、错开淡入、脉冲、flash、触觉 | 见待补 §6 |
| **息屏隐藏** | 把手**没有**息屏处理（对照 `ClipboardFloatService.kt:876-891` 有） | `HistoryFloatService` 无 SCREEN_OFF |

---

## 4. 数据层：两个必须知道的坑

### 4.1 `encodeDefaults = false`（默认值），所以加字段会丢

```kotlin
// StashRepository.kt:40
private val json = Json { ignoreUnknownKeys = true }
```
- `ignoreUnknownKeys = true` → 老版本读到新字段会**忽略**；
- `encodeDefaults = false`（kotlinx 默认，此处未显式设置）→ 等于默认值的字段**不落盘**；
- 组合后果：给 `StashEntry` 加 `done: Boolean = false`，`done=true` 会写进 `index.json`，**但老版本下一次重写就会把它抹掉**。
- ⇒ **`done` 与 `tags` 必须独立文件**（计划里的判断成立，现在有了代码依据）。

### 4.2 ⚠️ 解析失败会静默变空表 —— 既有数据丢失隐患（与本功能无关）

```kotlin
// StashRepository.kt:401-406
private fun readFromDisk(): List<StashEntry> {
    if (!indexFile.exists()) return emptyList()
    return runCatching { json.decodeFromString<List<StashEntry>>(indexFile.readText()) }
        .getOrDefault(emptyList())        // ← 解析失败 = 空列表
}
```
读失败返回 `emptyList()`，而所有写路径都是「读 → 改 → **整表写回**」。**一次解析失败 + 任意一次写入 = 整个暂存被空表覆盖。**
这不是本功能引入的，但本功能会显著增加写入频率。**建议顺手加一层保护**（解析失败时中止写入，而不是写入空表）。

### 4.3 新建独立文件的照抄模板

`app/src/main/java/com/slideindex/app/search/SearchHistoryRepository.kt`（129 行，最干净）—— 子任务已给出 15 步清单，核心是：
`CrossProcessMutex(context, file)`（**per-file，勿与 `index.json` 共用**）→ `withLock { readFromDisk() → 计算 → writeToDisk → _value = next }`，**`withLock` 会自动 `notifyChanged`，不要再手动调**；`init` 里 `registerListener(context, file) { reloadFromDisk() }`。

---

## 5. 把手与 demo 的形态差异（要决定改哪边）

| | 真机现状（`HistoryFloatContent.kt:60-167`） | demo |
| :- | :- | :- |
| 尺寸 | **32dp 宽 × 96dp 高**（激活时 48 × 108） | 9 × 28 |
| 贴边 | `RoundedCornerShape(topStart=22, bottomStart=22)` —— **只圆左侧，右边贴边** | 距边 12px |
| 内部 | 一条 **4 × 24dp** 竖条，距左 8dp，alpha 0.09 → 0.22 | 整条就是 9×28 |
| 手势 | 点 = 开面板；左滑 > **-20px** = 开面板；纵向拖 = 移动；双击 = 开面板 | 点 / 拖内 = 开面板；长按 = 速记 |

**结论：真机把手比 demo 画的"更大更明显"，而且本来就是贴边的。** 所以：
- 用户对 demo"指示条没贴边"的批评是对的 —— **真机反而是对的，demo 画错了**；
- demo 的 48dp 命中区主张在真机上**已经满足**（32×96 已经够）；
- 真正要新增的是**长按**和**排除区**，形态本身不需要改小。

---

## 6. 面板与卡片：逐项难度（勘察完毕）

### 6.1 现状要点

| 项 | 现状 | 证据 |
| :- | :- | :- |
| 宽度 | **`windowInfo.containerSize.width / 2f`**（不是 `windowSize`） | `HistoryPanelUi.kt:64-71`；用于 `HistoryPanelScreen.kt:205,232` |
| 圆角 | **14dp，且只圆内侧**（`gravityEnd` 决定左侧还是右侧） | `HistoryPanelScreen.kt:217-221` |
| 卡片圆角 | 16dp | `HistoryEntryCardParts.kt:255,262` |
| 页签 | miuix `MiuixTabRowWithContour`，**字节码层面已确认库内有 `Animatable<Float> $indicatorOffset`**（指示片位移是库内动画） | `HistoryPanelScreen.kt:358-366`；`ui/miuix/MiuixTabRow.kt:36-41` |
| 内容换页 | **已有** `HorizontalPager` + `animateScrollToPage` | `HistoryPanelScreen.kt:140-143,263-267,362` |
| 列表 | 两个 tab 各一个 `LazyColumn` + `items(key = { it.id })` **整卡渲染** | `HistoryPanelScreen.kt:437-451`（暂存）、`:603-621`（剪贴板） |
| 卡片骨架 | 时间在卡内首行 → 头部动作 → 内容 → `HorizontalDivider` → 操作行 | `HistoryEntryCardParts.kt:288-292,293-296,297,298,299-304` |
| 操作行 | **32dp 命中 / 20dp 字形**；`actions` 是 `RowScope` lambda | `HistoryEntryCardParts.kt:252,310-325` |
| 星标位置 | 在**头部**，不在操作行（暂存卡） | `HistoryEntryCards.kt:374-385` |
| 配色 | **只有 3 个语义色**；`starred` 是唯一布尔；剪贴板卡硬编码 `starred = false` | `HistoryPanelColors.kt:11-41`；`HistoryEntryCards.kt:140` |
| `stickyHeader` | **全仓零命中，零先例** | grep 确认 |
| 「今天/昨天/更早」 | **无字符串资源，无分组逻辑，从零加** | grep 确认 |

### 6.2 六条改动的难度

| # | 改动 | 判定 | 落点 | 量 |
| :- | :- | :- | :- | :- |
| ① | 头部去标题 → 搜索框 + 条数 + 关闭；页签改名；加 chip 行 | 改参数 + 改结构 | `HistoryPanelScreen.kt:322-349`（标题行）→ 常驻搜索框；`:350-357` 搜索条由展开式改常驻；chip 插在 `:358-366` 之后、`:367` 之前；文案 4 套 locale（`values-zh/strings.xml:3096-3097` 等）；chip 状态加进 `HistoryPanelViewModel.kt`（现仅 `:38-45` 三个 StateFlow） | **中** |
| ② | 页签滑动指示片 + 内容换页 | **几乎已具备** | 指示片在 miuix 库内；换页在 `HorizontalPager`。除非要自绘指示片，否则零改动 | **小** |
| ③ | 时间移出卡片 + 左侧时间轴槽（竖线/节点/分组在左） | **真结构改动** | 时间要从 `HistoryEntryCardParts.kt:288-292` 移出 → 壳签名 `:246-253` 的 `createdAtEpochMs` 改由外部消费；列表项 `HistoryPanelScreen.kt:451-500`/`:617-647` 要包成 `Row{ 时间槽; 卡片 }` + 按日分组（现有代码无此逻辑）；sticky 无先例；壳内 `spacedBy(8.dp)`（`:279-282`）随之重排 | **大** |
| ④ | 追加块 + 完成态（划掉 + 变暗） | **真结构改动，且要先建数据层** | 数据不存在（`StashEntry.kt:15-27`）→ 走独立 `tags.json`/`done.json`；卡片落点 `HistoryEntryCards.kt:387-455`、`HistoryPanelScreen.kt:451-500`；划掉样式加在 `HistoryContentBlockView.kt:49-57`（TEXT 分支）；变暗要扩 `HistoryPanelColors.kt:33-40`（现仅 `starred` 一个布尔） | **大** |
| ⑤ | 操作行改纯图标：主状态 + 复制 + ⋮ | 一半参数一半结构 | 尺寸是纯参数：`HistoryEntryCardParts.kt:310-325`（32/20 → 48/22）；结构改"哪个动作在行内"：暂存 `HistoryEntryCards.kt:456-512`、剪贴板 `:195-266`（要把「存到暂存夹」让给 ⋮）；**星标要从头部 `:374-385` 搬到操作行**；主状态动作依赖 ④ 的 done 数据 | **中** |
| ⑥ | FAB（＋）+ 就地输入条 | 改结构 | 插入点 `HistoryPanelScreen.kt:262-376` 的 `Box`，在 `:370-375` SnackbarHost 旁；底部已留 `HistoryListFooterPadding = 64.dp`（`HistoryPanelUi.kt:62`，`:501-503`）；写入需新的 VM 方法（现只有搜索/展开/分页）；输入条动画可仿 `ui/miuix/MiuixExpandableSearch.kt:144-165` | **中**（若接语音/IME 避让 → 大） |

**③④ 是仅有的两个"真结构"改动，且都要先建数据层；⑤ 有一半依赖 ④。** 与计划里 P3 = 3–4 天同量级。

### 6.3 本次勘察修掉的两处**我自己的错误**

1. ❌ **「50% 宽是 `docs/ui-guidelines.md` 的明确规范」—— 查无此条。** 通读该文件 98 行，grep 不到 50% / 面板 / 宽度。⇒ **改宽度没有成文规范挡着**，是产品决定，不是违规。计划文档已勘误。
2. ❌ **`OverlayPanelEnterAnimation.kt` / `OverlayPanelEnterAnimator.kt` 与收纳侧栏无关** —— 它们服务边缘手势面板（`EdgeGestureOverlayView.kt:86`）。侧栏走的是 `OverlaySidePanelHost.kt:207-232` 的 `AnimatedVisibility`。计划文档已勘误。

### 6.4 本节的未核实项（实施前要验）

- miuix 指示片**是否真的按选中索引滑动**：字节码只确认存在 `Animatable<Float> $indicatorOffset`，**没反编译实现体** ⇒ 强推测，别当成已达标
- `windowInfo.containerSize` 在分屏/折叠/横屏下的实际取值未验证（也可能有既有的分屏过窄 bug）
- 时间轴槽的竖线与圆点在**每个 lazy item 边界处是否连续**（无 1px 断口）—— 无法静态判断，计划里也只写了"要试"
- material3 `DropdownMenu` 在 `TYPE_APPLICATION_OVERLAY` + 非 focusable 窗里的关闭与层级行为未核实

---

## 7. 动效能力对照（勘察完毕）：**8 条里 0 条"做不到"**

| # | 动效 | 判定 | 依据 |
| :- | :- | :- | :- |
| 1 | 把手拖动 → 面板跟手拉出 | **需新写但可行** | 现状只累计 `totalX`、过 `-20f` 才开面板（`HistoryFloatContent.kt:87-121,170`）。**"跟手 + 过半定住 + 回弹"的状态机有现成实现可抄**：`overlay/backpanel/BackPanelController.kt:201-259`（onMove/onUp + `saturate()` progress + COMMITTED/CANCELLED）、`ui/miuix/bottombar/animation/DampedDragAnimation.kt:38-135`（VelocityTracker + 7 个 Animatable + spring）。**难点只在跨窗传进度**（见下） |
| 2 | 面板上右拖跟手收回 | **需新写但可行** | 无先例（`OverlaySidePanelHost.kt:215-232` 只能程序化 show/dismiss；`OverlayFullScreenPanelHost.kt:102-116 setDragHidden` 只是拖拽期间 INVISIBLE+NOT_TOUCHABLE）。机制同 #1 |
| 3 | 面板从右缘滑出/滑入 | **现有设施直接支持** | `OverlaySidePanelHost.kt:215-224`（`slideInHorizontally(spring(0.8f,300f)) + fadeIn(250)`，exit 对称） |
| 4 | 页签滑动指示片 + 内容横向换页 | **现有设施直接支持**（拼现成件，调数值） | 指示片模式：`ui/MainBottomNav.kt:180-183,357-361,204-214 drawNavItemCapsule`；换页：`ui/navigation/MainNavHost.kt:607-657`（alpha + translationX + scale 四个 `animateFloatAsState`）。**位移常量 `MainNavHost.kt:97 MainTabSwitchSlideOffset = 28.dp` → 改成 16.dp 即可** |
| 5 | 列表条目错开淡入 | **有现成可复用件** | `ui/OnboardingScreen.kt:852-908 StaggeredEntrance(delayMillis, initialOffsetY=24.dp, initialScale=0.92f)`（三个 Animatable，alpha tween 420ms + offset/scale spring）。⚠️ 它是 `private fun`，跨文件复用要提权或复制 |
| 6 | 存下 → 把手轻脉冲 + 浮出预览 1.2s | **需新写但可行，有近乎逐条对应的先例** | 脉冲：`overlay/FloatBallBuiltinAnimRenderer.kt:87-103 drawPulse`（`0.88+0.12*sin(t/800)`）、把手自身激活态 `HistoryFloatContent.kt:69-80`。<br>**peek 几乎就是现成的**：`overlay/CNoticePeekBannerOverlayWindow.kt:181-195`（独立 `WRAP_CONTENT` + `FLAG_NOT_TOUCHABLE` 小窗）、`:245-292`（fadeIn + slideIn 10dp + scaleIn 0.92 + 靠球 `transformOrigin`）、定时消失 `CNoticeOverlayWindow.kt:1286` 常量 `:255 PEEK_BANNER_DURATION_MS = 3_000L` → **改 1_200L 即可**。缺口：需要"存下一条"的事件源；现 peek 只渲染纯文本（`PEEK_BANNER_MAX_CHARS = 80`） |
| 7 | 新增条目在列表里闪一下 | **需新写但可行** | 无现成实现。最接近的定时闪一下：`overlay/pickresult/PickResultPanelChrome.kt:646-662`（`justCopied` + `LaunchedEffect{ delay(750); reset }`）+ `:703-710`（`animateColorAsState` + `animateFloatAsState(1.05f)`），整套可照搬 |
| 8 | 触觉（长按 / 确认 / 过半阈值） | **现有设施直接支持** | `util/HapticHelper.kt`（`longThreshold:21`、`confirmLaunch:36`、三档强度 `feedbackConstant:93-116`、开关齐备）；Compose 侧 `LocalHapticFeedback` + **`HapticFeedbackType.GestureThresholdActivate` 已有"过半阈值"先例**（`ui/searchengine/SearchEngineSortableGrid.kt:315`）。⚠️ `HapticHelper` 方法签名要 `(View, AppSettings)` |

### 7.1 跨窗进度：没有先例，但"几何跟随"有

- **明确没有**任何"两个 window 共享一个动画进度值"的实现。
- **但有相邻机制可参照**：
  - 共享 Compose `MutableState`（窗 A 写、窗 B 读渲染）：`overlay/FloatingPointerSession.kt:379` + 写方 `FloatingPointerHoverSelectController.kt:311,340` + 读方 `FloatingPointerDisplay.kt:284-316`
  - **手指按下期间由另一窗口坐标逐帧重定位**：`overlay/FloatingPointerWindowLifecycle.kt:401-422,427-461`（每帧 `params.x/y = ...; wm.updateViewLayout`）
  - 跨窗交接标志：`overlay/EdgeContinuedOverlayHandoff.kt:6-38`
- **架构提示**：`overlay/compositor/OverlayCompositor.kt:16-19` 存在的目的就是「把多层视觉塞进同一个 window，从而回避跨窗同步」。⇒ **这从架构意图上支持方案 A（合并成同窗）**。
- 若走 B：把手窗逐帧把进度写进一个共享 `MutableState`，面板窗订阅即可 —— **有几何跟随的先例，不是从零**。

### 7.2 ⚠️ 又一处必须更正我原来的判断

计划 §1 里我写「返回手势避让 → 复用 `OverlayPanelSystemGestureExclusion`（只排除把手自己 48×48）」。**这个工具做不了这件事**：

```kotlin
// overlay/OverlayPanelSystemGestureExclusion.kt
private const val BACK_EDGE_DP = 48f                     // :16
fun attach(view: View, excludeLeftBackEdge: Boolean = true)   // :19 唯一公开 API
private fun updateExclusionRects(view: View)             // :46 private，外部调不到
```
1. **只构造左侧 48dp 矩形 + 底部导航条矩形**（:64-71），**根本没有右侧边缘的排除矩形** —— 而把手在右缘；
2. **移动后没有重设入口**：重算只在 layout change / attach 时触发；把手靠 window 的 `params.y` 移动，view 自身 layout 不变 → `OnLayoutChangeListener` 不触发 → rects 不更新（`EdgeTouchCaptureView.kt:41-68` 有显式重算路径，可作参照）；
3. 重复 `attach()` 会**叠加** `OnLayoutChangeListener`（只有 detach 才移除）。

⇒ **把手的排除区需要新写**（或改造这个工具使其支持右缘 + 可手动刷新）。这是 §3 里"把手排除区"那条的实际工作量来源。

### 7.3 本节未核实项

- `OverlayLayer.zIndex/wmBand` **grep 不到任何读取点**，无法确认层级除 `OverlayZOrderCoordinator` 的 remove/addView 外还有别的机制
- 「无跨窗共享进度」的结论基于关键词 grep + 候选文件通读；`FloatBallDragSession.kt`、`FloatBallGifDragSnapshot.kt` 未读实现，若有逐帧回调式跨窗动画则结论需修正
- 「移动后 exclusion rect 失效」是"本文件无重算入口 + view layout 不触发"的推断，框架是否随 `params.x` 自动换算**未从 framework 源码确认，需真机实测**
- 未读完：`OverlaySidePanelHost.kt:270-342`、`OverlayFullScreenPanelHost.kt:141-231`、`HistoryPanelScreen.kt:355-685`（因此**不能确定 Stash/Clipboard 页签行现在有没有指示片**；另一份勘察从字节码看到 miuix 库内有 `Animatable<Float> $indicatorOffset`，两个结论需真机对齐）
- `HistoryFloatContent.kt` 的把手主体、`HistoryEntryDragHelper.kt` 未逐行确认

---

## 8. 修正后的工期

原估 **12–15 人日**。扣掉 §2 里已存在的一批（搜索、返回键、页签、焦点、z-order、位置持久化、全屏隐藏、玻璃），并考虑 §3 里新发现的"编辑正文完全不存在"、以及 §7 里 4 条动效已有现成件可抄：

| 期 | 内容 | 原估 | 修正 | 变化原因 |
| :- | :- | :- | :- | :- |
| P1 | 数据层：tags / done 独立文件 + **编辑正文接口** + 撤销 + 标签进搜索 +（顺手）解析失败保护 | 1–1.5 | **1.5–2** | ＋"编辑正文"这条真空白（§3） |
| P2 | 把手：长按=速记 + **手势排除区（要新写）** + 息屏隐藏 | 1.5–2 | **1.5–2** | 形态不用改（§5），但排除区工具不能直接用（§7.2） |
| P3 | 面板结构：图标行 + ⋮ 菜单 + 时间轴槽 + FAB/输入条 + 空状态 | 3–4 | **2.5–3.5** | −搜索行/页签已有（§2）；其中③④是仅有的真结构改动（§6.2） |
| P4 | 动效：页签指示片 + 换页 + 错开 + peek + 脉冲 + flash + 触觉（**不含跟手**） | 2–2.5 | **1–1.5** | −3 条直接支持、1 条有现成件（§7） |
| P5 | 输入面统一 + 撤销接线 + 提醒可见 | 1.5–2 | **1–1.5** | −输入焦点机制已有 |
| P6 | 真机适配与联调 | 2–3 | **2–3** | 不变 |
| **A（可选，独立）** | **把手与面板合并成同窗 → 真·跟手拉出** | — | **2–3**，风险独立 | 跨窗进度无先例（§7.1）；`OverlayCompositor.kt:16-19` 的架构意图反而支持合并 |

**不含 A：≈9.5–13.5 人日。含 A：≈11.5–16.5 人日。**

---

## 9. 据此需要你拍的板（更新）

1. **跟手拉出**：先按现状（点/左滑 → 面板弹簧滑入，**已经存在**）交付、把"合并成同窗"单独排一期（含 A ≈+2–3 天）？还是这轮就要 A？
2. **面板宽度**：现在是 `windowInfo.containerSize.width / 2f`，demo 是 78%。
   > ⚠️ **我原先说"50% 是 `docs/ui-guidelines.md` 的明确规范"是错的** —— 通读该文件查无此条。**所以改宽度没有规范挡着**，纯粹是产品决定。我仍建议不改，但理由改成"半屏够用、改了两页签的排版都要重调"。
3. **把手形态**：真机现状（32×96、**本来就贴边**、内嵌 4×24 竖条、alpha 0.09→0.22）比 demo 好，**建议保留真机、让 demo 反过来对齐它**。
4. **语音**：建议只放入口占位（三个输入面都要麦克风，需 3 项 manifest 声明）。
5. **`readFromDisk` 空表隐患**：要不要本期顺手修？我建议要 —— **它比本功能本身更危险**（解析失败 + 任意一次写入 = 整个暂存被清空）。
6. **`stickyHeader` 零先例**：分组标签要不要吸顶？不吸顶风险小很多（§6.4）。
7. **时间轴槽的连续性**：竖线/圆点在懒加载条目边界处能否不断口，**要真机试**。要不要为了降低风险改用"分组头 + 卡片左侧色条"这类不跨 item 的画法？

---

## 10. P0 交付物清单

| 文件 | 内容 |
| :- | :- |
| `docs/capsule-refactor-p0-findings.md`（本文） | 10 节：窗口结构结论、已有资产修正、真空白、数据层两个坑、把手形态差异、面板与卡片逐项难度、动效能力对照（8 条）、修正工期、待拍板 |
| `docs/capsule-refactor-plan.md` | 施工计划（已勘误 2 处：ui-guidelines 的 50% 规范不存在；`OverlayPanelEnterAnimation` 与侧栏无关） |
| `ui_demo_capsule.2050.html` | 设计基线冻结副本 |
