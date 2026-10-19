/* ============================================================
 * capture.cjs —— 无头 Edge 批量截图 + 交互 + 控制台错误巡检
 *
 * 用法：改下面的 CONFIG，然后
 *   <managed-node>/node.exe capture.cjs          （必须在沙箱外执行）
 *
 * 为什么不用 msedge --screenshot：它在不少环境下静默失败
 * （exit 0 却不生成 PNG）。CDP 走 WebSocket，稳定且能交互。
 * ============================================================ */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const CONFIG = {
  edge: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  port: 9333,
  base: 'http://127.0.0.1:8777/',
  outDir: '.',
  viewport: { width: 1440, height: 1000 },

  // 每项：file 输出文件名；url 相对 base；height 视口高；
  // steps 可选，为「先执行 JS 再截图」的步骤序列
  jobs: [
    { url: 'index.html', file: 'shot-index.png', height: 1000 },
    {
      url: 'index.html', file: 'shot-index-clicked.png', height: 1000,
      steps: [
        /* 底图切换按钮已随三合一删除，这里只保留回放交互 */
        { js: `document.getElementById('timeline-play').click()`, wait: 1800 }
      ]
    }
  ]
};

// 每个进程独立 profile —— 复用会导致「只出首张图」或 EBUSY
const PROF = path.join(require('node:os').tmpdir(), 'cdp-cap-' + process.pid);
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  fs.mkdirSync(CONFIG.outDir, { recursive: true });
  const edge = spawn(CONFIG.edge, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--remote-debugging-port=' + CONFIG.port,
    '--user-data-dir=' + PROF, 'about:blank'
  ], { stdio: 'ignore' });

  let ready = false;
  for (let i = 0; i < 40; i++) {
    try { const r = await fetch(`http://127.0.0.1:${CONFIG.port}/json/version`); if (r.ok) { ready = true; break; } }
    catch (e) { /* retry */ }
    await wait(500);
  }
  if (!ready) { console.error('DevTools 未就绪'); process.exit(1); }

  let totalErr = 0;
  for (const job of CONFIG.jobs) {
    let t;
    try {
      t = await (await fetch(`http://127.0.0.1:${CONFIG.port}/json/new?about:blank`, { method: 'PUT' })).json();
    } catch (e) { console.error('新建 target 失败', job.url, e.message); continue; }

    const ws = new WebSocket(t.webSocketDebuggerUrl);
    const pending = new Map();
    const errs = [];
    let id = 0;
    ws.addEventListener('message', (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
      if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
        // text 里没有文件名；要查具体是谁 404 去看静态服务器日志
        if ((m.params.entry.text || '').indexOf('favicon') < 0) errs.push('LOG  ' + m.params.entry.text);
      }
      if (m.method === 'Runtime.exceptionThrown') {
        const d = m.params.exceptionDetails;
        const msg = (d.exception && d.exception.description) || d.text || '';
        errs.push('EXC  ' + String(msg).slice(0, 220));
      }
    });
    const send = (method, params) => new Promise((res) => {
      const i = ++id; pending.set(i, res);
      ws.send(JSON.stringify({ id: i, method, params: params || {} }));
    });
    await new Promise((r) => ws.addEventListener('open', r));
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Log.enable');
    await send('Emulation.setDeviceMetricsOverride', {
      width: CONFIG.viewport.width, height: job.height || CONFIG.viewport.height,
      deviceScaleFactor: 1, mobile: false
    });
    await send('Page.navigate', { url: CONFIG.base + job.url });
    await wait(1600);

    const js = async (expr) => {
      const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true });
      if (r.result && r.result.exceptionDetails) return '<<ERR>> ' + JSON.stringify(r.result.exceptionDetails).slice(0, 200);
      return r.result && r.result.result ? r.result.result.value : null;
    };

    // 交互步骤（可选）
    for (const s of (job.steps || [])) {
      if (s.js) { const out = await js(s.js); if (s.log) console.log('   ', s.js, '=>', out); }
      await wait(s.wait || 500);
    }

    const shot = await send('Page.captureScreenshot', { format: 'png' });
    const file = path.join(CONFIG.outDir, job.file);
    if (shot && shot.result && shot.result.data) {
      fs.writeFileSync(file, Buffer.from(shot.result.data, 'base64'));
      console.log((errs.length ? 'WARN ' : 'ok   ') + job.file + '  ' + fs.statSync(file).size + 'B');
    } else {
      console.error('fail ' + job.file);
    }
    if (errs.length) { totalErr += errs.length; errs.forEach((e) => console.log('       ' + e)); }

    ws.close();
    try { await fetch(`http://127.0.0.1:${CONFIG.port}/json/close/${t.id}`); } catch (e) { /* ignore */ }
  }

  console.log('\n合计错误：' + totalErr);
  try { edge.kill(); } catch (e) { /* ignore */ }
  process.exit(0);
}

main();
