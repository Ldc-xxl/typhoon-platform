# -*- coding: utf-8 -*-
"""
生成「西北太平洋海岸线」底图数据（P1 实况地图的底图本体）。

背景
----
设计稿原先附带三张 804×640 的底图（map-base.svg / -sat / -terrain），
但它们只是一张只覆盖 105°E—135°E 的实心图：真实台风常出现在 130°E—160°E
的远洋，fitBox() 为把整条路径纳入画布会把视野缩到 45%，
底图随之缩成一小块挤在角落、其余全是空海面；底图方块之外既没有经纬网、
也没有地名，观感上"地图是残的"。

因此改为**完全由真实海岸线生成底图**：本脚本产出一份覆盖范围远大于画布的
海岸线（默认约 45°E—239°E / 76°N—35°S），三套底图样式（普通 / 卫星 / 地形）
共用这一份几何，只在 CSS 里换配色。这样无论缩到多小、平移到哪里，
画面上都是一张完整的地图。

坐标系
------
与 js/ui.js 的 proj() 完全一致：
    x = 351 + (lon - 118.0894) * 26.79
    y = 198 + (24.4798 - lat) * 27.11
因此输出的是**画布像素坐标**，可直接写进 <path d="...">，
与矢量层共用同一个视野变换，不需要任何额外换算。

数据源
------
Natural Earth 1:50m / 1:110m 陆地（public domain），
经 world-atlas npm 包分发为 TopoJSON（含 delta 编码的 arcs）。

用法
----
    python tools/gen-wpac-coast.py [精度 50m|110m] [x0 y0 x1 y1]

不传范围时用脚本内默认值。范围的取舍标准是：**用户把视野缩到允许的最小
比例尺（ui.js 的 ZOOM_MIN）时，画布四角仍落在数据范围内**。
范围越大文件越大，因此不要盲目放大（见 main() 里的实测数据）。

输出
----
src/main/webapp/js/geo-wpac.js
    window.ZFZ.geo = { wpac: { pad: [x0,y0,x1,y1], land: ["M...Z", ...] } }
"""
import json
import math
import os
import sys
import urllib.request

# ---- 与 ui.js 保持一致的投影参数（改这里必须同步改 ui.js 的 MW/MH/LNG0/LAT0/KX/KY）----
LNG0, LAT0, KX, KY = 118.0894, 24.4798, 26.79, 27.11
OX, OY = 351, 198

# 需要生成的屏幕坐标范围（画布 804×640 之外的富余量，供缩放/平移时使用）。
# 默认值经实测选定：范围放到「缩到 ui.js 的 ZOOM_MIN（合成比例尺 15%）时，
# 画布四角仍落在数据内」，再大就是纯粹的体积浪费（外围陆地被 RDP 简化为
# 极少的点，实测继续外扩只多几 KB，但也确实用不到）。
EXTENT = [-1600, -1600, 3600, 2200]
X0, Y0, X1, Y1 = EXTENT

# 下面的量由 EXTENT 派生，configure() 会重算
LON_LO = LON_HI = LON_CENTER = 0.0

# 简化容差（屏幕像素）。0.7px 在 26.79px/° 的比例尺下约等于 0.026°，
# 肉眼不可辨，但能把点数压掉一大半。
TOL = 0.7

SRC = 'https://cdn.jsdelivr.net/npm/world-atlas@2/land-%s.json'
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, '.cache')
OUT = os.path.abspath(os.path.join(HERE, '..', 'src', 'main', 'webapp', 'js', 'geo-wpac.js'))


def configure(extent):
    """设定画布范围并派生经度区间（环按整 360° 平移时以其中心为归属依据）。"""
    global X0, Y0, X1, Y1, LON_LO, LON_HI, LON_CENTER
    X0, Y0, X1, Y1 = extent
    LON_LO = LNG0 + (X0 - OX) / KX
    LON_HI = LNG0 + (X1 - OX) / KX
    LON_CENTER = (LON_LO + LON_HI) / 2.0


def download(res):
    """下载（首次）并缓存 TopoJSON。"""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, 'land-%s.json' % res)
    if not os.path.exists(path):
        url = SRC % res
        print('  下载 %s ...' % url)
        req = urllib.request.Request(url, headers={'User-Agent': 'zfz-basemap-gen'})
        with urllib.request.urlopen(req, timeout=90) as r, open(path, 'wb') as f:
            f.write(r.read())
    return json.load(open(path, encoding='utf-8'))


def decode(topo):
    """TopoJSON -> [[ring, ring, ...], ...]，ring 为 [(lon, lat), ...]（首尾不重复）。"""
    tr = topo['transform']
    sx, sy = tr['scale']
    tx, ty = tr['translate']

    arcs = []
    for arc in topo['arcs']:
        pts, x, y = [], 0.0, 0.0
        for dx, dy in arc:
            x += dx
            y += dy
            pts.append((x * sx + tx, y * sy + ty))
        arcs.append(pts)

    def ring(idxs):
        out = []
        for i in idxs:
            a = arcs[i] if i >= 0 else arcs[~i][::-1]
            out.extend(a[1:] if out else a)
        return out

    polys = []
    for g in topo['objects']['land']['geometries']:
        if g['type'] == 'MultiPolygon':
            for poly in g['arcs']:
                polys.append([ring(r) for r in poly])
        elif g['type'] == 'Polygon':
            polys.append([ring(r) for r in g['arcs']])
    return polys


def unwrap(ring):
    """把一条环的经度展开成连续值。

    Natural Earth 的经度是 -180..180。跨日界线的陆地（楚科奇半岛）会在
    179 与 -179 之间跳变，欧亚大陆这种西起欧洲、东至白令海峡的巨型多边形
    首尾更是相差 350°。必须按相邻点差逐点展开，否则投影后会横贯全图拉出假边。
    """
    lons = [ring[0][0]]
    for lon, _ in ring[1:]:
        prev = lons[-1]
        while lon - prev > 180:
            lon -= 360
        while prev - lon > 180:
            lon += 360
        lons.append(lon)
    return lons


def median(xs):
    s = sorted(xs)
    return s[len(s) // 2]


def encode(pts):
    """点列 -> path 片段（M 开路，其后 l 走增量，整数坐标）。"""
    px = py = 0
    parts = []
    for i, (x, y) in enumerate(pts):
        xi, yi = int(round(x)), int(round(y))
        if i == 0:
            parts.append('M%d %d' % (xi, yi))
        else:
            parts.append('l%d %d' % (xi - px, yi - py))
        px, py = xi, yi
    parts.append('Z')
    return ''.join(parts)


def ring_xy(ring, tol):
    """一条环 -> 画布像素点列（展开 / 整体平移 / 矩形裁剪 / 简化）；落空返回 None。"""
    lons = unwrap(ring)
    # 把整环平移 360° 的整数倍，使其落在目标经度区间附近
    k = round((LON_CENTER - median(lons)) / 360.0)
    xy = [project(lons[i] + 360.0 * k, ring[i][1]) for i in range(len(ring))]

    xs = [p[0] for p in xy]
    ys = [p[1] for p in xy]
    if max(xs) < X0 or min(xs) > X1 or max(ys) < Y0 or min(ys) > Y1:
        return None

    pts = clip_rect(xy, X0, Y0, X1, Y1)
    if len(pts) < 3:
        return None
    pts = rdp(pts, tol)
    return pts if len(pts) >= 3 else None


def project(lon, lat):
    return (OX + (lon - LNG0) * KX, OY + (LAT0 - lat) * KY)


def clip_rect(pts, x0, y0, x1, y1):
    """Sutherland-Hodgman 矩形裁剪。

    不能用「逐点过滤 + Z 闭合」——那样跨边界的大陆会被切断后首尾直连，
    海里凭空多出斜三角。必须真正求出多边形与窗口边的交点。
    """
    def edge(pts, inside, cut):
        out = []
        for i, cur in enumerate(pts):
            prev = pts[i - 1]
            ci, pi = inside(cur), inside(prev)
            if ci:
                if not pi:
                    out.append(cut(prev, cur))
                out.append(cur)
            elif pi:
                out.append(cut(prev, cur))
        return out

    def cutx(a, b, x):
        t = (x - a[0]) / (b[0] - a[0])
        return (x, a[1] + t * (b[1] - a[1]))

    def cuty(a, b, y):
        t = (y - a[1]) / (b[1] - a[1])
        return (a[0] + t * (b[0] - a[0]), y)

    for pts, ok, cut in (
        (pts, lambda p: p[0] >= x0, lambda a, b: cutx(a, b, x0)),
        (pts, lambda p: p[0] <= x1, lambda a, b: cutx(a, b, x1)),
        (pts, lambda p: p[1] >= y0, lambda a, b: cuty(a, b, y0)),
        (pts, lambda p: p[1] <= y1, lambda a, b: cuty(a, b, y1)),
    ):
        pts = edge(pts, ok, cut)
        if not pts:
            return []
    return pts


def rdp(pts, eps):
    """Douglas-Peucker 简化（迭代实现，避免深递归）。"""
    n = len(pts)
    if n < 3:
        return pts
    keep = [False] * n
    keep[0] = keep[n - 1] = True
    stack = [(0, n - 1)]
    while stack:
        i, j = stack.pop()
        if j <= i + 1:
            continue
        ax, ay = pts[i]
        bx, by = pts[j]
        dx, dy = bx - ax, by - ay
        seg = math.hypot(dx, dy)
        best, bi = -1.0, -1
        for k in range(i + 1, j):
            px, py = pts[k]
            if seg < 1e-12:
                d = math.hypot(px - ax, py - ay)
            else:
                d = abs(dy * (px - ax) - dx * (py - ay)) / seg
            if d > best:
                best, bi = d, k
        if best > eps:
            keep[bi] = True
            stack.append((i, bi))
            stack.append((bi, j))
    return [p for p, k in zip(pts, keep) if k]


def build(res, tol):
    polys = decode(download(res))

    paths = []
    n_pt = 0
    for poly in polys:
        segs = []
        for r in poly:
            if len(r) < 3:
                continue
            pts = ring_xy(r, tol)
            if pts:
                segs.append(encode(pts))
                n_pt += len(pts)
        if segs:
            # 同一多边形的外环与孔洞必须合成一个 path：fill-rule="evenodd"
            # 不跨 path 生效，拆开的话里海、黑海会被填成陆地。
            paths.append(''.join(segs))

    return paths, n_pt


def main():
    res = sys.argv[1] if len(sys.argv) > 1 else '50m'
    extent = [int(v) for v in sys.argv[2:6]] if len(sys.argv) >= 6 else list(EXTENT)
    configure(extent)

    rings, n = build(res, TOL)
    body = ','.join('"%s"' % s for s in rings)
    js = (
        '/* 自动生成，请勿手改 —— 由 tools/gen-wpac-coast.py 产出\n'
        ' * 数据源：Natural Earth 1:%s 陆地（public domain）\n'
        ' * 坐标系：与 js/ui.js 的 proj() 一致，坐标为画布像素\n'
        ' * pad：[x0,y0,x1,y1] 数据覆盖的画布像素范围；云图层的纹理矩形与\n'
        ' *      文档都引用它，渲染陆地图层本身不依赖\n'
        ' */\n'
        'window.ZFZ = window.ZFZ || {};\n'
        'ZFZ.geo = { wpac: { pad: [%d,%d,%d,%d], land: [%s] } };\n'
        % (res.rstrip('m'), X0, Y0, X1, Y1, body)
    )
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write(js)

    print('精度   : 1:%s' % res)
    print('范围   : [%d, %d, %d, %d]（经度 %.1f°E—%.1f°E）' % (
        X0, Y0, X1, Y1, min(LON_LO, LON_HI), max(LON_LO, LON_HI)))
    print('环数   : %d' % len(rings))
    print('点数   : %d' % n)
    print('文件   : %s' % OUT)
    print('体积   : %.1f KB' % (os.path.getsize(OUT) / 1024.0))


if __name__ == '__main__':
    main()
