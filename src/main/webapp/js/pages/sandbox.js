/* ==========================================================================
   P6 情景推演沙盘
   设计稿节点 3:809（1440×1000）
   左列 340：沙盘参数控制 / 情景库
   主列：沙盘推演路径对比 / 推演结果对比

   数据来源（全部走 ZFZ.api，失败即渲染错误态并弹 toast，不回退本地模拟数据）：
     GET  /api/sandbox/scenarios/params   四项参数的可调范围（用于设置滑杆 min/max）
     GET  /api/sandbox/scenarios          情景库：已登录返回「公开的 ∪ 自己的」，
                                          匿名只返回公开的；VO 带 mine / shared 标记
     GET  /api/typhoons?status=ACTIVE     基准台风
     GET  /api/typhoons/{id}/forecast-paths  基准路径（CMA 预报，与后端做偏移用的是同一份基底）
     POST /api/sandbox/scenarios          运行推演；save=1 时同时落库
     PUT  /api/sandbox/scenarios/{id}/share   公开 / 取消公开（仅自己创建的那几条）

   情景库为何要包含"自己的方案"：新存的情景 is_public 默认为 0，若默认只列公开情景，
   用户点完「另存为情景」会当场找不到自己刚存的东西，只能认定是保存失败了。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /** 无活跃台风在编时退回的默认台风 id（当前活跃台风「飞燕」） */
  var DEFAULT_ID = 13;

  /**
   * 页面运行时状态：接口返回后填充，交互只读写这里。
   * @type {{current: (Object|null), params: (Object|null), scenarios: Array<Object>,
   *         sliders: Array<Object>, lastRun: (Object|null),
   *         basePath: (Array<Array<number>>|null), simPath: (Array<Array<number>>|null),
   *         mapSeq: number}}
   */
  var state = {
    current: null, params: null, scenarios: [], sliders: [], lastRun: null,
    basePath: null,   /* 基准路径点 [[lon,lat],...]，来自 CMA 预报 */
    simPath: null,    /* 按当前参数扰动后的模拟路径点 [[lon,lat],...] */
    sceneIndex: null, /* 当前从情景库载入的那条（下标），仅用于高亮，null=未选 */
    cities: [],       /* /api/cities 城市点位，画在地图上做落区参照 */
    mapSeq: 0         /* 模拟路径请求的自增令牌，用来丢弃过期响应 */
  };

  /* ---------------- 数值工具 ---------------- */

  /**
   * 数值文案：整数原样，小数保留 1 位（2 → '2'，1.5 → '1.5'）。
   *
   * <p>空值必须是「—」而不是「0」：`Number(null)` 恰好是 0，
   * 早先的写法会把"取不到参数"渲染成"参数等于 0"，
   * 于是预置情景的登陆偏移显示成 0km —— 比显示破折号更误导。
   * @param {*} v 待格式化数值
   * @returns {string}
   */
  function num(v) {
    if (v === null || v === undefined || v === '') { return '—'; }
    var n = Number(v);
    if (!isFinite(n)) { return '—'; }
    return Number.isInteger(n) ? String(n) : n.toFixed(1);
  }

  /**
   * 带符号文案：0 → '±0'，正数 → '+1'，负数 → '-1'；空值 → '—'。
   * @param {*} v 待格式化数值
   * @returns {string}
   */
  function signed(v) {
    if (v === null || v === undefined || v === '') { return '—'; }
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
   * 情景参数的键名映射。
   *
   * <p>同一个「登陆偏移」在本项目里出现过三种写法：
   * <ul>
   *   <li>接口 DTO 字段：`landingOffset`（驼峰）</li>
   *   <li>前端滑杆 / 数据库列：`landing_offset`（下划线）</li>
   *   <li><b>预置情景的种子数据：`landing_offset_km`</b>（带单位后缀）</li>
   * </ul>
   * 只认前两种的话，情景库里的预置情景会显示「— km」，
   * 而且点开情景回填滑杆时会被静默跳过 —— 用户看不出参数到底有没有载入。
   * 后端 `SandboxRequest` 的 `@JsonAlias` 也补了这一个别名，两边必须同步。
   */
  var PARAM_KEYS = {
    landing_offset: ['landing_offset', 'landingOffset', 'landing_offset_km'],
    speed_factor: ['speed_factor', 'speedFactor'],
    intensity_offset: ['intensity_offset', 'intensityDelta', 'intensityOffset', 'intensity_offset_km'],
    circle_scale: ['circle_scale', 'windScale', 'circleScale']
  };

  /**
   * 按滑杆 key 读取情景参数（兼容多种写法）；取不到返回 null。
   * @param {Object} p 参数对象
   * @param {string} key 滑杆 key（landing_offset / speed_factor / ...）
   * @returns {number|null}
   */
  function paramOf(p, key) {
    if (!p) { return null; }
    var ks = PARAM_KEYS[key] || [key];
    for (var i = 0; i < ks.length; i++) {
      var v = p[ks[i]];
      if (v !== null && v !== undefined && v !== '') {
        var n = Number(v);
        return isFinite(n) ? n : null;
      }
    }
    return null;
  }

  /**
   * 参数组合文案，例：'-40 km / 0.8× / +2 级 / 1.3×'。
   *
   * <p>库里 `params_json` 与推演响应用的都是**驼峰**键
   * （`SandboxServiceImpl#simulate` 组装时就是驼峰，落库时原样存），
   * 但预置情景是课程种子数据，键名带 `_km` 后缀 —— 统一走 {@link paramOf}。
   * @param {Object} p 参数对象
   * @returns {string}
   */
  function paramsText(p) {
    var intens = paramOf(p, 'intensity_offset');
    return num(paramOf(p, 'landing_offset')) + ' km / ' +
      U.fixed1(paramOf(p, 'speed_factor')) + '× / ' +
      (intens == null ? '—' : signed(intens)) + ' 级 / ' +
      U.fixed1(paramOf(p, 'circle_scale')) + '×';
  }

  /**
   * 情景参数 → 推演接口请求体（四项，不含 typhoonId）。
   *
   * <p>接口接受下划线键，这里统一用下划线，避免又把 `landing_offset_km`
   * 这种库里的历史写法直接递给后端。
   * @param {Object} p 情景参数
   * @returns {Object} 请求体片段
   */
  function paramsPayload(p) {
    var out = {};
    state.sliders.forEach(function (s) {
      var v = paramOf(p, s.key);
      if (v != null) { out[s.key] = v; }
    });
    return out;
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
      /* 没有推演结果时写「未推演」而不是「—」：破折号读起来像"算出来是空"，
         而事实是这条情景压根没跑过（例如它的基准台风没有预报路径）。 */
      risk: r.maxRisk ? r.maxRisk + '风险' : '未推演',
      city: r.maxRiskCity || (list.length ? list[0].city : '—'),
      cities: names || '—',
      hasResult: Object.keys(r).length > 0
    };
  }

  /* ---------------- 路径工具 ---------------- */

  /**
   * 情景库 → 结果对比表的全部行。
   *
   * <p>抽成函数是因为结果表会被三处刷新（首屏、点击情景、补齐推演结果），
   * 每处都抄一遍 map 容易漏字段 —— 曾经就漏过 cities。
   * @returns {Array<Object>}
   */
  function resultRows() {
    return state.scenarios.map(function (s) {
      return toResultRow(s.name, s.params, s.result, s.cities);
    });
  }

  /**
   * 把后端下发的路径点序列归一化成 `[[lon,lat], ...]`。
   *
   * 为什么要宽容三种形态：后端 `SandboxServiceImpl#simulate` 把 `params.path`
   * 组装成 `List<double[]>`，序列化后是 `[[lon,lat],...]`；但同一套接口在别处
   * （如路径 VO）用的是 `{lon,lat}`，将来换字段名也并非不可能。与其赌一种形态、
   * 赌错就整张图空白，不如数组 / lon,lat / longitude,latitude 三种都吃下。
   * 非法点直接跳过（不塞 0，0 会画到几内亚湾去），一个有效点都没有才返回 []。
   * @param {Array} raw 原始点序列
   * @returns {Array<Array<number>>} 有效点数组；无有效点时返回 []
   */
  function normalizePath(raw) {
    if (!raw || !raw.length) { return []; }
    var out = [];
    for (var i = 0; i < raw.length; i++) {
      var p = raw[i];
      var lon, lat;
      if (Array.isArray(p)) {
        lon = p[0]; lat = p[1];
      } else if (p && typeof p === 'object') {
        lon = p.lon != null ? p.lon : p.longitude;
        lat = p.lat != null ? p.lat : p.latitude;
      } else {
        continue;
      }
      lon = lon == null ? NaN : Number(lon);
      lat = lat == null ? NaN : Number(lat);
      if (isFinite(lon) && isFinite(lat)) { out.push([lon, lat]); }
    }
    return out;
  }

  /**
   * 从推演响应里取出模拟路径点。
   *
   * 后端当前把参数扰动后的路径放在 `params.path`；`params.basePath` 是预留形态，
   * 有就顺带用上，两者都没有时返回 []，由调用方决定"没有路径"该怎么显示。
   * @param {Object} res runSandbox 的响应
   * @returns {Array<Array<number>>}
   */
  function extractSimPath(res) {
    var p = res && res.params;
    if (!p) { return []; }
    return normalizePath(p.path != null ? p.path : p.basePath);
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
   * 一条情景的归属 / 公开标签。
   *
   * 后端把 `is_public` 混进了"可见性"判断（情景库返回 公开的 ∪ 自己的），
   * 所以前端没法靠"看不看得见"反推状态，必须读 VO 里的 `mine` / `shared`：
   *   mine=true        → 这条是我存的，可以公开 / 取消公开
   *   shared=true      → 已在情景库里对所有人可见
   *   mine=false 且 shared=false → 不该出现（他人未公开的方案不会下发），
   *                                真出现了就如实显示"未公开"，不要假装一切正常
   * @param {Object} s 情景 VO
   * @returns {string} 标签 HTML（可能为空串）
   */
  function sceneTags(s) {
    var out = '';
    if (s.mine) { out += U.tag('我的', 'is-ok'); }
    out += U.tag(s.shared ? '已公开' : '未公开', s.shared ? 'is-neutral' : 'is-warn');
    return out;
  }

  /**
   * 「情景库」面板。
   *
   * 数据源是 `GET /api/sandbox/scenarios`：已登录时返回<b>公开的 ∪ 自己的</b>，
   * 因此用户刚点完「另存为情景」就能在这里看到它（is_public 默认为 0，
   * 只列公开情景的话，自己的方案会当场消失，看起来像保存失败）。
   * 自己创建的那几条额外给一个公开 / 取消公开的开关——这正是原先
   * `shareSandboxScenario` 接口"写了但没入口"的那一环。
   *
   * result 为空对象、cities 为空数组时给出明确的未推演文案。
   * @returns {string} 面板 HTML
   */
  function sceneCard() {
    var mineCount = 0;
    var items = state.scenarios.map(function (s, i) {
      var r = s.result || {};
      var risk = r.maxRisk ? r.maxRisk + '风险' : '未推演';
      var cities = (s.cities || []).map(function (c) { return c.city; }).join('、');
      var lines = [
        paramsText(s.params),
        cities ? '受影响城市：' + cities : '暂未生成受影响城市'
      ];
      if (s.mine) { mineCount++; }

      /* 公开开关只画在自己创建的情景上：对别人的方案点"取消公开"必然 403，
         与其事后报错，不如一开始就不给出这个按钮。 */
      var shareBtn = s.mine
        ? '<button class="zbtn is-outline is-sm" type="button" data-share="' + i +
            '" data-shared="' + (s.shared ? '1' : '0') + '">' +
            (s.shared ? '取消公开' : '公开') + '</button>'
        : '';

      /* 主行为是"把这条情景载入沙盘"——纯页面内状态切换，因此用
         div[role=button] 而不是 <a href>（地址栏不该跟着变），
         键盘 Enter / Space 由 bindActions 统一补上。 */
      var on = Number(state.sceneIndex) === i;
      return '<div class="scene-item' + (on ? ' is-active' : '') + '" data-scene="' + i + '"' +
        ' role="button" tabindex="0" aria-pressed="' + on + '"' +
        ' title="点击在沙盘上查看该情景的推演路径">' +
        '<div class="scene-head"><span class="scene-name">' + U.esc(s.name) + '</span>' +
        '<span class="scene-risk" style="color:' + U.riskColor(risk) + '">' + U.esc(risk) + '</span></div>' +
        '<div class="scene-lines">' + lines.map(function (l) {
          return '<span class="scene-desc">' + U.esc(l) + '</span>';
        }).join('') + '</div>' +
        '<div class="scene-foot">' +
          '<span class="scene-tags">' + sceneTags(s) + '</span>' + shareBtn +
        '</div></div>';
    }).join('');

    var sub = '共 ' + state.scenarios.length + ' 条' + (mineCount ? ' · 我的 ' + mineCount + ' 条' : '');

    return U.panel({
      title: '情景库',
      sub: sub,
      cls: 'is-tight',
      body: (items
          ? '<div class="sandbox-scenes">' + items + '</div>'
          : U.emptyRow('暂无已保存情景')) +
        U.note('沙盘结果仅反映参数扰动下的相对变化趋势，不构成正式预报。', 'is-plain')
    });
  }

  /* ---------------- 推演路径对比 ---------------- */

  /**
   * 「沙盘推演路径对比」面板。
   *
   * 地图已与「监测总览」统一为同一套引擎（真实海岸线 / 地形 / 云图 / 经纬网 /
   * 地名 / 缩放平移 / 比例尺），图层开关由 `U.mapToolbar()` 提供，
   * 因此两页看到的海岸线、地名、投影完全一致 —— 沙盘上的路径能直接与总览对照。
   *
   * 两条路径由 `U.sandboxMap()` 按经纬度投影：
   *   baseline = state.basePath（CMA 预报，与后端做偏移的同一份基底）
   *   simPath  = state.simPath（参数扰动后的模拟路径）
   * @returns {string} 面板 HTML
   */
  function mapCard() {
    var t = state.current;
    var name = t ? t.nameCn + ' ' + t.nameEn : '基准台风';
    var hasSim = !!(state.simPath && state.simPath.length);
    // 副标题如实反映状态：有路径就说清是"按当前参数生成的"，没有就引导去点「生成推演」
    var sub = hasSim
      ? name + ' · 已按当前参数生成模拟路径（' + paramsText(liveParams()) + '）'
      : name + ' · 尚未生成模拟路径，点击左侧「生成推演」';

    return U.panel({
      title: '沙盘推演路径对比',
      sub: sub,
      cls: 'is-tight',
      body: '<div class="map-card">' +
        U.mapToolbar({
          layers: [
            { name: '基准路径', target: '.map-layer[data-layer="sandbox-base"]' },
            { name: '模拟路径', target: '.map-layer[data-layer="sandbox-sim"]' },
            { name: '地名', target: '.map-layer[data-layer="labels"]' }
          ]
        }) +
        U.sandboxMap({ baseline: state.basePath, simPath: state.simPath, cities: state.cities }) +
        '</div>'
    });
  }

  /* ---------------- 推演结果对比 ---------------- */

  /**
   * 结果对比表的表头 + 行（不含外层 .tbl 容器，便于局部刷新）。
   * @param {Array<Object>} rows 结果行
   * @returns {string}
   */
  /**
   * 结果对比表的表头 + 行（不含外层 .tbl 容器，便于局部刷新）。
   * @param {Array<Object>} rows 结果行
   * @param {number} [active] 当前载入的那一行下标（从情景库点进来的那条）
   * @returns {string}
   */
  function resultTableInner(rows, active) {
    var head = ['推演情景', '参数组合', '风险等级', '最大风城市', '受影响城市'].map(function (h) {
      return '<span>' + U.esc(h) + '</span>';
    }).join('');

    var body = rows.map(function (row, i) {
      var on = active != null && Number(active) === i;
      return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + (on ? ' is-active' : '') + '">' +
        '<span class="cell-strong">' + U.esc(row.name) + '</span>' +
        '<span class="cell-num">' + U.esc(row.p) + '</span>' +
        '<span style="color:' + U.riskColor(row.risk) + ';font-weight:500">' + U.esc(row.risk) + '</span>' +
        '<span>' + U.esc(row.city) + '</span>' +
        '<span class="cell-num cell-truncate" title="' + U.esc(row.cities) + '">' + U.esc(row.cities) + '</span>' +
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
   * @param {number} [active] 当前载入的情景下标（高亮那一行）
   * @returns {string} 面板 HTML
   */
  function resultCard(rows, conclusionHtml, errMsg, active) {
    var body = rows
      ? '<div class="tbl" style="--cols:200px 200px 88px 100px minmax(0,1fr)">' +
          resultTableInner(rows, active) + '</div>' + (conclusionHtml || '')
      : U.emptyRow(errMsg || '');

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
   * @param {number} [active] 当前载入的情景下标
   */
  function updateResultPanel(rows, conclusionHtml, errMsg, active) {
    $('#page-body .col-main .panel').last().replaceWith(resultCard(rows, conclusionHtml, errMsg, active));
  }

  /** 只刷新左列最后一个面板（情景库），保留参数滑杆不动 */
  function refreshSceneList() {
    $('#page-body .col-side .panel').last().replaceWith(sceneCard());
  }

  /** 只刷新主列第一个面板（沙盘推演路径对比），保留滑杆与结果表不动 */
  function refreshMapPanel() {
    $('#page-body .col-main .panel').first().replaceWith(mapCard());
    /* 地图节点是全新的，缩放 / 拖拽必须重新接管：`bindMapView` 是直接
       addEventListener（不是事件委托），换节点就失效。
       底图切换与图层开关走 #page-body 上的委托，重复调用不会再叠监听器。 */
    bindMapControls();
  }

  /**
   * 把一次推演响应接到地图上。
   *
   * 自增 mapSeq 会作废此前还在路上的滑杆请求：点「生成推演」时若拖滑杆触发的
   * 请求尚未返回，它响应更晚落地就会把按钮的结果盖掉，图看起来就"跟参数对不上"。
   * @param {Object} res runSandbox 的响应
   */
  function applySimPath(res) {
    state.mapSeq += 1;
    state.simPath = extractSimPath(res);
    refreshMapPanel();
  }

  /**
   * 按当前滑杆参数重算模拟路径并刷新地图。
   *
   * 滑杆松手后可能连续触发，几个请求同时在飞是常态：用自增的 mapSeq 令牌 +
   * 闭包里的 seq 快照，只认最后一次请求的响应，过期响应直接丢弃。
   * 失败时只弹 toast 并保留上一次的图——把已画好的路径清空比留着旧图更让人困惑。
   * @returns {Promise} 供上层串行等待；内部已兜住异常，不会向外抛
   */
  function refreshMapPath() {
    state.mapSeq += 1;
    var seq = state.mapSeq;
    return ZFZ.api.runSandbox(currentPayload(), false).then(function (res) {
      if (seq !== state.mapSeq) { return; }   // 已有更新的请求发出，丢弃本次结果
      state.simPath = extractSimPath(res);
      refreshMapPanel();
    }).catch(function (err) {
      if (seq !== state.mapSeq) { return; }   // 过期请求的报错也不必再打扰用户
      ZFZ.layout.toast('模拟路径生成失败：' + err.message);
    });
  }

  /**
   * 取某只台风的基准路径（优先 CMA，没有则退回第一条）。
   *
   * <p>失败只让地图退化成"仅模拟路径"，`state.basePath = null`，
   * 页面其余面板照常 —— 绝不能因为一张底图路径拉不到就渲染整页错误态。
   * @param {number} tid 台风 id
   * @param {boolean} [quiet] 为真时不弹 toast（批量补算时用，避免一次弹一堆）
   * @returns {Promise}
   */
  function fetchBaseline(tid, quiet) {
    if (tid == null) { return Promise.resolve(); }
    return ZFZ.api.getForecastPaths(tid).then(function (list) {
      var arr = list || [];
      var picked = null;
      for (var i = 0; i < arr.length; i++) {
        if (arr[i] && arr[i].agency === 'CMA') { picked = arr[i]; break; }
      }
      // 没有 CMA 就退回第一条：能画出基准总比什么都不画强
      var item = picked || arr[0];
      state.basePath = item ? normalizePath(item.points) : null;
    }).catch(function (err) {
      state.basePath = null;
      if (!quiet) { ZFZ.layout.toast('基准路径加载失败：' + err.message); }
    });
  }

  /**
   * 重新取基准路径并重算模拟路径（首屏加载与「更换基准台风」共用同一段逻辑）。
   *
   * 两件事互相独立：基准路径接口失败只让地图退化成"仅模拟路径"，
   * state.basePath 保持 null，页面其余面板照常——绝不能因此渲染整页错误态。
   * @returns {Promise} 两者都结束后再补刷一次地图，让先后到达的基准/模拟一起落图
   */
  function reloadBaselineAndMap() {
    if (!state.current) { return Promise.resolve(); }

    var base = fetchBaseline(state.current.id);
    var sim = refreshMapPath();

    return Promise.all([base, sim]).then(function () {
      refreshMapPanel();
    }).catch(function () {
      /* base / sim 各自已兜底，这里只保证不把异常抛给 loadAll 拖垮整页 */
    });
  }

  /* ---------------- 情景载入（点击情景库） ---------------- */

  /**
   * 把一条情景的参数回填到滑杆上（只改 DOM 值，不改滑杆定义的默认值）。
   *
   * <p>不改 `s.value` 是有意的：「重置参数」应当回到中性（±0 / 1.0×），
   * 而不是回到"上一次点开的那条情景"—— 后者会让重置按钮的含义变得不可预测。
   * @param {Object} p 情景参数
   * @returns {void}
   */
  function applyParamsToSliders(p) {
    state.sliders.forEach(function (s, i) {
      var v = paramOf(p, s.key);
      if (v == null) { return; }
      if (v < s.min) { v = s.min; }
      if (v > s.max) { v = s.max; }
      var $inp = $('[data-slider="' + i + '"]');
      if (!$inp.length) { return; }
      $inp.val(v);
      $('[data-slider-value="' + i + '"]').text(sliderText(s, v));
    });
  }

  /**
   * 只刷新左列第一个面板（沙盘参数控制），并把滑杆当前位置写回去。
   *
   * <p>情景可能基于另一只台风（预置里有「梅花基准情景」）：切换基准台风后，
   * 左栏那张"基准台风"卡片必须跟着换，否则地图画着飞燕、左栏写着蔷琵。
   * <p>重建成默认值再写回当前值，而不是直接改 DOM —— 台风名是服务端数据。
   * @returns {void}
   */
  function refreshControlCard() {
    var live = liveParams();
    var $old = $('#page-body .col-side .panel').first();
    if (!$old.length) { return; }
    $old.replaceWith(controlCard());
    state.sliders.forEach(function (s, i) {
      var v = live[s.key];
      if (v == null) { return; }
      var $inp = $('[data-slider="' + i + '"]');
      if (!$inp.length) { return; }
      $inp.val(v);
      $('[data-slider-value="' + i + '"]').text(sliderText(s, v));
    });
  }

  /**
   * 只重画地图 + 结果表（不动左栏滑杆，避免输入焦点与滑杆位置丢失）。
   * @param {Object} s 情景
   * @returns {void}
   */
  function paintScene(s) {
    refreshMapPanel();
    var rows = resultRows();
    updateResultPanel(rows, conclusionNote(rows, null), null, state.sceneIndex);
  }

  /**
   * 把情景库里的一条情景载入沙盘：参数回填滑杆 + 地图改画该情景的路径 +
   * 结果表高亮该行。
   *
   * <p>为什么不能"只加个高亮"：情景库存在的意义就是把当时那套假设重新摆出来看。
   * 点了 A 却在看 B 的线，比点了没反应更糟。
   *
   * <p>路径来源两条，按优先级：
   * <ol>
   *   <li>情景自带的 `params.path`（保存时落库的扰动路径，最忠实于当时的结果）；</li>
   *   <li>库里没有路径（课程预置情景就是这样）→ 用该情景参数现场跑一次推演。</li>
   * </ol>
   * ②会顺带把 `result` / `cities` 补进这条情景，因此结果表那三列也随之有了内容。
   *
   * @param {number} index 情景在 state.scenarios 中的下标
   * @returns {Promise} 载入完成（单条失败已兜住，不会向外抛）
   */
  function applyScene(index) {
    var s = state.scenarios[index];
    if (!s) { return Promise.resolve(); }

    state.sceneIndex = index;
    state.mapSeq += 1;                 // 作废还在路上的滑杆推演，别让它盖掉情景路径

    var p = s.params || {};
    applyParamsToSliders(p);

    var tid = s.baseTyphoonId != null ? Number(s.baseTyphoonId)
      : (state.current ? Number(state.current.id) : null);

    /* 情景可能基于另一只台风（预置里有「梅花基准情景」）：需要把基准台风、
       基准路径一起换过去，否则图上是 A 的基准 + B 的推演，永远对不上。 */
    var switching = tid != null && (!state.current || Number(state.current.id) !== tid);
    var ready = switching
      ? ZFZ.api.getTyphoon(tid).then(function (t) {
          state.current = t;
          refreshControlCard();      // 左栏「基准台风」必须跟着换
          return fetchBaseline(tid);
        })
      : (state.basePath ? Promise.resolve() : fetchBaseline(tid));

    return ready.then(function () {
      var path = normalizePath(p.path);
      if (path.length >= 2) {
        state.simPath = path;
        refreshSceneList();
        paintScene(s);
        return null;
      }
      return ZFZ.api.runSandbox($.extend({ typhoonId: tid != null ? tid : DEFAULT_ID }, paramsPayload(p)), false)
        .then(function (res) {
          state.simPath = extractSimPath(res);
          if (res && res.result) { s.result = res.result; }
          if (res && res.cities) { s.cities = res.cities; }
          refreshSceneList();
          paintScene(s);
        })
        .catch(function (err) {
          /* 常见原因：该情景的基准台风没有预报路径（如历史台风），
             推演接口直接 400。此时必须把模拟路径清掉 ——
             留着上一条情景的线，用户会以为这就是这条情景的结果。 */
          state.simPath = null;
          paintScene(s);
          ZFZ.layout.toast('该情景推演失败：' + err.message);
        });
    }).catch(function (err) {
      ZFZ.layout.toast('情景载入失败：' + err.message);
    });
  }

  /**
   * 补齐情景库里没有推演结果的情景。
   *
   * <p>课程预置的 5 条情景只落了参数，`result_json` 是空的 —— 于是「风险等级 /
   * 最大风城市 / 受影响城市」三列全是「—」，看着像功能没做完。这些行又不能靠
   * 改库解决（重跑主脚本会 DROP TABLE），因此在页面侧按各自参数重算一次：
   * <b>只补空白的，不覆盖已有结论</b>——已保存的情景保留保存当时的判断。
   *
   * <p>并发跑而不是串行：每条只是内存里的一次规则计算，串行会让最后一条等很久。
   * 单条失败保留「—」且**不弹 toast**，避免一进页面就被一堆提示糊住。
   *
   * @returns {Promise} 全部结束（不会向外抛）
   */
  function fillMissingResults() {
    var jobs = [];
    state.scenarios.forEach(function (s) {
      if (s.result && Object.keys(s.result).length) { return; }
      var tid = s.baseTyphoonId != null ? Number(s.baseTyphoonId) : null;
      if (tid == null) { return; }
      var body = $.extend({ typhoonId: tid }, paramsPayload(s.params));
      jobs.push(ZFZ.api.runSandbox(body, false).then(function (res) {
        if (!res) { return; }
        if (res.result) { s.result = res.result; }
        if (res.cities) { s.cities = res.cities; }
        var path = extractSimPath(res);
        if (path.length) { s.params = $.extend({}, s.params, { path: path }); }
      }).catch(function () { /* 单条失败保留「—」 */ }));
    });

    if (!jobs.length) { return Promise.resolve(); }
    return Promise.all(jobs).then(function () {
      var rows = resultRows();
      updateResultPanel(rows, conclusionNote(rows, null), null, state.sceneIndex);
      /* 情景卡片上的「风险」角标也读 result.maxRisk，补算完要一起刷新，
         否则会出现"表里有风险等级、左边卡片还写着未推演"的自相矛盾。 */
      refreshSceneList();
    });
  }

  /* ---------------- 加载态 / 错误态 ---------------- */

  /**
   * 首屏占位：先渲染出左右两列布局，避免白屏等待接口。
   * @returns {string} 占位 HTML
   */
  function loadingState() {
    return '<div class="colstack col-side">' +
        U.panel({ title: '沙盘参数控制', cls: 'is-tight', body: U.loadingRow() }) +
        U.panel({ title: '情景库', cls: 'is-tight', body: U.loadingRow() }) +
      '</div>' +
      '<div class="colstack col-main">' +
        U.panel({ title: '沙盘推演路径对比', cls: 'is-tight', body: U.loadingRow() }) +
        U.panel({ title: '推演结果对比', cls: 'is-tight', body: U.loadingRow() }) +
      '</div>';
  }

  /**
   * 错误态的空行文案。
   * @param {string} message 可读的错误文案（已由 api.js 翻译）
   * @returns {string}
   */
  var errorBody = U.errorBody;

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
    var rows = resultRows();
    $('#page-body').html(
      '<div class="colstack col-side">' + controlCard() + sceneCard() + '</div>' +
      '<div class="colstack col-main">' + mapCard() +
        resultCard(rows, conclusionNote(rows, null)) + '</div>'
    );
    bindMapControls();
  }

  /**
   * 地图交互接管：缩放平移 + 图层开关 + 地形图层补齐。
   * 统一走 `U.bindMapControls`（幂等），图层事件挂在 #page-body 上，
   * 面板局部重建后不需要重绑。
   * @returns {void}
   */
  function bindMapControls() {
    var canvas = $('.map-canvas')[0];
    ZFZ.ui.bindMapControls(canvas, $('#page-body')[0]);
    /* 三合一后地形是常显图层：数据已就绪时立刻补上，未就绪则触发加载，
       到位后原地替换（见 U.ensureTerrain）。沙盘拖滑杆会反复重建地图面板，
       这一步每次都要走 —— 只补一次的话，第二次重建的地图就没有地形了。 */
    ZFZ.ui.ensureTerrain(canvas);
  }

  /**
   * 读取四根滑杆的当前取值。
   *
   * DOM 里就用 DOM 值（用户拖过的实时值）；DOM 还没有（首次 render、面板正在
   * 重建）时退回滑杆定义的默认值，免得把 undefined 读成 NaN 污染副标题文案。
   * @returns {Object} 形如 { landing_offset: 0, speed_factor: 1, ... }
   */
  function liveParams() {
    var p = {};
    state.sliders.forEach(function (s, i) {
      var v = $('[data-slider="' + i + '"]').val();
      p[s.key] = (v == null || v === '') ? s.value : Number(v);
    });
    return p;
  }

  /**
   * 读取滑杆当前值，组装推演请求体。
   * 后端推演接口接受下划线键：landing_offset / speed_factor / intensity_offset / circle_scale。
   * @returns {Object} 请求体
   */
  function currentPayload() {
    return $.extend({ typhoonId: state.current ? state.current.id : DEFAULT_ID }, liveParams());
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

    /* 滑杆松手（change）后按新参数重算模拟路径。
       只挂 change 不挂 input：input 在拖动过程中会连发，逐个请求既浪费又会让
       地图来回跳，等用户松手再算一次即可。 */
    $body.on('change', '[data-slider]', function () {
      refreshMapPath();
    });

    /* 重置参数（重置后参数变了，图也要跟着回到基准，故补一次重算） */
    $body.on('click', '#btn-reset', function () {
      $('[data-slider]').each(function () {
        var i = Number($(this).data('slider'));
        this.value = state.sliders[i].value;
        $(this).trigger('input');
      });
      refreshMapPath();
    });

    /* 生成推演（不落库） */
    $body.on('click', '#btn-run', function () {
      var $btn = $(this).prop('disabled', true);
      ZFZ.api.runSandbox(currentPayload(), false)
        .then(function (res) {
          state.lastRun = res;
          var rows = [toResultRow(res.name, res.params, res.result, res.cities)];
          updateResultPanel(rows, conclusionNote(rows, res));
          applySimPath(res);   // 地图同步到本次推演的模拟路径
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
          applySimPath(res);   // 保存的同时也把地图对齐到这次推演
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
          // 换台风后旧路径全部作废：先清空再重取，免得图上是上一只台风的线
          state.basePath = null;
          state.simPath = null;
          ZFZ.layout.toast('基准台风已切换为 ' + state.current.nameCn + ' ' + state.current.nameEn);
          render();
          return reloadBaselineAndMap();
        })
        .catch(function (err) { ZFZ.layout.toast('更换基准失败：' + err.message); });
    });

    /* 情景卡片：把该情景载入沙盘（参数回填 + 地图改画它的路径 + 结果表高亮）。
       原来这里只切了个 is-active 类，点完地图纹丝不动 —— 用户会以为"点坏了"。 */
    $body.on('click', '.scene-item', function () {
      applyScene(Number($(this).attr('data-scene')));
    });

    /* 键盘可达：div 不会自己把 Enter / Space 变成 click */
    $body.on('keydown', '.scene-item', function (e) {
      if (e.key !== 'Enter' && e.key !== ' ' && e.key !== 'Spacebar') { return; }
      e.preventDefault();
      applyScene(Number($(this).attr('data-scene')));
    });

    /* 公开 / 取消公开自己保存的情景。
       这是 N3 暴露出来的那半个缺口：接口 PUT /api/sandbox/scenarios/{id}/share
       早就写好了，但前端从来没有调用点，也没人能调（当时后端要求管理员）。 */
    $body.on('click', '[data-share]', function (e) {
      e.stopPropagation();          // 别顺手把外层 .scene-item 也选中了

      var i = Number($(this).attr('data-share'));
      var s = state.scenarios[i];
      if (!s || !s.id) { return; }

      var next = $(this).attr('data-shared') !== '1';
      var $btn = $(this).prop('disabled', true);

      ZFZ.api.shareSandboxScenario(s.id, next)
        .then(function () {
          ZFZ.layout.toast(next
            ? '情景已公开，其他人现在能在情景库里看到它'
            : '情景已取消公开，只有你自己还能看到');
          /* 重新拉库再渲染：公开状态会影响"可见性"，本地改一个布尔值
             无法反映"别人的方案会不会出现在我这儿"这类变化。 */
          return ZFZ.api.getSandboxScenarios().then(function (list) {
            state.scenarios = list || [];
            refreshSceneList();
          });
        })
        .catch(function (err) {
          /* 常见失败：未登录（code 401）、或该情景不属于当前用户（403） */
          ZFZ.layout.toast('设置失败：' + err.message);
          $btn.prop('disabled', false);
        });
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
      ZFZ.api.getTyphoons({ status: 'ACTIVE' }),
      /* 城市点位是底图参照，取不到就不画（不给它兜底数据，否则图上会出现
         与实际落区无关的假城市点）。 */
      ZFZ.api.getCities().catch(function () { return []; })
    ]).then(function (res) {
      state.params = res[0];
      state.scenarios = res[1] || [];
      state.cities = res[3] || [];
      state.sliders = buildSliders(state.params);
      var active = res[2] || [];
      return active.length ? active[0] : ZFZ.api.getTyphoon(DEFAULT_ID);
    }).then(function (current) {
      state.current = current;
      state.lastRun = null;
      state.basePath = null;
      state.simPath = null;
      state.sceneIndex = null;
      render();
      /* 结果对比表先有骨架，再把库里缺推演结果的情景补算出来（异步，不阻塞地图）。
         两件事互不依赖：地图只要基准路径 + 当前参数的模拟路径。 */
      fillMissingResults();
      // 基准路径 + 模拟路径异步补齐：只影响地图那一块，失败也不拖垮整页
      return reloadBaselineAndMap();
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
