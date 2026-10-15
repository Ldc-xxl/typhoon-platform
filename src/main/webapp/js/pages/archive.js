/* ==========================================================================
   P8 台风档案检索
   设计稿节点 3:1177（1440×1000）
   主列：检索条件 / 档案结果
   侧列 340：档案统计 / 最近导出记录 / 峰值强度分布
   --------------------------------------------------------------------------
   本页数据全部来自 ZFZ.api（改造规范见 docs/frontend-integration-guide.md）：
     档案列表      ZFZ.api.getTyphoonsPage(params)  GET /api/typhoons（items + total 分页）
     全量快照      ZFZ.api.getTyphoonsPage({limit}) 同接口，仅供侧列统计
     筛选元数据    ZFZ.api.getTyphoonMeta()         GET /api/typhoons/meta
     最近导出记录  ZFZ.api.getExportHistory()       GET /api/export/history（需登录）
     导出 CSV/Excel ZFZ.api.exportTracks({format})   GET /api/export/tracks（先取信息）
                   + ZFZ.api.downloadTracks()       同接口 download=1（触发浏览器下载）
                   format 取值 csv | xlsx，对应「导出 CSV」与「导出 Excel」两个按钮
   接口失败一律渲染错误态并弹 toast，不回退到 js/data.js 的演示数据。

   分页说明：结果表走**服务端分页**（每次翻页只取一页），总数来自响应体里的 total。
   侧列的「档案统计 / 峰值强度分布」按设计稿是"全部档案"口径，且不随关键词变化，
   因此启动时另取一份大 limit 的全量快照供其使用——见 SNAPSHOT_LIMIT 的注释。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /**
   * 结果表每页条数。真正的服务端分页：每次翻页只向后端要这一页。
   * @type {number}
   */
  var PAGE_SIZE = 10;

  /**
   * 侧列统计用的「全量快照」条数。
   *
   * 侧列的「档案统计 / 峰值强度分布」按设计稿是<b>全部档案</b>的口径，不随关键词变化；
   * 而结果表已经分页（一次只有 PAGE_SIZE 条），拿当前页去统计会把"档案总数"算成 10。
   * 因此启动时另拉一份大 limit 的快照专供侧列。
   *
   * 后端 limit 上限为 500，这里取满；档案超过 500 条时侧列会退化成
   * 「前 500 条」的统计。届时正确做法是让后端出一个聚合接口，
   * 而不是把 limit 继续调大——当前演示数据量下不构成问题，但不能装作没这回事。
   * @type {number}
   */
  var SNAPSHOT_LIMIT = 500;

  /**
   * 全量快照的检索参数：按编号倒序（与设计稿「按编号倒序」一致）。
   * @type {Object}
   */
  var SNAPSHOT_PARAMS = { limit: SNAPSHOT_LIMIT, sortBy: 'code', order: 'desc' };

  /**
   * 结果表的排序参数。与快照保持同序，翻页时行序才稳定，
   * 否则会出现"第 2 页的内容看起来和第 1 页重了"这种由排序不稳定造成的错觉。
   * @type {Object}
   */
  var TABLE_PARAMS = { sortBy: 'code', order: 'desc' };

  /**
   * 页面运行时状态。
   * @typedef {Object} ArchiveState
   * @property {Array<Object>} full     启动时拉取的全量快照，侧列统计以其为准
   * @property {Array<Object>} list     当前页的档案，导出按钮据此取主对象
   * @property {Object|null}   meta     筛选元数据
   * @property {Object}        filters  当前生效的检索条件（见 {@link emptyFilters}）
   * @property {number}        total    后端返回的匹配总数
   * @property {number}        page     当前页码（从 1 开始）
   * @property {number}        pages    总页数
   */
  var state = { full: [], list: [], meta: null, filters: null, total: 0, page: 1, pages: 0 };

  /**
   * 峰值强度分档。value 直接作为后端的 {@code minWindForce}（下限，含），
   * 采用国内风力等级口径：8 级热带风暴 / 10 级强热带风暴 / 12 级台风 /
   * 14 级强台风 / 16 级超强台风。
   * @type {Array<{value: string, label: string}>}
   */
  var LEVEL_OPTIONS = [
    { value: '',   label: '全部等级' },
    { value: '8',  label: '≥ 8 级 热带风暴' },
    { value: '10', label: '≥ 10 级 强热带风暴' },
    { value: '12', label: '≥ 12 级 台风' },
    { value: '14', label: '≥ 14 级 强台风' },
    { value: '16', label: '≥ 16 级 超强台风' }
  ];

  /**
   * 状态取值 → 页面文案。接口的 status 是 ACTIVE / ARCHIVED / FINISHED，
   * 直接摆给用户看太生硬（且 FINISHED 与 ARCHIVED 在业务上是同一件事）。
   * @type {Object<string, string>}
   */
  var STATUS_LABELS = { ACTIVE: '活跃中', ARCHIVED: '已结束', FINISHED: '已结束' };

  /**
   * 空条件（「全部…」状态）。
   * @returns {Object} 各字段均为空串的检索条件
   */
  function emptyFilters() {
    return { keyword: '', year: '', minWindForce: '', basin: '', status: '' };
  }

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
  var num = U.num;

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
  var fmtTime = U.fmtTime;

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
   *
   * <p>五个字段全部是<b>真实可用</b>的检索项，可任意组合：
   * <ul>
   *   <li>台风名称 → 后端 {@code keyword}（中英文名 + 国际编号模糊匹配）；</li>
   *   <li>年份 → {@code year}；</li>
   *   <li>峰值强度 → {@code minWindForce}（下限，含）；</li>
   *   <li>生成海域 → {@code basin}；</li>
   *   <li>状态 → {@code status}。</li>
   * </ul>
   * 各下拉的第一项（「全部…」）对应空值，后端会跳过该条件、不参与 SQL——
   * 这正是"任意组合筛选"的前提，因此不做"全部"以外的默认值。
   * 选项来自 {@code GET /api/typhoons/meta}，不在前端写死年份与海域清单。
   *
   * @param {Object} meta 筛选元数据 { years, basins, statuses }
   * @returns {string} 卡片 HTML
   */
  function queryCard(meta) {
    var years = (meta && meta.years) || [];
    var basins = (meta && meta.basins) || [];
    var statuses = (meta && meta.statuses) || [];

    var yearOpts = [{ value: '', label: '全部年份' }].concat(years.map(function (y) {
      return { value: y, label: y + ' 年' };
    }));
    var basinOpts = [{ value: '', label: '全部海域' }].concat(basins.map(function (b) {
      return { value: b, label: b };
    }));
    var statusOpts = [{ value: '', label: '全部状态' }].concat(statuses.map(function (s) {
      return { value: s, label: STATUS_LABELS[s] || s };
    }));

    var fields =
      U.fieldSelect('年份', 'q-year', yearOpts) +
      '<div class="field-box"><span class="field-box-label">台风名称</span>' +
        '<input class="field-input" id="q-name" type="text" placeholder="支持中英文模糊匹配"></div>' +
      U.fieldSelect('峰值强度', 'q-level', LEVEL_OPTIONS) +
      U.fieldSelect('生成海域', 'q-basin', basinOpts) +
      U.fieldSelect('状态', 'q-status', statusOpts);

    var buttons =
      '<div class="zbtn-row is-inline">' +
        '<button class="zbtn is-primary" type="button" id="btn-query">查询档案</button>' +
        '<button class="zbtn" type="button" id="btn-reset">重置条件</button>' +
        '<button class="zbtn" type="button" id="btn-export-csv">导出 CSV</button>' +
        '<button class="zbtn" type="button" id="btn-export-xlsx">导出 Excel</button>' +
      '</div>';

    return U.panel({
      title: '检索条件',
      sub: '年份 / 强度 / 海域 / 状态可任意组合，名称支持中英文模糊匹配',
      cls: 'is-tight',
      body: '<div class="query-form"><div class="query-fields">' + fields + '</div>' + buttons + '</div>'
    });
  }

  /**
   * 把当前检索条件读成一句话，用于结果卡片的副标题。
   * @param {Object} f 检索条件
   * @returns {string} 例：'2026 年 · 峰值 ≥ 12 级 · 西北太平洋'；无条件时返回空串
   */
  function filterSummary(f) {
    if (!f) { return ''; }
    var parts = [];
    if (f.keyword) { parts.push('名称含「' + f.keyword + '」'); }
    if (f.year) { parts.push(f.year + ' 年'); }
    if (f.minWindForce) { parts.push('峰值 ≥ ' + f.minWindForce + ' 级'); }
    if (f.basin) { parts.push(f.basin); }
    if (f.status) { parts.push(STATUS_LABELS[f.status] || f.status); }
    return parts.join(' · ');
  }

  /**
   * 档案结果表的行 HTML。
   * @param {Array<Object>} list 台风数组
   * @returns {string} 连续 .trow 组成的 HTML
   */
  function rowsHtml(list) {
    return list.map(function (t, i) {
      var active = t.status === 'ACTIVE';
      var name = t.nameCn + ' ' + t.nameEn;

      /* 「查看详情」要能跳到该台风的详情页，详情页靠 ?id= 取对象（见 detail.js 的 currentId）。
         该主键字段是 TyphoonVO 的 id（后端由 typhoon.getTyphoonId() 装配），也是本页
         导出按钮已在使用的主键（下方 doExport 里的 t.id）。 */
      /* 退化保护：id 取不到时**不拼**指向 id=undefined 的链接。detail.js#currentId 对
         取不到的 id 会静默回退成默认台风 13，那会变成"看着能点、点开却跳到另一条档案"的
         隐蔽错误，比按钮不能点更糟。这种情况保持原样的纯文本，并用 title 说明原因。 */
      var hasId = t.id !== undefined && t.id !== null && String(t.id) !== '';
      var opHtml = hasId
        ? '<a class="state-op" href="typhoon-detail.html?id=' + encodeURIComponent(t.id) + '"' +
            ' title="查看 ' + U.esc(name) + ' 的详情">· 查看详情</a>'
        : '<i class="state-op" title="该记录缺少标识，无法跳转">· 查看详情</i>';

      return '<div class="trow' + (active ? ' is-active' : (i % 2 === 0 ? ' is-alt' : '')) + '">' +
        '<span class="cell-num">' + U.esc(t.code) + '</span>' +
        '<span class="cell-strong">' + U.esc(t.nameCn + ' ' + t.nameEn) + '</span>' +
        '<span class="cell-num">' + U.esc(spanText(t)) + '</span>' +
        '<span class="cell-num" style="color:' + levelColor(t.maxWindForce || t.windLevel) + '">' +
          U.esc(peakText(t)) + '</span>' +
        '<span class="state-cell"><i class="state-txt" style="color:' +
          (active ? '#E8833A' : 'var(--ink-2)') + '">' +
          U.esc(t.statusText || (active ? '活跃中' : '已结束')) + '</i>' +
          opHtml + '</span>' +
        '</div>';
    }).join('');
  }

  /**
   * 档案结果卡片的说明文案。
   * 总数来自响应体里的 `total`（原先只在 X-Total-Count 响应头里，页面拿不到，
   * 只能含糊地写「已加载 N 条」，实际那是"当前页条数"）。
   * @param {number} count 当前页条数
   * @param {Object} filters 当前生效的检索条件（空条件时按「共 N 条」表述）
   * @param {number} total 匹配总数
   * @param {number} page 当前页码
   * @param {number} pages 总页数
   * @returns {string} 例：'筛选「2026 年 · 峰值 ≥ 12 级」共 13 条 · 第 1 / 2 页 · 本页 10 条 · 按编号倒序'
   */
  function resultSub(count, filters, total, page, pages) {
    var s = filterSummary(filters);
    var scope = s ? '筛选「' + s + '」共 ' : '共 ';
    var pageText = total > 0 ? '第 ' + page + ' / ' + Math.max(pages, 1) + ' 页' : '无结果';
    return scope + total + ' 条 · ' + pageText + ' · 本页 ' + count + ' 条 · 按编号倒序';
  }

  /**
   * 结果表的分页条。
   *
   * 只有一页时也把按钮画出来（禁用态），而不是让按钮消失：
   * 凭空出现/消失的控件会让人以为页面坏了，灰掉的按钮至少说明"就只有这一页"。
   * @param {number} page 当前页码
   * @param {number} pages 总页数
   * @returns {string} 分页条 HTML
   */
  function pagerHtml(page, pages) {
    var last = Math.max(pages, 1);
    return '<div class="zbtn-row is-inline" id="archive-pager">' +
        '<button class="zbtn is-outline is-sm" type="button" id="btn-prev-page"' +
          (page <= 1 ? ' disabled' : '') + '>上一页</button>' +
        '<span class="panel-sub" id="page-indicator">第 ' + page + ' / ' + last + ' 页</span>' +
        '<button class="zbtn is-outline is-sm" type="button" id="btn-next-page"' +
          (page >= last ? ' disabled' : '') + '>下一页</button>' +
      '</div>';
  }

  /**
   * 档案结果卡片。
   * @param {Array<Object>} list 当前页的台风数组
   * @param {string} sub 标题右侧说明
   * @param {number} page 当前页码
   * @param {number} pages 总页数
   * @returns {string} 卡片 HTML
   */
  function resultCard(list, sub, page, pages) {
    var rows = list.length
      ? rowsHtml(list)
      : U.emptyRow('没有匹配的档案，请调整检索条件。');

    return '<section class="panel is-tight">' +
      '<div class="panel-head"><span class="panel-title">档案结果</span>' +
        '<span class="panel-sub" id="result-sub">' + U.esc(sub) + '</span></div>' +
      '<div class="tbl" style="--cols:104px 186px 240px 168px minmax(0,1fr)">' +
        '<div class="tbl-head"><span>编号</span><span>台风名称</span><span>生成 → 结束时间</span>' +
        '<span>峰值强度</span><span>状态 / 操作</span></div>' +
        '<div id="archive-rows" class="tbl-rows">' + rows + '</div>' +
      '</div>' + pagerHtml(page, pages) +
      '</section>';
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
        body: U.emptyRow('暂无档案数据')
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
        : U.emptyRow('暂无导出记录')
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
  var errorPanel = U.errorPanel;

  /* ======================================================================== */
  /*  五、渲染与交互                                                          */
  /* ======================================================================== */

  $(function () {
    ZFZ.layout.mount('archive');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '台风档案检索',
      desc: '按年份 / 名称 / 强度 / 海域 / 状态检索历史台风档案'
    }));

    var loading = U.loadingRow();

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
            U.emptyRow('导出记录加载失败：' + err.message)
          );
          ZFZ.layout.toast('导出记录加载失败：' + err.message);
        });
    }

    /**
     * 渲染整页（首次加载成功后调用）。
     * @param {Object} meta 筛选元数据
     * @param {Array<Object>} snapshot 全量档案快照（侧列统计的唯一数据源）
     * @param {Object} firstPage 结果表第一页的响应 {items,total,page,pages}
     * @returns {void}
     */
    function paint(meta, snapshot, firstPage) {
      var items = (firstPage && firstPage.items) || [];

      state.meta = meta;
      state.full = snapshot || [];
      state.list = items;
      state.filters = emptyFilters();
      state.total = firstPage ? firstPage.total : 0;
      state.page = firstPage ? firstPage.page : 1;
      state.pages = firstPage ? firstPage.pages : 0;

      $('#page-body').html(
        '<div class="colstack col-main">' +
          queryCard(meta) +
          resultCard(items, resultSub(items.length, state.filters, state.total, state.page, state.pages),
                     state.page, state.pages) +
        '</div>' +
        '<div class="colstack col-side">' +
          statCard(snapshot, meta) +
          U.panel({ title: '最近导出记录', sub: 'export_records', cls: 'is-tight',
                    body: '<div id="export-rows-host">' + loading + '</div>' }) +
          distCard(snapshot) +
        '</div>'
      );

      bind();
      loadExports();
    }

    /**
     * 按检索条件 + 页码拉取结果表，并只刷新结果区（侧列统计始终对应全部档案快照）。
     *
     * <p>只把非空条件塞进请求参数：空串交给后端等于"该条件为下拉第一项（全部）"，
     * 但显式传空串会让 URL 上多出 `year=` 这样的噪声参数，也不利于排查。
     *
     * @param {Object} [filters] 检索条件，缺省沿用上一次的条件（翻页时用）
     * @param {number} [page]    目标页码，从 1 开始；非法值按第 1 页处理
     * @returns {void}
     */
    function query(filters, page) {
      var f = filters || state.filters || emptyFilters();
      var target = page && page > 0 ? page : 1;
      var params = $.extend({ limit: PAGE_SIZE, page: target }, TABLE_PARAMS);
      if (f.keyword) { params.keyword = f.keyword; }
      if (f.year) { params.year = f.year; }
      if (f.minWindForce) { params.minWindForce = f.minWindForce; }
      if (f.basin) { params.basin = f.basin; }
      if (f.status) { params.status = f.status; }

      ZFZ.api.getTyphoonsPage(params)
        .then(function (res) {
          state.list = res.items;
          state.filters = f;
          state.total = res.total;
          state.page = res.page;
          state.pages = res.pages;

          $('#archive-rows').html(res.items.length
            ? rowsHtml(res.items)
            : U.emptyRow('没有匹配的档案，请调整检索条件。'));
          $('#result-sub').text(resultSub(res.items.length, f,
                                          res.total, res.page, res.pages));
          /* 整条分页条一起换掉：按钮的禁用态与页码文案都随页码变 */
          $('#archive-pager').replaceWith(pagerHtml(res.page, res.pages));
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
     * 绑定查询 / 重置 / 翻页 / 导出等交互。
     * 每次重渲染 #page-body 后需要重新绑定（旧节点已随 DOM 移除）。
     * @returns {void}
     */
    function bind() {
      /**
       * 把表单里的五个检索项读成条件对象。
       * 下拉未选择时 val() 返回空串，正好等于「全部…」，无需额外判空。
       * @returns {Object} 检索条件
       */
      function readFilters() {
        return {
          keyword: $.trim($('#q-name').val() || ''),
          year: $('#q-year').val() || '',
          minWindForce: $('#q-level').val() || '',
          basin: $('#q-basin').val() || '',
          status: $('#q-status').val() || ''
        };
      }

      $('#btn-query').on('click', function () {
        /* 新条件一律回到第 1 页：停在第 3 页去查一个只有 1 页结果的条件组合，
           会直接看到"没有匹配的档案"，让人误以为条件没命中。 */
        query(readFilters(), 1);
      });

      $('#btn-reset').on('click', function () {
        $('#q-name').val('');
        /* 四个下拉一起回到第一项（全部）——只清名称会让"重置"名不副实 */
        $('#q-year, #q-level, #q-basin, #q-status').val('');
        /* 重置即为「查全部」，重新走一次接口拿最新档案 */
        query(emptyFilters(), 1);
      });

      $('#q-name').on('keydown', function (e) {
        if (e.key === 'Enter') { $('#btn-query').trigger('click'); }
      });

      /* 翻页：沿用当前条件，避免翻页后过滤条件"掉了"。
       *
       * 必须用事件委托挂到 #page-body 上，不能直接绑 #btn-prev-page / #btn-next-page：
       * 每次翻页都会用 replaceWith 整条重建分页条（按钮的禁用态与页码文案都要跟着变），
       * 直接绑在按钮上的处理器会随旧节点一起被丢掉 —— 表现是"只有第一次翻页生效"。
       * #page-body 自身不会被替换（paint() 用的是 .html()，只换子节点），委托挂在它身上才稳。 */
      $('#page-body')
        .off('click', '#btn-prev-page').on('click', '#btn-prev-page', function () {
          if (state.page > 1) { query(state.filters, state.page - 1); }
        })
        .off('click', '#btn-next-page').on('click', '#btn-next-page', function () {
          if (state.page < state.pages) { query(state.filters, state.page + 1); }
        });

      /**
       * 触发一次轨迹导出：先调接口登记导出记录，再触发浏览器下载。
       * @param {string} format 导出格式：csv（逗号分隔） | xlsx（Excel，手写 OOXML）
       * @returns {void}
       */
      function doExport(format) {
        /* 导出当前页的第一条档案（按编号倒序，即本页里编号最大的那个台风） */
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

    /* 2) 拉数据：全量快照（侧列统计） + 筛选元数据 + 结果表第一页，三者并发。
          后两个请求打的是同一个 /api/typhoons，但 limit 与用途不同：
          前者要「全部档案」的口径，后者要「当前这一页」。 */
    Promise.all([
      ZFZ.api.getTyphoonsPage($.extend({}, SNAPSHOT_PARAMS)),
      ZFZ.api.getTyphoonMeta(),
      ZFZ.api.getTyphoonsPage($.extend({ limit: PAGE_SIZE, page: 1 }, TABLE_PARAMS))
    ]).then(function (res) {
      paint(res[1], res[0].items || [], res[2]);
    }).catch(function (err) {
      $('#page-body').html(
        '<div class="colstack col-main">' + errorPanel('台风档案检索', err.message) + '</div>'
      );
      ZFZ.layout.toast('加载失败：' + err.message);
    });
  });
})(window.jQuery);
