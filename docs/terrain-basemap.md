# 地形图（terrain basemap）

「地形图」不是普通地图换个颜色，而是**带真实高程的独立底图**。本文说明它由什么组成、
数据从哪来、怎么改。

## 一、它由什么组成

制图学上表达地形的三个要素，本项目都做了：

| 要素 | 作用 | 实现 |
|---|---|---|
| **晕渲** hillshade | 立体感 | 由 DEM 的坡度/坡向算光照强度（方位角 315°、高度角 42°） |
| **分层设色** hypsometric tint | 高低分布 | 按高程分 9 档着色（绿→褐→白），色标见 `gen-terrain.py` 的 `HYPSO` |
| **矢量等高线** contour | 精确读高程 | marching squares 提取，**矢量故放大到 600% 也不糊** |

另加**高程点**（局部高点 + 高程数字）。

## 二、数据来源

真实 DEM，不是示意：

```
AWS Terrain Tiles（Terrarium 编码，RGB 编码高程）
  → tools/fetch-dem.py     抓取 92°E—178°E / 4°N—52°N（z7，672 张瓦片）
  → tools/gen-terrain.py   晕渲+设色位图、等高线、高程点
  → js/geo-wterrain.js     等高线与高程点（画布像素坐标）
  → assets/terrain/terrain-hillshade.png   晕渲+设色底图（RGBA，海面透明）
```

高程来自 SRTM / NED / ASTER 等公开 DEM。
已用玉山（3952m）、富士山（3776m）、泰山（1532m）、珠峰（8849m）实测校验。

## 三、改动流程

```bash
# 1. 抓 DEM（首次约 9 分钟；已缓存则秒过）
python tools/fetch-dem.py

# 2. 渲染 + 生成矢量数据（改 HYPSO / CONTOUR_STEPS / OVER 后重跑）
python tools/gen-terrain.py                 # 默认 2× 超采样
python tools/gen-terrain.py --over 1.5      # 降体积
python tools/gen-terrain.py --coarse 2      # 等高距整体放大（体积超预算时）

# 3. 验证
node tools/probe-terrain-map.cjs            # 32 项断言
node tools/shot-terrain-compare.cjs         # 出一张「改造前 vs 改造后」对照图

# 4. 同步到两个部署目录（见 MEMORY.md 的部署约定）
```

## 四、关键设计决定（都是实测踩出来的，别轻易改回去）

### 4.1 比例尺决定一切

画布 804px 横向覆盖约 2880km 地面，**1 画布像素 ≈ 3.6km**，属**大陆级小比例尺**。因此：

- 等高距必须**分级**且偏粗。统一 200m 在台湾中央山脉（坡度约 2°）上，
  相邻线地面间距仅 `200/tan(2°) ≈ 5.7km ≈ 1.6px`，会连成一片灰。
- 小比例尺地形图主要靠**晕渲 + 分层设色**表达地形，等高线只是补充。
- 当前分级：`<1500m: 1000m(计曲线)`、`1500–3000m: 500m(一般线)`、`>3000m: 1000m(计曲线)`。

### 4.2 等高线按比例尺渐进显示

CSS `.map-canvas:not([data-lod="0"]) .terr-contours path.is-normal { display: none }`
—— 缩小到只显示计曲线（每 1000m），放大后才放出一般等高线。

依据是制图学的「图上要素随比例尺增减」。固定全显等于把大比例尺的图上内容
套到小比例尺上，实测会把整片丘陵糊成白雾。

### 4.3 位图只画陆地，海面透明

`render_rgb()` 返回 `(rgb, land_mask)`，海面 `alpha=0`。

原因：项目约定「海面用容器底色，不用图层矩形」（缩到 15% 时可见范围有 5000+px，
任何固定矩形都铺不满）。位图若自带海色，会在 DEM 覆盖边界出现硬分界线。
alpha 还做了 1 格羽化，避免海岸锯齿。

### 4.4 SVG `<image>` 的 x/y 不能为负

本项目 DEM 的 `pad[0]=-348 / pad[1]=-548`，直接写进 `<image>` 会让元素**整体失效**
（请求发出、返回 200，但元素永远不渲染）。必须把负偏移挪到外层 `<g>` 的 translate：

```js
'<g transform="translate(-348 -548)"><image x="0" y="0" .../></g>'
```

### 4.5 按需加载，不进 index.html

`geo-wterrain.js` 约 1.2MB（等高线二十万点），默认底图用不到，
同步 `<script>` 会拖慢首屏。改为 `ui.loadWterrain()` 在**首次切到地形图**时注入脚本，
加载完**原地 replaceWith 地形图层**（`overview.js` 的底图切换处理）——
不整张重建地图，否则用户的缩放/平移与图层勾选态都会丢。

加载中 / 失败都有对应提示（`.map-canvas[data-terrain=loading|error]::after`），
不能静默——否则用户会看到一张纯绿陆地，又以为"地形图没生效"。

### 4.6 坐标必须与 proj() 同源

等高线、高程点、`pad` 全部是**画布像素坐标**，与 `ui.js` 的 `proj()` 一致，
因此地形层与海岸线层、经纬网共用同一个 `.map-view-g` 视野变换，缩放平移不会错位。
改投影参数必须同步改 `gen-terrain.py` 顶部的 `LNG0/LAT0/KX/KY/OX/OY`。

### 4.7 压缩

- 等高线用**相对增量编码**（`M123 456l4 -3`），比绝对坐标省三分之一以上。
- 高程点阈值 2500m、最小间距 150px（实测 1500m/62px 会打出 104 个点，密到互相压字）。

## 五、颜色同步（改一处必须改两处）

`gen-terrain.py` 的 `HYPSO` 与 `css/tokens.css` 的 `--hyps-0..4` 必须一一对应，
图例色带直接引用它们。色标是**按暗色 UI 压暗过的**（标准地形图色表在暗底上偏亮偏黄，
会压过台风路径线）。

## 六、已知取舍

- DEM 只覆盖 92°E—178°E / 4°N—52°N。范围外的陆地由海岸线图层的
  `--map-land-ter` 填充兜底（所以地形图下 `.map-land` **保留填充**，不是 `fill:none`）。
- 位图 3MB / 矢量 1.2MB。可接受，但若要再降只能牺牲分辨率或等高线密度。
- 海岸线、卫星云图两套底图**不含**地形（它们的设计意图就是纯色底图）。

## 七、回归

`tools/probe-terrain-map.cjs`（32 项）覆盖：按需加载触发、图层显隐、
位图尺寸与 pad 一致、等高线分级与 LOD 渐进、三套底图截图内容不同、
缩放后线宽不变、地形层与陆地共用同一变换、切底图不重建 DOM、控制台零错误。