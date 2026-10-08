# 闪念 · 按 UI demo 重构施工计划

> 设计基线：`ui_demo_capsule.2050.html`（冻结副本，SHA256 与 `ui_demo_capsule.html` 一致）
> 可交互版：`ui_demo_capsule.html`
> 相关文档：`docs/capsule-tag-bar-analysis.md`（§12 全系列结论）、`docs/capsule-feature-prompt.md`

---

### 0.4 P3b 剩余部分：勘察后发现的问题（**下次先读这里，别重复造**）

1. ✅ **空状态早就有了** —— `HistoryPanelScreen.kt:440-449`：`allEntries.isEmpty()` → `stash_empty`，否则 `stash_search_empty`；剪贴板页也有 `clipboard_empty`。
   ⇒ 计划里的「空状态（3 种）」**只剩"筛不出来"这一种缺**（设计稿的第三种：引导文案 + 一个出口按钮）。**不要重建前两种。**
2. ✅ **「按标签筛空 → 落到搜索为空文案」这条已查清，比原记录轻得多 —— 不必再找"最小修法"**：
   `stash_search_empty` 的实际文案是「没有匹配的暂存内容 / No matching stash items」，
   **它本身不专指搜索**，不会像原先写的那样把用户推去清搜索框。
   真正缺的只有「筛不出来」那一类**带出口**的空状态，也就是第 3 条，属于 P3b 的「空状态（3 种）」，没有单独可做的最小修法。
3. ✅ **空状态三选一已做完**（第二轮，含 §0.4 那种"带出口"的筛空）：见 `HistoryEmptyState.kt` 与 §0.7。
4. ✅ **时间轴槽已落地**（P3b 里唯一"真结构"改动）—— 实现要点见下面 **§0.6**。

### 0.7 卡片重排 / 操作行 / 空状态（已落地）实现要点与坑

- **完成态是两处配合，别只做一处**：卡片底色走 `HistoryPanelColors.cardBackground(starred, done = true)`
  （P3a 就有），正文/图标走 `HistoryEntryCardShell(done = true)` 的 `Modifier.alpha(0.62)`。
  两处都淡会灰得过头；只淡底色则正文不跟着变暗。
- **`isTodo` 规则**（设计稿 `isTodo()`）：主状态动作在「待办」条目上是**完成**、其它条目上是**星标**；
  已经完成的条目**即使后来摘掉「待办」标签也保留完成按钮**，否则没法取消完成。
  ⚠️ 判定键是 `StashMetaRepository.TODO_TAG_NAME`（硬编码中文「待办」）—— 用户删掉/改名这枚标签后
  新条目就没有「完成」入口了（已完成的仍保留）。见 §0.3 待确认第 4 条。
- **来源（`.foot` 的 `from` chip）必须新增数据**：`StashMetaStore.sources`，**存字符串键不存 enum** ——
  老版本遇到不认识的 enum 值会在解码**整个文件**时抛异常，而 `StashMetaRepository` 读失败会
  **禁用所有写入**（见 `writeToDisk` 的保护），于是一个新来源键就能让旧版本的标签/完成态全写不进去。
  写入口在 `StashCoordinator.addText/addImage/addRich(source = …)`（形参有默认值，既有调用点不用改），
  目前接了三条：剪贴板→闪念、取词面板→闪念、图片编辑→闪念。`StashMetaStoreTest` 覆盖了这几种兼容情况。
- **顺手补上的 P1 缺口**：`StashMetaRepository.forget` / `pruneOrphans` 之前**写了但从来没被调用**，
  元数据会跟着删除/200 条裁剪一直攒孤儿。现在：`StashRepository.delete/clearAll` 调 `forget(id)`，
  面板 ViewModel `init` 按有效 id 收敛一次（**只在列表非空时做** —— 空列表可能只是还没加载，
  照它 prune 会把用户元数据抹光）。
- **⋮ 菜单的分隔线**：`HistoryCardMenuAction.destructive = true` 的那一项前面自动排分隔线
  （设计稿 `['sep']`），绑在"危险动作"语义上而不是绑在"最后一项"上。
- **追加块**：设计稿一条只挂一段追加，我们数据层存的是列表，所以**一条一段**各渲染一块
  （比"只显示最后一段"信息更全，是刻意偏差）。
- **foot 行用 `FlowRow`**（设计稿 `flex-wrap: wrap`），标签多了会换行，别用 `Row`。
- **顺手修掉一个行为 bug**：`filteredStashEntries` 以前是"搜索 ∧ 标签筛选"叠加，而设计稿
  `visible()` 与计划 §2 都要求**搜索时忽略标签筛选** —— 已改成搜索时只看查询（标签名照样参与命中），
  否则用户很容易"搜不到自己刚存的那条"。

### 0.8 FAB + 就地输入条（已落地）实现要点与坑

- **位置用一个 Column 串起来，不要用绝对偏移**：`FAB → 间隔 → 输入条`。
  设计稿是 `.fab{bottom:92px}` / `.fab.open{bottom:158px}` 两个绝对值，但真机输入条高度会随字号变，
  绝对定位要么量高度、要么在展开动画里穿帮。Column 里让"间隔"在 `92dp`（关）/`48dp`（开）之间切，
  FAB 就永远贴着输入条走（158 = 输入条约 110 + 48，正好对上设计稿）。
- **IME 必须走 `rememberOverlayImeBottomHeight()`**：overlay 窗里 `WindowInsets.ime` 常常是 0
  （设计稿自己的 `.ime` 说明也点了这条）。整个 Column 加 `padding(bottom = imeBottom)` 即可。
- **抢焦点的节奏照抄搜索框**：先让展开动画起来、`delay(180ms)` 再 `requestFocus()`，
  否则 overlay 窗里 IME 常常不弹；同时要把"窗口需要可聚焦"这条路走上
  （`onSearchFocusChanged` 现在收 `searchExpanded || composerOpen`）。
- **存下后要清搜索与标签筛选**：设计稿 `addFromComposer()` 里 `filter = null; query = ''` ——
  不清的话新条目可能正好落在筛选之外，用户会以为没存上。
- ⚠️ **`BasicTextField` 只能用已废弃的 `value/onValueChange` 重载**：本仓库解析到的 foundation
  版本里**没有** `TextFieldState` 那个重载（`rememberTextFieldState` / `TextFieldLineLimits` 有，
  但 `BasicTextField(state = …)` 不存在），overlay 里既有的两处输入也都是老重载。
  已在 `HistoryComposerInput` 上 `@Suppress("DEPRECATION")` 并注明升级后应换 state 版。
- **还没接的两件周边**（都在后续期里）：①存下后的「撤销」toast（P5 撤销栈）；
  ②新条目那一下 flash 高亮（P4 动效，设计稿 `.item.flash`）。语音按钮按 P6 再放进输入条左侧。

### 0.9 P4 动效（已落地部分）实现要点与坑

- **flash 要用 `Animatable` 而不是 `animateFloatAsState`**：`animateFloatAsState` 只会盯着目标值，
  同一条被复用（滚出再滚回）或 key 变化时不会**从头播一次**。用 `Animatable` 显式
  `snapTo(1f) → animateTo(0f, 1400ms)`，只在 `flash = true` 时播。
  视觉用 `Modifier.border`（2dp 主题色环）+ `Modifier.shadow(ambient/spotColor = primary)`
  （8dp 柔光，**minSdk 31，彩色阴影可用**），不自己画 drawBehind —— 边框/阴影天然不越界、不被卡片圆角裁掉。
- **错开淡入的 `LaunchedEffect` key 必须是 `Unit`，不能是延迟值**：错开窗口结束时参数从
  `index*40` 变回 `0`，key 一变就会**取消**正在跑的动画，那一行永远停在 alpha 0（我踩过）。
  窗口也要有界（`isActive` 变 true 时开 720ms），否则往下滚出来的每一行都会再演一遍进场。
- **换页位移要在 `graphicsLayer {}` 里读翻页比例**：`pagerState.currentPageOffsetFraction`
  作为普通值读进组合，会每帧重组整页（两个页签的整棵列表）。放进 layer 块里读 = 只在绘制阶段，
  只重绘不重组。
- **触觉别直接复用 `confirmLaunch` / `appTick`**：那两个还叠了各自的开关
  （"启动确认触觉" / "手势槽选择触觉"），拿来做面板里的存下/复制语义不对。
  已在 `HapticHelper` 加 `actionConfirm` / `actionTick`：**只受总开关与强度控制**，语义与
  `preview` 一致但名字对得上。把手长按仍用 `longThreshold`（它本来就属于"手势触发"那一类）。
- **把手脉冲需要一个跨窗事件源**：把手窗与面板窗是两个 window，面板里存下的动作传不到把手。
  新 `HistorySaveSignal`（同进程静态量 + Compose 状态）承担这件事，读它即订阅。
  ⚠️ 脉冲的状态要用 `remember { mutableIntStateOf(saveCount) }` 记初值，否则**进程里之前存过的东西
  会让把手在启动时凭空脉冲一下**。`lastSavedText` 已经带着（peek 只差那个小窗）。
- **页签指示片不用做**：`MiuixTabRowWithContour` 库内自带 `Animatable<Float> $indicatorOffset`
  （P0 §6.1 从字节码确认），本轮只在内容侧补了换页动效。

### 0.10 跟手拉出（已落地）：**做了方案 B，没做 §0.1 的合并同窗**

**结论先说**：跟手拉出已经能用，但走的是 P0 §7.1 的**方案 B（两窗共享进度）**，
而不是本文 §0.1 原本写的**方案 A（把手与面板合并成同窗）**。

**为什么改方案**（这是一次有意的取舍，不是偷懒）：

| | A 合并同窗（§0.1 原文） | **B 共享进度（实际采用）** |
| :- | :- | :- |
| 要动什么 | 把手窗的尺寸/坐标系、把手 Y 从 `params.y` 搬进 Compose 布局；面板的返回键拦截、输入焦点、玻璃模糊、与悬浮球的 z-order 协同**整套搬过来** | 只在两个窗之间共享一个进度值；两个窗各自的结构**一行不动** |
| 手感 | 同窗同帧，最理想 | 两窗各自合成，极端情况可能差一帧（撕裂） |
| 风险面 | 动的是**最常驻的那个窗**（把手），出问题用户连面板都打不开 | 出问题最坏是"跟手不跟"，点击/拖过阈值打开的老路径**完全没动** |
| 依据 | §0.1 | P0 §7.1 明确写了"共享 Compose MutableState（窗 A 写、窗 B 读渲染）有先例：`FloatingPointerSession` + `FloatingPointerDisplay`" |

**实现要点**：

- `HistoryPanelReveal`（新）：`open` / `dragging` / `dragProgress` / `dragSession` / `retractPending`
  五个字段描述"面板该在哪、进度由谁定"。把手窗写、面板窗读。
- `HistoryPanelRevealDriver`（新）：面板侧一个 `Animatable<Float>` ——
  **拖动时 `snapTo(手指进度)`（这就是跟手）**，松手/点击时 `animateTo(目标)`（弹簧 0.8 / 300，
  与宿主原来的滑入动画同参数）。它同时负责收尾：归位完成 → 清 `dragSession`；
  收回完成 → **先叫宿主关窗再清** `dragSession`（顺序反了会看到面板"啪"地跳回屏幕再滑出去）。
- 面板侧只做一件事：`graphicsLayer { translationX = (1 - 进度) × 面板宽 }`，
  **且只在 `dragSession` 时生效** —— 点击打开那条路继续交给宿主的滑入动画（零回归风险）。
- 宿主侧加了两样：`dragReveal: () -> Boolean`（拖动发起的打开**不能**再叠一次滑入动画，
  否则位移翻倍）与 `setTouchable(false)`（拖动期间面板窗"看得见但不吃触摸"，
  否则 MATCH_PARENT 的它会把后续 MOVE 从把手手里抢走）。
- 把手侧：**前 8dp 锁主方向**（横向=拉出、纵向=挪位置，设计稿注释同款）、
  进度 = `-总位移 / 面板宽`、**过半轻震一次**；松手过半 → 归位，否则弹回。
  面板已经开着时不再进入跟手（`isShowing` 直接返回 false）。

**已知偏差 / 没做的**：

- ⚠️ **两窗可能差一帧**。真机如果看着"面板比手指慢半拍"，这就是 B 方案的固有代价；
  要彻底消除只能回到 A（2–3 人日，见上表）。
- 设计稿那层 **20% 黑遮罩没做**：面板窗在把手窗**上面**，全屏遮罩会把更低层的把手一起压暗
  （设计稿里把手 z-index 更高所以不会）。要做就得给遮罩挖个把手大小的洞，收益不划算。
- **右拖收回只做了"取消拖动时弹回"**：面板**已经打开后**再右拖收回需要和 `HorizontalPager`
  的横滑、`LazyColumn` 的竖滚抢手势，风险明显高于收益，留给真机手感确认后再定。
- 拖动期间面板窗是 `FLAG_NOT_TOUCHABLE`，所以**拖动中途点不了面板**（手指还按着，本来也点不了）。

### 0.11 输入槽 + peek（已落地）实现要点与坑

- **两个都是独立小窗，且都不在无障碍服务那边**：peek 与输入槽贴在把手上，用的是
  `HistoryFloatService` 自己的 `WindowManager`（与把手同源），不依赖无障碍服务 ——
  这样"没开无障碍也能长按记一条"。面板反过来走无障碍服务的 WM（为了 z-order），两者互不影响。
- **peek 必须 `FLAG_NOT_TOUCHABLE`**：它正好压在把手旁边，能点的话会把把手/面板的点击吃掉
  （设计稿自己也强调 `.peek { pointer-events: none }`）。位置用 `onSizeChanged` 回报尺寸后再
  `params.x/y`：右缘锚在把手左侧 48+10dp 处，纵向与把手居中。
- **输入槽要抢焦点，所以要"被动壳 → 激活"两态**：平时
  `NOT_FOCUSABLE | NOT_TOUCHABLE + GONE`，打开时清掉这两个 flag、`isFocusable=true`、
  `requestFocus()`，收起时再退回（同 `FloatBallPickResultPanel` 的做法）。
  ⚠️ 焦点一变就要 `OverlayViewBackHandler.refresh()` —— 那个类的注释写了"窗口变成可聚焦后要重试注册返回键"。
- **从右缘"长出来"靠的是窗口宽度动画 + 尺寸回调重算 x**：窗口是 `WRAP_CONTENT`，
  Compose 里把内容宽度从 72dp 动画到 300dp，`onSizeChanged` 每帧回调 → 重算 `params.x` 保持右缘不动。
  不能偷懒用一个固定 300dp 的窗口 + 透明左边，那段透明区域**照样吃触摸**（会挡住底下的 App）。
- **存下后的反馈是"信号驱动"，输入槽自己不管**：`StashCoordinator` 存成功后 `HistorySaveSignal.notifySaved()`
  → 把手脉冲（Compose 读状态）+ peek（Service 的普通回调）。所以**不管从哪儿存下**（输入槽 / 面板输入条 /
  剪贴板暂存 / 取词暂存）都会有一致的反馈。
- ⚠️ 输入槽的位置由 `params.y` 定，**输入法弹起时它不会跟着上移** —— 把手停靠位置偏下时输入槽可能被输入法盖住，
  留给 P7 的 IME 避让处理。

### 0.12 编辑条 + 撤销（已落地）实现要点与坑

- **"撤销栈"实际是单槽**：计划里写的是"仅内存栈"，但设计稿的 `undoFn` 就是一个槽
  （「撤销」永远指"上一步"），`SnackbarHostState` 也只有一个动作按钮。所以实现成
  **提示 + 撤销**：新动作会先 `newestSnackbarData()?.dismiss()` 顶掉上一条。要做真栈得换一种提示形态。
- ⚠️ **miuix 的 `SnackbarHostState` 没有 `currentSnackbarData`**（只有 `newestSnackbarData()` /
  `oldestSnackbarData()` 两个 suspend 方法，另有包内可见的 `currentSnackbars`）——
  想"顶掉上一条"必须用 `newestSnackbarData()?.dismiss()`。`showSnackbar` 返回 `SnackbarResult`，
  `ActionPerformed` 才执行撤销。
- **删除的撤销要真的能恢复**，于是：`delete` **不再立刻删图片文件**（新增
  `StashRepository.restore(entry, index)` 负责放回原位置，图片条目也能恢复），
  孤儿文件交给**启动时**的 `pruneOrphanImages()`：按"还有没有条目引用"列一次目录清掉 ——
  顺带也清掉崩溃路径留下的孤儿。
  ⚠️ 副作用：删掉的图片会活到**下次进程启动**。这是有意的（换撤销能力），代价有界。
- **顺手修掉一个数据保护隐患**：`StashRepository.indexUnreadable` 原先声明在 `init` **之后**，
  Kotlin 按声明顺序初始化，于是 `init` 里 `readFromDiskSync()` 设的标志会被随后的属性初始化
  冲回 `false`。已把声明挪到 `init` 之前（进程启动时那次读盘失败现在也能拒写）。
- **触觉只在一处给**：状态类动作（完成/星标/删除）的触觉放在 `HistoryPanelScreen` 的
  `setDone/toggleStar/deleteEntry` 回调里，卡片里的同名按钮**不再自己震** ——
  否则一次点击会震两下（编辑条与卡片共用同一批回调，这也是把它们集中到 Screen 的原因）。
- **编辑条与输入条互斥**：打开编辑条会收起输入条，并且 FAB 在编辑条开着时不显示；
  列表底部让位取两者高度的较大值（`bottomSheetHeight`）。
- **与设计稿的两处刻意偏差**：①编辑条挂在**面板底部**而不是卡片旁边（面板是 LazyColumn + 输入法抬升，
  跟着卡片定位在滚动/键盘下很容易跑偏）；②「追加」是**单独一个输入框**而不是复用正文框
  （数据层本来就是独立的追加列表，追加块因此永远不覆盖原文）。

### 0.15 真机保真度修复（第十轮）—— 两个"编译得过、真机上静默失效"的坑

装上真机（MEIZU 21 / Android 16）后对着截图逐条核对，发现两类问题。**两个都是"单测和编译都发现不了"的**：

#### ① ⚠️ `StashMetaRepository` 从来没被构造过（元数据层整层是死的）

- 症状：真机上**一个标签都没有**（卡片没有标签 chip、顶部没有筛选 chip 行、来源也永远空）。
- 根因：`StashMetaRepository` 是**自注册单例**（`init` 里把自己挂到 `StashAccess.metaRepository`），
  但**没有任何一处注入它** —— Hilt 只在有人要它时才构造它，于是它永远不存在，
  `StashAccess.metaRepository` 恒为 null；真机上连 `files/stash_meta.json` 都没生成过。
  所有 `metaRepository?.xxx()` 都是优雅的空操作，所以**没有任何报错**。
- 修法：把它变成 `StashRepository` 的**构造依赖**（`StashRepository` 由 `AppDependencies` 注入，
  而 `AppDependencies` 是进程启动就会建的），Hilt 会先造形参 ⇒ 元数据层随闪念仓储一起上线；
  `forgetMeta` 也改成直接用形参，不再靠静态查找。
- 教训（写进待确认）：**"自注册单例 + 没人注入 + 全部调用点都是 `?.`" = 运行时静默失效**。
  以后新增数据层仓库，必须有一个"进程启动就会被构造"的入口，且真机上要**看一眼文件是否生成**。

#### ② ⚠️ 面板宽度不能读 `LocalWindowInfo.containerSize`

- 症状：侧栏变成**满屏宽**（设计稿是 78%），`CenterEnd` 失效。
- 根因（日志实测）：`containerSize` 返回 **2340 x 1080**，而窗口实际是 1080 x 2340
  （旋转过的/陈旧的显示尺寸）⇒ `78% × 2340 = 1825px` 比窗口还宽，被裁成满屏。
- 修法：面板侧改用 `BoxWithConstraints` 的 **`maxWidth`**（真实布局约束）算
  `panelWidthOf(maxWidth)`；把手侧（48dp 小窗，读不到屏幕宽度）改用
  `WindowManager.currentWindowMetrics`（随旋转更新），不用 `resources.displayMetrics`。

#### ③ 这一轮补齐的设计稿保真度

| 项 | 之前 | 现在（对齐设计稿） |
| :- | :- | :- |
| **卡片分档** | 所有卡片都是实心白卡 | **今天**＝实心＋亮边＋投影（`.item.fresh`）· **昨天**＝半透明（`.item.mid`）· **更早**＝**完全透明**（`.item.old`），只剩文字浮在面板上 |
| 卡片圆角 / 内边距 | 16dp / 12dp | **18dp**（`--r-lg`）· **13/14/6dp**（`.box padding`） |
| 正文排版 | miuix body2（14sp） | **13sp / 行高 21sp**（`--f-sm` 12.5px / 1.6） |
| **头部** | 标题「收纳面板」＋图钉＋搜索图标（搜索要点开） | 设计稿 `.srchrow`：**常驻搜索框 ＋ 条数「N 条 · 今天 M」＋ 切边 ＋ 关闭**，**没有标题行** |
| 搜索 hint | 「搜索暂存夹…」 | 「搜索闪念…」（P3a 改名后的漏网之鱼） |

- 搜索框从"可展开"改成常驻后，**焦点即输入态**：`onFocusChanged` 驱动窗口临时可聚焦；
  返回键仍走 `consumeExpandableSearchBack(expanded = 查询非空)`（有内容先清空，无内容交还给宿主关面板）。
- 真机已核对（截图）：标签 chip 行出现（5 个默认标签 + 颜色点）、条数 `12 条 · 今天 2`、
  今天档卡片白底亮边、更早档卡片透明、编辑条 / 提醒 / 麦克风渲染正常、**IME 把编辑条整条抬起来了**。

### 0.16.1 ⚠️ 真机闪退：Compose 的 `padding` 不接受负值

照抄 demo 的 `.acts { margin-left: -13px }` 时我写成了 `Modifier.padding(start = (-13).dp)` ——
真机上 **必崩**：

```
java.lang.IllegalArgumentException: Padding must be non-negative
  at PaddingElement.<init>(Padding.kt:536)
  at HistoryEntryCardPartsKt.HistoryEntryCardShell(HistoryEntryCardParts.kt:410)
  at HistoryEntryCardsKt.HistoryClipboardEntryCard(HistoryEntryCards.kt:144)
```

**CSS 负 margin 的 Compose 等价物**：
- 只是想让元素**画**在正常位置左边一截 → `Modifier.offset(x = -13.dp)`（offset 收负值）；
- ⚠️ 但**整行**一起 offset 会把右端的 ⋮ 也带跑（CSS 的负 margin 是让整行往左**变宽**、右端不动）。
  所以正确做法是只给**行首那几个图标**各自加 `offset(x = -13.dp)`，行尾菜单保持原位
  （`HistoryActsOffsetX = 13.dp`，用的时候取负）。

教训：从 CSS 往 Compose 搬数值时，**`margin`/`gap`/`inset` 这些"盒模型"概念没有一一对应物**，
`padding`/`size`/`width` 全是非负的；负的只有 `offset`（以及 `graphicsLayer` 的 translation）。
### 0.16.2 ~~主动"提精致"~~ **已被用户打回，全部撤销**（保留本节作为记录）

用户原话：**"demo 有的地方很浅实际就是他的设计又不够精致，你也要改的，把我们改的精致些"**
—— 例：面板顶部的搜索框行 / 页签行"浅灰基本看不见，费眼睛"。

demo 那几处是**白底 + 白描边 + 4% 灰**（它是为"玻璃压在花哨背景上"设计的），
在真实浅色面板上确实糊成一片。所以这一轮**有意偏离设计稿**，数值按"看得清但不抢眼"重定
（全部集中在 `HistoryPanelTokens.kt` 的"提精致"那一段，带注释，翻案时一眼能找到）：

| 部位 | demo | 现在（提精致） | 理由 |
| :- | :- | :- | :- |
| 搜索框 | `--btn-bg` 4% + `--btn-bd` 13% | `fieldBg` 5% + `fieldBorder` **14%**，聚焦态 accent 8% | 描边太浅＝看不出来是个输入框 |
| 页签 | 5% 灰片 + hairline，**无轨道** | **分段控件**：`segTrack` 6% + `segBorder` 8% 的轨道，选中片 = **白底 + 3dp 投影**、圆角 8 | 原版选中态几乎看不出 |
| 标签药丸 | 白描边（`--g-border`） | `chipBorder` 10% 灰 | 白压白＝没有边 |
| 卡片描边 | 白描边 | `cardBorder` 8% 灰 | 同上 |
| 小字（时间/条数/未选中页签） | `--sub` + 10.5sp | **`subStrong`**（#545A6E）+ **11sp**（meta 11.5→12sp） | 费眼睛 |
| 时间轴竖线 / 节点 | 14% / 26% | **22% / 34%** | 几乎看不见 |
| 更早那档卡片 | **完全透明** + 灰正文 | **42% 白卡 + 细描边 + 正文用正文色** | 用户觉得"只有前两个有卡片"像没做完 |
| 浮窗（输入条/编辑条/提示条/peek/槽） | `.g` 半透明 | **不透明** `--g-solid` | 半透明会透出下面的列表 |
| 指示片 / 把手 | 距边 16dp / 12dp | **贴边** | 用户明确要求 |

#### 0.16.2.1 撤销时**保留**的几项（都是纯 bug 修复，和"提精致"无关）

- **`alpha` / `graphicsLayer` 的层顺序**（见下，完成态变暗 + 跟手拖出整块面板）。
- **跟手拖出距离**：demo 是 `p = clamp(-dx / 130)`（130px 拉满），原先我错用了"面板宽度 842px"。
- **搜索框点击**：只读态不渲染 `BasicTextField`（它会吃掉点击），点击后再切可编辑 + 抢焦点。
- **毛玻璃关掉时面板用不透明 `--g-solid`**：`[data-glass="off"] .g` 的兜底，是 demo 自己的规则。

#### ⚠️ 同一个坑踩了两次：`alpha` / `graphicsLayer` 只影响**排在它之后**的绘制

Compose 的 `Modifier.alpha` 和 `Modifier.graphicsLayer` 都是"层"（RenderNode）语义：
它们**只包住 modifier 链里排在它们后面的绘制**，排在前面的（`background` / `border` / `shadow`）
**不受影响**。真机上各踩了一次：

1. `.item.done .box { opacity: .62 }` 写成 `background().border().alpha()` → **只有文字淡了**，
   卡片底色和描边一点没变（用户："点完成后颜色没变化"）。
2. 跟手拖出的 `graphicsLayer { translationX }` 写成 `background().border().graphicsLayer{}` →
   **面板底色留在最终位置不动，只有里面的内容跟着手指跑**
   （用户："先出现一个遮罩占住面板最终位置，然后继续拖才是把面板移过去"）。

**规矩**：`alpha` / `offset` / `graphicsLayer` 一律放在 `background` / `border` / `shadow`
**之前**。（`Modifier.offset` 是布局语义，没这个问题；`alpha`/`graphicsLayer` 才会。）
**结论（已更新）**：用户看过实机后**不满意这批改动，要求全部撤销** —— 上面右列**已全部还原成左列（demo 原值）**。
唯一保留的是纯 bug 修复，见下面的 §0.16.2.1。**不要再自作主张提对比度**：demo 的"浅"就是他要的，
真觉得浅他会明确说"再实一点/再深一档"。

**本条修订后的结论**：视觉规格就是 `docs/capsule-ui-spec.md`，**以 demo 为唯一基线** ——
改回去就是把上表右列换回左列。
### 0.16.3 ❌ **已实施又被用户打回，整批回退**：面板窗改 78% 宽 + 拖动移动窗口

> **现状（最新）：§0.16.3 的改造已全部回退** —— 用户看了实机后不满意，要求恢复成"改造前"那一版
> （即"窗口满屏 + 面板内容自己平移 + App 自绘磨砂罩"）。
> **回退范围**（逐条）：
> - `OverlayPanelLayoutParams.stashClipboardSidePanel`：窗口宽度回 **`MATCH_PARENT`**（`width`/`x` 计算删掉；
>   `SIDE_PANEL_WIDTH_FRACTION` 常量留着只作对照，不再使用）
> - `OverlaySidePanelHost`：**停掉** `applyPanelX` 的两处调用（`snapshotFlow{Triple(...)}` 整段删掉、
>   窗口可见前那次定位也删掉）；`applyPanelX` 函数与 `revealProgress` 形参保留（无人调用）
> - `FloatBallStashPanel`：去掉面板打开时的 `sideHost.updateBackgroundBlur(...)`（不再要系统模糊）
> - `HistoryPanelScreen`：面板 Box 从 `fillMaxSize()` 改回 **`fillMaxHeight().width(panelWidthOf(maxWidth))`** +
>   恢复**内容平移** `graphicsLayer{ translationX = offsetPx(...) }`（放在 `background/border` **之前**）+
>   恢复 **`LocalFrostedGlassBackdrop` 自绘磨砂罩** + 恢复 **scrim/scrimAlpha**（放在面板 Box **之前**）+
>   删掉把进度导给宿主窗口的 `snapshotFlow`
> - `HistoryPanelUi.historyPreviewWidthPx`：恢复 **× `PANEL_WIDTH_FRACTION`**
> - `HistoryPanelReveal.windowProgress` 留着（无人使用）
>
> **保留不动**（用户明确要求保留）：`HistoryTimelineGutter.kt` 那批（轴槽垫底 + 今天/昨天/更早 11.5sp/正文色 88% +
> 时间行 11.5sp/正文色 80%/Medium）、提示条滑动消除与复位、tab 分段控件、标签行换行、所有条目都是卡片、
> 输入条/编辑条实心、星标卡顶部白线只在"今天未星标"、快速加标签、剪贴板列表顶部 18dp、搜索框可点。
>
> 回退后已验证：编译 ✅ · `assembleFullDebug` ✅ · 装机 ✅ · **无新崩溃**（`files/crashes` 最新仍是
> `crash_20261007_170028.txt`）· 应用侧**不再有任何窗口开 `blurBehindRadius`**（dumpsys 里那一处属于系统
> 窗口 `windowModeDim`）· ⚠️ 面板窗当时的 `mAttrs` 没能读到（那会儿面板已收起），所以"又变回 `fillxfill`"
> 这一条只从代码路径确认，没有 dumpsys 实证。

---

#### 实施记录（当时已完成并装机验证，现已回退 —— 保留作史料）

**改了这些文件**：

| 文件 | 改动 |
| :- | :- |
| `OverlayPanelLayoutParams.kt` | `stashClipboardSidePanel` 的窗口宽度从 `MATCH_PARENT` 改成**屏宽 × `SIDE_PANEL_WIDTH_FRACTION`(0.78)**，默认 `x` 贴右；新增该常量（与 `HistoryPanelUi.PANEL_WIDTH_FRACTION` 对照） |
| `OverlayFullScreenPanelHost.kt` | 新增 `setRevealOffsetPx(px)`：改 `params.x` + `updateViewLayout`（值没变就返回，拖动每帧都会调） |
| `OverlaySidePanelHost.kt` | `attachHidden` / `show` 都新增 `revealProgress: () -> Float = { 0f }`；新增 `applyPanelX(progress, gravityEnd)`（把进度换算成窗口左边缘坐标）与一次性诊断日志；`attachPanelWindow` 里订阅 `snapshotFlow { Triple(dragReveal, revealProgress, gravityEnd) }`，并在窗口可见前先摆好初始位置 |
| `FloatBallStashPanel.kt` | 三处 `show/attachHidden` 都传 `revealProgress = { HistoryPanelReveal.windowProgress }`；`beginDragReveal` 里显式把 `windowProgress` 置 0（保证第一帧就在屏外）；面板内容里按设置调 `sideHost.updateBackgroundBlur(context, if (panelBlurActive) blurRadiusDp else 0)` |
| `HistoryPanelReveal.kt` | 新增 `windowProgress`（**经弹簧的**进度，驱动窗口；和手指原始值 `dragProgress` 分开） |
| `HistoryPanelScreen.kt` | 删掉面板内容的 `graphicsLayer { translationX }`；删掉自绘磨砂罩 `LocalFrostedGlassBackdrop`（避免和系统模糊双份）；删掉 `scrim` 与 `scrimAlpha`；面板 Box 改 `fillMaxSize()`（窗口已等于面板大小，**不能再乘 78%**）；把 `revealProgress` 用 `snapshotFlow` 导给 `windowProgress` |
| `HistoryPanelUi.kt` | `historyPreviewWidthPx` 不再乘 78%（窗口就是面板宽度，再乘会缩成 61%）；`panelWidthOf` 标注为已废弃 |

**几何映射**：`gravity = TOP|START`，所以 `params.x` 就是窗口左边缘坐标（正数向右，左右侧都不用反号）：

- 贴右：完全拉出 `x = 屏宽 − 窗宽`（1080−842=238）· 完全收起 `x = 屏宽`
- 贴左：完全拉出 `x = 0` · 完全收起 `x = −窗宽`
- 中间线性插值；手指拖 130dp 拉满（`PANEL_REVEAL_DRAG_DP`，进度由把手侧给）

**真机验证**（MEIZU 21 / 安装后 dumpsys + logcat）：

- 面板窗：`mAttrs={(238,0)(842xfill) … blurBehindRadius=80` ✓ 宽度正好 842px=1080×0.78、位置贴右 ✓
- 系统模糊**确实启用**：`FloatBallStashPanel: panel background blur: setting=… nativeActive=true`（改设置会在 true/false 间切换 ✓）
- 无崩溃（`files/crashes` 最新仍是修复前的 `crash_20261007_170028.txt`）

**遗留（都记在这，下次处理）**：

1. **遮罩（`.scrim`）被删了** —— 窗口只有 78% 宽，左侧 22% 的压暗淡不到窗外。要恢复得**另开一个满屏、
   `FLAG_NOT_TOUCHABLE`、只画 scrim 的窗**（或干脆不要遮罩）。
2. **`Modifier.shadow` 外投影被窗口裁掉** —— 面板铺满窗口，投影没有"画在窗外"的余量 ✗ 面板看起来更平。
   要恢复得让窗口比面板略宽（内边距留投影空间），代价是几何/动画都要跟着调。
3. **点击打开**仍是"窗口直接到最终位置 + 内容滑入 280ms"：那 280ms 里窗口空白处会露出**模糊过的**背景
   （不是白块，可接受）；要更干净得把点击打开也改成动窗口。
4. **左手侧**（`gravityEnd=false`）只做了按公式推导（`hiddenX = −窗宽`），**没在真机上验**。
5. `HistoryPanelReveal.offsetPx` / `panelWidthOf` 现在没人用了（保留，避免动到单测）。

**为什么**：闪念面板内的"背景模糊"是 App 自绘的 **RenderEffect 快照**（`LocalFrostedGlassBackdrop`）——
窗口稳定时糊得对，**内容一平移快照就不刷新**，只剩白 tint（用户看到的"雾/白色半透明"）。
而快速启动器/搜索面板那些**看着是实时模糊**的面板，用的是**系统级**模糊
（`FLAG_BLUR_BEHIND` + `setBackgroundBlurRadius`，见 `SearchPanelOverlayWindow` / `CornerGestureController`
/ `HolographicLauncherOverlayController`）—— 合成器**每帧**重算，所以跟着窗口动也是实时糊 ✓。

闪念面板**没启用**系统模糊，唯一原因：**它的窗口是满屏的**（`MATCH_PARENT`）→
系统模糊会糊**整个窗口背后**＝整屏，而面板只占右侧 78%（就是之前那个"全屏一层白"）。

**改造清单**（按顺序做，每步都能单独编译验证）：

1. `OverlayPanelLayoutParams.stashClipboardSidePanel`：`MATCH_PARENT` → **宽度 = 屏宽 × 0.78**、`gravity = END or TOP`。
2. 宿主加一个 API：`fun setRevealOffsetPx(px: Int)` → `params.x = px`（END 重力下 x 为正 = 往右推出屏）。
3. `HistoryPanelReveal`：把"面板内容平移"（`HistoryPanelScreen` 里的 `graphicsLayer{translationX}`）删掉，
   改成面板侧把进度回调给宿主 → `setRevealOffsetPx(((1 - p) * panelWidthPx).toInt())`。
   （把手窗的拖动进度已经算好了，只需转发。）
4. 打开系统模糊：面板宿主调用 `updateBackgroundBlur(context, blurRadiusDp)`（API 已存在，
   `OverlayFullScreenPanelHost` 里就用 `FLAG_BLUR_BEHIND` + `isCrossWindowBlurEnabled` 判断）。
   面板底色保持 `--g-fill` 半透明（系统模糊生效时**不要**再叠自绘磨砂，否则双份）。
5. **遮罩**：窗口只有 78% 宽后，左侧 22% 的压暗淡不到了 → 二选一：
   (a) 去掉遮罩（最省事）；(b) 另开一个满屏、`FLAG_NOT_TOUCHABLE`、只画 scrim 的窗（多一个窗）。
6. 回归检查：点击打开（内容滑入 + 系统模糊，背景被糊的是窗口背后 = 正确）· 跟手拖出（窗口平移，
   模糊实时）· 返回键/手势 · 面板关闭后窗口销毁 · **左手侧**（`gravityEnd = false` 时窗口要贴左边、
   `params.x` 取负）· 深色模式。
### 0.16 完全复刻 UI（第十一轮）—— 先把 demo 抽成规格，再照着改

**问题**：前十轮我一直在按"二手笔记"（本文档 §0.x）改，没有逐行读 `ui_demo_capsule.2050.html`。
结果真机上一眼就看出来不像：页签是 miuix 的大白胶囊（demo 是 **34dp 分段控件**）、卡片全是实心白盒
（demo 按时间分三档，更早那档**完全透明**且正文变灰）、头部用的是 miuix 搜索框、FAB/输入条/空状态
全是 miuix 默认观感。用户原话：「丑，UI 就是差，交互跟 html 完全不一样」。

**做法**：先把 demo 抽成一份**逐条规格**（`docs/capsule-ui-spec.md`，1938 行，含全部 CSS 原值、
6 个 `@keyframes`、26 条 transition、JS 状态机、每个中文串），然后**按规格重建**。

**这一轮已重建的部分**（全部照抄 demo 数值）：

| 部位 | 关键改动 |
| :- | :- |
| **面板本体** | 改成 demo 的 `.stream.g` **玻璃**：`--g-fill` 半透明白渐变 + 白描边 + `inset 0 1px 0` 顶部高光 + 左上径向 gloss + 两层投影；**补上了 `.scrim`**（整屏压暗 20%，跟手时按进度淡入）—— 之前没做，导致玻璃"透"得不对 |
| **头部** | `.head { padding: 40px 16px 0 }`；搜索改成 demo 的 `.srch`（**h40 胶囊**、`--btn-bd` 描边 + `--btn-bg` 底、16px 图标、聚焦变 accent 60% 描边 + 6% 底、有内容才出现 22dp 清除圆）；条数 `--f-tiny`；关闭是 `.x`（**40×40 圆**）。**去掉了标题行和 miuix 顶栏毛玻璃**（demo 的头部是独立 flex 行，列表从它下面开始滚，不穿过） |
| **页签** | 换成 demo 的 `.ptabs`：**34dp 高、圆角 12、`text 5%` 指示片、`calc(50% - 2px)` 宽、left 280ms 滑**，字 12.5sp，选中 650 |
| **标签行** | `.chips`：h30 玻璃药丸（`.chip`）+ 5dp 圆点 + 选中 accent-soft/accent 55% 描边，**右端 86% 处渐隐**（`BlendMode.DstIn`） |
| **时间轴** | 分组名 `--f-tiny` + **letter-spacing 1px**；竖线用 `text 14%`；节点：今天 = accent + accent-soft 光晕、星标 = accent-solid、其余 = text 26% |
| **卡片** | `.box` 圆角 **18**、内边距 **13/14/6**；**三档**：今天 = `--g-fill` 玻璃渐变 + 白描边 + 顶部高光 + 投影 · 昨天 = `app-elev 66%` + hair 描边 · **更早 = 全透明**（且正文 `color: sub`）；星标 = accent 9% 底 + 45% 描边；完成 = **整张卡 opacity .62** + 正文划掉；正文 **12.5sp / 行高 20sp / letter-spacing .1**；追加块虚线 + 10/9 间距；脚注 `.chip.mini`（h22 玻璃） |
| **操作行** | `.acts`：**margin-top 2 + margin-left -13**（让第一个图标的字形左缘和正文 14dp 对齐）、48×48 命中区、22dp 字形、**opacity .84**、开启态 accent-solid |
| **FAB / 输入条** | FAB 54×54 圆角 19、accent-solid + 投影，打开时变玻璃 + 45° 旋转；输入条 12/16/28、输入框 h48 胶囊（`--btn-bd`/`--btn-bg`、13.5sp）、发送 48 圆 accent-solid（空内容 `.dim`）、hint 10.5sp |
| **空状态** | 46/20/20 内边距、标题 12.5sp/650、提示 11.5sp/行高 1.8、按钮 h34 胶囊（`--btn-bd`/`--btn-bg`）、箭头 accent-solid |

**新增文件**：`docs/capsule-ui-spec.md`（规格）、`HistoryPanelTokens.kt`（demo 的 `:root` token：
浅/深两套颜色、圆角/字号/间距刻度、`--e-out`/`--e-io` 缓动）、`HistoryPanelHeader.kt`（头部三段）。

**第十二轮已补齐（照规格逐条）**：

- ✅ **`.toast`**：新 `HistoryToast.kt` 自己画（`bottom:104px` 相对**整屏**居中、玻璃底、圆角 14、
  内边距 10/10/10/16、撤销按钮 h30 圆角 8 + accent 16% 底 + accent-solid 字），
  **有撤销 4200ms / 无撤销 2200ms** —— 原来用 miuix Snackbar，位置/配色/时长三样都不对。
- ✅ **`.editbar`**：内边距 14/14/12、meta `--f-tiny`、麦克风 30×30、正文 13.5sp/行高 1.6/min-height 48、
  标签 chip h30 玻璃（选中 accent-soft + accent 55%）、`.actbtn` **h38**/圆角 12/字 11.5/图标 13/`--btn-bd`+`--btn-bg`。
- ✅ **`.slot`**：高 **52**、pill、玻璃底+白描边、输入 13.5sp、发送键 accent-solid 圆（空内容透明 + sub 色）。
- ✅ **`.peek`**：纵向压 **44%** 屏高（原来是与把手居中）、圆角 14、玻璃底、`.k` 9.5sp/字距 .4、正文 11.5sp/行高 1.5。
- ✅ **剪贴板卡片也分档**（之前漏传 `dayGroup`，整列都是不透明底）。

**剩下的两条是"有意偏差"**（不是漏做）：

- **`.thumb` 单图 66dp**：demo 里 `.thumb` 是个 66px 的**占位渐变块**（demo 没有真图）。我们是真图片预览 +
  点开看大图，缩到 66px 就没法看了 —— 保留 150dp 预览。
- **页签切换的 filter 语义**：demo 是"切页签重置 filter、保留 query"（两个页签共用一个 filter 变量）。
  我们**每个页签各记自己的 filter**（存 SavedStateHandle）：照 demo 做的话，去剪贴板页看一眼回来，
  刚筛好的标签就丢了。
- **`.item.flash` 双环阴影** / **`.g::after` 噪点层**：Compose 没有对应语法（前者要自己 `drawBehind` 画两层描边，
  后者是 SVG 噪点），观感差别很小，优先级最低。

**已经对的**：搜索**忽略**标签筛选（`HistoryPanelViewModel.filteredStashEntries`：query 非空时跳过 tag 谓词）✅

### 0.13 提醒（已落地）实现要点与坑

- **⚠️ 没有复用 `RemindAlarmScheduler`**：那是**手势**的"x 分钟后提醒我"，到点会拉起
  `RemindAlarmService` 播响铃 + 全屏浮层，而且它的 `PendingIntent` 里**只带 `minutes`**
  —— 装不下"哪条闪念、正文是什么"。闪念提醒要的是"到点安静发一条通知"，所以另起一套：
  新 `StashReminderScheduler`（**绝对时间** + 每条一个 `REQUEST_CODE_BASE + entryId.hashCode()`）
  → 新 `StashReminderReceiver` → 通知渠道 `stash_remind`（`IMPORTANCE_DEFAULT`、**不响铃不震动**）。
- **接收器里不要碰数据层**：进程可能是被这条广播**冷启动**的，那时 Hilt 仓库还没构造好；
  正文直接放进 Intent extra（`EXTRA_TEXT`），接收器只读它。
- **元数据用 `reminders: Map<String, Long>`**（entryId → 触发时间），与 `doneAt` / `sources`
  同款约定：存在即有效、绝不进 `index.json`；`forget` / `pruneOrphans` 都要带上它。
  单测补了"往返"与"更老的文件（没这个键）仍能解码"。
- **完成即取消提醒**（设计稿 `if (s.done) s.remind = null`）：`setDone(true)` 会撤掉闹钟并清 meta；
  撤销完成时把提醒一起排回来。删除也会撤闹钟，撤销删除会补排。
- **重启自愈**：不做 `BOOT_COMPLETED` 接收器（少一个常驻入口），改在**打开面板时**
  `rescheduleAll()` 把**还没到点**的提醒补排一次 —— 成本是一次内存遍历。代价：重启后如果用户一直不开面板，
  那段时间的提醒不会响（已在待确认里写明）。
- **已知简化**：提醒时间没有选择器，只有设计稿那一档"明天 09:00"（`historyDefaultReminderAt()`）；
  真要选时间得加一个 picker（`RemindDurationPickerOverlay` 是手势那套的相对分钟数，不能直接用）。

### 0.14 语音输入（已落地）实现要点与坑

- **必须是前台服务（`microphone` 类型）**：三个输入面都是 overlay 窗，App 进程对系统来说不在前台，
  直连麦克风会被判成后台录音拒掉。overlay 权限（SYSTEM_ALERT_WINDOW）同时给了"后台启动前台服务"的豁免。
- **权限只能由 Activity 申请**：overlay 窗弹不出运行时权限，所以走了 `StashVoicePermissionActivity`
  跳板（同 `CameraPermissionTrampolineActivity` 的做法）；**授权后直接开始听**，
  这样"第一次点麦克风"也是一步到位。
- **会话状态放共享对象**（`StashVoiceSession`）：服务写、Compose 读。**谁发起谁取结果** ——
  按钮记下点击时的 `sessionId`，只有之后 `sessionId` 变大（真的开了新会话）才认领结果。
  ⚠️ 我第一版用的是布尔 `ownsSession`，结果是"用户拒绝权限 / 点了停止"时那一面会一直等着，
  把别人那轮的识别结果抢走。用 `sessionId` 比较就干净了。
- **优先端上识别**：`SpeechRecognizer.isOnDeviceRecognitionAvailable()` 为真时用
  `createOnDeviceSpeechRecognizer`（离线、不把语音送云端），否则退回标准识别器。
- **`onError(ERROR_CLIENT)` 是 `cancel()` 的回声**：主动收尾期间要把回调**丢掉**（`stopping` 标志），
  否则用户点"停止"会看到一条"语音输入失败"。
- **前台通知渠道**必须走 `ForegroundNotificationChannels.ensureUsable()` 再 `startForeground()`
  （渠道缺失/被关会让 AMS 异步杀进程，外面包 try/catch 无效）；渠道被用户关掉时
  本服务**静默降级为失败**（同 `RemindAlarmService` 的处理）。
- **没做的事**：没接"缺少权限"总览页（App 那个 `missing_permissions_*` 列表），
  权限完全由麦克风按钮自己申请；也没做实时把 partial 结果写进输入框
  （只在"正在听"上给反馈，final 才落字）。

### 0.6 时间轴槽（已落地）实现要点与坑

- **必须扁平化**：分组表头与条目是**同一层 `LazyColumn` 的两种行**（`HistoryTimelineGrouping.kt` 的
  `HistoryTimelineRow`），不是"每组一个 `Column`"。后者会让一整组变成不可拆分的懒加载项。
- **行间距必须是 0**：竖线是**逐行画自己那一段**的（`Modifier.matchParentSize().drawBehind{}`），
  原来那句 `verticalArrangement = Arrangement.spacedBy(12.dp)` 会在行之间留缝 ⇒ 竖线断口。
  间距改由行内 padding 提供（表头 18dp、条目 10dp，照设计稿 `.grp{margin-top:18px}` / `.item{margin-bottom:10px}`）。
- **竖线不能用逐行渐变**：设计稿是整组一根 `linear-gradient(…14%, transparent)`；逐行各渐变一次
  会在每个行边界"重启"成亮色，出现接缝。改成按行在**本段内**的位置预先算强度（`lineAlpha` 单调递减）。
- **分组 key 必须唯一**：数据层没承诺排序（`StashRepository` 只是 `listOf(new) + 旧`），一旦同一组
  被拆成不连续两段，`history_group_<组>_<段号>` 才不重复 —— **重复 key 会让 `LazyColumn` 直接抛异常**。
  这个坑是单测抓出来的（`HistoryTimelineGroupingTest`），改这块逻辑请连着跑那条测试。
- **分组边界用 `Calendar` 取当天零点**，不能用 `今天零点 - 86400000`（夏令时那天会差 1 小时，
  午夜前后的记录会被算错组）。单测里有美东回拨那天的用例。
- 与设计稿的**两处刻意偏差**：①节点中心对齐到竖线中心（设计稿 `left:-8.5px` 与 `.vline{left:54px}`
  自身就差 2.25dp，是它自己的取整）；②最后一组的竖线没做 `.grp:last-child{bottom:46%}` 那刀，
  因为强度渐减已经让段尾几乎不可见了。
- ⚠️ **没跟着改的一处**：`historyPreviewWidthPx()` 仍是「面板宽 − 24dp」，而闪念卡片现在实际窄了
  62+12−24 = 50dp（左边多了 62dp 时间轴槽）。解码图略大于显示宽度，**只是浪费一点内存，不影响正确性**；
  真要抠的话给闪念页签单独减 `HistoryTimelineGutterWidth`。

### 0.5 实现时踩过的坑（避免重犯）

- **Gradle 任务名必须写全风味**：`:app:compileDebugKotlin` 会因 `full`/`lite` 二义性失败，要用 `:app:compileFullDebugKotlin`。
- **不要在有构建活着时删 `app/build/kotlin`** —— 会让 daemon 持锁卡死（我踩过，45 分钟零输出）。
- **`Select-String` 默认大小写不敏感**：过滤 `'BUILD '` 会命中一堆 `preBuild UP-TO-DATE`，取尾部要改用 `-cmatch`。
- **增量编译会产生假报错**：P2 那次报的 `ClipboardMonitorForegroundService.kt` `canPromote` 就是，重跑即消失。**遇到不在自己改动范围内的报错，先重跑一次再下结论。**
- **不要让工具把整份文件改写成 CRLF**：本仓库是 **LF**（`core.autocrlf=false`、没有 `.gitattributes`）。上一个会话改过的
  9 个 `.kt` 在盘上变成了全 CRLF，于是 `git diff` 里它们显示成"整文件重写"（合计 ~3000 行），
  而真实改动只有 ~400 行；提交时根本没法 review。本次已还原成 LF。
  **提交前请跑一次这两条对比**（两边数字不一致的文件就是行尾被改了）：
  ```powershell
  git diff --numstat        # 原始
  git diff --numstat --ignore-cr-at-eol   # 忽略行尾
  ```
  ⚠️ 这个坑**还会复发**：第二轮里 `SlideIndexImageEditorActivity.kt`（HEAD 是 LF）被编辑工具整份写成了 CRLF，
  单文件 diff 从 2 行涨到 1578 行。**每轮改完都跑一次上面那两条对比**，中招就用下面的字节级还原
  （只删"CR 紧跟 LF"的那种，不动文件里真正的孤立 CR）：
  ```powershell
  $p = 'path/to/file.kt'
  $b = [IO.File]::ReadAllBytes($p)
  $out = [System.Collections.Generic.List[byte]]::new()
  for ($i=0; $i -lt $b.Length; $i++) {
    if ($b[$i] -eq 13 -and ($i+1) -lt $b.Length -and $b[$i+1] -eq 10) { continue }
    $out.Add($b[$i])
  }
  [IO.File]::WriteAllBytes($p, $out.ToArray())
  ```
- **Gradle 9 会对源文件输入做行尾归一化**：所以只改行尾（CRLF↔LF）时
  `:app:compileFullDebugKotlin` 仍报 `UP-TO-DATE` —— 这是正常的，不是漏编译，不必 `clean`。

---

## 0.3 实现进度（每次接手前先看这里）

编译校验命令（**两个风味，任务名必须写全**）：
```powershell
.\gradlew.bat :app:compileFullDebugKotlin
```
改到时间轴分组 / 列表结构 / 卡片结构时连着跑这条（会顺带编译 main）：
```powershell
.\gradlew.bat :app:testFullDebugUnitTest --tests "com.slideindex.app.overlay.history.HistoryTimelineGroupingTest"
.\gradlew.bat :app:testFullDebugUnitTest          # 全量，几分钟
```

| 期 | 状态 | 已落地内容 |
| :- | :- | :- |
| P1 数据层 | ✅ **完成，编译通过** | 新增 `stash/StashMetaRepository.kt`（标签定义 + 标签绑定 + 完成态 + 追加内容，独立文件 `stash_meta.json`，跨进程锁/监听、首运行灌默认五标签、`pruneOrphans`、`pendingTodoCount`）；`StashAccess` 加 `metaRepository`；`StashEntry.matchesQuery(query, tagNames = emptyList())` 加参数（不破坏既有调用点）；`StashRepository` 加 **读失败拒写**（数据保护）+ **`updateText()`**（以前仓储完全没有改正文的接口） |
| P2 把手 | ✅ **完成，编译通过** | 新增 `overlay/HistoryFloatHandleGestureExclusion.kt`（右缘排除区，现有工具做不了）；`HistoryFloatContent.kt` **按 demo 重做形态**（视觉 9×28 / 圆角 5 / 距右 12dp / 命中区 48×48 / 有待办整条变色 / 接上 `onLongClick`）；`HistoryFloatService.kt` 挂排除区 + **息屏隐藏** + `handleAlert` 随 500ms 轮询刷新 |
| P3a 参数对齐 | ✅ **完成，编译通过** | 面板宽 **50% → 78%**（`HistoryPanelUi.historyPanelWidth`，新增 `PANEL_WIDTH_FRACTION`）；圆角 **14 → 26dp**（新 `HistoryPanelCornerRadius`）；卡片操作行 **32/20 → 48/22dp**（新 `CARD_ACTION_HIT_DP` / `CARD_ACTION_GLYPH_DP`）；`HistoryPanelColors.cardBackground(starred, done = false)` 加完成态（+ `DONE_CONTENT_ALPHA`）；页签文案 4 套 locale 改为 闪念 / Flashes / ひらめき / ومضات |
| P3b 面板结构 | ✅ **完成**（状态标记曾长期停在 🟡，见 §0.3.1） | ✅ **已做**：标签筛选 chip 行（新 `HistoryTagFilterRow.kt`，横向滚动无滚动条；「全部」+ 各标签单选，标签定义来自 `stash_meta.json` 所以增删标签自动跟随）；`HistoryPanelViewModel` 加 `selectedTag`（SavedStateHandle 持久化）/`availableTags`/`metaRepository` 形参（有默认值）；`filteredStashEntries` 改为 4 路 combine，**并把标签名接进 `matchesQuery`**；`HistoryPanelViewModelFactory` 透传 `StashAccess.metaRepository`；文案 `stash_tag_filter_all`（values + values-zh，ja/ar 自动回退英文）。<br>✅ **已做（本次）**：**左侧时间轴槽** —— 新增 `HistoryTimelineGrouping.kt`（纯逻辑：今天/昨天/更早分组、摊平成 Lazy 行、竖线强度，见 §0.6）与 `HistoryTimelineGutter.kt`（62dp 槽 + 54dp 竖线 + 7dp 节点 + 分组名）；`HistoryPanelScreen` 的闪念列表改成"分组表头与条目同层的扁平列表"、行间距归零、左内边距归零；**时间移出卡片**（`HistoryEntryCardShell` 新增 `showTimestamp`，闪念传 `false`、剪贴板仍在卡片内）；分组文案 `stash_group_today/_yesterday/_earlier` 四套 locale，并**补齐了 ja/ar 缺失的 `stash_tag_filter_all`**；新增单测 `HistoryTimelineGroupingTest`（8 例，全绿）。<br>✅ **已做（第二轮）**：**卡片重排 + 操作行 + 空状态三选一** —— `HistoryEntryCardShell` 加 `done`（内容 0.62 淡出）/`showActionDivider`/可空 `headerTrailing`（**闪念卡片头部整行消失**，照设计稿）；正文完成态划掉（`textDecoration`）；追加块（虚线 + `＋ 内容`，`HistoryCardAppendBlock`）；底部 `.foot` 行（来源 chip + 标签 chip，`FlowRow`）；操作行改成「主状态 + 复制 + ⋮」，**星标**按 `isTodo` 规则成为主状态动作、「取词」搬进 ⋮ 菜单（⋮ = 星标 / 取词 / 钉屏 / 分享 / 保存图片 / —— / 删除，删除前有分隔线）；**来源**落地（`StashMetaStore.sources` 字符串键 + `setSource`，接线剪贴板 / 取词 / 图片三条路径）；空状态按「全空 / 搜不到 / 标签筛不出」三分支各给文案 + 出口（新 `HistoryEmptyState.kt`）。详见 **§0.7**。<br>✅ **已做（第三轮）**：**FAB + 就地输入条**（新 `HistoryPanelComposer.kt`）—— 54dp FAB（圆角 19、主题色、加号，打开后变玻璃底 + 转 45°）、输入条（48dp 胶囊 + 送出圆键 + 说明行、顶部 26dp 圆角）、**IME 抬升**（`rememberOverlayImeBottomHeight()`）、回车/送出键存下、存下后清空并**清掉搜索与标签筛选**（否则新条目可能被筛掉）、返回键先收输入条、输入条打开时列表底部让位。**P3b 到此全部做完。** |
| P4 动效 | ✅ **完成** | ✅ **第四轮**：**新条目 flash**（`Animatable` + 1.4s 主题色环 + 柔光，设计稿 `.item.flash`）· **首屏错开淡入**（前 8 行 40ms 步进，窗口 720ms 后不再给新滚进来的行播）· **换页动效**（页内容 16dp 位移 + 淡入，跟随翻页比例；比例在 `graphicsLayer` 里读，不触发重组）· **触觉**（新 `HistoryHaptics`：存下/复制/删除/完成/星标 = CONFIRM，把手长按 = LONG_PRESS，菜单项 = 轻点；`HapticHelper` 新增 `actionConfirm`/`actionTick` 两个**只受总开关+强度**控制的通用档）· **把手脉冲**（新 `HistorySaveSignal` 跨窗传「刚存下」，设计稿 `.pip.pulse` 900ms / 35% 处 `-3px + scaleY 1.18`）。<br>✅ **第五轮**：**跟手拉出 + 右拖收回（弹回）** —— 新 `HistoryPanelReveal`（跨窗共享进度）+ `HistoryPanelRevealDriver`（拖动贴手指 / 松手弹簧）+ 把手侧 8px 轴锁定与过半轻震 + 面板窗"看得见但不吃触摸"模式；侧栏宿主新增 `dragReveal` 与 `setTouchable`。做法是 P0 的方案 B（**不是** §0.1 的合并同窗），见 **§0.10**。<br>⏳ **订正（见 §0.3.1）**：**peek 已在 P5 落地**（`HistorySavePeekWindow`）；页签指示片 miuix 自带、不用改；**只有"设计稿那层 20% 遮罩"是有意不做**（§0.10，它会让更低层的把手一起变暗） |
| P5 输入面 | ✅ **完成** | ✅ **第六轮**：**输入槽**（设计稿 `.slot`）—— 新 `HistoryNoteSlotWindow.kt`：长按把手就地记一条，贴把手弹出、宽度 72→300dp **从右缘长出来**、抢焦点弹输入法、回车/✓ 存下、空内容直接收起、返回键收起；存下后**把手脉冲 + peek 预览**自动发生（`HistorySaveSignal`）。<br>✅ **peek**（设计稿 `.peek`）—— 新 `HistorySavePeekWindow.kt`：`FLAG_NOT_TOUCHABLE` 小窗、贴把手左侧、`已存下 + 正文（超 18 字截断）`、1.2s 自动消失，连续存下会重新计时。详见 **§0.11**。<br>✅ **第七轮**：**就地编辑条**（新 `HistoryPanelEditBar.kt`）—— 改正文（多行、光标停在末尾）/ 改标签 / **追加一段** / 完成 / 删除 / 保存；入口在卡片 ⋮ 里。**撤销接线**（设计稿的单槽 `undoFn`）—— 存下 / 删除 / 完成 / 星标 / 编辑保存 / 追加；删除的撤销靠新增的 `StashRepository.restore()`，`delete` 因此不再立刻删图片，改由启动时 `pruneOrphanImages()` 收敛。详见 **§0.12**。<br>✅ **第八轮**：**提醒**（设计稿 `.item .remind` + `.editbar` 的「提醒」）—— 元数据加 `reminders`；新 `StashReminderScheduler` + `StashReminderReceiver`（**绝对时间 + 每条一个 request code**，到点只发通知，不响铃不震动）；卡片时间行出现 `⏰ 明天 09:00` 这样的 chip；编辑条「提醒」按钮一键设/清（默认就是设计稿的"明天 09:00"）；**完成即取消提醒**（设计稿同款）；进面板时把未来的提醒补排一次（重启自愈）。详见 **§0.13**。 |
| P6 语音 | ✅ **完成** | ✅ **第九轮（真做）**：manifest 加 `RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE` + `StashVoiceInputService`（`foregroundServiceType="microphone"`）+ `StashVoicePermissionActivity`（权限跳板）；**会话状态机** `StashVoiceSession`（Idle/Listening/Error + partial/final/error + sessionId）；识别优先**端上识别**（`createOnDeviceSpeechRecognizer`），不支持时退回标准识别器；**三处输入面**（输入槽 / 面板输入条 / 编辑条）都挂了同一个麦克风按钮（`HistoryVoiceMicButton`：在听时实心 + 呼吸缩放，结果只给"点它的那一面"）。详见 **§0.14**。 |
| P7 真机适配 | ⏳ **未开始**（其中 IME 避让已被 §0.16.4–0.16.7 覆盖大半） | 全屏/横屏/分屏 · 深浅色 · 无障碍审计 · 性能 —— 仍待做；IME 避让：overlay 专用 IME 高度助手 + 弹窗抬升 + §0.16.7「键盘优先返回」已覆盖 |

**待你确认/复核的点：**
1. `values-ja` / `values-ar` 的「闪念」译文（`ひらめき` / `ومضات`）是我拟的，**建议母语者过一遍**。
   本次新增的 `stash_group_*`（今日/昨日/それ以前、اليوم/أمس/أقدم）与补的 `stash_tag_filter_all`（すべて/الكل）同样是我拟的。
1b. ⚠️ **时间轴槽的分组名是右对齐在 48dp 宽的槽里**（照设计稿 `.glabel{width:48px}`，设计稿原文是 2 个汉字）。
   英文 `Yesterday` 在 11sp 下可能比 48dp 宽 —— 我做了"单行 + 允许向左溢出"处理，
   **真机上请专门看一眼英文/日文下这个分组名有没有被面板左缘切掉**。切了的话最省事的修法是 `TimelineGroupLabelWidth` 加到 52–56dp（竖线在 54dp，会贴上去）或把分组名改短。
2. 面板宽度从 50% 改到 78% 之后，**两页签与卡片的排版要真机看一眼**（`historyPreviewWidthPx()` 也跟着变宽，上限仍是 960）。
3. P3a 只改了参数，**没动结构** —— 时间轴槽（P3b）才是唯一"真结构"改动，且 §6.4 那条"竖线在懒加载条目边界能否不断口"仍未验。
4. ⚠️ **「完成」的判定键是硬编码中文标签名「待办」**（`StashMetaRepository.TODO_TAG_NAME`，P1 就这样定的）。
   用户把这枚标签删掉或改名之后，新条目就没有「完成」入口了（已完成的条目仍保留按钮，能取消）。
   要不要改成"标签可以标记为待办类型"（在 `StashTag` 上加个 `isTodo` 字段）？
5. **来源 chip 的三条线我按理解接线**：剪贴板→闪念 / 取词面板→闪念 / 图片编辑→闪念。
   `ScreenPinManager`（钉图里"暂存"）没接（设计稿没有对应来源），所以那类条目**不显示来源 chip** —— 这是我的选择，可改。
6. **卡片完成态 + 追加块 + 标签 chip 都是新增可视元素**，浅色/深色两套主题下值得真机各看一眼（尤其 `.foot` 里那枚 10.5sp 的小 chip）。
7. ⚠️ **输入条 + IME 是这一轮最需要真机确认的一块**：overlay 窗抢焦点 / 弹输入法 / 收起输入法
   （`FLAG_NOT_FOCUSABLE` 来回切）在 MIUI 上历来最容易出问题。请专程试：
   ①点 FAB → 输入法是否弹出、输入条是否正好在输入法上方；②连续存两条；③返回键收输入条；
   ④输入法收起后列表底部是否恢复正常；⑤切到剪贴板页签时 FAB/输入条是否消失。
8. **输入条里暂时没有语音按钮**（设计稿 `.composer` 左侧有一个）：那是 P6 语音真做时才接，
   现在放一个点了没反应的按钮不如不放。P6 会把三处输入面一并加上。
9. **动效的主观手感要你试**：flash 的强度（1.4s 环 + 8dp 柔光）、错开淡入的步长（40ms × 8 行）、
   换页位移（16dp）都是我照设计稿数值搬的，真机上偏快/偏慢都容易调（常量都集中在文件末尾）。
10. **跟手拉出被我有意留到单独一轮**（§0.1 的"合并成同窗"）。理由：它要动**最常驻的那个窗**
    （`HistoryFloatService` 的把手窗），而这轮做的全是面板内的加法，风险互相独立。
    下一轮做它时如果真机出问题，回退范围也只有那一个窗。
    > ✅ **第五轮已落地**，但改成了 P0 方案 B（两窗共享进度），**没有**合并同窗 —— 理由见 §0.10。
11. ⚠️ **跟手拉出必须真机试这几条**（两窗协同的坑只能真机暴露）：
    ①慢慢拖：面板是否**黏着手指**、有没有明显滞后/撕裂；②快拖后松手：过半归位、不过半弹回；
    ③拖动中途被系统返回手势抢走（右缘 48dp 是返回热区）：面板要能自己弹回去、不能卡在半开；
    ④面板已经打开时再拖把手：不应重进跟手（应该没反应或照旧）；
    ⑤纵向拖把手：仍然只挪位置、不拉面板（8dp 轴锁定）；⑥过半那一下的轻震是否感觉得到。
12. ⚠️ **输入槽 + peek 也要真机试**：①长按把手（不是点）→ 输入槽是否弹出、输入法是否跟着弹；
    ②存一条：把手是否脉冲、peek 是否在把手左侧浮出并在 1.2s 后消失；③连存两条（peek 是否重新计时）；
    ④空内容按 ✓ 是否直接收起；⑤返回键是否只收输入槽（不该退出底层 App）；
    ⑥把手停在屏幕偏下时输入槽会不会被输入法盖住（已知问题，P7 处理）。
13. ⚠️ **编辑条 + 撤销要试**：①卡片 ⋮ →「编辑」是否弹出编辑条、光标是否在末尾；
    ②改正文 + 改标签 → 保存是否生效、提示里的「撤销」是否把两者都还原；③追加一段 → 卡片上是否出现追加块、
    撤销是否只去掉刚追加的那段；④删除 → 撤销是否把条目放回**原来的位置**（含图片条目）；
    ⑤连续两个动作时，第二条提示是否顶掉第一条（不该排队等 4 秒）；⑥编辑条开着时 FAB 是否让位、返回键是否只收编辑条。
14. ✅ **提醒已做**（§0.13）。真机要试：①编辑条点「提醒」→ 卡片时间行是否出现 `⏰ 明天 09:00`；
    ②到点是否收到通知（**应无声无震动**）、通知里是否是该条正文；③再点一次「提醒」是否取消、闹钟是否真的撤掉
    （到点不该再响）；④标记完成是否连带取消提醒；⑤杀进程/重启后**再打开面板**，未来的提醒是否还能响
    （已知：一直不开面板就不会补排）；⑥通知权限关掉时的表现（应静默跳过，不该崩）。
15. **提醒时间暂时只有"明天 09:00"一档**（设计稿就是这个简化交互）。要自由选时间得另做选择器。
16. ⚠️ **语音输入要真机试**（这一轮唯一没法在这里验的能力）：①第一次点麦克风 → 是否弹权限对话框、
    授权后是否**自动开始听**；②三处输入面（输入槽 / 面板输入条 / 编辑条）是否都能听、结果是否落进对应的输入框；
    ③"正在听"的反馈（实心麦克风 + 呼吸缩放 + 前台通知）是否正常；④点麦克风停止 / 点通知上的「取消」
    是否都不留残字；⑤说一句 → 结果是否只出现一次（不该被别的输入面重复插入）；
    ⑥设备没有语音识别服务时的提示；⑦录音权限被拒绝后是否还有提示；⑧锁屏/息屏时是否会被系统掐掉。
17. ⚠️ **元数据层刚修好（§0.15 ①）**：真机上 `files/stash_meta.json` 之前**根本没生成过** ——
    现在会出现（含 5 个默认标签）。要试：①标签 chip 行是否有 5 个默认标签；②给条目打标签 / 标完成 /
    追加一段 / 看来源 chip，**杀进程重进后是否还在**（这次是真的落盘了）；③提醒与语音也依赖这一层，
    之前的"没反应"大概率同源，值得重测一遍。
18. ⚠️ **面板宽度要复核（§0.15 ②）**：上一版真机上侧栏是**满屏**（`containerSize` 返回旋转尺寸所致），
    已改用 `BoxWithConstraints` 的 `maxWidth`。重连设备后要截图确认是 **78%**（右侧留 22% 能看到后面的 App）。
19. **自注册单例的纪律**（§0.15 ① 的教训）：新增数据层仓库时，必须确认"进程启动就会构造"，
    并且**在真机上看一眼文件是否真的生成** —— `?.` 调用链会把"对象不存在"变成静默空操作。

---

### 0.3.1 验收状态订正（2026-10-08，接手前先看这里）

**先明确一件事**：上面 1–19 是各轮留下的"待你确认/复核"提醒，**不等于"这些都没做"**。
用户从 UI 复刻那轮（`c8fee4e1`）起就**一直在日常使用这个面板**，所以"日常交互覆盖得到"的项按**已通过**计；
只把"日常使用盖不到的专项"留作待验。当前状态：

- ✅ **已通过**（日常使用覆盖 / 本轮真机核对过）：
  2、3、8、9、10、11、12、13、17、18
  - 其中本条会话直接核对过的：面板宽 78% 与两页签排版（2、18，多张真机截图）、
    时间轴竖线连续无断口（3，截图）、元数据真的落盘（17，`files/stash_meta.json` 里标签/绑定/order 都在，
    重装重启后仍在）、标签拖拽排序（用户自验）、返回键与手势返回（§0.16.6/§0.16.7 的清单）。
  - 12⑥「输入槽停在屏幕偏下时可能被输入法盖住」仍属 P7 的 IME 避让，单独留待。
- ⚠️ **仍待专门验**（日常使用盖不到的专项）：
  1（ja/ar 译文请母语者过一遍）· 1b（英文/日文下时间轴分组名会不会被面板左缘切掉）·
  6（**深色主题**下的小 chip 与完成态）· 14（提醒到点通知、重启后补排）· 16（语音权限/端上识别/锁屏被掐）
- 🅿️ **设计决定 / 有意不做**：4（「待办」仍是硬编码中文关键字 —— §0.16.4 已定只读规则：可改色、禁改名删除）·
  5（来源 chip 只接剪贴板/取词/图片三条线）· 15（提醒只有"明天 09:00"一档，设计稿即如此）·
  P4 的 20% 遮罩（§0.10）
- 🗑️ **已废弃**：7（面板输入条已改成屏幕居中模态，见 §0.16.4；那五条"输入法会不会弹"的验收已由
  §0.16.6/§0.16.7 的键盘路径覆盖）· 19（纪律条目，已并入 §0.16.4 的"环境与协作注意"）
- ⏳ **P7 真机适配整期未开始**：其中 **IME 避让**已被 §0.16.4–0.16.7 覆盖大半；
  **全屏/横屏/分屏排版 · 深色主题 · 无障碍审计 · 性能** 仍待做。

> 一句话：**代码层面 P1–P6 已完成（含 P3b 的结构改造），P7 未开始**；
> "真机验收"里日常使用盖得到的算过，只剩上面 5 项专项 + P7 那四块。

### 0.3.2 「过一遍」实测结果（2026-10-08：真机 + 静态审计）
把 §0.3.1 里"仍待"的项尽量过了一遍：

| 项 | 结果 |
| :- | :- |
| ① 四语言完整性 | ✅ **面板相关 364 个键四语言齐全**（本轮新增 15 个也齐）。全仓库只差 2 个键 —— **订正：那 2 个都是 `translatable="false"`，本就不该翻译**（`clipboard_overlay_screenshot_editor` 空串占位、`clipboard_overlay_remote_copy_package` 是 Google 的包名/Activity 名），我第一版审计没看 `translatable` 属性、误报成"缺口"。ja/ar 没有"整句漏翻"（唯一"与英文相同"的是许可证名 AGPLv3，本就不翻）。**译文质量仍要母语者过一遍** |
| ② 英文/日文下时间轴分组名会不会被切 | ✅ **英文实测未被切**：真机把本 App 切成英文后，`Today` / `Yesterday` 都完整显示（"单行 + 允许向左溢出"那套有效）。日文更短；阿拉伯语是 RTL，未单独验 |
| ③ 深色主题 | ✅ 面板底、小 chip、时间轴标签、FAB 在深色下都可读（真机截图）；"已完成"卡片在深色下的观感未单独看 |
| ④ 提醒 | ✅ **"重启补排"实测通过**：`dumpsys alarm` 里有 4 条 `…action.STASH_REMIND`，`am force-stop` 后重开面板又被补排回来（用用户自己设的提醒验的，没动数据）。"到点真的响"仍需等到时间点 |
| ⑤ 语音 | ⏳ 只能静态核对（`RECORD_AUDIO` + 前台服务 + 三处输入面的麦克风都在）；真机识别要真人说话 → 仍待 |
| P7 · 横屏 | ✅ 横屏下布局正常（面板 78% 宽，头部 / 页签 / 胶囊行 / 时间轴 / 卡片 / FAB 都到位），无崩溃 |
| P7 · 深色 | ✅ 见 ③ |
| P7 · 无障碍 | ⚠️ 静态审计：面板里几个**可点**图标没给无障碍标签（头部关闭 `✕`、搜索框「清空」`✕`、卡片里若干图标都传 `contentDescription = null`）→ TalkBack 读不出来。`panel_close` 等字符串已现成，属"补标签"的小活 |
| P7 · 性能 | 📊 一次粗糙实测（人工 swipe 滑两屏）：总帧 636 / 掉帧 89（14.0%）/ 50th 9ms / 90th 22ms / 95th 28ms / 99th 53ms；GPU 直方图干净（1–4ms）。窗口里混了"启动 + 开面板 + 注入滑动"，**要定性得单独做一次基准**（只统计列表滚动期） |
| P7 · 分屏/自由窗口 | ⏳ 未验（要手动进分屏） |

**结论**：这一遍把"日常盖不到的专项"里的 ②③④ 和 P7 的横屏/深色过掉了；剩 **ja/ar 译文人工审校 · 语音真机识别 · 分屏 · 性能基准 · 无障碍补标签** 五件，外加两个"要不要做"的小优化（条数常驻、空搜索框返回改两次）。

---

## 0. 基准声明（本文档最高优先级，覆盖下面所有条目）

**网页 demo 就是规格。App 向 demo 对齐，不是 demo 向 App 对齐。**

由此，下面这些条目**一律按 demo 做**，不接受"保留现状 / 降级 / 占位"：

| 项 | 现状 | **按 demo 改成** | 我之前错的建议 |
| :- | :- | :- | :- |
| **面板宽度** | `windowInfo.containerSize.width / 2f` | **78%**（`ui_demo_capsule.html` `.stream { width:78% }`），圆角 26（`--r-2xl`）只在左侧 | ~~建议不改~~ → 改 |
| **把手形态** | 32×96，`RoundedCornerShape(topStart=22,bottomStart=22)`，内嵌 4×24 竖条，alpha 0.09→0.22 | **视觉 9×28、距右缘 12px、命中区 48×48**（`.pipwrap{right:0;width:48px;padding-right:12px}` + `.pip{9×28}`） | ~~建议保留真机、让 demo 对齐它~~ → 反过来 |
| **跟手拉出** | 无（只累计位移过 -20f 才开面板） | **必须有**：拖动时面板黏着手指 | ~~先按现状交付、A 单独排一期~~ → 必做 |
| **语音** | 完全没有（manifest 里 `RECORD_AUDIO` / `microphone` = **0 个**） | **真做**，三个输入面（输入槽 / 面板输入条 / 编辑条）都能用 | ~~只放入口占位~~ → 真做 |
| **分组标签吸顶** | 无先例 | demo 里**没有**吸顶（分组标签在**左侧槽**，不是 sticky）⇒ 不用做 | （此问题作废） |
| **时间轴槽** | 时间在卡片内首行 | **槽在左**：`.grp{padding-left:62px}` + 分组标签 48 宽右对齐 + `.vline` 在 54px + `.item .node` 在 `left:-8.5px`；**时间移到卡片外**（`.item .when` 在 `.box` 之前） | — |

### 0.1 「跟手拉出」的具体做法（✅ 已落地，做法与下面这份原始方案不同）

> ⚠️ **实际落地走的是"两窗共享进度"（P0 方案 B），不是本文下面写的"合并成同窗"（方案 A）。**
> 原因与取舍见 **§0.10**。下面这段原始设计保留作对照。

难点是把手窗与面板窗是两个窗（见 `docs/capsule-refactor-p0-findings.md` §1）。**做法：合并成一个窗，并让它按状态改变尺寸。**

1. **关闭态**：窗保持现在的 `WRAP_CONTENT`（只有把手那么大）——因为全屏可触摸的浮窗会吃掉底层 App 的触摸，这一点不能变。
2. **开始拖动**：`windowManager.updateViewLayout(view, params)` 把窗**改成 `MATCH_PARENT`**（面板此时还完全在屏幕外，视觉上无变化）。
3. **拖动中**：把手与面板现在**在同一个窗、同一个坐标系**里 ⇒ 面板的 `translationX` 直接由拖动进度的 `MutableState` 驱动，**真·跟手**，无需跨窗同步。
4. **松手**：过半 → 弹簧收尾到 `translationX = 0`；不过半 → 弹回并**把窗缩回 `WRAP_CONTENT`**。
5. **面板打开期间**：窗保持 `MATCH_PARENT` + 可触摸（此时面板是模态的，本就该吃掉触摸）。
6. 需要留意：拖动中途改窗尺寸可能有 1 帧抖动；`FLAG_NOT_FOCUSABLE` 与"需要输入时临时抢焦点"的现有机制要保持。

用到的都是既有原语：`updateViewLayout`（`HistoryFloatService.kt:200` 已在用）、`MutableState` 驱动、弹簧（`OverlayPanelEnterAnimation.enterSpec`）。

### 0.2 修正后的工期（基准改严 + 语音真做 + 跟手必做）

| 期 | 内容 | 人日 |
| :- | :- | :- |
| P1 | 数据层：`tags.json` / `done.json` 独立文件 + **编辑正文接口** + 撤销栈 + 标签进 `matchesQuery` + 解析失败保护 | 2–2.5 |
| P2 | 把手：**形态按 demo 重做** + 长按=速记 + **手势排除区（要新写，现有工具只做左缘）** + 息屏隐藏 | 2–2.5 |
| P3 | 面板结构：头部换搜索行 + 页签改名 + chip 行 + **左侧时间轴槽** + 卡片重排（追加/完成/来源）+ 图标行 + ⋮ 菜单 + FAB/输入条 + 空状态 | 3.5–4.5 |
| P4 | 动效：**跟手拉出（含合并成同窗，§0.1）** + 右拖收回 + 页签指示片 + 换页 + 错开淡入 + peek + 脉冲 + flash + 触觉 | 3–4 |
| P5 | 三个输入面统一 + 撤销接线 + 提醒可见 | 1.5–2 |
| P6 | **语音（真做）**：`RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE` + FGS `microphone` 类型 + 运行时申请 + 会话状态机 + 三处接入 | 1.5–2 |
| P7 | 真机适配与联调：IME 避让、全屏/横屏/息屏、深浅色、无障碍、性能 | 2.5–3.5 |
| | **合计** | **16–21 人日** |

---

## 0. 一句话结论

**可以做，而且大部分是「改造既有」而不是从零写** —— 但**不是一次能做完的事**，估算 **12–15 人日**，拆 6 期。
其中有一个**架构级未知必须先解决**（见 §4.1：把手与面板是否同窗），它决定"跟手拉出"能不能按 demo 那样连续。

---

## 1. 可复用资产（这是好消息）

demo 里几乎每个元素在仓库里都有对应资产，重构 = 改造它们，不是新建：

| demo 里的东西 | 仓库既有 | 改造内容 |
| :- | :- | :- |
| 贴边把手（9×28 视觉 / 48×48 命中 / 可拖 Y / 位置持久化） | `service/HistoryFloatService.kt`、`settings/HistoryFloatHandlePosition.kt`（`resolveY`/`clampY` + 已有单测） | **不新建**，在既有把手上加：长按=速记、拖内=面板、点=面板 |
| 玻璃（真模糊 + 顶部光泽 / 内高光 / 内阴影 / 噪点） | `overlay/LocalFrostedGlassBackdrop.kt`（反射 `createBackgroundBlurDrawable`） | 保持"浮层内禁 miuix RuntimeShader"这条红线 |
| 面板骨架（78% 宽、14dp 内圆角、两页签） | `overlay/history/HistoryPanelUi.kt`、`HistoryPanelScreen.kt` | 页签 暂存夹 → 闪念；头部换成搜索行 |
| 条目卡片（时间 / 内容 / 分隔线 / 操作行） | `overlay/history/HistoryEntryCards.kt`、`HistoryEntryCardParts.kt` | 操作行改图标式；加 vline 时间轴槽、追加块、完成态 |
| 卡片配色（星标=primaryVariant 35% 底 + primary 45% 描边） | `overlay/history/HistoryPanelColors.kt` | 已有，demo 里已对齐 |
| 分页 / 上限 200 | `overlay/history/HistoryFloatPagination.kt`、`stash/StashRepository.kt`（`MAX_ENTRIES = 200`） | 沿用 |
| 面板进入动效 | **`overlay/OverlaySidePanelHost.kt:207-232`**（`AnimatedVisibility` + `slideInHorizontally(spring(0.8, 300f))` + `fadeIn(tween(250))`） | 改成"从把手位置滑出"。<br>⚠️ **勘误**：本表原先写的 `OverlayPanelEnterAnimation.kt` / `OverlayPanelEnterAnimator.kt` **与收纳侧栏无关** —— 它们服务边缘手势面板（`EdgeGestureOverlayView.kt:86` 等）。 |
| 弹簧动效 | `overlay/OverlayFloatSpringMotion.kt` | 跟手回弹用它 |
| 层级 / z-order 协调 | `overlay/compositor/OverlayScene.kt`、`OverlayLayer.kt`、`OverlayZOrderCoordinator.kt` | demo 里我手写的 z-index 断言，真机由这套统筹 |
| 触摸事件分发 | `overlay/MotionEventDispatcher.kt` | 手势冲突（横向/纵向轴锁定）走它 |
| 返回手势避让 | `overlay/OverlayPanelSystemGestureExclusion.kt`（`BACK_EDGE_DP = 48f`） | ⚠️ **勘误：这个工具不能直接给把手用。** P0 勘察确认它 **只构造左侧 48dp + 底部导航条两个矩形，没有右缘矩形**（`:64-71`）；唯一的公开 API 是 `attach()`（`:19`），`updateExclusionRects` 是 private（`:46`），重算只在 layout change / attach 时发生 —— **把手靠 `params.y` 移动时 view layout 不变，rects 不会重算**；重复 `attach()` 还会叠加监听。⇒ **把手的排除区要新写或改造此工具**（可参照 `EdgeTouchCaptureView.kt:41-68` 的显式重算路径） |
| 输入法避让 | `overlay/OverlayImeInsets.kt`、`overlay/searchpanel/SearchPanelOverlayWindow.kt` | 输入槽按 `rememberOverlayImeBottomHeight()` 上移 |
| 搜索 | `overlay/searchpanel/SearchPanelOverlayWindow.kt` | 复用其抢焦点 + IME 补发三件套 |
| 提醒推送 | `RemindAlarmScheduler` | 沿用，不占屏 |
| 跨进程数据 | `core/common/util/CrossProcessStore.kt`（`withFileLock` / `notifyChanged` / `registerListener`） | 新增 tags / done 两个独立文件 |
| 手势动作「闪念速记」 | `core/gesture/GestureAction.kt`（50+ 动作体系） | 新增一个动作，用户自行配到触发条/悬浮球 |

---

## 2. 需要真正新写的

| 项 | 说明 | 风险 |
| :- | :- | :- |
| `tags.json`（标签定义） | 独立文件，**绝不写进 `index.json`** | 低 |
| `done.json`（完成状态） | 同上。理由：老版本读到不认识的字段会 ignore 并**整体重写** `index.json`，状态会丢 | 低 |
| 撤销栈 | 存下 / 删除 / 完成 / 改标签 四类可回滚；仅内存栈（进程内） | 低 |
| 搜索（正文 + 标签） | 接入既有搜索面板；搜索时**忽略标签筛选** | 中 |
| 时间轴槽（vline + node） | 每组左侧竖线 + 每条一个节点；**滚动时是否会与 Lazy 虚拟化打架要试** | 中 |
| 图标行规范化 | 48dp 命中区 + 22px 字形 + `marginLeft = -(48-22)/2` 让首图标与正文左对齐 | 低 |
| peek（存下后 1.2s 浮出内容预览） | 独立小 overlay，`pointer-events: none` 等价物 | 低 |
| 空状态（3 种：全空 / 筛不出 / 搜不到） | 各带一个出口 | 低 |
| 页签滑动指示片 + 换页动效 | 指示片 `animateDpAsState`；换页 16dp 位移 + 淡入 | 低 |
| ⋮ 溢浮菜单 | 玻璃 popover，需处理"点外面关掉"与 z-order | 低 |
| 触觉反馈 | 长按 `LONG_PRESS`、存下/复制/删除 `CONFIRM`、过半阈值轻震 | 低（性价比最高） |

---

## 3. 兼容红线（一条都不能破）

1. **`index.json` 顶层结构不变** —— 仍是 `List<StashEntry>`。老版本解析空会**覆盖整个暂存**。
2. **5 个既有外部入口行为不变**，`xgesture://open/stash` 是外部契约（Tasker / 桌面快捷方式）。
   *好消息*：闪念与暂存**彻底合并成一个流**之后，"内部默认只看闪念 / 外部默认含暂存"这个差异**自然消失了**，兼容风险随之消失。
3. **`done` / `tags` 独立文件**，不进 `index.json`。
4. **浮层内禁 miuix `RuntimeShader`**（软件 Canvas 会崩）—— 玻璃只能走 `LocalFrostedGlassBackdrop`。
5. **列表必须 Lazy 虚拟化**（`docs/ui-guidelines.md`），条目渲染走 `RenderRows()` 那套。
6. 不动的：既有剪贴板把手的**用户配置项**（`CLIPBOARD_FLOAT_ENTRY_LONG_PRESS_ACTION_SINGLE_LINE` / `_CARD` 等）语义不能被覆盖。

---

## 4. 必须先定的 5 个决策

### 4.1 ⚠️ 把手与面板是否同一个 window（**最大未知，决定验收标准**）

demo 里"从把手位置跟手拉出面板"是**一个页面内的 transform**，天然连续。
真机上把手在 `HistoryFloatService` 的窗里，面板在 overlay 宿主窗里 —— **跨窗无法共享 transform**。

| 方案 | 效果 | 代价 |
| :- | :- | :- |
| A. 同窗 | 跟手连续，最接近 demo | 把手的常驻性与面板的生命周期耦合，要重构 `HistoryFloatService` |
| B. 跨窗 + 共享状态 60fps 协同 | 接近，但两窗不同帧时可能撕裂 | 需要在两窗间同步一个 `revealProgress` |
| C. 降级为"面板从右缘滑出"，不跟手 | 简单、稳 | 丢掉 demo 里最有手感的一环 |

**必须先读代码确认现状，再选。我倾向先 A/B，C 只当兜底。**

### 4.2 两个把手合并的形态
既有剪贴板把手已经有**可配置的长按动作**（`ClipboardFloatEntryGesturePrefs`）。闪念入口挂上去时：
- 是新增一个"把手模式"配置项，还是把闪念作为既有把手的默认动作之一？
- 用户只想要剪贴板时，闪念 tab 能否隐藏？

### 4.3 面板宽度与页面结构
demo 是 78% 宽 + 左侧 22% 遮罩。既有面板是 **window 宽度 / 2**（`HistoryPanelUi.kt:64-71`）。
> ⚠️ **勘误**：本行原先写"`docs/ui-guidelines.md` 里 50% 是明确的设计"—— **这句话不成立**。P0 勘察通读了 `docs/ui-guidelines.md`（98 行），grep 不到 50% / 面板 / 宽度任何一条。所以改宽度**没有成文规范挡着**，它纯粹是产品决定，不是"违反规范"。

### 4.4 深色模式
demo 是**浅色默认**（你要求的）。App 必须跟随系统。需要把 demo 的两套 token 映射到 miuix colorScheme。

### 4.5 语音（P2）
三个输入面（输入槽 / 面板输入条 / 编辑条）都要有麦克风，且需 `RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE` + FGS 类型 microphone（**当前 manifest 都没有**）。**是否本期做？** 我建议只留入口占位、真的做放下一期。

---

## 5. 分期（每期可独立交付 + 独立验证）

| 期 | 内容 | 交付物 | 验证 | 估时 |
| :- | :- | :- | :- | :- |
| **P0** | **精确代码勘察** | 带 `file:line` 的落位清单；确认 §4.1 的窗口结构 | 读代码 + 写结论；不改代码 | 0.5 天 |
| **P1** | 数据层：`tags.json`、`done.json`、撤销栈、搜索查询 | Repository + 单测 | 单测：往返序列化、老版本 JSON 不被破坏、撤销四类 | 1–1.5 天 |
| **P2** | 把手改造：长按=速记、拖内=面板、点=面板、位置持久化、全屏/横屏隐藏、48dp 排除区随 Y 更新 | Service 改造 + 手动验证 | 真机：返回手势在把手上方/下方仍可用；拖走后旧位置不再吃手势 | 1.5–2 天 |
| **P3** | 面板结构与卡片：搜索行、标签行、时间轴槽、卡片重排（追加块 / 完成态 / 来源）、图标行、⋮ 菜单 | Compose 页面 | 截图对照 demo；Lazy 虚拟化不破；浅色/深色都过 | 3–4 天 |
| **P4** | 动效：跟手拉出/收回、页签指示片、换页、错开淡入、peek、脉冲、flash、触觉 | 动画层 | 真机录屏比对；掉帧检查（jank 统计） | 2–2.5 天 |
| **P5** | 输入面三处统一（输入槽 / 面板输入条 / 编辑条）+ 撤销 + 空状态 + 提醒可见 | 交互闭环 | 逐个走：记一条 / 存错撤销 / 完成 / 删除 / 搜不到 | 1.5–2 天 |
| **P6** | 真机适配与联调：IME 避让、全屏/横屏、无障碍（TalkBack）、深浅色、性能 | 发布候选 | 真机全流程回归 | 2–3 天 |
| | | | **合计** | **12–15 人日** |

---

## 6. demo 里做不到 / 必须换实现的（免得实现者照抄）

| demo 效果 | 真机替代 |
| :- | :- |
| `backdrop-filter: blur()` | `LocalFrostedGlassBackdrop` + `FLAG_BLUR_BEHIND` + `setBlurBehindRadius(px.coerceIn(1,80))`；**浮层内不能用 miuix lens** |
| `.chips` 的 `mask-image` 右缘渐隐 | Compose `Modifier.drawWithContent` + 横向 `Brush` 遮罩 |
| 列表 `stagger` 错开淡入 | **只对首屏**做，`LazyColumn` 里逐项 `animateFloatAsState`；否则会和虚拟化打架 |
| 分组标签 `position: sticky` | `LazyColumn` 的 `stickyHeader` |
| CSS `z-index` 层级 | 交给 `OverlayZOrderCoordinator`，不要自己排序 |
| `pointer-events: none` 的信息层（peek / toast） | 该窗必须 `FLAG_NOT_TOUCHABLE`，否则会挡点击（demo 里 `.scrim` 正是栽在这） |
| 鼠标拖拽滚动（chips 行） | **删掉** —— 那是桌面演示脚手架，Android 靠原生滑动 |

---

## 7. 建议的第一步

**先做 P0（0.5 天，不改代码）**：读透这 6 处，产出一份带 `file:line` 的落位清单，并回答 §4.1。

1. `service/HistoryFloatService.kt` —— 把手的窗、手势占用、显隐时机
2. `overlay/history/HistoryPanelScreen.kt` + `HistoryPanelUi.kt` —— 面板宿主、宽度、页签、进入动效
3. `overlay/history/HistoryEntryCards.kt` + `HistoryEntryCardParts.kt` —— 卡片可改到什么程度
4. `stash/StashRepository.kt` + `core/common/util/CrossProcessStore.kt` —— 数据层扩展点
5. `overlay/compositor/*` —— z-order 与跨窗协同能力（决定 §4.1 选 A 还是 B）
6. `overlay/searchpanel/SearchPanelOverlayWindow.kt` —— 搜索复用面

**P0 之后再动手写代码** —— 否则计划里的估时都是假的。

---

### 0.16.4 已完成（本轮）：条数改总数 / 标签自定义 / 剪贴板筛选 / 单测+文档

**上一轮基线（安全点）**：胶囊 UI 全套 `c8fee4e1`（77 文件）。
**本轮提交**：`0b229969`（17 文件：4 个新源文件 + 2 个新单测；设备上已装机，`crashes` 最新仍是 `crash_20261007_170028.txt`）。
**本轮结果**：`.\gradlew.bat :app:testFullDebugUnitTest` → **108 套 / 610 条 / 0 失败**（基线 106 套 / 592 条；新增 2 套 18 条）。

#### 四件待办 —— 完成情况

1. **搜索框右侧条数改成“总数”** ✅
   - 剪贴板：`ClipboardHistoryStore.count(filter)`（SQL `COUNT`，带筛选）→ `ClipboardHistoryRepository.countEntries` → `HistoryPanelViewModel.clipboardFilterCount` / `clipboardViewCount`。头部那个数字现在是**该筛选下的完整条数**，不再是"已经加载了几页"（原来用 `filteredClipboardEntries.size`，往下滑就变大）。
   - 闪念：**没有**新增 `StashRepository.count()`。列表整份在内存（`MAX_ENTRIES = 200`），`stashEntries.size` 本身就是库总数，加一个"返回它自己"的方法只是死代码；本轮的修法是**把“今天 M”从筛选结果改回全量算**（原来筛标签时 M 会跟着跳）。
   - **有意偏差**：搜索 / 筛标签时显示的是**命中条数**（同样是完整值、不随滚动变），不是库总数 —— 筛出 3 条却写“200 条”只会更困惑；demo 的 `.n` 也是可见条数。
2. **标签可自定义** ✅
   - 数据层：新增 `StashTagEdits`（纯函数 `add/remove/rename/setColor`，返回 `null` = 无变化、不写盘不广播）；`StashMetaRepository` 补上 `renameTag(old,new)` 与 `setTagColor(name,color)`，`addTag/removeTag` 改为委托纯逻辑并返回 `Boolean`。
   - 改名**连带改写所有条目的绑定**（旧名不留孤儿）；目标名已存在时按**合并**处理（并到目标标签、保留目标的颜色与排序），否则 `tags` 里会出现两枚同名 chip。
   - 「待办」= `TODO_TAG_NAME` 关键字：**改名与删除一律拒绝**（改走 = 悄悄废掉"完成"按钮与把手变色；改进来 = 一批普通条目突然变成待办），**改色允许**（颜色不参与关键字判定）。仓库层也拦一道，UI 禁用并给提示。
   - UI：标签行末尾 **＋** 胶囊（`HistoryPanelChipRows.kt` 的 `HistoryTagChips`）→ `HistoryTagManagerModal.kt`，**复用编辑/输入那套壳**（96% 宽·最大 720dp·圆角 28·投影 18·内边距 26·26·26·22）。列表 = 色点 + 名字 + 改名 + 改色 + 删除；新增 = 名字 + 8 个预设色（前 5 个就是 `DEFAULT_TAGS` 的配色，改错了能改回去）。删除带**撤销**：删之前快照"哪些条目挂过它"，撤销时定义与绑定一起放回（`removeTag` 会清绑定，只加回定义是不够的）。
   - 字符串 4 个语言都加了（`stash_tag_*`、`clipboard_filter_*`）。⚠️ 提示里的「待办」**不翻译**：标签名本身是硬编码中文，四种语言下都显示「待办」，翻译了用户对不上号。
3. **剪贴板页签加固定筛选**：`全部 / 图片 / 链接 / 富文本 / 文件` ✅
   - 分类落在**已经持久化的两个轻量列**上（`has_image` / `entry_type`，都是入库时算好、老库升级回填的），**在 SQL 层**做：`ClipboardHistoryStore` 的 `sqlWhere()` 是唯一实现，**头部条数与列表分页共用它**（否则会出现“写了 3 条只列出 2 条”）。四类互斥。
   - 判据：图片 = `has_image = 1`（含"图文混排"）；文件 = `has_image = 0` 且 `entry_type = 'URI'`（`ClipboardReader` 只在"有 content/file 文件"和"有图片"两条路产出 `URI`）；富文本 = `has_image = 0` 且 `entry_type = 'HTML'` **且不是整条链接**；链接 = `has_image = 0` 且 `TEXT/HTML` 且 `search_blob` 以 `http://` / `https://` / `www.` 开头。
   - **推不出来的没硬凑**（两处偏差都写进了 `ClipboardHistoryFilter` 的文档与单测）：① 长文**中间夹带**的链接不算「链接」—— 那要逐条跑正则，只能退回内存过滤"已加载的那几页"，条数又会随滚动变，正是这轮要修的病；② `INTENT`（`intent://`、分享出来的 app 链接）不单列一类，只在「全部」里出现（硬塞进「链接」会让"打开链接"对不上）。另外"纯文本"不属于四类中任何一类，只在「全部」里出现 —— 这四类本来就是"看某一类"，不是分区。
   - UI：与闪念标签行**同一位置、同一套 `HistoryChip`**（`HistoryClipboardFilterChips`）；`HistoryPanelHeader` 的 `tags/selectedTag/showChips` 换成一个 `chipRow` 插槽，两个页签各自组装；筛选状态存 `SavedStateHandle`；**删掉了"剪贴板列表顶部 18dp"那条补偿**（顶部改 2dp，与闪念列表一致）；空状态区分"真的没有记录"（全部筛选下 0 条）与"筛不出来/搜不到"。
   - 搜索时**忽略固定筛选**（与闪念"搜索时忽略标签筛选"同款，见 `filteredStashEntries` 的注释）：搜索结果整批已加载，再叠一层筛选只会让人搜不到刚复制的东西。
4. **单测 + 文档** ✅
   - 新增 `StashTagEditsTest`（8 例，纯 JVM）：改名连带改写绑定 / 合并重名 / 「待办」拒绝改名删除 / 删除清绑定 / 新增去重 / 改色允许。
   - 新增 `ClipboardHistoryStoreFilterTest`（10 例，Robolectric + 真 SQLite）：10 条覆盖各种形态的条目，验证四类分类、**互斥**、`count` 与分页同源、keyset 分页带筛选仍然正确、删除后筛选条数收敛。

#### 上一轮已完成（UI 复刻，提交 `c8fee4e1`）

- **卡片统一**：取消"今天/昨天/更早"分档（用户要求），只保留状态（星标 / 完成）；顶部 1px 高光只画在未星标卡上。
- **编辑弹窗与加号弹窗复用同一套壳**：96% 宽 / 最大 720dp / 圆角 28 / 投影 18 / 内边距 26·26·26·22；顶部信息行 + 多行正文 min160~max320 + 标签行 + 底部整行主按钮。本轮新增的标签管理浮窗**也是这一套**（三块浮窗必须长得一样）。
- **标签选中态** = 实心 accent 底 + 白字 + 白圆点 + 实心描边（淡紫底 + accent 字那版用户说看不清）。
- **轴槽文字**：分组名/时间 11.5sp + 正文色（原来太细太淡，滚起来读不出时间）。
- **提示条**：左右滑消除 + 复位；撤销 4200ms、无撤销 2200ms；挂在**整屏**底部 104dp 居中（不是面板里）。
- **tab 分段控件**：轨道 + 白片 + 3dp 投影 + 同心圆角（滑块四周与轨道同距 3dp，圆角 = 轨道 12 − 3 = 9）。
- **闪念与剪贴板列表内边距**对齐（本轮把剪贴板那条 18dp 补偿删掉后，两边都是顶部 2dp）。

#### 遗留（下一轮可做）

- `HistoryTagFilterRow.kt` 已经是**死代码**（第一版标签行，横向滚动那版被头部里的 `HistoryTagChips` 取代，全仓库无人引用）。这轮没删是为了不扩大改动面，下轮可以直接删掉。
- 标签管理浮窗**没有排序/拖动**（`StashTag.order` 字段在，但只能按新增顺序排）；**没有长按拖动**，也没有"标签使用条数"提示。
- 剪贴板筛选胶囊**不显示各类条数**（要 5 次 SQL COUNT，且胶囊会变宽）；如果以后想要，`countEntries(filter)` 已经现成。
- 拖动面板时那层"雾"仍在（架构固有，见下）。
- 剪贴板"共 N 条记录"页脚在有筛选时显示的是**该筛选**的条数。

#### 环境与协作注意（本轮踩过的坑，务必遵守）

- **编译很慢**：全量 30+ 分钟、增量 2–9 分钟。**合并改动、减少编译次数**。
- **不要并行跑构建**：有一次“停守护进程”把正在跑的编译当场杀掉，导致退出码 1 且零输出。
- **僵尸编译进程**会让每次编译变成 20–30 分钟：`Get-Process java | Sort CPU` 看异常高的（>1000 秒）→ 定向 `Stop-Process`（**只杀 Gradle/Kotlin 的**，按命令行匹配 `GradleDaemon|gradle|kotlin`），必要时 `.\gradlew.bat --stop`。
- **构建输出写日志文件再读**（`*> .tmp\build.log`），**不要用 `Select-String` 过滤**——多次因为过滤把错误行全吞掉，白等好几轮。
- ⚠️ **注释里别写 `image/*` 这种片段**：Kotlin 的块注释**可以嵌套**，KDoc 里出现 `/*` 会开一个永不闭合的注释，编译器只在文件末尾报 `Syntax error: Unclosed comment`（本轮为此白跑了一轮 3 分 47 秒的编译）。写成 "mime 以 `image/` 开头"。
- **不要用子任务改结构**：有一次子任务把 `HistoryPanelScreen.kt` 的大括号改错位就停了，留下编译不过的树；用户明确要求**主任务里做、过程可见**。
- **不要用 `git add -A`**：仓库里有 `.ohc_apk/`、`.ohc_decompiled/`、`.ohc_jadx.log`、`PowerShell 7.6.6/`、`null/`（4000+ 文件）—— 已写进 `.gitignore`，提交时仍应**显式指定路径**（`app/`、`docs/`、`ui_demo*.html`）并用 `git status --porcelain` 复核暂存清单。
- **可能另有 AI 在同一仓库提交**（本轮发生过：对方 `git reset` 把我们的提交摘掉再提交自己的）。提交前先 `git log --oneline -3` + `git reflog -5` 看清 HEAD。
- **设备**：MEIZU 21，`adb` = `C:\Users\syp\AppData\Local\Android\Sdk\platform-tools\adb.exe`，USB 不稳（install 常需重试数轮）。装机后必须查 `adb shell run-as com.slideindex.app ls -lt files/crashes`（正常时最新仍是 `crash_20261007_170028.txt`）。
  ⚠️ §0.16.5 那轮**装到一半设备整个掉了**：`adb devices` 一直为空，`reconnect` / `kill-server`+`start-server` / 等 60s 都没回来 —— 这种情况只能拔插或换线，**别在 adb 上反复重试等它自己好**（APK 已经构建好，插上后一条 `adb install -r -d` 即可）。
  ⚠️ **验"手势返回"要短滑**：边缘**长滑**会先命中我们自己的悬浮球（并弹出搜索面板），**短滑**才是系统返回；
  而且 adb 注入的 `input swipe` 不走系统边缘返回识别（很容易命中自己的悬浮球）—— 这一档只能用手滑，或者先用
  `input keyevent 4` 当代理。快速判断"面板关没关"的客观信号：`adb shell dumpsys window | grep mCurrentFocus`
  （面板在 = 焦点是我们的 `APPLICATION_OVERLAY` 窗；关了 = 焦点回 `MainActivity`）。

#### 已有结论、不要再翻案的设计决定

- 卡片**取消“今天/昨天/更早”分档**（用户明确要求），只保留**状态**：星标 = accent 9% 底 + accent 45% 描边 + 实心蓝星；完成 = 整卡 `alpha .62` + 正文划掉；顶部 1px 高光**只画在未星标卡**上。
- 编辑弹窗与加号弹窗**复用同一套壳**（96% 宽 / 最大 720dp / 圆角 28 / 投影 18 / 内边距 26·26·26·22；顶部信息行 + 多行正文 min160~max320 + 标签行 + 底部整行主按钮）。
- 标签**选中态 = 实心 accent 底 + 白字 + 白圆点 + 实心描边**（淡紫底+accent 字那版用户说看不清）。
- **拖动时的“雾”**：架构固有（面板内自绘磨砂的快照在图层平移时不刷新）。**不要**为此改窗口结构（§0.16.3 那套已被用户打回）。
- **头部不是“内容从顶栏下穿过”的覆盖式顶栏**：它是独立 flex 行（`.head{flex:0 0 auto}`），所以也**没有**顶栏毛玻璃。要收起来只能改尺寸，不能靠平移图层（会把上面那条“雾”招到面板顶上）。

---

### 0.16.5 已完成（本轮追问后的小步）：标签排序数据层 + 列表滚动收起头部搜索行

上一节（§0.16.4）交付后用户问了两个设计问题，结论是"两项都做，但都按最小步子走"：

1. **标签长按拖拽排序** → 本轮**只做数据层**（UI 手势单独一轮）
2. **列表滑动收起顶栏** → 本轮做**收起第一行**（搜索+条数+关闭），页签与筛选行常驻

**本轮结果**：`:app:testFullDebugUnitTest` → **108 套 / 614 条 / 0 失败**（上一轮 610 条，新增 4 条 `move` 用例）；
本轮提交 `e71d2b3a`（6 文件）。

**装机验证（USB 插回后补做，2026-10-08 08:41–08:44）**：APK 已装机，
`run-as … ls -lt files/crashes` 最新仍是 `crash_20261007_170028.txt`、crash buffer 为空、无 FATAL/ANR。
用 `am start -n com.slideindex.app/.service.StashClipboardTrampolineActivity -a com.slideindex.app.action.OPEN_STASH_PANEL`
把面板叫起来（**这是不用手势就能开面板的入口，真机核对很有用**），逐项截图核对：

- **头部收起/展开**：列表下滑后第一行（搜索+条数+关闭）收起、顶部留白明显变窄、`✕` 落到页签行右端、筛选胶囊常驻；滑回顶部自动展开。
- **剪贴板页签**：`全部/图片/链接/富文本/文件` 五枚就位；「全部」显示 **138 条**（库总数 —— 旧代码只会显示已加载的 100 条），切「图片」后 **24 条**（SQL COUNT 带筛选），列表只剩图片；顶部 18dp 补偿确实没了。
- **标签管理浮窗**：标题 + 列表（色点 + 改名 / 改色 / 删除）+ 新增（名字 + 8 个预设色 + 整行主按钮）都在；**「待办」行的铅笔与垃圾桶置灰**并带提示；返回键**先关浮窗**（顺带验证了 §0.16.4 改过的返回键拦截顺序），面板本身照旧不吃返回。
- ⚠️ **只有真机才看得出来的一条**：收起时"条数"也一起藏了（它和搜索框同一行），滑到半截看不到总数。
  想让它常驻的话，下一轮把条数从第一行挪到页签行右端（和 `✕` 并排）即可，改动很小。已记进下面的"遗留"。

**装机过程中的插曲**：第一次 `adb install` 走到一半设备整个掉了（`adb devices` 一直为空，
`reconnect` / `kill-server`+`start-server` / 等 60s 都没回来），插回数据线后一次 `install -r -d` 即成功。

#### 1. 标签排序的数据层（UI 未接）

- 新增 `StashTagEdits.move(store, name, targetIndex)`（纯函数）+ `StashMetaRepository.moveTag(name, index)`。
- 语义（都钉在 `StashTagEditsTest` 里）：
  - 先按 `order` 排好再动，**原子重写 0..n-1**。只改被移动那一枚的 order 会留下重复/空洞的权重（两枚都是 2），之后 `sortedBy` 的先后变成实现细节 —— UI 上就是"拖了没动 / 顺序自己跳"；顺带**自愈**历史遗留的脏 order。
  - `targetIndex` 越界夹到两端；夹完等于原位 = `null`（不写盘不广播）。
  - 「待办」**可以移动**：只读只针对改名与删除，位置不影响 `pendingTodoCount` 的关键字语义。
- **为什么 UI 拖拽留到下一轮**：现在的标签列表是 `Column + verticalScroll`，拖拽要换 `LazyColumn` + `detectDragGesturesAfterLongPress` + 抬起项偏移 + 边缘自动滚动 + 落下动画，还要保证长按只落在"行体"上（右侧三个 34dp 图标按钮不能被抢手势）。数据层先落地，UI 那轮只需算目标下标。
  - 若不想写手势，退一步用上下箭头按钮接 `moveTag` 即可（观感差些，但零手势风险）。

#### 2. 列表滚动收起头部第一行（`HistoryPanelHeader(collapsed = …)`）

- **收什么**：第一行（搜索框 + 条数 + 关闭）整行收起，顶部留白 40 → 10dp，页签行上间距 14 → 8dp。
  头部高度约 **178dp → 94dp**（省下近一半），面板宽只有 ~320dp，这 84dp 是实打实的列表空间。
- **不收什么**：页签（导航）与筛选胶囊（"我现在筛着什么"的状态指示）常驻 —— 收起来用户就不知道列表为什么这么短。
- **关闭键不丢**：收起时它挪到页签行右端（同一个 `✕`、同一条右边缘，尺寸 34dp 与页签行等高），
  所以"滑到半截想关面板"不用去点空白或按返回键。
- **收起条件**（宿主 `HistoryPanelScreen` 判断，不只是"滑了就收"）：
  `listScrolled && activeSearchQuery.isBlank() && !searchFocused`
  —— 有查询词时那颗词要一直看得见；**输入框聚焦时绝不能收**（收起会把它从组合里摘掉，输入法当场掉）。
- **信号从哪来**：两个 tab body 各自 `snapshotFlow { firstVisibleItemIndex > 0 || offset > 16dp }` 上报，
  **只在"这一页是当前页"时上报**（否则两页会互相打架）。`LazyListState` 仍留在各自 body 里：
  它是 load-more 判断、新条目回顶、首屏错开淡入的锚点，搬出来动的地方比这个功能本身还多。
- **实现纪律**：高度变化一律走**改尺寸**（`animateDpAsState` 的 padding + `AnimatedVisibility` 的
  `expand/shrinkVertically`），**不用 `graphicsLayer` 平移** —— 平移会让面板那层自绘磨砂的快照不刷新，
  顶上立刻出现"雾"（§0.16.4 已有结论那条）。
- **行为代价（已知、可接受）**：收起后要搜东西得先滑回顶部（标准折叠顶栏行为）；滑回顶部即自动展开。

#### 遗留

- ~~标签拖拽 UI 未接（见上）；`moveTag` 目前只有单测在用。~~ ✅ **§0.16.6 已接上真机拖拽**（用户自验通过）。
- 收起/展开是**阈值触发**（16dp），没有做"下滑才收、上滑立刻展"的方向联动 —— 那需要把两个
  `LazyListState` 提到 `HistoryPanelScreen`，等真有人觉得现在这套别扭再动。
- **收起时"条数"跟着一起藏了**（真机核对时发现）：条数与搜索框同行，想让它在滚动时也看得见，
  把它挪到页签行右端、和收起态的 `✕` 并排即可（小改）。

---

### 0.16.6 已完成（本轮）：三件收尾 —— 卡片分隔线 / FAB 位置 / 返回键 / 标签拖拽

用户看完 §0.16.4–0.16.5 的问答后拍板"全做"，于是把攒下的四件事一次做完（轮 1+2 一次编译验完，轮 3 单独一轮）。

**本轮结果**：`:app:testFullDebugUnitTest` → **108 套 / 614 条 / 0 失败**（本轮无新增单测：轮 1 是替换、
轮 2 是窗口 flag、轮 3 的手势只能真机验；`moveTag` 的 4 条用例在 §0.16.5 已加）。
APK 已构建并装机，`crashes` 最新仍是 `crash_20261007_170028.txt`、crash buffer 为空。

**交互验证（真机核对）待补**：装机那一刻用户正在用手机（前台是 QQ），**没有在他人的应用上乱点** ——
返回键 / 拖拽 / FAB 三项的截图核对留到设备空出来时补做，结果追加在本节末尾。

#### 轮 1：两处观感/手感小修 + 一次清理

1. **删掉剪贴板卡片正文与操作行之间那条浅横线**（`theme.hair` 的发丝线）。
   - 它是 `HistoryEntryCardShell` 的 `showActionDivider`（默认 `true`）；闪念卡片早就显式传 `false` 并注释"设计稿的卡片里没有它"，剪贴板卡片却一直用默认值 —— 同一套 shell 两种观感。
   - 核过 demo：`clipItemHtml` 与 `itemHtml` 都没有这条线，`.acts` 只有 `margin-top:2px`（整份 HTML 里唯一带上下分隔线的是 `.editbar .acts`，那是就地编辑条）。
   - 改法：**把参数整个删掉**（只有 2 个调用点），杜绝以后再分叉。去掉后操作行与正文只隔 2dp —— 真机觉得挤的话，加的是**操作行上间距**，不是把线加回来。
2. **FAB 不再贴屏幕底边**：`HistoryComposerFab` 原先只有 `padding(end = 16.dp)`、底部偏移是 0，
   截图里能看到它**贴着屏幕底边、右下角被裁**，一半压在系统 home 手势区里（设计稿 `.fab` 是 `right:16px; bottom:92px`）。
   现在给 `bottom = 32.dp` —— 不照抄 92px，那是"底部输入条"时代的坐标（输入条现在已是屏幕居中浮窗）。
   同时把闪念列表尾部留白 `HistoryListFooterPadding` 64 → **96dp**（54 FAB + 32 偏移 + 10 余量），
   否则最后一张卡的 ⋮ 会被 FAB 压住。剪贴板页**没有 FAB**（`composerVisible` 只在闪念页为真，真机截图核对过），不用改。
3. **删掉死代码 `HistoryTagFilterRow.kt`**（第一版标签行，被 `HistoryPanelChipRows.kt` 取代后全仓库无人引用）。

#### 轮 2：面板响应返回键 / 手势返回（功能性缺陷）

- **病因**（真机验了两遍）：浏览态下面板窗是 `FLAG_NOT_FOCUSABLE`（`ensurePanelNonFocusable()` 的注释写着"不抢底层 App 焦点/输入法"），
  **而 NOT_FOCUSABLE 的窗口收不到系统返回派发** → 返回落到下面那个 App 上（"面板还在、底下的界面退了"）；
  只有浮窗/输入法把窗口临时切成可聚焦时，`handlePanelBack()` 才吃得到返回。宿主逻辑本身没问题
  （`handlePanelBack` = 先问浮窗拦截器 → clipboard 输入态 → `dismiss()`），manifest 也开了 `enableOnBackInvokedCallback`。
- **改法**：浏览态改成**可聚焦 + `FLAG_ALT_FOCUSABLE_IM`** —— 这个 flag 的语义正是"可聚焦，但不与输入法交互"：
  我们收得到返回，**但不把输入法抢过来**。要打字时仍走原来的 `activatePanelInputFocus()`（清掉该 flag、把输入法指向面板）。
  函数 `ensurePanelNonFocusable()` → `ensurePanelBrowsingInput()`，调用点两处（面板显示、剪贴板输入态收尾）；
  返回处理器改为 `attach(requestViewFocus = true)` 并在窗口变可聚焦后 `refresh()` 重新注册。
- **回退预案**：真机若出现"打开面板就弹输入法 / 点面板外不再穿透 / 打字输入法不对"，立即把这一处改回 NOT_FOCUSABLE，
  并在本节记下原因（这属于 §0.16.3 被用户打回过的窗口结构区，不能硬来）。
- 设备是 **Android 16（SDK 36）**：`OverlayViewBackHandler` 在"predictive back 开启"时走 `OnBackInvokedCallback`，
  否则走旧版 `OnUnhandledKeyEventListener`（两条路**都要求窗口可聚焦**，所以这个改动对两条路都有效）。

#### 轮 3：标签长按拖拽排序（数据层 §0.16.5 已就绪）

- 标签列表 `Column + verticalScroll` → **`LazyColumn`**（`key = 标签名`），加 `detectDragGesturesAfterLongPress`。
- **本地实时换位**：越过半行就把本地顺序换一格、同时把手指位移反着补回半行（行跟着手指走），
  落下时把最终下标交给 `moveTag` —— 它的语义（移除后插到第 N 位）与拖拽过程每一步一致，
  所以"看着落在哪"就是"落盘落在哪"。
- 两个坑按计划绕开：
  - **行高必须统一**（落点是按行高算的）：把「待办」只读提示从行内挪到**列表下方一行脚注**；
    改色色板也从行内挪到**列表下方**（带"改色：<标签名>"标题）—— 顺带行内不再有高度突变。
  - **状态用 holder 装**（`HistoryTagDragState`，`remember` 出来的稳定对象）：`pointerInput` 的 lambda 只在 key 变化时重建，
    捕获普通 local var 会读到旧实例；holder 里另存一份**拖拽开始时的顺序快照**用来判断"到底动没动"。
- 已知小瑕疵（有意留着、写在这儿）：拖到列表最上/最下时把位移**夹在半行以内**（否则那一行会被 LazyColumn 裁掉）；
  标签超过 6 行（列表可滚）时**拖到边缘不会自动滚动**。
- 验证手段：`adb shell input draganddrop`（或 `input motionevent` 手动序列）拖完截图核对顺序，并确认 `stash_meta.json` 的 order 真的重排。

#### 本轮遗留

- 标签拖拽没有边缘自动滚动（>6 个标签时）。
- ~~面板"点面板外关窗"与输入法在轮 2 的可聚焦改动后需要真机再确认一遍（见回退预案）。~~
  ✅ **已确认**（§0.16.7 真机结论：短滑手势关面板；点空白关面板一路也在用）；唯一没专门验的是
  轮 2 验收 ② 的"点面板外**穿透到底层 App 且不误触**"——日常使用未见问题（见 §0.3.1）。

---

### 0.16.7 已完成：键盘弹着时"返回先收键盘"（下沉到所有浮窗共用的返回入口）

**问题（用户实测）**：在编辑弹窗 / 加号弹窗 / 编辑标签弹窗里弹出键盘后，**手势返回直接把弹窗关了，而不是先收起键盘**。

**病因**：`OverlaySidePanelHost.handlePanelBack()` 的顺序是「先问面板里哪一层开着（拦截器）→ 再收输入态 → 最后关面板」，
而拦截器只按"哪一层开着"收，**完全不看键盘弹没弹**。普通 App 里这一档本来是**输入法自己吃掉的**
（IME 有返回回调），但我们这些浮窗是 `TYPE_*_OVERLAY`，且应用级 predictive-back 关掉时返回走
"注入 `KEYCODE_BACK`" 的兼容路径 —— 返回键**直接注给聚焦的那个浮窗**，输入法没机会先吃。

**改法（用户选的"档 2"：一次把同类浮窗都修好）**：判断与处理放进 `OverlayViewBackHandler.dispatchBack()`
—— 这是**所有浮窗收到返回的唯一漏斗**（`OnBackInvokedCallback` 与旧版 unhandled-key 两条路都汇到这里）：

```kotlin
if (hideImeAndConsumeBack()) return   // 键盘弹着 → 这一次返回归键盘
onBack()
```

- **检测**（两步，与 `OverlayImeInsets` 同一套）：① 窗口 insets 直接报 IME 高度 → 最权威；
  ② 覆盖窗上 `WindowInsets.ime` 常常是 0，退回"可见区域比根视图矮一截"。
  ⚠️ ② 的门槛**不能是 `>0`** —— 手势条/导航栏本身就会占掉几十 px，那样键盘没弹也会被误判成"弹着"，
  把第一下返回吞掉（表现成"按返回没反应"）。取根视图高度的 **15%**（键盘 ~30-45%、手势条 2-5%）。
- **只收键盘、绝不碰焦点**（`hideSoftInputFromWindow(windowToken, 0)`，与 `MessageReplyOverlayWindow.hideIme`
  同一套写法）：焦点一丢，窗口会回到"不与输入法交互"的状态，而面板的输入态是靠状态变化驱动的，
  再点同一个输入框键盘可能就弹不出来。

**影响范围（14 处浮窗，只在"键盘弹着时按返回"这一种情况下行为才变）**：

| 浮窗（用户可见名称 / 触发路径） | 有输入框 | 变化 |
| --- | --- | --- |
| 收纳面板（手势动作「打开暂存夹」/「打开剪贴板」、右侧把手；三个弹窗：记一条 / 编辑 / 标签） | ✅ | 键盘弹着：①收键盘 ②关弹窗 |
| 闪念输入槽（长按右侧把手） | ✅ | 同上 |
| 剪贴板小窗（手势动作「剪贴板小窗」） | ✅ | 同上 |
| 取词面板（悬浮球悬停取词 /「全屏截图取词」/「区域截图&取词」/「剪贴板取词」） | ✅ | 同上 |
| 消息快捷回复（通知上的"回复"） | ✅ | 同上 |
| 图片搜索（取词面板里点图片搜索，输入在 WebView 里） | ✅ | 同上 |
| 扩展面板（手势动作「扩展面板」= 音量/亮度） | 子页面有搜索框 | 有键盘时同上 |
| 冰箱（「冰箱」）/ 小组件弹出窗口（「小组件弹出窗口」）/ 小组件选择器（小组件面板里「添加小组件」）/ 屏幕翻译（「屏幕翻译（实验性）」）/ 通知面板与通知详情（消息浮窗） | ❌ | **零变化**（条件不成立就不走新分支） |

**没被这次改动碰到的**：搜索面板（`SearchPanelOverlayWindow` 自己 `dispatchKeyEvent`）、
无障碍手势注入那条返回（`SlideIndexAccessibilityGestureInjector`，它有自己的剪贴板小窗特例）、
以及各 Activity（不是覆盖窗）。

**验收清单（真机；"键盘弹着"的判据是 `adb shell dumpsys input_method | grep mInputShown`）**：

| # | 场景与触发路径 | 期望 |
| --- | --- | --- |
| 1 | 收纳面板 → 标签行末尾「＋」→ 点「标签名」输入框（键盘弹着）→ 返回 | ①收键盘、浮窗还在 ②再返回关浮窗 ✅已验 |
| 2 | 收纳面板 → 右下角 ＋（记一条，自动弹键盘）→ 返回 | ①收键盘、弹窗还在 ②再返回关弹窗 ✅已验 |
| 3 | 收纳面板 → 闪念卡片 ⋮ → 编辑（自动弹键盘）→ 返回 | ①收键盘、编辑条还在 ②再返回关编辑条 |
| 4 | 长按右侧把手（闪念输入槽，自动弹键盘）→ 返回 | ①收键盘、输入槽还在 ②再返回关输入槽 |
| 5 | 手势动作「剪贴板小窗」→ 搜索框聚焦弹键盘 → 返回 | ①收键盘、小窗还在 ②再返回关小窗 |
| 6 | 取词面板（悬浮球悬停取词 /「全屏截图取词」/「区域截图&amp;取词」/「剪贴板取词」）→ 搜索框弹键盘 → 返回 | ①收键盘、面板还在 ②再返回关面板 |
| 7 | 取词面板 → 图片 → 图片搜索（输入在网页里）→ 键盘弹着 → 返回 | ①收键盘 ②再返回关图片搜索 |
| 8 | 手势动作「扩展面板」→ 子页面搜索框 → 键盘弹着 → 返回 | ①收键盘 ②再返回 |
| 9 | 通知上的「回复」（消息快捷回复）→ 键盘弹着 → 返回 | ①收键盘 ②再返回关回复浮窗 |
| 10 | 无输入框的（冰箱 / 小组件弹出窗口 / 小组件选择器 / 屏幕翻译 / 通知面板与详情） | **行为不变**：一次返回就关 |
| 11 | 空搜索框聚焦（键盘弹着、没打字）→ 返回 | ①收键盘 ②退出输入态（光标消失）③关面板 —— 三次；嫌多的话下一轮让"空搜索框 + 返回"直接关面板 |

⚠️ 手势返回**没法用 adb 模拟**（注入的 swipe 不会走 SystemUI 的边缘手势识别），清单里 1–11 都要用手指划；
`input keyevent 4` 走同一条返回路径，可以先当代理快速摸底。

**真机结论（用户指正后补验）**：

- 用户实测指正：边缘**长滑**会先命中**我们自己的悬浮球**（还会弹出搜索面板），**短滑才是系统返回**。
  我先前用 `input swipe 8 1200 420 1200 250`（长滑）去验"手势返回"，命中的是自己的悬浮球 —— 这就是
  §0.16.6 里"手势返回没反应"那次误判的真正原因（不只是 adb 注入的限制）。
- 按这个改法补验（客观信号：`dumpsys window | grep mCurrentFocus`）：面板打开时焦点是覆盖窗
  `Window{…com.slideindex.app}`；左边缘**短滑**（`input swipe 8 1200 130 1200 120`）之后焦点回到
  `com.slideindex.app/.MainActivity` → **面板关掉了 ✅**（真手势链路上，键盘未弹的那一档也通了）。

---

### 0.16.8 已完成：横屏面板宽度上限（420dp）+ 无障碍补标签

**问题（用户看横屏截图后反馈）**：横屏下面板也是窗口宽的 78% → 2340px 的屏幕上占 1825px，**几乎铺满**；
用户要求"横屏就正常小半个屏幕"。

**改法**：`panelWidthOf(available)` 从"只有比例"改成**比例 + 上限**：

```kotlin
internal fun panelWidthOf(available: Dp): Dp = minOf(available * 0.78f, PANEL_WIDTH_MAX)  // 420dp
```

- 竖屏 411dp × 78% = **320dp**，够不着上限 → **与设计稿完全一致，肖像行为零变化**；
- 横屏 891dp × 78% = 695dp → 被收到 **420dp ≈ 47%**（"小半个屏幕"）；
- 平板/折叠屏展开态同理受益（侧栏不该随屏宽线性变胖）。
- `historyPreviewWidthPx()`（图片解码目标宽度）也改成走同一个算式，免得解码宽度和真实面板宽度跑偏。
- 顺手删掉 `panelWidthOf` 上面那段**错误的老注释**（它写着"已废弃、不要再拿去算宽度"，是 §0.16.3
  "窗口改 78% 宽"那次实验的残留 —— 而 §0.16.3 已被整批回退，现在窗口满屏、面板内容由这个函数算宽度，
  它就是生产路径）。

**无障碍补标签**（P7 里那一小块，静态审计的结论比预想的小）：
- 卡片动作行 / ⋮ 菜单 / 页签 / 胶囊**本来就有标签**（`clipboard_history_float_copy`、`moreLabel`、
  `stash_action_open_pick` …），我一开始只看 `contentDescription = null` 的计数，**高估了缺口**；
- 真正没标签的是两处：**面板头部关闭 `✕`**（展开态与收起态各一个）→ 补 `panel_close`；
  **搜索框「清空」`✕`** → 补 `stash_empty_search_action`；
- 剩下传 `null` 的都是装饰性图标（搜索放大镜、时间轴节点、空状态箭头、内容块里的图片）。

**同一轮订正的一处误报**：§0.3.2 里我写过"缺 2 个翻译键、建议补" —— 复查发现那两个键都是
`translatable="false"`（一个是空串占位、一个是 Google 的包名/Activity 名），**本就不该翻译**，
我第一版审计脚本没看 `translatable` 属性。已在 §0.3.2 里订正。
**真机验证**：装机后横屏截图核对 —— 面板现在只占右侧约一半（"小半个屏幕"），头部/页签/胶囊行/
时间轴/卡片/FAB 都在位；竖屏由算式保证不变（411dp × 78% = 320dp < 420dp 上限，未再截图）。
`crashes` 最新仍是 `crash_20261007_170028.txt`。

**⚠️ 这轮踩到的一个新坑（写进协作注意）**：**别用 `adb shell cmd locale set-app-locales` 切这个 App 的语言**。
App 会把"当前语言"**持久化进它自己的设置**（`app_ui_language_tag`，落盘在
`shared_prefs/app_locale_cache.xml` + DataStore）—— 我把 per-app locale 设成 `en-US` 之后，
清掉系统 per-app locale（`--locales ""`）也回不来，App 反而把 `en` 又写回系统，**必须用户去
「交互与外观 → 应用语言」手动改回**。要验英文/日文排版，请走 App 内那个设置、或让用户自己切。

---

### 0.16.9 已完成：弹窗"点内部也关闭"的修复 + 提醒时间选择器 + 加号弹窗 ⏰ 胶囊

用户提的三个问题，按我的建议一次做完：

**① 点在弹窗内部也会把弹窗关掉（真 bug）**

- 病因：三块浮窗（记一条 / 编辑条 / 标签）的**卡片本体**只有 `background` + `border` + `padding`，
  **没有任何消费触摸的节点** —— 点在卡片空白处（内边距、行间距、纯文字区、行内非按钮部分）会**穿到
  下面那层"点空白关闭"的遮罩**上，于是弹窗被关。Compose 里没有 pointerInput/clickable 的节点不吃事件。
- 有意思的是**面板自己早就处理过**：面板根节点挂着一个空的 `.clickable {}`（只消费），所以点面板内不关面板。
  三块浮窗当时漏了这一步。
- 修法：新增 `Modifier.historyConsumeTaps()`（空 `clickable`，只消费、不做别的），挂在三块浮窗的卡片本体上。
- 真机验证：点标签浮窗卡片内空白处（标题与首行之间）→ **浮窗保持打开 ✅**；点卡片外 → 照旧关闭 ✅。

**② 提醒时间选择器（原来只有"明天 09:00"一档，用户改不了时间）**

- 新增 `HistoryReminderPickerModal`（与其它浮窗同一套居中卡片壳）：**5 / 15 / 30 分钟后 · 1 / 3 小时后 ·
  明天 09:00**，已设提醒时多一枚**「清除提醒」**，并把当前值显示成「当前：…」。
- 打开它的两个入口：**编辑条的「提醒」**（不再"一点就明天 09:00、再点清掉"）与**加号弹窗的 ⏰ 胶囊**。
- 细节：选择器开着时**其它浮窗不渲染**（它们全是"屏幕居中卡片"，叠在一起会糊成一片）；返回键与遮罩点击都能收起它；
  "从现在起 N 分钟"这类锚点在**打开那一刻**算好并冻结（否则浮窗开着不动、值会一直往后漂）；
  只有"明天 09:00"参与回选高亮（相对时间每次都新算，回选没有意义）。
- 撤销：设/清提醒都进撤销条（撤销 = 把原时间设回去 + 重排/取消闹钟）。
- ⚠️ **仍缺"任意日期/时间"**：overlay 里塞 M3 的 DatePicker/TimePicker（约 400dp 高）在 320dp 宽的面板里会溢出，
  还要处理时区与"选到过去时间"的校验 —— 单独一轮再做。已记进遗留。

**③ 加号弹窗的 ⏰ 胶囊（按建议：一键、不新起一行）**

- 位置：**和标签胶囊同一行**（用户建议的"行尾"），标签为空时它也照样在。
- 行为：点开**同一个**选择器；选到的时间先记在本地（`composerReminderAt`），**存下拿到 newEntryId 之后**
  再写 meta + 排闹钟（与 `composerTags` 同款落法）；关弹窗/存下都会清空。
- 真机验证：胶囊显示「⏰ 提醒」→ 选「明天」→ 变成**实心选中的「⏰ 10月9日 09:00」**，条数不变（没提交新条目）✅。

**④ 用户问的三个设计问题 —— 按建议"保持现状"**

- `remind` **不**限制在「待办」条目上：「待办」是任务状态（决定主操作是"完成"还是"星标"、决定把手是否变色），
  提醒是时间触发器，两者正交；限制只会逼用户多打一个标签。
- 设了 remind **不**自动加「待办」标签：静默改标签会连带改主操作、把手变色、标签行与筛选结果 ——
  只想"让它响一下"却整条变成任务。真想要耦合就做成**显式勾选**（默认勾上、可取消），不要偷偷加。

**新字符串**（4 个 × 4 语言）：`stash_remind_in_minutes` / `stash_remind_in_hours` / `stash_remind_clear` / `stash_remind_current`。

**这一轮的构建事故（写进下面的协作注意）**：先撞上"另一个 AI 同时在跑 Gradle"（ASM 转换拿不到被对方重写的
class 属性 → 失败），改成"等空档再编译"；随后 `compileFullDebugKotlin` **日志 35 分钟不增长但 Kotlin 守护进程
一直在烧 CPU（3 核）** —— 我误判成僵尸进程把它杀了，结果 Gradle 守护进程卡死等了 50 分钟。
**判据：日志不动但 CPU 在烧 = 正常慢编译（这台机器全量 55 分钟）；只有 CPU 也停了才算卡死。**

---

### 0.16.10 已完成：提醒「自定义时间」滚轮（对齐用户给的参考 App）

用户拿参考 App（`com.moting.floatwidget`）的三张截图指出：**"你的时间还是不够自定义"** ——
参考 App 的提醒是「档位（15 分钟后 / 1 小时后 / 今天 17:00 / 明天 07:00）+ 自定义时间…」，
点「自定义时间…」进**年/月/日 + 时:分 滚轮**，顶部「← 档位 … 确定」。

**改法**（`HistoryReminderPicker.kt`，两种形态同在一张卡片里）：

- **档位形态**：5 / 15 / 30 分钟后 · 1 / 3 小时后 · **今天 17:00** · **明天 07:00** · **明天 09:00**
  （后三枚是固定整点，**已经过去的档位不显示**）+ 一枚「自定义时间…」+（已设时）「清除提醒」。
- **自定义形态**：顶部 `← 档位` /（中间是**选中时间的实时预览**）/ `确定`，下面 **日期 · 时 · 分** 三个滚轮；
  日期轮是"今天 / 明天 / `M月d日`"（未来 60 天），分钟按 5 分钟一档。
- 滚轮实现：`LazyColumn` + `rememberSnapFlingBehavior`（Compose BOM 2026.09 自带，项目里首次用）；
  选中项 = **最靠近视口中心的那一行**（视口 3 行 + 上下各留 1 行 padding 正好让选中项停在正中），
  中间那行画一条 `fieldBg` 底作为"选中的就是这一行"的线索。
  ⚠️ 踩过的坑：别用 `firstVisibleItemIndex + 半个偏移` 推选中项 —— 静止时 `firstVisibleItemIndex`
  本身就是选中项，那个公式会额外 +1（代码注释里记了）。
- "选到过去"的处理：档位不显示；自定义模式里若选到过去，点`确定`时**挪到 1 分钟后**（别设一个永远不响的提醒）。
- 新字符串 4 个 × 4 语言：`stash_remind_custom` / `stash_remind_presets` / `stash_remind_confirm` / `stash_remind_custom_hint`。

**参考 App 里还有、本轮不做的一件**：**「＋ 添加档位」**（用户把常用时间存成自己的档位）——
它要新开一份"用户档位"的持久化（跟 `app_ui_language_tag` 一样进 DataStore），是这块唯一的跨会话数据，单独一轮做。

### 0.16.11 待做（下一轮）：加号弹窗「加图片，且多张」

用户在同一条消息里提的（参考 App 的加号弹窗有 🖼 / 📁 / 🔔 / ↑ 四个按钮，图片可以多选）。

**为什么单独一轮**：**overlay 窗不能直接拉起系统选图** —— `rememberLauncherForActivityResult` 需要一个
`ActivityResultRegistryOwner`，而 overlay 的 Compose 树里没有（项目里所有 `rememberLauncherForActivityResult`
都在 `app/src/main/java/com/slideindex/app/ui/**` 的 Activity 里，overlay 侧一个都没有）。所以 overlay 里的选图
一律走 **trampoline Activity**（现成的有 `PinImagePickerTrampolineActivity`（单选）、`SearchPanelImagePickerActivity`）。

**方案（沿用 trampoline 那条既有链路）**：

1. 新增/扩展一个 **多选** trampoline（`PickMultipleVisualMedia`，带 `maxItems`），把选中的 `List<Uri>` 通过
   现有的结果回传通道交回 overlay（与钉屏选图同款）；
2. 记一条弹窗里加一排**缩略图 + 删除**（多张，未存下前只存在本地 state，和 `composerTags` / `composerReminderAt` 一样）；
3. 「存下」时按 `StashCoordinator` 的图片入口**一次写一条带多个图片块的条目**（条目本身已支持多图：
   `selectedImageIndices`、卡片里的图片块都是现成的），失败/撤销沿用 `showUndoMessage { delete(newEntryId) }`；
4. 拿不到读图权限时按现有 `StashClipboardSettingsScreen` 的媒体权限流程提示（别静默失败）。

**顺序**：先把本轮（自定义时间）验完提交，再做这条 —— 它要新 Activity + 清单 + 回传通道，改动面比提醒大得多。

---

### 0.16.12 已完成：分钟滚轮改 1 分钟一档 + 加号弹窗「加图片（多张）」

**① 分钟滚轮 1 分钟一档**（用户："5 分钟一个档位 666"）：`HistoryReminderMinuteStep` 5 → 1（60 档），
提示文案去掉"5 分钟一档"（4 语言）。

**② 加号弹窗加图片、且多张**（§0.16.11 的方案，已落地）：

- 新增 `StashComposerImageTrampolineActivity`：`PickMultipleVisualMedia`（最多 9 张，**系统相册多选、不需要读图权限**，
  没有照片选择器时自动回退 `ACTION_OPEN_DOCUMENT`）。overlay 侧用不了 `rememberLauncherForActivityResult`
  （Compose 树里没有 `ActivityResultRegistryOwner`）—— **全 App 是单进程**（清单里没有任何 `android:process`），
  所以直接复用现成的 `TrampolineResultPort`（token 注册回调 + `deliver`）回传。
- ⚠️ **在 trampoline 里就把图解码（长边 ≤2048）落成 App cache 里的 JPEG，只回传路径**：相册选择器给的 URI
  读权限绑在发起 Activity 的生命周期上，`finish()` 后再读可能 EACCES；这样做也绕开"Bundle 塞不下大图"。
- UI：加号弹窗里在标签行**上面**多一排「＋ 图片」胶囊 + 已选缩略图（64dp，右上角 ✕ 逐张删；
  缩略图 160px 采样 + `remember(path)` 缓存）。存下走 `addRich`（**一条多图** —— `StashRepository.addRich`
  早就支持多图块，命名 `id_0.png`/`id_1.png`），文字非空时作为第一块 `StashRichPart.Text`；
  标签 / 提醒 / 撤销沿用同一条路径（给 `StashCoordinator.addRich` 补了 `onSaved` 回调拿新条目 id）。
  成功后清空 state + 删 cache 临时图；关弹窗不存也删。
- **真机已验证**：胶囊 → 系统相册**多选**（有选择圈、底部「添加 N 项」）→ 选中 2 张 → 添加 →
  **`cache/stash_composer_images` 里确实落了 2 张 jpg（52KB / 45KB）** ✔。
- ⚠️ **没走完的一段（接手先看）**：缩略图渲染 + "一条多图入库"没在真机上看完 —— 从 adb 拉起选择器之后，
  面板/overlay 没能回来（焦点停在桌面 / MainActivity），草稿状态随之丢了。**需要用户手点一次**：
  面板 → ＋图片 → 选 2 张 → 添加 → 看缩略图 → 存下。
  如果真机上"从选择器回来草稿也没了"，那就得把草稿（`composerImagePaths` / `composerText` 等 Compose state）
  提到 ViewModel 或静态量 —— **这一条要专门确认**。
- 新字符串 `stash_composer_image_add`（＋ 图片）4 语言。

**同类坑（这轮踩到的）**：KDoc 里写了 `ui/**` —— `/**` 在 KDoc 内会**再开一层块注释**导致
`Syntax error: Unclosed comment`（和之前 `image/*` 那次同一个病）。**注释里别写带 `/*` 的 glob**。

**构建**：`assembleFullDebug` 一次过（上一次是上面那个注释坑），单测 **108 套 / 615 条 / 0 失败**。

---

### 0.16.13 事故：拉系统相册之后"收纳面板打不开了"（已恢复；根因**待修**）

**现象**（用户报的）：点「＋ 图片」→ 系统相册 → 选完图回来之后，面板就不显示了；再点悬浮球 /
从 adb 发 `OPEN_STASH_PANEL` 都打不开（`am start` 只说"已有 task 被提到前台"，面板窗始终不显示）。

**诊断（客观信号，全都没问题）**：无障碍服务在 `enabled_accessibility_services` 里、开关为 1；
`OverlayService`（前台）/`HistoryFloatService`/`SlideIndexAccessibilityService` 全在跑；`files/crashes` 无新文件；
`dumpsys accessibility` 的 `Crashed services:{}`。
**真正的问题在窗口层**：`dumpsys window windows` 里**悬浮球与左右边缘把手都 `isOnScreen=true`**，但
**面板窗是 `mViewVisibility=0x8`（GONE）+ `mHasSurface=false`** —— 面板被"藏住且状态卡住"：
宿主多半仍以为自己正在显示，于是后续的 toggle 全成了空操作（点了没反应）。

**恢复办法（已验证）**：`adb shell am force-stop com.slideindex.app` + 重开 App（服务重绑）→ 面板正常 ✔。
用户侧等价操作：设置里强制停止 XGesture，或重启手机。

**待修（下一轮第一件）**：面板宿主必须在"面板窗被系统隐藏 / 被别的 task 顶掉"时**复位自己的 visible 状态**
（或主动 hide 一次再允许打开）。否则**任何"面板开着时切到别的 App/Activity"都可能复现** ——
我的选图功能必须 `startActivity`（系统相册），于是把它变成了必现路径。

**顺带要确认的**：从选择器回来时草稿（`composerImagePaths` / `composerText` / 标签 / 提醒）还在不在。
若一起丢了，就得把草稿从 Compose state 提到 ViewModel 或静态量。

#### 0.16.13.1 修复（已提交、已装机验证）：detach 时复位宿主

根因（代码层坐实的）：系统把我们的覆盖窗摘掉时，ComposeView 会 `onViewDetachedFromWindow`，但
`OverlayFullScreenPanelHost` **不清 `composeViewRef`** → `isAttached` / `isViewVisible()` 永远为 true →
`OverlaySidePanelHost.isUserVisible` 为 true → `FloatBallStashPanel` 的开关判定"已经开着"，
于是**再点只会走 dismiss**，而且 `show()` 里 `if (panelHost.isAttached)` 那条分支是对着一个**已经不存在的窗口**
设可见性 —— 面板自然再也不出来，只能强停 App。

改法：`OverlayFullScreenPanelHost` 加 `onViewDetached` 回调 + `addOnAttachStateChangeListener`；
detach 时清 `composeViewRef`/`ownerRef`/`layoutParams`/`windowManager`、注销屏幕广播，并回调宿主；
`OverlaySidePanelHost.onPanelViewDetached()` 把动画状态目标压回 false。
这样 `isAttached=false` → 下一次点击/拖动会重新 `attachPanelWindow()` ✔。装机实测面板正常打开 ✔。

#### 0.16.13.2 用户实测又发现的三件事（**下一轮必修**）

1. **选完图回来草稿全丢** —— 用户实测："选择完图片再打开面板，发现没有添加成功"。
   病因：草稿（图片路径/文字/标签/提醒）是 `HistoryPanelScreen` 里的 Compose `remember` state，
   面板被系统摘掉又重建时整份 state 就没了；`TrampolineResultPort` 的回调写进的是**旧组合**的状态。
   → 修法：把草稿提到**进程级单例**（照 `StashPanelLaunchState` 的样子，用 `mutableStateOf`），
   选图回调直接写单例，面板恢复后自然带出缩略图。
2. **面板盖住了系统相册** —— 用户："添加图片时面板没有 suspend/hide，需要我先关闭面板"。
   病因：我们的面板是 `TYPE_ACCESSIBILITY_OVERLAY`，**画在系统相册之上**。
   → 修法：进系统 UI 前**主动挂起**面板（复用 `OverlayFullScreenPanelHost.setDragHidden(true)`：
   `INVISIBLE` + `FLAG_NOT_TOUCHABLE`），选完在回调里恢复。
3. **只有加号弹窗有「＋ 图片」，编辑弹窗没有** —— 用户指出这不合理。
   → 需要给"给已有条目追加图片"加一条仓库路径（`addRich` 是新建条目；追加要新写 `appendImages`）。

**并且要明确**：这三条落地前，加图功能**不要当可用功能用**（现状：能拉起相册、能落 cache，
但草稿会丢、面板会挡住相册）。

---

### 0.16.14 本轮：提醒从"黑盒"变成"看得见" + 加图三问题一起修

#### 一、提醒（用户实测"完全没效果"→ 查证结论）

真机证据链：`16:46:23.970 AlarmManager: Alarm deliverLocked … STASH_REMIND`（**闹钟准时**）
+ `IntentFirewall: CHECK INTENT … StashReminderReceiver`（**广播确实投给了我们的 Receiver**）
+ 渠道 `stash_remind` 存在、`importance=3`、有系统提示音、未被改
+ **但通知栏里没有这条通知**（活着的只有 1001/4102 两条前台服务通知）
⇒ **断点在 `StashReminderReceiver` 内部，而它原本一行日志都没有**，所以查不出来。

改动：

1. **加日志**：进入 / entryId 为空 / 通知总开关关 / notify 成功 / notify 异常，每步一条。
   `notify` **不再用 `runCatching` 吞异常**（这是以前"连崩溃文件都没有"的原因）。
2. **渠道换 id → `stash_remind_v2` + `IMPORTANCE_HIGH` + 震动**：老渠道是 DEFAULT（不弹横幅、不震动），
   而**渠道重要性创建后不可改**，只能换 id 重建（旧渠道留着给历史通知）。
3. **通知加「稍后 10 分钟」**：广播回自己重排**同一个 PendingIntent**
   （request code 与闹钟本体错开，`StashReminderScheduler.requestCodeOf()` 是唯一出处，避免响两次）。
4. **点通知本体 = 打开收纳面板**：走 `StashClipboardTrampolineActivity` + `CLEAR_TASK`
   （保证 trampoline 的 `onCreate` 重跑；只把旧 task 提到前台的话面板不会真打开 —— adb 里踩过）。
5. **提醒选择器加「1 分钟后」档**：既是常用档，也是**自助测试入口**（设完就能验"横幅/响/稍后按钮"）。
6. 仍然**不碰数据层**（进程可能是被广播拉起来的，那时 Hilt 仓库还没构造好），也仍不发响铃页。

#### 二、加图（用户实测三问题全修）

1. **草稿不再丢**：新增 `StashComposerDraft`（进程级单例，`mutableStateOf` 的文字 / 标签 / 提醒 / 图片路径），
   `HistoryPanelScreen` 里那四个 Compose `remember` state 全部换成它 → 面板被系统摘掉/重建后草稿还在。
2. **面板不再盖住相册**：新增 `StashPanelExternalUi`（挂起/恢复动作的进程级注册点），
   overlay 侧拉相册**之前** `suspend()`（复用 `OverlayFullScreenPanelHost.setDragHidden(true)`：
   `INVISIBLE` + `FLAG_NOT_TOUCHABLE`），回调里第一件事 `resume()`。
3. **编辑弹窗也能加图**：`StashRepository.appendImages(entryId, bitmaps)`（要么全成要么不改；
   文件名单调 `"${id}_append_${n}.png"` 绝不覆盖）+ `StashCoordinator.appendImages` +
   编辑条「＋ 图片」+ 缩略图（复用 `HistoryComposerThumbnail`）。
   **追加时把 `type` 升到 `RICH`** —— 卡片的图片缩略图是 `type == RICH` 门控的，不升就等于"存了看不见"；
   并给 `imageFileName` 兜底（拖拽路径要读它）。

#### 三、仍然留着（按优先级）

- **批次 2**：`BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`/时区变更重排（现在只在进面板时补排）·
  "强提醒"档用 `setAlarmClock` · 精确闹钟权限引导。
- **通知上的「完成」**：需要一条 app 侧动作通道（现在只有「稍后 10 分钟」）。
- **批次 5**：可靠提醒设置向导 · 响铃页。
- 已知限制：国内 ROM 强停/极限省电无法 100% 保证；`force-stop` 会清掉全部闹钟。

**验证（用户实测）**：设「1 分钟后」→ **弹了（横幅）+ 震动 ✅** —— P0 生效，困扰几天的"提醒没效果"到此闭环。
仍待单独确认：通知上的「稍后 10 分钟」按钮是否真按 10 分钟重排；**重启后是否还响**（批次 2 未做，目前只在进面板时补排）。
