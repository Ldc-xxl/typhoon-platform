/**
 * 一次性脚本：把监测总览的左栏与地图右上角缩放控件放大截图，用于核对排版细节。
 * 用法：node /tmp/shot-clip.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9800 + (process.pid % 300);
const PROF = path.join(os.tmpdir(), 'zfz-clip-' + Date.now());
const OUT = 'C:/Users/17168/Desktop/web课程项目/typhoon_platform/_probe_shots';
const BASE = 'http://localhost:8080/typhoon_platform/';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (p, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server', '--remote-debugging-port=' + PORT,
    '--user-data-dir=' + PROF, 'about:blank'], { stdio: 'ignore' });
  let ver = null;
  for (let i = 0; i < 60 && !ver; i++) { try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); } }
  const t = await getJson('/json/new?about:blank', 'PUT');
  const ws = new WebSocket(t.webSocketDebuggerUrl);
  const pending = new Map(); let id = 0;
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  });
  const send = (m, p) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: m, params: p || {} })); });
  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 2, mobile: false });
  await send('Page.navigate', { url: BASE + 'index.html' });
  await sleep(8000);

  /* 先切到第 2 个活跃台风，让「返回最新」「详情」链接都处于可见状态 */
  await send('Runtime.evaluate', { expression: `(function(){
    var it = document.querySelectorAll('#ty-list-host .ty-item');
    if (it[1]) { it[1].click(); }
    return it.length;
  })()`, returnByValue: true });
  await sleep(3000);

  const rects = await send('Runtime.evaluate', { expression: `(function(){
    var q = function (s) { var e = document.querySelector(s); if (!e) return null;
      var r = e.getBoundingClientRect(); return { x: r.left, y: r.top, width: r.width, height: r.height }; };
    return JSON.stringify({ left: q('.col-left'), zoom: q('.map-zoom'), hint: q('.ty-hint'), item: q('.ty-item') });
  })()`, returnByValue: true });
  const R = JSON.parse(rects.result.result.value);
  console.log(JSON.stringify(R, null, 1));

  for (const key of ['left', 'zoom', 'hint']) {
    if (!R[key]) { continue; }
    const pad = key === 'zoom' ? 14 : 6;
    const s = await send('Page.captureScreenshot', {
      format: 'png',
      clip: { x: R[key].x - pad, y: R[key].y - pad, width: R[key].width + pad * 2, height: R[key].height + pad * 2, scale: 2 }
    });
    fs.writeFileSync(path.join(OUT, 'clip-' + key + '.png'), Buffer.from(s.result.data, 'base64'));
    console.log('saved clip-' + key + '.png');
  }
  edge.kill(); setTimeout(() => process.exit(0), 400);
})().catch((e) => { console.error(e); process.exit(1); });
