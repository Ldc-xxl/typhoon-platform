/**
 * 地形图层离屏渲染对照：把 P1 地形图与普通地图，在**同一个有陆地的视野**
 * 下并排渲染成一张对比图，用于肉眼确认「地形图确实有地形」。
 *
 * 为什么要单独写这个脚本：探针脚本里，地图视野由台风数据决定
 * （最新台风常在西太平洋远洋，画面全是海，看不到山，
 *  会误判成"地形图没生效"）。这里绕开数据，直接用 proj()
 * 构造一段固定视野（福建—台湾—华南），逐像素比对两套底图。
 *
 * 用法：node tools/shot-terrain-compare.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9700 + (process.pid % 120);
const PROF = path.join(os.tmpdir(), 'zfz-tcmp-' + Date.now());
const OUT = 'C:/Users/17168/Desktop/web课程项目/typhoon_platform/_probe_shots';
const BASE = 'http://localhost:8080/typhoon_platform/index.html';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (p, m) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: m || 'GET' },
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
  const send = (me, p) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: me, params: p || {} })); });
  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 900, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });
  await sleep(12000);

  const ev = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true });
    const res = r.result || {};
    if (res.exceptionDetails) throw new Error(res.exceptionDetails.exception ? res.exceptionDetails.exception.description : res.exceptionDetails.text);
    return res.result ? res.result.value : null;
  };

  /* 换掉整页内容，做一张纯底图对照图。
     这里刻意不复用页面里的地图组件：一来绕开台风数据决定的视野，
     二来能精确定位到「福建—台湾—台湾海峡」，这一屏既有山又有海。 */
  const html = await ev(`(function(){
    var g = (window.ZFZ && ZFZ.geo && ZFZ.geo.wterrain) || null;
    if (!g) { return JSON.stringify({err:'no wterrain'}); }
    var w = (window.ZFZ && ZFZ.geo && ZFZ.geo.wpac) || null;
    var p = g.pad, W = p[2]-p[0], H = p[3]-p[1];
    // 视野：proj(119.5,26) 附近放大 1.6 倍 —— 覆盖台湾岛 + 福建 + 华南丘陵，
    // 一屏内同时有「海（台东/南海）、平原（台西/福建沿海）、高山（中央山脉）」
    // 三种地物，正好检验分层设色与晕渲是否都能表达出来。
    var LNG0=118.0894, LAT0=24.4798, KX=26.79, KY=27.11, OX=351, OY=198;
    var cx = OX + (119.5-LNG0)*KX, cy = OY + (LAT0-26)*KY;
    var sc = 1.6;
    var tf = 'translate(600 470) scale('+sc+') translate('+(-cx)+' '+(-cy)+')';

    function landPaths(){
      if (!w || !w.land) { return ''; }
      var s='';
      for (var i=0;i<w.land.length;i++){ s += '<path class="mp-land" fill-rule="evenodd" d="'+w.land[i]+'"/>'; }
      return s;
    }
    function contours(){
      var s='';
      for (var i=0;i<g.contour.length;i++){ s += '<path class="'+g.contour[i][1]+'" d="'+g.contour[i][2]+'"/>'; }
      return s;
    }
    function peaks(){
      var s='';
      for (var i=0;i<g.peaks.length;i++){
        var k=g.peaks[i];
        s += '<g transform="translate('+k[0]+','+k[1]+')"><path class="mp-pk" d="M0 -3 L2.5 1.6 L-2.5 1.6 Z"/><text class="mp-pkt" x="0" y="8">'+k[2]+'</text></g>';
      }
      return s;
    }

    function panel(mode, title){
      var bg = mode==='plain' ? '#0E3B59' : '#082631';
      /* 位图自身坐标必须从 0 起算：SVG <image> 的 x/y 不接受负值
         （本项目 DEM pad 左/上为负，pad[0]=-348 / pad[1]=-548），
         负偏移要挪到外层 translate 上 —— 与 ui.js 的 lyrTerrain 同一处理。 */
      var imgHtml = '';
      if (mode !== 'plain') {
        imgHtml = '<g transform="translate('+p[0]+' '+p[1]+')">'
          + '<image x="0" y="0" width="'+W+'" height="'+H+'" '
          + 'preserveAspectRatio="none" href="assets/terrain/terrain-hillshade.png"/></g>';
      }
      /* 两套底图必须用**同一套图层结构**，否则对比不公平：
   地形图的海岸线图层仍保留填充（作为 DEM 覆盖范围外的兜底），
   位图的海面 alpha=0，两者叠加后海面统一透出容器底色。
   早期版本这里给地形设了 fill:none，结果海面变成容器黑底，
   看起来像"位图把海吃掉了"，是脚本而非产品的 bug。 */
      return '<div class="mp-panel">'
        + '<svg viewBox="0 0 1200 700" preserveAspectRatio="xMidYMid meet">'
        + '<rect x="0" y="0" width="1200" height="700" fill="'+bg+'"/>'
        + '<g transform="'+tf+'">'
        +   '<g class="mp-landwrap" style="fill:'+(mode==='plain'?'#2A5C80':'#2E5638')+';stroke:'+(mode==='plain'?'#5892BC':'#5B8C57')+';stroke-width:0.7;vector-effect:non-scaling-stroke">'+landPaths()+'</g>'
        +   imgHtml
        +   (mode==='plain'?'':('<g class="mp-contours" style="fill:none">'+contours()+peaks()+'</g>'))
        + '</g></svg>'
        + '<div class="mp-title">'+title+'</div></div>';
    }

    document.body.innerHTML =
      '<div class="mp-wrap">'
      + panel('plain', '改造前：普通地图（地形图曾与它仅差一个颜色）')
      + panel('terrain', '改造后：地形图（晕渲 + 分层设色 + 矢量等高线）')
      + '</div>'
      + '<style>'
      + '.mp-wrap{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:10px;background:#0b1a26;font-family:system-ui,sans-serif}'
      + '.mp-panel{position:relative;background:#000;border-radius:6px;overflow:hidden}'
      + '.mp-panel svg{display:block;width:100%}'
      + '.mp-title{position:absolute;left:8px;top:8px;color:#fff;font-size:13px;background:rgba(0,0,0,.62);padding:4px 9px;border-radius:4px}'
      /* 与 app.css 的 .terr-contours 规则保持一致（含 LOD 渐进显示） */
      + '.mp-contours path{fill:none;stroke:rgba(255,255,255,.26);stroke-width:.6;vector-effect:non-scaling-stroke}'
      + '.mp-contours path.is-index{stroke:rgba(255,255,255,.62);stroke-width:1.1}'
      /* 放大 2.4 倍后仍属小比例尺，一般等高线仍应收起（与线上一致） */
      + '.mp-contours path.is-normal{display:none}'
      + '.mp-pk{fill:#FFE9A8;stroke:rgba(0,0,0,.55);stroke-width:.6}'
      + '.mp-pkt{fill:#FFF6DC;font-size:9px;text-anchor:middle;font-family:monospace;paint-order:stroke;stroke:rgba(0,0,0,.7);stroke-width:2px;stroke-linejoin:round}'
      + '</style>';
    return JSON.stringify({ok:true, pad:p, W:W, H:H, contours:g.contour.length, peaks:g.peaks.length});
  })()`);
  console.log('渲染参数: ' + html);
  await sleep(4000);

  const s = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  const out = path.join(OUT, 'terrain-compare.png');
  fs.writeFileSync(out, Buffer.from(s.result.data, 'base64'));
  console.log('已输出: ' + out + '  (' + (s.result.data.length * 0.75 / 1024).toFixed(0) + ' KB)');

  try { ws.close(); } catch (e) {}
  edge.kill();
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });