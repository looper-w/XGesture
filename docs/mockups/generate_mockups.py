"""
闪念胶囊 × 收纳面板 · 最终 UI 形态（静态稿 v3）

本稿按真实代码还原收纳面板：
  宽度 = 窗口宽度的 50%（HistoryPanelUi.kt:65-71）
  圆角 14dp 只圆内侧两角（HistoryPanelScreen.kt:217-221）
  灰底列表 surface + 白卡 surfaceContainer（HistoryPanelColors.kt:22-40）
  标题「收纳面板」+ 图钉 + 搜索 + 双 tab 暂存夹/剪贴板（HistoryPanelScreen.kt:322-360）
  卡片 = 时间|取词·星标 → 内容 → 分隔线 → 操作行（HistoryEntryCardParts.kt:246-307）

交互稿见 ui_demo_capsule.html（HTML 是本设计的第一手可视参考）。
运行：python generate_mockups.py
"""

import os
from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont

SS = 2
SCREEN_W, SCREEN_H = 400, 860
COLS, ROWS = 3, 2
GAP_X, GAP_Y = 44, 48
MARGIN_X = 48
TITLE_H, CAP_H, FOOT_H = 116, 58, 48
PANEL_W = SCREEN_W // 2          # 半屏宽：与代码一致
PANEL_X = SCREEN_W - PANEL_W

TOTAL_W = MARGIN_X * 2 + COLS * SCREEN_W + (COLS - 1) * GAP_X
TOTAL_H = 30 + TITLE_H + ROWS * (SCREEN_H + CAP_H) + (ROWS - 1) * GAP_Y + FOOT_H

FONT_R, FONT_B = "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/msyhbd.ttc"

LIGHT = dict(app=(244, 245, 249), surface=(241, 242, 246), container=(255, 255, 255),
             txt=(26, 27, 32), sub=(122, 126, 138), line=(226, 228, 235), accent=(86, 104, 240))
DARK = dict(app=(11, 12, 16), surface=(18, 19, 23), container=(35, 37, 44),
            txt=(232, 234, 240), sub=(142, 146, 158), line=(40, 42, 50), accent=(136, 152, 255))
AMOLED = dict(app=(0, 0, 0), surface=(0, 0, 0), container=(21, 22, 27),
              txt=(236, 238, 244), sub=(138, 142, 154), line=(30, 31, 38), accent=(136, 152, 255))

TAG_COLORS = {"工作": (76, 141, 246), "想法": (240, 169, 59), "待办": (87, 184, 122),
              "灵感": (199, 125, 240), "设计": (232, 110, 140)}

STASH = [
    ("会议纪要：Q3 主线是降低常驻内存", ["工作"], "周一", True),
    ("记得周五前把方案初稿发给老王", ["工作"], "3 分钟前", False),
    ("把液态玻璃的描边改成 1px，太粗会显廉价", ["想法", "设计"], "今天 14:20", False),
    ("买咖啡豆", ["待办"], "昨天", False),
]


def S(v):
    return int(round(v * SS))


def f(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT_R, S(size))


def rrect(d, box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle([S(box[0]), S(box[1]), S(box[2]), S(box[3])],
                        radius=S(radius), fill=fill, outline=outline, width=S(width))


def rect(d, box, fill=None):
    d.rectangle([S(box[0]), S(box[1]), S(box[2]), S(box[3])], fill=fill)


def ell(d, box, fill=None, outline=None, width=1):
    d.ellipse([S(box[0]), S(box[1]), S(box[2]), S(box[3])], fill=fill, outline=outline, width=S(width))


def txt(d, xy, s, size, color, bold=False, anchor="la"):
    d.text((S(xy[0]), S(xy[1])), s, font=f(size, bold), fill=color, anchor=anchor)


def new_layer(size):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def vgrad(w, h, top=255, bottom=0):
    g = Image.new("L", (w, h))
    dd = ImageDraw.Draw(g)
    for y in range(h):
        dd.line([(0, y), (w, y)], fill=int(top + (bottom - top) * y / max(1, h - 1)))
    return g


def hgrad(w, h, left=0, right=255, color=(255, 255, 255)):
    g = Image.new("L", (w, h))
    dd = ImageDraw.Draw(g)
    for x in range(w):
        dd.line([(x, 0), (x, h)], fill=int(left + (right - left) * x / max(1, w - 1)))
    lay = Image.new("RGBA", (w, h), color + (255,))
    lay.putalpha(g)
    return lay


def shape_mask(w, h, radius, corners=(True, True, True, True)):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], radius=S(radius), fill=255)
    dd = ImageDraw.Draw(m)
    r = S(radius)
    if not corners[0]:
        dd.rectangle([0, 0, r, r], fill=255)
    if not corners[1]:
        dd.rectangle([w - r - 1, 0, w, r], fill=255)
    if not corners[2]:
        dd.rectangle([w - r - 1, h - r - 1, w, h], fill=255)
    if not corners[3]:
        dd.rectangle([0, h - r - 1, r, h], fill=255)
    return m


def glass(canvas, box, radius, blur=18, tint=(252, 253, 255, 120), sat=1.24, rim=0.85,
          inner=0.55, shadow=95, shadow_dx=1, shadow_dy=6, corners=(True, True, True, True)):
    x0, y0, x1, y1 = S(box[0]), S(box[1]), S(box[2]), S(box[3])
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    if shadow > 0:
        sh = new_layer(canvas.size)
        ImageDraw.Draw(sh).rounded_rectangle(
            [x0 + S(shadow_dx), y0 + S(shadow_dy), x1 + S(shadow_dx), y1 + S(shadow_dy)],
            radius=S(radius), fill=(0, 0, 0, shadow))
        canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(S(9))))
    region = canvas.crop((x0, y0, x1, y1)).convert("RGBA")
    base = ImageEnhance.Color(region.filter(ImageFilter.GaussianBlur(S(blur)))).enhance(sat)
    base = Image.alpha_composite(base, Image.new("RGBA", (w, h), tint))
    mask = shape_mask(w, h, radius, corners)
    rl = new_layer((w, h))
    ImageDraw.Draw(rl).rounded_rectangle([S(0.8), S(0.8), w - S(0.8), h - S(0.8)], radius=S(radius),
                                        outline=(255, 255, 255, 255), width=max(1, S(0.9)))
    rl = rl.filter(ImageFilter.GaussianBlur(S(0.5)))
    a = ImageChops.multiply(rl.getchannel("A"), vgrad(w, h, 255, int(255 * 0.16)))
    rl.putalpha(a.point(lambda v: int(v * rim)))
    base = Image.alpha_composite(base, rl)
    if inner > 0:
        ring = Image.new("L", (w, h), 0)
        ImageDraw.Draw(ring).rounded_rectangle([0, 0, w - 1, h - 1], radius=S(radius),
                                              outline=255, width=max(2, S(2.4)))
        ring = ring.filter(ImageFilter.GaussianBlur(S(2.6)))
        ring = ImageChops.multiply(ring, mask)
        ring = ImageChops.multiply(ring, vgrad(w, h, 0, 255))
        dark = new_layer((w, h))
        dark.paste((14, 16, 24, 255), (0, 0, w, h))
        dark.putalpha(ring.point(lambda v: int(v * inner)))
        base = Image.alpha_composite(base, dark)
    canvas.paste(base, (x0, y0), mask)


def chip(canvas, x, y, label, tag=None, selected=False, dark=False, h=28, mini=False,
         ghost=False, accent=(86, 104, 240), editing=False, caret=False):
    size = 10 if mini else 11.5
    tw = f(size, selected).getlength(label)
    dot = (5 if mini else 6) if tag else 0
    pad_l = 8 if mini else 11
    pad_r = 8 if mini else 11
    w = pad_l + (dot + 5 if dot else 0) + tw + pad_r + (7 if editing else 0)
    box = (x, y, x + w, y + h)
    tint = (255, 255, 255, 96 if not dark else 34)
    if selected:
        tint = (accent[0], accent[1], accent[2], 74 if not dark else 62)
    glass(canvas, box, radius=h / 2, blur=9 if not mini else 6, tint=tint, sat=1.18,
          rim=1.0 if selected else 0.62, inner=0.34, shadow=0 if mini else 24,
          shadow_dx=0, shadow_dy=2 if not mini else 0)
    dd = ImageDraw.Draw(canvas)
    tx = x + pad_l
    if dot:
        r = 2.5 if mini else 3
        ell(dd, (tx, y + h / 2 - r, tx + dot, y + h / 2 + r),
            fill=TAG_COLORS.get(tag, (150, 155, 168)))
        tx += dot + 5
    col = (240, 242, 248) if dark else (24, 25, 30)
    if ghost:
        col = (140, 144, 156) if dark else (110, 114, 128)
    txt(dd, (tx, y + h / 2), label, size, col, bold=selected, anchor="lm")
    if editing and caret:
        cx = tx + tw + 3
        rect(dd, (cx, y + 6, cx + 1.5, y + h - 6), fill=accent)
    if editing:
        txt(dd, (x + w - 7, y + h / 2), "×", 10, (150, 152, 164), anchor="rm")
    return w


def icon(d, cx, cy, kind, color, scale=1.0, filled=False):
    """细线图标：复用交互稿的语义（图钉/搜索/取词/星标/钉屏/复制/分享/更多）。"""
    w = 1.5 * scale
    if kind == "pin":
        d.polygon([(S(cx - 3), S(cy - 6)), (S(cx + 3), S(cy - 6)), (S(cx + 2), S(cy - 1)),
                   (S(cx + 5), S(cy + 2)), (S(cx + 5), S(cy + 4)), (S(cx - 5), S(cy + 4)),
                   (S(cx - 5), S(cy + 2)), (S(cx - 2), S(cy - 1))], outline=color, width=S(w))
        rect(d, (cx - 0.7, cy + 4, cx + 0.7, cy + 8), fill=color)
    elif kind == "search":
        d.ellipse([S(cx - 5), S(cy - 5), S(cx + 3), S(cy + 3)], outline=color, width=S(w))
        d.line([S(cx + 2.4), S(cy + 2.4), S(cx + 6), S(cy + 6)], fill=color, width=S(w))
    elif kind == "pick":
        for i, ln in enumerate((9, 6, 7)):
            d.line([S(cx - 6), S(cy - 4 + i * 4), S(cx - 6 + ln), S(cy - 4 + i * 4)], fill=color, width=S(w))
    elif kind == "star":
        pts = []
        import math
        for i in range(10):
            ang = -math.pi / 2 + i * math.pi / 5
            r = 6.0 if i % 2 == 0 else 2.7
            pts.append((S(cx + r * math.cos(ang) * scale), S(cy + r * math.sin(ang) * scale)))
        if filled:
            d.polygon(pts, fill=color)
        else:
            d.polygon(pts, outline=color, width=S(w * 0.9))
    elif kind == "screen":
        rrect(d, (cx - 6, cy - 5, cx + 6, cy + 3), 1.6, outline=color, width=w)
        d.line([S(cx - 3), S(cy + 6), S(cx + 3), S(cy + 6)], fill=color, width=S(w))
    elif kind == "copy":
        rrect(d, (cx - 2, cy - 2, cx + 6, cy + 6), 1.4, outline=color, width=w)
        d.rectangle([S(cx - 6), S(cy - 6), S(cx + 2), S(cy + 2)], outline=color, width=S(w))
    elif kind == "share":
        d.line([S(cx), S(cy + 5), S(cx), S(cy - 6)], fill=color, width=S(w))
        d.line([S(cx - 3), S(cy - 3), S(cx), S(cy - 6), S(cx + 3), S(cy - 3)], fill=color, width=S(w))
        d.line([S(cx - 6), S(cy + 2), S(cx - 6), S(cy + 6), S(cx + 6), S(cy + 6), S(cx + 6), S(cy + 2)],
               fill=color, width=S(w))
    elif kind == "more":
        for i in (-4, 0, 4):
            d.ellipse([S(cx - 1), S(cy + i - 1), S(cx + 1), S(cy + i + 1)], fill=color)


def status_bar(d, c):
    col = c["txt"]
    txt(d, (20, 15), "14:20", 11, col, bold=True, anchor="lm")
    x = SCREEN_W - 20
    for i in range(4):
        h = 3 + i * 2.2
        rect(d, (x - 25 + i * 4, 15 - h, x - 22 + i * 4, 15), fill=col)
    ell(d, (x - 8, 10, x - 1.5, 16), outline=col, width=1.1)
    rrect(d, (x, 10, x + 14, 19), 3, outline=col, width=1.1)


def backdrop_app(canvas, theme):
    c = theme
    d = ImageDraw.Draw(canvas)
    rect(d, (0, 0, SCREEN_W, SCREEN_H), fill=c["app"])
    band = new_layer(canvas.size)
    bd = ImageDraw.Draw(band)
    a = (62, 104, 224) if theme is LIGHT else ((42, 58, 160) if theme is DARK else (20, 22, 28))
    b = (148, 104, 212) if theme is LIGHT else ((90, 62, 168) if theme is DARK else (28, 30, 38))
    for y in range(S(150)):
        t = y / S(150)
        bd.line([(0, y), (canvas.size[0], y)],
                fill=(int(a[0] + (b[0] - a[0]) * t), int(a[1] + (b[1] - a[1]) * t),
                      int(a[2] + (b[2] - a[2]) * t), 255))
    canvas.alpha_composite(band)
    d = ImageDraw.Draw(canvas)
    txt(d, (22, 58), "今日要闻", 19, (255, 255, 255), bold=True, anchor="lm")
    txt(d, (22, 84), "科技 · 设计 · 产品", 11, (255, 255, 255, 185), anchor="lm")
    rect(d, (22, 104, 62, 107), fill=(255, 255, 255, 215))
    txt(d, (22, 178), "液态玻璃之后，", 20, c["txt"], bold=True, anchor="lm")
    txt(d, (22, 208), "系统 UI 会走向哪里", 20, c["txt"], bold=True, anchor="lm")
    txt(d, (22, 244), "2026-10-05 · 8 分钟阅读", 11, c["sub"], anchor="lm")
    y = 300
    for w in (0.94, 0.88, 0.96, 0.62, 0.9):
        rect(d, (22, y, 22 + (SCREEN_W - 44) * w, y + 9), fill=c["line"])
        y += 22
    y = 470
    block = new_layer(canvas.size)
    bd = ImageDraw.Draw(block)
    for i in range(S(120)):
        t = i / S(120)
        bd.line([(S(22), S(y) + i), (S(SCREEN_W - 22), S(y) + i)],
                fill=(int(126 + t * 58), int(134 + t * 22), int(186 + t * 38), 255))
    m = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([S(22), S(y), S(SCREEN_W - 22), S(y + 120)], radius=S(14), fill=255)
    canvas.paste(block, (0, 0), m)
    d = ImageDraw.Draw(canvas)
    y += 144
    for w in (0.92, 0.86, 0.9):
        rect(d, (22, y, 22 + (SCREEN_W - 44) * w, y + 9), fill=c["line"])
        y += 22


# ==================== 收纳面板（半屏，真实结构） ====================
CARD_H2, CARD_H3 = 138, 157
CARD_GAP = 8


def panel_card(canvas, theme, y, text, tags, when, starred, show_tags, dark):
    c = theme
    x0, x1 = PANEL_X + 12, SCREEN_W - 12
    lines = 3 if len(text) > 15 else 2
    h = CARD_H3 if lines == 3 else CARD_H2
    if show_tags:
        h += 26
    # 卡片底
    bg = c["container"]
    if starred:
        bg = tuple(int(c["accent"][i] * 0.35 + c["container"][i] * 0.65) for i in range(3))
        bg = tuple(int(bg[i] * 0.82 + (143, 122, 232)[i] * 0.18) for i in range(3))
    lay = new_layer(canvas.size)
    ImageDraw.Draw(lay).rounded_rectangle([S(x0), S(y), S(x1), S(y + h)], radius=S(16), fill=bg + (255,))
    canvas.alpha_composite(lay)
    d = ImageDraw.Draw(canvas)
    if starred:
        # 不透明混色，避免在 RGBA 上“写入”半透明像素造成挖洞
        edge = tuple(int(c["accent"][i] * 0.45 + bg[i] * 0.55) for i in range(3))
        rrect(d, (x0, y, x1, y + h), 16, outline=edge, width=1)
    # 顶行：时间 | 取词 · 星标
    txt(d, (x0 + 12, y + 16), when, 10.5, c["sub"], anchor="lm")
    icon(d, x1 - 40, y + 16, "pick", c["txt"], 0.95)
    icon(d, x1 - 20, y + 16, "star", c["accent"] if starred else c["txt"], 0.95, filled=starred)
    # 内容（最多 3 行）
    ty = y + 34
    limit = 13
    chunks = [text[i:i + limit] for i in range(0, len(text), limit)][:3]
    for ln in chunks:
        txt(d, (x0 + 12, ty), ln, 11.5, c["txt"], anchor="la")
        ty += 17
    # 标签小胶囊
    if show_tags:
        tx = x0 + 12
        for t in tags:
            tx += chip(canvas, tx, ty + 2, t, tag=t, mini=True, dark=dark) + 5
        ty += 26
    # 分隔线 + 操作行
    div = tuple(int(c["txt"][i] * 0.10 + c["container"][i] * 0.90) for i in range(3)) if dark else c["line"]
    rect(d, (x0 + 12, ty + 4, x1 - 12, ty + 5), fill=div)
    fy = ty + 22
    for i, k in enumerate(("screen", "copy", "share", "more")):
        icon(d, x0 + 26 + i * 26, fy, k, c["txt"], 0.9)
    return h + CARD_GAP


def draw_panel(canvas, theme, tags_on=True, full=True, top=0, dark=False, star=True):
    """画收纳面板：chrome（标题行 + tab + chip 行）+ 列表。full=False 时为紧凑态。"""
    c = theme
    x0, h = PANEL_X, SCREEN_H - top
    # 面板底：灰（surface）
    lay = new_layer(canvas.size)
    ImageDraw.Draw(lay).rectangle([S(x0), S(top), S(SCREEN_W), S(top + h)], fill=c["surface"] + (255,))
    m = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([S(x0), S(top), S(SCREEN_W), S(top + h)],
                                       radius=S(14), fill=255)
    ImageDraw.Draw(m).rectangle([S(x0 + 14), S(top), S(SCREEN_W), S(top + h)], fill=255)
    canvas.paste(lay, (0, 0), m)
    d = ImageDraw.Draw(canvas)

    # chrome：白（surfaceContainer）
    chrome_h = 40 + 34
    if full:
        chrome_h += 34 + (40 if tags_on else 0)
    else:
        chrome_h += 40 if tags_on else 0
    lay = new_layer(canvas.size)
    ImageDraw.Draw(lay).rectangle([S(x0), S(top), S(SCREEN_W), S(top + chrome_h)],
                                 fill=c["container"] + (255,))
    m = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([S(x0), S(top), S(SCREEN_W), S(top + chrome_h + 16)],
                                       radius=S(14), fill=255)
    ImageDraw.Draw(m).rectangle([S(x0 + 14), S(top), S(SCREEN_W), S(top + chrome_h + 16)], fill=255)
    ImageDraw.Draw(m).rectangle([S(x0), S(top + chrome_h), S(SCREEN_W), S(top + chrome_h + 16)], fill=0)
    canvas.paste(lay, (0, 0), m)
    d = ImageDraw.Draw(canvas)

    status_bar(d, c)
    # 标题行
    ty = top + 40 + 17
    txt(d, (x0 + 16, ty), "收纳面板", 13.5, c["txt"], bold=True, anchor="lm")
    icon(d, SCREEN_W - 48, ty, "pin", c["txt"], 1.0)
    icon(d, SCREEN_W - 18, ty, "search", c["txt"], 1.0)
    y = top + 40 + 34

    # tab 行（暂存夹 / 剪贴板）
    if full:
        tw = (PANEL_W - 16 - 4) // 2
        for i, label in enumerate(("暂存夹", "剪贴板")):
            tx = x0 + 8 + i * (tw + 4)
            sel = (i == 0)
            if sel:
                tbg = tuple(int(c["txt"][j] * 0.06 + c["container"][j] * 0.94) for j in range(3)) if dark \
                    else (238, 239, 244)
                ted = tuple(int(c["txt"][j] * 0.22 + c["container"][j] * 0.78) for j in range(3)) if dark \
                    else (206, 209, 219)
                rrect(d, (tx, y + 2, tx + tw, y + 30), 10, fill=tbg, outline=ted, width=1)
            txt(d, (tx + tw / 2, y + 16), label, 12, c["txt"] if sel else c["sub"],
                bold=sel, anchor="mm")
        y += 34

    # chip 行（属于暂存夹 tab 的下级筛选）
    if tags_on:
        cx = x0 + 10
        cx += chip(canvas, cx, y + 6, "全部", dark=dark) + 6
        for name in ("工作", "想法", "待办"):
            if cx + 50 > SCREEN_W:
                break
            cx += chip(canvas, cx, y + 6, name, tag=name, selected=(name == "工作"), dark=dark) + 6
        chip(canvas, cx, y + 6, "＋", ghost=True, dark=dark)
        canvas.alpha_composite(hgrad(S(30), S(34), 0, 242,
                                     c["container"] if not dark else (26, 28, 34)),
                               (S(SCREEN_W - 30), S(y + 4)))
        y += 40

    # 列表
    y += 6
    footer_h = 40 if not full else 0
    for text, tags, when, st in STASH[:3 if not full else len(STASH)]:
        card_h = (CARD_H3 if len(text) > 15 else CARD_H2) + (26 if tags_on else 0) + CARD_GAP
        if y + card_h + footer_h > top + h:
            break
        y += panel_card(canvas, theme, y, text, tags, when, st and star, tags_on, dark)
    if not full:
        d = ImageDraw.Draw(canvas)
        fl = tuple(int(c["txt"][j] * 0.10 + c["surface"][j] * 0.90) for j in range(3)) if dark else c["line"]
        rect(d, (x0, y + 2, SCREEN_W, y + 3), fill=fl)
        txt(d, (x0 + PANEL_W / 2, y + 22), "查看全部  ›", 11.5, c["accent"], bold=True, anchor="mm")
    return chrome_h


def panel_only(canvas, ox, oy, theme, tags_on, full=True, top=0, dark=False, star=True):
    sub = canvas.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, theme)
    scrim = new_layer(sub.size)
    ImageDraw.Draw(scrim).rectangle([0, 0, sub.size[0], sub.size[1]],
                                   fill=(0, 0, 0, 46 if not dark else 20))
    sub.alpha_composite(scrim)
    draw_panel(sub, theme, tags_on=tags_on, full=full, top=top, dark=dark, star=star)
    status_bar(ImageDraw.Draw(sub), theme)
    canvas.paste(sub, (S(ox), S(oy)))


def capsule_only(canvas, ox, oy, theme):
    sub = canvas.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, theme)
    cw, ch, cx, cy = 46, 176, SCREEN_W - 10 - 46, 88
    glass(sub, (cx, cy, cx + cw, cy + ch), radius=cw / 2, blur=20,
          tint=(252, 253, 255, 150), sat=1.2, rim=0.95, inner=0.42,
          shadow=95, shadow_dx=-3, shadow_dy=5)
    d = ImageDraw.Draw(sub)
    for i, lb in enumerate(("工作", "想法", "待办")):
        yy = cy + 26 + i * 44
        ell(d, (cx + cw / 2 - 3, yy - 13, cx + cw / 2 + 3, yy - 7), fill=TAG_COLORS[lb])
        txt(d, (cx + cw / 2, yy + 8), lb, 11.5, (24, 25, 30), bold=True, anchor="mm")
    rect(d, (cx + 10, cy + ch - 42, cx + cw - 10, cy + ch - 41), fill=(90, 94, 108, 70))
    txt(d, (cx + cw / 2, cy + ch - 22), "＋", 14, (60, 64, 78), anchor="mm")
    status_bar(d, theme)
    canvas.paste(sub, (S(ox), S(oy)))


def capture_only(canvas, ox, oy, theme, typed, dark, new_tag=False, amoled=False):
    sub = canvas.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, theme)
    if amoled:
        ImageDraw.Draw(sub).rectangle([0, 0, sub.size[0], sub.size[1]], fill=(0, 0, 0, 255))
    scrim = new_layer(sub.size)
    ImageDraw.Draw(scrim).rectangle([0, 0, sub.size[0], sub.size[1]],
                                   fill=(0, 0, 0, 20 if amoled else (52 if dark else 46)))
    sub.alpha_composite(scrim)

    cw, ch = 312, 218
    cx, cy = (SCREEN_W - cw) / 2, 268
    if amoled:
        glass(sub, (cx, cy, cx + cw, cy + ch), radius=28, blur=2, tint=(30, 31, 38, 236),
              sat=1.0, rim=1.15, inner=0.5, shadow=120, shadow_dx=0, shadow_dy=8)
    else:
        glass(sub, (cx, cy, cx + cw, cy + ch), radius=28, blur=26,
              tint=(52, 55, 68, 138) if dark else (252, 253, 255, 146), sat=1.2,
              rim=0.95 if dark else 0.8, inner=0.5, shadow=120, shadow_dx=0, shadow_dy=8)
    d = ImageDraw.Draw(sub)
    tx, ty = cx + 16, cy + 15
    if new_tag:
        tx += chip(sub, tx, ty, "工作", tag="工作", selected=True, dark=dark, accent=theme["accent"]) + 7
        chip(sub, tx, ty, "＋", ghost=True, dark=dark)
        chip(sub, cx + cw - 86, ty, "灵感", selected=True, dark=dark, editing=True, caret=True,
             accent=theme["accent"])
    else:
        for i, n in enumerate(("工作", "想法", "待办")):
            tx += chip(sub, tx, ty, n, tag=n, selected=(i == 0), dark=dark,
                       accent=theme["accent"]) + 7
        chip(sub, tx, ty, "＋", ghost=True, dark=dark)
    col = (240, 242, 248) if dark else (28, 29, 34)
    if typed:
        txt(d, (cx + 18, cy + 74), typed, 13, col, anchor="lm")
        cxp = cx + 18 + f(13).getlength(typed) + 2
        rect(d, (cxp, cy + 65, cxp + 1.8, cy + 83), fill=theme["accent"])
    else:
        txt(d, (cx + 18, cy + 74), "输入刚刚想到的内容……", 13, (146, 150, 162), anchor="lm")
    by = cy + ch - 42
    rect(d, (cx + 18, by - 14, cx + cw - 18, by - 13),
         fill=(255, 255, 255, 28) if dark else (120, 124, 138, 42))
    mcx, mcy = cx + cw - 80, by + 11
    ell(d, (mcx - 15, mcy - 15, mcx + 15, mcy + 15),
        fill=(255, 255, 255, 26) if dark else (255, 255, 255, 150),
        outline=(255, 255, 255, 64) if dark else (150, 154, 170, 110), width=1)
    ic = (222, 226, 236) if dark else (72, 76, 92)
    rrect(d, (mcx - 3.5, mcy - 8, mcx + 3.5, mcy + 2), 3.5, fill=ic)
    d.arc([S(mcx - 8), S(mcy - 5), S(mcx + 8), S(mcy + 8)], 0, 180, fill=ic, width=S(1.4))
    rect(d, (mcx - 0.8, mcy + 8, mcx + 0.8, mcy + 11), fill=ic)
    scx, scy = cx + cw - 38, by + 11
    ell(d, (scx - 16, scy - 16, scx + 16, scy + 16), fill=theme["accent"])
    d.line([S(scx - 7), S(scy), S(scx - 2), S(scy + 5.5)], fill=(255, 255, 255), width=S(2))
    d.line([S(scx - 2), S(scy + 5.5), S(scx + 7.5), S(scy - 6)], fill=(255, 255, 255), width=S(2))
    status_bar(d, theme)
    canvas.paste(sub, (S(ox), S(oy)))


# ==================== 六格 ====================
SCREENS = [
    (lambda cv, o: panel_only(cv, o[0], o[1], LIGHT, tags_on=False),
     "① 收纳面板 · 现状", "宽度 50%｜圆角 14dp｜标题 + 图钉 + 搜索 + 双 tab｜灰底白卡 + 真实卡片结构"),
    (lambda cv, o: panel_only(cv, o[0], o[1], LIGHT, tags_on=True),
     "② 同一个面板 · 加入标签", "★唯一新增：tab 行下一行 chip + 卡片内容下一排标签小胶囊，其余全未动"),
    (lambda cv, o: capsule_only(cv, o[0], o[1], LIGHT),
     "③ 折叠态 · 贴边玻璃胶囊", "默认关闭。点击→紧凑面板｜长按→快速记录｜拖动→改位置｜上滑→切标签"),
    (lambda cv, o: panel_only(cv, o[0], o[1], LIGHT, tags_on=True, full=False, top=88),
     "④ 紧凑面板（就地展开）", "与收纳面板同宽同右缘，只是更矮、chrome 更少；「查看全部」进 ①"),
    (lambda cv, o: capture_only(cv, o[0], o[1], LIGHT, "", False),
     "⑤ 快速记录（浅色）", "标签条在最上，输入区居中，语音 / 保存于右下；获焦即弹输入法"),
    (lambda cv, o: capture_only(cv, o[0], o[1], AMOLED, "", True, new_tag=True, amoled=True),
     "⑥ 内联新建标签 · AMOLED 纯黑", "纯黑上模糊无内容可采 → 降级：1px 高光描边 + 极轻填充"),
]


def main():
    canvas = Image.new("RGBA", (S(TOTAL_W), S(TOTAL_H)), (18, 19, 23, 255))
    d = ImageDraw.Draw(canvas)
    txt(d, (MARGIN_X, 52), "闪念胶囊 × 收纳面板 · 最终 UI 形态", 26, (240, 242, 248), bold=True, anchor="lm")
    txt(d, (MARGIN_X, 84),
        "面板尺寸与结构按代码还原：宽 50% · 圆角 14dp 只圆内侧 · 灰底列表 + 白卡 · 标题/图钉/搜索/双 tab · 卡片含时间/取词/星标/操作行",
        12.5, (146, 150, 162), anchor="lm")

    caps = []
    for i, (fn, title, desc) in enumerate(SCREENS):
        col, row = i % COLS, i // COLS
        ox = MARGIN_X + col * (SCREEN_W + GAP_X)
        oy = 30 + TITLE_H + row * (SCREEN_H + CAP_H + GAP_Y)
        fn(canvas, (ox, oy))
        caps.append((title, desc, ox, oy))

    for title, desc, ox, oy in caps:
        d = ImageDraw.Draw(canvas)
        rrect(d, (ox - 7, oy - 7, ox + SCREEN_W + 7, oy + SCREEN_H + 7), 40,
              outline=(74, 78, 92), width=2.4)
        txt(d, (ox, oy + SCREEN_H + 22), title, 14, (238, 240, 246), bold=True)
        txt(d, (ox, oy + SCREEN_H + 42), desc, 11, (140, 144, 156))

    txt(d, (MARGIN_X, TOTAL_H - 20),
        "玻璃：跨应用真模糊 + 手写描边高光 / 内阴影 / 噪点 0.08f｜禁 miuix RuntimeShader（浮层软件 Canvas 会崩）｜面板默认不做玻璃（stashPanelBackgroundBlurEnabled=false）｜标签颜色仅 6dp 色点与选中描边着色",
        11, (128, 132, 144), anchor="lm")

    out = canvas.resize((TOTAL_W, TOTAL_H), Image.LANCZOS).convert("RGB")
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "capsule-ui.png")
    out.save(path, "PNG", optimize=True)
    print("saved", path, out.size, f"{os.path.getsize(path)/1024:.0f} KB")


if __name__ == "__main__":
    main()
