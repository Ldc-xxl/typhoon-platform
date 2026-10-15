# 追风者 · API 接口文档

> 适用范围：前端 `src/main/webapp/js/api.js` 已按本清单调用，后端只需保证
> URL、请求方法与响应结构一致，**前端无需改动任何代码**。
>
> 版本：v1.0 ｜ 约定：RESTful 风格，请求与响应均为 `application/json;charset=UTF-8`
>
> 课程要求对应：阶段三「业务逻辑与 Servlet 开发」④ 编写 API 接口文档（URL、请求方法、
> 参数说明、示例响应、错误码定义）。

---

## 1. 通用约定

### 1.1 服务地址

| 环境 | 基地址 |
|---|---|
| 本地开发（Tomcat 8080 + WAR 部署） | `http://localhost:8080/typhoon_platform` |
| 前端接口层默认值 | 留空，由 `detectContext()` 按当前页面地址自动推导 |

前端切换方式（仅此两行）：

```js
ZFZ.api.config.useMock = false;
ZFZ.api.config.baseUrl = 'http://localhost:8080/typhoon_platform';   // 同源部署可省略
```

### 1.2 统一响应格式

所有接口一律返回如下结构（由后端 `Result` 封装类 + Jackson 输出）：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

- 前端判定成功：`code === 200 || code === 0`，此时从 `data` 字段取值。
- 判定失败：`message` 中的文案会作为 `Error` 抛出，供页面统一 toast 提示。

### 1.3 错误码定义

| code | 含义 | 前端表现 |
|---|---|---|
| 200 | 成功 | 正常渲染 |
| 400 | 参数校验失败（缺失、格式错误） | toast 提示 message 文案 |
| 401 | 未登录或会话过期 | 顶栏切换为「未登录」，用户菜单只留「登录 / 注册」；**不自动跳转**，由用户自己点进 `login.html` |
| 403 | 已登录但无权限（非管理员访问后台接口） | toast「无访问权限」 |
| 404 | 资源不存在（台风 id 不存在等） | 空态提示 |
| 409 | 冲突（注册时用户名已被占用） | 前端把提示定位到「用户名」这一项，而不是笼统的参数错误 |
| 500 | 服务器内部错误（SQL 异常等） | toast「服务异常，请稍后重试」 |

> 401 的「不自动跳转」是**刻意的**：本项目的 401 既可能是「从未登录」（游客浏览公开页面，
> 此时把他弹去登录页是打扰），也可能是「会话过期」。两者的前端处理不同，所以统一由
> `layout.js#isUnauthorized(err)` 判断并只改顶栏，**不代用户做跳转决定**。
> （早期版本这里写的是「项目无独立登录页」，那只是当时没有登录页的事实描述，现已补上
> `login.html` —— 但「不自动跳转」这条行为约定没有变。）

> 后端约定：`Service` 层抛出的业务异常由 Servlet 统一捕获，
> 转换为上述错误码，**Servlet 内不写业务逻辑**（课程评审要点）。

### 1.4 量纲总表（易错，务必遵守）

本项目**同时存在 0—1 与 0—100 两套比例量纲**，取错一个就会放大 100 倍。
联调期已因此出过三次缺陷（见 `docs/issue-tracking.md` P1-2 / P1-3 / P1-4），
故在此固化唯一权威口径，**新增接口必须照此归类并在 VO 字段 JavaDoc 上标注量纲**。

| 字段 / 接口 | 量纲 | 前端渲染要点 |
|---|---|---|
| `GET /api/surveys/agg` 的 `suppliesRate` / `evacuationRate` / `highTrustRate` / `overestimationRate` / `underestimationRate` / `concernDist.*` / `cityBreakdown.*.xxxRate` | **0—100** | 直接拼 `%` |
| `GET /api/risk/assessments` 的 `riskScore` | **0—100** | 直接拼 `%`，或按区间映射风险等级 |
| `GET /api/ai/predictions` 的 `consistency`（多模型一致性） | **0—100** | 直接拼 `%` |
| `GET /api/ai/predictions` 的 `models[].confidence`（**单个模型**置信度） | **0—100** | 直接拼 `%` |
| `GET /api/admin/models` 的 `confidence`（后台模型平均置信度） | **0—100** | 已是带 `%` 的字符串，原样展示 |
| `GET /api/similar-cases` 的 `score`、`matchDims.*` | **0—1** | **×100** 后再拼 `%` |
| `GET /api/typhoons/{id}/disagreement` 的 `index` | **0—1** | **×100** 后再拼 `%` |
| `GET /api/typhoons/{id}/disagreement` 的 `dimensions[].variance` | **0—1** | **×100** 后再拼 `%` |
| `GET /api/ai/predictions` 的 `confidence`（**融合结果**置信度） | **0—1** | **×100** 后再拼 `%` |

> ⚠️ **最容易踩的一处**：`models[].confidence`（单模型，0—100）与
> `confidence`（融合结果，0—1）**同名不同量纲**。取用时务必看清楚取的是哪一层。
> 对应 VO：`AiPredictionVO.ModelItem#confidence` 与 `AiPredictionVO#confidence`，
> 两者的 JavaDoc 均已标注量纲与区别。

### 1.5 身份取用规则（项目级强制约定）

**涉及个人数据的查询，身份一律取自会话（HttpSession），禁止从请求参数取 `userId`。**

- 服务端用 `BaseServlet#requireUserId(req)` 取当前登录用户，**不要**写
  `req.getParameter("userId")` 之类的写法。URL 里出现 `userId` 意味着
  任何人改一下地址栏就能读到别人的数据（IDOR 越权）。
- 需要"只看自己的数据"时，用语义化开关表达，例如沙盘列表的 `mine=1`；
  服务端收到该开关后取会话身份，而不是取参数里的数字。
- 管理端跨用户查询必须显式走 `/api/admin/**`，由 `AuthFilter` 的管理员分支拦截，
  不允许散落在业务接口里。

> 背景：`/api/sandbox/scenarios` 曾接受 URL 参数 `userId`，匿名即可枚举他人未公开的
> 方案；`/api/export/history` 则一直是"只认会话"的正确口径。两种姿态并存本身就是隐患
> （后人复制哪个都有先例），故在此固化唯一规则。

### 1.6 资源标识的字段命名约定

**同一个资源在正文与 URL 里必须是同一个名字。** 现有两类情况，取用时先看清是哪一类：

| 接口类型 | 标识字段名 | 例子 |
|---|---|---|
| **新建**（POST） | `{资源}Id` | `typhoonId`、`surveyId`、`feedbackId`、`subscriptionId`、`favoriteId`、`logId`、`userId` |
| **列表 / 详情**（GET） | `id` | 直出 VO 对象（`TyphoonVO.id`、`CityVO.id`、`UserVO` 见下） |

也就是说：**先建后用**的调用链里，同一个资源的标识会出现两个名字：

```js
// 新建返回 { data: { typhoonId: 13 } }
ZFZ.api.createTyphoon(payload).then(function (d) { return d.typhoonId; });
// 而列表项里叫 id
ZFZ.api.getTyphoons().then(function (list) { return list[0].id; });
```

> ⚠️ **取错名字是静默的 `null`** —— 不会报错、不会 4xx，页面只是空白。
> 校验脚本已因此踩过一次（建成后按 `id` 取，拿到 `undefined`）。

**用户资源是已统一的一个**：`/api/admin/users`、`/api/admin/users/{userId}`、
`/api/auth/me` 里的用户对象，标识一律是 `userId`（与新建接口、URL 路径变量同名）。
实现上 `UserVO.getId()` 挂了 `@JsonProperty("userId")`，
只改对外名、不动 Java 侧（Java 字段仍是 `id`，与其它 VO 一致）。

> 其余资源**目前仍是上述两套命名**。把它们全部统一成一套属于**破坏性改动**
> （会同时打断所有读取列表项 `id` 的前端渲染），故本期只统一了用户资源，
> 并在本表固化口径 —— 这不是缺陷，是**已知取舍**，复核时不必再报。

---

## 2. 接口清单总览

| # | 方法 | URL | 说明 | 前端调用处 |
|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 用户登录（管理员与普通用户同一入口） | 登录页 |
| 2 | POST | `/api/auth/register` | 用户注册（**注册即登录**） | 登录页 |
| 3 | GET | `/api/typhoons` | 台风列表（可按条件筛选） | P1 / P5 / P8 |
| 4 | GET | `/api/typhoons/{id}` | 单个台风基础信息 | P2 |
| 5 | GET | `/api/typhoons/{id}/tracks` | 实况轨迹点序列 | P1 / P2 / P4 |
| 6 | GET | `/api/typhoons/{id}/wind-circles` | 7/10/12 级风圈半径 | P1 / P2 |
| 7 | GET | `/api/typhoons/{id}/forecast-paths` | 多机构预报路径 | P1 / P2 / P3 |
| 8 | GET | `/api/typhoons/{id}/disagreement` | 预报分歧解析结果 | P3 |
| 9 | GET | `/api/ai/predictions` | AI 路径智能测算结果 | P2 / P3 |
| 10 | POST | `/api/ai/predictions` | 触发一次新的 AI 测算 | P2 |
| 11 | GET | `/api/risk/assessments` | 城市风险预评估 | P4 |
| 12 | GET | `/api/similar-cases` | 历史相似台风 + 灾情记录 | P5 |
| 13 | GET | `/api/sandbox/scenarios` | 沙盘情景列表 | P6 |
| 14 | POST | `/api/sandbox/scenarios` | 保存沙盘情景 | P6 |
| 15 | POST | `/api/surveys` | 提交防灾行为问卷 | P7 |
| 16 | GET | `/api/surveys/agg` | 问卷聚合统计 | P7 |
| 17 | GET | `/api/export/tracks` | 导出轨迹 CSV / Excel（xlsx） | P2 / P5 |
| 18 | GET | `/api/admin/stats` | 后台统计图表数据（ECharts 用：用户累计增长 + 角色分布） | P10 |
| 19 | * | `/api/admin/*` | 管理后台：dashboard / todos / models / settings / datasources / users / feedback / logs/sync / stats（均已接真实后端） | P10 |

> 页面代号（与 `README.md` 目录树、各 `js/pages/*.js` 模块头注释一致）：
> P1 监测总览 / P2 台风详情 / P3 预报分歧解析 / P4 城市风险预评估 / P5 历史相似灾害案例 /
> P6 情景推演沙盘 / P7 防灾行为调研 / P8 历史台风档案 / P9 帮助与接口说明 / P10 管理后台。
> 登录页 `login.html` 是独立入口，不带 P 编号。
>
> 「前端调用处」只标**主要消费方**；同一接口常被多页共用（例如 `GET /api/typhoons`
> 被 P1 总览、P5 相似案例、P8 档案检索同时使用）。

---

## 3. 接口详情

### 3.1 用户登录

```
POST /api/auth/login
```

管理员与普通用户**共用同一个入口**，登录后由响应里的 `role` 区分身份
（`ADMIN` 可访问 `/api/admin/**`，`USER` 不可）。

**请求体**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| password | string | 是 | 明文口令（传输层之外不做处理；后端只存加盐摘要，明文不入库） |

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": {
    "token": "…", "role": "ADMIN", "roleText": "管理员",
    "userName": "总管理员", "account": "admin01", "userId": 1,
    "avatarText": "总", "expiresIn": 1800
  } }
```

| 字段 | 说明 |
|---|---|
| `token` | 会话凭据（本项目实际鉴权靠 **HttpSession cookie**，此字段仅为契约完整性保留） |
| `role` / `roleText` | `ADMIN` / `USER` 与对应中文名 |
| `userName` / `avatarText` | 顶栏展示名与头像首字 |
| `account` | 登录名（与 `userName` 可能不同：`userName` 优先取昵称） |
| `userId` | 用户标识（**不是** `id`，见 1.6） |
| `expiresIn` | 会话有效期（秒），**回读**自容器配置，前端不要拿它做本地计时 |

> ⚠️ **登录响应里没有嵌套的 `user` 对象，注册响应里有**（见 3.2）。这是既有的不对称，
> 前端登录后要拿完整用户视图必须再请求一次 `GET /api/auth/me`
> （`js/pages/login.js` 就是这么做的：成功后调 `refreshSession()`）。
> 之所以不顺手补齐：改登录响应形态会牵动既有断言与消费方，收益不抵风险。

**后端对应**：`AuthServlet#doPost` → `AuthService#login` → `AuthServiceImpl#login` → `UserDao#findByUsername`

> 注：不存在 `UserService#login`。认证相关逻辑集中在 `AuthService`，
> `UserService` 只负责用户的增删改查。

---

### 3.2 用户注册

```
POST /api/auth/register
```

**注册即登录**：成功后就地建立会话，返回体与 3.1 **同形**，用户不必注册完再登一次。

**请求体**

| 参数 | 类型 | 必填 | 缺省行为 |
|---|---|---|---|
| username | string | 是 | 3—50 位，仅字母 / 数字 / 下划线（`^[A-Za-z0-9_]{3,50}$`）；两端空白自动去掉 |
| password | string | 是 | 长度 6—64 位 |
| nickname | string | 否 | 空则**回退为登录名**（不能落空串：顶栏与用户表取首字做头像，空昵称会渲染出空白头像） |
| email | string | 否 | 空则落 `null`（不是空串）；填写则须通过基本格式校验，≤ 100 字符 |

**角色不可指定**：请求体里没有 `role` 字段，Jackson 又配置为忽略未知字段，
`AuthServiceImpl#register` 一律强制 `user.setRole(User.ROLE_USER)`。
**不存在「注册时把自己指定成管理员」的入口。**

**成功响应**（HTTP 200，`code: 200`）

```json
{ "code": 200, "message": "success",
  "data": {
    "token": "…", "role": "USER", "roleText": "普通用户",
    "userName": "若风", "account": "ruofeng", "userId": 42,
    "avatarText": "若", "expiresIn": 1800,
    "user": { "userId": 42, "username": "ruofeng", "nickname": "若风", "...": "..." }
  } }
```

扁平字段与 3.1 完全一致，**额外多一个 `user`** —— 其对象形态与 `GET /api/auth/me` 的
`data.user` 相同，这样前端注册完既可直接读扁平字段，也可直接拿嵌套 `user`，
不必再补一次 `/me` 请求。

**失败情形**

| 场景 | code | message |
|---|---|---|
| 用户名为空 / 不合规 | 400 | `用户名不能为空` / `用户名需为 3—50 位字母、数字或下划线` |
| 口令过短 / 过长 | 400 | `密码长度不能少于 6 位` / `密码长度不能超过 64 位` |
| 昵称 / 邮箱过长，邮箱格式错 | 400 | `昵称不能超过 50 个字符` / `邮箱不能超过 100 个字符` / `邮箱格式不正确` |
| 用户名已被占用 | **409** | `用户名已被注册，请换一个` |

> **为什么「用户名已占用」是 409 而不是 400**：前端需要把它定位到「用户名」这一项，
> 而不是当成笼统的参数错误。判据分两层：先 `UserDao#existsUsername` 前置查重
> （能给出可读提示），真正兜底的是 `users.username` 的**唯一键** ——
> `existsUsername` 与 `insert` 之间存在竞态窗口，两个请求同时注册同名账号时
> 都可能在检查处通过，靠数据库唯一键拒绝后一条，再由 `insertNewUser` 把
> `DataAccessException`（SQLState 类别码 `23`）翻译成 409。
> 不翻译的话，用户看到的会是「服务器内部错误」，而真实原因只是名字被抢注了。

**会话处理**：与 3.1 同一套防会话固定写法 —— 先 `invalidate()` 丢弃注册前的会话，
再 `getSession(true)` 新建并写入 `userId` / `role` / `userName` 三个属性。

**后端对应**：`AuthServlet#doPost` → `AuthService#register` → `AuthServiceImpl#register`
→ `UserDao#existsUsername` + `UserDao#insert`

> `AuthServiceImpl` 复用 `UserDao#insert`（与管理端建号同一条路径），
> **没有第二份插入逻辑**；口令一律走 `PasswordUtil.hash`（随机盐格式），
> 不存明文、也不自行拼摘要格式 —— 摘要格式一旦分叉，登录侧的 `verify`
> 与既有测试里写死的哈希串会一起挂掉。

---

### 3.3 台风列表

```
GET /api/typhoons?status=ACTIVE&year=2024&basin=西北太平洋&limit=10&page=2
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | string | 否 | `ACTIVE` 活跃 / `ARCHIVED`（别名 `FINISHED`）已结束 / `ALL`；不传返回全部 |
| year | int | 否 | 生成年份，用于历史档案筛选 |
| basin | string | 否 | 所属海域 |
| keyword（别名 `name`） | string | 否 | 中文名 / 英文名 / 国际编号模糊匹配 |
| minWindForce | int | 否 | 峰值风力下限（含） |
| sort / sortBy | string | 否 | 排序列（白名单校验） |
| order | string | 否 | `asc` / `desc` |
| limit | int | 否 | 每页条数，默认 50，允许区间 `[0, 500]`；`0` 表示不分页 |
| page | int | 否 | 页码，从 **1** 开始；与 `offset` 二选一 |
| offset | int | 否 | 偏移量，从 **0** 开始；`page` 缺省时生效 |

> **`page` 与 `offset` 为什么都收**：只认 `offset` 时，前端按常见的 `page` 语义传参
> 会每次都拿到第一页，而接口**不报错**。这种"静默失效"比 400 更难发现，
> 因此在边界上两种写法都接受。

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": {
    "items": [
      { "id": 13, "code": "2613", "nameCn": "飞燕", "nameEn": "Jebi",
        "status": "ACTIVE", "statusText": "活跃中", "maxWindForce": 14,
        "maxWindSpeed": 42, "pressure": 950, "basin": "西北太平洋",
        "generateTime": "2026-09-13 08:00", "dissipateTime": null }
    ],
    "total": 13,
    "page": 1,
    "limit": 10,
    "pages": 2
  } }
```

**响应头**：`X-Total-Count: 13` 仍然保留（`tools/` 下的校验脚本与既有外部调用方在用它），
但**页面请用 `data.total`** —— 前端 `api.js` 只 resolve 响应体，不把 xhr 暴露给调用方，
响应头在页面里根本读不到。

**前端对应**

| api.js 方法 | 返回 | 用途 |
|---|---|---|
| `getTyphoons(params)` | `Array` | 只要列表的调用方（兼容壳：内部取出 `data.items`），全站 13 处沿用 |
| `getTyphoonsPage(params)` | `{items,total,page,limit,pages}` | 需要分页总数的页面（P8 档案检索） |

> ⚠️ **不要用 `getTyphoons()` 的数组长度当总数**：那只是"当前页条数"，
> 翻页后长度不变，会把分页算成永远只有一页。要总数就用 `getTyphoonsPage()`。

**后端对应**：`TyphoonServlet#doGet`（`seg.length == 0` 分支）→
`TyphoonService#list` + `#count` → `TyphoonDao`

---

### 3.4 单个台风基础信息

```
GET /api/typhoons/13
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": { "id": 13, "code": "2613", "nameCn": "飞燕", "nameEn": "Jebi",
            "status": "ACTIVE", "level": "强台风", "maxWind": 14, "windSpeed": 42,
            "pressure": 950, "moveDir": "西北", "moveSpeed": 18,
            "seaArea": "西北太平洋" } }
```

---

### 3.5 实况轨迹

```
GET /api/typhoons/13/tracks
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [
    { "time": "2026-09-16 08:00", "lon": 128.50, "lat": 22.10, "windSpeed": 38, "pressure": 962 },
    { "time": "2026-09-17 08:00", "lon": 126.10, "lat": 23.20, "windSpeed": 40, "pressure": 958 },
    { "time": "2026-09-18 08:00", "lon": 123.80, "lat": 24.50, "windSpeed": 42, "pressure": 950 }
  ] }
```

> **前端渲染约定**：数组按时间升序；页面用等距经纬投影把 `lon/lat`
> 换算为画布像素后连成 `<polyline>`，因此轨迹形状完全由本接口数据决定。

**后端对应**：`TrackServlet#doGet` → `TrackService#listByTyphoon` → `TrackDao`

---

### 3.6 风圈半径

```
GET /api/typhoons/13/wind-circles
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [ { "level": 7,  "ne": 320, "se": 280, "sw": 300, "nw": 260 },
            { "level": 10, "ne": 180, "se": 150, "sw": 160, "nw": 140 },
            { "level": 12, "ne": 90,  "se": 80,  "sw": 85,  "nw": 70  } ] }
```

单位：公里（km），四象限分别给出。

---

### 3.7 多机构预报路径

```
GET /api/typhoons/13/forecast-paths
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [
    { "agency": "CMA",  "name": "中央气象台", "color": "#E60012", "dash": "7 5",
      "landing": "厦门沿海",
      "points": [[123.80, 19.50], [122.00, 20.80], [120.00, 22.60], [118.09, 24.48]] },
    { "agency": "JMA",  "name": "日本气象厅", "color": "#1E6FD9", "dash": "7 5",
      "landing": "泉州沿海", "points": [[123.80, 19.50], [121.60, 21.40], [118.60, 24.87]] },
    { "agency": "JTWC", "name": "美国联合台风警报中心", "color": "#E8830C", "dash": "7 5",
      "landing": "汕头沿海", "points": [[123.80, 19.50], [120.10, 21.60], [116.68, 23.35]] }
  ] }
```

> **业务约束（重要）**：多条预报路径**共用同一个起点**（即当前实况位置），
> 分歧只出现在预报段。已经走过的路径各机构不会有分歧，这是气象业务常识，
> 后端生成数据时必须保持该约束，否则页面会呈现出错误结论。

---

### 3.8 预报分歧解析

```
GET /api/typhoons/13/disagreement
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": { "riskLevel": "高", "convergence": "扩大中",
            "dimensions": [
              { "name": "登陆地点", "spread": "118.09°E ~ 116.68°E", "variance": 1.41 },
              { "name": "登陆时间", "spread": "约 6 小时", "variance": 0.8 },
              { "name": "峰值强度", "spread": "强台风 ~ 超强台风", "variance": 0.6 },
              { "name": "移动速度", "spread": "16 ~ 22 km/h", "variance": 0.3 }
            ],
            "conclusion": "一部分模型预报登陆粤东，另一部分预报转向出海，当前预报可信度较低，需持续跟踪。" } }
```

> 对应数据表：`forecast_paths`、`forecast_agencies`、`forecast_disagree_records`

---

### 3.9 AI 路径智能测算

```
GET  /api/ai/predictions?typhoonId=13
POST /api/ai/predictions          Body: { "typhoonId": 13, "model": "deepseek-v3" }
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": { "typhoonId": 13, "model": "DeepSeek-V3", "confidence": 0.72,
            "landingRange": "福建泉州 ~ 厦门沿海",
            "points": [[123.80, 19.50], [121.20, 21.30], [118.35, 24.60]],
            "intensityTrend": "登陆前维持强台风级，登陆后 12 小时内减弱为热带风暴",
            "comment": "三家机构在登陆点上存在约 150km 分歧……" } }
```

> 对应数据表：`ai_prediction_records`
> **合规要求**：界面必须显著标注「AI 测算仅供科普参考，不替代官方预报」。

---

### 3.10 城市风险预评估

```
GET  /api/risk/assessments?typhoonId=13                           全量（评估半径内全部城市）
GET  /api/risk/assessments?typhoonId=13&cityId=5                  单城市
GET  /api/risk/assessments?typhoonId=13&obsTime=2026-09-13 08:00:00   按观测时刻重演
POST /api/risk/assessments       body: { typhoonId, obsTime? }     评估并落库（需登录）
```

**参数**

| 参数 | 位置 | 说明 |
|---|---|---|
| `typhoonId` | query / body | 必填 |
| `cityId` | query | 可选，单城市评估。不管距离多远都会返回一条记录（超半径时等级为「低」并说明原因），**不会返回空对象** |
| `obsTime` | query / body | 可选，评估基准观测时刻（`yyyy-MM-dd HH:mm:ss` 或 `yyyy-MM-dd HH:mm`）。省略表示**跟随最新实测** |
| `cities=1` | query | 可选，只列受影响城市清单 |

**关于 `obsTime`（按观测时刻重演）**

风险评估的基准**从来不是"当前时间"**，而是台风的某个**实测时刻**——ETA 是从这个点往后推的。
`obsTime` 让你锁定任意一个历史实测时次，用**当时的中心位置 + 当时的风圈**重算：

- 中心位置取 `typhoon_tracks` 中**不晚于该时刻的最近一个实测点**；
- 风圈取 `wind_circles` 中**不晚于该时刻的最近一组**。

> **为什么风圈要"取最近一组"而不是精确匹配**：风圈表并非每个观测时次都有数据——多数历史
> 台风整场只存了一组（如 `typhoon_id=1` 只有 `2024-09-05 17:00` 一组，轨迹却有 3 个时次）。
> 若精确匹配不到就返回 `null`，「跟随最新」与「锁定最新那个时次」会算出两个不同的评分，
> 而这两者本该是同一件事，用户无法解释这个差异。

响应中的 `baseTime` 是**服务端实际使用的基准时刻**，前端应直接显示它，不要自己拼当前时间
（改造前页面显示浏览器时间、服务端按最后实测时刻推算，两处口径不一致）。

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [ { "cityId": 5, "cityName": "厦门", "distance": 0, "eta": "2026-09-19 06:00",
              "baseTime": "2026-09-15 08:00:00",
              "windLevel": "12 级", "riskLevel": "极高", "advice": "停止户外作业，人员转移" } ] }
```

**存档（`POST`）**

落库时 `obs_time` 与 `assessment_time` 分开记：前者是**评估所依据的基准时刻**，后者是**存档动作
发生的时刻**。这两个不能合并——防灾复盘时要回答的是「登陆前 24 小时平台给的是什么结论」，
只有 `obs_time` 能回答；`assessment_time` 只说明这条记录是什么时候写的。

`obs_time` 由 `db/migration_risk_obs_time.sql` 增量添加（幂等）。**未跑迁移的旧库不会报错**，
DAO 会在建连接时探测列是否存在，缺列时跳过该字段；历史记录的 `obs_time` 为 `NULL`，
前端显示「—」即可。

未登录调用返回 **HTTP 200 + `code: 401`**（本项目 HTTP 恒 200，成败看 body 的 `code`）。

---

### 3.11 历史相似台风与灾情

```
GET /api/similar-cases?typhoonId=13
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [ { "typhoonId": 9, "nameCn": "莫兰蒂", "year": 2016, "score": 0.87,
              "matchDims": { "path": 0.9, "intensity": 0.85, "season": 1.0, "speed": 0.8, "windCircle": 0.8 },
              "disaster": { "rainfall": "累计 320mm", "flood": "多处内涝",
                            "stormSurge": "超警戒 1.2m", "scene": "树木倒伏、大面积停电" } } ] }
```

> 对应数据表：`disaster_reports`、`similar_case_matches`
> **必须提示**：历史相似仅作类比，不代表结果会重演。

---

### 3.12 沙盘情景

```
GET    /api/sandbox/scenarios                    情景库：公开的 ∪ 自己的（匿名只有公开的）
GET    /api/sandbox/scenarios?mine=1             只看当前登录用户自己的方案（需登录）
GET    /api/sandbox/scenarios?baseTyphoonId=13   再叠加"某个基底台风"过滤
GET    /api/sandbox/scenarios/params             四项参数的可调范围
GET    /api/sandbox/scenarios/{id}               单个情景详情
POST   /api/sandbox/scenarios                    Body: { "name": "情景A", "typhoonId": 13, "landingOffset": -120, "speedFactor": 1.2, "intensityDelta": 1, "windScale": 1.15 }
POST   /api/sandbox/scenarios?save=1             同上，但把结果落库为一条情景
PUT    /api/sandbox/scenarios/{id}/share?shared=1|0   公开 / 取消公开（创建者本人 或 管理员）
DELETE /api/sandbox/scenarios/{id}               删除（创建者本人，需登录）
```

**列表可见性口径**

| 登录状态 | `mine` | 返回 |
|---|---|---|
| 匿名 | — | 只有 `is_public = 1` 的情景 |
| 已登录 | 不传 / `0` | `is_public = 1` 的 **∪** 自己创建的（含未公开的私有方案） |
| 已登录 | `1` | 只有自己创建的（含未公开的） |

> **默认口径为什么必须包含"自己的方案"**：`POST ...?save=1` 插入时
> `is_public` 取 DDL 默认值 `0`，而 `PUT .../share` 原先要求管理员。
> 两件事叠起来，普通用户点完「另存为情景」→ 方案落库了 →
> 情景库里**看不见**，页面上那几条还是建库脚本预置的演示数据。
> 现在默认口径含自己的方案，且公开开关对创建者开放，链路才闭环。

**列表项字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 情景ID |
| `name` / `type` | string | 情景名 / 类型（`BASELINE` / `SCENARIO_A` / `SCENARIO_B`） |
| `baseTyphoonId` / `baseTyphoonName` | long / string | 基底台风 |
| `params` | object | 四项推演参数。`params_json` 是 `SandboxServiceImpl#simulate` 的产出，键名**驼峰**：`landingOffset` / `speedFactor` / `intensityDelta` / `windScale` / `path` |
| `result` | object | 结果摘要（`cityCount` / `maxRisk` / `maxRiskCity` …） |
| `cities` | array | 受影响城市明细 |
| `mine` | boolean | **是否属于当前请求者**。匿名恒为 `false` |
| `shared` | boolean | 是否已公开（`is_public = 1`） |
| `note` | string | 固定免责文案，必须随结果展示 |

> `mine` / `shared` 是**派生字段**（由服务层比对会话用户得出），不是数据库列。
> 前端必须读它们来决定"要不要画公开开关"：列表的可见性已经把 `is_public` 混进去了，
> 光看"这条能不能看到"反推不出公开状态。

**`PUT .../share` 权限**

判据是**「创建者本人 或 管理员」**，不是"仅管理员"：
把"自己刚推演出来的方案"设为公开本来就是这个功能的正常用法。
越权时返回 **403**（`只能公开自己创建的情景方案`），
情景不存在返回 **404** —— 不会"静默返回 0 行"让前端以为设置成功。

> **参数名是 `shared`**，且走**查询串**（`?shared=1`）而非请求体：
> 与 `setPublic` 的入参、DDL 的 `is_public`、`SandboxRequest#shared` 是同一条语义链。
> 匿名或未登录调用 → **401**。

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [
    { "id": 3, "name": "情景A · 偏西登陆", "type": "SCENARIO_A",
      "baseTyphoonId": 13, "baseTyphoonName": "飞燕",
      "params": { "landingOffset": -120, "speedFactor": 1.2,
                  "intensityDelta": 1, "windScale": 1.15,
                  "path": [[122.1, 21.3], [121.4, 22.0]] },
      "result": { "cityCount": 7, "maxRisk": "极高", "maxRiskCity": "厦门" },
      "mine": true, "shared": false,
      "note": "假设推演结果，不可作为防灾依据。" }
  ] }
```

**后端对应**：`SandboxServlet` → `SandboxService#list` / `#setShared` →
`SandboxScenarioDao#listVisible`（`WHERE (is_public = 1 OR user_id = ?)`）

---

### 3.13 防灾行为问卷

```
POST /api/surveys                 Body: { "typhoonId": 13, "cityId": 5,
                                         "preparedSupplies": true, "payAttentionEvacuation": false,
                                         "worriedDisasterType": "RAIN", "trustLevel": 3,
                                         "contactInfo": "可选，仅用于回访" }
GET  /api/surveys/agg?typhoonId=13
```

字段说明：

- `worriedDisasterType` 规范取值是 **`WIND` / `RAIN` / `STORM_SURGE` / `OTHER`** 四个码；
  为兼容前端问卷表单，同时接受中文标签（`大风` / `暴雨` / `暴雨内涝` / `风暴潮` …），
  服务端会归一化成上面的码（见 `SurveyServlet#resolveConcern`）。
- 同一组字段还有**短别名 `q1`~`q4`**（依次对应问卷四道题，前端表单用的就是这套），
  服务端两套都认。
- **不提供删除接口**：`DELETE /api/surveys` 返回 **405** —— 问卷含公众隐私，
  设计上不可删（如需清理，在服务端按台风归档时一并处理）。
  因此**回归脚本每提交一次问卷，聚合样本数就永久 +1**，写断言时不要写死样本量。
- `GET /api/surveys`（**根路径**）和 `GET /api/surveys/agg` 走的是**同一段聚合逻辑**，
  根路径只是别名（`SurveyServlet#doGet` 里 `sub == null` 与 `sub == "agg"` 合并处理）。
  两个写法都返回聚合统计，不是两个不同的接口。
- 响应只回计数，**不回显填写内容**（避免把个人答案回传到前端）。

**聚合响应示例**（比率量纲为 **0—100**，与 §1.4 一致；键为中文）

```json
{ "code": 200, "message": "success",
  "data": { "sampleSize": 128, "suppliesRate": 30.0, "evacuationRate": 45.0,
            "concernDist": { "大风": 20.0, "暴雨": 70.0, "风暴潮": 10.0 },
            "hint": "70% 用户关注暴雨内涝，但仅 30% 做了物资储备 —— 建议加强物资准备科普" } }
```

> ⚠️ **本示例曾写错、现已更正**：旧版写成 `suppliesRate: 0.30`、`concernDist` 里是
> `0.20 / 0.70 / 0.10`（0—1 量纲），而实现是 `ROUND(100.0 * …)`，**差 100 倍**，
> 且与 §1.4 量纲总表自相矛盾。本项目已因量纲取错出过三次缺陷（P1-2/P1-3/P1-4），
> 文档示例是同一个坑的来源之一 —— 改这一节务必对着 §1.4 核一遍。
>
> 另注：`concernDist["大风"]` 是 `100 − 暴雨 − 风暴潮` **反推**出来的
> （DAO 只单独统计暴雨与风暴潮两档，避免为此多扫两次全表）。

> **隐私要求**：聚合接口只返回群体统计，绝不返回任何个人数据；
> `contactInfo` 字段仅用于回访，需在接口文档与页面上明确说明（入库走 AES-256-GCM 加密）。

---

### 3.14 导出轨迹

```
GET /api/export/tracks?typhoonId=13&format=csv       # 导出 CSV
GET /api/export/tracks?typhoonId=13&format=xlsx      # 导出 Excel（手写 OOXML，零第三方依赖）
```

**示例响应（非下载模式，返回可轮询的下载地址）**

```json
{ "code": 200, "message": "success",
  "data": { "url": "/typhoon_platform/export/2613-tracks-20260929.csv" } }
```

CSV / Excel 表头：`时间,纬度,经度,中心风力(级),最大风速(m/s),中心气压(hPa)`

> 课程要求阶段七①「数据导出 Excel」：本项目**不引入 Apache POI**，由
> `util/XlsxUtil.java` 手写符合 ECMA-376 规范的 SpreadsheetML 包（一个 ZIP，
> 内含 workbook / worksheet / 关系部件），生成的 `.xlsx` 可被 Excel / WPS / Numbers 打开。
> 数值单元格按 `<v>` 写入、保留数值类型；其余按 inline string 写入并做 XML 转义。
>
> 指定 `format=svg` 时，导出当前地图视图（前端已内置纯前端 SVG 导出作为兜底，
> 见 `js/pages/detail.js` 的 `snapshot()`）。

---

### 3.15 后台统计图表（ECharts 数据源）

```
GET /api/admin/stats
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": {
    "userGrowth": [
      { "date": "2026-09-01", "count": 3 },
      { "date": "2026-09-02", "count": 8 },
      { "date": "2026-09-03", "count": 10 }
    ],
    "dataDistribution": [
      { "name": "管理员", "value": 2 },
      { "name": "普通用户", "value": 8 }
    ]
  } }
```

字段说明：

- `userGrowth`：用户累计增长曲线。`count` 为**累计在册数**（把 `users` 表按注册日期的
  每日新增累加得到），前端用折线图展示，单调递增。无用户数据时为空数组。
- `dataDistribution`：按角色拆分的用户分布饼图。`value` 为人数；
  `普通用户 = 总用户数 − 管理员数`（不会出现负数）。

**后端对应**：`AdminServlet#doGet`（`stats` 分支）→ `AdminService#stats` →
`UserService#growthTrend`（DAO：`UserDao#countByCreatedDate`）+ `UserService#stats`。

---

## 4. 管理后台接口（已接真实后端）

管理后台 `admin.html` 与 `js/pages/admin.js` 已全量对接真实 `/api/admin/*`，
**不再使用前端模拟数据**。各子接口前缀与鉴权（需管理员）见 `README.md` 第六节的接口清单。

| 方法 | URL | 说明 |
|---|---|---|
| GET | `/api/admin/dashboard` | 后台首页汇总指标 |
| GET | `/api/admin/todos` | 待办事项（带跳转链接） |
| GET | `/api/admin/stats` | 统计图表数据（ECharts 用，见 3.15） |
| GET | `/api/admin/users` | 用户列表（`data = {items, stats}`；**列表项的标识字段是 `userId`**，见 1.6） |
| GET | `/api/admin/users/{userId}` | 用户详情（同样返回 `userId`） |
| POST | `/api/admin/users` | 新建用户（返回 `{userId}`） |
| PUT | `/api/admin/users/{userId}` | 修改用户资料 |
| PUT | `/api/admin/users/{userId}/status` | 启用 / 停用账号 |
| PUT | `/api/admin/users/{userId}/role` | 调整角色 |
| PUT | `/api/admin/users/{userId}/password` | 重置密码 |
| DELETE | `/api/admin/users/{userId}` | 删除用户 |
| GET | `/api/admin/logs/sync` | 数据同步日志 |
| GET | `/api/admin/models` | 大模型接入配置（密钥脱敏返回） |

> **本节是逐条对着 `AdminServlet` 的分派代码核过的**（`doGet` / `doPost` / `doPut` / `doDelete`）。
> 核对时**删掉了原先列在这里的 `PUT /api/admin/models/{id}`** ——
> `AdminServlet#doPut` 只分派 `settings` / `users` / `datasources` / `feedback` 四个资源，
> **没有 `models` 分支**，真发这个请求会落到 `notFound`。
> 模型配置是**只读**的（`GET /api/admin/models`，密钥脱敏），没有写入接口；
> 原先那行是"文档描述了不存在的接口"，不是后端漏实现。

> **另有三处已知不一致**，核查时发现、本次**未改动**，在此登记以免被反复当新缺陷重报：
> - `js/api.js#deleteNotification` 请求 `DELETE /api/notifications/{id}`，
>   而 `NotificationServlet#doDelete` 是**刻意**返回 405 的（注释写明"历史通知保留更利于复盘"）。
>   该方法**没有任何调用点**；谁调它必然 405。
> - `GET /api/surveys`（根路径）与 `GET /api/surveys/agg` 走**同一段聚合逻辑**，
>   根路径是别名。见 3.13。
> - `docs` 中曾长期写「16 个 Servlet、47 条接口路径」。逐条枚举后按
>   **「HTTP 方法 + 路径」口径为 82 条**、按**去重 URL 路径口径为 59 条**
>   （加入 `POST /api/auth/register` 之前是 81 / 58）。「47」两个口径都对不上，
>   已在 `README.md` 更正为双口径并列。

---

## 5. Servlet 路径变量解析范式

Servlet 3.0+ 的 `@WebServlet` **不支持** `/api/typhoons/{id}` 这种占位符写法，
需用通配符 + `pathInfo` 手动解析。本项目统一按以下范式处理：

```java
@WebServlet("/api/typhoons/*")
public class TyphoonDetailServlet extends HttpServlet {

    private final TyphoonService service = new TyphoonServiceImpl();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 请求 /api/typhoons/13/tracks 时，pathInfo = "/13/tracks"
        String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo();
        String[] seg = pathInfo.split("/");          // ["", "13", "tracks"]
        String id = seg.length > 1 ? seg[1] : null;

        Result result;
        try {
            result = Result.ok(service.findById(Integer.parseInt(id)));
        } catch (NumberFormatException e) {
            result = Result.fail(400, "台风 id 格式错误");
        } catch (BusinessException e) {
            result = Result.fail(e.getCode(), e.getMessage());
        }
        JsonUtil.write(resp, result);                // 内部用 Jackson 序列化
    }
}
```

三个约定：

1. **id 解析统一走 `seg[1]`**，子资源（`tracks` / `wind-circles` / `forecast-paths`）
   走 `seg[2]`，避免各 Servlet 各写一套。
2. **异常在 Servlet 层统一转成 `Result`**，Servlet 内不写业务判断
   （课程评审要求「Servlet 不包含业务逻辑」）。
3. **JSON 输出统一走 `JsonUtil.write`**，不各自 new ObjectMapper，
   保证响应格式与字符编码一致。

---

## 6. 外部数据同步（中央气象台台风网）

平台的数据**不是**写死的演示快照：`SyncSchedulerListener` 在应用启动后按固定间隔
调用中央气象台台风网的公开接口，把实况轨迹、风圈与机构预报路径落库。
这段能力**不对外暴露新的 HTTP 接口**（没有「手动触发同步」的 HTTP 端点），
因此接口清单（第 2 节）的 59 / 82 两个口径都不变。

### 6.1 数据源

| 项 | 值 |
|---|---|
| 提供方 | 中央气象台 · 台风网 |
| 基地址 | `https://typhoon.nmc.cn/weatherservice/typhoon/jsons/` |
| 台风列表 | `list_default` → JSONP `typhoon_jsons_list_default(({...}))` |
| 台风详情 | `view_{apiId}` → JSONP `typhoon_jsons_view_{apiId}({...})` |
| 传输格式 | **JSONP**（需 `Referer: https://typhoon.nmc.cn/web.html`）；客户端取首个 `{` 到末个 `}` 之间的正文解析 |
| 落库来源标识 | `data_sources.source_code = CMA_TP_API` |

> **HTTPS 证书**：`*.nmc.cn` 由 **CFCA（中国金融认证中心）** 签发，其根证书
> **不在 JDK 自带 cacerts 里**，直接请求会抛
> `PKIX path building failed: unable to find valid certification path`。
> 处理方式是**补信任、不是关校验**：中间证书与根证书随 WAR 分发在
> `WEB-INF/classes/certs/cfca-nmc-roots.pem`，`CmaApiClient` 构造
> 「JDK 默认信任库 ∪ 附加信任」的并集 `TrustManager`，影响范围仅限本客户端，
> 不改动全局 `cacerts`、也不使用 `TrustAllCerts`。

### 6.2 同步节奏与配置

配置在 `WEB-INF/web.xml` 的 `<context-param>`（改这里即可，无需改代码）：

| 参数 | 默认 | 含义 |
|---|---|---|
| `sync.enabled` | `true` | 是否启用定时同步 |
| `sync.intervalMinutes` | `30` | 两轮同步的间隔（分钟） |
| `sync.initialDelaySeconds` | `20` | 应用启动后首次同步的延迟（秒） |

> 前端首页 `js/pages/overview.js` 的「数据每 30 分钟自动同步一次（中央气象台台风网）」
> 文案与 `sync.intervalMinutes` 是**手工耦合**的：改了 web.xml 要同步改这行文案。

### 6.3 同步策略与已知限制

1. **只插入没见过的**：按国际编号（`normalizeIntlId` 把 `2629` 补成 `202629`）查重；
   编号相同但本轮接口未列出（已停编）的，记为 `skipped`，不动既有档案。
2. **活跃台风持续追新**：每轮重拉，按「台风 + 观测时间」幂等写入轨迹 / 风圈；
   同一观测时次重复同步不会产生重复行。
3. **既有档案只读不动**：平台上原有的人工录入 / 历史档案不会被同步任务覆盖。
4. ⚠ **已知限制**：同步**不会**把状态从 `ACTIVE` 自动收尾为 `FINISHED` ——
   任务无法区分「这条记录是同步创建的」与「是人工录入的」，贸然改写会污染人工数据。
   处于「已停编但库里仍是 ACTIVE」的记录计入 `SyncResult.stale`，由运维在管理后台确认。

### 6.4 同步结果接口

同步日志写入 `sync_logs` 表，并由 `data_sources.last_sync_time` 记录数据源的最后同步时间；
管理后台通过既有接口读取，**没有新增端点**：

| 方法 | URL | 说明 |
|---|---|---|
| GET | `/api/admin/logs/sync` | 同步日志列表（含 `syncStatus` / `recordCount` / `durationSeconds` / `errorMessage`） |

`SyncSchedulerListener` 的关闭是**优雅的**：`contextDestroyed` 里
`shutdownNow()` + `awaitTermination(10s)`，Tomcat 停止时不会残留
「appears to have started a thread ... but has failed to stop it」告警。

