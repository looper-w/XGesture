"""
闪念胶囊 · 大胆重构（胶囊即实体）— 静态稿

与 ui_demo_capsule.html 同一套设计：
  边缘时间轴 + 胶囊链（胶囊里是内容前 2 字）／呼出输入槽／点胶囊就地编辑（可追加）
  时间流面板（时间轴 + 今天·昨天·更早 + 记忆褪色 + 标签叠加筛选）／归档回流收纳面板

运行：python generate_stream_mockups.py  →  capsule-stream.png
"""

import math
import os
from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont

SS = 2
SCREEN_W, SCREEN_H = 400, 860
COLS, ROWS = 3, 2
GAP_X, GAP_Y = 44, 48
MARGIN_X = 48
TITLE_H, CAP_H, FOOT_H = 120, 58, 50

TOTAL_W = MARGIN_X * 2 + COLS * SCREEN_W + (COLS - 1) * GAP_X
TOTAL_H = 30 + TITLE_H + ROWS * (SCREEN_H + CAP_H) + (ROWS - 1) * GAP_Y + FOOT_H

FONT_R, FONT_B = "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/msyhbd.ttc"

DARK = dict(app=(11, 12, 16), surface=(18, 19, 23), container=(30, 33, 44),
            txt=(232, 234, 240), sub=(138, 142, 156), line=(255, 255, 255),
            accent=(91, 108, 240), hdr_a=(42, 58, 160), hdr_b=(90, 62, 168),
            tint=(40, 44, 58, 140), border=(255, 255, 255, 40), rim=0.30, inner=0.55, dark=True)
LIGHT = dict(app=(238, 240, 246), surface=(241, 242, 246), container=(255, 255, 255),
             txt=(23, 24, 29), sub=(116, 121, 138), line=(0, 0, 0),
             accent=(91, 108, 240), hdr_a=(62, 104, 224), hdr_b=(122, 90, 224),
             tint=(252, 253, 255, 150), border=(255, 255, 255, 230), rim=0.95, inner=0.34, dark=False)
AMOLED = dict(DARK, app=(0, 0, 0), surface=(0, 0, 0), container=(21, 22, 27),
              tint=(30, 31, 38, 236), border=(255, 255, 255, 52), rim=0.42, inner=0.7,
              hdr_a=(20, 22, 28), hdr_b=(28, 30, 38))

TAG_COLORS = {"工作": (91, 141, 246), "想法": (240, 169, 59), "待办": (87, 184, 122),
              "灵感": (199, 125, 240), "设计": (232, 110, 140)}

CHAIN = [("把描边改成 1px，太粗会显廉价", ["设计"], True),
         ("记得周五前把方案初稿发给老王", ["工作"], True),
         ("买咖啡豆", ["待办"], False)]

STREAM = [
    ("今天", [("刚刚", "把描边改成 1px，太粗会显廉价", ["设计"], "fresh"),
              ("12 分钟前", "记得周五前把方案初稿发给老王", ["工作"], "fresh"),
              ("今天 09:20", "买咖啡豆", ["待办"], "fresh"),
              ("今天 08:05", "液态玻璃的价值不是好看，是让层级可读", ["想法"], "fresh")]),
    ("昨天", [("17:40", "会议纪要：Q3 主线是降低常驻内存", ["工作"], "mid"),
              ("11:02", "提词器的滚动速度要可调，不然读稿很累", ["想法", "设计"], "mid")]),
    ("更早", [("周三", "和设计确认胶囊的圆角用 15 还是 16", ["设计"], "old")]),
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


def glass(canvas, box, radius, blur=20, tint=(252, 253, 255, 150), sat=1.3, rim=0.85,
          inner=0.5, shadow=100, sdx=1, sdy=6, corners=(True, True, True, True)):
    x0, y0, x1, y1 = S(box[0]), S(box[1]), S(box[2]), S(box[3])
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    if shadow > 0:
        sh = new_layer(canvas.size)
        ImageDraw.Draw(sh).rounded_rectangle(
            [x0 + S(sdx), y0 + S(sdy), x1 + S(sdx), y1 + S(sdy)], radius=S(radius), fill=(0, 0, 0, shadow))
        canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(S(10))))
    region = canvas.crop((x0, y0, x1, y1)).convert("RGBA")
    base = ImageEnhance.Color(region.filter(ImageFilter.GaussianBlur(S(blur)))).enhance(sat)
    base = Image.alpha_composite(base, Image.new("RGBA", (w, h), tint))
    mask = shape_mask(w, h, radius, corners)
    rl = new_layer((w, h))
    ImageDraw.Draw(rl).rounded_rectangle([S(0.8), S(0.8), w - S(0.8), h - S(0.8)], radius=S(radius),
                                        outline=(255, 255, 255, 255), width=max(1, S(0.9)))
    rl = rl.filter(ImageFilter.GaussianBlur(S(0.5)))
    a = ImageChops.multiply(rl.getchannel("A"), vgrad(w, h, 255, int(255 * 0.14)))
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
        dark.paste((10, 12, 20, 255), (0, 0, w, h))
        dark.putalpha(ring.point(lambda v: int(v * inner)))
        base = Image.alpha_composite(base, dark)
    canvas.paste(base, (x0, y0), mask)


def chip(canvas, x, y, label, tag=None, selected=False, th=None, h=24, ghost=False, mini=False):
    th = th or DARK
    size = 10 if mini else 11
    tw = f(size, selected).getlength(label)
    dot = 5 if tag else 0
    w = (9 if mini else 10) + (dot + 4 if dot else 0) + tw + (9 if mini else 10)
    glass(canvas, (x, y, x + w, y + h), radius=h / 2, blur=8, sat=1.2,
          tint=(91, 108, 240, 78) if selected else th["tint"],
          rim=1.0 if selected else 0.7, inner=0.32, shadow=0 if mini else 18, sdy=2)
    d = ImageDraw.Draw(canvas)
    tx = x + (9 if mini else 10)
    if dot:
        r = 2.5
        ell(d, (tx, y + h / 2 - r, tx + dot, y + h / 2 + r), fill=TAG_COLORS.get(tag, (150, 155, 168)))
        tx += dot + 4
    col = th["sub"] if ghost else th["txt"]
    txt(d, (tx, y + h / 2), label, size, col, bold=selected, anchor="lm")
    return w


def icon(d, cx, cy, kind, color, scale=1.0):
    w = 1.5 * scale
    if kind == "mic":
        rrect(d, (cx - 3, cy - 7, cx + 3, cy + 2), 3, fill=color)
        d.arc([S(cx - 7), S(cy - 5), S(cx + 7), S(cy + 7)], 0, 180, fill=color, width=S(w))
        rect(d, (cx - 0.7, cy + 6, cx + 0.7, cy + 9), fill=color)
    elif kind == "check":
        d.line([S(cx - 5), S(cy), S(cx - 1), S(cy + 4)], fill=color, width=S(2))
        d.line([S(cx - 1), S(cy + 4), S(cx + 6), S(cy - 4)], fill=color, width=S(2))
    elif kind == "clock":
        d.ellipse([S(cx - 6), S(cy - 6), S(cx + 6), S(cy + 6)], outline=color, width=S(w))
        d.line([S(cx), S(cy - 3), S(cx), S(cy + 1), S(cx + 3), S(cy + 3)], fill=color, width=S(w))
    elif kind == "arch":
        rrect(d, (cx - 7, cy - 3, cx + 7, cy + 6), 1.6, outline=color, width=w)
        rrect(d, (cx - 8, cy - 7, cx + 8, cy - 3), 1.6, outline=color, width=w)
        d.line([S(cx - 2), S(cy + 1), S(cx + 2), S(cy + 1)], fill=color, width=S(w))
    elif kind == "close":
        d.line([S(cx - 4), S(cy - 4), S(cx + 4), S(cy + 4)], fill=color, width=S(w))
        d.line([S(cx + 4), S(cy - 4), S(cx - 4), S(cy + 4)], fill=color, width=S(w))


def status_bar(d, th):
    col = th["txt"]
    txt(d, (20, 15), "14:20", 11, col, bold=True, anchor="lm")
    x = SCREEN_W - 20
    for i in range(4):
        h = 3 + i * 2.2
        rect(d, (x - 25 + i * 4, 15 - h, x - 22 + i * 4, 15), fill=col)
    rrect(d, (x, 10, x + 14, 19), 3, outline=col, width=1.1)


def backdrop_app(canvas, th):
    d = ImageDraw.Draw(canvas)
    rect(d, (0, 0, SCREEN_W, SCREEN_H), fill=th["app"])
    band = new_layer(canvas.size)
    bd = ImageDraw.Draw(band)
    for y in range(S(190)):
        t = y / S(190)
        bd.line([(0, y), (canvas.size[0], y)],
                fill=tuple(int(th["hdr_a"][i] + (th["hdr_b"][i] - th["hdr_a"][i]) * t) for i in range(3)) + (255,))
    canvas.alpha_composite(band)
    d = ImageDraw.Draw(canvas)
    txt(d, (22, 56), "今日要闻", 19, (255, 255, 255), bold=True, anchor="lm")
    txt(d, (22, 82), "科技 · 设计 · 产品", 11, (255, 255, 255, 190), anchor="lm")
    rect(d, (22, 102, 62, 105), fill=(255, 255, 255, 215))
    txt(d, (22, 222), "液态玻璃之后，", 20, th["txt"], bold=True, anchor="lm")
    txt(d, (22, 252), "系统 UI 会走向哪里", 20, th["txt"], bold=True, anchor="lm")
    txt(d, (22, 288), "2026-10-05 · 8 分钟阅读", 11, th["sub"], anchor="lm")
    # 正文大图：正好落在边缘胶囊链背后，玻璃才有东西可透
    y = 304
    block = new_layer(canvas.size)
    bd = ImageDraw.Draw(block)
    for i in range(S(250)):
        t = i / S(250)
        bd.line([(S(22), S(y) + i), (S(SCREEN_W - 22), S(y) + i)],
                fill=(int(104 + t * 62), int(114 + t * 26), int(176 + t * 44), 255))
    m = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([S(22), S(y), S(SCREEN_W - 22), S(y + 250)], radius=S(18), fill=255)
    canvas.paste(block, (0, 0), m)
    d = ImageDraw.Draw(canvas)
    y += 272
    for w in (0.92, 0.84, 0.9, 0.6):
        rect(d, (22, y, 22 + (SCREEN_W - 44) * w, y + 9),
             fill=th["line"] + (26,) if th["dark"] else th["line"] + (34,))
        y += 22


# ==================== 边缘链 ====================
RAIL_X = 338
CAP_X = 348
CAP_W = 44
CAP_H = 68
CAP_GAP = 12
ADD_H = 46


def dashed_rrect(d, box, radius, color, dash=3.0, gap=2.6, width=1.0):
    """沿圆角矩形周长画虚线（Pillow 没有 dash 支持）。"""
    x0, y0, x1, y1 = box
    r = radius
    pts = []

    def arc(cx, cy, a0, a1):
        n = max(2, int(abs(a1 - a0) / 6) + 1)
        for i in range(n + 1):
            a = math.radians(a0 + (a1 - a0) * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))

    pts.append((x0 + r, y0))
    pts.append((x1 - r, y0))
    arc(x1 - r, y0 + r, -90, 0)
    pts.append((x1, y1 - r))
    arc(x1 - r, y1 - r, 0, 90)
    pts.append((x0 + r, y1))
    arc(x0 + r, y1 - r, 90, 180)
    pts.append((x0, y0 + r))
    arc(x0 + r, y0 + r, 180, 270)

    step = dash + gap
    total = 0.0
    cur = []
    for i in range(len(pts) - 1):
        ax, ay = pts[i]
        bx, by = pts[i + 1]
        seg = math.hypot(bx - ax, by - ay)
        t = 0.0
        while t < seg:
            phase = (total + t) % step
            on = phase < dash
            px = ax + (bx - ax) * (t / seg if seg else 0)
            py = ay + (by - ay) * (t / seg if seg else 0)
            if on:
                cur.append((px, py))
            elif cur:
                if len(cur) > 1:
                    d.line([S(cur[0][0]), S(cur[0][1]), S(cur[-1][0]), S(cur[-1][1])],
                           fill=color, width=S(width))
                cur = []
            t += 1.0
        total += seg
    if len(cur) > 1:
        d.line([S(cur[0][0]), S(cur[0][1]), S(cur[-1][0]), S(cur[-1][1])], fill=color, width=S(width))


def chain_heights(n, with_add=True):
    return ([ADD_H] if with_add else []) + [CAP_H] * n


def chain_layout(heights, gap=CAP_GAP):
    total = sum(heights) + gap * (len(heights) - 1)
    y0 = int(SCREEN_H / 2 - total / 2)
    ys, y = [], y0
    for h in heights:
        ys.append(y)
        y += h + gap
    return ys


def draw_chain(canvas, th, items=None, with_add=True):
    items = items if items is not None else CHAIN
    hs = chain_heights(len(items), with_add)
    ys = chain_layout(hs)
    d = ImageDraw.Draw(canvas)

    # 时间轴细线：从第一颗到最后一颗
    top = ys[0]
    bottom = ys[-1] + hs[-1]
    lay = new_layer(canvas.size)
    ImageDraw.Draw(lay).line([S(RAIL_X), S(top + 10), S(RAIL_X), S(bottom - 10)],
                             fill=(255, 255, 255, 40), width=S(1))
    canvas.alpha_composite(lay)
    d = ImageDraw.Draw(canvas)

    idx = 0
    if with_add:
        y = ys[0]
        cy = y + ADD_H / 2
        lay = new_layer(canvas.size)
        ImageDraw.Draw(lay).line([S(RAIL_X), S(cy), S(CAP_X), S(cy)], fill=(255, 255, 255, 46), width=S(1))
        canvas.alpha_composite(lay)
        d = ImageDraw.Draw(canvas)
        # 极轻填充，靠虚线描边读出来
        lay = new_layer(canvas.size)
        ImageDraw.Draw(lay).rounded_rectangle(
            [S(CAP_X), S(y), S(CAP_X + CAP_W), S(y + ADD_H)], radius=S(15),
            fill=tuple(list(th["tint"][:3]) + [54]))
        canvas.alpha_composite(lay)
        d = ImageDraw.Draw(canvas)
        dashed_rrect(d, (CAP_X + 0.5, y + 0.5, CAP_X + CAP_W - 0.5, y + ADD_H - 0.5), 15,
                     (255, 255, 255, 120))
        txt(d, (CAP_X + CAP_W / 2, cy), "＋", 17, th["sub"], anchor="mm")
        idx = 1

    for i, (text, tags, fresh) in enumerate(items):
        y = ys[idx + i]
        cy = y + CAP_H / 2
        lay = new_layer(canvas.size)
        ImageDraw.Draw(lay).line([S(RAIL_X), S(cy), S(CAP_X), S(cy)],
                                 fill=(255, 255, 255, 60 if fresh else 34), width=S(1))
        canvas.alpha_composite(lay)
        tint = th["tint"] if fresh else tuple(list(th["tint"][:3]) + [int(th["tint"][3] * 0.62)])
        glass(canvas, (CAP_X, y, CAP_X + CAP_W, y + CAP_H), radius=15, blur=20, sat=1.3,
              tint=tint, rim=th["rim"] * (1.25 if fresh else 0.8), inner=th["inner"],
              shadow=90 if fresh else 40, sdx=-2, sdy=5)
        d = ImageDraw.Draw(canvas)
        label = text.replace("，", "").replace("。", "")[:2]
        col = th["txt"] if fresh else tuple(int(th["txt"][k] * 0.72 + th["sub"][k] * 0.28) for k in range(3))
        txt(d, (CAP_X + CAP_W / 2, y + 23), label[0], 12, col, bold=True, anchor="mm")
        if len(label) > 1:
            txt(d, (CAP_X + CAP_W / 2, y + 40), label[1], 12, col, bold=True, anchor="mm")
        for k, t in enumerate(tags[:3]):
            ell(d, (CAP_X + CAP_W / 2 - 6 + k * 6, y + 54, CAP_X + CAP_W / 2 - 3 + k * 6, y + 57),
                fill=TAG_COLORS.get(t, (150, 155, 168)))
        if fresh:
            lay = new_layer(canvas.size)
            ImageDraw.Draw(lay).rounded_rectangle(
                [S(CAP_X), S(y), S(CAP_X + CAP_W), S(y + CAP_H)], radius=S(15),
                outline=th["accent"] + (150,), width=max(1, S(1)))
            canvas.alpha_composite(lay)


def panel_tabs(canvas, th, y, labels, active=0, x0=0, w=SCREEN_W):
    d = ImageDraw.Draw(canvas)
    tw = (w - 16 - 8) // len(labels)
    for i, label in enumerate(labels):
        tx = x0 + 8 + i * (tw + 4)
        sel = (i == active)
        if sel:
            if th["dark"]:
                tbg = tuple(int(th["txt"][k] * 0.06 + th["container"][k] * 0.94) for k in range(3))
                ted = tuple(int(th["txt"][k] * 0.22 + th["container"][k] * 0.78) for k in range(3))
            else:
                tbg, ted = (238, 239, 244), (206, 209, 219)
            rrect(d, (tx, y, tx + tw, y + 28), 10, fill=tbg, outline=ted, width=1)
        txt(d, (tx + tw / 2, y + 14), label, 11.5, th["txt"] if sel else th["sub"], bold=sel, anchor="mm")


def draw_slot(canvas, th, y, text="把描边改成 1px", placeholder=False):
    w = 286
    x0 = SCREEN_W - 10 - w
    glass(canvas, (x0, y, x0 + w, y + 52), radius=26, blur=22, sat=1.3,
          tint=th["tint"], rim=th["rim"], inner=th["inner"], shadow=110, sdx=-3, sdy=6)
    d = ImageDraw.Draw(canvas)
    icon(d, x0 + 26, y + 26, "mic", th["txt"], 0.95)
    if placeholder:
        txt(d, (x0 + 48, y + 26), "想点什么… 回车落成胶囊", 13, th["sub"], anchor="lm")
    else:
        txt(d, (x0 + 48, y + 26), text, 13.5, th["txt"], anchor="lm")
        cxp = x0 + 48 + f(13.5).getlength(text) + 2
        rect(d, (cxp, y + 17, cxp + 1.8, y + 35), fill=th["accent"])
    cx = x0 + w - 26
    ell(d, (cx - 17, y + 9, cx + 17, y + 43), fill=th["accent"])
    icon(d, cx, y + 26, "check", (255, 255, 255), 1.0)


def draw_editbar(canvas, th, y, text_lines, tags, sel_tags):
    w, h = 300, 150
    x0 = SCREEN_W - 10 - w
    glass(canvas, (x0, y, x0 + w, y + h), radius=24, blur=22, sat=1.3,
          tint=th["tint"], rim=th["rim"], inner=th["inner"], shadow=120, sdx=-3, sdy=8)
    d = ImageDraw.Draw(canvas)
    txt(d, (x0 + 14, y + 16), "刚刚", 10.5, th["sub"], anchor="lm")
    txt(d, (x0 + w - 14, y + 16), "光标已在末尾，可直接追加", 10.5, th["sub"], anchor="rm")
    ty = y + 32
    for ln in text_lines:
        txt(d, (x0 + 14, ty), ln, 13.5, th["txt"], anchor="la")
        ty += 20
    cx = x0 + 14
    for t in tags:
        cx += chip(canvas, cx, y + 84, t, tag=t, selected=(t in sel_tags), th=th) + 5
    chip(canvas, cx, y + 84, "＋", ghost=True, th=th)
    rect(d, (x0 + 14, y + 118, x0 + w - 14, y + 119),
         fill=th["line"] + (30,) if th["dark"] else (0, 0, 0, 26))
    bw = (w - 28 - 12) / 3
    for i, (kind, label, primary) in enumerate((("clock", "提醒", False),
                                                ("arch", "归档", False),
                                                ("check", "保存", True))):
        bx = x0 + 14 + i * (bw + 6)
        if primary:
            rrect(d, (bx, y + 126, bx + bw, y + 142), 8, fill=th["accent"])
        else:
            rrect(d, (bx, y + 126, bx + bw, y + 142), 8, outline=th["border"], width=1)
        icol = (255, 255, 255) if primary else th["txt"]
        icon(d, bx + 14, y + 134, kind, icol, 0.66)
        txt(d, (bx + 26, y + 134), label, 11, icol, anchor="lm")


def draw_stream(canvas, th, y=0):
    w = 312
    x0 = SCREEN_W - w
    glass(canvas, (x0, y, SCREEN_W, y + SCREEN_H), radius=26, blur=24, sat=1.3,
          tint=th["tint"], rim=th["rim"] * 0.8, inner=th["inner"] * 0.8, shadow=0,
          corners=(True, False, False, True))
    d = ImageDraw.Draw(canvas)
    txt(d, (x0 + 20, 58), "闪念", 19, th["txt"], bold=True, anchor="lm")
    txt(d, (x0 + 76, 60), "10 条 · 3 颗仍在边缘", 11, th["sub"], anchor="lm")
    icon(d, SCREEN_W - 26, 58, "close", th["txt"], 1.0)
    # 三个页签：闪念 / 暂存夹 / 剪贴板（既有能力保留）
    panel_tabs(canvas, th, 78, ("闪念", "暂存夹", "剪贴板"), active=0, x0=x0, w=w)
    # 标签筛选（只放得下 3 个，其余横向滚动）
    cx = x0 + 18
    cx += chip(canvas, cx, 116, "全部", selected=False, th=th) + 6
    cx += chip(canvas, cx, 116, "工作", tag="工作", selected=True, th=th) + 6
    chip(canvas, cx, 116, "想法", tag="想法", th=th)
    canvas.alpha_composite(hgrad(S(26), S(30), 0, 244, th["container"]),
                           (S(SCREEN_W - 26), S(114)))

    yy = 158
    for gname, items in STREAM:
        txt(d, (x0 + 20, yy + 8), gname, 10.5, th["sub"], bold=True, anchor="lm")
        lay = new_layer(canvas.size)
        ImageDraw.Draw(lay).line([S(x0 + 62), S(yy + 4), S(x0 + 62), S(yy + 20 + len(items) * 66)],
                                 fill=th["line"] + (34,) if th["dark"] else (0, 0, 0, 30), width=S(1))
        canvas.alpha_composite(lay)
        d = ImageDraw.Draw(canvas)
        for when, text, tags, kind in items:
            bx0 = x0 + 76
            bx1 = SCREEN_W - 18
            bh = 56 if len(text) <= 20 else 68
            # 记忆褪色：越旧越淡
            if kind == "fresh":
                tint = th["tint"]
                rim = th["rim"]
            elif kind == "mid":
                tint = tuple(list(th["tint"][:3]) + [int(th["tint"][3] * 0.55)])
                rim = th["rim"] * 0.55
            else:
                tint = tuple(list(th["tint"][:3]) + [0])
                rim = th["rim"] * 0.25
            glass(canvas, (bx0, yy + 20, bx1, yy + 20 + bh), radius=18, blur=20, sat=1.25,
                  tint=tint, rim=rim, inner=th["inner"] * (1 if kind == "fresh" else 0.4),
                  shadow=70 if kind == "fresh" else 0, sdx=0, sdy=4)
            d = ImageDraw.Draw(canvas)
            node_col = th["accent"] if kind == "fresh" else th["sub"]
            ell(d, (x0 + 59, yy + 30, x0 + 66, yy + 37), fill=node_col)
            txt(d, (bx0 + 12, yy + 12), when, 10, th["sub"], anchor="lm")
            col = th["txt"] if kind != "old" else th["sub"]
            lines = [text[i:i + 15] for i in range(0, len(text), 15)][:2]
            for k, ln in enumerate(lines):
                txt(d, (bx0 + 12, yy + 34 + k * 19), ln, 12.5, col, anchor="lm")
            tx = bx0 + 12
            for t in tags:
                tx += chip(canvas, tx, yy + 20 + bh - 26, t, tag=t, th=th, h=20, mini=True) + 4
            yy += bh + 22
        yy += 6


# ==================== 六格 ====================
def vtext(canvas, cx, cy, s, size, color):
    """竖排文字（旋转 90°），用于边缘细带上的标注。"""
    tmp = Image.new("RGBA", (S(240), S(18)), (0, 0, 0, 0))
    ImageDraw.Draw(tmp).text((S(2), S(9)), s, font=f(size), fill=color, anchor="lm")
    bb = tmp.getbbox()
    if bb:
        tmp = tmp.crop(bb)
    tmp = tmp.rotate(90, expand=True)
    canvas.alpha_composite(tmp, (S(cx) - tmp.width // 2, S(cy) - tmp.height // 2))


def draw_gzone(canvas, th):
    """可视化右侧 48dp 系统返回手势热区（斜纹带）。"""
    lay = new_layer(canvas.size)
    ld = ImageDraw.Draw(lay)
    for i in range(0, S(SCREEN_H), S(5)):
        ld.line([(S(SCREEN_W - 48), i), (S(SCREEN_W - 48 + 5), i + S(5))],
                fill=(255, 110, 110, 34), width=S(5))
    canvas.alpha_composite(lay)
    lay = new_layer(canvas.size)
    ImageDraw.Draw(lay).line([S(SCREEN_W - 48), 0, S(SCREEN_W - 48), S(SCREEN_H)],
                             fill=(255, 110, 110, 90), width=S(1))
    canvas.alpha_composite(lay)
    vtext(canvas, SCREEN_W - 46, SCREEN_H * 0.2, "系统返回手势区 48dp", 9, (255, 155, 155))


def cell_settled(cv, ox, oy, th=DARK):
    """沉淀态：屏上几乎无物 —— 细指示条 + （有未处理时）计数胶囊。"""
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    draw_gzone(sub, th)
    # 细指示条 9×28
    y = 0.34 * SCREEN_H
    glass(sub, (SCREEN_W - 21, y, SCREEN_W - 12, y + 28), radius=5, blur=10,
          tint=th["tint"], rim=th["rim"], inner=th["inner"], shadow=40, sdx=-1, sdy=2)
    # 未处理计数胶囊（40 宽）
    dy = 0.52 * SCREEN_H
    glass(sub, (SCREEN_W - 52, dy, SCREEN_W - 12, dy + 66), radius=15, blur=18,
          tint=th["tint"], rim=th["rim"] * 1.1, inner=th["inner"], shadow=70, sdx=-2, sdy=4)
    d = ImageDraw.Draw(sub)
    txt(d, (SCREEN_W - 32, dy + 20), "2", 13, th["txt"], bold=True, anchor="mm")
    txt(d, (SCREEN_W - 32, dy + 38), "待办", 9, th["sub"], anchor="mm")
    ell(d, (SCREEN_W - 35, dy + 48, SCREEN_W - 30, dy + 53), fill=th["accent"])
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


def cell_active(cv, ox, oy, th=DARK):
    """瞬时态：只露最新一颗 + 「＋」。"""
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    draw_chain(sub, th, CHAIN[:1])
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


def cell_chain(cv, ox, oy, th=DARK):
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    draw_chain(sub, th)
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


def cell_slot(cv, ox, oy, th=DARK):
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    draw_chain(sub, th, CHAIN)
    # 输入槽从链顶那个「＋」就地长出
    ys = chain_layout(chain_heights(len(CHAIN), True))
    draw_slot(sub, th, ys[0] + ADD_H / 2 - 26)
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


def cell_edit(cv, ox, oy, th=DARK):
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    draw_chain(sub, th, CHAIN)
    draw_editbar(sub, th, 236, ["把描边改成 1px，太粗会显廉价", "—— 追加：改成 0.5px 试试"], ["设计", "想法"], ["设计"])
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


def cell_stream(cv, ox, oy, th=DARK):
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    scrim = new_layer(sub.size)
    ImageDraw.Draw(scrim).rectangle([0, 0, sub.size[0], sub.size[1]], fill=(0, 0, 0, 100))
    sub.alpha_composite(scrim)
    draw_stream(sub, th)
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


def cell_archive(cv, ox, oy, th=LIGHT):
    """归档回流：既有收纳面板（半屏）里出现刚归档的闪念，带标签。"""
    sub = cv.crop((S(ox), S(oy), S(ox + SCREEN_W), S(oy + SCREEN_H)))
    backdrop_app(sub, th)
    scrim = new_layer(sub.size)
    ImageDraw.Draw(scrim).rectangle([0, 0, sub.size[0], sub.size[1]], fill=(0, 0, 0, 40))
    sub.alpha_composite(scrim)
    px = SCREEN_W // 2
    d = ImageDraw.Draw(sub)
    # 灰底列表
    lay = new_layer(sub.size)
    ImageDraw.Draw(lay).rectangle([S(px), 0, S(SCREEN_W), S(SCREEN_H)], fill=th["surface"] + (255,))
    m = Image.new("L", sub.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([S(px), 0, S(SCREEN_W), S(SCREEN_H)], radius=S(14), fill=255)
    ImageDraw.Draw(m).rectangle([S(px + 14), 0, S(SCREEN_W), S(SCREEN_H)], fill=255)
    sub.paste(lay, (0, 0), m)
    d = ImageDraw.Draw(sub)
    # chrome
    ch = 40 + 34 + 34 + 40
    lay = new_layer(sub.size)
    ImageDraw.Draw(lay).rectangle([S(px), 0, S(SCREEN_W), S(ch)], fill=th["container"] + (255,))
    m = Image.new("L", sub.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([S(px), 0, S(SCREEN_W), S(ch + 16)], radius=S(14), fill=255)
    ImageDraw.Draw(m).rectangle([S(px + 14), 0, S(SCREEN_W), S(ch + 16)], fill=255)
    ImageDraw.Draw(m).rectangle([S(px), S(ch), S(SCREEN_W), S(ch + 16)], fill=0)
    sub.paste(lay, (0, 0), m)
    d = ImageDraw.Draw(sub)
    txt(d, (px + 16, 57), "收纳面板", 13.5, th["txt"], bold=True, anchor="lm")
    icon(d, SCREEN_W - 48, 57, "clock", th["txt"], 0.9)
    icon(d, SCREEN_W - 18, 57, "close", th["txt"], 0.85)
    # 三个页签：闪念 / 暂存夹 / 剪贴板 —— 暂存夹为当前页（闪念归档落在这里）
    panel_tabs(sub, th, 76, ("闪念", "暂存夹", "剪贴板"), active=1, x0=px, w=SCREEN_W - px)
    cx = px + 10
    cx += chip(sub, cx, 116, "全部", selected=True, th=th) + 6
    for n in ("工作", "想法"):
        cx += chip(sub, cx, 116, n, tag=n, th=th) + 6
    if cx + 40 < SCREEN_W:
        chip(sub, cx, 116, "＋", ghost=True, th=th)
    sub.alpha_composite(hgrad(S(30), S(34), 0, 240, th["container"]), (S(SCREEN_W - 30), S(114)))
    d = ImageDraw.Draw(sub)
    # 卡片：刚归档的那条在最上，带"新"标记
    rows = [("刚刚", "把描边改成 1px，太粗会显廉价", ["设计"], True),
            ("12 分钟前", "记得周五前把方案初稿发给老王", ["工作"], False),
            ("昨天", "会议纪要：Q3 主线是降低常驻内存", ["工作"], False)]
    ry = 164
    for when, text, tags, isnew in rows:
        h = 128
        lay = new_layer(sub.size)
        ImageDraw.Draw(lay).rounded_rectangle([S(px + 12), S(ry), S(SCREEN_W - 12), S(ry + h)],
                                             radius=S(16), fill=th["container"] + (255,))
        sub.alpha_composite(lay)
        d = ImageDraw.Draw(sub)
        txt(d, (px + 24, ry + 16), when, 10.5, th["accent"] if isnew else th["sub"],
            bold=isnew, anchor="lm")
        if isnew:
            txt(d, (SCREEN_W - 26, ry + 16), "新", 10, th["accent"], bold=True, anchor="rm")
        icon(d, SCREEN_W - 52, ry + 16, "clock", th["txt"], 0.8)
        for k, ln in enumerate([text[i:i + 13] for i in range(0, len(text), 13)][:2]):
            txt(d, (px + 24, ry + 40 + k * 18), ln, 12, th["txt"], anchor="lm")
        tx = px + 24
        for t in tags:
            tx += chip(sub, tx, ry + 84, t, tag=t, th=th, h=22, mini=True) + 5
        rect(d, (px + 24, ry + 112, SCREEN_W - 24, ry + 113), fill=(226, 228, 235))
        for k, kind in enumerate(("arch", "clock", "check")):
            icon(d, px + 36 + k * 26, ry + 122, kind, th["txt"], 0.6)
        ry += h + 8
    status_bar(ImageDraw.Draw(sub), th)
    cv.paste(sub, (S(ox), S(oy)))


SCREENS = [
    (lambda cv, o: cell_settled(cv, o[0], o[1], DARK),
     "① 沉淀态（默认）", "屏上几乎无物：细指示条 9×28dp；只有「未处理」才显示计数胶囊——那时它才有行动价值"),
    (lambda cv, o: cell_active(cv, o[0], o[1], DARK),
     "② 瞬时态（刚记录）", "只露最新一颗 + 「＋」，呼吸光提示；12s（真机 20~30s / 切应用 / 息屏）后自动沉淀"),
    (lambda cv, o: cell_edit(cv, o[0], o[1], DARK),
     "③ 展开态 · 点胶囊就地编辑", "按需呼出链条；点胶囊 → 它就地横向展开成编辑条，光标在原文末尾可直接追加"),
    (lambda cv, o: cell_stream(cv, o[0], o[1], DARK),
     "④ 时间流面板（三页签）", "闪念 / 暂存夹 / 剪贴板；时间轴 + 今天昨天更早 + 记忆褪色 + 标签叠加筛选"),
    (lambda cv, o: cell_archive(cv, o[0], o[1], LIGHT),
     "⑤ 归档回流收纳面板", "同一份数据：归档的胶囊落进「暂存夹」并带标签；剪贴板仍是第三个页签"),
    (lambda cv, o: cell_slot(cv, o[0], o[1], AMOLED),
     "⑥ 呼出输入槽（AMOLED）", "长按右缘热区 / 手势动作「闪念速记」直达；纯黑上靠 1px 高光描边读出玻璃"),
]


def main():
    canvas = Image.new("RGBA", (S(TOTAL_W), S(TOTAL_H)), (16, 17, 22, 255))
    d = ImageDraw.Draw(canvas)
    txt(d, (MARGIN_X, 52), "闪念胶囊 · 大胆重构 —— 胶囊即实体", 26, (240, 242, 248), bold=True, anchor="lm")
    txt(d, (MARGIN_X, 86),
        "记录不再是列表里的一行，而是挂在屏幕边缘的实物：可直接呼出、就地编辑、就地追加；玻璃亮度 = 时间距离；边缘链就是时间轴",
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
        rrect(d, (ox - 7, oy - 7, ox + SCREEN_W + 7, oy + SCREEN_H + 7), 42,
              outline=(72, 76, 90), width=2.4)
        txt(d, (ox, oy + SCREEN_H + 22), title, 14, (238, 240, 246), bold=True)
        txt(d, (ox, oy + SCREEN_H + 42), desc, 10.5, (140, 144, 156))

    txt(d, (MARGIN_X, TOTAL_H - 20),
        "玻璃：跨应用真模糊 + 手写描边高光 / 内阴影 / 噪点 0.07f｜禁 miuix RuntimeShader（浮层软件 Canvas 会崩）｜标签颜色仅 6dp 色点与选中描边着色",
        11, (128, 132, 144), anchor="lm")

    out = canvas.resize((TOTAL_W, TOTAL_H), Image.LANCZOS).convert("RGB")
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "capsule-stream.png")
    out.save(path, "PNG", optimize=True)
    print("saved", path, out.size, f"{os.path.getsize(path)/1024:.0f} KB")


if __name__ == "__main__":
    main()
