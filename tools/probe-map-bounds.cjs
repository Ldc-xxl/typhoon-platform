/**
 * 地图视野边界回归（2026-10-18）。
 *
 * 背景：地形晕渲是一张有覆盖范围的位图（DEM 92°E—178°E / 4°N—52°N）。
 * 用户把地图拖到边界以外时，位图之外没有高程，陆地只剩一层平涂底色，
 * 图面上会出现一条笔直的「地形到此为止」的直角分界线 —— 实测就是这张
 * 一片亮、一片死的割裂感。修复方式是把视野夹进 BOUND（见 ui.js）。
 *
 * 本脚本要钉住的不变量：
 *   ① 四个方向拖到底，视野边界恰好停在 92°E / 180°E / 52°N / 4°N；
 *   ② 任何视野下，地形位图都必须盖住画布（唯一例外见下）；
 *   ③ 默认 100% 时「−」置灰、放大后恢复、「重置」回恒等视野。
 *
 * ②的例外：DEM 东缘在 178°E，而画布右缘是 180°E，于是右边永远留 20px
 * 没有晕渲。那 2° 是开阔洋面（4—50°N 内无陆地），位图海面 alpha=0，
 * 两侧显示的是同一个容器底色 + 同一层云系，视觉上无从分辨，
 * 因此断言允许这 20px（= 2° × KX）缺口，但不允许更多。
 *
 * 用法：node tools/probe-map-bounds.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9800 + (process.pid % 150);
const PROF = path.join(os.tmpdir(), 'zfz-bound-' + Date.now());
const OUT = 'C:/Users/17168/Desktop/web课程项目/typhoon_platform/_probe_shots';
const BASE = 'http://localhost:8080/typhoon_platform/index.html';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (p, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

let PASS = 0, FAIL = 0;
function ok(cond, label, detail) {
  if (cond) { PASS++; console.log('  PASS  ' + label + (detail ? '  ' + detail : '')); }
  else { FAIL++; console.log('  FAIL  ' + label + (detail ? '  ' + detail : '')); }
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server', '--remote-debugging-port=' + PORT,
    '--user-data-dir=' + PROF, 'about:blank'], { stdio: 'ignore' });

  let ver = null;
  for (let i = 0; i < 60 && !ver; i++) {
    try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); }
  }
  if (!ver) { console.log('无法启动 Edge'); process.exit(1); }

  const t = await getJson('/json/new?about:blank', 'PUT');
  const ws = new WebSocket(t.webSocketDebuggerUrl);
  const pending = new Map(); let id = 0;
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  });
  const send = (method, params) => new Promise((res) => {
    const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params: params || {} }));
  });
  const js = (expr, ap) => send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: !!ap });
  const val = async (expr, ap) => {
    const r = await js(expr, ap);
    const res = r.result || {};
    if (res.exceptionDetails) {
      const ex = res.exceptionDetails;
      throw new Error((ex.exception && (ex.exception.description || ex.exception.value)) || ex.text);
    }
    return res.result ? res.result.value : null;
  };

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1020, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });
  await sleep(5600);

  /* 拖拽：从画布中心按住，分步移动 dx/dy（CSS 像素）后松开 */
  async function drag(dx, dy) {
    const b = JSON.parse(await val(`(function(){var r=document.querySelector('.map-canvas').getBoundingClientRect();
      return JSON.stringify([r.left+r.width/2, r.top+r.height/2]);})()`));
    const steps = 12;
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: b[0], y: b[1], button: 'left', clickCount: 1 });
    for (let i = 1; i <= steps; i++) {
      await send('Input.dispatchMouseEvent', {
        type: 'mouseMoved', x: b[0] + (dx * i) / steps, y: b[1] + (dy * i) / steps, button: 'left', buttons: 1
      });
      await sleep(16);
    }
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: b[0] + dx, y: b[1] + dy, button: 'left', clickCount: 1 });
    await sleep(320);
  }

  /* 视野四角对应的经纬度 + 地形位图是否盖住画布 */
  async function probe() {
    return JSON.parse(await val(`(function(){
      var c = document.querySelector('.map-canvas');
      var cr = c.getBoundingClientRect();
      var v = ZFZ.ui.viewState();
      var MW = 804, MH = 462, LON0 = 100, KX = 10.05, KY = 10.043;
      function unp(x, y) {
        var ux = (x - MW/2 - v.tx) / v.s + MW/2;
        var uy = (y - MH/2 - v.ty) / v.s + MH/2;
        return [LON0 + ux/KX, 50 - uy/KY];
      }
      var a = unp(0, 0), b = unp(MW, MH);
      var img = c.querySelector('.terr-hills');
      var ir = img ? img.getBoundingClientRect() : null;
      /* 右缘允许 21px 缺口（178—180°E 开阔洋面，位图海面透明，见文件头注释），
         左 / 上 / 下必须完全盖住 —— 那三个方向一旦露边就是真正的"死色直边"。 */
      var covers = !!ir && ir.left <= cr.left + 1 && ir.top <= cr.top + 1 &&
                   ir.right >= cr.right - 21 && ir.bottom >= cr.bottom - 1;
      var gapRight = ir ? Math.round(cr.right - ir.right) : -1;
      var zin = c.querySelector('[data-zoom="in"]');
      var zout = c.querySelector('[data-zoom="out"]');
      return JSON.stringify({
        s: v.s, k: v.k, tx: v.tx, ty: v.ty,
        lonL: +a[0].toFixed(2), lonR: +b[0].toFixed(2),
        latT: +a[1].toFixed(2), latB: +b[1].toFixed(2),
        covers: covers, gapRight: gapRight,
        imgBox: ir ? [Math.round(ir.left), Math.round(ir.top), Math.round(ir.width), Math.round(ir.height)] : null,
        canvasBox: [Math.round(cr.left), Math.round(cr.top), Math.round(cr.width), Math.round(cr.height)],
        inDisabled: !!(zin && zin.hasAttribute('disabled')),
        outDisabled: !!(zout && zout.hasAttribute('disabled'))
      });
    })()`));
  }

  console.log('=== 默认视野 ===');
  let p = await probe();
  console.log(JSON.stringify(p));
  ok(p.lonL === 100 && p.latT === 50, '默认视野仍是设计的 100°E / 50°N 起算', p.lonL + 'E ' + p.latT + 'N');
  ok(p.covers, '默认：地形位图盖住整张画布（仅右侧 178—180°E 洋面允许缺口）');
  ok(p.outDisabled, '默认 100% 时「−」置灰（再缩小就会露出无数据区）');
  ok(!p.inDisabled, '默认 100% 时「+」可用');

  console.log('=== 向西狠拖 900px ===');
  await drag(900, 0);
  p = await probe();
  console.log(JSON.stringify(p));
  ok(Math.abs(p.lonL - 92) < 0.35, '西边界夹在 92°E（DEM 西缘）', 'lonL=' + p.lonL);
  ok(p.covers, '拖到西界：地形位图仍盖住整张画布（无地形断边）');
  fs.writeFileSync(path.join(OUT, 'bound-west.png'), Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));

  console.log('=== 向北狠拖 900px ===');
  await drag(0, 900);
  p = await probe();
  console.log(JSON.stringify(p));
  ok(Math.abs(p.latT - 52) < 0.35, '北边界夹在 52°N（DEM 北缘）', 'latT=' + p.latT);
  ok(p.covers, '拖到北界：地形位图仍盖住整张画布（无地形断边）');
  fs.writeFileSync(path.join(OUT, 'bound-north.png'), Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));

  console.log('=== 向南 / 向东狠拖（应停在画布边缘）===');
  await drag(0, -1800);
  p = await probe();
  console.log('南: ' + JSON.stringify(p));
  ok(Math.abs(p.latB - 4) < 0.35, '南边界夹在 4°N（画布下缘 = DEM 南缘）', 'latB=' + p.latB);
  ok(p.covers, '拖到南界：地形位图仍盖住整张画布（无地形断边）');

  await drag(-1800, 0);
  p = await probe();
  console.log('东: ' + JSON.stringify(p));
  ok(Math.abs(p.lonR - 180) < 0.35, '东边界夹在 180°E', 'lonR=' + p.lonR);
  ok(p.covers, '拖到东界：地形位图仍盖住整张画布（右侧洋面缺口 ≤ 21px）');

  console.log('=== 缩放按钮状态 ===');
  await val(`document.querySelector('[data-zoom="in"]').click()`);
  await sleep(320);
  p = await probe();
  ok(p.s > 1.2, '放大生效', 's=' + p.s);
  ok(!p.outDisabled, '放大后「−」恢复可用');
  ok(p.covers, '放大后地形位图仍盖住整张画布');

  await val(`document.querySelector('[data-zoom="reset"]').click()`);
  await sleep(320);
  p = await probe();
  ok(Math.abs(p.s - 1) < 0.01 && p.tx === 0 && p.ty === 0, '「重置」回到恒等视野', JSON.stringify([p.s, p.tx, p.ty]));
  ok(p.outDisabled, '重置后「−」再次置灰');
  fs.writeFileSync(path.join(OUT, 'bound-reset.png'), Buffer.from((await send('Page.captureScreenshot', { format: 'png' })).result.data, 'base64'));

  console.log('\n合计  ' + PASS + ' PASS / ' + FAIL + ' FAIL');
  ws.close(); edge.kill();
})();
