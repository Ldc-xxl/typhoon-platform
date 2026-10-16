# -*- coding: utf-8 -*-
"""裁出地形底图的典型区域，用于肉眼检查晕渲/分层设色/等高线的实际效果。"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
WEBAPP = os.path.abspath(os.path.join(HERE, '..', 'src', 'main', 'webapp'))
SRC = os.path.join(WEBAPP, 'assets', 'terrain', 'terrain-hillshade.png')
OUT = os.path.join(HERE, '_terrain_shots')
os.makedirs(OUT, exist_ok=True)

# 与 ui.js proj 一致
LNG0, LAT0, KX, KY = 118.0894, 24.4798, 26.79, 27.11
OX, OY = 351, 198
LON_W, LAT_N, LON_E, LAT_S = 92.0, 52.0, 178.0, 4.0
PAD = [-348.0, -548.0, 1956.0, 753.0]   # geo-wterrain.js 的 pad

OVER = 2.0


def crop(lon0, lat0, lon1, lat1, name, scale=1.0):
    im = Image.open(SRC)
    sx = im.width / (PAD[2] - PAD[0])
    sy = im.height / (PAD[3] - PAD[1])

    def to_img(lon, lat):
        x = (OX + (lon - LNG0) * KX - PAD[0]) * sx
        y = (OY + (LAT0 - lat) * KY - PAD[1]) * sy
        return x, y

    x0, y0 = to_img(lon0, lat1)
    x1, y1 = to_img(lon1, lat0)
    box = (max(int(x0), 0), max(int(y0), 0),
           min(int(x1), im.width), min(int(y1), im.height))
    sub = im.crop(box)
    if scale != 1.0:
        sub = sub.resize((int(sub.width * scale), int(sub.height * scale)), Image.LANCZOS)
    p = os.path.join(OUT, name + '.png')
    sub.save(p)
    print('%-22s %s' % (name, sub.size))


if __name__ == '__main__':
    # 台湾中央山脉（最陡的地形最能看出晕渲与等高线是否有效）
    crop(119.5, 21.5, 122.5, 25.5, 'taiwan', 1.0)
    # 福建—浙江（台风登陆典型区：丘陵+平原+海）
    crop(116.0, 23.0, 124.0, 30.0, 'fujian', 1.0)
    # 华北平原 + 太行山（看分层设色从绿到褐的过渡）
    crop(110.0, 33.0, 120.0, 41.0, 'northchina', 1.0)
    # 全域缩略（看整体色彩与海陆对比）
    crop(92.0, 4.0, 178.0, 52.0, 'overview', 0.42)