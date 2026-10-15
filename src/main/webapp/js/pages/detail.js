/* ==========================================================================
   P2 台风详情
   设计稿节点 3:322（1440×1120）
   主列：实况概览 / 多机构预报对照 / AI 多模型测算 / 实况轨迹数据
   侧列 340：风圈半径四象限 / 卫星云图 / 历史相似案例 Top3 / 免责说明
   --------------------------------------------------------------------------
   本页数据全部来自 ZFZ.api（改造规范见 docs/frontend-integration-guide.md）：
     实测概览       ZFZ.api.getTyphoon(id)        GET /api/typhoons/{id}
     实况轨迹       ZFZ.api.getTracks(id)         GET /api/typhoons/{id}/tracks
     风圈四象限     ZFZ.api.getWindCircles(id)    GET /api/typhoons/{id}/wind-circles
     多机构预报     ZFZ.api.getForecastPaths(id)  GET /api/typhoons/{id}/forecast-paths
     AI 多模型测算  ZFZ.api.getAiPredictions(id)  GET /api/ai/predictions?typhoonId=
     历史相似案例   ZFZ.api.getSimilarCases(id,3) GET /api/similar-cases?typhoonId=
     轨迹回放帧     ZFZ.api.getReplay(id)         GET /api/typhoons/{id}/replay
     分歧解析       ZFZ.api.getDisagreement(id)   GET /api/typhoons/{id}/disagreement（可能为 null）
     灾情报告       ZFZ.api.getDisaster(id)       GET /api/typhoons/{id}/disaster（可能为 null）
     导出 CSV/Excel ZFZ.api.exportTracks({format}) + downloadTracks()
                   format 取值 csv | xlsx，对应「导出 CSV」与「导出 Excel」两个按钮
   接口失败一律渲染错误态并弹 toast，不回退到 js/data.js 的演示数据。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /**
   * 页面主对象 id：优先取地址栏 ?id=，缺省为当前活跃台风「飞燕」。
   * @returns {string} 台风 id
   */
  function currentId() {
    var q = null;
    try { q = new URLSearchParams(location.search).get('id'); } catch (e) { q = null; }
    return q || 13;
  }

  /* ======================================================================== */
  /*  一、工具                                                                */
  /* ======================================================================== */

  /**
   * 风圈配色（取自设计稿，用于浅色卡片上的圆点与名称）。
   * 后端 wind-circles 返回的 color 是深色地图上的风圈色，浅色卡片上对比度不足。
   * @type {Object}
   */
  var WIND_COLOR = { 7: '#C77400', 10: '#E8833A', 12: '#D8322B' };

  /**
   * 按风力等级取强度标签类名。
   * 分级规则统一由组件库 {@link ZFZ.ui.levelOf} 提供（自带静态分级表，
   * 含「热带低压」档），本页不再自带一份实现，避免两处分级不一致。
   * @param {number|string} windLevel 风力等级（级）
   * @returns {string} 类名，例：'is-ty'
   */
  function levelTagCls(windLevel) {
    return U.levelOf(windLevel).cls;
  }

  /**
   * 安全取数：把后端字段转成数字，非法值回退。
   * @param {*} v 原始值
   * @param {*} [fallback] 非法时的回退值，默认 null
   * @returns {number|null|*} 数字或回退值
   */
  var num = U.num;

  /**
   * 取时间串的展示形式。
   * @param {string} s 后端时间串，例：'2026-09-15 08:00:00'
   * @param {boolean} [withTime] true 保留到分钟，false 只保留日期
   * @returns {string} 例：'2026-09-15 08:00'；空值返回 '—'
   */
  var fmtTime = U.fmtTime;

  /**
   * 经纬度文案。
   * @param {number|string} lat 纬度
   * @param {number|string} lon 经度
   * @returns {string} 例：'19.5°N 123.8°E'；缺值时返回 '—'
   */
  function fmtLatLon(lat, lon) {
    var a = num(lat), b = num(lon);
    if (a === null || b === null) { return '—'; }
    return a.toFixed(1) + '°N ' + b.toFixed(1) + '°E';
  }

  /**
   * 展示用的数字，缺值统一为 '—'。
   * @param {*} v 原始值
   * @param {string} unit 单位后缀
   * @param {number} [digits] 小数位，缺省不保留小数
   * @returns {string} 例：'965 hPa'
   */
  function fmtNum(v, unit, digits) {
    var n = num(v);
    if (n === null) { return '—'; }
    return (digits === undefined ? Math.round(n) : n.toFixed(digits)) + (unit ? ' ' + unit : '');
  }

  /* ======================================================================== */
  /*  二、主列卡片                                                            */
  /* ======================================================================== */

  /**
   * 顶部实况概览卡片。
   * @param {Object} t 台风详情（GET /api/typhoons/{id}）
   * @returns {string} 卡片 HTML
   */
  function heroCard(t) {
    var meta = [t.code, t.basin];
    if (t.moveDir && num(t.moveDeg) !== null) {
      meta.push('向' + t.moveDir + '移动 ' + Math.round(num(t.moveDeg)) + '°');
    }
    if (t.generateTime) { meta.push('生成于 ' + fmtTime(t.generateTime, true)); }

    var active = t.status === 'ACTIVE';
    var head =
      '<div class="detail-hero-top">' +
        '<div class="flex-col gap-8">' +
          '<div class="hero-name"><span class="hero-cn">' + U.esc(t.nameCn) + '</span>' +
            '<span class="hero-en">' + U.esc(t.nameEn) + '</span></div>' +
          '<div class="hero-meta">' + U.esc(meta.join(' · ')) + '</div>' +
        '</div>' +
        '<div class="hero-tags">' +
          U.tag(t.level + '级 · ' + t.windLevel + '级', levelTagCls(t.windLevel)) +
          U.tag(t.status + ' ' + (t.statusText || (active ? '活跃中' : '已结束')),
                active ? 'is-active' : 'is-neutral') +
        '</div>' +
      '</div>';

    var metrics = [
      { label: '中心最大风力', value: fmtNum(t.windLevel, '级') },
      { label: '最大风速',     value: fmtNum(t.maxWindSpeed === undefined ? t.windSpeed : t.maxWindSpeed, 'm/s') },
      { label: '中心最低气压', value: fmtNum(t.pressure, 'hPa') },
      { label: '移动速度',     value: fmtNum(t.moveSpeed, 'km/h') }
    ].map(function (m) {
      return '<div class="hero-metric"><span class="hm-label">' + U.esc(m.label) + '</span>' +
        '<span class="hm-value">' + U.esc(m.value) + '</span></div>';
    }).join('');

    return U.panel({
      cls: 'is-tight',
      body: head + '<div class="divider-h"></div>' + '<div class="hero-metrics">' + metrics + '</div>'
    });
  }

  /**
   * 多机构预报路径对照卡片。
   * 后端每条预报路径给的是点序列（首点为当前实况位置、末点为预报登陆位置），
   * 没有逐点时效标签，因此不套用设计稿的「24h / 48h」字样，改为展示起讫点。
   * @param {Array<Object>} paths 预报路径数组（GET /api/typhoons/{id}/forecast-paths）
   * @param {Object|null} dis 分歧解析（GET .../disagreement，可能为 null）
   * @returns {string} 卡片 HTML
   */
  function forecastCard(paths, dis) {
    if (!paths || !paths.length) {
      return U.panel({
        title: '多机构预报路径对照', cls: 'is-tight',
        body: U.emptyRow('当前没有机构发布预报路径')
      });
    }

    var rows = paths.map(function (p) {
      var pts = p.points || [];
      var first = pts.length ? pts[0] : null;
      var last = pts.length ? pts[pts.length - 1] : null;
      var tail = [];
      if (p.landing) { tail.push('登陆 ' + p.landing); }
      if (last) { tail.push('末端 ' + fmtLatLon(last[1], last[0])); }
      if (num(p.endWindForce) !== null) { tail.push('峰值 ' + Math.round(num(p.endWindForce)) + ' 级'); }

      return [
        { html: '<span class="cell-agency" style="color:' + U.esc(p.color || '#5B6B7B') + '">' +
                U.esc(p.agency + ' ' + p.name) + '</span>' },
        { html: '<span class="cell-num">起点 ' + U.esc(first ? fmtLatLon(first[1], first[0]) : '—') + '</span>' +
                '<i class="cell-sep"></i><span class="cell-num">' + U.esc(tail.join(' · ')) + '</span>' }
      ];
    });

    var sub = '发布于 ' + fmtTime(paths[0].publishTime, true) + ' · 对比基准 ' + paths[0].agency;
    if (dis && num(dis.index) !== null) {
      sub += ' · 分歧指数 ' + num(dis.index).toFixed(2) + '（' + (dis.riskLevel || '—') + '）';
    }

    return U.panel({
      title: '多机构预报路径对照',
      sub: sub,
      cls: 'is-tight',
      body: '<div class="tbl" style="--cols:170px minmax(0,1fr);gap:0">' +
        '<div class="tbl-head"><span>预报机构</span>' +
        '<span>路径起点<i class="cell-sep"></i>预报登陆点 · 末端位置 · 峰值强度</span></div>' +
        rows.map(function (r) {
          return '<div class="trow">' +
            r.map(function (c) {
              return '<span class="' + (c.cls || '') + '">' + (c.html || U.esc(c.t)) + '</span>';
            }).join('') + '</div>';
        }).join('') +
        '</div>'
    });
  }

  /**
   * AI 多模型路径智能测算卡片。
   * 模型置信度为 0—100，直接加 % 展示。
   * @param {Object} ai AI 测算结果（GET /api/ai/predictions?typhoonId=）
   * @returns {string} 卡片 HTML
   */
  function aiCard(ai) {
    if (!ai) {
      return U.panel({
        title: 'AI 多模型路径智能测算', cls: 'is-tight',
        body: U.emptyRow('暂无 AI 测算结果')
      });
    }

    var models = ai.models || [];
    var sub = 'prompt ' + (ai.version || '—') + ' · 测算时间 ' + fmtTime(ai.predictionTime, true) +
      ' · 与机构预报并排对照';

    if (!models.length) {
      return U.panel({
        title: 'AI 多模型路径智能测算', sub: sub, cls: 'is-tight',
        body: U.emptyRow(ai.summary || '当前无模型对本次台风发起测算')
      });
    }

    var rows = models.map(function (m) {
      var conf = num(m.confidence);
      var lat = num(m.landfallLat), lon = num(m.landfallLon);
      var right = (conf === null || lat === null || lon === null)
        ? '<span class="cell-truncate">尚未对本次台风发起测算，可在管理后台「大模型接入」中触发</span>'
        : '<span class="cell-num">登陆点 ' + U.esc(fmtLatLon(lat, lon)) + '</span>' +
          '<span class="ai-sep">·</span><span class="cell-num">置信度 <b class="txt-brand">' +
            U.esc(Math.round(conf) + '%') + '</b></span>' +
          '<span class="ai-sep">·</span><span class="cell-num">偏差 ' +
            U.esc(fmtNum(m.deviation, 'km')) + '</span>' +
          '<span class="ai-sep">·</span><span class="ai-state">' + U.esc(m.status || '—') + '</span>';

      return '<div class="trow ai-trow">' +
        '<span class="ai-name">' + U.esc(m.name) + ' <i>· ' + U.esc(ai.version || '—') + '</i></span>' +
        '<span class="ai-detail">' + right + '</span></div>';
    }).join('');

    return U.panel({
      title: 'AI 多模型路径智能测算',
      sub: sub,
      cls: 'is-tight',
      body: '<div class="tbl is-plain" style="--cols:190px minmax(0,1fr);gap:0">' + rows + '</div>'
    });
  }

  /**
   * 实况轨迹数据卡片（含导出 / 截图按钮）。
   * 设计稿为新时次在上，故把后端返回的升序序列倒序展示；
   * 移向 / 移速只有最新时次有（取自台风实体的 moveDeg / moveSpeed），历史时次不臆造。
   * @param {Array<Object>} tracks 实况轨迹（GET /api/typhoons/{id}/tracks）
   * @param {Object} t 台风详情（用于最新时次的移向 / 移速）
   * @param {Object} replay 回放帧（GET /api/typhoons/{id}/replay）
   * @returns {string} 卡片 HTML
   */
  function trackCard(tracks, t, replay) {
    var list = (tracks || []).slice().reverse();

    var body = list.length
      ? '<div class="tbl" style="--cols:150px minmax(0,1fr);gap:0">' +
          list.map(function (p, i) {
            var parts = [fmtLatLon(p.lat, p.lon)];
            if (num(p.windLevel) !== null) { parts.push(Math.round(num(p.windLevel)) + ' 级'); }
            if (num(p.windSpeed) !== null) { parts.push(Math.round(num(p.windSpeed)) + ' m/s'); }
            if (num(p.pressure) !== null) { parts.push(Math.round(num(p.pressure)) + ' hPa'); }
            if (i === 0) {
              if (num(t.moveDeg) !== null) { parts.push('移向 ' + Math.round(num(t.moveDeg)) + '°'); }
              if (num(t.moveSpeed) !== null) { parts.push('移速 ' + Math.round(num(t.moveSpeed)) + ' km/h'); }
            }
            return '<div class="trow' + (i === 0 ? ' is-alt' : '') + '">' +
              '<span class="cell-num cell-strong">' + U.esc(fmtTime(p.time, true)) + '</span>' +
              '<span class="cell-num">' + U.esc(parts.join(' · ')) + '</span>' +
              '</div>';
          }).join('') +
        '</div>'
      : U.emptyRow('该台风暂无实况轨迹数据');

    var frames = (replay && replay.frames) || [];
    var note = '当前显示 ' + list.length + ' 个关键时次；' +
      (frames.length
        ? '完整逐时轨迹（共 ' + frames.length + ' 帧 · ' +
          fmtTime(replay.startTime, true) + ' → ' + fmtTime(replay.endTime, true) + '）'
        : '完整逐时轨迹') +
      '可在首页时间轴回放中查看并导出为 CSV。';

    return '<section class="panel is-tight">' +
      '<div class="panel-head">' +
        '<span class="panel-title-row"><span class="panel-title">实况轨迹数据</span>' +
        '<span class="panel-sub">时间 / 位置 / 风力 / 风速 / 气压 / 移向 · 移速</span></span>' +
        '<span class="zbtn-row" style="flex:none">' +
          '<button class="zbtn is-primary is-sm" type="button" id="btn-export">' + U.icons.download + '导出 CSV</button>' +
          '<button class="zbtn is-outline is-sm" type="button" id="btn-export-xlsx">导出 Excel</button>' +
          '<button class="zbtn is-outline is-sm" type="button" id="btn-snapshot">保存截图</button>' +
        '</span>' +
      '</div>' + body + U.note(U.esc(note), 'is-plain') + '</section>';
  }

  /* ======================================================================== */
  /*  三、侧列卡片                                                            */
  /* ======================================================================== */

  /**
   * 风圈半径（四象限）卡片。
   * 后端风圈是嵌套结构 { level, color, r, quad: { ne, se, sw, nw } }，
   * 取值时必须走 quad.*。
   * @param {Array<Object>} circles 风圈数组（GET /api/typhoons/{id}/wind-circles）
   * @param {string} latestTime 最新实况时次（后端风圈未带观测时间）
   * @returns {string} 卡片 HTML
   */
  function windCircleCard(circles, latestTime) {
    var list = circles || [];
    if (!list.length) {
      return U.panel({
        title: '风圈半径（四象限）', sub: '单位 km', cls: 'is-tight',
        body: U.emptyRow('该台风暂无风圈数据')
      });
    }

    /* 最大风圈轮廓的象限极值，用于生成由数据得出的结论句 */
    var widest = { k: 'ne', v: -1, level: null };

    var items = list.map(function (c) {
      var q = c.quad || {};
      var r = num(c.r);
      var val = function (k) {
        var v = num(q[k]);
        return v === null ? (r === null ? '—' : r) : v;
      };
      ['ne', 'se', 'sw', 'nw'].forEach(function (k) {
        var v = num(q[k]);
        if (v !== null && v > widest.v) { widest = { k: k, v: v, level: c.level }; }
      });
      return {
        level: c.level,
        color: WIND_COLOR[c.level] || c.color || '#5B6B7B',
        values: 'NE ' + U.esc(val('ne')) + ' · NW ' + U.esc(val('nw')) +
                ' · SE ' + U.esc(val('se')) + ' · SW ' + U.esc(val('sw'))
      };
    });

    var QUAD_CN = { ne: '东北', se: '东南', sw: '西南', nw: '西北' };
    var note = widest.level === null
      ? '四象限半径反映台风结构的不对称性。'
      : '最大半径位于' + QUAD_CN[widest.k] + '象限（' + widest.level + ' 级风圈 ' + widest.v +
        ' km），四象限半径反映台风结构的不对称性。';

    return U.panel({
      title: '风圈半径（四象限）',
      sub: (latestTime ? '观测时次 ' + latestTime + ' · ' : '') + '单位 km',
      cls: 'is-tight',
      body: U.wcRows(items) + U.note(note, 'is-plain')
    });
  }

  /**
   * 卫星云图卡片。
   * 云图本身是组件库按最新实况时次合成的示意图（后端未提供影像接口），
   * 因此文案如实标注为「云图示意」。
   * @param {string} latestTime 最新实况时次
   * @returns {string} 卡片 HTML
   */
  function satelliteCard(latestTime) {
    return U.panel({
      title: '卫星云图影像',
      cls: 'is-tight',
      body: U.satellite(latestTime) +
        '<div class="panel-sub">' + U.esc(latestTime + ' 云图示意 · 依据最新实况时次绘制') + '</div>'
    });
  }

  /**
   * 历史相似案例 Top 3 卡片。
   * score 为 0—1，展示时换算成百分比。
   * @param {Array<Object>} cases 相似案例（GET /api/similar-cases?typhoonId=&topN=3）
   * @returns {string} 卡片 HTML
   */
  function similarCard(cases) {
    var list = cases || [];
    var items = list.map(function (s) {
      var rate = Math.round(num(s.score, 0) * 100);
      var cls = rate >= 85 ? 'is-good' : (rate >= 75 ? 'is-mid' : 'is-low');
      return '<div class="row-item"><span class="row-name">' +
        U.esc(s.nameCn + ' ' + s.nameEn + ' · ' + s.year) + '</span>' +
        '<span class="row-value rate ' + cls + '">' + rate + '%</span></div>';
    }).join('');

    return U.panel({
      title: '历史相似案例 Top 3',
      sub: '路径形态 / 峰值强度 / 登陆季节 / 移速 / 风圈 五维综合',
      cls: 'is-tight',
      body: (items ? '<div class="rows">' + items + '</div>'
                   : U.emptyRow('暂无相似案例')) +
        '<div class="panel-foot"><a class="link-inline" href="similar-cases.html">查看完整归因分析与当年城市灾情档案</a></div>'
    });
  }

  /**
   * 侧列免责说明：灾情档案（若有）+ AI 测算免责声明。
   * @param {Object|null} disaster 灾情报告（GET /api/typhoons/{id}/disaster，可能为 null）
   * @returns {string} 提示条 HTML
   */
  function disclaimerNote(disaster) {
    var text = '';
    if (disaster) {
      var parts = [];
      if (disaster.affectedProvinces) { parts.push('影响省份 ' + disaster.affectedProvinces); }
      if (disaster.mainCityName) { parts.push('主要城市 ' + disaster.mainCityName); }
      if (disaster.reportTime) { parts.push('报告时间 ' + fmtTime(disaster.reportTime, true)); }
      if (disaster.disasterSummary) { parts.push(disaster.disasterSummary); }
      if (parts.length) { text += '灾情档案 · ' + parts.join(' · ') + '　'; }
    }
    text += 'AI 测算结果由大模型基于公开数据推理生成，不代表官方预报结论，不替代气象部门预警。';
    return U.note(U.esc(text));
  }

  /* ======================================================================== */
  /*  四、错误态                                                              */
  /* ======================================================================== */

  /**
   * 整页错误态卡片（接口失败时使用，绝不回退到演示数据）。
   * @param {string} title 卡片标题
   * @param {string} message 错误原因（err.message，已由 api.js 翻译）
   * @returns {string} 卡片 HTML
   */
  var errorPanel = U.errorPanel;

  /* ======================================================================== */
  /*  五、渲染与交互                                                          */
  /* ======================================================================== */

  $(function () {
    var id = currentId();

    ZFZ.layout.mount('archive');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '台风详情', center: true,
      desc: '实况参数 · 多机构预报对照 · AI 多模型测算 · 轨迹数据导出'
    }));

    var loading = U.loadingRow();

    /* 1) 先渲染「加载中」骨架，避免白屏 */
    $('#page-body').html(
      '<div class="colstack col-main">' +
        U.panel({ cls: 'is-tight', body: loading }) +
        U.panel({ title: '多机构预报路径对照', cls: 'is-tight', body: loading }) +
        U.panel({ title: 'AI 多模型路径智能测算', cls: 'is-tight', body: loading }) +
        U.panel({ title: '实况轨迹数据', cls: 'is-tight', body: loading }) +
      '</div>' +
      '<div class="colstack col-side is-narrow">' +
        U.panel({ title: '风圈半径（四象限）', cls: 'is-tight', body: loading }) +
        U.panel({ title: '卫星云图影像', cls: 'is-tight', body: loading }) +
        U.panel({ title: '历史相似案例 Top 3', cls: 'is-tight', body: loading }) +
      '</div>'
    );

    /* 2) 并发拉取：允许为空的子资源单独兜住，避免把整个 Promise.all 拖成失败 */
    Promise.all([
      ZFZ.api.getTyphoon(id),
      ZFZ.api.getTracks(id),
      ZFZ.api.getWindCircles(id),
      ZFZ.api.getForecastPaths(id),
      ZFZ.api.getAiPredictions(id),
      ZFZ.api.getSimilarCases(id, 3),
      ZFZ.api.getReplay(id),
      ZFZ.api.getDisagreement(id).catch(function () { return null; }),
      ZFZ.api.getDisaster(id).catch(function () { return null; })
    ]).then(function (res) {
      var t = res[0];
      var tracks = res[1] || [];
      var circles = res[2] || [];
      var paths = res[3] || [];
      var ai = res[4];
      var cases = res[5] || [];
      var replay = res[6] || {};
      var dis = res[7];
      var disaster = res[8];

      if (!t) {
        $('#page-body').html(
          '<div class="colstack col-main">' + errorPanel('台风详情', '接口未返回台风 ' + id + ' 的数据') + '</div>'
        );
        return;
      }

      /* 观测时次：实况轨迹的最新时次 */
      var latest = tracks.length ? fmtTime(tracks[tracks.length - 1].time, true) : '—';

      $('#page-title').html(ZFZ.layout.titleBar({
        title: '台风档案 · ' + U.esc(t.nameCn) + ' ' + U.esc(t.code), center: true,
        desc: '实况参数 · 多机构预报对照 · AI 多模型测算 · 轨迹数据导出'
      }));

      $('#page-body').html(
        '<div class="colstack col-main">' +
          heroCard(t) + forecastCard(paths, dis) + aiCard(ai) + trackCard(tracks, t, replay) +
        '</div>' +
        '<div class="colstack col-side is-narrow">' +
          windCircleCard(circles, latest) + satelliteCard(latest) + similarCard(cases) +
          disclaimerNote(disaster) +
        '</div>'
      );

      bind(t);
    }).catch(function (err) {
      $('#page-body').html(
        '<div class="colstack col-main">' + errorPanel('台风详情', err.message) + '</div>'
      );
      ZFZ.layout.toast('加载失败：' + err.message);
    });

    /**
     * 绑定导出与截图交互。
     * @param {Object} t 台风详情
     * @returns {void}
     */
    function bind(t) {
      /**
       * 触发一次轨迹导出：先调接口登记导出记录，再触发浏览器下载。
       * @param {string} format 导出格式：csv（逗号分隔） | xlsx（Excel，手写 OOXML）
       * @returns {void}
       */
      function doExport(format) {
        ZFZ.api.exportTracks({ typhoonId: t.id, format: format })
          .then(function (res) {
            var info = res || {};
            ZFZ.layout.toast('导出已创建：' + (info.fileName || format.toUpperCase()) +
              (info.rows === undefined || info.rows === null ? '' : '（' + info.rows + ' 行）'));
            /* 再触发浏览器下载（响应为文件流，不能走 ajax） */
            ZFZ.api.downloadTracks({ typhoonId: t.id, format: format });
          })
          .catch(function (err) {
            ZFZ.layout.toast('导出失败：' + err.message);
          });
      }

      $('#btn-export').on('click', function () { doExport('csv'); });
      $('#btn-export-xlsx').on('click', function () { doExport('xlsx'); });

      /* 保存截图：把轨迹卡片连同当前样式序列化为独立 SVG 下载。
         —— 纯前端实现，不依赖任何第三方库。 */
      $('#btn-snapshot').on('click', function () {
        var btn = this;
        btn.disabled = true;
        snapshot(t.id).then(function (ok) {
          ZFZ.layout.toast(ok ? '已保存轨迹卡片为 SVG' : '截图失败，请稍后重试');
        }).catch(function () {
          ZFZ.layout.toast('截图失败，请稍后重试');
        }).then(function () { btn.disabled = false; });
      });
    }
  });

  /* ---------------- 轨迹卡片截图导出 ----------------
     把「实况轨迹数据」卡片连同当前样式序列化进 SVG 的 foreignObject，
     下载为独立 .svg 文件。纯前端实现，不依赖任何第三方库。
     @param {number|string} id 台风 id（用于文件名）
     @returns {Promise<boolean>} 是否成功保存 */
  function snapshot(id) {
    var btn = document.getElementById('btn-snapshot');
    var card = btn && btn.closest ? btn.closest('.panel') : null;
    if (!card) { return Promise.resolve(false); }

    var rect = card.getBoundingClientRect();
    var W = Math.round(rect.width), H = Math.round(rect.height);

    /* 收集同源样式表（CDN 的 Bootstrap 跨域读不到 cssRules，跳过即可，其规则本页未用到） */
    var css = '';
    for (var i = 0; i < document.styleSheets.length; i++) {
      var rules;
      try { rules = document.styleSheets[i].cssRules; } catch (e) { continue; }
      if (!rules) { continue; }
      for (var j = 0; j < rules.length; j++) { css += rules[j].cssText + '\n'; }
    }

    var clone = card.cloneNode(true);
    var head = clone.querySelector('.zbtn-row');
    if (head) { head.parentNode.removeChild(head); }        /* 截图里不需要按钮本身 */

    var svg =
      '<svg xmlns="http://www.w3.org/2000/svg" width="' + W + '" height="' + H + '" ' +
        'viewBox="0 0 ' + W + ' ' + H + '">' +
        '<foreignObject x="0" y="0" width="' + W + '" height="' + H + '">' +
          '<div xmlns="http://www.w3.org/1999/xhtml" ' +
            'style="width:' + W + 'px;height:' + H + 'px;background:#FFFFFF">' +
            '<style>' + css + '</style>' +
            clone.outerHTML +
          '</div>' +
        '</foreignObject>' +
      '</svg>';

    var blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = 'zhuifengzhe-typhoon-' + id + '-track.svg';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 1500);
    return Promise.resolve(true);
  }
})(window.jQuery);
