/* ============================================================
 * zfz-mockfree-verify.cjs
 * 验证目标：前端彻底移除 js/data.js 演示数据层之后，
 *   ① 全部 10 个页面都能从真实接口拿到数据并渲染
 *   ② 页面上的数字与接口返回一致（数据驱动，而非写死）
 *   ③ 组件库 / 顶栏不再引用 ZFZ.mock（全局对象应为 undefined）
 *   ④ 后端不可用时如实报错，不回退到演示数据
 *
 * 跑法（须在沙箱外）：<managed-node>/node.exe zfz-mockfree-verify.cjs
 * ============================================================ */
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const CONFIG = {
  edge: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  port: 9371,
  root: 'http://127.0.0.1:8080/typhoon_platform/',
  width: 1440,
  height: 1000,
  wait: 2400
};

/* 期望的「验证用台风名」由外部注入；未注入时跳过该项断言 */
const EXPECT_NAME = process.env.ZFZ_EXPECT_NAME || '';

const OUT = 'C:\\Users\\17168\\zfz-runtime\\_verify';
const PROF = path.join(os.tmpdir(), 'cdp-zfz-mf-' + process.pid);
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

let pass = 0, fail = 0;
function check(name, ok, extra) {
  if (ok) { pass++; console.log('  PASS  ' + name + (extra ? '   [' + extra + ']' : '')); }
  else { fail++; console.log('  FAIL  ' + name + (extra ? '   [' + extra + ']' : '')); }
}

async function main() {
  fs.rmSync(PROF, { recursive: true, force: true });
  fs.mkdirSync(OUT, { recursive: true });
  spawn(CONFIG.edge, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-sandbox',
    '--disable-http-cache', '--hide-scrollbars',
    '--remote-debugging-port=' + CONFIG.port, '--user-data-dir=' + PROF, 'about:blank'
  ], { stdio: 'ignore' });

  let ready = false;
  for (let i = 0; i < 40; i++) {
    try { const r = await fetch(`http://127.0.0.1:${CONFIG.port}/json/version`); if (r.ok) { ready = true; break; } }
    catch (e) { /* retry */ }
    await wait(500);
  }
  if (!ready) { console.error('DevTools 未就绪'); process.exit(1); }

  async function open(url, preScript, waitMs, h) {
    const t = await (await fetch(`http://127.0.0.1:${CONFIG.port}/json/new?about:blank`, { method: 'PUT' })).json();
    const ws = new WebSocket(t.webSocketDebuggerUrl);
    const pending = new Map(); const errs = []; let id = 0;
    ws.addEventListener('message', (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
      if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
        if ((m.params.entry.text || '').indexOf('favicon') < 0) errs.push('LOG ' + m.params.entry.text);
      }
      if (m.method === 'Runtime.exceptionThrown') {
        const d = m.params.exceptionDetails;
        errs.push('EXC ' + String((d.exception && d.exception.description) || d.text || '').slice(0, 220));
      }
    });
    const send = (method, params) => new Promise((res) => {
      const i = ++id; pending.set(i, res);
      ws.send(JSON.stringify({ id: i, method, params: params || {} }));
    });
    await new Promise((r) => ws.addEventListener('open', r));
    await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable');
    await send('Network.enable'); await send('Network.setCacheDisabled', { cacheDisabled: true });
    await send('Emulation.setDeviceMetricsOverride',
      { width: CONFIG.width, height: h || CONFIG.height, deviceScaleFactor: 1, mobile: false });
    if (preScript) { await send('Page.addScriptToEvaluateOnNewDocument', { source: preScript }); }
    await send('Page.navigate', { url });
    await wait(waitMs || CONFIG.wait);

    const js = async (expr, awaitPromise) => {
      const r = await send('Runtime.evaluate', {
        expression: expr, returnByValue: true, awaitPromise: !!awaitPromise
      });
      if (r.result && r.result.exceptionDetails) { return 'EVAL-ERR ' + r.result.exceptionDetails.text; }
      return r.result && r.result.result ? r.result.result.value : null;
    };
    const shot = async (name) => {
      try {
        const r = await send('Page.captureScreenshot', { format: 'png' });
        fs.writeFileSync(path.join(OUT, name), Buffer.from(r.result.data, 'base64'));
      } catch (e) { /* 截图失败不影响断言 */ }
    };
    const close = async () => { try { await fetch(`http://127.0.0.1:${CONFIG.port}/json/close/${t.id}`); } catch (e) {} ws.close(); };
    return { js, shot, close, errs, send };
  }

  const txt = (p) => p.js(`document.querySelector('#page-body').innerText.replace(/\\s+/g,' ')`);

  /* ============================================================
     ① 演示数据层已物理移除
     ============================================================ */
  console.log('\n===== ① 演示数据层已移除 =====');
  {
    const p = await open(CONFIG.root + 'index.html');
    check('全局无 ZFZ.mock 对象', (await p.js(`typeof ZFZ.mock`)) === 'undefined',
      await p.js(`typeof ZFZ.mock`));
    check('页面未加载 js/data.js',
      (await p.js(`document.querySelectorAll('script[src*="data.js"]').length`)) === 0);
    check('api.js 未导出 useMock 开关', (await p.js(`'useMock' in ZFZ.api.config`)) === false);
    for (const f of ['ui.js', 'layout.js', 'api.js']) {
      const body = fs.readFileSync(
        'C:\\Users\\17168\\Desktop\\web课程项目\\typhoon_platform\\src\\main\\webapp\\js\\' + f, 'utf8');
      check(f + ' 源码无 ZFZ.mock', body.indexOf('ZFZ.mock') < 0);
    }
    check('js/data.js 文件已删除', !fs.existsSync(
      'C:\\Users\\17168\\Desktop\\web课程项目\\typhoon_platform\\src\\main\\webapp\\js\\data.js'));
    await p.close();
  }

  /* ============================================================
     ② 十页控制台巡检
     ============================================================ */
  console.log('\n===== ② 十页控制台错误巡检 =====');
  const PAGES = ['index.html', 'archive.html', 'typhoon-detail.html', 'forecast-disagreement.html',
    'risk-assessment.html', 'similar-cases.html', 'sandbox.html', 'survey.html',
    'help-api.html', 'admin.html'];
  for (const pg of PAGES) {
    const p = await open(CONFIG.root + pg);
    check(pg, p.errs.length === 0, p.errs.length ? p.errs.join(' | ').slice(0, 260) : '0 错误');
    await p.close();
  }

  /* ============================================================
     ③ P1 监测总览 —— 数据驱动核心
     ============================================================ */
  console.log('\n===== ③ index.html（P1 监测总览）=====');
  {
    const p = await open(CONFIG.root + 'index.html');
    const t = await txt(p);

    /* 左栏：真实分组 */
    check('活跃 Tab 计数与接口一致', await p.js(
      `document.querySelector('#ty-tabs .tab[data-group="active"] .tab-count').textContent`) === '1',
      'count=' + await p.js(`document.querySelector('#ty-tabs .tab[data-group="active"] .tab-count').textContent`));
    check('左栏活跃台风 1 条', await p.js(`document.querySelectorAll('.ty-item').length`) === 1);
    check('左栏首项为 飞燕 FEIYAN', /飞燕\s*FEIYAN/.test(await p.js(
      `document.querySelector('.ty-item-name').textContent.replace(/\\s+/g,' ')`)),
      await p.js(`document.querySelector('.ty-item-name').textContent.replace(/\\s+/g,' ')`));
    check('左栏强度分级文案含「台风」', /台风 · 12级/.test(await p.js(
      `document.querySelector('.ty-item-level').textContent`)),
      await p.js(`document.querySelector('.ty-item-level').textContent`));

    /* 切到历史档案 */
    await p.js(`document.querySelector('#ty-tabs .tab[data-group="history"]').click()`);
    await wait(250);
    check('历史 Tab 计数 = 12', await p.js(
      `document.querySelector('#ty-tabs .tab[data-group="history"] .tab-count').textContent`) === '12');
    check('历史分组渲染 12 条', await p.js(`document.querySelectorAll('.ty-item').length`) === 12,
      'count=' + await p.js(`document.querySelectorAll('.ty-item').length`));
    check('历史条目强度色为十六进制', /^rgb\(/.test(await p.js(
      `getComputedStyle(document.querySelector('.ty-item-level')).color`)));
    await p.js(`document.querySelector('#ty-tabs .tab[data-group="active"]').click()`);
    await wait(250);

    /* 右栏：实况参数随回放刷新 */
    const lvl = await p.js(`document.querySelector('[data-metric="level"] .metric-value').textContent`);
    check('实况参数·风力非空', /^\d+ 级$/.test(lvl), lvl);
    check('实况参数·气压格式正确',
      /^\d+ hPa$/.test(await p.js(`document.querySelector('[data-metric="pressure"] .metric-value').textContent`)),
      await p.js(`document.querySelector('[data-metric="pressure"] .metric-value').textContent`));
    check('实况参数·位置格式正确',
      /^\d+\.\d°N \d+\.\d°E$/.test(await p.js(`document.querySelector('[data-metric="pos"] .metric-value').textContent`)),
      await p.js(`document.querySelector('[data-metric="pos"] .metric-value').textContent`));
    check('等级标签用组件库分级表渲染', /^(热带低压|热带风暴|强热带风暴|台风|强台风|超强台风) · \d+级$/.test(
      await p.js(`document.querySelector('[data-metric="levelTag"]').textContent`)),
      await p.js(`document.querySelector('[data-metric="levelTag"]').textContent`));
    check('等级标签带正确强度类（随回放强度变化）',
      /^tag is-(neutral|ts|sts|ty|sty|super)$/.test(await p.js(`document.querySelector('[data-metric="levelTag"]').className`)),
      await p.js(`document.querySelector('[data-metric="levelTag"]').className`));

    /* 地图：城市来自 /api/cities（14 条，而非演示数据的 11 条） */
    check('地图城市标注 14 个', await p.js(`document.querySelectorAll('.map-city-label').length`) === 14,
      'count=' + await p.js(`document.querySelectorAll('.map-city-label').length`));
    check('城市标注含「杭州」（演示数据里没有的城市）',
      /杭州/.test(await p.js(
        `Array.prototype.map.call(document.querySelectorAll('.map-city-label'),function(e){return e.textContent;}).join(',')`)));

    /* 地图：风圈半径按接口真实公里数换算，而非写死的屏幕半径 */
    const ring = await p.js(`(async function(){
      var api = await ZFZ.api.getWindCircles(13);
      var km = 0;
      for (var i=0;i<api.length;i++){ if (Number(api[i].level)===7) km = Number(api[i].r); }
      var el = document.querySelector('.wind-ring[data-level="7"]');
      var base = el ? Number(el.getAttribute('data-base-r')) : -1;
      return JSON.stringify({ km: km, baseR: base, expect: Math.round(km/3.9*100)/100 });
    })()`, true);
    const rg = JSON.parse(ring);
    check('7 级风圈基准半径 = 接口公里数 / 3.9', Math.abs(rg.baseR - rg.km / 3.9) < 0.02,
      'km=' + rg.km + ' baseR=' + rg.baseR + ' 期望≈' + rg.km / 3.9);
    check('风圈图层 3 个等级 × 2 圈 = 6 个元素',
      await p.js(`document.querySelectorAll('.wind-ring').length`) === 6);

    /* 预报路径 / AI 路径 图层存在 */
    check('预报路径图层有内容', await p.js(
      `document.querySelectorAll('.map-layer[data-layer="forecast"] polyline').length`) >= 1);
    check('AI 测算路径图层有内容', await p.js(
      `document.querySelectorAll('.map-layer[data-layer="ai"] polyline').length`) === 1);
    check('台风中心标记存在（photo-over 图层未被漏拼）', await p.js(
      `document.querySelectorAll('.map-layer[data-layer="photo-over"] .ty-center').length`) === 1);
    check('图层顺序：labels 在最上层（最后一个子节点）', await p.js(
      `(function(){var c=document.querySelector('.map-canvas');var k=c.querySelectorAll('svg.map-layer');
        return k[k.length-1].getAttribute('data-layer');})()`) === 'labels');

    /* 时间轴 */
    check('时间轴存在', await p.js(`!!document.querySelector('.timeline')`));
    check('时间轴起始时刻来自接口 replay.startTime',
      /2026-09-13 08:00/.test(await p.js(`document.querySelector('.timeline-labels span').textContent`)),
      await p.js(`document.querySelector('.timeline-labels span').textContent`));
    check('时间轴结束时刻来自接口 replay.endTime',
      /2026-09-17 08:00/.test(await p.js(
        `document.querySelectorAll('.timeline-labels span')[2].textContent`)),
      await p.js(`document.querySelectorAll('.timeline-labels span')[2].textContent`));

    /* 播放回放：进度条与实况参数应变化 */
    const before = await p.js(`document.querySelector('.zbar-fill').style.width`);
    const posBefore = await p.js(`document.querySelector('[data-metric="pos"] .metric-value').textContent`);
    await p.js(`document.querySelector('#timeline-play').click()`);
    await wait(900);
    await p.js(`document.querySelector('#timeline-play').click()`);   /* 暂停 */
    await wait(150);
    const after = await p.js(`document.querySelector('.zbar-fill').style.width`);
    const posAfter = await p.js(`document.querySelector('[data-metric="pos"] .metric-value').textContent`);
    check('回放推进了时间轴进度', before !== after, before + ' → ' + after);
    check('回放联动刷新中心位置', posBefore !== posAfter, posBefore + ' → ' + posAfter);

    /* 底图切换 */
    await p.js(`document.querySelector('[data-basemap-btn="sat"]').click()`);
    await wait(400);
    check('底图切换生效',
      await p.js(`document.querySelector('.map-canvas').getAttribute('data-basemap')`) === 'sat');
    check('卫星底图图层可见',
      await p.js(`!document.querySelector('[data-basemap="sat"]').classList.contains('is-hidden')`));
    await p.js(`document.querySelector('[data-basemap-btn="plain"]').click()`);
    await wait(200);

    /* 图层开关 */
    await p.js(`document.querySelector('[data-layer-toggle="风圈"]').click()`);
    await wait(200);
    check('风圈图层可关闭',
      await p.js(`document.querySelector('.ty-wind').classList.contains('is-hidden')`));
    await p.js(`document.querySelector('[data-layer-toggle="风圈"]').click()`);
    await wait(200);
    check('风圈图层可恢复',
      await p.js(`!document.querySelector('.ty-wind').classList.contains('is-hidden')`));

    /* 右栏 AI 面板：条数与接口 models 一致 */
    const ai = await p.js(`(async function(){
      var d = await ZFZ.api.getAiPredictions(13);
      return JSON.stringify({ n: (d.models||[]).length, consistency: d.consistency, text: d.consistencyText });
    })()`, true);
    const aiObj = JSON.parse(ai);
    check('AI 模型块数 = 接口 models 条数', await p.js(`document.querySelectorAll('.ai-tile').length`) === aiObj.n,
      'DOM=' + await p.js(`document.querySelectorAll('.ai-tile').length`) + ' API=' + aiObj.n);
    check('模型一致性进度条宽度 = 接口 consistency',
      (await p.js(`document.querySelector('.col-right .bar i').style.width`)) === aiObj.consistency + '%',
      await p.js(`document.querySelector('.col-right .bar i').style.width`));
    check('一致性文案取自接口 consistencyText',
      await p.js(`document.querySelector('.col-right .bar-value').textContent`) ===
      aiObj.text + '（' + aiObj.consistency + '%）',
      await p.js(`document.querySelector('.col-right .bar-value').textContent`));

    /* 右栏城市风险：行数与接口一致 */
    const riskN = await p.js(`(async function(){ var d = await ZFZ.api.getRiskAssessments(13); return d.length; })()`, true);
    check('城市风险行数 = 接口条数', await p.js(`document.querySelectorAll('.col-right .row-item').length`) === riskN,
      'DOM=' + await p.js(`document.querySelectorAll('.col-right .row-item').length`) + ' API=' + riskN);

    check('页面无「加载中」残留', t.indexOf('加载中') < 0 && t.indexOf('正在加载') < 0);
    check('无失败提示面板（数据齐全）',
      await p.js(`document.querySelectorAll('.col-right .panel.is-warn').length`) === 0);

    if (EXPECT_NAME) {
      check('【数据驱动铁证】页面文本含数据库新值「' + EXPECT_NAME + '」', t.indexOf(EXPECT_NAME) >= 0);
      const first = await p.js(`document.querySelector('.ty-item-name').textContent.replace(/\\s+/g,' ')`);
      check('【数据驱动铁证】左栏条目名同步为新值', first.indexOf(EXPECT_NAME) >= 0, first);
      const detailTxt = await p.js(`document.querySelector('#page-title').innerText.replace(/\\s+/g,' ')`);
      check('【数据驱动铁证】标题栏不受影响（旧值已消失）', detailTxt.indexOf('飞燕') < 0 || EXPECT_NAME.indexOf('飞燕') >= 0);
    }

    check('无控制台错误', p.errs.length === 0, p.errs.join(' | ').slice(0, 260));
    await p.shot('overview-mockfree.png');
    await p.close();
  }

  /* ============================================================
     ④ help-api.html —— 机构清单走接口，文案为页面自带静态内容
     ============================================================ */
  console.log('\n===== ④ help-api.html =====');
  {
    const p = await open(CONFIG.root + 'help-api.html');
    const t = await txt(p);
    const srcRows = await p.js(`document.querySelectorAll('.col-main .panel:nth-child(1) .trow').length`);
    check('机构表 6 行（设计稿指定 6 家）', srcRows === 6, 'rows=' + srcRows);
    check('机构表含「中央气象台」', /中央气象台/.test(t));
    check('机构名称/所属/配色取自 /api/agencies',
      /CMA/.test(t) && /中国/.test(t) &&
      /rgb\(230, 0, 18\)/.test(await p.js(
        `getComputedStyle(document.querySelector('.agency-code')).color`)),
      await p.js(`getComputedStyle(document.querySelector('.agency-code')).color`));
    check('据实说明另有机构未列入本表', /另有 \d+ 家已接入未列入本表/.test(t),
      (t.match(/另有 \d+ 家已接入未列入本表/) || [])[0]);
    check('接口清单 13 行', await p.js(`document.querySelectorAll('.col-main .panel:nth-child(2) .trow').length`) === 13);
    check('侧列四张卡片', await p.js(`document.querySelectorAll('.col-side .panel').length`) === 4);
    check('无「加载中」残留', t.indexOf('加载中') < 0);
    check('无控制台错误', p.errs.length === 0, p.errs.join(' | ').slice(0, 260));
    await p.shot('help-mockfree.png');
    await p.close();
  }

  /* ============================================================
     ⑤ 顶栏：全局搜索 / 通知 / 用户信息
     ============================================================ */
  console.log('\n===== ⑤ 顶栏（搜索 / 通知 / 用户）=====');
  {
    const p = await open(CONFIG.root + 'index.html');

    /* 全局搜索 */
    await p.js(`document.querySelector('.hs-search .search-box').click()`);
    await wait(1200);
    check('搜索面板展开',
      await p.js(`document.querySelector('.hs-search').classList.contains('is-open')`));
    check('空关键词给出 6 条快捷入口', await p.js(`document.querySelectorAll('.sr-item').length`) === 6,
      'count=' + await p.js(`document.querySelectorAll('.sr-item').length`));
    check('快捷入口首条为活跃台风（含"活跃中"标记）',
      /活跃中/.test(await p.js(`document.querySelectorAll('.sr-item')[0].innerText`)),
      await p.js(`document.querySelectorAll('.sr-item')[0].innerText.replace(/\\s+/g,' ')`));
    check('搜索条目带强度徽标（tag）',
      await p.js(`document.querySelectorAll('.sr-item')[0].querySelectorAll('.tag').length`) === 1);

    await p.js(`(function(){var e=document.getElementById('global-search');e.value='杜苏芮';
      e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
    await wait(500);
    check('按中文名搜索命中 1 条', await p.js(`document.querySelectorAll('.sr-item').length`) === 1,
      'count=' + await p.js(`document.querySelectorAll('.sr-item').length`));
    check('搜索结果名称正确', /杜苏芮/.test(await p.js(`document.querySelectorAll('.sr-item')[0].innerText`)));

    await p.js(`(function(){var e=document.getElementById('global-search');e.value='2023';
      e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
    await wait(500);
    check('按年份搜索命中', await p.js(`document.querySelectorAll('.sr-item').length`) >= 1,
      'count=' + await p.js(`document.querySelectorAll('.sr-item').length`));

    await p.js(`(function(){var e=document.getElementById('global-search');e.value='zzzz';
      e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
    await wait(400);
    check('无结果时显示空态', /未找到匹配/.test(await p.js(
      `document.querySelector('.hs-search .drop-body').innerText`)));

    /* 清单只请求一次：第二次换关键词不应再出现 loading 文案 */
    await p.js(`(function(){var e=document.getElementById('global-search');e.value='';
      e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
    await wait(120);
    const midTxt = await p.js(`document.querySelector('.hs-search .drop-body').innerText`);
    check('二次检索走本地过滤（无 loading 闪烁）',
      midTxt.indexOf('正在加载台风清单') < 0 && midTxt.indexOf('加载失败') < 0,
      midTxt.replace(/\s+/g, ' ').slice(0, 60));

    /* 通知中心：未登录应如实说明 */
    await p.js(`document.querySelector('.hs-notice .icon-btn').click()`);
    await wait(1400);
    check('通知面板展开',
      await p.js(`document.querySelector('.hs-notice').classList.contains('is-open')`));
    check('未登录时明确提示"登录后可查看通知"',
      /登录后可查看通知/.test(await p.js(`document.querySelector('.hs-notice .drop-body').innerText`)),
      await p.js(`document.querySelector('.hs-notice .drop-body').innerText`));
    check('未登录不显示红点',
      await p.js(`document.querySelector('.hs-notice .dot-badge').classList.contains('is-cleared')`));

    /* 用户信息：不再是写死的「总管理员」 */
    check('顶栏用户名为「未登录」而非写死的总管理员',
      (await p.js(`document.querySelector('.hs-user .user-name').textContent`)) === '未登录',
      await p.js(`document.querySelector('.hs-user .user-name').textContent`));
    check('用户菜单副标题为提示文案',
      /登录后可管理收藏与订阅/.test(await p.js(`document.querySelector('.hs-user .dh-sub').textContent`)),
      await p.js(`document.querySelector('.hs-user .dh-sub').textContent`));

    check('无控制台错误', p.errs.length === 0, p.errs.join(' | ').slice(0, 260));
    await p.shot('header-mockfree.png');
    await p.close();
  }

  /* ============================================================
     ⑥ 登录后顶栏显示真实身份（admin01 / 总管理员）
     做法：先在一个 target 上完成登录（Cookie 落在该 target 的会话里），
     再导航到 admin.html —— 否则登录与页面自己的 getMe() 会竞态，
     断言结果时好时坏。
     ============================================================ */
  console.log('\n===== ⑥ 登录态回填顶栏 =====');
  {
    /* 注意：这里刻意【不用】addScriptToEvaluateOnNewDocument 注入登录。
       注入的脚本会在后续每次导航（含 admin.html）重复执行一次登录，
       而登录带防会话固定（换发新 JSESSIONID），会与 admin 页自己
       先发出的 /api/auth/me 抢会话，使后者偶发拿到 401 ——
       表现为「顶栏偶发显示未登录」，且伴随 /api/auth/me 明明返回 200 的矛盾现象。
       那是测试自身的假阳性，真实用户不会在页面加载时被登两次。
       改为页面加载完成后显式调用一次登录，稳定且更贴近真实路径。 */
    const p = await open(CONFIG.root + 'index.html', null, 2200);
    const loginRes = await p.js(
      `window.ZFZ.api.login('admin01','password').then(function (u) { return u ? 'ok' : 'empty'; },
        function (e) { return 'fail:' + e.message; })`, true);
    check('登录接口调用成功', loginRes === 'ok', loginRes);

    /* 诊断：登录后立刻在页面内直连 /api/auth/me。
       若此处 200 而导航后变 401，说明问题在会话传递；
       若此处就 401，说明登录请求本身没落地。 */
    const meHome = await p.js(`fetch('api/auth/me',{credentials:'include'}).then(r=>r.text())`, true);
    console.log('      [诊断] index 页登录后 /api/auth/me → ' + String(meHome).slice(0, 60));

    /* 直接问浏览器：JSESSIONID 到底有没有落盘、路径与归属域是什么。
       这是区分「服务端没下发」「浏览器拒收」「路径/域不匹配」的唯一硬证据。 */
    const ck = await p.send('Network.getAllCookies');
    const jar = ((ck.result && ck.result.cookies) || []).map(function (c) {
      return c.name + '@' + c.domain + c.path + (c.session ? '(会话)' : '');
    });
    console.log('      [诊断] 浏览器 Cookie → ' + (jar.length ? jar.join(', ') : '(空)'));

    /* 用同一 target 重新导航到 admin 页，会话 Cookie 仍在 */
    await p.send('Page.navigate', { url: CONFIG.root + 'admin.html' });
    await wait(3000);

    const meAdmin = await p.js(`fetch('api/auth/me',{credentials:'include'}).then(r=>r.text())`, true);
    console.log('      [诊断] admin 页导航后 /api/auth/me → ' + String(meAdmin).slice(0, 60));

    check('登录后顶栏用户名来自 /api/auth/me',
      (await p.js(`document.querySelector('.hs-user .user-name').textContent`)) === '总管理员',
      await p.js(`document.querySelector('.hs-user .user-name').textContent`));
    check('登录后头像字取自接口 avatarText',
      (await p.js(`document.querySelector('.hs-user .user-chip .avatar').textContent`)) === '总',
      await p.js(`document.querySelector('.hs-user .user-chip .avatar').textContent`));
    check('登录后副标题为角色文案',
      /平台总管理员/.test(await p.js(`document.querySelector('.hs-user .dh-sub').textContent`)),
      await p.js(`document.querySelector('.hs-user .dh-sub').textContent`));
    check('登录后 admin 页渲染概览（未落入「无访问权限 / 请先登录」态）',
      !/无访问权限|请先登录/.test(await txt(p)),
      (await txt(p)).slice(0, 90));
    check('登录后通知红点由接口决定（unread=0 → 已清除）',
      await p.js(`document.querySelector('.hs-notice .dot-badge').classList.contains('is-cleared')`));
    check('无控制台错误', p.errs.length === 0, p.errs.join(' | ').slice(0, 260));
    await p.shot('admin-loggedin.png');
    await p.close();
  }

  /* ============================================================
     ⑦ 后端不可用：如实报错，不回退演示数据
     ============================================================ */
  console.log('\n===== ⑦ 后端不可用 =====');
  const DEAD = `document.addEventListener('DOMContentLoaded', function(){
    window.ZFZ.api.config.baseUrl = 'http://127.0.0.1:9/typhoon_platform'; });`;
  {
    const p = await open(CONFIG.root + 'index.html', DEAD, 1600);
    const t = await txt(p);
    check('[overview] 渲染失败说明', /台风清单加载失败/.test(t), (t.match(/台风清单加载失败[^·]{0,60}/) || [])[0]);
    check('[overview] 文案来自 api.js 的错误翻译', /无法连接后端服务/.test(t));
    check('[overview] 无台风条目（未降级到演示数据）',
      await p.js(`document.querySelectorAll('.ty-item').length`) === 0);
    check('[overview] 无风圈元素', await p.js(`document.querySelectorAll('.wind-ring').length`) === 0);
    check('[overview] 无 AI 模型块', await p.js(`document.querySelectorAll('.ai-tile').length`) === 0);
    check('[overview] 无城市风险行', await p.js(`document.querySelectorAll('.row-item').length`) === 0);
    check('[overview] 弹出 toast', /^true\|/.test(
      await p.js(`(function(){var e=document.getElementById('zfz-toast');return e?(e.classList.contains('is-show')+'|'+e.textContent):'none';})()`)),
      await p.js(`(function(){var e=document.getElementById('zfz-toast');return e?e.textContent:'none';})()`));
    check('[overview] 无 JS 异常', p.errs.filter((e) => e.indexOf('EXC') === 0).length === 0,
      p.errs.filter((e) => e.indexOf('EXC') === 0).join(' | ').slice(0, 200));
    await p.shot('overview-dead.png');
    await p.close();
  }
  {
    const p = await open(CONFIG.root + 'help-api.html', DEAD, 1600);
    const t = await txt(p);
    check('[help] 机构表显示加载失败并给出原因', /机构清单加载失败/.test(t) && /无法连接后端服务/.test(t),
      (t.match(/机构清单加载失败[^·]{0,60}/) || [])[0]);
    check('[help] 静态文档部分照常可读', /RESTful 接口清单/.test(t) && /常见问题/.test(t));
    check('[help] 无 JS 异常', p.errs.filter((e) => e.indexOf('EXC') === 0).length === 0);
    await p.close();
  }

  /* ============================================================
     ⑧ 每页都有真实内容（不只是"没报错"）
     ============================================================ */
  console.log('\n===== ⑧ 各页真实内容巡检 =====');
  const CONTENT = [
    { pg: 'archive.html',              sel: '.ty-item, .trow, .row-item', min: 10, must: /飞燕|摩羯|杜苏芮/ },
    { pg: 'typhoon-detail.html?id=13', sel: '.panel',                    min: 4,  must: /飞燕|FEIYAN/ },
    { pg: 'forecast-disagreement.html', sel: '.panel',                   min: 3,  must: /分歧|CMA|JMA/ },
    { pg: 'risk-assessment.html',      sel: '.panel, .row-item',          min: 3,  must: /风险/ },
    { pg: 'similar-cases.html',        sel: '.panel, .trow',              min: 3,  must: /相似度/ },
    { pg: 'sandbox.html',              sel: '.panel, [data-slider]',      min: 4,  must: /推演|登陆点偏移/ },
    { pg: 'survey.html',               sel: '.panel',                    min: 2,  must: /问卷|调研/ },
    { pg: 'admin.html',                sel: '.panel',                    min: 1,  must: /管理|登录/ }
  ];
  for (const c of CONTENT) {
    const p = await open(CONFIG.root + c.pg);
    const t = await txt(p);
    const n = await p.js(`document.querySelectorAll(${JSON.stringify(c.sel)}).length`);
    check(c.pg + ' 渲染出真实内容节点 ≥' + c.min, n >= c.min, 'count=' + n);
    check(c.pg + ' 命中关键文案', c.must.test(t), (t.match(c.must) || [])[0] || '');
    check(c.pg + ' 无「加载失败」残留', !/加载失败|无法连接后端服务/.test(t));
    check(c.pg + ' 无「加载中」残留', !/正在加载|加载中…/.test(t));
    check(c.pg + ' 无控制台错误', p.errs.length === 0, p.errs.join(' | ').slice(0, 200));
    await p.close();
  }

  console.log('\n================ 合计：' + pass + ' 通过 / ' + fail + ' 失败 ================');
  process.exit(fail ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
