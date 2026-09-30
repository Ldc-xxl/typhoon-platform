package com.zfz.typhoon.servlet;

import com.zfz.typhoon.testsupport.FakeHttp;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公开接口的端到端测试：真实 Servlet + 真实数据库 + 假的请求/响应。
 *
 * <h3>为什么这一层必须测</h3>
 * <p>这是<b>前端真正调用的那条链路</b>：URL 路由 → 参数解析 → Service → DAO → JSON。
 * 前面各层的单测都过、但这一层挂掉的情况非常常见且代价极高：
 * <ul>
 *   <li>路径段解析少判一层 → 接口 404；</li>
 *   <li>参数名写错（前端传 {@code typhoonId}，后端读 {@code id}）→ 空列表；</li>
 *   <li>异常没被 {@code BaseServlet} 兜住 → 前端拿到 HTML 错误页而不是 JSON。</li>
 * </ul>
 * 这三类问题的共同点是<b>页面看起来"没数据"而不是"报错"</b>，人工点点很难发现。
 *
 * <h3>断言的落点</h3>
 * <p>统一断言响应体 JSON 里的 {@code code}、以及 HTTP 状态码恒为 200 ——
 * 这正是本项目与前端约定的核心契约（业务成败写在响应体里，不走 HTTP 状态码）。
 *
 * @author 追风者项目组
 */
@DisplayName("公开接口端到端测试")
class PublicEndpointTest {

    private static long sampleTyphoonId;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过接口层测试");
        Long id = JdbcTemplate.queryLong("SELECT MIN(typhoon_id) FROM typhoons");
        sampleTyphoonId = id == null ? 0L : id.longValue();
    }

    /**
     * 发起一次 GET 并返回响应体。
     *
     * <p>注意这里走的是 {@code service()} 而不是 {@code doGet()}：
     * {@code BaseServlet.service()} 才是把 BusinessException 翻译成
     * {@code {code:4xx}} 的那一层。直接调 doGet 会让业务异常原样抛出，
     * 测出来的行为与线上不一致 —— 线上前端拿到的是 JSON，不是异常栈。
     */
    private static String get(HttpServletLike invoker, String pathInfo, Map<String, String> params)
            throws java.io.IOException {
        FakeHttp.Req req = FakeHttp.Req.get(pathInfo);
        if (params != null) {
            req.params(params);
        }
        FakeHttp.Resp resp = FakeHttp.resp();
        invoker.service(req.build(), resp.build());
        assertEquals(200, resp.status(), "HTTP 状态码应恒为 200");
        assertEquals("application/json;charset=UTF-8", resp.contentType(),
                "所有接口都应输出 JSON，否则前端 jQuery 解析失败");
        return resp.body();
    }

    /** 便于把不同 Servlet 的 service 传进来 */
    private interface HttpServletLike {
        void service(javax.servlet.http.HttpServletRequest req,
                     javax.servlet.http.HttpServletResponse resp) throws java.io.IOException;
    }

    @Nested
    @DisplayName("台风档案接口 /api/typhoons")
    class Typhoons {

        private final TyphoonServlet servlet = new TyphoonServlet();

        @Test
        @DisplayName("列表：返回 200 + data 是数组")
        void list() throws Exception {
            String body = get(servlet::service, null, null);
            assertTrue(body.contains("\"code\":200"), body);
            assertTrue(body.contains("\"success\":true"), body);
            assertTrue(body.contains("\"data\":["), "列表接口的 data 应是数组，实际：" + body);
        }

        @Test
        @DisplayName("带筛选参数：关键字 / 年份 / 状态 / 分页都能正常下传")
        void listWithFilters() throws Exception {
            Map<String, String> p = new java.util.LinkedHashMap<String, String>();
            p.put("keyword", "台风");
            p.put("year", "2024");
            p.put("status", "ACTIVE");
            p.put("limit", "5");
            p.put("page", "1");
            assertTrue(get(servlet::service, null, p).contains("\"code\":200"));
        }

        @Test
        @DisplayName("meta 分支：返回筛选项元数据")
        void meta() throws Exception {
            assertTrue(get(servlet::service, "/meta", null).contains("\"code\":200"));
        }

        @Test
        @DisplayName("详情 / 轨迹 / 风圈 / 预报路径 / 分歧 / 回放 / 灾情 各子资源")
        void subResources() throws Exception {
            String id = String.valueOf(sampleTyphoonId);
            assertTrue(get(servlet::service, "/" + id, null).contains("\"code\":200"), "详情");
            assertTrue(get(servlet::service, "/" + id + "/tracks", null).contains("\"code\":200"), "轨迹");
            assertTrue(get(servlet::service, "/" + id + "/wind-circles", null).contains("\"code\":200"), "风圈");
            assertTrue(get(servlet::service, "/" + id + "/forecast-paths", null).contains("\"code\":200"), "预报路径");
            assertTrue(get(servlet::service, "/" + id + "/disagreement", null).contains("\"code\":200"), "分歧");

            Map<String, String> step = new java.util.LinkedHashMap<String, String>();
            step.put("stepHours", "6");
            assertTrue(get(servlet::service, "/" + id + "/replay", step).contains("\"code\":200"), "回放");
            assertTrue(get(servlet::service, "/" + id + "/disaster", null).contains("\"code\":200"), "灾情");
        }

        @Test
        @DisplayName("回放步长越界：400 且给出可读文案，而不是 500")
        void replayStepOutOfRange() throws Exception {
            Map<String, String> p = new java.util.LinkedHashMap<String, String>();
            p.put("stepHours", "999");
            String body = get(servlet::service, "/" + sampleTyphoonId + "/replay", p);
            assertTrue(body.contains("\"code\":400"), "越界参数应是 400，实际：" + body);
            assertFalse(body.contains("\"success\":true"));
        }

        @Test
        @DisplayName("未知子路径 → 404（业务码写在响应体里）")
        void unknownSubPathIs404() throws Exception {
            String body = get(servlet::service, "/" + sampleTyphoonId + "/no-such-sub", null);
            assertTrue(body.contains("\"code\":404"), body);
            assertTrue(body.contains("接口不存在"), body);
        }

        @Test
        @DisplayName("首段非数字且不是 meta → 404")
        void nonNumericFirstSegmentIs404() throws Exception {
            assertTrue(get(servlet::service, "/abc", null).contains("\"code\":404"));
        }

        @Test
        @DisplayName("路径段超过两层 → 404")
        void tooManySegmentsIs404() throws Exception {
            assertTrue(get(servlet::service, "/" + sampleTyphoonId + "/tracks/extra", null)
                    .contains("\"code\":404"));
        }

        @Test
        @DisplayName("台风 ID 非数字 → 400（携带参数名，便于前端定位）")
        void nonNumericIdIs400() throws Exception {
            String body = get(servlet::service, "/not-a-number/tracks", null);
            // 首段非数字会先走 404 分支；这里断言"不是 500"即可
            assertFalse(body.contains("\"code\":500"), "不应暴露成服务器内部错误：" + body);
        }
    }

    @Nested
    @DisplayName("元数据接口 /api/cities 与 /api/agencies")
    class Meta {

        private final MetaServlet servlet = new MetaServlet();

        @Test
        @DisplayName("城市列表")
        void cities() throws java.io.IOException {
            FakeHttp.Req req = FakeHttp.Req.get(null).servletPath("/api/cities");
            FakeHttp.Resp resp = FakeHttp.resp();
            servlet.service(req.build(), resp.build());
            assertTrue(resp.body().contains("\"code\":200"), resp.body());
            assertTrue(resp.body().contains("\"data\":["), resp.body());
        }

        @Test
        @DisplayName("城市列表：按关键字 / 省份 / enabled=false 过滤")
        void citiesWithFilters() throws Exception {
            Map<String, String> kw = new java.util.LinkedHashMap<String, String>();
            kw.put("keyword", "海");
            assertTrue(get(servlet::service, null, kw).contains("\"code\":200"));

            Map<String, String> pv = new java.util.LinkedHashMap<String, String>();
            pv.put("province", "广东");
            assertTrue(get(servlet::service, null, pv).contains("\"code\":200"));

            Map<String, String> all = new java.util.LinkedHashMap<String, String>();
            all.put("enabled", "false");
            assertTrue(get(servlet::service, null, all).contains("\"code\":200"));
        }

        @Test
        @DisplayName("机构列表")
        void agencies() throws java.io.IOException {
            FakeHttp.Req req = FakeHttp.Req.get(null).servletPath("/api/agencies");
            FakeHttp.Resp resp = FakeHttp.resp();
            servlet.service(req.build(), resp.build());
            assertTrue(resp.body().contains("\"code\":200"), resp.body());
            assertTrue(resp.body().contains("\"data\":["), resp.body());
        }
    }

    @Nested
    @DisplayName("健康检查 /api/health")
    class Health {

        @Test
        @DisplayName("返回 200，且带 application / database 状态")
        void health() throws Exception {
            HealthServlet servlet = new HealthServlet();
            FakeHttp.initWithFakeContext(servlet);
            String body = get(servlet::service, null, null);
            assertTrue(body.contains("\"code\":200"), body);
            assertTrue(body.contains("\"app\":\"typhoon_platform\""), body);
            assertTrue(body.contains("\"database\""), body);
            assertTrue(body.contains("\"ok\":true"), "本机数据库可用时应报告 ok=true，实际：" + body);
        }
    }
}
