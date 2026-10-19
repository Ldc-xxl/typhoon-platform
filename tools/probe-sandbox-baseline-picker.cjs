/* ============================================================
 * probe-sandbox-baseline-picker.cjs
 *
 * 验证 P6 沙盘「基准台风」下拉选择器（原来是一颗点一次换一只的「更换」按钮）：
 *   ① 候选条数 == /api/typhoons?status=ACTIVE 的条数（不写死数字）
 *   ② 初始收起；点「更换」展开，aria-expanded 同步
 *   ③ 展开后不被祖先容器裁切，且不超出视口
 *   ④ 点某一项 → 一步选中（基准名称变、列表收起、选中标记搬过去、有切换 toast）
 *   ⑤ 点页面空白处 / 按 Esc 都能收起；Esc 后焦点回到「更换」
 *   ⑥ 方向键能在候选项之间移动焦点
 *   ⑦ 全程无控制台报错
 *
 * 用法（必须在沙箱外执行，浏览器进程会被沙箱拦）：
 *   <managed-node>/node.exe tools/probe-sandbox-baseline-picker.cjs
 * 截图落在 tools/_probe_out/。
 * ============================================================ */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const PORT = 9336;
const PAGE = 'http://localhost:8080/typhoon_platform/sandbox.html';
const OUT = path.join(__dirname, '..', '_probe_shots');   // 与其它探针一致；.gitignore 已忽略
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

let pass = 0, fail = 0;
const fails = [];
function check(name, ok, extra) {
  if (ok) { pass++; console.log('  PASS ' + name); }
  else { fail++; fails.push(name + (extra ? '  ← ' + extra : '')); console.log('  FAIL ' + name + (extra ? '  ← ' + extra : '')); }
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const PROF = path.join(os.tmpdir(), 'cdp-bp-' + process.pid);
  spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server', '--disable-http-cache',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROF, 'about:blank'
  ], { stdio: 'ignore' });

  let ready = false;
  for (let i = 0; i < 40; i++) {
    try { if ((await fetch(`http://127.0.0.1:${PORT}/json/version`)).ok) { ready = true; break; } } catch (e) { /* retry */ }
    await wait(400);
  }
  if (!ready) { console.error('DevTools 未就绪'); return 1; }

  const t = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })).json();
  const ws = new WebSocket(t.webSocketDebuggerUrl);
  const pending = new Map();
  const errs = [];
  let id = 0;
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      const txt = m.params.entry.text || '';
      if (txt.indexOf('favicon') < 0) errs.push('LOG ' + txt);
    }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails;
      errs.push('EXC ' + String((d.exception && d.exception.description) || d.text).slice(0, 240));
    }
  });
  const send = (method, params) => new Promise((res) => {
    const i = ++id; pending.set(i, res);
    ws.send(JSON.stringify({ id: i, method, params: params || {} }));
  });
  await new Promise((r) => ws.addEventListener('open', r));

  const js = async (expr, awaitPromise) => {
    const r = await send('Runtime.evaluate', {
      expression: expr, returnByValue: true, awaitPromise: !!awaitPromise
    });
    if (r.result && r.result.exceptionDetails) {
      return '__EXC__ ' + JSON.stringify(r.result.exceptionDetails.text || '');
    }
    return r.result && r.result.result ? r.result.result.value : null;
  };
  const shot = async (name) => {
    const r = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUT, name), Buffer.from(r.result.data, 'base64'));
  };

  await send('Page.enable');
  await send('Runtime.enable');
  await send('Log.enable');
  await send('Network.enable');
  await send('Network.setCacheDisabled', { cacheDisabled: true });
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: PAGE });

  /* 等首屏渲染出「更换」按钮 */
  let booted = false;
  for (let i = 0; i < 40; i++) {
    if (await js(`!!document.getElementById('btn-change-base')`)) { booted = true; break; }
    await wait(500);
  }
  if (!booted) { console.error('沙盘页未渲染出基准台风选择器（页面可能报错）'); await shot('00-boot-fail.png'); return 1; }
  await wait(600);

  /* ---------- ① 候选条数 ---------- */
  console.log('\n[1] 候选集合');
  const apiN = await js(`new Promise(function(res){
      ZFZ.api.getTyphoons({ status: 'ACTIVE' }).then(function(l){ res(Array.isArray(l) ? l.length : -1); },
                                                    function(){ res(-2); });
    })`, true);
  const optN = await js(`document.querySelectorAll('.bp-opt').length`);
  check('活跃台风接口可用', apiN > 0, 'api=' + apiN);
  check('候选项数 == 活跃台风数', apiN === optN, 'api=' + apiN + ' opts=' + optN);
  const labelsOk = await js(`Array.prototype.every.call(document.querySelectorAll('.bp-opt'), function(o){
      var n = o.querySelector('.bp-opt-name').textContent, m = o.querySelector('.bp-opt-meta').textContent;
      return n.trim().length > 1 && /\\d{6}/.test(m);
    })`);
  check('每项含 名称 + 编号/状态', labelsOk === true);

  /* ---------- ② 展开 / aria ---------- */
  console.log('\n[2] 展开');
  check('初始收起', (await js(`document.querySelector('.bp-menu').hasAttribute('hidden')`)) === true);
  await js(`document.getElementById('btn-change-base').click()`);
  await wait(260);
  check('点「更换」展开', (await js(`document.querySelector('.bp-picker').classList.contains('is-open')`)) === true);
  check('hidden 移除', (await js(`document.querySelector('.bp-menu').hasAttribute('hidden')`)) === false);
  check('aria-expanded=true', (await js(`document.getElementById('btn-change-base').getAttribute('aria-expanded')`)) === 'true');
  check('展开即聚焦当前选中项', (await js(`document.activeElement && document.activeElement.classList.contains('bp-opt')`)) === true,
    await js(`document.activeElement ? document.activeElement.className : 'null'`));

  /* ---------- ③ 不被裁切 ---------- */
  console.log('\n[3] 几何');
  const geo = await js(`(function(){
      var menu = document.querySelector('.bp-menu');
      var r = menu.getBoundingClientRect();
      var clipped = [];
      for (var el = menu.parentElement; el && el !== document.documentElement; el = el.parentElement) {
        var o = getComputedStyle(el);
        if (o.overflow !== 'visible' || o.overflowX !== 'visible' || o.overflowY !== 'visible') {
          var pr = el.getBoundingClientRect();
          if (r.bottom > pr.bottom + 1 || r.right > pr.right + 1 || r.left < pr.left - 1) {
            clipped.push(el.className + ' [' + o.overflow + ']');
          }
        }
      }
      return { w: Math.round(r.width), h: Math.round(r.height),
               insideVp: r.left >= 0 && r.right <= window.innerWidth && r.top >= 0 && r.bottom <= window.innerHeight,
               overlapsSliders: r.bottom > document.querySelector('.slider-list').getBoundingClientRect().top,
               clipped: clipped };
    })()`);
  check('面板有尺寸', geo && geo.w > 150 && geo.h > 40, JSON.stringify(geo));
  check('面板完全在视口内', geo && geo.insideVp === true);
  check('面板浮在滑杆之上（靠 z-index 而非撑开布局）', geo && geo.overlapsSliders === true);
  check('无祖先容器裁切', geo && geo.clipped.length === 0, geo && geo.clipped.join(' | '));
  await shot('01-open.png');

  /* ---------- ④ 一步选中 ---------- */
  console.log('\n[4] 选中一项');
  const curBefore = await js(`document.querySelector('.bp-name').textContent`);
  const target = await js(`(function(){
      var os = document.querySelectorAll('.bp-opt');
      for (var i = 0; i < os.length; i++) {
        if (!os[i].classList.contains('is-on')) {
          return { id: os[i].getAttribute('data-base-id'),
                   name: os[i].querySelector('.bp-opt-name').textContent.trim(),
                   meta: os[i].querySelector('.bp-opt-meta').textContent.trim() };
        }
      }
      return null;
    })()`);
  if (!target) {
    check('存在非当前项可切换（活跃台风 ≥ 2 才能验证切换）', false, '只有 1 只活跃台风，跳过切换断言');
  } else {
    const onInfo = await js(`(function(){
        var on = document.querySelectorAll('.bp-opt.is-on');
        return 'on=' + on.length + ' ids=' + Array.prototype.map.call(document.querySelectorAll('.bp-opt'), function(o){ return o.getAttribute('data-base-id'); }).join('|');
      })()`);
    console.log('  · 点击前：' + onInfo + ' 目标=' + JSON.stringify(target));
    const clickRet = await js(`(function(){ var el = document.querySelector('.bp-opt[data-base-id="${target.id}"]');
        if (!el) { return 'no-el'; } el.click(); return 'clicked'; })()`);
    console.log('  · click() 返回：' + clickRet);
    await wait(1600);
    const nameAfter = await js(`document.querySelector('.bp-name').textContent`);
    check('基准名称已换成所选台风', nameAfter.indexOf(target.name.split(' ')[0]) === 0 && nameAfter !== curBefore,
      'before=' + curBefore + ' after=' + nameAfter);
    check('编号/状态与选项一致', nameAfter.indexOf(target.meta.split(' · ')[0]) > -1, nameAfter);
    check('选完自动收起', (await js(`document.querySelector('.bp-picker').classList.contains('is-open')`)) === false);
    check('选中标记只有一处且落在所选', (await js(`(function(){
        var on = document.querySelectorAll('.bp-opt.is-on');
        return on.length === 1 && on[0].getAttribute('data-base-id') === '${target.id}';
      })()`)) === true);
    check('有切换提示 toast', (await js(`(function(){
        var el = document.getElementById('zfz-toast');
        return !!el && el.classList.contains('is-show') && el.textContent.indexOf('基准台风已切换为') === 0;
      })()`)) === true, await js(`(document.getElementById('zfz-toast') || {}).textContent || ''`));
    await shot('02-after-pick.png');
  }

  /* ---------- ⑤ 空白处 / Esc 收起 ---------- */
  console.log('\n[5] 收起');
  await js(`document.getElementById('btn-change-base').click()`);
  await wait(200);
  await js(`document.body.click()`);
  await wait(200);
  check('点页面空白处收起', (await js(`document.querySelector('.bp-picker').classList.contains('is-open')`)) === false);

  await js(`document.getElementById('btn-change-base').click()`);
  await wait(200);
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
  await wait(220);
  check('Esc 收起', (await js(`document.querySelector('.bp-picker').classList.contains('is-open')`)) === false);
  check('Esc 后焦点回到「更换」', (await js(`document.activeElement && document.activeElement.id`)) === 'btn-change-base');

  /* ---------- ⑥ 方向键 ---------- */
  console.log('\n[6] 键盘');
  await js(`document.getElementById('btn-change-base').click()`);
  await wait(200);
  const f0 = await js(`Array.prototype.indexOf.call(document.querySelectorAll('.bp-opt'), document.activeElement)`);
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'ArrowDown', code: 'ArrowDown', windowsVirtualKeyCode: 40 });
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'ArrowDown', code: 'ArrowDown', windowsVirtualKeyCode: 40 });
  await wait(180);
  const f1 = await js(`Array.prototype.indexOf.call(document.querySelectorAll('.bp-opt'), document.activeElement)`);
  const total = await js(`document.querySelectorAll('.bp-opt').length`);
  check('ArrowDown 焦点下移（末项回环）', f1 === (f0 + 1) % total, 'from=' + f0 + ' to=' + f1 + ' total=' + total);
  await shot('03-keyboard.png');

  /* 收尾：把列表关掉，别让控制台巡检撞上半开状态 */
  await js(`document.body.click()`);
  await wait(200);

  /* ---------- ⑦ 控制台 ---------- */
  console.log('\n[7] 控制台');
  check('无 JS 异常 / 资源错误', errs.length === 0, errs.join(' ;; '));

  console.log('\n===== ' + pass + ' PASS / ' + fail + ' FAIL =====');
  if (fail) { fails.forEach((f) => console.log('  · ' + f)); }
  console.log('截图：' + OUT);
  return fail ? 1 : 0;
}

main().then((code) => process.exit(code)).catch((e) => { console.error(e); process.exit(1); });
