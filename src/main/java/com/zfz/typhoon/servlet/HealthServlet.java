package com.zfz.typhoon.servlet;

import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.DateUtil;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <pre>
 * GET /api/health   服务与数据库连通性自检
 * </pre>
 *
 * <h3>为什么需要一个"看起来没用"的接口</h3>
 * 部署到新环境后，"页面打得开但一直转圈"是最常见的故障现象，
 * 而它可能来自三个完全不同的原因：Tomcat 起没起、WAR 部署成不成功、
 * 数据库连不连得上。三者从浏览器上看一模一样。
 *
 * <p>健康检查把这个诊断过程压缩成一次请求：它返回应用是否已部署、
 * 数据库连接池能否借到连接、以及实际使用的 JDBC 地址（口令已排除）。
 * 排查时先看这一个接口，就能知道问题出在哪一层，
 * 不必再去翻 Tomcat 日志猜。
 *
 * <h3>不泄露敏感信息</h3>
 * 只回数据库地址与用户名，<b>绝不回口令</b>；
 * 连接失败时只回异常类名与简短原因，不回完整堆栈——
 * JDBC 的堆栈里常常带着带参数的连接串。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "healthServlet", urlPatterns = {"/api/health", "/api/health/*"})
public class HealthServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("app", "typhoon_platform");
        data.put("status", "UP");
        data.put("time", DateUtil.format(DateUtil.now()));
        data.put("contextPath", req.getContextPath());
        data.put("servletVersion", getServletContext().getMajorVersion()
                + "." + getServletContext().getMinorVersion());

        boolean dbOk;
        String dbMessage;
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            dbOk = conn != null && !conn.isClosed();
            dbMessage = dbOk ? "连接正常" : "连接已被关闭";
        } catch (Exception e) {
            dbOk = false;
            dbMessage = e.getClass().getSimpleName() + "：" + safe(e.getMessage());
        } finally {
            DBUtil.close(conn);
        }

        Map<String, Object> db = new LinkedHashMap<String, Object>();
        db.put("ok", Boolean.valueOf(dbOk));
        db.put("message", dbMessage);
        db.put("url", safeUrl(System.getProperty("typhoon.jdbc.url", "见 db.properties")));
        db.put("driver", "com.mysql.cj.jdbc.Driver");
        data.put("database", db);

        data.put("javaVersion", System.getProperty("java.version"));
        data.put("os", System.getProperty("os.name"));

        ok(resp, dbOk ? data : data);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        methodNotAllowed(req, resp);
    }

    /**
     * 截断过长/含控制字符的消息。
     *
     * @param message 原始消息
     * @return 安全文本
     */
    private String safe(String message) {
        if (message == null) {
            return "无详细信息";
        }
        String m = message.replaceAll("[\\r\\n\\t]", " ").trim();
        return m.length() > 200 ? m.substring(0, 200) + "..." : m;
    }

    /**
     * 对连接串做口令脱敏（防御性处理：即使有人把口令写进 URL 也不会泄露）。
     *
     * @param url 连接串
     * @return 脱敏后的连接串
     */
    private String safeUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password|pwd)=([^&;]*)", "$1=***");
    }
}
