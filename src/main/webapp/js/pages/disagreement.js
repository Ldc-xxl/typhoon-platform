/* ==========================================================================
   P3 预报分歧解析
   设计稿节点 3:330（1440×1000）
   主列：分歧量化 / 概率登陆分布热力图
   侧列 340：AI 分歧解读 / 登陆点偏差趋势 / 分歧高危信号 / 免责
   --------------------------------------------------------------------------
   数据来源（全部走真实接口，失败即渲染错误态，不做 mock 兜底）：
     ZFZ.api.getTyphoons      取当前活跃台风
     ZFZ.api.getDisagreement  取多机构分歧解析（可能为 null，需单独兜住）
     ZFZ.api.getForecastPaths 取各机构预报路径（用于登陆点来源说明）
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /* ---------------- 通用小工具 ---------------- */

  /**
   * 数值保留 1 位小数。
   * @param {*} v 原值
   * @returns {string} 形如 '313.9'；空值返回 '—'
   */
  var fixed1 = U.fixed1;

  /**
   * 取数值，非法值回落为默认值。
   * @param {*} v 原值
   * @param {number} fallback 默认值
   * @returns {number} 数值
   */
  function numOr(v, fallback) {
    var n = Number(v);
    return isNaN(n) ? fallback : n;
  }

  /**
   * 把后端时间串裁剪到「分」。
   * @param {string} s 后端时间字符串，如 '2026-09-30 00:50:05'
   * @returns {string} 形如 '2026-09-30 00:50'；无值返回 ''
   */
  function shortSec(s) {
    if (!s) { return ''; }
    var m = String(s).match(/(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/);
    return m ? (m[1] + '-' + m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5]) : String(s);
  }

  /** 统一空态行 */
  var emptyRow = U.emptyRow;

  /**
   * 统一错误态卡片。
   * @param {string} title 卡片标题
   * @param {string} message 错误文案（来自 err.message）
   * @returns {string} HTML
   */
  var errorPanel = U.errorPanel;

  /**
   * 从台风列表中取第一个活跃台风（无活跃则取第一条）。
   * @param {Array<Object>} list getTyphoons 结果
   * @returns {Object|null} 台风对象
   */
  function findActive(list) {
    var active = null;
    (list || []).forEach(function (t) { if (!active && t.status === 'ACTIVE') { active = t; } });
    if (!active) { active = (list && list[0]) || null; }
    return active;
  }

  /**
   * 由方差（0—1）推断风险等级文字。
   * @param {number} v variance
   * @returns {string} '高' | '中' | '低'
   */
  function varianceLevel(v) {
    var n = numOr(v, 0);
    if (n >= 0.5) { return '高'; }
    if (n >= 0.25) { return '中'; }
    return '低';
  }

  /* ---------------- 分歧量化 ---------------- */

  /**
   * 多机构预报分歧量化：各维度 + 综合分歧指数。
   * @param {Object|null} d getDisagreement 结果
   * @returns {string} HTML
   */
  function quantifyCard(d) {
    if (!d) {
      return U.panel({ title: '多机构预报分歧量化', cls: 'is-tight', body: emptyRow('该台风暂无分歧解析') });
    }

    var dims = d.dimensions || [];
    var rows = dims.map(function (dim) {
      var v = fixed1(dim.range) + ' km';
      if (dim.spread) { v += '（' + dim.spread + '）'; }
      if (dim.comment) { v += ' · ' + dim.comment; }
      return '<div class="km-row"><span class="km-name">' + U.esc(dim.name) + '</span>' +
        '<span class="km-value" style="color:#152A3D">' + U.esc(v) + '</span></div>';
    }).join('');
    if (!rows) { rows = emptyRow('暂无分歧维度数据'); }

    /* index 为 0—1 的指数，展示成百分比需 ×100 */
    var pct = Math.max(0, Math.min(100, numOr(d.index, 0) * 100));
    var idxColor = d.highUncertainty ? '#D8322B' : '#E8833A';

    var index =
      '<div class="km-index">' +
        '<div class="km-index-line">' +
          '<span class="km-name" style="white-space:nowrap">综合分歧指数</span>' +
          '<span class="bar"><i style="width:' + pct.toFixed(1) + '%;background:' + idxColor + '"></i></span>' +
        '</div>' +
        '<div class="km-index-value">' + pct.toFixed(1) + ' <small>/ 100</small></div>' +
      '</div>';

    var head = '多机构预报分歧量化';
    if (d.typhoonName) { head += ' · ' + d.typhoonName; }
    if (d.analysisTime) { head += '（' + shortSec(d.analysisTime) + '）'; }

    return U.panel({
      title: head,
      headExtra: d.highUncertainty ? U.tag('高不确定性事件', 'is-sty') : U.tag('不确定性可控', 'is-ok'),
      cls: 'is-tight',
      body: '<div class="km-rows">' + rows + '</div><div class="divider-h"></div>' + index
    });
  }

  /* ---------------- 概率登陆分布热力图 ---------------- */

  /**
   * 以各机构预报路径的登陆点生成热力图卡片副标题，说明示意图的概率来源。
   * @param {Array<Object>} paths getForecastPaths 结果
   * @returns {string} 副标题文本
   */
  function heatSub(paths) {
    if (!paths || !paths.length) { return '替代单点概率，按网格概率密度渲染 · 单位 %'; }
    var txt = paths.map(function (p) {
      return (p.agency || p.name || '—') + ' · ' + (p.landing || '—');
    }).join(' ｜ ');
    return '各机构预报登陆点：' + txt;
  }

  /**
   * 概率登陆分布热力图卡片（示意图，配机构登陆点来源说明）。
   * @param {Object|null} d getDisagreement 结果
   * @param {Array<Object>} paths getForecastPaths 结果
   * @returns {string} HTML
   */
  function heatCard(d, paths) {
    if (!d) {
      return U.panel({ title: '概率登陆分布热力图（示意）', cls: 'is-tight', body: emptyRow('该台风暂无分歧解析') });
    }
    return U.panel({
      title: '概率登陆分布热力图（示意）',
      sub: heatSub(paths),
      cls: 'is-tight',
      body: U.heatMap() + U.heatLegend()
    });
  }

  /* ---------------- 侧列 ---------------- */

  /**
   * 分歧解读（AI 生成）：结论 + 参与机构 / 收敛状态。
   * @param {Object|null} d getDisagreement 结果
   * @returns {string} HTML
   */
  function interpretCard(d) {
    if (!d) {
      return U.panel({
        title: '分歧解读（AI 生成）',
        headExtra: '<span class="ai-tag">DeepSeek-V3</span>',
        cls: 'is-tight',
        body: emptyRow('该台风暂无分歧解析')
      });
    }

    var tail = d.agencyCount
      ? '（共 ' + d.agencyCount + ' 家机构参与' + (d.convergence ? '，分歧' + d.convergence : '') + '）'
      : '';

    return U.panel({
      title: '分歧解读（AI 生成）',
      headExtra: '<span class="ai-tag">DeepSeek-V3</span>',
      cls: 'is-tight',
      body: '<p class="para">' + U.esc((d.conclusion || '暂无解读结论') + tail) + '</p>'
    });
  }

  /**
   * 登陆点偏差趋势（示意图），头部标记随不确定性变化。
   * @param {Object|null} d getDisagreement 结果
   * @returns {string} HTML
   */
  function trendCard(d) {
    if (!d) {
      return U.panel({ title: '登陆点偏差趋势', cls: 'is-tight', body: emptyRow('该台风暂无分歧解析') });
    }

    var flagColor = d.highUncertainty ? '#D8322B' : '#E8833A';
    var flagText = d.highUncertainty ? '分歧扩大' : ('分歧' + (d.convergence || '收敛'));

    return U.panel({
      title: '登陆点偏差趋势',
      headExtra: '<span class="head-flag" style="color:' + flagColor + '">' + U.esc(flagText) + '</span>',
      cls: 'is-tight',
      body: U.devTrendChart()
    });
  }

  /**
   * 分歧高危信号：把各维度按方差映射为高 / 中 / 低。
   * @param {Object|null} d getDisagreement 结果
   * @returns {string} HTML
   */
  function hazardCard(d) {
    if (!d || !(d.dimensions || []).length) {
      return U.panel({ title: '分歧高危信号', cls: 'is-tight', body: emptyRow('该台风暂无分歧解析') });
    }

    var rows = d.dimensions.map(function (dim) {
      var lv = varianceLevel(dim.variance);
      return '<div class="hazard-row"><span class="hz-name">' + U.esc(dim.name) + '</span>' +
        '<span class="hz-value">' + U.esc(fixed1(dim.range) + ' km') + '</span>' +
        '<span class="hz-level" style="color:' + U.riskColor(lv) + '">' + U.esc(lv) + '</span></div>';
    }).join('');

    return U.panel({
      title: '分歧高危信号',
      headExtra: '<span class="head-flag" style="color:' + U.riskColor(d.riskLevel) + '">' +
        U.esc(d.riskLevel || '—') + '</span>',
      cls: 'is-tight',
      body: '<div class="hazard-rows">' + rows + '</div>'
    });
  }

  /* ---------------- 渲染 ---------------- */

  /** 加载中骨架：先占位，避免白屏 */
  function loadingHtml() {
    return '<div class="colstack col-main">' +
        U.panel({ title: '多机构预报分歧量化', cls: 'is-tight', body: emptyRow('加载中…') }) +
      '</div>' +
      '<div class="colstack col-side is-narrow">' +
        U.panel({ title: '分歧解读（AI 生成）', cls: 'is-tight', body: emptyRow('加载中…') }) +
      '</div>';
  }

  /**
   * 用真实数据重渲染整页。
   * @param {Object|null} d getDisagreement 结果（可能为 null）
   * @param {Array<Object>} paths getForecastPaths 结果
   * @param {Object} [target] {@link ZFZ.api.analyzeTarget} 的返回值，用于说明是否换过台风
   * @returns {void}
   */
  function render(d, paths, target) {
    var disclaimer = (d && d.disclaimer) ||
      '预报分歧解析仅为多源数据的量化分析结果，不能作为判断台风发展走向的唯一依据，请以气象部门官方预报为准。';

    $('#page-body').html(
      '<div class="colstack col-main">' +
        U.switchNote(target, '暂无多机构预报（对外可用预报机构仅中央气象台一家），算不出分歧') +
        quantifyCard(d) + heatCard(d, paths) + '</div>' +
      '<div class="colstack col-side is-narrow">' +
        interpretCard(d) + trendCard(d) + hazardCard(d) +
        U.note(U.esc(disclaimer)) +
      '</div>'
    );
  }

  /**
   * 加载流程：先取台风清单，再按 {@link ZFZ.api.analyzeTarget} 选定"算得出来"的台风，
   * 最后并发取分歧解析与预报路径并渲染。
   *
   * <p>为什么不直接取活跃台风：分歧解析需要 ≥2 家机构的预报才能量化，
   * 而接真实数据后最新的活跃台风（小熊）只有中央气象台一家——
   * 这会得到一个合法但为空的页面，看起来像功能坏了。选台规则与理由见 analyzeTarget。
   *
   * @returns {void}
   */
  function load() {
    $('#page-body').html(loadingHtml());

    ZFZ.api.getTyphoons({ limit: 200 }).then(function (list) {
      var all = list || [];
      var actives = all.filter(function (t) { return t.status === 'ACTIVE'; });
      var fallback = findActive(all);

      return ZFZ.api.analyzeTarget({
        base: actives[0] || fallback,
        actives: actives.length ? actives : (fallback ? [fallback] : []),
        probe: function (id) {
          return ZFZ.api.getDisagreement(id).then(function (d) {
            return !!d && (d.dimensions || []).length > 0;
          }, function () { return false; });
        }
      }).then(function (target) {
        var id = target.typhoon && target.typhoon.id != null ? target.typhoon.id : fallback.id;
        return Promise.all([
          ZFZ.api.getDisagreement(id).catch(function () { return null; }),
          ZFZ.api.getForecastPaths(id)
        ]).then(function (res) {
          render(res[0], res[1] || [], target);
        });
      });
    }).catch(function (err) {
      $('#page-body').html(
        '<div class="colstack col-main">' + errorPanel('预报分歧解析', err.message) + '</div>'
      );
      ZFZ.layout.toast('加载失败：' + err.message);
    });
  }

  $(function () {
    ZFZ.layout.mount('disagree');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '预报分歧解析', center: true,
      desc: '多机构集合预报分歧与不确定性量化 · 非单纯路径叠加'
    }));

    load();
  });
})(window.jQuery);
