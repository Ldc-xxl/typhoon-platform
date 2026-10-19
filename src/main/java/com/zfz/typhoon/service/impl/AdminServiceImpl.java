package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.dao.SandboxScenarioDao;
import com.zfz.typhoon.dao.UserBehaviorSurveyDao;
import com.zfz.typhoon.dao.impl.AiPredictionRecordDaoImpl;
import com.zfz.typhoon.dao.impl.SandboxScenarioDaoImpl;
import com.zfz.typhoon.dao.impl.UserBehaviorSurveyDaoImpl;
import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.model.FeedbackReport;
import com.zfz.typhoon.model.LlmProviderConfig;
import com.zfz.typhoon.model.vo.DataSourceVO;
import com.zfz.typhoon.model.vo.ModelConfigVO;
import com.zfz.typhoon.service.AdminService;
import com.zfz.typhoon.service.DataSourceService;
import com.zfz.typhoon.service.FeedbackService;
import com.zfz.typhoon.service.LlmConfigService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.UserService;
import com.zfz.typhoon.util.DateUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台汇总业务实现。
 *
 * <h3>待办的实时推导规则</h3>
 * 待办不是一张表，而是"当前系统状态里所有需要人处理的事情"的投影：
 * <ol>
 *   <li>数据源成功率低于阈值 → 数据源异常待处理；</li>
 *   <li>存在 PENDING 反馈 → 反馈待审核；</li>
 *   <li>模型从未被调用过 → 接入密钥待配置；</li>
 *   <li>存在被停用账号 → 账号状态待复核。</li>
 * </ol>
 * 每条都带 {@code href}，前端点击可直达处理页面——
 * 待办如果不带去处，就只是一堆让管理员焦虑的文案。
 *
 * @author 追风者项目组
 * @see AdminService
 */
public class AdminServiceImpl implements AdminService {

    /** 后台模型表格里"未接入"的判定：累计调用为 0 */
    private static final long MODEL_IDLE_CALLS = 0L;

    /** 待办返回条数上限 */
    private static final int MAX_TODOS = 8;

    /** 模型"已接入"时展示的提示词版本（与 AiPredictionService 的默认版本一致） */
    private static final String DEFAULT_PROMPT_VERSION = "v1.1";

    /**
     * 平台支持接入的大模型清单。
     *
     * <p>这是<b>配置项</b>而非数据：模型是否"已接入"由
     * {@code ai_prediction_records} 里是否有该模型的测算记录决定，
     * 而不是靠人工在后台勾选。好处是表格永远不会骗人——
     * 显示"已接入"就一定有可用的测算记录。
     */
    private static final String[][] SUPPORTED_MODELS = {
            {"DeepSeek-V3", "deepseek-v3"},
            {"通义千问-max", "qwen-max"},
            {"豆包 Seed-1.6", "doubao-seed-1.6"},
            {"文心一言 4.0", "ernie-4.0"},
    };

    private final UserService userService;
    private final TyphoonService typhoonService;
    private final DataSourceService dataSourceService;
    private final FeedbackService feedbackService;
    private final SandboxScenarioDao scenarioDao;
    private final UserBehaviorSurveyDao surveyDao;
    private final AiPredictionRecordDao aiDao;
    private final LlmConfigService llmConfigService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public AdminServiceImpl() {
        this(new UserServiceImpl(), new TyphoonServiceImpl(), new DataSourceServiceImpl(), new FeedbackServiceImpl(), new SandboxScenarioDaoImpl(), new UserBehaviorSurveyDaoImpl(), new AiPredictionRecordDaoImpl(), new LlmConfigServiceImpl());
    }

    /**
     * 七参构造（历史签名，保留以兼容既有单元测试）。
     *
     * <p>新增的协作方 {@link LlmConfigService} 用默认实现补齐：
     * 它只在 {@link #modelConfigs()} 里读配置表，缺失时会自行降级为空列表，
     * 不会让本构造的调用方（测试）意外连库。
     */
    public AdminServiceImpl(UserService userService, TyphoonService typhoonService, DataSourceService dataSourceService, FeedbackService feedbackService, SandboxScenarioDao scenarioDao, UserBehaviorSurveyDao surveyDao, AiPredictionRecordDao aiDao) {
        this(userService, typhoonService, dataSourceService, feedbackService, scenarioDao, surveyDao, aiDao, new LlmConfigServiceImpl());
    }

    /**
     * 测试用构造：注入全部替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public AdminServiceImpl(UserService userService, TyphoonService typhoonService, DataSourceService dataSourceService, FeedbackService feedbackService, SandboxScenarioDao scenarioDao, UserBehaviorSurveyDao surveyDao, AiPredictionRecordDao aiDao, LlmConfigService llmConfigService) {
        this.userService = userService;
        this.typhoonService = typhoonService;
        this.dataSourceService = dataSourceService;
        this.feedbackService = feedbackService;
        this.scenarioDao = scenarioDao;
        this.surveyDao = surveyDao;
        this.aiDao = aiDao;
        this.llmConfigService = llmConfigService;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();

        Map<String, Integer> users = userService.stats();
        m.put("totalUsers", users.get("total"));
        m.put("adminUsers", users.get("admin"));
        m.put("disabledUsers", users.get("disabled"));

        m.put("totalTyphoons", Integer.valueOf(typhoonService.countAll()));
        m.put("activeTyphoons", Integer.valueOf(typhoonService.countActive()));
        m.put("archivedTyphoons", Integer.valueOf(typhoonService.countArchived()));

        m.put("sandboxScenarios", Integer.valueOf(scenarioDao.countAll()));
        m.put("surveySamples", Integer.valueOf(surveyDao.countAll()));

        Map<String, Integer> feedback = feedbackService.stats();
        m.put("pendingFeedback", feedback.get("pending"));
        m.put("processedFeedback", feedback.get("processed"));

        // 数据源与模型统计直接沿用各自服务的口径，避免后台首页与专项页数字对不上
        Map<String, Object> sourceSummary = dataSourceService.monitorSummary();
        m.put("dataSources", sourceSummary.get("sources"));
        m.put("sourcesOk", sourceSummary.get("ok"));
        m.put("sourcesError", sourceSummary.get("error"));
        m.put("todaySyncedRecords", sourceSummary.get("todayRecords"));

        long totalCalls = 0L;
        int onlineModels = 0;
        for (ModelConfigVO vo : modelConfigs()) {
            if (vo.getEnabled() != null && vo.getEnabled().booleanValue()) {
                onlineModels++;
            }
            totalCalls += parseCalls(vo.getCalls());
        }
        m.put("modelConfigs", Integer.valueOf(SUPPORTED_MODELS.length));
        m.put("onlineModels", Integer.valueOf(onlineModels));
        m.put("modelCalls", Long.valueOf(totalCalls));

        m.put("generatedAt", DateUtil.format(DateUtil.now()));
        return m;
    }

    @Override
    public List<ModelConfigVO> modelConfigs() {
        // 一次 GROUP BY 取回全部模型的调用次数、平均置信度与最近调用时间
        Map<String, Object[]> stats = new LinkedHashMap<String, Object[]>();
        for (Object[] row : aiDao.statsGroupByModel()) {
            if (row == null || row.length < 4 || row[0] == null) {
                continue;
            }
            stats.put(String.valueOf(row[0]), row);
        }
        // 真实调用耗时（只有确实连过厂商的模型才有）
        Map<String, Double> latencies = safeAvgLatency();
        // 厂商凭据（缺表时为空 Map，全部按"待接入"处理）
        Map<String, LlmProviderConfig> configs = indexConfigs();

        List<ModelConfigVO> result = new ArrayList<ModelConfigVO>(SUPPORTED_MODELS.length);
        for (String[] def : SUPPORTED_MODELS) {
            String name = def[0];
            String modelKey = def[1];
            Object[] stat = stats.get(name);
            LlmProviderConfig cfg = configs.get(modelKey);

            ModelConfigVO vo = new ModelConfigVO();
            vo.setName(name);
            vo.setModelKey(modelKey);
            long calls = stat == null ? 0L : ((Number) stat[1]).longValue();
            boolean used = calls > MODEL_IDLE_CALLS;

            boolean keyConfigured = cfg != null && cfg.hasKey();
            boolean verified = cfg != null && cfg.getVerifiedAt() != null;

            /* 三态口径：
               - 已接入：密钥已配置且验证通过，或历史上确实有测算记录；
               - 已配置：填了 Key 但这次验证还没通过（或从未验证）——
                 这个态是给管理员看的："我填了，但还没证明能用"；
               - 待接入：没有密钥、也没有任何记录。
               注意：历史记录可能来自本地融合算法（latency_ms 为 NULL），
               所以"已接入"不等于"真的调用过厂商" —— 是否真调用由"平均耗时"列区分。 */
            String status;
            if (keyConfigured && verified) {
                status = "已接入";
            } else if (keyConfigured) {
                status = "已配置";
            } else if (used) {
                status = "已接入";
            } else {
                status = "待接入";
            }

            vo.setVersion(used ? DEFAULT_PROMPT_VERSION : "—");
            vo.setStatus(status);
            vo.setEnabled(Boolean.valueOf("已接入".equals(status)));
            vo.setKeyConfigured(Boolean.valueOf(keyConfigured));
            // provider / 接入点 / 网关都不是密钥，回填给表单用；
            // 密钥本身永远不返回（VO 里根本没有这个字段）。
            vo.setProvider(cfg == null ? null : cfg.getProvider());
            vo.setEndpointId(cfg == null ? null : cfg.getEndpointId());
            vo.setBaseUrl(cfg == null ? null : cfg.getBaseUrl());
            vo.setCalls(used ? String.format("%,d", Long.valueOf(calls)) : "—");

            // 平均耗时只对"真的调用过厂商"的模型有意义；融合算法没有网络往返，
            // 该列为 NULL，这里如实显示 "—"，不拿计算耗时凑数。
            Double avgMs = latencies.get(name);
            vo.setCost(avgMs == null ? "—" : String.format("%.1f s", Double.valueOf(avgMs.doubleValue() / 1000D)));

            vo.setConfidence(used ? confidenceText(stat) : "—");
            if ("待接入".equals(status)) {
                vo.setAction("接入");
            } else {
                // 「已配置」与「已接入」都进同一个弹层：前者改完就地重试验证，
                // 后者改接入点/换 Key。按钮统一叫「配置」比「测试」更贴合点下去会发生什么。
                vo.setAction("配置");
            }
            vo.setLastUsed(stat == null ? null : DateUtil.format((LocalDateTime) stat[3]));
            result.add(vo);
        }
        return result;
    }

    /**
     * 读厂商配置并按 modelKey 建索引；任何异常都降级为空 Map。
     *
     * @return modelKey → 配置
     */
    private Map<String, LlmProviderConfig> indexConfigs() {
        Map<String, LlmProviderConfig> map = new LinkedHashMap<String, LlmProviderConfig>();
        for (LlmProviderConfig c : llmConfigService.listAll()) {
            if (c != null && c.getModelKey() != null) {
                map.put(c.getModelKey(), c);
            }
        }
        return map;
    }

    /**
     * 读各模型平均耗时；查询失败时返回空 Map（耗时列显示 "—"）。
     *
     * @return 模型名 → 平均毫秒
     */
    private Map<String, Double> safeAvgLatency() {
        try {
            return aiDao.avgLatencyByModel();
        } catch (RuntimeException e) {
            // latency_ms 是增量加的列，旧库可能没有；缺列时不要连累整张表
            return new LinkedHashMap<String, Double>();
        }
    }

    @Override
    public List<Map<String, Object>> todos() {
        List<Map<String, Object>> todos = new ArrayList<Map<String, Object>>();

        // 1) 数据源异常
        Map<String, Object> sourceSummary = dataSourceService.monitorSummary();
        Object error = sourceSummary.get("error");
        int errorCount = error instanceof Number ? ((Number) error).intValue() : 0;
        if (errorCount > 0) {
            todos.add(todo(errorCount + " 个数据源同步异常",
                    "数据源监控 · " + sourceSummary.get("updatedAt"), "待处理", "warn", "admin.html#sources"));
        }

        // 2) 待处理反馈
        Map<String, Integer> feedback = feedbackService.stats();
        int pending = feedback.get("pending") == null ? 0 : feedback.get("pending").intValue();
        if (pending > 0) {
            List<FeedbackReport> rows = feedbackService.list(FeedbackReport.STATUS_PENDING, null, pending);
            String latest = rows.isEmpty() || rows.get(0).getCreatedAt() == null
                    ? "" : DateUtil.format(rows.get(0).getCreatedAt().toLocalDate());
            todos.add(todo(pending + " 条用户反馈待处理",
                    "反馈处理 · " + latest, "待审核", "brand", "admin.html#feedback"));
        }

        // 3) 未接入的模型
        for (ModelConfigVO vo : modelConfigs()) {
            if (vo.getEnabled() != null && !vo.getEnabled().booleanValue()) {
                todos.add(todo(vo.getName() + " 接入密钥待配置",
                        "模型接入 · 未启用", "待处理", "warn", "admin.html#models"));
            }
        }

        // 4) 被停用的账号
        Map<String, Integer> users = userService.stats();
        Integer disabled = users.get("disabled");
        if (disabled != null && disabled.intValue() > 0) {
            todos.add(todo(disabled + " 个账号处于停用状态",
                    "用户管理 · 需复核", "待复核", "muted", "admin.html#users"));
        }

        if (todos.size() > MAX_TODOS) {
            return new ArrayList<Map<String, Object>>(todos.subList(0, MAX_TODOS));
        }
        return todos;
    }

    @Override
    public List<DataSourceVO> dataSources() {
        return dataSourceService.list(false);
    }

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();

        // 用户增长趋势：把"每日新增"累加为"累计在册"，形成单调递增曲线
        List<Map<String, Object>> daily = userService.growthTrend();
        List<Map<String, Object>> userGrowth = new ArrayList<Map<String, Object>>(daily.size());
        int cumulative = 0;
        for (Map<String, Object> row : daily) {
            cumulative += safeInt(row.get("count"));
            Map<String, Object> g = new LinkedHashMap<String, Object>();
            g.put("date", row.get("date"));
            g.put("count", Integer.valueOf(cumulative));
            userGrowth.add(g);
        }
        m.put("userGrowth", userGrowth);

        // 数据分布（饼图）：按角色拆分用户
        Map<String, Integer> us = userService.stats();
        int total = safeInt(us.get("total"));
        int admin = safeInt(us.get("admin"));
        int users = Math.max(0, total - admin);
        List<Map<String, Object>> dist = new ArrayList<Map<String, Object>>(2);
        dist.add(pie("管理员", admin));
        dist.add(pie("普通用户", users));
        m.put("dataDistribution", dist);

        return m;
    }

    /**
     * 把可能为 null / 非数值的对象安全转成 int。
     *
     * @param v 任意对象
     * @return int 值，缺失或非数值返回 0
     */
    private static int safeInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /**
     * 构造饼图的一项。
     *
     * @param name  名称
     * @param value 数值
     * @return {name, value}
     */
    private static Map<String, Object> pie(String name, int value) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("name", name);
        p.put("value", Integer.valueOf(value));
        return p;
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 构造一条待办。
     *
     * @param title 标题
     * @param src   来源说明
     * @param state 状态文案
     * @param level 级别：warn / brand / muted（对应前端样式类）
     * @param href  处理页面地址（相对路径）
     * @return 待办项
     */
    private Map<String, Object> todo(String title, String src, String state, String level, String href) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", title);
        m.put("src", src);
        m.put("state", state);
        m.put("level", level);
        m.put("href", href);
        return m;
    }

    /**
     * 平均置信度展示文案。
     *
     * @param stat 统计行 {@code [模型名, 次数, 平均置信度, 最近时间]}
     * @return 形如 {@code 78%}；无数据返回 {@code —}
     */
    private String confidenceText(Object[] stat) {
        if (stat == null || stat.length < 3 || stat[2] == null) {
            return "—";
        }
        double avg = ((Number) stat[2]).doubleValue();
        return String.format("%.0f%%", Double.valueOf(avg));
    }

    /**
     * 把带千分位的调用次数文本还原成数字。
     *
     * @param text 形如 {@code 1,286}；{@code —} 表示无数据
     * @return 数值
     */
    private long parseCalls(String text) {
        if (text == null || text.trim().isEmpty() || "—".equals(text.trim())) {
            return 0L;
        }
        try {
            return Long.parseLong(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
