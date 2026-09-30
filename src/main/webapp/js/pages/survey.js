/* ==========================================================================
   P7 公众调研问卷与统计看板
   设计稿节点 3:991（1440×1000）
   左列 340：问卷预览 / 采集与聚合设置 / 关键发现与建议
   主列：调研总览 / 分城市调研指标对比 / 关键指标可视化
   --------------------------------------------------------------------------
   本页数据全部来自 ZFZ.api（改造规范见 docs/frontend-integration-guide.md）：
     活跃台风   ZFZ.api.getTyphoons()                  GET /api/typhoons（取当前活跃台风 id）
     城市清单   ZFZ.api.getCities()                    GET /api/cities（cityBreakdown 只有 cityId，需据此映射城市名）
     聚合统计   ZFZ.api.getSurveyAgg({ typhoonId })    GET /api/surveys/agg（必须带 typhoonId，否则后端返回 code 400）
   接口失败一律渲染错误态并弹 toast，不回退到 js/data.js 的演示数据。

   注意量纲：聚合接口返回的各比例**已经是 0—100**（见 api_samples.txt，
   例如 suppliesRate=70.0），直接拼 `%` 即可，**不要再 ×100**。

   说明：「问卷预览」卡片列出的是问卷题目定义（题面 / 选项），属于静态
   schema，不来自接口；「问卷填写」需要独立的作答表单，本页设计稿未提供，
   故未接 ZFZ.api.submitSurvey。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /**
   * 问卷题目定义（静态：题面与选项属于问卷 schema，非业务数据）。
   * 与后端 POST /api/surveys 的语义字段一一对应：
   *   Q1 preparedSupplies（布尔）· Q2 payAttentionEvacuation（布尔）
   *   Q3 worriedDisasterType（大风/暴雨/风暴潮/其他）· Q4 trustLevel（1—5）
   *   Q5 contactInfo（选填）
   * @type {Array<{q: string, a: string}>}
   */
  var QA = [
    { q: 'Q1 是否已储备防灾物资（水、食品、药品）？', a: '已备足 / 部分储备 / 未储备' },
    { q: 'Q2 是否关注官方发布的撤离避险信息？',       a: '密切关注 / 偶尔关注 / 基本不关注' },
    { q: 'Q3 最担心的灾害类型是？',                   a: '大风 / 暴雨 / 风暴潮 / 其他' },
    { q: 'Q4 对当前预报的信任程度（1—5 星）？',       a: '1 星 — 5 星（trustLevel 1—5）' },
    { q: 'Q5 联系方式（选填，仅用于回访且脱敏展示）', a: '选填 · 存储时仅保留前 3 位与后 4 位' }
  ];

  /**
   * 页面运行时状态。
   * @typedef {Object} SurveyState
   * @property {number|string} typhoonId 当前聚合对应的台风 id
   * @property {Array<Object>} cities 城市清单，用于 cityId → 城市名映射
   * @property {Object|null}   agg 最近一次聚合统计
   */
  var state = { typhoonId: 13, cities: [], agg: null };

  /* ======================================================================== */
  /*  一、工具                                                                */
  /* ======================================================================== */

  /**
   * 安全取数：把后端字段转成数字。
   * @param {*} v 原始值
   * @param {*} [fallback] 非法时的回退值，默认 null
   * @returns {number|null|*} 数字或回退值
   */
  function numOf(v, fallback) {
    var n = Number(v);
    if (isFinite(n)) { return n; }
    return fallback === undefined ? null : fallback;
  }

  /**
   * 百分比格式化。后端聚合比例已是 0—100，直接保留 1 位小数并拼 `%`，不再 ×100。
   * @param {*} v 0—100 的数值
   * @returns {string} 例：'70.0%'；非法值返回 '—'
   */
  function pct(v) {
    var n = numOf(v);
    return n === null ? '—' : n.toFixed(1) + '%';
  }

  /**
   * 按 id 取城市名。
   * @param {number|string} id 城市 id
   * @returns {string} 城市名；未在清单中找到时返回 '城市 ' + id
   */
  function cityName(id) {
    var hit = null;
    state.cities.forEach(function (c) { if (String(c.id) === String(id)) { hit = c; } });
    return hit ? hit.name : ('城市 ' + id);
  }

  /**
   * 取数值数组的最小—最大区间文案。
   * @param {Array<number>} vs 数值数组
   * @returns {string} 例：'58.0% — 70.0%'；无有效值时返回 '—'
   */
  function rangeText(vs) {
    var list = (vs || []).filter(function (v) { return v !== null; });
    if (!list.length) { return '—'; }
    return Math.min.apply(null, list).toFixed(1) + '% — ' +
      Math.max.apply(null, list).toFixed(1) + '%';
  }

  /**
   * 从聚合的城市明细中抽取某字段的数值数组。
   * @param {Array<Object>} breakdown agg.cityBreakdown
   * @param {string} key 字段名
   * @returns {Array<number>} 数值数组（已剔除非法值）
   */
  function pick(breakdown, key) {
    return (breakdown || []).map(function (c) { return numOf(c[key]); })
      .filter(function (v) { return v !== null; });
  }

  /**
   * 取某指标最高的城市及其取值文案。
   * @param {Array<Object>} breakdown agg.cityBreakdown
   * @param {string} key 字段名
   * @returns {string} 例：'福州最高 55.0%'；无数据返回 '—'
   */
  function maxCityText(breakdown, key) {
    var best = null, bestV = null;
    (breakdown || []).forEach(function (c) {
      var v = numOf(c[key]);
      if (v === null) { return; }
      if (bestV === null || v > bestV) { bestV = v; best = c; }
    });
    return best === null ? '—' : cityName(best.cityId) + '最高 ' + bestV.toFixed(1) + '%';
  }

  /**
   * 取当前活跃台风；无活跃台风时回退列表第一个。
   * @param {Array<Object>} list 台风列表
   * @returns {Object|null} 台风对象；列表为空返回 null
   */
  function pickActive(list) {
    var arr = list || [], active = null;
    arr.forEach(function (t) { if (!active && t.status === 'ACTIVE') { active = t; } });
    return active || arr[0] || null;
  }

  /* ======================================================================== */
  /*  二、左列：问卷预览 / 采集与聚合设置 / 关键发现与建议                        */
  /* ======================================================================== */

  /**
   * 问卷预览卡片（静态题目定义，不依赖接口）。
   * @returns {string} 卡片 HTML
   */
  function previewCard() {
    var items = QA.map(function (x) {
      return '<div class="qa-item"><span class="qa-q">' + U.esc(x.q) + '</span>' +
        '<span class="qa-a">' + U.esc(x.a) + '</span></div>';
    }).join('');

    return U.panel({
      title: '问卷预览',
      sub: 'V1.0 · 5 题 · 约 40 秒',
      cls: 'is-tight',
      body: '<div class="qa-list">' + items + '</div>'
    });
  }

  /**
   * 采集与聚合设置卡片。
   * 覆盖城市 / 有效样本量由聚合结果实时推算；样本状态由 enoughSamples 决定。
   * 「最近聚合时间」「问卷采集开关」后端聚合接口未返回，故不再展示（不臆造数据）。
   * @param {Object|null} agg 聚合统计
   * @returns {string} 卡片 HTML
   */
  function settingCard(agg) {
    if (!agg) {
      return U.panel({
        title: '采集与聚合设置', sub: 'survey_agg_results', cls: 'is-tight',
        body: '<div class="empty-row">暂无聚合数据</div>'
      });
    }
    var bd = agg.cityBreakdown || [];
    var names = bd.map(function (c) { return cityName(c.cityId); }).join('、') || '—';

    return U.panel({
      title: '采集与聚合设置',
      sub: 'survey_agg_results',
      cls: 'is-tight',
      body: U.simpleRows([
        { k: '覆盖城市',   v: names, cls: '' },
        { k: '有效样本量', v: numOf(agg.sampleSize, 0) + ' 份', cls: '' },
        { k: '样本状态',   v: agg.enoughSamples ? '样本充足' : '样本不足（结论仅供参考）',
          cls: agg.enoughSamples ? 'txt-ok' : 'txt-warn' }
      ]) + (agg.privacyNote ? U.note(U.esc(agg.privacyNote), 'is-plain') : '')
    });
  }

  /**
   * 关键发现与建议卡片。
   * 主结论直接采用后端依据数据推导的 agg.hint；其余条目由聚合字段归纳。
   * @param {Object|null} agg 聚合统计
   * @returns {string} 卡片 HTML
   */
  function findingCard(agg) {
    if (!agg) {
      return U.panel({
        title: '关键发现与建议', sub: '聚合结果自动归纳', cls: 'is-tight',
        body: '<div class="empty-row">暂无聚合数据</div>'
      });
    }
    var cd = agg.concernDist || {};
    var items = [
      agg.hint || '—',
      '认知偏差：高估率 ' + pct(agg.overestimationRate) + '，低估率 ' + pct(agg.underestimationRate) + '。',
      '担忧结构：大风 ' + pct(cd['大风']) + '、暴雨 ' + pct(cd['暴雨']) + '、风暴潮 ' + pct(cd['风暴潮']) + '。',
      agg.enoughSamples
        ? '有效样本 ' + numOf(agg.sampleSize, 0) + ' 份，聚合结论具备统计参考价值。'
        : '样本量不足，结论仅供参考。'
    ];
    var html = items.map(function (t, i) {
      return '<div class="attr-item"><i class="attr-no">' + '①②③④'[i] + '</i>' +
        '<span class="attr-text">' + U.esc(t) + '</span></div>';
    }).join('');

    return U.panel({
      title: '关键发现与建议',
      sub: '聚合结果自动归纳',
      cls: 'is-tight',
      body: '<div class="attr-list">' + html + '</div>'
    });
  }

  /* ======================================================================== */
  /*  三、主列：调研总览 / 分城市对比 / 关键指标可视化                           */
  /* ======================================================================== */

  /**
   * 调研总览卡片（五项指标全部来自聚合结果）。
   * @param {Object|null} agg 聚合统计
   * @returns {string} 卡片 HTML
   */
  function overviewCard(agg) {
    if (!agg) {
      return U.panel({
        title: '调研总览', cls: 'is-tight',
        body: '<div class="empty-row">暂无聚合数据</div>'
      });
    }
    var bd = agg.cityBreakdown || [];
    var cd = agg.concernDist || {};

    var metrics = U.metricRow([
      { label: '有效样本量', value: String(numOf(agg.sampleSize, 0)),
        sub: '覆盖 ' + bd.length + ' 个沿海城市', color: '#152A3D' },
      { label: '物资储备率（均值）', value: pct(agg.suppliesRate),
        sub: '区间 ' + rangeText(pick(bd, 'suppliesRate')), color: '#0E5484' },
      { label: '撤离关注率（均值）', value: pct(agg.evacuationRate),
        sub: '区间 ' + rangeText(pick(bd, 'evacuationRate')), color: '#2FA84F' },
      { label: '暴雨内涝担忧率', value: pct(cd['暴雨']),
        sub: maxCityText(bd, 'rainWorryRate'), color: '#152A3D' },
      { label: '高可信度比例', value: pct(agg.highTrustRate),
        sub: '高估 ' + pct(agg.overestimationRate) + ' / 低估 ' + pct(agg.underestimationRate),
        color: '#E8833A' }
    ]);

    /* 样本量不足时在总览顶部给出醒目提示 */
    var warn = agg.enoughSamples ? '' : U.note(U.esc('样本量不足，结论仅供参考。'));

    return U.panel({
      title: '调研总览',
      sub: (agg.typhoonName ? agg.typhoonName + ' · ' : '') + 'survey_agg_results',
      cls: 'is-tight',
      body: warn + metrics
    });
  }

  /**
   * 分城市调研指标对比表。
   * 列取自 cityBreakdown：城市 / 有效样本 / 物资储备率 / 撤离关注率 /
   * 暴雨内涝担忧 / 风暴潮担忧 / 低估率（认知偏差）。
   * @param {Object|null} agg 聚合统计
   * @returns {string} 卡片 HTML
   */
  function cityTableCard(agg) {
    var bd = (agg && agg.cityBreakdown) || [];
    var head = ['城市', '有效样本', '物资储备率', '撤离关注率', '暴雨内涝担忧', '风暴潮担忧', '低估率'];

    var table;
    if (!bd.length) {
      table = '<div class="empty-row">当前台风暂无问卷样本</div>';
    } else {
      var rows = bd.map(function (c, i) {
        return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
          '<span class="cell-strong">' + U.esc(cityName(c.cityId)) + '</span>' +
          '<span class="cell-num">' + U.esc(String(numOf(c.sampleSize, 0))) + '</span>' +
          '<span class="cell-num">' + pct(c.suppliesRate) + '</span>' +
          '<span class="cell-num">' + pct(c.evacuationRate) + '</span>' +
          '<span class="cell-num">' + pct(c.rainWorryRate) + '</span>' +
          '<span class="cell-num">' + pct(c.stormSurgeWorryRate) + '</span>' +
          '<span class="cell-num">' + pct(c.underestimationRate) + '</span>' +
          '</div>';
      }).join('');
      table = '<div class="tbl" style="--cols:88px 88px repeat(5, minmax(0,1fr))">' +
        '<div class="tbl-head">' + head.map(function (h) { return '<span>' + h + '</span>'; }).join('') + '</div>' +
        rows + '</div>';
    }

    return U.panel({
      title: '分城市调研指标对比',
      sub: '单位：% · 高估 / 低估指认知偏差比例',
      cls: 'is-tight',
      body: table
    });
  }

  /**
   * 关键指标可视化（分组水平条形图）。
   * 三项指标取 cityBreakdown 中所有城市均有的字段：
   * 物资储备率 / 撤离关注率 / 暴雨担忧率。
   * @param {Object|null} agg 聚合统计
   * @returns {string} 卡片 HTML
   */
  function chartCard(agg) {
    var bd = (agg && agg.cityBreakdown) || [];
    var body;

    if (!bd.length) {
      body = '<div class="empty-row">当前台风暂无问卷样本</div>';
    } else {
      body = U.hbarGroups(
        bd.map(function (c) {
          return {
            name: cityName(c.cityId),
            values: [numOf(c.suppliesRate, 0), numOf(c.evacuationRate, 0), numOf(c.rainWorryRate, 0)]
          };
        }),
        [
          { name: '物资储备率', color: '#0E5484' },
          { name: '撤离关注率', color: '#2FA84F' },
          { name: '暴雨担忧率', color: '#E8833A' }
        ]
      );
    }

    return U.panel({
      title: '关键指标可视化',
      sub: '各城市 × 三项关键指标 · 单位 %',
      cls: 'is-tight',
      body: body
    });
  }

  /* ======================================================================== */
  /*  四、错误态 / 骨架                                                       */
  /* ======================================================================== */

  /**
   * 整页错误态卡片（接口失败时使用，绝不回退到演示数据）。
   * @param {string} title 卡片标题
   * @param {string} message 错误原因（err.message，已由 api.js 翻译）
   * @returns {string} 卡片 HTML
   */
  function errorPanel(title, message) {
    return U.panel({
      title: title,
      cls: 'is-tight',
      body: '<div class="empty-row">加载失败：' + U.esc(message) +
            '<br>请确认后端服务已启动后刷新页面</div>'
    });
  }

  /* ======================================================================== */
  /*  五、渲染与交互                                                          */
  /* ======================================================================== */

  /**
   * 用真实数据重渲染整页。
   * @param {Object} typhoon 当前活跃台风
   * @param {Object} agg 聚合统计
   * @param {Array<Object>} cities 城市清单
   * @returns {void}
   */
  function render(typhoon, agg, cities) {
    state.typhoonId = typhoon ? typhoon.id : state.typhoonId;
    state.cities = cities || [];
    state.agg = agg || null;

    $('#page-body').html(
      '<div class="colstack col-side is-wide">' +
        previewCard() + settingCard(state.agg) + findingCard(state.agg) +
      '</div>' +
      '<div class="colstack col-main">' +
        overviewCard(state.agg) + cityTableCard(state.agg) + chartCard(state.agg) +
      '</div>'
    );
  }

  /**
   * 加载流程：先取活跃台风，再并发取聚合统计与城市清单，最后渲染。
   * 聚合接口必须带 typhoonId，故先拿到台风 id 再请求。
   * @returns {void}
   */
  function load() {
    var loading = '<div class="empty-row">加载中…</div>';

    /* 先用「加载中」占位渲染骨架，避免白屏 */
    $('#page-body').html(
      '<div class="colstack col-side is-wide">' +
        U.panel({ title: '问卷预览', sub: 'V1.0 · 5 题 · 约 40 秒', cls: 'is-tight', body: loading }) +
        U.panel({ title: '采集与聚合设置', sub: 'survey_agg_results', cls: 'is-tight', body: loading }) +
        U.panel({ title: '关键发现与建议', sub: '聚合结果自动归纳', cls: 'is-tight', body: loading }) +
      '</div>' +
      '<div class="colstack col-main">' +
        U.panel({ title: '调研总览', cls: 'is-tight', body: loading }) +
        U.panel({ title: '分城市调研指标对比', cls: 'is-tight', body: loading }) +
        U.panel({ title: '关键指标可视化', cls: 'is-tight', body: loading }) +
      '</div>'
    );

    ZFZ.api.getTyphoons().then(function (list) {
      var active = pickActive(list);
      var id = active ? active.id : state.typhoonId;

      return Promise.all([
        ZFZ.api.getSurveyAgg({ typhoonId: id }),
        ZFZ.api.getCities()
      ]).then(function (res) {
        render(active, res[0], res[1] || []);
      });
    }).catch(function (err) {
      $('#page-body').html(
        '<div class="colstack col-main">' + errorPanel('公众调研看板', err.message) + '</div>'
      );
      ZFZ.layout.toast('加载失败：' + err.message);
    });
  }

  $(function () {
    ZFZ.layout.mount('survey');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '公众调研问卷与统计看板',
      desc: '防灾行为问卷采集 → 城市维度聚合 → 公众风险认知看板'
    }));

    load();
  });
})(window.jQuery);
