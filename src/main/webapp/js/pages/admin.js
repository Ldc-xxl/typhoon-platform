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
     改资料   ZFZ.api.updateUser(id, payload)       PUT /api/admin/users/{userId}
     改角色   ZFZ.api.setUserRole(id, role)         PUT /api/admin/users/{userId}/role
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
   * 最近一次 /api/admin/users 返回的用户数组（编辑弹层按 userId 回填用）。
   *
   * <p>刻意不把 email / phone 写进行的 data-* 属性：那等于把联系方式复制进
   * HTML，既多一份注入面，也会随每次重渲染反复拼接、越改越容易漏字段。
   *
   * @type {Array<Object>}
   */
  var lastUsers = [];

  /**
   * 当前登录用户 id（字符串）。boot 时由 /api/auth/me 填充，
   * 用于锁定「自己」那一行的角色下拉（后端 setRole 会拒绝降低本人权限）。
   * @type {string|null}
   */
  var meId = null;

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
   *
   * <p>三态口径由后端给出（见 {@code AdminServiceImpl.modelConfigs}）：
   * 「已接入」绿色 /「已配置」橙色（填了 Key 但还没验证通过）/「待接入」灰色。
   * 颜色之外一定同时显示文字 —— 只靠颜色区分状态对色弱用户是不可用的。
   *
   * @param {Array<Object>} list GET /api/admin/models 返回的数组
   * @returns {string} 卡片 HTML
   */
  function modelCard(list) {
    var head = ['模型', '版本', '接入状态', '调用次数', '平均耗时', '置信度贡献', '操作'];
    var STATE_COLOR = { '已接入': 'var(--ok)', '已配置': 'var(--warn-ink)' };
    var rows = (list || []).map(function (m, i) {
      return '<div class="trow' + (i % 2 === 0 ? ' is-alt' : '') + '">' +
        '<span class="cell-strong">' + U.esc(m.name) + '</span>' +
        '<span class="cell-num">' + U.esc(m.version) + '</span>' +
        '<span style="color:' + (STATE_COLOR[m.status] || 'var(--ink-3)') + '">' + U.esc(m.status) + '</span>' +
        '<span class="cell-num">' + U.esc(m.calls) + '</span>' +
        '<span class="cell-num">' + U.esc(m.cost) + '</span>' +
        '<span class="cell-num">' + U.esc(m.confidence) + '</span>' +
        '<span class="cell-ops">' +
          '<button class="cell-act" type="button"' +
            ' data-model-key="' + U.esc(m.modelKey) + '"' +
            ' data-model-name="' + U.esc(m.name) + '"' +
            ' data-model-status="' + U.esc(m.status) + '"' +
            ' data-model-provider="' + U.esc(m.provider || '') + '"' +
            ' data-model-endpoint="' + U.esc(m.endpointId || '') + '"' +
            ' data-model-base="' + U.esc(m.baseUrl || '') + '"' +
            ' data-model-key-ok="' + (m.keyConfigured ? '1' : '0') + '">' +
            U.esc(m.action) + '</button>' +
        '</span>' +
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

  /* ======================================================================== */
  /*  三、用户编辑弹层                                                        */
  /* ======================================================================== */

  /**
   * 校验规则，与后端 {@code UserServiceImpl.validateProfile} 逐条对齐。
   *
   * <p>前端校验只为即时反馈（少发一次注定 400 的请求），最终以后端为准；
   * 两端的正则必须一致，否则会出现"前端放过去、后端打回来"的错位。
   */
  var EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  var PHONE_RE = /^1[3-9]\d{9}$/;
  var NICKNAME_MAX = 50;

  /** 弹层当前编辑的用户（打开时快照，关闭置 null） @type {Object|null} */
  var dlgUser = null;

  /** 打开弹层前的焦点元素，关闭后还回去（键盘用户不该被扔回页面顶部） */
  var dlgReturnFocus = null;

  /**
   * 创建弹层骨架（幂等：已存在则直接返回）。
   *
   * <p>弹层挂在 {@code <body>} 末尾而不是 {@code #page-body} 里：
   * 保存成功后会调 {@code loadAll()} 整块重渲染 #page-body，
   * 挂在里面会被连带销毁，用户看到的是"弹层自己消失了"。
   *
   * @returns {void}
   */
  function ensureUserDialog() {
    if ($('#user-dlg').length) { return; }

    $('body').append(
      '<div class="zdlg-mask" id="user-dlg" hidden>' +
        '<div class="zdlg" role="dialog" aria-modal="true" aria-labelledby="user-dlg-title">' +
          '<div class="zdlg-head">' +
            '<div>' +
              '<div class="zdlg-title" id="user-dlg-title">编辑用户</div>' +
              '<div class="zdlg-sub" id="user-dlg-sub"></div>' +
            '</div>' +
            '<button class="zdlg-x" type="button" data-dlg-close aria-label="关闭">' +
              '<svg width="11" height="11" viewBox="0 0 12 12" fill="none" aria-hidden="true">' +
                '<path d="M1.5 1.5l9 9M10.5 1.5l-9 9" stroke="currentColor" stroke-width="1.5" ' +
                'stroke-linecap="round"/></svg>' +
            '</button>' +
          '</div>' +
          '<div class="zdlg-body">' +
            '<div class="field"><span class="field-label">登录账号（不可修改）</span>' +
              '<input class="input" id="user-dlg-account" type="text" disabled></div>' +
            '<div class="field"><span class="field-label">昵称</span>' +
              '<input class="input" id="user-dlg-nickname" type="text" maxlength="' + NICKNAME_MAX +
              '" autocomplete="off"></div>' +
            '<div class="field"><span class="field-label">邮箱（选填，留空即清除）</span>' +
              '<input class="input" id="user-dlg-email" type="email" maxlength="100" autocomplete="off"></div>' +
            '<div class="field"><span class="field-label">手机号（选填，留空即清除）</span>' +
              '<input class="input" id="user-dlg-phone" type="text" maxlength="11" inputmode="numeric" ' +
              'autocomplete="off"></div>' +
            '<div class="field"><span class="field-label">角色</span>' +
              '<select class="select" id="user-dlg-role">' +
                '<option value="USER">普通用户 · 查询 / 沙盘 / 问卷</option>' +
                '<option value="ADMIN">平台总管理员 · 全部功能 + 系统配置</option>' +
              '</select>' +
              '<span class="field-hint" id="user-dlg-role-hint" hidden></span></div>' +
            '<div class="zdlg-msg" id="user-dlg-msg" hidden></div>' +
          '</div>' +
          '<div class="zdlg-foot">' +
            '<button class="zbtn" type="button" data-dlg-close>取消</button>' +
            '<button class="zbtn is-primary" type="button" id="user-dlg-save">保存</button>' +
          '</div>' +
        '</div>' +
      '</div>'
    );

    $('#user-dlg').on('click', '[data-dlg-close]', closeUserDialog);
    /* 点遮罩空白处关闭；点面板内部不能关（事件会冒泡到这里，比对 target 即可区分） */
    $('#user-dlg').on('click', function (e) {
      if (e.target === this) { closeUserDialog(); }
    });
    $('#user-dlg-save').on('click', saveUserDialog);
    /* 昵称框回车 = 保存，省一次鼠标移动 */
    $('#user-dlg-nickname').on('keydown', function (e) {
      if (e.key === 'Enter') { saveUserDialog(); }
    });
    /* ESC 关闭：绑在 document 上，否则焦点在 select / 按钮里时键盘事件收不到 */
    $(document).on('keydown.zfzUserDlg', function (e) {
      if (e.key === 'Escape' && !$('#user-dlg').prop('hidden')) { closeUserDialog(); }
    });
  }

  /**
   * 按 id 在最近一次加载的列表里找用户。
   * @param {string|number} id 用户 id
   * @returns {Object|null} 用户对象
   */
  function findUser(id) {
    for (var i = 0; i < lastUsers.length; i++) {
      if (String(lastUsers[i].userId) === String(id)) { return lastUsers[i]; }
    }
    return null;
  }

  /**
   * 弹层内错误提示（传空字符串则隐藏）。
   * @param {string} text 提示文案
   * @returns {void}
   */
  function dlgMsg(text) {
    var $m = $('#user-dlg-msg');
    if (!text) { $m.prop('hidden', true).text(''); return; }
    $m.text(text).prop('hidden', false);
  }

  /**
   * 打开用户编辑弹层并回填当前值。
   * @param {Object|null} u 用户对象（来自 lastUsers）
   * @param {Element} [trigger] 触发按钮；关闭时把焦点还给它。
   *        不依赖 document.activeElement —— 程序化 click() 不会给按钮聚焦，
   *        取 activeElement 会拿到 body，关闭后焦点就丢了。
   * @returns {void}
   */
  function openUserDialog(u, trigger) {
    if (!u) { ZFZ.layout.toast('未找到该用户，请刷新页面后重试'); return; }
    ensureUserDialog();
    dlgUser = u;
    dlgReturnFocus = trigger || document.activeElement;

    $('#user-dlg-account').val(u.account || '');
    $('#user-dlg-nickname').val(u.nickname || u.account || '');
    $('#user-dlg-email').val(u.email || '');
    $('#user-dlg-phone').val(u.phone || '');
    $('#user-dlg-role').val(u.roleCode === 'ADMIN' ? 'ADMIN' : 'USER');
    $('#user-dlg-sub').text((u.role || '') + ' · 注册于 ' + (u.registered || '—'));
    $('#user-dlg-save').prop('disabled', false).text('保存');
    dlgMsg('');

    /* 当前登录账号的角色锁定：后端 setRole 会以 403 拒绝"降低本人权限"，
       前端不给注定失败的操作留入口，并把原因写在字段下方 ——
       否则用户只会收到一次看不懂的失败提示。 */
    var self = meId !== null && String(u.userId) === String(meId);
    $('#user-dlg-role').prop('disabled', self);
    $('#user-dlg-role-hint')
      .prop('hidden', !self)
      .text(self ? '当前登录账号，角色不可修改（防止把自己锁在管理后台外）' : '');

    $('#user-dlg').prop('hidden', false);
    $('body').addClass('is-dlg-open');
    $('#user-dlg-nickname').trigger('focus');
  }

  /**
   * 关闭弹层并把焦点还给触发它的按钮。
   * @returns {void}
   */
  function closeUserDialog() {
    if ($('#user-dlg').prop('hidden')) { return; }
    $('#user-dlg').prop('hidden', true);
    $('body').removeClass('is-dlg-open');
    dlgUser = null;
    if (dlgReturnFocus && dlgReturnFocus.focus) { dlgReturnFocus.focus(); }
    dlgReturnFocus = null;
  }

  /**
   * 保存编辑结果。
   *
   * <p>角色与资料是两个接口（{@code PUT /users/{id}} 的 body 只解析
   * nickname / email / phone，不含 role），所以角色变化时要发两次请求。
   * 顺序上先角色后资料：{@code /role} 最可能被「最后一个管理员」「不能降级自己」
   * 拦下，放在前面失败时资料一个字段都不会动；反过来会留下
   * 「角色没改成、昵称却已经变了」的半截状态。
   *
   * @returns {void}
   */
  function saveUserDialog() {
    if (!dlgUser) { return; }
    var u = dlgUser;
    var nickname = $.trim($('#user-dlg-nickname').val() || '');
    var email = $.trim($('#user-dlg-email').val() || '');
    var phone = $.trim($('#user-dlg-phone').val() || '');
    var role = $('#user-dlg-role').val();

    var bad = '';
    if (!nickname) {
      bad = '昵称不能为空';
    } else if (nickname.length > NICKNAME_MAX) {
      bad = '昵称不能超过 ' + NICKNAME_MAX + ' 个字符';
    } else if (email && !EMAIL_RE.test(email)) {
      bad = '邮箱格式不正确，例：name@example.com';
    } else if (phone && !PHONE_RE.test(phone)) {
      bad = '手机号格式不正确，应为 1 开头的 11 位数字';
    }
    if (bad) { dlgMsg(bad); return; }

    var roleChanged = !!role && role !== u.roleCode;
    var roleDone = false;
    var $save = $('#user-dlg-save').prop('disabled', true).text('保存中…');
    dlgMsg('');

    (roleChanged ? ZFZ.api.setUserRole(u.userId, role) : Promise.resolve(null))
      .then(function () {
        if (roleChanged) {
          roleDone = true;
          /* 立刻更新快照：若资料保存失败、用户重试，不会再把同一个 /role 请求发一遍 */
          u.roleCode = role;
        }
        return ZFZ.api.updateUser(u.userId, { nickname: nickname, email: email, phone: phone });
      })
      .then(function () {
        closeUserDialog();
        ZFZ.layout.toast('「' + u.account + '」的资料已保存' +
          (roleChanged ? '，角色已切换为' + (role === 'ADMIN' ? '平台总管理员' : '普通用户') : ''));
        loadAll();
      })
      .catch(function (err) {
        $save.prop('disabled', false).text('保存');
        var msg = (err && err.message) ? err.message : '保存失败';
        if (roleDone) {
          /* 角色已经落库了，列表必须刷新，否则界面还停在旧角色上 */
          msg = '角色已切换，但资料保存失败：' + msg;
          loadAll();
        }
        dlgMsg(msg);
      });
  }

  /* ======================================================================== */
  /*  四、大模型接入弹层                                                      */
  /* ======================================================================== */

  /**
   * 各厂商的默认网关与"Key 去哪拿"。
   *
   * <p>不写这段指引，管理员面对一个空输入框只能去翻文档 ——
   * 而这一步恰恰是接入最容易卡住的地方（豆包要的是接入点 ID，不是模型名）。
   * @type {Object<string, {label: string, where: string, endpointPh: string, basePh: string}>}
   */
  var PROVIDER_INFO = {
    doubao: {
      label: '火山方舟（豆包）',
      where: '密钥在 <b>火山方舟控制台 → API Key 管理</b> 创建；接入点填 <b>推理接入点 ID</b>' +
        '（ep- 开头）或模型 ID（如 doubao-seed-1-6-250615）。',
      endpointPh: 'ep-2026xxxx 或 doubao-seed-1-6-250615',
      basePh: '留空则用 https://ark.cn-beijing.volces.com/api/v3'
    },
    qianfan: {
      label: '百度千帆（文心一言）',
      where: '密钥在 <b>百度智能云 → 千帆大模型平台 → API Key</b> 创建；接入点填模型名（如 ernie-4.0-8k）。',
      endpointPh: 'ernie-4.0-8k',
      basePh: '留空则用 https://qianfan.baidubce.com/v2'
    },
    'openai-compatible': {
      label: 'OpenAI 兼容网关',
      where: '该厂商没有统一域名，<b>网关地址必须填写</b>' +
        '（DeepSeek 填 https://api.deepseek.com/v1，通义填 https://dashscope.aliyuncs.com/compatible-mode/v1）。',
      endpointPh: '如 deepseek-chat / qwen-max',
      basePh: 'https://api.deepseek.com/v1'
    }
  };

  /** 弹层当前编辑的模型（打开时快照） @type {Object|null} */
  var mdlgModel = null;

  /** 关闭后焦点归还的目标 @type {Element|null} */
  var mdlgReturnFocus = null;

  /**
   * 创建模型接入弹层骨架（幂等）。
   *
   * <p>与用户编辑弹层一样必须挂 {@code <body>}：保存后会 {@code loadAll()}
   * 整块重渲染 {@code #page-body}，挂在里面会被连带删掉。
   *
   * @returns {void}
   */
  function ensureModelDialog() {
    if ($('#model-dlg').length) { return; }

    $('body').append(
      '<div class="zdlg-mask" id="model-dlg" hidden>' +
        '<div class="zdlg" role="dialog" aria-modal="true" aria-labelledby="model-dlg-title">' +
          '<div class="zdlg-head">' +
            '<div>' +
              '<div class="zdlg-title" id="model-dlg-title">接入大模型</div>' +
              '<div class="zdlg-sub" id="model-dlg-sub"></div>' +
            '</div>' +
            '<button class="zdlg-x" type="button" data-dlg-close aria-label="关闭">' +
              '<svg width="11" height="11" viewBox="0 0 12 12" fill="none" aria-hidden="true">' +
                '<path d="M1.5 1.5l9 9M10.5 1.5l-9 9" stroke="currentColor" stroke-width="1.5" ' +
                'stroke-linecap="round"/></svg>' +
            '</button>' +
          '</div>' +
          '<div class="zdlg-body">' +
            '<div class="zdlg-note" id="model-dlg-where"></div>' +
            '<div class="field"><span class="field-label">API Key</span>' +
              '<input class="input" id="model-dlg-key" type="password" autocomplete="off" ' +
              'maxlength="200"></div>' +
            '<div class="field"><span class="field-label">推理接入点 / 模型 ID</span>' +
              '<input class="input" id="model-dlg-endpoint" type="text" autocomplete="off" ' +
              'maxlength="120"></div>' +
            '<div class="field"><span class="field-label">网关地址（选填）</span>' +
              '<input class="input" id="model-dlg-base" type="text" autocomplete="off" ' +
              'maxlength="200"></div>' +
            '<div class="zdlg-msg" id="model-dlg-msg" hidden></div>' +
          '</div>' +
          '<div class="zdlg-foot">' +
            '<button class="zbtn is-danger is-spread" type="button" id="model-dlg-disconnect" hidden>' +
              '断开接入</button>' +
            '<button class="zbtn" type="button" data-dlg-close>取消</button>' +
            '<button class="zbtn is-primary" type="button" id="model-dlg-save">保存并接入</button>' +
          '</div>' +
        '</div>' +
      '</div>'
    );

    $('#model-dlg').on('click', '[data-dlg-close]', closeModelDialog);
    $('#model-dlg').on('click', function (e) {
      if (e.target === this) { closeModelDialog(); }
    });
    $('#model-dlg-save').on('click', saveModelDialog);
    $('#model-dlg-disconnect').on('click', function () {
      var $b = $(this);
      /* 二次确认用"再点一次"而不是 window.confirm：后者样式与本项目完全不一致，
         而且会打断键盘流。3 秒内不再点就自动复原。 */
      if ($b.attr('data-armed') !== '1') {
        $b.attr('data-armed', '1').text('再次点击确认断开');
        setTimeout(function () {
          $b.attr('data-armed', '0').text('断开接入');
        }, 3000);
        return;
      }
      $b.attr('data-armed', '0').text('断开接入').prop('disabled', true);
      /* 名字必须先存下来：closeModelDialog() 会把 mdlgModel 置空，
         之后再读 mdlgModel.name 会抛 TypeError，toast 永远出不来。 */
      var name = mdlgModel.name;
      ZFZ.api.disconnectModel(mdlgModel.key).then(function () {
        closeModelDialog();
        ZFZ.layout.toast('「' + name + '」已断开接入');
        loadAll();
      }).catch(function (err) {
        $b.prop('disabled', false);
        mdlgMsg('断开失败：' + err.message, 'is-error');
      });
    });
    $('#model-dlg-key').on('keydown', function (e) {
      if (e.key === 'Enter') { saveModelDialog(); }
    });
    $(document).on('keydown.zfzModelDlg', function (e) {
      if (e.key === 'Escape' && !$('#model-dlg').prop('hidden')) { closeModelDialog(); }
    });
  }

  /**
   * 弹层内提示（传空则隐藏）。
   *
   * @param {string} text 文案
   * @param {string} [kind] 'is-error'（默认红）/ 'is-warn' / 'is-ok'
   * @returns {void}
   */
  function mdlgMsg(text, kind) {
    var $m = $('#model-dlg-msg');
    $m.removeClass('is-ok is-warn');
    if (kind === 'is-ok') { $m.addClass('is-ok'); }
    if (kind === 'is-warn') { $m.addClass('is-warn'); }
    if (!text) { $m.prop('hidden', true).text(''); return; }
    $m.text(text).prop('hidden', false);
  }

  /**
   * 打开模型接入弹层。
   *
   * @param {Object} m 从表格行取出的模型信息
   * @param {Element} [trigger] 触发按钮，关闭时用来归还焦点
   * @returns {void}
   */
  function openModelDialog(m, trigger) {
    ensureModelDialog();
    mdlgModel = m;
    /* 焦点归还必须记触发元素：程序化 click() 不会给按钮聚焦，
       此时 document.activeElement 是 <body>，归还后焦点就丢了。 */
    mdlgReturnFocus = trigger || document.activeElement;

    var info = PROVIDER_INFO[m.provider] || PROVIDER_INFO['openai-compatible'];
    var hasKey = m.keyConfigured === '1';

    /* provider 是 openai-compatible 时没有专属文案，此时副标题若还写"OpenAI 兼容网关"，
       会出现"DeepSeek-V3 · OpenAI 兼容网关"这种对不上的标签；直接退回模型名。 */
    var label = PROVIDER_INFO[m.provider] ? info.label : m.name;
    $('#model-dlg-sub').text(label + ' · 当前状态：' + m.status);
    $('#model-dlg-where').html(info.where);
    $('#model-dlg-key')
      .val('')
      .attr('placeholder', hasKey ? '已保存密钥；留空表示不修改' : '粘贴控制台生成的 API Key');
    $('#model-dlg-endpoint').val(m.endpoint || '').attr('placeholder', info.endpointPh);
    $('#model-dlg-base').val(m.base || '').attr('placeholder', info.basePh);
    $('#model-dlg-disconnect').prop('hidden', !hasKey)
      /* disabled 必须显式复位：断开成功时会把按钮置灰（防重复提交），
         而弹层是四个模型共用的**同一个实例** —— 不复位的话，
         断开过一次之后，其余模型的断开按钮就永远点不动了。 */
      .prop('disabled', false)
      .attr('data-armed', '0').text('断开接入');
    $('#model-dlg-save').prop('disabled', false).text('保存并接入');
    mdlgMsg('');

    $('#model-dlg').prop('hidden', false);
    $('body').addClass('is-dlg-open');
    /* 已配置时焦点落在接入点（改配置比改 Key 更常见），否则落在 Key */
    $(hasKey ? '#model-dlg-endpoint' : '#model-dlg-key').trigger('focus');
  }

  /**
   * 关闭弹层并归还焦点。
   * @returns {void}
   */
  function closeModelDialog() {
    if ($('#model-dlg').prop('hidden')) { return; }
    $('#model-dlg').prop('hidden', true);
    $('body').removeClass('is-dlg-open');
    var key = mdlgModel ? mdlgModel.key : null;
    mdlgModel = null;

    /* 触发按钮可能已经被 loadAll() 换掉了（保存/断开成功后都会重渲染 #page-body），
       旧节点脱离了文档，focus() 是空操作、焦点会掉到 <body>。
       所以先检查节点是否还在文档里，不在就按 modelKey 找回同一行的新按钮。 */
    var node = mdlgReturnFocus;
    if (node && key && !document.contains(node)) {
      var list = document.querySelectorAll('[data-model-key]');
      node = null;
      for (var i = 0; i < list.length; i++) {
        if (list[i].getAttribute('data-model-key') === key) { node = list[i]; break; }
      }
    }
    if (node && node.focus && document.contains(node)) { node.focus(); }
    mdlgReturnFocus = null;
  }

  /**
   * 保存并接入：先落库再验证，验证结果如实回显。
   *
   * <p>后端返回 {@code ok=false} 时<b>凭据已经保存了</b>，只是用不了。
   * 这种情况不能当"保存失败"简单报错，否则管理员会反复重填同一把 Key；
   * 提示必须写明"已保存，但验证未通过 + 原因"。
   *
   * @returns {void}
   */
  function saveModelDialog() {
    if (!mdlgModel) { return; }
    var m = mdlgModel;
    var apiKey = $.trim($('#model-dlg-key').val() || '');
    var endpointId = $.trim($('#model-dlg-endpoint').val() || '');
    var baseUrl = $.trim($('#model-dlg-base').val() || '');
    var hasKey = m.keyConfigured === '1';

    if (!hasKey && !apiKey) { mdlgMsg('请填写 API Key'); return; }
    if (!endpointId) { mdlgMsg('请填写推理接入点 / 模型 ID'); return; }
    if (baseUrl && !/^https?:\/\//i.test(baseUrl)) {
      mdlgMsg('网关地址需以 http:// 或 https:// 开头');
      return;
    }

    var $save = $('#model-dlg-save').prop('disabled', true).text('接入中…');
    mdlgMsg('正在向厂商发起一次最小请求验证连通性，通常 1–5 秒…', 'is-warn');

    ZFZ.api.connectModel(m.key, {
      apiKey: apiKey,
      endpointId: endpointId,
      baseUrl: baseUrl
    }).then(function (r) {
      var data = r || {};
      if (data.ok) {
        closeModelDialog();
        ZFZ.layout.toast('「' + m.name + '」接入成功，耗时 ' + (data.latencyMs || '—') + ' ms');
        loadAll();
        return;
      }
      /* 凭据已落库但验证失败：保持弹层打开，让管理员就地改 */
      $save.prop('disabled', false).text('保存并接入');
      m.keyConfigured = (data.keyConfigured ? '1' : '0');
      mdlgMsg('凭据已保存，但验证未通过：' + (data.message || '未知原因'), 'is-error');
      $('#model-dlg-disconnect').prop('hidden', m.keyConfigured !== '1');
      loadAll();
    }).catch(function (err) {
      $save.prop('disabled', false).text('保存并接入');
      mdlgMsg('接入失败：' + err.message);
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
  /*  五、鉴权态：请先登录 / 无权限 / 错误                                     */
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
  /*  六、渲染与交互                                                          */
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
    /* 存一份原始列表：编辑弹层要回填 nickname / email / phone，
       这些字段没有渲染进表格，只存在于接口响应里。 */
    lastUsers = (users && users.items) || [];

    /* 整块替换 #page-body 会销毁当前焦点所在的节点，焦点随之掉到 <body>。
       典型案例：弹层里点「保存」→ 接口返回 → 关弹层归还焦点 → 这边才拿到数据重渲染，
       刚还回去的焦点又被换掉。先按 data-* 身份记住"焦点在哪一行的哪个按钮"，画完再找回来。 */
    var keepFocus = describeFocus();

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
    restoreFocus(keepFocus);
  }

  /**
   * 记录当前焦点所在的页内元素，供重渲染后还原。
   *
   * <p>用 `data-*` 身份而不是节点引用 —— 重渲染后节点全换，引用必失效。
   * 只在焦点确实位于 `#page-body` 内部时才记录：弹层挂在 `<body>` 上，
   * 不在重渲染范围内，它的焦点不需要（也不应该）被这里接管。
   *
   * @returns {?Object} `{attr, value}` 或 `{id}`；无可还原焦点时返回 null
   */
  function describeFocus() {
    var el = document.activeElement;
    if (!el || el === document.body || !$.contains(document.getElementById('page-body'), el)) {
      return null;
    }
    var attrs = ['data-model-key', 'data-action', 'data-account'];
    for (var i = 0; i < attrs.length; i++) {
      var v = el.getAttribute(attrs[i]);
      if (v) { return { attr: attrs[i], value: v }; }
    }
    return el.id ? { id: el.id } : null;
  }

  /**
   * 按 {@link describeFocus} 的记录把焦点找回来。
   *
   * <p>找不到对应元素时静默跳过：列表内容变了（比如那条待办刚被处理掉），
   * 强行聚焦到"同位置"的其它按钮比不聚焦更误导人。
   *
   * @param {?Object} spec 焦点身份
   * @returns {void}
   */
  function restoreFocus(spec) {
    if (!spec) { return; }
    var el = null;
    if (spec.id) {
      el = document.getElementById(spec.id);
    } else {
      var list = document.querySelectorAll('[' + spec.attr + ']');
      for (var i = 0; i < list.length; i++) {
        if (list[i].getAttribute(spec.attr) === spec.value) { el = list[i]; break; }
      }
    }
    if (el && el.focus && document.contains(el)) { el.focus(); }
  }

  /**
   * 绑定页内交互：模型配置提示 + 用户操作列（禁用/启用、重置密码）。
   * 每次重渲染 #page-body 后需重新绑定（旧节点已随 DOM 移除）。
   * @returns {void}
   */
  function bind() {
    /* 大模型接入：打开接入弹层（填 Key / 接入点 / 网关，保存后立即验证）。
       注意一律用 .attr() 读 data-* —— jQuery 的 .data() 会缓存首次值，
       列表重渲染后同一节点若复用会读到旧数据。 */
    $('[data-model-key]').on('click', function () {
      var $b = $(this);
      openModelDialog({
        key: $b.attr('data-model-key'),
        name: $b.attr('data-model-name'),
        status: $b.attr('data-model-status'),
        provider: $b.attr('data-model-provider'),
        endpoint: $b.attr('data-model-endpoint'),
        base: $b.attr('data-model-base'),
        keyConfigured: $b.attr('data-model-key-ok')
      }, this);
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
        openUserDialog(findUser(id), this);
        return;
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
      /* 记下自己的 id：用户表里"自己"那一行的角色下拉要锁住 */
      meId = (me && me.userId != null) ? String(me.userId) : null;
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
