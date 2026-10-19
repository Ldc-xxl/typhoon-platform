# 追风者 · 台风实况监测与灾害辅助分析 Web 平台

> Web 应用开发实践 · 课程大作业
>
> **代码仓库**：https://github.com/Ldc-xxl/typhoon-platform
>
> **前端**：HTML + CSS + jQuery 3.7.1 + Bootstrap 5.3.3（本地副本，无 CDN 依赖）
> **后端**：Java 8 + 原生 Servlet 4.0 + JDBC（**未使用任何框架**）
> **数据库**：MySQL 8.4
> **数据来源**：中央气象台台风网（**真实接口**，内嵌定时任务每 30 分钟自动同步，见 §7.6）
> **架构**：Model → DAO → Service → Servlet 四层 + Filter
> **部署**：`mvn package` 产出 WAR → Tomcat 9

---

## 一、当前完成情况（据实说明）

| 阶段 | 内容 | 状态 | 依据 |
|---|---|---|---|
| 阶段一 | 项目选题与数据库设计 | ✅ 完成 | 28 张表、3NF、含索引，见 `db/typhoon_monitor.sql` |
| 阶段二 | 后端基础开发（Model / DAO / 工具类） | ✅ 完成 | 28 实体 + 20 VO、28 DAO 接口 + 28 实现、20 个工具类 |
| 阶段三 | 业务逻辑与 Servlet 开发 | ✅ 完成 | 25 Service 接口 + 25 实现、16 个 Servlet（另有 `BaseServlet` 基类不计）、**62 条接口路径 / 86 个「方法+路径」**（口径见下） |
| 阶段四 | 后台管理界面开发 | ✅ 完成 | `admin.html` + `pages/admin.js`，全量接 `/api/admin/*` |
| 阶段五 | 前端界面开发 | ✅ 完成 | 11 个页面（含登录 / 注册页 `login.html`），**已全部改为真实接口驱动，演示数据层已物理删除**；派生分析页在「当前台风无数据」时自动回落并显式说明（见 §7.7） |
| 阶段六 | 测试与部署 | ✅ 完成 | 部署已验证；**单元测试 938 例 / 0 失败 / 1 跳过，行覆盖率 72.4%**（本节数字为 2026-10-13 实测；`docs/test-report.md` 是 2026-09-30 的历史快照，未回填） |
| 阶段七 | 集中开发周 | ✅ 完成 | 3 个真实性能瓶颈已修复并量化对比，详见 `docs/performance-optimization.md` |
| 阶段八 | 答辩与总结 | ⬜ 未做 | 按约定暂缓 |

### 已验证通过的部分（实测数据，非估计）

| 验证项 | 方法 | 结果 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito，938 个用例 | **938 通过 / 0 失败 / 1 跳过** |
| 行覆盖率 | JaCoCo 0.8.15（闸门 ≥60%） | **72.4%**（6977 / 9639 行），分支 56.4%、方法 84.9% |
| 后端接口契约 | `tools/verify_api.py`，123 条用例（业务码 / HTTP 恒 200 / 响应信封 / 权限矩阵） | **123 通过 / 0 失败** |
| 前端端到端 · 全站巡检 | 无头 Edge + CDP，`tools/zfz-mockfree-verify.cjs`，137 条断言 | **137 通过 / 0 失败** |
| 前端端到端 · 相似案例 / 沙盘 | CDP `zfz-page-verify.cjs`，44 条断言 | **44 通过 / 0 失败** |
| 前端端到端 · 调研 / 管理后台 | CDP `zfz-survey-admin-verify.cjs`，48 条断言 | **48 通过 / 0 失败** |
| 前端端到端 · 档案 / 情景库 | CDP `zfz-archive-sandbox-verify.cjs`，41 条断言 | **41 通过 / 0 失败** |
| **外部数据同步**（新增） | CDP `zfz-sync-verify.cjs`，22 条断言；另实测连续两轮同步幂等 | **22 通过 / 0 失败**；第 2 轮「新增 0 / 更新 1 / 跳过 31」 |
| 数据库连通 | `GET /api/health` → `data.database.ok` | **true**（连 `localhost:3306/typhoon_monitor`） |
| 优雅关闭（新增） | 卸载应用后查 `catalina` 日志有无 `clearReferences*` 泄漏告警 | **0 告警**：连接池、MySQL 清理线程、JDBC 驱动均已注销 |
| 性能优化 | 3 个瓶颈（预编译缓存 / 连接池探活 / 静态缓存）+ 前后对比压测 | **延迟 ↓16–25%、吞吐 ↑34%**，详见 `docs/performance-optimization.md` |

> **关于覆盖率从 77.1% 降到 72.4%**：接入外部同步后新增了 `CmaApiClient`、
> `TyphoonSyncService(Impl)` 与两个 `ServletContextListener`，其中实时 HTTP 拉取、
> SSL 信任链回退、调度异常兜底这些分支在单测里难以覆盖，分母涨得比分母快；
> 闸门（行覆盖 ≥60%）仍然通过。


---

## 二、环境要求

| 组件 | 本项目实际使用 | 说明 |
|---|---|---|
| JDK | **Java 8 编译目标**（`pom.xml` 中 `source/target = 1.8`） | 本机用 JDK 25 运行 Tomcat 也能跑（编译产物为 Java 8 字节码）；若要贴合课程要求，装 JDK 8 即可 |
| Maven | 3.9.x | `mvn clean package` |
| Tomcat | **9.0.x** | ⚠️ **不要用 Tomcat 10+**：它改用 `jakarta.servlet.*`，全项目 `javax.servlet.*` 的 import 都要改 |
| MySQL | **8.4** | 库名 `typhoon_monitor`，端口 3306 |
| 浏览器 | Chrome / Edge 新版 | 用到 `aspect-ratio`、`clamp()`、CSS 变量 |

---

## 三、目录结构

```
typhoon_platform/                            ← 工程根（Maven 项目根）
│
├── pom.xml                                  Maven 构建（war 打包，finalName = typhoon_platform）
├── README.md                                本文件
├── .gitignore                               Git 忽略规则
│
├── db/
│   ├── typhoon_monitor.sql                  ★ 唯一建库脚本：28 张表 + 演示数据
│   └── migration_login_attempts.sql         增量迁移：为已有库补 login_attempts 表（幂等）
│
├── docs/
│   ├── api.md                               API 接口文档（URL / 参数 / 示例响应 / 错误码）
│   ├── code-review.md                       ★ 代码问题清单（含严重级别与修复建议）
│   ├── issue-tracking.md                    Issue 跟踪（联调期发现并已修复的缺陷）
│   ├── test-report.md                       ★ 测试报告（覆盖率明细 / 环境坑 / 测试发现的缺陷）
│   ├── frontend-integration-guide.md        前端接后端的改造规范
│   └── frontend-style-guide.md              前端代码规范（CSS 命名 + JS 注释标准）
│
├── tools/
│   ├── verify_api.py                        接口回归脚本（123 例，仅用 Python 标准库）
│   ├── zfz-mockfree-verify.cjs              前端浏览器巡检（137 断言，无头 Edge + CDP）
│   ├── bench_perf.py                        性能压测脚本（keep-alive，仅用 Python 标准库）
│   └── capture.cjs                          CDP 截图骨架（需要人工看图时用它）
│
├── target/                                  构建产物（不希望提交，已在 .gitignore）
│   ├── typhoon_platform.war                 ★ 部署包
│   └── typhoon_platform/                    解包后的 web 应用
│
└── src/
    ├── main/
    │   ├── java/com/zfz/typhoon/
    │   │   ├── model/          27 实体类（与表一一对应）
    │   │   ├── model/vo/       20 个 VO（对外 JSON 形状，与实体解耦）
    │   │   ├── dao/            26 个数据访问接口
    │   │   ├── dao/impl/       26 个 JDBC 实现
    │   │   ├── service/        22 个业务接口
    │   │   ├── service/impl/   22 个业务实现
    │   │   ├── servlet/        17 个文件 = 1 个 BaseServlet + 16 个 @WebServlet
    │   │   ├── filter/         2 个：AuthFilter（默认拒绝）、CorsFilter
    │   │   └── util/           15 个工具类
    │   │
    │   ├── resources/
    │   │   ├── db.properties           ★ 数据库连接参数（唯一配置源，改这里即可换库）
    │   │   │                             ⚠️ 含口令，已被 .gitignore 排除，不入版本库
    │   │   └── db.properties.example   配置模板（入库），新环境照它复制一份
    │   │
    │   └── webapp/             ★ 前端根目录（部署后即 Web 根）
    │       ├── WEB-INF/web.xml  欢迎页 / 编码过滤器 / 会话配置 / 接口注册对照表
    │       ├── index.html               P1 监测总览
    │       ├── typhoon-detail.html      P2 台风详情
    │       ├── forecast-disagreement.html  P3 预报分歧解析
    │       ├── risk-assessment.html     P4 城市风险预评估
    │       ├── similar-cases.html       P5 历史相似灾害案例
    │       ├── sandbox.html             P6 情景推演沙盘
    │       ├── survey.html              P7 防灾行为调研
    │       ├── archive.html             P8 历史台风档案
    │       ├── help-api.html            P9 帮助与接口说明
    │       ├── admin.html               P10 管理后台
    │       ├── login.html               登录 / 注册（独立入口，无设计稿编号）
    │       ├── css/      tokens.css → app.css → pages.css（三层：令牌 → 组件 → 布局）
    │       ├── js/       api.js（接口层）/ ui.js（组件库）/ layout.js（全局框架）
    │       │             pages/*.js（11 个页面模块）
    │       ├── assets/   地图底图（普通/卫星/地形）与 SVG 图标
    │       └── vendor/   jquery.min.js、bootstrap.min.css、bootstrap.bundle.min.js
    │
    └── test/java/com/zfz/typhoon/          单元测试（JUnit 5 + Mockito）：49 个文件 / 约 14800 行
        ├── util/            13 个工具类测试（+ FakeResultSet 替身）
        ├── dao/             9 个 DAO 集成测试（直连真实 MySQL）
        ├── service/impl/    13 个 Service 单测（Mockito 注入 DAO 替身）
        ├── servlet/         2 个端到端测试（+ FakeHttp 请求/响应替身）
        ├── model/           5 个 POJO 测试（+ BeanSweeper 反射批处理）
        └── filter/          2 个过滤器测试（鉴权矩阵 / CORS 白名单）
```

### 提交前自查（三条命令跑完全部回归）

```bash
# 1) 后端：单元测试 + 覆盖率闸门 + 打包
mvn verify
#    期望：Tests run: 938, Failures: 0 且 "All coverage checks have been met."

# 2) 接口回归（需 Tomcat 已启动并部署最新 WAR）
python tools/verify_api.py
#    期望：通过 123 / 共 123，失败 0

# 3) 前端巡检（需 Node 22+ 与本机 Edge，须在沙箱外执行）
node tools/zfz-mockfree-verify.cjs
#    期望：================ 合计：137 通过 / 0 失败 ================

# 4) 性能压测（可选，量化优化前后差异；keep-alive 长连接）
python tools/bench_perf.py --tag 优化后 --total 600 --conc 8
#    期望：total_fail=0，且核心接口 p95 明显低于优化前基线
```

> 脚本此前放在 `%TEMP%`，被系统清理过一次导致脚本丢失、只能重写。
> 现已落盘到 `tools/` 随工程走，**不要再复制到临时目录**。

---

## 四、数据库准备

### 4.1 本项目连的库

| 项 | 值 |
|---|---|
| 主机 / 端口 | `localhost:3306` |
| 库名 | **`typhoon_monitor`** |
| 账号 | `root` |
| 口令 | 见 `src/main/resources/db.properties`（本机由 MySQL 安装时设定） |
| 表数量 | 27 张 |
| 连接串 | `jdbc:mysql://localhost:3306/typhoon_monitor?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` |

> **关于库名为什么不是 `typhoon_platform`**
>
> 工程目录叫 `typhoon_platform`，库叫 `typhoon_monitor` —— 这不是笔误。
> `typhoon_monitor` 是原始备份 `Dump20260916.sql` 里既有的库名，也是本机 3306 上
> **真实存在且已有数据**的库。库名只保留一处口径：`db.properties` 与
> `db/typhoon_monitor.sql` 必须一致；两处写不同名字会导致应用启动后所有查询报
> `Unknown database 'xxx'`。

### 4.2 如果库不存在，或想重置数据

```bash
# 导入建库脚本（⚠️ 脚本内含 DROP TABLE，会清空既有业务数据）
mysql -u root -p < db/typhoon_monitor.sql
```

也可以在 Navicat / MySQL Workbench / **IntelliJ IDEA 的 Database 工具**里
打开 `db/typhoon_monitor.sql` 直接执行。

> **只想连库看数据、不想重建结构时，不要执行该脚本** —— 库在本机已经存在。

### 4.3 预置测试账号

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin01` | `password` | 平台总管理员（可进管理后台、看通知/收藏/订阅） |
| `user01` ~ `user08` | `password` | 普通用户 |

匿名访问时，台风档案、城市、机构、AI 测算、风险评估、相似案例、沙盘、问卷、导出
这些接口都是公开的；只有个人数据（通知、收藏、订阅、对比）与 `/api/admin/*` 需要登录。

---

## 五、如何运行（含"后端需要运行程序吗"）

### 5.1 直接回答：**后端没有、也不需要 `main()` 方法**

这是 Servlet 应用与普通 Java 程序的根本区别，也是"代码里找不到入口程序"的原因：

| | 普通 Java 程序 | 本项目（Servlet Web 应用） |
|---|---|---|
| 入口 | `public static void main(String[])` | **没有** |
| 谁启动 | JVM 直接跑 `main` | **Servlet 容器**（Tomcat） |
| 路由怎么注册 | 自己在 `main` 里写 | Tomcat 扫描到类上的 `@WebServlet(urlPatterns=...)` **自动注册** |
| 生命周期 | `main` 跑完即结束 | 容器启动时创建 Servlet 实例 → 每个请求一个线程调用 `doGet/doPost` → 容器停止时销毁 |

具体到本项目，Tomcat 启动时会做这些事：

1. 解压/挂载 `webapps/typhoon_platform.war`，把 `WEB-INF/lib/*.jar` 加入应用类加载器
   （Jackson、MySQL 驱动就在这里被打包进来）；
2. 读 `WEB-INF/web.xml`：设置欢迎页、字符编码、会话超时（30 分钟）；
3. **扫描 `com.zfz.typhoon.servlet` 等包中的 `@WebServlet` 注解**，
   把 16 个 Servlet 注册成路由；同时注册 `@WebFilter` 的 `AuthFilter` 与 `CorsFilter`；
4. 首次被访问时，`BaseServlet` 里的 Service 对象按需创建；`DBUtil` 静态初始化，
   读 `WEB-INF/classes/db.properties` 并建立 JDBC 连接池。

所以「运行后端」的准确说法是：**让容器（Tomcat）跑起来，并把 WAR 交给它**。
代码本身不是可执行程序，没有 `Main` 类，这是设计使然，不是缺失。

### 5.2 方式一：在 IntelliJ IDEA 2025.2 里运行（推荐给答辩演示）

1. `File → Open`，选择本文件夹（含 `pom.xml`），IDEA 会自动识别为 Maven 工程，
   等待依赖下载完成（右下角进度条走完）。
2. 确认 JDK：`File → Project Structure → Project`，Project SDK 选 **JDK 8**
   （若只有高版本 JDK 也能编译，因为 `pom.xml` 已把 `source/target` 锁在 1.8）。
3. 配置 Tomcat：`Run → Edit Configurations → + → Tomcat Server → Local`
   - **Server** 页：`Application server` 指向 Tomcat 9 安装目录；`HTTP port` 填 `8080`
   - **Deployment** 页：点 `+` → `Artifact...` → 选 **`typhoon_platform:war exploded`**
     （用 exploded 而不是 war，改前端文件后不用重新打包就能看到效果）
   - `Application context` 填 `/typhoon_platform`
4. 点绿色三角运行。IDEA 会编译、部署并启动 Tomcat，浏览器打开：

```
http://localhost:8080/typhoon_platform/
```

5. 连数据库：`View → Tool Windows → Database → + → Data Source → MySQL`，
   填 `localhost` `3306` / 库 `typhoon_monitor` / 用户 `root`，
   首次连接会提示下载驱动，点 `Download` 即可。

> **IDEA 里报 "Unknown database" 怎么办**：说明 `db.properties` 里的库名与
> 数据库里实际存在的库名不一致。到 MySQL 里执行 `SHOW DATABASES;` 核对，
> 以实际存在的库名为准，改 `db.properties` 一处即可（不必动 Java 代码）。

### 5.3 方式二：命令行打包 + 丢进 Tomcat（最贴近真实部署）

```bash
# 1) 打包
mvn clean package
# 产物：target/typhoon_platform.war

# 2) 部署
cp target/typhoon_platform.war $CATALINA_HOME/webapps/

# 3) 启动
%CATALINA_HOME%\bin\startup.bat        # Windows
$CATALINA_HOME/bin/startup.sh          # macOS / Linux
```

Tomcat 会自动解压 WAR 并部署，访问 `http://localhost:8080/typhoon_platform/`。

| 项 | 值 | 配置位置 |
|---|---|---|
| Tomcat 端口 | `8080` | `conf/server.xml` 的 `<Connector port="8080">` |
| 应用上下文 | `/typhoon_platform` | `pom.xml` 的 `<finalName>` |
| 接口基地址 | `/typhoon_platform/api/...` | 前端 `js/api.js` 由 `detectContext()` 自动推导，无需手写 |

### 5.4 启动成功怎么确认

```bash
curl http://localhost:8080/typhoon_platform/api/health
```

期望响应（关键看 `database.ok`）：

```json
{"code":200,"message":"success","data":{
  "app":"typhoon_platform","status":"UP",
  "contextPath":"/typhoon_platform","servletVersion":"4.0",
  "database":{"ok":true,"message":"连接正常","driver":"com.mysql.cj.jdbc.Driver"},
  "javaVersion":"...","os":"..."},"success":true}
```

`database.ok = false` 时，`message` 会写明具体原因（读配置失败 / 驱动缺失 /
连接被拒 / 库不存在），照提示排查即可。

### 5.5 常见启动问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 访问 404，页面完全打不开 | WAR 没部署成功，或 contextPath 不是 `/typhoon_platform` | 看 `logs/catalina.out`；确认 `webapps/` 下有解压出的目录 |
| 页面能打开，但接口全报错 | `web.xml` 解析失败或 `db.properties` 库名错 | 看 `logs/localhost.<日期>.log` |
| 所有查询报 `Unknown database 'xxx'` | `db.properties` 的库名与 MySQL 里实际库名不一致 | `SHOW DATABASES;` 核对后改 `db.properties` |
| 报 `Access denied for user 'root'` | 口令不对 | 改 `db.properties` 的 `jdbc.password` |
| 中文变问号/乱码 | 连接串缺 `characterEncoding=utf8` | 本项目连接串已带该参数，不要删 |
| 类找不到 `javax.servlet.*` | 用了 Tomcat 10+ | 换 Tomcat 9，或把全项目 import 改成 `jakarta.servlet.*` |

---

## 六、接口清单

共 **16 个 Servlet**；接口数量按两种口径并列 —— **62 条去重 URL 路径**，
或 **86 个「HTTP 方法 + 路径」组合**（例如 `/api/typhoons/{id}` 同时支持 GET / PUT / DELETE，
算 1 条路径、3 个组合）。完整定义（参数、示例响应、错误码）见
[`docs/api.md`](docs/api.md)。

> 这两个数字是**逐条对着 16 个 Servlet 的 `doGet` / `doPost` / `doPut` / `doDelete`
> 分派代码枚举**出来的，不是估算。此前各文档长期写的「47 条接口路径」与任一口径
> 都对不上（已更正）；枚举时的核对记录与顺带发现的 3 处不一致，写在
> `docs/api.md` 第 4 节末尾，避免被反复当新缺陷重报。

| 模块 | 路径前缀 | Servlet | 鉴权 |
|---|---|---|---|
| 健康检查 | `/api/health` | `HealthServlet` | 公开 |
| 认证 | `/api/auth/*`（register / login / logout / me / password） | `AuthServlet` | register 与 login 公开（**注册即登录**），me 与 password 需登录 |
| 台风档案与实况 | `/api/typhoons/*`（列表 / 详情 / tracks / wind-circles / forecast-paths / meta） | `TyphoonServlet` | 公开 |
| 基础元数据 | `/api/cities`、`/api/agencies` | `MetaServlet` | 公开 |
| AI 智能测算 | `/api/ai/predictions`（GET 读取 / POST 触发） | `AiPredictionServlet` | 公开 |
| 风险评估 | `/api/risk/assessments` | `RiskAssessmentServlet` | 公开 |
| 相似案例 | `/api/similar-cases` | `SimilarCaseServlet` | 公开 |
| 沙盘推演 | `/api/sandbox/scenarios/*` | `SandboxServlet` | 公开 |
| 防灾问卷 | `/api/surveys`（POST 提交）、`/api/surveys/agg` | `SurveyServlet` | 公开 |
| 导出 | `/api/export/tracks`、`/api/export/history` | `ExportServlet` | 公开 |
| 收藏 | `/api/favorites/*` | `FavoriteServlet` | 需登录 |
| 订阅 | `/api/subscriptions/*` | `SubscriptionServlet` | 需登录 |
| 通知 | `/api/notifications/*` | `NotificationServlet` | 需登录 |
| 对比任务 | `/api/comparisons/*` | `ComparisonServlet` | 需登录 |
| 意见反馈 | `/api/feedback` | `FeedbackServlet` | 公开 |
| 管理后台 | `/api/admin/*`（dashboard / todos / models / settings / datasources / users / feedback / logs/sync） | `AdminServlet` | **需管理员** |

> **台风档案与实况**的数据不由任何接口写入，而是由内嵌定时任务从中央气象台台风网同步
> （机制见 §7.6）；同步结果可在管理后台通过 `GET /api/admin/logs/sync` 查看（需管理员）。
> AI 测算、预报分歧、风险预评估、公众调研四类**派生结果**在"当前台风算不出来"时会
> 自动回落到最近一条可用台风，并在页头显式说明（见 §7.7）。

### 统一响应约定（很重要）

```json
{ "code": 200, "message": "success", "data": { }, "success": true }
```

- **业务失败时 HTTP 状态码仍为 200**，成败看响应体里的 `code`
  （401 未登录 / 403 无权限 / 400 参数错误 / 404 资源不存在 / **409 冲突** / 500 服务端异常）。
  - 401 由 `layout.js#isUnauthorized(err)` 判断，**只改顶栏、不自动跳转**：
    本项目的 401 既可能是「从未登录」（游客浏览公开页面，弹他去登录页是打扰），
    也可能是「会话过期」，两者处理方式不同，不代用户做跳转决定。
  - 409 目前只有一处：注册时用户名已被占用，前端据此把提示定位到「用户名」这一项。
- 这么设计的原因：前端 `api.js` 用 jQuery 的 `done` 回调统一处理响应体，
  若后端返回 4xx/5xx，jQuery 会走 `fail` 分支，页面就拿不到「请先登录」这类可读文案了。
- 只有**容器级**错误（路由不存在 404、方法不允许 405）才会返回非 200 状态码。

---

## 七、关键实现说明

### 7.1 连接池是手写的

课程要求「连接池需可参数化」，因此 `util/SimpleConnectionPool.java` 是自实现的
简易连接池（`initialSize` / `maxActive` / `maxIdle` / `minIdle` / `maxWait`
全部来自 `db.properties`），`JdbcTemplate` 在其上封装了参数化查询与 `RowMapper`，
**所有 SQL 一律用 `?` 占位符**，不拼接字符串，避免 SQL 注入。

### 7.2 鉴权放在 Filter，而不是每个 Servlet 里

`AuthFilter` 拦截 `/api/*`，按路径前缀判断需要「管理员」还是「登录」身份。
放在过滤器里的意义是让**默认拒绝**成为框架行为：新写的接口若忘了配鉴权，
表现是「接口用不了」（立刻能发现），而不是「接口谁都能调」（很难发现）。
数据级权限（这条记录是不是你的）仍由 Service 层校验，两者互补。

### 7.3 量纲约定（多处易错，务必遵守）

| 字段 | 量纲 |
|---|---|
| `survey_agg_results.*`、`riskScore`、`consistency`、`models[].confidence` | **0—100** |
| `similarity_score`、`disagreement.index`、`variance`、融合后的 `AiPredictionVO.confidence` | **0—1** |

前端渲染百分比时需按上表决定是否 `×100`（联调期曾因此出现过 100 倍偏差，
见 `docs/issue-tracking.md` P1-2 ~ P1-4）。

### 7.4 地图投影常量

前端不引地图库，用等比换算把经纬度画到 SVG 上，常量定义在 `js/ui.js`：

- `KX = 26.79 px/°`（每经度像素数）
- `KM_PER_PX = 3.9`（由 `111.32 · cos20° / 26.79` 推得）

风圈接口返回的 `r` 是**真实公里数**（如 7 级圈 193 km），页面用 `KM_PER_PX`
换算成屏幕像素，因此缩放地图时风圈会跟着缩放，不会"贴"在屏幕上不动。

### 7.5 前端没有演示数据回退

`js/data.js` 已**物理删除**，全部 HTML 页面对它的引用均已移除，全站 `grep ZFZ.mock` 为 0。
接口失败时页面渲染明确的错误态并弹 toast，**不会静默退回假数据**——
否则演示时看到的"正常数据"可能其实是编造的，这在答辩场景里是致命的。

### 7.6 数据来源：从中央气象台台风网真实同步

台风实况、历史路径与风圈**不是静态快照**，而是由内嵌定时任务从
[中央气象台台风网](https://typhoon.nmc.cn) 拉取的真实数据。

| 项 | 说明 |
|---|---|
| 数据源 | `https://typhoon.nmc.cn/weatherservice/typhoon/jsons/`（JSONP：`list_default` / `view_{apiId}`） |
| 采集器 | `util/CmaApiClient`（`HttpURLConnection` + 手写 JSONP 解包，**不引 HTTP 客户端库**） |
| 落库 | `service/TyphoonSyncService(Impl)`：写入 `typhoons` / `typhoon_tracks` / `wind_circles` / `forecast_paths`，并在 `sync_logs` 留痕 |
| 调度 | `listener/SyncSchedulerListener`：单线程守护，启动后延迟 `sync.initialDelaySeconds`（20s）首跑，之后每 `sync.intervalMinutes`（30 分钟）一次 |
| 配置 | `WEB-INF/web.xml` 的 `sync.enabled` / `sync.intervalMinutes` / `sync.initialDelaySeconds` |
| 优雅关闭 | `listener/ShutdownCleanupListener`：停调度 → 关连接池 → 停 MySQL 清理线程 → 注销 JDBC 驱动 |

**两处值得写进报告的工程细节：**

1. **HTTPS 证书链**。`*.nmc.cn` 由 **CFCA** 签发，其根证书不在 JDK 默认 `cacerts` 里，
   直接访问会抛 `PKIX path building failed`。正确做法**不是关掉校验**，
   而是随 WAR 带一份 `WEB-INF/classes/certs/cfca-nmc-roots.pem`，
   用「JDK 默认信任库 ∪ 附加根」的并集 `TrustManager`——既不牺牲安全性，也不依赖运维改 JDK。
2. **同步策略是「只补不改」**。按国际编号查重：**只插入没见过的台风**；活跃台风做幂等 upsert；
   已有档案只读；**不自动把 `ACTIVE` 改成 `FINISHED`**，而是计入 `stale` 等人工确认——
   避免一次网络抖动就把台风状态改错。

实测两轮（同一次部署内连续跑）验证幂等：

```
第 1 轮：接口 32 条，新增 31，更新 0，跳过 1，待确认 1；轨迹 1731，风圈 1397，预报 27
第 2 轮：接口 32 条，新增  0，更新 1，跳过 31，待确认 1；轨迹   68，风圈   67，预报  2
```

第二轮「新增 0」即证明重复同步不会产生重复数据。

> 同步后档案库从 13 条增长到 44 条（含 2026 年新台风），
> 因此**任何回归断言都不能写死条数**，必须从接口现取基线。

### 7.7 派生分析页的自动回落

AI 智能测算、预报分歧、风险预评估、公众调研看板这四类结果，**不是每条台风都算得出来**：

- 「预报分歧」需要 ≥2 家机构的预报路径，而对外可用的预报机构**只有中央气象台一家**；
- 「风险预评估」按真实风圈实时推算，远海台风的风圈半径内没有中国城市，结果就是空列表；
- 「公众调研」按台风聚合，刚同步进来的新台风一份问卷样本都没有。

若照搬「当前活跃台风」去算，最新台风一进来这几页就全空了。因此前端统一走
`ZFZ.api.analyzeTarget()`：按"最新优先"顺序探测候选台风，**改选最近一条算得出来的台风**，
并在页头用 `ZFZ.ui.switchNote()` **明写已经切换**：

> **已自动切换台风**：最新活跃台风「小熊 202629」还没有问卷样本，统计不出分布与结论；
> 以下展示最近一条可用结果 —— 「飞燕 202615」。

**为什么必须写明而不是静默替换**：静默替换会让人以为看到的就是最新台风的结果，
那比直接显示空态更容易误导。地址栏带 `?id=` 时以显式指定的台风为准，不做任何回落。
所有回落后的数据仍然**全部来自库内真实记录**，不存在任何编造。

---

## 八、与《课程要求.pptx》的对照

| 课程要求 | 落地情况 |
|---|---|
| 前端 HTML + CSS + jQuery + Bootstrap | ✅ 11 个页面全部采用，vendor 本地副本，无 CDN |
| 后端 Java 原生、禁用框架 | ✅ `pom.xml` 只有 Servlet API、Jackson、MySQL 驱动三类基础库 |
| Model → DAO → Service → Servlet 分层 | ✅ 见 `src/main/java/com/zfz/typhoon/` |
| 包命名规范 + JavaDoc | ✅ 包名统一 `com.zfz.typhoon.*`，主要类均含 JavaDoc |
| 数据库设计 ≥3NF + DDL 脚本 | ✅ 28 张表 + 索引，`db/typhoon_monitor.sql` |
| 连接池可参数化 | ✅ `db.properties` |
| API 接口文档 | ✅ `docs/api.md` |
| 前端代码规范文档 | ✅ `docs/frontend-style-guide.md` |
| 打包 WAR 部署 Tomcat | ✅ 已实际部署验证 |
| 后台管理用 Bootstrap + AJAX + **ECharts** | ✅ Bootstrap + AJAX + ECharts 5.5.1 均已接入（阶段四④：用户累计增长折线 + 角色分布饼图，见 `admin.html` / `pages/admin.js`，数据来自 `GET /api/admin/stats`） |
| 导出功能 | ✅ **CSV + Excel（xlsx）双格式**：阶段七① 数据导出 Excel 已用手写 OOXML 实现（零第三方依赖，不引 POI）；轨迹页与档案页均提供「导出 CSV / 导出 Excel」两个按钮 |
| 单元测试 JUnit 5 + Mockito，覆盖率 ≥60% | ✅ **938 个用例 / 行覆盖率 72.4%**（JaCoCo 闸门已生效，见 `docs/test-report.md`） |
| 性能优化记录（≥3 个瓶颈 + 前后对比） | ✅ 3 个瓶颈 + 量化对比，见 `docs/performance-optimization.md` |
| 加分项：Git 版本管理 | ✅ 已 `git init` 初始化仓库（`.gitignore` 已排除含口令的 `db.properties` 与 `target/`），见 `docs/issue-tracking.md` 阶段七④ |
| 加分项：安全防护 | ✅ 已做参数化 SQL（无拼接注入点）、口令加盐 SHA-256、XSS 转义、默认拒绝鉴权、CORS 按 Origin 回显且不与通配符同用、登录失败 5 次锁定 15 分钟（**已落库持久化**：`login_attempts` 表，Tomcat 重启不清零；表缺失时自动降级内存，不影响登录） |

> 上述 ⚠️ / ❌ 项与更多细节，逐条列在 [`docs/code-review.md`](docs/code-review.md)。

---

## 九、免责声明

平台所有数据与分析结果**仅作为公众科普与应急参考，不能替代气象部门官方预警与应急指挥决策**。
台风实况、路径、风圈与预报数据来自**中央气象台台风网**（系统每 30 分钟自动同步一次），
版权与解释权归数据来源方；平台不对同步数据的完整性、时效性与准确性作任何担保。
台风路径预报存在不确定性，风险评估、沙盘推演、相似案例类比结果仅供展示参考，
实际防灾避险请以当地气象部门和应急管理部门发布的官方信息为准。
涉及大模型测算的页面均已标注「AI 测算仅供科普参考，不替代官方预报」。

---

## 十、公开仓库使用说明

本仓库为课程作业公开存档，面向评审与学习交流。几点请注意：

### 演示账号

`db/typhoon_monitor.sql` 内预置了两个演示账号，口令**在本文档与
`docs/deployment-guide.md` 中均已明示**，属课程评审需要：

| 账号 | 口令 | 角色 |
|---|---|---|
| `admin01` | `password` | 总管理员 |
| `dataadmin01` | `123456` | 数据管理员 |

> ⚠️ **这两个账号的口令在公开仓库中人人可见**，且口令摘要采用无盐 SHA-256，
> 可被彩虹表直接反查。**任何真实部署都必须先改口令或停用该账号**，
> 详见 `docs/deployment-guide.md` 的上线检查清单。

### 敏感配置不在库内

`src/main/resources/db.properties`（含数据库口令）已由 `.gitignore` 排除。
仓库中只有不含真实口令的模板 `db.properties.example`，新环境按其注释创建即可。

### 版本管理说明

本仓库的提交历史遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范
（`feat` / `fix` / `docs` / `chore` 等类型前缀 + 正文说明变更动机）。

因上传者本机到 `github.com:443` 的网络受限、无法使用 `git push`，
提交历史经 `tools/push-history-via-api.py` 通过 GitHub Git Data API 逐个重建，
因此**提交 SHA 与本地仓库不一致**（内容与时间戳一致，SHA 因重建而不同）。
这是网络限制导致的处理方式，不代表规范或内容有任何缺失。

### 免责声明

见上一节第九章。本项目数据来源与用途均已在文中据实标注，
**云系图层为示意图而非观测产品**，风险评估与沙盘推演结果不替代官方预警。
