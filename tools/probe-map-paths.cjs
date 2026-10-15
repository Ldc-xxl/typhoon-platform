/**
 * 探针：验证「路径线是否真的落在画布内」。
 *
 * 为什么不用原来的 d 属性检查：路径点坐标本身没问题，问题在于它们被投影到
 * 画布之外。所以这里用 getBoundingClientRect 判断折线与容器的实际相交面积，
 * 并顺带读出自适应提示文案与底图的变换矩阵。
 *
 * 用法：node tools/probe-map-paths.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9800 + (process.pid % 300);
const PROF = path.join(os.tmpdir(), 'zfz-map-' + Date.now());
const OUT = path.resolve(__dirname, '../_probe_shots');
const BASE = 'http://localhost:8080/typhoon_platform/';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (p, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

const PROBE_JS = `(function(){
  var box = document.querySelector('.map-canvas') || document.querySelector('.lmap');
  if (!box) { return JSON.stringify({ error: 'no map container' }); }
  var bb = box.getBoundingClientRect();
  var out = { container: [bb.x|0, bb.y|0, bb.width|0, bb.height|0], lines: [] };
  function area(r) {
    var w = Math.min(r.right, bb.right) - Math.max(r.left, bb.left);
    var h = Math.min(r.bottom, bb.bottom) - Math.max(r.top, bb.top);
    return w > 0 && h > 0 ? Math.round(w * h) : 0;
  }
  box.querySelectorAll('polyline').forEach(function (p) {
    var r = p.getBoundingClientRect();
    out.lines.push({
      cls: p.getAttribute('class') || '(noclass)',
      box: [r.left|0, r.top|0, r.width|0, r.height|0],
      visibleArea: area(r),
      ownArea: Math.round(r.width * r.height)
    });
  });
  var land = box.querySelector('.map-land-layer .map-view-g');
  out.basemapTransform = land ? (land.getAttribute('transform') || '(none)') : null;
  out.landPaths = box.querySelectorAll('.map-land').length;
  var cloud = box.querySelector('.map-cloud-layer');
  out.cloudShown = cloud ? window.getComputedStyle(cloud).display !== 'none' : null;
  var note = box.querySelector('.map-view-note, .lmap-view-note');
  out.note = note ? note.innerText.replace(/\\s+/g, ' ') : null;
  out.graticuleLines = box.querySelectorAll('.map-grat').length;
  return JSON.stringify(out, null, 1);
})()`;

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server', '--remote-debugging-port=' + PORT,
    '--user-data-dir=' + PROF, 'about:blank'], { stdio: 'ignore' });
  let ver = null;
  for (let i = 0; i < 60 && !ver; i++) { try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); } }
  if (!ver) { console.error('DevTools 未就绪'); edge.kill(); process.exit(1); }

  for (const job of [{ url: 'index.html', file: 'index' }, { url: 'sandbox.html', file: 'sandbox' }]) {
    const t = await getJson('/json/new?about:blank', 'PUT');
    const ws = new WebSocket(t.webSocketDebuggerUrl);
    const pending = new Map(); let id = 0; const errs = [];
    ws.addEventListener('message', (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
      if (m.method === 'Runtime.exceptionThrown') errs.push('EXC ' + (m.params.exceptionDetails.exception && m.params.exceptionDetails.exception.description));
      if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') errs.push('LOG ' + m.params.entry.text);
    });
    const send = (m, p) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: m, params: p || {} })); });
    await new Promise((r) => ws.addEventListener('open', r));
    await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
    await send('Page.navigate', { url: BASE + job.url });
    await sleep(7500);
    const r = await send('Runtime.evaluate', { expression: PROBE_JS, returnByValue: true });
    const shot = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUT, job.file + '.png'), Buffer.from(shot.result.data, 'base64'));
    console.log('\n===== ' + job.url + ' =====');
    console.log(r.result && r.result.result && r.result.result.value);
    console.log('errors:', errs.length ? errs.join(' | ') : '(none)');
    try { await getJson('/json/close/' + t.id); } catch (e) {}
    ws.close();
  }
  edge.kill(); setTimeout(() => process.exit(0), 400);
}
main().catch((e) => { console.error(e); process.exit(1); });
