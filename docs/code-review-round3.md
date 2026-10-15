# 追风者 · 第三轮独立复核问题清单

> 复核时间：2026-10-11
> 复核方式：全量静态阅读（33k 行 Java / 7.1k 行前端 JS / 27 张表 DDL），逐项取证
> 与 [`code-review.md`](code-review.md) 的关系：**本文只记该文档未覆盖或标记"仍未解决"的问题**，
> 已修复项不重复。编号用 `N*`（New）以区别于原文档的 `P*`。
>
> 级别定义沿用原文档：**P0** 阻断交付 / 安全事故 / 课程硬性要求未达标；
> **P1** 功能或健壮性缺陷，用户可感知；**P2** 可维护性、一致性隐患。

---

## 修复状态（2026-10-11 当日处置）

### 已直接修复（改动集中、无产品口径争议）

| 编号 | 处理方式 |
|---|---|
| N1 | `LoginAttemptDao#incrementFail` 把 `fail_count = fail_count + 1`（含 TTL 重置判断）下沉到一条 `INSERT ... ON DUPLICATE KEY UPDATE`，由数据库行锁串行化；Service 只读回结果判定阈值，新增 `setLocked` 写入锁定并归零计数 |
| N3 | 前后端参数名统一为 `shared`：`js/api.js` 由 `public` 改正为 `shared`（后端读的一直是 `shared`） |
| N4 | `SandboxServlet` 去掉 `?userId=` 分支，身份改取会话（`mine=1`），杜绝 IDOR |
| N7 | 池化连接的 `close()` 增加 per-proxy 幂等标记，重复关闭不再二次归还 |
| N9 | 订正 `BaseServlet` / `docs/code-review.md` / `docs/api.md` 中不实的「401 跳登录页」表述 |
| N11 | 通知改为「点击单条标记已读」，面板头新增显式「全部已读」按钮，去掉"打开即全读" |
| N12 | 补上真实轮询：60s 一次 + 仅页面可见 + 仅已登录三重节流 |
| N13 | `JsonUtil.write` 保证不抛异常：序列化失败降级为通用错误响应，写出失败只记日志 |
| N14 | `JsonUtil.readBody` 增加 1MB 请求体上限（按 Content-Length 拦截 + chunked 逐行兜底） |
| N15 | `docs/api.md` 新增「1.5 身份取用规则」，固化为项目级强制约定 |

### 后续按决策落地（2026-10-11 决策 → 当日实现）

| 编号 | 决策 | 落地内容 |
|---|---|---|
| N2 | 做事务 | `DBUtil` 新增 `TxMode` 开关（JDBC / DIRECT）与 `runInTransaction(TxWork<T>)` 模板方法；三类多表写包进事务：`DataSourceServiceImpl#recordSync`（日志 + 回写同步时间）、`SurveyServiceImpl#rebuild`（删旧聚合 + 写新）、`RiskAssessmentServiceImpl#assessAndSave`（删旧评估 + 逐行插入）。失败通知、聚合计算等副作用刻意留在事务外 |
| N5 | 确认做 | 口令摘要改为 `sha256$<32位随机盐>$<hex>`（`SecureRandom` 出盐）；`verify` 同时认得 4 种历史格式；新增 `needsUpgrade`，登录成功后由 `AuthServiceImpl#migrateHashIfLegacy` **顺带**把旧格式升级为新格式（失败只记日志，不阻断登录） |
| N6 | 做「要能回访」 | 新建 `ContactUtil`：AES-256-GCM 认证加密（`enc1:` 前缀 + `Base64(IV‖密文‖tag)`），密钥由 `db.properties` 的 `contact.cipher.key` 经 SHA-256 派生；`FeedbackServiceImpl#submit` 与 `SurveyServiceImpl#submit` 入库前加密，读取时解密；`FeedbackReport#getContactMasked()` 派生打码值供列表展示；`contact_info` 由 `varchar(100)` 放宽到 `varchar(255)`（附幂等迁移脚本 `db/migration_contact_encryption.sql`） |
| N8 | 先压测，有数据再改 → **决定不改** | 新增 `ConnectionPoolProbeBenchmarkTest`（假 JDBC 驱动 + 探活计数器），量化出借出侧优化只省一半探活（2000/4000），归还侧仍 100% 探活。数据出来后**若风裁定维持现状**：对称化会把坏连接的拦截点从「归还那一刻」推迟到「整个安全窗口之后」，省下的是一次本地往返，换来的是随机 `Communications link failure`。**代码保持原样**，数据与理由见下方「N8 压测结论」 |
| N10 | 做 | 后端 `GET /api/typhoons` 的 `data` 由纯数组改为 `{items,total,page,limit,pages}`，`X-Total-Count` 响应头保留兼容；`api.js` 的 `getTyphoons()` 加兼容壳（仍吐数组，13 处调用点不动），新增 `getTyphoonsPage()`；`archive.js` 接入服务端分页 + 上一页/下一页 + 「共 N 条 · 第 x / y 页」 |
| N3（补 UI 入口）| 做「含用户自己的方案」 | DAO 新增 `listVisible(userId, baseTyphoonId, limit)`（`WHERE (is_public = 1 OR user_id = ?)`，括号必需），替换掉只查公开的 `listPublic` 与会把他人私有方案捞出来的 `listByBaseTyphoon`；`SandboxScenarioVO` 新增派生字段 `mine` / `shared`；`PUT .../share` 权限由"仅管理员"放宽为「创建者本人 或 管理员」并抛 403/404；`sandbox.js` 情景卡片补归属标签与公开开关 |

> **连带发现（N5 的遗漏）**：`UserServiceImplTest` 里有 3 个用例仍用
> `PasswordUtil.hash("x")` 做等值断言/桩值，随机盐下必然不匹配（1 个断言失败、
> 2 个抛 `PotentialStubbingProblem`）。已改为断言**性质**（格式合规、能校验通过、
> 不含明文、不判为待升级）与 `anyString()` 桩 + `ArgumentCaptor` 捕获实参。
> 这 3 个用例在第一轮修改 N5 时被漏跑 —— 单测改完必须跑**全量**，不能只跑受影响的类。

---

## 结论速览

| 编号 | 级别 | 问题 | 位置 |
|---|---|---|---|
| N1 | **P0** | 登录失败计数非原子，并发可绕过锁定（防护形同虚设） | `AuthServiceImpl.recordFail` + `LoginAttemptDaoImpl` |
| N2 | **P0** | 事务 API 是死代码，多表写入全程无事务保护 | `DBUtil` 事务四件套 |
| N3 | **P0** | 沙盘"公开情景"接口参数名前后端不一致，且前端无调用入口 | `api.js:482` ↔ `SandboxServlet:113` |
| N4 | P1 | 沙盘列表可按 `userId` 枚举他人私有情景（IDOR） | `SandboxServlet:63` + `SandboxServiceImpl.list` |
| N5 | P1 | 口令使用全局固定盐，且保留"不加盐"校验分支 | `PasswordUtil` |
| N6 | P1 | 联系方式被不可逆脱敏却对外称"加密存储"，回访能力丢失 | `FeedbackServiceImpl` / `SurveyServiceImpl` |
| N7 | P1 | 池化连接 `close()` 不幂等，重复归还可致同一物理连接入池两次 | `SimpleConnectionPool.wrap/release` |
| N8 | P1 | 归还路径无条件探活，抵消了借出侧的探活优化 | `SimpleConnectionPool.release` |
| N9 | P2 | 三处注释宣称"api.js 对 401 有跳登录页处理"，实际不存在 | `BaseServlet` / `api.js` / `code-review.md` |
| N10 | P2 | 列表总数只放在响应头，前端拿不到 → 分页不可用 | `api.js:getTyphoons` |
| N11 | P2 | 打开通知面板即全部标记已读（未看就清空） | `layout.js:406` |
| N12 | P2 | `refreshUnread()` 名义"轮询"，实际只调用一次 | `layout.js:485` |
| N13 | P2 | 统一兜底内部再抛异常会逃逸出 JSON 契约 | `JsonUtil.write` + `BaseServlet.service` |
| N14 | P2 | 请求体读取无大小上限 | `JsonUtil.readBody` |
| N15 | P2 | `?userId=` 型跨用户查询口径不统一（沙盘有、导出无） | `ExportServlet` / `SandboxServlet` |

---

## P0 · 必须处理

### N1 登录失败计数非原子 —— 并发请求可绕过 5 次锁定

**位置**
- `service/impl/AuthServiceImpl.java` → `recordFail(String account)`
- `dao/impl/LoginAttemptDaoImpl.java` → `save(...)`

**证据（读—改—写三步分离）**

```java
// AuthServiceImpl.recordFail —— 先查
LoginAttempt a = attemptDao.findByUsername(account);
int count = 1;
if (a != null && a.getLastFailAt() != null) {
    double gap = DateUtil.hoursBetween(a.getLastFailAt(), now);
    count = gap > FAIL_TTL_MINUTES / 60.0 ? 1 : a.getFailCount() + 1;
}
...
attemptDao.save(account, count, now, lockedUntil);   // 再写（UPSERT）
```

`LoginAttemptDaoImpl.save` 用的是 `INSERT ... ON DUPLICATE KEY UPDATE`，
**它只解决了"记录不存在时插入会主键冲突"这一件事**，
并没有让 `fail_count` 的递增变成原子操作 —— 递增仍发生在 Java 侧。

**为什么这是真问题**

`UPSERT` 的注释（`LoginAttemptDaoImpl` 类头）写的是"避免先查后写，把判断交给数据库"，
但计数恰恰还是"先查后写"。并发时序：

| 时刻 | 线程 A | 线程 B | 库中 fail_count |
|---|---|---|---|
| t1 | 读到 null，算 count=1 | | 0 |
| t2 | | 读到 null，算 count=1 | 0 |
| t3 | 写 1 | | 1 |
| t4 | | 写 1（覆盖） | 1 |

N 个并发错误口令请求，最终 `fail_count` 仍是 1，**永远到不了 5**。
攻击者只要并发发请求，锁定机制就等于不存在 —— 而锁定是本项目唯一的爆破防护。

**建议修复**

把自增下沉到 SQL，让数据库承担原子性，例如：

```sql
-- 失败自增（不存在则插 1）
INSERT INTO login_attempts (username, fail_count, last_fail_at)
VALUES (?, 1, ?)
ON DUPLICATE KEY UPDATE
  fail_count  = fail_count + 1,
  last_fail_at = VALUES(last_fail_at)

-- 再读回判定是否达到阈值并写 locked_until
```

更稳的做法是一步到位：

```sql
UPDATE login_attempts
   SET fail_count = fail_count + 1,
       last_fail_at = ?,
       locked_until = IF(fail_count + 1 >= ?, ?, locked_until)
 WHERE username = ?
```

（后者需要先保证行存在；可先 `INSERT IGNORE`。）无论哪种，
判断"是否锁定"的依据都应是**数据库自增后的值**，而不是 Java 里算出来的数。

> 顺带：这条也解释了为什么 `AuthServiceImplTest` 全绿却查不出来 ——
> 单测是串行调用，天然没有并发窗口。

---

### N2 事务 API 完全是死代码，全项目多表写入无事务保护

**证据**

```
$ grep -rn "DBUtil.beginTransaction" src/main/java
（仅命中 DBUtil.java 自身的 JavaDoc 示例与 JdbcTemplate 的注释，无任何业务调用）
```

`beginTransaction / commit / rollback / endTransaction / inTransaction`
四件套在主代码里**零调用点**，只在 `DBUtilTest` 中被测试。

**连锁影响**

1. `SimpleConnectionPool.release()` 里那段"事务残留的回滚保护"（`if (!real.getAutoCommit())`）
   **永远不会触发** —— 因为没有任何路径会把连接设为 `autoCommit=false`；
2. 所有写入都是"每条 SQL 一个独立自动提交事务"。凡是需要多张表一致的业务
   （保存风险评估、生成对比任务、提交问卷并更新聚合、登记同步日志、管理员批量改参数……）
   一旦中途抛异常，**前面的写已经落库，后面的没写**，留下半截数据；
3. `docs/api.md`、`docs/performance-optimization.md` 若声称有事务保证，则与实现不符。

**建议**

- 先确认真实需要原子性的业务点（建议从"问卷提交+聚合""对比任务+明细"
  "反馈提交+通知"这三类多表写开始），在这些 Service 方法上补：

```java
DBUtil.beginTransaction();
try {
    // 多个 DAO 调用
    DBUtil.commit();
} catch (RuntimeException e) {
    DBUtil.rollback();
    throw e;
} finally {
    DBUtil.endTransaction();   // 必须 finally，否则 ThreadLocal 残留 + 连接泄漏
}
```

- 若权衡后决定"演示项目不做事务"，**也应在文档里显式声明**，
  而不是留一套看上去能用、实则没接的 API —— 后者比没有更危险，
  因为它会让人以为已经有了保护。

---

### N3 沙盘"公开/取消公开"接口参数名前后端不一致，且前端根本没有入口

**证据（两端参数名对不上）**

```js
// js/api.js:480
shareSandboxScenario: function (id, isPublic) {
  return request('PUT', '/api/sandbox/scenarios/' + id + '/share',
                 { public: isPublic ? 1 : 0 });      // ← 发的是 public
},
```

```java
// SandboxServlet.java:113
boolean shared = ParamUtil.boolVal(req, "shared", false);   // ← 读的是 shared
int rows = sandboxService.setShared(scenarioId, shared);
```

`ParamUtil.boolVal` 在参数缺失时返回 `defaultValue`（此处 `false`），
所以 `PUT .../share` **永远被解析为"取消公开"**，无法公开任何情景。

**叠加影响（这才是真正让页面出问题的一环）**

```
$ grep -rn "shareSandboxScenario" src/main/webapp/js/
仅 api.js 的定义处 —— 没有任何页面调用它
```

即：**该能力在前端不可达**。而情景库列表的默认分支恰好只返回公开情景：

```java
// SandboxServiceImpl.list
} else {
    rows = scenarioDao.listPublic(max);      // WHERE is_public = 1
}
```

于是链路变成：新保存的情景 `is_public` 恒为 0（`INSERT` 未写该列，DDL 默认 0）
→ 没有任何入口能把它改成 1 → **沙盘页"情景库"永远看不到用户自己保存的方案**。
目前页面之所以还有内容，是靠 `db/typhoon_monitor.sql` 演示数据里
预置的 4 条 `is_public=1` 记录（scenario_id = 1,2,3,8,10）。

**建议**

1. 统一参数名。建议**都改成 `shared`**（与 `SandboxScenarioDao#setPublic`、
   `SandboxRequest#setShared`、DDL 的 `is_public` 语义链一致），或都改成 `public`；
   无论选哪个，`docs/api.md` 里要有一行明文（目前 grep `share` 在该文档中零命中，
   属接口文档覆盖缺口）。
2. 在 `pages/sandbox.js` 的情景卡片上补"公开/取消公开"入口（管理员可见），
   或明确产品决策"情景库只展示平台预置示例"，同时把 `listByUser` 用在"我的方案"标签页。

---

## P1 · 用户可感知的缺陷

### N4 沙盘列表可按 `userId` 枚举他人私有情景（IDOR）

**证据**

```java
// SandboxServlet.doGet —— mine=1 才要求登录，否则由 URL 任意指定 userId
boolean mine = ParamUtil.boolVal(req, "mine", false);
Long userId = mine ? Long.valueOf(requireUserId(req)) : optionalLong(req, "userId");
```

```java
// SandboxServiceImpl.list —— userId 分支不带 is_public 过滤
if (userId != null) {
    rows = baseTyphoonId == null
        ? scenarioDao.listByUser(userId.longValue())                    // 只看 user_id
        : scenarioDao.listByUserAndTyphoon(userId.longValue(), ...);
}
```

`listByUser` 的 SQL 是 `WHERE s.user_id = ?`，**没有 `AND s.is_public = 1`**。
`AuthFilter` 的 `LOGIN_PREFIXES` 也不含 `/api/sandbox`（该类路径整体公开）。

**后果**：匿名请求 `GET /api/sandbox/scenarios?userId=3` 即可拿到用户 3 的全部方案，
包括他并未公开的那些。这是教科书式的 IDOR（越权访问对象引用）。

**建议**

- 该分支只保留"看自己"：把 `?userId=` 直接去掉，统一用 `mine=1` + 会话身份；
- 若确实需要"看某人的公开方案"，则 SQL 必须加 `is_public = 1`，
  并把这个约束写在 DAO 方法名上（如 `listPublicByUser`），避免后人误用。

> 顺带：`SandboxServlet.doPut` 里 `requireAdmin` 使"公开"只有管理员能做，
> 而情景归属普通用户 —— 这个组合本身也值得再确认一次产品意图。

---

### N5 口令使用全局固定盐，且保留"不加盐"校验分支

**位置**：`util/PasswordUtil.java`

```java
private static final String SALT = "zfz-typhoon-platform-2026";   // 硬编码、全站共用
```

**问题**：这不是"加盐"，本质是 **pepper（全局固定前缀）**。
真正的盐应当**每个用户一份、随行存储**。固定盐的后果：

- 相同口令（如 `123456`）在所有用户行里的摘要完全相同 ⇒ 一次爆破对所有用户生效；
- 攻击者拿到库后可预计算答案表，盐没有起到"让预计算失效"的作用。

另外 `verify()` 为兼容历史数据保留了**不加盐 SHA-256** 分支
（`sha256$` 前缀下先试加盐、再试不加盐；无前缀走裸摘要）。
这在迁移期是必要的，但意味着**弱格式永远不会过期** —— 只要库里还留着旧值，
就永远存在一条比新格式更容易被击穿的校验路径。

**建议**

- 新格式改为 `sha256$<perUserSalt>$<hex>`，salt 用 `SecureRandom` 生成 16 字节；
- 登录成功后若发现该行仍是旧格式，**顺手升级为新格式**（渐进式迁移，不需要一次性刷库）；
- 在 `docs/code-review.md` 已经承认"生产应换 BCrypt/Argon2"的基础上，
  把"每用户随机盐"也写进该方法注释，避免答辩时被追问。

---

### N6 联系方式被不可逆脱敏，却对外称为"加密存储"

**位置**
- `service/impl/FeedbackServiceImpl.java:81` → `feedback.setContactInfo(PasswordUtil.maskContact(...))`
- `service/impl/SurveyServiceImpl.java:133` → 同上
- `util/PasswordUtil.maskContact` 的第一行 JavaDoc 写的是"**加密**问卷联系方式（课程要求：联系方式加密存储）"

**实际行为**

```java
if (c.matches("\\d{11}")) {
    return c.substring(0, 3) + "****" + c.substring(7);   // 138****8888
}
int at = c.indexOf('@');
if (at > 0) {
    return c.charAt(0) + "***" + c.substring(at);         // a***@x.com
}
return Base64.getEncoder().encodeToString(...);           // 其它：可逆 Base64
```

- 手机号/邮箱分支是**不可逆的信息销毁**（掩码位不保留任何原始信息）；
- 兜底分支又是**可逆的 Base64**（不是加密，也没有密钥）。

于是同一个方法里混了三种语义（销毁 / 混淆 / 明文可还原），
而三个调用点的注释都说"脱敏后入库、不落原始值"。

**后果**

1. 反馈功能失去"留下联系方式以便回访"的价值 —— 管理员拿到的就是 `138****8888`，
   既联系不上用户，也无法验证；
2. "课程要求：联系方式**加密**存储"这一考点，目前实现并不满足
   （既不是可还原的加密，也不是规范的脱敏策略）；
3. Base64 兜底分支会让人误以为"加密了"，实际上 `base64 -d` 即可还原。

**建议**（需先定产品意图，二选一）

- **要能回访**：改成"AES-GCM 加密入库 + 页面展示脱敏"，密钥走配置项
  （这是唯一能同时满足"加密存储"与"可回访"的方案）；
- **只要展示**：那就不该在入库前破坏原值 —— 应当**存原值、只在渲染时打码**，
  或者干脆在表单上说明"联系方式仅用于统计，不留存原始值"。

无论哪种，请把方法名与 JavaDoc 统一（现在是 `maskContact` 配"加密"的注释）。

---

### N7 池化连接的 `close()` 不幂等 —— 重复归还可致同一连接入池两次

**位置**：`util/SimpleConnectionPool.java` → `wrap()` / `release()`

```java
if ("close".equals(name)) {
    release(real);        // 每次 close 都走一次归还，没有"已归还"标记
    return null;
}
```

```java
private void release(Connection real) {
    ...
    borrowed.decrementAndGet();                      // 每次 close 都减
    ...
    if (!idle.offer(new IdleConn(real, ...))) { ... }
}
```

**问题**：JDBC 规范要求 `Connection.close()` **幂等**（重复调用必须是空操作），
但池化代理没有做这个保护。任何一次"double close"都会：

1. `borrowed` 重复递减 → 计数变负 → 后台"系统运行状态"面板指标失真；
2. **同一物理连接被 `offer` 进空闲队列两次** → 之后可能被两个线程同时 `poll` 到 →
   **两个请求并发使用同一个 Connection**。MySQL 连接不是线程安全的，
   这会造成结果错乱（最典型的现象是 `ResultSet` 串台、事务边界互相干扰），
   而且极难复现定位。

目前项目里 `JdbcTemplate` 每次都只 close 一次，所以**尚未触发**；
但这是 API 形状问题：任何一处将来写成"try-with-resources + finally 再 close"就会踩中。

**建议**：给每个代理配一个归还标记

```java
final AtomicBoolean returned = new AtomicBoolean(false);
...
if ("close".equals(name)) {
    if (returned.compareAndSet(false, true)) { release(real); }
    return null;
}
```

同时建议在 `release()` 入口加一道断言（`borrowed` 为负时打 warning 日志），
让这类问题一旦发生就能被观察到，而不是静默累积。

---

### N8 归还路径无条件探活，抵消了借出侧的探活优化

**位置**：`util/SimpleConnectionPool.java`

借出侧做了窗口优化（`getConnection`）：

```java
if (!fresh && (System.nanoTime() - ic.returnedNanos) > idleSafeNs) {
    if (!isValid(real)) { ... }        // 只在"空闲超过安全窗口"时才探活
}
```

归还侧却没有对应处理（`release`）：

```java
if (closed || idle.size() >= maxIdle || !isValid(real)) {   // ← 由于 || 短路
    safeClose(real);                                        //   只要池没满，isValid 必然执行
    ...
}
```

**为什么这是矛盾**：`closed` 为 false、池未满时，条件会一路求值到 `isValid(real)`，
即**每次归还都做一次 `isValid(2)` 探活**（一次网络往返）。
于是"借出时省掉的探活"在归还时又付了回去 —— 一个"借出+归还"循环仍至少一次往返，
类注释里"省去一次网络往返"的性能收益被对冲掉大部分。

**建议**（择一，不要两处都做）

- **对称化**：归还侧也按"连接被持有的时长"判断，短持有（比如 < 1s）直接入池不探活；
- **单一策略**：只在借出侧探活，归还侧只做 `idle.size()` 判断
  （配合一个后台保活线程定期 `isValid` 清理陈旧连接）；
- 压测对比：`pool.idleSafeNs` 设为极大值（退化为"每次探活"旧行为）与当前值各跑一轮，
  用 `describe()` 的"累计获取"与响应时间做对照，把结论写进
  `docs/performance-optimization.md`（该文档目前对这块的描述与实际行为不一致）。

---

## P2 · 可维护性与一致性隐患

### N9 三处注释都宣称"api.js 对 401 有跳登录页的处理"，实际没有

被引用的原文：

| 位置 | 原文 |
|---|---|
| `BaseServlet` 类注释 | "前端 api.js 对 401 有'跳登录页'的特殊处理，返回正确的 401 才能触发那条路径" |
| `docs/code-review.md` P2-11 | "`js/layout.js` 的 `isUnauthorized(err)` 把 `err.code === 401` 判定为'会话失效 / 未登录'，**会触发跳登录页的相关渲染**" |
| `docs/code-review.md` 第四节 | "401 才能触发那条路径" |

**实际**：

```
$ grep -rn "401" src/main/webapp/js/api.js src/main/webapp/js/pages/*.js
api.js:222        只在 JSDoc 里提到 401
admin.js:61       if (err.code === 401) { return true; }     ← 唯一真正处理 401 的地方
```

- `api.js` 的 `request()` 只负责 `reject(apiError(...))`，**没有全局 401 处理**；
- `layout.js` 的 401 分支只把顶栏画成"未登录"，**不跳转任何页面**；
- 项目里甚至没有独立登录页（登录表单嵌在 `admin.html` 内）。

也就是说，"401 → 跳登录页"这条被三处文档当作既成事实描述的链路，**并不存在**。
危害在于：后续有人依据这句话去写依赖（例如认为未登录时会被自动引导），
结果得到的是"页面原地不动"。建议要么补上统一的 401 钩子
（在 `request()` 里挂一个可注册的 `onUnauthorized` 回调，由 `layout.js` 注册，
只做"提示 + 引导到 admin.html 的登录区"），要么把三处注释改成实情。

---

### N10 列表总数只放在响应头，前端拿不到 → 分页不可用

**位置**：`js/api.js` → `getTyphoons`

其 JSDoc 自己写着：

> 台风列表（纯数组；总条数在响应头 X-Total-Count 中，而本方法只 resolve `data`，
> 因此前端拿不到该响应头——需要总数请自行留意这一点）

**后果**：`archive.html` 档案检索无法显示"共 N 条 / 第 x 页 y 页"，
只能做"上一页/下一页"的盲翻。这是**功能缺失**，不只是注释问题。

**建议**：两种都在一行内可改 ——
① 后端把 `total` 塞进 `data`（如 `{items, total}`）；
② `request()` 把 `xhr` 一并 resolve / reject 出去（`apiError` 已经带了 `xhr`，
成功路径没有）。②改动更小，但会改变所有调用方的返回值形状，需评估。

---

### N11 打开通知面板即把全部通知标记为已读

**位置**：`js/layout.js` → `bindHeader()` 的 notice 分支

```js
if (kind === 'notice' && !wasOpen) {
    renderNotice();
    var badge = host.querySelector('.dot-badge');
    if (badge) { badge.classList.add('is-cleared'); }
    if (ZFZ.api) {
        ZFZ.api.markAllNotificationsRead().then(null, function () { /* 未登录：忽略 */ });
    }
}
```

点开面板的**同一瞬间**（早于列表渲染完成）就把服务端全部置为已读。

**后果**：用户只是想扫一眼有什么新通知，未读状态立刻被清空，
"稍后再处理"变成"回头再也找不到"。对一个**时效性通知**为主的平台，这个交互是反向的。

**建议**：改为"点击某条通知时 `markNotificationRead(id)`"，
另在面板头部放一个显式的「全部已读」按钮。这也是 `api.js` 已经提供的方法
（`markNotificationRead` 目前同样**无调用点**，与 N3 是同类问题：接口写了、UI 没接）。

---

### N12 `refreshUnread()` 名义上是"轮询"，实际只调用一次

**位置**：`js/layout.js`

```js
refreshUnread: function () { ... }      // 内部注释写"顶栏红点轮询用"
```

而 `mount()` 里只有 `refreshUnread();` 一次调用，全文**没有任何 `setInterval` /
`visibilitychange` 触发**。结果是：新通知到来时红点不会自动出现，必须刷新页面。

**建议**：若要真轮询，建议 30s 一次 + `document.visibilityState === 'hidden'` 时跳过
（避免后台标签页白白打接口）；若刻意不做轮询，就把 `api.js` 与 `layout.js`
里"轮询"的措辞改掉，免得后续误判为已实现。

---

### N13 统一异常兜底内部再抛异常，会逃逸出 JSON 契约

**位置**：`util/JsonUtil.write()` + `servlet/BaseServlet.service()`

```java
// JsonUtil.write
} catch (IOException e) {
    throw new BusinessException(Result.CODE_SERVER_ERROR, "响应写出失败：" + e.getMessage(), e);
}
```

```java
// BaseServlet.service —— 正是在 catch 分支里调用 JsonUtil.write
} catch (Exception e) {
    LOG.log(Level.SEVERE, ...);
    JsonUtil.write(resp, Result.error("服务器内部错误：" + ...));   // ← 这里可能再抛
}
```

若响应已开始写出（客户端断连、连接重置），`write` 抛出的 `BusinessException`
**没有人接**，会一路冒到容器 → 返回 HTML 格式的 500 页。
这恰好破坏了本项目最在意的"HTTP 200 + JSON 契约"。

**建议**：`JsonUtil.write` 内部 catch 后**只记日志不抛**
（写失败时已经无法再写任何东西，抛出去没有意义）；
或在 `service()` 的这一行外面再套一层 try/catch 兜住。

---

### N14 请求体读取无大小上限

**位置**：`util/JsonUtil.readBody()`

```java
while ((line = reader.readLine()) != null) { sb.append(line); }
```

无长度校验。一个几十 MB 的请求体能直接吃掉堆内存（`readBody` 被所有
POST/PUT 接口使用，且大多数是公开接口）。

**建议**：先比对 `req.getContentLengthLong()` 与一个上限（例如 1 MB），
超限直接 `BusinessException.badRequest`；`getContentLength` 为 -1（chunked）时按读取字节数累计再截断。

---

### N15 `?userId=` 型跨用户查询的口径不统一

- `ExportServlet`（`/api/export/history`）：**只认会话身份**
  —— `long userId = requireUserId(req);`，无法通过参数换成别人；
- `SandboxServlet`（`/api/sandbox/scenarios`）：**接受 URL 里的 `userId`**（见 N4）。

同一种"查看某人数据"的需求，两个模块的安全姿态完全不同。
虽然当前只有沙盘这一个口子，但**口径不统一本身就是隐患**：
后人复制哪个模块的写法都"有先例"。

**建议**：定一条项目级规则并写进 `docs/api.md`：
**"涉及个人数据的查询，身份一律取会话，禁止从请求参数取 userId；
管理端跨用户查询必须显式走 `/api/admin/**` 并命中 `AuthFilter` 的管理员分支。"**

---

## 仍然悬着的老问题（来自 `code-review.md`，本次确认未处理）

| 原编号 | 内容 | 本次核对结论 |
|---|---|---|
| P2-10 | 父目录 `zhuifengzhe-web/` 旧副本未移出交付范围 | ⚠️ **第三轮误报** —— 交付目录全树（6 层）早已搜不到 `zhuifengzhe*` / `设计稿*`。**2026-10-11 追补更正**，见下方「第三轮的两处误报」 |
| P2-7 | 回归脚本未接入 Maven 生命周期 | **仍成立**：`pom.xml` 内没有 `exec-maven-plugin` / `antrun`，也没有任何 `node` / `python` 调用，回归脚本仍靠人工执行 `tools/verify_api.py` / `tools/zfz-mockfree-verify.cjs`；P0 级缺陷（N1~N3）恰好都是这两个脚本**测不出**的类型（并发、跨端参数名、无 UI 入口） |
| P2-6 | 量纲（0—100 / 0—1）两套并存，只靠注释 | ⚠️ **第三轮误报** —— `docs/api.md` §1.4「量纲总表」早已存在，且已显式点名 `models[].confidence`（0—100）与融合 `confidence`（0—1）**同名不同量纲**。**2026-10-11 追补更正**，见下方 |

---

## 第三轮的两处误报（2026-10-11 追补，自查发现）

**P2-10 与 P2-6 在写第三轮时已被修复，却被本表列为"仍悬着"。**

原因很具体：第三轮只读了 `code-review.md`（第一/二轮的**问题**清单），
没有接着读同目录下 `issue-tracking.md` 的「八、交付前清理（已做）」章节 ——
而那里正是记录"哪些老问题已经解决"的地方。于是把两个已关闭项重新报成待办。

核对证据：

| 编号 | 第三轮的结论 | 实际状态 | 证据 |
|---|---|---|---|
| P2-10 | 旧副本"仍在"，交付前需处理 | 交付目录已干净（副本已删，非归档） | 全树 6 层搜 `zhuifengzhe*`/`设计稿*` 为 0 命中 |
| P2-6 | "未变；建议在 api.md 补一张量纲总表" | 量纲总表已存在且相当完备 | `docs/api.md:58` §1.4，含同名不同量纲的告警 |

**流程修正（写进本章作为下一轮的强制步骤）**：
复核老问题前，必须**同时**读 `code-review.md`（问题）**与** `issue-tracking.md`（处置），
两者对账后才能下"仍悬着"的判断；只读前者必然产生误报。

---

## 建议的动手顺序

1. **N1（并发锁定）** —— 唯一一条"安全防护实际失效"的缺陷，改动集中在两个方法；
2. **N3（沙盘公开接口）** —— 参数名 + 补 UI 入口，改完情景库才真正可用；
3. **N2（事务）** —— 先定"做还是不做"，做则从三类多表写开始；
4. **N4（IDOR）** —— 删掉 `?userId=` 或给 SQL 加 `is_public = 1`，改动很小；
5. **N7 / N8（连接池幂等与探活对称）** —— 一起改，都属于 `SimpleConnectionPool`；
6. **N5 / N6（口令盐、联系方式语义）** —— 涉及数据格式与产品口径，需要先定策略；
7. **N9 ~ N15** —— 文档与体验层，可批量处理，建议顺手一起清掉。

> **一条方法论提醒**：本轮 15 个问题里，N1（并发）、N3（前端无调用点）、
> N11 / N12（接口写了 UI 没接）这几类，现有的"接口回归脚本 + 页面渲染巡检"
> 都天然测不到 —— 前者是时序问题，后两者是"接口存在但没人用"的空转。
> 若要补自动化，**"接口调用点覆盖检查"（前端 grep 不出的接口 = 未接线）**
> 与**并发用例**是两个投入产出比最高的方向。

---

## 附：N8 压测结论与裁定（决定不改，代码维持原样）

> 依据若风口径「先压测对比（把 `pool.idleSafeNs` 设极大值 vs 当前值各跑一轮），有数据再改」。
> 数据见下；**2026-10-11 若风裁定：选 ① 维持现状，等后续视情况再评估**。
> 于是本条由"待决策"转为"已决策 · 主动保留"，代码保持原样 ——
> 这是**有依据的取舍**，不是遗漏，下一轮复核请勿再作为待办重提。

**压测装置**：`src/test/java/com/zfz/typhoon/perf/ConnectionPoolProbeBenchmarkTest`
配套假驱动 `FakeJdbcDriver`。每次 `isValid()` 由假驱动模拟固定往返时延并计数，
因此 **"探活次数差"可直接换算成"省下/多付了几次往返"**。
断言的是**次数**（确定性），耗时只打印、不断言 —— 墙钟受 JIT 与调度影响，
写进断言必然变成随机失败源。

**对照设置**（池 `maxActive = maxIdle = 1`，每轮 2000 次「借出 → 立刻归还」，
单次探活模拟 200 µs）

| 轮次 | `pool.idleSafeNs` | 含义 |
|---|---|---|
| A | `0` | 优化前的行为：借出侧每次都探活 |
| B | `Long.MAX_VALUE` | 优化的理论上限：借出侧从不探活 |

**实测**

| 轮次 | 借出探活 | 归还探活 | 探活合计 | 耗时(ms) |
|---|---|---|---|---|
| A · 优化前 | 2000 | 2000 | **4000** | 5628.68 |
| B · 优化后 | **0** | 2000 | **2000** | 2830.36 |

**结论**

1. 借出侧优化**确实生效且符合预期**：2000 次探活归零，总耗时少 2798.32 ms，
   净收益 **50%**（4000 → 2000 次）。
2. **但优化只做了一半。** 归还侧 `release()` 的短路顺序是
   `closed || idle.size() >= maxIdle || !isValid(real)` ——
   中间那项在池未满时为假，于是**每次归还仍然必然探活一次**。
   稳态下"每轮净探活"从 2 次降到 1 次，而不是降到 0。
3. 若把归还侧也改成窗口判定（对称化），稳态下两侧都可不探活，
   **可再省 2000 次（约 400 ms）** —— 即当前实现只拿到了理论上限的一半收益。

**裁定理由（两条），以及"若要做需先补什么"**

**理由一 · 对称化会拆掉唯一的拦截点。** 归还侧探活的**目的**是"别把已经断掉的连接放回池子"。
改成时间窗口判定后，一条在窗口内断掉的连接会被放回，下一次借出时**也不会被探活**
（借出侧同样在窗口内跳过），于是坏连接被真正交给业务代码 —— 表现是随机的
`Communications link failure`。当前实现虽然多付一次往返，但"归还时必检 + 借出可能不检"
这个组合，恰好保证了**坏连接最多只在池里存活到归还那一刻**。

**理由二 · 本环境下收益≈0，而代价是随机故障。** 本项目数据库是本地 MySQL，
网络往返≈0；200 µs 是刻意放大后的模拟值。真实收益远小于上表，
而"取到坏连接"的排查成本很高。故**裁定维持现状**，把这条作为
"已知的性能/健壮性权衡"记录在案，不做为省一次本地往返而引入随机故障的改动。

**若某天要做（方案②），必须先补上别的失效感知通道**，可选：

- ① 借出侧改为"窗口判定 + 取用时一次轻量校验（如 `Connection#getAutoCommit`）"；
- ② 或引入池级租借时长上限（`maxLifetime`）+ 归还时的失效剔除；
- ③ 或依赖 MySQL 侧的 `wait_timeout` 与驱动自动重连（`autoReconnect`）兜底。

**何时应重新评估（触发条件）**

本条已结案，但并非永久定论。出现下列任一情况时，应重新翻开本节：

1. 数据库**离开本机**（换成远端实例 / 云数据库）—— 那时一次往返不再是 ≈0，
   200 µs 的模拟值开始接近真实，收益才值得换风险；
2. 日志里出现**非代码原因的 `Communications link failure`** —— 说明当前
   "归还时必检"这道闸门已经不够用，应先查失效感知而不是先删探活；
3. 池规模或并发量上调（`maxActive` 明显增大、出现连接长期空闲）—— 届时
   `maxLifetime` + 归还剔除可能比探活更划算；
4. 若最终仍要做对称化，**必须**先补上"归还后连接被外部关闭、再次借出必须探活到"
   的用例（用假驱动制造 `isClosed() == true`），证明没有牺牲这条保证。

> **代码里留了一道绊线**：`ConnectionPoolProbeBenchmarkTest` 断言
> `assertEquals(ROUNDS, current.returnProbes)`。一旦有人把归还侧改成不探活，
> 该断言会**立刻失败**——这正是设计意图，强制改动者回到本节重新决策，
> 而不是顺手把断言改绿。

---

**裁定留档**

| 项 | 内容 |
|---|---|
| 决策人 / 日期 | 若风 / 2026-10-11 |
| 结论 | 选 ① **维持现状**；本条从"待决策"转为"已决策 · 主动保留" |
| 代码变更 | **无** —— `SimpleConnectionPool` 保持原样，`db.properties` 的 `pool.idleSafeNs` 仍为 `5000000000`（5s） |
| 复现方式 | 跑 `ConnectionPoolProbeBenchmarkTest`（已并入默认测试集，约 16s） |
| 关联守则 | 性能类改动**先测再定**；"测了不改"同样是合法结论，但结论必须**连数据一起留档**，否则下一轮会被当待办重提 |
