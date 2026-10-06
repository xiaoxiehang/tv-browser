#!/usr/bin/env python3
"""鸡仔浏览器 v0.2.0 产品预览图：按新版深色 TV 界面重绘（无 emoji，纯文字+几何图形）"""
from PIL import Image, ImageDraw, ImageFont

FR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FB = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

W, H = 1920, 1080
BG_TOP = (20, 28, 54)
BG_BOT = (10, 13, 22)
CARD = (23, 32, 60)
INK = (245, 243, 236)
MUTED = (154, 163, 192)
FAINT = (98, 107, 140)
BRAND = (229, 9, 20)
WHITE = (255, 255, 255)


def font(size, bold=False):
    return ImageFont.truetype(FB if bold else FR, size)


def vgrad(d):
    for y in range(H):
        t = y / H
        c = tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)


def rr(d, box, radius, fill, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def tcenter(d, cx, y, s, f, fill):
    w = d.textlength(s, font=f)
    d.text((cx - w / 2, y), s, font=f, fill=fill)


def avatar(d, cx, cy, r, color, ch, fsize=44):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    f = font(fsize, True)
    w = d.textlength(ch, font=f)
    bb = f.getbbox(ch)
    d.text((cx - w / 2, cy - (bb[3] - bb[1]) / 2 - bb[1]), ch, font=f, fill=WHITE)


def home():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img, "RGBA")
    vgrad(d)
    rr(d, [96, 64, 192, 160], 26, BRAND)
    tcenter(d, 144, 78, "鸡", font(56, True), WHITE)
    d.text((224, 66), "鸡仔浏览器", font=font(60, True), fill=INK)
    d.text((226, 148), "为大屏而生 · v0.2.0", font=font(26), fill=MUTED)
    rr(d, [96, 250, 1824, 390], 70, (26, 34, 66), outline=WHITE, width=3)
    d.text((180, 288), "输入网址或搜索关键词…", font=font(34), fill=FAINT)
    rr(d, [1520, 270, 1800, 370], 50, BRAND)
    tcenter(d, 1660, 288, "前 往", font(34, True), WHITE)
    actions = [
        ((10, 132, 255), "手", "手机遥控", "扫码配对 · 手机变遥控器"),
        ((245, 197, 24), "星", "添加书签", "收藏常用网站"),
        ((52, 180, 85), "清", "清空历史", "删除浏览记录"),
    ]
    for i, (color, ch, title, desc) in enumerate(actions):
        x = 96 + i * 584
        rr(d, [x, 450, x + 560, 690], 28, CARD, outline=(255, 255, 255, 46), width=1)
        avatar(d, x + 280, 540, 46, color, ch, 44)
        tcenter(d, x + 280, 596, title, font(32, True), INK)
        tcenter(d, x + 280, 644, desc, font(22), MUTED)
    d.text((96, 736), "我的书签", font=font(40, True), fill=INK)
    d.text((1330, 748), "4 个 · 长按遥控器 OK 键可删除", font=font(24), fill=FAINT)
    marks = [
        ((229, 9, 20), "B", "B站", "bilibili.com"),
        ((10, 132, 255), "G", "GitHub", "github.com"),
        ((52, 180, 85), "知", "知乎", "zhihu.com"),
        ((255, 159, 10), "豆", "豆瓣", "douban.com"),
    ]
    for i, (color, ch, title, dom) in enumerate(marks):
        x = 96 + i * 444
        rr(d, [x, 806, x + 420, 1006], 28, CARD, outline=(255, 255, 255, 46), width=1)
        avatar(d, x + 80, 886, 40, color, ch, 40)
        d.text((x + 140, 836), title, font=font(32, True), fill=INK)
        d.text((x + 140, 892), dom, font=font(24), fill=MUTED)
    img.save("/home/hatch/workspace/xiaojj-pro/site/assets/img/tv-home.png")


def browser():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([0, 0, W, H], fill=(13, 16, 28))
    # 网页视频区（工具栏浮在网页内容之上）
    rr(d, [96, 190, 1824, 690], 24, (20, 27, 52))
    d.ellipse([880, 360, 1040, 520], fill=BRAND)
    d.polygon([(940, 395), (940, 485), (1010, 440)], fill=WHITE)
    d.text((120, 720), "【科技早报】AI 八条速递 · 鸡仔出品", font=font(40, True), fill=INK)
    d.text((120, 780), "128 万播放 · 2.3 万弹幕 · 2026-09-30", font=font(26), fill=MUTED)
    comments = [
        ((10, 132, 255), "A", "这个浏览器在电视上真好用"),
        ((245, 197, 24), "K", "求更新！想要倍速播放"),
        ((52, 180, 85), "M", "已安利给全家"),
    ]
    for i, (color, ch, txt) in enumerate(comments):
        y = 850 + i * 92
        rr(d, [96, y, 1240, y + 76], 20, CARD)
        avatar(d, 160, y + 38, 24, color, ch, 26)
        d.text((200, y + 12), txt, font=font(26), fill=INK)
    rr(d, [200, 36, 1720, 146], 55, (22, 30, 56), outline=(255, 255, 255, 70), width=1)
    rr(d, [228, 52, 318, 130], 24, (35, 48, 88))
    hx, hy = 273, 91
    d.polygon([(hx - 22, hy + 2), (hx, hy - 20), (hx + 22, hy + 2)], outline=WHITE, width=4)
    d.rectangle([hx - 14, hy, hx + 14, hy + 18], outline=WHITE, width=4)
    rr(d, [334, 52, 1570, 130], 24, (35, 48, 88))
    d.ellipse([364, 76, 388, 100], fill=(52, 180, 85))
    d.text((404, 68), "bilibili.com/video/BV1xJ4m1K7xQ", font=font(30), fill=INK)
    rr(d, [1586, 52, 1676, 130], 24, (35, 48, 88))
    for dy in (-18, 0, 18):
        d.ellipse([1621, 91 + dy - 6, 1633, 91 + dy + 6], fill=WHITE)
    d.rectangle([0, 0, 1150, 6], fill=BRAND)
    cx, cy = 960, 440
    d.ellipse([cx - 52, cy - 52, cx + 52, cy + 52], outline=WHITE, width=5)
    d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], fill=BRAND)
    img.save("/home/hatch/workspace/xiaojj-pro/site/assets/img/tv-browser.png")


home()
browser()
print("mockups v2 done")
