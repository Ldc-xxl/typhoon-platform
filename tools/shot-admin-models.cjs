/**
 * 截图：管理后台「大模型接入配置」表格 + 接入弹层。
 *
 * 为什么要脚本：`msedge --headless --screenshot` 在本机静默失败（exit 0 不产图），
 * 且弹层需要"登录后再点一下"才能出现，命令行截图做不到交互。
 *
 * 用法：node tools/shot-admin-models.cjs
 * 前置：Tomcat 在 8080 跑着、admin01/password 可登录。
 * 输出：docs/screenshots/admin-models-*.png
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const BASE = 'http://localhost:8080/typhoon_platform/';
const OUT = path.join(__dirname, '..', 'docs', 'screenshots');
const PORT = 9411;
const PROF = path.join(os.tmpdir(), 'zfz-shot-' + Date.now());
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const getJson = (p, m) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: p, method: m || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej); req.end();
});

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server', '--window-size=1440,1000',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROF, 'about:blank'], { stdio: 'ignore' });

  let ver = null;
  for (let i = 0; i < 80 && !ver; i++) { try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); } }
  if (!ver) { console.error('DevTools 未就绪'); edge.kill(); process.exit(1); }

  const target = await getJson('/json/new?' + encodeURIComponent('about:blank'), 'PUT');
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  const pending = new Map(); let seq = 0;
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  });
  const send = (method, params) => new Promise((res) => { const id = ++seq; pending.set(id, res); ws.send(JSON.stringify({ id, method, params: params || {} })); });
  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable'); await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });

  const js = async (e, aw) => { const r = await send('Runtime.evaluate', { expression: e, returnByValue: true, awaitPromise: !!aw }); return r.result && r.result.result ? r.result.result.value : undefined; };
  const shot = async (name, clip) => {
    const r = await send('Page.captureScreenshot', clip ? { format: 'png', clip: clip } : { format: 'png' });
    if (!r.result || !r.result.data) {
      console.error('截图失败:', JSON.stringify(r.error || r).slice(0, 300), '| clip=', JSON.stringify(clip));
      return;
    }
    const file = path.join(OUT, name);
    fs.writeFileSync(file, Buffer.from(r.result.data, 'base64'));
    console.log('  ->', file);
  };
  const goto = async (u) => {
    await send('Page.navigate', { url: u });
    for (let i = 0; i < 100; i++) { if (await js('document.readyState') === 'complete') { return; } await sleep(120); }
  };

  await goto(BASE + 'index.html');
  await js(`fetch('api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin01',password:'password'})}).then(r=>r.json()).then(j=>j.code)`, true);

  await goto(BASE + 'admin.html');
  for (let i = 0; i < 100; i++) {
    if (await js("document.querySelectorAll('#page-body [data-model-key]').length >= 4")) { break; }
    await sleep(200);
  }
  await sleep(600);

  /* 只截「大模型接入配置」那张卡（第 2 个 .panel），比整页更有说服力 */
  const box = await js(`(function () {
      var ps = document.querySelectorAll('.col-main .panel');
      var el = ps[1] || ps[0];
      if (!el) { return ''; }
      var r = el.getBoundingClientRect();
      /* clip 的 scale 是**必填**字段（漏了会 -32602），且宽高必须是正数 */
      return JSON.stringify({ x: Math.round(Math.max(0, r.left - 8)),
                              y: Math.round(Math.max(0, r.top + window.scrollY - 8)),
                              width: Math.round(r.width + 16), height: Math.round(r.height + 16),
                              scale: 1 });
    })()`);
  await shot('admin-models-table.png', box ? JSON.parse(box) : null);

  await js(`document.querySelector('#page-body [data-model-key="doubao-seed-1.6"]').click(); 1`);
  await sleep(700);
  await shot('admin-models-dialog.png');

  ws.close(); edge.kill();
  setTimeout(() => process.exit(0), 400);
}
main().catch((e) => { console.error(e); process.exit(2); });
