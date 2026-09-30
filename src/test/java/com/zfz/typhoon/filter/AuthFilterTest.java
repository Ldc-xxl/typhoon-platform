package com.zfz.typhoon.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthFilter} 单元测试。
 *
 * <h3>为什么这一层必须测</h3>
 * <p>鉴权放在 Filter 里是"默认拒绝"的设计：<b>漏配的表现是"接口用不了"（立刻能发现），
 * 而不是"接口谁都能用"（几乎发现不了）</b>。这个设计要成立，前提是规则表本身正确 ——
 * 一旦 {@code LOGIN_PREFIXES} 少写一条，或者上下文路径没裁掉导致前缀永远匹配不上，
 * 整条防线就静默失效。这类缺陷不会出现在功能测试里（功能是好的，只是别人也能用），
 * 只能靠规则表级的单测兜住。
 *
 * <h3>上下文路径这个坑</h3>
 * <p>{@code requestURI} 带应用前缀（{@code /typhoon_platform/api/...}），
 * 规则表却写的是 {@code /api/...}。过滤器的做法是先裁掉 {@code contextPath} 再比对。
 * 本测试分别覆盖「带上下文」「空上下文」「null 上下文」「上下文不匹配」四种情况，
 * 防止有人日后"顺手"把裁剪逻辑删掉。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFilter：接口鉴权过滤器")
class AuthFilterTest {

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse resp;
    @Mock
    private FilterChain chain;
    @Mock
    private HttpSession session;

    private AuthFilter filter;
    private final StringWriter out = new StringWriter();

    @BeforeEach
    void setUp() {
        filter = new AuthFilter();
        out.getBuffer().setLength(0);
    }

    /** 让响应真的能写出内容，便于断言拒绝时的响应体 */
    private void captureResponseBody() throws IOException {
        when(resp.getWriter()).thenReturn(new PrintWriter(out, true));
    }

    /** 已登录（非管理后台路径只读 userId） */
    private void stubSessionUser() {
        when(req.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(Long.valueOf(7L));
    }

    /** 已登录且带角色（管理后台路径会读 role） */
    private void stubSessionWithRole(Long userId, String role) {
        when(req.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(userId);
        when(session.getAttribute("role")).thenReturn(role);
    }

    private void requestPath(String contextPath, String uri) {
        when(req.getContextPath()).thenReturn(contextPath);
        when(req.getRequestURI()).thenReturn(uri);
    }

    /* ================================================================== */
    /*  放行                                                               */
    /* ================================================================== */

    @Nested
    @DisplayName("放行的情况")
    class Allowed {

        @Test
        @DisplayName("非 HTTP 请求直接放行（过滤器只管 HTTP）")
        void nonHttpRequestPasses() throws Exception {
            ServletRequest plainReq = org.mockito.Mockito.mock(ServletRequest.class);
            ServletResponse plainResp = org.mockito.Mockito.mock(ServletResponse.class);
            filter.doFilter(plainReq, plainResp, chain);
            verify(chain).doFilter(plainReq, plainResp);
        }

        @Test
        @DisplayName("登录接口永远放行 —— 否则没人能登录")
        void loginEndpointAlwaysPasses() throws Exception {
            requestPath("/typhoon_platform", "/typhoon_platform/api/auth/login");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("公开数据接口无需登录即可访问")
        void publicEndpointsPassWithoutSession() throws Exception {
            requestPath("/typhoon_platform", "/typhoon_platform/api/typhoons");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("已登录用户可访问需登录接口（收藏）")
        void loggedInUserPassesFavorites() throws Exception {
            stubSessionUser();
            requestPath("/typhoon_platform", "/typhoon_platform/api/favorites");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("管理员可访问管理后台")
        void adminPassesAdminArea() throws Exception {
            stubSessionWithRole(Long.valueOf(1L), "ADMIN");
            requestPath("", "/api/admin/users");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("管理员角色大小写不敏感（小写 admin 也放行）")
        void adminRoleIsCaseInsensitive() throws Exception {
            stubSessionWithRole(Long.valueOf(1L), "admin");
            requestPath("", "/api/admin/datasources");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("上下文路径为空字符串时不裁剪，直接比对完整 URI")
        void emptyContextPathIsHandled() throws Exception {
            stubSessionUser();
            requestPath("", "/api/auth/me");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("上下文路径与 URI 前缀不一致时不裁剪（防御性分支）")
        void mismatchedContextPathIsNotStripped() throws Exception {
            // 裁不掉上下文 → 路径仍以 /typhoon_platform 开头 →
            // 不命中任何需登录前缀 → 放行（这正是"上下文裁剪"被删掉后会出现的漏洞形态）
            requestPath("/other-app", "/typhoon_platform/api/favorites");
            filter.doFilter(req, resp, chain);
            verify(chain).doFilter(req, resp);
        }
    }

    /* ================================================================== */
    /*  拒绝                                                               */
    /* ================================================================== */

    @Nested
    @DisplayName("拒绝的情况")
    class Denied {

        @Test
        @DisplayName("管理后台未登录 → 401")
        void adminAreaRequiresLogin() throws Exception {
            requestPath("/typhoon_platform", "/typhoon_platform/api/admin/users");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            verify(chain, never()).doFilter(any(), any());
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
            assertTrue(out.toString().contains("请先登录后再访问管理后台"), out.toString());
        }

        @Test
        @DisplayName("管理后台已登录但非管理员 → 403")
        void adminAreaRequiresAdminRole() throws Exception {
            stubSessionWithRole(Long.valueOf(7L), "USER");
            requestPath("", "/api/admin/settings");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            verify(chain, never()).doFilter(any(), any());
            assertTrue(out.toString().contains("\"code\":403"), out.toString());
            assertTrue(out.toString().contains("该功能仅平台管理员可用"), out.toString());
        }

        @Test
        @DisplayName("会话里有 userId 但没有 role → 403（防越权升级）")
        void missingRoleIsForbidden() throws Exception {
            stubSessionWithRole(Long.valueOf(7L), null);
            requestPath("", "/api/admin/roles");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            verify(chain, never()).doFilter(any(), any());
            assertTrue(out.toString().contains("\"code\":403"), out.toString());
        }

        @Test
        @DisplayName("个人数据接口未登录 → 401（通知）")
        void notificationsRequireLogin() throws Exception {
            requestPath("/typhoon_platform", "/typhoon_platform/api/notifications");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
            assertTrue(out.toString().contains("请先登录后再操作"), out.toString());
        }

        @Test
        @DisplayName("个人数据接口未登录 → 401（收藏）")
        void favoritesRequireLogin() throws Exception {
            requestPath("", "/api/favorites");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
        }

        @Test
        @DisplayName("个人数据接口未登录 → 401（订阅）")
        void subscriptionsRequireLogin() throws Exception {
            requestPath("", "/api/subscriptions/1");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
        }

        @Test
        @DisplayName("个人数据接口未登录 → 401（对比任务）")
        void comparisonsRequireLogin() throws Exception {
            requestPath("", "/api/comparisons");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
        }

        @Test
        @DisplayName("auth/me 未登录 → 401")
        void authMeRequiresLogin() throws Exception {
            requestPath("", "/api/auth/me");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
        }

        @Test
        @DisplayName("auth/password 未登录 → 401")
        void authPasswordRequiresLogin() throws Exception {
            requestPath("", "/api/auth/password");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
        }

        @Test
        @DisplayName("上下文为 null 时不裁剪，路径仍照常匹配")
        void nullContextPathIsHandled() throws Exception {
            requestPath(null, "/api/auth/me");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            verify(chain, never()).doFilter(any(), any());
            assertTrue(out.toString().contains("\"code\":401"), out.toString());
        }

        @Test
        @DisplayName("拒绝时 HTTP 状态码仍是 200，业务码写在响应体里（与 BaseServlet 一致）")
        void denyUsesHttp200WithBusinessCode() throws Exception {
            requestPath("", "/api/favorites");
            captureResponseBody();
            filter.doFilter(req, resp, chain);
            verify(resp, never()).setStatus(anyInt());
            verify(resp, never()).sendError(anyInt());
            assertTrue(out.toString().contains("\"success\":false"), out.toString());
        }
    }

    @Test
    @DisplayName("生命周期方法无副作用：init / destroy 不抛异常")
    void lifecycleMethodsAreNoOp() throws javax.servlet.ServletException {
        filter.init(null);
        filter.destroy();
    }
}
