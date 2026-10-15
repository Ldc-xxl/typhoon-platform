/* ==========================================================================
   登录 / 注册页
   --------------------------------------------------------------------------
   为什么要有这个页面：项目原先**没有独立登录页** —— 登录表单内嵌在
   admin.html 的「请先登录」卡片里（由 js/pages/admin.js 渲染）。于是出现两个
   具体后果：普通用户被顶栏写着「请登录」却找不到任何登录入口；而且"登录"在
   语义上被绑死在管理后台，看起来像只有管理员才需要登录。

   本页把登录/注册提到一等的独立入口，admin.html 的内嵌卡片继续保留
   （它服务于"直接访问管理后台"这条路径，且已有自动断言依赖它）。

   接口（全部走 ZFZ.api，失败即如实提示，不回退任何本地假数据）：
     登录     ZFZ.api.login(username, password)                POST /api/auth/login
     注册     ZFZ.api.register({username, password,
                                nickname?, email?})            POST /api/auth/register
     退出     ZFZ.api.logout()                                 POST /api/auth/logout
     会话     ZFZ.layout.sessionStatus() / currentUser()        由 /api/auth/me 驱动

   表单的 HTML 由 ZFZ.ui.authForm() 产出（纯函数层），本页只负责三件事：
   切模式、校验、提交。表单里那几个 id（login-account / login-password /
   login-password2 / login-submit …）是 ui.js 的对外契约，改不得。
   ========================================================================== */
(function ($) {
  'use strict';

  var U = ZFZ.ui;

  /**
   * 允许作为 ?next= 落点的站内页面白名单。
   *
   * 为什么要白名单而不是"直接用 next 参数"：`?next=` 是攻击者可控的输入，
   * 若原样 `location.href = next`，那么
   * `login.html?next=https://evil.example.com` 就能把人从本站骗走 ——
   * 而由于跳转发生在"刚登录成功"这一瞬间，受害者几乎不会怀疑。
   * 所以只认下面这些本工程真实存在的页面文件名，其余一律回退首页。
   * @type {Array<string>}
   */
  var NEXT_ALLOW = [
    'index.html', 'archive.html', 'admin.html', 'sandbox.html', 'survey.html',
    'help-api.html', 'risk-assessment.html', 'forecast-disagreement.html',
    'similar-cases.html', 'typhoon-detail.html'
  ];

  /** 登录成功后的默认落点 */
  var HOME = 'index.html';

  /* ======================================================================== */
  /*  一、地址栏工具                                                          */
  /* ======================================================================== */

  /**
   * 读取 ?next= 并做同源白名单校验。
   * @returns {string} 合法的站内相对地址；不合法或缺省时为 'index.html'
   */
  function nextTarget() {
    var raw = null;
    try { raw = new URLSearchParams(location.search).get('next'); } catch (e) { raw = null; }
    if (!raw) { return HOME; }

    /* 拆掉可能带的查询串与锚点，再和文件名比对 */
    var name = String(raw).split('#')[0].split('?')[0];
    if (name.indexOf('./') === 0) { name = name.slice(2); }
    if (NEXT_ALLOW.indexOf(name) < 0) { return HOME; }
    return name;
  }

  /**
   * 从地址栏判断初始应展示哪个表单。
   * layout.js 的用户菜单里，「注册」链接写死的是 `login.html#register`，
   * 所以这个字面量 `#register` 是对外约定，不能改成别的。
   * @returns {string} 'login' 或 'register'
   */
  function modeFromHash() {
    return location.hash === '#register' ? 'register' : 'login';
  }

  /* ======================================================================== */
  /*  二、卡片提示                                                            */
  /* ======================================================================== */

  /**
   * 在表单上方显示一条提示。
   *
   * 刻意"就地改文本"而不是重渲染整个卡片：重渲染会把用户已经填好的
   * 账号/密码清空，而注册失败最常见的原因（用户名被占用）恰好只需要
   * 改一下用户名，清空输入是纯粹的帮倒忙。
   *
   * @param {string} text 提示文案
   * @param {string} [cls] 'is-error' 显示为红底
   */
  function showMsg(text, cls) {
    var $m = $('#page-body .auth-msg');
    if (!$m.length) {
      $m = $('<div class="auth-msg"></div>').insertBefore('#page-body #auth-form');
    }
    $m.attr('class', 'auth-msg' + (cls ? ' ' + cls : '')).text(text).show();
  }

  /** 清掉提示（切换模式、或重新提交时调用） */
  function clearMsg() {
    var $m = $('#page-body .auth-msg');
    if ($m.length) { $m.attr('class', 'auth-msg').text('').hide(); }
  }

  /* ======================================================================== */
  /*  三、模式切换                                                            */
  /* ======================================================================== */

  /**
   * 在「登录」与「注册」之间切换。
   *
   * 需要同步四样东西，缺一样界面就会自相矛盾（例如按钮写着"注册"、
   * 字段却还是登录的那两个）。注意 `data-mode` 必须用 .attr() 读写 ——
   * jQuery 的 .data() 会缓存首次读到的值，之后的 .attr() 改写它读不到。
   *
   * @param {string} mode 'login' | 'register'
   */
  function setMode(mode) {
    var isReg = mode === 'register';

    $('#auth-form').attr('data-mode', isReg ? 'register' : 'login');
    /* 注册专属字段容器（带 data-auth-register）：显示 / 隐藏 */
    $('#page-body [data-auth-register]').toggle(isReg);

    $('#login-account-label').text(isReg ? '用户名' : '登录账号');
    $('#login-tab-login').toggleClass('is-active', !isReg);
    $('#login-tab-register').toggleClass('is-active', isReg);
    $('#login-submit').text(isReg ? '注册' : '登录');

    /* 底部切换行。href 与 layout.js 用户菜单里的两个链接保持同一套约定 */
    if (isReg) {
      $('#login-switch').html('已有账号？<a href="login.html" id="login-switch-link">去登录</a>');
    } else {
      $('#login-switch').html('还没有账号？<a href="login.html#register" id="login-switch-link">立即注册</a>');
    }

    clearMsg();

    /* 同步地址栏，让链接可分享、可收藏、浏览器后退也能回到对的表单。
       用 history.replaceState 而不是赋值 location.hash：后者会往历史里塞记录，
       用户点一次页签就多一条历史，按返回时要按好几下才出得去。 */
    try {
      var want = isReg ? '#register' : '';
      if (history.replaceState) {
        history.replaceState(null, '', location.pathname + location.search + want);
      } else if (location.hash !== want) {
        location.hash = want;
      }
    } catch (e) { /* 沙箱或 file:// 下可能被拒，忽略即可 */ }
  }

  /* ======================================================================== */
  /*  四、表单渲染                                                            */
  /* ======================================================================== */

  /** 渲染登录/注册卡片（首次进入时用） */
  function renderForm() {
    $('#page-body').html(
      '<div class="colstack col-main">' +
        '<div class="auth-wrap">' +
          U.authForm({ mode: modeFromHash() }) +
        '</div>' +
      '</div>'
    );
  }

  /**
   * 已知登录身份时展示的"你已登录"卡片。
   *
   * 不重复渲染登录表单：已经登录的人再看到账号密码框，第一反应是
   * "我是不是掉线了"，然后用同一个账号再登一次 —— 纯属浪费。
   *
   * @param {string} name 当前登录身份（昵称或账号）
   */
  function renderSignedIn(name) {
    var body =
      U.note(U.esc('当前登录身份：' + name + '。无需重复登录。可直接返回首页，' +
        '或退出后换一个账号登录。'), 'is-plain') +
      '<div class="zbtn-row is-inline auth-hint">' +
        '<button class="zbtn is-primary" type="button" id="auth-go-home">返回首页</button>' +
        '<button class="zbtn" type="button" id="auth-do-logout">退出登录</button>' +
      '</div>';

    $('#page-body').html(
      '<div class="colstack col-main">' +
        '<div class="auth-wrap">' +
          '<div class="auth-card">' +
            U.panel({ title: '你已登录', sub: '无需重复登录', cls: 'is-tight', body: body }) +
          '</div>' +
        '</div>' +
      '</div>'
    );
  }

  /* ======================================================================== */
  /*  五、提交                                                                */
  /* ======================================================================== */

  /**
   * 前端校验。
   * 只拦"显然通不过"的输入，不代替后端校验 —— 后端才是权威
   * （用户名规则、重名、邮箱格式在 AuthService 里都会再判一次）。
   *
   * @param {string} mode  'login' | 'register'
   * @param {string} account 账号
   * @param {string} pw  密码
   * @param {string} pw2 确认密码（仅注册用）
   * @param {string} mail 邮箱（仅注册用，选填）
   * @returns {string} 错误文案；通过则返回空串
   */
  function validate(mode, account, pw, pw2, mail) {
    if (!account) { return '请输入登录账号'; }
    if (!pw) { return '请输入密码'; }
    if (mode === 'register') {
      if (pw.length < 6) { return '密码至少 6 位'; }
      if (pw !== pw2) { return '两次输入的密码不一致'; }
      if (mail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) { return '邮箱格式不正确'; }
    }
    return '';
  }

  /** 读表单当前值 */
  function readForm() {
    return {
      mode: $('#auth-form').attr('data-mode') === 'register' ? 'register' : 'login',
      account: $.trim($('#login-account').val() || ''),
      pw: $('#login-password').val() || '',
      pw2: $('#login-password2').val() || '',
      nickname: $.trim($('#login-nickname').val() || ''),
      email: $.trim($('#login-email').val() || '')
    };
  }

  /** 提交登录或注册 */
  function submit() {
    var f = readForm();
    var isReg = f.mode === 'register';
    var verb = isReg ? '注册' : '登录';

    var bad = validate(f.mode, f.account, f.pw, f.pw2, f.email);
    if (bad) { showMsg(bad, 'is-error'); return; }

    clearMsg();
    var $btn = $('#login-submit').prop('disabled', true).text(verb + '中…');

    var pending = isReg
      ? ZFZ.api.register({
          username: f.account,
          password: f.pw,
          nickname: f.nickname,
          email: f.email
        })
      : ZFZ.api.login(f.account, f.pw);

    pending.then(function () {
      ZFZ.layout.toast(verb + '成功，正在进入…');
      /* 顶栏身份是页面加载时那一次 /api/auth/me 画上去的，那时还没登录；
         不重新探测的话，跳过去会看到"已登录、顶栏却写着未登录"的矛盾画面。 */
      if (ZFZ.layout.refreshSession) { ZFZ.layout.refreshSession(); }
      setTimeout(function () { location.href = nextTarget(); }, 350);
    }, function (err) {
      /* 业务失败也返回 HTTP 200，成败看响应体 code —— 所以这里只看 err.message
         （由 api.js 统一翻译），绝不去判 xhr.status。 */
      var why = (err && err.message) || '未知错误';
      showMsg(verb + '失败：' + why, 'is-error');
      ZFZ.layout.toast(verb + '失败：' + why);
      $btn.prop('disabled', false).text(verb);
    });
  }

  /* ======================================================================== */
  /*  六、事件绑定（全部委托到 #page-body，卡片重渲染后不失绑）                */
  /* ======================================================================== */

  function bindActions() {
    var $body = $('#page-body');

    $body.on('click', '#login-tab-login', function () { setMode('login'); });
    $body.on('click', '#login-tab-register', function () { setMode('register'); });
    $body.on('click', '#login-switch-link', function (e) {
      /* 让 <a> 的默认跳转不生效：本页内切换即可，没必要真的导航一次 */
      e.preventDefault();
      setMode($('#auth-form').attr('data-mode') === 'register' ? 'login' : 'register');
    });

    $body.on('click', '#login-submit', function () { submit(); });

    /* 回车即提交。密码框上尤其必要——登录页最自然的操作就是敲完密码按回车 */
    $body.on('keydown', '#login-account, #login-password, #login-password2', function (e) {
      if (e.key === 'Enter' || e.keyCode === 13) {
        e.preventDefault();
        submit();
      }
    });

    /* 已登录态的两个按钮 */
    $body.on('click', '#auth-go-home', function () { location.href = HOME; });
    $body.on('click', '#auth-do-logout', function () {
      var $btn = $(this).prop('disabled', true);
      ZFZ.api.logout().then(function () {
        ZFZ.layout.toast('已退出登录');
        setTimeout(function () { location.reload(); }, 300);
      }, function (err) {
        ZFZ.layout.toast('退出登录失败：' + ((err && err.message) || '未知错误'));
        $btn.prop('disabled', false);
      });
    });

    /* 浏览器前进/后退导致的 hash 变化：把表单切回对应模式，否则会出现
       "地址栏是 #register、画面却是登录表单"的不一致 */
    $(window).on('hashchange', function () {
      if (!$('#auth-form').length) { return; }
      setMode(modeFromHash());
    });
  }

  /* ======================================================================== */
  /*  七、入口                                                                */
  /* ======================================================================== */

  /**
   * 会话状态是**异步**探测的（layout.js 的 mount 里发起 /api/auth/me），
   * 所以"你已登录"这个判断不能同步做。这里轻量轮询若干次：
   * 一旦确认已登录就换成"你已登录"卡片，确认未登录则保持表单。
   * 超时也保持表单 —— 登录页在探测失败时仍应可用，不能因为一个
   * 辅助判断把主功能挡掉。
   */
  function watchSession() {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      var st = ZFZ.layout.sessionStatus ? ZFZ.layout.sessionStatus() : 'guest';

      if (st === 'user') {
        clearInterval(timer);
        var me = ZFZ.layout.currentUser() || {};
        var u = me.user || {};
        renderSignedIn(String(me.userName || u.nickname || me.account || '当前用户'));
        return;
      }
      if (st === 'guest' || st === 'error' || tries > 16) { clearInterval(timer); }
    }, 250);
  }

  $(function () {
    ZFZ.layout.mount('login');
    $('#page-title').html(ZFZ.layout.titleBar({
      title: '登录 / 注册',
      desc: '登录后可管理收藏、订阅与个人情景 · 管理员登录后可进入管理后台',
      center: true
    }));

    renderForm();
    bindActions();
    watchSession();
  });
})(window.jQuery);
