# 闪念 (Capsule) Panel — Verbatim Implementation Spec

Source: `ui_demo_capsule.2050.html` (95 296 bytes, 1582 lines, single file, no external assets).
Title: `闪念 · 贴边把手 + 两页签面板`.
Purpose: this document is a **literal** transcription of the demo so a Jetpack Compose
implementation can be rebuilt to pixel/behaviour parity. Numbers are copied verbatim;
nothing is rounded or paraphrased. Where a value is derived (e.g. `78%` of a 390 dp phone)
the derivation is written out.

Reference geometry: **phone = 390 × 844 px**, so `width:78%` on the panel = **304.2 px**
and the panel's "drag-to-close" reference width `W = 390 * 0.78 = 304.2`.

---

## 1. Design tokens

### 1.1 `:root` (light = default) — lines 11–51

```css
:root {
  --s1:4px; --s2:8px; --s3:12px; --s4:16px; --s5:20px; --s6:24px; --s7:32px;
  --r-xs:8px; --r-sm:12px; --r-md:14px; --r-lg:18px; --r-xl:22px; --r-2xl:26px; --r-pill:999px;
  --f-tiny:10.5px; --f-meta:11.5px; --f-sm:12.5px; --f-body:13.5px;
  --f-title-s:15px; --f-title:19px; --f-display:26px;
  --e-out:cubic-bezier(.22,1,.36,1); --e-io:cubic-bezier(.4,0,.2,1);
  --d1:140ms; --d2:200ms; --d3:280ms;
  --hit:48px;                                  /* Android 最小触摸目标 */

  --page-bg:
    radial-gradient(1100px 560px at 12% -8%, #E3E9F7 0%, transparent 62%),
    radial-gradient(900px 500px at 92% 4%, #EFE8FA 0%, transparent 58%),
    #F3F5FA;
  --card-bg:linear-gradient(180deg, #FFFFFF, #FAFBFE);
  --seg-bg:rgba(18,22,40,.05);
  --sw-bg:rgba(18,22,40,.16);
  --btn-bg:rgba(18,22,40,.04);
  --btn-bd:rgba(18,22,40,.13);
  --code-bg:rgba(18,22,40,.06);
  --code-fg:#3B4A86;

  --app-bg:#EEF1F7; --app-elev:#F7F8FC;
  --text:#14161C; --sub:#666C80;
  --hair:rgba(18,22,40,.07); --line:rgba(18,22,40,.09);
  --accent:#5B6CF0; --accent-solid:#4A57D8; --accent-soft:rgba(91,108,240,.14);
  --warn:#C2622B; --danger:#C43D3F;

  --g-fill:linear-gradient(180deg, rgba(255,255,255,.78), rgba(255,255,255,.58));
  --g-border:rgba(255,255,255,.92);
  --g-rim:rgba(255,255,255,1);
  --g-rim-dim:rgba(24,30,52,.05);
  --g-gloss:rgba(255,255,255,.55);
  --g-shadow:rgba(24,30,52,.30);
  --g-shadow2:rgba(24,30,52,.16);
  --g-solid:linear-gradient(180deg,#FFFFFF,#F4F6FB);

  --scrim:rgba(20,24,44,.20);
  --frame:#12161C;
  --hdr-a:#3E68E0; --hdr-b:#7A5AE0;
  --blur:20px;
}
```

### 1.2 Dark override — lines 52–83

**The demo has NO `@media (prefers-color-scheme: dark)` block.** Dark mode is the
attribute selector `[data-theme="dark"]`, set on `<html>` (live phone) or on the
individual `.frame.phone` node (gallery frames, `data-theme="light"|"dark"`).
The only media query in the whole file is `prefers-reduced-motion` (see §5.5).

```css
[data-theme="dark"] {
  --page-bg:
    radial-gradient(1100px 560px at 12% -8%, #1B2030 0%, transparent 62%),
    radial-gradient(900px 500px at 92% 4%, #1A1730 0%, transparent 58%),
    #0C0D12;
  --card-bg:linear-gradient(180deg, rgba(255,255,255,.055), rgba(255,255,255,.022));
  --seg-bg:rgba(255,255,255,.06);
  --sw-bg:rgba(255,255,255,.18);
  --btn-bg:rgba(255,255,255,.075);
  --btn-bd:rgba(255,255,255,.19);
  --code-bg:rgba(255,255,255,.09);
  --code-fg:#C3CBEE;

  --app-bg:#0A0B0F; --app-elev:#12141A;
  --text:#ECEEF4; --sub:#9BA1B2;
  --hair:rgba(255,255,255,.08); --line:rgba(255,255,255,.10);
  --accent:#6E7CF7; --accent-solid:#5563E8; --accent-soft:rgba(110,124,247,.16);
  --warn:#F0A96B; --danger:#FF8F8F;

  --g-fill:linear-gradient(180deg, rgba(52,57,74,.58), rgba(28,32,44,.52));
  --g-border:rgba(255,255,255,.11);
  --g-rim:rgba(255,255,255,.30);
  --g-rim-dim:rgba(255,255,255,.05);
  --g-gloss:rgba(255,255,255,.13);
  --g-shadow:rgba(0,0,0,.70);
  --g-shadow2:rgba(0,0,0,.40);
  --g-solid:linear-gradient(180deg,#1D2029,#171A21);

  --scrim:rgba(6,8,14,.44);
  --frame:#161A20;
  --hdr-a:#2A3AA0; --hdr-b:#5A3EA8;
}
```

### 1.3 Token table (light / dark)

| Token | Light | Dark |
|---|---|---|
| `--s1` … `--s7` | 4 / 8 / 12 / 16 / 20 / 24 / 32 px | same |
| `--r-xs` | 8px | same |
| `--r-sm` | 12px | same |
| `--r-md` | 14px | same |
| `--r-lg` | 18px | same |
| `--r-xl` | 22px | same |
| `--r-2xl` | 26px | same |
| `--r-pill` | 999px | same |
| `--f-tiny` | 10.5px | same |
| `--f-meta` | 11.5px | same |
| `--f-sm` | 12.5px | same |
| `--f-body` | 13.5px | same |
| `--f-title-s` | 15px | same |
| `--f-title` | 19px | same |
| `--f-display` | 26px | same |
| `--e-out` | `cubic-bezier(.22,1,.36,1)` | same |
| `--e-io` | `cubic-bezier(.4,0,.2,1)` | same |
| `--d1` | 140ms | same |
| `--d2` | 200ms | same |
| `--d3` | 280ms | same |
| `--hit` | 48px | same |
| `--page-bg` | 2 radial + `#F3F5FA` | radial `#1B2030` / radial `#1A1730` + `#0C0D12` |
| `--card-bg` | `linear-gradient(180deg,#FFFFFF,#FAFBFE)` | `linear-gradient(180deg,rgba(255,255,255,.055),rgba(255,255,255,.022))` |
| `--seg-bg` | `rgba(18,22,40,.05)` | `rgba(255,255,255,.06)` |
| `--sw-bg` | `rgba(18,22,40,.16)` | `rgba(255,255,255,.18)` |
| `--btn-bg` | `rgba(18,22,40,.04)` | `rgba(255,255,255,.075)` |
| `--btn-bd` | `rgba(18,22,40,.13)` | `rgba(255,255,255,.19)` |
| `--code-bg` | `rgba(18,22,40,.06)` | `rgba(255,255,255,.09)` |
| `--code-fg` | `#3B4A86` | `#C3CBEE` |
| `--app-bg` | `#EEF1F7` | `#0A0B0F` |
| `--app-elev` | `#F7F8FC` | `#12141A` |
| `--text` | `#14161C` | `#ECEEF4` |
| `--sub` | `#666C80` | `#9BA1B2` |
| `--hair` | `rgba(18,22,40,.07)` | `rgba(255,255,255,.08)` |
| `--line` | `rgba(18,22,40,.09)` | `rgba(255,255,255,.10)` |
| `--accent` | `#5B6CF0` | `#6E7CF7` |
| `--accent-solid` | `#4A57D8` | `#5563E8` |
| `--accent-soft` | `rgba(91,108,240,.14)` | `rgba(110,124,247,.16)` |
| `--warn` | `#C2622B` | `#F0A96B` |
| `--danger` | `#C43D3F` | `#FF8F8F` |
| `--g-fill` | `linear-gradient(180deg, rgba(255,255,255,.78), rgba(255,255,255,.58))` | `linear-gradient(180deg, rgba(52,57,74,.58), rgba(28,32,44,.52))` |
| `--g-border` | `rgba(255,255,255,.92)` | `rgba(255,255,255,.11)` |
| `--g-rim` | `rgba(255,255,255,1)` | `rgba(255,255,255,.30)` |
| `--g-rim-dim` | `rgba(24,30,52,.05)` | `rgba(255,255,255,.05)` |
| `--g-gloss` | `rgba(255,255,255,.55)` | `rgba(255,255,255,.13)` |
| `--g-shadow` | `rgba(24,30,52,.30)` | `rgba(0,0,0,.70)` |
| `--g-shadow2` | `rgba(24,30,52,.16)` | `rgba(0,0,0,.40)` |
| `--g-solid` | `linear-gradient(180deg,#FFFFFF,#F4F6FB)` | `linear-gradient(180deg,#1D2029,#171A21)` |
| `--scrim` | `rgba(20,24,44,.20)` | `rgba(6,8,14,.44)` |
| `--frame` | `#12161C` | `#161A20` |
| `--hdr-a` | `#3E68E0` | `#2A3AA0` |
| `--hdr-b` | `#7A5AE0` | `#5A3EA8` |
| `--blur` | 20px | **not overridden → 20px** |

### 1.4 Theme / glass toggles on `<html>`

| Attribute | Values | Effect |
|---|---|---|
| `data-theme` | `light` (set at boot, line 1577) \| `dark` | swaps the token block |
| `data-glass` | `on` \| `off` | `off` kills `backdrop-filter` on all `.g`, swaps background to `--g-solid`, drops the 2nd outer shadow, and sets `.g::after { opacity: 0 }` |
| `--blur` | runtime, `0px`–`32px`, default `20px` | live-edited by the range slider |

### 1.5 Global resets / typography — lines 85–95

```css
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html { -webkit-text-size-adjust: 100%; }
body {
  margin: 0; padding: 48px 40px 80px; color: var(--text); background: var(--page-bg);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "PingFang SC", Roboto, sans-serif;
  font-variant-numeric: tabular-nums; -webkit-font-smoothing: antialiased;
}
code { background: var(--code-bg); padding: 1px 6px; border-radius: 5px; color: var(--code-fg);
  font-size: 11.5px; font-family: ui-monospace, "SFMono-Regular", Consolas, monospace; }
b { font-weight: 650; }
button { font-family: inherit; }
```

Note `body { padding: 48px 40px 80px }` and `font-variant-numeric: tabular-nums` — the phone is
laid out inside a desktop page, so the phone's own coordinates start at the `.phone` box.

### 1.6 The glass mixin `.g` (applies to panel, chips, slot, editbar, toast, menu, peek, pip) — lines 168–184

```css
.g {
  position: relative; background: var(--g-fill);
  -webkit-backdrop-filter: blur(var(--blur)) saturate(1.55);
  backdrop-filter: blur(var(--blur)) saturate(1.55);
  border: 1px solid var(--g-border);
  box-shadow: inset 0 1px 0 var(--g-rim), inset 0 -1px 0 var(--g-rim-dim),
    0 14px 34px -16px var(--g-shadow), 0 3px 10px -6px var(--g-shadow2);
}
.g::before { content: ''; position: absolute; inset: 0; border-radius: inherit; pointer-events: none;
  background: radial-gradient(130% 86% at 16% -22%, var(--g-gloss), transparent 56%),
              linear-gradient(180deg, color-mix(in srgb, var(--g-gloss) 55%, transparent), transparent 38%); }
.g::after { content: ''; position: absolute; inset: 0; border-radius: inherit; pointer-events: none;
  opacity: .045; mix-blend-mode: overlay;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='120' height='120'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2'/%3E%3C/filter%3E%3Crect width='120' height='120' filter='url(%23n)'/%3E%3C/svg%3E"); }
[data-glass="off"] .g { -webkit-backdrop-filter: none; backdrop-filter: none; background: var(--g-solid);
  box-shadow: inset 0 1px 0 var(--g-rim), 0 10px 26px -16px var(--g-shadow); }
[data-glass="off"] .g::after { opacity: 0; }
```

`color-mix(in srgb, X p%, transparent)` semantics used throughout: the result is `X`
with its **alpha multiplied by `p/100`**. In Compose that is
`X.copy(alpha = X.alpha * p / 100f)`, **not** `X.copy(alpha = p/100f)`.
`color-mix(in srgb, A p%, #fff)` blends toward white in **premultiplied sRGB**, which is
not `lerp` on the raw channels unless alpha is 1.

### 1.7 Z-index ladder (exact, panel-bearing surfaces)

| z | Element |
|---|---|
| 20 | `.scrim` |
| 22 | `.gzone` (system back-gesture zone debug overlay) |
| 28 | `.pipwrap` (+ `.pipwrap.legacy`) |
| 42 | `.slot` |
| 44 | `.peek` |
| 50 | `.stream` (panel) |
| 55 | `.fab` |
| 56 | `.composer` |
| 60 | `.editbar` |
| 62 | `.toast` |
| 66 | `.menu` |
| 70 | `.ime` |
| 90 | `.statusbar` |
| 95 | `.punch`, `.homebar` |

Consequences: the scrim dims the app but **not** the handle; the panel covers the handle;
the FAB/composer live *inside* the panel's stacking context (`z-index:50` on `.stream`, so
they still paint above `.stream`'s siblings only because they are descendants); the inline
edit bar, toast, menu and fake IME all paint **above** the panel; the fake IME covers the
composer.

---

## 2. Phone frame + backdrop

### 2.1 `.phone` — lines 111–116

```css
.phone {
  position: relative; width: 390px; height: 844px; flex: 0 0 auto;
  border-radius: 44px; overflow: hidden; background: var(--app-bg);
  box-shadow: 0 0 0 12px var(--frame), 0 0 0 13px rgba(255,255,255,.06),
    0 34px 74px -22px rgba(0,0,0,.8), 0 10px 26px -14px rgba(0,0,0,.6);
}
```

* Device: **390 × 844**, corner radius **44**, clipped (`overflow:hidden`).
* Bezel: solid ring **12px** of `--frame` (`#12161C` light / `#161A20` dark) plus a
  **1px** `rgba(255,255,255,.06)` hairline ring at 13px, then two drop shadows
  `0 34px 74px -22px rgba(0,0,0,.8)` and `0 10px 26px -14px rgba(0,0,0,.6)`.

### 2.2 Punch hole + home bar — lines 117–120

```css
.phone .punch { position: absolute; top: 12px; left: 50%; margin-left: -4px; width: 8px; height: 8px;
  border-radius: 50%; background: #05060A; z-index: 95; }
.phone .homebar { position: absolute; bottom: 8px; left: 50%; margin-left: -60px; width: 120px; height: 4px;
  border-radius: 2px; background: color-mix(in srgb, var(--text) 30%, transparent); z-index: 95; }
```

### 2.3 Status bar — lines 121–132

```css
.statusbar { position: absolute; top: 0; left: 0; right: 0; height: 30px; z-index: 90;
  display: flex; justify-content: space-between; align-items: center; padding: 0 22px; color: var(--text); }
.statusbar .tm { font-size: 12px; font-weight: 650; letter-spacing: .3px; }
.statusbar .icons { display: flex; align-items: center; gap: 6px; }
.statusbar .bars { display: flex; align-items: flex-end; gap: 1.6px; }
.statusbar .bars i { width: 2.6px; border-radius: 1px; background: currentColor; }
.statusbar .bat { width: 22px; height: 11px; border: 1.2px solid currentColor; border-radius: 3.5px;
  opacity: .9; position: relative; }
.statusbar .bat::after { content: ''; position: absolute; top: 1.6px; left: 1.6px; bottom: 1.6px;
  width: 12px; border-radius: 1.6px; background: currentColor; }
.statusbar .bat::before { content: ''; position: absolute; right: -3px; top: 3.4px; width: 1.8px;
  height: 3.6px; border-radius: 0 1px 1px 0; background: currentColor; opacity: .6; }
```

Injected markup (lines 800–804), fixed time **14:20**:
signal bars with inline heights `4px, 6px, 8px, 10px` (width 2.6px each, gap 1.6px),
a 14×11 wifi SVG (`stroke-width:1.35`, `stroke-linecap:round`,
`M1 3.6a9 9 0 0 1 12 0M3.4 6.2a5.6 5.6 0 0 1 7.2 0M7 8.9h.01`),
then the 22×11 battery outline with a 12px fill inset 1.6px and a 1.8×3.6px nub at the right.

### 2.4 The dimmed backdrop to the left of the panel — lines 186–188

```css
.scrim { position: absolute; inset: 0; z-index: 20; background: var(--scrim);
  opacity: 0; pointer-events: none; transition: opacity var(--d2) var(--e-io); }
.scrim.on { opacity: 1; pointer-events: auto; }
```

* Full-bleed `inset:0` over the whole phone, `--scrim` = `rgba(20,24,44,.20)` light /
  `rgba(6,8,14,.44)` dark. **No blur** on the scrim — the blur belongs to the panel.
* The panel is `width:78%` pinned to the right, so the dimmed strip that remains visible is
  the left **22%** = **85.8px**.
* `.on` is driven by `render()`: `elScrim.classList.toggle('on', elStream.classList.contains('open'))`.
* Clicking the scrim calls `closeStream(); closeEdit(); closeSlot(); hideToast();`.

### 2.5 The third-party app behind (rebuilt on every render) — lines 135–163

```css
.app { position: absolute; inset: 0; overflow: hidden; }
.app .hdr { height: 186px; padding: 48px 24px 0;
  background: linear-gradient(158deg, var(--hdr-a), var(--hdr-b)); color: #fff; }
.app .hdr .t { font-size: var(--f-title); font-weight: 700; letter-spacing: -.2px; }
.app .hdr .s { font-size: var(--f-meta); opacity: .8; margin-top: 5px; letter-spacing: .3px; }
.app .hdr .u { width: 40px; height: 3px; border-radius: 2px; background: rgba(255,255,255,.88); margin-top: 16px; }
.app .doc { padding: 26px 24px 0; }
.app .doc h2 { font-size: 21px; line-height: 1.42; letter-spacing: -.3px; font-weight: 700;
  color: var(--text); margin: 0 0 12px; }
.app .doc .meta { display: flex; align-items: center; gap: 6px; font-size: var(--f-meta);
  color: var(--sub); letter-spacing: .2px; }
.app .doc .meta .av { width: 14px; height: 14px; border-radius: 50%;
  background: linear-gradient(140deg,#7C86C8,#B79AE0); }
.app .hero { position: relative; height: 250px; margin: 20px 24px 0; border-radius: var(--r-lg);
  overflow: hidden; background: linear-gradient(158deg,#5F6AAE 0%, #8E7CC9 52%, #C0A0E2 100%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.35), 0 10px 26px -14px rgba(0,0,0,.6); }
.app .hero::after { content: ''; position: absolute; inset: 0;
  background: radial-gradient(120% 80% at 80% 8%, rgba(255,255,255,.30), transparent 58%),
              linear-gradient(180deg, transparent 55%, rgba(0,0,0,.22)); }
.app .doc .par { margin-top: 22px; display: grid; gap: 13px; }
.app .doc .par i { display: block; height: 8px; border-radius: 4px;
  background: linear-gradient(90deg, var(--line) 62%, transparent); }
.app .doc .par i:nth-child(2) { width: 92%; } .app .doc .par i:nth-child(3) { width: 86%; }
.app .doc .par i:nth-child(4) { width: 95%; } .app .doc .par i:nth-child(5) { width: 58%; }
.app .tabbar { position: absolute; left: 0; right: 0; bottom: 0; height: 62px;
  background: linear-gradient(180deg, transparent, color-mix(in srgb, var(--app-bg) 88%, transparent));
  display: flex; align-items: flex-end; justify-content: space-around; padding-bottom: 16px; }
.app .tabbar i { width: 18px; height: 18px; border-radius: 6px; background: var(--hair); }
.app .tabbar i.on { background: color-mix(in srgb, var(--accent) 55%, transparent); }
```

Its strings: header title `今日要闻`, subtitle `科技 · 设计 · 产品`, article title
`液态玻璃之后，<br>系统 UI 会走向哪里`, meta `2026-10-05 · 8 分钟阅读`.
The 5 paragraph bars use `nth-child` widths (100 / 92 / 86 / 95 / 58 %), the last bar being
the shortest. Bottom tab bar = 4 squares of 18×18 r6, first one "on". Note the whole `.app`
subtree is rebuilt on **every** `render()` (`elApp.innerHTML = appHtml()`), so any state in
it is lost — in Compose this is a static composable that must never recompose-allocate.

### 2.6 Debug overlays that affect the backdrop — lines 195–228

```css
/* 命中区 48×48dp 贴边，但视觉条要贴回右缘 12px —— 居中的话会被推到 19.5px，看着浮在半空 */
.pipwrap { position: absolute; right: 0; top: 44%; width: var(--hit); height: var(--hit); z-index: 28;
  display: flex; align-items: center; justify-content: flex-end; padding-right: 12px; cursor: grab; }
.pipwrap.grabbing { cursor: grabbing; }
.pipwrap:hover .pip { transform: translateX(-2px); }
.pip { position: relative; width: 9px; height: 28px; border-radius: 5px;
  transition: transform var(--d2) var(--e-out), background var(--d2) var(--e-io),
    box-shadow var(--d2) var(--e-io); }
/* 有待办未完成时只「变色」——不出数字、不加宽 */
.pip.due { background: var(--accent-solid); border-color: transparent;
  box-shadow: 0 5px 14px -5px color-mix(in srgb, var(--accent-solid) 75%, transparent); }
.pip.pulse { animation: pippulse 900ms var(--e-out); }
@keyframes pippulse { 0%, 100% { transform: none; }
  35% { transform: translateX(-3px) scaleY(1.18); } }

/* 合并前对照：另一个把手（剪贴板把手的老位置） */
.pipwrap.legacy { top: 62%; opacity: 0; pointer-events: none; transition: opacity var(--d2) var(--e-io); }
.pipwrap.legacy.on { opacity: 1; }
.pipwrap.legacy .pip { background: color-mix(in srgb, var(--warn) 70%, #fff); }

.gzone { position: absolute; top: 0; bottom: 0; right: 0; width: 48px; z-index: 22;
  background: repeating-linear-gradient(45deg, rgba(255,110,110,.10) 0 5px, transparent 5px 10px);
  border-left: 1px dashed rgba(255,110,110,.38); opacity: 0;
  transition: opacity var(--d2) var(--e-io); pointer-events: none; }
.gzone.on { opacity: 1; }
.gzone span { position: absolute; top: 12%; right: 15px; font-size: 9px; color: #FF9B9B;
  writing-mode: vertical-rl; letter-spacing: 2px; }
```

* Handle visual: **9 × 28**, radius **5px**. Hit box: **48 × 48** (`--hit`), pinned `right:0`,
  `top:44%`, `justify-content:flex-end` + `padding-right:12px` so the 9px bar sits flush at
  the right edge instead of floating at 19.5px. (Explicit source comment says so.)
* `top` is overwritten inline on every render: `elPipWrap.style.top = (pipY - 24) + 'px'`
  (i.e. the inline `top` is the **centre minus 24**; `44%` of 844 = 371.36 and the initial
  `pipY = 371`, so it matches on load).
* `.pip.due` = accent-solid **solid fill** with the same geometry (no number, no widening),
  and cancels the glass border via `border-color: transparent`.
* The pip is the *only* permanent on-screen element; the legacy pip is a comparison artefact
  hidden by default (`opacity:0; pointer-events:none`).

---

## 3. Element tree (the capsule / 闪念 panel)

### 3.1 Live DOM inside `.phone#live` (verbatim order from lines 572–674)

```
.phone#live[data-theme on <html>]
├─ .app#liveApp                      ← rebuilt every render(), see §2.5
├─ .scrim#scrim                      (class "on" conditional on .stream.open)
├─ .gzone#gzone  > span              ("系统返回手势区 48dp"; class "on" via control-panel switch)
├─ .statusbar#liveStatus             (innerHTML set every render)
├─ .pipwrap.legacy#pipLegacy > i.pip.g                 ("legacy" always; ".on" via switch)
├─ .pipwrap#pipWrap > i.pip.g#pip                       (class "due" if pendingTodos()>0;
│                                                        class "pulse" transiently after drop())
├─ .peek.g#peek > span.k "已存下" + span#peekText        (class "on" transient 1200ms)
├─ .slot.g#slot                                          (class "open" conditional)
│   └─ .row
│       ├─ button.ib#btnMic  > mic svg
│       ├─ input#slotInput   (placeholder "想点什么… 回车存下")
│       └─ button.ib.round#btnDrop > check svg (white stroke)
├─ .editbar.g#editbar                                    (class "open" conditional)
│   ├─ .meta
│   │   ├─ span#editWhen      ("刚刚" | "<when> · 提醒 <remind>")
│   │   ├─ span.src#editSrc   ("光标已在末尾，可直接追加")
│   │   └─ button.mic#editMic > mic svg
│   ├─ textarea#editInput (rows="2")
│   ├─ .tags#editTags         ← innerHTML = editTagsHtml(editTagSel)
│   └─ .acts
│       ├─ button.actbtn#btnRemind        (clock svg) 提醒
│       ├─ button.actbtn#btnDone          (circle-check svg) 完成
│       ├─ button.actbtn.danger#btnDelete (trash svg) 删除
│       └─ button.actbtn.primary#btnSaveEdit (white check svg) 保存
├─ .toast.g#toast                                        (class "on" conditional)
│   ├─ span.m#toastMsg
│   └─ button.undo#toastUndo  "撤销"                     (class "hide" when no undo)
├─ .menu.g#menu               ← innerHTML rebuilt per open
├─ .stream.g#stream                                     (class "open" conditional)
│   ├─ .head
│   │   ├─ .srchrow
│   │   │   ├─ label.srch#srchWrap                      (class "filled" when query != '')
│   │   │   │   ├─ svg (magnifier, stroke-width 1.9)
│   │   │   │   ├─ input#searchInput                    (placeholder 搜索闪念… | 搜索剪贴板…)
│   │   │   │   └─ button.clr#searchClear  "✕"          (display:none unless .srch.filled)
│   │   │   ├─ span.n#streamCount                       ("N 条 · 今天 M" | "N 条")
│   │   │   └─ button.x#streamClose  "✕"
│   │   ├─ .ptabs#streamTabs[data-i="0"|"1"]
│   │   │   ├─ i.tabind                                  (left animates via [data-i])
│   │   │   ├─ button[data-pt="stream"]  闪念            (class "on" when ptab=='stream')
│   │   │   └─ button[data-pt="clip"]    剪贴板          (class "on" when ptab=='clip')
│   │   └─ .chips#streamChips                           (display:flex|none per tab)
│   │       ├─ span.chip.sel?[data-f=""]   全部
│   │       └─ span.chip.sel?[data-f="<tag>"] > i.dot + text     ×5 (工作/想法/待办/灵感/设计)
│   ├─ .scroll#streamScroll                             (className = 'scroll' + [' flat'] + tabinCls)
│   │   └─ [stream tab]  .grp × 3 (only non-empty ones)
│   │        ├─ .glabel   今天 | 昨天 | 更早
│   │        ├─ .vline
│   │        └─ .item.fresh|.mid|.old[.star][.done][.flash][.hidden]
│   │             ├─ .node
│   │             ├─ .when
│   │             │    ├─ span  "<when>"
│   │             │    └─ span.remind[optional] > clock svg + "<remind>"
│   │             └─ .box[data-open="<id>"]
│   │                  ├─ .body      "<text>"
│   │                  ├─ .thumb     [optional, when img:true]
│   │                  ├─ .append    [optional] "＋ <append>"
│   │                  ├─ .foot      [optional, when (from || tags)]
│   │                  │    ├─ span.chip.mini.ghost   [optional, when src=='stash'] "<from|暂存>"
│   │                  │    └─ span.chip.mini (cursor:default) > i.dot + "<tag>"  × N
│   │                  └─ .acts
│   │                       ├─ button[data-act="done"|"star"][data-id][.on]  (primary state action)
│   │                       ├─ button[data-act="copy"][data-id]
│   │                       ├─ span.sp
│   │                       └─ button[data-act="more"][data-id]
│   │     [clip tab]  .item.fresh|.mid|.old[.hidden]  style="padding-left:0"
│   │             └─ .box                (no .node, no data-open, no .foot, no .append)
│   │                  ├─ .when
│   │                  │    ├─ span  "<when>"
│   │                  │    └─ span.kind  "链接"|"文本"|"图片"      ← NOTE: no CSS rule exists
│   │                  ├─ .body      "<text>"
│   │                  ├─ .thumb     [optional, when img:true]
│   │                  └─ .acts
│   │                       ├─ button[data-act="copyc"][data-id="<index>"]
│   │                       ├─ button[data-act="stashc"][data-id="<index>"]
│   │                       ├─ span.sp
│   │                       └─ button[data-act="morec"][data-id="<index>"]
│   │     [empty]  .empty
│   │             ├─ .big    "…"
│   │             ├─ .hint   "…"
│   │             ├─ button.go[data-empty="clearq"|"clearf"]
│   │             └─ .arrow  "→"          ← ONLY in the "no data at all" variant
│   ├─ .fab#fab                                         (class "off" when ptab=='clip';
│   │                                                    class "open" when composer is open)
│   │   └─ plus svg (stroke-width 2.4)
│   └─ .composer.g#composer                             (class "on" conditional)
│       ├─ .crow
│       │   ├─ button.ib#composerMic > mic svg
│       │   ├─ input#composerInput  (placeholder "想点什么… 回车存下")
│       │   └─ button.ok.dim#composerOk > check svg   ("dim" when input empty)
│       └─ .hint  "存下后直接出现在下面这一屏（今天的顶部），不用去别处找"
├─ .ime#ime                                             (class "on" via control-panel switch)
│   ├─ .bar > div ×4
│   ├─ .k#imeKeys > span ×26 (q w e r t y u i o p / a s d f g h j k l / z x c v b n m)
│   └─ .note  "模拟输入法 · 真机 overlay 窗读不到 WindowInsets.ime，须用 rememberOverlayImeBottomHeight()"
├─ .punch
└─ .homebar
```

### 3.2 Conditional classes — complete list

| Class | Element | Condition |
|---|---|---|
| `.open` | `.stream` | panel open (`openStream()` / `closeStream()` / `revealStream()`) |
| `.on` | `.scrim` | mirrors `.stream.open` at the end of `render()` |
| `.on` / `.off` (attr) | `<html>` `data-glass` | glass switch |
| `.due` | `i.pip` | `pendingTodos() > 0` |
| `.pulse` | `i.pip` | added for 900ms after `drop()` only (NOT after `addFromComposer`) |
| `.grabbing` | `.pipwrap` | during pointerdown→pointerup on the handle |
| `.on` | `.pipwrap.legacy` | legacy-handle switch |
| `.on` | `.gzone` | gesture-zone switch |
| `.on` | `.peek` | 1200ms after `drop()` |
| `.open` | `.slot` | long-press/`btnNew` opens it |
| `.open` | `.editbar` | card tap |
| `.on` | `.toast` | `showToast()` |
| `.hide` | `.toast .undo` | when `showToast` called without `actLabel` |
| `.on` | `.menu` | `openMenu()` |
| `.on` | `.fab` | composer is open |
| `.off` | `.fab` | `ptab === 'clip'` |
| `.on` | `.composer` | FAB toggled |
| `.dim` | `.composer .ok` | composer input empty/whitespace |
| `.filled` | `.srch` | `query !== ''` |
| `.on` | `.ptabs button` | matches `ptab` |
| `data-i="1"` | `.ptabs` | `ptab === 'clip'` → moves `.tabind` |
| `.sel` | `.chip` | active tag filter (`data-f` === `filter`), or "全部" when `filter` is null |
| `.flat` | `.scroll` | `ptab === 'clip'` (adds `padding-left:16px`) |
| `.tabin-r` / `.tabin-l` | `.scroll` | one-shot class applied for exactly one render on tab switch |
| `.fresh` | `.item` | `grp === '今天'` |
| `.mid` | `.item` | `grp === '昨天'` |
| `.old` | `.item` | `grp === '更早'` |
| `.star` | `.item` | `s.starred` |
| `.done` | `.item` | `s.done` |
| `.flash` | `.item` | `s.id === flashId` (set on add, cleared from state after 1500ms **without** re-render) |
| `.hidden` | `.item` | `!visible(s)` / `!clipVisible(s)` |
| `.mini` / `.ghost` | `.chip` | `.mini` for tags inside `.foot` and edit tags; `.ghost` for the `from` chip and the `＋` new-tag chip |
| `.on` | `.acts button` | `s.done` (done button) or `s.starred` (star button) |
| `.on` | `.ime` | fake-IME switch |
| `.on` | `.sw`, `.seg button` | control-panel switches (outside the phone) |

### 3.3 Full default render (regenerated every `render()`)

`render()` (lines 959–982) rebuilds **all of** `.app`, `.statusbar`, `.chips`, `.scroll`;
mutates `.pipwrap.style.top`, toggles `.pip.due`, writes `#todoN`, sets
`#streamScroll.className`, rewrites `#streamCount`, toggles `.srch.filled`, rewrites the
search placeholder, sets `#streamTabs.dataset.i`, toggles tab `.on`, toggles `.fab.off`,
force-closes composer + `.fab.open` when leaving the stream tab, and syncs the scrim.
`render()` never touches `.slot`, `.editbar`, `.peek`, `.toast`, or `.menu` visibility
(those are closed explicitly by whoever opens a competing surface).

---

## 4. CSS rules, verbatim by component

### 4.1 Panel container `.stream` — lines 333–338

```css
.stream { position: absolute; top: 0; right: 0; bottom: 0; width: 78%; z-index: 50;
  border-radius: var(--r-2xl) 0 0 var(--r-2xl); border-right: none;
  display: flex; flex-direction: column; opacity: 0; pointer-events: none; transform: translateX(100%);
  transition: opacity var(--d2) var(--e-io), transform var(--d3) var(--e-out); }
.stream.open { opacity: 1; pointer-events: auto; transform: translateX(0); }
.stream .head { padding: 40px 16px 0; flex: 0 0 auto; }
```

Derived: width **78% = 304.2px**, full height 844px, corners `26px 0 0 26px`, closed state is
`translateX(100%)` = 304.2px to the right (i.e. it really slides in from the right edge,
not a fade). Opacity eases with `--d2/--e-io`, transform with `--d3/--e-out` — two different
durations on the same element.

### 4.2 Header / search row — lines 341–360

```css
.stream .srchrow { display: flex; align-items: center; gap: 8px; }
.stream .srch { flex: 1; min-width: 0; display: flex; align-items: center; gap: 7px;
  height: 40px; padding: 0 12px; border-radius: var(--r-pill);
  border: 1px solid var(--btn-bd); background: var(--btn-bg);
  transition: border-color var(--d2) var(--e-io), background var(--d2) var(--e-io); }
.stream .srch:focus-within { border-color: color-mix(in srgb, var(--accent) 60%, transparent);
  background: color-mix(in srgb, var(--accent) 6%, transparent); }
.stream .srch svg { width: 16px; height: 16px; color: var(--sub); flex: 0 0 auto; }
.stream .srch input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent;
  font: inherit; font-size: var(--f-sm); color: var(--text); padding: 0; }
.stream .srch input::placeholder { color: var(--sub); }
.stream .srch .clr { width: 22px; height: 22px; flex: 0 0 auto; border: 0; border-radius: 50%;
  background: color-mix(in srgb, var(--text) 12%, transparent); color: var(--text);
  font-size: 10px; cursor: pointer; display: none; place-items: center; }
.stream .srch.filled .clr { display: grid; }
.stream .n { font-size: var(--f-tiny); color: var(--sub); white-space: nowrap; }
.stream .x { width: 40px; height: 40px; flex: 0 0 auto; border: 0; border-radius: 50%;
  background: transparent; display: grid; place-items: center; color: var(--sub);
  cursor: pointer; font-size: 13px; transition: background var(--d1) var(--e-io); }
.stream .x:hover { background: var(--hair); color: var(--text); }
```

Row is `padding:40px 16px 0` (40px = status-bar clearance), search pill **40px** tall, fully
round, gap 7px between icon/input/clear. Clear button is a **22×22** circle with
`color-mix(text 12%)` background and a **10px** `✕` glyph, `display:none` until `.filled`.
The close button is **40×40** with a **13px** `✕` glyph. `.n` is `10.5px` `--sub`.
The search icon SVG is `viewBox 0 0 24 24`, `stroke-width:1.9`, `stroke-linecap:round`,
`<circle cx="11" cy="11" r="7"/><path d="m16.5 16.5 4 4"/>`.

### 4.3 Tabs + sliding indicator — lines 362–375

```css
.stream .ptabs { position: relative; display: flex; gap: 4px; padding: 14px 0 0; }
.stream .ptabs .tabind { position: absolute; top: 14px; left: 0; height: 34px; width: calc(50% - 2px);
  border-radius: var(--r-sm); border: 1px solid var(--line);
  background: color-mix(in srgb, var(--text) 5%, transparent);
  transition: left var(--d3) var(--e-out); }
.stream .ptabs[data-i="1"] .tabind { left: calc(50% + 2px); }
.stream .ptabs button { position: relative; z-index: 1; flex: 1; height: 34px;
  border: 1px solid transparent; background: transparent; border-radius: var(--r-sm);
  font-size: var(--f-sm); color: var(--sub); cursor: pointer; transition: color var(--d2) var(--e-io); }
.stream .ptabs button.on { color: var(--text); font-weight: 650; }
@keyframes tabin-r { from { opacity: 0; transform: translateX(16px); } to { opacity: 1; transform: none; } }
@keyframes tabin-l { from { opacity: 0; transform: translateX(-16px); } to { opacity: 1; transform: none; } }
.stream .scroll.tabin-r { animation: tabin-r 280ms var(--e-out); }
.stream .scroll.tabin-l { animation: tabin-l 280ms var(--e-out); }
```

Indicator geometry: `top:14px` (i.e. flush with the tabs band), height **34px**, width
`calc(50% - 2px)` of the **head content width** (304.2 − 32 = 272.2 → 134.1px). With `gap:4px`
and two flex:1 buttons the two halves are 134.1px each; the indicator is exactly one button's
width, positioned at `left:0` or `left:calc(50% + 2px)`. It **only animates `left`** — no
width, no background change, no colour change.

### 4.4 Tag chips row — lines 377–383 and 274–288

```css
.stream .chips { display: flex; gap: 6px; padding: 12px 0 8px; overflow-x: auto; overflow-y: hidden;
  scrollbar-width: none;
  -webkit-mask-image: linear-gradient(to right, #000 86%, transparent);
  mask-image: linear-gradient(to right, #000 86%, transparent); }
.stream .chips::-webkit-scrollbar { display: none; }
```

```css
.chip { display: inline-flex; align-items: center; gap: 5px; height: 30px; padding: 0 11px;
  border-radius: var(--r-pill); font-size: var(--f-meta); color: var(--text); cursor: pointer;
  user-select: none; flex: 0 0 auto; white-space: nowrap;
  background: var(--g-fill); border: 1px solid var(--g-border);
  -webkit-backdrop-filter: blur(8px) saturate(1.3); backdrop-filter: blur(8px) saturate(1.3);
  box-shadow: inset 0 1px 0 var(--g-rim);
  transition: transform var(--d1) var(--e-out), border-color var(--d1) var(--e-io); }
.chip:hover { transform: translateY(-1px); }
.chip .dot { width: 5px; height: 5px; border-radius: 50%; }
.chip.sel { background: var(--accent-soft); border-color: color-mix(in srgb, var(--accent) 55%, transparent);
  font-weight: 650; box-shadow: inset 0 1px 0 rgba(255,255,255,.30),
    0 6px 16px -8px color-mix(in srgb, var(--accent) 60%, transparent); }
.chip.ghost { color: var(--sub); }
.chip.mini { height: 22px; padding: 0 8px; font-size: var(--f-tiny); gap: 4px; }
.chip.mini .dot { width: 4.5px; height: 4.5px; }
```

Every chip carries `flex: 0 0 auto` (never shrinks) and `white-space: nowrap`. Chips get
their **own** `blur(8px) saturate(1.3)` — a *different* blur from the panel's
`blur(var(--blur)) saturate(1.55)` and from `.item.fresh` (`…saturate(1.5)`) and
`.item.mid` (`blur(10px)`, no saturate).
Tag dot colours (JS `TAGS`, lines 756–759) are applied **only** as an inline
`background` on the `<i class="dot">` element — the selected state does **not** recolour to
the tag colour, it uses `--accent` (the footer comment wishes for "选中描边着色" but the CSS
does not do it). Tag colours: `工作 #5B8DF6`, `想法 #F0A93B`, `待办 #57B87A`,
`灵感 #C77DF0`, `设计 #E86E8C`. Unknown tag fallback: `#8C92A3`.

### 4.5 Scroll area — lines 385–387

```css
.stream .scroll { flex: 1; overflow-y: auto; padding: 2px 16px 40px 0; }
.stream .scroll.flat { padding-left: 16px; }
.stream .scroll::-webkit-scrollbar { width: 0; }
```

Note the asymmetry: on the 闪念 tab the left padding is 0 (because `.grp` supplies
`padding-left:62px` for its own timeline gutter) while the right padding is 16px; on the
剪贴板 tab `.flat` adds the missing `padding-left:16px`.

### 4.6 Group header + timeline spine `.grp` / `.glabel` / `.vline` — lines 389–394

```css
.grp { position: relative; padding-left: 62px; margin-top: 18px; }
.grp > .glabel { position: absolute; left: 0; top: 1px; width: 48px; text-align: right;
  font-size: var(--f-tiny); font-weight: 700; color: var(--sub); letter-spacing: 1px; }
.grp .vline { position: absolute; left: 54px; top: 8px; bottom: -14px; width: 1.5px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--text) 14%, transparent), transparent); }
.grp:last-child .vline { bottom: 46%; }
```

* Gutter: label right-aligned in a **48px** column at `left:0`; the spine is a **1.5px**
  gradient line at `left:54px`, starting at `top:8px`, running to `bottom:-14px`
  (i.e. 14px **into** the next group's 18px top margin, so consecutive groups join).
  The final group's spine stops early: `bottom:46%`.
* Label is 10.5px / weight 700 / `letter-spacing:1px` / `--sub`.

### 4.7 Timeline node + item — lines 396–398, 442, 489–493

```css
.item { position: relative; margin-bottom: 10px; }
.item .node { position: absolute; left: -8.5px; top: 17px; width: 7px; height: 7px; border-radius: 50%;
  background: color-mix(in srgb, var(--text) 26%, transparent); z-index: 2; }
.item.fresh .node { background: var(--accent); box-shadow: 0 0 0 4px var(--accent-soft); }
```

The node is a **7×7** dot, positioned at `left:-8.5px` relative to the `.item`, whose left
edge is at gutter 62px → dot spans x = 53.5…60.5, i.e. centred on the 54px spine (offset
3.5px to the right of the spine's left edge). `top:17px` lands it inside the `.when` row.
`.fresh` nodes get a **4px** `--accent-soft` halo ring. `.star .node` is solid
`--accent-solid` (§4.10).

```css
.item.hidden { display: none; }

.item.flash .box { animation: flashin 1.4s var(--e-out); }
@keyframes flashin {
  0% { box-shadow: 0 0 0 2px var(--accent), 0 0 0 8px var(--accent-soft); }
  100% { box-shadow: 0 0 0 0 transparent, 0 0 0 0 transparent; }
}
```

### 4.8 `.when` + `.remind` — lines 400–404

```css
.item .when { display: flex; align-items: center; gap: 7px; font-size: var(--f-tiny);
  color: var(--sub); margin: 0 0 6px 2px; letter-spacing: .3px; flex-wrap: wrap; }
.item .remind { display: inline-flex; align-items: center; gap: 3px; color: var(--accent-solid);
  font-weight: 650; }
.item .remind svg { width: 11px; height: 11px; }
```

The `.when` row is **above** the card box (outside `.box`), indented `2px`, 10.5px,
`letter-spacing:.3px`, `--sub`, 6px below. The clock icon in `.remind` is **11×11**
(`stroke-width:2`, `<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 1.8"/>`).

### 4.9 `.box` + `.body` + `.append` + `.foot` + `.thumb` — lines 405–422, 440–441

```css
.item .box { position: relative; border-radius: var(--r-lg); padding: 13px 14px 6px; cursor: pointer;
  border: 1px solid transparent; transition: transform var(--d1) var(--e-out); }
.item .box:hover { transform: translateX(-2px); }
.item.fresh .box { background: var(--g-fill); border-color: var(--g-border);
  -webkit-backdrop-filter: blur(var(--blur)) saturate(1.5); backdrop-filter: blur(var(--blur)) saturate(1.5);
  box-shadow: inset 0 1px 0 var(--g-rim), 0 14px 30px -18px var(--g-shadow); }
.item.mid .box { background: color-mix(in srgb, var(--app-elev) 66%, transparent);
  border-color: var(--hair); -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); }
.item.old .box { background: transparent; }
.item.old .box .body { color: var(--sub); }
.item .box .body { font-size: var(--f-sm); line-height: 1.6; color: var(--text); letter-spacing: .1px;
  overflow-wrap: anywhere; }
/* 完成 = 划掉 + 变暗，留在原位（时间流是「我记过什么」的历史，不移除） */
.item.done .box { opacity: .62; }
.item.done .box .body { text-decoration: line-through;
  text-decoration-color: color-mix(in srgb, var(--text) 45%, transparent); }
.item .box .append { border-top: 1px dashed var(--line); margin-top: 10px; padding-top: 9px;
  font-size: var(--f-meta); color: var(--sub); overflow-wrap: anywhere; }
.item .box .foot { display: flex; align-items: center; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
```

```css
.item .box .thumb { height: 66px; border-radius: var(--r-sm); margin-top: 10px;
  background: linear-gradient(150deg,#5F6AAE,#8E7CC9); box-shadow: inset 0 1px 0 rgba(255,255,255,.3); }
```

Card radius **18px**, padding **13px 14px 6px** (bottom 6px because `.acts` is a 48px-tall
icon row whose glyph already supplies visual bottom padding). Three tiers:
`fresh` = glass + border + inner top rim + `0 14px 30px -18px` shadow;
`mid` = 66 % `--app-elev` + `--hair` hairline + `blur(10px)`;
`old` = fully transparent, body turns `--sub`.
`done` = whole box at **opacity .62** plus a line-through whose stroke colour is `--text`
at 45 % alpha. `.append` is separated by a **1px dashed `--line`** top border with 10/9px
spacing, 11.5px `--sub`, prefixed by the full-width `＋` character.

### 4.10 `.acts` + `.act` icon buttons — lines 423–439

```css
.item .box .foot { display: flex; align-items: center; gap: 6px; margin-top: 10px; flex-wrap: wrap; }
/* 图标行：只放图标。48dp 目标 + 22px 字形 → 字形间隙 26px（接近 Material 密排的 24px）；
   margin-left 用 -(48−22)/2 = -13px，让第一个图标的左边缘和正文的 14px 对齐 */
.item .box .acts { display: flex; align-items: center; gap: 0; margin-top: 2px; margin-left: -13px; }
.item .box .acts .sp { flex: 1; }
/* 图标行：只放图标，不带文字。每个 48×48dp 命中区 */
.item .box .acts button { width: var(--hit); height: var(--hit); padding: 0; border: 0;
  border-radius: var(--r-sm); background: transparent; color: var(--text); cursor: pointer;
  display: grid; place-items: center; opacity: .84;
  transition: background var(--d1) var(--e-io), opacity var(--d1) var(--e-io); }
.item .box .acts button:hover { background: color-mix(in srgb, var(--text) 9%, transparent); opacity: 1; }
.item .box .acts button svg { width: 22px; height: 22px; }
.item .box .acts button.on { color: var(--accent-solid); opacity: 1; }
/* 星标条目：对齐真实代码 HistoryPanelColors（primaryVariant 35% 底 + primary 45% 描边） */
.item.star .box { border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: color-mix(in srgb, var(--accent) 9%, transparent); }
.item.star .node { background: var(--accent-solid); }
```

Key exact numbers: two 48×48 hit boxes with **gap 0** side by side (glyph pitch = 48px),
22×22 glyphs, `margin-left:-13px` to optically align the first glyph with the 14px body
padding, `margin-top:2px`, then `span.sp{flex:1}` pushes `⋮` to the right edge.
`.star` **replaces** the tier background with `color-mix(accent 9%)` and the border with
`color-mix(accent 45%)` — note this is a flat tint, so a starred `.fresh` item loses its
glass/backdrop-blur (the later rule wins on `background`).

### 4.11 `.fab` — lines 457–470

```css
.fab { position: absolute; right: 16px; bottom: 92px; width: 54px; height: 54px; z-index: 55;
  border-radius: 19px; display: grid; place-items: center; cursor: pointer; color: #fff;
  background: var(--accent-solid); border: 1px solid transparent;
  box-shadow: 0 14px 28px -12px color-mix(in srgb, var(--accent-solid) 85%, transparent),
    inset 0 1px 0 rgba(255,255,255,.28);
  transition: transform var(--d3) var(--e-out), opacity var(--d2) var(--e-io),
    bottom var(--d3) var(--e-out), background var(--d2) var(--e-io), color var(--d2) var(--e-io); }
.fab:hover { transform: translateY(-2px); }
.fab:active { transform: scale(.94); }
.fab svg { width: 22px; height: 22px; transition: transform var(--d3) var(--e-out); }
.fab.open { bottom: 158px; background: var(--g-fill); color: var(--text); border-color: var(--g-border);
  box-shadow: inset 0 1px 0 var(--g-rim), 0 10px 22px -14px var(--g-shadow); }
.fab.open svg { transform: rotate(45deg); }
.fab.off { opacity: 0; pointer-events: none; transform: scale(.8); }
```

54×54 with an unusual **19px** radius (a "squircle", not a circle). Sits at
`right:16px; bottom:92px`; when the composer opens it rises to `bottom:158px` (66px), loses
its accent fill for glass, and the `+` glyph rotates **45°** into an `×` (both over
`--d3/--e-out`). `.off` scales to .8 and fades (used on the 剪贴板 tab, which has no FAB).
Note `.fab` is a child of `.stream`, so `right:16px` is 16px from the panel's right edge.

### 4.12 `.composer` — lines 472–487

```css
.composer { position: absolute; left: 0; right: 0; bottom: 0; z-index: 56;
  padding: 12px 16px 28px; border-radius: var(--r-2xl) var(--r-2xl) 0 0;
  transform: translateY(112%); opacity: 0; pointer-events: none;
  transition: transform var(--d3) var(--e-out), opacity var(--d2) var(--e-io); }
.composer.on { transform: none; opacity: 1; pointer-events: auto; }
.composer .crow { display: flex; align-items: center; gap: 6px; }
.composer input { flex: 1; min-width: 0; height: var(--hit); padding: 0 15px; outline: 0;
  border-radius: var(--r-pill); border: 1px solid var(--btn-bd); background: var(--btn-bg);
  font: inherit; font-size: var(--f-body); color: var(--text); }
.composer input::placeholder { color: var(--sub); }
.composer .ok { width: var(--hit); height: var(--hit); flex: 0 0 auto; border: 0; border-radius: 50%;
  background: var(--accent-solid); color: #fff; display: grid; place-items: center; cursor: pointer;
  transition: background var(--d1) var(--e-io), color var(--d1) var(--e-io); }
.composer .ok svg { width: 19px; height: 19px; }
.composer .ok.dim { background: var(--btn-bg); color: var(--sub); }
.composer .hint { font-size: var(--f-tiny); color: var(--sub); margin: 9px 2px 0; letter-spacing: .2px; }
```

Closed state is `translateY(112%)` — deliberately more than 100 % so the drop shadow is
fully off-screen. Padding `12px 16px 28px` (28px bottom = home-bar clearance). Field is
48px tall, fully round, 15px inner padding; the send button is a 48px circle with a 19px
check; `.dim` swaps to `--btn-bg`/`--sub`. `mic` + `input` + `ok` are separated by 6px
(`gap:6px` on `.crow`). Composer mic reuses `.ib` (see §4.13).

### 4.13 `.slot` (呼出输入槽 / long-press recorder) — lines 233–249

```css
.slot { position: absolute; right: 10px; top: 349px; width: 72px; height: 52px;
  border-radius: var(--r-pill); overflow: hidden; z-index: 42;
  opacity: 0; pointer-events: none; transform: translateX(10px);
  transition: width var(--d3) var(--e-out), opacity var(--d2) var(--e-io),
    transform var(--d3) var(--e-out), top var(--d3) var(--e-out); }
.slot.open { width: 300px; opacity: 1; pointer-events: auto; transform: none; }
.slot .row { display: flex; align-items: center; gap: 4px; height: 50px; padding: 0 5px 0 6px; }
.slot input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent;
  font: inherit; font-size: var(--f-body); color: var(--text); padding: 0; letter-spacing: .1px; }
.slot input::placeholder { color: var(--sub); }
.ib { width: var(--hit); height: var(--hit); flex: 0 0 auto; border-radius: 50%; border: 0;
  background: transparent; color: var(--text); cursor: pointer; display: grid; place-items: center;
  opacity: .92; transition: opacity var(--d1) var(--e-io), background var(--d1) var(--e-io); }
.ib:hover { opacity: 1; background: var(--hair); }
.ib.round { background: var(--accent-solid); color: #fff; opacity: 1;
  box-shadow: 0 6px 16px -6px color-mix(in srgb, var(--accent-solid) 70%, transparent); }
.ib.round:hover { background: color-mix(in srgb, var(--accent-solid) 86%, #fff); }
.ib svg { width: 18px; height: 18px; }
```

**The slot morphs in width, 72px → 300px**, over `--d3/--e-out`, sliding in from
`translateX(10px)`. Height 52, row height 50 (so the 1px glass border on each side yields
exactly 50), radius pill, anchored `right:10px` with `top` set inline to `pipY - 26`
(so the 52px-tall slot is vertically centred on the handle). Two 48px buttons flank a
flexible input; inner padding `0 5px 0 6px`, gap 4px.

### 4.14 `.editbar` (in-place edit / append) — lines 252–272

```css
.editbar { position: absolute; right: 10px; top: 300px; width: 306px; border-radius: var(--r-xl);
  padding: 14px 14px 12px; z-index: 60; opacity: 0; pointer-events: none;
  transform: scale(.94) translateX(8px); transform-origin: right center;
  transition: opacity var(--d2) var(--e-io), transform var(--d3) var(--e-out), top var(--d3) var(--e-out); }
.editbar.open { opacity: 1; pointer-events: auto; transform: none; }
.editbar .meta { display: flex; align-items: center; gap: 8px;
  font-size: var(--f-tiny); color: var(--sub); letter-spacing: .2px; margin-bottom: 6px; }
/* 语音：三个能打字的面（输入槽 / 面板输入条 / 编辑条）都要有，否则不一致 */
.editbar .meta .src { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.editbar .meta .mic { width: 30px; height: 30px; flex: 0 0 auto; margin: -5px -6px -5px 0;
  border: 0; border-radius: 50%; background: transparent; color: var(--text); cursor: pointer;
  display: grid; place-items: center; opacity: .8;
  transition: background var(--d1) var(--e-io), opacity var(--d1) var(--e-io); }
.editbar .meta .mic:hover { background: var(--hair); opacity: 1; }
.editbar .meta .mic svg { width: 16px; height: 16px; }
.editbar textarea { width: 100%; border: 0; outline: 0; resize: none; background: transparent;
  font: inherit; font-size: var(--f-body); line-height: 1.6; color: var(--text); padding: 0;
  min-height: 48px; letter-spacing: .1px; }
.editbar .tags { display: flex; gap: 6px; margin-top: 10px; flex-wrap: wrap; align-items: center; }
.editbar .acts { display: flex; gap: 4px; margin-top: 8px; padding-top: 9px;
  border-top: 1px solid var(--hair); }
```

306px wide, radius 22px, glass, appearing with `scale(.94) translateX(8px)` from
`transform-origin: right center`. `top` is written inline and clamped to
`[72, 844-276=568]`. Note the tags row uses `.chip` (30px, non-mini) via `editTagsHtml`,
and the actions row uses `.actbtn` (4 flex-1 buttons with 4px gaps, separated by a 1px
`--hair` top border, 9px padding-top).

### 4.15 `.actbtn` (text+icon buttons inside `.editbar .acts`) — lines 290–300

```css
.actbtn { flex: 1; height: 38px; border-radius: var(--r-sm); border: 1px solid var(--btn-bd);
  background: var(--btn-bg); color: var(--text); font-size: var(--f-meta); cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; gap: 5px;
  transition: background var(--d1) var(--e-io), transform var(--d1) var(--e-out); }
.actbtn:hover { background: color-mix(in srgb, var(--text) 9%, transparent); }
.actbtn:active { transform: scale(.97); }
.actbtn svg { width: 13px; height: 13px; }
.actbtn.primary { background: var(--accent-solid); border-color: transparent; color: #fff;
  box-shadow: 0 8px 18px -8px color-mix(in srgb, var(--accent-solid) 75%, transparent); }
.actbtn.primary:hover { background: color-mix(in srgb, var(--accent-solid) 86%, #fff); }
.actbtn.danger { color: var(--danger); }
```

38px tall, 12px radius, 11.5px label, **13×13** icons, 5px icon/label gap. Four of them in a
306px-wide bar with 14px side padding and 4px gaps ⇒ each ≈ 63.5px.

### 4.16 `.toast` (undo bar) — lines 303–314

```css
.toast { position: absolute; left: 50%; bottom: 104px; transform: translate(-50%, 10px); z-index: 62;
  display: flex; align-items: center; gap: 12px; max-width: 342px;
  padding: 10px 10px 10px 16px; border-radius: var(--r-md); font-size: var(--f-meta); color: var(--text);
  opacity: 0; pointer-events: none;
  transition: opacity var(--d2) var(--e-io), transform var(--d3) var(--e-out); }
.toast.on { opacity: 1; transform: translate(-50%, 0); pointer-events: auto; }
.toast .m { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.toast .undo { flex: 0 0 auto; height: 30px; padding: 0 12px; border: 0; border-radius: var(--r-xs);
  background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent-solid);
  font-size: var(--f-meta); font-weight: 650; cursor: pointer; }
.toast .undo:hover { background: color-mix(in srgb, var(--accent) 26%, transparent); }
.toast .undo.hide { display: none; }
```

Centred horizontally on the phone (`left:50%` + `translate(-50%, …)`) — note the toast is a
sibling of `.stream`, i.e. it is **not** clipped to the panel and centres on the whole 390px
phone. Max width 342px, radius 14px, asymmetric padding 10/10/10/16. Message is one line with
ellipsis. Undo chip is 30px tall, 8px radius, `color-mix(accent 16%)` fill, `accent-solid`
650-weight label. `.hide` removes it entirely for toasts without an action.

### 4.17 `.menu` (⋮ overflow) — lines 317–328

```css
.menu { position: absolute; right: 16px; top: 300px; width: 188px; z-index: 66;
  padding: 6px; border-radius: var(--r-lg);
  opacity: 0; pointer-events: none; transform: scale(.94); transform-origin: right top;
  transition: opacity var(--d2) var(--e-io), transform var(--d2) var(--e-out); }
.menu.on { opacity: 1; pointer-events: auto; transform: none; }
.menu button { width: 100%; height: var(--hit); padding: 0 12px; border: 0; border-radius: var(--r-sm);
  background: transparent; color: var(--text); font-size: var(--f-sm); cursor: pointer;
  display: flex; align-items: center; gap: 10px; text-align: left; }
.menu button:hover { background: color-mix(in srgb, var(--text) 9%, transparent); }
.menu button svg { width: 17px; height: 17px; flex: 0 0 auto; }
.menu button.del { color: var(--danger); }
.menu .sep { height: 1px; background: var(--hair); margin: 5px 8px; }
```

188px wide, 18px radius, 6px padding, **every row 48px tall** (that is why the low-frequency
actions were moved out of the card), 17px icons with a 10px gap, label at 12.5px. Separator
is a 1px `--hair` line with 5px vertical / 8px horizontal margins.
Total menu height: **6 buttons + separator** (stash: 星标/取词/钉屏/分享/保存图片/删除) ⇒
`6 + 6×48 + (1+5+5) + 6 = 311px`; **5 buttons + separator** (clip) ⇒
`6 + 5×48 + 11 + 6 = 263px`. That is the `h` the runtime clamp `844 - h - 24` uses
(`elMenu.offsetHeight || 300`).

### 4.18 `.empty` (empty states) — lines 445–454

```css
.empty { padding: 46px 20px 20px; text-align: center; }
.empty .big { font-size: var(--f-sm); color: var(--text); font-weight: 650; margin-bottom: 8px; }
.empty .hint { font-size: var(--f-meta); color: var(--sub); line-height: 1.8; }
.empty .go { margin-top: 16px; height: 34px; padding: 0 16px; border-radius: var(--r-pill);
  border: 1px solid var(--btn-bd); background: var(--btn-bg); color: var(--text);
  font-size: var(--f-meta); cursor: pointer; }
.empty .go:hover { background: color-mix(in srgb, var(--text) 9%, transparent); }
.empty .arrow { margin-top: 30px; font-size: 22px; color: var(--accent-solid);
  animation: nudge 1.6s var(--e-io) infinite; }
@keyframes nudge { 0%,100% { transform: translateX(0); } 50% { transform: translateX(6px); } }
```

Only the "nothing at all" variant includes `.arrow` (a `→` glyph, 22px, accent-solid,
nudging 6px right forever). All three variants: `.big` (12.5px/650/`--text`),
`.hint` (11.5px/`--sub`/line-height 1.8), optional `.go` pill (34px tall, 16px side padding).

### 4.19 `.peek` (save preview next to the handle) — lines 222–228

```css
.peek { position: absolute; right: 58px; top: 44%; z-index: 44; max-width: 250px; padding: 9px 13px;
  border-radius: var(--r-md); font-size: var(--f-meta); line-height: 1.5; color: var(--text);
  opacity: 0; transform: translateX(8px); pointer-events: none; overflow-wrap: anywhere;
  transition: opacity var(--d2) var(--e-io), transform var(--d3) var(--e-out); }
.peek.on { opacity: 1; transform: none; }
.peek .k { display: block; font-size: 9.5px; color: var(--sub); letter-spacing: .4px; margin-bottom: 3px; }
```

`right:58px` (i.e. 48px hit box + 10px gutter), `top` set inline to `pipY - 24`,
max-width 250px, radius 14px, glass, label `已存下` at 9.5px then the truncated text.
Slides in 8px from the right while fading.

### 4.20 Clipboard-tab item

The clipboard tab reuses `.item` / `.box` / `.body` / `.acts` with **no dedicated class**.
The only differences come from markup and one inline style:

* root: `<div class="item {fresh|mid|old} {hidden}" style="padding-left:0">` — the inline
  `padding-left:0` is a no-op for `.item` (no padding is ever declared on `.item`) but is
  present verbatim; layout width comes from `.scroll.flat`'s 16px left padding.
* no `.node`, so no timeline dot.
* `.when` contains `<span class="kind">链接|文本|图片</span>`. **`.kind` has NO CSS rule in
  the file** — it inherits the `.when` style (10.5px, `--sub`, `letter-spacing:.3px`) and is
  visually indistinguishable from the timestamp except for the 7px flex gap.
* no `data-open` on `.box`, so tapping a clipboard card does nothing.
* actions are `copyc` / `stashc` / `morec` with `data-id` = the **array index**, not a
  string id.
* optional `.thumb` when `img:true`.

### 4.21 Fake IME (only needed to reproduce the keyboard-occlusion demo) — lines 496–505

```css
.ime { position: absolute; left: 0; right: 0; bottom: 0; height: 258px; z-index: 70;
  background: color-mix(in srgb, var(--app-bg) 84%, #6E7488); border-top: 1px solid var(--line);
  transform: translateY(100%); transition: transform var(--d3) var(--e-out); padding: 10px 6px 0; }
.ime.on { transform: translateY(0); }
.ime .bar { display: flex; gap: 6px; margin-bottom: 9px; padding: 0 6px; }
.ime .bar div { height: 26px; border-radius: var(--r-xs); flex: 1; background: var(--line); }
.ime .k { display: grid; grid-template-columns: repeat(10, 1fr); gap: 5px; padding: 0 4px; }
.ime .k span { height: 31px; border-radius: 7px; display: grid; place-items: center;
  font-size: var(--f-sm); color: var(--text); background: color-mix(in srgb, var(--text) 7%, transparent); }
.ime .note { text-align: center; font-size: 9.5px; color: var(--sub); margin-top: 8px; }
```

---

## 5. Animations

### 5.1 `@keyframes` — all six, verbatim

```css
@keyframes pippulse { 0%, 100% { transform: none; }
  35% { transform: translateX(-3px) scaleY(1.18); } }                            /* line 206 */

@keyframes tabin-r { from { opacity: 0; transform: translateX(16px); }
                     to   { opacity: 1; transform: none; } }                     /* line 372 */
@keyframes tabin-l { from { opacity: 0; transform: translateX(-16px); }
                     to   { opacity: 1; transform: none; } }                     /* line 373 */

@keyframes nudge { 0%,100% { transform: translateX(0); } 50% { transform: translateX(6px); } }
                                                                                 /* line 454 */
@keyframes flashin {
  0%   { box-shadow: 0 0 0 2px var(--accent), 0 0 0 8px var(--accent-soft); }
  100% { box-shadow: 0 0 0 0 transparent, 0 0 0 0 transparent; }
}                                                                                /* line 490 */
```

| Keyframes | Animates | Duration | Easing | Iteration | Applied by | Trigger |
|---|---|---|---|---|---|---|
| `pippulse` | `transform`: translateX −3px + scaleY 1.18 at 35 % | 900ms | `--e-out` `cubic-bezier(.22,1,.36,1)` | once | `.pip.pulse { animation: pippulse 900ms var(--e-out); }` | `drop()` only, after a forced reflow |
| `tabin-r` | opacity 0→1 + translateX 16px→0 (content enters from the right) | 280ms | `--e-out` | once | `.stream .scroll.tabin-r` | switching to the 剪贴板 tab |
| `tabin-l` | opacity 0→1 + translateX −16px→0 (enters from the left) | 280ms | `--e-out` | once | `.stream .scroll.tabin-l` | switching back to the 闪念 tab |
| `nudge` | translateX 0 → 6px → 0 | 1.6s | `--e-io` `cubic-bezier(.4,0,.2,1)` | **infinite** | `.empty .arrow` | empty state with zero items |
| `flashin` | two concentric `box-shadow` rings (2px `--accent` + 8px `--accent-soft`) collapse to zero | 1.4s | `--e-out` | once | `.item.flash .box` | item just added to the stream |

### 5.2 Every `transition` on the panel path (name → properties / duration / easing)

| Selector | Properties | Duration | Easing |
|---|---|---|---|
| `.scrim` | `opacity` | `--d2` 200ms | `--e-io` |
| `.pip` | `transform` / `background` / `box-shadow` | `--d2` 200ms each | `--e-out` / `--e-io` / `--e-io` |
| `.pipwrap.legacy` | `opacity` | `--d2` 200ms | `--e-io` |
| `.gzone` | `opacity` | `--d2` 200ms | `--e-io` |
| `.peek` | `opacity` / `transform` | `--d2` 200ms / `--d3` 280ms | `--e-io` / `--e-out` |
| `.slot` | `width` / `opacity` / `transform` / `top` | 280 / 200 / 280 / 280 ms | `--e-out` / `--e-io` / `--e-out` / `--e-out` |
| `.ib` | `opacity` / `background` | `--d1` 140ms | `--e-io` |
| `.editbar` | `opacity` / `transform` / `top` | 200 / 280 / 280 ms | `--e-io` / `--e-out` / `--e-out` |
| `.editbar .meta .mic` | `background` / `opacity` | `--d1` 140ms | `--e-io` |
| `.chip` | `transform` / `border-color` | `--d1` 140ms | `--e-out` / `--e-io` |
| `.actbtn` | `background` / `transform` | `--d1` 140ms | `--e-io` / `--e-out` |
| `.toast` | `opacity` / `transform` | 200 / 280 ms | `--e-io` / `--e-out` |
| `.menu` | `opacity` / `transform` | `--d2` 200ms | `--e-io` / `--e-out` (**note: `--e-out` on transform here, not `--d3`**) |
| `.stream` | `opacity` / `transform` | 200 / 280 ms | `--e-io` / `--e-out` |
| `.stream .srch` | `border-color` / `background` | `--d2` 200ms | `--e-io` |
| `.stream .x` | `background` | `--d1` 140ms | `--e-io` |
| `.stream .ptabs .tabind` | `left` | `--d3` 280ms | `--e-out` |
| `.stream .ptabs button` | `color` | `--d2` 200ms | `--e-io` |
| `.item .box` | `transform` | `--d1` 140ms | `--e-out` |
| `.item .box .acts button` | `background` / `opacity` | `--d1` 140ms | `--e-io` |
| `.fab` | `transform` / `opacity` / `bottom` / `background` / `color` | 280 / 200 / 280 / 200 / 200 ms | `--e-out` / `--e-io` / `--e-out` / `--e-io` / `--e-io` |
| `.fab svg` | `transform` | `--d3` 280ms | `--e-out` |
| `.composer` | `transform` / `opacity` | 280 / 200 ms | `--e-out` / `--e-io` |
| `.composer .ok` | `background` / `color` | `--d1` 140ms | `--e-io` |
| `.ime` | `transform` | `--d3` 280ms | `--e-out` |

Hover-only transitions (mouse scaffolding, drop in Compose): `.chip:hover{translateY(-1px)}`,
`.item .box:hover{translateX(-2px)}`, `.pipwrap:hover .pip{translateX(-2px)}`,
`.stream .x:hover`, `.actbtn:hover`, `.menu button:hover`, `.ib:hover`, `.empty .go:hover`,
`.toast .undo:hover`, `.item .box .acts button:hover`, `.fab:hover{translateY(-2px)}`,
`.actbtn:active{scale(.97)}`, `.fab:active{scale(.94)}`.

### 5.3 Transition-free, JS-driven motion

* Handle reveal (`revealStream`): inline `transition:'none'` + inline
  `opacity` / `transform` written per pointermove — a 1:1 finger-following map, **no easing**.
* Panel drag-to-close: same pattern, inline `transform: translateX(dx px)` and
  `opacity: max(0.15, 1 - dx/W)`, `transition:'none'`.
* Release either gesture and the inline styles are cleared, so the CSS transition
  (200/280ms) takes over for the snap open/closed.
* `top` on `.slot`, `.editbar`, `.menu`, `.peek` and `.pipwrap` is always written inline
  (`px`), which is itself animatable because `top` is in the transition list for
  `.slot` (280ms), `.editbar` (280ms) — `.menu` and `.peek` do **not** transition `top`,
  so they teleport vertically and only animate opacity/transform.

### 5.4 Animation restart technique

`drop()` restarts the pulse by forcing a reflow:

```js
elPip.classList.remove('pulse'); void elPip.offsetWidth; elPip.classList.add('pulse');
```

Compose equivalent: an `Animatable`/`animateFloatAsState` that is explicitly snapped back to
its start value before each new run — a plain state toggle will not restart a finished
animation.

### 5.5 Reduced motion

```css
@media (prefers-reduced-motion: reduce) {
  * { animation-duration: .001ms !important; transition-duration: .001ms !important; }
}
```

Compose equivalent: read `Settings.Global.ANIMATOR_DURATION_SCALE` (or the Compose
`MotionDurationScale` local) and collapse all durations.

### 5.6 Animation classes that persist after their "logical" end

`flashId` is nulled by `setTimeout(() => { flashId = null; }, 1500)` **without a re-render**
(lines 1027, 1041, 1297). So the `.flash` class stays on the DOM node after the 1.4s keyframe
ends; it only disappears on the next `render()`. A Compose port must decide deliberately
whether the flash state resets after 1.5s (visually identical) or on next recomposition.

---

## 6. Interactions (JS)

### 6.1 State variables — lines 784–794 (plus module-level extras)

| Variable | Initial value | Persisted? | Purpose |
|---|---|---|---|
| `filter` | `null` | no | active tag filter (`null` = 全部); reset to `null` on tab switch, on any add, and by the empty-state button |
| `query` | `''` | no | trimmed search text; shadows the input's own value |
| `ptab` | `'stream'` | **no** in the demo (comment explicitly says "记住上次停留的那一页" should persist on device) | which page: `'stream'` \| `'clip'` |
| `editingId` | `null` | no | id of the item open in `.editbar` |
| `flashId` | `null` | no | id that renders `.flash`; cleared after 1500ms |
| `pipY` | `371` | **no** (device should persist handle Y — see the demo's own note: `HistoryFloatService` 常驻窗 / 可拖 Y / 位置持久化) | handle centre-Y in px, clamped to `[96, 700]` |
| `seq` | `0` | no | uniquifier for `newId()` |
| `tabinCls` | `''` | no | one-render animation class (`' tabin-r'` / `' tabin-l'`) |
| `menuCtx` | `null` | no | `{kind:'stash', id}` \| `{kind:'clip', index}` |
| `editTagSel` | `[]` | no | working copy of the edited item's tags; re-seeded in `openEdit` |
| `toastTimer` | `null` | no | auto-hide handle |
| `undoFn` | `null` | no | the closure wired to the toast's 撤销 |
| `chipDragMoved` | `false` | no | one-shot click-swallow flag for the chips row |
| `panelDragMoved` | `false` | no | one-shot click-swallow flag for the panel |
| `STREAM` | 10 items (see §6.2) | no (device: `StashRepository` + `tags.json`; `done` must live in its **own** file) | 闪念 entries, mutable array |
| `CLIP_ITEMS` | 4 items | no | 剪贴板 entries |

Nothing is written to `localStorage`/`sessionStorage` in this demo; "复位" is
`location.reload()`.

```js
const newId = () => 'n' + Date.now().toString(36) + '-' + (++seq);
const clampPipY = (v) => Math.max(96, Math.min(700, v));
const pendingTodos = () => STREAM.filter(s => (s.tags || []).includes('待办') && !s.done).length;
```

### 6.2 Seed data (must be reused as-is for visual parity)

`TAGS` (line 756):
`{n:'工作',c:'#5B8DF6'} {n:'想法',c:'#F0A93B'} {n:'待办',c:'#57B87A'} {n:'灵感',c:'#C77DF0'} {n:'设计',c:'#E86E8C'}`,
`COL = Object.fromEntries(TAGS.map(t => [t.n, t.c]))`.

`STREAM` (lines 762–776) — 10 entries, field order `id, src, text, tags, when, grp` plus
optional `append`, `remind`, `from`, `img`, `rich`, `starred`, `done`:

| id | src | text | tags | when | grp | extra |
|---|---|---|---|---|---|---|
| s1 | capsule | 把描边改成 1px，太粗会显廉价 | 设计 | 刚刚 | 今天 | `append:'改成 0.5px 试了，AMOLED 上反而糊；还是 1px + 降透明度'` |
| s2 | capsule | 记得周五前把方案初稿发给老王 | 工作 | 12 分钟前 | 今天 | `remind:'周五 09:00'` |
| s3 | capsule | 买咖啡豆 | 待办 | 今天 09:20 | 今天 | `remind:'18:30'` |
| s4 | stash | 截图：搜索面板玻璃参数 | 设计 | 今天 08:40 | 今天 | `from:'图片'`, `img:true` |
| s5 | capsule | 液态玻璃的价值不是好看，是让层级可读 | 想法 | 今天 08:05 | 今天 | `append:'推论：所以它必须「真模糊」，假模糊反而增加噪音'` |
| s6 | stash | 从取词面板暂存的整段正文（富文本） | 工作 | 昨天 18:10 | 昨天 | `from:'取词'`, `rich:true`（`rich` is **never read** by any renderer) |
| s7 | capsule | 会议纪要：Q3 主线是降低常驻内存 | 工作 | 昨天 17:40 | 昨天 | `starred:true`, `append:'补充：先量 baseline，再谈优化目标'` |
| s8 | capsule | 提词器的滚动速度要可调，不然读稿很累 | 想法, 设计 | 昨天 11:02 | 昨天 | — |
| s9 | capsule | 和设计确认圆角用 14 还是 16 | 设计 | 周三 | 更早 | — |
| s10 | stash | LocalFrostedGlassBackdrop 的 tintColor 取 0x66F5F5F7 | （空） | 周三 | 更早 | `from:'剪贴板'` |

`CLIP_ITEMS` (lines 777–782) — 4 entries, fields `t, when, grp, kind, tags, [img]`:

| # | t | when | grp | kind | tags | extra |
|---|---|---|---|---|---|---|
| 0 | https://developer.android.com/develop/ui/compose | 2 分钟前 | 今天 | 链接 | （空） | — |
| 1 | LocalFrostedGlassBackdrop 的 tintColor 取 0x66F5F5F7 | 今天 11:02 | 今天 | 文本 | 设计 | — |
| 2 | 截图：通知滤盒的过滤规则面板 | 昨天 | 昨天 | 图片 | 设计 | `img:true` |
| 3 | ClipboardMonitorForegroundService 走 Shizuku 后台监听 | 周一 | 更早 | 文本 | （空） | — |

Derived initial UI state: 10 items shown; groups 今天 (5) / 昨天 (3) / 更早 (2); count label
`10 条 · 今天 5`; `pendingTodos()` = 1 (only `s3` has the 待办 tag and is not done) → the pip
renders `.due`.

### 6.3 `render()` — the single source of truth (lines 959–982)

```js
function render() {
  elApp.innerHTML = appHtml();
  $('liveStatus').innerHTML = statusHtml();
  elPipWrap.style.top = (pipY - 24) + 'px';
  elPip.classList.toggle('due', pendingTodos() > 0);
  $('todoN').textContent = String(pendingTodos());

  elStreamChips.innerHTML = chipsHtml();
  elStreamChips.style.display = ptab === 'stream' ? 'flex' : 'none';
  elStreamScroll.className = 'scroll' + (ptab === 'stream' ? '' : ' flat') + tabinCls;
  elStreamScroll.innerHTML = streamHtml();
  const n = ptab === 'stream' ? STREAM.filter(visible).length : CLIP_ITEMS.filter(clipVisible).length;
  const todayN = STREAM.filter(s => s.grp === '今天' && visible(s)).length;
  $('streamCount').textContent = (ptab === 'stream' && !query && !filter)
    ? `${n} 条 · 今天 ${todayN}` : `${n} 条`;
  $('srchWrap').classList.toggle('filled', !!query);
  $('searchInput').placeholder = ptab === 'stream' ? '搜索闪念…' : '搜索剪贴板…';
  $('streamTabs').dataset.i = ptab === 'stream' ? '0' : '1';
  [...$('streamTabs').children].forEach(b => b.classList.toggle('on', b.dataset.pt === ptab));
  const canAdd = ptab === 'stream';
  $('fab').classList.toggle('off', !canAdd);
  if (!canAdd) { $('composer').classList.remove('on'); $('fab').classList.remove('open'); }
  elScrim.classList.toggle('on', elStream.classList.contains('open'));
}
```

Three facts a Compose port must reproduce:

1. `render()` is a **full rebuild** of `.app`, `.statusbar`, `.chips`, `.scroll` contents.
   Replacing `innerHTML` on the scroller normally resets `scrollTop` to 0, so every filter /
   search / add / save / star / done action sends the timeline back to the top. Decide
   explicitly whether to keep that (it is what the demo does) or preserve the scroll offset.
2. `render()` never touches `.slot` / `.editbar` / `.peek` / `.toast` / `.menu` visibility —
   the only exception is the `canAdd` branch that force-closes the composer.
3. The count label has two formats: `"${n} 条 · 今天 ${todayN}"` only when
   `ptab === 'stream' && !query && !filter`; otherwise `"${n} 条"`.

### 6.4 Filtering predicates (lines 837–852)

```js
const matchQ = (s) => {
  if (!query) return true;
  const q = query.toLowerCase();
  return (s.text + ' ' + (s.append || '') + ' ' + (s.tags || []).join(' ')).toLowerCase().includes(q);
};
const matchClip = (s) => {
  if (!query) return true;
  return (s.t + ' ' + (s.kind || '')).toLowerCase().includes(query.toLowerCase());
};
/* 闪念与暂存已合并成一个流：不再有范围过滤，所有条目一律显示 */
const visible = (s) => query ? matchQ(s) : (!filter || (s.tags || []).includes(filter));
const clipVisible = (s) => query ? matchClip(s) : (!filter || (s.tags || []).includes(filter));
const isTodo = (s) => (s.tags || []).includes('待办') || !!s.done;
```

**Search ignores the tag filter** (`query ? matchQ(s) : …`) — this is stated in the UI copy.
Search matches text + append + joined tag names, case-insensitively. Clipboard search matches
text + kind only (not tags, not `when`).
`isTodo` keeps the done button visible on a completed item even after its 待办 tag is removed
("否则没法取消完成").
Group headers are dropped when *every* item of that group is filtered out
(`if (!items.some(visible)) return '';`) while hidden items stay in the DOM with
`.hidden{display:none}`.

### 6.5 The handle (`.pipwrap`) — the gesture state machine (lines 1178–1219)

Locals: `lp` (long-press timer), `longFired`, `drag`, `swallow`, const `TH = 56`.

| Event | Behaviour |
|---|---|
| `pointerdown` | `longFired=false; drag={x0:ev.clientX, y0:ev.clientY, pip0:pipY, axis:null, moved:false}`; `setPointerCapture`; add `.grabbing`; **start 400ms long-press timer** |
| long-press fires (400ms) | `longFired=true; cancelRevealStream(); openSlot();` |
| `pointermove`, axis undecided | return while `Math.abs(dx) < 8 && Math.abs(dy) < 8` |
| axis decided | `drag.axis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y'; drag.moved = true; clearTimeout(lp);` → long-press is cancelled |
| move, `axis==='x'` | `if (dx < -6) revealStream(dx);` (only inward/left drags reveal) |
| move, `axis==='y'` | `pipY = clampPipY(drag.pip0 + dy); elPipWrap.style.top = (pipY - 24) + 'px';` |
| `pointerup` | `finish`: clear timer, remove `.grabbing`; if `drag.moved`: `if (drag.axis==='x') { if (dx < -TH) openStream(); else cancelRevealStream(); }` and set `swallow = true`; `drag = null` |
| `pointercancel` | clear timer, `cancelRevealStream()`, remove `.grabbing`, `drag = null` |
| `click` | `if (longFired || swallow) { longFired = false; swallow = false; return; } openStream();` — **no tab change**, so the panel reopens on the last-used page |

Exact JS to preserve:

```js
const TH = 56;
lp = setTimeout(() => { longFired = true; cancelRevealStream(); openSlot(); }, 400);
if (drag.axis === 'x') { if (dx < -6) revealStream(dx); }
if (drag.axis === 'x') { if (dx < -TH) openStream(); else cancelRevealStream(); }
```

Thresholds: **8px** axis-lock dead zone, **−6px** before the panel starts following, **−56px**
release threshold to commit open, **400ms** long-press (the source comment insists on
`ViewConfiguration.getLongPressTimeout()` on device).

Reveal mapping (`revealStream`, lines 1059–1065) — no easing, linear on finger position:

```js
function revealStream(dx) {
  const p = Math.max(0, Math.min(1, -dx / 130));
  elStream.style.transition = 'none';
  elStream.style.opacity = String(p);
  elStream.style.transform = `translateX(${((1 - p) * 100).toFixed(2)}%)`;
  elStream.classList.add('open');
}
```

⇒ full reveal needs a **130px** inward drag; opacity and the `%` translation are the same
normalised value `p`.

### 6.6 Panel swipe-to-close (lines 1316–1354)

Locals: `panelDragMoved`, `d`, `const W = 390 * 0.78;` (= **304.2**).

| Step | Behaviour |
|---|---|
| `pointerdown` on `.stream` | ignore unless `.stream.open`; **ignore if the target is inside `.chips, .fab, .composer, .editbar, .menu`**; else `d={x0,y0,dx:0,axis:null}` |
| `pointermove` | dead zone `Math.abs(dx) < 10 && Math.abs(dy) < 10`; if `Math.abs(dy) > Math.abs(dx)` → `d = null` (vertical scroll wins, drag cancelled permanently); else `axis='x'` + `setPointerCapture` |
| move with `dx > 0` | `d.dx = dx; transition:'none'; transform: translateX(${dx}px); opacity: Math.max(0.15, 1 - dx / W)` — opacity floors at **0.15** |
| `pointerup`/`pointercancel` | `moved = d.axis==='x' && d.dx > 4`; if not moved, do nothing; else `panelDragMoved = true` and **`if (dx > W * 0.32 || dx > 120) closeStream(); else`** clear the inline styles |

Thresholds: **10px** dead zone, **4px** minimum to count as a drag, commit close at
`max(W*0.32 = 97.344px, 120px)` ⇒ effectively **120px** for a 390px phone (the `||` means the
120px branch wins whenever `W*0.32 < 120`, which it is).
`panelDragMoved` is consumed by the scroll-area click handler so the tap that ends a drag
does not also open the edit bar.

### 6.7 Tap targets inside the panel

**Tab buttons** (`#streamTabs`, lines 1251–1258):

```js
const next = b.dataset.pt;
if (next === ptab) return;
tabinCls = next === 'clip' ? ' tabin-r' : ' tabin-l';
ptab = next; filter = null; closeMenu(); render();
tabinCls = '';
```

⇒ switching tabs **resets the tag filter** but **keeps the query**; direction-aware entry
animation (right page enters from +16px, left page from −16px); clicking the active tab is a
no-op.

**Search input** (`input` event): `query = ev.target.value.trim(); render();` — renders on every
keystroke. **Clear button**: `ev.preventDefault(); query = ''; input.value = ''; render(); focus();`.

**Chips row click** (lines 1264–1268):

```js
if (chipDragMoved) { chipDragMoved = false; return; }
const t = ev.target.closest('[data-f]'); if (!t) return;
filter = t.dataset.f || null; render();
```

`data-f=""` on the 全部 chip maps to `null` via `|| null`.

**Scroll-area click** (lines 1269–1304), in priority order:

1. `if (panelDragMoved) { panelDragMoved = false; return; }`
2. `[data-empty]` → `query=''; searchInput.value=''; filter=null; render();` (both empty-state
   buttons share this handler regardless of which one was clicked)
3. `[data-act]` → dispatch on `a.dataset.act`:
   * `done` → `toggleDone(id)`
   * `copy` → `doCopy(s.text)`
   * `star` → flip `s.starred`, `render()`, toast `已加星标`/`已取消星标` **with undo**
   * `more` → `openMenu(a, {kind:'stash', id})`
   * `copyc` → `doCopy(CLIP_ITEMS[+id].t)`
   * `stashc` → **really creates an entry** (below)
   * `morec` → `openMenu(a, {kind:'clip', index:+id})`
4. `[data-open]` → `openEdit(b.dataset.open, b)`

`stashc` (lines 1286–1299) — the important one, because the source comment says a previous
version only showed a toast:

```js
const nid = newId();
STREAM.unshift({ id: nid, src: 'stash', from: '剪贴板', text: c.t,
  tags: (c.tags || []).slice(), when: '刚刚', grp: '今天' });
filter = null; query = ''; $('searchInput').value = '';
flashId = nid; render();
showToast('已暂存到闪念', '撤销', () => {
  STREAM = STREAM.filter(x => x.id !== nid); flashId = null; render(); hideToast();
});
setTimeout(() => { flashId = null; }, 1500);
```

Note it clears `filter`/`query` and switches nothing — the user must tap the 闪念 tab
themselves. There is **no** card long-press and **no** per-card swipe action; the only ways
into a card are the single tap and the three action buttons.

**Menu click**: `[data-mact]` → `runMenuAct(act)`.

**Menu dismissal**: `elStream`'s `pointerdown` listener in the **capture** phase:

```js
elStream.addEventListener('pointerdown', ev => {
  if (!ev.target.closest('[data-act="more"], [data-act="morec"]')) closeMenu();
}, true);
```

i.e. any pointerdown inside the panel that is not on the active `⋮` closes the menu first.

**Scrim click**: `closeStream(); closeEdit(); closeSlot(); hideToast();`

### 6.8 The `.act` icon actions, in full

`stashActions(s)` (lines 857–869) always emits `<span class="sp">` between the copy button and
`⋮`, and the *primary* button depends on `isTodo(s)`:

```js
const primary = isTodo(s)
  ? actB('done', s.id, s.done ? 'doneOn' : 'done',
      s.done ? '标记未完成' : '标记完成', s.done ? 'on' : '')
  : actB('star', s.id, s.starred ? 'starOn' : 'star',
      s.starred ? '取消星标' : '加星标', s.starred ? 'on' : '');
```

* 待办 (or already-done) item → primary = **完成/取消完成** (`done` icon = circle outline with
  a check; `doneOn` = filled circle with a **white** 2.1-stroke check), gets class `on` when done.
* every other item → primary = **星标** (`star` outline → `starOn` filled), `on` when starred.
* second slot is always **复制**.
* `title` attributes carry the labels: `标记完成` / `标记未完成` / `加星标` / `取消星标` / `复制` /
  `更多：进入取词 / 钉在屏幕 / 分享 / 保存图片 / 删除`.

`clipActions(s, i)` emits **复制** (`copyc`), **暂存到闪念** (`stashc`), `.sp`, **更多** (`morec`).

### 6.9 `toggleDone` — done + auto-cancel reminder (lines 1112–1123)

```js
const before = { done: !!s.done, remind: s.remind || null };
s.done = !s.done;
if (s.done) s.remind = null;                 /* 完成即取消提醒，否则到点还响 */
closeEdit(); closeMenu(); render();
showToast(s.done
  ? ('已完成' + (before.remind ? ' · 提醒已取消' : ''))
  : '已标记为未完成', '撤销', () => {
    s.done = before.done; s.remind = before.remind; render(); hideToast();
  });
```

Visual result: `.item.done .box { opacity:.62 }` + line-through on the body; the row **stays
in place** (explicit design note: the timeline is "what I have written down", not a task list).
The toast text gains ` · 提醒已取消` **only if a reminder existed**. Undo restores both flags.

### 6.10 `openEdit` / `saveEdit` / `deleteEntry`

`openEdit(id, anchorEl)` (lines 1072–1091):

1. `closeMenu()`; `editingId = id`; `editTagSel = (s.tags||[]).slice()`
2. `#editWhen = s.when + (s.remind ? ' · 提醒 ' + s.remind : '')`
3. `#editInput.value = s.text` (so the caret can be put at the end)
4. `#editTags.innerHTML = editTagsHtml(editTagSel)`
5. add `.open`
6. `top = Math.max(72, Math.min(844 - 276, top))` where `top` = the tapped box's
   `getBoundingClientRect().top` **minus the phone's rect top** (so the edit bar's top edge
   aligns with the card's top edge); the hard clamp is `[72, 568]`
7. `setTimeout(…, 150)` → `focus()` + `setSelectionRange(value.length, value.length)` ⇒
   **the caret is at the end, so typing appends** (the `.src` line literally reads
   `光标已在末尾，可直接追加`).

`saveEdit()` (lines 1093–1103): snapshot `{text, tags}`, `if (v) s.text = v`
(**an empty textarea keeps the old text**), `s.tags = editTagSel.slice()`, close, render,
toast `已保存` **with undo** that restores both fields.

`deleteEntry(id)` (lines 1104–1111): `splice(i,1)`, keep the removed object, toast
`` `已删除：${gone.text.slice(0, 10)}…` `` with undo that splices it back **at index `i`**.

### 6.11 The ⋮ menu (lines 1126–1170)

Rows by context:

| stash (`ctx.kind==='stash'`) | clip (`ctx.kind==='clip'`) |
|---|---|
| star — `加星标` / `取消星标` (label flips on `s.starred`) | — |
| pick — `进入取词` | pick — `进入取词` |
| pin — `钉在屏幕` | pin — `钉在屏幕` |
| share — `分享` | share — `分享` |
| save — `保存图片` | save — `保存图片` |
| `.sep` | `.sep` |
| del — `删除` (class `del`) | del — `删除` (class `del`) |

Positioning: `top = anchorRect.top - phoneRect.top - 8`, then
`elMenu.style.top = Math.max(60, Math.min(844 - h - 24, top)) + 'px'` with
`h = elMenu.offsetHeight || 300`. So the menu is 8px above the `⋮` button, clamped to
`[60, 844-h-24]`.

`runMenuAct(act)`:
* `del` + clip → `CLIP_ITEMS.splice(ctx.index,1)`, close, render, `showToast('已删除剪贴板记录')`
  (**no undo** — the only destructive action without a way back).
* `del` + stash → `deleteEntry(s.id)` (has undo).
* `star` → flip, close, render, toast `已加星标`/`已取消星标` with undo.
* `pick` / `pin` / `share` / `save` → close, then
  `` showToast(`${label}：真机走既有实现`) `` with label from
  `{pick:'进入取词', pin:'钉在屏幕', share:'分享', save:'保存图片'}` — i.e. a **stub**, no undo.

There is **no pin/side-toggle control anywhere in the demo**; "钉在屏幕" exists only as this
stubbed menu row.

### 6.12 The three text surfaces (slot / composer / editbar)

**`.slot` (long-press the handle, or `btnNew` in the control panel)**

```js
function openSlot() {
  closeEdit(); hideToast();
  closeStream();
  elSlot.classList.add('open');
  elSlot.style.top = (pipY - 26) + 'px';
  setTimeout(() => elSlotInput.focus(), 150);
}
function closeSlot() { elSlot.classList.remove('open'); elSlotInput.value = ''; }
```

* It is vertically centred on the handle (`pipY - 26` for a 52px-tall surface) and anchored
  `right:10px`, morphing 72px → 300px wide.
* Focus arrives after **150ms** (once the width transition is under way).
* No tags are exposed here, but `drop()` **does** consume `editTagSel`
  (`tags: editTagSel.slice()`), which is whatever the last-edited item's tags were.
* `Enter` (`keydown`) → `preventDefault(); drop();`.
* `btnDrop` click → `drop()`.

**`drop()` — the full "save from slot" beat (lines 1015–1028)**

```js
const v = elSlotInput.value.trim();
if (!v) { closeSlot(); return; }             // empty → silently close, NO toast
const id = newId();
STREAM.unshift({ id, src: 'capsule', text: v, tags: editTagSel.slice(), when: '刚刚', grp: '今天' });
closeSlot(); filter = null; query = ''; $('searchInput').value = '';
flashId = id; render();
elPip.classList.remove('pulse'); void elPip.offsetWidth; elPip.classList.add('pulse');
showPeek(v);
showToast('已存下', '撤销', () => {
  STREAM = STREAM.filter(x => x.id !== id); flashId = null; render(); hideToast();
});
setTimeout(() => { flashId = null; }, 1500);
```

Ordered timing of the feedback: input closes → filter/query cleared → item unshifted to the
top of 今天 and rendered with `.flash` (1.4s ring animation) → handle pulse restarted (900ms)
→ `showPeek` (1.2s) → `showToast('已存下','撤销')` (4.2s) → `flashId` cleared at 1.5s.

```js
function showPeek(text) {
  $('peekText').textContent = text.length > 18 ? text.slice(0, 18) + '…' : text;
  elPeek.style.top = (pipY - 24) + 'px';
  elPeek.classList.add('on');
  clearTimeout(showPeek._t);
  showPeek._t = setTimeout(() => elPeek.classList.remove('on'), 1200);
}
```

Truncation is **18 characters** (JS string units, no ellipsis if ≤18). The peek is positioned
at the same `pipY - 24` as the handle and slides 8px in from `right:58px`; the panel is closed
by then, so it is visible next to the handle.

**`.composer` (FAB inside the panel)**

```js
$('fab').addEventListener('click', () => {
  const on = $('composer').classList.toggle('on');
  $('fab').classList.toggle('open', on);
  syncComposerOk();
  if (on) setTimeout(() => $('composerInput').focus(), 160);
  else $('composerInput').blur();
});
function syncComposerOk() {
  $('composerOk').classList.toggle('dim', !$('composerInput').value.trim());
}
```

FAB rises 92px→158px, turns from accent to glass, its `+` rotates 45°, the composer slides up
from `translateY(112%)`, and focus lands at **160ms**.

`addFromComposer()` differs from `drop()` in three ways (lines 1029–1042): it toasts
`先写点什么` (no undo → 2.2s auto-hide) on empty input instead of silently closing, it stores
`tags: []`, and it does **neither** the handle pulse **nor** the `.peek`. It does clear
`filter`/`query` and set `flashId`, and re-focuses the input.

**`.editbar` footer buttons**

| Button | Handler |
|---|---|
| `提醒` (`btnRemind`) | `s.remind = s.remind ? null : '明天 09:00'`; `render()`; refresh `#editWhen`; toast `已设提醒 · 明天 09:00（推送，不占屏）` or `已取消提醒` — **no undo** |
| `完成` (`btnDone`) | `if (editingId) toggleDone(editingId)` |
| `删除` (`btnDelete`) | `if (editingId) deleteEntry(editingId)` |
| `保存` (`btnSaveEdit`) | `saveEdit()` |
| `textarea` keydown | `Enter` **without Shift** → `preventDefault(); saveEdit();` (Shift+Enter inserts a newline) |
| `.tags` click | `[data-et]`; `'__new'` → toast `新建标签：内联输入（≤ 2 字最佳）` and nothing else; otherwise toggle the tag in `editTagSel` and rewrite `#editTags.innerHTML` (**no full render**, so the textarea keeps focus/caret) |

### 6.13 Voice / mic — three identical stubs (lines 1223–1228)

```js
const micMsg = '语音为 P2：\n\n· 需 RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE + FGS 类型 microphone（当前 manifest 都没有）\n'
  + '· 按需申请、默认不申请；拒绝则隐藏入口\n· SpeechRecognizer 必须主线程使用，onResults 后 stopListening + destroy\n'
  + '· 每次录音重建会话，用 Idle/Requesting/Listening/Processing/Error 状态机防重入';
$('btnMic').addEventListener('click', () => alert(micMsg));
$('composerMic').addEventListener('click', () => alert(micMsg));
$('editMic').addEventListener('click', () => alert(micMsg));
```

Three mics exist deliberately — slot (`#btnMic`, 18px glyph), edit bar (`#editMic`, 30px
button / 16px glyph), composer (`#composerMic`, 18px glyph via `.ib`) — "三个能打字的面都要有，
否则不一致". Note the slot and composer use `.ib` (48px hit box, 18px glyph); the edit bar uses
its own 30×30 `.mic`. All three are stubs in the demo.

### 6.14 Toast + undo (lines 986–996)

```js
function showToast(msg, actLabel, act) {
  $('toastMsg').textContent = msg;
  undoFn = act || null;
  const b = $('toastUndo');
  b.textContent = actLabel || '撤销';
  b.classList.toggle('hide', !actLabel);
  elToast.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => elToast.classList.remove('on'), actLabel ? 4200 : 2200);
}
function hideToast() { clearTimeout(toastTimer); elToast.classList.remove('on'); undoFn = null; }
```

**Auto-hide is 4200ms when an undo action exists, 2200ms otherwise.** A new toast always
replaces the old one and resets the timer. `#toastUndo` click → `if (undoFn) undoFn()`.
Every undo closure calls `hideToast()` at the end, and every one of them re-renders.

Toast lifecycle per flow:
* **add (slot / composer / stashc)** → `已存下` / `已暂存到闪念`, undo removes the new entry.
* **save edit** → `已保存`, undo restores text+tags.
* **delete (stash)** → `已删除：<first 10 chars>…`, undo re-inserts at the old index.
* **delete (clip)** → `已删除剪贴板记录`, **no undo** (2.2s).
* **done** → `已完成` / `已完成 · 提醒已取消` / `已标记为未完成`, undo restores `done`+`remind`.
* **star (card or menu)** → `已加星标` / `已取消星标`, undo flips back.
* **remind** → `已设提醒 · 明天 09:00（推送，不占屏）` / `已取消提醒`, no undo.
* **copy** → `已复制` (+ ` · <after>` if provided; the demo never passes `after`), no undo.
* **menu stubs** → `进入取词：真机走既有实现` etc., no undo.
* **composer empty** → `先写点什么`, no undo.
* **new tag** → `新建标签：内联输入（≤ 2 字最佳）`, no undo.

### 6.15 `openStream` / `closeStream` / `cancelRevealStream` (lines 1047–1069)

```js
function openStream(nextTab) {
  closeSlot(); closeEdit(); closeMenu(); hideToast();
  if (nextTab) ptab = nextTab;
  elStream.style.transition = ''; elStream.style.opacity = ''; elStream.style.transform = '';
  elStream.classList.add('open'); render();
}
function closeStream() {
  elStream.style.transition = ''; elStream.style.opacity = ''; elStream.style.transform = '';
  elStream.classList.remove('open'); render();
  $('composer').classList.remove('on'); $('fab').classList.remove('open');
  closeMenu();
}
```

* `openStream()` with no argument **keeps** `ptab` ("点把手不改变页签"). Only the control-panel
  button `外部契约 · 剪贴板页` calls `openStream('clip')`.
* Both always clear the three inline style properties first, so a mid-gesture inline transform
  never blocks the CSS snap transition.
* `closeStream()` also collapses the composer and drops the FAB back to 92px.

### 6.16 Control panel (desktop scaffolding, lines 1400–1430)

| Control | Effect |
|---|---|
| `长按把手 = 记一条` (`#btnNew`) | `openSlot()` |
| `拖/点把手 = 面板` (`#btnStream`) | `openStream()` (keeps the current tab) |
| `外部契约 · 剪贴板页` (`#btnStreamExt`) | `openStream('clip')` |
| `复位` (`#btnReset`) | `location.reload()` |
| `主题` segment | `documentElement.setAttribute('data-theme', v)` + `galleryRender()` |
| `玻璃` switch (`.sw.on` by default) | `data-glass = 'on'|'off'` |
| `模糊半径` range `min=0 max=32 value=20` | sets inline `--blur = "<v>px"` and label `"<v>px"` |
| `输入法` switch | `elIme.classList.toggle('on')`; `elSlot.style.top = (on ? 150 : pipY - 26) + 'px'`; `elEdit.style.marginTop = on ? '-150px' : '0px'` |
| `返回手势区` switch | `.gzone.on` |
| `合并前对照` switch | `.pipwrap.legacy.on` |

The IME switch is the only place that models keyboard occlusion: with the fake IME up, the
slot is forced to `top:150px` and the edit bar is shifted by `marginTop:-150px`.

### 6.17 `showToast` in the ⋮-clip-delete path is the only non-undoable destructive action

Worth calling out in the port: `已删除剪贴板记录` has no 撤销, while stash deletion does.

---

## 7. Not obviously implementable in a normal Compose app

| # | Demo technique | Where | What it achieves visually | Compose route |
|---|---|---|---|---|
| 1 | `backdrop-filter: blur(20px) saturate(1.55)` | `.g`, applied to the panel, chips, slot, editbar, toast, menu, peek | True cross-window blur of the app **behind** the overlay | `Modifier.blur` blurs the node, not the backdrop. Needs a real backdrop: capture the window behind into a `GraphicsLayer`/`RenderNode` and blur it (`RenderEffect.createBlurEffect`), or Android 12+ `Window.setBackgroundBlurRadius`. The demo's own asset note says use `LocalFrostedGlassBackdrop` (and forbids miuix `RuntimeShader`, which crashes in the software-Canvas overlay path). Note **three different blur configs**: panel `blur(--blur) saturate(1.55)`, `.chip` `blur(8px) saturate(1.3)`, `.item.fresh` `blur(--blur) saturate(1.5)`, `.item.mid` `blur(10px)` |
| 2 | `saturate(1.55 / 1.5 / 1.3)` as part of the backdrop filter | `.g`, `.chip`, `.item.fresh` | pushes backdrop chroma | Compose: `ColorMatrix` saturation matrix applied to the blurred layer (`RenderEffect.createColorFilterEffect`) — `saturate()` has no direct equivalent |
| 3 | `.g::before` gloss | every glass surface | a top-left radial sheen plus a top 38% vertical sheen | `drawWithContent { drawRect(Brush.radialGradient(...)) }` with `Center(0.16w, -0.22h)` and `radius = 130% w`, then a vertical gradient of `--g-gloss` at 55% alpha → transparent at 38% |
| 4 | `.g::after` SVG `feTurbulence` noise, `opacity:.045`, `mix-blend-mode:overlay` | every glass surface | fine grain that kills banding | pre-baked noise `ImageBitmap` tiled and drawn with `BlendMode.Overlay` at 4.5 % — Compose has no runtime fractal-noise shader in the standard path |
| 5 | `inset 0 1px 0 <rim>` / `inset 0 -1px 0 <rim-dim>` box shadows | every glass surface, `.item.fresh .box`, `.fab` | 1px inner top highlight (the "glass rim") | no inset shadow in Compose: draw a 1px line/rect inside `drawBehind`/`drawWithContent`, or a hairline `BorderStroke` with a vertical gradient |
| 6 | `color-mix(in srgb, C p%, transparent)` — 20+ occurrences | everywhere (see grep in §1.6) | percentage-alpha tints of the current theme colour | `C.copy(alpha = C.alpha * p/100f)`. Do **not** substitute `C.copy(alpha = p/100f)`; for `--g-gloss` etc. the source alpha is already < 1 |
| 7 | `color-mix(in srgb, A p%, #fff)` / `#6E7488` | `.ib.round:hover`, `.actbtn.primary:hover`, `.ime` background | lighten/tint in premultiplied sRGB | compute the mixed RGB explicitly; `lerp` on non-premultiplied channels is not identical |
| 8 | `mask-image: linear-gradient(to right, #000 86%, transparent)` on the chips row | `.stream .chips` | fades the chip strip out over its last 14 % as it scrolls under the edge | `Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }` + `drawWithContent { drawContent(); drawRect(horizontalGradient, blendMode = BlendMode.DstIn) }` with the stop at 86 % |
| 9 | `overflow-x:auto` + `scrollbar-width:none` + `::-webkit-scrollbar{display:none}` and zero-width vertical scrollbar | `.chips`, `.scroll` | horizontal chip scrolling and vertical timeline scrolling with no visible scrollbar | `LazyRow`/`LazyColumn` (or `horizontalScroll`) with `ScrollBar` suppressed |
| 10 | `writing-mode: vertical-rl; letter-spacing:2px` | `.gzone span` | vertical Chinese label on the right edge debug zone | `Modifier.rotate(90f)` on a `Text`, or a vertical `TextStyle` layout |
| 11 | `font-variant-numeric: tabular-nums` on `body` | global | CJK digits stay column-aligned in `.when`, counts, timestamps | `TextStyle(fontFeatureSettings = "tnum")` |
| 12 | `@keyframes flashin` animating **two** `box-shadow` rings | `.item.flash .box` | a 2px accent outline plus an 8px soft halo expanding/fading on the just-added card | not a `Modifier` animation: draw both rings in `drawBehind` with an animated `Animatable` 1.4s / `--e-out`, or animate a `border` + a scaled halo layer |
| 13 | Animation retriggered by forced reflow `void elPip.offsetWidth` | `drop()` | the handle pulse replays on consecutive saves | snap the `Animatable` to its start value before each `animateTo` |
| 14 | `:hover` transforms (translateX(-2px) on cards, translateY(-1px) on chips, translateX(-2px) on the pip, scale on FAB/actbtn) plus 8 hover background fills | panel throughout | mouse-only affordances | drop them, or keep the `:active` scale(.97)/scale(.94) as `pressed` interaction states |
| 15 | `:focus-within` on `.srch` | search pill | accent border + 6 % accent fill while the field has focus | `interactionSource.collectIsFocusedAsState()` driving border/background |
| 16 | `opacity:.62` + `text-decoration:line-through` with `text-decoration-color: color-mix(text 45%)` | `.item.done` | struck-through body plus a dimmed card, in place | `TextDecoration.LineThrough` + `style.copy(textDecoration = …, color = …)`; Compose has no separate decoration-colour parameter, so the strike takes the text colour unless you draw the line yourself |
| 17 | `transform-origin: right center` / `right top` | `.editbar` (`scale(.94) translateX(8px)`), `.menu` (`scale(.94)`) | the popovers grow out of their anchor corner | `Modifier.graphicsLayer { transformOrigin = TransformOrigin(1f, .5f / 0f) }` |
| 18 | `transition: width 72px → 300px` on the pill slot | `.slot.open` | the recorder morphs from a stub to a full input bar | `animateDpAsState` on width + `Modifier.width()` |
| 19 | Different durations for `opacity` and `transform` on one element (200ms `--e-io` vs 280ms `--e-out`) | `.stream`, `.peek`, `.toast`, `.composer`, `.editbar`, `.fab` | fade and slide never share a curve | two separate `animate*AsState`/`Animatable` values driven by one boolean, with different specs |
| 20 | `transform: translateX(100%)` closed state on the panel | `.stream` | the panel genuinely slides in from beyond the right edge | `Modifier.offset { IntOffset(widthPx, 0) }` + `animateIntOffsetAsState`, not a fade |
| 21 | Inline per-frame style writes for 1:1 finger following (`revealStream`, panel drag) | handle drag, panel swipe | content tracks the finger with no easing, then snaps with CSS easing on release | `Modifier.draggable`/`pointerInput` writing raw offsets into a `mutableFloatStateOf`, with `animateTo` on release; must reproduce the exact mapping `p = clamp(-dx/130)`, `opacity = p`, `translateX = (1-p)*100%` |
| 22 | `scrollTop` reset caused by `innerHTML` replacement | every `render()` | the list jumps back to the top on any filter/search/add | decide explicitly; Compose `LazyListState` will *not* reset by default, so the demo's behaviour needs an explicit `scrollToItem(0)` if it should be reproduced |
| 23 | `@keyframes nudge` infinite on `.empty .arrow` | empty state | a looping 6px rightward nudge pointing at the FAB | `rememberInfiniteTransition` with `RepeatMode.Restart`, 1.6s, `--e-io`, 0→6→0 |
| 24 | `@keyframes tabin-r` / `-l` (direction-aware ±16px entry) applied via a one-shot class | tab switch | the list slides in from the side you came from | direction stored in state, then `AnimatedContent` with a custom `slideInHorizontally` per direction |
| 25 | Two hard-coded background image layers with **absolute px** radii on the wallpaper | `--page-bg` | soft blue/violet corner glows on the phone backdrop | `Brush.radialGradient(center = Offset(0.12w, -0.08h), radius = 1100f)` — the radii (1100/560, 900/500) are in px, far larger than the 390px phone, so they must **not** be normalised to the component size |
| 26 | `mask-image`/`backdrop-filter` on `.chip` inside a masked, horizontally-scrolling row | chips row | glass chips that also fade out under the mask | combining an offscreen `DstIn` mask with per-child backdrop blur is the hardest single composition in this spec; consider one shared blurred layer for the row |
| 27 | `@media (prefers-reduced-motion: reduce)` collapsing every duration | global | accessibility | `LocalMotionDurationScale` / animator duration scale |
| 28 | `-webkit-tap-highlight-color: transparent` | global | no grey tap flash | default Compose behaviour, but check `indication = null` on the 48px icon buttons (the demo explicitly has no ripple; it uses background-fill hover/opacity only) |
| 29 | `place-items:center` grid centering + `::before`/`::after` pseudo elements on `.bat` and `.g` | status bar, glass | exact centering and layered chrome | `Box(contentAlignment = Alignment.Center)` + extra drawn layers |
| 30 | `element.getBoundingClientRect()` anchor math for `.editbar` / `.menu` | `openEdit`, `openMenu` | the popover's top edge aligns with the tapped card / 8px above the `⋮` | `onGloballyPositioned` + `LayoutCoordinates` against the panel's own coordinates, then the same clamps `[72,568]` and `[60, 844-h-24]` |

Explicitly **absent** from this file (verified by search): `@media (prefers-color-scheme: dark)`,
`:has()`, `@container` / container queries, `@property`, `IntersectionObserver`,
`requestAnimationFrame`, `ResizeObserver`, `matchMedia`, `view-transition`, `background-clip:text`
/ gradient text, `position:sticky`, `scroll-snap`, and any scroll-driven animation.
The only `mask-image` is the chips fade; the only `mix-blend-mode` is the glass noise overlay;
the only `writing-mode` is the debug gesture-zone label.

---

## 8. Every user-visible Chinese string inside the capsule panel

### 8.1 Panel chrome

| String | Element |
|---|---|
| `搜索闪念…` | `#searchInput` placeholder (闪念 tab) |
| `搜索剪贴板…` | `#searchInput` placeholder (剪贴板 tab) |
| `✕` | `#searchClear` (clear) |
| `✕` | `#streamClose` (close) |
| `N 条 · 今天 M` | `#streamCount`, e.g. `10 条 · 今天 5` (only when stream tab, no query, no filter) |
| `N 条` | `#streamCount` otherwise |
| `闪念` | tab button `[data-pt="stream"]` |
| `剪贴板` | tab button `[data-pt="clip"]` |
| `全部` | chips row, `[data-f=""]` |
| `工作` `想法` `待办` `灵感` `设计` | chips row tag chips (`data-f`) |
| `今天` `昨天` `更早` | `.glabel` group headers |
| `＋` (full-width plus) | `.append` prefix; also the tag-new chip `[data-et="__new"]` |

### 8.2 Item content (seed data, all visible)

| String | Where |
|---|---|
| `刚刚`, `12 分钟前`, `今天 09:20`, `今天 08:40`, `今天 08:05`, `昨天 18:10`, `昨天 17:40`, `昨天 11:02`, `周三` | `.when > span` |
| `周五 09:00`, `18:30` | `.remind` |
| `把描边改成 1px，太粗会显廉价` | s1 `.body` |
| `改成 0.5px 试了，AMOLED 上反而糊；还是 1px + 降透明度` | s1 `.append` |
| `记得周五前把方案初稿发给老王` | s2 `.body` |
| `买咖啡豆` | s3 `.body` |
| `截图：搜索面板玻璃参数` | s4 `.body` |
| `液态玻璃的价值不是好看，是让层级可读` | s5 `.body` |
| `推论：所以它必须「真模糊」，假模糊反而增加噪音` | s5 `.append` |
| `从取词面板暂存的整段正文（富文本）` | s6 `.body` |
| `会议纪要：Q3 主线是降低常驻内存` | s7 `.body` |
| `补充：先量 baseline，再谈优化目标` | s7 `.append` |
| `提词器的滚动速度要可调，不然读稿很累` | s8 `.body` |
| `和设计确认圆角用 14 还是 16` | s9 `.body` |
| `LocalFrostedGlassBackdrop 的 tintColor 取 0x66F5F5F7` | s10 `.body` (also CLIP item 1) |
| `图片`, `取词`, `剪贴板` | `.foot > .chip.mini.ghost` (`from`) |
| `链接`, `文本`, `图片` | clipboard `.when > span.kind`, fallback `文本` |
| `https://developer.android.com/develop/ui/compose` | CLIP 0 `.body` |
| `截图：通知滤盒的过滤规则面板` | CLIP 2 `.body` |
| `ClipboardMonitorForegroundService 走 Shizuku 后台监听` | CLIP 3 `.body` |
| `2 分钟前`, `今天 11:02`, `昨天`, `周一` | clipboard `.when` |

### 8.3 Item action tooltips (`title` attributes)

`标记完成`, `标记未完成`, `加星标`, `取消星标`, `复制`, `暂存到闪念`,
`更多：进入取词 / 钉在屏幕 / 分享 / 保存图片 / 删除` (used for both `more` and `morec`).

### 8.4 Empty states (three variants)

| Variant | Strings |
|---|---|
| search miss | `.big` = `没有找到含「<query>」的条目`; `.hint` = `搜索会忽略标签筛选和范围，就近搜整个页签`; `.go` = `清除搜索` |
| filter miss | `.big` = `没有「<filter>」标签的条目`; `.hint` = `换个标签，或者看全部`; `.go` = `看全部` |
| nothing at all | `.big` = `还没有记过任何东西`; `.hint` = `长按右边那条把手，随时记一条<br>或者在右下角点「＋」`; `.arrow` = `→` |

### 8.5 Composer / slot / edit bar

| String | Element |
|---|---|
| `想点什么… 回车存下` | `#slotInput` and `#composerInput` placeholder |
| `存下后直接出现在下面这一屏（今天的顶部），不用去别处找` | `.composer .hint` |
| `已存下` | `.peek .k` |
| `刚刚` | `#editWhen` default |
| `光标已在末尾，可直接追加` | `#editSrc` |
| `提醒` `完成` `删除` `保存` | `#btnRemind` `#btnDone` `#btnDelete` `#btnSaveEdit` |
| `语音（P2）` | `#btnMic`, `#composerMic` titles |
| `语音输入（P2）` | `#editMic` title |
| `存下` | `#btnDrop`, `#composerOk` titles |
| `清除` | `#searchClear` title |
| `记一条（不用退出去长按把手）` | `#fab` title |
| `贴边把手 · 拖/点=面板，长按=直接记录` | `#pipWrap` title |
| `合并前的剪贴板把手（对照用）` | `#pipLegacy` title |
| `系统返回手势区 48dp` | `.gzone span` |
| `模拟输入法 · 真机 overlay 窗读不到 WindowInsets.ime，须用 rememberOverlayImeBottomHeight()` | `.ime .note` |
| `14:20` | `.statusbar .tm` |

### 8.6 ⋮ menu rows

`加星标`, `取消星标`, `进入取词`, `钉在屏幕`, `分享`, `保存图片`, `删除`.

### 8.7 Toasts (message + action)

| Message | Undo button | Auto-hide |
|---|---|---|
| `已存下` | `撤销` | 4200ms |
| `已保存` | `撤销` | 4200ms |
| `已删除：<first 10 chars>…` | `撤销` | 4200ms |
| `已完成` / `已完成 · 提醒已取消` / `已标记为未完成` | `撤销` | 4200ms |
| `已加星标` / `已取消星标` | `撤销` | 4200ms |
| `已暂存到闪念` | `撤销` | 4200ms |
| `已删除剪贴板记录` | — (`.hide`) | 2200ms |
| `已复制` (and `已复制 · <after>`) | — | 2200ms |
| `先写点什么` | — | 2200ms |
| `已设提醒 · 明天 09:00（推送，不占屏）` | — | 2200ms |
| `已取消提醒` | — | 2200ms |
| `新建标签：内联输入（≤ 2 字最佳）` | — | 2200ms |
| `进入取词：真机走既有实现` / `钉在屏幕：真机走既有实现` / `分享：真机走既有实现` / `保存图片：真机走既有实现` | — | 2200ms |

### 8.8 Third-party app behind (visible while the panel is closed / through the scrim)

`14:20` (status bar), `今日要闻`, `科技 · 设计 · 产品`, `液态玻璃之后，<br>系统 UI 会走向哪里`,
`2026-10-05 · 8 分钟阅读`.

### 8.9 Control-panel / gallery strings (desktop scaffolding, NOT part of the Android UI)

`闪念 · 贴边把手 + 两页签面板`, `01 可交互`, `02 八个关键状态`, `入口（就这三条）`,
`长按把手 = 记一条`, `拖/点把手 = 面板`, `外部契约 · 剪贴板页`, `复位`, `外观 / 演示`, `主题`,
`浅色`, `深色`, `玻璃`, `模糊半径`, `输入法`, `返回手势区`, `合并前对照`, `待办`,
`这一轮补的东西`, `与仓库既有资产对应`, the 9 gallery captions
(`01 屏上唯一常驻元素` … `09 就地编辑 · 可直接追加`), the footer and every `.note` paragraph.

---

## Other pages / sections in the same file

The file is a **single demo document**, not a multi-page app. Its structure:

1. **`.page-head`** (lines 554–566) — H1 `闪念 · 贴边把手 + 两页签面板`, a long `.sub`
   paragraph describing the merge of 暂存夹 → 闪念 and the three entry points, then a `.rule`.
   CSS at lines 97–106 (`.page-head`, `.rule`, `.sec`, `.secsub`, `.stage`).
2. **Section `01 可交互`** (lines 568–740) — the live, interactive phone (`#live`) documented
   above, plus `.ctl-panel` with four `.card`s: 入口（就这三条）/ 外观 / 演示 /
   这一轮补的东西 / 与仓库既有资产对应. `.ctl-panel` is `flex:1 1 400px; min-width:360px;
   max-width:600px`; `.stage` is `display:flex; gap:56px; align-items:flex-start; flex-wrap:wrap`.
   Card CSS at lines 510–535.
3. **Section `02 八个关键状态`** (lines 742–744) + `#gallery` — **nine** static clones
   (`.cell`), each a `.holder` (`258×540`, `overflow:hidden`) wrapping a full `.frame.phone`
   (`390×844`) scaled by an inline `transform: scale(.62); transform-origin: top left`.
   They share `staticFrame()` and re-use the real CSS classes, so they are frozen snapshots of
   specific states: 01 屏上唯一常驻元素 (with `.gzone.on`), 02 长按把手 → 记一条 + 存下预览
   (`slotStatic` + `.peek.on`), 03 往内拖 → 面板跟手拉出 (`translateX(40%); opacity:.55`),
   04 闪念页 · 搜索 + 标签, 05 图标行 + ⋮ 溢出菜单 (`.menu.on` at `top:186px`),
   06 待办完成 · 划掉 + 撤销 (toast at `bottom:96px`), 07 空状态, 08 剪贴板页,
   09 就地编辑 · 可直接追加 (`.editbar.open` at `top:214px`, stream at
   `translateX(26%); opacity:.5`). Caption CSS: `.cell .holder/.frame/.caption/.sub` (lines 537–543).
   The gallery honours the current `data-theme` (`staticFrame(inner, th)`).
4. **`<footer>`** (lines 746–750) — glass spec notes (真模糊 + 手写顶部光泽/内高光/内阴影/噪点
   0.045；禁 miuix RuntimeShader；标签颜色仅 5px 色点与选中描边着色), Android ≥48dp touch-target
   rule, and a pointer to `docs/capsule-tag-bar-analysis.md`.

All of §1–§8 above describes **only** the live panel and its transient surfaces; the control
panel and gallery are desktop scaffolding and need no Compose port — with two exceptions worth
keeping as **behaviour**, not layout: the **`data-glass=off` solid fallback**
(`isCrossWindowBlurEnabled == false` on device) and the **fake-IME occlusion model**
(`rememberOverlayImeBottomHeight()`, slot forced to `top:150px`).

---

## 9. Quick reference — the numbers most likely to drift

```
phone            390 × 844, radius 44, bezel 12px + 1px hairline
panel width      78% = 304.2px, radius 26px 0 0 26px, closed translateX(100%)
panel head        padding 40px 16px 0
search pill       40px tall, radius pill, gap 7px, padding 0 12px, clear 22×22, close 40×40
tabs              34px tall, gap 4px, padding-top 14px, indicator width calc(50% - 2px)
chips             30px tall (mini 22px), gap 6px, padding 12px 0 8px, dot 5px (mini 4.5px)
scroll            padding 2px 16px 40px 0; clip tab adds padding-left 16px
gutter            .grp padding-left 62px; .glabel 48px right-aligned; .vline left 54px, width 1.5px
node              7×7, left -8.5px, top 17px; .fresh halo 0 0 0 4px
item              margin-bottom 10px
box               radius 18px, padding 13px 14px 6px, border 1px
body              12.5px / 1.6 / letter-spacing .1px
acts              button 48×48, gap 0, glyph 22×22, margin-left -13px, margin-top 2px
fab               54×54, radius 19px, right 16, bottom 92 → 158 when open
composer          padding 12px 16px 28px, radius 26px 26px 0 0, closed translateY(112%)
slot              72 → 300 px wide, 52 tall, right 10, top pipY-26
editbar           306 wide, radius 22px, padding 14px 14px 12px, top clamp [72, 568]
menu              188 wide, radius 18px, padding 6px, rows 48px, top clamp [60, 844-h-24]
toast             bottom 104px, max-width 342, padding 10px 10px 10px 16px, centered on the phone
peek              right 58, max-width 250, text truncated at 18 chars, 1200ms
handle            9×28 radius 5, hit 48×48, right 0 padding-right 12, pipY clamp [96, 700]
durations         d1 140ms, d2 200ms, d3 280ms; toast 4200/2200ms; peek 1200ms; flash 1400ms;
                  pulse 900ms; focus delays 150ms (slot, editbar) / 160ms (composer);
                  flashId cleared at 1500ms; long-press 400ms
thresholds        axis lock 8px (handle) / 10px (panel); reveal start -6px; commit -56px (handle);
                  reveal span 130px; panel close max(W*0.32=97.34, 120)px
```
