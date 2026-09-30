package com.zfz.typhoon.servlet;

import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DataAccessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.Result;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 所有业务 Servlet 的基类：统一异常处理、统一响应写出、统一路径解析。
 *
 * <h3>关键契约：HTTP 状态码恒为 200</h3>
 * 前端 {@code api.js} 在 jQuery 的 {@code done} 回调里判断
 * {@code res.code === 200 || res.code === 0}，失败时读取 {@code res.message} 作为提示文案。
 * 如果后端把业务失败写成 HTTP 404 / 500，请求会落到 {@code fail} 回调，
 * 前端只能拿到「请求失败：500 Internal Server Error」——
 * 我们在服务层精心写的「该台风暂无轨迹数据」这类提示就全丢了。
 *
 * <p>因此本基类的约定是：<b>业务成败一律通过响应体里的 {@code code} 表达，
 * HTTP 状态码保持 200</b>。只有真正的传输层异常（Servlet 容器自己抛的）才会是非 200。
 *
 * <h3>异常到响应的映射</h3>
 * <ul>
 *   <li>{@link BusinessException} → 原样透出它的 code 与 message（这是给用户看的）；</li>
 *   <li>{@link DataAccessException} → 500 + 通用文案，SQL 细节只写日志
 *       （错误提示里带上 SQL 既没有帮助，又泄露表结构）；</li>
 *   <li>其他 {@link Exception} → 500 + 通用文案，堆栈写日志。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** 会话中存用户ID的属性名 */
    public static final String ATTR_USER_ID = "userId";
    /** 会话中存角色的属性名 */
    public static final String ATTR_ROLE = "role";
    /** 会话中存展示名的属性名 */
    public static final String ATTR_USER_NAME = "userName";

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        try {
            super.service(req, resp);
        } catch (BusinessException e) {
            // 业务异常：message 是精心写给用户看的，直接透出
            JsonUtil.write(resp, Result.fail(e.getCode(), e.getMessage()));
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "数据访问异常 sql=" + e.getSql(), e);
            JsonUtil.write(resp, Result.error("数据访问失败，请稍后重试或联系管理员"));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "接口处理异常 " + req.getMethod() + " " + req.getRequestURI(), e);
            JsonUtil.write(resp, Result.error("服务器内部错误：" + e.getClass().getSimpleName()));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  响应快捷方式                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 写出成功响应（带数据）。
     *
     * @param resp HTTP 响应
     * @param data 业务数据
     */
    protected void ok(HttpServletResponse resp, Object data) {
        JsonUtil.write(resp, Result.ok(data));
    }

    /**
     * 写出成功响应（无数据）。
     *
     * @param resp HTTP 响应
     */
    protected void ok(HttpServletResponse resp) {
        JsonUtil.write(resp, Result.ok());
    }

    /**
     * 写出成功响应并自定义提示文案。
     *
     * @param resp    HTTP 响应
     * @param message 提示文案
     * @param data    业务数据
     */
    protected void ok(HttpServletResponse resp, String message, Object data) {
        JsonUtil.write(resp, Result.ok(message, data));
    }

    /* ------------------------------------------------------------------ */
    /*  路径解析                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 解析 {@code pathInfo} 为路径段数组。
     *
     * <p>例：映射 {@code /api/typhoons/*}，请求 {@code /api/typhoons/13/tracks}
     * 时 {@code pathInfo} 为 {@code /13/tracks}，本方法返回 {@code ["13", "tracks"]}。
     *
     * <p>返回空数组而不是 null，调用方就不必到处判空。
     *
     * @param req HTTP 请求
     * @return 路径段数组（可能为空数组）
     */
    protected String[] pathSegments(HttpServletRequest req) {
        String info = req.getPathInfo();
        if (info == null || info.isEmpty() || "/".equals(info)) {
            return new String[0];
        }
        String[] raw = info.split("/");
        List<String> segs = new ArrayList<String>(raw.length);
        for (String s : raw) {
            if (s != null && !s.isEmpty()) {
                segs.add(s);
            }
        }
        return segs.toArray(new String[0]);
    }

    /**
     * 取路径第 {@code index} 段。
     *
     * @param segments 路径段
     * @param index    下标
     * @return 段内容；越界返回 null
     */
    protected String segment(String[] segments, int index) {
        return segments != null && index >= 0 && index < segments.length ? segments[index] : null;
    }

    /* ------------------------------------------------------------------ */
    /*  会话                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 取当前登录用户ID。
     *
     * @param req HTTP 请求
     * @return 用户ID；未登录返回 null
     */
    protected Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object v = session.getAttribute(ATTR_USER_ID);
        return v instanceof Long ? (Long) v : null;
    }

    /**
     * 取当前登录用户ID，未登录抛 401。
     *
     * @param req HTTP 请求
     * @return 用户ID
     */
    protected long requireUserId(HttpServletRequest req) {
        Long id = currentUserId(req);
        if (id == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "请先登录后再操作");
        }
        return id.longValue();
    }

    /**
     * 判断当前会话是否为管理员。
     *
     * @param req HTTP 请求
     * @return 管理员返回 true
     */
    protected boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        Object role = session.getAttribute(ATTR_ROLE);
        return role != null && "ADMIN".equalsIgnoreCase(String.valueOf(role));
    }

    /**
     * 要求管理员身份，否则抛 401 / 403。
     *
     * <h3>为什么要先判登录状态</h3>
     * 早期版本这里只做 {@code isAdmin} 判定，未登录与"已登录但非管理员"
     * 两种完全不同的情况都返回 403「该操作仅平台管理员可用」——
     * 对一个根本没登录的用户说"你没有管理员权限"是误导：他该做的是登录，
     * 而不是去找管理员要权限。
     *
     * <p>这里与 {@link com.zfz.typhoon.filter.AuthFilter} 的口径保持一致：
     * <ul>
     *   <li>未登录 → 401「请先登录后再操作」；</li>
     *   <li>已登录但角色不是 ADMIN → 403「该操作仅平台管理员可用」。</li>
     * </ul>
     * 前端 {@code api.js} 对 401 有"跳登录页"的特殊处理，
     * 返回正确的 401 才能触发那条路径。
     *
     * <p>正常情况下 {@code /api/admin/**} 的匿名请求会先被
     * {@code AuthFilter} 拦下（也是 401），本方法是过滤器被绕过或
     * 配置遗漏时的最后一道防线，两者语义必须一致。
     *
     * @param req HTTP 请求
     */
    protected void requireAdmin(HttpServletRequest req) {
        if (currentUserId(req) == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "请先登录后再操作");
        }
        if (!isAdmin(req)) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "该操作仅平台管理员可用");
        }
    }

    /**
     * 要求已登录。
     *
     * @param req HTTP 请求
     */
    protected void requireLogin(HttpServletRequest req) {
        requireUserId(req);
    }

    /* ------------------------------------------------------------------ */
    /*  其他                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 405 兜底：父类默认对未实现的方法返回 405 HTML 页，与 JSON 契约不符。
     *
     * @param req  HTTP 请求
     * @param resp HTTP 响应
     */
    protected void methodNotAllowed(HttpServletRequest req, HttpServletResponse resp) {
        JsonUtil.write(resp, Result.fail(405,
                "该接口不支持 " + req.getMethod() + " 方法：" + req.getRequestURI()));
    }

    /**
     * 404 兜底：路径未命中任何分支时调用。
     *
     * @param resp HTTP 响应
     * @param path 未命中的路径
     */
    protected void notFound(HttpServletResponse resp, String path) {
        JsonUtil.write(resp, Result.notFound("接口不存在：" + path));
    }

    /**
     * 可选地取查询参数中的用户ID过滤条件（管理端"只看某人的数据"场景）。
     *
     * @param req  HTTP 请求
     * @param name 参数名
     * @return 值；未传返回 null
     */
    protected Long optionalLong(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest(name + " 必须为整数：" + v);
        }
    }
}
