package com.zfz.typhoon.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 跨域请求过滤器。
 *
 * <h3>为什么需要它</h3>
 * 正式部署时前端页面与接口同源（都在同一个 WAR 里），浏览器不会发起 CORS 预检。
 * 但在<b>开发阶段</b>常见的两种用法会跨域：
 * <ol>
 *   <li>用 Live Server / VSCode 等工具在 5500 端口起一个纯静态前端，
 *       接口仍在 8080 的 Tomcat 上；</li>
 *   <li>前端直接以 {@code file://} 打开（此时 Origin 为 {@code null}）。</li>
 * </ol>
 * 没有本过滤器，这两种用法下浏览器会直接拦掉响应，且报错信息只会说
 * "CORS policy"，很难与"接口 404"区分开。
 *
 * <h3>安全边界：为什么不能用 {@code Access-Control-Allow-Origin: *}</h3>
 * 因为登录态基于 Cookie 会话。规范明确规定：
 * {@code Allow-Credentials: true} 与通配符 {@code *} 不能同时使用。
 * 所以本过滤器采用<b>白名单回显</b>：只有当请求的 Origin 命中
 * {@link #ALLOWED_ORIGINS} 时才回显该 Origin 并允许携带凭据，
 * 其余 Origin 一律不返回 CORS 头（浏览器自然拦截）。
 *
 * <p>白名单只含本机地址，生产部署时按实际域名增补。
 * 这样即使被恶意站点嵌入，也无法借助用户浏览器的登录态读取台风数据。
 *
 * @author 追风者项目组
 */
@WebFilter(filterName = "corsFilter", urlPatterns = "/api/*")
public class CorsFilter implements Filter {

    /** 允许携带凭据的跨域来源白名单（开发阶段的本机地址） */
    private static final Set<String> ALLOWED_ORIGINS = new LinkedHashSet<String>(Arrays.asList(
            "http://localhost:5500", "http://127.0.0.1:5500",
            "http://localhost:5501", "http://127.0.0.1:5501",
            "http://localhost:3000", "http://127.0.0.1:3000",
            "http://localhost:8080", "http://127.0.0.1:8080",
            "http://localhost:8081", "http://127.0.0.1:8081"
    ));

    /** 预检请求缓存时间（秒），减少 OPTIONS 往返 */
    private static final int PREFLIGHT_MAX_AGE = 1800;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 无初始化参数
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String origin = req.getHeader("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin.trim())) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
            resp.setHeader("Access-Control-Allow-Credentials", "true");
            resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With, Accept");
            resp.setHeader("Access-Control-Max-Age", String.valueOf(PREFLIGHT_MAX_AGE));
            // 同一 URL 不同 Origin 的响应不能相互复用，否则会串了 CORS 头
            resp.setHeader("Vary", "Origin");
        }

        // 预检请求直接返回：它不带业务参数，继续走过滤器链没有意义
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 无需清理
    }
}
