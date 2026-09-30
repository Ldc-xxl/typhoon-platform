package com.zfz.typhoon.filter;

import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.Result;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 接口鉴权过滤器。
 *
 * <h3>为什么鉴权放在 Filter 而不是每个 Servlet 里</h3>
 * 如果靠每个 Servlet 自己记得调用一次权限校验，那么<b>新写的接口只要忘一次
 * 就是一个越权漏洞</b>，而这类漏洞不会在功能测试里暴露——功能是好的，
 * 只是别人也能用。放在过滤器里，"默认拒绝"成为框架行为，
 * 漏配的表现是"接口用不了"（立刻能发现）而不是"接口谁都能用"（很难发现）。
 *
 * <h3>拦截规则</h3>
 * <table border="1">
 *   <tr><th>路径</th><th>要求</th><th>理由</th></tr>
 *   <tr><td>{@code /api/admin/**}</td><td>管理员</td><td>后台用户/参数/数据源管理</td></tr>
 *   <tr><td>{@code /api/notifications/**}</td><td>登录</td><td>通知属于个人数据</td></tr>
 *   <tr><td>{@code /api/favorites/**}</td><td>登录</td><td>收藏属于个人数据</td></tr>
 *   <tr><td>{@code /api/subscriptions/**}</td><td>登录</td><td>订阅属于个人数据</td></tr>
 *   <tr><td>{@code /api/comparisons/**}</td><td>登录</td><td>对比任务归属创建者</td></tr>
 *   <tr><td>{@code /api/auth/me}、{@code /api/auth/password}</td><td>登录</td><td>操作的是"当前用户"</td></tr>
 *   <tr><td>其余（台风档案、城市、机构、AI 测算、风险评估、相似案例、沙盘、问卷、导出）</td>
 *       <td>公开</td><td>本平台是面向公众的科普站点，这些数据本就公开</td></tr>
 * </table>
 *
 * <p><b>注意</b>：过滤器只做"这一大类路径需要什么身份"的判断，
 * 具体到"这条记录是不是你的"这类<b>数据级权限</b>仍由 Service 层校验
 * （例如 {@code ComparisonServiceImpl.get} 会比对 userId）。
 * 两者是不同层次的东西，缺一不可：过滤器挡不住"用户A读了用户B的任务"。
 *
 * <h3>响应形式</h3>
 * 与 {@code BaseServlet} 保持一致：HTTP 状态码 200，业务码写在响应体里，
 * 这样前端 {@code api.js} 能拿到「请先登录」这一类可读文案。
 *
 * @author 追风者项目组
 */
@WebFilter(filterName = "authFilter", urlPatterns = "/api/*")
public class AuthFilter implements Filter {

    /** 需要管理员身份的路径前缀 */
    private static final String[] ADMIN_PREFIXES = {
            "/api/admin/"
    };

    /** 需要登录的路径前缀 */
    private static final String[] LOGIN_PREFIXES = {
            "/api/notifications",
            "/api/favorites",
            "/api/subscriptions",
            "/api/comparisons",
            "/api/auth/me",
            "/api/auth/password"
    };

    /** 公开的认证接口（否则没人能登录） */
    private static final String AUTH_LOGIN = "/api/auth/login";

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
        String path = req.getRequestURI();
        // requestURI 含应用上下文（如 /typhoon_platform/api/admin/users），
        // 这里统一裁掉上下文，规则表才能用绝对路径书写
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }

        if (AUTH_LOGIN.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute("userId");
        Object role = session == null ? null : session.getAttribute("role");

        if (startsWithAny(path, ADMIN_PREFIXES)) {
            if (userId == null) {
                deny(resp, Result.CODE_UNAUTHORIZED, "请先登录后再访问管理后台");
                return;
            }
            if (role == null || !"ADMIN".equalsIgnoreCase(String.valueOf(role))) {
                deny(resp, Result.CODE_FORBIDDEN, "该功能仅平台管理员可用");
                return;
            }
        } else if (startsWithAny(path, LOGIN_PREFIXES)) {
            if (userId == null) {
                deny(resp, Result.CODE_UNAUTHORIZED, "请先登录后再操作");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 无需清理
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 判断路径是否命中任一前缀。
     *
     * @param path     已裁掉上下文的路径
     * @param prefixes 前缀数组
     * @return 命中返回 true
     */
    private boolean startsWithAny(String path, String[] prefixes) {
        for (String p : prefixes) {
            if (path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拒绝请求并写出统一格式的响应。
     *
     * @param resp    HTTP 响应
     * @param code    业务状态码
     * @param message 提示文案
     */
    private void deny(HttpServletResponse resp, int code, String message) {
        JsonUtil.write(resp, Result.fail(code, message));
    }
}
