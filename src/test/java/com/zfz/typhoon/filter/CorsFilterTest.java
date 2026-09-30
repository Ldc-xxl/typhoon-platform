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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CorsFilter} 单元测试。
 *
 * <h3>为什么不能用 {@code Access-Control-Allow-Origin: *}</h3>
 * <p>登录态基于 Cookie 会话，而规范明确禁止 {@code Allow-Credentials: true}
 * 与通配符 {@code *} 同时出现。所以本过滤器采用<b>白名单回显</b>：
 * 只有请求的 Origin 命中白名单时才回显该 Origin 并允许凭据，
 * 其余 Origin 一律不返回 CORS 头（浏览器自然拦截）。
 *
 * <p>这条规则要是被"顺手"改成通配符，站点就会被任意第三方页面借着用户
 * 登录态读走数据 —— 属于静默失效的安全缺陷，必须钉死在测试里。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorsFilter：跨域白名单过滤器")
class CorsFilterTest {

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse resp;
    @Mock
    private FilterChain chain;

    private CorsFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorsFilter();
    }

    /* ================================================================== */
    /*  白名单内                                                           */
    /* ================================================================== */

    @Nested
    @DisplayName("命中白名单的 Origin")
    class AllowedOrigins {

        @Test
        @DisplayName("回显 Origin 而不是通配符，并允许携带凭据")
        void echoesOriginAndAllowsCredentials() throws Exception {
            when(req.getHeader("Origin")).thenReturn("http://localhost:5500");
            when(req.getMethod()).thenReturn("GET");
            filter.doFilter(req, resp, chain);

            verify(resp).setHeader("Access-Control-Allow-Origin", "http://localhost:5500");
            verify(resp).setHeader("Access-Control-Allow-Credentials", "true");
            verify(resp).setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            verify(resp).setHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With, Accept");
            verify(resp).setHeader("Access-Control-Max-Age", "1800");
            verify(resp).setHeader("Vary", "Origin");
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("Origin 两侧有空白时先裁掉再比对")
        void trimsOriginBeforeMatching() throws Exception {
            when(req.getHeader("Origin")).thenReturn("  http://127.0.0.1:8080  ");
            when(req.getMethod()).thenReturn("POST");
            filter.doFilter(req, resp, chain);

            verify(resp).setHeader("Access-Control-Allow-Origin", "  http://127.0.0.1:8080  ");
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("白名单里的多个端口都能放行")
        void severalPortsAreAllowed() throws Exception {
            String[] origins = {
                    "http://localhost:3000", "http://127.0.0.1:3000",
                    "http://localhost:8081", "http://127.0.0.1:8081",
                    "http://localhost:5501"
            };
            for (String origin : origins) {
                HttpServletRequest r = org.mockito.Mockito.mock(HttpServletRequest.class);
                HttpServletResponse s = org.mockito.Mockito.mock(HttpServletResponse.class);
                FilterChain c = org.mockito.Mockito.mock(FilterChain.class);
                when(r.getHeader("Origin")).thenReturn(origin);
                when(r.getMethod()).thenReturn("GET");
                filter.doFilter(r, s, c);
                verify(s).setHeader(eq("Access-Control-Allow-Origin"), eq(origin));
                verify(c).doFilter(r, s);
            }
        }

        @Test
        @DisplayName("预检请求（OPTIONS）直接返回 204，不再走过滤器链")
        void preflightReturnsNoContent() throws Exception {
            when(req.getHeader("Origin")).thenReturn("http://localhost:5500");
            when(req.getMethod()).thenReturn("OPTIONS");
            filter.doFilter(req, resp, chain);

            verify(resp).setStatus(HttpServletResponse.SC_NO_CONTENT);
            verify(chain, never()).doFilter(req, resp);
        }
    }

    /* ================================================================== */
    /*  白名单外                                                           */
    /* ================================================================== */

    @Nested
    @DisplayName("不在白名单的 Origin")
    class ForeignOrigins {

        @Test
        @DisplayName("未知来源不返回任何 CORS 头（浏览器自行拦截）")
        void noHeadersForUnknownOrigin() throws Exception {
            when(req.getHeader("Origin")).thenReturn("https://evil.example.com");
            when(req.getMethod()).thenReturn("GET");
            filter.doFilter(req, resp, chain);

            verify(resp, never()).setHeader(anyString(), anyString());
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("缺 Origin 头（同源请求）时也不加 CORS 头")
        void noHeadersWhenOriginAbsent() throws Exception {
            when(req.getHeader("Origin")).thenReturn(null);
            when(req.getMethod()).thenReturn("GET");
            filter.doFilter(req, resp, chain);

            verify(resp, never()).setHeader(anyString(), anyString());
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("file:// 打开的页面 Origin 为 null，同样不放行")
        void nullOriginFromFileProtocol() throws Exception {
            when(req.getHeader("Origin")).thenReturn("null");
            when(req.getMethod()).thenReturn("GET");
            filter.doFilter(req, resp, chain);

            verify(resp, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
            verify(chain).doFilter(req, resp);
        }

        @Test
        @DisplayName("即便是 OPTIONS 预检，未知来源也不放行（只回 204）")
        void preflightWithoutCorsHeaders() throws Exception {
            when(req.getHeader("Origin")).thenReturn("https://evil.example.com");
            when(req.getMethod()).thenReturn("OPTIONS");
            filter.doFilter(req, resp, chain);

            verify(resp, never()).setHeader(anyString(), anyString());
            verify(resp).setStatus(HttpServletResponse.SC_NO_CONTENT);
            verify(chain, never()).doFilter(req, resp);
        }
    }

    @Test
    @DisplayName("非 HTTP 请求直接放行（过滤器只管 HTTP）")
    void nonHttpRequestPasses() throws Exception {
        ServletRequest plainReq = org.mockito.Mockito.mock(ServletRequest.class);
        ServletResponse plainResp = org.mockito.Mockito.mock(ServletResponse.class);
        filter.doFilter(plainReq, plainResp, chain);
        verify(chain).doFilter(plainReq, plainResp);
    }

    @Test
    @DisplayName("生命周期方法无副作用：init / destroy 不抛异常")
    void lifecycleMethodsAreNoOp() throws javax.servlet.ServletException {
        filter.init(null);
        filter.destroy();
    }
}
