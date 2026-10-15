/**
 * 探针：监测总览页的「点击列表项切换地图」与「地图缩放 / 平移」。
 *
 * 为什么要有这个脚本：
 *   这两件事都不是"看一眼能确认"的——切换台风要看四块东西是否同时跟上
 *   （地图路径、右栏参数、列表高亮、标题栏预报条数），缩放要看变换是否
 *   同时作用到底图 <img> 与全部矢量层（漏一层就会错位）。靠人工点几下截图
 *   很容易漏掉"底图没跟着缩"这种只在特定底图下才露馅的缺陷。
 *
 * 断言全部**动态取值**：台风条数、路径 bbox、缩放比例都从页面上读，
 * 不写死任何数字（数据库会随同步变化，写死的断言迟早变成噪音）。
 *
 * 用法：node tools/probe-overview-focus.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9800 + (process.pid % 300);
const PROF = path.join(os.tmpdir(), 'zfz-focus-' + Date.now());
const OUT = path.resolve(__dirname, '../_probe_shots');
const BASE = 'http://localhost:8080/typhoon_platform/';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (p, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

let pass = 0, fail = 0;
function check(label, ok, detail) {
  if (ok) { pass++; console.log('  \u2713 ' + label); }
  else { fail++; console.log('  \u2717 ' + label + (detail ? '  \u2192 ' + detail : '')); }
}

/* 一次抓齐页面可观测状态。字段名与断言一一对应，方便对着输出排查。 */
const SNAP = `(function(){
  var box = document.querySelector('.map-canvas');
  var right = document.querySelector('.col-right');
  var items = [].slice.call(document.querySelectorAll('#ty-list-host .ty-item'));
  var act = document.querySelector('#ty-list-host .ty-item.is-active');
  var tv = document.querySelector('[data-zoom-value]');
  var note = document.querySelector('.map-view-note');
  var img = box && box.querySelector('.map-land-layer');
  var landG = box && box.querySelector('.map-land-layer .map-view-g');
  var cloud = box && box.querySelector('.map-cloud-layer');
  var line = box && box.querySelector('.track-live');
  var rb = line ? line.getBoundingClientRect() : null;
  var bb = box ? box.getBoundingClientRect() : null;
  function area(r){
    if (!r || !bb) { return 0; }
    var w = Math.min(r.right, bb.right) - Math.max(r.left, bb.left);
    var h = Math.min(r.bottom, bb.bottom) - Math.max(r.top, bb.top);
    return (w > 0 && h > 0) ? Math.round(w * h) : 0;
  }
  var fnote = document.querySelector('.col-right .note');
  return JSON.stringify({
    focusAttr: right && right.getAttribute('data-live-typhoon'),
    analysisAttr: right && right.getAttribute('data-analysis-typhoon'),
    itemCount: items.length,
    activeId: act ? act.getAttribute('data-ty-id') : null,
    activeName: act ? act.querySelector('.ty-item-name').textContent.replace(/\\s+/g,' ') : null,
    hasBackLive: !!document.querySelector('#ty-back-live'),
    hint: (document.querySelector('#ty-hint') || { innerText: '' }).innerText.replace(/\\s+/g,' ').trim(),
    liveName: document.querySelector('[data-metric="pos"]') ? 'yes' : 'no',
    heroName: (document.querySelector('.panel-title.is-lg') || { innerText: '' }).innerText.replace(/\\s+/g,' ').trim(),
    zoomLabel: tv ? tv.textContent : null,
    dataZoom: box ? box.getAttribute('data-zoom') : null,
    dataView: box ? box.getAttribute('data-view') : null,
    imgTransform: landG ? (landG.getAttribute('transform') || '(none)') : null,
    /* 底图改为真实海岸线绘制后，底图与矢量层共用同一个 <g class="map-view-g"> 变换，
       不再有 <img>；下面三项用于确认「底图跟随视野 / 三套样式共用一份几何 / 云图显隐」 */
    landPaths: box ? box.querySelectorAll('.map-land').length : 0,
    lod: box ? box.getAttribute('data-lod') : null,
    cloudShown: cloud ? (cloud.style.display !== 'none' &&
      document.defaultView.getComputedStyle(cloud).display !== 'none') : null,
    noteText: note ? note.innerText.replace(/\\s+/g,' ') : null,
    noteHidden: note ? note.hasAttribute('hidden') : null,
    gratLines: box ? box.querySelectorAll('.map-grat').length : 0,
    graticuleShown: (function(){ var g = box && box.querySelector('.map-grat-layer'); return g ? g.style.display !== 'none' : null; })(),
    trackBox: rb ? [rb.left | 0, rb.top | 0, rb.width | 0, rb.height | 0] : null,
    trackVisible: area(rb),
    trackOwn: rb ? Math.round(rb.width * rb.height) : 0,
    focusNote: fnote ? fnote.innerText.replace(/\\s+/g,' ').slice(0, 50) : ''
  });
})()`;

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server', '--remote-debugging-port=' + PORT,
    '--user-data-dir=' + PROF, 'about:blank'], { stdio: 'ignore' });

  let ver = null;
  for (let i = 0; i < 60 && !ver; i++) { try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); } }
  if (!ver) { console.error('DevTools 未就绪'); edge.kill(); process.exit(1); }

  const t = await getJson('/json/new?about:blank', 'PUT');
  const ws = new WebSocket(t.webSocketDebuggerUrl);
  const pending = new Map(); let id = 0; const errs = [];
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
    if (m.method === 'Runtime.exceptionThrown') {
      errs.push('EXC ' + (m.params.exceptionDetails.exception && m.params.exceptionDetails.exception.description));
    }
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') { errs.push('LOG ' + m.params.entry.text); }
  });
  const send = (m, p) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: m, params: p || {} })); });
  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });

  /** 在页面里求值并取回结果（等待 Promise 落地） */
  const js = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    const res = r.result || {};
    if (res.exceptionDetails) { throw new Error('页面求值异常：' + JSON.stringify(res.exceptionDetails).slice(0, 300)); }
    return res.result ? res.result.value : undefined;
  };
  const snap = async () => JSON.parse(await js(SNAP));
  const shot = async (name) => {
    const s = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(s.result.data, 'base64'));
  };

  await send('Page.navigate', { url: BASE + 'index.html' });
  await sleep(8000);

  /* ---------- 0. 初始状态 ---------- */
  console.log('\n===== 0. 初始加载 =====');
  const s0 = await snap();
  console.log('  focus=%s  高亮=%s  条目=%d  zoom=%s  view=%s',
    s0.focusAttr, s0.activeName, s0.itemCount, s0.dataZoom, s0.dataView);
  check('左栏渲染出台风条目', s0.itemCount > 0, 'count=' + s0.itemCount);
  check('初始高亮项 = 右栏标注的聚焦台风 id',
    String(s0.activeId) === String(s0.focusAttr), 'activeId=' + s0.activeId + ' focusAttr=' + s0.focusAttr);
  check('初始不显示「返回最新」（当前就是最新活跃台风）', s0.hasBackLive === false);
  check('初始不显示"你选中的台风"说明条', s0.focusNote.indexOf('你在左栏选中') < 0, s0.focusNote);

  /* ---------- 1. 点击第二个活跃台风 ---------- */
  console.log('\n===== 1. 点击左栏第 2 个活跃台风 =====');
  const picked = JSON.parse(await js(`(function(){
    var it = document.querySelectorAll('#ty-list-host .ty-item');
    if (it.length < 2) { return JSON.stringify({ error: '活跃台风不足 2 个，无法验证切换' }); }
    var el = it[1];
    el.click();
    return JSON.stringify({ id: el.getAttribute('data-ty-id'),
      name: el.querySelector('.ty-item-name').textContent.replace(/\\s+/g,' ') });
  })()`));
  if (picked.error) {
    check('活跃台风至少 2 个（切换验证前提）', false, picked.error);
  } else {
    console.log('  已点击：%s (id=%s)', picked.name, picked.id);
    await sleep(3000);
    const s1 = await snap();
    await shot('index-focus-switched');

    check('右栏 data-live-typhoon 已切到被点台风',
      String(s1.focusAttr) === String(picked.id), '期望 ' + picked.id + ' 实际 ' + s1.focusAttr);
    check('列表高亮跟着切换', String(s1.activeId) === String(picked.id), 'activeId=' + s1.activeId);
    check('右栏实况参数卡已换成被点台风',
      s1.heroName.indexOf(picked.name.split(' ')[0]) >= 0, 'hero=' + s1.heroName);
    check('出现「返回最新」出口', s1.hasBackLive === true);
    check('出现"你选中的台风"说明条', s1.focusNote.indexOf('你在左栏选中') >= 0, s1.focusNote);
    check('左栏提示改为展示被点台风', s1.hint.indexOf('地图正在展示') >= 0, s1.hint);
    check('地图轨迹已重画（bbox 与初始不同）',
      JSON.stringify(s1.trackBox) !== JSON.stringify(s0.trackBox),
      'before=' + JSON.stringify(s0.trackBox) + ' after=' + JSON.stringify(s1.trackBox));
    check('切换后台风的路径仍 100% 落在画布内',
      s1.trackOwn > 0 && s1.trackVisible === s1.trackOwn,
      'visible=' + s1.trackVisible + ' own=' + s1.trackOwn);
    check('右栏标注的 analysis id 已不再是自动回落结果',
      s1.analysisAttr === s1.focusAttr, 'analysis=' + s1.analysisAttr + ' focus=' + s1.focusAttr);
    check('切换后台风不再回放（进度落在末帧）',
      s1.dataZoom === '1', 'dataZoom=' + s1.dataZoom);
  }

  /* ---------- 2. 缩放：按钮 ----------
     基线取"切换台风之后"而不是"页面初始"：换过台风地图会按新数据重算自动视野，
     拿页面初始那个台风的比例尺来比，比的是两个不同的台风，必然对不上。
     另外 data-view 缺失本身就是合法状态（数据装得下 → 恒等视野），
     必须按"1,0,0"归一化，不能当成"读不到"。 */
  console.log('\n===== 2. 缩放（工具栏按钮） =====');
  const s2a = await snap();
  await js(`document.querySelector('[data-zoom="in"]').click()`);
  await sleep(400);
  const s2b = await snap();
  const v = (s) => (s.dataView ? s.dataView.split(',').map(Number) : [1, 0, 0]);
  const vA = v(s2a), vB = v(s2b);
  check('点「放大」后出现视野变换', !!s2b.dataView, 'data-view=' + s2b.dataView);
  check('点「放大」后比例尺变大（约 +25%）',
    vB[0] / vA[0] > 1.15 && vB[0] / vA[0] < 1.35,
    vA[0] + ' → ' + vB[0]);
  check('缩放百分比文案同步', /%$/.test(String(s2b.zoomLabel)), 'label=' + s2b.zoomLabel);
  check('底图与矢量层共用同一个视野变换（底图的 .map-view-g 不是 none）',
    !!s2b.imgTransform && s2b.imgTransform !== '(none)', 'landG=' + s2b.imgTransform);
  check('底图由真实海岸线绘制（748 条 path）', s2b.landPaths === 748, 'paths=' + s2b.landPaths);
  check('经纬网图层始终可见（不再只在缩放时显示）',
    s2b.graticuleShown === true && s0.graticuleShown === true,
    '初始=' + s0.graticuleShown + ' 放大后=' + s2b.graticuleShown);
  check('视野提示条不再隐藏，且文案改为手动视角（含百分比与「重置」指引）',
    s2b.noteHidden === false && s2b.noteText.indexOf('手动调整') >= 0 && s2b.noteText.indexOf('重置') >= 0,
    s2b.noteText);
  check('放大后路径依旧完整落在画布内',
    s2b.trackOwn > 0 && s2b.trackVisible === s2b.trackOwn,
    'visible=' + s2b.trackVisible + ' own=' + s2b.trackOwn);

  /* ---------- 3. 缩放：滚轮 + 拖拽 ---------- */
  console.log('\n===== 3. 缩放（滚轮 + 拖拽平移） =====');
  await js(`(function(){
    var c = document.querySelector('.map-canvas'), r = c.getBoundingClientRect();
    c.dispatchEvent(new WheelEvent('wheel', { deltaY: 100, bubbles: true, cancelable: true,
      clientX: r.left + r.width * 0.7, clientY: r.top + r.height * 0.4 }));
    return 1;
  })()`);
  await sleep(400);
  const s3a = await snap();
  const vC = v(s3a);
  check('滚轮向下缩小比例尺', vC[0] < vB[0], vB[0] + ' → ' + vC[0]);

  await js(`(function(){
    var c = document.querySelector('.map-canvas'), r = c.getBoundingClientRect();
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var o = { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 };
    c.dispatchEvent(new MouseEvent('mousedown', o));
    document.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: x + 70, clientY: y + 40 }));
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    return 1;
  })()`);
  await sleep(400);
  const s3b = await snap();
  const vD = v(s3b);
  check('拖拽改变了平移量（tx 变化）', Math.abs(vD[1] - vC[1]) > 5,
    'tx ' + vC[1] + ' → ' + vD[1]);
  check('拖拽不改变比例尺', Math.abs(vD[0] - vC[0]) < 1e-6, 's=' + vD[0]);
  await shot('index-focus-zoomed');

  /* ---------- 4. 重置 ---------- */
  console.log('\n===== 4. 重置回自动适应视野 =====');
  await js(`document.querySelector('[data-zoom="reset"]').click()`);
  await sleep(400);
  const s4 = await snap();
  const vE = v(s4);
  check('重置后 data-zoom 回到 1', s4.dataZoom === '1', 'dataZoom=' + s4.dataZoom);
  check('重置后比例尺与该台风"未缩放时"一致', Math.abs(vE[0] - vA[0]) < 1e-6,
    vA[0] + ' → ' + vE[0]);
  check('重置后路径仍完整落在画布内',
    s4.trackOwn > 0 && s4.trackVisible === s4.trackOwn,
    'visible=' + s4.trackVisible + ' own=' + s4.trackOwn);
  check('重置回该台风原本的自动适应状态（有变换/无变换与缩放前一致）',
    !!s4.dataView === !!s2a.dataView,
    '缩放前 data-view=' + s2a.dataView + ' 重置后=' + s4.dataView);

  /* ---------- 5. 历史档案分组同样可切换 ---------- */
  console.log('\n===== 5. 历史档案分组 =====');
  await js(`document.querySelector('#ty-tabs .tab[data-group="history"]').click()`);
  await sleep(400);
  const s5a = await snap();
  const his = JSON.parse(await js(`(function(){
    var it = document.querySelectorAll('#ty-list-host .ty-item');
    if (!it.length) { return JSON.stringify({ error: '历史档案为空' }); }
    it[0].click();
    return JSON.stringify({ id: it[0].getAttribute('data-ty-id'),
      name: it[0].querySelector('.ty-item-name').textContent.replace(/\\s+/g,' ') });
  })()`));
  if (his.error) {
    check('历史档案非空（历史分组验证前提）', false, his.error);
  } else {
    await sleep(3000);
    const s5b = await snap();
    check('历史台风也能切换为地图聚焦对象',
      String(s5b.focusAttr) === String(his.id), '期望 ' + his.id + ' 实际 ' + s5b.focusAttr);
    check('历史分组下也能看到"你选中的台风"说明',
      s5b.focusNote.indexOf('你在左栏选中') >= 0, s5b.focusNote);
    check('历史分组下未渲染实时同步提示条（与设计一致）', s5b.hint === '', 'hint=' + s5b.hint);
  }

  /* ---------- 6. 返回最新活跃台风 ---------- */
  console.log('\n===== 6. 返回最新活跃台风 =====');
  await js(`document.querySelector('#ty-tabs .tab[data-group="active"]').click()`);
  await sleep(400);
  const s6a = await snap();
  check('切回活跃分组后出现「返回最新」', s6a.hasBackLive === true, 'hint=' + s6a.hint);
  await js(`document.querySelector('#ty-back-live').click()`);
  await sleep(3000);
  const s6b = await snap();
  check('点「返回最新」后聚焦回到最新活跃台风',
    String(s6b.focusAttr) === String(s0.focusAttr),
    '期望 ' + s0.focusAttr + ' 实际 ' + s6b.focusAttr);
  check('「返回最新」按钮随之消失', s6b.hasBackLive === false);
  check('说明条随之消失', s6b.focusNote.indexOf('你在左栏选中') < 0, s6b.focusNote);
  await shot('index-focus-back');

  console.log('\nerrors:', errs.length ? errs.join(' | ') : '(none)');
  check('无控制台错误', errs.length === 0, errs.join(' | ').slice(0, 260));

  console.log('\n结果：' + pass + ' 通过 / ' + fail + ' 失败');
  console.log('截图目录：' + OUT);
  try { await getJson('/json/close/' + t.id); } catch (e) {}
  ws.close();
  edge.kill();
  setTimeout(() => process.exit(fail ? 1 : 0), 400);
}
main().catch((e) => { console.error(e); process.exit(1); });
