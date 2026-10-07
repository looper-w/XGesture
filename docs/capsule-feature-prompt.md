# 闪念胶囊 / 快速记录 — 实现任务书 v1

> 适用仓库：`D:\AndroidDev\Projects\cebian`（XGesture / X手势，`com.slideindex.app`，1.36.0 / versionCode 71）
> 本文是**可直接投喂的实现任务书**：需求 + 已收敛的决策 + 复用清单 + 数据/UI 规格 + 分期 + 验收。
> 配套的完整分析与推演见 `docs/capsule-tag-bar-analysis.md`（§11 增量方案 / **§12 大胆重构：胶囊即实体**）。
> **若采行「胶囊即实体」的大胆重构方向，以 §12 + `ui_demo_capsule.html` 为准**；本文是它前面那个低成本增量阶段。
> 本文所有 `相对路径:行号` 均已在本仓库核实过。

---

## 0. 使用说明（投喂前的角色与硬约束）

你是本仓库的 Android 开发者。本仓库是一个已有 233k 行 Kotlin、6 个内容入口、4 套 locale（en/zh/ja/ar）的成熟工程。

**你的任务是「按已定方案施工」，不是「重新设计」。** 第 3 节的决策已经收敛完毕——**不要重新讨论、不要提供替代方案、不要顺手优化相邻代码**。

三条铁律：

1. **先读完第 2、3、4 节再动键盘。** 第 4 节列出的可复用组件，**先用完才允许新建**；任何新建都要在第 14 节的清单里给出理由。
2. **外科手术式修改。** 只改必须改的地方，与现有代码风格一致，即使你有更好的写法。
3. **编译自检。** 每阶段结束运行 `.\gradlew.bat compileDebugKotlin`（必须用仓库自带 wrapper，禁止直接调用 `gradle`），并跑相关单测。

**本任务的第一版（P0）只做：快速记录文字 + 标签 + 持久化 + 收纳夹内标签筛选。**
Liquid Glass 标签条（P1）与语音（P2）在 P0 稳定后接入——第 11 节给出了分界线。

---

## 1. 功能目标与核心体验

参考 Smartisan OS「闪念胶囊」与 Idea Note 的**交互理念**，但**不复制其 UI**。

> 用户在使用任何 App 时，都能通过本 App 已有的全局入口，快速记录一个突然想到的内容，而无需离开当前 App。

重点是**低操作成本**与**快速记录**，不是做一个完整的传统笔记 App。

**可判定的体验标准（这四条就是 P0 的验收核心）**

| # | 标准 |
| :-: | :- |
| 1 | 从触发到可输入 ≤ 300ms |
| 2 | 输入到落库 ≤ 2 步，**全程不跳出当前 App**（用 `dumpsys activity activities` 核对前台包名未变） |
| 3 | 保存后 UI 自动收起，用户回到原来的 App，**不需要任何额外操作** |
| 4 | 空闲状态下：不注册传感器、不定时器、不截图、不 OCR、不跑 GPU 模糊 |

---

## 2. 硬约束（先读，违反即返工）

### 2.1 不新增表面——表面归属已定

- **入口**：复用现有全局入口（手势动作 / 悬浮球动作 / 侧边栏 / Deeplink），**不新增入口**。
- **宿主**：复用 `FloatBallStashPanel` 的侧栏宿主与 `SearchPanelOverlayWindow` 的**输入法机制**。
  - 输入法三件套必须照抄，不要自己发明：抢焦点 → 请求显示 → 失败补发（`SearchPanelOverlayWindow.kt:290-341`），IME 高度用 `OverlayImeInsets.kt:23` 的 `rememberOverlayImeBottomHeight()` 读（**Compose 的 `WindowInsets.ime` 在 `TYPE_APPLICATION_OVERLAY` 窗上常为 0，直接读会拿到 0**）。
  - **禁止**新建一套独立的悬浮窗系统、禁止新增第 4 个 overlay 宿主、禁止新增内容入口。
- 窗口类型沿用 `OverlayWindowTypes` 的既有决策（内容面板固定 `TYPE_APPLICATION_OVERLAY`，`overlay/OverlayWindowTypes.kt:35-76`），不要自行指定。

### 2.2 不造第二份数据——数据归属已定

- **标签只有一份**，挂在 `StashEntry` 上，住在 `StashRepository` 里。
- **禁止**新建 `CapsuleRepository` / 第二套数据库 / 第二份标签表。
- **禁止**新建 Gradle 模块。
- 所有写入**必须**走既有跨进程写口 `StashRepository.withCrossProcessWrite`（`StashRepository.kt:54-61`，内含 `CrossProcessStore.withFileLock` + `notifyChanged`）。
- 本仓库**没有** Room / SQLite / DataStore 存业务数据；收纳夹的持久化就是 `filesDir/stash/index.json`（kotlinx.serialization）。**不要为了这个功能引入数据库。**

### 2.3 玻璃白名单 + 三段降级

- **允许**：`LocalFrostedGlassBackdrop`（`overlay/LocalFrostedGlassBackdrop.kt:17`，反射 `ViewRootImpl.createBackgroundBlurDrawable`，跨应用真模糊）或 `FLAG_BLUR_BEHIND` + `params.setBlurBehindRadius`（`OverlayFullScreenPanelHost.kt:118-152`）。
- **禁止**：miuix 的 `lens` / `vibrancy` / `innerShadow` / `textureBlur` / `drawBackdrop` 等 RuntimeShader 系。原因（已核实）：浮层可能整窗走软件 Canvas（`ViewRootImpl.drawSoftware`），RuntimeShader 会在 `drawRect` 直接崩溃，所以本仓库在浮层里**主动关掉了**它（`ui/miuix/MiuixOverlayComposeLocals.kt:12-19`）。
- **必须实现三段降级**，观感在每一段都要成立：
  1. 跨窗模糊可用 → 半透明玻璃 + 模糊；
  2. `WindowManager.isCrossWindowBlurEnabled == false`（部分 ROM / 省电模式）或隐藏 API 反射失败 → **不透明 `surfaceContainer` + 描边高光**（**不允许**塌成一块半透明砖）；
  3. 参考现有分支写法：`SearchPanelOverlayWindow.kt:251-254`、`LiquidGlassNavigationBar.kt:492-494`。
- BIOS 半径滑杆上限必须按密度反推：**`≤ 80px / density`**（窗口层 `setBlurBehindRadius` 被 clamp 到 `1..80`，`OverlayFullScreenPanelHost.kt:145`）。否则该控件在高密度屏上是骗人的。
- **不承诺折射**（Apple Liquid Glass 的透镜扭曲跨应用做不出来，只能靠 MediaProjection 常驻截屏，不可发布）。

### 2.4 权限

- **除语音外不新增任何权限。**
- 语音（P2）必然需要 `RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE` + FGS 类型 `microphone`（已核实：当前 manifest **没有** `RECORD_AUDIO`，FGS 类型只有 `specialUse / mediaProjection / dataSync`）。
- 语音权限**按需申请、默认不申请**；用户拒绝后功能降级（语音按钮隐藏或提示），**不允许崩溃或反复弹窗**。
- P0 / P1 **不申请任何权限**。

### 2.5 性能（对应「空闲零成本」）

| 要求 | 具体做法 |
| :- | :- |
| 空闲不占 CPU | 不注册传感器、不设定时器、不持有 ContentObserver；折叠/面板关闭时不监听任何数据流 |
| 不持续 OCR / 不截图 | 本功能**完全不接入**截图与 OCR 管线（`ScreenCaptureService` / `core:ocr` 一行都不要碰） |
| 不持续 GPU Blur | 模糊只在窗口可见时设置（`updateBackgroundBlur` 在 show/hide 时切换），窗口隐藏即置 0 |
| 打开时才初始化 | 借用项目已有的 warm-up 模式（`FloatBallStashPanel.warmUpBelowChrome`），**内容与识别器懒初始化** |
| 保存后释放 | 清空文本状态、释放 IME 焦点、释放音频/识别器等临时资源；**窗口壳可保留预热**（这是本仓库既有的加速策略，不要为了「释放」把预热也拆掉，否则第二次呼出会变慢） |
| 合成开销 | 玻璃底座由 SurfaceFlinger 承担，比自己画 shader 便宜；但仍必须提供「关闭玻璃」开关 |

### 2.6 兼容性红线（不许破坏）

现有悬浮球 / 悬浮指针 / 侧边栏 / 边缘触发条 / 无障碍服务 / OCR / 截图 / 取词 / 钉图 / 通知滤盒 / 剪贴板面板 / 收纳夹面板 / 搜索面板 / 角轮盘 / 各种启动器 —— **行为与默认值全部不变**。

- **不改变任何现有默认值**（尤其不要动 `MAX_ENTRIES = 200`、不要动裁剪逻辑、不要动 `index.json` 的顶层结构，理由见 §5.4）。
- 新增配置项**一律默认关闭或保持现有行为**（对齐本仓库惯例：`clipboardFloatEnabled = false`、`stashPanelBackgroundBlurEnabled = false`）。

### 2.7 仓库既有规范（必读）

- **UI**：改 `app/src/main/java/com/slideindex/app/ui/` 下任何 Compose 界面前先读 `docs/ui-guidelines.md`。重点：`LazyColumn` 里**禁止** `item { Card { 多行 } }`；**禁止** `rememberSettingsCardItems` / `emitSettingsCardItems` 两阶段模式；开关/条件子项的 state 必须在 lazy item 的 compose 作用域内读取；Lazy item key 保持稳定（`keyPrefix:rowKey`）。
- **非 Lazy 表面**（对话框、浮层内滚动 Column）：用 `settingsCardItems { }` + `.RenderRows()`。
- **文案**：任何新增 user-visible 字符串都要同时补 **4 套 locale**（`values` / `values-zh` / `values-ja` / `values-ar`，各约 3,800 条）。
- **代码风格**：Kotlin + Compose + Hilt；注释用中文，与周边一致。

---

## 3. 已定的设计决策（直接执行，不要重新讨论）

| # | 决策点 | **取值** |
| :-: | :- | :- |
| 1 | 「标签」语义 | 用户自由多标签（不做预置类型 / 不做分类树） |
| 2 | 数据归属 | **扩展 `StashEntry`**，不新建实体；闪念 = 一条带标签的 TEXT 条目 |
| 3 | 标签清单存放 | **独立文件 `filesDir/stash/tags.json`**（理由见 §5.4，这是兼容性红线） |
| 4 | 标签 UI 落点 | 收纳面板 **Stash tab 顶部的 chip 行**，**不做第三个 tab** |
| 5 | 标签条是否常驻 | **默认不常驻**。P1 的玻璃胶囊默认关闭（`stashTagBarEnabled = false`） |
| 6 | 玻璃胶囊形态（P1） | **竖向贴边胶囊**，与现有剪贴板把手**合并为同一个「收纳把手」**（`stashHandleMode` 三选一：剪贴板 / 标签 / 隐藏） |
| 7 | 语音 | P2 接入；P0/P1 只预留接口与数据字段，不引入任何语音 SDK |
| 8 | 玻璃档位 | 真模糊 + 仿真高光描边，**不做折射** |
| 9 | 数据形状 | **只给 `StashEntry` 加字段，绝不改 `index.json` 顶层结构** |
| 10 | 标签颜色 | 存在标签定义上（`StashTag.color`），**仅用于 6dp 色点或 1px 描边着色**，不做整卡填充、不做渐变 |

**关于「复用现有标签/分类系统」的结论（不要再去搜）**：本仓库**没有**任何标签或分类系统（全仓 grep `标签` 仅命中 1 条无关字符串）。所以要**新建标签数据，但复用既有的存储与状态管理架构**（`StashRepository` + JSON 文件 + `StateFlow` + `CrossProcessStore`）。

---

## 4. 可复用组件清单（必须先用完这些，才允许新建）

| 需要什么 | **复用什么** | 位置 |
| :- | :- | :- |
| 业务数据与持久化 | `StashRepository`（已有增删改查 / 搜索 / 缩略图 LRU / 跨进程锁） | `app/.../stash/StashRepository.kt` |
| 数据模型 | `StashEntry` | `app/.../stash/StashEntry.kt:15-27` |
| 跨进程一致性 | `CrossProcessStore.withFileLock` / `notifyChanged` / `registerListener` | `core/common/.../util/CrossProcessStore.kt`、`StashRepository.kt:54-80` |
| 侧栏宿主与生命周期 | `FloatBallStashPanel` + `OverlaySidePanelHost` | `app/.../overlay/FloatBallStashPanel.kt` |
| 面板窗布局参数 | `OverlayPanelLayoutParams.stashClipboardSidePanel` | `app/.../overlay/OverlayPanelLayoutParams.kt:51` |
| **浮层里弹输入法** | `SearchPanelOverlayWindow.requestImeShow` / `focusSearchFieldSoon` / 双路径 `showIme` | `app/.../overlay/searchpanel/SearchPanelOverlayWindow.kt:290-341` |
| **浮层 IME 高度** | `rememberOverlayImeBottomHeight()` | `app/.../overlay/OverlayImeInsets.kt:23` |
| **跨应用真玻璃** | `LocalFrostedGlassBackdrop` / `LocalFrostedGlassDrawable` | `app/.../overlay/LocalFrostedGlassBackdrop.kt:17,73` |
| 列表与筛选状态 | `HistoryPanelViewModel`（已有 Stash / Clipboard 双 tab 合并筛选与 debounce 搜索） | `app/.../overlay/history/HistoryPanelViewModel.kt:38-60` |
| 面板 UI 骨架 | `HistoryPanelScreen` + `HistoryStashTabBody` / `HistoryStashEntryCard` | `app/.../overlay/history/HistoryPanelScreen.kt:382`、`HistoryEntryCards.kt:271` |
| 按压 / 跟手动效 | `InteractiveHighlight` / `DampedDragAnimation` | `app/.../ui/miuix/bottombar/animation/` |
| 圆角面（squircle） | `squircleSurface` / `CardSegment` | `ui/miuix/GroupedCardItems.kt` |
| 图标 | `ThinActionIcons`（项目自有细线图标集） | `app/.../ui/ThinActionIcons.kt` |
| 折叠态位置持久化 | `HistoryFloatHandlePosition.resolveY / clampY` | `feature/settings/.../HistoryFloatHandlePosition.kt:9-17` |
| 常驻窗生命周期范式 | `HistoryFloatService` + `HistoryFloatLifecycle`（按设置 start/stop） | `app/.../service/HistoryFloatService.kt`、`HistoryFloatLifecycle.kt` |
| 主题（深浅色 / 动态取色） | `AppThemeMode` / `DarkBackgroundStyle` / `OverlayAwareModuleTheme` | `feature/settings/.../AppThemeMode.kt`、`app/.../ui/theme/` |
| 提醒（P2） | `RemindAlarmScheduler` + `RemindDurationPickerOverlay` | `app/.../remind/` |

> **图标规范**：需求原文里的 `✦ 工作` `💡 想法` 这类 emoji **不要用**，改用 `ThinActionIcons` 的细线图标（或只用文字 + 色点），以匹配项目现有观感。

---

## 5. 数据规格

### 5.1 `StashEntry` 新增字段（只加字段）

```kotlin
// app/src/main/java/com/slideindex/app/stash/StashEntry.kt
val tags: List<String> = emptyList(),           // 标签名列表；只存名字，不存 id
val source: StashSource = StashSource.CLIPBOARD, // 记录来源
val updatedAtEpochMs: Long = createdAtEpochMs,   // 修改时间
val voiceUri: String? = null,                    // 可选：原始语音文件 URI（P2 才有值）

@Serializable
enum class StashSource { TEXT, VOICE, CLIPBOARD, IMAGE, RICH, IMPORT }
```

- `source` 的默认值取 `CLIPBOARD`，**保证既有数据的语义不变**（现有条目都来自剪贴板 / 取词 / 编辑器的暂存）。
- 快速记录创建的条目：`source = TEXT`（P2 语音为 `VOICE`），`type = TEXT`，`text = 内容`。
- **不新增 `StashEntryType`**：闪念就是一条 TEXT 条目。
- `matchesQuery`（`StashEntry.kt:65-70`）增加标签命中。

### 5.2 标签定义（新文件）

```kotlin
// app/src/main/java/com/slideindex/app/stash/StashTag.kt（新）
@Serializable
data class StashTag(
    val name: String,
    val color: Int? = null,            // 仅用于 6dp 色点 / 1px 描边着色
    val order: Int = 0,                // 用户手动排序
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long = 0L,  // 「最近使用优先」的排序键
)

object StashTagLimits {
    const val MAX_TAGS = 20
    const val MAX_NAME_LENGTH = 12
}
```

- 名称规范化：`trim` → 去换行/多余空格 → 截断到 `MAX_NAME_LENGTH` → 去重（大小写与全半角归一，具体策略保持简单即可）。
- 排序规则：**默认按 `lastUsedAtEpochMs` 倒序**（需求要求「最近使用标签优先」）；提供按 `order` 的手动排序。
- 上限 20 个标签；达到上限时「＋」给出明确提示，不静默失败。

### 5.3 仓库新增方法（全部走 `withCrossProcessWrite`）

```kotlin
val tags: StateFlow<List<StashTag>>                       // 新
suspend fun setEntryTags(id: String, tags: List<String>)   // 改单条条目的标签
suspend fun createTag(name: String): StashTag?             // 新建（含规范化与上限判断）
suspend fun renameTag(oldName: String, newName: String)    // 同时批量改所有条目的 tags
suspend fun deleteTag(name: String)                        // 同时批量从所有条目的 tags 剔除
suspend fun touchTag(name: String)                         // 更新 lastUsedAtEpochMs（落库时调用）
```

- `CrossProcessStore.registerListener`（`StashRepository.kt:67-80`）**必须增加 `tagsFile` 分支**，否则另一个进程改了标签本进程不刷新。

### 5.4 ⚠️ 兼容性红线：绝不能改 `index.json` 的顶层结构

`index.json` 的顶层是 `List<StashEntry>`（`StashRepository.kt:401-406`）。若为了塞标签改成 wrapper 对象（`{entries:[…], tags:[…]}`）：

- 旧版本 `decodeFromString<List<StashEntry>>` 会解析失败 → `getOrDefault(emptyList())` **静默返回空表**；
- 用户下次新增条目时 `writeToDisk(listOf(新条目))` → **整份收纳夹被覆盖为空**。

⇒ **标签清单必须放独立的 `tags.json`，同一把跨进程锁。** 附带收益：条目超过 `MAX_ENTRIES = 200` 被裁剪时（`StashRepository.kt:412-416`），**标签不会跟着消失**——标签常驻、条目可过期。

### 5.5 兼容性与迁移

- **读**：`ignoreUnknownKeys = true`（`StashRepository.kt:40`）+ 新字段默认值 ⇒ 旧数据无 `tags` 正常读。
- **写**：旧版本回写 `index.json` 会丢条目上的 `tags` 关联（`tags.json` 仍在，标签名不丢）。**此行为必须写进 `CHANGELOG.md`。**
- **裁剪**：P0 **不改** `MAX_ENTRIES`、**不改**裁剪逻辑（§2.6）。「闪念条目是否豁免裁剪」列为 P2 待决项。

---

## 6. UI 规格

### 6.1 三个表面（P0 只做 ①；P1 加 ②③）

可视稿：**`docs/mockups/capsule-ui.png`**（浅色 / 深色 / AMOLED 三态，含 ①～⑥ 六个状态）；
交互稿：**`ui_demo_capsule.html`**（可切主题 / 开关玻璃 / 调模糊半径 / 模拟输入法，点胶囊展开、长按记录、回车保存）。
——**动工前先看这两份**，界面按图实现。

```
【② 折叠态 · P1】贴边玻璃胶囊        【③ 快速面板 · P1】就地展开，顶边与胶囊对齐
      ┌──┐                        ┌──────────────────────────────┐
      │●│ ← 点击：就地展开         │ 全部   工作   #想法      ＋   │ ← chip 行
      │工│ ← 长按：直接打字落库     ├──────────────────────────────┤
      │●│ ← 拖动：改纵向位置       │  ● 记得周五前把方案初稿发…    │ ← 最多 5 条
      │作│ ← 上滑：切换标签         │  ● 把液态玻璃的描边改成 1px…  │
      │＋│                        │  ● 买咖啡豆                   │
      └──┘                        ├──────────────────────────────┤
                                  │        查看全部  ›            │
                                  └──────────────────────────────┘
```

| 表面 | 何时出现 | 形状 |
| :- | :- | :- |
| ① 标签 chip 行（**P0 交付**） | 手势 / Deeplink / 桌面快捷方式打开收纳面板 | 既有全高侧栏，**形状不变**，顶部多一行 chip |
| ② 折叠态玻璃胶囊（P1） | 用户开启常驻（**默认关**） | 贴边竖向胶囊，可拖 Y、可上滑切标签 |
| ③ 快速面板（P1） | 点击 ② 就地展开 | 玻璃卡片，**顶边与胶囊对齐**，高度贴合内容，最多 5 条 |

**统一原则**：①②③ 是**同一份数据、同一个 ViewModel**。③ 的 `查看全部 ›` 进 ①，① 的手势/Deeplink 入口一律不变。
**几何连续性**：③ 的顶边与 ② 的顶边对齐（真正的几何连续）。**不要**把 ③ 做成全屏接管，也不要让 ② 展开成全高侧栏——胶囊可以停在任意 Y，而全高侧栏的 chip 行固定在顶部，两者无法对齐。
**边缘互斥规则**：同一侧边缘**最多一个**可见折叠元素；标签胶囊与现有剪贴板把手**合并为同一个**（`stashHandleMode`）。
**视觉层次**：玻璃为「开启态」；**默认关闭**时 ① 是实色面板、③ 是实色卡片（对齐本仓库 `stashPanelBackgroundBlurEnabled = false` 的既有默认）。

#### 6.1b 收纳面板保真清单（**照抄现有代码，禁止重新设计**）

| 项 | 取值 | 出处 |
| :- | :- | :- |
| 面板宽度 | **窗口宽度的 50%**（半屏） | `HistoryPanelUi.kt:65-71`（`containerSize.width / 2f`） |
| 圆角 | **14dp，只圆内侧两角**（`gravityEnd` → `topStart + bottomStart`） | `HistoryPanelScreen.kt:217-221` |
| 阴影 | `shadow(12.dp)` | `HistoryPanelScreen.kt:233` |
| 列表底色 | `scheme.surface`（**灰**）；毛玻璃开启时 alpha 0.65 | `HistoryPanelColors.kt:23-30` |
| chrome 底色 | `scheme.surfaceContainer`（**白**）；毛玻璃开启时 alpha 0.72 | `HistoryPanelColors.kt:13-20` |
| 卡片底 | `surfaceContainer`（白）；**星标卡** = `primaryVariant.copy(alpha = 0.35f)` + `primary 45%` 1dp 描边 | `HistoryPanelColors.kt:33-40`、`HistoryEntryCardParts.kt:256-276` |
| 卡片结构 | `时间（左）｜进入取词 · 星标（右）` → 内容预览 → `HorizontalDivider` → 操作行 | `HistoryEntryCardParts.kt:283-305` |
| 卡片圆角 / 内边距 | 16dp / 12dp | `HistoryEntryCardParts.kt:255,277` |
| chrome 顺序 | 标题「收纳面板」+ 图钉（切换侧边）+ 搜索图标 → 可展开搜索条 → 双 tab（**暂存夹 / 剪贴板**，带 contour） | `HistoryPanelScreen.kt:322-360` |
| 两页内容 | `HorizontalPager`：Stash / Clipboard 各一页，共用同一个 ViewModel | `HistoryPanelScreen.kt:263-303` |
| 面板玻璃 | 仅在 `panelBlurActive` 时挂 `LocalFrostedGlassBackdrop(cornerRadius = 14dp, tint = 0x66F5F5F7 / 0x661C1C1E)` | `HistoryPanelScreen.kt:222-255` |
| 文案 key | `floating_panel_title` = 收纳面板、`stash_panel_tab` = 暂存夹、`clipboard_panel_tab` = 剪贴板、`stash_search_hint` = 搜索暂存夹… | `values-zh/strings.xml` |

**chip 行的唯一插入点**：`标题行 → 搜索条 → tab 行 →` **← 这里** `→ 列表`，即 chrome 的最后一行。
**只在暂存夹 tab 显示**；切到剪贴板 tab 时**不出现**（剪贴板条目没有标签，做成全局开关会误导）。
**半屏宽下 chip 行只放得下约 3 个标签**（约 195dp 可用宽），第 4 个起必须横向滚动 + 右缘渐隐；**因此标签名建议 ≤ 2 字**。

### 6.2 材质（玻璃胶囊，P1；展开态可共用）

| 层 | 做法 |
| :- | :- |
| 模糊底座 | `LocalFrostedGlassBackdrop`（跨应用真模糊），或 `FLAG_BLUR_BEHIND` + `setBlurBehindRadius` |
| 描边高光 | **手写** 1px 内描边 + 顶亮底暗（**不得**引用 miuix RuntimeShader） |
| 内阴影 | 手写（`Modifier.drawWithContent` + 渐变），参数语义参考 `ui/miuix/bottombar/liquid/InnerShadow.kt:37-53` |
| 抗色带 | 噪点 `0.08f`（与 `MainBottomNav.kt:176` 同值） |
| 半透明填充 | `surfaceContainer.copy(alpha = 0.4f)`（对齐 `LiquidGlassNavigationBar.kt:213`） |
| 阴影 | 极轻 `dropShadow`，**不做发光** |

**深色/浅色/AMOLED**：必须三种都验证。注意本仓库有 `DarkBackgroundStyle.AMOLED_BLACK`（纯黑背景）——**纯黑上模糊几乎没有可模糊的内容**，此时玻璃必须靠「1px 高光描边 + 极轻填充」读出来，不能只靠模糊。

### 6.3 尺寸与动效

| 项 | 取值 |
| :- | :- |
| 折叠态宽度 | 28dp（窄）/ 36dp（宽），对齐 `HistoryFloatHandleWidth` 档位 |
| 折叠态高度 | 自适应，**最多 3 个 chip + 一个 ＋**，永远单行 |
| chip 行高 | 32dp，水平可滚 |
| 玻璃胶囊参考常量 | 高 40 / 左右内边距 14 / 描边 1.0（见 `docs/floatwidget-search-panel-reverse.md:444-446`） |
| 模糊半径默认 | **4dp**（对齐 `BottomNavBlurDefaults.LIQUID_GLASS_DEFAULT_RADIUS_DP = 4f`），上限按密度反推（§2.3） |
| 出现 | 缩放 `0.96 → 1` + 淡入，**180ms**，EaseOut |
| 标签横向展开 | 宽度动画 **160ms** |
| 保存 | 收缩 + 淡出 **120ms**，**然后才**清空状态 |
| 收起 | 220ms / 160ms（对齐项目习惯 `ADJUST_INDICATOR_ENTER_MS = 220L` / `EXIT_MS = 160L`） |
| 禁止 | 夸张弹簧；只允许 transform/alpha，避免每帧布局；不要在 draw 里读传感器（项目教训：`LiquidGlassNavigationBar.kt:349-351` 只在 draw 阶段读并做 3° 量化） |

---

## 7. 标签 UI 与交互（Liquid Glass 标签条）

### 7.1 视觉要求

- 标签是**独立的玻璃胶囊**，不是 Material 实心矩形 Chip。
- 半透明玻璃底 + 背景模糊 + **轻微高光边缘** + 大圆角（胶囊形）+ **轻微**阴影。
- 标签之间有**自然间距**（建议 8dp），不要挤成一排实心块。
- **根据状态产生轻微视觉层次**（未选中 / 选中 / 最近使用），层次靠**描边亮度与填充透明度**表达，不靠尺寸跳动。
- 支持深色 / 浅色 / AMOLED。
- **不要过度使用渐变；不要做成廉价的「彩色玻璃」**：颜色只作为 6dp 色点或 1px 描边着色，**禁止整卡着色、禁止彩虹渐变**。

### 7.2 交互要求

| 要求 | 实现 |
| :- | :- |
| 最近使用优先 | chip 行默认按 `StashTag.lastUsedAtEpochMs` 倒序 |
| 横向滑动 | chip 行水平可滚（`Row` + `horizontalScroll`），**不要**用 `LazyRow` 造成不必要开销 |
| 「＋」快速创建 | 就地内联输入（宽度动画展开 160ms），**禁止弹出大型阻断 Dialog** |
| 选中状态 | 明显但克制：描边加亮 + 填充不透明度提升一档（参考 `InteractiveHighlight`） |
| 多选 | 允许一条闪念挂多个标签；再点一次取消 |
| 标签管理（重命名 / 排序 / 删除） | 长按 chip 进入；这是**唯一**允许使用对话框的场景，且按 `docs/ui-guidelines.md` 的非 Lazy 表面规范写（`settingsCardItems { }.RenderRows()`） |

### 7.3 数据侧

标签选择**必须**复用现有数据与组件（§4）：`StashRepository.tags` + `HistoryPanelViewModel` 的筛选状态，**不要在浮层里另存一份标签副本**。

---

## 8. 快速记录 UI 规格

结构建议（**按本项目的 Miuix 风格融合，不要机械照搬**）：

```
┌──────────────────────────────────┐
│  #工作   #想法   #待办    ＋      │  ← 顶部：Liquid Glass 标签条（§7）
│                                  │
│  输入刚刚想到的内容……             │  ← 中间：快速输入区（单行起，最多 4 行）
│                                  │
│                      🎙    ✓      │  ← 底部：语音（P2）/ 保存
└──────────────────────────────────┘
```

- 输入区：`BasicTextField` / `TextField`，**默认单行**，内容变长后最多展开到 4 行，超出内部滚动。
- 输入区获得焦点即弹输入法（照抄 §2.1 的三件套，**不要自己写**）。
- 保存：`✓` 按钮 + **IME 的「完成 / 发送」动作**两条路径都要能用（键盘上的 Enter 落库是最短路径）。
- 保存后：内容校验（去空白后为空则不落库、不报错、直接收起）、`touchTag` 更新标签使用时间、UI 动画收起（§6.3）。
- **不强制进入独立笔记页面**；本条落库后不跳转、不弹 Toast 打断（用项目既有轻量提示样式）。
- 若有「未选标签」的情况：允许落库为无标签条目（展开态里显示在「未标注」筛选下），**不要强制选标签**。

---

## 9. 语音（P2，只留接口，P0/P1 不实现）

**本仓库现状（已核实，不要重复调研）**：`app/.../util/VoiceActionHelper.kt` 只是把 `RecognizerIntent`（`ACTION_VOICE_SEARCH_HANDS_FREE` / `ACTION_WEB_SEARCH` / `ACTION_RECOGNIZE_SPEECH`）**交给系统**，**拿不回识别结果**；全仓没有 `SpeechRecognizer` 实例，没有 Vosk / Sherpa / Whisper 依赖。⇒ 「接入现有语音能力」实际是从零实现。

**要求**

1. **不引入任何庞大的第三方语音 SDK**（不得因此让包体显著增长——本仓库有 Lite 轻量包的产品定位）。
2. 先设计好**接口与数据模型**（`StashSource.VOICE` + `voiceUri` + 一个可替换的识别器接口），使后续能平滑接入 `SpeechRecognizer` 或独立语音模块。
3. 用 Android 原生 `SpeechRecognizer` 时，必须正确处理：
   - **权限**：`RECORD_AUDIO` 运行时申请；拒绝 → 隐藏语音入口并给一次说明，不反复弹窗。
   - **开始识别**：主线程创建/调用；`SpeechRecognizer` 必须在主线程使用。
   - **结束识别**：拿到 `onResults` 后 `stopListening()` + `destroy()`，避免泄漏。
   - **识别失败**：`onError` 按 `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT` / `ERROR_RECOGNIZER_BUSY` / `ERROR_INSUFFICIENT_PERMISSIONS` 分别处理，给出**可重试**的提示。
   - **用户取消**：`cancel()` 路径要回到「未录音」状态，不落库。
   - **生命周期**：面板收起 / 窗口销毁 / 进程后台时**必须**停止识别并 `destroy()`；避免 Activity/Service 泄漏。
   - **多次连续录音**：每次录音前重建识别会话（不要复用已 `destroy()` 的实例）；用状态机（`Idle / Requesting / Listening / Processing / Error`）防止重入。
4. **存音频**：若保留原始语音，文件放 `filesDir/stash/` 下的独立子目录，与 `StashRepository` 的条目生命周期**一致**（条目删则音频删），并遵守 §5.5 的裁剪语义。
5. 语音能力做成**可关闭**的配置项，默认关闭。

---

## 10. 与现有 App 的兼容（逐项自查）

| 项 | 要求 |
| :- | :- |
| 悬浮球 / 悬浮指针 | 不改行为与默认值；折叠态与悬浮球**不抢触摸区域** |
| 侧边栏 / 边缘触发条 | 不改；折叠态遵循边缘互斥规则（§6.1） |
| Accessibility Service | 不新增无障碍能力需求；不依赖无障碍读屏 |
| OCR / 截图 / 取词 | **完全不接入**，一行都不改 |
| 钉图 | 不改（P2 的「钉图数量上限」是独立项，不属本任务） |
| 既有 Overlay | 只**复用**宿主；不新增窗口类型、不改 `OverlayWindowTypes` |
| 权限 | 只按 §2.4 |
| 默认行为 | **一律不变** |
| 进程 | **不新增进程**；写入必须走 `CrossProcessStore` 既有写口 |
| 空间占用 | P0/P1 不新增依赖 ⇒ Lite/Full 双包体积不变 |

---

## 11. 分期与施工顺序

### P0｜标签落地（本任务的第一步，0.5～1 人日）

**做什么**：`StashEntry.tags` + `StashTag` + `tags.json` 读写 + 收纳面板 chip 筛选 + 条目打标/编辑 + 搜索命中标签。
**不做什么**：折叠态、玻璃胶囊、采集浮层、手势动作、语音。

| 文件 | 改动 | 估行数 |
| :- | :- | -: |
| `app/.../stash/StashEntry.kt:15-27` | 新增 `tags` / `source` / `updatedAtEpochMs` / `voiceUri`；`matchesQuery(:65-70)` 加标签命中 | +16 |
| `app/.../stash/StashSource.kt`（新，或并入 `StashEntry.kt`） | `enum class StashSource` | +10 |
| `app/.../stash/StashTag.kt`（新） | `StashTag` / `StashTagLimits` / 名称规范化 | +50 |
| `app/.../stash/StashRepository.kt` | `tagsFile`、`_tags` StateFlow、`setEntryTags` / `createTag` / `renameTag` / `deleteTag` / `touchTag`、`registerListener` 增 `tagsFile` 分支（`:67-80`） | +120 |
| `app/.../overlay/history/HistoryPanelViewModel.kt` | `selectedTag: StateFlow<String?>` 并入 `filteredStashEntries` 的 `combine`（`:53-60`） | +30 |
| `app/.../overlay/history/HistoryPanelScreen.kt`＋`HistoryStashTabBody(:382-)` | 顶部 chip 行（全部 / 未标注 / #标签 / ＋）+ 空态文案 | +80 |
| `app/.../overlay/history/HistoryEntryCards.kt` | 条目标签小字 + 长按「编辑标签」（`HistoryStashEntryCard:271`） | +45 |
| 标签管理浮层（新） | 重命名 / 排序 / 删除；按 `docs/ui-guidelines.md:62-69` 用 `settingsCardItems{}.RenderRows()` | +120 |
| settings 5 文件（`AppSettingsSlices` / `OverlaySettings` / `SettingsSnapshotReader` / `OverlaySettingsMutator` / `SettingsRepository`） | `stashTagsEnabled`（默认 true，纯 UI 显隐开关） | +30 |
| `res/values{,-zh,-ja,-ar}/strings.xml` | ~8 条文案 ×4 | 小型 |
| 测试 | `StashTagTest`、`StashRepositoryTagsTest`：旧 JSON 无 `tags` 可读 / 旧版本回写不崩 / 裁剪后标签仍在 / 跨进程序列化往返 | +70 |

**P0 验证**：单测全绿 + `compileDebugKotlin`；真机：打标 → 杀进程重开 → 标签仍在；搜索命中标签；标签筛选与搜索条件叠加；200 条裁剪后标签仍在。

### P1｜折叠态 + 采集浮层（+1.5～2.5 人日，**P0 发版观察通过后再做**）

- 收纳把手多形态（`stashHandleMode`：剪贴板 / 标签 / 隐藏），与现有 `HistoryFloatService` 合并；
- 折叠态液态玻璃胶囊 UI（§6.2、§6.3）+ 三段降级；
- **快速面板 ③**：点击胶囊就地展开的**高度自适应**玻璃卡片（顶边与胶囊对齐，最多 5 条 + `查看全部 ›`）；`OverlayPanelLayoutParams` 新增自适应高度变体；
- 长按直接落库的采集浮层（复用 §4 的 IME 三件套）；
- 边缘互斥：折叠态出现时自动让旧剪贴板把手不显示；
- 设置项：`stashTagBarEnabled`（**默认 false**）、玻璃开关（默认 false）、模糊半径（默认 4dp，上限按密度反推）。
- **准入条件**：P0 发版后，标签确实被用户使用。这是流程要求，不是形式。

### P2｜闭环与语音

- 条目设提醒（复用 `remind`）、未读点（只留一个小圆点，**不显示数字**）、`xgesture://open/stash?tag=` Deeplink；
- 语音接入（§9），可关闭、默认关闭。

---

## 12. 验收标准（可验证）

| # | 标准 | 验证方式 |
| :-: | :- | :- |
| 1 | 触发到可输入 ≤ 300ms | 计时 / systrace |
| 2 | 保存后前台 App 未改变 | `adb shell dumpsys activity activities` 核对前台包名 |
| 3 | 输入 → 落库 ≤ 2 步（且键盘 Enter 可落库） | 手测 |
| 4 | 默认配置下屏上常驻**可见**元素 **0 个** | 手测 |
| 5 | 折叠态永远单行、≤ 4 个元素（3 chip + ＋）、不显示时间/计数 | 手测（P1） |
| 6 | 三段降级观感均成立 | 三类设备截图：支持模糊 / 不支持模糊（省电模式或关跨窗模糊）/ AMOLED 纯黑 |
| 7 | 空闲零成本 | 打开前后 CPU 占用对比；确认无传感器注册、无定时器、无截图/OCR 调用 |
| 8 | 开玻璃 vs 关玻璃滚动帧率差可测 | `core:monitoring` / `DebugPerformanceOverlay` |
| 9 | 标签在收纳面板与折叠态之间不出现双入口、不出现状态不同步 | 手测（P1） |
| 10 | 4 套 locale 文案齐全；Lite/Full 双包体积不增 | 检查 `values*`；对比 APK 体积 |
| 11 | 旧版本回写不丢条目（只丢标签关联，且已在 CHANGELOG 说明） | 装旧包回写验证（P0 前必做一次） |
| 12 | 现有功能零回归 | 悬浮球 / 侧边栏 / 取词 / 截图 / OCR / 钉图 / 剪贴板面板 冒烟 |

---

## 13. 不做清单（明确排除，不要顺手做）

- ❌ 本地语音识别引擎（Vosk / Sherpa / Whisper 等）与任何语音 SDK
- ❌ 常驻展开的标签条
- ❌ 独立的新面板 / 新 Gradle 模块 / 新浮层宿主 / 新内容入口
- ❌ 跨应用折射（透镜扭曲）效果
- ❌ 改动 `index.json` 顶层结构
- ❌ 改动 `MAX_ENTRIES` 与裁剪逻辑
- ❌ 预置标签类型 / 分类树 / 笔记本 / 时间轴页
- ❌ 为这个功能引入 Room / SQLite / 第二套 DataStore 业务模型
- ❌ 顺手重构相邻代码、顺手统一命名、顺手清理死代码（发现死代码可以提出，但不要直接删）
- ❌ 新增权限（语音除外，且按需申请）

---

## 14. 编码前必须先交付的 5 项（本任务书已预填前 3 项，你只需确认并补第 4、5 项）

| # | 项 | 状态 |
| :-: | :- | :- |
| 1 | 分析现有架构（UI / Compose / 数据库 / Repository / ViewModel / Overlay / 状态管理） | ✅ 见 §2、§4 |
| 2 | 找出可复用组件 | ✅ 见 §4 |
| 3 | 给出实现方案 | ✅ 见 §3、§5、§6、§7、§8 |
| 4 | **列出预计修改 / 新增的文件清单** | ⬜ 你在动工前把它落到 issue/PR 描述里，逐条标注「新建 / 修改」并给出理由（凡属 §4 已有能力的，必须说明为何不能复用） |
| 5 | **给出 P0 的最小 diff 计划** | ⬜ 按 §11 的表格核对，任何超出表格范围的改动都要先说明 |

**确认第 4、5 项后即可开始 P0 编码。** 每阶段结束：`.\gradlew.bat compileDebugKotlin` + 相关单测，并在 PR 描述里贴出 §12 对应条目的验证结果。
