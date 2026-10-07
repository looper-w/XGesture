# 闪念胶囊式「液态玻璃标签条」需求分析

> 本文只做分析，不含任何代码改动。结论标注 `相对路径:行号` 证据，并区分「已核实（源码/文档证据）」「未核实（待真机）」「推测」。
> 仓库版本：1.36.0（versionCode 71）；主分支 main；分析过程只读，未修改任何文件。
> 原始建议：**「能增加类似闪念胶囊类似标签功能，结合目前潮流，改成液态玻璃标签条吗，若软件会过于臃肿，可配置简化版」**

## 0. 结论速览

| 项 | 结论 |
| :- | :- |
| 能不能做 | **能做，而且比想象中便宜**。视觉侧（跨应用液态玻璃）项目里已经跑通并已用在 **9 处浮层/面板**上；数据侧（收纳夹）已经有完整仓库，只差一个 `tags` 字段 |
| 需求是否有歧义 | **有，且很大**。「闪念胶囊」「标签」「液态玻璃标签条」各有 2～3 种读法，见 §1。三种读法对应的成本相差 **约 20 倍**（0.5 人日 vs 10+ 人日） |
| 真正的臃肿源 | 不是「标签」，也不是「液态玻璃」，而是①**常驻窗**（第 6 个内容入口）②**新面板**（第 4 个历史/暂存面板）③**语音速记引擎**（本项目完全没有本地 ASR）。§3 |
| 视觉可行性 | 「液态玻璃」的三层里：**模糊层能真**（跨应用，已实现）、**高光/内阴影层能真**（miuix RuntimeShader，但只在应用内）、**折射层不能真**（跨应用拿不到底图像素，只能 MediaProjection 连续截屏 → 会亮投屏指示器，不可发布）。§4 |
| 可配置简化版 | 这个诉求项目里已有**现成范式**：`clipboardFloatEnabled`（默认 false）+ `HistoryFloatLifecycle` 按开关 start/stop 常驻窗（`AppSettingsSlices.kt:359`、`HistoryFloatLifecycle.kt:10-45`）。不需要发明新机制 |
| 建议优先级 | **高（但只做 C 档）**：先给 `StashEntry` 加 `tags` + 收纳夹内筛选 chip，约 0.5～1 人日，零新增常驻窗、零新增模块、零新依赖、零新引擎；B 档（非常驻玻璃标签条）视反馈再上；A 档（常驻条 + 语音）**不建议** |
| 主结论一句话 | **「标签」值得做，「液态玻璃」几乎免费，「常驻胶囊条」要默认关，「语音速记」不要做** |
| 待确认 | 9 条，见 §10；但**已在 §11.9 默认决策表逐条给出默认取值**——无异议即直接按 §11 施工 |
| **总方案** | **§11**。一句话：**不新增「标签条」，而是给收纳面板加一个「折叠态」**——同一份数据、同一个窗、同一个 ViewModel，两种尺寸 |

---

## 1. 需求还原与歧义

### 1.1 闪念胶囊可以拆成 4 个独立能力（推测 + 已核实的项目对照）

原版「闪念胶囊」（锤子 Smartisan OS）在功能层面可拆为：

| # | 能力 | 说法 | 本项目的对照（已核实） |
| :- | :- | :- | :- |
| ① | **秒级采集**：长按硬件键 → 说话或打字 → 立刻落库，不打断当前应用 | 语音/文字速记 | 文字侧有面板/浮层基础，**语音侧完全没有**（§2.5） |
| ② | **胶囊形态的常驻呈现**：屏幕边缘一个小气泡/条，随时可见可点 | 常驻边缘条 | 有该能力（`HistoryFloatService`），但默认关闭（§2.2） |
| ③ | **组织**：按类型着色（灵感/待办/注意）+ 用户自定义标签 + 时间轴回溯 | ←**你说的「标签」大概率是这一层** | 收纳夹有列表 + 星标 + 搜索，**没有标签、没有类型、没有时间轴视图**（§2.1） |
| ④ | **提醒闭环**：给胶囊设定时间，到点弹回 | 定时提醒 | 已有（`remind/RemindAlarmScheduler.kt`、手势动作「N 分钟后闹钟提醒」，`README_zh.md:83`） |

**关键观察**：①的语音一半、②的常驻形态、④的提醒闭环在本项目都是**新增或已存在**，而③才是「标签」的字面所指。**这四件事的成本差了 20 倍**，所以需求必须拆开确认。

### 1.2 「标签」的三种读法

- **读法 a｜胶囊的分类标签（系统预置的类型）**：灵感 / 待办 / 注意 / 问题，落库时随手选一个，用颜色区分。等价于给 `StashEntryType`（现在只有 `TEXT/IMAGE/RICH`，`StashEntry.kt:8-12`）之外再加一个正交的「类别」维度。
- **读法 b｜用户自定义标签（#工作 #购物 #灵感）**：自由打标，一条可多标签，用于检索与聚合。**这是最常见的理解，也是本文默认解读。**
- **读法 c｜「标签条」本身**：把标签做成屏幕边上一条常驻的 chip 条，点一下就是那个标签的列表。「标签」在这里是**入口**而不是**数据**。

a 与 b 可以共存（预置类型 + 自由标签）；c 是 UI 形态，与 a/b 正交。

### 1.3 「液态玻璃标签条」的三种读法

- **读法 ①｜真液态玻璃**：Apple 2025 的 Liquid Glass —— 实时模糊 + **边缘折射/透镜扭曲** + 镜面高光 + 随姿态变化。**跨应用做不出折射**（原因见 §4.4）。
- **读法 ②｜「毛玻璃胶囊条」的潮流叫法**：只要模糊 + 半透明 + 圆角 + 描边高光就算。**这个项目里已经是成品**（§2.4），边际成本≈0。
- **读法 ③｜现有底栏样式的延伸**：把主界面底部那条 `LIQUID_GLASS` 底栏的观感搬到浮层条上。

**推测**：你大概率是读法 ② —— 即「跟上 2026 年的审美，用液态玻璃的观感来做标签条」。这一点很重要，因为读法 ② 的成本只有读法 ① 的 1/5。

### 1.4 本文默认解读

> 在**收纳夹**（现有内容暂存仓库）上补一层**用户自定义标签**（读法 1.2-b），并提供一个**手势呼出、非常驻**的**液态玻璃观感标签条**浮层（读法 1.3-②），作为收纳夹的标签筛选入口；常驻形态做成**默认关闭**的可配置项；**不引入语音速记**。

这个解读下：C 档 ≈ 0.5～1 人日，B 档累计 ≈ 2.5～3.5 人日，且不新增模块、不新增依赖、不新增引擎。

---

## 2. 现状盘点（已核实）

### 2.1 收纳夹（Stash）已经是「胶囊仓库」，只差一个字段

`StashEntry` 的全部字段（`app/src/main/java/com/slideindex/app/stash/StashEntry.kt:15-27`）：

```
id / type(TEXT|IMAGE|RICH) / text / imageFileName / contentBlocks / htmlText
/ createdAtEpochMs / starred / pinDisplayWidthPx / pinDisplayHeightPx
```

→ **有**：文本、图片、富文本块、HTML、时间戳、**星标**、钉屏幕尺寸。
→ **没有**：`tags`、类别/颜色、`remindAt`、归档/回收站、排序字段。

仓库实现（`StashRepository.kt`）已经是可用的生产级：

- 存储 = `filesDir/stash/index.json`（kotlinx.serialization，`StashRepository.kt:36-40,401-410`）。
- **跨进程安全**：进程内 `Mutex` + `CrossProcessStore.withFileLock` + 变更广播（`StashRepository.kt:54-61,67-80`）——标签的写入必须走同一条路径，不能另开写口。
- 容量上限 `MAX_ENTRIES = 200`，超出即删（连图片一起删，`StashRepository.kt:412-416,423`）。
- 已有能力：`addText/addImage/addRich`、`toggleStar`、`matchesQuery` 搜索（`StashEntry.kt:65-70`）、`delete/clearAll`、缩略图 LRU 缓存。
- 已有能力（对外）：钉到屏幕 `pinTextToScreen/pinImageToScreen/pinRichFromStash`（`StashCoordinator.kt:92-160`）、复制、分享、打开面板。
- 已有入口：手势动作 `OPEN_STASH_PANEL(43)`（`core/gesture/src/main/java/com/slideindex/app/gesture/GestureAction.kt:67`）、Deeplink `xgesture://open/stash`（`README_zh.md:191`）、桌面快捷方式（`LauncherShortcutMenu.kt:26`）、悬浮球动作、剪贴板浮窗入口。

**兼容性（关键，已核实）**：`Json { ignoreUnknownKeys = true }`（`StashRepository.kt:40`）+ 新字段给默认值 ⇒ **旧版本读新 JSON 不会崩**；但旧版本回写时会丢掉 `tags`（只保护读，不保护写）→ 需要在 CHANGELOG 提示版本降级会丢标签。这是加字段的唯一代价。

**结论**：给 `StashEntry` 加 `tags: List<String> = emptyList()` 是本次建议里**性价比最高的一步**：不新建表、不新建模块、不改 manifest、不影响旧数据。

### 2.2 「常驻边缘条」的范式与开关都已经有了

- `HistoryFloatService`（`app/src/main/java/com/slideindex/app/service/HistoryFloatService.kt:37`）：独立 Service + `ComposeView` + `OverlayWindowTypes.overlayWindowType(this)` 常驻边缘把手（`:131-140`），`START_STICKY`，处理横屏/全屏隐藏、可拖动、位置持久化。
- 开关范式：`clipboardFloatEnabled`（**默认 false**，`feature/settings/.../AppSettingsSlices.kt:359`）+ `HistoryFloatLifecycle` 按设置 start/stop 服务（`HistoryFloatLifecycle.kt:10-45`）+ 设置页开关（`app/.../ui/StashClipboardSettingsScreen.kt:577`）。
- 生命周期协调：`OverlayLocaleCoordinator.kt:46`、`StashClipboardNavEntries.kt:45`。

⇒ **你说的「可配置简化版」在这个项目里不是新设计，而是既有范式**：默认关 + 一个开关拉常驻窗 + 跟随语言/横屏重建。做标签条只需照抄这一套。

### 2.3 「全局输入 + 悬浮提醒」的输入侧也已经有了

- `SearchPanelOverlayWindow`：面板窗固定 `TYPE_APPLICATION_OVERLAY(2038)`（`app/.../overlay/searchpanel/SearchPanelOverlayWindow.kt:374-389`），并且已经实现了**浮层里自动弹输入法**：`SOFT_INPUT_STATE_ALWAYS_VISIBLE` + 多次补发以对抗部分 ROM/输入法吞掉首次请求（`:259-289`）。
- `OverlayImeInsets.kt:20` 记录了「overlay 窗收不到正常 IME inset」这个坑，已有 helper。
- `remind/RemindDurationPickerOverlay.kt` + `RemindAlarmService.kt:92`（`TYPE_APPLICATION_OVERLAY`）：**「浮层里输入 → 定时提醒」的先例已经存在**，闪念胶囊的 ④ 基本是白送。

⇒ 「手势呼出浮层 → 打字 → 落库」这条链路的**全部难点都已经踩过**。

### 2.4 「液态玻璃」在本项目其实是两套机制，而且跨应用那套已经能打

项目里「玻璃」不是一个东西，而是**两套完全不同的机制**，分工清晰：

**机制 A｜跨应用真模糊（悬浮窗专用）**：反射调用隐藏 API `ViewRootImpl.createBackgroundBlurDrawable()` 拿到一个「窗口背后内容」的模糊 Drawable，可自由设 **bounds / 圆角 / 半径 / 着色**。

- 实现：`app/src/main/java/com/slideindex/app/overlay/LocalFrostedGlassBackdrop.kt:17,51-67,73-145`（`LocalFrostedGlassDrawable`，`@SuppressLint("PrivateApi")`，全程 `runCatching` 兜底）。
- **已用在 9 处**：收纳夹面板 `overlay/history/HistoryPanelScreen.kt:248`、搜索面板 `searchpanel/SearchPanelChrome.kt:138`、取词结果面板 `pickresult/PickResultPanelLayout.kt:722`、音量面板 `volumepanel/VolumePanelContent.kt:272`、OHO 快速工具 `OhoQuickToolsPanel.kt:154`、翻译面板 `FloatBallTranslatePanel.kt:451`、剪贴板链接选择 `clipboardoverlay/ClipboardLinkPickerOverlay.kt:296`，以及两处 Canvas 自绘（蜂窝/全息启动器 `holographic/HolographicLauncherBackgroundView.kt:22`、`QuickLauncherRenderer.kt:309`）；另有 `widget/WidgetPopupView.kt:273` 自行反射的同款实现。（其中 `HistoryPanelScreen.kt:248` 就是**收纳夹面板本身**——也就是说「悬浮在其他 App 之上的一块玻璃」正是这个面板现在就在做的事。）
- 另一条同类路径：`FLAG_BLUR_BEHIND` + `params.setBlurBehindRadius(px.coerceIn(1, 80))`，门禁 `WindowManager.isCrossWindowBlurEnabled`，见 `overlay/OverlayFullScreenPanelHost.kt:118-152`、`overlay/searchpanel/SearchPanelOverlayWindow.kt:239-256`、`overlay/corner/CornerGestureController.kt:649-656`。

**⇒ 结论（已核实）：本项目已经能把「悬浮在其他 App 之上的一块圆角半透明模糊玻璃」画出来，并已在 8 个面板上量产使用。「液态玻璃标签条」的模糊底座是现成的。**

**机制 B｜窗口内玻璃（应用内专用）**：miuix 的 `LayerBackdrop` 系（`recordLayer` + `RenderEffect.createBlurEffect`），配 `lens()`（圆角 SDF 折射 RuntimeShader + 7 抽样色散）、`vibrancy()`（`colorControls(saturation = 1.5f)`）、`Modifier.innerShadow`、镜面高光 + 随重力旋转。

- 入口：`IosLiquidGlassNavigationBar(items, selectedIndex, onItemClick, backdrop, isBlurActive, isDark, showLabels, …, blurRadiusDp)`（`ui/miuix/bottombar/liquid/LiquidGlassNavigationBar.kt:193-208`，`internal`）。
- 可复用件：`lens`（`Lens.kt:24`）、`vibrancy`（`Vibrancy.kt:14`）、`innerShadow`（`InnerShadow.kt:50`）、`rememberCombinedBackdrop`（`CombinedBackdrop.kt:47`）、`rememberMiuixBlurBackdrop`（`ui/miuix/MiuixBlur.kt:45`）。
- **硬耦合部分**：`NavigationItem` 语义、等宽 tab 测量、`DampedDragAnimation`、ViewPager `progress()` 跟手、固定 62/56dp 高度、navigationBars padding（`LiquidGlassNavigationBar.kt:226-236,253-300,310-318,333-347,355-365,449-453,465-491,520-532,553-584`）。**这部分不能直接搬到标签条**，但可以照 `MainMiuixFloatingNavBar.kt:79-92` 的写法用 miuix `textureBlur(backdrop, shape, blurRadius, colors, highlight)` + 一个普通 `RoundedCornerShape`，那才是标签条的 drop-in 路径。
- **重要限制（已核实）**：浮层里这套是**被主动关掉的** —— `MiuixOverlayComposeLocals` 把 `LocalMiuixBlurBackdropEnabled` 置为 false（`ui/miuix/MiuixOverlayComposeLocals.kt:12-19`），原因是无障碍/WindowManager 浮层可能整窗走软件 Canvas（`ViewRootImpl.drawSoftware`），RuntimeShader 会在 `drawRect` 直接崩。
  **⇒ 机制 B 不能用于「悬浮在其他 App 上的标签条」**；跨应用只能用机制 A。

**已有依赖**（无需新增）：`haze 1.7.3`、`miuix-blur`、`miuix-shader`（`gradle/libs.versions.toml:40,120,128,129`）。

**已有可配置模糊的先例**：`stashPanelBackgroundBlurEnabled`（**默认 false**）+ `stashPanelBackgroundBlurRadiusDp`（默认 36dp / 最大 72dp，`AppSettingsSlices.kt:395-396`、`AppSettings.kt:404-411`、`HoneycombDisplaySettings.kt:70-72`）；底栏另有 0～32dp 区间与 `usesBottomNavHaze()` 总开关（`OverlaySettings.kt:441-448,458-460`）。

### 2.5 真正没有的东西：本地语音速记

- `app/src/main/java/com/slideindex/app/util/VoiceActionHelper.kt:20-59` 只是把 `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE / ACTION_WEB_SEARCH / ACTION_RECOGNIZE_SPEECH` **交给系统**（含 Shizuku `am start` 兜底），**拿不回识别结果**。
- 全仓无 `SpeechRecognizer` 实例、无 Vosk / Sherpa-onnx / Whisper 依赖（唯一命中是云端模型目录里的字面量 `CloudLlmModelCatalogClient.kt:32`）。
- 已有 native 引擎只有 OCR（ML Kit / Tesseract / PaddleOCR ONNX）与分词（CppJieba）。

**⇒ 闪念胶囊的「长按说话出胶囊」这一半，本项目是 0。** 要做只能：①用系统 `ACTION_RECOGNIZE_SPEECH` + `startActivityForResult` 取 `EXTRA_RESULTS`（会跳出本应用、非悬浮、体验与原版差距大，且部分 ROM 无此 Activity）；②引入第三个 native 引擎（模型 10～100MB 级 + 新增录音权限 + 常驻内存 + 各 ROM 后台限制）。**这是「臃肿」的真正大头，本文建议不做。**

### 2.6 体量基线（臃肿的量化标尺）

| 对象 | 文件数 | 行数 | 说明 |
| :- | -: | -: | :- |
| `app` 模块 | 1270 | 233,242 | 宿主 |
| `feature/settings` | 122 | 14,837 | 设置与 DataStore |
| `core/common` | 72 | 5,004 | |
| `core/gesture` | 50 | 6,711 | |
| `overlay/pickresult` | 18 | 7,891 | 单个浮层的体量上限参考 |
| `overlay/searchpanel` | 17 | 5,156 | |
| `overlay/corner` | 15 | 2,902 | |
| `overlay/history`（收纳夹+剪贴板面板） | 12 | 2,590 | **标签条要并入的既有面板** |
| `overlay/carousel` | 3 | 609 | 单个中型面板的体量下限参考 |
| 4 套 locale 字符串 | — | 各 ~3,800 条 | **每个新 UI 功能的固定翻译税** |

**⇒ 一个「新面板」的现实成本是 2,500～8,000 行；而「给现有面板加一行 chip 筛选」是 100 行级。这两者的差距就是本文全部建议的出发点。**

---

## 3. 臃肿评估（你最担心的那一条）

### 3.1 现在已经有几个「内容入口」

收纳夹侧栏、剪贴板历史边缘把手、钉图/钉文本常驻浮窗、悬浮球、搜索面板、通知滤盒——**再加「标签条」就是第 6～7 个入口**。用户的心智负担是真实的，尤其当标签数据本来就住在收纳夹里时，「另外开一个条」在信息架构上是**冗余入口**而不是新能力。

### 3.2 功能本身不臃肿，臃肿的是这四样东西

| 臃肿源 | 量级 | 是否必需 |
| :- | :- | :- |
| ① **常驻窗**（第 6 个入口，常驻 Service + 触摸区域 + 生命周期） | 中（~300 行 + 服务/开关/语言/横屏协调） | **不必需**，可默认关 |
| ② **新面板**（而不是并入收纳夹） | **大**（2,500～8,000 行 + 4 语言文案） | **不必需**，应并入 `overlay/history` |
| ③ **语音速记引擎** | **很大**（新 native 引擎 + 模型 + 录音权限 + 内存/耗电 + ROM 后台限制） | **不必需**，建议不做 |
| ④ 液态玻璃观感 | **很小**（机制 A 现成；机制 B 不能跨应用） | 建议做 |
| ⑤ 标签数据模型 | **很小**（1 个字段 + 若干读写） | 建议做 |

### 3.3 项目已有的瘦身通道（可直接复用，不需要发明）

1. **数据复用**：标签直接挂在 `StashEntry` 上，不新建 capsule 实体/表/模块。
2. **UI 复用**：标签条 = `overlay/history` 里 Stash tab 顶部的一行筛选 chip（`HistoryPanelScreen.kt` 已有 Stash/Clipboard 双 tab，`HistoryPanelViewModel.kt:27`），不新建面板。
3. **常驻按需**：若确实要边缘常驻条，照 `clipboardFloatEnabled` + `HistoryFloatLifecycle` 的范式，**默认关闭**，一个开关拉起（§2.2）。
4. **编译期隔离**：`full` / `lite` 两个 product flavor 已经存在（`app/build.gradle.kts:55-63`），Lite 侧已有「按需下载引擎」的先例（`NativeEnginePackDownloadService.kt`、`OcrModelDownloadService.kt`）——若将来真引入语音引擎，应走这条按需下载路线，而不是进主包。
5. **运行期降级**：模糊半径可调、可整体关闭（`stashPanelBackgroundBlurEnabled` 默认 false；README 也已把「关闭毛玻璃以消除采样开销」作为卖点，`README_zh.md:98`）。

### 3.4 分档体积税

| 档位 | 新增常驻窗 | 新增面板/模块 | 新依赖/引擎 | 新增翻译键 | 估工作量 |
| :- | :-: | :-: | :-: | -: | :- |
| **C｜标签数据 + 收纳夹筛选** | 0 | 0 | 0 | ~6 | **0.5～1 人日** |
| **B｜＋手势呼出的玻璃标签条浮层** | 0（非常驻） | 0（并入 history 浮层） | 0 | ~10 | +1.5～2.5 人日 |
| **A｜＋常驻边缘条 + 类型色 + 时间轴** | 1（默认关） | 0～1 | 0 | ~25 | +2～4 人日 |
| **A+｜＋语音速记** | 1 | 1（引擎分发） | **1 个 ASR 引擎 + 模型** | ~20 | **+3～10 人日，且包体显著增长** |

---

## 4. 技术可行性：液态玻璃标签条能不能做「真」

### 4.1 玻璃的三层，逐层给答案

| 层 | Apple Liquid Glass 的作用 | 本项目跨应用能不能做 | 证据 |
| :- | :- | :- | :- |
| **模糊层** | 背景实时模糊 | ✅ **能，已实现并量产** | `LocalFrostedGlassBackdrop.kt:51-67,105-132`；另有 `FLAG_BLUR_BEHIND` 路径 `OverlayFullScreenPanelHost.kt:141-149` |
| **高光/内阴影/描边层** | 镜面高光、内阴影、边缘亮线、随姿态变化 | ✅ 能（**但只在应用内窗口**） | `Lens.kt:24`、`Vibrancy.kt:14`、`InnerShadow.kt:50`、`LiquidGlassNavigationBar.kt:115-133,152-191`；浮层内被 `MiuixOverlayComposeLocals.kt:12-19` 主动关闭 |
| **折射/透镜层** | 边缘把背景「扭曲拉入」——这是 Liquid Glass 的灵魂 | ❌ **不能** | 机制 A 只给一个已模糊的 Drawable，**不提供底图像素纹理**；机制 B 的 `LayerBackdrop` 只采样**本窗口自己绘制的内容**（`rememberLayerBackdrop()`，`LiquidGlassNavigationBar.kt:215`） |

### 4.2 想要真折射，只有一条路，且不可发布

用 `MediaProjection` 连续截屏 → 把区域纹理喂给 AGSL RuntimeShader 做 SDF 折射。

- 项目**有**这条能力（截屏/区域截图/OCR 全在，`ScreenCaptureService.kt`），技术上做得出来。
- 但代价：① 系统会**常驻「正在投屏/录制屏幕」隐私指示器**（Android 12+ 无法隐藏，国内 ROM 更醒目）；② 每秒 30～60 帧的捕获 + 上传纹理 = 显著功耗与发热；③ 常驻截屏权限是隐私审计红线。
- **⇒ 「真液态玻璃折射」在常驻浮层的形态下不可发布**（已核实为工程结论，非推测）。这一点应当在任何对用户的宣传文案里避免承诺。

### 4.3 建议的视觉方案（三段式降级，观感约 90%）

1. **底座**：机制 A 的 `LocalFrostedGlassBackdrop`（或 `setBlurBehindRadius`）→ 真跨应用模糊，圆角 + 着色。
2. **玻璃感**：外描边 1px 高光 + 内阴影 + 顶部渐变（`InnerShadow` 的思路可手写成 `Modifier.drawWithContent`，因为浮层内不能用 miuix RuntimeShader）+ 轻微噪点（项目已有先例 `noiseFactor = 0.08` 用于抗色带，`MainBottomNav.kt:175-176`）。
3. **交互高光**：按压时高光位移 + 缩放（抄 `DampedDragAnimation` / `InteractiveHighlight` 的思路，但**不要**把整条 bar 的 3 次 `drawBackdrop` 也带过来，标签条应只画 1 次）。
4. **降级兜底**：`isCrossWindowBlurEnabled == false`、RuntimeShader 不支持、省电模式 → **退化为不透明胶囊**（项目已有该分支，`SearchPanelOverlayWindow.kt:251-254`、`LiquidGlassNavigationBar.kt:492-494`）。

参考常量：本项目逆向过的商业搜索面板把玻璃胶囊定为 **高 40、左右内边距 14、描边 1.0**（`docs/floatwidget-search-panel-reverse.md:444-446`），可直接作为标签条尺寸起点。

### 4.4 已知硬限制清单（做方案前必须接受）

1. `isCrossWindowBlurEnabled` 在部分 ROM / 省电模式 / 低内存设备返回 **false** → 无模糊。**必须有兜底，不能假设模糊一定在。**
2. `setBlurBehindRadius` 的像素值被 **clamp 到 1～80px**（`OverlayFullScreenPanelHost.kt:145`、`SearchPanelOverlayWindow.kt:249`）。→ 在 3x 密度屏上，**dp 设定超过约 27dp 就已饱和**，「调更大」不会有任何效果。UI 滑杆的上限需要按密度反推，否则是骗用户。
3. 机制 A 走**隐藏 API 反射**（`ViewRootImpl.createBackgroundBlurDrawable`），无公开替代；未来 Android 版本可能失效 → 已有 `runCatching` 兜底，但要接受「某天模糊整体失灵」的风险。
4. 浮层内**不能**用 miuix RuntimeShader（`MiuixOverlayComposeLocals.kt:12-19`，软件 Canvas 会崩）。
5. 机制 B 在 **API < 33 上不可用**：`rememberMiuixBlurBackdrop` 会返回 null，退回纯色 surfaceContainer（`ui/MainMiuixFloatingNavBar.kt:76` 注释、`ui/miuix/MiuixBlur.kt:45`）。本项目 minSdk = 31，也就是说 Android 12 / 12L 设备**无论怎么做都拿不到 miuix 那套玻璃**——对这部分用户，观感完全取决于机制 A 的跨应用模糊。
6. 系统模糊由 SurfaceFlinger 承担，比自绘 shader 便宜，但**常驻**窗仍会带来持续的合成开销；建议默认关 + 可调半径。

---

## 5. 方案对比

| | A 完整版 | B 精简版标签条 | **C 地基版（推荐先做）** | D 折中（C→B） | A+ 语音版 |
| :- | :- | :- | :- | :- | :- |
| 内容 | 常驻边缘液态玻璃条 + 标签 + 类型色 + 时间轴 + 手势呼出 | 手势/悬浮球呼出玻璃标签条浮层 + 标签 + 筛选 | 仅 `StashEntry.tags` + 收纳夹内 chip 筛选行 + 搜索命中标签 | C 落地后再加 B | A + 本地/系统 ASR 速记 |
| 新增常驻窗 | 1（默认开） | 0 | 0 | 0 | 1 |
| 新增面板/模块 | 0～1 | 0（并入 `overlay/history`） | 0 | 0 | 1（引擎分发） |
| 新依赖/引擎 | 0 | 0 | 0 | 0 | **ASR 引擎 + 模型** |
| 风险 | 中（入口冗余、常驻被系统杀、模糊不可用） | 低 | **很低** | 低→中 | **高（包体/权限/功耗/ROM 限制）** |
| 估工作量 | 4～7 人日 | 2～3.5 人日 | **0.5～1 人日** | 3～4.5 人日 | +3～10 人日 |
| 建议 | 视 B 的反馈再议 | **第二批** | **第一批，立刻可做** | **推荐路线** | **不做** |

**推荐路线：C（本次）→ 观察 1～2 个版本的反馈 → B（若标签真被用起来）→ 只有当「常驻可见」被明确要求时才考虑 A，且默认关闭。**

理由：C 把**数据**（标签）与**入口**（标签条）解耦。数据先落地，标签才可能积累；没有数据的情况下先做常驻玻璃条，就是把最贵的一环压在最低价值的一环上。

---

## 6. 改动清单与工作量估算

### 6.1 C 档（地基）

| 文件 | 改动 | 估行数 |
| :- | :- | -: |
| `app/.../stash/StashEntry.kt:15-27` | `+ val tags: List<String> = emptyList()`；`matchesQuery(:65-70)` 加标签命中；新增 `normalizedTags()` | +12 |
| `app/.../stash/StashRepository.kt` | `setTags(id, tags)`、`allTags(): StateFlow<List<String>>`、`addText(text, tags)`；**必须复用 `withCrossProcessWrite`**（`:54-61`） | +45 |
| `app/.../overlay/history/HistoryPanelViewModel.kt` | 标签筛选 `StateFlow` + 与搜索条件 `combine`（参照现有 `filteredStashEntries`，`:53-60`） | +25 |
| `app/.../overlay/history/HistoryPanelScreen.kt` + `HistoryStashTabBody(:382-)` | Stash tab 顶部 chip 行（全部/未标注/各标签/＋新建标签）；条目上的标签小字 | +70 |
| `app/.../overlay/history/HistoryEntryCards.kt` | 条目卡显示标签 + 长按编辑标签 | +30 |
| `feature/settings/.../AppSettingsSlices.kt` / `OverlaySettings.kt` / `SettingsSnapshotReader.kt` / `OverlaySettingsMutator.kt` / `SettingsRepository.kt` | 开关 `stashTagsEnabled`（默认 true/false 待定，§10-4） | +30 |
| `app/src/main/res/values{,-zh,-ja,-ar}/strings.xml` | ~6 条文案 | 小型 |
| 测试（`StashEntryTest` / `StashRepositoryTest` 标签往返 / 迁移） | 含「旧 JSON 无 tags 字段可读」「旧版本回写丢标签不崩」两例 | +60 |

**小结：C 档约 0.5～1 人日，无新增依赖、无新增 Manifest 权限、无新增模块、无常驻窗。**

### 6.2 B 档（精简版标签条）在此之上的增量

| 项 | 说明 | 估行数 |
| :- | :- | -: |
| 浮层宿主 | 新建/复用一个 `OverlayCompose` 宿主 + `OverlayPanelLayoutParams` 新条目（`overlay/OverlayPanelLayoutParams.kt:51` 有 stash 侧栏先例） | +120 |
| 玻璃条 UI | 机制 A 底座 + 手写描边/内阴影（**不用** miuix RuntimeShader） | +250～400 |
| 手势动作 | `core/gesture/.../GestureAction.kt`（枚举 id + `override val type` + 动作映射，现 `OPEN_STASH_PANEL` 在 `:67,:429,:805`）+ `GestureActionIconBitmap.kt:119` + `GestureActionOutlinedIcons.kt:41,133` + `GestureActionPickerSearch.kt:295,444,640` + `GestureActionCatalog.kt:171` | +80（6 文件；多处 exhaustive `when`，编译器会强制补齐，是防漏改保障） |
| 文案 | 4 套 locale | 小型 |
| 开关 | `tagBarEnabled`（默认 **关**）+ 模糊半径（默认建议 4dp，对齐液态玻璃底栏 `BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP`） | +40 |

**小结：B 档增量约 1.5～2.5 人日；累计 2.5～3.5 人日。**

### 6.3 A / A+ 档增量（不建议现在做）

- A：常驻 Service（照抄 `HistoryFloatService.kt` 全套 + `HistoryFloatLifecycle` 式开关 + 横屏/全屏隐藏/位置持久化）、类型色、时间轴视图、+2～4 人日。
- A+：ASR 引擎接入（新 native 引擎 + 模型分发 + `RECORD_AUDIO` 权限 + 常驻内存 + 各 ROM 后台限制）、+3～10 人日 **且包体显著增长**，与「Lite 轻量包」的产品定位直接冲突（`README_zh.md:50-56`）。

---

## 7. 风险与未知

1. **入口冗余（产品层最高风险）**：标签数据住在收纳夹里，再开一个常驻条 = 同一份数据的第二个入口。缓解：**常驻默认关**；标签条只做「手势呼出的筛选入口」，与收纳夹共用同一个浮层与 ViewModel。
2. **`isCrossWindowBlurEnabled == false` 时观感塌陷**：必须设计不依赖模糊的形态（不透明胶囊 + 描边高光），否则在部分 ROM 上「液态玻璃」直接变成一块半透明砖。**未核实：具体哪些 ROM/省电模式会返回 false，需真机覆盖。**
3. **隐藏 API 依赖**：机制 A 反射 `ViewRootImpl.createBackgroundBlurDrawable`；Android 版本演进下可能失效。已有 `runCatching` 兜底，但要接受「某天全项目所有玻璃一起变砖」的单点风险。
4. **模糊半径在高密度屏饱和**：窗口层 clamp 到 80px（§4.4-2）。滑杆上限必须按 `density` 反推，否则是无效控件。
5. **标签会被 200 条上限连带删除**：`trimToMax`（`StashRepository.kt:412-416`）按条数裁剪并删图片 → 标签随条目一起消失。若标签要长期稳定，需要「标签独立于条目存活」的设计（例如独立的标签表 + 计数），这是 C 档唯一需要额外想清楚的点。**待确认，见 §10-5。**
6. **多进程写入**：标签必须走 `CrossProcessStore.withFileLock` 路径，否则会出现「标签写了但另一个进程看不到」或文件互相覆盖（项目已有一整套跨进程方案，见 `docs/multiprocess_state_ownership.md`）。
7. **版本降级丢标签**：`ignoreUnknownKeys` 只保护读不保护写 → 用户降级到旧版本再回写 `index.json` 会丢标签。需在 CHANGELOG / 设置页提示。
8. **翻译债**：4 套 locale 各 ~3,800 条，ja/ar 已落后 1～2 条；新增 UI 文案的固定成本不可忽略。
9. **语音方案不可行性**：系统 `RecognizerIntent` 拿不回结果且会跳出应用；自建 ASR 会与 Lite 包定位冲突（§6.3）。若用户坚持要语音，需重新评估 Lite/Full 策略与包体预算。
10. **「潮流」时效性**：液态玻璃是 2025～2026 的审美，国内 ROM 各有自己的玻璃语言（HyperOS / ColorOS / OriginOS）。**推测**：用可关、可退化、可调半径的方式实现，避免把产品审美绑死在一个会过时的样式上。

---

## 8. 验证方案

1. **C 档（无需真机专项）**：`StashRepository` 标签读写的单位测试（含旧 JSON 无 `tags` 字段、旧版本回写丢标签不崩、跨进程序列化往返）；`/gradlew.bat compileDebugKotlin` + 相关单测。
2. **C 档真机**：收纳夹打标签 → 杀进程重开 → 标签仍在；搜索命中标签；标签筛选与搜索条件叠加；200 条裁剪后标签的表现（对应风险 5）；收纳夹面板与剪贴板面板切换后筛选状态不串。
3. **B 档视觉专项（关键）**：至少在 3 类设备上截图对比 —— ① 支持跨窗模糊的原生/主流 ROM；② 关闭模糊或省电模式（`isCrossWindowBlurEnabled == false`）；③ 高密度屏（验证半径饱和）。三层降级都必须有可接受观感。
4. **B 档性能**：常驻/呼出时 `gfxinfo` 帧时间、面板滚动时的掉帧（项目已有 `core/monitoring` 与 `DebugPerformanceOverlay.kt` 可用）；对比「开玻璃 vs 关玻璃」的滚动帧率（README 已把这条作为卖点，需要能兑现）。
5. **B 档交互回归**：手势呼出/收起、横屏、全屏应用隐藏、语言切换后重建（`OverlayLocaleCoordinator.kt`）、与悬浮球/取词面板的 z-order 关系（`OverlayWindowTypes.kt:35-76` 有明确的层级契约）。
6. **开关与精简版**：开关关闭时**不创建**常驻窗（不能只是隐藏），内存/服务列表中不应出现该 Service。

---

## 9. 建议实施顺序

0. **先定 §10 的 1～3 条**（「标签」是类型还是自由标签？「标签条」是常驻还是呼出？要不要语音？）——它们决定整个方案形状。
1. **C 档**：`StashEntry.tags` → 仓库读写（复用跨进程写口）→ 收纳夹 chip 筛选 → 单测 + 编译自检。**不含任何 UI 玻璃、不含常驻窗。**
2. 观察 1～2 个版本：标签是否真的被用户使用（这是 B 档的准入条件）。
3. **B 档**：手势动作 → 浮层宿主（并入 `overlay/history`）→ 玻璃条 UI（机制 A + 手写描边/内阴影 + 三段降级）→ 开关默认关 → 截图基线。
4. 只有在「常驻可见」被明确要求后才考虑 A 档，且默认关闭、复用 `HistoryFloatLifecycle` 范式。
5. A+（语音）**单独立项**，按「是否值得为它增包」来决策，不要顺带做。

---

## 10. 待确认

1. **「标签」你要的是哪一种？** ① 系统预置的类型（灵感/待办/注意，带颜色）② 用户自由打的多标签（#工作 #购物）③ 两者都要。→ 决定数据模型（§1.2）。
2. **「标签条」必须常驻在屏幕上吗？** 还是手势呼出即可？→ 常驻是本次唯一真正的臃肿源（§3.2-①、§3.4）。
3. **要不要语音速记？** 若要，接受「引入 ASR 引擎 + 模型 + 录音权限 + 包体增长」，还是接受「借用系统语音、跳出应用、拿不回文本」？（§2.5、§6.3）
4. **标签功能默认开还是默认关？** 参考项目惯例：`clipboardFloatEnabled` 默认 false、`stashPanelBackgroundBlurEnabled` 默认 false（§2.2、§2.4）。
5. **标签要不要独立于条目存活？** `MAX_ENTRIES = 200` 的裁剪会连标签一起删（§7-5）。若要「标签常驻、条目可过期」，需要单独一张标签表，成本上升。
6. **标签条并入收纳夹面板，还是独立新面板？** 我强烈建议并入（§3.3-2）；独立面板的成本是 2,500～8,000 行（§2.6）。
7. **「液态玻璃」做到哪一档？** 接受「真模糊 + 模拟高光、无折射」（观感约 90%，成本低），还是坚持要 Apple 那种折射（跨应用不可发布，§4.2）？
8. **典型使用机型/ROM 是什么？** 决定 §8-3 的视觉基线覆盖范围，也决定 `isCrossWindowBlurEnabled` 的兜底形态权重。
9. **是否需要同步改 README / 扩展面板入口 / Deeplink**？例如 `xgesture://open/stash?tag=工作`，让 Tasker 也能按标签唤起（`README_zh.md:181-206` 已有 Deeplink 表）。

> §10 的 9 条已在 **§11.9 默认决策表**里逐条给出我的默认取值。若无异议，直接按 §11 施工。

---

# 11. 总方案

## 11.0 一句话

> **不新增「标签条」，而是给收纳面板加一个「折叠态」**：同一份数据、同一个 ViewModel，两种尺寸。折叠态是贴边的液态玻璃胶囊（携带标签），展开后是**就地长出来的高度自适应快速面板**；原有的全高收纳侧栏**形状不变**，只多一行标签 chip。

**形态学总图**（静态稿：[docs/mockups/capsule-ui.png](mockups/capsule-ui.png) · **交互稿：[ui_demo_capsule.html](../../ui_demo_capsule.html)**）

```
【折叠态】贴边玻璃胶囊              【快速面板】从胶囊就地展开，顶边与胶囊对齐
      ┌──┐                        ┌──────────────────────────────┐
      │●│ ← 点击：就地展开         │ 全部   工作   #想法      ＋   │ ← chip 行
      │工│ ← 长按：直接打字落库     ├──────────────────────────────┤
      │●│ ← 拖动：改纵向位置       │  ● 记得周五前把方案初稿发…    │ ← 最多 5 条
      │作│ ← 上滑：切换标签         │  ● 把液态玻璃的描边改成 1px…  │
      │＋│                        │  ● 买咖啡豆                   │
      └──┘                        ├──────────────────────────────┤
                                  │        查看全部  ›            │ ← 进全高侧栏
                                  └──────────────────────────────┘
```

**三个表面、一份数据、一个 ViewModel**

| 表面 | 何时出现 | 形状 |
| :- | :- | :- |
| ① 标签 chip 行（P0） | 手势 / Deeplink / 桌面快捷方式打开收纳面板 | **既有全高侧栏形状完全不变**（半屏宽 / 圆角 14dp / 灰底白卡 / 标题+图钉+搜索+双 tab），只在 tab 行下多一行 chip，仅在暂存夹 tab 显示 |
| ② 折叠态玻璃胶囊（P1） | 用户开启常驻（**默认关**） | 贴边竖向胶囊，可拖 Y、可上滑切标签 |
| ③ 快速面板（P1） | 点击 ② 就地展开 | 玻璃卡片，**顶边与胶囊对齐**，高度贴合内容，最多 5 条 |

> **几何连续性说明（修正早期草案）**：草案写的「chip 行位移 0、内容从它下面长出来」在**全高侧栏**上不成立——胶囊可以停在任何 Y，而全高侧栏的 chip 行固定在顶部。**成立且更轻的做法是 ③**：点击胶囊展开成一张**高度自适应**的玻璃卡片，其**顶边与胶囊对齐**，这才是真正的几何连续；要看全部内容再走「查看全部 ›」进 ① 的全高侧栏。

## 11.1 三层统一

### 1）数据统一：标签是「筛选维度」，不是第二种数据

- 收纳夹与剪贴板历史**已经**共用一个面板窗 + 一个 ViewModel（`HistoryPanelViewModel.kt:27` 的 `HistoryFloatingTab.Stash/Clipboard`）。
- 标签挂在 `StashEntry` 上，成为该面板的**第三个筛选维度**，不新建面板、不新建模块。

### 2）表面统一：条与面板 = 同一表面的两个 detent

- 项目里这个配对**已经存在**：边缘把手（`HistoryFloatService`）→ 收纳侧栏（`FloatBallStashPanel`）。
- 现状把手是**哑的**（`HistoryFloatContent.kt:38` 注释原文「仅作为入口」）。统一动作 = **给把手装上信息**，它就变成标签条。
- ⇒ **零新增窗口、零新增入口、零新增边缘占位。** 这是「不显挤」的根本答案。

### 3）边缘统一：互斥规则（防挤的硬保障）

| 边缘「车道」 | 内容 | 规则 |
| :- | :- | :- |
| 热区层（不可见） | 触发把手 | 不占视觉空间，可多 |
| **入口层（可见、可折叠）** | 标签胶囊 / 剪贴板把手 | **同一侧边缘最多一个**；二者**合并为同一个**，由 `stashHandleMode` 决定显示什么 |
| 内容层（临时） | 钉图 / 钉文本 | P2 补「屏上数量上限 + 一键清空」（现状无上限，`ScreenPinManager.kt:268`，是既有的挤） |
| 悬浮层 | 悬浮球 / 指针 | 自由位置，不参与边缘车道 |

> 没有这条互斥规则，标签条必然变成单侧边缘第 4 类可见元素然后打架。

## 11.2 功能范围（三阶段，逐阶段可独立发版）

| 阶段 | 做什么 | 不做什么 | 估工作量 |
| :- | :- | :- | :- |
| **P0｜标签落地** | `StashEntry.tags` + 独立标签清单 + 收纳夹顶部 chip 筛选 + 条目打标/编辑 + 搜索命中标签 | 不做折叠态、不做玻璃、不做常驻窗、不做手势动作 | **0.5～1 人日** |
| **P1｜折叠态 + 采集** | 收纳把手改造成多形态（剪贴板 / 标签 / 隐藏）；折叠态液态玻璃胶囊；长按直接落库的采集浮层；边缘互斥 | 不做常驻展开、不做类型色、不做时间轴 | **+1.5～2.5 人日** |
| **P2｜闭环与收敛** | 条目设提醒（复用 `remind`）、未读点、`xgesture://open/stash?tag=`、钉图数量上限与一键清空 | 不做语音速记 | **+1～1.5 人日** |
| **明确不做** | 本地语音速记（§2.5、§6.3）、常驻展开的标签条、独立新面板、跨应用折射（§4.2）、新增 Gradle 模块、改动 `index.json` 顶层结构（§11.5） | | — |

## 11.3 交互规格

**折叠态**

| 手势 | 行为 |
| :- | :- |
| 点击 | **就地展开快速面板（③）**，顶边与胶囊对齐；默认选中胶囊上高亮的那个标签 |
| 长按 | 就地弹出采集浮层（单行输入 + 自动弹输入法），Enter 落库并自动打上当前标签；**不跳出当前应用** |
| 拖动 | 改纵向位置，松手持久化（复用 `HistoryFloatHandlePosition.resolveY/clampY`，`HistoryFloatHandlePosition.kt:9-17`） |
| 上/下滑 | 在标签间切换（不展开），胶囊随之更新 |
| 双指捏合 | 切换宽度（宽/窄两档，对齐现有 `HistoryFloatHandleWidth.presets`） |

**快速面板 ③ / 全高侧栏 ①**

| 位置 | 行为 |
| :- | :- |
| 快速面板 chip 行 | `全部 · #标签… · ＋`；点击即筛选，长按进入标签管理（重命名/排序/删除） |
| 快速面板条目 | **最多 5 条**，只读摘要 + 点按进取词面板；不做编辑 |
| 快速面板底部 | `查看全部 ›` → 打开全高侧栏 ①（沿用既有手势动作 / Deeplink，语义不变） |
| 全高侧栏 chip 行（P0） | `全部 · 未标注 · #标签… · ＋`；点击即筛选 |
| 全高侧栏条目 | 显示标签小字；长按菜单新增「编辑标签」「设提醒」 |
| 收起 | 点外部 / 返回 → 收回折叠态；**不做常驻展开** |

**能力对照**

| 操作 | 折叠态 ② | 快速面板 ③ | 全高侧栏 ① |
| :- | :- | :- | :- |
| 采集 | ✅ 长按（最短路径） | ✅ | ✅ 顶部＋ |
| 筛选 | ✅ 滑动切标签 | ✅ chip 行 | ✅ chip 行 |
| 打标 | ❌ | ❌ | ✅ |
| 提醒 | ❌ | ❌ | ✅（P2） |
| 钉屏幕 / 复制 / 分享 | ❌ | ❌ | ✅（全部沿用现有） |

## 11.4 UI 规格

**材质（两态共用）**

| 层 | 实现 | 说明 |
| :- | :- | :- |
| 模糊底座 | `LocalFrostedGlassBackdrop`（`overlay/LocalFrostedGlassBackdrop.kt:17`）或 `FLAG_BLUR_BEHIND`+`setBlurBehindRadius` | 跨应用真模糊，已在 9 处量产 |
| 描边高光 | 手写 1px 内描边 + 顶亮底暗 | 抄 miuix 配方思路，**但不引用其 RuntimeShader**（浮层内被 `MiuixOverlayComposeLocals.kt:12-19` 主动关闭，会崩） |
| 内阴影 | 手写（`Modifier.drawWithContent` + 渐变） | 参考 `InnerShadow.kt:37-53` 的参数语义 |
| 抗色带 | 噪点 `0.08f` | 与 `MainBottomNav.kt:176` 同值 |
| 提亮 | 轻微饱和度提升 | 参考 `Vibrancy.kt:14-19` |

**尺寸与动效**

| 项 | 取值 | 依据 |
| :- | :- | :- |
| 折叠态宽度 | 28dp（窄）/ 36dp（宽） | 对齐 `HistoryFloatHandleWidth` 档位（默认 32dp） |
| 折叠态高度 | 自适应，**最多 3 个 chip + 一个 ＋**，永远单行 | §11.6 验收 |
| chip 圆角 / 文字 | 胶囊（宽/2）；10sp，2 字截断 | 参考 `docs/floatwidget-search-panel-reverse.md:444-446`（玻璃胶囊高 40 / 左右内边距 14 / 描边 1.0） |
| 展开态面板 | 沿用现有侧栏尺寸与 squircle 圆角 | `squircleSurface` / `CardSegment`，`docs/ui-guidelines.md:83` |
| chip 行高 | 32dp，水平可滚 | — |
| 展开 / 收起 | 220ms / 160ms | 对齐项目习惯（`ADJUST_INDICATOR_ENTER_MS = 220L`、`EXIT_MS = 160L`） |
| 展开动画 | **chip 行位移 0**，内容 alpha 0→1 + 位移 8dp | 「条长成面板」，不是「打开新界面」 |
| 按压 / 跟手 | `InteractiveHighlight` / `DampedDragAnimation` | 现成 |
| 模糊半径默认 | **4dp** | 对齐液态玻璃底栏 `BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP = 4f`（`OverlaySettings.kt:444`） |

**三段降级（必须全部实现）**

1. 跨窗模糊可用 → 玻璃（半透明 + 模糊）。
2. `isCrossWindowBlurEnabled == false` / 省电模式 → **不透明 `surfaceContainer` + 描边高光**（观感仍成立，不能塌成半透明砖）。参考现有分支 `SearchPanelOverlayWindow.kt:251-254`、`LiquidGlassNavigationBar.kt:492-494`。
3. 隐藏 API 反射失败（`createBackgroundBlurDrawable`）→ 同 ②，已有 `runCatching` 兜底（`LocalFrostedGlassBackdrop.kt:51-67`）。

**硬限制规避**

- 模糊半径滑杆上限按密度反推：`≤ 80px / density`（窗口层 clamp `1..80`，`OverlayFullScreenPanelHost.kt:145`），否则控制项是骗人的。
- **不使用** miuix RuntimeShader 系（浮层软件 Canvas 会崩）。
- **不承诺**折射效果（§4.2）。

## 11.5 数据规格

**字段**

```kotlin
// StashEntry.kt  —— 只存标签名，不存 id（可读、迁移零成本）
val tags: List<String> = emptyList()
```

**标签清单（独立文件，必须独立）**

```kotlin
// 新文件 stash/StashTag.kt
@Serializable data class StashTag(val name: String, val order: Int, val createdAtEpochMs: Long)
object StashTagLimits { const val MAX_TAGS = 20; const val MAX_NAME_LENGTH = 12 }
```

- 存放：`filesDir/stash/tags.json`（与 `index.json` 同目录、**同一把跨进程文件锁**）。

**⚠️ 为什么绝不能把标签塞进 `index.json`**

`index.json` 的顶层结构是 `List<StashEntry>`（`StashRepository.kt:401-406`）。若为加标签改成 wrapper 对象（`{entries:[…], tags:[…]}`），则**旧版本会解析失败**：`readFromDisk()` 的 `getOrDefault(emptyList())` 会静默返回空表，用户下一次新增条目时 `writeToDisk([新条目])` 就会**把整份收纳夹覆盖为空**（`StashRepository.kt:93-96,408-410`）。这是**旧版本降级导致数据全丢**的灾难级路径。
⇒ **顶层结构不可动，标签清单必须独立文件。**

**跨进程一致性**

- 所有标签写操作复用 `withCrossProcessWrite`（`StashRepository.kt:54-61`，含 `CrossProcessStore.withFileLock` + `notifyChanged`）。
- `CrossProcessStore.registerListener`（`StashRepository.kt:67-80`）**需要增加 `tagsFile` 分支**，否则另一进程改了标签本进程不刷新。

**标签生命周期（解决 §7-5）**

- 条目超出 `MAX_ENTRIES = 200` 被裁剪时（`StashRepository.kt:412-416`），**标签留在 `tags.json` 里不消失**——这就是独立清单的第二个理由：标签能常驻，条目可过期。
- 删除标签：从 `tags.json` 移除 + 批量从所有条目的 `tags` 里剔除（同一把锁内完成）。
- 重命名标签：改 `tags.json` + 批量改所有条目的 `tags`（锁内）。
- 空标签（0 条目）保留，显示计数 0，由用户手动删。

**兼容性**

- 读：`ignoreUnknownKeys = true`（`StashRepository.kt:40`）+ 新字段默认值 ⇒ 旧数据无 `tags` 正常读。
- 写：旧版本回写 `index.json` 会丢条目上的 `tags`（但 `tags.json` 仍在，标签名不丢，只是关联丢失）。**此行为须写进 CHANGELOG。**

## 11.6 改动清单

### P0（标签落地）

| 文件 | 改动 | 估行数 |
| :- | :- | -: |
| `app/.../stash/StashEntry.kt:15-27` | `+ val tags: List<String> = emptyList()`；`matchesQuery(:65-70)` 加标签命中；`hasTag()` | +14 |
| `app/.../stash/StashTag.kt`（新） | `StashTag`、`StashTagLimits`、名称规范化（trim/去重/去空/长度截断） | +45 |
| `app/.../stash/StashRepository.kt` | `tagsFile`、`_tags: StateFlow<List<StashTag>>`、`setEntryTags`、`createTag`、`renameTag`、`deleteTag`、监听 `tagsFile`（`:67-80`） | +110 |
| `app/.../overlay/history/HistoryPanelViewModel.kt` | `selectedTag: StateFlow<String?>` 并入 `filteredStashEntries` 的 `combine`（`:53-60`） | +30 |
| `app/.../overlay/history/HistoryPanelScreen.kt`＋`HistoryStashTabBody(:382-)` | 顶部 chip 行（全部/未标注/#标签/＋）+ 空态文案 | +80 |
| `app/.../overlay/history/HistoryEntryCards.kt` | 条目标签小字 + 长按「编辑标签」（`HistoryStashEntryCard:271`） | +45 |
| 标签管理浮层（新，对话框/浮层） | 重命名/排序/删除；按 `docs/ui-guidelines.md:62-69` 用 `settingsCardItems{}.RenderRows()` | +120 |
| settings 5 文件（`AppSettingsSlices` / `OverlaySettings` / `SettingsSnapshotReader` / `OverlaySettingsMutator` / `SettingsRepository`） | `stashTagsEnabled`（默认 true） | +30 |
| `res/values{,-zh,-ja,-ar}/strings.xml` | ~8 条文案 | 小型 |
| 测试 | `StashTagTest`、`StashRepositoryTagsTest`（旧 JSON 无 tags 可读 / 旧版本回写不崩 / 裁剪后标签仍在 / 跨进程序列化往返） | +70 |

### P1（折叠态 + 采集）

| 文件 | 改动 | 估行数 |
| :- | :- | -: |
| `app/.../service/HistoryFloatService.kt`＋`overlay/history/HistoryFloatContent.kt` | 多形态（`stashHandleMode`: CLIPBOARD / TAGS / HIDE）；折叠态渲染标签 chip；上滑切换标签 | +180 |
| 采集浮层（新，或复用 search panel 的 IME 机制） | 单行输入 + 自动弹输入法 + Enter 落库；抄 `SearchPanelOverlayWindow.kt:290-341` 的 `requestImeShow` / `focusSearchFieldSoon` / 双路径 `showIme`，配 `OverlayImeInsets.kt:23` 读 IME 高度 | +200 |
| 折叠态玻璃胶囊 UI（新） | `LocalFrostedGlassBackdrop` 底座 + 手写描边/内阴影/噪点 + 三段降级 | +250～400 |
| 快速面板 ③（新） | **高度自适应**玻璃卡片（顶边与胶囊对齐，最多 5 条 + `查看全部 ›`） | +220 |
| `overlay/FloatBallStashPanel.kt`＋`OverlayPanelLayoutParams.kt:51` | ③ 与 ① 共用 ViewModel；② 展开时自动隐藏 ②（互斥）；`OverlayPanelLayoutParams` 新增自适应高度变体 | +70 |
| settings（5 文件） | `stashHandleMode`、`stashTagBarBlurEnabled`（默认 false）、`stashTagBarBlurRadiusDp`（默认 4） | +45 |
| 手势动作（可选） | 「快速闪念」：`core/gesture/.../GestureAction.kt`（`:67,:429,:805` 三处）+ `GestureActionIconBitmap.kt:119` + `GestureActionOutlinedIcons.kt:41,133` + `GestureActionPickerSearch.kt:295,444,640` + `GestureActionCatalog.kt:171` + 4 locale | +80（6 文件，穷尽 `when` 会编译报错，防漏改） |
| 设置页 | 折叠态开关（**默认关**）、模糊开关与半径滑杆（上限按 density 反推） | +70 |

### P2（闭环与收敛）

| 项 | 改动 | 估行数 |
| :- | :- | -: |
| 条目设提醒 | 长按菜单 → 复用 `remind/RemindAlarmScheduler.kt` + `RemindDurationPickerOverlay.kt` | +80 |
| 未读点 | 折叠态单个小圆点（**不显示数字**，§11.4） | +30 |
| Deeplink `?tag=` | `AppLinks.kt` + `StashPanelLaunchState` + README Deeplink 表 | +50 |
| 钉图数量上限 + 一键清空 | `ScreenPinManager.kt:268` 的 `pins` map 加上限与清空入口（顺带治既有挤） | +70 |

**P0+P1 合计：约 6～11 人日（含 UI 打磨与真机适配），无新增依赖、无新增 Manifest 权限、无新增模块、Lite/Full 均不增包。**

## 11.7 实施顺序与验证

| 步 | 做什么 | 验证 |
| :-: | :- | :- |
| 0 | 定 §11.9 默认值（无异议即按默认施工） | — |
| 1 | P0 数据层：`StashEntry.tags` + `StashTag` + 仓库读写 | 单测全绿；`.\gradlew.bat compileDebugKotlin` |
| 2 | P0 UI：chip 筛选 + 打标 + 标签管理浮层 | 真机：打标 → 杀进程重开 → 仍在；搜索命中标签 |
| 3 | **发版观察**：标签是否真被用起来 | 这是 P1 的**准入条件**，不是流程形式 |
| 4 | P1 折叠态：把手多形态 + 玻璃胶囊 UI | 三类设备截图基线（支持模糊 / 不支持 / 高密度） |
| 5 | P1 采集：长按落库 + 输入法 | 真机覆盖主流输入法（§11.8 失败模式 ①） |
| 6 | P1 开关与互斥 | 关闭时不创建常驻窗（服务列表里不应出现该 Service） |
| 7 | P2 闭环 + 钉图收敛 + CHANGELOG / README / Deeplink 表 | 回归 |

## 11.8 验收标准（可量化）

1. **采集**：在当前应用内，长按 → 输入 → 落库 ≤ 2 步，**全程不跳出当前应用**。
2. **筛选**：任意标签 ≤ 1 次点击可达。
3. **默认配置**：屏上常驻**可见**元素 **0 个**（折叠态默认关）。
4. **折叠态**：永远单行、≤ 4 个元素（3 chip + ＋），不显示时间/计数。
5. **降级**：支持模糊 / 不支持模糊 / 高密度屏三类设备观感均成立，无「半透明砖」。
6. **性能**：开玻璃 vs 关玻璃的列表滚动帧率差可测且可接受（用 `core/monitoring` / `DebugPerformanceOverlay`）。
7. **一致性**：标签在收纳面板与折叠态之间**不出现双份入口、不出现状态不同步**。
8. **覆盖**：4 套 locale 文案齐全；Lite/Full 双包均不增体积；旧版本回写不丢条目。

## 11.9 默认决策表（我替你拍板，如需不同请直接说）

| # | 决策点 | 默认取值 | 理由 |
| :-: | :- | :- | :- |
| 1 | 「标签」语义 | **用户自由多标签**（读法 1.2-b） | 最通用；预置类型可后加，反向则难 |
| 2 | 折叠态是否常驻 | **默认关**，用户开启后默认驻留 | 对齐 `clipboardFloatEnabled=false`、`stashPanelBackgroundBlurEnabled=false` 惯例 |
| 3 | 折叠态形态 | **竖向贴边胶囊** | 与现有把手同构，零新增边缘占位；横向条会与通知卡/导航争空间 |
| 4 | 标签在展开态的形态 | **Stash tab 顶部 chip 行**（不做第三个 tab） | 标签是筛选维度，做成 tab 会被误认为另一种数据 |
| 5 | 折叠态与剪贴板把手 | **合并为一个「收纳把手」**，`stashHandleMode` 三选一 | 满足边缘互斥规则（§11.1-3） |
| 6 | 标签清单存放 | **独立 `tags.json`** | 避免旧版本降级清空收纳夹（§11.5），且标签不随 200 条裁剪消失 |
| 7 | 语音速记 | **不做** | 全仓无 ASR，增包与 Lite 定位冲突，半成品更伤口碑 |
| 8 | 液态玻璃档位 | **真模糊 + 仿真高光，不承诺折射** | 折射跨应用不可发布（§4.2） |
| 9 | 数据模型形状 | **只加字段，不动 `index.json` 顶层结构** | 兼容性红线（§11.5） |

## 11.10 三个可能的失败模式（施工时反复对照）

1. **入口冗余** → 做完了没人点。**对策**：折叠态默认关 + P0 先验证「用户会不会打标签」（§11.7 步 3 是硬准入）。
2. **把玻璃当卖点** → 功能本身是空的。**对策**：玻璃是材质，不是功能；宣传语只讲「任意应用上两秒记一件事」。
3. **采集是半成品** → 输入法不弹/遮挡 = 用户判定「做得不好」。**对策**：这一块是**全项目最 ROM 敏感的一处**（`SearchPanelOverlayWindow.kt:287` 注释承认部分机型/输入法吞掉每次请求），必须投入真机覆盖，不能只靠代码正确。

---

# 12. 大胆重构：胶囊即实体（约束已解除）

> §11 是在「不新增表面 / 不改变默认 / 与收纳面板同宽」三条约束下的 **minimal diff** 方案——天花板必然是「给旧面板加一行」。
> 本节是**解除这三条约束**后的方案。可视稿：[capsule-stream.png](mockups/capsule-stream.png) · 交互稿：**[ui_demo_capsule.html](../../ui_demo_capsule.html)**
> **§11 不作废**：它仍是低成本、低风险的第一阶段；§12 是它的**升维**，两者数据层完全共用。

## 12.1 核心转变：从「列表里的一行」到「挂在边缘的实物」

| | §11（增量） | §12（重构） |
| :- | :- | :- |
| 记录是什么 | 列表里的一行 | **挂在屏幕边缘的胶囊实体**，看得见、点得开、滑得走 |
| 胶囊显示什么 | 标签名（竖排 3 个） | **内容的前 2 个字，横向排**（72×44 胶囊）—— 不打开就知道最近记了什么；且拉丁/数字不会被竖排摞歪（§12.9） |
| 编辑怎么发生 | 打开面板 → 找条目 → 进编辑 | **点胶囊，它自己横向展开成编辑条**，光标已在原文末尾，接着打字就是追加 |
| 面板的骨架 | 按标签分组的列表 | **竖向时间轴**：今天 / 昨天 / 更早 |
| 玻璃的角色 | 皮肤（贴在胶囊和卡片上） | **信息编码**：填充与描边亮度 = 时间距离（记忆褪色） |
| 常驻元素 | 默认 0 个 | **默认也几乎为 0**：三态生命周期，沉淀态只剩一条 9×28dp 细指示条（见 §12.8） |

## 12.2 四个交互（这就是全部，没有别的页面）

1. **呼出**：长按边缘热区或点链顶的「＋」→ 输入槽**从边缘就地横向长出**（宽 44 → 286，`width` 过渡 260ms），不居中、不遮内容；获焦即弹输入法。
2. **落成**：回车 / 点 ✓ → 输入槽**收缩成一颗新胶囊插到链顶**（FLIP + 淡入缩放）；若链已满 4 颗，最旧的一颗先播「流走」动画（右移 + 缩小 + 淡出），随后提示「已归档到收纳面板 · 暂存夹」。
3. **就地编辑 / 追加**：单击任意胶囊 → **它自己横向展开成编辑条**（`transform-origin: right center`），`textarea` 预填原文、`setSelectionRange(len, len)` 把光标放到末尾 → **接着打字就是追加**；条内可直接打标签、设提醒、归档、删除。**全程不跳转任何页面。**
4. **时间流**：从右缘左滑或点「打开时间流」→ 时间轴面板；顶部浮动 chip 做**标签叠加筛选**（选中的标签会让不相关条目收起，但**时间刻度保留**，能看到「这类想法在时间上的分布」）。

## 12.3 复用什么（几乎全是既有资产）

| 新形态需要什么 | 复用什么 | 位置 |
| :- | :- | :- |
| 数据与持久化 | `StashRepository` + 独立 `tags.json` + 跨进程写口 | `app/.../stash/` |
| 胶囊挂屏（实体化） | **`ScreenPinManager` 的钉图机制**（多实例、可拖动、生命周期管理） | `app/.../overlay/ScreenPinManager.kt:268` |
| 边缘常驻窗 + 位置持久化 | `HistoryFloatService` + `HistoryFloatHandlePosition` | `app/.../service/HistoryFloatService.kt` |
| 呼出后弹输入法 | `SearchPanelOverlayWindow.requestImeShow / focusSearchFieldSoon` + `OverlayImeInsets` | `app/.../overlay/searchpanel/`、`overlay/OverlayImeInsets.kt:23` |
| 玻璃底座 | `LocalFrostedGlassBackdrop` / `setBlurBehindRadius` | `app/.../overlay/LocalFrostedGlassBackdrop.kt:17` |
| 落成/流走动画 | `DampedDragAnimation` 的阻尼思路 | `ui/miuix/bottombar/animation/` |
| 时间轴的标签 chip | §11 的 `StashTag` + chip 实现 | 本文件 §11.5 |
| 提醒 | `RemindAlarmScheduler` | `app/.../remind/` |

## 12.4 玻璃在这里终于是功能，不是皮肤

1. **记忆褪色**：条目按时间分组给不同的填充/描边强度 —— 今天（`--tint` alpha 1.0 / rim 1.0）、昨天（0.55 / 0.55）、更早（0 / 0.25，只剩文字）。**时间距离被编码进玻璃的不透明度。**
2. **上下文透出**：胶囊与面板的玻璃里透出的是它背后**真实内容**（挂在阅读 App 上就透出那篇文章的颜色）。这是 `backdrop-filter` / `createBackgroundBlurDrawable` 天然给的能力，不需要额外成本。
3. **拖动高光**：拖拽胶囊时镜面高光沿运动方向拉伸（CSS/Compose 上都是给高光层加一个跟随 dx 的 `translate + skew`）。
4. **仍然不做折射**（§4.2 结论不变：跨应用拿不到底图像素）。

## 12.5 成本与风险

| 项 | 量级 |
| :- | :- |
| 数据层 | **0 新增**（与 §11 共用 `StashEntry.tags` + `tags.json`） |
| 新 UI | 胶囊链 ~150 行 + 输入槽 ~120 行 + 编辑条 ~220 行 + 时间流面板 ~350 行 + 动画 ~100 行 ≈ **950 行** |
| 宿主 | 复用 `OverlayComposeOwner`，**不新增 overlay 宿主**；胶囊挂屏复用 `ScreenPinManager` |
| 估工作量 | 约 **5～8 人日**（含真机适配与输入法覆盖） |
| 新依赖 / 新权限 | **0 / 0** |
| 主要风险 | ① 边缘胶囊可能与既有触发把手/悬浮球**抢触摸区**（须复用边缘互斥规则，§11.1-3）② 输入法遮挡（最 ROM 敏感，同 §11.10-3）③ 时间流面板 78% 宽会盖住内容，需明确「松手即收」④ 胶囊上限 4 颗是硬编码，建议做成可配置 |

## 12.6 分期

| 阶段 | 内容 | 说明 |
| :- | :- | :- |
| **A｜数据层** | `StashEntry.tags` + `StashTag` + `tags.json` | **与 §11 的 P0 完全相同**，两方案共用，先做不亏 |
| **B｜胶囊实体** | 胶囊链 + 挂屏（复用 `ScreenPinManager`）+ 呼出输入槽 + 落成/流走 | 这一步就已经"有记忆点"了 |
| **C｜就地编辑** | 点胶囊 → 展开编辑条 + 追加 + 打标签 + 归档 | 回应用户「呼出后可直接编辑、添加」 |
| **D｜时间流** | 时间轴面板 + 记忆褪色 + 标签叠加筛选 | |
| **E｜回流** | 归档条目落进既有收纳面板暂存夹（带标签） | 与 §11 的 chip 行接上，数据一份 |

**建议**：A 立刻做（与 §11 的 P0 是同一件事）；B+C 是本方案的价值核心，值得先跑原型；D 视觉收益最大但可后置。

## 12.7 信息架构修正：剪贴板与暂存夹去哪了（**早期草案的漏项**）

> 本节修正 §12.1–12.2 的一个真实疏漏：追加「时间流面板」时，我把收纳面板整个换掉了，于是**剪贴板历史在新方案里消失了**、「暂存夹」也只剩一句 toast 提示、而屏上**没有任何可见的新建入口**。以下为修正。

**面板 = 两个页签（同一个面板、同一份数据、既有入口全部不变）**

| 页签 | 内容 | 与旧方案的关系 |
| :- | :- | :- |
| **内容流** | 时间流（今天 / 昨天 / 更早）+ **来源筛选**（只看闪念 / 全部内容）+ 标签叠加筛选 | **新增**；闪念与暂存夹合并于此 |
| **剪贴板** | 系统剪贴板历史（Shizuku 后台监听的那一份） | **原样保留**，能力与入口零改动 |

> **为什么不设「暂存夹」页签**：闪念与暂存夹本来就是**同一个 `StashEntry`**（`source` 字段区分），分两个页签等于给同一张表做两次筛选。合并成一个流、用来源筛选区分即可；图片与取词富文本带**来源标记**（图片 / 取词 / 剪贴板），归档的闪念标「已归档」。
> **默认范围取决于入口**（兼容性红线）：内部入口（胶囊链的「查看全部」）默认 **只看闪念**；外部契约（`xgesture://open/stash` 等 5 个既有入口）默认 **全部内容** —— 否则 Tasker / 桌面快捷方式唤起后，用户会发现自己的东西"不见了"。

- **归档语义**：胶囊「归档」= 从边缘摘下（不再是活跃实体），条目落进**暂存夹**并带标签；它不再出现在时间流顶部，但**仍在闪念页签的时间轴里**（按创建时间归位）。这两处是同一份数据的两个视图，不是两份数据。
- **既有 5 个入口一个都不改**：手势动作 `OPEN_STASH_PANEL(43)`、Deeplink `xgesture://open/stash`、桌面快捷方式、悬浮球动作、剪贴板浮窗 —— 打开的都是这个三页签面板，默认落在「暂存夹」（与今天的行为一致）。
- **边缘互斥**：胶囊链与既有「剪贴板历史边缘把手」（`HistoryFloatService`）都占右侧边缘 → 二选一，由设置决定（闪念链 / 剪贴板把手 / 都不显示），**默认闪念链**。剪贴板的可达性靠它原有的入口 + 面板页签兜住。

**新建入口必须可见（屏上，不能只藏在设置或外侧按钮里）**

| 入口 | 形态 | 说明 |
| :- | :- | :- |
| **链顶的「＋」** | 虚线描边的空胶囊，常驻在链的最上方 | **永远看得见**；点击 → 输入槽从它就地展开。新的胶囊从它下面进来，语义自洽 |
| **边缘热区长按** | 不可见热区，长按 ~380ms | 复刻原版「长按硬件键」的肌肉记忆；不需要先找到按钮 |
| **手势动作「闪念速记」** | 现有 50+ 动作体系里新增一项 | 长滑触发条 / 悬浮球动作 → **直达输入槽**。这是真正的「低操作成本」路径（`GestureAction` 穷尽 `when` 会编译报错，防漏改） |

> 教训记在这里：**「记录」这个动作必须有一个常驻、可见、且语义正确的入口。** 只把它放在设置页或演示页的外侧按钮里，等于没做。

## 12.8 常驻是错误的：三态生命周期（**早期草案的第二个漏项**）

> §12.1–12.2 让胶囊链**常驻**在右缘——这不符合手机实际使用。两条硬理由，都有仓库证据。

### 为什么不能常驻

| 问题 | 事实 | 证据 |
| :- | :- | :- |
| **霸占系统返回手势区** | Android 10+ 的返回手势从屏幕两侧边缘向内滑；本仓库自己按 **48dp** 预留这条边 | `OverlayPanelSystemGestureExclusion.kt:16`（`BACK_EDGE_DP = 48f`） |
| 要放可点元素就必须**申请手势排除区** | 仓库里已有两套工具专门干这事，说明这是踩过的坑 | `OverlayPanelSystemGestureExclusion.kt:72`、`EdgeTouchCaptureView.kt:81`、`EdgeSystemGestureExclusionView.kt:29` |
| 排除区**有系统上限** | 一条 ~250dp 高的常驻链条需要的排除区远超合理范围；超出部分会被系统忽略（精确上限**需在目标 ROM 上核实**，OEM 通常更严） | 待真机核实 |
| **遮挡右侧内容** | 聊天发送键、播放控制、列表侧滑操作都在右侧；常驻链条长期压在上面 | — |

### 修正：胶囊不是常驻 UI，而是**有寿命的状态**

| 状态 | 何时 | 屏上有什么 | 手势排除区 |
| :- | :- | :- | :- |
| **① 瞬时态** | 刚记录后 **20～30s**（或切应用 / 息屏即结束） | 只露**最新那一颗 + 「＋」**（约 56×150dp），呼吸光提示 | 按自身足迹申请，**限时** |
| **② 沉淀态（默认）** | 其余全部时间 | 有闪念时只剩一条 **9×28dp 细指示条**；有未处理事项时**只让这条指示条变色**——不出数字、不加宽、不新增常驻物 | 只排除指示条自己那一小块（约 21×28dp），**返回手势在其上下照常可用** |
| **③ 展开态** | 主动呼出后（点指示条 / 长按右缘热区 / 手势动作「闪念速记」） | 完整链条 | 按需申请；点外部 / 返回 / 12s 无操作即收回 |

**原则（修正过一次，见下）**：**常驻只配得上「事件」，配不上「状态」。**

### 修正：待办不常驻（早期草案的第三个漏项）

早期草案写的是「**存在未处理事项（待办未勾 / 提醒到点）时，显示一颗 40dp 计数胶囊**」。**这条规则自我拆台**：

- **「未勾完的待办」是状态，不是事件**。状态会持续好几天 → 「有未处理才常驻」实际等于「**只要有任何一件没做完的事，它就永久常驻**」。它不是例外，它就是常驻本身，与 §12.8 开头论证的「48dp 返回手势区 + 遮挡右侧内容」直接冲突。
- **待办提醒本来就有专门通道**：到点提醒走通知 / 闹钟（`RemindAlarmScheduler`），那是**推送**，不占屏。用常驻数字替代推送是退步。
- **计数胶囊信息量太低**：「2 待办」不告诉你是哪两件、什么时候到期——只能制造焦虑，不能推动行动。
- **它会把闪念变成待办 App**，与「只做快速记录、不做完整任务 App」的定位冲突。

**改法**：沉淀态 **0 个例外**。有未处理事项时只让那条 9×28 指示条**变色**（accent 色），**不出数字、不加宽**。真想一直看见待办的用户应该用桌面小组件 / 通知常驻，而不是让边缘多一个常驻物。

| 东西 | 性质 | 该不该占屏 |
| :- | :- | :- |
| 刚记的那颗胶囊 | 事件 | 占，20~30s 后沉淀 |
| 到点提醒 | 事件 | **不占屏**，走通知 / 闹钟推送 |
| 未勾完的待办 | **状态** | **不占屏** |
| 用户**显式钉住**的内容 | 用户意图 | 占（既有的钉图 / 钉文本，本来就有） |

### 几何上的诚实说明

- 细指示条与计数胶囊放在 `right: 12dp` 处，**仍落在 48dp 返回手势区内**——它们靠**申请自身足迹大小的排除区**换取可点性（这就是仓库既有工具的做法）。
- 要**完全避开**返回手势区，元素必须往内挪 **≥48dp**，那会明显侵入内容区、更难看。**取舍：宁可让出一小块返回手势区，也不要一个侵入内容的常驻物。**
- 展开态是短暂的、用户主动唤起的，可以接受较大排除区；沉淀态必须是**最小**的。

### 还要复用的既有行为

- **全屏 / 横屏隐藏**：`HistoryFloatService.updateFullscreenVisibility()` / `updateLandscapeVisibility()`（`:218-239`）已有全套逻辑，直接复用。
- 追加：**锁屏 / 游戏 / 桌面**（可配）自动隐藏。
- **可完全关闭**、**可换左边**（现在只有右缘）。

### 对实现的影响

- §12.6 的 B 阶段拆成 **B1 瞬时态 + 沉淀态**（最小可用，风险低）与 **B2 展开态**（链条核心交互）。
- 数据层 A 阶段不变（仍与 §11 的 P0 同一件事）。

### 一个踩过的坑（写下来免得再犯）

**常驻/沉淀类指示物必须显式指定纵向位置。** 在互稿里把 `.pip` / `.due` 写成 `position: absolute` 却忘了给 `top`，CSS 会让它们退回**静态位置**——也就是手机顶部，于是两条指示物跑进了状态栏。Compose 侧同理：`Modifier.align()` / `offset` 必须给明确锚点，不要依赖「默认居中」。

自检办法：对样式表里每个 `position: absolute` 的规则断言它含有 `top`/`bottom`/`inset` 之一（本仓库的 demo 页已加这条断言）。

## 12.9 胶囊文字改横排（44×68 竖排 → 72×44 横排）

**原设计**：胶囊 44 宽，把内容前 2 字用 `flex-direction: column` 摞起来。**两个问题**：

1. **竖排 2 字没有词感**：两个孤立的字摞着会读成"把"和"描"两个东西，而不是"把描…"这个被截断的开头。而且那只是"摞行"，不是真正的竖排排版（真要竖排得用 `writing-mode: vertical-rl`，才拿得到正确的字距与标点朝向）。
2. **拉丁与数字会坏**：内容以 `Q3` 开头时，前 2 字是 `Q3`，摞起来变成"Q 上 3 下"——明显是错的。这条基本决定性。

**改法**：胶囊改成 **72×44**，文字单行横排（`white-space: nowrap` + `text-overflow: ellipsis`）。
**代价**：链条往内容区多伸约 28px（胶囊外缘距边 82px）。
**为什么现在可以接受**：胶囊**不常驻**（§12.8 三态），只在瞬时态与展开态出现；唯一必须保持极小的是沉淀态那条 9×28dp 的细指示条，它本来没有文字。
**附带好处**：横排后落在 48dp 返回手势区内的只剩胶囊左侧一段，需要申请的排除区比原来更小。

## 12.10 边缘时间轴（rail）：它是什么、以及不许把它画歪

**它是什么**：链条左侧那条竖线 + 每颗胶囊横向伸出的 **10px 刻度线**，顶端一个小圆点是 **「现在」标记**。作用是让堆叠的胶囊读成"同一根时间轴上的点"，而不是一串浮动按钮。

**三条硬规则**（都曾画错）：

1. **「＋」不是时间点**，必须排除在时间轴之外 —— 否则时间轴会从"新建"按钮开始画。
2. **「现在」标记落在最新那颗闪念上**，不是落在「＋」上。
3. **只有 ≥2 颗闪念时才画** —— 只有一颗时它就是一截孤零零的竖线，会被当成渲染错误（真机上被报过）。

**几何算法**（`railGeom(n, hasAdd)`）：链条在 844 高里垂直居中，`total = items × 44 + (items − 1) × 12`，`top0 = 422 − total / 2`；
若有「＋」则时间轴起点跳过 `44 + 12`；`top = 最新中心 − 7`，`height = 最旧中心 − 最新中心 + 14`。
**验证数**（＋ + 3 颗）：`top = 387`，`height = 126`，覆盖 `387~513`，而胶囊中心是 `394~506`、「＋」底是 `360` ✓。

**手势分工**（同一片区域里三个手势，必须显式分派）：

| 手势 | 作用 | 实现 |
| :- | :- | :- |
| 点胶囊（位移 < 6px） | 就地编辑 | pointerup 判定 |
| **胶囊上左滑（> 40px）** | **归档** | 拖拽中 `translateX`，过阈值加 accent 描边 + 「松开归档」提示 |
| **边缘空白处左滑（> 40px）** | **打开闪念面板** | 胶囊上的 `pointerdown` 必须 `stopPropagation()`，否则会被边缘热区抢走 |
| 长按右缘热区（380ms） | 呼出输入槽 | 边缘热区 |

**同类坑（写下来免得再犯）**：互稿里 `railGeom` 被定义了两次（旧版返回字符串、新版返回对象），后定义的**静默覆盖**前者，于是 `rail.top` 变成 `undefined`、时间轴位置算不出来。**自检：对脚本里每个 `function <name>(` 断言名字不重复。**

## 12.11 入口必须是「把手」，不是「整条边缘」

**早期实现错在哪**：为了让「随时能记」零摩擦，我把**整条右侧边缘**（全高 48~62dp 带）做成了不可见热区——长按=新建、左滑=打开面板。这是**功能性错误**，而且踩了我在 §12.8 刚论证过的同一块地：

1. **整条右缘就是系统返回手势区**。在那里挂不可见、全高、拦截触摸的热区，用户想从右缘返回，滑出来的是闪念面板 —— **功能被破坏**，不是"体验不佳"。
2. **与「沉淀态屏上几乎无物」自相矛盾**：看不见，但一直在拦。
3. **排除区无法解决**：为整条边申请手势排除等于让用户的返回手势在那一侧彻底失效。

**正确做法：入口 = 那条贴边指示条本身**，也就是**复用 `HistoryFloatService` 的把手范式**（常驻窗 + 可拖 Y + 位置持久化 + 全屏/横屏隐藏），不新造热区。

| 入口 | 手势 | 说明 |
| :- | :- | :- |
| **贴边指示条**（9×28 视觉 + **32×44 命中区**） | 点 = 打开闪念面板；长按(380ms) = 直接记录 | 命中区放大到 32×44 保证可点，**排除区也只有 32×44**，返回手势在其上下照常 |
| 链顶「＋」 | 点 = 呼出输入槽 | 链条可见时（瞬时/展开态） |
| 链底「查看全部 ›」 | 点 = 打开面板 | **仅展开态**出现（沉淀态入口是指示条） |
| **手势动作「闪念速记」** | 用户自行配到触发条 / 悬浮球 | **「随时能记」的正确归属**：复用仓库既有的 50+ 动作体系与它自带的手势排除处理，而不是第二条不可见热区 |

**结论一句话**：**新能力要挂到既有的把手/动作体系上，不要自己再圈一块屏幕。** 项目里已经有把手（`HistoryFloatService`）、有 50+ 手势动作、有手势排除工具（`OverlayPanelSystemGestureExclusion`），凡是"想在任何地方都能触发"的诉求，答案都是**配一个手势动作**，不是**占一条边**。

## 12.12 锚定：把手在哪，链条就从哪长出来

**早期实现的"莫名其妙"从哪来**：把手在 `top:44%`、视觉宽 9dp；展开后的链条却**垂直居中**在屏幕正中、宽 72dp。两者没有任何空间关系——所以链条看起来是**凭空出现**的。更糟的是把手声称"可拖 Y"（把手范式），但拖完之后展开链条**完全不理会它的位置**，拖动因此毫无意义。

**规则（一句话）**：**你碰的那个东西，就是它出现的地方。**

| 项 | 取值 |
| :- | :- |
| 链条顶端 | `把手 Y − 22`（＝让第一个元素中心对齐把手中心） |
| 展开动画 | 第一个元素 `transform-origin: right center`，`scale(.26) translateX(26px) → 1`，240ms easeOut —— 看起来是**把手就地长成链条** |
| 纵向拖动 | 把手可拖 Y 并持久化（复用 `HistoryFloatHandlePosition` 的 `resolveY/clampY` + 横竖屏分别记忆），**链条跟着它走** |
| 纵向可拖范围 | 由"链条最短需要的高度"反推：`items × 44 + (items−1) × 12`；上限 4 颗 + 「＋」= 268dp，故 demo 取 `[96, 520]` |
| 同一元素两个手势 | **前 8dp 锁定主方向**：横向 = 拉出面板；纵向 = 挪把手。不锁方向就会互相打架 |
| 时间轴 / 「查看全部」 | 全部由链条顶端推导（`railGeom(n, hasAdd, chainTop)`），所以把手一动，时间轴和脚注一起跟着走 |

**验证数**（＋ + 3 颗）：把手 Y = 371 → 链条顶 349、时间轴 420~546、脚注 571；
把手拖到 Y = 200 → 链条顶 178、时间轴 249~375、脚注 400 —— **整组一起移动** ✓。

### 附：横向列表只管滑 —— 不要加滚动条
标签 chip 行是横向滚动容器。**这是 Android 应用**，正确做法只有一条：

```css
overflow-x: auto;  scrollbar-width: none;  .chips::-webkit-scrollbar { display: none; }
```

配一条右缘渐隐做"还有更多"的提示，剩下交给**原生触摸滑动**。

> **踩过的错**：为了在桌面浏览器里用鼠标测这份演示稿，我曾加过 `@media (hover: hover)` 下"恢复一条 3dp 细滚动条"，并且把它当成产品实践写进了文档。**这是错的** —— 桌面思维渗进了安卓设计。滚动条在安卓上既不该出现、也不需要出现。
> 桌面演示稿里那点 pointer 拖拽滚动属于**脚手架**（触屏分支直接 `return`，走原生滑动），不是设计行为，不该回写到产品规格。

## 12.13 终局：整条链条砍掉（本轮最终决定）

前十二节都在**给链条做加法**（三态、时间轴、锚定、把手门槛……），越做越复杂。用户看完实机截图后的判断是「**整个链条砍掉算了**」——**这是对的**，因为链条唯一无法被面板替代的价值只是「记录有实体」这个感觉，而它为这个感觉付出的复杂度是：

| 链条带来的复杂度 | 具体 |
| :- | :- |
| 屏上常驻元素 | 把手 + 链条 + 时间轴 + 「＋」+「查看全部」（5 类） |
| 状态机 | 沉淀 / 瞬时 / 展开 **三态** —— 用户得先判断"现在屏幕上是什么状态"，这是**认知成本**，比"多点一下"贵得多 |
| 新建入口冗余 | 3 个（长按把手 / 手势动作 / 链条「＋」），而且「＋」**反而多一步**：得先拉出链条才看得见它 |
| 需要解释的元素 | 时间轴（用户问过两次「叫啥」）—— **需要解释才成立的 UI 通常就是多余的** |
| 「归档」概念 | 存在的全部意义是"从边缘摘下来"；**没有边缘，归档就没有定义** |

### 砍掉后的模型（终局）

| 项 | 内容 |
| :- | :- |
| **屏上常驻元素** | **1 个**：贴边把手（视觉 9×28dp，命中区 32×44dp），复用 `HistoryFloatService` 把手范式 |
| **状态机** | **0 个** —— 没有三态，屏上永远是"一条把手" |
| **入口** | **2 条，都不藏**：① 拖/点把手 → 面板 ② 长按把手 → 直接记；另有既有手势动作「闪念速记」配到触发条/悬浮球 |
| **记录反馈** | 把手**轻脉冲 900ms** + toast（没有链条可挂，就不假装有） |
| **面板** | 仍两页签（闪念 / 剪贴板）；闪念页默认只看闪念，标题行可切「含暂存」 |
| **就地编辑** | 从**面板条目**进入（原来从链条胶囊进入）；光标在原文末尾，接着打字即追加 |
| **「归档」** | **取消**。条目一直待在流里，要清掉用 ⋮ → 删除。编辑条操作 = 提醒 / 删除 / 保存 |
| **待办** | 仍不常驻（§12.8）；把手仅**变色**，不出数字 |

**代码层同步清理**：`CHAIN` / `phase` / `setPhase` / `settle` / `expand` / `SETTLE_SECONDS` / `railGeom` / `layoutRail` / `capHtml` / `capLabel` / `capDots` / `ADD_CAP` / `archiveCapsule` / `archiveEditing` / `.rail` / `.cap` / `.capwrap` / `.tick` / `.chain` / `.chainfoot` / `.draghint` 全部删除；画廊从"六态含四态链条"改成"把手 / 记录 / 跟手拉出 / 闪念页 / 剪贴板页 / 就地编辑"。演示页 **1430 → 990 行（−31%）**。

### 这一轮真正的教训

**链条不是被"设计得不够好"打败的，是被"不必要"打败的。** 我前面十几轮都在优化一个不该存在的东西——每次用户提问题，我都给它打补丁（加锚定、加时间轴规则、加手势分工），而不是先问"它到底该不该在"。
**判据应该是：这个元素删掉之后，用户要做的动作是不是变多了？** 对链条来说答案是**没有**（记一条反而少一步），所以它就该被删。

**副产物**：`ui_demo_capsule.html` 里画廊的 `.cap`（caption 图注）与已删的胶囊 `.cap` 撞名，已重命名为 `.caption` —— 免得后人误读成"胶囊"。
## 12.14 第二轮补丁：把"点了没反应"和几个真缺口补掉

砍掉链条之后，用户认可了交互骨架，提出/认可了 8 项。**其中第 1 项是上一版留下的坑**：

| # | 事 | 关键结论 |
| :- | :- | :- |
| 1 | **卡片操作行没接任何 handler** | 进入取词 / 星标 / 复制 / 分享 / ⋮ 全是 `<i title>`，**点了真没反应**。视觉稿必须接上，否则和真机行为不一致，使用者会误判成 bug |
| 2 | **搜索** | 上限 200 条只能滚；标签只能筛"有标签的"，**正文关键词搜不到**。搜索时**忽略标签筛选与范围**，就近搜整个页签 |
| 3 | **撤销** | 存错一条原本要 4 步才能删。通知条上加「撤销」，存下 / 删除 / 完成三处统一 |
| 4 | **空状态** | 一条都没有时 `items.some(visible)` 返回 false → 列表渲染成空字符串，**面板一片空白**，只剩标题和 ＋。三种空（全空 / 筛不出 / 搜不到）各有文案 + 一个出口 |
| 5 | **存下预览** | 只脉冲 + 「已存下」**不告诉用户存了什么**。把手旁浮出内容前十几字，1.2s 消失。**这是"事件"不是"状态"**，与被砍掉的常驻胶囊不是一回事 |
| 6 | **两个把手合并** | **关键认识：现有剪贴板面板本来就是两页签（暂存夹 / 剪贴板）**，所以合并只是把「暂存夹」改名成「闪念」并按「含暂存」扩展 —— **1:1 换页签，零新增表面**，不是"加一个页签" |
| 7 | **待办「完成」** | 划掉 + 变暗，**留在原位**（不移除：时间流是"我记过什么"的历史，且列表跳动会让用户找不到刚点的那条）；**完成即自动取消提醒**（否则到点还响）；`done` **必须独立存放**（写进 `index.json` 的老版本会忽略它并整体重写，状态丢失） |
| 8 | **触摸目标补到 48dp** | 原 `.pipwrap` 32×44、`.acts i` 28×28 都低于 Android 最小值 |

### 由 #7 顺带修掉的一个悬空状态

§12.8 里我曾保留「有待办未完成 → 把手变色」，但**当时没有"完成"概念，"未处理"根本无从计算**，只能靠演示按钮手动置位 —— 是个悬空状态。现在它有了明确数据来源：

```
未完成待办数 = 带「待办」标签 且 done=false 的条数
```

把手变色因此变成数据驱动的真实反馈，不再需要手动开关。

### 由 #8 引出的一个真实取舍：5 个动作装不下 5 个 48dp

卡片可用内宽 ≈ 226dp，**5 个动作 × 48dp = 240dp > 226dp，装不下**。三条出路：

1. 降级到 44dp（5 × 44 = 220，勉强）—— 违反规范
2. 让命中区互相重叠 —— 不可接受
3. **行内只留高频，低频收进 ⋮ 菜单** ← 采用

最终行内 **3 个 48dp 目标**（完成 / 复制 / ⋮ = 144dp，宽裕），⋮ 菜单里放 星标 · 进入取词 · 钉在屏幕 · 分享 · 保存图片 · 删除。
**顺带修掉了 #1**：原来 5 个图标"有样子没功能"，现在能装下的这 3 个都是真功能，其余在菜单里。

### 布局自检新增两条

- **`pointer-events` 自检**：凡设了 `none` 的选择器，必须有 `.on`/`.open` 规则改回 `auto`，除非在白名单（`.gzone` 热区可视化、`.toast`/`.peek` 纯信息层、玻璃伪元素、`.fab.off` 停用态）。**`.scrim` 正是栽在这里**：`.scrim.on` 只改了 `opacity`，忘了把指针放回来，导致"点面板外没反应"而监听器明明在。
- **层级断言**：直接对 CSS 取 `z-index` 断言大小关系（编辑条 > 面板、通知条 ≥ 编辑条、⋮ 菜单 > 编辑条），因为**这类遮挡问题读代码看不出来**，只能靠断言。