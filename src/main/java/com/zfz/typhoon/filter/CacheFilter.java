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
 * <h3>策略（2026-10 修订）</h3>
 * <ul>
 *   <li><b>接口响应</b>（{@code /api/} 开头）：{@code Cache-Control: no-store}，
 *       禁止浏览器/代理缓存含用户态的 JSON，保证数据总是最新；</li>
 *   <li><b>第三方库</b>（{@code /vendor/} 下的 jquery / bootstrap）：
 *       {@code public, max-age=86400}（1 天）。这些文件随依赖版本变化，
 *       不会在开发过程中被改动，适合长缓存；</li>
 *   <li><b>其余静态资源</b>（页面 HTML、{@code js/}、{@code css/}、{@code assets/}）：
 *       {@code no-cache}。</li>
 * </ul>
 *
 * <h3>为什么从"统一 1 天强缓存"改成 {@code no-cache}</h3>
 * 原先对本项目自身的 js / css / html 也下发 {@code max-age=86400}，
 * 而缓存失效只能靠人工"改文件名 / 加版本查询串"。实际开发中这一步极易被遗漏，
 * 后果是<b>代码明明已经更新，浏览器却仍在跑 24 小时前的旧脚本</b>：
 * 典型症状是新页面调用新接口方法时报
 * {@code ZFZ.api.xxx is not a function}、页面样式与源码对不上号，
 * 排查时极容易被误判成后端缺陷。
 *
 * <p>{@code no-cache} 的语义不是"不缓存"，而是"可以缓存，但每次使用前必须回源校验"。
 * 配合 Tomcat 自动下发的 {@code ETag} / {@code Last-Modified}，
 * 内容没变时返回 {@code 304 Not Modified}——<b>不传响应体</b>，
 * 首屏那 350KB 依然省得下来，只是保留了一次极轻量的校验往返。
 * 对本地 / 内网部署而言这点开销可以忽略，换来的是"改完刷新即生效"。</p>
 *
 * <p>注意：本过滤器只在响应头层面做缓存协商，不拦截业务逻辑。
 * 若将来引入前端构建产物（带内容指纹的文件名），可把对应目录重新改回长缓存。</p>
 *
 * @author 追风者项目组
 */
@WebFilter(filterName = "cacheFilter", urlPatterns = "/*")
public class CacheFilter implements Filter {

    /** 第三方库缓存时长：1 天（秒） */
    private static final int VENDOR_MAX_AGE = 86400;

    /** 接口路径片段 */
    private static final String API_MARK = "/api/";

    /** 第三方库路径片段 */
    private static final String VENDOR_MARK = "/vendor/";

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
        if (uri == null) {
            uri = "";
        }

        if (uri.contains(API_MARK)) {
            // 接口：禁止缓存，永远取最新
            response.setHeader("Cache-Control", "no-store");
        } else if (uri.contains(VENDOR_MARK)) {
            // 第三方库：内容稳定、体积大，允许多天缓存不校验
            response.setHeader("Cache-Control", "public, max-age=" + VENDOR_MAX_AGE);
        } else {
            // 本项目自身的 HTML / JS / CSS / 静态资源：
            // 允许缓存但每次必须回源校验（内容未变时返回 304，不重传响应体）。
            // 这样源码一改、刷新即生效，不会再出现"页面跑着旧脚本"的假故障。
            response.setHeader("Cache-Control", "no-cache");
        }

        chain.doFilter(req, resp);
    }
}
