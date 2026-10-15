# 追风者 · 代码审查问题清单

> 审查对象：`typhoon_platform` 全量前后端代码（196 个类 / 约 3.2 万行 + 10 个页面）
> 审查时间：2026-09-30
> 审查方式：静态阅读 + 实际部署运行验证 + 单元测试
> （后端 112 条接口用例、前端 134 条浏览器断言、845 条 JUnit 用例 / 行覆盖率 77.4%）
>
> **2026-09-30 第二轮更新**：P0-1（单元测试）与 P1-3 / P1-5 / P1-6 已修复，
> 详见 [`test-report.md`](test-report.md)。本轮补测试过程中又发现并修复了 4 个真实缺陷
> （见第六节末尾「本轮新增修复」）。
>
> **2026-10-11 第三轮**：独立复核又发现 15 个本文档未覆盖的问题（含 3 个 P0：
> 登录锁定计数非原子、事务 API 是死代码、沙盘公开接口参数名前后端不一致），
> 见 [`code-review-round3.md`](code-review-round3.md)。本文档剩余未处理项
> （P2-6 / P2-7 / P2-10）在新文档末尾一并复核。
>
> 级别定义：
> - **P0** 阻断交付 / 安全事故 / 课程硬性要求未达标
> - **P1** 功能或健壮性缺陷，用户可感知
> - **P2** 可维护性、一致性隐患，短期不出错但会持续制造成本

---

## 一、P0 · 必须处理

### P0-1 ✅ 已修复（第二轮） · 单元测试完全缺失，覆盖率 0（课程硬性要求未达标）

- **实测结果**：**845 个用例 / 0 失败 / 行覆盖率 77.4%**（6569 / 8487），
  分支 59.0%、方法 88.1%、类 91.8%。明细与踩坑记录见 [`test-report.md`](test-report.md)
- **位置**：`src/test/java/com/zfz/typhoon/` 下只有 4 个 `.gitkeep` 占位文件，**没有任何 `.java` 测试类**
- **现状**：`pom.xml` 已配好 JUnit 5.10.1 + Mockito 5.7.0 + JaCoCo 0.8.12，
  但因为没有测试，`mvn test` 跑不出任何覆盖率数据
- **影响**：课程要求「JUnit 5 + Mockito 单元测试，覆盖率 ≥ 60%」这一项是空的。
  同时意味着重构没有安全网——改一处业务逻辑只能靠手工点页面来验证
- **为什么之前没写**：Service 实现类原先把依赖写死在字段初始化里
  （`private final XxxDao dao = new XxxDaoImpl();`），无法注入替身，
  写单元测试就必然连真实数据库，测试退化成集成测试。
  **这个障碍已经清除**：22 个 Service 实现类都已具备「无参构造 + 带参构造」两个构造口，
  生产行为零变化，测试可用 Mockito 注入替身
- **建议**：按「先易后难」补测，优先能纯静态验证的工具类，再补有分支的业务类

  | 优先级 | 目标 | 测什么 |
  |---|---|---|
  | 1 | `util` 包静态方法 | `GeoUtil`（Haversine 距离、方位角、象限）、`CsvUtil`（转义/换行/文件名）、`DateUtil`（解析/格式化/跨时区）、`PasswordUtil`（加盐与不加盐两种校验）、`SqlBuilder` |
  | 2 | `AuthServiceImpl` | 口令错误达 5 次锁定、锁定 15 分钟后解锁、`verify` 对 `sha256$` 前缀与无前缀两种存储格式的兼容 |
  | 3 | `SurveyServiceImpl` | 加权汇总算法、分母为 0 的边界 |
  | 4 | `RiskAssessmentServiceImpl` | 加权评分、量纲（0—100） |
  | 5 | `ExportServiceImpl` | CSV 生成的列顺序、含逗号/引号/换行的字段转义 |

  Java 8 与 Mockito 5.7 存在兼容问题（Mockito 5 要求 Java 11+ 字节码），
  若在本机 JDK 25 上跑可直接用；若要在 JDK 8 上跑，需把 Mockito 降到 4.x。

### P0-2 ✅ 已修复（本次） · MySQL root 明文口令差点随版本库外流

- **位置**：`src/main/resources/db.properties` 第 30 行 `jdbc.password=Ldc037+.`
- **配套风险**：`.gitignore` 原先只忽略了 `target/`、IDE 目录、`*.class`、`*.log`，**没有排除 `db.properties`**
- **影响**：
  1. 只要执行 `git add .` 并推送，数据库**最高权限账号**的口令就进了版本库；
     若仓库是公开的（或日后开源），等同于直接泄露
  2. 该口令一旦进过 Git 历史，事后删除文件也无法消除历史记录
  3. 交付物（课程提交包）里也会带上真实口令
- **为什么这是真问题而不是"教学项目无所谓"**：课程评分标准里"安全防护"是加分项，
  而明文 root 口令是安全审查里最典型的一类扣分理由，往往比功能缺陷更扎眼
- **已做的修复**：
  1. `.gitignore` 增加 `src/main/resources/db.properties`，
     并在注释里写明"若曾提交过，还需 `git rm --cached` 并轮换口令"
  2. 新增入库模板 `src/main/resources/db.properties.example`：
     不含真实口令（占位 `__请填写本机 MySQL 口令__`），
     并说明"复制一份改名为 `db.properties`"的部署步骤
  3. 模板中一并记录了两条更稳的做法：
     - `DBUtil` 读取配置时**优先取系统属性**，因此可改用
       `-Djdbc.password=xxx`（Tomcat 的 `bin/setenv.bat`）注入，磁盘上不留明文
     - 为应用单独建一个只对 `typhoon_monitor` 库有增删改查权限的账号，不用 `root`
- **⚠️ 仍需你确认的事**：如果这个工程**此前已经执行过 `git add` / `git commit`**，
  仅添加忽略规则是不够的 —— 历史提交里仍然留着口令。
  这种情况需要执行 `git rm --cached src/main/resources/db.properties` 后
  **更换 MySQL 口令**，旧口令即可作废

---

## 二、P1 · 用户可感知的缺陷

### P1-1 ✅ 已修复 · 顶栏把"任何请求失败"都显示成「未登录」

- **位置**：`src/main/webapp/js/layout.js` 的 `loadSession()`
- **原代码**：

  ```js
  ZFZ.api.getMe().then(function (me) { ... },
    function () {                       // ← 回调连 err 参数都没接
      session = null;
      paintUser({ userName: '未登录', avatarText: '访', ... });
    });
  ```

- **问题**：`/api/auth/me` 失败的原因不止一种——401 是"确实没登录"的正常表达，
  而超时、连接池繁忙、服务端 5xx 是**故障**。原实现把两者合并成同一句「未登录」，
  于是**用户明明登录着，顶栏却写着未登录**，被误导去重新登录，而真正的问题（网络异常）被掩盖
- **同文件内的反证**：同一个 `layout.js` 里的 `renderNotice()` 却正确使用
  `isUnauthorized(err)` 区分了 401 与其它错误 —— 说明这处是**遗漏**而非设计
- **已做的修复**：
  - 引入 `sessionState`（`unknown / user / guest / error`）四态，
    **只有 401 才画「未登录」**，其它失败画「连接异常 / 身份校验未完成，请刷新重试」
  - 非 401 首次失败时**自动重试一次**（延迟 600ms），消除页面刚加载时的偶发抖动
- **复现与验证**：实测偶发复现（同一套代码连跑 4 次里失败 2 次），
  定位过程中拿到过硬证据：失败时 `fetch('api/auth/me')` 直接返回 200、
  且 `Network.getAllCookies` 里 `JSESSIONID@127.0.0.1/typhoon_platform` 确实存在，
  而页面却画成未登录 —— 证实是**前端渲染问题，与后端会话无关**

### P1-2 ✅ 已修复 · 登录成功后顶栏不刷新

- **位置**：`src/main/webapp/js/pages/admin.js` 的登录成功回调
- **问题**：顶栏身份是在页面加载时那一次 `/api/auth/me` 画上去的，那时还没登录。
  登录成功后只调 `boot()` 重新加载后台数据，**没有刷新顶栏**，
  用户会看到"已进入管理后台、顶栏却写着未登录"的矛盾画面，只能手动刷页面
- **已做的修复**：`layout.js` 新增并导出 `refreshSession()` / `sessionStatus()`；
  `admin.js` 登录成功后调用 `ZFZ.layout.refreshSession()`

### P1-3 ✅ 已修复（第二轮） · `refreshUnread()` 存在与 P1-1 相同的病根

- **位置**：`src/main/webapp/js/layout.js` 的 `refreshUnread()`
- **代码**：

  ```js
  ZFZ.api.getUnreadCount().then(function (res) { ... 按 unread 设置红点 },
    function () {
      badge.classList.add('is-cleared');   /* 未登录或异常：不显示红点 */
    });
  ```

- **问题**：与 P1-1 同源——任何失败都被当作"没有未读"。后果是
  **通知接口挂掉时的表现，与"确实没有未读通知"完全一样**，
  属于静默降级：坏了也没人知道
- **为何仍列为 P1 而非 P2**：它掩盖的是"通知不可用"这一事实，
  而通知是这个平台最重要的时效性功能
- **已做的修复**（与 P1-1 同源，一次性处理）：
  - 引入三态：401 → 未登录（隐藏红点，属正常）；
    其它失败 → **接口异常**（红点照旧隐藏，但通知面板顶部显示
    「未读数量获取失败」+ 重试按钮，不再假装没有未读）；成功 → 按 `unread` 画红点
  - 非 401 首次失败自动重试一次（延迟 600ms），与 `loadSession()` 口径一致
  - 新增 `app.css` 的 `.dot-badge.is-error` 样式（灰色感叹号态），
    与"真的有未读"的红色实心点在视觉上可区分

### P1-4 ✅ 已修复（第四轮）· 登录失败锁定落库持久化

- **位置**：`service/impl/AuthServiceImpl.java`、`dao/LoginAttemptDao.java` + `dao/impl/LoginAttemptDaoImpl.java`、`model/LoginAttempt.java`
- **原问题**：计数存在**单个 JVM 的内存 Map 里**——Tomcat 一重启锁定记录全部清零，
  攻击者只要等（或触发）重启就能继续爆破；将来多实例部署还会每个实例各算各的。
- **修复方式**：新增 **`login_attempts` 表**（第 27 张表），失败计数与锁定截止时间
  写入数据库，`AuthServiceImpl` 优先读写该表。
  - 写入用 `INSERT ... ON DUPLICATE KEY UPDATE`，避免"先查后写"的并发窗口
    （并发下两次都判定"无记录"会导致计数被静默丢弃，防护等于失效）；
  - 主键是 `username` 且**刻意不加外键**：对不存在的账号也要能记失败，
    否则攻击者用随机账号探测不会被锁；
  - 表缺失或库异常时**自动降级**为原内存计数，登录功能不受影响
    （锁定是辅助防护，绝不能因为它导致正常用户登不进去）。
- **迁移**：已有库执行 `db/migration_login_attempts.sql`（幂等）；
  主脚本 `db/typhoon_monitor.sql` 也已包含该表（27 张表）。
  ⚠️ 主脚本含 `DROP TABLE`，已有库**不要重跑**主脚本来加这张表。
- **验证**：`AuthServiceImplTest` 新增「数据库持久化路径」7 个用例，
  含锁定中拒绝 / 过期放行 / 计数递增 / 达阈值锁定 / 成功清除 / **表缺失降级** 六类行为。
- **仍存在的边界**（答辩若被问到要知道）：多实例部署下仍建议换成 Redis 等集中式存储；
  当前实现在单实例下重启已不再清零。

### P1-5 ✅ 已修复（第二轮） · 会话超时在两处各写一遍

- **位置**：
  - `src/main/webapp/WEB-INF/web.xml` 第 38 行：`<session-timeout>30</session-timeout>`（分钟）
  - `src/main/java/com/zfz/typhoon/service/impl/AuthServiceImpl.java`：
    `SESSION_TIMEOUT_SECONDS = 30 * 60`（秒），注释写"与 web.xml 的 session-timeout 保持一致"
- **问题**：同一个数值两处维护，且"保持一致"是靠**人工约定**保证的，代码里没有任何机制
  阻止两者漂移。改了一处漏了另一处，就会出现「接口告诉前端会话 30 分钟、
  容器实际 20 分钟就踢掉」这类难查的问题
- **附带**：`/api/auth/me` 返回的 `sessionTimeout: 1800` 取自这个常量，
  一旦漂移，前端拿到的就是错的
- **已做的修复**：**容器配置成为唯一来源**。
  - `AuthServlet` 删掉 `session.setMaxInactiveInterval(...)`（容器已按 `web.xml` 设好，
    代码里再设一遍就是第二个真相源）
  - `AuthServiceImpl` 删掉 `SESSION_TIMEOUT_SECONDS` 常量与其所有引用，
    `/api/auth/me` 改为运行时读 `HttpSession.getMaxInactiveInterval()` 的真实值 ——
    容器改成 20 分钟，接口就返回 1200，不可能再漂移

### P1-6 ✅ 已修复（第二轮） · `lastLogin` 字段名与语义不符

- **位置**：
  - DDL：`users` 表**没有 `last_login` 列**（实际列为
    `user_id / username / password_hash / email / phone / nickname / role / status / created_at / updated_at`）
  - `model/vo/UserVO.java:100`：`LocalDateTime last = lastLogin != null ? lastLogin : user.getUpdatedAt();`
- **问题**：接口对外返回 `lastLogin`（例：`"lastLogin":"2026-09-01 09:00:00"`），
  但实现是拿 `updated_at` 兜底。于是这个字段的真实含义是
  **"该用户行最后一次被修改的时间"**，而不是"最后登录时间"。
  用户改个头像、被管理员停用一次，这个"最后登录时间"就变了
- **代码是诚实的**：`UserServiceImpl` 第 20—26 行、`AuthServiceImpl` 第 129 行
  都明确写了"users 表没有 last_login 列，用 updated_at 兜底"，
  并建议"应新增 `last_login datetime NULL` 列并在登录成功后写入" —— 说明这是**已知的临时方案**
- **已做的修复**：按注释说的补齐。
  - DDL：`db/typhoon_monitor.sql` 与现网 3306 库均新增 `last_login datetime NULL`
  - 实体 / DAO：`User` 加 `lastLogin` 字段，`UserDao` 加 `updateLastLogin(long, LocalDateTime)`，
    `UserDaoImpl` 实现对应 `UPDATE users SET last_login = ? WHERE user_id = ?`
  - 写入点：`AuthServiceImpl.login()` 校验通过后调用
  - 读取：`UserVO` 不再用 `updatedAt` 兜底，改为真实读取 `last_login`，
    未登录过的用户该字段为 `null`（前端显示「—」，比"被改动过的时间"诚实）

---

## 三、P2 · 可维护性与一致性隐患

### P2-1 建库脚本位置与文档口径不一致

- **现状**：
  - 真实脚本在 `db/typhoon_monitor.sql`（26 张表 + 演示数据）
  - `sql/` 目录下只有一份说明，**没有任何可执行脚本**
  - `pom.xml` 第 12 行的注释仍写「数据库：MySQL，脚本见 `sql/` 目录」——**指向了错的地方**
- **影响**：评审/答辩时按文档去找脚本会找不到；两处目录让人以为有两套脚本
- **建议**：删掉 `sql/` 目录（或保留为指向 `db/` 的说明），
  把 `pom.xml` 注释改为 `db/typhoon_monitor.sql`

### P2-2 库名在三个地方各写一遍

- **位置**：
  1. `src/main/resources/db.properties` → `jdbc.url`
  2. `src/main/java/com/zfz/typhoon/util/DBUtil.java:123` → 兜底默认值
     `getConfig("jdbc.url", "jdbc:mysql://localhost:3306/typhoon_monitor")`
  3. `db/typhoon_monitor.sql` → `CREATE DATABASE` / `USE`
- **为什么这条值得单列**：就在本次交付前，这里**真的踩过一次**——
  数据库里实际是 `typhoon_monitor`，而 `db.properties` 写的是 `typhoon_platform`，
  结果应用启动后所有查询报 `Unknown database 'typhoon_platform'`。
  这类"配置各说各话"的故障排查成本远高于它看起来的严重程度
- **建议**：
  - `DBUtil` 的兜底默认值不要写具体库名（宁可抛"未配置"的明确异常，
    也不要猜一个库名让人误以为配置生效了）
  - 脚本里的 `CREATE DATABASE` 可以保留（它就是库名的权威来源），
    但要在 `db.properties` 顶端注明"库名必须与之逐字一致"

### P2-3 导出只支持 CSV，没有 Excel ✅ 已修复（阶段七①）

- **位置**：`src/main/java/com/zfz/typhoon/servlet/ExportServlet.java`、`service/impl/ExportServiceImpl.java`、`util/XlsxUtil.java`
- **现状**：已新增 `format=xlsx` 导出，由手写 OOXML（`XlsxUtil`）生成真实 `.xlsx`，
  **不引入 Apache POI**（满足"禁用框架"要求，零新增第三方依赖）。
  轨迹页（`detail.js`）与档案页（`archive.js`）均提供「导出 CSV / 导出 Excel」两个按钮。
- **验证**：`XlsxUtilTest` 断言产物含 `[Content_Types].xml` 等 5 个必需部件、数值单元格走 `<v>`、
  中文与 `& < >` 正确转义；`mvn verify` 全绿、覆盖率闸门通过。

### P2-4 统计图未接入 ECharts ✅ 已修复（阶段四④）

- **现状**：后台已通过 CDN 引入 ECharts 5.5.1（`admin.html` 中
  `<script src="https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js">`），
  并由 `GET /api/admin/stats` 提供数据：用户累计增长折线 + 角色分布饼图
  （`pages/admin.js` 的 `renderCharts`）。离线部署时把 `echarts.min.js` 放入 `vendor/` 并改用本地路径即可。
- **验证**：链路 `UserDao#countByCreatedDate` → `UserService#growthTrend` →
  `AdminService#stats` → `AdminServlet#stats` → `api.js#getAdminStats` → ECharts；
  `AdminServiceImplTest.stats` / `UserServiceImplTest.growthTrend` 覆盖聚合逻辑。

### P2-5 `SqlBuilder.orderBy()` 接受任意字符串（当前安全，但 API 形状危险）

- **位置**：`src/main/java/com/zfz/typhoon/util/SqlBuilder.java:273`

  ```java
  public SqlBuilder orderBy(String orderByClause) {
      sql.append(" ORDER BY ").append(orderByClause);   // 直接拼字符串
  }
  ```

- **当前是否可被注入：否**。全项目只有一处动态传参 ——
  `TyphoonDaoImpl:270` 的 `resolveOrderBy(q.getSort())`，
  而该方法用**白名单**（`time_desc / time_asc / power_desc / name`）
  把用户输入映射成写死的 SQL 常量，未命中一律回落到默认排序。这个写法是正确的
- **为什么仍要提**：`orderBy(String)` 这个签名本身没有任何防护，
  形状上等同于"欢迎把请求参数传进来"。将来有人新增一个
  `sb.orderBy(req.getParameter("sort"))` 就是货真价实的 SQL 注入，
  而这类改动在代码评审里很容易被当成正常写法放过
- **建议**：把 `orderBy` 的入参收窄为枚举或"列名白名单 + 方向"两个参数，
  从 API 层面让危险写法无法表达

### P2-6 量纲两套并存，只靠注释约定

- **约定**（分散在多个类的注释里）：

  | 0—100 | 0—1 |
  |---|---|
  | `survey_agg_results.*`、`riskScore`、`consistency`、`models[].confidence` | `similarity_score`、`disagreement.index`、`variance`、融合后的 `AiPredictionVO.confidence` |

- **历史代价**：联调期因此出过 **100 倍偏差**（见 `docs/issue-tracking.md` P1-2 / P1-3 / P1-4），
  根因是后端按 0—1 返回、页面直接拼 `%`
- **风险仍在**：约定没有被代码强制。新写一个接口，返回 0—1 还是 0—100 全凭记忆
- **建议**：在 VO 字段上统一加 JavaDoc 标注量纲（部分已有），
  并在 `docs/api.md` 里做一张"量纲总表"；更进一步可引入统一的比例类型或工厂方法

### P2-7 契约一致性靠人工比对，没有自动化兜底

- **现状**：`docs/api.md` 与后端实现的字段一致性，是靠人工三方比对得出的
  （方法见 `docs/issue-tracking.md` 第四节），历史上确实发现过脱节
  （P1-7 `confidence` 量纲、P1-8 `seaArea` vs `basin`、机构配色）
- **另一个问题**：验证脚本是**外部临时产物，没有落在工程里**
  （曾放在 `%TEMP%`，被系统清理过一次，脚本直接丢失），
  因此平时 `mvn package` 不会跑它们，回归全靠人记得手动执行
- **已部分解决**：两个脚本已经**重写并落盘到 `tools/`**，写进 README 的"提交前自查"：

  | 脚本 | 用例数 | 作用 |
  |---|---:|---|
  | `tools/verify_api.py` | 112 | 按 `js/api.js` 的可执行契约逐条调真实接口（仅用 Python 标准库） |
  | `tools/zfz-mockfree-verify.cjs` | 134 | 无头 Edge + CDP 巡检 10 个页面的渲染与控制台错误 |

- **仍未解决**：尚未接入 Maven 生命周期。要做到 `mvn verify` 自动跑，
  需要内嵌容器（如 `tomcat7-maven-plugin` 或 Cargo）先起服务再跑脚本，
  这会显著拉长构建时间、且引入新插件依赖。
  对课程交付而言"写进自查清单 + 一条命令可跑"已够用，**是否接入构建视答辩要求取舍**

### P2-8 前端模块的 JSDoc 覆盖不均

- **现状**：`js/api.js` 是全量 JSDoc（每个方法都有 `@param` / `@returns`），
  但 `js/pages/*.js` 里各文件的注释密度差异较大
- **影响**：课程要求"JS 需有完整 JSDoc 注释"，这一项目前是"部分满足"
- **建议**：对 10 个页面模块做一次统一的 JSDoc 补齐（至少覆盖每个导出的函数）

### P2-9 同一份 schema 存在两份可能漂移的副本

- **现状**：
  - 上级目录 `Dump20260916.sql`（原始备份）
  - `typhoon_platform/db/typhoon_monitor.sql`（本项目整理版）
- **问题**：两者都含完整 DDL + 数据。日后若只改了其中一份（比如加了 `last_login` 列，
  见 P1-6 的建议），两份就会不一致，且没有机制能发现
- **建议**：明确"以 `db/typhoon_monitor.sql` 为唯一权威"，
  在 `Dump20260916.sql` 顶部加一行说明，或直接移入归档目录

### P2-11 登录失败返回 400 而不是 401（语义可商榷，暂不改）

- **位置**：`service/impl/AuthServiceImpl.java:107`
  `throw BusinessException.badRequest("用户名或密码错误")`
- **问题**：按 HTTP 语义，"凭证无效"是 401 的典型场景，返回 400（Bad Request）偏弱。
  本项目的其它鉴权失败都用 401（`AuthFilter` 未登录、`BaseServlet.requireAdmin` 未登录）
- **为什么不改**：这是**有意的**。`js/layout.js` 的 `isUnauthorized(err)` 把
  `err.code === 401` 判定为"会话失效 / 未登录"，顶栏据此切换为未登录态。
  而"口令填错"要的是在登录表单上原地提示「口令错误」，不是把用户当陌生人重新引导。
  两者共用同一个 401 会让前端难以区分
  （注：早先此处及 `BaseServlet` 注释写作"会触发跳登录页"，与实际不符——
  项目并无独立登录页，401 只把顶栏画成"未登录"，已在第三轮复核 N9 中一并订正）
- **若要改**：需要先把语义拆开（例如 401 保留给会话失效，另引入 419 之类给凭证错误），
  属于跨前后端的契约调整，不在单点修复范围内
- **影响**：仅限语义纯度，功能与提示文案都正确。回归脚本已按 **400** 固化该契约，
  并在脚本注释里写明理由，避免后人"顺手改成 401"引入前端行为变化

### P2-10 父目录仍留有前端旧副本与中间产物

- **现状**：`web课程项目/` 下除 `typhoon_platform/` 外还有
  `zhuifengzhe-web/`（前端开发期原始目录）、`设计稿导出/` 等
- **影响**：交付时容易把旧副本一起打包，评审看到两份前端会困惑
- **建议**：确认 `typhoon_platform/src/main/webapp/` 为唯一前端来源后，
  把旧目录移出交付范围（或归档重命名）

---

## 四、本次审查中确认"没有问题"的点（避免误报）

审查时逐项排查过、结论是**安全**的，列在此处以免日后重复怀疑：

| 检查项 | 结论 |
|---|---|
| SQL 注入 | **无实际注入点**。所有查询均用 `?` 占位符；SQL 字符串拼接只出现在列名常量（`COLUMNS`）与静态片段上；唯一动态的 ORDER BY 走白名单（见 P2-5 的说明） |
| CORS 配置 | **正确**。`CorsFilter` 回显具体 `Origin` 并配 `Vary: Origin`，且使用 `Allow-Credentials: true` 时**没有**使用通配符 `Access-Control-Allow-Origin: *` —— 这是正确的写法（两者同用会被浏览器拒绝且不安全） |
| 口令存储 | **正确**。`PasswordUtil` 使用加盐 SHA-256，`verify` 兼容"加盐"与"不加盐"两种历史格式 |
| XSS | 前端有统一转义工具 `U.esc`（`ui.js:42`）并在渲染中导出使用 |
| 鉴权 | `AuthFilter` 采用**默认拒绝**策略，且数据级权限在 Service 层二次校验，层次划分正确 |
| 业务失败的错误表达 | HTTP 200 + 响应体 `code` 是有意设计，且理由充分（jQuery 的 `done`/`fail` 分支行为），非缺陷。容器级错误仍返回真实状态码（404 / 405） |
| 演示数据回退 | **已彻底移除**。`js/data.js` 物理删除，10 个 HTML 的引用全部去掉，全站 `grep ZFZ.mock` 为 0。接口失败时渲染错误态，不静默退回假数据 |

---

## 五、优先处理顺序建议

1. ~~**P0-2**~~ ✅ 已修（`db.properties` 已加忽略，模板已入库）。
   **唯一还需你确认的**：若曾 `git commit` 过，需 `git rm --cached` + 轮换口令
2. ~~**P0-1**~~ ✅ 已修（第二轮）—— 845 个用例 / 行覆盖率 **77.4%**，详见 `test-report.md`
3. ~~**P1-3 / P1-5 / P1-6**~~ ✅ 已修（第二轮）—— 详见第六节
4. **P2-1 / P2-2**（10 分钟）—— 纯文档与注释修正，`sql/` 目录已移除（见 issue-tracking P2-1），库名口径统一
5. ~~**P2-4 / P2-3**~~ ✅ 已按要求实现（ECharts 接入 + Excel 导出），见上方对应小节
6. **P2-10**（交付前必做）—— 把 `zhuifengzhe-web/` 等旧副本移出交付范围，
   否则评审看到两份前端会困惑

---

## 六、本次审查中已修复的项（汇总）

| 编号 | 问题 | 修复位置 |
|---|---|---|
| P0-2 | 明文口令未出版本库 | `.gitignore` + 新增 `db.properties.example` |
| P1-1 | 顶栏把任何失败都显示成「未登录」 | `js/layout.js`（引入 `sessionState` 四态 + 自动重试） |
| P1-2 | 登录成功后顶栏不刷新 | `js/layout.js` 导出 `refreshSession()`；`js/pages/admin.js` 调用 |
| P2-1 | 建库脚本位置与 `pom.xml` 注释口径不一致 | `pom.xml` 注释改为 `db/typhoon_monitor.sql` |
| — | 库名口径不一致导致连不上库 | `db.properties` / `DBUtil` 兜底 / SQL 脚本 / 文档全部统一为 `typhoon_monitor` |
| — | `sql/README.md` 描述了一套不存在的分步脚本、并引用了已删除的 `data.js` | 重写为指向 `db/` 的说明 |

### 第二轮（补单元测试期间）新增修复

| 编号 | 问题 | 修复位置 | 怎么发现的 |
|---|---|---|---|
| P0-1 | 无单元测试 | 新增 49 个测试文件 / 约 14800 行 | 课程硬性要求 |
| P1-3 | `refreshUnread()` 把任何失败当"没有未读" | `js/layout.js` + `css/app.css` | 静态审查（与 P1-1 同源） |
| P1-5 | 会话超时两处维护 | `AuthServlet` / `AuthServiceImpl` | 静态审查 |
| P1-6 | `lastLogin` 实为 `updated_at` | `users` 表 / `User` / `UserDao` / `UserVO` / `AuthServiceImpl` | 静态审查 |
| **新 1** | `queryNumber()` 在空结果集上调 `wasNull()` → NPE | `util/JdbcTemplate.java` | 单测 `JdbcTemplateTest$Scalars` |
| **新 2** | `successRate()` 空数据返回 100% | `dao/impl/SyncLogDaoImpl.java` | 单测 `MetadataDaoTest$Logs` |
| **新 3** | `requireAdmin()` 未登录也抛 403 | `servlet/BaseServlet.java` | Servlet 端到端测试 |
| **新 4** | `PUT /api/typhoons/{id}` 全量覆盖导致 500 | `service/impl/TyphoonServiceImpl.java` | 接口回归脚本 + Tomcat 日志 |
| **新 5** | 两个 Service 带参构造声明实现类，堵死单测 | `ForecastServiceImpl` / `TrackServiceImpl` | 单测 mock 失败（JDK 25 限制） |
| **新 6** | `api.js` 的 `feedbackType` 示例值 `'BUG'` 是非法值 | `js/api.js` JSDoc | 接口回归脚本 |

> 新 1—新 4 是**生产缺陷**，新 5 / 新 6 是可测性与文档契约问题。
> 四条生产缺陷里有三条（新 1、2、4）在页面上表现为「数据访问失败，请稍后重试」，
> 靠点页面复现根本定位不到 —— 这正是补测试的直接收益。
