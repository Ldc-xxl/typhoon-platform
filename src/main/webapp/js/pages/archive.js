/* ==========================================================================
   P8 台风档案检索
   设计稿节点 3:1177（1440×1000）
   主列：检索条件 / 档案结果
   侧列 340：档案统计 / 最近导出记录 / 峰值强度分布
   --------------------------------------------------------------------------
   本页数据全部来自 ZFZ.api（改造规范见 docs/frontend-integration-guide.md）：
     档案列表      ZFZ.api.getTyphoons(params)      GET /api/typhoons
     筛选元数据    ZFZ.api.getTyphoonMeta()         GET /api/typhoons/meta
     最近导出记录  ZFZ.api.getExportHistory()       GET /api/export/history（需登录）
     导出 CSV/Excel ZFZ.api.exportTracks({format})   GET /api/export/tracks（先取信息）
                   + ZFZ.api.downloadTracks()       同接口 download=1（触发浏览器下载）
                   format 取值 csv | xlsx，对应「导出 CSV」与「导出 Excel」两个按钮
   接口失败一律渲染错误态并弹 toast，不回退到 js/data.js 的演示数据。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /**
   * 单次拉取条数上限。后端 limit 的允许区间是 [0, 500]，且 page 参数无效
   * （传 page=2 仍返回第一页），因此这里一次取回全部档案，页面自身不做分页。
   * 档案总数位于响应头 X-Total-Count，而 api.js 只 resolve 了 data，
   * 页面拿不到该响应头，所以总数文案统一表述为「已加载 N 条」。
   * @type {number}
   */
  var PAGE_LIMIT = 200;

  /**
   * 默认检索参数：按编号倒序（与设计稿「按编号倒序」一致）。
   * @type {Object}
   */
  var DEFAULT_PARAMS = { limit: PAGE_LIMIT, sortBy: 'code', order: 'desc' };

  /**
   * 页面运行时状态。
   * @typedef {Object} ArchiveState
   * @property {Array<Object>} full 首次加载的完整档案列表，侧列统计以其为准
   * @property {Array<Object>} list 当前结果表展示的档案，导出按钮据此取主对象
   * @property {Object|null}   meta 筛选元数据
   */
  var state = { full: [], list: [], meta: null };

  /* ======================================================================== */
  /*  一、工具                                                                */
  /* ======================================================================== */

  /**
   * 按（峰值）风力等级取强度配色。
   * 分级规则统一由组件库 {@link ZFZ.ui.levelColor} 提供（该函数原先依赖 js/data.js
   * 的演示数据，现已改为组件库自有的静态分级表），本页不再自带一份实现，
   * 避免同一个分级在两处出现不一致。
   * @param {number|string} windLevel 风力等级，单位「级」
   * @returns {string} 十六进制色值
   */
  function levelColor(windLevel) {
    return U.levelColor(windLevel);
  }

  /**
   * 安全取数：把后端字段转成数字，非法值回退。
   * @param {*} v 原始值
   * @param {*} [fallback] 非法时的回退值，默认 null
   * @returns {number|null|*} 数字或回退值
   */
  function num(v, fallback) {
    var n = Number(v);
    if (isFinite(n)) { return n; }
    return fallback === undefined ? null : fallback;
  }

  /**
   * 解析后端时间串（'YYYY-MM-DD HH:mm:ss'）为时间戳。
   * 把 '-' 换成 '/' 是为了兼容 Safari 对 'YYYY-MM-DD HH:mm:ss' 的解析差异。
   * @param {string} s 时间串
   * @returns {number|null} 时间戳；无法解析时返回 null
   */
  function parseTime(s) {
    if (!s) { return null; }
    var t = Date.parse(String(s).replace(/-/g, '/'));
    return isNaN(t) ? null : t;
  }

  /**
   * 取时间串的展示形式。
   * @param {string} s 后端时间串，例：'2024-09-01 20:00:00'
   * @param {boolean} [withTime] true 保留到分钟，false 只保留日期
   * @returns {string} 例：'2024-09-01' 或 '2024-09-01 20:00'；空值返回 '—'
   */
  function fmtTime(s, withTime) {
    if (!s) { return '—'; }
    var m = String(s).match(/^(\d{4}-\d{2}-\d{2})(?:[ T](\d{2}:\d{2}))?/);
    if (!m) { return String(s); }
    return withTime ? (m[1] + ' ' + (m[2] || '00:00')) : m[1];
  }

  /**
   * 档案的「生成 → 结束」时间文案。
   * 结束时间取后端字段 dissipateTime；活跃台风没有该字段，显示「进行中」。
   * @param {Object} t 台风对象
   * @returns {string} 例：'2024-09-01 → 2024-09-09'
   */
  function spanText(t) {
    var gen = fmtTime(t.generateTime);
    var end = t.status === 'ACTIVE' ? '进行中' : fmtTime(t.dissipateTime);
    return gen + ' → ' + end;
  }

  /**
   * 档案的峰值强度文案。
   * 峰值风力 / 峰值风速取 maxWindForce / maxWindSpeed，缺失时回退当前值。
   * @param {Object} t 台风对象
   * @returns {string} 例：'17 级 · 62 m/s'
   */
  function peakText(t) {
    var lv = num(t.maxWindForce, num(t.windLevel));
    var sp = num(t.maxWindSpeed, num(t.windSpeed));
    return (lv === null ? '—' : lv + ' 级') + ' · ' + (sp === null ? '—' : sp + ' m/s');
  }

  /**
   * 峰值风力（级），用于分档统计。
   * @param {Object} t 台风对象
   * @returns {number|null}
   */
  function peakWind(t) {
    return num(t.maxWindForce, num(t.windLevel));
  }

  /* ======================================================================== */
  /*  二、主列：检索条件 / 档案结果                                            */
  /* ======================================================================== */

  /**
   * 检索条件卡片。
   * 四个 field-box 中「台风名称」是真实的可输入检索项（id=q-name），
   * 其余三项展示由 getTyphoonMeta() 返回的可用取值区间，不再写死。
   * @param {Object} meta 筛选元数据 { years, basins, statuses, ... }
   * @param {Array<Object>} list 已加载的档案列表（用于推算峰值强度区间）
   * @returns {string} 卡片 HTML
   */
  function queryCard(meta, list) {
    var years = (meta && meta.years) || [];
    var basins = (meta && meta.basins) || [];
    var peaks = (list || []).map(peakWind).filter(function (v) { return v !== null; });

    var yearText = years.length
      ? '全部年份（' + Math.min.apply(null, years) + '—' + Math.max.apply(null, years) + '）'
      : '全部年份';
    var peakRange = peaks.length
      ? '全部等级（' + Math.min.apply(null, peaks) + '—' + Math.max.apply(null, peaks) + ' 级）'
      : '全部等级';
    var basinText = (basins.length ? '全部海域（' + basins.join(' · ') + '）' : '全部海域') + ' · 全部状态';

    var fields =
      U.fieldBox('年份', yearText) +
      '<div class="field-box"><span class="field-box-label">台风名称</span>' +
        '<input class="field-input" id="q-name" type="text" placeholder="支持中英文模糊匹配"></div>' +
      U.fieldBox('峰值强度', peakRange) +
      U.fieldBox('生成海域 / 状态', basinText);

    var buttons =
      '<div class="zbtn-row is-inline">' +
        '<button class="zbtn is-primary" type="button" id="btn-query">查询档案</button>' +
        '<button class="zbtn" type="button" id="btn-reset">重置条件</button>' +
        '<button class="zbtn" type="button" id="btn-export-csv">导出 CSV</button>' +
        '<button class="zbtn" type="button" id="btn-export-xlsx">导出 Excel</button>' +
      '</div>';

    return U.panel({
      title: '检索条件',
      sub: '支持模糊匹配与多条件组合查询',
      cls: 'is-tight',
      body: '<div class="query-form"><div class="query-fields">' + fields + '</div>' + buttons + '</div>'
    });
  }

  /**
   * 档案结果表的行 HTML。
   * @param {Array<Object>} list 台风数组
   * @returns {string} 连续 .trow 组成的 HTML
   */
  function rowsHtml(list) {
    return list.map(function (t, i) {
      var active = t.status === 'ACTIVE';
      return '<div class="trow' + (active ? ' is-active' : (i % 2 === 0 ? ' is-alt' : '')) + '">' +
        '<span class="cell-num">' + U.esc(t.code) + '</span>' +
        '<span class="cell-strong">' + U.esc(t.nameCn + ' ' + t.nameEn) + '</span>' +
        '<span class="cell-num">' + U.esc(spanText(t)) + '</span>' +
        '<span class="cell-num" style="color:' + levelColor(t.maxWindForce || t.windLevel) + '">' +
          U.esc(peakText(t)) + '</span>' +
        '<span class="state-cell"><i class="state-txt" style="color:' +
          (active ? '#E8833A' : 'var(--ink-2)') + '">' +
          U.esc(t.statusText || (active ? '活跃中' : '已结束')) + '</i>' +
          '<i class="state-op">· 查看详情</i></span>' +
        '</div>';
    }).join('');
  }

  /**
   * 档案结果卡片的说明文案。
   * 后端总数在响应头里拿不到，所以只报「已加载 N 条」。
   * @param {number} count 当前结果条数
   * @param {string} keyword 当前检索关键词（空串表示未检索）
   * @returns {string} 例：'已加载 13 条档案 · 按编号倒序'
   */
  function resultSub(count, keyword) {
    return (keyword ? '匹配「' + keyword + '」共 ' + count + ' 条档案' : '已加载 ' + count + ' 条档案') +
      ' · 按编号倒序';
  }

  /**
   * 档案结果卡片。
   * @param {Array<Object>} list 台风数组
   * @param {string} sub 标题右侧说明
   * @returns {string} 卡片 HTML
   */
  function resultCard(list, sub) {
    var rows = list.length
      ? rowsHtml(list)
      : '<div class="empty-row">没有匹配的档案，请调整检索条件。</div>';

    return '<section class="panel is-tight">' +
      '<div class="panel-head"><span class="panel-title">档案结果</span>' +
        '<span class="panel-sub" id="result-sub">' + U.esc(sub) + '</span></div>' +
      '<div class="tbl" style="--cols:104px 186px 240px 168px minmax(0,1fr)">' +
        '<div class="tbl-head"><span>编号</span><span>台风名称</span><span>生成 → 结束时间</span>' +
        '<span>峰值强度</span><span>状态 / 操作</span></div>' +
        '<div id="archive-rows" class="tbl-rows">' + rows + '</div>' +
      '</div></section>';
  }

  /* ======================================================================== */
  /*  三、侧列：档案统计 / 最近导出记录 / 峰值强度分布                          */
  /* ======================================================================== */

  /**
   * 峰值强度分档（与设计稿分档一致）。
   * @param {Array<Object>} list 台风数组
   * @returns {{p17: number, p16: number, p1415: number, le13: number}} 各档条数
   */
  function intensityBuckets(list) {
    var b = { p17: 0, p16: 0, p1415: 0, le13: 0 };
    list.forEach(function (t) {
      var v = peakWind(t);
      if (v === null) { return; }
      if (v >= 17) { b.p17++; }
      else if (v >= 16) { b.p16++; }
      else if (v >= 14) { b.p1415++; }
      else { b.le13++; }
    });
    return b;
  }

  /**
   * 档案统计卡片：全部指标由已加载的档案列表实时推算。
   * @param {Array<Object>} list 台风数组
   * @param {Object|null} meta 筛选元数据
   * @returns {string} 卡片 HTML
   */
  function statCard(list, meta) {
    var n = list.length;
    var active = list.filter(function (t) { return t.status === 'ACTIVE'; }).length;
    var b = intensityBuckets(list);

    /* 生成海域计数 */
    var basinCount = {};
    list.forEach(function (t) {
      var k = t.basin || '未标注';
      basinCount[k] = (basinCount[k] || 0) + 1;
    });
    var basinText = Object.keys(basinCount).map(function (k) {
      return k + ' ' + basinCount[k];
    }).join(' · ') || '—';

    /* 平均气压：取各档案 pressure 字段的均值 */
    var pressures = list.map(function (t) { return num(t.pressure); })
      .filter(function (v) { return v !== null; });
    var avgPressure = pressures.length
      ? (pressures.reduce(function (a, c) { return a + c; }, 0) / pressures.length).toFixed(1) + ' hPa'
      : '—';

    /* 最长生命史：generateTime → dissipateTime（仅归档台风有结束时间） */
    var longest = null, longestDays = 0;
    list.forEach(function (t) {
      var a = parseTime(t.generateTime), z = parseTime(t.dissipateTime);
      if (a === null || z === null) { return; }
      var days = (z - a) / 86400000;
      if (days > longestDays) { longestDays = days; longest = t; }
    });

    var years = (meta && meta.years) || [];
    var sub = years.length
      ? Math.min.apply(null, years) + '—' + Math.max.apply(null, years) + ' 全部档案'
      : '全部档案';

    return U.panel({
      title: '档案统计',
      sub: sub,
      cls: 'is-tight',
      body: U.kvLines([
        { k: '档案总数',     v: n + ' 条（含 ' + active + ' 个活跃台风）' },
        { k: '峰值强度分布', v: '≤13 级 ' + b.le13 + ' · 14—15 级 ' + b.p1415 +
                               ' · 16 级 ' + b.p16 + ' · 17 级 ' + b.p17 },
        { k: '生成海域',     v: basinText },
        { k: '平均峰值气压', v: avgPressure },
        { k: '最长生命史',   v: longest
            ? longest.nameCn + ' ' + longest.nameEn + ' · ' + Math.round(longestDays) + ' 天'
            : '—' }
      ])
    });
  }

  /**
   * 峰值强度分布卡片。
   * @param {Array<Object>} list 台风数组
   * @returns {string} 卡片 HTML
   */
  function distCard(list) {
    var n = list.length;
    if (!n) {
      return U.panel({
        title: '峰值强度分布', sub: '0 条档案', cls: 'is-tight',
        body: '<div class="empty-row">暂无档案数据</div>'
      });
    }
    var b = intensityBuckets(list);
    var pct = Math.round(b.p17 / n * 100);

    return U.panel({
      title: '峰值强度分布',
      sub: n + ' 条档案',
      cls: 'is-tight',
      body: U.distBars([
        { name: '17 级',    count: b.p17,   color: '#8B4BB8' },
        { name: '16 级',    count: b.p16,   color: '#8B4BB8' },
        { name: '14—15 级', count: b.p1415, color: '#D8322B' },
        { name: '≤13 级',   count: b.le13,  color: '#E8833A' }
      ]) + U.note(U.esc('17 级（超强台风）共 ' + b.p17 + ' 条，占档案库 ' + pct + '%。'), 'is-plain')
    });
  }

  /**
   * 从导出文件名解析导出日期。
   * 后端导出记录没有单独的时间字段，但文件名形如 '202615-csv-20260930.csv'，
   * 其中的 8 位数字即服务端生成文件时的日期。
   * @param {string} name 文件名
   * @returns {string} 例：'09-30'；解析不到返回 '—'
   */
  function dateFromFileName(name) {
    var m = String(name || '').match(/(20\d{2})(\d{2})(\d{2})/);
    return m ? (m[2] + '-' + m[3]) : '—';
  }

  /**
   * 最近导出记录卡片。
   * 数据来自 GET /api/export/history（需登录）：后端只回传
   * fileName / format / size / typhoonCode，因此行内不出现用户名。
   * @param {Array<Object>} records 导出记录数组
   * @returns {string} 卡片 HTML
   */
  function exportCard(records) {
    var rows = (records || []).slice(0, 5).map(function (r) {
      return {
        user: r.fileName || ('台风 ' + r.typhoonId),
        type: String(r.format || 'csv').toUpperCase(),
        size: r.size === undefined || r.size === null ? '—' : r.size + ' KB',
        date: dateFromFileName(r.fileName)
      };
    });

    return U.panel({
      title: '最近导出记录',
      sub: 'export_records',
      cls: 'is-tight',
      body: rows.length
        ? U.exportRows(rows)
        : '<div class="empty-row">暂无导出记录</div>'
    });
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

  $(function () {
    ZFZ.layout.mount('archive');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '台风档案检索',
      desc: '按年份 / 名称 / 强度 / 海域检索历史台风档案'
    }));

    var loading = '<div class="empty-row">加载中…</div>';

    /* 1) 先渲染「加载中」骨架，避免白屏 */
    $('#page-body').html(
      '<div class="colstack col-main">' +
        U.panel({ title: '检索条件', sub: '支持模糊匹配与多条件组合查询', cls: 'is-tight', body: loading }) +
        U.panel({ title: '档案结果', cls: 'is-tight', body: loading }) +
      '</div>' +
      '<div class="colstack col-side">' +
        U.panel({ title: '档案统计', cls: 'is-tight', body: loading }) +
        U.panel({ title: '最近导出记录', sub: 'export_records', cls: 'is-tight',
                  body: '<div id="export-rows-host">' + loading + '</div>' }) +
        U.panel({ title: '峰值强度分布', cls: 'is-tight', body: loading }) +
      '</div>'
    );

    /**
     * 拉取并渲染「最近导出记录」。
     * 该接口需要登录态：失败时只让这一张卡片进入错误态并弹 toast，
     * 不影响主列的档案列表（仍然如实报错，不做静默降级）。
     * @returns {void}
     */
    function loadExports() {
      ZFZ.api.getExportHistory()
        .then(function (records) {
          $('#export-rows-host').html(exportCard(records));
        })
        .catch(function (err) {
          $('#export-rows-host').html(
            '<div class="empty-row">导出记录加载失败：' + U.esc(err.message) + '</div>'
          );
          ZFZ.layout.toast('导出记录加载失败：' + err.message);
        });
    }

    /**
     * 渲染整页（首次加载成功后调用）。
     * @param {Object} meta 筛选元数据
     * @param {Array<Object>} list 完整档案列表
     * @returns {void}
     */
    function paint(meta, list) {
      state.meta = meta;
      state.full = list;
      state.list = list;

      $('#page-body').html(
        '<div class="colstack col-main">' +
          queryCard(meta, list) +
          resultCard(list, resultSub(list.length, '')) +
        '</div>' +
        '<div class="colstack col-side">' +
          statCard(list, meta) +
          U.panel({ title: '最近导出记录', sub: 'export_records', cls: 'is-tight',
                    body: '<div id="export-rows-host">' + loading + '</div>' }) +
          distCard(list) +
        '</div>'
      );

      bind();
      loadExports();
    }

    /**
     * 按检索条件查询档案，并只刷新结果表（侧列统计始终对应全部档案）。
     * @param {string} keyword 检索关键词，空串表示查全部
     * @returns {void}
     */
    function query(keyword) {
      var params = $.extend({}, DEFAULT_PARAMS);
      if (keyword) { params.keyword = keyword; }

      ZFZ.api.getTyphoons(params)
        .then(function (list) {
          var arr = list || [];
          state.list = arr;
          $('#archive-rows').html(arr.length
            ? rowsHtml(arr)
            : '<div class="empty-row">没有匹配的档案，请调整检索条件。</div>');
          $('#result-sub').text(resultSub(arr.length, keyword));
        })
        .catch(function (err) {
          $('#archive-rows').html(
            '<div class="empty-row">查询失败：' + U.esc(err.message) +
            '<br>请确认后端服务已启动后重试</div>'
          );
          ZFZ.layout.toast('查询失败：' + err.message);
        });
    }

    /**
     * 绑定查询 / 重置 / 导出等交互。
     * 每次重渲染 #page-body 后需要重新绑定（旧节点已随 DOM 移除）。
     * @returns {void}
     */
    function bind() {
      $('#btn-query').on('click', function () {
        query($.trim($('#q-name').val()));
      });

      $('#btn-reset').on('click', function () {
        $('#q-name').val('');
        /* 重置即为「查全部」，重新走一次接口拿最新档案 */
        query('');
      });

      $('#q-name').on('keydown', function (e) {
        if (e.key === 'Enter') { $('#btn-query').trigger('click'); }
      });

      /**
       * 触发一次轨迹导出：先调接口登记导出记录，再触发浏览器下载。
       * @param {string} format 导出格式：csv（逗号分隔） | xlsx（Excel，手写 OOXML）
       * @returns {void}
       */
      function doExport(format) {
        /* 导出当前结果表的第一条档案（默认按编号倒序，即最新一个台风） */
        var t = state.list[0];
        if (!t) {
          ZFZ.layout.toast('当前没有可导出的档案，请先调整检索条件');
          return;
        }
        ZFZ.api.exportTracks({ typhoonId: t.id, format: format })
          .then(function (res) {
            var info = res || {};
            ZFZ.layout.toast('导出已创建：' + (info.fileName || format.toUpperCase()) +
              (info.rows === undefined || info.rows === null ? '' : '（' + info.rows + ' 行）'));
            /* 再触发浏览器下载（响应为文件流，不能走 ajax） */
            ZFZ.api.downloadTracks({ typhoonId: t.id, format: format });
            loadExports();
          })
          .catch(function (err) {
            ZFZ.layout.toast('导出失败：' + err.message);
          });
      }

      $('#btn-export-csv').on('click', function () { doExport('csv'); });
      $('#btn-export-xlsx').on('click', function () { doExport('xlsx'); });
    }

    /* 2) 拉数据：档案列表 + 筛选元数据并发 */
    Promise.all([
      ZFZ.api.getTyphoons($.extend({}, DEFAULT_PARAMS)),
      ZFZ.api.getTyphoonMeta()
    ]).then(function (res) {
      paint(res[1], res[0] || []);
    }).catch(function (err) {
      $('#page-body').html(
        '<div class="colstack col-main">' + errorPanel('台风档案检索', err.message) + '</div>'
      );
      ZFZ.layout.toast('加载失败：' + err.message);
    });
  });
})(window.jQuery);
