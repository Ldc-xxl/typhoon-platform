package com.zfz.typhoon.servlet;

import com.zfz.typhoon.model.vo.LoginVO;
import com.zfz.typhoon.model.vo.UserVO;
import com.zfz.typhoon.service.AuthService;
import com.zfz.typhoon.service.impl.AuthServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.Result;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口。
 *
 * <pre>
 * POST   /api/auth/login     登录          body: {username, password}
 * POST   /api/auth/register  注册（注册即登录）body: {username, password, nickname?, email?}
 * POST   /api/auth/logout    退出登录
 * GET    /api/auth/me        取当前登录用户
 * PUT    /api/auth/password  修改本人密码   body: {oldPassword, newPassword}
 * </pre>
 *
 * <h3>会话设计</h3>
 * 登录成功后把 {@code userId / role / userName} 写进 HttpSession，
 * 前端拿到的 {@code token} 仅用于 localStorage 里做"是否已登录"的展示判断，
 * <b>真正的鉴权依据是容器会话</b>。这样做的原因是：
 * 课程场景没有引入 JWT 等无状态令牌的必要，而 HttpSession 天然带来
 * {@code HttpOnly} Cookie、会话超时、防会话固定等能力，
 * 比自行实现一个 token 校验器更不容易出错。
 *
 * <h3>退出登录</h3>
 * 直接 {@code invalidate()} 整个会话，而不是只删 userId 属性——
 * 后者会留下一个"半死"的会话，如果别处还存过数据，行为会变得难以预测。
 *
 * @author 追风者项目组
 */
@WebServlet(name = "authServlet", urlPatterns = "/api/auth/*")
public class AuthServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** 登录请求体 */
    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /** 注册请求体 */
    public static class RegisterRequest {
        private String username;
        private String password;
        private String nickname;
        private String email;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    /** 修改密码请求体 */
    public static class PasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    private final AuthService authService = new AuthServiceImpl();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        String action = segment(seg, 0);
        if ("login".equalsIgnoreCase(action)) {
            login(req, resp);
            return;
        }
        if ("register".equalsIgnoreCase(action)) {
            register(req, resp);
            return;
        }
        if ("logout".equalsIgnoreCase(action)) {
            logout(req, resp);
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        if ("me".equalsIgnoreCase(segment(seg, 0))) {
            me(req, resp);
            return;
        }
        if ("logout".equalsIgnoreCase(segment(seg, 0))) {
            // 允许用 GET 退出：某些跳转式退出只发 GET，兼容一下更稳
            logout(req, resp);
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] seg = pathSegments(req);
        if ("password".equalsIgnoreCase(segment(seg, 0))) {
            changePassword(req, resp);
            return;
        }
        notFound(resp, req.getRequestURI());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        methodNotAllowed(req, resp);
    }

    /* ==================================================================== */
    /*  处理分支                                                            */
    /* ==================================================================== */

    /**
     * 登录：校验口令 → 写会话 → 返回凭据。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void login(HttpServletRequest req, HttpServletResponse resp) {
        LoginRequest body = JsonUtil.readBody(req, LoginRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空，需要 username 与 password");
        }
        LoginVO vo = authService.login(body.getUsername(), body.getPassword());

        // 防会话固定攻击：登录成功时换一个新会话，丢弃登录前的那个
        HttpSession old = req.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = req.getSession(true);
        session.setAttribute(ATTR_USER_ID, vo.getUserId());
        session.setAttribute(ATTR_ROLE, vo.getRole());
        session.setAttribute(ATTR_USER_NAME, vo.getUserName());
        // 不调用 setMaxInactiveInterval：会话有效期唯一来源是 web.xml 的
        // <session-timeout>，这里只把容器已经生效的值回读给前端。
        // 早先这里用 Service 里的常量覆盖容器配置，同一个数字维护两处，
        // 改 web.xml 而不改常量就会出现"前端说 30 分钟、容器实际 15 分钟"。
        vo.setExpiresIn(Integer.valueOf(session.getMaxInactiveInterval()));

        ok(resp, vo);
    }

    /**
     * 注册：校验并落库 → 写会话 → 返回与登录同形的凭据。
     *
     * <p><b>注册即登录</b>：成功后与 {@link #login} 走同一套建会话写法（含防会话固定），
     * 用户不必注册完再登一次。角色由 Service 层强制为 USER，
     * Servlet 只负责解析请求体、建会话与包装响应，不含业务判断。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void register(HttpServletRequest req, HttpServletResponse resp) {
        RegisterRequest body = JsonUtil.readBody(req, RegisterRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空，需要 username 与 password");
        }
        LoginVO vo = authService.register(body.getUsername(), body.getPassword(),
                body.getNickname(), body.getEmail());

        // 防会话固定攻击：注册成功同样换一个新会话，丢弃注册前的那个（与 login 同一套写法）
        HttpSession old = req.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = req.getSession(true);
        session.setAttribute(ATTR_USER_ID, vo.getUserId());
        session.setAttribute(ATTR_ROLE, vo.getRole());
        session.setAttribute(ATTR_USER_NAME, vo.getUserName());
        // 会话有效期唯一来源是 web.xml 的 <session-timeout>，这里只回读给前端（理由见 login）
        vo.setExpiresIn(Integer.valueOf(session.getMaxInactiveInterval()));

        // 响应体与「登录成功」同形（token/role/roleText/userName/account/userId/avatarText/expiresIn），
        // 另附一份 user 视图（对象形态与 GET /api/auth/me 的 user 一致）：
        // 这样前端注册完既可直接读扁平字段，也可直接拿嵌套 user，不必再补一次 /me 请求。
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("token", vo.getToken());
        data.put("role", vo.getRole());
        data.put("roleText", vo.getRoleText());
        data.put("userName", vo.getUserName());
        data.put("account", vo.getAccount());
        data.put("userId", vo.getUserId());
        data.put("avatarText", vo.getAvatarText());
        data.put("expiresIn", vo.getExpiresIn());
        data.put("user", authService.profile(vo.getUserId().longValue()));
        ok(resp, data);
    }

    /**
     * 退出登录。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void logout(HttpServletRequest req, HttpServletResponse resp) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        ok(resp, "已退出登录", null);
    }

    /**
     * 取当前登录用户信息。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void me(HttpServletRequest req, HttpServletResponse resp) {
        long userId = requireUserId(req);
        UserVO vo = authService.profile(userId);
        HttpSession session = req.getSession(false);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("user", vo);
        data.put("userId", vo.getId());
        data.put("account", vo.getAccount());
        data.put("role", vo.getRoleCode());
        data.put("roleText", vo.getRole());
        data.put("avatarText", vo.getAvatarText());
        data.put("ops", vo.getOps());
        data.put("sessionTimeout", Integer.valueOf(session == null ? 0 : session.getMaxInactiveInterval()));
        ok(resp, data);
    }

    /**
     * 修改本人密码。
     *
     * @param req  请求
     * @param resp 响应
     */
    private void changePassword(HttpServletRequest req, HttpServletResponse resp) {
        long userId = requireUserId(req);
        PasswordRequest body = JsonUtil.readBody(req, PasswordRequest.class);
        if (body == null) {
            throw BusinessException.badRequest("请求体不能为空，需要 oldPassword 与 newPassword");
        }
        authService.changePassword(userId, body.getOldPassword(), body.getNewPassword());
        // 密码已变更，主动销毁会话，强制重新登录
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        JsonUtil.write(resp, Result.ok("密码已修改，请重新登录", null));
    }
}
