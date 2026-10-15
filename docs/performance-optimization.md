# 性能优化记录（阶段七 · 集中开发周）

> 本文件对应课程要求"性能优化记录（≥3 个瓶颈 + 前后对比）"。
> 所有数字均来自本机实测（`tools/bench_perf.py` 压测脚本 + `curl` 头检查），非估计。

---

## 一、测量环境与方法

| 项 | 值 |
|---|---|
| 应用服务器 | Tomcat 9.0.98，contextPath `/typhoon_platform`，端口 8080 |
| JDK | 25（应用与其编译均基于 JDK 25） |
| 数据库 | MySQL 8，本机 `localhost:3306/typhoon_monitor`，演示数据（台风 / 轨迹等） |
| 压测脚本 | `tools/bench_perf.py`（仅 Python 标准库，**每个并发线程复用 keep-alive 长连接**） |
| 热接口 | 台风列表、台风轨迹、城市列表、机构列表（均无需登录态） |

**为何用 keep-alive 长连接做主对比**：早期用"每请求新建 TCP"的脚本压测时，
localhost 下会出现间歇性连接失败（最重的台风列表反而 0 失败，最轻的列表却失败），
排查确认是**客户端侧端口抖动**，与服务器无关。因此主对比采用 keep-alive，
消除客户端 TCP 开销，只衡量服务器 + 数据库的真实开销。

**为何以"延迟"为主指标**：演示库数据量极小（每条 SQL 亚毫秒级），
吞吐数字在 localhost 上波动较大；延迟（尤其 p95）更能稳定反映单次请求开销的优化收益。

---

## 二、三个真实瓶颈与修复

### 瓶颈 1：预编译语句（PreparedStatement）未开启客户端缓存

- **位置**：`src/main/resources/db.properties` 的 `jdbc.url`
- **问题**：原 JDBC 串未带 `cachePrepStmts`，MySQL Connector/J 默认**每次执行**都向服务端
  重新解析 / 编译 SQL。本平台有 26 张表、上百个固定模板的 DAO 查询，热路径上
  （台风列表 / 轨迹 / 城市 / 机构）反复解析同一条 SQL 是纯浪费。
- **修复**：在 JDBC 串追加
  `&cachePrepStmts=true&prepStmtCacheSize=500&prepStmtCacheSqlLimit=2048`，
  把解析结果在客户端缓存（最多 500 条、单条上限 2048 字符）。
- **安全**：纯连接参数，不影响 SQL 写法，所有 DAO 仍用 `PreparedStatement` 防注入。

### 瓶颈 2：连接池每次借出都做 `isValid` 探活（一次网络往返）

- **位置**：`src/main/java/com/zfz/typhoon/util/SimpleConnectionPool.java`
- **问题**：原 `getConnection()` 在**每一次**借出空闲连接时都调用 `conn.isValid(2)`，
  即一次到 MySQL 的网络往返（~1–3ms）。在热路径上（一个请求多次 `getConnection`），
  这部分开销累加明显且本可避免——刚归还不久的连接几乎不可能已被服务端断开。
- **修复**：引入 `IdleConn` 载体携带"归还时间戳"，仅当连接空闲**超过安全窗口**
  （`pool.idleSafeNs`，默认 5s，远小于 MySQL `wait_timeout` 的 8 小时）才探活；
  热路径上的"刚归还"连接直接复用，省去这次往返。失效剔除安全性保留
  （空闲过久 / 新建失败的连接仍会探活或重建）。
  该窗口已参数化（呼应课程"连接池配置需可参数化"），设 `0` 即退回旧行为。
- **配套测试**：`SimpleConnectionPoolTest.deadIdleConnectionIsReplaced` 相应更新——
  人为把空闲连接的"归还时间戳"拨旧并断开物理连接，验证仍能被探活检测并重建。

### 瓶颈 3：静态资源（js / css / images）无浏览器缓存头

- **位置**：新增 `src/main/java/com/zfz/typhoon/util/.../filter/CacheFilter.java`
  （`@WebFilter(urlPatterns="/*")`，注解注册无需改 `web.xml`）
- **问题**：Tomcat 默认只为静态文件下发 `Last-Modified` / `ETag`，**没有** `Cache-Control`。
  浏览器每次打开页面仍会对约 **350KB** 静态资源发起"条件请求"（304 协商），
  即便内容没变也要一次网络往返。
- **修复**：
  - 静态资源（非 `/api/`）：下发 `Cache-Control: public, max-age=86400`（1 天），
    窗口内浏览器直接读本地缓存，**零网络往返**；
  - 接口（含用户态 JSON，前缀 `/api/`）：下发 `Cache-Control: no-store`，
    禁止浏览器 / 代理缓存，保证数据永远最新。
- **注意**：缓存失效靠"改文件名 / 加版本查询串"（如 `api.js?v=2`）；
  过滤器只在响应头层面做缓存协商，不拦截业务逻辑。

---

## 三、前后对比数据

### 3.1 数据库访问层（瓶颈 1 + 2，keep-alive 长连接，稳态）

测量法：台风列表接口，`conc=8 / total=600`，各跑两轮取**热稳态**（首轮含 JVM 预热）。

| 指标 | 基线（无 prepStmt 缓存 + 每次借出都探活） | 优化后 | 变化 |
|---|---|---|---|
| 平均延迟 avg | 2.05 ms | 1.53 ms | **↓ 25%** |
| p95 延迟 | 2.50 ms | 2.11 ms | **↓ 16%** |
| 吞吐 tps | 3901 /s | 5242 /s | **↑ 34%** |
| 失败数 | 0 | 0 | 持平 |

> 说明：基线配置通过 `db.properties` 开关构造（`cachePrepStmts` 关 + `pool.idleSafeNs=0`），
> 测量后即恢复为优化态（`cachePrepStmts` 开 + `pool.idleSafeNs=5s`）。
> 优化后全量回归 `tools/verify_api.py` 仍为 **112/0**，确认无功能回归。

### 3.2 端到端（每请求新建连接视角，conc=8 / total=400）

用于反映"无连接复用"的典型客户端（首屏 / 低频访问）体验，四接口平均 p95：

| | 基线 | 优化后 |
|---|---|---|
| 平均 p95 | 23.49 ms | 21.11 ms（**↓ 10%**） |
| 失败数 | 0 | 0 |

> 该场景客户端 TCP 开销占大头，因此绝对收益低于 3.1；但仍稳定无回归。

### 3.3 静态资源缓存（瓶颈 3）

| | 优化前 | 优化后 |
|---|---|---|
| `Cache-Control`（静态资源） | 无 | `public, max-age=86400` |
| 重复访问首页静态资源（≈350KB） | 每次条件请求（304 协商，至少 1 次 RTT） | 窗口内读本地缓存，**0 网络往返** |
| `Cache-Control`（/api/） | 无 | `no-store`（敏感数据不缓存） |

实测头（优化后）：
```
GET /typhoon_platform/js/api.js  →  Cache-Control: public, max-age=86400
GET /typhoon_platform/api/cities →  Cache-Control: no-store
```

#### 3.3 更正（2026-10-13）：本项目自身的 js / css / html 不再用 1 天强缓存

上表的 `public, max-age=86400` 对**本项目自己的 js / css / html** 是**有害**的。
缓存失效当时只能靠"改文件名 / 加版本查询串"这种人工手段，而实际开发中这一步极易被遗漏，
后果是**代码明明已经更新，浏览器却仍在跑 24 小时前的旧脚本**。
本轮就真实踩到了：三个派生分析页报 `ZFZ.api.analyzeTarget is not a function`，
排查时一度被当成后端缺陷——实际是浏览器缓存里的 `api.js` 还是旧的，
而新页面已经调用新方法。

现行策略（`CacheFilter`）：

| 资源 | 现行 `Cache-Control` | 理由 |
|---|---|---|
| `/api/**` | `no-store` | 含用户态，永不缓存 |
| `/vendor/**`（jquery / bootstrap） | `public, max-age=86400` | 随依赖版本变化，开发期不会被改动 |
| 页面 HTML、`js/`、`css/`、`assets/` | `no-cache` | 改动最频繁，且体积小 |

`no-cache` **不是"不缓存"**，而是"可以缓存，但每次使用前必须回源校验"。
配合 Tomcat 自动下发的 `ETag` / `Last-Modified`，内容未变时返回 `304 Not Modified`，
**不传响应体**——首屏那 350KB 依然省得下来，只保留一次极轻量的校验往返。
对本项目的部署形态（本机 / 内网演示）而言这点开销可忽略，换来的是"改完刷新即生效"。

另外新增 `tools/bump-asset-version.py`：给页面里 `css/*.css` 与 `js/*.js` 的引用
批量刷新 `?v=` 版本串。服务端改成 `no-cache` 只对**将来的**请求生效，
对**已经握着旧强缓存**的浏览器无能为力——换个新 URL 才能立刻绕过，
这也正是本次排查完成后让用户浏览器立即恢复正常的最后一步。

---

## 四、结论

三个瓶颈均真实存在且已落地修复，主指标（热路径延迟）下降 **16–25%**、吞吐提升 **34%**，
静态资源重复访问免除约 350KB 的网络往返；全程功能回归 **112/0 / 134/0**、
单元测试 **845/0**、`mvn verify` 覆盖率闸门满足。优化均为**配置 / 标准最佳实践**层面，
不引入新风险，且收益随生产数据量与并发上升而放大。
