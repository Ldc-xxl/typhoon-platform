/**
 * 探针：并行打开若干页面，收集
 *   1) 控制台错误 / 未捕获异常 / 资源加载失败
 *   2) 页面上下文中 ZFZ.api 的实际形状（有哪些 key、analyzeTarget 是否存在）
 *   3) 页面错误态文案（#page-body 是否出现"加载失败"）
 *
 * 用法：node tools/probe-runtime-errors.cjs [页面路径 ...]
 * 默认探查 5 个派生分析页。
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9333 + (process.pid % 500);
const PROF = path.join(os.tmpdir(), 'zfz-probe-' + Date.now());
const BASE = 'http://localhost:8080/typhoon_platform/';

const pages = process.argv.slice(2);
if (!pages.length) {
  pages.push('index.html', 'sandbox.html', 'archive.html',
             'forecast-disagreement.html', 'risk-assessment.html', 'similar-cases.html');
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const getJson = (pathname, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: pathname, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

async function main() {
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROF, 'about:blank'
  ], { stdio: 'ignore' });

  let ver = null;
  for (let i = 0; i < 60 && !ver; i++) {
    try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); }
  }
  if (!ver) { console.error('DevTools 未就绪'); edge.kill(); process.exit(1); }
  console.log('Edge:', ver.Browser);

  for (const p of pages) {
    const t = await getJson('/json/new?' + encodeURIComponent('about:blank'), 'PUT');
    const ws = new WebSocket(t.webSocketDebuggerUrl);
    const pending = new Map(); let id = 0;
    const events = [];
    ws.addEventListener('message', (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
      if (m.method === 'Runtime.exceptionThrown') {
        const d = m.params.exceptionDetails;
        events.push('EXC  ' + (d.exception && (d.exception.description || d.exception.value) || d.text));
      }
      if (m.method === 'Runtime.consoleAPICalled' && /error|warning/.test(m.params.type)) {
        events.push('CONSOLE[' + m.params.type + '] ' + m.params.args.map((a) => a.value || a.description || '').join(' '));
      }
      if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
        events.push('LOG  ' + m.params.entry.text + ' @ ' + (m.params.entry.url || ''));
      }
    });
    const send = (method, params) => new Promise((res) => {
      const i = ++id; pending.set(i, res);
      ws.send(JSON.stringify({ id: i, method, params: params || {} }));
    });
    await new Promise((r) => ws.addEventListener('open', r));
    await send('Page.enable'); await send('Runtime.enable');
    await send('Log.enable'); await send('Network.enable');
    await send('Page.navigate', { url: BASE + p });
    await sleep(6000);

    const r = await send('Runtime.evaluate', {
      expression: `JSON.stringify({
        hasZFZ: typeof window.ZFZ,
        apiKeys: (window.ZFZ && window.ZFZ.api) ? Object.keys(window.ZFZ.api).filter(k=>/arget|nalyze/.test(k)) : null,
        analyzeTargetType: (window.ZFZ && window.ZFZ.api) ? typeof window.ZFZ.api.analyzeTarget : 'no-api',
        apiKeyCount: (window.ZFZ && window.ZFZ.api) ? Object.keys(window.ZFZ.api).length : 0,
        bodyText: (document.getElementById('page-body')||{}).innerText ? document.getElementById('page-body').innerText.replace(/\\s+/g,' ').slice(0,160) : ''
      })`, returnByValue: true
    });
    console.log('\n===== ' + p + ' =====');
    console.log(r.result && r.result.result && r.result.result.value);
    console.log(events.length ? events.join('\n') : '(无错误事件)');
    await send('Page.close').catch(() => {});
    try { await getJson('/json/close/' + t.id); } catch (e) {}
    ws.close();
  }
  edge.kill();
  setTimeout(() => process.exit(0), 500);
}

main().catch((e) => { console.error(e); process.exit(1); });
