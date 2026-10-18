#!/usr/bin/env python3
"""鸡仔浏览器 UI 改版 v2 推导渲染：按新版代码逐像素绘制。
浅色苹果风精修：四色操作图标、书签字母砖、去 emoji、空态插画。"""
from PIL import Image, ImageDraw, ImageFont

FR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FB = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

W, H = 1920, 1080
BG_TOP = (255, 255, 255)
BG_BOT = (245, 245, 247)
CARD = (255, 255, 255)
INK = (29, 29, 31)
MUTED = (110, 110, 115)
FAINT = (161, 161, 166)
BRAND = (255, 56, 92)
WHITE = (255, 255, 255)

TILE_PAIRS = [
    ((253, 232, 236), (217, 50, 90)),
    ((232, 241, 254), (10, 100, 216)),
    ((228, 245, 234), (23, 138, 76)),
    ((255, 241, 222), (199, 106, 0)),
    ((240, 234, 253), (126, 63, 196)),
    ((223, 245, 241), (14, 124, 111)),
]
ACTION_TILES = [
    ((255, 233, 238), (255, 56, 92)),    # 手机遥控 粉
    ((232, 241, 254), (10, 132, 255)),   # 添加书签 蓝
    ((255, 243, 226), (232, 117, 0)),    # 清空历史 橙
    ((241, 234, 254), (142, 78, 198)),   # 视频源 紫
]

def font(size, bold=False):
    return ImageFont.truetype(FB if bold else FR, size)

def jhash(s):
    h = 0
    for c in s:
        h = (31 * h + ord(c)) & 0xFFFFFFFF
    return h - 0x100000000 if h & 0x80000000 else h

def vgrad(d):
    for y in range(H):
        t = y / H
        c = tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)

def rr(d, box, r, fill, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def txt(d, xy, s, f, fill):
    d.text(xy, s, font=f, fill=fill)

def tcenter(d, cx, y, s, f, fill):
    w = d.textlength(s, font=f)
    d.text((cx - w / 2, y), s, font=f, fill=fill)

def alpha_rr(base, box, r, fill):
    ov = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).rounded_rectangle(box, radius=r, fill=fill)
    base.alpha_composite(ov)

def shadow_card(base, box, r):
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    sd.rounded_rectangle([box[0], box[1] + 6, box[2], box[3] + 10], radius=r, fill=(0, 0, 0, 14))
    base.alpha_composite(sh)

def icon_search(d, cx, cy, s, color):
    w = max(2, int(s * .08))
    d.ellipse([cx - s * .26, cy - s * .32, cx + s * .26, cy + s * .2], outline=color, width=w)
    d.line([cx + s * .18, cy + s * .12, cx + s * .34, cy + s * .3], fill=color, width=w)

def icon_mic(d, cx, cy, s, color):
    w = max(2, int(s * .07))
    d.rounded_rectangle([cx - s * .12, cy - s * .3, cx + s * .12, cy - s * .02], radius=s * .12, outline=color, width=w)
    d.arc([cx - s * .24, cy - s * .2, cx + s * .24, cy + s * .24], 15, 165, fill=color, width=w)
    d.line([cx, cy + s * .24, cx, cy + s * .36], fill=color, width=w)
    d.line([cx - s * .1, cy + s * .36, cx + s * .1, cy + s * .36], fill=color, width=w)

def icon_phone(d, cx, cy, s, color):
    w = max(2, int(s * .08))
    d.rounded_rectangle([cx - s * .2, cy - s * .32, cx + s * .2, cy + s * .32], radius=s * .1, outline=color, width=w)
    d.line([cx - s * .06, cy + s * .22, cx + s * .06, cy + s * .22], fill=color, width=w)

def icon_star(d, cx, cy, s, color):
    import math
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = s * .34 if i % 2 == 0 else s * .15
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, outline=color, width=max(2, int(s * .07)))

def icon_trash(d, cx, cy, s, color):
    w = max(2, int(s * .07))
    d.line([cx - s * .22, cy - s * .18, cx + s * .22, cy - s * .18], fill=color, width=w)
    d.line([cx - s * .1, cy - s * .28, cx + s * .1, cy - s * .28], fill=color, width=w)
    d.rectangle([cx - s * .18, cy - s * .18, cx + s * .18, cy + s * .28], outline=color, width=w)

def icon_video(d, cx, cy, s, color):
    w = max(2, int(s * .07))
    d.rounded_rectangle([cx - s * .32, cy - s * .22, cx + s * .32, cy + s * .22], radius=s * .08, outline=color, width=w)
    d.polygon([(cx - s * .05, cy - s * .11), (cx - s * .05, cy + s * .11), (cx + s * .13, cy)], fill=color)

def icon_empty(d, cx, cy, s, color):
    w = max(2, int(s * .07))
    d.rounded_rectangle([cx - s * .34, cy - s * .1, cx + s * .34, cy + s * .3], radius=s * .06, outline=color, width=w)
    d.line([cx - s * .34, cy - s * .1, cx - s * .24, cy - s * .3], fill=color, width=w)
    d.line([cx + s * .34, cy - s * .1, cx + s * .24, cy - s * .3], fill=color, width=w)
    d.line([cx - s * .24, cy - s * .3, cx + s * .24, cy - s * .3], fill=color, width=w)

# ---------------- 首页 ----------------
def home():
    img = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    d = ImageDraw.Draw(img, "RGBA")
    vgrad(d)
    PX = 64
    # 品牌栏
    y0 = 48
    rr(d, [PX, y0, PX + 72, y0 + 72], 18, BRAND)
    tcenter(d, PX + 36, y0 + 10, "鸡", font(38, True), WHITE)
    txt(d, (PX + 92, y0 - 2), "鸡仔浏览器", font(40, True), INK)
    txt(d, (PX + 94, y0 + 54), "为大屏而生 · v0.6.0", font(16), MUTED)
    # 搜索 hero
    sy = y0 + 72 + 36
    shadow_card(img, [PX, sy, W - PX, sy + 112], 16)
    d = ImageDraw.Draw(img, "RGBA")
    rr(d, [PX, sy, W - PX, sy + 112], 16, CARD)
    icon_search(d, PX + 48, sy + 56, 44, FAINT)
    txt(d, (PX + 84, sy + 36), "输入网址或搜索关键词", font(26), FAINT)
    # 语音按钮（圆形）
    vx = W - PX - 12 - 200 - 8 - 76
    vcy = sy + 56
    alpha_rr(img, [vx, sy + 18, vx + 76, sy + 94], 38, (255, 233, 238, 255))
    d = ImageDraw.Draw(img, "RGBA")
    icon_mic(d, vx + 38, vcy, 44, BRAND)
    # 前往（胶囊）
    gx = W - PX - 12 - 200
    rr(d, [gx, sy + 18, gx + 200, sy + 94], 28, BRAND)
    tcenter(d, gx + 100, sy + 34, "前往", font(24, True), WHITE)
    # 快捷卡片
    cy = sy + 112 + 28
    cards = [
        ("手机遥控", "扫码配对 · 手机变遥控器", icon_phone, 0),
        ("添加书签", "收藏常用网站", icon_star, 1),
        ("清空历史", "删除浏览记录", icon_trash, 2),
        ("视频源", "合法片源 · 自己添加", icon_video, 3),
    ]
    cw = (W - 2 * PX - 30) // 4
    for i, (t, desc, ic, ti) in enumerate(cards):
        x = PX + i * (cw + 10)
        tbg, tfg = ACTION_TILES[ti]
        shadow_card(img, [x, cy, x + cw, cy + 184], 16)
        d = ImageDraw.Draw(img, "RGBA")
        rr(d, [x, cy, x + cw, cy + 184], 16, CARD)
        rr(d, [x + (cw - 52) // 2, cy + 24, x + (cw - 52) // 2 + 52, cy + 76], 14, tbg)
        ic(d, x + cw // 2, cy + 50, 44, tfg)
        tcenter(d, x + cw // 2, cy + 90, t, font(22, True), INK)
        tcenter(d, x + cw // 2, cy + 130, desc, font(14), MUTED)
    # 我的书签
    by = cy + 184 + 48
    txt(d, (PX, by), "我的书签", font(28, True), INK)
    txt(d, (W - PX - 60, by + 8), "4 个", font(16), FAINT)
    bms = [("Bilibili", "bilibili.com"), ("知乎", "zhihu.com"), ("GitHub", "github.com"), ("豆瓣电影", "movie.douban.com")]
    bx = PX
    for t, dom in bms:
        pair = (jhash(dom) & 0x7FFFFFFF) % 6
        pbg, pfg = TILE_PAIRS[pair]
        letter = t[0].upper()
        shadow_card(img, [bx, by + 56, bx + 300, by + 224], 16)
        d = ImageDraw.Draw(img, "RGBA")
        rr(d, [bx, by + 56, bx + 300, by + 224], 16, CARD)
        # 字母砖
        tx0, ty0 = bx + 24, by + 56 + 52
        rr(d, [tx0, ty0, tx0 + 64, ty0 + 64], 16, pbg)
        tcenter(d, tx0 + 32, ty0 + 12, letter, font(28, True), pfg)
        txt(d, (tx0 + 82, ty0 + 6), t, font(20), INK)
        txt(d, (tx0 + 82, ty0 + 40), dom, font(14), FAINT)
        bx += 316
    # 历史记录
    hy = by + 224 + 40
    txt(d, (PX, hy), "历史记录", font(28, True), INK)
    txt(d, (W - PX - 60, hy + 8), "3 条", font(16), FAINT)
    his = [("如何评价 Vision Pro 二代", "v2ex.com"), ("OpenAI 发布新推理模型", "36kr.com"), ("欧冠之夜集锦", "youtube.com")]
    hx = PX
    for t, dom in his:
        shadow_card(img, [hx, hy + 56, hx + 340, hy + 180], 16)
        d = ImageDraw.Draw(img, "RGBA")
        rr(d, [hx, hy + 56, hx + 340, hy + 180], 16, CARD)
        txt(d, (hx + 24, hy + 88), t, font(18), INK)
        txt(d, (hx + 24, hy + 120), dom, font(14), FAINT)
        hx += 356
    img.convert("RGB").save("/home/hatch/workspace/tv-renders/v2-home.png")
    print("v2 home ok")

# ---------------- 浏览页 ----------------
def browser():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    d = ImageDraw.Draw(img, "RGBA")
    rr(d, [0, 0, W, H], 0, (255, 255, 255))
    txt(d, (120, 170), "OpenAI 发布新一代推理模型，数学能力大幅提升", font(40, True), INK)
    txt(d, (120, 240), "36kr.com · 5 分钟前 · 阅读约 3 分钟", font(20), MUTED)
    for i in range(6):
        wdt = 1500 - (i % 3) * 220
        rr(d, [120, 300 + i * 46, 120 + wdt, 322 + i * 46], 11, (232, 232, 235))
    d.rectangle([0, 0, W * 0.62, 4], fill=BRAND)
    tx, tw = 200, W - 400
    ty = 24
    rr(d, [tx, ty, tx + tw, ty + 80], 28, (255, 255, 255, 242), outline=(229, 229, 234), width=1)
    rr(d, [tx + 10, ty + 10, tx + 106, ty + 70], 14, CARD, outline=BRAND, width=3)
    tcenter(d, tx + 58, ty + 24, "主页", font(20), INK)
    rr(d, [tx + 116, ty + 10, tx + tw - 186, ty + 70], 14, (245, 245, 247))
    txt(d, (tx + 138, ty + 26), "www.example.com/article", font(19), INK)
    # 嗅探按钮（图标版）
    sx = tx + tw - 176
    rr(d, [sx, ty + 10, sx + 60, ty + 70], 14, CARD)
    icon_video(d, sx + 30, ty + 40, 40, INK)
    rr(d, [tx + tw - 106, ty + 10, tx + tw - 10, ty + 70], 14, CARD)
    tcenter(d, tx + tw - 58, ty + 24, "菜单", font(20), INK)
    img.convert("RGB").save("/home/hatch/workspace/tv-renders/v2-browser.png")
    print("v2 browser ok")

# ---------------- 视频源页 ----------------
def videosource():
    img = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    d = ImageDraw.Draw(img, "RGBA")
    vgrad(d)
    PX = 64
    txt(d, (PX, 48), "视频源", font(40, True), INK)
    txt(d, (PX, 104), "请只添加合法视频源", font(16), MUTED)
    txt(d, (PX, 156), "我的源", font(28, True), INK)
    srcs = [("TMDB 热门", "需自填 Key"), ("本地 JSON", "2 个源")]
    sx = PX
    for t, dom in srcs:
        shadow_card(img, [sx, 216, sx + 300, sx + 364], 16)
        d = ImageDraw.Draw(img, "RGBA")
        rr(d, [sx, 216, sx + 300, sx + 364], 16, CARD)
        txt(d, (sx + 28, 252), t, font(20), INK)
        txt(d, (sx + 28, 288), dom, font(14), FAINT)
        sx += 316
    # 添加源 chip（图标+文字）
    shadow_card(img, [sx, 216, sx + 220, 364], 16)
    d = ImageDraw.Draw(img, "RGBA")
    rr(d, [sx, 216, sx + 220, 364], 16, CARD)
    d.line([sx + 48, 276, sx + 48, 304], fill=BRAND, width=4)
    d.line([sx + 34, 290, sx + 62, 290], fill=BRAND, width=4)
    txt(d, (sx + 76, 272), "添加源", font(20), BRAND)
    txt(d, (PX, 424), "热门推荐", font(28, True), INK)
    posters = [("星际穿越", "科幻"), ("深海探秘", "纪录片"), ("欧冠之夜", "体育"), ("科技周刊", "资讯")]
    px = PX
    for t, tag in posters:
        shadow_card(img, [px, 484, px + 280, 804], 16)
        d = ImageDraw.Draw(img, "RGBA")
        rr(d, [px, 484, px + 280, 804], 16, (38, 42, 58))
        d.polygon([(px + 128, 604), (px + 128, 652), (px + 168, 628)], fill=WHITE)
        rr(d, [px, 708, px + 280, 804], 0, CARD)
        txt(d, (px + 22, 732), t, font(18), INK)
        txt(d, (px + 22, 766), tag, font(14), FAINT)
        px += 296
    img.convert("RGB").save("/home/hatch/workspace/tv-renders/v2-videosource.png")
    print("v2 videosource ok")

if __name__ == "__main__":
    home(); browser(); videosource()
