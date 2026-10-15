# 追风者 · 单元测试与回归验证报告

> 课程要求对应（《课程要求.pptx》：JUnit 5 + Mockito 单元测试，覆盖率 ≥ 60%）
> 生成时间：2026-09-30
> 测试环境：JDK 25 · Maven 3.9.12 · JUnit 5.10.1 · Mockito 5.7.0 · JaCoCo 0.8.15
> 被测对象：`typhoon_platform` 后端（196 个类 / 8487 行可执行代码）
>
> ⚠️ **本文是 2026-09-30 的一次运行快照**，其中的用例数与覆盖率都是**那一次**的实测值，
> 换机器 / 加代码后必然变化。在交付机上用 JDK 21 · Maven 3.9.6 重跑
> `mvn -B test` 得到的当前值是：**918 例 / 0 失败 / 1 跳过**，
> 行覆盖率 **76.7%（6927 / 9026）**、指令 74.4%、分支 59.3%、方法 87.1%
> （取自 `target/site/jacoco/jacoco.csv`）。
> 保留原数字而不就地改写，是因为环境行（JDK / Maven 版本）与本机不同，
> 直接替换会变成一份"看起来是本机跑的、其实不是"的报告。

---

## 一、结论

| 指标 | 要求 | 实测 | 结论 |
|---|---|---|---|
| 行覆盖率 | ≥ 60% | **77.4%**（6569 / 8487） | 达标，超 17.4 个百分点 |
| 指令覆盖率 | — | 75.1%（28754 / 38289） | — |
| 分支覆盖率 | — | 59.0%（1971 / 3340） | — |
| 方法覆盖率 | — | 88.1%（2085 / 2367） | — |
| 类覆盖率 | — | 91.8%（180 / 196） | — |
| 单元测试用例 | — | **845 个，0 失败，1 跳过** | 全绿 |
| 接口回归用例 | — | **112 个，0 失败** | 全绿（真实 Tomcat + MySQL） |
| 前端浏览器断言 | — | **134 个，0 失败** | 全绿（无头 Edge + CDP） |

`mvn verify` 的 JaCoCo 闸门输出：

```
[INFO] --- jacoco:0.8.15:check (jacoco-check) @ typhoon-platform ---
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

---

## 二、覆盖率明细（按包）

| 包 | 覆盖行 | 总行数 | 行覆盖 | 分支覆盖 |
|---|---:|---:|---:|---:|
| `.model` | 917 | 917 | **100.0%** | 52 / 54 |
| `.filter` | 61 | 61 | **100.0%** | 40 / 42 |
| `.dao.query` | 45 | 46 | 97.8% | — |
| `.model.vo` | 1048 | 1087 | 96.4% | 84 / 128 |
| `.model.dto` | 30 | 32 | 93.8% | 3 / 10 |
| `.dao.impl` | 1258 | 1360 | 92.5% | 258 / 466 |
| `.util` | 760 | 841 | 90.4% | 360 / 406 |
| `.service.impl` | 2013 | 2916 | 69.0% | 1008 / 1716 |
| `.servlet` | 437 | 1227 | 35.6% | 166 / 518 |
| **合计** | **6569** | **8487** | **77.4%** | **1971 / 3340** |

### 两个低分包的成因（与"测没测到位"无关，是设计取舍）

- **`.servlet` 35.6%**：Servlet 层只有 **2 个**端到端测试类（`PublicEndpointTest` 13 例、
  `PersonalAndAdminEndpointTest` 30 例），覆盖的是"公共接口"与"鉴权矩阵"这两条最脆的路径。
  剩余未覆盖的多是各 Servlet 里 `if/else if` 的**资源分发分支**（`dashboard` / `todos` /
  `models` / `settings` …），它们逻辑同构、且已被 `tools/verify_api.py` 的 112 条真实
  HTTP 用例覆盖到 —— 继续补 JUnit 用例只会重复验证同一段分发逻辑，性价比低。
- **`.service.impl` 69.0%**：22 个 Service 里已有 13 个写了单测；未写的主要是
  重计算型服务（相似度矩阵、沙盘推演、导出编排），它们的分支多且强依赖 DAO 返回的数据形态，
  用 Mockito 打桩容易测成"桩的行为"而不是"代码的行为"。这类更适合集成测试，
  目前由接口回归脚本兜底。

---

## 三、测试资产清单

49 个测试文件 / 约 14800 行，分四类：

| 类别 | 数量 | 说明 |
|---|---:|---|
| 工具类测试 | 13 | `ParamUtil` / `PasswordUtil` / `DateUtil` / `GeoUtil` / `CsvUtil` / `JsonUtil` / `SqlBuilder` / `JdbcTemplate` / `DBUtil` / `SimpleConnectionPool` / `Result` / 两个异常类 |
| DAO 集成测试 | 9 | 直连真实 MySQL 3306，读写全链路；台风类测试自建 `UT000000X` 编号的临时台风，`@AfterAll` 清理 |
| Service 单测 | 13 | Mockito 注入 DAO 替身，验证业务规则（锁定次数、加权算法、量纲、幂等） |
| Servlet 端到端 | 2 | 用 `Proxy` 手写的请求/响应替身，走 `service()` 而非 `doGet()` |
| 支撑设施 | 4 | `FakeHttp` / `FakeResultSet`（测试替身）、`BeanSweeper`（POJO 反射批处理）、`BeanSweeper` 的三个调用方 |

---

## 四、三个必须知道的环境坑（换机器重跑时会遇到）

这几条是本次实测踩出来的，写在文档里省得下次从头排查。

### 4.1 JDK 25 上 Mockito **mock 不了 JDK 模块内的类**

```
Mockito cannot mock ... Could not modify all classes
  [java.sql.Wrapper, java.lang.AutoCloseable, java.sql.ResultSet]
```

Mockito 5 的 inline mock maker 无法改写 JDK 自带的类。**解法**：用
`java.lang.reflect.Proxy` 手写测试替身 —— `FakeResultSet`（实现 `ResultSet`）、
`FakeHttp.Req` / `FakeHttp.Resp`（实现 `HttpServletRequest` / `HttpServletResponse`）。
替身对未实现的方法返回**类型默认值**，这样测试先跑得起来，需要时再补分支。

### 4.2 JDK 25 上 Mockito 也 **mock 不了项目自己的具体类**

```
Could not modify all classes [class java.lang.Object,
  interface com.zfz.typhoon.dao.ForecastPathDao,
  class com.zfz.typhoon.dao.impl.ForecastPathDaoImpl]
```

这与 4.1 是同一个根因（`java.lang.Object` 也在改写链上）。**解法**：把生产代码里
带参构造的参数类型**从实现类放宽回接口** —— `ForecastServiceImpl` 与 `TrackServiceImpl`
原先写的是 `ForecastPathDaoImpl forecastDao`，已改为 `ForecastPathDao forecastDao`。
这个改动本身也是更好的设计（依赖倒置），生产行为零变化。

### 4.3 JaCoCo 与中文路径

- JaCoCo 0.8.12 的 ASM 只解析到 class major 67，**必须升到 0.8.15** 才能读 JDK 25 字节码；
- 工程路径含中文时，`destfile` 经 `cmd.exe /X /C` 转发会被破坏，
  因此报告落 `${java.io.tmpdir}`（即 `%TEMP%`）而非 `target/`；
- surefire 的 `argLine` **必须写 `@{argLine}`** 才能接上 JaCoCo 的 agent 参数；
- `prepare-agent` 认 `${jacoco.destFile}`，`report` 认 `${jacoco.dataFile}`，两个属性名不同。

---

## 五、测试过程中发现并修复的 4 个真实缺陷

补测试最大的价值不是那个覆盖率数字，而是下面这几条 —— 全是"写测试时跑出来的"，
不是读代码读出来的。

| # | 缺陷 | 位置 | 后果 | 修复 |
|---|---|---|---|---|
| 1 | `queryNumber()` 在**空结果集**上调用 `rs.wasNull()` | `util/JdbcTemplate.java` | MySQL Connector/J 抛 `NullPointerException`（`thisRow` 为 null），聚合查询无数据时直接 500 | 删掉空结果集上的 `wasNull()` 调用 |
| 2 | `successRate()` 在 `total == 0` 时返回 `100d` | `dao/impl/SyncLogDaoImpl.java` | **一次都没同步过的数据源，后台显示"成功率 100%"** —— 静默误导，运维会以为一切正常 | 改为返回 `0d` |
| 3 | `requireAdmin()` 未登录也抛 403 | `servlet/BaseServlet.java` | 与 `AuthFilter` 口径不一致（未登录→401、非管理员→403）；对没登录的人说"你没有管理员权限"是误导，前端也没法触发"跳登录页"逻辑 | 先判登录态：未登录 401，已登录非管理员 403 |
| 4 | `PUT /api/typhoons/{id}` 是**全量覆盖**而非局部更新 | `service/impl/TyphoonServiceImpl.java` | 只传一个字段会把其余列写成 `null`：轻则清空台风名/编号，重则撞 `international_id NOT NULL` 抛 500，前端只看到「数据访问失败」这句毫无指向性的提示 | 改为"先读库中现有记录 → 用请求体非 null 字段覆盖 → 整体回写" |

第 4 条的触发过程值得一提：回归脚本按 `api.js` 的文档「`payload` 待更新字段」
只传了 `{'typhoonId': tid, 'maxWindSpeed': 38}`，接口返回 500。
翻 Tomcat 日志才看到真正的错误是 `Column 'international_id' cannot be null` ——
**文档说的语义（局部更新）与实现（全量覆盖）不一致**，而错误信息被
`BaseServlet` 统一翻译成了看不出原因的通用文案。

---

## 六、回归验证脚本（已纳入工程）

| 脚本 | 用例数 | 作用 | 跑法 |
|---|---:|---|---|
| `tools/verify_api.py` | 112 | 按 `js/api.js` 的可执行契约逐条调真实接口；校验业务码、**HTTP 状态码恒为 200**、响应结构；写操作自建自删 | `python tools/verify_api.py` |
| `tools/zfz-mockfree-verify.cjs` | 134 | 无头 Edge + CDP 巡检 10 个页面：渲染节点数、关键文案、无控制台错误、无「加载失败 / 加载中」残留 | `<node> tools/zfz-mockfree-verify.cjs`（须在沙箱外跑） |

两个脚本都只依赖标准库 / Node 内置模块，不需要装第三方包。
它们此前是临时产物（在 `%TEMP%` 里，已被系统清理过一次），
现已落盘到 `tools/`，并写进 README 的「提交前自查」。

---

## 七、已知未覆盖项

- `DBUtil.shutdown()` **刻意不测**：它会把静态连接池置为 null，
  同 JVM 内后续用例会被污染。它的调用点在 `ServletContextListener` 的销毁回调里，
  容器关闭时才走，单测里验证不了。
- DAO 集成测试**依赖真实数据库**：没有 MySQL 时这些用例会失败。
  这是有意选择 —— 本项目 SQL 是手写拼装的，用 H2 之类的内存库跑出来的"通过"
  并不能证明在 MySQL 上真的能跑。
- 分支覆盖率 59% 低于行覆盖率：主要缺口在 Service 的异常兜底分支
  （`catch`、参数校验的边界），这类分支靠单测补齐性价比不高。
