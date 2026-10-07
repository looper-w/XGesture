# One Hand Control「显示快捷工具」音量条逆向报告

> 目标：搞清 `One Hand Control`（下称 OHC）为什么在 ColorOS 上能连续调音量，而 XGesture 的
> 「滑动调节音量」(ADJUST_VOLUME) 与「快捷工具面板」音量条不能，并给出可像素级移植的实现。
>
> 结论已经用 **jadx 反编译 + 自写 dex 指令级 dump 双重校验**，关键分支不存在猜测。

---

## 0. 结论速览（TL;DR）

| 项 | OHC | XGesture（现状） |
|---|---|---|
| 窗口类型 | `TYPE_ACCESSIBILITY_OVERLAY` (2032) | 同为无障碍悬浮窗 |
| 权限 | `MODIFY_AUDIO_SETTINGS` + 通知使用权（仅媒体会话路径用） | `MODIFY_AUDIO_SETTINGS` |
| Shizuku / root / shell / keyevent | **完全没有** | 有 Shizuku，但音量路径没用 |
| 写音量的方式 | `setStreamVolume`(绝对) → **立即回读** → 不一致时 `adjustStreamVolume`(相对 ±1) **步进补偿**（每次最多 24 步） | **只有** `setStreamVolume(stream, level, 0)` |
| 应用节流 | 两次应用之间 **≥24 ms**；用户拖动（`fromUser=true`）时立即应用 | 无 |

**所以差异不在窗口/权限，而在"绝对设置失败后有没有用相对步进兜底"。**
ColorOS 会静默忽略来自后台/悬浮窗应用的绝对值音量设置（无异常、无效果），但放行相对步进
（`ADJUST_RAISE` / `ADJUST_LOWER`）。XGesture 的 `VOLUME_UP`/`VOLUME_DOWN` 动作正好走的是
`adjustStreamVolume`，这就是用户说"单步动作正常、滑动和面板音量条不行"的原因。

---

## 1. 样本与工具链（可复现）

```
APK   : D:\Downloads\Quick Share\One Hand Control MOD APK V1.2.6 (Pro Unlocked).apk
size  : 7,148,941 bytes
sha256: F6F2466DE0CE69AA7134AD5A40BA46E7E7EF6373225F5E434277C1DB03C9B823
```

反编译（**注意坑**：`jadx-1.5.0-all.jar` 用 `-jar` 启动会进 GUI，必须显式指定 CLI 主类）：

```powershell
java -Xmx6g -cp .tools\lib\jadx-1.5.0-all.jar jadx.cli.JadxCLI `
     -j 8 --show-bad-code -d .ohc_decompiled "<apk>"
```

指令级校验（本仓新增工具，见 `tools/dex_method_dump.py`）：

```powershell
python tools\dex_method_dump.py .ohc_apk\classes.dex 'Lcom/onehandcontrol/app/service/EdgeGestureService;' 'h,j0'
python tools\dex_method_dump.py .ohc_apk\classes.dex 'LF2/h1;' 'run'
```

产物：`.ohc_decompiled/`（jadx 全量源码+资源）、`.ohc_apk/`（解包）、`.tmp/ohc_strings.txt`（dex 字符串池）。

### 混淆情况

`classes.dex` 共 4148 个类，应用类被 R8 重命名成 `F2.x1` 这类短名，但**字符串常量保留**
（`AndroidManifest` 里的组件名、`getVolumeControl`、`quick_tools_volume_set_no_effect` 等），
所以定位路径是：字符串锚点 → 引用它的类 → 方法。

jadx 别名 ↔ 真实类名对照：

| jadx 文件中显示 | 真实 descriptor | 角色 |
|---|---|---|
| `F2.C0279x1` | `LF2/x1;` | **QuickToolsOverlayController**：快捷工具面板控制器（音量/亮度条、媒体控制） |
| `F2.C0264s1` | `LF2/s1;` | 面板里的自定义滑条行 `View`（音量/亮度共用） |
| `F2.C0267t1` | `LF2/t1;` | 音量目标数据类 `(value, min, max, MediaController?)` |
| `F2.N` | `LF2/N;` | `EdgeSliderSession`（边缘滑动会话：kind/min/max/lastApplied/usesMediaControllerVolume…） |
| `F2.RunnableC0232h1` | `LF2/h1;` | 面板侧音量应用 `Runnable`（`case 9` 就是音量） |
| `E0.C0188p` | `LE0/p;` | `onVolumeProgressChanged(value, fromUser)` 的 lambda 宿主（方法被内联进这里） |
| `com.onehandcontrol.app.service.EdgeGestureService` | 同名 | 无障碍服务：边缘滑动调节（音量/亮度）、面板宿主 |
| `com.onehandcontrol.app.service.MediaSessionNotificationListener` | 同名 | **空的** `NotificationListenerService`，只为拿到 `getActiveSessions` 资格 |

UI 术语对应：用户说的「显示快捷工具」= `action_show_quick_tools`；「音量条」= `quick_tools_volume`。

---

## 2. OHC 的三处写音量代码（全部核对过）

OHC 里写音量只有 3 个入口，**没有任何** `Runtime.exec` / `su` / `input keyevent` /
`dispatchMediaKeyEvent(VOLUME_*)`：

1. `EdgeGestureService.j0()` —— 边缘滑动（长按边缘上下滑）的单步应用；
2. `F2.h1.run()` `case 9` —— **快捷工具面板音量条**的应用（带 24 步循环）；
3. `Y2.C1502i.D()/E()` —— 「媒体/铃声/通知 音量 ±」动作，`adjustStreamVolume(stream, dir, FLAG_SHOW_UI=1)`。

### 2.1 基石：`EdgeGestureService.j0(AudioManager, EdgeSliderSession, wantIndex)`

反编译（jadx）：

```java
public final int j0(AudioManager audioManager, N n2, int i) {
    float f4 = n2.f1972f;               // session.min
    float f5 = n2.f1973g;               // session.max
    int j4 = p.j(i, (int) f4, (int) f5);        // target = clamp(want, min, max)
    boolean z3;
    try {
        audioManager.setStreamVolume(3, j4, 0); // 3 = STREAM_MUSIC, flags = 0
        z3 = true;
    } catch (Exception e4) {
        ... report("volume_slider_set_exception") ...
        z3 = false;
    }
    int streamVolume = audioManager.getStreamVolume(3);          // ★ 立即回读
    int j5 = p.j(streamVolume, (int) f4, (int) f5);              // actual = clamp(readback)
    if (j5 == j4) return j5;                                     // 生效 → 结束
    if (!z3 || n2.f1980o) {                                      // 未生效 or 已上报过
        i5 = 1;
    } else {
        n2.f1980o = true;                                        // 每个 session 只报一次
        ... report("volume_slider_set_no_effect", target=j4, actual=j5) ...
    }
    try {
        audioManager.adjustStreamVolume(3, j4 > j5 ? i5 /*ADJUST_RAISE=1*/ : -1 /*ADJUST_LOWER*/,
                                        0);                      // ★ 相对步进兜底（每次 1 步）
    } catch (Exception e5) {
        ... report("volume_slider_adjust_exception") ...
    }
    return p.j(audioManager.getStreamVolume(3), (int) f4, (int) f5);
}
```

指令级校验（`tools/dex_method_dump.py`，节选）——确认顺序与常量，`const/16 0x1` / `-1`
分别对应 RAISE/LOWER，`setStreamVolume` 与 `adjustStreamVolume` 的 flags 寄存器都是 0：

```
 38: invoke-virtual  ... AudioManager;->setStreamVolume:V
 97: invoke-virtual  ... AudioManager;->getStreamVolume:I      ← 回读
109: if-ne  → 112        (actual != want)
111: return                    (actual == want → 结束)
112: if-eqz v0 → 162 / 116: if-nez → 162   (ok 且未上报过 → 上报 no_effect)
156: const-string 'volume_slider_set_no_effect'
178: invoke-virtual  ... AudioManager;->adjustStreamVolume:V   ← 只走 1 步
225: const-string 'volume_slider_adjust_exception'
231: getStreamVolume → 241: return
```

### 2.2 快捷工具面板音量条：`F2.x1.onVolumeProgressChanged(value, fromUser)`

方法被 R8 内联进 `LE0/p;` 的 lambda（`case default`），逻辑：

```java
// LE0/p.java case default
float v = clamp(value, 0f, 1f);
MediaController mc = controller.G();          // 见 2.4：有通知使用权 + 会话 volumeControl==ABSOLUTE 才非空
PlaybackInfo pi = mc != null ? mc.getPlaybackInfo() : null;
if (pi != null && pi.getVolumeControl() == 2 /*VOLUME_CONTROL_ABSOLUTE*/ && pi.getMaxVolume() > 0) {
    int max = pi.getMaxVolume();
    target = new VolumeTarget(round(v * max), 0, max, mc);
} else if (!audioManager.isVolumeFixed()) {
    int min = SDK >= 28 ? audioManager.getStreamMinVolume(3) : 0;
    int max = audioManager.getStreamMaxVolume(3);
    if (max < min + 1) max = min + 1;
    target = new VolumeTarget(round(v * (max - min)) + min, min, max, null);
} else {
    if (!reportedFixed) { reportedFixed = true; report("quick_tools_volume_fixed"); toast(); }
    target = null;
}
if (target != null) {
    pendingTarget = target;                    // 加锁交换
    long delay = fromUser ? 0 : max(0, 24 - (now - lastApplyAt));   // ★ 24 ms 节流
    handler.removeCallbacks(applyRunnable);
    if (delay == 0) handler.post(applyRunnable); else handler.postDelayed(applyRunnable, delay);
}
```

### 2.3 面板音量条的实际应用：`F2.h1.run()` `case 9`（**核心**）

```java
VolumeTarget t = pendingTarget; pendingTarget = null;   // 加锁取出
lastApplyAt = SystemClock.uptimeMillis();               // 加锁写入
MediaController mc = t.controller;
if (mc != null) {
    PlaybackInfo pi = mc.getPlaybackInfo();
    if (pi.getVolumeControl() == 2 && pi.getMaxVolume() > 0) {
        try { mc.setVolumeTo(clamp(t.value, 0, pi.getMaxVolume()), 0); return; }  // ★ 成功即 return，不校验
        catch (Exception e) { report("quick_tools_controller_volume_set_exception"); }
    }
}
int want = clamp(t.value, t.min, t.max);
boolean ok = true;
try { audioManager.setStreamVolume(3, want, 0); }
catch (Exception e) { report("quick_tools_volume_set_exception"); ok = false; }

int actual = clamp(audioManager.getStreamVolume(3), t.min, t.max);   // ★ 立即回读
if (actual == want) return;                                          // 生效 → 结束
if (ok && !reportedNoEffect) { reportedNoEffect = true;
    report("quick_tools_volume_set_no_effect", target=want, actual=actual); }

int steps = min(abs(want - actual), 24);                             // ★ 每次最多 24 步
for (int i = 0; i < steps && actual != want; i++) {
    try {
        audioManager.adjustStreamVolume(3, want > actual ? ADJUST_RAISE : ADJUST_LOWER, 0);
        actual = clamp(audioManager.getStreamVolume(3), t.min, t.max);
    } catch (Exception e) { report("quick_tools_volume_adjust_exception"); return; }
}
```

指令级校验（`LF2/h1;` `run`，节选）——确认 `const/16 0x18`(=24) 的步数上限与循环：

```
269: invoke-virtual ... AudioManager;->setStreamVolume:V
337: invoke-virtual ... AudioManager;->getStreamVolume:I         ← 回读
345: if-ne → 349 ; 347: goto/16 → 511                            (相等 → 结束)
349: if-eqz → 405 / 353: if-nez → 405                            (ok 且未上报 → no_effect)
400: const-string 'quick_tools_volume_set_no_effect'
407: invoke-static Math;->abs:I
411: const/16 0x18 (=24)                                          ← 步数上限
419: if-ge v3, v2 → 511                                          ← for i < steps
421: if-ne (actual == want) → 511
424-428: 取方向 (+1 / -1)
438: invoke-virtual ... AudioManager;->adjustStreamVolume:V
445: invoke-virtual ... AudioManager;->getStreamVolume:I         ← 每步回读
455: add-int/lit8 i,1 ; 459: goto → 419
```

### 2.4 媒体会话路径（可选层，需要通知使用权）

```java
// F2.x1.G()
if (Settings.Secure.enabled_notification_listeners 含本包) {   // O1/o.java A(Context)
    MediaController mc = cached ?? l();
    if (mc != null && mc.getPlaybackInfo().getVolumeControl() == 2
                  && mc.getPlaybackInfo().getMaxVolume() > 0) return mc;
}
return null;

// F2.x1.l()
mediaSessionManager.getActiveSessions(new ComponentName(ctx, MediaSessionNotificationListener.class));
// 优先沿用上一次的 session token 且（playing 或 有 metadata），否则取第一个 playing/有 metadata 的
// SecurityException / 异常 → 返回 null（静默降级到 AudioManager 路径）
```

`MediaSessionNotificationListener` 本体是**空类**（仅 10 行），作用只是让
`MediaSessionManager.getActiveSessions()` 不抛 `SecurityException`。
`getVolumeControl()==2` 即 `PlaybackInfo.VOLUME_CONTROL_ABSOLUTE`；本地播放的会话由
system_server 代表媒体应用执行音量变更，因此绕过了 ColorOS 对普通后台应用的拦截。

> 注意：**没有通知使用权时这条路径自动失效**，音量仍然靠 2.3 的相对步进兜底工作。
> 也就是说 OHC 在 ColorOS 上能用，并不依赖通知使用权。

### 2.5 常量与数学（移植必须一致）

| 常量 | 值 | 位置 |
|---|---|---|
| 流 | `STREAM_MUSIC = 3` | 全部三处 |
| flags | `0`（`setStreamVolume` / `adjustStreamVolume`） | 滑动 + 面板路径 |
| 步进上限 | `24` 步 / 次应用 | `F2.h1.run case 9` |
| 应用节流 | `24 ms`，`fromUser=true` 时立即 | `LE0/p case default` |
| 「无效果」上报 | 每个 session/面板实例只上报一次 | `reportedVolumeNoEffect` / `J` 标志 |
| 分数 → 索引 | `round(clamp(f,0,1) * (max - min)) + min` | 面板路径 |
| 索引 → 分数 | `(clamp(i,min,max) - min) / (max - min)` | 面板 UI 同步 |
| min/max | `min = SDK>=28 ? getStreamMinVolume(3) : 0`；`max = max(getStreamMaxVolume(3), min+1)` | 两处 |
| 媒体会话判定 | `playbackInfo.getVolumeControl() == 2 && getMaxVolume() > 0` | 两处 |
| 声音固定 | `audioManager.isVolumeFixed()` → toast + 上报 | 两处 |
| 面板窗口 | `type = 2032 (TYPE_ACCESSIBILITY_OVERLAY)`，flags `264/776/808` | `EdgeGestureService` |

遥测字符串（可用 logcat/抓包确认设备上走了哪条分支）：
`volume_slider_set_exception`、`volume_slider_set_no_effect`、`volume_slider_adjust_exception`、
`volume_slider_controller_set_exception`、`quick_tools_volume_set_exception`、
`quick_tools_volume_set_no_effect`、`quick_tools_volume_adjust_exception`、
`quick_tools_controller_volume_set_exception`、`quick_tools_volume_fixed`。

---

## 3. 为什么 ColorOS 上必须这么写

证据链（不依赖推测的部分）：

1. 双方窗口类型相同（`2032` 无障碍悬浮窗）、权限相同（`MODIFY_AUDIO_SETTINGS`），OHC 无 root/Shizuku/shell/keyevent，
   → 差异只可能来自 API 调用方式。
2. OHC 专门区分了 `*_set_exception`（抛异常）、`*_set_no_effect`（**没抛异常但回读值没变**）、
   `*_adjust_exception` 三类遥测，说明作者在真机上观察到"绝对设置静默失效"，才写的相对步进兜底。
3. 用户实测：XGesture 的 `VOLUME_UP`/`VOLUME_DOWN`（`adjustStreamVolume`）在这台 ColorOS 上正常，
   而 `setStreamVolume` 路径（滑动调节 + 面板音量条）"滑动后回弹、无变化"。
   → 在本机可复现的差异恰好就是 OHC 兜底的那一条路径。
4. 用户观察到的"回弹"= XGesture 应用绝对值 → ColorOS 拒绝/还原 → UI 在抬手时
   `readCurrentAdjustFraction()` 读回旧值（`AdjustPanelTouchHandler.ACTION_UP`）→ 视觉上跳回。

机制层面：ColorOS 的音频策略对**非前台应用**的"绝对值音量设置"做了限制（允许媒体键/相对步进这类
用户意图明确的变更，或由 system_server 代表正在播放的媒体会话执行），因此
`setStreamVolume` 静默无效、`adjustStreamVolume` 有效。OHC 的做法本质上就是
"用相对步进把绝对值模拟出来"。

---

## 4. XGesture 现状对照

| 位置 | 现状 |
|---|---|
| `app/src/main/java/com/slideindex/app/util/VolumeControlHelper.kt:172-181` | `setFraction()`：算出 `level`，`if (level == getStreamVolume) return`，否则 **只** `setStreamVolume(audioStream, level, 0)` |
| `app/src/main/java/com/slideindex/app/util/ContinuousAdjustController.kt:92-138` | 拖动/面板都调 `VolumeControlHelper.setFraction(...)`（单一出口，好改） |
| `app/src/main/java/com/slideindex/app/overlay/AdjustPanelTouchHandler.kt:130-160` | 每个 `ACTION_MOVE` → `updateContinuousAdjust` → `setFraction`；抬手读回系统值 |
| `app/src/main/java/com/slideindex/app/overlay/OhoQuickToolsPanelState.kt:306-309` | 快捷工具面板音量条 → `continuousAdjust.setFraction(Mode.VOLUME, …)` |
| `app/src/main/java/com/slideindex/app/gesture/GestureSessionActionDispatch.kt:143,215,531` | `GestureAction.AdjustVolume` → `ContinuousAdjustController.Mode.VOLUME` |

即：**两处症状共用同一个函数**（`VolumeControlHelper.setFraction`），改一处即可覆盖。

另外 `ADJUST_VOLUME`/`ADJUST_BRIGHTNESS`、`OPEN_VOLUME_PANEL`、`VOLUME_UP/DOWN`、
`FORCE_STOP_CURRENT_APP` 等动作在枚举里都已存在（`core/gesture/.../GestureAction.kt:37,101,119,121,123`）。

---

## 5. 移植方案（可直接落地）

### 5.1 必做：把 `setFraction` 换成 OHC 的分层写入

```kotlin
// app/src/main/java/com/slideindex/app/util/VolumeControlHelper.kt
private const val MAX_ADJUST_STEPS_PER_APPLY = 24   // 与 One Hand Control 一致

fun setFraction(context: Context, stream: Stream, fraction: Float) {
    if (stream.requiresPolicyAccess() && !hasAccess(context)) return
    val manager = audioManager(context) ?: return
    val audioStream = toAudioStream(stream)
    val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getStreamMinVolume(audioStream) else 0
    val max = manager.getStreamMaxVolume(audioStream).coerceAtLeast(min + 1)
    val level = (fraction.coerceIn(0f, 1f) * (max - min)).roundToInt() + min
    applyStreamLevel(manager, audioStream, level, min, max)
}

/**
 * One Hand Control 同款分层写入：
 * 1) 绝对设置 setStreamVolume(index, 0)；
 * 2) 立即 getStreamVolume() 回读校验；
 * 3) 不一致（ColorOS 会静默忽略绝对值设置）→ adjustStreamVolume(±1, 0) 步进补齐，单次最多 24 步。
 */
fun applyStreamLevel(manager: AudioManager, stream: Int, target: Int, min: Int, max: Int): Int {
    val want = target.coerceIn(min, max)
    if (manager.getStreamVolume(stream).coerceIn(min, max) == want) return want

    try {
        manager.setStreamVolume(stream, want, 0)
    } catch (e: Exception) {
        Log.w(TAG, "setStreamVolume(stream=$stream, want=$want) threw", e)
    }

    var actual = manager.getStreamVolume(stream).coerceIn(min, max)
    if (actual == want) return actual
    Log.i(TAG, "setStreamVolume had no effect stream=$stream want=$want actual=$actual; stepping")

    var steps = minOf(kotlin.math.abs(want - actual), MAX_ADJUST_STEPS_PER_APPLY)
    var done = 0
    while (done < steps && actual != want) {
        try {
            manager.adjustStreamVolume(
                stream,
                if (want > actual) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0,
            )
            actual = manager.getStreamVolume(stream).coerceIn(min, max)
            done++
        } catch (e: Exception) {
            Log.w(TAG, "adjustStreamVolume step $done failed", e)
            return actual
        }
    }
    return actual
}
```

要点（与 OHC 逐条对应）：

* 先绝对、后相对，**中间必须回读**（OHC 的 `getStreamVolume` 紧跟在 `setStreamVolume` 之后）；
* 步进 `flags = 0`、单次上限 24（`const/16 0x18`）；
* 分数↔索引改成 `(max - min)` 归一化（顺带把 `readFraction` 改成
  `(getStreamVolume(s) - min) / (max - min)`，否则滑动条与系统值在 min>0 的机型上会错位）；
* 早退条件从"level == 当前值"改为"回读 == want"，避免 ColorOS 回弹后不再重试。

### 5.2 建议：24 ms 节流（可选，但能省 binder）

`ACTION_MOVE` 每个事件都会调一次；OHC 用 `24 - (now - lastApplyAt)` 节流、拖动时立即应用。
XGesture 可在 `ContinuousAdjustController` 里加同样的 24 ms 下限（抬手时强制 flush）。

### 5.3 可选硬化：延迟复检（OHC 没有，但能治"回弹"）

若真机表现为"设置后短暂生效又被打回"，可在 `applyStreamLevel` 之后 `postDelayed(120ms)`
再回读一次，若被还原则重跑步进补偿。**建议先按 5.1 验证，再决定是否加。**

### 5.4 可选：媒体会话路径（需要新增 NotificationListenerService）

```kotlin
// 1) 新增空服务 + manifest 声明
class MediaSessionListenerService : NotificationListenerService()
// <service android:name=".service.MediaSessionListenerService"
//          android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" android:exported="true">
//   <intent-filter><action android:name="android.service.notification.NotificationListenerService"/></intent-filter>
// </service>

// 2) 只有在 Settings.Secure.enabled_notification_listeners 含本包时才可用
val sessions = ctx.getSystemService(MediaSessionManager::class.java)
    .getActiveSessions(ComponentName(ctx, MediaSessionListenerService::class.java))
val mc = sessions.firstOrNull {
    it.playbackInfo?.let { pi -> pi.volumeControl == PlaybackInfo.VOLUME_CONTROL_ABSOLUTE && pi.maxVolume > 0 } == true
}
if (mc != null) { mc.setVolumeTo(want.coerceIn(0, mc.playbackInfo.maxVolume), 0); return }  // 成功即返回
```

* 这一层**不解决 ColorOS 问题**（5.1 才是），只是给"正在播放媒体的应用"提供一条由 system_server 代执行的通道；
* 好处：XGesture 已有 Shizuku，可以用
  `cmd notification allow_listener <pkg>/<cls>` 一步授权，比 OHC 引导用户手点设置更顺；
* 风险：多一个常驻服务 + 一条敏感权限，建议做成开关（默认关）。

### 5.5 顺带（同一函数受益）

* 「快捷工具面板」音量条：`OhoQuickToolsPanelState.updateVolume` 无需改动，自动生效；
* `ADJUST_VOLUME` 手势滑动：同上；
* 扩展面板的 ring/notification 滑条：同函数，但这两个流仍需 `hasAccess`（勿扰策略权限），ColorOS 行为另测。

---

## 6. 真机验证清单

1. ColorOS 上拖动**快捷工具面板**音量条：应连续变化，不再回弹；
2. `ADJUST_VOLUME` 手势上下滑：同上；抬手后 UI 值 = 系统值（不回跳）；
3. `adb logcat -s VolumeControlHelper`：应看到
   `setStreamVolume had no effect ... stepping`（证明走了兜底），或直接 return（说明该 ROM 正常）；
4. `adb shell dumpsys audio | Select-String -Pattern 'STREAM_MUSIC' -Context 0,6`
   观察 `streamVolume` 是否跟随拖动；
5. 回归：音量 0 ↔ 最大、静音/勿扰状态、蓝牙 A2DP 连接时、播放/暂停时各测一轮；
6. 非 ColorOS 机型（Pixel/小米）确认没有变慢或跳变（绝对值设置一次成功，不会进循环）。

---

## 7. 风险与未决

* OHC 面板路径在 `setVolumeTo` 成功后**不做回读校验**；若某些 ROM 上该调用静默失败，会白走一步再落到
  AudioManager 路径（我们有回读，会比它更稳）。
* 24 步上限是为 `max=100` 的机型准备的；`max=15` 的机型一次拖动事件即可覆盖全量程。
* ColorOS 的拦截点未在系统源码层面证实（无 ROM 源码），结论基于"双方 API 用法差异 + 本机可复现现象 +
  OHC 遥测设计"三条独立证据；5.1 的改动是**幂等**的（绝对值成功时行为与现在完全一致），因此风险可控。

---

## 8. 附：快捷工具面板音量条的 UI 规格（供像素级还原参考）

`F2.x1` 构建该行（`C0279x1.java:945-966`），滑条本体是自绘 `View` `F2.s1 extends View`
（`F2/C0264s1.java`）。OHC 面板同样是 `TYPE_ACCESSIBILITY_OVERLAY` 窗口，所以**拖动本身**
在 ColorOS 上不受限，受限的只有音量写入。

| 项 | 值（源码常量） |
|---|---|
| 行高 | `dp(48)`，`topMargin = dp(8)`（音量行与亮度行相同） |
| 行背景 | 圆角 `dp(6)`（`t(ctx, color, 6)`） |
| 轨道描边 | `6.0f * density` |
| 轨道底色 | `889192447` = `0x34FFFFFF`（白 20%） |
| 进度色 | 构造参数传入：主题强调色或 `volume_slider_color_rgb`（`volume_slider_use_accent_color` 控制） |
| 轨道范围 | 左起 `58.0f * density`，右缩 `22.0f * density` |
| 左侧 Logo 圆 | 半径 `28.0f * density`（`f5 = density * 28`），点按 = `onVolumeLogoTapped`（静音/恢复） |
| 刻度/胶囊细节 | 笔宽 `1.9f * density`，刻度位置 `6.8/9.7/10.0/5.2/4.2/0.6/8.0/3.0/11.0/7.0/10.2 dp`（三种样式分支） |
| 触摸 → 分数 | `fraction = (event.x - 58dp) / (width - 22dp - 58dp)`（`C0264s1.java:193-239`） |
| 拖动时 | 立即回调 `onVolumeProgressChanged(fraction, fromUser=true)`；抬手无额外提交 |
| 长按反馈样式 | 3 种：`volume_slider_style_system_pill`（音量胶囊）/ `_edge_rail`（边缘轨道）/ `_minimal`（细线），另有强调色开关 |
| 亮度行 | 同一个 `F2.s1`，`EnumC0261r1` 区分 kind；`Settings.System.canWrite` 为假时 `setAlpha(0.58f)` 置灰 |

如果只想解决问题而不改 UI：**XGesture 现有滑条完全可以保留**，只按 §5.1 换掉写入实现即可。

---

## 9. 实施记录

改动文件：`app/src/main/java/com/slideindex/app/util/VolumeControlHelper.kt`

* 新增 `applyStreamLevel(manager, stream, target, min, max)`：绝对设置 → 立即回读 → 未生效则相对步进
  补齐（单次 ≤ `MAX_ADJUST_STEPS` = 24 步，每步回读），返回实际生效级数；
* `setFraction()`：归一化改为 `(max - min)` 口径（`min = getStreamMinVolume`，
  `max = max(getStreamMaxVolume, min + 1)`），改调 `applyStreamLevel()`；原先的
  `if (level == 当前值) return` 与裸 `setStreamVolume` 移除（判断已进入新函数）；
* `readFraction()`：与 `setFraction` 同口径，避免 min>0 机型上滑条与系统值错位；
* 新增私有 `streamMin` / `streamMax` 与常量 `MAX_ADJUST_STEPS`；
* **公开签名未变**，全部调用点无需改动：`ContinuousAdjustController`、`OhoQuickToolsPanelState`、
  `VolumePanelContent`、`ActionExecutorMediaSystem`、`SystemGestureActions`（静音全部）。

未做的部分：媒体会话第二层（§5.4）、24 ms 节流（§5.2 —— OHC 在 `fromUser=true` 时同样是
立即应用，行为一致，故先不加）。

编译校验：改动时工作区存在三个互相阻塞的 `:app:compileFullDebugKotlin` 构建，Gradle 侧 KSP
报 stale 生成目录错误（`NoSuchFileException: app\build\generated\ksp\fullDebug\...`），与本次改动无关。
为不受干扰，改用 Kotlin 编译器（`kotlin-compiler-embeddable` 2.4.20）配合 SDK 的
`android-36/android.jar` 单独编译该文件（桩见 `.tmp/volcheck/Stubs*.kt`）：**编译通过、无错误**，
产物含 `VolumeControlHelper.class`。

设备侧验证清单同 §6；预期在 ColorOS 的 logcat 中看到
`VolumeControlHelper: setStreamVolume no effect stream=3 want=… actual=…; stepping`。
