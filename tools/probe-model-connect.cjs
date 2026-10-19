/**
 * 探针：管理后台「大模型接入」端到端校验。
 *
 * 为什么需要它：这张表以前是"看着像配置页、实际点不出功能"的中间态 ——
 * 「接入」按钮只弹一句说明 toast，后端根本没有写接口，状态也只看
 * ai_prediction_records 有没有历史记录。所以真正要验的不是"弹层能不能打开"，
 * 而是"填了密钥之后，链路到底走没走通"。
 *
 * 覆盖：
 *   1) 四行模型齐全、顺序正确（验证增量迁移的预置数据没错位）
 *   2) 三态文案与操作列严格对应：待接入→「接入」，其余→「配置」
 *   3) 点「配置/接入」打开弹层：网关说明、占位符、接入点回填、断开按钮可见性、
 *      焦点落点（有 Key 落接入点，无 Key 落密钥框）
 *   4) 三类前端校验（缺接入点 / 网关协议非法 / 缺密钥）都不发请求、不关弹层
 *   5) 断开接入的「二次点击确认」：第一次只改文案，第二次才真断，随后状态回落待接入
 *   6) 填假密钥保存 → 凭据真的落库（状态变「已配置」），但同时如实回显厂商报错，
 *      弹层保持打开（这正是"已保存但不可用"该有的表现）
 *   7) 密钥留空再保存只改接入点 → 不报「请填写 API Key」（证明留空=保留原密钥生效）
 *   8) ESC / 点遮罩都能关，焦点归还到触发按钮
 *   9) 全程零控制台错误
 *
 * 副作用与可重复性：
 *   - 脚本最后会 disconnect 掉自己写入的假密钥，库回到「待接入」，可反复跑。
 *   - 但 `endpoint_id` 无法通过接口清回 NULL（connect 要求接入点非空），
 *     所以跑完 doubao-seed-1.6 这一行的接点会留成探针值。若需彻底还原：
 *     UPDATE llm_provider_config SET endpoint_id = NULL WHERE model_key='doubao-seed-1.6';
 *   - 第 6/7 步会**真实外网请求**火山方舟（假 Key 必 401），需本机可访问
 *     ark.cn-beijing.volces.com；不通时文案会变成网络类错误，断言仍成立
 *     （只断言"凭据已保存但验证未通过"这一层，不写死 401）。
 *
 * 用法：node tools/probe-model-connect.cjs
 * 前置：Tomcat 在 8080 跑着、admin01/password 可登录、已执行 migration_llm_config.sql。
 */
const { spawn } = require('child_process');
const http = require('http');
const os = require('os');
const path = require('path');

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const BASE = 'http://localhost:8080/typhoon_platform/';
const CREAT = 'admin01';
const CPWD = 'password';
const TARGET = 'doubao-seed-1.6';       // 被操练的模型
const EXPECT_ORDER = ['deepseek-v3', 'qwen-max', 'doubao-seed-1.6', 'ernie-4.0'];
const FAKE_KEY = 'sk-probe-invalid-key';
const FAKE_EP = 'ep-probe-0000';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 每个探针独占端口：共用端口时上一个 Edge 的 TIME_WAIT 会让新连接落到旧浏览器上，刷出假结果 */
async function pickPort() {
  for (let i = 0; i < 30; i++) {
    const port = 9200 + Math.floor(Math.random() * 900);
    const busy = await new Promise((res) => {
      const req = http.request({ host: '127.0.0.1', port, path: '/json/version', timeout: 400 }, () => res(true));
      req.on('error', () => res(false));
      req.on('timeout', () => { req.destroy(); res(false); });
      req.end();
    });
    if (!busy) { return port; }
  }
  throw new Error('找不到空闲调试端口');
}

/* 端口在 main() 里定：.cjs 是 CommonJS，顶层不能用 await */
let PORT = 0;
const PROF = path.join(os.tmpdir(), 'zfz-modeldlg-' + Date.now());

const getJson = (pathname, method) => new Promise((res, rej) => {
  const req = http.request({ host: '127.0.0.1', port: PORT, path: pathname, method: method || 'GET' },
    (r) => { let s = ''; r.on('data', (d) => s += d); r.on('end', () => { try { res(JSON.parse(s)); } catch (e) { res(s); } }); });
  req.on('error', rej);
  req.end();
});

let pass = 0;
const fails = [];
function check(name, ok, detail) {
  if (ok) { pass++; console.log('PASS  ' + name); }
  else { fails.push(name + (detail ? '  [' + detail + ']' : '')); console.log('FAIL  ' + name + (detail ? '  [' + detail + ']' : '')); }
}

async function main() {
  PORT = await pickPort();
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--hide-scrollbars', '--no-proxy-server',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROF, 'about:blank'
  ], { stdio: 'ignore' });

  let ver = null;
  for (let i = 0; i < 80 && !ver; i++) {
    try { ver = await getJson('/json/version'); } catch (e) { await sleep(250); }
  }
  if (!ver) { console.error('DevTools 未就绪'); edge.kill(); process.exit(1); }
  console.log('Edge:', ver.Browser, '| port', PORT);

  const target = await getJson('/json/new?' + encodeURIComponent('about:blank'), 'PUT');
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  const pending = new Map();
  let seq = 0;
  const errors = [];

  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails;
      errors.push('EXC ' + ((d.exception && (d.exception.description || d.exception.value)) || d.text));
    }
    if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      errors.push('CONSOLE ' + m.params.args.map((a) => a.value || a.description || '').join(' '));
    }
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      errors.push('LOG ' + m.params.entry.text + ' @ ' + (m.params.entry.url || ''));
    }
  });

  const send = (method, params) => new Promise((res) => {
    const id = ++seq;
    pending.set(id, res);
    ws.send(JSON.stringify({ id, method, params: params || {} }));
  });

  await new Promise((r) => ws.addEventListener('open', r));
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Log.enable');

  async function js(expression, awaitPromise) {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: !!awaitPromise });
    if (r.result && r.result.exceptionDetails) { return { __err: r.result.exceptionDetails.text }; }
    return r.result && r.result.result ? r.result.result.value : undefined;
  }

  async function goto(url) {
    await send('Page.navigate', { url });
    await sleep(400);
    for (let i = 0; i < 100; i++) {
      const ready = await js('document.readyState');
      if (ready === 'complete') { return; }
      await sleep(120);
    }
  }

  async function waitFor(expr, timeout) {
    const t0 = Date.now();
    while (Date.now() - t0 < (timeout || 10000)) {
      if (await js(expr)) { return true; }
      await sleep(150);
    }
    return false;
  }

  /** 读整张模型表的行（值全从 data-* 取，避免被列宽截图/文本截断影响） */
  const readRows = () => js(
    `JSON.stringify(Array.prototype.map.call(
       document.querySelectorAll('#page-body [data-model-key]'),
       function (b) {
         return {
           key: b.getAttribute('data-model-key'),
           name: b.getAttribute('data-model-name'),
           status: b.getAttribute('data-model-status'),
           provider: b.getAttribute('data-model-provider'),
           endpoint: b.getAttribute('data-model-endpoint') || '',
           base: b.getAttribute('data-model-base') || '',
           keyOk: b.getAttribute('data-model-key-ok'),
           action: (b.textContent || '').replace(/\\s+/g, '')
         };
       }))`).then((s) => JSON.parse(s));

  const rowOf = async (key) => (await readRows()).filter((r) => r.key === key)[0];

  const dlgState = () => js(
    `(function () {
       var d = document.getElementById('model-dlg');
       if (!d) { return JSON.stringify({ exists: false }); }
       var cs = getComputedStyle(d);
       var dis = document.getElementById('model-dlg-disconnect');
       var msg = document.getElementById('model-dlg-msg');
       return JSON.stringify({
         exists: true,
         visible: d.hidden === false && cs.display !== 'none',
         sub: document.getElementById('model-dlg-sub').textContent,
         where: document.getElementById('model-dlg-where').textContent,
         keyPh: document.getElementById('model-dlg-key').placeholder,
         keyVal: document.getElementById('model-dlg-key').value,
         endpoint: document.getElementById('model-dlg-endpoint').value,
         base: document.getElementById('model-dlg-base').value,
         disHidden: dis.hidden,
         disText: dis.textContent,
         disArmed: dis.getAttribute('data-armed'),
         saveText: document.getElementById('model-dlg-save').textContent,
         saveDisabled: document.getElementById('model-dlg-save').disabled,
         msg: msg.hidden ? '' : msg.textContent,
         msgCls: msg.className,
         focus: document.activeElement ? (document.activeElement.id || document.activeElement.tagName) : '',
         bodyLocked: document.body.classList.contains('is-dlg-open'),
         zIndex: cs.zIndex
       });
     })()`).then((s) => JSON.parse(s));

  const openTarget = () => js(
    `(function () {
       var b = document.querySelector('#page-body [data-model-key=' + JSON.stringify(${JSON.stringify(TARGET)}) + ']');
       if (!b) { return 'no-button'; }
       b.click();
       return 'ok';
     })()`);

  /* ---------------- 1. 登录并进入后台 ---------------- */
  await goto(BASE + 'index.html');
  const login = await js(
    `fetch('api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({ username: ${JSON.stringify(CREAT)}, password: ${JSON.stringify(CPWD)} }) })
       .then(function (r) { return r.json(); }).then(function (j) { return j.code; })`, true);
  check('管理员登录成功', login === 200, 'code=' + login);

  await goto(BASE + 'admin.html');
  const tableReady = await waitFor("document.querySelectorAll('#page-body [data-model-key]').length > 0", 20000);
  check('模型配置表已渲染', tableReady);
  if (!tableReady) { throw new Error('模型表没有渲染，后续断言无意义'); }

  /* ---------------- 2. 四行齐全 + 三态文案自洽 ---------------- */
  const rows = await readRows();
  check('四个模型都在（迁移预置未错位）', rows.length === 4, 'n=' + rows.length);
  check('顺序与预置一致', JSON.stringify(rows.map((r) => r.key)) === JSON.stringify(EXPECT_ORDER),
    rows.map((r) => r.key).join(','));

  const STATES = ['已接入', '已配置', '待接入'];
  const badState = rows.filter((r) => STATES.indexOf(r.status) < 0);
  check('状态只会是三态之一', badState.length === 0,
    badState.map((r) => r.key + '=' + r.status).join(','));

  const badAction = rows.filter((r) => r.action !== (r.status === '待接入' ? '接入' : '配置'));
  check('操作列文案与状态严格对应（待接入→接入，其余→配置）', badAction.length === 0,
    badAction.map((r) => r.key + ':' + r.status + '/' + r.action).join(', '));

  const badProvider = rows.filter((r) => !r.provider);
  check('每行都带 provider（弹层要靠它选文案）', badProvider.length === 0,
    badProvider.map((r) => r.key).join(','));

  const t0 = await rowOf(TARGET);
  check('目标行存在：' + TARGET, !!t0);
  if (!t0) { throw new Error('目标模型行缺失'); }
  const hadKey = t0.keyOk === '1';
  console.log('  · 基线：' + TARGET + ' status=' + t0.status + ' action=' + t0.action +
              ' keyConfigured=' + t0.keyOk + ' endpoint=' + (t0.endpoint || '(空)'));

  /* ---------------- 3. 打开弹层：回填与焦点 ---------------- */
  const opened = await openTarget();
  await sleep(400);
  const d1 = await dlgState();
  check('点「' + t0.action + '」打开弹层', opened === 'ok' && d1.visible, 'click=' + opened);
  check('弹层副标题带模型标签与当前状态',
    d1.sub.indexOf(t0.status) >= 0 && d1.sub.length > t0.status.length, d1.sub);
  check('弹层给出控制台入口说明', /控制台|console|volces|bce|bailian|platform|deepseek/i.test(d1.where),
    d1.where.slice(0, 60));
  check('接入点回填为库里的值', d1.endpoint === t0.endpoint, d1.endpoint + ' vs ' + t0.endpoint);
  check('弹层打开时锁定页面滚动', d1.bodyLocked === true);
  check('弹层层级低于 toast（180 < 200）', d1.zIndex === '180', 'z=' + d1.zIndex);

  if (hadKey) {
    check('已有密钥：占位符提示"留空不修改"', /留空/.test(d1.keyPh), d1.keyPh);
    check('已有密钥：密钥框不回显明文', d1.keyVal === '', d1.keyVal);
    check('已有密钥：断开按钮可见', d1.disHidden === false);
    check('已有密钥：焦点落在接入点', d1.focus === 'model-dlg-endpoint', d1.focus);
  } else {
    check('无密钥：占位符提示粘贴 Key', /粘贴|API Key/.test(d1.keyPh), d1.keyPh);
    check('无密钥：断开按钮隐藏', d1.disHidden === true);
    check('无密钥：焦点落在密钥框', d1.focus === 'model-dlg-key', d1.focus);
  }

  /* ---------------- 4. 前端校验（不关弹层、不发请求） ---------------- */
  await js("document.getElementById('model-dlg-endpoint').value = '';" +
           "document.getElementById('model-dlg-key').value = 'sk-x';" +
           "document.getElementById('model-dlg-save').click(); 1");
  await sleep(250);
  const d2 = await dlgState();
  check('缺接入点被拦下且文案明确', /接入点/.test(d2.msg) && d2.visible && !d2.saveDisabled, d2.msg);

  await js("document.getElementById('model-dlg-endpoint').value = 'ep-x';" +
           "document.getElementById('model-dlg-base').value = 'ftp://bad';" +
           "document.getElementById('model-dlg-save').click(); 1");
  await sleep(250);
  const d3 = await dlgState();
  check('网关协议非法被拦下', /http/i.test(d3.msg) && d3.visible, d3.msg);

  if (!hadKey) {
    await js("document.getElementById('model-dlg-key').value = '';" +
             "document.getElementById('model-dlg-base').value = '';" +
             "document.getElementById('model-dlg-save').click(); 1");
    await sleep(250);
    const d4 = await dlgState();
    check('无密钥时留空保存被拦下', /API Key/.test(d4.msg) && d4.visible, d4.msg);
  }

  /* ---------------- 5. 若有密钥：先测断开（二次确认） ---------------- */
  if (hadKey) {
    await js("document.getElementById('model-dlg-endpoint').value = " +
             JSON.stringify(t0.endpoint) + "; document.getElementById('model-dlg-base').value = " +
             JSON.stringify(t0.base) + "; 1");
    await js("document.getElementById('model-dlg-disconnect').click(); 1");
    await sleep(200);
    const a1 = await dlgState();
    check('断开：第一次点击只改文案不真断',
      a1.disArmed === '1' && /再次点击/.test(a1.disText) && a1.visible, a1.disText);

    await js("document.getElementById('model-dlg-disconnect').click(); 1");
    const closed = await waitFor("document.getElementById('model-dlg').hidden === true", 12000);
    check('断开：第二次点击真的断开并关弹层', closed);
    const toast1 = await js("(function(){var t=document.getElementById('zfz-toast');return t?t.textContent:'';})()");
    check('断开后给出提示（不因 mdlgModel 置空而丢 toast）', /已断开接入/.test(toast1 || ''), toast1);

    const back = await rowOf(TARGET);
    check('断开后状态回落「待接入」', back.status === '待接入', back.status);
    check('断开后操作列变回「接入」', back.action === '接入', back.action);
  }

  /* ---------------- 6. 无密钥：留空拦截 + 假 Key 保存 ---------------- */
  await openTarget();
  await sleep(350);
  const d5 = await dlgState();
  check('断开后重开：断开按钮隐藏', d5.disHidden === true);
  check('断开后重开：焦点落在密钥框', d5.focus === 'model-dlg-key', d5.focus);

  /* 填假密钥 + 假接入点保存。后端会先落库、再真的向厂商发一次请求；
     假 Key 必然被拒，所以正确的表现是：凭据已保存 + 验证未通过 + 弹层不关。 */
  await js("document.getElementById('model-dlg-key').value = " + JSON.stringify(FAKE_KEY) + ";" +
           "document.getElementById('model-dlg-endpoint').value = " + JSON.stringify(FAKE_EP) + ";" +
           "document.getElementById('model-dlg-save').click(); 1");
  const settled = await waitFor(
    "document.getElementById('model-dlg-save').disabled === false && " +
    "document.getElementById('model-dlg-msg').textContent.length > 0", 60000);
  check('保存后回到可交互状态（请求已结束）', settled);
  const d6 = await dlgState();
  check('验证失败时弹层保持打开（便于就地改）', d6.visible === true);
  check('提示写明"凭据已保存但验证未通过"', /凭据已保存/.test(d6.msg) && /未通过/.test(d6.msg), d6.msg);
  /* 失败态是 .zdlg-msg 的**默认红底**（不额外加 class），
     只有 is-ok / is-warn 才加类，所以这里断言"没有被打成成功/警告态"。 */
  check('失败提示未被误标成成功/警告态',
    !/is-ok|is-warn/.test(d6.msgCls || '') && /zdlg-msg/.test(d6.msgCls || ''), d6.msgCls);
  check('失败提示里不含密钥明文', (d6.msg || '').indexOf(FAKE_KEY) < 0, d6.msg);
  check('验证失败后断开按钮变得可用（真的存进去了）', d6.disHidden === false);
  check('保存按钮文案复原', /保存并接入/.test(d6.saveText), d6.saveText);

  /* ---------------- 7. 密钥留空再保存：不报缺 Key ---------------- */
  await js("document.getElementById('model-dlg-key').value = '';" +
           "document.getElementById('model-dlg-save').click(); 1");
  const settled2 = await waitFor(
    "document.getElementById('model-dlg-save').disabled === false && " +
    "document.getElementById('model-dlg-msg').textContent.length > 0", 60000);
  check('密钥留空再保存：请求真的发出去了（未被前端拦住）', settled2);
  const d7 = await dlgState();
  check('密钥留空＝保留原密钥（不报"请填写 API Key"）',
    !/请填写 API Key/.test(d7.msg) && /凭据已保存/.test(d7.msg), d7.msg);

  /* ---------------- 8. 关弹层后列表状态变「已配置」 ---------------- */
  await js("document.getElementById('model-dlg').dispatchEvent(new KeyboardEvent('keydown'," +
           "{ key: 'Escape', bubbles: true })); 1");
  await sleep(300);
  const d8 = await dlgState();
  check('ESC 关闭弹层', d8.visible === false);
  check('关闭后解除滚动锁', d8.bodyLocked === false);
  const focusBack = await js("document.activeElement ? (document.activeElement.getAttribute('data-model-key') || document.activeElement.tagName) : ''");
  check('焦点归还到触发按钮', focusBack === TARGET, String(focusBack));

  const afterSave = await rowOf(TARGET);
  check('保存后列表状态变「已配置」（有 Key 未验证通过）', afterSave.status === '已配置', afterSave.status);
  check('保存后操作列变「配置」', afterSave.action === '配置', afterSave.action);
  check('保存后接入点回填到行上', afterSave.endpoint === FAKE_EP, afterSave.endpoint);

  /* ---------------- 9. 点遮罩关闭 ---------------- */
  await openTarget();
  await sleep(300);
  await js("document.getElementById('model-dlg').click(); 1");
  await sleep(250);
  const d9 = await dlgState();
  check('点遮罩空白处关闭', d9.visible === false);

  /* ---------------- 10. 收尾：断开自己写入的假密钥 ---------------- */
  await openTarget();
  await sleep(300);
  await js("document.getElementById('model-dlg-disconnect').click(); 1");
  await sleep(200);
  await js("document.getElementById('model-dlg-disconnect').click(); 1");
  const cleaned = await waitFor("document.getElementById('model-dlg').hidden === true", 12000);
  check('收尾：假密钥已断开', cleaned);
  const finalRow = await rowOf(TARGET);
  check('收尾：状态回到「待接入」', finalRow.status === '待接入', finalRow.status);

  /* ---------------- 11. 控制台 ---------------- */
  check('控制台零错误', errors.length === 0, errors.slice(0, 3).join(' | '));

  console.log('\n===== ' + pass + ' 通过 / ' + fails.length + ' 失败 =====');
  if (fails.length) { console.log(fails.map((f) => ' - ' + f).join('\n')); }

  await send('Page.close').catch(() => {});
  ws.close();
  edge.kill();
  setTimeout(() => process.exit(fails.length ? 1 : 0), 600);
}

main().catch((e) => { console.error('探针异常:', e); process.exit(2); });
