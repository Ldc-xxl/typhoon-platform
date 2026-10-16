# -*- coding: utf-8 -*-
"""
抓取并缓存「西北太平洋高程栅格」（P1 地形图的**唯一数据来源**）。

背景
----
此前三套底图（普通 / 卫星 / 地形）共用同一份 Natural Earth 海岸线几何，
只在 CSS 里换 fill/stroke 三个颜色 token —— 所以"地形图"其实只是绿色的
普通地图，没有任何地形信息。本脚本引入真实高程（DEM），让地形图名副其实。

数据源
------
AWS Open Data「Terrain Tiles」（Mapzen / Tilezen 维护），Terrarium 编码：
    https://registry.opendata.aws/terrain-tiles/
    https://github.com/tilezen/joerd/blob/master/docs/terrarium.md
    高程(米) = R * 256 + G + B / 256 - 32768
    R=深绿 G=浅绿 B=棕；海洋为负值（海深），陆地非负。
    上游是 SRTM / NED / ASTER / 各类公开 DEM 与 ETOPO 拼接。
瓦片为 WebMercator 切片（256×256），z7 单瓦片约 1.2km/px（赤道）。
本项目 1° ≈ 26.79 画布像素（ui.js 的 proj），即 1px ≈ 0.037°，
z7 的 0.011° 仍比最常用的 100%–150% 视距更细，属合适档位。

为什么不用 GEBCO / ETOPO 直连
------------------------------
GEBCO 只有 WMS（返回渲染好的 PNG，拿不到高程数值）与一个报 SRS 错误的 WCS；
ncei.noaa.gov 的 OPeNDAP 在本机网络不通（实测超时）。Terrarium 可达、
且能解出高程数值 —— 已用玉山 / 富士山 / 泰山 / 珠峰实测校验。

用法
----
    python tools/fetch-dem.py            # 增量补齐缺失瓦片并重算网格
    python tools/fetch-dem.py --report   # 只打印当前缓存覆盖情况

输出
----
tools/.cache/dem/<z>_<x>_<y>.png        单瓦片原始 PNG（增量缓存）
tools/.cache/dem-<W>x<H>.npy             拼接后的高程数组（float32, NaN=缺）
"""
import io
import math
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import numpy as np
from PIL import Image

# ---- 覆盖范围：只覆盖台风活动的西北太平洋 + 中国东部/日本/朝鲜半岛/华南 ----
# 刻意不追求"缩到 15% 仍全覆盖"（那要下到 45°E–239°E 的全球 DEM），
# 超出范围一律填 NaN，前端绘制时按"无数据"处理（见 ui.js 的 lyrTerrain）。
LON0, LAT0, LON1, LAT1 = 92.0, 4.0, 178.0, 52.0
ZOOM = 7

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, '.cache', 'dem')
NPY = os.path.join(HERE, '.cache', 'dem-%dx%d.npy' % (ZOOM, 0))  # 名字里带范围哈希
BASE = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png'

THREADS = 12
TIMEOUT = 60


def tile_xy(lon, lat, z):
    """经纬度 -> WebMercator 瓦片号（与 decode 反函数一致）。"""
    n = 1 << z
    x = int(math.floor((lon + 180.0) / 360.0 * n))
    lat = max(min(lat, 85.05112878), -85.05112878)
    y = int(math.floor((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n))
    return x, y


def tile_lonlat(x, y, z):
    n = 1 << z
    lon = x / n * 360.0 - 180.0
    lat = math.degrees(math.atan(math.sinh(math.pi * (1.0 - 2.0 * y / n))))
    return lon, lat


def decode(png_bytes):
    a = np.asarray(Image.open(io.BytesIO(png_bytes)).convert('RGB')).astype(np.int32)
    return a[:, :, 0] * 256 + a[:, :, 1] + a[:, :, 2] / 256.0 - 32768.0


def cache_path(x, y):
    return os.path.join(CACHE, '%d_%d_%d.png' % (ZOOM, x, y))


def get_tile(x, y, tries=3):
    p = cache_path(x, y)
    if os.path.exists(p) and os.path.getsize(p) > 0:
        return p
    url = BASE % (ZOOM, x, y)
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'zfz-dem'})
            with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
                data = r.read()
            with open(p, 'wb') as f:
                f.write(data)
            return p
        except Exception as e:
            if i == tries - 1:
                print('  ! 瓦片 %d/%d/%d 下载失败: %s' % (ZOOM, x, y, e))
                return None
            time.sleep(1.2 * (i + 1))
    return None


def build():
    os.makedirs(CACHE, exist_ok=True)
    x0, y0 = tile_xy(LON0, LAT1, ZOOM)   # 西北角
    x1, y1 = tile_xy(LON1, LAT0, ZOOM)   # 东南角
    nx, ny = x1 - x0 + 1, y1 - y0 + 1
    total = nx * ny
    print('范围   : %.1f°E—%.1f°E / %.1f°N—%.1f°N' % (LON0, LON1, LAT0, LAT1))
    print('瓦片   : z%d  %d×%d = %d 张' % (ZOOM, nx, ny, total))

    todo = [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)
            if not os.path.exists(cache_path(x, y))]
    print('已缓存 : %d 张，需下载 %d 张' % (total - len(todo), len(todo)))

    ok = [0]
    def work(t):
        r = get_tile(*t)
        if r:
            ok[0] += 1
            if ok[0] % 50 == 0:
                print('  ... %d/%d' % (ok[0], len(todo)))

    if todo:
        with ThreadPoolExecutor(max_workers=THREADS) as ex:
            list(ex.map(work, todo))

    grid = np.full((ny * 256, nx * 256), np.nan, dtype=np.float32)
    miss = 0
    for j, y in enumerate(range(y0, y1 + 1)):
        for i, x in enumerate(range(x0, x1 + 1)):
            p = cache_path(x, y)
            if not (os.path.exists(p) and os.path.getsize(p) > 0):
                miss += 1
                continue
            try:
                with open(p, 'rb') as f:
                    grid[j * 256:(j + 1) * 256, i * 256:(i + 1) * 256] = decode(f.read())
            except Exception as e:
                print('  ! 解码 %d/%d/%d 失败: %s' % (ZOOM, x, y, e))
                miss += 1

    # 缺瓦片用最近邻填补：DEM 用于晕渲/等高线，洞会让等高线出现莫名的断线
    if np.isnan(grid).any():
        print('填补   : %d 个空瓦片' % miss)
        mask = np.isnan(grid)
        if mask.all():
            raise SystemExit('全部瓦片缺失，无法生成')
        idx = np.flatnonzero(~mask.ravel())
        # 用最近的有效行/列插值（分块，避免整块距离矩阵爆内存）
        ys, xs = np.nonzero(mask)
        for y0b in range(0, len(ys), 4096):
            sl = slice(y0b, y0b + 4096)
            yy, xx = ys[sl], xs[sl]
            gy, gx = np.mgrid[yy.min():yy.max() + 1, xx.min():xx.max() + 1]
            d = ((gy[:, :, None] - yy[None, None, :]) ** 2 +
                 (gx[:, :, None] - xx[None, None, :]) ** 2).astype(np.int64)
            grid[yy, xx] = grid[yy[xx[np.argmin(d, axis=2)]], xx[np.argmin(d, axis=2)]]

    out = NPY[:-6] + '_%.0f%.0f_%.0f%.0f.npy' % (LON0, LAT1, LON1, LAT0)
    np.save(out, grid)

    land = grid > 0
    print('网格   : %d×%d  (%.2f MB)' % (grid.shape[1], grid.shape[0], os.path.getsize(out) / 1048576.0))
    print('陆地   : %.1f%%   高程 %.0f–%.0f m' % (land.mean() * 100, grid[land].min(), grid.max()))
    print('输出   : %s' % os.path.basename(out))
    return out


def report():
    x0, y0 = tile_xy(LON0, LAT1, ZOOM)
    x1, y1 = tile_xy(LON1, LAT0, ZOOM)
    have = sum(1 for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)
               if os.path.exists(cache_path(x, y)))
    print('瓦片缓存 %d / %d' % (have, (x1 - x0 + 1) * (y1 - y0 + 1)))
    for f in sorted(os.listdir(HERE)):
        if f.startswith('dem-') and f.endswith('.npy'):
            print('  %s  %.2f MB' % (f, os.path.getsize(os.path.join(HERE, '.cache', f)) / 1048576.0))


if __name__ == '__main__':
    if '--report' in sys.argv:
        report()
    else:
        build()