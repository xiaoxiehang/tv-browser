# -*- coding: utf-8 -*-
"""浅色苹果风 TV 浏览器预览图（1920x1080）"""
from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 1080
BG_TOP = (255, 255, 255)
BG = (245, 245, 247)
INK = (29, 29, 31)
MUTED = (110, 110, 115)
FAINT = (161, 161, 166)
BLUE = (0, 122, 255)
BORDER = (229, 229, 234)
RED = (229, 9, 20)
WHITE = (255, 255, 255)

def font(sz, bold=False):
    for p in ["/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc" if bold else "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
              "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc" if bold else "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
              "/usr/share/fonts/noto-cjk/NotoSansCJK-Bold.ttc" if bold else "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc"]:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            continue
    return ImageFont.load_default()

def rr(d, box, r, fill, outline=None, width=1):
    d.rounded_rectangle(box, r, fill=fill, outline=outline, width=width)

def tcenter(d, cx, y, s, f, c):
    bb = d.textbbox((0, 0), s, font=f)
    d.text((cx - (bb[2] - bb[0]) / 2, y), s, font=f, fill=c)

def vgrad(d):
    for y in range(H):
        t = y / H
        c = tuple(int(BG_TOP[i] + (BG[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)

def brandbar(d, ver):
    d.rounded_rectangle([96, 56, 164, 124], 18, fill=RED)
    tcenter(d, 130, 60, "鸡", font(40, True), WHITE)
    d.text((192, 56), "鸡仔浏览器", font=font(44, True), fill=INK)
    d.text((192, 112), f"为大屏而生 · v{ver}", font=font(20), fill=MUTED)

def home():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    vgrad(d)
    brandbar(d, "0.3.1")
    # 搜索框
    rr(d, [96, 220, 1824, 324], 52, WHITE, BORDER, 1)
    d.text((150, 252), "输入网址或搜索关键词…", font=font(30), fill=FAINT)
    rr(d, [1590, 236, 1796, 308], 36, BLUE)
    tcenter(d, 1693, 250, "前往", font(30, True), WHITE)
    # 快捷卡片：图标底 + 文字
    TILE = (234, 241, 255)
    cards = [("手机遥控", "扫码配对 · 手机变遥控器", "phone"), ("添加书签", "收藏常用网站", "star"), ("清空历史", "删除浏览记录", "trash")]
    for i, (t, desc, kind) in enumerate(cards):
        x0 = 96 + i * 590
        focused = (i == 0)
        rr(d, [x0, 384, x0 + 552, 564], 20, (238, 238, 242))  # 投影
        rr(d, [x0, 380, x0 + 552, 560], 20, WHITE, BLUE if focused else BORDER, 2 if focused else 1)
        cx, ty = x0 + 276, 398
        rr(d, [cx - 32, ty, cx + 32, ty + 64], 16, TILE)
        if kind == "phone":
            d.rounded_rectangle([cx - 12, ty + 12, cx + 12, ty + 52], 6, outline=BLUE, width=3)
            d.line([cx - 5, ty + 46, cx + 5, ty + 46], fill=BLUE, width=3)
        elif kind == "star":
            pts = []
            import math
            for k in range(10):
                r = 20 if k % 2 == 0 else 8.5
                a = -math.pi / 2 + k * math.pi / 5
                pts.append((cx + r * math.cos(a), ty + 32 + r * math.sin(a)))
            d.polygon(pts, outline=BLUE)
        else:
            d.rectangle([cx - 14, ty + 22, cx + 14, ty + 52], outline=BLUE, width=3)
            d.line([cx - 18, ty + 22, cx + 18, ty + 22], fill=BLUE, width=3)
            d.line([cx - 6, ty + 30, cx - 6, ty + 46], fill=BLUE, width=3)
            d.line([cx + 6, ty + 30, cx + 6, ty + 46], fill=BLUE, width=3)
        tcenter(d, x0 + 276, 470, t, font(30, True), INK)
        tcenter(d, x0 + 276, 514, desc, font(20), MUTED)
    # 书签
    d.text((96, 620), "我的书签", font=font(34, True), fill=INK)
    d.text((1700, 630), "4 个 · 长按 OK 键可删除", font=font(20), fill=FAINT)
    bms = [("B站", "bilibili.com"), ("GitHub", "github.com"), ("知乎", "zhihu.com"), ("豆瓣", "douban.com")]
    for i, (t, dom) in enumerate(bms):
        x0 = 96 + i * 435
        rr(d, [x0, 690, x0 + 400, 838], 20, WHITE, BORDER, 1)
        d.text((x0 + 30, 716), t, font=font(28), fill=INK)
        d.text((x0 + 30, 762), dom, font=font(20), fill=FAINT)
    # 历史
    d.text((96, 890), "历史记录", font=font(34, True), fill=INK)
    d.text((1740, 900), "3 条", font=font(20), fill=FAINT)
    his = [("【科技早报】AI 八条速递", "bilibili.com"), ("GitHub Trending", "github.com")]
    for i, (t, dom) in enumerate(his):
        x0 = 96 + i * 465
        rr(d, [x0, 950, x0 + 430, 1030], 16, WHITE, BORDER, 1)
        d.text((x0 + 24, 966), t, font=font(22), fill=INK)
    img.save("/home/hatch/workspace/xiaojj-pro/site/assets/img/tv-home.png")

def browser():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, H], fill=WHITE)
    # 网页：浅色视频区
    rr(d, [96, 190, 1824, 640], 24, (240, 240, 243))
    d.ellipse([880, 320, 1040, 480], fill=BLUE)
    d.polygon([(940, 355), (940, 445), (1010, 400)], fill=WHITE)
    d.text((120, 670), "【科技早报】AI 八条速递 · 鸡仔出品", font=font(40, True), fill=INK)
    d.text((120, 730), "128 万播放 · 2.3 万弹幕 · 2026-09-30", font=font(26), fill=MUTED)
    comments = ["这个浏览器在电视上真好用", "求更新！想要倍速播放", "已安利给全家"]
    for i, txt in enumerate(comments):
        y = 800 + i * 92
        rr(d, [96, y, 1150, y + 76], 38, (245, 245, 247))
        d.text((140, y + 18), txt, font=font(26), fill=INK)
    # 悬浮胶囊工具栏
    rr(d, [200, 36, 1720, 148], 56, (245, 255, 255, 255), BORDER, 1)
    rr(d, [228, 52, 340, 132], 20, WHITE, BORDER, 1)
    tcenter(d, 284, 72, "主页", font(26), INK)
    rr(d, [356, 52, 1460, 132], 20, WHITE, BORDER, 1)
    d.text((392, 72), "bilibili.com/video/BV1xJ4m1K7xQ", font=font(26), fill=INK)
    rr(d, [1476, 52, 1588, 132], 20, WHITE, BLUE, 2)
    tcenter(d, 1532, 72, "菜单", font(26), INK)
    # 进度条
    d.rectangle([0, 0, 1150, 6], fill=BLUE)
    # 光标：蓝环 + 蓝点
    cx, cy = 960, 400
    d.ellipse([cx - 28, cy - 28, cx + 28, cy + 28], outline=WHITE, width=3)
    d.ellipse([cx - 25, cy - 25, cx + 25, cy + 25], outline=BLUE, width=4)
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=BLUE)
    img.save("/home/hatch/workspace/xiaojj-pro/site/assets/img/tv-browser.png")

home()
browser()
print("mockups light done")
