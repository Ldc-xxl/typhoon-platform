package com.zfz.typhoon.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 静态资源浏览器缓存过滤器（性能优化 · 阶段七）。
 *
 * <h3>解决的问题</h3>
 * 优化前，Tomcat 默认只为静态文件（js / css / images / fonts）下发
 * {@code Last-Modified} / {@code ETag}，但<b>没有</b> {@code Cache-Control}，
 * 浏览器每次打开页面仍会对这些资源做一次"条件请求"（304 协商），
 * 即便内容没变也要付出一次网络往返。本平台首屏约需下载 350KB 静态资源，
 * 反复进入会造成不必要的开销。
 *
 * <h3>策略</h3>
 * <ul>
 *   <li><b>静态资源</b>（非 {@code /api/} 开头的请求）：下发
 *       {@code Cache-Control: public, max-age=86400}（1 天），
 *       浏览器在窗口内直接读本地缓存，零网络往返；</li>
 *   <li><b>接口响应</b>（{@code /api/} 开头）：下发
 *       {@code Cache-Control: no-store}，禁止浏览器/代理缓存含用户态的 JSON，
 *       保证数据总是最新。</li>
 * </ul>
 *
 * <p>注意：缓存失效靠"改文件名 / 加版本查询串"实现（如 {@code api.js?v=2}）。
 * 本过滤器只在响应头层面做缓存协商，不拦截业务逻辑。
 *
 * @author 追风者项目组
 */
@WebFilter(filterName = "cacheFilter", urlPatterns = "/*")
public class CacheFilter implements Filter {

    /** 静态资源缓存时长：1 天（秒） */
    private static final int STATIC_MAX_AGE = 86400;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest) || !(resp instanceof HttpServletResponse)) {
            chain.doFilter(req, resp);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/api/")) {
            // 接口：禁止缓存，永远取最新
            response.setHeader("Cache-Control", "no-store");
        } else {
            // 静态资源：允许浏览器缓存 1 天
            response.setHeader("Cache-Control", "public, max-age=" + STATIC_MAX_AGE);
        }

        chain.doFilter(req, resp);
    }
}
