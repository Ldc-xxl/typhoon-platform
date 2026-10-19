/* ==========================================================================
   追风者 · 通用 UI 组件与地图图层渲染
   组件类型与设计稿图层一一对应：卡片 / 表格 / 指标块 / 徽标 / 列表项 / 地图图层 / 图表
   所有尺寸与色值均取自 Ardot 设计稿（fileId 728955385585345）
   ========================================================================== */
window.ZFZ = window.ZFZ || {};

ZFZ.ui = (function () {
  'use strict';

  /* ---------------- 图标（几何取自设计稿导出 SVG） ---------------- */
  var icons = {
    logo: '<svg class="brand-logo" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg">' +
      '<circle fill="#0E5484" cx="16" cy="16" r="16"/>' +
      '<path fill="#FFF" transform="matrix(1 0 0 1 10.4 9.5)" d="M5.6 -0.9Q8.6728 -0.9 10.8364 1.2636Q13 3.4272 13 6.5Q13 9.5728 10.8364 11.7364Q8.6728 13.9 5.6 13.9Q3.5874 13.9 1.8584 12.8895Q0.1813 11.9094 -0.7789 10.2509Q-0.9773 9.9299 -0.8696 9.5681Q-0.7829 9.2008 -0.4509 9.0211Q-0.1299 8.8227 0.2319 8.9304Q0.5992 9.0171 0.7789 9.3491Q1.5005 10.5955 2.7666 11.3355Q4.0748 12.1 5.6 12.1Q7.9272 12.1 9.5636 10.4636Q11.2 8.8272 11.2 6.5Q11.2 4.1728 9.5636 2.5364Q7.9272 0.9 5.6 0.9Q5.2227 0.9109 4.9636 0.6364Q4.6891 0.3773 4.7 0Q4.6891 -0.3773 4.9636 -0.6364Q5.2227 -0.9109 5.6 -0.9Z" fill-rule="evenodd"/>' +
      '<path fill="#7FC4E8" transform="matrix(1 0 0 1 9.5 9.5)" d="M6.5 13.9Q3.4272 13.9 1.2636 11.7364Q-0.9 9.5728 -0.9 6.5Q-0.9 3.4272 1.2636 1.2636Q3.4272 -0.9 6.5 -0.9Q8.5126 -0.9 10.2416 0.1105Q11.9187 1.0906 12.8789 2.7491Q13.0773 3.0701 12.9696 3.4319Q12.8829 3.7992 12.5509 3.9789Q12.2299 4.1773 11.8681 4.0696Q11.5008 3.9829 11.3211 3.6509Q10.5995 2.4045 9.3334 1.6645Q8.0252 0.9 6.5 0.9Q4.1728 0.9 2.5364 2.5364Q0.9 4.1728 0.9 6.5Q0.9 8.8272 2.5364 10.4636Q4.1728 12.1 6.5 12.1Q6.8773 12.0891 7.1364 12.3636Q7.4109 12.6227 7.4 13Q7.4109 13.3773 7.1364 13.6364Q6.8773 13.9109 6.5 13.9Z" fill-rule="evenodd"/>' +
      '<circle fill="#FFF" transform="matrix(1 0 0 1 14 14)" cx="2" cy="2" r="2"/></svg>',

    search: '<svg width="14" height="14" viewBox="0 0 14 14" xmlns="http://www.w3.org/2000/svg">' +
      '<circle fill="none" stroke="#93A1B0" stroke-width="1.4" cx="6.125" cy="6.125" r="4.375"/>' +
      '<path fill="#93A1B0" d="M10.12 9.13L13.1825 12.1925Q13.3959 12.3941 13.3875 12.6875Q13.3959 12.9809 13.1825 13.1825Q12.9809 13.3959 12.6875 13.3875Q12.3941 13.3959 12.1925 13.1825L9.13 10.12Q8.9166 9.9184 8.925 9.625Q8.9166 9.3316 9.13 9.13Q9.3316 8.9166 9.625 8.925Q9.9184 8.9166 10.12 9.13Z" fill-rule="evenodd"/></svg>',

    bell: '<svg width="16" height="16" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">' +
      '<path fill="#5B6B7B" transform="matrix(1 0 0 1 2.72 2)" d="M0.52 4.24Q0.52 2.188 1.8701 0.8014Q3.2347 -0.6 5.28 -0.6Q7.3253 -0.6 8.6899 0.8014Q10.04 2.188 10.04 4.24L10.04 6.8345L11.1097 9.2795Q11.2171 9.507 11.1187 9.7386Q11.0338 9.9755 10.8005 10.0697Q10.7436 10.0946 10.6828 10.1073Q10.6221 10.12 10.56 10.12L0 10.12Q-0.2515 10.1272 -0.4243 9.9443Q-0.6072 9.7715 -0.6 9.52Q-0.6 9.4579 -0.5873 9.3972Q-0.5746 9.3364 -0.5497 9.2795L0.52 6.8345L0.52 4.24ZM1.72 4.24L1.72 6.96Q1.72 7.0221 1.7073 7.0828Q1.6946 7.1436 1.6697 7.2005L0.9174 8.92L9.6426 8.92L8.8903 7.2005Q8.8654 7.1436 8.8527 7.0828Q8.84 7.0221 8.84 6.96L8.84 4.24Q8.84 2.6757 7.8301 1.6386Q6.8189 0.6 5.28 0.6Q3.7411 0.6 2.7299 1.6386Q1.72 2.6757 1.72 4.24Z" fill-rule="evenodd"/>' +
      '<path fill="#5B6B7B" transform="matrix(1 0 0 1 6.56 13.28)" d="M0.5684 -0.1921Q0.6632 0.0883 0.9036 0.2608Q1.1441 0.4334 1.44 0.4334Q1.7359 0.4334 1.9764 0.2608Q2.2168 0.0883 2.3116 -0.1921Q2.3852 -0.4327 2.6139 -0.5378Q2.8361 -0.6558 3.0721 -0.5684Q3.3127 -0.4948 3.4178 -0.2661Q3.5358 -0.0439 3.4484 0.1921Q3.2301 0.8382 2.676 1.2358Q2.122 1.6334 1.44 1.6334Q0.758 1.6334 0.204 1.2358Q-0.3501 0.8382 -0.5684 0.1921Q-0.6558 -0.0439 -0.5378 -0.2661Q-0.4327 -0.4948 -0.1921 -0.5684Q0.0439 -0.6558 0.2661 -0.5378Q0.4948 -0.4327 0.5684 -0.1921Z" fill-rule="evenodd"/></svg>',

    play: '<svg width="12" height="16" viewBox="0 0 12 16" xmlns="http://www.w3.org/2000/svg">' +
      '<path fill="#FFF" d="M0.9 1.2L11 8L0.9 14.8Z"/></svg>',

    pause: '<svg width="12" height="16" viewBox="0 0 12 16" xmlns="http://www.w3.org/2000/svg">' +
      '<path fill="#FFF" d="M1.5 1.4L4.9 1.4L4.9 14.6L1.5 14.6ZM7.1 1.4L10.5 1.4L10.5 14.6L7.1 14.6Z"/></svg>',

    download: '<svg width="12" height="12" viewBox="0 0 12 12" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M6 1L6 7.6M6 7.6L3.4 5M6 7.6L8.6 5" stroke="#FFF" stroke-width="1.4" stroke-linecap="round" fill="none"/>' +
      '<path d="M1.6 9.4L1.6 10.4L10.4 10.4L10.4 9.4" stroke="#FFF" stroke-width="1.4" stroke-linecap="round" fill="none"/></svg>',

    chevron: '<svg width="8" height="5" viewBox="0 0 8 5" xmlns="http://www.w3.org/2000/svg">' +
      '<path d="M1 1L4 4L7 1" stroke="#93A1B0" stroke-width="1.3" stroke-linecap="round" fill="none"/></svg>'
  };

  /* ---------------- 基础工具 ---------------- */
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function tag(text, cls) {
    return '<span class="tag ' + (cls || 'is-neutral') + '">' + esc(text) + '</span>';
  }

  /* ---------------- 空态 / 错误态 / 加载态 ----------------
     原先 8 个页面各自内联了同一段「加载失败」正文（共 11 份），
     文案与转义方式必须逐字一致 —— 否则会出现"有的页面把后端错误里的
     引号原样吐进 HTML"这类不一致。统一收拢到这里，各页只保留调用点。

     注意三个函数的产出**逐字节相同**于被替换掉的内联写法：
       emptyRow(t)   === '<div class="empty-row">' + esc(t) + '</div>'
       errorBody(m)  === 上述形态 + '加载失败：' 前缀 + '后刷新页面' 尾注
       loadingRow()  === emptyRow('加载中…')
     因此这次整合是纯粹的去重，不改变任何像素或文案。          */
  function emptyRow(text) {
    return '<div class="empty-row">' + esc(text) + '</div>';
  }

  function errorBody(message) {
    return '<div class="empty-row">加载失败：' + esc(message) +
      '<br>请确认后端服务已启动后刷新页面</div>';
  }

  function errorPanel(title, message) {
    return panel({ title: title, cls: 'is-tight', body: errorBody(message) });
  }

  function loadingRow() {
    return emptyRow('加载中…');
  }

  /* ---------------- 数值与时间格式化 ----------------
     这些是纯换算，跨页面语义必须一致，因此收拢到组件库。

     注意：各页仍有**同名但语义不同**的展示型助手，例如
     overview.js 的 `num(v)`（null → '—'）与 sandbox.js 的 `num(v)`
     （补一位小数），它们**不属于**本组、已刻意保留在各页，不要并入。 */
  function num(v, fallback) {
    var n = Number(v);
    if (isFinite(n)) { return n; }
    return fallback === undefined ? null : fallback;
  }

  function fixed1(v) {
    if (v === null || v === undefined || v === '') { return '—'; }
    var n = Number(v);
    return isNaN(n) ? '—' : n.toFixed(1);
  }

  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  function fmtTime(s, withTime) {
    if (!s) { return '—'; }
    var m = String(s).match(/^(\d{4}-\d{2}-\d{2})(?:[ T](\d{2}:\d{2}))?/);
    if (!m) { return String(s); }
    return withTime ? (m[1] + ' ' + (m[2] || '00:00')) : m[1];
  }

  /* ---------------- 台风强度分级（中国气象局标准） ----------------
     分级表原先放在 js/data.js（演示数据层）里，导致本组件库必须依赖 mock 才能给
     台风列表染色。分级是纯粹的换算规则、不是业务数据，因此搬进来作为组件库自有的
     静态表，让 ui.js 彻底摆脱对 data.js 的依赖。
     相比原表补上了「热带低压」（< 8 级）档 —— 原表从 8 级起判，低于 8 级会被
     误判成「热带风暴」，颜色与文字都不对。热带低压无专属配色，沿用中性灰。      */
  var LEVELS = [
    { name: '热带低压',   cls: 'is-neutral', min: 0,  max: 7 },
    { name: '热带风暴',   cls: 'is-ts',      min: 8,  max: 9 },
    { name: '强热带风暴', cls: 'is-sts',     min: 10, max: 11 },
    { name: '台风',       cls: 'is-ty',      min: 12, max: 13 },
    { name: '强台风',     cls: 'is-sty',     min: 14, max: 15 },
    { name: '超强台风',   cls: 'is-super',   min: 16, max: 17 }
  ];

  function levelOf(windLevel) {
    var n = Number(windLevel);
    if (isNaN(n)) { return LEVELS[0]; }
    for (var i = LEVELS.length - 1; i >= 0; i--) {
      if (n >= LEVELS[i].min) { return LEVELS[i]; }
    }
    return LEVELS[0];
  }

  /* ---------------- 卡片 ---------------- */
  function panel(o) {
    var head = '';
    if (o.title) {
      var right = o.sub ? '<span class="panel-sub">' + esc(o.sub) + '</span>' : (o.headExtra || '');
      head = '<div class="panel-head"><span class="panel-title' + (o.titleLg ? ' is-lg' : '') + '">' +
        esc(o.title) + '</span>' + right + '</div>';
    }
    return '<section class="panel' + (o.cls ? ' ' + o.cls : '') + '"' +
      (o.id ? ' id="' + o.id + '"' : '') + '>' + head + (o.body || '') + '</section>';
  }

  /* ---------------- 表格（列宽取自设计稿） ---------------- */
  function table(o) {
    var cols = o.cols;
    var head = (o.head || []).map(function (h) {
      return '<span>' + esc(h) + '</span>';
    }).join('');

    var rows = (o.rows || []).map(function (r, i) {
      var cls = 'trow' + (o.alt !== false && i % 2 === 0 ? ' is-alt' : '');
      var cells = r.map(function (c) {
        if (typeof c === 'string') { return '<span>' + esc(c) + '</span>'; }
        var tone = c.tone ? ' ' + c.tone : '';
        return '<span class="' + (c.cls || '') + tone + '"' + (c.style ? ' style="' + c.style + '"' : '') + '>' +
          (c.html || esc(c.t)) + '</span>';
      }).join('');
      return '<div class="' + cls + '">' + cells + '</div>';
    }).join('');

    return '<div class="tbl" style="--cols:' + cols + '">' +
      (head ? '<div class="tbl-head">' + head + '</div>' : '') + rows + '</div>';
  }

  /* ---------------- 指标块 ---------------- */
  function metricRow(items) {
    return '<div class="metric-row">' + items.map(function (m) {
      return '<div class="metric-block">' +
        '<span class="metric-label">' + esc(m.label) + '</span>' +
        '<span class="metric-value"' + (m.color ? ' style="color:' + m.color + '"' : '') + '>' + esc(m.value) + '</span>' +
        (m.sub ? '<span class="metric-sub">' + esc(m.sub) + '</span>' : '') +
        '</div>';
    }).join('') + '</div>';
  }

  function metricGrid(items) {
    return '<div class="metric-grid is-param">' + items.map(function (m) {
      return '<div class="metric-tile is-param' + (m.cls ? ' ' + m.cls : '') + '"' +
        (m.key ? ' data-metric="' + esc(m.key) + '"' : '') + '>' +
        '<span class="metric-value"' + (m.color ? ' style="color:' + m.color + '"' : '') + '>' + esc(m.value) + '</span>' +
        '<span class="metric-label">' + esc(m.label) + '</span>' +
        '</div>';
    }).join('') + '</div>';
  }

  /* ---------------- 列表项 ---------------- */
  /* 左文右值（P7 采集设置、P2 相似案例、P3 分歧量化） */
  function simpleRows(items) {
    return '<div class="rows">' + items.map(function (it) {
      var cls = it.cls === undefined ? 'txt-brand' : it.cls;
      return '<div class="row-item is-sm"><span class="row-name">' + (it.kHtml || esc(it.k)) + '</span>' +
        '<span class="row-value ' + cls + '">' + (it.vHtml || esc(it.v)) + '</span></div>';
    }).join('') + '</div>';
  }

  /* 标签在上、值在下的堆叠行（P8 档案统计） */
  function kvLines(items) {
    return '<div class="kv-lines">' + items.map(function (it) {
      return '<div class="kv-line"><span class="kv-line-k">' + esc(it.k) + '</span>' +
        '<span class="kv-line-v">' + (it.vHtml || esc(it.v)) + '</span></div>';
    }).join('') + '</div>';
  }

  /* 参数名 + 说明 + 右侧取值（P10 system_settings） */
  function paramRows(items) {
    return '<div class="param-rows">' + items.map(function (it) {
      return '<div class="param-row"><span class="param-left">' +
        '<span class="param-key">' + esc(it.key) + '</span>' +
        '<span class="param-desc">' + esc(it.desc) + '</span></span>' +
        '<span class="param-value">' + esc(it.value) + '</span></div>';
    }).join('') + '</div>';
  }

  /* 双行信息项（P5 灾情档案、P10 安全策略） */
  function stackItems(items) {
    return '<div class="stack-list">' + items.map(function (it) {
      return '<div class="stack-item">' +
        '<span class="stack-title">' + esc(it.title) + '</span>' +
        (it.desc ? '<span class="stack-desc">' + esc(it.desc) + '</span>' : '') +
        (it.meta ? '<span class="stack-meta">' + esc(it.meta) + '</span>' : '') +
        '</div>';
    }).join('') + '</div>';
  }

  /* 提示条 */
  function note(html, cls) {
    return '<div class="note' + (cls ? ' ' + cls : '') + '">' + html + '</div>';
  }

  /**
   * 「派生分析已自动选台」提示条。
   *
   * <p>AI 测算 / 预报分歧 / 风险预评估这三类结果，最新的活跃台风未必算得出来
   * （对外可用预报机构只有中央气象台一家，凑不出多机构融合；风险评估按真实风圈推算，
   * 远海台风半径内没有城市就是空列表）。此时页面会改选到"最近一条算得出来的台风"
   * （选择规则见 {@link ZFZ.api.analyzeTarget}）——**必须显式说明已经切换**，
   * 否则用户会误以为看到的就是最新台风的结果，那比空态更容易误导。
   *
   * @param {Object} info {@link ZFZ.api.analyzeTarget} 的返回值
   * @param {string} [why] 为什么当前台风算不出来（各页原因不同，由页面给出）
   * @returns {string} 提示条 HTML；未发生切换时返回空串
   */
  function switchNote(info, why) {
    if (!info || !info.switched || !info.base || !info.typhoon) { return ''; }
    var b = info.base, t = info.typhoon;
    var bName = (b.nameCn || b.nameEn || '该台风') + (b.code ? ' ' + b.code : '');
    var tName = (t.nameCn || t.nameEn || '—') + (t.code ? ' ' + t.code : '');
    return note('<b>已自动切换台风</b>：最新活跃台风「' + esc(bName) + '」' +
      esc(why || '暂无法得出该分析结果') + '；以下展示最近一条可用结果 —— 「' + esc(tName) + '」。');
  }

  /* 进度条 */
  function barLine(pct, color, text) {
    return '<div class="bar-line"><span class="bar"><i style="width:' + pct + '%;background:' + (color || 'var(--brand)') + '"></i></span>' +
      '<span class="bar-value"' + (color ? ' style="color:' + color + '"' : '') + '>' + esc(text) + '</span></div>';
  }

  /* 选项胶囊组（P4 检索条） */
  function chips(items) {
    return '<div class="chips">' + items.map(function (c) {
      return '<span class="chip"><i class="chip-k">' + esc(c.k) + '</i><b class="chip-v">' + esc(c.v) + '</b></span>';
    }).join('') + '</div>';
  }

  /* 检索条件块（P8） */
  function fieldBox(label, value, hint) {
    return '<div class="field-box"><span class="field-box-label">' + esc(label) + '</span>' +
      '<span class="field-box-value' + (hint ? ' is-hint' : '') + '">' + esc(value) + '</span></div>';
  }

  /**
   * 生成下拉框的 {@code <option>} 片段（含可选分组）。
   *
   * <p>单独抽出来是为了让页面能<b>只换选项、不换 select 本体</b>：
   * 换台风后基准时次列表会变，若连 select 一起重建，用户刚刚展开的那个控件
   * 会被替换掉（焦点与展开状态丢失，看起来像"点了没反应"）。只换 innerHTML
   * 则原元素与已绑定的事件都还在。
   *
   * <p>选项值统一转成字符串再比较：后端年份是数字、海域与状态是字符串，
   * 若直接与 `selected` 做 `===`，数字 2026 与字符串 '2026' 会被判为不等，
   * 回填选中项就会静默失效。
   *
   * <p><b>分组约定</b>：选项可带 `group` 字段输出成 {@code <optgroup>}，
   * 同一组的选项必须<b>连续排列</b>——本函数遇到 group 变化就闭合上一个组，
   * 回头再出现同名 group 会生成两个同名 optgroup，看着像 bug。
   * 全部不传 `group` 时输出与不带分组功能的旧版完全一致。
   *
   * @param {Array<{value: (string|number), label: string, group: (string|undefined)}>} options 选项
   * @param {string|number} [selected] 初始选中值，省略时由浏览器选中第一项
   * @returns {string} option 片段 HTML
   */
  function fieldOptions(options, selected) {
    var want = selected === undefined || selected === null ? null : String(selected);
    var html = '';
    var open = false;
    var cur = null;
    (options || []).forEach(function (o) {
      var g = o.group === undefined || o.group === null ? null : String(o.group);
      if (g !== cur) {
        if (open) { html += '</optgroup>'; open = false; }
        if (g !== null) { html += '<optgroup label="' + esc(g) + '">'; open = true; }
        cur = g;
      }
      var v = o.value === undefined || o.value === null ? '' : String(o.value);
      html += '<option value="' + esc(v) + '"' + (want !== null && v === want ? ' selected' : '') +
        '>' + esc(o.label) + '</option>';
    });
    if (open) { html += '</optgroup>'; }
    return html;
  }

  /**
   * 检索条件下的「标签 + 下拉框」字段，与 {@link ZFZ.ui.fieldBox} 同一外观。
   *
   * @param {string} label 字段标签
   * @param {string} id 下拉框 id
   * @param {Array<{value: (string|number), label: string, group: (string|undefined)}>} options 选项（第一项通常为「全部…」）
   * @param {string|number} [selected] 初始选中值，省略时由浏览器选中第一项
   * @returns {string} 字段 HTML
   */
  function fieldSelect(label, id, options, selected) {
    return '<div class="field-box"><span class="field-box-label">' + esc(label) + '</span>' +
      '<select class="field-select" id="' + esc(id) + '">' + fieldOptions(options, selected) +
      '</select></div>';
  }

  /* ---------------- 台风风险等级工具 ---------------- */
  var RISK = { high: '#D8322B', mid: '#C77400', low: '#2FA84F' };
  function riskColor(text) {
    if (/高/.test(text)) { return RISK.high; }
    if (/中/.test(text)) { return RISK.mid; }
    if (/低/.test(text)) { return RISK.low; }
    return '#5B6B7B';
  }

  /* ---------------- 左列台风列表（数据由调用方传入，本组件不读取任何演示数据） ----------------

     列表项的**主行为是「在右侧地图上聚焦该台风」**（点击事件由页面绑定），
     档案详情退化为行内一个次要的「详情」链接。

     为什么主容器不是 <a>：
       ① 一个可点区域里再嵌一个链接属于嵌套交互元素，键盘 Tab 顺序与读屏
          播报都会错乱（"链接 链接 详情 链接"）；
       ② 主行为是纯前端切换视野，用 <a href> 会让浏览器把地址栏也改掉，
          用户刷新一下就"回"到详情页，与预期不符。
     因此主容器用 div[role=button][tabindex=0]，行内保留**真正的** <a> 指向详情页——
     既保住了"中键/⌘点击在新标签打开档案"的浏览器原生习惯，也让键盘可达。
     页面侧需配套处理 Enter / Space（div 不会自己触发 click）。 */
  /**
   * 渲染左列台风列表。
   *
   * @param {Array} items 台风数组
   * @param {number|string} activeId 当前在地图上聚焦的台风 id（用于高亮）
   * @returns {string} 列表 HTML
   */
  function tyList(items, activeId) {
    return '<div class="ty-list">' + (items || []).map(function (t) {
      var lv = levelOf(t.windLevel);
      var statusText = t.statusText || (t.status === 'ACTIVE' ? '活跃中' : (t.year + ' 已归档'));
      var on = String(t.id) === String(activeId);
      return '<div class="ty-item' + (on ? ' is-active' : '') + '" data-ty-id="' + esc(t.id) + '"' +
        ' role="button" tabindex="0" aria-pressed="' + on + '" title="点击在地图上查看该台风路径">' +
        '<span class="ty-item-top">' +
          '<span class="ty-item-name">' + esc(t.nameCn + ' ' + t.nameEn) + '</span>' +
          '<span class="ty-item-level" style="color:' + levelColor(t.windLevel) + '">' +
            esc(lv.name + ' · ' + t.windLevel + '级') + '</span>' +
        '</span>' +
        '<span class="ty-item-sub">' + esc(t.code + ' · ' + t.basin + ' · ' + statusText) +
          '<a class="ty-item-detail" href="typhoon-detail.html?id=' + esc(t.id) + '"' +
            ' title="打开档案详情页">详情</a>' +
        '</span>' +
        '</div>';
    }).join('') + '</div>';
  }

  function levelColor(windLevel) {
    var colors = { 'is-neutral': '#5B6B7B', 'is-ts': '#2FA84F', 'is-sts': '#C77400', 'is-ty': '#E8833A', 'is-sty': '#D8322B', 'is-super': '#8B4BB8' };
    return colors[levelOf(windLevel).cls] || '#5B6B7B';
  }

  /* ======================================================================
     一、深色实况地图（P1 监测总览 / P6 推演沙盘）

     画布 804×462，等距圆柱投影，覆盖 **100°E—180°E / 4°N—50°N**。
     这个窗口就是「影响我国沿海的历年台风活动范围」：
       · 西边界 100°E —— 覆盖北部湾、孟加拉湾东缘（中纬台风西行终点）；
       · 东边界 180°E —— 覆盖到日期变更线，远洋生成后西行的台风
         （实测轨迹最东 179.4°E）不需要把整张图缩到 20% 才能入画；
       · 南北 4°N—50°N —— 台风生成带（赤道附近无科氏力，不生成台风）
         到俄罗斯远东，与 DEM 数据（92–178°E / 4–52°N）几乎重合。

     此前窗口是 105—135°E / 8—32°N（设计稿年代只考虑南海与东海），
     远洋台风一出现 fitBox 就得把视野压到 20%，于是画面上是一张
     「半个亚洲 + 一大片空海」的世界地图 —— 又小又读不出信息。
     现在**默认视野即整个区域**，缩放只用于看细节，不再用于"把线救回画布"。

     画布高从 640 降到 462（比例 804:462）是这次窗口扩大的代价：
     要在一屏内容下 80°×46°，画布必须变扁，否则经向被压扁 40%。
     页面总高因此从 ~1240 降到 ~1010，反而更好读。

     投影（等距圆柱，KX≈KY 保证形状不变形）：
       x = (lon - 100) * 10.05
       y = (50 - lat) * 10.043
     ====================================================================== */
  var MW = 804, MH = 462;
  var LON_MIN = 100, LON_MAX = 180, LAT_MIN = 4, LAT_MAX = 50;
  var KX = MW / (LON_MAX - LON_MIN);      /* 10.05 px/°E */
  var KY = MH / (LAT_MAX - LAT_MIN);      /* 10.043 px/°N */

  function r2(v) { return Math.round(v * 100) / 100; }
  function proj(lon, lat) { return [r2((lon - LON_MIN) * KX), r2((LAT_MAX - lat) * KY)]; }
  function unproj(x, y) { return [LON_MIN + x / KX, LAT_MAX - y / KY]; }

  /* 旧投影下烘死的画布坐标 → 当前画布坐标 的仿射系数。

     `js/geo-wpac.js`（748 条海岸线）与 `js/geo-wterrain.js`（晕渲位图坐标 +
     二十万点等高线）里的坐标都是在**设计稿那套投影**下生成好的：
       x = 351 + (lon − 118.0894) × 26.79
       y = 198 + (24.4798 − lat) × 27.11
     两个等距圆柱投影之间只差一个缩放 + 平移，因此改地图范围
     **不需要重跑 tools/gen-wpac-coast.py / tools/gen-terrain.py**
     （后者要重新抓 1.2GB DEM）。渲染时把这两份几何整体变换一次即可：
       p_new = T + R · p_old

     ⚠ 这两个系数是由"旧投影的一个锚点"与 KX/KY 反解出来的，
     一旦再改 LON_MIN/KX，这里会自动跟着变（不用手抄数字）；
     但若有人重新生成了数据文件、坐标已改成新投影，就必须删掉这层变换。 */
  var LEGACY = { x0: 351, y0: 198, lon0: 118.0894, lat0: 24.4798, kx: 26.79, ky: 27.11 };
  var LG_RX = KX / LEGACY.kx;
  var LG_RY = KY / LEGACY.ky;
  var LG_TX = (LEGACY.lon0 - LON_MIN) * KX - LEGACY.x0 * LG_RX;
  var LG_TY = (LAT_MAX - LEGACY.lat0) * KY - LEGACY.y0 * LG_RY;

  /** 旧画布坐标 → 当前画布坐标 的 SVG transform 属性值（作用于最内层 <g>） */
  var LEGACY_XF = 'translate(' + r2(LG_TX) + ' ' + r2(LG_TY) + ') scale(' +
    (Math.round(LG_RX * 100000) / 100000) + ' ' + (Math.round(LG_RY * 100000) / 100000) + ')';
  function pts(list) {
    return list.map(function (p) {
      var q = proj(p[0], p[1]); return q[0] + ',' + q[1];
    }).join(' ');
  }

  /* ======================================================================
     一·五、视野自适应（越界时自动缩放 / 平移）

     为什么要这一层：上表那套投影把经纬度写死映射到固定的 804×640 窗口，
     对应可见范围约 **105°E—135°E / 8°N—32°N**（沙盘图更窄）。
     设计稿年代活跃台风都在南海—东海一带，这条假设成立；但接入中央气象台
     真实数据后，台风完全可能位于西太平洋远洋（例：202629 小熊 157.8°E）。
     此时整条轨迹被投到 x≈1690 —— 远在画布之外，"路径线"就这么凭空消失了，
     页面看起来像功能坏了，实际是几何上根本没画进可视区。

     做法：渲染前先用 {@link fitBox} 判断「必须看得见」的点集是否装得下，
     装得下就原样 1:1（与设计稿完全一致，不做任何多余的缩放）；
     装不下才计算一次等比缩放 + 平移，并让底图、矢量层、标注同步变换，
     由页面在地图角上明示"已自动缩放"。

     变换是绕画布中心进行的：
       p' = (p - center) * s + center + t
     底图与所有矢量图层都在 SVG 里，统一用 <g transform> 表达，
     因此只有一套坐标换算（底图改用真实海岸线后，已不存在需要 CSS 变换兜底的
     <img> 底图）。
     ====================================================================== */

  /** 自动适应视野（{@link fitBox} 的结果）。用户缩放与平移都以它为基准 */
  var FIT = { s: 1, tx: 0, ty: 0, auto: false };

  /** 用户在自动视野之上**再叠加**的缩放倍率与平移（画布像素） */
  var ZOOM = { k: 1, px: 0, py: 0 };

  /** 当前 P1 实况地图的**实际**视野（= FIT ⊕ ZOOM）。auto=false 表示与设计稿 1:1 */
  var VIEW = { s: 1, tx: 0, ty: 0, auto: false };

  /**
   * 当前这张地图画的是什么：`live` 监测总览的台风实况 / `sandbox` 沙盘推演路径。
   *
   * <p>只用于生成提示文案（见 {@link viewNoteText}）：两张图共用同一套视野、
   * 同一套缩放交互，唯一要说清的区别就是"自动缩放是为了让你看见哪条线"。
   * 由 {@link mapCanvas} 与 {@link sandboxMap} 在渲染时各自写入。
   */
  var MAP_KIND = 'live';

  /** 自动缩放下限：再小地图就只剩轮廓，失去参照意义 */
  var MIN_VIEW_SCALE = 0.34;

  /** 视野留白（画布像素），避免轨迹贴着边缘 */
  var VIEW_PAD = 46;

  /** 用户缩放后「合成比例尺」的上下限。
   *
   * <p>下限取 1（即 100%）：本图在 100% 时已经把整个西北太平洋台风区装进画布，
   * 再缩小只会把视野推到 DEM 覆盖范围之外 —— 那里没有地形数据，画面会变成
   * 「一张小图漂在空白里」。所以「−」到 100% 就是到底（按钮会置灰，见 syncView）。 */
  var ZOOM_MIN = 1, ZOOM_MAX = 6;

  /** 单次缩放步进（按钮与滚轮共用），1.25 即每步 ±25% */
  var ZOOM_STEP = 1.25;

  /** 当前挂载了缩放交互的地图容器（由 {@link bindMapView} 写入），原地更新视野要用 */
  var viewHost = null;

  /** 恒等视野 */
  function identityView() { return { s: 1, tx: 0, ty: 0, auto: false }; }

  /**
   * 可平移范围的边界，单位是画布像素（即 {@link proj} 的输出坐标系）。
   *
   * <p>为什么需要这条边界：地形晕渲是一张**有覆盖范围**的位图
   * （DEM 92°E—178°E / 4°N—52°N，见 tools/fetch-dem.py）。位图以外没有高程，
   * 陆地只剩 `.map-land` 的一层平涂底色、海面直接透出容器底色 ——
   * 于是图面上会出现一条笔直的「地形到此为止」的直角分界线：
   * 一侧有晕渲、分层设色与等高线，另一侧是一块死色。2026-10-18 实测，
   * 把地图往西 / 北拖到 92°E、52°N 就是这个直角，割裂感极强。
   *
   * <p>与其让用户拖进「没有数据的地方」，不如把视野夹住 —— 这也与本图
   * 「只覆盖西北太平洋台风区」的定位一致：边界之外本来也没有要看的东西。
   *
   * <p>东边界取 180°E（画布右缘）而不是 DEM 的 178°E：那 2° 是开阔洋面，
   * 位图海面 alpha=0、两侧同色，不会留下痕迹；若取 178°E 则默认视野
   * （100—180°E）自己就越界了，反而不成立。
   */
  var BOUND = (function () {
    var a = proj(92, 52), b = proj(180, 4);
    return { x0: a[0], y0: a[1], x1: b[0], y1: b[1] };
  })();

  /**
   * 把数值夹进 `[lo, hi]`；区间为空（`lo > hi`，即边界比可视范围还窄）时取中点。
   * @param {number} v 原值
   * @param {number} lo 下界
   * @param {number} hi 上界
   * @returns {number}
   */
  function clampUnit(v, lo, hi) {
    if (lo > hi) { return (lo + hi) / 2; }
    if (v < lo) { return lo; }
    return v > hi ? hi : v;
  }

  /**
   * 把用户平移夹进 {@link BOUND}：可见矩形不允许超到有数据的范围之外。
   *
   * <p>视野公式是 `screen(p) = (p − c)·s + c + t`（见 {@link composeView}），
   * 于是可见区间为 `[c − (c+t)/s, c + (W−c−t)/s]`。要求它落在 `[B.x0, B.x1]`
   * 内，反解出 t 的上下界：
   * <pre>
   *   t ≥ W − c − s·(B.x1 − c)      （右缘不许越过 B.x1）
   *   t ≤ s·(c − B.x0) − c          （左缘不许越过 B.x0）
   * </pre>
   * 纵轴同理。
   *
   * <p>**原地改写 ZOOM.px/py**，而不是只在合成时夹一下返回值：拖拽是增量式的，
   * 若 ZOOM 里继续累加"越界量"，用户拖到边界再往回拖的那一小段会被吃掉，
   * 表现为地图黏在边界上不动。写回之后，越界的那部分增量下一帧就丢了。
   *
   * @returns {void}
   */
  function clampPan() {
    var b = FIT, s = (b.s || 1) * ZOOM.k;
    if (!(s > 0)) { return; }
    var cx = MW / 2, cy = MH / 2;
    var tx = b.tx * ZOOM.k + ZOOM.px, ty = b.ty * ZOOM.k + ZOOM.py;

    /* 自动适应本身选定的位置必须始终可达：万一某个台风跑到 100—180°E 之外，
       {@link fitBox} 会为了把它纳入画布而平移，此时若一味夹回 DEM 框内，
       那条路径反而会被裁掉。因此把区间放宽到"至少包含自动视野"，
       用户仍只能在 DEM 框（与自动位置）之间移动。 */
    var ax = b.tx * ZOOM.k, ay = b.ty * ZOOM.k;
    tx = clampUnit(tx, Math.min(cx - s * (BOUND.x1 - cx), ax), Math.max(s * (cx - BOUND.x0) - cx, ax));
    ty = clampUnit(ty, Math.min(cy - s * (BOUND.y1 - cy), ay), Math.max(s * (cy - BOUND.y0) - cy, ay));

    ZOOM.px = r2(tx - b.tx * ZOOM.k);
    ZOOM.py = r2(ty - b.ty * ZOOM.k);
  }

  /**
   * 把「自动适应视野」与「用户缩放 / 平移」合成为实际视野。
   *
   * <p>推导：自动适应先把点映射成 `(p − c)·b.s + c + b.t`；
   * 用户操作再叠一层「绕画布中心 c 缩放 k 倍，再平移 p」，于是
   * <pre>
   *   p'' = ((p − c)·b.s + c + b.t − c)·k + c + p
   *       = (p − c)·(b.s·k) + c + (b.t·k + p)
   * </pre>
   * 仍是同一种形式，只是 `s = b.s·k`、`t = b.t·k + p`。
   * 也就是说用户操作能被**无损折叠进同一个视野对象**，
   * {@link viewSvgTransform} / {@link viewApply} / {@link viewUnproj} 一行都不用改——
   * 这正是把这层做成"合成"而不是"在 SVG 里再套一层 transform"的原因：
   * 后者会让锚点公式、经纬网反投影、HTML 浮层定位各自维护一套坐标换算。
   *
   * @returns {{s:number, tx:number, ty:number, auto:boolean}} 合成视野
   */
  function composeView() {
    /* 先夹平移再合成：三个调用点（首屏渲染、总览、沙盘）都走这里，
       夹一次就够，不必在每个入口各写一遍。 */
    clampPan();
    var b = FIT;
    return {
      s: Math.round(b.s * ZOOM.k * 1000) / 1000,
      tx: r2(b.tx * ZOOM.k + ZOOM.px),
      ty: r2(b.ty * ZOOM.k + ZOOM.py),
      /* 只要用户动过缩放或平移就走变换路径——哪怕数值刚好回到 1，
         也不能回到"完全不加 <g>"的形态，否则运行时改不动已渲染的 DOM */
      auto: b.auto || ZOOM.k !== 1 || ZOOM.px !== 0 || ZOOM.py !== 0
    };
  }

  /**
   * 用户倍率的可用区间 `[lo, hi]`（已折算自动适应带来的基准比例尺）。
   *
   * <p>夹的是**合成后的 s** 而不是 k：自动适应本身可能已经把 s 压到 0.34，
   * 此时再允许 k 取 1 就会突破下限。另外不允许 k 越过 100%（`hi < 1` 时取 1）——
   * 基准已经比上限还大时，再放大只是把模糊的底图拉得更糊。
   *
   * <p>自动适应已经把 s 压到 1 以下时（极少数越界台风），下限也放开到 k=1，
   * 否则「重置」（k=1）本身就会落在区间外，按钮与状态会自相矛盾。
   *
   * @returns {{lo:number, hi:number}} 倍率区间
   */
  function zoomLimits() {
    var base = FIT.s || 1;
    var lo = ZOOM_MIN / base, hi = ZOOM_MAX / base;
    if (hi < 1) { hi = 1; }
    if (lo > 1) { lo = 1; }
    return { lo: lo, hi: hi };
  }

  /**
   * 把用户倍率夹到 {@link zoomLimits} 给出的区间。
   * @returns {void}
   */
  function clampZoom() {
    var L = zoomLimits();
    if (ZOOM.k < L.lo) { ZOOM.k = L.lo; }
    if (ZOOM.k > L.hi) { ZOOM.k = L.hi; }
  }

  /**
   * 缩放按钮的可用状态同步。
   *
   * <p>本图 100% 即装下整个区域（见 {@link ZOOM_MIN}），所以「−」在默认视野下
   * 本来就点不动。按钮一动不动却不解释，正是"按钮坏了"的观感来源；
   * 因此到底 / 到顶时直接用 `disabled` 属性置灰（同时天然屏蔽点击与键盘）。
   *
   * @param {Element} host 地图容器
   * @returns {void}
   */
  function syncZoomButtons(host) {
    var L = zoomLimits();
    var pairs = [
      [host.querySelector('[data-zoom="in"]'), ZOOM.k < L.hi - 1e-6],
      [host.querySelector('[data-zoom="out"]'), ZOOM.k > L.lo + 1e-6]
    ];
    for (var i = 0; i < pairs.length; i++) {
      var btn = pairs[i][0];
      if (!btn) { continue; }
      if (pairs[i][1]) {
        btn.removeAttribute('disabled');
        btn.removeAttribute('aria-disabled');
      } else {
        btn.setAttribute('disabled', 'disabled');
        btn.setAttribute('aria-disabled', 'true');
      }
    }
  }

  /**
   * 算出把一批「画布像素坐标」的点连同留白一起纳入 W×H 画布所需的变换。
   *
   * <p>判据是<b>数据是否完整落在画布内</b>，而不是"数据跨度是否小于画布"：
   * 后者会漏掉"跨度不大、但整体偏到画布之外"这种情况——例如沙盘图的路径点
   * 全都在 157°E 附近，彼此只差几十像素，却整体位于画布右侧 2000px 开外。
   * 只看跨度会算出 s=1，于是既不缩放也不平移，线依然在画布外。
   *
   * <p>数据本来就装得下时返回恒等视野（auto=false），
   * 调用方据此保持与设计稿完全一致的渲染结果。
   *
   * @param {Array<Array<number>>} pts 画布像素坐标点集
   * @param {number} W 画布宽
   * @param {number} H 画布高
   * @param {number} pad 四周留白（画布像素）
   * @returns {{s:number, tx:number, ty:number, auto:boolean}} 视野
   */
  function fitBox(pts, W, H, pad) {
    var v = identityView();
    var list = (pts || []).filter(function (p) {
      return p && isFinite(p[0]) && isFinite(p[1]);
    });
    if (list.length < 2) { return v; }

    var x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
    list.forEach(function (p) {
      if (p[0] < x0) { x0 = p[0]; }
      if (p[0] > x1) { x1 = p[0]; }
      if (p[1] < y0) { y0 = p[1]; }
      if (p[1] > y1) { y1 = p[1]; }
    });

    /* 数据完整落在画布内 —— 保持与设计稿逐像素一致，不引入任何多余变换 */
    if (x0 >= 0 && x1 <= W && y0 >= 0 && y1 <= H) { return v; }

    var bw = (x1 - x0) + pad * 2;
    var bh = (y1 - y0) + pad * 2;
    var s = Math.min(W / bw, H / bh, 1);
    if (s < MIN_VIEW_SCALE) { s = MIN_VIEW_SCALE; }

    v.s = Math.round(s * 1000) / 1000;
    v.tx = r2(-((x0 + x1) / 2 - W / 2) * v.s);
    v.ty = r2(-((y0 + y1) / 2 - H / 2) * v.s);
    v.auto = true;
    return v;
  }

  /**
   * 视野在 SVG 用户坐标系里的等效变换，对应 p' = (p - c) · s + c + t。
   *
   * <p>写成 `translate(t + c) scale(s) translate(-c)`：transform 列表从右往左依次作用于点，
   * 于是点先减去中心、再缩放、最后由平移把中心"加回来"。
   * <b>末尾那次 translate(-c) 与开头平移里的 +c 必须成对出现</b>——
   * 只减不加会让整个图层额外平移半个画布，表现为"路径贴到画布边缘"。
   *
   * @param {Object} v 视野
   * @param {number} W 画布宽
   * @param {number} H 画布高
   * @returns {string} transform 属性值
   */
  function viewSvgTransform(v, W, H) {
    return 'translate(' + r2(v.tx + W / 2) + ' ' + r2(v.ty + H / 2) + ') scale(' + v.s + ') translate(' +
      r2(-W / 2) + ' ' + r2(-H / 2) + ')';
  }

  /**
   * 把一个画布像素坐标点过一遍视野变换（供 HTML 浮层标注使用）。
   * @param {Array<number>} p 原坐标 [x, y]
   * @param {Object} v 视野
   * @param {number} W 画布宽
   * @param {number} H 画布高
   * @returns {Array<number>} 变换后的坐标
   */
  function viewApply(p, v, W, H) {
    return [r2((p[0] - W / 2) * v.s + W / 2 + v.tx), r2((p[1] - H / 2) * v.s + H / 2 + v.ty)];
  }

  /**
   * 画布坐标反投影回经纬度（仅 P1 的等距投影适用，用于画经纬网）。
   * @param {number} x 画布 x
   * @param {number} y 画布 y
   * @param {Object} v 视野
   * @returns {Array<number>} [lon, lat]
   */
  function viewUnproj(x, y, v) {
    var ux = (x - MW / 2 - v.tx) / v.s + MW / 2;
    var uy = (y - MH / 2 - v.ty) / v.s + MH / 2;
    return unproj(ux, uy);
  }

  /**
   * 包一层视野变换。
   *
   * <p>这里**恒产出** `<g class="map-view-g">`：恒等视野时省略 `transform` 属性
   * （`<g>` 不带 transform 等价于不存在，渲染结果与不包完全一致），
   * 非恒等时写入变换。
   *
   * <p>为什么不像以前那样"恒等就一个节点都不加"：缩放需要在**已渲染的 DOM 上原地生效**
   * （见 {@link ZFZ.ui.bindMapView}），只改这个 `<g>` 的 transform 即可。
   * 若恒等时不产出这个节点，拖动时就得重建整棵 SVG——不仅闪，
   * 还会把底图选择、图层开关的勾选状态一并冲掉。
   *
   * @param {string} inner 图层内容
   * @param {Object} v 视野
   * @param {number} W 画布宽
   * @param {number} H 画布高
   * @returns {string} 包好视野的图层内容
   */
  function withView(inner, v, W, H) {
    return '<g class="map-view-g"' + (v.auto ? ' transform="' + viewSvgTransform(v, W, H) + '"' : '') +
      '>' + inner + '</g>';
  }

  /* ======================================================================
     一·六、用户可干预的视野（缩放 / 平移）

     自动适应（fitBox）解决的是"程序能不能看见"，本层解决的是"用户想怎么看"：
     放大看某一段路径的细节，或缩小把周边海域一并纳入。
     实际视野 = composeView()，写入 VIEW；页面元素通过 syncView() 原地同步。
     ====================================================================== */

  /**
   * 把 `VIEW` 同步到**已经渲染好的** DOM —— 只改属性，不重建节点。
   *
   * <p>要同步五处，任何一处漏掉都会露馅：
   *   ① 矢量图层与底图的 `.map-view-g` 变换（底图与路径必须同源，否则海岸线和轨迹错位）；
   *   ② 经纬网图层的内容（只画当前可见范围内的整度线，步长还随比例尺变，必须重算）；
   *   ③ 字号反缩放系数 `--lbl-k` 与地名分级 `data-lod`（地名与刻度要保持屏幕字号恒定，
   *      缩得太小时低优先级地名要收起来，否则会挤成一团）；
   *   ④ 比例尺（长度与整距离都随比例尺变，写死的尺子是错的）；
   *   ⑤ 容器上的 `data-view` / `data-zoom`、控件百分比与右下角视野提示条
   *      （可观测性，回归脚本也读它）。
   *
   * <p>底图与矢量图层现在**都在 SVG 里**（底图已改为真实海岸线绘制），
   * 因此只有 `.map-view-g` 一处变换，不再需要 CSS 变换的等价表达。
   *
   * @returns {void}
   */
  function syncView() {
    if (!viewHost) { return; }
    VIEW = composeView();
    var on = VIEW.auto;

    var gs = viewHost.querySelectorAll('.map-view-g');
    var tr = on ? viewSvgTransform(VIEW, MW, MH) : null;
    for (var i = 0; i < gs.length; i++) {
      if (tr) { gs[i].setAttribute('transform', tr); } else { gs[i].removeAttribute('transform'); }
    }

    /* 地名与经纬刻度的字号按 1/s 放大，屏幕上的字保持不变。
       只改一个自定义属性即可，不必逐个 <text> 改 font-size。 */
    viewHost.style.setProperty('--lbl-k', String(r2(1 / (VIEW.s || 1))));

    var lod = lodLevel(VIEW.s);
    if (viewHost.getAttribute('data-lod') !== String(lod)) {
      viewHost.setAttribute('data-lod', String(lod));
    }

    var gratG = viewHost.querySelector('.map-grat-layer .map-view-g');
    if (gratG) { gratG.innerHTML = graticuleInner(VIEW); }

    var scale = viewHost.querySelector('.map-scale');
    if (scale) { scale.innerHTML = scaleBarInner(VIEW); }

    var note = viewHost.querySelector('.map-view-note');
    if (note) {
      note.hidden = !on;
      if (on) { note.innerHTML = viewNoteText(VIEW); }
    }

    if (on) {
      viewHost.setAttribute('data-view', VIEW.s + ',' + VIEW.tx + ',' + VIEW.ty);
    } else {
      viewHost.removeAttribute('data-view');
    }
    viewHost.setAttribute('data-zoom', String(Math.round(ZOOM.k * 100) / 100));

    var lab = viewHost.querySelector('[data-zoom-value]');
    if (lab) { lab.textContent = Math.round(VIEW.s * 100) + '%'; }

    /* 缩放按钮的可用状态：到顶 / 到底置灰（见 syncZoomButtons） */
    syncZoomButtons(viewHost);
  }

  /**
   * 以画布上的某个点为锚点缩放：锚点下的地物在缩放前后**保持不动**。
   *
   * <p>滚轮缩放必须这样，否则"鼠标指着某段路径滚一下，它反而跑出视野"。
   *
   * <p>推导：设基准视野 b、用户倍率 k、用户平移 p，屏幕坐标为
   * `screen(q) = (q − c)·b.s·k + c + b.t·k + p`。
   * 令锚点处的地物坐标 u 在 k → k′ 前后屏幕位置不变，解得
   * <pre>p′ = pu − c − (k′/k)·(pu − c − p)</pre>
   * 其中 pu 是锚点在画布坐标系里的位置。
   *
   * @param {number} f 倍率（>1 放大，<1 缩小）
   * @param {Array<number>} pu 锚点画布坐标 [x, y]
   * @returns {void}
   */
  function zoomAt(f, pu) {
    if (!viewHost) { return; }
    var c = [MW / 2, MH / 2];
    var k0 = ZOOM.k, px = ZOOM.px, py = ZOOM.py;
    ZOOM.k = k0 * f;
    /* 先夹倍率再算平移：否则算出的 p′ 对应的是"没被夹住的那个倍率"，
       表现为缩到极限后地图还在持续漂移 */
    clampZoom();
    var ratio = ZOOM.k / k0;
    if (ratio === 1) { return; }
    ZOOM.px = pu[0] - c[0] - ratio * (pu[0] - c[0] - px);
    ZOOM.py = pu[1] - c[1] - ratio * (pu[1] - c[1] - py);
    syncView();
  }

  /**
   * 以画布中心为锚点缩放（工具栏按钮走这里）。
   * @param {number} f 倍率
   * @returns {void}
   */
  function zoomView(f) { zoomAt(f, [MW / 2, MH / 2]); }

  /**
   * 拖拽平移。
   * @param {number} dx 画布坐标横向增量
   * @param {number} dy 画布坐标纵向增量
   * @returns {void}
   */
  function panView(dx, dy) {
    if (!viewHost) { return; }
    ZOOM.px += dx;
    ZOOM.py += dy;
    syncView();
  }

  /**
   * 回到自动适应视野（清掉用户的缩放与平移）。工具栏「重置」走这里。
   * @returns {void}
   */
  function resetView() {
    if (!viewHost) { return; }
    ZOOM = { k: 1, px: 0, py: 0 };
    syncView();
  }

  /**
   * 给地图容器挂上缩放交互：工具栏按钮 + 滚轮（按光标位置缩放）+ 拖拽平移。
   *
   * <p>只在带缩放控件（`.map-zoom`）的地图上生效——空态 / 加载态的地图没有这个控件，
   * 也就不会被滚轮劫持（那两处劫持了只会招人烦）。
   *
   * @param {Element} el `.map-canvas` 容器
   * @returns {void}
   */
  function bindMapView(el) {
    if (!el) { return; }
    viewHost = el;
    syncView();                       /* 把渲染期的视野同步到控件（百分比文案等） */

    var box = el.querySelector('.map-zoom');
    if (!box) { return; }
    el.classList.add('is-zoomable');

    box.addEventListener('click', function (e) {
      var b = e.target && e.target.closest ? e.target.closest('[data-zoom]') : null;
      if (!b) { return; }
      var act = b.getAttribute('data-zoom');
      if (act === 'in') { zoomView(ZOOM_STEP); }
      else if (act === 'out') { zoomView(1 / ZOOM_STEP); }
      else { resetView(); }
    });

    /**
     * 视口坐标 → 画布坐标。
     *
     * <p>`.map-canvas` 的 `aspect-ratio` 与 804×640 严格同比例（见 app.css），
     * 两个方向的比例尺本应相同；这里仍分开取，避免将来改比例时静默错位。
     *
     * @param {number} clientX 视口 x
     * @param {number} clientY 视口 y
     * @returns {Array<number>} 画布坐标 [x, y]
     */
    function toCanvas(clientX, clientY) {
      var r = el.getBoundingClientRect();
      return [(clientX - r.left) * MW / r.width, (clientY - r.top) * MH / r.height];
    }

    el.addEventListener('wheel', function (e) {
      if (!e.deltaY) { return; }              /* 只处理纵向滚动，横向留给页面 */
      e.preventDefault();                     /* 否则滚轮会把整页一起滚下去 */
      zoomAt(e.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP, toCanvas(e.clientX, e.clientY));
    }, { passive: false });

    el.addEventListener('mousedown', function (e) {
      if (e.button !== 0) { return; }
      /* 别把控件上的点击当成拖拽起手 */
      if (e.target && e.target.closest && e.target.closest('button')) { return; }
      var r = el.getBoundingClientRect();
      var kx = MW / r.width, ky = MH / r.height;
      var sx = e.clientX, sy = e.clientY;
      e.preventDefault();                     /* 防止拖拽时把地名标注选中成蓝色文字 */

      el.classList.add('is-panning');

      /** 拖拽过程中逐帧平移（增量式：每次只补上一帧到这一帧的差值） */
      function move(ev) {
        var dx = (ev.clientX - sx) * kx, dy = (ev.clientY - sy) * ky;
        if (!dx && !dy) { return; }
        sx = ev.clientX;
        sy = ev.clientY;
        panView(dx, dy);
      }
      function up() {
        document.removeEventListener('mousemove', move);
        document.removeEventListener('mouseup', up);
        el.classList.remove('is-panning');
      }
      document.addEventListener('mousemove', move);
      document.addEventListener('mouseup', up);
    });
  }

  /**
   * 当前视野快照，供回归脚本断言（不必去解析 `data-view` 字符串）。
   * @returns {{s:number, tx:number, ty:number, auto:boolean, k:number}} 视野 + 用户倍率
   */
  function viewState() {
    return { s: VIEW.s, tx: VIEW.tx, ty: VIEW.ty, auto: VIEW.auto, k: ZOOM.k };
  }

  /**
   * 浮动缩放控件（叠在地图右上角）。
   *
   * <p>放在画布内而不是上方工具栏：工具栏在 1440 宽度下已被「三张底图 + 三个图层开关」
   * 占满，再塞一组控件必然换行；而且缩放属于"对地图本身的直接操作"，
   * 跟着地图走比跟工具栏走更符合直觉（也是各类地图产品的通行做法）。
   *
   * @returns {string} 控件 HTML
   */
  function mapZoomCtl() {
    return '<div class="map-zoom" role="group" aria-label="地图缩放">' +
      '<button class="zoom-btn" type="button" data-zoom="in" title="放大" aria-label="放大">+</button>' +
      '<button class="zoom-btn" type="button" data-zoom="out" title="缩小" aria-label="缩小">−</button>' +
      '<span class="zoom-value" data-zoom-value>' + Math.round(VIEW.s * 100) + '%</span>' +
      '<button class="zoom-btn is-reset" type="button" data-zoom="reset" title="回到自动适应视野">重置</button>' +
      '</div>';
  }

  /**
   * 把当前视野写进容器属性（{@code scale,tx,ty}），仅用于可观测性：
   * 出问题时一眼就能看出地图到底缩没缩、平移到哪，不必反推像素坐标。
   * @param {Object} v 视野
   * @returns {string} 形如 ' data-view="0.697,-704.2,120.5"'；恒等视野时为空串
   */
  function viewAttr(v) {
    return v.auto ? ' data-view="' + v.s + ',' + v.tx + ',' + v.ty + '"' : '';
  }

  function layer(name, inner) {
    return '<svg class="map-layer" data-layer="' + name + '" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      withView(inner, VIEW, MW, MH) + '</svg>';
  }

  /* ---------------- 台风本体：可平移图层组 ----------------
     设计稿锚点：中心 (504,333)，风圈半径 7 级 48px / 10 级 19px / 12 级 7px。
     风圈半径按 speed / 38 缩放，因此设计稿锚点（38 m/s）下与设计稿完全一致，
     时间轴回放时则随强度「呼吸」。 */
  var PHOTO_X = 504, PHOTO_Y = 333;
  /* 兜底风圈半径（接口未给 r 时用），按真实公里数折算到新比例尺：
     7 级 ≈ 190 km / 10.4 ≈ 18px，10 级 ≈ 70 km ≈ 7px，12 级 ≈ 30 km ≈ 3px。 */
  var WIND_R = { 7: 18, 10: 7, 12: 3 };
  var WIND_FILL = { 7: ['#FFD166', .13, .55], 10: ['#FF9F43', .18, .70], 12: ['#E8453C', .45, .90] };
  var WIND_BASE_SPEED = 38;

  /* 投影比例尺：20°N 附近 1 画布像素 ≈ 10.4 km
     由 KX = 10.05 px/° 与 1° 经度 ≈ 111.32·cos20° ≈ 104.6 km 推得。
     风圈接口返回的 r 是真实公里数（7 级圈约 190 km），必须按此比例尺换算成屏幕像素；
     WIND_R 仅作为接口未提供 r 时的兜底屏幕半径（同样按真实公里数折算，
     不再沿用设计稿 26.79px/° 时代的 48/19/7 —— 那套在新比例下会大出 2.6 倍）。 */
  var KM_PER_PX = 10.4;

  function ringRadius(c) {
    var km = Number(c && c.r);
    if (isFinite(km) && km > 0) { return Math.max(3, r2(km / KM_PER_PX)); }
    return WIND_R[c && c.level] || 5;
  }

  /* 台风整体平移量（相对设计稿锚点） */
  function photoTransform(cx, cy) {
    return 'translate(' + r2(cx - PHOTO_X) + ' ' + r2(cy - PHOTO_Y) + ')';
  }

  /* 风圈：7 / 10 / 12 级同心圆（半径取自接口真实公里数，按投影比例尺换算） */
  function windRings(circles, cx, cy) {
    if (!circles || !circles.length) { return '<g class="ty-wind"></g>'; }
    var s = circles.map(function (c) {
      var r = ringRadius(c), f = WIND_FILL[c.level] || WIND_FILL[7];
      return '<circle class="wind-ring" data-level="' + c.level + '" data-base-r="' + r +
          '" cx="' + cx + '" cy="' + cy + '" r="' + r +
          '" fill="' + f[0] + '" fill-opacity="' + f[1] + '"/>' +
        '<circle class="wind-ring" data-level="' + c.level + '" data-base-r="' + r +
          '" cx="' + cx + '" cy="' + cy + '" r="' + r +
          '" fill="none" stroke="' + f[0] + '" stroke-width="1.2" stroke-opacity="' + f[2] + '"/>';
    }).join('');
    return '<g class="ty-wind">' + s + '</g>';
  }

  /* 台风中心与移动方向箭头 */
  function centerMark(cx, cy) {
    return '<g class="ty-center">' +
      '<circle cx="' + cx + '" cy="' + cy + '" r="13" fill="none" stroke="#FFF" stroke-width="1" stroke-opacity="0.3"/>' +
      '<path d="M' + (cx - 26) + ' ' + (cy - 15) + 'l2.6 1.5 2.6 1.5 2.6 1.5 2.6 1.5 2.6 1.5 2.6 1.5 2.6 1.5 2.6 1.5" ' +
        'stroke="#FFF" stroke-width="2.4" stroke-linecap="round" fill="none"/>' +
      '<path fill="#FFF" d="M' + (cx - 26) + ' ' + (cy - 15) + 'l11 1-5 8z"/>' +
      '<circle cx="' + cx + '" cy="' + cy + '" r="6" fill="#E8833A" stroke="#FFF" stroke-width="2"/>' +
      '<circle cx="' + cx + '" cy="' + cy + '" r="2" fill="#FFF"/>' +
      '</g>';
  }

  /* 卫星云图：台风螺旋云带（围绕中心合成三臂，高斯模糊柔化）
     三合一后常显（由 CSS 的 .map-cloud-layer 控制）。 */
  var CLOUD_DEFS = '<defs>' +
    /* 全域云系纹理：分形噪声直接当"云"用。三个通道的系数把噪声压成白色，
       再用 alpha 行的一次线性映射（k·(r+g+b) − c）只留下高亮的那部分——
       fractalNoise 各通道大致在 0.5 附近波动，求和约 1.5，
       取 k=2.0 / c=2.85 时约六成区域完全透明、最亮处接近不透明——
       关键是**斜率要陡**：斜率小的时候整张图会变成一层均匀的灰雾，
       既糊住地名又不像卫星图。 */
    '<filter id="zfzCloudTex" color-interpolation-filters="sRGB">' +
      '<feTurbulence type="fractalNoise" baseFrequency="0.0055 0.009" numOctaves="3" seed="17" result="n"/>' +
      '<feColorMatrix in="n" type="matrix" values="0 0 0 0 0.95  0 0 0 0 0.98  0 0 0 0 1  2.00 2.00 2.00 0 -2.85"/>' +
    '</filter>' +
    '<filter id="zfzCloudBlur" x="-60%" y="-60%" width="220%" height="220%">' +
      '<feGaussianBlur stdDeviation="6.5"/>' +
    '</filter>' +
    /* 云团用径向渐变做柔边，而不是给每个椭圆加高斯模糊：
       模糊要按元素逐个栅格化，几百个云团就是几百次滤镜；径向渐变是纯填充，
       既便宜又不需要担心滤镜区域的大小。色标颜色走 CSS 变量（见 app.css），
       颜色值不落在 JS 字符串里。 */
    '<radialGradient id="zfzCloudSoft">' +
      '<stop class="cloud-stop-0" offset="0"/>' +
      '<stop class="cloud-stop-1" offset="0.55"/>' +
      '<stop class="cloud-stop-2" offset="1"/>' +
    '</radialGradient></defs>';

  function cloudBand(cx, cy) {
    var s = '';
    for (var arm = 0; arm < 3; arm++) {
      for (var i = 0; i < 8; i++) {
        var ang = (arm * 120 + i * 27) * Math.PI / 180;
        var rad = 22 + i * 21;
        var x = cx + Math.cos(ang) * rad;
        var y = cy + Math.sin(ang) * rad * 0.74;
        var op = 0.72 - i * 0.062;
        if (op < 0.09) { continue; }
        s += '<ellipse cx="' + r2(x) + '" cy="' + r2(y) + '" rx="' + r2(32 - i * 1.7) + '" ry="' + r2(16 - i * 0.7) +
          '" fill="#DCEAF6" opacity="' + r2(op) + '" transform="rotate(' +
          (Math.round(ang * 180 / Math.PI) + 74) + ' ' + r2(x) + ' ' + r2(y) + ')"/>';
      }
    }
    return '<g class="ty-cloud" filter="url(#zfzCloudBlur)">' + s + '</g>';
  }

  /* ---------------- 云图图层（仅「卫星云图」底图显示） ----------------

     为什么单独成层：设计稿把云用 feTurbulence 烘在卫星底图那张 804×640 位图里，
     底图改成真实海岸线后云也跟着没了；而且它只覆盖 105°E—135°E，
     出了那个方块画面就是一片干净的海，看不出这是"卫星云图"。

     现在云图是独立图层：纹理矩形铺满整个数据范围（geo.pad），随视野变换
     （**地理锚定**——平移缩放时云跟着地面走，而不是像贴纸一样粘在屏幕上），
     三合一后常显（CSS 的 .map-cloud-layer）。

     形态是**示意云系**：分形噪声纹理给出细结构，确定性云团给出大尺度云系。
     它不是卫星反演产品——平台没有接入卫星数据源，因此图例中如实写作
     「云系示意」，不能让人误读成真实观测。台风本体的螺旋云带见 cloudBand()。 */
  var CLOUD_INNER = null;

  /** 确定性伪随机（线性同余）。云团位置必须每次渲染都一样，
      否则每切一次底图云就整片换位置，看起来像 bug。 */
  function lcg(seed) {
    var s = seed >>> 0;
    return function () { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
  }

  /** 大尺度云团：在数据范围内撒若干组，每组几团相邻的椭圆，用柔边渐变融成云系 */
  function cloudMasses(x0, y0, x1, y1) {
    var rnd = lcg(20261014);
    var s = '';
    for (var c = 0; c < 90; c++) {
      var cx = x0 + rnd() * (x1 - x0);
      var cy = y0 + rnd() * (y1 - y0);
      var n = 3 + Math.floor(rnd() * 3);
      for (var i = 0; i < n; i++) {
        var ang = rnd() * Math.PI * 2;
        var rad = rnd() * 210;
        var x = cx + Math.cos(ang) * rad;
        var y = cy + Math.sin(ang) * rad * 0.62;
        var rx = 90 + rnd() * 200;
        var ry = 34 + rnd() * 62;
        s += '<ellipse class="cloud-mass" fill="url(#zfzCloudSoft)" cx="' + r2(x) + '" cy="' + r2(y) +
          '" rx="' + r2(rx) + '" ry="' + r2(ry) + '" opacity="' + r2(0.16 + rnd() * 0.34) +
          '" transform="rotate(' + Math.round(rnd() * 180 - 90) + ' ' + r2(x) + ' ' + r2(y) + ')"/>';
      }
    }
    return s;
  }

  function cloudInner() {
    if (CLOUD_INNER === null) {
      var geo = (window.ZFZ && ZFZ.geo && ZFZ.geo.wpac) || null;
      var p = (geo && geo.pad) || [0, 0, MW, MH];
      var x0 = p[0], y0 = p[1], x1 = p[2], y1 = p[3];
      /* geo.pad 是旧投影的画布像素（与海岸线同一套坐标），整体套 LEGACY_XF；
         云团位置走 lcg 随机，但坐标基准也跟着变换，形态不变。 */
      CLOUD_INNER = '<g transform="' + LEGACY_XF + '">' +
        '<rect class="cloud-tex" x="' + x0 + '" y="' + y0 + '" width="' + (x1 - x0) +
          '" height="' + (y1 - y0) + '" filter="url(#zfzCloudTex)"/>' +
        '<g class="cloud-field">' + cloudMasses(x0, y0, x1, y1) + '</g></g>';
    }
    return CLOUD_INNER;
  }

  function lyrCloud() {
    return '<svg class="map-layer map-cloud-layer" data-layer="cloud" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      CLOUD_DEFS + withView(cloudInner(), VIEW, MW, MH) + '</svg>';
  }

  /* 多机构预报路径 */
  function lyrForecast(paths) {
    if (!paths || !paths.length) { return layer('forecast', ''); }
    var s = paths.map(function (p) {
      var last = p.points[p.points.length - 1];
      var end = proj(last[0], last[1]);
      return '<polyline points="' + pts(p.points) + '" fill="none" stroke="' + p.color + '" stroke-width="2" ' +
        'stroke-dasharray="' + (p.dash || '7 5') + '" stroke-linecap="round"/>' +
        '<rect x="' + (end[0] - 3.5) + '" y="' + (end[1] - 3.5) + '" width="7" height="7" fill="' + p.color + '" ' +
        'stroke="#0A2A44" stroke-width="1.4"/>';
    }).join('');
    return layer('forecast', s);
  }

  /* AI 综合测算路径 */
  function lyrAi(ai) {
    if (!ai || !ai.points || !ai.points.length) { return layer('ai', ''); }
    var last = ai.points[ai.points.length - 1];
    var end = proj(last[0], last[1]);
    var s = '<polyline points="' + pts(ai.points) + '" fill="none" stroke="' + ai.color + '" stroke-width="2" ' +
      'stroke-dasharray="' + (ai.dash || '6 5') + '" stroke-linecap="round"/>' +
      '<circle cx="' + end[0] + '" cy="' + end[1] + '" r="4" fill="' + ai.color + '" stroke="#0A2A44" stroke-width="1.4"/>';
    return layer('ai', s);
  }

  /* 台风路径轨迹（可回放）
     frames 为回放序列时用其平滑路径；否则退回实况三点。
     「已回放段」用 pathLength 归一化后由 stroke-dasharray 控制长度，
     时间轴推进时只需改一个属性即可实现逐段生长。 */
  function lyrTrack(tracks, frames) {
    var list = (frames && frames.length > 1)
      ? frames.map(function (f) { return [f.lon, f.lat]; })
      : (tracks || []).map(function (t) { return [t.lon, t.lat]; });
    if (list.length < 2) { return layer('track', ''); }

    var d = pts(list);
    var s =
      /* 全程引导线（细虚线，提示完整回放路径） */
      '<polyline class="track-guide" points="' + d + '" fill="none" stroke="#FFFFFF" stroke-opacity="0.18" ' +
        'stroke-width="1.4" stroke-dasharray="3 4" stroke-linecap="round"/>' +
      /* 已回放段（实线，长度由回放进度决定） */
      '<polyline class="track-live" points="' + d + '" pathLength="1000" fill="none" stroke="#FFFFFF" ' +
        'stroke-width="2.4" stroke-dasharray="480 1000" stroke-linecap="round"/>';

    /* 实况时次标记（起点绿 / 中间琥珀；当前时次即台风中心） */
    (tracks || []).forEach(function (t) {
      if (!t.marker || t.marker === 'current') { return; }
      var p = proj(t.lon, t.lat);
      s += '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="5" fill="' +
        (t.marker === 'start' ? '#27AE60' : '#E1A500') + '" stroke="#0A2A44" stroke-width="1.6"/>';
    });
    return layer('track', s);
  }

  /* ---------------- 回放运行时 ----------------
     由页面在时间轴推进时调用，只改动属性、不重建 DOM，因此动画平滑无闪烁。 */
  function movePhoto(cx, cy) {
    var t = photoTransform(cx, cy), ns = document.querySelectorAll('.ty-photo');
    for (var i = 0; i < ns.length; i++) { ns[i].setAttribute('transform', t); }
  }

  /* 风圈随强度缩放（speed m/s；38 m/s 即设计稿锚点）
     基准半径从节点的 data-base-r 读取 —— 该值已由 windRings() 按接口真实公里数
     换算得到，因此回放过程中的缩放始终以真实风圈为基准。 */
  function scaleWind(speed) {
    var k = (speed || WIND_BASE_SPEED) / WIND_BASE_SPEED;
    var ns = document.querySelectorAll('.wind-ring');
    for (var i = 0; i < ns.length; i++) {
      var lv = ns[i].getAttribute('data-level');
      var base = Number(ns[i].getAttribute('data-base-r'));
      if (!isFinite(base)) { base = WIND_R[lv] || 12; }
      ns[i].setAttribute('r', r2(base * k));
    }
  }

  /* 已回放轨迹长度（p：0-100） */
  function setTrackProgress(p) {
    var el = document.querySelector('.track-live');
    if (el) {
      el.setAttribute('stroke-dasharray', r2(Math.max(0, Math.min(100, p)) * 10) + ' 1000');
    }
  }

  /* 城市坐标有效性：后端字段缺失时跳过，避免投影出 NaN 把整条 SVG 弄坏 */
  function hasCoord(c) {
    return c && isFinite(Number(c.lon)) && isFinite(Number(c.lat));
  }

  /* 城市点位：普通城市点 + 受影响城市的金色圆环。
     设计稿原先把 15 个城市圆点烘在底图位图里，底图改为真实海岸线后点位
     改由本层绘制——这样才能跟着接口数据走，也不受底图覆盖范围限制。
     参数 base：全部参与展示的城市（接口 /api/cities）；affected：真正落入风圈的城市。
     颜色与半径全部走 CSS（含 --lbl-k 反缩放），组件不写死像素值。 */
  function lyrCities(base, affected) {
    var s = '';
    (base || []).filter(hasCoord).forEach(function (c) {
      var p = proj(Number(c.lon), Number(c.lat));
      s += '<circle class="map-city-dot" cx="' + p[0] + '" cy="' + p[1] + '" r="2.6"/>';
    });
    (affected || []).filter(hasCoord).forEach(function (c) {
      var p = proj(Number(c.lon), Number(c.lat));
      s += '<circle class="map-hit-ring" cx="' + p[0] + '" cy="' + p[1] + '" r="9" fill="none"/>' +
        '<circle class="map-hit-dot" cx="' + p[0] + '" cy="' + p[1] + '" r="3.2"/>';
    });
    return layer('cities', s);
  }

  /* ---------------- 地名（海域 / 国家与地区 / 省级 / 城市） ----------------

     设计稿原先把「南海」「西北太平洋」两个海域名和三组经纬刻度写死在标注层里，
     位置是像素常数，出了 105°E—135°E 就没有任何地名。底图改为真实海岸线后，
     地名也改为**按经纬度投影**绘制，因此平移到哪里都有对应的地名。

     条目格式：[名称, 经度, 纬度, 类别, 优先级, 可选配置]
       类别   sea 海域 / country 国家与地区 / prov 省级 / city 城市
       优先级 1 一直显示；2 需要中等以上比例尺；3 只在放大后显示
              （见 lodLevel 与 CSS 的 [data-lod] 规则，避免缩小时糊成一片）
       可选配置 { a: 'middle'|'end' 对齐, dx/dy: 相对投影点的偏移（画布像素） }

     区划与海域名称按国家测绘规范表述：台湾地区、香港特别行政区、澳门特别行政区、
     南海诸岛。本图只画自然海岸线、不画任何政治边界，条目中也绝不放军事或涉密点位。 */
  var PLACES = [
    /* ---- 海域 ---- */
    ['渤海', 119.5, 38.6, 'sea', 1],
    ['黄海', 123.6, 34.8, 'sea', 1],
    ['东海', 125.8, 28.8, 'sea', 1],
    ['南海', 114.5, 15.0, 'sea', 1],
    ['南海诸岛', 114.6, 10.0, 'sea', 1],
    ['菲律宾海', 134.0, 19.0, 'sea', 1],
    ['日本海', 135.5, 39.5, 'sea', 2],
    ['鄂霍次克海', 150.0, 53.0, 'sea', 2],
    ['西北太平洋', 158.0, 32.0, 'sea', 1],
    ['孟加拉湾', 88.0, 15.0, 'sea', 2],
    ['阿拉伯海', 63.0, 15.0, 'sea', 3],
    ['泰国湾', 101.0, 8.0, 'sea', 3],
    ['爪哇海', 112.0, -5.5, 'sea', 3],
    ['珊瑚海', 155.0, -18.0, 'sea', 3],

    /* ---- 国家与地区 ---- */
    ['中国', 103.0, 34.0, 'country', 1],
    ['蒙古', 103.0, 46.5, 'country', 2],
    ['俄罗斯', 140.0, 55.0, 'country', 1],
    ['哈萨克斯坦', 68.0, 48.0, 'country', 3],
    ['印度', 78.0, 22.0, 'country', 2],
    ['日本', 138.5, 36.5, 'country', 1],
    ['韩国', 127.8, 36.2, 'country', 1],
    ['朝鲜', 126.9, 40.0, 'country', 1],
    ['越南', 106.5, 16.5, 'country', 1],
    ['老挝', 103.5, 19.5, 'country', 3],
    ['柬埔寨', 105.0, 12.5, 'country', 2],
    ['泰国', 101.0, 15.5, 'country', 2],
    ['缅甸', 96.0, 21.0, 'country', 2],
    ['马来西亚', 102.5, 3.5, 'country', 2],
    ['新加坡', 103.8, 1.4, 'country', 3],
    ['印度尼西亚', 114.0, -2.0, 'country', 1],
    ['菲律宾', 122.5, 12.5, 'country', 1],
    ['巴布亚新几内亚', 144.0, -6.0, 'country', 2],
    ['澳大利亚', 133.0, -24.0, 'country', 2],
    ['新西兰', 172.0, -42.0, 'country', 3],
    ['台湾地区', 121.0, 23.5, 'country', 1, { dy: 10 }],

    /* ---- 省级行政区（中国） ---- */
    ['黑龙江', 127.5, 47.5, 'prov', 2],
    ['吉林', 126.5, 43.7, 'prov', 2],
    ['辽宁', 122.6, 41.5, 'prov', 2],
    ['内蒙古', 111.0, 42.0, 'prov', 2],
    ['新疆', 85.0, 41.5, 'prov', 3],
    ['甘肃', 103.0, 38.0, 'prov', 3],
    ['宁夏', 106.2, 37.3, 'prov', 3],
    ['山西', 112.3, 37.6, 'prov', 3],
    ['河北', 115.5, 38.5, 'prov', 2],
    ['北京', 116.4, 39.9, 'prov', 2],
    ['天津', 117.2, 39.1, 'prov', 3, { dy: 12 }],
    ['山东', 118.5, 36.4, 'prov', 2],
    ['河南', 113.5, 34.0, 'prov', 3],
    ['陕西', 108.9, 34.3, 'prov', 3],
    ['江苏', 119.5, 33.2, 'prov', 2],
    ['安徽', 117.2, 31.8, 'prov', 3],
    ['湖北', 112.3, 31.0, 'prov', 3],
    ['四川', 102.7, 30.6, 'prov', 3],
    ['重庆', 107.5, 29.9, 'prov', 3],
    ['浙江', 120.2, 29.2, 'prov', 2],
    ['江西', 115.7, 27.6, 'prov', 3],
    ['湖南', 111.7, 27.6, 'prov', 3],
    ['贵州', 106.7, 26.8, 'prov', 3],
    ['福建', 118.0, 26.0, 'prov', 2],
    ['云南', 101.5, 25.0, 'prov', 2],
    ['广西', 108.3, 23.5, 'prov', 2],
    ['广东', 113.5, 23.4, 'prov', 2],
    ['青海', 96.0, 35.5, 'prov', 3],
    ['西藏', 88.0, 31.5, 'prov', 3],

    /* ---- 城市（中国沿海 14 个来自接口，这里补西太平洋主要城市） ---- */
    ['海参崴', 131.9, 43.1, 'city', 3],
    ['哈巴罗夫斯克', 135.1, 48.5, 'city', 3],
    ['札幌', 141.4, 43.1, 'city', 3],
    ['仙台', 140.9, 38.3, 'city', 3],
    ['东京', 139.7, 35.7, 'city', 2],
    ['名古屋', 136.9, 35.2, 'city', 3],
    ['大阪', 135.5, 34.7, 'city', 3],
    ['福冈', 130.4, 33.6, 'city', 3],
    ['那霸', 127.7, 26.2, 'city', 3],
    ['首尔', 127.0, 37.6, 'city', 2],
    ['釜山', 129.1, 35.2, 'city', 3],
    ['平壤', 125.8, 39.0, 'city', 3],
    ['台北', 121.6, 25.0, 'city', 2],
    ['高雄', 120.3, 22.6, 'city', 3],
    ['香港特别行政区', 114.2, 22.3, 'city', 2, { dx: 7, dy: 13 }],
    ['澳门特别行政区', 113.5, 22.2, 'city', 2, { a: 'end', dx: -7, dy: -5 }],
    ['河内', 105.8, 21.0, 'city', 3],
    ['胡志明市', 106.6, 10.8, 'city', 3],
    ['曼谷', 100.5, 13.8, 'city', 3],
    ['马尼拉', 121.0, 14.6, 'city', 2],
    ['宿务', 123.9, 10.3, 'city', 3],
    ['达沃', 125.6, 7.1, 'city', 3],
    ['吉隆坡', 101.7, 3.1, 'city', 3],
    ['雅加达', 106.9, -6.2, 'city', 3],
    ['达尔文', 130.8, -12.5, 'city', 3],
    ['莫尔兹比港', 147.2, -9.4, 'city', 3],
    ['关岛', 144.8, 13.4, 'city', 3],
    ['塞班', 145.8, 15.2, 'city', 3],
    ['布里斯班', 153.0, -27.5, 'city', 3],
    ['悉尼', 151.2, -33.9, 'city', 3]
  ];

  /** 分级显示的门槛：返回当前比例尺下允许显示的最高优先级（1/2/3）
   *
   *  阈值跟着投影走：画布从 105—135°E / 8—32°N（26.79 px/°）扩到
   *  100—180°E / 4—50°N（10.05 px/°）之后，**同一个 s 的屏幕像素密度
   *  只有原来的 37.5%**。沿用旧的 0.55 / 0.32 会让默认视野（s=1）
   *  直接显示全部 108 条地名 —— 省级和城市名会挤成一团、盖住路径线。
   *  这里把阈值整体放大到「默认 s=1 只出到省级，放大 1.5 倍才出城市」。 */
  function lodLevel(s) {
    if (s >= 1.5) { return 3; }
    if (s >= 0.7) { return 2; }
    return 1;
  }

  /** 静态地名按类别分组的文本节点 */
  function placeLabels() {
    var groups = { sea: [], country: [], prov: [], city: [] };
    PLACES.forEach(function (p) {
      var kind = p[3];
      if (!groups[kind]) { return; }
      var o = p[5] || {};
      var q = proj(Number(p[1]), Number(p[2]));
      var anchor = o.a || (kind === 'city' ? 'start' : 'middle');
      var dx = o.dx !== undefined ? o.dx : (kind === 'city' ? 6 : 0);
      var dy = o.dy !== undefined ? o.dy : (kind === 'city' ? 6.1 : 0);
      groups[kind].push('<text class="map-lbl is-' + kind + ' is-lv' + p[4] + '"' +
        (anchor === 'start' ? '' : ' text-anchor="' + anchor + '"') +
        (kind === 'city' ? '' : ' dominant-baseline="middle"') +
        ' x="' + r2(q[0] + dx) + '" y="' + r2(q[1] + dy) + '">' + esc(p[0]) + '</text>');
    });
    return ['sea', 'country', 'prov', 'city'].map(function (k) {
      return '<g class="map-lbl-g is-' + k + '">' + groups[k].join('') + '</g>';
    }).join('');
  }

  /* 地名与刻度标注。
     参数 cities：接口返回的城市数组（元素形如 { name, lon, lat }）。
     类名 map-city-label 只加在接口城市上：回归脚本按它断言「城市来自 /api/cities」。 */
  function lyrLabels(cities) {
    var s = '<g class="map-lbl-g is-city">';
    (cities || []).filter(hasCoord).forEach(function (c) {
      var p = proj(Number(c.lon), Number(c.lat));
      s += '<text class="map-lbl is-city is-lv3 map-city-label" x="' + (p[0] + 6) + '" y="' + (p[1] + 6.1) + '">' +
        esc(c.name) + '</text>';
    });
    s += '</g>';
    s += placeLabels();
    return layer('labels', s);
  }

  /* 比例尺：长度随比例尺变化，缩小时自动换算成更大的整距离。
     换算沿用 KM_PER_PX（20°N 附近 1 画布像素 ≈ 3.9 km，与风圈换算同一个常数），
     因此文案用「≈」。写死长度的比例尺在可缩放的地图上是错的——缩到 45% 后
     那根 60px 的尺子代表的距离会翻一倍多。 */
  var SCALE_STEPS = [50, 100, 200, 500, 1000, 2000, 5000, 10000];

  function scaleBarInner(v) {
    var kmPerPx = KM_PER_PX / (v.s || 1);
    var d = SCALE_STEPS[SCALE_STEPS.length - 1];
    for (var i = 0; i < SCALE_STEPS.length; i++) {
      if (SCALE_STEPS[i] / kmPerPx >= 70) { d = SCALE_STEPS[i]; break; }
    }
    var len = Math.round(d / kmPerPx);
    return '<svg class="map-scale-bar" width="' + len + '" height="7" aria-hidden="true" focusable="false">' +
      '<path d="M0.5 1 V6 M0.5 3.5 H' + (len - 0.5) + ' M' + (len - 0.5) + ' 1 V6" ' +
      'stroke="currentColor" stroke-width="1" fill="none"/></svg>' +
      '<span class="map-scale-text">≈ ' + d + ' km</span>';
  }

  /* 地图图例（HTML 浮层，位于左下角）
     「云系示意」只在卫星云图下出现：云是程序合成的示意云系、不是卫星反演产品，
     必须在图上就能看出来，不能让人误读成真实观测。 */
  function mapLegend() {
    return '<div class="map-legend">' +
      '<div class="legend-item"><svg width="24" height="10"><line x1="1" y1="5" x2="23" y2="5" stroke="#FFF" stroke-width="2"/>' +
        '<circle cx="1.5" cy="5" r="3" fill="#27AE60"/><circle cx="22.5" cy="5" r="3" fill="#E1A500"/></svg>实况轨迹</div>' +
      '<div class="legend-item"><svg width="24" height="10">' +
        '<line x1="1" y1="5" x2="7" y2="5" stroke="#E60012" stroke-width="2.4"/>' +
        '<line x1="9" y1="5" x2="15" y2="5" stroke="#2E8FE0" stroke-width="2.4"/>' +
        '<line x1="17" y1="5" x2="23" y2="5" stroke="#FF9900" stroke-width="2.4"/></svg>CMA / JMA / JTWC 预报</div>' +
      '<div class="legend-item"><svg width="24" height="10">' +
        '<line x1="1" y1="5" x2="23" y2="5" stroke="#22D3EE" stroke-width="2" stroke-dasharray="4 3"/>' +
        '<circle cx="22.5" cy="5" r="2.6" fill="#22D3EE"/></svg>AI 多模型综合测算</div>' +
      '<div class="legend-item legend-cloud">' +
        '<svg width="24" height="10"><ellipse class="legend-cloud-blob" cx="9" cy="5" rx="7" ry="4" opacity=".55"/>' +
        '<ellipse class="legend-cloud-blob" cx="16" cy="5" rx="6" ry="3.4" opacity=".4"/></svg>云系示意（非观测产品）</div>' +
      /* 地形图例：三合一后地形常显，但数据仍按需加载（1.2MB），
         首屏渲染时可能还没到 —— terrainLegend() 在数据未就绪时返回空串，
         加载完成后由 ensureTerrain() 追加。 */
      terrainLegend() +
      '</div>';
  }

  /* 地形图图例：分层设色色带 + 等高距说明。

     地形图必须自带"颜色代表多少海拔"的读图说明 —— 否则切换到底图后，
     一片褐绿色对用户毫无意义，正是"地形图只换个颜色"这种做法的问题。
     色带与高程分档必须与 tools/gen-terrain.py 的 HYPSO 表一一对应，
     改了那边这里也要改（HYPSO_BANDS 是它的镜像）。 */
  var TERRAIN_BANDS = [
    { h: 0,    t: '0' },
    { h: 150,  t: '150' },
    { h: 800,  t: '800' },
    { h: 2500, t: '2500' },
    { h: 5000, t: '5000+' }
  ];

  function terrainLegend() {
    var g = terrainData();
    if (!g) { return ''; }
    var i, sw = '';
    for (i = 0; i < TERRAIN_BANDS.length; i++) {
      sw += '<i class="terr-swatch" style="--terr-c:var(--hyps-' + i + ')"></i>';
    }
    var ticks = '';
    for (i = 0; i < TERRAIN_BANDS.length; i++) {
      ticks += '<span>' + TERRAIN_BANDS[i].t + '</span>';
    }

    /* 等高距：geo-wterrain.js 的 interval 是「分级等高距」数组
       [ [高程上限, 间距], ... ]，这里渲染成一句人话。
       注意必须做类型防御 —— 该字段在不同版本的生成物里出现过
       数字与字符串两种形态，直接 .join/.split 会抛异常并让整张地图
       渲染失败（页面直接空白，比显示问题严重得多）。 */
    var ivText = '';
    if (Array.isArray(g.interval)) {
      var parts = [];
      for (i = 0; i < g.interval.length; i++) {
        var row = g.interval[i];
        parts.push(String(row && row.length > 1 ? row[1] : row) + ' m');
      }
      ivText = parts.join(' / ');
    } else if (g.interval != null) {
      ivText = esc(String(g.interval)) + ' m';
    }

    /* 图例要**压紧**：三合一后它和主图例叠在同一个浮层里，地图本身只有
       462px 高，四行主图例 + 五行地形说明会占掉近三分之一画面，还会把
       等高距、数据来源这些说明挤成折行的碎字。
       现在只保留「标题 + 色带 + 刻度 + 一行等高距」，数据来源与计曲线
       规则移到容器的 title（悬停可读），既不丢信息又不占地。 */
    return '<div class="legend-terrain" title="等高距 ' + (ivText || '—') +
        (g.indexAt ? '；计曲线每 ' + g.indexAt + ' m 加粗' : '') +
        '；底图源自真实高程数据（SRTM 等），非示意">' +
      '<div class="legend-terrain-title">地面高程（米）<span class="legend-terrain-tag">真实 DEM</span></div>' +
      '<div class="terr-scale">' + sw + '</div>' +
      '<div class="terr-scale-ticks">' + ticks + '</div>' +
      (ivText ? '<div class="legend-terrain-note">等高距 ' + ivText + '</div>' : '') +
      '</div>';
  }

  /* ---------------- 底图：真实海岸线 ----------------

     设计稿原先附带三张 804×640 的位图底图，只覆盖 105°E—135°E：
     远洋台风一旦出现，fitBox() 为把整条路径纳入画布就把视野缩到 20%，
     底图随之缩成角落里的一小块，方块之外既没有经纬网也没有地名，
     地图看上去是"残"的。现在底图改为**完全由真实海岸线生成**
     （Natural Earth 1:50m，由 tools/gen-wpac-coast.py 产出，
     坐标系与 proj() 同源，经 LEGACY_XF 换算到当前投影）。

     2026-10-18 三合一后，海岸线不再分「普通 / 卫星 / 地形」三套配色，
     而是**同一份几何、两种用法**：`map-land` 负责陆地填充（垫在最底），
     `map-coast` 负责描边（压在地形位图之上，见 {@link lyrCoast}）。

     海面不用图层矩形提供，而是 .map-canvas 的底色 ——
     缩到 15% 时可见范围有 4000 多像素宽，任何固定大小的矩形都铺不满，
     四周必然露出容器的原始底色。 */

  /** 陆地图层与海岸线图层共用的 path 片段。
      数据是静态的（748 条 path、约 104KB），只拼一次；每次 mapCanvas 重建都
      重拼一遍纯属浪费。外层 `<g>` 做「旧投影 → 当前投影」的整体变换
      （见 {@link LEGACY_XF}），所以数据文件不用重新生成。 */
  var LAND_INNER = null, COAST_INNER = null;

  function landPaths(cls) {
    var geo = (window.ZFZ && ZFZ.geo && ZFZ.geo.wpac) || null;
    var s = '';
    if (geo && geo.land) {
      for (var i = 0; i < geo.land.length; i++) {
        /* d 里只有 M / l / Z / 数字 / 空格 / 负号，没有需要转义的字符 */
        s += '<path class="' + cls + '"' + (cls === 'map-land' ? ' fill-rule="evenodd"' : '') +
          ' d="' + geo.land[i] + '"/>';
      }
    }
    return s ? '<g transform="' + LEGACY_XF + '">' + s + '</g>' : '';
  }

  function landInner() {
    if (LAND_INNER === null) { LAND_INNER = landPaths('map-land'); }
    return LAND_INNER;
  }

  /**
   * 海岸线：单独成层，压在**地形位图之上**。
   *
   * <p>三合一之后地形晕渲是常显的，它不透明地盖住整个陆地（只有海面
   * alpha=0）。如果海岸线还和陆地填充画在同一个 `<path>` 上（fill + stroke），
   * 描边会被位图遮掉一半 —— 地形色块与海面之间就失去明确界线，整张图发糊。
   * 拆成两层后，岸线永远是最后一道边。
   */
  function coastInner() {
    if (COAST_INNER === null) { COAST_INNER = landPaths('map-coast'); }
    return COAST_INNER;
  }

  function lyrLand() {
    var inner = landInner();
    if (!inner) { return ''; }
    return '<svg class="map-layer map-land-layer" data-layer="land" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      withView(inner, VIEW, MW, MH) + '</svg>';
  }

  function lyrCoast() {
    var inner = coastInner();
    if (!inner) { return ''; }
    return '<svg class="map-layer map-coast-layer" data-layer="coast" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      withView(inner, VIEW, MW, MH) + '</svg>';
  }

  /* ---------------- 地形图层：晕渲 + 分层设色 + 矢量等高线 ----------------

     此前「地形图」与普通/卫星底图共用同一份海岸线几何，只在 CSS 里换
     三个 fill/stroke 颜色 —— 那样的地形图没有任何地形信息，只是一张
     绿色的普通地图。真正的地形图必须有**高程**，并把「高程 → 颜色 +
     明暗 + 等高线」这条制图学映射表达出来：

       ① 晕渲（hillshade）：由坡度/坡向算光照强度 → 立体感
       ② 分层设色：按高程分档着色（绿→褐→白）→ 高低分布
       ③ 矢量等高线 → 精确读高程，且放大到 600% 也不糊

     数据全部来自真实 DEM（tools/fetch-dem.py 抓取、tools/gen-terrain.py
     渲染），坐标系与 proj() 同源，因此与海岸线、经纬网、地名共用同一个
     .map-view-g 视野变换 —— 缩放/平移时三者不会错位。

     底图位图（晕渲+设色）是一个 <image>，铺在覆盖范围内；它与海岸线
     图层同在一个 view-g 内，天然跟着视野变换走，不需要 CSS transform。

     ⚠ 按需加载：geo-wterrain.js 约 1.2MB（等高线二十万点），
     而默认底图是「普通地图」用不到它，同步 <script> 会让首屏白白
     多等 1.2MB。因此数据由 {@link loadWterrain} 在用户首次切到地形图时
     注入，加载完再回调重建图层。 */

  /** 地形数据的加载状态：null=未开始 / 'loading' / 'ready' / 'error' */
  var WTERRAIN_STATE = null;
  var WTERRAIN_PENDING = [];

  function terrainData() {
    return (window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain) || null;
  }

  /**
   * 按需加载地形数据（首次切到地形图时调用）。
   *
   * <p>重复调用是安全的：已在加载 / 已就绪都会直接走回调，不会重复插 <script>。
   * 加载失败也要回调 —— 否则地形图会永远停在一个空图层上，用户看到的是
   * 一张「只有绿色陆地、没有地形」的地形图，比报错更难察觉。
   *
   * @param {function} done 回调（无论成败都会被调用）
   * @returns {void}
   */
  function loadWterrain(done) {
    if (terrainData()) {
      WTERRAIN_STATE = 'ready';
      done();
      return;
    }
    if (WTERRAIN_STATE === 'loading') { WTERRAIN_PENDING.push(done); return; }
    if (WTERRAIN_STATE === 'error') { done(); return; }

    WTERRAIN_STATE = 'loading';
    WTERRAIN_PENDING.push(done);

    var s = document.createElement('script');
    s.src = 'js/geo-wterrain.js';
    s.onload = function () {
      WTERRAIN_STATE = terrainData() ? 'ready' : 'error';
      flushWterrain();
    };
    s.onerror = function () {
      WTERRAIN_STATE = 'error';
      flushWterrain();
    };
    document.head.appendChild(s);
  }

  function flushWterrain() {
    var list = WTERRAIN_PENDING;
    WTERRAIN_PENDING = [];
    for (var i = 0; i < list.length; i++) {
      try { list[i](); } catch (e) { /* 单个回调出错不应阻断其它回调 */ }
    }
  }

  /** 地形图层内容（位图 + 等高线 + 高程点），已包好当前视野的 .map-view-g。
      三合一后常显（CSS 的 .map-terrain-layer）。数据未就绪时返回空串。 */
  function terrainInner() {
    var g = terrainData();
    if (!g || !g.pad) { return ''; }

    var p = g.pad, w = p[2] - p[0], h = p[3] - p[1];

    /* SVG <image> 的 x/y 必须是**非负**值：给负坐标会让元素整体失效
       （实测 getBBox() 返回 0、complete 永不为 true，且请求确实发出并返回 200
       —— 浏览器把元素当成不可用，位图永远不显示）。
       本项目 DEM 覆盖范围左/上边界本就在画布原点之外（pad[0]=-348、
       pad[1]=-548），因此必须把负偏移挪到外层 <g> 的 translate 上，
       让 <image> 自身坐标从 0 起算。等高线与高程点不受此限（<path>/<text>
       允许负坐标），仍按原始画布像素写。 */
    var inner = '<g transform="translate(' + p[0] + ' ' + p[1] + ')">' +
      '<image class="terr-hills" x="0" y="0" ' +
      'width="' + w + '" height="' + h + '" ' +
      'preserveAspectRatio="none" href="assets/terrain/terrain-hillshade.png"/></g>';

    /* 等高线：矢量，放大不糊。count 条高程线已按「计曲线 / 一般线」分级，
       由 CSS 决定线宽与颜色，并按缩放层级渐进显示（见 .terr- 规则）：
       缩小时只留计曲线 —— 小比例尺下密集的等高线既读不出高程、
       又会盖住晕渲的明暗层次，这正是制图学上「图上要素随比例尺增减」。 */
    if (g.contour && g.contour.length) {
      var c = '';
      for (var i = 0; i < g.contour.length; i++) {
        var row = g.contour[i];
        c += '<path class="' + row[1] + '" data-elev="' + row[0] + '" d="' + row[2] + '"/>';
      }
      inner += '<g class="terr-contours">' + c + '</g>';
    }

    /* 高程点：局部高点的三角标 + 高程数字。字号走 --lbl-k 反缩放，
       保证屏幕上的字不随缩放变大变小（与地名标注同一套机制）。 */
    if (g.peaks && g.peaks.length) {
      var k = '';
      for (var j = 0; j < g.peaks.length; j++) {
        var pk = g.peaks[j];
        k += '<g class="terr-peak" transform="translate(' + r2(pk[0]) + ',' + r2(pk[1]) + ')">' +
          '<path d="M0 -3.2 L2.6 1.8 L-2.6 1.8 Z"/>' +
          '<text x="0" y="8">' + pk[2] + '</text></g>';
      }
      inner += '<g class="terr-peaks">' + k + '</g>';
    }

    /* 整个地形层（位图 + 等高线 + 高程点）用一层 <g> 做「旧投影 → 当前投影」
       的整体变换：geo-wterrain.js 的 pad / contour / peaks 全是旧画布坐标，
       重跑 gen-terrain.py 需要先重新抓 1.2GB DEM，没必要。 */
    return '<g transform="' + LEGACY_XF + '">' + inner + '</g>';
  }

  /* 地形图层的 <svg> 外壳。

     数据未就绪（按需加载中）时也**必须输出空图层** —— 否则切到地形图后
     容器里连占位都没有，加载完无法就地替换，只能整张地图重绘，
     用户的缩放 / 平移状态与图层勾选态都会丢。 */
  function lyrTerrain() {
    return '<svg class="map-layer map-terrain-layer" data-layer="terrain" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      withView(terrainInner(), VIEW, MW, MH) + '</svg>';
  }

  /* 地形图层的内容片段（已包好当前视野的 .map-view-g）。

     单独导出是为了支持「按需加载后原地替换」：geo-wterrain.js 到位后
     只需把这段塞进已有的 .map-terrain-layer，不必整张地图重建 ——
     否则用户的缩放、平移状态与图层勾选态都会丢。 */
  function terrainLayerInner() {
    return lyrTerrain();
  }

  /* 经纬网：恒定显示（底图已不含设计稿里烘死的网格），内容按**当前视野**生成——
     只画落在画布内的整度线，所以缩放后必须重算（见 syncView）。它显式收参而不是
     读 VIEW 全局量，就是为了能被重算调用。

     步长自适应：从 1° 起取第一个让可见范围内线数不超过 10 的步长。
     固定 5° 时缩到 15% 会只剩两三条线、放大到 600% 又一条都看不见，
     两种极端下经纬网都失去参照意义。 */
  var GRAT_STEPS = [1, 2, 5, 10, 20];
  var GRAT_MAX_LINES = 10;

  function graticuleStep(span) {
    for (var i = 0; i < GRAT_STEPS.length; i++) {
      if (span / GRAT_STEPS[i] <= GRAT_MAX_LINES) { return GRAT_STEPS[i]; }
    }
    return GRAT_STEPS[GRAT_STEPS.length - 1];
  }

  /** 经度标签：约定 -180..180 显示，换算成 E/W */
  function gratLonLabel(lon) {
    var x = ((lon % 360) + 360) % 360;
    if (x > 180) { x = 360 - x; return r2(x) + '°W'; }
    return r2(x) + '°E';
  }

  /** 纬度标签：0 不带方向；超出 ±85°（投影在极点附近已失真）返回空串以跳过 */
  function gratLatLabel(lat) {
    if (lat > 85 || lat < -85) { return ''; }
    if (lat === 0) { return '0°'; }
    return r2(Math.abs(lat)) + '°' + (lat > 0 ? 'N' : 'S');
  }

  function graticuleInner(v) {
    var a = viewUnproj(0, 0, v);
    var b = viewUnproj(MW, MH, v);
    var lonLo = Math.min(a[0], b[0]), lonHi = Math.max(a[0], b[0]);
    var latLo = Math.max(-85, Math.min(a[1], b[1]));
    var latHi = Math.min(85, Math.max(a[1], b[1]));

    var step = graticuleStep(lonHi - lonLo);
    var s = '';
    var lon, lat, p, q, txt;

    for (lon = Math.ceil(lonLo / step) * step; lon <= lonHi; lon += step) {
      p = proj(lon, latHi);
      q = proj(lon, latLo);
      s += '<line class="map-grat" x1="' + p[0] + '" y1="' + p[1] + '" x2="' + q[0] + '" y2="' + q[1] + '"/>';
      txt = gratLonLabel(lon);
      if (txt) {
        s += '<text class="map-grat-label" x="' + (p[0] + 4) + '" y="' + (p[1] + 12) + '">' + txt + '</text>';
      }
    }
    for (lat = Math.ceil(latLo / step) * step; lat <= latHi; lat += step) {
      p = proj(lonLo, lat);
      q = proj(lonHi, lat);
      s += '<line class="map-grat" x1="' + p[0] + '" y1="' + p[1] + '" x2="' + q[0] + '" y2="' + q[1] + '"/>';
      txt = gratLatLabel(lat);
      if (txt) {
        s += '<text class="map-grat-label" x="' + (p[0] + 4) + '" y="' + (p[1] - 4) + '">' + txt + '</text>';
      }
    }
    return s;
  }

  function lyrGraticule(v) {
    return '<svg class="map-layer map-grat-layer" data-layer="graticule" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      withView(graticuleInner(v), v, MW, MH) + '</svg>';
  }

  /**
   * 视野提示条文案。
   *
   * <p>自动适应与手动调整必须给不同的话：前者是"你没动，是程序为了让你看见而缩的"；
   * 后者要顺带说明怎么回去——地图被拖歪、缩得只剩一小块之后，
   * 没有这句提示用户只会以为页面坏了。
   *
   * <p>手动分支刻意**不说"已缩放"**：用户可能只拖了一下、比例尺仍是 100%，
   * 写成"已手动缩放至 100%"会让人以为操作没生效（本轮实测截图里就是这样）。
   *
   * <p>「为什么要缩」的那半句随页面变化：监测总览是台风路径，沙盘是推演路径。
   * 用 {@link MAP_KIND} 而不是参数传进来，是因为 {@link syncView} 会在缩放/拖拽时
   * 原地重写这段文案，那时手上只有一张已经渲染好的地图，没有调用栈可传参。
   *
   * @param {Object} v 视野
   * @returns {string} 提示文案（百分比用 <b> 高亮）
   */
  function viewNoteText(v) {
    var kind = MAP_KIND === 'sandbox' ? '推演路径' : '台风路径';
    if (ZOOM.k === 1 && ZOOM.px === 0 && ZOOM.py === 0) {
      /* 只平移没缩放时不能说"已缩放至 100%" —— 那等于告诉用户"程序做了事但你看不出来"。
         地图窗口扩到 100°E—180°E 之后，越界多半只是路径偏在一边、需要平移，
         缩放倍率确实还是 100%。 */
      if (v.s < 0.995) {
        return '为完整显示' + kind + '，地图已自动缩放至 <b>' + Math.round(v.s * 100) + '%</b>';
      }
      return '为完整显示' + kind + '，地图已自动移动视野';
    }
    return '视野已手动调整（缩放 <b>' + Math.round(v.s * 100) +
      '%</b>）· 拖拽平移，点「重置」回到自动适应视野';
  }

  /* 视野非恒等时在地图角上明示，避免用户以为地图"自己变小了"。
     这个提示条恒在 DOM 里，靠 hidden 属性控制显隐（syncView 原地改文案）。 */
  function viewNote() {
    return '<div class="map-view-note"' + (VIEW.auto ? '' : ' hidden') + '>' +
      viewNoteText(VIEW) + '</div>';
  }

  /* 地图整体组装
     图层自下而上：底图 → 台风本体(云系+风圈) → 多机构预报 → AI 测算 → 轨迹
                   → 影响城市 → 台风中心 → 地名标注
     台风本体拆成 under / over 两组，使路径线压在风圈之上、中心标记又压在路径之上，
     两组共用同一 transform 由 movePhoto() 同步平移。

     全部图层数据均由调用方通过 opts 注入（opts.cities / affectedCities / windCircles
     / tracks / frames / paths / ai）。本组件不读取任何演示数据；某项未传时渲染为空图层，
     由页面的错误态提示负责向用户说明，不做静默降级。                        */
  /* 「必须看得见」的点集：轨迹（或回放帧）、多机构预报路径、AI 测算路径。
     城市刻意不参与——它们是背景参照，散布在整个东南沿海，
     若纳入视野会把地图压得比轨迹本身还小。 */
  function focusPoints(o) {
    var list = [];
    var tracks = o.tracks || [];
    var frames = o.frames;
    var paths = o.paths || [];
    var ai = o.ai;

    if (frames && frames.length > 1) {
      frames.forEach(function (f) { list.push([f.lon, f.lat]); });
    } else {
      tracks.forEach(function (t) { list.push([t.lon, t.lat]); });
    }
    paths.forEach(function (p) {
      (p.points || []).forEach(function (q) { list.push(q); });
    });
    if (ai && ai.points) {
      ai.points.forEach(function (q) { list.push(q); });
    }

    return list.map(function (p) {
      var lon = Number(p[0]), lat = Number(p[1]);
      return isFinite(lon) && isFinite(lat) ? proj(lon, lat) : null;
    });
  }

  /**
   * 滤镜与渐变的定义层。
   *
   * <p>单独放一个 0×0 的 {@code <svg>}，而不是塞进某个可见图层里：
   * defs 是文档级作用域没有错，但把一个 defs 放进会被 {@code display:none}
   * 的图层（例如非卫星底图下的云图层）后，其他图层对它的引用是否仍然解析，
   * 各浏览器实现并不一致——这种事不值得赌。
   *
   * @returns {string} 定义层 HTML
   */
  function lyrDefs() {
    return '<svg class="map-defs" width="0" height="0" aria-hidden="true" focusable="false" ' +
      'xmlns="http://www.w3.org/2000/svg">' + CLOUD_DEFS + '</svg>';
  }

  /**
   * 组装 P1 深色实况地图。
   *
   * <p>`opts.zoom` 为真时叠加浮动缩放控件，并由页面再调 {@link ZFZ.ui.bindMapView}
   * 接管滚轮与拖拽。空态 / 加载态不传该选项：那时地图上没有可变的内容，
   * 给个能拖能缩的空底图只会让人以为界面坏了。
   *
   * @param {Object} [opts] 图层数据（cities / affectedCities / windCircles / tracks
   *   / frames / paths / ai）与 `zoom` 开关
   * @returns {string} 地图 HTML
   */
  function mapCanvas(opts) {
    var o = opts || {};
    var circles = o.windCircles || [];
    var tracks  = o.tracks || [];
    var frames  = o.frames || null;

    /* 先定视野：真实台风可能落在底图覆盖范围之外，原样投影会把整条路径画到
       画布外，页面上就是"路径线消失了"。装得下时这里不做任何事。
       每次重建地图都把用户的缩放/平移清零——换了台风（或换了页面数据）之后，
       上一个台风的视野平移没有任何参考意义，留着只会让人一脸茫然。 */
    MAP_KIND = 'live';
    FIT = fitBox(focusPoints(o), MW, MH, VIEW_PAD);
    ZOOM = { k: 1, px: 0, py: 0 };
    VIEW = composeView();

    var t0 = photoTransform(PHOTO_X, PHOTO_Y);
    var svgOpen = function (name) {
      return '<svg class="map-layer" data-layer="' + name + '" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
        'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">';
    };

    /* 图层自下而上：陆地填充（底）→ **地形（晕渲/设色/等高线）**
                   → 海岸线 → 云图 → 经纬网
                   → 台风本体（云系 + 风圈）
                   → 多机构预报 → AI 测算 → 轨迹 → 城市点位 → 台风中心 → 地名标注
       台风本体拆成 under / over 两组，使路径线压在风圈之上、中心标记又压在路径之上，
       两组共用同一 transform 由 movePhoto() 同步平移。

       顺序有四条硬约束：① labels 必须是**最后一个** svg.map-layer（回归脚本按此断言
       层级顺序）；② 云图压在地名之下，否则缩小时云会把地名糊掉；
       ③ 地形压在经纬网与台风图层之下 —— 等高线是背景参考信息，
       压在路径线之上会把路径割断；④ **海岸线必须在地形之上**（三合一后
       地形位图不透明覆盖陆地，岸线若在其下会被遮掉一半）。 */
    var layers = lyrDefs() + lyrLand() + lyrTerrain() + lyrCoast() + lyrCloud() + lyrGraticule(VIEW) +

      svgOpen('photo-under') + withView(
        '<g class="ty-photo" transform="' + t0 + '">' +
          cloudBand(PHOTO_X, PHOTO_Y) +
          windRings(circles, PHOTO_X, PHOTO_Y) +
        '</g>', VIEW, MW, MH) +
      '</svg>' +

      lyrForecast(o.paths || []) +
      lyrAi(o.ai || null) +
      lyrTrack(tracks, frames) +
      lyrCities(o.cities || [], o.affectedCities || []) +

      svgOpen('photo-over') +
        withView('<g class="ty-photo" transform="' + t0 + '">' + centerMark(PHOTO_X, PHOTO_Y) + '</g>',
          VIEW, MW, MH) +
      '</svg>' +

      lyrLabels(o.cities || []);

    return mapFrame(o, layers, mapLegend());
  }

  /**
   * 地图外壳：一块 `.map-canvas`，把图层栈、缩放控件、比例尺、视野提示与图例
   * 按固定层次装进去。
   *
   * <p>抽出来是因为 P1（实况）与 P6（沙盘）现在共用同一套地图引擎：
   * 底图、地形、云图、经纬网、地名、缩放平移、比例尺全部同源，两者**唯一的差别**
   * 是中间那几层画什么（台风本体 / 两条推演路径）。外壳若各写一份，
   * 迟早会出现"一个页面能拖、另一个不能"或者"提示条文案不一致"这类偏差。
   *
   * <p>`VIEW` 必须由调用方在调本函数之前算好（fitBox → composeView），
   * 因为图层在生成时已经按当前视野包好了 `.map-view-g`。
   *
   * @param {Object} o 地图选项（只用到 `zoom`）
   * @param {string} layers 图层栈 HTML
   * @param {string} legendHtml 图例 HTML（P1 用 mapLegend()，沙盘用 sandboxLegend()）
   * @returns {string} 地图 HTML
   */
  function mapFrame(o, layers, legendHtml) {
    /* 三合一之后没有「底图切换」，`data-terrain` 只表示地形数据的加载状态：
       pending（还没加载）/ loading / ready / error。CSS 用它控制提示文案与图例。 */
    return '<div class="map-canvas" data-terrain="' + (terrainData() ? 'ready' : 'pending') + '"' +
      viewAttr(VIEW) +
      ' data-zoom="' + Math.round(ZOOM.k * 100) / 100 + '" data-lod="' + lodLevel(VIEW.s) + '"' +
      ' style="--lbl-k:' + r2(1 / (VIEW.s || 1)) + '">' + layers +
      (o.zoom ? mapZoomCtl() : '') +
      '<div class="map-scale">' + scaleBarInner(VIEW) + '</div>' +
      viewNote() + (legendHtml || '') + '</div>';
  }

  /* 地图工具栏（只留图层开关）

     原先这里还有一组「普通地图 / 卫星云图 / 地形图」分段按钮。现在三种表现
     已经**合并成一张图**（真实海岸线 + 地形晕渲 + 云系同屏叠加），切换没有意义，
     按钮整组删掉 —— 保留一排点了没反应的按钮比没有更糟。

     图层项形如 `{ name, target }`：`target` 是 CSS 选择器，写进按钮的
     `data-layer-target`，由 {@link bindMapControls} 直接消费；
     `name` 只用于显示（P1 的 overview.js 仍按名字查自己的表，保持兼容）。 */
  var DEFAULT_LAYERS = [
    { name: '风圈', target: '.ty-wind' },
    { name: '预报路径', target: '.map-layer[data-layer="forecast"], .map-layer[data-layer="ai"]' },
    { name: '城市点位', target: '.map-layer[data-layer="cities"]' }
  ];

  function mapToolbar(o) {
    o = o || {};
    var layers = (o.layers || DEFAULT_LAYERS).map(function (it) {
      var name = typeof it === 'string' ? it : it.name;
      var target = typeof it === 'string' ? '' : (it.target || '');
      return '<button class="layer-toggle is-active" type="button" data-layer-toggle="' + esc(name) + '"' +
        (target ? ' data-layer-target="' + esc(target) + '"' : '') + '>' + esc(name) + '</button>';
    }).join('');
    return '<div class="map-toolbar"><div class="layer-switches">' + layers + '</div></div>';
  }

  /**
   * 把地图工具栏接上：底图切换（含地形图按需加载）、图层开关、视野缩放平移。
   *
   * <p>P1 的 overview.js 里有一份等价实现（它额外要处理时间轴、还要在换台风后
   * 重建中列），这里这份是给沙盘页用的通用版：一次绑定、事件委托，
   * 面板局部重建后不需要重新调用。
   *
   * <p>图层开关优先读按钮上的 `data-layer-target`；没写则回落到 P1 那套按名字查表，
   * 因此两边的工具栏 DOM 可以完全一致。
   *
   * @param {Element} canvas `.map-canvas` 容器
   * @param {Element} [scope] 事件委托的根节点，默认 `document`
   * @returns {void}
   */
  var LAYER_BY_NAME = {
    '风圈': '.ty-wind',
    '预报路径': '.map-layer[data-layer="forecast"], .map-layer[data-layer="ai"]',
    '城市点位': '.map-layer[data-layer="cities"]',
    '地名': '.map-layer[data-layer="labels"]'
  };

  function bindMapControls(canvas, scope) {
    var root = scope || document;

    /* 幂等：沙盘每次重算路径都会重建地图面板，调用方在每个渲染分支末尾顺手
       再调一次最省心。事件只挂一次（挂在 root 上做委托，重建 DOM 不影响），
       重复调用只重新接管缩放交互。不这么写会出现"点一下切两次底图"，
       因为同一个 root 上挂了 N 个一模一样的监听器。 */
    if (!root.getAttribute('data-map-controls')) {
      root.setAttribute('data-map-controls', '1');
      root.addEventListener('click', onMapToolbarClick);
    }

    bindMapView(canvas);
  }

  /**
   * 地图工具栏的点击处理（底图切换 / 图层开关）。
   * 从 {@link bindMapControls} 抽出来只是为了让它可被单独挂载。
   * @param {Event} e 点击事件
   * @returns {void}
   */
  function onMapToolbarClick(e) {
    var root = e.currentTarget;
    var t = e.target;
    if (!t || !t.closest) { return; }

    var btn = t.closest('[data-layer-toggle]');
    if (btn) {
      var sel = btn.getAttribute('data-layer-target') ||
        LAYER_BY_NAME[btn.getAttribute('data-layer-toggle')];
      if (!sel) { return; }
      var nodes = root.querySelectorAll(sel);
      if (!nodes.length) { return; }
      var off = !nodes[0].classList.contains('is-hidden');
      for (var j = 0; j < nodes.length; j++) { nodes[j].classList.toggle('is-hidden', off); }
      btn.classList.toggle('is-active', !off);
      btn.classList.toggle('is-off', off);
    }
  }

  /**
   * 把地形图层与图例补进一个已渲染的地图容器（数据已就绪时才有意义）。
   * @param {Element} box `.map-canvas`
   * @returns {void}
   */
  function fillTerrain(box) {
    if (!box || !terrainData()) { return; }
    box.setAttribute('data-terrain', 'ready');
    var old = box.querySelector('.map-terrain-layer');
    if (old) {
      var tmp = document.createElement('div');
      tmp.innerHTML = terrainLayerInner();
      if (tmp.firstChild) { box.replaceChild(tmp.firstChild, old); }
    }
    var lg = box.querySelector('.map-legend');
    if (lg && !lg.querySelector('.legend-terrain')) {
      lg.insertAdjacentHTML('beforeend', terrainLegend());
    }
  }

  /**
   * 地图渲染后调用：确保地形数据已加载，就绪后**原地**补进地形图层与图例。
   *
   * <p>三合一之后地形是常显图层，但数据仍有 1.2MB（等高线二十万点），
   * 同步 `<script>` 会让首屏白等。于是策略是「先渲染空地形层，再按需补」：
   *   ① {@link mapFrame} 已经输出一个空的 `.map-terrain-layer` 占位；
   *   ② 这里首次调用触发加载，容器打 `data-terrain="loading"` 显示提示；
   *   ③ 到位后只替换这一个图层节点 + 追加图例，不整张重建 ——
   *      用户的缩放 / 平移 / 图层勾选态都不会丢。
   *
   * <p>幂等，且**回调里重新查 DOM** 而不是捕获入参：沙盘拖滑杆时地图会被
   * 反复重建，若只认第一次那个已被替换掉的节点，加载完成时新地图就永远
   * 是空地形层 —— 表现是"拖一下就再也不出地形了"。
   *
   * @param {Element} canvas `.map-canvas` 容器
   * @returns {void}
   */
  function ensureTerrain(canvas) {
    if (!canvas) { return; }
    if (terrainData()) { fillTerrain(canvas); return; }
    if (canvas.getAttribute('data-terrain') !== 'ready') {
      canvas.setAttribute('data-terrain', 'loading');
    }
    loadWterrain(function () {
      var list = document.querySelectorAll('.map-canvas');
      for (var i = 0; i < list.length; i++) {
        if (terrainData()) { fillTerrain(list[i]); }
        else { list[i].setAttribute('data-terrain', 'error'); }
      }
    });
  }

  /* ---------------- 时间轴（P1） ----------------
     设计稿：标签行在上、进度条在下；「当前」时刻固定居中。 */
  function timeline(o) {
    o = o || {};
    var pct = o.pct === undefined ? 40 : o.pct;
    return '<div class="timeline">' +
      '<button class="play-btn" type="button" id="timeline-play" aria-label="播放回放">' + icons.play + '</button>' +
      '<div class="timeline-track">' +
        '<div class="timeline-labels"><span>' + esc(o.start) + '</span>' +
          '<span class="is-current">当前 ' + esc(o.current) + '</span>' +
          '<span>' + esc(o.end) + '</span></div>' +
        '<div class="zbar"><i class="zbar-fill" style="width:' + pct + '%"></i></div>' +
      '</div></div>';
  }

  /* ======================================================================
     二、浅色分析地图（P3 概率热力图 / P6 沙盘推演路径）
     直接采用设计稿导出的真实矢量图层（底图 / 影响带 / 推演路径 / 城市点），
     坐标锚定在设计稿的原始像素系（P6 为 1012×366、P3 为 1040×300），
     文字仍走 HTML 浮层、按同一像素系的百分比定位，
     因此既不会被非等比拉伸变形，又能与设计稿逐像素对齐。
     ====================================================================== */
  /* 设计稿像素系 → 百分比浮层 */
  function flabel(text, x, y, W, H, cls) {
    return '<span class="flabel' + (cls ? ' ' + cls : '') + '" style="left:' + r2(x / W * 100) +
      '%;top:' + r2(y / H * 100) + '%">' + esc(text) + '</span>';
  }
  function flabels(list, W, H, cls, v) {
    return list.map(function (a) {
      /* 传入视野时，标注跟着底图一起变换，否则底图缩放后地名会留在原地错位 */
      var p = (v && v.auto) ? viewApply([a[1], a[2]], v, W, H) : [a[1], a[2]];
      return flabel(a[0], p[0], p[1], W, H, cls);
    }).join('');
  }
  /* 图层底：一个设计稿尺寸的 SVG 画布。v 为可选视野，底图随之缩放 / 平移 */
  function ftile(src, W, H, v) {
    var image = '<image href="' + src + '" x="0" y="0" width="' + W + '" height="' + H + '"/>';
    return '<svg class="lmap-svg" viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="none">' +
      (v ? withView(image, v, W, H) : image) + '</svg>';
  }

  /* ---------------- P3 概率登陆分布热力图（1040×300） ---------------- */
  function heatMap() {
    var W = 1040, H = 300;
    return '<div class="lmap lmap-heat">' +
      ftile('assets/svg/lmap-heat.svg', W, H) +
      flabels([
        ['汕头', 423, 184.5], ['厦门', 545, 142.5],
        ['泉州', 596, 127.5], ['福州', 650, 82.5]
      ], W, H) +
      '</div>';
  }

  function heatLegend() {
    return '<div class="chart-legend heat-legend">' +
      '<span class="legend-item"><i class="dot-sq" style="background:#E8453C"></i>高概率登陆区（网格概率 ≥ 50%）</span>' +
      '<span class="legend-item"><i class="dot-sq" style="background:#F5C242"></i>中概率登陆区（网格概率 &lt; 50%）</span>' +
      '</div>';
  }

  /* ---------------- P6 沙盘推演路径对比（与监测总览同一套地图引擎） ----------------

     2026-10-18 改造。原先沙盘用的是一张设计稿导出的浅色矢量底图
     （`assets/svg/lmap-sandbox.svg`，1012×366）：只有海岸线轮廓与省份名，
     没有真实地形、没有地名分级、不能切底图、不能缩放；经纬度还得从设计稿
     烘死的刻度锚点用最小二乘反推（x = 59.50·lon − 6720.67 那一套）。

     现在直接复用 P1 的地图引擎：同一份真实海岸线（`geo-wpac.js`）、
     同一个投影 `proj()`、同一套视野体系（`fitBox` + `ZOOM`）、同一批图层
     （地形 / 云图 / 经纬网 / 地名 / 比例尺 / 缩放控件）。

     这么改的核心理由不是"好看"，而是**口径统一**：沙盘上的那条线必须和监测总览
     上的那条线落在同一个经纬度上，用户才会相信「参数扰动后的路径」是真的相对
     CMA 预报算出来的。两套各画各的坐标系，看起来都对，对到一起就对不上。

     两条推演路径是独立图层（可分别开关），叠在底图之上、地名之下。 */

  /* 面向前端的路径点归一化：后端可能回 [lon,lat] 数组，也可能是
     {lon,lat} / {longitude,latitude} 对象；非法点直接丢弃而不是抛错 ——
     一个坏点不该让整张沙盘变空白。 */
  function normPathPoint(p) {
    if (!p || typeof p !== 'object') { return null; }
    var lon, lat;
    if (p.length !== undefined) {
      lon = p[0]; lat = p[1];
    } else {
      lon = p.lon !== undefined ? p.lon : p.longitude;
      lat = p.lat !== undefined ? p.lat : p.latitude;
    }
    var x = Number(lon), y = Number(lat);
    if (!isFinite(x) || !isFinite(y)) { return null; }
    return { lon: x, lat: y };
  }

  function normPathList(list) {
    if (!list || !list.length) { return []; }
    var out = [], i, q;
    for (i = 0; i < list.length; i++) {
      q = normPathPoint(list[i]);
      if (q) { out.push(q); }
    }
    return out;
  }

  /* 两条推演路径的图层。
     拆成两个 `data-layer` 是为了能分别开关：用户经常只想看模拟路径相对基准
     偏了多少，两条叠在一起反而看不清偏的方向。
     颜色走 CSS 变量（tokens.css 的 --map-path-base / --map-path-sim）——原先那对
     深蓝 / 暗红是给浅色底图配的，放到 #0E3B59 的海面上对比度不到 2:1，几乎等于没画。 */

  /** [{lon,lat}] → [[lon,lat]]（proj / pts 吃数组形态） */
  function sbLatLng(list) {
    return list.map(function (p) { return [p.lon, p.lat]; });
  }

  /** 基准路径图层：实线 + 起点圆点 */
  function lyrSandboxBase(list) {
    if (list.length < 2) { return layer('sandbox-base', ''); }
    var p0 = proj(list[0].lon, list[0].lat);
    return layer('sandbox-base',
      '<polyline class="sb-path-base" data-role="baseline" points="' + pts(sbLatLng(list)) + '"/>' +
      '<circle class="sb-path-start" cx="' + p0[0] + '" cy="' + p0[1] + '" r="3.4"/>');
  }

  /** 模拟路径图层：虚线 + 末端圆点（末端即推演终点） */
  function lyrSandboxSim(list) {
    if (list.length < 2) { return layer('sandbox-sim', ''); }
    var last = list[list.length - 1];
    var pe = proj(last.lon, last.lat);
    return layer('sandbox-sim',
      '<polyline class="sb-path-sim" data-role="sim" points="' + pts(sbLatLng(list)) + '"/>' +
      '<circle class="sb-path-end" cx="' + pe[0] + '" cy="' + pe[1] + '" r="4.6"/>');
  }

  /**
   * 沙盘图例（深色底图，与 P1 的 {@link mapLegend} 共用 .map-legend 样式）。
   *
   * <p>没有模拟路径时**如实说明**：沙盘最容易出的偏差是"看起来有两条线，
   * 其实那条虚线是上一轮参数留下的"，所以图例必须紧跟当前真实数据。
   *
   * @param {number} baseLen 基准路径点数
   * @param {number} simLen 模拟路径点数
   * @returns {string} 图例 HTML
   */
  function sandboxLegend(baseLen, simLen) {
    var baseItem = baseLen >= 2
      ? '<div class="legend-item"><svg width="24" height="10">' +
          '<line x1="1" y1="5" x2="23" y2="5" style="stroke:var(--map-path-base)" stroke-width="2.4"/>' +
          '</svg>基准路径（官方预报 CMA）</div>'
      : '<div class="legend-item"><svg width="24" height="10">' +
          '<line x1="1" y1="5" x2="23" y2="5" style="stroke:rgba(255,255,255,.28)" stroke-width="2" stroke-dasharray="4 4"/>' +
          '</svg>基准路径未取到</div>';

    var simItem = simLen >= 2
      ? '<div class="legend-item"><svg width="24" height="10">' +
          '<line x1="1" y1="5" x2="23" y2="5" style="stroke:var(--map-path-sim)" stroke-width="2.6" stroke-dasharray="6 4"/>' +
          '<circle cx="22.5" cy="5" r="2.6" style="fill:var(--map-path-sim)"/>' +
          '</svg>模拟路径（参数扰动后）</div>'
      : '<div class="legend-item"><svg width="24" height="10">' +
          '<line x1="1" y1="5" x2="23" y2="5" style="stroke:rgba(255,255,255,.28)" stroke-width="2" stroke-dasharray="4 4"/>' +
          '</svg>尚未生成模拟路径</div>';

    return '<div class="map-legend is-wide">' + baseItem + simItem + '</div>';
  }

  /**
   * P6 沙盘「路径推演对比」图。
   *
   * <p>与 P1 共用同一套地图引擎（见 {@link mapFrame}）：同一份真实海岸线、
   * 同一个投影、同一套视野与图层，因此这里只需要「算视野 + 摆图层」。
   * 路径点是经纬度，直接用 proj() 投影，不再有任何"设计稿像素标定"。
   *
   * <p>**不清零 ZOOM**：沙盘的图会因为拖动滑杆反复重建，每次都把视野打回 100%
   * 会让人没法"放大盯着某一段，边调参数边看差异"。换基准台风时 FIT 会重算，
   * 缩放倍率保留也符合直觉。
   *
   * @param {Object} [opts]
   * @param {Array} [opts.baseline] 基准路径点 [[lon,lat],...]（CMA 官方预报）
   * @param {Array} [opts.simPath]  参数扰动后的模拟路径点 [[lon,lat],...]
   * @param {Array} [opts.cities]   城市点位（/api/cities），用于把表格里的城市名对回地图
   * @returns {string} 沙盘地图 HTML
   */
  function sandboxMap(opts) {
    var o = opts || {};
    var base = normPathList(o.baseline);
    var sim = normPathList(o.simPath);

    MAP_KIND = 'sandbox';

    /* 视野自适应：两条路径都纳入画布。装得下时恒等（auto=false），
       与底图 1:1；装不下才缩放平移，并由右下角提示条明示。 */
    var anchors = sbLatLng(base).concat(sbLatLng(sim)).map(function (p) {
      return proj(Number(p[0]), Number(p[1]));
    });
    FIT = fitBox(anchors, MW, MH, VIEW_PAD);
    VIEW = composeView();

    var layers = lyrDefs() + lyrLand() + lyrTerrain() + lyrCoast() + lyrCloud() + lyrGraticule(VIEW) +
      lyrSandboxBase(base) + lyrSandboxSim(sim) +
      /* 城市点位与总览同源（/api/cities）：沙盘的落区判断就是"哪几个城市受影响"，
         图上没有城市点，用户没法把表格里的城市名对回地图上的位置。
         这里刻意**不**再调 lyrLabels(cities)：静态地名已经覆盖了这些城市名，
         两套叠在一起会出现重字。 */
      lyrCities(o.cities || [], []) + lyrLabels([]);

    return mapFrame({ zoom: true }, layers, sandboxLegend(base.length, sim.length));
  }

  /* ---------------- P3 登陆点偏差趋势折线 ---------------- */
  function devTrendChart() {
    var data = [{ x: '09-13', v: 60 }, { x: '09-14', v: 95 }, { x: '09-15', v: 150 }];
    var W = 308, H = 116, padL = 16, padR = 14, top = 18, bottom = 30;
    var maxV = 160;
    var step = (W - padL - padR) / (data.length - 1);
    var yOf = function (v) { return top + (1 - v / maxV) * (H - top - bottom); };

    var line = data.map(function (d, i) { return (padL + i * step) + ',' + r2(yOf(d.v)); }).join(' ');
    var area = padL + ',' + yOf(data[0].v) + ' ' + line + ' ' +
      (padL + (data.length - 1) * step) + ',' + (H - bottom) + ' ' + padL + ',' + (H - bottom);

    var s = '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '" xmlns="http://www.w3.org/2000/svg">';
    s += '<polygon points="' + area + '" fill="#D8322B" fill-opacity="0.10"/>';
    s += '<rect x="' + (W - padR - 26) + '" y="' + top + '" width="26" height="' + (H - top - bottom) +
      '" fill="#D8322B" fill-opacity="0.05"/>';
    data.forEach(function (d, i) {
      s += '<line x1="' + r2(padL + i * step) + '" y1="' + top + '" x2="' + r2(padL + i * step) + '" y2="' + (H - bottom) +
        '" stroke="#E4E9EF" stroke-width="1" stroke-dasharray="3 3"/>';
      s += '<text x="' + r2(padL + i * step) + '" y="' + (H - 12) + '" text-anchor="middle" ' +
        'font-size="9" fill="#93A1B0" font-family="Inter, sans-serif">' + d.x + '</text>';
    });
    s += '<line x1="' + padL + '" y1="' + (H - bottom) + '" x2="' + (W - padR) + '" y2="' + (H - bottom) + '" stroke="#E4E9EF" stroke-width="1"/>';
    s += '<polyline points="' + line + '" fill="none" stroke="#D8322B" stroke-width="1.8" stroke-linejoin="round"/>';
    data.forEach(function (d, i) {
      var cx = r2(padL + i * step), cy = r2(yOf(d.v));
      s += '<circle cx="' + cx + '" cy="' + cy + '" r="3.6" fill="#FFF" stroke="#D8322B" stroke-width="1.8"/>';
      s += '<text x="' + cx + '" y="' + (cy - 9) + '" text-anchor="middle" font-size="9" fill="#D8322B" ' +
        'font-family="Inter, sans-serif">' + d.v + '</text>';
    });
    s += '</svg>';
    return s;
  }

  /* ---------------- P7 分组水平条形图 ---------------- */
  function hbarGroups(groups, series) {
    var max = 100;
    return '<div class="hg">' + groups.map(function (g) {
      var rows = g.values.map(function (v, i) {
        return '<div class="hg-row">' +
          '<span class="hg-track"><i style="width:' + (v / max * 100) + '%;background:' + series[i].color + '"></i></span>' +
          '<span class="hg-value">' + v.toFixed(1) + '%</span></div>';
      }).join('');
      return '<div class="hg-group"><span class="hg-name">' + esc(g.name) + '</span>' +
        '<span class="hg-bars">' + rows + '</span></div>';
    }).join('') + '</div>' +
      '<div class="chart-legend hg-legend">' + series.map(function (s) {
        return '<span class="legend-item"><i class="dot-sq" style="background:' + s.color + '"></i>' + esc(s.name) + '</span>';
      }).join('') + '</div>';
  }

  /* ---------------- P8 峰值强度分布条 ---------------- */
  function distBars(items) {
    var max = Math.max.apply(null, items.map(function (i) { return i.count; }));
    return '<div class="dist">' + items.map(function (it) {
      return '<div class="dist-row"><span class="dist-name">' + esc(it.name) + '</span>' +
        '<span class="dist-track"><i style="width:' + (it.count / max * 100) + '%;background:' + it.color + '"></i></span>' +
        '<span class="dist-count">' + it.count + ' 条</span></div>';
    }).join('') + '</div>';
  }

  /* ---------------- P2 风圈半径（四象限） ---------------- */
  function wcRows(items) {
    return '<div class="wc-rows">' + items.map(function (it) {
      return '<div class="wc-row"><span class="wc-head">' +
        '<i class="wc-dot" style="background:' + it.color + '"></i>' +
        '<b class="wc-name" style="color:' + it.color + '">' + it.level + ' 级风圈</b></span>' +
        '<span class="wc-values">' + it.values + '</span></div>';
    }).join('') + '</div>';
  }

  /* ---------------- P2 卫星云图影像（CSS/SVG 合成，无需外链图片） ---------------- */
  function satellite(ts) {
    return '<div class="satellite">' +
      '<svg class="sat-svg" viewBox="0 0 308 126" xmlns="http://www.w3.org/2000/svg">' +
        '<defs>' +
          '<radialGradient id="satEye" cx="50%" cy="50%" r="50%">' +
            '<stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.92"/>' +
            '<stop offset="26%" stop-color="#DCE9F2" stop-opacity="0.62"/>' +
            '<stop offset="58%" stop-color="#8FB4CE" stop-opacity="0.34"/>' +
            '<stop offset="100%" stop-color="#16283C" stop-opacity="0"/>' +
          '</radialGradient>' +
          '<radialGradient id="satBand" cx="50%" cy="50%" r="50%">' +
            '<stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.5"/>' +
            '<stop offset="100%" stop-color="#9FC0D6" stop-opacity="0"/>' +
          '</radialGradient>' +
        '</defs>' +
        '<rect width="308" height="126" fill="#12233A"/>' +
        '<g opacity="0.55">' +
          '<ellipse cx="96" cy="34" rx="70" ry="26" fill="url(#satBand)"/>' +
          '<ellipse cx="66" cy="92" rx="62" ry="24" fill="url(#satBand)"/>' +
          '<ellipse cx="238" cy="46" rx="58" ry="22" fill="url(#satBand)"/>' +
          '<ellipse cx="228" cy="102" rx="66" ry="24" fill="url(#satBand)"/>' +
        '</g>' +
        '<ellipse cx="168" cy="68" rx="76" ry="58" fill="url(#satEye)"/>' +
        '<circle cx="168" cy="68" r="7" fill="#FFFFFF" fill-opacity="0.85"/>' +
        '<circle cx="168" cy="68" r="3" fill="#E8833A"/>' +
        '<g opacity="0.28" stroke="#7FC4E8" stroke-width="1" fill="none">' +
          '<path d="M12 106 C60 92, 96 118, 150 108 C206 98, 246 120, 296 106"/>' +
        '</g>' +
      '</svg>' +
      '<span class="sat-caption">卫星云图 · ' + esc(ts) + '</span>' +
      '</div>';
  }

  /* ---------------- P4 距离测算行 ---------------- */
  function distRows(items) {
    return '<div class="rows dist-rows">' + items.map(function (it) {
      return '<div class="row-item is-sm"><span class="row-name">' + esc(it.city) + ' · ' + esc(it.province) + '</span>' +
        '<span class="row-value"><b class="num-txt">' + it.distance + ' km</b>' +
        '<i class="risk-sep">·</i><em style="color:' + riskColor(it.risk) + '">' + esc(it.risk) + '</em></span></div>';
    }).join('') + '</div>';
  }

  /* ---------------- P8 导出记录行 ---------------- */
  function exportRows(items) {
    return '<div class="rows export-rows">' + items.map(function (it) {
      return '<div class="row-item is-sm"><span class="row-name">' + esc(it.user) + '</span>' +
        '<span class="row-meta">' + esc(it.type) + '</span>' +
        '<span class="row-value">' + esc(it.size) + '</span>' +
        '<span class="row-date">' + esc(it.date) + '</span></div>';
    }).join('') + '</div>';
  }

  /* ---------------- 登录 / 注册卡片 ----------------
     项目原本没有登录/注册界面，这里提供唯一一份表单骨架，供 login.html 与
     管理后台（admin.js 的「请先登录」态）共用，避免两处各拼一套、字段 id 与
     校验规则逐渐漂移。本函数是纯函数：只把入参变成 HTML 字符串，既不绑事件、
     也不发请求；提交、切换、校验全部由调用方自己接管。

     界面默认同时提供登录/注册两个页签；管理后台不需要开放注册，传
     showRegister:false 即可整体隐藏注册相关的页签、字段与切换链接。       */

  /**
   * 登录 / 注册卡片（纯函数，只产出 HTML，不绑定事件）。
   * @param {Object} [opts]
   * @param {'login'|'register'} [opts.mode='login'] 初始展示的表单
   * @param {string} [opts.title] 卡片标题（调用方通常放进 panel，可不用）
   * @param {string} [opts.sub]   副标题（同上，可不用）
   * @param {string} [opts.msg]   顶部提示文案，内部会 esc()；空则不渲染该行
   * @param {string} [opts.msgCls] 'is-error' → 红底；其余 → 中性
   * @param {boolean} [opts.showRegister] 默认 true；为 false 时不渲染 tab 栏、
   *        不渲染注册专属字段、不渲染切换链接
   * @returns {string} 卡片 HTML
   */
  function authForm(opts) {
    var o = opts || {};
    var isReg = o.mode === 'register';
    var mode = isReg ? 'register' : 'login';
    var showRegister = o.showRegister !== false;

    /* 注册专属字段容器：注册态直接显示，登录态用内联 display:none 藏起，
       由调用方切页签时改这一个属性即可，无需重建 DOM。 */
    var regBox = function (label, inputHtml) {
      return '<div class="field" data-auth-register' + (isReg ? '' : ' style="display:none"') + '>' +
        '<span class="field-label">' + esc(label) + '</span>' + inputHtml + '</div>';
    };
    var tabCls = function (active) { return 'auth-tab' + (active ? ' is-active' : ''); };

    var html = '<div class="auth-card">';

    if (showRegister) {
      html += '<div class="auth-tabs">' +
        '<button class="' + tabCls(!isReg) + '" type="button" id="login-tab-login">登录</button>' +
        '<button class="' + tabCls(isReg) + '" type="button" id="login-tab-register">注册</button>' +
        '</div>';
    }

    if (o.msg) {
      html += '<div class="auth-msg' + (o.msgCls ? ' ' + o.msgCls : '') + '">' + esc(o.msg) + '</div>';
    }

    html += '<div class="auth-form" id="auth-form" data-mode="' + mode + '">';

    html += '<div class="field"><span class="field-label" id="login-account-label">' +
        esc(isReg ? '用户名' : '登录账号') + '</span>' +
      '<input class="input" id="login-account" type="text" autocomplete="username" ' +
        'placeholder="如 admin01" maxlength="50"></div>';

    if (showRegister) {
      html += regBox('昵称（选填）',
        '<input class="input" id="login-nickname" type="text" autocomplete="nickname" maxlength="50">');
      html += regBox('邮箱（选填，用于找回密码）',
        '<input class="input" id="login-email" type="email" autocomplete="email" maxlength="100">');
    }

    html += '<div class="field"><span class="field-label">' + esc(isReg ? '设置密码' : '登录密码') + '</span>' +
      '<input class="input" id="login-password" type="password" autocomplete="' +
        (isReg ? 'new-password' : 'current-password') + '" maxlength="64"></div>';

    if (showRegister) {
      html += regBox('确认密码',
        '<input class="input" id="login-password2" type="password" autocomplete="new-password" maxlength="64">');
    }

    html += '<button class="zbtn is-primary" type="button" id="login-submit">' +
      (isReg ? '注册' : '登录') + '</button>';

    if (showRegister) {
      html += '<div class="auth-switch" id="login-switch">' + (isReg ? '已有账号？' : '还没有账号？') +
        '<a href="' + (isReg ? 'login.html' : 'login.html#register') + '" id="login-switch-link">' +
        (isReg ? '去登录' : '立即注册') + '</a></div>';
    }

    html += '</div></div>';
    return html;
  }

  return {
    icons: icons,
    esc: esc,
    tag: tag,
    emptyRow: emptyRow,
    errorBody: errorBody,
    errorPanel: errorPanel,
    loadingRow: loadingRow,
    num: num,
    fixed1: fixed1,
    pad2: pad2,
    fmtTime: fmtTime,
    panel: panel,
    table: table,
    metricRow: metricRow,
    metricGrid: metricGrid,
    simpleRows: simpleRows,
    kvLines: kvLines,
    paramRows: paramRows,
    stackItems: stackItems,
    note: note,
    switchNote: switchNote,
    barLine: barLine,
    chips: chips,
    fieldBox: fieldBox,
    fieldSelect: fieldSelect,
    fieldOptions: fieldOptions,
    tyList: tyList,
    levelColor: levelColor,
    levelOf: levelOf,
    LEVELS: LEVELS,
    riskColor: riskColor,
    RISK: RISK,
    proj: proj,
    mapCanvas: mapCanvas,
    mapToolbar: mapToolbar,
    /* 沙盘页用的通用地图控件绑定（底图切换 + 地形按需加载 + 图层开关 + 缩放）。
       P1 的 overview.js 有一份等价实现，那份还要兼顾换台风后重建中列，暂不合并。 */
    bindMapControls: bindMapControls,
    /* 地图视野：自动适应由 fitBox 在 mapCanvas 内部完成，这一组是"用户可干预"的入口。
       bindMapView 必须在容器插入 DOM 之后再调，否则拿不到节点、滚轮也不会被接管。 */
    bindMapView: bindMapView,
    zoomView: zoomView,
    panView: panView,
    resetView: resetView,
    viewState: viewState,
    mapLegend: mapLegend,
    terrainLegend: terrainLegend,
    /* 地形数据按需加载（约 1.2MB）。三合一后地形常显，页面在每次地图渲染后
       调 ensureTerrain()：先出空图层，就绪后原地补进去，不阻塞首屏。 */
    loadWterrain: loadWterrain,
    ensureTerrain: ensureTerrain,
    terrainReady: terrainData,
    terrainLayerInner: terrainLayerInner,
    timeline: timeline,
    flabel: flabel,
    flabels: flabels,
    heatMap: heatMap,
    heatLegend: heatLegend,
    sandboxMap: sandboxMap,
    devTrendChart: devTrendChart,
    hbarGroups: hbarGroups,
    distBars: distBars,
    wcRows: wcRows,
    satellite: satellite,
    distRows: distRows,
    exportRows: exportRows,
    authForm: authForm,
    layer: layer,
    windRings: windRings,
    centerMark: centerMark,
    cloudBand: cloudBand,
    photoTransform: photoTransform,
    movePhoto: movePhoto,
    scaleWind: scaleWind,
    setTrackProgress: setTrackProgress,
    WIND_BASE_SPEED: WIND_BASE_SPEED,
    lyrForecast: lyrForecast,
    lyrAi: lyrAi,
    lyrTrack: lyrTrack,
    lyrCities: lyrCities,
    lyrLabels: lyrLabels
  };
})();
