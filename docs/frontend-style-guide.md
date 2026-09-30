# 追风者 · 前端代码规范

> 课程要求对应：阶段五「前端界面开发」📌 交付物 ——
> 提交前端代码规范文档（CSS 命名规则、JavaScript 注释标准）；
> ⚠ 评审要求 —— 前端代码需符合 BEM 命名规范，JS 需有完整 JSDoc 注释。

---

## 一、目录与文件职责

```
src/main/webapp/                  ← 前端根目录（部署后即 Web 根）
├── *.html                        10 个页面，只有结构骨架，不含业务代码
│                                 页面内不写 <script> 逻辑，也不写行内 style
├── css/
│   ├── tokens.css                第 1 层：设计令牌（颜色/字号/间距/圆角/阴影）
│   ├── app.css                   第 2 层：全站通用组件与布局系统
│   └── pages.css                 第 3 层：页面级 / 单页专用样式
├── js/
│   ├── data.js                   数据层：模拟数据（ZFZ.mock）
│   ├── api.js                    接口层：RESTful 契约（ZFZ.api）
│   ├── ui.js                     渲染层：无状态的 HTML 片段工厂（ZFZ.ui）
│   ├── layout.js                 骨架层：顶栏/导航/页头/页脚（ZFZ.layout）
│   └── pages/*.js                页面层：每页一个模块，只做"取数 + 拼装 + 绑事件"
├── assets/svg/                   静态矢量资源（地图底图、Logo）
└── vendor/                       第三方库本地副本（jQuery / Bootstrap）
```

### 引用顺序（不可调换）

```html
<link rel="stylesheet" href="vendor/bootstrap.min.css">
<link rel="stylesheet" href="css/tokens.css">      <!-- 必须先于 app.css -->
<link rel="stylesheet" href="css/app.css">         <!-- 必须先于 pages.css -->
<link rel="stylesheet" href="css/pages.css">

<script src="vendor/jquery.min.js"></script>
<script src="vendor/bootstrap.bundle.min.js"></script>
<script src="js/data.js"></script>                 <!-- 数据 -->
<script src="js/api.js"></script>                  <!-- 接口 -->
<script src="js/ui.js"></script>                   <!-- 渲染原语 -->
<script src="js/layout.js"></script>               <!-- 骨架 -->
<script src="js/pages/xx.js"></script>             <!-- 本页逻辑，最后 -->
```

---

## 二、CSS 命名规则

### 2.1 采用的 BEM 变体

标准 BEM 写作 `block__element--modifier`。本项目采用**业界常见的 BEM 变体**：
元素用**单个连字符**连接，修饰符统一用 `is-` 前缀，理由是
`.trow` / `.panel-head` 这类写法在长类名组合下可读性更好、也不与 Bootstrap 冲突。

| BEM 角色 | 本项目写法 | 示例 |
|---|---|---|
| Block（块） | `.名词` | `.panel`、`.tbl`、`.trow`、`.ty-item`、`.zbtn` |
| Element（元素） | `.块-元素` | `.panel-head`、`.panel-title`、`.zbar-fill`、`.cell-link` |
| Modifier（修饰符） | `.is-状态` | `.is-active`、`.is-narrow`、`.is-open`、`.is-danger` |
| 局部作用域块 | `.父前缀-子块` | `.drop-item`、`.dh-name`、`.ai-tile`、`.casualty-loss` |

**若答辩时老师严格要求 `__` / `--` 写法**，可做一次机械替换
（`panel-head → panel__head`、`is-active → panel--active`），需同步改 4 个 CSS
与 16 个 JS 文件，**属于组织层改动，不影响视觉与功能**。

### 2.2 命名硬性约定

| 规则 | 说明 |
|---|---|
| 全小写 + 连字符 | 不使用驼峰、下划线、大写字母 |
| 语义化，不用表现命名 | 用 `.panel-title` 而非 `.blue-bold-text` |
| 状态类一律 `is-` 开头 | `.is-hidden` / `.is-playing` / `.is-cleared` |
| 单页专用样式放 pages.css | 且以页面前缀收口，如 `.sandbox-*`、`.casualty-*` |
| 不使用 `!important` | 靠层级与选择器权重解决，全站 0 处使用 |
| 不使用 id 选择器写样式 | id 只用于 JS 取元素（如 `#btn-snapshot`） |
| 禁止行内 `style` 写静态样式 | 仅允许 JS 写入**运行时计算值**（如 `width:40%`、`transform`） |

### 2.3 与 Bootstrap 5 的类名避让（**重要**）

Bootstrap 已占用 `.card` / `.btn` / `.nav-item` / `.progress` / `.col` 等类名，
直接复用会被 Bootstrap 样式污染。本项目的避让方案：

| 我们用的类 | Bootstrap 同名类 | 避让原因 |
|---|---|---|
| `.panel` | `.card` | `.card` 自带边框/圆角/内边距，与设计稿不符 |
| `.zbtn` | `.btn` | `.btn` 自带高度/字体/内边距 |
| `.navlink` | `.nav-item` | 仅复用 `.nav` 布局，条目样式全部自写 |
| `.zbar` | `.progress` | `.progress` 自带 16px 高与背景条 |
| `.colstack` | `.col` | `.col` 依赖 `.row` 的负边距栅格，本项目用 Flex |

> 新增组件时**先确认类名是否被 Bootstrap 占用**（`vendor/bootstrap.min.css` 里搜一下），
> 占用就换名或加前缀。

### 2.4 设计令牌优先

颜色、字号、间距、圆角、阴影**一律取自 `tokens.css` 的 CSS 变量**，不写裸值：

```css
/* ✅ 正确 */
.panel-title { color: var(--ink); font-size: var(--fs-body); }

/* ❌ 禁止 */
.panel-title { color: #152A3D; font-size: 14px; }
```

主要令牌：

| 类别 | 令牌 | 值 |
|---|---|---|
| 品牌色 | `--brand` / `--brand-light` | `#0E5484` / … |
| 正文 | `--ink` / `--ink-2` / `--ink-3` | `#152A3D` / `#5B6B7B` / `#93A1B0` |
| 背景 | `--bg` / `--card` / `--row-alt` | `#F2F5F8` / `#FFFFFF` / `#F7F9FB` |
| 描边 | `--line` / `--divider` | `#E4E9EF` / `#D8E0E8` |
| 地图 | `--map-frame` | `#0E3B59` |
| 语义 | `--ok` / `--warn` / `--risk` | 绿 / 橙 / 红 |
| 圆角 | `--r-card` / `--r-inner` | 卡片 / 内部控制 |
| 间距 | `--gap-card` / `--pad-head` | 卡片间距 / 表头内边距 |
| 字重 | `--fw-medium` / `--fw-bold` | `500` / `700` |

> 例外：地图 SVG 内部的高程带、云絮渐变等**图形专用色**直接写在 SVG 里，
> 因为它们是画面内容而不是界面主题。

---

## 三、布局约定

### 3.1 页面骨架

```html
<div class="app">
  <header class="app-header">…</header>       <!-- 高 60px，由 layout.js 注入 -->
  <nav class="main-nav">…</nav>               <!-- 主导航，由 layout.js 注入 -->
  <main class="page-body">                    <!-- 内容区 -->
    <div class="page-title-bar">…</div>       <!-- 页标题条 -->
    <div class="colstack col-left">…</div>    <!-- 左列 -->
    <div class="colstack col-map">…</div>     <!-- 主列 -->
  </main>
</div>
```

### 3.2 列宽与自适应

| 类 | 宽度 | 用于 |
|---|---|---|
| `.col-left` | 264px | 左列（台风列表） |
| `.col-map` / `.col-main` | `flex: 1` | 主内容列，自适应 |
| `.col-side` | 340px（`.is-narrow` 320 / `.is-wide` 360） | 右侧信息列 |

- **设计基准宽度 1440px**，主内容列随窗口伸展；
- 列容器统一用 `.colstack`（纵向 Flex + `--gap-card` 间距）；

### 3.3 「替换元素不得只给 `inset: 0`」约定

地图等叠放图层必须**显式给出宽高**，否则 `<img>` 会退回固有尺寸、
与旁边按宽度缩放的 SVG 错位：

```css
.map-canvas { position: relative; aspect-ratio: 804 / 640; }   /* 锁定原始比例 */
.map-layer  { position: absolute; inset: 0; width: 100%; height: 100%; }
.map-canvas > img.map-layer { object-fit: cover; }             /* 与 SVG 的 slice 同语义 */
```

### 3.4 交互反馈四态

任何可点击元素必须给全 `cursor` / `:hover` / `:active` / `:focus-visible`：

```css
.zbtn { cursor: pointer; }
.zbtn:hover { background: #E8EEF4; }
.zbtn:active { transform: translateY(1px); }
.zbtn:focus-visible { outline: 2px solid var(--brand-light); outline-offset: 1px; }
```

---

## 四、JavaScript 规范

### 4.1 命名空间与模块职责

全局只暴露一个对象 `window.ZFZ`，其余全部封装在 IIFE 内：

| 模块 | 职责 | 允许依赖 |
|---|---|---|
| `ZFZ.mock` | 模拟数据（后端就绪后整体废弃） | 无 |
| `ZFZ.api` | 接口契约，唯一发请求的地方 | jQuery |
| `ZFZ.ui` | **纯函数**：入参 → HTML 字符串，不碰 DOM、不发请求 | 无 |
| `ZFZ.layout` | 顶栏/导航/页头骨架 + 全局交互（搜索/通知/用户菜单） | jQuery、ZFZ.ui |
| `js/pages/*.js` | 单页：取数 → 调用 `ZFZ.ui` 拼装 → 注入容器 → 绑事件 | jQuery、ZFZ.ui、ZFZ.api |

### 4.2 模块骨架（所有 JS 文件统一）

```js
/* ==========================================================================
   模块标题
   一句话说明本模块职责
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;                 // 常用依赖提到顶部，别处不再写前缀

  /** 函数级 JSDoc，见 4.4 */
  function someFunction(arg) { … }

  /* ---------------- 分区注释：用小节线分隔逻辑块 ---------------- */

  $(function () { … });           // 入口：DOM ready
})(window.jQuery);
```

### 4.3 命名约定

| 对象 | 规则 | 示例 |
|---|---|---|
| 函数 / 变量 | 小驼峰 | `mapCanvas`、`nextPhotoTransform` |
| 常量 | 全大写下划线 | `MW`、`MH`、`LNG0`、`RISK` |
| DOM id | 连字符 | `#btn-snapshot`、`#timeline-play` |
| CSS 类（JS 里选中的） | 同 CSS，连字符 | `.map-canvas`、`.zbar-fill` |
| 布尔量 | `is` / `has` 开头 | `isOpen`、`hasMore` |
| 页面模块 | 文件名 = 页面路由 | `forecast-disagreement.html` ↔ `pages/disagreement.js` |

### 4.4 JSDoc 注释标准

**(1) 文件头**：`@file` + `@description`，重要的还要 `@example`、`@see`。

```js
/**
 * @file 追风者 · 接口层（RESTful 契约）
 * @description 统一封装与后端 Servlet 的交互；开发阶段返回模拟数据。
 * @example
 *   ZFZ.api.getTracks(13).then(function (list) { … });
 */
```

**(2) 公共函数**：必须写 `@param` / `@returns`，返回值是 Promise 或复合对象时用 `@returns {Promise<{…}>}` 描述结构。

```js
/**
 * 查询台风的实况轨迹点序列。
 * @param {number|string} id 台风 id
 * @returns {Promise<Array<{time: string, lon: number, lat: number}>>} 轨迹点数组
 */
```

**(3) 私有函数**：加 `@private`，可省 `@param` 的详细描述。

**(4) 分区注释**：每个逻辑分区用一行小节线，不写无意义注释。

```js
/* ---------------- 左列：台风列表 ---------------- */
```

**(5) 禁止的注释**：`// 循环 i 从 0 到 n` 这类复述代码的注释；
注释要写「为什么这么做」，不是「做了什么」。

### 4.5 数据与 DOM 操作约定

| 约定 | 说明 |
|---|---|
| HTML 一律字符串拼装 | 不用模板引擎（课程禁用前端框架）；字符串拼接便于审查 |
| 输出用户/接口数据必须转义 | 统一用 `U.esc(str)`，防 XSS |
| 事件用委托绑定 | 绑在稳定的父容器（如 `#page-body`）上，避免重新渲染后失效 |
| 读 `data-*` 用 `.attr()` | **被 JS 改写过的 `data-*` 必须用 `.attr()` 读写**，`.data()` 会缓存首次值 |
| 不写行内 `onclick` | 会破坏 CSP，也无法统一管理 |
| 不定义全局变量 | 除 `window.ZFZ` 外不新增全局 |
| 图表用原生 SVG | 折线/柱状/环形均为函数返回的 SVG 片段，不引第三方图表库 |

### 4.6 接口调用约定

页面**只调用 `ZFZ.api` 的方法**，不直接写 `$.ajax`：

```js
// ✅ 正确
ZFZ.api.getForecastPaths(id).then(function (paths) { render(paths); });

// ❌ 禁止：页面直接请求，绕过统一错误处理与 mock 开关
$.get('/api/typhoons/' + id + '/forecast-paths', function (res) { … });
```

### 4.7 响应式与可访问性

- 适配断点：1440（设计基准）→ 1280 → 1024（平板横屏），主内容列收缩、列距变窄；
- 所有图标按钮给 `aria-label`，下拉触发器给 `aria-haspopup`；
- 键盘可达：下拉面板支持 `Esc` 关闭，可点元素用 `<button>` / `<a>` 而非 `<div>`。

---

## 五、提交前自检清单

- [ ] 新增样式是否用了 `tokens.css` 的变量？有没有裸色值？
- [ ] 新增类名是否与 Bootstrap 冲突？
- [ ] 这个样式该放 `app.css`（通用）还是 `pages.css`（单页）？
- [ ] 点击元素是否给全 `cursor` / `:hover` / `:active` / `:focus-visible`？
- [ ] 新写的 JS 函数是否有 JSDoc？文件头是否说明职责？
- [ ] 数据是否经 `U.esc()` 转义？
- [ ] 事件是否用委托绑定？
- [ ] 接口是否走 `ZFZ.api`？
- [ ] 是否引入了课程禁止的前端框架（Vue / React / ElementUI…）？
