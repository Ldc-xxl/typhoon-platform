/**
 * 一次性脚本：核对 P1 实况地图（三合一：海岸线 + 地形 + 云系）在
 * 「缩放 / 平移 / 分级显示」下的观感与关键指标。
 *
 * 只做两件事：截图，以及把「当前的视野比例尺、地名分级、各类地名可见数量、
 * 字号反缩放系数、一帧渲染耗时」打到控制台 ——
 * 这些量肉眼看不准，但出问题一定先在这里露馅。
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

  /* 一帧渲染耗时：三合一后地形位图 + 云滤镜同屏，是最重的一帧。
     若浏览器把云滤镜按元素包围盒（4000×2400）逐像素渲染，这里会立刻看出来。 */
  const frame = await send('Runtime.evaluate', {
    expression: `(async function(){
      var c = document.querySelector('.map-canvas');
      var t0 = performance.now();
      c.style.opacity = '0.999';
      await new Promise(function(r){ requestAnimationFrame(function(){ requestAnimationFrame(r); }); });
      var dt = Math.round(performance.now() - t0);
      c.style.opacity = '';
      return dt;
    })()`,
    awaitPromise: true, returnByValue: true
  });
  console.log('三合一地图稳定两帧耗时：' + frame.result.result.value + ' ms');

  /* 三合一只有一张图，没有 sat / terrain 可切 */
  await shot('map-look-combo');

  /* 缩小：100% 已是"整个台风区刚好装下"，再缩就会露出 DEM 覆盖范围之外
     的无数据区（那正是 2026-10-18 那版"地形直角断边"的来源）。
     所以这里断言的反而成了「缩不动」：连点四次「−」后比例尺仍是 100%。 */
  await click('[data-zoom="reset"]'); await sleep(400);
  await zoom('out', 4);
  const outPct = await js(`document.querySelector('[data-zoom-value]').textContent`);
  console.log('连点 4 次「−」后的比例尺：' + outPct.result.result.value + '（应恒为 100%）');
  await shot('map-look-out');

  /* 放大：看海岸线细节、经纬网步长是否变密、城市名是否放出来 */
  await click('[data-zoom="reset"]'); await sleep(400);
  await zoom('in', 5);
  await shot('map-look-in');

  /* 往**西**平移（这是当初踩雷的方向：拖到 92°E 就以 DEM 西缘为界，
     再往西应该拖不动、且画面里不会出现一条笔直的地形断边） */
  await click('[data-zoom="reset"]'); await sleep(400);
  await zoom('in', 1);
  await js(`(function(){
    var c = document.querySelector('.map-canvas'), r = c.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    c.dispatchEvent(new MouseEvent('mousedown', { bubbles:true, cancelable:true, clientX:x, clientY:y, button:0 }));
    document.dispatchEvent(new MouseEvent('mousemove', { bubbles:true, clientX:x + 520, clientY:y - 160 }));
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles:true }));
    return 1;
  })()`);
  await sleep(700);
  await shot('map-look-pan');

  const errs = await js(`JSON.stringify(window.__consoleErrors || [])`);
  console.log('\nconsole errors:', errs.result.result.value);

  edge.kill(); setTimeout(() => process.exit(0), 400);
})().catch((e) => { console.error(e); process.exit(1); });
