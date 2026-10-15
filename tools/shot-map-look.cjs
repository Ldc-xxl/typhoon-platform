/**
 * 一次性脚本：核对 P1 实况地图在「三套底图 / 缩放 / 分级显示」下的观感与关键指标。
 *
 * 只做两件事：截图，以及把「当前的视野比例尺、地名分级、各类地名可见数量、
 * 字号反缩放系数、云图显隐、一帧渲染耗时」打到控制台。
 * 这些量都是肉眼看不准、但出问题一定会先在这里露馅的。
 *
 * 用法：node tools/shot-map-look.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9700 + (process.pid % 200);
const PROF = path.join(os.tmpdir(), 'zfz-look-' + Date.now());
const OUT = 'C:/Users/17168/Desktop/web课程项目/typhoon_platform/_probe_shots';
const BASE = 'http://localhost:8080/typhoon_platform/index.html';
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
  const js = (expr, want = true) => send('Runtime.evaluate', { expression: expr, returnByValue: want });

  /** 当前地图的关键指标（每次截图前打印一次） */
  const stats = async () => {
    const r = await js(`(function(){
      var c = document.querySelector('.map-canvas');
      if (!c) { return '{"error":"no map-canvas"}'; }
      var vis = function (sel) {
        var n = 0, all = c.querySelectorAll(sel);
        for (var i = 0; i < all.length; i++) {
          var s = window.getComputedStyle(all[i]);
          if (s.display !== 'none' && s.visibility !== 'hidden') { n++; }
        }
        return n;
      };
      var lbl = c.querySelector('.map-lbl');
      var dot = c.querySelector('.map-city-dot');
      var g = c.querySelector('.map-grat-layer .map-view-g');
      return JSON.stringify({
        basemap: c.getAttribute('data-basemap'),
        lod: c.getAttribute('data-lod'),
        view: c.getAttribute('data-view'),
        lblK: c.getAttribute('style'),
        lblFont: lbl ? window.getComputedStyle(lbl).fontSize : null,
        dotR: dot ? window.getComputedStyle(dot).r : null,
        cloudShown: !!c.querySelector('.map-cloud-layer') &&
          window.getComputedStyle(c.querySelector('.map-cloud-layer')).display !== 'none',
        landPaths: c.querySelectorAll('.map-land').length,
        gratLines: g ? g.querySelectorAll('line').length : 0,
        gratTexts: g ? Array.prototype.map.call(g.querySelectorAll('text'), function (e) { return e.textContent; }).slice(0, 12).join(',') : '',
        cities: vis('.map-city-label'),
        places: { sea: vis('.map-lbl.is-sea'), country: vis('.map-lbl.is-country'), prov: vis('.map-lbl.is-prov'), city: vis('.map-lbl.is-city') },
        scaleText: (c.querySelector('.map-scale-text') || {}).textContent || ''
      });
    })()`);
    return r.result.result.value;
  };

  const shot = async (name) => {
    const r = (await js(`(function(){var e=document.querySelector('.map-canvas');var b=e.getBoundingClientRect();
      return JSON.stringify({x:b.left,y:b.top,width:b.width,height:b.height});})()`)).result.result.value;
    const R = JSON.parse(r);
    const s = await send('Page.captureScreenshot', {
      format: 'png',
      clip: { x: R.x, y: R.y, width: R.width, height: R.height, scale: 1.6 }
    });
    fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(s.result.data, 'base64'));
    console.log('\n[' + name + '] ' + await stats());
  };

  const click = (sel) => js(`document.querySelector('${sel}').click()`);
  const zoom = async (dir, n) => {
    for (let i = 0; i < n; i++) { await click(`[data-zoom="${dir}"]`); await sleep(260); }
    await sleep(500);
  };

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });
  await sleep(9000);

  /* 一帧渲染耗时：切到卫星云图（云图最重）后连等两帧的时间。
     云图那张纹理是一整块大滤镜，如果浏览器把它按元素包围盒（5000×3800）
     逐像素渲染，这里会立刻看出来（几百毫秒）。 */
  const frame = await send('Runtime.evaluate', {
    expression: `(async function(){
      var c = document.querySelector('.map-canvas');
      var t0 = performance.now();
      c.setAttribute('data-basemap', 'sat');
      await new Promise(function(r){ requestAnimationFrame(function(){ requestAnimationFrame(r); }); });
      var dt = Math.round(performance.now() - t0);
      c.setAttribute('data-basemap', 'plain');
      return dt;
    })()`,
    awaitPromise: true, returnByValue: true
  });
  console.log('切到卫星云图并稳定两帧耗时：' + frame.result.result.value + ' ms');

  await shot('map-look-plain');

  await click('[data-basemap-btn="sat"]'); await sleep(900);
  await shot('map-look-sat');

  await click('[data-basemap-btn="terrain"]'); await sleep(700);
  await shot('map-look-terrain');

  /* 缩小到最小比例尺附近：看底图是否仍然铺满、地名是否按级别收敛 */
  await click('[data-basemap-btn="plain"]'); await sleep(300);
  await click('[data-zoom="reset"]'); await sleep(400);
  await zoom('out', 4);
  await shot('map-look-out');

  /* 放大：看海岸线细节、经纬网步长是否变密、城市名是否放出来 */
  await click('[data-zoom="reset"]'); await sleep(400);
  await zoom('in', 5);
  await shot('map-look-in');

  /* 往东平移，确认任意位置都是一张完整的地图（不出现空白） */
  await click('[data-zoom="reset"]'); await sleep(400);
  await zoom('in', 1);
  await js(`(function(){
    var c = document.querySelector('.map-canvas'), r = c.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    c.dispatchEvent(new MouseEvent('mousedown', { bubbles:true, cancelable:true, clientX:x, clientY:y, button:0 }));
    document.dispatchEvent(new MouseEvent('mousemove', { bubbles:true, clientX:x - 420, clientY:y + 120 }));
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles:true }));
    return 1;
  })()`);
  await sleep(700);
  await shot('map-look-pan');

  const errs = await js(`JSON.stringify(window.__consoleErrors || [])`);
  console.log('\nconsole errors:', errs.result.result.value);

  edge.kill(); setTimeout(() => process.exit(0), 400);
})().catch((e) => { console.error(e); process.exit(1); });
