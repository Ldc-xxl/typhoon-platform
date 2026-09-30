/* ==========================================================================
   追风者 · 全局导航（App Header）
   设计稿对应组件：3:1「App Header / 全局导航」，1440×60，品牌区 + 9 项主导航 + 右侧功能区
   右侧功能区四件事均可用：品牌回首页 / 全局搜索 / 通知中心 / 用户菜单
   ========================================================================== */
window.ZFZ = window.ZFZ || {};

ZFZ.layout = (function () {
  'use strict';

  /* 导航顺序与设计稿一致；active 标识用于当前页高亮 */
  var NAV = [
    { key: 'overview',  label: '监测总览', href: 'index.html' },
    { key: 'archive',   label: '台风档案', href: 'archive.html' },
    { key: 'disagree',  label: '分歧解析', href: 'forecast-disagreement.html' },
    { key: 'risk',      label: '风险评估', href: 'risk-assessment.html' },
    { key: 'similar',   label: '相似案例', href: 'similar-cases.html' },
    { key: 'sandbox',   label: '推演沙盘', href: 'sandbox.html' },
    { key: 'survey',    label: '公众调研', href: 'survey.html' },
    { key: 'help',      label: '接口说明', href: 'help-api.html' },
    { key: 'admin',     label: '管理后台', href: 'admin.html' }
  ];

  /* 用户菜单：设计稿未画，按顶栏既有语义补全，均为站内已有页面 */
  var USER_MENU = [
    { label: '管理后台',   href: 'admin.html' },
    { label: '接口说明',   href: 'help-api.html' },
    { label: '台风档案',   href: 'archive.html' },
    { label: '退出登录',   action: 'logout', danger: true }
  ];

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function buildHeader(activeKey) {
    var nav = NAV.map(function (n) {
      var cls = 'navlink' + (n.key === activeKey ? ' is-active' : '');
      return '<a class="' + cls + '" href="' + n.href + '">' + n.label + '</a>';
    }).join('');

    return '' +
      '<a class="brand" href="index.html" title="返回监测总览">' +
        ZFZ.ui.icons.logo +
        '<div class="brand-text">' +
          '<span class="brand-name">追风者</span>' +
          '<span class="brand-sub">台风实况监测与灾害辅助分析平台</span>' +
        '</div>' +
      '</a>' +
      '<nav class="main-nav">' + nav + '</nav>' +
      '<div class="header-tools">' +

        /* ---- 全局搜索 ---- */
        '<div class="hdrop hs-search" data-drop="search">' +
          '<label class="search-box">' +
            ZFZ.ui.icons.search +
            '<input type="text" id="global-search" autocomplete="off" ' +
              'placeholder="搜索台风 / 编号 / 年份">' +
          '</label>' +
          '<div class="drop-panel is-search"><div class="drop-body"></div></div>' +
        '</div>' +

        /* ---- 通知中心 ---- */
        '<div class="hdrop hs-notice" data-drop="notice">' +
          '<button class="icon-btn" type="button" aria-label="通知">' +
            ZFZ.ui.icons.bell +
            '<i class="dot-badge"></i>' +
          '</button>' +
          '<div class="drop-panel is-notice">' +
            '<div class="drop-head"><span class="drop-title">通知中心</span>' +
              '<span class="drop-sub"></span></div>' +
            '<div class="drop-body"></div>' +
          '</div>' +
        '</div>' +

        /* ---- 用户菜单 ---- */
        /* 身份信息由 /api/auth/me 回填（见 loadSession/paintUser）。
           这里给中性占位而不是写死"总管理员"，避免未登录时闪出错误的身份。 */
        '<div class="hdrop hs-user" data-drop="user">' +
          '<button class="user-chip" type="button" aria-haspopup="menu">' +
            '<span class="avatar">·</span>' +
            '<span class="user-name">加载中…</span>' +
          '</button>' +
          '<div class="drop-panel is-user">' +
            '<div class="drop-hero">' +
              '<span class="avatar is-lg">·</span>' +
              '<div class="dh-text">' +
                '<span class="dh-name">加载中…</span>' +
                '<span class="dh-sub">正在获取登录信息</span>' +
              '</div>' +
            '</div>' +
            '<div class="drop-body">' +
              USER_MENU.map(function (m) {
                if (m.action) {
                  return '<button class="drop-item' + (m.danger ? ' is-danger' : '') +
                    '" type="button" data-action="' + m.action + '">' + m.label + '</button>';
                }
                return '<a class="drop-item" href="' + m.href + '">' + m.label + '</a>';
              }).join('') +
            '</div>' +
          '</div>' +
        '</div>' +

      '</div>';
  }

  /* ---------------- 下拉面板开合（互斥） ---------------- */
  function closeAll(except) {
    document.querySelectorAll('.hdrop.is-open').forEach(function (d) {
      if (d !== except) { d.classList.remove('is-open'); }
    });
  }
  function toggle(host) {
    var open = host.classList.contains('is-open');
    closeAll();
    if (!open) { host.classList.toggle('is-open', true); }
  }

  /* ---------------- 全局搜索 ----------------
     台风清单只在首次展开搜索面板时拉取一次并缓存，之后每次输入都在本地过滤 ——
     避免每敲一个字符就打一次接口。                                       */
  var searchPool = null;   /* null = 尚未拉取；数组 = 已拉取（可能为空） */
  var searchLoading = false;
  var searchError = '';

  function searchBody() {
    return document.querySelector('.hs-search .drop-body');
  }

  function paintSearch(kw) {
    var body = searchBody();
    if (!body) { return; }
    kw = String(kw || '').trim().toLowerCase();

    var all = searchPool || [];
    var hits;
    if (!kw) {
      /* 空关键词：活跃台风优先，其后补最近的档案，作为快捷入口 */
      hits = all.filter(function (t) { return t.status === 'ACTIVE'; })
                .concat(all.filter(function (t) { return t.status !== 'ACTIVE'; }))
                .slice(0, 6);
    } else {
      hits = all.filter(function (t) {
        return String(t.nameCn || '').toLowerCase().indexOf(kw) >= 0 ||
               String(t.nameEn || '').toLowerCase().indexOf(kw) >= 0 ||
               String(t.code || '').indexOf(kw) >= 0 ||
               String(t.year || '').indexOf(kw) >= 0;
      }).slice(0, 8);
    }

    if (!hits.length) {
      body.innerHTML = '<div class="drop-empty">未找到匹配「' + esc(kw) + '」的台风</div>';
      return;
    }

    body.innerHTML = hits.map(function (t) {
      /* 分级换算由组合库提供，不再依赖演示数据层 */
      var lv = ZFZ.ui.levelOf(t.windLevel);
      return '<a class="sr-item" href="typhoon-detail.html?id=' + t.id + '">' +
        '<span class="sr-name">' + esc(t.nameCn) + '</span>' +
        '<span class="sr-code">' + esc(t.code) + '</span>' +
        '<span class="tag ' + lv.cls + '">' + esc(lv.name) + '</span>' +
        (t.status === 'ACTIVE' ? '<span class="sr-live">活跃中</span>' : '') +
      '</a>';
    }).join('');
  }

  function renderSearch(kw) {
    var body = searchBody();
    if (!body) { return; }

    /* 已拉取过（含失败）：直接本地过滤，不再打接口 */
    if (searchPool !== null) { paintSearch(kw); return; }
    if (searchLoading) { return; }

    if (!ZFZ.api) {
      body.innerHTML = '<div class="drop-empty">接口层未加载，无法检索台风</div>';
      return;
    }

    searchLoading = true;
    body.innerHTML = '<div class="drop-empty">正在加载台风清单…</div>';

    ZFZ.api.getTyphoons({ limit: 200 }).then(function (list) {
      searchPool = list || [];
    }, function (err) {
      searchPool = [];                              /* 失败也标记为已拉取，避免反复重试 */
      searchError = (err && err.message) || '未知错误';
    }).then(function () {
      searchLoading = false;
      if (searchError) {
        body.innerHTML = '<div class="drop-empty">台风清单加载失败：' + esc(searchError) + '</div>';
      } else {
        paintSearch(kw);
      }
    });
  }

  /* ---------------- 通知中心（需登录，未登录由后端返回 401） ---------------- */
  function noticeEmpty(text) {
    return '<div class="drop-empty">' + esc(text) + '</div>';
  }

  /* 后端对业务失败统一返回 HTTP 200 + 响应体 code，
     因此"未登录"要判 err.code 而不是 HTTP 状态码（err.xhr.status 恒为 200）。 */
  function isUnauthorized(err) {
    if (!err) { return false; }
    return err.code === 401 || (err.xhr && err.xhr.status === 401);
  }

  function renderNotice() {
    var body = document.querySelector('.hs-notice .drop-body');
    var sub = document.querySelector('.hs-notice .drop-sub');
    var badge = document.querySelector('.hs-notice .dot-badge');
    if (!body) { return; }

    if (!ZFZ.api) {
      body.innerHTML = noticeEmpty('接口层未加载，无法获取通知');
      return;
    }

    body.innerHTML = noticeEmpty('正在加载通知…');

    ZFZ.api.getNotifications().then(function (res) {
      var list = (res && res.items) || [];
      var unread = Number(res && res.unread) || 0;

      if (sub) { sub.textContent = list.length + ' 条'; }
      if (badge) { badge.classList.toggle('is-cleared', unread === 0); }

      if (!list.length) { body.innerHTML = noticeEmpty('暂无通知'); return; }

      body.innerHTML = list.map(function (n) {
        return '<a class="nt-item level-' + esc(n.level) + '" href="' + esc(n.href || '#') + '">' +
          '<span class="nt-bar"></span>' +
          '<span class="nt-main">' +
            '<span class="nt-title">' + esc(n.title) + '</span>' +
            '<span class="nt-desc">' + esc(n.desc) + '</span>' +
          '</span>' +
          '<span class="nt-time">' + esc(n.time) + '</span>' +
        '</a>';
      }).join('');
    }, function (err) {
      if (sub) { sub.textContent = '—'; }
      if (badge) { badge.classList.add('is-cleared'); }

      body.innerHTML = noticeEmpty(isUnauthorized(err)
        ? '登录后可查看通知'
        : ('通知加载失败：' + ((err && err.message) || '未知错误')));
    });
  }

  /* ---------------- 顶栏用户信息（登录后显示真实账号） ----------------
     /api/auth/me 的返回是 { user: {...}, userId, account, role, roleText, avatarText, ... }：
     昵称嵌在 user.nickname 里、顶层没有 userName，所以两处都要取。 */
  function paintUser(me) {
    if (!me) { return; }
    var u = me.user || {};
    var name = me.userName || u.nickname || me.nickname || me.account || '未登录';
    var initial = me.avatarText || u.avatarText || String(name).charAt(0);
    var role = me.roleText || me.role || '';
    var mail = u.email || '';
    var sub = role ? (mail ? role + ' · ' + mail : role) : (mail || '');

    var chip = document.querySelector('.hs-user .user-chip');
    if (chip) {
      var av = chip.querySelector('.avatar');
      var nm = chip.querySelector('.user-name');
      if (av) { av.textContent = initial; }
      if (nm) { nm.textContent = name; }
    }
    var hero = document.querySelector('.hs-user .drop-hero');
    if (hero) {
      var avLg = hero.querySelector('.avatar');
      var nmEl = hero.querySelector('.dh-name');
      var sbEl = hero.querySelector('.dh-sub');
      if (avLg) { avLg.textContent = initial; }
      if (nmEl) { nmEl.textContent = name; }
      if (sbEl) { sbEl.textContent = sub; }
    }
  }

  /* ---------------- 登录态（真实会话，由 /api/auth/me 决定） ----------------
     原先用 localStorage 记一个 'zfz.session' 标记来"假装"登录，与后端会话完全脱节：
     退出登录只是改本地标记、Cookie 里的 JSESSIONID 依然有效，刷新后仍是登录态。
     现改为完全以服务端为准：未登录时 /api/auth/me 返回 401，属正常分支，不视为故障。 */
  var session = null;          /* null = 未登录；对象 = 当前登录用户 */

  /* 会话状态：'unknown' 未探测 / 'user' 已登录 / 'guest' 确实未登录 / 'error' 探测失败。
     之所以要分开 'guest' 与 'error'：/api/auth/me 失败的原因不止一种 ——
     401 是"没登录"的正常表达，而超时、连接池繁忙、服务端 5xx 是故障。
     两者混为一谈的后果很具体：用户明明登录着，顶栏却写"未登录"，
     于是被误导着重登一遍，而真正的问题（网络异常）被掩盖。 */
  var sessionState = 'unknown';

  /** 画成"确实未登录" */
  function paintGuest() {
    session = null;
    sessionState = 'guest';
    paintUser({ userName: '未登录', avatarText: '访', roleText: '登录后可管理收藏与订阅' });
  }

  /** 画成"身份探测失败"——如实说明，不谎称未登录 */
  function paintSessionError() {
    session = null;
    sessionState = 'error';
    paintUser({ userName: '连接异常', avatarText: '!', roleText: '身份校验未完成，请刷新重试' });
  }

  /**
   * 拉取当前会话并刷新顶栏。
   *
   * @param {boolean} [retryOnTransient] 首次失败且原因不是 401 时是否自动重试一次。
   *        页面刚加载时可能正赶上服务端繁忙，一次重试能消除绝大多数误判；
   *        重试仍失败才落成"连接异常"，避免把偶发抖动永久写死在界面上。
   */
  function loadSession(retryOnTransient) {
    if (!ZFZ.api) { return; }
    ZFZ.api.getMe().then(function (me) {
      session = me || null;
      sessionState = 'user';
      paintUser(session || {});
    }, function (err) {
      if (isUnauthorized(err)) {
        paintGuest();                       /* 401：未登录，属正常分支 */
        return;
      }
      if (retryOnTransient !== false) {
        setTimeout(function () { loadSession(false); }, 600);
        return;
      }
      paintSessionError();                  /* 真正的故障：如实提示 */
    });
  }

  /** 供登录成功后主动刷新顶栏（否则要等用户手动刷新页面才会变） */
  function refreshSession() { return loadSession(true); }

  function currentUser() { return session; }

  /** 当前会话状态，取值见 sessionState 注释 */
  function sessionStatus() { return sessionState; }

  /* ---------------- 通知红点：只取未读数（轻量），列表等展开面板时再拉 ---------------- */
  /* 通知红点的三态刷新。
     旧实现把任何失败都画成"已读"（加 is-cleared 隐藏红点），
     接口挂掉时表现成"没有未读消息"，用户完全看不出异常 —— 属静默降级。
     现在按失败原因分开处理：
       · 401 未登录      → 确实不该显示未读，隐藏红点
       · 其它失败/异常   → 未知态：空心灰点 + title 说明，并且不去猜数字 */
  function refreshUnread() {
    var badge = document.querySelector('.hs-notice .dot-badge');
    if (!badge || !ZFZ.api) { return; }
    ZFZ.api.getUnreadCount().then(function (res) {
      var n = Number(res && res.unread) || 0;
      badge.classList.remove('is-error');
      badge.classList.toggle('is-cleared', n === 0);
      badge.removeAttribute('title');
      badge.setAttribute('aria-label', n === 0 ? '没有未读通知' : ('有 ' + n + ' 条未读通知'));
    }, function (err) {
      if (isUnauthorized(err)) {
        badge.classList.remove('is-error');
        badge.classList.add('is-cleared');
        badge.removeAttribute('title');
        badge.setAttribute('aria-label', '登录后可查看未读通知');
        return;
      }
      badge.classList.remove('is-cleared');
      badge.classList.add('is-error');
      var why = (err && err.message) || '请求失败';
      badge.setAttribute('title', '未读通知数量获取失败：' + why);
      badge.setAttribute('aria-label', '未读通知数量未知');
    });
  }

  function bindHeader() {
    var header = document.querySelector('.app-header');
    if (!header) { return; }

    /* 点击任意下拉触发器 */
    header.addEventListener('click', function (e) {
      var host = e.target.closest ? e.target.closest('.hdrop') : null;
      var trigger = e.target.closest ? e.target.closest('.search-box, .icon-btn, .user-chip') : null;

      if (trigger && host) {
        e.stopPropagation();
        var kind = host.getAttribute('data-drop');
        var wasOpen = host.classList.contains('is-open');

        /* 搜索框：展开后点自身不收起，避免想打字却把面板关掉 */
        if (kind === 'search') {
          if (!wasOpen) {
            closeAll(host);
            host.classList.add('is-open');
            renderSearch('');
            var inp = host.querySelector('input');
            if (inp) { inp.focus(); }
          }
          return;
        }

        toggle(host);

        if (kind === 'notice' && !wasOpen) {
          /* 打开面板时才拉取列表；同时视为已读，清除红点并同步服务端 */
          renderNotice();
          var badge = host.querySelector('.dot-badge');
          if (badge) { badge.classList.add('is-cleared'); }
          if (ZFZ.api) {
            ZFZ.api.markAllNotificationsRead().then(null, function () { /* 未登录：忽略 */ });
          }
        }
        return;
      }

      /* 用户菜单里的退出登录 */
      var act = e.target.closest ? e.target.closest('[data-action="logout"]') : null;
      if (act) {
        e.preventDefault();
        e.stopPropagation();
        closeAll();
        if (!ZFZ.api) { toast('接口层未加载，无法退出登录'); return; }
        ZFZ.api.logout().then(function () {
          toast('已退出登录');
          /* 各页面缓存了登录后才可见的数据，整页刷新是状态最干净的做法 */
          setTimeout(function () { location.reload(); }, 400);
        }, function (err) {
          toast('退出登录失败：' + ((err && err.message) || '未知错误'));
        });
        return;
      }
    });

    /* 搜索输入实时过滤 */
    header.addEventListener('input', function (e) {
      if (e.target && e.target.id === 'global-search') {
        var host = e.target.closest('.hdrop');
        if (host && !host.classList.contains('is-open')) { toggle(host); }
        renderSearch(e.target.value);
      }
    });

    /* 点击面板内部（非触发器）不冒泡关闭 */
    header.addEventListener('click', function (e) {
      if (e.target.closest && e.target.closest('.drop-panel')) { e.stopPropagation(); }
    });

    /* 点击空白 / Esc 关闭 */
    document.addEventListener('click', function () { closeAll(); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { closeAll(); }
    });
  }

  /* ---------------- 轻提示 ---------------- */
  var toastTimer = null;
  function toast(msg) {
    var el = document.getElementById('zfz-toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'zfz-toast';
      el.className = 'zfz-toast';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.add('is-show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('is-show'); }, 2200);
  }

  return {
    NAV: NAV,
    toast: toast,

    /** 把全局导航挂载到 #app-header */
    mount: function (activeKey) {
      var host = document.getElementById('app-header');
      if (!host) { return; }
      host.className = 'app-header';
      host.innerHTML = buildHeader(activeKey);
      bindHeader();
      loadSession();      /* 顶栏用户信息取自 /api/auth/me */
      refreshUnread();    /* 通知红点取自 /api/notifications/unread-count */
    },

    /** 当前登录用户（未登录返回 null），供页面判断权限与展示身份 */
    currentUser: currentUser,

    /** 当前会话状态：'unknown' | 'user' | 'guest' | 'error' */
    sessionStatus: sessionStatus,

    /** 重新探测会话并重绘顶栏；登录/登出成功后应调用，免得用户手动刷新页面 */
    refreshSession: refreshSession,

    /** 页面标题行：标题 + 竖分隔 + 说明，右侧可放元信息 */
    titleBar: function (opts) {
      var meta = (opts.meta || []).map(function (m) {
        var inner = (m.dot ? '<i class="status-dot"></i>' : '') + m.text;
        return '<span class="meta-item' + (m.cls ? ' ' + m.cls : '') + '">' + inner + '</span>';
      }).join('');
      return '' +
        '<div class="title-group' + (opts.center ? ' is-centered' : '') + '">' +
          '<h1 class="page-title">' + opts.title + '</h1>' +
          '<i class="title-sep"></i>' +
          '<span class="page-desc">' + opts.desc + '</span>' +
        '</div>' +
        (meta ? '<div class="title-meta">' + meta + '</div>' : '');
    }
  };
})();
