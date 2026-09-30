/* ==========================================================================
   P6 情景推演沙盘
   设计稿节点 3:809（1440×1000）
   左列 340：沙盘参数控制 / 情景库
   主列：沙盘推演路径对比 / 推演结果对比

   数据来源（全部走 ZFZ.api，失败即渲染错误态并弹 toast，不回退本地模拟数据）：
     GET  /api/sandbox/scenarios/params   四项参数的可调范围（用于设置滑杆 min/max）
     GET  /api/sandbox/scenarios          已保存情景库（params 为下划线键，result / cities 可能为空）
     GET  /api/typhoons?status=ACTIVE     基准台风
     POST /api/sandbox/scenarios          运行推演；save=1 时同时落库
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /** 无活跃台风在编时退回的默认台风 id（当前活跃台风「飞燕」） */
  var DEFAULT_ID = 13;

  /**
   * 页面运行时状态：接口返回后填充，交互只读写这里。
   * @type {{current: (Object|null), params: (Object|null), scenarios: Array<Object>,
   *         sliders: Array<Object>, lastRun: (Object|null)}}
   */
  var state = { current: null, params: null, scenarios: [], sliders: [], lastRun: null };

  /* ---------------- 数值工具 ---------------- */

  /**
   * 数值文案：整数原样，小数保留 1 位（2 → '2'，1.5 → '1.5'）。
   * @param {*} v 待格式化数值
   * @returns {string}
   */
  function num(v) {
    var n = Number(v);
    if (!isFinite(n)) { return '—'; }
    return Number.isInteger(n) ? String(n) : n.toFixed(1);
  }

  /**
   * 带符号文案：0 → '±0'，正数 → '+1'，负数 → '-1'。
   * @param {*} v 待格式化数值
   * @returns {string}
   */
  function signed(v) {
    var n = Number(v) || 0;
    if (n === 0) { return '±0'; }
    return (n > 0 ? '+' : '') + num(n);
  }

  /**
   * 滑杆取值显示文案（单位由滑杆定义给出）。
   * @param {Object} s 滑杆定义
   * @param {number} v 当前值
   * @returns {string} 例：'0 km' / '+1 级' / '1.0×'
   */
  function sliderText(s, v) {
    var n = Number(v);
    if (!isFinite(n)) { return '—'; }
    return (s.sign ? signed(n) : (s.dec ? n.toFixed(s.dec) : num(n))) + s.unit;
  }

  /**
   * 依接口返回的可调范围构造四根滑杆，min/max 不再硬编码。
   * @param {{landing_offset_max_km: number, speed_factor: Array<number>,
   *          intensity_offset_max: number, circle_scale: Array<number>}} p 参数范围
   * @returns {Array<Object>} 滑杆定义数组
   */
  function buildSliders(p) {
    var landMax = p.landing_offset_max_km;
    var speed = p.speed_factor;
    var intensMax = p.intensity_offset_max;
    var circle = p.circle_scale;

    return [
      { key: 'landing_offset', name: '登陆点偏移（-' + num(landMax) + ' ~ +' + num(landMax) + ' km）',
        min: -landMax, max: landMax, step: 5, value: 0, unit: ' km' },
      { key: 'speed_factor', name: '移速倍数（' + Number(speed[0]).toFixed(1) + '× ~ ' + Number(speed[1]).toFixed(1) + '×）',
        min: speed[0], max: speed[1], step: 0.1, value: 1.0, unit: '×', dec: 1 },
      { key: 'intensity_offset', name: '强度偏移（-' + num(intensMax) + ' ~ +' + num(intensMax) + ' 级）',
        min: -intensMax, max: intensMax, step: 1, value: 0, unit: ' 级', sign: true },
      { key: 'circle_scale', name: '风圈缩放（' + Number(circle[0]).toFixed(1) + '× ~ ' + Number(circle[1]).toFixed(1) + '×）',
        min: circle[0], max: circle[1], step: 0.1, value: 1.0, unit: '×', dec: 1 }
    ];
  }

  /**
   * 参数组合文案，例：'-40 km / 0.8× / +2 级 / 1.3×'。
   * 情景列表接口用下划线键，推演响应接口用驼峰键，这里两种都兜住。
   * @param {Object} p 参数对象
   * @returns {string}
   */
  function paramsText(p) {
    p = p || {};
    var landing = p.landing_offset != null ? p.landing_offset : p.landingOffset;
    var speed = p.speed_factor != null ? p.speed_factor : p.speedFactor;
    var intens = p.intensity_offset != null ? p.intensity_offset : p.intensityDelta;
    var circle = p.circle_scale != null ? p.circle_scale : p.windScale;
    return num(landing) + ' km / ' + Number(speed).toFixed(1) + '× / ' +
      signed(intens) + ' 级 / ' + Number(circle).toFixed(1) + '×';
  }

  /**
   * 把一条情景（列表项或推演响应）归一化成结果对比表的一行。
   * @param {string} name 情景名
   * @param {Object} params 参数对象
   * @param {Object} result 推演结果，列表接口可能为空对象 {}
   * @param {Array<Object>} cities 受影响城市，列表接口可能为空数组 []
   * @returns {{name: string, p: string, risk: string, city: string, cities: string, hasResult: boolean}}
   */
  function toResultRow(name, params, result, cities) {
    var r = result || {};
    var list = cities || [];
    var names = list.map(function (c) { return c.city; }).join('、');
    return {
      name: name,
      p: paramsText(params),
      risk: r.maxRisk ? r.maxRisk + '风险' : '—',
      city: r.maxRiskCity || (list.length ? list[0].city : '—'),
      cities: names || '—',
      hasResult: Object.keys(r).length > 0
    };
  }

  /* ---------------- 参数控制 ---------------- */

  /**
   * 「沙盘参数控制」面板：基准台风 + 四根滑杆 + 操作按钮。
   * @returns {string} 面板 HTML
   */
  function controlCard() {
    var t = state.current;
    var baseline =
      '<div class="baseline-pick">' +
        '<span class="flex-col"><span class="bp-label">基准台风</span>' +
        '<span class="bp-name">' + U.esc(t
          ? t.nameCn + ' ' + t.nameEn + ' · ' + t.code + ' · ' + t.status
          : '暂无活跃台风') + '</span></span>' +
        '<button class="zbtn is-outline is-sm" type="button" id="btn-change-base">更换</button>' +
      '</div>';

    var sliders = state.sliders.map(function (s, i) {
      return '<div class="slider-row">' +
        '<div class="slider-head"><span class="slider-name">' + U.esc(s.name) + '</span>' +
        '<span class="slider-value" data-slider-value="' + i + '">' + U.esc(sliderText(s, s.value)) + '</span></div>' +
        '<input class="slider" type="range" min="' + s.min + '" max="' + s.max + '" step="' + s.step +
          '" value="' + s.value + '" data-slider="' + i + '">' +
        '</div>';
    }).join('');

    var actions =
      '<div class="sandbox-actions">' +
        '<button class="zbtn is-primary is-fill" type="button" id="btn-run">生成推演</button>' +
        '<button class="zbtn" type="button" id="btn-reset">重置参数</button>' +
        '<button class="zbtn" type="button" id="btn-save">另存为情景</button>' +
      '</div>';

    return U.panel({
      title: '沙盘参数控制',
      sub: '可调阈值见 system_settings',
      cls: 'is-tight',
      body: baseline + '<div class="slider-list">' + sliders + '</div>' + actions
    });
  }

  /* ---------------- 情景库 ---------------- */

  /**
   * 「情景库」面板。result 为空对象、cities 为空数组时给出明确的未推演文案。
   * @returns {string} 面板 HTML
   */
  function sceneCard() {
    var items = state.scenarios.map(function (s, i) {
      var r = s.result || {};
      var risk = r.maxRisk ? r.maxRisk + '风险' : '未推演';
      var cities = (s.cities || []).map(function (c) { return c.city; }).join('、');
      var lines = [
        paramsText(s.params),
        cities ? '受影响城市：' + cities : '暂未生成受影响城市'
      ];
      return '<div class="scene-item' + (i === 0 ? ' is-active' : '') + '" data-scene="' + i + '">' +
        '<div class="scene-head"><span class="scene-name">' + U.esc(s.name) + '</span>' +
        '<span class="scene-risk" style="color:' + U.riskColor(risk) + '">' + U.esc(risk) + '</span></div>' +
        '<div class="scene-lines">' + lines.map(function (l) {
          return '<span class="scene-desc">' + U.esc(l) + '</span>';
        }).join('') + '</div></div>';
    }).join('');

    return U.panel({
      title: '情景库',
      sub: '共 ' + state.scenarios.length + ' 条',
      cls: 'is-tight',
      body: (items
          ? '<div class="sandbox-scenes">' + items + '</div>'
          : '<div class="empty-row">暂无已保存情景</div>') +
        U.note('沙盘结果仅反映参数扰动下的相对变化趋势，不构成正式预报。', 'is-plain')
    });
  }

  /* ---------------- 推演路径对比 ---------------- */

  /**
   * 「沙盘推演路径对比」面板（底图与路径为设计稿矢量图层，与数据无关）。
   * @returns {string} 面板 HTML
   */
  function mapCard() {
    var t = state.current;
    return U.panel({
      title: '沙盘推演路径对比',
      sub: (t ? t.nameCn + ' ' + t.nameEn : '基准台风') + ' · 参数扰动下生成三套推演路径',
      cls: 'is-tight',
      body: U.sandboxMap()
    });
  }

  /* ---------------- 推演结果对比 ---------------- */

  /**
   * 结果对比表的表头 + 行（不含外层 .tbl 容器，便于局部刷新）。
   * @param {Array<Object>} rows 结果行
   * @returns {string}
   */
  function resultTableInner(rows) {
    var head = ['推演情景', '参数组合', '风险等级', '最大风城市', '受影响城市'].map(function (h) {
      return '<span>' + U.esc(h) + '</span>';
    }).join('');

    var body = rows.map(function (row, i) {
      return '<div class="trow tbl-6' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(row.name) + '</span>' +
        '<span class="cell-num">' + U.esc(row.p) + '</span>' +
        '<span style="color:' + U.riskColor(row.risk) + ';font-weight:500">' + U.esc(row.risk) + '</span>' +
        '<span>' + U.esc(row.city) + '</span>' +
        '<span class="cell-num">' + U.esc(row.cities) + '</span>' +
        '</div>';
    }).join('');

    return '<div class="tbl-head">' + head + '</div>' + body;
  }

  /**
   * 结果对比结论条。有本次推演响应时直接用后端给的 tip，否则按情景库的数据完整度归纳。
   * @param {Array<Object>} rows 结果行
   * @param {Object} [run] runSandbox 的响应
   * @returns {string} note HTML
   */
  function conclusionNote(rows, run) {
    var text;
    if (run && run.tip) {
      text = run.tip;
    } else {
      var withResult = rows.filter(function (r) { return r.hasResult; }).length;
      text = withResult
        ? '情景库共 ' + rows.length + ' 条，其中 ' + withResult +
          ' 条已含推演结果，下表对比各情景的参数组合与受影响城市。'
        : '情景库共 ' + rows.length + ' 条，列表接口暂未返回推演结果（result 为空）。' +
          '调整左侧参数后点击「生成推演」，即可查看参数扰动下的受影响城市与风险等级。';
    }
    return U.note('<b class="note-title">对比结论</b><span class="note-text">' + U.esc(text) + '</span>', 'is-plain');
  }

  /**
   * 「推演结果对比」面板。
   * @param {Array<Object>|null} rows 结果行；传 null 表示渲染错误态
   * @param {string} [conclusionHtml] 结论条 HTML
   * @param {string} [errMsg] 错误文案（rows 为 null 时使用）
   * @returns {string} 面板 HTML
   */
  function resultCard(rows, conclusionHtml, errMsg) {
    var body = rows
      ? '<div class="tbl" style="--cols:200px 200px 88px 100px minmax(0,1fr)">' +
          resultTableInner(rows) + '</div>' + (conclusionHtml || '')
      : '<div class="empty-row">' + U.esc(errMsg || '') + '</div>';

    return U.panel({
      title: '推演结果对比',
      sub: '参数顺序：登陆偏移 / 移速倍数 / 强度偏移 / 风圈缩放',
      cls: 'is-tight',
      body: body
    });
  }

  /* ---------------- 局部刷新（避免重置滑杆） ---------------- */

  /**
   * 只替换主列最后一个面板（推演结果对比），保留左侧滑杆的用户输入位置。
   * @param {Array<Object>|null} rows 结果行；null 表示错误态
   * @param {string} [conclusionHtml] 结论条 HTML
   * @param {string} [errMsg] 错误文案
   */
  function updateResultPanel(rows, conclusionHtml, errMsg) {
    $('#page-body .col-main .panel').last().replaceWith(resultCard(rows, conclusionHtml, errMsg));
  }

  /** 只刷新左列最后一个面板（情景库），保留参数滑杆不动 */
  function refreshSceneList() {
    $('#page-body .col-side .panel').last().replaceWith(sceneCard());
  }

  /* ---------------- 加载态 / 错误态 ---------------- */

  /**
   * 首屏占位：先渲染出左右两列布局，避免白屏等待接口。
   * @returns {string} 占位 HTML
   */
  function loadingState() {
    return '<div class="colstack col-side">' +
        U.panel({ title: '沙盘参数控制', cls: 'is-tight', body: '<div class="empty-row">加载中…</div>' }) +
        U.panel({ title: '情景库', cls: 'is-tight', body: '<div class="empty-row">加载中…</div>' }) +
      '</div>' +
      '<div class="colstack col-main">' +
        U.panel({ title: '沙盘推演路径对比', cls: 'is-tight', body: '<div class="empty-row">加载中…</div>' }) +
        U.panel({ title: '推演结果对比', cls: 'is-tight', body: '<div class="empty-row">加载中…</div>' }) +
      '</div>';
  }

  /**
   * 错误态的空行文案。
   * @param {string} message 可读的错误文案（已由 api.js 翻译）
   * @returns {string}
   */
  function errorBody(message) {
    return '<div class="empty-row">加载失败：' + U.esc(message) +
      '<br>请确认后端服务已启动后刷新页面</div>';
  }

  /**
   * 错误态：接口失败时如实展示，禁止退回本地模拟数据。
   * @param {string} message 可读的错误文案
   * @returns {string} 错误态 HTML
   */
  function errorState(message) {
    return '<div class="colstack col-side">' +
        U.panel({ title: '沙盘参数控制', cls: 'is-tight', body: errorBody(message) }) +
      '</div>' +
      '<div class="colstack col-main">' +
        U.panel({ title: '推演结果对比', cls: 'is-tight', body: errorBody(message) }) +
      '</div>';
  }

  /* ---------------- 渲染 ---------------- */

  /** 全量重渲染左右两列（首次加载 / 更换基准台风时调用） */
  function render() {
    var rows = state.scenarios.map(function (s) {
      return toResultRow(s.name, s.params, s.result, s.cities);
    });
    $('#page-body').html(
      '<div class="colstack col-side">' + controlCard() + sceneCard() + '</div>' +
      '<div class="colstack col-main">' + mapCard() +
        resultCard(rows, conclusionNote(rows, null)) + '</div>'
    );
  }

  /**
   * 读取滑杆当前值，组装推演请求体。
   * 后端推演接口接受下划线键：landing_offset / speed_factor / intensity_offset / circle_scale。
   * @returns {Object} 请求体
   */
  function currentPayload() {
    var payload = { typhoonId: state.current ? state.current.id : DEFAULT_ID };
    state.sliders.forEach(function (s, i) {
      payload[s.key] = Number($('[data-slider="' + i + '"]').val());
    });
    return payload;
  }

  /* ---------------- 交互 ---------------- */

  /** 事件统一委托到 #page-body，局部重渲染后无需重新绑定 */
  function bindActions() {
    var $body = $('#page-body');

    /* 滑杆联动取值显示 */
    $body.on('input change', '[data-slider]', function () {
      var i = Number($(this).data('slider'));
      var s = state.sliders[i];
      if (!s) { return; }
      $('[data-slider-value="' + i + '"]').text(sliderText(s, parseFloat(this.value)));
    });

    /* 重置参数 */
    $body.on('click', '#btn-reset', function () {
      $('[data-slider]').each(function () {
        var i = Number($(this).data('slider'));
        this.value = state.sliders[i].value;
        $(this).trigger('input');
      });
    });

    /* 生成推演（不落库） */
    $body.on('click', '#btn-run', function () {
      var $btn = $(this).prop('disabled', true);
      ZFZ.api.runSandbox(currentPayload(), false)
        .then(function (res) {
          state.lastRun = res;
          var rows = [toResultRow(res.name, res.params, res.result, res.cities)];
          updateResultPanel(rows, conclusionNote(rows, res));
          ZFZ.layout.toast('推演完成：命中 ' + ((res.result && res.result.cityCount) || 0) + ' 个受影响城市');
        })
        .catch(function (err) {
          updateResultPanel(null, null, '推演失败：' + err.message);
          ZFZ.layout.toast('推演失败：' + err.message);
        })
        .then(function () { $btn.prop('disabled', false); });
    });

    /* 另存为情景（落库，并刷新情景库） */
    $body.on('click', '#btn-save', function () {
      var $btn = $(this).prop('disabled', true);
      ZFZ.api.runSandbox(currentPayload(), true)
        .then(function (res) {
          state.lastRun = res;
          var rows = [toResultRow(res.name, res.params, res.result, res.cities)];
          updateResultPanel(rows, conclusionNote(rows, res));
          ZFZ.layout.toast('情景已保存');
          return ZFZ.api.getSandboxScenarios().then(function (list) {
            state.scenarios = list || [];
            refreshSceneList();
          }).catch(function (err) {
            /* 保存本身已成功，仅情景库刷新失败：如实提示，不覆盖已展示的推演结果 */
            ZFZ.layout.toast('情景库刷新失败：' + err.message);
          });
        })
        .catch(function (err) {
          ZFZ.layout.toast('保存失败：' + err.message);
        })
        .then(function () { $btn.prop('disabled', false); });
    });

    /* 更换基准台风：在活跃台风之间轮换 */
    $body.on('click', '#btn-change-base', function () {
      ZFZ.api.getTyphoons({ status: 'ACTIVE' })
        .then(function (list) {
          if (!list || !list.length) {
            ZFZ.layout.toast('当前无活跃台风在编，无法更换基准');
            return;
          }
          var idx = 0;
          list.forEach(function (t, i) { if (state.current && t.id === state.current.id) { idx = i; } });
          state.current = list[(idx + 1) % list.length];
          ZFZ.layout.toast('基准台风已切换为 ' + state.current.nameCn + ' ' + state.current.nameEn);
          render();
        })
        .catch(function (err) { ZFZ.layout.toast('更换基准失败：' + err.message); });
    });

    /* 情景卡片选中 */
    $body.on('click', '.scene-item', function () {
      $('.scene-item').removeClass('is-active');
      $(this).addClass('is-active');
    });
  }

  /* ---------------- 入口 ---------------- */

  /**
   * 拉取参数范围 / 情景库 / 基准台风，成功后填充滑杆、情景库与结果对比表。
   * 任一接口失败都渲染错误态并弹 toast。
   */
  function loadAll() {
    $('#page-body').html(loadingState());

    Promise.all([
      ZFZ.api.getSandboxParams(),
      ZFZ.api.getSandboxScenarios(),
      ZFZ.api.getTyphoons({ status: 'ACTIVE' })
    ]).then(function (res) {
      state.params = res[0];
      state.scenarios = res[1] || [];
      state.sliders = buildSliders(state.params);
      var active = res[2] || [];
      return active.length ? active[0] : ZFZ.api.getTyphoon(DEFAULT_ID);
    }).then(function (current) {
      state.current = current;
      state.lastRun = null;
      render();
    }).catch(function (err) {
      $('#page-body').html(errorState(err.message));
      ZFZ.layout.toast('加载失败：' + err.message);
    });
  }

  $(function () {
    ZFZ.layout.mount('sandbox');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '情景推演沙盘',
      desc: '调整参数 → 生成推演路径 → 对比受影响城市与风险等级'
    }));

    bindActions();
    loadAll();
  });
})(window.jQuery);
