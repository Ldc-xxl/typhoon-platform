/* ==========================================================================
   P5 历史相似案例归因
   设计稿节点 3:656（1440×1000）
   主列：相似案例匹配结果 / 五维相似度评分明细 / 关键参数对比
   侧列 340：归因结论 / 当年城市灾情档案 / 匹配方法与数据说明

   数据来源（全部走 ZFZ.api，失败即渲染错误态并弹 toast，不回退本地模拟数据）：
     GET /api/typhoons?status=ACTIVE    当前活跃台风（默认操作的基准台风）
     GET /api/similar-cases?typhoonId=  Top-N 相似案例（score 与 matchDims.* 均为 0—1）
     GET /api/typhoons                  台风档案（供「关键参数对比」取历史实况值）
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /** 无活跃台风在编时退回的默认台风 id（当前活跃台风「飞燕」） */
  var DEFAULT_ID = 13;

  /** 五维相似度的展示顺序，与表头「路径形态 / 峰值强度 / 登陆季节 / 移速 / 风圈规模」一一对应 */
  var DIMS = ['path', 'intensity', 'season', 'speed', 'windCircle'];

  /* ---------------- 数值与字段工具 ---------------- */

  /**
   * 把后端 0—1 的相似度得分换算成 0—100 的百分比数值。
   * 列表接口的 score / matchDims.* 与单对打分的各维度都是 0—1，
   * 直接当百分数用会得到「0.8%」这类错误结果。
   * @param {number} v 0—1 的得分
   * @returns {number} 0—100 的百分比数值
   */
  function toPct(v) {
    var n = Number(v);
    return isFinite(n) ? n * 100 : 0;
  }

  /**
   * 相似度百分比文案，保留 1 位小数。
   * @param {number} v 0—1 的得分
   * @returns {string} 形如 '80.2%'
   */
  function pctText(v) { return toPct(v).toFixed(1) + '%'; }

  /**
   * 相似度分档配色：≥ 85 绿 / 75—84 琥珀 / < 75 弱化。
   * @param {number} v 0—100 的百分比数值
   * @returns {string} 'is-good' | 'is-mid' | 'is-low'
   */
  function rateCls(v) {
    if (v >= 85) { return 'is-good'; }
    if (v >= 75) { return 'is-mid'; }
    return 'is-low';
  }

  /**
   * 案例标题：中文名 + 英文名 · 年份。
   * @param {Object} c 相似案例
   * @returns {string}
   */
  function caseLabel(c) { return c.nameCn + ' ' + (c.nameEn || '') + ' · ' + c.year; }

  /**
   * 取日期部分。后端时间为 'YYYY-MM-DD HH:mm:ss'。
   * @param {string} t 时间字符串
   * @returns {string} 'YYYY-MM-DD'；无值时返回空串
   */
  function dayOf(t) { return String(t || '').split(' ')[0]; }

  /**
   * 在台风列表中按 id 查找。
   * @param {Array<Object>} list 台风列表
   * @param {number|string} id 台风 id
   * @returns {Object|null} 命中的台风；未找到返回 null
   */
  function findTyphoon(list, id) {
    var hit = null;
    (list || []).forEach(function (t) { if (String(t.id) === String(id)) { hit = t; } });
    return hit;
  }

  /* ---------------- 相似案例匹配结果 ---------------- */

  /**
   * 「相似案例匹配结果」面板：逐条展示综合相似度与主要差异。
   * @param {Array<Object>} cases 相似案例列表（GET /api/similar-cases）
   * @param {Object} current 当前台风
   * @returns {string} 面板 HTML
   */
  function matchCard(cases, current) {
    if (!cases.length) {
      return U.panel({
        title: '相似案例匹配结果', cls: 'is-tight',
        body: U.emptyRow('暂无相似案例')
      });
    }

    var rows = cases.map(function (c, i) {
      return '<div class="trow is-compare' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(caseLabel(c)) + '</span>' +
        '<span class="rate ' + rateCls(toPct(c.score)) + '">综合相似度 ' + pctText(c.score) + ' ' +
          '<i class="rate-note">· ' + U.esc(c.diffHint || c.note || '') + '</i></span>' +
        '</div>';
    }).join('');

    return U.panel({
      title: '相似案例匹配结果（Top ' + cases.length + '）',
      sub: '当前台风 ' + (current ? current.nameCn : '—') + ' · 5 维特征相似度打分',
      cls: 'is-tight',
      body: '<div class="tbl is-plain is-compare" style="--cols:210px minmax(0,1fr)">' + rows + '</div>'
    });
  }

  /* ---------------- 五维相似度评分明细 ---------------- */

  /**
   * 「五维相似度评分明细」面板：按路径形态 / 峰值强度 / 登陆季节 / 移速 / 风圈规模展开。
   * @param {Array<Object>} cases 相似案例列表
   * @param {Object} current 当前台风
   * @returns {string} 面板 HTML
   */
  function dimCard(cases, current) {
    if (!cases.length) {
      return U.panel({
        title: '五维相似度评分明细', cls: 'is-tight',
        body: U.emptyRow('暂无相似案例')
      });
    }

    var rows = cases.map(function (c, i) {
      var dims = c.matchDims || {};
      return '<div class="trow is-compare' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(caseLabel(c)) + '</span>' +
        DIMS.map(function (k) {
          return '<span class="rate ' + rateCls(toPct(dims[k])) + '">' + pctText(dims[k]) + '</span>';
        }).join('') +
        '</div>';
    }).join('');

    return U.panel({
      title: '五维相似度评分明细',
      sub: '路径形态 / 峰值强度 / 登陆季节 / 移速 / 风圈规模 · 分值越高越接近' +
           (current ? current.nameCn : '当前台风'),
      cls: 'is-tight',
      body: '<div class="tbl is-compare" style="--cols:216px repeat(5, minmax(0,1fr))">' +
        '<div class="tbl-head"><span>历史案例</span><span>路径形态</span><span>峰值强度</span>' +
        '<span>登陆季节</span><span>移速</span><span>风圈规模</span></div>' + rows + '</div>'
    });
  }

  /* ---------------- 关键参数对比 ---------------- */

  /**
   * 关键参数对比的取值规则：每个函数接收一个台风档案对象，返回该行文案。
   * @type {Array<Array>} [行标题, 取值函数]
   */
  var COMPARE_ROWS = [
    ['峰值强度', function (t) {
      return t.windLevel == null ? '—' : t.windLevel + ' 级 · ' + t.windSpeed + ' m/s';
    }],
    ['中心最低气压', function (t) {
      return t.pressure == null ? '—' : t.pressure + ' hPa';
    }],
    ['中心位置', function (t) { return t.centerText || '—'; }],
    ['平均移速', function (t) {
      return t.moveSpeed == null ? '—' : Number(t.moveSpeed).toFixed(1) + ' km/h';
    }],
    ['生成日期', function (t) { return dayOf(t.generateTime) || '—'; }]
  ];

  /**
   * 「关键参数对比」面板：当前台风与 Top-N 相似案例的实况 / 档案参数逐行对照。
   * @param {Array<Object>} cases 相似案例列表
   * @param {Array<Object>} all 台风档案完整列表
   * @param {Object} current 当前台风
   * @returns {string} 面板 HTML
   */
  function compareCard(cases, all, current) {
    if (!current || !cases.length) {
      return U.panel({
        title: '关键参数对比', cls: 'is-tight',
        body: U.emptyRow('暂无可对比的历史案例')
      });
    }

    var cols = [current].concat(cases.map(function (c) { return findTyphoon(all, c.typhoonId); }));
    var head = ['对比项', current.nameCn + '（当前）'].concat(cases.map(function (c) {
      return c.nameCn + ' · ' + c.year;
    }));

    var rows = COMPARE_ROWS.map(function (row, i) {
      var cells = cols.map(function (t) { return row[1](t || {}); });
      return '<div class="trow is-compare' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(row[0]) + '</span>' +
        '<span class="num-amber">' + U.esc(cells[0]) + '</span>' +
        cells.slice(1).map(function (v) {
          return '<span class="cell-num">' + U.esc(v) + '</span>';
        }).join('') +
        '</div>';
    }).join('');

    return U.panel({
      title: '关键参数对比 · ' + current.nameCn + ' vs 历史相似案例',
      sub: '当前为实况值，历史为档案值',
      cls: 'is-tight',
      body: '<div class="tbl is-compare" style="--cols:190px repeat(' + cols.length + ', minmax(0,1fr))">' +
        '<div class="tbl-head">' + head.map(function (h) { return '<span>' + U.esc(h) + '</span>'; }).join('') +
        '</div>' + rows + '</div>'
    });
  }

  /* ---------------- 侧列 ---------------- */

  /**
   * 「归因结论」面板：逐条列出各相似案例的差异点（来自接口 differences / diffHint）。
   * @param {Array<Object>} cases 相似案例列表
   * @returns {string} 面板 HTML
   */
  function conclusionCard(cases) {
    if (!cases.length) {
      return U.panel({
        title: '归因结论', sub: '基于相似案例 · 系统自动归纳', cls: 'is-tight',
        body: U.emptyRow('暂无可归因的相似案例')
      });
    }

    var marks = ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧', '⑨', '⑩'];
    var items = cases.map(function (c, i) {
      var diffs = (c.differences && c.differences.length)
        ? c.differences.join('；')
        : (c.diffHint || '各维度接近，无明显差异');
      return '<div class="attr-item"><i class="attr-no">' + marks[i] + '</i>' +
        '<span class="attr-text"><b>' + U.esc(c.nameCn + ' ' + (c.nameEn || '') + ' · ' + c.year) +
        '（相似度 ' + pctText(c.score) + '）：</b>' + U.esc(diffs) + '</span></div>';
    }).join('');

    return U.panel({
      title: '归因结论',
      sub: '基于 Top' + cases.length + ' 相似案例 · 系统自动归纳',
      cls: 'is-tight',
      body: '<div class="attr-list">' + items + '</div>'
    });
  }

  /**
   * 「当年城市灾情档案」面板：展示 disaster 子对象（影响区域 / 直接损失 / 死亡失踪 / 灾情综述）。
   * @param {Array<Object>} cases 相似案例列表
   * @returns {string} 面板 HTML
   */
  function casualtyCard(cases) {
    if (!cases.length) {
      return U.panel({
        title: '当年城市灾情档案', sub: '公开灾情资料整理', cls: 'is-tight',
        body: U.emptyRow('暂无灾情档案')
      });
    }

    var items = cases.map(function (c) {
      var d = c.disaster;
      if (!d) {
        return '<div class="casualty">' +
          '<span class="casualty-title">' + U.esc(caseLabel(c)) + '</span>' +
          '<span class="casualty-meta">暂无灾情记录</span>' +
          '</div>';
      }
      var area = '影响区域：' + (d.provinces || '—') + (d.city ? ' · 主要城市 ' + d.city : '');
      var loss = '直接损失 ' + Number(d.loss || 0).toFixed(2) + ' 亿元 · 死亡失踪 ' +
        (d.casualties == null ? '—' : d.casualties) + ' 人';
      return '<div class="casualty">' +
        '<span class="casualty-title">' + U.esc(caseLabel(c)) + '</span>' +
        '<span class="casualty-meta">' + U.esc(area) + '</span>' +
        '<span class="casualty-loss">' + U.esc(loss) + '</span>' +
        '<span class="casualty-desc">' + U.esc(d.summary || '') + '</span>' +
        '</div>';
    }).join('');

    return U.panel({
      title: '当年城市灾情档案',
      sub: '公开灾情资料整理',
      cls: 'is-tight',
      body: '<div class="casualty-list">' + items + '</div>'
    });
  }

  /**
   * 「匹配方法与数据说明」面板。样本量取自台风档案接口的真实条数。
   * @param {Array<Object>} all 台风档案完整列表
   * @returns {string} 面板 HTML
   */
  function methodCard(all) {
    return U.panel({
      title: '匹配方法与数据说明',
      cls: 'is-tight',
      body: '<div class="para-block">' +
        '<p class="para">以路径形态、峰值强度、登陆季节、移速、风圈规模 5 个维度，对台风档案库' +
        '（共 ' + U.esc((all || []).length) + ' 例）逐条打分，综合相似度越高代表与当前台风越接近。</p>' +
        '<p class="para">相似度仅基于公开数据的量化类比，不代表本次台风一定会发生同等灾害。</p>' +
        '<p class="para">本页评分与结论由平台算法自动生成，历史灾情取自公开资料，' +
        '仅用于教学演示与辅助研判，不作为防灾决策依据。</p>' +
        '</div>'
    });
  }

  /* ---------------- 加载态 / 错误态 ---------------- */

  /**
   * 首屏占位：先渲染出整体布局，避免白屏等待接口。
   * @returns {string} 占位 HTML
   */
  function loadingState() {
    return '<div class="colstack col-main">' +
        U.panel({ title: '相似案例匹配结果', cls: 'is-tight', body: U.loadingRow() }) +
        U.panel({ title: '五维相似度评分明细', cls: 'is-tight', body: U.loadingRow() }) +
      '</div>' +
      '<div class="colstack col-side is-narrow">' +
        U.panel({ title: '归因结论', cls: 'is-tight', body: U.loadingRow() }) +
      '</div>';
  }

  /**
   * 错误态：接口失败时如实展示，禁止退回本地模拟数据。
   * @param {string} message 可读的错误文案（已由 api.js 翻译）
   * @returns {string} 错误态 HTML
   */
  function errorState(message) {
    return '<div class="colstack col-main">' + U.panel({
      title: '加载失败',
      cls: 'is-tight',
      body: U.errorBody(message)
    }) + '</div>';
  }

  /* ---------------- 入口 ---------------- */
  $(function () {
    ZFZ.layout.mount('similar');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '历史相似案例归因', center: true,
      desc: '当前台风 → 历史相似台风 → 当年城市灾情档案'
    }));

    var $body = $('#page-body');
    $body.html(loadingState());

    /* 本页的落点是**灾情档案**，所以先用"相似案例里有没有带灾情的"作探针选台风：
       相似度最高的一批往往是同季的近期台风，它们还没有灾情记录，
       直接用最新活跃台风会让整页变成空档案。规则与理由见 ZFZ.api.analyzeTarget。 */
    var hasDisaster = function (id) {
      return ZFZ.api.getSimilarCases(id, 3).then(function (rows) {
        return (rows || []).some(function (r) { return r && r.disaster; });
      }, function () { return false; });
    };

    ZFZ.api.getTyphoons({ status: 'ACTIVE' })
      .then(function (active) {
        var actives = active || [];
        var fallback = actives.length ? actives[0] : null;
        return ZFZ.api.analyzeTarget({
          base: fallback,
          actives: actives,
          probe: hasDisaster
        }).then(function (target) {
          var current = target.typhoon || fallback;
          var id = current && current.id != null ? current.id : DEFAULT_ID;
          return ZFZ.api.getSimilarCases(id, 3).then(function (cases) {
            return ZFZ.api.getTyphoons().then(function (all) {
              return {
                current: current || findTyphoon(all, id),
                cases: cases || [], all: all || [], target: target
              };
            });
          });
        });
      })
      .then(function (ctx) {
        if (!ctx.current) { throw new Error('未找到默认台风（id=' + DEFAULT_ID + '）'); }
        $body.html(
          '<div class="colstack col-main">' +
            U.switchNote(ctx.target, '相似度最高的几条都是同季近期台风，尚无灾情档案') +
            matchCard(ctx.cases, ctx.current) +
            dimCard(ctx.cases, ctx.current) +
            compareCard(ctx.cases, ctx.all, ctx.current) +
          '</div>' +
          '<div class="colstack col-side is-narrow">' +
            conclusionCard(ctx.cases) +
            casualtyCard(ctx.cases) +
            methodCard(ctx.all) +
          '</div>'
        );
      })
      .catch(function (err) {
        $body.html(errorState(err.message));
        ZFZ.layout.toast('加载失败：' + err.message);
      });
  });
})(window.jQuery);
