# -*- coding: utf-8 -*-
"""
生成「地形图」所需的全部资源：晕渲 + 分层设色底图、矢量等高线、高程点位。

为什么必须有这个脚本
--------------------
此前三套底图（普通 / 卫星 / 地形）共用同一份 Natural Earth 海岸线几何，
只在 CSS 里换三组 fill/stroke 颜色（app.css 里就三行）——所以"地形图"
其实只是绿色的普通地图，**没有任何地形信息**。要让地形图名副其实，
必须引入真实高程（DEM），并把制图学上「高程 → 颜色 + 明暗 + 等高线」
这条映射真正做出来。本脚本就是那条映射的实现。

三个输出，对应地形图的三项身份特征
----------------------------------
1. **晕渲（hillshade）**：由 DEM 的坡度/坡向算光照强度。
   地形图的立体感来源 —— 只换色永远做不出"山"的立体感。
2. **分层设色（hypsometric tint）**：按高程分档着色（绿→黄→褐→白），
   小比例尺地形图的主要地形载体。与晕渲**相乘**得到最终底图颜色。
3. **矢量等高线**：marching squares 提取 + Douglas-Peucker 简化。
   位图放大到 600% 会糊，矢量等高线不会。

关于比例尺（本脚本所有参数的推导依据）
------------------------------------
画布 804px 横向覆盖 30° 经度 ≈ 2880km 地面，**1 画布像素 ≈ 3.6km**。
这是**大陆级小比例尺**，因此：
  · 等高距必须**分级**且整体偏粗。台湾中央山脉坡度约 2°，
    若用 200m 等高距，相邻线地面间距仅 200/tan(2°) ≈ 5.7km ≈ 1.6px，
    必然糊成一片；统一间隔在小比例尺下是错的，制图学上用分级等高距。
  · 小比例尺地形图主要靠**晕渲 + 分层设色**表达地形，
    等高线只保留能读懂的**计曲线**级别，不是全要素等高线。
  · 位图按 OVER 倍超采样；更高只是浪费体积与加载时间。

坐标系
------
输出坐标与 js/ui.js 的 proj() 完全一致：
    x = 351 + (lon - 118.0894) * 26.79
    y = 198 + (24.4798 - lat) * 27.11
位图输出为覆盖整个 DEM 范围的 PNG（前端 <image> 铺在
.map-view-g 内，与陆地共用同一个视野变换）；等高线与高程点
输出为画布像素坐标的 path / 点位。

用法
----
    python tools/gen-terrain.py
    python tools/gen-terrain.py --over 1.5      # 降位图分辨率（体积/清晰度折衷）
    python tools/gen-terrain.py --coarse 2      # 等高距整体放大 N 倍

输出
----
src/main/webapp/assets/terrain/terrain-hillshade.png   晕渲+分层设色底图
src/main/webapp/js/geo-wterrain.js                      等高线 + 高程点
"""
import math
import os
import sys
from collections import defaultdict

import numpy as np
from PIL import Image

# ---- 与 ui.js / gen-wpac-coast.py 保持一致的投影参数（改这里必须同步改那两处）----
LNG0, LAT0, KX, KY = 118.0894, 24.4798, 26.79, 27.11
OX, OY = 351, 198

# DEM 覆盖范围（西, 北, 东, 南），须与 tools/fetch-dem.py 的范围一致
LON_W, LAT_N, LON_E, LAT_S = 92.0, 52.0, 178.0, 4.0

ZOOM = 7                      # fetch-dem.py 抓取的层级
RES_DEG = 360.0 / ((1 << ZOOM) * 256)   # 单瓦片像素的经度跨度（≈0.011°）
OVER = 2.0                    # 位图超采样倍数（2 = 100% 视距下 2 倍像素密度）

# ---- 分级等高距：[高程上限(米), 该段等高距(米)] ----
# 不能用统一间隔，必须分级。本项目画布 804px 横向覆盖约 2880km 地面，
# **1 画布像素 ≈ 3.6km**，属大陆级小比例尺：
#   · 200m 等高距在台湾中央山脉（坡度约 2°）上，相邻线地面间距仅
#     200/tan(2°) ≈ 5.7km ≈ 1.6px，线会连成一片灰，等于没画；
#   · 但低海拔丘陵区用 1000m 又几乎一条线都没有。
# 因此按高程分段加密：高海拔粗（线本来就密）、低海拔细（本来就稀疏）。
#
# 低海拔段从 500m 起、250m 间隔，是本项目实测的取舍：
# 试过从 250m 起、间隔 250m，结果低海拔段一条线就贡献 7.8 万点、600KB，
# 而在 3.6km/px 的比例尺下这些线既读不出高程、又盖住晕渲的明暗层次。
# 小比例尺地形图的取舍原则是：**宁可线稀而清楚，不要线密成灰**。
# 各级等高距 + 各自是否算计曲线（True = 加粗且默认显示）。
# 分三档的依据是**山区线密度**：试过统一 500m（实测 7.8 万点），
# 在 1.6×–2.4× 视距下台湾中央山脉/武夷山的 500m 线间距不足 1px，
# 连成一片白雾，反而把晕渲的立体感盖掉 —— 地形图最该保留的正是明暗层次。
# 现在：平原不画线，丘陵/低山走 1000m 计曲线（默认显示），
# 中高山上到 2500m 才加密到 500m（仅放大时显示），雪山再加密到 1000m。
CONTOUR_STEPS = [
    (1500, 1000, True),    # 1500m 以下：每 1000m 一条（计曲线）
    (3000, 500, False),    # 1500–3000m：每 500m（一般线，放大才显示）
    (1e9, 1000, True),     # 3000m 以上：每 1000m（计曲线）
]
# 计曲线：满 CONTOUR_INDEX_M 的整数倍加粗。
# 注意必须用「整数倍判定」而不是取模 —— 各级等高距不同（500/1000），
# 用 lv % 1000 判定时 250 步长永远撞不上 1000 的倍数（实测计曲线数为 0）。
CONTOUR_INDEX_M = 1000
# 海拔 0–250m 是平原与滩涂，等高线密集且对台风风雨分析无信息量，整段不画
CONTOUR_SKIP_BELOW = 500
CONTOUR_TOL = 0.5             # 等高线简化容差（画布像素）
# 目标体积上限（KB）：超了就提示调粗，避免前端塞一个几十 MB 的 JS
JS_BUDGET_KB = 1100

# ---- 高程点位 ----
# 只标「能代表地形骨架」的最高点：阈值 2500m（雪山/高山），
# 最小间距 150px（约 540km 地面，见比例尺推导）。
# 实测 1500m/62px 会打出 104 个点，在 2304px 宽的范围内密到互相压字，
# 反而看不清 —— 标注的价值在于「少而准」，不是「多而全」。
PEAK_MIN_H = 2500
PEAK_MIN_DIST_PX = 150

# ---- 分层设色色标（高程米 → RGB）----
# 参照 1:50万 分层设色表，但**整体压暗**。
# 本项目是暗色 UI：底图若用标准地形图的亮色调，整片华南丘陵会泛成一片土黄，
# 既压不住台风路径线（白色/红色），也让深色 UI 显得脏。
# 实测标准色表在暗底上偏亮偏黄，故整体降明度、略降饱和。
HYPSO = [
    (0,     (24, 54, 46)),      # 平原 / 海岸带
    (50,    (32, 68, 52)),
    (150,   (42, 82, 52)),      # 低丘
    (400,   (68, 88, 52)),      # 丘陵
    (800,   (96, 94, 56)),      # 低山
    (1500,  (112, 88, 58)),     # 中山
    (2500,  (104, 78, 62)),
    (3500,  (110, 92, 86)),     # 高山
    (5000,  (152, 150, 148)),   # 雪山
]
# 海面（海底为负高程 → 深蓝；只做很浅的一层深度提示，保持"海"的读图一致性）
SEA_MAX_DEPTH = 4000.0

HERE = os.path.dirname(os.path.abspath(__file__))
WEBAPP = os.path.abspath(os.path.join(HERE, '..', 'src', 'main', 'webapp'))
CACHE = os.path.join(HERE, '.cache')


# ================================ 基础工具 ================================

def load_dem():
    path = None
    for f in sorted(os.listdir(CACHE)):
        if f.startswith('dem-') and f.endswith('.npy'):
            path = os.path.join(CACHE, f)
    if not path:
        raise SystemExit('找不到 DEM 缓存，请先运行 tools/fetch-dem.py')
    grid = np.load(path).astype(np.float32)
    if np.isnan(grid).any():          # fetch-dem.py 已填补，这里兜底
        grid = np.nan_to_num(grid, nan=0.0)
    print('DEM    : %s  %s' % (os.path.basename(path), grid.shape))
    return grid


def box_blur(a, radius):
    """可分离盒式模糊（numpy 累积和实现，边缘用复制填充而非环绕）。

    环绕填充会在数组边界造出假的"山脊"，让晕渲出现一圈假阴影，故必须复制填充。
    """
    if radius <= 0:
        return a
    k = radius * 2 + 1
    pad = np.pad(a, ((radius, radius), (0, 0)), mode='edge')
    cs = np.cumsum(pad, axis=0)
    cs = np.concatenate([np.zeros((1, a.shape[1]), dtype=np.float64), cs], axis=0)
    out = (cs[k:, :] - cs[:-k, :]) / float(k)
    pad = np.pad(out.astype(np.float32), ((0, 0), (radius, radius)), mode='edge')
    cs = np.cumsum(pad, axis=1, dtype=np.float64)
    cs = np.concatenate([np.zeros((a.shape[0], 1)), cs], axis=1)
    return ((cs[:, k:] - cs[:, :-k]) / float(k)).astype(np.float32)


def smooth(a, radius, passes=3):
    """盒式模糊重复 3 次 ≈ 高斯。"""
    for _ in range(passes):
        a = box_blur(a, radius)
    return a


def downsample_mean(a, tw, th):
    """面积平均降采样（PIL BOX）。双线性会把海岸的负高程渗进陆地，
    在海陆交界处造出一圈假的低地色带，必须用面积平均。"""
    im = Image.fromarray(a, 'F')
    return np.asarray(im.resize((tw, th), Image.BOX), dtype=np.float32)


# ================================ 晕渲 ================================

def hillshade(grid, over, azimuth=315.0, altitude=42.0):
    """Horn 晕渲。

    azimuth  光源方位角（度，0=正北顺时针）。取 315°（西北）——
             制图惯例是光从左上来；投影计算里的默认 135° 看着像从右下来。
    altitude 光源高度角（度）。42° 略低于常用的 45°，斜坡层次感更强。
    z_factor 高程夸张。本项目是等距经纬投影（1°经≈26.8px / 1°纬≈27.1px，
             与真实的 1°纬111km、30°N 处 1°经96km 比例接近 1:1），
             取 1.0 保持几何不失真；1.2 只为让山脊更清晰。
    """
    H, W = grid.shape
    row_deg = RES_DEG * over
    lat = LAT_N + (LAT_S - LAT_N) * (np.arange(H) / float(H))
    # 每一行：水平一格的真实地面距离（WebMercator 行在纬度上近似等距）
    dx = (row_deg * 111320.0 * np.cos(np.radians(lat))).astype(np.float32)
    dy = float(row_deg * 110574.0)

    # np.gradient 返回 [d/d行, d/d列]，行号向下为南，取负号后指向"上坡方向"
    # dx 逐行不同（WebMercator 行的经度跨度随纬度收缩），须按列广播成 (H,1)
    gx = np.empty_like(grid)
    gy = np.empty_like(grid)
    gx[:, :-1] = (grid[:, 1:] - grid[:, :-1]) / dx[:, None]
    gx[:, -1] = gx[:, -2]
    gy[:-1, :] = (grid[1:, :] - grid[:-1, :]) / dy
    gy[-1, :] = gy[-2, :]

    slope = np.arctan(np.hypot(gx, gy))
    aspect = np.arctan2(-gx, gy)

    az = math.radians(360.0 - azimuth + 90.0)
    alt = math.radians(altitude)
    zf = 1.2
    shaded = (math.sin(alt) * np.cos(slope) +
              math.cos(alt) * np.sin(slope) * np.cos(az - aspect))
    shaded = np.clip(shaded * zf + (1.0 - zf) * 0.5, 0.0, 1.0)
    return shaded


def hypsometric(h):
    """高程 → 设色 RGB（float 0..1），逐档线性过渡。"""
    out = np.zeros(h.shape + (3,), dtype=np.float32)
    land = h > 0
    prev_h, prev_c = HYPSO[0][0], np.array(HYPSO[0][1], dtype=np.float32)
    for th, tc in HYPSO[1:]:
        tc = np.array(tc, dtype=np.float32)
        m = land & (h >= prev_h) & (h < th)
        t = np.clip((h - prev_h) / float(max(th - prev_h, 1)), 0, 1)
        for k in range(3):
            out[:, :, k] = np.where(m, prev_c[k] + t * (tc[k] - prev_c[k]), out[:, :, k])
        prev_h, prev_c = th, tc
    top = land & (h >= HYPSO[-1][0])
    for k in range(3):
        out[:, :, k] = np.where(top, HYPSO[-1][1][k], out[:, :, k])
    return out / 255.0


def render_rgb(grid, shade):
    """合成最终 RGB + 陆地掩膜：陆地 = 分层设色 × 晕渲明暗，海面透明。

    为什么海面要透明（而不是画成深蓝）：
    本项目约定「海面用容器底色提供，不用图层矩形」—— 缩到 15% 时可见范围
    有 5000 多像素宽，任何固定大小的矩形都铺不满。DEM 只覆盖
    92°E—178°E，位图若自带海色，就会在覆盖边界处出现一条
    "位图内的海色"与"容器底色的海"之间的硬分界线，缩放时一眼就能看出。
    因此位图**只画陆地**（海面 alpha=0），海色统一交给容器底色。

    返回 (rgb, land_mask)；land_mask 为 True 表示陆地。
    """
    land = grid > 0
    color = hypsometric(grid)

    # 陆地：设色乘晕渲（保留 0.40 的环境光，否则背阴面会全黑，
    # 把真实的地形细节一起吃掉）
    lit = (0.40 + 0.60 * shade)[:, :, None]
    color = np.where(land[:, :, None], color * lit, color)

    return np.clip(color * 255.0, 0, 255).astype(np.uint8), land


# ============================ 矢量等高线（marching squares） ============================

def marching_squares(field, level):
    """提取一条等值线，返回线段数组 segs: [(p0, p1), ...]（格点坐标）。

    16 查表法。格点四角按 a=左上 f00, b=右上 f10, c=右下 f11, d=左下 f01 编号，
    case 号 = (a>l)<<3 | (b>l)<<2 | (c>l)<<1 | (d>l)。
    边：0=顶 1=右 2=底 3=左。

    插值系数 t 必须夹在 [0,1]。DEM 里存在"两角高程几乎相等"的近退化格
    （尤其大片沿海滩涂），此时 t=(level-a)/(b-a) 会爆成上万，
    交点被甩到画布外，缝合后就是一条横贯全图的假等高线 —— 实测踩过。
    """
    f = field
    f00 = f[:-1, :-1]; f10 = f[:-1, 1:]; f01 = f[1:, :-1]; f11 = f[1:, 1:]
    cases = (((f00 > level).astype(np.int8) << 3) |
             ((f10 > level).astype(np.int8) << 2) |
             ((f11 > level).astype(np.int8) << 1) |
             (f01 > level).astype(np.int8))

    TBL = {
        0: (), 15: (),
        1: ((3, 0),), 2: ((0, 1),), 3: ((3, 1),),
        4: ((1, 2),), 5: ((3, 0), (1, 2)), 6: ((0, 2),),
        7: ((3, 2),), 8: ((2, 3),), 9: ((0, 2),),
        10: ((0, 1), (2, 3)), 11: ((1, 2),), 12: ((1, 3),),
        13: ((0, 1),), 14: ((3, 0),),
    }

    segs = []
    for case, edges in TBL.items():
        if not edges:
            continue
        ys, xs = np.nonzero(cases == case)
        if len(ys) == 0:
            continue
        v00 = f00[ys, xs].astype(np.float64); v10 = f10[ys, xs].astype(np.float64)
        v01 = f01[ys, xs].astype(np.float64); v11 = f11[ys, xs].astype(np.float64)
        fx = xs.astype(np.float64); fy = ys.astype(np.float64)

        def frac(a, b):
            """线性插值系数，夹在 [0,1]（见函数头注）。"""
            d = b - a
            d = np.where(np.abs(d) < 1e-9, 1e-9, d)
            return np.clip((level - a) / d, 0.0, 1.0)

        def pt(edge):
            if edge == 0:
                return fx + frac(v00, v10), fy
            if edge == 1:
                return fx + 1.0, fy + frac(v10, v11)
            if edge == 2:
                return fx + frac(v01, v11), fy + 1.0
            return fx, fy + frac(v00, v01)

        for e0, e1 in edges:
            ax, ay = pt(e0)
            bx, by = pt(e1)
            # 向量化成 (N,2) 数组再转成元组列表：逐点 Python 循环在
            # 上百万格点上要跑几十秒，这里是纯 numpy 一次算完
            segs.extend(zip(zip(ax.tolist(), ay.tolist()),
                            zip(bx.tolist(), by.tolist())))
    return segs


def stitch(segs, q):
    """把无序线段缝成连续折线。

    端点按 q 量化后建索引，端点落在同一格即视为同一条线的两端。
    每条线向两端各延伸一次；闭环（两端接不上）会自然收尾，不强接。
    """
    bucket = defaultdict(list)
    for i, (a, b) in enumerate(segs):
        bucket[(int(round(a[0] / q)), int(round(a[1] / q)))].append((i, 0))
        bucket[(int(round(b[0] / q)), int(round(b[1] / q)))].append((i, 1))

    used = bytearray(len(segs))
    lines = []
    for i0 in range(len(segs)):
        if used[i0]:
            continue
        used[i0] = 1
        pts = [segs[i0][0], segs[i0][1]]
        for direction in (1, 0):
            while True:
                tip = pts[-1] if direction == 1 else pts[0]
                nxt = None
                for (j, end) in bucket.get((int(round(tip[0] / q)), int(round(tip[1] / q))), ()):
                    if not used[j]:
                        nxt = (j, end)
                        break
                if nxt is None:
                    break
                j, end = nxt
                used[j] = 1
                a, b = segs[j]
                pt = b if end == 0 else a
                pts.append(pt) if direction == 1 else pts.insert(0, pt)
        if len(pts) >= 2:
            lines.append(pts)
    return lines


def rdp(pts, eps):
    """Douglas-Peucker（迭代实现，避免深递归）。"""
    n = len(pts)
    if n < 3:
        return pts
    keep = bytearray(n)
    keep[0] = keep[n - 1] = 1
    stack = [(0, n - 1)]
    while stack:
        i, j = stack.pop()
        if j <= i + 1:
            continue
        ax, ay = pts[i]
        dx = pts[j][0] - ax
        dy = pts[j][1] - ay
        seg = math.hypot(dx, dy)
        best, bi = -1.0, -1
        for k in range(i + 1, j):
            px, py = pts[k]
            d = (math.hypot(px - ax, py - ay) if seg < 1e-12
                 else abs(dy * (px - ax) - dx * (py - ay)) / seg)
            if d > best:
                best, bi = d, k
        if best > eps:
            keep[bi] = 1
            stack.append((i, bi))
            stack.append((bi, j))
    return [p for p, k in zip(pts, keep) if k]


def path_d(xy):
    """点列 -> path 片段，**相对增量**编码（M 后跟 l，走增量，整数坐标）。

    与 gen-wpac-coast.py 的 encode() 同一套思路。等高线点数是二十万级，
    用绝对坐标 "M1234 567L1230 560" 每点约 8 字节，增量后 "l-4 3" 约 5 字节，
    整体体积能砍掉三分之一以上（本项目实测 2304KB -> 约 1500KB）。
    增量能这么小，是因为等高线相邻点的屏幕距离本来就很小。
    """
    px = py = 0
    parts = []
    for i, (x, y) in enumerate(xy):
        xi = int(round(x))
        yi = int(round(y))
        if i == 0:
            parts.append('M%d %d' % (xi, yi))
        else:
            parts.append('l%d %d' % (xi - px, yi - py))
        px, py = xi, yi
    return ''.join(parts)


def contour_levels(hmax, coarse=1):
    """按分级等高距生成 [(高程, 是否计曲线), ...]。

    每档自带「是否计曲线」标记（CONTOUR_STEPS 的第三个元素），
    不再用 lv % CONTOUR_INDEX_M 反推 —— 反推在各级步长不同时会失效
    （500 步长下永远撞不上 1000 的倍数，实测计曲线数为 0）。
    """
    out = []
    for cap, step, is_idx in CONTOUR_STEPS:
        step = step * coarse
        lo = out[-1][0] if out else CONTOUR_SKIP_BELOW
        hi = min(cap * coarse, hmax)
        # 从「上一段最后一级的下一级」起算，避免两级区间交界处重复或跳级
        first = math.ceil(lo / float(step)) * step
        if first <= lo:
            first = lo + step
        lv = first
        while lv < hi + step * 0.5:
            out.append((lv, is_idx))
            lv += step
    return out


def build_contours(grid, tw, th, coarse=1):
    """在降采样后的工作网格上提取等高线 → 画布像素 path。"""
    hmax = float(grid.max())
    levels = contour_levels(hmax, coarse)
    print('等高线 : %d 级（%d 级为计曲线），分级等高距 %s' % (
        len(levels), sum(1 for _, i in levels if i),
        ' / '.join('%dm' % (s * coarse) for _, s, _ in CONTOUR_STEPS)))

    def to_xy(cx, cy):
        lon = LON_W + (LON_E - LON_W) * (cx / float(tw))
        lat = LAT_N + (LAT_S - LAT_N) * (cy / float(th))
        return (OX + (lon - LNG0) * KX, OY + (LAT0 - lat) * KY)

    out, n_idx, n_pt = [], 0, 0
    for li, (lv, is_idx) in enumerate(levels):
        segs = marching_squares(grid, float(lv))
        if not segs:
            continue
        # 缝合容差取半个工作格：小于它会缝不上（相邻格的端点差整整一格），
        # 大于它会把本不相邻的线接起来。
        lines = stitch(segs, q=0.55)
        # 剔除等深线：本级只关心陆地（高程 > 0）
        keep = []
        for ln in lines:
            step = max(len(ln) // 9, 1)
            s = 0
            cnt = 0
            for p in ln[::step]:
                # 下标必须夹紧：等值线端点可落在网格最后一格外侧
                yy = min(max(int(p[1]), 0), th - 1)
                xx = min(max(int(p[0]), 0), tw - 1)
                s += grid[yy, xx]
                cnt += 1
            if cnt and s / float(cnt) > 0:
                keep.append(ln)
        if not keep:
            continue
        parts = []
        for ln in keep:
            xy = [to_xy(p[0], p[1]) for p in rdp(ln, CONTOUR_TOL)]
            if len(xy) < 2:
                continue
            parts.append(path_d(xy))
            n_pt += len(xy)
        if parts:
            if is_idx:
                n_idx += 1
            # 每条折线各自以 M 开头，直接首尾相接即可拼成一个 d
            out.append((lv, 'is-index' if is_idx else 'is-normal', ''.join(parts)))
    return out, n_idx, n_pt


def build_peaks(grid, tw, th):
    """局部高点检测 → [(画布x, 画布y, 高程m), ...]。

    判定：该点高程是 3×3 邻域（含自身）的最大值 —— 用 pad + 9 次平移取 max
    实现，比逐点 3×3 比较快几个数量级（260 万点上就是秒级 vs 分钟级）。
    再按 PEAK_MIN_DIST_PX 贪心抽稀，避免在长山脊上打出密密麻麻的点。
    """
    H, W = grid.shape
    p = np.pad(grid, 1, mode='constant', constant_values=-np.inf)
    m = p[:-2, :-2].copy()
    for dy in range(3):
        for dx in range(3):
            np.maximum(m, p[dy:dy + H, dx:dx + W], out=m)

    ys, xs = np.nonzero((m == grid) & (grid >= PEAK_MIN_H))
    cand = [(float(grid[y, x]), x, y) for y, x in zip(ys.tolist(), xs.tolist())]
    cand.sort(key=lambda t: -t[0])

    def to_xy(cx, cy):
        lon = LON_W + (LON_E - LON_W) * (cx / float(W))
        lat = LAT_N + (LAT_S - LAT_N) * (cy / float(H))
        return (OX + (lon - LNG0) * KX, OY + (LAT0 - lat) * KY)

    picked = []
    for h, x, y in cand:
        px, py = to_xy(x, y)
        if all(math.hypot(px - a, py - b) >= PEAK_MIN_DIST_PX for a, b, _ in picked):
            picked.append((px, py, int(round(h))))
    picked.sort(key=lambda t: (round(t[1]), round(t[0])))
    return picked


# ================================ 主流程 ================================

def main():
    global OVER
    if '--over' in sys.argv:
        OVER = float(sys.argv[sys.argv.index('--over') + 1])
    coarse = int(sys.argv[sys.argv.index('--coarse') + 1]) if '--coarse' in sys.argv else 1

    raw = load_dem()

    # ---- 工作网格：先把 DEM 平滑再降采样到「画布像素密度 × OVER」----
    # 平滑必须放在降采样之前：DEM 单格噪声（约 1.2km 格距）直接晕渲会
    # 变成满屏假山脊，颜色也会出现椒盐噪点。
    print('预处理 : 平滑 + 降采样至 %gx 画布像素密度…' % OVER)
    bl = smooth(raw, radius=2)
    tw = int(round((LON_E - LON_W) * KX * OVER))
    th = int(round((LAT_N - LAT_S) * KY * OVER))
    work = downsample_mean(bl, tw, th)

    # ---- 1. 晕渲 + 分层设色 ----
    print('晕渲   : 计算坡度坡向…')
    rgb, land = render_rgb(work, hillshade(work, OVER))

    img_dir = os.path.join(WEBAPP, 'assets', 'terrain')
    os.makedirs(img_dir, exist_ok=True)
    img_path = os.path.join(img_dir, 'terrain-hillshade.png')
    # 海面透明（见 render_rgb 的说明）。alpha 不是硬 0/255，而是按
    # 「到陆地的距离」羽化 1 格 —— 硬边会让海岸线出现明显锯齿。
    lf = land.astype(np.float32)
    lf = smooth(lf, radius=1, passes=1)
    alpha = np.clip(lf * 1.25, 0.0, 1.0)
    rgba = np.dstack([rgb, (alpha * 255).astype(np.uint8)])

    im = Image.fromarray(rgba, 'RGBA')
    # 不做调色板量化：P 模式只支持「单一全透明索引」，无法逐像素表达
    # 海岸羽化 alpha（而 RGBA 量化又会把 alpha 混进调色板，导致海面发灰）。
    # 实测 RGBA 直接存 PNG 的体积可接受，故保持正确性优先。
    im.save(img_path, optimize=True)

    x0 = OX + (LON_W - LNG0) * KX
    y0 = OY + (LAT0 - LAT_N) * KY
    x1 = OX + (LON_E - LNG0) * KX
    y1 = OY + (LAT0 - LAT_S) * KY
    print('位图   : %d×%d  %.1f KB  覆盖画布 [%.0f,%.0f]–[%.0f,%.0f]' % (
        tw, th, os.path.getsize(img_path) / 1024.0, x0, y0, x1, y1))

    # ---- 2. 矢量等高线（直接用同一工作网格，坐标换算与位图严格同源）----
    contours, n_idx, n_pt = build_contours(work, tw, th, coarse)
    print('        %d 条（计曲线 %d），%d 个点' % (len(contours), n_idx, n_pt))

    # ---- 3. 高程点位 ----
    peaks = build_peaks(work, tw, th)
    print('高程点 : %d 个（≥%dm）' % (len(peaks), PEAK_MIN_H))

    # ---- 输出 JS ----
    body = ','.join('["%d","%s","%s"]' % (int(lv), cls, d) for lv, cls, d in contours)
    pk = ','.join('[%.0f,%.0f,%d]' % (a, b, c) for a, b, c in peaks)

    js = (
        '/* 自动生成，请勿手改 —— 由 tools/gen-terrain.py 产出\n'
        ' * 地形数据：AWS Terrain Tiles（Terrarium 编码）全球 DEM 拼接\n'
        ' *         高程来自 SRTM / NED / ASTER 等公开 DEM（见 tools/fetch-dem.py）\n'
        ' * 坐标系  ：与 js/ui.js 的 proj() 一致，坐标为画布像素\n'
        ' * pad     ：高程数据覆盖的画布像素范围 [x0,y0,x1,y1]\n'
        ' * hills   ：底图位图相对画布单位的像素比 [sx, sy]（即超采样倍数）\n'
        ' * interval：分级等高距 [[高程上限m, 间距m], ...]，供图例显示\n'
        ' * contour ：[高程m, css类, pathD]，等高距分级，见 tools/gen-terrain.py\n'
        ' * peaks   ：[x, y, 高程m]，局部高点\n'
        ' */\n'
        'window.ZFZ = window.ZFZ || {};\n'
        'ZFZ.geo = ZFZ.geo || {};\n'
        'ZFZ.geo.wterrain = {\n'
        '  pad: [%.0f, %.0f, %.0f, %.0f],\n'
        '  hills: [%.4f, %.4f],\n'
        '  interval: [%s],\n'
        '  indexAt: %d,\n'
        '  contour: [%s],\n'
        '  peaks: [%s]\n'
        '};\n'
        % (x0, y0, x1, y1,
           tw / (x1 - x0), th / (y1 - y0),
           ','.join('[%d,%d]' % (int(cap), int(step * coarse)) for cap, step, _ in CONTOUR_STEPS),
           CONTOUR_INDEX_M * coarse, body, pk)
    )
    out_js = os.path.join(WEBAPP, 'js', 'geo-wterrain.js')
    with open(out_js, 'w', encoding='utf-8', newline='\n') as f:
        f.write(js)
    kb = os.path.getsize(out_js) / 1024.0
    print('JS     : %s  %.1f KB' % (os.path.basename(out_js), kb))
    if kb > JS_BUDGET_KB:
        print('⚠ 超过体积预算 %.0f KB —— 重跑并加 --coarse 2 调粗等高距' % JS_BUDGET_KB)


if __name__ == '__main__':
    main()