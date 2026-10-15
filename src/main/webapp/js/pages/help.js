/* ==========================================================================
   P9 帮助与接口说明
   设计稿节点 3:1351（1440×1240）
   主列：多机构数据来源 / RESTful 接口清单
   侧列 340：平台本土测算机制 / 常见问题 / 术语与指标口径 / 五大核心特色功能

   本页是"说明性"页面：机构清单走真实接口 GET /api/agencies，
   其余为固定的文档文案，直接写在页面里，不再依赖 js/data.js 演示数据层。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui, A = ZFZ.api;

  /* 机构 → 数据角色与用途（设计稿 P9 表格原文） */
  var AGENCY_ROLE = {
    CMA:   '主数据源：台风路径、强度与登陆预报（官方基准）',
    JMA:   '西北太平洋台风命名与路径预报（东京区域专业气象中心）',
    JTWC:  '路径与强度预警，作为多机构分歧对比的重要参照',
    HKO:   '华南沿海风雨信号与路径预报',
    KMA:   '黄海、东海方向路径预报与影响评估',
    ECMWF: '集合预报，用于中期路径不确定性与分歧评估'
  };
  var ORDER = ['CMA', 'JMA', 'JTWC', 'HKO', 'KMA', 'ECMWF'];

  /* ---------------- 静态说明文案 ---------------- */
  var API_DOCS = [
    { method: 'POST', path: '/api/auth/login',                   desc: '用户登录（ADMIN / USER 角色），返回 token 与权限' },
    { method: 'POST', path: '/api/auth/register',                desc: '用户注册（注册即登录，成功后直接建立会话；角色强制为 USER）' },
    { method: 'GET',  path: '/api/typhoons',                     desc: '档案列表（多条件分页）；/typhoons/{id} 返回单台风详情' },
    { method: 'GET',  path: '/api/typhoons/{id}/tracks',         desc: '实况轨迹点序列（经纬度 / 强度 / 气压 / 移向移速）' },
    { method: 'GET',  path: '/api/typhoons/{id}/wind-circles',   desc: '7 / 10 / 12 级风圈四象限半径' },
    { method: 'GET',  path: '/api/typhoons/{id}/forecast-paths', desc: '多机构预报路径集合（CMA / JMA / JTWC / HKO / KMA / ECMWF）' },
    { method: 'GET',  path: '/api/typhoons/{id}/disagreement',   desc: '预报分歧量化：登陆点 / 时间 / 强度 / 移速偏差与方案' },
    { method: 'GET',  path: '/api/ai/predictions',               desc: 'AI 多模型测算结果查询（DeepSeek-V3 / 通义千问-max / 豆包 / 文心一言）' },
    { method: 'DELETE', path: '/api/ai/predictions',             desc: '清除指定台风的测算记录（管理员）' },
    { method: 'GET',  path: '/api/risk/assessments',             desc: '城市风险预评估：风险等级 / 距离 / 分时段风险提示' },
    { method: 'GET',  path: '/api/similar-cases',                desc: '历史相似案例匹配（五维相似度与归因提示）' },
    { method: 'POST', path: '/api/sandbox/scenarios',            desc: '沙盘情景创建与参数扰动推演；GET 返回情景库列表' },
    { method: 'POST', path: '/api/surveys',                      desc: '提交防灾行为问卷；GET /api/surveys/agg 返回城市聚合统计' },
    { method: 'GET',  path: '/api/export/tracks',                desc: '导出轨迹 CSV / Excel（format=csv|xlsx，export_records 留痕）；/api/export/history 查历史' }
  ];

  var LOCAL_COMPUTE = [
    '① 数据融合：以 CMA 为主基准，叠加 JMA / JTWC / HKO / KMA / ECMWF 路径，逐时对齐后计算分歧度。',
    '② 模型测算：接入 DeepSeek-V3、通义千问-max、豆包 Seed-1.6、文心一言 4.0，输出登陆点、强度与置信度。',
    '③ 本土修正：对模型输出做沿海地形与历史灾情回归修正，形成平台自有台风数据。',
    '④ 不确定性提示：分歧指数 > 0.70 时标注高不确定性，并展示各模型分歧原因。'
  ];

  var FAQS = [
    { q: 'Q：为什么不同机构的预报路径不一致？',
      a: '各机构初值场、模式分辨率与集合扰动不同，分歧指数 > 0.70 时应当关注共识区间，而非单一结论。' },
    { q: 'Q：平台自己的台风数据从哪来？',
      a: '由多机构路径 + 大模型测算 + 历史灾情回归修正生成，属于辅助研判结果，非官方发布。' },
    { q: 'Q：沙盘推演结果能直接用于决策吗？',
      a: '不可以。沙盘仅反映参数扰动下的相对变化趋势，正式决策请以官方发布为准。' },
    { q: 'Q：问卷中的个人信息如何保障？',
      a: '联系方式选填且脱敏存储（仅保留前 3 位与后 4 位），聚合统计不落到个人。' }
  ];

  var GLOSSARY = [
    '分歧指数：0—1 归一化指标，综合登陆点、登陆时间、峰值强度、移速四项偏差，越接近 1 表示机构间分歧越大。',
    '高不确定性阈值：分歧指数 > 0.70 判定为高不确定性，平台会同步提示。',
    '置信度：AI 模型对自身测算结果的自评可信度（0%—100%）。',
    '相似度：当前台风与历史台风在五个维度（路径形态、峰值强度、登陆季节、移速、风圈规模）上的加权接近程度（0—1）。'
  ];

  var FEATURES = [
    { name: '多机构分歧解析', page: 'P3', desc: '量化 6 家机构路径偏差，输出分歧指数与共识登陆区间' },
    { name: '历史灾害归因',   page: 'P5', desc: '五维相似度匹配历史案例，关联当年城市灾情档案' },
    { name: '情景推演沙盘',   page: 'P6', desc: '调整登陆点、移速、强度、风圈参数，推演影响城市与风险' },
    { name: '防灾问卷采集',   page: 'P7', desc: '采集公众防灾行为，按城市聚合形成风险认知看板' },
    { name: '大模型智能测算', page: 'P9', desc: '接入 4 个国产大模型，输出平台自有登陆点与置信度' }
  ];

  /* 机构清单：null = 加载中；[] = 加载失败或为空 */
  var agencies = null;
  var agencyError = '';

  /* ---------------- 数据来源 ---------------- */

  /**
   * 渲染主列 · 多机构数据来源表。
   *
   * <p>机构清单走真实接口 `GET /api/agencies`，三种状态分别渲染：
   * 加载中（`agencies === null`）、加载失败、正常列表。
   * 表里没有的机构不占空行；库里另有已接入机构时在 sub 里据实说明。
   *
   * @returns {string} 数据来源卡 HTML
   */
  function sourceCard() {
    var head = ['代码', '预报机构', '所属', '数据角色与用途'];
    var rows;

    if (agencies === null) {
      rows = '<div class="trow"><span>—</span><span class="cell-strong">正在加载机构清单…</span>' +
        '<span>—</span><span>—</span></div>';
    } else if (agencyError) {
      rows = '<div class="trow"><span>—</span><span class="cell-strong">机构清单加载失败</span>' +
        '<span>—</span><span>' + U.esc(agencyError) + '</span></div>';
    } else {
      rows = ORDER.map(function (code, i) {
        var a = agencies.filter(function (x) { return x.code === code; })[0];
        if (!a) { return ''; }        /* 库里没有这家机构就不占一行，避免出现空壳 */
        return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
          '<span class="agency-code" style="color:' + U.esc(a.color || 'var(--ink-2)') + '">' + U.esc(a.code) + '</span>' +
          '<span class="cell-strong">' + U.esc(a.name) + '</span>' +
          '<span>' + U.esc(a.region) + '</span>' +
          '<span>' + U.esc(AGENCY_ROLE[code] || a.type || '—') + '</span>' +
          '</div>';
      }).join('');
      if (!rows) { rows = '<div class="trow"><span>—</span><span class="cell-strong">机构清单为空</span><span>—</span><span>—</span></div>'; }
    }

    /* 库里还接了别的机构时据实说明，避免"表里只有 6 家"被误读成"平台只接 6 家" */
    var extra = (agencies || []).filter(function (a) { return ORDER.indexOf(a.code) < 0; });
    var sub = 'forecast_agencies · 默认刷新间隔 30 分钟';
    if (extra.length) {
      sub += ' · 另有 ' + extra.length + ' 家已接入未列入本表';
    }

    return U.panel({
      title: '多机构数据来源与更新机制',
      sub: sub,
      cls: 'is-tight',
      body: '<div class="tbl" style="--cols:86px 200px 104px minmax(0,1fr)">' +
        '<div class="tbl-head">' + head.map(function (h) { return '<span>' + h + '</span>'; }).join('') + '</div>' +
        rows + '</div>'
    });
  }

  /* ---------------- RESTful 接口清单 ---------------- */

  /**
   * 渲染主列 · RESTful 接口清单表（数据源为模块内 `API_DOCS` 常量）。
   *
   * <p>本表是<b>说明性文案</b>，与 `docs/api.md` 保持一致即可，不额外发请求。
   *
   * @returns {string} 接口清单卡 HTML
   */
  function apiCard() {
    var head = ['方法', '接口路径', '功能说明'];
    var rows = API_DOCS.map(function (d, i) {
      return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="method ' + (d.method === 'POST' ? 'is-post' : 'is-get') + '">' + d.method + '</span>' +
        '<span class="api-path">' + U.esc(d.path) + '</span>' +
        '<span>' + U.esc(d.desc) + '</span>' +
        '</div>';
    }).join('');

    return U.panel({
      title: 'RESTful 接口清单（Java Servlet + Jackson）',
      sub: '统一返回 JSON：code / message / data',
      cls: 'is-tight',
      body: '<div class="tbl" style="--cols:72px 312px minmax(0,1fr)">' +
        '<div class="tbl-head">' + head.map(function (h) { return '<span>' + h + '</span>'; }).join('') + '</div>' +
        rows + '</div>'
    });
  }

  /* ---------------- 侧列 ---------------- */

  /**
   * 渲染侧列 · 平台本土测算机制（四条链路说明）。
   * @returns {string} 测算机制卡 HTML
   */
  function computeCard() {
    var items = LOCAL_COMPUTE.map(function (text, i) {
      return '<div class="attr-item"><i class="attr-no">' + '①②③④'[i] + '</i>' +
        '<span class="attr-text">' + U.esc(text.replace(/^[①②③④]\s*/, '')) + '</span></div>';
    }).join('');

    return U.panel({
      title: '平台本土测算机制',
      sub: 'ai_prediction_records',
      cls: 'is-tight',
      body: '<div class="attr-list">' + items + '</div>'
    });
  }

  /**
   * 渲染侧列 · 常见问题（数据源为模块内 `FAQS` 常量）。
   * @returns {string} 常见问题卡 HTML
   */
  function faqCard() {
    var items = FAQS.map(function (f) {
      return '<div class="qa-item"><span class="qa-q">' + U.esc(f.q) + '</span>' +
        '<span class="qa-a">' + U.esc(f.a) + '</span></div>';
    }).join('');

    return U.panel({
      title: '常见问题',
      sub: '共 ' + FAQS.length + ' 条',
      cls: 'is-tight',
      body: '<div class="qa-list">' + items + '</div>'
    });
  }

  /**
   * 渲染侧列 · 术语与指标口径。
   *
   * <p>每条按第一个全角冒号拆成「术语 / 定义」两段，因此 `GLOSSARY` 文案
   * 必须保留该冒号，否则整条会被当成术语名、定义为空。
   *
   * @returns {string} 术语卡 HTML
   */
  function termCard() {
    var items = GLOSSARY.map(function (text) {
      var idx = text.indexOf('：');
      var term = idx >= 0 ? text.slice(0, idx) : text;
      var def = idx >= 0 ? text.slice(idx + 1) : '';
      return '<div class="term-item"><b class="term-name">' + U.esc(term + '：') + '</b>' +
        '<span class="term-def">' + U.esc(def) + '</span></div>';
    }).join('');

    return U.panel({
      title: '术语与指标口径',
      sub: '关键指标定义',
      cls: 'is-tight',
      body: '<div class="term-list">' + items + '</div>'
    });
  }

  /**
   * 渲染侧列 · 五大核心特色功能（含对应页面代号，便于跳转）。
   * @returns {string} 特色功能卡 HTML
   */
  function featureCard() {
    var items = FEATURES.map(function (f) {
      return '<div class="stack-item"><span class="stack-title">' + U.esc(f.name) + '</span>' +
        '<span class="stack-desc">' + U.esc(f.desc) + '</span></div>';
    }).join('');

    return U.panel({
      title: '五大核心特色功能',
      sub: 'P3 / P4 / P5 / P6 / P7',
      cls: 'is-tight',
      body: '<div class="stack-list">' + items + '</div>'
    });
  }

  /**
   * 渲染整页：主列（数据来源 + 接口清单）与侧列（测算机制 / 常见问题 / 术语 / 特色功能）。
   *
   * <p>机构清单未就绪时也会先渲染一次骨架（机构表显示"加载中"），
   * 保证说明性文案立即可读，不因单个接口失败而整页空白。
   *
   * @returns {void}
   */
  function render() {
    $('#page-body').html(
      '<div class="colstack col-main">' + sourceCard() + apiCard() + '</div>' +
      '<div class="colstack col-side">' +
        computeCard() + faqCard() + termCard() + featureCard() +
      '</div>'
    );
  }

  /* ---------------- 渲染 ---------------- */
  $(function () {
    ZFZ.layout.mount('help');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '帮助与接口说明',
      desc: '多源数据来源 · 平台本土测算机制 · RESTful 接口清单'
    }));

    render();       /* 先出骨架（机构表显示"加载中"），其余文案立即可读 */

    if (!A) {
      agencies = [];
      agencyError = '接口层未加载（js/api.js）';
      render();
      return;
    }

    A.getAgencies().then(function (list) {
      agencies = list || [];
    }, function (err) {
      agencies = [];
      agencyError = (err && err.message) || '未知错误';
    }).then(render);
  });
})(window.jQuery);
