/* ==========================================================================
   P1 监测总览
   设计稿节点 3:35（1440×900）
   三栏：左侧台风列表 264 / 中间实况地图 flex / 右侧实况参数 316

   数据全部来自后端接口，不再读取 js/data.js 的演示数据：
     · 左栏台风清单            GET /api/typhoons
     · 地图城市标注            GET /api/cities
     · 风圈 / 轨迹 / 预报路径   GET /api/typhoons/{id}/wind-circles | tracks | forecast-paths
     · 回放帧                  GET /api/typhoons/{id}/replay
     · AI 融合测算             GET /api/ai/predictions?typhoonId=
     · 城市风险预评估           GET /api/risk/assessments?typhoonId=

   失败处理：台风清单失败 → 整页错误态；其余子资源失败 → 记入 failures，
   在右栏顶部列明「哪一块没加载出来 + 原因」，绝不回退成演示数据。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui, A = ZFZ.api;

  /* ---------------- 页面状态 ---------------- */
  var state = {
    all: [], active: [], history: [],
    live: null,
    cities: [], affected: [], assessments: [],
    tracks: [], windCircles: [], paths: [], ai: null, replay: null,
    failures: [],
    pct: 40, timer: null,
    t0: null, totalH: 0, step: 1
  };

  /* ---------------- 小工具 ---------------- */

  /**
   * 生成一行「空态 / 提示」文案（已转义）。
   * @param {string} text 提示内容
   * @returns {string} 空态行 HTML
   */
  function hint(text) { return '<div class="empty-row">' + U.esc(text) + '</div>'; }

  /**
   * 数字补零成两位，用于拼时间文案。
   * @param {number} n 待补零的数字
   * @returns {string} 两位字符串，如 7 → "07"
   */
  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  /**
   * 解析后端返回的 'yyyy-MM-dd HH:mm:ss' 时间串。
   *
   * <p>后端时间格式带空格，部分浏览器对其 `new Date()` 会得到 Invalid Date，
   * 因此显式按段解析，避免时间轴文案变成 NaN。
   *
   * @param {string} s 时间字符串
   * @returns {Date|null} 解析成功返回 Date；格式不符或为空返回 null
   */
  function parseTime(s) {
    var m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(s || ''));
    return m ? new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0)) : null;
  }

  /**
   * 把 Date 格式化为 'yyyy-MM-dd HH:00'（整点，用于时间轴刻度）。
   * @param {Date} d 待格式化的时间
   * @returns {string} 形如 "2026-09-16 08:00"
   */
  function clock(d) {
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
      ' ' + pad2(d.getHours()) + ':00';
  }

  /**
   * 当前聚焦台风的主键（用于列表高亮）。
   * @returns {number|null} 台风 id；无聚焦台风时为 null
   */
  function liveId() { return state.live ? state.live.id : null; }

  /**
   * 包装一个「可选子资源」请求：失败不打断整页渲染，只登记到 failures 统一提示。
   *
   * @param {string} label 该资源的中文名，用于失败清单展示
   * @param {Promise} promise 原始请求 Promise
   * @param {*} fallback 失败时的兜底值
   * @returns {Promise} 成功时给原值、失败时给 fallback 的 Promise
   */
  function optional(label, promise, fallback) {
    return promise.then(null, function (err) {
      state.failures.push(label + ' —— ' + ((err && err.message) || '未知错误'));
      return fallback;
    });
  }

  /* ================= 数据加载 ================= */

  /**
   * 拉取整页所需数据并渲染。
   *
   * <p>台风清单是<b>必需</b>资源：它失败则整页进 `fatal()` 错误态；
   * 城市、轨迹、风圈、预报路径、回放帧、AI 测算、风险评估均为<b>可选</b>资源，
   * 经 `optional()` 包装，失败只登记进 `state.failures` 由右栏提示。
   *
   * @returns {void}
   */
  function load() {
    if (!A) {
      fatal('接口层未加载（js/api.js），无法获取数据');
      return;
    }

    A.getTyphoons({ limit: 200 }).then(function (list) {
      state.all = list || [];
      state.active = state.all.filter(function (t) { return t.status === 'ACTIVE'; });
      state.history = state.all.filter(function (t) { return t.status !== 'ACTIVE'; });
      state.live = state.active[0] || state.all[0] || null;

      if (!state.live) { renderAll(); return; }

      var id = state.live.id;
      return Promise.all([
        optional('城市清单', A.getCities(), []),
        optional('实况轨迹', A.getTracks(id), []),
        optional('风圈数据', A.getWindCircles(id), []),
        optional('预报路径', A.getForecastPaths(id), []),
        optional('回放帧', A.getReplay(id), null),
        optional('AI 测算', A.getAiPredictions(id), null),
        optional('城市风险预评估', A.getRiskAssessments(id), [])
      ]).then(function (r) {
        state.cities = r[0] || [];
        state.tracks = r[1] || [];
        state.windCircles = r[2] || [];
        state.paths = r[3] || [];
        state.replay = r[4] || null;
        state.ai = r[5] || null;
        state.assessments = r[6] || [];

        /* 金色圆环标在「真正落入风圈」的城市上，而不是全部参与评估的城市 */
        state.affected = state.assessments.filter(function (c) { return c.inWindCircle === true; });

        /* 回放时间基准：优先用接口给的起止时刻，缺失时退回帧间隔推算 */
        var rp = state.replay;
        if (rp && rp.frames && rp.frames.length > 1) {
          state.step = Number(rp.step) > 0 ? Number(rp.step) : 1;
          state.t0 = parseTime(rp.startTime) || parseTime(rp.frames[0].time);
          var end = parseTime(rp.endTime) || parseTime(rp.frames[rp.frames.length - 1].time);
          if (state.t0 && end) {
            state.totalH = Math.max(state.step, (end.getTime() - state.t0.getTime()) / 3600000);
          } else {
            state.totalH = (rp.frames.length - 1) * state.step;
          }
        }
      });
    }).then(renderAll, function (err) {
      fatal('台风清单加载失败：' + ((err && err.message) || '未知错误'));
    });
  }

  /* ================= 渲染 ================= */

  /**
   * 渲染左列：台风列表（活跃 / 历史 两组分列）。
   *
   * <p>设计稿原图为「活跃 1 + 历史 6」混排在同一列，与 Tab 语义冲突；
   * 此处按 `status` 真实分组，Tab 驱动两套数据，互不混排。
   *
   * @returns {string} 左列 HTML
   */
  function leftColumn() {
    var body =
      '<div class="tabs" id="ty-tabs" role="tablist">' +
        '<button class="tab is-active" type="button" role="tab" aria-selected="true" data-group="active">' +
          '活跃台风<i class="tab-count">' + state.active.length + '</i>' +
        '</button>' +
        '<button class="tab" type="button" role="tab" aria-selected="false" data-group="history">' +
          '历史档案<i class="tab-count">' + state.history.length + '</i>' +
        '</button>' +
      '</div>' +
      '<label class="search-box is-block">' + U.icons.search +
        '<input type="text" id="ty-search" placeholder="搜索台风名称 / 编号" autocomplete="off">' +
      '</label>' +
      '<div class="ty-list-host" id="ty-list-host"></div>' +
      '<div id="ty-hint"></div>' +
      '<div class="panel-foot" id="ty-foot"></div>';

    return '<div class="colstack col-left">' +
      U.panel({
        title: '台风列表',
        sub: '活跃 ' + state.active.length + ' · 历史 ' + state.history.length,
        cls: 'is-tight',
        body: body
      }) +
      '</div>';
  }

  /**
   * 渲染中列：地图区（底图 + 风圈 + 轨迹 + 预报路径 + AI 路径 + 时间轴）。
   *
   * <p>`frames` 传入后由 `lyrTrack` 自动改用回放帧绘制完整路径；
   * 风圈半径取接口真实公里数（见 `ui.js` 的 `KM_PER_PX` 换算）。
   * 有回放数据时才渲染时间轴，否则只出地图不出现空时间轴。
   *
   * @returns {string} 中列 HTML
   */
  function mapColumn() {
    var rp = state.replay, hasFrames = !!(rp && rp.frames && rp.frames.length > 1);

    var tl = '';
    if (hasFrames && state.t0) {
      var at = new Date(state.t0.getTime() + state.pct / 100 * state.totalH * 3600000);
      tl = U.timeline({
        pct: state.pct,
        start: clock(state.t0),
        current: clock(at),
        end: clock(new Date(state.t0.getTime() + state.totalH * 3600000))
      });
    }

    return '<div class="colstack col-map">' +
      '<div class="map-card">' +
        U.mapToolbar() +
        U.mapCanvas({
          cities: state.cities,
          affectedCities: state.affected,
          windCircles: state.windCircles,
          tracks: state.tracks,
          frames: hasFrames ? rp.frames : null,
          paths: state.paths,
          ai: state.ai
        }) +
        tl +
      '</div>' +
      '</div>';
  }

  /**
   * 渲染右列 · 实况参数卡（随回放进度刷新的指标块）。
   *
   * <p>各指标均带 `data-metric` 锚点，回放时由 `render()` 按锚点就地改数值，
   * 避免整块重绘导致的闪烁。
   *
   * @returns {string} 实况参数卡 HTML
   */
  function livePanel() {
    var t = state.live;
    var lv = U.levelOf(t.windLevel);
    var pos = coordinateText(t);

    var head =
      '<div class="panel-head">' +
        '<span class="panel-title is-lg">' +
          '<b style="color:var(--ink)">' + U.esc(t.nameCn) + '</b> ' +
          '<b style="color:var(--brand);letter-spacing:.5px">' + U.esc(t.nameEn) + '</b>' +
        '</span>' +
        '<span class="tag ' + lv.cls + '" data-metric="levelTag">' +
          U.esc(lv.name + ' · ' + t.windLevel + '级') + '</span>' +
      '</div>' +
      '<div class="hero-meta">' + U.esc(t.code + ' · ' + t.basin + ' · ' + (t.statusText || '')) + '</div>';

    var params = U.metricGrid([
      { key: 'level',    value: t.windLevel + ' 级',                    label: '中心最大风力' },
      { key: 'speed',    value: t.windSpeed + ' m/s',                   label: '最大风速' },
      { key: 'pressure', value: t.pressure + ' hPa',                    label: '中心最低气压' },
      { key: 'move',     value: moveText(t),                            label: '移动方向（方位角）' },
      { key: 'mph',      value: num(t.moveSpeed) + ' km/h',             label: '移动速度' },
      { key: 'pos',      value: pos,                                    label: '中心位置' }
    ]);

    return U.panel({
      cls: 'is-tight',
      body: head + '<div class="divider-h"></div>' + params
    });
  }

  /**
   * 缺失值占位：null / undefined 统一显示为破折号。
   * @param {*} v 任意值
   * @returns {*} 原值；为 null / undefined 时返回 '—'
   */
  function num(v) { return (v === null || v === undefined) ? '—' : v; }

  /**
   * 台风中心位置文案。优先用接口给的 `centerText`，没有时由经纬度拼。
   * @param {Object} t 台风对象
   * @returns {string} 形如 "22.1°N 128.5°E"；无坐标时返回 "—"
   */
  function coordinateText(t) {
    if (t.centerText) { return t.centerText; }
    if (t.lat === undefined || t.lon === undefined) { return '—'; }
    return Number(t.lat).toFixed(1) + '°N ' + Number(t.lon).toFixed(1) + '°E';
  }

  /**
   * 移动方向文案：方位中文 + 方位角。
   * @param {Object} t 台风对象
   * @returns {string} 形如 "西北 · 315°"；无方向数据时返回 "—"
   */
  function moveText(t) {
    if (!t.moveDir && t.moveDeg === undefined) { return '—'; }
    return (t.moveDir || '') + (t.moveDeg === undefined ? '' : ' · ' + Math.round(t.moveDeg) + '°');
  }

  /**
   * 渲染右列 · 城市风险预判。
   *
   * <p>标题从设计稿的「7 级风圈影响城市」改为「城市风险预判」：接口返回的是
   * 评估半径内所有城市的预计风力与风险等级，范围比"7 级风圈内"更大，
   * 沿用旧标题会把"评估半径内"误说成"风圈内"（这是准确性问题，不是措辞偏好）。
   *
   * @returns {string} 城市风险卡 HTML
   */
  function cityPanel() {
    var list = state.assessments || [];
    var inCircle = list.filter(function (c) { return c.inWindCircle === true; }).length;

    var rows = list.map(function (c) {
      return '<div class="row-item is-sm">' +
        '<span class="row-name">' + U.esc(c.cityName + ' · ' + c.province) + '</span>' +
        '<span class="row-value txt-muted">邻近 ' + Math.round(Number(c.distance) || 0) + ' km' +
          '<i style="font-style:normal;color:var(--ink-3)"> · </i>' +
          '<b style="color:' + U.riskColor(c.riskLevel) + ';font-weight:500">' +
            U.esc('预报 ' + (c.windLevel || '—')) + '</b>' +
        '</span></div>';
    }).join('');

    return U.panel({
      title: '城市风险预判',
      sub: '评估半径内 ' + list.length + ' 座 · 风圈内 ' + inCircle + ' 座',
      cls: 'is-tight',
      body: list.length
        ? '<div class="rows">' + rows + '</div>'
        : hint('台风中心距陆地较远，评估半径内暂无受影响城市')
    });
  }

  /**
   * 渲染右列 · AI 多模型路径测算卡。
   *
   * <p>量纲提醒：`models[].confidence` 是<b>单个模型</b>置信度（0—100，直接拼 %），
   * 与融合后的 `ai.confidence`（0—1）不同源也不同量纲，此处只取前者。
   * 详见 `docs/api.md` 1.4 量纲总表。
   *
   * @returns {string} AI 测算卡 HTML
   */
  function aiPanel() {
    var ai = state.ai;
    if (!ai) {
      return U.panel({
        title: 'AI 多模型路径测算', cls: 'is-tight',
        body: hint('该台风暂无 AI 测算结果')
      });
    }

    var models = ai.models || [];
    var tiles = models.map(function (m) {
      var off = m.confidence === null || m.confidence === undefined;
      return '<div class="ai-tile' + (off ? ' is-off' : '') + '">' +
        '<span>' + U.esc(m.name) + '</span><i class="ai-dot">·</i>' +
        '<b class="ai-conf">' + (off ? '待测算' : m.confidence + '%') + '</b>' +
        '</div>';
    }).join('');

    /* consistency 接口给的是 0—100 的百分数，直接作为进度条宽度 */
    var pct = Math.max(0, Math.min(100, Number(ai.consistency) || 0));

    var body =
      '<div class="ai-grid">' + (tiles || hint('暂无模型明细')) + '</div>' +
      '<div class="ai-note">' +
        '<span class="ai-note-title">综合测算</span>' +
        '<p class="ai-note-text">' + U.esc(ai.summary || '—') + '</p>' +
      '</div>' +
      '<div class="bar-line">' +
        '<span class="metric-label" style="font-size:var(--fs-mini);color:var(--ink-2);white-space:nowrap">模型一致性</span>' +
        '<span class="bar"><i style="width:' + pct + '%;background:var(--ok)"></i></span>' +
        '<span class="bar-value txt-ok">' + U.esc((ai.consistencyText || '—') + '（' + pct + '%）') + '</span>' +
      '</div>';

    return U.panel({
      title: 'AI 多模型路径测算',
      sub: (ai.version || '') + ' 提示词',
      cls: 'is-tight',
      body: body
    });
  }

  /**
   * 渲染「部分内容未能加载」清单卡，把「哪一块缺了、为什么缺」摆在最显眼处。
   *
   * <p>用 `.panel.is-warn`（组件库既有样式）而不是普通 note，让缺失一眼可见。
   * 全部加载成功时返回空串，不占位。
   *
   * @returns {string} 失败清单 HTML；无失败项时返回空串
   */
  function failureNote() {
    if (!state.failures.length) { return ''; }
    return U.panel({
      cls: 'is-warn',
      body: '<div class="panel-head"><span class="panel-title">部分内容未能从后端加载</span></div>' +
        '<div class="note-text">' +
        state.failures.map(function (f) { return '· ' + U.esc(f); }).join('<br>') +
        '</div>'
    });
  }

  /**
   * 渲染右列：失败清单 + 实况参数 + 城市风险 + AI 测算 + 免责说明。
   * @returns {string} 右列 HTML
   */
  function rightColumn() {
    return '<div class="colstack col-right">' +
      failureNote() +
      livePanel() +
      cityPanel() +
      aiPanel() +
      U.note('数据来源为中央气象台、JMA、JTWC 等公开信息；风圈与 AI 测算为平台推演结果，仅供参考，请以气象部门官方发布为准。') +
      '</div>';
  }

  /**
   * 整页渲染：把三列拼进 `#page-body`，并刷新标题栏与交互绑定。
   *
   * <p>无任何台风数据时渲染三列空态（骨架仍在），而不是留白或报错页。
   *
   * @returns {void}
   */
  function renderAll() {
    if (!state.live) {
      $('#page-body').html(
        '<div class="colstack col-left">' +
          U.panel({ title: '台风列表', cls: 'is-tight', body: hint('台风档案为空，请先在台风档案库导入数据') }) +
        '</div>' +
        '<div class="colstack col-map">' +
          '<div class="map-card">' + U.mapToolbar() + U.mapCanvas({}) + '</div>' +
        '</div>' +
        '<div class="colstack col-right">' +
          U.panel({ title: '实况参数', cls: 'is-tight', body: hint('暂无活跃台风') }) +
        '</div>'
      );
      return;
    }

    $('#page-body').html(leftColumn() + mapColumn() + rightColumn());
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '监测总览',
      desc: '实时台风地图 · 多源预报路径与风圈融合展示',
      meta: titleMeta()
    }));
    bind();
  }

  /**
   * 整页级失败（连台风清单都拿不到）：保留三列骨架但填错误说明，并弹 toast。
   *
   * <p>只展示可读错误，不回退演示数据——否则演示时看到的"正常数据"其实是编造的。
   *
   * @param {string} msg 失败原因
   * @returns {void}
   */
  function fatal(msg) {
    state.failures = [msg];
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '监测总览',
      desc: '实时台风地图 · 多源预报路径与风圈融合展示',
      meta: [{ dot: true, text: '数据加载失败' }]
    }));
    $('#page-body').html(
      '<div class="colstack col-left">' +
        U.panel({ title: '台风列表', cls: 'is-tight', body: hint(msg) }) +
      '</div>' +
      '<div class="colstack col-map">' +
        '<div class="map-card">' + U.mapToolbar() + U.mapCanvas({}) + '</div>' +
      '</div>' +
      '<div class="colstack col-right">' +
        U.panel({ title: '实况参数', cls: 'is-tight', body: hint('数据不可用') }) +
      '</div>'
    );
    ZFZ.layout.toast(msg);
  }

  /* ================= 交互 ================= */

  /**
   * 绑定整页交互：台风列表分组 / 搜索、底图切换、图层开关、时间轴回放。
   *
   * <p>每次 `renderAll()` 后调用一次；因三列是整块重绘的，
   * 事件用委托绑在容器上，避免重绘后失效。
   *
   * @returns {void}
   */
  function bind() {
    /* ---------- 左栏：活跃台风 / 历史档案 分组切换 + 组内搜索 ---------- */
    var group = 'active', kw = '';

    /**
     * 按当前分组与关键词重绘台风列表（含 Tab 态、底部档案库入口）。
     * @returns {void}
     */
    function renderTyList() {
      var pool = group === 'active' ? state.active : state.history;
      if (kw) {
        var k = kw.toUpperCase();
        pool = pool.filter(function (t) {
          return String(t.code || '').indexOf(k) >= 0 ||
                 String(t.nameCn || '').indexOf(kw) >= 0 ||
                 String(t.nameEn || '').toUpperCase().indexOf(k) >= 0 ||
                 String(t.year || '').indexOf(k) >= 0;
        });
      }

      if (!pool.length) {
        $('#ty-list-host').html(hint(kw
          ? '本分组内没有匹配「' + kw + '」的台风' +
            (group === 'active' ? '，可切换至「历史档案」继续查找' : '')
          : '当前无活跃台风在编'));
      } else {
        $('#ty-list-host').html(U.tyList(pool, group === 'active' ? liveId() : null));
      }

      /* 活跃组：说明另外还有多少历史；历史组：给出档案库入口 */
      $('#ty-hint').html(group === 'active'
        ? '<div class="ty-hint"><i class="ty-hint-dot"></i>' +
          '<span>数据每 10 分钟同步一次 · 地图正在回放「' + U.esc(state.live.nameCn) + '」</span></div>'
        : '');
      $('#ty-foot').html('<a class="link-inline" href="archive.html">' +
        (group === 'active'
          ? '另有 ' + state.history.length + ' 个历史档案 · 前往档案库'
          : '查看全部 ' + state.history.length + ' 个历史台风档案') +
        '</a>');

      $('#ty-tabs .tab').each(function () {
        var on = $(this).attr('data-group') === group;
        $(this).toggleClass('is-active', on).attr('aria-selected', on ? 'true' : 'false');
      });
    }

    $('#ty-tabs').on('click', '.tab', function () {
      var g = $(this).attr('data-group');
      if (g === group) { return; }
      group = g;                      /* 切换分组，搜索词在新分组内重新生效 */
      renderTyList();
    });

    $('#ty-search').on('input', function () {
      kw = $.trim(this.value);
      renderTyList();
    });

    renderTyList();

    /* ---------- 底图切换：普通地图 / 卫星云图 / 地形图 ---------- */
    /* 三张底图均为预加载的独立图层，切换只改显隐，不重新请求、不闪烁 */
    $('.map-basemaps').on('click', '.basemap', function () {
      var key = $(this).attr('data-basemap-btn');
      $(this).addClass('is-active').attr('aria-pressed', 'true')
        .siblings().removeClass('is-active').attr('aria-pressed', 'false');
      $('.map-canvas').attr('data-basemap', key);
      $('.map-canvas > img[data-basemap]').each(function () {
        $(this).toggleClass('is-hidden', $(this).attr('data-basemap') !== key);
      });
    });

    /* ---------- 图层开关 ---------- */
    var LAYER_TARGET = {
      '风圈':     '.ty-wind',
      '预报路径': '.map-layer[data-layer="forecast"], .map-layer[data-layer="ai"]',
      '城市点位': '.map-layer[data-layer="cities"]'
    };
    $('[data-layer-toggle]').on('click', function () {
      var sel = LAYER_TARGET[$(this).attr('data-layer-toggle')];
      if (!sel) { return; }
      var $t = $(sel);
      var on = !$t.first().hasClass('is-hidden');
      $t.toggleClass('is-hidden', on);
      $(this).toggleClass('is-active', !on).toggleClass('is-off', on);
    });

    /* ---------- 时间轴回放 ---------- */
    var rp = state.replay;
    if (!rp || !rp.frames || rp.frames.length < 2 || !state.t0) {
      /* 没有回放数据时不接管时间轴；把台风摆到最新观测位置即可 */
      placeAt(state.tracks.length ? state.tracks[state.tracks.length - 1] : state.live);
      return;
    }

    var $fill = $('.zbar-fill'),
        $cur  = $('.timeline-labels .is-current'),
        $play = $('#timeline-play');

    /**
     * 回放进度（小时数）→ 时间轴刻度文案。
     * @param {number} h 距回放起点的小时数
     * @returns {string} 形如 "2026-09-16 08:00"
     */
    function timeLabel(h) {
      return clock(new Date(state.t0.getTime() + h * 3600000));
    }

    /**
     * 在回放序列上线性插值，求任意进度下的台风状态。
     *
     * <p>进度落在两帧之间时按比例插值，因此拖动时间轴可以得到连续平滑的
     * 位置与强度，而不是只在整帧上跳变。
     *
     * @param {number} p 进度百分比（0—100）
     * @returns {Object} 插值后的状态 {lon, lat, speed, pressure, level, move, mph, hours}
     */
    function stateAt(p) {
      var frames = rp.frames, step = state.step;
      var h = p / 100 * state.totalH;
      var i = Math.min(frames.length - 2, Math.max(0, Math.floor(h / step)));
      var f = Math.min(1, Math.max(0, h / step - i));
      var a = frames[i], b = frames[i + 1];

      /**
       * 在相邻两帧 a、b 之间按 f 插值单个字段。
       * @param {string} k 字段名
       * @returns {number} 插值结果；两帧都非数值时返回 0
       */
      function mix(k) {
        var x = Number(a[k]), y = Number(b[k]);
        if (!isFinite(x)) { return isFinite(y) ? y : 0; }
        if (!isFinite(y)) { return x; }
        return x + (y - x) * f;
      }
      return {
        lon: mix('lon'), lat: mix('lat'), speed: mix('speed'),
        pressure: mix('pressure'), level: mix('level'),
        move: mix('move'), mph: mix('mph'), hours: h
      };
    }

    var DIRS = ['北', '东北', '东', '东南', '南', '西南', '西', '西北'];

    /**
     * 方位角（度）→ 八方位中文名。
     * @param {number} deg 方位角
     * @returns {string} 如 "西北"；非数值时返回 "—"
     */
    function dirName(deg) {
      if (!isFinite(deg)) { return '—'; }
      return DIRS[Math.round(((deg % 360) + 360) % 360 / 45) % 8];
    }

    /**
     * 按当前进度重绘时间轴、地图与右栏实况参数。
     *
     * <p>右栏指标按 `data-metric` 锚点就地改文本，不整块重绘，避免闪烁。
     *
     * @returns {void}
     */
    function render() {
      var s = stateAt(state.pct);
      var xy = U.proj(s.lon, s.lat);
      var lv = Math.round(s.level);

      /* 时间轴 */
      $fill.css('width', state.pct + '%');
      $cur.text('当前 ' + timeLabel(s.hours));

      /* 地图：台风本体沿路径平移 + 风圈随强度缩放 + 已回放轨迹逐段生长 */
      U.movePhoto(xy[0], xy[1]);
      U.scaleWind(s.speed);
      U.setTrackProgress(state.pct);

      /* 右栏实况参数随回放时刻刷新 */
      var tag = U.levelOf(lv);
      $('[data-metric="level"] .metric-value').text(lv + ' 级');
      $('[data-metric="speed"] .metric-value').text(Math.round(s.speed) + ' m/s');
      $('[data-metric="pressure"] .metric-value').text(Math.round(s.pressure) + ' hPa');
      $('[data-metric="move"] .metric-value').text(dirName(s.move) + ' · ' + Math.round(s.move) + '°');
      $('[data-metric="mph"] .metric-value').text(Math.round(s.mph) + ' km/h');
      $('[data-metric="pos"] .metric-value').text(s.lat.toFixed(1) + '°N ' + s.lon.toFixed(1) + '°E');
      $('[data-metric="levelTag"]').text(tag.name + ' · ' + lv + '级').attr('class', 'tag ' + tag.cls);
    }

    /**
     * 停止回放（清除定时器并把按钮还原为播放态）。
     * @returns {void}
     */
    function stop() {
      if (state.timer) { clearInterval(state.timer); state.timer = null; }
      $play.removeClass('is-playing').html(U.icons.play);
    }

    $play.on('click', function () {
      if (state.timer) { stop(); return; }              /* 播放中 → 暂停 */
      if (state.pct >= 100) { state.pct = 0; }          /* 已播完 → 从头重放 */
      $play.addClass('is-playing').html(U.icons.pause);
      state.timer = setInterval(function () {
        state.pct = Math.min(100, state.pct + 1.4);     /* 约 5 秒走完全程 */
        render();
        if (state.pct >= 100) { stop(); }
      }, 70);
    });

    /* 轨道点击 / 拖动跳转（拖动时自动暂停） */
    $('.timeline .zbar').on('mousedown', function (e) {
      var el = this;
      stop();
      /**
       * 按鼠标横坐标换算回放进度百分比并重绘（拖动过程中逐帧调用）。
       * @param {MouseEvent} ev 鼠标事件
       * @returns {void}
       */
      function seek(ev) {
        var rect = el.getBoundingClientRect();
        state.pct = Math.round(Math.min(1, Math.max(0, (ev.clientX - rect.left) / rect.width)) * 100);
        render();
      }
      seek(e);
      $(document).on('mousemove.zfzseek', seek)
                 .on('mouseup.zfzseek', function () { $(document).off('.zfzseek'); });
    });

    render();
  }

  /**
   * 无回放数据时，把台风本体摆到已知的最后一个位置上（取轨迹末点，退回首帧台风对象）。
   * @param {Object} point 含 lon / lat 的位置对象
   * @returns {void}
   */
  function placeAt(point) {
    if (!point || !isFinite(Number(point.lon)) || !isFinite(Number(point.lat))) { return; }
    var xy = U.proj(Number(point.lon), Number(point.lat));
    U.movePhoto(xy[0], xy[1]);
  }

  /* ================= 启动 ================= */

  /**
   * 生成标题栏右侧的元信息（机构预报数 + 最近更新时间）。
   * @returns {Array.<Object>} 元信息项数组，供 `ZFZ.layout.titleBar` 使用
   */
  function titleMeta() {
    var n = state.paths.length;
    var latest = '';
    state.paths.forEach(function (p) {
      if (p.publishTime && p.publishTime > latest) { latest = p.publishTime; }
    });
    if (!latest && state.live) { latest = state.live.generateTime || ''; }

    return [
      { dot: true, text: n ? n + ' 家机构预报已同步' : '暂无机构预报', cls: n ? 'is-ok' : '' },
      { text: latest ? '最近更新 ' + latest : '更新时间未知' }
    ];
  }

  $(function () {
    ZFZ.layout.mount('overview');

    $('#page-title').html(ZFZ.layout.titleBar({
      title: '监测总览',
      desc: '实时台风地图 · 多源预报路径与风圈融合展示',
      meta: [{ dot: true, text: '正在同步数据…' }]
    }));

    $('#page-body').html(
      '<div class="colstack col-left">' +
        U.panel({ title: '台风列表', cls: 'is-tight', body: hint('正在加载台风清单…') }) +
      '</div>' +
      '<div class="colstack col-map">' +
        '<div class="map-card">' + U.mapToolbar() + U.mapCanvas({}) + '</div>' +
      '</div>' +
      '<div class="colstack col-right">' +
        U.panel({ title: '实况参数', cls: 'is-tight', body: hint('正在加载…') }) +
      '</div>'
    );

    load();
  });
})(window.jQuery);
