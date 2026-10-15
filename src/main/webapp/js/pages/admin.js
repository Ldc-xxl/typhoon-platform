/* ==========================================================================
   P10 管理后台
   设计稿节点 3:1513（1440×1190）
   主列：数据源接入监控 / 大模型接入配置 / 用户与权限管理
   侧列 340：后台运行概览 / 系统参数设置 / 待办与审核队列 / 数据安全与审计
   --------------------------------------------------------------------------
   本页数据全部来自 ZFZ.api（改造规范见 docs/frontend-integration-guide.md）：
     登录态   ZFZ.api.getMe()          GET  /api/auth/me（未登录 401 / 非管理员 403）
     登录     ZFZ.api.login(u, p)      POST /api/auth/login
     退出     ZFZ.api.logout()         POST /api/auth/logout
     概览     ZFZ.api.getAdminDashboard()   GET /api/admin/dashboard（{metrics, todos}）
     待办     ZFZ.api.getAdminTodos()       GET /api/admin/todos（数组）
     模型     ZFZ.api.getAdminModels()      GET /api/admin/models（数组）
     参数     ZFZ.api.getAdminSettings()    GET /api/admin/settings（数组）
     数据源   ZFZ.api.getDataSources()      GET /api/admin/datasources（数组）
     用户     ZFZ.api.getUsers()            GET /api/admin/users（{items, stats}）
     停启用   ZFZ.api.setUserStatus(id, enabled)    PUT /api/admin/users/{userId}/status
     重置密码 ZFZ.api.resetUserPassword(id, pwd)    PUT /api/admin/users/{userId}/password
   用户标识统一取 u.userId（列表项与新建接口、URL 路径同名；后端 UserVO 经
   @JsonProperty 把对外名钉成 userId，详见 docs/api.md 的字段命名约定）。
   接口失败一律渲染错误态并弹 toast，不回退到 js/data.js 的演示数据。
   鉴权失败（401/403）单独渲染针对性文案，不当作「后端挂了」处理。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /** ECharts 实例引用，重渲染前先 dispose 释放旧 DOM 绑定 */
  var growthChart = null;
  var roleChart = null;

  /**
   * 「数据安全与审计」卡片内容。
   * 该卡片是平台安全策略的静态说明，后端未提供对应接口，故保留为本地常量
   * （不属于接口数据，不存在「静默降级」问题）。
   * @type {Array<{title: string, desc: string}>}
   */
  var SECURITY_ITEMS = [
    { title: '数据库备份', desc: '每日 02:00 自动全量备份，保留最近 30 天' },
    { title: '操作审计',   desc: '管理员操作全量留痕，可追溯 180 天' },
    { title: '接口鉴权',   desc: 'token 校验 + 角色权限拦截，未授权返回 403' },
    { title: '数据脱敏',   desc: '问卷联系方式加密存储，聚合结果不含个人信息' }
  ];

  /**
   * 待办等级 → 文案配色类（后端 level 取值 warn / brand / ok ...）。
   * @type {Object<string, string>}
   */
  var TODO_CLS = { warn: 'txt-warn', brand: 'txt-brand', ok: 'txt-ok', high: 'txt-high', danger: 'txt-high' };

  /* ======================================================================== */
  /*  一、工具与鉴权判定                                                      */
  /* ======================================================================== */

  /**
   * 判断错误是否为「未登录 / 会话过期」。
   * @param {Error} err api.js reject 的错误对象
   * @returns {boolean} true 表示需要重新登录
   */
  function isAuthErr(err) {
    if (!err) { return false; }
    if (err.code === 401) { return true; }
    return err.code === null && /请先登录/.test(err.message || '');
  }

  /**
   * 判断错误是否为「非管理员」。
   * @param {Error} err api.js reject 的错误对象
   * @returns {boolean} true 表示当前账号无管理权限
   */
  function isForbiddenErr(err) {
    if (!err) { return false; }
    if (err.code === 403) { return true; }
    return err.code === null && /管理员/.test(err.message || '');
  }

  /**
   * 判断当前登录用户是否为平台管理员。
   * @param {Object|null} me getMe() 返回的用户信息
   * @returns {boolean} true 表示管理员
   */
  function isAdmin(me) {
    return !!me && (me.role === 'ADMIN' || me.roleCode === 'ADMIN');
  }

  /* ======================================================================== */
  /*  二、卡片渲染（一律接收数据作为参数，不在内部读 mock）                      */
  /* ======================================================================== */

  /**
   * 数据源接入与更新监控。
   * @param {Array<Object>} list GET /api/admin/datasources 返回的数组
   * @returns {string} 卡片 HTML
   */
  function sourceCard(list) {
    var head = ['数据源', '接口类型', '最近更新', '今日请求', '成功率', '接入状态'];
    var rows = (list || []).map(function (d, i) {
      var ok = d.status === '正常';
      return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(d.name) + '</span>' +
        '<span class="cell-num">' + U.esc(d.type) + '</span>' +
        '<span class="cell-num">' + U.esc(d.updated) + '</span>' +
        '<span class="cell-num">' + U.esc(d.requests) + '</span>' +
        '<span class="cell-num">' + U.esc(d.success) + '</span>' +
        '<span style="color:' + (ok ? 'var(--ok)' : 'var(--warn-ink)') + '">' + U.esc(d.status) + '</span>' +
        '</div>';
    }).join('');

    var body = (list && list.length)
      ? '<div class="tbl" style="--cols:210px 96px 156px 96px 96px 84px">' +
          '<div class="tbl-head">' + head.map(function (h) { return '<span>' + h + '</span>'; }).join('') + '</div>' +
          rows + '</div>'
      : U.emptyRow('暂无数据源');

    return U.panel({
      title: '数据源接入与更新监控',
      sub: 'forecast_agencies · 多机构数据每 30 分钟自动同步',
      cls: 'is-tight',
      body: body
    });
  }

  /**
   * 大模型接入配置（平台本土测算）。
   * @param {Array<Object>} list GET /api/admin/models 返回的数组
   * @returns {string} 卡片 HTML
   */
  function modelCard(list) {
    var head = ['模型', '版本', '接入状态', '今日调用', '平均耗时', '置信度贡献', '操作'];
    var rows = (list || []).map(function (m, i) {
      var on = m.status === '已接入';
      return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(m.name) + '</span>' +
        '<span class="cell-num">' + U.esc(m.version) + '</span>' +
        '<span style="color:' + (on ? 'var(--ok)' : 'var(--ink-3)') + '">' + U.esc(m.status) + '</span>' +
        '<span class="cell-num">' + U.esc(m.calls) + '</span>' +
        '<span class="cell-num">' + U.esc(m.cost) + '</span>' +
        '<span class="cell-num">' + U.esc(m.confidence) + '</span>' +
        '<span class="cell-link" data-model="' + U.esc(m.name) + '" data-key-ok="' + (m.keyConfigured ? '1' : '0') + '">' +
          U.esc(m.action) + '</span>' +
        '</div>';
    }).join('');

    var body = (list && list.length)
      ? '<div class="tbl" style="--cols:160px 68px 88px 92px 92px 100px 62px">' +
          '<div class="tbl-head">' + head.map(function (h) { return '<span>' + h + '</span>'; }).join('') + '</div>' +
          rows + '</div>'
      : U.emptyRow('暂无模型配置');

    return U.panel({
      title: '大模型接入配置（平台本土测算）',
      sub: 'ai_prediction_records · 密钥仅加密存储于服务端',
      cls: 'is-tight',
      body: body
    });
  }

  /**
   * 用户与权限管理。
   * 操作列渲染后端提供的 ops（如「编辑 · 禁用」），每颗按钮带 data-user-id /
   * data-account / data-action，供点击时调用真实接口。
   * @param {Object} data GET /api/admin/users 返回的 { items, stats }
   * @returns {string} 卡片 HTML
   */
  function userCard(data) {
    var items = (data && data.items) || [];
    var head = ['账号', '角色', '权限范围', '注册时间', '最近登录', '状态', '操作'];
    var rows = items.map(function (u, i) {
      var on = u.enabled !== false && u.status === '正常';
      return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(u.account) + '</span>' +
        '<span>' + U.esc(u.role) + '</span>' +
        '<span>' + U.esc(u.scope) + '</span>' +
        '<span class="cell-num">' + U.esc(u.registered) + '</span>' +
        '<span class="cell-num">' + U.esc(u.lastLogin) + '</span>' +
        '<span class="cell-status" style="color:' + (on ? 'var(--ok)' : 'var(--ink-3)') + '">' +
          U.esc(u.status) + '</span>' +
        '<span class="cell-ops">' +
          String(u.ops || '').split(' · ').map(function (a) {
            return '<button class="cell-act' + (a === '禁用' ? ' is-danger' : '') + '" type="button" ' +
              'data-user-id="' + U.esc(u.userId) + '" data-account="' + U.esc(u.account) + '" ' +
              'data-action="' + U.esc(a) + '">' + U.esc(a) + '</button>';
          }).join('<i class="cell-act-sep"></i>') +
        '</span>' +
        '</div>';
    }).join('');

    var body = items.length
      ? '<div class="tbl" style="--cols:118px 128px 182px 96px 142px 76px 104px">' +
          '<div class="tbl-head">' + head.map(function (h) { return '<span>' + h + '</span>'; }).join('') + '</div>' +
          rows + '</div>'
      : U.emptyRow('暂无用户');

    return U.panel({
      title: '用户与权限管理',
      sub: 'users · 普通用户仅可查询/推演/问卷，管理员可配置系统',
      cls: 'is-tight',
      body: body
    });
  }

  /**
   * 后台运行概览（四块指标取自 dashboard.metrics）。
   * @param {Object} metrics GET /api/admin/dashboard 返回的 metrics
   * @returns {string} 卡片 HTML
   */
  function overviewCard(metrics) {
    var m = metrics || {};
    var tiles = [
      { label: '注册用户（users）',     value: m.totalUsers },
      { label: '活跃台风（typhoons）',  value: m.activeTyphoons },
      { label: '问卷样本（surveys）',   value: m.surveySamples },
      { label: '情景推演（scenarios）', value: m.sandboxScenarios }
    ].map(function (x) {
      return '<div class="ov-tile"><span class="ov-label">' + U.esc(x.label) + '</span>' +
        '<span class="ov-value">' + U.esc(x.value === undefined || x.value === null ? '—' : x.value) +
        '</span></div>';
    }).join('');

    return U.panel({
      title: '后台运行概览',
      sub: m.generatedAt || '',
      cls: 'is-tight',
      body: '<div class="ov-grid">' + tiles + '</div>'
    });
  }

  /**
   * 系统参数设置。
   * 说明：`sandbox_param_range` 等参数的值是一长串 JSON，而 `.param-value`
   * 为 `white-space:nowrap`，直接展示会把左侧键名挤成竖排。为保持既有版式
   * （不改 CSS），这里对超长值做等长截断展示。
   * @param {Array<Object>} list GET /api/admin/settings 返回的数组
   * @returns {string} 卡片 HTML
   */
  function settingCard(list) {
    var MAX = 26;
    var rows = (list || []).map(function (s) {
      var val = s.settingValue === null || s.settingValue === undefined ? '' : String(s.settingValue);
      return {
        key: s.settingKey,
        desc: s.description,
        value: val.length > MAX ? val.slice(0, MAX) + '…' : val
      };
    });

    return U.panel({
      title: '系统参数设置',
      sub: 'system_settings',
      cls: 'is-tight',
      body: rows.length ? U.paramRows(rows) : U.emptyRow('暂无系统参数')
    });
  }

  /**
   * 待办与审核队列。
   * @param {Array<Object>} list GET /api/admin/todos 返回的数组
   * @returns {string} 卡片 HTML
   */
  function todoCard(list) {
    var items = (list || []).map(function (t) {
      return '<div class="todo-row">' +
        '<span class="flex-col min-w-0"><span class="todo-title">' + U.esc(t.title) + '</span>' +
        '<span class="todo-src">' + U.esc(t.src) + '</span></span>' +
        '<span class="todo-state ' + (TODO_CLS[t.level] || 'txt-muted') + '">' + U.esc(t.state) + '</span>' +
        '</div>';
    }).join('');

    return U.panel({
      title: '待办与审核队列',
      sub: '共 ' + (list || []).length + ' 项',
      cls: 'is-tight',
      body: items ? '<div class="todo-list">' + items + '</div>'
                  : U.emptyRow('当前没有待办事项')
    });
  }

  /**
   * 数据安全与审计（静态策略说明）。
   * @returns {string} 卡片 HTML
   */
  function securityCard() {
    return U.panel({
      title: '数据安全与审计',
      sub: '安全策略',
      cls: 'is-tight',
      body: U.stackItems(SECURITY_ITEMS)
    });
  }

  /**
   * 平台数据统计（ECharts 图表容器）。
   * 数据源：GET /api/admin/stats → { userGrowth, dataDistribution }。
   * 容器高度由内联样式给定，ECharts 需要显式像素高度才能正确渲染。
   * @param {Object} stats getAdminStats() 返回的统计对象
   * @returns {string} 卡片 HTML
   */
  function statsCard(stats) {
    var note = (stats && ((stats.userGrowth && stats.userGrowth.length) || (stats.dataDistribution && stats.dataDistribution.length)))
      ? '' : U.emptyRow('暂无统计数据');
    return U.panel({
      title: '平台数据统计',
      sub: 'ECharts · 用户增长趋势 / 角色分布',
      cls: 'is-tight',
      body: note +
        '<div id="echart-growth" style="height:240px"></div>' +
        '<div id="echart-role" style="height:240px;margin-top:8px"></div>'
    });
  }

  /**
   * 用真实统计数渲染两张 ECharts 图。
   * 折线图：用户累计增长趋势；饼图：用户角色分布。
   * 若页面离线导致 ECharts 未加载，降级为文本提示，绝不抛错白屏。
   * @param {Object} stats getAdminStats() 返回的统计对象
   * @returns {void}
   */
  function renderCharts(stats) {
    if (!stats) { return; }
    var growth = stats.userGrowth || [];
    var dist = stats.dataDistribution || [];

    if (!window.echarts) {
      $('#echart-growth').html(U.emptyRow('图表库未加载（离线时请把 echarts.min.js 放入 vendor/）'));
      $('#echart-role').html(U.emptyRow('图表库未加载'));
      return;
    }

    if (growthChart) { growthChart.dispose(); }
    if (roleChart) { roleChart.dispose(); }

    growthChart = echarts.init(document.getElementById('echart-growth'));
    growthChart.setOption({
      title: { text: '用户增长趋势（累计）', left: 'center', textStyle: { fontSize: 13 } },
      tooltip: { trigger: 'axis' },
      grid: { left: 44, right: 16, top: 38, bottom: 28 },
      xAxis: { type: 'category', data: growth.map(function (g) { return g.date; }) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ type: 'line', smooth: true, areaStyle: {},
        data: growth.map(function (g) { return g.count; }) }]
    });

    roleChart = echarts.init(document.getElementById('echart-role'));
    roleChart.setOption({
      title: { text: '用户角色分布', left: 'center', textStyle: { fontSize: 13 } },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie', radius: ['40%', '66%'], center: ['50%', '46%'],
        data: dist.map(function (d) { return { name: d.name, value: d.value }; })
      }]
    });

    /* 侧列宽度固定，图表初始化后尺寸可能未就绪，下一帧强制刷新一次布局 */
    setTimeout(function () {
      if (growthChart) { growthChart.resize(); }
      if (roleChart) { roleChart.resize(); }
    }, 0);
  }

  /* ======================================================================== */
  /*  三、鉴权态：请先登录 / 无权限 / 错误                                     */
  /* ======================================================================== */

  /**
   * 「请先登录」卡片：复用组件库的 authForm（登录+可选的注册骨架），
   * 登录成功后自动重载管理后台。
   * @param {Error} [err] 触发该状态的原因（会话过期时展示）
   * @returns {string} 卡片 HTML
   */
  function loginPanel(err) {
    var reason = err && err.message ? err.message : '请先登录后再访问管理后台';
    return U.panel({
      title: '请先登录',
      sub: '管理后台需要管理员会话',
      cls: 'is-tight',
      body:
        /* 表单骨架改用组件库统一的 authForm（原先此处内联了一套，与 login.html 重复）。
           管理后台不开放注册入口，故 showRegister:false；失败原因走 msg 由内部统一 esc。
           刻意不传 msgCls —— 「未登录 / 会话已过期」是正常访客状态而非故障，用中性提示，
           避免红底错误框让人误以为后端挂了（layout.js 的 paintGuest 与 paintSessionError
           分开处理也是同一用意）。 */
        U.authForm({
          mode: 'login',
          showRegister: false,
          msg: '未登录或会话已过期：' + reason
        }) +
        U.note(U.esc('测试账号：admin01 / password'))
    });
  }

  /**
   * 「无访问权限」卡片：已登录但不是管理员。不渲染管理数据，避免误报「后端挂了」。
   * @param {Object|null} me getMe() 返回的用户信息
   * @returns {string} 卡片 HTML
   */
  function forbiddenPanel(me) {
    var who = (me && (me.userName || me.account)) || '当前账号';
    var tip = (me && me.roleText) ? '（角色：' + me.roleText + '）' : '';
    return U.panel({
      title: '无访问权限',
      sub: '仅平台管理员可用',
      cls: 'is-tight',
      body: U.note(U.esc('该功能仅平台管理员可用。登录账号「' + who + '」' + tip +
        ' 不是管理员，已阻断全部管理接口请求。')) +
        '<div class="panel-foot">' +
          '<button class="zbtn" type="button" id="logout-btn">退出登录</button>' +
        '</div>'
    });
  }

  /**
   * 整页错误态卡片（非鉴权类失败时使用，绝不回退到演示数据）。
   * @param {string} title 卡片标题
   * @param {string} message 错误原因（err.message，已由 api.js 翻译）
   * @returns {string} 卡片 HTML
   */
  var errorPanel = U.errorPanel;

  /* ======================================================================== */
  /*  四、渲染与交互                                                          */
  /* ======================================================================== */

  /**
   * 渲染「请先登录」态并绑定登录表单。
   * @param {Error} [err] 触发原因
   * @returns {void}
   */
  function renderLogin(err) {
    $('#page-body').html('<div class="colstack col-main">' + loginPanel(err) + '</div>');

    $('#login-submit').on('click', function () {
      var account = $.trim($('#login-account').val());
      var password = $('#login-password').val();
      if (!account || !password) {
        ZFZ.layout.toast('请输入账号与密码');
        return;
      }
      $(this).prop('disabled', true).text('登录中…');
      ZFZ.api.login(account, password).then(function () {
        ZFZ.layout.toast('登录成功，正在加载管理后台…');
        /* 顶栏身份是在页面加载时那一次 /api/auth/me 画上去的，那时还没登录。
           不重新探测一次的话，用户会看到"已进入后台、顶栏却写着未登录"的矛盾画面。 */
        if (ZFZ.layout.refreshSession) { ZFZ.layout.refreshSession(); }
        boot();
      }).catch(function (e) {
        ZFZ.layout.toast('登录失败：' + e.message);
        $('#login-submit').prop('disabled', false).text('登录');
      });
    });

    $('#login-password').on('keydown', function (e) {
      if (e.key === 'Enter') { $('#login-submit').trigger('click'); }
    });
  }

  /**
   * 渲染「无访问权限」态并绑定退出登录。
   * @param {Object|null} me 当前用户
   * @returns {void}
   */
  function renderForbidden(me) {
    $('#page-body').html('<div class="colstack col-main">' + forbiddenPanel(me) + '</div>');
    $('#logout-btn').on('click', doLogout);
  }

  /**
   * 渲染非鉴权类错误态。
   * @param {Error} err 错误对象
   * @returns {void}
   */
  function renderFatal(err) {
    $('#page-body').html(
      '<div class="colstack col-main">' + errorPanel('管理后台', err.message) + '</div>'
    );
    ZFZ.layout.toast('加载失败：' + err.message);
  }

  /**
   * 调用真实登出接口并回到登录态。
   * @returns {void}
   */
  function doLogout() {
    ZFZ.api.logout().then(function () {
      ZFZ.layout.toast('已退出登录');
      boot();
    }).catch(function (err) {
      ZFZ.layout.toast('退出失败：' + err.message);
    });
  }

  /**
   * 用真实数据重渲染整页。
   * @param {Object} dashboard getAdminDashboard() 返回的 { metrics, todos }
   * @param {Array<Object>} todos getAdminTodos() 返回的数组
   * @param {Array<Object>} models getAdminModels() 返回的数组
   * @param {Array<Object>} settings getAdminSettings() 返回的数组
   * @param {Array<Object>} sources getDataSources() 返回的数组
   * @param {Object} users getUsers() 返回的 { items, stats }
   * @returns {void}
   */
  function paint(dashboard, todos, models, settings, sources, users, stats) {
    var metrics = (dashboard && dashboard.metrics) || {};

    $('#page-body').html(
      '<div class="colstack col-main">' +
        sourceCard(sources) + modelCard(models) + userCard(users) +
      '</div>' +
      '<div class="colstack col-side">' +
        overviewCard(metrics) + statsCard(stats) + settingCard(settings) +
        todoCard(todos) + securityCard() +
      '</div>'
    );

    bind();
    renderCharts(stats);
  }

  /**
   * 绑定页内交互：模型配置提示 + 用户操作列（禁用/启用、重置密码）。
   * 每次重渲染 #page-body 后需重新绑定（旧节点已随 DOM 移除）。
   * @returns {void}
   */
  function bind() {
    /* 大模型配置项：无对应写接口，给出非阻塞说明（密钥服务端加密存储） */
    $('[data-model]').on('click', function () {
      var name = $(this).data('model');
      var keyOk = $(this).attr('data-key-ok') === '1';
      ZFZ.layout.toast('「' + name + '」' + (keyOk ? '密钥已配置' : '密钥待配置') +
        ' · 密钥仅加密存储于服务端');
    });

    /* 用户操作列。
       注意：必须用 .attr() 读写 data-action —— jQuery 的 .data() 会缓存首次读取的值，
       同一按钮二次点击时仍返回旧值。 */
    $('#page-body').on('click', '.cell-act', function () {
      var $btn = $(this);
      var act = $btn.attr('data-action');
      var acc = $btn.attr('data-account');
      var id = $btn.attr('data-user-id');

      if (act === '禁用' || act === '启用') {
        var enable = (act === '启用');
        $btn.prop('disabled', true);
        ZFZ.api.setUserStatus(id, enable).then(function () {
          ZFZ.layout.toast(acc + (enable ? ' 已启用' : ' 已停用'));
          loadAll();
        }).catch(function (err) {
          ZFZ.layout.toast('操作失败：' + err.message);
          $btn.prop('disabled', false);
        });
        return;
      }

      if (act === '重置') {
        /* 生成一次性临时密码并调用真实重置接口（后端不接收「发送链接」形式） */
        var temp = 'Zfz@' + (100000 + Math.floor(Math.random() * 900000));
        $btn.prop('disabled', true);
        ZFZ.api.resetUserPassword(id, temp).then(function () {
          ZFZ.layout.toast('已将「' + acc + '」的密码重置为 ' + temp + '，请提醒其尽快修改');
          loadAll();
        }).catch(function (err) {
          ZFZ.layout.toast('重置失败：' + err.message);
          $btn.prop('disabled', false);
        });
        return;
      }

      if (act === '编辑') {
        ZFZ.layout.toast('「' + acc + '」资料 / 角色编辑需用户编辑表单（设计稿未提供），' +
          '对应接口 PUT /api/admin/users/' + id);
      }
    });
  }

  /**
   * 加载全部管理数据并渲染。
   * 会话中途失效（401）或权限不足（403）时切换到对应鉴权态，而非报「后端挂了」。
   * @returns {void}
   */
  function loadAll() {
    var loading = U.loadingRow();

    /* 1) 先渲染「加载中」骨架，避免白屏 */
    $('#page-body').html(
      '<div class="colstack col-main">' +
        U.panel({ title: '数据源接入与更新监控', sub: 'forecast_agencies · 多机构数据每 30 分钟自动同步', cls: 'is-tight', body: loading }) +
        U.panel({ title: '大模型接入配置（平台本土测算）', sub: 'ai_prediction_records · 密钥仅加密存储于服务端', cls: 'is-tight', body: loading }) +
        U.panel({ title: '用户与权限管理', sub: 'users · 普通用户仅可查询/推演/问卷，管理员可配置系统', cls: 'is-tight', body: loading }) +
      '</div>' +
      '<div class="colstack col-side">' +
        U.panel({ title: '后台运行概览', cls: 'is-tight', body: loading }) +
        U.panel({ title: '系统参数设置', sub: 'system_settings', cls: 'is-tight', body: loading }) +
        U.panel({ title: '待办与审核队列', cls: 'is-tight', body: loading }) +
        U.panel({ title: '数据安全与审计', sub: '安全策略', cls: 'is-tight', body: loading }) +
      '</div>'
    );

    /* 2) 并发拉取全部管理数据 */
    Promise.all([
      ZFZ.api.getAdminDashboard(),
      ZFZ.api.getAdminTodos(),
      ZFZ.api.getAdminModels(),
      ZFZ.api.getAdminSettings(),
      ZFZ.api.getDataSources(),
      ZFZ.api.getUsers(),
      ZFZ.api.getAdminStats()
    ]).then(function (res) {
      paint(res[0], res[1], res[2], res[3], res[4], res[5], res[6]);
    }).catch(function (err) {
      if (isAuthErr(err)) { renderLogin(err); return; }
      if (isForbiddenErr(err)) { renderForbidden(null); return; }
      renderFatal(err);
    });
  }

  /**
   * 入口：先判定登录态，再决定渲染登录引导 / 无权限提示 / 管理后台。
   * @returns {void}
   */
  function boot() {
    ZFZ.api.getMe().then(function (me) {
      if (!isAdmin(me)) { renderForbidden(me); return; }
      loadAll();
    }).catch(function (err) {
      if (isAuthErr(err)) { renderLogin(err); return; }
      if (isForbiddenErr(err)) { renderForbidden(null); return; }
      renderFatal(err);
    });
  }

  $(function () {
    ZFZ.layout.mount('admin');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '管理后台',
      desc: '数据源与模型接入 · 系统参数 · 用户与权限 · 待办处理'
    }));

    /* 顶栏「退出登录」由 layout.js 统一处理（真调 /api/auth/logout 后整页刷新），
       此处不再重复绑定 —— 两边都绑会发两次登出请求，且刷新与局部重渲染互相打架。 */

    boot();
  });
})(window.jQuery);
