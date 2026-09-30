package com.zfz.typhoon.servlet;

import com.zfz.typhoon.testsupport.FakeHttp;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分析接口 / 个人数据接口 / 管理后台接口的端到端测试。
 *
 * <p>本文件最核心的价值是那批<b>「身份不对就必须是 4xx」</b>的用例：
 * 未登录访问个人数据要 401，普通用户访问管理后台要 403。
 * 这类越权漏洞的表现是"功能正常、只是别人也能用"，
 * 在功能测试里永远不会失败，只能靠接口层的断言钉死。
 *
 * <p>与 {@link PublicEndpointTest} 一样，这里也走 {@code service()} 而不是
 * {@code doGet()}，以便验证 {@code BaseServlet} 的异常映射确实生效
 * —— 即"业务失败也要返回 JSON，而不是把异常抛到容器变成 500 HTML 页"。
 *
 * @author 追风者项目组
 */
@DisplayName("分析 / 个人 / 管理接口端到端测试")
class PersonalAndAdminEndpointTest {

    private static long sampleTyphoonId;
    private static long sampleCityId;
    private static long sampleUserId;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过接口层测试");
        Long t = JdbcTemplate.queryLong("SELECT MIN(typhoon_id) FROM typhoons");
        sampleTyphoonId = t == null ? 0L : t.longValue();
        Long c = JdbcTemplate.queryLong("SELECT MIN(city_id) FROM cities");
        sampleCityId = c == null ? 0L : c.longValue();
        Long u = JdbcTemplate.queryLong("SELECT MIN(user_id) FROM users");
        sampleUserId = u == null ? 0L : u.longValue();
    }

    private interface Handler {
        void service(HttpServletRequest req, HttpServletResponse resp) throws IOException;
    }

    private static String call(Handler handler, String pathInfo,
                               Map<String, String> params, String role) {
        FakeHttp.Req req = FakeHttp.Req.get(pathInfo);
        if (params != null) {
            req.params(params);
        }
        if (role != null) {
            req.login(sampleUserId, role);
        } else {
            req.anonymous();
        }
        FakeHttp.Resp resp = FakeHttp.resp();
        try {
            handler.service(req.build(), resp.build());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertEquals(200, resp.status(), "HTTP 状态码应恒为 200，业务码写在响应体里");
        return resp.body();
    }

    private static Map<String, String> p(String... kv) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /* ================================================================== */
    /*  分析类公开接口                                                      */
    /* ================================================================== */

    @Nested
    @DisplayName("风险评估 /api/risk/assessments")
    class Risk {

        private final RiskAssessmentServlet servlet = new RiskAssessmentServlet();

        @Test
        @DisplayName("按台风评估：返回数组")
        void assessByTyphoon() {
            String body = call(servlet::service, null,
                    p("typhoonId", String.valueOf(sampleTyphoonId)), null);
            assertTrue(body.contains("\"code\":200"), body);
        }

        @Test
        @DisplayName("按城市评估：返回单个对象")
        void assessByCity() {
            String body = call(servlet::service, null,
                    p("typhoonId", String.valueOf(sampleTyphoonId),
                            "cityId", String.valueOf(sampleCityId)), null);
            assertTrue(body.contains("\"code\":200"), body);
        }

        @Test
        @DisplayName("缺少 typhoonId → 400 且给出可读文案")
        void missingTyphoonIdIs400() {
            String body = call(servlet::service, null, null, null);
            assertTrue(body.contains("\"code\":400"), body);
        }
    }

    @Nested
    @DisplayName("相似案例 /api/similar-cases")
    class Similar {

        private final SimilarCaseServlet servlet = new SimilarCaseServlet();

        @Test
        @DisplayName("查找相似历史台风")
        void findSimilar() {
            String body = call(servlet::service, null,
                    p("typhoonId", String.valueOf(sampleTyphoonId), "topN", "5"), null);
            assertTrue(body.contains("\"code\":200"), body);
        }

        @Test
        @DisplayName("指定一对台风求相似度")
        void scorePair() {
            String body = call(servlet::service, null,
                    p("currentId", String.valueOf(sampleTyphoonId),
                            "historyId", String.valueOf(sampleTyphoonId)), null);
            assertTrue(body.contains("\"code\":200"), body);
        }

        @Test
        @DisplayName("缺少台风参数 → 400")
        void missingParamsIs400() {
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":400"));
        }
    }

    @Nested
    @DisplayName("AI 测算 /api/ai/predictions")
    class Ai {

        private final AiPredictionServlet servlet = new AiPredictionServlet();

        @Test
        @DisplayName("取最新测算结果：有则 200，没有则 404（都是正常的业务结果）")
        void latest() {
            String body = call(servlet::service, null,
                    p("typhoonId", String.valueOf(sampleTyphoonId)), null);
            assertTrue(body.contains("\"code\":200") || body.contains("\"code\":404"),
                    "有结果是 200，没算过是 404，都不该是 500。实际：" + body);
        }

        @Test
        @DisplayName("models=true 时返回各模型清单")
        void models() {
            String body = call(servlet::service, null,
                    p("typhoonId", String.valueOf(sampleTyphoonId), "models", "true"), null);
            assertTrue(body.contains("\"code\":200"), body);
        }

        @Test
        @DisplayName("兼容前端用 id 传参")
        void acceptsIdAlias() {
            String body = call(servlet::service, null,
                    p("id", String.valueOf(sampleTyphoonId)), null);
            assertTrue(body.contains("\"code\":200") || body.contains("\"code\":404"), body);
        }

        @Test
        @DisplayName("缺少台风参数 → 400")
        void missingParamsIs400() {
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":400"));
        }
    }

    @Nested
    @DisplayName("问卷统计 /api/surveys")
    class Survey {

        private final SurveyServlet servlet = new SurveyServlet();

        @Test
        @DisplayName("按台风查聚合结果")
        void agg() {
            String body = call(servlet::service, null,
                    p("typhoonId", String.valueOf(sampleTyphoonId)), null);
            assertTrue(body.contains("\"code\":200"), body);
        }

        @Test
        @DisplayName("缺少 typhoonId → 400")
        void missingTyphoonIdIs400() {
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":400"));
        }

        @Test
        @DisplayName("未知子路径 → 404")
        void unknownSubPathIs404() {
            assertTrue(call(servlet::service, "/nope", null, null).contains("\"code\":404"));
        }
    }

    @Nested
    @DisplayName("沙盘 /api/sandbox/scenarios")
    class Sandbox {

        private final SandboxServlet servlet = new SandboxServlet();

        @Test
        @DisplayName("公开情景库（无需登录）")
        void publicScenarios() {
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":200"));
        }

        @Test
        @DisplayName("参数取值范围（供前端滑杆用）")
        void paramRange() {
            assertTrue(call(servlet::service, "/params", null, null).contains("\"code\":200"));
        }

        @Test
        @DisplayName("mine=1 时必须登录，否则 401")
        void mineRequiresLogin() {
            String body = call(servlet::service, null, p("mine", "1"), null);
            assertTrue(body.contains("\"code\":401"), body);
        }

        @Test
        @DisplayName("未知子路径 → 404")
        void unknownSubPathIs404() {
            assertTrue(call(servlet::service, "/nope", null, null).contains("\"code\":404"));
        }
    }

    /* ================================================================== */
    /*  个人数据接口                                                        */
    /* ================================================================== */

    @Nested
    @DisplayName("个人数据接口：未登录必须是 401")
    class Personal {

        @Test
        @DisplayName("通知列表 / 未读数")
        void notifications() {
            NotificationServlet servlet = new NotificationServlet();
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":401"),
                    "通知是个人数据，未登录必须 401");
            assertTrue(call(servlet::service, "/unread-count", null, null).contains("\"code\":401"));

            String body = call(servlet::service, null, null, "USER");
            assertTrue(body.contains("\"code\":200"), body);
            assertTrue(call(servlet::service, "/unread-count", null, "USER")
                    .contains("\"unread\""), "未读数接口应返回 unread 字段");
        }

        @Test
        @DisplayName("收藏列表")
        void favorites() {
            FavoriteServlet servlet = new FavoriteServlet();
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":401"));
            String body = call(servlet::service, null, null, "USER");
            assertTrue(body.contains("\"code\":200"), body);
            assertTrue(body.contains("\"typhoons\"") && body.contains("\"cities\""), body);
        }

        @Test
        @DisplayName("订阅列表")
        void subscriptions() {
            SubscriptionServlet servlet = new SubscriptionServlet();
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":401"));
            assertTrue(call(servlet::service, null, null, "USER").contains("\"code\":200"));
        }

        @Test
        @DisplayName("导出历史：未登录 401")
        void exportHistory() {
            ExportServlet servlet = new ExportServlet();
            assertTrue(call(servlet::service, "/history", null, null).contains("\"code\":401"));
            assertTrue(call(servlet::service, "/history", null, "USER").contains("\"code\":200"));
            assertTrue(call(servlet::service, "/nope", null, "USER").contains("\"code\":404"));
        }
    }

    /* ================================================================== */
    /*  管理后台                                                           */
    /* ================================================================== */

    @Nested
    @DisplayName("管理后台 /api/admin：必须是管理员")
    class Admin {

        private final AdminServlet servlet = new AdminServlet();

        @Test
        @DisplayName("未登录 → 401")
        void anonymousIs401() {
            assertTrue(call(servlet::service, "/dashboard", null, null).contains("\"code\":401"));
        }

        @Test
        @DisplayName("普通用户 → 403（不是 404，也不是 200）")
        void normalUserIs403() {
            String body = call(servlet::service, "/dashboard", null, "USER");
            assertTrue(body.contains("\"code\":403"),
                    "普通用户访问管理后台必须是 403，实际：" + body);
        }

        @Test
        @DisplayName("管理员可访问各资源")
        void adminSeesEverything() {
            String[] resources = {"dashboard", "todos", "models", "settings", "datasources", "users"};
            for (String r : resources) {
                String body = call(servlet::service, "/" + r, null, "ADMIN");
                assertTrue(body.contains("\"code\":200"), r + " 应返回 200，实际：" + body);
            }
        }

        @Test
        @DisplayName("未知资源 → 404")
        void unknownResourceIs404() {
            assertTrue(call(servlet::service, "/nope", null, "ADMIN").contains("\"code\":404"));
            assertTrue(call(servlet::service, null, null, "ADMIN").contains("\"code\":404"));
        }
    }

    @Nested
    @DisplayName("反馈 /api/feedback 与认证 /api/auth")
    class FeedbackAndAuth {

        @Test
        @DisplayName("反馈全量列表需要管理员：未登录 401，普通用户 403")
        void feedbackListRequiresAdmin() {
            FeedbackServlet servlet = new FeedbackServlet();
            assertTrue(call(servlet::service, null, null, null).contains("\"code\":401"));
            assertTrue(call(servlet::service, null, null, "USER").contains("\"code\":403"));
            assertTrue(call(servlet::service, null, null, "ADMIN").contains("\"code\":200"));
        }

        @Test
        @DisplayName("mine=1 时任何人登录后都能看自己的反馈")
        void feedbackMine() {
            FeedbackServlet servlet = new FeedbackServlet();
            String anonymous = call(servlet::service, null, p("mine", "1"), null);
            assertTrue(anonymous.contains("\"code\":401"), anonymous);
            assertTrue(call(servlet::service, null, p("mine", "1"), "USER")
                    .contains("\"code\":200"));
        }

        @Test
        @DisplayName("auth/me：未登录 401，登录后返回当前用户")
        void authMe() {
            AuthServlet servlet = new AuthServlet();
            assertTrue(call(servlet::service, "/me", null, null).contains("\"code\":401"));
            String body = call(servlet::service, "/me", null, "USER");
            assertTrue(body.contains("\"code\":200"), body);
            assertTrue(body.contains("\"userId\""), "应回传当前用户 ID，实际：" + body);
        }

        @Test
        @DisplayName("auth/logout：无论是否登录都能安全调用")
        void authLogout() {
            AuthServlet servlet = new AuthServlet();
            assertTrue(call(servlet::service, "/logout", null, null).contains("\"code\":200"));
            assertTrue(call(servlet::service, "/logout", null, "USER").contains("\"code\":200"));
        }

        @Test
        @DisplayName("auth 下的未知路径 → 404")
        void authUnknownIs404() {
            AuthServlet servlet = new AuthServlet();
            assertTrue(call(servlet::service, "/nope", null, null).contains("\"code\":404"));
        }
    }
}
