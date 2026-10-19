/**
 * 三合一地图专项验证（2026-10-18 改造后）。
 *
 * 「普通地图 / 卫星云图 / 地形图」三张底图已合并成一张：真实海岸线 +
 * 地形晕渲分层设色 + 云系同屏叠加。本脚本要回答的问题变成：
 *   ① 地形数据是否仍然**按需**加载（不能让首屏白付 1.2MB）；
 *   ② 三个图层是否真的同屏（地形 / 云 / 海岸线），且底图切换按钮确已删除；
 *   ③ 地形图层是否真有内容（晕渲位图 + 等高线 + 高程点 + 图例）；
 *   ④ 地形是否**真的改变了画面**（临时隐藏地形层截图对比，而不是只看 DOM）；
 *   ⑤ 缩放时地形与海岸线是否共用同一个视野变换（否则会错位）。
 *
 * 用法：node tools/probe-terrain-map.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
/* 端口必须独占：共用端口时即使串行，前一个 Edge 的 TIME_WAIT
   也会让后一个连上「旧浏览器」刷出假 FAIL（见 MEMORY.md）。 */
const PORT = 9800 + (process.pid % 150);
const PROF = path.join(os.tmpdir(), 'zfz-combo-' + Date.now());
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
function buffersEqual(a, b) {
  if (a.length !== b.length) { return false; }
  for (let i = 0; i < a.length; i++) { if (a[i] !== b[i]) { return false; } }
  return true;
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
  const js = (expr, awaitPromise) => send('Runtime.evaluate', {
    expression: expr, returnByValue: true, awaitPromise: !!awaitPromise
  });
  /* evaluate 抛异常时 result.result 为 undefined，直接 JSON.parse 会得到
     误导性的 "undefined is not valid JSON"。这里把异常原文打出来。 */
  const val = async (expr, aw) => {
    const r = await js(expr, aw);
    const res = r.result || {};
    if (res.exceptionDetails) {
      const ex = res.exceptionDetails;
      const msg = (ex.exception && (ex.exception.description || ex.exception.value)) || ex.text;
      throw new Error('页面内 JS 异常: ' + msg + '\n  表达式: ' + expr.slice(0, 160).replace(/\s+/g, ' '));
    }
    return res.result ? res.result.value : null;
  };

  const shotBuf = async () => {
    const R = JSON.parse(await val(`(function(){var e=document.querySelector('.map-canvas');
      var b=e.getBoundingClientRect();
      return JSON.stringify({x:b.left,y:b.top,width:b.width,height:b.height});})()`));
    const s = await send('Page.captureScreenshot', {
      format: 'png',
      clip: { x: R.x, y: R.y, width: R.width, height: R.height, scale: 1.5 }
    });
    return Buffer.from(s.result.data, 'base64');
  };
  const shot = async (name) => {
    const buf = await shotBuf();
    fs.writeFileSync(path.join(OUT, name + '.png'), buf);
    console.log('  截图  ' + name + '.png');
  };

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
  const errors = [];
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      errors.push(m.params.entry.text);
    }
  });

  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });
  await sleep(2500);

  console.log('\n=== 一、首屏：空地形层占位，数据按需加载 ===');
  /* geo-wterrain.js 约 1.2MB（晕渲位图坐标 + 二十万点等高线），
     index.html **刻意不写 <script>** —— 首屏不该为它付代价。
     地图先渲染一个空的地形层占位，ensureTerrain() 在背后加载，到位后原地替换。 */
  const pre = JSON.parse(await val(`JSON.stringify({
    hasLayer: !!document.querySelector('.map-terrain-layer'),
    terrain: (document.querySelector('.map-canvas')||{getAttribute:function(){return null;}}).getAttribute('data-terrain'),
    scriptTags: Array.prototype.filter.call(document.scripts, function(s){ return /geo-wterrain/.test(s.src); }).length
  })`));
  ok(pre.hasLayer, '地形图层占位已在 DOM 中（可原地替换，不必整张重建）',
    'data-terrain=' + pre.terrain);

  console.log('\n=== 二、ensureTerrain() 自动触发加载 ===');
  let loaded = false, state = '';
  for (let i = 0; i < 60; i++) {
    await sleep(400);
    state = await val(`(document.querySelector('.map-canvas')||{getAttribute:function(){return null;}}).getAttribute('data-terrain')`);
    if (state === 'ready') { loaded = true; break; }
  }
  ok(loaded, '地图渲染后自动加载地形数据（无需用户切底图）', 'data-terrain=' + state);
  /* 判据必须看 **HTML 源码**而不是 document.scripts —— ensureTerrain 会在
     运行时动态插入 <script src="js/geo-wterrain.js">，那也会计入 document.scripts，
     拿它断言「没有同步引入」必然误报（首版就栽在这里）。 */
  const htmlSrc = await val(`fetch('index.html').then(function(r){ return r.text(); })`, true);
  /* 只能匹配真正的 <script src="...geo-wterrain...">，
     不能光看文本里有没有 "geo-wterrain" —— index.html 的注释里就写着这个名字。 */
  ok(!/<script[^>]+src=["'][^"']*geo-wterrain/i.test(String(htmlSrc)),
    'index.html 源码中未同步引入 geo-wterrain.js（首屏不付 1.2MB）');

  const data = JSON.parse(await val(`JSON.stringify({
    hasWterrain: !!(window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain),
    contour: (window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain && ZFZ.geo.wterrain.contour) || [],
    peaks: (window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain && ZFZ.geo.wterrain.peaks) || [],
    interval: ((window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain && ZFZ.geo.wterrain.interval) || null)
  })`));
  ok(data.hasWterrain, 'geo-wterrain.js 已加载');
  ok(data.contour.length > 0, '存在矢量等高线', data.contour.length + ' 级');
  ok(data.peaks.length > 0, '存在高程点', data.peaks.length + ' 个');
  ok(Array.isArray(data.interval), '等高距为结构化数组（图例直接渲染）', JSON.stringify(data.interval));

  console.log('\n=== 三、三合一：三个图层同屏，且底图切换按钮已删除 ===');
  const combo = JSON.parse(await val(`(function(){
    var c = document.querySelector('.map-canvas');
    function disp(sel){ var e=c.querySelector(sel); return e ? window.getComputedStyle(e).display : 'missing'; }
    return JSON.stringify({
      basemapAttr: c.getAttribute('data-basemap'),
      basemapBtns: document.querySelectorAll('[data-basemap-btn]').length,
      basemapBoxes: document.querySelectorAll('.map-basemaps').length,
      terr: disp('.map-terrain-layer'),
      cloud: disp('.map-cloud-layer'),
      land: disp('.map-land-layer'),
      coast: disp('.map-coast-layer'),
      coastPaths: c.querySelectorAll('.map-coast').length,
      coastFill: (function(){ var p=c.querySelector('.map-coast'); return p?window.getComputedStyle(p).fill:null; })(),
      landFill: (function(){ var p=c.querySelector('.map-land'); return p?window.getComputedStyle(p).fill:null; })()
    });
  })()`));
  ok(combo.basemapBtns === 0 && combo.basemapBoxes === 0,
    '底图切换按钮整组已删除', '按钮=' + combo.basemapBtns + ' / 容器=' + combo.basemapBoxes);
  ok(combo.basemapAttr === null, '容器上不再有 data-basemap 属性（三合一无此概念）');
  ok(combo.land === 'block', '陆地填充层显示');
  ok(combo.terr === 'block', '地形图层显示（常显，不再依赖切换）');
  ok(combo.cloud === 'block', '云系图层显示（常显）');
  ok(combo.coast === 'block', '海岸线图层显示');
  ok(combo.coastPaths === 748, '海岸线 748 条路径', combo.coastPaths + ' 条');
  /* 海岸线必须 fill:none —— 若是填充，会盖住地形位图，整张图就糊了 */
  ok(combo.coastFill === 'none', '海岸线只描边（fill:none，不遮地形）', 'fill=' + combo.coastFill);
  ok(combo.landFill && combo.landFill !== 'none' && combo.landFill !== 'rgba(0, 0, 0, 0)',
    '陆地填充层仍有填充（DEM 覆盖范围外兜底）', 'fill=' + combo.landFill);

  console.log('\n=== 四、地形图层内容 ===');
  const dom = JSON.parse(await val(`(function(){
    var c = document.querySelector('.map-canvas');
    var img = c.querySelector('.terr-hills');
    var lg = document.querySelector('.legend-terrain');
    var sw = document.querySelectorAll('.legend-terrain .terr-swatch');
    function visCount(sel){
      var n=0, all=c.querySelectorAll(sel);
      for (var i=0;i<all.length;i++){
        var s=window.getComputedStyle(all[i]);
        if (s.display!=='none' && s.visibility!=='hidden') n++;
      }
      return n;
    }
    return JSON.stringify({
      hasImg: !!img,
      imgNS: img ? img.namespaceURI : null,
      imgTag: img ? img.tagName : null,
      imgBox: img ? img.getAttribute('width') + '×' + img.getAttribute('height') : null,
      idxCount: c.querySelectorAll('.terr-contours path.is-index').length,
      normCount: c.querySelectorAll('.terr-contours path.is-normal').length,
      visIndex: visCount('.terr-contours path.is-index'),
      visNormal: visCount('.terr-contours path.is-normal'),
      peakCount: c.querySelectorAll('.terr-peak').length,
      lod: c.getAttribute('data-lod'),
      legendDisplay: lg ? window.getComputedStyle(lg).display : null,
      swatchColors: Array.prototype.map.call(sw, function(s){ return window.getComputedStyle(s).backgroundColor; }),
      swatchText: Array.prototype.map.call(document.querySelectorAll('.terr-scale-ticks span'), function(s){return s.textContent;}),
      note: (document.querySelector('.legend-terrain-note')||{}).textContent || ''
    });
  })()`));
  ok(dom.hasImg, '晕渲底图 <image> 存在');
  ok(dom.imgNS === 'http://www.w3.org/2000/svg', '是 SVGImageElement（非 HTML img）', dom.imgTag);
  ok(dom.imgBox === '2304×1301', '底图尺寸与 geo-wterrain.js 的 pad 一致', dom.imgBox);
  ok(dom.idxCount > 0, '存在计曲线', dom.idxCount + ' 条');
  ok(dom.normCount > 0, '存在一般等高线（DOM 中）', dom.normCount + ' 条');
  ok(dom.peakCount > 0, '高程点已渲染', dom.peakCount + ' 个');
  ok(dom.legendDisplay && dom.legendDisplay !== 'none', '地形图例已显示');
  ok(dom.swatchColors.length === 5 && new Set(dom.swatchColors).size === 5,
    '分层设色色带 5 档且颜色互不相同', dom.swatchColors.join(' '));
  ok(dom.visIndex > 0, '计曲线可见', dom.visIndex + ' 条');
  ok(dom.visNormal === 0,
    '默认比例尺（lod ' + dom.lod + '）下一般等高线收起，避免糊成白雾',
    '可见 ' + dom.visNormal + ' 条');
  console.log('  色带刻度: ' + dom.swatchText.join(' / '));
  console.log('  说明: ' + dom.note);

  /* 挑一个能看到陆地与山地的台风：默认最新台风可能在远洋，一屏全是海，
     地形有没有加载根本看不出来。点左栏列表第一项即可。 */
  await val(`(function(){ var it=document.querySelector('[data-ty-id]'); if(it) it.click(); })()`);
  await sleep(3200);
  await shot('combo-final');

  console.log('\n=== 五、地形是否真的改变了画面（隐藏地形层前后截图对比） ===');
  const withTerr = await shotBuf();
  fs.writeFileSync(path.join(OUT, 'combo-with-terrain.png'), withTerr);
  await val(`document.querySelector('.map-terrain-layer').style.display='none'`);
  await sleep(900);
  const noTerr = await shotBuf();
  fs.writeFileSync(path.join(OUT, 'combo-no-terrain.png'), noTerr);
  await val(`document.querySelector('.map-terrain-layer').style.display=''`);
  await sleep(500);
  ok(!buffersEqual(withTerr, noTerr), '隐藏地形层后画面确实不同（地形不是"只换个颜色"）',
    (withTerr.length / 1024).toFixed(0) + 'KB vs ' + (noTerr.length / 1024).toFixed(0) + 'KB');

  console.log('\n=== 六、缩放后等高线仍锐利，且与海岸线共用同一视野变换 ===');
  const zoomInfo = JSON.parse(await val(`(async function(){
    var c = document.querySelector('.map-canvas');
    /* 缩放倍率要从**实际 transform** 里读 scale()，不能读 data-view。
       data-view 是 syncView 写的可观测属性，恒等视野时为空串。 */
    function z(){
      var g = c.querySelector('.map-land-layer .map-view-g');
      if (!g) { return 1; }
      var tr = g.getAttribute('transform') || '';
      var m = /scale\\(([\\d.]+)/.exec(tr);
      return m ? parseFloat(m[1]) : 1;
    }
    var before = z();
    var btn = document.querySelector('[data-zoom="in"]');
    if (!btn) { return JSON.stringify({err:'no zoom button'}); }
    for (var i=0;i<3;i++){ btn.click(); await new Promise(function(r){setTimeout(r,320);}); }
    await new Promise(function(r){ setTimeout(r, 700); });
    var p = document.querySelector('.terr-contours path.is-index');
    var sw = p ? window.getComputedStyle(p).strokeWidth : null;
    var gt = c.querySelector('.map-terrain-layer .map-view-g');
    var gl = c.querySelector('.map-land-layer .map-view-g');
    return JSON.stringify({
      before: before, after: z(), contourStrokeWidth: sw,
      terrainTransform: gt ? (gt.getAttribute('transform')||'identity') : null,
      landTransform: gl ? (gl.getAttribute('transform')||'identity') : null,
      lod: c.getAttribute('data-lod'),
      visNormal: (function(){var n=0,a=c.querySelectorAll('.terr-contours path.is-normal');
        for(var i=0;i<a.length;i++){if(window.getComputedStyle(a[i]).display!=='none')n++;}return n;})()
    });
  })()`, true));
  ok(!zoomInfo.err, '缩放控件可用');
  ok(zoomInfo.after > zoomInfo.before, '放大成功',
    zoomInfo.before.toFixed(2) + '× → ' + zoomInfo.after.toFixed(2) + '×');
  ok(zoomInfo.contourStrokeWidth && parseFloat(zoomInfo.contourStrokeWidth) < 2,
    '放大后等高线线宽不随缩放变粗（non-scaling-stroke）', zoomInfo.contourStrokeWidth);
  ok(zoomInfo.terrainTransform === zoomInfo.landTransform,
    '地形图层与陆地共用同一视野变换（缩放后不错位）',
    (zoomInfo.terrainTransform || '').slice(0, 40));
  console.log('  放大后 lod=' + zoomInfo.lod + '，一般等高线可见 ' + zoomInfo.visNormal + ' 条');
  await sleep(700);
  await shot('combo-zoomed');

  console.log('\n=== 七、控制台错误 ===');
  const realErr = errors.filter((e) => !/favicon|ERR_FAILED.*favicon/i.test(e));
  ok(realErr.length === 0, '无控制台错误', realErr.slice(0, 3).join(' | ') || '0 条');

  console.log('\n================  ' + PASS + ' PASS / ' + FAIL + ' FAIL  ================\n');
  try { ws.close(); } catch (e) {}
  edge.kill();
  process.exit(FAIL ? 1 : 0);
})().catch((e) => { console.error(e); try { edge.kill(); } catch (x) {} process.exit(1); });
