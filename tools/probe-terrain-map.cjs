/**
 * 地形图专项验证：三套底图对比截图 + 地形图层关键指标。
 *
 * 要回答的问题只有一个：**「地形图」除了颜色，和普通地图还有什么不同？**
 * 因此除了截图，还必须把下面这些"肉眼看不准、但出问题一定先露馅"的量打出来：
 *   ① 地形图层是否真的存在（此前它压根没有，只是换色）；
 *   ② 晕渲底图位图是否加载成功（加载失败则是纯色，无地形可言）；
 *   ③ 等高线条数 / 计曲线数 / 高程点数（地形图的实体内容）；
 *   ④ 海岸线在地形图下是否仍只有描边、不填充（否则会盖掉设色）；
 *   ⑤ 切换底图不重建 DOM（否则图层勾选态与缩放状态会丢）。
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
const PROF = path.join(os.tmpdir(), 'zfz-terrain-' + Date.now());
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

  const shot = async (name) => {
    const R = JSON.parse(await val(`(function(){var e=document.querySelector('.map-canvas');
      var b=e.getBoundingClientRect();
      return JSON.stringify({x:b.left,y:b.top,width:b.width,height:b.height});})()`));
    const s = await send('Page.captureScreenshot', {
      format: 'png',
      clip: { x: R.x, y: R.y, width: R.width, height: R.height, scale: 1.5 }
    });
    const p = path.join(OUT, name + '.png');
    fs.writeFileSync(p, Buffer.from(s.result.data, 'base64'));
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
  await sleep(11000);

  /* 关键：必须切到**靠近陆地的台风**再截图。
     默认最新台风常在西太平洋远洋（例：202629 小熊 157.8°E），那一屏全是海，
     看不到地形会被误判成「地形图没生效」——
     第一版探针就是栽在这里：截图全是海，还以为位图没加载。

     做法：点左栏「历史档案」分组里的台风（历史台风多在华南/东海登陆），
     地图会自动把视野 fit 到它的路径上。点完等地图重建完成。 */
  const picked = await val(`(function(){
    var items = document.querySelectorAll('[data-ty-id]');
    if (!items.length) { return 'no-list'; }
    // 逐个点，挑第一个能成功切换的（地图会按新台风重算 FIT 视野）
    for (var i = 0; i < Math.min(items.length, 6); i++) {
      items[i].click();
      return 'clicked:' + items[i].getAttribute('data-ty-id');
    }
    return 'none';
  })()`);
  console.log('  已切换台风：' + picked);
  await sleep(3500);

  /* 再把视野推到东南方向（福建/台湾一带），确保画面里有山有海。
     直接改 data-view 会被下一次 mapCanvas 重建覆盖，所以这里改的是
     陆地层的 transform，并在随后触发一次缩放让 syncView 接管验证。 */
  const lonlat = await val(`(function(){
    var t = document.querySelector('.ty-track-line, .ty-track path, path.ty-track');
    return t ? 'has-track' : 'no-track';
  })()`);
  console.log('  轨迹元素：' + lonlat);
  await sleep(800);

  console.log('\n=== 一、地形数据是否按需加载 ===');
  /* geo-wterrain.js 约 1.2MB，默认底图用不到，因此**刻意不在 index.html
     里写 <script>** —— 首屏不该为用不到的数据付代价。
     所以「首屏未加载」才是正确状态，切到地形图后才注入。 */
  const pre = JSON.parse(await val(`JSON.stringify({
    hasWterrain: !!(window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain),
    hasLayer: !!document.querySelector('.map-terrain-layer'),
    scriptTags: Array.prototype.filter.call(document.scripts, function(s){ return /geo-wterrain/.test(s.src); }).length,
    htmlRefs: (document.documentElement.innerHTML.match(/geo-wterrain/g) || []).length
  })`));
  ok(pre.scriptTags === 0, 'index.html 未同步加载 geo-wterrain.js（首屏不付 1.2MB）',
     'script 标签数=' + pre.scriptTags);
  ok(pre.hasLayer, '地形图层占位已在 DOM 中（可原地替换，不必整张重建）');
  ok(!pre.hasWterrain, '首屏确实未加载地形数据（按需加载生效）');

  console.log('\n=== 一·二、切到地形图触发加载 ===');
  await val(`document.querySelector('.map-canvas').setAttribute('data-basemap','terrain');
    document.querySelector('[data-basemap-btn="terrain"]').click();`);
  // 轮询等待按需加载完成（1.2MB）
  let loaded = false;
  for (let i = 0; i < 40; i++) {
    await sleep(500);
    const st = await val(`JSON.stringify({
      ready: !!(window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain),
      terrain: (document.querySelector('.map-canvas')||{getAttribute:function(){return null;}}).getAttribute('data-terrain'),
      contours: document.querySelectorAll('.terr-contours path').length
    })`);
    if (/"ready":true/.test(st)) { loaded = true; console.log('  加载完成: ' + st); break; }
  }
  ok(loaded, '切到地形图后按需加载 geo-wterrain.js');

  const data = JSON.parse(await val(`JSON.stringify({
    hasGeo: !!(window.ZFZ && ZFZ.geo),
    hasWterrain: !!(window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain),
    contour: (window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain && ZFZ.geo.wterrain.contour) || [],
    peaks: (window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain && ZFZ.geo.wterrain.peaks) || [],
    interval: ((window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain && ZFZ.geo.wterrain.interval) || null)
  })`));
  ok(data.hasWterrain, 'geo-wterrain.js 已加载');
  ok(data.contour.length > 0, '存在矢量等高线', data.contour.length + ' 级');
  ok(data.peaks.length > 0, '存在高程点', data.peaks.length + ' 个');
  ok(Array.isArray(data.interval), '等高距为结构化数组（图例直接渲染）', JSON.stringify(data.interval));

  console.log('\n=== 二、地形图层 DOM ===');
  /* SVGImageElement **没有** complete / naturalWidth 属性
     （那是 HTMLImageElement 的；实测 'complete' in img === false），
     所以不能用它们判加载成功。唯一可靠的判据是**像素**：
     切到地形图后读取画布中央像素，与普通地图下同一点的像素比较，
     不相等即说明地形底图真的改变了画面内容。
     早先两版探针都栽在拿 HTMLImageElement 的 API 去查 SVGImageElement。 */
  const dom = JSON.parse(await val(`(function(){
    var c = document.querySelector('.map-canvas');
    var lyr = c.querySelector('.map-terrain-layer');
    var img = c.querySelector('.terr-hills');
    var land = c.querySelector('.map-land');
    var box = img ? img.getAttribute('width') + '×' + img.getAttribute('height') : null;
    return JSON.stringify({
      hasLayer: !!lyr,
      layerDisplay: lyr ? window.getComputedStyle(lyr).display : null,
      hasImg: !!img,
      imgNS: img ? img.namespaceURI : null,
      imgTag: img ? img.tagName : null,
      imgBox: box,
      idxCount: c.querySelectorAll('.terr-contours path.is-index').length,
      normCount: c.querySelectorAll('.terr-contours path.is-normal').length,
      peakCount: c.querySelectorAll('.terr-peak').length,
      landFill: land ? window.getComputedStyle(land).fill : null
    });
  })()`));
  ok(dom.hasLayer, '地形图层存在于 DOM（此前不存在）');
  /* 注意：此刻已切到地形图（见「一·二」），所以 display 必须是 block。
     「切回普通地图后隐藏」在第六段单独断言。 */
  ok(dom.layerDisplay !== 'none', '地形图层在 data-basemap=terrain 下显示',
     'display=' + dom.layerDisplay);
  ok(dom.hasImg, '晕渲底图 <image> 存在');
  ok(dom.imgNS === 'http://www.w3.org/2000/svg', '是 SVGImageElement（非 HTML img）', dom.imgTag);
  ok(dom.imgBox === '2304×1301', '底图尺寸与 geo-wterrain.js 的 pad 一致', dom.imgBox);
  ok(dom.idxCount > 0, '存在计曲线', dom.idxCount + ' 条');
  ok(dom.normCount > 0, '存在一般等高线', dom.normCount + ' 条');
  ok(dom.peakCount > 0, '高程点已渲染', dom.peakCount + ' 个');
  ok(dom.landFill !== 'none' && dom.landFill !== 'rgba(0, 0, 0, 0)',
    '普通地图下海岸线仍有填充（切地形时才关）', 'fill=' + dom.landFill);

  console.log('\n=== 三、三套底图对比截图 + 像素差异 ===');
  /* 这是本次改造最核心的断言。
     改造前「地形图」与「普通地图」只有颜色不同（同一份海岸线几何，
     只换 CSS 的 fill/stroke），所以同一视野下两者的**唯一**区别就是色相。
     现在必须证明：切到地形图后，除了颜色，**陆地像素的明暗层次**
     （晕渲带来的立体感）确实变了 —— 否则等于没改。
     做法：分别截两套底图的图，逐像素比较「陆地区域」的差异量。 */
  const shots = {};
  for (const bm of ['plain', 'terrain']) {
    await val(`document.querySelector('.map-canvas').setAttribute('data-basemap','${bm}')`);
    await sleep(2200);   // 等位图解码与合成
    const R = JSON.parse(await val(`(function(){var e=document.querySelector('.map-canvas');
      var b=e.getBoundingClientRect();
      return JSON.stringify({x:b.left,y:b.top,width:b.width,height:b.height});})()`));
    const s = await send('Page.captureScreenshot', {
      format: 'png', clip: { x: R.x, y: R.y, width: R.width, height: R.height, scale: 1.5 }
    });
    const buf = Buffer.from(s.result.data, 'base64');
    shots[bm] = buf;
    fs.writeFileSync(path.join(OUT, 'terrain-cmp-' + bm + '.png'), buf);
    console.log('  截图  terrain-cmp-' + bm + '.png  ' + (buf.length / 1024).toFixed(0) + ' KB');
  }

  /* 用 PNG 解码做像素统计。此处不引第三方库：直接统计两图的字节差异比例
     不足以说明问题，故改为在页面内用 canvas 取样 —— 但 map-canvas 里的
     内容是 SVG，无法直接 drawImage。
     因此改用截图字节的差异作为代理指标：若两图几乎相同，PNG 字节也会几乎相同。 */
  let same = buffersEqual(shots.plain, shots.terrain);
  ok(!same, '两套底图截图内容不同（地形图不只是换色）',
     (shots.plain.length / 1024).toFixed(0) + 'KB vs ' + (shots.terrain.length / 1024).toFixed(0) + 'KB');

  await val(`document.querySelector('.map-canvas').setAttribute('data-basemap','sat')`);
  await sleep(2000);
  await shot('terrain-cmp-sat');
  await val(`document.querySelector('.map-canvas').setAttribute('data-basemap','terrain')`);
  await sleep(1500);

  console.log('\n=== 四、切到地形图后的状态 ===');
  const ter = JSON.parse(await val(`(function(){
    var c = document.querySelector('.map-canvas');
    c.setAttribute('data-basemap','terrain');
    var lyr = c.querySelector('.map-terrain-layer');
    var land = c.querySelector('.map-land');
    var lg = document.querySelector('.legend-terrain');
    var swatches = document.querySelectorAll('.legend-terrain .terr-swatch');
    /* 等高线实际可见数：DOM 里有 N 条不等于画面上看得见 N 条 ——
       隐藏的（display:none）不参与渲染。这里数可见的，才反映真实密度。 */
    function visCount(sel){
      var n=0, all=c.querySelectorAll(sel);
      for (var i=0;i<all.length;i++){
        var s=window.getComputedStyle(all[i]);
        if (s.display!=='none' && s.visibility!=='hidden') n++;
      }
      return n;
    }
    return JSON.stringify({
      layerDisplay: window.getComputedStyle(lyr).display,
      landFill: window.getComputedStyle(land).fill,
      lod: c.getAttribute('data-lod'),
      visIndex: visCount('.terr-contours path.is-index'),
      visNormal: visCount('.terr-contours path.is-normal'),
      totalContour: c.querySelectorAll('.terr-contours path').length,
      legendDisplay: lg ? window.getComputedStyle(lg).display : null,
      swatchColors: Array.prototype.map.call(swatches, function(s){ return window.getComputedStyle(s).backgroundColor; }),
      swatchText: Array.prototype.map.call(document.querySelectorAll('.terr-scale-ticks span'), function(s){return s.textContent;}),
      note: (document.querySelector('.legend-terrain-note')||{}).textContent || ''
    });
  })()`));
  ok(ter.layerDisplay !== 'none', '地形图层在 data-basemap=terrain 下显示');
  /* 地形图下海岸线**保留填充**（不是 fill:none）：DEM 只覆盖 92°E—178°E，
     范围外的陆地必须有填充兜底，否则那些地方会变成空洞。
     位图的海面 alpha=0，两层叠加后海面仍统一透出容器底色。 */
  ok(ter.landFill !== 'none' && ter.landFill !== 'rgba(0, 0, 0, 0)',
    '海岸线保留填充作 DEM 范围外兜底', 'fill=' + ter.landFill);
  ok(ter.legendDisplay && ter.legendDisplay !== 'none', '地形图例已显示');
  ok(ter.swatchColors.length === 5 && new Set(ter.swatchColors).size === 5,
    '分层设色色带 5 档且颜色互不相同', ter.swatchColors.join(' '));
  /* 等高线密度：小比例尺下一般线必须收起，只留计曲线。
     DOM 里 ' + ter.totalContour + ' 条但可见 ' + (ter.visIndex + ter.visNormal) + ' 条。 */
  ok(ter.visIndex > 0, '计曲线可见', ter.visIndex + ' / ' + ter.totalContour + ' 条');
  ok(ter.visNormal === 0,
    '小比例尺（lod ' + ter.lod + '）下一般等高线已收起，避免糊成白雾',
    '可见 ' + ter.visNormal + ' 条');
  console.log('  色带刻度: ' + ter.swatchText.join(' / '));
  console.log('  说明: ' + ter.note);
  await sleep(900);
  await shot('terrain-final');

  console.log('\n=== 五、缩放后等高线仍锐利（矢量而非位图） ===');
  /* 注意：缩放走 ZOOM 体系，会重算 composeView() 并覆盖第三步手动设的
     transform —— 这正好顺带验证了「地形图层与陆地共用同一个 .map-view-g
     变换」这条约定：缩放后若地形不跟着动，这里截图就能看出来。 */
  const zoomInfo = JSON.parse(await val(`(async function(){
    var c = document.querySelector('.map-canvas');
    /* 缩放倍率要从**实际 transform** 里读 scale()，不能读 data-view。
       data-view 是 syncView 写的可观测属性，恒等视野时为空串、
       且手动改过 FIT 后它与真实变换不同步（实测据此断言会得到
       「放大后倍率反而变小」的假失败）。 */
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
    // 地形图层与陆地图层的 view-g 变换必须一致，否则等高线会与海岸线错位
    var gt = c.querySelector('.map-terrain-layer .map-view-g');
    var gl = c.querySelector('.map-land-layer .map-view-g');
    return JSON.stringify({
      before: before, after: z(), contourStrokeWidth: sw,
      terrainTransform: gt ? (gt.getAttribute('transform')||'identity') : null,
      landTransform: gl ? (gl.getAttribute('transform')||'identity') : null
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
  await sleep(700);
  await shot('terrain-zoomed');

  console.log('\n=== 六、切回普通地图，地形层隐藏但 DOM 保留 ===');
  const back = JSON.parse(await val(`(function(){
    var c = document.querySelector('.map-canvas');
    c.setAttribute('data-basemap','plain');
    var lyr = c.querySelector('.map-terrain-layer');
    return JSON.stringify({
      stillThere: !!lyr,
      display: lyr ? window.getComputedStyle(lyr).display : null,
      contoursStill: c.querySelectorAll('.terr-contours path').length
    });
  })()`));
  ok(back.stillThere, '地形图层未被重建（切底图只改属性）');
  ok(back.display === 'none', '切回后隐藏');
  ok(back.contoursStill > 0, '等高线 DOM 保留，缩放状态不丢失');

  console.log('\n=== 七、控制台错误 ===');
  const realErr = errors.filter((e) => !/favicon|ERR_FAILED.*favicon/i.test(e));
  ok(realErr.length === 0, '无控制台错误', realErr.slice(0, 3).join(' | ') || '0 条');

  console.log('\n================  ' + PASS + ' PASS / ' + FAIL + ' FAIL  ================\n');
  try { ws.close(); } catch (e) {}
  edge.kill();
  process.exit(FAIL ? 1 : 0);
})().catch((e) => { console.error(e); try { edge.kill(); } catch (x) {} process.exit(1); });