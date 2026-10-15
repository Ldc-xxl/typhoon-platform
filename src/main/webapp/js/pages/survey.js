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
     问卷提交   ZFZ.api.submitSurvey(payload)          POST /api/surveys（游客可提交，无需登录；只回计数不回显答案）
   接口失败一律渲染错误态并弹 toast，不回退到 js/data.js 的演示数据。

   注意量纲：聚合接口返回的各比例**已经是 0—100**（见 api_samples.txt，
   例如 suppliesRate=70.0），直接拼 `%` 即可，**不要再 ×100**。

   说明：「问卷填写」卡片 = 静态题目定义（题面 / 选项，属 schema，不来自接口）
   + 真实作答表单。表单提交走 ZFZ.api.submitSurvey，成功后重新拉取聚合统计并
   **局部重渲染**看板（不重画整页，避免把用户刚填的表单闪没了）。
   表单与题目列表并入同一个面板，不新增 .panel 节点（页面结构契约固定：
   左列 3 面板 / 主列 3 面板）。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /**
   * 问卷题目定义（静态：题面与选项属于问卷 schema，非业务数据）。
   * 与后端 POST /api/surveys 的语义字段一一对应：
   *   Q1 preparedSupplies（布尔）· Q2 payAttentionEvacuation（布尔）
   *   Q3 worriedDisasterType（WIND / RAIN / STORM_SURGE / OTHER）· Q4 trustLevel（1—5）
   *   Q5 contactInfo（选填）
   *
   * Q1 / Q2 题面严格按「是 / 否」二选一描述：数据库 user_behavior_surveys 的
   * prepared_supplies 与 pay_attention_evacuation 都是 tinyint NOT NULL（1=是 / 0=否），
   * 后端 DTO 也是 Boolean，故不能写成三档口径，否则与库表 / DTO 对不上。
   * @type {Array<{q: string, a: string}>}
   */
  var QA = [
    { q: 'Q1 是否已储备防灾物资（水、食品、药品）？', a: '是 / 否' },
    { q: 'Q2 是否关注官方发布的撤离避险信息？',       a: '是 / 否' },
    { q: 'Q3 最担心的灾害类型是？',                   a: '大风 / 暴雨 / 风暴潮 / 其他（WIND / RAIN / STORM_SURGE / OTHER）' },
    { q: 'Q4 对当前预报的信任程度？',                 a: '1 星 — 5 星（trustLevel 1—5）' },
    { q: 'Q5 联系方式（选填，仅用于回访且脱敏展示）', a: '选填 · 存储时仅保留前 3 位与后 4 位' }
  ];

  /**
   * 各卡片的稳定 id，用于提交成功后按面板「局部重渲染」。
   * 只重画 previewCard() 之外的面板，作答表单（在 previewCard 内）保持不动。
   * @type {Object<string,string>}
   */
  var PANEL = {
    review:   'qa-panel-review',
    setting:  'qa-panel-setting',
    finding:  'qa-panel-finding',
    overview: 'qa-panel-overview',
    table:    'qa-panel-table',
    chart:    'qa-panel-chart'
  };

  /**
   * 页面运行时状态。
   *
   * <p>{@code typhoonId} 与 {@code aggTyphoonId} 是**两个不同的台风**，不要合并：
   * 表单填的问卷要记到「最新活跃台风」名下（{@code typhoonId}），
   * 而看板展示的聚合结果必须落在「有样本的那一条」上（{@code aggTyphoonId}）——
   * 从中央气象台同步进来的新台风一开始是 0 样本，硬拿它聚合只会得到一张空看板。
   * 选择规则见 {@link ZFZ.api.analyzeTarget}。
   *
   * @typedef {Object} SurveyState
   * @property {number|string} typhoonId 表单提交归属的台风 id（= 最新活跃台风）
   * @property {number|string} aggTyphoonId 聚合看板对应的台风 id（无样本时自动回落）
   * @property {Array<Object>} cities 城市清单，用于 cityId → 城市名映射
   * @property {Object|null}   agg 最近一次聚合统计
   * @property {Object|null}   currentTyphoon 当前活跃台风对象（含 nameCn），用于表单「当前台风」提示
   */
  var state = { typhoonId: 13, aggTyphoonId: 13, cities: [], agg: null, currentTyphoon: null };

  /* ======================================================================== */
  /*  一、工具                                                                */
  /* ======================================================================== */

  /**
   * 安全取数：把后端字段转成数字。
   * @param {*} v 原始值
   * @param {*} [fallback] 非法时的回退值，默认 null
   * @returns {number|null|*} 数字或回退值
   */
  var numOf = U.num;

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

  /* ---------------- 作答表单（并入 previewCard 面板内，不新增 .panel 节点） ---------------- */

  /**
   * 当前台风提示文案。
   * @returns {string} 例：'梅花（ID 13）'；无台风对象时退化为 'ID 13'
   */
  function typhoonLabel() {
    var t = state.currentTyphoon;
    if (!t) { return 'ID ' + state.typhoonId; }
    var id = (t.id != null) ? t.id : state.typhoonId;
    return t.nameCn ? (t.nameCn + '（ID ' + id + '）') : ('ID ' + id);
  }

  /**
   * 构造一个单选字段（Q1 / Q2 / Q3 / Q4 共用）。
   * @param {string} label 字段标签（含题号）
   * @param {string} name radio 的 name，也是提交时读取取值的键
   * @param {Array<Array<string>>} options 选项三元组 [value, 文案, id]
   * @returns {string} 字段 HTML
   */
  function radioField(label, name, options) {
    var opts = options.map(function (o) {
      return '<label class="qa-opt"><input type="radio" name="' + name +
        '" id="' + o[2] + '" value="' + U.esc(o[0]) + '">' + U.esc(o[1]) + '</label>';
    }).join('');
    return '<div class="qa-field"><span class="qa-label">' + U.esc(label) + '</span>' +
      '<div class="qa-opts">' + opts + '</div></div>';
  }

  /**
   * 作答表单 HTML（类名沿用已冻结的样式，不新增 style / 内联样式）。
   * 城市下拉仅在 state.cities 非空时渲染（cityId 为提交必填）；为空时提交给出明确提示。
   * @returns {string} 表单 HTML
   */
  function answerForm() {
    var fields = [];

    /* 只读的当前台风提示：让作答者知道这份问卷计到哪个台风上 */
    fields.push('<div class="qa-field"><span class="qa-label">当前台风</span>' +
      '<span id="qa-typhoon">' + U.esc(typhoonLabel()) + '</span></div>');

    /* 城市下拉：选项来自 state.cities，默认选中第一个 */
    if (state.cities.length) {
      var cityOpts = state.cities.map(function (c, i) {
        return '<option value="' + U.esc(String(c.id)) + '"' + (i === 0 ? ' selected' : '') + '>' +
          U.esc(c.name) + '</option>';
      }).join('');
      fields.push('<div class="qa-field"><span class="qa-label">所在城市</span>' +
        '<select class="select" id="qa-city" name="city">' + cityOpts + '</select></div>');
    }

    /* Q1 / Q2 必须是「是 / 否」：值与后端 Boolean 严格对齐（'1' 真 / '0' 假）
       表单标签用短题干，避免与上方 .qa-list 的完整题面重复 */
    fields.push(radioField('Q1 物资储备', 'supplies', [
      ['1', '是', 'qa-supplies-1'], ['0', '否', 'qa-supplies-0']
    ]));
    fields.push(radioField('Q2 关注撤离信息', 'evacuation', [
      ['1', '是', 'qa-evacuation-1'], ['0', '否', 'qa-evacuation-0']
    ]));

    /* Q3 取值即后端枚举，直接下发 WIND / RAIN / STORM_SURGE / OTHER */
    fields.push(radioField('Q3 最担心的灾害', 'disaster', [
      ['WIND', '大风', 'qa-disaster-WIND'],
      ['RAIN', '暴雨', 'qa-disaster-RAIN'],
      ['STORM_SURGE', '风暴潮', 'qa-disaster-STORM_SURGE'],
      ['OTHER', '其他', 'qa-disaster-OTHER']
    ]));

    /* Q4 trustLevel 1—5 */
    fields.push(radioField('Q4 预报信任度', 'trust', [
      ['1', '1 星', 'qa-trust-1'], ['2', '2 星', 'qa-trust-2'], ['3', '3 星', 'qa-trust-3'],
      ['4', '4 星', 'qa-trust-4'], ['5', '5 星', 'qa-trust-5']
    ]));

    /* Q5 选填：空串提交时转 null，不落库空字符串 */
    fields.push('<div class="qa-field"><span class="qa-label">Q5 联系方式（选填）</span>' +
      '<input class="input" id="qa-contact" type="text" maxlength="64" ' +
      'placeholder="选填，仅用于回访且脱敏展示"></div>');

    fields.push('<div class="qa-actions">' +
      '<button class="zbtn is-primary" type="button" id="qa-submit">提交问卷</button>' +
      '<button class="zbtn" type="button" id="qa-reset">重置</button></div>');

    return '<div class="qa-form" id="qa-form">' + fields.join('') + '</div>';
  }

  /**
   * 问卷填写卡片：题目列表（静态 schema）+ 真实作答表单，并入同一个面板。
   * @returns {string} 卡片 HTML
   */
  function previewCard() {
    var items = QA.map(function (x) {
      return '<div class="qa-item"><span class="qa-q">' + U.esc(x.q) + '</span>' +
        '<span class="qa-a">' + U.esc(x.a) + '</span></div>';
    }).join('');

    return U.panel({
      id: PANEL.review,
      title: '问卷填写',
      sub: 'V1.0 · 5 题 · 约 40 秒',
      cls: 'is-tight',
      body: '<div class="qa-list">' + items + '</div>' + answerForm()
    });
  }

  /**
   * 采集与聚合设置卡片。
   * 覆盖城市 / 有效样本量由聚合结果实时推算；样本状态由 enoughSamples 决定。
   * 「最近聚合时间」「问卷采集开关」后端聚合接口未返回，故不再展示（不臆造数据）。
   * @param {Object|null} agg 聚合统计
   * @param {string} [id] 面板 id，供提交成功后局部重渲染定位
   * @returns {string} 卡片 HTML
   */
  function settingCard(agg, id) {
    if (!agg) {
      return U.panel({
        id: id, title: '采集与聚合设置', sub: 'survey_agg_results', cls: 'is-tight',
        body: U.emptyRow('暂无聚合数据')
      });
    }
    var bd = agg.cityBreakdown || [];
    var names = bd.map(function (c) { return cityName(c.cityId); }).join('、') || '—';

    return U.panel({
      id: id,
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
   * @param {string} [id] 面板 id，供提交成功后局部重渲染定位
   * @returns {string} 卡片 HTML
   */
  function findingCard(agg, id) {
    if (!agg) {
      return U.panel({
        id: id, title: '关键发现与建议', sub: '聚合结果自动归纳', cls: 'is-tight',
        body: U.emptyRow('暂无聚合数据')
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
      id: id,
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
   * @param {string} [id] 面板 id，供提交成功后局部重渲染定位
   * @returns {string} 卡片 HTML
   */
  function overviewCard(agg, id) {
    if (!agg) {
      return U.panel({
        id: id, title: '调研总览', cls: 'is-tight',
        body: U.emptyRow('暂无聚合数据')
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
      id: id,
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
   * @param {string} [id] 面板 id，供提交成功后局部重渲染定位
   * @returns {string} 卡片 HTML
   */
  function cityTableCard(agg, id) {
    var bd = (agg && agg.cityBreakdown) || [];
    var head = ['城市', '有效样本', '物资储备率', '撤离关注率', '暴雨内涝担忧', '风暴潮担忧', '低估率'];

    var table;
    if (!bd.length) {
      table = U.emptyRow('当前台风暂无问卷样本');
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
      id: id,
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
   * @param {string} [id] 面板 id，供提交成功后局部重渲染定位
   * @returns {string} 卡片 HTML
   */
  function chartCard(agg, id) {
    var bd = (agg && agg.cityBreakdown) || [];
    var body;

    if (!bd.length) {
      body = U.emptyRow('当前台风暂无问卷样本');
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
      id: id,
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
  var errorPanel = U.errorPanel;

  /* ======================================================================== */
  /*  五、渲染与交互                                                          */
  /* ======================================================================== */

  /**
   * 用真实数据重渲染整页。
   * @param {Object} typhoon 最新活跃台风（表单归属 = live）
   * @param {Object} agg 聚合统计（属于看板台风 = analysis）
   * @param {Array<Object>} cities 城市清单
   * @param {Object} [target] {@link ZFZ.api.analyzeTarget} 的返回值，用于「已自动切换」提示
   * @returns {void}
   */
  function render(typhoon, agg, cities, target) {
    state.typhoonId = typhoon ? typhoon.id : state.typhoonId;
    state.currentTyphoon = typhoon || state.currentTyphoon;
    /* 看板台风取自 analyzeTarget 的结论（未回落时它就是 base 本身） */
    state.aggTyphoonId = (target && target.typhoon && target.typhoon.id != null)
      ? target.typhoon.id
      : (typhoon ? typhoon.id : state.aggTyphoonId);
    state.cities = cities || [];
    state.agg = agg || null;

    $('#page-body').html(
      '<div class="colstack col-side is-wide">' +
        previewCard() + settingCard(state.agg, PANEL.setting) + findingCard(state.agg, PANEL.finding) +
      '</div>' +
      '<div class="colstack col-main">' +
        U.switchNote(target, '还没有问卷样本，统计不出分布与结论') +
        overviewCard(state.agg, PANEL.overview) + cityTableCard(state.agg, PANEL.table) +
        chartCard(state.agg, PANEL.chart) +
      '</div>'
    );
  }

  /* ======================================================================== */
  /*  六、作答表单交互（提交 / 校验 / 重置）                                    */
  /* ======================================================================== */

  /**
   * 读取某个单选组当前选中值。
   * @param {string} name radio 的 name
   * @returns {string} 选中值；未选返回空串
   */
  function checkedValue(name) {
    var el = $('#qa-form input[name="' + name + '"]:checked');
    return el.length ? el.val() : '';
  }

  /**
   * 前端必答校验：Q1—Q4 必答、城市必填（清单为空即视为缺失），Q5 选填。
   * @returns {Array<string>} 缺失项名称数组；全部作答时为空数组
   */
  function missingFields() {
    var miss = [];
    if (!state.cities.length) { miss.push('所在城市'); }
    if (!checkedValue('supplies')) { miss.push('Q1'); }
    if (!checkedValue('evacuation')) { miss.push('Q2'); }
    if (!checkedValue('disaster')) { miss.push('Q3'); }
    if (!checkedValue('trust')) { miss.push('Q4'); }
    return miss;
  }

  /**
   * 组装提交 payload（注意类型：布尔 / 数字 / null）。
   * '1' → true、'0' → false（与后端 Boolean 对齐）；trustLevel / cityId 转 Number；
   * contactInfo 空串转 null。
   * @returns {Object} 与后端 SurveyRequest 语义字段一一对应
   */
  function buildPayload() {
    var contact = ($('#qa-contact').val() || '').trim();
    return {
      typhoonId: state.typhoonId,
      cityId: Number($('#qa-city').val()),
      preparedSupplies: checkedValue('supplies') === '1',
      payAttentionEvacuation: checkedValue('evacuation') === '1',
      worriedDisasterType: checkedValue('disaster'),
      trustLevel: Number(checkedValue('trust')),
      contactInfo: contact === '' ? null : contact
    };
  }

  /**
   * 清空所有作答（提交成功或点「重置」时调用）。
   * @returns {void}
   */
  function resetForm() {
    $('#qa-form input[type="radio"]').prop('checked', false);
    $('#qa-contact').val('');
    $('#qa-city').prop('selectedIndex', 0);
  }

  /**
   * 按面板 id 局部重渲染：只替换 previewCard 之外的面板，保住用户已填的表单。
   * @returns {void}
   */
  function repaint() {
    $('#' + PANEL.setting).replaceWith(settingCard(state.agg, PANEL.setting));
    $('#' + PANEL.finding).replaceWith(findingCard(state.agg, PANEL.finding));
    $('#' + PANEL.overview).replaceWith(overviewCard(state.agg, PANEL.overview));
    $('#' + PANEL.table).replaceWith(cityTableCard(state.agg, PANEL.table));
    $('#' + PANEL.chart).replaceWith(chartCard(state.agg, PANEL.chart));
  }

  /**
   * 拉取最新聚合并局部重渲染看板（提交成功路径专用）。
   * 刷新失败只提示、不影响已成功的提交结果。
   * @returns {Promise<void>}
   */
  function refreshAgg() {
    /* 刷新的必须是看板台风（aggTyphoonId），而不是刚提交问卷的那一条：
       提交记到最新活跃台风名下，但它此刻可能仍不足样本，看板仍按回落台风展示 */
    return ZFZ.api.getSurveyAgg({ typhoonId: state.aggTyphoonId }).then(function (agg) {
      state.agg = agg || null;
      repaint();
    }).catch(function (err) {
      ZFZ.layout.toast('问卷已提交，但刷新聚合失败：' + ((err && err.message) || '未知错误'));
    });
  }

  /**
   * 提交问卷：前置校验 → POST /api/surveys → 成功提示 + 重置 + 局部刷新聚合。
   * 校验不通过不发请求；提交失败保留已填内容，方便直接重试。
   * @returns {void}
   */
  function submitForm() {
    /* 1. 前端校验：缺项则列出名称（顺序按页面从上到下：所在城市 → Q1…），不发请求 */
    var miss = missingFields();
    if (miss.length) {
      ZFZ.layout.toast('请先填写：' + miss.join('、'));
      return;
    }

    /* 2. 组装 payload（类型转换见 buildPayload） */
    var payload = buildPayload();

    /* 3. 提交期间禁用按钮并改文案，结束（无论成败）都恢复 */
    var $btn = $('#qa-submit');
    $btn.prop('disabled', true).text('提交中…');

    ZFZ.api.submitSurvey(payload).then(function (res) {
      /* 4a. 成功：toast 带样本总数 → 重置表单 → 刷新聚合（局部重渲染） */
      var total = (res && res.totalSamples != null) ? res.totalSamples : null;
      ZFZ.layout.toast(total === null
        ? '提交成功，感谢参与'
        : '提交成功，当前共 ' + total + ' 份样本');
      resetForm();
      return refreshAgg();
    }).catch(function (err) {
      /* 4b. 失败：保留用户已填内容，仅提示 */
      ZFZ.layout.toast('提交失败：' + ((err && err.message) || '未知错误'));
    }).then(function () {
      $btn.prop('disabled', false).text('提交问卷');
    });
  }

  /**
   * 加载流程：先取活跃台风，再并发取聚合统计与城市清单，最后渲染。
   * 聚合接口必须带 typhoonId，故先拿到台风 id 再请求。
   * @returns {void}
   */
  function load() {
    var loading = U.loadingRow();

    /* 先用「加载中」占位渲染骨架，避免白屏（面板数量/标题与 render() 一致） */
    $('#page-body').html(
      '<div class="colstack col-side is-wide">' +
        U.panel({ title: '问卷填写', sub: 'V1.0 · 5 题 · 约 40 秒', cls: 'is-tight', body: loading }) +
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
      var actives = (list || []).filter(function (t) { return t && t.status === 'ACTIVE'; });

      /* 看板按台风聚合，而最新活跃台风通常是刚同步进来的、一份样本都没有。
         先用 analyzeTarget 顺序探测"哪条活跃台风有样本"，再按它取聚合；
         没有回落发生时 target.typhoon 就是 active 本身。规则与理由见 ZFZ.api.analyzeTarget。 */
      return ZFZ.api.analyzeTarget({
        base: active,
        actives: actives,
        probe: function (id) {
          return ZFZ.api.getSurveyAgg({ typhoonId: id }).then(function (a) {
            return numOf(a && a.sampleSize, 0) > 0;
          }, function () { return false; });
        }
      }).then(function (target) {
        var aggId = (target.typhoon && target.typhoon.id != null)
          ? target.typhoon.id
          : (active ? active.id : state.typhoonId);

        return Promise.all([
          ZFZ.api.getSurveyAgg({ typhoonId: aggId }),
          ZFZ.api.getCities()
        ]).then(function (res) {
          render(active, res[0], res[1] || [], target);
        });
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

    /* 事件委托必须挂在 #page-body 上：#page-body 本身不会被替换
       （render()/repaint() 只换其子节点），直绑 #qa-submit 会因面板重渲染失效 */
    $('#page-body')
      .off('click', '#qa-submit').on('click', '#qa-submit', submitForm)
      .off('click', '#qa-reset').on('click', '#qa-reset', resetForm);

    load();
  });
})(window.jQuery);
