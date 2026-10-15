/* ==========================================================================
   P4 城市风险预评估
   设计稿节点 3:338（1440×920）
   主列：评估条件（三个可切换下拉）/ 评估结果 / 分时段风险提示
   侧列 340：综合风险等级 / 距离测算 / 推算规则 / 免责
   --------------------------------------------------------------------------
   数据来源（全部走真实接口，失败即渲染错误态，不做 mock 兜底）：
     ZFZ.api.getTyphoons        取台风清单（活跃在前，历史档案在后）
     ZFZ.api.getCities          取城市清单（基础风险等级 / 常住人口）
     ZFZ.api.getTracks          取实测时次，作为「评估基准」下拉的选项
     ZFZ.api.getRiskAssessments 取城市风险预评估结果（可按 obsTime 重演）
     ZFZ.api.saveRiskAssessment 把当前口径的结果存档（需登录）

   --------------------------------------------------------------------------
   一、关于「评估基准」为什么是真的

     改造前页面顶部那个基准时间取的是**浏览器当前时间**（原 `nowText()`），
     而服务端推算 ETA 的起点其实是台风的**某个实测时刻**——两处口径不一致，
     页面每刷新一次那个时间就漂一次，而推算结果根本没变。

     现在基准时刻由服务端随结果回传（见 `RiskAssessmentVO.baseTime`），页面只负责
     显示；下拉的每一项就是一个真实观测时次（来自 `typhoon_tracks`），换时次即换
     一对「中心位置 + 风圈」重算。所有输入都是真实观测值，没有外推、没有造数。

   --------------------------------------------------------------------------
   二、为什么条件卡不参与重绘

     三个下拉选完即重算，但**只重绘结果区**。若连条件卡一起重建，用户刚点开的
     `<select>` 会被替换掉（焦点与展开状态丢失，看起来像"点了没反应"），
     而且事件绑定也得每次重做。因此：
       · 条件卡（`#risk-filter`）常驻，仅在**台风变化**（选项集合变了）时整体替换；
       · 改城市 / 改基准只把 select 的 value 与 option 原地同步（`U.fieldOptions`）。

   --------------------------------------------------------------------------
   三、为什么请求是"全量 + 偶发单城市"

     侧栏「距离测算 · 周边城市」天生要全部城市，所以每次按台风/基准取的是**全量**；
     主卡再从中筛出选中城市（零请求）。只有"选中的城市压根不在评估半径内、
     全量列表里没有它"时，才补一次单城市请求——这样主卡上那句"距离较远、暂不构成
     风险"始终是后端给的结论，而不是前端自己编的。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /* ---------------- 页面状态 ----------------
     三个下拉的取值集中放这里，而不是每次去读 DOM。
     "换台风"要连带重置基准时次并重拉时次列表，这种跨字段联动用一份显式 state
     表达最不容易漏；DOM 只作为展示层，在 render 末尾统一回填。 */
  var state = {
    typhoons: [],   // 全部台风（接口顺序：活跃在前）
    cities: [],     // 城市清单
    typhoonId: null,
    cityId: '',     // '' = 全部城市（主卡聚焦风险最高者）
    obsTime: '',    // '' = 跟随最新实测
    times: [],      // 当前台风的实测时次（降序，最新在前）
    target: null,   // analyzeTarget 结果，仅首次自动选台风时用于页头提示
    manual: false   // 用户是否手动动过下拉（动过就不再提示"已自动切换台风"）
  };

  /* ---------------- 通用小工具 ---------------- */

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
  var fixed1 = U.fixed1;

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
   * 后端 level 代码（LOW / MEDIUM / HIGH / EXTREME）→ 标签色调类。
   * @param {string} level 后端 level 代码
   * @returns {string} 标签类名
   */
  function levelTone(level) {
    if (level === 'HIGH' || level === 'EXTREME') { return 'is-sty'; }
    if (level === 'MEDIUM') { return 'is-warn'; }
    if (level === 'LOW') { return 'is-ok'; }
    return 'is-neutral';
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
   * 在数组里按 id 查找一条记录（统一转字符串比较，规避数字/字符串混用）。
   * @param {Array<Object>} list 记录数组
   * @param {string} key 作为 id 的字段名
   * @param {number|string} id 目标 id
   * @returns {Object|null} 命中项；未找到返回 null
   */
  function findById(list, key, id) {
    var hit = null;
    (list || []).forEach(function (it) {
      if (it && String(it[key]) === String(id)) { hit = it; }
    });
    return hit;
  }

  /* ======================================================================
     一、评估条件：三个可操作下拉
     ====================================================================== */

  /**
   * 「关联台风」选项：活跃台风在前、历史档案在后，各成一组。
   *
   * <p>历史台风也放进来（而不是只给活跃台风），因为回溯复盘本来就要对老台风做
   * 风险评估；代价是远海台风在 800km 评估半径内可能没有城市，结果合法地为空——
   * 这种情况由结果区的空态说明，**不偷偷换一个台风**。
   *
   * <p>**必须自己排序归拢，不能依赖接口顺序**：`fieldSelect` 是"遇到 group 变化就
   * 开一个新 `<optgroup>`"，若按接口原顺序直接 push，活跃与历史交错返回时就会被
   * 切成好几段同名分组（实测出现过 5 段交替）。因此这里先分成两桶再拼接，
   * 保证恒定产出 2 个 optgroup。
   *
   * @returns {Array<Object>} 选项数组
   */
  function typhoonOptions() {
    var actives = [], archives = [];
    state.typhoons.forEach(function (t) {
      (t.status === 'ACTIVE' ? actives : archives).push(t);
    });

    return actives.map(function (t) {
      return { value: t.id, group: '活跃台风', label: t.nameCn + ' ' + t.code };
    }).concat(archives.map(function (t) {
      return {
        value: t.id, group: '历史档案',
        label: t.nameCn + ' ' + t.code + '（' + t.year + ' · ' + (t.statusText || '已归档') + '）'
      };
    }));
  }

  /**
   * 「城市」选项：首项为「全部城市」，其余为全部城市（含省名便于区分重名）。
   * @returns {Array<Object>} 选项数组
   */
  function cityOptions() {
    return [{ value: '', label: '全部城市（按风险排序）' }].concat(
      state.cities.map(function (c) {
        return { value: c.id, label: c.name + '（' + c.province + '）' };
      }));
  }

  /**
   * 「评估基准」选项：首项表示跟随最新实测，其余为该台风的历次实测时次（降序）。
   * @returns {Array<Object>} 选项数组
   */
  function baseTimeOptions() {
    return [{ value: '', label: '跟随最新实测' }].concat(
      state.times.map(function (t) {
        return { value: t.time, label: shortTime(t.time) };
      }));
  }

  /**
   * 顶部评估条件卡（三个下拉 + 存档按钮）。
   *
   * <p>id 固定为 `risk-filter`：重绘时靠它定位"要不要重建整张卡"，
   * 结果区则用 `$('#risk-filter').nextAll()` 界定，两者是配套的。
   *
   * @returns {string} 卡片 HTML
   */
  function conditionCard() {
    var fields =
      U.fieldSelect('关联台风', 'q-typhoon', typhoonOptions(), state.typhoonId) +
      U.fieldSelect('城市', 'q-city', cityOptions(), state.cityId) +
      U.fieldSelect('评估基准', 'q-basetime', baseTimeOptions(), state.obsTime);

    var buttons =
      '<div class="zbtn-row is-inline">' +
        '<button class="zbtn is-primary" type="button" id="btn-assess">存档本次评估</button>' +
      '</div>';

    return U.panel({
      id: 'risk-filter',
      title: '评估条件',
      sub: '切换台风 / 城市 / 评估基准后立即重算',
      cls: 'is-tight',
      body: '<div class="query-form"><div class="query-fields">' + fields + '</div>' + buttons + '</div>'
    });
  }

  /**
   * 只换基准下拉的选项，不重建 select 本体（保住焦点与已绑定的事件）。
   * @returns {void}
   */
  function syncBaseTimeOptions() {
    var $s = $('#q-basetime');
    if ($s.length) { $s.html(U.fieldOptions(baseTimeOptions(), state.obsTime)); }
  }

  /** 把 state 回填到三个下拉上（幂等，不会打断用户操作） */
  function syncFilterValues() {
    $('#q-typhoon').val(state.typhoonId === null || state.typhoonId === undefined
      ? '' : String(state.typhoonId));
    $('#q-city').val(String(state.cityId));
    $('#q-basetime').val(String(state.obsTime));
  }

  /* ======================================================================
     二、评估结果卡片
     ====================================================================== */

  /**
   * 评估结果卡片：四个关键指标 + 建议文案。
   * @param {Object|null} a 单条风险评估记录（/api/risk/assessments）
   * @param {string} baseTime 服务端实际使用的基准时刻
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
      // 与 ETA / 时段 / 下拉选项同为 shortTime 短格式：后端回传的是
      // 'YYYY-MM-DD HH:mm:ss'，直接拼接会让这一页出现两种时间写法。
      sub: '基准时刻 ' + shortTime(baseTime) + ' · 基于后端风险预评估结果',
      cls: 'is-tight',
      body: body
    });
  }

  /* ======================================================================
     三、分时段风险提示
     ====================================================================== */

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

  /* ======================================================================
     四、侧列
     ====================================================================== */

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
   * @param {Object|null} focus 主卡当前聚焦的那条（用于在副标题里指出来）
   * @returns {string} HTML
   */
  function distanceCard(list, focus) {
    var items = (list || []).map(function (a) {
      return {
        city: a.cityName,
        province: a.province,
        distance: fixed1(a.distance),
        risk: a.riskLevel + '风险'
      };
    });

    var sub = '可在地图点击任意点或选择城市，测算与台风中心的直线距离。';
    if (focus && focus.cityName) {
      sub = '主卡当前聚焦「' + focus.cityName + '」；下表为评估半径内的全部城市。';
    }

    return U.panel({
      title: '距离测算 · 周边城市',
      sub: sub,
      cls: 'is-tight',
      body: items.length ? U.distRows(items) : emptyRow('评估半径内暂无城市')
    });
  }

  /** 推算规则说明（口径说明，与数据源无关，保持原样） */
  function ruleCard() {
    var items = [
      { k: '距离：', v: '台风中心与城市经纬度的球面直线距离' },
      { k: 'ETA：',  v: '按当前移动速度线性推算，未考虑路径转向与强度突变' },
      { k: '时段：', v: '7 级风圈预计覆盖该城市的时间区间' },
      { k: '基准：', v: '中心位置与风圈均取所选实测时次，可切换时次重演' }
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

  /* ======================================================================
     五、渲染与加载
     ====================================================================== */

  /**
   * 建出页面骨架（条件卡 + 两个结果槽位）。
   *
   * <p>条件卡与结果面板都是 `.colstack` 的**直接子元素**，中间不额外包一层 div——
   * 列内间距规则用的是相邻兄弟选择器，多包一层会让间距全部失效。
   *
   * @returns {void}
   */
  function ensureShell() {
    if ($('#risk-filter').length) { return; }
    $('#page-body').html(
      '<div class="colstack col-main">' + conditionCard() + '</div>' +
      '<div class="colstack col-side is-narrow"></div>'
    );
    bindFilter();
  }

  /** 结果区加载态：只清条件卡之后的内容，条件卡与用户的选择原样保留 */
  function showLoading() {
    $('#risk-filter').nextAll().remove();
    $('.col-main').append(U.panel({ title: '评估结果', cls: 'is-tight', body: emptyRow('加载中…') }));
    $('.col-side').html(U.panel({ title: '综合风险等级', cls: 'is-tight', body: emptyRow('加载中…') }));
  }

  /** 结果区错误态 */
  function showError(err) {
    var msg = (err && err.message) || '未知错误';
    $('#risk-filter').nextAll().remove();
    $('.col-main').append(errorPanel('评估结果', msg));
    $('.col-side').html(errorPanel('综合风险等级', msg));
  }

  /**
   * 用一批真实数据重绘结果区。
   *
   * @param {Array<Object>} all 评估半径内的全部城市评估（侧栏「距离测算」用）
   * @param {Object|null} focus 主卡要展示的那一条（null 表示没有可展示的城市）
   * @returns {void}
   */
  function render(all, focus) {
    ensureShell();

    var assessments = all || [];
    var baseTime = (focus && focus.baseTime) || (assessments[0] && assessments[0].baseTime) || '—';
    var city = focus ? findById(state.cities, 'id', focus.cityId) : null;
    var disclaimer = (focus && focus.disclaimer) ||
      '风险评估仅基于公开预报数据做简易规则推算，实际预警请以当地气象部门与应急管理部门发布的官方信息为准。';

    // 单城市下拉的选项是全局城市清单，与台风无关，故无需重建；
    // 基准下拉的选项随台风变，换掉 option 即可（select 本体不重建）。
    syncBaseTimeOptions();
    syncFilterValues();

    var emptyHint = state.cityId
      ? '所选城市在当前台风 / 基准下没有评估结果，可换一个城市或时次'
      : '评估半径内没有城市落入风圈，本节结果为空';

    $('#risk-filter').nextAll().remove();
    $('.col-main').append(
      U.switchNote(state.manual ? null : state.target, emptyHint) +
      resultCard(focus, baseTime) +
      slotsCard(focus)
    );
    $('.col-side').html(
      riskLevelCard(focus, city) +
      distanceCard(assessments, focus) +
      ruleCard() +
      U.note(U.esc(disclaimer))
    );
  }

  /* ======================================================================
     六、交互
     ====================================================================== */

  /** 绑定条件卡上的三个下拉与存档按钮（条件卡重建后需重新调用） */
  function bindFilter() {
    $('#q-typhoon').off('change').on('change', onTyphoonChange);
    $('#q-city').off('change').on('change', onCityChange);
    $('#q-basetime').off('change').on('change', onBaseTimeChange);
    $('#btn-assess').off('click').on('click', onSave);
  }

  /** 换台风：基准时次列表随之改变，故重置为「跟随最新」；城市清单与台风无关，保留 */
  function onTyphoonChange() {
    var v = $('#q-typhoon').val();
    state.typhoonId = (v === '' || v === null) ? null : Number(v);
    state.obsTime = '';
    state.manual = true;
    // 台风变了 → 基准下拉的选项集合也变了，整卡重建一次（此刻用户已离开该控件）
    var $f = $('#risk-filter');
    if ($f.length) { $f.replaceWith(conditionCard()); bindFilter(); }
    loadTimes(state.typhoonId).then(function () {
      syncBaseTimeOptions();
      return refresh();
    });
  }

  /** 换城市：全量数据已在手，命中即本地切换；半径外才补一次单城市请求 */
  function onCityChange() {
    state.cityId = $('#q-city').val() || '';
    state.manual = true;
    refresh();
  }

  /** 换基准：换一对「中心位置 + 风圈」重算，必须重新请求 */
  function onBaseTimeChange() {
    state.obsTime = $('#q-basetime').val() || '';
    state.manual = true;
    refresh();
  }

  /** 存档本次评估（需登录；未登录时后端返回 401） */
  function onSave() {
    if (state.typhoonId === null) {
      ZFZ.layout.toast('请先选择关联台风');
      return;
    }
    var $btn = $('#btn-assess');
    if ($btn.prop('disabled')) { return; }
    $btn.prop('disabled', true).text('存档中…');

    ZFZ.api.saveRiskAssessment(state.typhoonId, state.obsTime || null).then(function (rows) {
      var n = Array.isArray(rows) ? rows.length : 0;
      ZFZ.layout.toast('已存档 ' + n + ' 条城市评估（' + shortTime(baseTimeOf(rows)) + ' 基准）');
    }, function (err) {
      // 后端业务失败恒为 HTTP 200，未登录必须看 err.code（不能看状态码）
      if (ZFZ.layout.isUnauthorized(err)) {
        ZFZ.layout.toast('存档需要先登录（登录入口在「管理后台」页）');
      } else {
        ZFZ.layout.toast('存档失败：' + ((err && err.message) || '未知错误'));
      }
    }).then(function () {
      $btn.prop('disabled', false).text('存档本次评估');
    });
  }

  /**
   * 从接口返回里取基准时刻（用于 toast 文案）。
   * @param {Array<Object>|Object} rows 接口返回
   * @returns {string} 基准时刻；取不到返回空串
   */
  function baseTimeOf(rows) {
    if (Array.isArray(rows)) { return (rows[0] && rows[0].baseTime) || ''; }
    return (rows && rows.baseTime) || '';
  }

  /* ======================================================================
     七、数据加载
     ====================================================================== */

  /**
   * 拉取某台风的实测时次（作为基准下拉的选项）。
   *
   * <p>失败不视为致命：即使一个时次都取不到，基准下拉仍有「跟随最新实测」一项，
   * 全页照常可用——把整个评估页因为"时次列表没取到"而打成错误态是不合适的。
   *
   * @param {number|string|null} id 台风 id
   * @returns {Promise<void>}
   */
  function loadTimes(id) {
    if (id === null || id === undefined) {
      state.times = [];
      return Promise.resolve();
    }
    return ZFZ.api.getTracks(id).then(function (list) {
      // 接口按观测时间升序返回，下拉要"最新在前"故整体反转
      state.times = (list || []).slice().reverse();
    }, function () {
      state.times = [];
    });
  }

  /**
   * 重算并重绘结果区。
   *
   * <p>先取**全量**（侧栏距离测算要用），再在本地筛出主卡聚焦项；
   * 只有选中城市不在全量列表里（超出评估半径）时才补一次单城市请求。
   *
   * @returns {Promise<void>}
   */
  function refresh() {
    var id = state.typhoonId;
    if (id === null) {
      render([], null);
      return Promise.resolve();
    }

    var opts = state.obsTime ? { obsTime: state.obsTime } : null;

    return ZFZ.api.getRiskAssessments(id, opts).then(function (rows) {
      var all = Array.isArray(rows) ? rows : (rows ? [rows] : []);
      if (!state.cityId) {
        return { all: all, focus: all.length ? all[0] : null };
      }
      var hit = findById(all, 'cityId', state.cityId);
      if (hit) { return { all: all, focus: hit }; }

      // 超出评估半径：后端 assessCity 会返回一条"距离较远、暂不构成风险"的结论，
      // 拿它比前端自己编一句更可信，也保证了全站口径只有一个。
      var one = $.extend({}, opts || {}, { cityId: state.cityId });
      return ZFZ.api.getRiskAssessments(id, one).then(function (vo) {
        return { all: all, focus: vo || null };
      });
    }).then(function (res) {
      render(res.all, res.focus);
    }, function (err) {
      showError(err);
    });
  }

  /**
   * 首次加载：取台风清单 → 挑一个"算得出来"的台风（沿用 analyzeTarget 的回落逻辑，
   * 避免一进页面就是空结果）→ 取城市清单与实测时次 → 渲染。
   *
   * <p>**只在首次自动选**：一旦用户动过任一下拉，就以他的选择为准，
   * 不再自动改选（`state.manual`）。
   *
   * @returns {void}
   */
  function bootstrap() {
    $('#page-body').html(
      '<div class="colstack col-main">' +
        U.panel({ title: '评估结果', cls: 'is-tight', body: emptyRow('加载中…') }) +
      '</div>' +
      '<div class="colstack col-side is-narrow">' +
        U.panel({ title: '综合风险等级', cls: 'is-tight', body: emptyRow('加载中…') }) +
      '</div>'
    );

    ZFZ.api.getTyphoons({ limit: 200 }).then(function (list) {
      var typhoons = list || [];
      state.typhoons = typhoons;
      var actives = typhoons.filter(function (t) { return t.status === 'ACTIVE'; });
      var fallback = actives[0] || typhoons[0] || null;

      return ZFZ.api.analyzeTarget({
        base: fallback,
        actives: actives.length ? actives : (fallback ? [fallback] : []),
        probe: function (id) {
          return ZFZ.api.getRiskAssessments(id).then(function (rows) {
            return Array.isArray(rows) ? rows.length > 0 : !!rows;
          }, function () { return false; });
        }
      }).then(function (target) {
        state.target = target;
        var active = target.typhoon || fallback;
        state.typhoonId = active && active.id != null ? active.id : null;
        state.cityId = '';
        state.obsTime = '';
        state.manual = false;

        return Promise.all([ZFZ.api.getCities(), loadTimes(state.typhoonId)]).then(function (res) {
          state.cities = res[0] || [];
        });
      });
    }).then(function () {
      return refresh();
    }).catch(function (err) {
      showError(err);
      ZFZ.layout.toast('加载失败：' + ((err && err.message) || '未知错误'));
    });
  }

  $(function () {
    ZFZ.layout.mount('risk');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '城市风险预评估', center: true,
      desc: '可切换关联台风、城市与评估基准，基于预报路径推算城市距离、预计到达时间与受影响时段'
    }));

    bootstrap();
  });
})(window.jQuery);
