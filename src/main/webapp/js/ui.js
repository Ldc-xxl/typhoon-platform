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
     一、深色实况地图（P1 监测总览）
     设计稿地图画布为 804×640，采用等距经纬投影：
       x = 351 + (lon - 118.0894) * 26.79
       y = 198 + (24.4798 - lat) * 27.11
     （已用上海 / 温州 / 福州 / 厦门 / 汕头 / 深圳 / 海口 / 三亚 / 台北 / 那霸
       十个城市坐标反算校验，误差均小于 2px）
     ====================================================================== */
  var MW = 804, MH = 640, LNG0 = 118.0894, LAT0 = 24.4798, KX = 26.79, KY = 27.11;

  function r2(v) { return Math.round(v * 100) / 100; }
  function proj(lon, lat) { return [r2(351 + (lon - LNG0) * KX), r2(198 + (LAT0 - lat) * KY)]; }
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

  /** 自动缩放下限：再小地图就只剩轮廓，失去参照意义 */
  var MIN_VIEW_SCALE = 0.34;

  /** 视野留白（画布像素），避免轨迹贴着边缘 */
  var VIEW_PAD = 46;

  /** 用户缩放后「合成比例尺」的上下限，约对应 15%–600% */
  var ZOOM_MIN = 0.15, ZOOM_MAX = 6;

  /** 单次缩放步进（按钮与滚轮共用），1.25 即每步 ±25% */
  var ZOOM_STEP = 1.25;

  /** 当前挂载了缩放交互的地图容器（由 {@link bindMapView} 写入），原地更新视野要用 */
  var viewHost = null;

  /** 恒等视野 */
  function identityView() { return { s: 1, tx: 0, ty: 0, auto: false }; }

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
   * 把用户倍率夹到「合成比例尺落在 [ZOOM_MIN, ZOOM_MAX]」的区间。
   *
   * <p>夹的是**合成后的 s** 而不是 k：自动适应本身可能已经把 s 压到 0.34，
   * 此时再允许 k 取 1 就会突破下限。另外不允许 k 越过 100%（`hi < 1` 时取 1）——
   * 基准已经比上限还大时，再放大只是把模糊的底图拉得更糊。
   *
   * @returns {void}
   */
  function clampZoom() {
    var base = FIT.s || 1;
    var lo = ZOOM_MIN / base, hi = ZOOM_MAX / base;
    if (hi < 1) { hi = 1; }
    if (lo > 1) { lo = 1; }
    if (ZOOM.k < lo) { ZOOM.k = lo; }
    if (ZOOM.k > hi) { ZOOM.k = hi; }
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
    return [LNG0 + (ux - 351) / KX, LAT0 - (uy - 198) / KY];
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
  var WIND_R = { 7: 48, 10: 19, 12: 7 };
  var WIND_FILL = { 7: ['#FFD166', .13, .55], 10: ['#FF9F43', .18, .70], 12: ['#E8453C', .45, .90] };
  var WIND_BASE_SPEED = 38;

  /* 投影比例尺：20°N 附近 1 像素 ≈ 3.9 km
     由 KX = 26.79 px/° 与 1° 经度 ≈ 111.32·cos20° ≈ 104.6 km 推得。
     风圈接口返回的 r 是真实公里数（7 级圈约 193 km），必须按此比例尺换算成屏幕像素；
     WIND_R 仅作为接口未提供 r 时的兜底屏幕半径。 */
  var KM_PER_PX = 3.9;

  function ringRadius(c) {
    var km = Number(c && c.r);
    if (isFinite(km) && km > 0) { return Math.max(3, r2(km / KM_PER_PX)); }
    return WIND_R[c && c.level] || 12;
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
     仅在「卫星云图」底图下显示（由 CSS 依 .map-canvas[data-basemap] 控制） */
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
     由 CSS 只在 [data-basemap="sat"] 下显示。

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
      CLOUD_INNER =
        '<rect class="cloud-tex" x="' + x0 + '" y="' + y0 + '" width="' + (x1 - x0) +
          '" height="' + (y1 - y0) + '" filter="url(#zfzCloudTex)"/>' +
        '<g class="cloud-field">' + cloudMasses(x0, y0, x1, y1) + '</g>';
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

  /** 分级显示的门槛：返回当前比例尺下允许显示的最高优先级（1/2/3） */
  function lodLevel(s) {
    if (s >= 0.55) { return 3; }
    if (s >= 0.32) { return 2; }
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
      '</div>';
  }

  /* ---------------- 底图：真实海岸线（三套样式共用一份几何） ----------------

     设计稿原先附带三张 804×640 的位图底图，但它们只覆盖 105°E—135°E：
     真实台风常在 130°E—160°E 的远洋，fitBox() 为把整条路径纳入画布会把视野
     缩到 45%，底图随之缩成角落里的一小块，方块之外既没有经纬网也没有地名，
     地图看上去是"残"的。

     现在底图改为**完全由真实海岸线生成**（Natural Earth 1:50m，由
     tools/gen-wpac-coast.py 产出，坐标系与上面的 proj() 逐像素同源）：
     一份 <path> 几何，覆盖约 45°E—239°E / 76°N—35°S，三套样式
     （普通 / 卫星 / 地形）只在 CSS 里换填充与描边色，几何完全相同。
     海面不用图层矩形提供，而是 .map-canvas 的底色（随 [data-basemap] 切换）——
     缩到 15% 时可见范围有 5000 多像素宽，任何固定大小的矩形都铺不满，
     四周必然露出容器的原始底色。 */

  /** 陆地图层的 path 片段。数据是静态的（748 条 path、约 104KB），
      只拼一次；每次 mapCanvas 重建都重拼一遍纯属浪费。null = 尚未构建 */
  var LAND_INNER = null;

  function landInner() {
    if (LAND_INNER === null) {
      var geo = (window.ZFZ && ZFZ.geo && ZFZ.geo.wpac) || null;
      var s = '';
      if (geo && geo.land) {
        for (var i = 0; i < geo.land.length; i++) {
          /* d 里只有 M / l / Z / 数字 / 空格 / 负号，没有需要转义的字符 */
          s += '<path class="map-land" fill-rule="evenodd" d="' + geo.land[i] + '"/>';
        }
      }
      LAND_INNER = s;
    }
    return LAND_INNER;
  }

  function lyrLand() {
    var inner = landInner();
    if (!inner) { return ''; }
    return '<svg class="map-layer map-land-layer" data-layer="land" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' +
      withView(inner, VIEW, MW, MH) + '</svg>';
  }

  /* 底图三套：几何完全同源（同一份海岸线数据），仅配色不同，见 CSS 的
     .map-canvas[data-basemap=...] .map-land 规则。 */
  var BASEMAPS = [
    { key: 'plain',   name: '普通地图' },
    { key: 'sat',     name: '卫星云图' },
    { key: 'terrain', name: '地形图' }
  ];

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
   * @param {Object} v 视野
   * @returns {string} 提示文案（百分比用 <b> 高亮）
   */
  function viewNoteText(v) {
    if (ZOOM.k === 1 && ZOOM.px === 0 && ZOOM.py === 0) {
      return '为完整显示台风路径，地图已自动缩放至 <b>' + Math.round(v.s * 100) + '%</b>';
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
    FIT = fitBox(focusPoints(o), MW, MH, VIEW_PAD);
    ZOOM = { k: 1, px: 0, py: 0 };
    VIEW = composeView();

    var t0 = photoTransform(PHOTO_X, PHOTO_Y);
    var svgOpen = function (name) {
      return '<svg class="map-layer" data-layer="' + name + '" viewBox="0 0 ' + MW + ' ' + MH + '" ' +
        'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">';
    };

    /* 图层自下而上：陆地（底图）→ 云图 → 经纬网 → 台风本体（云系 + 风圈）
                   → 多机构预报 → AI 测算 → 轨迹 → 城市点位 → 台风中心 → 地名标注
       台风本体拆成 under / over 两组，使路径线压在风圈之上、中心标记又压在路径之上，
       两组共用同一 transform 由 movePhoto() 同步平移。

       顺序有两条硬约束：① labels 必须是**最后一个** svg.map-layer（回归脚本按此断言
       层级顺序）；② 云图压在地名之下，否则缩小时云会把地名糊掉。 */
    var layers = lyrDefs() + lyrLand() + lyrCloud() + lyrGraticule(VIEW) +

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

    return '<div class="map-canvas" data-basemap="plain"' + viewAttr(VIEW) +
      ' data-zoom="1" data-lod="' + lodLevel(VIEW.s) + '"' +
      ' style="--lbl-k:' + r2(1 / (VIEW.s || 1)) + '">' + layers +
      (o.zoom ? mapZoomCtl() : '') +
      '<div class="map-scale">' + scaleBarInner(VIEW) + '</div>' +
      viewNote() + mapLegend() + '</div>';
  }

  /* 地图工具栏（底图切换 + 图层开关，P1） */
  function mapToolbar(o) {
    o = o || {};
    var bases = BASEMAPS.map(function (b, i) {
      return '<button class="basemap' + (i === 0 ? ' is-active' : '') + '" type="button" ' +
        'data-basemap-btn="' + b.key + '" aria-pressed="' + (i === 0) + '">' + esc(b.name) + '</button>';
    }).join('');
    var layers = (o.layers || ['风圈', '预报路径', '城市点位']).map(function (n) {
      return '<button class="layer-toggle is-active" type="button" data-layer-toggle="' + esc(n) + '">' + esc(n) + '</button>';
    }).join('');
    return '<div class="map-toolbar">' +
      '<div class="map-basemaps">' + bases + '</div>' +
      '<div class="layer-switches">' + layers + '</div></div>';
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

  /* 图例（浅色地图右上角） */
  function lightLegend(items) {
    return '<div class="lmap-legend">' + items.map(function (it) {
      var mark = it.dash
        ? '<svg width="22" height="8"><line x1="0" y1="4" x2="22" y2="4" stroke="' + it.color + '" stroke-width="2" stroke-dasharray="5 3"/></svg>'
        : '<span class="lmap-legend-bar" style="background:' + it.color + '"></span>';
      return '<div class="lmap-legend-item">' + mark + esc(it.text) + '</div>';
    }).join('') + '</div>';
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

  /* ---------------- P6 沙盘推演路径对比（1012×366，数据驱动） ----------------
     底图仍是设计稿导出的矢量图（只画海岸线与省份轮廓），但三条路径曲线不再画死在
     图里 —— 曲线改由调用方传入的经纬点经现场投影后叠加。这样「参数控制」改变推演
     结果时，图上的模拟路径会真的跟着动，而不是永远显示设计稿那张静态底图。

     投影用最小二乘从设计稿经纬刻度锚点现场拟合，避免手抄斜率抄错：
       经度锚点 (115,122) (120,419) (125,717)  → x = 59.50·lon - 6720.67
       纬度锚点 (30,58.5) (25,160.5) (20,261.5) → y = -20.30·lat + 667.67
     结果与设计稿给出的解析解一致（保留两位小数，锚点处误差 < 0.5px）。
     落在 0..W / 0..H 之外的坐标不截断：SVG 根元素默认会裁掉。            */

  /* 一元线性最小二乘：pairs 为 [自变量, 因变量] 数组，返回 y = a·x + b 的系数 */
  function fitLinear(pairs) {
    var n = pairs.length, sx = 0, sy = 0, sxy = 0, sxx = 0, i;
    for (i = 0; i < n; i++) {
      var x = Number(pairs[i][0]), y = Number(pairs[i][1]);
      sx += x; sy += y; sxy += x * y; sxx += x * x;
    }
    var a = (n * sxy - sx * sy) / (n * sxx - sx * sx);
    return { a: a, b: (sy - a * sx) / n };
  }

  /* 路径点归一化：后端可能回 [lon,lat] 数组，也可能是 {lon,lat} / {longitude,latitude}
     对象；非法点直接丢弃而不是抛错 —— 一个坏点不该让整张沙盘变空白。 */
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

  /**
   * P6 沙盘「路径推演对比」图。
   * @param {Object} [opts]
   * @param {Array} [opts.baseline]  基准情景路径点（CMA 官方预报），形如 [[lon,lat],...]
   * @param {Array} [opts.simPath]   本次参数扰动后的模拟路径点，形如 [[lon,lat],...]
   * @param {string} [opts.typhoonLabel] 「台风 · N 级」浮标文案，默认沿用现值
   * @returns {string} 沙盘图 HTML
   */
  function sandboxMap(opts) {
    var o = opts || {};
    var W = 1012, H = 366;

    /* 经纬刻度锚点取自设计稿原始像素，投影系数由锚点现场拟合 */
    var lonTicks = [[115, 122], [120, 419], [125, 717]];
    var latTicks = [[30, 58.5], [25, 160.5], [20, 261.5]];
    var fitX = fitLinear(lonTicks);
    var fitY = fitLinear(latTicks);
    var px = function (lon) { return r2(fitX.a * lon + fitX.b); };
    var py = function (lat) { return r2(fitY.a * lat + fitY.b); };
    var toPts = function (list) {
      return list.map(function (p) { return px(p.lon) + ',' + py(p.lat); }).join(' ');
    };

    var base = normPathList(o.baseline);
    var sim = normPathList(o.simPath);

    /* 视野自适应：设计稿底图只覆盖约 113°E—130°E，而真实台风常在西太平洋
       远洋（例：202629 小熊 157.8°E），原样投影会把整条推演路径画到画布外。
       两条路径都装得下时不产生任何变换，与设计稿逐像素一致。 */
    var sv = fitBox(
      base.map(function (p) { return [px(p.lon), py(p.lat)]; })
        .concat(sim.map(function (p) { return [px(p.lon), py(p.lat)]; })),
      W, H, 26);

    /* 自动缩放 / 平移后底图可能整体移出画面，补一层与底图同投影的经纬网兜底，
       避免画布只剩空白、失去方位参照。与路径同放在叠加层里，顺序在路径之前。 */
    var grat = '';
    if (sv.auto) {
      /* 画布坐标 → 经纬度：先做视口逆变换回到底图原始像素系，再套投影的反函数 */
      var invX = function (x) { return (x - fitX.b) / fitX.a; };
      var invY = function (y) { return (y - fitY.b) / fitY.a; };
      var corner = function (x, y) {
        var u = [(x - W / 2 - sv.tx) / sv.s + W / 2, (y - H / 2 - sv.ty) / sv.s + H / 2];
        return [invX(u[0]), invY(u[1])];
      };
      var c0 = corner(0, 0), c1 = corner(W, H);
      var lonA = Math.floor(Math.min(c0[0], c1[0]) / 5) * 5;
      var lonB = Math.ceil(Math.max(c0[0], c1[0]) / 5) * 5;
      var latA = Math.floor(Math.min(c0[1], c1[1]) / 5) * 5;
      var latB = Math.ceil(Math.max(c0[1], c1[1]) / 5) * 5;
      var glon, glat;
      for (glon = lonA; glon <= lonB; glon += 5) {
        grat += '<line class="lmap-grat" x1="' + px(glon) + '" y1="' + py(latB) +
          '" x2="' + px(glon) + '" y2="' + py(latA) + '"/>' +
          '<text class="lmap-grat-label" x="' + (px(glon) + 4) + '" y="' + (py(latB) + 12) + '">' +
          glon + '°E</text>';
      }
      for (glat = latA; glat <= latB; glat += 5) {
        grat += '<line class="lmap-grat" x1="' + px(lonA) + '" y1="' + py(glat) +
          '" x2="' + px(lonB) + '" y2="' + py(glat) + '"/>' +
          '<text class="lmap-grat-label" x="' + (px(lonA) + 4) + '" y="' + (py(glat) - 4) + '">' +
          glat + '°N</text>';
      }
    }

    /* 叠加层：与底图同尺寸、同 viewBox、非等比拉伸，保证像素系与底图完全对齐 */
    var overlay = grat;
    if (base.length >= 2) {
      overlay += '<polyline data-role="baseline" points="' + toPts(base) + '" fill="none" ' +
        'stroke="#0E5484" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round"/>';
    }
    if (sim.length >= 2) {
      overlay += '<polyline data-role="sim" points="' + toPts(sim) + '" fill="none" ' +
        'stroke="#D8322B" stroke-width="2.6" stroke-dasharray="8 5" stroke-linejoin="round" stroke-linecap="round"/>';
      var end = sim[sim.length - 1];
      overlay += '<circle data-role="sim-end" cx="' + px(end.lon) + '" cy="' + py(end.lat) +
        '" r="4.5" fill="#D8322B" stroke="#FFFFFF" stroke-width="1.5"/>';
    }
    var overlaySvg = '<svg class="lmap-svg is-overlay" viewBox="0 0 ' + W + ' ' + H + '" ' +
      'preserveAspectRatio="none" aria-hidden="true" focusable="false">' +
      withView(overlay, sv, W, H) + '</svg>';

    /* 台风浮标跟随路径末端：优先模拟路径，其次基准，都没有才退回设计稿坐标 */
    var tipX = 687, tipY = 274;
    var tail = sim.length ? sim[sim.length - 1] : (base.length ? base[base.length - 1] : null);
    if (tail) { tipX = px(tail.lon); tipY = py(tail.lat); }
    if (sv.auto) {
      var tip = viewApply([tipX, tipY], sv, W, H);
      tipX = tip[0];
      tipY = tip[1];
    }

    /* 图例按实际数据生成：没有模拟路径时如实说明，不假装仍有三条情景 */
    var legend = [{ color: '#0E5484', text: '基准情景（官方预报 CMA）' }];
    if (sim.length) {
      legend.push({ color: '#D8322B', text: '当前模拟路径（参数扰动后）', dash: true });
    } else if (!base.length) {
      legend.push({ color: '#8A98A8', text: '尚未生成模拟路径', dash: true });
    }

    return '<div class="lmap lmap-sandbox" data-base-pts="' + base.length +
        '" data-sim-pts="' + sim.length + '"' + viewAttr(sv) + '>' +
      ftile('assets/svg/lmap-sandbox.svg', W, H, sv) +
      overlaySvg +
      flabels([['浙江', 428, 43], ['福建', 328, 107], ['广东', 32, 189]], W, H, 'is-prov', sv) +
      flabels([
        ['温州', 411, 102], ['福州', 328, 141], ['泉州', 361, 168],
        ['厦门', 256, 173], ['汕头', 188, 196]
      ], W, H, null, sv) +
      flabel(o.typhoonLabel || '台风 · 12 级', tipX, tipY, W, H, 'is-typhoon') +
      lonTicks.map(function (a) {
        var p = sv.auto ? viewApply([a[1], 354.5], sv, W, H) : [a[1], 354.5];
        return flabel(a[0] + '°E', p[0], p[1], W, H, 'is-axis');
      }).join('') +
      latTicks.map(function (a) {
        var p = sv.auto ? viewApply([16, a[1]], sv, W, H) : [16, a[1]];
        return flabel(a[0] + '°N', p[0], p[1], W, H, 'is-axis is-lat');
      }).join('') +
      lightLegend(legend) +
      (sv.auto
        ? '<div class="lmap-view-note">为完整显示推演路径，地图已自动缩放至 <b>' +
          Math.round(sv.s * 100) + '%</b></div>'
        : '') +
      '</div>';
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
    /* 地图视野：自动适应由 fitBox 在 mapCanvas 内部完成，这一组是"用户可干预"的入口。
       bindMapView 必须在容器插入 DOM 之后再调，否则拿不到节点、滚轮也不会被接管。 */
    bindMapView: bindMapView,
    zoomView: zoomView,
    panView: panView,
    resetView: resetView,
    viewState: viewState,
    mapLegend: mapLegend,
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
    BASEMAPS: BASEMAPS,
    WIND_BASE_SPEED: WIND_BASE_SPEED,
    lyrForecast: lyrForecast,
    lyrAi: lyrAi,
    lyrTrack: lyrTrack,
    lyrCities: lyrCities,
    lyrLabels: lyrLabels
  };
})();
