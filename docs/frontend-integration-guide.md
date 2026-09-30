# 前端数据驱动改造规范

> 本文件是「把页面从硬编码数据改为真实接口驱动」的唯一依据。
> 改造 10 个页面时，**所有实现必须以此为准**，不得各自发挥。

---

## 一、改造目标

改造前：页面在**模块顶层**直接读 `ZFZ.mock.*`（`js/data.js`）并用字面量拼 HTML，
`ZFZ.api.*` 的调用只是装饰（如按钮点击弹个 `alert`）。关掉后端页面照样"有数据"，
因此数据库断没断、接口通没通，从页面上完全看不出来。

改造后：页面所有业务数据来自 `ZFZ.api.*`，失败时**如实显示错误态**。

---

## 二、五条铁律

1. **不改视觉**。保留现有 HTML 结构、CSS 类名、DOM `id` 与 `data-*` 属性。
   改造只替换"数据来源"。页面看上去应当与改造前**几乎一样**，
   区别只在于数字变成了数据库里的真实值。
2. **不改组件库**。`js/ui.js`、`js/layout.js`、`css/*.css` 一律不动。
   组件不够用就用现有的组合，不要在组件库里加东西。
3. **不新增依赖**（唯一例外：ECharts）。`vendor/` 里是 jQuery、Bootstrap，
   以及**课程要求阶段四④明确指定**的 ECharts 5.5.1 —— 它已下载为
   `vendor/echarts.min.js` 本地副本，不走 CDN，断网也能出图。
   除 ECharts 外，**不要引入** bootstrap-table、axios 等任何新库。
4. **不做静默降级**。接口失败**禁止**回退到 `ZFZ.mock`。
   失败就渲染错误态 + 弹 `toast`。（这是明确的产品决策：故障必须可见。）
5. **不改 `js/api.js`**。接口层已经写好 60+ 方法，缺什么先确认是不是已有同名方法。

---

## 三、唯一权威的字段依据

**`C:\Users\17168\zfz-runtime\api_samples.txt`** 是后端接口的真实返回样本
（不是设计稿，是运行时抓的）。字段名、层级、量纲**一律以它为准**。

`docs/api.md` 是设计文档，若与样本冲突，**以样本为准**。

### 高频字段对照（改造时最容易写错的）

| 页面数据 | 后端字段 | 常见错误 |
|---|---|---|
| 台风中文名 | `nameCn` | 不是 `name` / `chineseName` |
| 台风英文名 | `nameEn` | 不是 `englishName` |
| 台风编号 | `code` | 实体层叫 `internationalId`，VO 层是 `code` |
| 中心位置文本 | `centerText` | 如 `"19.5°N 123.8°E"` |
| 最大风力 | `windLevel`（数字）/ `level`（文字，如 `"台风"`） | 两者不同，别混用 |
| 风险评估-城市名 | `cityName` | 不是 `city` |
| 风险评估-风力 | `windLevel`，但值是**字符串** `"1 级"` | 不是数字 |
| 风险评估-等级 | `riskLevel`（`"低"`）+ `level`（`"LOW"`） | 配色用 `U.riskColor(riskLevel)` |
| 风圈四象限 | `quad.ne / quad.se / quad.sw / quad.nw` | 是嵌套对象，不是扁平字段 |
| AI 各模型置信度 | `models[].confidence`，**0—100** | 不是 0—1 |
| AI 融合置信度 | `confidence`，**0—1** | 与上面同名但量纲不同 |
| AI 一致性 | `consistency`，**0—100** + `consistencyText` | |
| 相似度得分 | `score` / `similarityScore`，**0—1** | 展示要 ×100 再加 `%` |
| 问卷各比例 | `suppliesRate` 等，**0—100** | 直接加 `%` 即可，别再 ×100 |
| 数据源成功率 | `success`，字符串 `"100.0%"` | 已格式化，直接显示 |
| 回放起始时间 | `replay.startTime` | 页面原来硬编码了 `2026-09-13 08:00` |

---

## 四、页面 → 接口映射

| 页面 | 主要接口（`ZFZ.api.` 方法） |
|---|---|
| `overview.js` | `getTyphoons` `getTyphoonMeta` `getReplay` `getRiskAssessments` `getAiPredictions` |
| `archive.js` | `getTyphoons` `getTyphoonMeta` `downloadTracks` / `exportTracks` |
| `detail.js` | `getTyphoon` `getTracks` `getWindCircles` `getForecastPaths` `getDisagreement` `getDisaster` `getReplay` `exportTracks` |
| `risk.js` | `getRiskAssessments` `getCities` `getTyphoons` |
| `disagreement.js` | `getDisagreement` `getForecastPaths` `getTyphoons` |
| `similar.js` | `getSimilarCases` `scoreSimilarPair` `getTyphoons` |
| `sandbox.js` | `getSandboxScenarios` `getSandboxParams` `runSandbox` `getTyphoons` |
| `survey.js` | `getSurveyAgg` `submitSurvey` `getTyphoons` |
| `admin.js` | `getAdminDashboard` `getAdminTodos` `getAdminModels` `getAdminSettings` `getDataSources` `getSyncLogs` `getUsers` `getAdminFeedback` `login` |
| `help.js` | 无接口依赖（纯静态说明页），**保持原样** |

**主对象 id 约定**：页面默认操作台风 `id = 13`（当前活跃台风"飞燕"）。
若页面原代码里已有 `ref = 13` 之类的常量，保留它，但应优先用列表接口拿到的第一个活跃台风。

---

## 五、统一写法模板

### 5.1 错误提示

已存在，**直接复用，不要另写**：

```js
ZFZ.layout.toast('保存失败：' + err.message);
```

`err.message` 由 `api.js` 统一翻译过，已经可直接展示给用户
（例如「无法连接后端服务（/typhoon_platform），请确认 Tomcat 已启动」）。

### 5.2 页面骨架（必须照此结构）

```js
(function ($) {
  'use strict';

  var U = ZFZ.ui;                      // 注意：不再需要 var M = ZFZ.mock;

  /* -------- 渲染函数：一律接收数据作为参数，不再在内部读 mock -------- */

  function someCard(data) {
    if (!data) { return U.panel({ title: 'xxx', body: '<div class="empty-row">暂无数据</div>' }); }
    return U.panel({ title: 'xxx', body: '...' + U.esc(data.xxx) + '...' });
  }

  /* -------- 错误态 -------- */
  function errorPanel(title, message) {
    return U.panel({
      title: title,
      cls: 'is-tight',
      body: '<div class="empty-row">加载失败：' + U.esc(message) +
            '<br>请确认后端服务已启动后刷新页面</div>'
    });
  }

  /* -------- 入口：先渲染骨架，再拉数据，再填充 -------- */
  $(function () {
    ZFZ.layout.mount('xxx');
    $('#page-title').html(ZFZ.layout.titleBar({ title: '…', desc: '…' }));

    // 1) 先用"加载中"占位渲染出整体布局，避免白屏
    $('#page-body').html(
      '<div class="colstack col-main">' + U.panel({ title: '…', body: '<div class="empty-row">加载中…</div>' }) + '</div>'
    );

    // 2) 拉数据
    ZFZ.api.getSomething(13)
      .then(function (data) {
        $('#page-body').html(realCards(data));   // 用真实数据重渲染
        bind();                                  // 数据到位后再绑定交互
      })
      .catch(function (err) {
        $('#page-body').html(errorPanel('…', err.message));
        ZFZ.layout.toast('加载失败：' + err.message);
      });
  });
})(window.jQuery);
```

### 5.3 多个接口并发

```js
Promise.all([
  ZFZ.api.getTyphoon(13),
  ZFZ.api.getTracks(13),
  ZFZ.api.getWindCircles(13)
]).then(function (res) {
  var typhoon = res[0], tracks = res[1], circles = res[2];
  $('#page-body').html(render(typhoon, tracks, circles));
}).catch(function (err) { /* 同上错误态 */ });
```

> **注意**：`Promise.all` 只要有一个失败就整体 reject。若某个子资源允许为空
> （例如 `disagreement` 可能返回 `null`），请用 `.catch(function(){ return null; })`
> 单独兜住，再放进 `Promise.all`，否则"没有分歧数据"会被误报成"接口挂了"。

### 5.4 空数据

用页面已存在的 `.empty-row` 类（CSS 已有），例如：

```js
'<div class="empty-row">当前无活跃台风在编</div>'
```

不要自己造新的空态样式。

### 5.5 数值格式化

- 保留 1 位小数：`Number(v).toFixed(1)`
- 百分比（后端已是 0—100）：`v.toFixed(1) + '%'`
- 0—1 的得分要展示成百分比：`(v * 100).toFixed(1) + '%'`
- 所有插入 DOM 的**字符串**都要过 `U.esc()`（组件库里已有），防 XSS。

---

## 六、逐页面验收清单

改造完成后，每个页面都要能通过：

1. **后端开着** → 页面显示真实数据；改数据库里一条记录并刷新，页面跟着变。
2. **后端关掉**（停 Tomcat）→ 页面显示错误态 + toast，**不出现** mock 数据。
3. **控制台零报错**（`F12 → Console` 无红色）。
4. **不出现 `ZFZ.mock` 关键字**（`grep -n "ZFZ.mock\|M\." 页面文件` 应为空）。
5. 页面原有的交互（Tab 切换、筛选、搜索、播放、拖拽）仍然可用。
6. JS 注释保持 JSDoc 风格（课程要求阶段五：JS 需有完整 JSDoc 注释）。

---

## 七、完成后必须自检

```bash
# 1) 语法检查
"C:/Users/17168/.workbuddy/binaries/node/versions/22.22.2-3/node.exe" --check js/pages/xxx.js

# 2) 确认已不再依赖 mock
grep -n "ZFZ\.mock" js/pages/xxx.js

# 3) 同步到运行中的 Tomcat（改完务必执行，否则页面看到的还是旧文件）
cp js/pages/xxx.js /c/Users/17168/zfz-runtime/apache-tomcat-9.0.98/webapps/typhoon_platform/js/pages/xxx.js
```
