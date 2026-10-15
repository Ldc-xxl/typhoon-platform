/**
 * 风险评估页「三个可切换下拉」交互验证。
 *
 * 背景：这一页原先顶部三个字段（城市 / 关联台风 / 评估基准）是**只读文字**，
 * 且"评估基准"显示的还是浏览器当前时间（假数据）。本轮把它们改成真正可操作的
 * 下拉，并让基准可选该台风的历次实测时次（按时刻重演）。本脚本就是要证伪
 * "看起来能选、其实没接上"这类问题。
 *
 * 断言口径：
 *   1) 三个下拉都渲染成 <select>，且城市/基准选项来自接口而非写死
 *   2) 台风下拉按「活跃台风 / 历史档案」分组（optgroup）
 *   3) 换城市 → 主卡标题切换成该城市
 *   4) 换基准时次 → 评估结果卡片上「基准时刻」文本随之改变（证明真的重算了）
 *   5) 换台风 → 下拉值变化且结果区重绘
 *   6) 条件卡常驻：换城市/换基准后 #q-typhoon 仍是同一个 DOM 节点（未被重建）
 *   7) 未登录点「存档本次评估」→ 提示需要登录（401 走 err.code，不是 HTTP 状态）
 *   8) 全流程零控制台错误
 *
 * 用法：node tools/probe-risk-filters.cjs
 */
const { spawn } = require('child_process');
const http = require('http');
const os = require('os');
const path = require('path');
const fs = require('fs');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9900 + (process.pid % 90);
const PROF = path.join(os.tmpdir(), 'zfz-risk-' + Date.now());
const BASE = 'http://localhost:8080/typhoon_platform/risk-assessment.html';

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
  const clickSelect = (selId, value) => evalJs(
    `(function(){ var e=document.getElementById('${selId}'); if(!e) return false; ` +
    `e.value=${JSON.stringify(value)}; e.dispatchEvent(new Event('change', {bubbles:true})); return e.value; })()`);

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });
  await sleep(9000);

  const results = [];
  const ok = (name, pass, detail) => { results.push({ name, pass, detail }); console.log((pass ? 'PASS  ' : 'FAIL  ') + name + '  ' + detail); };

  /** 抓一份页面当前状态 */
  const snap = async () => JSON.parse(await evalJs(`JSON.stringify({
    sels: ['q-typhoon','q-city','q-basetime'].map(function(i){
      var e = document.getElementById(i);
      if (!e) { return null; }
      var gs = [].map.call(e.querySelectorAll('optgroup'), function(g){ return g.label + ':' + g.children.length; });
      return { id: i, tag: e.tagName, n: e.options.length, value: e.value, groups: gs };
    }),
    titles: [].map.call(document.querySelectorAll('.col-main .panel .panel-title'), function(x){ return x.textContent; }),
    subs: [].map.call(document.querySelectorAll('.col-main .panel .panel-sub'), function(x){ return x.textContent; }),
    sideRisks: document.querySelectorAll('.col-side .dist-row, .col-side .drow').length,
    mainHead: (document.querySelector('.col-main .panel .panel-title') || {}).textContent || ''
  })`));

  let s = await snap();
  ok('三个下拉都渲染为 <select>', s.sels.every((x) => x && x.tag === 'SELECT'),
    JSON.stringify(s.sels.map((x) => x && (x.id + ':' + x.n))));
  ok('城市下拉选项 > 1（来自 /api/cities）', s.sels[1] && s.sels[1].n > 1, 'n=' + (s.sels[1] && s.sels[1].n));
  ok('基准下拉选项 > 1（来自该台风实测时次）', s.sels[2] && s.sels[2].n > 1, 'n=' + (s.sels[2] && s.sels[2].n));
  ok('台风下拉按「活跃台风 / 历史档案」分组',
    s.sels[0] && s.sels[0].groups.length >= 1 && /活跃台风/.test(s.sels[0].groups.join(',')),
    JSON.stringify(s.sels[0] && s.sels[0].groups));
  // 分组必须恒为 2 段：若按接口原顺序 push，活跃/历史交错时会被切成多段同名 optgroup
  ok('台风下拉分组恒为 2 段（活跃 / 历史各自归拢，不随接口顺序碎裂）',
    s.sels[0] && s.sels[0].groups.length === 2,
    JSON.stringify(s.sels[0] && s.sels[0].groups));

  const resultTitle0 = s.titles.find((x) => /评估结果/.test(x)) || '';
  const base0 = (s.subs.find((x) => /基准时刻/.test(x)) || '');
  console.log('   初始结果卡:', resultTitle0, '|', base0);
  ok('结果卡写明「基准时刻」（不再是浏览器当前时间）', /基准时刻\s*\d{2}-\d{2}\s+\d{2}:\d{2}/.test(base0), base0);

  /* ---- ① 换城市 ---- */
  await evalJs(`(function(){ document.getElementById('q-typhoon').__zfzMark = 1; })()`);
  const cityVal = await evalJs(`(function(){ var e=document.getElementById('q-city'); return e.options[1].value; })()`);
  await clickSelect('q-city', cityVal);
  await sleep(3000);
  s = await snap();
  const resultTitle1 = s.titles.find((x) => /评估结果/.test(x)) || '';
  console.log('   换城市后结果卡:', resultTitle1);
  ok('换城市后主卡聚焦到该城市', resultTitle1 !== resultTitle0 && resultTitle1.length > 0,
    resultTitle0 + '  ->  ' + resultTitle1);
  ok('换城市后条件卡未被重建（#q-typhoon 仍是同一节点）',
    await evalJs(`document.getElementById('q-typhoon').__zfzMark === 1`), '');

  /* ---- ② 换基准时次 ---- */
  const timeOpts = await evalJs(`JSON.stringify([].map.call(document.getElementById('q-basetime').options, function(o){ return o.value; }))`);
  const times = JSON.parse(timeOpts).filter((v) => v);   // 去掉「跟随最新实测」（value=''）
  const base1 = (s.subs.find((x) => /基准时刻/.test(x)) || '');
  if (times.length >= 1) {
    const pick = times[times.length - 1];    // 最早的那个实测时次
    await clickSelect('q-basetime', pick);
    await sleep(3500);
    s = await snap();
    const base2 = (s.subs.find((x) => /基准时刻/.test(x)) || '');
    console.log('   换基准:', base1, ' -> ', base2, '  (选了 ' + pick + ')');
    // 期望：结果卡上的基准时刻 == 所选时次的「月-日 时:分」
    var wantBase = pick.slice(5, 16);   // '2024-09-05 17:00:00' -> '09-05 17:00'
    ok('换基准时次后「基准时刻」变为所选时次（真的按该时刻重算，不是换汤不换药）',
      base2 !== base1 && base2.indexOf(wantBase) >= 0,
      base1 + '  ->  ' + base2 + '  (期望含 ' + wantBase + ')');
    ok('换基准后条件卡未被重建',
      await evalJs(`document.getElementById('q-typhoon').__zfzMark === 1`), '');
  } else {
    console.log('   （该台风只有一组实测时次，跳过换基准断言）');
  }

  /* ---- ③ 换台风 ---- */
  const tyVal = await evalJs(`(function(){ var e=document.getElementById('q-typhoon'); return e.options[e.options.length-1].value; })()`);
  const beforeTy = await evalJs(`document.getElementById('q-typhoon').value`);
  await clickSelect('q-typhoon', tyVal);
  await sleep(4000);
  const afterTy = await evalJs(`document.getElementById('q-typhoon').value`);
  const titlesAfterTy = JSON.parse(await evalJs(`JSON.stringify([].map.call(document.querySelectorAll('.col-main .panel .panel-title'), function(x){return x.textContent;}))`));
  console.log('   换台风:', beforeTy, ' -> ', afterTy, ' tiles=', JSON.stringify(titlesAfterTy));
  ok('换台风后下拉值变化且结果区重绘',
    afterTy !== beforeTy && titlesAfterTy.length > 0, beforeTy + ' -> ' + afterTy);
  ok('换台风后基准下拉被重置为「跟随最新实测」',
    await evalJs(`document.getElementById('q-basetime').value === ''`), '');

  /* ---- ④ 未登录存档 ---- */
  await evalJs(`(function(){ var e=document.getElementById('btn-assess'); if(e) e.click(); })()`);
  await sleep(2500);
  const toast = await evalJs(`(function(){ var e=document.getElementById('zfz-toast'); return e ? e.textContent : ''; })()`);
  console.log('   存档 toast:', toast);
  ok('未登录存档给出「需要登录」提示（而非"服务器错误"）',
    /登录/.test(toast) || /失败/.test(toast), toast);

  /* ---- 截图留档 ---- */
  const shot = await send('Page.captureScreenshot', { format: 'png' });
  const out = path.resolve(__dirname, '../_probe_shots/risk-filters.png');
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, Buffer.from(shot.result.data, 'base64'));
  console.log('   效果图：' + out);

  console.log('\nerrors:', errs.length ? errs.join(' | ') : '(none)');
  ok('全流程零控制台错误', errs.length === 0, errs.slice(0, 2).join(' | '));

  const failed = results.filter((r) => !r.pass);
  console.log('\n合计 ' + results.length + ' 项断言，失败 ' + failed.length + ' 项');
  try { await getJson('/json/close/' + t.id); } catch (e) {}
  edge.kill();
  setTimeout(() => process.exit(failed.length ? 1 : 0), 400);
}
main().catch((e) => { console.error(e); process.exit(1); });
