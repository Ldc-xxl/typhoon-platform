# 追风者 · Issue 跟踪清单

> 课程要求对应（《课程要求.pptx》阶段七 · ②）：**全面修复前后端联调中发现的 Bug，建立 Issue 跟踪清单**。
>
> 状态图例：`待修复` / `修复中` / `已修复` / `待确认`
>
> **本文档记录的是联调期发现并已处置的历史缺陷**（大多数已修复），
> 用来说明"怎么发现、怎么修的"。
> **当前代码仍存在的问题**（含未达标项与遗留隐患）另见
> [`code-review.md`](code-review.md)，一份是病历，一份是体检报告，不要混看。

---

## 一、P0 · 会导致功能不可用

| # | 问题 | 位置 | 影响 | 状态 |
|---|---|---|---|---|
| P0-1 | `pom.xml` 不是合法 XML —— 注释内出现连续两个短横线（分隔线 `-----`），XML 规范禁止 | `pom.xml:6` | **Maven 无法解析 POM，整个项目构建不起来** | 已修复（分隔线改为 `=`） |
| P0-2 | 同上问题，`web.xml` 有 3 处 | `WEB-INF/web.xml:5,48,53` | Maven 不报错但 **Tomcat 解析 web.xml 失败，应用部署不起来** | 已修复 |
| P0-3 | 预置账号一律登不进去：初始化脚本存的是**不加盐** `SHA-256(口令)`，而 `PasswordUtil.verify` 见到 `sha256$` 前缀只按**加盐**方式比对 | `util/PasswordUtil.java` | `admin01 / dataadmin01` 及全部预置用户登录必然失败 | 已修复（`verify` 改为加盐→不加盐依次尝试） |
| P0-4 | `forecast_disagree_records` 的 4 个偏差列均为 `NOT NULL`，而分歧服务可能取值 `null` | `service/impl/DisagreementServiceImpl` | 分歧记录 INSERT 被 MySQL 拒绝 | 已修复（缺失维度回退 0） |
| P0-5 | 数据库名不一致：DDL 建的是 `typhoon_monitor`，`db.properties` 连的是 `typhoon_platform` | `Dump*.sql` / `db.properties` | 导入脚本后应用连不上库 | 已修复（**以实际存在的库名为准，统一为 `typhoon_monitor`**；库名只在 `db.properties` 与 `db/typhoon_monitor.sql` 两处出现且必须一致。⚠️ 中途曾试过反向统一为 `typhoon_platform`，因本机 3306 上真实库名是 `typhoon_monitor` 而回退 —— 教训：改口径要改「真实存在的那一份」，不是改代码去迁就文档） |
| P0-6 | **6 个页面完全没接后端数据**，全部渲染硬编码字面量，接口调用只是装饰（`risk.js` 甚至在点击回调里 `window.alert()` 一个数量后丢弃结果） | `pages/risk.js` `disagreement.js` `similar.js` `sandbox.js` `survey.js` `archive.js` | 违反课程阶段五 ③「接口对接」；答辩演示时数据永远是那几条死数据 | 已修复（改为真实 API 渲染） |

## 二、P1 · 字段契约对不上（页面会出现 `undefined` 或渲染错乱）

| # | 问题 | 前端消费 | 后端实际 | 状态 |
|---|---|---|---|---|
| P1-1 | `admin.js` 用户表格读 `u.ops`，`UserVO` 没这个字段 | `u.ops` | 无 | 已修复（`UserVO` 增加 `ops`） |
| P1-2 | 百分比量纲差 100 倍：后端按 0—1 小数返回，页面当 0—100 直接拼 `%` | `survey.js` 的 `suppliesRate` 等 | `0.30` | 已修复（页面 ×100） |
| P1-3 | 分歧指数量纲：后端 0—1，页面按 0—100 | `disagreement.js` | `0.72` | 已修复（页面 ×100） |
| P1-4 | 相似度分数量纲：后端 0—1，页面按 0—100 | `similar.js` | `0.87` | 已修复（页面 ×100） |
| P1-5 | mock 的 `/api/risk/assessments` 返回的是「受影响城市」结构（`city/expectLevel/color`），与真实接口契约不同源 | `risk.js` | `RiskAssessmentVO` | 已修复（页面改读 `RiskAssessmentVO` 字段） |
| P1-6 | 字段命名不一致：`city` vs `cityName`、`expectLevel` vs `windLevel`、`lv` vs `riskLevel`、`value` vs `riskScore` | 多个页面 | — | 已修复 |
| P1-7 | `docs/api.md` 3.8 节示例写 `confidence: 0.72`，而数据库列注释、`AiPredictionVO`、前端 mock 三处都是 **0—100** | — | `78` | 已修复（文档对齐实现） |
| P1-8 | `docs/api.md` 3.2 节字段用了 `seaArea/birthTime`，与 `data.js` 的 `basin/generateTime` 不符；3.6 节机构配色与数据库 `display_color` 不一致 | — | — | 已修复（文档对齐实现） |
| P1-9 | 接口文档 URL 多处带 `{id}` 前缀差异（如文档 `/api/typhoons/{id}/tracks` 与前端调用一致，但 3.2 示例参数与实现不符） | — | — | 已修复 |
| P1-10 | `user_behavior_surveys` 表名与 DAO 命名不符（单复数） | — | 表名为复数 | 已核对（DAO 已按复数 SQL 编写） |

## 三、P2 · 其他缺陷与隐患

| # | 问题 | 位置 | 状态 |
|---|---|---|---|
| P2-1 | `overview.js` / `detail.js` / `help.js` / `admin.js` / `layout.js` 直接读 `ZFZ.mock.*` 绕过接口层 | `js/pages/*` `js/layout.js` | 已修复（改为走 `ZFZ.api`；**后续进一步把演示数据层整体删除**：`js/data.js` 物理移除、10 个 HTML 去掉引用、`api.js` 的 `useMock` 开关与全部 mock 分支清零，全站 `grep ZFZ.mock` 为 0。接口失败时如实报错，不再静默回退假数据） |
| P2-2 | 分歧阈值键名自造：代码用 `disagree.index.threshold`，而 `system_settings` 已有 `high_uncertainty_threshold` | `DisagreementService` | 已修复（改用已有键，避免同一阈值两处配置） |
| P2-3 | 种子数据与前端 mock 的城市对不上：DB 给台风 13 的是「厦门/海口/福州」，mock 是「厦门/泉州/福州」 | `Dump*.sql` | 已修复（种子数据对齐 mock） |
| P2-4 | 预置用户 `user01`—`user08` 的 `password_hash` 是**伪造的**（末尾字节递增 `...0b0a/0b0b/0b0c`），任何口令都校验不通过 | `Dump*.sql` | 已修复（统一重算为项目加盐格式） |
| P2-5 | `sandbox` 参数命名两套并存：`system_settings` 用下划线（`circle_scale`），前端用驼峰（`windScale`） | — | 已修复（DTO 用 `@JsonAlias` 同时接受） |
| P2-6 | `risk_assessments.user_id` 为非空列，但读接口不应写库 —— 否则每次刷新页面都产生副作用 | `RiskAssessmentService` | 已修复（GET 只算不落库，写库走独立方法） |
| P2-7 | 用户表「编辑」按钮没有对应表单，点了只弹一条带接口路径的开发者备注（`PUT /api/admin/users/5`），相当于把未完成项直接展示给使用者 | `js/pages/admin.js` | 已修复（补 `.zdlg` 弹层：账号只读 + 昵称/邮箱/手机/角色；角色变化时先 `/role` 后 `/users/{id}`，失败不留半截状态；当前登录账号的角色锁定并写明原因。`tools/probe-user-edit.cjs` 28 项全通过，含一次真实落库并还原） |
| P2-8 | 大模型接入表是**纯展示**的：`status` / `keyConfigured` 都只看 `ai_prediction_records` 有没有历史记录（`model_name` 只是融合算法写进去的标签），"接入"按钮只弹 toast，后端**没有任何写接口** —— 于是平台从未真正调用过任何厂商模型，却把 DeepSeek/通义显示成「已接入」 | `model/vo/ModelConfigVO`、`service/impl/AdminServiceImpl`、`servlet/AdminServlet`、`js/pages/admin.js` | 已修复（新增 `llm_provider_config` 表 + `LlmConfigService` / `LlmPredictionService` / `OpenAiCompatLlmClient`；`POST|DELETE /api/admin/models/{key}/connect`、`POST .../test`、`POST .../predict`；状态改「配置表优先、历史记录兜底」三态；`latency_ms` 实测落库。`tools/probe-model-connect.cjs` 41–46 项全通过，含真实外网请求与两次真实落库） |
| P2-9 | 断连时 `mdlgModel` 已被置空却仍读 `mdlgModel.name` → `TypeError`，断开成功的 toast 永远出不来 | `js/pages/admin.js` `ensureModelDialog` | 已修复（关闭前先 `var name = mdlgModel.name`）。**由 P2-8 的探针抓出**，纯读代码不易发现 |
| P2-10 | 「断开接入」按钮成功后停在 `disabled`，而弹层是四个模型**共用的同一个实例** —— 断过一次之后，其余模型的断开按钮再也点不动（只能刷页） | `js/pages/admin.js` `openModelDialog` | 已修复（每次打开时显式 `.prop('disabled', false)`）。同上，由探针抓出 |
| P2-11 | `paint()` 整块替换 `#page-body` 会销毁焦点所在节点，焦点掉到 `<body>`。弹层里点「保存」的时序是"关弹层→归还焦点→接口才返回→重渲染"，刚还回去的焦点又被换掉；点「启用/停用」同理，键盘用户需从头 Tab | `js/pages/admin.js` `paint` | 已修复（新增 `describeFocus()` / `restoreFocus()`：重渲染前按 `data-*` 身份记住焦点，画完找回；只接管确实位于 `#page-body` 内的焦点，弹层自分挂 `<body>` 不受影响）。同上，由探针抓出 |

## 四、切换真实数据库（3306 / `typhoon_monitor`）时新发现

> 背景：把工程从隔离验证库切换到本机 3306 实例的真库后重新验证，
> 后端 57 条接口用例全通过，前端 134 条断言中**顶栏登录态偶发失败**，
> 追查后定位到两个前端缺陷（均与后端无关）。

| # | 问题 | 位置 | 现象 | 状态 |
|---|---|---|---|---|
| P1-11 | `loadSession()` 把 `/api/auth/me` 的**任何**失败都画成「未登录」，且失败回调连 `err` 参数都没接 | `js/layout.js` | 超时/5xx 等故障与真实 401 表现完全一样，用户明明登录着、顶栏却写未登录，被误导去重登 | 已修复（引入 `sessionState` 四态：仅 401 画未登录，其它画「连接异常」并自动重试一次；同文件的 `renderNotice()` 早已用 `isUnauthorized(err)` 区分，说明此处属遗漏） |
| P1-12 | 登录成功后不刷新顶栏 | `js/pages/admin.js` 登录成功回调 | 顶栏身份是页面加载时那一次 `getMe()` 画的（那时未登录），登录后只调 `boot()` 加载数据，顶栏仍显示未登录，需手动刷页 | 已修复（`layout.js` 新增并导出 `refreshSession()` / `sessionStatus()`，登录成功后调用） |

**定位过程记录**（这类"偶发"问题最值得留证据）：

1. 同一套代码连跑 4 次，失败 2 次 —— 确认是竞态而非稳定缺陷；
2. 失败时在页面内直接 `fetch('api/auth/me')` → **返回 200**，说明会话有效；
3. 用 CDP `Network.getAllCookies` 查浏览器 Cookie →
   `JSESSIONID@127.0.0.1/typhoon_platform` **确实存在**；
4. 三条证据同时成立却画成未登录 ⇒ **问题在前端渲染，不在会话**；
5. 再看代码：`loadSession()` 的失败分支无条件画「未登录」，且在页面生命周期里**只执行一次、没有任何重绘路径**。

另外，验证脚本自身也有一处假阳性需要记下来，避免日后重踩：
它原先用 `Page.addScriptToEvaluateOnNewDocument` 注入登录脚本，
该脚本会在**每次导航**（含 `admin.html`）重复执行一次登录；
而登录带防会话固定（换发新 JSESSIONID），会与 `admin.html` 自己先发出的
`/api/auth/me` 抢会话，使后者偶发拿到 401。
**这是测试脚本的问题，不是产品缺陷** —— 真实用户不会在页面加载时被登录两次。
已改为页面加载完成后显式调用一次登录。

## 五、补单元测试期间新发现（第二轮）

> 背景：按课程要求补 JUnit 5 + Mockito 单元测试（行覆盖率 0 → 77.4%），
> 以及把接口回归脚本重写落盘后，又跑出 6 个问题。
> **其中 4 个是生产缺陷**，且它们在页面上的表现高度一致 ——
> 都是「数据访问失败，请稍后重试」这句毫无指向性的提示，
> 靠手工点页面复现根本定位不到根因。

| # | 问题 | 位置 | 现象 / 后果 | 状态 |
|---|---|---|---|---|
| N-1 | `queryNumber()` 在**空结果集**上调用 `rs.wasNull()` | `util/JdbcTemplate.java` | MySQL Connector/J 抛 `NullPointerException`（`thisRow` 为 null）；任何"查不到数据"的聚合查询都直接 500，而正确行为应该是返回 null | 已修复（删掉空结果集上的 `wasNull()`） |
| N-2 | `successRate()` 在 `total == 0` 时返回 `100d` | `dao/impl/SyncLogDaoImpl.java` | **一次都没同步过的数据源，后台显示"成功率 100%"** —— 静默误导，运维会以为一切正常 | 已修复（改为返回 `0d`） |
| N-3 | `requireAdmin()` 未登录也抛 403 | `servlet/BaseServlet.java` | 与 `AuthFilter` 口径不一致（未登录应 401、已登录非管理员才 403）。对没登录的人说"你没有管理员权限"是误导，前端也无法触发 401 对应的"跳登录页"逻辑 | 已修复（先判登录态：未登录 401，非管理员 403） |
| N-4 | `PUT /api/typhoons/{id}` 是**全量覆盖**而非局部更新 | `service/impl/TyphoonServiceImpl.java` | `TyphoonDaoImpl.update` 生成的是全列 `UPDATE`，未传的字段以 null 写入：轻则清空台风名/编号，重则撞 `international_id NOT NULL` 抛 500。而 `js/api.js` 的文档明确写的是「`payload` 待更新字段」 | 已修复（先读库中记录 → 用非 null 字段覆盖 → 整体回写） |
| N-5 | 两个 Service 的带参构造声明**实现类**而非接口 | `ForecastServiceImpl` / `TrackServiceImpl` | JDK 25 上 Mockito 无法改写 `java.lang.Object`，导致这两个类**根本没法写单测**（依赖倒置也被破坏） | 已修复（参数类型放宽为 `ForecastPathDao` 接口） |
| N-6 | `js/api.js` 的 `feedbackType` 示例值 `'BUG'` 是**非法值** | `js/api.js:708` JSDoc | 后端只接受 `DATA_ISSUE` / `FEATURE_SUGGESTION` / `PROBLEM_REPORT`，照文档写会 400 | 已修复（文档改为 `DATA_ISSUE` 并列出三个合法值） |

### N-4 的定位过程（值得留证据）

回归脚本按文档只传 `{'typhoonId': tid, 'maxWindSpeed': 38}`，接口返回 500
「数据访问失败，请稍后重试」。响应体里没有任何有用信息 ——
**因为 `BaseServlet` 把所有 `DataAccessException` 统一翻译成了这句通用文案**
（这是有意的：SQL 细节不该暴露给前端）。

真正的错误只在服务端日志里：

```
com.zfz.typhoon.util.DataAccessException: 更新失败：Column 'international_id' cannot be null
    at com.zfz.typhoon.util.JdbcTemplate.update(JdbcTemplate.java:244)
    at com.zfz.typhoon.dao.impl.TyphoonDaoImpl.update(TyphoonDaoImpl.java:185)
    at com.zfz.typhoon.service.impl.TyphoonServiceImpl.update(TyphoonServiceImpl.java:192)
```

到这里才看清：**不是参数校验漏了，而是 PUT 的语义与实现不一致** ——
文档承诺局部更新，实现做的是全量覆盖。

> 顺带记一条通用教训：本项目的异常设计让"看日志"成为定位后端 500 的**唯一**手段，
> 所以回归脚本一旦报红，第一件事应该是翻 `logs/catalina.<日期>.log`，
> 而不是反复改请求参数试。

### 一处保留不改的语义选择

登录失败返回 **400** 而非 401（`AuthServiceImpl.java:107`）。
按 HTTP 语义 401 更"标准"，但 `js/layout.js` 的 `isUnauthorized(err)`
把 `code === 401` 判定为"会话失效"，会触发跳登录页的渲染；
而"口令填错"要的是在表单原地提示。两者共用 401 会让前端难以区分，
故**保留 400**，理由已写进 `code-review.md` 的 P2-11 与回归脚本注释。

## 六、审计方法说明
上述 P0-6 / P1-* / P2-1 是通过**逐页面字段比对**得出的，做法是：

1. 读 `js/api.js` 得到前端真实调用契约（URL + 方法）；
2. 逐页读 `js/pages/*.js`，抽出**渲染时实际访问的字段路径**；
3. 读后端 `model/vo/*.java`，抽出实际会输出的 JSON 字段名；
4. 三方比对（api.md 文档 / api.js 前端 / VO 后端），以 **`api.js` + `data.js` 为准**，
   文档与实现不符时改文档。

> 之所以以 `api.js` 而不是 `docs/api.md` 为准：`api.js` 是**可执行的**契约，
> 页面实际按它发请求；文档是描述，可能与代码脱节。实测确有脱节，见 P1-7 / P1-8。

## 七、阶段七性能优化（第三轮，已落地）

| 编号 | 瓶颈 | 修复 | 前后对比 |
|---|---|---|---|
| OPT-1 | 预编译语句未开客户端缓存（`db.properties` 的 `jdbc.url`） | 追加 `cachePrepStmts=true&prepStmtCacheSize=500&prepStmtCacheSqlLimit=2048` | 热路径延迟 ↓16–25%、吞吐 ↑34%（keep-alive 实测） |
| OPT-2 | 连接池每次借出都 `isValid` 探活（一次网络往返） | `SimpleConnectionPool` 引入 `IdleConn` 携带归还时间戳，仅空闲超 `pool.idleSafeNs`(默认 5s) 才探活；窗口可参数化，设 `0` 退回旧行为 | 同上（与 OPT-1 合并计入 DB 层对比） |
| OPT-3 | 静态资源无浏览器缓存头 | 新增 `CacheFilter`（`@WebFilter("/*")`）：静态资源 `Cache-Control: public, max-age=86400`，`/api/` 一律 `no-store` | 重复访问首页约 350KB 静态资源由"条件请求"变为"本地缓存 0 网络往返" |

- 测量脚本：`tools/bench_perf.py`（keep-alive 长连接，仅 Python 标准库）。
- 完整记录与数据表见 `docs/performance-optimization.md`。
- 回归：`tools/verify_api.py` 112/0、`tools/zfz-mockfree-verify.cjs` 134/0、`mvn verify` 845/0 且覆盖率闸门满足，确认优化无功能回归。

## 八、交付前清理（P2-10，已做）

| 编号 | 事项 | 处理 |
|---|---|---|
| P2-10 | 父目录 `web课程项目` 下存在旧前端 `zhuifengzhe-web/` 与设计稿 `设计稿导出/`，评审易误认为"两份前端" | **交付目录已清爽**（2026-10-11 复核：交付目录仅含 `typhoon_platform/` + 课程要求文档 pptx/docx + `Dump20260916.sql`，全树 6 层内搜不到 `zhuifengzhe*` / `设计稿*`）。**但原记的"已移出至 `C:/Users/17168/Desktop/web课程项目_旧副本归档/`"经核查不成立** —— 该目录不存在，且旧副本不在 C:/D 任一回收站，**已不可恢复**（详见下方「P2-10 更正」）。 |
| P2-1 | `sql/` 目录（仅 `.gitkeep` + 指向 `db/` 的 README）与 `db/typhoon_monitor.sql` 重复，易误判"两份建库脚本" | **已删除 `sql/`**（`README.md` 目录树同步移除该条目），唯一建库脚本即 `db/typhoon_monitor.sql`。 |
| P2-3 | 导出仅支持 CSV，无 Excel（课程要求阶段七①「数据导出 Excel」） | **已修复**：`ExportServlet`/`ExportServiceImpl` 新增 `format=xlsx`，`util/XlsxUtil` 手写 OOXML 生成真实 `.xlsx`（不引 POI）；轨迹页与档案页均提供「导出 Excel」按钮。 |
| P2-4 | 统计图未接入 ECharts（课程要求阶段四④「Bootstrap + AJAX + ECharts」） | **已修复**：`admin.html` 经 CDN 引入 ECharts 5.5.1，新增 `GET /api/admin/stats` 提供用户累计增长 + 角色分布数据，`pages/admin.js` 用折线图与饼图渲染。 |
| 阶段七④ | Git 版本管理（加分项） | **已 `git init`** 初始化仓库，`.gitignore` 已排除含口令的 `db.properties` 与 `target/`；首次提交见仓库日志。 |
| P1-4 | 登录失败锁定是单 JVM 内存实现，Tomcat 重启即清零（安全防护加分项待补） | **已修复**：新增 `login_attempts` 表（第 27 张表）持久化失败计数与锁定截止时间；`INSERT ... ON DUPLICATE KEY UPDATE` 避免并发丢计数；表缺失时自动降级内存，**不影响登录**。**本机已于 2026-10-13 执行** `db/migration_login_attempts.sql`（幂等；库内 26 → 27 张表，与主脚本 `CREATE TABLE` 集合完全一致）。**实测**：对不存在账号连打 5 次错误口令 → 计数 1→5 落库、第 5 次写入锁定截止时间并归零 → 第 6 次回 403「账号已锁定，请 15 分钟后重试」；**重启 Tomcat 后同一账号仍被拒（提示 15→14 分钟）**，证明判定取自数据库而非 JVM 内存；探针数据已清理，演示账号回归 code 200。 |
| P2-6 | 量纲两套并存（0—1 与 0—100），只靠注释约定，历史上出过 100 倍偏差 | **已缓解**：`DisagreementVO.dimensions[].variance`、`ModelConfigVO.confidence` 补齐量纲 JavaDoc；`docs/api.md` 新增 **1.4 量纲总表**，固化唯一口径并点明 `models[].confidence`（0—100）与融合 `confidence`（0—1）同名不同量纲的坑。 |
| P2-8 | 前端 JSDoc 覆盖不均，课程要求"JS 需有完整 JSDoc"仅部分满足 | **已修复**：`overview.js`（原 0 注释 / 30 函数）与 `help.js`（原 0 注释 / 7 函数）全量补齐；另补 `archive.js` / `detail.js` 的 `doExport`。现 `pages/*.js` **全部函数均有 JSDoc**（脚本扫描 0 遗漏），10 个文件 `node --check` 语法全通过。 |
| P3-1 | 监测总览 / 情景推演的**路径线看不见** | **已修复**：不是"线没画"，是**投影把它画到了画布外**。投影把经纬度写死映射到设计稿窗口（P1 `804×640` ≈ 105°E—135°E，P6 `1012×366` ≈ 113°E—130°E），而真实活跃台风 202629 小熊在 **154.5°E / 36.0°N**，整条轨迹落到 `x≈2472`。修复方式：`ui.js` 新增视野层 `fitBox()`，越界时自动等比缩放 + 平移，**底图 `<img>`、各矢量层、HTML 浮层标注共用同一变换**，并在地图角上明示"已自动缩放至 N% · 底图覆盖 105°E—135°E"、越界时补一层同投影经纬网兜底；数据本来就装得下时 `auto=false`，与设计稿逐像素一致。**实测**：P1 `track-live` 可见面积 223834 = 自身面积（100% 入画），缩放 70%；P6 两条路径 41810 = 自身面积，平移后居中。排查中还修掉两个几何错误：① 只按"数据跨度"判断会漏掉"跨度小但整体偏移到画布外"的情形；② 变换少了一次"把画布中心加回来"，导致整个图层额外平移半屏。 |
| P3-2 | 档案检索页只有名称可输入，年份 / 强度 / 海域 / 状态是**静态文本** | **已修复**：后端 `/api/typhoons` 早就支持 `year` / `minWindForce` / `basin` / `status`，前端却没给出可操作的控件——用户看到四个"框"却点不动。现改为 `ZFZ.ui.fieldSelect` 四个真实下拉（选项一律取自 `GET /api/typhoons/meta`，不在前端写死），可任意组合；「重置条件」清空全部下拉；结果区副标题写明当前生效条件（`filterSummary`）。**实测**（`tools/probe-archive-filters.cjs`，8/8 断言通过）：全量 44 条 → 年份 2026 为 32 条 → 叠加"峰值 ≥ 12 级"为 14 条 → 重置回 44 条；翻页保留条件（第 1/4 页 → 第 2/4 页仍带"2026 年"）。 |
| P3-3 | 浏览器缓存旧 `js/api.js`，导致三个派生分析页报 `ZFZ.api.analyzeTarget is not a function` | **已修复**：根因在**服务端缓存策略**而非代码——`CacheFilter` 对本项目自身的 js / css / html 也下发 `public, max-age=86400`，浏览器 24 小时内不会重新拉取，而新页面已调用新方法。现改为：`/api/**` → `no-store`；`/vendor/**` → 保持 1 天（第三方库不会在开发中改动）；**其余（HTML / js / css / assets）→ `no-cache`**（可缓存但每次回源校验，未变则 304 不重传响应体，350KB 的优化仍成立）。另新增 `tools/bump-asset-version.py` 批量刷新资源引用上的 `?v=` 版本串——`no-cache` 只对将来的请求生效，对已经握着旧强缓存的浏览器无能为力，必须换新 URL 才能立刻绕过。**实测**：无缓存实例下三个页面均正常（`analyzeTarget` 为 function，无任何控制台错误）；响应头已变为 `Cache-Control: no-cache`。 |

| P3-4 | 监测总览左栏的**活跃台风点不动**：列表项是 `<a href="typhoon-detail.html?id=…">`，点一下直接跳走，无法把某一台风的路径调到右侧地图上看 | **已修复**：`U.tyList()` 的行容器由 `<a>` 改为 `div[role=button][tabindex=0][data-ty-id]`（主行为=在地图聚焦该台风），行内保留真正的 `<a class="ty-item-detail">详情</a>` 指向档案页，保住中键 / ⌘ 点击开新标签的习惯，并补了 Enter / Space 与 `:focus-visible`（`div` 没有浏览器默认焦点环）。`overview.js` 引入 `state.focus`（默认 = `state.live`）：点击任一条目后，**地图（轨迹 / 风圈 / 预报路径 / AI 路径 / 回放时间轴）与右栏（实况参数 / AI 测算 / 城市风险）一起切换**，派生分析卡片不再走 `analyzeTarget` 的自动选台（用户已明确指定要看谁），该台风没有数据就如实显示空态；左栏提示条补「返回最新」出口，右栏补「你选中的台风」说明条。取数口径抽成 `loadSeries()` 供首次加载与切换聚焦两条路径共用，避免"某条路径上少取一项"的缺陷。**实测**：`tools/probe-overview-focus.cjs` **36/36 通过**——点第 2 个活跃台风（飞燕 202615）后 `data-live-typhoon` 167→13、列表高亮与右栏参数同步、轨迹 bbox 变化且仍 100% 落在画布内、出现「返回最新」；历史档案分组的条目同样可切换；点「返回最新」后回到 167。 |
| P3-5 | 地图只有"程序自动适应视野"，**用户没法缩放**，个别台风的细节看不清楚 | **已修复**：`ui.js` 把视野拆成两层——`FIT`（`fitBox()` 自动适应）⊕ `ZOOM`（`{k,px,py}` 用户操作），由 `composeView()` 合成：`s = b.s·k`、`t = b.t·k + p`。**关键点是"折叠"而不是"再套一层 transform"**，否则锚点公式、经纬网反投影、HTML 浮层定位要各维护一套坐标换算。新增浮动缩放控件（`+` / `−` / 百分比 / 重置）叠在画布右上角，并接管**滚轮（按光标位置缩放，公式 `p′ = pu − c − (k′/k)·(pu − c − p)`，先夹倍率再算平移）**与**按住拖拽平移**；缩放后合成比例尺夹在 15%–600%。`syncView()` 原地更新 **5 处**（矢量层 `.map-view-g`、底图 `<img>` CSS 变换、经纬网内容、提示条、`data-view`/`data-zoom`），不重建 DOM，因此不会冲掉底图与图层开关的勾选状态；经纬网图层改为**恒在 DOM、恒等视野时 `display:none`**，缩放时就地显示并重算。**实测**：`tools/probe-overview-focus.cjs` **36/36 通过**（其中缩放相关 14 项）——「＋」`s` ×1.25、滚轮向下缩小、拖拽只改 `tx` 不改 `s`、重置后与该台风"未缩放时"逐值一致，且每一步路径都仍 100% 落在画布内；`tools/probe-map-paths.cjs` P1 可见面积 223834 = 自身面积、19 条经纬网线；`tools/zfz-mockfree-verify.cjs` **137 通过 / 0 失败**。 |
| P3-6 | 热部署改错目录：当前 Tomcat 由 **IntelliJ SmartTomcat** 启动，docBase 是 `IdeaProjects/typhoon_platform/src/main/webapp`，不是 `zfz-runtime/.../webapps/typhoon_platform` | **已记录（非代码缺陷，是操作陷阱）**：改完前端先 `curl` 一次线上资源确认内容真的换了，别只看 `cp` 成功。判据：`curl -s --noproxy '*' http://localhost:8080/typhoon_platform/js/ui.js \| grep -c '<新写的标识符>'`。SmartTomcat 的 docBase 写在 `C:\Users\17168\.SmartTomcat\typhoon_platform\typhoon-platform\conf\Catalina\localhost\typhoon_platform.xml`，启动模式一变（SmartTomcat ↔ `catalina.bat run`）部署目录就跟着变。**本轮踩坑经过**：先 `cp` 到 `zfz-runtime/.../webapps/typhoon_platform` 并以为成功，探针却报「比例尺没变、`data-zoom` 读不到」——实际是线上仍是旧 `ui.js`，白查了一轮。 |
| P3-7 | 视野自适应虽让远洋台风"看得见"，但**三张设计稿底图只覆盖 105°E—135°E，缩到 45% 后底图只剩角落里的一小块、其余全是空海面**，地图看起来像坏了 | **已修复**：改为**双层底图**。新增 `tools/gen-wpac-coast.py`，用 Natural Earth 1:50m 陆地在**画布像素坐标系**里生成 `js/geo-wpac.js`（473 环 / 14982 点 / 79KB，覆盖 79°E—198°E），垫在设计稿底图**之下**：105°E—135°E 内被设计稿底图的实心矩形完全盖住（**视觉与设计稿逐像素一致**），之外由真实海岸线接管——于是任何缩放比例下画面上都是一张完整的西太平洋地图，日本、菲律宾、太平洋岛屿都在。海面底色与陆地填充随 `[data-basemap]` 切换（`app.css`），切「卫星云图 / 地形图」时远洋层同步变色；设计稿底图加四边羽化（`mask-image` + `mask-composite: intersect`，**仅 `[data-view]` 存在时启用**）消除两层交界的硬分界线；path 片段缓存进 `OCEAN_INNER`，避免每次重建地图重拼 79KB 字符串。<br>**排查中修掉三个几何错误**：① 用"逐点过滤 + `Z` 闭合"裁剪跨边界大陆，海里凭空多出斜三角 → 改 Sutherland-Hodgman；② "凡 `<79°E` 的经度就 `+360`"会把欧亚大陆（西起欧洲、东至白令海峡）扯出一条横贯全图的假边 → 改逐环 `unwrap()` 展开 + 整环平移 360° 的整数倍；③ 同一多边形的外环与孔洞拆成不同 `<path>`，`fill-rule="evenodd"` 不跨 path 生效，里海 / 黑海会被填成陆地 → 合并为一个 path。<br>**实测**：`_coast-preview.html` 的叠加面板证明新岸线与设计稿底图**逐点对齐**（朝鲜半岛、日本列岛、台湾、菲律宾全部重合）；截图 `map-ocean-auto / zoomin / sat / terrain / seam` 覆盖自动适应、放大、三套底图风格与两层交界；`probe-overview-focus.cjs` **36/36**、`probe-map-paths.cjs` 路径 100% 入画、`probe-runtime-errors.cjs` 11 页零控制台错误、`zfz-mockfree-verify.cjs` **137/137**。<br>**⚠️ 本行的"双层底图"方案已被 P3-8 取代**——底图现改为只有一层真实海岸线，设计稿底图与四边羽化规则均已删除。 |
| P3-8 | 经纬网 / 云图 / 地名三类信息**只有底图覆盖的那一小块（105°E—135°E）里有**，台风一跑到远洋就三样同时消失；且双层底图（设计稿位图 + 远洋矢量）在缩小时交界处纹理对不齐，一眼就是"拼的" | **已修复**：**废弃设计稿底图，底图只保留一层，完全由真实海岸线绘制**。`tools/gen-wpac-coast.py` 改为参数化范围并从画布像素坐标系生成，覆盖扩到 `pad:[-1600,-1600,3600,2200]`（约 **45°E—239°E**，748 环 / 19580 点 / 104KB），三套底图（普通 / 卫星 / 地形）**共用同一份几何**、只换 CSS 配色（`--map-ocean/land/coast` × `-sat` / `-ter`）。在此基础上把三类信息全部抽成独立图层、挂在底图之上：<br>① **经纬网**由"只在缩放时出现"改为**恒定显示**，步长自适应（`GRAT_STEPS=[1,2,5,10,20]`，取可见跨度内线数 ≤10 的第一档；放大到 212% 时自动细到 2°）；<br>② **地名体系** `PLACES` 108 条（海域 14 / 国家与地区 21 / 中国省区 29 / 城市 44，全部给经纬度走 `proj()` 投影），按比例尺三级 LOD（`data-lod`：缩到 28% 只剩海域 + 国家，放大后逐级冒出省与城市）；字号/圆点用 `--lbl-k = 1/s` **反缩放**（屏幕字号恒定，实测缩到 28% 时 `font-size` 31.59px 不变）；新增动态比例尺 `.map-scale`（取屏幕 ≥70px 的最小整距离，`≈500 km` → `≈200 km` → `≈1000 km` 随缩放重算）；地名表述合规化（台湾地区 / 香港特别行政区 / 澳门特别行政区 / 南海诸岛）；<br>③ **云图**抽成独立图层 `.map-cloud-layer`，**仅 `[data-basemap="sat"]` 显示**（普通 / 地形底图下整层 `display:none`）；`feTurbulence` + `feColorMatrix`（斜率 `k=2.00/c=-2.85`）压出云带、`radialGradient` 柔边云团，随机数走线性同余 `lcg(20261014)` **保证确定性**（否则每次重建换一张云、刷新一次变一次），滤镜与渐变放独立 `<svg class="map-defs">` 作用域（不塞进隐藏图层）；图例如实标注「**云系示意（非观测产品）**」——本项目数据全真不造数，云图是唯一例外，靠这句把边界划清楚。<br>**性能**：切到卫星底图并稳定两帧仅 **13—18 ms**（过滤镜未按 5000×3800 逐像素栅格化）。**观感返工**：第一版云图 `opacity:.46` + 云团 `0.25+rnd()*0.5` 过浓，截图整张灰白、海岸线与地名全糊，改 `opacity:.55` + 云团 `0.16+rnd()*0.34` 后云形清晰、地名可读。<br>**实测**：`tools/shot-map-look.cjs` 逐套底图核过（`landPaths=748`、云图仅 sat `cloudShown=true`、`places={sea:14,country:21,prov:29,city:44}`、`scaleText` 随缩放变化）；`probe-overview-focus.cjs` **37/37**、`probe-map-paths.cjs` P1 三条线 100% 入画（`basemapTransform` 有值、`graticuleLines=15`）、`probe-runtime-errors.cjs` 11 页零控制台错误、`zfz-mockfree-verify.cjs` **140/140**、`mvn -B test` **938/0/1**。 |
| P3-9 | 城市风险预评估页（P4）顶部「城市 / 关联台风 / 评估基准」是**只读文字**，只能被动接受 `analyzeTarget` 自动挑中的台风；且「评估基准」显示的是**浏览器当前时间**，是假数据 | **已修复**：三个字段改为 `U.fieldSelect` 可操作下拉，选完即重算。<br>**① 关联台风**：活跃台风置顶 + 历史档案在后，各成一组；历史台风也能评估（回溯复盘本就需要）。用户一旦手动选过就不再自动改选（`state.manual`）。<br>**② 城市**：默认「全部城市（按风险排序）」，主卡聚焦风险最高者；选中某城市后主卡聚焦它，侧栏「距离测算」仍列全部城市作对比。全量数据已在手，换城市**零额外请求**；只有选中城市超出评估半径时才补一次单城市请求，让"距离较远、暂不构成风险"这句话**由后端给出**而非前端自编。<br>**③ 评估基准**：下拉的每一项是该台风的一个**真实观测时次**（来自 `typhoon_tracks`），换时次即用**当时的中心位置 + 当时的风圈**重算。基准时刻由服务端随结果回传（`RiskAssessmentVO.baseTime`），页面只负责显示。<br>**后端改动**：`TyphoonService.trackAt` / `WindCircleService.at`（均取"不晚于该时刻的最近一条/一组"）；`RiskAssessmentService` 增加带 `obsTime` 的重载，原方法委托新方法保持签名兼容；`GET` 支持 `obsTime` 参数；`risk_assessments` 由 `db/migration_risk_obs_time.sql`（幂等）新增 `obs_time` 列，`POST` 落库时记基准时刻——该表存在的意义就是"防灾复盘时要能回答『登陆前 24 小时平台给的是什么结论』"，按历史时次评估却不留存基准时刻，这个意图就落空了。DAO 会探测列是否存在，**未跑迁移的旧库不报错**。<br>**踩坑一（一致性缺陷）**：风圈表并非每时次都有数据（多数台风整场只存一组，如 `typhoon_id=1` 仅 `2024-09-05 17:00` 一组而轨迹有 3 点）。"跟随最新"走 `findLatest` 拿到它、"锁定最新时次"走精确匹配却拿不到 → **同一时刻给出两个不同评分**（67.73 vs 74.79）。修法：`at()` 精确匹配不到时回退到"不晚于该时刻的最近一组"。修完后「跟随最新」与「锁定最新时次」结果逐值一致。<br>**踩坑二（下拉分组碎裂）**：`fieldSelect` 是"遇到 `group` 变化就开新 `<optgroup>`"，按接口原顺序 `push` 导致活跃/历史交错时被切成 **5 段**（`历史档案:1/活跃台风:1/历史档案:4/活跃台风:1/历史档案:37`）。修法：**先分桶再 `concat`**，恒定产出 2 段。不要假设接口已排好序。<br>**踩坑三（时间格式不一致）**：结果卡是后端原样串 `2026-09-15 08:00:00`，而 ETA / 时段 / 下拉选项都是短格式 `09-15 08:00`，同一页两种写法 → 统一走 `shortTime()`。<br>**踩坑四（测试契约漂移）**：`assess()` 改走 `at()` 后，测试仍 stub `latest()`，Mockito 严格模式下 6 个用例报 `UnnecessaryStubbing`（14 error + 1 failure，失败表现为风力断言 `10 级` vs 实际 `13 级`——因为 `at()` 返回 null 后退化为按中心风力衰减）。修法：stub 改为 `at(anyLong(), any())`。<br>**另**：「开始评估」按钮改为真正的「**存档本次评估**」（调 `POST` 把结果写入 `risk_assessments`），此前该按钮只做了重算，存档能力是断的。<br>**实测**：新增 `tools/probe-risk-filters.cjs` **14/14**（三下拉为 `<select>`、分组恒为 2 段、换城市主卡聚焦、换基准后结果卡基准时刻随之改变、条件卡未被重建、未登录存档走 401 提示、零控制台错误）；`curl` 验证重演有效（跟随最新 67.73 → 锁定 09-05 17:00 得到 6 个城市且集合完全不同）；登录后 `POST` 实测 `obs_time=2026-09-15 08:00:00` 已落库（历史记录为 `NULL`，前端显示「—」）；`zfz-mockfree-verify.cjs` **140/140**、`probe-overview-focus.cjs` **37/37**、`probe-runtime-errors.cjs` 零错误；`mvn -B test` **944/0/1**（938 基线 + 新增 6 例：`at()` 四条边界、`assess(obsTime)` 两条）。 |
| P3-10 | 地图可以拖到**没有地形数据**的地方：一旦视野越过 DEM 覆盖范围（92°E—178°E / 4°N—52°N），画面上就出现一个巨大的亮暗矩形——一侧有地形晕渲与分层设色，另一侧只剩一层平涂的陆地底色，直角边笔直贯穿整张图（用户反馈原话：「这些空白的地方怎么办？这会有很强的割裂感」） | **已修复**：把视野夹进有数据的范围，而不是靠「补数据」掩盖。`ui.js` 新增 `BOUND`（= DEM 的 92°E / 4°N / 52°N + 画布右缘 180°E）与 `clampPan()`，由 `composeView()` 统一调用——三个渲染入口（首屏、总览、沙盘）都走它，不必逐处写。夹的是 `ZOOM.px/py` **本身**而非合成时的返回值，否则越界增量会一直累加，用户拖到边界再往回拖时地图会黏住不动。<br>同时把 `ZOOM_MIN` 由 0.15 提到 **1**：100% 时整个台风区刚好装满画布，再缩小必然离开 DEM。「−」因此在该状态下点不动，`syncView()` 新增 `syncZoomButtons()` 用 `disabled` **置灰**（CSS 里连 hover / active 一起吃回去）——按钮点了没反应，本身就是「坏了」的观感。<br>**例外**：自动适应选定的位置永远可达（`clampPan()` 把区间放宽到至少包含 `FIT` 的平移量），否则某个台风万一跑到 100—180°E 之外，`fitBox()` 为纳入它而做的平移会被夹回去，路径反被裁掉。<br>**排查过程**（值得留证据）：先按用户截图量出直角边的位置 → 换算成经纬度正是 92°E / 52°N → 确认亮区 = DEM 覆盖范围；再在 1920 视口下用 CDP 模拟向西拖拽复现。顺带发现用户那次操作是**在 100% 缩放下把地图拖到了 64°E / 69°N**，早已离开数据很远。<br>**实测**：新增 `tools/probe-map-bounds.cjs` **17/17**（四向拖到底分别停在 92°E / 180°E / 52°N / 4°N、任意视野下地形位图都盖住画布、「−」的置灰时机、重置回恒等视野）；`zfz-mockfree-verify` **141/141**、`probe-terrain-map` **32/32**、`probe-overview-focus` **37/37**、`probe-map-paths` 沙盘两条线 `visibleArea === ownArea`、6 页控制台零错误；`shot-map-look.cjs` 的缩小步骤改为断言「缩不动」（恒 100%），并把平移步骤改成**向西**（当初踩雷的方向）。 |

> 课程硬性要求（阶段四④ ECharts、阶段七① Excel 导出、阶段七④ Git 仓库）现已全部落地，
> 详见 `README.md` 课程对照表与 `code-review.md` 对应小节。
>
> 本轮同时修正了一处**文档与事实不符**：此前在本文写下"已 `git init`"时并未真正执行，
> 现已实际初始化并提交（`git log` 可见），文档声明前先落地，避免再次出现此类偏差。

### P2-10 更正（2026-10-11 复核）

**结论：交付目录确实已清爽，但"可恢复"的归档路径不存在，旧副本实为永久删除。**

复核方式与结果：

| 检查项 | 结果 |
|---|---|
| 交付目录全树（6 层）搜 `zhuifengzhe*` / `设计稿*` / `设计图*` | **0 命中** —— 交付目录确是干净的 |
| `C:/Users/17168/Desktop/web课程项目_旧副本归档/`（本文原记的归档位置） | **不存在** |
| `C:/Users/17168` 下 4 层内搜 `zhuifengzhe*` | **0 命中** |
| `D:\` 下 4 层内搜 `zhuifengzhe*` | **0 命中** |
| C: 与 D: 回收站 `$I` 元数据（共 1151 条）按路径搜 `zhuifengzhe` / `设计稿` / `旧副本` | **0 命中**（回收站里只有 `target/` 构建产物） |

因此：

- 原句"**已移出交付目录至 `…_旧副本归档/`（可恢复，非删除）**"中，
  "已移出"这半句与实际一致（交付目录确实没有它了），
  但"归档到某目录、可恢复"**不成立** —— 该目录从未存在过（或已被再次删除），
  且两个盘的回收站都没有它的痕迹。**不要再把它当成可恢复的备份。**
- 好消息是**没有实际损失**：前端代码在权威位置
  `typhoon_platform/src/main/webapp/` 完整存在（10 个 HTML + `css/` `js/` `assets/` `vendor/`），
  该副本本就只是开发期的临时存底。
- 教训即本文上一段自己写过的那条：**"声明前先落地"**。
  本次是反向情况（记录了未真正发生的"归档"动作），
  同样属于文档与事实不符 —— 涉及"文件去哪了"的记录，
  **必须写核查命令与结果，而不是只写结论**。

