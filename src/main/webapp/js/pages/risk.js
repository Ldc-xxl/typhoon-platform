/* ==========================================================================
   P4 城市风险预评估
   设计稿节点 3:338（1440×920）
   主列：检索条 / 评估结果 / 分时段风险提示
   侧列 340：综合风险等级 / 距离测算 / 推算规则 / 免责
   --------------------------------------------------------------------------
   数据来源（全部走真实接口，失败即渲染错误态，不做 mock 兜底）：
     ZFZ.api.getTyphoons        取当前活跃台风
     ZFZ.api.getCities          取城市清单（基础风险等级 / 常住人口）
     ZFZ.api.getRiskAssessments 取城市风险预评估结果
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /* ---------------- 通用小工具 ---------------- */

  /**
   * 数字两位补零。
   * @param {number} n 数字
   * @returns {string} 补零后的字符串
   */
  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  /**
   * 生成当前时间文本，作为本次评估的基准时间。
   * @returns {string} 形如 '2026-09-30 08:30'
   */
  function nowText() {
    var d = new Date();
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
      ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }

  /**
   * 把后端时间串裁剪为“月-日 时:分”。
   * @param {string} s 后端时间字符串，如 '2026-09-16 23:51:00'
   * @returns {string} 短时间文本，如 '09-16 23:51'；无值返回 '—'
   */
  function shortTime(s) {
    if (!s) { return '—'; }
    var m = String(s).match(/(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/);
    return m ? (m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5]) : String(s);
  }

  /**
   * 解析后端时间串为毫秒时间戳（手写解析，规避各浏览器对
   * "YYYY-MM-DD HH:mm:ss" 的兼容性差异）。
   * @param {string} s 后端时间字符串
   * @returns {number} 毫秒时间戳；无法解析返回 NaN
   */
  function parseMs(s) {
    if (!s) { return NaN; }
    var m = String(s).match(/(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/);
    if (!m) { return NaN; }
    return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]).getTime();
  }

  /**
   * 数值保留 1 位小数。
   * @param {*} v 原值
   * @returns {string} 形如 '14.9'；空值返回 '—'
   */
  function fixed1(v) {
    if (v === null || v === undefined || v === '') { return '—'; }
    var n = Number(v);
    return isNaN(n) ? '—' : n.toFixed(1);
  }

  /**
   * 由受影响起止时间计算时长文本。
   * @param {string} start 开始时间（后端时间串）
   * @param {string} end   结束时间（后端时间串）
   * @returns {string} 形如 '9.0 小时'；无法计算返回 '—'
   */
  function durationText(start, end) {
    var a = parseMs(start), b = parseMs(end);
    if (isNaN(a) || isNaN(b) || b < a) { return '—'; }
    return ((b - a) / 3600000).toFixed(1) + ' 小时';
  }

  /**
   * 风险等级文字 → 时段行配色类（对应 pages.css 的 .slot-row 修饰类）。
   * @param {string} level 后端 riskLevel，如 '低'
   * @returns {string} 'is-high' | 'is-mid' | 'is-low'
   */
  function riskCls(level) {
    if (/高/.test(level || '')) { return 'is-high'; }
    if (/中/.test(level || '')) { return 'is-mid'; }
    if (/低/.test(level || '')) { return 'is-low'; }
    return 'is-mid';
  }

  /** 基础风险等级文字 → 文本配色类（app.css 仅提供 txt-high / txt-warn） */
  function baseRiskCls(text) {
    if (/高/.test(text || '')) { return 'txt-high'; }
    if (/中/.test(text || '')) { return 'txt-warn'; }
    return '';
  }

  /**
   * 后端 level 代码（LOW / MEDIUM / HIGH）→ 标签色调类。
   * @param {string} level 后端 level 代码
   * @returns {string} 标签类名
   */
  function levelTone(level) {
    if (level === 'HIGH') { return 'is-sty'; }
    if (level === 'MEDIUM') { return 'is-warn'; }
    if (level === 'LOW') { return 'is-ok'; }
    return 'is-neutral';
  }

  /** 统一空态行 */
  function emptyRow(text) { return '<div class="empty-row">' + U.esc(text) + '</div>'; }

  /**
   * 统一错误态卡片。
   * @param {string} title 卡片标题
   * @param {string} message 错误文案（来自 err.message）
   * @returns {string} HTML
   */
  function errorPanel(title, message) {
    return U.panel({
      title: title,
      cls: 'is-tight',
      body: '<div class="empty-row">加载失败：' + U.esc(message) +
        '<br>请确认后端服务已启动后刷新页面</div>'
    });
  }

  /**
   * 在 getCities 结果中按 id 查找城市。
   * @param {Array<Object>} cities 城市清单
   * @param {number|string} id 城市 id
   * @returns {Object|null} 命中的城市对象；未找到返回 null
   */
  function findCity(cities, id) {
    var hit = null;
    (cities || []).forEach(function (c) {
      if (String(c.id) === String(id)) { hit = c; }
    });
    return hit;
  }

  /* ---------------- 评估条件 ---------------- */

  /**
   * 顶部评估条件条（城市 / 关联台风 / 评估基准 + 评估按钮）。
   * @param {Object|null} typhoon 当前活跃台风
   * @param {Object|null} a 当前评估记录
   * @param {string} baseTime 评估基准时间
   * @returns {string} HTML
   */
  function conditionCard(typhoon, a, baseTime) {
    return '<section class="panel is-tight">' +
      '<div class="panel-head">' +
        U.chips([
          { k: '城市:', v: a ? a.cityName : '—' },
          { k: '关联台风:', v: typhoon ? (typhoon.nameCn + ' ' + typhoon.code) : '—' },
          { k: '评估基准:', v: baseTime }
        ]) +
        '<button class="zbtn is-primary" type="button" id="btn-assess">开始评估</button>' +
      '</div></section>';
  }

  /* ---------------- 评估结果 ---------------- */

  /**
   * 评估结果卡片：四个关键指标 + 建议文案。
   * @param {Object|null} a 单条风险评估记录（/api/risk/assessments）
   * @param {string} baseTime 评估基准时间
   * @returns {string} HTML
   */
  function resultCard(a, baseTime) {
    if (!a) {
      return U.panel({ title: '评估结果', cls: 'is-tight', body: emptyRow('暂无城市风险评估结果') });
    }

    var tiles = [
      { label: '台风中心直线距离', value: fixed1(a.distance) + ' km', color: '#0E5484' },
      { label: '预计到达时间（ETA）', value: shortTime(a.eta), color: '#152A3D' },
      { label: '受影响时段时长', value: durationText(a.affectedStart, a.affectedEnd), color: '#152A3D' },
      { label: '综合风险等级', value: a.riskLevel + '风险 · ' + fixed1(a.riskScore),
        color: U.riskColor(a.riskLevel), risk: true }
    ].map(function (t) {
      return '<div class="metric-tile' + (t.risk ? ' is-risk' : '') + '" style="background:' +
        (t.risk ? '#FDF6E8' : 'var(--row-alt)') + '">' +
        '<span class="metric-label">' + U.esc(t.label) + '</span>' +
        '<span class="metric-value" style="color:' + t.color + '">' + U.esc(t.value) + '</span>' +
        '</div>';
    }).join('');

    var extra = a.windLevel
      ? '（预计风力 ' + U.esc(a.windLevel) +
        (a.inWindCircle ? '，城市位于 7 级风圈覆盖范围内' : '，暂未进入 7 级风圈') + '）'
      : '';
    var body =
      '<div class="metric-grid is-param" style="grid-template-columns:repeat(4,minmax(0,1fr))">' + tiles + '</div>' +
      U.note(U.esc(a.advice || '暂无评估建议') + extra, 'is-plain');

    return U.panel({
      title: '评估结果 · ' + a.cityName + '（' + a.province + '）',
      sub: '评估时间 ' + baseTime + ' · 基于后端风险预评估结果',
      cls: 'is-tight',
      body: body
    });
  }

  /* ---------------- 分时段风险提示 ---------------- */

  /**
   * 分时段风险提示：把 7 级风圈影响时段渲染为一条时段行。
   * @param {Object|null} a 单条风险评估记录
   * @returns {string} HTML
   */
  function slotsCard(a) {
    if (!a) {
      return U.panel({ title: '分时段风险提示', cls: 'is-tight', body: emptyRow('暂无分时段风险数据') });
    }

    var timeText = shortTime(a.affectedStart) + ' → ' + shortTime(a.affectedEnd);
    var desc = '预计风力 ' + (a.windLevel || '—') +
      (a.inWindCircle ? '，城市位于 7 级风圈覆盖范围内' : '，暂未进入 7 级风圈');
    var rows =
      '<div class="slot-row ' + riskCls(a.riskLevel) + '">' +
        '<span class="slot-time">' + U.esc(timeText) + '</span>' +
        '<span class="slot-body"><b>' + U.esc(a.riskLevel + '风险') + '</b>' +
          '<span class="slot-desc"> · ' + U.esc(desc) + '</span></span>' +
      '</div>';

    return U.panel({
      title: '分时段风险提示',
      sub: '时段 / 风险等级与影响说明',
      cls: 'is-tight',
      body: '<div class="slot-list">' + rows + '</div>'
    });
  }

  /* ---------------- 侧列 ---------------- */

  /**
   * 综合风险等级：评分 + 基础风险 / 风圈 / 人口。
   * @param {Object|null} a 单条风险评估记录
   * @param {Object|null} city getCities 中对应的城市（提供基础风险等级与人口）
   * @returns {string} HTML
   */
  function riskLevelCard(a, city) {
    if (!a) {
      return U.panel({ title: '综合风险等级', cls: 'is-tight', body: emptyRow('暂无综合风险数据') });
    }

    var score = Number(a.riskScore);
    var pct = isNaN(score) ? 0 : Math.max(0, Math.min(100, score));
    var color = U.riskColor(a.riskLevel);
    var baseText = city && city.baseRiskLevelText ? city.baseRiskLevelText : '—';

    var body =
      '<div class="risk-score">' +
        '<span class="risk-score-value" style="color:' + color + '">' + U.esc(a.riskLevel + '风险') + '</span>' +
        '<span class="risk-score-sub">综合风险评分 ' + U.esc(fixed1(a.riskScore)) + ' / 100（参考值）</span>' +
        '<span class="bar" style="height:6px;border-radius:3px;background:var(--line);display:block">' +
          '<i style="display:block;height:100%;width:' + pct.toFixed(1) + '%;border-radius:3px;background:' +
            color + '"></i></span>' +
      '</div>' +
      '<div class="divider-h"></div>' +
      U.simpleRows([
        { k: '城市基础风险等级', v: baseText + ' · ' + (a.province || ''), cls: baseRiskCls(baseText) },
        { k: '风圈影响层级', v: (a.windLevel || '—') + (a.inWindCircle ? ' · 位于风圈内' : ' · 风圈外'),
          cls: a.inWindCircle ? 'txt-warn' : '' },
        { k: '常住人口', v: (city && city.population != null) ? (city.population + ' 万') : '—', cls: '' }
      ]);

    return U.panel({
      title: '综合风险等级',
      headExtra: U.tag(a.level || a.riskLevel, levelTone(a.level)),
      cls: 'is-tight',
      body: body
    });
  }

  /**
   * 距离测算 · 周边城市：直接来自风险评估结果中的距离与等级。
   * @param {Array<Object>} list 风险评估记录数组
   * @returns {string} HTML
   */
  function distanceCard(list) {
    var items = (list || []).map(function (a) {
      return {
        city: a.cityName,
        province: a.province,
        distance: fixed1(a.distance),
        risk: a.riskLevel + '风险'
      };
    });

    return U.panel({
      title: '距离测算 · 周边城市',
      sub: '可在地图点击任意点或选择城市，测算与台风中心的直线距离。',
      cls: 'is-tight',
      body: items.length ? U.distRows(items) : emptyRow('暂无城市距离测算结果')
    });
  }

  /** 推算规则说明（口径说明，与数据源无关，保持原样） */
  function ruleCard() {
    var items = [
      { k: '距离：', v: '台风中心与城市经纬度的球面直线距离' },
      { k: 'ETA：',  v: '按当前移动速度线性推算，未考虑路径转向与强度突变' },
      { k: '时段：', v: '7 级风圈预计覆盖该城市的时间区间' }
    ].map(function (it) {
      return '<div class="rule-row"><b class="rule-k">' + it.k + '</b>' +
        '<span class="rule-v">' + U.esc(it.v) + '</span></div>';
    }).join('');

    return U.panel({
      title: '推算规则说明',
      cls: 'is-tight',
      body: '<div class="rule-rows">' + items + '</div>'
    });
  }

  /* ---------------- 渲染 ---------------- */

  /** 加载中骨架：先占位，避免白屏 */
  function loadingHtml() {
    return '<div class="colstack col-main">' +
        U.panel({ title: '评估结果', cls: 'is-tight', body: emptyRow('加载中…') }) +
      '</div>' +
      '<div class="colstack col-side is-narrow">' +
        U.panel({ title: '综合风险等级', cls: 'is-tight', body: emptyRow('加载中…') }) +
      '</div>';
  }

  /** 绑定「开始评估」按钮：重新拉取接口并刷新页面 */
  function bind() {
    $('#btn-assess').on('click', function () { load(true); });
  }

  /**
   * 用真实数据重渲染整页。
   * @param {Object|null} typhoon 当前活跃台风
   * @param {Array<Object>} cities 城市清单
   * @param {Array<Object>} list 风险评估记录
   * @param {string} baseTime 评估基准时间
   * @returns {void}
   */
  function render(typhoon, cities, list, baseTime) {
    var assessments = list || [];
    var a = assessments.length ? assessments[0] : null;
    var city = a ? findCity(cities, a.cityId) : null;

    var disclaimer = (a && a.disclaimer) ||
      '风险评估仅基于公开预报数据做简易规则推算，实际预警请以当地气象部门与应急管理部门发布的官方信息为准。';

    $('#page-body').html(
      '<div class="colstack col-main">' +
        conditionCard(typhoon, a, baseTime) + resultCard(a, baseTime) + slotsCard(a) +
      '</div>' +
      '<div class="colstack col-side is-narrow">' +
        riskLevelCard(a, city) + distanceCard(assessments) + ruleCard() +
        U.note(U.esc(disclaimer)) +
      '</div>'
    );

    bind();
  }

  /**
   * 加载流程：先取活跃台风，再并发取城市清单与风险评估结果，最后渲染。
   * @param {boolean} [manual] 是否由「开始评估」按钮触发（触发时额外提示）
   * @returns {void}
   */
  function load(manual) {
    $('#page-body').html(loadingHtml());

    ZFZ.api.getTyphoons().then(function (list) {
      var typhoons = list || [];
      var active = null;
      typhoons.forEach(function (t) { if (!active && t.status === 'ACTIVE') { active = t; } });
      if (!active) { active = typhoons[0] || null; }
      var id = active ? active.id : 13;

      return Promise.all([
        ZFZ.api.getCities(),
        ZFZ.api.getRiskAssessments(id)
      ]).then(function (res) {
        var cities = res[0] || [];
        var raw = res[1];
        var assessments = Array.isArray(raw) ? raw : (raw ? [raw] : []);
        render(active, cities, assessments, nowText());
        if (manual) {
          ZFZ.layout.toast('评估完成 · 共 ' + assessments.length + ' 座城市');
        }
      });
    }).catch(function (err) {
      $('#page-body').html(
        '<div class="colstack col-main">' + errorPanel('评估结果', err.message) + '</div>'
      );
      ZFZ.layout.toast('加载失败：' + err.message);
    });
  }

  $(function () {
    ZFZ.layout.mount('risk');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '城市风险预评估', center: true,
      desc: '基于预报路径推算城市距离、预计到达时间与受影响时段'
    }));

    load(false);
  });
})(window.jQuery);
