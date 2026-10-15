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
| 401 | 未登录或会话过期 | 跳转登录页 |
| 403 | 已登录但无权限（非管理员访问后台接口） | toast「无访问权限」 |
| 404 | 资源不存在（台风 id 不存在等） | 空态提示 |
| 500 | 服务器内部错误（SQL 异常等） | toast「服务异常，请稍后重试」 |

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

---

## 2. 接口清单总览

| # | 方法 | URL | 说明 | 前端调用处 |
|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 管理员登录 | 登录页 |
| 2 | GET | `/api/typhoons` | 台风列表（可按条件筛选） | P1 / P5 / P8 |
| 3 | GET | `/api/typhoons/{id}` | 单个台风基础信息 | P2 |
| 4 | GET | `/api/typhoons/{id}/tracks` | 实况轨迹点序列 | P1 / P2 / P4 |
| 5 | GET | `/api/typhoons/{id}/wind-circles` | 7/10/12 级风圈半径 | P1 / P2 |
| 6 | GET | `/api/typhoons/{id}/forecast-paths` | 多机构预报路径 | P1 / P2 / P3 |
| 7 | GET | `/api/typhoons/{id}/disagreement` | 预报分歧解析结果 | P3 |
| 8 | GET | `/api/ai/predictions` | AI 路径智能测算结果 | P2 / P3 |
| 9 | POST | `/api/ai/predictions` | 触发一次新的 AI 测算 | P2 |
| 10 | GET | `/api/risk/assessments` | 城市风险预评估 | P4 |
| 11 | GET | `/api/similar-cases` | 历史相似台风 + 灾情记录 | P5 |
| 12 | GET | `/api/sandbox/scenarios` | 沙盘情景列表 | P6 |
| 13 | POST | `/api/sandbox/scenarios` | 保存沙盘情景 | P6 |
| 14 | POST | `/api/surveys` | 提交防灾行为问卷 | P7 |
| 15 | GET | `/api/surveys/agg` | 问卷聚合统计 | P7 |
| 16 | GET | `/api/export/tracks` | 导出轨迹 CSV / Excel（xlsx） | P2 / P5 |
| 17 | GET | `/api/admin/stats` | 后台统计图表数据（ECharts 用：用户累计增长 + 角色分布） | P9 |
| 18 | * | `/api/admin/*` | 管理后台：dashboard / todos / models / settings / datasources / users / feedback / logs/sync / stats（均已接真实后端） | P9 |

> 页面代号：P1 监测总览 / P2 台风详情 / P3 预报分歧解析 / P4 城市风险评估 /
> P5 相似灾害案例 / P6 情景推演沙盘 / P7 防灾行为调研 / P8 管理后台。

---

## 3. 接口详情

### 3.1 管理员登录

```
POST /api/auth/login
```

**请求体**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码（后端存 MD5 或 BCrypt 摘要，明文不入库） |

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": { "token": "eyJhbGciOi...", "role": "ADMIN", "userName": "总管理员" } }
```

**后端对应**：`AuthServlet#doPost` → `UserService#login` → `UserDao#findByUsername`

---

### 3.2 台风列表

```
GET /api/typhoons?status=ACTIVE&year=2024&seaArea=西北太平洋
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | string | 否 | `ACTIVE` 活跃 / `ENDED` 已结束；不传返回全部 |
| year | int | 否 | 生成年份，用于历史档案筛选 |
| seaArea | string | 否 | 所属海域 |
| level | string | 否 | 强度等级 |
| keyword | string | 否 | 名称或国际编号模糊匹配 |

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [
    { "id": 13, "code": "2613", "nameCn": "飞燕", "nameEn": "Jebi",
      "status": "ACTIVE", "level": "强台风", "year": 2026,
      "seaArea": "西北太平洋", "birthTime": "2026-09-13 08:00" }
  ] }
```

**后端对应**：`TyphoonListServlet#doGet` → `TyphoonService#listByCondition` → `TyphoonDao`

---

### 3.3 单个台风基础信息

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

### 3.4 实况轨迹

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

### 3.5 风圈半径

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

### 3.6 多机构预报路径

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

### 3.7 预报分歧解析

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

### 3.8 AI 路径智能测算

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

### 3.9 城市风险预评估

```
GET /api/risk/assessments?typhoonId=13
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": [ { "cityId": 5, "cityName": "厦门", "distance": 0, "eta": "2026-09-19 06:00",
              "windLevel": "12 级", "riskLevel": "极高", "advice": "停止户外作业，人员转移" } ] }
```

---

### 3.10 历史相似台风与灾情

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

### 3.11 沙盘情景

```
GET  /api/sandbox/scenarios
POST /api/sandbox/scenarios       Body: { "name": "情景A", "typhoonId": 13, "landingOffset": -120, "speedFactor": 1.2, "intensityDelta": 1, "windScale": 1.15 }
```

**示例响应**

```json
{ "code": 200, "message": "success",
  "data": { "id": 3, "name": "情景A · 偏西登陆", "cityCount": 7, "maxRisk": "极高",
            "note": "假设推演结果，不可作为防灾依据" } }
```

---

### 3.12 防灾行为问卷

```
POST /api/surveys                 Body: { "typhoonId": 13, "cityId": 5, "hasSupplies": true, "followEvacuation": false, "concern": "暴雨", "trustLevel": 3, "contact": "可选" }
GET  /api/surveys/agg?typhoonId=13
```

**聚合响应示例**

```json
{ "code": 200, "message": "success",
  "data": { "sampleSize": 128, "suppliesRate": 0.30, "evacuationRate": 0.45,
            "concernDist": { "大风": 0.20, "暴雨": 0.70, "风暴潮": 0.10 },
            "hint": "70% 用户关注暴雨内涝，但仅 30% 做了物资储备 —— 建议加强物资准备科普" } }
```

> **隐私要求**：聚合接口只返回群体统计，绝不返回任何个人数据；
> `contact` 字段仅用于回访，需在接口文档与页面上明确说明。

---

### 3.13 导出轨迹

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

### 3.14 后台统计图表（ECharts 数据源）

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
| GET | `/api/admin/stats` | 统计图表数据（ECharts 用，见 3.14） |
| GET | `/api/admin/users` | 用户列表（分页） |
| PUT | `/api/admin/users/{id}/status` | 启用 / 停用账号 |
| GET | `/api/admin/logs/sync` | 数据同步日志 |
| GET | `/api/admin/models` | 大模型接入配置（密钥脱敏返回） |
| PUT | `/api/admin/models/{id}` | 修改模型配置 |

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
