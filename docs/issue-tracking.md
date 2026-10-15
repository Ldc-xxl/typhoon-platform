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
| P2-10 | 父目录 `web课程项目` 下存在旧前端 `zhuifengzhe-web/` 与设计稿 `设计稿导出/`，评审易误认为"两份前端" | **已移出交付目录**至 `C:/Users/17168/Desktop/web课程项目_旧副本归档/`（可恢复，非删除）。交付目录 `web课程项目` 现仅含 `typhoon_platform`（交付物）+ 课程要求文档（pptx/docx）+ 数据库 dump。 |
| P2-1 | `sql/` 目录（仅 `.gitkeep` + 指向 `db/` 的 README）与 `db/typhoon_monitor.sql` 重复，易误判"两份建库脚本" | **已删除 `sql/`**（`README.md` 目录树同步移除该条目），唯一建库脚本即 `db/typhoon_monitor.sql`。 |
| P2-3 | 导出仅支持 CSV，无 Excel（课程要求阶段七①「数据导出 Excel」） | **已修复**：`ExportServlet`/`ExportServiceImpl` 新增 `format=xlsx`，`util/XlsxUtil` 手写 OOXML 生成真实 `.xlsx`（不引 POI）；轨迹页与档案页均提供「导出 Excel」按钮。 |
| P2-4 | 统计图未接入 ECharts（课程要求阶段四④「Bootstrap + AJAX + ECharts」） | **已修复**：`admin.html` 经 CDN 引入 ECharts 5.5.1，新增 `GET /api/admin/stats` 提供用户累计增长 + 角色分布数据，`pages/admin.js` 用折线图与饼图渲染。 |
| 阶段七④ | Git 版本管理（加分项） | **已 `git init`** 初始化仓库，`.gitignore` 已排除含口令的 `db.properties` 与 `target/`；首次提交见仓库日志。 |
| P1-4 | 登录失败锁定是单 JVM 内存实现，Tomcat 重启即清零（安全防护加分项待补） | **已修复**：新增 `login_attempts` 表（第 27 张表）持久化失败计数与锁定截止时间；`INSERT ... ON DUPLICATE KEY UPDATE` 避免并发丢计数；表缺失时自动降级内存，**不影响登录**。已有库需执行 `db/migration_login_attempts.sql`（幂等）。 |
| P2-6 | 量纲两套并存（0—1 与 0—100），只靠注释约定，历史上出过 100 倍偏差 | **已缓解**：`DisagreementVO.dimensions[].variance`、`ModelConfigVO.confidence` 补齐量纲 JavaDoc；`docs/api.md` 新增 **1.4 量纲总表**，固化唯一口径并点明 `models[].confidence`（0—100）与融合 `confidence`（0—1）同名不同量纲的坑。 |
| P2-8 | 前端 JSDoc 覆盖不均，课程要求"JS 需有完整 JSDoc"仅部分满足 | **已修复**：`overview.js`（原 0 注释 / 30 函数）与 `help.js`（原 0 注释 / 7 函数）全量补齐；另补 `archive.js` / `detail.js` 的 `doExport`。现 `pages/*.js` **全部函数均有 JSDoc**（脚本扫描 0 遗漏），10 个文件 `node --check` 语法全通过。 |

> 课程硬性要求（阶段四④ ECharts、阶段七① Excel 导出、阶段七④ Git 仓库）现已全部落地，
> 详见 `README.md` 课程对照表与 `code-review.md` 对应小节。
>
> 本轮同时修正了一处**文档与事实不符**：此前在本文写下"已 `git init`"时并未真正执行，
> 现已实际初始化并提交（`git log` 可见），文档声明前先落地，避免再次出现此类偏差。
