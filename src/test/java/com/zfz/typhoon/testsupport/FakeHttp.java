package com.zfz.typhoon.testsupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servlet 层的测试替身：基于 {@link Proxy} 手写，<b>不用 Mockito</b>。
 *
 * <h3>为什么不用 Mockito</h3>
 * <p>本项目跑在 JDK 25 上，Mockito 的 inline mock maker 无法改写 JDK 模块内的类
 * （{@code java.lang.Object}、{@code java.sql.*}）。Servlet API 本身虽然在
 * 命名空间之外可以 mock，但一旦被测代码顺带用到 {@code java.sql} 就会连带失败。
 * 更关键的是：Servlet 测试要模拟的是"一个请求来了长什么样"，
 * 本质是<b>数据</b>而不是<b>行为</b>——用 Proxy 直接给数据更直观，也更快。
 *
 * <h3>未实现方法的处理</h3>
 * <p>这里刻意对未显式设置的方法返回<b>类型默认值</b>（对象 null、数字 0、布尔 false），
 * 而不是抛 {@code UnsupportedOperationException}。理由是 Servlet API 方法极多，
 * 逐个实现会让替身比被测代码还长；返回默认值能让测试先跑起来，
 * 真遇到"某个参数没生效"再回来补对应的分支。
 *
 * <h3>响应体</h3>
 * <p>{@link Resp} 用一个 {@link StringWriter} 收集 {@link PrintWriter} 写出的内容，
 * 测试据此断言 JSON 响应体 —— 这是验证"统一响应格式"是否真的生效的唯一办法。
 *
 * @author 追风者项目组
 */
public final class FakeHttp {

    private FakeHttp() {
    }

    /* ================================================================== */
    /*  请求                                                               */
    /* ================================================================== */

    /** 请求替身 */
    public static final class Req {

        private String method = "GET";
        private String contextPath = "/typhoon_platform";
        private String pathInfo = null;
        private String servletPath = "/api/x";
        private String requestUri = null;
        private String queryString = "";
        private String body = "";
        private String remoteAddr = "127.0.0.1";
        private final Map<String, String> params = new LinkedHashMap<String, String>();
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private Map<String, Object> sessionAttrs = null;

        /**
         * @param method   HTTP 方法
         * @param pathInfo 如 {@code "/13/tracks"}；传 null 表示无子路径
         */
        public Req(String method, String pathInfo) {
            this.method = method;
            this.pathInfo = pathInfo;
        }

        public static Req get(String pathInfo) {
            return new Req("GET", pathInfo);
        }

        public static Req post(String pathInfo, String jsonBody) {
            Req r = new Req("POST", pathInfo);
            r.body = jsonBody == null ? "" : jsonBody;
            return r;
        }

        public Req param(String name, String value) {
            params.put(name, value);
            return this;
        }

        public Req params(Map<String, String> values) {
            if (values != null) {
                params.putAll(values);
            }
            return this;
        }

        public Req header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Req uri(String uri) {
            this.requestUri = uri;
            return this;
        }

        /** 部分 Servlet 靠 getServletPath() 区分分支（如 MetaServlet 判 /api/agencies） */
        public Req servletPath(String path) {
            this.servletPath = path;
            return this;
        }

        public Req contextPath(String ctx) {
            this.contextPath = ctx;
            return this;
        }

        /** 登录：会话里放 userId / role / userName */
        public Req login(long userId, String role) {
            Map<String, Object> attrs = new LinkedHashMap<String, Object>();
            attrs.put("userId", Long.valueOf(userId));
            attrs.put("role", role);
            attrs.put("userName", "测试用户");
            this.sessionAttrs = attrs;
            return this;
        }

        /** 未登录：getSession(false) 返回 null */
        public Req anonymous() {
            this.sessionAttrs = null;
            return this;
        }

        public HttpServletRequest build() {
            final Req self = this;
            HttpServletRequest proxy = (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object p, Method m, Object[] args) {
                            String n = m.getName();
                            if ("getMethod".equals(n)) {
                                return self.method;
                            }
                            if ("getPathInfo".equals(n)) {
                                return self.pathInfo;
                            }
                            if ("getContextPath".equals(n)) {
                                return self.contextPath;
                            }
                            if ("getRequestURI".equals(n)) {
                                if (self.requestUri != null) {
                                    return self.requestUri;
                                }
                                return (self.contextPath == null ? "" : self.contextPath)
                                        + "/api/x" + (self.pathInfo == null ? "" : self.pathInfo);
                            }
                            if ("getRequestURL".equals(n)) {
                                return new StringBuffer("http://localhost:8080"
                                        + (self.contextPath == null ? "" : self.contextPath) + "/api/x");
                            }
                            if ("getServletPath".equals(n)) {
                                return self.servletPath;
                            }
                            if ("getPathTranslated".equals(n)) {
                                return self.servletPath;
                            }
                            if ("getQueryString".equals(n)) {
                                return self.queryString;
                            }
                            if ("getParameter".equals(n)) {
                                return self.params.get(String.valueOf(args[0]));
                            }
                            if ("getParameterValues".equals(n)) {
                                String v = self.params.get(String.valueOf(args[0]));
                                return v == null ? null : new String[]{v};
                            }
                            if ("getParameterNames".equals(n)) {
                                return enumeration(self.params.keySet());
                            }
                            if ("getParameterMap".equals(n)) {
                                Map<String, String[]> copy = new LinkedHashMap<String, String[]>();
                                for (Map.Entry<String, String> e : self.params.entrySet()) {
                                    copy.put(e.getKey(), new String[]{e.getValue()});
                                }
                                return copy;
                            }
                            if ("getHeader".equals(n)) {
                                return self.headers.get(String.valueOf(args[0]));
                            }
                            if ("getHeaderNames".equals(n)) {
                                return enumeration(self.headers.keySet());
                            }
                            if ("getRemoteAddr".equals(n)) {
                                return self.remoteAddr;
                            }
                            if ("getSession".equals(n)) {
                                boolean create = args != null && args.length > 0
                                        && Boolean.TRUE.equals(args[0]);
                                if (self.sessionAttrs == null && !create) {
                                    return null;
                                }
                                return session(self.sessionAttrs == null
                                        ? new LinkedHashMap<String, Object>() : self.sessionAttrs);
                            }
                            if ("getReader".equals(n)) {
                                return new BufferedReader(new StringReader(self.body));
                            }
                            if ("toString".equals(n)) {
                                return "FakeRequest[" + self.method + " " + self.pathInfo + "]";
                            }
                            if ("hashCode".equals(n)) {
                                return System.identityHashCode(p);
                            }
                            if ("equals".equals(n)) {
                                return p == args[0];
                            }
                            return defaultValue(m.getReturnType());
                        }
                    });
            return proxy;
        }
    }

    private static HttpSession session(final Map<String, Object> attrs) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object p, Method m, Object[] args) {
                        String n = m.getName();
                        if ("getAttribute".equals(n)) {
                            return attrs.get(String.valueOf(args[0]));
                        }
                        if ("setAttribute".equals(n)) {
                            attrs.put(String.valueOf(args[0]), args[1]);
                            return null;
                        }
                        if ("removeAttribute".equals(n)) {
                            attrs.remove(String.valueOf(args[0]));
                            return null;
                        }
                        if ("getAttributeNames".equals(n)) {
                            return enumeration(attrs.keySet());
                        }
                        if ("getId".equals(n)) {
                            return "FAKE-SESSION";
                        }
                        if ("getMaxInactiveInterval".equals(n)) {
                            return Integer.valueOf(1800);
                        }
                        if ("isNew".equals(n)) {
                            return Boolean.FALSE;
                        }
                        if ("toString".equals(n)) {
                            return "FakeSession" + attrs;
                        }
                        if ("hashCode".equals(n)) {
                            return System.identityHashCode(p);
                        }
                        if ("equals".equals(n)) {
                            return p == args[0];
                        }
                        return defaultValue(m.getReturnType());
                    }
                });
    }

    /* ================================================================== */
    /*  响应                                                               */
    /* ================================================================== */

    /** 响应替身：收集写出的响应体、状态码与响应头 */
    public static final class Resp {

        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body, true);
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private int status = 200;
        private String contentType;
        private String charset;

        public HttpServletResponse build() {
            final Resp self = this;
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object p, Method m, Object[] args) {
                            String n = m.getName();
                            if ("getWriter".equals(n)) {
                                return self.writer;
                            }
                            if ("setStatus".equals(n)) {
                                self.status = ((Integer) args[0]).intValue();
                                return null;
                            }
                            if ("getStatus".equals(n)) {
                                return Integer.valueOf(self.status);
                            }
                            if ("sendError".equals(n)) {
                                self.status = ((Integer) args[0]).intValue();
                                return null;
                            }
                            if ("setContentType".equals(n)) {
                                self.contentType = String.valueOf(args[0]);
                                return null;
                            }
                            if ("getContentType".equals(n)) {
                                return self.contentType;
                            }
                            if ("setCharacterEncoding".equals(n)) {
                                self.charset = String.valueOf(args[0]);
                                return null;
                            }
                            if ("getCharacterEncoding".equals(n)) {
                                return self.charset;
                            }
                            if ("setHeader".equals(n)) {
                                self.headers.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                return null;
                            }
                            if ("addHeader".equals(n)) {
                                self.headers.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                return null;
                            }
                            if ("getHeader".equals(n)) {
                                return self.headers.get(String.valueOf(args[0]));
                            }
                            if ("containsHeader".equals(n)) {
                                return Boolean.valueOf(self.headers.containsKey(String.valueOf(args[0])));
                            }
                            if ("flushBuffer".equals(n)) {
                                self.writer.flush();
                                return null;
                            }
                            if ("toString".equals(n)) {
                                return "FakeResponse[status=" + self.status + "]";
                            }
                            if ("hashCode".equals(n)) {
                                return System.identityHashCode(p);
                            }
                            if ("equals".equals(n)) {
                                return p == args[0];
                            }
                            return defaultValue(m.getReturnType());
                        }
                    });
        }

        /** 已写出的响应体 */
        public String body() {
            writer.flush();
            return body.toString();
        }

        public int status() {
            return status;
        }

        public String header(String name) {
            return headers.get(name);
        }

        public String contentType() {
            return contentType;
        }
    }

    public static Resp resp() {
        return new Resp();
    }

    /**
     * 用假的 {@code ServletConfig / ServletContext} 初始化 Servlet。
     *
     * <p>有些 Servlet（如健康检查）会调 {@code getServletContext().getMajorVersion()}；
     * 不初始化的话该调用会 NPE，而这类 NPE 只在"容器真的把它装起来"之后才不出现，
     * 单测里必须先补上这一步。
     *
     * @param servlet 待初始化的 Servlet
     */
    public static void initWithFakeContext(javax.servlet.http.HttpServlet servlet)
            throws javax.servlet.ServletException {
        final javax.servlet.ServletContext ctx = (javax.servlet.ServletContext) Proxy.newProxyInstance(
                javax.servlet.ServletContext.class.getClassLoader(),
                new Class<?>[]{javax.servlet.ServletContext.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object p, Method m, Object[] args) {
                        String n = m.getName();
                        if ("getMajorVersion".equals(n)) {
                            return Integer.valueOf(4);
                        }
                        if ("getMinorVersion".equals(n)) {
                            return Integer.valueOf(0);
                        }
                        if ("getContextPath".equals(n)) {
                            return "/typhoon_platform";
                        }
                        if ("getServerInfo".equals(n)) {
                            return "FakeServletContext";
                        }
                        if ("toString".equals(n)) {
                            return "FakeServletContext";
                        }
                        if ("hashCode".equals(n)) {
                            return System.identityHashCode(p);
                        }
                        if ("equals".equals(n)) {
                            return p == args[0];
                        }
                        return defaultValue(m.getReturnType());
                    }
                });
        javax.servlet.ServletConfig cfg = (javax.servlet.ServletConfig) Proxy.newProxyInstance(
                javax.servlet.ServletConfig.class.getClassLoader(),
                new Class<?>[]{javax.servlet.ServletConfig.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object p, Method m, Object[] args) {
                        String n = m.getName();
                        if ("getServletContext".equals(n)) {
                            return ctx;
                        }
                        if ("getServletName".equals(n)) {
                            return "fakeServlet";
                        }
                        if ("toString".equals(n)) {
                            return "FakeServletConfig";
                        }
                        if ("hashCode".equals(n)) {
                            return System.identityHashCode(p);
                        }
                        if ("equals".equals(n)) {
                            return p == args[0];
                        }
                        return defaultValue(m.getReturnType());
                    }
                });
        servlet.init(cfg);
    }

    /* ================================================================== */
    /*  工具                                                               */
    /* ================================================================== */

    private static Enumeration<String> enumeration(Iterable<String> items) {
        final java.util.List<String> list = new java.util.ArrayList<String>();
        for (String s : items) {
            list.add(s);
        }
        if (list.isEmpty()) {
            return Collections.enumeration(list);
        }
        return java.util.Collections.enumeration(list);
    }

    /** 返回类型默认值：对象 null、数字 0、布尔 false、void 忽略 */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return Character.valueOf('\0');
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        if (type == int.class || type == short.class || type == byte.class) {
            return Integer.valueOf(0);
        }
        if (type == double.class) {
            return Double.valueOf(0D);
        }
        if (type == float.class) {
            return Float.valueOf(0F);
        }
        if (type == void.class) {
            return null;
        }
        if (Modifier.isPublic(type.getModifiers())) {
            return null;
        }
        return null;
    }

    /** 便于断言：把 IOException 包成非受检异常，省得每个测试都写 throws */
    public static RuntimeException wrap(IOException e) {
        return new IllegalStateException(e);
    }
}
