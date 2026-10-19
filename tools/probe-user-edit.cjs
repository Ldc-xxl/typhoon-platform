/**
 * 探针：管理后台「用户编辑弹层」端到端校验。
 *
 * 覆盖的不是"元素存在"，而是"点下去到底发生了什么"：
 *   1) 点「编辑」真的打开弹层，且 nickname / email / phone / role 全部回填成接口值
 *   2) 焦点落在昵称框、body 锁滚动
 *   3) 三类非法输入（空昵称 / 坏邮箱 / 坏手机号）被前端拦下，弹层不关、给出具体文案
 *   4) 合法输入真的落库（回查接口），随后**改回原值**，保证脚本可重复跑
 *   5) 当前登录账号（admin01）的角色下拉被锁 + 有说明文案
 *   6) ESC / 点遮罩 都能关，且焦点回到触发按钮
 *   7) 全程零控制台错误
 *
 * 为什么先改再改回：弹层必须至少真实写一次库，否则"保存"是否接对了接口无从证明；
 * 但脚本要能反复跑、不能污染演示数据，所以用完立刻还原原昵称。
 *
 * 用法：node tools/probe-user-edit.cjs
 * 前置：Tomcat 在 8080 跑着、admin01/password 可登录（脚本自己登）。
 */
const { spawn } = require('child_process');
const http = require('http');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const BASE = 'http://localhost:8080/typhoon_platform/';
const ACCOUNT = 'user08';        // 被编辑的普通用户（列表第一行）
const SELF = 'admin01';          // 当前登录账号（用于验证角色锁定）
const CREAT = 'admin01';
const CPWD = 'password';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 每个探针独占端口：共用端口时上一个 Edge 的 TIME_WAIT 会让新连接落到旧浏览器上，刷出假结果 */
async function pickPort() {
  for (let i = 0; i < 30; i++) {
    const port = 9200 + Math.floor(Math.random() * 900);
    const busy = await new Promise((res) => {
      const req = http.request({ host: '127.0.0.1', port, path: '/json/version', timeout: 400 }, () => res(true));
      req.on('error', () => res(false));
      req.on('timeout', () => { req.destroy(); res(false); });
      req.end();
    });
    if (!busy) { return port; }
  }
  throw new Error('找不到空闲调试端口');
}

/* 端口在 main() 里定：.cjs 是 CommonJS，顶层不能用 await */
let PORT = 0;
const PROF = path.join(os.tmpdir(), 'zfz-userdlg-' + Date.now());

const getJson = (pathname, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: pathname, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej);
  req.end();
});

let pass = 0;
const fails = [];
function check(name, ok, detail) {
  if (ok) { pass++; console.log('PASS  ' + name); }
  else { fails.push(name + (detail ? '  [' + detail + ']' : '')); console.log('FAIL  ' + name + (detail ? '  [' + detail + ']' : '')); }
}

async function main() {
  PORT = await pickPort();
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROF, 'about:blank'
  ], { stdio: 'ignore' });

  let ver = null;
  for (let i = 0; i < 80 && !ver; i++) {
    try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); }
  }
  if (!ver) { console.error('DevTools 未就绪'); edge.kill(); process.exit(1); }
  console.log('Edge:', ver.Browser, '| port', PORT);

  const target = await getJson('/json/new?' + encodeURIComponent('about:blank'), 'PUT');
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  const pending = new Map();
  let seq = 0;
  const errors = [];

  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails;
      errors.push('EXC ' + ((d.exception && (d.exception.description || d.exception.value)) || d.text));
    }
    if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      errors.push('CONSOLE ' + m.params.args.map((a) => a.value || a.description || '').join(' '));
    }
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      errors.push('LOG ' + m.params.entry.text + ' @ ' + (m.params.entry.url || ''));
    }
  });

  const send = (method, params) => new Promise((res) => {
    const id = ++seq;
    pending.set(id, res);
    ws.send(JSON.stringify({ id, method, params: params || {} }));
  });

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Log.enable');

  /** 求值；awaitPromise 用于页面里的 async IIFE */
  async function js(expression, awaitPromise) {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: !!awaitPromise });
    if (r.result && r.result.exceptionDetails) {
      return { __err: r.result.exceptionDetails.text };
    }
    return r.result && r.result.result ? r.result.result.value : undefined;
  }

  async function goto(url) {
    await send('Page.navigate', { url });
    await sleep(400);
    for (let i = 0; i < 100; i++) {
      const ready = await js('document.readyState');
      if (ready === 'complete') { return; }
      await sleep(120);
    }
  }

  async function waitFor(expr, timeout) {
    const t0 = Date.now();
    while (Date.now() - t0 < (timeout || 10000)) {
      if (await js(expr)) { return true; }
      await sleep(150);
    }
    return false;
  }

  /** 从接口读指定账号的当前值（页面上下文 fetch，自动带会话 Cookie） */
  const readUser = (account) => js(
    `(async () => {
       const r = await fetch('api/admin/users');
       const j = await r.json();
       const u = (j.data && j.data.items || []).find(function (x) { return x.account === ${JSON.stringify(account)}; });
       return JSON.stringify(u || null);
     })()`, true);

  const clickEdit = (account) => js(
    `(function () {
       var b = document.querySelector('#page-body [data-action="编辑"][data-account=' +
               JSON.stringify(${JSON.stringify(account)}) + ']');
       if (!b) { return 'no-button'; }
       b.click();
       return 'ok';
     })()`);

  const dlgState = () => js(
    `(function () {
       var d = document.getElementById('user-dlg');
       if (!d) { return JSON.stringify({ exists: false }); }
       var cs = getComputedStyle(d);
       return JSON.stringify({
         exists: true,
         visible: d.hidden === false && cs.display !== 'none',
         account: document.getElementById('user-dlg-account').value,
         nickname: document.getElementById('user-dlg-nickname').value,
         email: document.getElementById('user-dlg-email').value,
         phone: document.getElementById('user-dlg-phone').value,
         role: document.getElementById('user-dlg-role').value,
         roleDisabled: document.getElementById('user-dlg-role').disabled,
         roleHint: document.getElementById('user-dlg-role-hint').hidden
           ? '' : document.getElementById('user-dlg-role-hint').textContent,
         msg: document.getElementById('user-dlg-msg').hidden
           ? '' : document.getElementById('user-dlg-msg').textContent,
         focus: document.activeElement ? (document.activeElement.id || document.activeElement.tagName) : '',
         bodyLocked: document.body.classList.contains('is-dlg-open'),
         zIndex: cs.zIndex
       });
     })()`);

  /* ---------------- 1. 登录并进入后台 ---------------- */
  await goto(BASE + 'index.html');
  const login = await js(
    `fetch('api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({ username: ${JSON.stringify(CREAT)}, password: ${JSON.stringify(CPWD)} }) })
       .then(function (r) { return r.json(); }).then(function (j) { return j.code; })`, true);
  check('管理员登录成功', login === 200, 'code=' + login);

  await goto(BASE + 'admin.html');
  const tableReady = await waitFor("document.querySelectorAll('#page-body .cell-act').length > 0", 20000);
  check('用户表已渲染', tableReady);

  const before = JSON.parse(await readUser(ACCOUNT));
  check('读到基线用户', !!before, ACCOUNT);
  if (!before) { throw new Error('基线用户读取失败，后续断言无意义'); }

  /* ---------------- 2. 打开弹层：回填正确 ---------------- */
  const opened = await clickEdit(ACCOUNT);
  await sleep(400);
  const s1 = JSON.parse(await dlgState());
  check('点「编辑」打开弹层', opened === 'ok' && s1.visible, 'click=' + opened);
  check('账号字段回填且只读', s1.account === ACCOUNT && (await js("document.getElementById('user-dlg-account').disabled")),
    'account=' + s1.account);
  check('昵称回填接口值', s1.nickname === before.nickname, s1.nickname + ' vs ' + before.nickname);
  check('邮箱回填接口值', s1.email === (before.email || ''), s1.email);
  check('手机号回填接口值', s1.phone === (before.phone || ''), s1.phone);
  check('角色回填 USER', s1.role === 'USER', s1.role);
  check('普通用户角色可选', s1.roleDisabled === false);
  check('焦点落在昵称框', s1.focus === 'user-dlg-nickname', s1.focus);
  check('弹层打开时锁定页面滚动', s1.bodyLocked === true);
  check('弹层层级低于 toast（180 < 200）', s1.zIndex === '180', 'z=' + s1.zIndex);

  /* ---------------- 3. 前端校验（不关弹层、不发请求） ---------------- */
  await js("document.getElementById('user-dlg-nickname').value = '   '; document.getElementById('user-dlg-save').click(); 1");
  await sleep(250);
  const s2 = JSON.parse(await dlgState());
  check('空昵称被拦下且有文案', /昵称不能为空/.test(s2.msg) && s2.visible, s2.msg);

  await js("document.getElementById('user-dlg-nickname').value = '测试昵称';" +
           "document.getElementById('user-dlg-email').value = 'not-an-email';" +
           "document.getElementById('user-dlg-save').click(); 1");
  await sleep(250);
  const s3 = JSON.parse(await dlgState());
  check('非法邮箱被拦下', /邮箱格式不正确/.test(s3.msg) && s3.visible, s3.msg);

  await js("document.getElementById('user-dlg-email').value = 'user08@example.com';" +
           "document.getElementById('user-dlg-phone').value = '12345';" +
           "document.getElementById('user-dlg-save').click(); 1");
  await sleep(250);
  const s4 = JSON.parse(await dlgState());
  check('非法手机号被拦下', /手机号格式不正确/.test(s4.msg) && s4.visible, s4.msg);

  /* ---------------- 4. 真实保存（改昵称）→ 回查接口 ---------------- */
  const tmpNick = before.nickname + '·探针';
  await js(`document.getElementById('user-dlg-nickname').value = ${JSON.stringify(tmpNick)};
            document.getElementById('user-dlg-phone').value = ${JSON.stringify(before.phone || '')};
            document.getElementById('user-dlg-save').click(); 1`);
  const closed = await waitFor("document.getElementById('user-dlg').hidden === true", 10000);
  check('保存成功后弹层自动关闭', closed);

  const afterSave = JSON.parse(await readUser(ACCOUNT));
  check('昵称已真实落库', afterSave && afterSave.nickname === tmpNick,
    afterSave ? afterSave.nickname : 'null');

  const toastText = await js(
    `(function () {
       var t = document.getElementById('zfz-toast');
       return t ? t.textContent : '';
     })()`);
  check('保存后给出成功提示', /已保存/.test(toastText || ''), toastText);

  /* ---------------- 5. 复原（保证脚本可重复跑） ---------------- */
  await clickEdit(ACCOUNT);
  await sleep(300);
  const s5 = JSON.parse(await dlgState());
  check('再次打开时回填的是刚保存的值', s5.nickname === tmpNick && s5.visible, s5.nickname);
  await js(`document.getElementById('user-dlg-nickname').value = ${JSON.stringify(before.nickname)};
            document.getElementById('user-dlg-save').click(); 1`);
  await waitFor("document.getElementById('user-dlg').hidden === true", 10000);
  const restored = JSON.parse(await readUser(ACCOUNT));
  check('昵称已还原为原值', restored && restored.nickname === before.nickname,
    restored ? restored.nickname : 'null');

  /* ---------------- 6. 当前登录账号：角色锁定 ---------------- */
  await sleep(600);
  await clickEdit(SELF);
  await sleep(400);
  const s6 = JSON.parse(await dlgState());
  check('自己那一行：角色下拉被锁', s6.visible && s6.roleDisabled === true);
  check('自己那一行：给出锁定原因', /当前登录账号/.test(s6.roleHint || ''), s6.roleHint);

  /* ---------------- 7. ESC 关闭 + 焦点归还 ---------------- */
  await js("document.getElementById('user-dlg').dispatchEvent(new KeyboardEvent('keydown'," +
           "{ key: 'Escape', bubbles: true })); 1");
  await sleep(250);
  const s7 = JSON.parse(await dlgState());
  check('ESC 关闭弹层', s7.visible === false);
  check('关闭后解除滚动锁', s7.bodyLocked === false);
  const focusBack = await js("document.activeElement ? (document.activeElement.getAttribute('data-account') || document.activeElement.tagName) : ''");
  check('焦点归还到触发按钮', focusBack === SELF, String(focusBack));

  /* ---------------- 8. 点遮罩关闭 ---------------- */
  await clickEdit(ACCOUNT);
  await sleep(300);
  await js("document.getElementById('user-dlg').click(); 1");
  await sleep(250);
  const s8 = JSON.parse(await dlgState());
  check('点遮罩空白处关闭', s8.visible === false);

  /* ---------------- 9. 控制台 ---------------- */
  check('控制台零错误', errors.length === 0, errors.slice(0, 3).join(' | '));

  console.log('\n===== ' + pass + ' 通过 / ' + fails.length + ' 失败 =====');
  if (fails.length) { console.log(fails.map((f) => ' - ' + f).join('\n')); }

  await send('Page.close').catch(() => {});
  ws.close();
  edge.kill();
  setTimeout(() => process.exit(fails.length ? 1 : 0), 600);
}

main().catch((e) => { console.error('探针异常:', e); process.exit(2); });
