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

  /* ---------------- 台风风险等级工具 ---------------- */
  var RISK = { high: '#D8322B', mid: '#C77400', low: '#2FA84F' };
  function riskColor(text) {
    if (/高/.test(text)) { return RISK.high; }
    if (/中/.test(text)) { return RISK.mid; }
    if (/低/.test(text)) { return RISK.low; }
    return '#5B6B7B';
  }

  /* ---------------- 左列台风列表（数据由调用方传入，本组件不读取任何演示数据） ---------------- */
  function tyList(items, activeId) {
    return '<div class="ty-list">' + (items || []).map(function (t) {
      var lv = levelOf(t.windLevel);
      var statusText = t.statusText || (t.status === 'ACTIVE' ? '活跃中' : (t.year + ' 已归档'));
      return '<a class="ty-item' + (String(t.id) === String(activeId) ? ' is-active' : '') + '"' +
        ' href="typhoon-detail.html?id=' + t.id + '">' +
        '<span class="ty-item-top">' +
          '<span class="ty-item-name">' + esc(t.nameCn + ' ' + t.nameEn) + '</span>' +
          '<span class="ty-item-level" style="color:' + levelColor(t.windLevel) + '">' +
            esc(lv.name + ' · ' + t.windLevel + '级') + '</span>' +
        '</span>' +
        '<span class="ty-item-sub">' + esc(t.code + ' · ' + t.basin + ' · ' + statusText) + '</span>' +
        '</a>';
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

  function layer(name, inner) {
    return '<svg class="map-layer" data-layer="' + name + '" viewBox="0 0 804 640" ' +
      'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">' + inner + '</svg>';
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
  var CLOUD_DEFS = '<defs><filter id="zfzCloudBlur" x="-50%" y="-50%" width="200%" height="200%">' +
    '<feGaussianBlur stdDeviation="6.5"/></filter></defs>';

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

  /* 受影响城市：金色圆环 + 圆点（取自设计稿 9px 环 / 3.2px 点）
     参数 cities：受影响城市数组，元素形如 { name, lon, lat }，由调用方从接口取。 */
  function lyrAffectedCities(cities) {
    var s = (cities || []).filter(hasCoord).map(function (c) {
      var p = proj(Number(c.lon), Number(c.lat));
      return '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="9" fill="none" stroke="#FFD166" stroke-width="1.2" stroke-opacity="0.9"/>' +
        '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="3.2" fill="#FFD166"/>';
    }).join('');
    return layer('cities', s);
  }

  /* 城市 / 海域 / 经纬 / 比例尺 标注
     参数 cities：需要打点的城市数组，元素形如 { name, lon, lat }；海域名与经纬刻度
     属于底图固定元素，与业务数据无关，因此留在组件内。 */
  function lyrLabels(cities) {
    var s = '';

    /* 城市名：放在投影点右上方 6px（与设计稿一致） */
    (cities || []).filter(hasCoord).forEach(function (c) {
      var p = proj(Number(c.lon), Number(c.lat));
      s += '<text class="map-city-label" x="' + (p[0] + 6) + '" y="' + (p[1] + 6.1) + '">' + esc(c.name) + '</text>';
    });

    /* 海域名称 */
    s += '<text class="map-sea-label" x="290" y="435" letter-spacing="6">南海</text>';
    s += '<text class="map-sea-label" x="600" y="311">西北太平洋</text>';

    /* 经纬刻度 */
    s += '<text class="map-axis-label" x="122" y="630.7">110°E</text>';
    s += '<text class="map-axis-label" x="390" y="630.7">120°E</text>';
    s += '<text class="map-axis-label" x="658" y="630.7">130°E</text>';
    s += '<text class="map-axis-label" x="6" y="454.7">15°N</text>';
    s += '<text class="map-axis-label" x="6" y="188.7">25°N</text>';

    /* 比例尺 */
    s += '<path d="M704 596 L704 604 M704 600 L764 600 M764 596 L764 604" stroke="#5C8CB0" stroke-width="1" fill="none"/>';
    s += '<text class="map-axis-label" x="700" y="630.7">≈ 500 km</text>';

    return layer('labels', s);
  }

  /* 地图图例（HTML 浮层，位于左下角） */
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
      '</div>';
  }

  /* 底图三套：几何完全同源（同一海岸线数据），仅配色与质感不同 */
  var BASEMAPS = [
    { key: 'plain',   name: '普通地图', src: 'assets/svg/map-base.svg' },
    { key: 'sat',     name: '卫星云图', src: 'assets/svg/map-base-sat.svg' },
    { key: 'terrain', name: '地形图',   src: 'assets/svg/map-base-terrain.svg' }
  ];

  function basemapLayers() {
    return BASEMAPS.map(function (b, i) {
      return '<img class="map-layer' + (i === 0 ? '' : ' is-hidden') + '" data-basemap="' + b.key +
        '" src="' + b.src + '" alt="' + esc(b.name) + '">';
    }).join('');
  }

  /* 地图整体组装
     图层自下而上：底图 → 台风本体(云系+风圈) → 多机构预报 → AI 测算 → 轨迹
                   → 影响城市 → 台风中心 → 地名标注
     台风本体拆成 under / over 两组，使路径线压在风圈之上、中心标记又压在路径之上，
     两组共用同一 transform 由 movePhoto() 同步平移。

     全部图层数据均由调用方通过 opts 注入（opts.cities / affectedCities / windCircles
     / tracks / frames / paths / ai）。本组件不读取任何演示数据；某项未传时渲染为空图层，
     由页面的错误态提示负责向用户说明，不做静默降级。                        */
  function mapCanvas(opts) {
    var o = opts || {};
    var circles = o.windCircles || [];
    var tracks  = o.tracks || [];
    var frames  = o.frames || null;

    var t0 = photoTransform(PHOTO_X, PHOTO_Y);
    var svgOpen = function (name) {
      return '<svg class="map-layer" data-layer="' + name + '" viewBox="0 0 804 640" ' +
        'preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">';
    };

    var layers = basemapLayers() +

      svgOpen('photo-under') + CLOUD_DEFS +
        '<g class="ty-photo" transform="' + t0 + '">' +
          cloudBand(PHOTO_X, PHOTO_Y) +
          windRings(circles, PHOTO_X, PHOTO_Y) +
        '</g>' +
      '</svg>' +

      lyrForecast(o.paths || []) +
      lyrAi(o.ai || null) +
      lyrTrack(tracks, frames) +
      lyrAffectedCities(o.affectedCities || []) +

      svgOpen('photo-over') +
        '<g class="ty-photo" transform="' + t0 + '">' + centerMark(PHOTO_X, PHOTO_Y) + '</g>' +
      '</svg>' +

      lyrLabels(o.cities || []);

    return '<div class="map-canvas" data-basemap="plain">' + layers + mapLegend() + '</div>';
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
  function flabels(list, W, H, cls) {
    return list.map(function (a) { return flabel(a[0], a[1], a[2], W, H, cls); }).join('');
  }
  /* 图层底：一个设计稿尺寸的 SVG 画布 */
  function ftile(src, W, H) {
    return '<svg class="lmap-svg" viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="none">' +
      '<image href="' + src + '" x="0" y="0" width="' + W + '" height="' + H + '"/></svg>';
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

  /* ---------------- P6 沙盘推演路径对比（1012×366） ---------------- */
  function sandboxMap() {
    var W = 1012, H = 366;
    /* 经纬刻度锚点取自设计稿原始像素 */
    var lonTicks = [[115, 122], [120, 419], [125, 717]];
    var latTicks = [[30, 58.5], [25, 160.5], [20, 261.5]];
    return '<div class="lmap lmap-sandbox">' +
      ftile('assets/svg/lmap-sandbox.svg', W, H) +
      flabels([['浙江', 428, 43], ['福建', 328, 107], ['广东', 32, 189]], W, H, 'is-prov') +
      flabels([
        ['温州', 411, 102], ['福州', 328, 141], ['泉州', 361, 168],
        ['厦门', 256, 173], ['汕头', 188, 196]
      ], W, H) +
      flabel('台风 · 12 级', 687, 274, W, H, 'is-typhoon') +
      lonTicks.map(function (a) { return flabel(a[0] + '°E', a[1], 354.5, W, H, 'is-axis'); }).join('') +
      latTicks.map(function (a) { return flabel(a[0] + '°N', 16, a[1], W, H, 'is-axis is-lat'); }).join('') +
      lightLegend([
        { color: '#0E5484', text: '基准情景（官方预报）' },
        { color: '#D8322B', text: '情景A（偏西登陆）', dash: true },
        { color: '#2FA84F', text: '情景B（快速转向出海）', dash: true }
      ]) +
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

  return {
    icons: icons,
    esc: esc,
    tag: tag,
    panel: panel,
    table: table,
    metricRow: metricRow,
    metricGrid: metricGrid,
    simpleRows: simpleRows,
    kvLines: kvLines,
    paramRows: paramRows,
    stackItems: stackItems,
    note: note,
    barLine: barLine,
    chips: chips,
    fieldBox: fieldBox,
    tyList: tyList,
    levelColor: levelColor,
    levelOf: levelOf,
    LEVELS: LEVELS,
    riskColor: riskColor,
    RISK: RISK,
    proj: proj,
    mapCanvas: mapCanvas,
    mapToolbar: mapToolbar,
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
    lyrAffectedCities: lyrAffectedCities,
    lyrLabels: lyrLabels
  };
})();
