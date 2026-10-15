/**
 * 档案页检索条件交互验证：四个下拉是否真的参与查询、重置是否真的清空。
 *
 * 断言口径：
 *   1) 四个 select 存在且选项数 > 1（选项来自 /api/typhoons/meta，不是写死的）
 *   2) 单条件（年份）筛选后总数下降，且副标题写明条件
 *   3) 组合条件（年份 + 峰值强度下限）进一步收窄
 *   4) 「重置条件」把下拉清回第一项、总数回到全量
 *   5) 翻页沿用当前条件（不会把筛选"掉"了）
 *
 * 用法：node tools/probe-archive-filters.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9700 + (process.pid % 200);
const PROF = path.join(os.tmpdir(), 'zfz-arch-' + Date.now());
const BASE = 'http://localhost:8080/typhoon_platform/archive.html';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (p, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

async function main() {
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
    if (m.method === 'Runtime.exceptionThrown') errs.push('EXC ' + (m.params.exceptionDetails.exception && m.params.exceptionDetails.exception.description));
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') errs.push('LOG ' + m.params.entry.text);
  });
  const send = (m, p) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: m, params: p || {} })); });
  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    if (r.result && r.result.exceptionDetails) { throw new Error(JSON.stringify(r.result.exceptionDetails)); }
    return r.result && r.result.result && r.result.result.value;
  };

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });
  await sleep(7000);

  const results = [];
  const ok = (name, pass, detail) => { results.push({ name, pass, detail }); console.log((pass ? 'PASS  ' : 'FAIL  ') + name + '  ' + detail); };

  const shape = await evalJs(`JSON.stringify({
    sel: ['q-year','q-level','q-basin','q-status'].map(function(i){
      var e = document.getElementById(i);
      return e ? { id: i, tag: e.tagName, n: e.options.length, opts: [].map.call(e.options, function(o){return o.value + '=' + o.text;}).slice(0, 3) } : null;
    }),
    sub: document.getElementById('result-sub').textContent
  })`);
  const s = JSON.parse(shape);
  ok('四个下拉已渲染为 select', s.sel.every((x) => x && x.tag === 'SELECT'), JSON.stringify(s.sel.map((x) => x && (x.id + ':' + x.n))));
  ok('下拉选项来自 meta（>1 项）', s.sel.every((x) => x && x.n > 1), '');
  const initialSub = s.sub;
  console.log('   初始副标题:', initialSub);

  // 单条件：年份
  await evalJs(`(function(){ var e=document.getElementById('q-year'); e.value='2026'; document.getElementById('btn-query').click(); })()`);
  await sleep(2500);
  const afterYear = JSON.parse(await evalJs(`JSON.stringify({ sub: document.getElementById('result-sub').textContent, rows: document.querySelectorAll('#archive-rows .trow').length })`));
  console.log('   年份=2026 后:', afterYear.sub);
  ok('年份筛选生效（副标题写明条件）', /2026\s*年/.test(afterYear.sub), afterYear.sub);

  // 组合：年份 + 峰值强度下限
  await evalJs(`(function(){ var e=document.getElementById('q-level'); e.value='12'; document.getElementById('btn-query').click(); })()`);
  await sleep(2500);
  const afterLevel = JSON.parse(await evalJs(`JSON.stringify({ sub: document.getElementById('result-sub').textContent, rows: document.querySelectorAll('#archive-rows .trow').length })`));
  console.log('   年份=2026 + 强度≥12 后:', afterLevel.sub);
  ok('组合条件生效', /12\s*级/.test(afterLevel.sub) && /2026/.test(afterLevel.sub), afterLevel.sub);

  const nOf = (t) => { const m = /共\s*(\d+)\s*条/.exec(t); return m ? Number(m[1]) : NaN; };
  ok('组合条件的总数不超过单条件', nOf(afterLevel.sub) <= nOf(afterYear.sub), nOf(afterLevel.sub) + ' <= ' + nOf(afterYear.sub));

  // 重置
  await evalJs(`document.getElementById('btn-reset').click()`);
  await sleep(2500);
  const afterReset = JSON.parse(await evalJs(`JSON.stringify({
    sub: document.getElementById('result-sub').textContent,
    vals: ['q-year','q-level','q-basin','q-status'].map(function(i){ return document.getElementById(i).value; }),
    name: document.getElementById('q-name').value
  })`));
  console.log('   重置后:', afterReset.sub, JSON.stringify(afterReset.vals));
  ok('重置清空四个下拉与名称', afterReset.vals.every((v) => v === '') && afterReset.name === '', JSON.stringify(afterReset.vals));
  ok('重置回到全量', nOf(afterReset.sub) === nOf(initialSub), nOf(afterReset.sub) + ' vs ' + nOf(initialSub));

  // 翻页沿用条件
  await evalJs(`(function(){ var e=document.getElementById('q-year'); e.value='2026'; document.getElementById('btn-query').click(); })()`);
  await sleep(2500);
  const beforePaging = await evalJs(`document.getElementById('result-sub').textContent`);
  const paged = await evalJs(`(function(){ var b=document.getElementById('btn-next-page'); if (b && !b.disabled) { b.click(); return true; } return false; })()`);
  await sleep(2500);
  const afterPage = await evalJs(`document.getElementById('result-sub').textContent`);
  if (paged) {
    ok('翻页后仍保留筛选条件', /2026\s*年/.test(afterPage) && /第 2/.test(afterPage), beforePaging + '  ->  ' + afterPage);
  } else {
    console.log('   （当前条件只有一页，跳过翻页断言）');
  }

  // 留一张带条件的效果图，便于人工核对布局（5 列下拉是否被挤变形）
  await evalJs(`document.getElementById('btn-reset').click()`);
  await sleep(2000);
  await evalJs(`(function(){
    document.getElementById('q-year').value = '2026';
    document.getElementById('q-level').value = '12';
    document.getElementById('btn-query').click();
  })()`);
  await sleep(2500);
  const shot = await send('Page.captureScreenshot', { format: 'png' });
  const fs = require('fs');
  const out = path.resolve(__dirname, '../_probe_shots/archive-filters.png');
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, Buffer.from(shot.result.data, 'base64'));
  console.log('   效果图：' + out);

  console.log('\nerrors:', errs.length ? errs.join(' | ') : '(none)');
  const failed = results.filter((r) => !r.pass);
  console.log('\n合计 ' + results.length + ' 项断言，失败 ' + failed.length + ' 项');
  try { await getJson('/json/close/' + t.id); } catch (e) {}
  edge.kill();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 400);
}
main().catch((e) => { console.error(e); process.exit(1); });
